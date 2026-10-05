# M7 WP2 F2 hardening, phase P2: R-007 baseline on the Moto G 2025

- **Date / captured:** The report is filed on 2026-10-04. The recorded run ran from 2026-10-03 01:44 to 2026-10-04
  05:08. The two single-case reruns ran on 2026-10-04, from 19:09 and from 21:46. Times are UTC.
- **Author / host:** The author is the 2012 i7 development box of the fleet (Windows 10 Pro 10.0.19045). It drives
  the Moto G 2025 (`ZT4229HQ6X`, Android 15, API 35, arm64) over USB adb.
- **Tested revision:** Every segment of the recorded run ran at commit **`ca57786`**. The R1.2a rerun ran at
  **`6f28d8a`**, and the X11a rerun at **`8d1c28b`**. Each evidence header shows `host_changed_files=0`. The commits
  after `ca57786` changed only the harness scripts, its host tests, the device plan, and the changelog.
- **Installed APKs:** All runs used the build of the harness commit `f756287`: app `0975314c…22c4bb` and androidTest
  `9c32e9a2…bad55f1` (SHA-256). No app code changed between `f756287` and `8d1c28b`.
- **Scope:** the Moto G lane of phase P2 of the R-007 test plan: the baseline characterization of the lockout
  manager on a real device, with both live callers. The report is not evidence for a requirement, so the RTM does
  not change.

## Headline

- **The Moto G lane of P2 is complete. In the eight segments of the recorded run, 558 of 559 case repeats were as
  predicted.** The one miss (R1.2a) was a timing artifact of the harness. A rerun at the fix commit gave 12 of 12.
- **The Moto G reproduces all four R-007 residuals with both live callers.** Residuals /2, /3, and /4 also hold
  across a reboot.
- **Key numbers:** under a persistent read fault, each of 21 processes gave 5 verified guesses. Under a persistent
  write fault, each process gave 1. After a failed construction read, a stored 10 min lock was not enforced for 13.8
  to 14.5 s. A restart ended a 30 s degraded lock with 20.6 to 23.1 s left. After a failed reset and a restart, the
  first wrong PIN locked for 30 s.
- **The platform commit was atomic in each of 98 kills.** Each kill during or just after a commit left the complete
  old pair or the complete new pair. The 42 kills inside the platform write kept the old pair, so the verified
  failure was lost.
- **Device-only results:** a damaged store file reads as `(0,0)`, so a stored lock is lost (X16). A fingerprint
  clears a stored 10 min lock over an unreadable store (X11e). A 40 s construction read at the detector bind ends in
  an ANR kill (R1.4b). The construction read takes a median of 82 ms (S) and 194 ms (L) on the main thread.
- **Harness gaps:** the X11a check missed a short message in 2 of 3 repeats. A fix and a rerun at `8d1c28b` gave 3
  of 3. In 22 of 120 trials of the R3.1c sweep, the loop did not see the backup file, so no kill happened. Each sweep
  still reached its quota (finding 3). This gap is open.

## Context & terms

- **R-007:** the tracked risk that a storage fault weakens the brute-force lockout (FR-174). Change F2 left four
  residuals:
  - **R-007/1:** a cold-start read failure leaves the manager empty, so a stored lockout is not enforced.
  - **R-007/2:** the degraded fallback of a failed write lives in memory only and ends with the process.
  - **R-007/3:** a process death before an admitted change reaches storage loses that change.
  - **R-007/4:** a failed reset leaves the old count and deadline in storage, and a restart brings them back.
- **Test plan and device plan:** `docs/process/active/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md` defines phase P2
  and the case IDs: H01 to H06 (healthy storage), R1.1 to R4.4 (one group for each residual), and X cases
  (cross-cutting). `docs/testing/M7_WP2_F2H_P2_DEVICE_PLAN.md` gives the device procedures, repeat counts, and the
  lead decisions. The JVM lane is the 2026-09-28 JVM baseline report.
- **Callers:** S is the self-gate (`MainActivity`). L is the legacy lock screen (`LockScreenActivity`) over the
  Clock app, which the accessibility detector (`AppDetectionService`) protects.
- **Fixtures:** `D=(count, wall deadline)` is the stored pair. `Z=(0,0)`, `C4=(4,0)`, `L5=(5, now+30 s)`,
  `L8=(8, now+240 s)`. The threshold is 5 failures and the first lock window 30 s.
