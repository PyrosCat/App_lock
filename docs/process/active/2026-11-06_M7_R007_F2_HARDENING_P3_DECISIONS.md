# M7 / F2 Hardening: P3 Decisions D1–D6 (R-007)

**Date:** 2026-11-06

**Status:** Adopted by the lead between 2026-10-26 and 2026-10-29. The F2 hardening entry of `M7_PLAN.md` summarizes
these decisions. This document holds their full texts and is authoritative over that summary.

**Scope:** The six decisions that `2026-10-10_R007_F2_HARDENING_P3_DECISION_PROPOSAL.md` (the proposal) asks for, and
the lead's additions for the transition from 1.0.0 to 2.0.0. The test plan is
`2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`, and the options document is `2026-09-22_R007_F2_HARDENING_OPTIONS.md`. In
this document, D1 to D6 name these decisions, not the decisions D0 to D6 that `M7_PLAN.md` flags for review. Statements
about the code describe the code at commit `26ffe09`.

**Labels:** Option A, B2, and B3 come from the options document. C2 comes from the test plan, and the proposal calls it
count-first C2. The test plan also defines the constraints I1 to I9, cases such as X04, and the residuals R-007/n. P0 to
P5 are the phases of the F2 hardening entry of `M7_PLAN.md`. That entry also holds the P0 decisions, the P0 ranking, and
the P3 criteria. The invariants are those of section 2 of `M7_PLAN.md`, and F6 is a slice of change F in `M7_PLAN.md`.

## 1. State of the work

- **Adopted:** the decisions in sections 2 to 8.
- **Undecided:** the disposition of residual R-007/4 (section 4.2), and the selection of an admission mechanism, which
  D6 leaves to phase P5.
- **Not implemented:** the code has none of these rules yet.
- **Not verified:**
  - the transition table and JVM model of count-first C2, and the design and JVM model of Option A (D6);
  - X04 on a device, the P3 latency comparison, and the device cases of phase P4;
  - the D5 device qualification, including the wall-clock cases in which the device operator changes Settings;
  - the prediction that damage to the credential store makes the app show PIN setup, which needs a check on the Moto G.
- **Pending records:** the F2 hardening entry of `M7_PLAN.md` lists the baseline, test-plan, and proposal records that
  these decisions require.

## 2. D1: trusted state and lost records (2026-10-26)

### 2.1 Decision

Authentication requires validated credentials and validated lockout state. With trusted credentials but absent or
invalid lockout history, the app may durably install the maximum ladder count and maximum lock duration under D5, then
enforce that record normally. This replacement neither authenticates the user nor asserts that wrong PINs occurred.
Invalid credentials cannot be reconstructed. Setup requires confirmed empty credential and protected-selection state
without treating a storage failure or destructive database reconstruction as empty configuration.

For lost lockout history, D1 uses the strictest replacement. The app replaces the lost history with the strictest state
that the lockout ladder can reach: the count at its cap and the maximum lock duration. The replacement counts as trusted
only once its write is confirmed. The process that installed it tells the user what happened (section 3.2). For lost or
invalid credentials, D1 keeps the baseline rule: authentication stays unavailable, and the only route is the destructive
reset (SC-RESET-001). D1 does not include recovery through the device credential.

### 2.2 Rules

1. Authentication begins only when both the credential state and the lockout state are trusted. Validation sorts each
   store into trusted, absent, or invalid. A successful read alone never makes a store trusted.
2. A trusted credential has every required entry present. Each decrypted, uncoerced preference value has the expected
   type, the algorithm is a known one, and the salt and hash decode. Qualification 1 adds the lengths and parameters.
3. Setup requires the confirmed empty state that qualification 2 defines. Any other state with a missing or invalid
   credential is an unrecoverable data failure, handled by the baseline rule.
4. PIN setup first writes the initial lockout record and confirms the write, then saves the PIN with `commit()`. Both
   commits must succeed before setup completes, and both run off the main thread.
5. D4 and D5 define the valid values of the lockout record.

### 2.3 Qualifications

1. **Credential validation needs more than successful decoding.** Require the supported salt and hash lengths, plus
   permitted hashing parameters. Both current hash implementations produce a 16-byte salt and a 32-byte hash. Argon2
   memory, iterations, and parallelism, and PBKDF2 iterations, also need defined bounds or supported profiles. Correct
   types alone permit unusable or excessively expensive values. Successful reading followed by complete validation can
   establish trust. Keep an operational unavailable state for reads that cannot complete. A temporary read failure is
   neither proof of absence nor proof of irrecoverable corruption.
2. **The setup rule works only if "database opens" excludes replacement of a damaged database.**
   `AppLockDatabase.build()` catches an open/integrity failure and calls `recoverAndRebuild()`, which returns a fresh
   database. That database can open successfully and have no protected selections even though the original contained
   them. Setup requires confirmed absence of credential entries and a successful protected-selection query against a
   database whose opening did not discard or replace unavailable configuration. The current rebuilding path must not
   manufacture eligibility for setup. Otherwise the setup rule retains the bypass that it is meant to close. D1 treats
   genuine loss of both credentials and selections as equivalent to clearing app data. This explicit boundary does not
   mean the app has proved that the installation is new.
3. **The replacement is conservative under the selected admission and clock rules.** The ladder reaches its maximum at
   count 11. Use a defined saturating count cap at or above that level, and retain the cap after expiry. Expiry permits
   another attempt; it does not reset the count. Under C2, each subsequent PIN verification must still have its durable
   reservation first. Otherwise the replacement record alone does not close the existing restart/write-loss weakness.
   The replacement also requires:
   - No authentication while the replacement write is unconfirmed.
   - A single ordered writer, with stale reads, writes, and authentication callbacks fenced off.
   - Reuse of the stored deadline after restart; never reconstruct another full lock merely because startup runs again.
   - No invented wrong-PIN audit or intruder capture when installing the replacement.

   Each loss incident gets one replacement lock, after durable installation. Persistent write failure can keep
   authentication unavailable indefinitely; further corruption can require another replacement. Under the selected
   admission and clock rules, replacement grants no earlier PIN-verifier entry and no greater attempt allowance than any
   valid prior ladder state.
4. **The replacement avoids a new recovery authority, but it changes the baseline.** The baseline currently says
   unavailable protected preferences lead to initialization retries and, if unrecoverable, clearing application data
   (SDS 17.2). The replacement introduces a lockout-specific alternative to that behavior. That limited policy change
   must be recorded explicitly. Installing a replacement for unknown history differs from restarting a known penalty,
   which test-plan constraint I8 prohibits. The replacement itself can use the existing count and deadline fields.
5. **Existing installations.** Existing installations get no migration code and no one-time trust. Reopen that decision
   before distributing a build whose installed data must be preserved. Section 5.3 defines the reinstall cohort. Rules A
   and B of section 5.4 apply to an in-place upgrade that retains unmarked pre-release data, including the historical
   "PIN set, no record" state. D1 replacement does not apply to that data, because replacement requires a recognized
   active storage format (section 5.1, addition 3).

### 2.4 Findings added to D1

1. **A second way to look like a new install.** `DatabaseKeyProvider.getOrCreateKey()` creates a new random database key
   whenever its entry is missing, and saves it with `apply()`. If the `applock_db_key` file is damaged, the existing
   database can no longer be opened, so `build()` rebuilds it empty. The setup rule therefore permits a new database key
   only when no database file exists.
2. **A restart after a rebuild hides it.** On the next start, the fresh database opens normally. Two traces remain: a
   `.recovery-*.bak` file, which is missing when moving the old file failed and it was deleted instead, and a
   `DATABASE_RECOVERED` row that the code writes into the fresh database without checking that the write worked. So the
   fact that the database was rebuilt must survive restarts. Otherwise a second start makes the installation look new.
