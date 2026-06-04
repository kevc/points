plugins {
    id("points.kmp.library")
}

android {
    namespace = "com.points.core.domain"
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
