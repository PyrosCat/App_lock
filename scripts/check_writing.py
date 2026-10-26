#!/usr/bin/env python3
"""Report findings of the mechanical writing rules in changed text. The script never edits files.
It runs on Python 3.12 and newer.

Usage:
    python scripts/check_writing.py                  # uncommitted changes and untracked files
    python scripts/check_writing.py --base main      # all changes since the merge base with main
    python scripts/check_writing.py FILE ...         # changed lines of these files only
    python scripts/check_writing.py --all FILE ...   # every line of these files

Run it anywhere inside the repository or one of its worktrees: git gives the changes. In a
worktree whose branch has no copy of this script, run the copy of the main checkout. The
script reads the working files, so a file with staged changes and other changes in the working
file is an error: the commit takes the staged text. A named file is checked under the name that
git gives it, so on a file system that ignores case, the case of the name does not matter. For a
deleted file, this holds for the letters A to Z only. A name in another case that can name two
files of the index is an error.
The limits and the rule texts come from WRITING_RULES.md at run time, from the copy in the
repository of this script. When a rule no longer has the wording that its check expects, the
check is skipped and an error names the rule.

Exit status: 0 for no findings, 1 for findings, 2 for an error, such as a failed or hung git
command, a missing file, a file over the size limit of 4 MiB, or a changed rule. The report
lists the findings in each case. A file that a git filter converts is an error too, because
its committed version holds the filtered text. With --all, the script reads no committed
version of a code file, so it checks a converted code file.

Checks:
    changelog.txt: each committed entry stays unchanged and in its order, and the file stays
    (5.1.4). The text before the first entry stays unchanged too, and a changelog without a
    committed version holds one header paragraph there and at least one entry (5.1.1). A new entry
    goes above the entries committed at the base or at HEAD (5.1.1). A new entry in the place of a
    removed committed entry counts as its new version, so it gets the finding of the removal only.

    Each entry that is new or changed since the base, or every entry with --all, gets these checks:
    the title form and its underline (5.1.1), one short introduction and one flat bullet list
    (5.1.2), the date of an uncommitted new entry (5.1.3), the words "would", "might", and "if"
    outside quotes (5.2.1), a sentence that starts with a bare file name (5.3.4), sentence length
    (5.3.5), repeated bullet openers (5.4.1), sentences in a row that start with "It" (5.4.2), an
    introduction that starts with "This change" (5.4.3), and bullets copied from the previous entry
    (5.4.4).

    Changed lines of every checked file: the section sign outside double quotes, code spans, and the
    fenced code blocks of documents (1.9), and the line width. An indented code block of a document
    counts as text, and so do a fence after ">" or a list marker and a table whose rows do not start
    with "|". Code lines have the limit of 120 that detekt sets for Kotlin. A document keeps the
    wrap width of its committed lines, and a new document gets 120. In a document and in the
    changelog, an unchanged line counts as changed when a change on another line, such as a removed
    fence, moves it into or out of a code block or a quote.

    Kotlin test files, in a source set whose name starts with "test" or contains "Test": JUnit 4
    assertion messages that are string literals and hold only templates and key names such as
    "seed=" (3.4). The docstring of kotlin_assertions.py states which calls count as JUnit 4, which
    calls and files the report lists to check by hand, and when a call is checked.

    Every checked file: text that is not valid UTF-8.

The checks cover only the mechanical part of the rules. The reading that section 7 of
WRITING_RULES.md requires is still needed.
"""

import argparse
import datetime
import errno
import io
import os
import re
import sys
import textwrap
from collections.abc import Mapping, Sequence
from collections.abc import Set as AbstractSet
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import TextIO

