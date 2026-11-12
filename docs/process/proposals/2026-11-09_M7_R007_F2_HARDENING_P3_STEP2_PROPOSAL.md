# M7 / F2 Hardening: P3 Step 2 Designs (R-007)

**Date:** 2026-11-09

**Status:** Proposal for the lead, not approved.

**Scope:** Step 2 of the order of work in section 5 of `2026-10-10_R007_F2_HARDENING_P3_DECISION_PROPOSAL.md` (the P3
decision proposal). Section 1 specifies C1 with B1. Section 2 gives the shared D2 protocol, which the B2 work of step 3
also cites. Sections 3 and 4 give the transition table and JVM model requirements of count-first C2 and of Option A. The
lead reviews section 1 first, section 2 before sections 3 and 4 depend on it, and each design before its model runs.

**Labels:** D1 to D6, their points, the transition additions and refinements, and rules A and B are those of
`2026-11-06_M7_R007_F2_HARDENING_P3_DECISIONS.md` (the decision record). The constraints I1 to I9, the case IDs, the
residuals R-007/n, the evidence labels, and the candidate labels come from `2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`
(the test plan). The transitions T1 to T19 are those of `2026-10-07_R007_F2_HARDENING_P3_B1_SPEC.md` (the B1 spec), and
the scheduler spec is `2026-10-07_R007_F2_HARDENING_P3_SCHEDULER_SPEC.md`. The cases Q07 and Q19, the boundary K16, and
crypto decision D10 belong to `2026-09-24_SECURITY_CRYPTO_REPLACEMENT_PLAN.md` (the crypto replacement proposal). The
invariants are those of section 2 of `M7_PLAN.md`. Statements about the code describe commit `261dd01`.

**Tracking entries:** each finding has one entry, which holds its status and its evidence. X14 is in section 1.10, X08
in section 2.7, and X13 in section 2.8.

## 1. C1 with B1: trusted startup state

### 1.1 Purpose and limits

C1 admits no PIN check and accepts no fingerprint result until authentication readiness is Ready. B1 retries a startup
read that cannot complete, on the recovery scheduler. Section 3.1 of the P3 decision proposal gives the predicted
effects.

Limits:

- Section 1 keeps the baseline admission, which checks a PIN before it stores the result. R-007/2 and R-007/3 therefore
  remain under section 1 alone. Sections 3 and 4 add durable admission.
- Without C2, the baseline admission keeps its degraded fallback, so the fallback preservation of D3 applies.
- A read fault that does not end keeps authentication unavailable, also for the correct PIN.
- A restart after an accepted PIN success whose reset did not persist stays residual R-007/4.
- Integrated C1 needs a production storage boundary (section 1.3).

### 1.2 Responsibilities

Transition addition 1 assigns the responsibilities. Section 1 uses them as follows:

| Component | Work in section 1 |
|---|---|
| Lockout storage boundary | Reads the lockout record and reports one outcome (section 1.3). Applies the record checks that need no clock or boot ID (section 1.5). Performs durable writes on the ordered writer. |
| Platform boundary | Reads and parses `/proc/sys/kernel/random/boot_id` (D5 point 7). Reports a known ID or UNKNOWN. |
| Credential and compatibility providers | Report the classifications of section 1.3. |
| Lockout logic | Holds the fixed 1.0.0 policy (section 1.5). Applies the checks that need the current boot ID or elapsed time. Computes the remaining time, observes expiry, and builds the replacement payload. |
| Authentication coordination | Combines the inputs into readiness (section 1.4). Decides admission under the common lock (section 2). |
| Presentation | Shows the readiness state (section 1.9) and forwards input. |

### 1.3 Inputs and the storage dependency

Authentication coordination consumes four typed inputs. It never infers trusted credentials from a successful read or
from an "is PIN set" Boolean.

| Input | Values | Provider |
|---|---|---|
| Storage compatibility | Pending, CannotComplete, Compatible (with the active format and the lockout policy), Blocked (UnsupportedFormat, MigrationIncomplete, or AmbiguousAuthority) | Migration coordinator |
| Credential classification | Pending, CannotComplete, Configured (with the credential generation), ConfirmedEmpty, Invalid | Credential repository and the setup checks |
| Lockout record | Pending, CannotComplete, Valid (with the record), Absent, Invalid, Unsupported | Lockout storage boundary |
| Boot ID | Pending, Known (with the ID), UNKNOWN | Platform boundary |

The values mean:

- **Configured:** D1 rule 2 and qualification 1 hold.
- **ConfirmedEmpty:** the confirmed empty state of D1 qualification 2 and findings 1 and 2. Every other missing or
  invalid credential is Invalid (D1 rule 3).
- **Valid lockout record:** the fields of D5 point 3, and the record identity that D5 point 5 uses for expiry ownership.
- **Absent:** the read completed and found no record. The credential classification decides what the absence means
  (section 1.4). Beside ConfirmedEmpty credentials, row 5 gives SetupRequired, because an unconfigured generation has no
  lockout record until PIN setup writes it (section 5.5 of the crypto replacement proposal). Beside Configured
  credentials, PIN setup wrote the record before the PIN (D1 rule 4), so the absence is lost history, and row 11
  applies.
- **CannotComplete:** the check or read did not complete. It is neither proof of absence nor proof of damage (D1
  qualification 1).

Credential parameter validation, the database-rebuild checks, migration, and setup sequencing stay with their assigned
work, including crypto decision D10. Each provider must satisfy D1 before integrated C1 readiness counts as complete. A
model with supplied classifications proves only how C1 handles those inputs.

**Storage dependency.** Integrated C1 requires a production storage boundary that distinguishes valid, absent, invalid,
incomplete-read, and unsupported records, and establishes the active format and policy before replacement.
`EncryptedPrefsLockoutStorage` does not meet this requirement. It returns 0 for a missing key, so it loses the
information that trust needs. A damaged file also reads as `(0,0)` (case X16 of the P2 device reports). The lead
resolves the backing store before C1 integration or device qualification:

- An encrypted-preferences format needs the explicit persistence exception for D1's format metadata and D5's fields.
- The crypto replacement needs its own approved scope and qualification.

A format marker alone cannot establish authority after the whole lockout file is lost, because the marker is lost with
the file. The chosen design must explain how the boundary establishes authority in that case. The design and the model
of section 1 do not wait for this choice.

### 1.4 Readiness

