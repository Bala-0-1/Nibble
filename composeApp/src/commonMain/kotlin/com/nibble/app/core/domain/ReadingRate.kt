package com.nibble.app.core.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reading rate (§6). Start at 220 wpm; after each completed session update an exponential
 * moving average (α = 0.2) of words ÷ active seconds, discarding sessions shorter than 20 s.
 * Re-segmentation runs only when the rate moves more than 15% from the value the book was
 * chunked at, and it preserves the current position by character offset (positions store
 * `charOffset`, so re-segmenting alone preserves them).
 */
object ReadingRate {
    const val SEED = 220
    private const val ALPHA = 0.2
    private const val DRIFT = 0.15
    private const val MIN = 90
    private const val MAX = 600

    fun estimate(sessions: List<Session>): Int {
        var rate = SEED.toDouble()
        for (s in sessions) {
            if (s.activeSeconds < 20 || s.words <= 0) continue
            val wpm = (s.words.toDouble() / s.activeSeconds * 60.0).coerceIn(MIN.toDouble(), MAX.toDouble())
            rate = ALPHA * wpm + (1 - ALPHA) * rate
        }
        return rate.roundToInt().coerceIn(MIN, MAX)
    }

    /** True when [rate] has drifted far enough from [segmentedAt] to justify re-chunking. */
    fun shouldResegment(rate: Int, segmentedAt: Int): Boolean =
        segmentedAt > 0 && abs(rate - segmentedAt).toDouble() / segmentedAt > DRIFT
}