- **Harness:** `scripts/r007/p2_device.sh` runs one segment (a group of cases) per command, with its own evidence
  file, settings record, and exit handler. A wrapper around the storage adapter logs each read and write
  (`R007Fault` lines) and runs the injected fault script. A fixture writer and an inspector (androidTest
  instrumentation) set and read the stored pair. The option `-k` reruns selected cases of a segment.
- **Verifier entry (V):** a PIN that reached verification. V is exact from the wrapper write lines while no write
  is held, and inferred from the gate state otherwise. The evidence labels each count.
- **Two verdicts:** "As predicted" says whether the baseline matches the prediction of the device plan. "Objective
  met" says whether the security objective of the case holds. A correct reproduction of a residual is "as
  predicted: yes" and "objective met: no". A repeat that is not as predicted, a control that cannot lose a lock,
  and a measurement get the objective "na" (lead decision 6). The objective column counts the "yes" and "no"
  repeats and gives the "na" repeats apart.
- **Gate retry:** on the Moto G, the lock screen over Clock sometimes closes on its first launch. The harness then
  launches Clock again, up to 3 times, and records each retry.

## Procedure

```
bash scripts/r007/p2_device.sh -s ZT4229HQ6X SEGMENT
bash scripts/r007/p2_device.sh -s ZT4229HQ6X -k CASE SEGMENT
```

1. Each segment started with the phone unlocked. The preflight recorded the device settings, turned stay-awake on,
   locked the rotation, turned biometric unlock off in the app (on only for the biometric segment), and wrote `Z`.
2. Only the reboot and biometric segments used `-o` (operator present). The screen-off steps of H06 and R3.3 are a
   proposed skip (device plan section 5.1), so their markers record `screen_cycle=skipped`.
3. The reruns used `-k R1.2a` (cold-read) and `-k X11a` (biometric).
4. The exit handler of each segment restored the store, the app settings file, and the device settings.

| Segment | Evidence file (`build/r007-evidence/`) | Start–end (UTC) | Boot | Result |
|---|---|---|---|---|
| healthy | `p2_healthy-ZT4229HQ6X-20261003T014423Z.log` | 2026-10-03 01:44–03:09 | `a0b7b4db` | 80 of 80 as predicted |
| cold-read | `p2_cold_read-ZT4229HQ6X-20261003T031717Z.log` | 03:17–05:52 | `a0b7b4db` | 90 of 91 as predicted |
| degraded | `p2_degraded-ZT4229HQ6X-20261003T082633Z.log` | 08:26–10:15 | `a0b7b4db` | 60 of 60 as predicted |
| death (stopped) | `p2_death-ZT4229HQ6X-20261003T130223Z.log` | 13:02–14:06 | `a0b7b4db` | 72 of 72 as predicted, stopped in R3.1c S (finding 3) |
| death | `p2_death-ZT4229HQ6X-20261003T142353Z.log` | 14:23–17:54 | `b93cfb50` | 220 of 220 as predicted; 22 sweep trials without a kill |
| reset | `p2_reset-ZT4229HQ6X-20261003T192349Z.log` | 19:23–20:59 | `b93cfb50` | 54 of 54 as predicted |
| cross | `p2_cross-ZT4229HQ6X-20261004T005259Z.log` | 2026-10-04 00:53–01:22 | `b93cfb50` | 33 of 33 as predicted |
| reboot | `p2_reboot-ZT4229HQ6X-20261004T041000Z.log` | 04:10–04:40 | 12 boots | 11 of 11 as predicted |
| biometric | `p2_biometric-ZT4229HQ6X-20261004T045647Z.log` | 04:56–05:08 | `c6eaac84` | 10 of 10 as predicted; 2 X11a repeats without a record |
| R1.2a rerun | `p2_cold_read-ZT4229HQ6X-20261004T190945Z.log` | 19:09–19:18 | `c6eaac84` | 12 of 12 as predicted |
| X11a rerun | `p2_biometric-ZT4229HQ6X-20261004T214642Z.log` | 21:46–21:49 | `c6eaac84` | 3 of 3 as predicted |

Each run, including the stopped one, ended with `settings-restore match=yes`, the store back at `Z`, and the app
settings file restored. The stopped death run does not count toward the 559 repeats of the headline: the death
rerun replaces it.

## Results

In the residual tables, "0 of N" in the objective column is the expected baseline result: the phone showed the
weakness in every repeat. It is not a test failure.

### Healthy-storage controls (H), and the construction-read cost (R1.4d)

