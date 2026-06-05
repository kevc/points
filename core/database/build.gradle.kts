plugins {
    id("points.kmp.library")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.points.core.database"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Android local unit tests run on the JVM, so the ledger is exercised there against an
        // in-memory JDBC SQLite driver — off-device and fast, no emulator needed.
        getByName("androidUnitTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("PointsDatabase") {
            packageName.set("com.points.core.database")
        }
    }
}
