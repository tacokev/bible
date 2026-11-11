package com.example.bible.data

import android.content.Context
import com.example.bible.data.importer.ParsedBible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * A Bible text the app can show. Built-in ones live in assets; imported ones are files in
 * app storage. Both use the same gzipped line format (see BibleRepository).
 */
data class Translation(
    val id: String,
    val code: String,
    val title: String,
    val blurb: String,
    val asset: String? = null,
    val file: File? = null,
) {
    val builtIn get() = asset != null

    companion object {
        val BSB = Translation("BSB", "BSB", "Berean Standard Bible", "Modern, accurate and very readable", asset = "bsb")
        val builtIns = listOf(
            BSB,
            Translation("WEB", "WEB", "World English Bible", "Modern English, easy to read", asset = "web"),
            Translation("KJV", "KJV", "King James Version", "Classic 1611 English", asset = "kjv"),
            Translation("ASV", "ASV", "American Standard Version", "Formal and literal (1901)", asset = "asv"),
            Translation("YLT", "YLT", "Young's Literal Translation", "Very literal, word-for-word", asset = "ylt"),
        )
    }
}

/** Built-in translations plus the user's imported ones (stored in filesDir/translations). */
class TranslationLibrary(context: Context) {
    private val dir = File(context.filesDir, "translations").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val _all = MutableStateFlow(Translation.builtIns + readIndex())
    val all: StateFlow<List<Translation>> = _all.asStateFlow()

    fun find(id: String?): Translation? = _all.value.firstOrNull { it.id == id }

    private fun readIndex(): List<Translation> = try {
        if (!indexFile.exists()) emptyList() else {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Translation(it.getString("id"), it.getString("code"), it.getString("title"), it.optString("blurb"), file = File(dir, it.getString("file")))
            }.filter { it.file!!.exists() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeIndex(imported: List<Translation>) {
        val arr = JSONArray()
        imported.forEach {
            arr.put(JSONObject().put("id", it.id).put("code", it.code).put("title", it.title).put("blurb", it.blurb).put("file", it.file!!.name))
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    /** Saves a parsed Bible as a new translation. Call off the main thread. */
    fun add(bible: ParsedBible, title: String, code: String): Translation {
        val id = "user-${System.currentTimeMillis()}"
        val file = File(dir, "$id.bgz")
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { bible.writeTo(it) }
        val blurb = buildString {
            append("Imported · ")
            append(if (bible.bookCount == 66) "Full Bible" else "${bible.bookCount} books")
        }
        val t = Translation(id, code, title, blurb, file = file)
        val imported = _all.value.filter { !it.builtIn } + t
        writeIndex(imported)
        _all.value = Translation.builtIns + imported
        return t
    }

    fun delete(t: Translation) {
        if (t.builtIn) return
        val imported = _all.value.filter { !it.builtIn && it.id != t.id }
        writeIndex(imported)
        t.file?.delete()
        _all.value = Translation.builtIns + imported
    }
}
