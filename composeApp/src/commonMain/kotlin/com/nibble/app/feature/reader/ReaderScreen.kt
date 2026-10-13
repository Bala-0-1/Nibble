package com.nibble.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.audio.NarratorState
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.currentNibble
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.ProseText
import com.nibble.app.core.design.NibbleProgressBar
import com.nibble.app.core.design.tap
import com.nibble.app.core.domain.Copy
import com.nibble.app.core.domain.Nibble
import com.nibble.app.core.domain.ReaderMode

@Composable
fun ReaderScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val r = state.reader ?: return
    val book = state.bookOf(r.bookId) ?: return
    val pos = state.position(r.bookId)
    val spineIndex = pos?.spineIndex ?: book.spine.first().index
    val chapter = book.spine.firstOrNull { it.index == spineIndex } ?: book.spine.first()

    Box(Modifier.fillMaxSize().background(c.paperSunk)) {
        val audioActive = r.audio == NarratorState.SPEAKING || r.audio == NarratorState.PAUSED
        when {
            audioActive -> HandsFreeView(state, onEvent, chapter.plainText)
            else -> when (r.mode) {
                ReaderMode.NIBBLES -> NibbleView(state, onEvent, chapter.plainText, chapter.images)
                ReaderMode.CHAPTER -> ChapterView(state, onEvent, chapter.title, chapter.plainText, chapter.images)
                ReaderMode.FEED -> FeedView(state, onEvent, chapter.plainText)
            }
        }
        if (r.controlsOpen) {
            ReaderControlsSheet(state, onEvent)
        }
    }
}

private fun mmss(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** An anchored illustration: full width, alt text as caption and for screen readers. */
@Composable
private fun Illustration(anchor: com.nibble.app.core.domain.ImageAnchor, modifier: Modifier = Modifier) {
    val c = LocalNibbleColors.current
    val image = com.nibble.app.core.design.rememberBookImage(anchor.path) ?: return
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = anchor.alt.ifBlank { "Illustration" },
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        )
        if (anchor.alt.isNotBlank()) {
            Text(anchor.alt, color = c.inkFaint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 2d — Reader · the nibble card. */
@Composable
private fun NibbleView(
    state: AppState,
    onEvent: (Event) -> Unit,
    chapterText: String,
    chapterImages: List<com.nibble.app.core.domain.ImageAnchor> = emptyList(),
) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val r = state.reader!!
    val nibs = state.nibbles(r.bookId)
    val nib = state.currentNibble() ?: nibs.firstOrNull() ?: return
    val total = nibs.size

    val text = chapterText.substring(nib.startChar, nib.endChar).trim()
    val beatsRel = nib.beats.map { (it.first - nib.startChar).coerceAtLeast(0)..(it.last - nib.startChar).coerceAtMost(text.length - 1) }
    val cb = r.currentBeat.coerceIn(0, (beatsRel.size - 1).coerceAtLeast(0))
    val marker = beatsRel.getOrNull(cb)
    val dimFrom = marker?.let { it.last + 1 }

    val remainingFrac = 1f - (cb + 1f) / beatsRel.size.coerceAtLeast(1)
    val secondsLeft = (nib.wordCount * remainingFrac / state.wpm.coerceAtLeast(1) * 60f).toInt().coerceAtLeast(0)
    // Heart state for the beat under the marker — saved lines are keyed by beat start (§10).
    val beatAbs = nib.beats.getOrNull(cb)
    val lineSaved = beatAbs != null && state.isLineSaved(r.bookId, nib.spineIndex, beatAbs.first)

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).tap { onEvent(Event.CloseReader) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Close reader", tint = c.inkFaint, modifier = Modifier.size(20.dp))
            }
            NibbleProgressBar(total = total, current = nib.index)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap { onEvent(Event.SaveCurrentLine) }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (lineSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (lineSaved) "Un-save this line" else "Save this line",
                        tint = if (lineSaved) c.clay else c.inkFaint, modifier = Modifier.size(20.dp),
                    )
                }
                Box(Modifier.size(48.dp).tap { onEvent(Event.OpenControls) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Tune, "Reading controls", tint = c.inkFaint, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.size(18.dp))
        Text("${book(state)} · Ch. ${nib.spineIndex + 1}".uppercase(), color = c.inkFaint, style = type.kicker)
        Spacer(Modifier.size(16.dp))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column {
                // On-device "previously" recap when resuming mid-book (§12).
                r.recap?.let {
                    com.nibble.app.core.design.RecapCallout("Previously", it)
                    Spacer(Modifier.size(14.dp))
                }
                ProseText(
                    text = text,
                    style = type.prose.copy(fontSize = (22 * state.textScale).sp, lineHeight = (35 * state.textScale).sp),
                    markerRange = marker,
                    dimFrom = dimFrom,
                    // Screen readers get the whole nibble as one block with its position (§13).
                    modifier = Modifier.fillMaxWidth()
                        .semantics(mergeDescendants = true) { contentDescription = "Nibble ${nib.index + 1} of $total. $text" }
                        .tap { onEvent(Event.TapAdvanceBeat) },
                )
                // Illustrations whose anchor falls inside this nibble's range.
                chapterImages.filter { it.charOffset >= nib.startChar && it.charOffset < nib.endChar }.forEach { anchor ->
                    Spacer(Modifier.size(14.dp))
                    Illustration(anchor)
                }
                // On-device "Who?" gloss for a character introduced earlier; hidden if none.
                r.gloss?.let {
                    Spacer(Modifier.size(14.dp))
                    com.nibble.app.core.design.RecapCallout("Who?", it)
                }
            }
        }
        Spacer(Modifier.size(14.dp))
        val isLast = nib.index >= total - 1
        Column(
            Modifier.fillMaxWidth()
                .pointerInput(nib.index) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { _, delta -> dy += delta },
                        onDragEnd = {
                            if (dy < -60f) onEvent(Event.NextNibble)
                            else if (dy > 60f) onEvent(Event.PrevNibble)
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Overall book progress.
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (c.isDark) Color(0xFF2E271F) else Color(0xFFE2D8C5))) {
                Box(Modifier.fillMaxWidth(((nib.index + 1f) / total.coerceAtLeast(1))).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.clay))
            }
            // Clear navigation: Previous · counter · Next.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (nib.index > 0) {
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp)).tap { onEvent(Event.PrevNibble) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                        Text("Previous", color = c.inkMuted, fontSize = 13.sp)
                    }
                } else {
                    Box(Modifier.width(90.dp))
                }
                Text("${nib.index + 1} of $total  ·  ${mmss(secondsLeft)} left", color = c.inkFaint, fontSize = 11.5.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                ClayButton(
                    if (isLast) "Finish" else "Next",
                    onClick = { onEvent(Event.NextNibble) },
                    trailing = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.width(132.dp),
                    faceHeight = 46,
                    fontSize = 15,
                )
            }
        }
    }
}

