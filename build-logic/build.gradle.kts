plugins {
    `kotlin-dsl`
}

dependencies {
    // Put the Android, Kotlin, and Kover Gradle plugins on the classpath so the
    // precompiled convention plugins below can apply them by id.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
}
