# M7 F2 hardening: P3 candidate B2 specification (safe retry of failed persistent changes)

**Date:** 2026-10-07

**Status:** Ready, reviewed by the lead on 2026-10-07, together with the scheduler, B1, and B3 specifications.

**Scope:** The document specifies candidate B2 of phase P3. B2 gives the manager ownership of admitted writes. It
retries the latest failed write through the existing writer queue. A newer admission cancels the retry chain. B2 uses
only the mechanics of the shared scheduler in `2026-10-07_R007_F2_HARDENING_P3_SCHEDULER_SPEC.md`.

## 1. Purpose

At baseline, a write that returns false or throws a contained storage exception is never repeated. A caller can also
drop an admitted write by cancelling its result. In both cases memory stays ahead of storage until the next admission
writes again or the process dies.

Under B2, the manager owns each admitted write (section 2.2). It retries the latest failed write on the P0 schedule of
the F2 hardening entry of `M7_PLAN.md`, with the delays of the scheduler spec. B2 supplies the three rules that the
scheduler leaves to a candidate: when a chain starts (section 3), what a retry writes (section 2.1), and how a
committed retry is published (section 4).

What B2 targets:

- **R-007/4 (main target).** A failed clear becomes durable at the first retry that commits. A restart after that
  commit loads `Z` instead of the stale pair (R4.1, R4.2).
- **R-007/3 (in part).** After a failed write, the latest count, threshold deadline, or reset becomes durable at the
  first committed retry. A caller that cancels its result cannot drop an admitted write (X08). Writes that are held or
  queued at death (R3.1 to R3.3) keep their baseline result.
- **R-007/2 (side effect).** A committed retry of a failed threshold write makes its admission-time deadline durable.
  The fallback window that started at completion time stays in memory only. B3 persists it.

Limits of B2:

- B2 makes a failed change durable when a retry commits. A death before that commit still loses the change, as at
  baseline.
- Under a persistent write fault, each restart still gives one verified guess (R2.4) and brings back the stale pair
  (R4.4). The chain keeps writing at the 60 s cap for the life of the process (section 3).
- A write that never returns blocks every later write and retry (T6, T15). B2 sets no write timeout and starts no
  second writer.
- A retry cannot restore a count that an unreadable seed never loaded.
- Over an unreadable seed, the B2 retry erases a stored lockout that the baseline keeps (R1.3). At baseline, the
  failed first write leaves that lockout intact. The retry overwrites it with the latest admitted state. P3 runs B2
  with this regression and reports its cost through the R1_3 test of section 5. Suppressing only the retry would not
  prevent the loss, because a committed ordinary write already overwrites the same unknown history. B1 does not
  resolve it after a local mutation. The admission and storage policy must prevent it.
- B2 adds no durable record, key, or format. It changes no caller code.
- The runtime self-gate path stays inert until F6. On that path, the `LOCKOUT_TRIGGERED` audit and the capture run
  after the await, in the coroutine of the caller. A cancelled caller skips them, so that path gets accounting at most
  once. F6 must make them run exactly once (the F6 entry of `M7_PLAN.md`).

## 2. State and transitions

### 2.1 Chain, target, payload, and "still wanted"

A manager has at most one write chain, in the single slot of the scheduler. The **target** of a chain is the
`PendingWrite` of the admission whose ordinary write failed. The chain keeps only the target and its sequence number.

A retry writes the target payload `target.persist`, which the admission built:

- for a threshold failure, the count and the threshold deadline;
- for a below-threshold failure, the count and the recorded wall deadline of memory;
- for a reset, `(0, 0)`.

The `prepare` step gets this payload on the writer, under the manager lock, through the hook `buildRetrySnapshot`
(section 8). A retry performs I/O only while its target is the latest admission (section 4). Until a new admission,
no rule changes the count or the recorded wall deadline in memory. The payload is therefore the current state, not a
replay of an older state. Both fields are absolute values, so a repeated write of one payload cannot add a count or
move a deadline. Neither a failed write nor a retry enters the failure admission, so a storage failure never becomes
another authentication failure.

A write retry is **still wanted** while all three conditions hold:

