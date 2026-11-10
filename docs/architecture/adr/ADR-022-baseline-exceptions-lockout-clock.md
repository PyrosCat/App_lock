# ADR-022 — Exceptions to the Version 1.0.0 Baseline for Lockout State, Authentication States, and Clock Policy

**Status:** Accepted (2026-11-07); entry 5 superseded by ADR-022A (2026-11-09) · **Date:** 2026-11-07 ·
**Source/authority:** Project lead (decisions D1 to D6, 2026-10-26 to 2026-10-29; this record, 2026-11-07)

## Context
The client approved the 1.0.0 specification package on 2026-08-14 (ADR-019). Decisions D1 to D6 of the F2 hardening
work on risk R-007 of `RISK_REGISTER.md` (lockout enforcement during storage failures) conflict with some clauses of
that package, add to others, or fix how others read. `2026-11-06_M7_R007_F2_HARDENING_P3_DECISIONS.md` (the decision
record) holds the decisions. This ADR maps them to the clauses.

Labels: D1 to D6, their numbered items, the transition additions and refinements (sections 5.1 and 5.2), and rules A
and B are those of the decision record. C2 is count-first admission, and Option A is its comparator design. Case IDs
such as X14, constraints such as I6, and residuals R-007/n come from `2026-09-23_R007_F2_HARDENING_TEST_PLAN.md`. P3 to
P5 are phases of the F2 hardening entry of `M7_PLAN.md`. The crypto replacement proposal is
`2026-09-24_SECURITY_CRYPTO_REPLACEMENT_PLAN.md`. SRS, SDS, DDS, and UI/UX are the 1.0.0 documents.

## Decision
1. The 1.0.0 package stays unchanged. Where an entry below applies, it governs the clauses that it cites.
2. An interpretation fixes how a clause reads, and the clause holds. An addition adds a rule that the clause lacks. A
   departure replaces or narrows the clause.
3. Entries 1 to 11 apply under every admission design. Entry 12 applies only if the lead selects C2 in P5, and the
   Implementation status line then records the selection. Where D1 to D5 write a rule for C2, Option A's design states
   its own rule (D6 point 5), and those rules need their own baseline mapping.
4. The lead adopted each source decision on the date shown. Approval of an entry is neither implementation nor
   verification.

