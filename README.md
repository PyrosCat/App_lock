# App Lock (Android)

Android app locker — Phase 1: core locking system. See `docs/` for the full
requirements and the Technical Architecture Specification.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | App detection, PIN auth, basic locking, protected app management | **In progress** |
| 2 | Biometrics, encryption, intruder detection, security logs | Planned |
| 3 | Vault system | Planned |
| 4 | Automation (schedules, Wi-Fi, location) | Planned |
| 5 | Cloud backup, remote lock, premium | Planned |

## Getting started

1. Install [Android Studio](https://developer.android.com/studio) (bundles JDK + Android SDK).
2. Open this folder in Android Studio and let Gradle sync.
   - If prompted about a missing Gradle wrapper, let Studio generate it
     (Gradle 8.10+ / AGP 8.7).
3. Run the `app` configuration on a device or emulator (minSdk 26 / Android 8.0).

## First-run flow

1. Create a 4-digit PIN (stored as PBKDF2 hash in EncryptedSharedPreferences).
2. Enable the **App Lock protection** accessibility service when prompted —
   this is how the app detects foreground app launches.
3. Toggle apps in the list to protect them.
4. Open a protected app → the lock screen appears → enter PIN to continue.

## Architecture (Phase 1)

```
AccessibilityService (AppDetectionService)
        │  foreground package changed
        ▼
ApplicationLockEngine ──── logs ──► SecurityEventDao (Room)
        │
        ├── LockPolicyManager   — is this package protected? (in-memory cache over Room)
        ├── LockSessionManager  — is there a valid unlock session? (relock policy)
        │
        ▼  requires auth
LockScreenActivity (Compose PIN pad)
        │  verifyPin
        ▼
CredentialRepository (EncryptedSharedPreferences + PBKDF2)
```

Relock policies (in `LockSessionManager`): `IMMEDIATE`, `GRACE_10S`, `SCREEN_OFF`.
All sessions clear on screen-off.

DI is a hand-rolled service locator (`core/Graph.kt`) — swap for Hilt when the
module count grows.

## Deliberate Phase 1 simplifications

- Room DB is **not** yet SQLCipher-encrypted (it only stores package names +
  event log; the PIN hash is in EncryptedSharedPreferences). SQLCipher lands
  in Phase 2.
- PBKDF2 instead of Argon2id (Phase 2 per TAS §7.1).
- No foreground watchdog service yet; the accessibility service alone drives
  detection.
- No uninstall protection / device admin yet.
