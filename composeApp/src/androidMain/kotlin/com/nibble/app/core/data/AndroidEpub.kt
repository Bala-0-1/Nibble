package com.nibble.app.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nibble.app.core.domain.Book
import com.nibble.app.core.domain.EpubParser
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

object AndroidEpub {
    /** Extracted covers/illustrations live here, one subdirectory per imported book. */
    fun imagesDir(context: Context): File = File(context.filesDir, "book-images")

    fun read(context: Context, uri: Uri): Book {
        val entries = HashMap<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zis.readBytes()
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("Couldn't open the file.")
        val name = displayName(context, uri)?.substringBeforeLast('.') ?: "Imported book"
        return EpubParser.parse(entries, name) { relativeName, bytes ->
            runCatching {
                val f = File(imagesDir(context), relativeName)
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
                f.absolutePath
            }.getOrNull()
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
}
