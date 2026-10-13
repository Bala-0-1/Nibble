package com.nibble.app.core.domain

import kotlin.math.absoluteValue

/**
 * Parses an already-unzipped EPUB (§10): META-INF/container.xml → OPF → spine order,
 * each XHTML document stripped to plain text preserving paragraph boundaries. Platform
 * code does the unzip; this stays pure Kotlin.
 */
object EpubParser {

    class DrmException(message: String) : Exception(message)

    private val COVER_GROUNDS = listOf(
        Triple(0xFF221E1AL, 0xFFD9A85CL, 0xFFF7F2E8L),
        Triple(0xFF3C5C4EL, 0xFFBFD3C6L, 0xFFF2F6F1L),
        Triple(0xFFA96A4EL, 0xFFF6ECE4L, 0xFFF6ECE4L),
        Triple(0xFF54402FL, 0xFFD89173L, 0xFFEFDDD1L),
    )

    /** Image formats the platform decoders handle; anything else (SVG…) is skipped. */
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    /**
     * [saveImage] persists an extracted image (cover or illustration) and returns its
     * stored path, or null to skip. The default no-op keeps the parser pure — callers
     * without a disk (tests, platforms without import) simply get a text-only book.
     */
    fun parse(
        entries: Map<String, ByteArray>,
        fallbackTitle: String,
        saveImage: (relativeName: String, bytes: ByteArray) -> String? = { _, _ -> null },
    ): Book {
        if (entries.keys.any { it.startsWith("META-INF/") && it.contains("rights", true) } ||
            entries.keys.any { it.endsWith(".xml", true) && it.contains("encryption", true) }
        ) {
            throw DrmException("This file is copy-protected and can't be opened.")
        }

        val container = entries.entryText("META-INF/container.xml")
            ?: throw IllegalStateException("Not a valid EPUB (no container.xml).")
        val opfPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
            ?: throw IllegalStateException("Not a valid EPUB (no OPF path).")
        val opf = entries.entryText(opfPath) ?: throw IllegalStateException("EPUB package file missing.")
        val opfDir = opfPath.substringBeforeLast('/', "")

        val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            .find(opf)?.groupValues?.get(1)?.let { htmlText(it).trim() }?.ifBlank { null } ?: fallbackTitle
        val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
            .find(opf)?.groupValues?.get(1)?.let { htmlText(it).trim() }?.ifBlank { null } ?: "Unknown"
        val id = "import-" + (title.hashCode().absoluteValue.toString(36))

        // manifest: id -> (href, media-type, properties)
        data class Item(val href: String, val media: String, val props: String)
        val manifest = HashMap<String, Item>()
        for (m in Regex("""<item\b[^>]*>""").findAll(opf)) {
            val tag = m.value
            val idAttr = attr(tag, "id")
            val href = attr(tag, "href")
            if (idAttr != null && href != null) {
                manifest[idAttr] = Item(href, attr(tag, "media-type") ?: "", attr(tag, "properties") ?: "")
            }
        }
        // spine order
        val spineIds = Regex("""<itemref\b[^>]*\bidref="([^"]+)"""").findAll(opf).map { it.groupValues[1] }.toList()

        // One writer for every extracted image, dedup'd by zip entry (repeated flourishes).
        val savedByEntry = HashMap<String, String>()
        var imgSeq = 0
        fun store(zipPath: String): String? {
            savedByEntry[zipPath]?.let { return it }
            val ext = zipPath.substringAfterLast('.', "").lowercase()
            if (ext !in IMAGE_EXTS) return null
            val bytes = entries.entryBytes(zipPath) ?: return null
            val saved = saveImage("$id/img-${imgSeq++}.$ext", bytes) ?: return null
            savedByEntry[zipPath] = saved
            return saved
        }

        // Cover: EPUB3 properties="cover-image" → EPUB2 <meta name="cover"> → name hints.
        val coverHref = manifest.values.firstOrNull { it.props.contains("cover-image") }?.href
            ?: (Regex("""<meta\b[^>]*\bname="cover"[^>]*\bcontent="([^"]+)"""").find(opf)
                ?: Regex("""<meta\b[^>]*\bcontent="([^"]+)"[^>]*\bname="cover"""").find(opf))
                ?.groupValues?.get(1)?.let { manifest[it]?.href }
            ?: manifest.entries.firstOrNull { (key, v) ->
                v.media.startsWith("image/") && (key.contains("cover", true) || v.href.contains("cover", true))
            }?.value?.href
        val coverPath = coverHref?.let { store(resolve(opfDir, it)) }

        val chapters = ArrayList<SpineItem>()
        for (idref in spineIds) {
            val href = manifest[idref]?.href ?: continue
            val path = resolve(opfDir, href).substringBefore('#')
            val doc = entries.entryText(path) ?: continue
            // Lift illustrations out FIRST: each <img>/<image> becomes a token paragraph
            // so its position survives stripping + reflow; hrefs resolve against the
            // chapter file's own directory, not the OPF's.
            val docDir = path.substringBeforeLast('/', "")
            val extracted = ArrayList<Pair<String, String>>()   // saved path to alt text
            val withTokens = Regex("""(?is)<(img|image)\b[^>]*/?>""").replace(doc) { m ->
                val tag = m.value
                val src = attr(tag, "src") ?: attr(tag, "xlink:href") ?: attr(tag, "href")
                val saved = src?.takeIf { !it.startsWith("data:") }
                    ?.let { store(resolve(docDir, it).substringBefore('#')) }
                if (saved == null) " " else {
                    extracted.add(saved to (attr(tag, "alt") ?: ""))
                    "\n\n$TOKEN${extracted.size - 1}$TOKEN_END\n\n"
                }
            }
            val (text, anchors) = liftAnchors(reflow(htmlText(withTokens)), extracted)
            if (text.length >= 200 && !isFrontMatter(text)) {
                chapters.add(SpineItem(chapters.size, chapterTitle(doc, chapters.size), text, anchors))
            }
        }
        if (chapters.isEmpty()) throw IllegalStateException("No readable text found in this EPUB.")

        val palette = COVER_GROUNDS[title.hashCode().absoluteValue % COVER_GROUNDS.size]
        return Book(
            id = id,
            title = title,
            shortTitle = if (title.length > 22) title.take(20).trim() + "…" else title,
            author = author,
            source = BookSource.IMPORT,
            coverGround = palette.first,
            coverAccent = palette.second,
            onCover = palette.third,
            blurb = "Imported · $author",
            spine = chapters,
            coverPath = coverPath,
        )
    }

    // A token no book contains; flanked by blank lines it rides through reflow as its
    // own paragraph, then liftAnchors removes it and records where it sat.
    private const val TOKEN = "@@NIBIMG"
    private const val TOKEN_END = "@@"

    /** Remove image tokens from [text]; anchor each image at the paragraph boundary it held. */
    internal fun liftAnchors(text: String, images: List<Pair<String, String>>): Pair<String, List<ImageAnchor>> {
        if (images.isEmpty()) return text to emptyList()
        val rx = Regex("""@@NIBIMG(\d+)@@""")
        val out = StringBuilder()
        val raw = ArrayList<Pair<Int, Int>>()   // anchor offset (pre-clamp) to image index
        var pos = 0
        for (m in rx.findAll(text)) {
            val seg = text.substring(pos, m.range.first).trim()
            pos = m.range.last + 1
            if (seg.isNotEmpty()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append(seg)
            }
            // The image sits at the start of whatever paragraph follows it.
            val at = if (out.isEmpty()) 0 else out.length + 2
            m.groupValues[1].toIntOrNull()?.let { raw.add(at to it) }
        }
        val tail = text.substring(pos).trim()
        if (tail.isNotEmpty()) {
            if (out.isNotEmpty()) out.append("\n\n")
            out.append(tail)
        }
        val clean = out.toString()
        val cap = (clean.length - 1).coerceAtLeast(0)
        val anchors = raw.mapNotNull { (at, idx) ->
            images.getOrNull(idx)?.let { (p, alt) -> ImageAnchor(at.coerceAtMost(cap), p, alt) }
        }
        return clean to anchors
    }

    /** Attribute value, honouring the OPENING quote — apostrophes inside "…" are content. */
    private fun attr(tag: String, name: String): String? {
        val m = Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        return m.groupValues[1].ifEmpty { m.groupValues[2] }
    }

    /** Drop Project Gutenberg license / title / transcriber pages that sit in the spine. */
    private fun isFrontMatter(text: String): Boolean {
        val head = text.take(1200)
        val g = head.contains("Project Gutenberg", ignoreCase = true)
        if (g && Regex("""(no cost|restrictions whatsoever|Project Gutenberg License|START OF THE PROJECT|END OF THE PROJECT|www\.gutenberg\.org)""", RegexOption.IGNORE_CASE).containsMatchIn(head)) return true
        // A bare title/credits page: short, and mostly "Title/Author/Release date/Language".
        if (text.length < 700 && Regex("""(Release date|Most recently updated|Credits:|Language:\s*English)""").containsMatchIn(text)) return true
        return false
    }

    private fun Map<String, ByteArray>.entryBytes(path: String): ByteArray? {
        val key = keys.firstOrNull { it.equals(path, true) || it.equals(path.removePrefix("/"), true) }
        return key?.let { this[it] }
    }

    private fun Map<String, ByteArray>.entryText(path: String): String? = entryBytes(path)?.decodeToString()

    private fun resolve(dir: String, href: String): String {
        if (dir.isEmpty()) return normalize(href)
        return normalize("$dir/$href")
    }

    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (p in path.split('/')) when (p) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(p)
        }
        return parts.joinToString("/")
    }

    private fun chapterTitle(doc: String, index: Int): String {
        val h = Regex("""<h[1-3][^>]*>(.*?)</h[1-3]>""", RegexOption.DOT_MATCHES_ALL).find(doc)
            ?.groupValues?.get(1)?.let { htmlText(it).trim() }
        return if (!h.isNullOrBlank() && h.length < 60) h else "Chapter ${index + 1}"
    }

    /** Strip HTML to text, turning block elements into paragraph breaks. */
    private fun htmlText(html: String): String {
        var s = html
        s = s.replace(Regex("""(?is)<(script|style|head)[^>]*>.*?</\1>"""), " ")
        s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")
        s = s.replace(Regex("""(?i)</(p|div|h[1-6]|li|blockquote|section|article)>"""), "\n\n")
        s = s.replace(Regex("""<[^>]+>"""), "")
        s = decodeEntities(s)
        return s
    }

    private fun decodeEntities(s: String): String {
        var r = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–")
            .replace("&ldquo;", "“").replace("&rdquo;", "”")
            .replace("&lsquo;", "‘").replace("&rsquo;", "’").replace("&hellip;", "…")
        r = Regex("""&#(\d+);""").replace(r) { m -> m.groupValues[1].toIntOrNull()?.let { it.toChar().toString() } ?: m.value }
        r = Regex("""&#x([0-9a-fA-F]+);""").replace(r) { m -> m.groupValues[1].toIntOrNull(16)?.let { it.toChar().toString() } ?: m.value }
        return r
    }

    private fun reflow(text: String): String =
        text.split(Regex("\n\\s*\n"))
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ").replace(Regex(" {2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
}
