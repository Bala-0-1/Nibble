package com.nibble.app

import androidx.compose.ui.window.ComposeUIViewController
import com.nibble.app.core.audio.IosNarrator
import com.nibble.app.core.data.IosPersistence

fun MainViewController() = ComposeUIViewController {
    App(narrator = IosNarrator(), persistence = IosPersistence())
}
