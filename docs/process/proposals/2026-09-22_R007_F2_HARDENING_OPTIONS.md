# R-007: F2 hardening options

**Date:** 2026-09-22  
**Status:** Proposal; implementation approach and residual-risk acceptance remain undecided.  
**Placement:** After F4 and before F5. This moves the four R-007 residuals forward from their previous F6 review point.  
**Recommended starting point:** Option B, bounded recovery retries; consider fallback-deadline persistence separately.

## 1. Purpose and scheduling change

F2 fixed the main R-007 enforcement gap: a failed lockout write now produces an in-memory lockout that the actual authentication gates read. Four storage and restart residuals remain. This document compares two ways to address them:

- **Option A — authentication redesign:** require a durable attempt record before PIN verification, and recover interrupted attempts after restart.
- **Option B — mitigation:** retain the current authentication flow and add bounded recovery retries for transient storage faults.

Both options belong to a new **F2 hardening** milestone between F4 and F5. They are alternatives, not two mandatory implementations. Shared recovery work may be reusable if the larger redesign is selected later.

Previous sequence:

```text
F4 -> F5 graph wiring -> F5 fleet checkpoint -> F6 activation and R-007 revisit
```

Revised sequence:

```text
F4 observational adapters
  -> F2 hardening: choose scope, implement, review, and verify on the fleet
  -> F5 graph wiring
  -> F5 fleet integration checkpoint
  -> F6 activation
```

The four risks must receive a treatment or an explicit disposition at F2 hardening. Moving the milestone does not require eliminating every residual before F5. F5 can proceed once the selected work passes its gates and the lead records what remains accepted or deferred.

F2 hardening is not a reopening of the completed F2 commit. It is separate work with separate reviewable commits and evidence. The manager and legacy engine already serve production, so hardening cannot be tested only against the future runtime.

The risk register and M7 plan now place R-007 residual treatment at F2 hardening. The implementation option and each residual's disposition remain open. This proposal does not accept or close a risk. R-008 and other risks are outside this scheduling change.

## 2. Current behavior and the four residuals

The current manager publishes its authoritative state in memory and writes snapshots through one dedicated ordered writer. Failure counts and threshold enforcement update immediately. The runtime does not wait for storage on its event loop. Completion results carry each attempt's own count and write outcome for audit and capture.

Successful authentication clears the in-memory counters immediately. A failed persistent clear is reported, but does not undo that reset. The production legacy path and the future runtime share the manager; the runtime remains unwired until the later cutover.

The labels R-007/1 through R-007/4 below are local labels for the existing residuals, not new risk-register identifiers.

| Residual | Trigger and consequence | Security direction |
|---|---|---|
| **R-007/1: cold-start read failure** | The initial read fails and the manager returns `Available`. A persisted lockout may remain unenforced. A local mutation disables later reseeding so recovery cannot overwrite newer state. | Fail-open when a lockout should have applied. |
| **R-007/2: restart during degraded enforcement** | A failed write creates an in-memory fallback. Process death loses that fallback if no durable recovery record exists. | Fail-open for the lost enforcement interval. |
| **R-007/3: death before writes complete** | The process dies with admitted but uncommitted changes. Restart uses the last durable state. This can occur even if the write would otherwise have succeeded. | Lost failures weaken enforcement; a lost reset can over-enforce. |
| **R-007/4: failed reset followed by restart** | A reset clears memory but fails to clear storage. Restart can reload stale counters or an old deadline, if still relevant. | Fail-secure over-enforcement. |

These cases overlap. For example, a failed write followed by process death can involve both /2 and /3. Their probabilities must not be added as if they were independent events.

### Likelihood and confidence

The working assumption is that these are uncommon on healthy devices. There are no field measurements that support an absolute percentage for any residual. Fault-injection tests establish behavior under a fault, not its frequency in normal use.

Relevant measurements would include startup count, initial read failures, failed writes and resets, recovery latency, and evidence of interrupted writes across restarts. Diagnostics must exclude PINs, credentials, and unnecessary identifying data. Remote telemetry is not part of this proposal.

Retries can shorten the period during which state exists only in memory. Their benefit depends on how often faults are transient and how quickly recovery occurs. Persistent faults receive little benefit. Deliberate restarts also differ from random process death; a low accidental frequency does not establish resistance to repeated intentional interruption.

## 3. Boundaries shared by both options

