# M7 F2 hardening: P3 candidate B1 specification (cold-start read recovery)

**Date:** 2026-10-07

**Status:** Ready, reviewed by the lead on 2026-10-07, together with the scheduler, B2, and B3 specifications.

**Scope:** The document specifies candidate B1 of phase P3. B1 retries the cold-start read after the construction
read of `LockoutManager` fails. The retries run on the recovery scheduler of
`2026-10-07_R007_F2_HARDENING_P3_SCHEDULER_SPEC.md` (the scheduler spec). B1 changes no caller, no stored format, and
no write or completion rule.

**Inspected baseline:** `c47d642` on `main`. Code facts are INSPECTED at that revision, P2 results are HISTORICAL, and
every B1 result in this document is PREDICTED, with the evidence labels of `2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`
(the test plan).

## 1. Purpose and limits

At the baseline, a construction read that throws leaves the manager with an empty snapshot that answers Available.
The manager reads storage again only when a caller reads `currentState()`. This document calls that read the re-seed.
The P2 runs in `2026-09-28_m7-wp2-f2h-p2-jvm-baseline_2012-i7.md` (the JVM report) and
`2026-10-04_m7-wp2-f2h-p2-device_moto-g-2025.md` (the Moto G report) showed these weaknesses of the re-seed:

- Without a state read, no read runs. `L5` stayed unenforced for 29.999 s on the JVM and for 13.8 to 14.5 s on the
  Moto G detector path (R1.1).
- Each state read starts one read when none runs, with no backoff: 4 reads per second at the 250 ms self-gate poll,
  and 1000 reads for a burst of 1000 polls (R1.2, X05).
- The re-seed read runs on the writer thread, so a held read blocks the queued writes (R1.4).
- A `CancellationException` from the read leaves `reseedInFlight` set, and no later re-seed starts in that process
  (R1.4).
- A re-seed read queued before `shutdown()` still runs after it (X06).

B1 replaces the re-seed with a READ retry chain of the scheduler. Section 3 lists the transitions of the chain, and
section 4 gives the rules for its start and its end. `currentState()` becomes a pure read of the published snapshot.
A recovery read is the storage read of one B1 retry.

B1 targets residual R-007/1 (cold-start read failure) under transient faults. These limits remain:

- B1 loads the stored lockout once a recovery read succeeds. Until then, and for the whole process under a persistent
  read fault, the gate stays open. The open interval lasts at least 1 s. After a fault ends, it lasts until the next
  retry runs. The scheduler plans that retry at most 60 s after the previous one. Queueing, a stalled operation, or
  device sleep can make it run later.
- When a fault ends between two retries, B1 loads the lockout later than a polling caller does at the baseline. In
  R1.2d the gate stays open for 15 s, against 10 s at the baseline (section 7.4). B1 keeps the P0 schedule and adds no
  caller hint for an earlier retry, because a hint would add a second scheduling policy.
- Each restart under a persistent read fault still gives 5 verified guesses (R1.2e).
- A local failure or reset ends recovery (section 4). The stored counters then stay unknown to the process. Its next
  committed write replaces the stored pair (R1.2, R1.3). B1 does not merge the stored and the local counters.
- A held recovery read delays the durable commit of the writes that the process admits after its first local
  mutation (R1.4c, section 5). The read stays on the writer thread for P3. A separate read executor would change the
  storage concurrency and needs its own design and tests.
- A read that returns wrong data without a throw counts as a successful read. A damaged store file that reads as
  `(0,0)` (X16) therefore starts no recovery.
- The construction read does not change. It runs on the constructing thread and can hold it (R1.4a, R1.4b). A
  `CancellationException` from it still makes the constructor throw.

## 2. Changes to `LockoutManager`

- An `init` block after the snapshot seed starts the READ chain when the construction read threw a storage fault (T2).
  A healthy construction read starts no chain, so the production timer creates no thread.
- `currentState()` no longer calls `triggerReseed()` (T17). B1 removes `triggerReseed()`, `reseedInFlight`, and
  `runRead()`. The recovery action below replaces them.
- The admission paths of `submitFailure` and `submitSuccess` keep `wantReseed = false`. They also call `cancel()`
  under the same lock (T15).
