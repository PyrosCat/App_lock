# M7 WP2 F2 hardening, phase P1: R-007 device harness with settings handling on the Moto G 2025 (arm64, API 35)

- **Date / captured:** filed 2026-09-27. The recorded run lasted from 2026-09-28 00:10:50Z to 00:14:04Z (2026-09-27
  20:10 to 20:14 EDT).
- **Author / host:** 2012 i7 dev box (Windows 10 Pro), driving the Moto G 2025 over USB adb.
- **Device:** the same phone as in the [2026-09-26 report](2026-09-26_m7-wp2-f2h-p1-harness-rerun_moto-g-2025.md)
  (serial ZT4229HQ6X, Android 15, API 35, build `V1VKS35.22-125-5`). The boot id was still
  `bdbc452a-f16e-4bd7-828c-bccd8035840d`, so the phone did not restart between the two reports.
- **Tested revision:** commit **`d1887f8`** with a clean working tree (`host_changed_files=0` in the evidence header).
  This commit adds three parts to the harness: device-settings handling (record, change, restore), one exit handler
  for the cleanup and the summary, and a wake check in V0.
- **Build:** no rebuild. The installed APKs are the `f6d5883` clean build of the 2026-09-26 report, with the same
  hashes. No file under `app/` and no Gradle build file changed between `f6d5883` and `d1887f8`.
- **Variant:** `prodDebug` (`com.applock`, debuggable) and its androidTest APK (`com.applock.test`).
- **Toolchain:** AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0.
- **Scope:** the Moto G lane of the R-007 device harness (test plan phase P1) at the harness revision `d1887f8`. The
  run does not verify lockout behaviour.

| APK | SHA-256 (installed base APK = `f6d5883` build) |
|---|---|
| `app-prod-debug.apk` | `0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb` |
| `app-prod-debug-androidTest.apk` | `f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1` |

## Status

- **All 18 checks of the recorded run passed. The Moto G lane of P1 is complete at `d1887f8`.**
- The harness recorded the stay-awake and rotation settings, set them for the run, and restored them at the end.
  The comparison in the evidence file shows every value back as recorded.
- The host library tests passed 169 of 169.

## Context & terms

The terms of the 2026-09-26 report apply. This report adds these terms:

- **Settings record:** the file `/data/local/tmp/r007_settings.pending` on the phone. Before V0 changes a setting,
  it writes the original settings there and reads them back. A run that finds a record of an earlier run stops in
  V0.
- **Settings sections:** `## settings-before` in the evidence file holds the original values. `## settings-restore`
  holds each recorded value, the value after the restore, and `match=yes` or `match=no`.
- **Wake check:** after the settings change, V0 sends the wake key. It then requires the screen on, the stay-awake
  setting in effect (`mStayOn=true` in `dumpsys power`), and no keyguard.
- **Exit handler:** `r007_finish`, the EXIT trap of `p1_validate.sh`. It restores what is still pending (the V8 store
  copy and the device settings) and then prints the only summary of the run.

## Command and procedure

```
bash scripts/r007/test_lib_r007.sh
bash scripts/r007/p1_validate.sh -s ZT4229HQ6X
```

The phone was unlocked by hand just before the run. No setting was changed by hand: the harness recorded, set, and
restored the settings. The phone locks 30 s after the last user activity, so a run must start within that time.
From V0 on, the harness held the screen on.

| Setting / state | Before | During the recorded run | After |
|---|---|---|---|
| `settings global stay_on_while_plugged_in` | 0 | 7 (set by V0) | 0 |
| `settings system screen_off_timeout` | 30000 ms | 30000 ms | 30000 ms |
| `settings system accelerometer_rotation` / `user_rotation` | 1 / 1 | 0 / 0 (portrait) | 1 / 1 |
| Settings record | absent | present | absent |
| Stored lockout pair | count 0, deadline 0 | changed by the cases | count 0, deadline 0 |
| `files/r007` control directory | not checked | fault script and the V8 store copy | removed |
| App process | not checked (V0 stops one) | one for each case | none |