3. **The rebuild conflicts with the baseline.** SDS 17.2 also says "Database cannot open: do not treat protected set as
   empty", which the rebuild breaks. The crypto replacement proposal (`2026-09-24_SECURITY_CRYPTO_REPLACEMENT_PLAN.md`)
   raises this conflict as its decision D10. D1 refers to that item instead of tracking the conflict twice.

Hashing profiles behind qualification 1: the code writes only Argon2id with 19,456 KiB of memory, 2 iterations, and 1
lane, and no code writes a PBKDF2 iteration count. A supported PBKDF2 profile therefore has to come from the code that
originally wrote those credentials, which only pre-release test installations have.

## 3. D2: permissions by state (2026-10-27)

### 3.1 Invariant 4

Invariant 4 needs an interpretation, not an exception. The UI/UX baseline already qualifies PIN availability with "when
authentication is permitted".

### 3.2 States and permissions

| State | PIN check | Fingerprint prompt | Accept a fingerprint success | Screen |
|---|---|---|---|---|
| Ready | yes | yes | yes, if still Ready | PIN pad and fingerprint |
| LockedOut (timed, including the D1 replacement lock) | no | no | no | countdown; for a replacement lock, also a note that the lock history could not be read |
| Loading (startup read or check not finished) | no | no | no | "checking security state" |
| Unavailable (a read cannot complete) | no | no | no | "security state unavailable", retry status |
| Replacing (the D1 replacement is not confirmed yet) | no | no | no | same as Unavailable |
| CredentialInvalid | no | no | no | the loss explained, with clear-data guidance |
| SetupRequired | setup only | no | no | PIN setup |

Only the process that installed a replacement lock shows its note. After a process restart, the stored record cannot
show that a lock is a replacement: the count and deadline fields cannot distinguish it from an ordinary saturated count
(section 6, point 4), and D5's clock fields add no such identifier (section 7, point 3). The restarted process shows the
ordinary countdown.

Section 5.2 adds the state CompatibilityBlocked.

Enforcement:
- One admission owner decides. Every caller asks it at three moments: before a PIN check (under C2, the durable
  reservation), before opening the fingerprint prompt, and when a fingerprint success arrives.
- A fingerprint success is accepted only if the state is still Ready at that moment. The check and the acceptance happen
  in one locked step.
- A PIN result is accepted for the request that holds its C2 reservation, so a request that reaches the threshold still
  gets its own check.
- Every state other than Ready denies.
- A fingerprint success unlocks the app but leaves the PIN count as it is. Only a correct PIN, or a PIN change
  authorized with the current PIN, clears it (DDS 6.3).

### 3.3 Refinements

1. **Ready is necessary, but request validity is also required.** Fingerprint acceptance requires Ready, a current and
   unconsumed request, and a valid authentication operation. Cancellation, screen-off, credential replacement, or a
   transition into an unavailable state must invalidate the pending operation. Returning to Ready must not revive an
   invalidated operation. The new reducer already checks the process epoch and request ID. Preserve the existing checks
   and add the security-state admission rule.
2. **A C2 reservation overrides only the threshold lock created by the reservation.** A reservation does not provide
   unconditional permission to accept a PIN result. The request must remain active, the credential context must remain
   valid, and the result must remain unconsumed. A valid reserved PIN request may complete despite the lock created by
   the reservation. Cancellation, credential invalidation, and unrelated security-state failures still prevent
   acceptance. The default-deny rule applies to new admissions and fingerprint acceptance. Reserved PIN completion
   requires the separate reservation rule.
3. **Permissions during an outstanding PIN operation.** The table needs an explicit rule for reservation writes and PIN
   verification in progress. The recommended initial model permits one outstanding PIN operation across all callers and
   blocks competing PIN and biometric operations until completion or cancellation. The restriction can be an
   admission-owner condition without adding another screen state. If Loading covers reservation writes and verification,
   record the scope explicitly. Cancellation must not cancel an admitted storage write. The manager retains ownership of
   the write, as required by C2 and X08. Admission decisions and result consumption must be atomic. UI callbacks and
   external effects must run outside the manager lock to avoid recreating X13.
4. **I6 is revised, and immediate access stays.** I6 currently requires an immediate count and deadline reset after
   every accepted success, without distinguishing authentication methods. Preserving PIN accounting after fingerprint
   success requires revised wording: "An accepted authentication grants the valid session immediately. An accepted PIN
   success also clears the PIN count and deadlines in memory immediately. Persistence follows without delaying access.
   An accepted biometric success leaves PIN accounting unchanged. Failed or late persistence work does not revoke the
   accepted session."
5. **M7 is reconciled without rewriting historical evidence.** M7's biometric mapping says biometrics never touch the
   counter, while the baseline notes of the P0 decisions describe the current reset behavior. Clarify the intended rule:
   biometric results neither increment nor reset PIN accounting. Retain the current behavior in historical baseline
   reports. The implementation must preserve the PIN accounting generation and applicable pending work after fingerprint
   success. Preserving only the numeric count is insufficient. The reducer currently resets the projected lockout state
   and advances the lockout streak after either authentication method. All authentication surfaces must use the same
   admission owner. Rejected or stale results must create no session, reset, or successful-authentication event.
   Accepted fingerprint authentication preserves PIN accounting. Accepted PIN authentication resets PIN accounting under
   the revised I6.

### 3.4 Operation identity

Every PIN operation receives an admission-owner operation ID, independent of presentation request identity.

### 3.5 Availability cost

With the D1 replacement lock, an owner who uses only the fingerprint stays at the maximum count. Each mistyped PIN then
costs 30 minutes until the owner enters the correct PIN once. Under C2, an interrupted reserved PIN attempt can also
incur another 30-minute lock, including an interrupted attempt containing the correct PIN. After a D1 replacement lock
expires, fingerprint authentication can unlock normally. An accepted PIN success is still required to clear the
maximum-level PIN accounting.

## 4. D3: restart allowance (2026-10-27)

Process death, force-stop, and process restart must not remove, shorten, restart, or extend an active security delay,
and must not restore attempt capacity. Covered delays are the recorded lockout, any degraded fallback retained by the
evaluated design, and a durably installed D1 replacement lock. Normal expiry still applies.

At matched times, under equivalent inputs, storage faults, and clock conditions, execution containing process restarts
must produce no more PIN-verifier entries than equivalent execution without process restarts. A verifier entry counts
when verification begins, including a subsequently interrupted check. An outcome awaiting persistence does not justify
additional attempt capacity.

Count-first C2 retains no degraded fallback, so fallback preservation does not apply to C2, including C2 combined with
B2 reset retries. Fallback preservation applies to every evaluated design retaining a degraded fallback.

Before durable installation of a D1 replacement lock, authentication remains unavailable. After installation, process
restart resumes the stored deadline without starting another full replacement delay.

Device reboot and clock-change behavior follow D5. Any accepted departure from delay preservation must be recorded
explicitly.

Under permanent write failure, a B-only design cannot satisfy the full rule. B2 and B3 experiments may still measure
partial benefits, but successful retry cases cannot establish compliance with D3.

### 4.1 Exception for D1

"When lockout history is absent or invalid, D1 may install a full maximum-duration replacement lock, even if the
replacement increases the remaining wait compared with the lost history. The exception applies only to replacement of
untrusted history. A valid persisted replacement record retains the stored deadline across subsequent process restarts."

The exception affects the prohibition on extending or restarting delays. The prohibitions on restoring attempt capacity
and adding PIN-verifier entries remain unchanged.

### 4.2 Failed reset (R-007/4)

"After an accepted PIN success whose reset did not persist, a restart can restore the stored count and any remaining
delay under D5. The resulting over-enforcement is recorded as residual R-007/4, with disposition UNDECIDED pending an
explicit decision. A valid expired deadline does not become a fresh lock merely because the process restarts."

