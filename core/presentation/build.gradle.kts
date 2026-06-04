plugins {
    id("points.kmp.library")
    alias(libs.plugins.skie)
}

android {
    namespace = "com.points.core.presentation"
}

kotlin {
    // Export an iOS framework consumed by the SwiftUI app. SKIE (applied above)
    // rewrites the Obj-C interface into idiomatic Swift (async/await, sealed
    // enums, AsyncSequence) when the framework is linked.
    val frameworkName = "PointsKit"
    iosArm64().binaries.framework { baseName = frameworkName; isStatic = false }
    iosSimulatorArm64().binaries.framework { baseName = frameworkName; isStatic = false }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(libs.decompose)
            api(libs.mvikotlin)
            implementation(libs.mvikotlin.main)
            implementation(libs.mvikotlin.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
