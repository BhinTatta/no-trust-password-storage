# Roadmap, Performance, and Extra Features

## Phased build plan

### Phase 1 — MVP (local only)
- Vault creation: set master password, generate salt + secrets DEK, show
  optional recovery phrase once.
- Manual CRUD entries (site name, username, password, alias, notes, tags).
- Browse-tier list + search (alias/site name only) — no biometrics yet,
  gated by master password for now.
- Per-entry reveal flow: master password prompt → decrypt just that entry
  → auto-clear after a short display window.
- Auto-lock on background/timeout.
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

## Performance notes

- **Browse tier is cheap**: the biometric-gated index (alias + site name)
  is small and decrypts once per session — instant list rendering and search.
- **Reveal cost is intentional**: Argon2id at ~0.5–1s per reveal is a
  deliberate, small friction cost, paid per view rather than per session.
  Benchmark actual parameters against a low-end target device on first run.
- **Search stays fast**: SQLCipher decrypts pages on the fly with hardware
  AES acceleration; FTS5 indexed search against the browse-tier index (not
  the encrypted secrets) is effectively instant even at thousands of entries.
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
| Encryption at rest across their infra | SQLCipher AES-256 page encryption, entirely on-device |
| Account recovery flows | Deliberately absent — that's the whole point |
| Server-side rate limiting on login | Client-side exponential backoff (no server to rate-limit) |
| Play Integrity / SafetyNet app attestation | Same API, used to detect a tampered clone of this app |
| Encrypted backups to their cloud | Client-encrypted file synced to *your* Drive |

The philosophical difference: Google's model still requires trusting Google
to enforce access control correctly and stay uncompromised. This app's
model removes that requirement — there is nothing intelligible for anyone,
including a compromised Google account or a coerced unlock, to hand over
beyond what you've explicitly chosen to expose at the browse tier.