This sentence documents an availability limitation without marking the residual accepted or resolved. D1 replacement
remains a separate response to absent or invalid history.

### 4.3 Evidence

The 160 sampled kills of phase P2 support the observed preservation of complete records. The sampled results do not
justify excluding corruption or untested power-loss behavior from D3.

## 5. Additions for the transition from 1.0.0 to 2.0.0 (2026-10-27)

### 5.1 Additions

1. **D1 to D3, responsibility boundaries.** "Authentication coordination owns readiness, operation identity, admission,
   and result acceptance. Lockout logic owns attempt capacity and deadlines. Storage boundaries own record validation
   and durable writes. Session management grants authorized sessions. Presentation displays state and forwards input."
   Reason: The separation matches the 2.0.0 architecture and prevents `LockoutManager` from becoming responsible for
   database opening, key generation, credential decoding, and screen control.
2. **D1, unsupported formats.** "Storage validation distinguishes absent or invalid records from unsupported formats,
   incomplete migration, and uncertain storage authority. Unsupported formats and incomplete migrations remain
   unavailable without automatic replacement or destructive reset." Reason: A future format must not be mistaken for
   corruption. Automatic replacement could overwrite valid newer data or weaken a future lockout policy.
3. **D1, replacement policy.** "D1 replacement requires a recognized active storage format and a known lockout policy.
   Replacement installs the most restrictive valid state supported by the active policy. The 1.0.0 policy supplies the
   agreed count cap and 30-minute maximum." Reason: Version 2.0.0 introduces configurable retry limits and delays.
   Keeping the replacement rule separate from fixed 1.0.0 values avoids redesigning recovery for future policies.
4. **D1, released-format and migration contract, with D4 and D5.** "Before release, document the exact credential and
   lockout formats, including the D4 count meaning and D5 clock fields. Supported upgrades preserve credentials,
   protected selections, database-opening material, consumed attempt capacity, and applicable deadlines. C2 reservation
   counts and D1 replacement counts must not become claimed counts of actual wrong PINs." Reason: Disposable test
   installations need no migration guarantee. Released installations need a defined source format. Future audit and
   intruder-photo features also require accurate distinctions between actual failures, interrupted reservations, and
   replacement state.
5. **D1, database-key rule, implemented through D10 of the crypto replacement proposal.** "Missing database-opening
   material never authorizes replacement material for an existing database. An explicitly authorized migration or
   key-rotation procedure may create target material while preserving the source until verified cutover.
   Database-recovery implementation and baseline reconciliation remain owned by D10." Reason: The existing-database
   safeguard must prevent accidental replacement without blocking legitimate future migration. Keeping implementation
   ownership under D10 avoids duplicate tracking.
6. **D2, operation identity and authentication purpose.** "Authentication operation identity remains separate from
   presentation request identity and authentication purpose. The self-gate retains the package-keyed F6 interface.
   Authentication purpose distinguishes protected-app access, app/settings access, and current-PIN confirmation for PIN
   replacement." Reason: Version 2.0.0 adds authentication consumers and methods. Explicit purpose prevents a successful
   authentication for one operation from authorizing a different operation. Separate operation identity preserves
   cancellation and stale-result checks across different screens.
7. **D2, explicit method policies.** "Every supported authentication method has explicit eligibility, admission,
   acceptance, and counter-reset rules. Adding an authentication method does not change existing PIN accounting
   implicitly. Under the 1.0.0 policy, biometric success preserves PIN accounting." Reason: The 2.0.0 authentication
   requirements use broader reset wording and support additional methods. Explicit policies prevent future additions
   from accidentally restoring the current fingerprint-reset behavior.
8. **D3, storage and migration independence.** "D3 applies across supported storage implementations. Storage replacement
   and migration must preserve attempt capacity and applicable deadlines, subject to the recorded D1 and D5 exceptions
   and the R-007/4 disposition. Migration must not reactivate stale source records after verified target activation.
   Qualification covers interrupted migration, pending reservations, replacement locks, and pending resets." Reason:
   Changing the persistence library must not reopen restart weaknesses. A reusable verification contract allows the same
   security properties to be checked during the 1.0.0-to-2.0.0 transition.

### 5.2 Refinements

1. **D2, one owner for admission and acceptance.** Authentication coordination owns operation IDs and the common lock.
   Lockout state changes occur under the common lock. Admission and result acceptance are separate short transitions
   under the same lock; storage access, PIN verification, and external callbacks occur outside the lock. Storage
   completions return through coordination and undergo operation, generation, and cancellation checks before
   publication. C2 permits verification only after confirmed reservation persistence. One ordered writer remains
   required. Reason: Separate component locks would leave a race between checking capacity and accepting authentication.
   Holding the common lock across storage would violate I3. Operation IDs therefore move from the lockout manager to
   authentication coordination.
2. **D1 and D2, a separate compatibility state.** Add CompatibilityBlocked, with reasons UnsupportedFormat,
   MigrationIncomplete, and AmbiguousAuthority. PIN checks, fingerprint prompts, and success acceptance remain blocked.
   Guidance depends on the reason: install a compatible update, resume migration through the coordinator, or preserve
   storage pending recovery. No automatic replacement or clear-data guidance appears in the compatibility state. Reason:
   CredentialInvalid describes confirmed credential damage. Unsupported or ambiguous storage may contain valid
   credentials and configuration. A successful read alone cannot resolve compatibility or authority.
3. **D1, with D4 and D5, the first released format.** The first released format includes validated format
   identification, reader compatibility information, and explicit credential configuration state. Every released reader
   rejects unsupported formats without initializing or replacing security records. A marker added to encrypted
   preferences requires an explicit invariant 6 exception. A combined exception may cover the marker, C2 count
   semantics, and D5 fields, but each change needs a separate description. Reason: Future compatibility cannot be added
   retrospectively to an older reader. Two limits remain. A valid unconfigured marker simplifies credential
   classification but does not replace the database and protected-selection checks. A missing or damaged marker cannot
   prove a fresh install or identify an unknown format. D1 replacement therefore requires established storage authority
   and a known applicable policy; a marker alone cannot resolve total metadata loss.
4. **D1, D4, and D5, the crypto replacement proposal.** Revise the lockout schema and adapter contract to reference the
   selected C2 count meaning, D5 clock fields, D1 replacement rules, and D2 coordination ownership. Preserve the
   separation between storage migration and lockout-policy decisions. Record D1's rule for existing installations
   (section 2.3, qualification 5) as an explicit disposition for identified disposable pre-release builds. Retain
   migration support obligations for released builds and any installations designated for preservation. Reason: The
   crypto replacement proposal's "No new enforcement fields" and "Do not ... reinterpret existing values" describe the
   earlier contract. Updating that proposal aligns the migration design with the selected contract without transferring
   ownership of lockout policy. The reinstall disposition needs named source builds or a defined cohort. "No release
   exists" alone does not disposition every possible installation.
5. **D4 and D6, with D2 integration, the separation of real failures from consumed capacity.** Before enabling 2.0.0
   failure-based capture and reporting, introduce separate accounting for confirmed failed verifications. C2
   reservations and D1 replacement counts remain enforcement data. Persistence may use a separate failure record or an
   Option A design containing recorded outcomes. An unfinished reservation remains an unknown outcome and never becomes
   a wrong-PIN event. Reason: FR-081 uses consecutive failed authentication attempts, and FR-082 records the number of
   failed attempts. C2 and replacement counts cannot supply either meaning. Option A's reservation alone is
   insufficient. A crash after verification but before outcome persistence can still lose the result. Version 2.0.0
   therefore needs an explicit outcome-durability rule and treatment of unknown outcomes. Existing C2 history cannot be
   converted into an exact failure history during upgrade.

### 5.3 Installation cohort and migration coordinator

