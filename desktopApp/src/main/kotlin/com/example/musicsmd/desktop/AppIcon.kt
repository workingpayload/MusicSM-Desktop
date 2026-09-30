package com.example.musicsmd.desktop

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The phone app's launcher icon (`ic_launcher_img`), cropped and rounded the way Android shows it,
 * shipped as PNGs in `resources/icons`. The installers use the `.ico` / `.icns` built from the same
 * artwork (`desktopApp/icons`).
 */
object AppIcon {
    private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /**
     * Every size, for [java.awt.Window.setIconImages]: Windows picks the closest one for the title
     * bar, taskbar and Alt+Tab, so none of them is a blurry downscale of a single large image.
     */
    val windowImages: List<BufferedImage> by lazy { SIZES.mapNotNull(::read) }

    /** A single size as a Compose painter, for APIs that take one (the tray, the window's fallback). */
    fun painter(size: Int): Painter? =
        read(size)?.let { BitmapPainter(it.toComposeImageBitmap(), filterQuality = FilterQuality.High) }

    private fun read(size: Int): BufferedImage? =
        AppIcon::class.java.getResourceAsStream("/icons/icon-$size.png")?.use(ImageIO::read)
}
