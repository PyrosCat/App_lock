# M7 WP0 — Firebase Test Lab multi-OEM sweep: R-002 OEM/OS residual (OV-4 overlay race)

- **Date (filing):** 2026-08-29 · **Evidence captured:** 2026-08-29 (the authoritative 5-device sweep) and 2026-08-28 (pipeline-validation `quick` run + a partial sweep, recorded here as provenance).
- **Author / host:** Firebase Test Lab (Google's physical device farm), submitted from the primary dev box (Win 10) via `scripts/ftl/run_ov4_ftl.sh`. FTL project `applock-ftl-m7wp0`.
- **Produced against:** branch `spike/m7-wp0` @ `b318af1` (base current with `origin`). Test artifact = the WP0 **spike** overlay: `app-prod-debug.apk` + `app-prod-debug-androidTest.apk` (`OverlayRaceUiTest`, committed at `ed035fc`), compile/target SDK 36, built on the fleet host 2026-08-28. Device matrix = `run_ov4_ftl.sh` `sweep` profile (the `CPH2449`→`OP5552L1` swap was an uncommitted runner refinement at run time).
- **Verifies / observes:** the **FTL lane** of M7 WP0 (`M7_PLAN.md` §10) — in the canonical R-002 evidence standard, the **OEM/OS residual sweep**: the multi-OEM / multi-API physical sweep that closes the device/OS-diversity residual the single Moto G cannot. Bears on **FR-026 / FR-027 / FR-028** and risk **R-002**. WP0 lands no production code, so **no RTM / ADR / risk-register rows change** (M7_PLAN §6).
- **Complements (do not edit — each host keeps its own):** the **decisive emulator A/B** `docs/reports/campaigns/2026-08-28_m7-wp0-emulator_nucbox-g5.md` and the Moto G **real-hardware no-regression check** `docs/reports/campaigns/2026-08-26_m7-wp0-spike_moto-g-2025.md`.

## Context & terms (so this report stands alone)
- **M7 / WP0.** M7 replaces the app-lock engine (accessibility-service detection + full-screen lock `Activity`) with a `UsageStatsManager` poll + a drawn `SYSTEM_ALERT_WINDOW` overlay. **WP0** measures Android's behaviour on a **throwaway spike** before any production code; this report is one WP0 lane.
- **R-002 / OV-4.** R-002: a fast app-relaunch storm can beat the lock onto the screen, briefly exposing the protected app (the old Activity-lock loses ~12–37 % of the time; the overlay is meant to win). **OV-4** is the automated check: fire **K** rapid `am start` launches at a target app, sample `dumpsys window` for the overlay's z-order/focus, scored per burst as **TOP** (present and focused, correct), **BEHIND** (present, not focused, a brief flicker, budget ≤ 2 %), or **ABSENT** (not present, the R-002 exposure, budget **0**). Run **N** bursts × **R** repeats.
- **This lane's counts.** The `sweep` profile ran **N=30 bursts × K=20 relaunches × R=2 = 60 samples per device**, `T_appear`=1500 ms, sampled every 100 ms. This is lighter than §11's full **50×20×5 = 250** (chosen to fit Spark's free physical quota, below); the ABSENT=0 / BEHIND≤2 % budgets still apply.
- **Canonical R-002 evidence standard.** One standard with four parts, each a different kind of evidence: the **decisive A/B** — the emulator old-vs-new contrast, the only proof the fix *works* (NucBox report); the **no-regression check** — the single real phone (Moto G) can only show that nothing regressed, not that the race is fixed; the **OEM/OS residual sweep** — an FTL multi-OEM/API run that closes the device/OS-diversity gap the phone cannot (**this report**); and the **fallback** — until the sweep runs, R-002 stays *Open* at a reduced rating. R-002 is never full-Closed on one phone or one emulator alone.
- **Target app.** `OverlayRaceUiTest` resolves a *normal* (non-Settings) target app per device from a candidate list (Google Maps, Google clock, AOSP clock, calculators); Settings and permission screens force-hide overlays (`HIDE_NON_SYSTEM_OVERLAY`), so they cannot serve as the target.

## Headline
- **PASS — `ABSENT = 0` on every device across the full OEM/API physical matrix.** Motorola, Samsung One UI, OnePlus OxygenOS, and stock Pixel, spanning **API 30 / 33 / 34 / 35 / 36** (including the targetSdk-36 shipping target on real hardware). The spike overlay is never fully missing under the relaunch burst on any of these OEM WindowManagers — the standard's **OEM/OS residual sweep** passes at the WP0/spike level.
- **All BEHIND within the §11 2 % budget this run** (≤ 1/60 = 1.7 % per device).
- Target app resolved to **Google Maps** on all five (GMS present), so nothing was skipped — unlike the AOSP emulator lanes, which needed the `aosp/default` image fix (NucBox report).

## Environment
- **FTL physical devices**, real OEM hardware, `locale=en, orientation=portrait`. Project `applock-ftl-m7wp0`; Cloud Testing + Tool Results APIs enabled for this campaign.
- Submitted with `SKIP_BUILD=1 FTL_PROFILE=sweep scripts/ftl/run_ov4_ftl.sh`, which passes the counts as instrumentation args (`ov4_bursts=30,ov4_relaunches=20,ov4_repeat=2`) and selects only `class com.applock.e2e.OverlayRaceUiTest` (`--test-targets`), so the smoke suite does not run on this lane.
- **Billing:** Spark (no-cost) tier: physical devices are included at **5 tests/day, 30 min/day**. The full §11 counts (250 samples/device) will exceed that and need Blaze; recorded as a WP6 follow-up.

## Results — authoritative sweep (2026-08-29)
Matrix `matrix-3rifl7gf52e48` (`.../matrices/7044747560405875623`); raw artifacts `gs://test-lab-stzk3vs47t3fa-jhtr896k7wymu/ov4/20260829-101336-sweep/`. Counts read from each device's logcat (`M7SpikeTest`).

| Device | codename | OEM / skin | API | TOP | BEHIND | ABSENT | of | FTL outcome |
|---|---|---|---|---|---|---|---|---|
| Motorola moto e20 | `aruba` | Motorola | 30 | 59 | 1 (1.7 %) | **0** | 60 | Inconclusive* |
| Samsung Galaxy S22 Ultra | `b0q` | One UI | 33 | 59 | 1 (1.7 %) | **0** | 60 | Passed |
| OnePlus 10T | `OP5552L1` | OxygenOS | 34 | 60 | 0 | **0** | 60 | Passed |
| Google Pixel 8a | `akita` | stock | 35 | 60 | 0 | **0** | 60 | Passed |
| Samsung Galaxy A35 5G | `a35x` | One UI | 36 | 60 | 0 | **0** | 60 | Passed |

\* FTL marked `aruba` *Inconclusive — Infrastructure failure*, but its logcat shows a full clean run (target resolved to Maps, 60 samples, ABSENT=0): a teardown/results-processing hiccup on FTL's side, not a test failure. Data is present and counted. The matrix exit was 15 solely because of this one Inconclusive.

## Provenance runs (2026-08-28)
- **Pipeline validation (`quick`)** — `ov4/20260828-205000-quick`, device `akita`, light counts (5×10×1): **TOP=5, BEHIND=0, ABSENT=0**. Confirmed the whole FTL chain works end-to-end on real hardware (APK upload, `appops` grants, spike FGS start, `dumpsys window` parse) and that the test genuinely ran (not an `assumeTrue` skip).
- **Partial sweep** — `ov4/20260828-210625-sweep`: `aruba`/30, `b0q`/33, `a35x`/36 ran; `CPH2449` (OnePlus 11) hit an FTL infra error and produced no results. On this run **`b0q` scored BEHIND = 3/60 (5 %)**, over the 2 % budget (see the open item below). `CPH2449` was retried and **errored a second time**, so the matrix slot was reassigned to `OP5552L1` (OnePlus 10T, same OxygenOS/API-34 role); it passed cleanly on 2026-08-29.

## Findings
1. **OEM/OS residual sweep: passes at the WP0/spike level.** ABSENT=0 held on every OEM WindowManager tested and across API 30→36. With the **decisive emulator A/B** (NucBox report) and the **Moto G no-regression check** (Moto G report), the three evidence parts of the standard are now in hand for WP0. **Not full-Closed here:** this is the *spike* overlay and the lighter 60-sample counts, so final closure waits on the WP6 re-run against the production overlay (and, ideally, the full §11 counts).
2. **`b0q` (Samsung One UI) BEHIND is borderline and variable:** 5 % on 2026-08-28, 1.7 % on 2026-08-29. That run-to-run swing means it is a marginal signal, not a clean pass to lean on, and the cause is still ambiguous between (a) a genuine One UI overlay-focus delay and (b) the `mCurrentFocus` `dumpsys window` grep mis-parsing One UI's format (the overlay is focused but not matched, scoring a false BEHIND). **Open item for WP2** — re-validate the focus grep on One UI, and set the production overlay's focus flags accordingly. (ABSENT was 0 both runs, so this does not bear on the R-002 exposure itself.)
3. **Transient FTL infrastructure flakes, not code defects.** `CPH2449` (OnePlus 11) errored twice; `aruba` (which passed cleanly on 2026-08-28) went Inconclusive on teardown on 2026-08-29. Different device each time, all FTL-side — the same APK ran on 4–5 other devices per matrix. The runner's per-device isolation plus results-recovery from the GCS bucket makes these non-blocking; budget ~1 transient inconclusive per 5-device run.
4. **Per-device target resolution worked on physical hardware** — all five resolved Google Maps (GMS present), so nothing assume-skipped. The `dumpsys window` overlay-title + `mCurrentFocus` probe read correctly on every OEM (subject to finding #2's One UI focus-parse question).

## Disposition
- **ADR-020 / ADR-021 stay Proposed.** This report supplies the **OEM/OS residual sweep** evidence they were waiting on; with the **decisive A/B** and the **no-regression check** already filed, the WP0 R-002 evidence set is complete on the spike. The lead's move to Accepted, and the authoritative WP6 re-run against production, remain the gates before R-002 is dispositioned to Closed-with-residual.
- **R-002:** per the canonical standard, not full-Closed on this evidence (spike overlay, sweep-profile counts); the OEM/OS residual is now covered by a passing multi-OEM sweep rather than left Open-unaddressed. Final rating change is a WP6 / risk-register action, not made here.
- **No RTM / ADR / risk-register edits in this change** — WP0 evidence only (M7_PLAN §6).

## Follow-ups
1. **WP2 — resolve the One UI BEHIND question** (finding #2): re-validate the `mCurrentFocus` grep against Samsung's `dumpsys window` format and set the production overlay's focus flags; confirm on `b0q`/`a35x` that BEHIND stays ≤ 2 %.
2. **WP6 — authoritative re-run against the production overlay.** Repoint `OverlayRaceUiTest`'s `POLL_SERVICE` / `OVERLAY_TITLE` to the real engine and re-run this sweep; use the **full §11 counts** (50×20×5 = 250/device) under **Blaze** for the gate numbers, and widen OEM coverage (e.g. Xiaomi / Sony / OPPO) and add `orientation=landscape`.
3. **Matrix hygiene:** the `sweep` matrix now uses `OP5552L1` (OnePlus 10T) after `CPH2449` (OnePlus 11) errored twice; re-check codenames/versions against the live catalog (`gcloud firebase test android models list --filter="form=PHYSICAL"`) before the WP6 run, as the FTL catalog drifts.
