# M0 Plan — Baseline & Governance

Migration phase M0 of `MIGRATION_ASSESSMENT.md` (§12). Objective: adopt the new documentation
baseline physically and governance-wise so that M1 (foundation retrofit) starts from a clean,
traceable state. **No source code changes in M0** — this phase touches only `docs/`, the RTM,
README, changelog, `.gitignore`, and helper scripts.

Exit criteria (from the assessment): docs tree matches the target layout; RTM exists and is
seeded; no duplicate or superseded documents outside `docs/archive/`; README/changelog current;
ADR log established.

---

## 1. Target docs tree (end state)

```
docs/
├── srs/                          Requirements sections 1–18 (single canonical copy, .docx)
├── nfr/                          Non-functional sections 0–13 (.docx)
├── architecture/
│   ├── tas/                      TAS parts 1–9 (.docx)
│   └── adr/                      ADR-001..015 (.md, one file each) + README.md (index)
├── design/
│   └── sds/                      SDS sections 1–17 (.docx)
├── process/
│   ├── Implementation Strategy.docx
│   ├── MIGRATION_ASSESSMENT.md   (moved from docs/)
│   ├── M0_PLAN.md                (this file)
│   ├── PHASE4_PLAN.md            (moved; header note added: "input to M4, superseded as active plan")
│   └── rtm/
│       ├── rtm.csv               546-row traceability matrix (375 FR + 171 NFR)
│       └── RTM.md                Column/status vocabulary + maintenance rules
├── testing/
│   ├── PHASE3_TEST_PLAN.md       (moved; immutable campaign record)
│   └── PHASE4_TEST_PLAN.md       (moved; header note added, becomes M4 input)
└── archive/
    ├── README.md                 Why these are archived; what supersedes each
    ├── FR-226-250-RENUMBERING-NOTICE.md
    ├── Requirements_section_13_FutureExpansion&AdvancedFeatures.docx
    ├── Technical Architecture Specification.docx        (old)
    ├── Software Design Specification.docx               (old)
    ├── Android App Lock System Design Diagrams.docx
    ├── Draft_design_App_Lock.docx
    ├── app lock.docx
    └── Functional Requirements Completion Summary.docx
```

