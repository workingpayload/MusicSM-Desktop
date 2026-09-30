package com.example.musicsmd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork → accent colour, the desktop counterpart of the mobile app's
 * `Palette.from(bitmap).clearFilters().generate()` calls: the cover is loaded through Coil at 200 px
 * (as on mobile), its pixels run through [ArtworkPalette], and the result is cached per URL so every
 * screen showing the same cover agrees and pays for the extraction once.
 */
object ArtworkColors {
    private const val MAX_ENTRIES = 256

    /** Stores `0` for a cover that loaded but produced no swatch, so it isn't retried. */
    private const val NO_SWATCH = 0

    private val cache = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > MAX_ENTRIES
    }

    /** The cover's accent, or `null` when there is no cover or it has no usable colour. */
    suspend fun accentFor(url: String?): Color? {
        if (url.isNullOrEmpty()) return null
        synchronized(cache) { cache[url] }?.let { return if (it == NO_SWATCH) null else Color(it) }
        val rgb = try {
            withContext(Dispatchers.IO) { extract(url) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Network or decode failure: not cached, so the next screen to ask tries again.
            return null
        }
        synchronized(cache) { cache[url] = rgb ?: NO_SWATCH }
        return rgb?.let(::Color)
    }

    private suspend fun extract(url: String): Int? {
        val context = PlatformContext.INSTANCE
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(EXTRACT_SIZE_PX, EXTRACT_SIZE_PX))
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: throw IOException("Could not load $url")
        val bitmap = result.image.toBitmap().asComposeImageBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        return ArtworkPalette.accentRgb(pixels, bitmap.width, bitmap.height)
    }

    private const val EXTRACT_SIZE_PX = 200
}

/**
 * Turns an artwork accent into one the whole app can use as its theme accent.
 *
 * Mobile applies the raw swatch, but there the setting is opt-in; here it is on by default, so a
 * cover whose best swatch is near-black or washed out must not turn every button and link
 * unreadable. Greys keep the stock accent (as mobile's detail screens do with [isHueless]); anything
 * else keeps its hue and is only lifted into a range that reads on the dark surfaces.
 */
fun Color.asThemeAccent(): Color? {
    if (isHueless()) return null
    val hsl = toHsl()
    return hslColor(
        hue = hsl[0],
        saturation = hsl[1].coerceAtLeast(THEME_ACCENT_MIN_SATURATION),
        lightness = hsl[2].coerceIn(THEME_ACCENT_MIN_LIGHTNESS, THEME_ACCENT_MAX_LIGHTNESS),
        alpha = 1f,
    )
}

private const val THEME_ACCENT_MIN_SATURATION = 0.45f
private const val THEME_ACCENT_MIN_LIGHTNESS = 0.52f
private const val THEME_ACCENT_MAX_LIGHTNESS = 0.72f
