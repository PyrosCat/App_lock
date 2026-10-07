# M7 F2 hardening: P3 candidate B3 specification (degraded deadline in the stored pair)

**Date:** 2026-10-07

**Status:** Ready, reviewed by the lead on 2026-10-07, together with the scheduler, B1, and B2 specifications.

**Scope:** The document specifies candidate B3 of phase P3. In B3, a B2 retry also stores the remaining in-memory
degraded deadline in the existing `failure_count` and `lockout_until` keys. The deadline then survives a restart. B3
extends the retry of `2026-10-07_R007_F2_HARDENING_P3_B2_SPEC.md` and adds no second mechanism. The B3 arm is B2+B3,
and the comparison measures it against B2 alone.

Statements about the current code are INSPECTED at `c47d642`. All B3 behaviour in this document is PREDICTED, in the
evidence labels of `2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`.

## 1. Purpose, target residual, and limits

B3 targets R-007/2. At baseline, a failed failure write arms an in-memory fallback (`fallbackMonoDeadline`, on the
elapsed clock). The fallback blocks the next attempt for its window. A restart loses it, because the stored pair holds
only the count and the recorded wall deadline. B2 alone makes the count durable. For a failed threshold write, B2 also
stores the admission-time deadline. Neither a below-threshold fallback (R2.1, R2.2) nor the part of a fallback that
starts at a late completion (R2.3, X03) reaches storage under B2 alone.

B3 converts the remaining fallback to a wall deadline in the retry payload and records that deadline after the commit
(section 3). A committed count that leaves a fallback outside storage also starts a retry (D2).

B3 preserves the fallback after a retry commits. A restart before that commit can still lose it. P5 must resolve these
remaining R-007/2 risks:

- **Loss window.** The window starts at the failed completion (D1) or at the count commit that leaves a gap (D2). It
  ends at the first converted commit. The first attempt comes 1 s after the window starts, and the scheduler
  specification sets the later delays. An admission during a retry write opens the window again (D9). Each test case
  records the window (section 6).
- **Persistent write fault.** No retry commits, so each restart still gives one verified guess (R2.4).
- **Wall clock.** After the conversion, a backward wall change lengthens a below-threshold lockout (section 3.5).
- **Unread stored pair (R1.3).** Over an unreadable seed, a B2 retry already overwrites the unknown stored pair. B3
  adds its converted deadline to that payload. The unread count stays lost. An unread longer deadline can become the
  shorter converted one, for example `L8` with a 30 s window.

R-007/3 and R-007/4 do not change.

**Code scope, for the later code step.** Only `LockoutManager` changes: the three hooks of B2-A10 and one flag in the
chain record (section 3.1). In the harness, `LockoutModel` gets the B3 rules of section 6, and a new test class holds
the B3 tests. The B3 branch starts from the shared base while B2 is in progress. It is then merged onto B2, and the
tests that need a retry run after the merge.

## 2. Assumptions about B2

B3 depends on these properties of the B2 specification. A change to one of them requires a new check of the B3 rule in
the last column.

| ID | Property of B2 | B3 rule that depends on it |
|---|---|---|
| B2-A1 | Each admitted write runs as a manager job. A caller that cancels `Pending.resolved` changes only its own view. Every admitted write reaches its completion unless the process dies or the manager job is cancelled. A write fails when it returns false, throws a contained exception, or throws a `CancellationException` from the adapter while its job is active. After a failed failure write, the baseline completion arms the fallback. | D1 and D2 run at every completion, so no admitted write escapes eligibility |
| B2-A2 | A chain starts only when a write of the latest admission fails. At an ordinary completion no chain exists. | D1; D2 starts in the same place; the freshness argument of section 5 |
| B2-A3 | The chain keeps its target `PendingWrite`. Prepare builds the payload on the writer, under the manager lock, through `buildRetrySnapshot(target, current)`. B2 returns `target.persist`: count and admission-time deadline for a threshold target, count and rw for a below-threshold target, `(0, 0)` for a reset. | The converted payload extends this payload |
| B2-A4 | A retry is still wanted while the manager has not stopped, its sequence number is current, and `snapshot.revision == target.revision`. Every admission calls `cancel()` inside the admission lock. | Supersession and reset fencing |
| B2-A5 | A failed retry calls `scheduleNext`, so the next delay starts after the attempt finishes. It never runs `applyCompletion`, so it changes no count, fallback, or outcome. Cancellation follows the rule in the threads and locking section of the scheduler specification. | Original expiry (I8); D10 |
| B2-A6 | The completion and the chain decision belong to the manager job. `onResolved` fires once for each admission and never for a retry. A retry never changes an operation outcome. | Accounting (I5) |
| B2-A7 | A committed retry that is still wanted keeps the fallback with its original expiry. For a threshold target it records the admission-time deadlines. The derived degraded flag changes only when storage holds the applicable deadline. A commit after a newer admission or after the stop records nothing. | D7, D8, D9 |
| B2-A8 | The revision alone is the freshness token. It is complete only while no older write is queued (X02). | The freshness argument of section 5 |
| B2-A9 | `shutdown()` cancels a pending retry and ignores a queued one. A running write finishes with nothing published. | D13 |
| B2-A10 | The hooks `startsWriteRecovery`, `buildRetrySnapshot`, and `applyRetryCommit` hold the three candidate rules. B3 changes these functions and adds no second mechanism. | Code scope |
| B2-A11 | Harness: B2 replaces model rule S11 with S11 to S13. Store write indexes count ordinary and retry writes in queue order, and `ScheduleDriver` scripts faults for retry writes. The tests use the production backoff on the virtual timer, which runs on the elapsed clock. `advance(ms)` moves the clocks and then fires due retries, so a retry that falls due inside an advance is prepared at the end time of that advance. `jumpWall()` fires nothing. A kill stops the timer of the dead process. | The predictions of sections 6 and 7 |