- `shutdown()` keeps the `stop()` call of the base branch (T16).
- `wantReseed` and `seedFailed` keep their names. After construction, only code under the manager lock reads or writes
  `wantReseed`. `seedReadFailed()` keeps its meaning and stays true after a recovery read is applied.
- The public API, the stored keys, the callers, and `AppModule.kt` stay unchanged.

The recovery action supplies the three steps of a scheduler retry:

| Step | Thread and lock | B1 rule |
|---|---|---|
| `prepare` | Writer, manager lock | Returns the read while the retry is still wanted (T6). Otherwise it returns nothing (T7). No I/O. |
| `perform` | Writer, no lock | Calls `storage.read()` and returns a storage fault as a failed read. The cancellation rule in the threads and locking part of the scheduler spec gives T10 and T11. |
| `complete` | Writer, manager lock | Applies a returned pair and ends the chain (T8). Calls `scheduleNext` after a failed read (T9, T10). Ends the chain without a change when the retry is no longer wanted. No I/O. |

Applying a pair sets the snapshot that a successful construction read would have set: the stored count, the stored
wall deadline as the recorded deadline, no elapsed mirror, no fallback, revision 0, and streak 0. It also clears
`wantReseed`.

**Still wanted.** A B1 retry is still wanted while all four conditions hold:

1. Its sequence number is current.
2. The manager has not stopped.
3. `wantReseed` is true.
4. The snapshot revision is 0.

The scheduler checks the first two before `prepare`. `wantReseed` turns true at a failed construction read. It turns
false at the first admitted failure or reset, or when a recovery read is applied. Only an admission changes the
revision, so condition 4 repeats condition 3. B1 checks both, so that a later change to the admission code cannot
break the freshness rule unnoticed. While a retry is still wanted, the snapshot is the empty construction snapshot,
because only admissions and their write completions change it.

## 3. Transition table

States: **Seeding** (the construction read runs, no manager exists), **Trusted** (the snapshot comes from a successful
read, no chain), **Pending(n)** (retry n waits on the timer), **Queued(n)** (retry n waits in the writer queue),
**Reading(n)** (the recovery read runs on the writer without a lock), **Superseded** (a local failure or reset made
the snapshot authoritative, no chain, the stored history is unknown), and **Stopped**.

| ID | From | Trigger | Guard | To | Effect |
|---|---|---|---|---|---|
| T1 | Seeding | The construction read returns a pair | None | Trusted | Snapshot from the pair. No chain and no timer thread. |
| T2 | Seeding | The construction read throws a storage fault | None | Pending(1) | Empty snapshot. `seedFailed` and `wantReseed` true. `start(READ)` with attempt 1 (1 s). |
| T3 | Seeding | The construction read throws a `CancellationException` | None | No manager | The constructor throws, as at the baseline. |
| T4 | Seeding | The construction read does not return | None | Seeding | No manager exists, as at the baseline. |
| T5 | Pending(n) | The delay ends | None (the firing takes no lock) | Queued(n) | The retry joins the writer queue behind the tasks already there. |
| T6 | Queued(n) | The writer reaches the retry | Still wanted | Reading(n) | `prepare` returns the read. The read starts without a lock. |
| T7 | Queued(n) | The writer reaches the retry | n stale, stopped, or not wanted | Superseded or Stopped | No read. `IGNORED`. |
| T8 | Reading(n) | The read returns a pair | Still wanted | Trusted | Apply the pair (section 2). `ENDED success`. |
| T9 | Reading(n) | The read throws a storage fault | Still wanted | Pending(n+1) | No state change. `scheduleNext` with the next delay of the scheduler spec: 1, 2, 4, 8, 16, 32 s, then 60 s, from the end of the read. |
| T10 | Reading(n) | The read throws a `CancellationException`, manager job active | Still wanted | Pending(n+1) | As T9. |
| T11 | Reading(n) | The read throws a `CancellationException`, manager job cancelled | None | Chain ended | No state change and no retry. The cancelled scope runs no later task. |
| T12 | Reading(n) | The read returns or throws | n stale (an admission during the read called `cancel()`) | Superseded | The scheduler ends the run with `ENDED abandoned` and calls no `complete`. The result is discarded. No retry follows. |
| T13 | Reading(n) | The read returns or throws | n stale after `stop()` | Stopped | As T12. The state stays Stopped. |
| T14 | Reading(n) | The read does not return | None | Reading(n) | No retry falls due. Later writes wait behind the read (section 5). Enforcement continues from memory. |
| T15 | Pending, Queued, or Reading | A local failure or reset is admitted | Not stopped | Superseded | Under the lock: `wantReseed = false`, `cancel()`, then the baseline admission. |
| T16 | Any live state | `shutdown()` | None | Stopped | `stopped = true` and `stop()`. A pending or queued retry is cancelled. A running read continues (T13). |
| T17 | Any | `currentState()`, `failureCount()`, `seedReadFailed()` | None | Same | No I/O, no lock, no scheduler call. |
| T18 | Any | Process death | None | None | Timer, queue, chain, and memory are lost. B1 wrote nothing. The next process starts at Seeding, with a new chain at 1 s if its read fails. |
| T19 | Trusted or Superseded | Any later event | None | Same | B1 starts no new chain in that process. |

