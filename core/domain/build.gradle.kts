plugins {
    id("points.kmp.library")
}

android {
    namespace = "com.points.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Ports expose domain values over Flow; Instant is part of the ledger model.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
