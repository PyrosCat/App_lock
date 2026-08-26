# M7 WP0 — Platform-validation spike: campaign report

**Status: DRAFT — method + protocol fixed here; measurement columns PENDING (the fleet fills them).**
This report is not yet filed evidence; it becomes the immutable WP0 record once the fleet lanes below
are run and the result tables are completed.

- **Milestone/WP:** M7 WP0 (`docs/process/M7_PLAN.md`).
- **Baseline:** branch `spike/m7-wp0` off `f696d43`; toolchain bump committed `817ad78`.
- **Target:** `targetSdk`/`compileSdk` 36 (D0), AGP 8.13.2 / Gradle 8.13, Kotlin 2.1.0 (held).
- **Purpose:** retire the WP0 platform unknowns empirically before any WP2 production code, and
  supply the measured values ADR-020 / ADR-021 are Accept-gated on. Disposition per the **canonical
  R-002 evidence standard** and the **§11 numeric protocol** in the plan.

## 1. What was built (on the Windows dev box, all green on API 36)

| Artifact | Kind | Location |
|---|---|---|
| API-36 toolchain bump | production-bound | committed `817ad78` |
| `api36` GMD lane (`ci` 30+35+36, `full` …+36) | production-bound | `app/build.gradle.kts` |
| Throwaway spike: `UsagePollService` (specialUse FGS, queryEvents poll), `OverlayController` (`TYPE_APPLICATION_OVERLAY`, stable title `AppLockSpikeOverlay`), `SpikeBiometricActivity` (transparent `FragmentActivity`, BAL), `SpikeLauncherActivity` (manual driver) | **disposable** — delete at WP0 close | `app/src/main/java/com/applock/platform/spike/` + SPIKE manifest block |
| OV-4 rapid-relaunch race check (black-box UIAutomator) | **durable** — survives spike deletion, repointed to production in WP2 | `app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt` |

**Local gate (this box, API 36):** `compileProdDebugKotlin`, `assembleProdDebug`, `detekt`,
`testProdDebugUnitTest` (Konsist R4 satisfied by the `platform.spike` placement + 67 JVM tests),
`lintProdDebug`, and `compileProdDebugAndroidTestKotlin` all pass. Lint added only version-nag
warnings (AGP-newer, dependency updates, hardcoded spike strings); **no `NewApi`/deprecation/FGS
findings**, and the target-36 move cleared the old too-low-target nags.

## 1a. Dev-box pre-flight (Pixel_5 x86 API 30, headless) — partial

A functional smoke was attempted on the constrained 2012 dev box (the only viable local AVD). It was
**not** a measurement; it validated the driving mechanics. Two outcomes:

- **Defect found + fixed:** `am start-foreground-service` of the spike service failed from the shell
  uid (`Requires permission not exported from uid`) because the service was `exported=false` — which
  would break the OV-4 test's `@Before` on **every** lane, not just here. Fixed: the spike
  `UsagePollService` is now `exported=true` (SPIKE-only; the `@Before` foregrounds the app first so
  the FGS start is permitted on API 31+; production auto-starts its own detection service in WP3).
- **Overlay validation blocked here:** the emulator came up with a persistent
  `com.android.systemui` **ANR** (the known GPU-color-buffer cold-boot failure of this box — adb alive,
  UI layer dead). The overlay draw can't be validated against a dead window layer, so **the functional
  overlay/biometric validation is deferred to the fleet** (NucBox/Moto G), which is where the real
  measurements run anyway.

## 2. How the fleet drives it

Build + install the spike (debug): `./gradlew :app:installProdDebug`. Then over adb:

```bash
PKG=com.applock
# Grants (Usage Access + overlay are special-access ops, not runtime perms)
adb shell appops set $PKG android:get_usage_stats allow
adb shell appops set $PKG android:system_alert_window allow
# Start the poll detector with a target + interval (sweep intervals by repeating with --el interval)
adb shell am start -n $PKG/com.applock.platform.spike.SpikeLauncherActivity
adb shell am start-foreground-service -n $PKG/com.applock.platform.spike.UsagePollService \
  --es target com.android.settings --el interval 400
# Detection lag + biometric/overlay events land in logcat under the M7Spike tag
adb logcat -s M7Spike
```

The OV-4 race check runs as instrumentation; counts default light for CI and are overridden to the
§11 protocol on the reproducing rig / FTL:

