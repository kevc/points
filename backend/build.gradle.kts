import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // The Kotlin plugin is already on the classpath (build-logic), so apply it by id without a version
    // — an explicit version would conflict with the already-loaded one. Serialization and Kover are
    // distinct plugins and take their catalog versions.
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.points.backend.ApplicationKt")
}

dependencies {
    implementation(projects.shared.contract)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.h2)
    implementation(libs.hikaricp)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}
