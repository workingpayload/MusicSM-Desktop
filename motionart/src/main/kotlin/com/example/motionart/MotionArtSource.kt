package com.example.motionart

import com.example.motionart.internal.AppleProvider
import com.example.motionart.internal.ManifestProvider
import com.example.motionart.internal.MotionArtProviderClient
import com.example.motionart.internal.TidalProvider
import com.example.motionart.internal.TrackQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.motionart.internal.providerRequest

/**
 * Finds the short looping video some releases ship alongside their cover art.
 *
 * No single catalogue animates everything, and the ones that do animate overlap only partly, so a
 * lookup walks several sources rather than trusting one. They are tried in turn and the first
 * answer wins: the common case costs a single request, and the cost only grows for the tracks
 * nobody has animated, which is exactly where the extra effort is worth spending.
 *
 * A miss is normal and returns null. Transport failures are logged before trying another source;
 * the listener keeps seeing the still cover. Cancellation propagates so closing the player stops
 * outstanding requests.
 */
class MotionArtSource(
    private val locale: Locale = Locale.getDefault(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    /** Two-letter region, which decides which catalogue rows a provider will even return. */
    private val region: String =
        locale.country.takeIf { it.length == 2 } ?: DEFAULT_REGION

    private val clients: Map<MotionArtProvider, MotionArtProviderClient> = mapOf(
        MotionArtProvider.APPLE to AppleProvider(client, storefront = region.lowercase(Locale.ROOT)),
        MotionArtProvider.TIDAL to TidalProvider(client, countryCode = region.uppercase(Locale.ROOT)),
        MotionArtProvider.VIVI to ManifestProvider(
            client = client,
            url = ManifestProvider.VIVI_URL,
            provider = MotionArtProvider.VIVI,
            ttlMillis = ManifestProvider.MANIFEST_TTL_MS,
        ),
    )

    /**
     * The motion cover for a track, or null when no source has one.
     *
     * [album] is optional but makes the match far more reliable: with it a release can be
     * identified outright, and without it the best available evidence is the artist and the track
     * name, which is why the artist still has to agree before anything is returned.
     *
     * [preferred] pins the search to one source; the default walks them all.
     */
    suspend fun lookup(
        artist: String,
        title: String,
        album: String? = null,
        preferred: MotionArtProvider = MotionArtProvider.AUTO,
    ): MotionArt? {
        if (artist.isBlank() || title.isBlank()) return null
        val query = TrackQuery(artist = artist, title = title, album = album)
        val order = if (preferred == MotionArtProvider.AUTO) {
            MotionArtProvider.AUTO_ORDER
        } else {
            listOf(preferred)
        }
        for (provider in order) {
            currentCoroutineContext().ensureActive()
            val art = providerRequest(provider.name) { clients[provider]?.lookup(query) }
            if (art != null) return art
        }
        return null
    }

    fun close() = client.close()

    private companion object {
        const val DEFAULT_REGION = "US"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 25_000L
    }
}
