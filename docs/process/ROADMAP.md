# Roadmap — 1.0.0 Release Line (M7–M10) · Legacy Milestones M0–M6

**Class:** living (GOVERNANCE.md §5.1) · **Owns:** the canonical milestone model — the active
1.0.0 line **M7–M10**, the legacy **M0–M6 ↔ IS-phase** mapping, and current milestone status
(GOVERNANCE.md §5.3).
**Re-cut 2026-08-14 per [ADR-019](../architecture/adr/ADR-019-version-split-baseline.md):** the
client approved the reduced 1.0.0 specification (`docs/v1.0.0/`, the active baseline); the full
spec became the 2.0.0 target (`docs/v2.0.0/`). Milestones M2–M6 were defined by FR ranges that are
now 2.0.0 scope, so they are **frozen as the deferred 2.0.0 lineage** — never re-meant — and the
1.0.0 execution line continues with **new identifiers M7–M10** (identifiers are never reused,
consistent with ADR/FR practice). This scope cut is also the principal mitigation of risk R-003
(enterprise-scale baseline vs solo cadence).

## Current status

| Milestone | Line | Status |
|---|---|---|
| M0 Baseline & governance | foundation | **Done** (2026-07-19) |
| M1 Foundation retrofit | foundation | **Done** (2026-08-25) — exit granted; tag `M1_Exit` (`60265b6`) |
| **M7 Detection & Enforcement Replacement** | 1.0.0 | **Current** — **WP0 complete (2026-08-30)**: platform validation done (decisive emulator A/B + FTL OEM sweep + Moto G no-regression + biometric-via-BAL matrix), **ADR-020/021 Accepted**, API-36 toolchain in; spike held to WP2; **WP1 (harness rework) next** |
| **M8 1.0.0 Product Conformance** | 1.0.0 | Planned |
| **M9 Hardening & Verification** | 1.0.0 | Planned |
| **M10 Release** | 1.0.0 | Planned |
| M2–M6 | 2.0.0 (deferred) | **Frozen** — see "Legacy milestones" below |

## The 1.0.0 line — M7–M10

The 1.0.0 baseline (`docs/v1.0.0/`) governs: SRS/NFR/TM/UI-UX/SDS/DDS v1.0.0. Detection scope is
fixed by ADR-013B: **Usage Access detection + mandatory "Display over other apps" overlay
presentation; no App Lock accessibility service in the release.** The current build is the inverse
(accessibility-only detection, Activity lock screen, CAMERA/intruder/vault built), which makes M7
the pivotal milestone: the app's core engine is replaced, not extended.

| Milestone | Scope | Gate / exit evidence |
|---|---|---|
| **M7 — Detection & Enforcement Replacement** ("the accessibility exit") | UsageStatsManager polling detector behind the SDS §8 trigger-processor seam; overlay (`SYSTEM_ALERT_WINDOW`) lock surface replacing the Activity path; **remove `AppDetectionService` + the accessibility manifest declaration** (clean — nothing shipped; the ADR-018 FQCN pin stays dormant-binding for the 2.0.0 return); fail-secure policy initialization (closes **R-005**); overlay presentation resists the relaunch race (closes **R-002** on re-validation); FR-179-lineage protection health re-pointed to Usage Access + overlay loss; **WP2 harness rework in-scope** (resumed-activity assertions and a11y rebind steps die with the old engine — window/overlay-based assertions replace them); **detailed in `M7_PLAN.md`; ADR-020/021 (Proposed) govern presentation/detection; targetSdk 36 adopted at WP0 (D0)** | Device + emulator matrix green on the **API-36 target** with the new engine; **R-002 per the `M7_PLAN.md` canonical standard** (emulator A/B decisive, Moto G no-regression, FTL OEM sweep for the residual); NFR-PERF-012 end-to-end transition→lock figure recorded; no accessibility declaration in the merged manifest |
| **M8 — 1.0.0 Product Conformance** | Build the v1.0.0 UI/UX-spec surfaces (onboarding SCR-001.., two-grant setup checklist, protection-health states, permission recovery, settings, help, destructive reset); **remove descoped features from the 1.0.0 line** (vault/, privacy/ intruder capture, their screens, CAMERA permission, DB tables — decision 2026-08-14: removal, not dormancy; git history + a pre-removal tag preserve the code for 2.0.0); schema conformance to v1.0.0 DDS; truthful protection-state model end-to-end | All v1.0.0 SRS retained-FR acceptance criteria demonstrable; manifest/permission audit = exactly the v1.0.0 permission matrix (UI/UX Appendix D); RTM rows updated with evidence |
| **M9 — Hardening & Verification** | v1.0.0 Threat Model controls (SC-*), tapjacking/obscured-input defense (THR-UI-001/003 — hostile *third-party* overlays), lockout + biometric failure accounting (resolves defect CR-005), bounded diagnostics, NFR benchmark set (v1.0.0 NFR-PERF-015), UI-accessibility audit (NFR-UX-007..014), Play-listing pre-check (data-safety, permission declarations) | v1.0.0 TM §8 verification activities (VA-*) executed and recorded; benchmark report filed; no Critical/High finding open |
| **M10 — Release** | Full acceptance campaign per v1.0.0 SRS §5 + TM §8; release engineering (signed build, store assets); store submission | Acceptance evidence complete; signed 1.0.0; Play review passed |

