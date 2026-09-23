# M7 F2 hardening: R-007 storage-recovery test plan and decision framework

**Date:** 2026-09-23

**Status:** Proposed testing plan; no implementation selected, tests executed, or residual accepted by this document.

**Placement:** After F4, before F5 graph wiring and its fleet checkpoint.

**Inspected baseline:** `19bb7079907e9755d1edc92d87d9f65b7be950f8` on `main`, synchronized with `origin/main` during preparation. Re-pin the revision before execution.

**Decision owner:** Project lead. Test operators retain host-specific evidence; implementation and risk dispositions require their own records.

**Scope:** Healthy operation and all four R-007 residuals, including the live self-gate/legacy path and the future runtime harness. This document specifies future work only.

## 1. Purpose and evidence standard

Determine which mechanisms materially improve restart-resistant enforcement without unacceptable legitimate-user denial, latency, or state-ordering regressions. Compare Option A, B1/B2/B3 individually, useful combinations, and alternative designs. The existing proposal's recommendation to start with B1+B2 is a hypothesis to evaluate, not the test oracle.

Use these evidence labels throughout execution:

- **INSPECTED:** Established by reading the pinned source or documentation. This describes code structure, not a new execution result.
- **HISTORICAL:** Reported by an existing campaign at its stated revision and host; not evidence that a proposed mechanism passes.
- **PREDICTED:** Expected behavior derived from code or a candidate protocol; must be tested.
- **MEASURED:** A completed case with commands, exact revision, raw artifacts, and a reproducible result. There are no new MEASURED results in this plan.
- **UNRESOLVED:** A product/security policy choice, unavailable platform experiment, or untested claim.

Keep two verdicts for each case: **behavior matches its specified oracle** and **residual objective met**. A baseline test that correctly reproduces a bypass passes characterization while leaving the security objective unmet. An injected fault demonstrates behavior conditional on that fault; it does not estimate field frequency. Deliberate repeated interruption must be assessed separately from accidental process death. Do not add the four residuals' probabilities together: one run can involve several.

### 1.1 Inspected sources and their limits

Paths are relative to this proposal; line numbers describe the inspected revision and can move.

