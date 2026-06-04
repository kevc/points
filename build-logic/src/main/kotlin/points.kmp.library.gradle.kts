import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention for a Kotlin Multiplatform library module shared by the apps.
 * Targets Android + iOS; source sets stay in commonMain unless a module opts in.
 * The owning module sets its own `android.namespace`.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

configure<KotlinMultiplatformExtension> {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

configure<LibraryExtension> {
    compileSdk = version("android-compileSdk").toInt()
    defaultConfig {
        minSdk = version("android-minSdk").toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
