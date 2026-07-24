# WP2 — Device Regression Harness

The security freeze for M1. These scripts assert, mechanically and headlessly, the
gating behaviour that the two Phase-3 security bypasses defeated. **Run them before
AND after any change to the lock engine, session manager, or self-gate** (M1 WP5
Hilt migration, WP6 package moves) — a green run before and a green run after proves
the refactor preserved the security-critical semantics.

## What each check defends

| Script | Defends | Assertion |
|---|---|---|
| `smoke_core.sh` | Core lock → PIN → unlock path | Lock screen appears on protected-app launch; correct PIN unlocks; wrong PIN doesn't. |
| `ov3_fast_switch.sh` | IMMEDIATE relock on window-switching | Leaving and returning to a protected app re-shows the lock screen every time (×10, alternating speeds) — no session leak. |
| `ov4_rapid_relaunch.sh` | **F4** fast-relaunch bypass | After 5 rapid `am start`s, the lock screen is up and the protected content is NOT foreground. |
| `f3_self_gate.sh` | **F3** self-gate resume bypass (FR-108) | After unlocking App Lock's own gate, backgrounding, and resuming, the self-gate reappears — the vault/log is not reachable without re-auth. |

## How assertions work (headless, no screenshot parsing)

- **Lock screen present** → parse `dumpsys activity activities` for the resumed activity
  and match `LockScreenActivity` (it's a distinct activity, so `dumpsys` sees it).
- **Self-gate vs App List (F3)** → the two are the *same* `MainActivity` with different
  Compose nav state, so `dumpsys` can't tell them apart. Instead we `uiautomator dump`
  and test for the `"Open vault"` content-description, which appears **only** in the
  unlocked App List. Present = unlocked (bad on resume); absent + `"Enter your PIN"` = re-gated.
- **PIN entry** → taps by *fraction* of screen size (derived from the 1080×2340 campaign
  geometry), so it's portable across the pixel_5-profile matrix AVDs. See "Device notes".

## Prerequisites

- A booted device/emulator visible to `adb` (auto-detected, or pass `-s <serial>`).
- The debug APK built at `app/build/outputs/apk/debug/app-debug.apk` (or set `APK=`).
- `setup_device.sh` provisions the rest: install, PIN `1234`, bind the a11y service,
  protect Clock.
- **Real devices ≥ Android 13 — accessibility must be granted by hand.** The adb
  `settings put` enable that works on emulators leaves the service in the "Restricted
  Settings" *malfunctioning* state that delivers no events (verified on Moto G 2025 /
  Android 15, where the "Allow restricted settings" escape hatch is also removed).
  Grant it via the phone once: **Settings → Accessibility → App Lock protection → toggle
  OFF then ON**, then run with `--skip-setup`. `setup_device.sh` detects the failure and
  prints this guidance. Emulators are unaffected (adb enable works there).
- On a physical device the screen must be **unlocked** (past your own keyguard) and awake
  (`adb shell svc power stayon true`) for the harness to drive it.

## Running

```bash
# full gate: provision, then run all four checks twice (the 2/2 exit criterion)
scripts/e2e/run_all.sh                    # auto-detect device
scripts/e2e/run_all.sh -s emulator-5554 -n 2
scripts/e2e/run_all.sh --skip-setup       # device already provisioned

# individual checks (device must already be provisioned)
scripts/e2e/setup_device.sh
scripts/e2e/smoke_core.sh
scripts/e2e/f3_self_gate.sh
```

`run_all.sh` prints a markdown result block to paste into a dated report under
`docs/reports/campaigns/` (per `docs/reports/README.md`). Exit 0 = all green.

Overridable env: `SERIAL`, `APP_ID` (WP4 flavor suffixes), `PIN`, `PROTECTED_PKG`,
`NEUTRAL_PKG`, `CYCLES`, `BURST`, `TAP_GAP`, `APK`.

## Validation status

- **Baseline achieved (2026-07-22, NucBox G5, API 33 x86_64):** `run_all.sh -n 2` → all four
  checks PASS 2/2; the security assertions (IMMEDIATE relock 10/10, F4 rapid-relaunch,
  F3/FR-108 self-gate) held in every run. WP2 exit criterion met for API 33. Report:
  `docs/reports/campaigns/2026-07-22_wp2-regression-baseline_nucbox-g5.md`.
- **Harness fixes (2026-07-22)** from three issues that baseline run surfaced on Windows/
  Git Bash paths the 2012 host never exercised (the checks passed *with* manual workarounds;
  these make setup turnkey): (1) `host_path()` converts the APK to a native Windows path for
  `adb install` (MSYS_NO_PATHCONV mangled it); (2) `open_app_list()` clears the self-gate
  before the Clock-protection locate loop; (3) wait timeouts are tunable (`FG_WAIT`,
  `LOCKSCREEN_WAIT`) and OV-3's between-cycle unlock soft-warns (the relock security
  assertion still hard-fails). **These fixes touch `setup_device.sh` and cold-start pacing,
  not the security assertions — re-run `run_all.sh -n 2` (with setup, no `--skip-setup`) on
  the NucBox to confirm turnkey operation and file a follow-up campaign note.**
- **Deterministic helpers** were separately validated live on the 2012-host Pixel_5 API 30
  emulator (screen parse → 1080×2340, `dumpsys` focus parse, Clock resolution, a11y read)
  before that host's SystemUI ANR-wedged (`bad color buffer handle`/swiftshader).
- **Coverage caveat:** the baseline exercised API 33 only. API 26/29/35 in the matrix are not
  yet run — a single-API pass does not guarantee the others (tracked on the RTM FR-108 row).

## Device notes

- **PIN entry** locates each digit by its Compose button text (`uiautomator`) and taps the
  node centre — resolution-independent, so it works on any screen (the AVD matrix and physical
  devices like the Moto G 2025) without geometry tuning. It falls back to fraction-based taps
  (the 1080×2340 pixel_5 geometry) only if a device doesn't expose the digit text. If PIN entry
  ever misses, that fallback is the suspect — adjust `PIN_COL_FRAC`/`PIN_ROW_FRAC` in `lib.sh`.
- **Slow-emulator timing**: taps are spaced `TAP_GAP=0.9s`; raise it on a slower host.
- **`dismiss_anr`** taps away the "System UI isn't responding" dialog this class of emulator
  throws; a *persistently* ANR-wedged SystemUI (as seen on the 2012 box) can't be worked
  around — use a healthier host.
- **Vault round-trip is intentionally NOT automated here.** SAF/DocumentsUI automation is
  fragile (d-pad selection, picker state) and would cause false failures in a fast regression
  loop; the vault encrypt/export/delete path is covered by the Phase-3 campaign record and is
  re-checked manually when vault code changes.
