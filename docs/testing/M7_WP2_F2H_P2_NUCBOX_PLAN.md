# M7 WP2 F2 hardening, phase P2: NucBox emulator lane plan

**For:** the NucBox G5 (the fleet's x86_64 emulator host).
**Status:** ready, reviewed by the lead on 2026-10-05, for the harness at commit `8d1c28b`.
**Companion:** the Moto G lane is complete (`2026-10-04_m7-wp2-f2h-p2-device_moto-g-2025.md`).
**Device plan:** `M7_WP2_F2H_P2_DEVICE_PLAN.md` holds the case matrix, the segments, the predictions, and the lead
decisions. This plan holds only the NucBox procedure.

Phase P2 characterizes the baseline lockout manager before any fix is chosen. The JVM lane and the Moto G lane are
complete. This plan covers the two NucBox lanes: the AOSP emulators at API 36 and API 30. Each lane gets a P1 check,
read-only probes, a smoke run, and a recorded run. One report covers both lanes.

## What the NucBox lanes add

- An x86_64 emulator with AOSP images and a virtual disk, at two API levels. The API 30 lane is the first run of the
  P2 harness below API 35.
- The screen-off steps of H06 and R3.3. The AVDs have no screen lock, so the runs use `-o`, and these steps run. On
  the Moto G they were skipped, because each screen-off locks the phone.
- Not on the NucBox: the `biometric` segment. The AVDs have no screen lock, so no fingerprint can be enrolled (lead
  decision 3 of the device plan).

## 1. Operator rules

The runs need no person at the emulator. These rules bind the operator, also in auto mode:

1. **No commits.** The operator does not stage, commit, or push. At the end, it hands over the report draft and the
   change of the fleet table (section 10). The user commits them.
2. **Only small harness fixes.** The operator changes no file under `app/` or `docs/`, and no plan. It may set an
   environment override or prepare a small harness fix under section 9. The report draft stays in
   `build/r007-report-draft/` until the runs end. Then the report and the fleet table row go to `docs/` (section 10).
3. **A running segment goes on.** A failed case repeat does not stop a segment: the segment goes on with the next
   repeat. Stop a segment only when the emulator or the host is broken: the emulator process ended, adb has not seen
   the emulator for 5 min, or neither the evidence file nor the console file has grown for 30 min. Before a stop,
   read the evidence file again and count from it. Do not act on an earlier count.
4. **Decisions stay with the lead.** The operator does not decide a skip, a change of a prediction, or a rerun that
   section 9 does not allow. Sections 7 and 9 list the only changes that the operator makes without the lead.
5. **Device and host limits.** The operator does not wipe AVD data, does not uninstall the app between an action and
   its inspection, never enters a credential, downloads nothing, and does not change Windows settings.
6. **Stop and report.** When this plan says "stop", the operator lets the running segment finish or stops it under
   rule 3, keeps every file, runs `bash scripts/r007/restore_settings.sh -s emulator-5554 </dev/null` if a run did
   not finish its cleanup, and reports the state to the user. It does not start the next step.
7. **Long commands.** Run each segment as a background command, with its console output in a file (section 7). Check
   the progress by the size and the last lines of the evidence file and the console file. On Windows, a stopped
   background task can leave its `bash.exe` children running: check for them and end them. Write scratch files under
   `build/` or outside the repository, so that the changed-file list of each run shows only real changes.

The user's part: the power setting (section 2), the review of this plan, the decision after any stop, the commit of
each harness fix, and the commit of the report.

## 2. Prerequisites

1. **Revision.** Run the session-start git sync. Use `origin/main` at or after the commit of this plan, with a clean
   working tree. Check that the harness and the app are those of `8d1c28b`:

   ```
   git diff --stat 8d1c28b HEAD -- scripts/r007 scripts/e2e app
   ```

   The output must be empty, or list only the files of committed fixes under section 9.3. Otherwise stop: the user
   names the revision to test.
2. **Host tools.** Use Git Bash, with the SDK `platform-tools` and `emulator` folders at the front of `PATH`. Run
   every command of this plan from the repository root. The commands that run `adb shell` read from `</dev/null`:
   `adb shell` reads its standard input and would take the next lines of a pasted block.
3. **Power.** Before the first run, the user sets the Windows power plan so that the NucBox does not sleep or
   hibernate while it is on AC power. The lanes take about two days.
4. **Host tests.**

   ```
   bash scripts/r007/test_lib_r007.sh
   ```

   Expected at `8d1c28b`: `481 tests, 0 failed`. Record the result. Any other result: stop.

## 3. Build

Build once. Both AVDs use the same APKs. First check that the harness, the app, and the build files have no
uncommitted change. Other uncommitted files do not matter for the build. The task `:app:clean` deletes the whole
`app/build/` folder, which can hold the evidence of the JVM lane (`app/build/r007-evidence/`), so copy that folder
to the root `build/` folder first. Git Bash runs the Gradle wrapper only with its path (`./gradlew.bat`).

```
git status --porcelain -- scripts/r007 scripts/e2e app gradle build.gradle.kts settings.gradle.kts gradle.properties
if [ -d app/build/r007-evidence ]; then
  cp -a app/build/r007-evidence "build/jvm-evidence-$(date -u +%Y%m%dT%H%M%SZ)"; fi
./gradlew.bat :app:clean assembleProdDebug assembleProdDebugAndroidTest --no-build-cache
sha256sum app/build/outputs/apk/prod/debug/app-prod-debug.apk \
  app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
```

The `git status` command must print nothing. Record both hashes. They differ from the Moto G hashes, because each
host signs debug builds with its own key. Each evidence header records the hashes of the installed APKs, and they
must equal these hashes. Install these files and do not build again during the lanes: a new build of the same
revision can give other hashes (Notes and risks). Do not use `connectedProdDebugAndroidTest`: it uninstalls the app
and the test APK after the run.

## 4. Order of work

| Step | API 36 (`matrix_api36`) | API 30 (`matrix_api30def`) |
|---|---|---|
| 1 | Start, install, PIN and P1 check, probes (sections 5 and 6) | |
| 2 | Smoke run (section 7) | |
| 3 | | Start, install, PIN and P1 check, probes |
| 4 | | Smoke run |
| 5 | Start, recorded run (section 8) | |
| 6 | | Start, recorded run |
| 7 | Report for both lanes (section 10) | |

Both smoke runs come before the recorded runs, so a harness fix for API 30 does not split the recorded runs over two
revisions. Run one emulator at a time. To change the AVD, stop the running emulator with
`adb -s emulator-5554 emu kill </dev/null` and wait until `adb devices` no longer lists it.

## 5. Start, install, and PIN

1. **Start the AVD.** Both AVDs use an AOSP `default` image for x86_64, with no screen-lock credential:

   | API level | AVD | System image |
   |---|---|---|
   | 36 | `matrix_api36` | `system-images;android-36;default;x86_64` |
   | 30 | `matrix_api30def` | `system-images;android-30;default;x86_64` |

   Boot headless, with the hardware GPU:

   ```
   emulator -avd AVD -no-snapshot -no-audio -no-boot-anim -no-window -memory 3072 -gpu host &
   adb -s emulator-5554 wait-for-device
   until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed </dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
   ```

   On API 36 the software GPU did not render the Compose surfaces (2026-09-16 NucBox report). A windowed `-gpu host`
   boot coincided with a host BSOD once, so boot headless only. If the AVD is missing, stop. If API 30 does not boot
   in 10 min with `-gpu host`, or its UI dump in section 6 is empty, use `-gpu swiftshader_indirect` for the API 30
   lane and record it.
2. **Record the AVD.** Record the output of `emulator -version`, the lines `hw.device.name`, `hw.ramSize`,
   `hw.lcd.width`, `hw.lcd.height`, and `hw.lcd.density` of `~/.android/avd/AVD.avd/config.ini`, and these
   properties:

   ```
   adb -s emulator-5554 shell getprop ro.build.version.sdk </dev/null
   adb -s emulator-5554 shell getprop ro.build.fingerprint </dev/null
   ```

3. **Install.** The user data of an AVD stays after a cold boot with `-no-snapshot`, so an earlier install can hold an
   unknown PIN and store state. Uninstall both packages first, check that neither is listed, and then install:

   ```
   adb -s emulator-5554 uninstall com.applock.test </dev/null
   adb -s emulator-5554 uninstall com.applock </dev/null
   adb -s emulator-5554 shell pm list packages com.applock </dev/null; echo "exit status $?"
   adb -s emulator-5554 install -r -g app/build/outputs/apk/prod/debug/app-prod-debug.apk
   adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
   ```

   An `uninstall` of a package that is not installed fails, for example with `Failure [DELETE_FAILED_INTERNAL_ERROR]`
   on the Moto G. That is correct here. The `pm list` command must print no `package:` line and `exit status 0`. An
   error with `exit status 1` means that the query failed. This uninstall happens before any run, not between an
   action and its inspection.
4. **Screen lock.** Disable the keyguard and turn the screen on:

   ```
   adb -s emulator-5554 shell locksettings set-disabled true </dev/null
   adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP </dev/null
   ```

   If `locksettings` refuses, use `adb -s emulator-5554 shell wm dismiss-keyguard </dev/null`. Record the step that
   you used.
5. **PIN and P1 check.** The P2 harness needs the PIN 1234. The P1 harness creates it on a new install, and it also
   checks the kills, the inspections, and the settings restore on this AVD:

   ```
   bash scripts/r007/p1_validate.sh -s emulator-5554 </dev/null
   ```

   Expected: the last line reads `R-007 P1 device harness: 18 passed, 0 failed`, and the exit status is 0. On API 36
   this repeats the P1 lane of 2026-09-28. On API 30 it is the first run of the P1 harness. Any other result: stop.

## 6. Probes (read-only)

The device-facing checks of the P2 harness ran on API 35 only. Check them on this AVD before the smoke run. These
commands change nothing on the device, except the UI dump file of the harness on `/sdcard`.

```
SERIAL=emulator-5554 bash -c 'source scripts/r007/lib_p2.sh
  if r007_debuggable; then echo "run-as: ok"; else echo "run-as: FAILED"; fi
  if r007_test_apk_installed; then echo "test APK: ok"; else echo "test APK: MISSING or unknown"; fi
  if r007_unlocked; then echo "keyguard: not showing"; else echo "keyguard: SHOWING or unknown"; fi
  echo "settings: $(r007_settings_read | tr "\n" " " || echo UNREADABLE)"
  echo "settings record: $(r007_settings_record_state || echo UNKNOWN)"
  pending="$(r007_pending_list)" && echo "pending markers: ${pending:-none}" || echo "pending markers: UNKNOWN"
  echo "display rotation: $(r007_display_rotation || echo UNREADABLE)"
  echo "device clock: $(r007_device_wall || echo NOT-13-DIGITS)"
  echo "boot id: $(r007_boot_id || echo UNREADABLE)"
  echo "detector: $(r007_detector_state || echo UNKNOWN)"
  echo "ui dump: $(ui_xml | wc -c) bytes"
  r007_resolve_clock >/dev/null && echo "clock app: $R007_CLOCK" || echo "clock app: NONE"' </dev/null
adb -s emulator-5554 shell dumpsys power </dev/null | grep -E "mWakefulness=|mStayOn=|mIsPowered="
adb -s emulator-5554 shell dumpsys battery </dev/null | grep -i powered
adb -s emulator-5554 shell settings get system screen_off_timeout </dev/null
adb -s emulator-5554 shell "mount | grep ' /data '" </dev/null
adb -s emulator-5554 shell getconf CLK_TCK </dev/null
adb -s emulator-5554 shell nproc </dev/null
adb -s emulator-5554 get-state </dev/null && adb -s emulator-5554 logcat -g </dev/null
```

A failed query prints `FAILED`, `UNREADABLE`, `UNKNOWN`, `NONE`, `NOT-13-DIGITS`, or `0 bytes`, never a normal value.
The block was tested as a paste on the Moto G, and once with a serial that does not exist.

| Check | Expected | If not |
|---|---|---|
| run-as | `ok` | Not a debuggable build: rebuild (section 3) |
| test APK | `ok` | Install the androidTest APK (section 5) |
| keyguard | `not showing` | Repeat the screen-lock step of section 5 |
| settings | five values: the accessibility services as a component name, an empty value, or `null`; each other value an integer or `null` | Stop |
| settings record | `absent` | `present`: an earlier run did not finish. Run `restore_settings.sh` |
| pending markers | `none` | Run `restore_settings.sh`, then probe again |
| display rotation | `0` to `3` | `UNREADABLE`: the display dump of this image differs. Small fix (section 9.3) |
| device clock | 13 digits | The harness needs the device time in ms (`date +%s%3N`). Small fix (section 9.3) |
| boot id | a UUID | Stop |
| detector | `unbound` | `UNKNOWN`: the accessibility dump of this image differs. Small fix (section 9.3) |
| ui dump | more than 1000 bytes | `0 bytes` at API 30: use the GPU fallback of section 5. Any other short dump: stop |
| clock app | `com.android.deskclock` | `NONE`: the legacy caller has no protected app. Stop |
| power state | `mWakefulness=Awake`, `mIsPowered=true` | The stay-awake setting cannot work. Stop |
| battery | at least one `powered: true` line | Same as the power state |
| `/data` mount, `CLK_TCK`, `nproc`, log buffers | record only | The R3.1c classes depend on the file system of `/data`. R3.1a counts CPU ticks of 10 ms (`CLK_TCK` 100) |

Record the whole output. The report compares the settings with the values after the runs.

## 7. Smoke run

The smoke run runs each segment once with one repeat (`-r 1`). It checks the harness on this AVD. It is not evidence
of record. Run the segments in this order, one at a time, each to its end:
`healthy`, `cold-read`, `degraded`, `death`, `reset`, `cross`, `reboot`.

```
mkdir -p build/r007-console
bash scripts/r007/p2_device.sh -s emulator-5554 -r 1 -o SEGMENT </dev/null \
  > build/r007-console/smoke-apiNN-SEGMENT.out 2>&1
```

`NN` is the API level. Use `-o` for every segment: the screen-off steps need it, and the `reboot` segment refuses to
start without it. At each reboot, the harness prints `ACTION: unlock the device`. The AVD has no keyguard, so no
action is needed. The harness goes on when the AVD has booted.

After each segment, check:

- The last line reads `R-007 P2 SEGMENT: N passed, M failed`, and it appears once. Record the exit status.
- The evidence file (`build/r007-evidence/p2_SEGMENT-emulator-5554-*.log`) ends with a `## settings-restore` section
  with `match=yes`. The console file shows `PASS the store is back at Z` and `PASS the app settings file is restored`.
- Each case marker (`## CASE` line) shows `predicted=yes`.

The operator makes only these changes without the lead, and records each one:

| Observation in the smoke run | Change for this API level |
|---|---|
| A PIN that did not reach the app: a missing `WRITE` line after a PIN entry, or a correct PIN that did not unlock | Put `TAP_GAP=1.3` before the command of every segment, and repeat the smoke run of the segments run so far |
| A failure in a screen-off step of H06 or R3.3 | Run `healthy` and `death` without `-o`. Their markers then record `screen_cycle=skipped`, as on the Moto G |
| R3.1a: `the control gave no kill window`, or too few counted trials after 30 trials (`counted=no` lines) | None. R3.1a is then a gap of this API level, and the report gives the window and the reasons |
| R3.1c: trials without a kill (`the loop did not kill in trial N`) | None. These count as failures of the segment. The report gives the counts |
| `## gate-retry` lines of caller L | None. These are not failures |
| A wait that ends too early: a gate, a detector bind, a boot, a death after a kill, or the screen | Raise its environment variable (section 9.2) |
| A device query that cannot read the output of this image | A small fix (section 9.3) |

Any other failure in the smoke run: stop.

## 8. Recorded run

Run the seven segments of section 7 in the same order, without `-r`. Use the changes of sections 7 and 9 for this
API level, with the same values for all segments of one API level. Before each segment:

- The `git status` command of section 3 prints nothing: the harness, the app, and the build files have no
  uncommitted change.
- Other uncommitted files are allowed, but their diff is recorded, as the evidence bundle E0 of the test plan asks.
  The evidence header counts them in `host_changed_files`. When the count will be above 0, save the diff first:

  ```
  { git rev-parse HEAD; git status --porcelain; git diff HEAD; } > build/r007-console/apiNN-SEGMENT.git.txt
  ```

  The record gives the content of changed tracked files, and the names of new untracked files.

```
bash scripts/r007/p2_device.sh -s emulator-5554 -o SEGMENT </dev/null > build/r007-console/apiNN-SEGMENT.out 2>&1
```

For comparison: the Moto G recorded run took 12 h 7 min, and its `death` segment took the longest, 3 h 31 min. The
NucBox ran the P1 harness 20 to 30 % slower than the Moto G.

After each segment, make the checks of section 7. Then:

- **Go on with the next segment** when the only failures are repeats that were not as predicted, R3.1a trials that did
  not count, or R3.1c trials without a kill. The report gives each of them.
- **Stop** when the preflight refused the run, the cleanup failed (no `match=yes`, or a store or app-settings restore
  failed), or a run stopped with `the run stopped with exit status`. Also stop when the `git status` command of
  section 3 prints a file, or when the APK hashes of the evidence header differ from the hashes of section 3.

After the last segment of an API level, check that the settings equal the probe values and that no settings record,
no control directory, and no app process remain:

```
SERIAL=emulator-5554 bash -c 'source scripts/r007/lib_r007.sh
  echo "settings: $(r007_settings_read | tr "\n" " " || echo UNREADABLE)"
  echo "settings record: $(r007_settings_record_state || echo UNKNOWN)"' </dev/null
adb -s emulator-5554 shell \
  "run-as com.applock sh -c 'if [ -e files/r007 ]; then echo present; else echo absent; fi'" </dev/null
adb -s emulator-5554 shell "if pidof com.applock >/dev/null; then echo running; else echo none; fi" </dev/null
```

Expected: the probe settings, `absent`, `absent`, and `none`. An empty answer or an error means that the query
failed.

## 9. Failures, fixes, and reruns

The harness was tuned on the Moto G (API 35). An emulator can answer a device query in another format, or take
longer for a step. Section 9.2 and section 9.3 give the two ways to adapt the harness without the lead.

### 9.1 Results and reruns

- Keep every evidence file and console file, also of a failed or stopped run. The report lists each run.
- A repeat that is not as predicted is a result. Do not rerun to replace it, and do not change the harness to make it
  pass.
- A rerun is allowed only when the cause is known and lies outside the app: the emulator or the host stopped, adb
  lost the emulator, or a wait or a device query of the harness failed and section 9.2 or 9.3 fixed it. Rerun the
  affected cases with `-k` in the `cold-read` segment, and the whole segment otherwise. The report gives both runs.
- After a crash of the host or a kill of a script, the exit handler did not run. Run
  `bash scripts/r007/restore_settings.sh -s emulator-5554 </dev/null` before anything else.
- Every other case: stop.

### 9.2 Environment overrides

Most waits of the harness are environment variables with a default. When a wait ends too early on this AVD, set a
larger value before the command, as for `TAP_GAP`:

```
TAP_GAP=1.3 R007_GATE_WAIT=40 bash scripts/r007/p2_device.sh -s emulator-5554 -o SEGMENT </dev/null \
  > build/r007-console/apiNN-SEGMENT.out 2>&1
```

| Variable | Default | What it sets |
|---|---|---|
| `TAP_GAP` | 0.9 | seconds between PIN taps |
| `R007_GATE_WAIT` | 20 | seconds to wait for a gate |
| `R007_GATE_TRIES` | 3 | launches before the opening of a gate fails |
| `R007_BIND_POLLS` | 40 | polls, 0.25 s apart, for a detector bind or unbind |
| `R007_BOOT_WAIT` | 240 | seconds to wait for a reboot |
| `R007_KILL_POLLS` | 40 | polls, 0.25 s apart, for the death of a killed process |
| `R007_WAKE_POLLS` | 8 | polls, 0.25 s apart, for the screen to turn on |
| `R007_ROTATION_POLLS` | 20 | polls, 0.25 s apart, for a rotation |

- Set the values in the smoke run, so that the recorded run of an API level uses the same values for all its
  segments. When a recorded segment still needs a larger value, rerun the segment with it (section 9.1).
- A larger wait does not change a verdict. The other variables of the harness change what a case counts or predicts,
  for example `R31A_ENTRY_TICKS`. The lead decides them.
- The evidence records only `TAP_GAP`. The report gives every override and the segments that used it.

### 9.3 Small harness fixes

When a device query of the harness cannot read the output of this image, or a step that only an emulator needs is
missing, and no override helps, the operator may prepare a small fix.

- **In scope:** changes under `scripts/r007` and `scripts/e2e` to the parsing of device output (for example the
  display rotation, the accessibility dump, or the device clock), to waits and polls, and to steps that only an
  emulator needs. Such a fix changes no APK, so the build of section 3 stays valid.
- **Out of scope, for the lead:** the predictions, the verdict conditions, the case steps, the repeat counts, the
  fixtures, and any change under `app/`, including the androidTest tools.
- **Each fix:**
  1. keeps the old behaviour wherever the old output still appears, so the Moto G and the other API level are not
     affected;
  2. gets a host test in `scripts/r007/test_lib_r007.sh` with the captured device output as its sample;
  3. passes all host tests (`bash scripts/r007/test_lib_r007.sh`);
  4. passes the hand-off check of `WRITING_RULES.md`, and comes with a draft changelog entry;
  5. may run in a smoke run of the affected segments before the commit. Such a run is a trial and not evidence of
     record. Save its diff as in section 8;
  6. goes to the user, who commits it. The operator then runs the git sync and goes on at the new revision.
- A recorded run never runs on an uncommitted fix (section 8). When a fix comes during the recorded runs, the
  segments after it run at the new revision, and the report gives the revision of each segment.

## 10. Report

- Write one report for both lanes. It goes to the campaign reports, with the file name
  `<filing date>_m7-wp2-f2h-p2-device_nucbox-g5.md`, as the naming rule of `reports/README.md` asks.
- Keep the draft in `build/r007-report-draft/` while runs remain, and move it to its filing name after the last run.
  The `build/` folder is ignored by git, so the draft cannot be staged by accident before it is final, and it adds no
  entry to the changed-file list of the runs. A draft among the reports would not change a result, but each later run
  would then need its diff saved (section 8).
- Use the sections of the Moto G report of 2026-10-04: the header (dates and capture times, author and host, tested
  revision, APK hashes, toolchain, scope), Headline, Context & terms, Procedure with a table of the runs, the results
  for each case group with both verdicts, Findings, Device facts, Notes, the smoke run, the cases not run,
  Disposition, Follow-ups, and an appendix with the SHA-256 of each evidence file.
- Give the results of both API levels side by side, and compare them with the Moto G lane where they differ.
- Also record: the AVDs and images, the emulator version, the GPU mode, the screen-lock step, the P1 check, the probe
  output, `TAP_GAP`, the use of `-o`, the changes of section 7, the overrides of section 9.2, and each fix of section
  9.3 with its commit and the device output that caused it.
- Keep the draft uncommitted until it is final. Do the hand-off check of `WRITING_RULES.md` and state it in the
  hand-off. The user commits the report with a changelog entry. After the commit, the report is fixed, and
  corrections follow the correction rule of `reports/README.md`.
- Update the NucBox row of the fleet table in `reports/README.md` to link the report.
- RTM: no change. Risk register: no change before P5.

## 11. After the lanes

The P2 exit follows: a clean JVM run at `05c69b7` or later, the lead's decision on each proposed skip of section 5.1
of the device plan, and the exit record in the F2 hardening entry of `M7_PLAN.md`. These steps are not part of the
NucBox lanes.

## Notes and risks

- **First P2 runs on an emulator and on API 30.** The P1 check and the probes of sections 5 and 6 cover the device
  queries. The smoke run covers the cases. A query that reads another output format on this image gets a small fix
  (section 9.3).
- **First screen-off steps.** No device has run the screen-off steps of H06 and R3.3, because the Moto G runs skipped
  them. Section 7 gives the fallback.
- **R3.1a kill window.** A trial kills in the middle of the window from the last tap to the write. A window under
  200 ms stops R3.1a. The PIN check can be faster on the x86_64 emulator. On the Moto G, the window was 775 ms wide
  for S and 931 ms for L.
- **R3.1c trials without a kill.** On the Moto G, the loop missed the backup file in 22 of 120 trials (finding 3 of
  the Moto G report). The emulator disk is a file on the host, so the catch rate can differ.
- **Reboots.** `adb reboot` restarts the emulator system. The serial stays `emulator-5554`. The reboot wait of the
  harness handles a device that connects before an unlock.
- **Tap gap.** On a slow emulator, `input tap` can drop a digit. `TAP_GAP` is 0.9 s by default. A gap of 1.3 s
  makes the API 30 runs 15 to 20 % longer.
- **Revision evidence.** The evidence header gives the host revision, the count of changed and new files that git
  reports, and the hashes of the installed APKs. The revision and the count do not cover files that git ignores,
  such as `local.properties`, and they do not show that the installed APKs come from that revision. Three checks
  together give the provenance: the installed hashes equal the hashes of the build of section 3; the scoped
  `git status` check shows that the harness and the build inputs that git tracks match the revision; and the saved
  diffs record every other change. Ignored build inputs and the build tools stay outside these checks. An APK hash
  identifies one build file, not a revision. On 2026-10-05, a clean build of the androidTest APK at `8d1c28b` had
  the same 55 entries, with the same content, as the earlier build of `f756287` on the Moto G, but in another order
  and with another file size, so its hash differed. A hash comparison therefore holds only against the installed
  build file.
- **Command tests.** These commands ran on the 2012 i7 host at `8d1c28b` on 2026-10-05, from Git Bash:
  - with the Moto G: the probe block, the P1 script (18 of 18), the `getprop` lines, the boot wait loop, the package
    list, and the after-run checks;
  - on the host only: the revision check, the scoped `git status` check, the evidence copy, the build (78 of 78 tasks
    in 7 min 22 s) and hash commands, the host tests, and the diff record;
  - with a serial that does not exist: the probe block, the P1 script, and both forms of the P2 command, which each
    ended within 1 s with exit status 2.

  The NucBox ran the emulator start, the install, and the screen-lock step for `matrix_api36` in the P1 lane on
  2026-09-28. Not tested yet: `emu kill`, `matrix_api30def` with `-gpu host`, the screen-off steps, and the P2
  segments on an emulator.
- **Disposable state.** The harness changes five device settings and restores them. The uninstall, the PIN, and the
  screen-lock step of section 5 are operator changes, and the report records them.
