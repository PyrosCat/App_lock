# Database Design Specification

## Version 1.0.0

**Supported devices:** Conventional Android phones running Android 11 through Android 15 (API levels 30 through 35).

> This specification defines the minimum local persistence required by the version 1.0.0 phone-only App Lock. It creates no database, file, history, backup, or migration obligation for excluded capabilities.

## Volume I — Database Architecture

## 1. Introduction

### 1.1 Purpose

This document defines what version 1.0.0 stores, where each kind of information is authoritative, how sensitive information is protected, how persisted state changes, how supported earlier schemas are migrated, and how storage failure is represented to the user.

The design supports a small local-only product. Persistent information is limited to ordinary settings, protected authentication and encryption state, and the set of package identifiers selected for App Lock protection.

### 1.2 Scope

The database design covers:

- private application preferences;
- Keystore-backed protected preferences;
- one encrypted embedded relational database;
- memory-only session and runtime state;
- data classification and ownership;
- protected-package constraints and transactions;
- database opening and version validation;
- migration from explicitly supported earlier schemas;
- lifecycle, deletion, reset, and Keystore-loss behavior;
- Android backup exclusion;
- corruption and storage-failure behavior; and
- storage verification for Android phones on API levels 30–35.

### 1.3 Explicit exclusions

Version 1.0.0 does not store or support:

- vault items, files, attachments, categories, tags, indexes, or vault keys;
- backup packages, restore staging, manifests, recovery passwords, or new-device transfer;
- profiles, application groups, schedules, automation rules, conditions, actions, or histories;
- intruder photographs, camera metadata, or event media;
- notification queues, notification histories, delivery receipts, or archives;
- security-event history, authentication-attempt history, diagnostic records, crash packages, or support exports;
- telemetry, performance metrics, usage history, trend data, or analytics;
- remote accounts, cloud state, server tokens, synchronization state, or enterprise policy;
- installed-application labels, icons, versions, categories, or a persistent search catalog;
- biometric templates, biometric identifiers, or detailed enrollment information;
- active unlock sessions, current foreground identity, active lock requests, or persisted health history;
- device-location, network, Bluetooth, contacts, or general-storage information; or
- any information obtained from an application Accessibility service, because version 1.0.0 does not provide one.

Inactive schema objects left from earlier work do not become supported data merely because they remain physically present. Their removal is not required solely for version 1.0.0 if they are unreachable, receive no writes, create no externally visible behavior, and do not weaken confidentiality or migration safety.

### 1.4 Objectives

The storage design shall:

- preserve the confidentiality of credentials, encryption material, and protected package choices;
- retain only information necessary for the included behavior;
- make each data element authoritative in one location;
- preserve PIN lockout and protected selections across process death and reboot;
- keep unlock sessions and current usage observations out of persistent storage;
- use atomic changes for security-relevant state;
- prevent a database failure from appearing as an empty unprotected configuration;
- support deterministic and testable migration;
- exclude application-controlled data from Android backup; and
- recover honestly when local protected material cannot be opened.

### 1.5 Intended use

This specification is the authoritative description of the supported persistent data contract. It does not prescribe source-code names or require a separate software object for every logical record. Physical implementation may reuse the existing local storage facilities where they satisfy the required boundaries.

The Software Design Specification defines how application responsibilities use these stores. The security specification defines credential strength and cryptographic requirements. The user-experience specification defines how storage and recovery states are explained.

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

## 3. Data Classification

### 3.1 Classification model

| Classification | Version 1.0.0 examples | Required handling |
|---|---|---|
| Private settings | Relock choice, haptics, reduced-motion choice | Application-private storage; validated values; excluded from backup |
| Confidential protection configuration | Protected package identifiers, biometric enablement choice | Encrypted or Keystore-backed storage; authenticated access in the interface; no notifications or ordinary logs |
| Secret authentication and encryption material | PIN verifier, salt and verification parameters, retry state, database-opening material | Keystore-backed protection; no export; no diagnostic exposure; no plaintext fallback |
| Transient security state | Current foreground package, active lock target, in-memory session, current health | Memory only; cleared on process death; no history |

### 3.2 Protected package identifiers

