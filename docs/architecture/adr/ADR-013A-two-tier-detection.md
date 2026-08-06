# ADR-013A — Two-Tier Foreground Detection: UsageStatsManager Baseline, Accessibility Optional

**Status:** Accepted — implementation deferred to M2 (Core Security Platform). The current build
remains accessibility-only until then. · **Date:** 2026-08-04 · **Source/authority:** Product
decision (project lead), 2026-08-04 — Google Play Store compliance requirement ·
**Supersedes:** ADR-013

## Context
Lock enforcement currently detects protected-app launches **only** through an
`AccessibilityService` consuming `TYPE_WINDOW_STATE_CHANGED` events (ADR-013). That single
dependency is the highest-severity item on the risk register (R-001): Google Play scrutinises
accessibility used for non-accessibility purposes and has removed app-lockers (R-001b), and
Android 13+ Restricted Settings blocks the grant for sideloaded installs (R-001a). A production
release therefore **requires the app to function without accessibility.**

## Decision
Adopt a **two-tier foreground-detection model**:

1. **Baseline (default, Play-compliant).** `UsageStatsManager` with the **Usage Access** special
   permission: a foreground service polls `queryEvents` for `MOVE_TO_FOREGROUND`. The lock UI is
   presented over the foreground app via the **overlay path** (`SYSTEM_ALERT_WINDOW`, "draw over
   other apps") — required because a background foreground-service cannot reliably launch the
   current Activity-based lock screen under Android 10+ background-activity-launch rules.
2. **Optional enhancement (opt-in at setup).** The existing `AccessibilityService`, offered as a
   "faster, more reliable locking" toggle for lower detection latency.

**Binding constraints:** the app **MUST** lock protected apps with accessibility **off**, using
the baseline. Accessibility **MUST** remain optional and **MUST NOT** be a precondition for
protection.

Honest scope note: "without accessibility" means "using Usage Access + overlay instead of
accessibility" — both Play-acceptable — not "with zero special permissions" (impossible for
app-locking on modern Android; `getRunningTasks` is gone).

## Alternatives considered
- **Keep accessibility as primary (ADR-013, status quo).** Rejected: R-001b is existential for a
  Play release, and R-001a blocks sideloaded installs.
- **Accessibility-only, declared/justified to Play.** Rejected: high, ongoing approval risk; Play
  actively removes app-lockers that use accessibility for enforcement.
- **UsageStatsManager-only (drop accessibility entirely).** Rejected: loses the low-latency
  event path; retaining accessibility as opt-in keeps that benefit for power users without
  gating the product on it.
- **Overlay-only.** Not viable: `SYSTEM_ALERT_WINDOW` is a presentation mechanism, not a
  detection source; it cannot identify the foreground app.

## Consequences
Positive:
- Play-compliant; the app functions without accessibility; resolves R-001a and R-001b as a
  design matter; accessibility becomes value-add rather than a hard dependency.
- Additive architecturally: the SDS §8.4 **Trigger Processor** already decouples detection from
  the Lock Coordinator, so this adds a second Trigger Processor implementation + a source-
  selection layer + the overlay presentation — **the lock engine itself does not change.**

Negative / costs:
- Baseline polling adds detection latency (poll interval) and battery cost that event-driven
  accessibility avoids.
- Adds two special-permission dependencies (Usage Access, overlay). Both are Play-acceptable, but
  both are user grants and Usage Access is itself Restricted-Settings-gated for sideloaded 13+
  installs — so R-001a is reduced, not eliminated, for the sideload channel.
- The lock-screen presentation must move to an overlay-capable path; the choice between
  overlay-drawn UI vs. an overlay-granted background-activity-launch exemption is **an open M2
  design point.**
- New health-monitoring surface: loss of Usage Access or overlay must be detected and surfaced
  (extends FR-179 beyond accessibility).

Requirement-baseline actions (recorded here; the SRS/NFR are client-received originals and are
not edited by this ADR — these are flagged for a governed baseline revision):
- **NFR-PERF-012** needs a **tiered** reading: the 250 ms *post-detection* enforcement budget is
  unchanged, but a per-mode **detection-delay** target must be added (baseline poll interval vs.
  near-immediate accessibility).
- **FR-179** scope extends to monitoring Usage Access and overlay, not only accessibility.
- Likely **new FR(s)** for: the two-tier detection-source selection, the optional-accessibility
  onboarding, and overlay-based lock presentation.

## Related requirements
FR-179, FR-231, FR-233, FR-242, FR-253 · NFR-PERF-012 · NFR-COMPY-002/003 · NFR-COMP-001 ·
risk R-001 (`docs/process/RISK_REGISTER.md`)

## Implementation status
**Not yet implemented.** Scheduled for M2 (Core Security Platform); the detection-approach line
in `MIGRATION_ASSESSMENT.md` §12 M2 becomes a directed build of this model rather than an open
evaluation. Until M2 lands it, the shipping build is accessibility-only and R-001 remains Open.
