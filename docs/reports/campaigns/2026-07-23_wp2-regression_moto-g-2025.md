# WP2 Regression Harness — Moto G 2025 / Android 15 (real hardware)

**Date:** 2026-07-23 (filed)
**Author / host:** 2012 i7-3520M dev box driving a physical **Moto G 2025** over USB adb
(the phone runs Android natively — the 2012 host's emulator problems do not apply).
**Device under test:** Moto G 2025 (`ZT4229HQ6X`) — **Android 15 (API 35)**, `arm64-v8a`,
**720×1604**. First real-hardware, ARM, and non-1080×2340 data point in the fleet.
**Produced against:** repo HEAD `00eaad9`; app under test is the debug APK (Phase-3 app code —
last Kotlin change `1dc9e25`; `8ca489a` only added a lint block). Harness run from the
**working tree** (uncommitted WP2 fixes described in §3), not yet committed.
**Verifies:** WP2 security-freeze gating on real Android 15 hardware — the two Phase-3 bypasses
(F3 self-gate/FR-108; F4 fast-relaunch) plus core lock→PIN→unlock and IMMEDIATE relock.
**Verdict:** ✅ **PASS — all four checks green** (see §1). Plus one significant environment
finding (§2) and harness fixes required to run on this hardware (§3).

---

## 1. Results (`TAP_GAP=1.0–1.2`, checks run individually)

| Check | Result | Notes |
|---|---|---|
| `smoke_core` | ✅ 3/3 PASS | lock on launch; correct PIN unlocks; wrong PIN stays locked |
| `ov4_rapid_relaunch` (F4 defense) | ✅ PASS | lock screen up after 5 rapid `am start`s; content not exposed |
| `f3_self_gate` (F3 / FR-108 defense) | ✅ PASS | self-gate re-appears on resume; App List unreachable without PIN |
| `ov3_fast_switch` | ✅ 10/10 relock PASS | passed warm; first (cold) attempt flaked its *initial* scaffolding unlock only |

`run_all.sh -n 2` in one shot exceeds a ~7 min budget on this device (OV-3 × many PIN entries);
checks were run individually. The relock **security** assertion held on every OV-3 cycle.

## 2. Significant finding — accessibility "Restricted Settings" on Android 13+ (real devices)

The app **does gate correctly on Android 15**, but only after accessibility is granted through
the **real Settings UI**. Enabling the service via `adb settings put`
(`enabled_accessibility_services`) — which works on emulators (API 30/33, NucBox) — left it in a
**"malfunctioning"** state on this device: `dumpsys accessibility` showed it *Bound*, the app
process was running, Clock was protected, yet **no `AppLockEngine` logs fired** and Clock did not
lock — the OS silently withholds events (Android 13+ Restricted Settings hardening for sideloaded
accessibility services). Under App Info the service read *"This service is malfunctioning."*

Toggling the service **off then on in Settings → Accessibility** cleared it, after which all four
checks passed. On this Moto G 2025 / Android 15 the usual **"Allow restricted settings" three-dot
menu is absent** (stricter security mode), so the off/on toggle is the only grant path — there is
**no adb workaround** on this hardware.

Implications:
- **App:** not a defect. The in-app onboarding already deep-links to Accessibility settings; real
  users grant it there. Confirmed working on Android 15.
- **Harness:** `rebind_a11y()` (adb) is **emulator-only reliable**. On real devices ≥ API 13 the
  operator must grant accessibility by hand before `--skip-setup`. Captured in `lib.sh`
  (`a11y_working()` + note), `setup_device.sh` (failure now prints the grant guidance), and the
  harness README prerequisites.
- **RTM:** re-verifies FR-108 (and the core lock-engine gating) on API 35 / real hardware, with
  the manual-a11y caveat noted.

## 3. Harness fixes required for this hardware (working tree; uncommitted)

Real Android 15 / ARM / 720×1604 surfaced issues the emulators never did — all fixed:

1. **Focus parser (`top_component`)** matched only `mResumedActivity` (API 30/33); Android 15
   prints `topResumedActivity=` / `ResumedActivity:`. Broadened to match all forms and exclude
   `*Last*`/`*Paused*` history entries. (Verified: home→launcher, launch→DeskClock.)
2. **PIN-pad taps** — the fraction-based geometry landed correctly on 720×1604 (a different
   aspect ratio than the 1080×2340 emulators); **no change needed**, assumption validated.
3. **Clock-row locate** — a single big swipe *overshot* Clock on this device's long app list
   (jumped Chrome→Games). Now scrolls to top, then steps down in small increments
   (`PROTECTED_LABEL`, configurable). The Compose `Switch` is exposed as a `checkable`
   `android.view.View` at the row's right edge (not `android.widget.Switch`) — the geometric
   right-edge tap works; toggle confirmed via `checked="true"`.
4. **a11y handling** (§2): `a11y_working()` helper + `setup_device.sh` failure guidance +
   README prerequisite; and the a11y bind step is now **non-destructive** — if the service is
   already enabled it is preserved rather than delete+re-put, so re-running setup on a real
   device no longer resets the working manual grant back to "malfunctioning". Turnkey re-run
   verified green on this device.

(These are in addition to the three fixes from the NucBox baseline —
`host_path`, `open_app_list`, tunable waits — carried in the same uncommitted set.)

## 4. Scope & caveats

- Vault round-trip intentionally out of harness scope (Phase-3 campaign covers it).
- OV-3 cold-start scaffolding-unlock flake persists (documented in the NucBox report); the relock
  security property is unaffected. A warm-up pass avoids it.
- Coverage now: **API 33 (NucBox emulator, 2/2)** + **API 35 (this device, all green)**. API
  26/29/35-emulator matrix runs remain a WP8 concern.

## 5. Supersession

First real-hardware WP2 campaign. Immutable; a later harness or lock-engine/session/self-gate
change re-runs this gate and files a new dated campaign report.
