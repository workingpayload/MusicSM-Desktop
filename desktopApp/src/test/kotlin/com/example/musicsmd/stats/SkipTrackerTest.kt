package com.example.musicsmd.stats

import com.example.musicsm.domain.model.Song
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipTrackerTest {
    private val dir: File = Files.createTempDirectory("skips").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000_000_000_000L
    private val minute = 60_000L

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun tracker() = SkipTracker(scope, File(dir, "skips.json")) { clock }

    private fun song(id: String, artist: String = "Artist $id") = Song(id, "Song $id", artist, durationMs = 4 * minute)

    private fun SkipTracker.awaitSignals(check: (SkipSignals) -> Boolean): SkipSignals = runBlocking {
        withTimeout(5_000) {
            while (!check(signals())) delay(10)
            signals()
        }
    }

    @Test
    fun whatCountsAsASkip() {
        assertTrue(SkipTracker.isSkip(listenedMs = 5_000, durationMs = 4 * minute))
        assertTrue(SkipTracker.isSkip(listenedMs = 5_000, durationMs = 0))
        assertFalse("barely started: a change of plan", SkipTracker.isSkip(listenedMs = 500, durationMs = 4 * minute))
        assertFalse("heard a good part", SkipTracker.isSkip(listenedMs = 45_000, durationMs = 4 * minute))
        assertFalse("a short clip that was mostly heard", SkipTracker.isSkip(listenedMs = 25_000, durationMs = 50_000))
        assertTrue(SkipTracker.isFullListen(listenedMs = 3 * minute, durationMs = 4 * minute))
    }

    @Test
    fun countsSkipsPerSongAndArtistAndPersists() {
        val tracker = tracker()
        tracker.onSongFinished(song("a", "Band"), 5_000, 4 * minute)
        tracker.awaitSignals { "a" in it.skippedOnce }
        tracker.onSongFinished(song("a", "Band"), 8_000, 4 * minute)
        tracker.onSongFinished(song("b", "Band & Friend"), 8_000, 4 * minute)
        val signals = tracker.awaitSignals { "a" in it.skippedRepeatedly && "b" in it.skippedOnce }
        assertEquals(setOf("a"), signals.skippedRepeatedly)
        assertEquals(3, signals.artistSkipCount("Band"))

        // Saved in the background: wait for a fresh tracker to read it back.
        val reloaded = runBlocking {
            withTimeout(5_000) {
                while (tracker().signals().skippedOnce != setOf("a", "b")) delay(10)
                tracker().signals()
            }
        }
        assertEquals(setOf("a", "b"), reloaded.skippedOnce)
    }

    @Test
    fun aFullListenForgivesAndOldSkipsExpire() {
        val tracker = tracker()
        tracker.onSongFinished(song("a"), 5_000, 4 * minute)
        tracker.onSongFinished(song("b"), 5_000, 4 * minute)
        tracker.awaitSignals { it.skippedOnce == setOf("a", "b") }
        tracker.onSongFinished(song("a"), 4 * minute, 4 * minute)
        tracker.awaitSignals { it.skippedOnce == setOf("b") }

        clock += (SkipTracker.WINDOW_DAYS + 1) * 24 * 60 * minute
        assertTrue(tracker.signals().skippedOnce.isEmpty())
    }
}
