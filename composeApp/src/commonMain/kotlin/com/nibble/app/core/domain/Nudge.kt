package com.nibble.app.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** What a lockscreen drip / notification shows (§9). Shared, never queried per-process. */
data class DripSnapshot(
    val bookId: String,
    val bookTitle: String,
    val shortTitle: String,
    val chapterLabel: String,
    val excerpt: String,
    val sub: String,
    val secondsLeft: Int,
)

/**
 * The nagging budget and dormancy logic (§9, §11). Automated nudges are capped at two
 * per day, never between 23:00 and 07:00, and the streak warning is suppressed if the
 * user has already read today.
 */
object Nudger {

    fun readToday(sessions: List<Session>): Boolean {
        val today = Time.today()
        return sessions.any { it.activeSeconds >= 60 && Stats.dateOf(it) == today }
    }

    /** Whole days since the last real reading day (0 if read today). */
    fun daysDormant(sessions: List<Session>): Int {
        val days = sessions.filter { it.activeSeconds >= 60 }.map { Stats.dateOf(it) }
        val last = days.maxOrNull() ?: return 99
        val today = Time.today()
        var d = 0
        var cursor = today
        while (cursor > last && d < 99) { cursor = cursor.minus(1, DateTimeUnit.DAY); d++ }
        return d
    }

    fun isQuietHour(hour: Int): Boolean = hour >= 23 || hour < 7

    /**
     * Whether an automated nudge may fire now, given the day's count and last time.
     * User-initiated drips (tapping the tile) bypass this.
     */
    fun mayNudge(hour: Int, nudgesToday: Int, sessions: List<Session>): Boolean {
        if (isQuietHour(hour)) return false
        if (nudgesToday >= 2) return false
        if (readToday(sessions)) return false
        return true
    }
}
