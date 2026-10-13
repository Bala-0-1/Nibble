package com.nibble.app.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.design.Kicker
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.Pill
import com.nibble.app.core.domain.Copy
import com.nibble.app.core.domain.Stats
import com.nibble.app.core.domain.Time
import com.nibble.app.core.domain.Wit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** 2g — Damage report · progress. Dry-equivalence register (§11). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DamageReportScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val stats = Stats.month(state.sessions)
    val daily = Stats.dailySeconds(state.sessions)
    val maxDaySecs = (daily.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val today = Time.today()
    val last14 = (13 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
    val monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        Text(Copy.statsTitle, style = type.display.copy(fontSize = 30.sp), color = c.ink)
        Text("$monthName so far.", color = c.inkFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))

        // Streak card (Moss)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.moss).padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${state.streak}", style = type.display.copy(fontSize = 52.sp, lineHeight = 52.sp), color = c.onMoss)
                Text("days in a row", color = c.onMoss.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(Wit.statsBoast.resolve(state.voice, remember { kotlin.random.Random.nextInt(99) }), color = c.onMoss.copy(alpha = 0.85f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.size(16.dp))
            // 14-day heat strip, derived from the last 14 days of sessions.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                last14.forEach { date ->
                    val secs = daily[date]
                    val cell = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                    when {
                        secs == null && date == today ->
                            Box(cell.border(1.dp, c.onMoss.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
                        secs == null ->
                            Box(cell.background(Color(0x24FFFFFF)))
                        else -> {
                            val ratio = secs.toFloat() / maxDaySecs
                            val color = if (ratio > 0.9f) c.gold else c.mossLeaf.copy(alpha = 0.4f + 0.6f * ratio)
                            Box(cell.background(color))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(16.dp))

        // Two stat tiles (derived)
        val timeRead = Stats.formatDuration(stats.totalActiveSeconds)
        val prevMonthName = today.minus(1, DateTimeUnit.MONTH).month.name.lowercase().replaceFirstChar { it.uppercase() }
        val foot = if (stats.priorMonthSeconds > 0)
            "↑ ${Stats.formatDuration(stats.totalActiveSeconds - stats.priorMonthSeconds)} on $prevMonthName"
        else "Your first month"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile("Time read", timeRead, foot, c.moss, Modifier.weight(1f))
            InfoTile("Screen time saved", timeRead, Wit.screenTimeSaved.resolve(state.voice, remember { kotlin.random.Random.nextInt(99) }), c.inkFaint, Modifier.weight(1f))
        }
        Spacer(Modifier.size(16.dp))

        Kicker("When you actually read")
        Spacer(Modifier.size(9.dp))
        Row(
            Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.rule, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Derived from the time-of-day distribution of real sessions.
            stats.histogram.forEach { h ->
                val col = when {
                    h > 0.85f -> c.clay
                    h > 0.6f -> if (c.isDark) Color(0xFFA06B50) else Color(0xFFC99B82)
                    h > 0.33f -> if (c.isDark) Color(0xFF6B4A3A) else Color(0xFFDBBBA8)
                    else -> if (c.isDark) Color(0xFF3B2E26) else Color(0xFFE8D6CA)
                }
                Box(Modifier.weight(1f).fillMaxHeight(h.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(col))
            }
        }
        Spacer(Modifier.size(16.dp))

        Kicker("Badges, of a sort")
        Spacer(Modifier.size(9.dp))
        // Real badges, derived from real sessions and finished books — never stored counters.
        val badges = com.nibble.app.core.domain.Badges.derive(state.sessions, state.finishedBookIds.size)
        if (badges.isEmpty()) {
            Text(
                "None yet. Badges are earned here, tragically.",
                color = c.inkFaint, fontSize = 12.5.sp,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                badges.forEach { b ->
                    when (b.tone) {
                        com.nibble.app.core.domain.Badges.BadgeTone.CLAY ->
                            Pill(b.label, c.clayWash, if (c.isDark) Color(0xFFD9A188) else c.clayPress, bold = true)
                        com.nibble.app.core.domain.Badges.BadgeTone.MOSS ->
                            Pill(b.label, if (c.isDark) Color(0xFF1A2C24) else Color(0xFFE7EFE8), if (c.isDark) c.mossLeaf else c.moss, bold = true)
                        com.nibble.app.core.domain.Badges.BadgeTone.PAPER ->
                            Pill(b.label, if (c.isDark) Color(0xFF2A241D) else Color(0xFFEBE3D3), c.inkMuted)
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun InfoTile(label: String, value: String, foot: String, footColor: Color, modifier: Modifier) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.rule, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(label, color = c.inkFaint, fontSize = 11.sp)
        Text(value, style = type.display.copy(fontSize = 28.sp, lineHeight = 28.sp), color = c.ink, modifier = Modifier.padding(top = 4.dp))
        Text(foot, color = footColor, fontSize = 11.sp, fontWeight = if (footColor == c.moss) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 5.dp))
    }
}
