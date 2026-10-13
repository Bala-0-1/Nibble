package com.nibble.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.Tab
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.Kicker
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.StreakPill
import com.nibble.app.core.design.tap
import com.nibble.app.core.domain.BookSearch
import com.nibble.app.LocalImportEpub
import com.nibble.app.LocalPostDrip
import com.nibble.app.LocalShareText
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** 2c — Home · today's nibble. */
@Composable
fun HomeTodayScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    // Today's nibble is derived from the primary book + its real resume point.
    val book = state.bookOf(state.selectedBookId) ?: state.books.first()
    val pos = state.position(book.id)
    val spineIndex = pos?.spineIndex ?: book.spine.firstOrNull()?.index ?: 0
    val chapter = book.spine.firstOrNull { it.index == spineIndex } ?: book.spine.firstOrNull()
    val total = state.nibbleCount(book.id)
    val resumeIdx = state.resumeIndex(book.id)
    val chapterLabel = chapter?.let { "Ch. ${it.index + 1} — ${it.title}" } ?: book.title
    val heroBg = if (c.isDark) Color(0xFF241E18) else Color(0xFF221E1A)
    val heroOn = Color(0xFFF7F2E8)
    val heroMuted = if (c.isDark) Color(0xFFA2957F) else Color(0xFFB3A99A)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
    ) {
        // Greeting + streak — greeting is real (time of day), subtitle reflects real streak.
        val hour = com.nibble.app.core.domain.Time.hour()
        val greeting = when {
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            hour < 22 -> "Good evening."
            else -> "Still up?"
        }
        val subtitle = if (state.streak == 0)
            com.nibble.app.core.domain.Wit.homeSubZero.resolve(state.voice, remember { kotlin.random.Random.nextInt(99) })
        else
            com.nibble.app.core.domain.Wit.homeSubStreak.resolve(state.voice, remember { kotlin.random.Random.nextInt(99) }).replace("%d", state.streak.toString())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(greeting, style = type.title, color = c.ink)
                Text(subtitle, color = c.inkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
            StreakPill(state.streak)
        }
        Spacer(Modifier.size(14.dp))

        // Daily goal — real minutes read today against the goal set in You.
        val goalSecs = state.dailyGoalMinutes * 60
        val todaySecs = state.todaySeconds
        val goalFrac = (todaySecs.toFloat() / goalSecs.coerceAtLeast(1)).coerceIn(0f, 1f)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                .border(1.dp, c.rule, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Today's goal", color = c.inkFaint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (todaySecs >= goalSecs) "Done. Suspiciously disciplined."
                        else "${todaySecs / 60} of ${state.dailyGoalMinutes} min",
                        color = if (todaySecs >= goalSecs) c.moss else c.inkMuted,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (c.isDark) Color(0xFF2E271F) else Color(0xFFEBE3D3))) {
                    if (goalFrac > 0f) {
                        Box(Modifier.fillMaxWidth(goalFrac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (todaySecs >= goalSecs) c.moss else c.clay))
                    }
                }
            }
        }
        Spacer(Modifier.size(18.dp))

        // Hero — today's nibble (dark feature card in both themes)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(heroBg)
                .then(if (c.isDark) Modifier.border(1.dp, Color(0xFF3A3026), RoundedCornerShape(20.dp)) else Modifier)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(c.clay))
                Text("TODAY'S NIBBLE", color = c.gold, style = type.kicker)
            }
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // mini cover — the real one when the book brought its own
                if (book.coverPath != null) {
                    com.nibble.app.core.design.CoverThumb(
                        imagePath = book.coverPath, ground = if (c.isDark) Color(0xFF3A2F25) else c.paper,
                        modifier = Modifier.width(76.dp).height(101.dp).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Column(
                        Modifier.width(76.dp).height(101.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (c.isDark) Color(0xFF3A2F25) else c.paper).padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.width(14.dp).height(2.dp).background(c.clay))
                        Text(book.shortTitle, style = type.title.copy(fontSize = 13.sp, lineHeight = 14.sp), color = if (c.isDark) c.paper else c.ink)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(chapterLabel, style = type.title.copy(fontSize = 20.sp, lineHeight = 23.sp), color = heroOn)
                    Spacer(Modifier.size(7.dp))
                    Text(book.blurb, color = heroMuted, fontSize = 12.5.sp, style = type.body)
                    Spacer(Modifier.size(11.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetaBit(Icons.Filled.Timer, "2 min", c.gold, heroMuted)
                        MetaBit(Icons.Filled.Layers, "${resumeIdx + 1} of $total", heroMuted, heroMuted)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ClayButton(
                    "Read 2 minutes",
                    onClick = { onEvent(Event.OpenReader(book.id)) },
                    leading = Icons.Filled.PlayArrow,
                    faceHeight = 52,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (c.isDark) Color(0xFF3A3026) else Color(0xFF37312A))
                        .tap { onEvent(Event.OpenReader(book.id)); onEvent(Event.HandoffToAudio) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Headphones, null, tint = c.gold, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.size(20.dp))

        // Sneak it in
        Kicker("Sneak it in")
        Spacer(Modifier.size(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val postDrip = LocalPostDrip.current
            val share = LocalShareText.current
            SneakTile(Icons.Filled.Smartphone, "Lockscreen drip", "Send one now", c.moss, Modifier.weight(1f), onClick = postDrip)
            SneakTile(Icons.Filled.WavingHand, "Share a streak", "Send a nudge", c.inkFaint, Modifier.weight(1f), onClick = {
                val streakBit = if (state.streak > 0) "${state.streak}-day reading streak" else "reading streak (day zero, but watch this space)"
                share("My $streakBit on Nibble — ${book.title}, two minutes at a time. Your move.")
            })
            SneakTile(Icons.Filled.DirectionsCar, "Hands-free", "Listen instead", c.inkFaint, Modifier.weight(1f), onClick = { onEvent(Event.OpenReader(book.id)); onEvent(Event.HandoffToAudio) })
        }
        Spacer(Modifier.size(20.dp))

        // Abandoned — real books you started, paused, and haven't finished.
        val abandoned = state.books.filter { b ->
            b.id != book.id && state.position(b.id) != null && !state.isFinished(b.id) &&
                state.resumeIndex(b.id) in 1 until state.nibbleCount(b.id).coerceAtLeast(1)
        }
        if (abandoned.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Kicker("Abandoned, but forgivable")
                Text("See all", color = c.clay, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.tap { onEvent(Event.SelectTab(Tab.SHELF)) })
            }
            Spacer(Modifier.size(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                abandoned.forEach { b ->
                    val pct = ((state.resumeIndex(b.id) + 1) * 100 / state.nibbleCount(b.id).coerceAtLeast(1)).coerceIn(1, 99)
                    AbandonedRow(b.shortTitle, "Paused · $pct%", pct, Color(b.coverGround), b.coverPath) { onEvent(Event.OpenReader(b.id)) }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun MetaBit(icon: ImageVector, text: String, iconTint: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
        Text(text, color = textColor, fontSize = 11.5.sp)
    }
}

@Composable
private fun SneakTile(icon: ImageVector, title: String, sub: String, subColor: Color, modifier: Modifier, onClick: () -> Unit = {}) {
    val c = LocalNibbleColors.current
    Column(
        modifier.defaultMinSize(minHeight = 96.dp).clip(RoundedCornerShape(15.dp))
            .background(c.card).border(1.dp, c.rule, RoundedCornerShape(15.dp)).tap(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = c.clay, modifier = Modifier.size(20.dp))
        Column {
            Text(title, color = c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
            Text(sub, color = subColor, fontSize = 10.5.sp, fontWeight = if (subColor == c.moss) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun AbandonedRow(title: String, label: String, percent: Int, ground: Color, coverPath: String?, onClick: () -> Unit) {
    val c = LocalNibbleColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(c.card)
            .border(1.dp, c.rule, RoundedCornerShape(15.dp)).tap(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.nibble.app.core.design.CoverThumb(
            imagePath = coverPath, ground = ground,
            modifier = Modifier.width(34.dp).height(48.dp).clip(RoundedCornerShape(5.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(label, color = c.inkFaint, fontSize = 11.sp)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (c.isDark) Color(0xFF2E271F) else Color(0xFFEBE3D3))) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.clay))
            }
        }
        Text("Catch me up", color = c.clay, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Shelf tab — the real library: search inside every book, import, saved lines. */
@Composable
fun ShelfScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    val importEpub = LocalImportEpub.current
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Your shelf", style = type.display.copy(fontSize = 30.sp), color = c.ink)
        Text("Everything you've started, and everything you keep meaning to.", color = c.inkFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

        // Full-text search across every book on the shelf.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp))
                .background(c.card).border(1.dp, c.rule, RoundedCornerShape(14.dp))
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = c.inkFaint, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search inside your books…", color = c.inkFaint, fontSize = 13.5.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = type.body.copy(fontSize = 13.5.sp, color = c.ink),
                    cursorBrush = SolidColor(c.clay),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(Icons.Filled.Close, "Clear search", tint = c.inkFaint, modifier = Modifier.size(18.dp).tap { query = "" })
            }
        }

        val hits = remember(query, state.books) { BookSearch.search(state.books, query) }
        if (query.trim().length >= 2) {
            if (hits.isEmpty()) {
                Text("No luck. The books have never heard of it.", color = c.inkFaint, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            hits.forEach { hit ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(13.dp)).background(c.card)
                        .border(1.dp, c.rule, RoundedCornerShape(13.dp))
                        .tap { onEvent(Event.OpenReaderAt(hit.bookId, hit.spineIndex, hit.charOffset)) }
                        .padding(12.dp),
                ) {
                    Kicker("${hit.bookTitle} · Ch. ${hit.spineIndex + 1}")
                    Spacer(Modifier.size(6.dp))
                    val snippet = buildAnnotatedString {
                        append("…${hit.snippet}…")
                        val s = (hit.matchStart + 1).coerceIn(0, hit.snippet.length + 1)
                        val e = (hit.matchEnd + 1).coerceIn(s, hit.snippet.length + 1)
                        if (e > s) addStyle(SpanStyle(background = c.marker, color = c.ink), s, e)
                    }
                    Text(snippet, style = type.proseTight.copy(fontSize = 14.sp, lineHeight = 21.sp), color = c.inkMuted)
                }
            }
            Spacer(Modifier.size(8.dp))
            return@Column   // searching replaces the shelf list until cleared
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                .background(c.clayWash).tap { importEpub() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(Icons.Filled.Add, null, tint = c.clay, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("Import a book", color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text("Add an EPUB from your device.", color = c.inkMuted, fontSize = 12.sp)
            }
        }

        // Saved lines — the margin you actually kept.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(14.dp))
                .background(c.card).border(1.dp, c.rule, RoundedCornerShape(14.dp))
                .tap { onEvent(Event.OpenSavedLines) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(Icons.Filled.Favorite, null, tint = c.clay, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Saved lines", color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.savedLines.isEmpty()) "Keep the sentences worth keeping."
                    else "${state.savedLines.size} kept so far.",
                    color = c.inkMuted, fontSize = 12.sp,
                )
            }
        }
        state.importError?.let { err ->
            Text(err, color = c.clayPress, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        state.books.forEach { b ->
            val total = state.nibbleCount(b.id)
            val started = state.position(b.id) != null
            val finished = state.isFinished(b.id)
            val idx = state.resumeIndex(b.id)
            val imported = b.source == com.nibble.app.core.domain.BookSource.IMPORT
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(15.dp)).background(c.card)
                    .border(1.dp, c.rule, RoundedCornerShape(15.dp)).tap { onEvent(Event.OpenReader(b.id)) }.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.nibble.app.core.design.CoverThumb(
                    imagePath = b.coverPath, ground = Color(b.coverGround),
                    modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(b.shortTitle, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(b.author, color = c.inkFaint, fontSize = 11.5.sp)
                    val meta = when {
                        finished -> "Finished. All $total nibbles. Showing off."
                        started -> "$total nibbles · ${idx + 1} of $total read"
                        else -> "$total nibbles · ${state.durationLabel(b.id)}"
                    }
                    Text(meta, color = if (finished) c.moss else c.inkMuted, fontSize = 11.5.sp, fontWeight = if (finished) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
                }
                if (imported) {
                    Icon(
                        Icons.Filled.Close, "Remove book", tint = c.inkFaint,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .tap { onEvent(Event.RemoveBook(b.id)) }.padding(11.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun YouScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("You", style = type.display.copy(fontSize = 30.sp), color = c.ink)
        Spacer(Modifier.size(18.dp))
        SettingRow("Theme")
        ThemePicker(state, onEvent)
        Spacer(Modifier.size(14.dp))
        SettingRow("Voice")
        VoicePicker(state, onEvent)
        Spacer(Modifier.size(14.dp))
        SettingRow("Daily goal")
        GoalPicker(state, onEvent)
        val c2 = LocalNibbleColors.current
        Text(
            "Minutes a day before the app stops giving you looks.",
            color = c2.inkFaint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.size(18.dp))
        SettingRow("Account")
        AccountSection(state, onEvent)
    }
}

/** Signed out: an invitation, not a wall. Signed in: who you are, and the way out. */
@Composable
private fun AccountSection(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val account = state.account
    if (account == null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                .border(1.dp, c.rule, RoundedCornerShape(14.dp))
                .tap { onEvent(Event.OpenAuth) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(Icons.Filled.Person, null, tint = c.clay, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Sign in or create account", color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text("Same book, same page, on the web too.", color = c.inkMuted, fontSize = 12.sp)
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                .border(1.dp, c.rule, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Icon(Icons.Filled.Person, null, tint = c.moss, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.email, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text("Signed in. Sync arrives with the web app.", color = c.inkMuted, fontSize = 12.sp)
                }
            }
            com.nibble.app.core.design.QuietButton("Sign out", onClick = { onEvent(Event.SignOut) })
        }
    }
}

private val GOAL_OPTIONS = listOf(5, 10, 15, 20)

@Composable
private fun GoalPicker(state: AppState, onEvent: (Event) -> Unit) {
    com.nibble.app.core.design.SegmentedControl(
        options = GOAL_OPTIONS.map { "$it min" },
        selectedIndex = GOAL_OPTIONS.indexOf(state.dailyGoalMinutes).coerceAtLeast(0),
        onSelect = { onEvent(Event.SetDailyGoal(GOAL_OPTIONS[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingRow(label: String) {
    Kicker(label, Modifier.padding(bottom = 10.dp))
}

@Composable
private fun ThemePicker(state: AppState, onEvent: (Event) -> Unit) {
    com.nibble.app.core.design.SegmentedControl(
        options = listOf("System", "Light", "Dark", "Sunset"),
        selectedIndex = state.theme.ordinal,
        onSelect = { onEvent(Event.SetTheme(com.nibble.app.core.domain.ThemeChoice.entries[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VoicePicker(state: AppState, onEvent: (Event) -> Unit) {
    com.nibble.app.core.design.SegmentedControl(
        options = listOf("Dry", "Neutral"),
        selectedIndex = state.voice.ordinal,
        onSelect = { onEvent(Event.SetVoice(com.nibble.app.core.domain.Voice.entries[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}
