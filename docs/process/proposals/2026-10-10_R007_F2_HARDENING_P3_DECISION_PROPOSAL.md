# M7 F2 hardening: P3 decision proposal after P2

**Date:** 2026-10-10

**Status:** Proposal for the lead, not approved. It selects no production design, and it changes no criterion or
decision recorded in `M7_PLAN.md`.

**Scope:** The document proposes which P3 candidates go forward after the P2 results, the decisions that the lead
records first, and the order of the work. It uses the candidate labels and the evidence labels of
`2026-09-23_R007_F2_HARDENING_TEST_PLAN.md` (the test plan). P2 results are HISTORICAL, candidate behaviour is
PREDICTED, and open policy questions are UNRESOLVED.

## 1. Summary

The proposal recommends three candidates:

- **C1 with B1:** no PIN check starts until the stored lockout state is read and trusted. B1 retries startup reads
  automatically. Once storage becomes readable, startup can complete without a restart.
- **Count-first C2:** each PIN request stores the failure count before its PIN check, in the existing `failure_count`
  and `lockout_until` keys.
- **B2:** a failed write is retried, mainly the reset after an accepted success.

Option A stays a comparator for limits of count-first C2. B3 gets a bounded comparison with B2 and no plan to ship.
C4 and a startup write probe stay named as partial alternatives. C3, C5, and C7 are not prioritized. C6 becomes a
separate clock-policy track.

The P3 criteria of 2026-10-07 in `M7_PLAN.md` set the bar: fail-closed admission while the lockout state is unknown,
and zero extra PIN-verifier entries attributable to a restart. Under these criteria, B-only combinations are recovery
components, not complete solutions. Only durable admission (count-first C2 or A) keeps a death before the write from
losing a checked PIN.

| Candidate | Recommendation | Condition |
|---|---|---|
| C1 + B1 | Proceed first | Trust needs more than a read that returned (section 3.1) |
| Count-first C2 | Preferred durable-admission design to prove | The requirements still to prove in section 3.2, and decision D4 |
| B2 | Retain, and adapt to the selected protocol | A death before the reset commits stays a residual |
| A | Comparator, and alternative for limits specific to count-first C2 | Shares the costs of C2; a new record needs an exception |
| B3 | Bounded comparison only, no plan to ship | Decision D3; omitted if the selected design removes the fallback |
| C6 | Separate clock-policy track | X04 on a device first, and decision D5 |
| C3 | No separate experiment | Only for a demonstrated protocol need |
| C4, startup write probe | Partial alternatives if the shared costs of C2 and A are rejected | Neither meets the restart criterion |
| C5, C7 | Not prioritized | C5 fails with its own write; C7 delays access after a success |

## 2. P2 evidence that drives the proposal

The sources are four P2 reports:

- `2026-09-28_m7-wp2-f2h-p2-jvm-baseline_2012-i7.md`: the JVM baseline.
- `2026-10-09_m7-wp2-f2h-p2-jvm-rerun_2012-i7.md`: the clean JVM rerun.
- `2026-10-04_m7-wp2-f2h-p2-device_moto-g-2025.md`: the Moto G phone.
- `2026-10-08_m7-wp2-f2h-p2-device_nucbox-g5.md`: the NucBox emulators, API 36 and API 30.

"As predicted" in these reports means that the baseline reproduced the weakness. It is not a security pass.

