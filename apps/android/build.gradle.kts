plugins {
    id("points.android.application")
    alias(libs.plugins.compose.compiler)
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
}

dependencies {
    implementation(projects.core.presentation)
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.koin.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
