# Database Design Specification

## Version 1.0.0

## 4. Database Architecture

### 4.1 Storage overview

| Storage area | Authority | Retained information | Not retained |
|---|---|---|---|
| Private preferences | Ordinary settings | Relock behavior and version 1.0.0 presentation choices | Credentials, sessions, protected package set, histories |
| Keystore-backed protected preferences | Authentication and encrypted-storage initialization | PIN verifier record, failed-attempt count, lockout deadline, biometric choice where protected, database-opening material | Raw PIN, biometric template, session, recovery credential |
| Encrypted relational database | Protected-application selection | Unique package identifiers selected for protection | Labels, icons, groups, schedules, events, diagnostics, metrics |
| Process memory | Runtime owners | Package-scoped sessions, current foreground identity, current lock request, current health | Any durable history |
| Android platform | Current platform facts | Usage Access grant, notification grant, biometric eligibility, installed application metadata | No application-owned duplicate treated as authoritative |

### 4.2 Access path

The permitted storage path is:

1. a screen or Android callback requests an application operation;
2. the owning authentication, settings, or protected-application responsibility validates the request;
3. the appropriate storage boundary performs the read or write;
4. storage-specific errors are translated into defined outcomes; and
5. visible or runtime state changes only after the durable result is known.

Screens, notifications, Usage Access polling, and biometric callbacks do not access database files or preference files directly.

### 4.3 Relational database role

The encrypted embedded relational database provides:

- durable protected package selection;
- unique package-identifier enforcement;
- atomic addition and removal;
- schema versioning;
- migration; and
- encrypted local persistence inside the application sandbox.

It does not evaluate protection policy or authentication. Record existence means selected for protection.

### 4.4 Protected preferences role

Keystore-backed protected preferences provide small, atomic records that must be available before or independently of ordinary database queries. They hold the PIN verifier, retry state, and the material required to open the encrypted database.

The database-opening material is not derived directly from the PIN. Changing the PIN therefore does not require re-encrypting the entire relational database.

### 4.5 Memory-only role

Package-scoped unlock sessions are process-local. Current foreground identity and active lock request are short-lived. Protection health is calculated from current storage and Android facts. None is restored from storage after process death or reboot.

### 4.6 Transaction boundaries

The retained relational operations are each atomic:

- add one protected package;
- remove one protected package; and
- migrate the supported schema.

There is no retained business operation that requires a transaction across protected preferences and the relational database. Setup and reset are sequenced so that an incomplete step remains visibly incomplete rather than relying on a distributed transaction.

### 4.7 Concurrency

Database initialization and migration are serialized before ordinary access. Protected package writes are serialized. Reads use the verified database and produce one consistent set.

The in-memory protected set is updated after database commit. It is not allowed to lead the durable store during protection-reducing changes.

### 4.8 Backup boundary

All application-controlled data is excluded from Android backup. No automatic restore is accepted on reinstall or another phone. A new installation begins unconfigured with new Keystore and database-encryption material.

### 4.9 Failure boundary

If credential storage is unavailable, authentication is unavailable. If database-opening material or the encrypted database is unavailable, the selected protected set is unknown rather than empty. If migration fails, normal database access does not begin.

Where bounded retry or supported migration cannot recover the data, the application directs the user to clear application data and complete setup again.