1. Keep the runtime event loop, screen-off processing, and overlay dismissal responsive. Never await storage on the single runtime drain.
2. Keep a single ordered persistence writer. Do not introduce competing writers to work around a slow or stuck write.
3. Do not hold the mutation-admission lock across storage I/O.
4. Do not let stale reads or writes replace newer authentication state.
5. Preserve the distinction between an actual authentication failure and storage recovery. Recovery must not fabricate wrong-PIN events or intruder captures.
6. Preserve immediate in-memory reset after an accepted authentication success. Any proposal to delay access until reset persistence succeeds is a separate product decision.
7. Treat a write that returns failure differently from a write that has not returned. A coroutine timeout does not prove that a blocking write stopped or that no data reached storage.
8. Keep Android-managed biometric retry accounting unchanged unless separately approved. This work must not silently start counting biometric mismatches as PIN failures.

There is a fundamental limit: if no required state reaches durable storage before process death, an in-memory implementation cannot reconstruct it reliably afterward. Neither option makes permanently unavailable storage reliable.

## 4. Option A — redesign authentication admission

### 4.1 Intended behavior

Introduce a durable pending-attempt record before evaluating a PIN. Authentication admission would follow this sequence:

```text
Read trusted lockout state
  -> Admit one PIN submission
  -> Persist its pending-attempt record
  -> Confirm the write succeeded
  -> Verify the PIN
  -> Record the outcome and update enforcement
```

The record would contain an attempt identifier, schema/version information, and the minimum state required for recovery. It must not contain the PIN or other credential material.

If admission persistence fails, PIN verification does not start. If the process dies before the outcome is recorded, startup recognizes the unfinished record and applies a defined conservative recovery policy before another attempt is permitted.

Suggested availability states are `Ready`, `LockedOut`, `Recovering`, and `StorageUnavailable`. Their exact API is a design choice. Permission to authenticate must be explicit: existing callers must not interpret every state other than `LockedOut` as permission to proceed.

### 4.2 Decisions required before coding

- **Persistent storage failure:** define the user-visible recovery path. Blocking authentication indefinitely has an availability cost. Automatically permitting authentication after a timeout restores a security gap. Neither choice should be hidden in implementation details.
- **Interrupted attempts:** decide how an unfinished reservation affects future admission and deadlines. It cannot prove that the submitted PIN was wrong or even checked.
- **Repeated restart:** recovery must not grant unlimited fresh attempts or continually extend a countdown merely because the app restarted.
- **Cancellation:** distinguish cancellation before verification from an interruption after verification started. A late write completion must not revive a cancelled request.
- **Mutation ownership:** assign one component responsibility for counting failures, resetting counters, granting sessions, and recording outcomes. Existing engine callbacks must not perform the same mutation again.
- **Biometrics:** define availability gating before prompt launch and reset handling after success, while retaining Android's retry accounting. Do not apply PIN reservation semantics to biometric sessions without a separate review.
- **Migration and downgrade:** preserve existing counts and deadlines when introducing the new schema. Define how an older application version would treat the new records; do not assume downgrade is safe.
- **Reset durability:** explicitly accept that immediate successful unlock can still be followed by stale enforcement after a crash before the reset commits.

### 4.3 Proposed implementation slices

| Slice | Scope | Gate |
|---|---|---|
| **A0: design review** | Resolve recovery policy, state transitions, mutation ownership, cancellation, migration, and the reset tradeoff. | Reviewed protocol and transition cases before implementation. |
| **A1: inactive protocol** | Add the versioned attempt record, ordered persistence operations, and restart recovery logic without activating new authentication behavior. | JVM recovery, migration, and ordering tests; normal build gates. |
| **A2: caller integration** | Activate admission in the live self-gate and legacy activity; adapt the future overlay path. Add asynchronous submissions, a shared in-flight guard, and request-keyed result handling. | No caller bypasses admission; no duplicate counts or stale unlocks; normal build gates. |
| **A3: recovery and diagnostics** | Add bounded persistence recovery, user-visible unavailable/pending states, and diagnostic reporting. | Failure, cancellation, stalled-write, and shutdown tests; normal build gates. |

The asynchronous self-gate work currently planned for F6 would move into A2. F6 would then reuse it rather than add a second asynchronous flow. Because A2 changes live behavior, it must include all existing callers in its scope.

### 4.4 Benefits

