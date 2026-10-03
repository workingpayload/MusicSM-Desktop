package com.example.innertube

import com.example.innertube.internal.BrowseBody
import com.example.innertube.internal.ClientInfo
import com.example.innertube.internal.InnerTubeResponse
import com.example.innertube.internal.NextBody
import com.example.innertube.internal.NextResponse
import com.example.innertube.internal.Parsers
import com.example.innertube.internal.PlayerBody
import com.example.innertube.internal.RequestContext
import com.example.innertube.internal.SearchBody
import com.example.innertube.internal.SessionCarrier
import com.example.innertube.model.YtAlbum
import com.example.innertube.model.YtArtist
import com.example.innertube.model.YtItem
import com.example.innertube.model.YtPage
import com.example.innertube.model.YtPlaylist
import com.example.innertube.model.YtSearchFilter
import com.example.innertube.model.YtSong
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * A minimal client for YouTube Music's private InnerTube API.
 *
 * The app already resolves playable audio through NewPipeExtractor; what it lacked was YouTube
 * Music's own *metadata* — real album pages, real artist pages, and above all the
 * "you might also like" graph that powers recommendations. Search-query heuristics cannot
 * approximate that, so this module talks to the same endpoints the YouTube Music web player uses.
 *
 * It identifies itself as `WEB_REMIX`, which needs no login and no proof-of-origin token for the
 * browse and search endpoints used here; an [InnerTubeAuth] signs requests in, for the account's
 * personal home, history and playlists. Nothing in this class throws for a malformed page: a
 * response whose shape has drifted yields empty results, and only genuine transport failures
 * propagate, so a caller can fall back cleanly.
 */
