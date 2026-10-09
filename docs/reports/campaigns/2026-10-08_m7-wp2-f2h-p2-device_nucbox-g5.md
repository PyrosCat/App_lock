# M7 WP2 F2 hardening, phase P2: R-007 baseline on the NucBox emulators (API 36 and API 30)

- **Date / captured:** The report is filed on 2026-10-08. The API 36 recorded run ran from 2026-10-06 07:48 to
  2026-10-07 03:20. The API 30 recorded run ran from 2026-10-07 21:39 to 2026-10-08 14:17, with the rerun of the
  `reboot` segment last. The smoke runs ran from 2026-10-05 22:17 to 2026-10-07 21:38. Times are UTC.
- **Author / host:** The author is the NucBox G5 of the fleet (Windows 11 Pro 10.0.26200, Intel N97, 4 cores, 12 GB
  RAM). It runs one Android emulator at a time, as `emulator-5554`, over local adb.
- **Tested revision:** On API 36, the `healthy` and `cold-read` segments and the first `degraded` run ran at commit
  **`2ce71df`**. The `degraded` rerun and the later segments ran at **`c47d642`**. On API 30, every segment of the
  recorded run ran at **`4864c4f`**. Each evidence header of a recorded run shows `host_changed_files=0`. The commits
  after `a16ff11` changed only the harness scripts, their host tests, and the changelog.
- **Installed APKs:** All runs used one build, made on the NucBox on 2026-10-05 at `a16ff11` (78 of 78 tasks, without
  the build cache, after a clean): app `7e5d89d1…76d803` and androidTest `acd0f1bf…904ed8` (SHA-256). No app code
  changed between `8d1c28b` and `4864c4f`.
- **Toolchain:** Android emulator 36.6.11.0 (build 15507667), platform-tools 37.0.0 (adb 1.0.41), build-tools 36.0.0,
  and the JDK 21 of Android Studio.
- **Scope:** the NucBox lanes of phase P2 of the R-007 test plan: the baseline characterization of the lockout manager
  on two AOSP emulators, Android 16 (API 36) and Android 11 (API 30), with both live callers. The report is not
  evidence for a requirement, so the RTM does not change.

## Headline

- **Both NucBox lanes of P2 are complete.** On API 36, 499 of 503 recorded case repeats were as predicted, and on
  API 30, 427 of 429. The Moto G lane gave 558 of 559.
- **Every repeat that missed its prediction traces to one app weakness:** the lock screen of caller L closes itself
  (finding 1). The same weakness cost 9 case records on API 36. It also left Clock, the protected app, without a
  lock screen for up to 162 s. The weakness lies outside R-007.
- **Both emulators reproduce all four R-007 residuals** with both live callers. Residuals /2, /3, and /4 also show
  across a reboot.
- **Key numbers (API 36 / API 30):** a persistent read fault allowed 5 verified guesses in each of 21 processes. A
  persistent write fault allowed 1 per process, with one exception (finding 1). A failed construction read left a
  stored 10 min lock unenforced for 13.9 to 14.3 s / 13.9 to 15.5 s. A restart cut a 30 s degraded lock short with
  22.6 to 23.8 s / 21.0 to 22.8 s left. After a failed reset and a restart, the first wrong PIN locked for 30 s.
- **The platform commit was atomic in all 62 kills of the R3.1c sweep (API 36).** The 22 kills inside the platform
  write kept the old pair and lost the verified failure. The 40 later kills kept the new pair.
- **Four committed harness fixes made the emulator lanes run** (findings 2 to 5). None changed a prediction, a
  verdict condition, or the app.
- **Gaps:** R3.1a and R3.1c did not run on API 30 (finding 2). An emulator graphics fault cost one API 30 `reboot`
  repeat, and the rerun of that segment gave 11 of 11 (finding 6).

## Context & terms

- **R-007:** the tracked risk that a storage fault weakens the brute-force lockout (FR-174). Change F2 left four
  residuals:
  - **R-007/1:** a cold-start read failure leaves the manager empty, so a stored lockout is not enforced.
  - **R-007/2:** the degraded fallback of a failed write lives in memory only and ends with the process.
  - **R-007/3:** a process death before an admitted change reaches storage loses that change.
  - **R-007/4:** a failed reset leaves the old count and deadline in storage, and a restart brings them back.
- **Plans:** the R-007 test plan (`2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`) defines phase P2 and the case IDs: H01
  to H06 (healthy storage), R1.1 to R4.4 (one group for each residual), and X cases (cross-cutting). The device plan
  (`M7_WP2_F2H_P2_DEVICE_PLAN.md`) gives the case steps, the repeat counts, the predictions, and the lead decisions.
  The NucBox plan (`M7_WP2_F2H_P2_NUCBOX_PLAN.md`) gives the emulator procedure. The Moto G lane is in the Moto G P2
  report of 2026-10-04.
- **Callers:** S is the self-gate (`MainActivity`). L is the legacy lock screen (`LockScreenActivity`) over the Clock
  app, which the accessibility detector (`AppDetectionService`) protects.
- **Fixtures:** `D=(count, wall deadline)` is the stored pair. `Z=(0,0)`, `C4=(4,0)`, `L5=(5, now+30 s)`,
  `L8=(8, now+240 s)`. The threshold is 5 failures and the first lock window 30 s.
- **Harness:** `scripts/r007/p2_device.sh` runs one segment (a group of cases) per command, with its own evidence
  file, settings record, and exit handler. A wrapper around the storage adapter logs each read and write (`R007Fault`
  lines) and runs the injected fault script. A fixture writer and an inspector (androidTest instrumentation) set and
  read the stored pair. The option `-r 1` limits each case to one repeat, `-k` selects cases, and `-o` runs the steps
  that need an operator: the screen-off steps and the reboots.
- **Smoke run and recorded run:** the smoke run runs each segment with one repeat. It checks the harness on the AVD
  and is not evidence of record. The recorded run runs every repeat of the device plan.
- **Verifier entry (V):** a PIN that reached verification. V is exact from the wrapper write lines while no write is
  held, and inferred from the gate state otherwise. The evidence labels each count.
- **Two verdicts:** "As predicted" says whether the baseline matches the prediction of the device plan. "Objective
  met" says whether the security objective of the case holds. Reproducing a residual correctly gives "as predicted:
  yes" and "objective met: no". In a residual case, a repeat that misses its prediction gets the objective "na"
  (lead decision 6), as do controls that cannot lose a lock and measurements. R3.3 with the 40 s ending and R3.4
  predict that the objective holds. The harness checks these objectives directly, so a repeat that misses there gets
  "no". In the objective column, "N of M" counts the "yes" repeats among those with a "yes" or "no" objective, and
  the "na" repeats appear separately.
