package com.nibble.app.core.domain

/**
 * Badges are DERIVED from real session rows and the finished-book set (§10) — never
 * stored counters. Labels keep the dry register but state plain facts; a badge that
 * hasn't been earned simply doesn't exist (no greyed-out guilt trips).
 */
object Badges {

    enum class BadgeTone { CLAY, MOSS, PAPER }

    data class Badge(val label: String, val tone: BadgeTone)

    fun derive(sessions: List<Session>, finishedBooks: Int): List<Badge> {
        val out = ArrayList<Badge>()
        val real = sessions.filter { it.activeSeconds >= 60 }

        if (finishedBooks > 0) {
            val label = if (finishedBooks == 1) "Finished a book" else "Finished $finishedBooks books"
            out += Badge(label, BadgeTone.MOSS)
        }

        val longest = Stats.longestStreak(sessions)
        if (longest >= 3) out += Badge("Longest streak · $longest days", BadgeTone.CLAY)

        val words = sessions.sumOf { it.words }
        wordMilestone(words)?.let { out += Badge(it, BadgeTone.CLAY) }

        val nightOwl = real.count { Stats.hourOf(it) >= 23 || Stats.hourOf(it) < 4 }
        if (nightOwl >= 3) out += Badge("Night owl · $nightOwl×", BadgeTone.PAPER)

        val earlyBird = real.count { Stats.hourOf(it) in 5..7 }
        if (earlyBird >= 3) out += Badge("Early bird · $earlyBird×", BadgeTone.PAPER)

        val chapterSits = real.count { it.mode == ReaderMode.CHAPTER }
        if (chapterSits >= 5) out += Badge("Sat through a chapter · $chapterSits×", BadgeTone.PAPER)

        if (out.isEmpty() && real.isNotEmpty()) out += Badge("First nibble", BadgeTone.CLAY)
        return out
    }

    private fun wordMilestone(words: Int): String? = when {
        words >= 100_000 -> "100,000 words eaten"
        words >= 50_000 -> "50,000 words eaten"
        words >= 10_000 -> "10,000 words eaten"
        words >= 1_000 -> "1,000 words eaten"
        else -> null
    }
}
