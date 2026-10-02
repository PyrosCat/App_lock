# M7 WP2 F2 hardening, phase P2: R-007 baseline characterization on the JVM (2012 i7)

- **Date / captured:** The report and both recorded runs are dated 2026-09-28. The runs started at 19:57 and
  20:05 EDT.
- **Author / host:** The author is the 2012 i7 development box of the fleet, with Windows 10 Pro 10.0.19045. The
  runs are JVM unit tests only. No device or emulator took part.
- **Tested revision:** Both runs used commit **`1c38037`** with a clean working tree. The run header of each run
  shows `source_tree=clean` and the source fingerprint `3e52d54e…a2246`. The production classes under test,
  `LockoutManager` and `EncryptedPrefsLockoutStorage`, are unchanged since `b9e7c53` (change F2).
- **Toolchain:** The build used AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0, and JDK 21.0.10 from the Android Studio JBR.
- **Scope:** This report records the JVM lane of phase P2 of the R-007 test plan: the characterization of the
  baseline lockout manager. The report is not evidence for a requirement, so the RTM does not change.

## Headline

- **All 70 P2 JVM tests passed. The full local gate ran 468 unit tests with 0 failures and 0 skipped tests.** In
  the same gate run, Gradle found the assembleProdDebug and compileProdDebugAndroidTestKotlin tasks up to date
  from an earlier successful run with the same inputs. The detekt task ran and passed in the second run.
- **The JVM reproduces each of the four R-007 residuals.** For each residual, the baseline behaves as the test
  plan predicts, and the security objective of the residual is not met.
- **Key numbers:** under a persistent read fault, each restart gives 5 new verified guesses; with healthy storage,
  20 process cycles (the first start and 19 restarts) give 5 guesses in total. Under a persistent write fault, each
  restart gives 1 new verified guess, and the count never passes 1. A death before a commit loses the verified
  failure. After a failed reset, a restart brings the old lockout back.
- **The evidence reproduces exactly.** The two recorded runs wrote 72 evidence files each, and all 72 files are
  byte-identical between the runs.

## Context & terms

- **R-007:** the tracked risk that a storage fault weakens the brute-force lockout (FR-174). Change F2 made storage
  failure an explicit outcome and left four residuals:
  - **R-007/1:** a cold-start read failure leaves the manager empty, so a stored lockout is not enforced.
  - **R-007/2:** the degraded fallback of a failed write lives in memory only and ends with the process.
  - **R-007/3:** a process death before an admitted change reaches storage loses that change.
  - **R-007/4:** a failed reset leaves the old count and deadline in storage, and a restart brings them back.
- **Test plan:** `docs/process/active/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`. Phase P2 characterizes the
  baseline. Its case IDs are H01 to H06 (healthy storage), R1.1 to R4.4 (one group for each residual), and X01 to
  X15 (cross-cutting cases). This work adds X16 for the damaged-file finding of probe A (device only).
- **Harness:** the P1 JVM harness in `security/harness`. The harness runs the production `LockoutManager` in
  simulated processes over a simulated store. The store keeps the durable state apart from the cache of each
  process, and a kill fences the dead process so that it cannot change the store. A reference model checks each
  step where the specification has a rule.
- **Fixtures:** `D=(count, wall deadline)` is the stored pair. `Z=(0,0)`, `C4=(4,0)`, `L5=(5, W0+30 s)`, and
  `L8=(8, W0+240 s)`. The clocks start at W0 = 1,000,000 ms (wall) and E0 = 5,000,000 ms (elapsed). T = 30 s is the
  base lockout window, the threshold is 5 failures, and the cap is 30 min.
- **Gated caller and verifier entry (V):** the `GatedCaller` class reads the lockout state before each attempt, as
  the live callers do, and verifies a PIN only while the state is Available. A verifier entry is one PIN that
  reached verification. This class is a manager-level stand-in, not the caller code.
- **Two verdicts:** "As predicted" says whether the baseline matches the predicted baseline of the test plan.
  "Objective met" says whether the security objective of the case holds. A correct reproduction of a residual is
  "As predicted: yes" and "Objective met: no".
- **Virtual time:** the harness moves the wall and elapsed clocks by hand, so each time value in this report is
  exact. The JVM store commits in one step, so a death inside the platform commit is a device case.
- **Evidence file:** each test writes one file with its verdict, the source identity, the recorded values, and the
  ledger trace of each harness. A run writes to its own directory with a `run.txt` header and a source snapshot.

## Command and procedure

```
gradlew.bat testProdDebugUnitTest --rerun detekt assembleProdDebug compileProdDebugAndroidTestKotlin --continue
gradlew.bat :app:testProdDebugUnitTest --tests "com.applock.security.r007.*" --rerun :app:detekt --rerun
```