- **Gate and gate retry:** the gate is the lock state that the harness reads from a UI dump: `open`, `blocked`, or
  `none` (no lock screen). When the lock screen of caller L does not show after a Clock launch, the harness launches
  Clock again, up to 3 times, and records each retry (`## gate-retry`). When the gate does not show after the third
  launch, the case stops.
- **Case run without a record:** a case that stopped before its `## CASE` marker. The console shows its `FAIL` line,
  and the evidence file has no marker for it. The run counts it as a failure.
- **AVD:** an Android Virtual Device. Both AVDs use an AOSP `default` system image for x86_64 (a `userdebug` build,
  so `su` exists), with no screen-lock credential.

## Procedure

```
bash scripts/r007/p2_device.sh -s emulator-5554 -r 1 -o SEGMENT    # smoke run
bash scripts/r007/p2_device.sh -s emulator-5554 -o SEGMENT         # recorded run
bash scripts/r007/p2_device.sh -s emulator-5554 -k "R3.1b R3.1held R3.1d R3.2 R3.3 R3.4" -o death   # API 30
```

1. **AVD start.** Each start was a cold boot, headless, with the hardware GPU: `-no-snapshot -no-audio -no-boot-anim
   -no-window -memory 3072 -gpu host`. The boot took 48 to 76 s on API 36 and 44 to 58 s on API 30. The emulator log
   showed `vulkan_mode_selected:host gles_mode_selected:host` at each start, so no GPU fallback was needed.
2. **Install and screen lock.** Before the first run on each AVD, the operator uninstalled both packages and checked
   that the package list was empty (exit status 0). Then the operator installed both APKs.
   `locksettings set-disabled true` turned the keyguard off on both AVDs. The user data of an AVD stays after a cold
   boot, so the install and the PIN 1234 stayed across the later starts.
3. **P1 check.** `scripts/r007/p1_validate.sh` ran at each tested revision before the segments: 18 of 18 passed on
   API 36 at `a16ff11`, `2ce71df`, and `c47d642`, and on API 30 at `2ce71df`, `c47d642`, and `4864c4f`. Each API 30
   check had 6 `## kill-via-su` lines (finding 2). The P1 harness also creates the PIN 1234 on a new install.
4. **Probes.** The read-only probe block of the NucBox plan gave the expected values on both AVDs (Device facts). A
   pre-check before each later start found both packages installed, the keyguard off, the probe settings, no
   settings record, and the detector unbound.
5. **Operator steps.** Every segment ran with `-o` on both API levels (user decision of 2026-10-05). So the
   screen-off steps of H06 and R3.3 ran for the first time on any device (finding 9). The AVDs have no screen lock,
   so no reboot needed an unlock.
6. **Settings and overrides.** `TAP_GAP` stayed at its default of 0.9 s. No wait of the harness was raised.
7. **Event capture.** During each segment, the host saved the device `events` log to a file (`logcat -b events`). The
   device log buffers hold 2 MiB, so this capture keeps the activity events of a whole segment. The capture is
   read-only and is not part of the harness. It traced the failures of finding 1.
8. **Exit.** The exit handler of each segment restored the store, the app settings file, and the device settings.
   After the last segment of each API level, the after-run checks of the NucBox plan found the probe settings, no
   settings record, no control directory, and no app process.

Recorded runs (the boot column gives the first 8 characters of the boot id):

| API | Segment | Evidence file (`build/r007-evidence/`) | Start–end (UTC) | Revision | Boot | Result |
|---|---|---|---|---|---|---|
| 36 | healthy | `p2_healthy-emulator-5554-20261006T074805Z.log` | 2026-10-06 07:48–09:11 | `2ce71df` | `3a6291a8` | 80 of 80 as predicted |
| 36 | cold-read | `p2_cold_read-emulator-5554-20261006T092748Z.log` | 09:27–11:44 | `2ce71df` | `3a6291a8` | 91 of 91 as predicted |
| 36 | degraded (replaced) | `p2_degraded-emulator-5554-20261006T115811Z.log` | 11:58–13:40 | `2ce71df` | `3a6291a8` | 57 of 57 as predicted; 3 case runs without a record (findings 1 and 4) |
| 36 | degraded rerun | `p2_degraded-emulator-5554-20261006T193645Z.log` | 19:36–21:28 | `c47d642` | `b95c91e0` | 59 of 60 as predicted (finding 1) |
| 36 | death | `p2_death-emulator-5554-20261006T212909Z.log` | 21:29–2026-10-07 00:37 | `c47d642` | `b95c91e0` | 173 of 176 as predicted; 7 case runs without a record (finding 1); 6 sweep trials without a kill |
| 36 | reset | `p2_reset-emulator-5554-20261007T003946Z.log` | 00:39–02:16 | `c47d642` | `b95c91e0` | 52 of 52 as predicted; 2 case runs without a record (finding 1) |
| 36 | cross | `p2_cross-emulator-5554-20261007T021815Z.log` | 02:18–02:52 | `c47d642` | `b95c91e0` | 33 of 33 as predicted |
| 36 | reboot | `p2_reboot-emulator-5554-20261007T025259Z.log` | 02:52–03:20 | `c47d642` | 12 boots | 11 of 11 as predicted |
| 30 | healthy | `p2_healthy-emulator-5554-20261007T213943Z.log` | 2026-10-07 21:39–23:07 | `4864c4f` | `d525272d` | 80 of 80 as predicted |
| 30 | cold-read | `p2_cold_read-emulator-5554-20261007T230803Z.log` | 23:07–2026-10-08 01:34 | `4864c4f` | `d525272d` | 89 of 91 as predicted (finding 1) |
| 30 | degraded | `p2_degraded-emulator-5554-20261008T013939Z.log` | 01:39–03:37 | `4864c4f` | `d525272d` | 60 of 60 as predicted |
| 30 | death (`-k`) | `p2_death-emulator-5554-20261008T033740Z.log` | 03:37–05:38 | `4864c4f` | `d525272d` | 100 of 100 as predicted |
| 30 | reset | `p2_reset-emulator-5554-20261008T053847Z.log` | 05:38–07:16 | `4864c4f` | `d525272d` | 54 of 54 as predicted |
| 30 | cross | `p2_cross-emulator-5554-20261008T071653Z.log` | 07:16–07:47 | `4864c4f` | `d525272d` | 33 of 33 as predicted |
| 30 | reboot (replaced) | `p2_reboot-emulator-5554-20261008T074834Z.log` | 07:48–08:12 | `4864c4f` | 11 boots | 10 of 10 as predicted; 1 case run without a record (findings 1 and 6) |
| 30 | reboot rerun | `p2_reboot-emulator-5554-20261008T135021Z.log` | 13:50–14:17 | `4864c4f` | 12 boots | 11 of 11 as predicted |

