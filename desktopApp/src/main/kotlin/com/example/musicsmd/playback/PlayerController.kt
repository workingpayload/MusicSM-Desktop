package com.example.musicsmd.playback

import com.example.musicsm.domain.model.PlayableStream
import uk.co.caprica.vlcj.player.base.AudioDevice
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/** Desktop audio output exposed by libVLC. */
data class AudioOutputDeviceInfo(
    val id: String,
    val name: String,
    val isCurrent: Boolean,
)

/**
 * Desktop equivalent of the mobile app's Media3/ExoPlayer bridge. Wraps libVLC (via vlcj) since
 * ExoPlayer doesn't run on the JVM — everything above this (queue, UI) is provider-agnostic, so
 * only this class and [com.example.musicsmd.player.PlayerViewModel] know about libVLC.
 *
 * Requires VLC to be installed on the host machine (libvlc on the system path).
 */
class PlayerController {
    private val audioComponent = AudioPlayerComponent()
    val mediaPlayer: MediaPlayer get() = audioComponent.mediaPlayer()

    var onEndReached: (() -> Unit)? = null
    var onPositionChanged: ((positionMs: Long, durationMs: Long) -> Unit)? = null

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                onEndReached?.invoke()
            }

            override fun positionChanged(mediaPlayer: MediaPlayer, newPosition: Float) {
                val durationMs = mediaPlayer.status().length()
                onPositionChanged?.invoke((newPosition * durationMs).toLong(), durationMs)
            }
        })
    }

    fun play(stream: PlayableStream, rate: Float = 1f, startPositionMs: Long = 0L, outputDeviceId: String? = null) {
        mediaPlayer.media().play(stream.url)
        setPlaybackSpeed(rate)
        outputDeviceId?.let(::setOutputDevice)
        if (startPositionMs > 0L) {
            mediaPlayer.submit {
                Thread.sleep(350)
                mediaPlayer.controls().setTime(startPositionMs)
            }
        }
    }

    fun pause() = mediaPlayer.controls().setPause(true)

    fun resume() = mediaPlayer.controls().setPause(false)

    fun togglePlayPause() {
        if (mediaPlayer.status().isPlaying) pause() else resume()
    }

    fun seekTo(positionMs: Long) = mediaPlayer.controls().setTime(positionMs)

    fun setVolume(percent: Int) {
        mediaPlayer.audio().setVolume(percent.coerceIn(0, 100))
    }

    fun setPlaybackSpeed(rate: Float) {
        mediaPlayer.controls().setRate(rate.coerceIn(0.5f, 2f))
    }

    fun outputDevices(): List<AudioOutputDeviceInfo> {
        val current = runCatching { mediaPlayer.audio().outputDevice() }.getOrNull()
        val devices = runCatching { mediaPlayer.audio().outputDevices() }.getOrDefault(emptyList())
            .ifEmpty {
                runCatching {
                    audioComponent.mediaPlayerFactory().audio().audioOutputs().flatMap { it.devices }
                }.getOrDefault(emptyList())
            }
        return devices.distinctBy(AudioDevice::getDeviceId).map { device ->
            AudioOutputDeviceInfo(
                id = device.deviceId,
                name = device.longName.ifBlank { device.deviceId },
                isCurrent = device.deviceId == current,
            )
        }
    }

    fun setOutputDevice(deviceId: String) {
        runCatching { mediaPlayer.audio().setOutputDevice(null, deviceId) }
    }

    fun equalizerPresets(): List<String> =
        runCatching { audioComponent.mediaPlayerFactory().equalizer().presets() }.getOrDefault(emptyList())

    fun equalizerBands(): List<Float> =
        runCatching { audioComponent.mediaPlayerFactory().equalizer().bands() }.getOrDefault(emptyList())

    /** A libVLC preset's preamp and per-band gains, so the UI can show what the preset does. */
    fun equalizerPresetValues(preset: String): Pair<Float, List<Float>>? = runCatching {
        val eq = audioComponent.mediaPlayerFactory().equalizer().newEqualizer(preset)
        eq.preamp() to eq.amps().toList()
    }.getOrNull()

    fun applyEqualizer(enabled: Boolean, preset: String?, preamp: Float, bands: List<Float>) {
        if (!enabled) {
            mediaPlayer.audio().setEqualizer(null)
            return
        }
        val api = audioComponent.mediaPlayerFactory().equalizer()
        val eq: Equalizer = preset?.takeIf { it in api.presets() }?.let(api::newEqualizer) ?: api.newEqualizer()
        eq.setPreamp(preamp.coerceIn(MIN_EQ_DB, MAX_EQ_DB))
        if (bands.isNotEmpty()) {
            bands.take(eq.bandCount()).forEachIndexed { index, amp ->
                eq.setAmp(index, amp.coerceIn(MIN_EQ_DB, MAX_EQ_DB))
            }
        }
        mediaPlayer.audio().setEqualizer(eq)
    }

    fun stop() = mediaPlayer.controls().stop()

    fun release() = audioComponent.release()

    private companion object {
        const val MIN_EQ_DB = -20f
        const val MAX_EQ_DB = 20f
    }
}