1. **D1, the disposable installation cohort.** "The reinstall disposition covers project builds with versionCode 1 under
   com.applock.dev, com.applock.qa, com.applock.staging, and com.applock. A versionCode of 1 remains reserved for
   disposable builds. The first build requiring preservation of installed data uses versionCode 2 or higher. Before
   replacement, the test operator records the installed application ID and versionCode for each installation." Reason:
   The cohort becomes identifiable on each device. Recording the version before replacement matters because the version
   displayed after an upgrade identifies the new binary, not the origin of retained data. Application version remains
   separate from storage-format identification.
2. **D2, the migration coordinator.** "MigrationIncomplete permits migration recovery only through the migration
   coordinator. Authentication coordination continues to deny authentication until migration completes and
   security-state validation succeeds." Reason: The migration coordinator owns migration and storage-authority
   transitions. Authentication coordination owns authentication readiness, operation identity, admission, and result
   acceptance.

### 5.4 Rules A and B

The term "unmarked pre-release data" applies throughout, and the setup checks determine eligibility.

- **Rule A:** "An in-place upgrade retaining unmarked pre-release data enters CompatibilityBlocked when completed D1
  checks establish that the fresh-setup conditions do not hold. Authentication remains unavailable. The compatibility
  screen offers no clear-data guidance. The test operator completes an external uninstall that removes application data,
  followed by a clean installation. Blocking is the intended outcome for the unsupported upgrade. A check that cannot
  complete leaves the state Unavailable, not CompatibilityBlocked. Authentication remains unavailable while the app
  retries. A read failure alone does not require reinstalling."
- **Rule B:** "D1 fresh-setup checks apply to both clean installations and installations retaining unmarked pre-release
  data. When every condition is confirmed, setup is allowed. Setup retains the existing database key when the database
  opens normally without rebuilding, writes the required format markers, and follows D1's confirmed-write ordering for
  the initial lockout record and PIN."

## 6. D4: invariant 6 and count-first C2 (2026-10-28)

The exception should be named for the lockout record. Its C2-specific provisions must be removable without removing D1's
requirements or the overflow fix.

1. **Baseline changes must be recorded separately by decision.** D1 replacement writes the maximum enforcement count
   without establishing that any PIN verification failed. That changes the meaning of the stored record independently of
   the admission design. The baseline must permit the synthetic replacement state and define the count cap. Initial
   setup writes and their ordering remain owned by D1. If C2 is selected, an additional baseline change permits
   reservation accounting before verification. The count can then include reservations whose verification never starts
   or whose outcome remains unknown. A reservation at or above the threshold also installs its deadline before
   verification. D2 permits the reservation owner to complete despite that reservation's lock. Reason: selecting Option
   A instead of C2 would remove the C2-specific changes, but would not remove D1's replacement policy or the required
   overflow protection.
2. **The lockout-record exception has two parts.** The narrowly scoped exception to invariant 6 covers:
   - Required under the selected D1 policy, regardless of admission design: count saturation, initial lockout-record
     writes during PIN setup, and D1 replacement writes.
   - Conditional on selecting C2: reservation accounting in the count, saving the applicable deadline with the
     reservation, and confirming reservation persistence before PIN verification.

   These changes reuse the existing applock_lockout file, failure_count key, lockout_until key, and value types. The
   exception introduces no additional key, file, or table. Format metadata required by D1, clock fields required by D5,
   and any additional record required by Option A need separately described persistence exceptions. Approval may combine
   the exceptions while retaining each change's scope. A valid initial lockout record establishes lockout
   initialization. Record presence alone establishes neither trust nor completed PIN setup. If D6 selects Option A, the
   shared provisions remain. Only the C2-specific provisions are removed or replaced by the approved Option A contract.
   Reason: the exception follows the required behavior and its owning decision, rather than depending entirely on one
   admission candidate.
3. **The count is enforcement state, and the policy is explicit.** "The count represents consumed PIN-attempt capacity.
   Each ordinary C2 reservation increases the count before verification, up to the policy cap. A persisted reservation
   remains consumed when verification never starts, is cancelled, or is interrupted. D1 replacement writes the cap
   without asserting any failed verification. Expiry and biometric results preserve PIN accounting." Accepted PIN
   success clears the count and deadlines in memory immediately. Persistence follows through the ordered writer. A
   restart before reset persistence remains subject to R-007/4. For the fixed 1.0.0 ladder, cap 11 is sufficient. The
   exact cap should be recorded before implementation. Saturation must occur before arithmetic can overflow. At the cap,
   another permitted reservation must still durably save the new deadline before verification. The threshold, cap,
   delays, and reset rules belong to one fixed 1.0.0 policy implementation. Screens and storage adapters should not
   independently reproduce those rules. Reason: 2.0.0 permits configurable retry limits and delays. Centralizing the
   fixed policy supports that transition without introducing a configurable framework in 1.0.0.
4. **Existing installations follow D1, and future conversion is explicit.** "Pre-release installations follow D1's
   fresh-setup and compatibility rules. D4 introduces no migration of configured unmarked installations. Within the
   recognized active format, counts outside the specified range are invalid. Supported future migrations validate the
   source format and apply an explicit conversion rule." A saturated count no longer reveals the number of reservations
   beyond the cap. The two existing fields also cannot distinguish an ordinary saturated count from a D1 replacement
   count. A future migration must therefore preserve enforcement conservatively without reconstructing an exact attempt
   history. Normalizing a larger legacy count can be considered within a supported migration; it should not become a
   general read-time rule. Reason: the storage contract must describe what the number means before a future policy can
   safely interpret it.
5. **A return to pre-C2 binaries is unsupported.** The numerical interpretation can be conservative, but older code also
   resets PIN accounting after biometric success, defaults missing records to zero, verifies before persistence, and
   permits overflow. Android's downgrade check compares version and revision codes. An older binary with unchanged codes
   may avoid that check, subject to package and signing compatibility. That is a source-based inference from the Android
   package-manager implementation, not a tested installation result. The adb documentation describes `-d` as permitting
   version-code downgrade for debuggable packages. However, replacement installation can retain data, and `uninstall -k`
   expressly retains data. Use D1's precise procedure: an uninstall that removes application data, followed by a clean
   installation. Future supported rollback requires a reader compatible with the active format. Format markers cannot
   retrofit rejection behavior into existing pre-release binaries.
6. **Event meaning is separate from enforcement.** "LOCKOUT_TRIGGERED is emitted only for an accepted wrong-PIN result
   whose own reservation durably established a lockout deadline at or above the threshold. Reservation persistence
   alone, D1 replacement, and startup recovery emit no such event. Duplicate or rejected completions emit no additional
   event." A reservation can establish a lock before the result exists. The event therefore means a durable lock
   associated with an accepted wrong-PIN result; it does not announce every enforced lock. Result processing must
   preserve the reservation's deadline. An interrupted reservation can leave an enforced lock without an event, and
   restart must not invent the missing outcome. I5's "own count" should mean the operation's enforcement count under C2.
   It must not be presented as the number of confirmed wrong PINs. Reason: truthful event reporting does not require
   durable failure history in 1.0.0, but it does require precise event and field meanings.
7. **Legacy capture must be resolved before activation.** The new engine passes the count to its capture interface and
   uses the count when ordering lockout updates. The supplied capture adapter does nothing, while `LoggingAuditLog`
   records only the event name. A saturated count cannot establish which operation is newer. D2's operation identities
   and independent revisions must provide that ordering. Legacy capture also needs an explicit disposition. With
   unchanged capture logic, cap 11 and capture threshold 5 would prevent further qualifying captures after saturation. A
   cap divisible by the capture threshold would instead make every subsequent wrong result qualify. D1 replacement would
   also contaminate reported failure totals. The required condition is to disable legacy capture before C2 becomes
   active, or activate C2 after the legacy path becomes unreachable. Future deletion alone does not resolve the active
   incompatibility. Reason: C2 counts admitted PIN checks, including interrupted checks. Legacy capture treats the count
   as wrong PINs, and saturation changes capture behavior. Disabling legacy capture or making the path unreachable is
   therefore an activation prerequisite. Capture in 2.0.0 requires separate accounting of confirmed wrong PINs.
