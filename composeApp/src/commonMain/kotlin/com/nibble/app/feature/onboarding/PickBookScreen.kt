package com.nibble.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
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
import com.nibble.app.core.design.BookCover
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.SegmentedControl
import com.nibble.app.core.design.tap

private data class CoverCard(
    val id: String,
    val title: String,
    val author: String,
    val ground: Long,
    val accent: Long,
    val onColor: Long,
    val bordered: Boolean,
    val caption: String,
    val short: String,
)

/** 2b — Onboarding · pick your poison. */
@Composable
fun PickBookScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current

    val cards = listOf(
        CoverCard("jekyll", "Dr Jekyll and Mr Hyde", "R. L. Stevenson", 0xFF221E1A, 0xFFD9A85C, 0xFFF7F2E8, false, "64 nibbles · 2h 8m", "Jekyll & Hyde"),
        CoverCard("dorian", "The Picture of Dorian Gray", "Oscar Wilde", 0xFF3C5C4E, 0xFFBFD3C6, 0xFFF2F6F1, false, "121 nibbles · 4h 2m", "Dorian Gray"),
        CoverCard("time-machine", "The Time Machine", "H. G. Wells", 0xFFA96A4E, 0xFFF6ECE4, 0xFFF6ECE4, false, "58 nibbles · 1h 54m", "The Time Machine"),
        CoverCard("metamorphosis", "The Metamorphosis", "Franz Kafka", 0xFFEFE6D2, 0xFF221E1A, 0xFF221E1A, true, "37 nibbles · 1h 12m", "The Metamorphosis"),
    )
    val selectedId = state.selectedBookId ?: "jekyll"
    val selectedShort = cards.firstOrNull { it.id == selectedId }?.short ?: "Jekyll & Hyde"

    Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
        // Header: back, progress dots, step count
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.ink, modifier = Modifier.size(20.dp).tap { onEvent(Event.Back) })
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { i ->
                    Box(Modifier.width(24.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < 2) c.clay else c.rule))
                }
            }
            Text("2 / 3", color = c.inkFaint, fontSize = 12.5.sp)
        }
        Spacer(Modifier.size(16.dp))
        Text("Pick one. Just one.", style = type.display.copy(fontSize = 29.sp, lineHeight = 32.sp), color = c.ink)
        Spacer(Modifier.size(5.dp))
        Text(com.nibble.app.core.domain.Wit.pickSub.resolve(state.voice, androidx.compose.runtime.remember { kotlin.random.Random.nextInt(99) }), color = c.inkMuted, fontSize = 13.5.sp, style = type.body)
        Spacer(Modifier.size(16.dp))
        SegmentedControl(listOf("Curated", "My books · 14"), selectedIndex = 0, onSelect = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(16.dp))

        // 2x2 grid (scrolls if the covers don't fit, so the button stays pinned)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            cards.chunked(2).forEach { rowCards ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowCards.forEach { card ->
                        Column(Modifier.weight(1f)) {
                            Box {
                                val coverMod = if (card.id == selectedId) {
                                    Modifier.border(3.dp, c.clay, RoundedCornerShape(14.dp)).padding(3.dp)
                                } else Modifier
                                Box(coverMod.tap { onEvent(Event.SelectBook(card.id)) }) {
                                    val cover = Modifier.fillMaxWidth().let {
                                        if (card.bordered) it.border(1.dp, c.rule, RoundedCornerShape(12.dp)) else it
                                    }
                                    BookCover(
                                        title = card.title,
                                        author = card.author,
                                        ground = Color(card.ground),
                                        accent = Color(card.accent),
                                        onColor = Color(card.onColor),
                                        modifier = cover,
                                    )
                                }
                                if (card.id == selectedId) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                                            .size(26.dp).clip(CircleShape).background(c.clay)
                                            .border(2.dp, c.paper, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.Check, null, tint = c.onClay, modifier = Modifier.size(12.dp)) }
                                }
                            }
                            Text(card.caption, color = c.inkFaint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        ClayButton(
            "Start with $selectedShort",
            onClick = { onEvent(Event.StartWithBook) },
            trailing = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}
