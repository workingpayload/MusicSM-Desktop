package com.example.musicsm.data.source.youtube

import com.example.musicsm.domain.match.ArtistMatching
import com.example.musicsm.domain.model.Song

/**
 * The audio-only version of a song, for a video titled [title] from channel [artist], among
 * [candidates] (YouTube Music song search results, best first); null if none is safe to use.
 *
 * It has to be the same song and the same version: a remix or a live take doesn't stand in for
 * the original. A result credited to the same artist wins. Failing that (a channel isn't always
 * named after the artist: an old band name, a label) only YouTube Music's own top result is
 * trusted, and only if it is the same song.
 */
internal fun pickAudioVersion(title: String, artist: String, candidates: List<Song>): Song? {
    val wanted = songKey(title, artist) ?: return null
    val same = candidates.filter { songKey(it.title, it.artist) == wanted }
    val artistKeys = ArtistMatching.creditKeys(artist)
    return same.firstOrNull { song -> ArtistMatching.creditKeys(song.artist).any { it in artistKeys } }
        ?: candidates.firstOrNull()?.takeIf { it in same }
}

/** A song's name without credits, tags or "Artist - " prefix, plus its version markers. */
private fun songKey(title: String, artist: String): Pair<String, Set<String>>? {
    val bare = FEATURING.replace(PIPE_SUFFIX.replace(BRACKETED.replace(title, " "), ""), "")
    var core = ArtistMatching.normalize(bare)
    val artistKey = ArtistMatching.normalize(artist)
    if (artistKey.isNotEmpty() && core.length > artistKey.length && core.startsWith(artistKey)) {
        core = core.removePrefix(artistKey)
    }
    if (core.isEmpty()) return null
    val markers = VERSION_MARKERS.findAll(title).map { it.value.lowercase().replace(SPACES, "") }.toSet()
    return core to markers
}

private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
private val PIPE_SUFFIX = Regex("""\s[|｜].*$""")
private val FEATURING = Regex("""(?i)\s(?:feat\.?|ft\.?|featuring)\s.*$""")
private val SPACES = Regex("""\s+""")
private val VERSION_MARKERS = Regex(
    """(?i)\b(?:remix|live|acoustic|unplugged|instrumental|karaoke|cover|sped\s*up|slowed|reverb|lo-?fi|8d|mashup|a\s*cappella|acapella)\b""",
)
