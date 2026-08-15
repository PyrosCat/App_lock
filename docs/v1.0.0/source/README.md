# Version 1.0.0 — Build Source

This directory is the **self-contained build source** for the client-approved Version 1.0.0
specification package (the DOCX and Markdown files in the parent `v1.0.0/` folder). The pipeline
lives with the specs it builds and is portable across fleet machines (script paths derive from their
own location, no absolute paths).

## Single source of truth

`staging/` holds the **six consolidated Markdown files** — the only files you edit by hand:

```
staging/SRS_v1_0_0.md            staging/SDS_v1_0_0.md
staging/NFR_v1_0_0.md            staging/DDS_v1_0_0.md
staging/Threat_Model_v1_0_0.md   staging/UI_UX_Specification_v1_0_0.md
```

Everything else in `v1.0.0/` is **derived** from these:

- `../{six}.docx` — consolidated DOCX (the primary deliverable)
- `../markdown/{six}.md` — consolidated Markdown mirrors
- `../sections/*.docx` — 118 section DOCX
- `../markdown/sections/*.md` — 118 section Markdown mirrors

**Do not hand-edit the derived files** — they are overwritten on every rebuild. Change content in
`staging/` and rebuild.

## Rebuild

```bash
pip install python-docx      # one-time dependency
python rebuild.py            # runs the 4 steps below in order, then audits
```

`rebuild.py` runs:

1. `build_v1_documents.py build` → consolidated DOCX + MD mirrors (styled via `_style_reference.docx`;
   normalizes legacy mojibake in the sources).
2. `split_v1_sections.py` → section MD into `split-staging/`.
3. `build_split_sections.py` → section DOCX into `split-docx/`.
4. `final_package_audit.py` → copies the section files into the deliverable, then audits the whole
   package.

## What the audit enforces

`final_package_audit.py` is both a copy step and a gate. It fails the build if any of these regress,
so they double as **client-facing cleanliness rules** for the source text:

- No internal-process terms leak into the specs: `ADR-`, `roadmap`, `milestone`, `work package`,
  `project governance`.
- No statement that an **App Lock Accessibility service is required/used/depended on** (1.0.0 is
  Usage Access + overlay only; accessibility is deferred to 2.0.0).
- Scope terms present: `Android 11`, `Android 15`, `API levels 30 through 35`, `Usage Access`.
- **FR/NFR identifier accounting**: the staging SRS/NFR must reference exactly the same FR-### and
  NFR-XXX-### identifiers as `docs/v2.0.0/srs/md/` and `docs/v2.0.0/nfr/md/` (nothing added or dropped).
- DOCX hygiene: anonymized metadata, no comments, no tracked changes, Word heading styles present.

## Generated / ignorable

`split-staging/`, `split-docx/`, and `__pycache__/` are build intermediates — gitignored, safe to
delete; they are regenerated on every rebuild.

## Provenance

Detection scope is fixed by **ADR-013B** (2026-08-14): Usage Access baseline + mandatory
"Display over other apps" overlay presentation; the optional Accessibility enhancement is deferred
to 2.0.0. The 1.0.0 specification was **client-approved 2026-08-14** and adopted as the active
baseline per **ADR-019** (version split: `docs/v1.0.0/` active, `docs/v2.0.0/` = the 2.0.0 target).