- Prevents PIN evaluation when the prerequisite attempt record cannot be saved.
- Gives startup evidence that an attempt was interrupted instead of silently treating it as absent.
- Can address the restart bypass mechanisms behind /2 and /3 when recovery is enforced correctly.
- Makes unavailable storage an explicit authentication state rather than an implicit `Available` result.

### 4.5 Costs and new risks

- Every PIN attempt gains a storage dependency and additional latency.
- Storage corruption or a long stall can prevent legitimate users from authenticating.
- An interrupted correct or unevaluated PIN can lead to conservative enforcement.
- New asynchronous paths create cancellation, stale-result, and credential-buffer lifetime concerns. PIN buffers must not remain queued indefinitely and must be cleared when no longer needed.
- Partial caller migration could create a bypass. Duplicate mutation ownership could count failures twice.
- A versioned persistence protocol adds migration and maintenance work.
- A successful reset still cannot survive process death unless its persistent change actually succeeds. Eliminating this residual would require a further change to successful-unlock behavior.

### 4.6 Residual outcome

Option A offers the stronger enforcement policy, but converts some storage faults into denied legitimate access. It does not recover the exact result of an interrupted authentication. It uses an explicit conservative policy for that uncertainty. /4 remains mitigated, not eliminated, while immediate unlock is preserved.

## 5. Option B — bounded recovery retries

### 5.1 Intended behavior

Keep F2's authentication flow and add a small shared recovery mechanism around persistence. It does not reserve attempts or require a disk write before checking a PIN.

Use bounded retry scheduling with backoff. Delays must not occupy the writer thread. Limit scheduled recovery work to prevent one retry chain per failed attempt. Recovery exhaustion is reported; it does not crash the app or undo an in-memory reset.

Select retry counts and delays during implementation review. They are tuning parameters, not security guarantees. Avoid a retry budget that resets on every UI poll and therefore becomes an unlimited tight loop.

### 5.2 B1 — automatic cold-start read recovery

After the initial read fails, schedule background retries rather than depending only on calls to `currentState()`.

- Permit at most one recovery read at a time.
- Check eligibility before reading and again before applying the result.
- Stop automatic reseeding after a local mutation, successful recovery, shutdown, or retry-budget exhaustion.
- Coordinate with the existing read-triggered retry path so UI polling does not bypass the backoff or create duplicate reads.
- Do not load old persisted counters over a newer reset or locally counted failure.

**Benefit:** transient cold-start faults can recover sooner, reducing /1's exposure window.

**Limit:** persistent read failure remains fail-open under the existing policy. If a local mutation disables reseeding, this mechanism cannot recover the unknown historical counters safely by simply replacing the snapshot.

### 5.3 B2 — safe retry of failed persistent changes

Schedule recovery after a write returns false or throws a contained storage exception. Prioritize correctness of the current state over replaying the exact failed operation.

- Retry a failed reset only while it is still applicable.
- If new authentication activity supersedes it, discard that reset retry. Reconcile the applicable current state through the same writer if needed.
- Validate freshness before disk I/O, not only before publishing the completion.
- Preserve write ordering across ordinary operations and recovery work. A newly constructed recovery snapshot must not jump ahead of older queued writes and then be overwritten by them.
- Preserve each original attempt's completion result and audit/capture identity. Coalescing recovery requests must not coalesce actual authentication outcomes.
- Separate the retry scheduler's lifecycle from accepted ordinary writes. Shutdown prevents new retries and late state publication; it does not claim that an already-running disk operation can be cancelled safely.

**Benefit:** transient reset failures become less likely to survive until the next restart, reducing /4.

**Limit:** a crash before recovery succeeds still loses the reset. Saving the latest state does not reconstruct failure counts that were never read at startup.

### 5.4 B3 — optional persistence of the degraded deadline

When storage recovers, save the current count and remaining fallback deadline. A successful count-only write does not preserve a below-threshold degraded lockout.

- Translate the remaining monotonic duration into the persisted deadline format at the time of the recovery write.
- Preserve the original expiry; do not restart the fallback window on retries.
- Skip expired fallback enforcement. Do not revive it because an old retry became runnable late.
- Track freshness of the complete recovery snapshot, including changes to the fallback deadline. An authentication revision alone may be insufficient if deadlines change without a new admission.
- Confirm that the persisted representation retains any longer existing recorded lockout.
- A successful recovery write may update the durability description of current enforcement, but must not retroactively rewrite the original attempt's reported outcome or emit another `LOCKOUT_TRIGGERED` event.

