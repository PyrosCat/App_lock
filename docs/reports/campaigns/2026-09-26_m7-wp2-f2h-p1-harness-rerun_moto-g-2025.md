# M7 WP2 F2 hardening, phase P1: R-007 device harness rerun on the Moto G 2025 (arm64, API 35)

- **Date / captured:** 2026-09-26. The recorded run lasted from 19:20:37Z to 19:24:02Z. A first run at the same
  revision failed; it lasted from 19:08:54Z to 19:15:08Z.
- **Author / host:** 2012 i7 dev box (Windows 10 Pro), driving the Moto G 2025 over USB adb.
- **Device:** the same phone as in the [2026-09-25 report](2026-09-25_m7-wp2-f2h-p1-harness_moto-g-2025.md)
  (serial ZT4229HQ6X, Android 15, API 35, build `V1VKS35.22-125-5`). The boot id was still
  `bdbc452a-f16e-4bd7-828c-bccd8035840d`, so the phone did not restart between the two reports.
- **Tested revision:** commit **`f6d5883`** with a clean working tree (`host_changed_files=0` in both evidence
  headers). The harness scripts come from `4f90893`. That commit added the V0 precondition checks, case V8, and the
  store hash check. The two later commits changed only review documents and `.gitignore`.
- **Build:** clean rebuild (`:app:clean`, `--no-build-cache`, 78 of 78 tasks executed). Both APKs are byte-identical
  to the `e521026` build of the 2026-09-25 report, and the installed copies have the same hashes.
- **Variant:** `prodDebug` (`com.applock`, debuggable) and its androidTest APK (`com.applock.test`).
- **Toolchain:** AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0.
- **Scope:** the Moto G lane of the R-007 device harness (test plan phase P1). The 2026-09-25 report left this lane
  incomplete, because its run did not include the unknown-state case. The run does not verify lockout behaviour.

| APK | SHA-256 (local build = installed base APK) |
|---|---|
| `app-prod-debug.apk` | `0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb` |
| `app-prod-debug-androidTest.apk` | `f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1` |

## Status

- **All 17 checks of the recorded run passed, including the unknown-state case (V8). The Moto G lane of P1 is
  complete.**
- A first run at the same revision failed 10 of 21 checks. The screen was in landscape, so the wrong PIN never
  reached the app. At `f6d5883` the harness does not control the screen orientation.
- The host library tests passed 80 of 80.

## Context & terms

- **R-007:** the risk register entry for lockout persistence. F2 hardening treats its four residuals (/1 to /4).
- **Phase P1:** the test plan phase that validates the harness. The P1 exit needs the JVM harness, one NucBox
  emulator lane, and the Moto G.
- **Cases V0 to V8:** the checks of `scripts/r007/p1_validate.sh`. V8 runs after V5, before the V7 cleanup. V6 is
  not a separate step: its conditions apply to every kill and every inspection.
- **Self-gate:** the PIN prompt of `MainActivity`. Opening it builds the lockout manager.
- **Fault wrapper:** `FaultInjectingLockoutStorage`. This debug-only class wraps the real encrypted store. Before
  each operation it reads a fault script from `files/r007/faults`. It logs each phase with the tag `R007Fault`.
- **`HoldBeforeCommit` and `CommitThenHold`:** fault scripts that hold a write before or after the real `commit()`.
- **Inspector:** `LockoutStoreInspector`. This androidTest tool reads the stored pair (`failure_count`,
  `lockout_until`) in a new process and logs with the tag `R007Inspect`. `am instrument` stops the app first.
- **Abrupt kill:** `run-as com.applock kill -9 <pid>`, then a poll until `/proc/<pid>` is absent. It is not
  `am force-stop`.
- **Unknown state:** the stored pair cannot be read. The inspector then reports a read error and no count.
- **Store file:** `shared_prefs/applock_lockout.xml`. This encrypted file holds the stored pair.
- **Store hash check:** each inspection compares the SHA-256 of the store file before and after the inspector runs.
- **UI dump:** the `uiautomator dump` of the screen. The harness taps each PIN key at the position that the dump
  reports.
