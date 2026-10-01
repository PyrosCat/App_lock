# M7 WP2 F2 hardening, phase P2: device lane plan

**For:** the Moto G 2025 lane on the 2012 i7 box, and the NucBox G5 emulator lanes (API 36 and API 30).
**Status:** draft for the lead's review, written 2026-09-28. The harness of section 3 exists since 2026-09-29
(`scripts/r007/p2_device.sh`, `lib_p2.sh`, the segment files in `scripts/r007/p2/`, and `LockoutStoreFixture`).
**Baseline:** `05c69b7`. The production classes under test are unchanged since `b9e7c53` (change F2).
**SSOT:** `docs/process/M7_PLAN.md`, F2 hardening entry. The phase definition and the case IDs are in
`docs/process/proposals/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`. The JVM lane is
`docs/reports/campaigns/2026-09-28_m7-wp2-f2h-p2-jvm-baseline_2012-i7.md`.

Phase P2 characterizes the baseline lockout manager. The JVM lane reproduced all four R-007 residuals at manager
level. The device lanes add what the JVM cannot show: the two live callers and their UI, the real encrypted store,
real process death, death inside the platform commit, reboots, biometrics, and the cost of the construction read on
the main thread.

## 1. Lead decisions (2026-09-28)

1. **Depth.** Every device-only case runs on each host. Each critical kill cut runs 10 times per caller. Each
   residual gets 3 reboots where a reboot can change the result. X16 and the R3.1c mid-commit sweep are in scope.
   The latency distributions of the test plan move to the start of P3, as the baseline arm of the comparison, with
   the same build and hooks as the candidates. P2 records only the construction-read cost and the storage operation
   times from the wrapper.
2. **Callers.** The self-gate (`MainActivity`) and the legacy lock screen (`LockScreenActivity`) run on both hosts.
3. **Biometrics.** Biometric cases run on the Moto G only, with the lead present. The NucBox biometric cases are a
   recorded gap: fingerprint enrollment needs a screen lock, and the NucBox AVDs have none.
4. **Verifier entries (V).** The app does not change. V is exact from the wrapper write lines while no write is held.
   Otherwise V is inferred from the gate state in a UI dump before each submission. The evidence labels each count
   as exact or inferred.

## 2. Probe results that shape the harness

These probes ran on the Moto G on 2026-09-28, on the disposable P1 install (`com.applock`, prodDebug). They are
design inputs, not evidence of record. The device was returned to its earlier state after them.

| Probe | Result | Consequence for the harness |
|---|---|---|
| `am start` of `LockScreenActivity`, from the shell and through `run-as` | Both refused: the activity is not exported, and the `run-as` call fails the calling-package check | The legacy caller needs the detector (`AppDetectionService`) and a protected app. The harness drives it as a user does |
| An accessibility grant through `settings put secure` | The service was bound within 2 s. After a force-stop removed the grant, a new grant bound again within 2 s | The harness can grant the detector by adb on the Moto. Earlier Moto setups needed a manual grant after a clean install, so the harness preflight checks the binding and stops if it fails |
| `kill -9` of the app process with the grant on | The system started a new app process about 1 s later. That process ran the construction read and wrote nothing | Before an inspected kill, the harness removes the grant |
| `am force-stop` with the grant on | The grant was removed and `accessibility_enabled` was set to 0 | Each inspection (`am instrument` stops the app first) removes the grant. The harness grants it again after each inspection |
| Construction read at the detector bind | 176 ms and 287 ms on the main thread | Input for R1.4. The P1 Moto report measured 73 to 88 ms at a self-gate start |
| App settings | Biometric unlock is on by default. The Moto has 2 enrolled fingerprints | The legacy lock screen shows the biometric prompt at each start. The harness turns the app setting off for the PIN cases |
| `logcat` through `run-as` | No output | A kill cannot be triggered from the app's own log lines. The mid-commit kill uses the preferences backup file instead |
| Healthy write times in the P1 evidence (Moto) | 10 to 26 ms from the wrapper's `BEGIN` line to its `REAL_RESULT` line | The window of the mid-commit sweep |

More probes ran on the Moto G on 2026-09-29, with the new harness functions, and with the same limits:

