import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":motionart"))
    implementation(project(":innertube"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.haze)
    implementation(libs.backdrop)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.jaudiotagger)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)

    // libVLC bindings for audio playback — Media3 is Android-only, so this is the desktop audio
    // engine. The installers carry libVLC itself (see prepareBundledVlc below).
    implementation(libs.vlcj)
    implementation(libs.jnativehook)
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    testImplementation(libs.junit)
}

// ---- App version ------------------------------------------------------------------------------
// One number for the installers and the app itself (Settings > About, the new-version check):
// -PappVersion, which the release workflow sets from the git tag (v1.2.3 -> 1.2.3).
val appVersion: String = providers.gradleProperty("appVersion").getOrElse("1.1.1")

abstract class GenerateAppVersion : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("com/example/musicsmd/app-version.txt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(version.get())
    }
}

val generateAppVersion = tasks.register<GenerateAppVersion>("generateAppVersion") {
    version.set(appVersion)
    outputDir.set(layout.buildDirectory.dir("generated/app-version"))
}

sourceSets.main { resources.srcDir(generateAppVersion) }

// ---- Bundled libVLC ---------------------------------------------------------------------------
// Playback goes through libVLC, so the installers carry it: nobody has to install VLC first. The
// files come from the VLC installed on the build machine (on CI, the release workflow installs
// it); only the parts an audio player needs are taken. At run time they sit in the app's resources
// dir (compose.application.resources.dir), and BundledVlcDirectoryProvider points vlcj at them.

val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

/** Where VLC is installed; -PvlcDir=<dir> overrides (on macOS, VLC.app/Contents/MacOS). */
val vlcInstall: File? = (
    providers.gradleProperty("vlcDir").orNull?.let(::File)
        ?: when {
            isWindows -> File(System.getenv("ProgramFiles") ?: "C:/Program Files", "VideoLAN/VLC")
            isMac -> File("/Applications/VLC.app/Contents/MacOS")
            else -> null
        }
    )?.takeIf { it.isDirectory }

/** Audio playback plus software video decoding/conversion for motion covers; GUI and most streaming-out stay out. */
val vlcPluginFolders = listOf(
    "access", "audio_filter", "audio_mixer", "audio_output", "codec", "demux", "keystore", "logger",
    "meta_engine", "misc", "packetizer", "stream_extractor", "stream_filter", "video_chroma", "video_filter",
    "d3d11", "d3d9",
)

/** Big plugins inside those folders that only video, discs or network protocols the app never uses need. */
val vlcPluginsLeftOut = listOf(
    // codec: video, image and subtitle decoders, and the MP2 encoder / MIDI synth
    "libx265", "libx264", "libx26410b", "libvpx", "libaom", "libdav1d", "libschroedinger", "libtheora",
    "liblibass", "libzvbi", "libaribsub", "libkate", "libdvbsub", "libscte27", "libcc", "libsubsdec",
    "libsubsusf", "libsdl_image", "libjpeg", "libpng", "libdxva2", "libd3d11va", "libqsv", "libcrystalhd",
    "libtwolame", "libfluidsynth",
    // access: discs, capture, remote desktops and streaming protocols
    "libaccess_srt", "libvnc", "libdcp", "liblibbluray", "libdshow", "libdtv", "libsftp", "libcdda", "librtp",
    "liblive555", "libnfs", "libdvdnav", "libdvdread", "libaccess_realrtsp", "libvcd", "libvdr",
    "libaccess_mms", "libsatip", "libscreen", "libshm", "libsmb", "libtimecode", "libaccess_wasapi",
    // demux: C64 and game-console music
    "libsid", "libgme",
    // misc: add-ons, scrobbling, exporting
    "libaddonsfsstorage", "libaddonsvorepository", "libaudioscrobbler", "libfingerprinter", "libexport",
    "libvod_rtsp",
)

val bundleVlc = providers.gradleProperty("bundleVlc").map { it.toBoolean() }.getOrElse(true)

/**
 * Copies libVLC into build/bundled-vlc/<os>/vlc, laid out the way vlcj's discovery expects. (No
 * plugin cache: the packaging steps copy the files again, the cache's file times stop matching, and
 * a valid cache would only save libVLC about 0.1 s at startup anyway.)
 */
