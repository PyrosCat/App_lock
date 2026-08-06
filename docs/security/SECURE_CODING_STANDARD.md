# Secure Coding Standard (SCS) — v0 SKELETON

**Status:** **SKELETON / DRAFT — NOT YET AUTHORITATIVE.** Section headings and intent are in
place; normative rules are authored in **M2 (Core Security Platform)**. Do not treat a
placeholder section as an effective control. · **Started:** 2026-08-04 · **Owner:** project lead
· **Target:** SCS v1, an M2 deliverable (MIGRATION_ASSESSMENT §12; TAS §72.2).

The SCS defines the mandatory implementation-level security practices for the Android App Lock
codebase. It is the "how we write secure code" companion to the **Threat Model** ("what we defend
against") and is verified through static analysis, code review, and the security tests defined in
the Test Specification.

*(Placement: lives in `docs/security/` alongside the Threat Model — the authored security
governance & analysis area, distinct from the security requirements (SRS §8) and security
architecture (TAS Part III).)*

## Relationship to project documents (TAS §72.2)

- **SRS §8 (FR-161..180)** — the security requirements these rules implement.
- **TAS Part III — Security Architecture (§13–20; Cryptographic = §16)** — the architecture the
  rules must uphold. (Use `docs/architecture/tas/README.md` for §→part lookups.)
- **Threat Model** (`docs/security/tm/`, client-delivered 2026-08-05, 16 sections) — the threats each rule mitigates; rules trace to its threat IDs (THR-*).
- **Test Specification (§7.10 Security Testing; §8.23–8.25 security/pen/fuzz)** — how conformance is verified.
- **RTM** — each rule's verification status.
- **GOVERNANCE.md** — change control for this document.

## How to read this skeleton

Each section is a **placeholder**: a one-line statement of intent, the requirements it implements,
and known as-built concerns it must resolve. `M2:` marks what authoring must produce — normative
**MUST/SHOULD** rules, code examples, and the static-analysis or review check that enforces each.

---

## 1. Purpose & Scope
Intent: what the standard governs (all `app/**` Kotlin/Android code) and who it binds.
M2: scope statement, applicability, and the waiver process.

## 2. Secure-Coding Principles
Intent: secure defaults, least privilege, fail-safe/fail-closed, defense in depth, no secret in
code or logs, validate all untrusted input. M2: each principle as a normative rule with rationale.

## 3. Authentication & Credential Handling — FR-001..017, FR-174
Intent: Argon2id credential hashing, never persist plaintext credentials, clear credential
material from memory after use, lockout counters that survive process death.
**M2 must also resolve:** the PIN currently gates the UI only and is **not** bound to the
encryption keys (as-built finding) — decide whether the SCS requires PIN-derived key wrapping.

## 4. Cryptography & Key Management — FR-163, TAS §16
Intent: Android Keystore as root of trust; non-exportable keys; approved algorithms
(AES-256-GCM); no hardcoded keys/IVs; key rotation/retirement.
**M2 must also resolve:** `KeyPermanentlyInvalidatedException` handling (as-built gap — unhandled
Keystore invalidation is an unrecoverable data-loss path).

## 5. Secure Storage & Data at Rest — FR-162, FR-164, DDS
Intent: SQLCipher for the database, EncryptedFile / EncryptedSharedPreferences for blobs and
prefs; no sensitive data in plaintext files, caches, or logs.

## 6. Input Validation & Injection Resistance — TS §8.25 (fuzz)
Intent: validate/parameterize all untrusted input — SQL, intent extras, file paths (traversal),
deserialized data, imported vault content. Harden security-sensitive parsers.

## 7. IPC & Exported Components — TAS Part III, FR-179
Intent: minimal exported surface; permission-gate every exported component; validate all inbound
intents; never place sensitive data in intents or exported-component I/O.

## 8. Platform Integration Security — FR-167..171, FR-179, R-001
Intent: accessibility service, foreground services, permission monitoring, screen-capture
protection (FLAG_SECURE).
**M2 must also resolve:** anti-tapjacking / obscured-touch filtering (as-built gap); the
two-tier detection model (**ADR-013A**) — UsageStatsManager + overlay baseline security rules.

## 9. Session & Authorization Handling — FR-013..017
Intent: in-memory sessions only, never persist session/authorization state, relock on policy,
per-app authorization isolation.

## 10. Logging, Diagnostics & Error Handling — FR-173, FR-178
Intent: no sensitive data in logs or diagnostics; fail safe on error; no information leakage in
error messages; tamper-resistant audit logs.

## 11. Dependency & Supply-Chain Hygiene — TAS §52, §60; FR-247
Intent: pinned versions, vulnerability/license scanning, no unreviewed transitive additions;
keep R8/minification green (the Tink release-build break is the cautionary precedent).

## 12. Anti-Tampering, Integrity & Root Handling — FR-167..170
Intent: best-effort root/tamper/debug detection and configurable response, per the Threat Model's
accepted trust boundary (a rooted OS is out of scope for the security guarantee).

## 13. Secure Defaults & Configuration
Intent: `allowBackup=false`, FLAG_SECURE on sensitive screens, no debuggable release, least-
privilege manifest, no test/debug hooks in production builds.

## 14. Prohibited Practices
Intent: hardcoded secrets/keys; plaintext credential storage; disabling security controls to
"make it work"; logging sensitive data; catching-and-ignoring security exceptions; reflection to
bypass access controls.

## 15. Verification & Enforcement
Intent: how each rule is checked — detekt/ktlint/Konsist static rules, mandatory code review,
mapping to Test Specification security tests, CI gates. M2: name the enforcing mechanism per rule
(automated wherever possible).

## 16. Traceability
Intent: every rule traces to an FR/NFR and (once it exists) a Threat Model threat, plus its
verification evidence in the RTM.

## Revision history
- 2026-08-04 — v0 skeleton created (headings + intent + as-built hooks). Normative rules pending M2.
