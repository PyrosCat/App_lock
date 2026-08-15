from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent
STAGING = ROOT / "staging"
OUT = ROOT / "split-staging"


def slug(text: str) -> str:
    text = re.sub(r"^[A-Za-z ]*\d+[.\s—-]*", "", text).strip()
    words = re.findall(r"[A-Za-z0-9]+", text)
    return "".join(words[:8]) or "Section"


def h2_sections(lines: list[str], *, include_parts: bool = False) -> list[tuple[str, list[str]]]:
    starts = []
    for i, line in enumerate(lines):
        if not line.startswith("## "):
            continue
        title = line[3:].strip()
        if title.startswith("Version ") or title == "Document Status":
            continue
        if not include_parts and (title.startswith("Part ") or title.startswith("Volume ")):
            continue
        starts.append((i, title))
    result = []
    for n, (start, title) in enumerate(starts):
        end = starts[n + 1][0] if n + 1 < len(starts) else len(lines)
        result.append((title, lines[start:end]))
    return result


def srs_sections(lines: list[str]) -> list[tuple[str, list[str]]]:
    ranges = []
    functional_start = next(i for i, x in enumerate(lines) if x.startswith("## 4. Functional Requirements"))
    functional_end = next(i for i, x in enumerate(lines) if x.startswith("## 5. Acceptance Boundaries"))
    for i, line in enumerate(lines):
        if functional_start < i < functional_end and line.startswith("### 4."):
            ranges.append((i, line[4:].strip()))
    result = [("1. Purpose and Scope", lines[next(i for i,x in enumerate(lines) if x.startswith('## 1.')):next(i for i,x in enumerate(lines) if x.startswith('## 2.'))]),
              ("2. Users and Operating Context", lines[next(i for i,x in enumerate(lines) if x.startswith('## 2.')):next(i for i,x in enumerate(lines) if x.startswith('## 3.'))]),
              ("3. Controlled Terms", lines[next(i for i,x in enumerate(lines) if x.startswith('## 3.')):functional_start])]
    for n, (start, title) in enumerate(ranges):
        end = ranges[n + 1][0] if n + 1 < len(ranges) else functional_end
        content = lines[start:end]
        content[0] = "## " + title.replace("4.", "Section ", 1)
        result.append((title, content))
    excluded_sections = [
        ("4.5 Vault", "FR-106 through FR-125", "Vault storage, file handling, Vault cryptography, Vault migration, and Vault verification"),
        ("4.6 Scheduling and Automation", "FR-126 through FR-145", "schedules, profiles, triggers, rules, recommendations, overrides, and automation records"),
        ("4.10 Backup and Recovery", "FR-196 through FR-205", "backup, restore, recovery password, retention, and new-device migration"),
        ("4.18 Secure Development and Maintenance", "FR-351 through FR-375", "functional product obligations; applicable quality and design outcomes are stated once in the NFR and design specifications"),
    ]
    exclusion_map = {title: (identifiers, capability) for title, identifiers, capability in excluded_sections}
    ordered = []
    operative = {title: body for title, body in result[3:]}
    for number in range(1, 19):
        prefix = f"4.{number}"
        match = next(((title, body) for title, body in operative.items() if title.startswith(prefix + " ")), None)
        if match:
            ordered.append(match)
            continue
        title = next(title for title in exclusion_map if title.startswith(prefix + " "))
        identifiers, capability = exclusion_map[title]
        ordered.append((title, [
            "## Section " + title.removeprefix("4."),
            "",
            f"{identifiers} are not included as normative Version 1.0.0 requirements. Their identifiers remain reserved and are not renumbered or reused.",
            "",
            f"Version 1.0.0 creates no screen, data, permission, background work, compatibility, migration, or acceptance obligation for {capability}.",
        ]))
    result = result[:3] + ordered
    result.append(("5. Acceptance Boundaries", lines[functional_end:next(i for i,x in enumerate(lines) if x.startswith('## Appendix A'))]))
    result.append(("Appendix A - Requirement Disposition", lines[next(i for i,x in enumerate(lines) if x.startswith('## Appendix A')):]))
    return result


def uiux_sections(lines: list[str]) -> list[tuple[str, list[str]]]:
    starts = [(i, line[4:].strip()) for i, line in enumerate(lines) if re.match(r"^### \d+\. ", line)]
    result = []
    for n, (start, title) in enumerate(starts):
        end = starts[n + 1][0] if n + 1 < len(starts) else next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## Appendices")), len(lines))
        content = lines[start:end]
        content[0] = "## " + title
        result.append((title, content))
    appendix_start = next(i for i, x in enumerate(lines) if x.startswith("## Appendices"))
    app_starts = [(i, line[4:].strip()) for i, line in enumerate(lines) if i > appendix_start and line.startswith("### Appendix ")]
    for n, (start, title) in enumerate(app_starts):
        end = app_starts[n + 1][0] if n + 1 < len(app_starts) else len(lines)
        content = lines[start:end]
        content[0] = "## " + title
        result.append((title, content))
    return result


def threat_sections(lines: list[str]) -> list[tuple[str, list[str]]]:
    starts = [(i, line[3:].strip()) for i, line in enumerate(lines) if re.match(r"^## \d+\. ", line)]
    appendix_start = next((i for i, x in enumerate(lines) if x.startswith("## Appendix A")), len(lines))
    result = []
    for n, (start, title) in enumerate(starts):
        end = starts[n + 1][0] if n + 1 < len(starts) else appendix_start
        result.append((title, lines[start:end]))
    app_starts = [(i, line[3:].strip()) for i, line in enumerate(lines) if line.startswith("## Appendix ")]
    completion = next((i for i, x in enumerate(lines) if x.startswith("## Document Completion Statement")), len(lines))
    for n, (start, title) in enumerate(app_starts):
        end = app_starts[n + 1][0] if n + 1 < len(app_starts) else completion
        result.append((title, lines[start:end]))
    return result


def split_file(path: Path) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    doc_title = lines[0]
    version = next((x for x in lines[1:12] if x.startswith("## Version") or x.startswith("> Version")), "> Version 1.0.0 — Draft")
    if path.name.startswith("SRS_"):
        sections = srs_sections(lines)
    elif path.name.startswith("UI_UX_"):
        sections = uiux_sections(lines)
    elif path.name.startswith("Threat_"):
        sections = threat_sections(lines)
    else:
        sections = h2_sections(lines)
    family = path.stem.replace("_v1_0_0", "")
    for index, (title, body) in enumerate(sections, 1):
        if title.startswith("Volume ") or title == "Document Completion Statement":
            continue
        kind = "Appendix" if title.startswith("Appendix") else "section"
        label_match = re.match(r"^(\d+|Appendix [A-Z])", title)
        label = (label_match.group(1) if label_match else str(index)).replace(" ", "_")
        filename = f"{family}_{kind}_{label}_{slug(title)}.md"
        content = [doc_title, "", version, "", *body]
        (OUT / filename).write_text("\n".join(content).rstrip() + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.md"):
        old.unlink()
    for path in sorted(STAGING.glob("*.md")):
        split_file(path)
    print(f"Created {len(list(OUT.glob('*.md')))} section Markdown files in {OUT}")


if __name__ == "__main__":
    main()
