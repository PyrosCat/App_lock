# M7 WP1 — Reworked Harness §11 Gate on Real Hardware (Moto G 2025)

- **Date (filing):** 2026-08-31 · **Evidence captured:** 2026-08-31
- **Author / host:** the 2012 i7 dev box, driving the Moto G 2025 over USB adb.
- **Produced against:** `main` @ `f12535b` (clean tree). Harness = `scripts/e2e/` reworked to the M7
  overlay/appops engine (Plan A + the 2026-08-31 formal-review hardening + the neg-control recovery fix).
  APKs built on this host 2026-08-31, compile/target SDK 36:
  - `app-prod-debug.apk` — sha256 `f81b47e96ed7d460839cdaeded28824e981f1fa19b450ad4e3b3049a5a54c4d9`
  - `app-prod-debug-androidTest.apk` — sha256 `0a9fbd4bdeddeeb39aabc1d95b33f1d3a57a235df90787823755477e46aadee9`
- **Verifies / observes:** the **Moto G lane of M7 WP1** (`M7_PLAN.md` WP1; `docs/testing/M7_WP1_HARNESS.md`):
  the reworked harness runs the §11 gate profile against the WP0 spike overlay, on real hardware, clean.
  Bears on **FR-026 / FR-027 / FR-028** and risk **R-002** (the reworked OV-4 control). WP1 lands no
  production code, so **no RTM / ADR / risk-register rows change**.
- **Complements (do not edit — each host keeps its own):** the pending **NucBox emulator-lane** report
  (probe/grep portability across API 30/33/35/36) — the remaining half of WP1 acceptance.

## Context & terms (so this report stands alone)
- **M7 WP1** reworked the M1 device harness (`scripts/e2e/`) off the accessibility engine onto the M7
  overlay/appops engine: an overlay-window probe (`dumpsys window`), `appops` grants replacing the a11y
  rebind, a `LOCK_ENGINE=spike|prod` seam, and OV-4 as a thin `am instrument` wrapper over the durable
  `OverlayRaceUiTest` (the single race truth; its two constants repoint to production at WP2).
- **OV-4 / R-002.** A fast app-relaunch storm can beat the lock onto the screen (the old Activity-lock
  lost 12–37 %); the overlay is meant to win. OV-4 fires K relaunches × N bursts × R repeats and scores
  each burst **TOP** (present + focused), **BEHIND** (present, not focused; budget ≤ 2 %), or **ABSENT**
  (missing — the R-002 exposure; budget **0**).
- **§11 gate profile:** N=50 bursts × K=20 relaunches × R=5 = **250 samples/run**, `T_appear` = **1500 ms**
  (fixed), run **×2** (the 2/2 criterion). Reported by `run_all` as `gate-profile=yes`.
- **Negative control (`neg_overlay_grant`):** revoke the overlay op → the overlay must be **ABSENT**
  (probe sensitivity); re-grant + restart the detector → **recovery**.
- **The spike** = the throwaway WP0 prototype the harness validates against on this lane.

## Environment
- **Moto G 2025** (`kansas`, arm64, **Android 15 / SDK 35**), serial `ZT4229HQ6X`, fingerprint
  `motorola/kansas_g_sys/kansas:15/V1VK35.22-125/06468-7f0382:user/release-keys`. USB adb from the 2012
  host. Screen pinned awake (`stay_on_while_plugged_in=15`; `svc power stayon true`, re-applied by setup).
- **Target app:** Google Clock (`com.google.android.deskclock`) — a normal app (overlays are force-hidden
  over Settings, so Settings can't be the target).
- **Provisioning (all PASS):** fresh app-APK install (fail-closed), `appops` grant `get_usage_stats` +
  `system_alert_window`, logcat buffer → 16 MB, spike overlay confirmed for the target. The androidTest
  APK was reinstalled fresh each OV-4 run (sha256 above).

## Results — VERDICT: PASS (2/2, §11 gate profile)

### OV-4 rapid-relaunch race — PASS (both runs)
| Run | TOP | BEHIND | ABSENT | of | Budget (§11) |
|---|---|---|---|---|---|
| 1 | 250 | 0 | **0** | 250 | ABSENT = 0 hard, BEHIND ≤ 2 % |
| 2 | 250 | 0 | **0** | 250 | " |

50 × 20 × 5, `T_appear` = 1500 ms, z-order sampled every 100 ms via `OverlayRaceUiTest`. The overlay held
**TOP on every one of the 500 bursts** — never ABSENT, not even a self-healing BEHIND flicker. This is the
clean ABSENT = 0 real-hardware evidence (the WP0 software-GPU rig coin-flips on the un-fixed spike, so the
NucBox lanes are diagnostic and the Moto G is the gate host — `M7_PLAN.md` WP1 slow-rig caveat).

### Negative control (`neg_overlay_grant`) — PASS (both runs)
- **Sensitivity:** with `system_alert_window` revoked (`appops … ignore`), the overlay was **ABSENT** —
  the probe is sensitive to the capability it asserts (not matching stale window state or the wrong title).
- **Recovery:** re-granting **+ restarting the detector** restored the overlay to TOP. (A revoked SAW
  appop wedges the running app's overlay drawing until restart — a real Android behavior confirmed on this
  device during the first gate run; `f12535b` fixes the recovery step to restart the detector.)

## Disposition
- **WP1 — Moto G half MET.** The reworked harness runs the §11 gate profile clean on real hardware, and
  the missing-grant negative control demonstrates probe sensitivity + recovery.
- **WP1 acceptance not yet complete:** the **NucBox emulator-lane** probe/grep portability across API
  30/33/35/36 is still owed (`docs/testing/M7_WP1_HARNESS.md`) — diagnostic/non-gate (scaled `T_appear`).
- **No RTM / ADR / risk-register change** — WP1 is test infrastructure. **R-002 is not dispositioned
  here:** this is the WP1 *harness* validation on the *spike*; R-002 closure is a WP6 activity against the
  production overlay.

## Follow-ups
1. **NucBox emulator lanes** (API 30/33/35/36, `aosp/default`, scaled `OV4_T_APPEAR_MS`) — the remaining
   WP1 acceptance half. File `..._m7-wp1-harness_nucbox-g5.md`.
2. Deferred review items are WP2/WP6 (`M7_PLAN.md` WP1 dispositions): CR-004 a11y-detector provisioning
   (WP2), CR-002 BEHIND streak-duration (WP6 / R-002 closure), CR-008 `settle()` detector-state ack (WP2–6).
