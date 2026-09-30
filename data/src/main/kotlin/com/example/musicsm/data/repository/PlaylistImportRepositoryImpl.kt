package com.example.musicsm.data.repository

import com.example.musicsm.data.applemusic.AppleMusicPublicClient
import com.example.musicsm.data.importer.ImportLink
import com.example.musicsm.data.importer.ImportedPlaylist
import com.example.musicsm.data.importer.ImportedTrack
import com.example.musicsm.data.importer.isRadioMix
import com.example.musicsm.data.importer.parseImportLink
import com.example.musicsm.data.spotify.SpotifyPublicClient
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.ImportResult
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Desktop/JVM playlist importer mirroring the Android implementation. */
@Singleton
class PlaylistImportRepositoryImpl @Inject constructor(
    private val spotify: SpotifyPublicClient,
    private val appleMusic: AppleMusicPublicClient,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
) : PlaylistImportRepository {

    override suspend fun importFromLink(
        link: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<ImportResult> = try {
        Result.success(withContext(Dispatchers.IO) { importParsed(link, onProgress) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        println("[$TAG] import failed for $link: ${failure.message}")
        Result.failure(failure)
    }

    private suspend fun importParsed(link: String, onProgress: (Int, Int) -> Unit): ImportResult =
        when (val source = parseImportLink(link) ?: error(NOT_A_PLAYLIST)) {
            is ImportLink.YouTube -> importYouTube(source.playlistId, onProgress)
            is ImportLink.Spotify -> importMatched(spotify.fetchPlaylist(source.playlistId), onProgress)
            is ImportLink.AppleMusic -> importMatched(appleMusic.fetchPlaylist(source.storefront, source.playlistId), onProgress)
        }

    private suspend fun importYouTube(playlistId: String, onProgress: (Int, Int) -> Unit): ImportResult {
        val playlist = try {
            val maxTracks = if (isRadioMix(playlistId)) MAX_MIX_TRACKS else MAX_YOUTUBE_TRACKS
            musicRepository.fullPlaylist(playlistId, maxTracks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IllegalStateException("Couldn't read that YouTube playlist (is it public?)", failure)
        }
        val songs = playlist.songs.distinctBy { it.id }
        if (songs.isEmpty()) error("That YouTube playlist is empty or private")
        val name = playlist.name.ifBlank { DEFAULT_NAME }
        println("[$TAG] YouTube '$name': ${songs.size} tracks")
        val id = libraryRepository.createPlaylist(name)
        playlist.artworkUrl?.let { libraryRepository.setPlaylistArtwork(id, it) }
        songs.forEachIndexed { index, song ->
            libraryRepository.addToPlaylist(id, song)
            onProgress(index + 1, songs.size)
        }
        return ImportResult(id, name, songs.size, songs.size)
    }

    private suspend fun importMatched(playlist: ImportedPlaylist, onProgress: (Int, Int) -> Unit): ImportResult {
        val tracks = playlist.tracks
        if (tracks.isEmpty()) error("No tracks found in that playlist")
        println("[$TAG] '${playlist.name}': ${tracks.size} tracks")
        val id = libraryRepository.createPlaylist(playlist.name.ifBlank { DEFAULT_NAME })
        playlist.coverUrl?.let { libraryRepository.setPlaylistArtwork(id, it) }
        var matched = 0
        var done = 0
        val addedSongIds = HashSet<String>()
        for (batch in tracks.chunked(PARALLEL_SEARCHES)) {
            val songs = coroutineScope { batch.map { async { find(it) } }.awaitAll() }
            for (song in songs) {
                if (song != null && addedSongIds.add(song.id)) {
                    libraryRepository.addToPlaylist(id, song)
                    matched++
                }
                onProgress(++done, tracks.size)
            }
        }
        return ImportResult(id, playlist.name.ifBlank { DEFAULT_NAME }, matched, tracks.size)
    }

    private suspend fun find(track: ImportedTrack): Song? = try {
        pickMatch(musicRepository.searchSongs(track.searchQuery), track.durationMs)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        println("[$TAG] search failed for '${track.searchQuery}': ${failure.message}")
        null
    }

    private companion object {
        const val TAG = "PlaylistImport"
        const val NOT_A_PLAYLIST = "Paste a Spotify, Apple Music or YouTube playlist link"
        const val DEFAULT_NAME = "Imported playlist"
        const val MAX_YOUTUBE_TRACKS = 5_000
        const val MAX_MIX_TRACKS = 50
        const val PARALLEL_SEARCHES = 4
    }
}

/** Picks the first of the top five results whose duration is within ±10 s, otherwise the top hit. */
internal fun pickMatch(results: List<Song>, durationMs: Long): Song? {
    if (durationMs <= 0L) return results.firstOrNull()
    return results.take(MATCH_CANDIDATES)
        .firstOrNull { it.durationMs > 0L && abs(it.durationMs - durationMs) <= MATCH_TOLERANCE_MS }
        ?: results.firstOrNull()
}

private const val MATCH_CANDIDATES = 5
private const val MATCH_TOLERANCE_MS = 10_000L
