import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Plain Kotlin/JVM for the same reason as :innertube — this module only talks HTTP and parses
// JSON, so keeping it off Android leaves the matching rules testable on a normal JVM.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
