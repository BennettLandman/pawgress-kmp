@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.balandman.pawgress.data

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS implementation of [AppFileStorage], backed by a file in the app's
 * Documents directory (the standard place for a small amount of app-owned
 * user data on iOS — it's included in iCloud/iTunes backups, unlike the
 * Caches directory).
 *
 * NOTE: unlike the rest of this port, this file has not been checked by a
 * Kotlin/Native compiler (this session cannot build for iOS at all — see
 * PORTING_PLAN.md). The Foundation interop signatures below (`stringWithContentsOfFile`,
 * `writeToFile`, `NSString`/`String` bridging) are written from documented
 * Kotlin/Native <-> Objective-C interop behavior, but if Xcode/the Kotlin
 * compiler flags a signature mismatch here, this is the first place to look.
 */
class IosAppFileStorage(fileName: String = "pawgress.json") : AppFileStorage {

    private val path: String = run {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = (dirs.firstOrNull() as? String) ?: NSTemporaryDirectory()
        "$dir/$fileName"
    }
    private val tmpPath: String get() = "$path.tmp"

    override fun read(): String? = try {
        platform.Foundation.NSString.stringWithContentsOfFile(
            path,
            encoding = NSUTF8StringEncoding,
            error = null,
        ) as String?
    } catch (e: Throwable) {
        null
    }

    override fun quarantineUnreadable(content: String): String? {
        // Mirrors AndroidAppFileStorage: a copy beside the real file, stamped
        // so repeat failures don't overwrite each other. Best-effort by
        // design -- a failure here must never stop the app from starting.
        val copy = "$path.unreadable-${'$'}{(NSDate().timeIntervalSince1970 * 1000.0).toLong()}"
        val wrote = (content as platform.Foundation.NSString).writeToFile(
            copy,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return if (wrote) copy else null
    }

    override fun writeAtomic(content: String) {
        val fileManager = NSFileManager.defaultManager
        val wroteTmp = (content as platform.Foundation.NSString).writeToFile(
            tmpPath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (!wroteTmp) return
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, error = null)
        }
        val moved = fileManager.moveItemAtPath(tmpPath, toPath = path, error = null)
        if (!moved) {
            // Fall back to a direct (non-atomic) write rather than silently losing data.
            (content as platform.Foundation.NSString).writeToFile(
                path,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
        }
    }
}
