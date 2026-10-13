package com.nibble.app.core.domain

import kotlinx.serialization.Serializable

/**
 * Domain models. These mirror the schema in §10 of the dev guide. They are @Serializable
 * so the repository can persist mutable state (positions, sessions, imported books) as JSON.
 */

/** A book is an ordered spine of chapter documents plus catalog metadata. */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val shortTitle: String,
    val author: String,
    val source: BookSource,
    /** ARGB used by the typographic cover fallback (picked by title hash at ingest). */
    val coverGround: Long,
    val coverAccent: Long,
    val onCover: Long,
    val blurb: String,
    val spine: List<SpineItem>,
    /** Extracted cover image on disk (EPUB imports); null = typographic cover. */
    val coverPath: String? = null,
)

@Serializable
enum class BookSource { CATALOG, IMPORT }

/** One chapter document, stripped to plain text with paragraph boundaries preserved. */
@Serializable
data class SpineItem(
    val index: Int,
    val title: String,
    val plainText: String,
    /** Illustrations, anchored BESIDE the text — plainText and its charOffsets stay canonical. */
    val images: List<ImageAnchor> = emptyList(),
)

/**
 * An illustration's place in a chapter: [charOffset] is the position in plainText where
 * the image sat (always a paragraph boundary), [path] the extracted file on disk.
 */
@Serializable
data class ImageAnchor(
    val charOffset: Int,
    val path: String,
    val alt: String = "",
)

/**
 * A ~2 minute chunk. Computed once at import in [NibbleEngine.segment] and persisted.
 * `index` is stable and user-visible ("19 of 64"); char offsets are into the chapter text.
 */
data class Nibble(
    val index: Int,
    val spineIndex: Int,
    val startChar: Int,   // inclusive
    val endChar: Int,     // exclusive
    val wordCount: Int,
    val beats: List<IntRange>,          // sentence groups for feed mode (2j)
    val endsOnCliffhanger: Boolean,
)

/**
 * The single canonical position for a book. Every surface reads and writes this and
 * nothing else stores progress (§7). `charOffset` is the only source of truth; the
 * nibble index / scroll / utterance are all derived on demand.
 */
@Serializable
data class ReadingPosition(
    val bookId: String,
    val spineIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
    val source: PositionSource,
)

@Serializable
enum class PositionSource { NIBBLE, CHAPTER, AUDIO, WIDGET }

/** Three renderers over one position — never three bookmarks. */
@Serializable
enum class ReaderMode { NIBBLES, CHAPTER, FEED }

/**
 * A line the reader chose to keep. The text is snapshotted at save time so the quote
 * survives re-segmentation and imported-book deletion; the offsets let us jump back.
 */
@Serializable
data class SavedLine(
    val id: Long,
    val bookId: String,
    val spineIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val text: String,
    val savedAt: Long,
)

/** A completed reading session. Streaks and stats are derived from these, never stored. */
@Serializable
data class Session(
    val id: Long,
    val bookId: String,
    val startedAt: Long,
    val activeSeconds: Int,
    val words: Int,
    val mode: ReaderMode,
)

/** Self-reported attention tier from onboarding (2a) — seeds the initial nibble length. */
@Serializable
enum class AttentionTier(val label: String, val sub: String, val seedWpm: Int) {
    MILD("Mildly fried", "I finish articles. Sometimes.", 240),
    DEEP_FRIED("Fully deep-fried", "I reread this sentence twice already.", 200),
    BEYOND("Beyond saving", "I watch films at 2× with subtitles.", 170),
}

/** Dry vs Plain voice (§11). */
@Serializable
enum class Voice { DRY, NEUTRAL }

/** Theme selection (§3). SUNSET auto-switches to dark after 20:00 local (fallback). */
@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK, SUNSET }
