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

To add Android: in an environment with the SDK (Android Studio, or CI with
`android-actions/setup-android`), add `androidTarget()` next to `jvm()` in
`shared/build.gradle.kts`, then add the `/androidApp` module. No changes
to `commonMain`/`commonTest` are needed — `androidTarget()` compiles the
exact same shared code, since the libsodium binding and kotlinx.serialization
both already publish Android artifacts (confirmed against the actual
published jars, not just the README, before writing any of this).

iOS (`iosX64()`/`iosArm64()`/`iosSimulatorArm64()`) is Phase 7 in
`docs/ROADMAP.md` — deferred until there's an actual Mac to build on, since
Kotlin/Native's iOS targets require Xcode toolchain access that doesn't
exist in this environment either.

## What this means for build order

1. `/shared` (crypto + vault core) is done and tested — Phase 0, complete.
2. Add `androidTarget()` + `/androidApp` next (Phase 1), in an environment
   with the Android SDK. `/shared`'s commonMain/commonTest carry over unchanged.
3. When iOS work starts (Phase 7), `/shared` is reused as-is again; only
   `/iosApp` and an `iosMain` implementation of `BiometricKeyStore` are new.
   The crypto and vault logic is not rewritten, just re-tested against the
   same shared test suite (see `docs/TESTING.md`).