## 3. Transitions

### 3.1 Terms and conversion

The notation of the test plan applies: M = (c, rw, rm, f, rev, streak) and D = (c, w). Wf is the wall time of the
failed completion that armed f. For a retry prepared at wall time Wb and elapsed time Eb:

- rem = `remainingOf(f, Eb)`, clamped to 0..`MAX_LOCKOUT_MS`.
- rec = max(`remainingOf(rw, Wb)`, `remainingOf(rm, Eb)`), the recorded remaining of `stateOf`.
- A **gap** exists when rem > rec. This is the condition under which `stateOf` reports `degraded = true`.
- The **converted payload** is (c, max(t, Wb + rem)), where t is the deadline of the B2 payload. Without a gap the
  payload is the B2 payload.
- A **deadline chain** is a chain that B3 starts after a committed write (D2). Its target is already durable. The
  chain record keeps this as a flag.

Properties of the conversion:

1. **Original expiry.** f is an absolute elapsed deadline. No retry changes it (B2-A5). Under stable clocks Wb + rem
   is the same wall instant for every retry, so a retry never restarts the window.
2. **Expiry.** After f, rem is 0, so there is no gap and no converted deadline.
3. **Longer recorded lockout.** With a gap, Wb + rem > rw, because rec ≥ `remainingOf(rw, Wb)`. When the recorded
   window is longer, there is no gap, and the B2 payload keeps its deadline. A converted deadline is never earlier
   than t.
4. **Cap.** rem ≤ `MAX_LOCKOUT_MS`, so a converted deadline is at most 30 min after Wb.
5. **Idempotence.** A repeated write of a converted payload, for example after a false acknowledgement, stores the
   same pair.

### 3.2 Transition table

States and B2 transition IDs follow the B2 specification. The D-rows give the B3 transitions.

| ID | From | Trigger | Guard | Effect | To |
|---|---|---|---|---|---|
| D1 | None | Failure write of the latest admission fails (B2-A1) | none | Baseline completion arms or extends f from the completion time, with no new revision. B2 starts the chain (B2 T2). | Pending |
| D2 | None | Failure write of the latest admission commits | Gap after the baseline completion | `startsWriteRecovery` also returns true: B3 starts a deadline chain at attempt 1, with this write as target. | Pending |
| D3 | None | As D2 | No gap | Baseline completion only (B2 T1). | None |
| D4 | Queued | Writer reaches the retry | Still wanted (B2-A4) and a gap | Prepare builds the converted payload. | Running |
| D5 | Queued | As D4 | Still wanted, no gap, B2 chain | Prepare builds the B2 payload. | Running |
| D6 | Queued | As D4 | Still wanted, no gap, deadline chain | `IGNORED`, no I/O: the target pair is durable and no deadline is due. | None |
| D7 | Running | Retry write returns true | Still wanted, payload converted | `applyRetryCommit` sets rw to the stored deadline and rm to max(rm, f). c, f, rev, and streak keep their values. | None |
| D8 | Running | Retry write returns true | Still wanted, payload not converted | B2 T10. | None |
| D9 | Running | Retry write returns true | Newer admission, or stopped | B2 T11: nothing recorded. The completion of the newer write runs D1, D2, or D3. | None |
| D10 | Running | Retry write fails | Still wanted | B2 T12: next attempt. Memory does not change. The next prepare converts the same f. | Pending |
| D11 | any | f passes | none | Nothing happens at that moment. The next prepare finds no gap (D5 or D6). | unchanged |
| D12 | any | Reset admitted | none | The baseline reset clears c, rw, rm, and f and advances revision and streak. Its admission also cancels the chain (B2-A4). | None |
| D13 | any | `shutdown()`, a cancelled manager job, or process death | none | B2 T18, T4, T14, or T19. A converted payload in its write can still reach storage. Nothing is recorded. | Stopped or None |
| D14 | New process | Seed read of (c, w) | none | Baseline seed: w becomes rw, with no elapsed mirror and no fallback. | None |