1. Before run 1, a git sync showed the local branch equal to `origin/main` at `1c38037`. The command
   `git status --porcelain` listed no file.
2. The first command ran the full local gate with a forced test run. Gradle found the detekt, assembleProdDebug,
   and compileProdDebugAndroidTestKotlin tasks up to date, because their inputs were the same as in their last
   successful run.
3. The second command ran the 70 P2 tests again and forced the detekt task to run.
4. A SHA-256 list of the evidence files of each run was compared with the list of the other run.

| Run | Evidence directory (under `app/build/r007-evidence/p2-jvm/`) | Result |
|---|---|---|
| 1 | `20260928T235721Z-e21b5dc4` | 468 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL |
| 2 | `20260929T000541Z-b6e8ffa1` | 70 tests, 0 failures; detekt passed; BUILD SUCCESSFUL |

Both run headers show `source_rev=1c38037f20e423c497d9a2600653703130ac1e36`, `source_tree=clean`, and
`source_fingerprint=3e52d54e273c44d945012de865d663f3c0f53a2ce6b837c7cf8a0c3ca9ea2246`. In each run, the files
`source.diff` and `untracked.sha256` are empty. The `build/` folder is not committed, so Appendix A lists the SHA-256
of each evidence file.

## Results

| Test class | Cases | Tests | Failures |
|---|---|---|---|
| `security.r007.HealthyControlsTest` | H01 to H06 | 6 | 0 |
| `security.r007.ColdReadTest` | R1.1 to R1.4 | 17 | 0 |
| `security.r007.DegradedRestartTest` | R2.1 to R2.4 | 5 | 0 |
| `security.r007.UndurableDeathTest` | R3.1 to R3.4 | 14 | 0 |
| `security.r007.FailedResetTest` | R4.1 to R4.4 | 7 | 0 |
| `security.r007.CrossCuttingTest` | X03 to X06, X08, X13, X14 | 21 | 0 |
| **P2 JVM total** | | **70** | **0** |

### Healthy-storage controls (H)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| H01 | 1000 polls of a healthy `Z` store made 1 read (the construction read) and 0 writes. | yes | yes |
| H02 | Wrong PINs 1 to 4 were Available, and each committed `(c,0)`. The fifth PIN locked at once (30 s, degraded until the commit) and then recorded `L5`. The gate blocked the next two attempts. The caller made 5 verifier entries, and exactly 1 outcome was a recorded lockout. | yes | yes |
| H03 | From `L5`, the state was locked 1 ms before the deadline and Available at it. The count stayed 5. Later windows were 60, 120, 240, 480, and 960 s, then 30 min twice. There were 7 writes for 7 failures, and none for a countdown tick. | yes | yes |
| H04 | From `C4`, a correct PIN cleared memory before its held write committed. After the commit and a restart, the stored pair was `Z`. | yes | yes |
| H05 | A restart reloaded counts 1 and 4, and `L5` with 20 s left after 10 s. A simulated reboot with 60 s of downtime reloaded `L8` with 170 s left. An expired lockout came back as Available with its count. | yes | yes |
| H06 | With the first write held for 0, 10, 100, or 1000 ms, state reads answered from memory, a later failure queued behind the held write, and the last admitted pair became durable. This held for a below-threshold failure, a threshold failure, and a reset. | yes | yes |

### R-007/1: cold-start read failure (R1)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R1.1, no poll | After a failed construction read over `L5`, no read ran for 29.999 s. At T - 1 ms, the first state read of a caller answered Available and let 1 PIN reach verification. Its re-seed read then loaded `L5`, so the count went to 6. | yes | no |
| R1.1, 250 ms polls | Four polls ran failing reads. The storage became readable at 1 s, and the fifth poll loaded `L5` (29 s left). The lockout was not enforced for 1 s. The seed-failure flag stayed set. | yes | no |
| R1.1, burst | While one re-seed read was held, 100 polls on the test thread and 100 concurrent polls on 100 threads started 0 reads. After the release, 100 polls started 100 reads. | yes | no (see X05) |
| R1.2, poll rate | Under a persistent read fault, `L5`, `L8`, and `Z` all read as Available for 10 s, with 4 reads per second at 250 ms polls. | yes | no |
| R1.2, local mutation | A wrong PIN over an unreadable `L5` or `L8` committed `(1,0)`. The stored lockout was lost for good. A correct PIN committed `Z`. | yes | no |
| R1.2, late recovery | After 10 s of failing polls, the first poll after the fault ended loaded `L5` with 20 s left. | yes | no |
| R1.2, restarts | Under a persistent read fault, each of 20 process cycles (the first start and 19 restarts) gave 5 verifier entries, 100 in total. The healthy control gave 5 entries in the first process and 0 in each of the 19 restarted processes. | yes | no |
| R1.3 | An old re-seed value was not applied after a local failure, after a reset, or when the read waited in the queue. When the racing failure write committed, it replaced `L5` with `(1,0)`. When it failed, `L5` stayed and came back after a restart. After a local mutation, a poll started no read. | yes | no |
| R1.4 | A construction read held for 40 s left no manager until it returned. A `CancellationException` from the construction read made the constructor throw. A held re-seed read kept 5 admitted writes from starting for 40 s, while the admissions enforced from memory. A re-seed read that threw a `CancellationException` left the re-seed stuck: 100 later polls started 0 reads, and `L5` was not enforced for 29.999 s. A new process read it. | yes | no |

