# ADR-005 — Android Keystore (or equivalent) for Cryptographic Key Storage

**Status:** Accepted (baseline; as built) · **Date:** 2026-07-19 · **Source:** TAS §70/§71

## Context
Key material must never be extractable by the application process or backups.

## Decision
Master keys live in the Android Keystore; data keys are wrapped by Keystore-backed keys. As built: androidx.security-crypto MasterKey (Keystore-backed) protects the SQLCipher passphrase (EncryptedSharedPreferences) and file encryption keys (EncryptedFile).

## Consequences
Key rotation/retirement (FR-317..319) is not yet implemented — scheduled for M2 design, M5 delivery.

## Related requirements
FR-162/164, FR-317..319, NFR-SEC-004.
