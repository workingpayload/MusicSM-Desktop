package com.example.motionart.internal

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Square cover videos from Tidal's catalogue.
 *
 * A release that has one exposes it as a bare UUID rather than a URL; the playable address is
 * assembled from that id, so the id alone is what the search has to find.
 */
internal class TidalProvider(
    private val client: HttpClient,
    private val countryCode: String,
) : MotionArtProviderClient {

    private val cache = TtlCache<String, MotionArt>(ttlMillis = CACHE_TTL_MS)

    override suspend fun lookup(query: TrackQuery): MotionArt? {
        val key = listOf(query.artist, query.title, query.album.orEmpty())
            .joinToString("|") { it.trim().lowercase() }
        return cache.get(key) { search(query) }
    }

    private suspend fun search(query: TrackQuery): MotionArt? {
        val term = listOfNotNull(
            query.album?.takeIf { it.isNotBlank() },
            query.artist,
            query.title,
        ).joinToString(" ")

        val response = providerRequest("TIDAL") {
            client.get(SEARCH_URL) {
                header("X-Tidal-Token", EMBED_TOKEN)
                parameter("query", term)
                parameter("limit", SEARCH_LIMIT)
                parameter("types", "TRACKS")
                parameter("countryCode", countryCode)
            }.body<TidalSearchResponse>()
        } ?: return null

        // A track title is far from unique — the same name comes back for covers, remixes and lofi
        // re-recordings — so the artist has to agree before a cover id is accepted.
        val cover = response.tracks?.items.orEmpty()
            .asSequence()
            .filter { Matching.artistsMatch(query.artist, it.primaryArtist) }
            .filter { Matching.titlesMatch(query.title, it.title) }
            .mapNotNull { it.album?.videoCover }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        return videoUrl(cover)?.let { MotionArt(videoUrl = it, provider = MotionArtProvider.TIDAL) }
    }

    internal companion object {
        const val SEARCH_URL = "https://api.tidal.com/v1/search"
        const val SEARCH_LIMIT = 10
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

        /**
         * The public identifier Tidal's own embeddable player presents.
         *
         * It grants nothing beyond anonymous catalogue search, which is all this provider does.
         */
        const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"

        /** Covers are published square at this edge length. */
        const val COVER_SIZE = "1280x1280"

        /**
         * Turns a cover id into its address.
         *
         * The id is a UUID and the CDN lays the files out along its five dash-separated groups, so
         * anything that is not a five-part UUID cannot be resolved and is rejected rather than
         * guessed at.
         */
        fun videoUrl(coverId: String): String? {
            val parts = coverId.split('-')
            if (parts.size != 5 || parts.any { it.isEmpty() }) return null
            return "https://resources.tidal.com/videos/${parts.joinToString("/")}/$COVER_SIZE.mp4"
        }
    }
}

@Serializable
internal data class TidalSearchResponse(val tracks: TidalTracks? = null)

@Serializable
internal data class TidalTracks(val items: List<TidalTrack> = emptyList())

@Serializable
internal data class TidalTrack(
    val title: String? = null,
    val artists: List<TidalArtist> = emptyList(),
    val artist: TidalArtist? = null,
    val album: TidalAlbum? = null,
) {
    /** Lead credit, taken from whichever of the two shapes the response used. */
    val primaryArtist: String?
        get() = artists.firstOrNull()?.name ?: artist?.name
}

@Serializable
internal data class TidalArtist(val name: String? = null)

@Serializable
internal data class TidalAlbum(
    val title: String? = null,
    @SerialName("videoCover") val videoCover: String? = null,
)
