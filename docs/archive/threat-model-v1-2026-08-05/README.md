# Threat Model v1 — archived 2026-08-06 (superseded)

Everything in this directory is **historical**. It is retained for reference and audit only
and must not be used as current security guidance. The authoritative Threat Model is **v2**
at `docs/security/tm/` — see `docs/security/tm/VERSION.md`.

## What this is

The **v1** Threat Model exactly as received from the client (16 `.docx` sections, filed
`c985445`, 2026-08-05). In v1, Sections 1–5 model the Android **Accessibility** framework as
the *single, mandatory* foreground-detection and enforcement mechanism (as do §6–16). That framing was
superseded by **ADR-013A** (two-tier detection: UsageStatsManager + Usage Access baseline,
Accessibility demoted to an optional enhancement) and by the Threat Model's own §16.32.

These files are the **verbatim v1 baseline**, preserved unchanged (client-received-originals
rule, `docs/README.md` convention 7).

## How v2 was produced

| Item | Detail |
|---|---|
| §1–5 | Reconciled to the two-tier model via the **approved reconciliation** below, applied as a **superset merge** (all approved two-tier text incorporated; no original threat/scope/control coverage dropped). Approved 2026-08-05. Now live in `docs/security/tm/`. |
| §6–9 | Reconciled the same way; **corrected** before landing (§6.32 INV-011/012 stable-ID collision fixed → new detection invariants added as INV-013/014; §6.14 and §6.28 added as draft omissions; superset restores; AS-019/020/021 traceability). Approved 2026-08-06. Now live in `docs/security/tm/`. |
| §10–16 | Reconciled the same way; **corrected** before landing (§16.34.4 vs §16.32.15 contradiction resolved; both stale forward-refs fixed — §12.44 and §14.35, the latter missing from the client draft and supplied here; HF-006/007 reclassified "Accepted limitation" → "Open with two-tier planned mitigation"; diagram notation cleaned; supersets preserved). Approved 2026-08-06. Now live in `docs/security/tm/`. This completes the full §1–16 reconciliation. |

## Files here

- `Threat_Model_section_1..16 *.docx` — the 16 verbatim v1 sections.
- `Threat_Model_Reconciliation_Sections_1-5_APPROVED.md` / `.docx` — the approved §1–5
  reconciliation overlay (drop-in replacement subsections); the authoritative record of
  *what changed* from v1 §1–5 to v2 §1–5. Approved 2026-08-05.
- `Threat_Model_Reconciliation_Sections_6-9_PROPOSAL.md` / `.docx` — the approved (corrected +
  tightened) §6–9 reconciliation; the record of *what changed* from v1 §6–9 to v2 §6–9.
  Approved 2026-08-06.
- `Threat_Model_Reconciliation_Sections_10-16_PROPOSAL.md` / `.docx` — the approved (corrected)
  §10–16 reconciliation; the record of *what changed* from v1 §10–16 to v2 §10–16.
  Approved 2026-08-06.

The v1 Markdown mirrors of §1–16 (before the merge) are recoverable from git at commit
**`c985445`** under `docs/security/tm/md/`.