### R-007/2: degraded enforcement lost on restart (R2)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R2.1 | A failed below-threshold write (false or throw) armed a 30 s degraded lock. A restart at 0 s, 5 s, or 29.999 s, and a reboot at 5 s, came back Available with `D=Z`. 30 s, 25 s, or 1 ms of enforcement was lost, and the gate opened at once. | yes | no |
| R2.2 | F1 failed and F2 committed `(2,0)`. The global state was degraded for 30 s, and F2 kept its own Available outcome. A restart at 10 s gave count 2 and Available, so 20 s were lost. | yes | no |
| R2.3 | F4 failed after 40 s, and F5 then committed a deadline that had already passed. The degraded lock ran 30 s from the completion of F4. A restart gave count 5 and Available, so 30 s were lost. | yes | no |
| R2.4 | Under persistent write faults, the fallback ended exactly at its deadline, and a death at any point cleared it. Over 20 process cycles (the first start and 19 restarts), each cycle gave 1 verifier entry for both fault types. The stored pair stayed `Z`, and the count never passed 1. | yes | no |

### R-007/3: death before admitted changes are durable (R3)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R3.1a | A death after verification and before admission lost 1 verified failure (from `Z` and from `C4`). | yes | no |
| R3.1b | A write that waited in the queue was dropped at death. From `C4`, the immediate threshold lock was lost, and the gate opened at once after the restart. | yes | no |
| R3.1, held write | A write held before its commit was lost at death, with the same results as R3.1b. | yes | no |
| R3.1c | Not run on the JVM: the JVM store commits in one step. | n/a | n/a |
| R3.1d | A death after the commit and before its return kept the new pair: `(1,0)`, or `L5` with a recorded 30 s lock. 0 verified failures were lost. | yes | yes |
| R3.1e | The pair was durable when the completion callback started. A death inside the callback kept the pair and ran none of the callback effects (the legacy audit and capture). | yes | no (accounting) |
| R3.2 | For F, F, reset, F, a death after each queue boundary left exactly the committed prefix. With a threshold write in the queue, a death between that write and the reset restored a recorded 30 s lock after an accepted reset. | yes | no |
| R3.3 | While the first write stalled, 6 PINs reached verification with nothing durable, and up to 5 writes waited. The threshold lock ended during the stall. A reset behind the stall stayed clear. After shutdown, the accepted write still reached storage, with no publication and no callback. | yes | no |
| R3.4 | After a commit whose return was lost, each new process read the new pair and wrote nothing. An injected false acknowledgement gave 30 s of in-process lock over a stored count 1. For a threshold write, it gave a degraded outcome (so no `LOCKOUT_TRIGGERED` audit) for a durable lockout. For a reset, it reported a failed clear that was durable. | yes | no |

### R-007/4: failed reset brings stale enforcement back (R4)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R4.1 | From `L5` (manager-level), a clear that returned false, threw, or stalled left memory clear, and no re-seed undid the reset. A restart at 5 s brought the old lock back with 25 s left. After the deadline, the old count 5 made the next wrong PIN lock for 60 s. Through the gate from `C4`, the first wrong PIN after a restart locked for 30 s. | yes | no |
| R4.2 | A failed clear was not retried, so the process made 1 write. The stale pair stayed until the next committed write, here the next accepted success. | yes | no |
| R4.3 | After a failed clear, the next failure of the new streak committed `(1,0)` and replaced the stale pair. A failure queued behind a held failing clear committed after it. | yes | n/a (no retry at baseline) |
| R4.4 | With every write failing, the first of 20 process cycles waited 30 s for the stale lock, and later cycles waited 0 s. Each of the 20 restarts (19 in the cycles and 1 after them) came back with the stale count 5, and the first wrong PIN after the last restart locked for 60 s (degraded). The evidence field `stale_count_restarts=21` counts process starts, including the first start. | yes | no |

