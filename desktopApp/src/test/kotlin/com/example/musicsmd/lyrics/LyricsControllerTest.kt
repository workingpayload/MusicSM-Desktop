package com.example.musicsmd.lyrics

import com.example.musicsm.domain.model.LyricLine
import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsmd.player.PlaybackUiState
import com.example.musicsmd.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LyricsControllerTest {

    private val dir: File = Files.createTempDirectory("lyrics-controller").toFile()
    private val settings = SettingsStore(File(dir, "settings.json"))

    private val withLyrics = Song(id = "a", title = "A", artist = "X")
    private val alsoWithLyrics = Song(id = "b", title = "B", artist = "X")
    private val withoutLyrics = Song(id = "c", title = "C", artist = "X")

    private val repository = object : LyricsRepository {
        override suspend fun forSong(song: Song): Lyrics? =
            if (song.id == withoutLyrics.id) null else Lyrics(synced = true, lines = listOf(LyricLine(0L, song.title)))
    }

    private val playback = MutableStateFlow(PlaybackUiState())
    private var controller: LyricsController? = null

    private fun start(song: Song): LyricsController =
        LyricsController(playback, repository, settings).also {
            controller = it
            play(song)
        }

    private fun play(song: Song) {
        playback.value = PlaybackUiState(currentSong = song)
    }

    @After
    fun tearDown() {
        controller?.dispose()
        dir.deleteRecursively()
    }

    @Test
    fun `the panel opens on its own when the song has lyrics`() {
        val lyrics = start(withLyrics)
        eventually { lyrics.visible.value }
    }

    @Test
    fun `closing it keeps it closed for that song but the next song with lyrics opens it again`() {
        val lyrics = start(withLyrics)
        eventually { lyrics.visible.value }

        lyrics.setVisible(false)
        settle(lyrics, withLyrics)
        assertFalse(lyrics.visible.value)

        play(alsoWithLyrics)
        eventually { lyrics.visible.value }
    }

    @Test
    fun `a panel it opened itself closes again for a song without lyrics`() {
        val lyrics = start(withLyrics)
        eventually { lyrics.visible.value }

        play(withoutLyrics)
        eventually { !lyrics.visible.value }
    }

    @Test
    fun `a panel the user opened stays open for a song without lyrics`() {
        val lyrics = start(withoutLyrics)
        settle(lyrics, withoutLyrics)
        lyrics.setVisible(true)

        play(withoutLyrics.copy(id = "d"))
        settle(lyrics, withoutLyrics.copy(id = "d"))
        assertTrue(lyrics.visible.value)
    }

    @Test
    fun `nothing opens on its own when the setting is off`() {
        settings.update { it.copy(autoOpenLyrics = false) }
        val lyrics = start(withLyrics)
        settle(lyrics, withLyrics)
        assertFalse(lyrics.visible.value)
    }

    /** Waits until [song]'s lookup has finished, and a moment more for the panel to react to it. */
    private fun settle(lyrics: LyricsController, song: Song) {
        eventually {
            when (val state = lyrics.state.value) {
                is LyricsUiState.Loaded -> state.songId == song.id
                LyricsUiState.NotFound, is LyricsUiState.Error -> playback.value.currentSong?.id == song.id
                else -> false
            }
        }
        Thread.sleep(SETTLE_MS)
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("condition not met within $TIMEOUT_MS ms")
            Thread.sleep(10)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
        const val SETTLE_MS = 150L
    }
}
