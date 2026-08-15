# WP7 Migration Fail-Safe — On-Device Drill Runbook (NucBox handoff)

**For:** the NucBox G5 emulator host (the fleet's device-verification machine).
**From:** the 2012-box session, 2026-08-15. Shared via git (the only cross-machine channel).
**Status of the work this verifies:** code complete + local-gate green, pushed to `origin/main`.

---

## 1. Context — what already landed

M1/WP7 hardened `AppLockDatabase` migration in two pushed commits (`origin/main`):

| Commit | Fix | Risk |
|---|---|---|
| `c9ebcae` | **(b)** deleted the legacy plaintext→encrypted conversion path | R-006 **Closed** by elimination |
| `83bebcb` | **(a)** removed `fallbackToDestructiveMigration()`; added the fail-safe open + `PRAGMA quick_check` | R-004 **Open** — *awaits this drill* |

Fix (a) behaviour (`AppLockDatabase.build` → `openEncrypted`/`verifyIntegrity`/`recoverAndRebuild`):
on an open/verify failure (missing migration, schema mismatch, corrupt/unreadable file, or a
non-`ok` `quick_check`) the unreadable DB is **moved aside** as a timestamped
`applock.db.recovery-<ts>.bak` (bytes preserved — never silently wiped), a **fresh encrypted DB** is
created so detection never crash-loops, a **persistent notification** is raised, and a
`DATABASE_RECOVERED` security event is written. The PIN (EncryptedSharedPreferences) and the SQLCipher
key are separate stores and survive independently.

Local gate already green on the 2012 box: `testProdDebugUnitTest` (incl. `DatabaseRecoveryTest` +
Konsist R1–R4), `detekt`, `lintProdDebug`, `assembleProdRelease`. **What only a device can prove is
below.**

## 2. Exit criteria — what these drills close

- **Drill A passes** ⇒ RTM **FR-228 → `implemented-verified`** and risk **R-004 → Closed** (see §7).
- **Drill B passes** ⇒ confirming evidence for **R-006** (already closed by elimination) and the
  encrypted-from-birth property (FR-162/FR-164 regression touch).
- This is the last device obligation of WP7; only **WP8** (instrumentation seed + GMD matrix + the IS
  Phase-0 gate record) then remains before the M1 exit.

## 3. Prerequisites

- A booted emulator (NucBox baseline = **API 33 x86_64**; API 30/35 welcome as extra coverage).
- **A debuggable build** so `run-as` can read the app's `databases/` dir — use `devDebug`
  (`applicationId = com.applock.dev`), matching the WP5 recipe in `scripts/e2e/README.md`:

  ```bash
  ./gradlew assembleDevDebug
  ```
  APK: `app/build/outputs/apk/dev/debug/app-dev-debug.apk` · data dir: `/data/data/com.applock.dev/`
- **Accessibility / locking is NOT needed here** — these drills exercise DB open/recovery at startup,
  not the lock engine, so skip `setup_device.sh`'s a11y bind. You only need to launch the app.
- Convenience vars used below: `APP=com.applock.dev`, `APK=app/build/outputs/apk/dev/debug/app-dev-debug.apk`.

## 4. Drill A — deliberate future-schema → fail-safe (the R-004 closure drill)

Proves a schema version with no registered migration engages the fail-safe **instead of** the old
destructive wipe. This is the exact R-004 scenario.

**A.1 Seed a version-2 install with real data**
```bash
adb install -r "$APK"
adb shell monkey -p "$APP" -c android.intent.category.LAUNCHER 1   # or am start the launcher activity
# In the UI: set PIN 1234, then protect at least one app (e.g. Clock) so protected_apps + security_events have rows.
adb shell run-as "$APP" ls -l databases/                            # expect: applock.db present
```

**A.2 Build a throwaway "future schema" APK — version bump, NO migration**
- Edit `app/src/main/java/com/applock/data/AppLockDatabase.kt`: `version = 2` → `version = 3`.
- **Do not** add a `MIGRATION_2_3`. **Do not commit this edit** — it is a disposable test artifact.
```bash
./gradlew assembleDevDebug
adb install -r "$APK"        # install-over: keeps databases/ + shared_prefs/ (the key)
```

**A.3 Launch and observe**
```bash
adb logcat -c ; adb shell am start -n "$APP/com.applock.presentation.applist.MainActivity"
adb logcat -d -s AppLockDatabase:*      # capture the recovery trace
adb shell run-as "$APP" ls -l databases/
```

**A.4 Assertions (all must hold)**
- [ ] **No crash-loop** — the app is foreground/usable (PIN-entry screen: the PIN persists in prefs),
      not a repeating crash dialog.
- [ ] logcat shows `Encrypted database could not be opened or verified — recovering` **and**
      `Unreadable database preserved as applock.db.recovery-<ts>.bak`.
- [ ] `databases/` now holds **both** a fresh `applock.db` **and** `applock.db.recovery-<ts>.bak`
      (the preserved v2 DB — the previously-protected apps live here, **not wiped**).
- [ ] A `DATABASE_RECOVERED` row exists (visible in the app's security-event log, or inferred from the
      logcat trace).

**A.5 Revert the throwaway build**
```bash
git checkout -- app/src/main/java/com/applock/data/AppLockDatabase.kt   # discard the version=3 hack
```

### Drill A′ (supplementary, no source edit) — corrupt-file branch
Confirms the fail-safe also fires on an unreadable file (not only schema mismatch):
```bash
adb shell run-as "$APP" sh -c 'dd if=/dev/urandom of=databases/applock.db bs=1 count=64 conv=notrunc'
adb logcat -c ; adb shell am start -n "$APP/com.applock.presentation.applist.MainActivity"
adb logcat -d -s AppLockDatabase:*        # same recovery trace; .bak present; app usable
```

## 5. Drill B — fresh install encrypted-from-birth + stray plaintext ignored (R-006 confirmation)

**B.1 Encrypted-from-birth**
```bash
adb uninstall "$APP" ; adb install "$APK"
adb shell am start -n "$APP/com.applock.presentation.applist.MainActivity"   # set PIN → creates DB
adb shell run-as "$APP" sh -c 'head -c 16 databases/applock.db | od -An -tx1'
```
- [ ] The 16 header bytes are **not** the plaintext magic
      `53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00` ("SQLite format 3\0") — i.e. SQLCipher
      encrypted the header from creation.

**B.2 Stray plaintext is not adopted (confirmatory)**
Place any plaintext SQLite file named `applock.db` in `databases/` before first launch (e.g. push a
`sqlite3`-made file, or copy a captured Phase-1 dev DB), then launch:
- [ ] The plaintext file is **not opened as current** and **not imported** (there is no import code
      post-`c9ebcae`); the fail-safe moves it aside to `.recovery-*.bak` and a fresh **encrypted** DB
      is created. No plaintext rows surface as protected apps.

## 6. Evidence

File a dated campaign report per `docs/reports/README.md` naming:

```
docs/reports/campaigns/2026-08-1X_wp7-migration-drills_nucbox-g5.md
```

Include: emulator API level, the logcat recovery excerpts, the `run-as ls`/`od` outputs, and a
pass/fail line per assertion in §4–§5. (A `scripts/e2e/wp7_migration_drill.sh` wrapping the adb steps
above is welcome but optional — the drill is short and one-shot, unlike the OV/F3 regression loops.)

## 7. Governance follow-up (only after the drills pass — one commit)

Per GOVERNANCE §3.2/§5.2, land the evidence and the row/status changes **together** (the NucBox user
runs `git add`/commit, D4):

1. Add the campaign report (§6).
2. `docs/process/rtm/rtm.csv` — **FR-228 → `implemented-verified`**, Evidence = the report path
   (the drill is the required evidence pointer). FR-229 stays `partial` (the `quick_check` seed is by
   design; the fuller integrity framework is M7+).
3. `docs/process/RISK_REGISTER.md` — **R-004 → Closed**: flip the summary-table Status cell and the
   R-004 detail Status line, add a dated closure line citing the report and commit `83bebcb`.
4. `changelog.txt` — a WP7 device-drill entry.

Then WP7 is fully closed; proceed to **WP8**.

## 8. Notes / caveats

- `adb install -r` (Drill A) preserves `databases/` **and** `shared_prefs/` — the SQLCipher key
  persists, so the v2 DB is decryptable and the version-mismatch (not a key failure) is what triggers
  recovery. A full `uninstall` wipes the key and is a different scenario — use `-r` for Drill A.
- The current shipping engine is still accessibility-based (pre-M7); it is irrelevant to these DB
  drills. Do **not** block on a11y setup.
- If you also still owe the **WP5 device gate** (`run_all.sh -n 2` on the Hilt `devDebug` build, per
  `scripts/e2e/README.md` §Validation status), that is independent of WP7 and can be run in the same
  session on the same emulator.
