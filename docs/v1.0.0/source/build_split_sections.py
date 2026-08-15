from __future__ import annotations

import shutil
from pathlib import Path

import build_v1_documents as builder


QA = Path(__file__).resolve().parent
SOURCES = QA / "split-staging"
OUTPUT = QA / "split-docx"


def main() -> None:
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    OUTPUT.mkdir(parents=True)
    old_output = builder.OUTPUT_DIR
    builder.OUTPUT_DIR = OUTPUT
    try:
        for source in sorted(SOURCES.glob("*.md")):
            builder.build_one(source, source.with_suffix(".docx").name)
    finally:
        builder.OUTPUT_DIR = old_output
    print(f"Built {len(list(OUTPUT.glob('*.docx')))} section DOCX files")


if __name__ == "__main__":
    main()
