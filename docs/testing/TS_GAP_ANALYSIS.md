# Test Specification Gap Analysis — TS Volume I vs. Current State

**Filed:** 2026-08-03 · **Baseline:** commit `9714931` (TS filed + md mirrors) · **Status:** living
document — update at each phase gate and whenever a new TS volume arrives.

> **Annotation 2026-08-14 (ADR-019 version split):** spec paths cited below moved — `docs/testing/tsp/`
> is now `docs/v2.0.0/tsp/`, `docs/design/dds/` is now `docs/v2.0.0/dds/`. The TSP belongs to the
> full-spec (2.0.0) lineage; the active baseline is the client-approved 1.0.0 set at `docs/v1.0.0/`,
> and the 1.0.0 execution line is milestones M7–M10 (`ROADMAP.md`). Original text below is unchanged.

Maps the requirements of the client-delivered Test Specification Volume I (Test Strategy &
Governance, `docs/testing/tsp/`, filed 2026-08-03) against what the project actually implements
today, and assigns each gap a landing zone in the migration plan
(`MIGRATION_ASSESSMENT.md` Phase 12, `M1_PLAN.md` work packages). Sections cited as
"TS §N" refer to TS Volume I.

## 1. TS delivery state (the spec itself has gaps)

| Volume | Content | Delivered? |
|---|---|---|
| V-I Test Strategy & Governance | sections 1–15 | **Yes — complete** |
| V-II Functional Test Specification | section 1 only (Functional Test Design) | **Partial** |
| V-III Non-Functional Test Specification | — | No |
| V-IV Security Test Specification | — | No |
| V-V Integration, System & Release Testing | — | No |
| V-VI Test Management & Traceability | — | No |

Several V-I sections defer their operational detail to the missing volumes (defect workflow →
V-VI §12.33; security tests → V-IV §13.18; release qualification → V-V §13.29; risk/requirement
traceability → V-VI §13.30/§15.38). Detailed test-case design is therefore **blocked on client
delivery** of V-II (remaining sections) through V-VI.

**Document defects to raise with the client:**
1. TS §5.3 governance table: the role-name column is empty (merged/lost cells; roles are
   recoverable from §5.5 — Lead Developer / Software Architect and Quality & Security Engineer).
2. Phase numbering inconsistency: §6.3 numbers the lifecycle Phase 0–6; §11.7 lists the same
   seven phases numbered 1–7.

## 2. Referenced documents that do not exist yet

TS V-I assumes a **Threat Model** and **Secure Coding Standard (SCS)** as sibling governing
documents (§1.5, §3.5, §13.5). Neither exists; both are already planned as **M2 deliverables**
(MIGRATION_ASSESSMENT Phase 12, flagged P1 in Phase 10). The DDS arrived 2026-08-03
(`docs/design/dds/`), closing that reference.

**Plan-impact note:** M3's deliverable "Test Specification v1" (MIGRATION_ASSESSMENT Phase 12)
predates the client delivery of this TS and is superseded by it. M3's testing scope should be
reread as: *adopt the client TS, derive concrete test cases from V-II, and land the first
instrumentation wave*. To be reflected in the M3 plan when it is drafted.

## 3. Where the project already complies (governance layer)

| TS requirement | Existing mechanism | Assessment |
|---|---|---|
| Continuous verification; verified requirements return to unverified on change (§4.4, §11.27, §15.8) | GOVERNANCE.md §1.2–1.3: `invalidated` status, same-commit RTM updates, evidence-pointer rule | **Aligned** — vocabulary maps 1:1 (§15.8 "Regression Required" ≈ `invalidated`) |
| Bidirectional traceability via RTM (§4.8, §7.16) | `process/rtm/rtm.csv` + RTM.md vocabulary; verification column carries evidence pointers | **Aligned** at requirement level; per-test-case tracing awaits V-II/V-VI (gap G-11) |
| Evidence-based verification, config identification (§4.17–4.18, §14, §15.22) | `docs/reports/` conventions: dated immutable records, commit-pinned, device/API identified | **Aligned** for what is executed today |
| Phase-gated lifecycle mirroring the Implementation Strategy (§6, §11.22) | M0–M6 milestones = IS Phases 0–6; gate reviews with RTM burn-down (GOVERNANCE §1.3 rule 5); M1 gate record planned in WP8 | **Aligned** in structure; TS Phase 0 ≈ M1, Phase 1 ≈ M2, … Phase 5–6 ≈ M6 |
| Regression as a permanent, expanding asset (§7.11, §8.6) | WP2 e2e harness (`scripts/e2e/`: smoke_core, ov3, ov4, f3) + CI unit tests; validated on NucBox API 33 and Moto G Android 15 with campaign reports | **Aligned (seed)** — expansion obligation stands |
| Risk-based testing (§13) | `process/RISK_REGISTER.md` (R-001); harness prioritizes the historical bypasses (F3/F4) | **Partial** — register exists; no systematic per-requirement risk classification driving test depth yet |
| Two-role governance with peer review (§5) | Solo developer + AI assistants; §5.1 explicitly permits one individual holding multiple roles with peer review "whenever practical" | **Acceptable** — record the role mapping in the M1 gate review |
| Configuration management (§14) | git, version catalog, CI, commit-pinned reports, GOVERNANCE §3 | **Largely aligned**; test-data versioning missing (G-05) |