| P2 observation (HISTORICAL) | What it shows | What it does not show |
|---|---|---|
| A persistent read fault gave 5 verified guesses in each process: 105 over the first process and 20 restarts on the JVM, and 5 in each of 21 processes on the Moto G and both NucBox emulators, for both callers. | A restart restores the attempt allowance while the state is unreadable. | How often the fault occurs in the field. |
| A persistent write fault gave 1 verified guess in each process, 21 on the JVM. The stored count never passed 1. | A restart restores the allowance while no change reaches storage. | Whether a user or an attacker can cause the fault on a device. |
| A failed construction read left a stored 10 min lock unenforced for 13.8 to 15.5 s. A wrong PIN over an unreadable `L8` stored `(1,0)`. | The unknown state permits checks and erases the stored history. | That a read retry alone closes the gap. |
| A restart cut a 30 s degraded lock short with 20.6 to 23.8 s left. | The fallback lives in memory only. | Whether keeping the fallback changes the attempt allowance (section 3.5). |
| The mid-commit sweeps left a complete old or new pair in all 160 kills (98 on the Moto G, 62 on API 36). The 64 kills inside the platform write kept the old pair and lost the verified failure. A kill during the PIN check left the old pair, before any result existed. During one held write, 6 PINs were checked with nothing stored, and a kill lost all 6. | All 160 sampled kills left a complete pair. A check that completed before its result was stored could still be lost, so the order of the check and the write is the problem. | That the commit is atomic at every kill point, behaviour at a power loss, or the correctness of a candidate protocol. |
| After an accepted success over an unreadable store and a failed clear, a restart brought back 517 to 556 s of a stored 10 min lock. A failed clear got no retry within 60 s. | A reset retry has a real benefit for the legitimate user. | That a retry removes stale enforcement before it commits. |
| A damaged store file read as `(0,0)` (X16). A stored count of `Int.MAX_VALUE` became negative at the next failure (X14). A fingerprint over an unreadable store cleared a stored 10 min lock (X11e). | Trust needs validation, and the biometric path needs its own policy. | That a gate on a returned read is enough. |
| The construction read ran on the main thread, median 82 ms (self-gate) and 194 ms (legacy lock screen) on the Moto G. A 40 s hold at the detector bind ended in an ANR kill. | Startup needs an explicit loading state off the main thread. | Startup times in the field. |
| On the JVM, a forward wall-clock change of 1 h and a restart ended a stored lock (X04). | The clock needs an explicit policy. | Device behaviour, or a solution across a reboot. |

## 3. Candidates

### 3.1 C1 with B1: trusted startup state

**Recommendation:** proceed first. C1 admits no PIN check until the stored lockout state is trusted in this process.
B1 retries the read on the scheduler delays (1 s, doubling to 60 s). Once storage becomes readable, startup can
complete without a restart.

**PREDICTED effect:** the 5 guesses per process under a persistent read fault fall to 0, and so does the window of
13.8 to 15.5 s. A legitimate user waits for the next successful retry instead. A local change can no longer overwrite
an unread stored lockout (R1.2b). A correct PIN over an unread store can no longer bring back a stale lock (R4.1b).

C1 needs these parts:

- **A trust rule beyond a returned read.** A damaged file reads as `(0,0)` (X16), so the rule must tell a lost record
  from a fresh install (decision D1). Stored values out of range (X14) are not trusted either.
- **An explicit loading state.** The construction read can then leave the main thread, which removes the ANR risk of
  R1.4.
- **Every caller.** The self-gate, the legacy lock screen, the overlay of F6, and the biometric host treat each state
  other than ready as no permission. At baseline, the `is LockedOut` checks treat every other state as permission.
- **A biometric policy** (decision D2).

**Cost:** a persistent read fault, for example a lost keystore key, keeps the PIN path closed until the fault ends.
The user needs a visible route out.

### 3.2 Count-first C2: durable admission with the existing keys

**Recommendation:** the preferred durable-admission design to prove. The test plan describes C2 as a durable debit of
attempt capacity before the PIN check. P0 grouped C2 with the candidates that add a persisted record. Count-first C2
uses the stored count as the debit instead.

**PREDICTED protocol:**

1. With a trusted state, a PIN request first commits the count that a failure would give, through the one ordered
   writer. When that count reaches the threshold, the same commit holds the lockout deadline.
2. The PIN check starts only after that commit returned true. The request keeps permission for its own check, also
   when its commit installs a lockout.
