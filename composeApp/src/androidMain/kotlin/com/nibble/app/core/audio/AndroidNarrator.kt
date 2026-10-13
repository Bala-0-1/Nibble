package com.nibble.app.core.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin client over [NarrationService] (§8). speak/pause/resume/stop are sent to the
 * foreground service so narration survives backgrounding; state comes back via [NarrationBus].
 */
class AndroidNarrator(private val context: Context) : Narrator {

    override val state: StateFlow<NarratorState> = NarrationBus.state
    override val spokenChar: StateFlow<Int> = NarrationBus.spokenChar

    private var nowTitle = "Nibble"
    private var nowBook = ""

    override fun setNowPlaying(title: String, bookId: String) {
        nowTitle = title
        nowBook = bookId
    }

    override fun speak(text: String, fromChar: Int, rate: Float) {
        val i = Intent(context, NarrationService::class.java).setAction(NarrationService.ACTION_SPEAK)
            .putExtra(NarrationService.EX_TEXT, text)
            .putExtra(NarrationService.EX_FROM, fromChar)
            .putExtra(NarrationService.EX_RATE, rate)
            .putExtra(NarrationService.EX_TITLE, nowTitle)
            .putExtra(NarrationService.EX_BOOK, nowBook)
        ContextCompat.startForegroundService(context, i)
    }

    override fun pause() = send(NarrationService.ACTION_PAUSE)
    override fun resume() = send(NarrationService.ACTION_RESUME)
    override fun stop() = send(NarrationService.ACTION_STOP)
    override fun release() = send(NarrationService.ACTION_STOP)

    private fun send(action: String) {
        runCatching { context.startService(Intent(context, NarrationService::class.java).setAction(action)) }
    }
}
