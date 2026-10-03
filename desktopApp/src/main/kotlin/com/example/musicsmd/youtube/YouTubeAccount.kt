package com.example.musicsmd.youtube

import com.example.musicsm.data.source.youtube.signin.YouTubeSessionStore
import com.example.musicsmd.settings.AppPaths
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

sealed interface YouTubeAccountState {
    data object SignedOut : YouTubeAccountState
    data object SigningIn : YouTubeAccountState
    data object SignedIn : YouTubeAccountState
    data class Failed(val message: String) : YouTubeAccountState
}

/**
 * The user's YouTube sign-in, used only when YouTube refuses to play anonymously on their network.
 *
 * Signing in opens Edge/Chrome with a fresh MusicSM-only profile at Google's sign-in page; once
 * the youtube.com session cookies appear they are copied out, the browser is closed and its
 * profile deleted. The cookies are stored encrypted for the Windows user (DPAPI) or readable only
 * by the user elsewhere, and are sent to YouTube only.
 */
class YouTubeAccount(
    private val client: OkHttpClient,
    private val file: File = File(AppPaths.dataDir, "youtube_session.bin"),
    private val cipher: SessionCipher = SessionCipher.forPlatform(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : YouTubeSessionStore {

    @Volatile
    private var cookies: Map<String, String>? = load()

    private val _state = MutableStateFlow<YouTubeAccountState>(
        if (cookies != null) YouTubeAccountState.SignedIn else YouTubeAccountState.SignedOut,
    )
    val state: StateFlow<YouTubeAccountState> = _state.asStateFlow()

    private var signInJob: Job? = null

    override fun cookies(): Map<String, String>? = cookies

    override fun onSessionRejected() {
        if (cookies == null) return
        forget()
        _state.value = YouTubeAccountState.Failed("YouTube ended the saved sign-in. Sign in again to keep playing.")
    }

    /** Opens the browser for the user to sign in; [state] follows along. */
    @Synchronized
    fun startSignIn() {
        if (signInJob?.isActive == true) return
        _state.value = YouTubeAccountState.SigningIn
        signInJob = scope.launch {
            _state.value = try {
                val signedIn = signInWithBrowser()
                save(signedIn)
                cookies = signedIn
                YouTubeAccountState.SignedIn
            } catch (cancelled: CancellationException) {
                if (cookies != null) YouTubeAccountState.SignedIn else YouTubeAccountState.SignedOut
            } catch (closed: SignInWindowClosedException) {
                if (cookies != null) YouTubeAccountState.SignedIn else YouTubeAccountState.SignedOut
            } catch (failure: Exception) {
                YouTubeAccountState.Failed(failure.message ?: "Couldn't sign in to YouTube")
            }
        }
    }

    @Synchronized
    fun cancelSignIn() {
        signInJob?.cancel()
    }

    fun signOut() {
        cancelSignIn()
        forget()
        _state.value = YouTubeAccountState.SignedOut
    }

    private fun forget() {
        cookies = null
        file.delete()
    }

    private suspend fun signInWithBrowser(): Map<String, String> {
        val browser = ChromiumLocator.find() ?: throw BrowserUnavailableException()
        val profile = File(AppPaths.dataDir, "youtube-signin")
        withContext(Dispatchers.IO) { profile.deleteRecursively() }
        try {
            val window = ChromiumProcess.launchPlain(browser, profile, SIGN_IN_URL)
            val closedByUser = try {
                withTimeout(SIGN_IN_TIMEOUT_MS) { awaitYouTubeMusic(window) }
            } finally {
                withContext(NonCancellable) { closeGracefully(window) }
            }
            // The profile is closed now, so a hidden copy of the browser can open it and read the
            // cookies the sign-in left behind.
            val cookies = ChromiumProcess.launch(browser, profile, client, headless = true).use { reader ->
                reader.connectBrowser().use { youTubeCookies(it.send("Storage.getCookies")) }
            }
            if (isSignedInSession(cookies)) return cookies
            if (closedByUser) throw SignInWindowClosedException()
            throw IOException("Sign-in wasn't finished. Try again and stay in the window until YouTube Music opens.")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { deleteWithRetry(profile) }
        }
    }

    /**
     * Waits until the window shows YouTube Music (where Google sends it after signing in), or the
     * user closes it. Returns whether the user closed it. Nothing is attached to the browser
     * meanwhile: Google refuses to sign in a browser it can tell is being remotely controlled.
     */
    private suspend fun awaitYouTubeMusic(window: Process): Boolean {
        while (true) {
            delay(POLL_MS)
            if (!window.isAlive) return true
            if (BrowserWindows.titles(window).any { YOUTUBE_MUSIC_TITLE in it }) {
                // Google sets the last session cookies just after the redirect lands.
                delay(SETTLE_MS)
                return false
            }
        }
    }

    private suspend fun closeGracefully(window: Process) {
        if (!window.isAlive) return
        BrowserWindows.close(window)
        withContext(Dispatchers.IO) {
            if (!window.waitFor(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                window.descendants().forEach { it.destroy() }
                window.destroy()
                window.waitFor(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    private fun load(): Map<String, String>? = runCatching {
        if (!file.isFile) return null
        val json = Json.parseToJsonElement(cipher.decrypt(file.readBytes()).decodeToString()).jsonObject
        json.mapValues { (_, value) -> value.jsonPrimitive.content }.takeIf(::isSignedInSession)
    }.getOrNull()

    private fun save(values: Map<String, String>) {
        file.parentFile?.mkdirs()
        val bytes = cipher.encrypt(JsonObject(values.mapValues { JsonPrimitive(it.value) }).toString().encodeToByteArray())
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        runCatching { Files.setPosixFilePermissions(temp.toPath(), PosixFilePermissions.fromString("rw-------")) }
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Couldn't save the YouTube sign-in")
        }
    }

    private suspend fun deleteWithRetry(dir: File) {
        // The browser can hold profile files for a moment after it exits.
        repeat(10) {
            if (!dir.exists() || dir.deleteRecursively()) return
            delay(300)
        }
    }

    private class SignInWindowClosedException : IOException("Sign-in window closed")

    internal companion object {
        const val SIGN_IN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"
        const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L
        const val POLL_MS = 1_000L
        const val SETTLE_MS = 3_000L
        const val CLOSE_WAIT_SECONDS = 10L
        const val YOUTUBE_MUSIC_TITLE = "YouTube Music"

        /** youtube.com cookies from a `Storage.getCookies` result; the `.youtube.com` copy wins. */
        fun youTubeCookies(result: JsonObject): Map<String, String> {
            val all = (result["cookies"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            return all
                .filter { it.text("domain")?.trimStart('.')?.let { d -> d == "youtube.com" || d.endsWith(".youtube.com") } == true }
                .sortedBy { if (it.text("domain") == ".youtube.com") 0 else 1 }
                .mapNotNull { cookie -> cookie.text("name")?.let { name -> name to cookie.text("value").orEmpty() } }
                .distinctBy { it.first }
                .toMap()
        }

        fun isSignedInSession(cookies: Map<String, String>): Boolean =
            ("SAPISID" in cookies || "__Secure-3PAPISID" in cookies) && "LOGIN_INFO" in cookies

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}

/** How the saved session is protected at rest. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(stored: ByteArray): ByteArray

    companion object {
        fun forPlatform(): SessionCipher =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) WindowsDpapi else FilePermissionsOnly
    }

    /** Windows' per-user data protection: only this Windows account can decrypt the file. */
    object WindowsDpapi : SessionCipher {
        override fun encrypt(plain: ByteArray): ByteArray = Crypt32Util.cryptProtectData(plain)
        override fun decrypt(stored: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(stored)
    }

    /** Elsewhere the file is made readable by its owner only. */
    object FilePermissionsOnly : SessionCipher {
        override fun encrypt(plain: ByteArray): ByteArray = plain
        override fun decrypt(stored: ByteArray): ByteArray = stored
    }
}
