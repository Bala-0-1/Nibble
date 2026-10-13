package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** §10 — streaks are derived from sessions, so a clock change or restore can't inflate them. */
class StatsTest {

    private val day = 86_400_000L
    private fun session(id: Long, ago: Long, seconds: Int) =
        Session(id, "b", Time.nowMillis() - ago, seconds, seconds * 4, ReaderMode.NIBBLES)

    @Test
    fun streakCountsConsecutiveDays() {
        val sessions = listOf(session(1, 0, 120), session(2, day, 120), session(3, 2 * day, 120))
        assertEquals(3, Stats.streak(sessions))
    }

    @Test
    fun shortSessionsDoNotCount() {
        assertEquals(0, Stats.streak(listOf(session(1, 0, 30))))
    }

    @Test
    fun gapBreaksTheStreak() {
        val sessions = listOf(session(1, 0, 120), session(2, 3 * day, 120))
        assertEquals(1, Stats.streak(sessions))
    }

    @Test
    fun noSessionsIsZero() {
        assertEquals(0, Stats.streak(emptyList()))
    }

    @Test
    fun monthTotalsAreDerived() {
        val sessions = listOf(session(1, 0, 100), session(2, day, 200))
        val m = Stats.month(sessions)
        assertEquals(300, m.totalActiveSeconds)
        assertEquals(2, m.sessionCount)
    }

    @Test
    fun longestStreakSurvivesBeingBroken() {
        // A 3-day run long ago beats the 1-day run today.
        val sessions = listOf(
            session(1, 0, 120),
            session(2, 10 * day, 120), session(3, 11 * day, 120), session(4, 12 * day, 120),
        )
        assertEquals(3, Stats.longestStreak(sessions))
        assertEquals(1, Stats.streak(sessions))
    }

    @Test
    fun longestStreakIgnoresShortSessions() {
        val sessions = listOf(session(1, 0, 120), session(2, day, 30), session(3, 2 * day, 120))
        assertEquals(1, Stats.longestStreak(sessions))
    }

    @Test
    fun todaySecondsSumsOnlyToday() {
        val sessions = listOf(session(1, 0, 100), session(2, 60_000, 40), session(3, 2 * day, 500))
        assertEquals(140, Stats.todaySeconds(sessions))
    }
}
