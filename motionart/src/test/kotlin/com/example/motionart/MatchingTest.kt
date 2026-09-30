package com.example.motionart

import com.example.motionart.internal.Matching
import com.example.motionart.internal.TokenScraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MatchingTest {

    @Test
    fun `normalize strips bracketed decoration`() {
        assertEquals("1989", Matching.normalize("1989 (Taylor's Version) [Deluxe]"))
    }

    @Test
    fun `normalize strips dash suffixes`() {
        assertEquals("her", Matching.normalize("her - Single"))
        assertEquals("currents", Matching.normalize("Currents - Deluxe Edition"))
    }

    @Test
    fun `normalize strips bare reissue markers`() {
        assertEquals("in rainbows", Matching.normalize("In Rainbows Remastered"))
        assertEquals("utopia", Matching.normalize("UTOPIA Deluxe"))
    }

    @Test
    fun `normalize strips featured credits`() {
        assertEquals("sicko mode", Matching.normalize("SICKO MODE feat. Drake"))
        assertEquals("sicko mode", Matching.normalize("SICKO MODE ft Drake"))
    }

    @Test
    fun `normalize folds accents and punctuation`() {
        assertEquals("bailando", Matching.normalize("Bailándo"))
        assertEquals("short n sweet", Matching.normalize("Short n' Sweet"))
    }

    @Test
    fun `normalize tolerates missing input`() {
        assertEquals("", Matching.normalize(null))
        assertEquals("", Matching.normalize("   "))
    }

    @Test
    fun `primaryArtist takes the lead credit`() {
        assertEquals("drake", Matching.primaryArtist("Drake & 21 Savage"))
        assertEquals("calvin harris", Matching.primaryArtist("Calvin Harris, Dua Lipa"))
        assertEquals("tame impala", Matching.primaryArtist("Tame Impala"))
    }

    @Test
    fun `primaryArtist keeps names containing a separator word`() {
        // "and" is a separator, but dropping everything after it here would leave a stub name.
        assertEquals("simon", Matching.primaryArtist("Simon and Garfunkel"))
    }

    @Test
    fun `artistsMatch accepts padded credits`() {
        assertTrue(Matching.artistsMatch("Drake", "Drake & 21 Savage"))
        assertTrue(Matching.artistsMatch("The Weeknd", "the weeknd"))
    }

    @Test
    fun `artistsMatch rejects unrelated acts sharing a substring`() {
        assertFalse(Matching.artistsMatch("Sia", "Anastasia"))
        assertFalse(Matching.artistsMatch("Drake", "Drakeo the Ruler"))
    }

    @Test
    fun `artistsMatch rejects blanks`() {
        assertFalse(Matching.artistsMatch("", "Tame Impala"))
        assertFalse(Matching.artistsMatch("Tame Impala", null))
    }

    @Test
    fun `titlesMatch survives edition differences`() {
        assertTrue(Matching.titlesMatch("1989", "1989 (Taylor's Version) [Deluxe]"))
        assertTrue(Matching.titlesMatch("Short n Sweet", "Short n' Sweet (Deluxe)"))
        assertTrue(Matching.titlesMatch("good kid, m.A.A.d city", "good kid, m.A.A.d city (Deluxe Version)"))
    }

    @Test
    fun `titlesMatch rejects different releases`() {
        assertFalse(Matching.titlesMatch("Currents", "The Slow Rush"))
    }

    @Test
    fun `bundlePath prefers the modern bundle`() {
        val html = """
            <script src="/assets/index-legacy~17528a465c.js"></script>
            <script type="module" src="/assets/index~8bc3c631ba.js"></script>
        """.trimIndent()
        assertEquals("/assets/index~8bc3c631ba.js", TokenScraper.bundlePath(html))
    }

    @Test
    fun `bundlePath falls back when the naming changes`() {
        val html = """<script src="/assets/app/index.9f8e7d.js"></script>"""
        assertEquals("/assets/app/index.9f8e7d.js", TokenScraper.bundlePath(html))
    }

    @Test
    fun `bundlePath returns null when there is nothing to find`() {
        assertNull(TokenScraper.bundlePath("<html><body>no scripts</body></html>"))
    }

    @Test
    fun `extractToken skips expired candidates`() {
        val stale = jwt(expSeconds = 1_000)
        val live = jwt(expSeconds = 9_000)
        val js = "var a=\"$stale\";var b=\"$live\";"
        assertEquals(live, TokenScraper.extractToken(js, nowSeconds = 5_000))
    }

    @Test
    fun `extractToken returns null when every candidate has lapsed`() {
        val js = "var a=\"${jwt(expSeconds = 100)}\";"
        assertNull(TokenScraper.extractToken(js, nowSeconds = 5_000))
    }

    @Test
    fun `expiresAt reads the claim`() {
        assertEquals(4_242_424_242L, TokenScraper.expiresAt(jwt(expSeconds = 4_242_424_242L)))
    }

    @Test
    fun `expiresAt tolerates junk`() {
        assertNull(TokenScraper.expiresAt("not.a.jwt"))
        assertNull(TokenScraper.expiresAt("eyJhbGciOiJFUzI1NiJ9"))
    }

    /** Builds a structurally valid JWT; only the payload's `exp` claim is ever read. */
    private fun jwt(expSeconds: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"ES256","kid":"TESTKEY0"}""".toByteArray())
        val payload = enc.encodeToString(
            """{"iss":"TESTISSUER","iat":1,"exp":$expSeconds}""".toByteArray(),
        )
        val signature = enc.encodeToString(ByteArray(48) { it.toByte() })
        return "$header.$payload.$signature"
    }
}