```bash
# Full §11 protocol (N=50 bursts, K=20 relaunches, R=5 repeat)
adb shell am instrument -w \
  -e class com.applock.e2e.OverlayRaceUiTest \
  -e ov4_bursts 50 -e ov4_relaunches 20 -e ov4_repeat 5 \
  com.applock.test/androidx.test.runner.AndroidJUnitRunner
```

## 3. Measurement protocol (§11) + result tables — PENDING

> **Canonical R-002 standard.** The **emulator A/B on the NucBox software-GPU rig** is the *decisive*
> proof (old engine reproduces `ABSENT`/`BEHIND` ≥ historical 12–37 %, new overlay never `ABSENT`).
> The **Moto G 2025** shows real-device **no-regression only**. **FTL** closes the OEM/OS residual.
> Never full-Closed on single-OEM evidence.

### 3a. OV-4 overlay-wins-race (per §11: ABSENT=0 hard; BEHIND ≤2% self-healing)

| Lane | Engine | ABSENT | BEHIND | TOP | Verdict |
|---|---|---|---|---|---|
| NucBox emulator (software-GPU) | **OLD** (positive control) | _pending_ | _pending_ | _pending_ | must reproduce ≥12–37% |
| NucBox emulator (software-GPU) | **NEW** (overlay) | _pending_ | _pending_ | _pending_ | ABSENT must = 0 |
| Moto G 2025 (arm64 / API 35) | NEW | _pending_ | _pending_ | _pending_ | no-regression |
| Emulator api30/33/35/36 | NEW | _pending_ | _pending_ | _pending_ | — |
| FTL multi-OEM/API | NEW | _pending_ | _pending_ | _pending_ | residual |

### 3b. Detection latency (≥100 transitions/lane; report p50/p95/p99)

| Lane | Poll interval P (ms) | p50 | p95 | p99 | End-to-end (transition→overlay) |
|---|---|---|---|---|---|
| _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ |

Sweep P over ~300–800 ms; the accepted P (D1) is recorded in **NFR-PERF-012**, the detector interval
constant, and this report (ADR-021 SSOT per GOVERNANCE §2.7), not the ADR body.

### 3c. Biometric-via-BAL reliability

| Lane (API 30/33/35/36 + Moto G) | Launches from overlay OK? | Notes |
|---|---|---|
| _pending_ | _pending_ | _pending_ |

### 3d. Battery / CPU (soak ≥2 h screen-on-idle + ≥8 h screen-off)

| Metric | Result | Budget |
|---|---|---|
| Added drain %/h | _pending_ | X (set from profile) |
| Wakelock across screen-off | _pending_ | none |

### 3e. §2.2 platform cells (†) confirmed on API 36

| Cell | Result |
|---|---|
| Boot FGS-start (post-boot window, `specialUse`) | _pending_ |
| Killed-while-visible → overlay redraw / BAL biometric | _pending_ |

## 4. WP0 acceptance checklist (M7_PLAN.md WP0)

- [ ] (i) poll interval whose p50/p95/p99 the lead accepts vs NFR-PERF-012
- [ ] (ii) emulator A/B: OLD reproduces `ABSENT`/`BEHIND`, NEW never `ABSENT`; Moto G no-regression
- [ ] (iii) biometric launches from the overlay across the matrix
- [ ] (iv) §2.2 platform cells (†) confirmed on API 36
- [x] (v) API-36 toolchain builds clean — build + detekt/ktlint/lint/Konsist green on 36 *(this box)*
- [ ] ADR-021 measured values recorded in SSOT; **ADR-020 + ADR-021 Proposed → Accepted** (lead) before WP2

**Hard gate:** stop/escalate if the A/B does not eliminate `ABSENT`, the Moto G regresses, or the
36-capable toolchain proves blocked (D0 fallback = hold 35 + Play extension). The OEM/OS-diversity
residual (FTL) is a residual-with-plan, not a milestone blocker.

## 5. Fleet limitation (stated explicitly)

Only one real device (Moto G 2025, budget, single OEM/OS). Per the canonical standard the emulator A/B
is decisive and the FTL sweep closes the OEM residual; without FTL, R-002 stays Open at a reduced
rating under a TM §14.10 compensating treatment with a review trigger before M10.