## 4. Eligibility, supersession, cancellation, and shutdown

**Eligibility.** Only a construction read that throws a storage fault starts a chain, once for each process. A failed
recovery read continues that chain. No other event starts a chain or a read. A state read, a poll burst, and a UI
recreation do nothing (P0 decision). The number of recovery reads therefore depends on the time since the failed
construction read, not on the number of state reads. With instant reads, the retries of one chain fall due at 1, 3, 7,
15, 31, and 63 s, and then every 60 s.

**One read at a time.** The chain asks for its next retry only in `complete`, after its read has ended. While a read
runs or holds, no second recovery read can therefore join the queue (T14). Under a permanent fault, an unchanged chain
at its cap makes one recovery read every 60 s for the life of the process. Each restart starts a new chain at 1 s, so
that figure is not a rate limit of the manager. A retry can succeed after a transient keystore fault. The lazy
initialisation of `EncryptedPrefsLockoutStorage` runs again after a throw, because Kotlin `lazy` keeps no failed value
(INSPECTED).

**Supersession.** The first admitted failure or reset ends recovery (T15). The admission clears `wantReseed` and calls
`cancel()` under the admission lock. That lock section is the linearization point. A retry that the writer reaches
later performs no read (T7). A read that runs across that point is discarded (T12).

**Cancellation.** B1 needs no in-flight flag, because the scheduler clears its running state after every `perform`,
also after a throw. The cancellation rule of the scheduler spec gives T10 and T11. Production never cancels the
manager scope, so T11 occurs only in a test that cancels the scope.

**Shutdown.** After `shutdown()`, no recovery read starts (T7) and no recovery result is published (T13). A read that
already runs finishes its I/O.

**Process death.** B1 keeps its state in memory and writes nothing. A restart therefore starts a new chain at 1 s
(T18). Each restart costs one construction read, so the restart rate bounds that cost.

**Interaction with B2.** The B1+B2 branch decides which kind wins the single slot of the scheduler. B1 needs these
three rules from that branch:

1. An admitted local failure or reset ends B1 recovery. The admission clears `wantReseed`. It cancels a pending,
   queued, or running READ chain before it starts any WRITE chain for its own write.
2. A READ chain never replaces a WRITE chain. B1 starts its chain only during construction, before any admission, so
   no WRITE chain exists at that time.
3. A WRITE retry never applies a read result. A READ retry never writes.

In a B1-only branch, `cancel()` at admission needs no kind check, because no WRITE chain exists.

## 5. Write ordering and stale results

**Where the read runs.** The recovery read runs on the `lockout-io` writer, behind every task queued before the
firing (scheduler spec, threads and locking). Storage therefore keeps one thread for all operations (I2). While
recovery is wanted, the process has admitted no mutation, so no write of the process waits in the queue when a retry
fires.

**Effect on queued writes.** A write joins the queue behind a recovery retry only after an admission has ended
recovery. If the retry has not started, the writer skips it without a read (T7). The write then waits only for an
empty task. If the read already runs, the write waits until the read returns (R1.4c). Admissions still publish their
state in memory at once, so enforcement does not wait. Only the durable commit waits.