**Benefit:** reduces /2 if recovery commits before the process dies.

**Limit:** persistent write failure, death before recovery, and existing reboot/wall-clock limitations remain. This slice changes restart behavior and is more involved than simple reset retries; review it separately.

### 5.5 Rules that prevent retries from becoming new authentication events

Retry failures must never call the ordinary failed-authentication transition merely to reuse code. That transition may extend the fallback from the failure completion time. Reusing it for each retry could produce an unexpectedly long or repeated lockout.

Recovery must not:

- Increment the failure count.
- Restart or extend the fallback deadline.
- Grant or revoke an authentication session.
- Repeat intruder capture or authentication audit events.
- Disable or dismiss the current surface independently of the runtime.

A successful read is not a substitute for a confirmed successful write. In-process preference state can differ from what survived on disk. Persistence evidence must come from the writer contract and restart verification, not merely from reading back the same object.

### 5.6 Benefits

- Preserves normal PIN verification and immediate unlock behavior.
- Adds no mandatory pre-verification write to the healthy authentication path.
- Can reduce exposure to transient read and reset-write failures with relatively small scope.
- Reuses the current writer, snapshot, and lifecycle model.
- Can be delivered incrementally: B1 and B2 first, B3 only if its additional benefit justifies the work.

### 5.7 Costs and new risks

- Stale recovery work could erase newer failures or restore an old lockout if ordering is wrong.
- Unbounded retries could consume resources, delay ordinary writes, and flood diagnostics.
- Incorrect deadline handling could extend enforcement or persist an expired fallback.
- A write that never returns cannot be repaired by retries queued behind it. Do not start a parallel replacement writer.
- Recovery remains best effort. Faster retries do not prove the crash window is closed.

### 5.8 Residual outcome

B1 mitigates /1 for transient faults. B2 mitigates /4. B3 mitigates /2 after recovery succeeds. **/3 is largely unchanged:** the process can still die before the first successful durable write. The lead should explicitly disposition that limitation rather than mark it resolved by retries.

## 6. Comparison and recommendation

| Question | Option A: redesign | Option B: mitigation |
|---|---|---|
| Main objective | Prevent evaluation of a PIN without durable attempt admission. | Recover current state sooner after transient faults. |
| Healthy-path latency | Adds a pre-verification storage dependency. | No mandatory new pre-verification write. |
| Storage-unavailable behavior | Authentication is blocked pending recovery or an approved recovery path. | Retains the existing authentication policy. |
| Restart protection | Stronger if admission and startup recovery cover every caller. | Best effort; depends on a successful recovery write before death. |
| Live caller changes | Broad: asynchronous submissions, admission gating, and ownership changes. | Primarily manager and persistence recovery changes; verify callers for compatibility. |
| State/schema complexity | Higher; pending records, recovery semantics, and migration. | Lower overall; optional fallback persistence still needs careful deadline handling. |
| New availability risk | Significant: correct credentials may not be evaluated during storage failure. | Smaller; retries can still cause delay or over-enforcement if implemented incorrectly. |
| Risk /3 | Addresses the attempt-loss mechanism through durable admission and conservative recovery. | Largely remains. |
| Risk /4 with immediate unlock | Remains possible before a durable reset. | Remains possible before a successful retry. |
| Evidence required | Protocol, caller, migration, performance, and fleet restart verification. | Retry ordering, lifecycle, deadline, and fleet restart verification. |

**Recommendation:** begin with B1 and B2 after F4. Treat B3 as an explicit optional scope decision. Retain /3 for lead disposition. Choose Option A only if the requirement is to prevent restart-based PIN-attempt loss strongly enough to justify the availability and integration costs.

This recommendation assumes low event frequency but does not claim measured probabilities. If field evidence or the threat model shows repeated interruption is material, reconsider Option A. A decision to accept a residual belongs in the risk register, with rationale and a review trigger.

## 7. Verification and evidence

### 7.1 Local gates for every code slice

```powershell
.\gradlew.bat testProdDebugUnitTest detekt assembleProdDebug compileProdDebugAndroidTestKotlin --console=plain
```

Each slice must pass independently before review and commit. Retain the tested revision, command, host, and results. Compiling Android tests is not evidence that they passed on a device.

### 7.2 Required JVM coverage

