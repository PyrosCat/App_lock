# WP7 Migration Fail-Safe — On-Device Drill Results (NucBox G5)

**Date:** 2026-08-16 (filed)
**Author / host:** NucBox G5 — GMKtec, Windows 11 Pro, Intel N-series x86_64 (on-host assistant)
**Device under test:** AVD `matrix_api33` — `google_apis;x86_64`, pixel_5 profile, **API 33** (`ro.build.version.sdk=33`, `ro.product.cpu.abi=x86_64`, `dalvik.vm.heapgrowthlimit=192m`), headless under WHPX.
**Produced against:** commit `0063e7d`. App under test: **`app-dev-debug.apk` v0.1.0, variant `devDebug`** (`applicationId com.applock.dev`), built from the clean tree at `0063e7d` (the disposable schema-bump for Drill A was built on top, then reverted — see §Drill A). The fail-safe code under test is **WP7(a) `83bebcb`** (`data/AppLockDatabase.kt` → `build`/`openEncrypted`/`verifyIntegrity`/`recoverAndRebuild`, `DatabaseRecovery.moveAside`); the removed plaintext-import path is **WP7(b) `c9ebcae`**.
**Runbook executed:** `docs/testing/WP7_MIGRATION_DRILLS.md` (the 2012-box → NucBox handoff).
**Verifies:** **FR-228** (migration fail-safe / recovery, no silent wipe) and **FR-229** seed (`PRAGMA quick_check`); risk **R-004** (destructive-fallback data loss) closure; confirming evidence for **R-006** (legacy plaintext path) and the encrypted-from-birth property (**FR-162/FR-164** regression touch).
**Verdict:** ✅ **All four drills PASS.** The deliberate schema-mismatch, a corrupt file, and a stray plaintext database each engage the fail-safe: the unreadable DB is preserved as a timestamped `.recovery-*.bak`, a fresh **encrypted** database is created, and the app stays usable (no crash-loop). Fresh installs are encrypted from birth. No plaintext database is adopted or imported.

---

## 1. Summary

| Drill | Scenario | Result | Closes / confirms |
|---|---|---|---|
| **A** | Future schema (v3) with no `MIGRATION_2_3`, installed over a used v2 install | ✅ PASS | **R-004** (was the destructive-wipe scenario) → recommend Closed; **FR-228** verified |
| **A′** | Corrupt file (64 random bytes over the SQLCipher header) | ✅ PASS | Fail-safe also fires on an unreadable file, not only a schema mismatch |
| **B.1** | Fresh install, first launch | ✅ PASS | Encrypted from birth (**FR-162/FR-164**) |
| **B.2** | Stray **plaintext** `applock.db` present before first launch | ✅ PASS | **R-006** — no plaintext-import path; stray file preserved aside, not adopted |

Emulator prep: `matrix_api33` cold-booted headless; animations disabled; API 33 carries a normal 192 MiB heap, so the API-29 Argon2/Zygote heap bug (see `2026-08-09_wp5-matrix_nucbox-g5.md` §3) is not in play here.

## 2. Drill A — future-schema → fail-safe (the R-004 closure drill)

**Seed (v2, real data).** `devDebug` installed; PIN `1234` set; **Clock protected** via the app list (`scripts/e2e/setup_device.sh`, `APP_ID=com.applock.dev`), so `protected_apps` + `security_events` carry rows. Pre-drill `databases/`:

```
applock.db      4096   md5 182fa8db8c2c7ea78e42b31bb145c1fd
applock.db-wal 74192   ← the seeded rows live here (not yet checkpointed)
applock.db-shm 32768
applock.db header: ca a2 ea c4 ae cb bd f5 81 02 86 ba 0f d0 34 3d   (encrypted, not SQLite magic)
```

**Throwaway "future schema".** `AppLockDatabase.kt` `version = 2 → 3`, **no** `MIGRATION_2_3`, **not committed**; `assembleDevDebug`; `adb install -r` over the seeded install (preserves `databases/` + the key in `shared_prefs/`).

