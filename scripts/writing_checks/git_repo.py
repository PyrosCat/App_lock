"""The git commands of the script. Repo reads the changed files, the committed texts, and the
changed lines of a file."""

import bisect
import os
import posixpath
import re
import string
import subprocess
from collections import Counter, defaultdict
from collections.abc import Iterable, Mapping, Sequence
from itertools import accumulate
from pathlib import Path
from typing import NamedTuple

from writing_checks.findings import CheckError

# A byte order mark at the start of a file is not text, and Kotlin ignores it. The script removes
# it, because as part of line 1 it hides an import or a code fence.
BYTE_ORDER_MARK = chr(0xFEFF)
GIT_TIMEOUT_SECONDS = 60  # a git command that runs longer is taken as hung
# The size limit of each version of a checked file. It bounds the memory used to check one file.
MAX_FILE_BYTES = 4 * 1024 * 1024
SHORT_HASH_LENGTH = 7


def git(args: Sequence[str], cwd: str | Path, stdin: str | None = None) -> str:
    """The output of a git command, which reads stdin when it is given. A failed or hung command
    raises CheckError."""
    # The arguments are fixed options, repository paths, and commit names. Repo refuses a commit
    # name that starts with "-", so git cannot read it as an option. No shell reads the arguments,
    # and --literal-pathspecs makes git read each path as a file name, not as a pattern.
    command = " ".join(args)
    try:
        done = subprocess.run(["git", "--literal-pathspecs", *args], cwd=cwd, capture_output=True, check=False,
                              input=None if stdin is None else stdin.encode("utf-8"), timeout=GIT_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as hung:
        raise CheckError(f"git {command} did not finish within {GIT_TIMEOUT_SECONDS} seconds") from hung
    # The output is decoded from bytes. Text mode would turn a lone carriage return into a line break.
    if done.returncode != 0:
        raise CheckError(f"git {command} failed: {done.stderr.decode('utf-8', 'replace').strip()}")
    return done.stdout.decode("utf-8", "replace")


HUNK_HEADER = re.compile(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
DELETED_FILE = re.compile(r"^deleted file mode ", re.MULTILINE)  # a header line of git diff


class LineChanges(NamedTuple):
    """The changes of a diff in the lines of the new file: the lines that it adds or changes, and
    the number of lines that it removes at each place n, between line n and line n + 1."""
    added: set[int]
    removals: Counter[int]


def line_changes(diff: str) -> LineChanges:
    """The changes of a diff: the "+" lines of its hunks, and the place of each "-" line.
    Context lines, which the GIT_DIFF_OPTS variable and the diff.interHunkContext setting can
    add, do not count. With the diff.suppressBlankEmpty setting, an empty context line has no
    leading space, so an empty hunk line counts as a context line, as git apply reads it. The
    diff is split at newlines only, because a line of the file can hold other line separators."""
    added: set[int] = set()
    removals: Counter[int] = Counter()
    number: int | None = None  # the line of the new file that the next hunk line holds
    for line in diff.split("\n"):
        hunk = HUNK_HEADER.match(line)
        if hunk:
            # A header "+n,0" names the line before the hunk, which holds no line of the new file.
            number = int(hunk.group(1)) + (1 if hunk.group(2) == "0" else 0)
        elif number is not None and line.startswith("-"):
            removals[number - 1] += 1
        elif number is not None and (not line or line.startswith(("+", " "))):
            if line.startswith("+"):
                added.add(number)
            number += 1
    return LineChanges(added, removals)


class LineMap:
    """The committed line of each unchanged line of the working file. Line n was line n - a + r
    of the committed file, for a added lines and r removed lines above it.

    Time: O(C log C) to build and O(log C) per line, for C changed lines and places of removals."""

    def __init__(self, added: Iterable[int], removals: Mapping[int, int]) -> None:
        self._added = sorted(added)
        self._places = sorted(removals)
        self._removed = list(accumulate(removals[place] for place in self._places))  # removed up to each place

    def committed_line(self, number: int) -> int:
        above = bisect.bisect_left(self._places, number)  # the places of removals above the line
        removed = self._removed[above - 1] if above else 0
        return number - bisect.bisect_left(self._added, number) + removed


# With the setting core.ignorecase, git compares names with only the letters A to Z folded to lower
# case. lower() joins names that git and NTFS keep apart, such as the Kelvin sign and K, and
# casefold() also joins straße and strasse.
ASCII_LOWER = str.maketrans(string.ascii_uppercase, string.ascii_lowercase)


class Repo:
    def __init__(self, cwd: Path, base: str) -> None:
        if base.startswith("-"):
            raise CheckError(f"--base {base} is not a commit name")
        # The folder of the repository, whether the work folder is inside it, and the prefix: the
        # path of the work folder in the repository, with a "/" at the end. The prefix is empty at
        # the top, and also in a work folder outside the repository, which the GIT_WORK_TREE
        # variable allows.
        root, inside, prefix = git(["rev-parse", "--show-toplevel", "--is-inside-work-tree", "--show-prefix"],
                                   cwd).split("\n")[:3]
        self.root = Path(root)
        # The folders that relative() reads a named path from, each with its prefix.
        self.folders = ([(cwd, prefix)] if inside == "true" else []) + [(self.root, "")]
        self.head = git(["rev-parse", "HEAD"], self.root).strip()
        self.base = git(["merge-base", base, "HEAD"], self.root).strip()

    def changed_files(self) -> list[str]:
        """Files that differ from the base or from HEAD, and untracked files. A deleted file is
        not listed; check_files() handles a deleted changelog."""
        names: list[str] = []
        for commit in dict.fromkeys((self.base, self.head)):
            names += git(["diff", "--name-only", "-z", "--no-renames", "--diff-filter=d", commit],
                         self.root).split("\0")
        untracked = git(["ls-files", "--others", "--exclude-standard", "-z"], self.root)
        return sorted({name for name in names + untracked.split("\0") if name})

    def partly_staged(self) -> set[str]:
        """Files with staged changes and other changes in the working file, as after git add and a
        later edit. The commit takes the staged text, and the script reads the working file."""
        staged = git(["diff", "--cached", "--name-only", "-z", "--no-renames"], self.root).split("\0")
        unstaged = git(["diff", "--name-only", "-z", "--no-renames"], self.root).split("\0")
        return {name for name in staged if name} & set(unstaged)

    def indexed(self, path: str) -> bool:
        """True when the index holds a file of exactly this name. git compares the name
        case-sensitively, also with the setting core.ignorecase, so a file renamed only in case
        does not count."""
        return bool(git(["ls-files", "-z", "--cached", "--", path], self.root))

    def committed_text(self, commit: str, path: str) -> str | None:
        """The text of the file at the commit without a byte order mark, or None when the commit
        lacks it. A failed git command and a file over MAX_FILE_BYTES raise CheckError, so a failure
        is not a missing file."""
        entry = git(["ls-tree", "-l", "-z", commit, "--", path], self.root).split()
        if len(entry) < 4 or entry[1] != "blob":  # the mode, the type, the object name, and the size
            return None
        # The size of an object that git cannot read is "BAD", and git show then fails.
        if entry[3].isdecimal() and int(entry[3]) > MAX_FILE_BYTES:
            raise CheckError(f"{path} has {entry[3]} bytes at {commit[:SHORT_HASH_LENGTH]}, over the limit of "
                             f"{MAX_FILE_BYTES} bytes that the script reads")
        return git(["show", f"{commit}:{path}"], self.root).removeprefix(BYTE_ORDER_MARK)

    def changed_lines(self, commit: str, path: str) -> LineChanges | None:
        """The changes of the working file against the commit, which holds the file, or None for
        all lines. git compares the commit with the working tree, but only for the files
        that the index lists. When the index lacks the file, as after "git rm --cached", git reports
        the file as deleted. Every line then counts as changed, as in any other untracked file."""
        # The options keep attributes and the git configuration from changing the hunks: no text
        # conversion, and no binary detection, which hides every change of a file with a NUL byte.
        diff = git(["diff", "-U0", "--text", "--no-textconv", "--no-color", "--no-ext-diff", "--no-renames",
                    commit, "--", path], self.root)
        return None if DELETED_FILE.search(diff) else line_changes(diff)

    def filter_drivers(self, paths: Sequence[str]) -> dict[str, str]:
        """{path: driver} for each of the paths whose filter driver git runs before it compares the
        file: the attributes name the driver, and the git configuration gives it a clean or process
        command. One git call reads the attributes of all paths."""
        if not paths:
            return {}
        fields = git(["check-attr", "-z", "--all", "--stdin"], self.root, "".join(f"{path}\0" for path in paths))
        values = fields.split("\0")  # path, attribute, and value for each attribute of each path
        # --all lists only the attributes that a path has, so it lists no filter attribute for a path
        # that has none. check-attr prints "set" and "unset" for a bare and a removed attribute, which
        # run no filter, and also for a driver of that name, which git runs. The script cannot tell
        # them apart, so such a value counts as a driver name.
        named = {path: value for path, attribute, value in zip(values[0::3], values[1::3], values[2::3])
                 if attribute == "filter"}
        configured = {driver for driver in set(named.values()) if self.has_filter_command(driver)}
        return {path: driver for path, driver in named.items() if driver in configured}

    def has_filter_command(self, driver: str) -> bool:
        return any(git(["config", "--default=", "--get", f"filter.{driver}.{key}"], self.root).strip()
                   for key in ("clean", "process"))

    def relative(self, path: Path) -> str:
        """The name of a file in the repository, with "/" between the folders. The path relative to
        the work folder gets the prefix of that folder, which git gives, so a link that leads to the
        work folder does not change the name. When that name is outside the repository, the path is
        compared with the folder of the repository."""
        cause: ValueError | None = None
        for folder, prefix in self.folders:
            try:
                # os.path, not pathlib: abspath removes "..", and Path.resolve() would follow links.
                rel = os.path.relpath(os.path.abspath(path), folder).replace(os.sep, "/")
            except ValueError as problem:  # on Windows, another drive, or the same drive spelled \\?\C:
                cause = problem
                continue
            rel = posixpath.normpath(f"{prefix}{rel}")
            if rel != ".." and not rel.startswith("../"):
                return rel
        raise CheckError(f"{path} is outside the repository {self.root}") from cause

    def git_names(self, paths: Sequence[str]) -> tuple[list[str], list[str]]:
        """(names, errors): the names that git gives the files of the paths, which relative()
        gives, and an error for each path whose name is not clear. On a file system that ignores
        case, a path can name a file of the index or an untracked file in another case. git
        compares names case-sensitively, so under that name the script reads no committed version
        and no staged change of the file. Such a path gets the name that git lists for the same
        file. A path that leads to no file, as for a deleted file, gets a name that git lists and
        that differs from it only in the case of the letters A to Z, when the file system ignores
        case, as the git setting core.ignorecase gives it. A path that can get two or more names,
        which the index can list for one file, gets an error instead, because the script cannot
        tell which committed and staged text it names. Any other path stays as it is.

        Time: O(K + P) on average for K names that git lists and P paths."""
        listed = git(["ls-files", "-z", "--cached", "--others", "--exclude-standard"], self.root).split("\0")
        known = set(listed)
        by_folded_case: defaultdict[str, list[str]] = defaultdict(list)
        for name in listed:
            by_folded_case[name.casefold()].append(name)
        names: list[str] = []
        errors: list[str] = []
        for path in paths:
            # The index can list one file under two names, so a name that git lists stays.
            candidates = [] if path in known else by_folded_case.get(path.casefold(), [])
            same = [name for name in candidates if same_file(self.root / name, self.root / path)]
            if candidates and not same and not os.path.lexists(self.root / path) and self.ignores_case():
                # Without a file to compare, only a name that git takes for the same name counts.
                folded = path.translate(ASCII_LOWER)
                same = [name for name in candidates if name.translate(ASCII_LOWER) == folded]
            if len(same) > 1:
                errors.append(f"{path} can name {' or '.join(same)} of the index, so the script cannot tell its "
                              "committed text; name one of them")
                continue
            names.append(same[0] if same else path)
        return names, errors

    def ignores_case(self) -> bool:
        """True when the git setting core.ignorecase says that the file system of the repository
        ignores the case of names. git init sets it after a test of the file system."""
        return git(["config", "--bool", "--default=false", "--get", "core.ignorecase"], self.root).strip() == "true"


def same_file(first: Path, second: Path) -> bool:
    try:
        return os.path.samefile(first, second)
    except OSError:  # a missing file, or a folder that denies access
        return False
