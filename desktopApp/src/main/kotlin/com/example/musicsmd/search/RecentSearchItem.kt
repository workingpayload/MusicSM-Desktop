package com.example.musicsmd.search

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Song
import kotlinx.serialization.Serializable

@Serializable
enum class RecentKind { SONG, ALBUM, ARTIST }

/**
 * A song, album or artist opened from search results, kept so Search can show it again as a card.
 * Flat rather than the domain types: only what a card and a later tap need is stored.
 */
@Serializable
data class RecentSearchItem(
    val kind: RecentKind,
    val id: String,
    val title: String,
    /** Artist for songs and albums; subscriber count for artists. */
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
) {
    val key: String get() = "${kind.name}:$id"

    fun toSong() = Song(id = id, title = title, artist = subtitle, album = album, artworkUrl = artworkUrl, durationMs = durationMs)

    fun toAlbum() = Album(id = id, title = title, artist = subtitle, artworkUrl = artworkUrl)

    fun toArtist() = Artist(id = id, name = title, artworkUrl = artworkUrl, subscribers = subtitle.ifBlank { null })

    companion object {
        fun of(song: Song) = RecentSearchItem(
            kind = RecentKind.SONG,
            id = song.id,
            title = song.title,
            subtitle = song.artist,
            artworkUrl = song.artworkUrl,
            album = song.album,
            durationMs = song.durationMs,
        )

        fun of(album: Album) = RecentSearchItem(RecentKind.ALBUM, album.id, album.title, album.artist, album.artworkUrl)

        fun of(artist: Artist) =
            RecentSearchItem(RecentKind.ARTIST, artist.id, artist.name, artist.subscribers.orEmpty(), artist.artworkUrl)
    }
}
