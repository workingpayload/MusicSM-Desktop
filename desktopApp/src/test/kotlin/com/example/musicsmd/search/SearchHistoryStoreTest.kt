package com.example.musicsmd.search

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Song
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryStoreTest {

    private val dir: File = Files.createTempDirectory("search-history").toFile()

    private fun store() = SearchHistoryStore(file = File(dir, "search_history.json"))

    private val song = Song(id = "s1", title = "Janice STFU", artist = "Drake", album = "ICEMAN", artworkUrl = "https://a/s1", durationMs = 180_000)
    private val album = Album(id = "MPREb_1", title = "ICEMAN", artist = "Drake", artworkUrl = "https://a/al")
    private val artist = Artist(id = "UC1", name = "Drake", artworkUrl = "https://a/ar", subscribers = "33M")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `the latest pick leads and a repeat pick moves to the front instead of duplicating`() {
        val store = store()
        store.addItem(RecentSearchItem.of(song))
        store.addItem(RecentSearchItem.of(album))
        store.addItem(RecentSearchItem.of(song))

        assertEquals(listOf("SONG:s1", "ALBUM:MPREb_1"), store.items.value.map { it.key })
    }

    @Test
    fun `a song and an album with the same id are different entries`() {
        val store = store()
        store.addItem(RecentSearchItem.of(song.copy(id = "x")))
        store.addItem(RecentSearchItem.of(album.copy(id = "x")))

        assertEquals(2, store.items.value.size)
    }

    @Test
    fun `picks survive a restart and turn back into what was opened`() {
        store().apply {
            addItem(RecentSearchItem.of(artist))
            addItem(RecentSearchItem.of(album))
            addItem(RecentSearchItem.of(song))
        }

        val items = store().items.value
        assertEquals(song, items[0].toSong())
        assertEquals(album, items[1].toAlbum())
        assertEquals(artist, items[2].toArtist())
    }

    @Test
    fun `the shelf keeps only the most recent twenty`() {
        val store = store()
        repeat(25) { store.addItem(RecentSearchItem.of(song.copy(id = "s$it"))) }

        assertEquals(20, store.items.value.size)
        assertEquals("SONG:s24", store.items.value.first().key)
    }

    @Test
    fun `removing one pick leaves the rest and clearing empties queries and picks`() {
        val store = store()
        store.add("drake")
        store.addItem(RecentSearchItem.of(song))
        store.addItem(RecentSearchItem.of(album))

        store.removeItem(RecentSearchItem.of(song))
        assertEquals(listOf("ALBUM:MPREb_1"), store.items.value.map { it.key })

        store.clear()
        assertTrue(store.items.value.isEmpty())
        assertTrue(store.history.value.isEmpty())
        assertTrue(store().items.value.isEmpty())
    }
}