**Locks and checks.** No B1 step holds the manager lock during I/O (I3), and `currentState()` takes no lock. `prepare`
tests the still-wanted rule before the read. An admission between the read and the result calls `cancel()`, which
makes the run stale. The ownership check of the scheduler then ends the run without `complete`, and the result is
discarded (T12). `complete` tests the still-wanted rule again, as a second guard, before it applies a result.

**Stale results (I4).** A read result that returns after a local failure or reset never replaces the newer state. The
admission has cleared `wantReseed` and advanced the revision under the lock before the result arrives. A retry that is
still wanted finds the empty construction snapshot (section 2). Applying a result therefore never overwrites a count,
a fallback, or a recorded deadline of this process.

**No durability claim.** B1 writes nothing. A recovery read returns the process cache of the store. That value shows
what storage held at its first successful load. It is not evidence of a durable commit.

## 6. Expected outcome and test for each transition

The B1 branch changes the harness as follows:

- `LockoutModel` replaces rule S10 (a poll starts a re-seed read) and the scheduler spec's rule S11 (no retry) with
  one B1 rule. A poll starts no read. After a failed construction read, the first retry is due 1 s later. A retry
  fires when `advance` reaches its due time. Its read runs only while the request stands and the manager runs. An
  applied read sets the state as rule S1 does. A failed read, also a cancellation that storage throws while the
  manager job is active, schedules the next retry at the next P0 delay from the end of the read. A read whose request
  ended changes nothing and schedules nothing. Rule S4 also cancels the pending retry. The model computes each due
  time from the P0 delays and checks each `RETRY_` ledger event against it.
- `LockoutHarness.check()` compares `currentState()` with the model in every state, because the call starts no read.
  The model still has no rule for a stopped manager, so the X06 tests keep their direct assertions.
- Every test uses the production delays. The virtual timer fires a due retry at the end of an `advance` call, so a
  test that measures a firing time advances in steps that end at the due times.
- The KDoc of `ReadScript.ThrowCancellation` in `FaultPlan.kt` says that the model rules do not cover the script. On
  the B1 branch, the manager treats that cancellation as a failed read. The model already does so for a `THREW`
  event, and the KDoc changes to say so.

Notation: M is the count and the public state, D the durable pair, V the verifier entries of `GatedCaller`, A the
admissions (recovery creates none), and R the retry events of the ledger. "Reads" counts the storage reads of the
first process, with the construction read. Times count from the process start at W0 and E0. Tests are in
`ColdReadTest` unless a class is named.