### 3.3 Recording and the derived degraded flag

`applyRetryCommit` recognizes a converted payload by a stored deadline that differs from the deadline of
`target.persist`. Under the still-wanted check, f cannot change between prepare and complete. So the rm of D7 ends
exactly where the converted window ends. f is at least the elapsed deadline of a threshold target, so D7 also covers
the B2 record of a threshold target (B2-A7).

After D7, `stateOf` reports `LockedOut(remaining, degraded = false)` with the same remaining as before. The flag then
reports that storage holds the deadline, and nothing else changes. `ApplicationLockEngine` and `LockEngineRuntime`
emit `LOCKOUT_TRIGGERED` and start the capture from the outcome of the original operation. B3 creates no outcome, so
the flag change emits nothing.

### 3.4 Ordinary payloads after recording

No ordinary payload path changes: `seedSnapshot`, `stateOf`, `admitFailure`, `admitReset`, `applyCompletion`,
`opOutcome`, and `EncryptedPrefsLockoutStorage` keep their code. D7 puts the converted deadline into rw, and the
baseline payload rules then carry it. Below, a **B3 deadline** is a converted deadline that D7 has recorded.

The rule: an ordinary write keeps a B3 deadline or replaces it with one that is not earlier in real time. Only a reset
clears it.

- **Below-threshold failure.** The payload is (c, rw), so it carries the B3 deadline unchanged. A new process keeps
  it too, because the seed and the re-seed load the stored deadline into rw.
- **Threshold failure.** The payload is (c, wall time at admission + ladder duration). It replaces rw. Under stable
  clocks this deadline is later than a B3 deadline recorded before it. The admission comes after the completion that
  armed the converted fallback, and the ladder duration is at least the window of that fallback. After a backward
  wall change, the new deadline can be an earlier wall value. It still covers the true remainder of the B3 deadline
  and removes only the excess that the wall change added, as baseline threshold writes do.
- **Reset.** The payload is `(0, 0)`. It clears the B3 deadline with every other deadline, because an accepted
  success ends enforcement (I6).
- **B2 retry.** The `target.persist` of a target admitted after D7 carries the B3 deadline, by the first two rules.
  B3 converts on top of it.
- **Admission before D7.** An ordinary write admitted before the recording carries the older rw. This is the loss
  window of section 1, including an admission during a retry write (D9).

### 3.5 Restart, reboot, and wall clock

- **Restart.** The baseline seed reads (c, w) as the count and the recorded wall deadline. After a B3 commit of
  (1, Wf + T), the new process shows a lockout with the original remainder and a count of 1. The flag reports
  `degraded = false`, as after D7 in the old process. When the window ends, the next wrong PIN counts 2. With healthy
  storage, that PIN does not lock. This is intended. The block on the next attempt after an unpersisted count
  survives the restart and still ends at the original expiry.
- **Stored form.** A count below the threshold with a deadline is new in practice. It uses the existing keys with
  their existing meaning. An older app version enforces it the same way, so no migration and no version are needed.
- **Reboot.** The elapsed clock starts again and the wall clock continues. Enforcement ends at the stored wall
  instant, so the downtime counts against the window.
- **Wall changes.** Before D7 the fallback ignores wall changes (X04). After D7, and after a restart, the lockout
  follows the rules of a recorded lockout. In the running process a forward change does not shorten it, because the
  elapsed mirror holds. After a restart a forward change ends it early. A backward change lengthens it by the size of
  the change, in the running process and after a restart. Access stays blocked until the wall clock reaches the
  stored deadline again. The 30 min cap limits only the reported remaining time, not that deadline. A wall change
  between retries moves the converted deadline with the wall clock at prepare, so a restart at that wall clock keeps
  the exact remainder.