| Case | Observed baseline (S and L) | As predicted | Objective met |
|---|---|---|---|
| H01 | Over `Z`, a gate left open made 1 read (the construction read) and 0 writes. | 6 of 6 | 6 of 6 |
| H02 | Wrong PINs 1 to 4 committed `(1,0)` to `(4,0)`. The fifth committed `L5`, and the gate blocked the next two attempts. V was 5 (exact) in each repeat, from 7 submissions. | 6 of 6 | 6 of 6 |
| H03 | From `L5`, the countdown fell to the deadline with no early opening, no late block, and no write during the countdown. The sixth wrong PIN gave a 60 s window (59,995 to 59,996 ms). From `(11, expired)`, the next wrong PIN gave the 30 min cap (1,799,995 to 1,799,998 ms). | 6 of 6 | 6 of 6 |
| H04 | From `C4`, a correct PIN opened the gate while the write was still held. The stored pair after the release was `Z`. | 6 of 6 | 6 of 6 |
| H05 | A kill and relaunch reloaded `(1,0)` and `(4,0)` as open, `(8, now+10 min)` as blocked with 538 to 583 s left, and `(8, expired)` as open. No reload wrote. | 24 of 24 | 24 of 24 |
| H06 | Holds of at least 1 s, 5 s, and 40 s lasted 4.9 to 45.7 s (`held_ms`). During each hold, the UI dumps that showed the gate showed no ANR dialog. A second wrong PIN queued behind the hold, and the last admitted pair was stored. A threshold write under a hold blocked the gate, and a reset under a hold unlocked it. | 30 of 30 | 30 of 30 |
| R1.4d | 30 cold starts per caller path. Main-thread construction read: S 71 to 88 ms (median 82); L (at the detector bind) 87 to 248 ms (median 194). | 2 of 2 | na (measurement) |

### R-007/1: cold-start read failure (R1)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R1.1a (L) | The construction read at the detector bind threw over count 5 (10 min window). When the harness opened Clock about 11 s later, the re-seed read loaded the lock and the gate blocked. The lock was not enforced for 13.8 to 14.5 s. | 3 of 3 | 0 of 3 |
| R1.1b | Every read threw while the gate polled, then the host cleared the fault 1 s after the gate opened. The gate first blocked 5 to 7 s after it opened, after 22 to 39 failed reads. | 6 of 6 | 0 of 6 |
| R1.2a | Under a persistent read fault over `L8` and `Z`, the gate stayed open, and the stored pair did not change. The read rate was 4 per second (250 ms poll): 40 to 42 failed reads in 10 s, except one repeat (finding 1). The rerun gave 40 or 41 failed reads over 10.0 to 10.3 s, 3.90 to 3.97 reads per second. | 11 of 12; rerun 12 of 12 | 0 of 11, 1 na; rerun 0 of 12 |
| R1.2b | A wrong PIN over an unreadable `L8` committed `(1,0)`: the stored lockout was lost. Over `Z`, the same is the correct result (control). | 12 of 12 | 0 of 6, 6 na |
| R1.2c | A correct PIN over an unreadable `L8` committed `Z`. | 12 of 12 | 0 of 6, 6 na |
| R1.2d | When the fault ended after 10 s, the next poll loaded the stored pair: blocked over `L8`, open over `Z`. | 12 of 12 | 0 of 6, 6 na |
| R1.2e | Under a persistent read fault, each of 21 processes (the first and 20 restarts) gave 5 verifier entries (exact), for both callers. The control without the fault gave 5 in the first process and 0 or 1 in each restarted process. | 2 of 2, control 2 of 2 | 0 of 2, control 2 of 2 |
| R1.3a | A wrong PIN while the old value was held in the re-seed read: after the release the gate showed "incorrect", the old lock did not come back, and the stored pair was `(1,0)`. | 6 of 6 | 0 of 6 |
| R1.3b | A correct PIN in the same window: the gate stayed open, and the stored pair was `Z`. | 6 of 6 | 0 of 6 |
| R1.3f | A wrong PIN whose write failed: the gate showed the degraded lock (26 to 27 s left), not the old lock, and the old pair stayed stored. | 6 of 6 | 0 of 6 |
| R1.4a (S) | A construction read held for 40 s on the main thread at a self-gate start. The read took 42.2 to 43.5 s. No UI dump returned a screen during the hold, so the ANR record is unknown. No `am_anr` or `am_kill` event. | 3 of 3 | 0 of 3 |
| R1.4b (L) | The same hold at the detector bind. The host saw the process die 38 to 43 s into the hold. The event log shows an `am_anr` at 39 s (`executing service …AppDetectionService`, a service timeout) and an `am_kill` `bg anr` at 39 s. The stored `L5` stayed. | 3 of 3 | 0 of 3 |
| R1.4c | A held re-seed read: five wrong PINs during the hold began 0 writes. After the release, 5 writes ran (V = 5, exact), and the last pair was stored. | 6 of 6 | 0 of 6 |

