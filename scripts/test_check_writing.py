#!/usr/bin/env python3
"""Tests of check_writing.py with known good and bad samples. They run on Python 3.12 and newer.

Usage:
    python -m unittest scripts/test_check_writing.py

The rule tests read the WRITING_RULES.md of this repository, so a change of a checked rule
fails them until the script follows it. The git tests build a temporary repository.
"""
import functools
import io
import os
import stat
import subprocess
import sys
import tempfile
import time
import unittest
from collections.abc import Mapping, Sequence
from collections.abc import Set as AbstractSet
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).absolute().parent))
import check_writing as cw
import writing_checks.changelog
import writing_checks.git_repo
import writing_checks.kotlin_assertions
from writing_checks.changelog import (
    Entry,
    check_changelog,
    committed_entry_changes,
    match_entries,
    parse_changelog,
    parse_version,
)
from writing_checks.findings import CheckError, Finding
from writing_checks.git_repo import BYTE_ORDER_MARK, GIT_TIMEOUT_SECONDS, Repo
from writing_checks.kotlin_assertions import (
    BY_HAND,
    KotlinText,
    check_assertions,
    imported_names,
    kotlin_mask,
    unread_reason,
)
from writing_checks.rules import CHECKED_RULES, Rules, parse_rules
from writing_checks.text import blank_quotes, context_changes, split_lines, split_sentences, words

ROOT = Path(__file__).absolute().parent.parent
TODAY = "2026-10-20"
SIGN = chr(0xA7)
TIME_LIMIT_SECONDS = 15  # a check in linear time takes well under this; a quadratic one, minutes
PROCESS_TIMEOUT_SECONDS = 60  # for the git and Python processes of the tests


@functools.cache
def rules_text() -> str:
    """The text of the WRITING_RULES.md of this repository, read on first use. The cache holds
    only this text, and the tests do not change the file."""
    return (ROOT / cw.RULES_FILE).read_text(encoding="utf-8")


def repository_rules() -> Rules:
    """New rules from the WRITING_RULES.md of this repository, so a test that changes its rules
    does not change the rules of another test."""
    return Rules(rules_text())


# The entry of 2026-10-20 from changelog.txt, written under the current rules.
GOOD_ENTRY = split_lines("""\
Docs: rules for changelog dates, tool versions, claims, and wording   (2026-10-20)
----------------------------------------------------------------------------------
The governance rules and the writing rules of the repository now give a changelog entry the date of its commit. New
rules cover tool versions, claims, findings, and wording.
- Both date rules require an updated date when the commit is made on a later day than the entry.
- The version rules in GOVERNANCE.md require upstream research and local checks before a pinned version changes.
- The changelog entry of such a change must state why the version was chosen and what the checks found. Missing
  information requires a report of the checks.
- The enforcement section of GOVERNANCE.md moves to section 7 and dates the new section from 2026-10-20.
- Claims in any text must be supported by suitable evidence and stay within its scope. A test run also needs
  permission from the test plan or the lead.
""")


def entry(intro: str, *bullets: str, title: str = "Docs: sample entry", date: str = TODAY,
          underline: str | None = None) -> list[str]:
    """Lines of one changelog entry. A bullet can hold continuation lines after a newline."""
    head = f"{title}   ({date})"
    lines = [head, "-" * len(head) if underline is None else underline]
    lines += intro.split("\n")
    for bullet in bullets:
        lines += f"- {bullet}".split("\n")
    return [*lines, "", ""]


def changelog_findings(lines: Sequence[str], committed: Sequence[str] | None = (),
                       head: Sequence[str] | None = None, rules: Rules | None = None) -> list[Finding]:
    """Findings for a working changelog. committed is the version at the base: an empty changelog
    when not given, and None for a changelog without a committed version, which needs a header
    paragraph. head is the version at HEAD, which is the base version when not given."""
    base = None if committed is None else parse_version(committed)
    head_version = base if head is None else parse_version(head)
    checked_rules = repository_rules() if rules is None else rules
    return check_changelog("changelog.txt", parse_version(lines), base, head_version, checked_rules, TODAY)


def changelog_rules(lines: Sequence[str], committed: Sequence[str] | None = (),
                    head: Sequence[str] | None = None, rules: Rules | None = None) -> list[str]:
    return sorted(f.rule for f in changelog_findings(lines, committed, head, rules))


def sentence(count: int) -> str:
    return " ".join([*["word"] * (count - 1), "end."])


def numbered_lines(count: int) -> str:
    return "".join(f"Line {number}.\n" for number in range(1, count + 1))


class RulesTest(unittest.TestCase):
    def test_every_checked_rule_has_its_expected_wording(self) -> None:
        rules = repository_rules()
        self.assertEqual([], rules.errors, "the repository rules match every check")
        self.assertEqual(set(CHECKED_RULES), rules.usable, "every check is usable")

    def test_each_caller_gets_its_own_rules(self) -> None:
        repository_rules().usable.clear()
        self.assertEqual(set(CHECKED_RULES), repository_rules().usable,
                         "a change of the rules of one caller does not reach the next caller")

    def test_importing_the_tests_reads_no_file(self) -> None:
        code = ("import builtins, io, sys\n"
                "def refuse(*args, **kwargs): raise AssertionError('a file is opened at import')\n"
                "builtins.open = io.open = refuse\n"
                f"sys.path.insert(0, {str(ROOT / 'scripts')!r})\n"
                "import test_check_writing\n")
        done = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True, encoding="utf-8",
                              check=False, timeout=PROCESS_TIMEOUT_SECONDS)
        self.assertEqual(0, done.returncode, f"the import of the tests reads no file: {done.stderr}")

    def test_limits_come_from_the_rule_text(self) -> None:
        rules = repository_rules()
        self.assertEqual(20, rules.max_words, "rule 5.3.5 gives 20 words")
        self.assertEqual(3, rules.opener_words, "rule 5.4.1 gives three words")

    def test_title_form_comes_from_rule_5_1_1(self) -> None:
        title = "M7/WP2 (F2 hardening): P3 decision proposal after P2   (2026-10-19)"
        title_form = repository_rules().title
        assert title_form is not None
        self.assertIsNotNone(title_form.fullmatch(title), "three spaces before the date match")
        self.assertIsNone(title_form.fullmatch(title.replace("   (", "  (")),
                          "two spaces before the date do not match")

    def test_continuation_lines_join_the_rule_text(self) -> None:
        self.assertIn("not with a bare file name", repository_rules().text["5.3.4"],
                      "the text of rule 5.3.4 continues across its line break")

    def test_a_table_inside_a_rule_does_not_end_it(self) -> None:
        rules = parse_rules("## 3. Tests\n\n4. First part.\n\n   | a | b |\n   |---|---|\n"
                               "   more text.\n")
        self.assertEqual("First part. more text.", rules["3.4"], "text after the table stays")

    def test_a_changed_limit_changes_the_check(self) -> None:
        rules = Rules(rules_text().replace("to 20 words or fewer", "to 25 words or fewer"))
        self.assertEqual(25, rules.max_words, "the limit follows the rule text")
        self.assertEqual([], changelog_rules(entry("Intro.", sentence(25)), rules=rules),
                         "25 words pass a limit of 25")

    def test_an_unknown_or_repeated_title_placeholder_skips_the_title_check(self) -> None:
        for form in ("`<area>: <scope>   (<date>)`", "`<area>: <what> <date>   (<date>)`"):
            rules = Rules(rules_text().replace("`<area>: <what>   (<date>)`", form))
            self.assertTrue(any("5.1.1" in error for error in rules.errors), f"{form}: the error names 5.1.1")
            self.assertEqual([], changelog_rules(entry("Intro.", "One bullet."), rules=rules),
                             f"{form}: the title check is skipped")

    def test_a_rules_file_that_is_not_utf_8_is_an_error(self) -> None:
        folder = Path(self.enterContext(tempfile.TemporaryDirectory()))
        rules_path = folder / "WRITING_RULES.md"
        rules_path.write_bytes(b"# Rules\n\xff\n")
        stderr_buffer = io.StringIO()
        code = cw.main(["--rules", str(rules_path)], cwd=ROOT, stdout=io.StringIO(), stderr=stderr_buffer)
        self.assertEqual((2, True), (code, "is not valid UTF-8" in stderr_buffer.getvalue()),
                         "exit status 2 and an error line, not a traceback")

    def test_the_introduction_limit_comes_from_rule_5_1_2(self) -> None:
        self.assertEqual(2, repository_rules().intro_sentences, "rule 5.1.2 gives one or two sentences")
        rules = Rules(rules_text().replace("introduction of one or two plain", "introduction of one or three plain"))
        self.assertEqual([], changelog_rules(entry("One. Two. Three.", "One bullet."), rules=rules),
                         "three sentences pass a limit of three")

    def test_a_relative_rules_path_is_read_from_the_working_directory(self) -> None:
        folder = Path(self.enterContext(tempfile.TemporaryDirectory()))
        (folder / "WRITING_RULES.md").write_bytes(b"# Rules\n\xff\n")
        stderr_buffer = io.StringIO()
        code = cw.main(["--rules", "WRITING_RULES.md"], cwd=folder, stdout=io.StringIO(), stderr=stderr_buffer)
        self.assertEqual((2, True), (code, "is not valid UTF-8" in stderr_buffer.getvalue()),
                         "the rules file of the working directory is read, as named files are")

    def test_a_missing_rule_skips_its_check_with_an_error(self) -> None:
        text = rules_text().replace("1. No two bullets of an entry start with the same three words.",
                                    "1. Bullets vary.")
        rules = Rules(text)
        self.assertTrue(any("5.4.1" in error for error in rules.errors), "the error names 5.4.1")
        lines = entry("Intro.", "The proposal recommends one.", "The proposal recommends two.")
        self.assertEqual([], changelog_rules(lines, rules=rules), "the opener check is skipped")


class SentenceTest(unittest.TestCase):
    def test_sentences_split_before_a_capital_or_a_digit(self) -> None:
        parts = [s for _, s in split_sentences("One two. Three four. 5 tests passed.")]
        self.assertEqual(["One two.", "Three four.", "5 tests passed."], parts,
                         "three sentences")

    def test_a_file_name_does_not_split_a_sentence(self) -> None:
        parts = split_sentences("The reports/README.md table now links it.")
        self.assertEqual(1, len(parts), "one sentence")

    def test_a_sentence_can_start_with_a_lower_case_name(self) -> None:
        text = "The checks run. check_writing.py reads the logs of the devs. tests pass. Ångström units stay."
        parts = [s for _, s in split_sentences(text)]
        self.assertEqual(["The checks run.", "check_writing.py reads the logs of the devs.", "tests pass.",
                          "Ångström units stay."], parts,
                         "four sentences, also after a word that ends like an abbreviation and before a letter of "
                         "another alphabet")

    def test_an_ellipsis_or_an_abbreviation_does_not_end_a_sentence(self) -> None:
        text = ("Run the tools, e.g. ruff, i.e. one linter, incl. mypy, etc. and more, vs. none ... then stop. "
                "E.g. this one.")
        parts = [s for _, s in split_sentences(text)]
        self.assertEqual([text.partition(" E.g.")[0], "E.g. this one."], parts,
                         "only the period after stop ends a sentence, and the case of an abbreviation does not matter")

    def test_etc_ends_a_sentence_only_before_a_capital(self) -> None:
        cases = {
            "Logs, tests, etc. The result stays. Logs etc. stay in place.":
                ["Logs, tests, etc.", "The result stays.", "Logs etc. stay in place."],
            "Logs, tests, Etc. and more stay.": ["Logs, tests, Etc. and more stay."],
            'Logs, tests, etc. "The result" stays.': ["Logs, tests, etc.", '"The result" stays.'],
            "Logs, tests, etc. 'The result' stays.": ["Logs, tests, etc.", "'The result' stays."],
            "Logs, tests, etc. `Make` runs.": ["Logs, tests, etc.", "`Make` runs."],
            "Logs, tests, etc. (The result) stays.": ["Logs, tests, etc.", "(The result) stays."],
            "Logs, tests, etc. **The result** stays.": ["Logs, tests, etc.", "**The result** stays."],
        }
        for text, expected in cases.items():
            self.assertEqual(expected, [s for _, s in split_sentences(text)],
                             f"{text}: etc. in any case ends a sentence only before a capital, which can follow an "
                             "opening quote or parenthesis")

    def test_marks_around_a_sentence_keep_its_end(self) -> None:
        cases = {
            "One. [FR-1] Two. Three.": ["One.", "[FR-1] Two.", "Three."],
            'One. ("Two.") Three.': ["One.", '("Two.")', "Three."],
            "One. **Two** three.": ["One.", "**Two** three."],
            "Run `make.` Then stop.": ["Run `make.`", "Then stop."],
            "One **two.** Three. Four.": ["One **two.**", "Three.", "Four."],
            "One *two?* Three.": ["One *two?*", "Three."],
            "No Graph.* lookups stay. They pass.": ["No Graph.* lookups stay.", "They pass."],
            "The Graph.* API stays. It passes.": ["The Graph.* API stays.", "It passes."],
            "**A** and Graph.* API stay. Next.": ["**A** and Graph.* API stay.", "Next."],
            "**A**, Graph.* API stay. Next.": ["**A**, Graph.* API stay.", "Next."],
            "Multiply a * b in Graph.* API calls. Next.": ["Multiply a * b in Graph.* API calls.", "Next."],
            "The Graph.* and *item.* Tags stay.": ["The Graph.* and *item.*", "Tags stay."],
            "*A* and **b.** Next.": ["*A* and **b.**", "Next."],
            "One **two.** The Graph.* API stays.": ["One **two.**", "The Graph.* API stays."],
            "One **two. The Graph.* API stays.": ["One **two.", "The Graph.* API stays."],
            "The `Assert.*` imports stay.": ["The `Assert.*` imports stay."],
            "Run `make.` in the shell. It stops.": ["Run `make.` in the shell.", "It stops."],
            'The "already protected?" probe waits. It stops.': ['The "already protected?" probe waits.', "It stops."],
            "Use *item.* in the report. It stays.": ["Use *item.* in the report.", "It stays."],
        }
        for text, expected in cases.items():
            self.assertEqual(expected, [s for _, s in split_sentences(text)],
                             f'{text}: a sentence ends after its closing marks only before a capital, and a "*" '
                             "ends it only when it closes open emphasis")

    def test_a_quote_or_a_code_span_stays_inside_its_sentence(self) -> None:
        cases = {
            "Run `make. Then` stop. Next.": ["Run `make. Then` stop.", "Next."],
            'Run `git commit -m "Fix. Done"` now. Next.': ['Run `git commit -m "Fix. Done"` now.', "Next."],
            'Run `echo "Fix." Next` now. Done.': ['Run `echo "Fix." Next` now.', "Done."],
            'Run `echo "Fix."` Next. Done.': ['Run `echo "Fix."`', "Next.", "Done."],
            'The "no Graph. refs outside" rule stays. Next.': ['The "no Graph. refs outside" rule stays.', "Next."],
            "The `*item` Graph.* API stays. It passes.": ["The `*item` Graph.* API stays.", "It passes."],
            'The "*item" Graph.* API stays. It passes.': ['The "*item" Graph.* API stays.', "It passes."],
            "Run `make.*` Then stop.": ["Run `make.*`", "Then stop."],
        }
        for text, expected in cases.items():
            self.assertEqual(expected, [s for _, s in split_sentences(text)],
                             f"{text}: an end inside the span ends a sentence only when the closing marks after it "
                             'close the span, and a "*" inside the span is no emphasis')

    def test_labels_count_as_words_and_dashes_do_not(self) -> None:
        self.assertEqual(["(C1)", "starts", "-x"], words("(C1) starts - -x"),
                         "the label and -x count, the lone dash does not")


