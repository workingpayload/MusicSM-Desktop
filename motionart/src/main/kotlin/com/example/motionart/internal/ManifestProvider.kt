package com.example.motionart.internal

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/**
 * A community-maintained list mapping tracks to looping videos.
 *
 * These are published as one flat document rather than as a searchable API, so the whole thing is
 * fetched once and then answered from memory. That makes a lookup free after the first one, but it
 * also means the document has to be refreshed periodically or contributors' additions would never
 * appear; [ttlMillis] is how long a copy is trusted.
 */
internal class ManifestProvider(
    private val client: HttpClient,
    private val url: String,
    private val provider: MotionArtProvider,
    ttlMillis: Long,
) : MotionArtProviderClient {

    private val cache = TtlCache<Unit, List<ManifestEntry>>(ttlMillis = ttlMillis, maxEntries = 1)

    override suspend fun lookup(query: TrackQuery): MotionArt? {
        if (query.artist.isBlank() || query.title.isBlank()) return null
        val entries = cache.get(Unit) { fetch() } ?: return null

        val hit = entries.firstOrNull { entry ->
            Matching.titlesMatch(query.title, entry.song) &&
                Matching.artistsMatch(query.artist, entry.artist)
        } ?: return null

        return MotionArt(videoUrl = hit.url, provider = provider)
    }

    /**
     * Reads the document.
     *
     * A failure returns null rather than an empty list so the miss is not mistaken for a manifest
     * that genuinely contains nothing — an empty list would be cached as a valid answer and the
     * document would not be retried until the next expiry.
     */
    private suspend fun fetch(): List<ManifestEntry>? = runCatching {
        client.get(url).body<Manifest>().items.filter { it.url.isNotBlank() }
    }.getOrNull()

    internal companion object {
        const val VIVI_URL = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"

        /** These lists change by hand and rarely; re-reading them often would be pure noise. */
        const val MANIFEST_TTL_MS = 30L * 60 * 1000
    }
}

@Serializable
internal data class Manifest(val items: List<ManifestEntry> = emptyList())

@Serializable
internal data class ManifestEntry(
    val song: String = "",
    val artist: String = "",
    val url: String = "",
    val album: String? = null,
)
