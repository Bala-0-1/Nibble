package com.nibble.app.core.domain

/**
 * Turns a full plain-text book (Project Gutenberg, or the extracted text of an EPUB
 * spine document) into a clean chapter spine (§10). Boilerplate is stripped, chapters
 * are detected by blank-line-isolated headings (which skips tables of contents), and
 * each chapter is reflowed so paragraphs are single-spaced with blank lines between.
 */
object BookIngest {

    private val START = Regex(""".*\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG.*""", RegexOption.IGNORE_CASE)
    private val END = Regex(""".*\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG.*""", RegexOption.IGNORE_CASE)

    fun chapters(raw: String): List<SpineItem> {
        val body = stripBoilerplate(raw)
        val lines = body.split("\n")
        val heads = ArrayList<Pair<Int, String>>()   // line index, title
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val prevBlank = i == 0 || lines[i - 1].isBlank()
            val nextBlank = i == lines.lastIndex || lines[i + 1].isBlank()
            if (prevBlank && nextBlank && isHeading(line)) heads.add(i to line)
        }
        if (heads.isEmpty()) {
            val text = reflow(body)
            return if (text.isBlank()) emptyList() else listOf(SpineItem(0, "", text))
        }
        val chapters = ArrayList<SpineItem>()
        for (h in heads.indices) {
            val from = heads[h].first + 1
            val to = if (h + 1 < heads.size) heads[h + 1].first else lines.size
            val text = reflow(lines.subList(from, to.coerceAtMost(lines.size)).joinToString("\n"))
            if (text.length >= 400) {   // drop TOC lines / stray short headings
                chapters.add(SpineItem(chapters.size, prettyTitle(heads[h].second), text))
            }
        }
        return chapters.ifEmpty { listOf(SpineItem(0, "", reflow(body))) }
    }

    private fun stripBoilerplate(raw: String): String {
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.split("\n")
        var start = 0
        var end = lines.size
        for (i in lines.indices) if (START.matches(lines[i])) { start = i + 1; break }
        for (i in lines.indices.reversed()) if (END.matches(lines[i])) { end = i; break }
        return lines.subList(start.coerceAtMost(lines.size), end.coerceIn(start, lines.size)).joinToString("\n")
    }

    private fun isHeading(line: String): Boolean {
        if (line.length > 48) return false
        if (Regex("""^(CHAPTER|Chapter|PART|Part|BOOK|Book|LETTER|Letter)\b.*""").matches(line)) return true
        if (Regex("""^[IVXLCDM]{1,7}\.?$""").matches(line)) return true
        if (Regex("""^\d{1,3}\.?$""").matches(line)) return true
        if (Regex("""^[A-Z][A-Z0-9 .,'’!?;:()\-]{3,44}$""").matches(line)) {
            val letters = line.count { it.isLetter() }
            val upper = line == line.uppercase()
            if (letters >= 4 && upper && line != "CONTENTS" && line != "ILLUSTRATIONS") return true
        }
        return false
    }

    /** Split into paragraphs on blank lines; join wrapped lines within a paragraph. */
    private fun reflow(text: String): String {
        val cleaned = text.replace(Regex("""\[Illustration[^\]]*\]"""), "")
        val paras = cleaned.split(Regex("\n\\s*\n"))
        return paras
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ").replace(Regex(" {2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    private fun prettyTitle(raw: String): String {
        val t = raw.trim().removeSuffix(".").trim()
        // Title-case shouted headings; leave roman numerals / "Chapter I" as-is.
        return if (t == t.uppercase() && t.any { it.isLetter() } && t.length > 3) {
            t.split(" ").joinToString(" ") { w ->
                if (w.length <= 3 && w == w.uppercase() && w.all { it.isLetter() }) w.lowercase().replaceFirstChar { it.uppercase() }
                else w.lowercase().replaceFirstChar { it.uppercase() }
            }
        } else t
    }
}
