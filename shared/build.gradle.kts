plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    // id("com.android.library") // uncomment together with the block below
}

// NOTE on targets: only jvm() is active. androidTarget() is written below,
// commented out, ready to enable — but this sandbox has no Android SDK,
// and network egress to dl.google.com (which hosts the Android Gradle
// Plugin and every AndroidX/Compose artifact) is blocked here, confirmed
// directly: declaring com.android.library anywhere in this build — even
// with `apply false` — makes Gradle fail to resolve it before running any
// task at all, including :shared:jvmTest. That's why this stays commented
// rather than applied: the jvm() target below is what's actually been
// compiled and tested for real (see git history — 31 passing tests), and
// keeping it working is worth more than a target that can't be verified
// here anyway.
//
// To activate Android (in Android Studio, or CI with the SDK installed):
//   1. In the root build.gradle.kts, uncomment the three commented plugin lines.
//   2. In this file, uncomment `id("com.android.library")` above, and the
//      androidTarget() line, the `android { }` block, and the `androidMain`
//      source set below.
//   3. In settings.gradle.kts, uncomment `include(":androidApp")`.
// No commonMain/commonTest changes are needed — they're already written to
// be target-agnostic.
kotlin {
    jvm()

    // androidTarget {
    //     compilerOptions {
    //         jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    //     }
    // }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings:0.9.5")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        // val androidMain by getting
    }
}

// android {
//     namespace = "com.notrust.vault.shared"
//     compileSdk = 34
//     defaultConfig {
//         minSdk = 26
//     }
//     compileOptions {
//         sourceCompatibility = JavaVersion.VERSION_11
//         targetCompatibility = JavaVersion.VERSION_11
//     }
// }
