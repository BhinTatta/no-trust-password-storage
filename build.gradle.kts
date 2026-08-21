plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false

    // Uncomment these two once opening this project somewhere with the
    // Android SDK (Android Studio, or CI with android-actions/setup-android).
    // They can't resolve without it — confirmed directly in the sandbox this
    // was written in, not assumed — and declaring them here breaks every
    // other Gradle task too, including the shared module's own jvm() tests,
    // which is why they're commented out rather than left in. See
    // docs/ARCHITECTURE.md for the full three-file activation checklist.
    // kotlin("plugin.compose") version "2.0.21" apply false
    // id("com.android.library") version "8.7.3" apply false
    // id("com.android.application") version "8.7.3" apply false
}
