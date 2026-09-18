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

import build_split_sections
import build_v1_documents
import final_package_audit
import split_v1_sections

# Run each stage in this process. Any exception stops the rebuild, and no shell command is built.
STEPS = [
    ("build_v1_documents.py build", build_v1_documents.build_all),
    ("split_v1_sections.py", split_v1_sections.main),
    ("build_split_sections.py", build_split_sections.main),
    ("final_package_audit.py", final_package_audit.main),
]


def main() -> None:
    for label, run_step in STEPS:
        print(f"\n=== {label} ===")
        run_step()
    print("\nRebuild + audit complete.")


if __name__ == "__main__":
    main()
