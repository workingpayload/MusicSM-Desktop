package com.example.musicsmd.playback

import com.example.musicsm.domain.model.Song
import com.example.musicsmd.settings.AppPaths
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Persisted paused queue snapshot used to restore Now Playing after an app restart. */
data class RestoredQueue(
    val queue: List<Song>,
    val originalQueue: List<Song>,
    val index: Int,
    val positionMs: Long,
)

/** Tiny JSON queue store under `~/.musicsm-desktop/queue.json`. */
class QueuePersistence(private val file: File = File(AppPaths.dataDir, "queue.json")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun save(queue: List<Song>, originalQueue: List<Song>, index: Int, positionMs: Long) {
        if (queue.isEmpty()) {
            clear()
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val snapshot = PersistedQueue(
                queue = queue.map(PersistedSong::from),
                originalQueue = originalQueue.takeIf { it.isNotEmpty() }?.map(PersistedSong::from).orEmpty(),
                index = index.coerceIn(queue.indices),
                positionMs = positionMs.coerceAtLeast(0L),
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(PersistedQueue.serializer(), snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun load(): RestoredQueue? = runCatching {
        val persisted = json.decodeFromString<PersistedQueue>(file.readText())
        val songs = persisted.queue.map { it.toSong() }.filter { it.id.isNotBlank() }
        if (songs.isEmpty()) return@runCatching null
        val originals = persisted.originalQueue.map { it.toSong() }
            .filter { it.id.isNotBlank() }
            .ifEmpty { songs }
        RestoredQueue(
            queue = songs,
            originalQueue = originals,
            index = persisted.index.coerceIn(songs.indices),
            positionMs = persisted.positionMs.coerceAtLeast(0L),
        )
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }
}

@Serializable
private data class PersistedQueue(
    val queue: List<PersistedSong> = emptyList(),
    val originalQueue: List<PersistedSong> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
)

@Serializable
private data class PersistedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toSong(): Song = Song(id, title, artist, album, artworkUrl, durationMs)

    companion object {
        fun from(song: Song): PersistedSong = PersistedSong(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            durationMs = song.durationMs,
        )
    }
}