1. The manager has not stopped.
2. Its sequence number is current.
3. No admission followed the target: `snapshot.revision == target.revision`. A reset advances the revision too, so
   this check also covers the streak.

Time does not end a chain. An expired lockout still leaves a count that the next failure builds on. A stale count
still shortens the way to the next lockout.

### 2.2 Manager-owned writes and caller results

Once a failure or reset is admitted, its persistence belongs to the manager. B2 separates the write from the result
that the caller receives:

- `enqueueWrite` and `enqueueReset` start the write as a **manager job** (`scope.launch(ioDispatcher)`), in admission
  order as before. The manager never hands this job out.
- `Pending.resolved` is a `CompletableDeferred`. The manager job completes it after its completion step, also after
  shutdown, as at baseline. A caller that cancels `resolved`, or a waiter on it, changes only its own view (T5).
- The manager job contains every failure. A failed write (section 2.3) gets the failed-write completion. A throwing
  callback completes `resolved` with its exception, as at baseline (X13).
- A cancelled manager job ends its work without a completion (T4). Its completion handler then cancels `resolved`, so
  no waiter hangs. This also holds for a job that was cancelled before it started. Production never cancels the
  manager scope.
- `onResolved` is the accounting channel. It fires once for each admission whose manager job completes while the
  manager has not stopped. It carries the outcome of that admission and never fires for a retry.

What "accounting exactly once" and "no late UI or session effect" mean for each caller:

| Caller | Accounting exactly once | No late UI or session effect | Effect of B2 |
|---|---|---|---|
| `ApplicationLockEngine` (live; `LockScreenActivity` and the self-gate of `MainActivity` through `AuthGateViewModel`) | The count rises at admission. The callback gives one `LOCKOUT_TRIGGERED` audit for a recorded outcome and one intruder capture with the count of the operation, or one log line for a failed clear | The session grant and the UI step run synchronously before any result exists, and the callback has no UI or session action. No caller cancels a request | The callback also fires after an adapter cancellation, and never twice |
| `LockEngineRuntime` drain path (inert until F6) | One `LockoutRecorded` for each `FailureToken`, which the reducer accepts once | Runtime shutdown cancels `workScope`, so no follow-up starts, and the runtime rejects inputs after it stops | The write persists after the waiter is cancelled, as at baseline |
| `LockEngineRuntime` self-gate (suspend; F6 wires it to `MainActivity`) | The `UNLOCK_FAILURE` audit and the admission run under `lifecycleLock`; `LOCKOUT_TRIGGERED` and the capture run after the await | A cancelled caller coroutine ends at its await and acts on no late result; the steps after the await check `stopped` again | The write persists after the caller is cancelled. The steps after the await are skipped for a cancelled caller (a limit of section 1) |

### 2.3 Transition table

States: None, Pending (on the timer), Queued (in the writer queue), Running (retry I/O in progress), Stopped. A write
**fails** when it returns false, throws a contained exception, or throws a `CancellationException` while its manager
job is active. The last case follows the cancellation rule in the threads and locking section of the scheduler spec.
"Failed-write completion" is the existing `applyCompletion`, outcome, and callback of a failed ordinary write, for
that one operation.

