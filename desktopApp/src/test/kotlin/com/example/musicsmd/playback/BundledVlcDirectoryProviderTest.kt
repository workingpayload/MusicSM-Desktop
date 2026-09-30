package com.example.musicsmd.playback

import java.io.File
import java.nio.file.Files
import java.util.ServiceLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryProviderPriority

class BundledVlcDirectoryProviderTest {

    private val resources: File = Files.createTempDirectory("app-resources").toFile()
    private val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

    @After
    fun cleanUp() {
        resources.deleteRecursively()
    }

    /** Lays libVLC out the way prepareBundledVlc does for this OS; returns the folder holding libvlc. */
    private fun bundleLibVlc(): File {
        val libDir = if (isMac) File(resources, "vlc/lib") else File(resources, "vlc")
        libDir.mkdirs()
        File(libDir, if (isMac) "libvlc.dylib" else "libvlc.dll").writeText("")
        return libDir
    }

    @Test
    fun `finds the libVLC bundled in the app's resources`() {
        val libDir = bundleLibVlc()

        assertEquals(libDir, BundledVlcDirectoryProvider.libraryDir(resources.path))
    }

    @Test
    fun `stands aside when nothing is bundled, so vlcj falls back to the system's VLC`() {
        File(resources, "vlc").mkdirs()

        assertNull(BundledVlcDirectoryProvider.libraryDir(resources.path))
        assertNull(BundledVlcDirectoryProvider.libraryDir(null))
    }

    @Test
    fun `is registered with vlcj's discovery`() {
        val providers = ServiceLoader.load(DiscoveryDirectoryProvider::class.java).toList()

        assertTrue(providers.any { it is BundledVlcDirectoryProvider })
    }

    @Test
    fun `beats every place vlcj looks by itself but not a deliberate vlcj config file`() {
        val priority = BundledVlcDirectoryProvider.PRIORITY

        assertTrue(priority > DiscoveryProviderPriority.JNA_LIBRARY_PATH)
        assertTrue(priority > DiscoveryProviderPriority.INSTALL_DIR)
        assertTrue(priority > DiscoveryProviderPriority.WELL_KNOWN_DIRECTORY)
        assertTrue(priority > DiscoveryProviderPriority.SYSTEM_PATH)
        assertTrue(priority < DiscoveryProviderPriority.CONFIG_FILE)
    }
}