from writing_checks.changelog import changelog_body, check_changelog, parse_changelog, parse_version
from writing_checks.findings import CheckError, Finding
from writing_checks.git_repo import BYTE_ORDER_MARK, MAX_FILE_BYTES, SHORT_HASH_LENGTH, LineMap, Repo
from writing_checks.kotlin_assertions import (
    BY_HAND,
    CommittedCalls,
    KotlinText,
    check_assertions,
    context_moved,
    removed_declarations,
)
from writing_checks.rules import Rules
from writing_checks.text import (
    NEW_DOC_WIDTH,
    check_section_sign,
    check_width,
    context_changes,
    plural,
    prose_lines,
    split_lines,
    wrap_width,
)


class FileKind(Enum):
    CHANGELOG = "changelog"
    DOC = "document"
    CODE = "code"


CHANGELOG = "changelog.txt"
CODE_SUFFIXES = (".kt", ".kts", ".sh", ".py", ".ps1")
DOC_SUFFIXES = (".md",)
CODE_WIDTH = 120  # detekt MaxLineLength, also applied to the shell, Python, and PowerShell files
RULES_FILE = Path("docs", "process", "WRITING_RULES.md")
KOTLIN_TEST_PATH = re.compile(r"(^|/)src/(test\w*|\w*Test\w*)/")  # testDebug, androidTest, jvmTest
REPORT_WIDTH = 100  # wrap width of the rule texts in the report
RULE_ID_WIDTH = 6  # column of the rule IDs in the report, as in "5.3.5 "


def file_kind(path: str) -> FileKind | None:
    if path == CHANGELOG:
        return FileKind.CHANGELOG
    if path.endswith(DOC_SUFFIXES):
        return FileKind.DOC
    if path.endswith(CODE_SUFFIXES):
        return FileKind.CODE
    return None


def check_file(path: str, text: str, changed: AbstractSet[int] | None, committed: str | None,
               head_committed: str | None, rules: Rules, today: str, every_entry: bool = False,
               removals: Mapping[int, int] | None = None) -> list[Finding]:
    """Findings of one file. changed holds the changed line numbers, None for all lines, and
    removals the number of removed lines at each place, as LineChanges gives them. committed and
    head_committed are the texts at the base and at HEAD, None where absent. Only the changelog
    reads head_committed. Of the code files, only Kotlin test files read removals."""
    lines = split_lines(text)
    kind = file_kind(path)
    committed_lines = None if committed is None else split_lines(committed)
    removed = {} if removals is None else removals
    if changed is not None and committed_lines is not None and kind is not FileKind.CODE:
        changed = changed | context_changes(lines, committed_lines, changed, removed)
    found = check_section_sign(path, lines, changed, rules, across_lines=kind is not FileKind.CODE)
    if kind is FileKind.CHANGELOG:
        working = parse_version(lines)
        base = parse_version(committed_lines) if committed_lines is not None else None
        head = base if head_committed is committed \
            else parse_version(split_lines(head_committed)) if head_committed is not None else None
        found += check_changelog(path, working, base, head, rules, today, every_entry)
        width = wrap_width([line for _, line in changelog_body(base)]) if base is not None else NEW_DOC_WIDTH
        if width:
            found += check_width(path, prose_lines_of(changelog_body(working)), changed, width,
                                 "wrap width of the committed entries")
    elif kind is FileKind.DOC:
        width = wrap_width(committed_lines) if committed_lines is not None else NEW_DOC_WIDTH
        reason = "new document" if committed is None else "wrap width of the committed lines"
        if width:
            found += check_width(path, prose_lines(lines), changed, width, reason)
    elif kind is FileKind.CODE:
        numbered = [(n, line) for n, line in enumerate(lines, 1)
                    if not (path.endswith((".kt", ".kts"))
                            and line.startswith(("import ", "package ")))]
        found += check_width(path, numbered, changed, CODE_WIDTH, "detekt line limit")
        if kotlin_test(path):
            found += kotlin_test_findings(path, KotlinText(text), changed, committed, removed, rules)
    return found


def kotlin_test(path: str) -> bool:
    return path.endswith(".kt") and bool(KOTLIN_TEST_PATH.search(path))


