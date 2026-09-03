# Project Schedule

Version: 1.0

Date: 2026-09-03

Applies to: Milestones M0, M1, M7, M8, M9, and M10

## 1. Purpose

This schedule records estimated effort and final actual time for the Version 1.0.0 release line.
It covers foundation milestones M0 and M1 and active release milestones M7 through M10.

`ROADMAP.md` remains authoritative for scope, status, and gate criteria. This file owns only the
time estimate and actual-time record.

## 2. Estimation basis

Estimates use developer-days. One developer-day is one normal working day for one developer,
approximately 6 to 8 hours of productive project work. Ranges account for implementation, tests,
documentation, review, planning, and required device or build setup unless a table states otherwise.

The following terms apply:

- Estimated effort is the planned number of developer-days.
- Actual elapsed (calendar) is the difference between the recorded start and completion dates.
- `TBD` or `TBD at completion` means a final value must not be entered while work is active.

This schedule tracks estimated effort (developer-days) against actual elapsed calendar time only; it
does not record labor effort, which a solo cadence cannot log reliably without added friction.
**Calendar elapsed is not comparable to the effort estimate** and must not be read as an actual-effort
figure: elapsed time includes the gaps between work sessions (M1 spanned 37 calendar days for an
estimated 15 to 20 effort-days). Milestones may overlap, and values are not inferred from commit count.
Earlier focused-block estimates are normalized at one block per developer-day.

## 3. Milestone schedule

Table 1. Milestone estimates and actuals

| Milestone | Scope | Status | Estimated effort | Start | Completion or target | Actual elapsed (calendar) |
|---|---|---|---:|---|---|---:|
| M0 | Baseline and governance | Complete | 5 developer-days | 2026-07-19 | 2026-07-21 | 2 calendar days |
| M1 | Foundation retrofit | Complete | 15 to 20 developer-days | 2026-07-19 | 2026-08-25 | 37 calendar days |
| M7 | Detection and enforcement replacement | In progress | 22 to 30 developer-days | 2026-08-25 | TBD | TBD at completion |
| M8 | Version 1.0.0 product conformance | Planned | 18 to 28 developer-days, provisional | TBD | TBD | TBD at completion |
| M9 | Hardening and verification | Planned | 12 to 18 developer-days, provisional | TBD | TBD | TBD at completion |
| M10 | Release | Planned | 6 to 9 developer-days, provisional | TBD | TBD | TBD at completion |
| Total | Version 1.0.0 line | Active | 78 to 110 developer-days | 2026-07-19 | TBD | TBD at M10 completion |

The M0 and M1 estimates preserve the original `MIGRATION_ASSESSMENT.md` ranges of one and three to
four solo-developer weeks, using five working days per week. The M8 through M10 values are planning
ranges and must be recalibrated at the M7 gate.

## 4. Work-package estimates

### 4.1 M1 foundation retrofit

The M1 plan estimated work in grouped packages. These package values cover execution only; the
milestone estimate in Table 1 is the controlling whole-milestone estimate.

Table 2. M1 work-package estimates

| Work package | Scope | Estimated effort | Status |
|---|---|---:|---|
| WP1 and WP3 | CI baseline freeze and static analysis | 1 developer-day | Complete |
| WP2 | Device regression harness and fleet onboarding | 1 to 2 developer-days | Complete |
| WP4 | Build environments and dependency governance | 1 developer-day | Complete |
| WP5 | Hilt migration | 1 to 2 developer-days | Complete |
| WP6 | Package realignment | 1 developer-day | Complete |
| WP7 | Database migration hardening | 1 developer-day | Complete |
| WP8 and gate | Instrumentation seed, device matrix, and close-out | 1 developer-day | Complete |
| Execution subtotal | Grouped package estimate from `M1_PLAN.md` | 7 to 9 developer-days | Complete |

### 4.2 M7 detection and enforcement replacement

The M7 package estimates cover execution. The milestone estimate also includes a 7 to 9
developer-day allowance for planning, design decisions, fleet setup, and CI setup. Completed
work packages are shown at their estimate-at-completion; for example, WP0's plan estimate
(`M7_PLAN.md` §15) was 1 to 2 developer-days against an actual of about 5, which is the main reason
these milestone estimates sit above the plan's per-package figures.

Table 3. M7 work-package estimates and actuals

| Work package | Scope | Estimated effort | Status | Start | Completion | Actual elapsed (calendar) |
|---|---|---:|---|---|---|---:|
| WP0 | Platform validation and design decisions | 5 developer-days | Complete | 2026-08-25 | 2026-08-30 | 5 calendar days |
| WP1 | Overlay-aware and app-operations-aware test harness | 1 to 2 developer-days | Complete | 2026-08-31 | 2026-09-01 | 1 calendar day |
| WP2 | Overlay lock and request identity | 4 to 6 developer-days | In progress | 2026-09-02 | TBD | TBD at completion |
| WP3 | UsageStats detection, readiness, and performance measurement | 2 to 4 developer-days | Planned | TBD | TBD | TBD at completion |
| WP4 | Protection-health state | 1 to 2 developer-days | Planned | TBD | TBD | TBD at completion |
| WP5 | Accessibility cutover | 1 developer-day | Planned | TBD | TBD | TBD at completion |
| WP6 | Verification matrix and M7 gate | 1 developer-day | Planned | TBD | TBD | TBD at completion |
| Execution subtotal | WP0 through WP6 | 15 to 21 developer-days | In progress | 2026-08-25 | TBD | TBD at completion |
| Planning and setup allowance | Design, fleet, toolchain, and CI work | 7 to 9 developer-days | In progress | 2026-08-20 | TBD | TBD at completion |
| M7 total | Execution plus planning and setup | 22 to 30 developer-days | In progress | 2026-08-25 | TBD | TBD at completion |

Detailed work-package estimates for M8 through M10 must be added when their plans are approved.
Until then, the provisional milestone ranges in Table 1 are the controlling estimates.

## 5. Evidence and update rules

Table 4. Current date evidence

| Boundary | Evidence |
|---|---|
| M0 baseline | `f7ffe29`, dated 2026-07-19 |
| M0 fleet setup | `c9bea71`, dated 2026-07-21 |
| M1 first work package | `8ca489a`, dated 2026-07-19 |
| M1 completion | Tag `M1_Exit` and commit `60265b6`, dated 2026-08-25 |
| M7 plan baseline | `7a490cd`, dated 2026-08-25 |
| M7 WP0 completion | `b692d85`, dated 2026-08-30 |
| M7 WP1 completion | `d087301`, dated 2026-09-01 |
| M7 WP2 start | Decision record ending at `9de84f5`, dated 2026-09-02 |

Apply these rules whenever this schedule changes:

1. Record the estimate when a milestone or work package is approved.
2. Record the start date when implementation or governed planning begins.
3. While work is active, keep final actual fields as `TBD at completion`.
4. At completion, record the completion date and actual elapsed (calendar) time.
5. Explain material estimate changes in the revision history or the applicable plan.
6. Re-estimate M8 through M10 at the M7 gate using M7 actuals and judgment (no labor-effort record is kept).
7. Keep status and scope synchronized with `ROADMAP.md` without duplicating its detail.

## References

- `docs/process/ROADMAP.md`
- `docs/process/M1_PLAN.md`
- `docs/process/M7_PLAN.md`
- `docs/process/GOVERNANCE.md`
- `docs/process/MIGRATION_ASSESSMENT.md`

## Revision history

- 2026-09-03: Initial version.