8. **The documented candidates are compared against the practical transition from 1.0.0 to 2.0.0.**
   - Candidate status: The 1.0.0 capture and reporting requirements do not, by themselves, require Option A. That
     conclusion neither rules out A nor establishes that deferring A produces the simpler architecture. The proposal
     identifies C2 as the preferred design to prove and retains A as a comparator and alternative for demonstrated C2
     limits. The options document expressly proposes A's pending-attempt record, followed by verification and outcome
     recording. Neither document establishes an adopted 2.0.0 design. Keeping C2 and adding separate confirmed-failure
     accounting is an additional architectural possibility. The checked documents do not specify or approve that
     combined design. Its storage contract, coordination rules, and crash behavior remain undesigned. Neither approach
     has been selected or demonstrated to satisfy the complete 2.0.0 requirements.
   - Unresolved 2.0.0 requirements: Separate reporting would not, by itself, establish compatibility with 2.0.0. FR-081
     and FR-082 require capture and reporting based on actual authentication failures. FR-009 and FR-010 also define
     retry limits and progressive delays in terms of failures. Penalties arising from interrupted reservations therefore
     require explicit reconciliation with the 2.0.0 requirements. The same issue applies to any Option A recovery policy
     that penalizes an unfinished reservation. Both candidates must define outcome durability and the treatment of
     unknown outcomes. An explicit reservation cannot recover a result lost after verification but before persistence.
     C2 reservation counts and D1 replacement counts cannot become an exact history of wrong PINs.
   - Comparison criteria: Compare implementation complexity in 1.0.0, later storage and migration changes, verification
     effort, and components requiring replacement for 2.0.0. Consider whether earlier adoption of A would avoid
     substantial redesign, rather than assuming that an absent 1.0.0 feature justifies deferral. Equally, an inferred
     future benefit does not establish that A has lower total complexity. Keep implementation within approved 1.0.0
     scope. The transition comparison informs candidate selection but does not broaden D6, authorize additional
     persistence, or select either design.

   Reason: a practical transition requires comparing complete architectural consequences while distinguishing documented
   proposals, inferred possibilities, and unresolved decisions.

## 7. D5: clock policy (2026-10-29)

**Terms.**
- **Boot:** one run of the kernel, from kernel start to the next kernel restart. A userspace reboot does not start a new
  boot.
- **d:** the lock's own duration, from the fixed 1.0.0 policy for the stored count (30 minutes at the cap).
- **u:** the current elapsed realtime.
- **E:** the stored elapsed-realtime deadline. **S = E − d:** the elapsed realtime when the lock started.
- **B:** the stored boot ID. **b:** the current boot ID.
- **NONE:** the no-deadline sentinel. **UNKNOWN:** the boot-ID value for a deadline written while the boot ID could not
  be read.
- **Time anchor:** a deadline's creation time (point 3).
- **Earlier-boot proof:** a process's retained finding that a record comes from an earlier boot (point 4).
- **Generation and revision:** the security-state generation and revision that point 5 uses for expiry ownership.

**1. Threat scope.**
- The Threat Model already covers a person with access to Settings and a non-root developer-tools attacker within the
  normal non-root Android boundary.
- THR-AUTH-004 names reboot and time manipulation as ways to bypass the lockout.
- D5 does not change that scope. It decides how the lockout meets THR-AUTH-004 and which availability limits are
  accepted. The proposal's D5 question is restated to match.
- Security claims concern the non-debuggable production package.
- Data clearing, uninstall, and removal of Android capabilities keep their existing boundaries. Root access and modified
  system software stay outside D5.
- The X04 reproduction is JVM evidence, not device confirmation.

**2. Clock.**
- Lock enforcement uses elapsed realtime: `SystemClock.elapsedRealtime()`, milliseconds since kernel start, including
  deep sleep. It is not `uptimeMillis()`.
- The wall clock, network time, and GNSS time never decide or shorten a lock.
- `lockout_until` holds the nominal wall deadline that D4 keeps:
  - enforcement and the displayed remaining time never read it;
  - the stored wall time is never compared with the current wall time;
  - no compatibility with older readers is implied.
- The displayed remaining time is the D5 result.

**3. Clock record and writes.**

Every lockout-record mutation writes a complete, internally consistent record atomically. A retry preserves its
operation's original payload, including count, deadlines, and boot identity, and remains subject to freshness checks.

The mutations are:
- D1 replacement;
- initial setup;
- the reset after an accepted PIN success;
- the expiry write;
- all count-changing operations of the selected admission design. Under C2, this includes every reservation, both below
  and at or above the threshold.

The record holds:
- `failure_count`;
- `lockout_until`: the wall time at deadline creation + d;
- `lockout_elapsed_until` (E): the elapsed realtime at deadline creation + d;
- `lockout_boot_id` (B): the current boot ID, or UNKNOWN when it cannot be read.

The deadline's creation time is its time anchor:
- For a C2 reservation, the anchor is the admission time.
- For D1, it is the time the replacement is created.
- `lockout_until` and E describe one logical creation: the wall and elapsed readings taken at that anchor.
- Retries keep the original anchor.
- Overflow of either deadline is checked when the deadline is constructed.

A below-threshold reservation writes the new count with NONE in all three clock keys.

Rules:
- **Keys:** within a successfully read, recognized initialized D5 record, every required key must be present.
- **No deadline:** logical absence of a deadline uses the defined sentinels, NONE in all three clock keys. For B, NONE
  is distinct from UNKNOWN.
- **Other states:** other storage and initialization states retain their D1 classifications.
- **Boundary:** the platform boundary reads and parses the boot ID. Lockout policy receives only a validated identity
  that is either known or unknown.
- **Boot ID of a new deadline:** "Every deadline creation, D1 replacement included, uses the latest completed, cached
  boot-ID result. No deadline is created while the first boot-ID read is pending. A later retry in progress does not
  prevent creation using an already completed UNKNOWN result. The selected identity becomes part of the operation's
  immutable payload."
- **Admission design:** no admission design may bypass D5. Option A must define the duration and time anchor of any lock
  tied to an unfinished reservation.

**4. Reading.** The rules apply in this order:

a. After validation, a record that holds the no-deadline sentinels imposes no lockout. The rules below apply only to
   records with a deadline.

b. If B and b are known and equal: remaining = max(0, E − u).

c. If B and b are known and different: remaining = max(0, d − u).

d. If B or b is unknown:
   - if the process holds earlier-boot proof for the record, or u < S: remaining = max(0, d − u);
   - otherwise: remaining = max(0, E − u).

Earlier-boot proof:
- When rule d applies and a read finds u < S, the process records earlier-boot proof for that record.
- The proof binds to the record's generation and revision. It lasts while the record is current and ends with the
  process.
- A new reservation, a replacement, or a reset does not inherit it. A stale callback or read never reinstates it.
- A later known boot ID that matches B contradicts the proof (point 8).

The current boot ID:
- The platform boundary reads b during the asynchronous startup, together with the record.
- Startup and boot-ID retries perform I/O outside the UI thread and the coordination lock. State queries use cached
  results.
- Retries are rate-limited, with at most one read in flight.
- Once b is known, it stays fixed for the rest of the process, and no further boot-ID read occurs.
- A completed read that fails, or that returns a malformed value, gives UNKNOWN. It never makes the stored record
  invalid.
- A process restart discards the cached ID, the earlier-boot proof, and any observed expiry.
- A successful read that replaces an unknown boot ID may remove excess conservative delay. This applies to a retry
  within the process and to the first read of a new process.

