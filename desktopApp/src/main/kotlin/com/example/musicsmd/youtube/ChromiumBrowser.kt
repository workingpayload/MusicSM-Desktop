package com.example.musicsmd.youtube

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The Chromium-family browsers (Edge, Chrome, Brave, Chromium) MusicSM can drive for YouTube sign-in. */
internal object ChromiumLocator {
    /**
     * The browser to use. `MUSICSM_BROWSER` names one explicitly (a portable or unusual install);
     * when it is set, nothing else is tried.
     */
    fun find(override: String? = System.getenv(OVERRIDE_ENV)): File? {
        if (!override.isNullOrBlank()) return File(override).takeIf { it.isFile }
        return candidates().firstOrNull { it.isFile }
    }

    const val OVERRIDE_ENV = "MUSICSM_BROWSER"

    internal fun candidates(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: (String) -> String? = System::getenv,
        home: String = System.getProperty("user.home").orEmpty(),
    ): List<File> = when {
        osName.startsWith("Windows", ignoreCase = true) -> {
            val roots = listOfNotNull(env("ProgramFiles"), env("ProgramFiles(x86)"), env("LOCALAPPDATA")).distinct()
            listOf(
                "Microsoft\\Edge\\Application\\msedge.exe",
                "Google\\Chrome\\Application\\chrome.exe",
                "BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "Chromium\\Application\\chrome.exe",
            ).flatMap { path -> roots.map { File(it, path) } }
        }
        osName.startsWith("Mac", ignoreCase = true) -> listOf(
            "Google Chrome.app/Contents/MacOS/Google Chrome",
            "Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "Brave Browser.app/Contents/MacOS/Brave Browser",
            "Chromium.app/Contents/MacOS/Chromium",
        ).flatMap { app -> listOf(File("/Applications", app), File("$home/Applications", app)) }
        else -> listOf(
            "google-chrome", "google-chrome-stable", "microsoft-edge", "microsoft-edge-stable",
            "brave-browser", "chromium", "chromium-browser",
        ).flatMap { name -> listOf(File("/usr/bin", name), File("/usr/local/bin", name)) } + listOf(
            File("/opt/google/chrome/chrome"),
            File("/opt/microsoft/msedge/msedge"),
            File("/opt/brave.com/brave/brave"),
        )
    }
}

class BrowserUnavailableException : IOException(
    "Signing in to YouTube needs Microsoft Edge, Google Chrome, Brave or Chromium installed.",
)

/** What to tell someone with no supported browser, per platform. */
object BrowserHelp {
    const val DOWNLOAD_URL = "https://www.google.com/chrome/"

    fun isAvailable(): Boolean = ChromiumLocator.find() != null

    fun message(osName: String = System.getProperty("os.name").orEmpty()): String = when {
        osName.startsWith("Windows", ignoreCase = true) ->
            "YouTube sign-in and signed-in playback use Microsoft Edge or Google Chrome (Brave and Chromium work too), " +
                "but none was found. Install one, then try again."
        osName.startsWith("Mac", ignoreCase = true) ->
            "YouTube sign-in and signed-in playback use Google Chrome, Microsoft Edge, Brave or Chromium, but none " +
                "was found in Applications. Install one there, then try again. Safari and Firefox can't be used."
        else ->
            "YouTube sign-in and signed-in playback use Google Chrome, Chromium, Microsoft Edge or Brave, installed " +
                "from your distribution's packages or the vendor's .deb/.rpm (so it's in /usr/bin), but none was " +
                "found. Install one, then try again. Firefox and Flatpak/Snap-only Chromium builds can't be used."
    }
}

/**
 * A browser started with its own throwaway profile and the DevTools protocol on a local port.
 * The profile directory is MusicSM's, so it never touches the user's everyday browser profile.
 */