| Transition | Test | Expected M / D / V / A / R |
|---|---|---|
| T1 | `HealthyControlsTest`: `H01 - polls of a healthy zero store read once and write nothing` (unchanged) | Count 0, Available / `Z` / na / none / no retry event; reads 1 |
| T2, T5, T6, T8, T19 | `R1_1 - without a poll the recovery retry at 1 s loads the stored lockout` (changed) | Available up to 999 ms, then count 5 and `LockedOut(T - 1 s)` recorded / `L5` / V = 0 at T - 1 ms / none / `SCHEDULED` attempt 1 delay 1000, `FIRED`, `STARTED`, `ENDED success`, no event in the next 10 min; reads 2; the read runs on `lockout-io-g1` |
| T3 | `R1_4 - a construction read that throws a cancellation makes the construction throw` (unchanged) | No manager / `L5` / na / none / none |
| T4 | `R1_4 - a held construction read leaves no manager until it returns` (unchanged) | As at the baseline; the throw variant starts a chain that does not fire in the test |
| T7, after an admission | `R1_3 - a recovery retry that waits in the queue skips its read after a failure` (changed) | Count 1 / `(1,0)` / na / one failure / `FIRED`, `CANCELLED cancelled`, `IGNORED`; reads 1 |
| T7, after a stop | `CrossCuttingTest`: `X06 - a recovery retry queued before shutdown skips its read, and no retry follows` (changed) | Count 0, Available / `L5` / na / none / `FIRED`, `CANCELLED stopped`, `IGNORED`; reads 1, also after 10 min |
| T9 | `R1_2 - under a persistent read fault the state stays Available and the reads follow the backoff` (changed) | Count 0, Available / unchanged / na / none / failed reads at 1, 3, and 7 s; reads 4 at 10 s; 0 writes |
| T9, fault ends | `R1_2 - when a persistent read fault ends, the next due retry loads the stored lockout` (changed) | Available up to 15 s, then `LockedOut(T - 15 s)` recorded / `L5` / na / none / reads 5 |
| T9, delay cap | `CrossCuttingTest`: `X05 - under a persistent read fault polls start no read, and the retries back off to one every 60 s` (changed) | Count 0, then 5 at 603 s / `L5` / na / none / 0 reads for 1000 polls; 14 retries in 10 min with delays of 1, 2, 4, 8, 16, 32 s, then 60 s; the retry at 603 s applies `L5` |
| T10 | `R1_4 - a recovery read that throws a cancellation counts as a failed read, and the next retry loads the stored lockout` (changed) | Available up to 3 s, then count 5 and `LockedOut(T - 3 s)` / `L5` / na / none / `ENDED retry` at 1 s, `ENDED success` at 3 s; reads 3 |
| T11 | `LockoutManagerTest`: `a recovery read that throws a cancellation after its scope was cancelled ends the chain` (new) | Count 0, Available / unchanged / na / none / no pending retry after the throw |
| T12, failure | `R1_3 - an old recovery value is not applied after a failure, and the failure write replaces the lockout` and `R1_3 - when the racing failure write fails, the stored lockout returns after a restart` (both changed) | Count 1 / `(1,0)`, or `L5` when the write fails / na / one failure / `ENDED abandoned`; after a restart Available, or `LockedOut(T - 1 s)` recorded |
| T12, reset | `R1_3 - an old recovery value is not applied after a reset` (changed) | Count 0, Available / `Z` / na / one reset / `ENDED abandoned` |
| T12, T14 | `R1_4 - a held recovery read blocks every queued write while admissions enforce from memory` (changed) | Count 5, `LockedOut(T)` degraded, Available after 40 s / `L5` until the release, then `(5, W0 + 1 s + T)` / V = 5 / five failures / no retry after the release |
| T13 | `CrossCuttingTest`: `X06 - a recovery read in flight at shutdown finishes unpublished, and no retry follows` (changed) | Count 0, Available / `L5` / na / none / `ENDED abandoned`; reads 2, also after 10 min |
| T14 | `R1_1 - polls start no read, and no retry falls due while a recovery read is held` (changed) | Available / `L5` / na / none / no `FIRED` during the hold or a 10 min advance; the next retry 2 s after the release; reads 2, then 3 |
| T14, I8 | `R1_4 - a recovery read released after the stored window ended loads the count but no lockout` (new) | Count 5 and Available after the release at 41 s; the next gated wrong PIN gives `LockedOut(60 s)` recorded / `(6, W0 + 101 s)` / V = 1 / one failure / `ENDED success` |
| T15 | `R1_3 - a failure or a reset admitted while a retry is pending cancels it, and no recovery read follows` (new) | Count 1 or 0 / `(1,0)` or `Z` / na / one admission / `SCHEDULED`, `CANCELLED cancelled`, no event in 10 min; reads 1 |
| T16 | `CrossCuttingTest`: `X06 - shutdown cancels a pending recovery retry, and no read follows` (new) | Count 0, Available / `L5` / na / none / `CANCELLED stopped`; reads 1 after 10 min |
| T17 | `R1_1 - with 250 ms polls no poll reads, and the retry at 3 s loads the stored lockout` (changed) | Available at 1 s, `LockedOut(T - 3 s)` at 3 s / `L5` / na / none / the retry at 1 s fails, the retry at 3 s applies; reads 3 |
| T18 | `R1_2 - a restart while a retry is pending starts a new chain at 1 s, and the dead process never fires` (new) | Count 0, Available / `L5` / V = 0 / none / no g1 retry event after `KILL`; g2 `SCHEDULED` attempt 1 delay 1000, `FIRED` at 1 s, then attempt 2 delay 2000 |
| T18 | `R1_2 - under a persistent read fault each restart gives five new verified guesses` (unchanged) | 5 entries in each of 21 processes; healthy control 5, then 0 |