Trust: "The lockout state becomes trusted when the record read and the first boot-ID read have completed, and the
current record passes D1 and D5 validation using that boot-ID result. A completed boot-ID read that fails or returns a
malformed value supplies UNKNOWN to rule d; it does not by itself make the lockout state unavailable. While the first
boot-ID read is pending, neither new PIN verification nor fingerprint authentication is admitted."
- Trusted does not mean unlocked: rules a to d can still give a positive remaining time.
- Later boot-ID retries run while the state stays trusted under UNKNOWN. Each result is validated when it arrives (point
  8).

States: "While the first boot-ID read is pending, the state is Loading. If the record is invalid, the state moves to
Replacing once that read has completed and D1's replacement conditions are established. A later boot-ID result is
validated against the current record before any state transition. A validation failure moves the state to Replacing.
D2's rules for invalidating a pending fingerprint operation and for completing a reserved PIN operation stay unchanged."

Writes from reads: "Reading does not directly rewrite the record. When a read or scheduled check observes expiry, the
manager clears that deadline in memory and submits a separate expiry operation through the ordered writer."

Changing the wall clock, the time zone, or automatic time has no effect.

**5. Observing the end, and the expiry write.**

Observing the end:
- The manager schedules a check at the computed end.
- When the check runs, it confirms that the record's generation and revision are still current, and it recomputes the
  remaining time before it clears anything.
- Reads keep enforcing the current elapsed-time result when the check is late.
- The observed expiry binds to the record's generation and revision, like the earlier-boot proof, and ends with the
  process.
- Scheduling reduces how often an expiry is not durably cleared. The retained in-memory state, not the schedule,
  supplies the within-process guarantee in point 6.
- "When an accepted update changes the computed end, the manager recomputes remaining time. Zero remaining time triggers
  immediate observation of expiry and submission of the ordered expiry write. Positive remaining time causes the manager
  to reschedule the check for the new elapsed-realtime end. All callbacks retain the existing freshness and expiry
  checks."

The expiry write:
- **Ownership:** the expiry operation belongs to the generation and revision from which the expiry was established.
- **Checks:** before the write starts, and before each retry, the ordered writer checks that the operation still
  applies. Publishing its result passes the matching freshness check.
- **Effect:** it writes NONE into all three clock keys, and keeps the current count and the format metadata.
- **No side effects:** it emits no security event, creates no audit entry or capture, and does not change PIN
  accounting.
- **Ordering:** authentication does not wait for this cleanup. Under C2, PIN verification still waits for its own
  confirmed reservation write through the same ordered writer.

**6. Bounds, availability, and restarts.**

Bounds:
- **Earlier boot:** "For an unchanged deadline record from an earlier boot, while both boot IDs remain known and
  different, remaining time is max(0, d − u). Without another device reboot, a new deadline write, or a transition to
  unknown boot identity, enforcement ends by elapsed realtime d."
- **Any boot:** an unchanged valid record gives zero remaining time once u reaches E, whatever the boot-ID knowledge and
  the retained per-process facts.
- **One process:** for an unchanged validated record, remaining time never rises, and an ended lock never returns,
  without a new deadline write.
- **No shortening:** under the prerequisites in points 7 and 9, no rule ends a lock before its original duration has
  elapsed.

Availability limits, recorded under D3. Each one blocks both PIN and fingerprint:
- **Repeated device restarts:** each device restart during a lock postpones its end. There is no finite bound on the
  total wait.
- **End not stored:** if no expiry write stored a lock's end, a device restart brings the lock back, for up to d.
- **Unknown boot ID:** "Under rule d, retained earlier-boot proof or u < S selects max(0, d − u). Otherwise, remaining
  time is max(0, E − u)." If the first read occurs at u ≥ d, a positive result is possible only when S ≤ u < S + d. The
  lock can then outlast its original duration.
- **Stale boot ID:** on a device that reuses an ID across kernel boots, a contradiction sends the record to D1's
  controlled recovery (point 8).

Restart departures, the D3 disposition:

"At matched elapsed times, a process restart preserves the remaining-time calculation when the validated record, the
boot-ID knowledge, and the per-process facts that D5 retains are unchanged. D5 records two kinds of departure from D3.

Later or revived enforcement: a restarted process can lose a cached boot ID, or lose the earlier-boot proof retained for
the record, before or after the lock's observed end while the expiry is not durably cleared. Its remaining time can then
rise, or an ended lock can return, blocking PIN and fingerprint. This departs from D3's first sentence and from 'A valid
expired deadline does not become a fresh lock merely because the process restarts'. For the unchanged record, the
restarted process never reports more than max(0, E − u).

Earlier admission: a restarted process that reads a known boot ID different from B, where the earlier process had
neither a known boot ID nor earlier-boot proof, removes excess conservative delay at once. It can admit a PIN check that
the earlier process would still refuse until its next boot-ID retry. This departs from D3's matched-times comparison of
PIN-verifier entries.

Both departures keep three guarantees: no restoration of consumed attempt capacity, no reset of the lockout ladder, and
no admission before the original duration has expired. The last guarantee holds for a record proven to predate the
current kernel boot on the same device. A known different boot ID gives that proof under D5's boot-ID assumption. D5's
adoption accepts these departures under D3. They do not decide R-007/4 (an accepted PIN success whose reset did not
persist), which stays undecided."

**7. Boot ID.**
- The boot ID is the kernel boot ID, `/proc/sys/kernel/random/boot_id`. `BOOT_COUNT`, network time, and GNSS time are
  not used.
- **The boot-ID assumption needed for no shortening:** the ID does not change within a kernel boot. The kernel
  documentation describes boot_id as generated on first retrieval and unchanged afterwards. That supports the
  assumption, but does not prove that every OEM build conforms.
- **Device gate:** "For every required test configuration, qualify either the known-ID path or the unknown-ID path.
  Record the configuration, observed mode, test results, and disposition of its availability limitations."
- **Required configurations:**
  - one emulator image for each API level from 26 through 36, and the Moto G;
  - each is identified by its system image or build fingerprint;
  - each runs the non-debuggable production package at targetSdk 36 in the normal app process.
- **Missing or failing configurations:** a configuration that could not be tested is missing evidence. A configuration
  where the read fails qualifies the unknown-ID path by passing the rule d tests.
- **Known-ID path checks:**
  - the app reads and parses the ID;
  - the ID stays the same across process restarts and force-stop;
  - the ID changes across a kernel reboot;
  - the ID stays the same across a userspace reboot, where the device supports one.
- **Observation:**
  - "Once the first boot-ID read has completed, the production build logs the observed mode (known or UNKNOWN). For a
    record with a deadline, it also logs the comparison with B (SAME, DIFFERENT, or UNKNOWN). It never logs either ID
    value. When a retry succeeds, it logs the UNKNOWN-to-known transition at that moment. Logging is best effort, so a
    missing message is missing evidence, not an observed read failure."
  - "The mode diagnostic follows completion of the first boot-ID read. Once the record read also succeeds, the
    comparison diagnostic uses B from that loaded record and the completed boot-ID result. The comparison is captured
    before any mutation replaces or clears that record. SAME and DIFFERENT expectations require both IDs to be known;
    otherwise the comparison is UNKNOWN."
  - "The restart and reboot checks create a lock before the restart and read the comparison afterwards: SAME across
    process restarts, force-stop, and userspace reboot; DIFFERENT across kernel reboot."
  - "Instrumentation signed with the release key is an alternative, and it runs inside the app's process. Starting it
    restarts that process, so the checks of cached knowledge must account for that restart."
  - "The diagnostic is an FR-220 startup and authentication-delay-state event. Each record contains a time, a fixed
    event type, a non-debug severity, an outcome, and only the applicable mode, comparison, or transition as context.
    Event names and meanings remain stable within compatible 1.x releases. Neither boot ID is recorded. The event uses
    the shared production logging policy, including fixed, automatically bounded retention."
  - "The diagnostic is a diagnostic record, not a D4 security event."
  - "D5 defines the event through the existing logging interface (`AppLogger`). Filtering and retention depend on the
    shared production logging policy, which ADR-008 records as not yet implemented. The production observation check
    confirms that the event remains available under the intended distributed-build configuration. A missing message is
    missing evidence."
  - Until the shared logging policy exists, a device run can confirm the event only under the current logcat binding,
    and the check has to be repeated once that policy is in place. Instrumentation signed with the release key does not
    depend on the logging policy, because it reads the mode inside the app's process.