abstract class PrepareBundledVlc @Inject constructor(private val fs: FileSystemOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val vlcFiles: ConfigurableFileCollection

    @get:Input
    abstract val osFolder: Property<String>

    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        if (vlcFiles.isEmpty) {
            if (required.get()) throw GradleException("No VLC install found to bundle; install VLC or pass -PvlcDir=<dir>.")
            logger.warn("No VLC install found to bundle; the app will need VLC installed on the system.")
            fs.delete { delete(outputDir) }
            outputDir.get().asFile.mkdirs()
            return
        }
        fs.sync {
            from(vlcFiles.asFileTree)
            into(outputDir.dir("${osFolder.get()}/vlc"))
        }
    }
}

val prepareBundledVlc = tasks.register<PrepareBundledVlc>("prepareBundledVlc") {
    osFolder.set(if (isWindows) "windows" else if (isMac) "macos" else "linux")
    required.set(providers.gradleProperty("requireVlc").map { it.toBoolean() }.getOrElse(false))
    outputDir.set(layout.buildDirectory.dir("bundled-vlc"))
    val install = vlcInstall
    if (bundleVlc && install != null) {
        if (isWindows) {
            vlcFiles.from(
                fileTree(install) {
                    include("libvlc.dll", "libvlccore.dll")
                    vlcPluginFolders.forEach { include("plugins/$it/**") }
                    include(
                        "plugins/video_output/libvmem_plugin.dll",
                        "plugins/video_output/libvdummy_plugin.dll",
                        "plugins/video_output/libdrawable_plugin.dll",
                    )
                    include("plugins/spu/libmarq_plugin.dll", "plugins/spu/liblogo_plugin.dll")
                    // Mix decodes track snippets to a WAV file, faster than real time.
                    include(
                        "plugins/stream_out/libstream_out_transcode_plugin.dll",
                        "plugins/stream_out/libstream_out_standard_plugin.dll",
                        "plugins/mux/libmux_wav_plugin.dll",
                        "plugins/access_output/libaccess_output_file_plugin.dll",
                    )
                    vlcPluginsLeftOut.forEach { exclude("plugins/**/${it}_plugin.dll") }
                    // The Blu-ray menus' Java helpers, left behind by the Blu-ray plugin above.
                    exclude("plugins/**/*.jar")
                },
            )
        } else if (isMac) {
            // macOS keeps every plugin in one folder, so all of them go (untested there: safer whole).
            vlcFiles.from(fileTree(install) { include("lib/**", "plugins/**") })
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.musicsmd.MainKt"
        // Packaging runs jpackage from this JDK, by default the one running Gradle. Android Studio's
        // bundled JDK has no jpackage, so pass a full JDK (17+, the version the app is compiled for)
        // with -PpackagingJdk=<path> to build the installer.
        providers.gradleProperty("packagingJdk").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            // The installer's trimmed Java runtime only gets the modules listed here; anything a
            // library reaches for that's missing fails only in installed builds. jdk.unsupported
            // (sun.misc.Unsafe) is what vlcj's video buffers need: without it animated artwork never
            // shows. The rest are what `suggestRuntimeModules` finds in the dependencies.
            modules("java.instrument", "java.management", "java.net.http", "jdk.dynalink", "jdk.unsupported")
            packageName = "MusicSM Desktop"
            packageVersion = appVersion
            // libVLC, from prepareBundledVlc: lands in the app's resources dir, where
            // BundledVlcDirectoryProvider points vlcj at it.
            appResourcesRootDir.set(prepareBundledVlc.flatMap { it.outputDir })
            // The phone app's launcher icon, generated from its ic_launcher_img (see AppIcon.kt).
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                // A Start-menu entry, so the installed app has an icon to click.
                menu = true
                // Fixed, so installing a newer MSI upgrades the old install instead of adding a second.
                upgradeUuid = "6f1c3a52-9d4e-4b8a-a7f2-3e5d8c1b0a94"
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "com.example.musicsmd"
            }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}
