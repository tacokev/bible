package com.example.bible.data.importer

import android.util.Xml
import com.example.bible.data.Books
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.Writer
import java.util.TreeMap
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class ImportException(message: String) : Exception(message)

/** A Bible read from a file, before it's saved. Books are keyed by our book IDs. */
class ParsedBible(var format: String) {
    var title: String? = null
    var code: String? = null
    /** Book IDs in file order that we didn't recognise (e.g. apocrypha), for the warning list. */
    val skippedBooks = LinkedHashSet<String>()
    private val books = TreeMap<Int, TreeMap<Int, TreeMap<Int, String>>>()

    fun add(bookId: String?, chapter: Int, verse: Int, rawText: String) {
        if (bookId == null || chapter < 1 || verse < 1) return
        val text = clean(rawText)
        if (text.isEmpty()) return
        val chapters = books.getOrPut(Books.indexOf(bookId)) { TreeMap() }
        val verses = chapters.getOrPut(chapter) { TreeMap() }
        // Some files split one verse into several pieces; join them.
        verses[verse] = verses[verse]?.let { "$it $text" } ?: text
    }

    val bookCount get() = books.size
    val chapterCount get() = books.values.sumOf { it.size }
    val verseCount get() = books.values.sumOf { c -> c.values.sumOf { it.size } }
    val missingBooks get() = Books.all.filterIndexed { i, _ -> i !in books.keys }

    /** Writes the app's internal format (see BibleRepository): one paragraph per verse. */
    fun writeTo(out: Writer) {
        for ((bi, chapters) in books) {
            val book = Books.all[bi]
            out.write("#B\t${book.id}\t${book.name}\n")
            // Chapters are looked up by position, so write every chapter number up to the last one.
            for (c in 1..chapters.lastKey()) {
                out.write("#C\t$c\n")
                chapters[c]?.forEach { (v, text) -> out.write("P\nV\t$v\t$text\n") }
            }
        }
    }

    companion object {
        private val space = Regex("\\s+")
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace(space, " ").trim().trim('|').trim()
    }
}

/** Reads Zefania XML, OSIS XML, JSON and CSV/TSV Bibles (optionally inside a .zip or .gz). */
object Importer {

    fun parse(bytes: ByteArray, fileName: String): ParsedBible {
        val text = decode(unwrap(bytes))
        val start = text.trimStart()
        val bible = when {
            start.startsWith("<") -> {
                val head = start.take(4000).lowercase()
                when {
                    "<xmlbible" in head -> parseZefania(text)
                    "<osis" in head || "osistext" in head -> parseOsis(text)
                    else -> throw ImportException("This XML file isn't Zefania or OSIS, so it can't be read.")
                }
            }
            start.startsWith("{") || start.startsWith("[") -> parseJson(text)
            else -> parseCsv(text)
        }
        if (bible.verseCount < 10) {
            throw ImportException("No Bible verses were found in this file. Check it's a Zefania, OSIS, JSON or CSV Bible.")
        }
        tidyNames(bible, fileName)
        return bible
    }

    private val fileExts = setOf("gz", "zip", "xml", "osis", "zefania", "json", "csv", "tsv", "txt")
    private val genericCodes = setOf("bible", "main", "default", "text", "work")

