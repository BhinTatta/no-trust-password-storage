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

## Android activation history

This was built in a Linux sandbox with no Android SDK and no Xcode, and
with network egress to `dl.google.com` (which hosts the Android Gradle
Plugin and every AndroidX/Compose artifact) blocked — confirmed by direct
`curl`, not assumed. Declaring `com.android.library`/`com.android.application`
anywhere in the build, even behind `apply false`, made Gradle fail before
running any task at all, including `:shared:jvmTest`. For Phase 0 and most
of Phase 1/2, `androidTarget()` stayed commented out for exactly that
reason: the module targeted `jvm()` only, and the full `commonMain`/
`commonTest` source sets were written, compiled, and run for real against
that target.

**`androidTarget()`, `com.android.library`/`com.android.application`, and
`include(":androidApp")` are now active** — the three-file checklist that
used to live here has been applied. This build now needs the Android SDK
to configure at all, which the original sandbox never had; GitHub Actions
(`.github/workflows/build-apk.yml` and the updated `ci.yml`) is the
environment that actually has it, and is what verifies this build going
forward, including compiling the Compose UI for the first time. Android
Studio works too, locally, whenever you want it.

**Caveat on `/androidApp`**: it's now wired into the build and being
compiled for real by GitHub Actions (see `build-apk.yml`), which is the
first actual compiler check it's had — nothing in this sandbox could ever
build it. If that workflow is green, the Compose UI compiles; that's a
real, load-bearing signal, not a guess. `AndroidBiometricKeyStore`
specifically still has no way to be *test*-verified short of a real
device with biometrics enrolled — a green build only proves it compiles,
not that the Keystore/BiometricPrompt flow behaves correctly at runtime.

iOS (`iosX64()`/`iosArm64()`/`iosSimulatorArm64()`) is Phase 7 in
`docs/ROADMAP.md` — deferred until there's an actual Mac to build on, since
Kotlin/Native's iOS targets require Xcode toolchain access that doesn't
exist in this environment either.

## What this means for build order

1. `/shared` (crypto + vault core) is done, tested, and verified — Phase 0, complete.
2. `/androidApp` (Phase 1-2 UI) is written and now wired into the build —
   CI compiles it on every push; see the root README for how to get an
   installable APK without Android Studio at all.
3. When iOS work starts (Phase 7), `/shared` is reused as-is again; only
   `/iosApp` and an `iosMain` implementation of `BiometricKeyStore` are new.
   The crypto and vault logic is not rewritten, just re-tested against the
   same shared test suite (see `docs/TESTING.md`).
