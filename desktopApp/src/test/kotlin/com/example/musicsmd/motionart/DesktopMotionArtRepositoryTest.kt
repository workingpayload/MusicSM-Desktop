package com.example.musicsmd.motionart

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import com.example.musicsm.domain.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class DesktopMotionArtRepositoryTest {
    private val song = Song(id = "a", title = "Title", artist = "Artist", album = "Album")
    private val art = MotionArt("https://example.com/cover.mp4", MotionArtProvider.APPLE)

    @Test
    fun `hits and misses are cached per recording and source`() = runBlocking {
        var calls = 0
        val repository = DesktopMotionArtRepository({ _, source ->
            calls++
            art.takeIf { source == MotionArtProvider.APPLE }
        })
        assertSame(art, repository.forSong(song, MotionArtProvider.APPLE))
        assertSame(art, repository.forSong(song.copy(id = "other-video"), MotionArtProvider.APPLE))
        assertNull(repository.forSong(song, MotionArtProvider.TIDAL))
        assertNull(repository.forSong(song, MotionArtProvider.TIDAL))
        assertEquals(2, calls)
        repository.forSong(song.copy(album = "Other release"), MotionArtProvider.APPLE)
        assertEquals(3, calls)
    }

    @Test
    fun `misses expire and signed video URLs are refreshed`() = runBlocking {
        var time = 0L
        var calls = 0
        val repository = DesktopMotionArtRepository({ _, source ->
            calls++
            art.takeIf { source == MotionArtProvider.APPLE }
        }, now = { time })
        repository.forSong(song, MotionArtProvider.AUTO)
        time = 15 * 60 * 1000L
        repository.forSong(song, MotionArtProvider.AUTO)
        assertEquals(2, calls)
        repository.forSong(song, MotionArtProvider.APPLE)
        time += 60 * 60 * 1000L
        repository.forSong(song, MotionArtProvider.APPLE)
        assertEquals(4, calls)
    }

    @Test
    fun `cancellation is not cached as a missing cover`() = runBlocking {
        var cancel = true
        val repository = DesktopMotionArtRepository({ _, _ ->
            if (cancel) throw CancellationException("Cancelled")
            art
        })
        try {
            repository.forSong(song, MotionArtProvider.AUTO)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            cancel = false
        }
        assertSame(art, repository.forSong(song, MotionArtProvider.AUTO))
    }

    @Test
    fun `least recently used entries are bounded`() = runBlocking {
        var calls = 0
        val repository = DesktopMotionArtRepository({ _, _ -> calls++; art })
        repeat(256) { repository.forSong(song.copy(title = "$it"), MotionArtProvider.AUTO) }
        repository.forSong(song.copy(title = "0"), MotionArtProvider.AUTO)
        repository.forSong(song.copy(title = "new"), MotionArtProvider.AUTO)
        repository.forSong(song.copy(title = "0"), MotionArtProvider.AUTO)
        assertEquals(257, calls)
        repository.forSong(song.copy(title = "1"), MotionArtProvider.AUTO)
        assertEquals(258, calls)
    }
}
