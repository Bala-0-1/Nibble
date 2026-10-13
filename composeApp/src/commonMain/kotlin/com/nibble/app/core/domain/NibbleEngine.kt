package com.nibble.app.core.domain

/**
 * The nibble engine (§6). Chunking is deterministic: the same chapter text and wpm
 * always yield identical boundaries, or saved positions drift. Runs once at import.
 *
 * Sentences tile the chapter with no gaps — concatenating every nibble reproduces the
 * chapter exactly (the property test in §14).
 */
object NibbleEngine {

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "st", "sr", "jr", "prof", "rev", "hon", "gen",
        "col", "capt", "lt", "sgt", "vs", "etc", "no", "vol", "co", "inc", "ave",
    )

    /** A sentence span [start, end) into the chapter text, including trailing whitespace. */
    data class Sentence(val start: Int, val end: Int, val words: Int, val terminal: Char)

    /**
     * Rule-based sentence splitter (ICU-free). Handles "Mr.", em-dashes, ellipses and
     * quotes. Ranges are contiguous and cover the whole string.
     */
    fun splitSentences(text: String): List<Sentence> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Sentence>()
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                // Skip an ellipsis run — never a boundary inside "..."
                if (c == '.' && i + 1 < n && text[i + 1] == '.') {
                    while (i < n && text[i] == '.') i++
                    continue
                }
                // Not a boundary if this period closes a known abbreviation.
                if (c == '.' && isAbbreviation(text, i)) { i++; continue }
                // Consume trailing closing quotes/brackets that belong to this sentence.
                var j = i + 1
                while (j < n && text[j] in "\"'”’)]") j++
                // A boundary needs whitespace (or end of text) after the punctuation.
                if (j >= n || text[j].isWhitespace()) {
                    val terminal = c
                    // Absorb the whitespace gap so ranges stay contiguous.
                    var k = j
                    while (k < n && text[k].isWhitespace()) k++
                    out.add(Sentence(start, k, countWords(text, start, j), terminal))
                    start = k
                    i = k
                    continue
                }
            }
            i++
        }
        if (start < n) out.add(Sentence(start, n, countWords(text, start, n), '.'))
        return out
    }

    private fun isAbbreviation(text: String, dot: Int): Boolean {
        var s = dot - 1
        while (s >= 0 && text[s].isLetter()) s--
        val word = text.substring(s + 1, dot).lowercase()
        if (word.isEmpty()) return false
        // Single capital initial like "H." in "H. G. Wells".
        if (word.length == 1 && text[s + 1].isUpperCase()) return true
        return word in ABBREVIATIONS
    }

    private fun countWords(text: String, start: Int, end: Int): Int {
        var count = 0
        var inWord = false
        var i = start
        while (i < end) {
            val letter = text[i].isLetterOrDigit()
            if (letter && !inWord) { count++; inWord = true }
            else if (!letter && text[i] != '\'' && text[i] != '’') inWord = false
            i++
        }
        return count
    }

    /**
     * Segment a single chapter. Returns nibbles with LOCAL indices (0-based); the caller
     * assigns global indices across the book's spine.
     */
    fun segmentChapter(spineIndex: Int, text: String, wpm: Int): List<Nibble> {
        val target = wpm * 2                 // words per ~2 minutes
        val low = target * 0.85
        val high = target * 1.15
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val nibbles = ArrayList<Nibble>()
        var acc = 0
        var chunkStartSentence = 0
        var words = 0

        fun isParagraphEnd(idx: Int): Boolean {
            val s = sentences[idx]
            // The trailing whitespace absorbed into this sentence spans a line break,
            // or it's the final sentence of the chapter.
            return idx == sentences.lastIndex || text.substring(s.start, s.end).contains('\n')
        }

        for (idx in sentences.indices) {
            acc += sentences[idx].words
            words += sentences[idx].words
            val paraEnd = isParagraphEnd(idx)
            val shouldClose = when {
                acc >= low && paraEnd -> true
                acc >= high -> true
                idx == sentences.lastIndex -> true
                else -> false
            }
            if (shouldClose) {
                nibbles += buildNibble(text, sentences, chunkStartSentence, idx, spineIndex, words)
                chunkStartSentence = idx + 1
                acc = 0
                words = 0
            }
        }

        // Rule 4: never emit a nibble under target*0.5 — merge it backwards.
        if (nibbles.size >= 2) {
            val last = nibbles.last()
            if (last.wordCount < target * 0.5) {
                val prev = nibbles[nibbles.size - 2]
                nibbles[nibbles.size - 2] = prev.copy(
                    endChar = last.endChar,
                    wordCount = prev.wordCount + last.wordCount,
                    beats = prev.beats + last.beats,
                    endsOnCliffhanger = last.endsOnCliffhanger,
                )
                nibbles.removeAt(nibbles.size - 1)
            }
        }
        return nibbles
    }

    private fun buildNibble(
        text: String,
        sentences: List<NibbleEngine.Sentence>,
        from: Int,
        to: Int,
        spineIndex: Int,
        words: Int,
    ): Nibble {
        val startChar = sentences[from].start
        val endChar = sentences[to].end
        val beats = (from..to).map { sentences[it].start until sentences[it].end }
        val closing = sentences[to]
        val closingText = text.substring(closing.start, closing.end).trim()
        val cliffhanger = closing.terminal == '?' ||
            closingText.endsWith("—") || closingText.endsWith("…") ||
            closingText.contains('"') || closingText.contains('“') ||
            closingText.endsWith("!")
        return Nibble(
            index = 0, // assigned globally by caller
            spineIndex = spineIndex,
            startChar = startChar,
            endChar = endChar,
            wordCount = words,
            beats = beats,
            endsOnCliffhanger = cliffhanger,
        )
    }

    /** Segment a whole book across its spine, assigning stable global indices. */
    fun segmentBook(book: Book, wpm: Int): List<Nibble> {
        val all = ArrayList<Nibble>()
        var global = 0
        for (item in book.spine) {
            for (nib in segmentChapter(item.index, item.plainText, wpm)) {
                all += nib.copy(index = global++)
            }
        }
        return all
    }

    /** Derived: the nibble containing a canonical (spineIndex, charOffset). Never persisted. */
    fun nibbleContaining(nibbles: List<Nibble>, spineIndex: Int, charOffset: Int): Nibble? {
        val inChapter = nibbles.filter { it.spineIndex == spineIndex }
        if (inChapter.isEmpty()) return null
        // Binary search over contiguous ranges.
        var lo = 0
        var hi = inChapter.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val nib = inChapter[mid]
            when {
                charOffset < nib.startChar -> hi = mid - 1
                charOffset >= nib.endChar -> lo = mid + 1
                else -> return nib
            }
        }
        return inChapter.last()
    }
}