| No. | 1.0.0 clauses | Exception | Source | Verification |
|---|---|---|---|---|
| 1 | SDS 17.2, row "Protected preferences unavailable". DDS 8.3, state "Unavailable". DDS 9.3: "unavailable authentication rather than a guessed default". DDS 19.3, row "Protected preference cannot decrypt". | Departure. With trusted credentials, absent or invalid lockout history gets the strictest ladder state (the count at its cap, the maximum lock duration) instead of unavailable authentication or clear-data guidance. Authentication waits for the confirmed write. Not for reads that cannot complete, unsupported formats, incomplete migrations, or unmarked pre-release data. | D1 qualifications 3 and 4 (2026-10-26). D3 exception for D1, transition addition 3 (2026-10-27). | X14, R1.2, the D5 point 11 cases on stale boot IDs. No test-plan case covers the installed replacement. |
| 2 | SRS FR-009: "Every incorrect PIN increments the current failure count." SRS FR-010: "after repeated incorrect PIN attempts". SRS FR-014: "the local failure state needed for ... immediate troubleshooting". SDS 7.3, step 5 and the ladder paragraph. SDS 17.2, row "PIN is invalid". DDS 6.3, the count and the ladder paragraph. DDS 18.4, steps 2 and 3. | Departure. The count saturates at a cap of at least 11 and keeps it after expiry, so an incorrect PIN at the cap leaves it unchanged. The lead records the exact cap before implementation (D4 point 3). A D1 replacement sets the cap and the maximum lock without a failed verification, and that count is never presented as a number of wrong PINs. | D1 qualification 3 (2026-10-26). Transition addition 4 (2026-10-27). D4 points 1, 2, and 4 (2026-10-28). | X14, H03. |
| 3 | SDS 7.9, state "Not configured": "No complete verifier exists". DDS 8.3, state "Not configured": "No complete protected verifier exists". | Interpretation. "Not configured" is the confirmed empty state of D1: no credential entries, and a successful protected-selection query on a database that was not rebuilt or replaced. Any other missing or invalid credential is an unrecoverable local-data failure. | D1 rule 3, qualification 2, findings 1 and 2 (2026-10-26). Rule B (2026-10-27). | The Moto G check of the credential-damage prediction (decision record, section 1). No test-plan case. |
| 4 | SDS 7.2, steps 5 and 7. DDS 18.3, step 2. | Addition. PIN setup writes and confirms the initial lockout record before it commits the PIN, and completes only after both commits succeed. | D1 rule 4 (2026-10-26). D4 point 2 (2026-10-28). | No test-plan case. |
| 5 | UI/UX SCR-013, layout: "Show “Too many attempts,” ...". | Addition. In the process that installed a D1 replacement lock, the screen also states that the lock history could not be read. After a process restart, the ordinary screen appears. | D1 decision (2026-10-26). D2 state table (2026-10-27). | No test-plan case. |
| 6 | SRS FR-010: "A successful authentication clears the applicable consecutive-failure state." | Departure. Only an accepted PIN success, or a PIN change authorized with the current PIN, clears PIN accounting. An accepted biometric success leaves it unchanged. | D2 enforcement rules, refinements 4 and 5, transition addition 7 (2026-10-27). | H04 and X11 with their candidate results, constraint I6. |
| 7 | UI/UX 6.4: "keep “Use PIN” available when authentication is permitted". SDS 7.9, the states table. | Interpretation. The D2 state table refines the SDS 7.9 states. Authentication is permitted only in Ready, and PIN setup only in SetupRequired. Loading is the "Checking" state of SCR-013. | D2 state table (2026-10-27). | X11, the D5 point 11 trust cases. |
| 8 | Clear-data guidance in SDS 7.9 ("Unavailable"), SDS 17.1 ("Unrecoverable local-data failure"), SDS 17.2 ("Migration interrupted or invalid"), DDS 16.8, and DDS 19.3 ("Unsupported schema"). | Departure. An unsupported format, an incomplete migration, or ambiguous storage authority gives CompatibilityBlocked. Authentication stays blocked, the guidance depends on the reason, and the app neither suggests clearing data nor replaces or resets records. A check that cannot complete gives Unavailable. | Transition addition 2, transition refinement 2, section 5.3 point 2, rule A (2026-10-27). | No test-plan case. The crypto replacement work implements and tests it (transition refinement 4). |
| 9 | DDS 6.3: "the deadline before which another PIN verification is not permitted". | Addition. The record also holds the elapsed-realtime deadline, the boot identity at deadline creation or UNKNOWN, and the wall deadline as a nominal value that enforcement never reads. Only records written on this device are valid. | D5 points 2, 3, 9, and 10 (2026-10-29). | The D5 point 11 cases with X04, H05, the D5 device gate. |
| 10 | DDS 9.3: "Failed-attempt count cannot be negative. Lockout deadline must be absent or a valid time value defensively bounded at read." | Addition. The cross-field rules of D5 point 8 and the count range of D4 point 4 decide validity. An invalid record leads to entry 1. | D4 point 4 (2026-10-28). D5 point 8 (2026-10-29). | The D5 point 11 cases on records and trust, X14. |
| 11 | UI/UX 6.4, UI/UX 8.5, and SCR-013 on the authoritative delay. DDS 8.4: "in the future", the expiry clearing, and the display cap. | Interpretation. The authoritative delay is the original lock's duration from its creation, and a conservative estimate above it is not part of it. The remaining time of D5 decides whether a deadline is in the future and is the displayed time. The D5 expiry write is the clearing that DDS 8.4 permits. | D5 points 2, 4, 5, and 10 (2026-10-29). | The D5 point 11 cases on earlier admission, the wall clock, and expiry cleanup; the D5 device gate. |
| 12 | SRS FR-009, the first two acceptance criteria. SRS FR-010: "after repeated incorrect PIN attempts". SRS FR-014: "Incorrect attempts update the retry state accurately." SDS 7.3: "If a lockout is active, verification does not run", step 5, and the ladder paragraph. SDS 17.2, rows "PIN is invalid" and "PIN lockout active". DDS 6.3, the count, "the deadline before which another PIN verification is not permitted", and the ladder paragraph. DDS 8.4: "the next failure uses the correct doubled wait". DDS 18.4, steps 2, 3, and 5. | Departure, under C2 only. The count is consumed PIN-attempt capacity. Each PIN check first reserves one attempt, and the ladder advances at the reservation, not at a failed result. Verification starts after the reservation write is confirmed. A reservation stays consumed when its check never runs, is cancelled, or is interrupted. The reserving request may complete its check despite the lock that it installed. Legacy capture is disabled or unreachable before C2 is active. | D2 refinement 2 (2026-10-27). D4 points 1 to 3, 6, and 7 (2026-10-28). D5 point 3 (2026-10-29). | The C2 transition table and JVM model of P3, X08, X13, constraint I5, the device cases of P4. |

