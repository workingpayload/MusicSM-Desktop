package com.example.musicsmd.playback

import com.example.musicsm.domain.model.PlayableStream
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

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

    fun play(stream: PlayableStream) {
        mediaPlayer.media().play(stream.url)
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

    fun stop() = mediaPlayer.controls().stop()

    fun release() = audioComponent.release()
}
