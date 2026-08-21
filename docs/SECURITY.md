# Security & Cryptographic Design

## Threat model

**In scope (defended against):**
- Google, or anyone with access to Google Drive infrastructure, reading vault contents.
- A stolen/lost, biometrically-unlocked phone — the attacker gets your list
  of *site names and aliases*, never a real username or password.
- An attacker who extracts the raw vault file (from Drive or the device)
  and attempts an offline brute-force/dictionary attack.
- Another app on the same device trying to read the vault file or clipboard.
- Someone repackaging/tampering with the APK to plant a backdoor.
- Casual shoulder-surfing / screenshots while browsing or revealing entries.
- Coercion to unlock the app (mitigated via the decoy/duress password).

**Out of scope (accepted risk, disclosed to the user):**
- A fully compromised, rooted device at the exact moment you type the
  master password to reveal a secret (any password manager loses to this).
- Forgetting the master password with no paper backup — the deliberate
  trade-off for non-recoverability.

## Two-tier access model

This is the central design decision, driven directly by the requirement
that biometrics should only ever grant *browsing*, never *reading secrets*:

| Tier | Unlocked by | Data exposed | Frequency |
|---|---|---|---|
| **Browse** | Biometric (fingerprint/face) | Alias, site/service name only | Once per app-unlock session |
| **Reveal** | Master password | Username + password for one entry | **Every single time**, no caching |

These are backed by two separate keys, both protecting data inside the
same vault file:

1. **Browse-index key** — an AES key generated in the Android Keystore
   with `setUserAuthenticationRequired(true)` and
   `setInvalidatedByBiometricEnrollment(true)` (StrongBox-backed where the
   device supports it). A successful `BiometricPrompt` unwraps this key for
   the current session, decrypting only a lightweight "index" table
   (alias + site name + entry id). This is what powers the list view and
   search. It never has access to the encrypted username/password columns.

2. **Secrets key (DEK)** — a random 256-bit key, itself wrapped by the
   **Master Key**, which is derived fresh from Argon2id every time you
   reveal an entry:
   `MasterKey = Argon2id(masterPassword, salt, m=64–256 MiB, t=2–3, p=1)`
   There is deliberately **no session-wide caching** of the Master Key or
   the unwrapped DEK. Tapping "reveal" on an entry prompts for the master
   password, derives the key, decrypts *that one entry's* username/password
   into memory just long enough to display/copy it, then zeroes it. Viewing
   a second entry, or the same entry again a minute later, asks again.
   This is intentionally more friction than a typical password manager —
   that friction is the point: a stolen unlocked phone still can't be used
   to dump credentials, only to browse names.

**Why biometrics can never become a backdoor to secrets:** the browse-index
key and the secrets DEK are cryptographically independent. There is no key
material anywhere in the Keystore, hardware or software, that unwraps the
secrets DEK. Only the Argon2id derivation from the master password can do
that, and that derivation only ever happens transiently, on demand.

**Master passwords are restricted to printable ASCII.** This was found
during implementation, not assumed up front: the JVM binding of the
libsodium library this app uses derives the native byte-length of the
password from Kotlin's `String.length` (a UTF-16 code-unit count) before
handing the password to native code separately. For any character outside
the single-UTF-16-unit-equals-single-byte ASCII range, those two counts can
disagree — a real risk of silently-wrong key material for a KDF, and
potentially *different* wrong material on different platforms/library
versions. Rather than accept that hazard, master (and decoy) passwords are
required to be printable ASCII, enforced in code before any password ever
reaches the KDF — the same convention hardware wallets use for seed
phrases, for the same reason.

## Non-recoverability, by design

There is no password-reset flow because there is no server to reset it on.
If you want a safety net, the app can optionally generate a **BIP39-style
12/24-word recovery phrase** at setup that independently wraps the secrets
DEK. Shown once, never transmitted or stored by the app — entirely your
responsibility, same model as a hardware crypto wallet.

## Decoy / duress password (Settings)

