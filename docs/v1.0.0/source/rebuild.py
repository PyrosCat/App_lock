# -*- coding: utf-8 -*-
"""Rebuild the entire Version 1.0.0 draft package from source/staging/.

Usage:  python rebuild.py     (requires: pip install python-docx)

Single source of truth: the six consolidated Markdown files in staging/.
Everything else (consolidated DOCX/MD mirrors, 118 section DOCX/MD) is derived.
To change spec content, edit staging/*.md and re-run this — never hand-edit the
derived section or consolidated files.

Pipeline (all script inputs live in this source/ directory):
  1. build_v1_documents.py build  -> 6 consolidated DOCX + 6 consolidated MD mirrors -> ../ and ../markdown/
  2. split_v1_sections.py         -> 118 section MD   -> source/split-staging/ (intermediate)
  3. build_split_sections.py      -> 118 section DOCX -> source/split-docx/    (intermediate)
  4. final_package_audit.py       -> copies sections into ../sections and ../markdown/sections, then audits
"""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
STEPS = [
    ("build_v1_documents.py", ["build"]),
    ("split_v1_sections.py", []),
    ("build_split_sections.py", []),
    ("final_package_audit.py", []),
]


def main() -> None:
    for script, args in STEPS:
        print(f"\n=== {script} {' '.join(args)} ===")
        subprocess.run([sys.executable, str(HERE / script), *args], check=True, cwd=HERE)
    print("\nRebuild + audit complete.")


if __name__ == "__main__":
    main()
