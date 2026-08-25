# ADR-021 — UsageStats Polling Foreground Detector: Consolidated Special-Use Foreground Service, queryEvents Contract, and Poll Interval

**Status:** Proposed · **Date:** 2026-08-25 · **Source/authority:** M7 plan
(`docs/process/M7_PLAN.md`, WP3 + §2.4), drafted 2026-08-25; **to be Accepted at M7 WP0 by the
project lead before any WP2/WP3 production code** (GOVERNANCE §2.2). · **Implements:** ADR-013B (does
not supersede it). · **Accept gate:** stays *Proposed* until WP0 confirms a viable bounded poll
interval and tolerable battery on the API-36 target, with those measured values recorded in their SSOT
(WP0 report + the detector constant + NFR-PERF-012) per GOVERNANCE §2.7 — this ADR fixes the method,
not the numbers.

## Context
ADR-013B fixed `UsageStatsManager` (Usage Access) as the **sole** 1.0.0 foreground-detection source,
replacing the accessibility event stream. It did not decide the detector *mechanics*: which
`UsageStatsManager` API, the poll interval, the query-window / de-duplication / freshness contract,
how the locked/keyguard state is handled, the foreground-service type and its Play justification, or
whether detection and protection-health share one service.

The platform facts that force these decisions: `UsageStatsManager` has **no foreground callback**, so
detection is a poll; `queryEvents` is a wall-clock event query that returns **nothing while the user
is not unlocked** (Android R+); and running a persistent poll loop under targetSdk 36 has battery and
foreground-service-policy implications. These choices set NFR-PERF-012 (latency) and the Play/battery
compliance surface, so they belong in a recorded, reviewed decision.

## Decision (proposed)
1. **Detection API + contract.** Poll `queryEvents(now − W, now)` each tick (not `queryUsageStats`,
   which aggregates and is coarser), consuming `ACTIVITY_RESUMED` as the foreground signal. The full
   behavior is the **§2.4 detection contract** in the M7 plan and is normative for the adapter: query
   window `W` > interval `P`; a `(package, eventTimestamp)` de-duplication cursor; freshness threshold
   `F`; a wall-clock-jump guard; `UserManager.isUserUnlocked()` / `ACTION_USER_UNLOCKED` gating (screen-on
   is **not** unlocked); and a process-restart bootstrap (seed the cursor from `now`, hold `loading`
   until the first confirmed post-start event, never emit a retroactive lock).
2. **Poll interval `P` (D1).** Poll at a **fixed, bounded, documented** interval with backoff on
   repeated query failure; the latency model is *poll interval + the ≤250 ms enforcement budget* (an
   honest documented figure, not a sub-second promise). **Per GOVERNANCE §2.7 the value is not restated
   in this ADR** — WP0 measures it (explored ~300–800 ms) and records it in its **SSOT**: the detector's
   interval constant, the WP0 evidence report, and **NFR-PERF-012** (RTM). This ADR fixes the *method
   and acceptance model*; the number lives in those sources.