Gate-blocking risks by milestone (authoritative list: `RISK_REGISTER.md`): M7 — R-002, R-005
(R-004/R-006 already **Closed** in M1/WP7; M7 adds no schema — plan invariant); M10 — the
Usage-Access/overlay successor of R-001 (Play + sideload grant friction; distribution-model decision
still open).

## Foundation close-out (M1, unchanged identifiers)

**M1 closed 2026-08-25** — exit granted (`docs/reports/gates/2026-08-25_gate-m1-signoff.md`), tag
`M1_Exit` (`60265b6`); the FR-026..080 `review` burndown is done, NFR-COMP-001 verified, and R-004 and
R-006 **Closed** (WP7). The description below is retained as history.

M1 finished under its original plan (`M1_PLAN.md`) with WP7 **re-scoped 2026-08-14**: the legacy
plaintext→encrypted migration path is **deleted** rather than hardened (zero shipped installs — the
path only ever served dev devices; **R-006 closes by elimination**), and the WP7 fail-safe work
narrows to removing `fallbackToDestructiveMigration` (**R-004**). WP8 (instrumentation seed, GMD
matrix, defect-record convention, IS Phase-0 gate record) is unchanged; its gate record doubles as
the adoption record of the 1.0.0 baseline for execution purposes.

## Legacy milestones M2–M6 — frozen 2.0.0 lineage

Definitions frozen as written pre-split (content below unchanged from the 2026-08-10 extraction;
per-FR scope in `MIGRATION_ASSESSMENT.md` Phase 12). They resume, re-planned, when 2.0.0 work
begins — likely re-cut against the 2.0.0 baseline rather than executed verbatim.

| Milestone | IS phase | TSP §11.7 ordinal | Name / scope (condensed) |
|---|---|---|---|
| M2 | Phase 1 | 2 | Core Security Platform — security-service facade; two-tier detection (ADR-013A); a11y-health self-test (FR-231/242/253); key rotation (FR-317..319) — *the detection line is executed in 1.0.0 as M7 (baseline-only per ADR-013B); the optional a11y tier returns here* |
| M3 | Phase 2 | 3 | Core Application Features (MVP) — backup/restore (FR-196..205, 244/245); config service + feature flags (FR-236..238); onboarding/dashboard/diagnostics UI |
| M4 | Phase 3 | 4 | Automation & Intelligent Operations — schedules, Wi-Fi/Bluetooth/location rules, rule engine (FR-126..145) |
| M5 | Phase 4 | 5 | Production Hardening — observability (FR-276..300), resilience (FR-251..275), data lifecycle (FR-301..325), scalability (FR-326..350) |
| M6 | Phase 5+6 | 6+7 | Security Hardening → Release Readiness — secure-dev processes (FR-351..375), full verification, release acceptance |

**2.0.0 backlog additions** (beyond frozen M2–M6): vault + intruder-capture reinstatement (code
preserved at the M8 pre-removal tag); optional accessibility tier return (ADR-013B; ADR-018 FQCN
pin binds the service name); TAS revision for the post-1.0.0 architecture; review + promotion of
the 2.0.0 UI/UX draft (unreviewed, in gitignored scratch — ADR-019).

## Phase-citation rules

The IS/TSP phase numbering applies to the **2.0.0 lineage only** (the 1.0.0 doc set has no
Implementation Strategy): cite as `IS Phase N (Mx)` (GOVERNANCE.md §5.3). M7–M10 have no IS-phase
aliases — cite them bare (`M7`). Spec citations must be version-qualified where ambiguous —
`v1.0.0 SRS §8` (GOVERNANCE.md §5.3).

Open risks and their affected gates: `RISK_REGISTER.md` (authoritative — GOVERNANCE.md §5.2).
Requirement-level status: `rtm/rtm.csv`. A green regression run is evidence for one gate
dimension, not a gate pass by itself (TSP §11.15).
