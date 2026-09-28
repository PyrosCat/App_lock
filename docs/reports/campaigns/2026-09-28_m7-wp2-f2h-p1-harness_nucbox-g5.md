# M7 WP2 F2 hardening, phase P1: R-007 device harness on the NucBox G5 emulator lane (x86_64, API 36)

- **Date / captured:** The date of this report is 2026-09-28. The recorded run started at 2026-09-28 05:13:48Z and
  stopped at 05:18:02Z. In EDT, the run started at 01:13 and stopped at 01:18. The optional settings-recovery check
  followed, from 05:19:02Z to 05:19:15Z.
- **Author / host:** The author is the GMKtec NucBox G5, the x86_64 emulator host of the fleet. The host has
  Windows 11 Pro, an Intel N-series CPU, Intel UHD Graphics, and 12 GB of RAM. It controlled the emulator over adb
  from Git Bash.
- **Device:** The device is the AVD `matrix_api36` with the Pixel 5 profile: 1080 × 2340 pixels at 440 dpi, with
  3072 MB of RAM. Its system image is `system-images;android-36;default;x86_64`, revision 2, the AOSP "Default
  Android System Image". The image has Android 16 at API 36 for x86_64. Its build is `BE2A.250530.026.D1`, a
  userdebug build with test keys. Its security patch is 2025-07-05.
- **Emulator:** The emulator is version 36.6.11.0, build 15507667. It started without a window, with the flags
  `-no-snapshot -no-audio -no-boot-anim -no-window -memory 3072 -gpu host`. Its log shows
  `vulkan_mode_selected:host gles_mode_selected:host` on the Intel UHD Graphics GPU. The boot id was
  `66571adb-1b6b-43ea-99f4-d9cb1bcf4163` for all of the session. This boot id shows that the emulator did not restart
  between the preflight, the recorded run, and the optional check.
- **Tested revision:** The run used commit **`0785782`**. This commit was `origin/main` after the git sync at the
  start of the session. The working tree was clean, and the evidence header shows `host_changed_files=0`. The
  command `git diff --stat d1887f8 HEAD -- scripts/r007 scripts/e2e app` gave no output. The empty output shows
  that the harness and the app are those of `d1887f8`. The two commits after `d1887f8` change only documents: the
  Moto G report for `d1887f8` and the plan for this lane.
- **Build:** The build was a clean rebuild with `:app:clean` and `--no-build-cache`. Gradle executed 78 of 78 tasks
  in 4 min 46 s. The APK hashes are different from the Moto G hashes, because each host signs debug builds with its
  own key. The evidence header shows the same hashes for the installed base APKs.
- **Variant:** The run used the debuggable `prodDebug` build of `com.applock` and its androidTest APK,
  `com.applock.test`.
- **Toolchain:** The build used AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0, and JDK 21.0.10 from the Android Studio JBR.
  The host used adb 1.0.41 from platform-tools 37.0.0 and Git Bash 5.3.15.
- **Scope:** This report records the NucBox emulator lane of the R-007 device harness for phase P1 of the test plan
  for F2 hardening. The lane followed `docs/testing/M7_WP2_F2H_P1_NUCBOX_PLAN.md`, which this report calls "the
  plan". The run examines the harness. It does not examine the lockout. The report is not evidence for a
  requirement, so the RTM does not change.

| APK | SHA-256 of the local build and the installed base APK |
|---|---|
| `app-prod-debug.apk` | `7e5d89d1805ccab26ab0cb15c856c86137703b0c624803df89a61de2576d6803` |
| `app-prod-debug-androidTest.apk` | `246bff3eebeb69d2d335f8162c4e81393f684077904f1b596e169caf42eec468` |

## Status

- **The recorded run passed all 18 checks. The NucBox lane of P1 is complete at `0785782`, with the harness and the
  app of `d1887f8`.**
- The JVM harness and the Moto G lane passed before this run. With this lane, all three P1 lanes passed. The lead
  records the P1 exit.
