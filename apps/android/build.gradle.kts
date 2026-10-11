plugins {
    id("points.android.application")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.screenshot)
}

android {
    namespace = "com.points.android"

    defaultConfig {
        applicationId = "com.points.android"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    // The Compose screenshot plugin reads this per-module DSL flag at apply time (the gradle.properties value
    // alone isn't enough); it enables AGP's `screenshotTest` source set + the validate/update render tasks.
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(projects.core.presentation)
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Compose Preview Screenshot Testing: previews live in the `screenshotTest` source set.
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
