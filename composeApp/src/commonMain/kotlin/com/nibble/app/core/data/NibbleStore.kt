package com.nibble.app.core.data

import com.nibble.app.core.audio.Narrator
import com.nibble.app.core.audio.NarratorState
import com.nibble.app.core.domain.AttentionTier
import com.nibble.app.core.domain.Book
import com.nibble.app.core.domain.Catalog
import com.nibble.app.core.domain.Nibble
import com.nibble.app.core.domain.NibbleEngine
import com.nibble.app.core.domain.PositionSource
import com.nibble.app.core.domain.ReaderMode
import com.nibble.app.core.domain.ReadingPosition
import com.nibble.app.core.domain.Session
import com.nibble.app.core.domain.Stats
import com.nibble.app.core.domain.ThemeChoice
import com.nibble.app.core.domain.Time
import com.nibble.app.core.domain.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ---- Navigation ---------------------------------------------------------------

sealed interface Screen {
    data object Diagnosis : Screen
    data object PickBook : Screen
    data object Home : Screen
    data class Reader(val bookId: String) : Screen
    data class Reward(val sessionId: Long) : Screen
    data object SavedLines : Screen
    data object Auth : Screen
}

enum class Tab { TODAY, SHELF, DAMAGE, YOU }

// ---- Reader UI ----------------------------------------------------------------

data class ReaderUi(
    val bookId: String,
    val mode: ReaderMode = ReaderMode.NIBBLES,
    val controlsOpen: Boolean = false,
    val currentBeat: Int = 0,
    val audio: NarratorState = NarratorState.IDLE,
    val spokenChar: Int? = null,
    val sessionSeconds: Int = 0,
    val gloss: String? = null,   // on-device "Who?" gloss (§12), grounded before position
    val recap: String? = null,   // on-device "previously" recap when resuming
    val sleepMinutes: Int? = null,   // audio sleep timer selection; null = off
)

// ---- App state ----------------------------------------------------------------

data class AppState(
    val booting: Boolean = true,
    val onboarded: Boolean = false,
    val tier: AttentionTier? = null,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val voice: Voice = Voice.DRY,
    val textScale: Float = 1f,           // 0.8×–1.6×, scales prose only
    val nagEnabled: Boolean = true,
    val nagTime: String = "21:40",
    val wpm: Int = 220,
    val backStack: List<Screen> = listOf(Screen.Diagnosis),
    val tab: Tab = Tab.TODAY,
    val books: List<Book> = emptyList(),
    val nibblesByBook: Map<String, List<Nibble>> = emptyMap(),
    val positions: Map<String, ReadingPosition> = emptyMap(),
    val selectedBookId: String? = "jekyll",
    val reader: ReaderUi? = null,
    val sessions: List<Session> = emptyList(),
    val lastSessionId: Long? = null,
    val importError: String? = null,
    val wittyRotation: Map<String, Int> = emptyMap(),
    val rewardPraiseIndex: Int = 0,
    val declineIndex: Int = 0,
    val lastNudgeMillis: Long = 0L,
    val nudgesToday: Int = 0,
    val nudgeDayEpoch: Int = 0,
    val savedLines: List<com.nibble.app.core.domain.SavedLine> = emptyList(),
    val finishedBookIds: Set<String> = emptySet(),
    val dailyGoalMinutes: Int = 10,
    val audioRate: Float = 1f,
    val account: com.nibble.app.core.auth.Account? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,   // PLAIN tone, shown verbatim
    val authNotice: String? = null,  // non-error info ("reset email sent")
    val authConfigured: Boolean = true,
) {
    val current: Screen get() = backStack.last()
    val streak: Int get() = Stats.streak(sessions)

    /**
     * True when a system back press means something inside the app; false hands the
     * press to the platform (which closes the app — only ever at a true root).
     */
    val canHandleBack: Boolean get() = when (current) {
        Screen.Diagnosis -> false                 // onboarding root
        Screen.PickBook -> true                   // back to the diagnosis question
        Screen.Home -> tab != Tab.TODAY           // non-primary tab → Today, then exit
        is Screen.Reader -> true                  // sheet/audio/reader all layer here
        is Screen.Reward -> true                  // acts as a decline, back to Home
        Screen.SavedLines -> true
        Screen.Auth -> true
    }
    val todaySeconds: Int get() = Stats.todaySeconds(sessions)
    fun isFinished(id: String?): Boolean = id != null && id in finishedBookIds
    fun isLineSaved(bookId: String, spineIndex: Int, startChar: Int): Boolean =
        savedLines.any { it.bookId == bookId && it.spineIndex == spineIndex && it.startChar == startChar }
    fun bookOf(id: String?): Book? = books.firstOrNull { it.id == id }
    fun nibbles(id: String?): List<Nibble> = nibblesByBook[id] ?: emptyList()
    fun position(id: String?): ReadingPosition? = id?.let { positions[it] }
    fun nibbleCount(id: String?): Int = nibbles(id).size
    fun durationLabel(id: String?): String = Stats.formatDuration(nibbleCount(id) * 2 * 60)

    /** Resume nibble index for a book (0 if unread). */
    fun resumeIndex(id: String?): Int {
        val pos = position(id) ?: return 0
        return NibbleEngine.nibbleContaining(nibbles(id), pos.spineIndex, pos.charOffset)?.index ?: 0
    }
}

