package com.example.musicsmd.home

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
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsm.domain.source.MusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** Builds the desktop Home feed from local taste signals plus the provider's paged shelves. */
class DesktopHomeFeedBuilder(
    private val musicSource: MusicSource,
    private val library: LibraryRepository,
    private val stats: StatsRepository,
) {
    suspend fun initialFeed(): HomeFeed = coroutineScope {
        val likedAsync = async { runCatching { library.likedSongs().first() }.getOrDefault(emptyList()) }
        val recentAsync = async { runCatching { library.recentlyPlayed().first() }.getOrDefault(emptyList()) }
        val followedAsync = async { runCatching { library.likedArtists().first() }.getOrDefault(emptyList()) }
        val recentStatsAsync = async { statsOrEmpty(StatsRange.LAST_4_WEEKS) }
        val lifetimeStatsAsync = async { statsOrEmpty(StatsRange.ALL_TIME) }
        val forgottenAsync = async { runCatching { stats.forgottenFavorites(SHELF_SIZE) }.getOrDefault(emptyList()).map { it.song } }
        val providerAsync = async { runCatching { musicSource.homeFeed() }.getOrDefault(HomeFeed()) }
        val trendingAsync = async { runCatching { musicSource.trending(SHELF_SIZE * 2) }.getOrDefault(emptyList()) }

        val liked = likedAsync.await()
        val recent = recentAsync.await()
        val followed = followedAsync.await()
        val profile = TasteProfiles.build(
            recent = recentStatsAsync.await(),
            lifetime = lifetimeStatsAsync.await(),
            liked = liked,
            followedArtists = followed,
            recentlyPlayed = recent,
            seedCount = SEED_COUNT,
            rotationSize = SHELF_SIZE,
        )

        val sections = mutableListOf<HomeSection>()
        val shown = linkedSetOf<String>()
        fun addSongs(title: String, songs: List<Song>) {
            val fresh = songs.distinctBy { it.id }.filter { it.id.isNotBlank() && shown.add(it.id) }
            if (fresh.isNotEmpty()) sections += HomeSection(title, fresh.take(SHELF_SIZE).map { HomeItem.SongItem(it) })
        }

        addSongs("Recently played", recent)
        addSongs("Listen again", profile.heavyRotation)
        addSongs("Forgotten favorites", forgottenAsync.await())

        val dailyPool = (profile.heavyRotation + liked + recent).distinctBy { it.id }
        addSongs("Daily mix", DailyRotation.pick(dailyPool, SHELF_SIZE))

        val yourArtists = followed.sortedByDescending { profile.affinity(it.name) }.take(ARTIST_SHELF_SIZE)
        if (yourArtists.size >= MIN_ARTIST_SHELF) {
            sections += HomeSection("Your artists", yourArtists.map { HomeItem.ArtistItem(it) })
        }

        addSongs("Quick picks", relatedPicks(profile, shown))
        addSongs("Trending now", ShelfRanker.dedupe(trendingAsync.await(), SHELF_SIZE, shown))

        val provider = providerAsync.await()
        val generic = if (profile.hasHistory) provider.sections.take(MAX_GENERIC_SECTIONS) else provider.sections
        val merged = merge(sections, generic)
        HomeFeed(sections = merged, continuation = provider.continuation)
    }

    private suspend fun relatedPicks(profile: TasteProfile, shown: Set<String>): List<Song> = coroutineScope {
        val seeds = DailyRotation.pick(profile.discoveryPool(), DISCOVER_SEEDS)
        if (seeds.isEmpty()) return@coroutineScope emptyList()
        val related = seeds.map { seed -> async { runCatching { musicSource.relatedTo(seed.id) }.getOrDefault(emptyList()) } }.awaitAll()
        ShelfRanker.rank(
            candidates = ShelfRanker.interleave(related, SHELF_SIZE * 2),
            profile = profile,
            limit = SHELF_SIZE,
            exclude = shown + seeds.map { it.id },
            excludeKnown = profile.hasHistory,
        )
    }

    private suspend fun statsOrEmpty(range: StatsRange): ListeningStats =
        runCatching { stats.stats(range).first() }.getOrDefault(ListeningStats(range = range))

    private fun TasteProfile.discoveryPool(): List<Song> = heavyRotation.ifEmpty { seeds }

    private fun merge(first: List<HomeSection>, second: List<HomeSection>): List<HomeSection> {
        val seen = first.mapTo(mutableSetOf()) { it.title }
        return first + second.filter { it.items.isNotEmpty() && seen.add(it.title) }
    }

    private companion object {
        const val SEED_COUNT = 4
        const val SHELF_SIZE = 12
        const val DISCOVER_SEEDS = 3
        const val ARTIST_SHELF_SIZE = 12
        const val MIN_ARTIST_SHELF = 3
        const val MAX_GENERIC_SECTIONS = 5
    }
}
