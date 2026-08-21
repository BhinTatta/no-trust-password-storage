# Roadmap, Performance, and Extra Features

## Phased build plan

### Phase 0 — Shared core scaffolding — ✅ done
- KMP `shared` module set up (see `docs/ARCHITECTURE.md`): libsodium
  binding (Argon2id KDF + AEAD), the two-tier `VaultFile` container format,
  `VaultSession` for unlock/reveal/CRUD, and the `BiometricKeyStore` plug-in
  point for Phase 2.
- 31 tests passing (see `docs/TESTING.md`): determinism/sensitivity checks
  on the KDF, a pinned regression vector, AEAD round-trip and tamper
  detection, full vault CRUD, wrong-master-password handling, and the
  two-tier isolation property (browse DEK can never decrypt secrets and
  vice versa). Verified with a real `./gradlew :shared:jvmTest` run, not
  just written and assumed correct.
- Along the way: confirmed the exact libsodium binding API against its
  actual published source (not just its README, which had at least one
  stale detail) before writing code against it, and found — then designed
  around — a real cross-platform password-encoding hazard in that binding
  (see `docs/SECURITY.md`, "Master passwords are restricted to printable
  ASCII"). Also dropped an earlier SQLCipher-based storage plan in favor of
  a simpler custom container once SQLCipher's KMP integration turned out
  to be genuinely fragile (see `docs/ARCHITECTURE.md`).

### Phase 1 — MVP (Android, local only) — written, pending build verification
- `/androidApp`: Jetpack Compose, no Navigation library or ViewModel/DI
  framework — a plain sealed `Screen` state switch and coroutine-backed
  callbacks are all six screens need at this size (see docs/UI_DESIGN.md
  for the visual direction: dark, monospace vault content, one accent color).
- Vault creation: set master password (ASCII-validated), create the vault,
  unlock straight into it. (Recovery-phrase backup is not yet implemented —
  still optional/future, not blocking Phase 1.)
- Manual CRUD entries (site name, username, password, alias, notes) on top
  of `VaultSession` from Phase 0.
- Browse-tier list + search (alias/site name only) — gated by master
  password for now, since biometrics are Phase 2.
- Per-entry reveal flow: master password prompt → decrypt just that entry
  → 20s auto-redact countdown. Editing an entry is only reachable *after*
  revealing it (nothing to prefill otherwise, and blind-overwriting an
  unseen secret is bad hygiene) — deleting and renaming don't need the
  master password at all, consistent with the two-tier design.
- Auto-lock on backgrounding (`ON_STOP`), `FLAG_SECURE` to block
  screenshots/recording, and clipboard auto-clear (30s, marked sensitive
  on API 33+) on every copy.
- **Not build-verified**: this sandbox has no Android SDK and can't reach
  Google's Maven to get one — see `docs/ARCHITECTURE.md` for the exact
  three-file activation checklist and what to expect on first Android
  Studio sync.
- **Goal:** a fully usable, fully local password manager with the two-tier
  reveal model already in place, before biometrics or sync exist.

### Phase 2 — Biometrics + decoy password
- Android Keystore/StrongBox browse-index key, wired to `BiometricPrompt`.
- Settings → Security → decoy password setup flow (second password, second
  independent vault, seedable with dummy entries).
- `FLAG_SECURE`, clipboard auto-clear, rate-limited unlock attempts, root/tamper banner.

### Phase 3 — Google Drive sync
- Google Sign-In, `drive.file` scope, appDataFolder upload/download.
- WorkManager background sync (on save, on foreground/background, periodic).
- Revision counter + conflict detection/prompt.

### Phase 4 — OCR quick-add
- ML Kit Text Recognition integration, camera capture flow.
- Heuristic field-guessing (below), mandatory user confirmation before saving.
- Captured photo discarded immediately unless the user opts to keep an
  encrypted thumbnail.

### Phase 5 — Built-in authenticator (TOTP)
- Add TOTP seed entries alongside password entries, same secrets tier
  (master-password-gated reveal, same encrypted Drive sync).
- Live 6-digit code display only while the entry is in an active reveal state.

### Phase 6 — Audit & polish
- Independent review of the crypto core (Argon2id params, envelope
  encryption, Keystore usage, decoy-vault isolation).
- Consider open-sourcing the crypto/storage layer specifically so
  "no backdoor" is verifiable, not just claimed.

### Phase 7 — iOS (later, when/if needed)
- Add `iosApp` (SwiftUI) and the `iosMain` `actual` implementations for
  Secure Enclave/Keychain key wrapping and `LocalAuthentication` biometrics.
- Reuses the entire `shared` module — vault format, crypto, sync logic —
  unchanged. The existing shared test suite (`docs/TESTING.md`) runs
  against the same code, so this phase is additive, not a rewrite.

## Performance notes

- **Browse tier is cheap**: the biometric-gated index (alias + site name)
  is small and decrypts once per session — instant list rendering and search.
- **Reveal cost is intentional**: Argon2id at ~0.5–1s per reveal is a
  deliberate, small friction cost, paid per view rather than per session.
  Benchmark actual parameters against a low-end target device on first run.
- **Search stays fast**: the browse index is a plain in-memory list after
  unlock; an in-memory substring filter over a few thousand short strings
  is effectively instant, with none of the complexity a SQL engine would add.
- **Sync is cheap**: vault files at this scale are kilobytes to low
  megabytes; only sync when a content hash shows the file actually changed.

## OCR quick-add, in more detail

1. Tap "scan", photograph a card, sticky note, router label, etc.
2. ML Kit's on-device Text Recognition extracts raw text — nothing sent to
   any cloud OCR API.
3. Heuristics pre-fill the form: URL/domain-looking line → site name;
   email-looking line → username; long high-entropy string → password;
   everything else → notes.
4. User always reviews and confirms before saving — OCR only proposes.
5. The photo is discarded from memory/disk immediately after extraction
   unless the user explicitly opts to keep an encrypted thumbnail.

## Matching "how Google secures things"

| Google-style protection | This app's equivalent |
|---|---|
| Hardware security module (Titan M) for key storage | Android Keystore/StrongBox for the browse-tier key |
| Encryption at rest across their infra | libsodium AEAD encryption of the vault container, entirely on-device |
| Account recovery flows | Deliberately absent — that's the whole point |
| Server-side rate limiting on login | Client-side exponential backoff (no server to rate-limit) |
| Play Integrity / SafetyNet app attestation | Same API, used to detect a tampered clone of this app |
| Encrypted backups to their cloud | Client-encrypted file synced to *your* Drive |

The philosophical difference: Google's model still requires trusting Google
to enforce access control correctly and stay uncompromised. This app's
model removes that requirement — there is nothing intelligible for anyone,
including a compromised Google account or a coerced unlock, to hand over
beyond what you've explicitly chosen to expose at the browse tier.