Each run ended with `settings-restore match=yes`, the store back at `Z`, and the app settings file restored. The
replaced runs do not count toward the totals of the headline. Their reruns replace them, as the rerun rules of the
NucBox plan allow.

## Results

Each table gives both API levels. Where they differ, the API 36 value comes first and the API 30 value follows a
slash. Moto G values appear only where they differ from both. In the residual tables, "0 of N" in the objective
column is the expected baseline: the emulator showed the weakness in every repeat. It is not a test failure.

On API 36, finding 1 cost caller L records in R3.1a, R3.1c, R3.1d, R3.3, R3.4, R4.2, and R4.4. The count columns
give the records that exist, and the Notes list what is missing.

### Healthy-storage controls (H), and the construction-read cost (R1.4d)

| Case | Observed baseline (S and L) | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| H01 | Over `Z`, a gate left open made 1 read (the construction read) and 0 writes. | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| H02 | Wrong PINs 1 to 4 committed `(1,0)` to `(4,0)`. The fifth committed `L5`, and the gate blocked the next two attempts. Each repeat reached V = 5 (exact) from 7 submissions. | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| H03 | From `L5`, the countdown fell to the deadline with no early opening, no late block, and no write. A sixth wrong PIN then gave a 60 s window (59,995 to 59,997 ms / 59,987 to 59,997 ms). From `(11, expired)`, one more wrong PIN reached the 30 min cap (1,799,992 to 1,799,997 ms / 1,799,981 to 1,799,998 ms). | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| H04 | From `C4`, a correct PIN opened the gate while its write was still held. Once released, that write stored `Z`. | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| H05 | After a kill and relaunch, `(1,0)`, `(4,0)`, and `(8, expired)` came back open. `(8, now+10 min)` came back blocked, with 523 to 582 s / 539 to 584 s left. None of the reloads wrote. | 24 of 24 / 24 of 24 | 24 of 24 / 24 of 24 |
| H06 | Holds of at least 1 s, 5 s, and 40 s lasted 4.1 to 58.0 s / 4.2 to 44.7 s (`held_ms`). The 5 s and 40 s holds included a screen-off step (finding 9). No UI dump showed an ANR dialog. A second wrong PIN queued behind the hold, and the last admitted pair reached storage. Under a hold, a threshold write blocked the gate, and a reset unlocked it. | 30 of 30 / 30 of 30 | 30 of 30 / 30 of 30 |
| R1.4d | 30 cold starts per caller path timed the construction read on the main thread. S: 22 to 64 ms (median 42) / 43 to 85 ms (median 55). L, at the detector bind: 28 to 91 ms (median 41) / 53 to 158 ms (median 64.5). The Moto G medians were 82 ms (S) and 194 ms (L). | 2 of 2 / 2 of 2 | na (measurement) |

### R-007/1: cold-start read failure (R1)

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| R1.1a (L) | The construction read at the detector bind threw over count 5 (10 min window). About 11 s later the harness opened Clock, and the re-seed read loaded the lock. Enforcement was missing for 13.9 to 14.3 s / 13.9 to 15.5 s. Every API 36 repeat ended with the gate blocked. On API 30, 2 of 3 repeats saw no gate, because the lock screen closed itself before the first sample (finding 1). | 3 of 3 / 1 of 3 | 0 of 3 / 0 of 1, 2 na |
| R1.1b | Every read threw while the gate polled. The host cleared the fault 1 s after the gate opened. The first block came 4 to 5 s later, after 19 to 26 / 20 to 28 failed reads. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R1.2a | A persistent read fault over `L8` and `Z` left the gate open and the stored pair unchanged. Reads ran at 3.96 to 3.97 per second / 3.93 to 3.97 per second. That made 40 or 41 failed reads over 9.8 to 10.1 s / 52 to 55 over 12.8 to 13.6 s. | 12 of 12 / 12 of 12 | 0 of 12 / 0 of 12 |
| R1.2b | Over an unreadable `L8`, a wrong PIN committed `(1,0)`: the stored lockout was lost. Over `Z`, the same commit is correct (control). | 12 of 12 / 12 of 12 | 0 of 6, 6 na / 0 of 6, 6 na |
| R1.2c | Over an unreadable `L8`, a correct PIN committed `Z`. | 12 of 12 / 12 of 12 | 0 of 6, 6 na / 0 of 6, 6 na |
| R1.2d | When the fault ended after 10 s, the next poll loaded the stored pair: blocked over `L8`, open over `Z`. | 12 of 12 / 12 of 12 | 0 of 6, 6 na / 0 of 6, 6 na |
| R1.2e | Under a persistent read fault, each of 21 processes (the first and 20 restarts) gave 5 verifier entries (exact), for both callers. Without the fault (control), the first process gave 5 and each restart 0 or 1. | 2 of 2, control 2 of 2 (both) | 0 of 2, control 2 of 2 (both) |
| R1.3a | A wrong PIN arrived while the re-seed read held the old value. Once the read was released, the gate showed "incorrect", the old lock did not come back, and `(1,0)` was stored. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R1.3b | A correct PIN in the same window kept the gate open and stored `Z`. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R1.3f | A wrong PIN with a failing write brought the degraded lock (28 s / 27 to 28 s left), not the old lock. Storage kept the old pair. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R1.4a (S) | At a self-gate start, the construction read held the main thread for 40 s and took 46.2 to 46.4 s / 40.7 to 47.7 s in total. No UI dump returned a screen during the hold, so the ANR record is unknown. Neither an `am_anr` nor an `am_kill` event came. | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |
| R1.4b (L) | The same hold at the detector bind ended differently per API level. On API 36, 1 repeat logged a service-timeout `am_anr` (`executing service …AppDetectionService`) 20.0 s after the read began. An `am_kill` `bg anr` and the death followed at 20.6 s, and the host saw the death at 24 s. In the other 2 repeats, the read returned after 41.4 to 41.8 s. On API 30, every repeat logged the `am_anr`, the `am_kill`, and the death 18.8 to 19.0 s after the read began. The host saw these deaths 18 to 22 s into the hold. Their markers miss the `am_kill` (finding 10). The Moto G logged both events at 39 s. `L5` stayed stored. | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |
| R1.4c | Five wrong PINs during a held re-seed read began no write. The release let 5 writes run (V = 5, exact), and the last pair reached storage. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |

### R-007/2: degraded enforcement lost on restart (R2)

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| R2.1, kill at 5 s | A failed below-threshold write (`ReturnFalseBeforeCommit` or `Throw`, 5 repeats each) armed the 30 s degraded lock. The kill landed 6.2 to 7.4 s / 7.2 to 9.0 s after the write returned. The restarted app opened the gate at once, so 22.6 to 23.8 s / 21.0 to 22.8 s of the lock were lost. Storage kept `Z`. | 20 of 20 / 20 of 20 | 0 of 20 / 0 of 20 |
| R2.1, kill at 25 s | A kill 26.3 to 26.8 s / 27.2 to 28.5 s after the return lost 3.2 to 3.7 s / 1.5 to 2.8 s. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R2.1, platform fault | A read-only preferences directory made `commit()` return false. Killed after 6.3 to 6.9 s / 7.5 to 9.5 s, the app lost 23.1 to 23.7 s / 20.5 to 22.5 s of the lock. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R2.2 | F1 failed, and F2 committed `(2,0)`. The process kept the gate blocked, but after the kill the count read 2 and the gate opened. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R2.3 | F4 failed after a 40 s hold, and F5 committed `L5` with a deadline in the past. Blocked in the process, the gate opened after the kill. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R2.4 | Under persistent write faults (`ReturnFalseBeforeCommit` and `Throw`), each of 21 processes gave 1 verifier entry (exact), and `Z` stayed stored. Process 16 of caller L on API 36 (`ReturnFalseBeforeCommit`) gave 0 instead: its lock screen closed itself before the PIN (finding 1). | 3 of 4 / 4 of 4 | 0 of 3, 1 na / 0 of 4 |
| R2.4, deadline kills | Kills 26.1 to 27.2 s / 27.5 to 28.3 s after the return lost the rest of the degraded lock. Kills 36.2 to 37.5 s / 37.3 to 38.2 s came after its end and lost nothing. | 12 of 12 / 12 of 12 | 0 of 6, 6 of 6 (both) |

### R-007/3: death before admitted changes are durable (R3)

The critical cuts used `Z` and `C4` in turn.

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| R3.1a control | A timed wrong PIN measured the window from the tap that reaches the app to the write: 118 to 665 ms (S), 79 to 639 ms (L). | 2 of 2 / not run (finding 2) | na (measurement) |
| R3.1a | The host started the last tap in the background and killed the app during the PIN check. The kill came 430 to 475 ms (S) and 397 to 439 ms (L) after the tap start. Each kill found the main thread running, 35 to 38 (S) and 31 to 34 (L) CPU ticks after the tap, with no `WRITE` line. The old pair survived, and the gate was open after the restart. | 17 of 17 / not run (finding 2) | na (no check result before the kill) |
| R3.1b | The first wrong PIN's write held before commit. The second PIN waited in the queue. The kill left the old pair, and the gate was open after the restart. From `C4`, this lost the threshold lock. | 20 of 20 / 20 of 20 | 0 of 20 / 0 of 20 |
| R3.1 held | One wrong PIN's write held before commit, and the host killed the app. The old pair survived, and the gate was open. | 20 of 20 / 20 of 20 | 0 of 20 / 0 of 20 |
| R3.1c | The mid-commit sweep killed the app when the backup file appeared, after 0, 2, 5, or 10 ms. Caller S: 54 of 60 trials killed, 20 inside the platform write and 34 after the return. Caller L: 8 trials killed, 2 inside and 6 after. Every delay-0 trial that killed was inside the platform write (14 S, 2 L). Kills inside the platform write kept the old pair, so the verified failure was lost. In 12 of these 22, the store file was empty at the death, and the load restored the backup file. The 40 later kills kept the new pair. In 6 trials of caller S, no kill happened (finding 7). | 62 of 62 / not run (finding 2) | 62 of 62 (complete pair) |
| R3.1d | The write committed and then held before its return. The kill found the new pair already stored, so nothing was lost. | 19 of 19 / 20 of 20 | 19 of 19 / 20 of 20 |
| R3.2 | From `(2,0)`, four writes held: F3, F4, a correct PIN, and F1 of the new streak. With k of them released before the kill, storage kept the committed prefix: `(2,0)`, `(3,0)`, `(4,0)`, `Z`, `(1,0)` for k = 0 to 4. | 10 of 10 / 10 of 10 | 2 of 10 / 2 of 10 (k = 4 only) |
| R3.3 | The first write held, and the screen went off and on during the hold (finding 9). Five wrong PINs reached verification before the threshold lock in memory first blocked the gate, 43 to 45 s / 54 to 59 s in. The gate opened again at 67 to 82 s / 74 to 80 s. A sixth wrong PIN blocked it at 84 to 100 s / 95 to 103 s (inferred V = 6 from 8 submissions). With a release at 40 s, all writes drained, and count 6 with its deadline was stored. Without a release, the kill left `Z`, and the 6 verified failures were lost. One API 36 repeat of caller L was not as predicted (finding 1). Moto G: 48 to 50 s, 71 to 74 s, and 90 to 92 s. | 10 of 11 / 12 of 12 | 4 of 11 / 6 of 12 (the 40 s ending) |
| R3.4 | The write committed and then reported false. From `Z`, the process showed the degraded lock, `(1,0)` was stored, and the gate was open after the restart. From `C4`, the stored `L5` blocked the gate after the restart. A reset unlocked the gate and stored `Z`. In two API 36 `C4` repeats of caller L, the lock screen closed itself until the lockout had ended, so the restart found the gate open (finding 1). | 13 of 15 / 18 of 18 | 13 of 15 / 18 of 18 (stored state; Notes) |

### R-007/4: failed reset brings stale enforcement back (R4)

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| R4.1a | From `(4,0)`, a correct PIN unlocked while its clear failed (`ReturnFalseBeforeCommit`, `Throw`, or `HoldBeforeCommit` never released). A kill 5 s later left the stale `(4,0)` in storage. After the relaunch, the first wrong PIN locked for 30 s (29,994 to 29,998 ms / 29,985 to 29,998 ms). | 20 of 20 / 20 of 20 | 0 of 20 / 0 of 20 |
| R4.1b | Over an unreadable `(8, now+10 min)`, the gate was open, a correct PIN unlocked, and the clear failed. The host then cleared the read fault and killed the app. The relaunch blocked with 517 to 555 s / 551 to 556 s left: the old lock came back after an accepted success. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R4.1c | A read-only preferences directory made the clear's `commit()` return false, and the stale `(4,0)` remained. | 6 of 6 / 6 of 6 | 0 of 6 / 0 of 6 |
| R4.2 | After a failed clear, the process made exactly 1 write in 60 s. It did not retry, and the stale pair remained. | 5 of 5 / 6 of 6 | 0 of 5 / 0 of 6 |
| R4.3 | After a failed clear, the next wrong PIN committed `(1,0)` and replaced the stale pair. When the clear was held and failing, a wrong PIN queued behind it and committed after it. | 12 of 12 / 12 of 12 | na (no retry at baseline) |
| R4.4 | From `L5` with every write failing (`ReturnFalseBeforeCommit` and `Throw`), the first process waited 6 to 16 s / 16 to 19 s for the stale lock. In each of the 20 restarted processes, the harness saw the gate open 2 to 4 s after it appeared, and the clear of the correct PIN failed again. After the last restart, the first wrong PIN locked for 60 s (59,993 to 59,998 ms / 59,985 to 59,998 ms), counting from the stale 5. Count 5 stayed in storage. | 3 of 3 / 4 of 4 | 0 of 3 / 0 of 4 |

