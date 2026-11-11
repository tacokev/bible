package com.example.bible.data.importer

import com.example.bible.data.Books

/** Resolves the many ways files name books ("1 Corinthians", "1Cor", "I Co", "Song of Solomon", 46…) to our IDs. */
object BookNames {
    /** OSIS book abbreviations, in the same order as [Books.all]. */
    val osis = listOf(
        "Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs",
        "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov", "Eccl", "Song", "Isa", "Jer", "Lam",
        "Ezek", "Dan", "Hos", "Joel", "Amos", "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech",
        "Mal", "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil", "Col",
        "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas", "1Pet", "2Pet", "1John",
        "2John", "3John", "Jude", "Rev",
    )

    private val extra = mapOf(
        "psalm" to "PSA", "pss" to "PSA", "canticles" to "SNG", "songofsolomon" to "SNG", "songofsongs" to "SNG",
        "sos" to "SNG", "qoheleth" to "ECC", "ecclesiastes" to "ECC", "apocalypse" to "REV",
        "revelationofjohn" to "REV", "revelations" to "REV", "jn" to "JHN", "jhn" to "JHN", "mt" to "MAT",
        "mk" to "MRK", "mr" to "MRK", "lk" to "LUK", "lu" to "LUK", "ac" to "ACT", "jas" to "JAS", "jm" to "JAS",
        "phm" to "PHM", "philem" to "PHM", "php" to "PHP", "jud" to "JUD", "jdg" to "JDG", "jg" to "JDG",
        "ez" to "EZK", "eze" to "EZK", "dt" to "DEU", "nu" to "NUM", "nm" to "NUM", "lv" to "LEV", "ex" to "EXO",
        "gn" to "GEN", "ge" to "GEN", "rt" to "RUT", "ru" to "RUT", "ne" to "NEH", "es" to "EST", "jb" to "JOB",
        "pr" to "PRO", "prv" to "PRO", "ec" to "ECC", "is" to "ISA", "jr" to "JER", "je" to "JER", "la" to "LAM",
        "lm" to "LAM", "dn" to "DAN", "da" to "DAN", "ho" to "HOS", "jl" to "JOL", "am" to "AMO", "ob" to "OBA",
        "jon" to "JON", "mi" to "MIC", "na" to "NAM", "hb" to "HAB", "zp" to "ZEP", "hg" to "HAG", "zc" to "ZEC",
        "ml" to "MAL", "ro" to "ROM", "rm" to "ROM", "ga" to "GAL", "ep" to "EPH", "col" to "COL", "tit" to "TIT",
        "he" to "HEB", "re" to "REV", "rv" to "REV",
    )

    private val keys: Map<String, String> = buildMap {
        Books.all.forEachIndexed { i, b ->
            put(norm(b.id), b.id)
            put(norm(b.name), b.id)
            put(norm(osis[i]), b.id)
        }
        extra.forEach { (k, v) -> put(k, v) }
    }

    /** Lower-case, no spaces/dots, and leading roman numerals / ordinals turned into digits. */
    fun norm(raw: String): String {
        var s = raw.trim().lowercase().replace(".", " ").replace(Regex("\\s+"), " ")
        s = s.removePrefix("the ").removePrefix("book of ").removePrefix("gospel of ").removePrefix("gospel according to ")
        s = s.replace(Regex("^(iii|third|3rd) "), "3 ").replace(Regex("^(ii|second|2nd) "), "2 ")
            .replace(Regex("^(i|first|1st) "), "1 ")
        s = s.replace(Regex("^(st |saint )"), "")
        return s.replace(" ", "")
    }

    /** Book ID for a name/abbreviation, or for a 1-based book number. Null if unknown (e.g. apocrypha). */
    fun resolve(raw: String): String? {
        raw.trim().toIntOrNull()?.let { return byNumber(it) }
        val k = norm(raw)
        if (k.isEmpty()) return null
        keys[k]?.let { return it }
        // Unique prefix of a full name, e.g. "genes", "1corinth", "philipp".
        if (k.length >= 3) {
            val hits = Books.all.filter { norm(it.name).startsWith(k) }
            if (hits.size == 1) return hits[0].id
        }
        return null
    }

    fun byNumber(n: Int): String? = Books.all.getOrNull(n - 1)?.id
}
