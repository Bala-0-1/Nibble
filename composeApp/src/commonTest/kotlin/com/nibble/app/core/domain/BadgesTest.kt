package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Badges are derived, never stored — the same sessions always yield the same shelf. */
class BadgesTest {

    private val day = 86_400_000L
    private fun session(id: Long, ago: Long, seconds: Int, words: Int = seconds * 4) =
        Session(id, "b", Time.nowMillis() - ago, seconds, words, ReaderMode.NIBBLES)

    @Test
    fun noSessionsNoBadges() {
        assertEquals(emptyList(), Badges.derive(emptyList(), 0))
    }

    @Test
    fun firstRealSessionEarnsFirstNibble() {
        val badges = Badges.derive(listOf(session(1, 0, 120, words = 100)), 0)
        assertEquals(listOf("First nibble"), badges.map { it.label })
    }

    @Test
    fun finishedBooksAreCounted() {
        val badges = Badges.derive(listOf(session(1, 0, 120)), 2)
        assertTrue(badges.any { it.label == "Finished 2 books" })
    }

    @Test
    fun longStreakEarnsItsBadge() {
        val sessions = (0..4).map { session(it + 1L, it * day, 120, words = 10) }
        val badges = Badges.derive(sessions, 0)
        assertTrue(badges.any { it.label == "Longest streak · 5 days" })
    }

    @Test
    fun wordMilestoneUsesTheHighestCrossed() {
        val sessions = listOf(session(1, 0, 120, words = 12_000))
        val badges = Badges.derive(sessions, 0)
        assertTrue(badges.any { it.label == "10,000 words eaten" })
        assertTrue(badges.none { it.label == "1,000 words eaten" })
    }
}