- This run was the first run of the harness on an emulator and on API 36. Each check of the read-only preflight gave
  its expected value on this image. No change to the harness was necessary.
- The harness recorded the stay-awake and rotation settings as 15, 1, and 0. It set the values 7, 0, and 0 for the
  run. At the end, it restored the original values, and the evidence file shows `match=yes`.
- The optional settings-recovery check of the plan passed. After a simulated crash, the next run stopped in V0 and
  changed nothing. Then `restore_settings.sh` restored the settings.
- The host library tests passed 169 of 169.

## Context & terms

The terms of the 2026-09-26 and 2026-09-27 Moto G reports apply to this report. This list gives the terms in short
form:

- **R-007:** R-007 is the entry for lockout persistence in the risk register. The F2 hardening work is about the four
  residuals of R-007, /1 to /4.
- **Phase P1:** P1 is a phase of the test plan for F2 hardening,
  `docs/process/proposals/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`. Phase P1 examines the harness itself, not the
  lockout candidates. Three lanes are necessary for the P1 exit: the JVM harness, one NucBox emulator lane, and the
  Moto G. This report records the NucBox lane.
- **Cases V0 to V8:** These cases are the checks of `scripts/r007/p1_validate.sh`. V8 runs after V5 and before the
  V7 cleanup. V6 is not a separate step, because its conditions apply to each kill and each inspection.
- **Self-gate:** The self-gate is the PIN prompt of `MainActivity`. When the self-gate opens, it builds the lockout
  manager.
- **Fault wrapper:** The fault wrapper, `FaultInjectingLockoutStorage`, is a debug-only class around the real
  encrypted store. Before each operation, it reads a fault script from `files/r007/faults`. It logs each phase with
  the tag `R007Fault`. The script `HoldBeforeCommit` holds a write before the real `commit()`. The script
  `CommitThenHold` holds a write after the real `commit()`.
- **Inspector:** The inspector, `LockoutStoreInspector`, is an androidTest tool. It reads the stored pair,
  `failure_count` and `lockout_until`, in a new process. It logs with the tag `R007Inspect`. The command
  `am instrument` stops the app before the inspector starts.
- **Abrupt kill:** For an abrupt kill, the harness runs `run-as com.applock kill -9 <pid>`. Then it polls until
  `/proc/<pid>` is absent. An abrupt kill is not `am force-stop`.
- **Unknown state:** In the unknown state, the inspector cannot read the stored pair. The inspector then reports a
  read error and no count.
- **Store file:** The store file, `shared_prefs/applock_lockout.xml`, is the encrypted file that holds the stored
  pair. Each inspection compares the SHA-256 of this file before and after the inspector runs.
- **Settings record:** The settings record is the file `/data/local/tmp/r007_settings.pending` on the device. V0
  writes the original values of three settings to this file before it changes them. These settings are stay-awake,
  auto-rotate, and rotation: `stay_on_while_plugged_in`, `accelerometer_rotation`, and `user_rotation`. A run that
  finds a settings record stops in V0.
- **Exit handler:** The exit handler, `r007_finish`, is the EXIT trap of `p1_validate.sh`. It restores the V8 store
  copy and the device settings, if the run did not restore them before. Then it prints the only summary of the run.
- **AVD and `-gpu host`:** An AVD is an Android Virtual Device, the device profile of an emulator. In the `-gpu host`
  mode, the emulator renders with the real GPU of the host, not with the SwiftShader software renderer. On API 36,
  the software renderer did not render the Compose surfaces of the app, as the 2026-09-16 NucBox report shows. For
  this reason, the plan starts the emulator with `-gpu host`.

## Command and procedure