| Probe | Result | Consequence for the harness |
|---|---|---|
| Removal of the grant while the lock screen shows | Unbound after 0.84 s. The process (same pid) and the lock screen stayed, and a wrong PIN after the removal wrote `(1,0)` | Confirms the order of section 3.3 |
| Mid-commit loop, delay 0 | 3 of 3 trials were inside the platform write: backup file present, the old pair after the inspection, and once an empty store file | The class "inside the platform write" is reachable with delay 0 |
| Mid-commit loop, delays 2, 5, and 10 ms | 3 of 3 trials were after the return. The `sleep` process of the loop takes a few ms | The classes between the file write and the return are rare with these delays. The report gives the counts |
| Clock launches with the detector on | Once, the lock screen started, got a second start request 0.16 s later, and closed. `LockScreenActivity` finishes on a pause without an unlock | `r007_open_gate` launches again, up to 3 times, and records each retry |
| Switch of the Clock row in the app list | The tap protected Clock, but the UI dump still showed `checked="false"` | The harness checks the protection by behaviour only |
| Sleep key (`KEYCODE_SLEEP`) | The keyguard showed 0.13 s after the screen went off (the power key locks at once) | A screen-off step needs the operator on the Moto. It runs only with `-o`; otherwise the case records `screen_cycle=skipped` |
| UI dump (`uiautomator dump`) | About 2 to 5 s for each dump | Gate samples are seconds apart. H03 bounds each sample by the device time before and after its dump |
| Empty string in a secure setting | `settings put` with `''` stores an empty string, and `settings get` prints an empty line | The settings record accepts an empty service list and restores it as an empty string |

Five more probes ran on the Moto G on 2026-09-30, with the same limits:

| Probe | Result | Consequence for the harness |
|---|---|---|
| Timed `input tap` for each digit of a wrong PIN and a correct PIN on the self-gate | The first three digits took 68 to 106 ms each, and the last digit 630 ms (wrong PIN) and 577 ms (correct PIN). The `WRITE BEGIN` line came 26 ms and 19 ms before the last tap returned. The PIN check runs in the click handler on the main thread | A kill after the last tap comes after the write begins. R3.1a starts the last tap in the background and kills during it. The JVM cut of R3.1a (after the check, before the admission) is not reachable by a host kill (section 5) |
| Clock launch with the detector bound | The lock engine logged `AppLockEngine: com.google.android.deskclock -> LockDecision(requiresAuthentication=true, reason=protected app, no session)` at debug level, three times for one launch | `r007_protect_clock` uses these lines as evidence of the protection when the lock screen does not show |
| Kill of the self-gate process, with Clock protected | About 1.04 s after each of 3 kills, the system started a new app process for the sticky `ProtectionWatchdogService`. The new process logged no wrapper line, so it did not build the lockout store | Before an inspection, the harness stops a running app process that logged no wrapper line, and records the stop. A running process with a wrapper line still fails the inspection |
| State and CPU ticks of the main thread (`/proc/<pid>/task/<pid>/stat`, read by the shell about every 55 ms) during the third and the last digit of a wrong PIN | The third digit used 3 ticks in all, and the thread slept from 77 ms after the tap start. During the last digit the thread ran from 73 ms to 640 ms and used 61 ticks. The `WRITE BEGIN` line came at 643 ms | The PIN check hashes on the main thread, so its CPU ticks show that the check has started. R3.1a counts a trial only with at least 8 ticks between the tap start and the kill |
| `input tap` in the background, with the app killed 382 ms after the tap start | The tap returned 54 ms after the kill ended, with exit status 0. The process logged no `WRITE` line | The kill command waits for the tap and records its exit status. A trial with a failed tap does not count |

## 3. Harness additions

All additions are host scripts in `scripts/r007/` and androidTest code with `@R007DeviceTool`. The app does not
change, so the release dex scan of the 2026-09-25 Moto G report stays valid. The work is one reviewable commit with
its host tests, before any recorded run.

### 3.1 Fixture writer

A new androidTest tool, `LockoutStoreFixture`, writes one stored pair through `EncryptedPrefsLockoutStorage` in a
new process. Its arguments are the count and the deadline, either absolute or as an offset from the device wall
clock. It reports the result of `commit()`. The host then checks the pair with the inspector.

- The tool runs only when no app process runs (`am instrument` stops the app first), so it never competes with the
  manager's writer.
- It gives exact fixtures (`Z`, `C4`, `L5`, `L8`, long windows for reboots, expired deadlines) in seconds, instead
  of wrong-PIN ladders that take minutes. The test plan asks for fixtures made by a confirmed write and checked in
  a fresh process.