3. **Port / adapter.** A `ForegroundDetectionSource` port (emits a normalized `current package +
   observation time`) in `service/`; the `UsageAccessDetector` adapter in `platform/`. This port is the
   single seam the deferred 2.0.0 accessibility tier plugs a second adapter into (the ADR-013A "Trigger
   Processor" seam). No source-selection layer ships in 1.0.0.
4. **Service topology (D2): consolidate.** One foreground service hosts **both** the detection poll
   **and** protection-health reporting (repurpose `ProtectionWatchdogService` into the poll host, or a
   single new service), rather than a separate poller plus the existing watchdog. Rationale: the same
   "protection required" lifecycle gate, fewer foreground services, a smaller Play/battery surface, and
   detector-liveness read in-process (no cross-service boundary). The screen-state receiver
   (`SCREEN_OFF` / unlock) re-homes into this service (M7 §2.1/§2.2).
5. **FGS type + justification.** `foregroundServiceType="specialUse"` with a Play-review justification
   string describing continuous app-lock foreground detection. Lifecycle per SDS §15.2: start only when
   a PIN is set **and** ≥1 protected app **and** capabilities are present; stop when none are selected;
   pause the loop **only** on screen-off / locked, or when **no protected apps are selected at all** —
   **never** merely because the current foreground app is unprotected, which would miss the switch
   *into* the next protected app (battery, SDS §15.8); bounded retry and backoff, never a tight loop.
6. **Battery profile.** A frugal loop (stop on screen-off / locked / **no protected selections**, per
   #5) that holds **no wakelock across screen-off**. Per §2.7 the measured drain / wakelock / CPU-wake
   figures are recorded in their SSOT (the WP0 evidence report), not restated here; this ADR fixes the
   *requirement* (frugal loop, no cross-screen-off wakelock).

**Binding constraints (1.0.0):** Usage Access is the sole detection source (ADR-013B); the interval is
bounded and documented; no usage timeline is stored, only current + previous package identity (SDS
§8.2/§15.3); nothing is persisted (DDS §1.3).

## Alternatives considered
- **`queryUsageStats` (aggregated) instead of `queryEvents`.** Rejected: it aggregates over an
  interval, coarser and laggier for detecting a specific transition; `queryEvents` returns ordered
  transition events.
- **An event callback / "real-time" `UsageStatsManager`.** Not available: there is no foreground
  callback, so polling is the only mechanism (which is why the interval is a decision).
- **Separate poll service + the existing watchdog (non-consolidated).** Rejected (D2): two foreground
  services, a cross-service liveness read, and a larger Play/battery surface, for no benefit.
- **Resume polling on `SCREEN_ON`.** Rejected: `queryEvents` yields nothing while locked (Android R+);
  resume must gate on unlock (§2.4).
- **Accessibility low-latency tier in 1.0.0.** Out of scope: deferred to 2.0.0 (ADR-013B); the port
  leaves the seam for it.

## Consequences
**Positive:**
- A single detection mode gives one NFR-PERF-012 latency profile (ADR-013B consequence).
- The consolidated foreground service is the smallest Play/battery surface, and detector-liveness (the
  three timestamps in M7 WP4) reads in-process.
- The port preserves the 2.0.0 tiering path; the fail-secure readiness model (WP3) builds on the
  in-process detector.

**Negative / costs:**
- Poll latency is device-dependent and is the dominant latency term; the honest bar is "poll + 250 ms,"
  not a sub-second guarantee.
- `specialUse` FGS plus the 2026 battery/wakelock policy demand a frugal loop and a defensible Play
  justification (Play review itself is M10).
- The `queryEvents` wall-clock and locked-state quirks require the §2.4 guards; getting them wrong makes
  detection silently stall.
- The interval and battery numbers are **empirical** and gate this ADR's Accept (WP0).

## Related requirements
FR-026 (foreground detection = Usage Access baseline) · FR-034 / FR-035 (engine init, background-service
persistence) · FR-179 (health = Usage Access + overlay) · FR-231 / FR-242 (startup health / self-test,
iff the WP4 liveness lands) · NFR-PERF-012 (latency) · NFR-PERF-015 (benchmark). Risks: **R-005**
(fail-secure readiness builds on this detector), **R-001** (this is the replacement that removes the
accessibility exposure), **R-002** (the detector feeds the overlay remediation). Related ADRs:
**implements ADR-013B**; pairs with **ADR-020** (presentation); the `specialUse` FGS + UsageStats are
validated on the **API-36** target (D0; ADR-014).

## Implementation status
**Proposed — not implemented.** WP0 measures `P` + the battery profile on the API-36 target and records
them in their SSOT (the report + the detector constant + NFR-PERF-012), confirming a viable interval
exists; this ADR is then **Accepted** (its method unchanged) before WP2/WP3. WP3 implements the
detector + the consolidated foreground service; WP6 verifies the latency figure on the §10 matrix.