3. A wrong PIN needs no further write. A correct PIN clears memory at once (constraint I6 of the test plan), and the
   reset write follows. B2 retries a failed reset.
4. A commit that returns false or throws starts no check. The caller shows that storage is unavailable.
5. After a death, the debit stays. It counts as consumed capacity, not as a wrong PIN: no audit event and no capture.

**PREDICTED effect:** no PIN is checked without a stored count. Each kill point of P2 then lands before the commit, when
no check ran and nothing was learned, or after it, when the attempt is already counted. A failed debit starts no check,
so no checked failure exists in memory only. The degraded fallback then has no purpose, and a persistent write fault
gives 0 checks. Residual R-007/4 of the test plan (a failed reset brings back a stale lock at restart) does not
change.

**Costs:**

- Every caller checks the PIN asynchronously after the commit. This is the caller work of Option A. The asynchronous
  self-gate planned for F6 moves into it, and the PIN hash leaves the main thread.
- A write fault closes the PIN path. If a full disk causes write faults, anyone who can fill the storage can close the
  owner's PIN path (step 5).
- An interrupted correct PIN costs one count. At count 4, that gives a lock of up to 30 s after the restart.
- A threshold lock counts from the debit, which is earlier than at baseline by the duration of the PIN check.
- The ordinary failure-completion extension of the fallback ends. Constraint I8 of the test plan requires a stated
  decision for that change.
- The stored count changes meaning: failures plus interrupted requests. Decision D4 settles how invariant 6 of
  `M7_PLAN.md` (no new persistence in M7) applies.
- Each PIN check waits for one commit. In 7 missed trials of the R3.1c sweep, a commit took 10 to 13 ms on the Moto G.
  The P3 latency arm measures the end-to-end effect.

**Requirements still to prove** before count-first C2 counts as a complete design:

1. **Ownership at the threshold.** A request that stores count 5 and a deadline keeps permission for its own check,
   also for a correct fifth PIN. Other requests stay blocked. All callers share one admission owner.
2. **Ambiguous commit and cancellation.** A committed debit with a lost acknowledgement is not consumed twice. The late
   completion of a cancelled request starts no check and grants no session. Cancelling the request cannot drop the
   debit write (X08).
3. **Expiry and repeated restart.** The design defines a long stall before the check, an expiry during the check, and
   a restart during recovery. A restart neither restores capacity nor restarts a penalty.
4. **Truthful accounting.** An interrupted debit is not evidence of a wrong PIN. No failure audit or capture is
   invented.
5. **Compatibility and trust.** The design specifies the new meaning of the count, the valid legacy states, downgrade,
   corruption, overflow, and the rule for a missing record.
6. **Availability and latency.** The evaluation measures the end-to-end authentication time and the legitimate-user
   denial under faults.

Most of these requirements apply to Option A too.

### 3.3 B2: reset retry

**Recommendation:** retain, and adapt to the selected admission protocol. B2 retries a failed clear while it still
applies, and access after an accepted success stays immediate. The adapted B2 keeps the writes with the manager, one
ordered writer, freshness checks before the I/O, and the rejection of stale work. Under count-first C2, B2 is needed
for the reset write only. A death before the reset commits stays a residual of R-007/4.

### 3.4 A: explicit reservation

**Recommendation:** comparator, and alternative only for limits specific to count-first C2. A shares the costs of C2: a
write before every check, asynchronous callers, and a closed PIN path during write faults. When those shared costs are
the reason to reject C2, A is no alternative. A remains an option when the changed meaning of the count is not
acceptable under invariant 6, or when the accounting must tell an interrupted attempt from a wrong PIN. A adds a new
record, so P0 limits it to a design and a JVM model until the lead records an exception.

### 3.5 B3: stored fallback deadline

**Recommendation:** complete the bounded comparison with B2, with no plan to ship B3. B3 is omitted when the selected
admission design removes the fallback.