- **Stay-awake:** the setting `stay_on_while_plugged_in`. A non-zero value keeps the screen on while the phone is
  plugged in.
- **Auto-rotate:** the setting `accelerometer_rotation`. With 1, the screen turns with the phone. With 0, the screen
  keeps the rotation in `user_rotation`, where 0 is the natural portrait orientation.

## Command and procedure

```
gradlew.bat :app:clean assembleProdDebug assembleProdDebugAndroidTest --no-build-cache
adb -s ZT4229HQ6X install -r -g app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb -s ZT4229HQ6X install -r app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
bash scripts/r007/test_lib_r007.sh
bash scripts/r007/p1_validate.sh -s ZT4229HQ6X
```

The install kept the app data and the PIN (1234). Stay-awake was set before the rebuild, so the phone stayed
unlocked. Before the recorded run, the screen was locked to portrait through `settings put system
accelerometer_rotation 0` and `settings put system user_rotation 0`.

| Setting / state | Before | During the recorded run | After |
|---|---|---|---|
| `settings global stay_on_while_plugged_in` | 0 | 15 (`svc power stayon true`) | 0 |
| `settings system screen_off_timeout` | 30000 ms | 30000 ms | 30000 ms |
| `settings system accelerometer_rotation` / `user_rotation` | 1 / 1 (auto-rotate on, landscape) | 0 / 0 (portrait) | 1 / 1 |
| App process | 9541, left from a manual check of the PIN screen | stopped by V0, then one for each case | none |
| Stored lockout pair | count 0, deadline 0 | changed by the cases | count 0, deadline 0 |
| `files/r007` control directory | absent | fault script and the V8 store copy | removed |

## Results of the recorded run

| Case | Action | App process | Wrapper or inspector evidence | Inspector process | Stored count | Expected |
|---|---|---|---|---|---|---|
| V0 | Stop process 9541, launch the self-gate | 10039 | `op=INIT phase=CREATED script=present` | none | none | wrapper present |
| V1 | Correct PIN, kill | 10039 | reset write `REAL_RESULT result=true` | 10213 | **0** | 0 |
| V2 | Wrong PIN, kill | 10258 | write count 1, `result=true` | 10411 | **1** | 1 |
| V3 | Wrong PIN under `HoldBeforeCommit`, kill while held | 10469 | write count 2, `HELD`, no `REAL_RESULT` | 10619 | **1** | 1 |
| V4 | Wrong PIN under `CommitThenHold`, kill while held | 10675 | write count 2, `REAL_RESULT result=true`, `HELD` | 10829 | **2** | 2 |
| V5 | `chmod 500 shared_prefs`, wrong PIN, `chmod 771`, kill | 10887 | write count 3, `script=Normal result=false` | 11063 | **2** | 2 |
| V8 | Save the store, tamper one encrypted value, inspect | none | `r007_read_error=java.lang.SecurityException` | 11188 | **none** | read error, no count |
| V8 | Restore the saved store, inspect | none | restored hash equals the saved hash | 11269 | **2** | 2 |
| V7 | Correct PIN, kill | 11349 | reset write `REAL_RESULT result=true` | 11521 | **0** | 0 |

**The 17 checks:** V0 checks that the wrapper is present (1 check). V1 to V5 and V7 each check the kill and the
stored count (12 checks). V5 also checks that `commit()` returned false (1 check). V8 checks the read error, the
restore, and the restored count (3 checks). V6 adds no check of its own.

### V8: unknown state

The console output of the run gives the store hashes, because the evidence file holds only logcat lines. The saved
store file had SHA-256 `b5efdfe7…a093`. After the tamper it had `882d80d3…5f2d`. The inspector in process 11188
reported `java.lang.SecurityException` and no count. The store file kept the tampered hash through the inspection.
The restore wrote the saved copy back, and the store file then had the saved hash again. A new process (11269) read
count 2, as before the tamper.

### Inspection checks

Before each of the eight inspections, the device confirmed that no app process ran. Each inspection left the store
file hash unchanged. Each inspection ran in a new process, and logcat holds its begin and end markers. No
`R007Fault` line came from an inspector process.