```
git diff --stat d1887f8 HEAD -- scripts/r007 scripts/e2e app      # empty
emulator -avd matrix_api36 -no-snapshot -no-audio -no-boot-anim -no-window -memory 3072 -gpu host
gradlew.bat :app:clean assembleProdDebug assembleProdDebugAndroidTest --no-build-cache
bash scripts/r007/test_lib_r007.sh                                 # during the build
adb -s emulator-5554 uninstall com.applock.test                    # earlier installation, see Notes
adb -s emulator-5554 uninstall com.applock
adb -s emulator-5554 install -r -g app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
adb -s emulator-5554 shell locksettings set-disabled true </dev/null
adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP </dev/null
(preflight of plan section 5)
bash scripts/r007/p1_validate.sh -s emulator-5554 </dev/null
(after-run checks of plan section 6)
```

- The operator ran the commands from the repository root in Git Bash. The SDK folders `platform-tools` and
  `emulator` were at the front of `PATH`. The recorded run ran as a background job, and its console output went to
  a file.
- The host tests ran during the Gradle build, from 05:06Z to 05:11Z. These tests use a stub adb and do not touch the
  device.
- **Screen lock (plan section 3):** The command `locksettings set-disabled true` gave the answer
  `Lock screen disabled set to true`. Before this step, the AVD already had no credential and a disabled lock screen.
  The lock settings showed `CredentialType: NONE` and `IsLockScreenDisabled: true`. For this reason, the step
  changed nothing. The operator did not use the fallback command `wm dismiss-keyguard`.
- `TAP_GAP` remained at its default value, 0.9 s. The operator changed no setting by hand. From V0 on, the harness
  kept the screen on.

| Setting or state | Before the run | During the recorded run | After the run |
|---|---|---|---|
| `settings global stay_on_while_plugged_in` | 15 | 7, set by V0 | 15 |
| `settings system screen_off_timeout` | 2147483647 ms | not read | 2147483647 ms |
| `settings system accelerometer_rotation` / `user_rotation` | 1 / 0 | 0 / 0, portrait | 1 / 0 |
| Settings record | absent | present | absent |
| Keyguard | disabled, with no credential | not shown | not shown |
| Stored lockout pair | no store file | changed by the cases | count 0, deadline 0 |
| `files/r007` control directory | absent | fault script and V8 store copy | removed |
| App process | not checked | one process for each case | none |

These notes explain some values in the table:

- The stay-awake value 15 was on the AVD before this session started.
- The value 2147483647 ms is the maximum screen timeout. The harness does not read or change this setting.
- The app installation was new. For this reason, the store file and the control directory were absent before the
  run.
- The operator did not check for an app process before the run, because V0 stops any app process.

## Preflight (plan section 5)

The operator ran the read-only preflight at 05:13:15Z, 33 s before the recorded run. The preflight gave this output:

```
run-as: ok
test APK: ok
keyguard: not showing
settings: global:stay_on_while_plugged_in=15 system:accelerometer_rotation=1 system:user_rotation=0
settings record: absent
display rotation: 0
  mWakefulness=Awake
  mIsPowered=true
  mStayOn=true
  mIsPowered=true
  AC powered: true
  USB powered: false
  Wireless powered: false
  Dock powered: false
2147483647
```

Each check gave the expected value from the table in the plan. The display rotation came from the `mRotation` field
of `dumpsys window displays`. The plan named a different display dump on API 36 as a risk, but this risk did not
occur. The emulator reports AC power, so the stay-awake setting operates, and the output shows `mStayOn=true`. The
last line of the output is the screen timeout in milliseconds.

The operator did one more check that the plan does not ask for. The PIN entry taps each key at the position that a
`uiautomator dump` of the screen gives. The preflight of the plan does not examine this dump. On this image, the dump
function `ui_xml` of `scripts/e2e/lib.sh` returned the launcher hierarchy with 10 018 bytes and 26 nodes. This check
wrote only the dump file of the harness on `/sdcard`.

## Results of the recorded run

The last line of the console output was `R-007 P1 device harness: 18 passed, 0 failed`. The exit status was 0, and
the summary line occurred one time. No line of the output contained `the run stopped with exit status`.

