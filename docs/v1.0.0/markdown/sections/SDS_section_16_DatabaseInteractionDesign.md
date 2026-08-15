# Software Design Specification

## Version 1.0.0

## 16. Database Interaction Design

### 16.1 Database role

The encrypted embedded relational database is the authoritative persistent store for the selected protected package identifiers. It is not used for credentials, sessions, settings, installed-app metadata, histories, notifications, diagnostics, or excluded features.

### 16.2 Opening sequence

Before normal access, the application first identifies whether local storage is absent, current encrypted storage, or an explicitly supported earlier source format. A clean installation creates the current encrypted store. A current encrypted source follows the normal opening path below. A supported earlier source follows the migration sequence in the Database Design Specification before normal encrypted opening. An unknown source stops safely.

For a current encrypted source:

1. obtain the Keystore-protected database-opening material;
2. open the encrypted database in private application storage;
3. read the schema version;
4. complete any explicitly supported ordered migration;
5. verify that the protected-package collection and uniqueness constraint are usable; and
6. load the selected package identifiers.

Normal protection does not start from an Unknown or not verified state or a partially migrated database.

### 16.3 Operation contract

Database operations use parameterized statements through the retained persistence boundary. Package identifiers are validated before insertion. Duplicate insertion produces the already-present final state. Removal of an absent identifier produces the absent final state.

The protected-package set is small enough to load as a complete in-memory snapshot. No pagination, join, full-text search, sorting index, or generic query description is required.

### 16.4 Atomicity and concurrency

Each add or remove is one transaction. Schema migration is serialized and runs before ordinary access. Reads may occur concurrently only after database readiness is established.

The process contains one authoritative database instance. Callers do not open independent connections with different encryption or migration behavior.

### 16.5 Failure

An open, encryption, schema, integrity, or migration failure is translated to an unavailable local-configuration state. The application does not silently create an empty replacement and thereby discard protection.

When the failure cannot be repaired without the absent key or a supported migration, the only version 1.0.0 recovery is to clear application data and configure protection again.

### 16.6 Detailed persistence design

The Database Design Specification defines the supported information, constraints, migration, lifecycle, encryption, and verification. This section is authoritative only for the software interaction and sequencing described above.