A protected package identifier can reveal which applications the user considers sensitive. It is therefore confidential even though the identifier is available elsewhere on the phone. It is stored in the encrypted relational database and is excluded from notification text, general diagnostics, backup, and ordinary logging.

### 3.3 Authentication information

The application stores no raw or reversible PIN. It stores only an approved verifier and the parameters necessary to evaluate future attempts. The verifier remains sensitive because it may be subject to offline guessing if exposed.

Biometric templates and matching remain entirely within Android. The application stores only whether the user chose to offer biometrics, when that choice requires persistence.

### 3.4 Usage and operational information

The current foreground package obtained through Usage Access is processed only for the immediate protection decision. It is not persisted as an event, duration, frequency, recent-app list, metric, or diagnostic item.

Current service and permission states are queried from Android. They are not authoritative database values.

### 3.5 Logging restrictions

Storage logs may identify a generalized operation category and success or failure. They shall not contain:

- the PIN or candidate length beyond a generic validation category;
- the verifier, salt, derivation parameters, or database-opening material;
- a protected package identifier, label, or count tied to a user action;
- the current foreground package;
- file paths or database connection material;
- biometric enrollment detail; or
- complete internal exception output in a user-accessible surface.

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

## 5. Storage Technologies

### 5.1 Private application preferences

Ordinary settings use the Android private application preference facility already selected by the software. The file is accessible only inside the application sandbox under ordinary platform operation.

Each setting has:

- a defined supported value set;
- a safe default;
- validation on read and write; and
- clear ownership by the settings responsibility.

Unknown values fall back to the safe supported default. The default relock behavior is immediate.

### 5.2 Keystore-backed protected preferences

Credential, lockout, biometric-choice, and database-opening records use preference encryption rooted in Android Keystore. The application does not substitute an application constant, device identifier, PIN, or package name for Keystore-protected random material.

Protected preference failure is explicit. The application does not fall back to ordinary preferences or plaintext files.

### 5.3 Encrypted embedded relational database

The protected-app database resides in private internal storage and is encrypted using the existing approved embedded database facility. It provides authenticated opening, transaction support, schema versioning, and uniqueness enforcement.

The physical database is local to the phone. It is not copied to shared storage, exposed through a content provider, attached to a remote service, or included in Android backup.

### 5.4 Internal files

Version 1.0.0 does not require user files, export packages, backup artifacts, media, diagnostic bundles, or a persistent application cache. Temporary files used by a supported migration, if any, remain in private storage, have a bounded lifetime, contain only the minimum migration data, and are removed after success or controlled rollback.

### 5.5 External and shared storage

No version 1.0.0 data is stored in external or shared storage. The application does not request general storage access.

### 5.6 Cache

No persistent cache is required. Android-provided labels and icons may be held temporarily in memory for list rendering. Such data is disposable and does not become authoritative.

### 5.7 Android backup configuration

The application explicitly disables or excludes backup of:

- private settings;
- protected preferences;
- encrypted database files;
- database journals and temporary files; and
- any migration artifact.

Backup exclusion is verified on supported API levels. The absence of a backup path is deliberate and is communicated by the recovery design.

## Volume II — Logical Database Design

## 6. Logical Information Catalog

### 6.1 Ordinary settings

Ordinary settings are a small non-relational record. Only choices exposed by the version 1.0.0 interface are retained.

| Information | Purpose | Default and validation |
|---|---|---|
| Relock behavior | Determines when a package-scoped session becomes invalid after leaving a protected app | Immediate is default; allowed alternatives are ten-second grace and screen-off |
| Haptic choice | Controls nonessential interface feedback | Uses the user-experience default; ignored when system settings prohibit feedback |
| Reduced-motion choice | Limits nonessential motion | Uses the user-experience default; does not affect security timing |

No general feature flag, profile, schedule, automation, backup, vault, event, or remote-configuration value is included.

### 6.2 PIN verification record

The protected PIN record contains:

- an indicator that a complete credential exists;
- the verifier format version;
- a unique random salt or equivalent verifier input;
- the approved memory-hard verifier;
- the parameters required to evaluate that verifier; and
- only the integrity information required by the chosen protected preference facility.

It does not contain the raw PIN, a reversible encrypted PIN, a recovery answer, a backup password, a device credential, or biometric data.