| ID | From | Trigger | Guard | Effect | To |
|---|---|---|---|---|---|
| T1 | None | Ordinary write returns true | none | Baseline completion | None |
| T2 | None | Ordinary write fails | Latest admission (`write.revision == snapshot.revision`) | Failed-write completion (fallback from completion time for a failure, memory unchanged for a reset), then `start(WRITE)` with this write as target | Pending, attempt 1, 1 s |
| T3 | None | Ordinary write fails | A newer admission exists | Failed-write completion only. The newer queued write carries the newer state | None |
| T4 | any | The manager job of an ordinary write is cancelled | none | The work ends: no completion, no callback, no chain. `resolved` is cancelled. The write may already have reached storage | unchanged |
| T5 | any | A caller cancels `resolved` or a waiter on it | none | No effect on the manager job | unchanged |
| T6 | any | Ordinary write never returns | none | Nothing completes. Later writes and a fired retry wait behind it. No second writer | unchanged |
| T7 | Pending | Timer fires | none | Retry task joins the writer queue behind the queued writes. No lock | Queued |
| T8 | Queued | Writer reaches the task | Still wanted | `prepare` builds the payload under the lock. `perform` writes without a lock | Running |
| T9 | Queued | Writer reaches the task | Not still wanted | `IGNORED`. No I/O | None |
| T10 | Running | Retry write returns true | Still wanted | Publication of a committed retry (section 4). No outcome, callback, or count change | None (`ENDED success`) |
| T11 | Running | Retry write returns true | Newer admission, or stopped | Nothing published. The newer write queued behind overwrites the payload | None (`ENDED abandoned`) |
| T12 | Running | Retry write fails | Still wanted | `scheduleNext` after the attempt finishes. No fallback, count, or outcome change | Pending, attempt n+1 |
| T13 | Running | Retry write fails | Newer admission, or stopped | Nothing published or scheduled | None (`ENDED abandoned`) |
| T14 | Running | The job of the retry is cancelled | none | Chain ends. Nothing published or scheduled | None (`ENDED threw`) |
| T15 | Running | Retry write never returns | none | Later admissions cancel the chain. Their writes wait behind the retry. No overlapping retry | Running until it returns |
| T16 | Pending or Queued | Failure or reset admitted | none | `cancel()` under the admission lock, then the new write is enqueued. A queued task later ends as T9 | None |
| T17 | Running | Failure or reset admitted | none | `cancel()` makes the sequence stale. The I/O goes on, the new write queues behind it, and the retry ends as T11 or T13 | Running, then None |
| T18 | any | `shutdown()` | none | `stopped = true`, then scheduler `stop()`. A pending retry is cancelled, a queued one ends as T9, a running one ends as T11 or T13 | Stopped |
| T19 | any | Process death | none | Timer, queue, and running step die. D keeps the last committed payload, which can be a retry payload. The new process starts with no chain | None |
| T20 | any | `currentState()` or `failureCount()` | none | B2 starts no retry and no storage I/O. The baseline re-seed stays until B1 replaces it | unchanged |

## 3. Eligibility and supersession

- **Start.** Only T2 starts a chain. B2 calls `start` in the completion step of the manager job, after
  `applyCompletion` and before the callback. A throwing or blocking callback (X13) therefore cannot prevent the start.
  At an ordinary completion no chain exists, because every admission cancelled the earlier chain. A stale running
  retry has also finished before this write ran. So `start` never replaces a chain in a B2-only branch.
- **Not eligible.** A commit, a write that is not the latest admission, a stall, a cancelled manager job, a read, or a
  poll starts no chain.
- **Supersession.** `submitFailure` and `submitSuccess` call `cancel()` inside their existing `synchronized(lock)`
  block, after the `stopped` check and before they enqueue. A new failure or a new success therefore ends a reset
  chain. A reset ends a failure chain in the same way. The write of the new admission carries the applicable state.
  If that write fails, it starts the next chain at attempt 1.
- **Retry failure.** A retry failure never runs the ordinary completion. It therefore cannot extend the fallback from
  its own completion time (T12).
- **Delays.** The delays follow the delays section of the scheduler spec. An unchanged chain at its cap writes every
  60 s for the life of the process. This is not an overall rate limit, because each new admission and each restart
  starts a new chain at 1 s.
- **Queue growth.** Retries do not bound the queue growth of ordinary admissions during a stall. That growth is the
  baseline behaviour of R3.3.
- **Cancellation and shutdown.** The transition table holds these rules (T4, T5, T14, T18).

## 4. Write ordering and stale results

**Ordering rule.** Storage receives payloads in non-decreasing order of their admission revision. Three facts give
this:

1. Ordinary writes run in admission order on the single writer (baseline).
2. A chain for target r starts only at the completion of write r while r is the latest admission. No older write is
   then queued, and no newer write exists.
3. A retry performs I/O only if `prepare` finds revision r still current. Every later admission has a higher revision,
   and its write joins the queue behind the running retry.

So a retry never jumps ahead of an older queued write, and no older queued write can overwrite it.

**Freshness checks.** Both checks test "still wanted" (section 2.1) under the manager lock, and neither does I/O.