### Cross-cutting cases (X)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| X03, baseline control | These tests cover ordinary threshold writes only, not the queued B3 recovery of X03. A threshold write that stalled 40 s and then failed armed a new 30 s lock from its completion, after 10 s without enforcement. A threshold write that stalled 40 s and then committed did not bring the lock back. | yes | not run (B3 only) |
| X04 | In process, a wall change of +1 h kept the recorded 30 s lock through the elapsed mirror, and a later change to a net -1 h raised it to 30 min. After a restart, +1 h ended the lockout and -1 h gave 30 min. Wall changes did not move a degraded lock. | yes | no |
| X05 | Under a persistent read fault, each poll started 1 read: 1000 reads for a burst of 1000 polls and 240 reads for 60 s of 250 ms polls. There was no budget and no backoff. The first poll after the fault ended loaded the stored pair. | yes | no |
| X06 | A re-seed read queued before shutdown still ran after it, and its value was not published. A re-seed read in flight at shutdown finished and was not published. After shutdown, submissions returned synthetic results, with 0 writes and 0 callbacks. | yes | no (read after stop) |
| X08 | A cancelled waiter did not stop the write. A `Pending.resolved` cancelled before its write started dropped the write, so a restart lost that failure. A cancel during the write still published and called back, but a waiter received a `CancellationException`. A `CancellationException` from the storage adapter armed no fallback and skipped the callback, for a failure and for a clear. | yes | no |
| X13 | A blocking completion callback ran on the writer thread and held the manager lock: admission and shutdown waited, and a state read did not. A throwing callback turned the resolved outcome into its exception. | yes | no |
| X14 | A stored count of -3 allowed 8 wrong PINs before the first lock. A stored count of `Int.MAX_VALUE` wrapped on the next failure: 100 wrong PINs caused no lock, and the negative count became durable. A negative stored deadline read as no lock. A deadline past the cap showed the 30 min cap while more than 30 min remained, and only then counted down. `Long.MAX_VALUE` never comes within 30 min, so it gave no end. For W0 + 2 h, the test sampled 30 min shown at 90 min and Available at 120 min, so the denial was 2 h. | yes | no |

## Findings

The P0 decisions and the P1 reports named some baseline findings before this run. The JVM run reproduced them:
the poll-driven re-seed with no backoff (X05), the re-seed read on the writer thread (R1.4), the stuck re-seed
after a cancellation (R1.4), the callbacks under the manager lock (X13), the construction read on the caller
thread (R1.4), and residual /2 after a restart (R2.1).

This run found these new baseline behaviours. Each one belongs to an existing case ID:

1. **Restart allowance (R1.2, R2.4).** A persistent read fault gives 5 verified guesses for each restart. A
   persistent write fault gives 1 verified guess for each restart, and the count never climbs.
2. **Count wrap (X14).** The next failure after a stored count of `Int.MAX_VALUE` wraps to a negative count. A
   negative stored count gives extra guesses before the first lock.
3. **Deadline past the cap (X14).** A stored deadline far in the future shows the 30 min cap until less than 30 min
   remain, and only then counts down. A correct PIN cannot reach verification until the deadline passes, which for
   `Long.MAX_VALUE` is never.
4. **Cancellation (X08).** Cancelling `Pending.resolved` before its write starts drops an admitted write. A
   `CancellationException` from the adapter drops the outcome: no fallback, no callback, and so no audit or
   capture on the legacy path.
5. **Throwing callback (X13).** A callback that throws turns the resolved outcome into that exception.
6. **Read after stop (X06).** A re-seed read queued before shutdown still runs after it.
7. **Wall clock and restart (X04).** After a restart, a forward wall change ends a stored lockout, and a backward
   change of 1 h raises a 30 s lockout to 30 min.
8. **Racing write (R1.3).** Over an unreadable seed, a successful local write erases the stored history, and a
   failed one keeps it.
9. **False acknowledgement (R3.4, injected).** A threshold commit reported as false gives a degraded outcome, so
   the legacy path skips the `LOCKOUT_TRIGGERED` audit for a durable lockout.

## Not run on the JVM lane

- **Device lanes (NucBox, Moto G):** the live callers and their UI in H01 to H05 and R3.2; biometric behaviour in
  H04 and X11; the startup cost of the construction read on the main thread (R1.4; the 2026-09-25 Moto G report
  measured 73 to 88 ms); real kills with controlled timing (R2.1); death inside the platform commit (R3.1c); real
  reboots; the caller cases X09 and X10; X16 (probe A); and the latency and responsiveness distributions of test
  plan §12.
- **Candidates only:** the B1, B3, and A oracles of R1.1, R1.2, and R2.4; the retries of R4.2 and R4.3; X01; the B3
  parts of X02 and X03, including the preservation of a longer recorded deadline; X12; X15; and the boundary matrix
  of Option A. The baseline has no mechanism for these parts.
- **Runtime level:** the same gate run included `LockEngineRuntimeTest` with 0 failures. Its tests cover the
  control events while a write stalls (H06, R3.3), the rejection of self-gate calls after a runtime shutdown (X07,
  in part), and biometric results that count no failure (X11, in part).

## Notes

