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

## Module layout

```
/shared                     (Kotlin Multiplatform module)
  /commonMain
    vault/                  entry model, envelope encryption orchestration,
                             Argon2id KDF calls, libsodium bindings
    storage/                SQLDelight schema + queries (vault + browse index)
    sync/                   Google Drive REST client, conflict/revision logic
    ocr/                    text-parsing heuristics (site/username/password guessing)
                             — the OCR *engine* itself is platform-native (see below),
                             this is just the shared "what to do with the extracted text"
  /androidMain
    secure/                 Android Keystore + StrongBox key wrapping, BiometricPrompt glue
  /iosMain                  (added when iOS work starts)
    secure/                 Secure Enclave + Keychain key wrapping, LocalAuthentication glue

/androidApp                 Jetpack Compose UI, consumes /shared
/iosApp                     (added later) SwiftUI UI, consumes /shared via a
                             Kotlin/Native framework
```

The `secure/` package is defined with a Kotlin `expect` interface in
`commonMain` (e.g. `interface BiometricKeyStore { fun wrapKey(...); fun
unwrapKey(...) }`) and an `actual` implementation per platform. This is the
*only* place platform divergence is allowed to live — everything above it
(vault logic, entry model, sync, search) calls the same shared interface
and has no idea which platform it's running on.

## What this means for build order

1. Build `/shared` and `/androidApp` together first — there is no iOS
   target yet, so KMP costs nothing extra right now beyond the
   `expect`/`actual` boundary around the Keystore code, which we'd need to
   isolate cleanly regardless.
2. When iOS work starts, `/shared` is reused as-is; only `/iosApp` and the
   `iosMain` `actual` implementations are new. The crypto and vault logic
   — the part that must never have a subtle bug — is not rewritten, just
   re-tested against the same shared test suite (see `docs/TESTING.md`).
