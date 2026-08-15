# Database Design Specification

## Version 1.0.0

## 19. Database Operation and Failure Handling

### 19.1 Startup health check

Startup verifies:

- protected preference accessibility;
- database-opening material availability;
- encrypted database opening;
- supported schema version;
- successful completion of any required migration;
- protected-package uniqueness; and
- readable protected-package rows.

A full periodic integrity scanner is not required. Additional integrity checks run when startup, migration, or a database error indicates a problem.

### 19.2 Ordinary operation

The primary database remains closed to presentation and detector code. The protected-package persistence boundary maintains the verified database connection or lifecycle supplied by the selected framework.

Reads and writes are small, local, and independent of network state.

### 19.3 Failure classification

| Failure | Stored state treatment | Product result |
|---|---|---|
| Ordinary preference value invalid | Use documented safe default | Continue and allow user correction |
| Protected preference cannot decrypt | Do not substitute empty values | Authentication or database initialization unavailable |
| Database-opening material missing | Do not generate a replacement for the existing database | Unrecoverable local configuration |
| Database open failure | Preserve files; do not create an empty store | Retry where safe, otherwise clear-data guidance |
| Unsupported schema | No ordinary access | Unsupported local data; clear-data guidance |
| Migration failure | Prior consistent source remains where possible | No protection claim until resolved |
| Uniqueness or integrity failure | No ordinary protected-set use | Unavailable local configuration |
| Storage exhaustion during add | No new row committed | Application is not reported protected |
| Storage exhaustion during remove | Existing row remains | Application remains protected |
| Storage exhaustion during PIN change | Prior verifier remains | Existing PIN remains required |

### 19.4 Bounded retry

A transient open or write failure may be retried once or according to a small bounded local policy when the previous transaction result is known. The database is not repeatedly reopened in a tight loop.

PIN verification and protection-reducing writes are not automatically repeated when commit status is ambiguous.

### 19.5 Corruption

Confirmed corruption blocks normal protected-set access. Version 1.0.0 has no database backup or restore copy. A framework-supported, non-destructive integrity recovery may be used only when it preserves encryption and can verify the resulting retained rows.

Otherwise, the user is told that the local configuration cannot be recovered and that clearing application data will erase settings and protected selections.

### 19.6 Maintenance

No scheduled maintenance worker is required. The selected database framework may perform its normal journal and transaction maintenance. The application does not schedule reindexing, vacuuming, statistics collection, diagnostic pruning, event retention, or metric aggregation for this bounded dataset.

### 19.7 Resource use

Database work executes outside the main thread. Transactions are short. The complete protected set is read once after verified initialization and after a refresh need, not for every foreground polling cycle.

### 19.8 User messages

Storage messages state whether:

- a requested change was saved;
- existing protection remains active;
- local data is temporarily unavailable;
- retry is safe;
- application data must be cleared; and
- clearing data will remove the PIN, settings, and protected selections.

They do not expose file names, physical collection names, encryption details, or internal exception text.
