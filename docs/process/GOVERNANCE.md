# Project Governance Rules — RTM, ADR, Git Workflow, Dates, Document Classes & Risk

Authoritative rules for maintaining the Requirements Traceability Matrix, Architecture
Decision Records, the multi-machine git workflow, date handling, and document classes &
risk authority. They bind **every contributor — human or AI assistant — on every fleet
machine.**

**Keywords:** **MUST** / **MUST NOT** = mandatory, no exceptions without explicit user
approval recorded in the changelog. **SHOULD** = required unless a justified reason is
documented where the deviation occurs. **MAY** = optional.

**Precedence:** for the five areas below, this document overrides older guidance in other
docs; on discovering a conflict, correct the other doc in the next docs commit. Changes to
this document itself require explicit user approval and a changelog entry.

---

## 1. Requirements Traceability Matrix (`docs/process/rtm/rtm.csv`)

### 1.1 Principle

The RTM **MUST** represent the current true state of every requirement at all times.
Requirements are **living artifacts**: a row is never "done forever" — later changes can
invalidate earlier work, and the RTM must show that honestly. An RTM that overstates
progress is worse than none.

### 1.2 Status vocabulary (closed set — no other values permitted)

| Status | Definition (exact) |
|---|---|
| `not-started` | No implementation work has begun. |
| `in-progress` | Implementation is actively underway in the current work package. |
| `partial` | Some acceptance criteria are met, others are not. `notes` **MUST** state what remains. |
| `implemented` | All acceptance criteria are believed met. No recorded verification evidence yet. |
| `implemented-verified` | Implemented **and** verification evidence exists. `verification` **MUST** contain an evidence pointer (a `docs/reports/` record or CI run). Never assigned without one. |
| `invalidated` | Was `implemented`/`implemented-verified`, but a later change touched its implementation or undermined its evidence. Requires re-verification. `notes` **MUST** cite the invalidating change (commit or report). |
| `descoped-v1` | Deliberately deferred beyond v1.0.0. `notes` **MUST** cite the deferral decision. |
| `review` | **Legacy seed status only** (M0 seeding, 2026-07-19): functionality exists in the area but per-requirement classification is pending. **MUST NOT** be newly assigned; only burned down at gate reviews. |

Removed requirements do not appear in the RTM at all (see
`docs/archive/FR-226-250-RENUMBERING-NOTICE.md`).

Mapping to conventional terms: Planned→`not-started`; In Progress→`in-progress`;
Implemented→`implemented`; Verified→`implemented-verified`; Validated→campaign sign-off
recorded in `docs/reports/` and cited in `verification`; Deferred→`descoped-v1`;
Removed→absent.

### 1.3 When the RTM MUST be updated (same commit as the triggering change)

1. **Implementation:** code lands that implements, extends, or alters behavior tied to a
   requirement → update that row's `status`/`implementation`.
2. **Verification:** evidence is produced (campaign report, CI matrix run, benchmark) →
   update `status` and `verification` with the pointer.
3. **Invalidation:** a refactor or change touches the implementation of an
   `implemented`/`implemented-verified` requirement **without** re-verification evidence in
   the same change → that row **MUST** move to `invalidated`. (A green run of the standing
   regression suite covering that requirement, cited in `verification`, counts as
   re-verification and avoids the downgrade.)
4. **Scope decisions:** deferral, de-scoping, or reactivation → `descoped-v1` or back, with
   the decision reference.
5. **Gate reviews:** each migration-phase gate **MUST** burn down the `review` rows in its
   scope (M1→FR-026..080, M2→FR-161..180, M3→FR-106..125/146..160/181..195) **and** sample
   previously-`implemented-verified` rows in the phase's touched scope to confirm they still
   hold; any that don't → `invalidated`.
6. **Baseline changes:** a new SRS/NFR revision → add/adjust rows in the same commit that
   adopts the revision.

### 1.4 When the RTM MUST NOT be modified

- In commits that do not change any requirement's actual state (docs-only, tooling,
  formatting). No drive-by edits.
- To promote a status **without meeting its definition** (no evidence pointer → no
  `implemented-verified`, ever — including "it obviously works").
- Wholesale regeneration or re-seeding. The M0 generator is history; the file is now
  maintained row-by-row.
- By one fleet machine to record another machine's results before those results are pulled
  from the remote as a committed report.

### 1.5 Wording rules

`notes` **MUST** be factual and specific (cite files, reports, commits). Prohibited:
hedging or ambiguous phrasing such as "mostly done", "should work", "probably",
"basically complete". If certainty is lacking, the status is `partial` or `invalidated`,
and the note says precisely what is unknown.

---

