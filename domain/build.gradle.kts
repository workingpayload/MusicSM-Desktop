import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, no framework/UI deps — mirrors the mobile app's domain module so the same
// models/interfaces/business rules (recommend, match, share) are shared verbatim.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":motionart"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