The record is logically indivisible. A missing required part means the credential is unavailable, not partially configured.

### 6.3 Retry and lockout record

The protected retry record contains:

- the consecutive failed PIN-attempt count; and
- the deadline before which another PIN verification is not permitted.

Five consecutive failed PIN attempts begin a 30-second lockout. Further failures after each wait double the next wait up to 30 minutes. Successful PIN verification or current-PIN-authorized PIN replacement clears the count and deadline.

The record survives process death, force-stop, and reboot. It does not store a detailed attempt history, target package, candidate length, or biometric failure history.

### 6.4 Biometric choice

The persisted biometric value records only the user’s decision to offer the platform prompt. It is meaningful only while a PIN exists. Current hardware, enrollment, temporary availability, and platform lockout are queried directly from Android at the time of authentication.

Destructive local reset clears the choice. PIN replacement preserves it only after current biometric eligibility is rechecked. Enrollment change does not create a database update unless the user later changes the preference.

### 6.5 Database-opening material

The protected database-opening record contains random material required by the encrypted database facility and the minimum format information needed to use it. It is generated once for the local installation.

It is not derived from a short PIN, does not leave protected storage, and is never included in a backup or diagnostic view.

### 6.6 Protected application record

The sole supported relational record represents one selected protected application.

Required information:

- package identifier.

Optional existing information may remain only when already needed for schema compatibility and must not become authoritative if Android can provide the current value.

Record existence means protected. A second enabled state, category, policy reference, group reference, archive state, user name, device identity, or schedule reference is not required.

### 6.7 Nonpersistent information

| Information | Why it is not persistent | Re-creation |
|---|---|---|
| Package-scoped unlock session | Persisting would extend authorization across process death or reboot | New authentication |
| Current foreground package | Usage history is unnecessary and privacy-sensitive | Next Usage Access query |
| Prior foreground package | Needed only for immediate relock evaluation | Next detector sequence |
| Active lock request | A stale request must not survive recreation | New protection decision |
| Protection health | It is a current assessment, not history | Recompute from storage and Android |
| Biometric eligibility | Android owns hardware and enrollment state | Query platform |
| Usage Access grant | Android owns the permission state | Query system setting |
| Notification permission | Android owns the permission state | Query platform |
| Installed app label and icon | Android owns current package metadata | Query package information |
| Diagnostic status | Current support facts do not require history | Query current owners |

### 6.8 Unsupported logical information

There are no supported logical records for vault, backup, recovery, application groups, schedules, automation, notification history, security-event history, diagnostics, telemetry, metrics, media, remote accounts, or administration.

## 7. Logical Relationships

### 7.1 Relationship model

The supported relational model has no parent-child relationship and no cross-domain foreign key.

Each protected application record is independent and uniquely identified by its package identifier. Settings and protected authentication records are stored outside the relational database and do not reference a protected application row.

### 7.2 Runtime association

At runtime, protection associates:

- a foreground package identifier reported through Usage Access;
- membership in the in-memory protected set loaded from the encrypted database; and
- an optional package-scoped session held in memory.

This association is calculated and is not persisted as a relationship.

### 7.3 Package metadata

The display label and icon are joined in memory from Android package information using the package identifier. They are not a relational entity and do not create a referential requirement.

### 7.4 Consequences of the reduced model

The logical design requires no:

- application-group membership;
- schedule or rule references;
- profile ownership;
- event-to-package relationship;
- notification-to-event relationship;
- vault parent-child relationship;
- session foreign key; or
- generic ownership and archive model.

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

## 9. Keys and Constraints

### 9.1 Package identity

The package identifier is the natural identity of a protected application. It is normalized using one documented representation and compared consistently.

The relational database enforces uniqueness. The application also validates package form before persistence and confirms current eligibility when selection originates from the installed-app screen.

### 9.2 Primary storage key

The physical store may use the package identifier directly as its primary key or use an existing internal row key with an additional unique package constraint. Version 1.0.0 does not require a migration solely to change between these equivalent physical choices.

The supported contract is uniqueness and stable lookup by package identifier, not a particular physical field name.

### 9.3 Value constraints

