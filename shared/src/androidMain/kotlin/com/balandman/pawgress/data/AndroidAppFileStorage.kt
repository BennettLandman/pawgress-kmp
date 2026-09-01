package com.balandman.pawgress.data

import android.content.Context
import java.io.File

/** Android implementation of [AppFileStorage], backed by the app's private files directory. */
class AndroidAppFileStorage(
    context: Context,
    fileName: String = "pawgress.json",
) : AppFileStorage {

    private val file = File(context.applicationContext.filesDir, fileName)

    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun writeAtomic(content: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            // renameTo can fail if the destination exists on some devices.
            file.writeText(content)
            tmp.delete()
        }
    }
}
