package com.nibble.app.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decode an image file from disk, downsampled to a screen-sized bitmap. Platform actuals:
 * BitmapFactory on Android, okio + Skia on iOS. Returns null for missing/undecodable
 * files — callers fall back to the typographic cover / render nothing.
 */
expect fun decodeImageFile(path: String): ImageBitmap?

/** Decoded-bitmap cache; illustrated chapters re-enter composition constantly. */
private val imageCache = HashMap<String, ImageBitmap?>()

/** Off-main decode with a bounded cache. Null while loading, on failure, or for null paths. */
@Composable
fun rememberBookImage(path: String?): ImageBitmap? {
    var bmp by remember(path) { mutableStateOf(if (path != null) imageCache[path] else null) }
    LaunchedEffect(path) {
        if (path != null && !imageCache.containsKey(path)) {
            val decoded = withContext(Dispatchers.Default) { decodeImageFile(path) }
            if (imageCache.size > 24) imageCache.clear()   // crude bound; re-decode is cheap
            imageCache[path] = decoded
            bmp = decoded
        }
    }
    return bmp
}
