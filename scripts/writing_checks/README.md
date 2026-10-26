# Writing checks

The check_writing.py script reports changed text that breaks one of the mechanical rules of `WRITING_RULES.md`. Its
module docstring describes the checks and the errors in detail. The docstring of `kotlin_assertions.py` states which
calls the Kotlin check reads and when it checks a call.

## Usage

The script needs Python 3.12 or newer and git. It checks changelog entries, line widths, section signs, and the
assertion messages of Kotlin tests. Run it in the repository or in one of its worktrees:

```
python scripts/check_writing.py                  # uncommitted changes and untracked files
python scripts/check_writing.py --base main      # all changes since the merge base with main
python scripts/check_writing.py FILE ...         # changed lines of these files only
python scripts/check_writing.py --all FILE ...   # every line of these files
```

In a worktree whose branch has no copy of the script, run the copy of the main checkout.

## Report

Each finding is a line `path:line: [rule] message`. Items that the script cannot read follow under "Check by hand";
they are not findings. Then the cited rules follow with their text from `WRITING_RULES.md`, and a last line gives the
counts. Errors go to stderr. The exit status is 0 for no findings, 1 for findings, and 2 for an error.

## Files

The script and its tests are in the `scripts` folder. The other files are in this package.

| File | Contents |
|---|---|
| `__init__.py` | The minimum Python version of the package |
| `findings.py` | The `Finding` and `CheckError` types |
| `rules.py` | The checked rules and their limits, read from `WRITING_RULES.md` |
| `git_repo.py` | The git commands, and the changed lines of a file |
| `text.py` | Helpers for lines, sentences, quotes, and code blocks, and the checks of section signs and line widths |
| `changelog.py` | The checks of `changelog.txt` |
| `kotlin_assertions.py` | The check of the JUnit 4 assertion messages of Kotlin test files |
| `check_writing.py` | The command line, the file handling, and the report |
| `test_check_writing.py` | The tests of the script and of each module |

## Imports

Each file imports only the files above it in the table. Keep this order for a new import: the package never imports
the script, and two modules that import names from each other can fail to load.

Only `git_repo.py` runs git, and only `check_writing.py` reads files. The other modules check the texts that they get,
so a test can give them texts without a repository.

## Changes to the checks

- A check cites its rule in `CHECKED_RULES` of `rules.py`, with the phrases that the check needs in the rule text.
- The modules import names, not modules. A test that replaces a function therefore patches the module that calls it,
  for example `mock.patch.object(writing_checks.kotlin_assertions, "kotlin_mask", counting_mask)`.
- The script finds the package in its own folder, so the package stays next to `check_writing.py`.

## Verification

Run the tests on Python 3.12 and on the newest Python version in use. On the Windows 10 development machine, each run
takes about five minutes.

```
python -m unittest scripts/test_check_writing.py
```

The mypy command is at the top of `mypy.ini`.