| Point | Checks | When a check fails |
|---|---|---|
| Before I/O (`prepare`) | Not stopped; sequence current (scheduler); `snapshot.revision == target.revision` | `IGNORED`, no I/O, chain ends |
| Before publication (`complete`) | Not stopped; sequence current; `snapshot.revision == target.revision` | Nothing published or scheduled; chain ends |

A running retry that a newer chain or `stop()` made stale never reaches `complete`. The scheduler records
`ENDED abandoned` for it (the retry chains section of the scheduler spec). The B2 check in `complete` is therefore a
second guard.

**No new counter.** The revision changes at every admission. While a chain exists, no other rule changes its inputs.
The re-seed is off after the first admission, and a retry changes no fallback. No older write can still complete (fact
2), and the recorded deadline changes only when the target itself is promoted.

**Publication of a committed retry.** A committed retry keeps the in-memory fallback, because persisting the count
does not revoke the existing penalty. The fallback keeps its original expiry; the retry neither clears nor extends
it. For a threshold target, the retry records the admission-time wall and elapsed deadlines of the target. Storage
then holds that deadline. The derived degraded flag therefore clears only when the stored deadline covers the
remaining fallback. For a below-threshold target the flag stays set, because its stored pair has no applicable
deadline.

**Commit after newer state arrived.** If an admission happens while a retry write runs (T17), storage first gets the
target payload. The newer payload follows from the write queued behind it. The retry publishes nothing and promotes
nothing. A death between the two commits leaves the target payload, which is a committed prefix in admission order,
as in R3.2. Example: a reset retry in flight when F1 is admitted commits `Z`, then F1 commits `(1,0)`.

**Acknowledgement ambiguity.** After a commit reported as false (R3.4), the retry writes the same absolute pair again.
No count, deadline, or outcome changes.

## 5. Expected outcomes and tests

**Harness changes.** The model replaces the base rule S11 (no retry) with these rules:

- **S11.** A failed write of the latest admission expects one chain, with attempt 1 and a delay of 1 s from its
  completion.
- **S12.** Every admission cancels the expected chain. A retry task that reaches the writer afterwards writes nothing.
- **S13.** Each retry write carries the target payload. A commit records the deadlines of a threshold target while the
  target is the latest admission. A failure expects the next attempt with the next delay. A retry changes no count,
  fallback, outcome, or callback.
- **S5, extended.** Store write indexes count ordinary and retry writes in queue order. `nextWriteIndex` returns the
  store index of the next admitted write. The `ScheduleDriver` also scripts faults for retry writes.
- **Cancelled results.** `checkOutcomes` treats a result that the test cancelled as cancelled and does not await it.
- **Fault script KDoc.** The KDoc of `WriteScript.ThrowCancellation` in `FaultPlan.kt` says that the model rules do
  not cover the script. On the B2 branch, the manager treats that cancellation as a failed write, as the model already
  does for a `THREW` event. The KDoc changes to say so.

The tests use the production backoff on the virtual timer. New tests go into a new class `WriteRetryTest` in the
`r007` package, on `BaselineCase` for its evidence rule. Changed tests stay in their classes. M is the memory and
public state, D the stored pair, V the verifier entries, A the operation outcomes and callbacks, and R the retry
events and retry writes.

