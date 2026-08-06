# Technical Architecture Specification — Part ↔ Section Index

The TAS is delivered as **nine `.docx` parts** (`Technical_Architecture_Specification_part_1..9`,
with GFM mirrors under `md/`). Its sections are numbered **contiguously §1–75** across the parts —
three parts per volume — so this table is all you need to turn a `§N` citation into its file.

| File (`part_N`) | Volume · Part | Sections | Topics |
|---|---|---|---|
| `part_1` | Vol I · I — Architectural Foundation | **§1–6** | Introduction, Goals, Drivers, Design Principles, Assumptions, Constraints |
| `part_2` | Vol I · II — System Architecture | **§7–12** | Overall System, Components, Layered, Modules, Cross-Cutting Services, Data Flow |
| `part_3` | Vol I · III — Security Architecture | **§13–20** | Security, Authentication, Authorization, **Cryptographic (§16)**, Secure Storage, Privacy, Cross-Cutting, Governance |
| `part_4` | Vol II · IV — Runtime Architecture | **§21–30** | Runtime, Lifecycle, Process, Background, Scheduling, Resource, Concurrency, State, Failure, Monitoring |
| `part_5` | Vol II · V — Data Architecture | **§31–40** | Data, Classification, Model, Database, Storage, Cache, Backup, Configuration, Lifecycle, Integrity |
| `part_6` | Vol II · VI — Operational Architecture | **§41–49** | Operational, Logging, Monitoring, Diagnostics, Config Mgmt, Update, Recovery, Deployment, Governance |
| `part_7` | Vol III · VII — Engineering Architecture | **§50–60** | Engineering, Build, Dependency, CI, CD, Testing, Versioning, Source Code, Documentation, Governance, Supply Chain |
| `part_8` | Vol III · VIII — Quality Architecture | **§61–69** | Quality, Reliability, Scalability, Maintainability, Availability, Observability, **Risk Mitigation (§67)**, Verification, **Conformance (§69)** |
| `part_9` | Vol III · IX — Appendices | **§70–75** | Technology Decisions, ADRs, **Traceability (§72)**, Glossary, Future Evolution, Conclusion |

Volume grouping: **Vol I** = parts 1–3, **Vol II** = parts 4–6, **Vol III** = parts 7–9.

Sub-sections live in the same file as their parent — e.g. §69.3 (Deviation Management) is in
`part_8` with §69; §72.2 (Document Relationships) is in `part_9` with §72.

## Ad-hoc lookup

Because each part has a Markdown mirror, grep for a top-level section to locate its file (the
result applies to the `.docx` too — `part_N.md` mirrors `part_N.docx`):

```bash
rg -l "^\*\*67\." docs/architecture/tas/md      # -> part_8 (Risk Mitigation Architecture)
```

## Maintenance

Regenerate this table by hand only if the TAS is re-delivered with a changed part/section
layout. The section numbers are the stable citation key: a `§N → other-doc` cross-reference
(docs/README convention 6) resolves to a file through this table.