- **Availability regression.** A backward wall change of size J turns a below-threshold remainder r into a lockout of
  r + J, in the running process after D7 and after a restart. The reported remaining time stays at the 30 min cap
  until the actual remainder falls below it. After a one-hour rollback, a 29 s remainder blocks access for 1 h 29 s.
  At baseline only a threshold lockout has this exposure. The experiment keeps the exposure. Each occurrence is
  recorded as an availability regression (X04), measured until the gate is Available again. B3 has no startup clamp,
  because a clamp at the seed would not cover the running process. Before B3 can ship, a clock policy must cover
  running processes, restarts, and reboots.
- **What B3 cannot do.** B3 cannot tell a restart from a reboot. It cannot detect a wall change across a restart, and
  it has no trusted time after a reboot. The C6 boot-aware alternative of the test plan stores a boot identity and an
  elapsed deadline. That needs a new key, which P0 excludes for B3. A recorded window that only the elapsed mirror
  covers after a forward wall change is no gap. B3 does not store it, so its restart loss (X04) stays.

## 4. Retry eligibility, supersession, cancellation, and shutdown

B3 adds these rules to those of B2:

1. **Retries only.** B3 writes the fallback deadline only through retries (section 3.4). The P3 comparison measures
   the loss window of section 1. Variant B3w converts the fallback in the payload of every failure write while a gap
   exists, and it is measured separately. When the fallback exists at admission, B3w closes the window after a count
   commit (D2) and after an admission during a retry write (D9). It cannot close the window after a failed write
   (D1). An admission-time conversion also cannot see a later change of f.
2. **Chain start.** D2 is the only new start condition. Like D1, it fires at an ordinary completion, where no chain
   exists (B2-A2), and it starts at attempt 1.
3. **Healthy path.** D2 never fires without an earlier failed write. On the healthy path the latest committed write
   either records its own threshold deadline, which is the longest fallback of its streak, or belongs to a
   below-threshold streak with no fallback. A stale threshold commit (F5 committed after F6 was admitted) is not the
   latest admission. So B3 adds no write and no chain to the healthy path.
4. **Deadline chain.** A deadline chain is wanted only while a gap exists (D6). It ends at its first run after f. A
   B2 chain continues by the B2 rule and carries no converted deadline after f.
5. **Other lifecycle events.** Supersession, reset, failed retries, cancellation, and shutdown follow B2 (B2-A4,
   B2-A5, B2-A9) and the rows D9 to D13. Section 5 lists their stale results.
6. **Reads.** B3 adds nothing to `currentState()` or `failureCount()`.

## 5. Write ordering and stale results

**Linearization point.** The prepare step of the retry, on the writer, under the manager lock. There the conversion
reads c, rw, rm, and f from one snapshot.

**Freshness rule.** B3 adds no counter. It uses the B2 revision check before I/O and before recording. The revision is
a complete token for the whole B3 snapshot, including the fallback, for these reasons:

- A WRITE chain, from B2 or B3, starts only at the completion of the latest admission (B2-A2). No older write is
  queued then, and FIFO order has run every earlier completion.
- Without an admission, only an ordinary completion (`applyCompletion`) or a retry commit changes f, rw, or rm. No
  ordinary completion can follow while the chain exists, and the retry commit ends the chain.
- The conversion reads f in prepare, not at chain start, so every earlier change of f is in the payload. Between
  prepare and complete, only an admission (new revision) or `shutdown()` (stopped) can change memory.
- The argument depends on B2-A8. A chain that starts with an older write queued would need f and rw in both checks.

| Case | Rule | Result |
|---|---|---|
| Admission after the retry fired | Sequence stale | `IGNORED`. The new write decides. |
| Reset after the retry fired | Sequence stale | `IGNORED`. No deadline of the old streak is written. |
| Late failed completion extends f before the chain starts (X02) | Prepare reads f | The payload holds the extended deadline. |
| Admission during the retry write | Revision changed | Commit with nothing recorded. The newer count-only write overwrites the deadline. Its completion starts the next chain (D1 or D2). |
| Reset during the retry write | Revision changed | Nothing recorded. The clear behind it stores Z. If the clear fails, D keeps the converted deadline until the B2 reset retry commits, and a restart in between enforces it (R-007/4). |
| Retry prepared before f, committed after f | Deadline is the original expiry | Nothing revives, in process or after a restart. |
| False acknowledgement of a converted commit | B2 retries the same payload | The same pair is stored again. |

**Accounting (I5, I8).** A retry creates no outcome and calls no callback (B2-A6). It adds no count and never writes f
(B2-A5). D7 is the only memory change of B3. It changes the degraded flag of the current lockout, not its length.
Each original outcome stays as delivered, including its degraded state.

