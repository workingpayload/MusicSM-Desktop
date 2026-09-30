package com.example.musicsmd.ui.theme

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The colour extraction behind the mobile app's artwork theming, which is AndroidX Palette
 * (`androidx.palette:palette`, Apache License 2.0) — Android-only, so its algorithm is ported here
 * to plain Kotlin on ARGB pixels: the same 5-bit-per-channel median-cut quantizer
 * (`ColorCutQuantizer`, 16 colours, image scaled to a 112×112 area) and the same six targets with
 * the same scoring. Mobile always calls it as `Palette.from(bitmap).clearFilters().generate()`, so
 * no swatch filters are applied here either.
 */
internal object ArtworkPalette {

    class Swatch(val rgb: Int, val population: Int) {
        /** `[hue 0..360, saturation 0..1, lightness 0..1]`, as `ColorUtils.colorToHSL`. */
        val hsl: FloatArray by lazy { rgbToHsl(rgb) }
    }

    class Result(
        val swatches: List<Swatch>,
        val lightVibrant: Swatch?,
        val vibrant: Swatch?,
        val darkVibrant: Swatch?,
        val lightMuted: Swatch?,
        val muted: Swatch?,
        val darkMuted: Swatch?,
        val dominant: Swatch?,
    )

    /** Mobile's pick for an artwork accent: vibrant, else dominant, else dark vibrant, else muted. */
    fun accentRgb(argbPixels: IntArray, width: Int, height: Int): Int? {
        val palette = generate(argbPixels, width, height)
        return (palette.vibrant ?: palette.dominant ?: palette.darkVibrant ?: palette.muted)?.rgb
    }

    fun generate(argbPixels: IntArray, width: Int, height: Int): Result {
        val pixels = scaleDown(argbPixels, width, height)
        val swatches = quantize(pixels, MAX_COLORS)
        val dominant = swatches.maxByFirstOrNull { it.population }
        val used = HashSet<Int>()
        fun pick(target: Target): Swatch? {
            var best: Swatch? = null
            var bestScore = 0f
            for (swatch in swatches) {
                val hsl = swatch.hsl
                val eligible = hsl[1] in target.minSaturation..target.maxSaturation &&
                    hsl[2] in target.minLightness..target.maxLightness &&
                    swatch.rgb !in used
                if (!eligible) continue
                val score = score(swatch, target, dominant)
                if (best == null || score > bestScore) {
                    best = swatch
                    bestScore = score
                }
            }
            best?.let { used += it.rgb }
            return best
        }
        // Generated in AndroidX Palette's target order; each target claims its swatch exclusively.
        val lightVibrant = pick(Target.LIGHT_VIBRANT)
        val vibrant = pick(Target.VIBRANT)
        val darkVibrant = pick(Target.DARK_VIBRANT)
        val lightMuted = pick(Target.LIGHT_MUTED)
        val muted = pick(Target.MUTED)
        val darkMuted = pick(Target.DARK_MUTED)
        return Result(swatches, lightVibrant, vibrant, darkVibrant, lightMuted, muted, darkMuted, dominant)
    }

    // ---- Target scoring (androidx.palette.graphics.Target / Palette.generateScore) ----

    private class Target(
        val minSaturation: Float,
        val targetSaturation: Float,
        val maxSaturation: Float,
        val minLightness: Float,
        val targetLightness: Float,
        val maxLightness: Float,
    ) {
        companion object {
            private const val TARGET_DARK_LUMA = 0.26f
            private const val MAX_DARK_LUMA = 0.45f
            private const val MIN_LIGHT_LUMA = 0.55f
            private const val TARGET_LIGHT_LUMA = 0.74f
            private const val MIN_NORMAL_LUMA = 0.3f
            private const val TARGET_NORMAL_LUMA = 0.5f
            private const val MAX_NORMAL_LUMA = 0.7f
            private const val TARGET_MUTED_SATURATION = 0.3f
            private const val MAX_MUTED_SATURATION = 0.4f
            private const val TARGET_VIBRANT_SATURATION = 1f
            private const val MIN_VIBRANT_SATURATION = 0.35f

            val LIGHT_VIBRANT = Target(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f, MIN_LIGHT_LUMA, TARGET_LIGHT_LUMA, 1f)
            val VIBRANT = Target(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f, MIN_NORMAL_LUMA, TARGET_NORMAL_LUMA, MAX_NORMAL_LUMA)
            val DARK_VIBRANT = Target(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f, 0f, TARGET_DARK_LUMA, MAX_DARK_LUMA)
            val LIGHT_MUTED = Target(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION, MIN_LIGHT_LUMA, TARGET_LIGHT_LUMA, 1f)
            val MUTED = Target(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION, MIN_NORMAL_LUMA, TARGET_NORMAL_LUMA, MAX_NORMAL_LUMA)
            val DARK_MUTED = Target(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION, 0f, TARGET_DARK_LUMA, MAX_DARK_LUMA)
        }
    }

