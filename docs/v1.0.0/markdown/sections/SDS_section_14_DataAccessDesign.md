# Software Design Specification

## Version 1.0.0

## 14. Data Access Design

### 14.1 Storage boundaries

The software uses three persistent storage boundaries and one transient boundary:

| Boundary | Information | Access |
|---|---|---|
| Private preferences | Relock choice, haptic and reduced-motion preferences, and other ordinary version 1.0.0 settings | Settings and presentation through one settings boundary |
| Keystore-backed protected preferences | PIN verifier, verifier format, retry state, biometric preference where protected, and database-opening material | Authentication and storage initialization only |
| Encrypted relational database | Unique protected package identifiers | Protected-application persistence only |
| Process memory | Package sessions, authenticated settings state, current foreground target, active lock request, current health, and current diagnostics | Owning runtime responsibilities only |

Screens, Android callbacks, and notifications do not read or write these stores directly.

### 14.2 Protected-application access

The protected-application persistence boundary supports only the retained operations:

- read the complete protected package set;
- determine whether a package is selected;
- add a unique package identifier;
- remove a package identifier; and
- observe the committed set needed by presentation and protection.

Installed-app discovery and search do not become database queries. Display labels and icons remain Android-owned current information.

### 14.3 Settings access

Settings are read through one validated snapshot with documented defaults. An unknown or unavailable stored value falls back to a safe supported value, with immediate relock as the relock default.

Writing a setting validates that the value is one of the supported choices. A failed write leaves the last durable value active and tells the user that the change was not saved.

### 14.4 Authentication storage access

Credential and lockout operations are atomic at the logical level. PIN setup never exposes a configured state until a complete verifier record exists. Failure counting and lockout deadline are updated together as one authentication outcome.

Authentication storage returns success, not configured, unavailable, or invalid-format outcomes. It does not return internal paths, encryption metadata, or platform exceptions to presentation.

### 14.5 Transaction and snapshot consistency

Protected package addition and removal commit before the in-memory protection snapshot changes. When commit succeeds but snapshot refresh fails, protection re-reads the authoritative set before another decision.

A failed database write never produces a temporary unprotected state. If a user requests removal and the write fails, the package remains protected.

### 14.6 Caching

The in-memory protected set is a runtime snapshot, not an independent source of truth. It is loaded from the verified database at startup and updated after commits. It is discarded on process death.

Application labels and icons may use short-lived presentation caching already provided by the platform or user-interface toolkit. No persistent app-metadata cache is required.

### 14.7 Excluded data access

There are no version 1.0.0 repositories or stores for vault data, schedules, automation, backup, recovery material, notification history, security events, diagnostic history, metrics, app groups, or media.