## 6. Expected outcome and test for each transition

New tests go into a new class `DeadlineRetryTest` in the `r007` package, on `BaselineCase` for its evidence rule. The
model extends the B2 rules S11 and S13 with the transitions D2, D4, D6, and D7. T = 30 s, the start clocks are W0 and
E0, and the failed completion is at time 0 unless a row says otherwise. A holds the original outcomes and callbacks,
and nothing else, unless a row says otherwise. V is unchanged unless a row names it. Each case records
`deadline_loss_window_ms`, the loss window of section 1. The X04 cases also record `availability_regression`.

| Test | Transitions | Schedule | Expected M, D, V, A, R |
|---|---|---|---|
| `R2_1 - after the retry commits a restart keeps the remainder of the fallback` | D1, D4, D7, D14 | Z; write 0 `ReturnFalse`, then `Throw`; advance 1 s, then 4 s; restart | Retry write 1 stores (1, W0+T) at 1 s. M `LockedOut(25 s, degraded = false)` at 5 s. A: (`LockedOut(T, degraded = true)`, 1), 1 callback. R: scheduled, fired, started, ended success. After restart `LockedOut(25 s)` recorded, count 1, gate blocked. Loss window 1 s. |
| `R2_1 - a reboot after the retry commit enforces the stored deadline less the downtime` | D7, D14 | As above; reboot at 5 s with 10 s downtime | Boot 2. `LockedOut(15 s)` recorded, count 1, gate blocked. |
| `R2_1 - a death before the retry commit loses the fallback and a death after it keeps it` | D13 | As above: kill at 500 ms; retry write `HoldBeforeCommit`, then kill; retry write `CommitThenHold`, then kill | First two: D Z, Available, V + 1. Third: D (1, W0+T), recorded remainder after restart, no write in the new process. |
| `R2_2 - a committed count after the failed write starts a retry that stores the fallback` | D2, D4, D7 | Order A: F1 held then false while F2 waits, so F1 is not the latest admission. Order B: F1 false, then F2 cancels the B2 chain. F2 commits (2, 0); advance 1 s, then 9 s; restart | Retry stores (2, W0+T) at 1 s. M `LockedOut(20 s, degraded = false)` at 10 s. A: F1 (`LockedOut(T, degraded = true)`, 1), F2 (Available, 2). After restart `LockedOut(20 s)` recorded, count 2. Loss window 1 s from F2's commit. |
| `R2_2 - ordinary writes after a converted commit keep or replace the stored deadline, and a reset clears it` | D7, D14, D12 | Z; write 0 false; advance 1 s; manager-only F2; restart at 2 s; manager-only F3, F4, F5; manager-only reset | D (1, W0+T), then (2, W0+T). After restart `LockedOut(28 s)` recorded, count 2. F3 and F4 store (3, W0+T) and (4, W0+T). F5 stores (5, W0+32 s), `LockedOut(30 s)` recorded. The reset stores Z. |
| `R2_3 - a retry stores the remainder of a fallback that outlasts an expired threshold commit` | D2, D4, D7 | (3, 0); F4 held 40 s, then false; F5 commits (5, W0+T); advance 1 s; restart | Retry stores (5, W0+70 s). M `LockedOut(29 s, degraded = false)`. A: F4 (`LockedOut(T, degraded = true)`, 4), F5 (`LockedOut(T, degraded = false)`, 5). After restart `LockedOut(29 s)` recorded. |
| `R2_4 - failed retries store the original deadline until it passes and none after it` | D10, D5 | Z; all writes `ReturnFalse`; advance 1, 2, 4, 8, 16 s | Retries at 1, 3, 7, and 15 s carry (1, W0+T). The retry at 31 s carries (1, 0). f stays E0+T: degraded until T, then Available. D Z. After restart Available, gate open. |
| `X08 - adapter cancellations of a failure write and of its retry keep the original deadline` | D1, D10, D7 | Z; write 0 and retry write 1 `ThrowCancellation` with the job active; advance 1 s, then 2 s | The failed write arms f = E0+T, with 1 callback. Retry 1 fails and schedules attempt 2 after 2 s. Retry 2 stores (1, W0+T). M `LockedOut(27 s, degraded = false)`. |
| `X03 - a deadline retry that runs at or after the end of the fallback writes nothing` | D6, D11 | Order B above; `holdIo`; the retry fires at T - 1 ms; resume at T - 1 ms, T, T + 1 ms, or 10 min | Resume at T - 1 ms: stores (2, W0+T), `LockedOut(1 ms)` recorded. Otherwise `IGNORED`, no I/O, D (2, 0), Available. |
| `X03 - a retry write that commits after the fallback ends brings no lockout back` | D4, D7, D11 | Z; write 0 false; retry write 1 `HoldBeforeCommit`; advance 1 s; advance to T + 1 ms; release; restart | Stores (1, W0+T) after the expiry. M Available before and after the commit. After restart Available, count 1. |
| `X03 - a retry keeps a longer recorded deadline` | D5, D8 | (3, W0+10 min), the stored form of a converted deadline after a backward wall change; manager-only failure, write 0 false; advance 1 s | No gap. Retry stores the B2 payload (4, W0+10 min). M `LockedOut(10 min - 1 s)` recorded. |
| `X02 - a fallback extended without a new revision is stored with its extended deadline` | D1, D4 | Z; writes 0 and 1 `HoldBeforeCommit(ReturnFalse)`; F1, F2; release write 0; advance 10 s; release write 1; advance 1 s; restart | No chain while write 1 waits. f moves to E0+40 s without a new revision, then the chain starts. Retry stores (2, W0+40 s). After restart `LockedOut(29 s)` recorded. |
| `X02 - an admission during the retry write blocks the recording and the next chain stores the deadline` | D9, D2, D7 | Z; write 0 false; advance 1 s with retry write 1 held; manager-only F2; release; advance 1 s | Disk order (1, W0+T), (2, 0), (2, W0+T). M degraded until the last commit, then `LockedOut(28 s)` recorded. R: ended abandoned, then a deadline chain that ends in success. |
| `X04 - a wall change between retries moves the stored deadline and keeps the remainder` | D10, D4, D7 | Realistic wall Wr; writes 0 and 1 false; advance 1 s; `jumpWall(+1 h)`; advance 2 s; restart | Retry 1 carries (1, Wr+T). Retry 2 stores (1, Wr + 1 h + T). M `LockedOut(27 s)` recorded. After restart `LockedOut(27 s)` recorded. |
| `X04 - after a retry commit a backward wall change blocks access until the wall clock reaches the stored deadline, in process and after a restart` | D7, D14 | Commit at 1 s; `jumpWall(+1 h)` or `jumpWall(-1 h)`; read the state. For -1 h, two variants: stay in process, or restart at the jump; then advance to 30 min 29 s, to 1 h 29 s - 1 ms, and to 1 h 29 s after the jump | +1 h: `LockedOut(29 s)` recorded in process, Available after restart. -1 h, both variants: `LockedOut(30 min)` recorded until 30 min 29 s after the jump, then counting down; `LockedOut(1 ms)` at 1 h 29 s - 1 ms; Available at 1 h 29 s. `availability_regression` records 1 h 29 s of blocked access against 29 s. |
| `R4_3 - a reset drops a queued retry and its clear follows a running retry` | D12, D9 | Z; write 0 false. Queued run: `holdIo`, advance 1 s, manager-only reset, `resumeIo`. Running run: retry write held, reset, release | Queued: `IGNORED`, D Z. Running: D (1, W0+T), then Z, nothing recorded. M Available and count 0 from the reset on. After restart Available. |
| `R4_3 - a failed clear behind a running retry leaves the stored deadline until the reset retry commits` | D9, D12 | Running run above with the clear `ReturnFalse`; restart at 1.5 s, or advance 1 s | M Available. Restart: D (1, W0+T), `LockedOut(28.5 s)` recorded (R-007/4). Advance: the reset retry stores Z. |
| `X06 - after shutdown a running retry still commits and nothing is recorded` | D13 | Z; write 0 false; advance 1 s with retry write 1 held; shutdown; release; restart | D (1, W0+T). M stays degraded: no recording, no callback, and no rescheduling. R: `ENDED abandoned` when the held retry finishes after the stop, and no other retry event. After restart `LockedOut(29 s)` recorded. |
| `X12 - a retry commit changes only the derived degraded flag` | D7 | Z; write 0 false with a counting callback; advance 1 s | 1 callback, outcome unchanged, count 1. `LockedOut(29 s, degraded = false)`: the remaining of the fallback, with no extension. No second outcome. |
| `R3_4 - a retry after a false acknowledgement stores the same deadline again` | D10, D7 | Z; write 0 false; retry write 1 `CommitThenReportFalse`; advance 1 s, then 2 s | D (1, W0+T) after write 1 and after write 2. Recorded only after write 2. f unchanged. |
| `H06 - healthy writes and a stale threshold commit start no retry` | D3 | The H02, H03, and H06 sequences; C4 with F5 held while F6 is admitted, then released | R: no event. Payloads and write counts as at baseline. |