Configurable from **Settings → Security → Decoy password**:
- You set a second, different password (setup rejects a decoy password
  identical to your real one — the real vault is always tried first on
  unlock, so an identical decoy password would simply be unreachable).
- Unlocking with it (instead of the real master password) opens a
  separate, cosmetically identical, initially empty vault — you can seed
  it yourself with a few harmless-looking entries if you want.
- The decoy vault is its own independent vault file with its own DEK;
  there is no way to derive the real vault from the decoy one, and no UI
  element hints at the real vault's existence while the decoy is open.
- This is off by default and entirely optional — it exists for the
  "someone is forcing me to unlock my phone" scenario, not as a primary
  feature.
- **What this doesn't hide**: the real and decoy vaults are two separate
  files on disk (`vault.json`, `vault_decoy.json`). Someone with root or
  physical file access to the device can see that a second vault file
  exists, even if they can't tell which one (if either) is real without a
  password. True forensic deniability — making the two indistinguishable
  on disk — is a meaningfully bigger design (interleaved/padded
  ciphertext with no structural difference) than this feature's stated
  threat model (a coerced live unlock, not a forensic device search) calls
  for, so it's stated as a limit rather than solved for.

## Local storage: a custom encrypted container, not SQLCipher

Earlier drafts of this plan called for SQLCipher (encrypted SQLite). In
practice, SQLCipher's Kotlin Multiplatform story is genuinely fragile — it
needs custom CocoaPods linking on iOS and fights with SQLDelight's default
`linkSqlite` linker flags — for a database that, realistically, holds a
few hundred to a few thousand short entries. That's not a scale where a
SQL engine earns its complexity.

Instead, the vault is a single JSON file (see `shared/.../vault/VaultFile.kt`):
plaintext metadata (format version, KDF salt and parameters — none of
this is secret) plus a handful of ciphertext blobs (the wrapped DEKs, the
encrypted browse index, one independently-encrypted blob per entry's
secrets), each produced by libsodium's authenticated encryption
(XSalsa20-Poly1305 via `SecretBox`). "Search" is an in-memory filter over
the browse index once it's decrypted at unlock — at this data scale that's
microseconds, with none of SQLCipher's platform-linking risk.

## Google Drive sync — zero-trust by construction

- **`drive.file`** OAuth scope only: the app can see/write only files it
  created itself, never your general Drive contents.
- Uploads to the hidden **appDataFolder** by default, with an option to
  point at a normal visible folder if you want to browse/manually back up
  the ciphertext yourself.
- The **entire vault** — entries, aliases, and TOTP/2FA seeds alike — lives
  in the same encrypted container file and syncs as one unit. TOTP secrets
  get exactly the same secrets-tier protection as passwords: encrypted by
  the DEK, wrapped by the Master Key, never readable via biometrics alone.
- Conflict handling: each upload carries a monotonic revision counter and
  content hash. Drive's built-in revision history is an additional safety
  net; a genuine concurrent edit (two devices, both dirty before either
  synced) prompts you to pick a version rather than silently merging.

## Additional hardening

- **`FLAG_SECURE`** — blocks screenshots, screen recording, and the
  recents-list thumbnail while the app is open, in both browse and reveal states.
- **Clipboard auto-clear**: copied passwords clear after ~30s and are
  marked `EXTRA_IS_SENSITIVE` (Android 13+) so they don't appear in
  clipboard preview UI or history.
- **Auto-lock**: browse-tier session ends (Keystore session invalidated)
  after a short idle timeout or backgrounding; reveal-tier never persists
  past a single action regardless.
- **Rate-limited unlock**: exponential backoff on repeated wrong master
  passwords or biometric failures. No auto-wipe-on-failure.
- **Root/tamper detection**: non-blocking warning banner, your call whether to proceed.
- **Play Integrity API**: verifies at runtime this is the genuine,
  unmodified build — defends against a malicious repackaged clone.
- **No network calls other than Google Drive.** No analytics SDKs, no
  crash reporters that phone home, no ad/tracking libraries.