class ChangelogTest(unittest.TestCase):
    def test_the_good_entry_has_no_findings(self) -> None:
        self.assertEqual([], changelog_rules(GOOD_ENTRY), "no findings")

    def test_a_title_without_three_spaces_before_the_date_is_reported(self) -> None:
        lines = entry("Intro.", "One bullet.")
        lines[0] = lines[0].replace("   (", "  (")
        lines[1] = "-" * len(lines[0])
        self.assertEqual(["5.1.1"], changelog_rules(lines), "the title form is reported")

    def test_an_underline_of_another_length_is_reported(self) -> None:
        lines = entry("Intro.", "One bullet.", underline="-" * 10)
        self.assertEqual(["5.1.1"], changelog_rules(lines), "the underline is reported")

    def test_trailing_spaces_do_not_count_for_the_underline_length(self) -> None:
        lines = entry("Intro.", "One bullet.")
        lines[1] = f"{lines[1][:-1]} "
        self.assertEqual(["5.1.1"], changelog_rules(lines), "an underline one dash short is reported")
        lines = entry("Intro.", "One bullet.")
        lines[0] += " "
        self.assertEqual(["5.1.1"], changelog_rules(lines),
                         "a title with a trailing space gets the title form finding only")

    def test_a_nested_bullet_is_reported(self) -> None:
        for nested in ("  - A nested bullet.", "  1. A nested step.", "  2) A nested step.", "  1.\tA nested step."):
            lines = entry("Intro.", f"One bullet.\n{nested}")
            self.assertEqual(["5.1.2"], changelog_rules(lines), f"{nested.strip()}: the nested item is reported")

    def test_text_after_the_bullet_list_is_reported(self) -> None:
        lines = entry("Intro.", "One bullet.")
        lines.insert(4, "A closing sentence.")
        self.assertEqual(["5.1.2"], changelog_rules(lines), "the closing text is reported")

    def test_an_introduction_of_three_sentences_is_reported(self) -> None:
        for intro in ("One. Two. Three.", "One. Logs, tests, etc. The result stays.", "One **two.** Three. Four."):
            lines = entry(intro, "One bullet.")
            self.assertEqual(["5.1.2"], changelog_rules(lines), f"{intro}: the long introduction is reported")
        lines = entry('Run `make.` in the shell. The "already protected?" probe waits.', "One bullet.")
        self.assertEqual([], changelog_rules(lines), "a closing mark before a lower-case word ends no sentence")
        for intro in ("The Graph.* API stays. It passes.", "The `*item` Graph.* API stays. It passes."):
            lines = entry(intro, "One bullet.")
            self.assertEqual([], changelog_rules(lines), f"{intro}: a star pattern before a capital ends no sentence")
        lines = entry("Run `make. Then` stop. Next. Done.", "One bullet.")
        self.assertEqual(["5.1.2"], changelog_rules(lines), "three sentences, one of them with a code span")

    def test_an_old_date_is_reported_only_in_an_uncommitted_entry(self) -> None:
        lines = entry("Intro.", "One bullet.", date="2026-10-19")
        self.assertEqual(["5.1.3"], changelog_rules(lines),
                         "a new entry with an old date is reported")
        self.assertEqual([], changelog_rules(lines, committed=lines),
                         "a committed entry keeps its date")
        self.assertEqual([], changelog_rules(lines, committed=[], head=lines),
                         "an entry committed on the branch keeps its date")

    def test_a_change_to_a_committed_entry_is_reported_at_the_changed_line(self) -> None:
        committed = entry("Intro.", "One bullet.", "Second bullet.")
        lines = entry("Intro.", "One bullet.", "Second bullet, edited.")
        found = changelog_findings(lines, committed)
        self.assertEqual([("5.1.4", 5)], [(f.rule, f.line) for f in found],
                         "the edited line of the committed entry is reported")

    def test_an_edited_committed_entry_keeps_its_date(self) -> None:
        committed = entry("Intro.", "One bullet.", "Second bullet.", date="2026-10-08")
        lines = entry("Intro.", "One bullet.", "Second bullet, edited.", date="2026-10-08")
        self.assertEqual(["5.1.4"], changelog_rules(lines, committed),
                         "only the edit is reported, not the date of the committed entry")

    def test_a_deleted_bullet_of_a_committed_entry_is_reported(self) -> None:
        committed = entry("Intro.", "One bullet.", "Second bullet.")
        lines = entry("Intro.", "One bullet.")
        self.assertEqual(["5.1.4"], changelog_rules(lines, committed),
                         "the removed line of the committed entry is reported")

    def test_a_retitled_committed_entry_is_reported_as_removed(self) -> None:
        older = entry("Older intro.", "Older bullet.", date="2026-10-19")
        committed = entry("Intro.", "One bullet.", title="Docs: old title") + older
        lines = entry("Another intro.", "Another bullet.", title="Docs: a longer new title") + older
        found = changelog_findings(lines, committed)
        self.assertEqual([("5.1.4", 1)], [(f.rule, f.line) for f in found],
                         "the retitled entry counts as removed, at the new entry in its place")

    def test_a_removed_committed_entry_is_reported_at_the_next_kept_entry(self) -> None:
        newest = entry("Newest intro.", "Newest bullet.")
        removed = entry("Removed intro.", "Removed bullet.", title="Docs: removed", date="2026-10-19")
        oldest = entry("Oldest intro.", "Oldest bullet.", title="Docs: oldest", date="2026-10-18")
        found = changelog_findings(newest + oldest, newest + removed + oldest)
        self.assertEqual([("5.1.4", len(newest) + 1)], [(f.rule, f.line) for f in found],
                         "the removal is reported at the next entry that is still there")

    def test_a_committed_entry_changed_by_a_branch_commit_is_reported(self) -> None:
        main_version = entry("Intro.", "One bullet.")
        branch_version = entry("Intro.", "One bullet, edited.")
        self.assertEqual(["5.1.4"], changelog_rules(branch_version, main_version,
                                                    head=branch_version),
                         "the entry differs from the base, although it matches HEAD")

    def test_a_new_entry_with_a_repeated_title_is_checked_as_new(self) -> None:
        committed = entry("Intro.", "First bullet.")
        lines = entry("Another intro.", "Another bullet.") + committed
        self.assertEqual([], changelog_rules(lines, committed),
                         "the committed entry keeps its content, so nothing is reported")
        committed = entry("Intro.", "First bullet.", date="2026-10-19")
        lines = entry("Another intro.", "Another bullet.", date="2026-10-19") + committed
        self.assertEqual(["5.1.3"], changelog_rules(lines, committed),
                         "the new entry is new, so its old date is reported")

    def test_a_new_entry_that_repeats_all_lines_of_a_committed_entry_is_checked_as_new(self) -> None:
        committed = entry("Intro.", "First bullet.")
        lines = entry("Intro.", "First bullet.", "Extra bullet.") + committed
        self.assertEqual(["5.4.4"], changelog_rules(lines, committed),
                         "the unchanged copy below pairs first, although both share every line, "
                         "so only the copied bullet of the new entry is reported")

    def test_an_edit_beside_a_repeated_title_is_reported_in_the_edited_entry(self) -> None:
        committed = entry("Intro.", "First bullet.", "Second bullet.")
        edited = entry("Intro.", "First bullet.", "Second bullet, edited.")
        newer = entry("Another intro.", "Another bullet.")
        found = changelog_findings(newer + edited, committed)
        self.assertEqual([("5.1.4", len(newer) + 5)], [(f.rule, f.line) for f in found],
                         "the edit is reported in the edited entry, not in the new one")

    def test_a_malformed_entry_after_a_committed_entry_is_reported_as_added_lines(self) -> None:
        top = entry("Top intro.", "Top bullet.")
        bottom = entry("Bottom intro.", "Bottom bullet.", title="Docs: bottom", date="2026-10-19")
        malformed = ["Docs: no date or underline", "Its intro.", "- Its bullet.", "", ""]
        found = changelog_findings(top + malformed + bottom, top + bottom)
        changes = [(f.line, f.message) for f in found if f.rule == "5.1.4"]
        self.assertEqual(1, len(changes), "one committed-entry finding")
        self.assertEqual(len(top) + 1, changes[0][0], "the finding points at the new title")
        self.assertIn("adds lines after the committed entry", changes[0][1],
                      "the message names the added lines")

    def test_a_new_entry_below_a_committed_entry_is_reported(self) -> None:
        top = entry("Top intro.", "Top bullet.", title="Docs: top")
        bottom = entry("Bottom intro.", "Bottom bullet.", title="Docs: bottom", date="2026-10-19")
        new = entry("New intro.", "New bullet.", title="Docs: new")
        found = changelog_findings(top + new + bottom, top + bottom)
        self.assertEqual([("5.1.1", len(top) + 1)], [(f.rule, f.line) for f in found],
                         "the new entry is reported at its title")
        self.assertIn('below the committed entry "Docs: top', found[0].message,
                      "the message names the committed entry above it")
        self.assertEqual([], changelog_rules(new + top + bottom, top + bottom), "a new entry on top passes")

    def test_a_new_entry_below_an_entry_committed_on_the_branch_is_reported(self) -> None:
        main_entry = entry("Main intro.", "Main bullet.", title="Docs: main", date="2026-10-18")
        branch_entry = entry("Branch intro.", "Branch bullet.", title="Docs: branch", date="2026-10-19")
        new = entry("New intro.", "New bullet.", title="Docs: new")
        found = changelog_findings(branch_entry + new + main_entry, main_entry, head=branch_entry + main_entry)
        self.assertEqual([("5.1.1", len(branch_entry) + 1)], [(f.rule, f.line) for f in found],
                         "the order is checked against HEAD too, not only against the merge base")

    def test_moved_committed_entries_are_reported(self) -> None:
        first, second, third, fourth = (entry(f"Intro {number}.", f"Bullet {number}.", title=f"Docs: entry {number}",
                                              date=f"2026-10-1{9 - number}") for number in range(4))
        found = changelog_findings(second + first, first + second)
        self.assertEqual([("5.1.4", len(second) + 1)], [(f.rule, f.line) for f in found],
                         "of two swapped entries, the one below is reported as moved")
        self.assertIn('moves the committed entry "Docs: entry 0', found[0].message, "the message names the entry")
        found = changelog_findings(second + third + fourth + first, first + second + third + fourth)
        self.assertEqual([("5.1.4", 3 * len(first) + 1)], [(f.rule, f.line) for f in found],
                         "only the top entry moved to the bottom is reported, not the three that kept their order")

    def test_a_retitled_or_redated_committed_entry_gets_only_the_removal_finding(self) -> None:
        top = entry("Top intro.", "Top bullet.", title="Docs: top", date="2026-10-19")
        middle = entry("Middle intro.", "Middle bullet.", title="Docs: middle", date="2026-10-18")
        bottom = entry("Bottom intro.", "Bottom bullet.", title="Docs: bottom", date="2026-10-17")
        for changed_middle in (entry("Middle intro.", "Middle bullet.", title="Docs: new middle", date="2026-10-18"),
                               entry("Middle intro.", "Middle bullet.", title="Docs: middle", date="2026-10-16")):
            found = changelog_findings(top + changed_middle + bottom, top + middle + bottom)
            self.assertEqual([("5.1.4", len(top) + 1)], [(f.rule, f.line) for f in found],
                             f"{changed_middle[0]}: no order or date finding for the committed entry")

    def test_each_changelog_version_is_parsed_once(self) -> None:
        header = ["CHANGELOG", "=========", ""]
        old = entry("Old intro.", "Old bullet.", date="2026-10-19")
        committed = "\n".join([*header, *old])
        text = "\n".join([*header, *entry("New intro.", "New bullet."), *old])
        parsed: list[int] = []
        real_parse = parse_changelog

        def counting_parse(lines: Sequence[str]) -> list[Entry]:
            parsed.append(len(lines))
            return real_parse(lines)

        with mock.patch.object(writing_checks.changelog, "parse_changelog", counting_parse):
            cw.check_file("changelog.txt", text, None, committed, committed, repository_rules(), TODAY)
        self.assertEqual(2, len(parsed), "one parse of the working version and one of the committed version")

    def test_a_bullet_added_to_a_committed_entry_is_reported_as_a_change(self) -> None:
        committed = entry("Intro.", "One bullet.")
        lines = entry("Intro.", "One bullet.", "Added bullet.")
        found = changelog_findings(lines, committed)
        self.assertEqual([("5.1.4", 5)], [(f.rule, f.line) for f in found],
                         "the added bullet is a change of the committed entry")
        self.assertIn("changes the committed entry", found[0].message, "the message says so")

    def test_an_entry_without_an_underline_is_reported(self) -> None:
        older = entry("Older intro.", "Older bullet.", date="2026-10-19")
        newer = entry("Intro.", "One bullet.")
        del newer[1]
        self.assertEqual(["5.1.1"], changelog_rules(newer + older, committed=older),
                         "the missing underline is reported")

    def test_added_text_before_the_first_entry_is_reported(self) -> None:
        header = ["CHANGELOG", "=========", "", ""]
        older = entry("Older intro.", "Older bullet.", date="2026-10-19")
        lines = [*header[:2], "", "A note without a title.", "", *older]
        found = changelog_findings(lines, header + older)
        self.assertEqual([("5.1.1", 4)], [(f.rule, f.line) for f in found],
                         "the added text before the first entry is reported")

    def test_a_changed_header_of_a_changelog_committed_on_the_branch_is_reported(self) -> None:
        committed = ["CHANGELOG", "=========", "Old header.", "", *entry("Intro.", "One bullet.")]
        lines = [*committed[:2], "Edited header.", *committed[3:]]
        found = changelog_findings(lines, committed=None, head=committed)
        self.assertEqual([("5.1.1", 3)], [(f.rule, f.line) for f in found],
                         "the header differs from HEAD, although the base has no changelog")

    def test_a_new_changelog_without_one_header_paragraph_and_an_entry_is_reported(self) -> None:
        header = ["CHANGELOG", "=========", "", ""]
        self.assertEqual([], changelog_rules(header + entry("Intro.", "One bullet."), committed=None),
                         "a header paragraph and an entry pass")
        malformed = ["Docs: no underline or date", "Some text.", "- A bullet.", ""]
        found = changelog_findings(header + malformed, committed=None)
        self.assertEqual([("5.1.1", 5)], [(f.rule, f.line) for f in found],
                         "the text after the header paragraph is reported")
        found = changelog_findings(malformed, committed=None)
        self.assertEqual([("5.1.1", 1)], [(f.rule, f.line) for f in found],
                         "a changelog without an entry is reported")
        for empty in ([], ["   "]):
            self.assertEqual([("5.1.1", 1)], [(f.rule, f.line) for f in changelog_findings(empty, committed=None)],
                             f"{empty}: an empty new changelog is reported")
        found = changelog_findings(entry("Intro.", "One bullet."), committed=None)
        self.assertEqual([("5.1.1", 1)], [(f.rule, f.line) for f in found],
                         "a new changelog without a header paragraph is reported")

    def test_hypothetical_words_are_reported(self) -> None:
        lines = entry("Intro.", "The rule would apply later.", "If the lead agrees, X starts.")
        self.assertEqual(["5.2.1", "5.2.1"], changelog_rules(lines), "would and if are reported")

    def test_words_inside_code_spans_are_not_hypothetical(self) -> None:
        lines = entry("Intro.", "The matching no longer uses `grep -iF`.")
        self.assertEqual([], changelog_rules(lines), "the code span is not read as text")

    def test_quoted_words_are_not_reported(self) -> None:
        lines = entry("Intro.", 'Words such as "would" and "if" are reported too.')
        self.assertEqual([], changelog_rules(lines), "quoted words are not reported")

    def test_a_sentence_that_starts_with_a_file_name_is_reported(self) -> None:
        for bullet in ("test_lib_r007.sh passed.", "build.gradle sets the version.",
                       "LockoutManager.java holds it.", "gradle.properties pins it.", "test.py, when run, passes.",
                       "(test.py) passes.", "The run ended. test_lib_r007.sh passed.", "**README.md** describes it."):
            self.assertEqual(["5.3.4"], changelog_rules(entry("Intro.", bullet)),
                             f"{bullet}: the bare file name is reported")
        self.assertEqual([], changelog_rules(entry("Intro.", "The test_lib_r007.sh script "
                                                   "passed.")), "a named script is correct")

    def test_a_sentence_over_the_word_limit_is_reported(self) -> None:
        self.assertEqual([], changelog_rules(entry("Intro.", sentence(20))), "20 words pass")
        self.assertEqual(["5.3.5"], changelog_rules(entry("Intro.", sentence(21))),
                         "21 words are reported")
        labelled = f"{sentence(20)[:-1]} (C1)."
        self.assertEqual(["5.3.5"], changelog_rules(entry("Intro.", labelled)),
                         "the label (C1) is the 21st word")

    def test_a_long_sentence_across_two_lines_is_reported_at_its_start(self) -> None:
        tokens = sentence(21).split(" ")
        bullet = "\n  ".join([" ".join(tokens[:10]), " ".join(tokens[10:])])
        found = changelog_findings(entry("Intro.", bullet))
        self.assertEqual([("5.3.5", 4)], [(f.rule, f.line) for f in found],
                         "the finding gives the line where the sentence starts")

    def test_bullets_that_open_with_the_same_three_words_are_reported(self) -> None:
        lines = entry("Intro.", "The proposal recommends one.", "The proposal recommends two.")
        self.assertEqual(["5.4.1"], changelog_rules(lines), "the second opener is reported")
        lines = entry("Intro.", "The proposal recommends one.", "The proposal favors two.")
        self.assertEqual([], changelog_rules(lines), "openers that differ in word 3 pass")

    def test_sentences_in_a_row_that_start_with_it_are_reported(self) -> None:
        for bullet in ("It runs first. It stops later.", "**It** runs first. **It** stops later.",
                       "One **step.** It runs first. It stops later."):
            lines = entry("Intro.", bullet)
            self.assertEqual(["5.4.2"], changelog_rules(lines), f"{bullet}: the second It sentence is reported")

    def test_an_introduction_that_starts_with_this_change_is_reported(self) -> None:
        for intro in ("This change adds a rule.", "**This change** adds a rule."):
            lines = entry(intro, "One bullet.")
            self.assertEqual(["5.4.3"], changelog_rules(lines), f"{intro}: the opening is reported")
        for intro in ("This changes the policy.", "This changelog records the rules."):
            self.assertEqual([], changelog_rules(entry(intro, "One bullet.")), f"{intro}: another phrase passes")

    def test_a_bullet_copied_from_the_previous_entry_is_reported(self) -> None:
        newer = entry("Intro.", "The local gate ran 468 unit tests with no failures.")
        older = entry("Older intro.", "The local gate ran 468 unit tests with no failures.",
                      date="2026-10-19")
        self.assertEqual(["5.4.4"], changelog_rules(newer + older, committed=older),
                         "the copied bullet of the newer entry is reported")

    def test_only_new_or_changed_entries_are_checked(self) -> None:
        newer = entry("Intro.", "One bullet.")
        older = entry("This change was old.", "It ran. It stopped.", date="2026-01-01")
        self.assertEqual([], changelog_rules(newer + older, committed=older),
                         "the unchanged committed entry is not checked")

    def test_entry_titles_do_not_count_for_the_width(self) -> None:
        lines = ["CHANGELOG", "=========", *entry("Intro.", "One bullet.", title=f"Docs: {'t' * 130}")]
        found = cw.check_file("changelog.txt", "\n".join(lines), None, None, None, repository_rules(), TODAY)
        self.assertEqual([], found, "the long title and its underline are not width findings")


