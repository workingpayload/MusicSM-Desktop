package com.example.musicsmd.motionart

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionArtworkPlayerTest {
    @Test
    fun `video size is bounded without stretching or upscaling`() {
        assertEquals(1280 to 720, motionVideoSize(3840, 2160))
        assertEquals(720 to 1280, motionVideoSize(2160, 3840))
        assertEquals(1280 to 1280, motionVideoSize(2048, 2048))
        assertEquals(320 to 240, motionVideoSize(320, 240))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid video dimensions are rejected`() {
        motionVideoSize(0, 100)
    }

    @Test
    fun `frame snapshots remove row padding and never retain the native buffer`() {
        val native = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 99, 99, 99, 99, 5, 6, 7, 8))
        val frame = copyVideoFrame(native, width = 1, height = 2, pitch = 8)
        native.put(0, 42)
        assertEquals(0, native.position())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), frame.pixels)
    }

    @Test
    fun `unknown saved styles keep the full screen default`() {
        assertEquals(MotionArtStyle.FULL_SCREEN, MotionArtStyle.fromKey("obsolete"))
        assertEquals(MotionArtStyle.FULL_SCREEN, MotionArtStyle.fromKey("EDGE"))
        assertEquals(MotionArtStyle.CARD, MotionArtStyle.fromKey("CARD"))
        assertEquals(listOf(MotionArtStyle.CARD, MotionArtStyle.FULL_SCREEN), MotionArtStyle.entries)
    }
}
