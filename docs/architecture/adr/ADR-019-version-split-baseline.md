# ADR-019 — Version-Split Documentation Baseline: 1.0.0 Active, Existing Full Spec Becomes the 2.0.0 Target

**Status:** Accepted · **Date:** 2026-08-14 · **Source/authority:** Product decision (project
lead) + **client approval of the reduced 1.0.0 specification, 2026-08-14**

## Context
The scope-narrowing recorded in ADR-013B produced a reduced, self-consistent Version 1.0.0
specification set (SRS, NFR, SDS, DDS, Threat Model, UI/UX). It was authored and audited in the
gitignored scratch area (`New docs/1.0.0 Draft/`), sent to the client, and **client-approved on
2026-08-14.** The existing `docs/` specifications (SRS FR-001..375, NFR 171, TAS, SDS, DDS, TSP,
Threat Model) are the M0 re-baseline authoritative baseline that the code, RTM, and ADRs currently
trace to.

With a year-end 1.0.0 release now the active build target and the fuller feature set moved to a
future 2.0.0, the project must carry **two coexisting specification baselines.** The complication:
1.0.0 **deliberately reuses the same FR/NFR identifiers with narrowed meanings** (e.g. FR-042 drops
schedule/location; FR-026 drops accessibility; FR-044 renamed). "Which version's FR-042?" is a real
ambiguity that a flat single tree cannot answer safely.

## Decision
Adopt **version-namespaced specification baselines** under `docs/`:

- **`docs/v1.0.0/`** — the **active baseline**. The client-approved 1.0.0 specs (SRS, NFR, SDS,
  DDS, TM, UI/UX) plus the build pipeline (`source/`), promoted from the gitignored scratch area
  into version control.
- **`docs/v2.0.0/`** — the **future target**. The existing full specification set (SRS, NFR, TAS,
  SDS, DDS, TSP, TM), moved with `git mv` so history is preserved.

Binding points:

1. **The active baseline is 1.0.0.** Code, RTM, tests, and gate reviews trace to `docs/v1.0.0/`
   until a future decision activates 2.0.0.
2. **Reviewed-content-only in a baseline.** Only reviewed material enters a version directory.
   The 2.0.0 UI/UX draft is **unreviewed**, so it stays in gitignored scratch
   (`New docs/2.0.0 Planned/`) and is promoted to `docs/v2.0.0/uiux/` only after it passes review.
   Consequently `docs/v2.0.0/` has **no standalone UI/UX** yet (a tracked 2.0.0 gap; the only
   reviewed 2.0.0 UI intent is SDS §6). 1.0.0 does have a reviewed UI/UX.
3. **Shared, cross-version artifacts stay put** and are version-aware: `architecture/adr/`,
   `process/` (RTM, risk register, GOVERNANCE, ROADMAP, plans, setup), `security/` (Secure Coding
   Standard), `reports/`.
4. **Identifier-reuse handling.** The same FR/NFR identifiers carry **narrowed 1.0.0 meanings**;
   the version directory disambiguates the document, and spec **cross-references MUST be
   version-qualified when ambiguous** — `v1.0.0 SRS §8` — mirroring the `IS Phase N (Mx)`
   convention (GOVERNANCE §5.3). GOVERNANCE §5.1's spec-path list and this citation rule are
   updated in the migration.
5. **The RTM tracks the active 1.0.0 build.** Capabilities outside 1.0.0 scope move to
   `descoped-v1` (deferred to 2.0.0) **per the SRS v1.0.0 Appendix A disposition** (its
   reserved-identifier record is the authoritative descope list); retained requirements are
   re-scoped to their 1.0.0 meaning. A single RTM is retained (no per-version RTM) because only
   1.0.0 is active and the `descoped-v1` vocabulary already expresses the deferral.

Execution is staged per `docs/process/VERSION_BASELINE_MIGRATION_PLAN.md`.

## Alternatives considered
- **Active-in-place** (1.0.0 replaces `docs/srs` etc.; existing full spec → `docs/future/2.0.0/`).
  Rejected: `docs/srs` would silently mean 1.0.0, leaving the identifier-reuse trap unmarked;
  version-namespacing puts the version in every path.
- **Single evolving baseline** (edit `docs/` down to 1.0.0, discard the superset). Rejected: the
  full spec is the 2.0.0 target and client-received history — it must be preserved, not overwritten.
- **Leave 1.0.0 in gitignored scratch.** Rejected: a client-approved deliverable and the active
  baseline cannot live only in an ignored working directory.
- **Per-version RTMs.** Rejected for now: only 1.0.0 is active; a second RTM adds maintenance with
  no current consumer. Revisit when 2.0.0 work begins.

## Consequences
Positive:
- Two coexisting baselines with the active one (1.0.0) unambiguous; identifier reuse is guarded by
  the version path.
- The client-approved deliverable and its reproducible build source enter version control.
Costs / follow-on work:
- **RTM re-base** onto 1.0.0 (descope per SRS v1.0.0 Appendix A; re-scope retained rows). Phase-1..3
  code built for now-descoped features (Vault, intruder) stays in the tree; the RTM shows
  `descoped-v1` honestly rather than deleting the rows.
- **ROADMAP/milestone reconciliation:** the M0–M6 milestones were defined against the full spec;
  the ROADMAP must state which milestones are 1.0.0-scoped vs 2.0.0 (owned by ROADMAP, GOVERNANCE
  §5.3) — handled in the migration plan, not this ADR.
- The **96 bare `SRS §N`-style cross-references** become version-ambiguous; the new
  version-qualification rule applies going forward, and existing refs are brought into compliance
  when next touched (GOVERNANCE §6), not retroactively rewritten.
- **1.0.0 has no standalone TAS** (by design; its architecture-level deviations, e.g. accessibility,
  are carried by ADR-013B). Only `docs/v2.0.0/` carries a TAS.

## Related
ADR-013B (1.0.0 detection scope) · GOVERNANCE.md §5.1 (document classes), §5.3 (phase/version
citation) · SRS v1.0.0 Appendix A (authoritative descope list) · `process/ROADMAP.md`,
`process/rtm/rtm.csv` · migration plan `process/VERSION_BASELINE_MIGRATION_PLAN.md`.

## Implementation status
**Not yet executed.** Staged per the migration plan: structure + reviewed-content promotion first,
then reference/governance updates, then the RTM/ROADMAP re-base, then verification. Client approval
(2026-08-14) is the trigger that unblocked execution.
