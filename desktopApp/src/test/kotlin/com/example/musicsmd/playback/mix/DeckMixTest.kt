package com.example.musicsmd.playback.mix

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckMixTest {

    private val isoBands = listOf(60f, 170f, 310f, 600f, 1_000f, 3_000f, 6_000f, 12_000f, 14_000f, 16_000f)

    @Test
    fun fullLevelKeepsTheUsersEqualizer() {
        val user = floatArrayOf(3f, 2f, 1f, 0f, -1f, -2f, 0f, 0f, 4f, 5f)
        val v = DeckEqualizer.compute(2f, user, isoBands, DeckLevel.FULL)
        assertEquals(2f, v.preamp, 0f)
        assertArrayEquals(user, v.bands, 0f)
    }

    @Test
    fun fadeGoesOnThePreampThenSpillsIntoTheBands() {
        val flat = FloatArray(10)
        val half = DeckEqualizer.compute(0f, flat, isoBands, DeckLevel(gain = 0.5))
        assertEquals(-6.02f, half.preamp, 0.01f)
        assertArrayEquals(flat, half.bands, 0f)

        val silent = DeckEqualizer.compute(0f, flat, isoBands, DeckLevel.SILENT)
        assertEquals(DeckEqualizer.MIN_DB, silent.preamp, 0f)
        assertArrayEquals(FloatArray(10) { -20f }, silent.bands, 0f)
    }

    @Test
    fun bassCutOnlyTouchesLowBands() {
        val v = DeckEqualizer.compute(0f, FloatArray(10), isoBands, DeckLevel(gain = 1.0, bassCut = 0.5))
        assertEquals(0f, v.preamp, 0f)
        assertEquals(-10f, v.bands[0], 0f)
        assertEquals(-10f, v.bands[1], 0f)
        assertEquals(0f, v.bands[2], 0f)
        assertEquals(0f, v.bands[9], 0f)
    }

    @Test
    fun valuesStayInLibVlcRange() {
        val v = DeckEqualizer.compute(15f, FloatArray(10) { -15f }, isoBands, DeckLevel(gain = 0.001, bassCut = 1.0))
        assertEquals(-20f, v.preamp, 0f)
        v.bands.forEach { assertEquals(-20f, it, 0f) }
    }

    @Test
    fun silentDeckIsSilentEvenWithTheUsersBoosts() {
        val v = DeckEqualizer.compute(20f, FloatArray(10) { 20f }, isoBands, DeckLevel.SILENT)
        assertEquals(-20f, v.preamp, 0f)
        v.bands.forEach { assertEquals(-20f, it, 0f) }
    }

    @Test
    fun readsVlcWavOutputAsMono() {
        val wav = wav(channels = 2, rate = 44_100, frames = listOf(16_384 to -16_384, 32_767 to 32_767, 0 to -32_768))
        val (samples, rate) = WavReader.readMono(wav)!!
        assertEquals(44_100, rate)
        assertArrayEquals(floatArrayOf(0f, 32_767f / 32_768f, -0.5f), samples, 1e-6f)
    }

    @Test
    fun unfinishedWavHeaderUsesTheDataThatIsThere() {
        val wav = wav(channels = 1, rate = 22_050, frames = listOf(8_192 to 0, -8_192 to 0), declaredDataSize = 0)
        val (samples, rate) = WavReader.readMono(wav)!!
        assertEquals(22_050, rate)
        assertArrayEquals(floatArrayOf(0.25f, -0.25f), samples, 1e-6f)
        assertNull(WavReader.readMono(ByteArray(8)))
    }

    private fun wav(channels: Int, rate: Int, frames: List<Pair<Int, Int>>, declaredDataSize: Int? = null): ByteArray {
        val dataSize = frames.size * channels * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        buf.put("data".toByteArray()).putInt(declaredDataSize ?: dataSize)
        frames.forEach { (l, r) ->
            buf.putShort(l.toShort())
            if (channels == 2) buf.putShort(r.toShort())
        }
        return buf.array()
    }
}
