package com.nibble.app.feature.lines

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.LocalShareText
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.design.Kicker
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.tap

/** Lines the reader chose to keep. Tap a quote to reopen the book on that exact line (§7). */
@Composable
fun SavedLinesScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val share = LocalShareText.current
    val lines = state.savedLines.sortedByDescending { it.savedAt }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).tap { onEvent(Event.Back) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.inkFaint, modifier = Modifier.size(20.dp))
            }
            Text("Saved lines", style = type.display.copy(fontSize = 30.sp), color = c.ink)
        }
        Text(
            if (lines.isEmpty()) "Nothing kept yet. The heart in the reader saves the line you're on."
            else "Tap a line to fall back into the book at that exact spot.",
            color = c.inkFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        lines.forEach { line ->
            val book = state.bookOf(line.bookId)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(15.dp))
                    .background(c.card).border(1.dp, c.rule, RoundedCornerShape(15.dp))
                    .tap { if (book != null) onEvent(Event.OpenReaderAt(line.bookId, line.spineIndex, line.startChar)) }
                    .padding(14.dp),
            ) {
                Text("“${line.text}”", style = type.proseTight, color = c.ink)
                Spacer(Modifier.size(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker("${book?.shortTitle ?: "Removed book"} · Ch. ${line.spineIndex + 1}", Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Share, "Share line", tint = c.inkFaint,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                            .tap { share("“${line.text}” — ${book?.title ?: "a book"}, via Nibble") }
                            .padding(8.dp),
                    )
                    Icon(
                        Icons.Filled.Close, "Delete line", tint = c.inkFaint,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                            .tap { onEvent(Event.DeleteSavedLine(line.id)) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