| Case | Action | App process | Wrapper or inspector evidence | Inspector process | Stored count | Expected |
|---|---|---|---|---|---|---|
| V0 | The harness recorded and set the settings and did the wake check. Then it created the PIN and started the self-gate. | 2535 | The settings-before section is in the evidence file. The wrapper logged `op=INIT phase=CREATED script=present`. | none | none | The settings are recorded, and the wrapper is present. |
| V1 | The harness entered the correct PIN. Then it killed the app. | 2535 | The reset write logged `REAL_RESULT result=true`. | 2826 | **0** | 0 |
| V2 | The harness entered a wrong PIN. Then it killed the app. | 2871 | The write of count 1 logged `result=true`. | 3010 | **1** | 1 |
| V3 | The harness entered a wrong PIN under `HoldBeforeCommit`. It killed the app during the hold. | 3068 | The write of count 2 logged `HELD` and no `REAL_RESULT`. | 3210 | **1** | 1 |
| V4 | The harness entered a wrong PIN under `CommitThenHold`. It killed the app during the hold. | 3266 | The write of count 2 logged `REAL_RESULT result=true` and then `HELD`. | 3409 | **2** | 2 |
| V5 | The harness ran `chmod 500 shared_prefs` and entered a wrong PIN. Then it ran `chmod 771 shared_prefs` and killed the app. | 3467 | The write of count 3 logged `script=Normal result=false`. | 3615 | **2** | 2 |
| V8 | The harness saved the store and changed one encrypted value. Then it ran the inspector. | none | The inspector reported `r007_read_error=java.lang.SecurityException`. | 3713 | **none** | a read error and no count |
| V8 | The harness wrote the saved store back. Then it ran the inspector. | none | The store hash was the same as the saved hash. | 3774 | **2** | 2 |
| V7 | The harness entered the correct PIN. Then it killed the app. | 3819 | The reset write logged `REAL_RESULT result=true`. | 3964 | **0** | 0 |
| End | The exit handler restored the settings. | none | The settings-restore section ends with `match=yes`. | none | none | Each value is the same as the recorded value. |

This table shows the 18 checks of the run:

| Case | What the case checks | Checks |
|---|---|---|
| V0 | V0 checks that the wrapper is present. | 1 |
| V1 to V5 and V7 | Each case checks the kill and the stored count. | 12 |
| V5 | V5 also checks that `commit()` returned false. | 1 |
| V8 | V8 checks the read error, the restore, and the restored count. | 3 |
| End | The exit handler checks the settings restore. | 1 |

The settings change and the wake check in V0 have no check line of their own. A failure in these steps stops the run.

| Measurement | NucBox | Moto G at `d1887f8` |
|---|---|---|
| Time for the recorded run | 4 min 14 s | 3 min 14 s |
| Time from one app start to the next in V2, V3, and V4 | approximately 32 s | approximately 27 s |

V0 created the PIN. For this reason, the reset write of V1 occurred 63 s after the start of the run, at 05:14:51Z.
Each wait of the harness ended before its timeout.

### V8: unknown state

The evidence file holds only logcat lines. For this reason, the store hashes come from the console output of the
run. The saved store file had the SHA-256 `9b3be4e4…aa27`. After the harness changed one encrypted value, the store
file had the SHA-256 `77b41dbe…1e29`.

The inspector in process 3713 reported `java.lang.SecurityException` and no count. The store file kept the changed
hash through the inspection. The restore wrote the saved copy back, and the store file then had the saved hash again.
A new process, 3774, read count 2, as before the change. The Moto G, with arm64 and API 35, reported the same type
of exception.

### Inspection checks

Before each of the eight inspections, the device showed that no app process ran. No inspection changed the hash of
the store file. Each inspection ran in a new process: 2826, 3010, 3210, 3409, 3615, 3713, 3774, and 3964. Logcat
holds the `INSPECT_BEGIN` and `INSPECT_END` markers of each inspection. No `R007Fault` line in the evidence file
comes from one of these eight processes. The `before-V0` section of the evidence file is empty, because the app did
not run between the installation and the run.

### Device settings

