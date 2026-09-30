package com.example.musicsmd.stats

import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.DailyPlayCount
import com.example.musicsm.domain.model.HourlyPlayCount
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.settings.AppPaths
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** File-backed append-only listening log for desktop stats and personalization. */
class FileStatsRepository(
    private val scope: CoroutineScope,
    private val file: File = File(AppPaths.dataDir, "stats.json"),
) : StatsRepository, PlaybackListener {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val events = MutableStateFlow(load())

    override fun onSongFinished(song: Song, listenedMs: Long, durationMs: Long) {
        if (!countsAsPlay(listenedMs, durationMs)) return
        scope.launch(Dispatchers.IO) { record(song, listenedMs.coerceAtLeast(0L)) }
    }

    override fun stats(range: StatsRange): Flow<ListeningStats> =
        events.map { aggregate(it, range) }

    override suspend fun forgottenFavorites(limit: Int): List<SongPlayCount> {
        if (limit <= 0) return emptyList()
        val staleBefore = LocalDate.now(ZoneId.systemDefault())
            .minusWeeks(FORGOTTEN_STALE_WEEKS)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return events.value
            .groupBy { it.song.id }
            .mapNotNull { (_, plays) ->
                val count = plays.size
                val last = plays.maxOfOrNull { it.playedAt } ?: return@mapNotNull null
                if (count < FORGOTTEN_MIN_PLAYS || last >= staleBefore) return@mapNotNull null
                SongPlayCount(plays.latestSong(), count) to last
            }
            .sortedWith(compareByDescending<Pair<SongPlayCount, Long>> { it.first.playCount }.thenBy { it.second })
            .take(limit)
            .map { it.first }
    }

    override suspend fun clear() {
        events.value = emptyList()
        persist(emptyList())
    }

    private fun record(song: Song, listenedMs: Long) {
        val updated = events.value + PlayEventDto(
            song = PersistedSong.from(song),
            playedAt = System.currentTimeMillis(),
            listenedMs = listenedMs,
        )
        events.value = updated
        persist(updated)
    }

    private fun aggregate(allEvents: List<PlayEventDto>, range: StatsRange): ListeningStats {
        val since = startOf(range)
        val scoped = allEvents.filter { it.playedAt >= since }
        if (scoped.isEmpty()) {
            return ListeningStats(range = range, firstPlayedAt = allEvents.minOfOrNull { it.playedAt })
        }

        val topSongs = scoped
            .groupBy { it.song.id }
            .map { (_, plays) -> SongPlayCount(plays.latestSong(), plays.size) to (plays.maxOfOrNull { it.playedAt } ?: 0L) }
            .sortedWith(compareByDescending<Pair<SongPlayCount, Long>> { it.first.playCount }.thenByDescending { it.second })
            .take(TOP_LIMIT)
            .map { it.first }

        val topArtists = scoped
            .filter { it.song.artist.isNotBlank() }
            .groupBy { it.song.artist.trim() }
            .map { (artist, plays) ->
                ArtistPlayCount(
                    name = artist,
                    artworkUrl = plays.firstNotNullOfOrNull { it.song.artworkUrl },
                    playCount = plays.size,
                    songCount = plays.map { it.song.id }.distinct().size,
                    totalMs = plays.sumOf { it.statDurationMs },
                )
            }
            .sortedWith(compareByDescending<ArtistPlayCount> { it.playCount }.thenByDescending { it.songCount })
            .take(TOP_LIMIT)

        val zone = ZoneId.systemDefault()
        val byDay = scoped
            .groupingBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate().toEpochDay() }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { DailyPlayCount(epochDay = it.key, playCount = it.value) }

        val byHour = scoped
            .groupingBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).hour }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { HourlyPlayCount(hour = it.key, playCount = it.value) }

        return ListeningStats(
            range = range,
            totalPlays = scoped.size,
            totalMs = scoped.sumOf { it.statDurationMs },
            distinctSongs = scoped.map { it.song.id }.distinct().size,
            distinctArtists = scoped.map { it.song.artist.trim() }.filter { it.isNotBlank() }.distinct().size,
            firstPlayedAt = allEvents.minOfOrNull { it.playedAt },
            topSongs = topSongs,
            topArtists = topArtists,
            byDay = byDay,
            byHour = byHour,
        )
    }

    private fun startOf(range: StatsRange): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = when (range) {
            StatsRange.LAST_4_WEEKS -> today.minusWeeks(4)
            StatsRange.LAST_6_MONTHS -> today.minusMonths(6)
            StatsRange.THIS_YEAR -> today.withDayOfYear(1)
            StatsRange.ALL_TIME -> return 0L
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun countsAsPlay(listenedMs: Long, durationMs: Long): Boolean {
        if (listenedMs <= 0L) return false
        if (durationMs <= 0L) return listenedMs >= MIN_LISTENED_MS
        val threshold = minOf(MIN_LISTENED_MS, (durationMs / 2).coerceAtLeast(1L))
        return listenedMs >= threshold
    }

    private fun load(): List<PlayEventDto> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(StatsStore.serializer(), file.readText()).events
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist(value: List<PlayEventDto>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(StatsStore.serializer(), StatsStore(value)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private val PlayEventDto.statDurationMs: Long
        get() = song.durationMs.takeIf { it > 0L } ?: listenedMs

    private fun List<PlayEventDto>.latestSong(): Song = maxBy { it.playedAt }.song.toDomain()

    private companion object {
        const val TOP_LIMIT = 25
        const val MIN_LISTENED_MS = 30_000L
        const val FORGOTTEN_STALE_WEEKS = 6L
        const val FORGOTTEN_MIN_PLAYS = 3
    }
}

@Serializable
private data class StatsStore(val events: List<PlayEventDto> = emptyList())

@Serializable
private data class PlayEventDto(
    val song: PersistedSong,
    val playedAt: Long,
    val listenedMs: Long = 0L,
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
    fun toDomain() = Song(id, title, artist, album, artworkUrl, durationMs)

    companion object {
        fun from(song: Song) = PersistedSong(song.id, song.title, song.artist, song.album, song.artworkUrl, song.durationMs)
    }
}