Authentication coordination evaluates the rows in order, and the first row that matches gives the readiness state of
D2. It evaluates them again whenever an input changes. A valid lockout record therefore cannot override an earlier row.

| Row | Condition | Readiness |
|---|---|---|
| 1 | Compatibility is Blocked, or the lockout record is Unsupported | CompatibilityBlocked, with the reason that compatibility reports, or UnsupportedFormat when compatibility is not Blocked |
| 2 | Compatibility is Pending | Loading |
| 3 | Compatibility is CannotComplete | Unavailable |
| 4 | Credentials are Invalid | CredentialInvalid |
| 5 | Credentials are ConfirmedEmpty | SetupRequired |
| 6 | Credentials are Pending | Loading |
| 7 | Credentials are CannotComplete | Unavailable |
| 8 | The lockout record is Pending | Loading |
| 9 | The lockout record is CannotComplete | Unavailable |
| 10 | The first boot-ID read is pending | Loading |
| 11 | The lockout record is Absent or Invalid, or lockout logic finds it invalid (section 1.5) | Replacing, until the replacement write is confirmed (section 1.7) |
| 12 | The lockout record is trusted, and the remaining time is positive | LockedOut |
| 13 | The lockout record is trusted, and the remaining time is zero | Ready |

- Rows 1 to 9 establish the conditions of D1 for a replacement: the active format, storage authority, the policy,
  trusted credentials, and a completed record read. Row 10 adds the first boot-ID result that D5 point 4 requires.
- Rows 12 and 13 use the remaining time at the moment of the query (section 1.8).
- Retries do not depend on the row that applies. B1 retries run while the lockout record is CannotComplete, and
  boot-ID retries while the boot ID is UNKNOWN (section 1.6).
- A move from Ready to another state invalidates a pending fingerprint operation (D2 refinement 1). Section 2 gives the
  rule for a PIN operation that is in progress.

### 1.5 Lockout trust and the fixed policy

**Trust.** The lockout state becomes trusted under the trust rule of D5 point 4. A successful read alone never makes it
trusted (D1 rule 1), and trusted does not mean unlocked.

**Where each check runs.** The storage boundary applies the checks of D4 point 4 and D5 point 8 that depend only on the
record:

- the required keys and their exact types;
- NONE in all three clock keys, or a deadline in all three;
- the count range.

For a record with a deadline, the storage boundary also checks:

- a count at or above the threshold;
- E ≥ d;
- B well formed, or UNKNOWN for a deadline created while the boot ID could not be read. Such a record is valid.

A record with NONE in all three clock keys is valid at any count in the range, including a count at or above the
threshold after expiry (D5 point 8).

For a record with a deadline, lockout logic applies the checks of D5 point 8 that depend on the current boot ID or the
current elapsed time, under the coordination lock. These are matching known IDs together with u < S, and a known ID that
contradicts retained earlier-boot proof. Lockout logic applies them to the first boot-ID result before trust, and to
each later result (section 1.6).

**Count cap.** The lead set the count cap to 11 on 2026-11-09 (D4 point 3). Within the recognized active format, a
stored count outside 0 to 11 is invalid (D4 point 4). Every count change saturates at 11 before its arithmetic (D1
qualification 3), and the count keeps the cap after expiry.

**Fixed 1.0.0 policy.** One implementation in lockout logic holds the threshold (5), the cap (11), the ladder, and the
lock duration d of each count (D4 point 3). The ladder is that of `LockoutManager.lockoutDurationFor`: 30 s at count 5,
doubled for each further count, up to 30 min at count 11. Storage adapters and screens do not reproduce these values.

### 1.6 Startup, B1 retries, and boot-ID retries

**Startup.** Construction does no storage I/O, and the lockout-record and boot-ID inputs start Pending. Readiness
follows section 1.4 from the start. The startup record read runs on the `lockout-io` writer. The first boot-ID read runs
on the platform I/O worker at the same time. Neither runs on the UI thread or under the coordination lock. The
main-thread read time and the ANR risk of R1.4 therefore end (PREDICTED).

**B1 under C1.** The B1 spec applies, with these changes:

- The startup read replaces the construction read of T1 to T4. A read that completes, with any outcome other than
  CannotComplete, starts no chain (T1). A read that cannot complete sets the lockout input to CannotComplete and starts
  the READ chain (T2). A `CancellationException` from the adapter while the manager job is active counts as a read that
  cannot complete, as in T10, so T3 no longer makes the constructor throw. A read that does not return leaves the
  lockout input Pending, and the manager exists (T4).
- Applying a read result sets the lockout input of section 1.3, and readiness is evaluated again. This replaces the
  snapshot rule of section 2 of the B1 spec.
- While the READ chain runs, the lockout input is CannotComplete, so section 1.4 gives neither Ready nor LockedOut. No
  PIN check or fingerprint result is admitted, so T15 and the admission cases of T7 and T12 cannot occur. The
  replacement and the expiry write follow a completed read, so neither runs during the chain. The chain ends when a read
  completes (T8), when PIN setup confirms the initial lockout record (below), when the manager stops (T13, T16), or when
  the manager scope is cancelled (T11).
- A B1 retry is still wanted while its sequence number is current, the manager has not stopped, and the lockout input is
  CannotComplete. This replaces the four conditions of section 2 of the B1 spec.
- The open interval of section 1 of the B1 spec does not occur. Under a persistent read fault, each restart gives 0
  verified guesses instead of 5 (R1.2e, PREDICTED).
- No failure or reset write is admitted before a read completes, so none waits behind a held read (R1.4c). The initial
  lockout write of PIN setup can wait behind it.
- The delays and the read rate of section 4 of the B1 spec stay (X05).

**PIN setup.** In SetupRequired, PIN setup can run while the READ chain retries, or after a read found the record
Absent. When setup confirms its write of the initial lockout record (D1 rule 4), the lockout input becomes Valid with
that record. A READ chain then ends, because the input is no longer CannotComplete. Without this rule, an Absent result
from before setup would meet the Configured credentials of the finished setup, and row 11 would install a replacement
lock. The setup work owns the order of the setup writes.

**Boot-ID retries.** After a first boot-ID read that gives UNKNOWN, the lockout state can be trusted under rule d of D5
point 4. A later known ID can remove conservative delay. It can also expose a contradiction that makes the current
record invalid (D5 point 8). A blocked storage writer therefore must not stop the boot-ID check. The retries follow
these rules:

