# M7 WP1 — Reworked Harness Probe/Grep Portability on Emulator Lanes (NucBox G5)

- **Date (filing):** 2026-08-31 · **Evidence captured:** 2026-08-31
- **Author / host:** the GMKtec NucBox G5 (Win 11, N-series), driving four local AVDs over adb.
- **Produced against:** `main` @ `b04e36b` (clean tree; in sync with `origin/main`). Harness = `scripts/e2e/`
  reworked to the M7 overlay/appops engine (Plan A + the 2026-08-31 formal-review hardening + the
  neg-control recovery fix). APKs built on **this** host 2026-08-31, compile/target SDK 36:
  - `app-prod-debug.apk` — sha256 `230fb9dc1f82e3578b4b90d95e45cc2dc7e5b9fbabdbafb8f2e67b4eddc547eb` (42888 KB)
  - `app-prod-debug-androidTest.apk` — sha256 `4dd7e746372e3e99988ec417a090cbfbae00db9e8e9d955aa7893e43e8d79501` (1010 KB)
- **Verifies / observes:** the **NucBox emulator-lane half of M7 WP1** (`M7_PLAN.md` WP1;
  `docs/testing/M7_WP1_HARNESS.md` §4): that the reworked harness's probes/greps read **TOP / BEHIND /
  ABSENT correctly per API level** across **API 30 / 33 / 35 / 36** (`aosp/default` images). This is the
  **diagnostic / NON-GATE** lane (scaled `T_appear`) — it reads for **parser portability**, not the
  ABSENT=0 gate. Bears on **FR-026 / FR-027 / FR-028** and risk **R-002** (the reworked OV-4 control).
  WP1 lands no production code, so **no RTM / ADR / risk-register rows change**.
- **Complements (do not edit — each host keeps its own):** the **Moto G** §11 gate PASS
  (`campaigns/2026-08-31_m7-wp1-harness_moto-g-2025.md`, the clean ABSENT=0 real-hardware evidence) and the
  decisive old-vs-new A/B banked at WP0 (`campaigns/2026-08-28_m7-wp0-emulator_nucbox-g5.md`). With this
  report, **both halves of WP1 acceptance are now on record.**

## Context & terms (so this report stands alone)
- **M7 WP1** reworked the M1 device harness (`scripts/e2e/`) off the accessibility engine onto the M7
  overlay/appops engine: an overlay-window probe (`dumpsys window`), `appops` grants replacing the a11y
  rebind, a `LOCK_ENGINE=spike|prod` seam, and OV-4 as a thin `am instrument` wrapper over the durable
  `OverlayRaceUiTest` (the single race truth; its two constants repoint to production at WP2).
- **OV-4 / R-002.** A fast app-relaunch storm can beat the lock onto the screen (the old Activity-lock lost
  12–37 %); the overlay is meant to win. OV-4 fires K relaunches × N bursts × R repeats and scores each
  burst **TOP** (present + focused), **BEHIND** (present, not focused), or **ABSENT** (missing — the R-002
  exposure). The z-order read is `lib.sh: overlay_z` (bash) / `OverlayRaceUiTest.zOrder()` (instrumentation),
  both parsing `dumpsys window` (not `… window windows`, which omits `mCurrentFocus`).
- **Why these lanes are diagnostic, not gate.** On the slow software-GPU rig the un-fixed spike's overlay
  appear-latency straddles the fixed 1500 ms `T_appear`; *more bursts raise* P(fail) (WP0 finding — the
  warm-overlay/off-main remedy is a WP2/WP3 production item). So these lanes run at a **scaled
  `OV4_T_APPEAR_MS=4000`** and the light default count (25×20×1 = 25 samples), and `run_all` labels the
  verdict **NON-GATE**. The clean ABSENT=0 gate evidence is real hardware (the Moto G, TOP=250/250 ×2).
- **§4 acceptance for this lane** = per API level: (1) OV-4 **runs** (not assume-skipped — a launchable
  target app resolves), (2) its `dumpsys window` count line **parses** (the `zOrder` grep reads valid
  TOP/BEHIND/ABSENT), and (3) `neg_overlay_grant` **passes** (the bash `overlay_z` probe reads
  present-vs-absent correctly). That is what "validated" means here — **not** ABSENT=0.
