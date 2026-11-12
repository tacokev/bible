package com.example.bible.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.bible.R
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reads chapters aloud on-device, one verse per utterance so the reader can highlight and
 * follow along. Speaks with the phone's own TTS engine ([SystemSpeaker]) or
 * a downloaded/imported neural voice ([SherpaSpeaker], from [library]); see [voice].
 *
 * Plays through a [playlist] of chapters by itself (so it carries on with the screen locked);
 * screens watch [chapterKey] to follow along. While [active], [NarrationService] keeps the
 * app alive in the background and shows lock-screen controls.
 *
 * One per process: get it with [Narrator.get].
 */
@SuppressLint("StaticFieldLeak") // Holds the application context only.
class Narrator private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private var session = 0
    private var loader: (suspend (String, Int) -> Chapter)? = null
    private var loadJob: Job? = null
    private var chapter: Chapter? = null
    private var resumeOnFocusGain = false

    /** Which list is playing, e.g. "reader" or "lesson:12". */
    var playlistId by mutableStateOf<String?>(null)
        private set
    var playlist by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    /** Narration is on (playing or paused). */
    val active get() = playlistId != null
    /** Audio is playing right now (false while paused). */
    var playing by mutableStateOf(false)
        private set
    /** Chapter currently being read, as "BOOK:number". */
    val chapterKey get() = playlist.getOrNull(index)?.let { key(it.first, it.second) }
    /** Verse being spoken (or where it's paused). */
    var verse by mutableStateOf<Int?>(null)
        private set
    /** Lock-screen title, e.g. "Genesis 1". */
    val title get() = playlist.getOrNull(index)?.let { "${Books[it.first].name} ${it.second}" }.orEmpty()
    var subtitle by mutableStateOf("")
        private set
    /** Set when the device has no usable TTS engine. */
    var unavailable by mutableStateOf(false)
        private set

    /** Notified (main thread) on every state change; used by [NarrationService]. */
    var onChange: (() -> Unit)? = null

    /** Downloaded/imported neural voices. */
    val library = VoiceLibrary(context)

    /** A speaker within an installed voice model. */
    data class VoiceChoice(val modelId: String, val sid: Int)

    /** Selected neural voice, or null to use the phone's own. */
    var voice by mutableStateOf(readVoice())
        private set

    private fun readVoice(): VoiceChoice? {
        prefs.getString("voiceModel", null)?.let { return VoiceChoice(it, prefs.getInt("voiceSid", 0)) }
        // Earlier builds only had Kitten and stored just its speaker id.
        return prefs.getInt("kittenSid", -1).takeIf { it >= 0 }?.let { VoiceChoice(VoiceLibrary.KITTEN_NAME, it) }
    }

    /** Set when a chosen voice failed and reading fell back to the phone's voice. */
    var voiceError by mutableStateOf<String?>(null)
        private set

    /** Reading speed, 1 = normal; one of [speeds]. */
    var speed by mutableStateOf(prefs.getFloat("speed", 1f))
        private set

    private var speaker: Speaker = createSpeaker()

    private fun createSpeaker(): Speaker {
        val choice = voice
        val model = choice?.let { library.find(it.modelId) }
        val s = if (choice != null && model != null) SherpaSpeaker(model.config(library.espeakDir), choice.sid) else SystemSpeaker(context)
        s.speed = speed
        s.onStart = ::onUtteranceStart
        s.onDone = ::onUtteranceDone
        s.onFailed = {
            if (s is SherpaSpeaker) {
                // Model wouldn't load or speak: fall back to the phone's voice and carry on.
                val name = choice?.let { library.find(it.modelId) }?.name ?: "That voice"
                selectVoice(null)
                voiceError = "$name couldn't play on this phone, so the phone voice is being used."
            } else {
                unavailable = true
                stop()
            }
        }
        return s
    }

    /** Picks a speaker of an installed voice model, or null for the phone's voice. */
    fun selectVoice(choice: VoiceChoice?) {
        val valid = choice?.takeIf { library.find(it.modelId) != null }
        if (valid != null) voiceError = null
        prefs.edit()
            .putString("voiceModel", valid?.modelId)
            .putInt("voiceSid", valid?.sid ?: 0)
            .remove("kittenSid")
            .apply()
        if (valid == voice && (speaker is SherpaSpeaker) == (valid != null)) return
        voice = valid
        halt()
        speaker.release()
        speaker = createSpeaker()
        // Carry on from the same verse in the new voice.
        if (playing) chapter?.let { speakChapter(it, verse) }
    }

    fun changeSpeed(value: Float) {
        if (value == speed) return
        speed = value
        prefs.edit().putFloat("speed", value).apply()
        speaker.speed = value
        // Already-generated audio is at the old speed: pick up again from the current verse.
        if (playing) chapter?.let { speakChapter(it, verse) }
    }

    /** Runs a library install in the background (survives leaving the screen), then uses the new voice. */
    private fun installThen(block: suspend VoiceLibrary.() -> Unit) {
        scope.launch {
            val before = library.voices.value.map { it.id }.toSet()
            library.block()
            val added = library.voices.value.firstOrNull { it.id !in before } ?: return@launch
            // Kitten's first speaker is male; start on Bella (sid 1), its best-known voice.
            selectVoice(VoiceChoice(added.id, if (added.type == VoiceModel.Type.Kitten && added.speakers.size > 1) 1 else 0))
        }
    }

    fun importVoice(uri: android.net.Uri) = installThen { installFromUri(uri) }
    fun downloadVoice(link: String) = installThen { installFromUrl(link) }

    /** Says a sample line in the current voice (when not already reading). */
    fun preview() {
        if (active) return
        speaker.speak(listOf(Utterance.Speech("-1:preview", "In the beginning God created the heavens and the earth.")))
    }

    /** Setting: play the intro music when a chapter starts. Off by default. */
    var introEnabled by mutableStateOf(prefs.getBoolean("intro", false))
        private set

    fun changeIntroEnabled(on: Boolean) {
        introEnabled = on
        prefs.edit().putBoolean("intro", on).apply()
        if (!on) stopIntro()
    }

    /** Removes an installed voice model (switching back to the phone's voice if it was in use). */
    fun deleteVoice(id: String) {
        if (voice?.modelId == id) selectVoice(null)
        library.delete(id)
    }

    /**
     * Starts reading [list] at [startIndex] (from [fromVerse] if given), carrying on through
     * the rest of the list. [load] fetches chapter text.
     */
    fun play(
        id: String,
        list: List<Pair<String, Int>>,
        startIndex: Int,
        subtitle: String,
        fromVerse: Int? = null,
        load: suspend (String, Int) -> Chapter,
    ) {
        playlistId = id
        playlist = list
        loader = load
        this.subtitle = subtitle
        ContextCompat.startForegroundService(context, Intent(context, NarrationService::class.java))
        startAt(startIndex, fromVerse)
    }

    /** Jumps to another chapter of the current list; keeps playing if it was. */
    fun jumpTo(newIndex: Int) {
        if (!active || newIndex !in playlist.indices) return
        if (playing) startAt(newIndex) else { halt(); index = newIndex; chapter = null; verse = null; changed() }
    }

    fun next() = jumpTo(index + 1)
    fun previous() = jumpTo(index - 1)

    private fun startAt(i: Int, fromVerse: Int? = null) {
        halt()
        index = i
        verse = fromVerse
        chapter = null
        playing = true
        changed()
        if (!requestFocus()) { playing = false; changed(); return }
        val (bookId, number) = playlist[i]
        val s = session
        loadJob = scope.launch {
            val c = loader!!(bookId, number)
            if (s != session) return@launch
            chapter = c
            // A fresh start of a chapter (not "read from this verse") opens with the intro music.
            speakChapter(c, fromVerse, intro = fromVerse == null)
        }
    }

    fun pause() {
        if (!playing) return
        halt()
        playing = false
        changed()
    }

    fun resume() {
        if (!active || playing) return
        val c = chapter ?: return startAt(index)
        if (!requestFocus()) return
        playing = true
        changed()
        speakChapter(c, verse)
    }

    fun togglePause() = if (playing) pause() else resume()

    fun stop() {
        halt()
        playing = false
        playlistId = null
        playlist = emptyList()
        chapter = null
        verse = null
        loader = null
        abandonFocus()
        changed()
    }

    /** Silences speech (and the intro music) and invalidates queued callbacks, keeping position. */
    private fun halt() {
        session++
        loadJob?.cancel()
        speaker.stop()
        stopIntro()
    }

    // ---- Chapter intro music: plays under the opening of each chapter, stops with the reading ----

    private var intro: MediaPlayer? = null

    private fun playIntro() {
        stopIntro()
        intro = runCatching {
            MediaPlayer.create(context, R.raw.chapter_intro, speechAttributes, audio.generateAudioSessionId())
        }.getOrNull()?.apply {
            setOnCompletionListener { stopIntro() }
            start()
        }
    }

    private fun stopIntro() {
        intro?.let { runCatching { it.stop() }; it.release() }
        intro = null
    }

    private fun speakChapter(c: Chapter, fromVerse: Int?, intro: Boolean = false) {
        session++
        if (intro && introEnabled) playIntro()
        val verses = c.blocks.filterIsInstance<Block.Paragraph>().flatMap { it.verses }
            .filter { fromVerse == null || it.number >= fromVerse }
        val items = buildList {
            if (fromVerse == null || verses.isEmpty()) {
                add(Utterance.Speech("$session:title", "${c.book.name}, chapter ${c.number}."))
                add(Utterance.Pause("$session:gap", 400))
            }
            verses.forEach { v -> add(Utterance.Speech("$session:v${v.number}", v.lines.joinToString(" "))) }
            add(Utterance.Pause("$session:end", 600))
        }
        speaker.speak(items)
    }

    private fun onUtteranceStart(id: String) {
        val (s, part) = id.split(':', limit = 2)
        if (s.toInt() != session) return
        if (part.startsWith("v")) verse = part.drop(1).toInt()
    }

    private fun onUtteranceDone(id: String) {
        val (s, part) = id.split(':', limit = 2)
        if (s.toInt() != session || part != "end") return
        if (index < playlist.lastIndex) startAt(index + 1) else stop()
    }

    private fun changed() = onChange?.invoke()

    // ---- Audio focus: pause for calls, navigation prompts, other media ----

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocusGain = false; pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    resumeOnFocusGain = playing
                    pause()
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) { resumeOnFocusGain = false; resume() }
            }
        }
    }

    private val focusRequest by lazy {
        if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(speechAttributes)
                // Speech shouldn't just get quieter under a nav prompt; pause instead.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
        } else null
    }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(focusRequest!!)
        else @Suppress("DEPRECATION") audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        resumeOnFocusGain = false
        if (Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focusRequest!!)
        else @Suppress("DEPRECATION") audio.abandonAudioFocus(focusListener)
    }

    companion object {
        @Volatile private var instance: Narrator? = null
        fun get(context: Context): Narrator =
            instance ?: synchronized(this) { instance ?: Narrator(context.applicationContext).also { instance = it } }

        fun key(bookId: String, number: Int) = "$bookId:$number"

        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

        /** Every chapter of the Bible in order, for continuous reading. */
        val wholeBible: List<Pair<String, Int>> by lazy { Books.all.flatMap { b -> (1..b.chapters).map { b.id to it } } }
    }
}
