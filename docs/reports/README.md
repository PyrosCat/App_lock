# Reports — Point-in-Time Evidence Records

Everything under `docs/reports/` is **evidence**: a dated record of something that was observed,
measured, reviewed, or executed. Reports differ from the rest of `docs/` in nature:

| | Specs (`srs/ nfr/ tas/ sds/`) | Plans (`process/ testing/`) | **Reports (here)** |
|---|---|---|---|
| Answers | what/how the product must be | what we intend to do | **what actually happened** |
| Lifecycle | governed, versioned | living, edited | **immutable once committed** |
| Referenced by | design & code | work execution | **RTM `verification` column, gate reviews** |

## Rules

1. **Immutable.** A committed report is never edited except typo fixes. Corrections or new
   observations = a **new** dated report that supersedes the old one (link it).
2. **Dated, host-tagged names:** `YYYY-MM-DD_<topic>[_<host>].md`
   — e.g. `2026-07-20_fleet-nucbox-g5.md`, `2026-07-25_gate-m1.md`,
   `2026-07-22_regression-baseline_nucbox.md`. Date prefix sorts chronologically; the host tag
   (when machine-specific) prevents cross-machine filename collisions — each fleet machine's
   assistant writes its own files and never edits another host's (git is the only shared
   channel; append-only files cannot merge-conflict).
3. **Every report states:** date, author/host, git commit it was produced against, and what it
   verifies or observes (FR/NFR IDs where applicable, so the RTM can point at it).
4. Raw CI output (test/lint artifacts) lives on GitHub Actions with short retention — reports
   here are the **curated, durable** conclusions, not dumps.
5. **Write it to be read standalone.** A report is read later by people or machines'
   assistants, who may not have the plan and ADRs open. Structure it, in order: a
   one-line-per-item **Headline** (verdict + the key number) up top; a short **"Context & terms"**
   section that expands the IDs and jargon the report leans on (risk/test IDs such as R-002 / OV-4,
   the metric vocabulary, work-package numbers, acronyms like GMD / FGS) so a technical reader
   follows the *meaning* without opening another doc — a `§N` cross-reference still names its
   document (GOVERNANCE.md §6); then **per-item sections**, each pairing raw evidence (exact counts,
   log lines, task/command names) with a plain-language verdict; and a closing **Disposition**
   (impact on requirements / risks / ADRs) + **Follow-ups**. Keep sentences readable — precision,
   not density. Precedent: `campaigns/2026-08-28_m7-wp0-emulator_nucbox-g5.md`.

## Categories (subfolders created on first use — git doesn't track empty dirs)

| Subfolder | Holds | Mandated by / first needed |
|---|---|---|
| `fleet/` | Machine/environment status & readiness (accel-check, build smoke, SDK/AVD inventory per host) | ADR-014 fleet; **first report: NucBox G5 setup outcome** |
| `campaigns/` | Executed test-run records: WP2 regression-harness runs, API-matrix runs, E2E campaigns | M1/WP2; RTM evidence |
| `gates/` | Phase-gate review records (M1..M6 / IS phase gates), architecture-conformance reviews | Implementation Strategy §5; first at M1/WP8 |
| `benchmarks/` | Performance/resource measurements vs NFR targets (startup, lock latency, queries) | NFR-PERF-015; M5 |
| `security/` | Security reviews, dependency reviews, threat-model reviews, vulnerability reports | IS Phase 5, FR-366; M2 onward |
| `release/` | Release checklists, readiness verifications, the production acceptance report | FR-248/249/250; M6 |

## Current fleet state (index only — one line per host, link the latest report)

| Host | Latest report | State |
|---|---|---|
| 2012 i7 (primary dev) | — | Pixel_5 API 30 x86 emulator; WP1 CI baseline produced here |
| NucBox G5 (Win 11, N-series) | [2026-08-30](campaigns/2026-08-30_m7-wp0-biometric-matrix_nucbox-g5.md) | Biometric-via-BAL **PASS** on api30/33/36 (Item A) + real-match on google_apis api33 (Item B) — closes the ADR-020 platform residual. |
| Moto G 2025 (physical) | [2026-08-31](campaigns/2026-08-31_m7-wp1-harness_moto-g-2025.md) | Connected (USB adb via 2012 host). M7 WP1 reworked harness **§11 gate PASS** on Android 15: OV-4 ABSENT=0 (TOP=250/250) ×2 + the missing-grant negative control. Now appops (no a11y). |
| Firebase Test Lab (cloud, physical) | [2026-08-29](campaigns/2026-08-29_m7-wp0-ftl-sweep_firebase-test-lab.md) | M7 WP0 OEM/OS residual sweep **PASS**: ABSENT=0 across Motorola / Samsung One UI / OnePlus / Pixel, API 30–36 (incl. real API-36). b0q One UI BEHIND borderline (5%→1.7%), open for WP2. Spike overlay; authoritative re-run at WP6 |

Keep this table to one line per host — details belong in the dated reports it links to.

## Historical note

`docs/testing/PHASE3_TEST_PLAN.md` predates this structure and fuses plan + results in one file;
it stays as-is (immutable record). From WP2 onward: plans in `docs/testing/`, execution results
here in `campaigns/`.
