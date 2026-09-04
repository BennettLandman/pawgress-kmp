package com.balandman.pawgress.sync

import com.balandman.pawgress.data.LiftRepository
import com.balandman.pawgress.data.Profile
import com.balandman.pawgress.data.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncResult {
    data object Success : SyncResult
    data object NothingToDo : SyncResult
    data object NotConnected : SyncResult
    data class SwitchedProfile(val email: String) : SyncResult
    data class Failed(val message: String) : SyncResult
    data class Restored(val summary: RestoreSummary) : SyncResult
}

/**
 * Pushes the active profile's log up to that account's own spreadsheet.
 *
 * Two rules hold this together. The account is always resolved from the
 * token just issued, never from anything cached — so the app cannot write
 * one person's lifts into another person's Drive. And only an action the
 * user took deliberately is allowed to switch profiles; a background sync
 * that finds itself holding the wrong account gives up instead.
 *
 * Ported from the original Android-only `SyncManager`. The only real change
 * is at the auth boundary: the original returned `SyncResult.NeedsConsent`
 * holding an Android `PendingIntent` for the UI layer to launch itself; here,
 * `allowConsentUi = true` calls [AuthProvider.requestConsent] directly, which
 * handles showing and awaiting whatever consent UI the platform needs behind
 * the scenes. That removes the need for a `NeedsConsent` case entirely — see
 * `AuthProvider.kt`.
 */