### R-007/2: degraded enforcement lost on restart (R2)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R2.1, kill at 5 s | A failed below-threshold write (`ReturnFalseBeforeCommit` or `Throw`, 5 repeats each) armed the 30 s degraded lock. The kill landed 6.9 to 9.3 s after the write returned, and the gate opened at once after the restart. Lost enforcement: 20.7 to 23.1 s. The stored pair stayed `Z`. | 20 of 20 | 0 of 20 |
| R2.1, kill at 25 s | Kill 26.6 to 27.8 s after the return: 2.2 to 3.4 s lost. | 6 of 6 | 0 of 6 |
| R2.1, platform fault | A read-only preferences directory made `commit()` return false. Kill after 7.6 to 9.4 s: 20.6 to 22.4 s lost. | 6 of 6 | 0 of 6 |
| R2.2 | F1 failed and F2 committed `(2,0)`. The gate was blocked in the process. After the kill, the stored count was 2 and the gate open. | 6 of 6 | 0 of 6 |
| R2.3 | F4 failed after a 40 s hold, and F5 committed `L5` with a deadline in the past. The gate was blocked in the process and open after the kill. | 6 of 6 | 0 of 6 |
| R2.4 | Under persistent write faults (`ReturnFalseBeforeCommit` and `Throw`), each of 21 processes gave 1 verifier entry (exact). The stored pair stayed `Z`. | 4 of 4 | 0 of 4 |
| R2.4, deadline kills | Kills 26.6 to 27.7 s after the return lost the rest of the degraded lock. Kills 36.6 to 37.7 s after the return came after its end, so nothing was lost. | 12 of 12 | 0 of 6, 6 of 6 |

### R-007/3: death before admitted changes are durable (R3)

From the death rerun on boot `b93cfb50`. The critical cuts used `Z` and `C4` in turn.

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R3.1a control | A timed wrong PIN measured the window from the tap that reaches the app to the write: 73 to 848 ms (S), 91 to 1022 ms (L). | 2 of 2 | na (measurement) |
| R3.1a | The host started the last tap in the background and killed during the PIN check. The kill command ran 507 to 561 ms (S) and 604 to 696 ms (L) after the tap start. Each kill found the main thread running, with 43 to 52 CPU ticks since the tap, and no `WRITE` line. The stored pair stayed old, and the gate was open after the restart. | 20 of 20 | na (no check result before the kill) |
| R3.1b | The first wrong PIN held before its commit, the second waited in the queue. After the kill, the stored pair was the old one, and the gate was open. From `C4`, the threshold lock was lost. | 20 of 20 | 0 of 20 |
| R3.1 held | One wrong PIN held before its commit, then a kill: the old pair stayed, and the gate was open. | 20 of 20 | 0 of 20 |
| R3.1c | The mid-commit sweep: 60 trials per caller, killed when the backup file appeared, after 0, 2, 5, or 10 ms. S: 52 trials killed, 23 inside the platform write, 28 after the return, 1 after the file write. L: 46 killed, 19 inside the platform write, 27 after the return. Each delay-0 trial that killed was inside the platform write (13 S, 11 L). The 42 trials inside the platform write kept the old pair, so the verified failure was lost. In 22 of them, the store file was empty at the death, and the load restored the backup file. The 56 later trials kept the new pair. No trial gave a read error. In 8 (S) and 14 (L) trials, no kill happened (finding 3). | 98 of 98 | 98 of 98 (complete pair) |
| R3.1d | The write committed and then held before its return. After the kill, the new pair was stored: no loss. | 20 of 20 | 20 of 20 |
| R3.2 | From `(2,0)`, four held writes: F3, F4, a correct PIN, and F1 of the new streak. After k released writes and a kill, the stored pair was the committed prefix: `(2,0)`, `(3,0)`, `(4,0)`, `Z`, `(1,0)` for k = 0 to 4. | 10 of 10 | 2 of 10 (k = 4 only) |
| R3.3 | The first write held. Five wrong PINs reached verification before the gate first blocked, 48 to 50 s in, with the threshold lock from memory. The gate opened again at 71 to 74 s, and a sixth wrong PIN blocked it again at 90 to 92 s (inferred V = 6 from 8 submissions). Released at 40 s, all writes drained and the last pair (count 6 with its deadline) was stored. Never released, the kill left `Z`: the 6 verified failures were lost. | 12 of 12 | 6 of 12 (the 40 s ending) |
| R3.4 | The write committed and then reported false. From `Z`, the gate was blocked in the process (degraded), the stored pair was `(1,0)`, and the gate was open after the restart. From `C4`, the stored `L5` blocked the gate after the restart. For a reset, the gate unlocked, and `Z` was stored. | 18 of 18 | 18 of 18 (stored state; Notes) |

