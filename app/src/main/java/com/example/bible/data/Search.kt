package com.example.bible.data

enum class SearchScope(val label: String) { All("All"), Old("Old Testament"), New("New Testament") }

data class SearchHit(val bookId: String, val chapter: Int, val verse: Int, val text: String) {
    val reference get() = "${Books[bookId].name} $chapter:$verse"
}

data class SearchResults(val query: String, val hits: List<SearchHit>, val total: Int)

/** A parsed passage reference such as "John 3:16", "1 cor 13" or "Psalm 23". */
data class PassageRef(val bookId: String, val chapter: Int, val verse: Int?) {
    val label get() = Books[bookId].name + " $chapter" + (verse?.let { ":$it" } ?: "")
}

object SearchText {
    /** Lower-case and straighten curly quotes so "Lord's" matches "Lord’s". */
    fun normalize(s: String): String = s.lowercase()
        .replace('’', '\'').replace('‘', '\'')
        .replace('“', '"').replace('”', '"')

    fun terms(query: String): List<String> =
        normalize(query).split(Regex("\\s+")).map { it.trim('"', ',', '.', ';', ':', '!', '?') }.filter { it.isNotEmpty() }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\''

    /**
     * Index of the next place [term] occurs at the start of a word in [hay] (already normalized).
     * Short terms (3 letters or fewer, e.g. "of", "the") must be the whole word so they don't
     * match inside "there" or "delighting"; longer terms may continue ("shepherd" → "shepherds").
     */
    fun indexOfWord(hay: String, term: String, from: Int = 0): Int {
        var i = hay.indexOf(term, from)
        while (i >= 0) {
            val startOk = i == 0 || !isWordChar(hay[i - 1])
            val end = i + term.length
            val endOk = term.length > 3 || end == hay.length || !isWordChar(hay[end])
            if (startOk && endOk) return i
            i = hay.indexOf(term, i + 1)
        }
        return -1
    }

    fun matches(normalizedText: String, terms: List<String>) = terms.all { indexOfWord(normalizedText, it) >= 0 }

    /** All ranges where a term occurs at a word start in [text], merged and sorted. */
    fun matchRanges(text: String, terms: List<String>): List<IntRange> {
        val hay = normalize(text)
        val ranges = mutableListOf<IntRange>()
        for (t in terms) {
            var i = indexOfWord(hay, t)
            while (i >= 0) {
                ranges += i until i + t.length
                i = indexOfWord(hay, t, i + t.length)
            }
        }
        ranges.sortBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (r in ranges) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            else merged += r
        }
        return merged
    }

    private val refPattern = Regex("""^\s*([1-3]?\s*[a-z][a-z .]*?)\s*(\d+)(?:\s*[:.]\s*(\d+))?\s*$""")

    private val aliases = mapOf(
        "psalm" to "PSA", "ps" to "PSA", "jn" to "JHN", "mt" to "MAT", "mk" to "MRK", "lk" to "LUK",
        "rom" to "ROM", "rev" to "REV", "song of songs" to "SNG", "song of solomon" to "SNG", "songs" to "SNG",
        "phil" to "PHP", "philip" to "PHP", "jude" to "JUD", "judg" to "JDG",
    )

    /** Parses "John 3:16", "1cor 13", "gen 1" etc. Returns null if it isn't a valid reference. */
    fun parseReference(query: String): PassageRef? {
        val m = refPattern.find(normalize(query)) ?: return null
        val rawBook = m.groupValues[1].replace(".", "").replace(Regex("\\s+"), " ").trim()
        val chapter = m.groupValues[2].toInt()
        val verse = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
        val key = rawBook.replace(" ", "")
        val book = aliases[rawBook]?.let { Books[it] }
            ?: Books.all.firstOrNull { it.id.lowercase() == key }
            ?: Books.all.firstOrNull { it.name.lowercase().replace(" ", "").startsWith(key) && key.length >= 2 }
            ?: return null
        if (chapter !in 1..book.chapters) return null
        if (verse != null && verse < 1) return null
        return PassageRef(book.id, chapter, verse)
    }
}
