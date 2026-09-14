import java.util.Properties

// The PostHog project key is read from a gitignored file rather than baked into
// source. A checkout without it builds fine and simply starts with analytics
// off, so a fork never reports into this project's PostHog.
val posthogProps = Properties().apply {
    val file = rootProject.file("posthog.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun posthog(key: String, fallback: String = "") =
    (posthogProps.getProperty(key) ?: fallback).trim()

// Release signing credentials, also kept out of the repo. When the file is
// absent — anyone else's checkout — the release build simply produces an
// unsigned APK instead of failing, so the project still builds for a fork.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigning = keystoreProps.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.brightdesk.myluckycharm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.brightdesk.myluckycharm"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "POSTHOG_API_KEY", "\"${posthog("POSTHOG_API_KEY")}\"")
        buildConfigField(
            "String",
            "POSTHOG_HOST",
            "\"${posthog("POSTHOG_HOST", "https://us.i.posthog.com")}\"",
        )
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasSigning) signingConfigs.getByName("release") else null
            // Nothing here is reflection-heavy — the one dynamic lookup,
            // BrightnessRange.ofSystem, resolves framework resources in the
            // "android" package, which app resource shrinking never touches.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Exposes BuildConfig.VERSION_NAME so the Settings screen can show it
        // without hardcoding a second copy that would drift from this file.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.posthog.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
