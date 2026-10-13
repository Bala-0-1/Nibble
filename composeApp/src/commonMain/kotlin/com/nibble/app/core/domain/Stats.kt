package com.nibble.app.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Streaks and stats are DERIVED from real [Session] rows (§10), never stored as counters.
 * Days are computed in the device's current zone; a session's `startedAt` is epoch millis.
 */
object Stats {

    private val SLOT_STARTS = intArrayOf(5, 8, 11, 14, 17, 20, 23) // 7 time-of-day buckets

    fun dateOf(session: Session): LocalDate =
        Instant.fromEpochMilliseconds(session.startedAt).toLocalDateTime(Time.zone()).date

    fun hourOf(session: Session): Int =
        Instant.fromEpochMilliseconds(session.startedAt).toLocalDateTime(Time.zone()).hour

    /** Consecutive days (≥60s each) ending today or yesterday; otherwise the streak is broken. */
    fun streak(sessions: List<Session>): Int {
        val days = sessions.filter { it.activeSeconds >= 60 }.map { dateOf(it) }.toSortedSet()
        if (days.isEmpty()) return 0
        val today = Time.today()
        val last = days.last()
        if (last < today.minus(1, DateTimeUnit.DAY)) return 0
        var streak = 0
        var d = last
        while (d in days) { streak++; d = d.minus(1, DateTimeUnit.DAY) }
        return streak
    }

    /** The longest run of consecutive days (≥60s each) anywhere in history, not just now. */
    fun longestStreak(sessions: List<Session>): Int {
        val days = sessions.filter { it.activeSeconds >= 60 }.map { dateOf(it) }.toSortedSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && d == prev.plus(1, DateTimeUnit.DAY)) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    /** Seconds read today — the daily-goal progress source. */
    fun todaySeconds(sessions: List<Session>): Int =
        sessions.filter { dateOf(it) == Time.today() }.sumOf { it.activeSeconds }

    fun dailySeconds(sessions: List<Session>): Map<LocalDate, Int> =
        sessions.groupBy { dateOf(it) }.mapValues { (_, rows) -> rows.sumOf { it.activeSeconds } }

    data class MonthStats(
        val streak: Int,
        val totalActiveSeconds: Int,
        val sessionCount: Int,
        val totalWords: Int,
        val histogram: List<Float>,   // 7 buckets, normalised 0..1
        val priorMonthSeconds: Int,
    )

    fun month(sessions: List<Session>): MonthStats {
        val today = Time.today()
        val firstOfMonth = LocalDate(today.year, today.month, 1)
        val firstOfPrev = firstOfMonth.minus(1, DateTimeUnit.MONTH)
        val thisMonth = sessions.filter { dateOf(it) >= firstOfMonth }
        val prevMonth = sessions.filter { val d = dateOf(it); d >= firstOfPrev && d < firstOfMonth }

        val total = thisMonth.sumOf { it.activeSeconds }
        val buckets = IntArray(7)
        for (s in thisMonth) buckets[slotOf(hourOf(s))] += s.activeSeconds
        val max = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
        return MonthStats(
            streak = streak(sessions),
            totalActiveSeconds = total,
            sessionCount = thisMonth.size,
            totalWords = thisMonth.sumOf { it.words },
            histogram = buckets.map { it.toFloat() / max },
            priorMonthSeconds = prevMonth.sumOf { it.activeSeconds },
        )
    }

    private fun slotOf(hour: Int): Int {
        var slot = 0
        for (i in SLOT_STARTS.indices) if (hour >= SLOT_STARTS[i]) slot = i
        return slot
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
