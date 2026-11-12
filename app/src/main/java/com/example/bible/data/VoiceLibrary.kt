package com.example.bible.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** An installed on-device voice model (a sherpa-onnx TTS package). Paths are relative to [dir]. */
data class VoiceModel(
    val id: String,
    val dir: File,
    val name: String,
    val detail: String,
    val type: Type,
    val model: String,
    val tokens: String,
    val voices: String?,
    val lexicons: List<String>,
    val dictDir: String?,
    val ruleFsts: List<String>,
    val ruleFars: List<String>,
    val usesEspeak: Boolean,
    val speakers: List<String>,
) {
    enum class Type(val label: String) { Kitten("Kitten"), Kokoro("Kokoro"), Vits("Piper / VITS") }

    fun f(rel: String) = File(dir, rel).path

    fun config(espeakDir: File, threads: Int = 4): OfflineTtsConfig {
        val data = if (usesEspeak) espeakDir.path else ""
        val lexicon = lexicons.joinToString(",") { f(it) }
        val dict = dictDir?.let(::f) ?: ""
        val model = when (type) {
            Type.Kitten -> OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(model = f(model), voices = f(voices!!), tokens = f(tokens), dataDir = data),
                numThreads = threads,
            )
            Type.Kokoro -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = f(model), voices = f(voices!!), tokens = f(tokens), dataDir = data,
                    lexicon = lexicon, dictDir = dict,
                ),
                numThreads = threads,
            )
            Type.Vits -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = f(model), lexicon = lexicon, tokens = f(tokens), dataDir = data, dictDir = dict),
                numThreads = threads,
            )
        }
        return OfflineTtsConfig(
            model = model,
            ruleFsts = ruleFsts.joinToString(",") { f(it) },
            ruleFars = ruleFars.joinToString(",") { f(it) },
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("detail", detail).put("type", type.name)
        .put("model", model).put("tokens", tokens).put("voices", voices)
        .put("lexicons", JSONArray(lexicons)).put("dictDir", dictDir)
        .put("ruleFsts", JSONArray(ruleFsts)).put("ruleFars", JSONArray(ruleFars))
        .put("espeak", usesEspeak).put("speakers", JSONArray(speakers))

    companion object {
        fun fromJson(dir: File, o: JSONObject) = VoiceModel(
            id = dir.name,
            dir = dir,
            name = o.getString("name"),
            detail = o.optString("detail"),
            type = Type.valueOf(o.getString("type")),
            model = o.getString("model"),
            tokens = o.getString("tokens"),
            voices = o.optString("voices").takeIf { it.isNotEmpty() && it != "null" },
            lexicons = o.optJSONArray("lexicons").strings(),
            dictDir = o.optString("dictDir").takeIf { it.isNotEmpty() && it != "null" },
            ruleFsts = o.optJSONArray("ruleFsts").strings(),
            ruleFars = o.optJSONArray("ruleFars").strings(),
            usesEspeak = o.optBoolean("espeak", true),
            speakers = o.optJSONArray("speakers").strings(),
        )

        private fun JSONArray?.strings() = if (this == null) emptyList() else List(length()) { getString(it) }
    }
}

/**
 * Installed voice models: downloads/imports sherpa-onnx voice packages (Piper/VITS, Kokoro,
 * Kitten; .tar.bz2, .tar.gz or .zip), works out how to load each one, checks it
 * actually speaks, and keeps one shared copy of the espeak-ng phoneme data they use.
 */
class VoiceLibrary(context: Context) {
    sealed interface Task {
        /** [fraction] is 0..1 while transferring, null while unpacking/checking. */
        data class Working(val label: String, val fraction: Float?) : Task
        data class Failed(val message: String) : Task
    }

    private val app = context.applicationContext
    private val root = File(app.filesDir, "voices").apply { mkdirs() }
    /** espeak-ng data shared by every voice that needs it (union of what packages shipped). */
    val espeakDir = File(root, "espeak-ng-data")
    private val mutex = Mutex()

    private val _voices = MutableStateFlow<List<VoiceModel>>(emptyList())
    val voices: StateFlow<List<VoiceModel>> = _voices.asStateFlow()
    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task.asStateFlow()

    init {
        migrateOldKitten()
        refresh()
    }

    fun find(id: String?) = _voices.value.firstOrNull { it.id == id }