private fun book(state: AppState): String = state.bookOf(state.reader?.bookId)?.shortTitle ?: "Jekyll & Hyde"

/** 2i — Classic · full chapter, no chrome. */
@Composable
private fun ChapterView(
    state: AppState,
    onEvent: (Event) -> Unit,
    title: String,
    chapterText: String,
    chapterImages: List<com.nibble.app.core.domain.ImageAnchor> = emptyList(),
) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val r = state.reader!!
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 30.dp, vertical = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(title, style = type.title.copy(fontSize = 25.sp, lineHeight = 28.sp), color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Tune, "Controls", tint = c.inkFaint, modifier = Modifier.size(20.dp).tap { onEvent(Event.OpenControls) })
        }
        Box(Modifier.width(44.dp).height(3.dp).background(c.clay).padding(top = 0.dp))
        Spacer(Modifier.size(18.dp))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column {
                // Paragraphs are joined with "\n\n" at ingest, so offsets reconstruct exactly;
                // each illustration renders at the paragraph boundary its anchor names.
                var runningOffset = 0
                chapterText.split("\n\n").forEach { para ->
                    val start = runningOffset
                    runningOffset += para.length + 2
                    chapterImages.filter { it.charOffset in start until runningOffset }.forEach { anchor ->
                        Illustration(anchor, Modifier.padding(bottom = 18.dp))
                    }
                    Text(
                        para.trim(),
                        style = type.prose.copy(fontSize = (19.5 * state.textScale).sp, lineHeight = (33 * state.textScale).sp),
                        color = c.ink,
                        modifier = Modifier.padding(bottom = 18.dp),
                    )
                }
            }
        }
        // Chapter navigation.
        val book = state.bookOf(r.bookId)
        val pos = state.position(r.bookId)
        val ordinal = book?.spine?.indexOfFirst { it.index == pos?.spineIndex }?.coerceAtLeast(0) ?: 0
        val chapters = book?.spine?.size ?: 1
        Spacer(Modifier.size(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (c.isDark) Color(0xFF2E271F) else Color(0xFFE2D8C5))) {
            Box(Modifier.fillMaxWidth(((ordinal + 1f) / chapters.coerceAtLeast(1))).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.clay))
        }
        Spacer(Modifier.size(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ordinal > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp)).tap { onEvent(Event.PrevChapter) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                    Text("Previous", color = c.inkMuted, fontSize = 13.sp)
                }
            } else {
                Box(Modifier.width(90.dp))
            }
            Text("Chapter ${ordinal + 1} of $chapters", color = c.inkFaint, fontSize = 11.5.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (ordinal < chapters - 1) {
                ClayButton("Next chapter", onClick = { onEvent(Event.NextChapter) }, trailing = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.width(168.dp), faceHeight = 44, fontSize = 14)
            } else {
                Box(Modifier.width(90.dp))
            }
        }
    }
}

