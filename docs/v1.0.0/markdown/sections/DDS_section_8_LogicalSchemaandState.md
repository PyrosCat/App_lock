# Database Design Specification

## Version 1.0.0

## 8. Logical Schema and State

### 8.1 Protected application set

The protected application set obeys the following invariants:

- every package identifier is required and nonblank;
- each normalized package identifier occurs at most once;
- record existence means selected for protection;
- adding an existing identifier is idempotent;
- removing an absent identifier is idempotent;
- the set contains no authoritative label, icon, app version, category, installation status, or user identifier;
- an update to an installed application preserves selection when the package identifier is unchanged; and
- a confirmed uninstall removes the active selection.

### 8.2 Settings state

Settings are updated in place. They do not require creation and modification timestamps, status, version, owner, or soft deletion. Each value is validated against the choices included in version 1.0.0.

An unknown relock value resolves to immediate. An unknown optional presentation value resolves to its documented default. An unavailable preference file produces defaults only for ordinary settings, never for credentials or database-opening material.

### 8.3 Authentication state

Authentication persistence has three logical states:

| State | Meaning | Allowed transition |
|---|---|---|
| Not configured | No complete protected verifier exists | Complete PIN creation |
| Configured | A complete readable verifier exists | Verify, replace after authentication, or clear all app data |
| Unavailable | Required protected data is missing, invalid, or cannot be decrypted | Bounded retry or destructive application-data clear |

An unavailable record is not converted into not configured without explicit destructive reset because doing so could bypass existing protection.

### 8.4 Lockout state

Lockout is available when the deadline is absent or expired and temporarily locked when the deadline is in the future. Defensive time handling caps the displayed remaining duration at the maximum defined wait.

Expiration may clear the deadline while preserving the consecutive-failure count so that the next failure uses the correct doubled wait. Successful PIN verification clears both.

### 8.5 No generic lifecycle columns

The database shall not add a universal identifier, status, creation time, modification time, last-access time, version, archive flag, owner, or soft-delete field to every record. A field is included only when a retained operation uses it.
