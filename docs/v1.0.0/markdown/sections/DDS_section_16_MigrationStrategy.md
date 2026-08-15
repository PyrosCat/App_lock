# Database Design Specification

## Version 1.0.0

## 16. Migration Strategy

### 16.1 Purpose

Migration allows an installation with an explicitly supported earlier local schema to open under version 1.0.0 without losing retained protected-package selections or the ordinary settings that remain applicable.

Migration is not a general compatibility promise for every development schema, every future version, or excluded feature data.

### 16.2 Supported source identification

Version 1.0.0 supports a clean installation and migration from the immediately preceding installed schema only when that source declares its exact format as schema version 1. No other earlier or development schema is supported. A source version is supported only when:

- it existed in an installation intended to be upgraded;
- its format can be identified deterministically;
- its retained data can be read without weakening encryption;
- a repeatable test fixture exists; and
- the migration can verify the result.

An unknown, newer, malformed, or unlisted schema is not opened as if it were current.

### 16.3 Migration boundary

Migration preserves:

- unique valid protected package identifiers;
- retained ordinary settings whose values remain supported;
- the retained biometric preference when its protected format is valid;
- the current complete PIN verifier when its protected format is explicitly supported;
- failed-attempt and active lockout state; and
- the database encryption boundary.

Migration is not required to preserve:

- vault, backup, recovery, schedule, automation, profile, group, notification-history, event-history, diagnostic, metric, media, or remote-account information;
- installed-application labels and icons;
- sessions, current foreground identity, active lock request, or health history; or
- unsupported settings that have no version 1.0.0 behavior.

### 16.4 Migration sequence

For an existing installation:

1. identify protected preference format and database format without normal application access;
2. confirm that the source is explicitly supported;
3. obtain or create the Keystore-protected material required by the target encrypted database;
4. preserve the prior consistent database until target verification succeeds;
5. open the source using only the approved read path;
6. normalize and deduplicate retained protected package identifiers;
7. write retained data into the target schema in a transaction;
8. apply retained setting conversion with safe defaults for invalid optional values;
9. verify target schema version, encrypted opening, uniqueness, record accessibility, and retained record count;
10. commit and make the target authoritative; and
11. remove obsolete plaintext or temporary migration material after successful verification.

Normal protection monitoring begins only after step 10.

### 16.5 Plaintext-source handling

If an explicitly supported earlier installation used a plaintext relational database, migration reads it only from private storage and only for the single conversion. The target is encrypted before it becomes authoritative.

The plaintext source remains untouched until target verification succeeds. After success it is removed. The application does not continue using it, retain it as a long-term backup, or create another plaintext copy.

### 16.6 Credential-format upgrade

A supported older PIN verifier may be upgraded after the user successfully authenticates with it. Verification uses the stored source parameters. The target memory-hard verifier is written atomically and becomes authoritative only after a complete protected write.

The application does not weaken the current verifier merely to migrate it without authentication.

### 16.7 Interrupted migration

After process death, reboot, or storage interruption during migration:

- a committed verified target is used;
- an uncommitted target is discarded or safely retried;
- the prior consistent source remains authoritative until target verification succeeds;
- partial target data is not exposed to normal repositories; and
- all sessions remain cleared.

Migration is idempotent at the version boundary and does not duplicate protected package rows.

### 16.8 Migration failure

A failed migration produces an unavailable local-configuration state. The application does not:

- silently create an empty database;
- delete the prior source before target verification;
- ignore invalid package records without reporting the overall outcome;
- continue with a partially migrated schema; or
- describe selected applications as protected.

If bounded retry cannot complete the explicitly supported migration, the user is told that local configuration cannot be recovered in version 1.0.0 and may clear application data to start again.

### 16.9 Downgrade

Database downgrade is unsupported. Installing software that expects an earlier schema must not destructively rewrite a newer database.

### 16.10 Migration performance

Migration executes away from the user-interface thread and shows a blocking startup state. The retained dataset is small. No separate migration scheduler, progress history, analytics record, or background continuation service is required.