    // The default weights already sum to 1, so AndroidX's normalizeWeights() leaves them as-is.
    private const val WEIGHT_SATURATION = 0.24f
    private const val WEIGHT_LUMA = 0.52f
    private const val WEIGHT_POPULATION = 0.24f

    private fun score(swatch: Swatch, target: Target, dominant: Swatch?): Float {
        val hsl = swatch.hsl
        val maxPopulation = dominant?.population ?: 1
        return WEIGHT_SATURATION * (1 - abs(hsl[1] - target.targetSaturation)) +
            WEIGHT_LUMA * (1 - abs(hsl[2] - target.targetLightness)) +
            WEIGHT_POPULATION * (swatch.population / maxPopulation.toFloat())
    }

    // ---- Bitmap scaling (Palette.Builder.scaleBitmapDown, default resize area) ----

    private const val MAX_COLORS = 16
    private const val RESIZE_AREA = 112 * 112

    /** Nearest-neighbour downscale to [RESIZE_AREA], like `createScaledBitmap(..., filter = false)`. */
    private fun scaleDown(pixels: IntArray, width: Int, height: Int): IntArray {
        val area = width * height
        if (area <= RESIZE_AREA) return pixels.copyOf()
        val ratio = sqrt(RESIZE_AREA / area.toDouble())
        val w = ceil(width * ratio).toInt().coerceAtLeast(1)
        val h = ceil(height * ratio).toInt().coerceAtLeast(1)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy = ((y + 0.5) * height / h).toInt().coerceIn(0, height - 1)
            for (x in 0 until w) {
                val sx = ((x + 0.5) * width / w).toInt().coerceIn(0, width - 1)
                out[y * w + x] = pixels[sy * width + sx]
            }
        }
        return out
    }

    // ---- ColorCutQuantizer ----

    private const val WORD_WIDTH = 5
    private const val WORD_MASK = (1 shl WORD_WIDTH) - 1
    private const val COMPONENT_RED = -3
    private const val COMPONENT_GREEN = -2
    private const val COMPONENT_BLUE = -1

    private fun quantize(pixels: IntArray, maxColors: Int): List<Swatch> {
        val histogram = IntArray(1 shl (WORD_WIDTH * 3))
        for (i in pixels.indices) {
            val quantized = quantizeFromRgb888(pixels[i])
            pixels[i] = quantized
            histogram[quantized]++
        }
        var distinct = 0
        for (count in histogram) if (count > 0) distinct++
        val colors = IntArray(distinct)
        var index = 0
        for (color in histogram.indices) if (histogram[color] > 0) colors[index++] = color

        if (distinct <= maxColors) {
            return colors.map { Swatch(approximateToRgb888(it), histogram[it]) }
        }

        // Median cut: always split the box with the largest volume until there are maxColors.
        // Like AndroidX's splitBoxes(), a box that cannot be split ends the loop without being
        // offered back.
        val queue = PriorityQueue<Vbox>(maxColors) { a, b -> b.volume - a.volume }
        queue.offer(Vbox(0, distinct - 1, colors, histogram))
        while (queue.size < maxColors) {
            val vbox = queue.poll()
            if (vbox != null && vbox.canSplit()) {
                queue.offer(vbox.splitBox())
                queue.offer(vbox)
            } else {
                break
            }
        }
        return queue.map { it.averageColor() }
    }

    private class Vbox(
        private val lowerIndex: Int,
        private var upperIndex: Int,
        private val colors: IntArray,
        private val histogram: IntArray,
    ) {
        private var population = 0
        private var minRed = 0
        private var maxRed = 0
        private var minGreen = 0
        private var maxGreen = 0
        private var minBlue = 0
        private var maxBlue = 0

        init {
            fitBox()
        }

        val volume: Int get() = (maxRed - minRed + 1) * (maxGreen - minGreen + 1) * (maxBlue - minBlue + 1)

        fun canSplit(): Boolean = upperIndex + 1 - lowerIndex > 1

        fun fitBox() {
            var minR = Int.MAX_VALUE
            var minG = Int.MAX_VALUE
            var minB = Int.MAX_VALUE
            var maxR = Int.MIN_VALUE
            var maxG = Int.MIN_VALUE
            var maxB = Int.MIN_VALUE
            var count = 0
            for (i in lowerIndex..upperIndex) {
                val color = colors[i]
                count += histogram[color]
                val r = quantizedRed(color)
                val g = quantizedGreen(color)
                val b = quantizedBlue(color)
                if (r > maxR) maxR = r
                if (r < minR) minR = r
                if (g > maxG) maxG = g
                if (g < minG) minG = g
                if (b > maxB) maxB = b
                if (b < minB) minB = b
            }
            minRed = minR
            maxRed = maxR
            minGreen = minG
            maxGreen = maxG
            minBlue = minB
            maxBlue = maxB
            population = count
        }

        fun splitBox(): Vbox {
            val splitPoint = findSplitPoint()
            val newBox = Vbox(splitPoint + 1, upperIndex, colors, histogram)
            upperIndex = splitPoint
            fitBox()
            return newBox
        }

        private fun longestColorDimension(): Int {
            val redLength = maxRed - minRed
            val greenLength = maxGreen - minGreen
            val blueLength = maxBlue - minBlue
            return when {
                redLength >= greenLength && redLength >= blueLength -> COMPONENT_RED
                greenLength >= redLength && greenLength >= blueLength -> COMPONENT_GREEN
                else -> COMPONENT_BLUE
            }
        }

        private fun findSplitPoint(): Int {
            val longest = longestColorDimension()
            // Sort this box's colours along the longest dimension by making it the most
            // significant bits, sorting, and swapping back.
            modifySignificantOctet(colors, longest, lowerIndex, upperIndex)
            colors.sort(lowerIndex, upperIndex + 1)
            modifySignificantOctet(colors, longest, lowerIndex, upperIndex)
            val midPoint = population / 2
            var count = 0
            for (i in lowerIndex..upperIndex) {
                count += histogram[colors[i]]
                if (count >= midPoint) return minOf(upperIndex - 1, i)
            }
            return lowerIndex
        }

        fun averageColor(): Swatch {
            var redSum = 0L
            var greenSum = 0L
            var blueSum = 0L
            var total = 0
            for (i in lowerIndex..upperIndex) {
                val color = colors[i]
                val count = histogram[color]
                total += count
                redSum += count.toLong() * quantizedRed(color)
                greenSum += count.toLong() * quantizedGreen(color)
                blueSum += count.toLong() * quantizedBlue(color)
            }
            val r = (redSum / total.toFloat()).roundToInt()
            val g = (greenSum / total.toFloat()).roundToInt()
            val b = (blueSum / total.toFloat()).roundToInt()
            return Swatch(approximateToRgb888(r, g, b), total)
        }
    }

    private fun modifySignificantOctet(a: IntArray, dimension: Int, lower: Int, upper: Int) {
        when (dimension) {
            COMPONENT_RED -> Unit
            COMPONENT_GREEN -> for (i in lower..upper) {
                val color = a[i]
                a[i] = (quantizedGreen(color) shl (WORD_WIDTH + WORD_WIDTH)) or
                    (quantizedRed(color) shl WORD_WIDTH) or quantizedBlue(color)
            }
            COMPONENT_BLUE -> for (i in lower..upper) {
                val color = a[i]
                a[i] = (quantizedBlue(color) shl (WORD_WIDTH + WORD_WIDTH)) or
                    (quantizedGreen(color) shl WORD_WIDTH) or quantizedRed(color)
            }
        }
    }

    private fun quantizeFromRgb888(argb: Int): Int {
        val r = modifyWordWidth((argb shr 16) and 0xFF, 8, WORD_WIDTH)
        val g = modifyWordWidth((argb shr 8) and 0xFF, 8, WORD_WIDTH)
        val b = modifyWordWidth(argb and 0xFF, 8, WORD_WIDTH)
        return (r shl (WORD_WIDTH + WORD_WIDTH)) or (g shl WORD_WIDTH) or b
    }

    private fun approximateToRgb888(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or
            (modifyWordWidth(r, WORD_WIDTH, 8) shl 16) or
            (modifyWordWidth(g, WORD_WIDTH, 8) shl 8) or
            modifyWordWidth(b, WORD_WIDTH, 8)

    private fun approximateToRgb888(color: Int): Int =
        approximateToRgb888(quantizedRed(color), quantizedGreen(color), quantizedBlue(color))

    private fun quantizedRed(color: Int): Int = (color shr (WORD_WIDTH + WORD_WIDTH)) and WORD_MASK
    private fun quantizedGreen(color: Int): Int = (color shr WORD_WIDTH) and WORD_MASK
    private fun quantizedBlue(color: Int): Int = color and WORD_MASK

    private fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int {
        val shifted = if (targetWidth > currentWidth) {
            value shl (targetWidth - currentWidth)
        } else {
            value shr (currentWidth - targetWidth)
        }
        return shifted and ((1 shl targetWidth) - 1)
    }

    /** `ColorUtils.RGBToHSL`. */
    private fun rgbToHsl(rgb: Int): FloatArray {
        val rf = ((rgb shr 16) and 0xFF) / 255f
        val gf = ((rgb shr 8) and 0xFF) / 255f
        val bf = (rgb and 0xFF) / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val l = (max + min) / 2f
        var h: Float
        val s: Float
        if (max == min) {
            h = 0f
            s = 0f
        } else {
            h = when (max) {
                rf -> ((gf - bf) / delta) % 6f
                gf -> ((bf - rf) / delta) + 2f
                else -> ((rf - gf) / delta) + 4f
            }
            s = delta / (1f - abs(2f * l - 1f))
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        return floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /** Like AndroidX's `findDominantSwatch`: the first swatch with the strictly largest population. */
    private inline fun <T> List<T>.maxByFirstOrNull(selector: (T) -> Int): T? {
        var best: T? = null
        var bestValue = Int.MIN_VALUE
        for (item in this) {
            val value = selector(item)
            if (value > bestValue) {
                best = item
                bestValue = value
            }
        }
        return best
    }
}