Under B2, a committed retry keeps the in-memory fallback but does not store it (the publication of a committed retry in
`2026-10-07_R007_F2_HARDENING_P3_B2_SPEC.md`). A restart then ends the fallback. B3 stores its remaining deadline.
PREDICTED example, with stable clocks, healthy reads, and one transient below-threshold write failure whose fallback
ends at 30 s:

| Time | B2 | B2+B3 |
|---|---|---|
| 0 s: the failure write returns false | Memory holds count 1 and the fallback to 30 s. | Same. |
| 1 s: the retry commits | Storage holds count 1 without a deadline. Memory still blocks. | Storage holds count 1 and the deadline at 30 s. |
| 5 s: death and restart | The count survives, the fallback does not. | The count and the rest of the fallback survive. |
| 6 s: a wrong PIN | The check runs. Without the restart, it would still be blocked. | Blocked until 30 s. |

So B3 can stop a check that a restart moves into the original fallback window. It does not change the stored count,
and it does not help while every retry fails. The two comparisons give different answers:

- Against healthy storage, B2 gives no extra checks in R2.1 to R2.3 once its retry has committed, because the count
  and the lockout ladder survive.
- Against the same fault without a restart, B2 permits the check at 6 s.

Decision D3 settles which comparison the restart criterion uses.

B3 has costs of its own: the conversion to a wall deadline, the backward-change regression of its X04 case (a backward
change of 1 h blocks access for 1 h 29 s instead of 29 s), and the limits of a backward wall change in the B3
specification.

The bounded comparison:

- runs the formal JVM runs of the B2 branch and the B2+B3 branch at committed revisions, with the existing cases: R2.1
  with a death before and after the retry commit, R2.2, R2.3, R2.4, X03, X04, and R4.3;
- adds a same-fault run without a restart, at matched times and inputs, where a case lacks one;
- records the stored count lost, the time until the next permitted check, the checks before the original fallback
  expiry, the checks over a fixed longer horizon, the retry commit time, the writes, and the legitimate-user denial;
- runs no device campaign for B3, unless the JVM comparison shows a benefit worth its cost.

Neither "both tie" nor "B3 wins" is the oracle. A result that shows a kept fallback can still justify omitting B3.

### 3.6 C6: clock policy

**Recommendation:** a separate track. P2 showed the clock weakness on the JVM only (X04), so X04 on a device comes
first. A person with the unlocked phone and access to Settings could change the clock and force-stop the app, unless
Settings itself is protected. Decision D5 sets the threat scope. C6 covers a restart in the same boot, not a reboot.
Its boot identity needs a new key, so C6 needs an exception to invariant 6.

### 3.7 C3: unified record or journal

**Recommendation:** no separate experiment without a concrete protocol need. The adapter already commits both keys in
one editor commit, and in all 160 sampled kills of P2 that commit left a complete pair. Count-first C2 needs no new
structure. If A is built, its record can join the same commit.

### 3.8 C4 and a startup write probe

**Recommendation:** keep both named as partial alternatives, for the case that the lead rejects the shared costs of C2
and A.

- C4 blocks further checks while an earlier result is not stored. In R3.3, it would cap the 6 lost checks at 1 per
  process.
- A startup write probe requires one stored write before the first check of each process. That ends the restart loop
  of a persistent write fault. The probe must prove that its write reached storage.

Neither meets the restart criterion, because the first check after each restart can still be lost. Selecting either
needs an explicit policy change and an accepted residual.

### 3.9 C5 and C7

**Recommendation:** not prioritized. A reset marker (C5) fails together with the write that would store it. Waiting
for a durable reset before access (C7) removes the immediate access after an accepted success (I6).

## 4. Decisions for the lead

The lead records these decisions before the P3 designs depend on them.