| Scenario | Required observation |
|---|---|
| Initial read fails, later read succeeds | Recovery occurs without UI polling; stored state is applied only while eligible. |
| Local reset or failure races a recovery read | Recovered old data cannot replace local state. |
| Failed reset followed by a new failure | An old retry cannot erase the newer count or deadline. |
| Normal writes overlap a recovery snapshot | Persistent ordering and final restart state remain correct. |
| Retry also fails | Count and enforcement deadline do not advance; budget and backoff remain bounded. |
| Fallback expires before retry | No expired lockout is revived or extended. |
| Recovery succeeds | Original audit/capture events are not repeated. |
| Shutdown while a retry is delayed or running | No new retry begins after stop; no late state publication or callback escapes the lifecycle boundary. |
| Storage write remains stalled | Screen-off, dismissal, and queued runtime events still proceed. |
| Option A: death/cancellation at every protocol boundary | No verification before durable admission, no cancelled request revived, and no fabricated wrong-PIN event. |
| Option A: duplicate or superseded completion | At most one applicable state transition; no duplicate count or stale unlock. |
| Option A: migration and recovery | Existing counts/deadlines survive migration; pending records follow the reviewed recovery policy. |

### 7.3 Fleet gate before F5

Run Android-execution verification on **NucBox/Moto G**, not the current development box. Use controlled fault injection and a disposable test setup; do not corrupt the user's production credential store.

Required scenarios include transient read failure, failed reset followed by recovery, process death before and after recovery, below-threshold fallback persistence if B3 is selected, and restart/reboot deadline behavior. Inspect the persistent state after restarting the process rather than relying on the original process's in-memory preference cache.

Cover the live self-gate and legacy lock-screen activity. Exercise the future overlay/runtime path through its test harness before activation. For Option A, also measure authentication latency and verify cancellation and recovery UI on the real device.

Some faults are not safely reproducible in the platform itself. State clearly when evidence comes from an injected storage adapter versus actual platform storage failure. Keep deterministic fault tests and real persistence/restart tests as complementary evidence.

Retain a host-tagged campaign report under `docs/reports/campaigns/`. No risk changes status merely because a test command was launched.

### 7.4 F5 and F6 follow-through

The existing F5 fleet checkpoint remains required. Add checks that the newly wired graph shares the hardened manager and that consumers use the selected admission/recovery behavior. F5 remains graph wiring, not early production overlay activation.

F6 remains the activation step. The later **prodRelease FLAG_SECURE proof and on-device replacement smoke stay on NucBox/Moto G**. Neither local JVM tests nor the hardening campaign replaces those proofs.

## 8. Documentation and completion criteria

After selecting an option, update:

- `docs/process/RISK_REGISTER.md`: replace the F6 revisit with F2 hardening between F4 and F5; give each residual its own treatment, evidence, remaining limitation, and disposition.
- `docs/process/M7_PLAN.md` and the working Change F plan: insert the milestone and adjust F6 responsibilities if Option A moves caller work earlier.
- `docs/process/rtm/rtm.csv`: cite specific FR-174 re-verification evidence for the changed implementation, following repository governance.
- `changelog.txt`: summarize behavior, production scope, verification, and remaining risks concisely.
- A campaign report: retain the exact tested revision, hosts, cases, outcomes, and limitations.

Completion means the selected implementation is reviewed, its local and fleet gates pass, no stale recovery work can corrupt newer state, and the lead has recorded a disposition for each remaining residual. It does not mean all storage failures have become impossible.

The minimum recommended delivery is B1 plus B2. B3 must be either completed with evidence or explicitly left out. Option A must not begin until its recovery and availability policies are reviewed.

## 9. Source references and limits of this proposal

- [R-007 risk register](../RISK_REGISTER.md#r-007)
- [M7 plan](../M7_PLAN.md)
- [F2 JVM re-verification report](../../reports/campaigns/2026-09-21_m7-wp2-f2-lockout-jvm_2012-i7.md)
- [LockoutManager implementation](../../../app/src/main/java/com/applock/security/LockoutManager.kt)
- [EncryptedPrefs storage adapter](../../../app/src/main/java/com/applock/security/EncryptedPrefsLockoutStorage.kt)
- [Android SharedPreferences.Editor contract](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit())

The Android contract defines successful `commit()` as successful writing to persistent storage. It does not provide field probabilities for the failures discussed here. The benefit estimates in this document are qualitative design judgments, not measured incident rates or guarantees.

This document proposes work and records the requested scheduling move. It does not implement either option, execute verification, or approve residual-risk acceptance.