Ordinary settings accept only retained values. PIN verifier fields must form a complete supported format. Failed-attempt count cannot be negative. Lockout deadline must be absent or a valid time value defensively bounded at read.

Invalid protected authentication state produces unavailable authentication rather than a guessed default.

### 9.4 No foreign keys

No retained relationship requires a foreign key. Dormant foreign keys associated with excluded objects do not appear in the supported logical model.

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

## 12. Index Strategy

### 12.1 Required indexes

The protected package collection requires:

- the primary access structure supplied by the database framework; and
- uniqueness and efficient equality lookup for package identifier.

These may be satisfied by one physical index when the package identifier is the primary key.

### 12.2 Excluded indexes

Version 1.0.0 does not require indexes for:

- application label, icon, category, or version;
- group, profile, schedule, rule, priority, or time range;
- vault item, tag, category, attachment, or search content;
- security event or notification time;
- diagnostic severity or component;
- metric name or measurement time; or
- soft-deletion and archive state.

### 12.3 Index maintenance

The database framework maintains the retained index during ordinary transactions and migration. There is no periodic index-analysis service, index-usage history, or scheduled reindex operation.

An integrity failure affecting the unique package lookup makes the database unavailable until a supported recovery or destructive reset occurs.

## 13. Query and Transaction Design

### 13.1 Supported relational operations

The relational persistence boundary supports:

1. list all protected package identifiers;
2. determine whether one package identifier is present;
3. add one package identifier;
4. remove one package identifier; and
5. run the explicitly supported schema migration and verification.

No general query builder, full-text search, aggregation, pagination, join graph, or reporting query is required.

### 13.2 Read behavior

At verified startup, the complete protected set is read into memory. Equality checks during foreground detection use that in-memory set and do not query the database on every detector cycle.

The protected-app management screen combines the stored set with current Android package information in memory.

### 13.3 Write behavior

Each user selection change produces at most one add or remove transaction. Repeated taps are disabled or coalesced while the current write is unresolved. A user-visible success state is shown only after commit.

Adding an existing package and removing an absent package are idempotent final states. They do not generate error history or duplicate records.

### 13.4 Main-thread restriction

Database opening, migration, full-set reading, and writes execute away from the user-interface thread. Completion is delivered to the owner as a success or defined failure category.

### 13.5 Parameterization and input validation

Every package identifier used in a database operation is supplied as a bound value through the selected persistence framework. Text concatenation is not used to construct executable database statements.

Input validation confirms required form and supported length. Selection additionally confirms that Android currently reports an eligible launchable application.

### 13.6 Transaction isolation

Initialization and migration exclude ordinary access. Protected package writes are serialized. A read made after a successful write observes the committed set.

The design does not require distributed transactions, nested transactions, write batching across unrelated domains, or separate read and write databases.

### 13.7 Performance expectation

The complete protected set and an equality lookup must remain fast for the number of launchable applications reasonably present on a supported phone. The design favors predictable lookup and minimal writes over throughput for a large enterprise dataset.

Performance measurement does not persist package identifiers or query histories.

## 14. Storage Layout and Capacity

### 14.1 Layout

The version 1.0.0 local layout consists of:

- one ordinary settings area;
- one or more logically separated protected preference records;
- one encrypted protected-app database and framework-required companion files; and
- no required persistent cache or user-content directory.

### 14.2 Expected growth

Ordinary and protected preferences have bounded size. The relational database grows only with the number of selected applications and schema overhead. No retained data type grows continuously with elapsed time or application use.

### 14.3 Storage exhaustion

If storage exhaustion prevents a protection-reducing write, the previous committed protected state remains active. If it prevents adding protection, the new application is not reported as protected. If it prevents credential setup or replacement, the prior complete credential state remains authoritative.

The user receives a concise instruction to free device storage and retry. The application does not delete protected configuration, credential state, or encryption material to reclaim space.

### 14.4 Temporary storage

The application avoids persistent temporary data. When a supported migration requires temporary state, its maximum lifetime is the migration attempt plus controlled recovery. It is private, excluded from backup, and contains no raw PIN.

### 14.5 Cache and cleanup

There is no scheduled cache cleanup, diagnostic pruning, event retention, metric rollup, or storage forecasting. Normal application-data clearing and uninstall are the complete removal mechanisms.

