package com.example.musicsmd.library

import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Desktop equivalent of the mobile app's Room-backed `LibraryRepositoryImpl` — same
 * [LibraryRepository] contract, but persisted as a single JSON file under the user's home
 * directory instead of SQLite (no Room on plain desktop JVM without extra native setup).
 */
class FileLibraryRepository(
    private val scope: CoroutineScope,
    storeDir: File = File(System.getProperty("user.home"), ".musicsm-desktop"),
) : LibraryRepository {

    private val file = File(storeDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val state = MutableStateFlow(load())

    override fun likedSongs(): Flow<List<Song>> =
        state.map { s -> s.likedSongIds.mapNotNull { id -> s.songsById[id] }.map(PersistedSong::toDomain) }

    override fun playlists(): Flow<List<Playlist>> = state.map { s -> s.playlists.map { it.toDomain(s.songsById) } }

    override fun isLiked(songId: String): Flow<Boolean> = state.map { songId in it.likedSongIds }

    override suspend fun toggleLike(song: Song) = mutate { s ->
        val liked = song.id in s.likedSongIds
        s.copy(
            likedSongIds = if (liked) s.likedSongIds - song.id else s.likedSongIds + song.id,
            songsById = s.songsById + (song.id to PersistedSong.from(song)),
        )
    }

    override fun likedArtists(): Flow<List<Artist>> =
        state.map { s -> s.likedArtistIds.mapNotNull { id -> s.artistsById[id] }.map(PersistedArtist::toDomain) }

    override fun isArtistLiked(artistId: String): Flow<Boolean> = state.map { artistId in it.likedArtistIds }

    override suspend fun toggleArtistLike(artist: Artist) = mutate { s ->
        val liked = artist.id in s.likedArtistIds
        s.copy(
            likedArtistIds = if (liked) s.likedArtistIds - artist.id else s.likedArtistIds + artist.id,
            artistsById = s.artistsById + (artist.id to PersistedArtist.from(artist)),
        )
    }

    override fun recentlyPlayed(): Flow<List<Song>> =
        state.map { s -> s.recentPlayIds.mapNotNull { id -> s.songsById[id] }.map(PersistedSong::toDomain) }

    override suspend fun recordPlay(song: Song) = mutate { s ->
        val trimmed = (listOf(song.id) + s.recentPlayIds.filter { it != song.id }).take(MAX_RECENT)
        s.copy(recentPlayIds = trimmed, songsById = s.songsById + (song.id to PersistedSong.from(song)))
    }

    override suspend fun createPlaylist(name: String): Long {
        var newId = 0L
        mutate { s ->
            newId = s.nextPlaylistId
            s.copy(
                playlists = s.playlists + PersistedPlaylist(id = newId, name = name),
                nextPlaylistId = s.nextPlaylistId + 1,
            )
        }
        return newId
    }

    override suspend fun deletePlaylist(playlistId: Long) = mutate { s ->
        s.copy(playlists = s.playlists.filterNot { it.id == playlistId })
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = mutate { s ->
        s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(name = name) else it })
    }

    override suspend fun setPlaylistArtwork(playlistId: Long, url: String?) = mutate { s ->
        s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(artworkUrl = url) else it })
    }

    override fun playlist(playlistId: Long): Flow<Playlist?> =
        state.map { s -> s.playlists.find { it.id == playlistId }?.toDomain(s.songsById) }

    override suspend fun addToPlaylist(playlistId: Long, song: Song) = mutate { s ->
        s.copy(
            playlists = s.playlists.map {
                if (it.id == playlistId && song.id !in it.songIds) it.copy(songIds = it.songIds + song.id) else it
            },
            songsById = s.songsById + (song.id to PersistedSong.from(song)),
        )
    }

    override suspend fun removeFromPlaylist(playlistId: Long, songId: String) = mutate { s ->
        s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it })
    }

    override suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) = mutate { s ->
        s.copy(
            playlists = s.playlists.map { pl ->
                if (pl.id != playlistId) return@map pl
                val ids = pl.songIds.toMutableList()
                if (fromIndex !in ids.indices || toIndex !in ids.indices) return@map pl
                val item = ids.removeAt(fromIndex)
                ids.add(toIndex, item)
                pl.copy(songIds = ids)
            },
        )
    }

    private fun mutate(block: (PersistedLibrary) -> PersistedLibrary) {
        state.value = block(state.value)
        persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(PersistedLibrary.serializer(), state.value))
            }
        }
    }

    private fun load(): PersistedLibrary {
        if (!file.exists()) return PersistedLibrary()
        return runCatching { json.decodeFromString(PersistedLibrary.serializer(), file.readText()) }
            .getOrDefault(PersistedLibrary())
    }

    private companion object {
        const val MAX_RECENT = 200
    }
}

@Serializable
private data class PersistedLibrary(
    val likedSongIds: List<String> = emptyList(),
    val likedArtistIds: List<String> = emptyList(),
    val recentPlayIds: List<String> = emptyList(),
    val songsById: Map<String, PersistedSong> = emptyMap(),
    val artistsById: Map<String, PersistedArtist> = emptyMap(),
    val playlists: List<PersistedPlaylist> = emptyList(),
    val nextPlaylistId: Long = 1L,
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

@Serializable
private data class PersistedArtist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
) {
    fun toDomain() = Artist(id = id, name = name, artworkUrl = artworkUrl)

    companion object {
        fun from(artist: Artist) = PersistedArtist(artist.id, artist.name, artist.artworkUrl)
    }
}

@Serializable
private data class PersistedPlaylist(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val songIds: List<String> = emptyList(),
) {
    fun toDomain(songsById: Map<String, PersistedSong>) = Playlist(
        id = id.toString(),
        name = name,
        artworkUrl = artworkUrl,
        songs = songIds.mapNotNull { songsById[it] }.map(PersistedSong::toDomain),
        isLocal = true,
    )
}