class LargeChangelogTest(unittest.TestCase):
    """Changelogs of many entries, which a pass over all entries for each entry would take
    minutes to compare."""

    ENTRIES = 20000
    committed: list[Entry]
    working: list[Entry]

    @classmethod
    def setUpClass(cls) -> None:
        def changelog(title: str) -> list[Entry]:
            return parse_changelog([line for number in range(cls.ENTRIES)
                                       for line in entry("Intro.", f"Bullet {number}.", title=f"{title} {number}")])

        cls.committed = changelog("Docs: old title")
        cls.working = changelog("Docs: new title")

    def test_retitled_entries_are_paired_in_linear_time(self) -> None:
        started = time.monotonic()
        pairs = match_entries(self.committed, self.working)
        elapsed = time.monotonic() - started
        self.assertEqual({}, pairs, "no working entry has the text or the title of a committed entry")
        self.assertLess(elapsed, TIME_LIMIT_SECONDS, "the entries are paired in linear time")

    def test_removed_entries_are_placed_in_linear_time(self) -> None:
        started = time.monotonic()
        changes = committed_entry_changes(self.committed, self.working, {}, last_line=1)
        elapsed = time.monotonic() - started
        self.assertEqual(self.ENTRIES, len(changes), "each committed entry is reported as removed")
        self.assertLess(elapsed, TIME_LIMIT_SECONDS, "the removed entries are placed in linear time")


