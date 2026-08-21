# No-Trust Password Storage

A self-custodied, zero-knowledge password manager for Android. One master
password, set once, that nobody — not the developer, not a support team,
not Google, not you if you forget it — can ever recover. Everything is
encrypted on-device before it touches disk or the network, and the only
sync destination is *your own* Google Drive, which only ever sees
ciphertext.

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
  SQLCipher database file, synced as one opaque blob.
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
- Argon2id + AES-256-GCM envelope encryption; SQLCipher-encrypted local DB
- Biometric unlock for the *browse* tier only, via Android Keystore/StrongBox
- Automatic encrypted sync to your own Google Drive (`drive.file` scope)
- On-device OCR quick-add (ML Kit) — no cloud OCR call, ever
- Built-in TOTP (2FA) generator, seeds encrypted and synced like everything else
- **Decoy/duress password** — configurable in Settings, opens a fake/empty vault
- Auto-lock, clipboard auto-clear, screenshot blocking, root/tamper warnings
- Optional recovery phrase (your own paper backup, never transmitted anywhere)

See [`docs/SECURITY.md`](docs/SECURITY.md) for the full threat model and
crypto design, [`docs/ROADMAP.md`](docs/ROADMAP.md) for the phased build
plan and performance notes, and [`docs/UI_DESIGN.md`](docs/UI_DESIGN.md)
for the visual direction.

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| UI | Kotlin + Jetpack Compose | Modern native Android UI, full control over look |
| Local storage | SQLCipher (SQLite + transparent AES-256 page encryption) | Indexed encrypted search, syncs as one opaque file |
| KDF | Argon2id (argon2kt / BouncyCastle) | Memory-hard, GPU/ASIC-resistant password stretching |
| Key storage | Android Keystore (StrongBox when available) | Hardware-backed key for the biometric browse-tier |
| Biometrics | androidx.biometric `BiometricPrompt` | Standard, supports `CryptoObject` binding |
| Sync | Google Drive REST v3, `drive.file` scope | Least-privilege — app only sees files it created |
| Background sync | WorkManager | Reliable, battery-aware background jobs |
| OCR | ML Kit Text Recognition (on-device) | No cloud call — text never leaves the device |

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

Two independent keys guard two independent tiers of the *same* SQLCipher
file. Compromising the biometric/Keystore path exposes only labels you
chose to be low-stakes (which services you have accounts with, and
whatever nickname you gave them) — never a username or password. That
always requires the master password, live, at the moment you ask to see it.
