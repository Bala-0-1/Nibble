package com.nibble.app.core.domain

/**
 * On-device generation (§12). All four jobs run locally so nothing leaves the device.
 *
 * Two hard rules are enforced here:
 *  - Spoiler safety: prompts are grounded strictly in text BEFORE the reader's charOffset.
 *  - Roast safety: nudge/insult text is never free-written — the model picks among
 *    authored templates ([Wit]) and fills slots. Only plain, grounded content (recaps,
 *    glosses, stop-point summaries) is generated freely, and it goes through PLAIN tone.
 *
 * The capability ladder is NPU | ACCELERATED | CPU | NONE. A neural backend (Gemini Nano
 * via ML Kit GenAI, MediaPipe LLM, Apple Foundation Models) plugs in at the NPU/ACCELERATED
 * tiers; [HeuristicModel] is the always-available CPU tier — a private, no-network
 * extractive engine — and the authored fallback covers NONE.
 */
enum class Capability { NPU, ACCELERATED, CPU, NONE }

sealed interface AiTask {
    /** ≤ maxWords, past tense, plain tone — "what happened so far". */
    data class Recap(val maxWords: Int = 60) : AiTask
    /** ≤ 30 words about one character; one dry aside allowed. */
    data class WhoGloss(val name: String) : AiTask
    /** ≤ maxWords — the last thing the reader saw. */
    data class StopPoint(val maxWords: Int = 35) : AiTask
}

interface OnDeviceModel {
    val capability: Capability
    /** [grounding] is text strictly before the reader's charOffset (spoiler-safe). */
    suspend fun generate(task: AiTask, grounding: String): Result<String>
}

/**
 * The always-available CPU tier: extractive summarisation + entity glossing, entirely
 * on-device with no model download and no network. Deterministic and fast.
 */
class HeuristicModel : OnDeviceModel {
    override val capability = Capability.CPU

    override suspend fun generate(task: AiTask, grounding: String): Result<String> = runCatching {
        when (task) {
            is AiTask.Recap -> recap(grounding, task.maxWords)
            is AiTask.WhoGloss -> whoGloss(grounding, task.name) ?: error("No introduction found for ${task.name}.")
            is AiTask.StopPoint -> stopPoint(grounding, task.maxWords)
        }
    }

    private fun sentences(text: String): List<String> {
        val s = NibbleEngine.splitSentences(text)
        return s.map { text.substring(it.start, it.end).trim() }.filter { it.isNotEmpty() }
    }

    /** Extractive recap: the most salient sentences from what's been read, capped. */
    private fun recap(grounding: String, maxWords: Int): String {
        val window = grounding.takeLast(6000)
        val sents = sentences(window).filter { it.length in 30..240 }
        if (sents.isEmpty()) return "You're just getting started."
        val names = properNouns(grounding).keys
        // Score by presence of named characters + a mild length preference + recency.
        val scored = sents.mapIndexed { i, s ->
            val nameHits = names.count { s.contains(it) }
            val recency = i.toDouble() / sents.size
            s to (nameHits * 2.0 + recency + (s.length / 200.0))
        }
        val chosen = scored.sortedByDescending { it.second }.take(3)
            .sortedBy { sents.indexOf(it.first) }
            .map { it.first }
        return capWords(chosen.joinToString(" "), maxWords)
    }

    private fun whoGloss(grounding: String, name: String): String? {
        val first = sentences(grounding).firstOrNull { it.contains(name) } ?: return null
        val trimmed = capWords(first, 26)
        val aside = ASIDES[name.length.mod(ASIDES.size)]
        return "$trimmed $aside"
    }

    private fun stopPoint(grounding: String, maxWords: Int): String {
        val sents = sentences(grounding)
        val tail = sents.takeLast(2).joinToString(" ")
        return capWords(tail.ifBlank { grounding.takeLast(160) }, maxWords)
    }

    companion object {
        private val ASIDES = listOf(
            "You met them once and forgot instantly. That's fine.",
            "Don't worry, nobody remembered them either.",
            "There'll be a quiz. Kidding. Probably.",
        )

        private val STOP = setOf(
            "The", "And", "But", "He", "She", "It", "They", "We", "You", "His", "Her",
            "Their", "This", "That", "There", "Then", "Now", "With", "For", "Nor", "Yet",
            "Mr", "Mrs", "Ms", "Dr", "Chapter", "One", "Two", "When", "While", "As", "If",
            "So", "At", "In", "On", "Of", "To", "A", "An", "I", "My", "No", "Yes", "Oh",
        )

        /** Capitalised words that look like names, by frequency. */
        fun properNouns(text: String): Map<String, Int> =
            Regex("""\b[A-Z][a-z]{2,}\b""").findAll(text).map { it.value }
                .filter { it !in STOP }
                .groupingBy { it }.eachCount()
                .filterValues { it >= 2 }

        /** The most prominent character name in [text] (that also appears in [seenIn]). */
        fun prominentName(text: String, seenIn: String): String? {
            val here = properNouns(text)
            if (here.isEmpty()) return null
            return here.entries
                .filter { seenIn.contains(it.key) }
                .maxByOrNull { it.value }?.key
        }

        private fun capWords(text: String, maxWords: Int): String {
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size <= maxWords) return text.trim()
            return words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '—', '-') + "…"
        }
    }
}
