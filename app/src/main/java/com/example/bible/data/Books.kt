package com.example.bible.data

data class Book(val id: String, val name: String, val chapters: Int) {
    val isNewTestament: Boolean get() = Books.indexOf(id) >= Books.NT_START
}

object Books {
    const val NT_START = 39

    val all = listOf(
    Book("GEN", "Genesis", 50),
    Book("EXO", "Exodus", 40),
    Book("LEV", "Leviticus", 27),
    Book("NUM", "Numbers", 36),
    Book("DEU", "Deuteronomy", 34),
    Book("JOS", "Joshua", 24),
    Book("JDG", "Judges", 21),
    Book("RUT", "Ruth", 4),
    Book("1SA", "1 Samuel", 31),
    Book("2SA", "2 Samuel", 24),
    Book("1KI", "1 Kings", 22),
    Book("2KI", "2 Kings", 25),
    Book("1CH", "1 Chronicles", 29),
    Book("2CH", "2 Chronicles", 36),
    Book("EZR", "Ezra", 10),
    Book("NEH", "Nehemiah", 13),
    Book("EST", "Esther", 10),
    Book("JOB", "Job", 42),
    Book("PSA", "Psalms", 150),
    Book("PRO", "Proverbs", 31),
    Book("ECC", "Ecclesiastes", 12),
    Book("SNG", "Song of Solomon", 8),
    Book("ISA", "Isaiah", 66),
    Book("JER", "Jeremiah", 52),
    Book("LAM", "Lamentations", 5),
    Book("EZK", "Ezekiel", 48),
    Book("DAN", "Daniel", 12),
    Book("HOS", "Hosea", 14),
    Book("JOL", "Joel", 3),
    Book("AMO", "Amos", 9),
    Book("OBA", "Obadiah", 1),
    Book("JON", "Jonah", 4),
    Book("MIC", "Micah", 7),
    Book("NAM", "Nahum", 3),
    Book("HAB", "Habakkuk", 3),
    Book("ZEP", "Zephaniah", 3),
    Book("HAG", "Haggai", 2),
    Book("ZEC", "Zechariah", 14),
    Book("MAL", "Malachi", 4),
    Book("MAT", "Matthew", 28),
    Book("MRK", "Mark", 16),
    Book("LUK", "Luke", 24),
    Book("JHN", "John", 21),
    Book("ACT", "Acts", 28),
    Book("ROM", "Romans", 16),
    Book("1CO", "1 Corinthians", 16),
    Book("2CO", "2 Corinthians", 13),
    Book("GAL", "Galatians", 6),
    Book("EPH", "Ephesians", 6),
    Book("PHP", "Philippians", 4),
    Book("COL", "Colossians", 4),
    Book("1TH", "1 Thessalonians", 5),
    Book("2TH", "2 Thessalonians", 3),
    Book("1TI", "1 Timothy", 6),
    Book("2TI", "2 Timothy", 4),
    Book("TIT", "Titus", 3),
    Book("PHM", "Philemon", 1),
    Book("HEB", "Hebrews", 13),
    Book("JAS", "James", 5),
    Book("1PE", "1 Peter", 5),
    Book("2PE", "2 Peter", 3),
    Book("1JN", "1 John", 5),
    Book("2JN", "2 John", 1),
    Book("3JN", "3 John", 1),
    Book("JUD", "Jude", 1),
    Book("REV", "Revelation", 22),
    )

    private val byId = all.associateBy { it.id }
    private val index = all.withIndex().associate { it.value.id to it.index }

    operator fun get(id: String): Book = byId.getValue(id)
    fun indexOf(id: String): Int = index.getValue(id)
    val oldTestament get() = all.subList(0, NT_START)
    val newTestament get() = all.subList(NT_START, all.size)
}
