// AGP 9 compiles Kotlin itself, so the kotlin-android plugin must not be applied.
// It pins KGP transitively; this raises it to match the Compose compiler plugin.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Version catalog accessors are unavailable in buildscript blocks; keep
        // this in sync with `kotlin` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
