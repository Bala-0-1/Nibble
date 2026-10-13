package com.nibble.app.core.data

import com.nibble.app.core.auth.Account
import com.nibble.app.core.domain.AttentionTier
import com.nibble.app.core.domain.Book
import com.nibble.app.core.domain.ReadingPosition
import com.nibble.app.core.domain.SavedLine
import com.nibble.app.core.domain.Session
import com.nibble.app.core.domain.ThemeChoice
import com.nibble.app.core.domain.Voice
import kotlinx.serialization.Serializable

/** Platform-provided blob storage (Android filesDir, iOS Documents). */
interface Persistence {
    fun load(): String?
    fun save(text: String)
    /** Write the shared widget snapshot (§9) so the widget process never queries the DB. */
    fun saveWidget(json: String) {}
    /** Delete a removed import's extracted cover/illustrations. Default: nothing stored. */
    fun deleteBookImages(bookId: String) {}
}

class NoopPersistence : Persistence {
    private var mem: String? = null
    override fun load(): String? = mem
    override fun save(text: String) { mem = text }
}

/** The shared snapshot the home-screen widget renders from (§9). */
@Serializable
data class WidgetData(
    val bookId: String,
    val title: String,
    val chapter: String,
    val excerpt: String,
    val minutesLeft: Int,
)

/** The mutable state that survives restarts. Catalog books are re-ingested, not stored. */
@Serializable
data class PersistedState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val tier: AttentionTier? = null,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val voice: Voice = Voice.DRY,
    val textScale: Float = 1f,
    val nagEnabled: Boolean = true,
    val wpm: Int = 220,
    val importedBooks: List<Book> = emptyList(),
    val positions: Map<String, ReadingPosition> = emptyMap(),
    val sessions: List<Session> = emptyList(),
    val wittyRotation: Map<String, Int> = emptyMap(),
    val lastNudgeMillis: Long = 0L,
    val nudgesToday: Int = 0,
    val nudgeDayEpoch: Int = 0,
    val aiCache: Map<String, String> = emptyMap(),
    val savedLines: List<SavedLine> = emptyList(),
    val finishedBookIds: Set<String> = emptySet(),
    val dailyGoalMinutes: Int = 10,
    val audioRate: Float = 1f,
    val account: Account? = null,
)
