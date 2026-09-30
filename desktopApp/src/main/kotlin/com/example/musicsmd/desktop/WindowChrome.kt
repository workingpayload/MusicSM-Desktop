package com.example.musicsmd.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.RootPaneContainer

/**
 * Paints the native Windows title bar and window border in the app's background colour, so the
 * frame reads as part of the dark UI instead of a white strip around it.
 *
 * Uses DWM window attributes: immersive dark mode (Windows 10 1809+) for dark caption buttons, and
 * on Windows 11 the exact caption, text and border colours. Anything unsupported is ignored, so
 * older systems simply keep the default frame. No-op on macOS/Linux.
 */
object WindowChrome {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val dwm: Dwmapi? by lazy {
        if (!isWindows) null else runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
    }

    fun apply(window: Window, background: Color, text: Color) {
        val awtBackground = java.awt.Color(background.toArgb() and 0xFFFFFF)
        // The AWT background shows for a frame while the window is resized; keep it dark too.
        window.background = awtBackground
        // A window whose size in pixels isn't a whole number of dp (maximised at 125 % scaling,
        // say) leaves a pixel row or column that Compose's canvas doesn't reach, and the Swing
        // panels behind it paint that in their light default colour: a white line along the edge.
        (window as? RootPaneContainer)?.rootPane?.let { paintSwingBackgrounds(it, awtBackground) }
        window.repaint()
        val api = dwm ?: return
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
        runCatching {
            if (api.set(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) {
                api.set(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1)
            }
            api.set(hwnd, DWMWA_CAPTION_COLOR, background.toColorRef())
            api.set(hwnd, DWMWA_BORDER_COLOR, background.toColorRef())
            api.set(hwnd, DWMWA_TEXT_COLOR, text.toColorRef())
        }
    }

    private fun Dwmapi.set(hwnd: Pointer, attribute: Int, value: Int): Int =
        DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)

    /** Every Swing container under [component]; Compose's own canvas (skiko) is left alone. */
    private fun paintSwingBackgrounds(component: Component, color: java.awt.Color) {
        if (component.javaClass.name.startsWith("org.jetbrains.skiko")) return
        component.background = color
        if (component is Container) component.components.forEach { paintSwingBackgrounds(it, color) }
    }

    /** Win32 COLORREF is 0x00BBGGRR. */
    private fun Color.toColorRef(): Int {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
