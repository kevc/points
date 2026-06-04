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
    iosX64().binaries.framework { baseName = frameworkName; isStatic = true }
    iosArm64().binaries.framework { baseName = frameworkName; isStatic = true }
    iosSimulatorArm64().binaries.framework { baseName = frameworkName; isStatic = true }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
