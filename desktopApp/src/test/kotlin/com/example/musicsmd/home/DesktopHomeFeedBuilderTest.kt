package com.example.musicsmd.home

import com.example.musicsm.data.source.youtube.signin.YouTubeAccountLibrary
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.ArtistPlayCount
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.ListeningStats
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongPlayCount
import com.example.musicsm.domain.model.StatsRange
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsmd.stats.SkipSignals
import java.lang.reflect.Proxy
import java.util.Collections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopHomeFeedBuilderTest {

    private fun track(id: String, artist: String = "Artist $id") = Song(id, "Song $id", artist)

    /** 10 songs by 10 artists, played enough to count as history. */
    private val history = (1..10).map { SongPlayCount(track("h$it"), playCount = 20 - it) }

    private val followed = (1..8).map { Artist(id = "a$it", name = "Followed $it") }

    private inner class FakeMusic : MusicRepository by unimplemented() {
        val seedCalls: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())

        override suspend fun homeFeed() = HomeFeed()
        override suspend fun trending() = emptyList<Song>()
        override suspend fun relatedTo(songId: String) = (1..15).map { track("rel-$songId-$it") }
        override suspend fun recommendations(seeds: List<Song>, limit: Int): List<Song> {
            seedCalls += seeds.map { it.id }
            return listOf(track("skipped")) + (1..20).map { track("rec-${seeds.first().id}-$it", artist = "New ${seeds.first().id} $it") }
        }
        override suspend fun artist(id: String) =
            Artist(id = id, name = "Followed ${id.drop(1)}", topSongs = (1..6).map { track("$id-top$it", artist = "Followed ${id.drop(1)}") })
    }

    private val library = object : LibraryRepository by unimplemented() {
        override fun likedSongs(): Flow<List<Song>> = flowOf(emptyList())
        override fun recentlyPlayed(): Flow<List<Song>> = flowOf(emptyList())
        override fun likedArtists(): Flow<List<Artist>> = flowOf(followed)
    }

    private val stats = object : StatsRepository {
        override fun stats(range: StatsRange): Flow<ListeningStats> =
            flowOf(
                ListeningStats(
                    range = range,
                    totalPlays = 100,
                    topSongs = history,
                    topArtists = history.map { ArtistPlayCount(it.song.artist, null, it.playCount, 1, 0L) },
                ),
            )
        override suspend fun forgottenFavorites(limit: Int) = emptyList<SongPlayCount>()
        override suspend fun clear() = Unit
    }

    private fun HomeFeed.songIds(title: String): List<String> =
        sections.firstOrNull { it.title == title }?.items.orEmpty().mapNotNull { (it as? HomeItem.SongItem)?.song?.id }

    @Test
    fun buildsPersonalShelves() = runBlocking {
        val music = FakeMusic()
        val feed = DesktopHomeFeedBuilder(music, library, stats).initialFeed()
        val titles = feed.sections.map { it.title }
        assertTrue(titles.toString(), "Listen again" in titles)
        assertTrue(titles.toString(), "Daily discover" in titles)
        assertTrue(titles.toString(), "Recommended for you" in titles)
        assertTrue(titles.toString(), titles.any { it.startsWith("More like ") })
        assertTrue(titles.toString(), "Your artists" in titles)
        assertTrue(titles.toString(), "From artists you follow" in titles)
        // Nothing repeats across shelves.
        val all = feed.sections.flatMap { s -> s.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun refreshRotatesSeeds() = runBlocking {
        val first = FakeMusic()
        val second = FakeMusic()
        DesktopHomeFeedBuilder(first, library, stats).initialFeed(refresh = 0)
        DesktopHomeFeedBuilder(second, library, stats).initialFeed(refresh = 1)
        assertNotEquals(first.seedCalls.toSet(), second.seedCalls.toSet())

        // Same refresh, same feed: nothing reshuffles behind the listener's back.
        val again = FakeMusic()
        DesktopHomeFeedBuilder(again, library, stats).initialFeed(refresh = 0)
        assertEquals(first.seedCalls.toSet(), again.seedCalls.toSet())
    }

    @Test
    fun skippedSongsAndArtistsStayOutOfDiscovery() = runBlocking {
        val signals = SkipSignals(
            skippedOnce = setOf("skipped"),
            artistSkips = mapOf("followed1" to 10),
        )
        val feed = DesktopHomeFeedBuilder(FakeMusic(), library, stats) { signals }.initialFeed()
        val discovery = feed.songIds("Daily discover") + feed.songIds("Recommended for you")
        assertFalse(feed.sections.map { it.title to it.items.size }.toString(), discovery.isEmpty())
        assertFalse("skipped" in discovery)
        // "Followed 1" keeps getting skipped and is barely played: none of their songs anywhere.
        val builder = DesktopHomeFeedBuilder(FakeMusic(), library, stats) { signals }
        val first = builder.initialFeed()
        val songIds = first.sections.flatMap { s -> s.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }.toMutableList()
        var cursor = first.continuation
        while (cursor != null) {
            val more = builder.moreShelves(cursor, emptySet())
            songIds += more.sections.flatMap { s -> s.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }
            cursor = more.continuation
        }
        assertFalse(songIds.any { it.startsWith("a1-") })
        assertTrue(songIds.any { it.startsWith("a2-") })
    }

    @Test
    fun pagingFallsBackToFollowedArtists() = runBlocking {
        val builder = DesktopHomeFeedBuilder(FakeMusic(), library, stats)
        val feed = builder.initialFeed()
        assertEquals(DesktopHomeFeedBuilder.ARTIST_CURSOR, feed.continuation)
        var cursor = feed.continuation
        val titles = mutableListOf<String>()
        while (cursor != null) {
            val more = builder.moreShelves(cursor, emptySet())
            titles += more.sections.map { it.title }
            cursor = more.continuation
        }
        // 8 followed, 4 already featured on the first page: the other 4 get shelves.
        assertEquals(4, titles.size)
        assertTrue(titles.all { it.startsWith("More from Followed ") })
        assertNull(cursor)
    }

    private class FakeAccount(override val isAvailable: Boolean = true) : YouTubeAccountLibrary {
        val continuations = mutableListOf<String>()
        override suspend fun accountHome() = HomeFeed(
            sections = listOf(
                HomeSection("Quick picks", (1..5).map { HomeItem.SongItem(Song("yt$it", "YT $it", "YT Artist $it")) }),
                // Same title as a local shelf, and a song the local shelves would also show.
                HomeSection("Listen again", listOf(HomeItem.SongItem(Song("h1", "Song h1", "Artist h1")))),
            ),
            continuation = "page2",
        )
        override suspend fun moreAccountHome(continuation: String): HomeFeed {
            continuations += continuation
            return HomeFeed(listOf(HomeSection("Mixed for you", listOf(HomeItem.SongItem(Song("mix", "Mix", "M"))))))
        }
        override suspend fun accountHistory(limit: Int) = (1..3).map { Song("hist$it", "Hist $it", "H $it") }
        override suspend fun accountPlaylists() = emptyList<Playlist>()
    }

    @Test
    fun accountShelvesLeadAndLocalShelvesSkipTheirSongs() = runBlocking {
        val account = FakeAccount()
        val builder = DesktopHomeFeedBuilder(FakeMusic(), library, stats, account = account)
        val feed = builder.initialFeed()
        val titles = feed.sections.map { it.title }
        assertEquals(listOf("Quick picks", "Listen again", "Recently played on YouTube"), titles.take(3))
        assertEquals(1, titles.count { it == "Listen again" })
        assertTrue(titles.toString(), "Recommended for you" in titles)
        val all = feed.sections.flatMap { s -> s.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }
        assertEquals(all.size, all.toSet().size)

        // The account's feed continues through the account, then the followed artists.
        val more = builder.moreShelves(feed.continuation!!, emptySet())
        assertEquals(listOf("page2"), account.continuations)
        assertEquals(listOf("Mixed for you"), more.sections.map { it.title })
        assertEquals(DesktopHomeFeedBuilder.ARTIST_CURSOR, more.continuation)
    }

    @Test
    fun unavailableAccountLeavesHomeAsBefore() = runBlocking {
        val feed = DesktopHomeFeedBuilder(FakeMusic(), library, stats, account = FakeAccount(isAvailable = false)).initialFeed()
        val titles = feed.sections.map { it.title }
        assertFalse("Quick picks" in titles)
        assertFalse("Recently played on YouTube" in titles)
        assertEquals(DesktopHomeFeedBuilder.ARTIST_CURSOR, feed.continuation)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> unimplemented(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as T
}
