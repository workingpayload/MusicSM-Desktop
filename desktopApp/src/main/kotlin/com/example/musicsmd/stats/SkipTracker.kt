package com.example.musicsmd.stats

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.settings.AppPaths
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the listener's recent skips say to leave out of recommendations. */
data class SkipSignals(
    /** Songs skipped at least once lately: not worth suggesting as something new. */
    val skippedOnce: Set<String> = emptySet(),
    /** Songs skipped repeatedly: not even worth suggesting again from their own history. */
    val skippedRepeatedly: Set<String> = emptySet(),
    /** Normalized artist keys with this many skips lately, for the feed to weigh against plays. */
    val artistSkips: Map<String, Int> = emptyMap(),
) {
    fun artistSkipCount(credit: String): Int =
        ArtistMatching.creditKeys(credit).maxOfOrNull { artistSkips[it] ?: 0 } ?: 0
}

/**
 * Remembers songs the listener moved on from early, the negative signal the play log can't give
 * (it only records real plays). A song played through afterwards is forgiven. Kept for
 * [WINDOW_DAYS] in `skips.json`.
 */
class SkipTracker(
    private val scope: CoroutineScope,
    private val file: File = File(AppPaths.dataDir, "skips.json"),
    private val now: () -> Long = System::currentTimeMillis,
) : PlaybackListener {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val events = MutableStateFlow(load())

    override fun onSongFinished(song: Song, listenedMs: Long, durationMs: Long) {
        if (song.id.isBlank()) return
        when {
            isSkip(listenedMs, durationMs) -> scope.launch(Dispatchers.IO) { record(song) }
            isFullListen(listenedMs, durationMs) -> scope.launch(Dispatchers.IO) { forgive(song.id) }
        }
    }

    fun signals(): SkipSignals {
        val cutoff = now() - TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        val recent = events.value.filter { it.at >= cutoff }
        val perSong = recent.groupingBy { it.songId }.eachCount()
        val perArtist = HashMap<String, Int>()
        recent.forEach { event ->
            ArtistMatching.creditKeys(event.artist).forEach { key -> perArtist[key] = (perArtist[key] ?: 0) + 1 }
        }
        return SkipSignals(
            skippedOnce = perSong.keys,
            skippedRepeatedly = perSong.filterValues { it >= REPEATED_SKIPS }.keys,
            artistSkips = perArtist,
        )
    }

    /** Forgets every skip (part of clearing listening history). */
    fun clear() {
        events.value = emptyList()
        persist()
    }

    private fun record(song: Song) {
        val cutoff = now() - TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        events.update { current ->
            (current.filter { it.at >= cutoff } + SkipEvent(song.id, song.artist, now())).takeLast(MAX_EVENTS)
        }
        persist()
    }

    private fun forgive(songId: String) {
        if (events.value.none { it.songId == songId }) return
        events.update { current -> current.filterNot { it.songId == songId } }
        persist()
    }

    private fun load(): List<SkipEvent> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(SkipStore.serializer(), file.readText()).events
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist() {
        // Read inside the lock: a concurrent writer must never save an older snapshot last.
        val value = events.value
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SkipStore.serializer(), SkipStore(value)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        /** Moving on before this much was heard is a skip... */
        const val SKIP_BEFORE_MS = 30_000L

        /** ...unless it was barely started, which is more likely a change of plan or a misclick. */
        const val MIN_HEARD_MS = 1_000L

        const val REPEATED_SKIPS = 2
        const val WINDOW_DAYS = 60L
        const val MAX_EVENTS = 1_000

        internal fun isSkip(listenedMs: Long, durationMs: Long): Boolean =
            listenedMs in MIN_HEARD_MS until SKIP_BEFORE_MS && (durationMs <= 0L || durationMs > 2 * SKIP_BEFORE_MS)

        internal fun isFullListen(listenedMs: Long, durationMs: Long): Boolean =
            durationMs > 0L && listenedMs >= durationMs * 3 / 4
    }
}

@Serializable
private data class SkipStore(val events: List<SkipEvent> = emptyList())

@Serializable
private data class SkipEvent(val songId: String, val artist: String, val at: Long)