def kotlin_test_findings(path: str, source: KotlinText, changed: AbstractSet[int] | None, committed: str | None,
                         removals: Mapping[int, int], rules: Rules) -> list[Finding]:
    """Findings and calls to check by hand of a Kotlin test file, against its committed text.
    Removed lines can change calls too. A change that gives a call another class, such as a
    removed import, rechecks every call, as context_moved() describes. A call is rechecked when
    the committed text holds no call of its text on its line, as CommittedCalls tells, and a
    removed declaration rechecks the calls whose message uses its name."""
    if changed is None or committed is None:
        return check_assertions(path, source, changed, rules)
    old = KotlinText(committed)
    if context_moved(source, old):
        return check_assertions(path, source, None, rules)
    calls = CommittedCalls(old, LineMap(changed, removals))
    return check_assertions(path, source, changed, rules, calls, removals,
                            removed_declarations(source.masked, old.masked))


def prose_lines_of(numbered: Sequence[tuple[int, str]]) -> list[tuple[int, str]]:
    """prose_lines() for (number, line) pairs that keep their own numbers."""
    by_index = prose_lines([line for _, line in numbered])
    return [(numbered[index - 1][0], line) for index, line in by_index]


def check_path(repo: Repo, path: str, every_line: bool, rules: Rules, today: str,
               driver: str | None) -> list[Finding] | None:
    """Findings of one file of the repository, or None when its checked text did not change.
    driver is the git filter driver that converts the file, or None.
    Removed lines matter in three cases: they can change a committed changelog entry, which is then
    compared in full, they can move the lines of a document or of the changelog into or out of a
    code block or a quote, as context_changes() describes, and they can change the calls of a
    Kotlin test file, as kotlin_test_findings() describes.

    Memory: the working text and the committed versions of one file at a time, with their parsed
    forms, so O(S) for a file of S bytes, which MAX_FILE_BYTES limits."""
    full = repo.root / path
    size = full.stat().st_size
    if size > MAX_FILE_BYTES:
        raise CheckError(f"{path} has {size} bytes, over the limit of {MAX_FILE_BYTES} bytes that the script reads")
    data = full.read_bytes()
    try:
        text = data.decode("utf-8").removeprefix(BYTE_ORDER_MARK)
    except UnicodeDecodeError as bad:
        return [Finding(path, data.count(b"\n", 0, bad.start) + 1, "encoding", "not valid UTF-8")]
    kind = file_kind(path)
    # With every line checked, the committed version of a code file decides nothing.
    committed = None if every_line and kind is FileKind.CODE else repo.committed_text(repo.base, path)
    # The committed version of a file that a git filter converts holds the filtered text, not the
    # text that the script reads. The changelog is also compared with its version at HEAD.
    if driver is not None and (kind is FileKind.CHANGELOG or committed is not None):
        raise CheckError(f'git converts {path} with the filter driver "{driver}", so the script cannot compare '
                         "it with its committed version")
    changes = None if every_line or committed is None else repo.changed_lines(repo.base, path)
    changed = None if changes is None else changes.added
    removals: Mapping[int, int] = {} if changes is None else changes.removals
    if (changed is not None and not changed and kind is not FileKind.CHANGELOG
            and not (removals and (kind is FileKind.DOC or kotlin_test(path)))):
        # Without a changed line, only removed lines can change the changelog, the code blocks and
        # quotes of a document, and the calls of a Kotlin test file. The other checks read changed lines.
        return None
    head_committed = committed  # only the changelog reads the version at HEAD
    if kind is FileKind.CHANGELOG and repo.head != repo.base:
        head_committed = repo.committed_text(repo.head, path)
    return check_file(path, text, changed, committed, head_committed, rules, today, every_line, removals)