- **Emulators:** "On emulators, the reboot checks use `adb reboot` or a cold boot, never a Quick Boot snapshot load, and
  the report records which reboot actually happened. Emulator cold-boot runs are assigned to the NucBox."
- **Userspace reboot:** it is deprecated in Android 15, unsupported devices reject the command, and Android 11 can fall
  back to a full reboot. The tests record which reboot happened, and unsupported coverage is recorded.
- **Repeats:** the checks are repeated after every targetSdk change and on each new Android version.
- **Untested devices:**
  - A failed read selects rule d.
  - If a readable ID stays the same across a kernel reboot, rule b over-enforces the unchanged record and ends it by E.
  - The other outcome is an invalid record (point 8). A D1 replacement then sets a new deadline, which can end after the
    old E.

**8. Validation.**
- Within a successfully read, recognized initialized D5 record, every required key is present and has its exact type.
- All three clock keys are NONE, or all three hold a deadline.
- `lockout_until` is checked for its type and this pairing.
- A count at or above the threshold with no deadline is valid. This is recorded as a D1 clarification.
- An active deadline has:
  - a count at or above the threshold;
  - E ≥ d;
  - B well formed, or UNKNOWN.
- Matching known IDs together with u < S make the record invalid. This applies to the first boot-ID result before trust,
  and to each later result.
- A known boot ID that matches B contradicts retained earlier-boot proof for that record. The contradiction makes the
  record invalid. This holds until the record is replaced or durably cleared, including after the observed end while the
  expiry write is pending.
- A failed or malformed read of b gives UNKNOWN and never makes the stored record invalid. A malformed stored B does.

What follows from an invalid record:
- It goes through D1's controlled recovery from invalid lockout history through a confirmed replacement record.
  Authentication is unavailable until the replacement is confirmed.
- Replacement applies only after D1 establishes the recognized active format, storage authority, applicable policy, and
  a successful record read.
- Unsupported formats and unresolved storage failures keep their D1 classifications.

**9. Duration, migration, and device boundary.**

"An active lock retains the duration and clock semantics under which its deadline was created. Policy changes and
migrations must not reinterpret the deadline using a different duration. Copying elapsed-time fields unchanged is
permitted only when the source meaning is preserved within the same device's clock domain."

- **1.0.0:** the versioned format identifies the fixed duration calculation.
- **2.0.0:** before it adds configurable delays, each active lock must keep its original duration.
- **Boot ID in the format:** the released format names the boot-ID source, its encoding, and the NONE and UNKNOWN
  values. Readers never compare IDs from different sources as one identity scheme. A migrated active lock keeps the
  identity scheme that gives its boot ID meaning.
- **Device boundary:** the clock rules apply only to records written on this device. Records copied from another device
  are never valid input. The crypto replacement proposal owns backup and transfer exclusion and the restore rejection
  tests.
- **Prerequisites:** same-device origin, preserved duration semantics, valid records, the elapsed-realtime clock
  contract, and the boot-ID assumption must carry through the crypto replacement and the 2.0.0 migration.
- **Code at adoption:** a copied record that cannot be decrypted loads as empty, which means Available, because D1 is
  not implemented yet.

**10. Baseline, plan, test plan, and persistence.** D5's adoption approves the changes below. Their baseline, test-plan,
and code updates are pending (section 1).
- **Threat Model:** no change (point 1).
- **UI/UX section 8 and SDS section 3:** they hold.
- **UI/UX authoritative delay, interpretation:** the authoritative delay is the original lock's duration from its
  creation. A conservative estimate above it is not part of that delay.
- **DDS 6.3:** the record also holds:
  - the boot identity at deadline creation, or UNKNOWN;
  - the elapsed-realtime deadline;
  - the wall deadline, as a nominal value that enforcement never reads.

  This is recorded next to D4's change to the count's meaning.
- **DDS 8.4:** it permits expiry cleanup. Its display cap holds, because d never exceeds the maximum wait.
- **DDS 9.3:** point 8's cross-field rules and D1's replacement for invalid records are recorded against DDS 9.3,
  together with D1's baseline changes.
- **M7_PLAN:** "Approval of D5 and its D3 dispositions records the specified exceptions against the frozen restart
  criterion. All other restart protections remain unchanged."
- **Test plan:**
  - I8 gets this text: "Recovery and retries preserve their operation's original payload and remain subject to freshness
    checks. They never re-anchor a deadline or overwrite newer state. Wherever a recovery path evaluates a record, D5
    determines its remaining time. Extension or revival is permitted only in the explicitly accepted cases in point 6."
  - Each limit and departure in point 6 gets a residual ID with a disposition.
  - X04's expected results are updated with this text: "D5 supplies the clock-policy decision required by X04."
- **Code:** the 30-minute cap in `remainingOf` becomes d.
- **Persistence:** the two new keys and the expiry write form D5's separately described exception to invariant 6. The
  DDS entries do not replace it. The released-format contract documents them.

**11. Verification, done together with X04.**

Cases:
- **Start position:** S = 0, S < d, S = d, and S > d, with first reads and restarts before S, at S, and after S.
- **Late start:** the first app start late in a new boot.
- **Trust:** the first boot-ID read pending (no PIN or fingerprint admitted), failed (trusted under UNKNOWN), and
  matching B with u < S (D1, never trusted).
- **Startup order:** an invalid record found while the first boot-ID read is pending: the state stays Loading until the
  read completes, then moves to Replacing.
- **Earlier-boot proof:**
  - it is set at u < S and kept after u passes S;
  - a new reservation, replacement, or reset does not inherit it;
  - a restart loses it (d = 30, S = 10, u = 20: 10 becomes 20).
- **Observed expiry:** it is kept in memory; a restart while the cleanup is pending can bring the lock back (d = 30, S =
  100, u = 110: 0 becomes 20).
- **Earlier admission:** a restarted process reads a different ID where the earlier process had neither a known ID nor
  proof (d = 30, S = 10, u = 35: 5 becomes 0), never before the original duration.
- **Stale ID:** a matching ID after the proof, before and after the observed end, leads to D1.
- **Contradiction during an operation:** a later contradiction that arrives during a PIN or fingerprint operation.
- **Moving end:** a computed end that moves nearer but stays in the future (rescheduled), and one that moves into the
  past (immediate expiry handling).
- **Scheduling:** a late scheduled check after deep sleep, and a stale callback.
- **Boot-ID reads:**
  - a failed or malformed b;
  - startup reads off the UI thread, at most one read in flight, and no read once b is known;
  - an unknown b becoming known within a process and at a new process's first read, both matching and different;
  - a stored UNKNOWN B with a known b.
- **Observation:**
  - the mode is logged only after the first read completes;
  - the comparison is logged for a lock created before a restart;
  - the UNKNOWN-to-known transition is logged when it happens.
- **Expiry cleanup:**
  - a failed expiry cleanup, and one the app never saw;
  - expiry cleanup racing with a reservation, a reset, or a D1 replacement.
- **Restarts:** force-stop, repeated restarts, a kernel reboot, and a userspace reboot where supported.
- **Writes and migration:**
  - below-threshold reservations, each writing the complete record with NONE clock keys;
  - a retry that writes the creation-time values after a delay;
  - a policy change or migration while a lock is active.