- **Manager level.** The `GatedCaller` class follows the rule of the live callers, but it is not their code.
- **Virtual time and a simulated store.** The time values are exact. The runs used no Android preferences, no
  keystore, and no real process death: the harness fence stands for the death.
- **Injected faults.** The faults show behaviour under the fault. They do not estimate how often a fault happens.
- **Model gaps.** The reference model has no rules for a cancellation or a stopped manager. The tests for those
  cases assert the results directly.
- **Real threads.** The concurrency tests of R1.1 and X13 use real threads with time limits of 5 s and a callback
  hold of up to 60 s.
- **Latency.** The JVM lane measured no timing distribution. The device lanes own the §12 measurements.
- **Restart cycles.** The test plan asks for 20 restart and guess cycles in R2.4 and R4.4. The tests of R1.2 and
  R2.4 ran 20 process cycles with 19 restarts, and the test of R4.4 ran 20 process cycles and one more restart.
- **X14 test name.** At `1c38037`, the name of the X14 deadline test says that the display does not count down
  until the wall clock reaches the deadline. The display counts down in the last 30 min, and the test samples only
  90 min and 120 min. The X14 row and finding 3 describe the observed behaviour.
- **Mutation check.** Before the commit, nine temporary changes to `LockoutManager` each made at least one test of
  the matching case fail. The checks ran on an uncommitted tree and are not evidence of record.

## Disposition

- **The JVM lane of P2 is complete at `1c38037`.**
- The P2 exit needs every residual to have an observed or an explicitly blocked reproduction. The JVM lane gives an
  observed reproduction for all four residuals. The device lanes and the platform coverage are still open.
- **RTM:** The RTM does not change.
- **Risk register:** The register does not change. The residual dispositions come at P5.

## Follow-ups

- **P2 device lanes:** plan and run the NucBox and Moto G parts of P2, with the cases in "Not run on the JVM lane".
- **Lead, before P3:** set the numeric restart limit (input: finding 1 and R2.1 to R2.4), and the storage policy
  (input: R1.2). Decide where the findings X08, X13, and X14 are tracked.
- **Lead:** record the P1 exit.
- **Next code change of the P2 tests:** rename the X14 deadline test, and make the tests of R2.4 and R4.4 run 20
  full restart and guess cycles, as the test plan asks.

## Appendix A: evidence files

The list below is the same for run 1 and run 2. Its SHA-256, as UTF-8 text with one LF after each line, is
`e6608d245a1dd4b3ded02b77ac9d5016ac7a09f7e1b5fdebff9cf6d7d09ff30b`. Paths are relative to the run directory. The
list leaves out `run.txt`, because that file holds the start time of the run.

