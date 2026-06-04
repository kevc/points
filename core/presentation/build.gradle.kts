plugins {
    id("points.kmp.library")
}

android {
    namespace = "com.points.core.presentation"
}

kotlin {
    // Export an iOS framework consumed by the SwiftUI app. SKIE is layered on in
    // the iOS scaffold task (#12), where it can be validated against full Xcode.
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
