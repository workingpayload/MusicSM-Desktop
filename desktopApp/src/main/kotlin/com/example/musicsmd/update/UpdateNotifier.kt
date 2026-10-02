package com.example.musicsmd.update

import com.example.musicsmd.settings.SettingsStore
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where every MusicSM build is downloaded from; the update notice sends people here. */
const val DOWNLOAD_PAGE_URL = "https://music-sm.vercel.app"

/** This build's version. */
object AppVersion {
    /** -PappVersion at build time (the release tag without its "v"); "1.1.0" in local builds. */
    val current: String by lazy {
        AppVersion::class.java.getResource("/com/example/musicsmd/app-version.txt")
            ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "0.0.0"
    }

    /** Set by jpackage's launcher, so false when running from Gradle, which has nothing to update. */
    val isInstalled: Boolean get() = !System.getProperty("jpackage.app-path").isNullOrBlank()
}

/** Release versions ("v1.2.3", "1.2.3"), compared number by number as mobile's updater does. */
object Versions {
    /** The tag without its "v". */
    fun clean(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v1.2.3-beta" -> [1, 2, 3]: a pre-release suffix doesn't count. */
    private fun parts(version: String): List<Int> =
        clean(version).substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

/** A release newer than the running app. */
data class NewVersion(val version: String, val notes: String)

/**
 * Reads the newest MusicSM Desktop release on GitHub, as mobile's `AppUpdater.check()` does for the
 * APK. `releases/latest` skips drafts and pre-releases, so a beta is never announced.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val currentVersion: String = AppVersion.current,
    // -Dmusicsmd.updateFeed=<url> reads another feed instead (a fork, or a local test server).
    private val feedUrl: String = System.getProperty("musicsmd.updateFeed") ?: LATEST_RELEASE_URL,
) {
    /** The newest release if it is newer than this build, else null. Throws when it can't be read. */
    suspend fun newerRelease(): NewVersion? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MusicSM-Desktop/$currentVersion")
            .build()
        val body = client.newCall(request).execute().use { response ->
            // No release published yet.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            (response.body ?: throw IOException("Empty answer from GitHub")).string()
        }
        val release = json.decodeFromString<GitHubRelease>(body)
        val version = Versions.clean(release.tagName)
        if (release.draft || release.prerelease || !Versions.isNewer(version, currentVersion)) {
            return@withContext null
        }
        NewVersion(version, release.body.orEmpty().trim())
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/workingpayload/MusicSM-Desktop/releases/latest"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * Tells the user when a new MusicSM Desktop is out and sends them to [DOWNLOAD_PAGE_URL] for it —
 * the desktop side of mobile's update prompt, minus the self-install. Checks a few seconds after
 * launch and then every few hours, since a desktop app can stay open for days. "Later" means that
 * version isn't announced again.
 */
class UpdateNotifier(
    private val newerRelease: suspend () -> NewVersion?,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    // A Gradle `run` is built from source, so there is nothing to tell it about.
    private val enabled: Boolean = AppVersion.isInstalled,
    private val openInBrowser: (String) -> Unit = ::browse,
) {
    private val _available = MutableStateFlow<NewVersion?>(null)
    /** The version to announce, or null when there's nothing to show. */
    val available: StateFlow<NewVersion?> = _available.asStateFlow()

    fun start(firstCheckAfterMs: Long = FIRST_CHECK_DELAY_MS, everyMs: Long = CHECK_INTERVAL_MS) {
        if (!enabled) return
        scope.launch {
            delay(firstCheckAfterMs)
            while (isActive) {
                checkOnce()
                delay(everyMs)
            }
        }
    }

    internal suspend fun checkOnce() {
        val release = try {
            newerRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or rate-limited: say nothing, the next check will tell.
            null
        } ?: return
        if (release.version == settingsStore.current.dismissedUpdateVersion) return
        _available.value = release
    }

    /** "Download": opens the download page. Not remembered, so it's announced again until installed. */
    fun openDownloadPage() {
        openInBrowser(DOWNLOAD_PAGE_URL)
        _available.value = null
    }

    /** "Later": this version isn't announced again. */
    fun later(release: NewVersion) {
        settingsStore.update { it.copy(dismissedUpdateVersion = release.version) }
        _available.value = null
    }

    /** Closed without choosing: it's back at the next check. */
    fun close() {
        _available.value = null
    }

    private companion object {
        const val FIRST_CHECK_DELAY_MS = 4_000L
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

private fun browse(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