- **Negative control (`neg_overlay_grant`, CR-006):** revoke the overlay op → the overlay must be **ABSENT**
  (probe sensitivity); re-grant + restart the detector → **recovery**.
- **The spike** = the throwaway WP0 prototype the harness validates against on this lane. **`aosp/default`**
  images (NOT `aosp-atd`, which ship no launchable target app → OV-4 assume-skips, reported as a failure).

## Environment
- **Host:** GMKtec NucBox G5, Windows 11, ADR-014 verification-fleet host. SDK at
  `%LOCALAPPDATA%\Android\Sdk`; harness driven from Git Bash with platform-tools on PATH.
- **AVDs (all `default`/AOSP tag, `x86_64`), booted headless** `-no-window -no-snapshot -no-audio
  -no-boot-anim -gpu swiftshader_indirect -memory 3072`:

  | Lane | AVD | `ro.build.version.release` / `.sdk` / `.cpu.abi` | image tag |
  |---|---|---|---|
  | api30 | `matrix_api30def` | 11 / 30 / x86_64 | default (aosp) |
  | api33 | `matrix_api33def` | 13 / 33 / x86_64 | default (aosp) |
  | api35 | `matrix_api35d`   | 15 / 35 / x86_64 | default (aosp) |
  | api36 | `matrix_api36`    | 16 / 36 / x86_64 | default (aosp) — **real API-36** |

- **Target app (all lanes):** `com.android.deskclock` (AOSP DeskClock — a normal app; overlays are
  force-hidden over Settings, so Settings can't be the target). `resolve_clock` found it on every image,
  so **OV-4 did not assume-skip on any lane.**
- **Provisioning (all lanes, all PASS):** app-APK install via **`adb install -r -g`** (fail-closed,
  CR-005), `appops` grant `get_usage_stats` + `system_alert_window`, logcat buffer → 16 MB,
  `warm_detection` past the post-install first-detection gap, spike overlay confirmed for the target.
  androidTest APK reinstalled via `-r` each run and its sha256 re-recorded (`4dd7e746…`, matching the
  build above). **Note on "reinstall", not "clean install":** `-r` is an in-place reinstall — it replaces
  the APK **binary** every run (the point: provenance / no stale on-device build can be cited as evidence)
  but **preserves app data** (`/data/data`), and the AVDs were not `-wipe-data`'d (userdata persists across
  `-no-snapshot` cold boots). So lanes are independent by virtue of being **separate AVDs**, not by a
  data wipe. Immaterial to what this lane asserts — the spike holds no PIN/DB and resets its target on
  process death — but stated precisely so the post-install behaviour isn't misread.

## Results — VERDICT: PASS (4/4 lanes, NON-GATE / diagnostic profile)

Every lane ran `OV4_T_APPEAR_MS=4000 run_all.sh -n 1` (engine=spike, `gate-profile=no`). **Both checks
(`ov4_rapid_relaunch`, `neg_overlay_grant`) passed on all four API levels.** The probes read the
z-order correctly on every image — the portability claim — and, with the scaled `T_appear` absorbing the
swGPU appear-latency, the diagnostic OV-4 count was clean (ABSENT=0) on all four.

### OV-4 rapid-relaunch race — parser read correctly on every lane
| Lane | TOP | BEHIND | ABSENT | of | Profile |
|---|---|---|---|---|---|
| api30 | 25 | 0 | **0** | 25 | 25×20×1, `T_appear`=4000 ms — NON-GATE |
| api33 | 25 | 0 | **0** | 25 | " |
| api35 | 25 | 0 | **0** | 25 | " |
| api36 | 25 | 0 | **0** | 25 | " |