/** The lockscreen-drip snapshot for the primary book at its resume point (§9). */
fun AppState.dripSnapshot(): com.nibble.app.core.domain.DripSnapshot? {
    val book = bookOf(selectedBookId) ?: books.firstOrNull() ?: return null
    val nibs = nibbles(book.id)
    if (nibs.isEmpty()) return null
    val pos = position(book.id)
    val nib = if (pos != null) NibbleEngine.nibbleContaining(nibs, pos.spineIndex, pos.charOffset) ?: nibs.first() else nibs.first()
    val chapter = book.spine.firstOrNull { it.index == nib.spineIndex } ?: return null
    val len = chapter.plainText.length
    val beat = nib.beats.firstOrNull() ?: return null
    val excerpt = chapter.plainText.substring(beat.first.coerceIn(0, len), (beat.last + 1).coerceIn(0, len)).trim()
    val secondsLeft = (nib.wordCount / wpm.toDouble().coerceAtLeast(1.0) * 60).toInt().coerceAtLeast(20)
    val chapterLabel = "Ch. ${nib.spineIndex + 1}"
    val mmss = "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}"
    return com.nibble.app.core.domain.DripSnapshot(
        bookId = book.id, bookTitle = book.title, shortTitle = book.shortTitle,
        chapterLabel = chapterLabel, excerpt = "“$excerpt”",
        sub = "${book.shortTitle}, $chapterLabel — $mmss of reading left", secondsLeft = secondsLeft,
    )
}

/** Derived: the nibble the canonical position currently sits in. Never persisted. */
fun AppState.currentNibble(): Nibble? {
    val r = reader ?: return null
    val pos = position(r.bookId) ?: return nibbles(r.bookId).firstOrNull()
    return NibbleEngine.nibbleContaining(nibbles(r.bookId), pos.spineIndex, pos.charOffset)
        ?: nibbles(r.bookId).firstOrNull()
}

// ---- Events -------------------------------------------------------------------

sealed interface Event {
    data class SelectTier(val tier: AttentionTier) : Event
    data object Diagnose : Event
    data object SkipDiagnosis : Event
    data class SelectBook(val id: String) : Event
    data object StartWithBook : Event

    data class SelectTab(val tab: Tab) : Event
    data class OpenReader(val bookId: String) : Event
    /** Open the reader at an exact canonical position (search hit, saved line). */
    data class OpenReaderAt(val bookId: String, val spineIndex: Int, val charOffset: Int) : Event
    data object CloseReader : Event
    data object Back : Event
    /** System back (gesture/button). Routed here so one place decides what it means. */
    data object SystemBack : Event

    data class SetMode(val mode: ReaderMode) : Event
    data object OpenControls : Event
    data object CloseControls : Event
    data class SetTextScale(val scale: Float) : Event
    data object ToggleNag : Event
    data object NextNibble : Event
    data object PrevNibble : Event
    data object NextChapter : Event
    data object PrevChapter : Event
    data class GoToChapter(val spineIndex: Int) : Event
    data object TapAdvanceBeat : Event
    data class ScrollTo(val charOffset: Int) : Event

    data object HandoffToAudio : Event
    data object AudioPlayPause : Event
    data object AudioBackToText : Event
    data object CycleAudioRate : Event
    data object RestartSentence : Event
    data class SetSleepTimer(val minutes: Int?) : Event

    data object SaveCurrentLine : Event
    data class DeleteSavedLine(val id: Long) : Event
    data object OpenSavedLines : Event

    data class SetDailyGoal(val minutes: Int) : Event
    data class RemoveBook(val id: String) : Event

    data object OpenAuth : Event
    data class SignIn(val email: String, val password: String) : Event
    data class SignUp(val email: String, val password: String) : Event
    data object SignOut : Event
    data class SendPasswordReset(val email: String) : Event
    data object ClearAuthMessages : Event

    data object FinishNibble : Event
    data object OneMore : Event
    data object DeclineMore : Event

    data class SetTheme(val choice: ThemeChoice) : Event
    data class SetVoice(val voice: Voice) : Event

    data class ImportBook(val book: Book) : Event
    data class ImportFailed(val message: String) : Event
    data object ClearImportError : Event
}

// ---- Store --------------------------------------------------------------------