The settings-before section records stay-awake 15, auto-rotate 1, and rotation 0. The settings-restore section shows
that each of the three values is the same as the recorded value. This section ends with `match=yes`.

### After-run checks (plan section 6)

| Query | Answer | Expected answer |
|---|---|---|
| Settings | `global:stay_on_while_plugged_in=15 system:accelerometer_rotation=1 system:user_rotation=0` | the preflight values |
| Settings record | `absent` | `absent` |
| Control directory `files/r007` | `absent` | `absent` |
| App process | `none` | `none` |

After the run, the screen timeout was still 2147483647 ms, and the keyguard was not shown.

## Optional check: settings recovery (plan section 8)

The operator did this check after the after-run checks, from 05:19:02Z to 05:19:15Z. The check used the commands of
plan section 8.

| Step | Result | Expected result |
|---|---|---|
| 1. Simulate a crash: run `r007_settings_apply`, then `kill -9 $BASHPID`. | The shell stopped with status 137 before its exit trap ran. The settings were 7, 0, and 0. The settings record was present and held 15, 1, and 0. | The settings record remains on the device. |
| 2. Run `p1_validate.sh -s emulator-5554`. | V0 gave `FAIL an earlier run did not restore the device settings; run scripts/r007/restore_settings.sh first`. The summary was `0 passed, 1 failed`, and the exit status was 1. The evidence file of this run holds only the header, with no settings-before section. The run stopped before it started the app or changed a setting. | V0 stops the run, and the exit status is 1. |
| 3. Run `restore_settings.sh -s emulator-5554`. | The script gave `PASS the device settings are restored` for the values 15, 1, and 0, with `match=yes`. The summary was `1 passed, 0 failed`, and the exit status was 0. | The script gives PASS and `match=yes`. |
| 4. Read the settings and the settings record. | The settings were 15, 1, and 0. The settings record was absent. | The settings have the preflight values, and no settings record is present. |

The evidence files of this check are in Appendix B. This check is not a P1 exit criterion.

## Notes

- **First run on an emulator and on API 36.** The plan named the first emulator run as a risk, and it used the
  preflight to examine this risk. The keyguard field, the settings, the display rotation, and the power state gave
  the expected values. No change to the harness was necessary.
- **First recorded run in which V0 created the PIN.** The app installation was new. For this reason, V0 found the
  "Create a PIN" screen and entered the PIN 1234 two times. Then V0 started the self-gate. Each recorded Moto G run
  started with a PIN that was already set.
- **Earlier installation.** The AVD still held `com.applock` 0.1.0 and `com.applock.test` from the Gate-2 change-E
  session of 2026-09-16. The user data of an AVD remains after a cold boot with `-no-snapshot`. That build had no
  lockout store, and the PIN state of its credentials file was not known.
- **New test installation.** The operator uninstalled both packages before the install commands of the plan. The run
  then started from a new test installation and created its own PIN. Item 1 of §11.2 of the test plan for F2
  hardening asks for this type of installation. The uninstall occurred before the run, and not between an action
  and its inspection.
- **Device state at the start.** Before this session, the lock screen of the AVD was already disabled, and
  stay-awake was 15. The command `svc power stayon true` sets this value. The harness recorded 15 and restored 15.
  The Moto G runs restored the value 0. This run also shows a restore of an original value that is not zero.
- **Limitations.** Four limitations apply to this run. The Moto G reports also have the first three limitations.
  - The wrapper faults are barriers around the real `commit()`. They do not kill the process inside the file
    replacement of Android.
  - The run used one device and one boot, with no reboot case.
  - Only the self-gate caller ran.
  - The storage of the emulator is a virtual disk on the host. For this reason, the lane examines the harness on
    API 36. It does not examine the storage of real flash memory.

## Disposition

