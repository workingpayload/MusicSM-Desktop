package com.example.innertube.model

/**
 * The public shape of everything this module returns.
 *
 * These are deliberately plain and provider-neutral. YouTube's own JSON is a deep tree of
 * "renderers" whose shape changes without notice; keeping that entirely inside the module means a
 * change on their side is a parser fix here rather than a refactor of the app.
 */

/** A reference to an artist. [id] is null for credits YouTube renders as plain text. */
data class YtArtistRef(
    val name: String,
    val id: String? = null,
)

/** A reference to an album. [id] is a `MPREb_…` browse id. */
data class YtAlbumRef(
    val title: String,
    val id: String? = null,
)

data class YtSong(
    val id: String,
    val title: String,
    val artists: List<YtArtistRef> = emptyList(),
    val album: YtAlbumRef? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
) {
    /** Artists joined the way a music app displays a credit line. */
    val artistLine: String get() = artists.joinToString(", ") { it.name }
}

data class YtAlbum(
    val id: String,
    val title: String,
    val artists: List<YtArtistRef> = emptyList(),
    val year: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<YtSong> = emptyList(),
) {
    val artistLine: String get() = artists.joinToString(", ") { it.name }
}

data class YtArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscribers: String? = null,
    val topSongs: List<YtSong> = emptyList(),
    val albums: List<YtAlbum> = emptyList(),
)

data class YtPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val songs: List<YtSong> = emptyList(),
)

/** One card on a shelf. A shelf can legitimately mix these, so the type is carried per item. */
sealed interface YtItem {
    data class Song(val song: YtSong) : YtItem
    data class Album(val album: YtAlbum) : YtItem
    data class Artist(val artist: YtArtist) : YtItem
    data class Playlist(val playlist: YtPlaylist) : YtItem
}

/** A titled horizontal row, as YouTube Music lays out its browse pages. */
data class YtShelf(
    val title: String,
    val items: List<YtItem>,
)

/**
 * A browse page reduced to its shelves.
 *
 * [continuation] is an opaque token for the next batch of shelves, or null when the page has no
 * more to give. Callers should treat it as a cursor and nothing else.
 */
data class YtPage(
    val shelves: List<YtShelf> = emptyList(),
    val continuation: String? = null,
) {
    val songs: List<YtSong> get() = items<YtItem.Song>().map { it.song }
    val albums: List<YtAlbum> get() = items<YtItem.Album>().map { it.album }
    val artists: List<YtArtist> get() = items<YtItem.Artist>().map { it.artist }
    val playlists: List<YtPlaylist> get() = items<YtItem.Playlist>().map { it.playlist }

    private inline fun <reified T : YtItem> items(): List<T> =
        shelves.flatMap { it.items }.filterIsInstance<T>()
}

/**
 * Which kind of result a search should return.
 *
 * [params] is the opaque, URL-encoded filter token YouTube Music's own web client sends. Without
 * one, a search returns a mixed "top result" page; with one, the response is a single clean shelf
 * of the requested type, which is far cheaper to parse and far more predictable.
 */
enum class YtSearchFilter(val params: String) {
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    PLAYLISTS("EgWKAQIoAWoKEAkQChAFEAMQBA%3D%3D"),
}