### 3.2 Legacy caller driver

- **Why accessibility.** In the baseline app, only the accessibility detector starts `LockScreenActivity`, and the
  activity is not exported. The harness grants the detector only as a device setting of a run, for the legacy-caller
  cases. The app does not change. The overlay caller stays inert until F6, so it cannot be the second caller at
  baseline. When the overlay engine replaces the detector, these results describe a caller that is removed. The
  results of the self-gate stay valid for the lockout manager.
- **Grant.** The harness grants `AppDetectionService` (`settings delete`, then `put`, then `accessibility_enabled
  1`) and waits until `dumpsys accessibility` lists it as bound. The original values go into the device settings
  record and are restored at the end.
- **Protected app.** The harness protects the Clock app once, through the app list, as `scripts/e2e/setup_device.sh`
  does. It checks the protection by behaviour: opening Clock shows the lock screen, or the lock engine logs a lock
  decision for Clock. A tap on the switch of a protected row removes the protection, so the harness taps only after
  three launches in a row with no lock screen and only "not protected" decisions in the log. The Clock package is
  `com.google.android.deskclock` on the Moto and `com.android.deskclock` on the AOSP images.
- **Open the gate.** HOME, launch Clock, then wait until the top activity is `LockScreenActivity` and the UI shows
  "Enter your PIN".
- **Relock.** The relock policy is immediate. HOME and a new launch of Clock show the lock screen again.

### 3.3 Kills with the detector on

With the grant on, the system starts a new process about 1 s after a kill. That process loads the store before an
inspection could run. A load can change the store file: it restores the backup file that a death inside the commit
leaves.

- **Inspected kills.** When the gate shows, the harness writes the original value back to
  `enabled_accessibility_services` and waits until the service is not bound. The process stays, because its
  activity is in the foreground. The `onDestroy` method of the service only removes a screen-off receiver, so the
  lockout path does not change. The kill then leaves no process. The inspection runs first, and the new grant
  starts the new process. This is the order that the restart procedure of the test plan requires.
- **Restart-cycle runs** (R1.2e, R2.4, R4.4). The grant stays on, and the restart by the system is the relaunch, as
  it is for a user with the detector on. These runs inspect only at the end.
- **Fault script.** The harness publishes the script for the new process before the kill.

### 3.4 Verifier entries and gate state

- **Gate state:** a UI dump shows "Try again in" (blocked), "Enter your PIN", or "Incorrect PIN — try again" (open).
  Both callers poll the lockout state every 250 ms.
- **Exact V:** the number of `WRITE ... phase=BEGIN` lines of one process. It is exact when no write is held and no
  write waits in the queue at a kill. Each verified PIN admits exactly one mutation, and each mutation is one write.
- **Inferred V:** the number of submissions made while a UI dump just before the submission showed an open gate.
  The harness uses it when a write is held (R3.3, H06, X09).

### 3.5 Mid-commit kill (R3.1c)

The kill targets the platform commit, not a wrapper hold. The fault script is empty, so the wrapper only logs.

- Android's preferences code renames the store file to `applock_lockout.xml.bak` before it writes the new file. It
  deletes the backup file after the new file is written and synced. A load that finds a backup file restores it.
- Before the PIN entry, the harness starts a loop in the app uid (`run-as`) that waits for the backup file, waits
  an optional delay (0, 2, 5, or 10 ms), and then sends `kill -9` to the app process. The loop stops by itself after
  30 s.
- For the legacy caller, the harness removes the grant before the kill. So no new process loads the store before
  the harness looks at the files.
- The trial is classified from the wrapper lines, the backup file, and the inspected pair:

  | Class | Wrapper lines of the dead process | Backup file after the death | Inspected pair |
  |---|---|---|---|
  | Before the write | no `BEGIN` | no | old |
  | In the adapter, before the file write | `BEGIN`, no `REAL_RESULT` | no | old |
  | Inside the platform file write | `BEGIN`, no `REAL_RESULT` | yes | old (the load restores the backup) |
  | After the file write, before the return | `BEGIN`, no `REAL_RESULT` | no | new |
  | After the return | `REAL_RESULT` | no | new |

- The inspection of a trial with a backup file changes the store file, because the load restores the backup. The
  harness records the hashes of both files before the inspection and the store hash after it, and does not treat
  this change as a failure.
