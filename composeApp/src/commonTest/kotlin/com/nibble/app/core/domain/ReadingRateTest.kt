package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingRateTest {

    private fun session(words: Int, seconds: Int) =
        Session(1, "b", 0, seconds, words, ReaderMode.NIBBLES)

    @Test
    fun emaMovesTowardMeasuredRate() {
        // ~300 wpm reader (300 words / 60 s), sustained.
        val sessions = List(25) { session(300, 60) }
        val r = ReadingRate.estimate(sessions)
        assertTrue(r in 270..320, "expected ~300, got $r")
    }

    @Test
    fun shortSessionsAreIgnored() {
        assertEquals(ReadingRate.SEED, ReadingRate.estimate(listOf(session(100, 5))))
    }

    @Test
    fun driftThresholdIsFifteenPercent() {
        assertTrue(ReadingRate.shouldResegment(260, 220))     // +18% → re-segment
        assertFalse(ReadingRate.shouldResegment(232, 220))    // +5%  → keep
    }
}
