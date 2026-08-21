# UI Design Direction

Explicit goal: this should not look like a generic Material Design
CRUD-list-with-FAB app. It should feel closer to a crypto wallet or a
terminal-grade security tool — quiet, confident, deliberate — because
that's the mental model the whole product is built on.

## Visual language

- **Dark-first**, near-black background (not pure `#000`), single accent
  color used sparingly (only for the reveal action and unlock state — it
  should mean something, not decorate everything).
- **Typography does the work.** A distinctive monospace or semi-mono
  typeface for site names/usernames/passwords (reinforces "this is
  precise, technical data," and matches the crypto-wallet feel). One
  clean sans for UI chrome. No more than two typefaces, total.
- **Flat, no skeuomorphism.** No card shadows stacked on card shadows, no
  gradients-for-the-sake-of-it, no default Material `ElevatedCard`/`FAB`
  look straight out of the components catalog.
- **Generous whitespace, restrained density.** The entry list is names and
  aliases only (browse tier) — let that breathe; don't cram in preview
  icons/badges/metadata that isn't there yet anyway.
- **Motion with purpose only**: the transition from "browse" to "reveal"
  (master password prompt → decrypted value appearing) should feel like a
  distinct, deliberate state change — a subtle reveal animation, not a
  generic dialog pop-in. When a revealed value auto-clears, it should
  visibly fade/redact rather than just vanish, so you always know when
  you're no longer looking at live secrets.

## Screen-by-screen

- **Unlock screen**: master password field (or biometric prompt) front
  and center, nothing else competing for attention. No logo animation,
  no marketing copy.
- **Browse list**: alias (primary text) + site name (secondary, smaller/
  muted) + search bar pinned at top. Tapping a row does *not* reveal
  anything — it opens the entry detail, still redacted, with an explicit
  "Reveal" action.
- **Reveal state**: master password prompt appears inline (bottom sheet or
  contextual, not a jarring full-screen modal), then username/password
  render in the mono typeface with copy buttons; a visible countdown/timer
  indicator shows exactly how long until it auto-redacts.
- **Add/edit entry**: single clean form, OCR "scan" action available
  inline rather than buried in a menu.
- **Settings**: grouped, plain-language sections — Security (master
  password change, biometric toggle, decoy password setup, auto-lock
  timeout), Sync (Google account, last synced time, manual sync-now),
  Backup (export encrypted file, recovery phrase).

## What to explicitly avoid

- Default Material 3 dynamic color theming (looks like every other app on
  the phone) — pick a fixed, deliberate palette instead.
- Generic padlock/shield stock-icon imagery.
- A dashboard/stats screen for its own sake — this app has one job.
- Onboarding carousels/tutorials beyond the one-time vault-creation and
  recovery-phrase flow.
