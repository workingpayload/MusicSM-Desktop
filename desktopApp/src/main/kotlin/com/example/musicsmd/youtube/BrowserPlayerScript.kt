package com.example.musicsmd.youtube

import com.example.musicsm.data.source.youtube.signin.PlayerScriptSolutions
import com.example.musicsm.data.source.youtube.signin.YouTubePlayerScript
import com.example.musicsmd.settings.AppPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * [YouTubePlayerScript] backed by a hidden (headless) Edge/Chrome tab.
 *
 * Unscrambling needs YouTube's whole player script evaluated by a real JavaScript engine, and PO
 * tokens come from BotGuard, which only passes in a real browser. The tab sits on a plain
 * music.youtube.com page, holds no account data (its profile is fresh each launch), and closes
 * itself after a few idle minutes.
 */
class BrowserPlayerScript(
    private val client: OkHttpClient,
    private val profileDir: File = File(AppPaths.dataDir, "youtube-player"),
) : YouTubePlayerScript, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var browser: ChromiumProcess? = null
    private var page: CdpConnection? = null
    private var idleJob: Job? = null

    override suspend fun signatureTimestamp(playerUrl: String): Int =
        run("__msm.signatureTimestamp(${playerUrl.js()})")?.jsonPrimitive?.intOrNull
            ?: throw IOException("No signature timestamp in YouTube's player script")

    override suspend fun solve(playerUrl: String, signatures: List<String>, nParameters: List<String>): PlayerScriptSolutions {
        val result = run("__msm.solve(${playerUrl.js()}, ${signatures.js()}, ${nParameters.js()})")?.jsonObject
            ?: throw IOException("YouTube's player script gave no answer")
        return PlayerScriptSolutions(
            signatures = result["sig"].stringMap(),
            nParameters = result["n"].stringMap(),
        )
    }

    override suspend fun mintPoToken(videoId: String): String =
        run("__msm.mintPoToken(${videoId.js()})")?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Couldn't create a YouTube PO token")

    /** Runs [expression] in the tab, restarting the browser once if it has gone away. */
    private suspend fun run(expression: String): JsonElement? = mutex.withLock {
        idleJob?.cancel()
        try {
            try {
                ensurePage().evaluate(expression, EVALUATE_TIMEOUT_MS)
            } catch (failure: IOException) {
                if (browser?.isAlive == true && page != null && failure.message?.startsWith("Browser script failed") == true) throw failure
                shutDown()
                ensurePage().evaluate(expression, EVALUATE_TIMEOUT_MS)
            }
        } finally {
            idleJob = scope.launch {
                delay(IDLE_SHUTDOWN_MS)
                mutex.withLock { shutDown() }
            }
        }
    }

    private suspend fun ensurePage(): CdpConnection {
        page?.takeIf { browser?.isAlive == true }?.let { return it }
        shutDown()
        val executable = ChromiumLocator.find() ?: throw BrowserUnavailableException()
        withContext(Dispatchers.IO) { profileDir.deleteRecursively() }
        val process = ChromiumProcess.launch(executable, profileDir, client, headless = true)
        browser = process
        try {
            val tab = process.openPage("about:blank")
            page = tab
            val userAgent = process.connectBrowser().use { it.send("Browser.getVersion") }["userAgent"]
                ?.jsonPrimitive?.contentOrNull?.replace("HeadlessChrome", "Chrome")
            if (userAgent != null) tab.send("Network.setUserAgentOverride", buildJsonObject { put("userAgent", userAgent) })
            tab.send("Page.enable")
            tab.send("Page.setBypassCSP", buildJsonObject { put("enabled", true) })
            tab.send("Page.navigate", buildJsonObject { put("url", HOST_PAGE) })
            awaitLoaded(tab)
            tab.evaluate("window.__msmSolverSrc = ${solverSource.js()}; true")
            tab.evaluate(resource("botguard.js") + "\n;true")
            tab.evaluate(resource("player-host.js") + "\n;true")
            return tab
        } catch (failure: Throwable) {
            shutDown()
            throw failure
        }
    }

    private suspend fun awaitLoaded(tab: CdpConnection) {
        repeat(LOAD_POLLS) {
            val ready = runCatching {
                tab.evaluate("location.host === 'music.youtube.com' && document.readyState === 'complete'")
            }.getOrNull()
            if (ready?.jsonPrimitive?.contentOrNull == "true") return
            delay(LOAD_POLL_MS)
        }
        throw IOException("Couldn't open YouTube in the hidden browser")
    }

    private fun shutDown() {
        page?.close()
        page = null
        browser?.close()
        browser = null
    }

    override fun close() {
        idleJob?.cancel()
        shutDown()
        profileDir.deleteRecursively()
    }

    private companion object {
        const val HOST_PAGE = "https://music.youtube.com/robots.txt"
        const val EVALUATE_TIMEOUT_MS = 45_000L
        const val IDLE_SHUTDOWN_MS = 10 * 60 * 1000L
        const val LOAD_POLLS = 100
        const val LOAD_POLL_MS = 100L

        val solverSource: String by lazy {
            resource("solver-lib.js") +
                "\nglobalThis.meriyah = lib.meriyah; globalThis.astring = lib.astring;\n" +
                resource("solver-core.js")
        }

        fun resource(name: String): String =
            BrowserPlayerScript::class.java.getResourceAsStream("/youtube/$name")?.use { it.readBytes().decodeToString() }
                ?: error("Missing resource youtube/$name")

        fun String.js(): String = JsonPrimitive(this).toString()

        fun List<String>.js(): String = JsonArray(map(::JsonPrimitive)).toString()

        fun JsonElement?.stringMap(): Map<String, String> =
            (this as? JsonObject)?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }?.toMap()
                .orEmpty()
    }
}
