# Version-Baseline Migration Plan (1.0.0 active / 2.0.0 target)

**Class:** Living plan (GOVERNANCE.md §5.1). **Decision of record:** [ADR-019](../architecture/adr/ADR-019-version-split-baseline.md).
**Trigger:** client approval of the reduced 1.0.0 specification, 2026-08-14.
**Status:** **Executed 2026-08-14** — Phases A+B committed with the structure move (incl. the
post-approval de-draft: outputs renamed `*_v1.0.0.*`, "Draft" status lines removed); Phases C+D
executed with the milestone re-cut to **M7–M10** (`ROADMAP.md`, decision recorded there and in
ADR-019's lineage) — doc map, GOVERNANCE §5.1/§5.3, path references, risk-register gate
re-pointing, and the RTM re-base (232 rows `descoped-v1` per the v1.0.0 SRS/NFR Appendix A lists)
all landed; Phase E verification green (pipeline rebuild + audit, RTM parse, reference sweep).
Checklist below retained as the execution record.

Executes the version-split adopted in ADR-019: promote the client-approved **1.0.0** specs into
`docs/v1.0.0/` as the **active baseline**, reposition the existing full spec as the **2.0.0** target
in `docs/v2.0.0/`, and re-base the governed artifacts onto 1.0.0. Nothing here changes product code.

Commits are performed by the user (GOVERNANCE §3.3). Suggested commit boundaries are noted per
phase; the RTM re-base (Phase D) MUST land in the same commit as the baseline adoption it records
(GOVERNANCE §1.3 rule 6) — either bundle C+D, or keep the whole migration as one commit.

## Target layout

```
docs/
├── v1.0.0/   srs/ nfr/ sds/ dds/ tm/ uiux/ source/     ← ACTIVE (client-approved; code/RTM trace here)
├── v2.0.0/   srs/ nfr/ tas/ sds/ dds/ tsp/ tm/         ← FUTURE target (no uiux yet — unreviewed)
├── architecture/adr/                                    ← shared
├── process/  reports/  security/(SCS)                   ← shared
```

Reviewed-content-only: the 2.0.0 UI/UX draft is unreviewed and stays in gitignored
`New docs/2.0.0 Planned/` until it passes review, then promotes to `docs/v2.0.0/uiux/` (ADR-019 §2).

## Phase A — Establish `docs/v1.0.0/` (active), from the approved package

- [ ] **A1.** Create `docs/v1.0.0/`; copy the approved deliverable from `New docs/1.0.0 Draft/`:
  the 6 consolidated DOCX, `markdown/` (consolidated mirrors), `sections/` (118 DOCX),
  `markdown/sections/` (118 MD).
- [ ] **A2.** Copy `New docs/1.0.0 Draft/source/` → `docs/v1.0.0/source/`, then **repoint the
  pipeline for the new home** (it currently targets `New docs/1.0.0 Draft/`):
  - `build_v1_documents.py`: `OUTPUT_DIR = QA_ROOT.parent` (i.e. `docs/v1.0.0/`).
  - Bring the Word **style-reference template** into tracking — copy the docx currently referenced
    from gitignored `New docs/2.0.0 Planned/` into `docs/v1.0.0/source/_style_reference.docx` and
    repoint `REFERENCE` there (removes the gitignored build dependency).
- [ ] **A3.** `.gitignore`: add the build intermediates `docs/v1.0.0/source/split-staging/`,
  `docs/v1.0.0/source/split-docx/`, `docs/v1.0.0/source/__pycache__/`. Leave `New docs/` ignored.
- [ ] **A4. Verify:** `python docs/v1.0.0/source/rebuild.py` runs clean; the final package audit
  passes against the new location; 6+6 consolidated and 118+118 section artifacts present.
- *Commit boundary:* A can stand alone ("promote approved 1.0.0 into version control").

## Phase B — Reposition existing full spec as `docs/v2.0.0/`

- [ ] **B1.** `git mv` (history preserved): `docs/srs`→`docs/v2.0.0/srs`, `docs/nfr`→`docs/v2.0.0/nfr`,
  `docs/architecture/tas`→`docs/v2.0.0/tas`, `docs/design/sds`→`docs/v2.0.0/sds`,
  `docs/design/dds`→`docs/v2.0.0/dds`, `docs/testing/tsp`→`docs/v2.0.0/tsp`,
  `docs/security/tm`→`docs/v2.0.0/tm`.
- [ ] **B2.** Remove the now-empty `docs/design/`. Keep `docs/architecture/` (adr/),
  `docs/testing/` (test plans), `docs/security/` (Secure Coding Standard).
- [ ] **B3. Verify:** `git status` shows renames only, no content deltas.

## Phase C — References & governance

- [ ] **C1.** `docs/README.md` doc map — rewrite the layout section for the version split.
- [ ] **C2.** `GOVERNANCE.md`: update §5.1's spec-path list (`srs/ nfr/ tas/ …` → `v1.0.0/…`,
  `v2.0.0/…`); add a **version-qualified citation rule** (mirror of §5.3's `IS Phase N (Mx)`):
  spec cross-references MUST be version-qualified when ambiguous — `v1.0.0 SRS §8`.
- [ ] **C3.** Update the path-style references to the moved dirs (2–5 tracked files each — RTM notes,
  ADRs, reports). Do **not** retroactively rewrite the 96 bare `SRS §N` refs — the new rule applies
  going forward; existing ones are corrected when next touched (GOVERNANCE §6).
- [ ] **C4.** `ROADMAP.md`: add the 1.0.0/2.0.0 ↔ M0–M6 reconciliation (which milestones are
  1.0.0-scoped). Owned by ROADMAP per GOVERNANCE §5.3.

## Phase D — RTM re-base onto 1.0.0 (same commit as C)

- [ ] **D1.** Descope **per SRS v1.0.0 Appendix A** (its reserved-identifier record is the
  authoritative list): each listed FR → `descoped-v1`, `notes` citing ADR-019 + "deferred to 2.0.0".
  This moves the Phase-3-built **Vault (FR-106–125)** and **intruder (FR-081–085)** rows from
  `implemented-verified` → `descoped-v1` — the code stays; the requirement defers; the RTM says so
  honestly.
- [ ] **D2.** Re-scope retained FRs whose 1.0.0 meaning narrowed (e.g. FR-042 no schedule/location,
  FR-026 no accessibility, FR-049 latency-to-NFR) — update `notes`; status per evidence (no
  promotion without an evidence pointer).
- [ ] **D3. Verify:** closed-vocabulary only; every `descoped-v1` cites the decision; no overstated
  status; `notes` factual (GOVERNANCE §1.2/§1.5).

## Phase E — Final verification

- [ ] **E1.** Rebuild the 1.0.0 package from `docs/v1.0.0/source`; audit green.
- [ ] **E2.** Repo consistency: doc map accurate; no dangling path refs; RTM internally consistent.
- [ ] **E3.** `changelog.txt` entry; hand the staged set to the user for commit.

## Safety & rollback

- Product code (`app/**`) is untouched; this is docs-only.
- History is preserved for the 2.0.0 move (`git mv`); the 1.0.0 files arrive fresh (were gitignored).
- A pre-migration backup of the approved package already exists in the session scratchpad
  (`draft-backup-20260814-*`).
- Reversible before commit; after commit, revert the migration commit(s).

## Open items to confirm before executing

- **Commit granularity:** one migration commit, or A+B (structure) then C+D (governance+RTM) as two?
- **ROADMAP milestone reconciliation (C4):** may need a short working pass with you — the M0–M6
  model was defined against the full spec.
