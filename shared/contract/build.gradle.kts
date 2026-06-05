plugins {
    id("points.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.points.shared.contract"
}

kotlin {
    // The backend is a plain kotlin-jvm module, so the contract needs a JVM variant in
    // addition to the android/iOS targets the convention provides.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
