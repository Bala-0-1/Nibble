package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers + inline illustrations: extracted through the sink, anchored beside the text. */
class EpubParserTest {

    private val para1 = "It was a bright cold day in the port town, and the clocks were striking thirteen as the fishermen hauled their nets up the shingle and complained about the weather to anyone who would listen."
    private val para2 = "After the map was drawn, the captain folded it twice and slid it beneath the floorboards, saying nothing at all to the crew about what he had seen marked along the northern reef."
    private val para3 = "That night the wind turned, and the ship dragged her anchor a full mile before anyone noticed the lights of the town sliding away to the south."

    private fun entries(
        opfExtra: String = """<meta name="cover" content="cov"/>""",
        coverItem: String = """<item id="cov" href="images/cover.jpg" media-type="image/jpeg"/>""",
        chapterBody: String = "<p>$para1</p><img src=\"../images/pic1.png\" alt=\"The stranger's map of the island\"/><p>$para2</p><p>$para3</p>",
    ): Map<String, ByteArray> = mapOf(
        "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""".encodeToByteArray(),
        "OEBPS/content.opf" to """
            <package><metadata><dc:title>Harbour Test</dc:title><dc:creator>A. Author</dc:creator>$opfExtra</metadata>
            <manifest>
              $coverItem
              <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
              <item id="pic" href="images/pic1.png" media-type="image/png"/>
            </manifest>
            <spine><itemref idref="c1"/></spine></package>
        """.trimIndent().encodeToByteArray(),
        "OEBPS/text/ch1.xhtml" to "<html><body><h1>One</h1>$chapterBody</body></html>".encodeToByteArray(),
        "OEBPS/images/cover.jpg" to byteArrayOf(1, 2, 3),
        "OEBPS/images/pic1.png" to byteArrayOf(4, 5, 6),
    )

    private class Sink {
        val saved = LinkedHashMap<String, ByteArray>()
        val fn: (String, ByteArray) -> String? = { name, bytes -> saved[name] = bytes; "/fake/$name" }
    }

    @Test
    fun coverIsResolvedFromEpub2Meta() {
        val sink = Sink()
        val book = EpubParser.parse(entries(), "fallback", sink.fn)
        assertTrue(book.coverPath!!.startsWith("/fake/import-") && book.coverPath!!.endsWith(".jpg"))
        assertTrue(sink.saved.values.any { it.contentEquals(byteArrayOf(1, 2, 3)) })
    }

    @Test
    fun coverIsResolvedFromEpub3Properties() {
        val sink = Sink()
        val book = EpubParser.parse(
            entries(
                opfExtra = "",
                coverItem = """<item id="ci" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
            ),
            "fallback", sink.fn,
        )
        assertTrue(book.coverPath!!.endsWith(".jpg"))
    }

    @Test
    fun inlineImageBecomesAnAnchorAtItsParagraphBoundary() {
        val sink = Sink()
        val book = EpubParser.parse(entries(), "fallback", sink.fn)
        val ch = book.spine.single()
        assertTrue(!ch.plainText.contains("NIBIMG"), "token residue in text")
        val anchor = ch.images.single()
        // The apostrophe is the point: quotes must pair with the OPENING quote character.
        assertEquals("The stranger's map of the island", anchor.alt)
        assertTrue(anchor.path.endsWith(".png"))
        // The image sat between para1 and para2 — its anchor is where para2 begins.
        assertTrue(ch.plainText.substring(anchor.charOffset).startsWith("After the map was drawn"))
    }

    @Test
    fun repeatedImagesAreStoredOnceButAnchoredTwice() {
        val sink = Sink()
        val body = "<p>$para1</p><img src=\"../images/pic1.png\"/><p>$para2</p><img src=\"../images/pic1.png\"/><p>$para3</p>"
        val book = EpubParser.parse(entries(chapterBody = body), "fallback", sink.fn)
        val ch = book.spine.single()
        assertEquals(2, ch.images.size)
        assertEquals(ch.images[0].path, ch.images[1].path)
        // cover + one copy of pic1 — not two.
        assertEquals(2, sink.saved.size)
    }

    @Test
    fun withoutASinkTheBookIsTextOnlyAndClean() {
        val book = EpubParser.parse(entries(), "fallback")
        assertNull(book.coverPath)
        val ch = book.spine.single()
        assertTrue(ch.images.isEmpty())
        assertTrue(!ch.plainText.contains("NIBIMG"))
        assertTrue(ch.plainText.contains("After the map was drawn"))
    }

    @Test
    fun sentencesStillTileTheChapterAroundAnchors() {
        val sink = Sink()
        val book = EpubParser.parse(entries(), "fallback", sink.fn)
        val text = book.spine.single().plainText
        val sentences = NibbleEngine.splitSentences(text)
        // Contiguity: concatenating every sentence range reproduces the chapter exactly.
        assertEquals(0, sentences.first().start)
        assertEquals(text.length, sentences.last().end)
        for (i in 1 until sentences.size) assertEquals(sentences[i - 1].end, sentences[i].start)
    }
}
