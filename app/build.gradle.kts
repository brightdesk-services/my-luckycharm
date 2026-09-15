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

// Release signing credentials, also kept out of the repo. When a file is
// absent — anyone else's checkout, and F-Droid's builder — the release build
// simply produces an unsigned APK instead of failing, so the project still
// builds for a fork. F-Droid relies on that: it builds unsigned, then verifies
// its output against the signed APK published alongside the tag.
//
// The two distributions sign with *different* keys on purpose. `play` uses the
// Play upload key; `foss` uses a key dedicated to F-Droid, whose certificate
// F-Droid pins permanently in AllowedAPKSigningKeys. Keeping them apart means
// neither can be rotated or compromised on the other's behalf.
fun loadProps(name: String) = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { load(it) }
}
fun Properties.keystoreExists() =
    getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

val keystoreProps = loadProps("keystore.properties")
val fdroidKeystoreProps = loadProps("fdroid-keystore.properties")
val hasSigning = keystoreProps.keystoreExists()
val hasFdroidSigning = fdroidKeystoreProps.keystoreExists()

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
        versionCode = 5
        versionName = "1.4"

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
        if (hasFdroidSigning) {
            create("fdroid") {
                storeFile = rootProject.file(fdroidKeystoreProps.getProperty("storeFile"))
                storePassword = fdroidKeystoreProps.getProperty("storePassword")
                keyAlias = fdroidKeystoreProps.getProperty("keyAlias")
                keyPassword = fdroidKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Two distributions of the same app. `play` is the Play Store build and
    // carries PostHog; `foss` is what F-Droid builds and has no analytics
    // dependency, no analytics code and no INTERNET permission. The
    // applicationId is deliberately the same in both — they are the same app,
    // and F-Droid lists it under the id users already have.
    flavorDimensions += "distribution"

    productFlavors {
        create("foss") {
            dimension = "distribution"

            // Signed here rather than in buildTypes because the two flavors use
            // different keys — and a buildType signingConfig *overrides* a
            // flavor one, so setting it in `release` would silently sign both
            // flavors with the Play upload key. Absent the keystore this stays
            // null and the release APK comes out unsigned, which is exactly
            // what F-Droid's builder produces and compares against.
            if (hasFdroidSigning) {
                signingConfig = signingConfigs.getByName("fdroid")
            }
        }
        create("play") {
            dimension = "distribution"

            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }

            buildConfigField("String", "POSTHOG_API_KEY", "\"${posthog("POSTHOG_API_KEY")}\"")
            buildConfigField(
                "String",
                "POSTHOG_HOST",
                "\"${posthog("POSTHOG_HOST", "https://us.i.posthog.com")}\"",
            )
        }
    }


    buildTypes {
        release {
            // Deliberately no signingConfig: each flavor sets its own, and a
            // value here would override both.
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

    // AGP otherwise embeds a Google-encrypted blob of the dependency tree in
    // the APK's signing block. F-Droid's scanner rejects any extra signing
    // block outright — it is an opaque payload it cannot audit — and fails the
    // build with "found extra signing block 'Dependency metadata'".
    //
    // Left on for the bundle, which is what Play receives: Play reads it to
    // warn about known-vulnerable dependencies, and nothing there objects.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
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
    "playImplementation"(libs.posthog.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