### R-007/4: failed reset brings stale enforcement back (R4)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| R4.1a | From `(4,0)`, a correct PIN unlocked while its clear failed (`ReturnFalseBeforeCommit`, `Throw`, or `HoldBeforeCommit` never released). After a kill 5 s later, the stale `(4,0)` was stored, and the first wrong PIN after the relaunch locked for 30 s (29,996 to 29,999 ms). | 20 of 20 | 0 of 20 |
| R4.1b | Over an unreadable `(8, now+10 min)`, the gate was open, a correct PIN unlocked, and the clear failed. After the host cleared the read fault and killed the app, the relaunch blocked with 533 to 556 s left: the old lock came back after an accepted success. | 6 of 6 | 0 of 6 |
| R4.1c | A read-only preferences directory made the clear's `commit()` return false. The stale `(4,0)` stayed stored. | 6 of 6 | 0 of 6 |
| R4.2 | After a failed clear, the process made exactly 1 write in 60 s: no retry. The stale pair stayed. | 6 of 6 | 0 of 6 |
| R4.3 | After a failed clear, the next wrong PIN committed `(1,0)` and replaced the stale pair. With the clear held and failing, a wrong PIN queued behind it committed after it. | 12 of 12 | na (no retry at baseline) |
| R4.4 | From `L5` with every write failing (`ReturnFalseBeforeCommit` and `Throw`), the first process waited 17 to 19 s for the stale lock. In each of the 20 restarted processes, the harness saw the gate open 2 to 5 s after it appeared, and the correct PIN's clear failed again. After the last restart, the first wrong PIN locked for 60 s (59,997 to 59,999 ms) from the stale count 5. The stored pair stayed count 5. | 4 of 4 | 0 of 4 |

### Cross-cutting cases (X)

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| X09a | Two taps on the last digit while the first write was held: the second tap started a new entry, so there was 1 write for the completed entry, and `(1,0)` was stored. | 6 of 6 | na (no double submit possible, device plan section 5) |
| X09b | HOME and back during the held write: 1 write, `(1,0)` stored, no second unlock. | 6 of 6 | 6 of 6 |
| X09c | A rotation during the held write (`user_rotation` 1, then 0): the gate came back open, 1 write, `(1,0)` stored. | 6 of 6 | 6 of 6 |
| X10 | Both callers in one process. A wrong PIN on one caller with its write held, then a correct PIN on the other: the writes ran in order (`(1,0)`, then `Z`), and only the second caller's target unlocked. With a kill before the release, `Z` stayed stored (control from `Z`). | 12 of 12 | 6 of 6, 6 na |
| X16 (S) | A damaged store file (unparseable XML) over `(8, now+10 min)`: the inspection read `(0,0)`, the gate was open, and the next wrong PIN stored `(1,0)`. The stored lockout was lost. | 3 of 3 | 0 of 3 |

Biometric cases (caller L, biometric unlock on, operator present):

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| X11a | A finger that is not enrolled, from `C4`: the prompt said "Not recognized", nothing was written, and `(4,0)` stayed. Only 1 of 3 repeats gave a record in the recorded run (finding 4). In the rerun, each repeat logged 1 rejected attempt. | 1 of 1; rerun 3 of 3 | 1 of 1; rerun 3 of 3 |
| X11b | The harness cancelled the prompt with "Use PIN": the PIN pad showed, nothing was written, and `(4,0)` stayed. | 1 of 1 | 1 of 1 |
| X11c | Repeated mismatches until the system biometric lockout closed the prompt: the PIN pad showed, nothing was written, and `(4,0)` stayed. | 1 of 1 | 1 of 1 |
| X11d | An enrolled finger from `C4` unlocked, and the reset stored `Z`. | 3 of 3 | 3 of 3 |
| X11e | An enrolled finger over an unreadable `(8, now+10 min)` (every read throws): the prompt showed instead of the lock, the fingerprint unlocked, and `Z` was stored. A biometric success cleared the stored 10 min lock. | 1 of 1 | 0 of 1 |
| H04, biometric | From `C4` with the reset write held, an enrolled finger opened the gate before the write returned, and `Z` was stored. | 3 of 3 | 3 of 3 |

### Reboot variants

