# ADR-013 — Accessibility-Service-Based Foreground App Detection

**Status:** Accepted (as built) · **Date:** 2026-07-19 · **Source:** M0 decision; validated Phases 1–3

## Context
Lock enforcement requires detecting protected-app launches within NFR-PERF-012's 250 ms budget. As built: AppDetectionService consumes TYPE_WINDOW_STATE_CHANGED accessibility events feeding the lock engine — validated across gating campaigns (back/recents/fast-switch/rapid-relaunch).

## Decision
Retain accessibility-event detection as the primary mechanism.

## Alternatives
UsageStatsManager polling — rejected: polling latency cannot meet the 250 ms enforcement target reliably and costs battery.

## Known limitation (accepted)
`force-stop` of the app nulls `enabled_accessibility_services` platform-wide (inherent to accessibility lockers). Mitigation: ProtectionWatchdogService revocation alert (FR-179) + recovery guidance; maps forward to FR-253 (Accessibility Service Recovery).

## Consequences
The Trigger Processor abstraction (SDS §8.4) wraps this source in M1 so alternative/additional detection sources can be added without engine changes.

## Discovered consequences (2026-07-23) — amendment, does not alter the decision
Real-hardware testing (Moto G 2025 / Android 15,
`docs/reports/campaigns/2026-07-23_wp2-regression_moto-g-2025.md`) surfaced further consequences
of this decision, tracked as **risk R-001** (`docs/process/RISK_REGISTER.md`):
- **Restricted Settings (Android 13+)** blocks the accessibility grant for sideloaded installs,
  and the "Allow restricted settings" escape hatch is being removed on newer devices — a
  distribution-dependent adoption risk. Google Play installs are unaffected.
- **Google Play policy** scrutinises AccessibilityService for non-accessibility use (app-locker
  approval risk).
- **Silent-failure gap:** the service can be enabled-in-setting yet delivering no events; the
  current enabled-setting check (and the FR-179 watchdog) likely cannot detect this — motivating
  an accessibility-health self-test (FR-231/242/253).

These do not change the decision to use accessibility detection for v1; the
**accessibility-vs-UsageStatsManager + Play-policy evaluation is folded into M2**, whose outcome
may confirm or supersede this ADR.
