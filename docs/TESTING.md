# Testing & Reliability Strategy

This app fails in a way most apps don't: if a bug corrupts or misencrypts
the vault, there is no support flow to fix it and no "reset password" to
fall back on. The testing strategy below exists specifically so that
correctness is verified automatically, in CI, rather than relying on
manual debugging after the fact.

## Principles

- **Fail loud, never fail silent.** An AEAD authentication-tag mismatch, a
  corrupt page, an unexpected schema version — all of these must surface
  a clear, specific error immediately. The one thing worse than "this
  operation failed" is "this operation silently returned wrong data."
- **Never let a single bug destroy the only copy.** Any operation that
  rewrites the vault file in place (master password change, decoy-vault
  setup, schema migration) first writes to a new file and only replaces
  the original after that new file has been read back and verified —
  never edited in place. Combined with the existing Drive sync history,
  a bad write is always recoverable from the previous version.
- **The shared module is the trust boundary.** Because the crypto/vault
  logic lives once in the KMP `shared` module, it gets one test suite
  that both platforms benefit from — a bug caught here is a bug caught for
  Android and iOS alike, and there's only one place to have gotten it right.

## Test layers

1. **Known-answer tests (KATs)** for every crypto primitive — Argon2id and
   the AEAD cipher — run against the official published test vectors from
   the algorithm specs, not just "encrypt then decrypt on my own data."
   This catches a wrong parameter or a subtly wrong library call that
   would otherwise still "work" against self-generated test data.
2. **Round-trip property tests**: for many randomly generated entries
   (random-length strings, unicode, empty fields, huge fields), assert
   `decrypt(encrypt(x)) == x` and `unwrap(wrap(key)) == key`, always. Run
   with a large number of random cases on every CI run, not a handful of
   hand-picked examples.
3. **Vault format fixtures**: every released schema version gets a
   checked-in sample vault file. Migration tests open every old fixture
   with the current code and assert the data comes out identical to a
   known-good expected result. This is what makes "update the app" safe —
   an old vault must always open correctly under a newer app version.
4. **Two-tier isolation tests**: explicit tests asserting the browse-index
   key can *never* decrypt secrets-tier ciphertext and vice versa — this
   is the core security property of the whole design, so it gets its own
   dedicated, always-run test rather than being an implicit side effect of
   other tests.
5. **Sync/conflict tests**: simulated concurrent edits (two "devices"
   writing before either has synced) assert the conflict detector always
   flags the divergence rather than silently picking one side.
6. **Platform instrumented tests**: the small `expect`/`actual` layer that
   touches real Android Keystore/StrongBox (and later, Secure Enclave) is
   tested with on-device instrumented tests, since it can't be tested in
   plain JVM unit tests — this is deliberately kept as the *only* part of
   the codebase that needs platform-specific test infrastructure.

## What "done" means for any change

- All existing tests pass, plus new tests added for the change itself —
  a change to crypto or vault-format code without new KATs/round-trip
  tests for it is not considered complete.
- Any change to the vault file format ships with a new fixture file and a
  migration test proving old vaults still open correctly.
- CI (GitHub Actions) runs the full shared-module test suite on every
  push; nothing is merged to `main` with a red test run.