11 reboots, each with a new boot id. The operator unlocked the phone after each one. The callers alternate over the
three repeats of each residual (S, L, S).

| Case | Observed baseline | As predicted | Objective met |
|---|---|---|---|
| H05, active | `(8, now+10 min)` reloaded after the reboot as blocked on both callers, with 480 s (S) and 465 s (L) shown and 490 s left against the stored deadline. | 1 of 1 | 1 of 1 |
| H05, expired | `(8, expired)` reloaded as open on both callers: the expired lockout did not come back. | 1 of 1 | 1 of 1 |
| R2.1 | From `(8, expired)`, a wrong PIN with a failing write blocked the gate in the process: the degraded lock of count 9 (480 s on the lock ladder). After the reboot, the stored pair was still `(8, expired)`, and the gate was open. | 3 of 3 | 0 of 3 |
| R3.1 held | One wrong PIN held before its commit, then a reboot: the old pair stayed (`Z`, `C4`, `Z`), and the gate was open. | 3 of 3 | 0 of 3 |
| R4.1a | From `(4,0)`, a correct PIN with a failing clear, then a reboot: the stale `(4,0)` stayed, and the first wrong PIN locked for 30 s (29,994 to 29,999 ms). | 3 of 3 | 0 of 3 |

## Findings

1. **R1.2a timing artifact (cold-read, S, `L8`, repeat 2).** The repeat counted 53 failed reads against a
   predicted band of 30 to 50. In the counted window, the app's reads were 251 to 259 ms apart, as in the other
   repeats. The counted reads covered about 13.2 s instead of about 10 s: the log read that ends the window came
   about 3 s late. The log clear before the window was slow by about 3 s too: the read numbers jump from 48 to 62
   across a 3.5 s gap, so the lines of 13 reads were lost between the save and the clear. The stored pair did not
   change. The check compared a raw count with a band that assumes a 10 s window. Commit `6f28d8a` changed the check
   to a rate over the measured span (3 to 5 reads per second over at least 9 s) and added the case selection `-k`.
   The rerun of R1.2a alone at that commit gave 12 of 12 as predicted, with the stored pair unchanged in each
   repeat.
2. **Gate retries.** Caller L needed a second Clock launch 5 times in healthy, 12 times in cold-read, 12 times in
   degraded, 28 times in the death rerun, 18 times in reset, once in cross, and 3 times in reboot. The R1.2a rerun
   needed one. Twice the gate showed only on the third launch: in degraded (R2.4 L) and in the death rerun (R3.1a L,
   trial 2). No case needed more launches. Each retry is in the evidence (`## gate-retry` lines).
3. **R3.1c trials without a kill.** In a trial, an on-device loop waits for the preferences backup file and kills
   the app when the file appears. When the loop does not see the file within 30 s, no kill happens, and the run
   counts a failure. The evidence file then has the trial header without a case record.
   - In the first death run, the loop missed the file in 14 of 45 completed trials of caller S. In a missed trial,
     the app's write committed normally (10 to 13 ms from `BEGIN` to `RETURNED` in the first 7 misses). The misses
     were bunched at the start: 7 of the first 12 trials (1 to 3, 5, 8, 9, 12), then 7 of the next 33. The 31 trials
     with a kill were 16 inside the platform write and 15 after the return. All 72 case repeats with a record were
     as predicted.
   - The run was stopped with SIGTERM (exit status 143) at about 14:06, during trial 46, to check the device state.
     The decision to stop used an early tally, from the first trials, when the misses were bunched. At the stop, the
     sweep already had its 10 trials inside the platform write, so it would have ended after trial 60 without the
     stop. The exit handler restored the store and the settings.
   - On the phone, a poll of the loop took 17 to 24 µs, also under `run-as`, and a `run-as` start took about 250 ms.
     The loop runs in the root cpuset (all cores).
   - A probe on 2026-10-03 polled for the backup file during 20 fixture-writer commits. It saw the file 16 times
     before a reboot (14 h 22 min uptime, 219 MB free) and 18 times after it (4 min uptime, 148 MB free). So the
     reboot did not change the catch rate much. The probe is not evidence of record.
   - `/data` is f2fs with `fsync_mode=nobarrier` and `inline_data`. The kernel f2fs documentation says that
     `nobarrier` issues no cache flush for non-atomic files, and that small files (about 3.4 KB or less) are stored
     in the inode block. The store file is far smaller. A commit renames the file to the backup, writes and syncs
     the new file, and then deletes the backup (AOSP `SharedPreferencesImpl.writeToFile`). So the backup file can
     exist for much less than a millisecond. This explains a partial catch rate, but it was not measured.
   - The device plan's probe of 2026-09-29 caught 3 of 3 trials with delay 0, and the smoke run caught the first
     trial of each caller.
   - In the death rerun after the reboot, the loop missed 8 of 60 trials (S) and 14 of 60 (L). Each sweep still
     reached its 10 trials inside the platform write (23 and 19). The misses count as failures of the segment, so
     the rerun ended with exit status 1 and 22 failures, while all 220 case repeats were as predicted.