- They use their own retry slot and a bounded platform I/O worker, never the `lockout-io` writer. They reuse the backoff
  and the ownership approach of the scheduler spec, with their own dispatch and cancellation.
- The delays between failed attempts are 1, 2, 4, 8, 16, 32, and 60 s, then 60 s for each later attempt. Each delay
  starts when the previous attempt has completed. At most one read is queued or running.
- Storage retries, PIN operations, and state queries neither replace the chain nor restart its backoff.
- The read runs outside the coordination lock. On completion, the attempt takes that lock and checks that it still owns
  the chain and that the manager has not stopped. Only then does it validate the result against the current record
  (section 1.5).
- The chain stops once the ID is known or the manager stops. A result that arrives after shutdown is discarded.
- An expiry write or a replacement write that a result causes goes through the ordered writer.

A known ID stays fixed for the rest of the process (D5 point 4). Lockout logic emits the diagnostics of D5 point 7
through `AppLogger`.

### 1.7 Replacement confirmation

In Replacing, lockout logic builds the replacement payload: the count at the cap (11), the lock duration of the cap
(30 min), and the time anchor and boot ID of D5 point 3. The boot ID is the latest completed boot-ID result, so no
payload is built while the first boot-ID read is pending. The payload does not change, and every retry writes the same
payload (D5 point 3, constraint I8).

- The write goes through the ordered writer, to the destination that the storage boundary selects. In the crypto
  replacement proposal, the pending-replacement protocol of its section 5.5 selects it, and K16 tests it.
- A write that returns true confirms the replacement, and the lockout input becomes Valid with the replacement record.
  Unless an earlier row matches, rows 12 and 13 then decide between LockedOut and Ready.
- A write that returns false, throws, or does not return leaves the replacement unconfirmed, so row 11 still applies.
  Section 2 gives the retry rules.
- A process death before the write reached storage leaves the old record, and the next process builds a new payload with
  a new anchor. Authentication was unavailable throughout, so no attempt capacity was restored (D3).
- A process death after the write reached storage leaves the replacement, also when the acknowledgement was lost. The
  next process reads it as Valid and keeps its deadline (section 4.1 of the decision record).
- A replacement emits no `LOCKOUT_TRIGGERED`, no audit event, and no capture (D1 qualification 3, D4 point 6).

### 1.8 Deadline enforcement and expiry

- The remaining time follows rules a to d of D5 point 4, with the earlier-boot proof. The screen shows that remaining
  time (D5 point 2). The 30 min cap in `remainingOf` becomes d (D5 point 10).
- Enforcement never reads `lockout_until`, and the wall clock never decides or shortens a lock (D5 point 2).
- Lockout logic schedules a check at the computed end and handles it as D5 point 5 states, also when the end moves.
- An observed expiry clears the deadline in memory and submits the expiry write of D5 point 5 through the ordered
  writer. Authentication does not wait for it. Section 2 gives the retry rules of a failed expiry write.
- Each count-changing write carries the complete record of D5 point 3. Sections 3 and 4 define the time anchor of each
  admission design.

### 1.9 Callers and screens

**Callers.** Every caller asks the admission owner at the three moments of D2: before a PIN check, before it opens the
fingerprint prompt, and when a fingerprint success arrives. Every state other than Ready denies. The callers are those
that the P0 decisions in `M7_PLAN.md` list:

- the self-gate of `MainActivity`, through `AuthGateViewModel`;
- the legacy `LockScreenActivity`;
- `OverlayLockPresenter` and `BiometricHostActivity`, from slice F6;
- `LockEngineRuntime`, which reads the lockout state.

Their `is LockedOut` checks treat every other state as permission, and readiness checks replace them. Authentication
decisions stop using `isPinSet()`, which only tests whether the hash key exists. This includes the choice of the
self-gate between setup and the PIN pad, and the PIN status that `ProtectionWatchdogService` reports.

**Screens.** The screen of each state follows the state table of D2. The LockedOut screen shows the heading "Try again
later", the countdown, and the existing controls (ADR-022A). In the process that confirmed a replacement lock, it also
shows "The lock history could not be read." while that lock lasts. After a restart, the note is omitted.

### 1.10 X14 tracking entry

**Finding.** The baseline trusts stored values out of range. The JVM baseline report
(`2026-09-28_m7-wp2-f2h-p2-jvm-baseline_2012-i7.md`) gives these results:

- A stored count of -3 allowed 8 wrong PINs before the first lock.
- A stored count of `Int.MAX_VALUE` wrapped to a negative count at the next failure.
- A negative stored deadline read as no lock.
- A deadline beyond the 30 min cap showed the cap until less than 30 min remained, and `Long.MAX_VALUE` gave no end.

**Required treatment.** The trust rule, count validation from 0 to 11, and saturation at 11 (section 1.5). Deadline
validation under D5 point 8, and enforcement and display under D5 point 4 (section 1.8). An invalid record leads to the
replacement of section 1.7.

**Status.** Specified in this proposal. Not implemented.

**Verification.** No evidence yet. The cases are X14 and X17 of the test plan, and the D5 point 11 cases on records and
trust.

**Other owners.** The storage boundary that the lead selects (section 1.3) implements the record checks of section 1.5.

### 1.11 Acceptance cases

The test plan owns the setup, the schedule, the expected results, and the pass and fail criteria of each case. Section 1
is accepted on these cases:

| Contract | Cases |
|---|---|
| Trust, record checks, and count cap | X14, and the D5 point 11 cases on trust, startup order, and records |
| Startup and B1 retries | R1.1 to R1.4, X05, X06 |
| Readiness from supplied inputs | X18 (credentials and setup), X20 (compatibility) |
| Replacement | X17 |
| Screens | X19 |
| Boot-ID retries | X21, and the D5 point 11 cases on boot-ID reads and on a contradiction during an operation |
| Deadline enforcement and expiry | H05, X04, and the D5 point 11 cases on start position, expiry cleanup, and the wall clock |
| Callers and fingerprint results | X10, X11 |

X18 and X20 test how C1 responds to supplied inputs. Q19 of the crypto replacement proposal tests that real storage
conditions give CompatibilityBlocked and block authentication. Q07 tests that setup confirms the initial lockout record
before the PIN commit.

## 2. Shared D2 protocol

### 2.1 Scope

