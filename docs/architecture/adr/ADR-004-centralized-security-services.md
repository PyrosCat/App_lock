# ADR-004 — Centralize Security Services

**Status:** Accepted (baseline) · **Date:** 2026-07-19 · **Source:** TAS §71/Part III, SDS §13

## Context
Distributed crypto/auth logic invites inconsistent enforcement and duplicated primitives.

## Decision
A central Security Services subsystem (Security Coordinator, Cryptographic Service, Key Management, Secure Storage, Policy Engine, Audit — SDS §13) performs all cryptographic operations. Business components never implement crypto directly (TAS §16.1).

## Alternatives
Per-feature crypto (current: EncryptedFileStore + DatabaseKeyProvider + Argon2PinHasher are already narrowly scoped helpers — close in spirit, not yet a unified facade).

## Consequences
M2 consolidates existing crypto helpers behind the Security Service facade without changing algorithms (Argon2id, AES-256-GCM, SQLCipher all retained).

## Related requirements
FR-354, FR-161..180, NFR-SEC-001..005.