- Expected at baseline: each trial gives the complete old pair or the complete new pair, never a read error.
- Count: at least 60 trials per caller per host, and at least 10 trials in the class "inside the platform write". The
  harness stops at 150 trials and reports the counts.
- The harness commit confirms the backup-file behaviour on the Moto before the recorded run.

### 3.6 Reboots

- The harness runs `adb reboot`, waits for `sys.boot_completed`, and checks that the boot id changed.
- **Moto:** the first unlock after a boot needs the device credential. The harness prints a request and waits up
  to 5 min for the lead to unlock the phone. It never enters a device credential.
- **NucBox:** the AVD has no screen lock. The harness wakes the screen and checks the keyguard.
- The harness removes the grant before the reboot. After the boot, the boot receiver can start the app. The
  harness checks that the wrapper logged no `WRITE` line after the boot, stops the app, inspects, and then grants
  the detector again.
- The device settings and the settings record survive the reboot. The fault script in `files/r007/` also survives,
  so the harness publishes the post-boot script before the reboot.
- Lockout windows: a 30 s window can end during a reboot. Reboot fixtures use windows of at least 480 s, and the report
  compares the stored wall deadline with the times.

### 3.7 Damaged store file (X16)

The harness saves the store, writes XML that does not parse over it, and then inspects. The inspection can change
the file: in probe A of the 2026-09-25 Moto G report, the read wrote new keysets and no values. So the harness
records the change instead of failing.
It then opens the gate, enters a wrong PIN, kills, inspects, and restores the saved store. The P1 functions
`r007_store_save` and `r007_store_restore` are reused.

### 3.8 Device and app settings

- The device settings record gets two more entries: `secure:enabled_accessibility_services` (a component name or
  `null`) and `secure:accessibility_enabled`. The record format accepts these string values.
- The harness sets the app setting `biometric_unlock` to false for the PIN cases, by writing
  `shared_prefs/applock_settings.xml` through `run-as` while no app process runs. It records the original file (or
  its absence) and restores it at the end. The biometric segment sets it to true.
- Each change of app data that a run must undo (the saved app settings file, a saved store copy, a read-only
  preferences directory) has a marker file in `files/r007/pending/`. A run does not start while a marker of an earlier
  run exists. `scripts/r007/restore_settings.sh` undoes the changes that the markers name, and then restores the
  device settings.
- The Moto locks about 4.3 s after a screen timeout, but at once after the sleep key. So a screen-off step (H06,
  R3.3) runs only when an operator is present (`-o`), who unlocks the phone after it. Without `-o`, the case records
  `screen_cycle=skipped`. The NucBox AVDs have no screen lock, so their runs use `-o`.

### 3.9 Segments and evidence

The run is split into segments. Each segment is one command, with its own evidence file, preconditions, settings
record, and exit handler, as in P1. A failed segment does not stop the others. The command is
`scripts/r007/p2_device.sh [-s SERIAL] [-r MAX_REPEATS] [-c CALLERS] [-o] SEGMENT`. The option `-r` caps every repeat
count (the smoke run uses `-r 1`), `-c` selects the callers (`"S L"` by default; one or both, each once), and `-o`
says that an operator is present.

| Segment | Cases | Operator present |
|---|---|---|
| `healthy` | H01 to H06, construction-read cost (R1.4d) | no |
| `cold-read` | R1.1 to R1.4 | no |
| `degraded` | R2.1 to R2.4 | no |
| `death` | R3.1 to R3.4, including the sweep | no |
| `reset` | R4.1 to R4.4 | no |
| `cross` | X09, X10, X16 | no |
| `reboot` | the reboot variants of H05, R2.1, R3.1, and R4.1 | Moto: yes (unlock after each reboot) |
| `biometric` | X11 and the biometric part of H04 (Moto only) | yes |

- Each case and each repeat writes a marker line to the evidence file. It records the case, the caller, the
  repeat, the fixture, the fault script, the pids, the boot id, the stored pair before and after, V with its label,
  and the two verdicts ("as predicted" and "objective met"), as in the JVM report.
- In a residual case, a repeat as predicted shows the loss, so its objective is "no". A repeat that is not as
  predicted does not establish an unmet objective, so its objective is "na", and the run counts it as a failure.
  Controls that cannot lose a lock (R1.2 b to d over `Z`, the X10 kill from `Z`) also have the objective "na".