### Cross-cutting cases (X)

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| X09a | Two taps on the last digit came while the first write was held, and the second tap started a new entry. The completed entry made 1 write, and `(1,0)` was stored. | 6 of 6 / 6 of 6 | na (no double submit possible, device plan section 5) |
| X09b | HOME and back during the held write: 1 write, `(1,0)` stored, and no second unlock. | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| X09c | A rotation during the held write (`user_rotation` 1, then 0) gave 1 write and stored `(1,0)`. Afterwards the gate was open again, except in 2 of 3 caller-L repeats on API 36, where the lock screen closed itself (finding 1). | 6 of 6 / 6 of 6 | 6 of 6 / 6 of 6 |
| X10 | Both callers shared one process: a wrong PIN on one with its write held, then a correct PIN on the other. The writes ran in order (`(1,0)`, then `Z`), and only the second caller's target unlocked. A kill before the release left `Z` (control from `Z`). | 12 of 12 / 12 of 12 | 6 of 6, 6 na (both) |
| X16 (S) | A damaged store file (unparseable XML) over `(8, now+10 min)` read as `(0,0)`. With the gate open, the next wrong PIN stored `(1,0)`: the stored lockout was lost. | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |

### Reboot variants

The callers alternate over the three repeats of each residual (S, L, S). No reboot needed an unlock.

| Case | Observed baseline | As predicted (36 / 30) | Objective met (36 / 30) |
|---|---|---|---|
| H05, active | The reboot brought `(8, now+10 min)` back blocked on both callers. API 36 showed 495 s (S) and 473 s (L), with 505 s left against the stored deadline. API 30 showed 491 s (S) and 475 s (L), with 501 s left. | 1 of 1 / 1 of 1 | 1 of 1 / 1 of 1 |
| H05, expired | `(8, expired)` came back open on both callers: the expired lockout stayed expired. | 1 of 1 / 1 of 1 | 1 of 1 / 1 of 1 |
| R2.1 | From `(8, expired)`, a wrong PIN with a failing write blocked the gate in the process with the degraded lock of count 9. The reboot left `(8, expired)` in storage, and the gate was open. | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |
| R3.1 held | One wrong PIN's write held before commit, and the host rebooted the emulator. The old pair survived (`Z`, `C4`, `Z`), and the gate was open. | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |
| R4.1a | From `(4,0)`, a correct PIN with a failing clear came before a reboot. The stale `(4,0)` remained, and the first wrong PIN locked for 30 s (29,985 to 29,998 ms / 29,979 to 29,998 ms). | 3 of 3 / 3 of 3 | 0 of 3 / 0 of 3 |

## Findings

1. **The lock screen of caller L closes itself (two triggers).** `LockScreenActivity.onPause()` calls `finish()`
   unless the gate is authenticated, already finishing, or waiting for a biometric result. The activity has no
   `onNewIntent` of its own. `ApplicationLockEngine` launches the lock screen on every foreground event of the
   protected app, and the manifest declares the activity `singleTop` with `noHistory`. Two triggers follow:
   - **Screen off.** Sleep pauses the activity (`wm_pause_activity … sleep`), and `onPause` finishes it.
   - **A second lock request.** Android delivers a repeated launch to the running `singleTop` activity as a new
     intent, and it pauses a resumed activity first (Android documentation of `Activity#onNewIntent`). That pause
     runs `onPause`, which finishes the lock screen. The event log shows `wm_new_intent`, then
     `wm_finish_activity … app-request`.
   - **Exposure.** Until the next detection event, Clock stays in the foreground with no lock screen. The longest
     gap, 162 s, came in API 36 R3.3 (caller L, 40 s ending, repeat 1). There, the screen-off pause and four later
     closings left Clock open until the harness killed the app. In API 36 R3.4 repeat 3 (caller L, `C4`), Clock
     showed for 17.8 s, 8.5 s of it inside the stored lockout. In API 30 R1.1a repeats 1 and 3, the gaps were 14.7 s
     and 12.2 s, both under the active 10 min lock.
   - **Lost PIN entries.** A lock screen that closes after the harness saw it sends the PIN taps to Clock, so no
     write begins. Process 16 of API 36 R2.4 (caller L, `ReturnFalseBeforeCommit`) read the store and never wrote.
     Its lock screen received a new intent 0.6 s after starting and closed 1.7 s later, 0.13 s after its first
     frame.
   - **Counts in the runs of record.** API 36 had 4 repeats not as predicted, all of caller L: R2.4, R3.3, and two
     R3.4. It also had 9 case runs without a record: R3.1a trial 8, R3.1c trial 9, R3.1d repeat 8, R3.3 repeat 3,
     three R3.4 repeats, R4.2 repeat 1, and one R4.4 run. In each of these 9, three Clock launches produced 11 to 17
     lock screens, and none stayed open. API 30 had 2 repeats not as predicted (R1.1a repeats 1 and 3).
   - **Gate retries.** Caller L needed 126 of them on API 36: 4 in healthy, 14 in cold-read, 10 in degraded, 58 in
     death, 30 in reset, and 10 in cross. API 30 needed 79, as many as the Moto G lane.
   - **One attribution is inferred.** For API 30 R1.1a repeat 1, the capture holds the lock screen's creation and a
     new intent 0.23 s later. A server pause (`userLeaving=false`) followed 0.7 s after that, then
     `completeFinishing`, and Clock resumed. Only the `wm_finish_activity` line is missing. No other lock screen of
     the 161 in that segment lacks it, and the device buffer had already rotated. Repeat 3 shows the same sequence
     with its `app-request` line, and every finish path of Android logs that line. The report therefore counts
     repeat 1 as a result of this finding, labelled as an inferred attribution (user decision of 2026-10-08). Its
     verdict stays "not as predicted".
   - **Rotation.** In the API 36 X09c repeats without a gate after the rotation, the lock screens closed after new
     intents in the same way. X09c judges only the writes, so its verdict does not depend on the gate.
   - **Moto G.** Gate retries occurred on the Moto G too. Its event logs were not traced, so the mechanism there is
     unconfirmed.
   - **Scope.** No storage fault takes part, so the finding lies outside R-007. Neither the app, the harness, nor
     the predictions changed for it. The first such failure stopped the API 36 smoke run. The user then ruled that a
     failure traced to this finding counts as a result, on both API levels.
