# ADR-022A — Neutral Heading of the Lockout Screen

**Status:** Accepted (2026-11-09) · **Date:** 2026-11-09 · **Source/authority:** Project lead (2026-11-09) ·
**Supersedes:** ADR-022, entry 5 only (entries 1 to 4 and 6 to 12 stay under ADR-022)

## Context
Entry 5 of ADR-022 adds a note to the lockout screen SCR-013 of the 1.0.0 UI/UX specification. In the process that
installed a D1 replacement lock, the screen states that the lock history could not be read. The entry keeps the
heading of SCR-013, "Too many attempts".

D1 of `2026-11-06_M7_R007_F2_HARDENING_P3_DECISIONS.md` (the decision record) installs a replacement lock without
asserting that any wrong PIN occurred, and a replacement can follow no PIN attempt at all. Under C2, count-first
admission, an interrupted reservation can also lock without a wrong PIN (ADR-022 entry 12). In both cases, the heading
states a reason that the app has not established, and the note does not remove that claim. The stored record also
cannot show why a lock exists (the D2 state table of the decision record).

## Decision
This entry replaces entry 5 of ADR-022. The labels are those of ADR-022.

| No. | 1.0.0 clauses | Exception | Source | Verification |
|---|---|---|---|---|
| 5 | UI/UX SCR-013, layout: "Show “Too many attempts,” ...". | Departure. Every lockout screen shows the heading "Try again later" instead of "Too many attempts". The countdown and the existing controls stay. In the process that installed a D1 replacement lock, the screen also shows "The lock history could not be read." After a process restart, the note is omitted. | Lead decision (2026-11-09). D1 decision (2026-10-26). D2 state table (2026-10-27). | X19 of `2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`. |

Entries 1 to 4 and 6 to 12 of ADR-022 stay in force unchanged. So do its decision points, its transition list, and its
lists of clauses not covered and clauses that hold.

## Alternatives considered
- **Keep "Too many attempts" and add the note (ADR-022 entry 5).** Rejected: the heading still gives a reason that the
  app has not established.
- **A neutral heading only in the process that installed the replacement.** Rejected: after a restart, the same
  replacement lock shows the old heading again, and C2's interrupted reservations keep the claim.

## Consequences
- The heading makes no claim about wrong PINs, so the app needs no stored record of why a lock exists.
- The screen states less about the cause of an ordinary lock after five wrong PINs.

## Related requirements
v1.0.0 SRS: FR-010.

## Related
ADR-019 (the 1.0.0 baseline), ADR-022 (entry 5 superseded).

## Implementation status
2026-11-09: proposed and accepted. No code implements the heading or the note, and neither has verification evidence.