| P1 exit criterion | NucBox evidence |
|---|---|
| A healthy commit survives. This is the positive control. | V2 shows it. |
| The harness detects a deliberate restart with the old state. This is the negative control. | V3 shows it. |
| The harness distinguishes old, new, and unknown state. | V3 and V4 show the old and the new state. V8 shows the unknown state. |
| The target process dies. | All six kills ended the target process. |
| The tests cannot reset the counters themselves. | The script clears no data and does not reinstall between an action and its inspection. |
| Faults cannot ship enabled. | The release dex scan of the 2026-09-25 Moto G report shows it. No file under `app/` and no Gradle build file changed after that `e521026` build. The plan gives no device work for this criterion on this host. |
| The oracle catches a stale-write mutation. | The JVM harness, P1a, shows it. This criterion is not a device criterion. |

- **The NucBox lane of P1 is complete at `0785782`, with the harness and the app of `d1887f8`.**
- The JVM harness is also complete, and the Moto G lane is complete at `d1887f8`. With this lane, all three P1 lanes
  passed.
- The lead records the P1 exit in the F2 hardening entry of `docs/process/M7_PLAN.md` and in the changelog. Section
  10 of the plan gives this step.
- **RTM:** The RTM does not change.

## Follow-ups

- **Lead:** Record the P1 exit. Phase P2, the baseline characterization, comes next. The NucBox work of P2 gets its
  own plan.
- **P2 NucBox plan:** Before the install step, examine the AVD for an earlier installation of `com.applock`. The user
  data of an AVD remains after a cold boot with `-no-snapshot`. Also, examine if the preflight must include the
  UI-dump check of this report. The PIN entry uses this dump.

## Appendix A: evidence file of the recorded run

The evidence file is `build/r007-evidence/p1_validate-emulator-5554-20260928T051348Z.log`. It has 74 lines, and its
SHA-256 is `4aa8eea07568457123ec0c605191a31d62d595d17d684678812dbe6721a3039e`.

