package com.nibble.app.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform TTS behind one interface (§8). The critical requirement is character-range
 * callbacks — [spokenChar] emits the absolute charOffset as each word begins, so the
 * highlight follows the voice and the text resumes exactly where audio stopped.
 *
 * The guide specifies `expect class Narrator`; an interface with platform implementations
 * is the same contract and avoids expect/actual constructor mismatch (Android needs a
 * Context, iOS does not).
 */
interface Narrator {
    val state: StateFlow<NarratorState>

    /** Absolute charOffset (into the chapter text) as each word begins. */
    val spokenChar: StateFlow<Int>

    fun speak(text: String, fromChar: Int, rate: Float)
    fun pause()
    fun resume()
    fun stop()
    fun release()

    /** Book context for the lockscreen "now playing" surface (§8). Default: no-op. */
    fun setNowPlaying(title: String, bookId: String) {}
}

enum class NarratorState { IDLE, SPEAKING, PAUSED }

/** No-op used in previews and before a platform narrator is wired in. */
class NoopNarrator : Narrator {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(NarratorState.IDLE)
    override val spokenChar = kotlinx.coroutines.flow.MutableStateFlow(0)
    override fun speak(text: String, fromChar: Int, rate: Float) {}
    override fun pause() {}
    override fun resume() {}
    override fun stop() {}
    override fun release() {}
}
