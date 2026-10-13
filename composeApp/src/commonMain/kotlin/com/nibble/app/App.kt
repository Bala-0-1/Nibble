package com.nibble.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nibble.app.core.audio.Narrator
import com.nibble.app.core.audio.NoopNarrator
import com.nibble.app.core.data.Event
import com.nibble.app.core.data.NibbleStore
import com.nibble.app.core.data.Persistence
import com.nibble.app.core.data.NoopPersistence
import com.nibble.app.core.data.Screen
import com.nibble.app.core.data.Tab
import com.nibble.app.core.design.BottomDest
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.NibbleBottomBar
import com.nibble.app.core.design.NibbleTheme
import com.nibble.app.core.domain.ThemeChoice
import com.nibble.app.feature.home.HomeTodayScreen
import com.nibble.app.feature.home.ShelfScreen
import com.nibble.app.feature.home.YouScreen
import com.nibble.app.feature.onboarding.DiagnosisScreen
import com.nibble.app.feature.onboarding.PickBookScreen
import com.nibble.app.feature.reader.ReaderScreen
import com.nibble.app.feature.reward.RewardScreen
import com.nibble.app.feature.stats.DamageReportScreen

/** Provided by the platform so the Shelf can launch a native file picker for EPUB import. */
val LocalImportEpub = staticCompositionLocalOf<() -> Unit> { {} }

/** Provided by the platform to post a lockscreen drip notification (§9). */
val LocalPostDrip = staticCompositionLocalOf<() -> Unit> { {} }

/** Provided by the platform to hand text to the system share sheet. */
val LocalShareText = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Respect the OS "remove animations" setting (§13). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Handle to the store so the platform can force a save on background. */
class AppHandle(val store: NibbleStore)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun App(
    narrator: Narrator = NoopNarrator(),
    persistence: Persistence = NoopPersistence(),
    onImportEpub: () -> Unit = {},
    onPostDrip: () -> Unit = {},
    onShareText: (String) -> Unit = {},
    onReaderActive: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false,
    onReady: (AppHandle) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val store = remember { NibbleStore(scope, narrator, persistence) }
    LaunchedEffect(Unit) {
        store.bootstrap()
        onReady(AppHandle(store))
    }
    val state by store.state.collectAsState()
    val onEvent: (Event) -> Unit = store::onEvent

    // Keep the screen awake while a page is open — books outlast screen timeouts.
    val readerOpen = state.reader != null
    LaunchedEffect(readerOpen) { onReaderActive(readerOpen) }

    // System back navigates the app's own stack; only a true root lets the app close.
    androidx.compose.ui.backhandler.BackHandler(enabled = state.canHandleBack) {
        onEvent(Event.SystemBack)
    }

    val systemDark = isSystemInDarkTheme()
    val dark = when (state.theme) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        ThemeChoice.SUNSET -> com.nibble.app.core.domain.Time.hour().let { it >= 20 || it < 7 }
    }

    NibbleTheme(dark = dark) {
        val colors = LocalNibbleColors.current
        CompositionLocalProvider(
            LocalImportEpub provides onImportEpub,
            LocalPostDrip provides onPostDrip,
            LocalShareText provides onShareText,
            LocalReduceMotion provides reduceMotion,
        ) {
            // Nibble is a phone layout (no tablet-specific screens in v1); on a large or
            // unfolded screen we cap content to a phone width and centre it (§1 scope).
            Box(Modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.TopCenter) {
                if (state.booting || state.books.isEmpty()) {
                    // Ingest is fast; nothing to show but the paper ground.
                    return@Box
                }
                Box(Modifier.fillMaxHeight().widthIn(max = 560.dp).fillMaxWidth()) {
                when (val screen = state.current) {
                    Screen.Diagnosis ->
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            DiagnosisScreen(state, onEvent)
                        }
                    Screen.PickBook ->
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            PickBookScreen(state, onEvent)
                        }
                    Screen.Home -> HomeScaffold(state, onEvent)
                    is Screen.Reader -> ReaderScreen(state, onEvent)
                    is Screen.Reward ->
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            RewardScreen(state, onEvent)
                        }
                    Screen.SavedLines ->
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            com.nibble.app.feature.lines.SavedLinesScreen(state, onEvent)
                        }
                    Screen.Auth ->
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                            com.nibble.app.feature.auth.AuthScreen(state, onEvent)
                        }
                }
                }
            }
        }
    }
}

@Composable
private fun HomeScaffold(
    state: com.nibble.app.core.data.AppState,
    onEvent: (Event) -> Unit,
) {
    val tabs = listOf(
        BottomDest(Icons.AutoMirrored.Filled.MenuBook, "Today"),
        BottomDest(Icons.AutoMirrored.Filled.MenuBook, "Shelf"),
        BottomDest(Icons.Filled.ShowChart, "Damage"),
        BottomDest(Icons.Filled.Person, "You"),
    )
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            when (state.tab) {
                Tab.TODAY -> HomeTodayScreen(state, onEvent)
                Tab.SHELF -> ShelfScreen(state, onEvent)
                Tab.DAMAGE -> DamageReportScreen(state, onEvent)
                Tab.YOU -> YouScreen(state, onEvent)
            }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            NibbleBottomBar(
                destinations = tabs,
                selectedIndex = state.tab.ordinal,
                onSelect = { onEvent(Event.SelectTab(Tab.entries[it])) },
            )
        }
    }
}
