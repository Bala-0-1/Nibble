package com.nibble.app.core.design

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun decodeImageFile(path: String): ImageBitmap? = runCatching {
    val bytes = FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
