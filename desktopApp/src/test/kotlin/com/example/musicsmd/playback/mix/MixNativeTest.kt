package com.example.musicsmd.playback.mix

import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsmd.playback.PlayerController
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Needs libVLC: set MUSICSM_TEST_VLC_RESOURCES to the folder holding the bundled `vlc` directory. */
class MixNativeTest {

    private val sr = 44_100

    @Test
    fun decodesARangeFastAndOnTime() = withVlc { fixture ->
        val player = PlayerController()
        try {
            val decoder = VlcSnippetDecoder(player.factory)
            val started = System.nanoTime()
            val snippet = runBlocking { decoder.decode(fixture.toPath().toUri().toString(), 20_000, 40_000) }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertNotNull("nothing decoded", snippet)
            snippet!!
            val seconds = snippet.samples.size.toDouble() / snippet.sampleRate
            assertEquals("decoded length", 20.0, seconds, 0.3)
            assertTrue("decoding 20 s took ${tookMs}ms", tookMs < 10_000)
            val grid = BeatAnalyzer.analyze(snippet.samples, snippet.sampleRate, snippet.startMs)!!
            assertEquals(120.0, grid.bpm, 0.5)
            // Beats are at 250 + k*500 ms in the source: the snippet must say where it starts.
            val d = ((grid.anchorMs - 250.0) % 500.0 + 500.0) % 500.0
            assertTrue("phase off by ${minOf(d, 500 - d)}ms ($grid)", minOf(d, 500 - d) < 25.0)
        } finally {
            player.release()
        }
    }

    @Test
    fun standbyDeckStartsAndTakesOver() = withVlc { fixture ->
        val player = PlayerController()
        try {
            player.setVolume(0)
            player.keepEqualizerAttached = true
            val stream = PlayableStream(url = fixture.toPath().toUri().toString())
            player.play(stream)
            runBlocking {
                withTimeout(10_000) { while (!player.isPlaying) delay(20) }
                player.prepareStandby(stream, startPositionMs = 30_000)
                withTimeout(10_000) { while (!player.isStandbyReady) delay(20) }
                assertTrue(player.startStandby())
                player.setLevels(active = DeckLevel(gain = 0.5, bassCut = 1.0), outgoing = DeckLevel(gain = 0.5))
                withTimeout(10_000) { while (!player.isPlaying || player.positionMs() < 30_100) delay(20) }
                val position = player.positionMs()
                assertTrue("active deck at $position", position in 30_000..32_000)
                player.releaseOther()
                assertTrue(player.isPlaying)
            }
        } finally {
            player.stop()
            player.release()
        }
    }

    private fun withVlc(block: (File) -> Unit) {
        val resources = System.getenv("MUSICSM_TEST_VLC_RESOURCES")
        assumeTrue("Set MUSICSM_TEST_VLC_RESOURCES for native tests", resources != null)
        System.setProperty("compose.application.resources.dir", checkNotNull(resources))
        val file = File.createTempFile("musicsm-mix-fixture-", ".wav")
        try {
            file.writeBytes(beatWav(seconds = 60.0))
            block(file)
        } finally {
            file.delete()
        }
    }

    /** 120 BPM kicks from 250 ms, as 16-bit mono WAV. */
    private fun beatWav(seconds: Double): ByteArray {
        val n = (seconds * sr).toInt()
        val pcm = FloatArray(n)
        var t = 0.25
        var beat = 0
        while (t < seconds) {
            val amp = if (beat % 4 == 0) 0.9 else 0.5
            val start = (t * sr).toInt()
            for (i in 0 until (0.12 * sr).toInt()) {
                if (start + i >= n) break
                val tt = i.toDouble() / sr
                pcm[start + i] += (amp * exp(-tt * 30) * sin(2 * PI * (60 + 90 * exp(-tt * 40)) * tt)).toFloat()
            }
            beat++
            t += 0.5
        }
        val buf = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(n * 2)
        pcm.forEach { buf.putShort((it.coerceIn(-1f, 1f) * 32_767).toInt().toShort()) }
        return buf.array()
    }
}
