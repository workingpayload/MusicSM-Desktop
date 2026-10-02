package com.example.musicsmd.motionart

import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

internal data class VideoFrame(val width: Int, val height: Int, val pixels: ByteArray)

internal fun motionVideoSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Invalid animated artwork dimensions: ${width}x$height" }
    val scale = minOf(1.0, MAX_VIDEO_PX.toDouble() / maxOf(width, height))
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}

internal fun copyVideoFrame(buffer: ByteBuffer, width: Int, height: Int, pitch: Int): VideoFrame {
    require(width > 0 && height > 0 && pitch >= width * 4) { "Invalid animated artwork frame layout" }
    val pixels = ByteArray(width * height * 4)
    val source = buffer.duplicate()
    for (row in 0 until height) {
        source.position(row * pitch)
        source.get(pixels, row * width * 4, width * 4)
    }
    return VideoFrame(width, height, pixels)
}

/** Copies libVLC's callback surface into Compose, so clipping, glass and overlays still work. */
internal class MotionArtworkPlayer private constructor(private val factory: MediaPlayerFactory) {
    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
    internal val mediaPlayer: MediaPlayer get() = player
    internal val isReleased: Boolean get() = released
    private val mutex = Mutex()
    private val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _frame = MutableStateFlow<VideoFrame?>(null)
    val frame: StateFlow<VideoFrame?> = _frame.asStateFlow()

    @Volatile private var released = false
    @Volatile private var failed = false
    @Volatile private var desiredPlaying = false
    private var lastFrameNanos = 0L

    init {
        try {
            val formatCallback = object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    val (width, height) = motionVideoSize(sourceWidth, sourceHeight)
                    return RV32BufferFormat(width, height)
                }

                override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit
                override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
            }
            val renderCallback = object : RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer) = Unit
                override fun unlock(mediaPlayer: MediaPlayer) = Unit

                override fun display(
                    mediaPlayer: MediaPlayer,
                    buffers: Array<ByteBuffer>,
                    format: BufferFormat,
                    displayWidth: Int,
                    displayHeight: Int,
                ) {
                    if (released || failed) return
                    if (!desiredPlaying && _frame.value != null) return
                    val now = System.nanoTime()
                    if (now - lastFrameNanos < FRAME_INTERVAL_NANOS) return
                    try {
                        val copied = copyVideoFrame(buffers[0], format.width, format.height, format.pitches[0])
                        if (!released && !failed) _frame.value = copied
                        lastFrameNanos = now
                    } catch (error: RuntimeException) {
                        fail("Could not copy video frame: ${error.message}")
                    }
                }
            }
            player.videoSurface().set(factory.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
            player.controls().setRepeat(true)
            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    eventsScope.launch { applyPlaybackState() }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    fail("libVLC could not play the animated cover")
                }
            })
        } catch (error: Exception) {
            eventsScope.cancel()
            player.release()
            throw error
        }
    }

    suspend fun start(url: String, playing: Boolean) = mutex.withLock {
        check(!released) { "Animated artwork player is released" }
        desiredPlaying = playing
        val options = if (playing) arrayOf(":no-audio") else arrayOf(":no-audio", ":start-paused")
        if (!player.media().play(url, *options)) throw IOException("libVLC could not open the animated cover")
    }

    suspend fun setPlaying(playing: Boolean) = mutex.withLock {
        desiredPlaying = playing
        if (!released && !failed) player.controls().setPause(!playing)
    }

    private suspend fun applyPlaybackState() = mutex.withLock {
        if (!released && !failed) player.controls().setPause(!desiredPlaying)
    }

    suspend fun release() = mutex.withLock {
        if (!released) {
            released = true
            eventsScope.cancel()
            try {
                try {
                    player.controls().setRepeat(false)
                    player.controls().stop()
                } finally {
                    player.release()
                }
            } finally {
                factory.release()
                _frame.value = null
            }
        }
    }

    @Synchronized
    private fun fail(message: String) {
        if (released || failed) return
        failed = true
        _frame.value = null
        System.err.println("Animated artwork playback failed: $message")
        eventsScope.launch { release() }
    }

    companion object {
        fun open(): MotionArtworkPlayer {
            val factory = MediaPlayerFactory(
                "--no-audio",
                "--no-sub-autodetect-file",
                "--no-video-title-show",
                "--avcodec-hw=none",
                // HLS loops otherwise open on a low rendition and switch up mid-play, which rebuilds
                // the video output and glitches. Start on the best rendition within the size cap.
                "--adaptive-logic=highest",
                "--adaptive-maxwidth=$MAX_VIDEO_PX",
                "--adaptive-maxheight=$MAX_VIDEO_PX",
            )
            try {
                return MotionArtworkPlayer(factory)
            } catch (error: Exception) {
                factory.release()
                throw error
            }
        }
    }
}

private const val MAX_VIDEO_PX = 1280
private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 30
