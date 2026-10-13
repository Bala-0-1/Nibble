package com.nibble.app.core.data

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

class IosPersistence : Persistence {
    private val path: String by lazy {
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val dir = dirs.firstOrNull() as? String ?: "."
        "$dir/nibble_state.json"
    }

    override fun load(): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String?

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun save(text: String) {
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
