# WP6 Package-Realignment Device Exit — Moto G 2025 / Android 15 (real hardware)

**Date:** 2026-08-11 (filed; captured same day)
**Author / host:** 2012 i7-3520M dev box driving a physical **Moto G 2025** over USB adb.
**Device under test:** Moto G 2025 (`ZT4229HQ6X`) — **Android 15 (API 35)**, `arm64-v8a`, **720×1604**.
**Builds under test:** WP4 **`dev`** flavor (`applicationId com.applock.dev`), both debug-signed:
- **pre-WP6** `app-dev-debug.apk` built from **`0eb0aad`** (last commit before the package move —
  old layout: `MainActivity` at `com.applock.ui`, `LockScreenActivity` at `com.applock.authentication.ui`).
- **WP6** `app-dev-debug.apk` built from **`830685b`** (WP6a moves + WP6b Konsist R2/R4). Working tree clean.

**Verifies (the two WP6 exit criteria):**
1. **ADR-018 FQCN pinning** — an upgrade-install of the WP6 build over a pre-WP6 install leaves the
   accessibility grant **bound and delivering** (and the encrypted DB intact), because the two
   pinned component FQCNs did not move.
2. **Gating regression** — the security-critical semantics frozen by the WP2 harness (core
   lock→PIN→unlock, OV-3 IMMEDIATE relock, OV-4 fast-relaunch defense, F3/FR-108 self-gate) survive
   the package realignment.

**Verdict:** ✅ **PASS — both halves green.** ADR-018 pin held (a11y survived the upgrade with no
re-grant); harness **4/4 checks PASS 2/2** on the upgraded WP6 build. Matches the pre-move all-green
baselines (WP5 real-hardware 2026-08-08; WP2 2026-07-23) → **no gating regression from the move**.

---

## 1. ADR-018 upgrade-install drill — accessibility pin

### 1a. Static confirmation (aapt, both APKs)

| Component | pre-WP6 (`0eb0aad`) | WP6 (`830685b`) | |
|---|---|---|---|
| `applicationId` | `com.applock.dev` | `com.applock.dev` | same |
| launchable `MainActivity` | `com.applock.ui.MainActivity` | `com.applock.presentation.applist.MainActivity` | **moved** ✔ |
| a11y `AppDetectionService` | `com.applock.applocker.service.AppDetectionService` | *(identical)* | **pinned** ✔ |
| device-admin `UninstallProtectionReceiver` | `com.applock.applocker.admin.UninstallProtectionReceiver` | *(identical)* | **pinned** ✔ |

The refactor genuinely moved a launcher activity while both externally-persisted FQCNs are
byte-identical — the exact hazard ADR-018 addresses.

### 1b. Runtime (upgrade-install, nothing re-granted)

Provisioned the **pre-WP6** build (`setup_device.sh`: PIN `1234`, Clock protected). Captured state,
then `adb install -r` the **WP6** build over it (no uninstall) and re-checked — **no re-grant, no
re-provision**:

| Signal | Before (pre-WP6) | After upgrade (WP6) |
|---|---|---|
| launcher resolves to | `…ui.MainActivity` | `…presentation.applist.MainActivity` (WP6 build live) |
| `enabled_accessibility_services` | `com.applock.dev/…applocker.service.AppDetectionService` | **unchanged** |
| `accessibility_enabled` | `1` | `1` |
| launch Clock → top activity | `…authentication.ui.LockScreenActivity` (LOCKED) | `…presentation.authentication.LockScreenActivity` (**LOCKED**) |

The top-activity FQCN changing (`authentication.ui` → `presentation.authentication`) confirms the
**WP6** binary is servicing the lock; it locked **without any re-grant** → the persisted a11y grant
stayed valid across the move because `AppDetectionService` kept its FQCN. Clock still being
protected (and the PIN still unlocking, §2) shows the SQLCipher DB survived the upgrade. **ADR-018
accessibility pin: verified at runtime.**