The `before-V0` section of the evidence file holds lines from before the run: the last inspection of the failed run
(process 9488) and the manual check of the PIN screen (process 9541). Process 9541 found no fault script
(`script=absent`), because the failed run had removed the control directory.

## Failed run in landscape

The first run at `f6d5883` passed 11 and failed 10 of 21 checks. Auto-rotate was on, and the display was rotated to
landscape (`mRotation=1`, 1604 × 720). A UI dump of the PIN screen, taken after the run in the same orientation,
listed the keys 1 to 9 but not the key 0. The wrong PIN of the harness is 0000. When a key is missing from the dump,
the harness taps a fixed position of the portrait screen instead. Those taps did not submit a PIN: any four digits
would have caused a failure write.

- The only `WRITE` lines in the evidence file come from the two correct-PIN resets in V0-V1 and V7. V2 to V5 have
  none, so no wrong PIN reached the app.
- The failed checks follow from that: the missing writes in V2 to V5, the stored counts after them, and the count
  after the V8 restore (0 instead of 2).
- The checks of V8 itself passed: the tampered store gave a read error with no count and an unchanged file, and the
  restore matched the saved hash.

The evidence file of the failed run is in Appendix B.

## Notes

- **Limitations.** The limitations of the 2026-09-25 report also apply to this run.
  - The wrapper faults are barriers around the real `commit()`. They do not kill the process inside Android's file
    replacement.
  - The run used one device and one boot, with no reboot case.
  - Only the self-gate caller ran. The legacy `LockScreenActivity` caller did not run.
- **Correction to the 2026-09-25 report.** Its procedure table lists "release files" in the control directory.
  `p1_validate.sh` creates no release files; the directory held only the fault script.

## Disposition

| P1 exit criterion | Moto G evidence |
|---|---|
| A healthy commit survives (positive control) | V2 |
| A deliberate old-state restart is detected (negative control) | V3 |
| The harness distinguishes old, new, and unknown state | Old and new: V3 and V4. Unknown: V8 |
| The target process dies | Six of six kills |
| The tests cannot reset the counters themselves | The script clears no data and does not reinstall between an action and its inspection |
| Faults cannot ship enabled | Release dex scan in the 2026-09-25 report; the app code has not changed since |
| The oracle catches a stale-write mutation | JVM harness (P1a), not a device criterion |

- **The Moto G lane of P1 is complete.**
- **P1 exit stays open** until the NucBox lane passes and the lead records the exit.
- **RTM:** no change.

## Follow-ups

- Make the harness lock the screen to portrait during a run and restore the original device settings afterwards.
- Run the NucBox P1 lane on the NucBox host.
- Carry the P2 observations and the probe findings of the 2026-09-25 report into P2.

## Appendix A: evidence file of the recorded run

`build/r007-evidence/p1_validate-ZT4229HQ6X-20260926T192036Z.log` (68 lines, SHA-256 `140b16aa770c072677e1b4f040d7734ac9911e5795e51f0cc0dd55bad3acd29d`):

