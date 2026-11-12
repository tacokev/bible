package com.example.bible.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One thing to say (or a pause), with an id reported back as it plays. */
sealed interface Utterance {
    val id: String
    data class Speech(override val id: String, val text: String) : Utterance
    data class Pause(override val id: String, val millis: Int) : Utterance
}

/**
 * A text-to-speech backend. Callbacks arrive on the main thread, like
 * [UtteranceProgressListener] but simpler.
 */
interface Speaker {
    var onStart: (String) -> Unit
    var onDone: (String) -> Unit
    /** Called if the speaker can't work at all (e.g. no engine, broken model). */
    var onFailed: () -> Unit
    /** Speaking rate, 1 = normal. Applies to the next [speak]. */
    var speed: Float
    /** Replaces anything queued with [items] and starts speaking. */
    fun speak(items: List<Utterance>)
    fun stop()
    fun release()
}

internal val speechAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** The phone's own TTS engine (Google's on Pixel), preferring its best offline voice. */
class SystemSpeaker(context: Context) : Speaker {
    override var onStart: (String) -> Unit = {}
    override var onDone: (String) -> Unit = {}
    override var onFailed: () -> Unit = {}
    override var speed = 1f
        set(value) { field = value; if (ready) tts.setSpeechRate(0.95f * value) }

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: List<Utterance>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                configure()
                pending?.let { pending = null; speak(it) }
            } else {
                onFailed()
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { main.post { onStart.invoke(id) } }
            override fun onDone(id: String) { main.post { onDone.invoke(id) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { main.post { onDone.invoke(id) } }
        })
    }

    private fun configure() {
        tts.setAudioAttributes(speechAttributes)
        val locale = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
        tts.language = locale
        val voice = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter {
                it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            .sortedWith(compareByDescending<Voice> { it.locale.country == locale.country }.thenByDescending { it.quality })
            .firstOrNull()
        voice?.let { tts.voice = it }
        tts.setSpeechRate(0.95f * speed)
    }

    override fun speak(items: List<Utterance>) {
        if (!ready) { pending = items; return }
        tts.stop()
        items.forEach {
            when (it) {
                is Utterance.Speech -> tts.speak(it.text, TextToSpeech.QUEUE_ADD, null, it.id)
                is Utterance.Pause -> tts.playSilentUtterance(it.millis.toLong(), TextToSpeech.QUEUE_ADD, it.id)
            }
        }
    }

    override fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }
}

/**
 * A neural voice model (Kitten, Kokoro, Piper/VITS) run on-device with sherpa-onnx. Audio is
 * generated on one thread a few sentences ahead of playback on another, so there are no gaps
 * between verses.
 */
class SherpaSpeaker(private val config: OfflineTtsConfig, private val sid: Int) : Speaker {
    override var onStart: (String) -> Unit = {}
    override var onDone: (String) -> Unit = {}
    override var onFailed: () -> Unit = {}
    @Volatile override var speed = 1f

    private val main = Handler(Looper.getMainLooper())
    private val generator = Executors.newSingleThreadExecutor { Thread(it, "tts-gen").apply { priority = Thread.MAX_PRIORITY } }
    private val player = Executors.newSingleThreadExecutor { Thread(it, "tts-play") }
    /** Bumped by every speak/stop; anything tagged with an older value is dropped. */
    private val generation = AtomicInteger()
    @Volatile private var released = false

    private sealed interface Chunk { val gen: Int }
    private data class Mark(override val gen: Int, val id: String, val start: Boolean) : Chunk
    private class Pcm(override val gen: Int, val samples: FloatArray) : Chunk

    /** Generated audio waiting to play; bounded so generation only runs a little ahead. */
    private val queue = ArrayBlockingQueue<Chunk>(32)

    private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    init {
        generator.execute {
            try {
                val t = OfflineTts(config = config)
                tts = t
                val rate = t.sampleRate()
                val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(speechAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .build(),
                    )
                    // ~250 ms, in whole 4-byte float frames (AudioTrack rejects partial frames,
                    // e.g. 22050 bytes for Piper's 22.05 kHz voices).
                    .setBufferSizeInBytes(maxOf(min, rate / 4 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                player.execute(::playLoop)
            } catch (t: Throwable) {
                Log.e("SherpaSpeaker", "Couldn't load the voice model", t)
                main.post { onFailed() }
            }
        }
    }

    override fun speak(items: List<Utterance>) {
        val gen = stopInternal()
        val speed = speed
        generator.execute {
            val t = tts ?: return@execute
            val rate = t.sampleRate()
            var produced = false
            for (item in items) {
                if (gen != generation.get()) return@execute
                put(Mark(gen, item.id, start = true))
                when (item) {
                    is Utterance.Speech -> {
                        try {
                            t.generateWithCallback(item.text, sid, speed, SherpaCallback { samples ->
                                if (gen != generation.get()) false
                                else { if (samples.isNotEmpty()) produced = true; put(Pcm(gen, samples.copyOf())); true }
                            })
                        } catch (e: Throwable) {
                            Log.e("SherpaSpeaker", "Generation failed", e)
                        }
                        // Nothing spoken yet in this request: the engine isn't working (e.g. its
                        // text frontend failed). Report it before any "done" marker plays, rather
                        // than silently racing through the chapter (and the next, and the next…).
                        if (!produced && gen == generation.get()) {
                            stopInternal()
                            main.post { onFailed() }
                            return@execute
                        }
                    }
                    is Utterance.Pause -> put(Pcm(gen, FloatArray(rate * item.millis / 1000)))
                }
                put(Mark(gen, item.id, start = false))
            }
        }
    }

    /** Blocks while the queue is full, giving up if this generation is cancelled. */
    private fun put(chunk: Chunk) {
        while (!released && chunk.gen == generation.get()) {
            if (queue.offer(chunk, 100, TimeUnit.MILLISECONDS)) return
        }
    }

    private fun playLoop() {
        val track = track ?: return
        track.play()
        var lastGen = -1
        while (!released) {
            val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (chunk.gen != generation.get()) continue
            if (chunk.gen != lastGen) {
                // New request: drop any tail of the previous one still in the track's buffer.
                runCatching { track.pause(); track.flush() }
                lastGen = chunk.gen
            }
            when (chunk) {
                is Mark -> main.post {
                    if (chunk.gen == generation.get()) (if (chunk.start) onStart else onDone).invoke(chunk.id)
                }
                is Pcm -> {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    var off = 0
                    // Small writes so stop() takes effect quickly.
                    while (off < chunk.samples.size && chunk.gen == generation.get() && !released) {
                        val n = minOf(2048, chunk.samples.size - off)
                        val w = track.write(chunk.samples, off, n, AudioTrack.WRITE_BLOCKING)
                        if (w <= 0) break
                        off += w
                    }
                }
            }
        }
    }

    private fun stopInternal(): Int {
        val gen = generation.incrementAndGet()
        queue.clear()
        track?.let { runCatching { it.pause(); it.flush() } }
        return gen
    }

    override fun stop() { stopInternal() }

    override fun release() {
        stopInternal()
        released = true
        generator.execute {
            tts?.release()
            tts = null
        }
        generator.shutdown()
        player.shutdown()
        // The play loop exits within ~200 ms; free the track after it has.
        Thread {
            player.awaitTermination(2, TimeUnit.SECONDS)
            track?.release()
            track = null
        }.start()
    }
}