| Source | INSPECTED finding and test consequence |
|---|---|
| [Hardening options](2026-09-22_R007_F2_HARDENING_OPTIONS.md), sections 2–7 | Four residuals, A/B alternatives, constraints, and fleet requirement. Its qualitative likelihood and recommendation have no field-frequency measurements. |
| [Risk register](../RISK_REGISTER.md#r-007), lines 499–540; [M7 plan](../M7_PLAN.md), lines 931–1115 | F2 manager/storage/legacy behavior is live; F4 is complete but unwired; F2 hardening precedes F5; runtime activation is F6. All four residual dispositions remain open. |
| [LockoutManager](../../../app/src/main/java/com/applock/security/LockoutManager.kt), lines 178–237, 297–369 | Construction reads synchronously. A failed seed yields empty/Available and arms a lazy reseed. `currentState()` can enqueue reseeding. First local mutation permanently disables it. Failure threshold is 5; base duration 30 s; cap 30 min. |
| Same manager, lines 297–454 | Failure admission publishes count immediately. At threshold, a monotonic fallback starts at admission. Below threshold, an unresolved write alone does not arm fallback. A failed ordinary failure-write arms/extends fallback from its completion time, even for an older revision in the same streak. |
| Same manager, lines 344–514 | Success immediately clears count and both enforcement windows and advances streak. Revision fences stale successful publication; streak fences old failed writes after reset. Writes are FIFO on one dispatcher. Shutdown rejects admissions/publication/callbacks but does not cancel accepted writes. Cancellation exceptions are rethrown, not ordinary false outcomes. |
| [EncryptedPrefsLockoutStorage](../../../app/src/main/java/com/applock/security/EncryptedPrefsLockoutStorage.kt) | One editor commits `failure_count` and `lockout_until` using `commit()`; adapter returns its Boolean. No journal, versioned attempt record, boot identity, or recovery tombstone exists. Lazy key/prefs initialization occurs inside the first read. |
| [AppModule](../../../app/src/main/java/com/applock/di/AppModule.kt), lines 90–99 | Production manager is a singleton and receives `SystemClock.elapsedRealtime`. Test graph must not accidentally introduce a second writer/manager for the same store. |
| [AuthGateViewModel](../../../app/src/main/java/com/applock/presentation/authentication/AuthGateViewModel.kt); [MainActivity](../../../app/src/main/java/com/applock/presentation/applist/MainActivity.kt), lines 193–240 | Live self-gate polls every 250 ms, checks current state again on submission, verifies synchronously, then calls the legacy engine. No shared asynchronous PIN admission protocol exists here. |
| [LockScreenActivity](../../../app/src/main/java/com/applock/presentation/authentication/LockScreenActivity.kt), lines 113–152; [ApplicationLockEngine](../../../app/src/main/java/com/applock/service/ApplicationLockEngine.kt), lines 76–117 | Legacy PIN flow checks countdown before verification. Success marks session and admits reset without awaiting disk. Failure audit is requested before admission; recorded-lockout audit and capture use the completion's own count. Biometric mismatches remain Android-managed. |
| [LockEngineRuntime](../../../app/src/main/java/com/applock/service/engine/LockEngineRuntime.kt), lines 206–289, 565–667; [reducer](../../../app/src/main/java/com/applock/service/engine/LockEngineReducer.kt), `onLockoutRecorded` | Drain admits then awaits off-drain, using `FailureToken` for completion. Suspend self-gate awaits outside its lifecycle lock; its initial failure audit precedes the wait. Shutdown is not a join of every already-running drain effect. Do not infer a stronger lifecycle guarantee from a comment elsewhere. |
| [OverlayLockPresenter](../../../app/src/main/java/com/applock/platform/lock/OverlayLockPresenter.kt), lines 416–427; [CredentialRepository](../../../app/src/main/java/com/applock/security/CredentialRepository.kt) | Future surface still verifies before forwarding result. Option A must move admission before this verification too. Existing verification clears its supplied PIN array; delayed admission creates new buffer-lifetime cases. |
| [LockoutManagerTest](../../../app/src/test/java/com/applock/security/LockoutManagerTest.kt); [LockEngineRuntimeTest](../../../app/src/test/java/com/applock/service/engine/LockEngineRuntimeTest.kt) | Existing gated fake supports false/throw/stall and ordering tests. Fake restart tests reconstruct a manager over retained fake state, not an Android process or encrypted file. No automatic bounded retry protocol currently exists. |
| [F2 JVM report](../../reports/campaigns/2026-09-21_m7-wp2-f2-lockout-jvm_2012-i7.md) | HISTORICAL: at `b9e7c53`, report states 348 JVM tests passed, including 41 manager and 59 runtime cases; builds/static gate passed. It explicitly leaves four residuals and device verification outstanding. Do not reuse those totals as current results. |
| [Governance](../GOVERNANCE.md), sections 1, 3, 5 | No RTM promotion or risk closure from this planning change. Future verified behavior, host reports, implementation, and risk decisions must be linked at their actual revisions. |

Android documents `commit()` success as successful writing to persistent storage, and competing editors can overwrite each other. Use that contract plus fresh-process inspection, not a same-process preference read, as durability evidence. An unknown completion is not confirmed success or confirmed absence. [Android editor contract](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()).

Android's elapsed clock includes deep sleep and is measured since boot. The current persisted format has only a wall deadline, so its in-process monotonic mirror is lost at restart. Reboot tests must distinguish a new boot from a new process. [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock).

Process destruction need not call `onDestroy`; cleanup and retry flushing cannot be assumed to run. Graceful shutdown, forced termination, activity recreation, and reboot are different experiments. [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).

### 1.2 Questions to settle, not assume

1. How much enforcement loss occurs before a healthy write completes, before a failed write returns, and before recovery commits? Can repeated restarts buy additional verified guesses?
2. Does recovery meaningfully shorten transient-fault exposure on both hosts, including recovery without UI polling? What remains under permanent fault or a never-returning write?
3. Can a user with correct credentials regain access after storage failure without indefinite denial, stale re-locking, or an undocumented bypass?
4. Does A's pending record constrain interrupted verification across every caller and restart, or merely move an unprotected window elsewhere?
5. Would narrower read gating, a unified durable state record, or pre-debited attempt capacity outperform A/B on the measured fault patterns?

## 2. Non-negotiable oracles and explicit design decisions

Every candidate is evaluated against I1–I9. A violation is a candidate failure unless the lead explicitly changes the requirement and records the tradeoff before a new test run. Do not silently adjust expected values to pass a candidate.

| ID | Preserved constraint | Test oracle |
|---|---|---|
| I1 | Runtime responsiveness | Storage waits never occupy the single runtime drain. Screen-off, safe dismissal, timers, and subsequent inputs progress while storage is gated. UI startup is measured separately: current synchronous construction read can itself stall. |
| I2 | One ordered writer | At most one persistent mutation executes at a time. Admission order, disk order, and recovery serialization have a documented linearization point. A stalled writer is not replaced by a competing writer. |
| I3 | Short admission critical section | No storage access or retry delay under mutation/lifecycle admission locks. Admission, state reads, reset, and shutdown are independently probed while I/O is blocked. |
| I4 | State freshness | Recovered old reads, reset retries, fallback snapshots, and late callbacks cannot replace newer applicable state. Check freshness before recovery I/O and before publication. Account for fallback changes without a new authentication revision. |
| I5 | Event accounting | Actual failed verifications retain identity, target, method, own count, and original durability outcome. Recovery creates no wrong-PIN events, captures, session grants, or extra `LOCKOUT_TRIGGERED`. Recovery diagnostics use separate event types. |
| I6 | Immediate in-memory reset | Once success is accepted, count and deadlines clear immediately and its current session is not re-locked by failed or late storage work. Making access wait for reset durability is an explicit separate product decision. |
| I7 | Honest completion/lifecycle semantics | Returned false/exception, in-flight/unknown, cancellation, shutdown, and actual process death are distinct. A timeout does not demonstrate termination of blocking I/O. Accepted writes may still reach disk after a waiter cancels or manager shuts down. |
| I8 | Deadline integrity | Recovery never restarts/extends a fallback or revives an expired window; preserve any longer applicable recorded window. The existing ordinary failure-completion extension is distinct from recovery. Changing it requires a stated decision. |
| I9 | Caller and biometric compatibility | All live/future paths consult the same enforcement/admission decision. Biometric mismatches remain Android-managed; storage recovery does not count them as PIN failures. Only the runtime drives future overlay presentation/dismissal. |

Before testing A or an admission-gating alternative, define `Ready`, `LockedOut`, `Recovering`, and `StorageUnavailable` permissions. The current `is LockedOut` checks otherwise treat newly added states as allowed. Define cancellation ownership, one shared in-flight scope, pending-record recovery, unavailable-storage UI, and deadline anchoring. These are protocol decisions, not test-harness defaults.

Explicit decisions also include: blocking authentication during an unreadable seed; consuming conservative capacity for an unevaluated/correct-but-interrupted attempt; any storage-dependent success delivery; new schema/backend; downgrade rejection; reboot/clock rollback policy; or stronger audit durability. Record which constraints change and why. A PIN evaluated before its reservation reaches disk is not Option A.

## 3. Reproducible state model and harness prerequisites

### 3.1 Common fixtures and notation

- Use JVM clocks `W0 = 1,000,000 ms` (wall) and `E0 = 5,000,000 ms` (elapsed), independently controlled. Device fixtures use current real clocks and record both. `T = 30,000 ms`, threshold `K = 5`.
- `D=(c,w)` means the **durable** count and wall deadline. `Z=(0,0)`, `C4=(4,0)`, `L5=(5,W0+T)`, `L8=(8,W0+240,000)`; create fixtures through a confirmed write and verify from a fresh process before starting the measured case.
- `M=(c,rw,rm,f,rev,streak)` describes manager memory: count, recorded wall/elapsed deadlines, fallback elapsed deadline, revision, reset streak. These private fields are conceptual oracles; a proposed test-only snapshot observer may expose them. Public `failureCount/currentState` and caller gating remain independent checks.
- `P(id,epoch,baseRevision,anchor,phase)` is a **candidate-only** durable attempt reservation. No such record exists at baseline. Candidate specifications must define exact fields, terminal records, and recovery semantics before running candidate cases.
- `Q` is the writer queue; `V` is the verifier-entry counter; `A` is actual authentication-event/capture ledger. Record separate recovery-diagnostic entries `R`. Do not conflate submission, admitted mutation, PIN verification, outcome, and durable commit.
- Restart creates a new manager/process from `D` only. A JVM crash simulation must discard old jobs/queues using a simulated process generation, so the dead manager cannot subsequently write into the survivor's store. Calling `shutdown()` and releasing all gates is **not** a crash simulation.

For ordinary unlocked gates, verify PINs through the caller. Direct manager submissions are permitted to isolate ordering or construct otherwise unreachable overlaps, but label them manager-only. In particular, do not claim a correct PIN was accepted through an already-locked UI to manufacture /4.

### 3.2 Planned harness work (not implemented here)

| Harness addition | Purpose and acceptance before candidate testing |
|---|---|
| Extend the existing gated fake | Separate volatile preference cache from durable state; record read/write begin, payload, completion, revision/streak, thread, and concurrency. Script failures by operation ID, not timing-dependent global flags. |
| Controlled I/O outcomes | `read throws`; read stalls; write false/throws before durable change; write holds before commit; durable change then hold before return; real commit succeeded then wrapper reports failure (ambiguous acknowledgement). The last is an injected protocol ambiguity, not evidence Android normally returns false after successful commit. |
| Deterministic scheduler and clocks | Independent virtual timers for backoff/deadlines plus a real single-thread executor with latches for blocking behavior. Backoff advances off-writer. Include seeded interleavings; retain seed and exact operation schedule. No correctness assertion depends on arbitrary sleep. |
| Lifecycle/caller probes | Barriers before/after verification, admission, writer enqueue/start, commit return, completion publication, audit/capture, session grant, and dismissal. Track request/attempt IDs, process generation, and buffer disposal without logging PIN data. |
| Independent reference oracle | Small transition table/specification that calculates allowed `M/D/A/V` states from the controlled schedule. Do not copy production branches into assertions. Check invariants after every step, not just final convergence. |
| Debug-only Android fault adapter | Decorates the real encrypted store; operation-indexed scripts install before first manager construction. Fault control survives a deliberate restart when requested, separately from lockout records. Default off, isolated test data, unavailable in release. Ensure scripts cannot accidentally reset on each restart. |
| Fresh-process durable inspector | Runs before normal graph startup can reseed/retry/write; opens the real encrypted adapter with the app's test identity/keys and outputs only count/deadline/schema/pending metadata. It exits before launching the normal manager. Read-only inspector must not become another writer. |
| External restart controller | Host-side adb/UIAutomator helper app observes barrier acknowledgements and PID/boot identity, kills target, restarts, and inspects storage. Controller must outlive the target; target-process instrumentation cannot alone verify its own death. |
| Evidence/latency trace | Correlate host commands with on-device monotonic timestamps, local event ledgers, UI evidence, and parsed persisted state. Keep diagnostic overhead measurable; no PIN, hash, keys, raw credential store, or unnecessary package identifiers in retained reports. |

Do not use a new `EncryptedPrefsLockoutStorage` object in the same process as proof of disk durability: preferences can still be cached. Raw encrypted XML hashes are supporting evidence only; they do not prove which logical state is present. Do not treat a read-after-failed-commit as an acknowledgement.

### 3.3 Evidence bundle required for every case

**E0** = case/variant ID; baseline/candidate configuration; Git SHA and dirty diff if any; APK and harness hashes; toolchain; host/device model, API, ABI, image/build; seed; initial fixture; exact fault script; barrier timeline with both clocks; expected and observed `M/D/Q/V/A/R`; pre/post PID and boot identity; assertion verdicts; command/output exit status; limitations. Record all failures and reruns, not only the last green result.

**E1** = fresh-process logical store dumps before action and after restart, plus commit acknowledgements and operation payloads. **E2** = redacted event/capture/session/verifier ledger. **E3** = UI/video or screenshots, logcat, relevant thread/Perfetto traces and responsiveness timings. **E4** = reboot/time/sleep records and deadline calculations. These codes below always include E0.

## 4. Healthy-storage controls

Run controls first on the unmodified baseline, then on every candidate with fault injection disabled. Controls must share the same test build and observability hooks as fault runs. Repeat key latency controls with hooks disabled to quantify instrumentation overhead.

| ID | Setup, action sequence, injection | Expected memory and persisted state | Observable/pass criterion; evidence |
|---|---|---|---|
| H01 | Fresh verified `Z`; cold launch; no fault; read state repeatedly without authenticating. | `M.c=0`, Available; `D=Z`; no writes/events caused by polling. | Self-gate/legacy permit ordinary authentication; no recovery loop or unnecessary new healthy-path write under B. A's admission write occurs only on submission. E1/E2/E3. |
| H02 | `Z`; submit four actual wrong PINs, await each write; submit fifth. | Counts 1–4 remain Available and `D=(c,0)`. Fifth immediately enforces fallback until commit; after current commit `D=L5`, recorded coverage active. | Gate rejects further input without calling verifier or increasing count while locked. Five real failure identities and own counts; only recorded threshold outcome produces lockout audit. E1/E2. |
| H03 | `L5`; advance both clocks to `T-1`, `T`, `T+1`; after expiry submit another wrong PIN. Continue expiry/failure cycles to cap. | Locked before expiry, Available at expiry; count retained at 5; sixth produces 60 s, then doubling to 30 min. | No off-by-one permission, count reset on expiry, overflow, or new event on a countdown tick. Device tolerance bounded by measured scheduling delay, not extended deadline. E1/E2/E4. |
| H04 | `D=(4,0)`; accept correct PIN, then repeat using valid biometric success on its supported live surface. | Reset publishes `M=Z` at admission; `D=Z` after commit; current accepted session remains usable. | Immediate reset and ordinary access; one success, zero failure/capture; restart retains clear state. Biometric mismatch/cancel leaves counts unchanged. E1/E2/E3. |
| H05 | Persist counts 1/4 and active/expired deadlines; terminate and relaunch, then repeat with reboot. No pending writes at termination. | New memory seeded from the exact last committed pair; ordinary wall-deadline countdown reflects time actually elapsed. | New PID for restart, new boot identity for reboot; active state enforced by both callers, expired state not revived. E1/E3/E4. |
| H06 | Below-threshold/threshold/reset operations with ordinary slow successful writes at 0/10/100/1000 ms; no false/throw. During each, send screen-off and dismissal/navigation events. | Admission ordering and immediate reset unchanged; last disk state matches final admitted ordinary operation once drained. A may wait off-drain before verification. | All control events handled before gate release; measure queue delay, UI latency and pending-state presentation separately. No storage wait on drain/admission lock. E1/E2/E3. |

Manager math tests may submit failures during an active lockout to verify duration computation. Caller tests must instead prove those submissions never reach verification/accounting. A candidate's admission reservation is not an extra actual failure in H02.

## 5. Residual R-007/1: cold-start read failure

**Objective:** Restore trusted state without overwriting newer authentication activity, and measure whether any caller can verify while historical enforcement is unknown. Test exceptions separately from stalled construction/recovery reads.

### R1.1 — transient read failure, with and without polling

- **Setup:** Confirm `D=L5`; start at W0/E0. Fail exactly the construction read. Keep writes healthy. Run once with no `currentState()` calls after construction until a fixed inspection checkpoint, once with normal 250 ms UI polling, and once with 100 concurrent/repeated polls.
- **Injection/action:** Make storage readable at `t=1 s`; inspect at `t=2 s` and just before expiry. For B1 use a declared test schedule, for example delays 100/500/2000 ms after successive failures, three retries total; do not silently treat these as final production values. Record actual retry times.
- **PREDICTED baseline:** Initial `M=Z/Available`, `seedReadFailed=true`, `D=L5`. No polling means no autonomous retry. With polling, eligible successful reseed loads `c=5,rw=W0+T,rm=f=0`; the historical seed-failure flag remains true.
- **Candidate oracle:** B1 recovers on its schedule without polling and permits at most one eligible read. A/read-gated variants deny verifier entry while trust is unknown and load the record before admission. Neither invents an authentication failure. `D` remains `L5` throughout.
- **Observable/pass:** Record the exact interval of Available/Recovering, verifier permission, retry count and remaining deadline. B1 must pass autonomous recovery and backoff assertions but receives only a **mitigation** verdict for the initial fail-open window. A's denial must be responsive and visibly distinct from an incorrect PIN. **Evidence:** E1/E2/E3.

### R1.2 — persistent read failure and late recovery after exhaustion

- **Setup:** Repeat R1.1 with `L5`, `L8`, and `Z`; every initial/retry read throws. Test at least a full configured recovery budget, repeated UI recreation/polling, screen-off/on, and elapsed expiry. Then restore reads without local mutation.
- **Injection/action:** Poll at 250 ms and in a burst; try wrong and correct PIN submissions while the seed remains unknown; then relaunch under the same persistent fault. Keep a read-only branch separate from a mutation branch.
- **PREDICTED baseline:** Available while unreadable; polling can keep triggering lazy reads. B1 alone remains Available and stops at budget exhaustion; it cannot claim convergence after exhaustion unless a specified explicit re-arm exists. A/read gate remains unavailable until trusted recovery or an explicitly approved alternative.
- **Memory/disk:** Before a permitted local mutation, `M=Z`, `D` unchanged. If a candidate permits mutation, record its actual new count/reset and possible overwrite of unknown historical state; do not assume the history remains recoverable.
- **Observable/pass:** Recovery work bounded independently of poll count; correct-PIN delay and re-arm behavior meet the predeclared availability policy. No timeout quietly converts an A denial into Ready. Repeated restarts must not create an unrecorded fresh-attempt allowance. **Evidence:** E1/E2/E3 plus retry-budget timeline.

### R1.3 — old recovery read races local failure or successful reset

- **Setup:** `D=L5`; fail seed. Start a recovery read, capture its old `L5` result, and hold it immediately before publication. Repeat with the read queued but not started. Use manager-level submissions to isolate the race.
- **Injection/action:** (a) Admit one failure; (b) in a separate run, admit reset. Hold/fail the resulting write so `D` stays `L5`; release old read; inspect; then allow latest ordinary write to commit and restart. Also let the local mutation happen just before recovery read starts.
- **Memory/disk:** Baseline local failure gives `M.c=1` (unknown history was not loaded), then fallback after failed write; reset gives `M=Z`. Both disable reseed. Old `L5` must never replace either. `D` remains old until applicable new writes commit; a committed local count can erase unknown history.
- **Observable/pass:** Zero stale publication; B1 checks eligibility before I/O and again at apply. A must prohibit the untrusted local verification in the first place. Do not credit B1/B2 with reconstructing lost historical counts. If a new merge design is proposed, include a reset generation and test its rules; blindly taking maximum count/deadline would violate reset. **Evidence:** E1/E2, ordered read/admission/publication trace.

### R1.4 — initial read or reseed never returns

- **Setup:** `L5`; hold initial read before return in one run, and hold a reseed after a failed initial read in another. These are separate fault points.
- **Action:** Attempt UI startup, lifecycle cancellation, screen-off, shutdown and later authentication admissions where construction completed. Release at 40 s with success, throw, or cancellation in separate runs.
- **Memory/disk:** During constructor stall no constructed manager is available; existing I1 writer tests do not establish startup responsiveness. During stalled reseed, baseline memory remains empty and the shared I/O dispatcher cannot service queued writes. `D` unchanged until real writes run.
- **Observable/pass:** No candidate masks an unresolved read as trusted empty state. Measure startup ANR/jank and writer starvation; a newly asynchronous initialization state is an explicit design change. Cancellation must release eligibility bookkeeping or stop the lifecycle as specified, not strand a permanent `reseedInFlight` flag. Any baseline defect reproduced is recorded, not predeclared fixed. **Evidence:** E1/E3 with thread stacks.

## 6. Residual R-007/2: in-memory degraded enforcement lost on restart

**Objective:** Distinguish a successfully persisted count from a durable enforcement deadline. Quantify the window before a degraded interval becomes recoverable.

### R2.1 — below-threshold failure followed by death

- **Setup:** `D=Z`; clocks at W0/E0. Verify one wrong PIN and make its write return false before disk change. Repeat with a contained exception.
- **Action:** Wait for failed-write completion; record `M.c=1,f=Efail+T`, degraded. At `Efail+5 s`, terminate and relaunch with reads healthy. Repeat with process death immediately before and immediately after a candidate recovery commit. Reboot variants use L8-sized fixture windows only where the candidate policy actually allows them; otherwise record whether the 30 s interval expired during reboot.
- **Memory/disk:** Baseline before death is degraded, `D=Z`; after restart `M=Z/Available`. B2 count-only recovery can yield `D=(1,0)` and still loses enforcement. B3 at recovery time `tr` should save `c=1,w=Wr+(f-Er)` if positive, retaining original expiry under stable clocks. After confirmed recovery and restart, remaining enforcement is original remainder, not a new T.
- **Observable/pass:** Both live gates block before death. B3 claims mitigation only after its actual commit; false/throw/no-return leaves the gap. A must recover its durable reservation under its reviewed uncertainty policy; it cannot recreate an exact lost result. **Evidence:** E1/E2/E3/E4 and death-to-commit timing.

### R2.2 — healthy count commit does not persist fallback

- **Setup/action:** `D=Z`; hold failure write F1, admit F2 at manager level before F1 returns; F1 returns false, F2 commits `(2,0)`. Inspect memory and restart inside the fallback window.
- **Memory/disk:** Baseline `M.c=2` remains degraded from F1; `D=(2,0)`; restart retains count 2 but no lockout. F2's own outcome is Available while global enforcement remains degraded. These are not contradictory.
- **Candidate/pass:** B3 must preserve the applicable fallback despite a later count-only success. B2 alone does not resolve this. Recovery may change current durability metadata but must not rewrite either original failure outcome or issue another lockout audit/capture. **Evidence:** E1/E2/E4.

### R2.3 — delayed threshold commit is older than the active fallback

- **Setup/action:** Seed `(3,0)`. Admit F4, hold and later fail it; admit F5 behind it with threshold deadline `W0+T`. Advance both clocks by 40 s; release F4 false, then F5 true. This matches the intent of existing manager test `a delayed threshold write whose deadline elapsed is not mislabeled as durable`.
- **Memory/disk:** `M.c=5,f=E0+70 s` after F4's delayed failure; F5 persisted deadline `W0+30 s` is expired. Global state remains degraded despite F5's own recorded threshold outcome. Restart without B3 is Available with count 5.
- **Candidate/pass:** B3 persists only the remaining applicable interval, with a freshness token that sees deadline changes even when admission revision did not change. No recovery extension or duplicate event; retain original F4/F5 results. **Evidence:** E1/E2/E4.

### R2.4 — persistent faults, expiry, and repeated interruption

- **Setup/action:** Repeat R2.1 with every recovery write false/throw; then with the first recovery write stalled. Test death before budget exhaustion, after exhaustion, and at `f-1`, `f`, `f+1`. Run 20 deliberate restart/guess cycles with the fault script preserved; use JVM virtual time and real device wall time as separate evidence.
- **Memory/disk:** Failed retries must keep `c=1` and the original `f`, never `now+T`; count-only retries cannot make it durable. After expiry skip deadline persistence, though count reconciliation may still be needed. Baseline/B retain only last durable state on death.
- **Observable/pass:** B's residual remains open when no recovery reaches disk; measure extra actual verifications per cycle. A must not clear pending state on restart or restart a full penalty every boot. Persistent unavailability is recorded as legitimate-user denial, not a security pass without cost. **Evidence:** E1/E2/E4, retry and restart-cycle ledger.

## 7. Residual R-007/3: death before admitted mutations become durable

**Objective:** Exercise healthy slow storage as well as faults. A fast successful retry cannot erase the existence of the pre-commit crash window.

### R3.1 — healthy write held before commit, below and at threshold

- **Setup:** Run with `D=Z` and `D=C4`. Real verification reports wrong PIN. Hold write before any durable change, with no eventual failure configured.
- **Action:** Observe admitted state, then kill before releasing write. Restart from old durable state. Repeat death (a) after verifier outcome but before mutation admission, (b) after enqueue but before writer starts, (c) while commit is in progress, (d) after real commit but before return/publication, (e) after return but before audit/capture.
- **Memory/disk:** At baseline admission, Z branch has `M.c=1/Available`; C4 branch has `M.c=5` and degraded threshold lockout. Before commit `D` is old; after confirmed commit it is the new pair. Mid-platform-commit is **unknown** until fresh inspection; accept only complete old/new snapshots under the tested contract, never fabricate knowledge of which won.
- **Observable/pass:** Quantify lost actual verified failures and new guesses enabled after restart. For baseline/B, reproduce and disposition the window. A must have durable `P` before verifier entry even for boundary (a), then recover uncertainty before allowing another attempt. Death after durable write but before notification must not lose or double-account state. **Evidence:** E1/E2/E3.

### R3.2 — multiple queued mutations and reset loss

- **Setup/action:** `D=(2,0)`; at manager level hold F3, admit F4, reset S, then new-streak F1. Memory ends at count 1. Kill at each queue boundary in independent runs. Also exercise reachable concurrent live self-gate/legacy submissions and document which overlap can actually occur.
- **Memory/disk:** Allowed committed prefixes are initial `(2,0)`, `(3,0)`, `(4,0)`, `Z`, then `(1,0)`; the final new-streak memory cannot be inferred after death unless persisted. For threshold variants include each admission's own deadline. Accepted writes are not coalesced merely because recovery is coalesced.
- **Observable/pass:** Disk order exactly follows the serialized protocol. Count/capture for each original operation remains its own, including completions after a newer reset. No late old callback unlocks a new screen. A needs shared admission ownership across callers; a guard on one Compose screen alone fails this test. **Evidence:** E1/E2, complete queue trace.

### R3.3 — stalled write, continued admission, and shutdown

- **Setup/action:** Hold the first below-threshold write indefinitely. Submit additional attempts at caller-possible rates; separately inject five manager failures to reach immediate enforcement. Send screen-off, safe dismiss, timer events and runtime shutdown. In another run accept success and hold its reset while dismissal proceeds. Release storage with true/false after 40 s, or kill without release.
- **Memory/disk:** Unresolved below-threshold write is not a returned failure; no completion-based fallback yet. Threshold fallback can expire while storage is still stalled. A later ordinary false outcome can arm a fresh fallback from completion. Reset memory stays clear despite old-streak failures. Durable state remains old until the blocked operation resolves.
- **Observable/pass:** Drain liveness, admission latency, bounded recovery work and correct shutdown semantics hold. Measure ordinary queue/memory growth separately: bounding retries does not bound legitimate or duplicate admissions. Any new admission backpressure, fail-closed rule during a pending write, or changed completion-time fallback is an explicit decision. No replacement writer or timeout-based assertion that commit stopped. **Evidence:** E1/E2/E3/E4.

### R3.4 — commit completed but acknowledgement was lost

- **Setup/action:** Commit a failure/reset/reservation through the real adapter, hold wrapper return, then kill. Separately return false/throw after that confirmed real success using the ambiguity fixture. For candidates retry/recover the same logical operation, including duplicate callbacks and repeat recovery after another crash.
- **Memory/disk:** Durable record is new despite the caller lacking confirmation. New process must interpret that record once. A pending reservation may already exist even though no verifier started; a reset may already be clear despite an earlier failure report.
- **Observable/pass:** No double count, renewed deadline, duplicate grant/capture, or stale overwrite. Stable IDs/generations must survive the relevant process boundary if used for idempotence. Report this as injected acknowledgement ambiguity, not a natural-platform incident rate. **Evidence:** E1/E2, real commit and wrapper barrier trace.

## 8. Residual R-007/4: failed reset restores stale enforcement on restart

**Objective:** Preserve immediate current-session reset and measure the separate durability window, including reset retries that would erase newer failures.

### R4.1 — failed clear and stale count/deadline on restart

- **Setup:** Manager-isolation fixture `D=L5`, memory loaded from it; call accepted-success/reset directly and fail reset before disk mutation. Repeat false and throw. For a reachable caller case, use `D=(4,0)` and a correct PIN; for an active stale deadline use an explicitly controlled valid success already in flight before a competing caller establishes a lockout. If such a live overlap is unreachable, label the active-deadline case manager-only.
- **Action:** Assert reset publication, session/access behavior and failure diagnostic. Kill at 5 s and inspect before normal graph startup; relaunch while stale deadline is relevant. Repeat after old deadline expiry.
- **Memory/disk:** Before death, `M=Z/Available`, new streak, `D` old. Restart loads old active deadline (over-enforcement), or old count after expiry (earlier next lockout). No in-process reseed may undo the reset.
- **Observable/pass:** Baseline reproduces stale enforcement; candidates must keep current session open. A alone cannot know that the success/reset occurred if no durable evidence exists. Do not describe the recovery path as simply “unlock again” while an active deadline prevents PIN entry; measure time until that is possible. **Evidence:** E1/E2/E3/E4.

### R4.2 — transient reset failure, retry success, death on both sides

- **Setup/action:** R4.1; first reset fails; no newer auth activity. Make storage healthy at 1 s. In separate runs kill just before retry commit and after confirmed commit, also after commit but before callback. Restart without data clearing.
- **Memory/disk:** `M` remains reset throughout. B2 applicable retry changes `D` from old to `Z`; before success restart can reload old state, after success it must load `Z`. Recovery emits diagnostics only.
- **Observable/pass:** Autonomous bounded retry succeeds at its scheduled opportunity, without re-locking, delay in current access, or duplicate success/capture. Measure accepted-success-to-durable-clear interval; claim mitigation only after commit. **Evidence:** E1/E2/E3.

### R4.3 — stale reset retry versus new failures

- **Setup/action:** Fail reset S; schedule delayed retry RS. Before RS starts, admit/commit new-streak F1. Release RS, then restart. Repeat with F1 queued but not committed, RS constructed before F1 but enqueued after it, and RS already doing I/O when F1 is admitted. Add a second success S2 and later F1 to test generation reuse.
- **Memory/disk:** Current count/deadline follows the newest streak. If RS is stale **before I/O**, skip/reconcile applicable current state through the ordered writer; it must not erase durable F1. If RS already started while applicable, F1 must be ordered behind it, and after both commit `D=F1`.
- **Observable/pass:** Freshness only at completion is insufficient: inspect disk at each commit, not just memory. A death between an already-running valid RS and queued F1 is /3, not proof RS can be cancelled. No stale reset appended behind newer ordinary writes. **Evidence:** E1/E2 and exact enqueue/start/commit order.

### R4.4 — persistent clear failure and repeated restart

- **Setup/action:** R4.1 with all retries false/throw, then with clear stalled. Let old deadline expire, restart again, accept success when gate permits, repeat reset failure. Exercise 20 restart cycles with no automatic fault reset and inspect old counts.
- **Memory/disk:** Current accepted reset stays cleared; persistent disk may retain old count/deadline. Expired deadline must not be renewed merely by recovery/restart. A conservative pending-attempt policy may add availability loss but is not proof the old reset survived.
- **Observable/pass:** Retry work stops at declared bounds; UI explains unavailable/recovery state accurately; measure maximum legitimate-user denial and repeated stale-count penalties. A tombstone helps only if durably committed and ordered before death. Under immediate success with zero durable evidence, record /4 unresolved rather than claiming elimination. **Evidence:** E1/E2/E3/E4.

## 9. Cross-cutting ordering, accounting, and lifecycle cases

Each row inherits E0, controlled clocks, explicit old/new `M/D`, and the relevant residual fixture. Run all on the selected mechanism combinations, not only isolated B slices.

| ID | Reproducible schedule / injection | Expected state, observable result, and pass/fail |
|---|---|---|
| X01 | Failed F1 schedules recovery RF1; admit F2 and S before RF1 executes. Reverse the enqueue/validation order using barriers. | RF1 cannot persist a snapshot behind a newer reset or overwrite F2. If a recovery snapshot is built at execution time, prove it cannot jump ahead of older queued ordinary writes that later overwrite it. E1/E2. |
| X02 | Keep auth revision fixed; let an older same-streak ordinary failure complete false and extend `f` while a B3 snapshot is queued. | Recovery sees the new fallback generation/deadline or safely reconciles later; an old success cannot mark a longer live fallback durable. Ordinary failure may extend under existing rules; retries may not. E1/E4. |
| X03 | Queue B3 at `f-1`; start/commit at `f`, `f+1`, and after long delay. Repeat with a longer recorded deadline still active. | Expired fallback not revived; a valid longer recorded deadline not shortened. If a write started before expiry but finishes after, its absolute deadline stays expired. Never recompute a full T on execution/completion. E1/E4. |
| X04 | Fail retries repeatedly; advance independent wall clock ±1 h while elapsed clock advances normally; then restart and reboot. | In-process longer-window rule remains; stable-clock retries preserve original expiry. Wall conversion cannot silently reset a duration or shorten longer recorded protection. Restart wall-clock weakness is measured separately; a new clock policy needs approval. E1/E4. |
| X05 | Queue many recovery requests from failed writes plus 1000 state polls; exhaust budget; recreate UI 10 times; restore storage. | One bounded recovery chain/read-in-flight policy; no budget reset per poll or screen creation; no delay on writer during backoff. Record ordinary queue growth separately. Define explicit re-arm after exhaustion; no assumed eventual retry. E1/E3. |
| X06 | Manager shutdown while recovery delayed, queued, running, and completing; release each gate afterward. Try post-stop submissions. | No new retry I/O begins after stop; already-running disk may complete. Accepted ordinary writes drain under current contract; no late manager publication/callback. Post-stop no mutation (its synthetic completed return is not a real authentication grant). E1/E2/E3. |
| X07 | Runtime shutdown/cancel its work scope while shared manager writes; keep a legacy caller alive. | Manager remains usable by its other owner; runtime rejects later inputs and self-gate mutation. Already-running drain effects retain documented cooperative semantics. No stale surface/session action on completion. E1/E2/E3. |
| X08 | Cancel caller waiting on `Pending.resolved`; separately cancel the Deferred itself, throw `CancellationException` from adapter, and cancel recovery scheduler. | Test these as distinct ownership cases; no assumption that disk stopped. Define disposition of an accepted unresolved write, callback suppression and reseed/retry bookkeeping. Required auth/capture events are neither fabricated nor doubled. Any existing missing-outcome behavior is a finding. E1/E2. |
| X09 | Duplicate one request/result ID 2–10 times; double-tap PIN submit; rotate/recreate, background, replace target, then deliver old success. Also submit two genuinely distinct wrong PINs. | A accepts at most one reservation/verifier invocation per request; stale success grants no session. Recovery coalesces but distinct accepted failures keep their own accounting. Baseline manager has no request dedup API: report caller guarantees and gaps instead of demanding manager magic. E1/E2/E3. |
| X10 | Overlap live self-gate, legacy activity, and future runtime harness; wrong PIN on one, success on another; deliver completions in all reachable orders. | Same manager/store, correct reset streak, exact target/method/count per original outcome; stale result cannot re-lock reset or unlock different target. A's in-flight guard must cover the shared admission domain. E1/E2. |
| X11 | Biometric mismatch, user cancel, OS lockout, delayed biometric success after PIN reset/target change, and startup storage unavailability before prompt launch. | No PIN-failure count for mismatches; eligible success resets once; stale host lease/request does not grant access. Define whether unavailable storage gates biometrics before launch; do not silently give biometrics PIN reservation semantics. E2/E3. |
| X12 | Recovery succeeds after an original degraded outcome was already delivered; duplicate completion event; let current enforcement expire before outcome delivery. | Original outcome/count immutable; global state read from current manager, not old outcome duration. At most one lockout audit/capture dispatch for each original eligible event; recovery is separate diagnostics. E1/E2. |
| X13 | Throw/slow audit, capture, diagnostics while persistence returns; hold a callback at manager completion. | No new recovery dependence on observers. Measure callback/lock occupancy: baseline callbacks run under manager lock, so a slow callback can block admission without any disk lock. If reproduced, record separately and evaluate scoped remediation. E2/E3. |
| X14 | Seed missing store, valid zero, invalid negative/extreme count, malformed deadline/schema, or key/prefs initialization exception in a disposable fixture. | Missing fresh state and unreadable/corrupt prior state are not conflated in a new protocol. No crash, wraparound-based bypass, or silent migration-to-zero. Baseline behavior is characterized before candidate rules are chosen. E1/E3. |
| X15 | Recovery reports success; kill before publication; relaunch twice; replay same pending/terminal record. | Fresh disk state governs; no duplicate penalty, reset, capture, or extended deadline. New process IDs cannot accidentally match stale in-memory request ownership. E1/E2/E4. |

Event assertions are about dispatch/accounting within the tested lifecycle. The legacy audit DAO is asynchronous; a process kill may lose an audit write even when lockout state is durable. Do not claim exactly-once durable auditing across crashes without an explicit outbox/transaction design and its own tests. Retain dispatch counts separately from persisted audit rows and actual captures.

## 10. Candidate experiments and comparative evaluation

### 10.1 Option A: protocol boundary matrix

Before implementing any A experiment, write its transition table, persistence format, authoritative mutation owner, and recovery rule. A pending attempt is evidence of reservation, not proof of a wrong PIN. Conservative recovery consumes a specified security budget or denies admission; it must not fabricate `UNLOCK_FAILURE` or intruder capture.

Run wrong, correct, and not-yet-evaluated PIN variants at every cut below, on each caller. Repeat each recovery interruption and duplicate submission. Disk writes remain on the same ordered writer; waits occur outside the runtime drain and admission lock.

| Cut / injected fault | Required `M/D/V` and recovery oracle |
|---|---|
| Before trusted read / read fails | No verifier call while state is untrusted. UI can cancel/navigate safely. No empty-state fallback presented as trusted. |
| After admission request, before reservation begins or commits | `V=0`; `D` old, unless commit status is unknown. No new PIN evaluation merely because a timeout fires. Cancellation disposes credential buffer and invalidates request. |
| Reservation committed, before return or before verifier begins | `D` contains P; `V=0`. Restart treats interrupted reservation consistently, even if PIN was correct or never evaluated. Cancelled late completion cannot start verification. |
| Verifier starts/finishes, before terminal outcome admission | `V=1`, `D=P`. Restart must not offer an unlimited free guess for each abandoned P. Exact outcome is unknowable; conservative policy is explicit and bounded/anchored. |
| Failure outcome admitted, terminal write false/stalls | In-memory failure enforces; durable pending or terminal record prevents restart erasing admission. Recovery is idempotent even if old/new terminal record is observed. No terminal-clear-before-outcome hole. |
| Correct outcome accepted, reset/terminal clear false/stalls | Immediate `M=Z` and current accepted access survive. `D` may still contain P/old enforcement; /4 and lost-reset part of /3 remain. Measure false denial on restart; no claim of exact success recovery. |
| Terminal record committed, notification lost | Recovery uses terminal state once. Duplicate callback cannot increment count, grant stale session, or recreate P. |
| Recovering P, death before/after persisting recovery disposition | Repeated boot cannot indefinitely extend countdown or consume the same reservation repeatedly. If recovery cannot commit, prohibit new admission under A's policy rather than forgetting uncertainty. |

Tests must decide how an interrupted reservation is anchored in time. Anchoring a penalty only to reservation time may let a long stall expire the penalty before a late verification; anchoring anew on every reboot can indefinitely deny a legitimate user. Exercise long stalls before verifier entry, expiry during verification, and 20 repeated restarts. Define whether an expired reservation permits verification, requires a new durable reservation, or is rejected. Recovery scheduling alone cannot settle that policy.

Migration fixtures: legacy Z/C4/active/expired deadlines; below-threshold degraded recovery record if B3 preceded A; complete and partial new schema; unknown future version; corrupt record; duplicate IDs; process death at every migration write; downgrade to the previous app on a disposable store. Preserve old effective counts/deadlines until migration commits. A format that old code ignores can bypass pending reservations on downgrade; either demonstrate compatible enforcement or explicitly reject/support-limit downgrade. A corrupt migration must not silently clear state. Retain E1/E2 and schema bytes excluding credentials.

### 10.2 Mechanism and combination matrix

All benefits below are **PREDICTED**, conditional on satisfying sections 2–9. “Resolve” is reserved for a stated fault/threat scope; none makes permanently unavailable storage reliable.

| Candidate | Security and residual reach | Legitimate-user availability / healthy-path latency | Complexity and migration risk |
|---|---|---|---|
| Baseline control | All four residuals remain; important reference for actual exposure windows. | Existing immediate verification/reset; below-threshold pending writes remain permissive. | No new complexity. Existing reboot wall-clock and event durability limits remain. |
| B1 only | Mitigates /1 if read succeeds before local mutation. Does not close initial unknown-state gap or /2–/4. | No mandatory per-PIN write; recovery traffic/startup contention may still cost time. Persistent fault keeps current fail-open policy. | Low–medium: eligibility, polling coordination, cancellation, backoff. No necessary schema change. |
| B2 only | Mitigates /4 and shortens some count-loss windows after false/throw. Cannot repair an in-flight write or historical count lost after unreadable seed; /3 remains. | Immediate reset retained; ordinary healthy path need not wait. Fair scheduling must prevent recovery starving normal writes. | Medium: stale reset, freshness-before-I/O, FIFO and lifecycle. Usually no schema change. |
| B3 with ordered recovery | Mitigates /2 only once fallback recovery commits; /3 remains before that point. Requires B2-like scheduling/serialization even if delivered separately. | No mandatory pre-verification write; adds recovery I/O and can restore an intended lockout on restart. | Medium–high: exact expiry conversion, full-state freshness, longer recorded window, reboot semantics. Existing two fields may suffice, but semantic migration still needs tests. |
| B1+B2 | Covers transient seed/reset opportunities; does not persist below-threshold fallback or prevent pre-commit death. | Potentially low healthy-path cost; justified only if measured recovery benefit and accepted security remainder warrant it. | Shared scheduler reduces duplicate work but introduces read/write starvation interactions. Test combined, not infer from isolated passes. |
| B1+B2+B3 | Broad transient mitigation of /1,/2,/4; /3 and all pre-recovery crash windows persist. | Similar admission policy; additional disk traffic and stale-enforcement risk need measurement. | More deadline/freshness complexity; can be poor value if recovery rarely precedes restart. |
| A alone | Can prevent unrecorded PIN evaluation and address /1, failure side of /3, and /2's restart-bypass mechanism through durable admission/conservative recovery. Depends on complete caller coverage and trusted startup. Cannot reconstruct exact interrupted result. | Mandatory pre-verification write; permanent/stalled storage can deny correct credentials. Immediate reset still leaves /4. | High: protocol, UI states, shared in-flight guard, ownership, buffers, schema/migration/downgrade. |
| A + B1/B2 | A supplies admission policy; retries restore reads, reservations/outcomes, and resets after transient faults. No weakening to Ready on timeout. | May shorten unavailability but cannot fix a blocked writer. Healthy admission write remains. | High; unify recovery scheduler and operation IDs, avoid two independent state machines. |
| A + B3 (optionally B1/B2) | May retain an exact degraded remaining interval where a generic pending policy would over/under-enforce. Added value is uncertain; /4 still remains before reset durability. | Could reduce conservative recovery denial or increase it if stale deadlines win. Measure against A with same recovery budget. | Highest combined state complexity unless one versioned record unifies reservations, deadlines, and reset generations. Demand incremental evidence of value. |

B1+B3 without B2 can be tested if deliberately scoped, but B3 still needs an ordered recovery writer and reset invalidation. A+B1 without reset retries and A+B2 without autonomous read retries are ablations: run them to identify which mechanism caused improvement. Do not assume A and B are mutually exclusive, or that all B slices are mandatory.

### 10.3 Plausible alternatives to investigate if results justify them

| Design hypothesis | Distinct experiment and decision value | Limit / explicit decision |
|---|---|---|
| **C1: trusted-read admission gate + B2/B3** | Make unknown seed deny admission until successful read, without a per-attempt reservation. R1 persistent/transient and live caller tests establish whether most required benefit can be achieved at startup only. Compare warm PIN latency with A. | Addresses /1 by policy, transient /2,/4 through recovery; healthy pre-commit /3 persists. Availability policy and every `is LockedOut` caller change explicitly. |
| **C2: durable pre-debit of attempt capacity** | Before verification, durably consume an attempt token/budget slot; reconcile success/failure later. Test interrupted unevaluated/correct attempts, budget exhaustion, duplicate consumption, restart loops, and reset loss. Compare one-record protocol complexity/latency with A's reservation plus terminal outcome. | Functionally an alternative form of durable admission, not a free durability guarantee. May impose conservative penalties on innocent attempts; those are not wrong-PIN audit events. Success before durable restoration still has /4-like availability loss. |
| **C3: generation-tagged journal/unified transactional record** | Persist count, deadline, pending attempt, and reset generation together, or append operations and compact under one writer. Inject death at append/commit/compaction/migration boundaries, duplicate replay and corrupt tail. Determine whether atomic recovery simplifies X01–X03/R3.4. | A new backend alone does not close evaluate-before-write. Same disk/keystore can fail; no independent “reliable” store assumption. Async journal only narrows /2–/4 until commit. Durable pre-verification journal becomes an A/C2 variant. Significant migration/downgrade review. |
| **C4: gate later submissions while prior outcome is undurable** | Keep immediate memory/reset but stop additional verification until earlier outcome reaches durability or a stated fault policy. Test stalled below-threshold writes and cross-caller bursts; measure maximum lost verified attempts per restart. | Can bound multiple queued losses, not eliminate the first lost guess or repeated first guesses across restarts. This changes admission/availability, and timeout fail-open reintroduces the bound's escape. |
| **C5: durable reset generation/tombstone in unified state** | On accepted success, immediately clear memory and queue generation marker; restart ignores older snapshots only after marker is durable. Test marker-before/after snapshot crash cuts and later failures. Compare over-enforcement interval against plain B2. | Mitigates /4 only after durable marker; cannot infer success if marker also fails. Two files need explicit ordering/reconciliation and one writer. An alleged independent fallback store needs evidence about shared failure modes. |
| **C6: boot-aware deadline record** | Store validated boot identity plus elapsed deadline for same-boot restart, with defined wall-time policy across reboot. Test deep sleep, same-boot process death, elapsed reset and wall jumps. | Potentially improves deadline fidelity, not durability of never-written state. Cross-boot trusted time remains unresolved without extra assumptions; schema and clock policy decision required. |
| **C7: success delivery waits for durable reset** | Only if lead requests a changed success policy: compare over-enforcement prevention with correct-user stall/denial, including persistent reset failure. | **Conflicts with the preserved immediate-access expectation.** Immediate memory can still clear, but delaying accepted access is a separate product decision. Not part of the default candidate set or a hidden solution to /4. |

Discard “retry faster,” a new storage library by itself, graceful shutdown flushing, a timeout wrapped around blocking commit, or launching a second writer as proofs of closure. Faster/alternate persistence can be measured as mitigation; protocol and crash cuts still apply. A rate-limit record that never becomes durable cannot be reconstructed exactly after death.

## 11. Android execution and persisted-state inspection

### 11.1 What each evidence layer can establish

| Layer | Can establish | Cannot establish alone |
|---|---|---|
| JVM deterministic fake/model | Exact transition, ordering, identity, budget, deadline, cancellation and crash-prefix behavior under modeled outcomes. Real-thread latch tests establish local drain/lock independence. | EncryptedPrefs/keystore durability, Android initialization, real kill/reboot, OEM scheduling, UI behavior, or field fault likelihood. |
| Android with injected adapter + real store | Caller wiring, real dispatcher/UI/clock behavior, conditional failure recovery, and real persisted-state survival across actual target-process death. | That the platform naturally produces the injected fault or its frequency. Wrapper gate before commit is not a kill inside Android's file replacement. |
| Android unmodified storage + timed process termination | Old/new state observed around actual commits, real cache loss, restart and reboot behavior, healthy latency. | Exact mid-commit phase without a platform hook, exhaustive atomicity under all crashes, hardware power-loss survival. |
| Platform fault on disposable emulator | Behavior under the particular reproduced file/key/storage error with logs establishing fault origin. | Generalization to Moto G or all storage faults. Never represent debug wrapper exceptions as platform corruption. |

### 11.2 Host-side restart procedure

For each R1–R4 boundary/variant selected for device execution:

1. Pin tested SHA and APK hashes. Use a dedicated emulator/user/test installation and disposable credentials. `prodDebug` currently uses `com.applock`; do not overwrite or clear a personal production credential store. A QA package is not proof of the prod graph unless its equivalence is demonstrated.
2. Verify fixture using real `commit()` followed by an inspector-only new process. Save E1. Exit inspector; ensure no other process/manager owns the store. Install the fault script before the target singleton/keystore read is created.
3. Start the target surface and acknowledge each requested barrier externally. Save PID/process start and boot identity. Use event IDs and on-device timestamps; do not rely on host sleep to identify the boundary.
4. For deterministic pre/post-commit death, hold the debug wrapper at the named boundary and terminate the target with an app-owned debug kill hook or a permitted external kill. Record whether this was abrupt kill, crash, `am force-stop`, normal background eviction, or reboot. A force-stop also changes subsequent launch/service behavior; explicitly relaunch it. Activity recreation/“Don't keep activities” alone does not count as process death.
5. Prove old process is gone and new process/start identity differs. Do **not** call `pm clear`, uninstall/reinstall, restore a clean emulator snapshot, re-provision PIN, or run a test setup that resets counters between action and verification.
6. Before automatic recovery can change evidence, run inspector-only startup and export the logical stored record. Retain read failures too. If target initialization would mutate state before inspection, fix the harness ordering rather than taking an after-recovery dump as pre-recovery evidence.
7. Launch ordinary graph with the intended post-restart fault policy. Read public enforcement, try a submitted PIN with a verifier-entry probe, and record countdown/session/UI behavior. Repeat required recovery/deadline observations. Use a second fresh process after claimed recovery success.
8. For reboot, preserve data and fault policy, record boot identity change, real elapsed/wall times and device-unlock state. Test app access after device unlock separately from any before-unlock keystore/unavailability observation. A 30 s lockout may legitimately expire while rebooting; compare the stored absolute deadline and use a valid longer fixture to test still-active recovery.
9. Terminate/clean only the disposable case environment after evidence collection. Keep accepted ordinary writes draining in shutdown tests; kill them in crash tests. Never let a surviving old test writer contaminate the next fixture.

Use actual mid-commit timed-kill sweeps with fault adapter disabled as a complementary experiment. Classify a trial by observed barriers and new-process state; if the commit boundary cannot be located, report UNKNOWN placement, not a passing pre-commit test. Routine reboot can flush storage; it is not a power-cut test. Sudden power-loss qualification would need a separately designed disposable rig and is outside this campaign's claim.

### 11.3 Fleet coverage and repeat counts

Counts below are proposed campaign minima, not field-frequency estimates or a guarantee of exhaustiveness. Fix them and any justified adjustment before candidate comparisons.

| Fleet lane | Required coverage |
|---|---|
| Development host JVM | Existing relevant suites plus every deterministic case/transition above; every specified schedule once, then 100 reproducible seeded schedules for ordering/recovery combinations. A reproducible violation is a failure even if subsequent runs pass. |
| NucBox x86_64 API 30 and 36 | Full R1–R4 boundary matrix, X01–X15 applicable Android cases, healthy controls, live self-gate/legacy UI and future runtime harness. Ten real kill/restart repetitions per critical pre/post-commit cut per caller. At least three reboot runs per residual where reboot can distinguish behavior. |
| NucBox `full` API 26/29/30/33/35/36 | H01–H05, each residual's representative transient/persistent case, actual restart/persisted-state inspection, schema/keystore compatibility, and selected candidate regressions. Minimum three repetitions per representative restart case. Run a representative active/expired reboot deadline case on every available lane. |
| Moto G 2025 arm64/API 35 | Same full residual critical cuts as API 30/36 (ten repeats); both live callers; real PIN and enrolled biometric behavior; sleep/wake, background restrictions, startup latency, real restart and reboot (three per applicable residual). Test idle and controlled load/low-free-space conditions on disposable data; record thermal/battery/storage conditions. |
| Optional additional OEM/FTL | Extend confidence if device-specific outcomes disagree or the selected policy depends on keystore/storage behavior absent on the fleet. Mark unavailable coverage explicitly. This R-007 campaign does not replace M7's broader OEM/R-002 obligations. |

Use manual persistent-data AVD/controller lanes for restart/reboot cuts if GMD recreates or clears the environment. GMD compilation/execution of ordinary instrumentation cannot substitute for that lane. Record unavailable image, no biometric enrollment/HAL, and known API-29 heap limitation as explicit coverage gaps with a compensating run; never count assume-skipped tests as passes.

Commands already supported by the repository, to be run **later**, with actual report checks:

```powershell
.\gradlew.bat testProdDebugUnitTest detekt assembleProdDebug compileProdDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:testProdDebugUnitTest --tests com.applock.security.LockoutManagerTest --tests com.applock.service.engine.LockEngineRuntimeTest --console=plain
.\gradlew.bat connectedProdDebugAndroidTest --console=plain
```

```bash
./gradlew fullGroupProdDebugAndroidTest -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1 --console=plain
./gradlew api36ProdDebugAndroidTest --console=plain
```

Filter/select new suites once they exist; these commands do not yet name an implemented R-007 device harness. Explicitly select adb target/serial for connected runs. Retain XML counts, failures, skips, and host reports, not just “BUILD SUCCESSFUL.” The [older GMD runbook](../../testing/WP8_GMD_MATRIX.md) supplies host setup; the inspected [build configuration](../../../app/build.gradle.kts) is authoritative for the current six API lanes. [Gate-2 NucBox plan](../../testing/M7_WP2_GATE2_NUCBOX_PLAN.md) supplies prior surface-test context, not R-007 restart evidence.

## 12. Measurements and proposed acceptance budgets

Freeze baseline/candidate configurations, clocks, backoff schedules, fixture, CPU/load and sample rules before comparison. Change one mechanism at a time, then test interactions. Include healthy storage, transient failures at each retry position, persistent false/throw, and never-returning I/O. Report absolute measurements plus deltas; keep NucBox and Moto distributions separate.

| Measurement | Definition / collection | Decision use |
|---|---|---|
| Security exposure | Time from actual verified failure/admission to sufficient durable enforcement; count lost on restart; extra verifier entries per restart cycle; duration persisted lockout is unenforced after bad seed. | Determines whether retry mitigation is sufficient or pre-verification durable admission is needed. |
| Reset over-enforcement | Accepted-success-to-durable-clear time; stale lock duration/count after restart; time to legitimate access; number of repeated stale penalties. | Distinguishes B2 benefit, A's uncertainty cost, and whether a reset marker is worthwhile. |
| Healthy authentication latency | Submission→verification start/end→accepted session→visible unlock; admission queue wait and commit duration separately. Wrong-PIN feedback and cold startup separately. | A's added disk dependency must be evaluated against correct-user cost; subtracting only commit time misses queue/UI delay. |
| Responsiveness | State-read/admission duration, drain event enqueue→handle, screen-off/dismiss response, main-thread stall/jank/ANR and lock occupancy while storage is held. | Protects I1/I3 and exposes constructor/callback stalls that writer tests miss. |
| Recovery quality | Fault end→applicable durable recovery; retry count, exhaustion/re-arm count, writer queue length, bytes/writes, CPU/wakeups, reads per poll burst. | Compares backoff schedules and B3's incremental value; detects starvation/resource cost. |
| Availability | Correct PIN denied/deferred by reason, time in Recovering/Unavailable, cancellation recovery, reboot retry loops and buffer retention duration. | Explicitly prices fail-closed policy; a secure denial is not an availability success. |
| Accounting | Verifier entries vs accepted attempts vs failure/success audit dispatches, recorded-lockout audits, capture dispatches, stored audit rows, recovery diagnostics. | Detects duplicates, invented failures, missing per-operation identity, and crash-lost observations. |
| Deployment cost | New states/callers/schema operations, reviewed race obligations, migration/downgrade failures and operational recovery steps. | Compare maintainability using concrete changes rather than lines-of-code alone. |

For steady-state latency, propose 30 warmups plus 200 measured samples per caller/outcome/configuration on each core host; use at least 1000 samples before using p99 as a selection gate. Record p50/p95, max and raw samples for smaller sets; do not infer a stable tail from ten crash trials. Restore below-threshold/expired fixtures between measured samples outside the timed interval so lockout gating does not invalidate the benchmark. Run at least 30 cold starts and separate first keystore initialization from warm-process runs.

**Proposed thresholds requiring calibration/lead adoption at phase P0:** zero invariant violations; all deterministic crash-prefix assertions exact; control events complete before releasing the I/O gate; no new storage-induced ANR. Initial responsiveness targets are p95 state read/admission ≤10 ms and p95 screen-off/dismiss handling ≤100 ms under the controlled fault workload. Initial healthy-path regression alert is >10% **and** >20 ms increase in p95 visible authentication latency against the same-host baseline. These are investigation/selection thresholds, not existing M7 requirements and not permission to weaken an existing stricter limit. Report all overruns and decide budgets before ranking candidates, especially A.

Availability and security acceptance need policy limits, not invented numerical guarantees: maximum tolerated unknown-state verifier entries (A target zero), acceptable correct-user denial under permanent storage failure, maximum conservative recovery penalty, acceptable reset-loss residual, and maximum extra guesses across repeated restarts. If the lead requires zero lost verified attempts, B cannot meet that requirement solely by showing short average writes. If the lead requires immediate access despite permanently unwritable storage, A cannot meet that requirement without an explicit alternate path and its security cost.

Local diagnostic counters can measure startup/read/write/reset faults per opportunity during controlled or future consented observation; record denominators and observation period. No field probability or severity downgrade follows from this fault-injection campaign. Remote telemetry is not part of this plan.

## 13. Phased execution and exit gates

No phase is executed by creating this document. Proposed harness/candidate work is subsequent implementation work; this deliverable does not change production behavior or claim passing results.

| Phase | Prerequisites and work | Fleet/tools/measurements | Exit gate |
|---|---|---|---|
| **P0 — freeze questions and policy** | Re-fetch/pin source; confirm F4/F5 status and clean test environment. Inventory all verification/gate/mutation callers. Set candidate protocols, persistent-fault behavior, retry budgets, latency/availability limits, residual objectives and test IDs before code. | Source review, current caller graph, this plan, options/risk register. | Lead records policy choices and comparison criteria; no assumption that B1+B2 wins. Undefined A recovery/success semantics block A experiment, not baseline characterization. |
| **P1 — validate the oracle/harness** | Add only test seams/harness in separately reviewable work. Validate fake durable/cache split, process-generation disposal, debug fault control, new-process inspector and external controller. | JVM, one NucBox lane and Moto. Positive control healthy commit survives; negative control deliberate old-state restart is detected; stale-write mutation is caught by oracle. | Harness demonstrably distinguishes old/new/unknown state, target truly dies, tests cannot self-reset counters, faults cannot ship enabled. No source behavior change disguised as harness. |
| **P2 — characterize current baseline** | Run H01–H06 and all R1–R4 cases feasible with baseline, then X races. Re-run existing local gate. No proposed fixes needed to reproduce residuals. | Full deterministic JVM; core NucBox/Moto real restart/reboot; baseline latency/resource distributions. | Every residual has an observed or explicitly blocked reproduction with evidence. Existing passes/failures separated from proposed candidates; missing platform coverage remains open. |
| **P3 — compare bounded candidate experiments** | Based on P2, implement reviewable experimental variants later: B1, B2, B3, B1+B2, full B, A protocol, relevant A+B ablations, and justified C designs. Define transition tables first. | Identical deterministic schedules and core fleet fixtures; healthy latency and failure costs; schema migration for each changed representation. | Reject candidates violating I1–I9. For survivors, evidence matrix identifies exactly which residual subcases improve/remain. No extrapolating combination correctness from individual passes. |
| **P4 — adversarial and fleet confirmation** | Select provisional candidates on measured tradeoffs; run full critical crash-cut matrix, persistent stalls, 20 restart attacks, migrations, clock/sleep/reboot cases and resource stress. | NucBox core and six-API coverage; Moto real callers/biometrics; timed kills without injected faults; retain per-host reports. | Zero unresolved stale-state/duplicate-accounting/drain-blocking defects in selected scope. Claimed durability corroborated by fresh process. Any unrun case recorded as a gap, not green. |
| **P5 — recommend and disposition** | Compare candidates with frozen criteria, rejected alternatives and remaining uncertainty. Review each residual/subcase independently, including A lost-reset and B pre-commit windows. | Campaign synthesis linking raw artifacts, hosts and exact candidate SHAs. | Lead-approved mechanism selection and separate R-007/1–/4 dispositions; no unsupported closure. Selected implementation subsequently passes per-slice local gate and required fleet gate before F5. |
| **P6 — integration follow-through** | Carry selected invariants/cases into F5 shared-graph checkpoint; verify singleton manager and admission ownership. F6 reuses any earlier asynchronous self-gate work and activates only after its checkpoint. | NucBox/Moto integrated tests; existing M7 gates. | F5 cannot be inferred from an isolated harness. F6 deployed `prodRelease` FLAG_SECURE proof and on-device replacement smoke remain required; this plan does not replace them. |

If a baseline finding exposes a fifth mechanism (for example constructor stall or duplicate verification before admission), record it as a distinct finding linked to affected residuals; do not quietly expand R-007's accepted scope. Add a minimal reproducer, candidate hypothesis, and new test ID, then rerun the affected controls. Separate firmware/environment failures from app failures using evidence rather than relabeling all timing failures as flakiness.

Future evidence lives in dated, host-tagged reports under `docs/reports/campaigns/`, with a case matrix and raw artifact references/hashes. Update the working Change F section of M7, risk register, RTM FR-174 and changelog when actual implementation/verification/decisions warrant it under governance. This proposal alone leaves those states unchanged.

## 14. Decision framework and recommendation record

### 14.1 Findings that would favor each approach

Apply hard invariants first; then compare security, legitimate-user availability, latency, implementation complexity and migration risk as separate dimensions. Do not hide an unacceptable restart bypass or indefinite correct-user denial inside a favorable average score. Choose weights and threat assumptions before looking at candidate performance.

| Finding from completed tests | Approach favored, conditionally | Evidence still required before recommending |
|---|---|---|
| Transient read/reset faults recover promptly, repeated-interruption risk explicitly accepted, and no need to preserve below-threshold fallback across restart | B1+B2, or only the slice with measured benefit | R1 mutation race, R4 stale reset ordering, bounded exhaustion, both live callers, real pre/post-recovery restart evidence. /2 and /3 remain explicitly dispositioned. |
| Cold reads are healthy but failed clears create material over-enforcement | B2 alone may be the smallest useful scope | Persistent/stalled clear availability; prove retries never erase newer counts and quantify residual pre-retry crash window. |
| Count writes recover but degraded intervals are frequently lost in relevant test schedules; B3 preserves them without extending deadlines | B2+B3, with B1 only if its read benefit is demonstrated | R2.2/R2.3, X02/X03/X04, true restart/reboot and enough remaining interval for recovery to matter. Compare incremental complexity versus measured enforcement time recovered. |
| Unknown startup state must never admit verification, while per-attempt crash loss is acceptable | C1 read gate plus selected recovery slices | Persistent read failure correct-user UI/recovery and startup-stall tests; all new-state caller checks; explicit acceptance of /3. |
| Repeated kill/restart permits unrecorded verified guesses and the threat policy rejects any such allowance | A or C2 durable admission, commonly with B1/B2 recovery | Zero verifier entry before durable reservation on every path; interrupted correct/unevaluated cases; restart-idempotent recovery; healthy/stalled latency and legitimate-user denial accepted; migration/downgrade verified. |
| A closes failure-side crash loss but conservative recovery harms users more than precise persisted fallback would | A plus carefully unified B3 or C3 record | Demonstrate incremental benefit over A+B2 using same workload and budgets; no duplicate uncertainty penalties or stale reset resurrection. |
| Races are caused by multiple partially ordered records or ambiguous replay, not retry timing | C3 unified journal/transactional representation | Crash during compaction/migration, idempotence, real storage measurements, one-writer preservation; prove whether admission policy still leaves /3. |
| Multiple undurable attempts amplify loss, but a single-attempt residual is explicitly acceptable | C4 later-submission gate | Bound holds across all callers, stalls/cancellation/restarts; compare denial with A. Repeated one-guess-per-restart weakness remains documented. |
| Same-boot restart clock behavior is the dominant remaining issue after durable writes work | C6 boot-aware deadline, composed with selected storage recovery | Real deep sleep, process restart and reboot with wall jumps; explicit cross-boot policy and migration. Does not resolve unwritten state. |
| Requirement demands no stale re-lock after accepted success, even when all reset writes fail | No current A/B mechanism meets it while immediate access and absent durable evidence remain | Lead must change requirement, accept /4, or authorize a separate success-durability policy such as C7 and evaluate persistent-fault denial. Do not recommend a tombstone that can fail on the same storage as “elimination.” |

### 14.2 Tests that settle remaining uncertainty

- If JVM and device results disagree, repeat with injected adapter around the real store, then without it; compare barrier placement, process identity, preference cache, clocks and graph initialization. A dump taken after automatic recovery is insufficient to settle pre-recovery state.
- If B3's value is unclear, hold identical failure/death distributions and compare B2 vs B2+B3 using enforcement milliseconds preserved, extra disk writes and expiry defects. Do not infer benefit from count-only commit rate.
- If A's latency is uncertain, compare A/A+B on Moto with actual crypto cost, warm/cold keystore, writer backlog, correct/wrong PIN and cancellation; measure UI/session timing as well as commit duration. Expand to 1000 samples before a tail-latency decision.
- If A's restart policy is uncertain, repeat deaths while recovering the same P, before and after its anchor expires, including no verification and correct verification. Require one stable uncertainty disposition, no fabricated failure events, and no unlimited fresh attempts.
- If faults appear persistent, extend unavailable duration beyond the entire retry budget and an ordinary lockout cap; verify a documented legitimate-user recovery route. Short transient demonstrations do not settle that policy.
- If a new backend/secondary record is proposed, inject correlated failures, interrupted migration and unknown acknowledgement; demonstrate one ordered writer and inspect disk after each cut. Separate storage improvements from admission-policy improvements.
- If natural failure likelihood matters to acceptance, collect local opportunity counts over a declared observation period; this campaign supplies conditional behavior, not incident probabilities. Keep a residual open/deferred if the decision depends on evidence not yet collected.

### 14.3 Required recommendation and residual disposition template

Create a dated recommendation after execution, with **selected mechanism(s)**, **rejected alternatives**, **policy changes**, **measured healthy/fault performance**, **security/availability tradeoffs**, **migration/downgrade strategy**, **fleet gaps**, and **lead decision/date**. Link exact SHAs and cases; label every remaining inference. A proposed recommendation is not an approved risk acceptance.

Use one row per residual, splitting subcases where necessary:

| Residual | Mechanism and tested claim | Evidence to cite | Limitation that must be dispositioned | Initial disposition in this plan |
|---|---|---|---|---|
| R-007/1 cold-start read failure | State whether startup is gated or only recovered; include mutation-before-read and persistent/stalled read cases. | R1.1–R1.4, H01/H05, live caller verifier traces, fresh-state dumps. | Unknown-state verification window, lost historical counts, availability during unreadable storage. | **UNDECIDED; no new execution evidence.** |
| R-007/2 degraded restart loss | State whether exact deadline or conservative pending policy survives, and the first durable boundary that enables it. | R2.1–R2.4, X02–X04, pre/post-recovery real kill and reboot. | Before-recovery death, permanent write failure, deadline/clock/reboot limits. | **UNDECIDED; no new execution evidence.** |
| R-007/3 admitted-but-undurable death | Separate lost verified failures, multiple queued losses, and lost successful resets. | R3.1–R3.4, A boundary matrix, repeated restart attack, caller ownership/accounting. | B's first-write window; A's uncertain result and reset-loss side; unlocated platform crash cuts. | **UNDECIDED; no new execution evidence.** |
| R-007/4 failed reset resurrection | State when clear/tombstone becomes effective on restart and what correct users experience before then. | R4.1–R4.4, X01/X06/X08/X15, real fresh-process read and UI. | Immediate access before durable reset, permanent clear failure, old count after expiry. | **UNDECIDED; no new execution evidence.** |

For each final row record: **resolved within a precisely stated scope**, **mitigated with remaining exposure**, **accepted**, or **deferred**, mapping any formal status to the risk register's vocabulary. Include owner, rationale, security direction, availability impact, threat assumptions, evidence confidence, unrun tests, follow-up milestone/date, review trigger, and lead approval. Useful triggers include a reproduced restart bypass, a storage incident, material correct-user denial, a schema/clock change, or F5/F6 caller changes. No residual becomes resolved merely because retry tests pass; no accepted/deferred residual disappears from the register.

The recommendation must follow the observed tradeoffs and explicit policy choices. Until those tests and decisions exist, both the existing B1+B2 recommendation and stronger admission alternatives remain proposals.