```
# R-007 evidence: p1_validate
# utc=2026-09-26T19:20:37Z host_rev=f6d58830bfe8c342f685db49dccaccef7a4396ec host_changed_files=0
# serial=ZT4229HQ6X model=moto g - 2025 sdk=35 boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d
# app=com.applock apk_sha256=0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb
# test=com.applock.test apk_sha256=f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1
## before-V0
         1790450108.356  9488  9502 I R007Inspect: pid=9488 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342154856 r007_lockout_until=0 r007_pid=9488 r007_wall=1790450108185
         1790450173.166  9541  9541 I R007Fault: pid=9541 thread=main op=INIT phase=CREATED script=absent wall=1790450173166 elapsed=342219836
         1790450173.167  9541  9541 I R007Fault: pid=9541 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450173167 elapsed=342219838
         1790450173.249  9541  9541 I R007Fault: pid=9541 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790450173249 elapsed=342219919
## V0-V1
         1790450447.851 10039 10039 I R007Fault: pid=10039 thread=main op=INIT phase=CREATED script=present wall=1790450447851 elapsed=342494521
         1790450447.858 10039 10039 I R007Fault: pid=10039 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450447858 elapsed=342494529
         1790450447.940 10039 10039 I R007Fault: pid=10039 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790450447940 elapsed=342494611
         1790450467.476 10039 10183 I R007Fault: pid=10039 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790450467476 elapsed=342514146
         1790450467.492 10039 10183 I R007Fault: pid=10039 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790450467492 elapsed=342514162
         1790450467.492 10039 10183 I R007Fault: pid=10039 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790450467492 elapsed=342514163
         1790450474.384 10213 10227 I R007Inspect: pid=10213 phase=INSPECT_BEGIN
         1790450474.536 10213 10227 I R007Inspect: pid=10213 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342521055 r007_lockout_until=0 r007_pid=10213 r007_wall=1790450474384
## V2
         1790450479.969 10258 10258 I R007Fault: pid=10258 thread=main op=INIT phase=CREATED script=present wall=1790450479969 elapsed=342526639
         1790450479.976 10258 10258 I R007Fault: pid=10258 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450479976 elapsed=342526646
         1790450480.057 10258 10258 I R007Fault: pid=10258 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790450480057 elapsed=342526728
         1790450496.451 10258 10379 I R007Fault: pid=10258 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=1 until=0 wall=1790450496450 elapsed=342543121
         1790450496.468 10258 10379 I R007Fault: pid=10258 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790450496468 elapsed=342543138
         1790450496.468 10258 10379 I R007Fault: pid=10258 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790450496468 elapsed=342543138
         1790450503.343 10411 10427 I R007Inspect: pid=10411 phase=INSPECT_BEGIN
         1790450503.501 10411 10427 I R007Inspect: pid=10411 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=342550014 r007_lockout_until=0 r007_pid=10411 r007_wall=1790450503343
## V3
         1790450509.800 10469 10469 I R007Fault: pid=10469 thread=main op=INIT phase=CREATED script=present wall=1790450509800 elapsed=342556470
         1790450509.808 10469 10469 I R007Fault: pid=10469 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450509808 elapsed=342556478
         1790450509.894 10469 10469 I R007Fault: pid=10469 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790450509894 elapsed=342556565
         1790450526.338 10469 10588 I R007Fault: pid=10469 thread=lockout-io op=WRITE index=0 phase=BEGIN script=HoldBeforeCommit+Normal count=2 until=0 wall=1790450526338 elapsed=342573008
         1790450526.338 10469 10588 I R007Fault: pid=10469 thread=lockout-io op=WRITE index=0 phase=HELD script=HoldBeforeCommit+Normal wall=1790450526338 elapsed=342573008
         1790450533.084 10619 10633 I R007Inspect: pid=10619 phase=INSPECT_BEGIN
         1790450533.224 10619 10633 I R007Inspect: pid=10619 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=342579755 r007_lockout_until=0 r007_pid=10619 r007_wall=1790450533084
## V4
         1790450541.170 10675 10675 I R007Fault: pid=10675 thread=main op=INIT phase=CREATED script=present wall=1790450541170 elapsed=342587840
         1790450541.179 10675 10675 I R007Fault: pid=10675 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450541179 elapsed=342587850
         1790450541.258 10675 10675 I R007Fault: pid=10675 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790450541258 elapsed=342587929
         1790450557.649 10675 10795 I R007Fault: pid=10675 thread=lockout-io op=WRITE index=0 phase=BEGIN script=CommitThenHold count=2 until=0 wall=1790450557649 elapsed=342604319
         1790450557.668 10675 10795 I R007Fault: pid=10675 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=CommitThenHold result=true wall=1790450557667 elapsed=342604338
         1790450557.668 10675 10795 I R007Fault: pid=10675 thread=lockout-io op=WRITE index=0 phase=HELD script=CommitThenHold wall=1790450557668 elapsed=342604339
         1790450564.786 10829 10843 I R007Inspect: pid=10829 phase=INSPECT_BEGIN
         1790450564.930 10829 10843 I R007Inspect: pid=10829 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=342611457 r007_lockout_until=0 r007_pid=10829 r007_wall=1790450564786
## V5
         1790450572.859 10887 10887 I R007Fault: pid=10887 thread=main op=INIT phase=CREATED script=present wall=1790450572859 elapsed=342619529
         1790450572.865 10887 10887 I R007Fault: pid=10887 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450572865 elapsed=342619536
         1790450572.949 10887 10887 I R007Fault: pid=10887 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790450572949 elapsed=342619620
         1790450589.773 10887 11026 I R007Fault: pid=10887 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=3 until=0 wall=1790450589773 elapsed=342636444
         1790450589.778 10887 11026 I R007Fault: pid=10887 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=false wall=1790450589778 elapsed=342636449
         1790450589.778 10887 11026 I R007Fault: pid=10887 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=false wall=1790450589778 elapsed=342636449
         1790450597.018 11063 11077 I R007Inspect: pid=11063 phase=INSPECT_BEGIN
         1790450597.173 11063 11077 I R007Inspect: pid=11063 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=342643688 r007_lockout_until=0 r007_pid=11063 r007_wall=1790450597017
## V8
         1790450606.594 11188 11209 I R007Inspect: pid=11188 phase=INSPECT_BEGIN
         1790450606.900 11188 11209 I R007Inspect: pid=11188 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_elapsed=342653264 r007_pid=11188 r007_read_error=java.lang.SecurityException r007_wall=1790450606594
         1790450613.059 11269 11309 I R007Inspect: pid=11269 phase=INSPECT_BEGIN
         1790450613.364 11269 11309 I R007Inspect: pid=11269 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=342659729 r007_lockout_until=0 r007_pid=11269 r007_wall=1790450613058
## V7
         1790450618.814 11349 11349 I R007Fault: pid=11349 thread=main op=INIT phase=CREATED script=present wall=1790450618814 elapsed=342665485
         1790450618.822 11349 11349 I R007Fault: pid=11349 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450618822 elapsed=342665492
         1790450618.904 11349 11349 I R007Fault: pid=11349 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790450618904 elapsed=342665574
         1790450635.441 11349 11488 I R007Fault: pid=11349 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790450635441 elapsed=342682111
         1790450635.483 11349 11488 I R007Fault: pid=11349 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790450635483 elapsed=342682154
         1790450635.484 11349 11488 I R007Fault: pid=11349 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790450635483 elapsed=342682154
         1790450642.224 11521 11535 I R007Inspect: pid=11521 phase=INSPECT_BEGIN
         1790450642.380 11521 11535 I R007Inspect: pid=11521 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342688894 r007_lockout_until=0 r007_pid=11521 r007_wall=1790450642224
```

