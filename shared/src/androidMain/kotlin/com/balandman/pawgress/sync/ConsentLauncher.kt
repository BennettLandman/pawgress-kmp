package com.balandman.pawgress.sync

import android.app.PendingIntent
import android.content.Intent

/**
 * Bridges Android's callback-based Activity Result API into a suspend
 * function, so [AndroidAuthProvider] can launch Google's consent screen and
 * simply await the result rather than the caller having to split its logic
 * across "launch" and "handle the result" like the original Android-only
 * `MainActivity` did.
 *
 * The real implementation of this lives in each app's Activity, because
 * `registerForActivityResult` must be called during Activity
 * initialization — not lazily from deep inside a suspend function. See
 * `MainActivity.kt` for the wiring (an `ActivityResultLauncher` registered
 * in `onCreate`, whose callback forwards into this interface's `onResult`).
 */
interface ConsentLauncher {

    /** Launches the given consent [PendingIntent] and suspends for its result. */
    suspend fun launch(pendingIntent: PendingIntent): ConsentResult
}

data class ConsentResult(val ok: Boolean, val data: Intent?)
