package com.example.musicsm.data.source.youtube.signin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SignedInStreamResolverTest {

    @Test
    fun sapisidHashMatchesTheWebClientFormat() {
        // sha1("1700000000 abc/DEF https://music.youtube.com")
        assertEquals(
            "SAPISIDHASH 1700000000_" + sha1Hex("1700000000 abc/DEF https://music.youtube.com"),
            sapisidHash("abc/DEF", "https://music.youtube.com", 1_700_000_000),
        )
    }

    @Test
    fun playerIdIsReadFromIframeApi() {
        val page = """var scriptUrl = 'https:\/\/www.youtube.com\/s\/player\/8ab5c328\/www-widgetapi.vflset\/www-widgetapi.js';"""
        assertEquals("8ab5c328", playerIdFrom(page))
        assertEquals(
            "https://www.youtube.com/s/player/8ab5c328/player_ias.vflset/en_US/base.js",
            playerScriptUrl("8ab5c328"),
        )
        assertNull(playerIdFrom("nothing here"))
    }

    @Test
    fun selectsAacOverOpusAndSkipsDrcAndDubs() {
        val data = Json.parseToJsonElement(
            """
            {"adaptiveFormats":[
              {"itag":248,"mimeType":"video/webm; codecs=\"vp9\"","bitrate":2000000,"url":"https://v/video"},
              {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000,"signatureCipher":"s=x&url=https%3A%2F%2Fa%2Fopus"},
              {"itag":141,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":260000,"isDrc":true,"url":"https://a/drc"},
              {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131000,"averageBitrate":129000,"signatureCipher":"s=x&url=https%3A%2F%2Fa%2Faac"},
              {"itag":139,"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":300000,"audioTrack":{"audioIsDefault":false},"url":"https://a/dub"}
            ]}
            """,
        ).jsonObject
        val format = selectAudioFormat(data)!!
        assertEquals(140, format.itag)
        assertEquals(129000, format.bitrate)
    }

    @Test
    fun cipheredUrlIsRebuiltWithSolvedSignatureNAndPoToken() {
        val format = AudioFormat(
            itag = 140,
            mimeType = "audio/mp4",
            bitrate = 128000,
            url = null,
            signatureCipher = "s=AB%3DCD&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fexpire%3D1%26n%3Dabc%26mime%3Daudio%252Fmp4",
        )
        val scrambled = format.scrambledUrl()
        assertEquals("AB=CD", scrambled.signature)
        assertEquals("sig", scrambled.signatureParam)
        assertEquals("abc", nParameter(scrambled.url))

        val url = playableUrl(scrambled, signature = "solved=", n = "xyz", poToken = "tok").toHttpUrl()
        assertEquals("solved=", url.queryParameter("sig"))
        assertEquals("xyz", url.queryParameter("n"))
        assertEquals("tok", url.queryParameter("pot"))
        assertEquals("audio/mp4", url.queryParameter("mime"))
        assertEquals("1", url.queryParameter("expire"))
    }

    @Test
    fun loggedInIsReadFromTrackingParams() {
        fun response(value: String) = Json.parseToJsonElement(
            """{"responseContext":{"serviceTrackingParams":[{"service":"GFEEDBACK","params":[{"key":"logged_in","value":"$value"}]}]}}""",
        ).jsonObject
        assertTrue(isLoggedIn(response("1")))
        assertFalse(isLoggedIn(response("0")))
        assertTrue(isLoggedIn(Json.parseToJsonElement("{}").jsonObject))
    }

    @Test
    fun resolvesAPlayableStreamThroughTheSignedInSession() = runBlocking {
        val session = FakeSession(mapOf("SAPISID" to "sap", "LOGIN_INFO" to "x"))
        val script = FakeScript()
        val requests = mutableListOf<okhttp3.Request>()
        val resolver = SignedInStreamResolver(
            client = fakeClient(requests) { path ->
                when {
                    path.endsWith("iframe_api") -> IFRAME
                    path.contains("/player") -> PLAYER_OK
                    else -> error(path)
                }
            },
            session = session,
            script = script,
            clock = { 1_000_000L },
        )

        val stream = resolver.resolveStream("vid")

        val url = stream.url.toHttpUrl()
        assertEquals("sig-solved", url.queryParameter("sig"))
        assertEquals("n-solved", url.queryParameter("n"))
        assertEquals("pot-for-vid", url.queryParameter("pot"))
        assertEquals("audio/mp4", stream.mimeType)
        assertEquals(129, stream.bitrate)
        // YouTube said 21540 s; the 5 h cache cap is shorter.
        assertEquals(1_000_000L + 5 * 60 * 60 * 1000L, stream.expiresAtMs)

        val player = requests.single { it.url.encodedPath.contains("player") }
        assertEquals("SAPISID=sap; LOGIN_INFO=x", player.header("Cookie"))
        assertTrue(player.header("Authorization")!!.startsWith("SAPISIDHASH 1000_"))
        assertEquals(listOf("https://www.youtube.com/s/player/8ab5c328/player_ias.vflset/en_US/base.js"), script.stsRequests)
    }

    @Test
    fun aSignedOutAnswerForgetsTheSessionAndAsksForSignIn() = runBlocking {
        val session = FakeSession(mapOf("SAPISID" to "sap", "LOGIN_INFO" to "x"))
        val resolver = SignedInStreamResolver(
            client = fakeClient(mutableListOf()) { path -> if (path.endsWith("iframe_api")) IFRAME else PLAYER_SIGNED_OUT },
            session = session,
            script = FakeScript(),
        )
        try {
            resolver.resolveStream("vid")
            fail("expected sign-in to be required")
        } catch (expected: YouTubeSignInRequiredException) {
            assertTrue(session.rejected)
        }
    }

    @Test
    fun otherRefusalsKeepTheSession() = runBlocking {
        val session = FakeSession(mapOf("SAPISID" to "sap", "LOGIN_INFO" to "x"))
        val resolver = SignedInStreamResolver(
            client = fakeClient(mutableListOf()) { path -> if (path.endsWith("iframe_api")) IFRAME else PLAYER_UNAVAILABLE },
            session = session,
            script = FakeScript(),
        )
        try {
            resolver.resolveStream("vid")
            fail("expected a failure")
        } catch (expected: YouTubeSignInRequiredException) {
            fail("not a sign-in problem")
        } catch (expected: IOException) {
            assertEquals("Video unavailable", expected.message)
            assertFalse(session.rejected)
        }
    }

    @Test(expected = YouTubeSignInRequiredException::class)
    fun signedOutCannotResolve() = runBlocking<Unit> {
        SignedInStreamResolver(fakeClient(mutableListOf()) { error("no request expected") }, FakeSession(null), FakeScript())
            .resolveStream("vid")
    }

    private class FakeSession(private var stored: Map<String, String>?) : YouTubeSessionStore {
        var rejected = false
        override fun cookies() = stored
        override fun onSessionRejected() {
            rejected = true
            stored = null
        }
    }

    private class FakeScript : YouTubePlayerScript {
        val stsRequests = mutableListOf<String>()
        override suspend fun signatureTimestamp(playerUrl: String): Int {
            stsRequests += playerUrl
            return 20725
        }

        override suspend fun solve(playerUrl: String, signatures: List<String>, nParameters: List<String>) =
            PlayerScriptSolutions(
                signatures = signatures.associateWith { "sig-solved" },
                nParameters = nParameters.associateWith { "n-solved" },
            )

        override suspend fun mintPoToken(videoId: String) = "pot-for-$videoId"
    }

    private fun fakeClient(requests: MutableList<okhttp3.Request>, body: (String) -> String) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body(request.url.toString()).toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun sha1Hex(text: String) = java.security.MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val IFRAME = """if (!window['YT']) {var scriptUrl = 'https:\/\/www.youtube.com\/s\/player\/8ab5c328\/www-widgetapi.vflset\/www-widgetapi.js';}"""
        const val PLAYER_OK = """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{"expiresInSeconds":"21540","adaptiveFormats":[
               {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131000,"averageBitrate":129000,
                "signatureCipher":"s=SCRAMBLED&sp=sig&url=https%3A%2F%2Frr1.googlevideo.com%2Fvideoplayback%3Fn%3Draw"}
             ]}}
        """
        const val PLAYER_SIGNED_OUT = """
            {"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you're not a bot"},
             "responseContext":{"serviceTrackingParams":[{"params":[{"key":"logged_in","value":"0"}]}]}}
        """
        const val PLAYER_UNAVAILABLE = """
            {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Video unavailable"},
             "responseContext":{"serviceTrackingParams":[{"params":[{"key":"logged_in","value":"1"}]}]}}
        """
    }
}
