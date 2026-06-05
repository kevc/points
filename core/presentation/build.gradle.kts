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
    // -lsqlite3: the SQLDelight native (SQLiter) driver pulled in via core:data calls into the
    // system libsqlite3, so the exported framework must link it.
    iosArm64().binaries.framework { baseName = frameworkName; isStatic = false; linkerOpts("-lsqlite3") }
    iosSimulatorArm64().binaries.framework { baseName = frameworkName; isStatic = false; linkerOpts("-lsqlite3") }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.data)
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
