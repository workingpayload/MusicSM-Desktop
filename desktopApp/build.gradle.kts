import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    // libVLC bindings for audio playback (requires VLC installed on the host OS) — Media3 is
    // Android-only, so this is the desktop equivalent audio engine.
    implementation(libs.vlcj)
    implementation(libs.jnativehook)
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.example.musicsmd.MainKt"
        // Packaging runs jpackage from this JDK, by default the one running Gradle. Android Studio's
        // bundled JDK has no jpackage, so pass a full JDK (25+, the version the app is compiled for)
        // with -PpackagingJdk=<path> to build the installer.
        providers.gradleProperty("packagingJdk").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "MusicSM Desktop"
            packageVersion = "1.0.0"
            // The phone app's launcher icon, generated from its ic_launcher_img (see AppIcon.kt).
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                // A Start-menu entry, so the installed app has an icon to click.
                menu = true
            }
            macOS { iconFile.set(project.file("icons/icon.icns")) }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}