## 15. Database Security

### 15.1 Confidentiality

Protected package identifiers are encrypted at rest in the relational database. PIN verifier, retry state, biometric choice where protected, and database-opening material are encrypted and authenticated in Keystore-backed protected preferences.

Ordinary private settings need Android sandbox protection but do not receive weaker copies of confidential information.

### 15.2 Key protection

Database-opening material is generated from a cryptographically secure random source and protected by Android Keystore-backed storage. It is not:

- derived solely from the App Lock PIN;
- hard-coded;
- based on a phone identifier;
- stored in the relational database;
- copied into a log, crash message, or diagnostic screen;
- included in Android backup; or
- exported.

### 15.3 Access control

Only the protected-application persistence responsibility accesses the relational database. Only authentication and storage initialization access the credential and database-opening records. Screens and Android detector callbacks use application operations instead of opening stores directly.

The application exports no database access component.

### 15.4 Integrity

Integrity is protected by authenticated protected preferences, encrypted database opening, transactions, uniqueness constraints, schema validation, and post-migration verification.

An integrity failure cannot be resolved by accepting a row, verifier, or schema value that fails validation.

### 15.5 Credential isolation

The PIN verifier is outside the relational database so that package selection queries cannot expose authentication data and database migration does not need to transform the credential. The raw PIN exists only during setup or verification and never becomes a database value.

### 15.6 Usage privacy

The database contains no foreground-application events from Usage Access. It cannot be used to reconstruct which application the user opened or when it was used. The only application identity retained is the set the user deliberately selected for protection.

### 15.7 Secure removal

Removing one protected application deletes its active row and invalidates its in-memory session. Version 1.0.0 does not promise physical overwrite of flash storage.

Clearing application data or uninstalling removes the application’s local files and protected preferences. Destruction of the Keystore-protected opening material provides cryptographic erasure of any remaining encrypted database bytes within Android storage limits.

### 15.8 No plaintext fallback

If encrypted storage cannot initialize, the application remains unavailable or requires reset. It does not create a plaintext relational database or store credentials in ordinary preferences.

### 15.9 Backup and export security

There is no backup or export path. All application-controlled data is excluded from Android backup, including ordinary preferences. This prevents a restored preference set from referring to absent Keystore material or silently transferring a protected-app list to another phone.

### 15.10 Security verification conditions

Security verification demonstrates:

- encrypted database bytes do not expose selected package identifiers through simple inspection;
- protected preferences cannot be read as plaintext;
- raw PIN values do not appear in any application-controlled storage;
- database-opening material does not appear in the database or logs;
- no Usage Access history is written;
- no Accessibility-service data or configuration exists;
- no application data participates in Android backup; and
- storage failure does not create a bypass or empty protected set.

## Volume IV — Database Operations and Lifecycle

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

## 17. Backup, Restore, and New-Device Behavior

### 17.1 Boundary

Version 1.0.0 creates no backup package, accepts no restore package, performs no cloud backup or synchronization, and does not participate in Android application-data backup.

### 17.2 New installation

A new installation, including installation on a different phone, begins with:

- no PIN verifier;
- no biometric preference;
- no protected package selection;
- no retry state;
- new database-opening material; and
- no unlock session.

The user completes setup and selects applications again.

### 17.3 Reinstallation

After uninstall and reinstall, prior local data is not expected to be available. The application does not search for an old database, external file, backup manifest, recovery password, or cloud account.

### 17.4 No restore staging

The design requires no restore parser, staging database, compatibility manifest, pre-restore snapshot, import transaction, backup password, restore verification, or post-restore reconciliation.

## 18. Data Lifecycle Management

### 18.1 First installation

The installation starts unconfigured. Keystore-protected database-opening material is created only when the encrypted database is first required. The database is created at the current schema version.

Setup completeness is derived from a complete PIN configuration, current Usage Access, readable protected-app storage, and selected-app state. A duplicate “setup complete” database row is not required.

### 18.2 Ordinary settings lifecycle

Ordinary settings begin at documented defaults, change only through validated user choices, and are updated in place. They remain until changed, application data is cleared, or the application is uninstalled.

