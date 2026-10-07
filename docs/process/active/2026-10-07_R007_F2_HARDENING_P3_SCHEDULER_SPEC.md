# M7 F2 hardening: P3 recovery scheduler specification

**Date:** 2026-10-07

**Status:** Ready, reviewed by the lead on 2026-10-07, together with the B1, B2, and B3 specifications.

**Scope:** The shared retry scheduler of the P3 base branch and its test support. The candidate specifications define
when a retry starts, what it does, and when it ends.

## 1. Purpose

B1 retries a failed storage read, and B2 retries a failed write. B3 extends the B2 retry. The scheduler gives all
three one mechanism for the delay, the queueing, and the lifecycle of a retry. The candidates then differ only in
their rules. The mechanism follows the P0 retry policy in the F2 hardening entry of `M7_PLAN.md`.

## 2. Components

| Component | Role |
|---|---|
| `RecoveryScheduler` | Holds at most one pending retry for one manager. It assigns sequence numbers, computes delays, and runs the three steps of a retry on the writer (section 5) |
| `RecoveryTimer` | Calls a task once after a delay, on a thread that is not the writer. The production timer (section 7) and the virtual test timer (section 8) implement it |
| `RecoveryBackoff` | Maps the attempt number of a chain to its delay (section 4) |
| `RecoveryAction` | The three steps of one retry, supplied by a candidate: `prepare`, `perform`, and `complete` |
| `RecoveryListener` | Receives the lifecycle events of each retry. The harness writes them to its ledger as test evidence. Production keeps a listener that does nothing (lead decision of 2026-10-07), so recovery creates no authentication audit event. Production diagnostics would need a separate change with a defined purpose |
| `RecoverySetup` | Bundles the timer, the backoff, and the listener for the new constructor parameter of `LockoutManager` |

All components are pure Kotlin in the `security` package, next to `LockoutManager.kt`.

## 3. Retry chains and sequence numbers

A retry chain is the series of retries for one recovery need, for example one failed reset. A candidate starts a
chain with `start(kind, action)`. After a failed attempt, the `complete` step asks for the next retry with
`scheduleNext(sequence)`, and the attempt number increases by one. A chain ends when a step reports success or that
the chain is no longer wanted. It also ends when the candidate calls `cancel()`, when a newer `start` replaces it, or
when the manager calls `stop()`.

Every scheduled retry gets the next sequence number of its manager. Only the most recently scheduled number is
current. `start`, `cancel()`, and `stop()` make the previous number stale. The states of the table belong to the
current number.

| State | Trigger | Next state | Listener event |
|---|---|---|---|
| Idle | `start(kind, action)` | Pending(n) | `SCHEDULED n kind attempt=1 delay` |
| Pending(n) | The timer fires | Queued(n) | `FIRED n` |
| Pending(n) or Queued(n) | `start` for a new chain | Pending(n+1) | `CANCELLED n replaced`, then `SCHEDULED n+1` |
| Pending(n) or Queued(n) | `cancel()` | Idle | `CANCELLED n cancelled` |
| Queued(n) | The writer reaches the task, n is current, and `prepare` returns no operation | Idle | `IGNORED n not-wanted` |
| Queued(n) | The writer reaches the task, n is current, and `prepare` returns an operation | Running(n) | `STARTED n` |
| Running(n) | `start` for a new chain | Pending(n+1); run n continues without ownership | `SCHEDULED n+1` |
| Running(n) | `cancel()` | Idle; run n continues without ownership | none |
| Running(n) | `complete` reports success while n is current | Idle | `ENDED n success` |
| Running(n) | `complete` calls `scheduleNext(n)` while n is current | Pending(n+1) | `ENDED n retry`, then `SCHEDULED n+1 attempt+1 delay` |
| Running(n) | `complete` reports that the chain is no longer wanted while n is current | Idle | `ENDED n abandoned` |
| Running(n) | The adapter throws a `CancellationException` while the manager job is active and n is current | Pending(n+1) | `ENDED n retry`, then `SCHEDULED n+1 attempt+1 delay` |
| Running(n) | The manager job is cancelled during `perform` while n is current | Idle | `ENDED n threw` |
| Any | `stop()` | Stopped; a running retry continues without ownership | `CANCELLED n stopped` for a pending or queued retry |
| Any state of a newer owner | The writer reaches a queued task n without ownership | Unchanged. `prepare` is not called | `IGNORED n stale` |
| Any state of a newer owner | A run n without ownership finishes `perform`, with any result | Unchanged. `complete` is not called | `ENDED n abandoned` |
| Stopped | `start` or `scheduleNext` | Stopped | none (refused) |
| Any | Process death | none | none: the timer, the queue, and the running step die with the process |