def deleted_changelog(repo: Repo, rules: Rules) -> Finding | None:
    """The finding for a deleted changelog whose committed versions hold entries, or None. The
    changelog counts as deleted when its working file is missing, or when the index does not hold
    a file of its name, as after git rm --cached or a rename. A file system that ignores case finds
    a file renamed only in case under the old name, so the index decides."""
    if "5.1.4" not in rules.usable:
        return None
    in_work_folder = (repo.root / CHANGELOG).is_file()
    if in_work_folder and repo.indexed(CHANGELOG):
        return None
    texts = [repo.committed_text(commit, CHANGELOG) for commit in dict.fromkeys((repo.base, repo.head))]
    counts = [len(parse_changelog(split_lines(text))) for text in texts if text is not None]
    if not counts or not max(counts):
        return None
    removed = plural(max(counts), "committed entry", "committed entries")
    deletion = "the index does not hold the file" if in_work_folder else "the file is deleted"
    return Finding(CHANGELOG, 1, "5.1.4", f"{deletion}, which removes its {removed}")


@dataclass
class Report:
    """The findings of a run, the counts of checked and skipped files, and the errors."""
    findings: list[Finding] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    checked: int = 0
    skipped: int = 0


def check_files(repo: Repo, paths: Sequence[str], named: bool, every_line: bool, rules: Rules,
                today: str) -> Report:
    """The report for the files. named marks files named on the command line: a named file that
    is missing is an error. A deleted changelog is reported when no files are named, or when one of
    them is the changelog in any case: Repo.git_names() gives a named changelog.txt the name of a
    file renamed only in case. A checked kind of file with staged changes and other changes in the
    working file is an error, among the named files when files are named."""
    report = Report()
    in_scope = set(paths)
    try:
        partly_staged = repo.partly_staged()
    except (CheckError, OSError) as problem:
        report.errors.append(str(problem))
        return report
    for path in sorted(partly_staged):
        if file_kind(path) is not None and (not named or path in in_scope):
            report.errors.append(f"{path} has staged changes and other changes in the working file, so the commit "
                                 "takes text that the script does not read; stage the file again or unstage it")
    if not named or any(path.casefold() == CHANGELOG for path in paths):
        try:
            gone = deleted_changelog(repo, rules)
        except (CheckError, OSError) as problem:
            report.errors.append(str(problem))
            gone = None
        if gone:
            report.findings.append(gone)
            report.checked += 1
            paths = [path for path in paths if path != CHANGELOG]
    try:
        filters = repo.filter_drivers([path for path in paths if file_kind(path) is not None])
    except (CheckError, OSError) as problem:
        report.errors.append(str(problem))
        return report
    for path in paths:
        try:  # is_file() too can fail, for example on a folder that denies access
            if not (repo.root / path).is_file():
                # git lists an untracked nested repository as a directory. A named path is an error.
                if named:
                    report.errors.append(f"{path} is not a file")
                report.skipped += 1
                continue
            if file_kind(path) is None:
                report.skipped += 1
                continue
            found = check_path(repo, path, every_line, rules, today, filters.get(path))
        except (CheckError, OSError) as problem:
            report.errors.append(str(problem))
            continue
        if found is not None:
            report.checked += 1
            report.findings += found
    return report


def print_report(findings: Sequence[Finding], by_hand: Sequence[Finding], rules: Rules, stdout: TextIO) -> None:
    for finding in sorted(findings, key=lambda f: (f.path, f.line, f.rule)):
        print(f"{finding.path}:{finding.line}: [{finding.rule}] {finding.message}", file=stdout)
    if by_hand:
        print("\nCheck by hand (the script does not read these messages; they are not findings):",
              file=stdout)
        for item in sorted(by_hand, key=lambda f: (f.path, f.line)):
            print(f"{item.path}:{item.line}: {item.message}", file=stdout)
    cited = sorted({f.rule for f in findings if f.rule in rules.text},
                   key=lambda r: [int(p) for p in r.split(".")])
    if cited:
        print("\nRules cited (WRITING_RULES.md):", file=stdout)
        for rule in cited:
            indent = f"  {rule:<{RULE_ID_WIDTH}} "
            print(textwrap.fill(rules.text[rule], width=REPORT_WIDTH, initial_indent=indent,
                                subsequent_indent=" " * len(indent)), file=stdout)


def argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Report findings of the mechanical rules of "
                                     "WRITING_RULES.md in changed text.")
    parser.add_argument("paths", nargs="*", help="files to check (default: the changed files)")
    parser.add_argument("--base", default="HEAD", help="compare with the merge base of this "
                        "commit and HEAD (default: HEAD, so only uncommitted changes)")
    parser.add_argument("--all", action="store_true", help="check every line of the files")
    parser.add_argument("--rules", help="the WRITING_RULES.md to read (default: the copy in "
                        "the repository of this script)")
    return parser


def main(argv: Sequence[str] | None = None, cwd: str | Path | None = None, today: str | None = None,
         stdout: TextIO | None = None, stderr: TextIO | None = None) -> int:
    stdout = sys.stdout if stdout is None else stdout
    stderr = sys.stderr if stderr is None else stderr
    parser = argument_parser()
    args = parser.parse_args(argv)
    named: list[str] = args.paths
    every_line: bool = args.all
    base: str = args.base
    if every_line and not named:
        parser.error("--all needs the files to check")
    work_dir = Path.cwd() if cwd is None else Path(cwd)
    rules_path = work_dir / args.rules if args.rules else Path(__file__).absolute().parent.parent / RULES_FILE
    try:
        rules = Rules(rules_path.read_text(encoding="utf-8"))
        repo = Repo(work_dir, base)
        paths = [] if named else repo.changed_files()
    except UnicodeDecodeError:
        print(f"error: {rules_path} is not valid UTF-8", file=stderr)
        return 2
    except (CheckError, OSError) as problem:
        print(f"error: {problem}", file=stderr)
        return 2
    if today is None:
        today = datetime.datetime.now().astimezone().date().isoformat()
    errors = list(rules.errors)
    named_paths: list[str] = []
    for name in named:
        try:
            named_paths.append(repo.relative(work_dir / name))
        except CheckError as problem:
            errors.append(str(problem))
    try:
        names, problems = repo.git_names(named_paths) if named_paths else ([], [])
    except CheckError as problem:
        names, problems = named_paths, [str(problem)]
    paths += names
    errors += problems
    report = check_files(repo, list(dict.fromkeys(paths)), bool(named), every_line, rules, today)
    errors += report.errors
    by_hand = [f for f in report.findings if f.rule == BY_HAND]
    findings = [f for f in report.findings if f.rule != BY_HAND]
    print_report(findings, by_hand, rules, stdout)
    compared = "HEAD" if repo.base == repo.head else f"the merge base {repo.base[:SHORT_HASH_LENGTH]} of {base}"
    scope = "every line" if every_line else f"changes against {compared}"
    print(f"\ncheck_writing: {plural(len(findings), 'finding')} in {plural(report.checked, 'checked file')}"
          f" ({scope}); {plural(len(by_hand), 'call')} to check by hand; "
          f"{plural(report.skipped, 'other file')} skipped", file=stdout)
    for error in errors:
        print(f"error: {error}", file=stderr)
    return 2 if errors else (1 if findings else 0)


if __name__ == "__main__":
    # Python writes Unicode to a console. Redirected output gets UTF-8, not the ANSI code page.
    for stream in (sys.stdout, sys.stderr):
        if isinstance(stream, io.TextIOWrapper):
            stream.reconfigure(encoding=None if stream.isatty() else "utf-8", errors="backslashreplace")
    try:
        sys.exit(main())
    except OSError as closed:
        # A reader such as head can close the pipe early. Windows reports EINVAL for this.
        if closed.errno not in (errno.EPIPE, errno.EINVAL):
            raise
        os._exit(1)