### 1c. Device-admin pin — static only this run

The device-admin `UninstallProtectionReceiver` FQCN is **identical** across both builds (§1a), so
its stored `ComponentName` cannot dangle on upgrade — the same mechanism just proven at runtime for
the a11y grant. A full runtime drill (enable the opt-in uninstall protection on the pre-WP6 build,
upgrade, re-check `dumpsys device_policy`) was **not run** this session to avoid the device-admin
deactivation friction; it is a low-stakes follow-up (uninstall protection is opt-in / default-off;
failure mode is a stranded admin, not silent protection loss). Deferred, not blocking.

## 2. Gating regression harness

`APP_ID=com.applock.dev PROTECTED_PKG=com.google.android.deskclock scripts/e2e/run_all.sh
--skip-setup -n 2` on the **upgraded WP6 install**:

| Check | Run 1 | Run 2 | Assertion |
|---|---|---|---|
| `smoke_core` | ✅ | ✅ | lock on protected-app launch; correct PIN (`1234`) unlocks; wrong PIN stays locked |
| `ov3_fast_switch` | ✅ | ✅ | IMMEDIATE relock on every return (no session leak) |
| `ov4_rapid_relaunch` (F4) | ✅ | ✅ | lock screen up after 5 rapid `am start`s; protected content not foreground |
| `f3_self_gate` (F3 / FR-108) | ✅ | ✅ | self-gate re-appears on resume; App List unreachable without PIN |

**VERDICT: PASS (2/2 all green).** OV-4 was clean on both runs — the R-002 rapid-relaunch flake
(an emulator-timing artifact) did not manifest on real hardware, consistent with prior Moto G runs.
`smoke_core` passing re-confirms the PIN survived the upgrade (data integrity).

## 3. Harness fix required to run against the WP6 build

`launch_main()` (`scripts/e2e/lib.sh`) hard-launched `am start -n <APP_ID>/com.applock.ui.MainActivity`
— stale after WP6 moved `MainActivity` to `presentation/applist`. Repointed it to the existing
dynamic resolver (`launch_pkg "$APP_ID"`, which resolves the launcher activity via
`cmd package resolve-activity`), so the harness now works against **both** the pre-WP6 and WP6 builds
without a hardcoded FQCN. `MAIN_ACTIVITY` corrected to the new FQCN (informational). This was a WP6
follow-up miss (the harness references a moved, non-pinned class); it ships with this report.

## 4. Accessibility grant provenance (unchanged from 2026-08-08)

Setup enabled a11y via adb; it came up **delivering events** and gated correctly with **no
Settings-UI toggle this session** — same as the 2026-08-08 WP5 run. This device retains per-app
trust for `com.applock.dev` from a manual grant in an earlier session, so the adb enable delivers
without re-tripping Restricted Settings. This does **not** refute the 2026-07-23 "manual grant
required on a clean Android-15 device" finding. Crucially, the **upgrade-survival result (§1b) is
independent of grant provenance**: had the pinned FQCN changed, the persisted grant string would
resolve to a non-existent service and stop delivering regardless of per-app trust.

## 5. What this closes

- **WP6 device exit criteria: MET** — (a) ADR-018 accessibility pin verified at runtime across an
  upgrade-install; (b) gating regression green 4/4, 2/2 on the migrated build. With WP6a/WP6b
  already committed + pushed (`origin/main` = `830685b`) and this evidence, **WP6 is closed → WP7
  unblocked** (governance: nothing after WP2 proceeds while the gate is red — it is green here).
- **Owed / deferred:** device-admin pin runtime drill (§1c, low-stakes); RTM batch to the WP8 IS
  Phase-0 gate per M1_PLAN §5 (this report is the evidence pointer); ADR-018 gains a WP6-verified
  implementation-status note (already added in `830685b`).

## Immutability

Immutable evidence per GOVERNANCE §2/§5.1. A later package/layer/lock-engine change re-runs the
relevant gate and files a new dated campaign report; this one is not edited.