## Results of the recorded run

| Case | Action | App process | Wrapper or inspector evidence | Inspector process | Stored count | Expected |
|---|---|---|---|---|---|---|
| V0 | Record and set the settings, wake check, launch the self-gate | 5954 | settings-before section; `op=INIT phase=CREATED script=present` | none | none | settings recorded; wrapper present |
| V1 | Correct PIN, kill | 5954 | reset write `REAL_RESULT result=true` | 6185 | **0** | 0 |
| V2 | Wrong PIN, kill | 6246 | write count 1, `result=true` | 6423 | **1** | 1 |
| V3 | Wrong PIN under `HoldBeforeCommit`, kill while held | 6481 | write count 2, `HELD`, no `REAL_RESULT` | 6632 | **1** | 1 |
| V4 | Wrong PIN under `CommitThenHold`, kill while held | 6699 | write count 2, `REAL_RESULT result=true`, `HELD` | 6867 | **2** | 2 |
| V5 | `chmod 500 shared_prefs`, wrong PIN, `chmod 771`, kill | 6929 | write count 3, `script=Normal result=false` | 7086 | **2** | 2 |
| V8 | Save the store, tamper one encrypted value, inspect | none | `r007_read_error=java.lang.SecurityException` | 7201 | **none** | read error, no count |
| V8 | Restore the saved store, inspect | none | restored hash equals the saved hash | 7265 | **2** | 2 |
| V7 | Correct PIN, kill | 7318 | reset write `REAL_RESULT result=true` | 7488 | **0** | 0 |
| End | Exit handler restores the settings | none | settings-restore section, `match=yes` | none | none | every value as recorded |

**The 18 checks:** the 17 checks of the 2026-09-26 report, plus the settings restore at the end (1 check). The
settings change and the wake check in V0 have no check line of their own: a failure there stops the run.

### V8: unknown state

The saved store file had SHA-256 `7c145127…f48f`. After the tamper it had `c3d2c076…b422`. The inspector in process
7201 reported `java.lang.SecurityException` and no count, and the store file kept the tampered hash through the
inspection. The restore wrote the saved copy back, and the store file had the saved hash again. A new process (7265)
read count 2, as before the tamper.

### Inspection checks

Before each of the eight inspections, the device confirmed that no app process ran. Each inspection left the store
file hash unchanged. Each inspection ran in a new process, and logcat holds its begin and end markers. No
`R007Fault` line came from an inspector process. The `before-V0` section of the evidence file is empty.

### Device settings

The settings-before section records stay-awake 0, auto-rotate 1, and rotation 1. The settings-restore section shows
each of the three values back as recorded (`match=yes`). After the run, the phone reported the same values and a
screen timeout of 30000 ms. The settings record and the control directory were absent.

## Notes

- **Limitations.** The three limitations in the 2026-09-26 report also apply to this run.
- **Development checks.** Before the commit, the harness changes of `d1887f8` were checked on this phone with the
  uncommitted tree: a crash simulation with the V0 refusal and `restore_settings.sh`, an interrupt during V1, and
  failed evidence writes. The changelog entry of `d1887f8` lists them. This report records only the run at the
  committed revision.

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

- **The Moto G lane of P1 is complete at `d1887f8`.**
- **P1 exit stays open** until the NucBox lane passes and the lead records the exit.
- **RTM:** no change.

## Follow-ups

- Run the NucBox P1 lane on the NucBox host at `d1887f8`.
- The first follow-up of the 2026-09-26 report is done in `d1887f8`: the harness locks the screen to portrait and
  restores the device settings.
- Carry the P2 observations and the probe findings of the 2026-09-25 report into P2.

## Appendix A: evidence file of the recorded run

