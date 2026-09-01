package com.balandman.pawgress.sync

/**
 * The outcome of trying to get a usable Google access token.
 *
 * There is no "needs consent" case here on purpose — see [AuthProvider] for
 * why. By the time either platform hands one of these back, the UI part (if
 * any was needed) has already happened.
 */
sealed interface AuthOutcome {
    data class Success(val accessToken: String) : AuthOutcome

    data class Failure(val message: String) : AuthOutcome
}

/**
 * Authorization only — there is no separate "sign in" step, same as the
 * original Android-only `GoogleAuth`.
 *
 * The Android and iOS implementations of this interface get consent through
 * completely different platform UI (an Activity Result launched from a
 * `PendingIntent` vs. an `ASWebAuthenticationSession`), so rather than trying
 * to model "a launchable, resumable platform UI flow" generically, that
 * whole concern is pushed behind [requestConsent] — commonMain code never
 * needs to know what either platform's consent screen actually looks like or
 * how it's launched.
 */
interface AuthProvider {

    /**
     * Returns a token straight away if consent was already granted for this
     * account, without showing any UI — this is also the "refresh an expired
     * token" path. Returns `null` (not [AuthOutcome.Failure]) specifically
     * when the platform needs to show its consent UI to proceed, so the
     * caller can decide whether that's appropriate right now (a background
     * sync should not throw a sign-in screen at someone mid-workout; a
     * manual "Sync Now" tap should).
     *
     * @param accountHint the account to authorize as, if known — keeps a
     *   silent refresh pinned to the profile on screen instead of drifting to
     *   whichever account the platform considers default.
     */
    suspend fun silentAuthorize(accountHint: String? = null): AuthOutcome?

    /**
     * Always shows whatever consent/sign-in UI the platform needs and
     * suspends until it resolves to a token or a failure (including the
     * user cancelling).
     */
    suspend fun requestConsent(accountHint: String? = null): AuthOutcome
}
