# App Lock (Android)

Android app locker — Phases 1–2: core locking + security hardening. See
`docs/` for the full requirements and the Technical Architecture Specification.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | App detection, PIN auth, basic locking, protected app management | **Done** |
| 2 | Biometrics, Argon2id, SQLCipher, lockout, watchdog, uninstall protection | **Done** |
| 3 | Vault system, intruder selfie | Planned |
| 4 | Automation (schedules, Wi-Fi, location) | Planned |
| 5 | Cloud backup, remote lock, premium | Planned |

## Getting started

1. Install [Android Studio](https://developer.android.com/studio) (bundles JDK + Android SDK).
2. Open this folder in Android Studio and let Gradle sync.
   - If prompted about a missing Gradle wrapper, let Studio generate it
     (Gradle 8.10+ / AGP 8.7).
3. Run the `app` configuration on a device or emulator (minSdk 26 / Android 8.0).

## First-run flow

1. Create a 4-digit PIN (stored as an Argon2id hash in EncryptedSharedPreferences).
2. Enable the **App Lock protection** accessibility service when prompted —
   this is how the app detects foreground app launches.
3. Toggle apps in the list to protect them.
4. Open a protected app → the lock screen appears → PIN or biometrics to continue.

## Architecture (Phases 1–2)

```
AccessibilityService (AppDetectionService)          ProtectionWatchdogService
        │  foreground package changed               (FGS: alerts if the a11y
        ▼                                            permission is revoked)
ApplicationLockEngine ──── logs ──► SecurityEventDao (Room + SQLCipher)
        │
        ├── LockPolicyManager   — is this package protected? (in-memory cache over Room)
        ├── LockSessionManager  — is there a valid unlock session? (relock policy)
        ├── LockoutManager      — brute-force protection (5 tries, doubling delays,
        │                         counters persisted so restarts can't bypass)
        ▼  requires auth
LockScreenActivity (Compose PIN pad + BiometricPrompt + lockout countdown)
        │  verifyPin
        ▼
CredentialRepository (EncryptedSharedPreferences + Argon2id,
                      legacy PBKDF2 hashes upgrade on first verify)
```

Relock policies (in `LockSessionManager`): `IMMEDIATE`, `GRACE_10S`, `SCREEN_OFF`.
All sessions clear on screen-off; reboot clears them trivially (in-memory).

Phase 2 security notes:

- **Argon2id** via BouncyCastle's pure-Java implementation (OWASP params:
  19 MiB, t=2, p=1) — JVM-unit-testable, no native ABI baggage.
- **SQLCipher** (`net.zetetic:sqlcipher-android`) encrypts the Room DB; the
  passphrase is random, hex-encoded, and wrapped by the Android Keystore via
  EncryptedSharedPreferences. A Phase 1 plaintext DB is encrypted in place on
  first open (`sqlcipher_export`).
- **Lockout**: 5 failures → 30 s, doubling per failure, capped at 30 min
  (FR-009/FR-010/FR-174). State survives process restarts.
- **Biometrics**: `BIOMETRIC_WEAK` prompt with PIN fallback (FR-002/FR-007);
  hidden on unsupported hardware; toggle in Settings.
- **FLAG_SECURE** on lock screen + main UI in release builds only (debug stays
  screenshot-able for emulator E2E).
- **Uninstall protection**: opt-in device admin (Settings toggle, default off —
  note it also blocks `adb uninstall` while active).

DI is a hand-rolled service locator (`core/Graph.kt`) — swap for Hilt when the
module count grows.

## Remaining simplifications

- Failure threshold/delays are constants (`LockoutManager`), not yet
  user-configurable.
- No intruder selfie on lockout yet (Phase 3, hook is in
  `ApplicationLockEngine.onUnlockFailure`).
- Root/tamper detection (FR-167..170) not implemented.
- No pattern/knock-code auth methods (FR-004/FR-005).
