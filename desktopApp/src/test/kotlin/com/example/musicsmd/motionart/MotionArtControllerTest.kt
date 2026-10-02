package com.example.musicsmd.motionart

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.PlaybackUiState
import com.example.musicsmd.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class MotionArtControllerTest {
    private val dir = Files.createTempDirectory("motion-art-controller").toFile()
    private val settings = SettingsStore(File(dir, "settings.json"))
    private val song = Song("a", "Title", "Artist")
    private val playback = MutableStateFlow(PlaybackUiState(currentSong = song))
    private val calls = AtomicInteger()
    private var controller: MotionArtController? = null

    @After
    fun tearDown() {
        controller?.dispose()
        dir.deleteRecursively()
    }

    private fun start(repository: DesktopMotionArtRepository = DesktopMotionArtRepository({ _, source ->
        calls.incrementAndGet()
        MotionArt("https://example.com/${source.name}.mp4", source)
    })): MotionArtController = MotionArtController(playback, settings, repository).also { controller = it }

    @Test
    fun `hidden player does not look up art and closing clears it`() {
        val art = start()
        Thread.sleep(100)
        assertEquals(0, calls.get())
        art.setVisible(true)
        eventually { art.state.value.art != null }
        playback.value = playback.value.copy(isPlaying = true)
        Thread.sleep(100)
        assertEquals(1, calls.get())
        art.setVisible(false)
        eventually { art.state.value.art == null }
        art.setVisible(true)
        eventually { art.state.value.art != null }
        assertEquals(1, calls.get())
    }

    @Test
    fun `source changes reload while style changes do not`() {
        val art = start()
        art.setVisible(true)
        eventually { art.state.value.art != null }
        settings.update { it.copy(animatedArtworkStyle = "CARD") }
        Thread.sleep(100)
        assertEquals(1, calls.get())
        settings.update { it.copy(animatedArtworkSource = "TIDAL") }
        eventually { art.state.value.art?.provider == MotionArtProvider.TIDAL }
        assertEquals(2, calls.get())
        settings.update { it.copy(animatedArtwork = false) }
        eventually { art.state.value.art == null }
        playback.value = PlaybackUiState(currentSong = song.copy(id = "b", title = "Other"))
        Thread.sleep(100)
        assertEquals(2, calls.get())
    }

    @Test
    fun `track switches cancel old lookups without publishing stale art`() {
        val began = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val art = start(DesktopMotionArtRepository({ track, source ->
            if (track.id == "a") {
                began.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            MotionArt("https://example.com/${track.id}.mp4", source)
        }))
        art.setVisible(true)
        eventually { began.isCompleted }
        playback.value = PlaybackUiState(currentSong = song.copy(id = "b", title = "Other"))
        eventually { art.state.value.songId == "b" && art.state.value.art != null }
        assertEquals(true, cancelled.isCompleted)
        assertEquals("https://example.com/b.mp4", art.state.value.art?.videoUrl)
    }

    @Test
    fun `lookup failure leaves still artwork and later tracks can load`() {
        val failed = CompletableDeferred<Unit>()
        val art = start(DesktopMotionArtRepository({ track, source ->
            if (track.id == "a") {
                failed.complete(Unit)
                throw java.io.IOException("Offline")
            }
            MotionArt("https://example.com/b.mp4", source)
        }))
        art.setVisible(true)
        eventually { failed.isCompleted }
        assertNull(art.state.value.art)
        playback.value = PlaybackUiState(currentSong = song.copy(id = "b", title = "Other"))
        eventually { art.state.value.art != null }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Animated artwork condition timed out")
            Thread.sleep(10)
        }
    }
}
