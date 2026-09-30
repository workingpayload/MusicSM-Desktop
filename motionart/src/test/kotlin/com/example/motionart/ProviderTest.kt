package com.example.motionart

import com.example.motionart.internal.ManifestProvider
import com.example.motionart.internal.TidalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderTest {

    @Test
    fun `tidal builds a cover url from the five uuid groups`() {
        assertEquals(
            "https://resources.tidal.com/videos/5ea14b35/eff1/449a/9721/dc5c99f98e57/1280x1280.mp4",
            TidalProvider.videoUrl("5ea14b35-eff1-449a-9721-dc5c99f98e57"),
        )
    }

    @Test
    fun `tidal rejects ids that are not five part uuids`() {
        assertNull(TidalProvider.videoUrl("not-a-uuid"))
        assertNull(TidalProvider.videoUrl(""))
        assertNull(TidalProvider.videoUrl("5ea14b35-eff1-449a-9721"))
        assertNull(TidalProvider.videoUrl("5ea14b35-eff1-449a-9721-dc5c99f98e57-extra"))
    }

    @Test
    fun `tidal rejects ids with an empty group`() {
        // A blank group would produce a double slash and a 404, so it is caught up front.
        assertNull(TidalProvider.videoUrl("5ea14b35--449a-9721-dc5c99f98e57"))
    }

    @Test
    fun `auto order tries the broad catalogues before the curated lists`() {
        assertEquals(
            listOf(
                MotionArtProvider.APPLE,
                MotionArtProvider.TIDAL,
                MotionArtProvider.VIVI,
            ),
            MotionArtProvider.AUTO_ORDER,
        )
    }

    @Test
    fun `auto order covers every real provider exactly once`() {
        val real = MotionArtProvider.entries - MotionArtProvider.AUTO
        assertEquals(real.toSet(), MotionArtProvider.AUTO_ORDER.toSet())
        assertEquals(real.size, MotionArtProvider.AUTO_ORDER.size)
    }

    @Test
    fun `provider names round-trip and unknown values fall back to auto`() {
        assertEquals(MotionArtProvider.TIDAL, MotionArtProvider.fromName("TIDAL"))
        assertEquals(MotionArtProvider.APPLE, MotionArtProvider.fromName("apple"))
        assertEquals(MotionArtProvider.AUTO, MotionArtProvider.fromName("spotify"))
        assertEquals(MotionArtProvider.AUTO, MotionArtProvider.fromName("ECHO"))
        assertEquals(MotionArtProvider.AUTO, MotionArtProvider.fromName(null))
    }

    @Test
    fun `hls is detected past a query string`() {
        val hls = MotionArt("https://example.com/a/b_default.m3u8?token=x", MotionArtProvider.APPLE)
        val mp4 = MotionArt("https://example.com/a/b/1280x1280.mp4", MotionArtProvider.TIDAL)
        assert(hls.isHls)
        assert(!mp4.isHls)
    }

    @Test
    fun `manifest urls are the documented endpoints`() {
        assertEquals(
            "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json",
            ManifestProvider.VIVI_URL,
        )
    }
}
