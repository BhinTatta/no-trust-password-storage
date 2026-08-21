# Cross-Platform Architecture (KMP)

## Why Kotlin Multiplatform, specifically

The two mainstream ways to get "one codebase, two app stores" are Flutter/
React Native (share the UI and business logic, bridge to native for
platform APIs) or Kotlin Multiplatform (share only the logic layer,
write native UI per platform). For a security-critical app, that
difference matters more than usual:

- Flutter/RN route calls to platform crypto/biometric/secure-storage APIs
  through a bridge (Dart↔platform channel, or JS↔native module). That
  bridge is extra code, extra surface area, and — for the exact APIs this
  app depends on most (Keystore/StrongBox, Secure Enclave, BiometricPrompt/
  LocalAuthentication) — the community plugins are thinner and less
  battle-tested than the equivalent native code.
- KMP shares only what's genuinely shareable — the vault format, the
  encryption scheme, the entry model, search, sync logic — as plain Kotlin
  compiled natively for each target (JVM/Android and Kotlin/Native for
  iOS). The UI and anything touching hardware-backed security is written
  natively per platform, using each platform's own first-party APIs
  directly, with no bridge in between.

Net effect: less code to get right in the parts that must never be wrong
(the crypto), at the cost of writing the UI twice — an acceptable trade
for a password manager.

## Module layout (as built)

```
/shared                     (Kotlin Multiplatform module — jvm() target only for now, see below)
  /commonMain
    crypto/                 VaultCrypto (Argon2id KDF, AEAD encrypt/decrypt/wrap,
                             random generation, ASCII password validation),
                             EncryptedBox + its Base64 (de)serializers
    model/                  BrowseIndexItem, EntrySecrets — the two data
                             shapes for the two trust tiers
    vault/                  VaultFile (the on-disk/synced container format),
                             VaultSession (stateful unlock/reveal/CRUD),
                             BiometricKeyStore (the Phase 2 plug-in point)
  /commonTest
    crypto/                 VaultCryptoTest — determinism, sensitivity, a
                             pinned regression vector, AEAD round-trip/tamper tests
    vault/                  VaultFileTest — unlock, wrong-password, full CRUD,
                             and the two-tier isolation test

/androidApp                 (Phase 1) Jetpack Compose UI, consumes /shared
/iosApp                     (Phase 7, later) SwiftUI UI, consumes /shared via a
                             Kotlin/Native framework
```

`storage/`, `sync/`, and `ocr/` from the original sketch don't exist as
separate packages: the vault format turned out simple enough (see
`docs/SECURITY.md` — no SQLCipher, just a JSON container) that persistence
lives directly in `vault/`; Google Drive sync and OCR text-parsing land
here in Phase 3/4 once there's a UI to drive them.

`BiometricKeyStore` (in `vault/`) is a plain Kotlin `interface` in
`commonMain`, not an `expect`/`actual` pair — it's a pluggable strategy
(platform code hands the shared module an implementation) rather than a
compile-time per-platform swap, which is a slightly better fit here since
nothing in `commonMain` needs to construct one itself. Either pattern
keeps the same property that matters: this is the *only* place platform
divergence is allowed to live. Everything else in `commonMain`/`commonTest`
has no idea which platform it's running on.

## Why this module currently targets only `jvm()`

This was built in a Linux environment with no Android SDK and no Xcode.
Applying the Android Gradle Plugin without an installed SDK breaks Gradle
configuration for the *entire* build, not just the Android-specific parts —
so `androidTarget()` isn't in `shared/build.gradle.kts` yet. Instead, the
module targets `jvm()`, and the full `commonMain`/`commonTest` source sets
(the KDF, the AEAD, the vault format, all 31 tests) were written, compiled,
and run for real against that target — see the root `README.md` Status
section.

The `/androidApp` module (Phase 1) is now written in full — Compose UI for
vault creation/unlock, browse+search, per-entry reveal with auto-redact,
add/edit, delete, auto-lock on background, `FLAG_SECURE`, clipboard
auto-clear — but it's deliberately **not wired into this build**. Both
`androidTarget()` and `com.android.library`/`com.android.application`
require a real Android SDK to even *resolve*, and this sandbox's network
egress to `dl.google.com` (which hosts the Android Gradle Plugin and every
AndroidX/Compose artifact — confirmed by direct `curl`, not assumed) is
blocked. Declaring those plugins anywhere in the build — even behind
`apply false` — makes Gradle fail before running any task at all,
including the already-verified `:shared:jvmTest`. Rather than trade away
the ability to keep testing the crypto core for an Android target that
can't be checked here anyway, the activation is three small, clearly
marked, commented-out edits:

1. **Root `build.gradle.kts`** — uncomment the three plugin lines
   (`kotlin("plugin.compose")`, `com.android.library`, `com.android.application`).
2. **`shared/build.gradle.kts`** — uncomment `id("com.android.library")`,
   the `androidTarget { }` block, the `android { }` block, and the
   `androidMain` source set line.
3. **`settings.gradle.kts`** — uncomment `include(":androidApp")`.

Do this in Android Studio (or CI with the SDK installed) and sync — no
changes to `commonMain`/`commonTest` are needed, since the libsodium
binding and kotlinx.serialization both already publish Android artifacts
(confirmed against the actual published jars, not just the README, back
in Phase 0).

**Important caveat on `/androidApp` specifically**: unlike `/shared`,
which was compiled and tested for real (31 passing tests), the
`/androidApp` Compose code could not be build-verified in this
environment at all — Compose, AndroidX, and AGP are exclusively on
Google's Maven, which is unreachable here. The Kotlin logic was written
carefully and cross-checked against the already-tested `VaultSession`/
`VaultFile` API it calls into, and against version/DSL details confirmed
via web search rather than guessed, but it has not been compiled. Expect
to fix a handful of small issues (an import, a version bump Android
Studio suggests) on first sync — that's expected, not a sign anything
deeper is wrong.

iOS (`iosX64()`/`iosArm64()`/`iosSimulatorArm64()`) is Phase 7 in
`docs/ROADMAP.md` — deferred until there's an actual Mac to build on, since
Kotlin/Native's iOS targets require Xcode toolchain access that doesn't
exist in this environment either.

## What this means for build order

1. `/shared` (crypto + vault core) is done, tested, and verified — Phase 0, complete.
2. `/androidApp` (Phase 1 UI) is written and ready but unverified — activate
   it with the three-step checklist above in Android Studio, fix whatever
   Android Studio's first sync flags, then build/run on a device or emulator.
3. When iOS work starts (Phase 7), `/shared` is reused as-is again; only
   `/iosApp` and an `iosMain` implementation of `BiometricKeyStore` are new.
   The crypto and vault logic is not rewritten, just re-tested against the
   same shared test suite (see `docs/TESTING.md`).
