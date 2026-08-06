#!/usr/bin/env python3
"""Extract plain text from the project's .docx documentation (no dependencies).

Usage:
    python scripts/extract_docx.py <file.docx> [more.docx ...]   # print to stdout
    python scripts/extract_docx.py --all [outdir]                # extract every docs/**/*.docx
                                                                 # to <outdir>/<name>.txt
                                                                 # (default outdir: build/doc-text)

Used during the 2026-07-19 baseline migration for hash-verification and RTM generation;
kept for future doc diffs and searches. The .docx files remain authoritative, and since
2026-08-03 committed markdown mirrors exist under each spec area's md/ subfolder
(scripts/docx_to_md.py — user-approved decision superseding M0_PLAN.md decision D2's
"no markdown mirrors").
"""
import glob
import html
import os
import re
import sys
import zipfile


def extract(path: str) -> str:
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    lines = []
    for para in re.split(r"</w:p>", xml):
        text = "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", para)).strip()
        if text:
            lines.append(html.unescape(text))
    return "\n".join(lines)


def main() -> None:
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    if args[0] == "--all":
        outdir = args[1] if len(args) > 1 else "build/doc-text"
        os.makedirs(outdir, exist_ok=True)
        for f in glob.glob("docs/**/*.docx", recursive=True):
            name = os.path.splitext(os.path.basename(f))[0] + ".txt"
            with open(os.path.join(outdir, name), "w", encoding="utf-8") as fh:
                fh.write(extract(f))
            print(f"{f} -> {outdir}/{name}")
    else:
        for f in args:
            print(extract(f))


if __name__ == "__main__":
    main()