```
c9f7ef4ceb92100a88c2ee6bc1d9da6e0428780ac5b1ab7618d45752ad6a5880  ColdReadTest/R1_1_-_each_poll_starts_one_read__and_polls_during_a_read_in-f10cd39b.txt
97424b79cfed104ee5d89576468f9d2badd2caeaf1cec87d9f1d9aa7709740d5  ColdReadTest/R1_1_-_with_250_ms_polls_the_first_successful_re-seed_read_l-05ef4f51.txt
489b06f02fc73b5c48523d5d91b6a5ed9decba060ffd55c7230c4c91ea4b95fa  ColdReadTest/R1_1_-_without_a_poll_a_failed_seed_read_is_never_retried_an-01638abf.txt
89d6616c7c106891f90997d26f56e7806dff5db3f5d8268d0a170e7a58a35a8e  ColdReadTest/R1_2_-_a_correct_PIN_over_an_unreadable_lockout_clears_the_s-ae476d03.txt
261e710435f4ff01853b314a6fb1a9c9ccb245949db7aa998064140d9965bda5  ColdReadTest/R1_2_-_a_wrong_PIN_over_an_unreadable_lockout_overwrites_the-a56792de.txt
7983bf9aa83877a050c0aed617f1ec552954f2760a04001ed9f2c4caa17ef46a  ColdReadTest/R1_2_-_under_a_persistent_read_fault_each_restart_gives_five-48d4f154.txt
0eb588709810f84a432ad05fa525739ff64e08c0b29f43be0a897c57763faace  ColdReadTest/R1_2_-_under_a_persistent_read_fault_the_state_stays_Availab-fb5005ef.txt
73e8cc4a03fc6cb509ee9e321498bb51ed05f99eaf6902447ad45fc9938be742  ColdReadTest/R1_2_-_when_a_persistent_read_fault_ends__the_next_poll_load-d7952eda.txt
85a265f6cb420c5c7a0defc17f8b9316bbfb797e92bf4200e005cc0d76e8dfc7  ColdReadTest/R1_3_-_a_re-seed_read_that_waits_in_the_queue_is_not_applied-ca43bdb5.txt
594232db75bd5c43dd1ca684cd0951d7ae7f303a404a5a8b0b55fcabd7657953  ColdReadTest/R1_3_-_after_a_local_mutation_a_poll_starts_no_re-seed_read-4a09153e.txt
d4bce09a8c3eeb9e2b79b7a3c18959f772a5e37c8d59723c01e972f70633c9f2  ColdReadTest/R1_3_-_an_old_re-seed_value_is_not_applied_after_a_failure__-12e13227.txt
99a2d94dc30e0a25d10b9bb1e23a0ca948a2f528d5cb6db2ea8dea9c02d23b4e  ColdReadTest/R1_3_-_an_old_re-seed_value_is_not_applied_after_a_reset-1604fb16.txt
f0e7ddb4fda32a7c1f894c2cba136d5df0da755cca70003df72b6b822af56f6f  ColdReadTest/R1_3_-_when_the_racing_failure_write_fails__the_stored_locko-4f224179.txt
bd5a6bbb312bbefa33629a9f732e34f77aad07bb2adf02d070285de7f146b391  ColdReadTest/R1_4_-_a_construction_read_that_throws_a_cancellation_makes_-c67f22cc.txt
955e3c675607b2ab01997e6a145b4cff890707ff2d38ae3b7b0e314b294b2133  ColdReadTest/R1_4_-_a_held_construction_read_leaves_no_manager_until_it_r-5c920e66.txt
4f186ce739c22641f14e1b5d46cefe41222f54ffdfaa1dc5d8ca1bbf90c88762  ColdReadTest/R1_4_-_a_held_re-seed_read_blocks_every_queued_write_while_a-b55d6b09.txt
4393375cf11140e55dbab2a6ed62f528c99de5a20e70070ded6cba2df590d38f  ColdReadTest/R1_4_-_a_re-seed_read_that_throws_a_cancellation_stops_all_r-aeabd318.txt
b9f322c98544dbaca6a9e8f5c4aaccf5e3f6b8f9f8e00e921d416d76a6d7c6e7  CrossCuttingTest/X03_-_a_threshold_write_that_stalls_past_its_window_and_comm-c76fd75d.txt
cbfcd91ad16cb34888934a45af90a33c5a82802b59ddadca03c48473e352744a  CrossCuttingTest/X03_-_a_threshold_write_that_stalls_past_its_window_and_fail-946dee75.txt
561e050df6dd915a9f80d605ca26bef20fb6e010dd6767c157aab2bd9ea0b2ff  CrossCuttingTest/X04_-_a_wall_change_does_not_move_a_degraded_fallback-ba02aaaa.txt
1bf59dc87362368e5c16a8f9fb3dccd679e9793bd5bada85d4c407414327281f  CrossCuttingTest/X04_-_after_a_restart_a_forward_wall_change_ends_the_lockout-9b903eea.txt
94755400827f289f6c7efb6ac6e69f34df7b41b09b691cedba4787d24a5fe9e4  CrossCuttingTest/X04_-_in_process_a_wall_change_cannot_shorten_a_recorded_loc-4a3a47c6.txt
356d96ed4dde74d47dc25867e50ca42996652735cb670ce2a895de7b8fa8336b  CrossCuttingTest/X05_-_under_a_persistent_read_fault_every_poll_starts_one_re-58cdf6f5.txt
4de186a6c3ebd60676f0f414fe1b577d7ebea88f08becdee0d5871cdb1c3547a  CrossCuttingTest/X06_-_a_re-seed_read_in_flight_at_shutdown_finishes__and_its-1fa7e9e3.txt
28efe987c1e3828fe1e4ea44da8fe5ab0a2da170b8162f152696c5cba2d51dc1  CrossCuttingTest/X06_-_a_re-seed_read_queued_before_shutdown_still_runs_after-23112629.txt
2cf524268457a1483a4f42072375a54b028c03249867519af5568e0bc060e436  CrossCuttingTest/X06_-_after_shutdown_a_submission_changes_nothing_and_its_ca-52586a82.txt
5225ce02bf3c1223941dec1f7c6133895d9aac543e23ff173b924ac84e060470  CrossCuttingTest/X08_-_a_clear_that_throws_a_cancellation_skips_its_callback_-208178d7.txt
055f973c5ccf41a4a582098c52ae613c802f569e8bdab6fdf85f1adaeb3f2b00  CrossCuttingTest/X08_-_a_failure_write_that_throws_a_cancellation_arms_no_fal-0783041f.txt
7191de601002ffe259118eb87c73131a162c53f1d0b5ad6b8dc7abf36340b0f9  CrossCuttingTest/X08_-_cancelling_a_waiter_does_not_cancel_the_write-e6a1ca9c.txt
8ead3cd9fb53460dacad6dcfa05fd55eeda29292c07ee2b2e48763caa28e7338  CrossCuttingTest/X08_-_cancelling_the_resolved_Deferred_before_its_write_star-d8bbb122.txt
5d2a69984eb2e2a097d381f70a56b1ceb83076253d53602836657fa8d4541264  CrossCuttingTest/X08_-_cancelling_the_resolved_Deferred_while_its_write_runs_-c5c14a61.txt
2119bf1bd4dfb67b000c496a1938f6897bb093bd3934bf4ff5089cce4198ad51  CrossCuttingTest/X13_-_a_blocking_completion_callback_holds_the_manager_lock_-b6e8d4a3.txt
5752cc20c4c935b686fe6a1ccdb25c0caa0915cd79f8aa8565e19ad16aebb5b5  CrossCuttingTest/X13_-_a_throwing_completion_callback_turns_the_resolved_outc-3e2fe9f1.txt
8d61da6a65f6f9409b08887016ea13efdee16bba7ff154bd3315b8843b75ac7f  CrossCuttingTest/X13_-_shutdown_waits_for_a_blocking_completion_callback-7ee4a3a7.txt
0bce7af0bfc3cf4db3cc930d8bad46902c9fb94236120734248bdcde54144107  CrossCuttingTest/X14_-_a_negative_stored_count_gives_extra_guesses_before_the-8034fac6.txt
4facfc07ff721e53dec7e9f7434ffe1f4889769f69b9e8902c24ad4781c56937  CrossCuttingTest/X14_-_a_negative_stored_deadline_reads_as_no_lockout-249ab430.txt
6bcbebcc48ec7db83cd6fa3ffa970983ae5fc753fb479c61ebcd1b19baf1fb16  CrossCuttingTest/X14_-_a_stored_count_of_Int_MAX_VALUE_wraps_on_the_next_fail-48a37814.txt
d52b0e7c38a1570ced00bf27a951e0bc636214a981ef5fa4e159c917557912d3  CrossCuttingTest/X14_-_a_stored_deadline_past_the_cap_shows_the_cap_and_does_-eb81a6fd.txt
248df60948f6c2d6503fef02aaf08054f21b74ff5b772cde33974f538e065d88  DegradedRestartTest/R2_1_-_a_restart_erases_the_degraded_fallback_of_a_failed_be-2770b396.txt
bbaaed2bd81ccf4a4ad7af1c83a8809c0135c082ede154ea4a23f5d6325bb396  DegradedRestartTest/R2_2_-_a_later_committed_count_keeps_the_earlier_fallback_in-edb1fe05.txt
31d2ccb0ee416c7c98e15ef00c150f99db040de195af24fc9a386ba071bf755e  DegradedRestartTest/R2_3_-_a_threshold_commit_whose_deadline_elapsed_leaves_a_de-ccf7ac4d.txt
c397907847aa2794bbbeacbf05b60150d6fb5ad7c4187a1aa3c37dff06b327da  DegradedRestartTest/R2_4_-_under_persistent_write_faults_each_restart_gives_one_-4bb6b23f.txt
3e9ac00a7a653e7958893722602872092478c4f40064e9e6759e70cf78dd30b7  DegradedRestartTest/R2_4_-_under_persistent_write_faults_the_fallback_ends_at_it-4bc5feb5.txt
252c3c6de8797cc449e3501d958681f57137ba2c1df1a7a7457f5b14a11e2f2d  FailedResetTest/R4_1_-_a_failed_clear_keeps_the_old_lockout_in_storage_and_a-20513fd0.txt
c1b5f988a11e129dc45e9a4acadc832babde75151a6e10cf44a9402cd797355d  FailedResetTest/R4_1_-_after_the_stale_deadline_ends__the_old_count_makes_th-4d004866.txt
b2467404b9c3fbde3d0eaef888a085fe20071420de94979ca775795d30015abc  FailedResetTest/R4_1_-_through_the_gate_a_failed_clear_of_count_4_makes_the_-1cce88f2.txt
1ef1ddf45881a4a65625d6e8810f5104a5832dbe50bef3d01a8741f241bf9966  FailedResetTest/R4_2_-_a_failed_clear_is_never_retried__so_the_stale_pair_st-851a26ab.txt
cdd5d75c44b99bb493773d3e50d5efd412e2cfef352824e0ab638671ef541635  FailedResetTest/R4_3_-_a_failure_queued_behind_a_held_failing_clear_commits_-cd243f63.txt
72934f791a93f9db7124abe2b474f2f415903ff0b0b6815a3aa8867ca4d1ee96  FailedResetTest/R4_3_-_after_a_failed_clear_the_next_failure_write_of_the_ne-a91ec268.txt
4051fc061c734e236a920b44ad45a430a6e7ee123555282154c2ba63db9ac3a9  FailedResetTest/R4_4_-_under_persistent_write_faults_the_stored_lockout_and_-d14def5d.txt
da2627ff35b316f860c9f8050e11aed8b2726a83692895e3d6bf73b2481cf716  HealthyControlsTest/H01_-_polls_of_a_healthy_zero_store_read_once_and_write_noth-c665ef4c.txt
aacacd6ca43f7911d7c76ad3c779b8a0f8a089192353a8ab66b608ec828332e5  HealthyControlsTest/H02_-_the_fifth_wrong_PIN_locks_at_once_and_the_gate_blocks_-70868c7d.txt
961293cf7dc0c12a139a35cd691f81b55c3bfc7fdc71204fc6a3798ca417131b  HealthyControlsTest/H03_-_a_recorded_lockout_ends_at_its_deadline_and_the_ladder-fbc0a21d.txt
07cdc6acf3a99ad32f374af2ec0080c6bdfc089abd0b8d6ce260b9fba1007750  HealthyControlsTest/H04_-_a_correct_PIN_clears_memory_at_once_and_the_durable_cl-ab61b4ff.txt
1ada4a7575e8448167e2e9fce80b5444d282ce5c4548c899af7e734c1ed7ab18  HealthyControlsTest/H05_-_a_restart_and_a_reboot_reload_the_last_committed_pair-c3f570cf.txt
4e7950ffbb366f9722bfe68a49c540995679ca5ff39be3e6d2075bd6e2cfe0dc  HealthyControlsTest/H06_-_slow_healthy_writes_keep_the_admission_order_and_the_l-cefad677.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  source.diff
0a4265572a5b5724e90d1cdf34eb3ff735fec9a827440b9793fc94c9c832ca97  UndurableDeathTest/R3_1_-_a_write_held_before_its_commit_at_death_is_lost__and_-eb4df0b8.txt
19dc90989504a0567e8e0ad886fdeb4a17ea14ac1596e028687d3ea61b3f5a2b  UndurableDeathTest/R3_1a_-_a_death_after_verification_and_before_admission_lose-f5d411e1.txt
3da088854e6bbb5fda7d3d1a867cfde4da1572f2d97c3923cce0108795fab30f  UndurableDeathTest/R3_1b_-_a_write_that_waits_in_the_queue_at_death_is_lost__an-dba3cf3f.txt
734f00439767d6c112a0dd6c3e8323079cc55b34e3b9c2ea1ba1a0de563151f1  UndurableDeathTest/R3_1d_-_a_death_after_the_commit_and_before_its_return_keeps-fdb85e84.txt
a95d25b79801fe0fce011875fb4a7f5f360b475dc5f37ca35fa90ca37812b357  UndurableDeathTest/R3_1e_-_a_death_in_the_completion_callback_keeps_the_commit_-ba76dc14.txt
c64b8370aca01fdfdfc903293553a07ecf7bb814a11c9f95df3538ff2f4ffa7b  UndurableDeathTest/R3_2_-_a_death_at_each_queue_boundary_leaves_exactly_the_com-cf703992.txt
44b56da43a43203777de2883b1fc18a00305d1603881a4089a8cd7c2739c797c  UndurableDeathTest/R3_2_-_with_a_threshold_write_in_the_queue_a_death_can_bring-79b6cd1a.txt
b4622ca264404afbd5e133edcc5da1bed3182cf6edb076e6282e825e2dc0ef2f  UndurableDeathTest/R3_3_-_a_reset_behind_a_stalled_failure_write_clears_memory_-7845b278.txt
35f13a802ce7924d3658806e2ec793dae2e08d04db4beb67b39e83a9c563f5e4  UndurableDeathTest/R3_3_-_after_shutdown_an_accepted_write_still_drains__and_no-c8c35383.txt
6ab70af49862f00fe40eccde0c5a44fe5d1539ef80e5499420e5575e610faea0  UndurableDeathTest/R3_3_-_while_the_first_write_stalls__admissions_go_on_and_en-0caecae7.txt
09d7859fc7ac47202c15af297cdf6188d4f750312435954dd941114842486549  UndurableDeathTest/R3_4_-_a_commit_whose_return_is_lost_at_death_is_read_once_b-534b3a7a.txt
29f321e223caeb25624446799c930fdb65eb27ce49b3c3d84cd45a3f1f2fb983  UndurableDeathTest/R3_4_-_a_false_acknowledgement_after_a_real_commit_arms_a_fa-e1e2f5fe.txt
0c271658aad0743cc877297168551aff2619130aef13281fef58a06eedefc5db  UndurableDeathTest/R3_4_-_a_false_acknowledgement_of_a_reset_reports_a_failed_c-181caa42.txt
bc7c05baa9a0fe3d077e0134b70bee39488f313a615743818629b94352e498f8  UndurableDeathTest/R3_4_-_a_false_acknowledgement_of_a_threshold_write_gives_a_-2e374d04.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  untracked.sha256
```
