plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

// Version choices here (AGP 8.7.x, this Compose BOM) favor a pattern that's
// been stable and widely documented for a long time over whatever is
// bleeding-edge as you read this — this module could not be built/verified
// in the environment it was written in (see shared/build.gradle.kts).
// If Android Studio's first sync offers an upgrade, take it.
android {
    namespace = "com.notrust.vault.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.notrust.vault.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":shared"))
    // VaultRepository's own ThrottleState uses @Serializable/Json directly
    // (a small local-only file, separate from the vault itself) — this
    // module needs its own copy of the dependency, not just a transitive
    // one via :shared, since Gradle's `implementation` deps aren't exposed
    // to consumers by default.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // Using -extended rather than -core: it's a superset, and it's not
    // worth guessing which specific icons (ContentCopy, Edit, Delete,
    // ArrowBack, Add) the trimmed-down core set does or doesn't include
    // without a real build to check against. The APK-size cost of the
    // full icon pack is a non-issue for an app this size.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    // FileProvider, for the "Share" export option.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // FragmentActivity (BiometricPrompt requires one) + BiometricPrompt itself.
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // TOTP QR scanning: CameraX for the preview/frame pipeline, ML Kit for
    // on-device barcode decoding — nothing here ever leaves the device or
    // touches the network, same as every other "scan" feature in this app.
    // This, like AndroidBiometricKeyStore and the deferred OCR camera path
    // (see docs/ROADMAP.md Phase 4), is camera/device-integration code that
    // could not be exercised against a real camera in the environment this
    // was written in — expect it to be the piece most likely to need
    // on-device iteration.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
}