Section 2 gives the rules of D2 that every admission design shares: operation identity, admission, result acceptance,
write ownership, cancellation, and result delivery. Sections 3 and 4 add the reservation rules of each design. The B2
work of step 3 applies section 2.5 to the reset retries.

Authentication coordination is the admission owner of D2. It owns the operation IDs, the common lock, and every
admitted record write (transition refinement 1). Each step under the common lock is short and does no I/O. Storage
access, the PIN check, and caller code run outside the lock (constraint I3).

### 2.2 Operations

- An operation is one PIN check or one fingerprint prompt. Authentication coordination gives each operation an ID at
  its admission (the operation identity of D2).
- The ID is separate from the request ID of the screen and from the authentication purpose (transition addition 6). An
  operation keeps the purpose and the credential generation of its admission, and its result authorizes only that
  purpose.
- An ID is valid only in the process that issued it.
- Each operation has one result, and the result is consumed once. A duplicate or late result for an operation that has
  ended changes nothing.

### 2.3 Admission

Authentication coordination decides each admission in one step under the common lock:

- A PIN operation is admitted when readiness is Ready and no PIN operation is unsettled. This is the recommended
  initial model of D2 refinement 3.
- A fingerprint prompt is admitted under the same two conditions.
- The admission of a PIN operation invalidates a pending fingerprint operation.
- Every other request is denied.

**Settlement.** A PIN operation is unsettled from its admission until both conditions hold:

1. A check that the operation started has returned.
2. Storage has confirmed its accounting. Section 3.3 gives the confirmation for C2, and section 4.4 gives it for
   Option A.

Neither condition replaces the other. An acknowledgement, also that of a replacement under section 1.7, does not
settle an operation whose check is still running. Admission then stays closed until the check has returned, also
after the lock of the replacement has ended.

Cancellation and invalidation do not settle an operation either. This refines the cancellation rule of D2 refinement 3
(lead decision of 2026-11-11): an operation blocks competing PIN and fingerprint operations until it is settled, not
only until it is cancelled. At settlement, readiness is evaluated again. A storage fault that persists can therefore
block the fingerprint path for as long as it lasts.

A denial for an unsettled operation is not a readiness state. Readiness stays as section 1.4 gives it, and Loading
does not cover a PIN operation in progress.

**Restart.** A restart ends what blocks admission inside one process. The restarted process reads the stored record,
and it can admit a PIN check or a fingerprint operation that the earlier process would still refuse. The stored count,
the ladder, and the applicable D5 deadlines stay preserved. Under C1 with C2 or with Option A, each new verifier entry
has its own confirmed reservation. This guarantee does not apply to C1 alone. The test plan records two proposed
residuals for the departures from D3:

- **R-007/11, storage blockage.** A storage call has not returned, or confirmation or release retries have not
  succeeded. The restart departs from the matched-times comparison of D3. Where only memory held a C2 debit (section
  3.3) and storage does not hold its reservation, the restart also returns that attempt capacity and removes that
  lock. D3 prohibits both. No PIN check ever started for such a debit. Case DA13 holds the schedules.
- **R-007/12, unfinished check.** A PIN check has not returned, and no storage call is blocked. The restarted process
  can reserve and start another check, so its run has one more verifier entry at matched times. Both checks have their
  own confirmed reservations, and storage keeps both attempts consumed. Case DA14 holds the schedules.

Until the lead adopts a departure, each of its schedules is a failed D3 property.

### 2.4 Result acceptance and invalidation

Acceptance is a second step under the common lock. It checks the conditions and consumes the result in that one step.

- A fingerprint success is accepted when readiness is Ready and its operation is current, valid, and unconsumed (D2
  refinement 1). It grants the session and leaves PIN accounting unchanged.
- A PIN result is accepted when its operation is active and unconsumed. An operation is active while it is neither
  cancelled nor invalidated.
- For an accepted correct PIN, lockout logic clears PIN accounting in memory in the same step, and session management
  grants the session at once (constraint I6). The reset write follows through the ordered writer.
- For an accepted wrong PIN, the admission design changes PIN accounting (sections 3 and 4).

Each of these events invalidates a pending operation: its cancellation by the caller, a credential replacement, a stop
of the manager, and a move of readiness to a state other than Ready. Screen-off also invalidates a pending fingerprint
operation (D2 refinement 1). Under C2, the LockedOut state that the operation's own reservation caused is the one
exception (D2 refinement 2). A later move from that state to a state other than Ready invalidates the operation.

An invalidated operation stays invalid when readiness returns to Ready. Its result is not accepted: it gives no
session, no reset, and no authentication event. Its reservation follows the rules of its admission design.

### 2.5 Record writes

- **Ownership.** Every record mutation is one write of the complete record on the ordered writer (D5 point 3,
  constraint I2). Authentication coordination owns the write from the admission of its mutation until the write
  returns. The cancellation or invalidation of an operation never cancels its write, and a caller holds no handle that
  can cancel it.
- **Completion.** A write returns true, returns false, throws, or does not return. A `CancellationException` from the
  storage adapter while the manager job is active counts as a write that throws, under the cancellation rule of the
  scheduler spec. A write that does not return gets no timeout and no second writer (constraint I7), and later writes
  wait behind it. A write that has returned or thrown does no further storage I/O. The storage boundary must
  guarantee this, and a write without that guarantee counts as a write that has not returned.
- **Publication.** A completion returns through authentication coordination. Under the common lock, it checks the
  operation, the record generation and revision, and the cancellation state before it publishes (transition
  refinement 1). A completion that is no longer current publishes nothing but its acknowledgement (constraint I4).
- **Acknowledgement.** A write that returns true shows that storage held its record. Authentication coordination
  records this acknowledgement under the common lock, also for a write that is no longer current. Such a write changes
  neither the record in memory nor readiness. Its acknowledgement can only confirm the accounting of an operation
  (section 2.3). The scheduler spec ends a retry that lost its ownership without a completion step, so the
  acknowledgement must not depend on that step. The B2 work of step 3 changes the scheduler for this rule.
- **Replaced record.** When section 1.7 replaces the record during an operation, no later step of that operation
  changes the record in memory or submits a write. This holds for the return of its write and for the return of its
  check, which count only for the settlement of section 2.3. The replacement holds the cap, so it covers the attempt
  of that operation, and its deadline keeps its own anchor.
