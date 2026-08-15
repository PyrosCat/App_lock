# ADR-013B — Version 1.0.0 Detection Scope: Overlay-Presented UsageStats Baseline, Accessibility Deferred to 2.0.0

**Status:** Accepted · **Date:** 2026-08-14 · **Source/authority:** Product decision (project
lead), 2026-08-14 — year-end 1.0.0 scope narrowing (smallest verifiable product; full feature
set targeted for 2.0.0) · **Supersedes:** ADR-013A

**Amendment 2026-08-15:** milestone pointers below updated **M2 → M7** following the ADR-019
version-split re-cut — the 1.0.0 detection/enforcement replacement is now milestone **M7**
(`ROADMAP.md`); the old M2–M6 are frozen as the 2.0.0 lineage. Factual correction only; the
decision is unchanged. (Historical mentions of ADR-013A's *original* M2 scheduling are retained
as history.)

## Context
ADR-013A adopted a **two-tier** foreground-detection model — a `UsageStatsManager` + overlay
(`SYSTEM_ALERT_WINDOW`) Play-compliant **baseline** plus an **optional** opt-in
`AccessibilityService` low-latency enhancement — with the binding constraint that accessibility
**remain optional** and the app **lock without it**. Implementation was scheduled for M2; the
shipping build is still accessibility-only.

The programme is now scoped to a **year-end 1.0.0 release**: the smallest complete product that
can make and verify the protection promise, with the fuller feature set moved to 2.0.0 (see
`New docs/1.0.0 Draft` and `New docs/2.0.0 Planned`). That reframing forces a decision ADR-013A
did not make: whether the **optional accessibility tier** is part of 1.0.0 or deferred. Keeping it
in 1.0.0 re-admits the accessibility exposure ADR-013A was written to escape — Google Play
scrutiny of app-lockers using `AccessibilityService` (risk R-001b) and Android 13+ Restricted
Settings on sideloaded grants (R-001a) — into the first release, enlarging its scope and its
compliance surface for a benefit (lower detection latency) that a minimum-viable release does not
require.

## Decision
For the **1.0.0 release scope**, foreground detection and lock enforcement use the **baseline
tier only**:

1. **Detection.** `UsageStatsManager` via the **Usage Access** special permission is the **sole**
   foreground-detection source. No App Lock `AccessibilityService` is declared, requested, offered,
   or required in 1.0.0.
2. **Presentation.** The lock surface is a drawn **overlay** via the **`SYSTEM_ALERT_WINDOW`
   ("draw over other apps")** permission. In 1.0.0 the overlay permission is **mandatory** — it is
   the **sole** lock-presentation mechanism and a **precondition for a "Protected" health state.**

The **optional `AccessibilityService` enhancement** from ADR-013A (tier 2) is **deferred beyond
1.0.0** and targeted for **2.0.0**. The two-tier target itself is **not abandoned** — it is
**re-sequenced**: 1.0.0 ships the baseline; the optional low-latency tier returns in 2.0.0.

**Binding constraints (1.0.0):**
- The app **MUST** detect and lock protected apps using Usage Access + overlay, with **no
  accessibility component present.**
- The overlay permission is **REQUIRED, not optional.** Without it, detection cannot be enforced —
  a background foreground-service cannot reliably launch the lock surface under Android 10+
  background-activity-launch (BAL) rules — so protection **MUST** report *Action required* /
  not-*Protected* until it is granted. "Usage Access alone" is a detect-but-cannot-enforce
  configuration and is not a valid protected state.

Honest scope note: dropping accessibility does **not** mean "zero special permissions." 1.0.0
depends on **two** special-access grants — Usage Access (detection) and the overlay permission
(presentation) — both Play-acceptable. This is the ADR-013A baseline made mandatory and sole, not
a new mechanism.

## TAS deviation (recorded here)
TAS Parts 2–3 enumerate *Accessibility* / *Accessibility Services (where applicable)* among the
platform components. 1.0.0 **deviates**: no accessibility component ships. Per GOVERNANCE §2.2
(deviations from the TAS/SDS baseline require a documented, approved ADR), **this ADR is that
record** — a standalone TAS revision is deferred, and accessibility re-enters the architecture in
2.0.0. **ADR-018's permanent FQCN pin** on `com.applock.applocker.service.AppDetectionService`
remains binding for that return (the service reappears at the same FQCN in 2.0.0); because **no
production install exists yet**, removing the accessibility service from the 1.0.0 build strands
no externally persisted grant. The device-admin half (`UninstallProtectionReceiver`) is unaffected
by this ADR.

