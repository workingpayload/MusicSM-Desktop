package com.example.motionart.internal

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Editorial motion artwork from the Apple Music catalogue.
 *
 * This is the widest source of the four, and the only one that needs a credential. The credential
 * is the anonymous one the public web player uses, obtained the same way the page obtains it —
 * read out of the site's own script bundle at runtime, held in memory, and re-read when it lapses.
 * Nothing is stored and nothing is ours.
 */
internal class AppleProvider(
    private val client: HttpClient,
    private val storefront: String,
) : MotionArtProviderClient {

    private val cache = TtlCache<String, MotionArt>(ttlMillis = CACHE_TTL_MS)
    private val tokenLock = Mutex()

    @Volatile
    private var token: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    override suspend fun lookup(query: TrackQuery): MotionArt? {
        if (query.artist.isBlank() || query.title.isBlank()) return null
        val key = listOf(query.artist, query.title, query.album.orEmpty(), storefront)
            .joinToString("|") { it.trim().lowercase() }
        return cache.get(key) { search(query) }
    }

    private suspend fun search(query: TrackQuery): MotionArt? {
        val term = listOfNotNull(
            query.artist,
            query.album?.takeIf { it.isNotBlank() } ?: query.title,
        ).joinToString(" ")
        val items = albums(term, retryOnAuthFailure = true) ?: return null

        // Among the releases that genuinely belong to this track, take the first that actually has
        // motion. The top hit is often a single with none while the parent album further down does.
        val clip = items.asSequence()
            .filter { matches(it, query) }
            .mapNotNull { it.attributes?.editorialVideo?.best }
            .firstOrNull { !it.video.isNullOrBlank() }
            ?: return null

        return MotionArt(
            videoUrl = clip.video ?: return null,
            provider = MotionArtProvider.APPLE,
            previewImageUrl = clip.previewFrame?.url?.let(::sizedPreview),
        )
    }

    /**
     * Whether a catalogue release is the one we were asked about.
     *
     * The artist always has to agree, because a motion cover from the wrong act is worse than no
     * motion cover at all. The release name is only required when the caller actually knew it;
     * a single's parent release is frequently named nothing like the track.
     */
    private fun matches(item: AlbumItem, query: TrackQuery): Boolean {
        val attrs = item.attributes ?: return false
        if (!Matching.artistsMatch(query.artist, attrs.artistName)) return false
        if (query.album.isNullOrBlank()) return true
        return Matching.titlesMatch(query.album, attrs.name) ||
            Matching.titlesMatch(query.title, attrs.name)
    }

    /**
     * Runs the catalogue search, refreshing the credential once if it was rejected.
     *
     * The token is read from a bundle that can be redeployed at any moment, so a rejection is an
     * expected event rather than a bug: the cached value is dropped and the call retried once with
     * a freshly read one.
     */
    private suspend fun albums(term: String, retryOnAuthFailure: Boolean): List<AlbumItem>? {
        val bearer = token() ?: return null
        val response = runCatching {
            client.get("$CATALOG_URL/$storefront/search") {
                header("Authorization", "Bearer $bearer")
                header("Origin", ORIGIN)
                header("Referer", "$ORIGIN/")
                parameter("term", term)
                parameter("types", "albums")
                parameter("limit", SEARCH_LIMIT)
                parameter("extend", "editorialVideo")
            }.body<SearchResponse>()
        }
        val body = response.getOrElse {
            if (!retryOnAuthFailure) return null
            invalidateToken(bearer)
            return albums(term, retryOnAuthFailure = false)
        }
        return body.results?.albums?.data
    }

    private suspend fun token(): String? {
        val cached = token
        if (cached != null && nowSeconds() < tokenExpiresAt) return cached
        return tokenLock.withLock {
            val current = token
            if (current != null && nowSeconds() < tokenExpiresAt) return@withLock current
            val scraped = runCatching { scrapeToken() }.getOrNull()
            if (scraped != null) {
                token = scraped
                // Retire it early so a request is never sent with a credential about to lapse.
                tokenExpiresAt = (TokenScraper.expiresAt(scraped) ?: 0L) - EXPIRY_MARGIN_SECONDS
            }
            scraped
        }
    }

    /** Drops a credential the API refused, unless another caller already replaced it. */
    private suspend fun invalidateToken(rejected: String) = tokenLock.withLock {
        if (token == rejected) {
            token = null
            tokenExpiresAt = 0L
        }
    }

    private suspend fun scrapeToken(): String? {
        val html = client.get("$ORIGIN/$storefront/browse") { header("User-Agent", USER_AGENT) }.bodyAsText()
        val bundle = TokenScraper.bundlePath(html) ?: return null
        val js = client.get("$ORIGIN$bundle") { header("User-Agent", USER_AGENT) }.bodyAsText()
        return TokenScraper.extractToken(js, nowSeconds())
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    /** Preview frames are templated on size; the still only has to cover a cover-sized slot. */
    private fun sizedPreview(template: String): String =
        template.replace("{w}", PREVIEW_PX.toString()).replace("{h}", PREVIEW_PX.toString())

    internal companion object {
        const val ORIGIN = "https://music.apple.com"
        const val CATALOG_URL = "https://amp-api.music.apple.com/v1/catalog"
        const val SEARCH_LIMIT = 5
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
        const val PREVIEW_PX = 600

        /** The site serves an empty shell to clients it does not recognise as a browser. */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/127.0.0.0 Safari/537.36"

        /** Refresh this long before the real expiry so an in-flight request cannot straddle it. */
        const val EXPIRY_MARGIN_SECONDS = 300L
    }
}
