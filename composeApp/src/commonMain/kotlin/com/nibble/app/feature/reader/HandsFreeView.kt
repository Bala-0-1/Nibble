package com.nibble.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.audio.NarratorState
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.currentNibble
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.tap

/** 2k — Hybrid · voice reads, text keeps up. Always a dark surface. */
@Composable
fun HandsFreeView(state: AppState, onEvent: (Event) -> Unit, chapterText: String) {
    val type = LocalNibbleType.current
    val r = state.reader!!
    val nib = state.currentNibble()
    val bg = Color(0xFF1E1915)
    val dim = Color(0xFF6E655A)
    val bright = Color(0xFFFFF7EF)
    val gold = Color(0xFFD9A85C)
    val clay = Color(0xFFC97A56)

    // Show only the sentence the voice is on (like 2k), with the spoken portion bright.
    val spoken = r.spokenChar ?: (nib?.startChar ?: 0)
    val beat = nib?.beats?.firstOrNull { spoken in it } ?: nib?.beats?.firstOrNull()
    val sentence = if (beat != null) chapterText.substring(beat.first, (beat.last + 1).coerceAtMost(chapterText.length)).trim() else chapterText.take(160)
    val cutRel = if (beat != null) (spoken - beat.first).coerceIn(0, sentence.length) else 0
    val annotated: AnnotatedString = buildAnnotatedString {
        append(sentence)
        addStyle(SpanStyle(color = dim), 0, sentence.length)
        if (cutRel > 0) addStyle(SpanStyle(color = bright), 0, cutRel)
    }

    Column(
        Modifier.fillMaxSize().background(bg).windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Filled.KeyboardArrowDown, "Back to text", tint = Color(0xFFA99F90), modifier = Modifier.size(22.dp).tap { onEvent(Event.AudioBackToText) })
            Text("HANDS-FREE", color = Color(0xFFA99F90), style = type.kicker)
            Icon(Icons.Filled.DirectionsCar, null, tint = gold, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(annotated, style = type.prose.copy(fontSize = 22.sp, lineHeight = 35.sp))
        }

        // Waveform
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val heights = listOf(24, 52, 36, 78, 96, 62, 44, 70, 30, 56, 38, 66, 28, 48)
            heights.forEachIndexed { i, h ->
                val barColor = when {
                    i == 4 -> gold
                    i < 4 -> clay
                    i < 8 -> Color(0xFF5C5347)
                    else -> Color(0xFF3A342D)
                }
                Box(Modifier.weight(1f).fillMaxHeight(h / 100f).clip(RoundedCornerShape(2.dp)).background(barColor))
            }
        }
        Spacer(Modifier.size(16.dp))
        // Real numbers: elapsed session time; remaining ≈ chars left ÷ rate-adjusted pace.
        val remainingSecs = run {
            val charsLeft = (chapterText.length - spoken).coerceAtLeast(0)
            val wordsLeft = charsLeft / 5.7f
            (wordsLeft / (state.wpm.coerceAtLeast(1) * state.audioRate) * 60f).toInt()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(clock(r.sessionSeconds), color = Color(0xFFA99F90), fontSize = 11.5.sp)
            // Tap to cycle 0.8 → 1 → 1.25 → 1.5 → 2; the voice picks up the new pace mid-sentence.
            Text(
                "Narrated · ${rateLabel(state.audioRate)}×",
                color = bright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).tap { onEvent(Event.CycleAudioRate) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text("-${clock(remainingSecs)}", color = Color(0xFFA99F90), fontSize = 11.5.sp)
        }
        Spacer(Modifier.size(14.dp))
        // Sleep timer — the voice stops where it is; the page reopens on the same word.
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SLEEP", color = Color(0xFFA99F90), style = type.kicker)
            Spacer(Modifier.size(10.dp))
            listOf(null, 10, 20, 30).forEach { m ->
                val selected = r.sleepMinutes == m
                Text(
                    m?.toString() ?: "Off",
                    color = if (selected) Color(0xFF1E1915) else Color(0xFFA99F90),
                    fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(99.dp))
                        .background(if (selected) gold else Color(0x14F7F2E8))
                        .tap { onEvent(Event.SetSleepTimer(m)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.size(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircleControl(Icons.Filled.Refresh, Color(0x1AF7F2E8), Color(0xFFE4DCCF), 50) { onEvent(Event.RestartSentence) }
            Spacer(Modifier.size(24.dp))
            // Big play/pause with hard edge
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0xFF8A5238)))
                Box(
                    Modifier.size(70.dp).padding(bottom = 5.dp).clip(CircleShape).background(clay).tap { onEvent(Event.AudioPlayPause) },
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = if (r.audio == NarratorState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause
                    Icon(icon, "Play/Pause", tint = Color(0xFFFFF7EF), modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.size(24.dp))
            CircleControl(Icons.Filled.Visibility, Color(0x1AF7F2E8), Color(0xFFE4DCCF), 50) { onEvent(Event.AudioBackToText) }
        }
        Spacer(Modifier.size(16.dp))
        Text("Stop anywhere — the page opens on the same word.", color = Color(0xFFA99F90), fontSize = 11.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun CircleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, tint: Color, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(bg).tap { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
}

private fun clock(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

private fun rateLabel(rate: Float): String =
    if (rate == rate.toInt().toFloat()) "${rate.toInt()}" else "$rate"