class SyncManager(
    private val repo: LiftRepository,
    private val auth: AuthProvider,
    private val api: SheetsApi = SheetsApi(),
) {

    private val mutex = Mutex()

    /**
     * @param allowConsentUi false for background syncs, where silently
     *   failing is better than throwing a sign-in screen at someone
     *   mid-workout.
     * @param accountHint the account to authorize as. Defaults to whoever
     *   owns the active profile, so a background refresh can never drift to
     *   another login.
     */
    suspend fun sync(
        allowConsentUi: Boolean,
        accountHint: String? = null,
    ): SyncResult = mutex.withLock {
        val profile = repo.current()
        val target = accountHint ?: profile.accountEmail

        if (!allowConsentUi && (!profile.connected || target == null)) {
            return@withLock SyncResult.NotConnected
        }

        when (val outcome = resolveToken(target, allowConsentUi)) {
            is AuthOutcome.Success ->
                runSync(outcome.accessToken, allowProfileSwitch = allowConsentUi)

            is AuthOutcome.Failure -> {
                repo.recordSyncError(outcome.message)
                SyncResult.Failed(outcome.message)
            }

            null -> SyncResult.NotConnected
        }
    }

    /** Used right after the consent/sign-in UI hands back a token. */
    suspend fun syncWithToken(token: String): SyncResult =
        mutex.withLock { runSync(token, allowProfileSwitch = true) }

    /** Used right after the consent/sign-in UI hands back a token for a restore. */
    suspend fun restoreWithToken(token: String): SyncResult =
        mutex.withLock { runRestore(token) }

    /**
     * Reads the active profile's own spreadsheet back into the app —
     * restoring a backup, typically after a reinstall or a
     * [LiftRepository.fullReset]. Purely additive: it merges sheet rows into
     * whatever is already local rather than replacing it, so it's safe to
     * run more than once.
     */
    suspend fun restore(allowConsentUi: Boolean): SyncResult = mutex.withLock {
        val profile = repo.current()
        val target = profile.accountEmail
            ?: return@withLock SyncResult.Failed("Sign in with Google first.")

        when (val outcome = resolveToken(target, allowConsentUi)) {
            is AuthOutcome.Success -> runRestore(outcome.accessToken)
            is AuthOutcome.Failure -> SyncResult.Failed(outcome.message)
            null -> SyncResult.NotConnected
        }
    }

    /**
     * Tries silently first; only shows consent UI when [allowConsentUi] is
     * true and silent auth couldn't produce a token on its own. Returns
     * `null` when UI would have been needed but wasn't allowed.
     */
    private suspend fun resolveToken(
        accountHint: String?,
        allowConsentUi: Boolean,
    ): AuthOutcome? {
        val silent = auth.silentAuthorize(accountHint)
        if (silent != null) return silent
        if (!allowConsentUi) return null
        return auth.requestConsent(accountHint)
    }

    private suspend fun runRestore(token: String): SyncResult = withContext(Dispatchers.Default) {
        try {
            val profile = repo.current()
            val email = api.userEmail(token)
            if (email == null || email != profile.accountEmail) {
                return@withContext SyncResult.Failed(
                    "Signed into a different Google account than this profile — try Sync first."
                )
            }
            val spreadsheetId = profile.spreadsheetId
                ?: return@withContext SyncResult.Failed(
                    "No Google Sheet connected yet — sync once first so there's something to restore from."
                )
            // Settings first, so that a machine the sheet knows about exists
            // (with its real name and area) before the log rows that reference
            // it are merged — otherwise restoreFromRows would recreate it from
            // the log alone, with a guessed icon and no visibility setting.
            runCatching {
                val settingsRows = api.readSettings(token, spreadsheetId)
                SettingsRows.fromRows(settingsRows, repo.settingsSnapshot())
                    ?.let { repo.applySettingsSnapshot(it) }
            }

            val rows = api.readAllRows(token, spreadsheetId)
            SyncResult.Restored(repo.restoreFromRows(rows))
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Restore failed.")
        }
    }

    private suspend fun runSync(
        token: String,
        allowProfileSwitch: Boolean,
    ): SyncResult = withContext(Dispatchers.Default) {
        try {
            // Who does this token actually belong to? Everything downstream
            // keys off the answer, so it is never taken on trust from stored
            // state.
            val email = api.userEmail(token)
                ?: return@withContext SyncResult.Failed(
                    "Google did not say which account this is."
                )

            val before = repo.current()
            val switching = before.accountEmail != email

            if (switching && !allowProfileSwitch) {
                // A background refresh came back holding a different account
                // than the grid on screen. Swapping the whole app out from
                // underneath someone mid-workout would be worse than doing
                // nothing.
                return@withContext SyncResult.NotConnected
            }

            // Marks this account connected, and switches to (or creates) its
            // profile if it is not the one already on screen.
            val profile: Profile = repo.activateAccount(email)

            val sheet = ensureSpreadsheet(token, profile)

            val deletions = repo.pendingDeletions()
            if (deletions.isNotEmpty()) {
                repo.clearPendingDeletions(api.deleteEntries(token, sheet.id, deletions))
            }

            val pending = repo.unsyncedEntries()
            if (pending.isNotEmpty()) {
                api.appendEntries(token, sheet.id, pending)
                repo.markSynced(pending.map { it.id })
            }

            // Best effort, and deliberately after the lifts: a settings-tab
            // failure (an old sheet, a permissions oddity) must never fail a
            // sync that successfully uploaded someone's workout.
            runCatching {
                api.writeSettings(token, sheet.id, SettingsRows.toRows(repo.settingsSnapshot()))
            }

            repo.recordSyncSuccess(sheet.id, sheet.url)

            when {
                switching -> SyncResult.SwitchedProfile(email)
                pending.isEmpty() && deletions.isEmpty() -> SyncResult.NothingToDo
                else -> SyncResult.Success
            }
        } catch (e: Exception) {
            val message = e.message ?: "Sync failed."
            repo.recordSyncError(message)
            SyncResult.Failed(message)
        }
    }

    /**
     * Every profile owns exactly one spreadsheet, created by the app in that
     * account's own Drive. If it was deleted, a new one is created and the
     * profile's whole history is replayed into it.
     */
    private suspend fun ensureSpreadsheet(token: String, profile: Profile): SpreadsheetRef {
        val existingId = profile.spreadsheetId?.takeIf { api.spreadsheetExists(token, it) }
        if (existingId != null) {
            // Cheap and idempotent — picks up new columns (Area, Difficulty)
            // on a sheet created before they existed, without touching any
            // data row.
            runCatching { api.ensureHeaderUpToDate(token, existingId) }
            return SpreadsheetRef(
                id = existingId,
                url = profile.spreadsheetUrl
                    ?: "https://docs.google.com/spreadsheets/d/$existingId/edit",
            )
        }
        return api.createLogSpreadsheet(token).also { repo.markAllUnsynced() }
    }
}