class WidthTest(unittest.TestCase):
    def test_code_width_ignores_the_carriage_return(self) -> None:
        text = f"{'x' * 120}\r\n{'y' * 121}\r\n"
        found = cw.check_file("scripts/a.sh", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([("width", 2)], [(f.rule, f.line) for f in found],
                         "only the 121-character line is reported")

    def test_kotlin_imports_are_not_limited(self) -> None:
        text = f"import {'a.' * 70}B\n"
        found = cw.check_file("app/src/main/A.kt", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([], found, "an import line is not a width finding")

    def test_a_document_keeps_its_committed_wrap_width(self) -> None:
        committed = "\n".join(["w" * 95, "w" * 99, "w" * 97] * 10)
        text = f"{committed}\n{'n' * 99}\n{'n' * 100}\n"
        found = cw.check_file("docs/a.md", text, {31, 32}, committed, None, repository_rules(), TODAY)
        self.assertEqual([32], [f.line for f in found], "only the 100-character line is reported")

    def test_an_unwrapped_document_has_no_width_limit(self) -> None:
        committed = "\n".join(["p" * 300, ""] * 25)
        found = cw.check_file("docs/a.md", f"{committed}\n{'q' * 500}", None, committed, None,
                              repository_rules(), TODAY)
        self.assertEqual([], found, "a document without wrapped lines gets no width finding")

    def test_a_short_unwrapped_document_has_no_width_limit(self) -> None:
        committed = "\n".join(["p" * 300] * 5)
        found = cw.check_file("docs/a.md", f"{committed}\n{'q' * 300}\n", None, committed,
                              None, repository_rules(), TODAY)
        self.assertEqual([], found, "five long paragraph lines mark the document as not wrapped")

    def test_a_short_wrapped_document_gets_the_default_width(self) -> None:
        committed = "\n".join(["w" * 80] * 5)
        found = cw.check_file("docs/a.md", f"{committed}\n{'n' * 121}\n", None, committed,
                              None, repository_rules(), TODAY)
        self.assertEqual([6], [f.line for f in found], "the limit of a short document is 120")

    def test_a_new_document_gets_the_default_width(self) -> None:
        text = f"{'a' * 120}\n{'b' * 121}\n"
        found = cw.check_file("docs/new.md", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([2], [f.line for f in found], "the limit of a new document is 120")

    def test_a_fence_closes_only_with_its_character_and_length(self) -> None:
        for block in (["```", "~~~", "code", "```"], ["````", "```", "code", "````"]):
            text = "\n".join([*block, "p" * 150, ""])
            found = cw.check_file("docs/new.md", text, None, None, None, repository_rules(), TODAY)
            self.assertEqual([5], [f.line for f in found], f"{block[:2]}: the paragraph after the block is checked")

    def test_tables_and_code_blocks_are_not_limited(self) -> None:
        text = f"| {'c' * 200} |\n```\n{'d' * 200}\n```\n"
        found = cw.check_file("docs/new.md", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([], found, "table rows and code lines are not width findings")


class SectionSignTest(unittest.TestCase):
    def check(self, text: str, changed: AbstractSet[int] | None = None) -> list[int]:
        found = cw.check_file("docs/new.md", text, changed, None, None, repository_rules(), TODAY)
        return [f.line for f in found if f.rule == "1.9"]

    def test_a_section_sign_in_text_is_reported(self) -> None:
        self.assertEqual([1], self.check(f"See {SIGN}3 of the plan.\n"), "the sign is reported")

    def test_a_quoted_section_sign_is_not_reported(self) -> None:
        self.assertEqual([], self.check(f'Write "section 3.3", not "{SIGN}3.3".\n'),
                         "a quoted example is not reported")

    def test_a_quote_that_continues_on_the_next_line_hides_its_section_sign(self) -> None:
        text = f'Write "the latency of the\ntest plan", not "test plan {SIGN}12". Use it.\n'
        self.assertEqual([], self.check(text), "the quote is followed across the line break")

    def test_a_section_sign_in_a_code_block_is_not_reported(self) -> None:
        for fence in ("```", "~~~"):
            text = f'Intro.\n{fence}\necho "unclosed\ngrep {SIGN} notes.txt\n{fence}\nSee {SIGN}3 of the plan.\n'
            self.assertEqual([6], self.check(text),
                             f"{fence}: the sign in the block is an example, and its open quote ends with it")

    def test_only_changed_lines_are_reported(self) -> None:
        text = f"Old {SIGN}1 text.\nNew {SIGN}2 text.\n"
        self.assertEqual([2], self.check(text, changed={2}), "only the changed line is reported")

    def test_a_fence_or_a_quote_on_another_line_rechecks_the_lines_that_it_moves(self) -> None:
        wrapped = ["w" * 78] * 30
        block = ["```", f"See {SIGN}3.", "p" * 130, "```"]
        committed = "\n".join(wrapped + block)
        text = "\n".join(wrapped + block[:1] + ["```"] + block[1:])
        found = cw.check_file("docs/a.md", text, {32}, committed, None, repository_rules(), TODAY)
        self.assertEqual([(33, "1.9"), (34, "width")], [(f.line, f.rule) for f in found],
                         "the added fence closes the block, so its two unchanged lines are text")
        committed = "\n".join(wrapped + ["", 'Say "the', f'{SIGN}4 rule" here.'])
        text = "\n".join(wrapped + ["", f'{SIGN}4 rule" here.'])
        found = cw.check_file("docs/a.md", text, set(), committed, None, repository_rules(), TODAY, removals={31: 1})
        self.assertEqual([(32, "1.9")], [(f.line, f.rule) for f in found],
                         "without the line that opens the quote, the section sign is text")

    def test_context_changes_give_unchanged_lines_only(self) -> None:
        self.assertEqual(set(), context_changes(["Text.", "More text."], ["Text."], {2}, {}),
                         "the changed line 2 is not a moved line")
        self.assertEqual({2}, context_changes(["Text.", "More text."], ["Text."], set(), {}),
                         "a line after the end of the committed text, as after an edit during the run, counts")

    def test_a_quote_in_code_ends_with_its_line(self) -> None:
        text = f"val quote = '\"'\n// see {SIGN}3 of the plan\n"
        found = cw.check_file("app/src/main/A.kt", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([2], [f.line for f in found if f.rule == "1.9"],
                         "the quote character of line 1 does not hide line 2")

    def test_quoted_spans_are_blanked_in_place(self) -> None:
        self.assertEqual("say         now", blank_quotes('say "would" now'),
                         "the quoted span becomes spaces of the same length")
        self.assertEqual('a "b', blank_quotes('a "b'), "a quote without a closing quote is text")

    def test_code_spans_follow_markdown(self) -> None:
        backslash = chr(92)
        cases = {f"See ``{SIGN}3`` here.": [], f"See ``a ` {SIGN}3`` here.": [],
                 f"Use {backslash}` and see {SIGN}3, not `x`.": [1], f"Use ` and see {SIGN}3.": [1],
                 f"A `code\nspan {SIGN}3` ends.": []}
        for text, expected in cases.items():
            self.assertEqual(expected, self.check(f"{text}\n"), f"{text!r}: a run of backticks closes only "
                             "at a run of the same length, and an escaped or unclosed backtick is text")

    def test_an_escaped_quote_in_a_code_string_keeps_the_string_open(self) -> None:
        backslash = chr(92)
        text = f'val s = "a {backslash}"{SIGN}3{backslash}" b"\n'
        found = cw.check_file("app/src/main/A.kt", text, None, None, None, repository_rules(), TODAY)
        self.assertEqual([], [f.line for f in found if f.rule == "1.9"], "the section sign is inside the string")


def assertion_findings(code: str, changed: AbstractSet[int] | None = None) -> list[Finding]:
    return check_assertions("app/src/test/A.kt", KotlinText(code), changed, repository_rules())


class AssertionTest(unittest.TestCase):
    def lines(self, code: str, changed: AbstractSet[int] | None = None, rule: str = "3.4") -> list[int]:
        return [f.line for f in assertion_findings(code, changed) if f.rule == rule]

    def by_hand(self, code: str) -> list[int]:
        return self.lines(code, rule=BY_HAND)

    def by_hand_message(self, code: str) -> list[str]:
        return [f.message for f in assertion_findings(code) if f.rule == BY_HAND]

    def test_a_message_of_only_templates_is_reported(self) -> None:
        self.assertEqual([1], self.lines('assertEquals("$script", 1, x)'), "reported")
        self.assertEqual([1], self.lines('assertTrue("${thrown.cause}", ok)'), "reported")
        self.assertEqual([1], self.lines('assertEquals("f$offsetMs", 1, x)'),
                         "a one-letter prefix is no word")

    def test_a_message_that_states_the_expected_result_is_not_reported(self) -> None:
        code = 'assertEquals("$script: the restart keeps the failure count", 1, x)'
        self.assertEqual([], self.lines(code), "not reported")

    def test_a_message_whose_value_is_unknown_is_checked_by_hand(self) -> None:
        for code in ("assertTrue(label, ok)", "assertEquals(case.name, 1, x)",
                     "assertTrue(error.message.orEmpty(), ok)"):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one call to check by hand")

    def test_a_message_variable_is_checked_by_hand_whatever_its_value(self) -> None:
        for value in ('"the failure count is reset to zero"', '"$script death=$deathMs"'):
            code = f"fun t() {{\n    val message = {value}\n    assertEquals(message, 0, actual)\n}}"
            self.assertEqual(([], [3]), (self.lines(code), self.by_hand(code)),
                             f"message = {value}: the script does not read the value")

    def test_a_comment_after_the_literal_is_not_part_of_the_message(self) -> None:
        self.assertEqual([1], self.lines('assertTrue("$case" /* current scenario */, ok)'),
                         "the words of the comment do not count")
        self.assertEqual([], self.lines('assertTrue("the lock holds" /* $case */, ok)'),
                         "the literal itself has words")

    def test_key_names_are_not_words(self) -> None:
        self.assertEqual([1], self.lines('assertEquals("seed=$seed step=$step", a, b)'),
                         "key names with templates are a label")
        self.assertEqual([], self.lines('assertEquals("delay=0 keeps the lock", a, b)'),
                         "words after the key names state a result")

    def test_words_of_any_alphabet_count(self) -> None:
        cyrillic_result = "замок держится"
        self.assertEqual([], self.lines(f'assertTrue("$case: {cyrillic_result}", ok)'),
                         "Cyrillic words state a result")
        self.assertEqual([1], self.lines('assertTrue("$имя", ok)'),
                         "a template with a Cyrillic name is no word")
        self.assertEqual([1], self.lines('assertTrue("ключ=$value", ok)'),
                         "a Cyrillic key name is no word")

    def test_an_expression_is_checked_by_hand_whatever_its_literals(self) -> None:
        for code in ('assertTrue(makeLabel("case", caseId), ok)',
                     'assertTrue("$x: the lock holds " + y, ok)', 'assertTrue("$x: " + detail, ok)'):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one call to check by hand")

    def test_a_long_unread_message_is_shortened(self) -> None:
        expression = f'"the lock holds for " + {"x" * 100}'
        message = assertion_findings(f"assertTrue({expression}, ok)")[0].message
        self.assertIn(f"`{expression[:77]}...`", message,
                      "the report shows the first 77 characters and three dots")

    def test_calls_without_a_message_are_not_reported(self) -> None:
        self.assertEqual([], self.lines("assertTrue(ok)"), "one argument has no message")
        self.assertEqual([], self.lines("assertEquals(expected, actual)"), "two arguments")
        self.assertEqual([], self.lines("assertEquals(1.0, x, 0.001)"), "a delta, no message")

    def test_a_message_in_a_multi_line_call_is_reported_at_its_line(self) -> None:
        code = 'assertEquals(\n    "$case",\n    1,\n    x,\n)'
        self.assertEqual([2], self.lines(code), "the message line is reported")

    def test_an_assertion_inside_a_template_is_read(self) -> None:
        self.assertEqual([1], self.lines('val shown = "${assertTrue("$case", ok)}"'),
                         "the code of the template is code")
        self.assertIn("does not read", self.by_hand_message('assertEquals(name, "${x}", actual)')[0],
                      "a string with a template still rules out the tolerance form")

    def test_escapes_are_read_as_kotlin_reads_them(self) -> None:
        backslash = chr(92)
        self.assertEqual([1], self.lines(f'assertTrue("""{backslash}$case""", ok)'),
                         "in a raw string, the backslash is text and $case a template")
        lock_holds = "".join(f"{backslash}u{ord(letter):04x}" for letter in "lock holds")
        self.assertEqual([], self.lines(f'assertTrue("$case: {lock_holds}", ok)'),
                         "the Unicode escapes spell the words lock holds")

    def test_a_template_with_a_backticked_name_is_no_word(self) -> None:
        self.assertEqual([1], self.lines('assertTrue("$`test case`", ok)'), "the backticked name names a template")
        self.assertEqual([], self.lines('assertTrue("$`test case`: the lock holds", ok)'),
                         "the words after the template count")
        self.assertEqual([1], self.lines('assertTrue("$`a"b`", ok)'),
                         "a quote inside the backticked name does not end the string")

    def test_a_trailing_lambda_after_a_long_comment_counts_as_an_argument(self) -> None:
        code = f'assertThrows("$case", Failure::class.java) /* {"x" * 210} */ {{ run() }}'
        self.assertEqual([1], self.lines(code), "the lambda after the comment counts as an argument")

    def test_a_labelled_or_annotated_trailing_lambda_counts_as_an_argument(self) -> None:
        for before_lambda in ("block@", '@Suppress("unused")', '@Suppress("unused") block@'):
            code = f'assertThrows("$case", Failure::class.java) {before_lambda} {{ run() }}'
            self.assertEqual([1], self.lines(code), f"{before_lambda}: the message is read")
        code = 'assertTrue("$case", ok)\n@Suppress("unused")\nval next = run { 1 }'
        self.assertEqual(([1], []), (self.lines(code), self.by_hand(code)),
                         "an annotation of the next declaration starts no lambda, so the call keeps two arguments")

    def test_other_annotation_and_label_forms_before_a_lambda_are_read(self) -> None:
        for before_lambda in ('@Suppress ("unused")', "`my label`@", "`block`@", '@[Suppress("x") Mark]',
                              "@`My Mark`", '@Suppress ("unused")\n    @Mark\n    block@'):
            code = f'assertThrows("$case", Failure::class.java) {before_lambda} {{ run() }}'
            self.assertEqual([1], self.lines(code), f"{before_lambda}: the message is read")

    def test_an_unread_annotation_or_label_after_a_call_is_checked_by_hand(self) -> None:
        for after in ("@Mark", "`a b`@ x"):
            code = f'assertTrue("$case", ok) {after}'
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{after}: no finding, and one call to check by hand")
        self.assertIn("is followed on its line by an annotation or a label", self.by_hand_message(code)[0],
                      "the item names the cause")

    def test_an_unread_annotation_keeps_the_trailing_lambda_in_the_call(self) -> None:
        code = 'assertThrows("$case", Failure::class.java) @Suppress /* a\nnote */ ("unused") {\n    run()\n}'
        self.assertEqual([1], self.lines(code, changed={3}, rule=BY_HAND),
                         "a change inside the lambda lists the call, as a check of every line does")

    def test_spaced_annotation_arguments_in_type_arguments_are_read(self) -> None:
        code = 'assertThrows<@Mark ("x") Failure>("$case", Failure::class.java) { run() }'
        self.assertEqual(([1], []), (self.lines(code), self.by_hand(code)), "the type arguments parse")

    def test_a_class_alias_of_any_alphabet_is_resolved(self) -> None:
        code = 'import org.junit.Assert as Проверка\nПроверка.assertTrue("$case", ok)'
        self.assertEqual([2], self.lines(code), "the call through the alias is read as JUnit 4")

    def test_a_backticked_alias_of_a_junit_name_puts_the_file_on_the_list_to_check_by_hand(self) -> None:
        for code in ('import org.junit.Assert as `Junit Assert`\n`Junit Assert`.assertTrue("$case", ok)',
                     'import org.junit.Assert.assertTrue as `check it`\n`check it`("$case", ok)'):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one item at the import to check by hand")
        self.assertIn("the file imports org.junit.Assert as `Junit Assert`, a backticked name",
                      self.by_hand_message('import org.junit.Assert as `Junit Assert`')[0], "the item names the alias")
        code = 'import com.applock.testing.Checks as `My Checks`\nassertTrue("$case", ok)'
        self.assertEqual(([2], []), (self.lines(code), self.by_hand(code)),
                         "a backticked alias of another class leaves the file readable")

    def test_imports_with_comments_or_semicolons_are_resolved(self) -> None:
        for imports in ("import /* a\nb */ org.junit.Assert as Checks", "import org.junit.Assert as /* a\nb */ Checks",
                        "import org.junit.Assert /* a\nb */ as Checks",
                        "import org.junit.Assert as Checks; import org.junit.Assume",
                        "import org.junit.Assume; import org.junit.Assert as Checks"):
            code = f'{imports}\nChecks.assertTrue("$case", ok)'
            self.assertEqual([code.count("\n") + 1], self.lines(code), f"{imports}: the call through the alias is read")

    def test_an_import_that_the_script_cannot_read_puts_the_file_on_the_list_to_check_by_hand(self) -> None:
        for imports in ("import org.junit.Assert as Checks Other", "package p import org.junit.Assert as Checks"):
            code = f'{imports}\nChecks.assertTrue("$case", ok)'
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{imports}: no finding, and one item at the import to check by hand")
            self.assertIn("the file holds an import that the script cannot read", self.by_hand_message(code)[0],
                          f"{imports}: the item names the cause")

    def test_a_type_alias_resolves_the_class_of_a_call(self) -> None:
        cases = {
            "an alias of org.junit.Assert": (('typealias Checks = org.junit.Assert\n'
                                              'Checks.assertTrue("$case", ok)'), [2]),
            "an alias of an import alias": (('import org.junit.Assert as JUnitAssert\ntypealias Checks = JUnitAssert\n'
                                             'Checks.assertTrue("$case", ok)'), [3]),
            "an alias with a comment and a line break": (('private typealias Checks /* a */ =\n    org.junit.Assert\n'
                                                          'Checks.assertTrue("$case", ok)'), [3]),
            "a project class under the name Assert": (('typealias Assert = com.applock.testing.Assert\n'
                                                       'Assert.assertTrue("$case", ok)'), []),
            "an alias of a later alias": (('typealias Checks = Later\ntypealias Later = org.junit.Assert\n'
                                           'Checks.assertTrue("$case", ok)'), [3]),
            "a chain of three aliases in mixed order": (('typealias A = B\ntypealias C = org.junit.Assert\n'
                                                         'typealias B = C\nA.assertTrue("$case", ok)'), [4]),
        }
        for case, (code, expected) in cases.items():
            self.assertEqual((expected, []), (self.lines(code), self.by_hand(code)),
                             f"{case}: {'read as JUnit 4' if expected else 'not read'}")

    def test_a_call_through_a_type_alias_that_the_script_cannot_read_is_checked_by_hand(self) -> None:
        for aliases in ("typealias Checks<T> = org.junit.Assert", "typealias Checks = (org.junit.Assert)",
                        "typealias Checks = @Mark org.junit.Assert",
                        "typealias Checks = Later\ntypealias Later<T> = org.junit.Assert",
                        "typealias Checks = Other\ntypealias Other = Checks"):  # a cycle, which Kotlin rejects
            code = f'{aliases}\nChecks.assertTrue("$case", ok)'
            self.assertEqual(([], [code.count("\n") + 1]), (self.lines(code), self.by_hand(code)),
                             f"{aliases}: no finding, and one item at the call to check by hand")
        code = 'typealias checks<T> = org.junit.Assert\nchecks.assertTrue("$case", ok)'
        self.assertEqual(([], [2]), (self.lines(code), self.by_hand(code)), "a lower-case alias is checked by hand too")
        self.assertIn("assertTrue is called through checks, a class or type alias that the script cannot resolve",
                      self.by_hand_message(code)[0], "the item names the qualifier")

    def test_a_call_through_a_class_that_the_file_does_not_resolve_is_checked_by_hand(self) -> None:
        for code in ('Checks.assertTrue("$case", ok)', 'com.applock.testing.Checks.assertTrue("$case", ok)'):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one item at the call to check by hand")
        for code in ('harness.assertTrue("$case", ok)', 'harness.state.assertTrue("$case", ok)'):
            self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                             f"{code}: a call on a variable is not read")
        self.assertEqual([], self.lines('Checks.assertTrue("$case", ok)\nval x = 1', {2}, rule=BY_HAND),
                         "an unchanged call is not listed")

    def test_a_type_alias_changed_to_a_form_that_the_script_cannot_read_rechecks_every_call(self) -> None:
        committed = 'typealias Checks = com.applock.testing.Checks\nfun t() {\n    Checks.assertTrue("$case", ok)\n}\n'
        text = committed.replace("typealias Checks =", "typealias Checks<T> =")
        found = cw.check_file("app/src/test/A.kt", text, {1}, committed, committed, repository_rules(), TODAY)
        self.assertEqual([(3, BY_HAND)], [(f.line, f.rule) for f in found],
                         "the unchanged call through the changed alias is listed to check by hand")

    def test_a_removed_declaration_rechecks_the_calls_of_its_name(self) -> None:
        committed = ('class T {\n    private val label = "$case"\n    fun t() {\n'
                     '        val label = "the restart keeps the count"\n        assertTrue(label, ok)\n    }\n}\n')
        text = committed.replace('        val label = "the restart keeps the count"\n', "")
        found = cw.check_file("app/src/test/A.kt", text, set(), committed, committed, repository_rules(), TODAY,
                              removals={3: 1})
        self.assertEqual([(4, BY_HAND)], [(f.line, f.rule) for f in found],
                         "the call whose message the removed declaration named is listed")

    def test_a_changed_declaration_lists_a_call_to_check_by_hand(self) -> None:
        cases = {
            "a call through an unresolved class": "Checks.assertTrue(message, ok)",
            "a call that fits no JUnit 4 form": "assertTrue(message, ok, 3)",
            "the name in another argument": "Checks.assertTrue(ok, message)",
        }
        for case, call in cases.items():
            code = f'val message = "$case"\nfun t() {{\n    {call}\n}}'
            self.assertEqual([3], self.lines(code, changed={1}, rule=BY_HAND),
                             f"{case}: the changed declaration lists the unchanged call")
            found = check_assertions("app/src/test/A.kt", KotlinText(code), set(), repository_rules(),
                                        removed={"message"})
            self.assertEqual([3], [f.line for f in found], f"{case}: a removed declaration lists it too")

    def test_a_change_inside_a_declaration_of_several_lines_rechecks_its_calls(self) -> None:
        code = 'val message =\n    "the count is kept"\nassertTrue(message, ok)'
        self.assertEqual([3], self.lines(code, changed={2}, rule=BY_HAND), "a changed value on the next line")
        code = 'val message =\n    "the count " +\n    "is kept"\nassertTrue(message, ok)'
        found = check_assertions("app/src/test/A.kt", KotlinText(code), set(), repository_rules(), removals={2})
        self.assertEqual([4], [f.line for f in found], "lines removed between two lines of the declaration")
        code = ('val message = buildString {\n    val part =\n        "the count is kept"\n    append(part)\n}\n'
                'assertTrue(message, ok)')
        self.assertEqual([6], self.lines(code, changed={3}, rule=BY_HAND),
                         "a change in a declaration inside the value of message")
        code = 'val message = "$case"\nval other =\n    "the count is kept"\nassertTrue(message, ok)'
        self.assertEqual([], self.lines(code, changed={3}, rule=BY_HAND),
                         "a line at the indent of the declaration ends it")
        code = 'assertTrue(message, ok)\nval message =\n    "the count is kept"'
        self.assertEqual([1], self.lines(code, changed={3}, rule=BY_HAND), "a declaration that ends the text")
        code = ('val other = "$case"\nval message = """\nthe count is kept\n"""\n'
                'assertTrue(message, ok)\nassertTrue(other, ok)')
        self.assertEqual([5], self.lines(code, changed={3}, rule=BY_HAND),
                         "a raw string line at the margin continues its declaration only")
        committed = ('val message =\n    "the count " +\n    "is " +\n    "kept"\n'
                     'fun t() {\n    assertTrue(message, ok)\n}\n')
        text = committed.replace('    "is " +\n', "")
        found = cw.check_file("app/src/test/A.kt", text, set(), committed, committed, repository_rules(), TODAY,
                              removals={2: 1})
        self.assertEqual([(5, BY_HAND)], [(f.line, f.rule) for f in found],
                         "a line removed inside the declaration of a test file lists the call")

    def test_a_changed_or_removed_destructuring_declaration_rechecks_its_calls(self) -> None:
        for declaration in ("val (message, ok) = pair", "val (message: String, ok) = pair"):
            code = f"fun t() {{\n    {declaration}\n    assertTrue(message, ok)\n}}"
            self.assertEqual([3], self.lines(code, changed={2}, rule=BY_HAND), f"{declaration}: the call is listed")
        committed = ('class T {\n    private val message = "$case"\n    fun t() {\n'
                     '        val (message, ok) = pair\n        assertTrue(message, ok)\n    }\n}\n')
        text = committed.replace("        val (message, ok) = pair\n", "")
        found = cw.check_file("app/src/test/A.kt", text, set(), committed, committed, repository_rules(), TODAY,
                              removals={3: 1})
        self.assertEqual([(4, BY_HAND)], [(f.line, f.rule) for f in found],
                         "the removed destructuring declaration lists the call")

    def test_a_changed_or_removed_extension_property_rechecks_its_calls(self) -> None:
        for declaration in ("val S.message: String get() = text", "val <T> List<T>.message: String get() = text",
                            "val kotlin.String?.message: String get() = text"):
            code = f"{declaration}\nfun t() {{\n    assertTrue(s.message, ok)\n    assertTrue(S, ok)\n}}"
            self.assertEqual([3], self.lines(code, changed={1}, rule=BY_HAND),
                             f"{declaration}: the call that uses the property is listed, the call that uses the "
                             "receiver type is not")
        committed = ('private val S.message: String get() = "the count is kept"\n'
                     'fun t() {\n    assertTrue(s.message, ok)\n}\n')
        text = committed.partition("\n")[2]
        found = cw.check_file("app/src/test/A.kt", text, set(), committed, committed, repository_rules(), TODAY,
                              removals={0: 1})
        self.assertEqual([(2, BY_HAND)], [(f.line, f.rule) for f in found],
                         "the removed extension property lists the call")

    def test_a_changed_declaration_rechecks_a_message_expression_that_uses_it(self) -> None:
        backslash = chr(92)
        for message in ("this.message", "message.trim()", '"$message".trim()', '"$message" + suffix',
                        '"""$message""".trim()', '"$`message`".trim()', f'"""{backslash}$message""".trim()',
                        f'"${{"""{backslash}$message"""}}".trim()'):
            code = f'val message = "$case"\nfun t() {{\n    assertTrue({message}, ok)\n}}'
            self.assertEqual([3], self.lines(code, changed={1}, rule=BY_HAND), f"{message}: the call is listed")
        for message in (f'"{backslash}$message".trim()', "label.trim()", f'"""${{"{backslash}$message"}}""".trim()'):
            code = f'val message = "$case"\nval other = "$message"\nfun t() {{\n    assertTrue({message}, ok)\n}}'
            self.assertEqual([], self.lines(code, changed={1}, rule=BY_HAND), f"{message}: the call is not listed")
        code = 'val message = "$case"\nfun t() {\n    assertTrue("${message}", ok)\n}'
        self.assertEqual([], self.lines(code, changed={1}), "a template does not change the words of the literal")
        code = 'val f = "$case"\nfun t() {\n    Checks.assertEquals(expected, 2.5f, 0.1f)\n}'
        self.assertEqual([], self.lines(code, changed={1}, rule=BY_HAND), "the f of 2.5f is no name")

    def test_a_function_imported_from_junit_4_and_from_another_class_is_checked_by_hand(self) -> None:
        junit, other = "import org.junit.Assert.assertTrue", "import com.applock.testing.assertTrue"
        for imports in (f"{junit}\n{other}", f"{other}\n{junit}"):
            code = f'{imports}\nassertTrue("$case", ok)'
            self.assertEqual(([], [3]), (self.lines(code), self.by_hand(code)),
                             f"{imports}: no finding, and one call to check by hand")
        self.assertIn("assertTrue is imported from org.junit.Assert and from com.applock.testing, and the script "
                      "does not know which one the call reaches",
                      self.by_hand_message(f'{junit}\n{other}\nassertTrue("$case", ok)')[0],
                      "the item names both imports")
        code = 'import com.applock.testing.assertTrue\nimport com.applock.other.assertTrue\nassertTrue("$case", ok)'
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "imports from two other classes are not read")

    def test_an_import_wins_over_a_type_alias_of_the_same_name(self) -> None:
        cases = {
            "an import of org.junit.Assert": (('import org.junit.Assert as Checks\n'
                                               'typealias Checks = com.applock.testing.Checks\n'
                                               'Checks.assertTrue("$case", ok)'), [3]),
            "an import of another class": (('import com.applock.testing.Checks as Checks\n'
                                            'typealias Checks = org.junit.Assert\nChecks.assertTrue("$case", ok)'), []),
            "the target of another type alias": (('import org.junit.Assert as Checks\n'
                                                  'typealias Checks = com.applock.testing.Checks\n'
                                                  'typealias Other = Checks\nOther.assertTrue("$case", ok)'), [4]),
            "a chain that reaches an imported name": (('import org.junit.Assert as B\ntypealias A = B\n'
                                                       'typealias B = C\ntypealias C = A\nC.assertTrue("$case", ok)'),
                                                      [5]),
        }
        for case, (code, expected) in cases.items():
            self.assertEqual((expected, []), (self.lines(code), self.by_hand(code)),
                             f"{case}: {'read as JUnit 4' if expected else 'not read'}")

    def test_the_imports_of_each_kotlin_text_are_read_once(self) -> None:
        committed = 'import org.junit.Assert.assertTrue\nfun t() {\n    assertTrue("$case", ok)\n}\n'
        text = committed.replace("ok)", "ok && ready)")
        calls: list[str] = []
        real_names, real_reason = imported_names, unread_reason

        def counting_names(masked: str, imports: Mapping[str, Sequence[str]]) -> dict[str, str | None]:
            calls.append("imported_names")
            return real_names(masked, imports)

        def counting_reason(source: KotlinText) -> tuple[int, str] | None:
            calls.append("unread_reason")
            return real_reason(source)

        with mock.patch.object(writing_checks.kotlin_assertions, "imported_names", counting_names), \
                mock.patch.object(writing_checks.kotlin_assertions, "unread_reason", counting_reason):
            cw.check_file("app/src/test/A.kt", text, {3}, committed, committed, repository_rules(), TODAY)
        self.assertEqual(["imported_names", "unread_reason"] * 2, calls,
                         "one reading of the imports of the working text and one of the committed text")

    def test_a_change_that_gives_no_call_another_class_rechecks_no_call(self) -> None:
        committed = 'fun t() {\n    assertTrue("$case", ok)\n}\n'
        text = f"import com.applock.testing.Helper\ntypealias Handler<T> = (T) -> Unit\n{committed}"
        found = cw.check_file("app/src/test/A.kt", text, {1, 2}, committed, committed, repository_rules(), TODAY)
        self.assertEqual([], found, "the unchanged call keeps its class, so it is not read again")

    def test_a_backticked_type_alias_puts_the_file_on_the_list_to_check_by_hand(self) -> None:
        cases = {
            "an alias of org.junit.Assert": ('typealias `Junit Assert` = org.junit.Assert',
                                             ("the file declares the type alias `Junit Assert` of org.junit.Assert, "
                                              "a backticked name")),
            "an alias with type parameters": ('typealias `Junit Assert`<T> = org.junit.Assert',
                                              ("the file declares the type alias `Junit Assert`, a backticked name "
                                               "whose target the script cannot read")),
        }
        for case, (alias, message) in cases.items():
            code = f'{alias}\n`Junit Assert`.assertTrue("$case", ok)'
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{case}: no finding, and one item at the type alias to check by hand")
            self.assertIn(message, self.by_hand_message(code)[0], f"{case}: the item names the type alias")
        code = 'typealias `My Checks` = com.applock.testing.Checks\nassertTrue("$case", ok)'
        self.assertEqual(([2], []), (self.lines(code), self.by_hand(code)),
                         "a backticked alias of another class leaves the file readable")

    def test_an_import_with_spaces_or_backticks_in_its_name_is_resolved(self) -> None:
        for code in ('import org.junit . Assert as Checks\nChecks.assertTrue("$case", ok)',
                     'import org.`junit`.Assert as Checks\nChecks.assertTrue("$case", ok)',
                     'import org.junit\n    .Assert as Checks\nChecks.assertTrue("$case", ok)'):
            self.assertEqual([code.count("\n") + 1], self.lines(code), f"{code}: the call through the alias is read")
        code = 'import com.applock . verify as assertTrue\nassertTrue(ok, "$case")'
        self.assertIn("the file imports com.applock.verify as assertTrue", self.by_hand_message(code)[0],
                      "the renamed function puts the file on the list to check by hand")

    def test_each_kotlin_text_is_masked_once(self) -> None:
        committed = 'import org.junit.Assert\nfun t() {\n    assertTrue("$case", ok)\n}\n'
        text = committed.replace("ok)", "ok && ready)")
        masked: list[int] = []
        real_mask = kotlin_mask

        def counting_mask(kotlin: str) -> str:
            masked.append(len(kotlin))
            return real_mask(kotlin)

        with mock.patch.object(writing_checks.kotlin_assertions, "kotlin_mask", counting_mask):
            cw.check_file("app/src/test/A.kt", text, {3}, committed, committed, repository_rules(), TODAY)
        self.assertEqual(sorted([len(text), len(committed)]), sorted(masked),
                         "one mask of the working text and one of the committed text")

    def test_every_call_is_checked_without_the_committed_call_context(self) -> None:
        text = 'import org.junit.Assert\nfun t() {\n    assertTrue("$case", ok)\n}\n'
        masked: list[int] = []
        real_mask = kotlin_mask

        def counting_mask(kotlin: str) -> str:
            masked.append(len(kotlin))
            return real_mask(kotlin)

        with mock.patch.object(writing_checks.kotlin_assertions, "kotlin_mask", counting_mask):
            found = cw.check_file("app/src/test/A.kt", text, None, text, text, repository_rules(), TODAY)
        self.assertEqual(([(3, "3.4")], 1), ([(f.line, f.rule) for f in found], len(masked)),
                         "the call is reported, and only the working text is masked")

    def test_a_change_inside_a_trailing_lambda_rechecks_the_call(self) -> None:
        code = 'assertThrows("$case", Failure::class.java) {\n    throwIt()\n}'
        self.assertEqual([1], self.lines(code, changed={2}), "the lambda is part of the call")
        self.assertEqual([], self.lines(f"{code}\nassertTrue(ok)", changed={4}),
                         "a changed line after the lambda is not part of the call")

    def test_a_comment_inside_a_template_is_skipped(self) -> None:
        for template in ('"${case /* opening { */}"', '"${case // a " quote\n}"'):
            code = f'val debug = {template}\nassertTrue("$case", ok)'
            self.assertEqual([code.count("\n") + 1], self.lines(code),
                             f"{template}: the comment does not hide the next assertion")

    def test_a_backticked_name_inside_a_template_is_skipped(self) -> None:
        code = 'val debug = "${`a 2" pipe`}"\nassertTrue("$case", ok)'
        self.assertEqual([2], self.lines(code), "the quote in the name does not hide the next assertion")

    def test_commas_and_parentheses_inside_a_string_do_not_split_arguments(self) -> None:
        self.assertEqual([], self.lines('assertEquals("the pair (a, b) is stored", x, y)'),
                         "one message with words")
        code = 'assertEquals("${names.joinToString(", ")}", a, b)'
        self.assertEqual([1], self.lines(code), "a nested string stays inside its template")

    def test_calls_inside_comments_are_not_read(self) -> None:
        code = '// assertEquals("$x", a, b)\n/* assertTrue(label, ok) /* nested */ */'
        self.assertEqual([], self.lines(code), "comments are not calls")

    def test_type_arguments_do_not_split_arguments(self) -> None:
        self.assertEqual([1], self.lines('assertEquals("$x", emptyList<Pair<Int, Int>>(), y)'),
                         "three arguments with a message")
        self.assertEqual([], self.lines("assertEquals(emptyList<Pair<Int, Int>>(), y)"),
                         "two arguments without a message")
        self.assertEqual([1], self.lines('assertTrue("$x", value is Map<*, *>)'),
                         "a star projection opens type arguments too")

    def test_a_comparison_without_spaces_is_not_a_type_argument(self) -> None:
        self.assertEqual([1], self.lines('assertEquals("$case", x<0, flag)'),
                         "the comparison leaves three arguments, so the message is read")

    def test_type_arguments_follow_the_kotlin_parser(self) -> None:
        for code in ('assertSame("$case", emptyMap<kotlin.String, kotlin.Int>(), actual)',
                     'assertTrue("$case", value is Map<out K, V>)',
                     'assertEquals("$case", emptyList<(Int) -> Unit>(), actual)',
                     'assertEquals("$case", x<MAX, expected)', 'assertEquals("$case", x < limit, flag)'):
            self.assertEqual([1], self.lines(code), f"{code}: the message is read")
        code = 'assertEquals("$case", a < b, c > (d))'
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "Kotlin reads the generic call a<b, c>(d), so the call has no message")

    def test_a_call_that_fits_no_junit_4_form_is_checked_by_hand(self) -> None:
        code = 'assertSame("$case", emptyMap<`Lock State`, Int>(), actual)'
        self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                         "the backticked type splits the call into four arguments")
        self.assertIn("assertSame with 4 arguments fits no JUnit 4 form", self.by_hand_message(code)[0],
                      "the item names the call and its argument count")
        self.assertEqual([], self.lines(f"{code}\nassertTrue(ok)", changed={2}, rule=BY_HAND),
                         "an unchanged call is not listed")

    def test_a_message_after_explicit_type_arguments_is_read(self) -> None:
        code = 'assertThrows<IllegalStateException>("$case", IllegalStateException::class.java) { run() }'
        self.assertEqual([1], self.lines(code), "the message after the type arguments is read")

    def test_annotation_arguments_in_type_arguments_are_skipped(self) -> None:
        for code in ('assertThrows<@Suppress("x") Failure>("$case", Failure::class.java) { run() }',
                     'assertSame("$case", emptyMap<@Ann("k") String, Int>(), actual)'):
            self.assertEqual([1], self.lines(code), f"{code}: the annotation arguments are skipped")

    def test_type_arguments_that_do_not_parse_are_checked_by_hand(self) -> None:
        code = 'assertThrows<`a b`>("$case", Failure::class.java) { run() }'
        self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                         "the backticked type name stops the parse, so the call is listed")
        self.assertEqual([], self.lines(f"{code}\nassertTrue(ok)", changed={2}, rule=BY_HAND),
                         "an unchanged call is not listed")
        code = 'assertThrows<`a b`>(\n    "$case",\n    Failure::class.java,\n) { run() }'
        self.assertEqual([1], self.lines(code, changed={2}, rule=BY_HAND),
                         "a change on the message line of a multi-line call lists the call")

    def test_unparsed_type_arguments_with_annotation_arguments_keep_the_whole_call(self) -> None:
        code = 'assertThrows<@Mark("x") `a b`>(\n    "$case",\n) { run() }'
        self.assertEqual([1], self.lines(code, changed={2}, rule=BY_HAND),
                         "the parentheses of the annotation do not end the call, so its message line counts")

    def test_backticked_plain_names_are_read_as_names(self) -> None:
        for code in ('`assertTrue`("$case", ok)', '`Assert`.assertTrue("$case", ok)',
                     'import org.junit.Assert.`assertTrue`\n\n`assertTrue`("$case", ok)'):
            self.assertEqual([code.count("\n") + 1], self.lines(code), f"{code}: the call is read")

    def test_a_trailing_lambda_counts_as_an_argument(self) -> None:
        self.assertEqual([1], self.lines('assertThrows("$x", Foo::class.java) { run() }'),
                         "the message of assertThrows is reported")
        self.assertEqual([], self.lines("assertThrows(Foo::class.java) { run() }"),
                         "no message")

    def test_a_qualified_call_is_read_and_a_definition_is_not(self) -> None:
        self.assertEqual([1], self.lines('Assert.assertEquals("$x", a, b)'), "Assert. is read")
        self.assertEqual([], self.lines("private fun assertTrue(label: String, ok: Boolean) {}"),
                         "a definition is not a call")

    def test_a_definition_with_nested_type_parameters_is_not_a_call(self) -> None:
        code = "fun <T : Comparable<T>> assertEquals(message: String, a: T, b: T) {}"
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "the definition is not a call, so nothing is listed")

    def test_a_definition_with_long_type_parameters_is_not_a_call(self) -> None:
        bounds = ",\n    ".join(["T : Comparable<T>", *(f"U{number} : Any" for number in range(8))])
        code = f"fun <\n    {bounds},\n> assertEquals(message: String, a: T, b: T, c: T) {{}}"
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "type parameters over several lines still mark a definition")

    def test_quotes_in_a_backticked_test_name_open_no_string(self) -> None:
        code = 'fun `the "fast" path isn\'t used`() {\n    assertTrue("$label", ok)\n}'
        self.assertEqual([2], self.lines(code), "the quotes of the name open no string")

    def test_only_changed_calls_are_reported(self) -> None:
        code = 'assertTrue("$label", ok)\nassertTrue("$name", ok)'
        self.assertEqual([2], self.lines(code, changed={2}), "only the changed line")

    def test_undeclared_names_in_three_arguments_are_checked_by_hand(self) -> None:
        for code in ("assertEquals(expected, actual, tolerance)",
                     "assertArrayEquals(expecteds, actuals, DELTA)",
                     "assertEquals(wanted, actual, allowedError)"):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one call to check by hand")

    def test_declared_types_are_not_read(self) -> None:
        code = "val wanted: Double = 1.0\nval allowedError = 0.1\nassertEquals(wanted, actual, allowedError)"
        self.assertEqual(([], [3]), (self.lines(code), self.by_hand(code)),
                         "the script does not read declarations, so the call is checked by hand")

    def test_names_do_not_decide_the_overload(self) -> None:
        self.assertEqual([1], self.lines('assertEquals("$case", 1, actualDelta)'),
                         "the string literal is the message, whatever the last name says")

    def test_a_literal_beside_the_message_rules_out_the_tolerance_form(self) -> None:
        for code in ("assertEquals(name, 0, harness.failureCount())",
                     "assertEquals(name, null, value)", 'assertEquals(label, "stored", actual)'):
            self.assertIn("does not read", self.by_hand_message(code)[0],
                          f"{code}: the message position is certain, its value is not read")
        self.assertIn("if `name` is the message", self.by_hand_message("assertEquals(name, a, b)")[0],
                      "without a literal the overload stays open")
        self.assertIn("if `name` is the message", self.by_hand_message("assertEquals(name, 1.0, x)")[0],
                      "a floating literal does not rule out the tolerance form")

    def test_variables_are_never_resolved(self) -> None:
        cases = {
            "a computed initializer": 'val expected = "1.0".toDouble()\nassertEquals(expected, actual, 0.01)',
            "another function": 'fun a() {\n    val label = "$x"\n}\nfun b() {\n    assertTrue(label, ok)\n}',
            "a lambda parameter": 'val label = "$x"\nitems.forEach { label ->\n    assertTrue(label, ok)\n}',
            "a nullable lambda parameter": ('val label = "$x"\nitems.forEach { label: String? ->\n'
                                            "    assertTrue(label, ok)\n}"),
            "the implicit it": 'val it = "$case"\nmessages.forEach { assertTrue(it, ok) }',
            "a continued initializer": 'val label = "$case"\n    .trim()\nassertTrue(label, ok)',
            "a custom getter": 'val label: String = "$case"\n    get() = "the count stays"\nassertTrue(label, ok)',
            "a same-line value": 'val label = "$case"; assertTrue(label, ok)',
        }
        for case, code in cases.items():
            self.assertEqual([], self.lines(code), f"{case}: no finding")
            self.assertEqual(1, len(self.by_hand(code)), f"{case}: one call to check by hand")

    def test_a_changed_declaration_rechecks_the_call(self) -> None:
        code = 'fun t() {\n    val message = "$case"\n    assertEquals(message, 0, actual)\n}'
        self.assertEqual([3], self.lines(code, changed={2}, rule=BY_HAND),
                         "the changed declaration of message lists the unchanged call to check")
        self.assertEqual([], self.lines(code, changed={1}, rule=BY_HAND),
                         "an unrelated changed line")

    def test_a_changed_argument_line_rechecks_the_call(self) -> None:
        code = "assertEquals(\n    label,\n    expected,\n    delta,\n)"
        self.assertEqual([2], self.lines(code, changed={4}, rule=BY_HAND),
                         "the changed last argument decides how the call is read")

    def test_a_changed_import_rechecks_every_call(self) -> None:
        committed = 'import kotlin.test.assertEquals\nfun t() {\n    assertEquals("$case", a, b)\n}\n'
        text = committed.replace("kotlin.test", "org.junit.Assert")
        found = cw.check_file("app/src/test/A.kt", text, {1}, committed, committed, repository_rules(), TODAY)
        self.assertEqual([(3, "3.4")], [(f.line, f.rule) for f in found],
                         "JUnit 4 makes the unchanged call readable, with the first argument as message")
        committed = text.replace('"$case"', "label")
        text = f"import com.applock.Other\n{committed}"
        found = cw.check_file("app/src/test/A.kt", text, {1}, committed, committed, repository_rules(), TODAY)
        self.assertEqual([], found, "an import of another function rechecks nothing")

    def test_a_removed_kotlin_test_call_rechecks_every_call(self) -> None:
        committed = 'fun t() {\n    kotlin.test.fail("unreachable")\n    assertTrue("$case", ok)\n}\n'
        text = committed.replace('    kotlin.test.fail("unreachable")\n', "")
        found = cw.check_file("app/src/test/A.kt", text, set(), committed, committed, repository_rules(), TODAY)
        self.assertEqual([(2, "3.4")], [(f.line, f.rule) for f in found],
                         "without kotlin.test, the unchanged JUnit 4 call is read")

    def test_the_finding_text_is_true_for_a_one_letter_prefix(self) -> None:
        found = assertion_findings('assertTrue("f$offsetMs", ok)')
        self.assertIn('"f$offsetMs" holds no words besides templates and key names',
                      found[0].message, "the text is true for the letter f")

    def test_many_calls_in_a_large_file_are_read_in_linear_time(self) -> None:
        body = "\n".join(f"    val value{number} = compute({number})" for number in range(2000))
        calls = "\n".join(f"    assertEquals(name{number % 50}, value{number}, other{number})"
                          for number in range(2000))
        started = time.monotonic()
        assertion_findings(f"fun t() {{\n{body}\n{calls}\n}}")
        self.assertLess(time.monotonic() - started, TIME_LIMIT_SECONDS, "the file is read in linear time")

    def test_a_file_that_uses_kotlin_test_or_junit_5_is_checked_by_hand(self) -> None:
        cases = {
            "a message before a block": 'import kotlin.test.assertTrue\n\nassertTrue("$case") { false }',
            "a message before a cause": 'import kotlin.test.fail\n\nfail("$case", cause)',
            "a message between a value and a block": ('import kotlin.test.assertNotNull\n\n'
                                                      'assertNotNull(x, "$case") { it.size }'),
            "a function outside the table": ('import kotlin.test.assertFailsWith\n\n'
                                             'assertFailsWith(Foo::class, "$case") { run() }'),
            "a lazy message": 'import kotlin.test.assertTrue\n\nassertTrue(ok) { "$case" }',
            "a named message": 'import kotlin.test.assertTrue\n\nassertTrue(ok, message = "$case")',
            "star imports of two libraries": 'import kotlin.test.*\nimport org.junit.Assert.*\n\nassertTrue(label, ok)',
            "a JUnit 5 message supplier": ('import org.junit.jupiter.api.Assertions.assertTrue\n\n'
                                           'assertTrue(ok) { "$case" }'),
            "a JUnit 5 message before a cause": 'import org.junit.jupiter.api.Assertions.fail\n\nfail("$case", cause)',
            "a JUnit 5 Kotlin function": 'import org.junit.jupiter.api.assertNotNull\n\nassertNotNull(x, "$case")',
            "a Hamcrest reason and condition": ('import org.hamcrest.MatcherAssert.assertThat\n\n'
                                                'assertThat("$case", ok)'),
            "a qualified Hamcrest call": 'import org.hamcrest.MatcherAssert\n\nMatcherAssert.assertThat("$case", ok)',
        }
        for case, code in cases.items():
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{case}: no finding, and one item at the import to check by hand")
        self.assertIn("the file uses JUnit 5, so the script does not read its assertion messages",
                      self.by_hand_message(cases["a JUnit 5 message supplier"])[0],
                      "the item names the library")

    def test_hamcrest_matchers_alone_leave_the_file_readable(self) -> None:
        code = 'import org.hamcrest.Matchers.equalTo\n\nAssert.assertThat("$case", actual, equalTo(1))'
        self.assertEqual([3], self.lines(code), "the JUnit 4 assertThat is read")

    def test_an_import_alias_puts_the_file_on_the_list_to_check_by_hand(self) -> None:
        for code in ('import org.junit.Assert.assertTrue as check\n\ncheck("$case", ok)',
                     'import com.applock.testing.verify as assertTrue\n\nassertTrue(ok, "$case")'):
            self.assertEqual(([], [1]), (self.lines(code), self.by_hand(code)),
                             f"{code}: no finding, and one item at the import to check by hand")
        self.assertIn("the file imports org.junit.Assert.assertTrue as check",
                      self.by_hand_message('import org.junit.Assert.assertTrue as check')[0],
                      "the item names the import")

    def test_an_import_with_a_semicolon_is_resolved(self) -> None:
        code = 'import com.applock.testing.assertTrue;\nassertTrue("$label", ok)'
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "the function imported from another package is not JUnit 4")

    def test_a_qualified_kotlin_test_call_puts_the_file_on_the_list_to_check_by_hand(self) -> None:
        code = 'fun t() {\n    kotlin.test.fail("$case", cause)\n}'
        self.assertEqual(([], [2]), (self.lines(code), self.by_hand(code)),
                         "the qualified call puts the file on the list to check by hand")

    def test_a_fully_qualified_junit_4_call_is_read(self) -> None:
        self.assertEqual([1], self.lines('org.junit.Assert.assertEquals("$x", a, b)'), "read")

    def test_imports_resolve_the_class_of_a_call(self) -> None:
        cases = {
            "an alias of org.junit.Assert": (('import org.junit.Assert as JUnitAssert\n'
                                              'JUnitAssert.assertTrue("$case", ok)'), [2]),
            "junit.framework.TestCase": ('import junit.framework.TestCase\nTestCase.assertTrue("$case", ok)', [2]),
            "a project class named Assert": (('import com.applock.testing.Assert\n'
                                              'Assert.assertTrue("$case", ok)'), []),
            "a function of a project class named Assert": (('import com.applock.testing.Assert.assertTrue\n'
                                                            'assertTrue("$case", ok)'), []),
        }
        for case, (code, expected) in cases.items():
            self.assertEqual((expected, []), (self.lines(code), self.by_hand(code)),
                             f"{case}: {'read as JUnit 4' if expected else 'not read'}")

    def test_a_changed_class_alias_rechecks_every_call(self) -> None:
        committed = 'import com.applock.testing.Checks as Check\nfun t() {\n    Check.assertTrue("$case", ok)\n}\n'
        text = committed.replace("com.applock.testing.Checks", "org.junit.Assert")
        found = cw.check_file("app/src/test/A.kt", text, {1}, committed, committed, repository_rules(), TODAY)
        self.assertEqual([(3, "3.4")], [(f.line, f.rule) for f in found],
                         "the alias now names org.junit.Assert, so the unchanged call is read")

    def test_calls_of_other_owners_are_not_read(self) -> None:
        self.assertEqual([], self.lines("harness.assertTrue(label, ok)"), "a member function")
        self.assertEqual([], self.lines("fun Foo.assertTrue(label: String, ok: Boolean) {}"),
                         "an extension function definition")
        code = 'import com.applock.testing.assertTrue\nassertTrue("$label", ok)'
        self.assertEqual(([], []), (self.lines(code), self.by_hand(code)),
                         "a function imported from another package is not JUnit 4")
        code = 'import junit.framework.Assert.assertTrue\nassertTrue("$label", ok)'
        self.assertEqual([2], self.lines(code), "an import from a class named Assert is JUnit 4")

    def test_only_test_sources_are_checked_for_messages(self) -> None:
        found = cw.check_file("app/src/main/A.kt", "assertTrue(label, ok)\n", None, None, None,
                              repository_rules(), TODAY)
        self.assertEqual([], found, "production code is not checked for messages")

    def test_test_source_sets_are_recognized_by_name(self) -> None:
        for path in ("app/src/test/A.kt", "app/src/testDebug/A.kt", "app/src/androidTest/A.kt",
                     "app/src/integrationTest/kotlin/A.kt", "shared/src/commonTest/kotlin/A.kt"):
            self.assertTrue(cw.kotlin_test(path), f"{path} is a test file")
        for path in ("app/src/main/A.kt", "app/src/attestation/A.kt"):
            self.assertFalse(cw.kotlin_test(path), f"{path} is not a test file")