- A summary table at the end of each evidence file lists each case with its repeat count and verdicts. The objective
  column counts only the repeats with a "yes" or "no" objective and gives the number of "na" repeats apart.

### 3.10 Host tests

`scripts/r007/test_lib_r007.sh` gets tests for the new functions that need no device: V counting from log text,
the gate-state parse, the sweep classification, the settings record with string values, and the argument checks of
the fixture writer.

## 4. Case matrix

Callers: S = self-gate, L = legacy lock screen. Repeats are per caller per host unless the row says otherwise.
Fixtures are made with the fixture writer. "Kill" means the P1 `r007_kill` (SIGKILL). For L, the grant is removed
before an inspected kill (section 3.3). "Exact" and "inferred" are the V labels of section 3.4.

### 4.1 Healthy controls (H)

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| H01 | `Z`. Open the gate and leave it for 60 s. | S, L | 3 | reads and writes (expected: 1 construction read, no write) |
| H02 | `Z`. Four wrong PINs, each until its write returns. A fifth, then two more attempts. Kill, inspect. | S, L | 3 | stored count after each write, gate state, exact V (5), no write for the blocked attempts, stored pair |
| H03 | `L5`: gate state around the deadline, then a sixth wrong PIN (60 s window). Fixture `(11, expired)` and a wrong PIN: the window is the 30 min cap. | S, L | 3 | countdown values, no write during a countdown, window lengths |
| H04 | `C4`. A correct PIN with the write held 5 s: the gate opens before the write returns. Kill after the return, inspect. | S, L | 3 | reset before the commit, stored `Z` |
| H05 | Fixtures `(1,0)`, `(4,0)`, `(8, now + 10 min)`, `(8, expired)`. Kill and relaunch. Reboot with the active and the expired fixture. | S, L | 3 kills per fixture; reboots: 1 active, 1 expired per host | reloaded state, remaining time against the wall deadline, expired lockout not revived, boot id |
| H06 | `Z`. The first write held for 1 s, 5 s, and 40 s. During the hold: HOME and back, a second wrong PIN, a UI dump every second, screen off and on. Also a threshold failure and a reset under a hold. | S, L | 3 | responsive gate (no ANR dialog in the UI dumps that show the gate; at least one such dump), queue order, last admitted pair stored |

### 4.2 R-007/1: cold-start read failure (R1)

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| R1.1 | `L5`. (a) No poll: `READ 0 Throw`; the detector builds the manager with no lock screen; Clock opens after 10 s. (b) With polls: `READ * Throw`; the host publishes an empty script 1 s after the gate opens. | (a) L; (b) S, L | 3 | reads and their times, unenforced interval, count after the re-seed |
| R1.2 | `READ * Throw` over `L8` and `Z`. (a) Read rate over 10 s. (b) A wrong PIN, kill, inspect: `(1,0)`. (c) A correct PIN: `Z`. (d) The fault ends after 10 s. (e) First process and 20 restart cycles, wrong PINs until the gate blocks, with a control run without the fault. Over `Z`, (b) to (d) are controls. | S, L | (a) to (d): 3; (e): 1 run of 21 processes | reads per second, exact V per process, stored pairs |
| R1.3 | Count 5 with a 10 min window (so that an old lock that comes back still blocks at the check), `READ 0 Throw`, `READ 1 ReadThenHold`. While the old value is held: (a) a wrong PIN, (b) a correct PIN. Release the read. Also (a) with `WRITE 0 ReturnFalseBeforeCommit`. Check the live gate: open for (a) and (b), the 30 s degraded lock for the failed write. Kill, inspect. | S, L | 3 | old value not applied (live gate), stored pair (`(1,0)` or `Z`; the old pair when the write fails) |
| R1.4 | (a) `READ 0 HoldThenRead` at a self-gate cold start, released at 40 s. (b) The same at the detector bind. (c) `READ 0 Throw`, `READ 1 HoldThenRead`: five wrong PINs while the re-seed read holds, released at 40 s. (d) Construction-read cost: 30 cold starts per caller path. | (a) S; (b) L; (c) S, L; (d) S, L | (a) to (c): 3; (d): 30 | ANR dialog and its time (unknown when no UI dump returned a screen), a process death during the hold and its time, main-thread read time, writes waiting behind the read; for (d) the first start after a boot is reported apart |

