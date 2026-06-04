plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kover)
}

// Aggregate coverage across all modules into a single report.
dependencies {
    kover(project(":core:domain"))
    kover(project(":core:presentation"))
}
