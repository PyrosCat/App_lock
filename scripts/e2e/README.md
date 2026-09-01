# M7 — Device Security-Regression Harness

The security freeze for M7 (the accessibility exit). These scripts assert, mechanically and
headlessly, the gating behaviour the two Phase-3 bypasses defeated — reworked for the M7 engine: the
lock surface is a drawn **`SYSTEM_ALERT_WINDOW` overlay** (found by window title via `dumpsys window`)
and capabilities are granted with **`appops`** (Usage Access + overlay), replacing the
resumed-`LockScreenActivity` assertions and the accessibility bind. **Run before AND after any change
to the lock engine, detector, session manager, or self-gate** — green before + green after proves the
change preserved the security-critical semantics.

## Two engines (`LOCK_ENGINE`)

- **`spike`** (default, WP1): drives the throwaway WP0 spike overlay. Only **OV-4** (the overlay race)
  is behaviourally validatable here; `setup_device.sh` smoke-tests detection→overlay. The prod-path
  checks below need a PIN/relock/self-gate the spike has none of, so they self-skip.
- **`prod`** (WP2+): the real overlay lock surface. The full suite runs; WP2 repoints the two test
  constants (`POLL_SERVICE` / `OVERLAY_WINDOW_TITLE`) to production and the harness is otherwise unchanged.

## What each check defends

| Script | Defends | Engine |
|---|---|---|
| `ov4_rapid_relaunch.sh` | **R-002 / F4** rapid-relaunch race | spike + prod — a thin `am instrument` wrapper over the durable `OverlayRaceUiTest`: N bursts × K relaunches × R repeats, each scored TOP/BEHIND/ABSENT (ABSENT=0 hard, BEHIND≤2%, §11). |
| `smoke_core.sh` | Core lock → PIN → unlock | prod — overlay appears on launch; correct PIN unlocks, wrong PIN doesn't. |
| `ov3_fast_switch.sh` | IMMEDIATE relock on window-switching | prod — leaving/returning re-presents the overlay every time (×10, alternating speeds). |
| `f3_self_gate.sh` | **F3** self-gate resume (FR-108) | prod — after unlock + background + resume, the self-gate reappears. |

## How assertions work (headless, no screenshots)

- **Overlay present / on top** → `dumpsys window` (NOT `dumpsys window windows`, which omits
  `mCurrentFocus` so a present overlay reads BEHIND). TOP = the `mCurrentFocus` line carries the overlay
  title; BEHIND = present but not focused; ABSENT = not present. `lib.sh: overlay_z`, mirroring
  `OverlayRaceUiTest.zOrder()`. `is_lockscreen`/`wait_lockscreen` now mean "overlay is TOP".
- **OV-4** delegates to the instrumentation test (`am instrument`) so there is one race truth across
  emulator, real device, and FTL; counts pass as `-e ov4_*` args.
- **Self-gate vs App List (F3)** → both are the same `MainActivity`; `uiautomator dump` tests for the
  `"Open vault"` content-desc (App List only) vs `"Enter your PIN"` (re-gated).
- **PIN entry** → taps each digit by its Compose text, else by fraction (1080×2340 geometry).

## Prerequisites

- A booted device/emulator visible to `adb` (auto-detected, or `-s <serial>`).
- App APK at `app/build/outputs/apk/prod/debug/app-prod-debug.apk` (or set `APK=`); for OV-4 the
  androidTest APK at `app/build/outputs/apk/androidTest/prod/debug/app-prod-debug-androidTest.apk` (or
  `ANDROIDTEST_APK=`). Build both:
  `./gradlew :app:assembleProdDebug :app:assembleProdDebugAndroidTest`.
- `setup_device.sh` does the rest: install, `appops` grant Usage Access + overlay (works over adb on
  emulators AND real devices — no Restricted-Settings trap), keep the screen awake, provision the engine.
- Physical device: unlocked past your own keyguard and awake (`svc power stayon true`, done by setup).

## Running

```bash
# WP1 (spike): provision + OV-4 against the spike overlay
scripts/e2e/run_all.sh                          # LOCK_ENGINE defaults to spike
OV4_BURSTS=50 OV4_RELAUNCHES=20 OV4_REPEAT=5 scripts/e2e/run_all.sh   # §11 heavy counts
OV4_T_APPEAR_MS=4000 scripts/e2e/run_all.sh     # slow software-GPU (NucBox): scale the budget

# WP2+ (prod): the full suite once the real engine exists
LOCK_ENGINE=prod scripts/e2e/run_all.sh -n 2

# individual checks (device already provisioned)
scripts/e2e/setup_device.sh
scripts/e2e/ov4_rapid_relaunch.sh
```