## 7. Predicted evidence

### 7.1 Baseline tests in `r007` that B1 changes

On the B1 branch, each changed test replaces the baseline test of the same case. The baseline branch keeps the P2
version, so the P3 comparison runs both arms.

| Class: baseline name, then B1 name | Baseline expected | B1 expected |
|---|---|---|
| `ColdReadTest`: `R1_1 - without a poll a failed seed read is never retried and the stored lockout is not enforced`, then `R1_1 - without a poll the recovery retry at 1 s loads the stored lockout` | Reads 1 up to T - 1 ms; the state read there answers Available; V = 1; count 6; D = `(6, W0 + T - 1 + 60 s)` | Reads 2 from 1 s; count 5; V = 0; D = `L5`; unenforced 1 s |
| `ColdReadTest`: `R1_1 - with 250 ms polls the first successful re-seed read loads the stored lockout`, then `R1_1 - with 250 ms polls no poll reads, and the retry at 3 s loads the stored lockout` | 5 poll reads; `LockedOut(T - 1 s)` at 1 s; reads 6 | 0 poll reads; Available at 1 s; `LockedOut(T - 3 s)` at 3 s; reads 3 |
| `ColdReadTest`: `R1_1 - each poll starts one read, and polls during a read in flight start none`, then `R1_1 - polls start no read, and no retry falls due while a recovery read is held` | Reads 2 during the hold; 100 polls after the release start 100 reads (102) | The retry at 1 s starts read #1. During the hold, 200 polls and a 10 min advance start no read (reads 2). After the release, 100 polls start none, and the retry 2 s later reads (reads 3) |
| `ColdReadTest`: `R1_2 - under a persistent read fault the state stays Available and each 250 ms poll reads`, then `R1_2 - under a persistent read fault the state stays Available and the reads follow the backoff` | Reads 41 in 10 s for `L5`, `L8`, and `Z` | Reads 4 in 10 s for each; count 0; 0 writes |
| `ColdReadTest`: `R1_2 - when a persistent read fault ends, the next poll loads the stored lockout`, then `R1_2 - when a persistent read fault ends, the next due retry loads the stored lockout` | `LockedOut(T - 10 s)` right after the fault ends at 10 s; reads 42 | Available at 10 s; `LockedOut(T - 15 s)` at 15 s; reads 5 |
| `ColdReadTest`: `R1_3 - an old re-seed value is not applied after a failure, and the failure write replaces the lockout`, then `R1_3 - an old recovery value is not applied after a failure, and the failure write replaces the lockout` | Trigger `poll()`; count 1; D = `(1,0)`; Available after a restart | Trigger `advance(1_000)`; same results |
| `ColdReadTest`: `R1_3 - when the racing failure write fails, the stored lockout returns after a restart` (name kept) | Trigger `poll()`; `LockedOut(T)` degraded; after a restart `LockedOut(T)` recorded, count 5 | Trigger `advance(1_000)`; `LockedOut(T)` degraded; after a restart `LockedOut(T - 1 s)` recorded, count 5; D = `L5` |
| `ColdReadTest`: `R1_3 - an old re-seed value is not applied after a reset`, then `R1_3 - an old recovery value is not applied after a reset` | Trigger `poll()`; count 0; D = `Z` | Trigger `advance(1_000)`; same results |
| `ColdReadTest`: `R1_3 - a re-seed read that waits in the queue is not applied after a failure`, then `R1_3 - a recovery retry that waits in the queue skips its read after a failure` | The queued read runs: reads 2 | The queued retry is ignored: reads 1; count 1; D = `(1,0)` |
| `ColdReadTest`: `R1_4 - a held re-seed read blocks every queued write while admissions enforce from memory`, then `R1_4 - a held recovery read blocks every queued write while admissions enforce from memory` | Trigger `poll()`; D after the release `(5, W0 + T)` | Trigger `advance(1_000)`; D after the release `(5, W0 + 1 s + T)`; the other values stay |
| `ColdReadTest`: `R1_4 - a re-seed read that throws a cancellation stops all re-seeds for the life of the process`, then `R1_4 - a recovery read that throws a cancellation counts as a failed read, and the next retry loads the stored lockout` | Reads stay 2; `L5` unenforced for T - 1 ms; a new process reads it | The retry at 3 s applies `L5`: `LockedOut(T - 3 s)`, count 5, reads 3 |
| `CrossCuttingTest`: `X05 - under a persistent read fault every poll starts one read, with no budget and no backoff`, then `X05 - under a persistent read fault polls start no read, and the retries back off to one every 60 s` | 1000 reads for the burst; 240 reads in 60 s; the next poll after the fault ends recovers | 0 reads for the burst; 14 retries in 10 min (reads 15); the retry at 603 s recovers |
| `CrossCuttingTest`: `X06 - a re-seed read queued before shutdown still runs after it, and its value is not published`, then `X06 - a recovery retry queued before shutdown skips its read, and no retry follows` | Reads 2 | Reads 1 |
| `CrossCuttingTest`: `X06 - a re-seed read in flight at shutdown finishes, and its value is not published`, then `X06 - a recovery read in flight at shutdown finishes unpublished, and no retry follows` | Trigger `poll()`; reads 2; count 0 | Trigger `advance(1_000)`; reads 2, also after 10 min; count 0 |