- **Limits:** deep sleep, and arithmetic limits.
- **Records:**
  - cleared records across a reboot, and a cleared record that still holds an old B;
  - matching IDs with u < S, and mixed sentinel values.
- **Wall clock:** "The wall clock set forward and back, a time-zone change, and automatic time switched off and on, each
  during a lock with healthy storage: with no restart, after a process restart, and after a kernel reboot. The changed
  setting stays in place for the restart and reboot reads. X04 keeps the failing-retry variant." The device operator
  performs and records the Settings changes.

Acceptance criteria:
- No PIN verification or fingerprint authentication before the lockout state is trusted.
- No shortening: no rule ends a lock before its original duration has elapsed.
- A valid no-deadline record never imposes a lockout. Mixed sentinel values are invalid and follow D1.
- Within one process, for an unchanged validated record: no rise in remaining time and no revival without a new deadline
  write.
- Restart departures occur only as recorded in point 6, within their bounds.
- Changes in boot-ID knowledge keep both expiry bounds.
- "For the same validated record, elapsed time, boot-ID knowledge, and retained per-process facts, wall-clock,
  time-zone, and automatic-time changes produce the same remaining time. Normal elapsed-time progress continues to
  reduce the countdown. Restart effects are checked separately against D5's specified rules."

Devices: the configuration set in point 7.

D5 keeps rule c and adopts the earlier-boot proof and the scheduled end check. The rebase write, which rewrites the
elapsed deadline for the new boot once a device restart is proven, is not part of D5. It stays a separate candidate.

## 8. D6: when Option A may advance (2026-10-29)

**Terms.** To *advance* means a device prototype of Option A or a selection of A. The P0 decisions permit either step
only after an exception that the lead records first. The *approved C2 record contract* is the lockout record that D1,
D4, and D5 permit for C2: D1's format metadata, the lockout-record exception of D4, and D5's clock fields and expiry
write. The *shared cases* are the crash points, the uncertainty policy, and the required cases that the models of C2 and
A both use.

**1. Scope.** D6 decides when Option A may advance beyond a design and a JVM model with fake storage. Until the lead
records a finding under point 3, A stays a design and a JVM model. It is the comparator for count-first C2.

**2. Required properties.** A required property is a pass-or-fail limit from this list:
- the hard limits of the P0 ranking: the test plan constraints I1 to I9, the invariants of `M7_PLAN.md` section 2, and
  the threat and storage policies;
- the pass-or-fail thresholds of the P0 decisions;
- the P3 criteria, with the changes that the lead approves under D3 and D5;
- the rules of D1 to D5.

The invariants apply with D2's interpretation and the approved exceptions recorded under D1, D4, and D5. C2's six
requirements still to prove (section 3.2 of the proposal) are C2's obligations under these limits. A need of the 2.0.0
transition is not a required property under D6. It informs the selection (D4 point 8).

**3. Advancement.** A advances only after the lead records a finding that meets every condition of (a) or of (b), with
its evidence. A finding concerning one comparative benefit does not waive another required property: A's design and JVM
model meet every required property that they can check. Device-only properties remain unverified before device testing.

(a) A demonstrated protocol limit of C2. A required property fails under C2, and A's design and JVM model meet the
property in the same case. The finding includes a reproducible counterexample and an explanation of why correcting the
failure requires changing the approved C2 record contract. Viable corrections within the approved D1, D4, and D5
boundaries are evaluated before the failure is classified as a protocol limit.

A limit that A shares does not advance A. The costs that the proposal names as shared are such limits: the write before
every check, the asynchronous callers, and the closed PIN path during write faults. When a revision of C2 needs
persistence beyond the approved C2 record contract, the lead compares that revision with A on the measures of (b) before
either advances.

(b) A materially simpler and safer recovery:
- In the shared cases, A has lower measured security exposure than C2. Before the comparison, the lead names the
  exposure measure and the shared schedule. The security-exposure measures of the test plan are the starting point.
  Equal measured exposure, including zero for both candidates, does not satisfy this condition. Model-derived
  measurements remain labelled as model evidence.
- A is simpler on the measures of D4 point 8: 1.0.0 implementation complexity, later storage and migration changes,
  verification effort, and components replaced for 2.0.0. Fewer rules or race obligations support this complexity
  comparison. They do not by themselves establish lower measured exposure.

The lead judges whether the difference is material and whether it justifies A's persistence changes, and records the
reasons. A simplicity gain alone does not advance A, even when the transition comparison shows it.

**4. Gates.** After a recorded finding:
- The lead records A's persistence exception before either a device prototype or selection of A. The exception describes
  A's additional record separately from D1's format metadata, D5's clock fields, and the shared part of the
  lockout-record exception. If Option A is selected under D6, the approved A contract removes or replaces the
  C2-specific part of that exception, as D4 provides.
- A device prototype follows the device fault-injection rules of the P0 decisions.

Selection remains a P5 decision under the P0 ranking and the required evidence gates.

**5. A's design and JVM model in P3.** A's design is reviewed before A's JVM model runs. The design gives A's transition
table, persistence format, authoritative mutation owner, and recovery rule. It settles the decisions that the options
document requires before Option A is coded. D1 to D5 apply to A. Where a rule is written for C2, A's design states its
own rule for the same requirement: D2's reservation-owner rule, D4's count meaning, events, and capture rule, and D5's
anchor for a reservation. A's JVM model uses the shared cases at the level of detail of C2's model. The design and the
model cover at least:
- wrong, correct, and unevaluated PINs at every protocol cut, for each caller;
- cancellation before and after the verifier starts, including X08;
- a long stall before the verifier starts, and an expiry during verification;
- repeated restarts while one unfinished reservation is recovered, including an interruption before the recovery
  disposition becomes durable;
- the duration and time anchor of any lock tied to an unfinished reservation. A recovery anchor must satisfy the same
  approved timing rule as the corresponding normal execution. Persisting an anchor prevents later reconstruction from
  choosing another anchor after commitment, but does not justify an extension introduced by the first recovery. The
  extension requires a separate D3 disposition;
- A's recovery states, mapped to the state table of D2 (a new state needs the lead's approval);
- whether A keeps a degraded fallback (if it does, D3's fallback preservation applies);
- outcome durability and the treatment of unknown outcomes. An unfinished A reservation cannot establish a confirmed
  wrong PIN, so it creates no wrong-PIN event;
- the meaning of A's stored count, and how LOCKOUT_TRIGGERED, audit, and capture use it;
- a fingerprint success while a reservation is pending;
- A's additional record as part of the lockout record: the same file, the same atomic commit, validation under D1 and
  D5, and no Room schema change. A later crypto migration must preserve the atomic relationship between the reservation,
  the count, and the deadline;
- the failure-based limits and capture of 2.0.0 (FR-009, FR-010, FR-081, and FR-082 of the 2.0.0 SRS), recorded as
  transition data: the outcome information available and lost, the migration work, and the implications for
  failure-based enforcement and capture.

**6. Limits.** A required property that C2 and A both fail is a candidate failure for both. Only the lead can change
that requirement, and the lead records the tradeoff before a new test run, as the test plan requires. D6 changes neither
the P3 criteria, nor the P0 ranking, nor the approved exceptions recorded under D1, D4, and D5. The transition
comparison informs the selection and authorizes no persistence.

**7. Record text.** It replaces the proposal's sentence "Option A is used only where count-first admission has a
demonstrated protocol limit.":

> Option A stays a design and a JVM model with fake storage, as the comparator for count-first C2. Advancement requires
> a recorded finding satisfying every condition in D6 point 3(a) or 3(b). The lead records A's persistence exception
> before either a device prototype or selection of A. Selection remains a P5 decision under the P0 ranking and the
> required evidence gates.
