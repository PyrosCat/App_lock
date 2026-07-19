# Requirements Traceability Matrix — Guide

`rtm.csv` is the authoritative status ledger for all **546** baseline requirements
(375 FRs from `docs/srs/`, 171 NFRs from `docs/nfr/`). It replaces the archived
"Functional Requirements Completion Summary".

## Columns

| Column | Meaning |
|---|---|
| `id` | FR-nnn / NFR-CAT-nnn — **new-baseline meanings only** (for FR-226..250 history see `docs/archive/FR-226-250-RENUMBERING-NOTICE.md`) |
| `title` | Requirement title as extracted from the source document |
| `section` | Source document section |
| `type` | FR or NFR |
| `priority` | Priority stated in the source doc (sections 13–18 and NFRs mostly don't state one — blank) |
| `status` | See vocabulary below |
| `implementation` | Package/file pointers once implemented |
| `verification` | Evidence pointer (test-plan campaign, CI job, benchmark record) |
| `notes` | Free text — deferrals, cross-references, partial-status explanation |

## Status vocabulary

- **`implemented-verified`** — built AND evidenced by a recorded validation campaign
  (currently: Phase 2/3 campaigns in `docs/testing/PHASE3_TEST_PLAN.md`). Used strictly;
  never assigned without an evidence pointer.
- **`implemented`** — built, no campaign evidence yet.
- **`partial`** — meaningfully but incompletely satisfied; `notes` says what's missing.
- **`review`** — functionality exists in this area but per-FR classification hasn't been done;
  resolved at the phase-gate reviews named in `notes` (M1/M2/M3).
- **`not-started`** — no implementation.
- **`descoped-v1`** — formally deferred beyond v1.0.0 with a decision reference.

## Maintenance rules

1. Every change that implements, extends, or verifies a requirement updates its row in the same
   commit (per the changelog workflow).
2. Phase-gate reviews (Implementation Strategy §5) burn down the `review` backlog for their
   scope: M1 → lock engine + protected apps (FR-026..080), M2 → security (FR-161..180),
   M3 → vault/settings/notifications remainder (FR-106..125, 146..160, 181..195).
3. Statuses only move to `implemented-verified` with a `verification` pointer.
4. Seeded 2026-07-19 from MIGRATION_ASSESSMENT.md Phase 5; distribution at seed time:
   23 implemented-verified · 9 partial · 115 review · 399 not-started.