class InnerTube(
    private val locale: InnerTubeLocale = InnerTubeLocale(),
    /**
     * A signed-in YouTube session to send with every request, making the home feed personal and
     * the account's history and library readable. Null (or null cookies) means anonymous.
     */
    private val auth: InnerTubeAuth? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        defaultRequest {
            header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            header("X-YouTube-Client-Version", CLIENT_VERSION)
            header("Origin", ORIGIN)
            header("Referer", "$ORIGIN/")
        }
    }

    /**
     * The anonymous session id YouTube issues on the first response.
     *
     * Paging is scoped to a session: a continuation token presented without the identity that
     * produced it comes back empty rather than failing, so every response is mined for this and
     * every later request carries it. It also keeps the feed coherent across calls instead of
     * re-rolling the catalogue on each request.
     */
    @Volatile
    private var visitorData: String? = null

    private fun context() = RequestContext(
        client = ClientInfo(
            clientName = CLIENT_NAME,
            clientVersion = CLIENT_VERSION,
            hl = locale.language,
            gl = locale.region,
            visitorData = visitorData,
        ),
    )

    /**
     * YouTube Music's home feed.
     *
     * Without a signed-in session this is the editorial feed rather than a personalised one, which
     * is exactly the right input for a local ranker to blend with the user's own listening history.
     * The returned page carries a cursor; see [homeContinuation].
     */
    suspend fun home(): YtPage = Parsers.page(Parsers.singleColumnList(browse(HOME_BROWSE_ID)))

    /**
     * The next batch of home shelves for a cursor from [home] or a previous continuation.
     *
     * The feed is effectively endless, so this is how the home screen keeps growing as it is
     * scrolled rather than stopping at whatever the first response happened to contain.
     */
    suspend fun homeContinuation(continuation: String): YtPage {
        val response = request<InnerTubeResponse>(
            path = BROWSE_PATH,
            body = BrowseBody(context = context(), continuation = continuation),
        )
        return Parsers.page(Parsers.continuationList(response))
    }

    /** Browse any feed id directly, for shelves like new releases or moods. */
    suspend fun feed(browseId: String, params: String? = null): YtPage =
        Parsers.page(Parsers.singleColumnList(browse(browseId, params)))

    suspend fun search(query: String, filter: YtSearchFilter = YtSearchFilter.SONGS): List<YtItem> {
        val response = request<InnerTubeResponse>(
            path = SEARCH_PATH,
            body = SearchBody(context = context(), query = query, params = filter.params),
        )
        return Parsers.page(Parsers.searchSections(response)).shelves.flatMap { it.items }
    }

    suspend fun searchSongs(query: String): List<YtSong> =
        search(query, YtSearchFilter.SONGS).filterIsInstance<YtItem.Song>().map { it.song }

    suspend fun album(browseId: String): YtAlbum? = Parsers.album(browseId, browse(browseId))

    /**
     * A playlist by its playlist id.
     *
     * Playlists are browsed by their id behind a `VL` prefix; callers pass the bare id and the
     * prefix is applied here so that detail is not leaked into the app. The first page holds up
     * to 100 tracks; a larger [maxSongs] follows the list's further pages, e.g. to import it all.
     */
    suspend fun playlist(playlistId: String, maxSongs: Int = 0): YtPlaylist? {
        val id = playlistId.removePrefix(PLAYLIST_BROWSE_PREFIX)
        val response = browse(PLAYLIST_BROWSE_PREFIX + id)
        val playlist = Parsers.playlist(id, response) ?: return null
        val songs = playlist.songs.toMutableList()
        var token = Parsers.playlistContinuation(response)
        while (token != null && songs.size < maxSongs) {
            val page = Parsers.playlistPage(
                request(path = BROWSE_PATH, body = BrowseBody(context = context(), continuation = token)),
                fallbackThumbnail = playlist.thumbnailUrl,
            )
            if (page.songs.isEmpty()) break
            songs += page.songs
            token = page.continuation
        }
        return if (songs.size == playlist.songs.size) playlist else playlist.copy(songs = songs.take(maxSongs))
    }

    /**
     * An artist page. The page itself lists only five top songs, so the full song list behind its
     * "Show all" is fetched too (first 100, most popular first). If that second request fails the
     * five are kept rather than failing the page.
     */
    suspend fun artist(browseId: String): YtArtist {
        val response = browse(browseId)
        val artist = Parsers.artist(browseId, response)
        val allSongsId = Parsers.artistSongsBrowseId(response) ?: return artist
        val allSongs = try {
            Parsers.tracks(Parsers.secondarySections(browse(allSongsId)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            emptyList()
        }
        return artist.copy(topSongs = (artist.topSongs + allSongs).distinctBy { it.id })
    }

    /**
     * Tracks YouTube Music associates with [videoId].
     *
     * This takes two round trips by design: the watch endpoint reports where a track's "Related"
     * page lives, and that page is then browsed. The id is not derivable from the video id, so the
     * first call cannot be skipped.
     */
    suspend fun related(videoId: String): YtPage {
        val next = request<NextResponse>(
            path = NEXT_PATH,
            body = NextBody(context = context(), videoId = videoId),
        )
        val browseId = Parsers.relatedBrowseId(next) ?: return YtPage()
        return Parsers.page(Parsers.directSections(browse(browseId)))
    }

    /** Related songs only — the common case when seeding recommendations from a played track. */
    suspend fun relatedSongs(videoId: String): List<YtSong> = related(videoId).songs

    /**
     * The signed-in account's listening history, newest first, as YouTube Music groups it
     * (Today, Yesterday, This week, months…). Empty when signed out.
     */
    suspend fun history(): YtPage = Parsers.page(Parsers.singleColumnList(browse(HISTORY_BROWSE_ID)))

    /**
     * The signed-in account's playlists, "Liked Music" (id `LM`) included; [playlist] reads any of
     * them. Podcast "Episodes for later" is left out. Empty when signed out.
     */
    suspend fun libraryPlaylists(): List<YtPlaylist> =
        Parsers.page(Parsers.singleColumnList(browse(LIBRARY_PLAYLISTS_BROWSE_ID))).playlists
            .filter { it.id != EPISODES_FOR_LATER_ID }
            .distinctBy { it.id }

    /**
     * A video's title, channel and length. The player endpoint reports these even for a video
     * it won't play without signing in (age-restricted), which is when they're needed.
     */
    suspend fun videoDetails(videoId: String): YtSong? = Parsers.videoDetails(
        videoId,
        request(path = PLAYER_PATH, body = PlayerBody(context = context(), videoId = videoId)),
    )

    /**
     * YouTube Music's own lyrics for [videoId], as plain text (it never carries timings), or null
     * when the track has none. Two round trips, like [related]: the watch endpoint names the tab.
     */
    suspend fun lyrics(videoId: String): String? {
        val next = request<NextResponse>(
            path = NEXT_PATH,
            body = NextBody(context = context(), videoId = videoId),
        )
        val browseId = Parsers.lyricsBrowseId(next) ?: return null
        return Parsers.lyricsText(browse(browseId))
    }

    private suspend fun browse(browseId: String, params: String? = null): InnerTubeResponse =
        request(
            path = BROWSE_PATH,
            body = BrowseBody(context = context(), browseId = browseId, params = params),
        )

    private suspend inline fun <reified T> request(path: String, body: Any): T {
        val cookies = auth?.cookies()
        val response: T = client.post {
            url("$API_BASE/$path?prettyPrint=false")
            contentType(ContentType.Application.Json)
            if (cookies != null) {
                val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"]
                header("Cookie", cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
                if (sapisid != null) header("Authorization", sapisidHash(sapisid, ORIGIN, System.currentTimeMillis() / 1000))
                header("X-Goog-AuthUser", "0")
            }
            setBody(body)
        }.body()
        // First writer wins: a continuation token is only valid for the session that issued it,
        // so the identity must not drift midway through paging a feed.
        if (visitorData == null && response is SessionCarrier) {
            response.responseContext?.visitorData?.let { visitorData = it }
        }
        return response
    }

    private companion object {
        const val API_BASE = "https://music.youtube.com/youtubei/v1"
        const val ORIGIN = "https://music.youtube.com"
        const val BROWSE_PATH = "browse"
        const val SEARCH_PATH = "search"
        const val NEXT_PATH = "next"
        const val PLAYER_PATH = "player"

        const val CLIENT_NAME = "WEB_REMIX"
        const val WEB_REMIX_CLIENT_ID = "67"
        const val CLIENT_VERSION = "1.20240701.01.00"

        const val HOME_BROWSE_ID = "FEmusic_home"
        const val HISTORY_BROWSE_ID = "FEmusic_history"
        const val LIBRARY_PLAYLISTS_BROWSE_ID = "FEmusic_liked_playlists"
        const val EPISODES_FOR_LATER_ID = "SE"
        const val PLAYLIST_BROWSE_PREFIX = "VL"
    }
}

/** Supplies a signed-in YouTube session's youtube.com cookies, or null while signed out. */
fun interface InnerTubeAuth {
    fun cookies(): Map<String, String>?
}

/** The `Authorization` header a signed-in web client sends: SHA-1 over time, SAPISID and origin. */
internal fun sapisidHash(sapisid: String, origin: String, epochSeconds: Long): String {
    val digest = MessageDigest.getInstance("SHA-1").digest("$epochSeconds $sapisid $origin".toByteArray())
    return "SAPISIDHASH ${epochSeconds}_${digest.joinToString("") { "%02x".format(it) }}"
}

/**
 * Language and region sent with every request.
 *
 * YouTube geolocates by IP regardless of what is asked for, so this steers result *language* and
 * regional catalogue preference rather than guaranteeing a region.
 */
data class InnerTubeLocale(
    val language: String = "en",
    val region: String = "US",
)
