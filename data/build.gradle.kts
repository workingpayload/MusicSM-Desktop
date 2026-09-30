import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Concrete MusicSource: YouTube Music metadata (:innertube) + NewPipeExtractor audio
// resolution, verbatim from the mobile app's data/source/youtube package (only Android's
// `Log` was swapped for println — everything else is plain JVM already).
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))
    implementation(project(":innertube"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
    implementation("net.sf.kxml:kxml2:2.3.0")
    implementation("org.json:json:20240303")
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
