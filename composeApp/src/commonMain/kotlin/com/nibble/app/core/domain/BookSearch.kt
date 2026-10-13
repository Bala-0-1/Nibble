package com.nibble.app.core.domain

/**
 * Full-text search over the library. Plain case-insensitive scan — the whole catalog is
 * a few hundred KB of ingested text, so an index would be ceremony. Results carry the
 * canonical (spineIndex, charOffset) so tapping one jumps the reader straight there (§7).
 */
object BookSearch {

    data class Hit(
        val bookId: String,
        val bookTitle: String,
        val spineIndex: Int,
        val chapterTitle: String,
        val charOffset: Int,
        /** Snippet around the match; [matchStart, matchEnd) are offsets into the snippet. */
        val snippet: String,
        val matchStart: Int,
        val matchEnd: Int,
    )

    fun search(books: List<Book>, query: String, limit: Int = 30): List<Hit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val out = ArrayList<Hit>()
        outer@ for (book in books) {
            for (chapter in book.spine) {
                var from = 0
                while (true) {
                    val at = chapter.plainText.indexOf(q, from, ignoreCase = true)
                    if (at < 0) break
                    out += hit(book, chapter, at, q.length)
                    if (out.size >= limit) break@outer
                    // One hit per neighbourhood — skip ahead so a repeated phrase doesn't flood.
                    from = at + q.length + 80
                }
            }
        }
        return out
    }

    private fun hit(book: Book, chapter: SpineItem, at: Int, matchLen: Int): Hit {
        val text = chapter.plainText
        var s = (at - 40).coerceAtLeast(0)
        var e = (at + matchLen + 60).coerceAtMost(text.length)
        // Snap to word boundaries so snippets don't open mid-word.
        while (s > 0 && !text[s - 1].isWhitespace()) s--
        while (e < text.length && !text[e - 1].isWhitespace()) e++
        val snippet = text.substring(s, e).replace('\n', ' ').trim()
        val lead = text.substring(s, at).replace('\n', ' ').trimStart().length
        return Hit(
            bookId = book.id,
            bookTitle = book.shortTitle,
            spineIndex = chapter.index,
            chapterTitle = chapter.title,
            charOffset = at,
            snippet = snippet,
            matchStart = lead.coerceIn(0, snippet.length),
            matchEnd = (lead + matchLen).coerceIn(0, snippet.length),
        )
    }
}