**Launch trace** (`am start …/com.applock.presentation.applist.MainActivity`; `logcat -s AppLockDatabase`):

```
E/AppLockDatabase: Encrypted database could not be opened or verified — recovering
E/AppLockDatabase: java.lang.IllegalStateException: A migration from 2 to 3 was required but not
                   found. Please provide the necessary Migration path via
                   RoomDatabase.Builder.addMigration(Migration ...) or allow for destructive
                   migrations via one of the RoomDatabase.Builder.fallbackToDestructiveMigration* …
        at androidx.room.RoomOpenHelper.onUpgrade(RoomOpenHelper.kt:109)
        at com.applock.data.AppLockDatabase$Companion.verifyIntegrity(AppLockDatabase.kt:117)
        at com.applock.data.AppLockDatabase$Companion.build(AppLockDatabase.kt:93)
        at com.applock.di.AppModule.provideDatabase(AppModule.kt:53)
        at com.applock.AppLockApplication.onCreate(AppLockApplication.kt:17)
I/AppLockDatabase: Unreadable database preserved as applock.db.recovery-1786863572717.bak
```

**Post-recovery `databases/`:**

```
applock.db                          4096   header 0c ec e9 97 7b 7a df f8 …   (fresh, encrypted)
applock.db.recovery-1786863572717.bak 32768 header ca a2 ea c4 ae cb bd f5 …   (== the pre-drill baseline header)
```

**Assertions (runbook §A.4) — all hold:**

- [x] **No crash-loop** — process pid stable; top activity `com.applock.dev/…presentation.applist.MainActivity`; UI shows **"Enter your PIN"** (PIN persisted in EncryptedSharedPreferences, a store independent of the DB); no `FATAL`/`AndroidRuntime`.
- [x] logcat shows *"Encrypted database could not be opened or verified — recovering"* **and** *"Unreadable database preserved as applock.db.recovery-\<ts\>.bak"*.
- [x] `databases/` holds **both** a fresh `applock.db` **and** `applock.db.recovery-1786863572717.bak`. The `.bak`'s 16-byte header is **byte-identical to the pre-drill baseline** (`ca a2 ea c4 …`) — i.e. the `.bak` *is* the original seeded v2 database, preserved, **not wiped**.
- [x] `DATABASE_RECOVERED` audit written — inferred from the clean recovery (the fresh-DB insert path in `recordRecoveryEvent` ran with no *"Could not record database-recovery event"* error). Direct read isn't possible (the DB is SQLCipher-encrypted and there is no in-app security-event viewer); inference from the trace is the runbook-accepted method.

**WAL-checkpoint note (why the `.bak` is 32 KiB, not 4 KiB).** The seeded rows were still in the 74 KiB `-wal` at drill start. On the failed open, `build()`'s `catch` calls `room.close()`, whose last-connection checkpoint flushes the committed WAL frames into the main `applock.db` **before** `moveAside` renames it. The `.bak` therefore captured the seeded data (8 pages), and `moveAside` dropped the now-redundant `-wal`/`-shm`. The identical header confirms preservation. (Had the close not checkpointed, only the 4 KiB main file would have been preserved — worth knowing, but it did checkpoint here.)

## 3. Drill A′ — corrupt-file branch

`am force-stop`; overwrite the first 64 bytes of the (fresh, encrypted) `applock.db` header with random bytes (`run-as … dd if=/dev/urandom of=databases/applock.db bs=64 count=1 conv=notrunc`; header `0c ec e9 97 … → 7b 08 f3 08 …`); relaunch.

```
E/AppLockDatabase: Encrypted database could not be opened or verified — recovering
I/AppLockDatabase: Unreadable database preserved as applock.db.recovery-1786863750568.bak
databases/: applock.db (4096, fresh) + applock.db.recovery-1786863572717.bak + applock.db.recovery-1786863750568.bak
```

- [x] Same recovery trace on an **unreadable** file (corrupt SQLCipher header → decrypt/verify fails), not only on a schema mismatch.
- [x] The `.bak` name is **timestamped**, so this second recovery did **not** clobber the Drill-A `.bak` — both preserved copies coexist (R-004: a repeated recovery never overwrites an earlier preserved copy).
- [x] App usable (pid stable, PIN gate), no `FATAL`.

