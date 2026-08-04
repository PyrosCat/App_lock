# Project Governance Rules — RTM, ADR, Git Workflow, Dates

Authoritative rules for maintaining the Requirements Traceability Matrix, Architecture
Decision Records, the multi-machine git workflow, and date handling. They bind **every
contributor — human or AI assistant — on every fleet machine.**

**Keywords:** **MUST** / **MUST NOT** = mandatory, no exceptions without explicit user
approval recorded in the changelog. **SHOULD** = required unless a justified reason is
documented where the deviation occurs. **MAY** = optional.

**Precedence:** for the four areas below, this document overrides older guidance in other
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
- Recording a discovered constraint that binds future work (e.g. ADR-013's FQCN pinning).
- **Reversing or materially changing a prior decision** → new ADR that supersedes the old.

### 2.3 Amendment rules

| Action | Allowed? |
|---|---|
| Change `Status` field (lifecycle: Proposed → Accepted → Superseded / Rejected) | **MAY**, dated |
| Update a separate **Implementation status** line (e.g. "executed in WP5, commit …") | **MAY**, dated |
| Add cross-links (`Superseded-by`, `Related`), fix typos/formatting, correct factual errors that do not alter the decision | **MAY** |
| Rewrite Context, Decision, Alternatives, or Consequences to say something **different** | **MUST NOT** — that is a supersession |
| Delete an ADR or reuse its number/suffix | **MUST NOT** — identifiers are permanent, even for Rejected ADRs |

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
any ADR addition or status change.

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

## 5. Enforcement

- A discovered violation of these rules **MUST** be corrected in the next commit touching
  that artifact, and noted in the changelog if it affected recorded state (e.g. an RTM
  status that was overstated).
- These rules apply from their adoption date (2026-07-22). Pre-existing artifacts are not
  retroactively rewritten (history stays honest); they are brought into compliance when
  next touched.
