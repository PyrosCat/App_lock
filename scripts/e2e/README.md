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

## Validation status (2026-07-21)

- **Deterministic helpers — validated live** on the 2012-host Pixel_5 API 30 emulator:
  device/boot detection, screen-size parse (→ exactly 1080×2340), `dumpsys` resumed-activity
  parsing (→ correctly identified the launcher and `com.applock/.ui.MainActivity`), Clock
  resolution (→ `com.google.android.deskclock`), and the a11y-binding read.
- **Interactive checks — baseline run PENDING on a healthy host.** The 2012 emulator's
  SystemUI ANR-wedged this session (`bad color buffer handle` / swiftshader on that ancient
  GPU — the documented fragility), so the tap/lock/self-gate flows could not be exercised
  end-to-end here. Run the first baseline (`run_all.sh -n 2`) on the **NucBox G5** (healthy
  WHPX emulators — its reason for joining the fleet) and file the result in
  `docs/reports/campaigns/`. That baseline run is the WP2 exit criterion.

## Device notes

- **PIN-pad geometry** is fraction-based and exact for the pixel_5-profile matrix AVDs
  (1080×2340). On a differently-shaped screen (e.g. the Moto G 2025), the fractions are
  approximate; if a PIN tap misses, adjust `PIN_COL_FRAC`/`PIN_ROW_FRAC` in `lib.sh` or
  add a per-device profile. A future hardening: locate PIN digits via `uiautomator` semantics.
- **Slow-emulator timing**: taps are spaced `TAP_GAP=0.9s`; raise it on a slower host.
- **`dismiss_anr`** taps away the "System UI isn't responding" dialog this class of emulator
  throws; a *persistently* ANR-wedged SystemUI (as seen on the 2012 box) can't be worked
  around — use a healthier host.
- **Vault round-trip is intentionally NOT automated here.** SAF/DocumentsUI automation is
  fragile (d-pad selection, picker state) and would cause false failures in a fast regression
  loop; the vault encrypt/export/delete path is covered by the Phase-3 campaign record and is
  re-checked manually when vault code changes.
