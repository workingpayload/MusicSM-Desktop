package com.example.musicsmd.youtube

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference

/**
 * The visible top-level windows of a browser process tree on Windows: lets the sign-in notice
 * when YouTube Music has loaded and close the window gracefully (so its cookies are written),
 * without attaching anything Google could detect. Elsewhere the user closes the window.
 */
internal object BrowserWindows {
    val supported: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun titles(process: Process): List<String> = if (!supported) emptyList() else windows(process).map { it.second }

    /** Asks every window to close, as clicking its close button would. */
    fun close(process: Process) {
        if (!supported) return
        windows(process).forEach { (window, _) ->
            User32.INSTANCE.PostMessage(window, WinUser.WM_CLOSE, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }
    }

    private fun windows(process: Process): List<Pair<WinDef.HWND, String>> {
        val pids = buildSet {
            add(process.pid())
            process.descendants().forEach { add(it.pid()) }
        }
        val found = mutableListOf<Pair<WinDef.HWND, String>>()
        val user32 = User32.INSTANCE
        user32.EnumWindows({ window, _ ->
            val owner = IntByReference()
            user32.GetWindowThreadProcessId(window, owner)
            if (owner.value.toLong() in pids && user32.IsWindowVisible(window)) {
                val buffer = CharArray(512)
                val length = user32.GetWindowText(window, buffer, buffer.size)
                found += window to Native.toString(buffer.copyOf(length))
            }
            true
        }, null)
        return found
    }
}