    /** Picks a sensible name and short name from whatever metadata the file had. */
    internal fun tidyNames(bible: ParsedBible, fileName: String) {
        var title = bible.title?.trim()?.takeUnless { it.isEmpty() || '/' in it || '\\' in it || Regex("\\.(usfm|xml|txt|sfm)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        var code = bible.code?.trim()?.takeUnless { it.isEmpty() || it.lowercase() in genericCodes }
        // "YLT: Young's Literal Translation (1898)" → code YLT, title "Young's Literal Translation (1898)".
        title?.let { t ->
            Regex("^([A-Za-z0-9]{2,8})\\s*[:\\-–]\\s+(.+)$").find(t)?.let { m ->
                if (code == null) code = m.groupValues[1]
                title = m.groupValues[2]
            }
        }
        if (title == null) {
            var base = fileName
            while (base.substringAfterLast('.', "").lowercase() in fileExts) base = base.substringBeforeLast('.')
            title = base.replace('_', ' ').replace('-', ' ').trim().ifEmpty { "My translation" }
        }
        bible.title = title
        bible.code = (code ?: abbreviate(title!!)).uppercase().filter { it.isLetterOrDigit() }.take(6).ifEmpty { "MINE" }
    }

    private val minorWords = setOf("of", "the", "and", "in", "a", "an", "for", "to")

    /** "World English Bible" → "WEB", "Young's Literal Translation" → "YLT", "Luther 1912" → "LUTH". */
    fun abbreviate(title: String): String {
        val words = title.replace("'", "").replace("\u2019", "").split(Regex("[^A-Za-z]+"))
            .filter { it.isNotEmpty() && it.lowercase() !in minorWords }
        val initials = words.joinToString("") { it.take(1) }
        val code = if (initials.length >= 2) initials else words.firstOrNull()?.take(4) ?: "MINE"
        return code.uppercase().take(6)
    }

    // ---- container handling ----

    private fun unwrap(bytes: ByteArray): ByteArray {
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return unwrap(GZIPInputStream(ByteArrayInputStream(bytes)).readBytes())
        }
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var best: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && ext in setOf("xml", "osis", "json", "csv", "tsv", "txt")) {
                        val data = zip.readBytes()
                        if (best == null || data.size > best.size) best = data
                    }
                }
                return best ?: throw ImportException("The zip file doesn't contain an XML, JSON or CSV file.")
            }
        }
        return bytes
    }

    private fun decode(bytes: ByteArray): String {
        val s = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
        return s.removePrefix("﻿")
    }

    private fun xml(text: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(text))
    }

    private fun XmlPullParser.local() = name.substringAfter(':')
    private fun XmlPullParser.attr(name: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':').equals(name, true) }?.let { getAttributeValue(it) }

    private fun leadingInt(s: String?): Int? = s?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

    // ---- Zefania ----

    private fun parseZefania(text: String): ParsedBible {
        val bible = ParsedBible("Zefania XML")
        val p = xml(text)
        var book: String? = null
        var chapter = 0
        var verse = 0
        val sb = StringBuilder()
        var inVerse = false
        var noteDepth = 0
        var infoField: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local().uppercase()) {
                    "XMLBIBLE" -> bible.title = p.attr("biblename")
                    "TITLE" -> infoField = "title"
                    "IDENTIFIER" -> infoField = "id"
                    "BIBLEBOOK" -> {
                        val num = leadingInt(p.attr("bnumber"))
                        book = num?.let { BookNames.byNumber(it).takeIf { _ -> it in 1..66 } }
                            ?: p.attr("bname")?.let { BookNames.resolve(it) }
                        if (book == null) bible.skippedBooks += p.attr("bname") ?: "Book ${num ?: "?"}"
                    }
                    "CHAPTER" -> chapter = leadingInt(p.attr("cnumber")) ?: 0
                    "VERS" -> { verse = leadingInt(p.attr("vnumber")) ?: 0; sb.clear(); inVerse = true }
                    "NOTE" -> noteDepth++
                    "BR" -> if (inVerse) sb.append(' ')
                }
                XmlPullParser.TEXT -> when {
                    inVerse && noteDepth == 0 -> sb.append(p.text)
                    infoField == "title" -> bible.title = p.text.trim().ifEmpty { bible.title }
                    infoField == "id" -> bible.code = p.text.trim().takeIf { it.length in 1..8 } ?: bible.code
                }
                XmlPullParser.END_TAG -> when (p.local().uppercase()) {
                    "VERS" -> { bible.add(book, chapter, verse, sb.toString()); inVerse = false }
                    "NOTE" -> noteDepth--
                    "TITLE", "IDENTIFIER" -> infoField = null
                }
            }
        }
        return bible
    }

    // ---- OSIS ----

    private data class Ref(val book: String?, val chapter: Int, val verse: Int)

    private fun osisRef(id: String?): Ref? {
        val first = id?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: return null
        val parts = first.substringAfter(':').split('.')
        if (parts.size < 3) return null
        val idx = BookNames.osis.indexOfFirst { it.equals(parts[0], true) }
        val book = if (idx >= 0) Books.all[idx].id else BookNames.resolve(parts[0])
        return Ref(book, leadingInt(parts[1]) ?: return null, leadingInt(parts[2]) ?: return null)
    }

    /**
     * Handles both OSIS styles: container verses (`<verse osisID>text</verse>`) and milestones
     * (`<verse sID/>text<verse eID/>`). Footnotes and section titles are left out.
     */
    private fun parseOsis(text: String): ParsedBible {
        val bible = ParsedBible("OSIS XML")
        val p = xml(text)
        var current: Ref? = null
        var container = false
        val sb = StringBuilder()
        var skipDepth = 0
        var inHeader = false
        var headerField: String? = null
        fun finish() {
            current?.let { r -> bible.add(r.book, r.chapter, r.verse, sb.toString()) }
            current = null
            sb.clear()
        }

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local()) {
                    "header" -> inHeader = true
                    "work" -> if (inHeader && bible.code == null) bible.code = p.attr("osisWork")?.takeIf { it.length <= 8 }
                    "title" -> if (inHeader) headerField = "title" else skipDepth++
                    "verse" -> {
                        if (p.attr("eID") != null) {
                            finish()
                        } else {
                            finish()
                            val ref = osisRef(p.attr("osisID") ?: p.attr("sID"))
                            if (ref != null && ref.book == null) bible.skippedBooks += (p.attr("osisID") ?: "").substringBefore('.')
                            current = ref?.takeIf { it.book != null }
                            container = p.attr("sID") == null && !p.isEmptyElementTag
                        }
                    }
                    "note", "rdg" -> skipDepth++
                    "l" -> if (current != null && sb.isNotBlank()) sb.append('|')
                    "lb" -> if (current != null) sb.append(' ')
                    "chapter" -> if (p.attr("eID") != null) finish()
                }
                XmlPullParser.TEXT -> when {
                    headerField == "title" -> { if (bible.title == null) bible.title = p.text.trim().ifEmpty { null } }
                    current != null && skipDepth == 0 -> sb.append(p.text)
                }
                XmlPullParser.END_TAG -> when (p.local()) {
                    "header" -> inHeader = false
                    "title" -> if (headerField == "title") headerField = null else skipDepth--
                    "note", "rdg" -> skipDepth--
                    "verse" -> if (container) { finish(); container = false }
                    "chapter", "div", "book" -> if (!container && p.local() == "chapter") finish()
                }
            }
        }
        finish()
        return bible
    }

    // ---- JSON ----

    private val bookKeys = listOf("book", "book_name", "bookName", "book_id", "bookId", "b", "book_number", "bookNumber", "name", "abbrev", "abbreviation", "id")
    private val chapterKeys = listOf("chapter", "c", "chapter_number", "chapterNumber", "number")
    private val verseKeys = listOf("verse", "v", "verse_number", "verseNumber", "number")
    private val textKeys = listOf("text", "t", "content", "verse_text", "verseText", "value")

    private fun JSONObject.firstOf(keys: List<String>): Any? = keys.firstOrNull { has(it) && !isNull(it) }?.let { get(it) }
    private fun Any?.asInt(): Int? = when (this) {
        is Number -> toInt()
        is String -> leadingInt(this)
        else -> null
    }
    private fun bookFrom(v: Any?): String? = when (v) {
        is Number -> BookNames.byNumber(v.toInt())
        is String -> BookNames.resolve(v)
        else -> null
    }

    private fun parseJson(text: String): ParsedBible {
        val bible = ParsedBible("JSON")
        val root = try { JSONTokener(text).nextValue() } catch (e: Exception) {
            throw ImportException("The JSON file couldn't be read: ${e.message}")
        }
        when (root) {
            is JSONArray -> jsonArray(root, bible)
            is JSONObject -> {
                listOf("name", "translation", "title", "version").firstNotNullOfOrNull { k ->
                    root.opt(k) as? String ?: (root.opt(k) as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }
                }?.let { bible.title = it }
                listOf("abbreviation", "abbrev", "shortName", "code", "id").firstNotNullOfOrNull { k ->
                    (root.opt(k) as? String)?.takeIf { it.length in 1..8 }
                }?.let { bible.code = it }
                val inner = listOf("books", "verses", "bible", "data", "resultset", "result").firstNotNullOfOrNull { root.opt(it) }
                when (inner) {
                    is JSONArray -> jsonArray(inner, bible)
                    is JSONObject -> if (inner.has("row")) jsonArray(inner.getJSONArray("row"), bible) else jsonBookMap(inner, bible)
                    else -> jsonBookMap(root, bible)
                }
            }
            else -> throw ImportException("The JSON file doesn't contain a Bible.")
        }
        return bible
    }

    private fun jsonArray(arr: JSONArray, bible: ParsedBible) {
        val first = (0 until arr.length()).map { arr.opt(it) }.firstOrNull { it is JSONObject } as? JSONObject ?: return
        if (first.has("chapters")) {
            // Array of books. A full 66-book array is matched by position (safest with odd abbreviations).
            val positional = arr.length() == 66
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val id = if (positional) Books.all[i].id else bookFrom(b.firstOf(bookKeys))
                if (id == null) { bible.skippedBooks += b.firstOf(bookKeys)?.toString() ?: "Book ${i + 1}"; continue }
                jsonChapters(b.get("chapters"), id, bible)
            }
        } else if (first.has("field") && first.get("field") is JSONArray) {
            // scrollmapper style: {"field":[id, book, chapter, verse, text]}
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i)?.optJSONArray("field") ?: continue
                if (f.length() >= 5) bible.add(bookFrom(f.get(1)), f.get(2).asInt() ?: 0, f.get(3).asInt() ?: 0, f.optString(4))
            }
        } else {
            // Flat list of verses.
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val book = bookFrom(o.firstOf(bookKeys))
                if (book == null) { o.firstOf(bookKeys)?.let { bible.skippedBooks += it.toString() }; continue }
                bible.add(book, o.firstOf(chapterKeys).asInt() ?: 0, o.firstOf(verseKeys).asInt() ?: 0, o.firstOf(textKeys)?.toString().orEmpty())
            }
        }
    }

    /** chapters as [[verse, …], …], [{chapter, verses:[…]}, …] or {"1": …}. */
    private fun jsonChapters(chapters: Any, bookId: String, bible: ParsedBible) {
        fun verses(v: Any?, chapter: Int) {
            when (v) {
                is JSONArray -> for (j in 0 until v.length()) when (val e = v.get(j)) {
                    is String -> bible.add(bookId, chapter, j + 1, e)
                    is JSONObject -> bible.add(bookId, chapter, e.firstOf(verseKeys).asInt() ?: (j + 1), e.firstOf(textKeys)?.toString().orEmpty())
                }
                is JSONObject -> v.keys().forEach { k -> bible.add(bookId, chapter, leadingInt(k) ?: 0, v.optString(k)) }
            }
        }
        when (chapters) {
            is JSONArray -> for (i in 0 until chapters.length()) when (val c = chapters.get(i)) {
                is JSONArray -> verses(c, i + 1)
                is JSONObject -> verses(c.opt("verses") ?: c.opt("content"), c.firstOf(chapterKeys).asInt() ?: (i + 1))
            }
            is JSONObject -> chapters.keys().forEach { k -> verses(chapters.get(k), leadingInt(k) ?: 0) }
        }
    }

    /** {"Genesis": {"1": {"1": "text"}}} or {"Genesis": [[…]]}. */
    private fun jsonBookMap(obj: JSONObject, bible: ParsedBible) {
        obj.keys().forEach { k ->
            val id = BookNames.resolve(k)
            val v = obj.get(k)
            if (v !is JSONObject && v !is JSONArray) return@forEach
            if (id == null) bible.skippedBooks += k else jsonChapters(v, id, bible)
        }
    }

    // ---- CSV / TSV ----

    private fun splitRow(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' && (quoted || sb.isEmpty()) -> quoted = !quoted
                c == delim && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private val refRegex = Regex("""^\s*(.+?)\s+(\d+)\s*[:.]\s*(\d+)\s*$""")

    private fun parseCsv(text: String): ParsedBible {
        val bible = ParsedBible("CSV")
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) throw ImportException("The file is empty.")
        val sample = lines.take(20)
        val delim = listOf('\t', ',', ';', '|').maxBy { d -> sample.sumOf { l -> l.count { it == d } } }
        if (delim == '\t') bible.format = "TSV"

        val head = splitRow(lines[0], delim).map { it.trim().lowercase() }
        fun col(keys: List<String>) = head.indexOfFirst { h -> keys.any { it.equals(h, true) } }
        val hasHeader = col(textKeys) >= 0 && (col(chapterKeys) >= 0 || col(listOf("reference", "ref")) >= 0)
        val rows = if (hasHeader) lines.drop(1) else lines

        var bCol: Int; var cCol: Int; var vCol: Int; var tCol: Int; var refCol = -1
        if (hasHeader) {
            bCol = col(bookKeys.filter { it != "id" && it != "name" } + "book")
            cCol = col(chapterKeys); vCol = col(verseKeys); tCol = col(textKeys)
            refCol = col(listOf("reference", "ref"))
        } else {
            val first = splitRow(rows[0], delim)
            when {
                first.size >= 5 && first.subList(1, 4).all { it.trim().toIntOrNull() != null } -> { bCol = 1; cCol = 2; vCol = 3; tCol = 4 }
                first.size >= 4 && first[1].trim().toIntOrNull() != null && first[2].trim().toIntOrNull() != null -> { bCol = 0; cCol = 1; vCol = 2; tCol = 3 }
                first.size >= 2 && refRegex.matches(first[0]) -> { bCol = -1; cCol = -1; vCol = -1; tCol = 1; refCol = 0 }
                else -> throw ImportException("Couldn't work out the columns. Use book, chapter, verse, text (with or without a header row).")
            }
        }

        for (line in rows) {
            val f = splitRow(line, delim)
            // Unquoted text containing the delimiter spills into extra columns, so join the rest back up.
            val text = if (tCol >= 0 && tCol < f.size) f.subList(tCol, f.size).joinToString(delim.toString()) else continue
            if (refCol >= 0 && (bCol < 0 || cCol < 0)) {
                val m = refRegex.find(f[refCol]) ?: continue
                val book = BookNames.resolve(m.groupValues[1])
                if (book == null) { bible.skippedBooks += m.groupValues[1]; continue }
                bible.add(book, m.groupValues[2].toInt(), m.groupValues[3].toInt(), text)
            } else {
                val rawBook = f.getOrNull(bCol) ?: continue
                val book = BookNames.resolve(rawBook)
                if (book == null) { bible.skippedBooks += rawBook; continue }
                bible.add(book, leadingInt(f.getOrNull(cCol)) ?: 0, leadingInt(f.getOrNull(vCol)) ?: 0, text)
            }
        }
        return bible
    }
}