| Test | Transitions | Schedule | Expected M, D, V, A, R |
|---|---|---|---|
| `R4_2 - a failed clear is retried after 1 s and a restart after the retry loads Z` (FailedResetTest, changed) | T2, T7, T8, T10 | `L5`; clear `ReturnFalse`, then `Throw`; advance 1 s; restart | M Available, count 0 throughout. D `L5`, then `Z` at 1 s. A: reset false, 1 callback. R: scheduled, fired, started, ended success; 2 writes. After restart Available, count 0 |
| `R4_2 - a death before the retry commit keeps the stale pair and a death after it keeps Z` | T19 | `L5`, clear false; run 1 kills at 500 ms; runs 2 and 3 advance 1 s with the retry `HoldBeforeCommit` (kill) or `CommitThenHold` (two restarts) | D `L5` with `LockedOut(T-500)` after restart; D `L5`; D `Z` and no write in either new process (covers X15) |
| `R4_2 - a clear that fails three times is retried after 1, 2, and 4 s and stops at the first commit` | T12, T10 | `L5`; writes 0 to 2 false; advance 1, 2, 4 s | M unchanged. D `Z` at 7 s. R: attempts 1 to 3 with delays 1, 2, 4 s, then ended success; 4 writes |
| `R4_3 - a waiting reset retry is cancelled by a new failure and never writes` | T16 | `L5`, clear false; F1 at once; advance 120 s | D `(1,0)`. R: scheduled, cancelled, no fired; 2 writes |
| `R4_3 - a reset retry that fired before a new failure is ignored on the writer` | T7, T16, T9 | `holdIo`; advance 1 s; F1; `resumeIo` | D `(1,0)`. R: fired, cancelled, ignored, no started; 2 writes |
| `R4_3 - a reset retry in flight when a new failure is admitted commits first, and the failure write follows` | T17, T11 | Retry write held; F1; release; second run kills with F1's write held | Disk order `Z`, then `(1,0)`; M count 1. R: ended abandoned, no promotion. Kill run: D `Z` (committed prefix) |
| `R4_3 - a second success and a later failure each supersede the reset retry before them` | T2, T16 | S false; S2 false; F1 commits; advance 120 s | D `(1,0)`. R: two chains, each cancelled; 3 writes |
| `R4_4 - a retry write that never returns blocks later writes and no second writer starts` | T15, T17 | `L5`, clear false; retry held; F1, F2; advance 120 s; kill | Max concurrent writes 1; 2 writes started; no new firing; D `L5` after kill |
| `R2_1 - a failed below-threshold write is retried, so a later restart keeps its count but not its fallback` (DegradedRestartTest, changed) | T2, T10, T19 | As the baseline test | See section 6.1 |
| `R2_4 - failed retries keep the count and end the fallback at its original deadline` | T12, T10 | `Z`, all writes false; F1; advance 1, 2, 4, 8 s (retries at 1, 3, 7, 15 s), then to T - 1 ms, T, and 31 s | M count 1; `LockedOut(1 ms, degraded)` at T - 1 ms and Available at T (no extension). D `Z`. A: one degraded outcome, 1 callback. 6 writes |
| `X01 - a failure retry cannot persist behind a newer reset or over a newer failure` | T16, T9, T3 | F1 false; F2 and S admitted with the retry waiting, then fired (`holdIo`); F2 false behind a queued S | D `Z` in each run. R: no started. F2 false starts no chain |
| `X03 - a retry of a threshold write that failed after its window persists the expired deadline and revives nothing` | T2, T10 | `C4`; F5 held 40 s, then false; advance 1 s; restart | D `(5, W0+T)` (expired). M degraded 29 s from the fallback, never a new T. After restart count 5, Available |
| `X05 - under a persistent write fault one chain runs with delays up to 60 s, and polls add no retry` | T12, T20 | `Z`, all writes false; F1; attempts 1 to 9; 1000 polls between attempts | Delays 1, 2, 4, 8, 16, 32, 60, 60, 60 s; one pending retry at any time; 1 read; 10 writes |
| `X06 - shutdown cancels a waiting retry, ignores a queued one, and lets a running one finish unpublished` | T18 | Three runs; the running run holds the retry of a `C4` threshold target, then releases true or false | Waiting: cancelled, no fired. Queued: ignored, no I/O. Running: D `L5` (true run), no promotion, no rescheduling (false run), 1 callback, and `ENDED abandoned` when the held retry finishes |
| The three changed X08 tests of `CrossCuttingTest` | T2, T5 | As the baseline tests | See section 6.1 |
| `X08 - a retry write that throws a cancellation while the job is active is retried after the next delay` | T12, T10 | `L5`, clear false, retry 1 `ThrowCancellation`; advance 1 s, then 2 s | R: ended retry, scheduled (attempt 2, 2 s), then ended success. D `Z` at 3 s. M unchanged |
| `X08 - a cancelled manager job ends its write without a completion and cancels the caller result` | T4, T14 | Direct manager with an injected scope; cancel the scope with a write queued, with a write blocked, and with a retry blocked | No completion, no callback, no chain; `resolved` cancelled, no waiter hangs. Retry run: ended threw, nothing scheduled |
| `X12 - a committed retry of a failed threshold write records the lockout without a second outcome or callback` | T10 | `C4`, F5 false; advance 1 s; restart at 5 s | Outcome stays degraded with count 5; 1 callback. M `LockedOut(T-1 s, degraded = false)` after the retry. D `L5`. After restart `LockedOut(T-5 s)` recorded |
| `R3_4 - a retry after a false acknowledgement writes the same pair again and changes nothing in memory` | T2, T10 | `Z`, write 0 `CommitThenReportFalse`; advance 1 s | D `(1,0)` before and after; M count 1, `LockedOut(T-1 s, degraded)`; outcome unchanged |
| `R1_3 - a retry of the racing failure write erases the unreadable stored lockout that the baseline keeps` | T2, T10 | As the baseline racing failed-write test, plus advance 1 s before the restart | D `(1,0)`. After restart Available, count 1. The evidence records the erased `L5` as the cost of the regression |
| `I3 - a blocked retry write holds no manager lock, so admission and state reads go on` | T8, T17 | Real threads: plain manager with a test timer, first write false, retry write blocks on a latch | `submitFailure` and `currentState()` return on another thread while the retry blocks; the retry runs on `lockout-io` |
| Unchanged baseline tests, through S11 | T1, T3, T6, T20 | H01 to H06, R2_2, R2_3, R3_1 to R3_3 | No retry event; results as at baseline |