4. **X11a detection (biometric).** Two of the three X11a repeats ended without a record. In repeat 1 the harness saw
   "Not recognized" but then found no "Use PIN" button in the next UI dump. In repeat 3 the operator saw "Not
   recognized", but no UI dump within 120 s caught it: the message shows for about 2 s, and a dump takes 2 to 5 s.
   Neither is an app result. The accessibility event stream of the phone does not carry the message either. The
   biometric service logs each rejected attempt with the owner of the prompt
   (`Biometrics/AuthenticationClient: onAuthenticated(false) … Owner: com.applock`). Commit `8d1c28b` makes X11a wait
   for that log line and keep it in the evidence. The rerun of X11a alone at that commit gave 3 of 3 as predicted:
   1 rejected attempt in each, no write, and `(4,0)` stayed stored.
5. **USB debugging prompt after a reboot.** During the reboot segment, the operator had to allow USB debugging again
   twice. The PC uses one adb key. The likely cause is a prompt accepted without "Always allow from this computer".
   It did not change a result: the boot and unlock wait allows 9 min.

## Device facts (Moto G 2025, API 35)

- adb connects only after the first unlock after a boot.
- `adb logcat` waits forever for a device that is not connected. `adb shell`, `get-state`, and `reboot` fail at
  once. The harness reads the log through `r007_logcat`, which fails at once without a device.
- R1.4b death: a service-timeout ANR (`executing service …AppDetectionService`) and an `am_kill` `bg anr`, both 39 s
  into the hold.
- In the R1.4a holds of the main thread at a self-gate start (42.2 to 43.5 s), no `am_anr` event came, and no UI
  dump returned a screen.
- `uiautomator dump` fails ("Killed") while `uiautomator events` runs.
- In the probe of 2026-10-03, the 1-minute load average was 12.3 before the reboot and 14.8 after it. A `top`
  sample during the same investigation showed about 91% idle CPU (728% of 800%). On this phone, the load average is
  not a measure of CPU use.

## Notes

- **R3.4 objective.** The device check marks the objective met when the stored pair is the new pair and both gate
  states are as predicted. The JVM lane gave R3.4 the objective "no" for the accounting of the false result: no
  `LOCKOUT_TRIGGERED` audit for a durable lockout, a failed clear reported for a durable clear, and 30 s of
  in-process lock over a stored count 1. The device lanes do not observe the audit, and the device plan predicts
  the in-process lock. So the device "yes" means that no admitted change was lost. It does not replace the JVM
  verdict on the accounting.
- **R3.1c objective.** The objective of the sweep is a complete pair after the death: the old one or the new one,
  never a torn or unreadable store. The loss of the verified failure in the 42 trials inside the platform write is
  the R-007/3 loss of R3.1b and R3.1 held, at a later cut.
- **Trial files.** Four biometric files from the X11a fix work (`p2_biometric-ZT4229HQ6X-20261004T193006Z.log`,
  `…193440Z`, `…193551Z`, `…194510Z`) are not evidence of record.

## Smoke run (2026-10-01 and 2026-10-02) and the fixes it caused

The smoke run ran each segment once with one repeat (`-r 1`). It is not evidence of record. It found these
mismatches, each fixed in a separate commit before the recorded run:

- R1.1a and R1.1b: a gate retry took 20 s of the 30 s window of `L5`, so the lock had expired before the re-seed
  read. The cases now use a 10 min window (`3f5f186`).
- R1.4a: UI dumps failed while the main thread was held, so the ANR record was unknown. R1.4a and R1.4b now record
  the `am_anr` and `am_kill` events of the case process (`3f5f186`).
- R1.4b: the detector process died about 40 s into the hold. Lead decision 5 sets the prediction for that death
  (`3f5f186`, `1a03d06`).
- Reboot: after a reboot the Moto connects adb only after the first unlock, and one log read waited about 25 min
  for the phone. The reboot wait now asks for the unlock at once, with one deadline for boot and unlock, and log
  reads fail at once without a device (`ab749fc`).
- Biometric: the first try got an enrolled finger in X11a (operator step). The rerun passed.

