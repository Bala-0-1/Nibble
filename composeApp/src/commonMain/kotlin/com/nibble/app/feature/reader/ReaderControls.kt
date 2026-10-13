package com.nibble.app.feature.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.nibble.app.core.design.SegmentedControl
import com.nibble.app.core.design.tap
import com.nibble.app.core.domain.ReaderMode

/** 2e — Reader · controls + audio handoff (bottom sheet). */
@Composable
fun ReaderControlsSheet(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val r = state.reader ?: return

    Box(Modifier.fillMaxSize()) {
        // Scrim
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).tap { onEvent(Event.CloseControls) })
        // Sheet
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.card)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.rule))
            Spacer(Modifier.size(18.dp))

            // Hand off to audio
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.ink)
                    .tap { onEvent(Event.HandoffToAudio) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.clay), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Headphones, null, tint = c.onClay, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Hand off to audio", color = c.paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Picks up mid-sentence. Comes back where the voice stopped.", color = c.paper.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.size(20.dp))

            Kicker("Text size")
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A", style = type.title.copy(fontSize = 14.sp), color = c.inkFaint)
                Slider(
                    value = (state.textScale - 0.8f) / 0.8f,
                    onValueChange = { onEvent(Event.SetTextScale(0.8f + it * 0.8f)) },
                    colors = SliderDefaults.colors(
                        thumbColor = c.clay,
                        activeTrackColor = c.clay,
                        inactiveTrackColor = if (c.isDark) Color(0xFF332C24) else Color(0xFFEBE3D3),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text("A", style = type.title.copy(fontSize = 24.sp), color = c.ink)
            }
            Spacer(Modifier.size(12.dp))

            Kicker("Mode")
            Spacer(Modifier.size(6.dp))
            SegmentedControl(
                options = listOf("Nibbles", "Full chapter", "Feed"),
                selectedIndex = r.mode.ordinal,
                onSelect = { onEvent(Event.SetMode(ReaderMode.entries[it])); onEvent(Event.CloseControls) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(16.dp))

            // Chapters — jump anywhere; the position stays canonical (§7).
            val book = state.bookOf(r.bookId)
            val currentSpine = state.position(r.bookId)?.spineIndex
            if (book != null && book.spine.size > 1) {
                Kicker("Chapters")
                Spacer(Modifier.size(6.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 190.dp)
                        .clip(RoundedCornerShape(14.dp)).background(c.paperSunk)
                        .verticalScroll(rememberScrollState()),
                ) {
                    book.spine.forEachIndexed { ordinal, ch ->
                        val here = ch.index == currentSpine
                        Row(
                            Modifier.fillMaxWidth().tap { onEvent(Event.GoToChapter(ch.index)) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${ordinal + 1}", color = if (here) c.clay else c.inkFaint,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp),
                            )
                            Text(
                                ch.title, color = if (here) c.ink else c.inkMuted, fontSize = 13.5.sp,
                                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (here) Box(Modifier.size(6.dp).clip(CircleShape).background(c.clay))
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
            }

            // Nag row
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(c.paperSunk).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = c.clay, modifier = Modifier.size(18.dp))
                    Text("Nag me at ${state.nagTime}", color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                }
                NibbleToggle(on = state.nagEnabled) { onEvent(Event.ToggleNag) }
            }
            Spacer(Modifier.size(6.dp))
        }
    }
}

@Composable
fun NibbleToggle(on: Boolean, onToggle: () -> Unit) {
    val c = LocalNibbleColors.current
    val toggleTarget = if (on) 23.dp else 3.dp
    val toggleAnim by animateDpAsState(toggleTarget)
    val thumbX = if (com.nibble.app.LocalReduceMotion.current) toggleTarget else toggleAnim
    Box(
        Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(99.dp))
            .background(if (on) c.moss else c.rule).tap { onToggle() },
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = thumbX).size(22.dp).clip(CircleShape)
                .background(if (on) Color(0xFFCFE6D8) else c.card),
        )
    }
}
