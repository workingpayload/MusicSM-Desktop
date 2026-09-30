package com.example.musicsmd.playback

import java.io.File
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider

/**
 * Points vlcj at the libVLC shipped inside the app (see `prepareBundledVlc` in the build script),
 * so an installed copy of MusicSM plays without VLC being installed. Registered as a service in
 * `META-INF/services`, which is how vlcj collects discovery providers.
 *
 * When nothing is bundled (a dev machine without VLC, say) it stands aside and vlcj falls back to
 * its usual search of the system's VLC install.
 */
class BundledVlcDirectoryProvider : DiscoveryDirectoryProvider {
    override fun priority(): Int = PRIORITY

    override fun supported(): Boolean = libraryDir() != null

    override fun directories(): Array<String> = libraryDir()?.let { arrayOf(it.absolutePath) } ?: emptyArray()

    internal companion object {
        /**
         * Ahead of every place vlcj looks by itself (all negative: JNA path, install dirs, PATH) but
         * behind a vlcj config file (1), so someone who points vlcj at a VLC deliberately still wins.
         */
        const val PRIORITY = 0

        private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

        /**
         * The folder holding libvlc: `vlc/` on Windows (plugins in `vlc/plugins`), `vlc/lib/` on macOS
         * (plugins in `vlc/plugins`, i.e. `lib/../plugins`) — the layouts vlcj's discovery expects.
         */
        fun libraryDir(resourcesDir: String? = System.getProperty("compose.application.resources.dir")): File? {
            val vlc = File(resourcesDir ?: return null, "vlc")
            val dir = if (isMac) File(vlc, "lib") else vlc
            val library = if (isMac) "libvlc.dylib" else "libvlc.dll"
            return dir.takeIf { File(it, library).isFile }
        }
    }
}