The changed tests of sections 7.1 and 7.2 also cover D1, D2, D4, D7, and D14, with the fixtures of the baseline and B2
tests.

## 7. Predicted evidence

All results of this section are PREDICTED. Each unpredicted change goes to the lead as a finding.

### 7.1 r007 baseline tests that change against B2 alone

| Class and test | B2 alone | B2+B3 |
|---|---|---|
| `DegradedRestartTest` `R2_1 - a restart erases the degraded fallback of a failed below-threshold write` (B2 name: `R2_1 - a failed below-threshold write is retried, so a later restart keeps its count but not its fallback`) | Death at 5 s, at T - 1 ms, and the reboot at 5 s: in process `LockedOut(T - d, degraded = true)`, D (1, 0), after restart Available, count 1, gate open at once. Death at 0 s: D Z, count 0, Available. | Death at 5 s, at T - 1 ms, and the reboot: in process `LockedOut(T - d, degraded = false)`, D (1, W0+T), after restart `LockedOut(T - d)` recorded (reboot: 24 s), count 1, gate blocked, `lost_enforcement_ms` 0. Death at 0 s unchanged. New name: "after the retry commits a restart keeps the remainder of the fallback, and a death before it loses the fallback". |
| `DegradedRestartTest` `R2_2 - a later committed count keeps the earlier fallback in memory only` | Unchanged from baseline: no chain, D (2, 0), restart at 10 s gives count 2 and Available. | Before the advance unchanged. F2's commit starts a deadline chain, and the retry at 10 s stores (2, W0+T). After restart count 2, `LockedOut(20 s)` recorded. New name: "a later committed count starts a retry that stores the earlier fallback". |

