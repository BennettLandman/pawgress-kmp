package com.balandman.pawgress.sync

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CompletableDeferred

/**
 * Concrete [ConsentLauncher] built on `ActivityResultContracts.StartIntentSenderForResult`.
 *
 * Usage from an Activity's `onCreate`:
 * ```
 * val consentLauncher = AndroidConsentLauncher()
 * val activityResultLauncher = registerForActivityResult(
 *     ActivityResultContracts.StartIntentSenderForResult()
 * ) { result -> consentLauncher.onResult(result) }
 * consentLauncher.attach(activityResultLauncher)
 * ```
 * Only one consent flow can be in flight at a time, which matches
 * `SyncManager`'s own mutex — sync operations are already serialized.
 */
class AndroidConsentLauncher : ConsentLauncher {

    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pending: CompletableDeferred<ActivityResult>? = null

    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
    }

    override suspend fun launch(pendingIntent: PendingIntent): ConsentResult {
        val activityLauncher = launcher
            ?: return ConsentResult(ok = false, data = null)

        val deferred = CompletableDeferred<ActivityResult>()
        pending = deferred
        activityLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        val result = deferred.await()
        return ConsentResult(ok = result.resultCode == Activity.RESULT_OK, data = result.data)
    }

    /** Call this from the registered launcher's callback. */
    fun onResult(result: ActivityResult) {
        pending?.complete(result)
        pending = null
    }
}