Raw `M7SpikeTest` logcat lines (the count line the `zOrder` grep produces and `run_all` retains):
```
api30  08-31 19:30:14.284 I M7SpikeTest: OV-4 overlay race: TOP=25 BEHIND=0 ABSENT=0 of 25 (bursts=25 relaunches=20 repeat=1 t_appear=4000ms)
api33  08-31 19:34:55.429 I M7SpikeTest: OV-4 overlay race: TOP=25 BEHIND=0 ABSENT=0 of 25 (bursts=25 relaunches=20 repeat=1 t_appear=4000ms)
api35  08-31 19:39:50.610 I M7SpikeTest: OV-4 overlay race: TOP=25 BEHIND=0 ABSENT=0 of 25 (bursts=25 relaunches=20 repeat=1 t_appear=4000ms)
api36  08-31 19:44:48.106 I M7SpikeTest: OV-4 overlay race: TOP=25 BEHIND=0 ABSENT=0 of 25 (bursts=25 relaunches=20 repeat=1 t_appear=4000ms)
```
The instrumentation's `dumpsys window` z-order parse produced a valid TOP/BEHIND/ABSENT tally on API 30, 33,
35 **and real API-36** — the grep is portable across the four AOSP images. **This ABSENT=0 is diagnostic,
not gate evidence:** it was obtained at the scaled 4000 ms `T_appear` and the light 25-sample count. At the
§11 gate (250 samples, fixed 1500 ms) a larger N *raises* P(fail) on this swGPU rig — which is exactly why
the gate host is real hardware (the Moto G).

**How an ABSENT on this lane would be handled (none occurred here):** it would **trigger investigation, not
automatic dismissal** — distinguishing a rig artifact from a real defect is precisely what this diagnostic
lane is for. The two known swGPU mechanisms (a late overlay appear past the scaled budget;
`settle()`-coalescing, CR-008) are the *most likely* explanation and would be checked first, but an ABSENT
beyond the
4 s budget could equally expose a **parser, harness, detector, or overlay defect**; only once those are
ruled out is it attributed to rig timing. The harness backstops this rather than hiding it: OV-4 **fails
closed on any ABSENT>0** (`ov4_rapid_relaunch.sh`) and **CR-007 validates the count-line arithmetic**, so a
miss or a mis-parse surfaces as a lane **FAIL**, never a silent pass.

### Negative control (`neg_overlay_grant`) — PASS on every lane
- **Sensitivity (all 4):** with `system_alert_window` revoked (`appops … ignore`), the overlay was
  **ABSENT** — the bash `overlay_z` probe is sensitive to the capability it asserts (not matching stale
  window state or the wrong title).
- **Recovery (all 4):** re-granting **+ restarting the detector** restored the overlay to TOP (a revoked
  SAW appop wedges the running app's overlay drawing until restart — the `b04e36b`/neg-control fix restarts
  the detector after re-grant). The present-vs-absent contrast — the operative portability check for the
  bash probe — read correctly on API 30/33/35/36.

## Disposition
- **WP1 — NucBox emulator-lane half MET.** The reworked harness's probes/greps read TOP/BEHIND/ABSENT
  correctly across API 30/33/35/36 (`aosp/default`), OV-4 ran (never assume-skipped), and the missing-grant
  negative control demonstrated probe sensitivity + recovery on every level. Diagnostic profile
  (scaled `T_appear`), as the plan requires for this rig.
- **WP1 acceptance now complete (both halves on record):** this NucBox portability lane + the Moto G §11
  gate PASS (`campaigns/2026-08-31_m7-wp1-harness_moto-g-2025.md`).
- **No RTM / ADR / risk-register change** — WP1 is test infrastructure. **R-002 is not dispositioned
  here:** this is the WP1 *harness* validation on the *spike*; R-002 closure is a WP6 activity against the
  production overlay.
- **One UI `mCurrentFocus` caveat is out of scope for these lanes** — the AOSP NucBox images don't exercise
  it; Samsung One UI coverage comes via FTL (`campaigns/2026-08-29_m7-wp0-ftl-sweep_firebase-test-lab.md`),
  and the WP2 open item sets the production overlay focus flags + revalidates the grep.

## Follow-ups
1. Deferred formal-review items are WP2/WP6 (`M7_PLAN.md` WP1 dispositions): **CR-004** a11y-detector
   provisioning (WP2), **CR-002** BEHIND streak-duration (WP6 / R-002 closure), **CR-008** `settle()`
   detector-state ack (WP2–6).
2. At WP2 the harness flips `LOCK_ENGINE=prod` (repoints `OVERLAY_WINDOW_TITLE` / `POLL_SERVICE`), enabling
   the full suite (`smoke_core` / `ov3_fast_switch` / `f3_self_gate`); re-run this matrix against the real
   engine then, with the warm-overlay/off-main remedy in place so the swGPU lanes can gate rather than
   coin-flip.