### 7.2 Tests of the B2 specification that change

| Test of `WriteRetryTest` | B2 alone | B2+B3 |
|---|---|---|
| `X03 - a retry of a threshold write that failed after its window persists the expired deadline and revives nothing` | D (5, W0+T), already expired. M degraded 29 s. After restart Available, count 5. | D (5, W0+70 s). M `LockedOut(29 s)` recorded. After restart `LockedOut(29 s)` recorded, count 5. |
| `R3_4 - a retry after a false acknowledgement writes the same pair again and changes nothing in memory` | D (1, 0) before and after. M `LockedOut(T - 1 s, degraded = true)`. | D (1, W0+T) after the retry. M `LockedOut(29 s, degraded = false)`. |
| `R1_3 - a retry of the racing failure write erases the unreadable stored lockout that the baseline keeps` | D (1, 0). After restart Available, count 1. The erased `L5` is the cost of the regression. | D (1, W0+T). After restart `LockedOut(29 s)` recorded, count 1. The unread count 5 is still erased. |

The other B2 tests keep their assertions. In `R2_4`, `X05`, and `I3`, the payloads carry a converted deadline while a
gap exists. `X12` and the running run of `X06` already store (5, W0+T), which equals the conversion. The two
cancellation tests of `WriteRetryTest` involve a reset or no completion, so B3 changes nothing in them.

### 7.3 r007 baseline tests whose assertions B3 does not change

- `HealthyControlsTest`: all 6 tests. No write fails, so D2 never fires, and the traces stay identical.
- `ColdReadTest`: all 17 tests. `R1_3 - when the racing failure write fails, ...` starts a B2 chain, but the restart
  comes before it fires.
