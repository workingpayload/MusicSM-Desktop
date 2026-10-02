package com.example.musicsmd.motionart

import com.example.musicsmd.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionArtSettingsTest {
    @Test
    fun `older settings keep their values and gain artwork defaults`() {
        val dir = Files.createTempDirectory("motion-art-settings").toFile()
        try {
            val file = File(dir, "settings.json")
            file.writeText("""{"volume":42,"themeFromArtwork":false}""")
            val settings = SettingsStore(file)
            assertEquals(42, settings.current.volume)
            assertTrue(settings.current.animatedArtwork)
            assertEquals("FULL_SCREEN", settings.current.animatedArtworkStyle)
            assertEquals("AUTO", settings.current.animatedArtworkSource)
            settings.update { it.copy(animatedArtwork = false, animatedArtworkStyle = "CARD", animatedArtworkSource = "VIVI") }
            val restored = SettingsStore(file).current
            assertEquals(false, restored.animatedArtwork)
            assertEquals("CARD", restored.animatedArtworkStyle)
            assertEquals("VIVI", restored.animatedArtworkSource)
            assertEquals(42, restored.volume)
            assertEquals(false, restored.themeFromArtwork)
        } finally {
            dir.deleteRecursively()
        }
    }
}
