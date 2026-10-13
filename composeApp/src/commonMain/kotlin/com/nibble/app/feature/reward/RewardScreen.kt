package com.nibble.app.feature.reward

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.currentNibble
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.CookieIcon
import com.nibble.app.core.design.Kicker
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.Pill
import com.nibble.app.core.design.QuietButton
import com.nibble.app.core.domain.Copy
import com.nibble.app.core.domain.Wit

/** 2f — Nibble finished · the reward. Faint-praise register (§11). */
@Composable
fun RewardScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val session = state.sessions.firstOrNull { it.id == state.lastSessionId }
    val seconds = session?.activeSeconds ?: 0
    val words = session?.words ?: 0
    val timeLabel = "${seconds / 60} min ${seconds % 60} s"
    val total = state.nibbleCount(session?.bookId).coerceAtLeast(1)
    val nib = state.currentNibble()
    val curIdx = nib?.index ?: 0
    val pct = (((curIdx + 1) * 100) / total).coerceIn(1, 100)
    val cliffhanger = nib?.endsOnCliffhanger ?: false
    // Stop-point recap = the actual last sentences the reader read (§12 fallback).
    val stoppedText = run {
        val chapter = state.bookOf(session?.bookId)?.spine?.firstOrNull { it.index == (nib?.spineIndex ?: -1) }
        fun beatsText(n: Int): String = nib!!.beats.takeLast(n).joinToString(" ") { r ->
            chapter!!.plainText.substring(r.first.coerceIn(0, chapter.plainText.length), (r.last + 1).coerceIn(0, chapter.plainText.length))
        }.trim()
        if (nib != null && chapter != null && nib.beats.isNotEmpty()) {
            // Prefer the last two sentences; fall back to the last one if that's too long.
            val two = beatsText(2)
            if (two.length <= 240) two else beatsText(1)
        } else Copy.whereYouStopped
    }
    val nextTeaser = run {
        val nibs = state.nibbles(session?.bookId)
        val next = nibs.firstOrNull { it.index == curIdx + 1 }
        val nextChapter = next?.let { nn -> state.bookOf(session?.bookId)?.spine?.firstOrNull { it.index == nn.spineIndex } }
        when {
            next == null -> "You finished the book"
            next.spineIndex != nib?.spineIndex && nextChapter != null -> "Next: ${nextChapter.title}"
            else -> "Next: it continues"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)) {
        // Medallion + title
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(c.clayPress))
                Box(
                    Modifier.size(80.dp).padding(bottom = 6.dp).clip(CircleShape).background(c.clay),
                    contentAlignment = Alignment.Center,
                ) { CookieIcon(c.onClay, Modifier.size(36.dp)) }
            }
            Spacer(Modifier.size(16.dp))
            Text(Copy.rewardTitle, style = type.display, color = c.ink)
            Spacer(Modifier.size(8.dp))
            Text(
                Wit.rewardPraise.resolve(state.voice, state.rewardPraiseIndex).replaceFirst("%s", timeLabel),
                color = c.inkMuted, fontSize = 14.sp, style = type.body,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.size(20.dp))

        // Stat tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatTile("${state.streak}", "day streak", moss = true, modifier = Modifier.weight(1f))
            StatTile("$pct%", "of the book", moss = false, modifier = Modifier.weight(1f))
            StatTile("$words", "words eaten", moss = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.size(16.dp))

        // Where you stopped
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).border(1.dp, c.rule, RoundedCornerShape(18.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.FormatQuote, null, tint = c.clay, modifier = Modifier.size(15.dp))
                Kicker("Where you stopped")
            }
            Spacer(Modifier.size(9.dp))
            Text(stoppedText, style = type.proseTight, color = c.ink)
            Spacer(Modifier.size(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (cliffhanger) Pill("Cliffhanger", c.clayWash, if (c.isDark) Color(0xFFD9A188) else c.clayPress, bold = true)
                if (state.todaySeconds >= state.dailyGoalMinutes * 60) {
                    Pill("Daily goal met", if (c.isDark) Color(0xFF1A2C24) else Color(0xFFE7EFE8), if (c.isDark) c.mossLeaf else c.moss, bold = true)
                }
                Pill(nextTeaser, if (c.isDark) Color(0xFF2A241D) else Color(0xFFEBE3D3), c.inkMuted)
            }
        }
        Spacer(Modifier.weight(1f))

        ClayButton(Copy.oneMore, onClick = { onEvent(Event.OneMore) }, trailing = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
        Spacer(Modifier.size(6.dp))
        QuietButton(Wit.decline.resolve(state.voice, state.declineIndex), onClick = { onEvent(Event.DeclineMore) })
    }
}

@Composable
private fun StatTile(value: String, label: String, moss: Boolean, modifier: Modifier) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val bg = if (moss) c.moss else c.card
    val base = modifier.clip(RoundedCornerShape(16.dp)).background(bg)
    val withBorder = if (moss) base else base.border(1.dp, c.rule, RoundedCornerShape(16.dp))
    Column(withBorder.padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(value, style = type.display.copy(fontSize = 28.sp, lineHeight = 28.sp), color = if (moss) c.onMoss else c.ink)
        Text(label, color = if (moss) c.onMoss.copy(alpha = 0.8f) else c.inkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
