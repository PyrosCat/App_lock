# WP5 Hilt-Migration Gating Regression — Moto G 2025 / Android 15 (real hardware)

**Date:** 2026-08-08 (filed; captured same day)
**Author / host:** 2012 i7-3520M dev box driving a physical **Moto G 2025** over USB adb.
**Device under test:** Moto G 2025 (`ZT4229HQ6X`) — **Android 15 (API 35)**, `arm64-v8a`,
**720×1604**, build `V1VK35.22-125` (security patch 2025-06-01).
**App under test:** `app-dev-debug.apk` — WP4 **`dev`** flavor (`applicationId com.applock.dev`),
built from repo HEAD **`c8532e0`**. The binary includes the **WP5 Hilt migration** (`f38ecc3`
introduce Hilt; `498a240` delete `core/Graph` + flip Konsist R1 to terminal); the two commits on
top of it (`2515f49`, `c8532e0`) are docs-only. Working tree clean.
**Verifies:** the WP5 Hilt DI migration **preserves the security-critical gating semantics** frozen
by the WP2 harness — core lock→PIN→unlock, IMMEDIATE relock on window switch (OV-3), the F4
fast-relaunch defense (OV-4), and the F3 self-gate / FR-108 resume defense. **ADR-015's closure
gate** (real-hardware half).
**Verdict:** ✅ **PASS — all four checks green, 2/2 consecutive runs**
(`run_all.sh --skip-setup -n 2`, single shot). Matches the pre-Hilt WP2 all-green baseline on the
same device (2026-07-23) → **no Hilt regression**.

---

## 1. Results

`APP_ID=com.applock.dev scripts/e2e/run_all.sh --skip-setup -n 2` — one shot, both runs green:

| Check | Run 1 | Run 2 | Assertion |
|---|---|---|---|
| `smoke_core` | ✅ 3/3 | ✅ 3/3 | lock on protected-app launch; correct PIN unlocks; wrong PIN stays locked |
| `ov3_fast_switch` | ✅ 10/10 | ✅ 10/10 | IMMEDIATE relock on every return (no session leak) |
| `ov4_rapid_relaunch` (F4) | ✅ PASS | ✅ PASS | lock screen up after 5 rapid `am start`s; protected content not foreground |
| `f3_self_gate` (F3 / FR-108) | ✅ PASS | ✅ PASS | self-gate re-appears on resume; App List unreachable without PIN |

Unlike the emulator matrix, **OV-4 was clean on both runs** — the R-002 rapid-relaunch flake
(12–37% on some emulator APIs) did not manifest on real hardware, consistent with it being an
emulator-timing artifact rather than a lock-engine defect.

## 2. Accessibility grant — path inconclusive this run (prior-grant confound)

Setup enabled the service via adb
(`settings put secure enabled_accessibility_services com.applock.dev/…AppDetectionService`). This
session it came up **Bound and delivering `TYPE_WINDOW_STATE_CHANGED` events**
(`dumpsys accessibility`) and gated correctly (Clock locked on launch) — with **no Settings-UI
toggle performed during this session**.

This does **not** cleanly refute the 2026-07-23 finding (adb-enabled a11y left "malfunctioning" on
this device; manual off/on the only fix on Android 15, where the "Allow restricted settings" menu
is absent). The operator recalls granting the service **manually in a prior session**, and Android
may retain per-app/component trust that lets a later adb enable deliver events without re-tripping
the Restricted-Settings malfunction. **Grant path this run is therefore undetermined** — the
2026-07-23 "manual grant required" guidance still stands for a genuinely clean device. The gating
verdict (2/2 green) is **independent of grant path** and stands.

## 3. Environment finding — device in landscape; harness assumes portrait

Provisioning first failed at the Clock-protect step ("could not locate the 'Clock' row"). Root
cause: the phone was **rotated to landscape**, so the UI coordinate space was ~1520×720 while the
harness computes all swipes/taps from `wm size` (720×1604, portrait) — every gesture landed
off-target and the Compose `LazyColumn` never scrolled. Fix: lock portrait before the run —

```bash
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
```

After locking portrait, setup located and toggled Clock on the first step and the full gate passed.
The emulators always boot portrait, so this never surfaced before. **Follow-up (harness):**
`setup_device.sh` should force/assert portrait during provisioning on real devices.

## 4. Scope & caveats

- **Coverage:** Moto G 2025 / **API 35 / arm64 real hardware, post-Hilt** — the real-hardware half
  of the WP5 device exit. The **NucBox emulator API 26/29/33/35 matrix remains owed** (tracked in
  `scripts/e2e/README.md` "Validation status" and the 2026-08-08 handoff).
- **Before/after:** pre-Hilt WP2 on this device (2026-07-23) = all green; post-Hilt (this run) =
  all green → the mechanical `Graph`→Hilt swap preserved the security-critical semantics.
- Vault round-trip intentionally out of harness scope (Phase-3 campaign covers it).

## 5. What this closes

- **WP5 device exit criterion (real-hardware half): MET** — ADR-015's closure gate for on-device
  gating regression on real hardware.
- **WP6 (package realignment) is unblocked** on the real-hardware axis (governance: nothing after
  WP2 proceeds while the gate is red — it is green here). The NucBox emulator-matrix half is still
  owed before the gate is fully closed at the WP8 IS Phase-0 gate.
- RTM batch (FR-108 re-verification pointer, etc.) is **deferred to WP8** per the M1 plan; this
  report is the evidence pointer.

## Immutability

Immutable evidence per GOVERNANCE §2. A later lock-engine / session-manager / self-gate / DI change
re-runs this gate and files a new dated campaign report; this one is not edited.