The new tests of section 6 add three tests to `ColdReadTest` and one to `CrossCuttingTest`.

### 7.2 Tests in `r007` that B1 leaves unchanged

- `ColdReadTest`: `R1_2 - a wrong PIN over an unreadable lockout overwrites the stored lockout`,
  `R1_2 - a correct PIN over an unreadable lockout clears the stored lockout`,
  `R1_2 - under a persistent read fault each restart gives five new verified guesses`,
  `R1_3 - after a local mutation a poll starts no re-seed read`,
  `R1_4 - a held construction read leaves no manager until it returns`, and
  `R1_4 - a construction read that throws a cancellation makes the construction throw`. The assertions stay. The
  traces of the first five gain `RETRY_` events, and the three R1.2 traces lose the reads that polls started.
- `CrossCuttingTest`: X03 (2 tests), X04 (3), X08 (5), X13 (3), X14 (4), and
  `X06 - after shutdown a submission changes nothing and its callback never runs`.
- `HealthyControlsTest` (6 tests), `DegradedRestartTest` (5), `FailedResetTest` (7), and `UndurableDeathTest` (14).
  Their construction reads succeed, so no chain starts, and their traces stay identical.

In total, 14 of the 70 `r007` tests change, 56 stay, and 4 tests are new.

### 7.3 Tests outside `r007`

- `LockoutHarnessTest`: `a re-seed read parked with its value is not applied after a local admission` changes its
  trigger from `poll()` to `advance(1_000)` and says "recovery read". The seeded-schedule tests keep their assertions,
  and their traces change. The schedules are predicted to reach `READ#1 RETURNED` and `READ#1 THREW` through the
  retries that `advance` fires. If they do not, the driver adds an advance after a failed start, and the lead must
  approve that driver change.
- `LockoutManagerTest`: the three cold-start tests (`a failed seed read degrades to Available then an off-main re-seed
  picks up the persisted lockout`, `re-seed is disabled after any local mutation so a failed reset is not
  resurrected`, and `an in-flight re-seed does not publish after shutdown`) get a test timer through `RecoverySetup`.
  They fire the retry instead of calling `currentState()`, and their names say "recovery read". Their expected results
  stay. One test is new (T11).
- `LockEngineRuntimeTest`: no assertion changes. Two tests build the manager over a storage that always throws
  (`self-gate failure resolves a degraded lockout when the durable write fails` and `a lockout storage that throws at
  construction degrades but the runtime still locks`). `buildRuntime` therefore passes a test timer, so that no real
  `lockout-retry` thread queues work on the test dispatcher.

### 7.4 Residual subcases

