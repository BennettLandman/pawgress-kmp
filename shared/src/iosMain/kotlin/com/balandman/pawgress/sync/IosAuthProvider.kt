package com.balandman.pawgress.sync

/**
 * iOS implementation of [AuthProvider] — **not implemented yet.**
 *
 * Both platforms need `AuthProvider` to exist and conform so the `shared`
 * module compiles for iOS targets, but the real Google sign-in flow here
 * needs work that can't be done blind, without Xcode or a Simulator to
 * check it against:
 *
 * - A generic OAuth 2.0 Authorization Code + PKCE flow directly against
 *   Google's endpoints (`accounts.google.com/o/oauth2/v2/auth` for the
 *   consent page, `oauth2.googleapis.com/token` for the code-for-token
 *   exchange) — this part is plain Kotlin and could live in `commonMain`
 *   via Ktor once written.
 * - PKCE needs a real SHA-256 for the code_challenge, which has no
 *   pure-Kotlin-common stdlib implementation — needs either a multiplatform
 *   crypto dependency or careful hand-written crypto, neither of which
 *   should be picked blind.
 * - The one truly iOS-specific piece is presenting the consent page and
 *   getting the redirect back, via `ASWebAuthenticationSession` — this is
 *   Kotlin/Native interop with `AuthenticationServices` that has never been
 *   compiled in this project.
 *
 * See `PORTING_PLAN.md`'s Phase 4 section for the full reasoning. This gets
 * implemented for real once Phase 6 sets up the actual Xcode project, so it
 * can be checked against a real Simulator run instead of guessed.
 */
class IosAuthProvider : AuthProvider {

    override suspend fun silentAuthorize(accountHint: String?): AuthOutcome? = null

    override suspend fun requestConsent(accountHint: String?): AuthOutcome =
        AuthOutcome.Failure("Google sign-in isn't implemented on iOS yet.")
}
