package com.nibble.app.core.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Real device clock. Streaks are computed in the device's current zone (§10). */
object Time {
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
    fun zone(): TimeZone = TimeZone.currentSystemDefault()
    fun today(): LocalDate = Clock.System.now().toLocalDateTime(zone()).date
    fun hour(): Int = Clock.System.now().toLocalDateTime(zone()).hour
    /** A stable per-day integer key (for "already counted today" checks). */
    fun dayKey(): Int = today().let { it.year * 1000 + it.dayOfYear }
}
