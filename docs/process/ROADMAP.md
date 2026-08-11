# Roadmap — Migration Milestones M0–M6

**Class:** living (GOVERNANCE.md §5.1) · **Owns:** the canonical milestone ↔ IS-phase mapping
and current milestone status (GOVERNANCE.md §5.3).
**Provenance:** extracted 2026-08-10 from `MIGRATION_ASSESSMENT.md` Phase 12 (frozen
2026-07-19 snapshot), which retains the original scoping rationale and effort estimates.
Update this file when milestone/WP status changes or a gate record lands; per-milestone
detail lives in the milestone plans (`M0_PLAN.md`, `M1_PLAN.md`, …).

## Canonical phase mapping

The Implementation Strategy numbers its phases **0–6**; the Test Specification lists the
same sequence **1–7** (TSP §11.7). To avoid the off-by-one, cite phases as
`IS Phase N (Mx)` (GOVERNANCE.md §5.3).

| Milestone | IS phase | TSP §11.7 ordinal | Name |
|---|---|---|---|
| M0 | — (pre-phase) | — | Baseline & governance |
| M1 | Phase 0 | 1 | Foundation (retrofit) |
| M2 | Phase 1 | 2 | Core Security Platform |
| M3 | Phase 2 | 3 | Core Application Features (MVP) |
| M4 | Phase 3 | 4 | Automation & Intelligent Operations |
| M5 | Phase 4 | 5 | Production Hardening |
| M6 | Phase 5 + 6 | 6 + 7 | Security Hardening → Release Readiness |

## Milestone status

| Milestone | Status | Scope (condensed) | Gate / evidence |
|---|---|---|---|
| **M0** | **Done** (2026-07-19) | docs restructure + archive with FR-226..250 renumbering notice, SRS dedup, RTM seed (546 rows), ADR-001..010 backfill + ADR-011..014 | `M0_PLAN.md`; changelog 2026-07-19 |
| **M1** | **Current** — WP1–WP5 done, WP6 next | WP1 CI freeze ✓ · WP2 device regression harness ✓ · WP3 detekt/ktlint/Konsist ✓ · WP4 build variants + dependency governance ✓ (ADR-017) · WP5 Hilt replacing `Graph` ✓ (ADR-015; device gate green on real hardware **and** emulator matrix) · WP6 package realignment (ADR-018 FQCN pins) · WP7 remove destructive-migration fallback (R-004) · WP8 instrumentation seed + GMD matrix + RTM batch + **defect-record convention decision** (TS_GAP G-04 — holds the 2026-08-11 review defects CR-005/CR-006, RISK_REGISTER §Defects) + **IS Phase-0 (M1) gate record** | `M1_PLAN.md`; WP5 evidence: `reports/campaigns/2026-08-08_wp5-harness_moto-g-2025.md` + `2026-08-09_wp5-matrix_nucbox-g5.md`; gate record owed at WP8 |
| **M2** | Planned | Centralized Security Service facade; **two-tier detection per ADR-013A** (UsageStats + overlay baseline, optional accessibility); accessibility-health self-test (FR-231/242/253); key-rotation design (FR-317..319); threat-model-driven security tests in CI | IS Phase-1 (M2) gate review. **Gate-blocking risks: R-001, R-002 — both High** (TM §14.10; see `RISK_REGISTER.md`) |
| **M3** | Planned | Backup/restore (FR-196..205, 244/245); Configuration Service + feature flags (FR-236..238); onboarding/dashboard/diagnostics UI; navigation coordinator (completes MVVM refactor); instrumentation growth | All MVP FRs + functional regression pass (TSP §11.10/§11.19) |
| **M4** | Planned | Schedules, Wi-Fi/Bluetooth/location rules, rule engine (FR-141 priority/override), automation logging; battery impact assessment | Battery + reliability objectives (TSP §11.20); Bluetooth needs real hardware (Moto G) |
| **M5** | Planned | Observability platform (FR-276..300); resilience framework (FR-251..275); data lifecycle (FR-301..325); scalability (FR-326..350); NFR benchmark harness | Performance + reliability objectives demonstrated |
| **M6** | Planned | Secure-development processes (FR-351..375); dependency/vulnerability review; full verification campaign; release checklist + readiness + acceptance (FR-248..250); signed v1.0.0 | Critical **and** High vulnerabilities resolved (TSP §11.18); Play compliance review (R-001b); production approval |

Open risks and their affected gates: `RISK_REGISTER.md` (authoritative — GOVERNANCE.md §5.2).
Requirement-level status: `rtm/rtm.csv`. Phase-gate evaluation dimensions: Implementation
Strategy §5 / TSP §11.22 — a green regression run is evidence for one dimension, not a gate
pass by itself (TSP §11.15).
