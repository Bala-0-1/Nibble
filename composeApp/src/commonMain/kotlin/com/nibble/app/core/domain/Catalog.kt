package com.nibble.app.core.domain

import com.nibble.app.resources.Res

/** The bundled catalog. Full public-domain texts are ingested into real chapter spines. */
object Catalog {

    data class Meta(
        val id: String,
        val file: String,
        val title: String,
        val shortTitle: String,
        val author: String,
        val blurb: String,
        val ground: Long,
        val accent: Long,
        val onCover: Long,
    )

    val bundled = listOf(
        Meta("jekyll", "files/books/jekyll.txt", "Dr Jekyll and Mr Hyde", "Jekyll & Hyde", "R. L. Stevenson",
            "A maid sees something out of a window. It goes badly for everyone.", 0xFF221E1A, 0xFFD9A85C, 0xFFF7F2E8),
        Meta("dorian", "files/books/dorian.txt", "The Picture of Dorian Gray", "Dorian Gray", "Oscar Wilde",
            "A painter, a portrait, and a summer that refuses to end.", 0xFF3C5C4E, 0xFFBFD3C6, 0xFFF2F6F1),
        Meta("timemachine", "files/books/timemachine.txt", "The Time Machine", "The Time Machine", "H. G. Wells",
            "A dinner party, a small brass machine, and an argument about time.", 0xFFA96A4E, 0xFFF6ECE4, 0xFFF6ECE4),
        Meta("metamorphosis", "files/books/metamorphosis.txt", "The Metamorphosis", "The Metamorphosis", "Franz Kafka",
            "A man wakes up as something else entirely. His family adjusts, slowly.", 0xFFEFE6D2, 0xFF221E1A, 0xFF221E1A),
    )

    suspend fun ingestAll(): List<Book> = bundled.mapNotNull { meta ->
        runCatching {
            val raw = Res.readBytes(meta.file).decodeToString()
            val spine = BookIngest.chapters(raw)
            if (spine.isEmpty()) return@runCatching null
            Book(
                id = meta.id,
                title = meta.title,
                shortTitle = meta.shortTitle,
                author = meta.author,
                source = BookSource.CATALOG,
                coverGround = meta.ground,
                coverAccent = meta.accent,
                onCover = meta.onCover,
                blurb = meta.blurb,
                spine = spine,
            )
        }.getOrNull()
    }
}
