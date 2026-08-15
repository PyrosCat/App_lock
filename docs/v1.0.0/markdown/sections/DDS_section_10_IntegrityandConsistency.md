# Database Design Specification

## Version 1.0.0

## 10. Integrity and Consistency

### 10.1 Write integrity

Protected package add and remove operations use transactions. Authentication record creation and replacement use an all-or-nothing protected preference update. Retry state is committed as one logical authentication outcome.

### 10.2 Runtime snapshot consistency

The in-memory protected set is loaded only after encrypted database opening and schema verification. A selection change updates that snapshot only after durable commit.

If snapshot refresh fails after commit, the application re-reads the complete authoritative set before the next protection decision.

### 10.3 Initialization consistency

Startup never infers an empty protected set from:

- missing database-opening material;
- an encrypted database open failure;
- an unsupported schema version;
- a failed migration;
- corruption;
- storage exhaustion; or
- a transient initialization timeout.

These conditions produce unavailable or unrecoverable local-data status.

### 10.4 Cross-store sequencing

No operation requires atomic commit across the relational database and protected preferences. Where setup spans stores, the sequence preserves a visible incomplete state:

1. create and verify protected PIN state;
2. obtain Usage Access;
3. open and verify protected-app storage;
4. commit protected selections; and
5. begin protection monitoring.

Failure at any step returns to the incomplete step without inventing a completed setup marker.

### 10.5 Removal consistency

Deselecting an application requires authentication before storage change. Failed deletion leaves the durable row and active snapshot protected. Confirmed uninstall may remove the row without user authentication because the target no longer exists; a temporary package-query failure is not sufficient evidence of uninstall.

### 10.6 Migration consistency

Migration preserves only the data included in the version 1.0.0 contract. It verifies protected package uniqueness, readable retained settings where applicable, schema version, encrypted opening, and record accessibility before normal use.

Excluded-domain records do not create a preservation promise.

## Volume III — Physical Design