2. **Android 11 denies the harness kill through `run-as`.** At `a16ff11`, the first P1 check on API 30 gave 7 passed
   and 15 failed. Every kill failed, and the other failures followed from the missed kills. For each kill, the events
   log held `avc: denied { sigkill } for comm="kill" scontext=u:r:runas_app tcontext=u:r:untrusted_app …
   permissive=0`. Android 16 allows the same kill. Commit `2ce71df` gives `r007_kill` a fallback through `su 0`
   after this denial. The fallback acts only when the denial names the kill's own sender process, at or after its
   start time, and when the app still has the same process ID. Each such kill adds a `## kill-via-su` line, with the
   denial line, to the evidence. The same commit adds the case selection `-k` to the `death` segment. Later P1 checks
   on API 30, at `2ce71df`, `c47d642`, and `4864c4f`, gave 18 of 18 with 6 such lines each. In the API 30 recorded
   run, 659 kills went through `su 0`. R3.1a and R3.1c kill through `run-as` only, in a timed kill and in a sweep,
   so they did not run on API 30.
3. **Android 11 keeps a crash mark for the detector.** At `2ce71df`, the API 30 smoke run of `healthy` failed every
   case of caller L with `the detector did not bind after the grant` (13 failures). The preflight had bound the
   detector and then stopped the app with `am force-stop`. Android 11 answered by listing the detector under "Crashed
   services" in `dumpsys accessibility` and dropping it from the enabled list. A later grant listed it as enabled
   again. Android 11 does not bind a service with a crash mark, though, and it clears the mark only for a service
   outside the enabled list. Android 16 sets no such mark (probe of 2026-10-06). Commit `c47d642` clears the mark
   before the grant: it lists the detector, deletes the list, and waits until the dump no longer names the detector
   as crashed. Each clearing adds a `## detector-crash-cleared` line, 109 of them in the API 30 recorded run.
4. **One fault-script read failed on API 36.** In the first recorded `degraded` run, the harness could not read its
   fault script back through `run-as` (R2.4, caller L, deadline kill at 35 s, repeat 3). The failure fell in a gap of
   about 3 s between an inspection and the next instrumentation start. No log line named a cause, so the operator
   stopped the recorded run after this segment. Commit `c47d642` retries the read once after 1 s and keeps the error
   text of `run-as`. A first failure adds a `## read-retry` line. No run after `c47d642` needed the retry, on either
   API level.
5. **Android 11 keeps an unfinished detector bind.** At `c47d642`, the API 30 smoke run of `cold-read` failed R1.4c
   for caller L with `the detector did not bind after the grant`. R1.4b, the case before it, holds the construction
   read of the detector for 40 s during its bind. Android 11 logged a service-timeout `am_anr` 19 s into the hold,
   killed the detector process, and started it again. The harness then revoked the access and stopped the app. A
   diagnostic probe of the accessibility dump found the detector still in Android 11's list of binding services
   ("Binding services"). At the next grant, the detector was enabled but stayed in that list, unbound, for 25 s:
   Android 11 does not bind a service from it again. Commit `4864c4f` clears the bind before each grant. It lists the
   detector as enabled, stops the app, and waits until the binding list no longer names the detector. Each clearing
   adds a `## detector-binding-cleared` line. The rerun of R1.4b and R1.4c at `4864c4f` gave 3 of 3 as predicted,
   with one such line. The API 30 recorded run had 3, one before each R1.4c repeat of caller L.
6. **An emulator graphics fault ended an app process on API 30.** In the first API 30 `reboot` run, R2.1 (caller L,
   repeat 2) ended without a record: `write 0 of process 2356 did not return`. The process had read the store but
   never wrote. Its first lock screen closed itself 0.17 s after its first frame (finding 1), and a second one opened
   0.18 s later. 7.5 s after the second lock screen drew its first frame, the app aborted:
   `am_crash … com.applock … Native crash,Aborted`. The crash record on the device names the cause. The render
   thread of the app aborted with `GL errors! frameworks/base/libs/hwui/pipeline/skia/SkiaOpenGLPipeline.cpp:127` in
   `SkiaOpenGLPipeline::swapBuffers`, so the GL path of the emulator (`-gpu host`) had returned errors to the
   hardware renderer. Later in the same run, the same abort took down the app and Clock within 0.7 s. A gate retry
   absorbed it, and that case kept its record. No other capture of either lane has an `am_crash` line. Which of the
   two events stopped the write cannot be settled: the tap times are not logged, and the reboots cleared the device
   log. The user chose a rerun of the whole segment, which the NucBox plan allows for a fault outside the app. The
   rerun gave 11 of 11 as predicted.
7. **R3.1c catch rate (API 36).** In 54 of 60 trials of caller S, the loop saw the backup file and killed. It missed
   trials 10, 23, 24, 25, 43, and 54, and each miss counts as a failure of the segment. With 20 trials inside the
   platform write, the sweep of caller S still reached its quota of 10. Caller L killed in each of its first 8
   trials. Its trial 9 ended without a record, and the sweep of caller L stopped there (finding 1). For comparison,
   the Moto G caught 52 of 60 (S) and 46 of 60 (L). `/data` is ext4 on the emulators and f2fs on the Moto G.
8. **R3.1a kill window (API 36).** From the tap that reaches the app to the write, the control measured 118 to 665 ms
   (S) and 79 to 639 ms (L). The Moto G measured 73 to 848 ms (S) and 91 to 1022 ms (L). Every trial of the recorded
   run counted: each kill landed during the PIN check, before any `WRITE` line.
9. **Screen-off steps (H06 and R3.3).** The NucBox lanes are the first to run these steps on any device. On each API
   level, 12 H06 repeats (5 s and 40 s holds) turned the screen off and on during a held write. So did the R3.3
   repeats with a record: 11 on API 36 and 12 on API 30. All H06 repeats were as predicted, and so were all R3.3
   repeats on API 30. One API 36 R3.3 repeat of caller L was not (finding 1). Because the screen-off pause closes
   the lock screen of caller L, the gate read after the screen comes on belongs to a new lock screen.
10. **The R1.4b markers of API 30 miss the `am_kill` event.** To fill the marker, the harness reads the `am_anr` and
    `am_kill` events of the case process from the events log (`r007_proc_events` in `lib_p2.sh`). Its `am_kill`
    pattern needs a field after the reason. Android 16 logs one (`[0,10831,com.applock,0,bg anr,205080]`), while
    Android 11 logs only five fields (`[0,6851,com.applock,0,bg anr]`). Each API 30 R1.4b marker therefore says
    `am_kill=none`, although the capture holds an `am_kill` `bg anr` for all three processes (6851, 8102, and
    9358). Replaying the pattern on the captured lines on the host returned the `am_anr` event alone. Only R1.4a and
    R1.4b use these events. Both R1.4b verdicts stand: the prediction needs only the `am_anr`, and the objective
    uses the death that the host saw. The defect is limited to the `am_kill` field of the three markers.

