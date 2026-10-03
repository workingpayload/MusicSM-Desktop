package com.example.musicsm.data.source.youtube.signin

import com.example.musicsm.data.source.youtube.pickAudioStream
import com.example.musicsm.domain.model.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Resolves audio through the user's signed-in YouTube session.
 *
 * YouTube can refuse every anonymous client from a network it has flagged ("Sign in to confirm
 * you're not a bot"); a signed-in YouTube Music web (`WEB_REMIX`) request still gets formats there.
 * Those come scrambled, so [script] unscrambles them and mints the PO token googlevideo wants
 * before it serves more than the first megabyte.
 */
class SignedInStreamResolver(
    private val client: OkHttpClient,
    private val session: YouTubeSessionStore,
    private val script: YouTubePlayerScript,
    private val clock: () -> Long = System::currentTimeMillis,
) : SignedInStreams {

    override val isSignedIn: Boolean get() = session.cookies() != null

    @Volatile
    private var player: CachedPlayer? = null

    override suspend fun resolveStream(videoId: String): PlayableStream {
        val cookies = session.cookies() ?: throw YouTubeSignInRequiredException()
        val playerUrl = playerUrl()
        val response = requestPlayer(videoId, cookies, script.signatureTimestamp(playerUrl))
        checkPlayable(response)

        val streamingData = response["streamingData"]?.jsonObject ?: throw IOException("YouTube sent no streams for $videoId")
        val format = selectAudioFormat(streamingData) ?: throw IOException("No audio stream for $videoId")
        val scrambled = format.scrambledUrl()
        val n = nParameter(scrambled.url)
        val solutions = if (scrambled.signature != null || n != null) {
            script.solve(playerUrl, listOfNotNull(scrambled.signature), listOfNotNull(n))
        } else {
            PlayerScriptSolutions(emptyMap(), emptyMap())
        }
        val url = playableUrl(
            scrambled = scrambled,
            signature = scrambled.signature?.let { solutions.signatures[it] ?: throw IOException("Couldn't unscramble the stream signature") },
            n = n?.let { solutions.nParameters[it] ?: throw IOException("Couldn't unscramble the stream's n parameter") },
            poToken = script.mintPoToken(videoId),
        )
        val expiresInMs = streamingData["expiresInSeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1000)
        return PlayableStream(
            url = url,
            mimeType = format.mimeType.substringBefore(';').trim(),
            bitrate = format.bitrate / 1000,
            expiresAtMs = clock() + minOf(expiresInMs ?: STREAM_TTL_MS, STREAM_TTL_MS),
        )
    }

    /** The current web player script; it changes every few days, so the answer is re-checked hourly. */
    private suspend fun playerUrl(): String {
        player?.takeIf { clock() - it.fetchedAtMs < PLAYER_CHECK_MS }?.let { return it.url }
        val page = get(IFRAME_API_URL)
        val id = playerIdFrom(page) ?: throw IOException("Couldn't find YouTube's player script")
        return playerScriptUrl(id).also { player = CachedPlayer(it, clock()) }
    }

    private suspend fun requestPlayer(videoId: String, cookies: Map<String, String>, signatureTimestamp: Int): JsonObject {
        val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"]
        if (sapisid == null) {
            session.onSessionRejected()
            throw YouTubeSignInRequiredException()
        }
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", CLIENT_NAME)
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") { put("signatureTimestamp", signatureTimestamp) }
            }
        }
        val request = Request.Builder()
            .url(PLAYER_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("Cookie", cookieHeader(cookies))
            .header("Authorization", sapisidHash(sapisid, ORIGIN, clock() / 1000))
            .header("X-Goog-AuthUser", "0")
            .build()
        val text = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    session.onSessionRejected()
                    throw YouTubeSignInRequiredException()
                }
                if (!response.isSuccessful) throw IOException("YouTube player request failed: HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun checkPlayable(response: JsonObject) {
        val playability = response["playabilityStatus"]?.jsonObject
        val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
        if (status == "OK") return
        if (!isLoggedIn(response)) {
            session.onSessionRejected()
            throw YouTubeSignInRequiredException()
        }
        val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull
        throw IOException(reason ?: "YouTube won't play this song (${status ?: "no status"})")
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body?.string().orEmpty()
        }
    }

    private class CachedPlayer(val url: String, val fetchedAtMs: Long)

    companion object {
        private const val ORIGIN = "https://music.youtube.com"
        private const val PLAYER_URL = "$ORIGIN/youtubei/v1/player?prettyPrint=false"
        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_ID = "67"
        private const val CLIENT_VERSION = "1.20250310.01.00"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private const val PLAYER_CHECK_MS = 60 * 60 * 1000L
        private const val STREAM_TTL_MS = 5 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal data class AudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String?,
    val signatureCipher: String?,
)

/** A format's URL, and its still-scrambled signature with the query parameter it belongs in. */
internal data class ScrambledUrl(val url: String, val signature: String?, val signatureParam: String)

/** The best audio-only format: AAC first (it seeks best in libVLC), original language, no DRC. */
internal fun selectAudioFormat(streamingData: JsonObject): AudioFormat? {
    val formats = (streamingData["adaptiveFormats"] as? JsonArray).orEmpty().mapNotNull { element ->
        val format = element as? JsonObject ?: return@mapNotNull null
        val mimeType = format.string("mimeType") ?: return@mapNotNull null
        if (!mimeType.startsWith("audio/")) return@mapNotNull null
        if (format["isDrc"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
        val track = format["audioTrack"] as? JsonObject
        if (track?.get("audioIsDefault")?.jsonPrimitive?.booleanOrNull == false) return@mapNotNull null
        val url = format.string("url")
        val cipher = format.string("signatureCipher") ?: format.string("cipher")
        if (url == null && cipher == null) return@mapNotNull null
        AudioFormat(
            itag = format["itag"]?.jsonPrimitive?.intOrNull ?: 0,
            mimeType = mimeType,
            bitrate = (format["averageBitrate"] ?: format["bitrate"])?.jsonPrimitive?.intOrNull ?: 0,
            url = url,
            signatureCipher = cipher,
        )
    }
    return pickAudioStream(
        streams = formats,
        isAac = { it.mimeType.startsWith("audio/mp4") },
        isProgressive = { true },
        bitrate = AudioFormat::bitrate,
    )
}

internal fun AudioFormat.scrambledUrl(): ScrambledUrl {
    url?.let { return ScrambledUrl(it, signature = null, signatureParam = "signature") }
    val fields = signatureCipher.orEmpty().split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=', missingDelimiterValue = "")
        if (key.isEmpty()) null else key to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }.toMap()
    val base = fields["url"] ?: throw IOException("Stream cipher without a URL")
    return ScrambledUrl(base, signature = fields["s"], signatureParam = fields["sp"] ?: "signature")
}

internal fun nParameter(url: String): String? = url.toHttpUrl().queryParameter("n")

internal fun playableUrl(scrambled: ScrambledUrl, signature: String?, n: String?, poToken: String): String {
    val builder = scrambled.url.toHttpUrl().newBuilder()
    signature?.let { builder.setQueryParameter(scrambled.signatureParam, it) }
    n?.let { builder.setQueryParameter("n", it) }
    builder.setQueryParameter("pot", poToken)
    return builder.build().toString()
}

/** The `Authorization` header a signed-in web client sends: SHA-1 over time, SAPISID and origin. */
internal fun sapisidHash(sapisid: String, origin: String, epochSeconds: Long): String {
    val digest = MessageDigest.getInstance("SHA-1").digest("$epochSeconds $sapisid $origin".toByteArray())
    return "SAPISIDHASH ${epochSeconds}_${digest.joinToString("") { "%02x".format(it) }}"
}

internal fun cookieHeader(cookies: Map<String, String>): String =
    cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

/** The player id in `www.youtube.com/iframe_api`, e.g. `8ab5c328`. */
internal fun playerIdFrom(iframeApi: String): String? =
    Regex("""player\\?/([0-9a-fA-F]{8})\\?/""").find(iframeApi)?.groupValues?.get(1)

internal fun playerScriptUrl(playerId: String): String =
    "https://www.youtube.com/s/player/$playerId/player_ias.vflset/en_US/base.js"

/** Whether YouTube treated the request as signed in, from its tracking params; unknown counts as yes. */
internal fun isLoggedIn(response: JsonObject): Boolean {
    val services = response["responseContext"]?.jsonObject?.get("serviceTrackingParams") as? JsonArray ?: return true
    for (service in services) {
        val params = (service as? JsonObject)?.get("params") as? JsonArray ?: continue
        for (param in params) {
            val entry = param as? JsonObject ?: continue
            if (entry.string("key") == "logged_in") return entry.string("value") != "0"
        }
    }
    return true
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
