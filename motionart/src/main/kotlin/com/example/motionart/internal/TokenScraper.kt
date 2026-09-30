package com.example.motionart.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Pulls the anonymous web-player credential out of the provider's own site.
 *
 * The catalog API will not answer without a bearer token, and the public web player gets one by
 * shipping it inside its JavaScript bundle. So we do what the page does: read the bundle, take the
 * token, and use it until it expires. Nothing here is a secret of ours and nothing is stored — the
 * token is fetched at runtime, held in memory, and re-fetched when it lapses.
 */
internal object TokenScraper {

    /**
     * The main bundle's path, which is content-hashed and therefore changes on every deploy.
     *
     * The legacy build carries the same token but is compiled for older browsers and is much
     * larger, so it is only a fallback.
     */
    private val BUNDLE = Regex("/assets/[A-Za-z0-9_./~-]*index~[A-Za-z0-9]+\\.js")
    private val BUNDLE_ANY = Regex("/assets/[A-Za-z0-9_./~-]*index[A-Za-z0-9_.~-]*\\.js")

    /** A JWT's three base64url segments. The length floors keep short lookalikes out. */
    private val JWT = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}")

    private val json = Json { ignoreUnknownKeys = true }

    fun bundlePath(html: String): String? =
        BUNDLE.find(html)?.value
            ?: BUNDLE_ANY.findAll(html).map { it.value }.firstOrNull { !it.contains("legacy") }

    /**
     * The first token in the bundle that is actually usable.
     *
     * A bundle can contain more than one JWT-shaped string; rather than trusting position, each
     * candidate is checked for an expiry that has not already passed.
     */
    fun extractToken(js: String, nowSeconds: Long): String? =
        JWT.findAll(js).map { it.value }.firstOrNull { token ->
            val exp = expiresAt(token)
            exp != null && exp > nowSeconds
        }

    /** The `exp` claim in epoch seconds, or null if the payload is not readable. */
    fun expiresAt(token: String): Long? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return null
        val decoded = Base64.getUrlDecoder().decode(payload).decodeToString()
        json.parseToJsonElement(decoded).jsonObject["exp"]?.jsonPrimitive?.content?.toLong()
    }.getOrNull()
}