## 6. Predicted evidence

All results in this section are PREDICTED. The lead reviews every change that this section does not predict.

### 6.1 Changed r007 tests

| Class and test | Baseline result | B2 result for the current test code | Proposed change |
|---|---|---|---|
| `FailedResetTest` `R4_1 - a failed clear keeps the old lockout in storage and a restart enforces it again` | All three scripts: D `L5` at restart, `LockedOut(T-5 s)` | `ReturnFalse` and `Throw`: the retry commits during the 5 s advance; D `Z`, Available, count 0. `HoldBeforeCommit`: unchanged | Kill at 500 ms, before the first retry: the baseline result holds for all three scripts with `LockedOut(T-500)`. Name ends "a restart before the first retry enforces it again" |
| `FailedResetTest` `R4_1 - after the stale deadline ends, the old count makes the next wrong PIN lock at once` | Count 5 after restart; 1 wrong PIN; `LockedOut(60 s)` | The retry commits `Z` during the 40 s advance; count 0; 5 wrong PINs before the lock; `LockedOut(T)` recorded | Restart at 500 ms, then advance 40 s in the new process: the baseline result holds |
| `FailedResetTest` `R4_2 - a failed clear is never retried, so the stale pair stays until the next committed write` | 1 write; D `L5`; restart `LockedOut(T-5 s)`; a correct PIN clears it | 2 writes; D `Z` at 1 s; restart Available, count 0 | Replaced by the first R4_2 test of section 5 |
| `DegradedRestartTest` `R2_1 - a restart erases the degraded fallback of a failed below-threshold write` | Every death: D `Z`, count 0 after restart | Death at 5 s, at T-1, and the reboot at 5 s: D `(1,0)`, count 1 after restart. State still Available, gate still open at once, `lost_enforcement_ms` unchanged. Death at 0 s: unchanged | New expected values and the new name of section 5 |
| `CrossCuttingTest` `X08 - cancelling the resolved Deferred before its write starts drops the admitted write` | The second write never runs; 1 write; D `(1,0)`; count 2; 0 callbacks; restart count 1 | The second write runs after the release; 2 writes; D `(2,0)`; `resolved` cancelled; 1 callback; restart count 2 | New values; `check()` runs before the restart. Name: "... still runs its write and calls back once" |
| `CrossCuttingTest` `X08 - a failure write that throws a cancellation arms no fallback and skips its callback` | Available; count 1; 0 callbacks; `resolved` cancelled; D `Z` | Failed write: `LockedOut(T, degraded)`; count 1; 1 callback; `resolved` gives the degraded outcome with count 1; D `Z`; a chain starts. The second admission cancels it and commits `(2,0)` | Harness admissions and `check()` replace the direct submission. Name: "... is a failed write that arms the fallback and calls back once" |
| `CrossCuttingTest` `X08 - a clear that throws a cancellation skips its callback, so the failed clear is not reported` | Callback value none; `resolved` cancelled; restart `LockedOut(T)` | Callback reports false; `resolved` gives false; a chain starts; the restart comes before the first retry, so `LockedOut(T)` stays | New values. Name: "... is reported as a failed clear" |

