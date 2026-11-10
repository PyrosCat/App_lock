# M7 / F2 Hardening: P3 Step 2 Designs (R-007)

**Date:** 2026-11-09

**Status:** Proposal for the lead, not approved. Section 1 is drafted. Sections 2 to 4 are not drafted yet.

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

**Tracking entries:** each finding has one entry, which holds its status and its evidence. X14 is in section 1.10. X08
and X13 go into section 2.

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

Not drafted yet. This section is to hold the shared rules of D2 for operation IDs, write ownership, cancellation, and
callbacks, with the tracking entries of X08 and X13.

## 3. Count-first C2: transition table and JVM model requirements

Not drafted yet.

## 4. Option A: design and JVM model requirements under D6

Not drafted yet.
