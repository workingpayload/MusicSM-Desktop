package com.example.musicsmd.update

import com.example.musicsmd.settings.SettingsStore
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotifierTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val client = OkHttpClient()
    private val dir: File = Files.createTempDirectory("notifier").toFile()
    private val settings = SettingsStore(File(dir, "settings.json"))
    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun serve(path: String, status: Int = 200, body: String) {
        val bytes = body.toByteArray()
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun release(tag: String, prerelease: Boolean = false) = """
        {"tag_name":"$tag","name":"$tag","draft":false,"prerelease":$prerelease,
         "html_url":"https://github.com/workingpayload/MusicSM-Desktop/releases/tag/$tag",
         "body":"  ## What's Changed\n* Faster search  ","assets":[{"name":"x.msi","size":1}]}
    """.trimIndent()

    private fun checker(current: String, path: String) = UpdateChecker(client, currentVersion = current, feedUrl = "$base$path")

    // ---- Versions -------------------------------------------------------------------------------

    @Test
    fun `newer versions are compared number by number`() {
        assertTrue(Versions.isNewer("v1.0.1", "1.0.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("v2.0.0-beta", "1.9"))
        assertFalse(Versions.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("1.0", "1.0.0"))
        assertFalse(Versions.isNewer("v0.9.9", "1.0.0"))
    }

    @Test
    fun `the build knows its own version`() {
        // Local and test builds get the default; releases get -PappVersion from the tag.
        assertEquals("1.1.1", AppVersion.current)
    }

    // ---- Reading GitHub -------------------------------------------------------------------------

    @Test
    fun `a newer release is found`() = runBlocking {
        serve("/latest", body = release("v1.0.1"))
        assertEquals(
            NewVersion("1.0.1", "## What's Changed\n* Faster search"),
            checker("1.0.0", "/latest").newerRelease(),
        )
    }

    @Test
    fun `nothing is found when up to date, before the first release, or for a pre-release`() = runBlocking {
        serve("/latest", body = release("v1.0.1"))
        serve("/beta", body = release("v1.1.0-beta", prerelease = true))
        serve("/none", status = 404, body = "{}")
        assertNull(checker("1.0.1", "/latest").newerRelease())
        assertNull(checker("1.0.0", "/none").newerRelease())
        assertNull(checker("1.0.0", "/beta").newerRelease())
    }

    @Test(expected = IOException::class)
    fun `a failed read throws`() {
        serve("/broken", status = 500, body = "oops")
        runBlocking { checker("1.0.0", "/broken").newerRelease() }
    }

    // ---- Announcing ------------------------------------------------------------------------------

    private val v101 = NewVersion("1.0.1", "notes")

    @Test
    fun `a new version is announced and the download page opened`() = runBlocking {
        val opened = mutableListOf<String>()
        val notifier = UpdateNotifier({ v101 }, settings, scope, enabled = true, openInBrowser = { opened += it })
        notifier.checkOnce()
        assertEquals(v101, notifier.available.value)

        notifier.openDownloadPage()
        assertEquals(listOf("https://music-sm.vercel.app"), opened)
        assertNull(notifier.available.value)
        // Not remembered: it's announced again until it's installed.
        notifier.checkOnce()
        assertEquals(v101, notifier.available.value)
    }

    @Test
    fun `later means that version is not announced again, but the next one is`() = runBlocking {
        var latest = v101
        val notifier = UpdateNotifier({ latest }, settings, scope, enabled = true, openInBrowser = {})
        notifier.checkOnce()
        notifier.later(v101)
        assertNull(notifier.available.value)
        assertEquals("1.0.1", settings.current.dismissedUpdateVersion)

        notifier.checkOnce()
        assertNull(notifier.available.value)

        latest = NewVersion("1.0.2", "")
        notifier.checkOnce()
        assertEquals("1.0.2", notifier.available.value?.version)
    }

    @Test
    fun `a failed check says nothing`() = runBlocking {
        val notifier = UpdateNotifier({ throw IOException("offline") }, settings, scope, enabled = true, openInBrowser = {})
        notifier.checkOnce()
        assertNull(notifier.available.value)
    }

    @Test
    fun `release notes lose their markdown`() {
        assertEquals(
            "What's Changed\n• Search as you type by @raj\n• Smoother player\n\nFull Changelog: https://x/compare/v1.0.0...v1.0.1",
            plainNotes("## What's Changed\n* Search as you type by @raj\n- Smoother player\n\n**Full Changelog**: https://x/compare/v1.0.0...v1.0.1\n"),
        )
    }

    @Test
    fun `a build run from source never checks`() = runBlocking {
        var checks = 0
        val notifier = UpdateNotifier({ checks++; v101 }, settings, scope, enabled = false, openInBrowser = {})
        notifier.start(firstCheckAfterMs = 0, everyMs = 10)
        delay(100)
        assertEquals(0, checks)
        assertNull(notifier.available.value)
    }

    @Test
    fun `an installed build checks at launch and again later`() = runBlocking {
        var checks = 0
        val notifier = UpdateNotifier({ checks++; null }, settings, scope, enabled = true, openInBrowser = {})
        notifier.start(firstCheckAfterMs = 0, everyMs = 20)
        delay(150)
        assertTrue("checked $checks times", checks >= 3)
    }
}