## Device facts (NucBox emulators)

| | API 36 | API 30 |
|---|---|---|
| AVD | `matrix_api36` | `matrix_api30def` |
| System image | `system-images;android-36;default;x86_64` | `system-images;android-30;default;x86_64` |
| Build fingerprint | `Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys` | `Android/sdk_phone_x86_64/generic_x86_64:11/RSR1.210722.013.A2/10067904:userdebug/test-keys` |
| Profile | `pixel_5`, 1080 x 2340, 440 dpi, 3072 MB RAM | `pixel_5`, 1080 x 2340, 440 dpi, 3072 MB RAM (boot flag; config 2 GB) |
| Settings before the runs | `stay_on_while_plugged_in=15`, rotation auto 1, user rotation 0, accessibility services `null`, accessibility 0 | `stay_on_while_plugged_in=7`, other values as API 36 |
| UI dump of the probe | 10018 B | 9294 B |
| `/data` | ext4, `noatime` | ext4, `noatime` |
| R1.4b, held read at the detector bind | `am_anr`, `am_kill` (`bg anr`), and death 20.0 to 20.6 s after the read began, in 1 of 3 repeats | `am_anr`, `am_kill` (`bg anr`), and death 18.8 to 19.0 s after the read began, in each repeat |
| `am_kill` event | 6 fields: `[0,10831,com.applock,0,bg anr,205080]` | 5 fields: `[0,6851,com.applock,0,bg anr]` (finding 10) |

- Both AVDs: `CLK_TCK` 100, 2 processors (`nproc`), log buffers of 2 MiB, screen-off timeout 2147483647 ms, AC power,
  and `com.android.deskclock` as the clock app.
- `adb -s emulator-5554 emu kill` ended the emulator in 3 to 6 s. The adb serial stayed `emulator-5554` across AVD
  changes and reboots.
- `run-as <package> kill -9` works on Android 16 and is denied by SELinux on Android 11 (finding 2). The API 30 image
  has `su` (`userdebug`). On Android 11, toybox `kill` prints `unknown pid` for every failed kill, also for a denied
  one, so only the audit line identifies the denial.
- Android 11 marks a force-stopped accessibility service as crashed (finding 3), and keeps an unfinished bind in its
  binding list (finding 5).
- On API 30 with `-gpu host`, the hardware renderer of an app aborted twice on GL errors of the emulator during the
  first `reboot` run (finding 6). The device keeps the crash records under `/data/system/dropbox` and
  `/data/tombstones`. Other tombstones of the API 30 AVD came from `com.android.bluetooth` at reboot shutdowns.
- A fork-heavy host test beside a device run filled the Windows commit charge (16.9 of 18.3 GB), and one P1 trial
  stalled for 11 min. The runs of record ran with no host test beside them.

## Notes

- **R3.4 objective.** The device check marks the objective met when the stored pair is the new pair and both gate
  states are as predicted. The device lanes do not observe the audit, so the device "yes" means that no admitted
  change was lost. It does not replace the JVM verdict on the accounting (Moto G report, Notes).
- **Missing records of caller L on API 36.** The 9 case runs without a record (finding 1) left fewer records than
  the plan asks. Records per case: R3.1a 7 of 10 trials, R3.1c 8 of 60 trials, R3.1d 9 of 10, R3.3 5 of 6, R3.4 6
  of 9, R4.2 2 of 3, and R4.4 1 of 2. In R3.1a and R3.1c, the failed trial also ended the loop, so the later trials
  did not run. Caller S has a record of every repeat.
- **Trials and probes.** These evidence files are not evidence of record:
  - `p1_validate-emulator-5554-20261006T040949Z.log`, `…043544Z.log`, and `…051640Z.log`: P1 trials on API 30 of
    the fix of finding 2.
  - `p1_validate-emulator-5554-20261006T061536Z.log`: the P1 check of that commit.
  - `p2_healthy-emulator-5554-20261006T180042Z.log`: the trial of the fixes of `c47d642`.
  - `p2_cold_read-emulator-5554-20261007T042533Z.log`: the diagnostic probe of finding 5.
  - `p2_cold_read-emulator-5554-20261007T072524Z.log`: the trial of the fix of finding 5.
- **AVD starts.** The emulator stopped and started again between runs, never during a run of record. Each start
  gives a new boot id (Procedure table).

## Smoke runs and the fixes they caused

API 36 smoke run at `a16ff11`, boot `57305d61`, 2026-10-05 22:17 to 2026-10-06 01:43:

| Segment | Result | Note |
|---|---|---|
| healthy (`-o`) | 27 of 28 as predicted | H06, caller L, 40 s hold: not as predicted (finding 1) |
| healthy without `-o` | 28 of 28 as predicted | A fallback of the NucBox plan, later withdrawn: the user chose `-o` for every segment |
| cold-read | 33 of 33 as predicted | |
| degraded | 18 of 18 as predicted | |
| death | 34 of 35 as predicted | R3.4, caller L: not as predicted (finding 1). R3.1c: no kill in trial 1 of caller L |
| reset | 16 of 16 as predicted | |
| cross | 11 of 11 as predicted | |
| reboot | 5 of 5 as predicted | 5 reboots, no action needed |

API 30, in order:

- **P1 check at `a16ff11`:** 7 passed, 15 failed (finding 2). Fix `2ce71df`. The P1 check at `2ce71df` gave 18 of 18.
- **Smoke run of `healthy` at `2ce71df`:** 14 of 14 as predicted (caller S only), 13 failures of caller L
  (finding 3). The smoke run stopped there. Fix `c47d642`.

API 30 smoke run at `c47d642`, boot `eabd7ecf`, 2026-10-07 03:28 to 08:46:

| Segment | Result | Note |
|---|---|---|
| healthy | 28 of 28 as predicted | |
| cold-read | 32 of 32 as predicted | R1.4c, caller L: ended without a record (finding 5) |
| degraded | 18 of 18 as predicted | |
| death (`-k`) | 26 of 26 as predicted | Without R3.1a and R3.1c |
| reset | 16 of 16 as predicted | |
| cross | 11 of 11 as predicted | |
| reboot | 5 of 5 as predicted | 5 reboots, no action needed |

The R1.4c case run led to fix `4864c4f`. The rerun of R1.4b and R1.4c at `4864c4f` (2026-10-07 21:34) gave 3 of 3
as predicted (`p2_cold_read-emulator-5554-20261007T213417Z.log`).

In the API 36 recorded run, an untraceable fault-script read stopped the first `degraded` run (finding 4). After fix
`c47d642`, the `degraded` rerun and the later segments ran at that commit.

## Not run on the NucBox lanes