### 4.3 R-007/2: degraded enforcement lost on restart (R2)

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| R2.1 | `Z`, `WRITE 0 ReturnFalseBeforeCommit` (and `Throw`). A wrong PIN arms the 30 s degraded lock. Kill 5 s after the write returned; relaunch. Also a kill at 25 s, and the real platform fault (read-only preferences directory). Reboot: `(8, expired)`, a wrong PIN with a failing write arms a 480 s degraded lock, then reboot. The kill time is the device time after the confirmed death, so the lost time is a lower bound. A kill after the end of the lock shows no loss and fails the repeat. | S, L | kill at 5 s: 10; kill at 25 s and platform fault: 3; reboots: 3 per host | lost enforcement time, gate state after the restart, stored pair |
| R2.2 | `Z`, `WRITE 0 HoldBeforeCommit ReturnFalseBeforeCommit`. F1 held, F2 queued. Release: F1 fails, F2 commits `(2,0)`. Kill 10 s later. | S, L | 3 | degraded state before the kill, count 2 and Available after it |
| R2.3 | `(3,0)`, `WRITE 0 HoldBeforeCommit ReturnFalseBeforeCommit`. F4 held, F5 locks. Wait 40 s, release, kill. | S, L | 3 | degraded window from the F4 completion, stored `L5` with a passed deadline, Available after the kill |
| R2.4 | `WRITE * ReturnFalseBeforeCommit`, then `WRITE * Throw`. First process and 20 restart cycles, wrong PINs until the gate blocks. Also kills before and after the degraded deadline, timed as in R2.1. | S, L | 1 run of 21 processes per script; deadline kills: 3 | exact V per process, stored `Z`, count never above 1 |

### 4.4 R-007/3: death before admitted changes are durable (R3)

The critical cuts use the fixtures `Z` and `C4` in turn (5 repeats each).

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| R3.1a | A kill during the PIN check, before the write begins. A timed control submission per caller measures the window from the time a tap reaches the app to the write. The host starts the last tap in the background and kills at the middle of the window. The kill command reads the CPU ticks of the main thread before the tap and at the kill. A trial counts only when the tap succeeded, the main thread used at least 8 ticks in that time (the PIN check hashes on the main thread, so the check has started), and the wrapper logged no `WRITE` line; the harness stops after 30 trials. A kill that reports an error stops R3.1a for that caller: the process then died by another cause, or not at all. The check gave no result before the kill, so the objective verdict is "na". | S, L | 10 | control window, kill time, tap status, main-thread ticks, stored pair (old), gate after the restart |
| R3.1b | `WRITE 0 HoldBeforeCommit`: the first wrong PIN holds, the second waits in the queue. Kill. | S, L | 10 | stored pair (old), both failures lost; from `C4` the threshold lock is lost |
| R3.1 held | `WRITE 0 HoldBeforeCommit`: one wrong PIN, kill. Reboot variant. | S, L | 10; reboots: 3 per host | stored pair (old), gate after the restart |
| R3.1c | The mid-commit sweep (section 3.5), with no fault script. | S, L | at least 60 trials, 10 inside the platform write | class of each trial, stored pair (complete old or new), backup file |
| R3.1d | `WRITE 0 CommitThenHold`: one wrong PIN, kill. | S, L | 10 | stored pair (new), no loss |
| R3.2 | `(2,0)`, `WRITE 0` to `WRITE 3 HoldBeforeCommit`: F3, F4, a correct PIN, then F1 of the new streak. Release k writes (k = 0 to 4), kill. | S, L | 1 per k | stored prefix for each k: `(2,0)`, `(3,0)`, `(4,0)`, `Z`, `(1,0)` |
| R3.3 | `Z`, `WRITE 0 HoldBeforeCommit`, released at 40 s or never (kill). Wrong PINs at the UI rate until the gate blocks, and again after the threshold window ends. HOME and back, screen off and on. | S, L | 3 per ending | inferred V, threshold window ending during the stall, stored pair |
| R3.4 | `WRITE 0 CommitThenReportFalse` from `Z` and from `C4`, and for a reset. Kill after the degraded outcome, inspect. | S, L | 3 | new stored pair despite the false result, degraded gate in the process, recorded lock enforced after the restart (`C4`) |

In R3.2, F1 comes from the same caller when its gate shows again after the unlock. Otherwise F1 comes from the
other caller, and the evidence records it.