```
# R-007 evidence: p1_validate
# utc=2026-09-28T05:13:48Z host_rev=0785782f10265c6eb4f3f46f680fa0227b3e80ba host_changed_files=0
# serial=emulator-5554 model=Android SDK built for x86_64 sdk=36 boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163
# app=com.applock apk_sha256=7e5d89d1805ccab26ab0cb15c856c86137703b0c624803df89a61de2576d6803
# test=com.applock.test apk_sha256=246bff3eebeb69d2d335f8162c4e81393f684077904f1b596e169caf42eec468
## settings-before
global:stay_on_while_plugged_in=15
system:accelerometer_rotation=1
system:user_rotation=0
## before-V0

## V0-V1
         1790572441.581  2535  2535 I R007Fault: pid=2535 thread=main op=INIT phase=CREATED script=present wall=1790572441581 elapsed=488689
         1790572441.589  2535  2535 I R007Fault: pid=2535 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572441589 elapsed=488697
         1790572441.647  2535  2535 I R007Fault: pid=2535 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790572441647 elapsed=488755
         1790572491.547  2535  2796 I R007Fault: pid=2535 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790572491547 elapsed=538656
         1790572491.567  2535  2796 I R007Fault: pid=2535 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790572491567 elapsed=538676
         1790572491.570  2535  2796 I R007Fault: pid=2535 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790572491570 elapsed=538678
         1790572499.532  2826  2840 I R007Inspect: pid=2826 phase=INSPECT_BEGIN
         1790572499.591  2826  2840 I R007Inspect: pid=2826 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=0 r007_elapsed=546640 r007_lockout_until=0 r007_pid=2826 r007_wall=1790572499532
## V2
         1790572505.743  2871  2871 I R007Fault: pid=2871 thread=main op=INIT phase=CREATED script=present wall=1790572505743 elapsed=552852
         1790572505.750  2871  2871 I R007Fault: pid=2871 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572505750 elapsed=552858
         1790572505.801  2871  2871 I R007Fault: pid=2871 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790572505800 elapsed=552909
         1790572523.355  2871  2979 I R007Fault: pid=2871 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=1 until=0 wall=1790572523355 elapsed=570463
         1790572523.381  2871  2979 I R007Fault: pid=2871 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790572523381 elapsed=570489
         1790572523.381  2871  2979 I R007Fault: pid=2871 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790572523381 elapsed=570490
         1790572530.816  3010  3024 I R007Inspect: pid=3010 phase=INSPECT_BEGIN
         1790572530.874  3010  3024 I R007Inspect: pid=3010 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=1 r007_elapsed=577925 r007_lockout_until=0 r007_pid=3010 r007_wall=1790572530816
## V3
         1790572537.480  3068  3068 I R007Fault: pid=3068 thread=main op=INIT phase=CREATED script=present wall=1790572537480 elapsed=584588
         1790572537.491  3068  3068 I R007Fault: pid=3068 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572537491 elapsed=584600
         1790572537.546  3068  3068 I R007Fault: pid=3068 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790572537546 elapsed=584654
         1790572555.046  3068  3178 I R007Fault: pid=3068 thread=lockout-io op=WRITE index=0 phase=BEGIN script=HoldBeforeCommit+Normal count=2 until=0 wall=1790572555046 elapsed=602154
         1790572555.046  3068  3178 I R007Fault: pid=3068 thread=lockout-io op=WRITE index=0 phase=HELD script=HoldBeforeCommit+Normal wall=1790572555046 elapsed=602154
         1790572562.620  3210  3224 I R007Inspect: pid=3210 phase=INSPECT_BEGIN
         1790572562.692  3210  3224 I R007Inspect: pid=3210 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=1 r007_elapsed=609728 r007_lockout_until=0 r007_pid=3210 r007_wall=1790572562620
## V4
         1790572569.955  3266  3266 I R007Fault: pid=3266 thread=main op=INIT phase=CREATED script=present wall=1790572569955 elapsed=617064
         1790572569.964  3266  3266 I R007Fault: pid=3266 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572569964 elapsed=617073
         1790572570.023  3266  3266 I R007Fault: pid=3266 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790572570023 elapsed=617132
         1790572587.461  3266  3376 I R007Fault: pid=3266 thread=lockout-io op=WRITE index=0 phase=BEGIN script=CommitThenHold count=2 until=0 wall=1790572587461 elapsed=634569
         1790572587.476  3266  3376 I R007Fault: pid=3266 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=CommitThenHold result=true wall=1790572587476 elapsed=634584
         1790572587.476  3266  3376 I R007Fault: pid=3266 thread=lockout-io op=WRITE index=0 phase=HELD script=CommitThenHold wall=1790572587476 elapsed=634585
         1790572595.613  3409  3424 I R007Inspect: pid=3409 phase=INSPECT_BEGIN
         1790572595.672  3409  3424 I R007Inspect: pid=3409 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=2 r007_elapsed=642722 r007_lockout_until=0 r007_pid=3409 r007_wall=1790572595613
## V5
         1790572602.398  3467  3467 I R007Fault: pid=3467 thread=main op=INIT phase=CREATED script=present wall=1790572602398 elapsed=649507
         1790572602.406  3467  3467 I R007Fault: pid=3467 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572602406 elapsed=649515
         1790572602.455  3467  3467 I R007Fault: pid=3467 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790572602455 elapsed=649563
         1790572621.046  3467  3579 I R007Fault: pid=3467 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=3 until=0 wall=1790572621045 elapsed=668154
         1790572621.076  3467  3579 I R007Fault: pid=3467 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=false wall=1790572621076 elapsed=668184
         1790572621.076  3467  3579 I R007Fault: pid=3467 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=false wall=1790572621076 elapsed=668185
         1790572629.307  3615  3629 I R007Inspect: pid=3615 phase=INSPECT_BEGIN
         1790572629.357  3615  3629 I R007Inspect: pid=3615 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=2 r007_elapsed=676415 r007_lockout_until=0 r007_pid=3615 r007_wall=1790572629307
## V8
         1790572639.496  3713  3726 I R007Inspect: pid=3713 phase=INSPECT_BEGIN
         1790572639.556  3713  3726 I R007Inspect: pid=3713 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_elapsed=686605 r007_pid=3713 r007_read_error=java.lang.SecurityException r007_wall=1790572639496
         1790572646.061  3774  3789 I R007Inspect: pid=3774 phase=INSPECT_BEGIN
         1790572646.153  3774  3789 I R007Inspect: pid=3774 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=2 r007_elapsed=693170 r007_lockout_until=0 r007_pid=3774 r007_wall=1790572646061
## V7
         1790572652.021  3819  3819 I R007Fault: pid=3819 thread=main op=INIT phase=CREATED script=present wall=1790572652021 elapsed=699129
         1790572652.032  3819  3819 I R007Fault: pid=3819 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790572652032 elapsed=699140
         1790572652.079  3819  3819 I R007Fault: pid=3819 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790572652079 elapsed=699187
         1790572669.758  3819  3931 I R007Fault: pid=3819 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790572669758 elapsed=716867
         1790572669.795  3819  3931 I R007Fault: pid=3819 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790572669795 elapsed=716904
         1790572669.796  3819  3931 I R007Fault: pid=3819 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790572669796 elapsed=716905
         1790572677.543  3964  3978 I R007Inspect: pid=3964 phase=INSPECT_BEGIN
         1790572677.595  3964  3978 I R007Inspect: pid=3964 phase=INSPECT_END r007_boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163 r007_count=0 r007_elapsed=724652 r007_lockout_until=0 r007_pid=3964 r007_wall=1790572677543
## settings-restore
global:stay_on_while_plugged_in recorded=15 now=15
system:accelerometer_rotation recorded=1 now=1
system:user_rotation recorded=0 now=0
match=yes
```

