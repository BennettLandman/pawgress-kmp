package com.balandman.pawgress.data

/**
 * Reads and atomically writes the single JSON file [LiftRepository] persists
 * everything to. This is the one piece of the persistence layer that
 * genuinely differs by platform (Android's `Context.filesDir` vs iOS's
 * `NSFileManager` document directory), so it's a plain interface rather than
 * `expect`/`actual`: each app target constructs the concrete implementation
 * at startup and hands it to `LiftRepository`'s constructor. See
 * `AndroidAppFileStorage` (androidMain) and `IosAppFileStorage` (iosMain).
 */
interface AppFileStorage {
    /** Null if nothing has ever been written yet. */
    fun read(): String?

    /**
     * Writes [content] so that a crash or kill mid-write can never leave a
     * truncated, unparseable file in its place — implementations should write
     * to a temp location first and rename/move it over the real file.
     */
    fun writeAtomic(content: String)
}