- **Retries.** The recovery scheduler holds one WRITE chain. Its target is the latest record mutation, once the write
  of that mutation returned false or threw. Each retry writes the original payload of the target (constraint I8).
  Before its I/O and before its publication, a retry checks that the manager has not stopped, that its sequence number
  is current, and that the target is still the latest mutation. A newer mutation cancels the chain, and the write of
  the newer mutation carries the complete newer record. The mutations therefore need no priority rule.
- **Writes that retry.** The replacement write of section 1.7 and the expiry write of section 1.8 retry under this
  rule. So do the unconfirmed reservation write of C2 (section 3.3) and the release write of Option A (section 4.5).
  Step 3 specifies the reset retries of B2.
- **READ and WRITE chains.** The READ chain of B1 runs only while the lockout input is CannotComplete. The record
  writes of sections 1.7, 1.8, 3, and 4 need a completed read, so the scheduler slot never holds both kinds.

### 2.6 Result delivery

- Authentication coordination publishes a result under the common lock and delivers it after it has released the
  lock. It calls no caller code under the lock or on the `lockout-io` writer (D2 refinement 3).
- A caller awaits the result in its own coroutine. A synchronous callback that a caller still passes runs on a
  separate serial dispatcher.
- A callback that blocks delays no admission, no write, and no state read. A callback that throws changes no result,
  and `AppLogger` records the failure.
- Audit events, capture, session actions, and screen changes follow from an accepted result. Each runs once for its
  operation ID.

### 2.7 X08 tracking entry

**Finding.** The JVM baseline report gives these results for cancellation:

- A `Pending.resolved` that was cancelled before its write started dropped the admitted write, and a restart lost that
  failure.
- A `CancellationException` from the storage adapter armed no fallback and skipped the callback, for a failure and for
  a clear.

**Required treatment.** The ownership and completion rules of section 2.5.

**Status.** Specified in this proposal. Not implemented.

**Verification.** No evidence yet. The cases are X08 and DA09 of the test plan.

**Other owners.** The B2 work of step 3 applies section 2.5 to the reset retries and their bookkeeping.

### 2.8 X13 tracking entry

**Finding.** In the JVM baseline report, a blocking completion callback ran on the writer thread and held the manager
lock. Admission and shutdown waited, and a state read did not. A callback that threw turned the resolved outcome into
its exception.

**Required treatment.** The delivery rules of section 2.6.

**Status.** Specified in this proposal. Not implemented.

**Verification.** No evidence yet. The case is X13 of the test plan.

**Other owners.** The caller work of the selected admission design moves the effects of each caller to the awaited
result.

### 2.9 Acceptance cases

Section 2 is accepted on these cases of the test plan:

| Contract | Cases |
|---|---|
| Operation identity, duplicate results, and late results | X09, X15 |
| Admission, settlement, and callers | X10, DA12, DA13, DA14 |
| Fingerprint acceptance and invalidation | X11, DA12, and the D5 point 11 case on a contradiction during an operation |
| Write ownership and cancellation | X08, DA09, X06, X07 |
| Retries, acknowledgements, and completions that are no longer current | X01, X17, DA13, and the D5 point 11 cases on expiry cleanup |
| Result delivery | X13 |

## 3. Count-first C2: transition table and JVM model requirements

### 3.1 Protocol and limits

C2 adds durable admission to section 1. Each PIN operation stores a reservation before its PIN check. Section 3.2 of
the P3 decision proposal gives the predicted protocol, its effects, and its costs. D4 gives the meaning of the count
and the events.

The reservation is the complete record of D5 point 3:

- The count is the current count plus one, saturated at 11.
- Below the threshold, all three clock keys hold NONE.
- At or above the threshold, the record holds a deadline with the lock duration of the new count. Its time anchor is
  the admission, and its boot ID is the latest completed boot-ID result.

Limits:

- C2 keeps no degraded fallback for a checked PIN (D3).
- A write fault closes the PIN path, because a reservation that is not confirmed starts no check.
- A reservation write that is not acknowledged costs one count without a check. Until storage acknowledges that
  debit, only memory enforces it (section 3.3). A restart can then return the count and remove the lock, which is part
  of the proposed residual R-007/11.
- An interrupted operation costs one count, also when its PIN was correct (the availability cost of D2).
- A restart after an accepted PIN success whose reset did not persist stays residual R-007/4.

### 3.2 Transition table

One PIN operation is in the state Reserving, Checking, or Unconfirmed until it ends. Section 2.4 defines an active
operation. After a replacement of the record under section 1.7, the rows change nothing in memory and submit no write
(section 2.5).

| Row | State | Event | Effect | Next state |
|---|---|---|---|---|
| 1 | None | A caller submits a PIN, and section 2.3 admits the operation | Authentication coordination builds the reservation and submits its write. Memory does not change. | Reserving |
| 2 | Reserving | The write returns true, and the operation is active | The reservation becomes the record in memory. Readiness becomes LockedOut when the reservation holds a deadline that has not ended. In the same step, authentication coordination claims the check, which then runs outside the lock. | Checking |
| 3 | Reserving | The write returns true, and the operation is not active | The reservation becomes the record in memory and stays consumed. No check starts. The operation is settled. | Ended |
| 4 | Reserving | The write returns false or throws | The reservation becomes the record in memory, with its count and its deadline, and its durability is marked unconfirmed. No check starts for this operation, now or later. The caller gets the result "storage unavailable". The write retries with the same payload (section 3.3). | Unconfirmed |
| 5 | Unconfirmed | The write of the reservation, or of a newer record, returns true | The debit is confirmed. The operation is settled without a check and without an event. | Ended |
| 6 | Reserving or Checking | The write or the check does not return | The operation stays unsettled, so section 2.3 admits nothing. | Unchanged |
| 7 | Reserving or Checking | The caller cancels the operation, or section 2.4 invalidates it | The operation is no longer active. Its write continues, and a running check finishes. The operation stays unsettled until section 2.3 settles it. | Unchanged |
| 8 | Checking | The check returns wrong, and the operation is active | The result is accepted. No write follows, because the reservation already holds the count. The caller gets the wrong-PIN result with the enforcement count of the reservation. `LOCKOUT_TRIGGERED` follows D4 point 6. | Ended |
| 9 | Checking | The check returns correct, and the operation is active | The result is accepted under section 2.4. The reset write holds count 0 and NONE in all three clock keys. | Ended |
| 10 | Checking | The check returns, and the operation is not active | The result is discarded. The reservation stays consumed. | Ended |
| 11 | Any | The process dies | Storage holds the last record that a write committed, with or without an acknowledgement. This can be the record from before the reservation, the reservation, or a newer record such as its expiry write. The next process reads it under section 1 and emits no event for a reservation. | None |

