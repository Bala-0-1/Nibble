package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookSearchTest {

    private fun book(vararg chapters: String) = Book(
        id = "b", title = "Test Book", shortTitle = "Test", author = "A",
        source = BookSource.CATALOG, coverGround = 0, coverAccent = 0, onCover = 0,
        blurb = "", spine = chapters.mapIndexed { i, t -> SpineItem(i, "Ch $i", t) },
    )

    @Test
    fun findsAMatchCaseInsensitively() {
        val hits = BookSearch.search(listOf(book("The Lawyer walked home. It was late.")), "lawyer")
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].spineIndex)
        assertEquals(4, hits[0].charOffset)
        // The snippet's marked range reproduces the query.
        val h = hits[0]
        assertEquals("lawyer", h.snippet.substring(h.matchStart, h.matchEnd).lowercase())
    }

    @Test
    fun tooShortQueriesReturnNothing() {
        assertTrue(BookSearch.search(listOf(book("something")), "s").isEmpty())
        assertTrue(BookSearch.search(listOf(book("something")), " ").isEmpty())
    }

    @Test
    fun searchesAcrossChapters() {
        val hits = BookSearch.search(listOf(book("nothing here", "the door was found here")), "door")
        assertEquals(1, hits.size)
        assertEquals(1, hits[0].spineIndex)
    }

    @Test
    fun resultsAreCapped() {
        val text = (1..50).joinToString(" — a long stretch of filler between mentions — ") { "cellar" }
        val hits = BookSearch.search(listOf(book(text)), "cellar", limit = 5)
        assertEquals(5, hits.size)
    }
}