They are not archived, historized, synchronized, or backed up.

### 18.3 PIN lifecycle

1. The user enters and confirms a permitted PIN.
2. A complete protected verifier is created atomically.
3. Authentication reads the verifier and evaluates candidates without changing it.
4. An authenticated PIN change atomically replaces the verifier.
5. Successful replacement clears retry state and sessions.
6. Clearing application data or uninstall removes the verifier.

No old verifier or recovery copy is retained after successful replacement.

### 18.4 Lockout lifecycle

1. The count begins at zero with no deadline.
2. Each failed PIN increments the count.
3. Beginning with the fifth failure, a wait deadline is stored.
4. Expiry permits the next attempt but retains the count.
5. A further failure creates the next doubled wait, capped at 30 minutes.
6. Successful PIN verification or current-PIN-authorized PIN replacement clears count and deadline.
7. Data clear or uninstall removes the record.

### 18.5 Biometric-choice lifecycle

Biometric use is disabled before PIN setup. The user may enable it only while eligible, but platform eligibility is not persisted. The protected preference remains until disabled, a destructive local reset completes, application data is cleared, or the application is uninstalled. Ordinary PIN replacement does not enable it automatically and preserves the user's prior choice only after current eligibility is rechecked.

### 18.6 Protected application lifecycle

1. Android reports an eligible launchable package.
2. The user selects it after required setup and authentication.
3. The unique package identifier is inserted and becomes active after commit.
4. Updates to the same package identifier preserve selection.
5. User deselection deletes the row after authentication.
6. Confirmed uninstall deletes the row and clears its session.
7. Reinstallation does not restore the row automatically.
8. Data clear or uninstall of App Lock removes all rows.

There is no archive, soft deletion, group assignment, or selection history.

### 18.7 Database-opening material lifecycle

Opening material is generated once for the local installation, protected through Android Keystore, and used only to open the encrypted database. It remains until application data clear, uninstall, or unrecoverable Keystore invalidation.

There is no rotation requirement in version 1.0.0 unless the selected storage facility performs a compatible platform-managed change without introducing a second key-management system.

### 18.8 Session and detector lifecycle

Package sessions, authenticated settings state, current foreground identity, prior foreground identity, and lock request remain in process memory. They are discarded on process death and reboot. Screen-off clears all package sessions and authenticated settings state. The authenticated settings state also ends when its sensitive flow completes or is cancelled, App Lock leaves the foreground, the PIN changes, or a security-relevant error occurs. No lifecycle event writes these values to storage.

### 18.9 Permission lifecycle

Usage Access, the "Display over other apps" overlay permission, and notification permission are Android-owned states. The application queries them on setup, resume, settings return, and relevant service change. It does not store a duplicate granted flag as authority or retain permission history.

There is no Accessibility permission state because the application provides no Accessibility service.

### 18.10 Reset and uninstall

Clearing application data or uninstalling removes:

- ordinary settings;
- PIN verifier and biometric choice;
- failure count and lockout deadline;
- database-opening material;
- encrypted database and selected packages;
- framework journals and temporary files; and
- all memory-only state.

The next installation or launch after clear begins as a new unconfigured installation.

### 18.11 Key loss and unrecoverable data

If Android Keystore material is invalidated or missing, the application cannot recover the protected preferences or encrypted database through a password or backup. It reports the local configuration as unavailable and directs the user to clear application data.

It does not delete the encrypted database silently, create a new key over the old database, or report an empty protected set as normal.

### 18.12 Retention

| Data | Retention |
|---|---|
| Ordinary settings | Until changed, data clear, or uninstall |
| PIN verifier | Until authenticated replacement, data clear, or uninstall |
| Failure and lockout state | Until successful PIN, credential replacement, data clear, or uninstall |
| Biometric preference | Until disabled, destructive local reset, data clear, or uninstall; PIN replacement preserves the choice only after eligibility is rechecked |
| Database-opening material | Local installation lifetime or unrecoverable invalidation |
| Protected package identifier | Until authenticated deselection, confirmed package uninstall, App Lock data clear, or App Lock uninstall |
| Sessions and current runtime state | Current process and security lifecycle only |

No retained category has a rolling historical retention period.

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
