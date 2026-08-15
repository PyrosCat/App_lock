# Database Design Specification

## Version 1.0.0

## 20. Verification and Design Rationale

### 20.1 Schema verification

Verification confirms:

- the encrypted database opens only with valid protected opening material;
- the current schema version is recognized;
- the protected package collection exists and is readable;
- package identifiers are required and unique;
- duplicate add and absent remove are idempotent;
- no foreign-key relationship is required by the supported data;
- no unsupported domain is needed to open or use the retained collection; and
- no silent empty database replaces a failed existing database.

### 20.2 Preference verification

Verification confirms:

- ordinary settings accept only supported values and use safe defaults;
- the PIN verifier is complete or treated as unavailable;
- no raw PIN is stored;
- failure count and deadline survive process death, force-stop, and reboot;
- successful PIN verification or current-PIN-authorized PIN replacement resets retry state;
- PIN replacement is atomic;
- biometric preference does not replace current platform eligibility; and
- database-opening material remains protected and unexported.

### 20.3 Migration verification

For every explicitly supported source schema, verification covers:

- exact source recognition;
- retained protected-package record count;
- package normalization and deduplication;
- retained setting conversion;
- encrypted target opening;
- target schema and uniqueness;
- interrupted migration before and after target commit;
- repeated startup after successful migration;
- removal of obsolete plaintext or temporary material after verification;
- preservation of the prior consistent source before target success; and
- failure behavior without destructive fallback.

### 20.4 Backup-exclusion verification

Tests demonstrate that Android backup and restore do not transfer:

- ordinary settings;
- protected preferences;
- PIN verifier or lockout state;
- database-opening material;
- encrypted database or journals; or
- protected package choices.

A new installation on another supported phone begins unconfigured.

### 20.5 Lifecycle verification

Verification covers:

- first install;
- setup interruption;
- PIN creation and replacement;
- failed attempts and lockout;
- protected-app add and remove;
- package update, uninstall, and reinstall;
- process death;
- screen-off;
- reboot;
- force-stop followed by user relaunch;
- application-data clear;
- App Lock uninstall and reinstall;
- Keystore invalidation;
- storage exhaustion; and
- database corruption.

### 20.6 Privacy verification

Storage and diagnostic inspection confirm that:

- no Usage Access history is persisted;
- no protected package name or identifier appears in notifications or ordinary logs;
- no biometric detail beyond the permitted preference is stored;
- no session, current foreground identity, or active lock request is persisted;
- no unsupported event, diagnostic, metric, media, backup, or export store is written; and
- the application declares and uses no Accessibility service.

### 20.7 Supported-phone verification

Storage creation, encrypted opening, protected preferences, migration, backup exclusion, reset, and Keystore-loss behavior are exercised across Android API levels 30–35 using available emulators and at least one physical Android phone for the security-critical path.

Behavior on tablets, foldables, Chromebooks, earlier APIs, work profiles, secondary users, and cloned applications does not expand the supported database contract.

### 20.8 Design rationale

The version 1.0.0 storage design is intentionally smaller than a general security suite. It keeps secrets in Keystore-backed protected storage, structured protected selections in one encrypted database, ordinary preferences in private settings, and authorization state in memory.

This division minimizes code, migrations, permissions, retention rules, and recovery paths while preserving the information required for a credible App Lock. Removing vault, backup, automation, event history, exports, and analytics eliminates entire data domains rather than leaving them as inactive obligations.

### 20.9 Completion statement

This database design is complete when every persisted value belongs to the included version 1.0.0 behavior, every security-relevant write is atomic, every supported migration is verified, all application data is excluded from Android backup, failures remain visible, and memory-only authorization cannot reappear after process death or reboot.

Capabilities listed in Section 1.3 remain outside this specification and require no schema, storage, migration, lifecycle, or verification work for version 1.0.0.