## Appendix B: evidence file of the failed run

`build/r007-evidence/p1_validate-ZT4229HQ6X-20260926T190854Z.log` (54 lines, SHA-256 `a18d1c02d0bba72d9aa7bd100b98f98b64d439489ff180e4b7d777f89afef126`):

```
# R-007 evidence: p1_validate
# utc=2026-09-26T19:08:54Z host_rev=f6d58830bfe8c342f685db49dccaccef7a4396ec host_changed_files=0
# serial=ZT4229HQ6X model=moto g - 2025 sdk=35 boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d
# app=com.applock apk_sha256=0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb
# test=com.applock.test apk_sha256=f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1
## before-V0

## V0-V1
         1790449746.416  7202  7202 I R007Fault: pid=7202 thread=main op=INIT phase=CREATED script=present wall=1790449746416 elapsed=341793087
         1790449746.424  7202  7202 I R007Fault: pid=7202 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790449746423 elapsed=341793094
         1790449746.490  7202  7202 I R007Fault: pid=7202 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790449746490 elapsed=341793160
         1790449767.153  7202  7479 I R007Fault: pid=7202 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790449767152 elapsed=341813823
         1790449767.170  7202  7479 I R007Fault: pid=7202 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790449767170 elapsed=341813841
         1790449767.171  7202  7479 I R007Fault: pid=7202 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790449767171 elapsed=341813841
         1790449774.664  7512  7526 I R007Inspect: pid=7512 phase=INSPECT_BEGIN
         1790449774.806  7512  7526 I R007Inspect: pid=7512 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=341821335 r007_lockout_until=0 r007_pid=7512 r007_wall=1790449774664
## V2
         1790449781.403  7556  7556 I R007Fault: pid=7556 thread=main op=INIT phase=CREATED script=present wall=1790449781403 elapsed=341828074
         1790449781.411  7556  7556 I R007Fault: pid=7556 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790449781411 elapsed=341828082
         1790449781.498  7556  7556 I R007Fault: pid=7556 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790449781498 elapsed=341828169
         1790449849.430  7887  7901 I R007Inspect: pid=7887 phase=INSPECT_BEGIN
         1790449849.564  7887  7901 I R007Inspect: pid=7887 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=341896101 r007_lockout_until=0 r007_pid=7887 r007_wall=1790449849430
## V3
         1790449855.904  7944  7944 I R007Fault: pid=7944 thread=main op=INIT phase=CREATED script=present wall=1790449855904 elapsed=341902575
         1790449855.913  7944  7944 I R007Fault: pid=7944 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790449855913 elapsed=341902583
         1790449855.989  7944  7944 I R007Fault: pid=7944 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790449855989 elapsed=341902660
         1790449919.635  8287  8302 I R007Inspect: pid=8287 phase=INSPECT_BEGIN
         1790449919.821  8287  8302 I R007Inspect: pid=8287 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=341966305 r007_lockout_until=0 r007_pid=8287 r007_wall=1790449919635
## V4
         1790449926.298  8344  8344 I R007Fault: pid=8344 thread=main op=INIT phase=CREATED script=present wall=1790449926298 elapsed=341972968
         1790449926.306  8344  8344 I R007Fault: pid=8344 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790449926306 elapsed=341972977
         1790449926.396  8344  8344 I R007Fault: pid=8344 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790449926396 elapsed=341973066
         1790449990.832  8679  8694 I R007Inspect: pid=8679 phase=INSPECT_BEGIN
         1790449990.992  8679  8694 I R007Inspect: pid=8679 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342037503 r007_lockout_until=0 r007_pid=8679 r007_wall=1790449990832
## V5
         1790449997.409  8738  8738 I R007Fault: pid=8738 thread=main op=INIT phase=CREATED script=present wall=1790449997409 elapsed=342044079
         1790449997.417  8738  8738 I R007Fault: pid=8738 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790449997417 elapsed=342044087
         1790449997.501  8738  8738 I R007Fault: pid=8738 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790449997501 elapsed=342044172
         1790450063.065  9093  9107 I R007Inspect: pid=9093 phase=INSPECT_BEGIN
         1790450063.226  9093  9107 I R007Inspect: pid=9093 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342109735 r007_lockout_until=0 r007_pid=9093 r007_wall=1790450063064
## V8
         1790450072.444  9202  9215 I R007Inspect: pid=9202 phase=INSPECT_BEGIN
         1790450072.738  9202  9215 I R007Inspect: pid=9202 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_elapsed=342119115 r007_pid=9202 r007_read_error=java.lang.SecurityException r007_wall=1790450072444
         1790450078.956  9261  9274 I R007Inspect: pid=9261 phase=INSPECT_BEGIN
         1790450079.285  9261  9274 I R007Inspect: pid=9261 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342125627 r007_lockout_until=0 r007_pid=9261 r007_wall=1790450078956
## V7
         1790450085.024  9311  9311 I R007Fault: pid=9311 thread=main op=INIT phase=CREATED script=present wall=1790450085024 elapsed=342131695
         1790450085.031  9311  9311 I R007Fault: pid=9311 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790450085031 elapsed=342131702
         1790450085.107  9311  9311 I R007Fault: pid=9311 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790450085107 elapsed=342131778
         1790450101.336  9311  9440 I R007Fault: pid=9311 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790450101336 elapsed=342148006
         1790450101.402  9311  9440 I R007Fault: pid=9311 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790450101402 elapsed=342148073
         1790450101.403  9311  9440 I R007Fault: pid=9311 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790450101403 elapsed=342148073
         1790450108.185  9488  9502 I R007Inspect: pid=9488 phase=INSPECT_BEGIN
         1790450108.356  9488  9502 I R007Inspect: pid=9488 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=342154856 r007_lockout_until=0 r007_pid=9488 r007_wall=1790450108185
```
