# Database Design Specification

## Version 1.0.0

## 11. Physical Storage Design

### 11.1 Private placement

All version 1.0.0 storage resides in application-private internal storage controlled by Android sandbox permissions. No database, preference file, journal, temporary migration artifact, or protected material is deliberately placed in shared or external storage.

The application exposes no content provider or exported storage service for this information.

### 11.2 Encrypted relational database

The relational database is one local encrypted database using the existing approved Android persistence framework and encrypted storage facility. The design requires:

- encryption before protected package identifiers reach physical storage;
- transaction support;
- a schema version;
- a unique package-identifier constraint;
- safe framework-managed journal behavior;
- serialized initialization and migration; and
- no destructive fallback after open failure.

The design does not require changing database technology, adding another database, or introducing field-level encryption in addition to full database encryption solely for the protected package identifier.

### 11.3 Physical protected application collection

The supported collection contains one row for each selected package. The minimum physical value is the package identifier.

An existing surrogate record identifier or legacy timestamp may remain when removing it would create extra migration risk, but version 1.0.0 does not depend on it. A dormant field does not become part of the supported logical contract.

No physical relationship is required to an application catalog, group, policy, schedule, security event, or notification.

### 11.4 Preferences

Ordinary and protected preferences use separate private stores or otherwise preserve equivalent separation of sensitivity. A component allowed to read an ordinary presentation preference is not thereby given access to the PIN verifier or database-opening material.

Protected preference values are authenticated and encrypted through the selected Keystore-backed facility. A failure to decrypt is surfaced; it does not return a plausible empty default for security state.

### 11.5 Memory-only structures

Runtime sessions and detector state may use in-memory maps or immutable snapshots. They have no disk serialization, saved-state representation, database entity, or backup representation.

Activity recreation may preserve harmless presentation state but must not write an unlock session or PIN input into saved-state storage.

### 11.6 Journal and temporary database files

Database journals and temporary files remain in private storage and receive the same backup exclusion as the primary database. The selected encrypted database mode must not expose protected package identifiers in plaintext journal content.

Temporary files created during an explicitly supported migration are removed after verified success. After failure, they are either retained only as needed for a safe retry or removed after the prior consistent database is restored. They are not presented as a backup.

### 11.7 File naming and implementation identifiers

Physical file, preference, collection, and field names are implementation details and are not fixed by this specification. They must be stable enough for supported migration and must not include user-provided content.