Naming: original .docx filenames are **kept** (they match the user's Word workflow and the
assessment's citations), with one exception — `Non-functional_section_5_SecurityQuality .docx`
loses its trailing space. `docs/New docs/` is removed once empty. The old Requirements
sections 1–12 are **deleted, not archived** (their extracted text is byte-identical to the new
copies — hash-verified in the assessment — and git history preserves the originals).

## 2. Work breakdown (execution order)

| # | Step | Detail | Output |
|---|------|--------|--------|
| 1 | Git hygiene | Add `~$*` (Word lock files) to `.gitignore`; stage the two already-deleted `~$` entries. Pre-move inventory: record SHA-256 of every file under `docs/` to a scratch manifest so step 8 can prove nothing was lost or altered in transit. | .gitignore rule; manifest |
| 2 | Create tree & move | `git mv` (tracked) / `mv` (untracked) per §1. New-docs set → srs/nfr/architecture/design/process; existing .md plans → process/ + testing/; superseded set → archive/. Fix the trailing-space filename during its move. | Tree per §1 |
| 3 | Deduplicate SRS | Delete old `docs/Requirements_section_1..12_*.docx` (12 files). Re-verify identity immediately before deletion (extract text → hash-compare against the new copies, same method as the assessment) as a final guard. | 12 deletions, verified |
| 4 | Archive notices | `archive/README.md` (per-file: what it was, why archived, what supersedes it). `FR-226-250-RENUMBERING-NOTICE.md`: old section 13's FR-226..250 (cloud/remote/enterprise/AI) were removed and the identifiers reused by the new Production Readiness section; any pre-2026-07-19 reference to FR-226..250 means the OLD meanings; no code/changelog references exist above FR-179 (verified). | 2 notice files |
| 5 | ADR log | `architecture/adr/`: backfill ADR-001..010 verbatim-in-substance from TAS §71 (status: Accepted, source cited) plus five new records — see §3. One .md per ADR, shared template (ID, title, status, context, decision, alternatives, consequences, related requirements). Index in adr/README.md. | 15 ADRs + index |
| 6 | RTM | Generate `process/rtm/rtm.csv` by script from the extracted requirement texts (IDs + titles already parsed during the assessment). Columns: `id, title, section, type, priority, status, implementation, verification, notes`. Seed statuses per §4. `RTM.md` documents the status vocabulary and the update rule (every future PR that implements/verifies an FR updates its row). | rtm.csv (546 rows) + RTM.md |
| 7 | README + changelog | README: replace the old 5-phase status table with the IS 7-phase model (0–6) + migration position (M0 done, M1 next), point to `docs/process/MIGRATION_ASSESSMENT.md` and the new tree. changelog.txt: entry "Adopted new documentation baseline; M0 restructure" with the notable facts (375 FRs, renumbering notice, RTM introduced). | Updated files |
| 8 | Verification | (a) Post-move manifest: every pre-move hash accounted for (moved, deleted-as-duplicate, or archived); (b) tree diff vs §1; (c) no `.docx` remains directly under `docs/` or in `docs/New docs/`; (d) rtm.csv row count = 546, spot-check 10 rows against source docs; (e) all internal doc links in README/assessment/plans resolve to the new paths. | Verification note appended here |
| 9 | Commit checkpoint | Stage everything; single commit (subject: `M0: adopt new doc baseline — restructure docs, add RTM + ADRs`; detail in changelog per workflow). Committed by user or by me per your call (§5 D4). | Clean tree |

Steps 2–3 move ~70 files; the helper extraction script used for verification is kept at
`scripts/extract_docx.py` (new top-level `scripts/` dir) since doc-text extraction will recur
(RTM upkeep, future doc diffs).

## 3. ADR set (step 5 contents)

Backfill (from TAS §71, status Accepted-by-baseline): **ADR-001** layered Clean Architecture ·
**ADR-002** MVVM presentation · **ADR-003** Repository pattern · **ADR-004** centralized security
services · **ADR-005** Android Keystore for key storage · **ADR-006** WorkManager background
processing · **ADR-007** Room as persistence · **ADR-008** centralized logging/diagnostics ·
**ADR-009** DI across application services · **ADR-010** architecture review for tech changes.

New records (as-built decisions + M0 decisions):

- **ADR-011 — Single-module `:app` with package-enforced layering.** SDS §4.1 permits physical
  adaptation; module split deferred until build times/team size demand it. Consequence: layer
  rules must be enforced by static analysis (M1) instead of module boundaries.
- **ADR-012 — SQLCipher (net.zetetic) as the Room "(or equivalent)" encrypted-persistence
  instantiation.** As-built since Phase 2, validated; satisfies ADR-007 + FR-162/164. Documents
  the known `sqlcipher_export` no-op and the read-reinsert migration approach.
- **ADR-013 — Accessibility-service-based app detection.** As-built; alternatives
  (UsageStatsManager polling) rejected on latency; documents the inherent force-stop
  service-nulling limitation and the watchdog mitigation (FR-179/FR-253 lineage).
- **ADR-014 — Supported API range.** Proposed: keep **minSdk 26 / targetSdk 35** (decision D1,
  §5). The new docs leave the range "defined by project requirements" — this ADR becomes that
  definition.
- **ADR-015 — Hilt adoption plan.** Graph service locator is prohibited (SDS §5.5); replacement
  lands in M1. Interim rule effective immediately: no new `Graph.*` lookup sites; new code takes
  constructor parameters.

## 4. RTM seeding (step 6 statuses)

Vocabulary: `implemented-verified` (built + evidenced in a validation campaign) ·
`implemented` (built, not yet campaign-verified) · `partial` · `not-started` ·
`descoped-v1` (explicitly deferred with source citation) · `removed` (not in RTM — removed FRs
simply don't appear; the renumbering notice covers them).

Seed rules, applied per section from the assessment + changelog + test plans:

| Range | Seed |
|---|---|
| FR-001..025 Authentication | PIN + biometric rows `implemented-verified`; pattern/knock/password rows `not-started` |
| FR-026..055 Lock Engine | Core detection/lock/relock rows `implemented-verified` (PHASE3 campaign evidence); remainder per-FR review |
| FR-056..080 Protected Apps | List/toggle/persistence `implemented-verified`; bulk/categories per-FR review |
| FR-081..105 Privacy | Intruder selfie set (081/082/084/085) `implemented-verified`; FR-083 location, disguise/stealth rows `not-started` |
| FR-106..125 Vault | Import/export/delete/preview/formats `implemented-verified`; search/folders/backup/camera-capture `not-started` |
| FR-126..145 Automation | All `not-started` (PHASE4_PLAN exists as design input — noted in `notes`) |
| FR-146..160 Notifications | Watchdog/intruder notifications `partial`; rest `not-started` |
| FR-161..180 Security | Encryption/lockout/FLAG_SECURE/watchdog rows `implemented-verified`; root/tamper (167..170) `not-started` |
| FR-181..225 | Settings subset `partial`; performance/admin/backup sections mostly `not-started` |
| FR-226..375 (new) | `not-started`, except incidental overlaps flagged `partial` with pointers: FR-228 (migrations exist), FR-237 (secure defaults), FR-239 (no internal detail in errors), FR-241 (state recovery validated P-1/P-3), FR-253 (watchdog), FR-308 (secure delete) |
| NFR-* (171 rows) | `not-started` except NFR-TEST-001 `partial` (injectable clocks/policies) and NFR-SEC-002/004 `partial` (Argon2id/Keystore in place, unverified against the spec text) |

Per-FR precision inside the coarse ranges gets refined opportunistically during M1–M3 gate
reviews; M0 only promises honest coarse seeding, never overclaiming (`implemented-verified` is
used strictly where a campaign record exists).

## 5. Decisions — RESOLVED 2026-07-19

- **D1 — API range: minSdk 26 / targetSdk 35 confirmed.** ADR-014 additionally records: the
  verification fleet is not limited to the local API-30 emulator — a physical Moto G (2023,
  Android 13+) and a dedicated emulator host (NucBox G5) join the fleet, plus the M1 CI managed-
  device matrix; and the design must absorb future API levels (e.g., API 37) without redesign —
  platform APIs stay isolated in the Platform Integration layer, targetSdk/compileSdk bump
  annually, deprecated-API reliance is prohibited.
- **D2 — Confirmed:** .docx stays authoritative, no markdown mirrors; `scripts/extract_docx.py`
  for on-demand text extraction.
- **D3 — Confirmed:** delete the 12 duplicate old SRS sections (hash-verified; git history
  retains them).
- **D4 — User performs all commits.** Execution stages changes and stops before `git commit`.

Note: 10 SDS sections (7–17, detailed component designs) were added to `docs/New docs/` after
this plan was first written; file counts and the tree above reflect them (59 baseline docs).

## 6. Effort & sequencing

Single session (~half a day of agent work): steps 1–4 are mechanical file operations with hash
verification; step 5 is ~15 short documents; step 6 is one generation script plus seeding; steps
7–9 are small edits plus checks. No dependency on the emulator, no build required (nothing under
`app/` changes — a `gradlew assembleDebug` smoke run at the end confirms the no-code-impact
claim cheaply).

Risks: none structural. The only irreversible action is the D3 deletion, guarded by the
immediately-preceding hash re-verification and git history.
