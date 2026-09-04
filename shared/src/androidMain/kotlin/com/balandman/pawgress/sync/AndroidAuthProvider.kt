package com.balandman.pawgress.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android implementation of [AuthProvider], using Google's Identity
 * Authorization API — ported from the original Android-only `GoogleAuth`.
 *
 * `drive.file` grants access *only* to files this app creates, so the app
 * can make and update its own spreadsheet and can never see the rest of
 * Drive.
 */
class AndroidAuthProvider(
    private val context: Context,
    private val consentLauncher: ConsentLauncher,
) : AuthProvider {

    override val isSupported: Boolean = true

    override suspend fun silentAuthorize(accountHint: String?): AuthOutcome? =
        when (val outcome = rawAuthorize(accountHint?.let { Account(it, GOOGLE_ACCOUNT_TYPE) })) {
            is RawOutcome.Success -> AuthOutcome.Success(outcome.token)
            is RawOutcome.Failure -> AuthOutcome.Failure(outcome.message)
            is RawOutcome.NeedsConsent -> null
        }

    override suspend fun requestConsent(accountHint: String?): AuthOutcome =
        when (val outcome = rawAuthorize(accountHint?.let { Account(it, GOOGLE_ACCOUNT_TYPE) })) {
            is RawOutcome.Success -> AuthOutcome.Success(outcome.token)
            is RawOutcome.Failure -> AuthOutcome.Failure(outcome.message)
            is RawOutcome.NeedsConsent -> {
                val result = consentLauncher.launch(outcome.pendingIntent)
                if (!result.ok) {
                    AuthOutcome.Failure("Google sign-in was cancelled.")
                } else {
                    resultFromIntent(result.data)
                }
            }
        }

    private fun buildRequest(account: Account?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE), Scope(SCOPE_EMAIL)))
        // Naming the account keeps a background refresh pinned to the
        // profile on screen instead of drifting to whichever login Google
        // considers default.
        if (account != null) builder.setAccount(account)
        return builder.build()
    }

    /**
     * Returns a token straight away once consent has been given previously,
     * so this is also the "refresh the expired token" path — no UI in the
     * common case.
     */
    private suspend fun rawAuthorize(account: Account?): RawOutcome = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context.applicationContext)
            .authorize(buildRequest(account))
            .addOnSuccessListener { result ->
                val pending: PendingIntent? = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    cont.resume(RawOutcome.NeedsConsent(pending))
                } else {
                    val token = result.accessToken
                    cont.resume(
                        if (token.isNullOrEmpty()) {
                            RawOutcome.Failure("Google did not return an access token.")
                        } else {
                            RawOutcome.Success(token)
                        }
                    )
                }
            }
            .addOnFailureListener { error -> cont.resume(RawOutcome.Failure(explain(error))) }
    }

    /** Reads the result of the consent screen launched via [ConsentLauncher]. */
    private fun resultFromIntent(data: Intent?): AuthOutcome = try {
        val result = Identity.getAuthorizationClient(context.applicationContext)
            .getAuthorizationResultFromIntent(data)
        val token = result.accessToken
        if (token.isNullOrEmpty()) {
            AuthOutcome.Failure("Google did not return an access token.")
        } else {
            AuthOutcome.Success(token)
        }
    } catch (e: Exception) {
        AuthOutcome.Failure(explain(e))
    }

    private fun explain(error: Exception): String = when {
        error is ApiException && error.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
            "Google rejected this app's OAuth setup (error 10). The Android OAuth " +
                "client must list package name com.balandman.pawgress and the SHA-1 " +
                "of the keystore this build was signed with."

        error is ApiException && error.statusCode == CommonStatusCodes.NETWORK_ERROR ->
            "No network connection."

        error is ApiException ->
            "Google authorization failed (code ${error.statusCode})."

        else -> error.message ?: "Google authorization failed."
    }

    private sealed interface RawOutcome {
        data class Success(val token: String) : RawOutcome
        data class NeedsConsent(val pendingIntent: PendingIntent) : RawOutcome
        data class Failure(val message: String) : RawOutcome
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    }
}
