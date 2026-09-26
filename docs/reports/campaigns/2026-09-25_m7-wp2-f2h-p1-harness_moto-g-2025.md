# M7 WP2 F2 hardening, phase P1: R-007 device harness check on the Moto G 2025 (arm64, API 35)

- **Date / captured:** 2026-09-25. The recorded run lasted from 18:16:29Z to 18:19:34Z.
- **Author / host:** 2012 i7 dev box (Windows 10 Pro), driving the Moto G 2025 over USB adb.
- **Device:** moto g - 2025 (serial ZT4229HQ6X), Android **15**, API **35**, ABI **arm64-v8a**, build
  `V1VKS35.22-125-5` (user, release-keys), security patch 2025-08-01. The boot id was
  `bdbc452a-f16e-4bd7-828c-bccd8035840d` for the whole run. The phone was on AC power at 95 % battery and 29.0 °C,
  with about 100 GB free on `/data`.
- **Tested revision:** commit **`e521026`**, clean rebuild (`:app:clean`, `--no-build-cache`, 78 of 78 tasks
  executed). 
- **Variant:** `prodDebug` (`com.applock`, debuggable) and its androidTest APK (`com.applock.test`).
- **Toolchain:** AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0.
- **Scope:** the R-007 device harness on the Moto G (test plan phase P1). The run does not verify lockout
  behaviour.

| APK | SHA-256 (local build = installed base APK) |
|---|---|
| `app-prod-debug.apk` | `0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb` |
| `app-prod-debug-androidTest.apk` | `f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1` |
| `app-prod-release-unsigned.apk` (release scan only, not installed) | `592ed884a451083e30869de8740ace5f079dad8ca68c906fcf815ca9fce9e8b3` |

## Status

- **All 14 executed checks passed. Moto G validation remains incomplete because the required unknown-state case
  was not included.**
- The host library tests passed 41 of 41. The `prodRelease` dex contains no fault-wrapper string.
- The follow-up probes produced one new P2 finding for the lockout store and two findings for other stores.

## Context & terms

- **R-007:** the risk register entry for lockout persistence. F2 hardening treats its four residuals (/1 to /4).
- **Phase P1:** the test plan phase that validates the harness. The P1 exit needs the JVM harness, one NucBox
  emulator lane, and the Moto G.
- **Fault wrapper:** `FaultInjectingLockoutStorage`. This debug-only class wraps the real encrypted store. Before
  each operation it reads a fault script from `files/r007/faults`. It logs each phase with the tag `R007Fault`.
- **`HoldBeforeCommit`:** the wrapper holds a write before it calls the real `commit()`.
- **`CommitThenHold`:** the wrapper calls the real `commit()`, logs the result, and holds before it returns.
- **Inspector:** `LockoutStoreInspector`. This androidTest tool reads the stored pair (`failure_count`,
  `lockout_until`) in a new process and logs with the tag `R007Inspect`. `am instrument` stops the app first.
- **Abrupt kill:** `run-as com.applock kill -9 <pid>`, then a poll until `/proc/<pid>` is absent. It is not
  `am force-stop`.
- **Unknown state:** the stored pair cannot be read. The inspector then reports a read error and no count.
- **Residual /2:** the process death erases the in-memory degraded fallback lockout.

## Command and procedure

```
gradlew.bat :app:clean assembleProdDebug assembleProdDebugAndroidTest --no-build-cache
adb -s ZT4229HQ6X install -r -g app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb -s ZT4229HQ6X install -r app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk
bash scripts/r007/test_lib_r007.sh
bash scripts/r007/p1_validate.sh -s ZT4229HQ6X
gradlew.bat assembleProdRelease
```

The install kept the app data. The PIN (1234) existed before the run, so V0 did not create a PIN. The script
clears no app data and does not reinstall between an action and its inspection.