    private fun refresh() {
        _voices.value = root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, MANIFEST).exists() }
            .mapNotNull { dir -> runCatching { VoiceModel.fromJson(dir, JSONObject(File(dir, MANIFEST).readText())) }.getOrNull() }
            .sortedBy { it.name.lowercase() }
    }

    fun dismissError() { if (_task.value is Task.Failed) _task.value = null }

    /** Downloads a voice package from a link. */
    suspend fun installFromUrl(link: String) = run("voice") {
        val url = runCatching { URL(link.trim()) }.getOrNull()?.takeIf { it.protocol == "https" || it.protocol == "http" }
            ?: throw UserError("That doesn't look like a link. It should start with https://")
        val archive = File(root, "download.part")
        try {
            download(url, archive, null, 0)
            val name = url.path.substringAfterLast('/').ifEmpty { "voice" }
            archive.inputStream().use { install(it, name, englishOnly = false) }
        } finally {
            archive.delete()
        }
    }

    /** Imports a voice package file the user picked. */
    suspend fun installFromUri(uri: Uri) = run("voice") {
        var name = "voice"
        var size = 0L
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                size = if (c.isNull(1)) 0 else c.getLong(1)
            }
        }
        val input = app.contentResolver.openInputStream(uri) ?: throw UserError("The file couldn't be opened.")
        input.use {
            val counted = Counting(it) { read ->
                if (size > 0) _task.value = Task.Working("Importing…", (read.toFloat() / size).coerceIn(0f, 1f))
            }
            install(counted, name, englishOnly = false)
        }
    }

    fun delete(id: String) {
        File(root, id).takeIf { it.parentFile == root && id != espeakDir.name }?.deleteRecursively()
        refresh()
        if (_voices.value.none { it.usesEspeak }) espeakDir.deleteRecursively()
    }

    // ---- Install pipeline ----

    private class UserError(message: String) : Exception(message)

    private suspend fun run(what: String, block: suspend () -> Unit) {
        if (_task.value is Task.Working) return
        _task.value = Task.Working("Starting…", 0f)
        try {
            mutex.withLock { withContext(Dispatchers.IO) { block() } }
            _task.value = null
        } catch (e: CancellationException) {
            _task.value = null
            throw e
        } catch (e: UserError) {
            _task.value = Task.Failed(e.message!!)
        } catch (e: IOException) {
            _task.value = Task.Failed("Couldn't get the $what: ${e.message ?: "connection problem"}. Check your connection and try again.")
        } catch (e: Throwable) {
            _task.value = Task.Failed("Couldn't add the $what: ${e.message ?: e::class.simpleName}")
        } finally {
            File(root, "download.part").delete()
            root.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
        }
    }

    private suspend fun download(start: URL, to: File, sha256: String?, expectedSize: Long) {
        var url = start
        var conn: HttpURLConnection
        var hops = 0
        // Follow redirects by hand (HttpURLConnection won't across hosts, e.g. GitHub → its CDN).
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "BibleApp")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 8) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                continue
            }
            if (code != 200) throw IOException("the server said HTTP $code")
            break
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            conn.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        currentCoroutineContext().ensureActive()
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        _task.value = Task.Working("Downloading…", if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (sha256 != null && digest.digest().joinToString("") { "%02x".format(it) } != sha256) {
            throw UserError("The download was corrupted. Please try again.")
        }
    }

    /** Unpacks a package into a temp folder, works out how to load it, checks it speaks, installs it. */
    private suspend fun install(raw: InputStream, fileName: String, englishOnly: Boolean) {
        val baseName = fileName.substringBefore(".tar").substringBefore(".zip").substringBefore(".tgz")
        val tmp = File(root, "import.tmp").apply { deleteRecursively(); mkdirs() }
        val newEspeak = File(root, "espeak.tmp").apply { deleteRecursively() }
        unpack(raw, tmp, newEspeak, englishOnly)

        _task.value = Task.Working("Checking the voice…", null)
        // Packages usually wrap everything in one folder; look inside it.
        val top = tmp.listFiles().orEmpty()
        val pkg = top.singleOrNull()?.takeIf { it.isDirectory } ?: tmp
        val folderName = if (pkg != tmp) pkg.name else baseName
        val hadEspeak = newEspeak.exists()

        val files = pkg.listFiles().orEmpty()
        val onnx = files.filter { it.isFile && it.name.endsWith(".onnx") }
        if (onnx.isEmpty()) throw UserError("No voice model (.onnx file) found. Use a sherpa-onnx TTS voice package.")
        if (onnx.size > 1) {
            throw UserError("This package has more than one model file. Only Piper/VITS, Kokoro and Kitten voices are supported.")
        }
        val model = onnx.single()
        if (!File(pkg, "tokens.txt").exists()) throw UserError("This package is missing tokens.txt, so it isn't a sherpa-onnx voice package.")

        val meta = OnnxMetadata.read(model)
        val modelType = meta["model_type"].orEmpty().lowercase()
        val hasVoicesBin = File(pkg, "voices.bin").exists()
        val type = when {
            "kitten" in modelType -> VoiceModel.Type.Kitten
            "kokoro" in modelType -> VoiceModel.Type.Kokoro
            "vits" in modelType || "piper" in modelType -> VoiceModel.Type.Vits
            hasVoicesBin && "kitten" in folderName.lowercase() -> VoiceModel.Type.Kitten
            hasVoicesBin -> VoiceModel.Type.Kokoro
            "matcha" in modelType -> throw UserError("Matcha voices need a separate vocoder and aren't supported.")
            modelType.isNotEmpty() -> throw UserError("Voices of type \"$modelType\" aren't supported. Try a Piper, Kokoro or Kitten voice.")
            else -> VoiceModel.Type.Vits
        }
        if (type != VoiceModel.Type.Vits && !hasVoicesBin) throw UserError("This package is missing voices.bin.")

        val lexicons = files.filter { it.isFile && it.name.startsWith("lexicon") && it.name.endsWith(".txt") }.map { it.name }.sorted()
        val usesEspeak = hadEspeak || meta["has_espeak"] == "1" || (lexicons.isEmpty() && espeakDir.exists())
        if (usesEspeak && !hadEspeak && !espeakDir.exists()) {
            throw UserError("This voice needs espeak-ng-data, which isn't in the package. Download the full package that includes it.")
        }

        val (name, detail) = describe(folderName, type, meta)
        // Temporarily merge any new espeak files so the check below can use them.
        mergeEspeak(newEspeak)

        val draft = VoiceModel(
            id = sanitize(folderName),
            dir = pkg,
            name = name,
            detail = detail,
            type = type,
            model = model.name,
            tokens = "tokens.txt",
            voices = if (hasVoicesBin) "voices.bin" else null,
            lexicons = lexicons,
            dictDir = File(pkg, "dict").takeIf { it.isDirectory }?.name,
            ruleFsts = files.filter { it.isFile && it.name.endsWith(".fst") }.map { it.name }.sorted(),
            ruleFars = files.filter { it.isFile && it.name.endsWith(".far") }.map { it.name }.sorted(),
            usesEspeak = usesEspeak,
            speakers = emptyList(),
        )

        // Load it and say a word, so a broken or unsupported package fails here, not mid-chapter.
        val count = try {
            val tts = OfflineTts(config = draft.config(espeakDir, threads = 2))
            try {
                val audio = tts.generate("Hello.", 0, 1f)
                if (audio.samples.isEmpty()) throw UserError("The voice loaded but produced no sound. It may need files this package doesn't include.")
                tts.numSpeakers()
            } finally {
                tts.release()
            }
        } catch (e: UserError) {
            throw e
        } catch (e: Throwable) {
            throw UserError("The voice couldn't be loaded (${e.message ?: e::class.simpleName}).")
        }

        val installed = File(root, draft.id)
        val final = draft.copy(dir = installed, speakers = speakerNames(type, meta, count))
        File(pkg, MANIFEST).writeText(final.toJson().toString(2))
        installed.deleteRecursively()
        if (!pkg.renameTo(installed)) throw IOException("couldn't move the voice into place")
        tmp.deleteRecursively()
        refresh()
    }

    private suspend fun unpack(raw: InputStream, into: File, espeakOut: File, englishOnly: Boolean) {
        val input = BufferedInputStream(raw, 256 * 1024)
        input.mark(8)
        val magic = ByteArray(6).also { input.read(it) }
        input.reset()
        val archive: ArchiveInputStream<*> = when {
            magic[0] == 'B'.code.toByte() && magic[1] == 'Z'.code.toByte() && magic[2] == 'h'.code.toByte() ->
                TarArchiveInputStream(BZip2CompressorInputStream(input))
            magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> TarArchiveInputStream(GzipCompressorInputStream(input))
            magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() -> ZipArchiveInputStream(input)
            else -> throw UserError("That isn't a supported file. Pick a voice package ending in .tar.bz2, .tar.gz or .zip.")
        }
        if (_task.value.let { it is Task.Working && it.fraction == 0f }) _task.value = Task.Working("Unpacking…", null)
        val base = into.canonicalPath + File.separator
        val espeakBase = espeakOut.canonicalPath + File.separator
        archive.use { a ->
            while (true) {
                val entry = a.nextEntry ?: break
                currentCoroutineContext().ensureActive()
                val name = entry.name.trimStart('/')
                // espeak-ng-data goes to its own folder, to be merged into the shared copy.
                val idx = name.indexOf("espeak-ng-data/")
                val out = if (idx >= 0) {
                    val rel = name.substring(idx + "espeak-ng-data/".length)
                    if (rel.isEmpty()) continue
                    val file = rel.substringAfterLast('/')
                    if (englishOnly && file.endsWith("_dict") && file != "en_dict") continue
                    File(espeakOut, rel).also { if (!it.canonicalPath.startsWith(espeakBase)) throw UserError("The package has a bad file path.") }
                } else {
                    File(into, name).also { if (!it.canonicalPath.startsWith(base)) throw UserError("The package has a bad file path.") }
                }
                if (entry.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { a.copyTo(it) }
            }
        }
    }

    /** Adds files from a package's espeak-ng-data that the shared copy doesn't have yet. */
    private fun mergeEspeak(from: File) {
        if (!from.exists()) return
        if (!espeakDir.exists()) {
            if (from.renameTo(espeakDir)) return
        }
        from.walkTopDown().filter { it.isFile }.forEach { f ->
            val dest = File(espeakDir, f.relativeTo(from).path)
            if (!dest.exists()) { dest.parentFile?.mkdirs(); f.renameTo(dest) || run { f.copyTo(dest); true } }
        }
        from.deleteRecursively()
    }

    private fun describe(folder: String, type: VoiceModel.Type, meta: Map<String, String>): Pair<String, String> {
        val lower = folder.lowercase()
        val quant = when {
            "int8" in lower -> " · int8"
            "fp16" in lower -> " · fp16"
            else -> ""
        }
        return when (type) {
            VoiceModel.Type.Kitten -> "Kitten" to "Kitten TTS · ${meta["language"] ?: "English"}$quant"
            VoiceModel.Type.Kokoro -> "Kokoro" to "Kokoro · ${if ("multi" in lower) "multilingual" else meta["language"] ?: "English"}$quant"
            VoiceModel.Type.Vits -> {
                // e.g. vits-piper-en_US-norman-medium-int8 → "Norman", "Piper · en_US · medium"
                val piper = Regex("""piper-([a-z]{2,3}_[A-Z]{2})-(.+?)-(x_low|low|medium|high)""").find(folder)
                if (piper != null) {
                    val (locale, voice, quality) = piper.destructured
                    pretty(voice) to "Piper · $locale · $quality$quant"
                } else {
                    pretty(folder.removePrefix("vits-").removePrefix("piper-")) to "VITS · ${meta["language"] ?: ""}$quant".trimEnd(' ', '·')
                }
            }
        }
    }

    private fun speakerNames(type: VoiceModel.Type, meta: Map<String, String>, count: Int): List<String> {
        val raw = meta["speaker_names"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val names = raw?.takeIf { it.size == count }?.map { n ->
            when (type) {
                VoiceModel.Type.Kitten -> KITTEN_NAMES[n] ?: pretty(n)
                VoiceModel.Type.Kokoro -> kokoroName(n)
                VoiceModel.Type.Vits -> pretty(n)
            }
        }
        return names ?: if (count <= 1) emptyList() else List(count) { "Speaker ${it + 1}" }
    }

    /** "bm_george" → "George · British male". */
    private fun kokoroName(n: String): String {
        val parts = n.split('_', limit = 2)
        if (parts.size < 2 || parts[0].length != 2) return pretty(n)
        val accent = when (parts[0][0]) {
            'a' -> "American"; 'b' -> "British"; 'e' -> "Spanish"; 'f' -> "French"; 'h' -> "Hindi"
            'i' -> "Italian"; 'j' -> "Japanese"; 'p' -> "Portuguese"; 'z' -> "Chinese"; else -> null
        }
        val gender = when (parts[0][1]) { 'm' -> "male"; 'f' -> "female"; else -> null }
        val label = listOfNotNull(accent, gender).joinToString(" ")
        return if (label.isEmpty()) pretty(parts[1]) else "${pretty(parts[1])} · $label"
    }

    private fun pretty(s: String) = s.replace('_', ' ').replace('-', ' ').trim()
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.').ifEmpty { "voice" }.take(80)

    /** Earlier builds installed Kitten without a manifest and with espeak inside its folder. */
    private fun migrateOldKitten() {
        val old = File(root, KITTEN_NAME)
        if (!old.isDirectory || File(old, MANIFEST).exists() || !File(old, "model.int8.onnx").exists()) return
        mergeEspeak(File(old, "espeak-ng-data"))
        File(old, ".installed").delete()
        val v = VoiceModel(
            id = KITTEN_NAME, dir = old, name = "Kitten", detail = "Kitten TTS · English · int8",
            type = VoiceModel.Type.Kitten, model = "model.int8.onnx", tokens = "tokens.txt", voices = "voices.bin",
            lexicons = emptyList(), dictDir = null, ruleFsts = emptyList(), ruleFars = emptyList(),
            usesEspeak = true, speakers = KITTEN_ORDER.map { KITTEN_NAMES.getValue(it) },
        )
        File(old, MANIFEST).writeText(v.toJson().toString(2))
    }

    private class Counting(input: InputStream, val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var lastReport = 0L
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        private fun bump(n: Long) {
            count += n
            if (count - lastReport > 256 * 1024) { lastReport = count; onRead(count) }
        }
    }

    companion object {
        private const val MANIFEST = "voice.json"
        const val KITTEN_NAME = "kitten-nano-en-v0_8-int8"
        const val CATALOG_URL = "https://k2-fsa.github.io/sherpa/onnx/tts/all/English/index.html"

        /** Kitten speaker ids in model order, with KittenML's names for them. */
        private val KITTEN_ORDER = listOf(
            "expr-voice-2-m", "expr-voice-2-f", "expr-voice-3-m", "expr-voice-3-f",
            "expr-voice-4-m", "expr-voice-4-f", "expr-voice-5-m", "expr-voice-5-f",
        )
        private val KITTEN_NAMES = mapOf(
            "expr-voice-2-f" to "Bella", "expr-voice-2-m" to "Jasper", "expr-voice-3-f" to "Luna", "expr-voice-3-m" to "Bruno",
            "expr-voice-4-f" to "Rosie", "expr-voice-4-m" to "Hugo", "expr-voice-5-f" to "Kiki", "expr-voice-5-m" to "Leo",
        )
    }
}

/** Reads the key/value metadata sherpa-onnx stores at the end of a model file. */
internal object OnnxMetadata {
    fun read(file: File): Map<String, String> = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val n = minOf(f.length(), 512L * 1024).toInt()
            val buf = ByteArray(n)
            f.seek(f.length() - n)
            f.readFully(buf)
            parse(buf)
        }
    }.getOrDefault(emptyMap())

    /** Scans for ModelProto.metadata_props entries: tag 0x72, len, {0x0a len key, 0x12 len value}. */
    private fun parse(b: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < b.size - 4) {
            if (b[i] == 0x72.toByte()) {
                val (entryLen, p) = varint(b, i + 1)
                if (entryLen in 4..200_000 && p + entryLen <= b.size && b[p] == 0x0a.toByte()) {
                    val (kLen, kp) = varint(b, p + 1)
                    val vTag = kp + kLen
                    if (kLen in 1..200 && vTag < p + entryLen && b[vTag] == 0x12.toByte()) {
                        val (vLen, vp) = varint(b, vTag + 1)
                        if (vp + vLen == p + entryLen) {
                            val key = String(b, kp, kLen, Charsets.UTF_8)
                            if (key.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) {
                                out[key] = String(b, vp, vLen, Charsets.UTF_8)
                                i = p + entryLen
                                continue
                            }
                        }
                    }
                }
            }
            i++
        }
        return out
    }

    private fun varint(b: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0L
        var shift = 0
        var p = start
        while (p < b.size && shift < 35) {
            val x = b[p++].toInt() and 0xff
            result = result or ((x and 0x7f).toLong() shl shift)
            if (x and 0x80 == 0) return (if (result > Int.MAX_VALUE) -1 else result.toInt()) to p
            shift += 7
        }
        return -1 to p
    }
}