internal class ChromiumProcess private constructor(
    private val process: Process,
    private val client: OkHttpClient,
    val port: Int,
    private val browserPath: String,
) : Closeable {

    val isAlive: Boolean get() = process.isAlive

    /** Resolves when the browser exits, e.g. because the user closed its window. */
    fun onExit() = process.onExit()

    /** A connection to the browser itself (cookies, closing), rather than to one tab. */
    fun connectBrowser(): CdpConnection = CdpConnection(client, "ws://127.0.0.1:$port$browserPath")

    /** Opens a tab at [url] and connects to it. */
    suspend fun openPage(url: String): CdpConnection {
        val target = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/json/new?" + URLEncoder.encode(url, Charsets.UTF_8))
                .put(ByteArray(0).toRequestBody())
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Browser refused a new tab: HTTP ${response.code}")
                Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            }
        }
        val socketUrl = target["webSocketDebuggerUrl"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Browser tab has no DevTools socket")
        return CdpConnection(client, socketUrl)
    }

    override fun close() {
        if (!process.isAlive) return
        runCatching { connectBrowser().use { it.sendBlocking("Browser.close") } }
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroy() }
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    companion object {
        /**
         * Starts an ordinary browser window, with no DevTools port: Google refuses to sign in a
         * browser it can tell is remotely debuggable.
         */
        fun launchPlain(executable: File, profileDir: File, startUrl: String): Process {
            profileDir.mkdirs()
            return ProcessBuilder(
                executable.absolutePath,
                "--user-data-dir=${profileDir.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
                startUrl,
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }

        suspend fun launch(
            executable: File,
            profileDir: File,
            client: OkHttpClient,
            headless: Boolean,
            startUrl: String = "about:blank",
        ): ChromiumProcess = withContext(Dispatchers.IO) {
            profileDir.mkdirs()
            val portFile = File(profileDir, "DevToolsActivePort").apply { delete() }
            val command = buildList {
                add(executable.absolutePath)
                add("--remote-debugging-port=0")
                add("--user-data-dir=${profileDir.absolutePath}")
                add("--no-first-run")
                add("--no-default-browser-check")
                if (headless) {
                    add("--headless=new")
                    add("--mute-audio")
                    add("--disable-gpu")
                    add("--disable-sync")
                    add("--disable-extensions")
                    add("--disable-background-networking")
                }
                add(startUrl)
            }
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            try {
                withTimeout(STARTUP_TIMEOUT_MS) {
                    var started: ChromiumProcess? = null
                    while (started == null) {
                        if (!process.isAlive) throw IOException("The browser closed while starting")
                        val lines = portFile.takeIf { it.isFile }?.readLines().orEmpty()
                        val port = lines.getOrNull(0)?.trim()?.toIntOrNull()
                        val path = lines.getOrNull(1)?.trim()
                        if (port != null && path != null) {
                            started = ChromiumProcess(process, client, port, path)
                        } else {
                            delay(100)
                        }
                    }
                    started
                }
            } catch (failure: Throwable) {
                process.destroyForcibly()
                throw failure
            }
        }

        private const val STARTUP_TIMEOUT_MS = 20_000L
    }
}

/** A Chrome DevTools Protocol connection over a WebSocket. */
internal class CdpConnection(client: OkHttpClient, url: String) : Closeable {
    private val nextId = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile
    private var failure: Throwable? = null

    private val socket: WebSocket = client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                val id = message["id"]?.jsonPrimitive?.int ?: return
                pending.remove(id)?.complete(message)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = fail(t)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                fail(IOException("Browser connection closed"))
        },
    )

    private fun fail(cause: Throwable) {
        failure = cause
        pending.keys.toList().forEach { id -> pending.remove(id)?.completeExceptionally(cause) }
    }

    /** Sends [method] and returns its `result`; a protocol error is thrown as [IOException]. */
    suspend fun send(method: String, params: JsonObject = EMPTY, timeoutMs: Long = DEFAULT_TIMEOUT_MS): JsonObject {
        failure?.let { throw IOException("Browser connection lost", it) }
        val id = nextId.incrementAndGet()
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = reply
        val message = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }
        if (!socket.send(message.toString())) {
            pending.remove(id)
            throw IOException("Browser connection closed")
        }
        val response = try {
            withTimeout(timeoutMs) { reply.await() }
        } finally {
            pending.remove(id)
        }
        response["error"]?.let { throw IOException("$method failed: $it") }
        return response["result"]?.jsonObject ?: EMPTY
    }

    fun sendBlocking(method: String) {
        socket.send(buildJsonObject { put("id", nextId.incrementAndGet()); put("method", method) }.toString())
    }

    /** Evaluates [expression] in the page, awaiting a promise, and returns its JSON value. */
    suspend fun evaluate(expression: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): JsonElement? {
        val result = send(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", expression)
                put("awaitPromise", true)
                put("returnByValue", true)
            },
            timeoutMs,
        )
        result["exceptionDetails"]?.jsonObject?.let { details ->
            val description = details["exception"]?.jsonObject?.get("description")?.jsonPrimitive?.contentOrNull
                ?: details["text"]?.jsonPrimitive?.contentOrNull
            throw IOException("Browser script failed: ${description?.lineSequence()?.firstOrNull()}")
        }
        return result["result"]?.jsonObject?.get("value")
    }

    override fun close() {
        socket.close(1000, null)
    }

    private companion object {
        val EMPTY = JsonObject(emptyMap())
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