A run whose number became stale never changes the state, as the two rows with a newer owner show. Its I/O still
finishes, because blocking I/O cannot be interrupted safely. A `scheduleNext` with a stale number does nothing.

## 4. Delays

`RecoveryBackoff` with the P0 production values gives attempt 1 a delay of 1 s and doubles the delay for each
attempt, up to the 60 s cap that attempt 7 reaches. Every later attempt waits 60 s. The policy has no attempt limit
(lead decision of 2026-10-07). A chain continues until it ends as section 3 describes, so recovery still happens
after a long fault.

Each delay starts when the previous attempt has finished, so a stalled operation never gets an overlapping retry. A
new chain starts again at attempt 1, and a restarted process starts with no chain. "One operation every 60 s"
therefore describes an unchanged chain at its cap, not a rate limit of the manager.

The 60 s cap is the maximum scheduled delay, not a bound on recovery time. Queueing behind other writes, a stalled
operation, or device sleep (section 7) can make a retry run later.

A JVM test may construct a shorter backoff. The harness then writes `backoff=test` with its values into the evidence
file of the case. With the virtual timer, production delays cost no test time, so a shorter backoff is rarely needed.

## 5. Threads and locking

The wait runs on the timer thread. When the delay ends, the firing appends one task to the writer queue
(`ioDispatcher`, the single `lockout-io` thread) and returns. The task joins the queue behind the writes already
there. Storage therefore keeps a single writer, and a retry runs after every write that was admitted before it fired.

The firing takes no manager lock. It carries its own sequence number and neither reads nor changes a scheduler field.
A storage operation held on the writer, or a callback parked with the manager lock, therefore cannot delay it.
Pending and Queued differ only in where the task waits.

On the writer, the task runs three steps:

1. Under the manager lock, the scheduler checks the sequence number. It then calls the candidate's `prepare`, which
   decides whether the retry is still wanted and returns the operation to perform or nothing.
2. Without a lock, `perform` does the storage read or write. A storage fault becomes a failed result, as in the
   existing `runWrite` and `runRead`.
3. Under the manager lock, the scheduler checks again that the run owns the state (section 3). Only then does
   `complete` apply the result and either end the chain or call `scheduleNext`.

`prepare` and `complete` do no I/O, so no thread holds the manager lock while it does storage I/O. No thread holds it
during a delay either. `start`, `scheduleNext`, `cancel()`, and `stop()` run under the manager lock. They only update
scheduler fields and call the timer, which never blocks.

One cancellation rule applies to every retry kind (lead decision of 2026-10-07), and the scheduler applies it around
`perform`. A `CancellationException` that the storage adapter throws while the manager job is active is an
unsuccessful storage operation. `complete` receives it as a failed attempt, and the backoff applies. A cancellation
of the manager job itself ends the work, and no retry follows. A storage failure never becomes an authentication
failure. The scheduler clears its running state in every case, so no flag can stay set.

`currentState()` never starts a storage read through the scheduler.

## 6. Two kinds of retry in one slot

Each chain has a kind: `READ` for B1, `WRITE` for B2 and B3. `pendingKind()` reports the kind of the pending or
running chain. The scheduler holds one pending retry, so a `start` of one kind replaces a pending retry of the other
kind. A branch with a single kind never meets this case. The B1+B2 branch decides which kind wins and when a `start`
must check `pendingKind()` first.

## 7. Production timer

The production timer is one daemon thread named `lockout-retry`, behind a scheduled executor that drops cancelled
tasks. The thread starts at the first `schedule` call, so a manager that never retries never starts it. `stop()`
shuts the executor down. The executor measures delays with the monotonic clock of the JVM, which pauses during deep
sleep on Android. A retry can therefore fire later than its nominal delay. No correctness rule depends on the exact
delay.

## 8. Test support

**Virtual timer.** `VirtualRecoveryTimer` keeps each task with a due time on the elapsed clock of `VirtualClocks`. It
runs due tasks in due-time order, on the thread that asks, and never runs a cancelled task. Due times use the elapsed
clock, so `jumpWall()` fires nothing.

**Harness.** `LockoutHarness` gives each new process its own virtual timer and a listener that writes to the ledger.
`advance(ms)` pauses the executor, moves the clocks, and fires every task that is due at the new time. Firing never
waits for a storage hold or a callback hold to end. `advance` then resumes the executor and settles to the existing
quiescent state: the executor is idle or held between tasks, or its worker is parked at a storage hold or in a parked
callback. A test can therefore let a retry fall due while an earlier write is held, and then observe the retry run
after the release.