### 6.2 Unchanged r007 assertions with new ledger lines

These tests schedule a chain, so their evidence traces gain retry events, but their assertions stay:

- `FailedResetTest`: `R4_1 - through the gate a failed clear of count 4 ...` (the restart comes before the first
  retry), `R4_3 - after a failed clear the next failure write ...` (scheduled, then cancelled by F1), and `R4_4`
  (each process dies before its first retry; the failing last wrong PIN schedules one more chain).
- `DegradedRestartTest`: `R2_4 - ... the fallback ends at its deadline ...` (one retry fires in the single advance and
  fails; the fallback still ends at T; 2 writes) and `R2_4 - ... each restart gives one verified guess ...`.
- `UndurableDeathTest`: the three `R3_4 - a false acknowledgement ...` tests (the restart comes first).
- `CrossCuttingTest`: `X03 - ... stalls past its window and fails ...`, `X04 - a wall change does not move a degraded
  fallback` (wall jumps do not fire the timer, which runs on the elapsed clock), and `X08 - cancelling the resolved
  Deferred while its write runs ...`.
- `ColdReadTest`: `R1_3 - when the racing failure write fails, the stored lockout returns after a restart`.

### 6.3 Unchanged r007 assertions and identical traces

These tests start no chain, so their assertions and their ledger traces stay as at baseline:

- `HealthyControlsTest`: H01 to H06. No write fails.
- `ColdReadTest`: every test except the racing failed-write test of section 6.2.
- `UndurableDeathTest`: every test except the three R3.4 tests of section 6.2. R3.1 and R3.2 use held or queued
  writes, and R3.3 fails only a write that is not the latest admission. The result of a write after shutdown (R3.3)
  still completes with its outcome.
- `DegradedRestartTest`: `R2_2` and `R2_3`. The failed write is not the latest admission.
- `CrossCuttingTest`: every test except the three tests of section 6.2 and the three X08 tests of section 6.1. X05,
  X06, X13, and X14 fail no write. A throwing callback still completes `resolved` with its exception (X13). A
  cancelled waiter still leaves its write running (X08).
- `FailedResetTest`: `R4_3 - a failure queued behind a held failing clear commits after it`.
- Outside `r007`: `LockoutHarnessTest` keeps its assertions, and its seeded schedules gain retry events.
  `LockoutManagerTest` and `LockEngineRuntimeTest` build managers over failing storage. They keep their assertions
  and get a virtual timer that fires only on request (scheduler test support), so no real retry runs inside a test.

### 6.4 Residual subcases that change

- JVM: R4.1 for a death after the first retry; R4.2 (retry, then `Z`); R4.3 gets its retry oracles (stale before I/O,
  in flight, generation reuse); R2.1 count part (durable count, fallback still lost); X01; X03 retry part; X05 (one
  chain at a time); X06 retry part; X08 (a cancelled result keeps its write, an adapter cancellation is a failed
  write, a cancelled manager job ends its work); X12; X15; R3.4 rewrite. R1.3 changes for the worse: the retry erases
  the stored lockout that the baseline keeps after a failed racing write. R3.1 to R3.3, R2.2, R2.3, and R4.4 keep
  their results.
- Device lanes (from the Moto G report of 2026-10-04): R4.1a with a fault on the clear only stores `Z` before the kill
  at 5 s, and the first wrong PIN after the relaunch does not lock. R4.1b changes only if the write fault ends before
  the kill. R4.1c (read-only directory) keeps failing while the directory stays read-only. R4.2 makes 2 writes and
  stores `Z` for a transient fault, or 6 writes in 60 s with the stale pair for a persistent fault. R3.x, R4.3, R4.4,
  X16, and the caller cases X09 to X11 keep their results. The device lanes do not run X08.