### 4.5 R-007/4: failed reset brings stale enforcement back (R4)

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| R4.1a | `(4,0)`. A correct PIN with `WRITE 0 ReturnFalseBeforeCommit`, `Throw`, or `HoldBeforeCommit` (never released). Kill 5 s later, inspect, relaunch: the first wrong PIN locks for 30 s. Reboot variant. | S, L | 10 (scripts in turn); reboots: 3 per host | stale pair after the kill, time until the lock |
| R4.1b | Active stale deadline through unknown storage: `(8, now + 10 min)`, `READ * Throw`, `WRITE 0 ReturnFalseBeforeCommit`. The gate is open, a correct PIN clears memory, and the clear fails. The host publishes an empty script, kills, and relaunches. | S, L | 3 | old lock enforced after an accepted success, time until a PIN is possible |
| R4.1c | `(4,0)`. The clear fails on a read-only preferences directory (real platform fault). | S, L | 3 | `commit()` false, stale pair |
| R4.2 | `(4,0)`. A failed clear, then 60 s of observation. | S, L | 3 | exactly 1 write in the process (no retry), stale pair |
| R4.3 | `(4,0)`. A failed clear, then a wrong PIN: `(1,0)`. Also the clear held and failing, with the wrong PIN queued behind it. | S, L | 3 | stored pair after each write, write order |
| R4.4 | `L5`, `WRITE * ReturnFalseBeforeCommit`, then `WRITE * Throw`. The first process waits for the lock and enters the correct PIN. 20 restart cycles, each with a correct PIN. A last restart and a wrong PIN. | S, L | 1 run of 21 processes per script | stale count on each restart, waits, the last window (60 s, degraded) |

### 4.6 Cross-cutting cases (X)

| Case | Device procedure at baseline | Callers | Repeats | Records |
|---|---|---|---|---|
| X09 | `Z`, `WRITE 0 HoldBeforeCommit`. (a) Two taps on the last digit; the second tap starts a new entry (section 5), so the objective of (a) is "na". (b) HOME and back during the hold. (c) A rotation during the hold (`user_rotation` 1, then 0). Release. | S, L | 3 | writes per completed PIN entry, count, no second unlock |
| X10 | Both callers in one process. (a) A wrong PIN on L with the write held, then a correct PIN on S. Release. (b) The reverse order. (c) Both orders with a kill before the release. | S + L | 3 per order | write order, stored pair, which target unlocked |
| X11 | Moto, L, biometric setting on, lead present. (a) A non-enrolled finger from `C4`. (b) Cancel with "Use PIN". (c) System biometric lockout after repeated mismatches. (d) An enrolled finger from `C4`. (e) An enrolled finger over an unreadable stored lockout (`(8, now + 10 min)`, `READ * Throw`). | L | (a), (d): 3; (b), (c), (e): 1 | writes, stored pair, prompt shown or not |
| X16 | `(8, now + 10 min)`. The damaged store file (section 3.7). | S | 3 | inspected result, file change, gate state, stored pair after a wrong PIN |

## 5. Cases not run on the device lanes

| Case | Reason |
|---|---|
| R1.4 cancellation, X08 | The device wrapper throws only `InjectedStorageFault`. The JVM lane covers the cancellation cases. |
| R3.1a cut of the JVM lane (a death after the PIN check and before the admission of the failure) | On the device, the check and the admission run one after the other in one click handler on the main thread, with no wait between them (probe of 2026-09-30). A host kill has a delay of tens of milliseconds, and the app logs nothing between the two steps. The device R3.1a kills during the check instead. The JVM lane covers the cut. |
| R3.1e, X13 | No device hook holds or observes the completion callback. The JVM lane covers them. |
| X09, a double submit of one PIN entry | `input tap` returns only after the app has handled the tap, and the click handler checks and clears the PIN on the main thread. A second tap therefore starts a new entry, and a host cannot submit one entry twice. X09a records the tap times and one write for each completed entry. |
| R3.2 threshold write before a reset | The gate blocks a reset while a threshold lock is active, on both callers. The case is manager-only (JVM). |
| R4.1 reset over an active lockout (direct) | Manager-only for the same reason. R4.1b reaches an active stale deadline through unknown storage instead. |
| X04 | Setting the wall clock needs root on the Moto. The JVM lane covers it. |
| X05 burst, X06, X07 | Manager-level or runtime-level (the runtime is inert until F6). The JVM lane covers them. R1.2a gives the device read rate at the UI poll. |
| X14 | Manager logic, the same on both lanes. The JVM lane covers it. |
| X01, X02, X03 (B3 part), X12, X15 | Candidate cases. The baseline has no mechanism for them. |
| Audit and capture effects (I5) | The audit database is encrypted, so the device lanes cannot read its rows. Accounting on the device is limited to the wrapper writes. The JVM lane covers the callbacks. |
| NucBox biometrics (H04 biometric part, X11) | No screen lock on the AVDs (lead decision 3). |
| Latency distributions of the test plan | Moved to the start of P3 (lead decision 1). |

