package com.nibble.app.core.data

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nibble.app.core.domain.Nudger
import com.nibble.app.core.domain.Time
import com.nibble.app.core.domain.Wit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The streak-warning surface (§9). Reads the shared snapshot (never queries the DB from
 * its own process), enforces the nagging budget (≤2/day, quiet hours, suppressed if read
 * today), picks an escalating dry line, and posts it.
 */
class StreakWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun doWork(): Result {
        val app = applicationContext
        val persistence = AndroidPersistence(app)
        val state = persistence.load()?.let { runCatching { json.decodeFromString<PersistedState>(it) }.getOrNull() }
            ?: return Result.success()
        if (!state.onboarded) return Result.success()

        val hour = Time.hour()
        val todayEpoch = Time.dayKey()
        val nudgesToday = if (state.nudgeDayEpoch == todayEpoch) state.nudgesToday else 0
        if (!Nudger.mayNudge(hour, nudgesToday, state.sessions)) return Result.success()

        val days = Nudger.daysDormant(state.sessions)
        val idx = (state.wittyRotation["nudge"] ?: -1) + 1
        val text = Wit.nudge(days, idx).resolve(state.voice, idx)
        val bookId = state.positions.maxByOrNull { it.value.updatedAt }?.key ?: "jekyll"

        Notifier.postStreakNudge(app, bookId, text)

        val updated = state.copy(
            wittyRotation = state.wittyRotation + ("nudge" to idx),
            nudgesToday = nudgesToday + 1,
            nudgeDayEpoch = todayEpoch,
            lastNudgeMillis = Time.nowMillis(),
        )
        runCatching { persistence.save(json.encodeToString(updated)) }
        return Result.success()
    }
}
