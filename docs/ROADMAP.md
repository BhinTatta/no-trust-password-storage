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

### Phase 2 — Biometrics + decoy password — written, pending build verification
- **Shared module** (`VaultSession.fromBrowseDek`, `exportBrowseDekForBiometricSetup`)
  is verified for real — 3 new tests, 40 total, all passing. Biometric
  unlock can only ever reach the browse tier: `reveal`/`upsertSecret`
  still always take the master password fresh, no matter which unlock
  path got you there.
- **`AndroidBiometricKeyStore`**: Keystore-backed AES-GCM key
  (StrongBox-backed where available, falling back cleanly when it isn't),
  `setUserAuthenticationRequired`/`setInvalidatedByBiometricEnrollment`,
  wired to `BiometricPrompt.CryptoObject`. This is the single least-verified
  file in the whole project — no device or emulator existed to test
  against — see the warning at the top of that file and the activation
  checklist in `docs/ARCHITECTURE.md`.
- **Decoy password**: Settings → Security. A second, independent vault
  file (`vault_decoy.json`); unlocking tries the real vault first, then
  the decoy, so the flow is identical either way. Setup rejects a decoy
  password identical to the real one (otherwise it's unreachable, since
  the real vault always matches first). Caveat, stated plainly rather than
  oversold: this doesn't hide the *existence* of a second file from
  someone with root/file access to the device — true forensic
  deniability would need the two vaults' ciphertext to be
  indistinguishable on disk, which is meaningfully more complexity than
  this feature's stated threat model (a coerced unlock, not a forensic
  device search) asks for.
- **Rate-limited unlock**: `UnlockThrottle` (shared, tested) decides the
  delay; `VaultRepository` persists the attempt counter locally,
  non-synced. No attempt limit ever deletes anything — see docs/SECURITY.md.
- **Root/tamper banner**: `DeviceIntegrity` — su-binary paths, test-keys
  build tag, attached debugger. Non-blocking, and *will* trigger while
  running the app from Android Studio's debugger — that's an attached
  debugger doing exactly what it's detecting, not a bug.
- `FLAG_SECURE` and clipboard auto-clear already shipped in Phase 1.

### Phase 3 — Google Drive sync
- Google Sign-In, `drive.file` scope, appDataFolder upload/download.
- WorkManager background sync (on save, on foreground/background, periodic).
- Revision counter + conflict detection/prompt.

### Phase 4 — OCR quick-add
- **`OcrFieldGuesser` (shared, tested — 7 tests): done.** Pure text-in,
  guesses-out logic — no ML Kit/camera dependency, so unlike everything
  else past Phase 0 this could be written *and verified for real* in the
  sandbox. Writing its tests caught a real bug before it shipped: an
  early "just guess the first other line is the site name" fallback
  would misclassify a password as the site name whenever nothing looked
  like a domain, silently losing the actual password guess. Fixed by
  dropping the fallback — no domain-looking line means no site-name
  guess, which is the safer wrong answer here.
- **Not yet done**: ML Kit Text Recognition integration and the camera
  capture flow — Android/ML Kit-specific, needs Android Studio the same
  way the rest of `/androidApp` does.
- Captured photo discarded immediately unless the user opts to keep an
  encrypted thumbnail.

### Phase 5 — Built-in authenticator (TOTP)
- **HOTP/TOTP core (shared, tested — RFC 4226 + RFC 6238 vectors for
  SHA1/256/512, plus RFC 4648 Base32 and otpauth:// URI parsing): done.**
  HMAC itself is the one platform-specific piece (`expect`/`actual`,
  backed by `javax.crypto.Mac` on both `jvmMain` and `androidMain` —
  deliberately not hand-rolled, unlike everything else here) so the
  actual HOTP/TOTP math, truncation, Base32, and URI parsing stay pure
  Kotlin and fully verified against the RFCs' own published vectors.
- **A standalone authenticator feature, not attached to password
  entries: done.** This isn't a field on a password entry — it's its own
  peer destination (a "Codes" tab in the bottom nav, next to Vault), same
  as any real authenticator app. `TotpEntry(id, alias, seed)` lives in its
  own list, in its own encrypted blob (`VaultFile.totpVault`), same
  secrets DEK as password entries but a completely separate read/write
  path (`VaultSession.listTotpEntries`/`upsertTotpEntry`/`deleteTotpEntry`).
  A biometric-only session never reaches it, same as a password: the seed
  generates valid codes forever, so it gets no less protection than the
  credential it might back — but *unlike* a password, the whole list
  unlocks together with one master-password prompt per visit to the tab,
  not a per-code reveal, since nothing further is disclosed per-code once
  that prompt has already been answered.
- **Live, always-ticking codes with a shrinking pie-timer per entry, an
  optional alias (falls back to the QR's own issuer/label, or a generic
  placeholder), and delete-with-confirmation: done.**
- **QR scan + manual paste/import: done**, via CameraX + on-device ML
  Kit barcode decoding for the scan path, `TotpSeedParser` (accepts
  either a full `otpauth://totp/...` URI or a bare Base32 secret) for
  paste/import. Like `AndroidBiometricKeyStore` and the Phase 4 camera
  path above, this is the one part that's camera/device-integration
  code that couldn't be exercised against a real camera in the
  environment this was written in — expect it to need on-device iteration.

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