## 6. Hosts

### 6.1 Moto G 2025 (API 35, arm64)

- The run uses the disposable P1 install (`com.applock`, PIN 1234). The prodDebug APK and the androidTest APK of the
  tested revision are installed with `install -r`, which keeps the data.
- The lead is present for the `reboot` and `biometric` segments. The other segments run unattended once the phone
  is unlocked and V0 has set stay-awake.
- The Moto lane runs first. It also validates the new harness on a device.

### 6.2 NucBox G5 (API 36 and API 30, x86_64)

- The NucBox gets its own operator plan, `docs/testing/M7_WP2_F2H_P2_NUCBOX_PLAN.md`, after the Moto lane. It
  follows the P1 NucBox plan: headless `-gpu host`, the keyguard disabled, a read-only preflight, and tested snippets.
- The API 36 lane uses `matrix_api36`. The API 30 lane needs an AOSP API 30 AVD with the same profile. Its preflight
  also checks the rotation read and the accessibility binding, because the harness was validated on API 35 and 36 only.
- The NucBox operator plan also takes the follow-ups of the P1 NucBox report: check the AVD for an earlier
  `com.applock` install before the install, and add the UI-dump check to the preflight.
- `TAP_GAP` may need 1.3 s on API 30. The restart-cycle runs are then longer.

## 7. Order of work

1. Harness commit: the harness additions with their host tests, the local gate, and review.
2. Moto smoke run: each segment once with one repeat. It confirms the backup-file trigger, the grant cycle, that
   the lock screen and its process stay when the grant is removed, and the reboot wait. Harness fixes are separate
   commits.
3. Moto recorded run of all segments at one clean revision, then the Moto report.
4. NucBox operator plan, then the NucBox runs (API 36, then API 30), then the NucBox report.
5. P2 exit record in M7_PLAN.md. It also needs a clean JVM run at `05c69b7` or later, because the P2 JVM tests
   changed after the JVM report.

## 8. Reports

- One report per host: `<filing date>_m7-wp2-f2h-p2-device_moto-g-2025.md` and
  `<filing date>_m7-wp2-f2h-p2-device_nucbox-g5.md` (both API levels in one report).
- Each report uses the verdict columns of the JVM report ("as predicted" and "objective met"), labels each V as
  exact or inferred, lists the repeat counts, and lists each case that the device lanes do not run as a gap with
  its reason.
- Drafts stay uncommitted until they are final. RTM: no change. Risk register: no change before P5.

## 9. Risks and limits

- **Detector grant.** The legacy caller depends on an adb grant of the accessibility service. If a device refuses
  the grant, the legacy cases on that device stop and become a recorded gap.
- **Restarted process.** In the restart-cycle runs, the process that the system restarts loads the store before
  the harness acts. At baseline it only reads, and these runs inspect only at the end. Inspected kills remove the
  grant first. The process that the watchdog service starts after a kill does not use the store, and the harness
  stops it before an inspection.
- **Kill timing.** A host kill has a delay of tens of milliseconds. R3.1a therefore kills in the middle of a window
  of about 500 ms on the Moto G, and a trial counts only by the main-thread CPU ticks at the kill and the wrapper
  lines. The window of the
  NucBox AVDs can be narrower, and a window under 200 ms stops R3.1a on that host. The mid-commit class depends on
  the backup-file behaviour, which the smoke run confirms first.
- **Reboots on the Moto** need the lead to unlock the phone 11 times: 2 for H05, and 3 each for R2.1, R3.1,
  and R4.1a.
- **Fault source.** Wrapper faults are injected. Only R2.1, R4.1c, and X16 use real platform file faults.
- **Clocks.** Device times come from the wrapper timestamps (`wall` and `elapsed`). Host times are used only to
  order commands.