> Operator note: the runbook's `dd … bs=1 count=64` read **0 bytes** from `/dev/urandom` under `run-as` on this API-33 image (`0+0 records`), silently leaving the file intact. `bs=64 count=1` (a single 64-byte block read) works. Recommend the runbook use the block form.

## 4. Drill B — encrypted-from-birth + stray plaintext ignored (R-006)

**B.1 — encrypted from birth.** `adb uninstall` (wipes data + key) → fresh `adb install` → launch. `applock.db` is created at `AppLockApplication.onCreate` (before any PIN is set):

```
applock.db header: 39 6b 4a dd 96 ad f6 af d5 95 79 b3 a4 e1 90 45
SQLite plaintext magic (would-be):  53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00
```

- [x] The 16 header bytes are **not** `"SQLite format 3\0"` — SQLCipher encrypted the header from creation (FR-162/FR-164). Encrypted before the PIN even exists.

**B.2 — stray plaintext not adopted.** Fresh install; before first launch, plant a **plaintext** SQLite DB as `databases/applock.db` (built on-device with `/system/bin/sqlite3`) carrying a realistic `protected_apps` row:

```
planted file: 16384 bytes, header 53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00  ("SQLite format 3\0")
planted row : protected_apps.packageName = 'com.stray.plaintext.NOTADOPTED'
```

Launch:

```
E/AppLockDatabase: Encrypted database could not be opened or verified — recovering
I/AppLockDatabase: Unreadable database preserved as applock.db.recovery-1786863946097.bak
databases/:
  applock.db                          4096   header e0 86 5f 4d bb ca 3f 93 …   (fresh, ENCRYPTED)
  applock.db.recovery-1786863946097.bak 16384 header 53 51 4c 69 74 65 … 33 00   (the stray PLAINTEXT file)
```

- [x] The plaintext file is **not opened as current** and **not imported** (no import code exists post-`c9ebcae`): it is moved aside to `.recovery-*.bak` and a fresh **encrypted** DB is created.
- [x] **No plaintext row surfaces as a protected app.** `SELECT packageName FROM protected_apps` reads `com.stray.plaintext.NOTADOPTED` from the **`.bak`** but the live `applock.db` is a fresh, empty, encrypted store — the stray package was preserved, never adopted.

## 5. Exit criteria (runbook §2)

- **Drill A passes** ⇒ RTM **FR-228 → `implemented-verified`**, risk **R-004 → Closed** (evidence = this report). Staged for commit per §7 of the runbook (D4: the NucBox user commits).
- **Drill B passes** ⇒ confirming evidence for **R-006** (already Closed by elimination) and encrypted-from-birth (FR-162/FR-164).
- This was the last device obligation of WP7. Only **WP8** (instrumentation seed + GMD matrix + IS Phase-0 gate record) then remains before the M1 exit.

## 6. Scope & caveats

- Single level (**API 33**). FR-228's logic is device-independent (JVM `DatabaseRecoveryTest` covers the decision logic; this drill covers the on-device open/recover/notify orchestration), so one representative level satisfies the runbook; API 30/35 are optional extra coverage, not run here.
- `DATABASE_RECOVERED` and the recovery **notification** were inferred from the trace, not read directly (encrypted DB; on API 33 the persistent notice also needs runtime `POST_NOTIFICATIONS`, not granted on these fresh installs, so `notifyRecovery` self-skips by design).
- The schema-bump used for Drill A was a disposable working-tree edit (`version = 3`, no migration); it was reverted immediately after the drill (`git checkout -- app/src/main/java/com/applock/data/AppLockDatabase.kt`) and **never committed**. The tree is clean at `version = 2`.

## 7. Supersession

On-device WP7 fail-safe evidence for API 33 on this host. Immutable; a later change to `AppLockDatabase` migration/recovery re-runs these drills and files a new dated campaign report.
