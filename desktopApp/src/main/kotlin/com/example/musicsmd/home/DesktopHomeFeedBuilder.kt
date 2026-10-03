package com.example.musicsmd.home

import com.example.musicsm.data.source.youtube.signin.YouTubeAccountLibrary
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.recommend.DailyRotation
import com.example.musicsm.domain.recommend.ShelfRanker
import com.example.musicsm.domain.recommend.TasteProfile
import com.example.musicsm.domain.recommend.TasteProfiles
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsmd.stats.SkipSignals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Builds the desktop Home feed: the signed-in YouTube account's own shelves and history first
 * (when there is one and it's allowed), then shelves from the listener's own history, then
 * personalized catalog shelves (ported from mobile's HomeViewModel), then the provider's generic
 * feed (left out when the account's home stands in for it).
 *
 * Everything is deterministic for a given day and [refresh] count: the feed stays put while you
 * look at it, and each manual refresh rotates which seeds, artists and slices are used, so it
 * brings something new without being random. Recent skips ([SkipSignals]) keep songs and artists
 * the listener keeps moving on from out of the discovery shelves.
 */
class DesktopHomeFeedBuilder(
    private val music: MusicRepository,
    private val library: LibraryRepository,
    private val stats: StatsRepository,
    /** The signed-in YouTube account, whose personal shelves lead Home when it is available. */
    private val account: YouTubeAccountLibrary? = null,
    private val skips: () -> SkipSignals = { SkipSignals() },
) {
    /** Followed artists not yet given a shelf, offered as the page scrolls past the provider's feed. */
    @Volatile
    private var pendingArtists: List<Artist> = emptyList()

    @Volatile
    private var avoid: Avoid = Avoid(SkipSignals(), TasteProfile())

    suspend fun initialFeed(refresh: Int = 0): HomeFeed = coroutineScope {
        val key = DailyRotation.today() + refresh
        val likedAsync = async { runCatching { library.likedSongs().first() }.getOrDefault(emptyList()) }
        val recentAsync = async { runCatching { library.recentlyPlayed().first() }.getOrDefault(emptyList()) }
        val followedAsync = async { runCatching { library.likedArtists().first() }.getOrDefault(emptyList()) }
        val recentStatsAsync = async { statsOrEmpty(StatsRange.LAST_4_WEEKS) }
        val lifetimeStatsAsync = async { statsOrEmpty(StatsRange.ALL_TIME) }
        val forgottenAsync = async { runCatching { stats.forgottenFavorites(SHELF_SIZE) }.getOrDefault(emptyList()).map { it.song } }
        val providerAsync = async { runCatching { music.homeFeed() }.getOrDefault(HomeFeed()) }
        val trendingAsync = async { runCatching { music.trending() }.getOrDefault(emptyList()) }
        val acct = account?.takeIf { it.isAvailable }
        val accountHomeAsync = async { acct?.let { runCatching { it.accountHome() }.getOrNull() } ?: HomeFeed() }
        val accountHistoryAsync = async {
            acct?.let { runCatching { it.accountHistory(ACCOUNT_HISTORY_POOL) }.getOrNull() }.orEmpty()
        }

        val liked = likedAsync.await()
        val recent = recentAsync.await()
        val youTubeHistory = accountHistoryAsync.await()
        val profile = TasteProfiles.build(
            recent = recentStatsAsync.await(),
            lifetime = lifetimeStatsAsync.await(),
            liked = liked,
            followedArtists = followedAsync.await(),
            // YouTube history seeds recommendations too, behind what was played here.
            recentlyPlayed = (recent + youTubeHistory).distinctBy { it.id },
            seedCount = SEED_POOL,
            rotationSize = SHELF_SIZE * 2,
        )
        val avoid = Avoid(skips(), profile).also { this@DesktopHomeFeedBuilder.avoid = it }
        val followed = followedAsync.await().sortedByDescending { profile.affinity(it.name) }

        // Each refresh moves these windows along; within one refresh they're fixed.
        val seeds = DailyRotation.pick(profile.seeds.filterNot(avoid::song), SEED_COUNT, key)
        val discoverSeeds = DailyRotation.pick(profile.discoveryPool().filterNot(avoid::song), DISCOVER_SEEDS, key + 1)
        val topSeed = seeds.firstOrNull()
        val artistPicks = DailyRotation.pick(followed.take(ARTIST_POOL), SEED_COUNT, key)

        val discoverAsync = async { recommendations(discoverSeeds) }
        val recommendedAsync = async { recommendations(seeds) }
        val relatedAsync = async {
            topSeed?.let { seed -> runCatching { music.relatedTo(seed.id) }.getOrDefault(emptyList()) }.orEmpty()
        }
        val artistPagesAsync = async {
            artistPicks.map { artist -> async { runCatching { music.artist(artist.id) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }

        val sections = mutableListOf<HomeSection>()
        val shown = linkedSetOf<String>()
        fun addSongs(title: String, songs: List<Song>, min: Int = 1) {
            // A shelf the account's home already has by this name keeps YouTube's version.
            if (sections.any { it.title == title }) return
            val fresh = songs.distinctBy { it.id }.filter { it.id.isNotBlank() && it.id !in shown }.take(SHELF_SIZE)
            if (fresh.size < min) return
            shown += fresh.map { it.id }
            sections += HomeSection(title, fresh.map { HomeItem.SongItem(it) })
        }
        fun discovery(candidates: List<Song>, exclude: Set<String> = emptySet(), maxPerArtist: Int = ShelfRanker.DEFAULT_MAX_PER_ARTIST) =
            ShelfRanker.rank(
                candidates.filterNot { avoid.song(it, discovery = true) },
                profile,
                SHELF_SIZE,
                exclude = shown + exclude,
                excludeKnown = profile.hasHistory,
                maxPerArtist = maxPerArtist,
            )

        // The account's own shelves lead; the local ones below skip songs those already show.
        val accountHome = accountHomeAsync.await()
        accountHome.sections.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            sections += section
            section.items.filterIsInstance<HomeItem.SongItem>().forEach { shown += it.song.id }
        }
        addSongs("Recently played on YouTube", youTubeHistory.filterNot { avoid.song(it) })

        addSongs("Recently played", recent)
        addSongs("Listen again", DailyRotation.pick(profile.heavyRotation.filterNot { avoid.song(it) }, SHELF_SIZE, key))
        addSongs("Daily discover", discovery(discoverAsync.await()), min = MIN_SONG_SHELF)
        addSongs("Recommended for you", discovery(recommendedAsync.await()), min = MIN_SONG_SHELF)
        topSeed?.let { seed ->
            val title = if (profile.hasHistory) "More like ${seed.title}" else "Because you liked ${seed.title}"
            addSongs(title, discovery(relatedAsync.await(), exclude = setOf(seed.id)), min = MIN_SONG_SHELF)
        }

        if (followed.size >= MIN_ARTIST_SHELF) {
            sections += HomeSection("Your artists", followed.take(ARTIST_SHELF_SIZE).map { HomeItem.ArtistItem(it) })
        }
        val artistPages = artistPagesAsync.await()
        addSongs(
            "From artists you follow",
            ShelfRanker.rank(
                artistPages.flatMap { it.topSongs.take(ARTIST_PICKS) }.filterNot { avoid.song(it) },
                profile,
                SHELF_SIZE,
                exclude = shown,
                maxPerArtist = ARTIST_PICKS,
            ),
            min = MIN_SONG_SHELF,
        )

        addSongs("Forgotten favorites", forgottenAsync.await().filterNot { avoid.song(it) })
        val dailyPool = (profile.heavyRotation + liked + recent).distinctBy { it.id }.filterNot { avoid.song(it) }
        addSongs("Daily mix", DailyRotation.pick(dailyPool, SHELF_SIZE, key))

        val albums = artistPages.flatMap { it.albums }
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }
            .take(SHELF_SIZE)
        if (albums.size >= MIN_ALBUM_SHELF) {
            sections += HomeSection("Albums from your artists", albums.map { HomeItem.AlbumItem(it) })
        }

        addSongs("Trending now", ShelfRanker.dedupe(trendingAsync.await(), SHELF_SIZE, shown))

        // Artists already given a shelf here aren't offered again while paging.
        pendingArtists = followed.filter { artist -> artistPicks.none { it.id == artist.id } }

        val provider = providerAsync.await()
        // Signed in, the account's home stands in for the anonymous editorial feed; its pages
        // continue through the account.
        if (accountHome.sections.isNotEmpty()) {
            return@coroutineScope HomeFeed(
                sections = sections,
                continuation = accountHome.continuation?.let { ACCOUNT_CURSOR_PREFIX + it }
                    ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() },
            )
        }
        val generic = if (profile.hasHistory) provider.sections.take(MAX_GENERIC_SECTIONS) else provider.sections
        HomeFeed(
            sections = merge(sections, generic),
            continuation = provider.continuation ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() },
        )
    }

    /**
     * The next shelves as the page scrolls: the provider's own feed while it has more, then a
     * "More from" shelf per remaining followed artist, a few at a time.
     */
    suspend fun moreShelves(continuation: String, shownSongIds: Set<String>): HomeFeed {
        if (continuation.startsWith(ACCOUNT_CURSOR_PREFIX)) {
            val more = account?.let { runCatching { it.moreAccountHome(continuation.removePrefix(ACCOUNT_CURSOR_PREFIX)) }.getOrNull() }
            if (more != null && more.sections.isNotEmpty()) {
                val next = more.continuation?.let { ACCOUNT_CURSOR_PREFIX + it } ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() }
                return HomeFeed(more.sections, next)
            }
            return artistShelves(shownSongIds.toMutableSet())
        }
        if (continuation != ARTIST_CURSOR) {
            val more = runCatching { music.moreHomeShelves(continuation) }.getOrNull()
            if (more != null && more.sections.isNotEmpty()) {
                return HomeFeed(more.sections, more.continuation ?: ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() })
            }
        }
        return artistShelves(shownSongIds.toMutableSet())
    }

    private suspend fun artistShelves(shown: MutableSet<String>): HomeFeed = coroutineScope {
        val batch = pendingArtists.take(ARTIST_PAGE_SIZE)
        pendingArtists = pendingArtists.drop(batch.size)
        val avoid = avoid
        val sections = batch
            .map { artist -> async { runCatching { music.artist(artist.id) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
            .mapNotNull { page ->
                val songs = page.topSongs.filter { it.id.isNotBlank() && it.id !in shown && !avoid.song(it) }.take(SHELF_SIZE)
                if (songs.size < MIN_SONG_SHELF) return@mapNotNull null
                shown += songs.map { it.id }
                HomeSection("More from ${page.name}", songs.map { HomeItem.SongItem(it) })
            }
        HomeFeed(sections, ARTIST_CURSOR.takeIf { pendingArtists.isNotEmpty() })
    }

    private suspend fun recommendations(seeds: List<Song>): List<Song> =
        if (seeds.isEmpty()) emptyList() else runCatching { music.recommendations(seeds, SHELF_SIZE * 2) }.getOrDefault(emptyList())

    private suspend fun statsOrEmpty(range: StatsRange): ListeningStats =
        runCatching { stats.stats(range).first() }.getOrDefault(ListeningStats(range = range))

    private fun TasteProfile.discoveryPool(): List<Song> = heavyRotation.ifEmpty { seeds }

    private fun merge(first: List<HomeSection>, second: List<HomeSection>): List<HomeSection> {
        val seen = first.mapTo(mutableSetOf()) { it.title }
        return first + second.filter { it.items.isNotEmpty() && seen.add(it.title) }
    }

    /** What recent skips rule out. */
    internal class Avoid(private val signals: SkipSignals, private val profile: TasteProfile) {
        /**
         * A song skipped repeatedly, or once when it would be offered as something new, or by an
         * artist the listener keeps skipping and doesn't otherwise play much.
         */
        fun song(song: Song, discovery: Boolean): Boolean {
            val skipped = if (discovery) signals.skippedOnce else signals.skippedRepeatedly
            return song.id in skipped || artist(song.artist)
        }

        fun song(song: Song): Boolean = song(song, discovery = false)

        private fun artist(credit: String): Boolean =
            signals.artistSkipCount(credit) >= AVOID_ARTIST_SKIPS && profile.affinity(credit) < AVOID_ARTIST_MAX_AFFINITY
    }

    companion object {
        /** Continuation meaning "the provider is done; next come followed-artist shelves". */
        const val ARTIST_CURSOR = "musicsm:followed-artists"

        /** Marks a continuation of the signed-in account's home feed. */
        const val ACCOUNT_CURSOR_PREFIX = "musicsm:account:"

        /** YouTube history songs read for the shelf and for recommendation seeds. */
        private const val ACCOUNT_HISTORY_POOL = 50

        private const val SEED_POOL = 8
        private const val SEED_COUNT = 4
        private const val SHELF_SIZE = 12
        private const val DISCOVER_SEEDS = 3
        private const val ARTIST_POOL = 12
        private const val ARTIST_PICKS = 4
        private const val ARTIST_SHELF_SIZE = 12
        private const val ARTIST_PAGE_SIZE = 3
        private const val MIN_ARTIST_SHELF = 3
        private const val MIN_ALBUM_SHELF = 3
        private const val MIN_SONG_SHELF = 4
        private const val MAX_GENERIC_SECTIONS = 5

        internal const val AVOID_ARTIST_SKIPS = 4
        internal const val AVOID_ARTIST_MAX_AFFINITY = 0.25
    }
}