## Appendix B: evidence files of the optional check

The file `build/r007-evidence/crash-sim.log` has 4 lines. Its SHA-256 is
`147d2719c6da77979b778485c5770eb1715a9a926e1cb43f4b88ce729ac969ef`.

```
## settings-before
global:stay_on_while_plugged_in=15
system:accelerometer_rotation=1
system:user_rotation=0
```

The file `build/r007-evidence/p1_validate-emulator-5554-20260928T051907Z.log` has 5 lines. Its SHA-256 is
`af88c6e34e3e01365621ff21b91fdab2a957d124ac5945d75350d96c717656f0`.

```
# R-007 evidence: p1_validate
# utc=2026-09-28T05:19:07Z host_rev=0785782f10265c6eb4f3f46f680fa0227b3e80ba host_changed_files=0
# serial=emulator-5554 model=Android SDK built for x86_64 sdk=36 boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163
# app=com.applock apk_sha256=7e5d89d1805ccab26ab0cb15c856c86137703b0c624803df89a61de2576d6803
# test=com.applock.test apk_sha256=246bff3eebeb69d2d335f8162c4e81393f684077904f1b596e169caf42eec468
```

The file `build/r007-evidence/restore_settings-emulator-5554-20260928T051910Z.log` has 10 lines. Its SHA-256 is
`f9d2f223e337b1a36ad080f8f6842d4c6f601ced374acfd68d5a96c9d2edf965`.

```
# R-007 evidence: restore_settings
# utc=2026-09-28T05:19:11Z host_rev=0785782f10265c6eb4f3f46f680fa0227b3e80ba host_changed_files=0
# serial=emulator-5554 model=Android SDK built for x86_64 sdk=36 boot_id=66571adb-1b6b-43ea-99f4-d9cb1bcf4163
# app=com.applock apk_sha256=7e5d89d1805ccab26ab0cb15c856c86137703b0c624803df89a61de2576d6803
# test=com.applock.test apk_sha256=246bff3eebeb69d2d335f8162c4e81393f684077904f1b596e169caf42eec468
## settings-restore
global:stay_on_while_plugged_in recorded=15 now=15
system:accelerometer_rotation recorded=1 now=1
system:user_rotation recorded=0 now=0
match=yes
```
