package com.example.musicsmd.motionart

import com.example.motionart.MotionArtSource
import com.sun.jna.NativeLibrary
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in smoke tests against the actual bundled VLC, using developer-supplied local fixtures. */
class MotionArtworkNativeTest {
    @Test
    fun `live catalog lookup produces a playable animated cover`() {
        assumeTrue("Set MUSICSM_TEST_REMOTE_ART=true for the live catalog test", System.getenv("MUSICSM_TEST_REMOTE_ART") == "true")
        val resources = checkNotNull(System.getenv("MUSICSM_TEST_VLC_RESOURCES"))
        System.setProperty("compose.application.resources.dir", resources)
        runBlocking {
            val source = MotionArtSource()
            try {
                val art = withTimeout(90_000L) {
                    source.lookup("The Weeknd", "Blinding Lights", "After Hours")
                }
                requireNotNull(art) { "No provider returned the known test release's animated cover" }
                val player = MotionArtworkPlayer.open()
                val sizes = ConcurrentHashMap.newKeySet<Pair<Int, Int>>()
                val observe = launch(Dispatchers.Default) {
                    player.frame.filterNotNull().collect { sizes.add(it.width to it.height) }
                }
                try {
                    player.start(art.videoUrl, playing = true)
                    withTimeout(30_000L) { while (player.frame.value == null) delay(50) }
                    // Adaptive streams used to open on a low rendition and switch up mid-loop,
                    // rebuilding the video output and visibly glitching.
                    delay(12_000L)
                    println("Animated artwork ${art.provider} sizes: $sizes")
                    assertEquals("Animated artwork must not switch quality mid-playback: $sizes", 1, sizes.size)
                    val (width, height) = sizes.single()
                    assertTrue(width in 1..1280 && height in 1..1280)
                } finally {
                    observe.cancel()
                    player.release()
                }
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun `bundled VLC decodes loops pauses and resumes MP4`() = verifyVideo("MUSICSM_TEST_MP4")

    @Test
    fun `bundled VLC decodes loops pauses and resumes HLS`() = verifyVideo("MUSICSM_TEST_HLS")

    private fun verifyVideo(fixtureVariable: String) {
        val fixture = System.getenv(fixtureVariable)
        val resources = System.getenv("MUSICSM_TEST_VLC_RESOURCES")
        assumeTrue("Set $fixtureVariable and MUSICSM_TEST_VLC_RESOURCES for native tests", fixture != null && resources != null)
        require(File(checkNotNull(fixture)).isFile) { "Video fixture is missing" }
        System.setProperty("compose.application.resources.dir", checkNotNull(resources))

        runBlocking {
            val player = MotionArtworkPlayer.open()
            val count = AtomicInteger()
            val hashes = ConcurrentHashMap.newKeySet<Int>()
            val observe = launch(Dispatchers.Default) {
                player.frame.filterNotNull().collect {
                    count.incrementAndGet()
                    hashes.add(it.pixels.contentHashCode())
                }
            }
            try {
                player.start(fixture, playing = true)
                assertTrue(
                    "Native tests must load the requested VLC, not fall back to a system install",
                    NativeLibrary.getInstance("libvlc").file.canonicalPath
                        .startsWith(File(resources, "vlc").canonicalPath, ignoreCase = true),
                )
                withTimeout(10_000L) { while (count.get() < 3) delay(20) }
                val frame = checkNotNull(player.frame.value)
                assertEquals(64, frame.width)
                // libVLC may include up to two decoder padding rows in the callback surface.
                assertTrue(frame.height in 64..66)
                assertEquals(frame.width * frame.height * 4, frame.pixels.size)
                assertTrue(frame.pixels.any { it != 0.toByte() })
                assertEquals("The decorative video must not select audio", -1, player.mediaPlayer.audio().track())
                Image.makeRaster(
                    ImageInfo(frame.width, frame.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                    frame.pixels,
                    frame.width * 4,
                ).use { image ->
                    assertEquals(frame.width, image.width)
                    assertEquals(frame.height, image.height)
                }
                // Fixtures are one second long: frames must continue after their first pass.
                val beforeLoop = count.get()
                delay(2_200L)
                assertTrue("The decorative video must loop", count.get() > beforeLoop + 5)
                hashes.clear()
                delay(600L)
                assertTrue("Looped playback must contain changing pixels, not redraw a frozen last frame", hashes.size > 2)
                player.setPlaying(false)
                delay(500L)
                assertFalse("Native playback must pause, not just stop publishing frames", player.mediaPlayer.status().isPlaying)
                val paused = player.frame.value
                delay(400L)
                assertTrue("Paused artwork must stop decoding", paused === player.frame.value)
                player.setPlaying(true)
                withTimeout(3_000L) { while (player.frame.value === paused) delay(20) }
                assertNotSame(paused, player.frame.value)
            } finally {
                observe.cancel()
                player.release()
                player.release()
                assertNull(player.frame.value)
            }
        }

    }

    @Test
    fun `starting paused does not animate and corrupt video releases its player`() {
        val fixture = System.getenv("MUSICSM_TEST_MP4")
        val resources = System.getenv("MUSICSM_TEST_VLC_RESOURCES")
        assumeTrue("Native fixtures are required", fixture != null && resources != null)
        System.setProperty("compose.application.resources.dir", checkNotNull(resources))
        runBlocking {
            val player = MotionArtworkPlayer.open()
            try {
                player.start(checkNotNull(fixture), playing = false)
                delay(500L)
                assertFalse(player.mediaPlayer.status().isPlaying)
                player.setPlaying(true)
                withTimeout(5_000L) { while (player.frame.value == null) delay(20) }
            } finally {
                player.release()
            }
            val corrupt = File.createTempFile("motion-invalid", ".mp4")
            val broken = MotionArtworkPlayer.open()
            try {
                corrupt.writeText("Not a video")
                broken.start(corrupt.absolutePath, playing = true)
                withTimeout(5_000L) { while (!broken.isReleased) delay(20) }
                assertNull(broken.frame.value)
            } finally {
                broken.release()
                corrupt.delete()
            }
        }
    }
}