| ID | Decision | Input |
|---|---|---|
| D1 | What makes the lockout state trusted, and how a lost record is told from a fresh install | X16, X14 |
| D2 | The permissions of each caller in each state other than ready, including a biometric success while the PIN path waits | X11e. Invariant 4 of `M7_PLAN.md` (the PIN fallback is always available) conflicts with fail-closed. |
| D3 | Whether the restart criterion covers the degraded fallback, or only the attempt allowance and the lockout ladder | Section 3.5. The criterion asks for "zero extra PIN-verifier entries attributable to a restart", and it also says "A restart does not replenish the attempt allowance or reset the lockout ladder. Normal expiry still applies." Under count-first C2, the question is moot. |
| D4 | How invariant 6 applies to count-first C2, which keeps the two keys but changes the meaning of the count | Section 3.2 |
| D5 | Whether a wall-clock change by a person with access to Settings is in the threat scope | X04 on a device |
| D6 | Whether A may go beyond a design and a JVM model | Only for a demonstrated limit of count-first C2 |

Access after an accepted success stays immediate (I6). R-007/4 before a durable reset stays a recorded residual.

## 5. Order of work

| Step | Work | Decision rule |
|---|---|---|
| 1 | Record decisions D1 to D6. | Recorded criteria stay unless the lead changes them. |
| 2 | Specify C1 with B1: the trust rule, the asynchronous startup, and the fail-closed states. Write the transition table of count-first C2 and a model with simulated storage. Model A for the same crash points and the same uncertainty policy. | Prefer C2 if it passes the required cases with less complexity. Extend A only for an identified limit of C2. Follow the P0 restrictions before a device prototype. |
| 3 | Adapt B2 to the selected admission protocol: reset retries, ordered writes, rejection of stale work, and cancellation ownership. | Access after an accepted success stays immediate. A timeout never counts as proof that a write stopped. |
| 4 | Run the bounded B3 comparison of section 3.5. | Omit B3 if the selected design removes the fallback. Otherwise weigh its measured benefit against its clock and availability costs. |
| 5 | On devices: X04, a low-free-space run that checks whether a full disk makes `commit()` fail, the P3 latency comparison, and the crash and caller cases of the candidates. | No device claim from JVM results. No claim of restart protection while an in-scope clock bypass remains. |
| 6 | Select the design, and record a disposition for each residual with local and fleet evidence. | If the lead rejects the shared costs of C2 and A, the lead reopens the policy before C1 + B1 + B2 with C4 or a probe is considered. |

Steps 2 to 4 can share work. "Proceed first" for C1 means that its contract is specified first. It does not mean that
a partial solution goes into production as if R-007 were closed.

## 6. Findings outside the candidates

- X14 (stored values out of range), X08 (cancelling `Pending.resolved` before its write starts drops the write), and
  X13 (callbacks under the manager lock) must be assigned to the selected design or to a separately tracked fix. The
  JVM baseline report left their tracking to the lead.
- P2-F1, the self-closing legacy lock screen, left the protected app without a lock screen for up to 162 s, with no
  storage fault. Step G resolves it. R-007 work alone cannot establish the protection.
- Every candidate keeps storage waits off the runtime drain and the main thread, keeps one ordered writer, and keeps
  the immediate in-memory reset.

## 7. Proposed decision record

The lead can record this text in the F2 hardening entry of `M7_PLAN.md`:

> P3 prioritizes trusted startup (C1 with B1) and count-first durable admission, and retains the reset retry (B2).
> Option A is used only where count-first admission has a demonstrated protocol limit. B3 has no production plan. A
> bounded comparison with B2 reports two effects separately: the remaining fallback wait that survives a restart,
> and the attempt allowance that a restart restores. The lead decides whether the restart criterion covers the
> degraded fallback before that comparison is used for selection. B3 is omitted if the chosen admission design
> removes its purpose. A partial alternative needs explicit acceptance of the restart exposure that it leaves.