## 2. Architecture Decision Records (`docs/architecture/adr/`)

### 2.1 Principle

ADRs preserve architectural history. **The decision content of an accepted ADR is
immutable** — architecture evolves by *superseding* decisions, never by rewriting them.

### 2.2 A new ADR MUST be created when

- Selecting or replacing a technology, framework, or significant dependency.
- Defining or changing an architectural structure, layer rule, or security boundary.
- Adopting a project-wide convention with architectural consequences.
- Deviating from the TAS/SDS baseline (per TAS §69.3, deviations need documented approval).
- Recording a discovered constraint that binds future work (e.g. ADR-018's FQCN pinning).
- **Reversing or materially changing a prior decision** → new ADR that supersedes the old.

### 2.3 Amendment rules

| Action | Allowed? |
|---|---|
| Change `Status` field (lifecycle: Proposed → Accepted → Superseded / Rejected) | **MAY**, dated |
| Update a separate **Implementation status** line (e.g. "executed in WP5, commit …") | **MAY**, dated |
| Add cross-links (`Superseded-by`, `Related`); fix typos, formatting, a broken reference, or a wrong date/commit-hash, provided it changes **no value a reader would act on** | **MAY** |
| Rewrite Context, Decision, Alternatives, or Consequences to say something **different** | **MUST NOT** (that is a supersession) |
| Edit the body to reconcile a value in it with how the implementation has since changed (e.g. syncing a listed parameter to the build) | **MUST NOT.** Correct the value in its living SSOT (§2.7), not the ADR; if the ADR should stop carrying that value, re-point it with a dated Implementation note. A genuine change of the decision itself is a supersession (§2.4) |
| Delete an ADR or reuse its number/suffix | **MUST NOT** (identifiers are permanent, even for Rejected ADRs) |

If it is unclear whether an edit is a permitted correction or a parameter/decision change, it is a
decision change by default: do not edit the ADR; update the cited living document and/or record the
discrepancy and surface it. Uncertainty about the boundary is never a license to edit the ADR body.

### 2.4 Supersession protocol

1. **Number the superseding ADR by the lineage-suffix rule.** A supersession of exactly
   **one** existing ADR takes that ADR's *lineage-root* number plus the next unused letter:
   `ADR-013` → `ADR-013A` → `ADR-013B` … (letters always hang off the lineage root, never the
   immediate parent — so a chain reads 013A, 013B, 013C, not 013A-A). A supersession of **more
   than one** ADR instead takes the next integer and lists all parents. An ADR that supersedes
   nothing is an ordinary new decision and takes the **next integer**. Include
   `Supersedes: ADR-NNN` and state *why* the old decision no longer holds.
2. Edit the old ADR's metadata only: `Status: Superseded by ADR-MMM` + date.
3. Update the index (`adr/README.md`) for both rows, same commit; list the superseding ADR
   directly beneath its lineage root so the chain reads in order.
4. If the superseded decision backed any RTM rows, re-evaluate them (§1.3 rule 3).

Lettered-suffix identifiers are permanent and non-reusable exactly like integer identifiers
(§2.3). Adopted 2026-08-04 (user-approved) to make supersession lineage visible in the
identifier itself.

### 2.5 Required metadata (every ADR)

`ID` · `Title` · `Status` (Proposed | Accepted | Superseded | Rejected) · `Date` (decision
date; amendments dated individually) · `Source/authority` (who or what mandated it) ·
`Context` · `Decision` · `Alternatives considered` (at least one, with why rejected — "none
considered" must be stated explicitly) · `Consequences` (including negative ones) ·
`Related requirements` (FR/NFR IDs) · where applicable: `Supersedes` / `Superseded-by`,
related ADRs, Implementation status.

### 2.6 Index

`adr/README.md` **MUST** list every ADR with current status, updated in the same commit as
any ADR addition or status change, and, per §2.8, an ADR body or status change names its §2.3
row in the accompanying changelog entry.

### 2.7 ADRs record durable decisions, not volatile parameters

An ADR states the decision and its rationale. Operational parameters that change on their own
cadence (exact API-level or test matrices, tool and dependency versions, fleet composition, file
paths, CI groupings) live in their living single-source-of-truth: the build files, `rtm.csv`, the
reports fleet index, or a runbook. The ADR **references** that source; it does not restate the
value. Restating a value that has a living source duplicates a fact that will drift, and drift
creates pressure to edit an immutable record (§2.3).

When an ADR already carries such a value and it has drifted, do not sync it in place. Correct it in
its source; if the ADR should no longer carry it, re-point it with a dated Implementation note
(§2.3). An instance that notices a drift it may not fix in place appends a row to
`docs/process/DISCREPANCIES.md` (append-only: date, file and section, the mismatch, the
authoritative source, status) and moves on, rather than editing the ADR.

### 2.8 Reviewing ADR amendments

A new ADR is reviewed before it counts, through its `Status` lifecycle (Proposed, then Accepted,
§2.5). An amendment to an already-Accepted ADR does not ride in under that original acceptance. It
is its own reviewed change, reviewed at the commit gate (§3.3, where the user applies all commits):

- Any change to an ADR body or status is staged with its §2.3 classification and a one-line
  justification recorded in the changelog entry the user reviews before committing (which row of
  §2.3 it falls under, and why).
- If the §2.3 classification is uncertain, the tie-breaker applies: it is treated as a decision
  change, it is not staged, and the discrepancy is recorded instead (§2.7).

---

## 3. Multi-Machine Git Workflow

### 3.1 Principle

Git is the **only** shared channel between fleet machines; each assistant's local memory is
invisible to the others. Every unit of work **MUST** be based on the current remote state,
and completed work is only "real" once pushed.

### 3.2 Session-start protocol (MANDATORY before creating or editing any file)

```
1. git fetch origin
2. git rev-list --left-right --count main...origin/main   # "<ahead> <behind>"
3. git status --short                                      # uncommitted local changes?
```

- `0 0`, clean tree → proceed.
- Behind only (`0 N`) → `git merge --ff-only origin/main`, then proceed.
- Ahead only (`N 0`) → local commits not yet pushed: surface to the user before adding
  more work on top.
- **Diverged** (`N M`) → **STOP.** Do not commit, do not merge autonomously. Report both
  sides to the user and follow §3.4.
- Dirty tree from a previous session → identify what it is and surface it before new work.

The same check **MUST** be repeated immediately before staging finished work; if the remote
moved meanwhile, sync first (ff-only or merge per §3.4) and re-run affected checks.

### 3.3 Prohibited operations

- `git push --force` (any variant) to `main`. **MUST NOT**, ever.
- Rewriting pushed history (rebase/amend of published commits).
- Committing or pushing by an AI assistant: **the user performs all commits and pushes**
  (M0 decision D4). Assistants stage (`git add`) and stop. (If the user ever delegates a
  specific commit explicitly, that delegation covers that commit only.)
- Basing work on a stale HEAD "to save time", or continuing a task on machine B while its
  work-in-progress sits uncommitted/unpushed on machine A (see §3.5).

### 3.4 Merge-conflict handling (by file class)

| File class | Rule |
|---|---|
| `docs/reports/**` (append-only, host-tagged) | Should never conflict by design. If it does: keep **both** files; for the fleet-index table, keep one row per host pointing at that host's newest dated report. |
| `changelog.txt` | Keep **both** entries, ordered newest-first by date. Never drop an entry. |
| `rtm.csv` | Union of row-level changes. Same-row conflict: the side with an evidence pointer wins; both with evidence → the more recent evidence; still ambiguous → escalate to the user. |
| Source code (`app/**`), build files | Trivial conflicts (imports, formatting) **MAY** be resolved; anything touching logic → escalate to the user. After any source-conflict resolution, the affected test suites **MUST** be re-run before staging. |
| `GOVERNANCE.md`, ADRs, plans | Escalate to the user. |

### 3.5 Hand-off rule

Before a task moves between machines, all of its work **MUST** be committed **and pushed**
(via the user, per §3.3), and the receiving machine **MUST** run §3.2 before continuing.
Results produced on one machine exist for the others only once they are pushed committed
files — never assume the other machine "knows".

---

## 4. Date Management

1. All dates in committed artifacts **MUST** be absolute ISO format `YYYY-MM-DD`. Relative
   terms ("today", "tomorrow", "next Wednesday") **MUST NOT** appear in committed files.
2. The current date **MUST** be taken from the machine's system clock at writing time —
   never assumed from conversation context, memory, or a previously seen date.
3. Report filenames carry the **filing date**. If the evidence was captured on a different
   date, the body **MUST** state both (precedent: `2026-07-21_fleet-nucbox-g5.md`, evidence
   2026-07-20).
4. Backdating and future-dating are prohibited. Expected future events are written as
   expectations with a date ("arriving 2026-07-22"), and corrected if reality differs.
5. Changelog entry dates = the date the work was completed. ADR dates = the decision date.
6. When updating a dated statement (fleet arrivals, deadlines), replace it — do not leave
   contradictory dates in the same document.

---

## 5. Document Classes & Risk Authority

Adopted 2026-08-10 (user-approved). Trigger: the 2026-08-10 scale/gate crosswalk review
found risk ratings drifting between a frozen assessment and the living register, with no
stated rule for which wins.

### 5.1 Document classes

Every `docs/` artifact belongs to exactly one class, which fixes how it may change:

| Class | Examples | Change rule |
|---|---|---|
| **Spec (client-received / client-approved)** | `v1.0.0/` (active baseline, client-approved — ADR-019) · `v2.0.0/{srs,nfr,tas,sds,dds,tsp,tm}` (full spec, 2.0.0 target) | Preserved verbatim; changes arrive only as new revisions (docs/README.md convention 7). v1.0.0 revisions are produced via its governed pipeline (`docs/v1.0.0/source/`), never by hand-editing outputs. |
| **Living** | `ROADMAP.md`, `RISK_REGISTER.md`, `M*_PLAN.md`, `rtm.csv`, this document, READMEs | Updated in place; **MUST** reflect current truth at all times. |
| **Snapshot** | `MIGRATION_ASSESSMENT.md`, `TS_GAP_ANALYSIS.md`, dated analyses | Frozen at their stated date. Original text **MUST NOT** be rewritten; dated annotations **MAY** be added to correct a reading or point at living successors. |
| **Evidence** | `docs/reports/**` | Immutable per `docs/reports/README.md`. |

When a snapshot's content acquires a living successor, the snapshot **MUST** carry a banner
naming it, and the living document is authoritative from that date. Citing a snapshot's
judgments (ratings, priorities) **SHOULD** name it as a snapshot, with its date.

### 5.2 Risk authority

`docs/process/RISK_REGISTER.md` is the **single authoritative record of tracked project
risks**.

- Gate reviews **MUST** take the risk picture from the register, not from snapshots or
  prose summaries.
- Every register entry **MUST** carry: stable `R-NNN` id · category · likelihood · impact ·
  severity (Likelihood × Impact on the Low/Medium/High/Critical scale, consistent with
  TM §14) · status · opened date · owner · **affected gate(s)** · provenance where the risk
  derives from a snapshot or report.
- TM §14 gate consequences apply: a **Critical** risk **MUST NOT** be silently carried past
  a gate (TM §14.9); a **High** risk blocks its affected security gate absent remediation or
  an explicitly approved compensating treatment (TM §14.10).
- Risk statements anywhere else (assessments, reports, threat-model text) are inputs or
  history — on conflict the register wins, and the conflicting document is annotated per
  §5.1.

### 5.3 Milestones and phase citation

`docs/process/ROADMAP.md` owns the canonical milestone model: the active **1.0.0 line M7–M10**
(adopted 2026-08-14, ADR-019 — M2–M6 are frozen as the deferred 2.0.0 lineage and are never
re-meant) plus the legacy mapping **M0–M6 ↔ Implementation Strategy Phases 0–6**. Because the IS
numbers its phases **0–6** while the Test Specification lists the same sequence **1–7**
(TSP §11.7), a bare "Phase N" is ambiguous: phase references in project-authored docs **MUST** be
written as `IS Phase N (Mx)` — e.g. "IS Phase 1 (M2)". M7–M10 have no IS-phase aliases and are
cited bare.

**Version-qualified spec citations (adopted 2026-08-14, ADR-019):** the v1.0.0 and v2.0.0
baselines deliberately share FR/NFR identifiers (reserved, with narrowed 1.0.0 meanings) and
overlapping section numbering, so a bare `SRS §8` or unqualified `FR-042` reading is ambiguous
where the meanings differ. Spec cross-references **MUST** be version-qualified when ambiguous —
`v1.0.0 SRS §8`, `v2.0.0 TAS Part 3`. Pre-existing bare references are corrected when next
touched (§6), not retroactively.

### 5.4 Classification vocabularies

Ratings **MUST** be qualified by object and source, because the project carries several
disjoint scales: risk severity (Low/Medium/High/Critical — TM §14, the register), defect
severity (Critical/Major/Minor — TSP §12.10), defect priority (urgency — TSP §12.11,
deliberately separate from severity), and two snapshot P-scales (`P0–P3` in
MIGRATION_ASSESSMENT Phase 10; `P1–P3` in TS_GAP_ANALYSIS — different anchors, different
ranges). Write "Critical *defect*", "High *risk*", "P1 *(migration)*". Snapshot P-codes
prioritize plans; they **MUST NOT** be treated as gate criteria.

---

## 6. Enforcement

- A discovered violation of these rules **MUST** be corrected in the next commit touching
  that artifact, and noted in the changelog if it affected recorded state (e.g. an RTM
  status that was overstated).
- These rules apply from their adoption date (2026-07-22; §5 from 2026-08-10). Pre-existing
  artifacts are not retroactively rewritten (history stays honest); they are brought into
  compliance when next touched.
