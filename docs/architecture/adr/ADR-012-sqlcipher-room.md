# ADR-012 — SQLCipher (net.zetetic) as the Room-"(or equivalent)" Encrypted Persistence

**Status:** Accepted (as built) · **Date:** 2026-07-19 · **Source:** M0 decision; validated Phases 2–3

## Context
TAS §70 baseline specifies Room "(or equivalent)"; FR-162/164 require encrypted persistence. Since Phase 2 the app runs Room over SQLCipher (`net.zetetic:sqlcipher-android`), passphrase in EncryptedSharedPreferences, validated E2E including reboot/force-stop/fresh-install campaigns.

## Decision
Retain SQLCipher-encrypted Room as the standard persistence stack.

## Known constraints (documented for future maintainers)
- The classic `sqlcipher_export()` migration silently no-ops in this integration; plaintext→encrypted migration reads legacy rows via framework SQLite and reinserts (AppLockDatabase.build).
- The encrypted DB cannot be inspected via `run-as` + sqlite3; verification is behavioral + header checks.

## Consequences
Database Design Specification (future doc) must document the as-built schema; FR-229 integrity verification is added in M1/M2.
