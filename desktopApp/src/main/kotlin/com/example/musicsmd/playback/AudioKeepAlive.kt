package com.example.musicsmd.playback

import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** An open audio stream playing silence; [close] stops it. */
fun interface SilentStream {
    fun close()
}

/**
 * Keeps the audio device streaming silence while music plays.
 *
 * libVLC closes and reopens its Windows audio stream for every track. A wired output doesn't
 * mind, but a Bluetooth speaker sees the device go quiet and start again, restarts its link, and
 * drops about a second just after each song begins. Browsers keep one stream open, which is why
 * they don't. A silent stream of our own keeps the device busy across track changes, so libVLC's
 * new stream joins one that is already running. It stays open for [lingerMs] after playback stops,
 * so resuming is smooth too, then lets the device (and the speaker) go idle.
 */
class AudioKeepAlive(
    scope: CoroutineScope,
    private val lingerMs: Long = DEFAULT_LINGER_MS,
    private val open: (deviceName: String?) -> SilentStream? = ::openSilentStream,
) {
    private data class Request(val active: Boolean, val deviceName: String?)

    // One consumer applies requests in the order they were made.
    private val requests = Channel<Request>(Channel.UNLIMITED)

    @Volatile
    private var stream: SilentStream? = null
    private var device: String? = null

    internal val isOpen: Boolean get() = stream != null

    init {
        scope.launch(Dispatchers.IO) {
            var active = false
            try {
                while (true) {
                    val lingering = stream != null && !active
                    val next = if (lingering) withTimeoutOrNull(lingerMs) { requests.receive() } else requests.receive()
                    if (next == null) {
                        closeStream()
                        continue
                    }
                    active = next.active
                    if (active) {
                        if (stream != null && device != next.deviceName) closeStream()
                        if (stream == null) {
                            stream = open(next.deviceName)
                            device = next.deviceName
                        }
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
            } finally {
                closeStream()
            }
        }
    }

    /** Playing (or loading) on [deviceName] — null for the system default — or stopped. */
    fun update(active: Boolean, deviceName: String?) {
        requests.trySend(Request(active, deviceName))
    }

    /** Stops for good, closing the stream. */
    fun close() {
        requests.close()
    }

    private fun closeStream() {
        stream?.close()
        stream = null
        device = null
    }

    companion object {
        const val DEFAULT_LINGER_MS = 5 * 60 * 1000L
    }
}

private val SilenceFormat = AudioFormat(48_000f, 16, 2, true, false)

/** Silence on the named output (as libVLC names it), or the system default. Null if none opens. */
fun openSilentStream(deviceName: String?): SilentStream? = try {
    val info = DataLine.Info(SourceDataLine::class.java, SilenceFormat)
    val mixer = deviceName?.let { name ->
        AudioSystem.getMixerInfo().firstOrNull { it.name == name && AudioSystem.getMixer(it).isLineSupported(info) }
    }
    val line = (if (mixer != null) AudioSystem.getMixer(mixer).getLine(info) else AudioSystem.getLine(info)) as SourceDataLine
    // ~200 ms of buffer, written 20 ms at a time.
    line.open(SilenceFormat, SilenceFormat.frameSize * 9_600)
    line.start()
    JavaSoundSilence(line)
} catch (e: Exception) {
    System.err.println("Audio keep-alive unavailable: ${e.message}")
    null
}

private class JavaSoundSilence(private val line: SourceDataLine) : SilentStream {
    private val running = AtomicBoolean(true)
    private val writer = Thread({
        val zeros = ByteArray(SilenceFormat.frameSize * 960)
        while (running.get()) {
            if (line.write(zeros, 0, zeros.size) <= 0) Thread.sleep(20)
        }
    }, "audio-keep-alive").apply {
        isDaemon = true
        start()
    }

    override fun close() {
        running.set(false)
        line.stop()
        line.flush()
        writer.join(1_000)
        line.close()
    }
}