- `DegradedRestartTest`: `R2_3` (F5's commit starts a deadline chain, but the restart comes before it fires) and both
  `R2_4` tests. In the expiry test every retry fails. Its one retry carries (1, W0+T) at T - 1 ms and (1, 0) at T and
  T + 1 ms.
- `UndurableDeathTest`: all 14 tests. In `R3_3 - while the first write stalls, ...` with `ReturnFalse`, F6's commit
  records a deadline that covers f, so there is no gap. The `R3_4` false-acknowledgement tests restart before the
  retry.
- `FailedResetTest`: all 7 tests. A reset leaves no fallback. The last wrong PIN of `R4_4` starts a B2 chain with no
  advance after it.
- `CrossCuttingTest`: all 21 tests, with the B2 values of the three X08 tests that B2 changes. In
  `X08 - a failure write that throws a cancellation ...` the commit of the second admission leaves the fallback of the
  first. Its trace gains a scheduled deadline chain, which does not fire before the test ends.
  `X08 - cancelling the resolved Deferred before its write starts ...` writes only healthy pairs, and the clear test
  has no fallback. `X03 - ... stalls past its window and fails ...` and
  `X08 - cancelling the resolved Deferred while its write runs ...` start a B2 chain with no advance. In
  `X04 - a wall change does not move a degraded fallback` the wall jumps fire nothing and every retry fails.
- Outside `r007`, `LockoutManagerTest` and `LockEngineRuntimeTest` get a virtual timer that fires only on request
  (scheduler test support), so no retry runs.

### 7.4 Residual subcases

| Subcase | B2 alone | B2+B3 |
|---|---|---|
| R2.1 | Count durable, fallback lost. Not met. | Met for a death or a reboot after the first converted commit. Not met for a death in the loss window. |
| R2.2 | No chain; as baseline. Not met. | Met after the deadline chain commits, at least 1 s after F2's commit. |
| R2.3 | No chain; as baseline. Not met. | Met after the chain commits: the remainder of F4's completion-time fallback. |
| R2.4 | Not met: one verified guess per restart. | Unchanged under a persistent fault. If the fault ends, a restart after the commit gives no guess until the original expiry. |
| X02, X03 (B3 parts) | Not applicable, or B2 stores only the admission-time deadline (X03). | First run of the B3 oracles in section 6. |
| X04 (B3 part) | Not applicable. | Availability regression of section 3.5: a backward wall change keeps a below-threshold lockout for its remainder plus the size of the change, in process and after a restart. The reported remaining time stays capped at 30 min. |
| X06, X08, X12, X15 | B2 parts. | A converted payload can be durable after the stop. An adapter-cancelled failure write arms a fallback that a retry converts. Only the flag changes. A committed conversion is read once by each later process. |
| R1.3 | Regression: the retry erases the unread stored lockout with (1, 0). | Same regression with (1, Wf + T): the unread count is lost, a 30 s window survives, and an unread longer deadline can be shortened. |
| R3.4 | B2 rewrites (1, 0). | The in-process 30 s window becomes durable. |
| R4.3 | B2 oracles. | Adds the running converted retry before a reset and the R-007/4 window of a failed clear behind it. |
| H01 to H06, R1.1, R1.2, R1.4, R3.1 to R3.3, R4.1, R4.2, R4.4 | B2 results. | Unchanged. |

On the device lanes (from the Moto G report of 2026-10-04), R2.1 to R2.3 change only when the fault hits the first
write alone and the kill comes after the retry commit. The gate then stays blocked with the remainder after the
relaunch. A persistent platform fault (R2.1 with a read-only directory) and R2.4 keep their results.

## 8. Boundary and oracle check

| Boundary or oracle | B3 |
|---|---|
| Never await storage on the runtime drain | No caller waits for a retry. |
| Keep one ordered writer | B3 writes only through B2 retries. |
| Do not hold the admission lock across I/O | The conversion and the recording are CPU work. |
| Stale work must not replace newer state | Prepare reads the payload, and the revision fences the recording (section 5). |
| No fabricated failures, captures, or audit events | A retry creates no outcome and calls no callback (section 5). |
| Never extend the fallback deadline | No retry writes f (section 5). |
| Immediate in-memory reset stays | A reset clears a B3 deadline (section 3.4). |
| P0: B3 writes only the existing keys, with no new key or version | Only `failure_count` and `lockout_until`. The deadline chain flag lives in memory. |
| P0 retry policy | The B2 schedule applies unchanged. |
| P0: `currentState()` starts no storage I/O | Unchanged (section 4). |
| Invariant 6 of `M7_PLAN.md` | No new persistence. A below-threshold count can carry a deadline (section 3.5). |
| I1 responsiveness | No new work on the drain or the main thread. |
| I2 one ordered writer | The linearization point is the prepare step (section 5). |
| I3 short admission section | No I/O and no delay under the lock. |
| I4 freshness | Section 5. Tested by X02. |
| I5 event accounting | Original outcomes and callbacks unchanged. Tested by X12 and X08. |
| I6 immediate reset | Tested by the R4_3 tests and the ordinary-payload test of R2_2. |
| I7 honest lifecycle | D13 and the cancellation rule of the scheduler specification apply. A converted commit that reaches storage governs the next process. |
| I8 deadline integrity | Properties 1 to 4 of section 3.1. Tested by R2_4 and X03. The wall-clock regression of section 3.5 does not come from recovery. |
| I9 callers and biometrics | No caller change. A stored below-threshold lockout reads as a recorded lockout. |
