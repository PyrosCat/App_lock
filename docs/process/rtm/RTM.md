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

## Status vocabulary and maintenance rules

**Governed by [`../GOVERNANCE.md`](../GOVERNANCE.md) §1** — the authoritative source for the
status vocabulary (closed set: `not-started`, `in-progress`, `partial`, `implemented`,
`implemented-verified`, `invalidated`, `descoped-v1`, plus the legacy seed status `review`),
update triggers, prohibitions, and wording rules. Read that before editing `rtm.csv`.

Seed history: populated 2026-07-19 from MIGRATION_ASSESSMENT.md Phase 5; distribution at seed
time: 23 implemented-verified · 9 partial · 115 review · 399 not-started.
