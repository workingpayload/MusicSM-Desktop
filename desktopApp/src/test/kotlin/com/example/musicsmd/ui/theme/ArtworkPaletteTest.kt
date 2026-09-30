package com.example.musicsmd.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AndroidX Palette port behind artwork theming, plus the dark-palette parts of mobile's
 * `PaletteTest` that desktop shares (withAccent, luminance).
 */
class ArtworkPaletteTest {

    private fun image(vararg parts: Pair<Int, Int>): IntArray =
        parts.flatMap { (argb, count) -> List(count) { argb } }.toIntArray()

    @Test
    fun `a vibrant colour wins over a larger grey area`() {
        val red = 0xFFE02020.toInt()
        val grey = 0xFF808080.toInt()
        val pixels = image(grey to 600, red to 400)
        val palette = ArtworkPalette.generate(pixels, 1000, 1)

        assertEquals(red, palette.vibrant?.rgb)
        assertEquals(grey, palette.dominant?.rgb)
        assertEquals(red, ArtworkPalette.accentRgb(pixels, 1000, 1))
    }

    @Test
    fun `a dark cover still themes from its small vibrant highlight`() {
        val blue = 0xFF2050E0.toInt()
        val pixels = image(0xFF080808.toInt() to 900, blue to 100)
        assertEquals(blue, ArtworkPalette.accentRgb(pixels, 1000, 1))
    }

    @Test
    fun `a monochrome cover falls back to its dominant colour`() {
        val grey = 0xFF808080.toInt()
        val palette = ArtworkPalette.generate(image(grey to 100), 100, 1)
        assertNull(palette.vibrant)
        assertEquals(grey, ArtworkPalette.accentRgb(image(grey to 100), 100, 1))
    }

    @Test
    fun `colours are quantised to five bits per channel like AndroidX`() {
        // 0xE7 → 0b11100 → 0xE0: the low three bits are dropped on the way through.
        val palette = ArtworkPalette.generate(image(0xFFE7E7E7.toInt() to 10), 10, 1)
        assertEquals(0xFFE0E0E0.toInt(), palette.dominant?.rgb)
    }

    @Test
    fun `a many-coloured cover is cut down to sixteen swatches covering every pixel`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            (0xFF shl 24) or ((x * 4) shl 16) or ((y * 4) shl 8) or (((x + y) * 2) and 0xFF)
        }
        val palette = ArtworkPalette.generate(pixels, width, height)
        assertEquals(16, palette.swatches.size)
        assertEquals(width * height, palette.swatches.sumOf { it.population })
    }

    @Test
    fun `large covers are scaled down to palette's 112 by 112 area first`() {
        val size = 400
        val pixels = IntArray(size * size) { i -> if (i % 2 == 0) 0xFFE02020.toInt() else 0xFF2050E0.toInt() }
        val palette = ArtworkPalette.generate(pixels, size, size)
        assertEquals(112 * 112, palette.swatches.sumOf { it.population })
    }

    @Test
    fun `each target claims its own swatch`() {
        val pixels = image(
            0xFFE02020.toInt() to 300, // vibrant
            0xFF801818.toInt() to 300, // dark vibrant
            0xFFF0A0A0.toInt() to 300, // light vibrant
        )
        val palette = ArtworkPalette.generate(pixels, 900, 1)
        val picked = listOfNotNull(palette.lightVibrant, palette.vibrant, palette.darkVibrant).map { it.rgb }
        assertEquals(picked.size, picked.toSet().size)
    }

    @Test
    fun `grey covers keep the stock theme accent`() {
        assertNull(Color(0xFF808080).asThemeAccent())
    }

    @Test
    fun `a dark cover accent is lifted to read on dark surfaces without losing its hue`() {
        val darkRed = Color(0xFF5A0A10)
        val accent = darkRed.asThemeAccent()
        assertNotNull(accent)
        val hsl = accent!!.toHsl()
        assertTrue("lightness ${hsl[2]}", hsl[2] >= 0.52f)
        assertEquals(darkRed.toHsl()[0], hsl[0], 0.01f)
    }

    @Test
    fun `withAccent re-tints the accent family and leaves surfaces alone`() {
        val seed = Color(0xFF3DDC84)
        val themed = DarkPalette.withAccent(seed)
        assertEquals(seed, themed.accent)
        assertEquals(DarkPalette.background, themed.background)
        assertEquals(DarkPalette.surfaceHigh, themed.surfaceHigh)
        assertEquals(DarkPalette.onSurface, themed.onSurface)
    }

    @Test
    fun `on-accent content flips to dark text for pale accents`() {
        assertEquals(Color.White, DarkPalette.withAccent(Color(0xFF1E3264)).onAccent)
        assertNotEquals(Color.White, DarkPalette.withAccent(Color(0xFFFFC53D)).onAccent)
    }

    @Test
    fun `amoled variant blacks out the base tiers but keeps the accent`() {
        assertEquals(Color.Black, AmoledPalette.background)
        assertEquals(Color.Black, AmoledPalette.surfaceLowest)
        assertEquals(DarkPalette.accent, AmoledPalette.accent)
        assertNotEquals(Color.Black, AmoledPalette.surfaceHigh)
    }

    @Test
    fun `luminance test matches the extremes`() {
        assertTrue(Color.Black.isDarkEnoughForWhiteText())
        assertFalse(Color.White.isDarkEnoughForWhiteText())
        assertTrue(Color(0xFFFF525E).isDarkEnoughForWhiteText())
    }
}