## Alternatives considered
- **Keep ADR-013A as-is (optional accessibility in 1.0.0).** Rejected: enlarges the year-end
  release and re-admits the R-001a/R-001b accessibility exposure into 1.0.0 for a latency benefit
  an MVP does not need.
- **Amend ADR-013A's implementation-status line only (no supersession).** Rejected: deferring the
  accessibility tier changes the decision's *release composition*, and the overlay's status changes
  from *optional presentation option* to *mandatory sole mechanism* — a material change to the
  decision, which GOVERNANCE §2.2 makes a supersession, not an amendment.
- **Accessibility-only for 1.0.0 (current build).** Rejected: R-001 (existential Play + sideload
  risk) is exactly what ADR-013A moved off; reverting is not an option for a release.
- **Overlay-only detection.** Not viable: `SYSTEM_ALERT_WINDOW` is a presentation mechanism, not a
  detection source; it cannot identify the foreground app (unchanged from ADR-013A).

## Consequences
Positive:
- Smallest verifiable 1.0.0 detection/enforcement surface.
- **R-001a/R-001b no longer apply to 1.0.0** — there is no accessibility service to be scrutinised
  by Play or blocked by Restricted Settings in the release (they move with the tier to 2.0.0).
- The **mandatory overlay is also the R-002 remediation.** A drawn `SYSTEM_ALERT_WINDOW` sits on
  top of the foreground task rather than as a slide-over-able `noHistory` Activity, so it resists
  the rapid-relaunch window-ordering race that the Activity-launch presentation loses (R-002
  planned action #2). Making it mandatory converts that "open presentation choice" into
  committed 1.0.0 scope.
- **Single detection mode simplifies NFR-PERF-012** — no baseline-vs-accessibility tiering; one
  polling-latency profile to state and verify.

Negative / costs:
- 1.0.0 loses the low-latency accessibility event path; end-to-end latency becomes *poll interval
  + the 250 ms post-detection enforcement budget*. **NFR-PERF-012 needs an explicit poll-interval
  detection-delay target** — the 1.0.0 draft NFR currently starts its clock at "Usage Access
  reports the transition" and omits the poll-interval component (the target ADR-013A said "must be
  added"). This is a 1.0.0-draft correction, not an optional nicety.
- **R-001a is reduced but not eliminated — it shifts, it does not vanish.** Usage Access is itself
  Restricted-Settings-gated for sideloaded Android 13+ installs; the overlay grant is a second
  mandatory user grant. The 1.0.0 exposure is "two special grants the user must complete," not
  "zero."
- The current **accessibility-only build must be replaced** by the Usage Access + overlay baseline
  before 1.0.0 (the M7 baseline build stays on the critical path; only the optional accessibility
  tier leaves it, deferred to 2.0.0).
- Protection-health monitoring (FR-179) monitors **Usage Access + overlay** loss in 1.0.0, not
  accessibility.

## Related requirements
FR-026 (detection = Usage Access, sole baseline) · FR-027 / FR-028 / FR-044 (overlay presentation
and lock-presentation capability verification) · FR-179 (health monitoring — Usage Access + overlay)
· NFR-PERF-012 (single polling-latency target; add the poll-interval detection-delay figure) ·
NFR-COMPY-002/003, NFR-COMP-001.
**Deferred beyond 1.0.0 (RTM → `descoped-v1` in this change):** FR-043 (Accessibility Event
Monitoring), FR-045 (Accessibility Permission Verification), FR-253 (Accessibility Service
Recovery).
Risks: **R-001** (accessibility exposure — leaves 1.0.0 with the deferred tier; register updated
2026-08-14), **R-002** (rapid-relaunch race — overlay mandate is the committed remediation).
Related ADRs: **supersedes ADR-013A** (lineage ADR-013 → 013A → 013B); **ADR-018** (FQCN pin,
still binding for the 2.0.0 accessibility return); **ADR-014** (supported API range).

## Implementation status
**Not yet implemented.** The 1.0.0 baseline (Usage Access + overlay) is the M7 build; the current
shipping build remains accessibility-only until then. This ADR fixes the **1.0.0 detection scope**;
the `New docs/1.0.0 Draft` SRS/NFR/SDS/Threat-Model revision (a proposal, not yet adopted) is being
aligned to it — including naming the overlay permission explicitly and adding the NFR-PERF-012
poll-interval target.