## 4. Gap register

Severity: **P1** = blocks a near-term gate or TS mandate with no plan coverage;
**P2** = required, planned or partially planned; **P3** = required later, correctly sequenced.

| ID | Gap (TS ref) | Current state | Pri | Landing zone |
|---|---|---|---|---|
| G-01 | Static analysis beyond Android lint: detekt/ktlint, architecture rules (§7.3) | lint only in CI | P2 | **M1/WP3** (planned: detekt + ktlint + Konsist) |
| G-02 | Dependency scanning, secret scanning, dependency audit (§7.3, §14.7) | none | P2 | **M1/WP4** (Dependabot + license inventory); full security scanning M6 |
| G-03 | Component/integration/instrumentation testing — `app/src/androidTest` does not exist (§7.5–7.7) | none | P2 | **M1/WP8** seed suite + GMD matrix; expansion M3 |
| G-04 | Defect management: lifecycle, states, severity/priority, records, escape analysis (§12) | **no defect tracker or record convention at all** | **P1** | unplanned — needs a decision (lightweight: GitHub Issues with the §12.6 state set + §12.10 severities, or a `docs/testing/defects/` record convention). Decide at M1 gate |
| G-05 | Test data management: versioned synthetic/boundary/corrupted/migration datasets (§10) | ad-hoc setup inside e2e scripts | P2 | seeds in **M1/WP7** (deliberate-failure DB drill) + **WP8** fixtures; framework decision M3 |
| G-06 | Environment strategy: device matrix, environment baselines, drift control (§9) | fleet exists (NucBox emulator host, Moto G hardware) + DEV_ENVIRONMENT_SETUP.md; no formal matrix; NucBox API-matrix run still owed | P2 | **M1/WP8** (GMD API 30+35 in CI, full matrix on NucBox per D4); baseline table to add to the M1 gate record |
| G-07 | Non-functional testing: performance, battery, endurance, resource (§7.9, §8.11–8.14) | none | P3 | **M5** (NFR benchmark harness); battery specifically gates **M4** |
| G-08 | Security testing depth: Keystore, tamper/root detection, fuzzing, penetration (§7.10, §8.23–8.25) | overlay-defense checks only (ov4/f3) | P2 | **M2** (security suite in CI, threat-model-driven) → **M6** (pen test, vulnerability assessment) |
| G-09 | E2E workflow coverage: vault, backup/restore, scheduling, automation, profiles (§7.8) | lock/unlock/relock + vault import/export only | P3 | grows with features: **M3** (backup, config), **M4** (automation) |
| G-10 | Metrics & gate reporting: §15.43 minimum reporting set at each gate | narrative campaign reports; RTM statuses | P2 | **M1/WP8** gate record — adopt §15.43 as the gate-record checklist template |
| G-11 | Test-case-level traceability: test case IDs ↔ requirements ↔ executions (§7.16, §15.7) | RTM traces requirement → evidence report; no test-case register | P3 | blocked on TS V-II/V-VI delivery; revisit on arrival |
| G-12 | TS volumes V-II (rest)–V-VI not delivered (§1.6) | see §1 above | **P1** | client dependency — request delivery schedule; detailed test design blocked |

## 5. Current test inventory (evidence base for §3–§4)

- **Unit tests:** 7 classes / 67 tests (`app/src/test/`) — security primitives (PIN + Argon2
  hashing, lockout), lock session, lock policy, intruder policy, vault file types. Run in CI.
- **CI:** `.github/workflows/ci.yml` — unit tests, debug + release (R8) builds, lint; reports
  and APKs archived (14 days).
- **Device regression harness:** `scripts/e2e/` (4 checks + runner + device setup), validated
  2/2 green on NucBox G5 API 33 (2026-07-22 report) and Moto G 2025 / Android 15
  (2026-07-23 report).
- **Traceability:** RTM (375 FR + 171 NFR rows), governed by GOVERNANCE.md §1.
- **Risk:** RISK_REGISTER.md (R-001 accessibility-detection, severity High).

## 6. Standing rule derived from this analysis

Until superseded by a fuller TS adoption plan: **each phase-gate review must re-walk this
document**, close gaps whose landing zone that phase owns, and re-grade the rest. New TS volume
deliveries trigger an update of §1 and a re-derivation of §4.

*Docs-only analysis: no RTM rows change with this commit (GOVERNANCE.md §1.4).*
