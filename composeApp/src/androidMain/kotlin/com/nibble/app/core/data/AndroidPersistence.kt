package com.nibble.app.core.data

import android.content.Context
import com.nibble.app.widget.refreshNibbleWidget
import java.io.File

class AndroidPersistence(private val context: Context) : Persistence {
    private val file = File(context.filesDir, "nibble_state.json")
    private val widgetFile = File(context.filesDir, "nibble_widget.json")

    override fun load(): String? = if (file.exists()) file.readText() else null

    override fun save(text: String) {
        val tmp = File(file.parentFile, "nibble_state.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)   // atomic-ish replace
    }

    override fun saveWidget(json: String) {
        runCatching {
            val tmp = File(widgetFile.parentFile, "nibble_widget.tmp")
            tmp.writeText(json)
            tmp.renameTo(widgetFile)
            refreshNibbleWidget(context)
        }
    }

    override fun deleteBookImages(bookId: String) {
        // bookId is app-generated ("import-<hash36>"), never raw user input.
        runCatching { File(AndroidEpub.imagesDir(context), bookId).deleteRecursively() }
    }
}

