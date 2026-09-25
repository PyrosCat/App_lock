# Replacing Jetpack security-crypto: storage, encryption, and installed-user migration plan

**Date:** 2026-09-24. **Class:** dated proposal/snapshot. **Status:** proposed; design approval, implementation, and qualification remain outstanding.

**Inspected revision:** 59535f746f5d1843579963aa83aa5e5389963489 on main; fetched origin and confirmed main/origin/main were aligned before writing. Re-pin the revision, dependency graph, and fleet at execution. No application code, requirement status, risk disposition, or accepted architecture decision is changed by this plan.

**Decision owner:** project lead. Security/design reviewers approve the cryptographic format, recovery policy, and migration protocol; test operators produce host-specific evidence. This document recommends a design, not authorization to deploy it or an assertion that its tests pass.

**Review route:** [recommendation and scope](#1-recommendation-scope-and-evidence-labels); [official API evidence](#2-why-replacement-is-needed-official-documentation); [project constraints](#3-project-authority-requirements-and-threat-boundary); [consumer inventory](#4-complete-source-inventory-at-the-inspected-revision); [serializer and keys](#5-proposed-record-serializer-and-key-design); [integration](#6-integration-with-startup-authentication-and-existing-storage-callers); [migration protocol](#7-installed-user-migration-protocol); [blobs and newer API](#8-encrypted-blobs-and-the-newer-datastore-encryption-api); [harness and kill boundaries](#9-qualification-harness-fixtures-and-evidence-contract); [qualification cases](#10-qualification-cases-with-explicit-passfail-criteria); [review decisions](#11-decisions-requiring-review-before-implementation); [implementation and fleet gates](#12-concrete-implementation-slices-and-phased-execution); [alternatives and risks](#13-alternatives-and-remaining-risks); [removal gate](#14-removal-gate-and-final-recommendation-record).

## 1. Recommendation, scope, and evidence labels

Use stable typed DataStore for the small protected records, with a centrally reviewed serializer that encrypts and authenticates the entire serialized record using Android Keystore AES-GCM. Keep credentials, retry state, database-opening material, and migration control in separate records with separate purposes. Retain SQLCipher-encrypted Room for the protected-app database. Large blobs, if retained, need a streaming encryption/file storage design, not DataStore.

Schedule replacement as a separately governed work package, coordinated with M8/M9, or record an explicit M7 invariant-6 exception before implementation in M7. A new encrypted file format and durable migration control are new persistence. This proposal does not grant that exception.

**Scope boundary (clarified 2026-09-24):** this plan owns replacement of security-crypto, installed-data migration, and compatibility of the new storage adapter with existing callers. [2026-09-23_R007_F2_HARDENING_TEST_PLAN.md](2026-09-23_R007_F2_HARDENING_TEST_PLAN.md) owns R-007 analysis, candidate selection, runtime storage-recovery policy, hardening tests, and residual dispositions. Those are out of scope here. Lockout data is included solely because its encrypted store must migrate without losing values or changing the approved manager contract. Reference that contract and its regression evidence at integration; do not duplicate or redesign its hardening campaign in this plan.

Evidence labels throughout:

- **Verified by inspection:** source code, project documents, or official API documentation read for this proposal. This is not execution evidence.
- **Proposed:** a concrete design or acceptance rule to approve and implement.
- **Unverified:** behavior requiring builds, security review, fault injection, or device evidence. No replacement performance, migration outcome, or dependency-removal readiness is claimed here.

The user explicitly requires an installed-user migration. The [roadmap](../ROADMAP.md) records a historical zero-shipped-install decision for deleting an unrelated plaintext-database migration. That is not evidence that this encrypted-store migration can discard data. Build installed-user fixtures and support or explicitly disposition each source version even if the eventual rollout cohort is small.

## 2. Why replacement is needed; official documentation

All links below were checked on 2026-09-24. Recheck release status and resolve actual artifacts before implementation; floating documentation can describe APIs newer than the selected stable artifact.

| Source | Verified statement and implication |
|---|---|
| [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography#jetpack_security-crypto_library) | security-crypto APIs are deprecated in 1.1.0, and Google says there will be no subsequent library releases. The same guidance lists AES/GCM/NoPadding with 256-bit keys among recommended algorithms. Replacement addresses maintenance and future compatibility; deprecation alone does not prove a vulnerability in existing ciphertext. |
| [Security release notes](https://developer.android.com/jetpack/androidx/releases/security#1.1.0) | Stable security-crypto 1.1.0 was released on 2025-07-30. The project pins 1.1.0-alpha06. A separately tested stable-version update can be a bridge; it does not restore ongoing maintenance. |
| [DataStore release notes](https://developer.android.com/jetpack/androidx/releases/datastore) | Stable DataStore is 1.2.1; the latest listed 1.3 release is alpha11. The datastore-tink artifact and AeadSerializer were introduced in 1.3.0-alpha07. Thus the encryption integration is not part of the selected stable line. |
| [DataStore API contract](https://developer.android.com/reference/kotlin/androidx/datastore/core/DataStore) | Updates are serialized atomic read-modify-write transactions; successful completion follows durable persistence. Failed transforms/writes throw. The data flow represents durable state and can throw on reads. DataStore rewrites the object; it is not a partial-update database. These are library guarantees, not proof of every device's behavior during power loss. |
| [DataStore usage guide](https://developer.android.com/topic/libraries/architecture/datastore) | A file must have one DataStore instance per process, with immutable values. Single-process and multiprocess implementations must not be mixed for a file. These constraints shape the DI and inspector designs. |
| [Serializer contract](https://developer.android.com/reference/kotlin/androidx/datastore/core/Serializer) | The serializer defines reading/writing; its default value is used when no data exists. Consequently, the application must distinguish an authorized new record from loss of an established record. A zero-valued default is not a safe security recovery policy. |
| [Android Keystore](https://developer.android.com/privacy-and-security/keystore) | Keystore restricts key extraction and use; secure-hardware/StrongBox availability varies. StrongBox has resource/performance tradeoffs. Do not claim all supported devices provide the same hardware protection. |
| [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder) | Platform controls cover randomized encryption, key purposes, authentication, and device-unlocked restrictions. The unlocked-device option has documented behavior differences and bugs on older Android releases. Key policy needs explicit compatibility review. |
| [Android backup guidance](https://developer.android.com/identity/data/autobackup) | Some manufacturers' device-to-device transfer behavior is not disabled solely by allowBackup=false. Android 12+ extraction rules and older backup rules require separate treatment. Test the actual merged manifest and transfer paths. |
| [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) | It assists atomic file replacement but does not provide locking; a custom storage adapter still owns concurrency and failure handling. |
| [Google Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead) | Streaming AEAD supports large authenticated streams and associated-data binding. It is an alternative for retained blobs, with a separate dependency/version review. It does not establish the freshness of an entire restored application snapshot. |

**DataStore does not encrypt data by default.** The proposed serializer supplies encryption; DataStore supplies the selected persistence API. Neither component defines App Lock's authorization, recovery, migration, or rollback policy. Do not describe the combined implementation as reviewed until its independent review is complete.

## 3. Project authority, requirements, and threat boundary

| Project source | Constraint on this proposal |
|---|---|
| [v1.0.0 SRS](../../v1.0.0/markdown/Software_Requirements_Specification_v1.0.0.md), FR-011/012, FR-161..164, FR-170/173/174, FR-228/229 | Preserve a salted one-way PIN verifier, platform-protected keys, encrypted sensitive data, bounded secret exposure, retry enforcement, and safe migration/integrity handling. Never store a recoverable PIN. |
| [v1.0.0 DDS](../../v1.0.0/markdown/Database_Design_Specification_v1.0.0.md), §§4.4–4.9, 5.2 | Small protected records must be available before/independently of Room; credential and database-opening material remain separate. Migration precedes normal access. Missing security state is unknown, not an empty configuration. No backup or cross-device recovery is promised. DataStore satisfies the intended small protected-record role, but replacing the named preferences technology requires a documented architecture/baseline interpretation. |
| [v1.0.0 NFR](../../v1.0.0/markdown/Non_Functional_Requirements_v1.0.0.md), compatibility/dependency, responsiveness, storage, and resilience requirements | Review maintenance/platform support; measure startup/authentication and contention. The written v1.0.0 acceptance range is API 30–35; the build currently has minSdk 26 and target/compile SDK 36. Preserve this distinction in coverage and resolve release-range authority before acceptance. |
| [v1.0.0 threat model](../../v1.0.0/markdown/Threat_Model_v1.0.0.md), THR-AUTH-004, THR-CRYPTO-002/004/005, THR-REC-001/002/003/005; SC-DATA-001..005; VA-AUTH-004, VA-DATA-004..010 | Deliberate restart must not yield unrestricted guesses. Corruption, interrupted migration, key loss, and reset must not grant authorization. Safe status and complete destructive reset are the recovery boundary when data cannot be recovered. |
| [ADR-004](../../architecture/adr/ADR-004-centralized-security-services.md), [ADR-005](../../architecture/adr/ADR-005-keystore-key-storage.md), [ADR-012](../../architecture/adr/ADR-012-sqlcipher-room.md), [ADR-014](../../architecture/adr/ADR-014-api-range.md), [ADR-019](../../architecture/adr/ADR-019-version-split-baseline.md) | Centralize crypto; retain Keystore protection and SQLCipher Room. Do not rewrite accepted ADR decisions. A replacement ADR must identify which decisions it supersedes or implements and follow the lineage-number rules. Do not revive deferred v2.0.0 features merely to replace a dependency. |
| [M7 plan](../M7_PLAN.md), §2 and applicable integration contracts | Preserve responsiveness, ordered storage, freshness, event accounting, and immediate in-memory reset. No new persistence in M7 without an exception. Consume the approved lockout contract as an external dependency, as defined in §1. |
| [Risk register](../RISK_REGISTER.md), [RTM](../rtm/rtm.csv), [governance](../GOVERNANCE.md) | No status/risk reduction follows from this proposal. Implementation touching verified rows, including FR-162/164/174/228, requires cited re-verification or truthful invalidation in the same change. |

The threat model assumes the normal non-root Android sandbox/Keystore boundary. AEAD detects altered ciphertext and incorrect associated data; it does not stop a privileged actor restoring a complete older valid dataset, modifying the app, or clearing its data. File-edit tests deliberately use debug privileges to exercise rejection paths; their existence is not evidence ordinary third-party apps have those privileges. App Lock cannot make another application's content inaccessible while Android prevents App Lock from running. Report unavailable protection honestly.

The proposed policy for unreadable migration authority, credentials, or database-opening material is **fail-secure with visible recovery**. Review its user-facing behavior under D3 in §11. Optional biometric authentication cannot bypass missing core credentials/key material or migration readiness. This does not select a new runtime enforcement policy for a lockout read/write failure; that policy remains owned by the separate plan identified in §1.

## 4. Complete source inventory at the inspected revision

Paths in this table are relative to the repository. Searches covered app source sets and build declarations; repeat the inventory before removal, including resolved dependencies and packaged classes.

| Use and current file | Persisted content / callers | Access and durability observations | Proposed disposition |
|---|---|---|---|
| [CredentialRepository](../../../app/src/main/java/com/applock/security/CredentialRepository.kt): MasterKey.Builder and EncryptedSharedPreferences.create | applock_credentials; pin_algo, pin_salt, pin_hash, pin_iterations, pin_argon2_m/t/p. Called by AuthGateViewModel/MainActivity, LockScreenActivity, OverlayLockPresenter, ProtectionWatchdogService; direct construction also occurs in instrumentation tests. | Synchronous reads/verification; new PIN and successful PBKDF2 upgrade use apply(). Existing Argon2id/PBKDF2 values and historical defaults are part of compatibility. | Dedicated encrypted typed CredentialRecord. Copy verifier fields without knowing/changing the PIN; keep hashing separate from encryption. Durable setup/PIN-change acknowledgement; rehash is a distinct operation. |
| [DatabaseKeyProvider](../../../app/src/main/java/com/applock/security/DatabaseKeyProvider.kt): MasterKey.Builder and EncryptedSharedPreferences.create | applock_db_key; db_passphrase. AppLockDatabase.build directly constructs the provider. | Generates 32 random bytes, hex-encodes them, stores with apply(), then returns a String. Room currently uses that String's bytes as the passphrase. Startup depends on it. | Dedicated encrypted DatabaseKeyRecord. Preserve the exact existing passphrase byte sequence supplied to SQLCipher, including its encoding; do not hex-decode it into a different key. New-key creation is a separate fresh-install operation. |
| [EncryptedPrefsLockoutStorage](../../../app/src/main/java/com/applock/security/EncryptedPrefsLockoutStorage.kt): MasterKey.Builder and EncryptedSharedPreferences.create | applock_lockout; failure_count and lockout_until. LockoutManager through debug/release factories; independent instrumentation inspector. | Synchronous pair read; pair written by commit(), Boolean checked. Manager memory admission precedes durability. Deadline is wall-clock epoch millis, with separate process-only monotonic enforcement. | Dedicated encrypted LockoutRecord. Preserve the pair and its time meaning; serialize writes in manager admission order. New migration metadata must not be represented as PIN attempts. |
| [EncryptedFileStore](../../../app/src/main/java/com/applock/security/EncryptedFileStore.kt): MasterKey.Builder and EncryptedFile.Builder | AES256_GCM_HKDF_4KB streams under filesDir/vault and filesDir/intruder. VaultRepository import/export/preview/delete; IntruderCaptureManager capture/preview/delete. | openOutput deletes an existing destination before opening a stream. Blob/index durability is separate. decodeBitmap collapses unreadable/image errors to null. Large data and streaming needs differ from preference records. | Prefer governed retirement with M8 for v1.0.0. If an installed cohort must retain blobs, separately migrate with a supported streaming AEAD/file adapter; no destructive overwrite during migration. |
| [AppModule](../../../app/src/main/java/com/applock/di/AppModule.kt), [debug factory](../../../app/src/debug/java/com/applock/security/LockoutStorageFactory.kt), [release factory](../../../app/src/release/java/com/applock/security/LockoutStorageFactory.kt) | Singleton repositories/store wiring; Android elapsedRealtime binding; debug fault wrapper around the real encrypted adapter. | Factory split is a test seam, not a second encrypted format. AppLockApplication injection triggers eager database creation through LockPolicyManager. | Preserve clock and fault seams; inject one coordinator and one store instance per file; remove I/O from graph construction. |
| [libs.versions.toml](../../../gradle/libs.versions.toml), [app build](../../../app/build.gradle.kts) | securityCrypto=1.1.0-alpha06; implementation(libs.androidx.security.crypto). | Actual transitive resolution may differ from declared dependency versions. Current Kotlin 2.1.0/coroutines 1.9.0 must be checked against selected artifacts. | Add pinned stable DataStore and reviewed serialization dependencies first. Remove security-crypto only after §14, with dependency insight and APK inspection. |

All four MasterKey builders omit an explicit alias. Treat the default alias as potentially shared across all these stores and library-managed Tink keysets; verify actual aliases/keyset ownership in disposable fixtures for the resolved library version. Do not delete a default alias when only one consumer has migrated. Inventory library-generated preference files and keyset entries as well as the named application keys. Do not log their values.

Adjacent boundaries requiring integration, not an automatic expansion into unrelated refactoring:

- [AppLockDatabase](../../../app/src/main/java/com/applock/data/AppLockDatabase.kt) opens/verifies SQLCipher eagerly and can move an unreadable DB aside, create a fresh one, or attempt a last-resort deletion. Migration/key errors must never enter that rebuild path. Existing R-004 closure does not prove compliance with this proposal's stronger no-reset-on-read-failure rule.
- [SettingsRepository](../../../app/src/main/java/com/applock/data/SettingsRepository.kt) uses ordinary applock_settings preferences, including biometric_unlock and relock_policy. It is not a security-crypto consumer. Preserve values during migration verification; decide explicitly whether the retained security-sensitive settings join an encrypted SettingsRecord in this work or a linked conformance slice. The DDS's protected biometric choice needs a tracked disposition, not a claim that replacing the other stores fixes it.
- SQLCipher is retained, not replaced by DataStore. The database key cannot live only inside the database it unlocks. No runtime sessions, active requests, foreground identities, or health history enter these new records.

## 5. Proposed record, serializer, and key design

### 5.1 Data layout and ownership

Proposed names are reviewable design choices, not files created by this plan. Use a private, credential-encrypted, no-backup directory, for example noBackupFilesDir/security/v1. Do not relocate security records to device-protected storage to make boot reads succeed.

| Record | Contents and validation | Normal writer / publication |
|---|---|---|
| MigrationControl | Protocol version, installation/migration identity, state, active generation, store identities, supported reader version, cleanup/key dependency progress. No sessions or authentication results. | Migration coordinator; infrequent durable state transitions. |
| CredentialRecord | Explicit configured/unconfigured state; algorithm, verifier/salt bytes, complete parameters, credential revision; active generation identity. | Credential repository's serialized operations. Publish setup/change success only after durability. |
| LockoutRecord | Existing count and wall deadline, plus migration generation identity and storage-format version. No new enforcement fields are proposed here. | Existing manager's ordered writer exclusively. Do not persist process-relative monotonic deadlines or reinterpret existing values. |
| DatabaseKeyRecord | Exact SQLCipher input bytes, encoding identifier, record version and purpose/generation. | Bootstrap/migration only initially; no database rekey in this migration. |
| SettingsRecord, if approved | Retained protected choices with explicit validated enums/booleans. | Settings repository; separate from high-frequency retry state. |

Use stable DataStoreFactory APIs available in the pinned stable artifact, not newer documentation's alpha-only builder methods. Encrypted serializer and platform Keystore operations belong in the security/platform implementation boundary; repositories depend on narrow storage/key interfaces. Proposed types should use immutable generated protobuf messages/ByteString, or equally strict immutable values, not mutable ByteArrays exposed as DataStore state. Treat decrypted DataStore caching as process memory exposure: verifier/key material may remain reachable; bound lifetime, do not expose it to UI state, and do not promise complete JVM memory erasure.

DataStore's default object must be an explicit **Uninitialized** sentinel that cannot authorize, verify a PIN, open Room, or mean count=0. Only the coordinator may initialize a record after validating a fresh-install or migration transition. A missing active file is an error even if the library returns its default object. Do not attach a corruption handler that writes empty credentials, a zero lockout, or a replacement DB key.

### 5.2 Serializer alternatives

| Design | Benefits | Costs / disposition |
|---|---|---|
| Whole-record Keystore AES-GCM around protobuf-lite (recommended) | Typed fields, compact records, explicit schema, one authentication check before parsing; platform-generated non-exportable AES key. | New schema/toolchain and a small custom envelope/crypto integration require review. Every real write invokes Keystore and rewrites the record. |
| Whole-record AES-GCM around strict JSON | Easier test fixtures; can avoid protobuf generation. | Larger representation; parser/number/default handling and serialization-plugin compatibility need care. Ciphertext is not human-readable regardless of inner format. Accept only if equivalently strict and simpler for this build. |
| Encrypt selected Preferences DataStore values | Familiar key/value access. | Metadata/omitted fields can leak; easier to create partial or mixed records; more nonce/AAD rules. Reject for credential and lockout records. |
| Stable DataStore plus directly integrated Tink AEAD | Uses a maintained AEAD abstraction and may reduce crypto integration code. | Adds wrapped-keyset lifecycle/bootstrap and dependency scope. Evaluate only with an explicit no-plaintext-keyset policy and equally strong migration tests. |
| Handwritten Keystore-encrypted AtomicFile adapter | Potentially fits synchronous callers and limits DataStore caching. | Owns file durability/error propagation/locking plus crypto. Not the default; require a measured reason to take this larger maintenance burden. |

Do not implement a new cipher, password derivation algorithm, chunked encryption scheme, or authentication-tag comparison. Use platform Cipher.doFinal for small records, authenticating before releasing plaintext to a parser or caller. Use constant-time verifier comparison already provided by the hashing implementation; do not replace Argon2id with encryption.

### 5.3 Cryptographic envelope to review

Proposed version 1: fixed magic; envelope version; bounded header length; store-purpose ID; installation/migration identity; key generation; ciphertext length; 12-byte GCM nonce; ciphertext with a 128-bit tag. Set an initial 64 KiB maximum encoded record size for these small records and a bounded header, subject to fixture validation before approval. Reject oversized lengths, overflow, trailing bytes, unknown mandatory fields/algorithms, zero-length initialized records, and malformed encoding before allocating unbounded memory.

Authenticate the exact canonical header plus a stable application namespace and expected store purpose as associated data. Derive the expected purpose from the opened repository, not merely from untrusted header bytes. Do not bind to an absolute path, APK version, mutable display name, or device identifier. Alias selection is from an allowlisted generation mapping; ciphertext cannot name an arbitrary Keystore alias. No secrets belong in the clear header/AAD. A versioned plaintext schema is inside the authenticated ciphertext; reject unsupported newer schemas instead of interpreting them as defaults. Unknown optional fields may be preserved only under a reviewed compatibility rule.

For each encryption, create a fresh Cipher, initialize with the Android Keystore key and randomized encryption enabled, obtain the provider-generated IV, validate its required length, set AAD, then encrypt. Never reuse a stored IV for a rewrite, failed-write retry, or rotation. Do not share a mutable Cipher among concurrent callers. On decrypt, validate envelope bounds, select an existing authorized key, authenticate with doFinal, then parse/validate the complete record. Clear temporary plaintext/PIN buffers in finally blocks where possible. Serializer code does not independently rename files, launch coroutines, or publish state; DataStore owns the write transaction.

Authenticity is not freshness. Generation/revision fields fence application races and mixed-store imports, but are not a rollback-proof monotonic counter. Restoring the entire old authenticated control record plus its matching files under an available key may pass cryptography. Stronger anti-rollback would require a separately justified trusted mechanism and threat-boundary change.

### 5.4 Keys, access, and future rotation

Propose separate versioned AES-256 Keystore aliases for control, credentials, lockout, and database wrapping, generated under one application initialization coordinator. Generate only for an authorized fresh record, migration target, or approved rotation. The read path calls loadExisting and never getOrCreate. An absent required alias is KeyUnavailable, not permission to generate a substitute. A key-generation exception cannot trigger a software/plaintext fallback.

Default proposal: no per-use biometric/device-credential authentication requirement for these storage keys, and no new unlocked-device-required flag. App Lock's own PIN verification is separate from Android device authentication; lockout persistence/background recovery must not require an extra prompt or lose keys after biometric enrollment changes. Credential-encrypted storage still imposes boot availability limits. Review this policy against v1.0.0 requirements and test screen lock, enrollment, reboot-before-first-unlock, and device unlock. StrongBox-only operation is not proposed; record actual protection level and explicitly review any future stronger requirement.

The exact SQLCipher secret necessarily enters process memory to open SQLCipher; non-exportability applies to the Keystore wrapping key, not to every data secret in RAM. Avoid logging/stringifying payloads and reduce duplicate byte/string copies. Do not derive any encryption key from the user's PIN, salt, package name, or Android ID.

Reserve key-generation and schema fields now; general user-facing rotation is deferred v2.0.0 scope, not silently implemented here. A future rotation should generate a new alias, write and verify a new record generation, durably select it, then retire the old alias only when every dependent record/reader has been disposed. Database wrapping-key rotation must preserve the SQLCipher passphrase; changing that passphrase is a different SQLCipher rekey procedure. Rotation crash tests must cover key creation, rewrite, selection, and deletion, just like initial migration.

### 5.5 Failures, backup, and recovery

Use explicit outcomes: Uninitialized, Ready, TransientUnavailable, KeyUnavailable, AuthenticationFailed, InvalidRecord, UnsupportedVersion, MigrationIncomplete, and AmbiguousAuthority. An invalid PIN is a separate authentication result. Catch cancellation separately; it is not automatically a failed storage commit. Preserve error categories without logging secrets or plaintext exceptions from parsers.

On a transient migration/key-access fault, retry only the selected source through the coordinator; define bounded concurrent work and cancellable retry controls without occupying the UI/runtime thread. Show Checking briefly, then Action required/Protection interrupted as appropriate. On confirmed corruption/key loss/unsupported migration data, preserve bytes and stop normal use; offer safe retry/update guidance and the approved full destructive reset path. No silent substitute key, no default empty protected set, no automatically imported old record. Runtime lockout recovery scheduling and enforcement remain the existing manager's responsibility.

Maintain allowBackup=false, add reviewed explicit exclusions for both supported backup-rule formats and all applicable transfer sections, and keep new records under noBackupFilesDir. Inventory old prefs/keysets, Room DB/WAL/SHM/recovery files, new/control/staging files, and retained blobs. No secret-containing domain can be accidentally included by a new rule. Backup rules do not constitute proof against every OEM transfer implementation. Restore tests are rejection/containment tests, not a new backup feature.

Destructive application reset is separate from a successful-authentication lockout reset. Reset must be explicit and follow the v1.0.0 user flow; it grants no session. Prefer the governed whole-app-data-clearing route where appropriate. If implementing manual reset, persist a ResetPending state, block ordinary access, remove all dependent data, remove keys last, and complete the reset idempotently. If control cannot be read, do not invent a selective data-preserving reset: direct the user to the approved complete reset route. Flash deletion is not a promise of physical overwriting.

## 6. Integration with startup, authentication, and existing storage callers

### 6.1 Startup and dependency injection

Introduce a SecurityStorageCoordinator with an application-owned scope and explicit Loading/Ready/RecoveryRequired state. Its dependencies are lightweight constructors; work starts asynchronously. It inventories/validates authority, completes/resumes migration, loads credential/key records, opens/verifies Room with the exact key, and supplies the lockout seed. Ordinary repository access waits for readiness off the UI/runtime drain or receives a typed not-ready result.

Replace AppLockDatabase.build(context)'s hidden DatabaseKeyProvider construction with a factory that receives already validated key material and opens on an I/O worker. Split database open/validation from recovery. Migration and key failures must propagate to the coordinator without calling recoverAndRebuild, moveAside, delete, or opening an empty database. Any retained corruption-rebuild behavior outside migration needs separate reconciliation with the active v1.0.0 failure rules.

Refactor AppLockApplication/AppModule's eager LockPolicyManager/DAO graph so injection does not force database I/O. A lazy provider alone is insufficient if immediately dereferenced by startup or Hilt field injection. Repositories may depend on a suspending database handle/readiness provider; policy state stays Loading/Failed until verified data is available. Preserve boot-time elapsedRealtime injection. Review architecture tests/layer rules and existing pinned component names rather than adding new exported components.

Create exactly one DataStore instance for each active file. Candidate verification uses the same instance while running; an independent physical read uses a stopped/closed owner or a fresh process. Instrumentation's Application.onCreate must not open/migrate the target before the inspector. New test orchestration must assert the first accessor by PID and store identity, not assume instrumentation startup is inert.

### 6.2 Synchronous authentication callers

Change CredentialRepository to expose asynchronous operations and typed readiness/results: read credential status; set/change PIN durably; verify PIN; and separately reconcile an optional legacy-hash upgrade. Proposed contracts are suspending operations plus immutable public readiness state, not a Boolean that conflates missing data and no PIN.

Update AuthGateViewModel, MainActivity setup/self-gate, LockScreenActivity, OverlayLockPresenter, and ProtectionWatchdogService together. Replace synchronous PinPad verification callbacks with submission plus pending/result state. UI/Compose callbacks enqueue work; hashing uses a bounded CPU dispatcher, storage/Keystore work an I/O worker. No runBlocking, main-thread data.first(), synchronous key generation, or storage wait inside the runtime event loop. A readiness Boolean cache must not make Unknown look like Unconfigured or cause the watchdog to stand down.

Serialize authentication admission across the self-gate and protected-app surfaces. Recheck readiness and the current lockout before verification, coalesce/reject duplicate submission IDs, and fence results by request/surface/credential generation. A cancelled/dismissed surface cannot create a session from a late result. Once a PIN has actually been evaluated, account its result exactly once under the approved authentication policy; caller cancellation does not erase a verified bad guess. Clear PIN arrays on all exits, including ignored/duplicate/blocked submissions and exceptions. Do not persist PIN input or restore it after process death.

Migration copies PBKDF2 or Argon2id records without verifying a PIN or changing parameters. Preserve the baseline interpretation of absent historical algorithm/parameter fields only for a documented, recognizable legacy schema. Unknown algorithms and malformed/partial verifiers are recovery conditions. A later successful PBKDF2 verification may durably upgrade the hash; failed upgrade keeps the old valid verifier, reports a storage problem, and must not be counted as an incorrect PIN. PIN creation/change does not announce success before durability. Cancellation around that acknowledgement requires reconciliation, not blind rollback to an old PIN.

### 6.3 Lockout adapter compatibility boundary

The current LockoutStorage.read/write are synchronous. Adapt this interface to DataStore's suspending operations without blocking the UI/runtime thread, changing persisted count/deadline meaning, or introducing a second writer. A suspending port and explicit initialization result are implementation options to review against the approved manager contract. The adapter must propagate missing/unreadable data as a storage outcome, never manufacture an empty snapshot or select legacy data after cutover.

**A single-thread dispatcher is insufficient after write becomes suspending.** The current scope.async-per-write pattern could start a later operation while an earlier write is suspended. If the port changes, provide an explicit FIFO consumer or equivalent ordering mechanism that awaits operation N before starting N+1. DataStore serialization alone does not establish manager admission order. Never hold the admission monitor across storage or Keystore I/O.

Preserve existing in-memory publication, operation outcomes, event accounting, immediate success/reset, and lifecycle semantics. Do not feed DataStore's durable flow back into the manager as an independent enforcement authority. Migration finishes before the manager accepts normal work. Import the existing manager's regression suite and its approved expected outcomes by reference; this work tests adapter compatibility, not a new recovery algorithm. A change to enforcement, retry scheduling, fallback deadlines, admission policy, or residual treatment belongs in the separate workstream identified in §1.

## 7. Installed-user migration protocol

### 7.1 Authority model and preconditions

Use one durable encrypted MigrationControl record to select a complete generation of small protected records. Separate DataStore files are not a distributed transaction. The coordinator creates application-level atomic cutover by withholding all ordinary access while preparing, then committing one authority decision after the target set is durable and validated.

States: **Preparing**, **ReadyToActivate**, **Active**, **CleanupPending**, **Complete**, and **ResetPending**. Active/CleanupPending/Complete all mean new-format-only authority. Error/readiness status is memory-only unless a reviewed transition explicitly changes the control record. Generation IDs and per-record purpose bind the selected set; initial-copy digests are validation evidence for cutover, not a checksum that must keep matching after legitimate mutations. Do not persist hashes of PIN verifiers or raw key fingerprints in general diagnostics.

Acquire one initialization/migration ownership lock before any repository or Room opens. All application entry points, including services, receivers, and instrumentation, observe it. No normal writes are allowed to legacy or target records during preparation. Upgrading a running installation requires a process restart and quiescent old writers; do not hot-migrate a live writer queue. Future multiprocess access requires an explicit protocol and multiprocess-capable storage for every participant, not a second singleton in another process.

Classify installation state before key creation:

- No control, no target/staging artifacts, no legacy records/DB/blobs, and no relevant existing aliases: candidate pristine installation, subject to explicit setup initialization.
- Recognized readable legacy installation and no new-format artifacts: candidate legacy migration.
- Valid Preparing/ReadyToActivate control: resume that migration; validate its source/target facts again.
- Valid Active/CleanupPending/Complete control: use only its new generation. Failure reading it or its records is recovery, never permission to read legacy.
- Missing/unreadable control with any new-format artifact, established key, or inconsistent inventory: AmbiguousAuthority. Preserve and stop; do not infer a fresh installation or restart import.

**Narrow bootstrap exception to prove in review:** a control key may have been generated immediately before death, before Preparing was written. Resuming with that same key is allowed only when the control file is positively absent (not unreadable), no control temp/backup or target/staging artifact exists, a successful complete alias inventory finds no target-purpose generation key, and the untouched source passes legacy eligibility validation (or the installation meets the explicit pristine-setup test). Target-purpose aliases are created only after durable Preparing and at least one remains throughout every active generation/rotation until full destructive reset; ordinary cleanup removes legacy aliases only. This establishes that activation could not have occurred under the protocol's kill-only fault model. Retry creates Preparing, not new legacy data, and never replaces the existing control key. If any inventory/lookup fails or the proof is incomplete, remain in recovery. Review/test this ordering invariant; it is not protection against a privileged actor deleting every witness of a previous installation.

A legacy file never written and a legacy file lost may be indistinguishable because the old format lacks an initialization marker. Build a source-version eligibility table. Permit an absent field/file only when a reviewed historical schema and consistent other facts unambiguously establish an uninitialized state. Where evidence cannot distinguish loss from legitimate absence, fail safely and require a disposition; do not invent zero retry state or a replacement passphrase. Read exceptions, including permissions/decryption failures, are never absence.

The LegacySecurityReader must be demonstrably read-only. Do not call the existing repositories' getOrCreate-style initialization to discover whether data exists. Inventory expected files, embedded/library keysets and the existing alias first; bypass MasterKey.Builder's creation path. Review the resolved legacy library's keyset-bootstrap behavior as well: a missing keyset must be reported, not silently generated. Under the exclusive coordinator, verify read attempts make no preference/keyset/key writes even when a fault occurs after inventory. If the available legacy API cannot meet that condition, the migration slice is blocked until a reviewed read-only access strategy is supplied; a new empty keyset is not a valid migration source.

### 7.2 Ordered steps; each is restartable

1. **Inventory and freeze:** enter Checking; reject authentication/configuration mutations; ensure no existing writer/DB owner remains. Read source files through the legacy APIs with known keys; do not use raw encrypted XML as if it were plaintext preferences. Record the source format and participating dependencies without exposing contents.
2. **Validate source:** verify complete credential structure, exact DB passphrase, lockout pair and settings; validate DB integrity and known logical data using a non-rebuilding open. Read from a fresh process so old SharedPreferences memory cannot mask an uncommitted apply(). Unknown source version or decryption failure stops migration. Do not rehash credentials, alter lockout duration, migrate Room schema, or rekey SQLCipher in the same step.
3. **Prepare control:** generate the new control key only under authorized target creation; persist Preparing with migration/generation identity and source inventory. Existing legacy bytes/keys remain untouched. If death occurs after creating only the control alias, apply the narrow bootstrap proof above and reuse that key; otherwise stop safely. No reset of an installed user's valid data is an acceptable substitute for passing the ordinary kill-only migration case.
4. **Create target keys and records:** for each store in a fixed order, create/load its authorized target alias; serialize/encrypt the validated source into its generation-specific record; await durable completion. Re-entry with an existing matching target verifies it instead of blindly generating another key. Interrupted target files are never treated as active. A mismatched target is quarantined/diagnosed under this preparation identity, never silently used.
5. **Verify targets:** independently decrypt/authenticate and compare semantic contents; compare exact database input bytes in memory; open the unchanged DB with that key without recovery or mutation. Validate original protected selections/settings and lockout timing. Stop/close owners before independent disk verification. Persist ReadyToActivate only after the complete participating set passes.
6. **Commit authority:** atomically update control to Active, referencing the validated generation and minimum compatible reader. This is the cutover linearization point. On an exception or lost acknowledgement, resolve by reading control from the authoritative store; if unreadable, stop. A timeout is not evidence that activation failed.
7. **Activate runtime:** initialize repositories and the existing lockout manager from the selected generation, publish Ready only after current records and Room are valid, and permit new operations. New writes never touch legacy stores. No automatic read fallback or dual-write compatibility mode.
8. **Deferred cleanup:** after the qualification/rollout hold, set CleanupPending and remove legacy files/keysets only under a verified dependency ledger. Delete each artifact idempotently and record completion. Keep all required migration-reader support until every allowed upgrade path is covered. Only then retire unused aliases and mark Complete. Complete remains durable and forbids legacy re-import; cleanup is not deletion of authority.

A fresh install uses the same controlled target creation/activation path with an explicit unconfigured CredentialRecord and zero retry state. Generate a random DB secret once, durably store it before creating/opening a DB that depends on it, then publish setup readiness. Interrupted setup stays unconfigured/migration-incomplete; it is never an authenticated session. Setup with a new PIN is a later durable operation.

### 7.3 Cutover, reset, and restart rules

After Active, control and record generations are checked at every startup before use. A missing/corrupt active record stops normal access even when all legacy copies decrypt correctly. Control missing/corrupt also stops access. This deliberately trades availability for avoiding ambiguous old/new mixtures. The control record is a new availability dependency; test it as rigorously as credentials.

Before Active, validated legacy data remains the last committed authority; the proposed binary remains in migration/recovery until preparation resumes or an explicit reviewed abort restores legacy-only operation. An abort is allowed only after proving no activation and no new user operation occurred, and durably disposing preparation artifacts. Do not switch based on a process-local Boolean or an unreadable activation record.

A successful-authentication reset writes the selected new lockout record through the existing manager; it does not clear migration control. If count=0/deadline=0 became durable, restart must preserve that reset even if legacy contains a long deadline. The migration must never import an older legacy copy over the selected store. Recovery of runtime operations that never became durable is outside this plan. Complete destructive reset removes legacy/new/control records as a coordinated separate operation, not a lockout reset.

### 7.4 Rollback, downgrade, and skipped upgrades

Before activation, rollback to a known legacy binary is possible only under the controlled abort conditions above. After activation, application rollback must use a migration-aware binary that understands the active format; rollback changes application code, not storage authority. Retain a signed compatible rescue build for testing and release operations.

An old APK that knows only legacy preferences cannot honor the new control record. Keeping old data current by dual-writing would create two authorities and could resurrect counters/credentials; deleting it may make that old APK create fresh defaults. Neither is a safe downgrade plan. Declare such downgrades unsupported, test the normal installer/version safeguards, and document forced developer/root downgrade as outside the guarantee. A forward update following a forced downgrade must detect inconsistency where possible and refuse to merge old state; do not claim detection of complete privileged snapshot rollback.

Dependency removal must cover users skipping intermediate versions. Either the final supported upgrader still contains a reviewed legacy reader (and therefore retains security-crypto until no such source is supported), or distribution enforces an intermediate migration release with verified reachability. An optional staged rollout alone does not enforce that upgrade path. If direct upgrades from an unmigrated supported install remain possible, removing its only legacy decoder fails the release gate. An independently maintained legacy-format decoder is a separate security project, not the default workaround.

## 8. Encrypted blobs and the newer DataStore encryption API

### 8.1 Blob disposition

The v1.0.0 roadmap removes Vault/intruder features in M8. Prefer retirement with an explicit installed-data retention/disposal decision, not building their replacement into the v1.0.0 product. Removing their UI/code is not permission to delete an installed user's only ciphertext or a key still needed by migration. D6 must identify any existing data cohort, allowed retrieval period/process, and final disposition. No dependency can be removed while a promised legacy blob reader still needs it.

If preservation is required, use a dedicated streaming adapter using an approved stable Tink Streaming AEAD artifact and a Keystore-wrapped keyset. Select/pin an AES-256 profile and chunk size after reviewing the maintained API and measuring memory/throughput. The official guidance supports streaming use; it is not evidence that old EncryptedFile ciphertext is directly compatible with a new adapter.

For each blob: allocate a new random destination ID, decrypt the legacy stream and encrypt to a private temporary ciphertext file, close/finalize and sync it, verify the complete stream and expected length/content in the test/validation path, then atomically publish the file and transactionally update its encrypted index/manifest. Bind blob identity, purpose and generation as AAD. Do not expose migrated content as complete before final authentication succeeds. Never spool plaintext to disk. Do not replace the original first as current openOutput does. Old blob plus old index remains authoritative until the per-blob commit; after commit no fallback to old content on read error. Handle orphan temp/new files after death without deleting the last referenced copy. Keep shared blob keys until all blobs are migrated or explicitly retired.

Blob migration may require a Room schema/index change and separate file/index commit protocol. It is outside M7 absent explicit authorization. An unknown/corrupt blob does not justify resetting authentication/database records. Report its isolated disposition and retain keys needed by other intact blobs.

### 8.2 Official AeadSerializer assessment

The official datastore-tink integration is an alternative, not the implementation baseline. It wraps a serializer with Tink AEAD; its documented example uses a Keystore-protected keyset and associated data. Alpha10 changed cryptographic exceptions into CorruptionException. That change is particularly relevant to accidental reset handlers. [Official encryption release notes](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha07)

Qualification is the same whether encryption is custom integration or AeadSerializer: no plaintext fallback/keyset, explicit key creation vs lookup, context binding, safe unknown-state handling, exact migration and cutover, and no reset corruption handler. Reassess it at dependency freeze. Prefer it if a suitable stable release exists and eliminates material custom code without compromising the protocol; an alpha exception requires a documented risk/maintenance owner and the full tests. Do not swap it mid-campaign without re-baselining. Platform Android Keystore/JCA APIs are the stable encryption building blocks in the recommended design.

## 9. Qualification harness, fixtures, and evidence contract

### 9.1 Common setup and observable states

Run only on disposable installs with synthetic secrets. Prepare source installs using actual supported legacy APKs, not just hand-written XML. Include the inspected alpha06 build, any supported historical PBKDF2 build, and an interim 1.1.0 build if it is shipped. Record package, signing identity, version, source APK digest, resolved dependency graph, fixture recipe, and observed baseline state.

Fixture families:

- **F0:** genuinely fresh app data; no records, DB, blobs, or applicable aliases.
- **F1:** configured Argon2id PIN, deterministic test-only verifier inputs, several protected apps, non-default retained settings, valid SQLCipher DB with logical sentinel rows.
- **F2:** recognized PBKDF2 schema including allowed historical missing-default fields; separately, deliberately partial/invalid credentials.
- **F3:** lockout count 0, 1, 4, 5, and higher ladder stages; future/expired deadlines; wall-clock jumps; near-expiry deadlines. Preserve exact raw values as well as derived remaining time.
- **F4:** valid legacy data plus interrupted target/control preparations; mixtures of generations; missing/corrupt active records; stale legacy count/deadline after a durable new reset.
- **F5:** retained blobs of zero/minimum/maximum supported size and multiple chunks, metadata/index entries, and one damaged blob among intact peers, if retention is selected.
- **F6:** missing aliases, decrypt/tag failure, unavailable Keystore, read-only/low-space storage, and restored files without original keys. Distinguish injected exceptions from actual platform failures.

Use distinct synthetic old/new values so accidental fallback is observable. For example legacy lockout=(9, future deadline A), new active=(0,0), then new active=(2,0). A source key equality assertion must compare all exact SQLCipher input bytes, not merely key length, record presence, or successful generation of some key.

Every test records three independent views: **M** (in-memory readiness/enforcement/operation outcomes), **P** (durable control and record generation/values from a fresh reader), and **U** (user-visible status, allowed actions and authorization/session result). A successful UI unlock alone proves none of the migration assertions.

### 9.2 Harness changes to implement later

1. Add a pure JVM migration/authority state-machine oracle with an operation ledger and simulated durable store. Include before-commit, after-commit-before-ack, throw/false, stalled, cancelled, corrupt, and missing outcomes. Compare public outputs and persisted state; do not copy implementation control flow into the oracle.
2. Add migration-specific integration tests against the actual pinned DataStore implementation with a fake key provider. For any storage-port adaptation, run the existing [LockoutManagerTest](../../../app/src/test/java/com/applock/security/LockoutManagerTest.kt) compatibility checks with their established expected behavior; reference the owning workstream's regression results rather than extending its recovery campaign here.
3. Add debug-source-set fault hooks around legacy reads, key lookup/generation, serialization, target commit, authority commit, activation, cleanup, and key deletion. Control via app-private files written by adb run-as before construction; PID/operation-index barrier acknowledgements go to logcat. No new exported entry point. Reuse existing debug file/barrier conventions where useful, but put migration control and scripts in their own test namespace.
4. Add a migration-specific read-only fresh-process inspector whose test bootstrap cannot auto-migrate or open the live database first; assert first-access PID/order. It validates control/records and opens SQLCipher in non-rebuilding mode; it emits comparison booleans/record identities and synthetic state, never PIN/verifier/salt/passphrase/keyset material. Do not start two DataStores for one live file to obtain an allegedly fresh read.
5. Add host scripts with bounded waits, PID checks, exact adb serial/package targeting, exit-code propagation, and cleanup restoration. A test cannot pass if the barrier, kill, restart, or inspector did not actually run. Capture the process list and first-access ledger after every restart. Release builds must contain no fault wrapper, test bypass, or secret inspector.
6. Exercise real storage behavior with disposable permission/corruption/space drills in addition to wrapper faults. Serializer/pre/post-operation barriers do not expose every internal fsync/rename instruction. Use controlled partial-stream failures, random timed kills of the unmodified pinned library, and, if necessary, a separately labelled test-only instrumented library to study internals. Evidence from modified library code cannot replace runs against the shipping library.

### 9.3 Kill-boundary matrix

For **every boundary below**, run kill-before and kill-after in each applicable fixture, then restart at least twice to verify idempotence. For a write boundary, separately inject pre-commit failure, commit-then-lost-ack, and a stall followed by kill. Record whether the durable transition happened; the host's last log line is not the oracle. Repeat per record, per alias, per cleanup artifact, and per retained blob, not only for the first loop iteration.

| Boundary | Interrupted action | Required restart result |
|---|---|---|
| K00 | Startup inventory before any mutation | Legacy/pristine classification unchanged; no new authorization. |
| K01 | Each legacy read/decrypt/validation and DB verification | Source untouched; invalid source enters recovery; no target declared active. |
| K02 | Control-key creation | Existing data remains intact. Valid control resumes; control-key-only interruption resumes with the same key only after the §7.1 bootstrap proof. Failed proof/inventory stops, not new data. |
| K03 | Preparing control write, including lost acknowledgement | Either no committed preparation or valid Preparing. Ambiguous/malformed control stops; never assume a failed return means absence. |
| K04 | Each target alias creation | Existing authorized alias reused on resume; source and other aliases retained; no re-generation over an existing key. |
| K05 | Target encryption/write/finalization and durable acknowledgement | Target is absent/incomplete and non-active, or fully valid. Source remains authoritative; retry cannot change PIN/key/counter values. |
| K06 | Each target verification; closing/reopening for physical inspection | No activation until all records and DB checks pass. Death does not turn cached comparisons into evidence. |
| K07 | ReadyToActivate control commit | Preparing or ReadyToActivate resumes; neither grants normal access. |
| K08 | Active control commit, including after durable commit before callback | Readable old control means pre-active; readable Active means new-only; unreadable control means recovery. Never mix sources. |
| K09 | Active publication, manager/DB construction, first Ready notification | New authority survives even if no UI saw success; restart builds from new records only. |
| K10 | First post-cutover record update, durable lockout reset, PIN change or settings update | Correct selected record before/after its commit; no legacy import or second authority. Runtime enforcement for an uncommitted operation remains governed by its existing contract. |
| K11 | CleanupPending commit and each legacy file/keyset removal | New-only operation; cleanup resumes without requiring deleted legacy input. No active dependencies removed. |
| K12 | Each old alias deletion and Complete commit | Only proven unused aliases may be missing. New records and any retained blobs remain readable; no re-import when cleanup repeats. |
| K13 | Explicit ResetPending, each data deletion, each key deletion, reset completion | Ordinary operation stays blocked until complete; no old credential/lockout/session resurrected. No automatic reset on a read error. |
| K14 | Future rotation prepare/rewrite/select/retire, if implemented | Selected old or new complete generation, or visible recovery; no silent key replacement. |
| K15 | Blob temp write/finalization, file publication, index selection, old-file cleanup, if retained | One selected readable version or an isolated reported failure; no partial blob is presented as complete. |

An emulator process kill, force-stop, managed reboot, guest crash, and hardware power loss are different faults. Run both abrupt PID death and force-stop; force-stop affects component restart permissions. Explicitly relaunch when intended. Reboot without clearing app data; inspect before any new authentication. A phone with a battery is not power-loss-tested by unplugging USB. Record any untested true power-interruption boundary as a limitation.

### 9.4 Evidence bundles

For each case retain: test/candidate/source-version IDs; commit and APK/dependency digests; device model/API/build fingerprint/ABI/Keystore protection level; clock settings; deterministic random seed where applicable; initial M/P/U description; fault script and acknowledged cutpoint/PID; ordered event ledger; command exit codes; relevant redacted logcat/Perfetto; fresh-process inspector output before/after; screenshots or video of recovery/authentication status; expected-versus-observed table; verdict and defect pointer.

Retain ciphertext artifacts/digests only under the synthetic-data test policy. Compare secrets in process and emit equality booleans; do not store live secrets, PIN hashes, salts, passphrases, keysets, or protected-package identifiers in campaign logs. Counts/deadlines used in device reports must be synthetic and clearly labelled. Reports go under docs/reports/campaigns with host and filing date; do not overwrite failed evidence on rerun. An absent, skipped, or unreliable inspector result is a gap, not a pass.

## 10. Qualification cases with explicit pass/fail criteria

All cases inherit §9's evidence bundle. The six required claims are Q01–Q06; the remaining cases close integration and boundary risks. Proposed expectations are not observed results.

### Q01 — Preserve credentials and the exact SQLCipher passphrase

**Setup:** F1/F2/F3 from every supported source version, including non-default hash parameters and a passphrase fixture whose hex text cannot be confused with decoded bytes. Include an expired lockout and a future lockout. Establish protected selections/settings and logical DB sentinels; quiesce DB/WAL for any file-level comparison.

**Fault/action:** First run healthy upgrade; then transient failures at each source-read and target-write step, followed by recovery/restart. Copy records without a PIN submission. Run correct/incorrect PIN verification only after inspecting completed migration, so upgrade-on-verify cannot hide a changed verifier.

**Expected M/P/U:** M stays not-ready until activation; P contains byte-identical verifier/salt/parameters and exact DB input secret, unchanged count/deadline, and the same logical protected data/settings; U resumes the existing credential flow, not setup. An incorrect PIN remains incorrect and is counted once. A correct PIN uses the approved lockout policy.

**Pass/fail:** All field/secret comparisons and non-rebuilding SQLCipher opens pass. No new passphrase is generated for an installed DB. No migration-created session, relaxed deadline, or silent empty protected set. A changed key, missing parameter, or successful open of a newly created empty DB is failure.

**Additional evidence:** In-process equality booleans, algorithm/parameter classification without verifier bytes, DB identity/sentinel comparison, source/target record generations, and key-generation-call ledger showing zero replacement generation for the DB secret.

### Q02 — Recoverable, unambiguous authority at every migration boundary

**Setup:** F0–F5 as applicable; enumerate all K00–K15 boundaries into a machine-readable campaign matrix. Distinguish library commit from application callback acknowledgement.

**Fault/action:** Kill before/after every applicable boundary and during gated writes; relaunch and inspect before accepting a PIN; restart again and resume preparation/cleanup. Also exercise failed/uncertain Active commits, control corruption, and missing control with surviving target files.

**Expected M/P/U:** Pre-active keeps intact legacy authority while the new binary remains Checking/recovery; post-active uses only new records. Ambiguous authority produces explicit recovery with original data retained. Repeated execution reuses identities/keys and does not duplicate transformations. U never sees a healthy partial dataset.

**Pass/fail:** For kill-only interruption of valid data, exactly one authority is proved and the migration resumes without data reset, including the control-key-only prefix. No mixture, new-key overwrite, false Active/Ready, duplicate user operation, or permanent spinner without recovery controls. For additional unreadable/corrupt authority faults, safe stop and retained data are mandatory, but do not count as evidence of successful automatic migration: repair/retry must re-establish the same authority, or the case receives an explicit unrecoverable-data disposition under Q06. The bootstrap proof is a release-blocking obligation if it cannot be established.

**Additional evidence:** Completed boundary matrix with actual PIDs and persisted state classification, activation ledger, second-restart comparison, and proof that invalid cases made zero authorization/Room-rebuild calls.

### Q03 — No stale legacy fallback after cutover

**Setup:** Active new lockout=(2,0); legacy=(9,future A). Also invert the relationship so legacy is more permissive than new. Keep both sets decryptable.

**Fault/action:** Make new lockout or control unreadable, missing, wrong-generation, or unauthenticated; restart. Leave legacy untouched. Restore the new record and retry; repeat with a transient Keystore lookup failure.

**Expected M/P/U:** The migration/storage layer reports the failing required record without substituting a value; durable files are preserved. There are zero legacy-reader calls after Active. U follows the owning caller's approved storage-error contract, not a state copied from legacy. Recovery returns to the selected new generation. This case does not select a runtime lockout read-failure policy.

**Pass/fail:** No fallback read, legacy re-import, counter substitution, or “no PIN/no apps” default. Any legacy-derived allow or deny is failure, even if it happens to look stricter.

**Additional evidence:** Per-source read ledger, before/after ciphertext digests, startup status, and fresh-process synthetic values after repair.

### Q04 — A durable reset cannot be undone by importing old state

**Setup:** Legacy count 9/deadline A; activate an equivalent new record. Use the existing manager to make a reset to (0,0) durable in the new store. Keep the old legacy file unchanged so re-import would be observable.

**Fault/action:** Kill after durable commit before its acknowledgement, and after acknowledgement. Restart with normal/missing/corrupt new data and intact legacy. Repeat migration initialization and cleanup several times. Also commit a later new-store count of 2 and repeat, to detect migration overwriting subsequent legitimate changes.

**Expected M/P/U:** With readable active data, restart loads (0,0), or the later committed (2,0), from the new store. Missing/corrupt active data produces the storage/recovery outcome required by the owning caller; the migration layer never selects legacy deadline A. No migration-created authentication/session or user event occurs.

**Pass/fail:** Zero legacy re-import and no replacement of a later committed value by the migration's initial copy. This case qualifies preservation of committed state across format cutover; it does not test recovery of a reset that failed to persist.

**Additional evidence:** Durable-reset cutpoint, exact synthetic P values, selected-generation and source-read ledger, repeated-start/cleanup results, and user-visible behavior under the existing caller contract.

### Q05 — Retain legacy keys until every dependent store is disposed

**Setup:** Shared legacy MasterKey alias, all three preference stores, library keysets, and two retained blobs; only a subset migrated. Include a corrupt blob whose disposition remains unresolved.

**Fault/action:** Trigger cleanup after each subset; kill around each file/keyset/alias deletion; restart cleanup. Inject alias enumeration/deletion failure. Attempt cleanup from stale progress metadata.

**Expected M/P/U:** Migration of one store never retires a key used by another. The dependency ledger is conservative when uncertain. Remaining valid legacy data is still decryptable; new records remain active; a damaged blob is reported separately. Cleanup failure delays removal rather than creating a new key.

**Pass/fail:** No alias deletion while any supported reader or retained artifact still needs it. Only after all dependencies have migrated or approved retirement may deletion occur. Retry after an already-deleted unused alias is harmless. Any lost decryptability caused by premature cleanup is failure.

**Additional evidence:** Redacted alias/dependency graph and deletion order, per-dependent read results, blob dispositions, cleanup state across restarts, and first/last supported upgrader inventory.

### Q06 — Missing keys, corruption, and restore faults never create fresh security state

**Setup:** Established legacy and active new installations. Remove each required alias separately; corrupt/truncate each record and header; restore ciphertext/control/DB without original keys; restore only selected subsets; supply an unsupported version. Keep valid comparison copies under test control.

**Fault/action:** Start, submit a correct test PIN/biometric if the surface permits, retry, reboot, and retry again. For transient faults restore access to the same key/data. Exercise explicit full reset as a separate, user-confirmed action, interrupting it at K13.

**Expected M/P/U:** The decoder/coordinator never supplies fabricated fresh data. Incomplete migration, unreadable credential material, or unavailable database-opening material prevents normal startup and shows Action required/Protection interrupted with recovery guidance; no session or new DB secret is created. A lockout-only storage fault is passed to the approved caller contract without selecting new enforcement behavior here. Transient recovery reuses the same source/state. Unrecoverable data loss offers only the approved complete reset route; confirming reset does not authenticate.

**Pass/fail:** Zero automatic key generation, security-default writes, DB recreation/move-aside, or legacy fallback on read failure. All intact files remain unchanged until an authorized recovery/reset action. Explicit reset completes without later resurrection. An inability to recover encrypted data is honestly reported, not counted as successful migration.

**Additional evidence:** Key-generation/recovery-call counters, preserved file digests, merged manifest/backup transport observations, fresh-process results, reset confirmation UI and no-session assertions.

### Q07–Q18 — Integration, abuse, and performance matrix

| Case | Setup and fault/action | Expected M/P/U and pass/fail | Additional evidence |
|---|---|---|---|
| Q07 Fresh install and partial setup | F0; fail or kill each key/record/DB/setup-PIN write; retry and reboot. Include orphan-control-key state. | No DB exists using an undurable new secret; incomplete setup is visible, no PIN creation success before commit, no session after restart. Explicit initialization is the only path allowed to create defaults. | Creation order, target identity reuse, setup UI, exact key/DB relationship. |
| Q08 Concurrent access | Simultaneous activity/service/watchdog initialization, both auth surfaces, duplicate Hilt/test construction, concurrent migration attempts; stall the first operation. | One coordinator/one active store instance/file/one ordered writer. Later writes cannot overtake a suspended earlier write. Duplicate user submission verifies/counts once. Readiness is not inferred from “isPinSet=false.” | Thread/operation timeline, constructor counts, queue oracle, duplicate event counters. |
| Q09 Async repository and migration cancellation | Cancel the caller awaiting setup/PIN-change persistence or a migration step; dismiss its UI while DataStore is stalled; release before/after commit; restart and inspect. | The application-owned store is not destroyed by UI cancellation. Lost acknowledgement is reconciled from selected durable state; no duplicate key creation, legacy rollback, false setup success, or late UI/session completion. Ordering remains intact and control events remain responsive. Existing manager lifecycle behavior is checked only through its referenced compatibility suite. | Caller/transaction timeline, commit boundary, selected P state, UI/request tokens, responsiveness trace. |
| Q10 Lockout record format preservation | Migrate count 0/1/4/5/higher and future/expired deadlines; interrupt copying; allow a deadline to expire during migration; restart and inspect before new authentication. | Exact count and wall-deadline values survive. Migration neither restarts a duration nor converts a wall timestamp into a monotonic timestamp; elapsed time may naturally expire the deadline. No additional enforcement field or policy is introduced. | Before/after values, clocks, generation, and repeat-import results. |
| Q11 Tampering/parser limits | Flip each envelope field/tag/ciphertext region; swap credential/lockout files or generations; truncate/append; oversized lengths; invalid hash cost/enum/count. | Reject before use; no unbounded allocation/work, secret exception, parser default or setup fallback. Valid older whole-record replay is tested separately and documented as a freshness limitation where undetectable. | Mutation seed/corpus, bounded CPU/memory measurements, rejection category, replay disposition. |
| Q12 Crypto/key concurrency | Equal plaintext repeated many times, parallel store operations, retries, rotation fixture; inject wrong-purpose/missing alias and RNG/key-generation failure. | Fresh randomized ciphertext/nonces for writes; existing aliases never overwritten; purpose/AAD checks reject swaps. Zero plaintext key fallback. Sampling is regression evidence, not mathematical proof of nonce uniqueness. | Non-secret nonce/ciphertext comparison statistics, key-provider call ledger, reviewer analysis. |
| Q13 Credentials and rehash | Correct/incorrect PINs; PBKDF2 success then failed rehash write; PIN change commit-then-lost-ack; concurrent old-PIN submission; malformed/high-cost parameters. | Migration itself never rehashes; valid old verifier remains if upgrade fails. Wrong PIN counts once; storage failure is not wrong PIN. PIN change reconciles actual durable revision; late old-credential result creates no session. | Verifier equality booleans, operation/result categories, credential revision/request ledger. |
| Q14 Database protection | Existing DB with valid key; absent/wrong/corrupt key record; DB corruption/schema mismatch; WAL present; settings non-default. | No migration-triggered rebuild, new secret, empty protected list, or silent settings default. Logical data preserved on valid path; failure produces safe status. Keep Room schema changes out of this migration. | Non-rebuilding open trace, sentinel comparison, no-recovery-call assertion, DB/WAL inventory. |
| Q15 Backup/restore/device transfer | Inspect release manifest and rules; attempt available cloud/local and D2D paths; restore all/subsets on same and another disposable installation/device. | Exclusions work where platform exposes the test; unexpected restored files are rejected without keys or valid authority. No claimed cross-device recovery. Same-device valid snapshot replay limits explicitly dispositioned. | Transport commands/results, rules/APK digest, first-access ledger, inability-to-test gaps. |
| Q16 Upgrade/downgrade | Every supported direct/skip upgrade; legacy-to-intermediate-to-final; Active to compatible rescue build; attempt normal old-version install and forced debug downgrade in isolated fixture. | Supported upgrades preserve data. Compatible rollback reads new authority. Unsupported old APK cannot be presented as supported; a skipped legacy install lacking a reader blocks dependency removal. | Version/signature/install outcomes, source-matrix coverage, reader compatibility tests. |
| Q17 Retained blob path | F5; stream/tag/index failure; low disk; K15 kills; concurrent preview/export and cleanup; unsupported blob version. | No plaintext temp files, partial authenticated stream presented as complete, overwritten sole original, or premature shared-key retirement. Old/new index authority is explicit. Omit only with approved retirement evidence. | Stream equality/length booleans, encrypted index lineage, file lifecycle, memory/throughput. |
| Q18 Healthy and degraded responsiveness | Identical baseline/candidate fixtures and workload; cold/warm launches, PIN submissions, screen off/dismiss, slow Keystore/storage, long persistent fault. | No runtime invariant violation, main-thread storage/hash work or new storage ANR. Meet §12 thresholds and report healthy latency/availability costs; no performance superiority claimed without comparable data. | Perfetto, distributions/raw measurements, operation rate, memory/CPU, UI response video. |

For each row, a fail includes incorrect M, P, or U even when the other two look correct. A no-crash test alone is insufficient. Expected failure/recovery tests pass only when the specified failure was proven to occur and the safe response was observed.

## 11. Decisions requiring review before implementation

Record each decision with owner, date, rationale, alternatives, affected requirements/invariants, and remaining migration/dependency risks. This proposal's defaults are not approvals.

| ID | Recommended decision to review | Consequence / blocking scope |
|---|---|---|
| D1 Work-package authority | Separate governed dependency-replacement work package; explicit exception if placed in M7. | New files/control records and any Room blob-index change require scope approval. This documentation work needs no exception. |
| D2 Format and serializer | Stable typed DataStore, protobuf-lite whole-record Keystore AES-GCM; independent format/key/error review. | Pin compatible build/plugin/runtime versions and size bounds; do not infer compatibility from documentation examples. |
| D3 Migration/bootstrap recovery UX | No authorization from incomplete migration, unreadable credentials, or missing database-opening material; visible recovery and escape controls. | Review availability and recovery wording for the replacement. Runtime lockout-only fault enforcement and retry policy are external inputs, not decisions in this plan. |
| D4 Key policy | Purpose-separated non-exportable AES aliases, no per-use auth/StrongBox-only requirement; generate only in authorized transitions. | Review hardware boundary, boot behavior, memory exposure, alias inventory and lack of general anti-rollback. |
| D5 Authority/cutover | Global encrypted control selecting separate records; no legacy fallback after Active; explicit ambiguous-state recovery; prove control-key-only bootstrap ordering. | Adds a control availability dependency. Kill-only prefixes must resume without reset; safe stopping on a further read fault is not proof of that claim. Compare a per-record protocol only if it proves equivalent cross-store consistency. |
| D6 Blob retirement/preservation | Align with M8 removal for v1.0.0; identify installed data and approved disposition before deletion. | Retained users/data require streaming migration or continued legacy reader/key retention. No inferred deletion authorization. |
| D7 Protected settings | Resolve biometric choice and retained relock-setting handling against DDS. | Decide separate encrypted record now versus named linked conformance slice; preserve current values in either case. |
| D8 Rollback/upgrade support | New-format-compatible rescue build; explicitly enumerate supported source versions and skipped-upgrade policy. | Cannot remove the only decoder for a still-supported legacy source. Forced downgrade/whole-snapshot rollback remains a disclosed boundary. |
| D9 Acceptance budgets/fleet | Preserve applicable project responsiveness limits; freeze cold-start/setup/migration time and resource budgets after baseline, before replacement comparison; settle API 30–35 vs build range coverage. | No retroactive threshold relaxation. Missing device/API coverage is a gap, not success. |
| D10 Existing DB recovery | Separate key/migration failure from AppLockDatabase's rebuild path; reconcile remaining corruption recovery with v1.0.0 safe-state policy. | Review R-004/FR-228/229 evidence impact; no silent change to an accepted risk disposition. |
| D11 Authentication integration | Async caller state and single admission protocol across self-gate/legacy/future overlay; typed storage vs incorrect-PIN results. | Changes callers as well as storage. New session/cancellation/accounting evidence is mandatory. |
| D12 Legacy absence cases | Conservative migration eligibility; no unproved inference that absent security data was never initialized. | Some old partial installs may need visible recovery rather than automatic migration. Document supported cohorts and user consequences. |
| D13 New encryption API | Recheck datastore-tink maturity at freeze; stable reviewed serializer remains baseline unless a replacement qualifies. | Alpha use or changing serializer during campaign requires a new review/baseline. |

An ADR must capture the selected technology and security boundary, with references to the living parameter sources rather than freezing volatile version/fleet values into accepted decision text. Follow governance's supersession numbering; do not reserve an ADR number in this proposal. Spec interpretation/deviation approval, RTM changes, risk updates, release notes, and recovery UI wording belong in their implementation/acceptance slices, not fabricated evidence in this document.

## 12. Concrete implementation slices and phased execution

The following is planned work only. Each slice is independently reviewable; no partially wired migration ships merely because one slice passes unit tests.

| Slice / gate | Files and interfaces expected to change or be added | Work and prerequisites | Exit evidence |
|---|---|---|---|
| S0 — Decisions and baseline | New ADR through governance; campaign/runbook; eventual roadmap/risk/RTM changes only when their state actually changes | Resolve D1–D13, source-version/upgrade cohort, migration recovery and baseline devices. Freeze the existing storage comparator and map all key consumers. | Reviewed design/protocol; fixture manifest; baseline measurements; no implementation claim. |
| S1 — Pure schema, crypto boundary and oracle | Proposed security/storage interfaces, immutable record schemas, envelope codec, platform key-provider adapter; gradle/libs.versions.toml and app/build.gradle.kts | Pin stable dependencies; build Kotlin/Hilt/serialization combination and both release/debug variants. Separate JVM crypto fakes from Android Keystore adapter. | Format review, parser/AEAD/JVM properties, min/runtime compatibility, release dependency/APK inspection; Q11/Q12 model coverage. |
| S2 — Coordinator and database startup | Proposed SecurityStorageCoordinator, SecureStoreRegistry and MigrationControlStore; AppLockApplication.kt, di/AppModule.kt, data/AppLockDatabase.kt, LockPolicyManager and DAO repository initialization | Introduce async readiness without switching existing persisted authority. Remove hidden key creation and isolate non-rebuilding DB open. Add test-only startup bypass for inspector, no release bypass. | Startup/threading tests, no permissive unknown policy, Q07/Q08/Q14; architecture rules. |
| S3 — Repository/authentication contracts | CredentialRepository.kt, DatabaseKeyProvider.kt, AuthGateViewModel.kt, MainActivity.kt, LockScreenActivity.kt, OverlayLockPresenter.kt, shared PIN UI callbacks, ProtectionWatchdogService.kt; direct-construction tests | Async typed outcomes, once-only admission, durable setup/change, exact key loading, no UI crypto/I/O. Preserve existing valid hashing semantics. | Q01/Q09/Q13 plus both live self-gate and legacy flows; future overlay harness separately labelled until wired. |
| S4 — Lockout adapter compatibility | LockoutManager.kt/LockoutStorage interface only as needed for the port, proposed DataStoreLockoutStorage, debug/release LockoutStorageFactory.kt, affected callers and adapter tests | Integrate DataStore while preserving ordering, publication and lifecycle semantics. No new recovery algorithm, enforcement rule or retry schedule. | Q04/Q08/Q10 plus referenced existing manager-contract regression evidence; FR-174 evidence/RTM treatment in the same implementation change. |
| S5 — Migration and cleanup | Proposed LegacySecurityReader, SecurityStoreMigrator, dependency ledger; debug hooks, androidTest inspector/fixtures, scripts/security-storage; backup XML and manifest; optional SettingsRepository slice | Implement §§7/9 protocol with every K cutpoint, explicit fresh classification, no fallback, safe old-data failure, conservative cleanup. Legacy dependency remains. | Q01–Q06 and Q15/Q16 on actual stores; every boundary accounted for; no cleanup without key ownership proof. |
| S6 — Blob disposition | EncryptedFileStore.kt, VaultRepository.kt, IntruderCaptureManager.kt and feature/DI references; optional new streaming adapter/index migration | Only after D6; coordinate M8 removals. No blob deletion or schema alteration hidden in small-record migration. | Q05/Q17 or approved retirement/cohort evidence; legacy readers/keys disposition. |
| S7 — Fleet qualification and release rehearsal | Host-tagged docs/reports/campaigns, release/R8 and upgrade artifacts, recovery UX, compatible rescue build | Run complete matrix, real restarts/reboots and persisted inspection; independent design/code review; rehearse staged rollout and support procedure. | Q01–Q18 evidence, hard-limit results, reviewed migration/dependency risks and signed acceptance. |
| S8 — Dependency removal | Remove security-crypto alias/declaration/imports/classes; retire migrated legacy adapter/key code only where supported upgrade graph permits | Meet §14 for all retained and removed consumers, including skipped upgrades. Re-run affected release/upgrade tests after actual removal. | Dependency graph and APK absence, direct-upgrade/rescue proof, same-change RTM/risk/ADR implementation records. |

### 12.1 Measurements and acceptance thresholds

Use the applicable project responsiveness limits as integration constraints: zero applicable invariant violations; p95 memory state read/admission at most 10 ms; p95 screen-off/dismiss at most 100 ms while replacement storage is stalled; no new storage-induced ANR. Healthy visible-authentication latency triggers review if p95 increases by both more than 10% and more than 20 ms. Compare like-for-like builds, thermal state, PIN/hash fixtures and workloads. Source: [M7 plan](../M7_PLAN.md). These checks qualify the replacement's integration cost; they do not recreate the separately owned hardening campaign.

Measure separately: time to verified startup/readiness; first and warm Keystore lookup/encrypt/decrypt; PIN hash time; DataStore transaction time; admission-to-durable interval; UI result latency; migration duration per record/blob; cold restart and reboot recovery; memory allocations/peak decrypted buffers; write counts/bytes and retry operations per minute; control/storage event-loop responsiveness while disk is held indefinitely. Report p50/p95/p99/max, sample count, raw samples, and uncertainty rather than just one average. Include whether cold-cache/first-key runs were separated from warm runs.

Proposed minimum sampling: 30 cold process launches per mandatory device/candidate/fixture category and 1,000 warm state/admission operations per measured workload; authenticate only within allowed test state, never defeat lockout to inflate throughput. Increase samples where distributions/thermal effects make comparison inconclusive. Correctness at a deterministic boundary is exact, with no tolerated lost fields or duplicate authorization events; repetition samples race exposure but does not prove its absence.

Startup/setup/migration budgets and CPU/memory limits need baseline distributions and D9 approval before comparing candidates. These are additional acceptance criteria; the 10 ms admission limit is not a requirement that a durable disk write or PIN hash finish within 10 ms. A migration that indefinitely prevents a legitimate user's access is an availability failure requiring explicit disposition, even if it rejects all unauthorized access.

### 12.2 Fleet coverage and what each layer proves

| Layer / fleet | Required work | Limits of the evidence |
|---|---|---|
| JVM pure model | Exhaustive bounded operation sequences for migration/cutover/order/reset; deterministic faults at all K boundaries; randomized schedules with saved seeds; codec validation and fake-key paths. | Proves modeled transitions and public behavior, not Android Keystore, actual fsync, process startup, OEM backup or reboot. |
| JVM actual DataStore integration | Pinned stable library with temporary files; serialized updates, exceptions, repeated lifecycle ownership, cancellation/ack ambiguity and default sentinel behavior. | Host filesystem and fake encryption do not establish Android durability/security. |
| NucBox G5 Android emulators | Full mandatory API 30/31/32/33/34/35 acceptance range, plus API 36 build/forward lane; add missing AVDs as prerequisites. API 26/29 are build-range compatibility checks, not automatic v1.0.0 support claims. Sequential runs; all K/Q cases on core API 30 and 36 lanes, healthy/upgrades/error/backup-rule coverage across remaining levels. | Real Android processes/filesystems but emulated hardware/Keystore; emulator snapshots are not true phone power-loss evidence. Fleet availability does not equal a passed campaign. |
| Moto G 2025 physical phone | Actual installed OS/API recorded; current fleet record is API 35. Full small-record migration/kill/restart/reboot/key/error matrix, latency, screen lock/enrollment/deep sleep, release-like graph smoke and recovery UI. Real platform file faults complement injected ones. | Do not infer StrongBox or other OEM behavior. Record inaccessible failure modes and any impossible D2D setup. |
| Additional physical/device-transfer lane | A second disposable phone or approved device lab for D2D and OEM differences; include physical API 36 if release claim requires it. | Missing second device means transfer remains an explicit gap; injected restored files test containment, not the transport exclusion itself. |
| Release artifact checks | Production package graph, signing/upgrade paths, R8 behavior, merged manifest, no debug hooks, dependency/APK inventory, compatible rescue build. | Debug wrapper success alone does not prove release equivalence. |

Use [fleet index](../../reports/README.md) and current build/test configuration as execution sources. Prefer prodDebug disposable installs for deterministic hooks, then release-like artifacts for unaffected real paths. Record uninstall/cleanup procedures and ensure no personal installation is overwritten. No real-device runs are performed by this proposal.

## 13. Alternatives and remaining risks

| Alternative | Security and legitimate-user availability | Latency, complexity, migration implications | Decision direction |
|---|---|---|---|
| Retain current adapter indefinitely | Avoids immediate migration mistakes but leaves a discontinued dependency. | Lowest short-term change; accumulating maintenance risk. | Reject as long-term default; time-bounded bridge with owner/review date is reasonable. |
| Stable DataStore plus reviewed Keystore serializer | Purpose-bound authenticated records and explicit failure/authority policy; key/control loss still makes data unavailable. | Moderate integration/migration work; Keystore cost on each write; measured, not assumed faster. | Recommended small-record target, conditional on qualification. |
| Official datastore-tink encryption | Can reduce custom encryption integration; equivalent safe recovery still required. | Currently alpha; additional keyset lifecycle and release-policy risk. | Reassess at freeze; prefer once appropriate stable/qualified component exists. |
| Custom encrypted AtomicFile storage | Can fit narrow bootstrap use and reduce caching; larger error/locking responsibility. | More owned persistence/format code; synchronous compatibility does not excuse main-thread I/O. | Reserve for measured constraints that stable DataStore cannot meet. |
| Put all state into SQLCipher Room | Database transactions may simplify some cross-record updates. | Cannot bootstrap its own key; couples authentication to DB availability; schema/architecture change and larger M7 exception. | Retain Room's current role; consider only a separate architectural comparison. |
| One encrypted DataStore containing everything | One atomic record simplifies small-record cutover. | High-frequency lockout writes rewrite credential/key data; increased coupling/exposure; conflicts with separate credential/key roles and excludes blobs. | Not preferred. Compare only with explicit requirements interpretation and measured benefit. |
| Dual-write old/new and fall back on read failure | May appear more available; permits stale credential/lockout resurrection and ambiguous authority. | Two consistency domains; complex failures and downgrade behavior. | Reject. |
| Fresh-install-only replacement | Simple new path. | Fails the requested installed-user preservation unless all legacy users are explicitly excluded with an approved, enforceable distribution policy. | Not a substitute for this migration. |

Remaining risks requiring explicit disposition:

1. Permanently unavailable storage/Keystore cannot be repaired by retry or a new library; legitimate users may need complete data reset. Control adds another availability dependency.
2. Storage-interface adaptation can accidentally change caller behavior. Require compatibility evidence against the approved manager contract without adding a separate recovery-policy decision to this replacement.
3. Complete valid-state rollback and forced old-binary execution remain outside ordinary app-controlled freshness guarantees. Partial-state substitution should fail safely; do not conflate these cases.
4. Freshness classification for old missing files is inherently limited by absent legacy metadata. Do not relax validation to make every historical partial install appear migratable.
5. Existing eager graph/database rebuild and synchronous PIN paths make this a behavioral integration, not merely a version bump. Asynchronous conversion can create duplicate/racing verification and session bugs unless caller tests pass.
6. Skipped upgrades and promised blob readability may delay dependency/key removal beyond the first successful cutover release.
7. DataStore caches decrypted values; Android/JVM memory handling and hardware key protection differ by device. No claim of full memory secrecy or universal StrongBox protection is made.
8. Clean process-death evidence is not proof of every power-loss/filesystem/OEM restoration scenario. Record gaps and release-blocking consequences with the lead.

## 14. Removal gate and final recommendation record

Remove security-crypto only when all of the following are evidenced, with no implicit pass for skipped work:

1. Approved ADR/design/recovery decisions, migration authority model, source-version support, and M7 scope treatment; independent review of key/nonce/AAD/format/error handling.
2. Q01–Q06 pass for every supported source cohort, including exact SQLCipher bytes and every enumerated kill boundary. All kill-only prefixes of valid data resume without destructive reset. Additional corruption/key-loss cases have a tested safe recovery route or an explicit unrecoverable-data disposition; safe stop alone is not successful migration.
3. Q07–Q18 complete on the agreed fleet; proposed blob cases may be replaced only by approved retirement evidence. Real restarts, reboots, persisted-state inspection and release artifact verification are present.
4. Storage-adapter compatibility is evidenced against the approved manager contract and its existing regression suite. No weakening of ordering, responsiveness, freshness, event accounting or immediate in-memory reset is hidden by the storage change. This removal gate does not require or record separate hardening residual dispositions.
5. Legacy preference/file/keyset/alias dependency graph is empty for retired artifacts, or any remaining dependency has a supported retained reader outside the removal claim. Installed users who skip a release still have an executable migration path.
6. Direct and transitive dependency inspection and debug/release APK/class scans show no remaining androidx.security.crypto use in the artifacts claimed free of the library. All MasterKey/EncryptedFile/EncryptedSharedPreferences consumer paths are migrated or explicitly retired, including tests and feature wiring.
7. Backup/restore, key loss, corrupted active state and incompatible-version behavior meet the chosen policy. No new DB key or security-state reset occurs on a read failure. Compatible rollback/rescue is rehearsed.
8. Performance budgets pass; no new relevant ANR or runtime invariant violation. Security/availability tradeoffs, unresolved device limits and report links are accepted by the lead.
9. Implementation-linked RTM rows, ADR implementation notes/index as applicable, risk register, release/recovery documentation, and changelog accurately reflect evidence. This plan alone changes none of those statuses.

Record the replacement recommendation in a dated decision/report using these fields: inspected implementation/dependency revision; selected storage/encryption design and rejected alternatives; D1–D13 outcomes; Q/K matrix links; exact source-version/upgrade coverage; fleet gaps; migration security and legitimate-user availability measurements; healthy/fault latency; key/blob cleanup proof; referenced caller-compatibility evidence; removal eligibility; owner and review date. Record remaining migration/dependency risks and follow-up work here; leave runtime hardening decisions with their owning plan.

**Recommended approach:** qualify stable typed DataStore with a centrally reviewed Android Keystore whole-record serializer for credentials, lockout, database-opening material and migration control; retain SQLCipher Room; retire excluded blobs through the governed product plan or use a separately qualified streaming adapter if retention is required. Preserve one durable authority through staged cutover, and preserve legacy keys/readers until their last supported dependency is disposed. Reassess the official DataStore encryption API at implementation freeze. Dependency removal is the final evidence-backed step, not the first implementation slice.