`run_all.sh` prints a markdown result block (with the engine + OV-4 counts) to paste into a dated
report under `docs/reports/campaigns/`. Exit 0 = all green.

Overridable env: `SERIAL`, `LOCK_ENGINE`, `APP_ID` (WP4 flavor suffixes), `PIN`, `PROTECTED_PKG`,
`NEUTRAL_PKG`, `CYCLES`, `OV4_BURSTS` / `OV4_RELAUNCHES` / `OV4_REPEAT` / `OV4_T_APPEAR_MS`,
`OVERLAY_WINDOW_TITLE`, `POLL_SERVICE`, `TAP_GAP`, `APK`, `ANDROIDTEST_APK`.

## Device & OEM notes (WP0 findings)

- **Slow software-GPU emulators (NucBox) coin-flip on OV-4.** The un-fixed spike's overlay
  appear-latency straddles the 1500 ms `T_appear`; *more bursts raise* P(fail). Raise `OV4_T_APPEAR_MS`
  and read these lanes for **grep/probe portability + the old-vs-new A/B delta**, not a strict ABSENT=0.
  The clean ABSENT=0 pass is real hardware (Moto G). The warm-overlay / off-main remedy lands in
  WP2/WP3 (`docs/process/M7_PLAN.md`).
- **Emulator images:** use `aosp/default`, not `aosp-atd` — the stripped ATD images ship no launchable
  target app, so OV-4 assume-skips (the wrapper reports the skip as a failure, not a silent pass).
- **Samsung One UI** may mis-parse `mCurrentFocus`, scoring a focused overlay as a false BEHIND (WP2
  sets the production focus flags + revalidates the grep). ABSENT reads correctly on every OEM.
- **Post-(re)install detection gap:** `queryEvents` can miss the first foreground detection right after
  install (~90% on api30; steady-state fine). `setup_device.sh` runs `warm_detection` to prime past it.
- **Overlays are force-hidden over Settings** (`HIDE_NON_SYSTEM_OVERLAY`), so the target app is always a
  *normal* app (Clock / Maps), never Settings.
- **logcat under bursts:** the default buffer rotates `M7Spike` lines out; setup grows it to 16M.
- **Slow-emulator timing:** taps spaced `TAP_GAP=0.9s`; raise on slower hosts. `dismiss_anr` taps away
  "System UI isn't responding"; a persistently ANR-wedged SystemUI needs a healthier host.

## Validation status

- **M7/WP1 — PASS (fleet), closed 2026-08-31.** The reworked harness was proven against the WP0 spike
  build on both hosts: the Moto G 2025 §11 gate (OV-4 `ABSENT=0` twice over + the `neg_overlay_grant`
  negative control; `docs/reports/campaigns/2026-08-31_m7-wp1-harness_moto-g-2025.md`) and the NucBox
  emulator lanes (probe/grep portability PASS 4/4 across API 30/33/35/36, diagnostic non-gate profile;
  `docs/reports/campaigns/2026-08-31_m7-wp1-harness_nucbox-g5.md`). WP2 flips `LOCK_ENGINE=prod` and
  re-runs this matrix against the real engine.
- **M1 (history).** The pre-rework harness gated M1: baseline 2026-07-22 (NucBox, API 33, all four
  checks PASS 2/2; `docs/reports/campaigns/2026-07-22_wp2-regression-baseline_nucbox-g5.md`) plus the
  WP5/WP8 device matrix at M1 exit. Superseded by this M7 rework.

## Device notes (PIN entry, vault)

- **PIN entry** locates each digit by its Compose button text and taps the node centre —
  resolution-independent (the AVD matrix + physical devices like the Moto G 2025), with a
  fraction-based fallback (`PIN_COL_FRAC` / `PIN_ROW_FRAC` in `lib.sh`) if a device doesn't expose the
  digit text. If PIN entry ever misses, that fallback is the suspect.
- **Vault round-trip is intentionally NOT automated** — SAF/DocumentsUI automation is fragile; the
  vault encrypt/export/delete path is covered by the Phase-3 campaign record and re-checked manually
  when vault code changes.
