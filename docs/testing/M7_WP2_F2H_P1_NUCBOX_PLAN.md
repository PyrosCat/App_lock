# M7 WP2 F2 hardening, phase P1: NucBox emulator lane plan

**For:** the NucBox G5 (the fleet's x86_64 emulator host) and its operator.
**Status:** ready, written 2026-09-27 for the harness at commit `d1887f8`.
**Companion:** the Moto G lane is complete at `d1887f8`
(`docs/reports/campaigns/2026-09-27_m7-wp2-f2h-p1-harness-settings_moto-g-2025.md`).
**SSOT:** `docs/process/M7_PLAN.md`, F2 hardening entry. The phase definition is §13 of
`docs/process/active/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`.

Phase P1 validates the R-007 device harness itself, not the lockout candidates. The P1 exit needs the JVM harness
(done), the Moto G lane (done), and one NucBox emulator lane. This plan is that lane: one recorded run of
`scripts/r007/p1_validate.sh` on an API 36 emulator, with its own report.

## What the lane shows

| P1 exit criterion | Case in `p1_validate.sh` |
|---|---|
| A healthy commit survives (positive control) | V2 |
| A deliberate old-state restart is detected (negative control) | V3 |
| The harness distinguishes old, new, and unknown state | Old and new: V3 and V4. Unknown: V8 |
| The target process dies | The six kills of V1 to V5 and V7 |
| The tests cannot reset the counters themselves | No data clear or reinstall between an action and its inspection |

Two criteria are not device work on this host. "Faults cannot ship enabled" rests on the release dex scan of the
2026-09-25 Moto G report; the app code has not changed since. "The oracle catches a stale-write mutation" is
covered by the JVM harness.

## 1. Prerequisites

1. **Revision.** Run the session-start git sync. Use `origin/main` at or after `d1887f8`, with a clean working tree.
   Check that the harness and the app are unchanged since `d1887f8`:

   ```
   git diff --stat d1887f8 HEAD -- scripts/r007 scripts/e2e app
   ```

   The output must be empty. If it is not, the report names the revision that it tests.
2. **Host tools.** Use Git Bash, with the SDK `platform-tools` and `emulator` folders at the front of `PATH`, as in
   the 2026-09-16 NucBox report. Run every command of this plan from the repository root. The commands that run
   `adb shell` read from `</dev/null`: `adb shell` reads its standard input and would take the next lines of a
   pasted block.
3. **AVD.** Use `matrix_api36`: `android-36;default` (AOSP), x86_64, Pixel 5 profile, with no screen-lock credential.
   Boot it headless, with the hardware GPU:

   ```
   emulator -avd matrix_api36 -no-snapshot -no-audio -no-boot-anim -no-window -memory 3072 -gpu host &
   adb wait-for-device
   until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed </dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
   ```

   On API 36 the software GPU did not render the Compose surfaces (2026-09-16 report). A windowed `-gpu host` boot
   coincided with a host BSOD once, so boot headless only.
4. **Serial.** The commands use `emulator-5554`, the serial of the first emulator. If `adb devices` shows another
   serial, use that one.

## 2. Build and install

```
gradlew.bat :app:clean assembleProdDebug assembleProdDebugAndroidTest --no-build-cache
adb -s emulator-5554 install -r -g app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
sha256sum app/build/outputs/apk/prod/debug/app-prod-debug.apk \
  app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
```

- Record both hashes. They differ from the Moto G hashes, because each host signs debug builds with its own key.
- The install must be disposable: the harness writes the private files of the app. The first run creates the PIN
  1234.
- Do not use `connectedProdDebugAndroidTest` for this lane. It uninstalls the app and the test APK after the run.

## 3. Screen lock

V0 stops when the keyguard shows. On an AVD without a credential, disable the keyguard and turn the screen on:

```
adb -s emulator-5554 shell locksettings set-disabled true </dev/null
adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP </dev/null
```

If `locksettings` refuses, dismiss the keyguard with `adb -s emulator-5554 shell wm dismiss-keyguard </dev/null`.
Record the step that you used in the report.

## 4. Host tests

```
bash scripts/r007/test_lib_r007.sh
```

Expected at `d1887f8`: `169 tests, 0 failed`. Record the result.

## 5. Preflight (read-only)

The device-facing checks of the harness were validated on the Moto G (API 35) only. Check them on this emulator
before the recorded run. These commands change nothing on the device.

```
SERIAL=emulator-5554 bash -c 'source scripts/r007/lib_r007.sh
  if r007_debuggable; then echo "run-as: ok"; else echo "run-as: FAILED"; fi
  if r007_test_apk_installed; then echo "test APK: ok"; else echo "test APK: MISSING or unknown"; fi
  if r007_unlocked; then echo "keyguard: not showing"; else echo "keyguard: SHOWING or unknown"; fi
  echo "settings: $(r007_settings_read | tr "\n" " " || echo UNREADABLE)"
  echo "settings record: $(r007_settings_record_state || echo UNKNOWN)"
  echo "display rotation: $(r007_display_rotation || echo UNREADABLE)"' </dev/null
adb -s emulator-5554 shell dumpsys power </dev/null | grep -E "mWakefulness=|mStayOn=|mIsPowered="
adb -s emulator-5554 shell dumpsys battery </dev/null | grep -i powered
adb -s emulator-5554 shell settings get system screen_off_timeout </dev/null
```

Each check prints a value. A failed query prints `FAILED`, `UNREADABLE`, or `UNKNOWN`, never a normal value.

| Check | Expected | If not |
|---|---|---|
| run-as | `ok` | Not a debuggable build. Rebuild `prodDebug` (section 2) |
| test APK | `ok` | Install the androidTest APK (section 2) |
| keyguard | `not showing` | Repeat section 3 |
| settings | three values, each an integer or `null` | Stop and report |
| settings record | `absent` | `present`: an earlier run did not finish. Run `bash scripts/r007/restore_settings.sh -s emulator-5554` |
| display rotation | `0` in portrait (0 to 3) | `UNREADABLE`: the display dump of API 36 differs. Stop and report |
| power state | `mWakefulness=Awake`, `mIsPowered=true` | No power source: the stay-awake setting cannot work. Stop and report |
| battery | at least one `powered: true` line (an emulator normally reports AC) | Same as the power state |

Record the settings values and the screen timeout. The report compares them with the values after the run.

Do not change the harness on this host. A harness fix is a separate commit, and the lane then runs at the new
revision.

## 6. Recorded run

Start the run right after the preflight, while the screen is on. From V0 on, the harness keeps the screen on.

```
bash scripts/r007/p1_validate.sh -s emulator-5554
```

**Pass criteria:**

- The last line reads `R-007 P1 device harness: 18 passed, 0 failed`, the exit status is 0, and the summary line
  appears once.
- No line reads `the run stopped with exit status`.
- The evidence header shows the tested revision and `host_changed_files=0`.
- The evidence file has one `## settings-before` section and one `## settings-restore` section, and the second one
  ends with `match=yes`.
- After the run, the settings equal the preflight values, and no settings record, no control directory, and no app
  process remain:

  ```
  SERIAL=emulator-5554 bash -c 'source scripts/r007/lib_r007.sh
    echo "settings: $(r007_settings_read | tr "\n" " " || echo UNREADABLE)"
    echo "settings record: $(r007_settings_record_state || echo UNKNOWN)"' </dev/null
  adb -s emulator-5554 shell \
    "run-as com.applock sh -c 'if [ -e files/r007 ]; then echo present; else echo absent; fi'" </dev/null
  adb -s emulator-5554 shell "if pidof com.applock >/dev/null; then echo running; else echo none; fi" </dev/null
  ```

  Expected: the preflight settings, `absent` for the record, `absent` for the control directory, and `none` for the
  app process. An empty answer or an error means that the query failed.

**The 18 checks:** V0 checks that the fault wrapper is present (1). V1 to V5 and V7 each check the kill and the
stored count (12). V5 also checks that `commit()` returned false (1). V8 checks the read error, the restore, and the
restored count (3). The exit handler checks the settings restore (1). The settings change and the wake check in V0
have no check line of their own: a failure there stops the run.

## 7. If the run fails

- Keep the evidence file and the console output. The report records each run at the tested revision, including a
  failed run and its cause.
- Rerun only after the cause is known.
- Known causes from the Moto G runs:
  - **The screen went off or locked before V0 set stay-awake.** V0 fails with "the device locked before the screen
    was kept on". Repeat section 3 and start the run at once.
  - **A settings record of an earlier run.** V0 fails with "an earlier run did not restore the device settings".
    Run `bash scripts/r007/restore_settings.sh -s emulator-5554`, then rerun.
  - **PIN taps that do not arrive.** The wrong-PIN cases then write nothing, so V2 to V5 have no `WRITE` lines in
    the evidence file. The harness waits `TAP_GAP` seconds between taps (default 0.9). A slow emulator may need
    more, for example `TAP_GAP=1.3 bash scripts/r007/p1_validate.sh -s emulator-5554`. Record the value.
- After an early stop, the exit handler restores the settings and the V8 store copy. A crash of the host or a kill
  of the script skips the exit handler, so run `restore_settings.sh` before anything else.

## 8. Optional checks

These checks are not P1 exit criteria. Record them in the report if you run them.

- **Settings recovery.** A simulated crash leaves the settings record. The next run must stop in V0, and
  `restore_settings.sh` must restore the settings:

  ```
  SERIAL=emulator-5554 bash -c 'source scripts/r007/lib_r007.sh
    R007_LOG_OUT=build/r007-evidence/crash-sim.log; trap r007_settings_restore_owned EXIT
    r007_settings_apply && kill -9 $BASHPID' </dev/null
  bash scripts/r007/p1_validate.sh -s emulator-5554 </dev/null       # expected: a V0 stop and exit status 1
  bash scripts/r007/restore_settings.sh -s emulator-5554 </dev/null   # expected: PASS, match=yes
  ```

- **More API levels.** P1 needs one emulator lane. P4 needs six API levels, so extra levels belong to the P4 plan.

## 9. Report

- Write one report for this host: `docs/reports/campaigns/<filing date>_m7-wp2-f2h-p1-harness_nucbox-g5.md`, named
  per `docs/reports/README.md`.
- Use the sections of the 2026-09-27 Moto G report: the header (dates and capture times, author and host, tested
  revision, build and APK hashes, toolchain, scope), Status, the procedure with a before, during, and after table,
  the results per case, V8, the inspection checks, the device settings, notes, the disposition against the P1 exit
  criteria, follow-ups, and the evidence file as an appendix.
- Also record the AVD, the system image, the GPU mode, the emulator version (`emulator -version`), the screen-lock
  step, the preflight output, and `TAP_GAP` if you changed it.
- Keep the draft uncommitted until it is final. The user commits it with a changelog entry. After the commit, the
  report is fixed, and corrections follow rule 1 of `docs/reports/README.md`.
- Update the NucBox row of the fleet table in `docs/reports/README.md` to link the report.
- RTM: no change.

## 10. After the lane

The lead records the P1 exit in the F2 hardening entry of `docs/process/M7_PLAN.md` and in the changelog. Phase P2,
the baseline characterization, follows. Its NucBox work gets its own plan.

## Notes and risks

- **First emulator run.** This is the first run of the harness on an emulator and on API 36. The preflight of
  section 5 covers this risk.
- **Power source.** The stay-awake value 7 covers AC, USB, and wireless power. An emulator normally reports AC
  power. If an image reports none, V0 stops with "the stay-awake setting has no effect".
- **Disposable state.** The harness changes three device settings (stay-awake, auto-rotate, rotation) and restores
  them. The screen-lock step of section 3 is an operator change, and the report records it.