The 2.0.0 target specification does not change. The decision record gives these items for the move to 2.0.0:
- Entries 1, 2, and 12: replacement and reservation counts never become counts of actual wrong PINs. Failure-based
  capture and reports in 2.0.0 need separate accounting of confirmed failures (transition refinement 5). Under C2, the
  failure-based limits and delays of 2.0.0 need reconciliation with penalties from interrupted reservations (D4 point
  8).
- Entries 3, 4, and 8: the first released format identifies itself and the credential configuration state, and every
  released reader rejects unsupported formats without replacing records (transition refinement 3).
- Entries 6 and 7: each authentication method gets explicit eligibility, admission, acceptance, and reset rules
  (transition addition 7).
- Entries 9 to 11: a migration keeps each active lock's duration, clock meaning, and boot-ID scheme (D5 point 9).

Not covered by this ADR:
- Residual R-007/4, in which a restart restores the count after a reset that did not persist, is undecided under D3.
  This ADR records no exception to FR-010 for it.
- D1 finding 3: the database rebuild in the code conflicts with SDS 17.2 ("Database cannot open"). The clause holds, and
  decision D10 of the crypto replacement proposal owns the fix.
- The exceptions to invariant 6 of `M7_PLAN.md` (D4 point 2, D5 point 10) are plan exceptions.

Clauses that hold: the Threat Model, UI/UX section 8, and SDS section 3 (D5 points 1 and 10). SRS FR-174 also holds,
because no limit or departure of D5 admits a PIN check before the original duration has expired (D5 point 6).

## Alternatives considered
- **A revision of the 1.0.0 package through its build pipeline.** Rejected: it would change client-approved documents.
- **Copies of the decision texts in this ADR.** Rejected: two copies would drift apart.

## Consequences
- A reader of the 1.0.0 package also needs this ADR.
- The RTM changes when an implementation or its evidence changes a requirement row. That commit cites this ADR in the
  row.
- Entries 1 and 6 cost availability. After a D1 replacement, an owner who uses only the fingerprint stays at the cap,
  and each mistyped PIN costs 30 minutes until one correct PIN (D2).

## Related requirements
v1.0.0 SRS: FR-009, FR-010, FR-014, FR-174.

## Related
ADR-019 (the 1.0.0 baseline). ADR-022A (supersedes entry 5, added 2026-11-09).

## Implementation status
2026-11-07: proposed and accepted. No code implements an entry, and no entry has verification evidence.

2026-11-09: ADR-022A supersedes entry 5. Cases written after this ADR cover entries whose Verification cell names no
test-plan case. In the test plan, X17 covers entry 1, and X18 and X20 cover the handling of entries 3 and 8 by C1, the
trusted-state admission gate. Q07 and Q19 of the crypto replacement proposal test entries 4 and 8 on real storage.