class NibbleStore(
    private val scope: CoroutineScope,
    private val narrator: Narrator,
    private val persistence: Persistence = NoopPersistence(),
    private val auth: com.nibble.app.core.auth.AuthService = com.nibble.app.core.auth.FirebaseAuth(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val ai: com.nibble.app.core.domain.OnDeviceModel = com.nibble.app.core.domain.HeuristicModel()
    private val aiCache = HashMap<String, String>()

    private var sessionSeq = 100L
    private var lineSeq = 0L
    private var saveJob: Job? = null
    private var sleepJob: Job? = null

    init {
        // Drive the highlight from the voice (§8): absolute charOffset as each word begins.
        scope.launch {
            narrator.spokenChar.collect { ch ->
                val r = _state.value.reader ?: return@collect
                if (narrator.state.value == NarratorState.IDLE) return@collect
                writePosition(r.bookId, ch, PositionSource.AUDIO)
                _state.update { it.copy(reader = it.reader?.copy(spokenChar = ch)) }
            }
        }
        scope.launch {
            narrator.state.collect { ns -> _state.update { it.copy(reader = it.reader?.copy(audio = ns)) } }
        }
        // Session timer.
        scope.launch {
            while (true) {
                delay(1000)
                val r = _state.value.reader
                if (r != null && r.audio != NarratorState.PAUSED && !r.controlsOpen) {
                    _state.update { it.copy(reader = it.reader?.copy(sessionSeconds = it.reader!!.sessionSeconds + 1)) }
                }
            }
        }
    }

    /** Ingest bundled books and load persisted state. Call once at app start. */
    suspend fun bootstrap() {
        val persisted = runCatching { persistence.load()?.let { json.decodeFromString<PersistedState>(it) } }
            .getOrNull() ?: PersistedState()
        aiCache.putAll(persisted.aiCache)   // byte-identical recaps across restarts (§12)
        // Sequences resume ABOVE anything persisted, or new ids collide with saved rows.
        sessionSeq = maxOf(100L, persisted.sessions.maxOfOrNull { it.id } ?: 0L)
        lineSeq = persisted.savedLines.maxOfOrNull { it.id } ?: 0L
        val catalog = Catalog.ingestAll()
        val books = catalog + persisted.importedBooks.filter { imp -> catalog.none { it.id == imp.id } }
        // Use the learned rate; fall back to the onboarding tier seed for a fresh install.
        val wpm = persisted.wpm.takeIf { persisted.sessions.isNotEmpty() || !persisted.onboarded }
            ?: (persisted.tier?.seedWpm ?: com.nibble.app.core.domain.ReadingRate.SEED)
        val nibbles = HashMap<String, List<Nibble>>()
        for (b in books) nibbles[b.id] = NibbleEngine.segmentBook(b, wpm)

        _state.update {
            it.copy(
                booting = false,
                onboarded = persisted.onboarded,
                tier = persisted.tier,
                theme = persisted.theme,
                voice = persisted.voice,
                textScale = persisted.textScale,
                nagEnabled = persisted.nagEnabled,
                wpm = wpm,
                books = books,
                nibblesByBook = nibbles,
                positions = persisted.positions,
                sessions = persisted.sessions,
                wittyRotation = persisted.wittyRotation,
                lastNudgeMillis = persisted.lastNudgeMillis,
                nudgesToday = persisted.nudgesToday,
                nudgeDayEpoch = persisted.nudgeDayEpoch,
                savedLines = persisted.savedLines,
                finishedBookIds = persisted.finishedBookIds,
                dailyGoalMinutes = persisted.dailyGoalMinutes,
                audioRate = persisted.audioRate,
                account = persisted.account,
                authConfigured = auth.isConfigured,
                selectedBookId = it.selectedBookId ?: books.firstOrNull()?.id,
                backStack = if (persisted.onboarded) listOf(Screen.Home) else listOf(Screen.Diagnosis),
            )
        }
        // Silent refresh: keep a signed-in session fresh without asking anything of the user.
        persisted.account?.let { acc ->
            if (acc.expiresAtMillis < Time.nowMillis() + 300_000) {
                scope.launch {
                    auth.refresh(acc).onSuccess { fresh ->
                        _state.update { it.copy(account = fresh) }
                        requestSave()
                    }
                    // Failure is fine: offline or expired — the account stays until sign-out.
                }
            }
        }
    }

    fun onEvent(e: Event) {
        when (e) {
            is Event.SelectTier -> _state.update { it.copy(tier = e.tier) }.also { requestSave() }
            Event.Diagnose -> { reSegmentForTier(); push(Screen.PickBook) }
            Event.SkipDiagnosis -> push(Screen.PickBook)
            is Event.SelectBook -> _state.update { it.copy(selectedBookId = e.id) }
            Event.StartWithBook -> {
                _state.update { it.copy(onboarded = true, backStack = listOf(Screen.Home), tab = Tab.TODAY) }
                requestSave()
            }

            is Event.SelectTab -> _state.update { it.copy(tab = e.tab) }
            is Event.OpenReader -> openReader(e.bookId)
            is Event.OpenReaderAt -> {
                openReader(e.bookId)
                writePositionAt(e.bookId, e.spineIndex, e.charOffset, PositionSource.NIBBLE)
                _state.update { it.copy(reader = it.reader?.copy(currentBeat = 0, spokenChar = null)) }
                refreshAi()
            }
            Event.CloseReader -> closeReader()
            Event.Back -> back()
            Event.SystemBack -> systemBack()

            is Event.SetMode -> _state.update { it.copy(reader = it.reader?.copy(mode = e.mode)) }
            Event.OpenControls -> _state.update { it.copy(reader = it.reader?.copy(controlsOpen = true)) }
            Event.CloseControls -> _state.update { it.copy(reader = it.reader?.copy(controlsOpen = false)) }
            is Event.SetTextScale -> { _state.update { it.copy(textScale = e.scale) }; requestSave() }
            Event.ToggleNag -> { _state.update { it.copy(nagEnabled = !it.nagEnabled) }; requestSave() }
            Event.NextNibble -> advanceNibble()
            Event.PrevNibble -> prevNibble()
            Event.NextChapter -> changeChapter(+1)
            Event.PrevChapter -> changeChapter(-1)
            is Event.GoToChapter -> goToChapter(e.spineIndex)
            Event.TapAdvanceBeat -> advanceBeat()
            is Event.ScrollTo -> _state.value.reader?.let { writePosition(it.bookId, e.charOffset, PositionSource.CHAPTER) }

            Event.HandoffToAudio -> handoffToAudio()
            Event.AudioPlayPause -> audioPlayPause()
            Event.AudioBackToText -> { cancelSleepTimer(); narrator.stop(); _state.update { it.copy(reader = it.reader?.copy(audio = NarratorState.IDLE, mode = ReaderMode.NIBBLES)) } }
            Event.CycleAudioRate -> cycleAudioRate()
            Event.RestartSentence -> restartSentence()
            is Event.SetSleepTimer -> setSleepTimer(e.minutes)

            Event.SaveCurrentLine -> toggleSaveCurrentLine()
            is Event.DeleteSavedLine -> {
                _state.update { st -> st.copy(savedLines = st.savedLines.filter { it.id != e.id }) }
                requestSave()
            }
            Event.OpenSavedLines -> push(Screen.SavedLines)

            is Event.SetDailyGoal -> { _state.update { it.copy(dailyGoalMinutes = e.minutes) }; requestSave() }
            is Event.RemoveBook -> removeBook(e.id)

            Event.OpenAuth -> { _state.update { it.copy(authError = null, authNotice = null) }; push(Screen.Auth) }
            is Event.SignIn -> credentialFlow(e.email, e.password) { em, pw -> auth.signIn(em, pw) }
            is Event.SignUp -> credentialFlow(e.email, e.password) { em, pw -> auth.signUp(em, pw) }
            Event.SignOut -> { _state.update { it.copy(account = null) }; requestSave() }
            is Event.SendPasswordReset -> sendPasswordReset(e.email)
            Event.ClearAuthMessages -> _state.update { it.copy(authError = null, authNotice = null) }

            Event.FinishNibble -> endSession()
            Event.OneMore -> oneMore()
            Event.DeclineMore -> { cancelSleepTimer(); narrator.stop(); _state.update { it.copy(reader = null, backStack = listOf(Screen.Home), tab = Tab.TODAY) } }

            is Event.SetTheme -> { _state.update { it.copy(theme = e.choice) }; requestSave() }
            is Event.SetVoice -> { _state.update { it.copy(voice = e.voice) }; requestSave() }

            is Event.ImportBook -> importBook(e.book)
            is Event.ImportFailed -> _state.update { it.copy(importError = e.message) }
            Event.ClearImportError -> _state.update { it.copy(importError = null) }
        }
    }

    // ---- internals ----

    /** Advance the rotation for a witty-line key so it's never repeated twice running. */
    private fun bumpWit(key: String): Int {
        val next = (_state.value.wittyRotation[key] ?: -1) + 1
        _state.update { it.copy(wittyRotation = it.wittyRotation + (key to next)) }
        return next
    }

    private fun push(s: Screen) = _state.update { it.copy(backStack = it.backStack + s) }
    private fun back() = _state.update { if (it.backStack.size > 1) it.copy(backStack = it.backStack.dropLast(1)) else it }

    /**
     * One system back press, peeled in layers: the topmost dismissable thing goes first
     * (controls sheet → audio → the reader itself), and Home falls back to the Today tab
     * before the platform is allowed to close the app. Mirrors [AppState.canHandleBack].
     */
    private fun systemBack() {
        val s = _state.value
        when (s.current) {
            Screen.Diagnosis -> Unit   // root; canHandleBack is false, we never get here
            Screen.PickBook -> back()
            Screen.Home -> if (s.tab != Tab.TODAY) _state.update { it.copy(tab = Tab.TODAY) }
            is Screen.Reader -> {
                val r = s.reader
                when {
                    r == null -> back()
                    r.controlsOpen -> _state.update { it.copy(reader = it.reader?.copy(controlsOpen = false)) }
                    r.audio == NarratorState.SPEAKING || r.audio == NarratorState.PAUSED -> {
                        // Same as the hands-free down-chevron: voice stops, page opens on the word.
                        cancelSleepTimer()
                        narrator.stop()
                        _state.update { it.copy(reader = it.reader?.copy(audio = NarratorState.IDLE, mode = ReaderMode.NIBBLES)) }
                    }
                    else -> closeReader()   // records the session (reward if ≥15s) like the ✕
                }
            }
            is Screen.Reward -> {
                // Backing out of the reward is a decline, not a resume.
                cancelSleepTimer()
                narrator.stop()
                _state.update { it.copy(reader = null, backStack = listOf(Screen.Home), tab = Tab.TODAY) }
            }
            Screen.SavedLines -> back()
            Screen.Auth -> back()
        }
    }

    private fun reSegmentForTier() {
        val wpm = _state.value.tier?.seedWpm ?: 220
        _state.update { st ->
            val map = HashMap<String, List<Nibble>>()
            for (b in st.books) map[b.id] = NibbleEngine.segmentBook(b, wpm)
            st.copy(wpm = wpm, nibblesByBook = map)
        }
    }

    private fun openReader(bookId: String) {
        val s = _state.value
        val nibs = s.nibbles(bookId)
        val existing = s.positions[bookId]
        val pos = existing ?: nibs.firstOrNull()?.let {
            ReadingPosition(bookId, it.spineIndex, it.startChar, Time.nowMillis(), PositionSource.NIBBLE)
        }
        _state.update {
            it.copy(
                reader = ReaderUi(bookId = bookId, currentBeat = 0, sessionSeconds = 0),
                positions = if (pos != null) it.positions + (bookId to pos) else it.positions,
                backStack = if (it.current is Screen.Reader) it.backStack else it.backStack + Screen.Reader(bookId),
            )
        }
        refreshAi()
    }

    /** Leave the reader. If you actually read, show the session summary; otherwise go home. */
    private fun closeReader() {
        cancelSleepTimer()
        narrator.stop()
        val r = _state.value.reader
        if (r != null && r.sessionSeconds >= 15) endSession()
        else _state.update { it.copy(reader = null, backStack = listOf(Screen.Home)) }
    }

    /** The one write path for progress (§7). Highest updatedAt wins; ties resolve to AUDIO. */
    private fun writePosition(bookId: String, charOffset: Int, source: PositionSource) {
        _state.update { st ->
            val book = st.bookOf(bookId) ?: return@update st
            val prev = st.positions[bookId]
            val spine = prev?.spineIndex ?: book.spine.firstOrNull()?.index ?: 0
            val next = ReadingPosition(bookId, spine, charOffset, Time.nowMillis(), source)
            if (prev != null && prev.updatedAt > next.updatedAt && prev.source != PositionSource.AUDIO) st
            else st.copy(positions = st.positions + (bookId to next))
        }
        requestSave()
    }

    /** Explicit navigation write — carries the target chapter's spineIndex (fixes cross-chapter). */
    private fun writePositionAt(bookId: String, spineIndex: Int, charOffset: Int, source: PositionSource) {
        _state.update { st ->
            st.copy(positions = st.positions + (bookId to ReadingPosition(bookId, spineIndex, charOffset, Time.nowMillis(), source)))
        }
        requestSave()
    }

    private fun goToNibble(nib: Nibble?) {
        val r = _state.value.reader ?: return
        if (nib == null) return
        writePositionAt(r.bookId, nib.spineIndex, nib.startChar, PositionSource.NIBBLE)
        _state.update { it.copy(reader = it.reader?.copy(currentBeat = 0, spokenChar = null)) }
        refreshAi()
    }

    private fun advanceBeat() {
        _state.update { st ->
            val r = st.reader ?: return@update st
            val nib = st.currentNibble() ?: return@update st
            st.copy(reader = r.copy(currentBeat = (r.currentBeat + 1).coerceAtMost(nib.beats.lastIndex)))
        }
    }

    /** Next nibble — a clear page turn. Crosses into the next chapter; reward at book end. */
    private fun advanceNibble() {
        val s = _state.value
        val cur = s.currentNibble() ?: return
        val next = s.nibbles(s.reader?.bookId).firstOrNull { it.index == cur.index + 1 }
        if (next == null) {
            // Last nibble finished — the book is done. Derived UI (shelf, badges) reads this set.
            s.reader?.let { r -> _state.update { it.copy(finishedBookIds = it.finishedBookIds + r.bookId) } }
            endSession()
        } else goToNibble(next)
    }

    private fun prevNibble() {
        val s = _state.value
        val cur = s.currentNibble() ?: return
        goToNibble(s.nibbles(s.reader?.bookId).firstOrNull { it.index == cur.index - 1 })
    }

    /** Jump a whole chapter forward/back to the first nibble of that chapter. */
    private fun changeChapter(delta: Int) {
        val s = _state.value
        val r = s.reader ?: return
        val book = s.bookOf(r.bookId) ?: return
        val pos = s.position(r.bookId) ?: return
        val ordinal = book.spine.indexOfFirst { it.index == pos.spineIndex }
        val target = book.spine.getOrNull(ordinal + delta) ?: return
        val firstNib = s.nibbles(r.bookId).firstOrNull { it.spineIndex == target.index }
        writePositionAt(r.bookId, target.index, firstNib?.startChar ?: 0, PositionSource.CHAPTER)
        _state.update { it.copy(reader = it.reader?.copy(currentBeat = 0, spokenChar = null)) }
        refreshAi()
    }

    /** Jump straight to a chapter picked from the reader's chapter list. */
    private fun goToChapter(spineIndex: Int) {
        val s = _state.value
        val r = s.reader ?: return
        val firstNib = s.nibbles(r.bookId).firstOrNull { it.spineIndex == spineIndex }
        writePositionAt(r.bookId, spineIndex, firstNib?.startChar ?: 0, PositionSource.CHAPTER)
        _state.update { it.copy(reader = it.reader?.copy(currentBeat = 0, spokenChar = null, controlsOpen = false)) }
        refreshAi()
    }

    private fun handoffToAudio() {
        val s = _state.value
        val r = s.reader ?: return
        val book = s.bookOf(r.bookId) ?: return
        val pos = s.position(r.bookId) ?: return
        val text = book.spine.firstOrNull { it.index == pos.spineIndex }?.plainText ?: return
        _state.update { it.copy(reader = it.reader?.copy(mode = ReaderMode.NIBBLES, controlsOpen = false)) }
        narrator.setNowPlaying(book.shortTitle, book.id)
        narrator.speak(text, pos.charOffset, rate = s.audioRate)
    }

    /** Re-speak the current chapter from a given char at the current rate. */
    private fun speakFrom(charOffset: Int) {
        val s = _state.value
        val r = s.reader ?: return
        val book = s.bookOf(r.bookId) ?: return
        val pos = s.position(r.bookId) ?: return
        val text = book.spine.firstOrNull { it.index == pos.spineIndex }?.plainText ?: return
        narrator.setNowPlaying(book.shortTitle, book.id)
        narrator.speak(text, charOffset.coerceIn(0, text.length), rate = s.audioRate)
    }

    private fun audioPlayPause() {
        when (narrator.state.value) {
            NarratorState.SPEAKING -> narrator.pause()
            // Resume by re-speaking from the spoken char — picks up a rate changed while paused.
            NarratorState.PAUSED -> speakFrom(_state.value.reader?.spokenChar ?: narrator.spokenChar.value)
            NarratorState.IDLE -> handoffToAudio()
        }
    }

    private val AUDIO_RATES = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

    /** Cycle narration speed; if the voice is mid-sentence, re-speak at the new rate. */
    private fun cycleAudioRate() {
        val s = _state.value
        val idx = AUDIO_RATES.indexOfFirst { it >= s.audioRate - 0.01f }.coerceAtLeast(0)
        val next = AUDIO_RATES[(idx + 1) % AUDIO_RATES.size]
        _state.update { it.copy(audioRate = next) }
        requestSave()
        if (narrator.state.value == NarratorState.SPEAKING) {
            speakFrom(s.reader?.spokenChar ?: narrator.spokenChar.value)
        }
    }

    /** Hands-free "back a bit" — restart the sentence the voice is on. */
    private fun restartSentence() {
        val s = _state.value
        val nib = s.currentNibble() ?: return
        val spoken = s.reader?.spokenChar ?: nib.startChar
        val beatStart = nib.beats.lastOrNull { it.first <= spoken }?.first ?: nib.startChar
        speakFrom(beatStart)
    }

    /** Sleep timer: after N minutes the narration pauses where it is (§8 — one position). */
    private fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(reader = it.reader?.copy(sleepMinutes = minutes)) }
        if (minutes != null) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                if (narrator.state.value == NarratorState.SPEAKING) narrator.pause()
                _state.update { it.copy(reader = it.reader?.copy(sleepMinutes = null)) }
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(reader = it.reader?.copy(sleepMinutes = null)) }
    }

    /** Heart on the current beat: save it, or un-save it if it's already kept. */
    private fun toggleSaveCurrentLine() {
        val s = _state.value
        val r = s.reader ?: return
        val book = s.bookOf(r.bookId) ?: return
        val nib = s.currentNibble() ?: return
        val beat = nib.beats.getOrNull(r.currentBeat.coerceIn(0, nib.beats.lastIndex)) ?: return
        val chapter = book.spine.firstOrNull { it.index == nib.spineIndex } ?: return
        val len = chapter.plainText.length
        val start = beat.first.coerceIn(0, len)
        val end = (beat.last + 1).coerceIn(0, len)
        val existing = s.savedLines.firstOrNull {
            it.bookId == r.bookId && it.spineIndex == nib.spineIndex && it.startChar == start
        }
        _state.update { st ->
            if (existing != null) st.copy(savedLines = st.savedLines.filter { it.id != existing.id })
            else st.copy(
                savedLines = st.savedLines + com.nibble.app.core.domain.SavedLine(
                    id = ++lineSeq, bookId = r.bookId, spineIndex = nib.spineIndex,
                    startChar = start, endChar = end,
                    text = chapter.plainText.substring(start, end).trim(),
                    savedAt = Time.nowMillis(),
                ),
            )
        }
        requestSave()
    }

    /**
     * Shared sign-in/sign-up path: validate locally, call the service, and either land
     * the account (popping the auth screen) or surface a PLAIN error verbatim (§11).
     */
    private fun credentialFlow(
        email: String,
        password: String,
        call: suspend (String, String) -> Result<com.nibble.app.core.auth.Account>,
    ) {
        if (_state.value.authBusy) return
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(authError = "Enter an email and a password.", authNotice = null) }
            return
        }
        _state.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        scope.launch {
            val result = call(email, password)
            _state.update { st ->
                result.fold(
                    onSuccess = { acc ->
                        st.copy(
                            authBusy = false,
                            account = acc,
                            backStack = if (st.current == Screen.Auth) st.backStack.dropLast(1) else st.backStack,
                        )
                    },
                    onFailure = { e ->
                        st.copy(authBusy = false, authError = e.message ?: "Something went wrong. Try again.")
                    },
                )
            }
            if (result.isSuccess) requestSave()
        }
    }

    private fun sendPasswordReset(email: String) {
        if (_state.value.authBusy) return
        if (email.isBlank()) {
            _state.update { it.copy(authError = "Enter your email first.", authNotice = null) }
            return
        }
        _state.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        scope.launch {
            val result = auth.sendPasswordReset(email)
            _state.update { st ->
                result.fold(
                    onSuccess = { st.copy(authBusy = false, authNotice = "Reset email sent. Check your inbox.") },
                    onFailure = { e -> st.copy(authBusy = false, authError = e.message ?: "Couldn't send the reset email.") },
                )
            }
        }
    }

    /** Remove an imported book and everything that pointed into it. */
    private fun removeBook(id: String) {
        if (_state.value.bookOf(id)?.source == com.nibble.app.core.domain.BookSource.IMPORT) {
            runCatching { persistence.deleteBookImages(id) }
        }
        _state.update { st ->
            val book = st.bookOf(id) ?: return@update st
            if (book.source != com.nibble.app.core.domain.BookSource.IMPORT) return@update st
            val remaining = st.books.filter { it.id != id }
            st.copy(
                books = remaining,
                nibblesByBook = st.nibblesByBook - id,
                positions = st.positions - id,
                savedLines = st.savedLines.filter { it.bookId != id },
                finishedBookIds = st.finishedBookIds - id,
                selectedBookId = if (st.selectedBookId == id) remaining.firstOrNull()?.id else st.selectedBookId,
            )
        }
        requestSave()
    }

    /** End the reading session: record it and show the "Nibble down" summary (reward). */
    private fun endSession() {
        val s = _state.value
        val r = s.reader ?: return
        narrator.stop()
        val nib = s.currentNibble()
        val words = nib?.wordCount ?: 0
        val seconds = r.sessionSeconds.coerceAtLeast(1)   // real elapsed reading time
        val id = ++sessionSeq
        // Rotate the witty lines so the reward never repeats the same roast twice running.
        val praiseIdx = bumpWit("reward.praise")
        val declineIdx = bumpWit("reward.decline")
        val session = Session(id, r.bookId, Time.nowMillis(), seconds, words, r.mode)
        _state.update {
            it.copy(
                sessions = it.sessions + session,
                lastSessionId = id,
                rewardPraiseIndex = praiseIdx,
                declineIndex = declineIdx,
                reader = it.reader?.copy(audio = NarratorState.IDLE),
                backStack = it.backStack + Screen.Reward(id),
            )
        }
        maybeRecalibrate()
        requestSave()
    }

    /** Re-chunk books when the measured reading rate drifts >15% (§6). Position is by
     *  charOffset, so re-segmenting alone preserves where the reader is. */
    private fun maybeRecalibrate() {
        val s = _state.value
        val rate = com.nibble.app.core.domain.ReadingRate.estimate(s.sessions)
        if (com.nibble.app.core.domain.ReadingRate.shouldResegment(rate, s.wpm)) {
            val map = HashMap<String, List<Nibble>>()
            for (b in s.books) map[b.id] = NibbleEngine.segmentBook(b, rate)
            _state.update { it.copy(wpm = rate, nibblesByBook = map) }
        }
    }

    /** Record accrued reading time when leaving the reader without an explicit finish. */
    private fun flushSession() {
        val r = _state.value.reader ?: return
        if (r.sessionSeconds < 20) return
        val id = ++sessionSeq
        val session = Session(id, r.bookId, Time.nowMillis(), r.sessionSeconds, 0, r.mode)
        _state.update { it.copy(sessions = it.sessions + session) }
        requestSave()
    }

    /** "One more?" — pop the reward and resume reading right where you are, fresh timer. */
    private fun oneMore() {
        _state.update {
            it.copy(
                backStack = if (it.current is Screen.Reward) it.backStack.dropLast(1) else it.backStack,
                reader = it.reader?.copy(sessionSeconds = 0),
            )
        }
        if (_state.value.reader == null) {
            // Safety: if we lost the reader, reopen the book at its saved position.
            _state.value.positions.keys.firstOrNull()?.let { openReader(it) }
        }
    }

    private fun importBook(book: Book) {
        _state.update { st ->
            val nibs = NibbleEngine.segmentBook(book, st.wpm)
            st.copy(
                books = st.books.filter { it.id != book.id } + book,
                nibblesByBook = st.nibblesByBook + (book.id to nibs),
                importError = null,
            )
        }
        requestSave()
    }

    /**
     * Regenerate on-device text for the current nibble: the "Who?" gloss for a character
     * introduced earlier, and a "previously" recap when resuming mid-book. Both grounded
     * strictly in text before the reader's position (spoiler-safe, §12). Cached per bucket.
     */
    private fun refreshAi() {
        val s = _state.value
        val r = s.reader ?: return
        val book = s.bookOf(r.bookId) ?: return
        val pos = s.position(r.bookId) ?: return
        val chapter = book.spine.firstOrNull { it.index == pos.spineIndex } ?: return
        val nib = s.currentNibble() ?: return
        val len = chapter.plainText.length
        val nibbleText = chapter.plainText.substring(nib.startChar.coerceIn(0, len), nib.endChar.coerceIn(0, len))
        val grounding = chapter.plainText.substring(0, pos.charOffset.coerceIn(0, len))
        scope.launch {
            val name = com.nibble.app.core.domain.HeuristicModel.prominentName(nibbleText, grounding)
            val glossKey = "gloss:${r.bookId}:${pos.spineIndex}:$name"
            val gloss = if (name == null) null
            else aiCache[glossKey] ?: ai.generate(com.nibble.app.core.domain.AiTask.WhoGloss(name), grounding)
                .getOrNull()?.also { aiCache[glossKey] = it }
            val resuming = nib.index > 0 && grounding.length > 600
            val recapKey = "recap:${r.bookId}:${pos.spineIndex}:${pos.charOffset / 2000}"
            val recap = if (!resuming) null
            else aiCache[recapKey] ?: ai.generate(com.nibble.app.core.domain.AiTask.Recap(), grounding)
                .getOrNull()?.also { aiCache[recapKey] = it }
            _state.update { st ->
                if (st.reader?.bookId == r.bookId) st.copy(reader = st.reader.copy(gloss = gloss, recap = recap)) else st
            }
            if (gloss != null || recap != null) requestSave()   // persist the generated text (§12)
        }
    }

    private fun requestSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)   // debounce (§7: debounce while reading)
            saveNow()
        }
    }

    /** Force a write immediately — call on app background. */
    fun saveNow() {
        val s = _state.value
        if (s.booting) return
        val ps = PersistedState(
            onboarded = s.onboarded,
            tier = s.tier,
            theme = s.theme,
            voice = s.voice,
            textScale = s.textScale,
            nagEnabled = s.nagEnabled,
            wpm = s.wpm,
            importedBooks = s.books.filter { it.source == com.nibble.app.core.domain.BookSource.IMPORT },
            positions = s.positions,
            sessions = s.sessions,
            wittyRotation = s.wittyRotation,
            lastNudgeMillis = s.lastNudgeMillis,
            nudgesToday = s.nudgesToday,
            nudgeDayEpoch = s.nudgeDayEpoch,
            aiCache = aiCache.entries.toList().takeLast(80).associate { it.key to it.value },
            savedLines = s.savedLines,
            finishedBookIds = s.finishedBookIds,
            dailyGoalMinutes = s.dailyGoalMinutes,
            audioRate = s.audioRate,
            account = s.account,
        )
        runCatching { persistence.save(json.encodeToString(ps)) }
        // Shared widget snapshot, written on every position change (§9).
        s.dripSnapshot()?.let { d ->
            val wd = WidgetData(d.bookId, d.shortTitle, d.chapterLabel, d.excerpt, (d.secondsLeft + 59) / 60)
            runCatching { persistence.saveWidget(json.encodeToString(wd)) }
        }
    }
}