- The cases that are gaps of all device lanes, as in the Moto G report (device plan section 5).
- The `biometric` segment: the AVDs have no screen lock, so no fingerprint can be enrolled (lead decision 3 of the
  device plan).
- On API 30: R3.1a and R3.1c (finding 2).

## Disposition

- **P2 device lanes:** with this report, the Moto G lane and both NucBox lanes have results. The NucBox runs of
  record ran at `2ce71df` and `c47d642` (API 36) and at `4864c4f` (API 30).
- **R-007:** the emulators add Android 16 and Android 11 to the JVM and Moto G reproductions of the four residuals.
  Finding 1 changes no residual and no verdict.
- **RTM:** no change.
- **Risk register:** no change. The residual dispositions come at P5.

## Follow-ups

- **Lead, at the P2 exit:** decide how to track finding 1, the self-closing lock screen of caller L (user decision
  of 2026-10-08). Approve or reject each proposed skip of device plan section 5.1; the NucBox lanes ran the
  screen-off steps of H06 and R3.3 (finding 9). Confirm the reading of the R3.4 objective in the Notes.
- **Harness:** a kill path for R3.1a and R3.1c on Android 11, before a later lane below API 35 (finding 2).
- **Harness:** read the five-field `am_kill` line of Android 11 in `r007_proc_events` (finding 10).
- **Harness:** decide whether a sweep trial without a kill fails the segment (6 such trials on API 36, 22 on the Moto
  G).
- **Fleet:** watch for the GL abort of finding 6 in later emulator lanes with `-gpu host`.

## Appendix A: evidence files

The `build/` folder is not committed. Each file is under `build/r007-evidence/`.

Recorded runs:

| File | Run | Lines | SHA-256 |
|---|---|---|---|
| `p2_healthy-emulator-5554-20261006T074805Z.log` | API 36 healthy | 1570 | `cb5352244b6dd4e0760cdb51e0149e15de3cf43c4a72f5c9b228082a7bb7dbeb` |
| `p2_cold_read-emulator-5554-20261006T092748Z.log` | API 36 cold-read | 11982 | `95185150800cb5da15e0ed42fbefd022616408c1ef3aad407a412fbfda624ae5` |
| `p2_degraded-emulator-5554-20261006T115811Z.log` | API 36 degraded (replaced) | 1486 | `8958d6b30df8765392bd85e6fc7daa58a615bdc37f3523034ea056b782611129` |
| `p2_degraded-emulator-5554-20261006T193645Z.log` | API 36 degraded rerun | 1562 | `f58e720bc3486314e8b6c209eb612e386120acfc3f78d26009d373d4d596b498` |
| `p2_death-emulator-5554-20261006T212909Z.log` | API 36 death | 3000 | `6044bc4068a757d964e931e456ed76b025dffab441ad5fd08fdafb009a8b0ec2` |
| `p2_reset-emulator-5554-20261007T003946Z.log` | API 36 reset | 2092 | `896eb5858e487df00dcc8a0f2f1e58eec7112af7f4ae0ba2b4668f97783836aa` |
| `p2_cross-emulator-5554-20261007T021815Z.log` | API 36 cross | 589 | `5307a87b37aedef0c2db3e90f6787b7865a55c47f91da6e4ae1416b1a3d473ca` |
| `p2_reboot-emulator-5554-20261007T025259Z.log` | API 36 reboot | 241 | `5297a1eeadf33631b4f78b600fdc9dd4d380ea52cc7ad6d79012e70830646748` |
| `p2_healthy-emulator-5554-20261007T213943Z.log` | API 30 healthy | 2025 | `fd1122cba8b12fad955da22644b2457763a2de2257bf226534e61b1cd26d911c` |
| `p2_cold_read-emulator-5554-20261007T230803Z.log` | API 30 cold-read | 13728 | `7f194742f020717af520f86e0fcc5992afc7c58c783117653ac673f30ad4734e` |
| `p2_degraded-emulator-5554-20261008T013939Z.log` | API 30 degraded | 1880 | `3a69389beb41334be084578b9132969cfd89d1a7a6346788f35278563d1564dd` |
| `p2_death-emulator-5554-20261008T033740Z.log` | API 30 death | 2097 | `0df39726fdecb7117517ecce722a771e8db933048535e7876a67c19ea8488b24` |
| `p2_reset-emulator-5554-20261008T053847Z.log` | API 30 reset | 2426 | `e0b289daa124c7435ce549af1728389e4cf69088fa1f05add5e99238f8cb5d52` |
| `p2_cross-emulator-5554-20261008T071653Z.log` | API 30 cross | 712 | `aff6935ec8b8cca51b429a130c27fa9e7e3e99018387cecc81d43981be4f4823` |
| `p2_reboot-emulator-5554-20261008T074834Z.log` | API 30 reboot (replaced) | 239 | `322c1fea545683429824cafbbe7559f554f556e31a49d768b32977c6d3557dd1` |
| `p2_reboot-emulator-5554-20261008T135021Z.log` | API 30 reboot rerun | 241 | `3fb1f4396530f12da4d254a6f533af4222ac5cf14cc884d26e2cef28fb721f43` |

P1 checks:

| File | Run | Lines | SHA-256 |
|---|---|---|---|
| `p1_validate-emulator-5554-20261005T221232Z.log` | API 36 at `a16ff11` | 78 | `6ed67e1e5abf9b7737037a756aca0afd1ec02ed8beed036785fa7428bd721632` |
| `p1_validate-emulator-5554-20261006T074327Z.log` | API 36 at `2ce71df` | 78 | `e8c47fecd3e6de262bc3c2256b115e115bc665db64e1c33504699ee88b08c43f` |
| `p1_validate-emulator-5554-20261006T193123Z.log` | API 36 at `c47d642` | 78 | `dc4318ab5c9ae3126e225db64b61dcd81de072c97b04f58b3b9e5817b1687a80` |
| `p1_validate-emulator-5554-20261006T014502Z.log` | API 30 at `a16ff11` (7 passed, 15 failed; finding 2) | 64 | `af9d178f0ad2a1820c08b370fe60687bb90c1e46cc4eb88ffb82d4d15cfe3655` |
| `p1_validate-emulator-5554-20261006T070312Z.log` | API 30 at `2ce71df` | 101 | `c336fccd2229fbbaff4e4238436d443387b1075f37292f5aeedff0d6e6a41f74` |
| `p1_validate-emulator-5554-20261007T032324Z.log` | API 30 at `c47d642` | 102 | `aa407dde621613232da09b2ecaa67cfc163641a51a19acada18b6f3b28010096` |
| `p1_validate-emulator-5554-20261007T212959Z.log` | API 30 at `4864c4f` | 94 | `b05564a36d7f438458c9d9200d8cb2ac194178f80845ccf348d5a97a4bfcfb6e` |
