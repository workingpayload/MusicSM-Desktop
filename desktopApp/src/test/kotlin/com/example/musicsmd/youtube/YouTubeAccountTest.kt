package com.example.musicsmd.youtube

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class YouTubeAccountTest {

    @Test
    fun keepsYouTubeCookiesPreferringTheSiteWideCopy() {
        val result = Json.parseToJsonElement(
            """
            {"cookies":[
              {"name":"SAPISID","value":"music-only","domain":"music.youtube.com"},
              {"name":"SAPISID","value":"site","domain":".youtube.com"},
              {"name":"LOGIN_INFO","value":"login","domain":".youtube.com"},
              {"name":"SID","value":"google","domain":".google.com"},
              {"name":"X","value":"evil","domain":"notyoutube.com"}
            ]}
            """,
        ).jsonObject
        assertEquals(
            mapOf("SAPISID" to "site", "LOGIN_INFO" to "login"),
            YouTubeAccount.youTubeCookies(result),
        )
    }

    @Test
    fun aSessionNeedsSapisidAndLoginInfo() {
        assertTrue(YouTubeAccount.isSignedInSession(mapOf("SAPISID" to "a", "LOGIN_INFO" to "b")))
        assertTrue(YouTubeAccount.isSignedInSession(mapOf("__Secure-3PAPISID" to "a", "LOGIN_INFO" to "b")))
        assertFalse(YouTubeAccount.isSignedInSession(mapOf("SAPISID" to "a")))
        assertFalse(YouTubeAccount.isSignedInSession(mapOf("LOGIN_INFO" to "b", "YSC" to "c")))
    }

    @Test
    fun savedSessionSurvivesARestartAndRejectionForgetsIt() {
        val dir = Files.createTempDirectory("yt-account").toFile()
        val file = File(dir, "session.bin")
        val cookies = mapOf("SAPISID" to "a", "LOGIN_INFO" to "b")
        file.writeBytes(ReversingCipher.encrypt("""{"SAPISID":"a","LOGIN_INFO":"b"}""".encodeToByteArray()))

        val account = YouTubeAccount(OkHttpClient(), file, ReversingCipher)
        assertEquals(cookies, account.cookies())
        assertEquals(YouTubeAccountState.SignedIn, account.state.value)

        account.onSessionRejected()
        assertNull(account.cookies())
        assertFalse(file.exists())
        assertTrue(account.state.value is YouTubeAccountState.Failed)
        assertNull(YouTubeAccount(OkHttpClient(), file, ReversingCipher).cookies())
        dir.deleteRecursively()
    }

    @Test
    fun unreadableSessionFileMeansSignedOut() {
        val dir = Files.createTempDirectory("yt-account").toFile()
        val file = File(dir, "session.bin").apply { writeText("garbage") }
        val account = YouTubeAccount(OkHttpClient(), file, ReversingCipher)
        assertNull(account.cookies())
        assertEquals(YouTubeAccountState.SignedOut, account.state.value)
        dir.deleteRecursively()
    }

    @Test
    fun windowsDpapiRoundTrips() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val plain = "secret".encodeToByteArray()
        val stored = SessionCipher.WindowsDpapi.encrypt(plain)
        assertFalse(stored.contentEquals(plain))
        assertTrue(SessionCipher.WindowsDpapi.decrypt(stored).contentEquals(plain))
    }

    @Test
    fun locatorKnowsTheUsualBrowserInstallsPerPlatform() {
        val windows = ChromiumLocator.candidates(
            osName = "Windows 11",
            env = mapOf("ProgramFiles" to "C:\\PF", "ProgramFiles(x86)" to "C:\\PF86", "LOCALAPPDATA" to "C:\\L")::get,
        ).map { it.path }
        assertTrue("C:\\PF86\\Microsoft\\Edge\\Application\\msedge.exe" in windows)
        assertTrue("C:\\L\\Google\\Chrome\\Application\\chrome.exe" in windows)
        assertTrue(windows.indexOfFirst { it.endsWith("msedge.exe") } < windows.indexOfFirst { it.endsWith("chrome.exe") })

        val mac = ChromiumLocator.candidates(osName = "Mac OS X", env = { null }, home = "/Users/me").map { it.path.replace('\\', '/') }
        assertTrue("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" in mac)

        val linux = ChromiumLocator.candidates(osName = "Linux", env = { null }).map { it.path.replace('\\', '/') }
        assertTrue("/usr/bin/chromium" in linux)
        assertTrue("/opt/google/chrome/chrome" in linux)
        assertFalse(linux.any { it.startsWith("/snap/") })
    }

    @Test
    fun anExplicitBrowserOverridesTheSearch() {
        val fake = Files.createTempFile("browser", ".exe").toFile()
        assertEquals(fake, ChromiumLocator.find(fake.path))
        assertNull(ChromiumLocator.find(File(fake.parentFile, "missing-browser.exe").path))
        fake.delete()
    }

    @Test
    fun browserHelpNamesWhatToInstallPerPlatform() {
        assertTrue(BrowserHelp.message("Windows 11").contains("Microsoft Edge"))
        val mac = BrowserHelp.message("Mac OS X")
        assertTrue(mac.contains("Applications") && mac.contains("Safari"))
        val linux = BrowserHelp.message("Linux")
        assertTrue(linux.contains("/usr/bin") && linux.contains("Flatpak"))
    }

    /**
     * Opt-in, live: plays one song through the signed-in path with a real headless browser.
     * Set MUSICSM_YT_COOKIES to a JSON file of DevTools `Storage.getCookies` output (or a plain
     * name→value object) for a signed-in YouTube account.
     */
    @Test
    fun liveSignedInPlayback() = runBlocking {
        val cookieFile = System.getenv("MUSICSM_YT_COOKIES")?.let(::File)?.takeIf { it.isFile } ?: return@runBlocking
        val parsed = Json.parseToJsonElement(cookieFile.readText())
        val cookies = if (parsed is kotlinx.serialization.json.JsonArray) {
            YouTubeAccount.youTubeCookies(kotlinx.serialization.json.JsonObject(mapOf("cookies" to parsed)))
        } else {
            parsed.jsonObject.mapValues { it.value.toString().trim('"') }
        }
        val client = OkHttpClient()
        val profile = Files.createTempDirectory("yt-player").toFile()
        val script = BrowserPlayerScript(client, profile)
        try {
            val resolver = com.example.musicsm.data.source.youtube.signin.SignedInStreamResolver(
                client = client,
                session = object : com.example.musicsm.data.source.youtube.signin.YouTubeSessionStore {
                    override fun cookies() = cookies
                    override fun onSessionRejected() = error("session rejected")
                },
                script = script,
            )
            val stream = resolver.resolveStream(System.getenv("MUSICSM_YT_VIDEO") ?: "4uTNVumfm84")
            val head = client.newCall(okhttp3.Request.Builder().url(stream.url).header("Range", "bytes=0-1").build()).execute()
            val total = head.use { it.header("Content-Range")?.substringAfter('/')?.toLong() } ?: error("no Content-Range")
            val mid = total / 2
            client.newCall(okhttp3.Request.Builder().url(stream.url).header("Range", "bytes=$mid-${mid + 200_000}").build())
                .execute().use { response ->
                    assertEquals(206, response.code)
                    assertEquals(200_001, response.body!!.bytes().size)
                }
        } finally {
            script.close()
        }
    }

    private object ReversingCipher : SessionCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(stored: ByteArray) = stored.reversedArray()
    }
}
