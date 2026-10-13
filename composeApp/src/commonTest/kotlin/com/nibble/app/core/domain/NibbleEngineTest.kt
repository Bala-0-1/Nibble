package com.nibble.app.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** §14 — segmentation bugs silently corrupt saved positions, so these are non-negotiable. */
class NibbleEngineTest {

    private val chapter = """
        Mr. Utterson the lawyer was a man of a rugged countenance. He was never lighted by a smile. He was cold, scanty, and embarrassed in discourse.

        At friendly meetings, and when the wine was to his taste, something eminently human beaconed from his eye. It never found its way into his talk. But it spoke in the acts of his life.

        He was austere with himself. He drank gin when he was alone. He had not crossed the doors of a theatre for twenty years. Yet he had an approved tolerance for others.

        No doubt the feat was easy to Mr. Utterson. He was undemonstrative at the best. Even his friendships seemed founded in a good-natured catholicity of temper.
    """.trimIndent()

    @Test
    fun nibblesTileTheChapterExactly() {
        val nibs = NibbleEngine.segmentChapter(0, chapter, 30)   // small target → several nibbles
        assertTrue(nibs.size >= 2, "expected multiple nibbles, got ${nibs.size}")
        assertEquals(0, nibs.first().startChar, "first nibble must start at 0")
        assertEquals(chapter.length, nibs.last().endChar, "last nibble must end at chapter end")
        for (i in 1 until nibs.size) {
            assertEquals(nibs[i - 1].endChar, nibs[i].startChar, "nibbles must be contiguous")
        }
        // Concatenating every nibble reproduces the chapter exactly.
        val rebuilt = nibs.joinToString("") { chapter.substring(it.startChar, it.endChar) }
        assertEquals(chapter, rebuilt)
    }

    @Test
    fun segmentationIsDeterministic() {
        assertEquals(
            NibbleEngine.segmentChapter(0, chapter, 55),
            NibbleEngine.segmentChapter(0, chapter, 55),
        )
    }

    @Test
    fun differentRateCanChangeBoundaries() {
        val slow = NibbleEngine.segmentChapter(0, chapter, 20)
        val fast = NibbleEngine.segmentChapter(0, chapter, 200)
        assertTrue(slow.size >= fast.size, "a smaller target should not produce fewer nibbles")
    }

    @Test
    fun everyNibbleHasWords() {
        val nibs = NibbleEngine.segmentChapter(0, chapter, 30)
        assertTrue(nibs.all { it.wordCount > 0 })
    }

    @Test
    fun abbreviationsDoNotSplitSentences() {
        val s = NibbleEngine.splitSentences("Mr. Utterson went home. He slept well.")
        assertEquals(2, s.size)
    }

    @Test
    fun ellipsesAndInitialsHoldTogether() {
        val s = NibbleEngine.splitSentences("She paused... then spoke. H. G. Wells agreed.")
        assertEquals(2, s.size)
    }

    @Test
    fun nibbleContainingIsConsistentWithRanges() {
        val nibs = NibbleEngine.segmentChapter(0, chapter, 30)
        for (n in nibs) {
            assertEquals(n.index, NibbleEngine.nibbleContaining(nibs, 0, n.startChar)?.index)
            val mid = (n.startChar + n.endChar) / 2
            assertEquals(n.index, NibbleEngine.nibbleContaining(nibs, 0, mid)?.index)
        }
    }
}