| Setting / state | Before | During | After |
|---|---|---|---|
| `settings global stay_on_while_plugged_in` | 0 | 15 (`svc power stayon true`) | 0 |
| `settings system screen_off_timeout` | 30000 ms | 30000 ms | 30000 ms |
| `shared_prefs` directory mode | 771 | 500 during V5 only | 771 |
| `files/r007` control directory | present (from the aborted attempts) | fault script and release files | removed |
| Stored lockout pair | count 0, deadline 0 | changed by the cases | count 0, deadline 0 |
| App process | none (stopped with `am force-stop`) | one for each case | none |

## Results

| Case | Action | App process | Wrapper evidence | Inspector process | Stored count | Expected |
|---|---|---|---|---|---|---|
| V0 | Launch the self-gate | 7360 | `op=INIT phase=CREATED script=present` | none | none | wrapper present |
| V1 | Correct PIN, kill | 7360 | reset write `REAL_RESULT result=true` | 7534 | **0** | 0 |
| V2 | Wrong PIN, kill | 7577 | write count 1, `result=true` | 7725 | **1** | 1 |
| V3 | Wrong PIN under `HoldBeforeCommit`, kill while held | 7781 | write count 2, `HELD`, no `REAL_RESULT` | 7931 | **1** | 1 |
| V4 | Wrong PIN under `CommitThenHold`, kill while held | 7987 | write count 2, `REAL_RESULT result=true`, `HELD` | 8306 | **2** | 2 |
| V5 | `chmod 500 shared_prefs`, wrong PIN, `chmod 771`, kill | 8493 | write count 3, `script=Normal result=false` | 8780 | **2** | 2 |
| V7 | Correct PIN, kill | 8837 | reset write `REAL_RESULT result=true` | 8997 | **0** | 0 |

**The 14 checks:** V0 checks that the wrapper is present (1 check). V1 to V5 and V7 each check the kill and the
stored count (2 checks each, 12 in total). V5 also checks that `commit()` returned false (1 check). V6 has no check
of its own, because its conditions are part of every kill check and every inspection check.

All stored deadlines were 0. All app and inspector process ids were different. Each inspection reported the boot id
of the evidence header.

### V3 and V4: old and new state

V3 and V4 start from count 1 and admit the same wrong PIN, so the in-memory count is 2 in both. Only the kill
position differs: V3 kills before the real `commit()`, V4 kills after it. The new process reads 1 after V3 and 2
after V4. V2 is the positive control: a healthy commit is durable before the kill.

### V5: real platform fault

The platform produced this fault, not the wrapper. The wrapper ran the `Normal` script and passed the real result
through (`script=Normal result=false`). The failed commit returned in 4 ms. The durable count stayed 2.

### V6: kill and inspection checks

- **Kill:** SIGKILL as the app uid, then an explicit "absent" answer for `/proc/<pid>`.
- **Inspection:** a completed `am instrument` run with a process id; `INSPECT_BEGIN` and `INSPECT_END` markers for
  that process; an end marker that repeats the reported count; no `R007Fault` line from the inspector process.

### Release build: no fault injection

The release source set defines `lockoutStorage()` as the plain encrypted adapter. The debug source set holds the
wrapper and its script parser. A byte scan of the dex files confirms the split:

| String | `prodRelease` dex (1 file) | `prodDebug` dex (16 files) |
|---|---|---|
| `R007Fault` (log tag) | 0 | 2 |
| `HoldBeforeCommit` | 0 | 2 |
| `CommitThenHold` | 0 | 1 |
| `FaultInjectingLockoutStorage` | 0 | 11 |
| `DeviceFaultScript` | 0 | 9 |
| `applock_lockout` (the real store's file name) | 1 | 1 |

R8 shrinks and renames the release classes, so a missing class name alone is weak evidence. The log tag and the
script names are string constants that the wrapper uses at run time, and the release dex contains none of them.
Both builds contain the real store's file name, which shows that the scan reads the storage code.

## Observations for P2

These are single observations from this run. P2 measures them under the protocol in §12 of the test plan.

- **The initial storage read during manager construction runs on the main thread.** All six app processes logged
  that read with `thread=main`. From `BEGIN` to `RETURNED` the reads took 73, 87, 88, 82, 82, and 85 ms. A stalled
  read would therefore stall the UI thread (test plan case R1.4).
- **Healthy write time** (`BEGIN` to `REAL_RESULT`, on the `lockout-io` thread): 67, 38, 19, and 44 ms.
- **Residual /2 appeared by a thin margin.** The V5 write failure armed the 30 s in-memory fallback. The manager
  sets that deadline at write completion, at elapsed time 252 384 485 ms or later. The V5 kill erased the fallback.
  The next process (8837) accepted the correct PIN and began the reset write at elapsed time 252 413 992 ms, at
  least 0.49 s before the old fallback would have ended. V7 does not control this timing. P2 case R2.1 tests the
  residual by design.

## Follow-up probes

After the run, four probes on the same build and boot each damaged one EncryptedSharedPreferences file while no app
process ran. Each damaged file was restored and verified byte for byte afterwards. Appendix B gives the procedure,
the changed files, and the log lines.

- **A. Lockout file, malformed XML.** The inspector read count 0 with no error, and the library wrote new keysets
  over the file. The stored count was lost. This is a new P2 finding: residual /1 covers a read that throws, but
  here the read succeeds. The inspection itself changed the store file.
- **B. Lockout file, one ciphertext character changed.** Tampered ciphertext makes the inspector report unknown
  state: a read error and no count. The file stayed unchanged.
- **C. Credentials file, malformed XML.** The app opened on "Create a PIN" instead of asking for the existing PIN.
- **D. Database-key file, malformed XML.** The app stored a new database passphrase, could not open the existing
  database, moved it aside, and created an empty one.

Probes C and D concern stores outside R-007. They are observations for the security-crypto replacement plan.

## Notes

- **Aborted attempts.** Two attempts before the recorded run stopped at V0, before any case ran. Appendix A gives
  the details.
- **Harness finding.** At `e521026`, V0 does not enforce two preconditions: the phone is unlocked, and no app
  process runs at the start. A broken precondition makes V0 fail safe, but the failure is false.
- **Limitations.**
  - The wrapper faults in V3 and V4 are barriers around the real `commit()`. They do not kill the process inside
    Android's file replacement. The timed-kill sweeps of the test plan cover that case.
  - The wrapper's `ReturnFalseBeforeCommit` script does not update the preferences cache, but a real disk failure
    does (AOSP `SharedPreferencesImpl` changes the cache before the disk write). This run did not use that script,
    and no read followed the V5 failure in the same process.
  - One device and one boot, with no reboot case. Only the self-gate caller (`MainActivity`) ran. The legacy
    `LockScreenActivity` caller did not run.
- **Evidence file.** The run wrote `build/r007-evidence/p1_validate-ZT4229HQ6X-20260925T181628Z.log` (60 lines,
  SHA-256 `fb4bfcf53350ffe114ca326c027d240fecb618f5a434ec75ccb5d7dc75264877`). The `build/` folder is not committed,
  so Appendix C holds the full content.

## Disposition

| P1 exit criterion | Moto G evidence |
|---|---|
| A healthy commit survives (positive control) | V2 |
| A deliberate old-state restart is detected (negative control) | V3 |
| The harness distinguishes old, new, and unknown state | Old and new: V3 and V4. Unknown: **not in the run** |
| The target process dies | V6, six of six kills |
| The tests cannot reset the counters themselves | The script clears no data and does not reinstall between an action and its inspection |
| Faults cannot ship enabled | Release dex scan |
| The oracle catches a stale-write mutation | JVM harness (P1a), not a device criterion |

- **P1 exit stays open** until the NucBox lane passes, a Moto G run includes the unknown-state case, and the lead
  records the exit.
- **RTM:** no change.

## Follow-ups

- Make V0 enforce its two preconditions.
- Add a P1 case for the unknown state with the probe B method, and rerun the Moto G lane at the commit that adds it.
- Run the NucBox P1 lane on the NucBox host.
- Carry into P2: the main-thread initial read (R1.4), a residual /2 case with a controlled time margin (R2.1), and
  the probe A finding with its own test ID.
- Give the probe C and D findings to the lead, to decide where the security-crypto plan and the risk register
  track them.

## Appendix A: aborted attempts

Both attempts used the same build on the same boot.

1. At 18:12:38Z, V0 failed with "the self-gate PIN prompt did not show". The phone had locked during the rebuild.
   The attempt had already started `MainActivity` behind the keyguard, and that process (6721) built the lockout
   manager.
2. At 18:14:55Z, V0 failed with "the fault wrapper did not log its creation". Process 6721 still ran, so its
   creation line was older than the logcat clear. Between the two attempts, someone entered a wrong PIN (count 1,
   at 18:14:24Z) and then the correct PIN (count 0, at 18:14:27Z) by hand in the self-gate from the first attempt.
   Both commits returned true.

After `am force-stop`, the recorded run started with no app process. The evidence files of the aborted attempts are
not retained.

## Appendix B: probe procedure and logs

**Probes A and B** changed the lockout store and then ran the inspector once. Before them the original file
(SHA-256 `f1ecec3b…2f69`, 1416 bytes) was copied. After each probe the copy was written back byte for byte. An
inspection of the restored file read count 0 and did not change the file.

**Probes C and D** changed one file and then started the app as a user would (`am start` of `MainActivity`).
Before each probe, the host saved a `tar` archive and a SHA-256 list of `shared_prefs`, `databases`, and `files`.
After each probe, the host removed the files that the start created and extracted the archive. The SHA-256 list
then matched the original list exactly. The app showed the PIN prompt, PIN 1234 opened it, and no database recovery
ran. Nothing was entered on the setup screen of probe C.

**Damage and changed files:**

- **A:** `applock_lockout.xml` held only `<map><int`. Afterwards the file held two new keysets and no values (1144
  bytes). The keyset prefixes changed from `12a9017c89dd` / `12880161d5a9` to `12a901506244` / `1288015fe0b5`.
- **B:** one base64 character in one encrypted value of `applock_lockout.xml` changed, and the XML stayed valid. No
  file changed during the inspection.
- **C:** `applock_credentials.xml` held only `<map><int`. Afterwards the file held two new keysets and no values.
- **D:** `applock_db_key.xml` held only `<map><int`. Afterwards the key file held two new keysets and one new value.
  `applock.db` was moved to `applock.db.recovery-1790365307536.bak`, and a new `applock.db` with new `-wal` and
  `-shm` files was created.

**Why probe A reads count 0:** `SharedPreferencesImpl.loadFromDisk` catches the XML parse error, logs "Cannot
read", and loads an empty map. The AOSP source handles a parse error in this way on API 27 and API 30. With no
keysets in the map, Tink 1.8.0 (`AndroidKeysetManager`) generates new keysets and writes them to the file. The read
then returns the defaults. Probes C and D follow the same library path.

**Log lines:**

```
A: W/SharedPreferencesImpl( 9732): Cannot read /data/user/0/com.applock/shared_prefs/applock_lockout.xml
A: W/SharedPreferencesImpl( 9732): org.xmlpull.v1.XmlPullParserException: Unexpected EOF (position:START_TAG <int>@1:10 ...)
B: I/AeadWrapper( 9840): ciphertext prefix matches a key, but cannot decrypt: javax.crypto.AEADBadTagException: ... BAD_DECRYPT
B: I/R007Inspect( 9840): pid=9840 phase=INSPECT_END ... r007_read_error=java.lang.SecurityException ...
C: W/SharedPreferencesImpl(16474): Cannot read /data/user/0/com.applock/shared_prefs/applock_credentials.xml
D: W/SharedPreferencesImpl(16848): Cannot read /data/user/0/com.applock/shared_prefs/applock_db_key.xml
D: D/sqlcipher(16848): ERROR CORE sqlcipher_page_cipher: hmac check failed for pgno=1
D: E/AppLockDatabase(16848): Encrypted database could not be opened or verified — recovering
D: E/AppLockDatabase(16848): android.database.sqlite.SQLiteException: file is not a database (code 26): ...
D: I/AppLockDatabase(16848): Unreadable database preserved as applock.db.recovery-1790365307536.bak
```

## Appendix C: evidence file

```
# R-007 evidence: p1_validate
# utc=2026-09-25T18:16:29Z host_rev=e521026e2838c2bff592022046d6d0571d0e818f host_changed_files=7
# serial=ZT4229HQ6X model=moto g - 2025 sdk=35 boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d
# app=com.applock apk_sha256=0975314c283a55e151686cb3ffb7dde703b700eaafe9b84de58daf011c22c4bb
# test=com.applock.test apk_sha256=f5d74438607571831adce4fd09532f23382f2c34c0344dccf0645158bf5373e1
## before-V0

## V0-V1
         1790360199.341  7360  7360 I R007Fault: pid=7360 thread=main op=INIT phase=CREATED script=present wall=1790360199341 elapsed=252246011
         1790360199.348  7360  7360 I R007Fault: pid=7360 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360199348 elapsed=252246019
         1790360199.421  7360  7360 I R007Fault: pid=7360 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790360199421 elapsed=252246091
         1790360218.467  7360  7508 I R007Fault: pid=7360 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790360218467 elapsed=252265138
         1790360218.534  7360  7508 I R007Fault: pid=7360 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790360218534 elapsed=252265205
         1790360218.535  7360  7508 I R007Fault: pid=7360 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790360218535 elapsed=252265205
         1790360224.828  7534  7549 I R007Inspect: pid=7534 phase=INSPECT_BEGIN
         1790360224.965  7534  7549 I R007Inspect: pid=7534 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=252271498 r007_lockout_until=0 r007_pid=7534 r007_wall=1790360224828
## V2
         1790360230.056  7577  7577 I R007Fault: pid=7577 thread=main op=INIT phase=CREATED script=present wall=1790360230056 elapsed=252276727
         1790360230.063  7577  7577 I R007Fault: pid=7577 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360230063 elapsed=252276734
         1790360230.150  7577  7577 I R007Fault: pid=7577 thread=main op=READ index=0 phase=RETURNED script=Normal count=0 until=0 wall=1790360230150 elapsed=252276821
         1790360246.394  7577  7699 I R007Fault: pid=7577 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=1 until=0 wall=1790360246394 elapsed=252293064
         1790360246.432  7577  7699 I R007Fault: pid=7577 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790360246432 elapsed=252293103
         1790360246.433  7577  7699 I R007Fault: pid=7577 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790360246432 elapsed=252293103
         1790360252.757  7725  7739 I R007Inspect: pid=7725 phase=INSPECT_BEGIN
         1790360252.885  7725  7739 I R007Inspect: pid=7725 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=252299428 r007_lockout_until=0 r007_pid=7725 r007_wall=1790360252757
## V3
         1790360261.805  7781  7781 I R007Fault: pid=7781 thread=main op=INIT phase=CREATED script=present wall=1790360261804 elapsed=252308475
         1790360261.813  7781  7781 I R007Fault: pid=7781 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360261813 elapsed=252308484
         1790360261.901  7781  7781 I R007Fault: pid=7781 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790360261901 elapsed=252308572
         1790360278.174  7781  7905 I R007Fault: pid=7781 thread=lockout-io op=WRITE index=0 phase=BEGIN script=HoldBeforeCommit+Normal count=2 until=0 wall=1790360278174 elapsed=252324844
         1790360278.174  7781  7905 I R007Fault: pid=7781 thread=lockout-io op=WRITE index=0 phase=HELD script=HoldBeforeCommit+Normal wall=1790360278174 elapsed=252324845
         1790360284.592  7931  7945 I R007Inspect: pid=7931 phase=INSPECT_BEGIN
         1790360284.762  7931  7945 I R007Inspect: pid=7931 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=1 r007_elapsed=252331262 r007_lockout_until=0 r007_pid=7931 r007_wall=1790360284591
## V4
         1790360291.228  7987  7987 I R007Fault: pid=7987 thread=main op=INIT phase=CREATED script=present wall=1790360291228 elapsed=252337899
         1790360291.236  7987  7987 I R007Fault: pid=7987 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360291236 elapsed=252337907
         1790360291.318  7987  7987 I R007Fault: pid=7987 thread=main op=READ index=0 phase=RETURNED script=Normal count=1 until=0 wall=1790360291318 elapsed=252337989
         1790360307.245  7987  8199 I R007Fault: pid=7987 thread=lockout-io op=WRITE index=0 phase=BEGIN script=CommitThenHold count=2 until=0 wall=1790360307245 elapsed=252353915
         1790360307.264  7987  8199 I R007Fault: pid=7987 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=CommitThenHold result=true wall=1790360307264 elapsed=252353934
         1790360307.264  7987  8199 I R007Fault: pid=7987 thread=lockout-io op=WRITE index=0 phase=HELD script=CommitThenHold wall=1790360307264 elapsed=252353934
         1790360315.325  8306  8395 I R007Inspect: pid=8306 phase=INSPECT_BEGIN
         1790360315.502  8306  8395 I R007Inspect: pid=8306 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=252361996 r007_lockout_until=0 r007_pid=8306 r007_wall=1790360315325
## V5
         1790360321.195  8493  8493 I R007Fault: pid=8493 thread=main op=INIT phase=CREATED script=present wall=1790360321195 elapsed=252367865
         1790360321.202  8493  8493 I R007Fault: pid=8493 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360321202 elapsed=252367873
         1790360321.284  8493  8493 I R007Fault: pid=8493 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790360321284 elapsed=252367954
         1790360337.810  8493  8748 I R007Fault: pid=8493 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=3 until=0 wall=1790360337810 elapsed=252384480
         1790360337.814  8493  8748 I R007Fault: pid=8493 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=false wall=1790360337814 elapsed=252384485
         1790360337.814  8493  8748 I R007Fault: pid=8493 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=false wall=1790360337814 elapsed=252384485
         1790360344.843  8780  8794 I R007Inspect: pid=8780 phase=INSPECT_BEGIN
         1790360344.989  8780  8794 I R007Inspect: pid=8780 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=2 r007_elapsed=252391514 r007_lockout_until=0 r007_pid=8780 r007_wall=1790360344843
## V7
         1790360351.040  8837  8837 I R007Fault: pid=8837 thread=main op=INIT phase=CREATED script=present wall=1790360351040 elapsed=252397710
         1790360351.048  8837  8837 I R007Fault: pid=8837 thread=main op=READ index=0 phase=BEGIN script=Normal wall=1790360351048 elapsed=252397719
         1790360351.133  8837  8837 I R007Fault: pid=8837 thread=main op=READ index=0 phase=RETURNED script=Normal count=2 until=0 wall=1790360351133 elapsed=252397804
         1790360367.321  8837  8970 I R007Fault: pid=8837 thread=lockout-io op=WRITE index=0 phase=BEGIN script=Normal count=0 until=0 wall=1790360367321 elapsed=252413992
         1790360367.366  8837  8970 I R007Fault: pid=8837 thread=lockout-io op=WRITE index=0 phase=REAL_RESULT script=Normal result=true wall=1790360367365 elapsed=252414036
         1790360367.366  8837  8970 I R007Fault: pid=8837 thread=lockout-io op=WRITE index=0 phase=RETURNED script=Normal result=true wall=1790360367366 elapsed=252414036
         1790360373.734  8997  9014 I R007Inspect: pid=8997 phase=INSPECT_BEGIN
         1790360373.889  8997  9014 I R007Inspect: pid=8997 phase=INSPECT_END r007_boot_id=bdbc452a-f16e-4bd7-828c-bccd8035840d r007_count=0 r007_elapsed=252420405 r007_lockout_until=0 r007_pid=8997 r007_wall=1790360373734
```
