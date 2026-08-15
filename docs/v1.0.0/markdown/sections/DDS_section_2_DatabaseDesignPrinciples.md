# Database Design Specification

## Version 1.0.0

## 2. Database Design Principles

### 2.1 One authoritative location

Each persisted value has one source of truth:

- ordinary settings reside in private application preferences;
- PIN verification, retry state, biometric preference where protected, and database-opening material reside in Keystore-backed protected preferences;
- protected package identifiers reside in the encrypted relational database; and
- sessions, current foreground identity, current protection health, and active lock presentation remain in memory or are derived from Android.

The application does not maintain a second persistent copy for convenience.

### 2.2 Data minimization

A value is persisted only when the included behavior cannot operate correctly after process death without it. The database does not retain display metadata that Android can provide again, histories that are not shown, or generic status and timestamp fields that have no retained use.

### 2.3 Storage chosen by sensitivity and structure

Small structured secrets and retry state use Keystore-backed protected preferences. The potentially changing protected package set uses an encrypted relational database with a uniqueness constraint. Ordinary nonsecret choices use app-private preferences. Runtime security state remains in memory.

No single store is forced to contain all information.

### 2.4 Atomic security changes

PIN setup, PIN replacement, failed-attempt state, protected package addition, protected package removal, and schema migration produce either the prior complete state or the new complete state. A partial state is not exposed as usable.

### 2.5 Fail-safe opening

The application validates protected material and schema compatibility before ordinary access. It does not create a replacement empty database after an unexplained open or migration failure. Losing the selected protected set would weaken the product’s promise and must be visible.

### 2.6 Privacy by default

Protected package choices are confidential. Credentials and database-opening material are secret. Usage Access observations are transient and are not written to storage. Notification and diagnostic behavior does not create a hidden usage history.

### 2.7 No unsupported recovery promise

Version 1.0.0 has no backup, restore, recovery password, remote copy, or key escrow. If Android Keystore material is lost or the encrypted database cannot be recovered through a supported migration, the user must clear application data and configure protection again.

### 2.8 Proportionate schema

The relational model is intentionally small. It does not need cross-domain relationships, foreign keys, soft deletion, generic lifecycle columns, version columns on every row, or a reusable event store. Existing dormant objects need not be removed merely to make the physical schema look smaller, but they are outside the supported contract.