/** 2j — Feed · vertical scroll, one beat per card. */
@Composable
private fun FeedView(state: AppState, onEvent: (Event) -> Unit, chapterText: String) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val r = state.reader!!
    val nib = state.currentNibble() ?: return
    val beats = nib.beats
    val cb = r.currentBeat.coerceIn(0, beats.lastIndex)
    fun beatText(i: Int): String = if (i in beats.indices) chapterText.substring(beats[i].first, beats[i].last + 1).trim() else ""
    // Real per-beat estimate: this beat's share of the nibble at the learned rate.
    val beatWords = (nib.wordCount.toFloat() / beats.size.coerceAtLeast(1)).coerceAtLeast(1f)
    val beatSecs = (beatWords / state.wpm.coerceAtLeast(1) * 60f).toInt().coerceAtLeast(5)
    val beatSaved = state.isLineSaved(r.bookId, nib.spineIndex, beats[cb].first)
    var whoOpen by remember(cb) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // previous beat (faded)
        if (cb > 0) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.paperSunk.copy(alpha = 0.5f)).padding(16.dp)) {
                Text("…" + beatText(cb - 1).takeLast(70), style = type.proseTight.copy(fontSize = 18.sp), color = c.inkFaint)
            }
        }
        // current card
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BEAT ${cb + 1} OF ${beats.size}", color = c.clay, style = type.kicker)
                Text("$beatSecs sec", color = c.inkFaint, fontSize = 11.5.sp)
            }
            Spacer(Modifier.size(18.dp))
            Text(beatText(cb), style = type.prose.copy(fontSize = 22.sp, lineHeight = 34.sp), color = c.ink)
            if (whoOpen) {
                Spacer(Modifier.size(12.dp))
                com.nibble.app.core.design.RecapCallout(
                    "Who?",
                    r.gloss ?: "No one new here — everyone in this beat you've already met.",
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.tap { onEvent(Event.SaveCurrentLine) }) {
                    com.nibble.app.core.design.Pill(
                        if (beatSaved) "♥ Saved" else "♥ Save line",
                        if (beatSaved) c.clayWash else if (c.isDark) Color(0xFF2A241D) else Color(0xFFEBE3D3),
                        if (beatSaved) c.clay else c.inkMuted, bold = true,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Box(Modifier.tap { whoOpen = !whoOpen }) {
                    com.nibble.app.core.design.Pill("? Who's this", if (c.isDark) Color(0xFF2A241D) else Color(0xFFEBE3D3), c.inkMuted, bold = true)
                }
                Spacer(Modifier.weight(1f))
                Text("Next ↑", color = c.clay, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tap {
                    if (cb >= beats.lastIndex) onEvent(Event.NextNibble) else onEvent(Event.TapAdvanceBeat)
                })
            }
        }
        // next beat (blurred)
        if (cb < beats.lastIndex) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.paperSunk.copy(alpha = 0.35f)).padding(16.dp)) {
                Text(beatText(cb + 1).take(70), style = type.proseTight.copy(fontSize = 18.sp), color = c.inkFaint, modifier = Modifier.blur(2.5.dp))
            }
        }
    }
}
