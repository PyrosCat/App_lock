# M1 (IS Phase 0) — Foundation Retrofit — Gate Sign-Off

- **Date:** 2026-08-25
- **Gate lead:** Malcolm D. (2012-box) — lead sign-off.
- **Against commit:** `cf7a1e1` (+ this sign-off commit, which lands the FR-026..080 `review` burndown, the NFR-COMP-001 promotion, and this record together).
- **Completes / supersedes the decision of:** [`2026-08-25 → docs/reports/gates/2026-08-17_gate-m1.md`](2026-08-17_gate-m1.md), which recorded **"M1 EXIT: PENDING — lead sign-off"** with one open item. Reports are immutable (`docs/reports/README.md` rule 1), so this new dated record carries the completed decision rather than editing that one.

## Purpose

The 2026-08-17 review confirmed every **technical** M1 exit criterion green (CI builds, static analysis, dependency/license audit, service-locator elimination, WP6 device gating-regression, NucBox GMD `full` 20/20, Moto G 2025 4/4, real-hardware smoke) and left the decision **PENDING** on two lead-scope items. This record resolves both and grants the exit.

## Item (a) — FR-026..080 `review` burndown (GOVERNANCE §1.3.5)

The 33 `review` rows in the M1 scope band (FR-026..080) were cross-checked against **SRS v1.0.0 Appendix A §A.19** (the authoritative reserved-identifier record) and classified against the ADR-013B/019-narrowed v1.0.0 meanings, per the `WP8_GMD_MATRIX.md §7.2` reconciliation.

- **Finding:** none of the 33 are in §A.19 — they are all **retained** v1.0.0 requirements. Their descoped twins (FR-037, 043, 045, 058, 059, 061–071, 074–077, 079, 080) already read `descoped-v1`, so §A.19 was already honored; no descoping was reverted.
- **Disposition (lead call, two buckets):**
  - **26 Lock-Engine rows → `not-started`** — FR-026, 028, 029, 030, 031, 032, 033, 034, 035, 036, 038, 039, 040, 041, 042, 044, 046, 047, 048, 049, 050, 051, 052, 053, 054, 055. The v1.0.0 detection/lock engine is **UsageStats + mandatory overlay, no accessibility** (ADR-013B); that engine is **M7** scope and does not yet exist. The as-built legacy PHASE3 accessibility detector does not satisfy the v1.0.0 criteria and is being replaced, so no M1 credit is claimed.
  - **7 already-built rows → `partial`** — FR-027 (Lock Screen Display), FR-056 (Protected Application Selection), FR-057 (Application Search), FR-060 (Enable/Disable Protection), FR-072 (Protected Application Icons), FR-073 (Application Information), FR-078 (Application Removal Cleanup). These are mechanism-agnostic UI/data that already ship (FR-027 is exercised by the WP8 `LockScreenLaunchTest`; the protected-apps rows by "basic protect/unprotect/persist verified"); each row's note states that v1.0.0 integration + verification is owned by M7.
- **Result:** **zero `review` rows remain in FR-026..080** — GOVERNANCE §1.3.5 satisfied for the M1 gate. (The `review` rows still present elsewhere — authentication FR-001..024, privacy FR-095..105, and the M2 security / M3 settings bands — are out of the M1 §1.3.5 scope and are owned by their own phase gates.)

RTM applied in the same commit as this record (GOVERNANCE §1.3 / §3.2).

## Item (b) — NFR-COMP-001 promotion

**NFR-COMP-001 (Android Platform Compatibility): `partial` → `implemented-verified`.** The full M1 acceptance matrix is green — NucBox `full` GMD 20/20 across API 26/29/30/33/35 (x86_64) + Moto G 2025 4/4 (arm64 / API 35) + CI `ci`-group API 30/35 — which is the reconciliation's own eligibility criterion. Evidence pointer (`docs/reports/campaigns/2026-08-17_wp8-gmd-matrix_nucbox-g5.md`) was already present on the row. Broader real-device / API breadth is a later-milestone enhancement, not an outstanding v1.0.0 criterion.

## Docs synchronized this commit

- **RTM** (`docs/process/rtm/rtm.csv`): the 33-row burndown (26 `not-started` + 7 `partial`) + NFR-COMP-001 → `implemented-verified`.
- **ADRs:** none touched. ADR-015 was already closed at the 2026-08-17 gate; ADR-014 is deliberately left unedited (GOVERNANCE §2.7 — NFR-COMP-001's status lives in `rtm.csv`, not in the ADR). No §2.8 amendment applies.
- **changelog.txt:** M1 exit sign-off entry.

## Decision

**M1 EXIT: GRANTED — Malcolm D. (gate lead, 2012-box), 2026-08-25.**

All technical exit criteria were met and green at the 2026-08-17 review; the two lead-scope items — the FR-026..080 `review` burndown and the NFR-COMP-001 promotion — are resolved above. **M1 (IS Phase 0, Foundation Retrofit) is closed.**

**Next milestone: M7 (accessibility / detection exit)** — WP0 authors ADR-020 (overlay / biometric / request-identity) and ADR-021 (UsageStats poll detector), Accepted before any WP2 code; the 26 lock-engine rows above advance there.