| Case | Baseline (HISTORICAL) | B1 (PREDICTED) |
|---|---|---|
| R1.1 without polls (JVM; Moto R1.1a) | No read until a caller reads; `L5` unenforced 29.999 s (JVM), 13.8 to 14.5 s (Moto, path L) | The retry at 1 s loads the lock. Unenforced about 1 s when the fault has ended by then. |
| R1.1 with polls (JVM; Moto R1.1b) | The first poll after the fault ends loads (JVM 1 s). Moto: 22 to 39 failed reads, gate blocked 5 to 7 s after it opened | Polls read nothing. The first due retry after the fault ends loads (JVM 3 s). Moto: the failed reads follow the backoff, and the gate blocks at the first due retry after the fault ends. That time depends on the age of the manager, which path L builds at the detector bind. |
| R1.2a read rate | 4 reads per second at the 250 ms poll | 3 reads in the first 10 s after the construction read, then the backoff of section 4, with no relation to the poll |
| R1.2b, R1.2c | A wrong PIN overwrites the stored lockout, a correct PIN clears it | Unchanged |
| R1.2d late recovery | The next poll after the fault ends loads (JVM 10 s unenforced) | The next retry loads (JVM 15 s unenforced) |
| R1.2e restarts | 5 verified guesses for each restart | Unchanged. Each process starts a new chain at 1 s. |
| R1.3a, R1.3b, R1.3f | The old value is not applied; a queued re-seed read still runs | Unchanged results. A queued retry skips its read. The device trigger is the retry at 1 s, not a poll. |
| R1.4a, R1.4b, R1.4d | Construction read on the main thread; held reads; an ANR kill on path L | Unchanged |
| R1.4c held recovery read | 5 writes wait for the read | Unchanged: the read stays on the writer (section 1) |
| R1.4 cancellation (JVM) | Re-seed stopped for the life of the process | A failed read; the chain continues |
| X05 | One read per poll, no budget | No read per poll; delays 1 s to 60 s; no exhaustion |
| X06 | A queued read runs after the stop | No read after the stop; a running read finishes unpublished |
| X13, X14 | Callbacks under the lock; stored values outside the range | Unchanged. B1 adds no callback. The manager handles a recovered pair as it handles a construction seed. |
| X16 | A damaged file reads as `(0,0)`; the lock is lost | Unchanged: the read does not throw, so no recovery starts |

Predicted residual objectives: for R1.1, B1 enforces the stored lockout from the first successful retry, but the gate
stays open for at least 1 s, so the objective is met only in part. R1.2, R1.3, and R1.4 stay "not met". The stuck
re-seed after a cancellation no longer occurs. P5 must resolve these remaining R-007/1 risks: the open gate under a
persistent read fault (R1.2), the lost history after a local mutation (R1.3), and the held construction read and the
delayed writes (R1.4).

## 8. Check against the boundaries and the oracles

| Boundary or oracle | B1 |
|---|---|
| No storage wait on the runtime drain (I1) | No caller gains a wait (T17). The first `start` creates the timer thread during construction. The P3 latency arm measures that cost. The construction read is unchanged (section 1). |
| One ordered writer (I2) | The recovery read uses the existing writer (section 5). B1 writes nothing. |
| No admission lock across I/O (I3) | No B1 step holds the lock during the read or the delay. Admission adds a `cancel()` that never blocks. |
| Stale work does not replace newer state (I4) | A stale result is discarded (T12). No fallback changes while recovery is wanted (section 5). |
| No fabricated failures, captures, or audit events (I5) | B1 never calls the admission code. The retry events go only to the test ledger, and the production listener does nothing. |
| Immediate in-memory reset stays (I6) | A reset ends recovery (T15). No later result can resurrect it (T12). |
| Honest lifecycle (I7) | A held read is not a failure (T14). Adapter and job cancellation differ (T10, T11). A read that runs at `stop()` finishes unpublished (T13). Process death differs from stop (T18). |
| No fallback extension, deadline integrity (I8) | The stored wall deadline applies as stored. An expired deadline stays expired (T14, I8 test). A pair applies only to the empty snapshot, which has no fallback. The wall-clock weakness after a restart (X04) also applies to a recovered deadline. |
| Callers and biometrics (I9) | No caller changes. Every caller reads the same snapshot, which now changes without a poll. A biometric success is an admitted reset and ends recovery, as a correct PIN does. |
| P0 retry policy | The scheduler spec supplies the delays. `currentState()` starts no I/O (T17). |
| P0 rule on persisted records | B1 adds no key and no record. |
