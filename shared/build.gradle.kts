plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// NOTE on targets: this module is designed to eventually target
// androidTarget() (Android) and iosX64()/iosArm64()/iosSimulatorArm64()
// (iOS), per docs/ARCHITECTURE.md. Right now it targets only jvm() because
// this development environment has no Android SDK or Xcode installed, and
// applying the Android Gradle Plugin without an SDK breaks configuration
// for the whole build. Everything in commonMain/commonTest below is written
// to be equally valid once those targets are added — add them in an
// environment that actually has the SDK/Xcode (Android Studio, or CI) and
// no source changes are needed here, only additional target blocks and
// per-platform `actual` implementations where noted.
kotlin {
    jvm()

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
    }
}