class GitTest(unittest.TestCase):
    """The script against a temporary repository, with the git configuration, the global git
    files, and the git variables of this host kept out of it."""

    def setUp(self) -> None:
        self.home = Path(self.enterContext(tempfile.TemporaryDirectory()))
        self.repo = self.home / "repo"
        self.repo.mkdir()
        config = self.home / "gitconfig"
        config.touch()
        # Git reads its global attributes and ignore files under XDG_CONFIG_HOME. A GIT_ variable of
        # a calling process, such as the GIT_INDEX_FILE of a hook, points git at another repository.
        environment = {name: value for name, value in os.environ.items() if not name.startswith("GIT_")}
        environment |= {"XDG_CONFIG_HOME": str(self.home), "GIT_CONFIG_GLOBAL": str(config),
                        "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "test",
                        "GIT_AUTHOR_EMAIL": "test@example.invalid", "GIT_COMMITTER_NAME": "test",
                        "GIT_COMMITTER_EMAIL": "test@example.invalid"}
        patcher = mock.patch.dict(os.environ, environment, clear=True)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.git("init", "-q", "-b", "main")
        self.write("docs/a.md", "".join(f"{'w' * width}\n" for width in (75, 79, 77) * 10))
        old = entry("This change is old.", "One bullet.", date="2026-01-01")
        self.write("changelog.txt", "\n".join(["CHANGELOG", "=========", "", *old]))
        self.git("add", ".")
        self.git("commit", "-q", "-m", "base")

    def git(self, *args: str) -> str:
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True,
                              encoding="utf-8", timeout=PROCESS_TIMEOUT_SECONDS).stdout

    def write(self, path: str, text: str) -> None:
        full = self.repo / path
        full.parent.mkdir(parents=True, exist_ok=True)
        full.write_text(text, encoding="utf-8", newline="\n")

    def read(self, path: str) -> str:
        return (self.repo / path).read_text(encoding="utf-8")

    def append_line(self, path: str, line: str) -> None:
        self.write(path, f"{self.read(path)}{line}\n")

    def add_entry_on_top(self, new: Sequence[str]) -> None:
        """Puts the entry lines new above the entries of the changelog, below its header."""
        rest = self.read("changelog.txt").split("\n", 3)[3]
        self.write("changelog.txt", "\n".join(["CHANGELOG", "=========", "", *new, rest]))

    def remove_object(self, name: str) -> None:
        """Deletes the git object of name, such as "HEAD:docs/a.md", so git cannot read it."""
        blob = self.git("rev-parse", name).strip()
        object_path = self.repo / ".git" / "objects" / blob[:2] / blob[2:]
        object_path.chmod(stat.S_IWRITE)  # git stores objects read-only, which Windows enforces
        object_path.unlink()

    def commit_all(self, message: str) -> None:
        self.git("add", ".")
        self.git("commit", "-q", "-m", message)

    def run_check(self, *args: str, cwd: Path | None = None) -> tuple[int, list[str], str]:
        """Exit status, findings, and errors of a run with args in cwd, by default the repository."""
        stdout_buffer, stderr_buffer = io.StringIO(), io.StringIO()
        code = cw.main(list(args), cwd=self.repo if cwd is None else cwd, today=TODAY, stdout=stdout_buffer,
                       stderr=stderr_buffer)
        findings = [" ".join(line.split(" ")[:2])
                    for line in stdout_buffer.getvalue().splitlines()
                    if line.startswith(("docs", "chan", "app")) and "[" in line]
        return code, findings, stderr_buffer.getvalue()

    def git_calls(self, *args: str) -> list[Sequence[str]]:
        """The arguments of each git command of a run of the script with args."""
        calls: list[Sequence[str]] = []
        real_git = writing_checks.git_repo.git

        def recording_git(git_args: Sequence[str], cwd: str | Path, stdin: str | None = None) -> str:
            calls.append(git_args)
            return real_git(git_args, cwd, stdin)

        with mock.patch.object(writing_checks.git_repo, "git", recording_git):
            self.run_check(*args)
        return calls

    def checked_by_hand(self, *args: str) -> list[str]:
        """The places, as "path:line", of the calls to check by hand in a run with args."""
        listed = self.report_text(*args).partition("\nCheck by hand")[2].partition("\n\n")[0]
        return [line.split(": ")[0] for line in listed.splitlines()[1:]]

    def report_text(self, *args: str) -> str:
        """The report of a run with args, as the script prints it."""
        stdout_buffer = io.StringIO()
        cw.main(list(args), cwd=self.repo, today=TODAY, stdout=stdout_buffer, stderr=io.StringIO())
        return stdout_buffer.getvalue()

    def shown_paths(self, *args: str) -> list[str]:
        """The paths of the committed versions that git show reads in a run with args."""
        return [call[1].split(":", 1)[1] for call in self.git_calls(*args) if call[0] == "show"]

    def test_uncommitted_and_untracked_changes_are_reported(self) -> None:
        self.add_entry_on_top(entry("Intro.", "A new bullet.", date="2026-10-19"))
        self.append_line("docs/a.md", "x" * 80)
        self.write("docs/b.md", f"See {SIGN}2.\n")
        code, findings, stderr_text = self.run_check()
        self.assertEqual("", stderr_text, "no error")
        self.assertEqual(1, code, "findings give exit status 1")
        self.assertEqual(["changelog.txt:4: [5.1.3]", "docs/a.md:31: [width]",
                          "docs/b.md:1: [1.9]"], findings,
                         "the new entry, the new wide line, and the untracked file are reported")

    def test_a_new_entry_that_repeats_a_bullet_of_the_older_entry_is_reported(self) -> None:
        self.add_entry_on_top(entry("Intro.", "One bullet."))
        code, findings, _ = self.run_check()
        self.assertEqual((1, ["changelog.txt:7: [5.4.4]"]), (code, findings),
                         "only the copied bullet is reported, and the older entry stays unchanged")

    def test_a_branch_commit_is_checked_against_its_merge_base(self) -> None:
        self.git("checkout", "-q", "-b", "work")
        self.append_line("docs/a.md", "x" * 80)
        self.git("commit", "-q", "-am", "work")
        self.assertEqual((0, [], ""), self.run_check(), "a clean tree has no findings")
        code, findings, _ = self.run_check("--base", "main")
        self.assertEqual((1, ["docs/a.md:31: [width]"]), (code, findings),
                         "the branch commit is checked against main")

    def test_a_file_removed_from_the_index_is_checked_in_full(self) -> None:
        self.write("docs/c.md", numbered_lines(10).replace("Line 2.", f"See {SIGN}2."))
        self.commit_all("file")
        self.git("rm", "-q", "--cached", "docs/c.md")
        self.write("docs/c.md", self.read("docs/c.md").replace("Line 4.", f"See {SIGN}4."))
        expected = (1, ["docs/c.md:2: [1.9]", "docs/c.md:4: [1.9]"], "")
        self.assertEqual(expected, self.run_check(), "the untracked file is checked in full")
        self.assertEqual(expected, self.run_check("docs/c.md"), "the named file is checked in full")

    def test_a_byte_order_mark_does_not_hide_an_import_on_line_1(self) -> None:
        path = "app/src/test/A.kt"
        self.write(path, f'{BYTE_ORDER_MARK}import org.junit.Assert as Checks\nfun t() {{\n'
                         '    Checks.assertTrue("$case", ok)\n}\n')
        self.assertEqual((1, [f"{path}:3: [3.4]"], ""), self.run_check(), "the call through the alias is read")

    def test_the_byte_order_mark_of_a_committed_changelog_is_no_text(self) -> None:
        text = f"{BYTE_ORDER_MARK}{self.read('changelog.txt')}"
        self.write("changelog.txt", text)
        self.commit_all("byte order mark")
        *header, rest = text.split("\n", 3)
        self.write("changelog.txt", "\n".join([*header, *entry("Intro.", "A new bullet."), rest]))
        self.assertEqual((0, [], ""), self.run_check(), "the new entry on top changes no committed text")

    def test_a_file_that_is_not_utf_8_is_reported(self) -> None:
        (self.repo / "docs" / "c.md").write_bytes(b"Text.\n\xff\n")
        code, findings, _ = self.run_check()
        self.assertEqual((1, ["docs/c.md:2: [encoding]"]), (code, findings),
                         "the line of the first byte that is not UTF-8 is reported")

    def test_errors_give_exit_status_2(self) -> None:
        code, _, stderr_text = self.run_check("--base=--output=x")
        self.assertEqual(2, code, "a base that starts with a dash is refused")
        self.assertIn("is not a commit name", stderr_text, "the error names the problem")
        self.write("docs/b.md", f"See {SIGN}2.\n")
        code, findings, stderr_text = self.run_check("docs/missing.md", "docs/b.md")
        self.assertEqual((2, ["docs/b.md:1: [1.9]"]), (code, findings),
                         "a missing file is an error, and the other findings are still reported")
        self.assertIn("is not a file", stderr_text, "the error names the missing file")

    def test_a_hung_git_command_is_an_error(self) -> None:
        hung = subprocess.TimeoutExpired(["git", "rev-parse"], GIT_TIMEOUT_SECONDS)
        with mock.patch.object(subprocess, "run", side_effect=hung):
            code, _, stderr_text = self.run_check()
        self.assertEqual((2, True), (code, "did not finish within" in stderr_text),
                         "exit status 2 and an error line, not a traceback")

    @unittest.skipUnless(os.name == "nt", "only Windows has drive letters")
    def test_a_path_on_another_drive_is_an_error(self) -> None:
        drive = "Y:" if self.repo.drive.upper() == "Z:" else "Z:"
        code, _, stderr_text = self.run_check(f"{drive}\\notes.md")
        self.assertEqual((2, True), (code, "is outside the repository" in stderr_text),
                         "the path gives an error, not a traceback")

    def test_named_files_are_found_from_a_linked_work_folder(self) -> None:
        self.write("docs/b.md", f"See {SIGN}2.\n")
        link = self.home / "link"
        if os.name == "nt":  # a junction needs no privilege, unlike a symbolic link on Windows
            subprocess.run(["cmd", "/c", "mklink", "/J", str(link), str(self.repo)], check=True, capture_output=True,
                           timeout=PROCESS_TIMEOUT_SECONDS)
        else:
            link.symlink_to(self.repo, target_is_directory=True)
        (self.repo / "docs" / "sub").mkdir()
        names = ("../b.md", str(self.repo / "docs" / "b.md"))
        self.assertEqual((1, ["docs/b.md:1: [1.9]"], ""), self.run_check(*names, cwd=link / "docs" / "sub"),
                         "the relative and the absolute name are both the file docs/b.md of the repository")

    @unittest.skipUnless(os.name == "nt", "only Windows has the \\\\?\\ prefix")
    def test_named_files_are_found_from_a_work_folder_with_the_long_path_prefix(self) -> None:
        self.write("docs/b.md", f"See {SIGN}2.\n")
        names = ("docs/b.md", str(self.repo / "docs" / "b.md"))
        self.assertEqual((1, ["docs/b.md:1: [1.9]"], ""), self.run_check(*names, cwd=Path(f"\\\\?\\{self.repo}")),
                         "the relative and the absolute name are both the file docs/b.md of the repository")

    def test_a_named_file_of_a_folder_outside_the_work_tree_is_an_error(self) -> None:
        elsewhere = self.home / "elsewhere"
        (elsewhere / "docs").mkdir(parents=True)
        (elsewhere / "docs" / "a.md").write_text(f"See {SIGN}2.\n", encoding="utf-8")
        with mock.patch.dict(os.environ, {"GIT_DIR": str(self.repo / ".git"), "GIT_WORK_TREE": str(self.repo)}):
            code, _, stderr_text = self.run_check("docs/a.md", cwd=elsewhere)
        self.assertEqual((2, True), (code, "is outside the repository" in stderr_text),
                         "the file of the other folder is not read as the docs/a.md of the repository")

    def test_a_nested_repository_does_not_stop_the_run(self) -> None:
        nested = self.repo / "nested"
        nested.mkdir()
        subprocess.run(["git", "init", "-q"], cwd=nested, check=True, capture_output=True,
                       timeout=PROCESS_TIMEOUT_SECONDS)
        self.write("docs/b.md", f"See {SIGN}2.\n")
        self.assertEqual((1, ["docs/b.md:1: [1.9]"], ""), self.run_check(),
                         "the nested repository is skipped, and the other files are checked")

    def test_a_deleted_bullet_of_a_committed_entry_is_reported_in_git(self) -> None:
        self.write("changelog.txt", self.read("changelog.txt").replace("- One bullet.\n", ""))
        code, findings, _ = self.run_check()
        self.assertEqual(1, code, "the deletion gives a finding")
        self.assertIn("changelog.txt:6: [5.1.4]", findings, "the shortened entry is reported")

    def test_a_branch_commit_that_edits_a_main_entry_is_reported(self) -> None:
        self.git("checkout", "-q", "-b", "work")
        self.write("changelog.txt", self.read("changelog.txt").replace("One bullet.",
                                                                       "One bullet, edited."))
        self.git("commit", "-q", "-am", "edit")
        self.assertEqual((0, [], ""), self.run_check(), "the clean tree has no findings")
        code, findings, _ = self.run_check("--base", "main")
        self.assertEqual(1, code, "the branch commit gives a finding")
        self.assertIn("changelog.txt:7: [5.1.4]", findings, "the edited bullet is reported")

    def test_a_deleted_changelog_is_reported(self) -> None:
        (self.repo / "changelog.txt").unlink()
        expected = (1, ["changelog.txt:1: [5.1.4]"], "")
        self.assertEqual(expected, self.run_check(), "the deletion is reported")
        self.assertEqual(expected, self.run_check("changelog.txt"),
                         "a named deleted changelog is reported, not an error")
        self.assertIn("the file is deleted, which removes its 1 committed entry", self.report_text(),
                      "the finding names the deletion")

    def test_a_changelog_removed_from_the_index_is_reported_as_deleted(self) -> None:
        self.git("rm", "-q", "--cached", "changelog.txt")
        expected = (1, ["changelog.txt:1: [5.1.4]"], "")
        self.assertEqual(expected, self.run_check(),
                         "the next commit deletes the changelog, although its working file stays")
        self.assertEqual(expected, self.run_check("changelog.txt"), "the named changelog gets the same finding")
        self.assertIn("the index does not hold the file, which removes its 1 committed entry", self.report_text(),
                      "the finding names the missing index entry")

    def test_a_changelog_renamed_only_in_case_on_the_branch_is_reported_as_deleted(self) -> None:
        self.git("checkout", "-q", "-b", "work")
        self.git("mv", "changelog.txt", "CHANGELOG.txt")
        self.git("commit", "-q", "-m", "rename")
        for args in (["--base", "main"], ["--base", "main", "changelog.txt"]):
            self.assertEqual((1, ["changelog.txt:1: [5.1.4]"], ""), self.run_check(*args),
                             f"{args}: the branch removes changelog.txt, although a file system that ignores case "
                             "finds CHANGELOG.txt under that name")

    def test_a_removed_fence_rechecks_the_lines_of_its_block(self) -> None:
        block = f"```\nSee {SIGN}3.\n{'p' * 130}\n```\n"
        self.append_line("docs/a.md", block.removesuffix("\n"))
        self.commit_all("code block")
        self.write("docs/a.md", self.read("docs/a.md").replace(block, block.removeprefix("```\n")))
        self.assertEqual((1, ["docs/a.md:31: [1.9]", "docs/a.md:32: [width]"], ""), self.run_check(),
                         "without its opening fence, the unchanged lines of the block are text")

    def test_a_reverted_branch_entry_is_reported(self) -> None:
        main_text = self.read("changelog.txt")
        self.git("checkout", "-q", "-b", "work")
        self.add_entry_on_top(entry("Branch intro.", "Branch bullet.", title="Docs: branch"))
        self.git("commit", "-q", "-am", "branch entry")
        self.write("changelog.txt", main_text)
        code, findings, _ = self.run_check("--base", "main")
        self.assertEqual((1, ["changelog.txt:4: [5.1.4]"]), (code, findings),
                         "the removed branch entry is reported, although the file equals main")

    def test_a_deleted_import_rechecks_the_calls_of_a_test_file(self) -> None:
        path = "app/src/test/A.kt"
        self.write(path, "import kotlin.test.assertEquals\nfun t() {\n"
                         '    assertEquals("$case", expected, actual)\n}\n')
        self.git("add", ".")
        self.git("commit", "-q", "-m", "test file")
        self.write(path, self.read(path).split("\n", 1)[1])
        self.assertEqual((1, [f"{path}:2: [3.4]"], ""), self.run_check(),
                         "without the import, JUnit 4 makes the first argument the message")

    def test_lines_removed_inside_a_call_recheck_the_call(self) -> None:
        path = "app/src/test/A.kt"
        self.write(path, 'fun t() {\n    assertTrue(\n        "the restart keeps " +\n        "$case",\n'
                         '        ok)\n}\nfun u() {\n    // the next call stays unchanged\n'
                         '    assertTrue("$case", ok)\n\n}\nval other = 1\n')
        self.commit_all("test file")
        removed = self.read(path).replace('        "the restart keeps " +\n', "")
        self.write(path, removed.replace("    // the next call stays unchanged\n", "").replace("ok)\n\n}", "ok)\n}"))
        expected = (1, [f"{path}:3: [3.4]"], "")
        self.assertEqual(expected, self.run_check(), "only the call with lines removed inside it is read again")
        self.write(path, self.read(path).replace("val other = 1", "val other = 2"))
        self.assertEqual(expected, self.run_check(), "the same with a changed line elsewhere")

    def test_a_removed_import_of_another_class_rechecks_its_calls(self) -> None:
        path = "app/src/test/p/UseIt.kt"
        self.write(path, 'package p\nimport helper.ForeignChecks as Checks\nfun t() {\n'
                         '    Checks.assertTrue("$case", ok)\n}\n')
        self.commit_all("test file")
        self.write(path, self.read(path).replace("import helper.ForeignChecks as Checks\n", ""))
        self.assertEqual([f"{path}:3"], self.checked_by_hand(), "the call through the unresolved Checks is listed")

    def test_staged_changes_that_differ_from_the_working_file_are_an_error(self) -> None:
        self.assertEqual(set(), Repo(self.repo, "HEAD").partly_staged(), "a clean repository has no such file")
        self.append_line("docs/a.md", f"See {SIGN}3.")
        self.git("add", "docs/a.md")
        self.git("restore", "--source=HEAD", "--", "docs/a.md")
        code, findings, stderr_text = self.run_check()
        self.assertEqual((2, [], True), (code, findings, "docs/a.md has staged changes" in stderr_text),
                         "the commit takes a section sign that the working file lacks, so the run is an error")
        self.append_line("docs/a.md", "A changed line.")
        self.assertIn("docs/a.md has staged changes", self.run_check()[2], "a file staged in part is an error too")
        self.assertEqual("", self.run_check("changelog.txt")[2], "a run with named files reads only these files")
        self.write("data.bin", "staged\n")
        self.git("add", "data.bin")
        self.write("data.bin", "changed again\n")
        self.git("add", "docs/a.md")
        code, _, stderr_text = self.run_check()
        self.assertEqual((0, ""), (code, stderr_text),
                         "the file staged again passes, and the script reads no file of another kind")

    def test_a_change_above_an_unchanged_call_that_changes_the_call_rechecks_it(self) -> None:
        path = "app/src/test/A.kt"
        call = '        assertTrue("$case", ok)\n'
        cases = {
            "a removed qualifier line": (f"    Checks.\n{call}", call, 3),
            "removed comment delimiters": (f"    /*\n{call}    */\n", call, 3),
            "comment delimiters changed to line comments": (f"    /* start\n{call}    end */\n",
                                                            f"    // start\n{call}    // end\n", 4),
        }
        for case, (committed_body, working_body, line) in cases.items():
            self.write(path, f"import org.junit.Assert.assertTrue\nfun t() {{\n{committed_body}}}\n")
            self.commit_all(case)
            self.write(path, f"import org.junit.Assert.assertTrue\nfun t() {{\n{working_body}}}\n")
            self.assertEqual((1, [f"{path}:{line}: [3.4]"], ""), self.run_check(),
                             f"{case}: the call that now reaches JUnit 4 is read")

    def test_lines_added_and_removed_above_a_call_keep_its_place(self) -> None:
        path = "app/src/test/A.kt"
        self.write(path, 'fun t() {\n    val first = 1\n    val second = 2\n    assertTrue("$case", ok)\n}\n')
        self.commit_all("test file")
        self.write(path, 'val added = 0\nfun t() {\n    assertTrue("$case", ok)\n}\n')
        self.assertEqual((0, [], ""), self.run_check(), "the unchanged call is not read again")

    def test_carriage_returns_of_the_working_file_do_not_change_a_call(self) -> None:
        path = "app/src/test/A.kt"
        committed = 'fun t() {\n    assertTrue(\n        "$case",\n        ok)\n}\nval other = 1\n'
        self.write(path, committed)
        self.commit_all("test file")
        self.git("config", "core.autocrlf", "true")
        working = committed.replace("other = 1", "other = 2").replace("\n", "\r\n")
        (self.repo / path).write_bytes(working.encode("utf-8"))
        self.assertEqual((0, [], ""), self.run_check(), "git compares the lines without CR, so the call is unchanged")

    def test_a_failed_git_read_is_an_error(self) -> None:
        self.append_line("docs/a.md", "x" * 10)
        self.remove_object("HEAD:docs/a.md")
        code, _, stderr_text = self.run_check("docs/a.md")
        self.assertEqual((2, True), (code, "git diff" in stderr_text),
                         "git diff cannot compare the index with the working file, which is an error line")
        self.git("add", "docs/a.md")  # git diff of the index and the working file then needs no committed text
        code, _, stderr_text = self.run_check("docs/a.md")
        self.assertEqual((2, True), (code, "git show" in stderr_text),
                         "the unreadable committed version is an error, not a new file")

    def test_a_failed_git_read_of_a_deleted_changelog_is_an_error(self) -> None:
        self.remove_object("HEAD:changelog.txt")
        (self.repo / "changelog.txt").unlink()
        code, _, stderr_text = self.run_check("changelog.txt")
        self.assertEqual((2, True), (code, "git show" in stderr_text), "an error line, not a traceback")

    def skip_on_a_file_system_that_tells_case_apart(self) -> None:
        if not (self.repo / "DOCS" / "A.md").is_file():
            self.skipTest("the file system tells names apart by their case")

    def test_a_named_file_in_another_case_is_checked_under_its_git_name(self) -> None:
        self.skip_on_a_file_system_that_tells_case_apart()
        self.append_line("docs/a.md", f"See {SIGN}3.")
        self.git("add", "docs/a.md")
        self.git("restore", "--source=HEAD", "--", "docs/a.md")
        code, findings, stderr_text = self.run_check("DOCS/A.md")
        self.assertEqual((2, [], True), (code, findings, "docs/a.md has staged changes" in stderr_text),
                         "the staged section sign is an error, as with the name docs/a.md")
        self.git("restore", "--staged", "--", "docs/a.md")
        self.write("changelog.txt", self.read("changelog.txt").replace("One bullet.", "One bullet, edited."))
        expected = self.run_check("changelog.txt")
        self.assertIn("changelog.txt:7: [5.1.4]", expected[1], "the edited committed entry is reported")
        self.assertEqual(expected, self.run_check("CHANGELOG.txt"),
                         "the changelog named in capitals gets the findings of changelog.txt")

    def test_a_deleted_file_named_in_another_case_is_checked_under_its_git_name(self) -> None:
        self.skip_on_a_file_system_that_tells_case_apart()
        (self.repo / "changelog.txt").unlink()
        self.assertEqual((1, ["changelog.txt:1: [5.1.4]"], ""), self.run_check("CHANGELOG.txt"),
                         "the deletion of the changelog named in capitals is reported")
        settings_read = [call for call in self.git_calls("changelog.txt") if "core.ignorecase" in call]
        self.assertEqual([], settings_read, "a name that git lists needs no git setting")
        self.git("config", "core.ignorecase", "false")
        code, _, stderr_text = self.run_check("CHANGELOG.txt")
        self.assertEqual((2, True), (code, "CHANGELOG.txt is not a file" in stderr_text),
                         "with core.ignorecase false, the given name stays")

    def test_a_named_file_that_can_name_two_index_entries_is_an_error(self) -> None:
        self.skip_on_a_file_system_that_tells_case_apart()
        blob = self.git("rev-parse", "HEAD:docs/a.md").strip()
        self.git("update-index", "--add", "--cacheinfo", f"100644,{blob},docs/A.md")
        expected_error = "docs/a.MD can name docs/A.md or docs/a.md of the index"
        code, findings, stderr_text = self.run_check("docs/a.MD")
        self.assertEqual((2, [], True), (code, findings, expected_error in stderr_text),
                         "the run names both index entries and checks neither")
        (self.repo / "docs" / "a.md").unlink()
        code, _, stderr_text = self.run_check("docs/a.MD")
        self.assertEqual((2, True), (code, expected_error in stderr_text), "the same for a deleted file")

    def test_a_missing_named_file_keeps_its_name_beside_a_name_that_folds_alike(self) -> None:
        self.skip_on_a_file_system_that_tells_case_apart()
        self.write("docs/strasse.md", "Text.\n")
        self.write("docs/K.md", "Text.\n")
        self.commit_all("files")
        kelvin = f"docs/{chr(0x212A)}.md"  # KELVIN SIGN, which lower() and casefold() turn into k
        cases = {"docs/straße.md": "casefold() joins it with docs/strasse.md",
                 kelvin: "lower() and casefold() join it with docs/K.md"}
        for name, reason in cases.items():
            if (self.repo / name).exists():
                continue  # a file system that joins the names too, so the name leads to the file
            code, findings, stderr_text = self.run_check(name)
            self.assertEqual((2, [], True), (code, findings, f"{name} is not a file" in stderr_text),
                             f"{name}: the missing file keeps its name, although {reason}")

    def test_a_named_file_keeps_a_name_that_git_lists(self) -> None:
        self.skip_on_a_file_system_that_tells_case_apart()
        blob = self.git("rev-parse", "HEAD:docs/a.md").strip()
        self.git("update-index", "--add", "--cacheinfo", f"100644,{blob},docs/A.md")
        self.append_line("docs/a.md", f"See {SIGN}3.")
        self.assertEqual((1, ["docs/a.md:31: [1.9]"], ""), self.run_check("docs/a.md"),
                         "the index lists the file as docs/A.md too, and the named docs/a.md is checked")

    def test_a_file_whose_name_folds_like_a_listed_name_keeps_its_name(self) -> None:
        self.write(".gitignore", "straße.md\n")
        self.write("docs/strasse.md", "Text.\n")
        self.commit_all("files")
        self.write("docs/straße.md", f"See {SIGN}3.\n")
        self.assertEqual((1, ["docs/straße.md:1: [1.9]"], ""), self.run_check("docs/straße.md"),
                         "the ignored docs/straße.md is another file than docs/strasse.md, whose name folds alike")

    def test_a_failed_git_list_of_the_named_files_is_an_error(self) -> None:
        self.write("docs/b.md", f"See {SIGN}2.\n")
        with mock.patch.object(Repo, "git_names", side_effect=CheckError("git ls-files failed")):
            code, findings, stderr_text = self.run_check("docs/b.md")
        self.assertEqual((2, ["docs/b.md:1: [1.9]"], True), (code, findings, "git ls-files failed" in stderr_text),
                         "an error line, and the named file is checked under the given name")

    def test_a_file_named_twice_is_checked_once(self) -> None:
        self.write("docs/b.md", f"See {SIGN}2.\n")
        self.assertEqual((1, ["docs/b.md:1: [1.9]"], ""), self.run_check("docs/b.md", "docs/b.md"),
                         "the file is checked once")

    def test_each_committed_version_is_read_once(self) -> None:
        self.append_line("docs/a.md", "x" * 80)
        self.assertEqual(["docs/a.md"], self.shown_paths(), "one git show for the base version of docs/a.md")
        (self.repo / "changelog.txt").unlink()
        self.assertEqual(["changelog.txt", "docs/a.md"], self.shown_paths(),
                         "the deleted changelog is read once, because the base is HEAD")

    def test_only_the_changelog_is_read_at_head(self) -> None:
        self.git("checkout", "-q", "-b", "work")
        self.write("docs/b.md", "Branch text.\n")
        self.commit_all("work")
        self.append_line("docs/a.md", "x" * 10)
        self.assertEqual(["docs/a.md"], self.shown_paths("--base", "main"),
                         "docs/a.md is read at the base only, and docs/b.md is new since the base")

    def test_every_line_of_a_test_file_is_checked_without_its_committed_version(self) -> None:
        path = "app/src/test/A.kt"
        self.write(path, 'fun t() {\n    assertTrue("$case", ok)\n}\n')
        self.commit_all("test file")
        self.assertEqual([], self.shown_paths("--all", path), "the committed version decides nothing")
        self.assertEqual((1, [f"{path}:2: [3.4]"], ""), self.run_check("--all", path), "every call is checked")

    def test_a_text_conversion_driver_does_not_move_the_changed_lines(self) -> None:
        self.write(".gitattributes", "*.md diff=tail\n")
        self.write("docs/c.md", numbered_lines(40))
        self.commit_all("attributes")
        self.git("config", "diff.tail.textconv", "tail -n 10")
        self.write("docs/c.md", numbered_lines(40).replace("Line 31.", f"See {SIGN}3."))
        self.assertEqual((1, ["docs/c.md:31: [1.9]"], ""), self.run_check(),
                         "the line numbers come from the file, not from its converted text")

    def test_changes_close_together_keep_their_own_lines(self) -> None:
        self.write("docs/c.md", numbered_lines(30).replace("Line 12.", f"See {SIGN}3."))
        self.commit_all("sign")
        self.git("config", "diff.interHunkContext", "5")
        self.write("docs/c.md", self.read("docs/c.md").replace("Line 10.", "Line 1x.").replace("Line 14.", "Line 1y."))
        self.assertEqual((0, [], ""), self.run_check(), "the unchanged line 12 between the changes is not checked")

    def test_a_file_with_a_nul_byte_keeps_its_changed_lines(self) -> None:
        self.write("docs/c.md", "Text.\n")
        self.commit_all("text")
        self.write("docs/c.md", f"Text.\0\nSee {SIGN}3.\n")
        self.assertEqual((1, ["docs/c.md:2: [1.9]"], ""), self.run_check(),
                         "git compares the file as text, not as a binary file")

    def test_a_named_file_is_not_a_pattern(self) -> None:
        self.write("docs/d.md", "Plain text.\n")
        self.write("docs/[draft].md", f"See {SIGN}1.\n")
        self.commit_all("two files")
        self.write("docs/d.md", "Other text.\n")
        self.assertEqual((0, [], ""), self.run_check("docs/[draft].md"),
                         "the changes of docs/d.md are not changes of docs/[draft].md")

    def test_a_carriage_return_inside_a_committed_line_keeps_the_entry_unchanged(self) -> None:
        header = ["CHANGELOG", "=========", ""]
        old = entry("Old intro.", "One\rbullet.", title="Docs: old", date="2026-01-02")
        self.write("changelog.txt", "\n".join([*header, *old]))
        self.commit_all("carriage return")
        self.write("changelog.txt", "\n".join([*header, *entry("New intro.", "New bullet."), *old]))
        self.assertEqual((0, [], ""), self.run_check(), "the committed entry equals its working copy")

    def test_a_file_over_the_size_limit_is_an_error(self) -> None:
        self.write("docs/b.md", f"{'x' * 200}\n")
        self.write("docs/a.md", "Short.\n")
        with mock.patch.object(cw, "MAX_FILE_BYTES", 100), \
                mock.patch.object(writing_checks.git_repo, "MAX_FILE_BYTES", 100):
            new_file = self.run_check("docs/b.md")
            committed_file = self.run_check("docs/a.md")
        self.assertEqual((2, True), (new_file[0], "over the limit of 100 bytes" in new_file[2]),
                         "a working file over the limit is not read")
        self.assertEqual((2, True), (committed_file[0], "over the limit of 100 bytes" in committed_file[2]),
                         "a committed version over the limit is not read")

    def test_the_temporary_repository_is_removed(self) -> None:
        other = GitTest("test_a_file_named_twice_is_checked_once")
        self.addCleanup(other.doCleanups)  # also after a failure below
        other.setUp()
        other.doCleanups()
        self.assertFalse(other.home.exists(), "the folder and its read-only git objects are removed")

    def test_git_files_and_variables_of_the_host_stay_out_of_the_repository(self) -> None:
        other_index = self.home / "other-index"
        host_attributes = self.home / "host" / "git" / "attributes"
        host_attributes.parent.mkdir(parents=True)
        host_attributes.write_text("*.md -diff\n", encoding="utf-8")
        host = {"GIT_INDEX_FILE": str(other_index), "XDG_CONFIG_HOME": str(self.home / "host")}
        with mock.patch.dict(os.environ, host):
            other = GitTest("test_a_file_named_twice_is_checked_once")
            self.addCleanup(other.doCleanups)  # also after a failure below
            other.setUp()
            attribute = other.git("check-attr", "diff", "docs/a.md").strip()
            other.doCleanups()
        self.assertEqual((False, "docs/a.md: diff: unspecified"), (other_index.exists(), attribute),
                         "git add writes the index of the temporary repository, and the host attributes are not read")

    def run_with_a_failed_git_call(self, test_name: str, command: str, occurrence: int) -> tuple[int, list[str]]:
        """The number of errors of a run of the GitTest test_name in which call number occurrence
        of the git command fails, and the temporary folders left after the run."""
        directories: list[tempfile.TemporaryDirectory[str]] = []
        commands: list[str] = []
        real_git = GitTest.git

        class RecordingDirectory(tempfile.TemporaryDirectory[str]):
            # The list keeps each folder object alive, so no finalizer removes a forgotten folder.
            def __init__(self) -> None:
                super().__init__()
                directories.append(self)

        def failing_git(test: GitTest, *args: str) -> str:
            commands.append(args[0])
            if args[0] == command and commands.count(command) == occurrence:
                raise subprocess.CalledProcessError(1, ["git", *args])
            return real_git(test, *args)

        with mock.patch.object(tempfile, "TemporaryDirectory", RecordingDirectory), \
                mock.patch.object(GitTest, "git", failing_git):
            result = unittest.TextTestRunner(stream=io.StringIO()).run(GitTest(test_name))
        left = [directory.name for directory in directories if Path(directory.name).exists()]
        for directory in directories:
            directory.cleanup()
        return len(result.errors), left

    def test_nested_repositories_are_removed_after_a_failure(self) -> None:
        self.assertEqual((1, []), self.run_with_a_failed_git_call(
            "test_git_files_and_variables_of_the_host_stay_out_of_the_repository", "check-attr", 1),
            "the failed check-attr is the one error, and it leaves no folder")
        self.assertEqual((1, []), self.run_with_a_failed_git_call(
            "test_the_temporary_repository_is_removed", "commit", 2),
            "the failed commit of the nested setup is the one error, and it leaves no folder")

    def test_one_git_call_reads_the_filters_of_all_files(self) -> None:
        self.append_line("docs/a.md", "x" * 10)
        self.write("docs/b.md", "Text.\n")
        self.add_entry_on_top(entry("Intro.", "A new bullet."))
        commands = [call[0] for call in self.git_calls()]
        self.assertEqual(1, commands.count("check-attr"), "one check-attr call for the three changed files")

    def test_a_file_that_a_git_filter_converts_is_an_error(self) -> None:
        self.write(".gitattributes", "*.md filter=last\n*.kt filter=last\n*.txt filter=unconfigured\n")
        self.git("config", "filter.last.clean", "tail -n 10")
        self.write("docs/c.md", numbered_lines(40))
        self.write("app/src/main/C.kt", numbered_lines(40))
        self.commit_all("filters")
        self.write("docs/c.md", numbered_lines(40).replace("Line 31.", f"See {SIGN}3."))
        self.write("app/src/main/C.kt", numbered_lines(40).replace("Line 31.", f"// see {SIGN}3"))
        for args in (["docs/c.md"], ["--all", "docs/c.md"], ["app/src/main/C.kt"]):
            code, findings, stderr_text = self.run_check(*args)
            self.assertEqual((2, [], True), (code, findings, 'with the filter driver "last"' in stderr_text),
                             f"{args}: a comparison with the filtered committed version is an error")
        self.assertEqual((1, ["app/src/main/C.kt:31: [1.9]"], ""), self.run_check("--all", "app/src/main/C.kt"),
                         "every line of a filtered code file is checked, without its committed version")
        self.add_entry_on_top(entry("Intro.", "A new bullet."))
        self.assertEqual((0, [], ""), self.run_check("changelog.txt"),
                         "a filter attribute without a configured command converts nothing")

    def test_a_filter_driver_named_like_an_attribute_state_is_an_error(self) -> None:
        self.append_line("docs/a.md", "x" * 10)
        for driver in ("set", "unset", "unspecified"):
            self.git("config", f"filter.{driver}.clean", "tail -n 10")
            self.write(".gitattributes", f"*.md filter={driver}\n")
            code, _, stderr_text = self.run_check("docs/a.md")
            self.assertEqual((2, True), (code, f'with the filter driver "{driver}"' in stderr_text),
                             f"filter={driver}: the configured driver of that name gives an error")
        self.write(".gitattributes", "*.md text\n")
        self.assertEqual((0, [], ""), self.run_check("docs/a.md"),
                         "the configured drivers of these names convert nothing that no filter attribute selects")

    def test_a_line_separator_inside_a_line_starts_no_hunk(self) -> None:
        self.write("docs/c.md", numbered_lines(10).replace("Line 2.", f"See {SIGN}3."))
        self.commit_all("sign")
        separator = chr(0x2028)  # LINE SEPARATOR, at which str.splitlines() splits
        changed = self.read("docs/c.md").replace("Line 4.", f"Line 4{separator}@@ -0,0 +2 @@")
        self.write("docs/c.md", changed.replace("Line 5.", f"See {SIGN}5."))
        self.assertEqual((1, ["docs/c.md:5: [1.9]"], ""), self.run_check(),
                         "the text after the separator is part of line 4, so the next added line is line 5")

    def test_context_lines_of_git_diff_opts_are_not_changed_lines(self) -> None:
        self.write("docs/c.md", numbered_lines(10).replace("Line 2.", f"See {SIGN}3."))
        self.commit_all("sign")
        self.write("docs/c.md", self.read("docs/c.md").replace("Line 4.", "Line 4x."))
        with mock.patch.dict(os.environ, {"GIT_DIFF_OPTS": "--unified=3"}):
            self.assertEqual((0, [], ""), self.run_check(), "the unchanged line 2 in the context is not checked")

    def test_an_empty_context_line_keeps_the_changed_line_numbers(self) -> None:
        self.write("docs/c.md", numbered_lines(10).replace("Line 3.", ""))
        self.commit_all("blank line")
        # git joins the changes of lines 2 and 4 in one hunk and writes the blank line 3 as an empty line.
        self.git("config", "diff.interHunkContext", "3")
        self.git("config", "diff.suppressBlankEmpty", "true")
        changed = self.read("docs/c.md").replace("Line 2.", "Line 2x.")
        self.write("docs/c.md", changed.replace("Line 4.", f"See {SIGN}4."))
        self.assertEqual((1, ["docs/c.md:4: [1.9]"], ""), self.run_check(), "the sign is reported at its line 4")

    def test_a_file_that_denies_access_is_an_error(self) -> None:
        self.append_line("docs/a.md", "x" * 10)
        self.write("docs/b.md", f"See {SIGN}2.\n")
        (self.repo / "changelog.txt").unlink()
        real_is_file = Path.is_file

        def denied(path: Path) -> bool:
            if path.name in ("a.md", "changelog.txt"):
                raise PermissionError(13, "Access is denied", str(path))
            return real_is_file(path)

        with mock.patch.object(Path, "is_file", denied):
            code, findings, stderr_text = self.run_check()
        self.assertEqual((2, ["docs/b.md:1: [1.9]"], 2), (code, findings, stderr_text.count("Access is denied")),
                         "both files give an error line, and the other files are still checked")

    @unittest.skipUnless(os.name == "nt", "only Windows has drive letters")
    def test_the_error_for_another_drive_keeps_its_cause(self) -> None:
        drive = "Y:" if self.repo.drive.upper() == "Z:" else "Z:"
        with self.assertRaises(CheckError) as raised:
            Repo(self.repo, "HEAD").relative(Path(f"{drive}\\notes.md"))
        self.assertIsInstance(raised.exception.__cause__, ValueError, "the error names the ValueError as its cause")

    def test_the_script_of_this_checkout_runs_in_another_repository(self) -> None:
        self.write("docs/b.md", f"See {SIGN}2.\n")
        script = ROOT / "scripts" / "check_writing.py"
        done = subprocess.run([sys.executable, str(script)], cwd=self.repo, capture_output=True,
                              text=True, encoding="utf-8", check=False, timeout=PROCESS_TIMEOUT_SECONDS)
        self.assertEqual((1, True), (done.returncode, "docs/b.md:1: [1.9]" in done.stdout),
                         "a repository without the script is checked with this copy")

    def test_every_line_of_a_named_file_is_checked(self) -> None:
        code, findings, _ = self.run_check("--all", "changelog.txt")
        self.assertEqual((1, ["changelog.txt:6: [5.4.3]"]), (code, findings),
                         "the committed entry is checked, and its date is not reported")


if __name__ == "__main__":
    unittest.main()
