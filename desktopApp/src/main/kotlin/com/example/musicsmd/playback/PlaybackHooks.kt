package com.example.musicsmd.playback

import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Song

/**
 * Resolves a song to something on disk (a download or a local-library file) so it plays without
 * the network. Checked before [com.example.musicsm.domain.source.MusicSource.resolveStream].
 */
fun interface OfflineSource {
    fun localStream(songId: String): PlayableStream?
}

/** Tries each source in order; first hit wins. */
class CompositeOfflineSource(private val sources: List<OfflineSource>) : OfflineSource {
    override fun localStream(songId: String): PlayableStream? =
        sources.firstNotNullOfOrNull { runCatching { it.localStream(songId) }.getOrNull() }
}

/** Observes track transitions — e.g. the stats log records a play event on [onSongFinished]. */
interface PlaybackListener {
    fun onSongStarted(song: Song) {}

    /** [listenedMs] is time actually played (seeks excluded); [durationMs] is 0 when unknown. */
    fun onSongFinished(song: Song, listenedMs: Long, durationMs: Long) {}
}