## 7. Boundary and oracle check

| Boundary or oracle | B2 |
|---|---|
| Never await storage on the runtime drain | No caller waits for a retry |
| Keep one ordered writer | Retries join the writer queue; a stall is never bypassed (T6, T15) |
| Do not hold the admission lock across I/O | No I/O under the manager lock (threads and locking section of the scheduler spec) |
| Stale work must not replace newer state | Payload revisions reach storage in non-decreasing order (section 4) |
| No fabricated failures, captures, or audit events | No retry enters an admission or calls a callback (sections 2.1 and 2.2); recovery creates no audit event |
| Never extend the fallback deadline | A retry never changes the fallback expiry (T12, section 4) |
| Immediate in-memory reset stays | A reset retry never changes memory (T10) |
| P0 retry policy | Scheduler delays, one chain, and no B2 I/O from `currentState()` (T20) |
| P0 threat and storage policies | Unchanged; each restart under a persistent write fault still gives one verified guess (R2.4) |
| I1 responsiveness | No new work on the drain or the main thread |
| I2 one ordered writer | The linearization point of a retry is its `prepare` step on the writer |
| I3 short admission section | `cancel()` and `start` only change scheduler fields; tested by the I3 test of section 5 |
| I4 freshness | Revision checks before I/O and before publication (section 4); tested by the R4_3 tests and X01 |
| I5 event accounting | One callback for each completed admission and none for a retry (section 2.2); tested by X12 and the X08 tests |
| I6 immediate reset | Unchanged |
| I7 honest lifecycle | Separate transitions T2 to T6, T14, T15, T18, and T19 |
| I8 deadline integrity | The retry writes the admission-time deadline and never recomputes T; tested by X03 and R2_4 |
| I9 callers and biometrics | No caller code changes; biometric accounting unchanged |

## 8. Extension hook for B3

B2 puts its three candidate rules into private functions of `LockoutManager`. B3 changes these functions and adds no
second mechanism:

```kotlin
// Eligibility, at each ordinary completion under the lock (T2). B2: a failed write of the latest admission.
private fun startsWriteRecovery(write: PendingWrite, committed: Boolean, current: Snapshot): Boolean =
    !committed && write.revision == current.revision

// Payload, in prepare() under the lock. B2: the target payload (count and admission-time wall deadline).
private fun buildRetrySnapshot(target: PendingWrite, current: Snapshot): LockoutSnapshot = target.persist

// Publication, in complete() under the lock (T10). B2: record the deadlines of a threshold target and keep the
// fallback. B2 ignores persisted.
private fun applyRetryCommit(target: PendingWrite, persisted: LockoutSnapshot, current: Snapshot): Snapshot =
    if (!target.wasThreshold) {
        current
    } else {
        current.copy(
            recordedWallDeadline = target.recordedWallDeadline,
            recordedMonoDeadline = target.recordedMonoDeadline,
        )
    }
```

`buildRetrySnapshot` is the hook where B3 adds the fallback deadline. It would write the larger of the target deadline
and the remaining fallback converted to wall time, and it would skip an expired fallback. `startsWriteRecovery` is
where B3 starts a chain after a committed count-only write that leaves a fallback in memory (R2.2). In
`applyRetryCommit`, B3 records the deadline that it persisted, so the derived degraded flag clears only when storage
covers the fallback. If B3 starts a chain while older writes are queued, it must add the fallback deadline to both
freshness checks of section 4 (X02).

Assumptions that B3 builds on:

1. Every admitted write reaches a completion unless the process dies or its manager job is cancelled (section 2.2).
2. An adapter cancellation with the job active is a failed write that arms the fallback (section 2.3, T2).
3. The completion and the chain decision belong to the manager job, and `onResolved` never fires for a retry
   (section 2.2).
4. A committed retry keeps the fallback, and the derived degraded flag follows the stored deadline (section 4).
5. The revision alone is a complete freshness token only while no older write is queued at chain start (section 4).
6. A new admission or a restart starts a new chain at 1 s (section 3).
7. A retry over an unreadable seed overwrites the unknown stored state (section 1, R1.3).
