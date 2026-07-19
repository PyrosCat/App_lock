# Archive — Superseded Project Documentation

Everything in this directory is **historical**. It is retained for reference and audit only and
must not be used as a basis for implementation. The authoritative baseline (adopted 2026-07-19)
lives in `docs/srs/`, `docs/nfr/`, `docs/architecture/`, `docs/design/`, and `docs/process/`.
Full transition analysis: `docs/process/MIGRATION_ASSESSMENT.md`.

| File | What it was | Superseded by | Notes |
|---|---|---|---|
| `Requirements_section_13_FutureExpansion&AdvancedFeatures.docx` | Old SRS section 13, FR-226..250: cloud sync, remote lock, multi-device, enterprise, plugins, AI assistant, premium/licensing | Removed from the baseline; a subset survives as *non-committal* extension candidates in TAS Part IX §74 | ⚠ **FR-numbering collision — read `FR-226-250-RENUMBERING-NOTICE.md`** |
| `Technical Architecture Specification.docx` | Old TAS: concrete engineering blueprint (tech stack, package tree, class sketches, 5-phase plan) | `docs/architecture/tas/` parts 1–9 | Accurately describes the **as-built** Phase 1–3 system — useful when reading pre-2026-07-19 code/commits |
| `Software Design Specification.docx` | Old SDS: class/interface sketches, sprint breakdown | `docs/design/sds/` sections 1–17 | Interface sketches informed the new §7–17 component designs |
| `Android App Lock System Design Diagrams.docx` | ~50 system diagrams | New TAS diagrams + future ADR/architecture artifacts | Selectively re-derive under architecture governance if needed |
| `Draft_design_App_Lock.docx` | Early high-level design draft | Entire new baseline | Historical only |
| `app lock.docx` | Initial feature brainstorm/notes | SRS | Historical only |
| `Functional Requirements Completion Summary.docx` | "250/250 requirements complete" tracker | `docs/process/rtm/rtm.csv` | Was about *specification* completion, not implementation; counts now wrong (375 FRs) |

Old SRS sections 1–12 are **not** archived: their content is byte-identical to the new
`docs/srs/` sections 1–12 (hash-verified during migration, see MIGRATION_ASSESSMENT.md Phase 5),
so the duplicates were deleted. Git history retains the original files.