### 3.3 Rules for the requirements still to prove

The numbers are those of the requirements still to prove in section 3.2 of the P3 decision proposal.

- **Ownership at the threshold (1).** A reservation at or above the threshold locks every other request at row 2. The
  reserving operation keeps permission for its own check, and its correct result is accepted in LockedOut (D2
  refinement 2).
- **Unacknowledged write (2).** A reservation write that returns false or throws is no evidence that storage holds the
  reservation, and none that storage does not hold it. Row 4 applies the lead's decision of 2026-11-11 to this case.
  Its retries keep the payload, so they neither raise the count nor move the anchor. The debit is enforcement data,
  not a confirmed failure.
- **Expiry before confirmation (2).** When the deadline of an unconfirmed reservation ends, section 1.8 applies. The
  expiry write holds the count of the reservation with NONE in all three clock keys, so it keeps the debit. As the
  newer mutation, it replaces the reservation as the target of the WRITE chain (section 2.5). A reservation retry that
  is already running can still return true. Its acknowledgement then confirms the debit although the retry is no
  longer current, and row 5 applies. The retry publishes no deadline: memory keeps the cleared deadline, and the
  expiry write continues. Admission does not wait for that expiry write.
- **Confirmation.** The first acknowledgement of a write that holds the debit confirms the accounting of section 2.3.
  That write is the reservation write, one of its retries, or the write of a newer record. A newer record is an expiry
  write, which keeps the count, or the replacement of section 1.7, which holds the cap.
- **Cancellation (2).** Rows 3, 7, and 10 give the rules, with the ownership rule of section 2.5 and the invalidation
  rule of section 2.4.
- **Stall and expiry (3).** The check of row 2 starts whenever the write returns true for an active operation, also
  after the deadline of the reservation has ended. A deadline that ends during the check is observed as section 1.8
  states, and the result is accepted under the same rules. One reservation admits one check. The next admission needs
  Ready, so it comes no earlier than the end of that deadline.
- **Restart (3).** The stored record needs no recovery step, and C2 adds no state to the state table of D2. A restart
  neither restores stored capacity nor moves a stored deadline (D3, constraint I8). The two proposed departures of
  section 2.3 concern a restart after row 4 or during row 6.
- **Accounting (4).** An unfinished reservation is no evidence of a wrong PIN. Rows 3, 5, 10, and 11 emit no audit
  event, no `LOCKOUT_TRIGGERED`, and no capture (D4 points 3 and 6). Legacy capture is disabled or unreachable before
  C2 is active (D4 point 7).
- **Compatibility and trust (5).** Section 1 and D4 points 4 and 5 give these rules.
- **Availability and latency (6).** The device comparison of P3 measures the authentication time. The model counts the
  correct PINs that each fault denies.

### 3.4 JVM models

**Arrangement.** The lead set the form of the JVM models on 2026-11-11:

- The models are protocol models in test code, with fake storage. One shared test harness holds the common C1
  readiness, the simulated record store, the clocks, and the crash cuts. C2 and Option A are separate transition
  implementations in it, and each keeps its own record and recovery rules.
- The cases of section 1.11 test C1 readiness directly. No variant models the baseline admission.
- The expected results and the invariant checks are independent of the transition implementations. The cases are
  reused when production code is tested.
- Model results identify a provisional candidate. Real-thread checks of constraints I1 and I3, actual storage, and
  device qualification remain required before the final selection.

**Scope of the C2 model.** The model holds the record in memory, the stored record, each write with its phase, the
state of each operation, the verifier entries, the events, and the sessions. A verifier entry is the start of a PIN
check, as D3 counts it. A write can return true, return false, throw, hold before its commit, hold after its commit,
or commit and then report false. The process can die at every step. The reset write has no retries in this model,
because step 3 adds them.

**Invariants.** The model checks them after every step:

1. A check starts only for an operation whose reservation write returned true at its first attempt. An operation whose
   reservation write failed never gets a check.
2. No PIN operation and no fingerprint operation is admitted while a PIN operation is unsettled, and each result is
   consumed at most once.
3. A cancelled or invalidated operation gets no session, no reset, and no event.
4. The stored count of a trusted record decreases only after an accepted correct PIN. The replacement of an invalid
   record under section 1.7 is outside this invariant.
5. Every write of one mutation carries the same payload, so no deadline changes its anchor (constraint I8).
6. No wrong-PIN event, `LOCKOUT_TRIGGERED`, or capture exists without an accepted wrong result (constraint I5).
7. An accepted correct PIN gets its session in the acceptance step, before any write returns (constraint I6).
8. Writes run one at a time in admission order, and a completion that is no longer current publishes nothing but its
   acknowledgement (constraints I2 and I4).
9. No operation stays unsettled once storage has acknowledged its debit and its check, if it started, has returned.

For each schedule with restarts, the model also runs the matched schedule without restarts, with equivalent inputs,
faults, and clocks. The run with restarts must show no more verifier entries, no restored attempt capacity, and no
removed delay (D3). The model counts separately what only the end of an in-process blockage permits (the proposed
residuals R-007/11 and R-007/12). These results fail the check until the lead adopts the departure.

### 3.5 Acceptance cases

Section 3 is accepted on these cases of the test plan, run on the C2 model. A cut that C2 does not have is recorded as
not applicable.

| Requirement | Cases |
|---|---|
| Every protocol cut, with a wrong, a correct, and an unchecked PIN | DA01 to DA08, R3.1 |
| Ownership at the threshold, and one operation across callers | X10, R3.2, R3.3 |
| Unacknowledged write, duplicates, and cancellation | R3.4, X09, X08, DA09 |
| Stall, expiry, and restarts | DA10, DA11, R2.4, X15 |
| In-process blockage and restart | DA13, DA14 |
| Accounting | H02, and constraint I5 in every case |
| Fingerprint results during a PIN operation | DA12, X11 |

## 4. Option A: design and JVM model requirements under D6

### 4.1 Purpose and limits

Option A is the comparator for count-first C2 (D6). It stays a design and a JVM model with fake storage. The lead
records A's persistence exception before a device prototype or a selection of A (D6 point 4).

