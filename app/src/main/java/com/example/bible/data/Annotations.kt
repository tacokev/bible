package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Highlight colours a verse can have (stored by [name]). */
enum class HighlightColor { Yellow, Green, Blue, Pink, Purple }

/** What the reader has added to one verse. Verses are keyed by reference, so marks show in every translation. */
data class VerseMark(val highlight: HighlightColor? = null, val note: String? = null) {
    val isEmpty get() = highlight == null && note.isNullOrBlank()
}

/** Highlights and notes, saved as a small JSON file in app storage. */
class AnnotationStore(context: Context) {
    private val file = File(context.filesDir, "annotations.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _marks = MutableStateFlow(read())
    /** Keyed by [key], e.g. "GEN:1:3". */
    val marks: StateFlow<Map<String, VerseMark>> = _marks.asStateFlow()

    fun forChapter(all: Map<String, VerseMark>, bookId: String, chapter: Int): Map<Int, VerseMark> {
        val prefix = "$bookId:$chapter:"
        return all.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix).toInt() }
    }

    fun setHighlight(bookId: String, chapter: Int, verse: Int, color: HighlightColor?) =
        update(key(bookId, chapter, verse)) { it.copy(highlight = color) }

    fun setNote(bookId: String, chapter: Int, verse: Int, note: String?) =
        update(key(bookId, chapter, verse)) { it.copy(note = note?.trim()?.takeIf { n -> n.isNotEmpty() }) }

    private fun update(key: String, change: (VerseMark) -> VerseMark) {
        val next = _marks.value.toMutableMap()
        val mark = change(next[key] ?: VerseMark())
        if (mark.isEmpty) next.remove(key) else next[key] = mark
        _marks.value = next
        save(next)
    }

    private fun read(): Map<String, VerseMark> = runCatching {
        if (!file.exists()) return emptyMap()
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { k ->
            val m = o.getJSONObject(k)
            VerseMark(
                highlight = m.optString("highlight").takeIf { it.isNotEmpty() }
                    ?.let { name -> HighlightColor.entries.firstOrNull { it.name == name } },
                note = m.optString("note").takeIf { it.isNotEmpty() },
            )
        }.filterValues { !it.isEmpty }
    }.getOrDefault(emptyMap())

    private fun save(marks: Map<String, VerseMark>) {
        scope.launch {
            writeLock.withLock {
                val o = JSONObject()
                marks.forEach { (k, m) ->
                    o.put(k, JSONObject().apply {
                        m.highlight?.let { put("highlight", it.name) }
                        m.note?.let { put("note", it) }
                    })
                }
                // Write to a temp file and swap, so a crash mid-write can't lose everything.
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(o.toString())
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    companion object {
        fun key(bookId: String, chapter: Int, verse: Int) = "$bookId:$chapter:$verse"
    }
}
