plugins {
    id("points.kmp.library")
}

android {
    namespace = "com.points.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        // Repository tests run on the JVM (Android local unit tests) against an in-memory JDBC driver.
        getByName("androidUnitTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