A retry fires at the end time of the `advance` call that reaches its due time. A test that needs exact firing times
advances in steps that end at the due times. `kill()` stops the virtual timer of the dead process, so a dead process
never fires.

**Ledger.** The listener writes each event of the table in section 3 as a harness action through `Ledger.action`, for
example `RETRY_SCHEDULED seq=3 kind=WRITE attempt=2 delay=2000`. The ledger needs no new event type. A run without
retries produces the same trace as before.

**Direct manager tests.** `LockoutManagerTest` and `LockEngineRuntimeTest` construct managers without the harness.
At the base they keep the production default, because no retry starts. A candidate branch passes them a virtual
timer through `RecoverySetup` and fires it on request, so that no real `lockout-retry` thread runs inside a test.

**Model.** `LockoutModel` gets rule S11: the baseline schedules no retry. `check()` compares the number of retry
events of the live process with the number that the model expects, which is zero at the baseline. A candidate branch
replaces S11 with its own retry rules.

## 9. What the base changes and what stays

The base adds the components of section 2, the `RecoverySetup` constructor parameter, a call of `stop()` from
`shutdown()`, and the test support of section 8. The constructor parameter has a production default, so
`AppModule.kt` and every caller stay unchanged.

No code path of the base starts a retry, and the base creates no thread at run time. The base keeps the
poll-triggered re-seed, which B1 replaces, and every write and completion rule. The P2 baseline tests in the `r007`
package stay unchanged and must pass. The ledger traces of their evidence files stay identical.

## 10. Tests of the base

| Test | Expected result |
|---|---|
| Delay sequence | Attempts 1 to 8 wait 1, 2, 4, 8, 16, 32, 60, and 60 s |
| Single pending retry | A second `start` cancels the first; only the second fires |
| Stale retry after replacement | A retry queued before a newer `start` reaches the writer and is ignored without running `prepare` |
| Stale retry after cancel | A retry queued before `cancel()` is ignored |
| Replacement during running I/O | A `start` while retry n is held in `perform` schedules n+1. When n finishes, it records `ENDED n abandoned` without a `complete` call, and n+1 stays pending and fires at its own delay |
| Cancel during running I/O | After a `cancel()` while retry n is held in `perform`, n finishes with `ENDED n abandoned`, the state stays Idle, and nothing is scheduled |
| Stop | `stop()` cancels a pending retry and refuses later `start` and `scheduleNext` |
| Stop during running I/O | After a `stop()` while retry n is held in `perform`, n finishes its storage operation with `ENDED n abandoned` and no publication, and the state stays Stopped |
| Stale `scheduleNext` | `scheduleNext` with a sequence number that is no longer current schedules nothing |
| Wait off the writer | While a delay is pending, the writer runs other tasks; the production timer fires on `lockout-retry`, never on `lockout-io` |
| Queue order | A fired retry runs after the writes queued before the firing |
| Firing during a storage hold | A retry that falls due while an earlier write is held joins the queue behind that write and runs only after the write is released |
| Firing during a parked callback | A retry fires and queues while a completion callback holds the manager lock |
| Adapter cancellation | A `CancellationException` from the adapter with the job active reaches `complete` as a failed attempt and schedules the next delay |
| Job cancellation | A cancellation of the manager job during `perform` ends the chain, and the scheduler returns to Idle |
| Lazy production thread | A manager that never starts a chain creates no `lockout-retry` thread |
| Baseline unchanged | The `r007` suite, `LockoutManagerTest`, and the harness tests pass without change, and S11 holds in every case |

## 11. Boundaries

| Boundary or oracle | Consequence for the scheduler |
|---|---|
| No storage wait on the runtime drain (I1) | No caller waits: the delay runs on the timer thread, and the steps run on the writer (section 5) |
| One ordered writer (I2) | Every retry joins the existing writer queue (section 5) |
| Short admission section (I3) | Nothing under the manager lock waits or does I/O (section 5) |
| Freshness (I4) | A stale run changes no state (section 3). The candidates check state freshness in `prepare` and `complete` |
| Event accounting (I5) | The scheduler creates no authentication event. A candidate must not route a retry through the failure or reset admission |
| Immediate reset (I6) | The scheduler never changes manager memory; only the candidate steps do |
| Honest lifecycle (I7) | After `stop()`, running I/O finishes without effect, and new retries are refused (section 3) |
| Deadline integrity (I8) | The scheduler changes no deadline |
| Callers (I9) | No caller changes in the base (section 9) |
