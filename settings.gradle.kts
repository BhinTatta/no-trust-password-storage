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
// include(":androidApp")
// Uncomment the line above once building somewhere with the Android SDK —
// see the activation checklist in shared/build.gradle.kts and
// docs/ARCHITECTURE.md. The androidApp module's source is complete and
// ready; it just isn't wired into this build in an environment that can't
// verify it (see docs/ARCHITECTURE.md for why).
