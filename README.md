# App Lock (Android)

[![CI](https://github.com/PyrosCat/App_lock/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/PyrosCat/App_lock/actions/workflows/ci.yml)

Android app locker: PIN/biometric gating of protected apps, encrypted media vault, and
intruder-selfie capture — local-only, encrypted at rest.

**2026-07-19: the project was re-baselined** onto a new authoritative documentation set
(SRS 375 FRs, NFR 171 requirements, TAS, SDS, Implementation Strategy). See
[docs/process/MIGRATION_ASSESSMENT.md](docs/process/MIGRATION_ASSESSMENT.md) for the full
transition analysis and [docs/process/rtm/rtm.csv](docs/process/rtm/RTM.md) for per-requirement
status.

## Status

Implemented and E2E-validated (pre-rebaseline Phases 1–3, commits `61f1db4`…`1dc9e25`):
core locking (accessibility detection, PIN + biometric auth, relock policies), security
hardening (Argon2id, SQLCipher, lockout, watchdog, opt-in uninstall protection), encrypted
vault, and intruder selfie.

Lifecycle position under the new Implementation Strategy (phases 0–6):

| IS Phase | Scope | Status |
|---|---|---|
| 0 Foundation | CI/CD, static analysis, Hilt DI, build variants, docs governance | **← current (retrofit, migration M0–M1)** |
| 1 Core Security Platform | Auth, crypto, lock engine, sessions | Built; formal gate review pending (M2) |
| 2 Core App Features (MVP) | Protected apps ✓, vault ✓, settings ◐, backup ✗, onboarding ✗ | Partial (M3) |
| 3 Automation | Schedules, Wi-Fi/Bluetooth/location rules, rule engine | Planned (M4; design input in docs/process/PHASE4_PLAN.md) |
| 4 Production Hardening | Observability, resilience, data lifecycle, scalability | Planned (M5) |
| 5–6 Security Hardening & Release | Verification campaigns, release governance, v1.0.0 | Planned (M6) |

Migration phase **M0 (baseline & governance) is complete** — docs restructured, RTM and ADR log
established. Next: M1 foundation retrofit (CI, Hilt, variants, regression harness).

## Documentation map

| Path | Contents |
|---|---|
| `docs/srs/` | Software Requirements Specification, sections 1–18 (FR-001..375) |
| `docs/nfr/` | Non-Functional Requirements, sections 0–13 |
| `docs/architecture/tas/` | Technical Architecture Specification, parts 1–9 |
| `docs/architecture/adr/` | Architecture Decision Records (ADR-001..015, index in README) |
| `docs/design/sds/` | Software Design Specification, sections 1–17 |
| `docs/process/` | Implementation Strategy, migration assessment, plans, RTM |
| `docs/testing/` | Validation campaign records (Phase 3) and the automation test plan |
| `docs/archive/` | Superseded docs — **note the FR-226..250 renumbering notice** |

`changelog.txt` (repo root) carries human-readable change detail; commit subjects stay one line.

## Getting started

1. Install [Android Studio](https://developer.android.com/studio) (bundles JDK + Android SDK).
2. Open this folder in Android Studio and let Gradle sync (Gradle 8.10+ / AGP 8.7).
3. Run the `app` configuration on a device or emulator — minSdk 26 / targetSdk 35 (ADR-014).

First-run flow: create a PIN (Argon2id hash in EncryptedSharedPreferences) → enable the
**App Lock protection** accessibility service → toggle apps to protect → opening a protected
app shows the lock screen (PIN or biometrics). Optional: intruder selfie (Settings, needs
CAMERA) and the encrypted vault (photo-library icon in the app list).

## Architecture (as built)

```
AccessibilityService (AppDetectionService)          ProtectionWatchdogService
        │  foreground package changed               (FGS: alerts if the a11y
        ▼                                            permission is revoked)
ApplicationLockEngine ──── logs ──► SecurityEventDao (Room + SQLCipher)
        │                    └────► IntruderCaptureManager (threshold → front-camera
        │                           JPEG → EncryptedFile + event row + notification)
        ├── LockPolicyManager   — is this package protected? (in-memory cache over Room)
        ├── LockSessionManager  — valid unlock session? (IMMEDIATE / GRACE_10S / SCREEN_OFF)
        ├── LockoutManager      — brute-force protection (persisted counters, FR-174)
        ▼  requires auth
LockScreenActivity (Compose PIN pad + BiometricPrompt + lockout countdown)
        ▼
CredentialRepository (EncryptedSharedPreferences + Argon2id;
                      legacy PBKDF2 hashes upgrade on first verify)

Vault: SAF import → AES-256-GCM EncryptedFile blobs (UUID names) + SQLCipher index rows;
byte-identical export; secure delete. Self-gate re-locks the app's own UI on resume (FR-108).
```

Security notes: Argon2id (BouncyCastle, OWASP params m=19 MiB t=2 p=1); SQLCipher passphrase
random + Keystore-wrapped; Phase-1 plaintext DBs migrate by read-and-reinsert (see ADR-012 —
`sqlcipher_export` silently fails in this integration); FLAG_SECURE in release builds;
uninstall protection is opt-in device admin.

Known deviations scheduled for migration (see ADRs): `core/Graph.kt` service locator → Hilt
(ADR-015, M1); destructive-migration fallback removal (ADR-007, M1); root/tamper detection and
pattern/knock auth not yet implemented (RTM rows FR-167..170, FR-004/005).