A stores a reservation before each PIN check, like C2. Unlike C2, the reservation does not change the count. The count
changes when the outcome is known, or when the reservation stays unfinished.

Limits:

- A shares the costs that the P3 decision proposal names for C2: a write before every check, asynchronous callers, and
  a closed PIN path during write faults.
- A wrong PIN needs a second write for its outcome.
- A restart after an accepted PIN success whose reset did not persist finds the reservation and consumes one attempt.
  This is A's form of residual R-007/4.

### 4.2 Record

A's lockout record is the record of D5 point 3 plus three reservation keys, in the same file and the same atomic
commit:

| Key | Value |
|---|---|
| `reservation_wall` | The wall time at the admission, or NONE |
| `reservation_elapsed` | The elapsed realtime at the admission, or NONE |
| `reservation_boot_id` | The latest completed boot-ID result at the admission (a known ID or UNKNOWN), or NONE |

- All three keys hold NONE, or all three hold the anchor of one reservation. The record stores at most one
  reservation.
- **Base record.** The count and the clock keys form the base record. A reservation does not change the base record.
- **Count.** The count is a capped enforcement count. It is not a number of wrong PINs: D1 can set it to 11 without
  any PIN attempt, and saturation at 11 ends any exact total.
- **Validation.** The storage boundary checks the presence, the types, and the NONE pairing of the three keys, as
  section 1.5 states for the clock keys. Lockout logic finds a reservation invalid when its boot ID matches the
  current known ID and the current elapsed time is earlier than `reservation_elapsed`. An invalid record leads to the
  replacement of section 1.7.
- **Persistence.** The keys add no file and no table, and the Room schema does not change. A later crypto migration
  must keep the reservation, the count, and the deadline in one atomic record (D6 point 5).

### 4.3 Transition table

One PIN operation is in the state Reserving, Checking, or Releasing until it ends. After a replacement of the record
under section 1.7, the rows change nothing in memory and submit no write (section 2.5).

| Row | State | Event | Effect | Next state |
|---|---|---|---|---|
| 1 | None | A caller submits a PIN, and section 2.3 admits the operation | Authentication coordination builds the base record of memory plus the reservation of this operation, and submits its write. Memory does not change. | Reserving |
| 2 | Reserving | The write returns true, and the operation is active | The reservation enters memory beside the base record. In the same step, authentication coordination claims the check, which then runs outside the lock. | Checking |
| 3 | Reserving | The write returns true, and the operation is not active | No check can start. The reservation enters memory beside the base record, and authentication coordination submits the release write of section 4.5. | Releasing |
| 4 | Reserving | The write returns false or throws | Storage may hold the reservation, so the reservation enters memory beside the base record. No check starts for this operation, now or later. The caller gets the result "storage unavailable". Authentication coordination submits the release write of section 4.5. | Releasing |
| 5 | Reserving, Checking, or Releasing | The write or the check does not return | The operation stays unsettled, so section 2.3 admits nothing. | Unchanged |
| 6 | Reserving or Checking | The caller cancels the operation, or section 2.4 invalidates it | The operation is no longer active. Its write continues, and a running check finishes. Row 3 follows a cancellation before the claim of row 2, and row 10 follows a later one. | Unchanged |
| 7 | Releasing | The release write returns true, and memory still holds the reservation of this operation | The reservation leaves memory. The base record is unchanged, so the attempt is not consumed. The operation is settled. | Ended |
| 8 | Releasing | The release write returns false or throws | The release write retries under section 2.5 with the same payload. The operation stays unsettled. | Releasing |
| 9 | Checking | The check returns wrong, and the operation is active | The result is accepted. The consumed record of section 4.4 becomes the base record in memory, and the reservation leaves memory. The outcome write holds that base record and no reservation. The caller gets the wrong-PIN result. | Ended |
| 10 | Checking | The check returns, and the operation is not active | The result is discarded. While memory still holds the reservation of this operation, the attempt is consumed as an unknown outcome: memory and the outcome write change as in row 9, with no event. After a replacement of the record, nothing changes and no write follows. | Ended |
| 11 | Checking | The check returns correct, and the operation is active | The result is accepted under section 2.4. The reset write holds count 0, NONE in all three clock keys, and no reservation. | Ended |
| 12 | Any | The process dies | Storage holds the last record that a write committed. The next process applies the recovery of section 4.4 to a stored reservation. | None |

### 4.4 Consumed attempts, recovery, and confirmation

**Consuming an attempt.** Consuming the attempt of a reservation turns the base record into the consumed record:

- The count becomes the count of the base record plus one, saturated at 11.
- When the new count is at or above the threshold, the consumed record holds a deadline with the lock duration of the
  new count. Its time anchor and its boot ID are those of the reservation.
- Below the threshold, the clock keys stay NONE.

Rows 9 and 10 and the recovery use this one rule, so the outcome of a check and the recovery have the same anchor and
the same duration. Recovery adds no extension, so it needs no further D3 disposition (D6 point 5). The deadline of the
consumed record replaces an ended deadline that the base record still stores, also where D5 would revive that
deadline after a restart. The reservation has the later anchor and at least the same duration.

**Recovery.** A process that reads a valid record with a reservation consumes that attempt, with an unknown outcome.
The consumed record becomes the base record in memory. Lockout logic derives it at each read from the stored count and
the stored anchor, so repeated restarts give the same count and the same deadline, and the attempt is charged once.
The recovery writes nothing and emits no event. The next mutation writes the base record of memory without the old
reservation. Recovery adds no state to the state table of D2: readiness follows section 1.4 with the base record of
memory.

**Confirmation.** Three events confirm the accounting of section 2.3:

- **A returned check.** After a wrong or a discarded result, storage holds the reservation, or the outcome record
  once the outcome write has committed, with or without an acknowledgement. Either gives the same count and deadline
  after a restart. The confirmation therefore waits for neither the outcome write nor the reset write of row 11, and a
  failed outcome write of row 9 or row 10 gets no retry. The next mutation writes the base record of memory.
- **An acknowledged release write**, for an operation without a check (section 4.5).
- **An acknowledged replacement** under section 1.7, because that record holds the cap and no reservation.

**Stall and expiry.** The rule of section 3.3 applies. A confirmed reservation permits its check while the operation is
active, also when the deadline of its consumed record would already have ended.

### 4.5 Release of an unchecked reservation

