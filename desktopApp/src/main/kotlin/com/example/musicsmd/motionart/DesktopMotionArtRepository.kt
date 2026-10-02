package com.example.musicsmd.motionart

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopMotionArtRepository(
    private val lookup: suspend (Song, MotionArtProvider) -> MotionArt?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val artist: String, val title: String, val album: String?, val source: MotionArtProvider)
    private data class Entry(val art: MotionArt?, val expiresAt: Long)

    private val mutex = Mutex()
    private val cache = LinkedHashMap<Key, Entry>(0, 0.75f, true)

    suspend fun forSong(song: Song, source: MotionArtProvider): MotionArt? = mutex.withLock {
        val key = Key(song.artist, song.title, song.album, source)
        cache[key]?.takeIf { it.expiresAt > now() }?.let { return@withLock it.art }
        val art = lookup(song, source)
        currentCoroutineContext().ensureActive()
        cache[key] = Entry(art, now() + if (art == null) MISS_TTL_MS else HIT_TTL_MS)
        while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        art
    }

    private companion object {
        const val CACHE_SIZE = 256
        const val HIT_TTL_MS = 60 * 60 * 1000L
        const val MISS_TTL_MS = 15 * 60 * 1000L
    }
}
