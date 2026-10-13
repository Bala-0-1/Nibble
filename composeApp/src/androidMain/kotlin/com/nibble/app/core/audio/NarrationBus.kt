package com.nibble.app.core.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared state between the foreground [NarrationService] and the app's [AndroidNarrator]. */
object NarrationBus {
    val state = MutableStateFlow(NarratorState.IDLE)
    val spokenChar = MutableStateFlow(0)
}