A running process can establish that a check never started. A releases the reservation of such an operation (lead
decision of 2026-11-11). After a restart that proof is lost, so the recovery of section 4.4 applies.

- **Claim against cancellation.** The claim of row 2 and the cancellation of row 6 compete under the common lock. When
  the cancellation comes first, no queued work can start the check, and row 3 follows. When the claim comes first, the
  attempt stays consumed (row 10).
- **Unacknowledged reservation write.** Row 4 leads to the same release. The process knows that no check started, and
  it does not know whether storage holds the reservation.
- **Release write.** The release write holds the base record of memory and no reservation. A reservation never
  changes the base record (section 4.2), so the release changes neither the count nor the clock keys and cannot lower
  a count of 11. Authentication coordination owns the write, whatever the caller cancels (section 2.5).
- **Confirmation.** The capacity is free again only after storage has acknowledged the release write (row 7). Row 7
  applies only while memory still holds the reservation of this operation, which the record generation and revision
  show.
- **Process death.** A death before the release commits leaves the stored record as it was. A stored reservation is
  then unfinished, and the recovery of section 4.4 consumes it. A committed release stays released after a restart,
  also when its acknowledgement was lost.

### 4.6 A's rules for the items of D6 point 5

| Item | A's rule |
|---|---|
| Wrong, correct, and unchecked PINs at every cut, for each caller | Section 4.3. The model takes the caller as an input of each operation. |
| Cancellation before and after the check starts, including X08 | Section 4.5, with rows 3, 4, 6, and 10 |
| A long stall, and an expiry during the check | Section 4.4 |
| Repeated restarts during recovery | Section 4.4. The recovery writes nothing, so no interruption can cut it. |
| Duration and anchor of a lock tied to an unfinished reservation | Section 4.4 |
| Recovery states | None (section 4.4) |
| Degraded fallback | None. Storage keeps a consumed attempt, also after a failed outcome write (section 4.4). |
| Outcome durability and unknown outcomes | An outcome is durable once its write has committed. A restart before that commit gives the unknown outcome of section 4.4. |
| Reservation owner (D2 refinement 2) | A reservation installs no lock, so the owner needs no exception. Sections 2.3 and 2.4 apply. |
| Count meaning (D4 point 3) | Section 4.2. Rows 9 and 10 and the recovery each consume one attempt, and a release consumes none. |
| Events, audit, and capture (D4 points 6 and 7) | A wrong-PIN event exists only for row 9. `LOCKOUT_TRIGGERED` exists only for a row 9 result whose new count is at or above the threshold. The stored reservation already makes that deadline durable. Rows 10 and 12 emit none. Legacy capture is disabled or unreachable before A is active, as for C2. |
| Anchor of a reservation (D5 point 3) | The admission, stored with the reservation (sections 4.2 and 4.4) |
| A fingerprint success while a reservation is pending | Section 2.3 denies it until the PIN operation is settled. An accepted fingerprint changes neither the count nor a reservation. |
| The additional record | Section 4.2 |
| Failure-based limits and capture of 2.0.0 | Section 4.8 |

### 4.7 JVM model and comparison

**Model.** A's model uses the arrangement, the scope, and the invariants of section 3.4, at the level of detail of the
C2 model (D6 point 5). Where invariants 1 and 9 of section 3.4 name the debit, A's model reads the reservation and
the confirmation of section 4.4. Three invariants are A's own:

1. Storage loses a reservation without a consumed attempt only through a committed release write, for an operation
   whose check was never claimed. An acknowledgement is not needed for this. The reset of an accepted correct PIN and
   the replacement of section 1.7 are outside this invariant.
2. In the process that submitted a release write, the capacity is free again only after storage acknowledged that
   write. No release changes the base record, so none lowers the count.
3. An unfinished reservation is charged exactly once, with the same anchor and the same duration in every later
   process.

Section 4 is accepted on the cases of section 3.5, run on the A model. DA09 holds the release races and crash cuts of
section 4.5.

**Measures.** The lead named the measures and the shared schedule of D6 point 3(b) on 2026-11-11. Each measure is taken
for each schedule and each model:

1. Verifier entries without a valid, durable reservation for that operation. Another reservation record in storage
   does not count.
2. Extra verifier entries of a run with restarts, against its matched run without restarts, with equivalent inputs,
   faults, and clocks. The entries under the proposed residuals R-007/11 and R-007/12 are reported separately.
3. Unauthorized loss of enforcement capacity. These are not losses: a committed release under section 4.5, with or
   without an acknowledgement, an accepted reset, normal expiry, and the replacement of an invalid record. In the
   process that submitted a release, freeing the capacity before the acknowledgement is a loss. A debit that only
   memory held and that a restart ends is reported separately under R-007/11.
4. The time during which authentication is permitted while a lock applies under D5. The departures that D5 approves
   are reported separately.

**Shared schedule.** The schedule holds the cuts and cases DA01 to DA14, each cut with a wrong, a correct, and an
unchecked PIN, and a cycle of 20 restarts. The 100 seeded schedules of the test plan are additional coverage. The
seeds, the generator version, and the run bounds are frozen before the comparison. Both models receive equivalent
external events and faults at corresponding logical cuts, which the same seed alone does not ensure.

**Reading the result.** Measurements from the models are model evidence (D6 point 3). Equal measured exposure does not
advance A, and a comparison that shows no advantage for A is a valid result. The comparison also reports the attempts
that each model consumes without a check. The release of section 4.5 makes A differ from C2 on that count, which is an
availability measure.

### 4.8 Transition data for 2.0.0

D6 point 5 asks for this data on the failure-based limits (FR-009, FR-010) and capture (FR-081, FR-082) of the 2.0.0
SRS. It informs the selection and authorizes no persistence.

- **Outcome information available.** A running process knows each accepted wrong result at row 9. The stored record
  shows one reservation that is in progress or unfinished.
- **Outcome information lost.** A restart loses the outcome of an unfinished reservation, also when the check had
  returned (transition refinement 5). The count does not separate wrong results, unknown outcomes, and a D1
  replacement.
- **Migration work.** The reservation keys move with the count and the deadline as one atomic record. A migrated
  reservation keeps its anchor and its boot-ID scheme (D5 point 9).
- **Implications.** Failure-based limits and capture need separate accounting of confirmed failed verifications under
  A, as under C2. Row 9 is the step at which A knows a confirmed failure.
