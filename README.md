# No-Trust Password Storage

A self-custodied, zero-knowledge password manager, built Android-first
with a straight path to iOS. One master password, set once, that nobody —
not the developer, not a support team, not Google/Apple, not you if you
forget it — can ever recover. Everything is encrypted on-device before it
touches disk or the network, and the only sync destination is *your own*
Google Drive, which only ever sees ciphertext.

## Status

**Phase 0 (shared crypto/vault core) is implemented and tested** — see
`/shared`: Argon2id KDF, AEAD envelope encryption, the two-tier vault
format, and 31 passing tests (known-answer/regression, round-trip,
two-tier isolation, full CRUD). It currently builds and tests against the
`jvm()` target only, since this was built in an environment without the
Android SDK — see `docs/ARCHITECTURE.md` for exactly what's left to add
`androidTarget()` in Android Studio. No UI exists yet (Phase 1).

## Why

Password managers ask you to trust their servers, their recovery flows,
and their promise not to look. This removes the need for that trust
entirely: there is no backend, no account recovery, and no server that
ever sees your data in any form. Forgetting the master password means
losing the vault — same trade-off as a self-custodied crypto wallet.

## Core principles

- **Zero-knowledge**: only you hold the key. No "forgot password" flow.
- **No backend**: the app talks to Google Drive (your own account) and
  nothing else. No analytics, no telemetry, no third-party server.
- **Two-tier reveal, not one big unlock**: biometrics get you a fast
  glance at *what* you have saved (site names, aliases) — never the
  actual secrets. Seeing a real username/password always costs a master
  password entry, every single time, not just once per session.
- **Local-first, encrypted-at-rest**: the vault is a single encrypted
  container file (libsodium AEAD over a serialized, versioned format —
  see `docs/ARCHITECTURE.md` for why this replaced an earlier SQLCipher
  plan), synced as one opaque file.
- **Minimal, deliberate UI**: no generic template look. Typography-led,
  quiet, confident — see [`docs/UI_DESIGN.md`](docs/UI_DESIGN.md).

## How it works, day to day

1. Open the app → **biometric prompt** → you land on a searchable list of
   entries showing only **alias + site/service name**. Nothing sensitive
   is decrypted yet.
2. Search/filter that list instantly (it's just labels, cheap to decrypt).
3. Tap an entry → prompted for your **master password** → username and
   password decrypt and display for that one entry, briefly. Close it and
   you're back to needing the master password again to see it a second
   time.
4. Add a new entry manually, or use **OCR quick-add** to photograph a card
   or note and pre-fill the form.
5. Everything — including the built-in authenticator (TOTP) codes —
   auto-syncs to your own Google Drive in the background, fully encrypted.

## Feature summary

- Manual entry: website/service, username, password, alias, notes, tags
- Fast local search over aliases/site names (biometric-gated tier)
- Per-view master-password reveal for the actual username/password (no
  session-wide caching of secrets)
- Argon2id + libsodium AEAD envelope encryption; custom encrypted vault container
- Biometric unlock for the *browse* tier only, via Android Keystore/StrongBox
- Automatic encrypted sync to your own Google Drive (`drive.file` scope)
- On-device OCR quick-add (ML Kit) — no cloud OCR call, ever
- Built-in TOTP (2FA) generator, seeds encrypted and synced like everything else
- **Decoy/duress password** — configurable in Settings, opens a fake/empty vault
- Auto-lock, clipboard auto-clear, screenshot blocking, root/tamper warnings
- Optional recovery phrase (your own paper backup, never transmitted anywhere)

See [`docs/SECURITY.md`](docs/SECURITY.md) for the full threat model and
crypto design, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the
cross-platform (KMP) module layout, [`docs/ROADMAP.md`](docs/ROADMAP.md)
for the phased build plan and performance notes,
[`docs/UI_DESIGN.md`](docs/UI_DESIGN.md) for the visual direction, and
[`docs/TESTING.md`](docs/TESTING.md) for how correctness is verified
without relying on manual debugging.

## Tech stack

Built as **Kotlin Multiplatform (KMP)**: the crypto/vault/sync logic is
written once in a shared module and used by both the Android app today
and an iOS app later, while anything that must touch platform-specific
secure hardware is implemented natively per platform (there is no
portable API for "the phone's secure enclave" — Android and iOS expose
genuinely different hardware primitives, so this layer is intentionally
platform-specific rather than papered over by a framework).

| Layer | Choice | Why |
|---|---|---|
| Shared core | Kotlin Multiplatform module | One implementation of the vault format, crypto, and sync logic — no second copy to keep in sync (and no second copy to introduce a second set of bugs) when iOS arrives |
| UI (Android) | Jetpack Compose | Modern native Android UI, full control over look |
| UI (iOS, later) | SwiftUI | Native look/feel and native biometric UX on iOS, calling into the shared core |
| Local storage | Custom encrypted container (kotlinx.serialization + libsodium AEAD) | A vault this size (hundreds of entries) doesn't need a SQL engine; SQLCipher's KMP story requires fragile per-platform native linking (custom CocoaPods setup on iOS) for no real benefit here — see `docs/ARCHITECTURE.md` |
| Crypto primitives | libsodium (via a Kotlin/Native multiplatform binding) | One well-audited, battle-tested crypto library on both platforms instead of two different platform-native crypto stacks that could drift or be misused differently |
| KDF | Argon2id (via the same libsodium binding) | Memory-hard, GPU/ASIC-resistant password stretching, identical behavior on both platforms |
| Hardware-backed key storage | Android Keystore (StrongBox when available) / iOS Secure Enclave + Keychain | Platform-specific by necessity — this is the one layer that can't be shared |
| Biometrics | androidx.biometric `BiometricPrompt` / iOS `LocalAuthentication` | Platform-native biometric prompts, both bound to the platform's own secure key store |
| Sync | Google Drive REST v3, `drive.file` scope | Least-privilege, works identically from either platform's shared sync code |
| Background sync | WorkManager (Android) / `BGTaskScheduler` (iOS, later) | Platform-native reliable background execution |
| OCR | ML Kit Text Recognition (Android) / Vision framework (iOS, later), both on-device | No cloud call — text never leaves the device on either platform |

## Architecture — two-tier access model

```
                         Master Password
                                │
                        Argon2id(salt)
                                │
                                ▼
                          Master Key (memory, per-reveal only)
                                │
                             unwraps
                                ▼
                 Secrets DEK ──► username + password
                 (per entry, decrypted only for the
                  duration of a single reveal action)

     ─────────────────────────────────────────────────

                        Biometric prompt
                                │
                    Keystore/StrongBox key
                     (setUserAuthenticationRequired)
                                │
                             unwraps
                                ▼
                   Browse-index DEK ──► alias + site name
                   (decrypted once per app-unlock session,
                    used for the list + search only)
```

Two independent keys guard two independent tiers of the *same* vault
file. Compromising the biometric/Keystore path exposes only labels you
chose to be low-stakes (which services you have accounts with, and
whatever nickname you gave them) — never a username or password. That
always requires the master password, live, at the moment you ask to see it.
