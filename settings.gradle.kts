pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "no-trust-password-storage"

include(":shared")
// ":androidApp" is added in Phase 1, once this is built in an environment
// that has the Android SDK installed (Android Studio, or CI with
// android-actions/setup-android) — see docs/ARCHITECTURE.md.
