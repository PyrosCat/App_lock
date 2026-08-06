#!/usr/bin/env python3
"""Convert the active .docx spec baseline to committed Markdown mirrors (via pandoc).

Usage:
    python scripts/docx_to_md.py            # convert every active-baseline docx
    python scripts/docx_to_md.py --check    # verify committed md is current (no writes);
                                            # exit 1 if stale/missing

Output layout (per user decision 2026-08-03, superseding M0 decision D2):
    <area>/md/<same-basename>.md            derived Markdown, never hand-edited
    <area>/md/media/<basename>/...          images extracted from the docx
    <area>/md/MANIFEST.txt                  sha256 of each source docx -> output md

The .docx files remain the authoritative client-received originals (docs/README.md
rule 7); the md/ mirrors are regenerable reading/search copies. When a spec revision
arrives as docx, re-run this script and commit the result.

Requires pandoc (windows: winget install --id JohnMacFarlane.Pandoc). Fleet machines
consuming the committed md/ output do not need pandoc.
"""
import hashlib
import os
import subprocess
import sys

# Active baseline: per-area, non-recursive. docs/archive/** is deliberately excluded
# (frozen, superseded documents are not mirrored).
AREAS = [
    "docs/srs",
    "docs/nfr",
    "docs/architecture/tas",
    "docs/design/sds",
    "docs/design/dds",
    "docs/testing/tsp",
    "docs/security/tm",
    "docs/process",
]


def find_pandoc() -> str:
    from shutil import which
    cand = which("pandoc")
    if cand:
        return cand
    for base in (os.environ.get("LOCALAPPDATA", ""), os.environ.get("ProgramFiles", "")):
        p = os.path.join(base, "Pandoc", "pandoc.exe")
        if base and os.path.isfile(p):
            return p
    sys.exit("pandoc not found: winget install --id JohnMacFarlane.Pandoc")


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def area_sources(area: str) -> list:
    return sorted(
        f for f in os.listdir(area)
        if f.endswith(".docx") and not f.startswith("~$")
    )


def convert(pandoc: str) -> None:
    total = 0
    for area in AREAS:
        sources = area_sources(area)
        if not sources:
            continue
        md_dir = os.path.join(area, "md")
        os.makedirs(md_dir, exist_ok=True)
        manifest = []
        for name in sources:
            base = os.path.splitext(name)[0]
            src_abs = os.path.abspath(os.path.join(area, name))
            # cwd=md_dir so image links inside the md are relative to the md file.
            subprocess.run(
                [pandoc, "-f", "docx", "-t", "gfm", "--wrap=none",
                 "--extract-media", f"media/{base}", src_abs, "-o", f"{base}.md"],
                cwd=md_dir, check=True,
            )
            manifest.append(f"{sha256(src_abs)}  {name}  {base}.md")
            total += 1
        with open(os.path.join(md_dir, "MANIFEST.txt"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(manifest) + "\n")
        print(f"{area}: {len(sources)} converted")
    print(f"total: {total}")


def check() -> None:
    stale = []
    for area in AREAS:
        md_dir = os.path.join(area, "md")
        recorded = {}
        mpath = os.path.join(md_dir, "MANIFEST.txt")
        if os.path.isfile(mpath):
            with open(mpath, encoding="utf-8") as fh:
                for line in fh:
                    digest, name, _out = line.rstrip("\n").split("  ")
                    recorded[name] = digest
        for name in area_sources(area):
            base = os.path.splitext(name)[0]
            if not os.path.isfile(os.path.join(md_dir, base + ".md")):
                stale.append(f"missing md: {area}/{name}")
            elif recorded.get(name) != sha256(os.path.join(area, name)):
                stale.append(f"stale md (source changed): {area}/{name}")
        for name in recorded:
            if not os.path.isfile(os.path.join(area, name)):
                stale.append(f"orphaned md (source gone): {area}/md for {name}")
    if stale:
        print("\n".join(stale))
        sys.exit(1)
    print("md mirrors are current")


def main() -> None:
    os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if "--check" in sys.argv[1:]:
        check()
    else:
        convert(find_pandoc())


if __name__ == "__main__":
    main()