## Not run on the Moto G lane

These cases are gaps of the device lanes (device plan section 5): R1.4 cancellation and X08; the R3.1a cut of the
JVM lane; R3.1e and X13; a double submit of one PIN entry (X09); the R3.2 threshold write before a reset; a direct
R4.1 reset over an active lockout; X04; X05 burst, X06, X07; X14; the candidate cases X01, X02, X03 (B3 part), X12,
X15; the audit and capture effects; and the latency distributions (moved to P3).

Proposed skips for the P2 exit (device plan section 5.1, the lead approves or rejects each): R1.1 with 100
concurrent polls; the R1.4 release with a throw; R1.4 screen-off, shutdown, and later admissions during the held
read; R1.4 thread stacks; the H06 holds of 0 to 1000 ms; the screen-off steps of H06 and R3.3; and the Moto G
background restrictions, load, and storage conditions (moved to P4).

## Disposition

- **The Moto G lane of P2 is complete at `ca57786`, with the reruns at `6f28d8a` and `8d1c28b`.**
- Each R-007 residual now has an observed reproduction on the JVM and on a real device with both live callers. The
  NucBox lanes (API 36 and API 30) are still open for the P2 exit.
- **RTM:** The RTM does not change.
- **Risk register:** The register does not change. The residual dispositions come at P5.

## Follow-ups

- **NucBox lanes:** write the operator plan, run API 36 and API 30, and file the NucBox report. Check the R3.1c
  catch rate there (finding 3). The option `-k` exists for the cold-read and biometric segments only.
- **Harness:** decide whether a sweep trial without a kill fails the segment. In the death rerun, 22 such trials set
  exit status 1 while all 220 case repeats were as predicted.
- **Lead, at the P2 exit:** approve or reject each proposed skip of device plan section 5.1. Confirm the reading of
  the R3.4 objective in the Notes.
- **Lead, before P3:** set the storage policy (input: R1.2, X16, X11e), the restart-guess limit (input: R1.2e,
  R2.4, R4.4), and the Option A policies.
- **Moto G setup:** before the next reboot segment, accept USB debugging with "Always allow from this computer"
  (finding 5).

## Appendix A: evidence files

The `build/` folder is not committed. Each file is under `build/r007-evidence/`.

| File | Lines | SHA-256 |
|---|---|---|
| `p2_healthy-ZT4229HQ6X-20261003T014423Z.log` | 1524 | `4a9528ca7964363e8e9dc264c01813a40621bd826c02b93f3083cb54f7ba647c` |
| `p2_cold_read-ZT4229HQ6X-20261003T031717Z.log` | 7811 | `1fdfbe7d4f6b9febdebd783720b08898849adf5520665b27f72a9514df2fa658` |
| `p2_degraded-ZT4229HQ6X-20261003T082633Z.log` | 1173 | `49edfc0cec836f86fa5f9b22f971692bcc032b48575c373d7d39ba8763e25de2` |
| `p2_death-ZT4229HQ6X-20261003T130223Z.log` | 1255 | `c2341029fceeff101590d345329b9046e83ee484da1b0d99cc8fef405dc31c72` |
| `p2_death-ZT4229HQ6X-20261003T142353Z.log` | 3647 | `cbc9017328c05d9f4ccf8f70c84cc8b0cafb92109f7f927737264e14344a8f2a` |
| `p2_reset-ZT4229HQ6X-20261003T192349Z.log` | 1674 | `203aac0f96cc25552e8fcad916e0fd33c3b681152daf0e50da58cb93da67e28b` |
| `p2_cross-ZT4229HQ6X-20261004T005259Z.log` | 576 | `4b32ba288c63af1c633c988f9f07e59bb5424805a43610487975071ed91f8c2d` |
| `p2_reboot-ZT4229HQ6X-20261004T041000Z.log` | 221 | `ec88087abf7cfa8206d629cf89128e8e24fc01572609783587482396c6275205` |
| `p2_biometric-ZT4229HQ6X-20261004T045647Z.log` | 219 | `e493b2186d94ddc2a38cb6aeb58b2fc801e406d8bd1ea32e974dab1c58b44559` |
| `p2_cold_read-ZT4229HQ6X-20261004T190945Z.log` | 1757 | `a634d44d4de40f23914a9d9dd14c3bda33aabad8e96ebe8cc3fdda1d44e82286` |
| `p2_biometric-ZT4229HQ6X-20261004T214642Z.log` | 78 | `47baa5b82eed1d2295aec6b30615ce9e3128009903ae8d341e091bc68e67b615` |
