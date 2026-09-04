package com.balandman.pawgress.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balandman.pawgress.coach.CoachCatalog
import com.balandman.pawgress.data.CoachTheme
import com.balandman.pawgress.data.Difficulty
import com.balandman.pawgress.data.Equipment
import com.balandman.pawgress.data.LiftRepository
import com.balandman.pawgress.data.LogEntry
import com.balandman.pawgress.data.Machine
import com.balandman.pawgress.data.MachineGroup
import com.balandman.pawgress.data.RestoreSummary
import com.balandman.pawgress.data.SyncState
import com.balandman.pawgress.data.WeightRange
import com.balandman.pawgress.sync.SyncManager
import com.balandman.pawgress.sync.SyncResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ported from the Android-only `AndroidViewModel`. The biggest change isn't
 * mechanical — it's a real simplification that falls out of Phase 4's
 * `AuthProvider` redesign: the original had to hold a `PendingIntent` in
 * `_consentRequest`, expose `onConsentResult(context, resultOk, data)` for
 * the Activity to call back into after launching that intent, and track
 * `pendingRestore` so that callback knew whether to route the resulting
 * token to a sync or a restore. None of that exists anymore — `SyncManager`
 * now calls `AuthProvider.requestConsent()` directly and suspends for the
 * result, so consent UI is handled entirely inside the platform-specific
 * `AuthProvider` implementation, invisible to this ViewModel. `SyncResult`
 * itself dropped its `NeedsConsent` case for the same reason (see
 * `SyncManager.kt`).
 *
 * `repo` and `syncManager` are constructor-injected (matching the
 * interface-plus-DI pattern used everywhere else in this port) instead of
 * being read off an `Application` subclass — `AndroidViewModel` itself has
 * no multiplatform equivalent, so androidApp/iosApp each construct this with
 * their own platform's `LiftRepository`/`SyncManager` instances.
 *
 * `chooseAccount()`/`accountPickerRequest`/`onAccountChosen(email)` still
 * exist for Android's explicit "switch account" flow (the system account
 * picker, kept Android-only per Phase 4's `AccountPicker` note) — but
 * `onAccountChosen` now takes a plain `String?` email rather than an
 * Android `Account`, so this file stays platform-agnostic; the Android UI
 * layer resolves `AccountPicker`'s result down to an email before calling
 * in. An iOS "switch account" affordance can just call
 * `onAccountChosen(null)` directly, since Google's own sign-in page handles
 * account selection inline there — no separate native picker step needed.
 */
