package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

data class Verse(val number: Int, val lines: List<String>) {
    val isPoetry get() = lines.size > 1
}

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Subtitle(val text: String) : Block
    data class Paragraph(val verses: List<Verse>) : Block
}

data class Chapter(val book: Book, val number: Int, val blocks: List<Block>) {
    val verseCount get() = blocks.sumOf { (it as? Block.Paragraph)?.verses?.size ?: 0 }
}

/**
 * Loads a whole translation into memory (raw lines grouped per chapter) and parses
 * chapters on demand. Only the most recently used translation is kept in memory.
 */
class BibleRepository(private val context: Context) {
    private class Loaded(val translation: Translation, val chapters: Map<String, List<List<String>>>)

    private val mutex = Mutex()
    private var loaded: Loaded? = null

    suspend fun chapter(translation: Translation, bookId: String, number: Int): Chapter {
        val data = load(translation)
        val raw = data.chapters[bookId]?.getOrNull(number - 1).orEmpty()
        return withContext(Dispatchers.Default) { parse(Books[bookId], number, raw) }
    }

    /**
     * Finds verses containing every word of [query] (case-insensitive, any order).
     * Returns at most [limit] hits but counts them all.
     */
    suspend fun search(translation: Translation, query: String, scope: SearchScope, limit: Int = 300): SearchResults {
        val terms = SearchText.terms(query)
        if (terms.isEmpty()) return SearchResults(query, emptyList(), 0)
        val phrase = terms.joinToString(" ")
        val data = load(translation)
        return withContext(Dispatchers.Default) {
            // Verses with the exact phrase come first, then the rest, each in Bible order.
            val exact = mutableListOf<SearchHit>()
            val other = mutableListOf<SearchHit>()
            val books = when (scope) {
                SearchScope.All -> Books.all
                SearchScope.Old -> Books.oldTestament
                SearchScope.New -> Books.newTestament
            }
            for (book in books) {
                ensureActive()
                data.chapters[book.id]?.forEachIndexed { ci, lines ->
                    for (line in lines) {
                        if (!line.startsWith("V\t")) continue
                        val parts = line.split('\t', limit = 3)
                        val norm = SearchText.normalize(parts[2])
                        if (SearchText.matches(norm, terms)) {
                            val hit = SearchHit(book.id, ci + 1, parts[1].toInt(), parts[2].replace('|', ' '))
                            if (terms.size > 1 && SearchText.indexOfWord(norm.replace('|', ' '), phrase) >= 0) exact += hit else other += hit
                        }
                    }
                }
            }
            SearchResults(query, (exact + other).take(limit), exact.size + other.size)
        }
    }

    private suspend fun load(translation: Translation): Loaded = mutex.withLock {
        loaded?.takeIf { it.translation == translation }?.let { return it }
        withContext(Dispatchers.IO) {
            val books = LinkedHashMap<String, MutableList<MutableList<String>>>()
            var current: MutableList<MutableList<String>>? = null
            val stream = translation.asset?.let { context.assets.open("bibles/$it.bgz") } ?: translation.file!!.inputStream()
            stream.use { input ->
                GZIPInputStream(input, 64 * 1024).bufferedReader().forEachLine { line ->
                    when {
                        line.startsWith("#B\t") -> {
                            current = mutableListOf()
                            books[line.split('\t')[1]] = current!!
                        }
                        line.startsWith("#C\t") -> current!!.add(mutableListOf())
                        else -> current!!.last().add(line)
                    }
                }
            }
            Loaded(translation, books).also { loaded = it }
        }
    }

    private fun parse(book: Book, number: Int, raw: List<String>): Chapter {
        val blocks = mutableListOf<Block>()
        var para = mutableListOf<Verse>()
        fun flush() {
            if (para.isNotEmpty()) blocks += Block.Paragraph(para)
            para = mutableListOf()
        }
        for (line in raw) {
            val parts = line.split('\t')
            when (parts[0]) {
                "H" -> { flush(); blocks += Block.Heading(parts[1]) }
                "S" -> { flush(); blocks += Block.Subtitle(parts[1]) }
                "P" -> flush()
                "V" -> para += Verse(parts[1].toInt(), parts[2].split('|'))
            }
        }
        flush()
        return Chapter(book, number, blocks)
    }
}
