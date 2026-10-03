package com.example.musicsmd.playback.mix

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/** Decodes a time range of a stream to mono PCM for analysis. */
fun interface SnippetDecoder {
    suspend fun decode(url: String, startMs: Long, endMs: Long): PcmSnippet?
}

/**
 * Decodes with libVLC by transcoding the range to a temporary WAV file. Stream output isn't
 * paced by a clock, so this runs much faster than real time and plays nothing.
 */
class VlcSnippetDecoder(
    private val factory: MediaPlayerFactory,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : SnippetDecoder {

    override suspend fun decode(url: String, startMs: Long, endMs: Long): PcmSnippet? = withContext(Dispatchers.IO) {
        val from = startMs.coerceAtLeast(0L)
        if (endMs <= from) return@withContext null
        val file = File.createTempFile("musicsm-mix-", ".wav")
        val player = factory.mediaPlayers().newMediaPlayer()
        try {
            val done = CompletableDeferred<Boolean>()
            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) {
                    done.complete(true)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    done.complete(true)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    done.complete(false)
                }
            })
            val dst = file.absolutePath.replace('\\', '/')
            val started = player.media().play(
                url,
                ":sout=#transcode{vcodec=none,acodec=s16l,channels=1,samplerate=$SAMPLE_RATE}" +
                    ":std{access=file,mux=wav,dst=\"$dst\"}",
                ":no-sout-video",
                ":sout-audio",
                ":no-video",
                ":start-time=${from / 1000.0}",
                ":stop-time=${endMs / 1000.0}",
            )
            if (!started) return@withContext null
            val ok = withTimeoutOrNull(timeoutMs) { done.await() } ?: false
            // Stopping closes the muxer, which writes the WAV header's sizes.
            player.controls().stop()
            if (!ok) return@withContext null
            WavReader.readMono(file.readBytes())?.let { (samples, rate) ->
                samples.takeIf { it.isNotEmpty() }?.let { PcmSnippet(it, rate, from.toDouble()) }
            }
        } finally {
            player.release()
            file.delete()
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val DEFAULT_TIMEOUT_MS = 25_000L
    }
}

/** Minimal reader for libVLC's 16-bit PCM WAV output, mixed down to mono. */
object WavReader {

    /** Mono samples in [-1, 1] and the sample rate, or null if this isn't 16-bit PCM WAV. */
    fun readMono(bytes: ByteArray): Pair<FloatArray, Int>? {
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") return null
        var pos = 12
        var channels = 0
        var rate = 0
        var bits = 0
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val declared = buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    val format = buf.getShort(body).toInt() and 0xFFFF
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                    // 1 = PCM, 0xFFFE = extensible (still PCM here).
                    if (format != 1 && format != 0xFFFE) return null
                }
                "data" -> {
                    if (channels <= 0 || rate <= 0 || bits != 16) return null
                    // A header that was never finalised says 0 (or too much): use what's there.
                    val available = (bytes.size - body).toLong()
                    val size = (if (declared == 0L || declared > available) available else declared).toInt()
                    val frames = size / (2 * channels)
                    val out = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0
                        for (c in 0 until channels) sum += buf.getShort(body + (f * channels + c) * 2).toInt()
                        out[f] = sum / (channels * 32_768f)
                    }
                    return out to rate
                }
            }
            if (declared > bytes.size) return null
            pos = body + declared.toInt() + (declared.toInt() and 1)
        }
        return null
    }

    private fun tag(bytes: ByteArray, at: Int): String =
        if (at + 4 > bytes.size) "" else String(bytes, at, 4, Charsets.US_ASCII)
}