class MainViewModel(
    private val repo: LiftRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    val machines: StateFlow<List<Machine>> = repo.machines

    val visibleMachines: StateFlow<List<Machine>> = repo.machines
        .map { list -> list.filter { it.visible }.sortedBy { it.sortOrder } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repo.current().machines.filter { it.visible }.sortedBy { it.sortOrder },
        )

    /** False on platforms with no working Google sign-in yet (currently iOS). */
    val syncAvailable: Boolean = syncManager.isAvailable

    val syncState: StateFlow<SyncState> = repo.sync

    val log: StateFlow<List<LogEntry>> = repo.log

    val pendingCount: StateFlow<Int> = repo.log
        .map { entries -> entries.count { !it.synced } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Set when the user asked to pick or change the Google account. */
    private val _accountPickerRequest = MutableStateFlow(false)
    val accountPickerRequest: StateFlow<Boolean> = _accountPickerRequest.asStateFlow()

    // ------------------------------------------------------------ gamification

    val pawprintsBalance: StateFlow<Int> = repo.active.map { it.pawprintsBalance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().pawprintsBalance)

    val pawprintsEarnedTotal: StateFlow<Int> = repo.active.map { it.pawprintsEarnedTotal }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().pawprintsEarnedTotal)

    val unlockedCoachIds: StateFlow<Set<Int>> = repo.active.map { it.unlockedCoachIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().unlockedCoachIds)

    val selectedCoachId: StateFlow<Int> = repo.active.map { it.selectedCoachId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().selectedCoachId)

    val unlockedOutfits: StateFlow<Set<String>> = repo.active.map { it.unlockedOutfits }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().unlockedOutfits)

    val equippedOutfits: StateFlow<Map<Int, String>> = repo.active.map { it.equippedOutfits }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.current().equippedOutfits)

    fun unlockCoach(coachId: Int, cost: Int) {
        val unlocked = repo.unlockCoach(coachId, cost)
        _message.value = if (unlocked) {
            "${CoachCatalog.byId(coachId)?.name ?: "Coach"} unlocked!"
        } else {
            "Not enough pawprints yet."
        }
    }

    fun selectCoach(coachId: Int) = repo.selectCoach(coachId)

    fun unlockOutfit(coachId: Int, theme: CoachTheme, cost: Int) {
        val unlocked = repo.unlockOutfit(coachId, theme, cost)
        _message.value = if (unlocked) "${theme.displayName} unlocked!" else "Not enough pawprints yet."
    }

    /** Pass null to switch a coach back to its base look. */
    fun equipOutfit(coachId: Int, theme: CoachTheme?) = repo.equipOutfit(coachId, theme)

    /** DEBUG ONLY — remove before release, along with the Settings button that calls this. */
    fun debugGrantPawprints() {
        repo.debugGrantPawprints(1000)
        _message.value = "Debug: +1000 pawprints"
    }

    private var autoSyncJob: Job? = null

    /**
     * Set when a lift is logged while a sync is already in flight. The running
     * sync loop re-runs once more rather than dropping the request, so the
     * entry that arrived mid-upload still gets pushed.
     */
    private var resyncRequested = false

    // ------------------------------------------------------------------ logging

    fun logLift(machineId: String, weight: Int, difficulty: Difficulty? = null) {
        repo.logLift(machineId, weight, difficulty)
        syncAfterEntry()
    }

    fun undoToday(machineId: String) {
        repo.undoToday(machineId)
        syncAfterEntry()
    }

    // ----------------------------------------------------------------- machines

    /** Dial limits for [equipment], for whoever is drawing the sheet or the settings. */
    fun weightsFor(equipment: Equipment): WeightRange = repo.current().weightsFor(equipment)

    fun setWeightRange(equipment: Equipment, min: Int, max: Int, step: Int) =
        repo.setWeightRange(equipment, WeightRange.of(min, max, step))

    fun setVisible(machineId: String, visible: Boolean) = repo.setVisible(machineId, visible)

    fun setAllVisible(visible: Boolean) = repo.setAllVisible(visible)

    fun rename(machineId: String, name: String) = repo.rename(machineId, name)

    fun setIcon(machineId: String, iconKey: String, illustrated: Boolean) =
        repo.setIcon(machineId, iconKey, illustrated)

    fun setGroup(machineId: String, group: MachineGroup) = repo.setGroup(machineId, group)

    fun addMachine(name: String, iconKey: String, group: MachineGroup, illustrated: Boolean) {
        val created = repo.addCustomMachine(name, iconKey, group, illustrated)
        _message.value =
            if (created != null) "Added ${created.name}" else "Give the machine a name first."
    }

    fun deleteMachine(machineId: String) = repo.deleteCustomMachine(machineId)

    fun resetToday() {
        repo.resetToday()
        _message.value = "Today's lifts were reset."
        syncAfterEntry()
    }

    fun fullReset() {
        autoSyncJob?.cancel()
        repo.fullReset()
        // The reassurance only makes sense where a sheet can exist. On iOS
        // there is none, and naming one in a toast is how a user ends up
        // hunting for a spreadsheet that was never created.
        _message.value = if (syncAvailable) {
            "All activity was cleared. Your Google Sheet history is untouched."
        } else {
            "All activity was cleared."
        }
    }

    // --------------------------------------------------------------------- sync

    /** Connect an account, or switch to a different one. Opens the system picker (Android). */
    fun chooseAccount() {
        autoSyncJob?.cancel()
        _accountPickerRequest.value = true
    }

    fun accountPickerLaunched() {
        _accountPickerRequest.value = false
    }

    fun onAccountChosen(email: String?) {
        if (email == null) {
            _message.value = "No account chosen."
            return
        }
        viewModelScope.launch { runSync(allowConsentUi = true, accountHint = email) }
    }

    /** The Sync now button: refreshes the profile already on screen. */
    fun syncNow() {
        autoSyncJob?.cancel()
        viewModelScope.launch { runSync(allowConsentUi = true) }
    }

    /**
     * Settings → "Restore from Google Sheet": reads the profile's own
     * spreadsheet back in and merges anything missing locally. Safe to run
     * any time — it never removes local data, only adds to it.
     */
    fun restoreFromSheet() {
        if (_syncing.value) return
        autoSyncJob?.cancel()
        viewModelScope.launch {
            _syncing.value = true
            try {
                handle(syncManager.restore(allowConsentUi = true), announce = true)
            } finally {
                _syncing.value = false
            }
        }
    }

    /**
     * Pushes to the sheet immediately after every logged lift.
     *
     * This used to debounce: cancel any pending job, wait four seconds, then
     * upload one batch. That is kinder to the network and it lost data. Two
     * ways, both real:
     *
     *  - The wait ran in `viewModelScope`. Closing the app (or Android killing
     *    it) inside those four seconds cancelled the job, and the lift was
     *    never uploaded.
     *  - Each new lift cancelled the previous timer, so logging several
     *    machines in quick succession kept pushing the deadline out. Walk away
     *    mid-session and none of them had synced.
     *
     * A gym session is a few dozen appends at most, so per-lift upload costs
     * little and removes both failure modes. If a lift lands while a sync is
     * already running, [resyncRequested] makes the loop go round again instead
     * of dropping it — the old code's `if (_syncing.value) return` silently
     * discarded that second request.
     *
     * Failures stay quiet (`announce = false`): unsynced lifts remain queued
     * and the pending count in Settings is the honest signal. Offline in a gym
     * basement is normal, not worth a snackbar per set.
     */
    private fun syncAfterEntry() {
        if (repo.current().accountEmail == null || !repo.current().connected) return
        if (autoSyncJob?.isActive == true) {
            resyncRequested = true
            return
        }
        autoSyncJob = viewModelScope.launch {
            do {
                resyncRequested = false
                runSync(allowConsentUi = false, announce = false)
            } while (resyncRequested)
        }
    }

    private suspend fun runSync(
        allowConsentUi: Boolean,
        accountHint: String? = null,
        announce: Boolean = true,
    ) {
        if (_syncing.value) return
        _syncing.value = true
        try {
            handle(syncManager.sync(allowConsentUi, accountHint), announce)
        } finally {
            _syncing.value = false
        }
    }

    private fun handle(result: SyncResult, announce: Boolean) {
        when (result) {
            is SyncResult.Failed -> if (announce) _message.value = result.message
            is SyncResult.SwitchedProfile -> _message.value = "Switched to ${result.email}"
            SyncResult.Success -> if (announce) _message.value = "Synced to Google Sheets."
            SyncResult.NothingToDo -> if (announce) _message.value = "Already up to date."
            SyncResult.NotConnected ->
                if (announce) _message.value = "Connect a Google account first."
            is SyncResult.Restored -> if (announce) _message.value = restoreMessage(result.summary)
        }
    }

    private fun restoreMessage(summary: RestoreSummary): String {
        if (summary.entriesAdded == 0 && summary.machinesCreated == 0) {
            return "Already up to date — nothing new in the sheet."
        }
        val parts = mutableListOf("Restored ${summary.entriesAdded} lift(s)")
        if (summary.machinesCreated > 0) {
            parts += "${summary.machinesCreated} new exercise(s)"
        }
        return parts.joinToString(" and ") + " from your Google Sheet."
    }

    fun disconnect() {
        autoSyncJob?.cancel()
        repo.disconnect()
        _message.value = "Stopped syncing. This profile's history stays on the phone."
    }

    // ------------------------------------------------------------------- events

    fun messageShown() {
        _message.value = null
    }

}