`build/r007-evidence/p1_validate-ZT4229HQ6X-20260928T001050Z.log` (74 lines, SHA-256 `2672a0570197d9260516f1d510622321a226d389c6693bc6b303dba1caf3a015`):

```
# R-007 evidence: p1_validate
# utc=2026-09-28T00:10:50Z host_rev=d1887f8d76bbb2941e68389766b79c929ea8fbf5 host_changed_files=0
# serial=ZT4229HQ6X model=moto g - 2025 sdk=35 boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d
# app=com.applock apk_sha256=0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb
# test=com.applock.test apk_sha256=f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1
## settings-before
global:stay_on_while_plugged_in=0
system:accelerometer_rotation=1
system:user_rotation=1
## before-V0

## V0-V1
         1790554265.237  5954  5954 I R007Fault: pid=5954 thread=main op=INIT phase=CREATED script=present wall=1790554265237 elapsed=446311908
         1790554265.239  5954  5954 I R007Fault: pid=5954 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554265239 elapsed=446311909
         1790554265.298  5954  5954 I R007Fault: pid=5954 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790554265298 elapsed=446311968
         1790554284.873  5954  6149 I R007Fault: pid=5954 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790554284873 elapsed=446331544
         1790554284.893  5954  6149 I R007Fault: pid=5954 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790554284893 elapsed=446331563
         1790554284.893  5954  6149 I R007Fault: pid=5954 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790554284893 elapsed=446331564
         1790554290.104  6185  6210 I R007Inspect: pid=6185 phase=INSPECT_BEGIN
         1790554290.185  6185  6210 I R007Inspect: pid=6185 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=446336775 r007_lockout_until=0 r007_pid=6185 r007_wall=1790554290104
## V2
         1790554295.093  6246  6246 I R007Fault: pid=6246 thread=main op=INIT phase=CREATED script=present wall=1790554295093 elapsed=446341764
         1790554295.095  6246  6246 I R007Fault: pid=6246 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554295095 elapsed=446341765
         1790554295.177  6246  6246 I R007Fault: pid=6246 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790554295177 elapsed=446341848
         1790554310.807  6246  6392 I R007Fault: pid=6246 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=1 until=0 wall=1790554310807 elapsed=446357477
         1790554310.825  6246  6392 I R007Fault: pid=6246 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790554310825 elapsed=446357496
         1790554310.825  6246  6392 I R007Fault: pid=6246 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790554310825 elapsed=446357496
         1790554316.100  6423  6437 I R007Inspect: pid=6423 phase=INSPECT_BEGIN
         1790554316.268  6423  6437 I R007Inspect: pid=6423 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=446362771 r007_lockout_until=0 r007_pid=6423 r007_wall=1790554316100
## V3
         1790554321.665  6481  6481 I R007Fault: pid=6481 thread=main op=INIT phase=CREATED script=present wall=1790554321665 elapsed=446368335
         1790554321.667  6481  6481 I R007Fault: pid=6481 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554321667 elapsed=446368338
         1790554321.750  6481  6481 I R007Fault: pid=6481 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790554321750 elapsed=446368420
         1790554337.573  6481  6601 I R007Fault: pid=6481 thread=lockout-io op=WRITE index=0 phase=BEGIN script=HoldBeforeCommit+Normal count=2 until=0 wall=1790554337573 elapsed=446384244
         1790554337.573  6481  6601 I R007Fault: pid=6481 thread=lockout-io op=WRITE index=0 phase=HELD script=HoldBeforeCommit+Normal wall=1790554337573 elapsed=446384244
         1790554343.142  6632  6657 I R007Inspect: pid=6632 phase=INSPECT_BEGIN
         1790554343.233  6632  6657 I R007Inspect: pid=6632 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=446389812 r007_lockout_until=0 r007_pid=6632 r007_wall=1790554343142
## V4
         1790554349.352  6699  6699 I R007Fault: pid=6699 thread=main op=INIT phase=CREATED script=present wall=1790554349352 elapsed=446396022
         1790554349.354  6699  6699 I R007Fault: pid=6699 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554349354 elapsed=446396025
         1790554349.411  6699  6699 I R007Fault: pid=6699 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790554349411 elapsed=446396082
         1790554365.665  6699  6831 I R007Fault: pid=6699 thread=lockout-io op=WRITE index=0 phase=BEGIN script=CommitThenHold count=2 until=0 wall=1790554365665 elapsed=446412335
         1790554365.691  6699  6831 I R007Fault: pid=6699 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=CommitThenHold result=true wall=1790554365691 elapsed=446412361
         1790554365.691  6699  6831 I R007Fault: pid=6699 thread=lockout-io op=WRITE index=0 phase=HELD script=CommitThenHold wall=1790554365691 elapsed=446412361
         1790554371.518  6867  6882 I R007Inspect: pid=6867 phase=INSPECT_BEGIN
         1790554371.599  6867  6882 I R007Inspect: pid=6867 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=446418189 r007_lockout_until=0 r007_pid=6867 r007_wall=1790554371518
## V5
         1790554377.370  6929  6929 I R007Fault: pid=6929 thread=main op=INIT phase=CREATED script=present wall=1790554377370 elapsed=446424040
         1790554377.372  6929  6929 I R007Fault: pid=6929 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554377372 elapsed=446424042
         1790554377.434  6929  6929 I R007Fault: pid=6929 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790554377434 elapsed=446424105
         1790554393.740  6929  7051 I R007Fault: pid=6929 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=3 until=0 wall=1790554393738 elapsed=446440409
         1790554393.748  6929  7051 I R007Fault: pid=6929 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=false wall=1790554393748 elapsed=446440419
         1790554393.748  6929  7051 I R007Fault: pid=6929 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=false wall=1790554393748 elapsed=446440419
         1790554399.813  7086  7101 I R007Inspect: pid=7086 phase=INSPECT_BEGIN
         1790554399.956  7086  7101 I R007Inspect: pid=7086 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=446446484 r007_lockout_until=0 r007_pid=7086 r007_wall=1790554399813
## V8
         1790554408.383  7201  7216 I R007Inspect: pid=7201 phase=INSPECT_BEGIN
         1790554408.675  7201  7216 I R007Inspect: pid=7201 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_elapsed=446455053 r007_pid=7201 r007_read_error=java.lang.SecurityException r007_wall=1790554408382
         1790554414.398  7265  7278 I R007Inspect: pid=7265 phase=INSPECT_BEGIN
         1790554414.690  7265  7278 I R007Inspect: pid=7265 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=446461068 r007_lockout_until=0 r007_pid=7265 r007_wall=1790554414398
## V7
         1790554420.242  7318  7318 I R007Fault: pid=7318 thread=main op=INIT phase=CREATED script=present wall=1790554420242 elapsed=446466913
         1790554420.244  7318  7318 I R007Fault: pid=7318 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790554420244 elapsed=446466915
         1790554420.333  7318  7318 I R007Fault: pid=7318 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790554420332 elapsed=446467003
         1790554436.129  7318  7454 I R007Fault: pid=7318 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790554436128 elapsed=446482799
         1790554436.148  7318  7454 I R007Fault: pid=7318 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790554436148 elapsed=446482819
         1790554436.148  7318  7454 I R007Fault: pid=7318 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790554436148 elapsed=446482819
         1790554441.519  7488  7502 I R007Inspect: pid=7488 phase=INSPECT_BEGIN
         1790554441.600  7488  7502 I R007Inspect: pid=7488 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=446488189 r007_lockout_until=0 r007_pid=7488 r007_wall=1790554441518
## settings-restore
global:stay_on_while_plugged_in recorded=0 now=0
system:accelerometer_rotation recorded=1 now=1
system:user_rotation recorded=1 now=1
match=yes
```
