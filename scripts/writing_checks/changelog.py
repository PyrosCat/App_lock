"""The checks of changelog.txt."""

import bisect
import re
from collections import defaultdict, deque
from collections.abc import Iterable, Mapping, Sequence
from collections.abc import Set as AbstractSet
from itertools import accumulate, pairwise
from typing import NamedTuple

from writing_checks.findings import Finding
from writing_checks.rules import Rules
from writing_checks.text import (
    CLOSING_MARKS,
    OPENING_MARKS,
    blank_quotes,
    first_difference,
    shortened,
    split_sentences,
    words,
)

# A finding before it gets its path: the rule, the line, and the message.
Problem = tuple[str, int, str]

# A file name at the start of a sentence (rule 5.3.4): the file types of the repository, and
# Java source files.
FILE_NAME = re.compile(r"[\w./-]+\.(bat|csv|docx|gradle|gz|html|jar|java|json|kts?|md|pro|properties|ps1|py|sh"
                       r"|toml|txt|xml|ya?ml)")
UNDERLINE = re.compile(r"-{3,}")
TITLE_DATE = re.compile(r"\(\d{4}-\d{2}-\d{2}\)$")
NESTED_BULLET = re.compile(r"\s+([-*+]|\d+[.)])[ \t]")  # an indented bullet or numbered item
SPECULATIVE = re.compile(r"\b(would|might|if)\b", re.IGNORECASE)
THIS_CHANGE = re.compile(r"This change\b")  # the phrase of rule 5.4.3, not "This changes" or "This changelog"
CHANGELOG_HEADING_LINES = 2  # the name of the file and its underline, above the header paragraph
MAX_TITLE_SHOWN = 60  # characters of an entry title that a finding shows
EXCERPT_WORDS = 8  # words of a long sentence that a finding shows


class Unit:
    """The joined text of an introduction or of one bullet, with the line of each offset."""

    def __init__(self, parts: Sequence[tuple[int, str]]) -> None:
        texts = [line.strip() for _, line in parts]
        self.text = " ".join(texts)
        self.line = parts[0][0]
        self._numbers = [number for number, _ in parts]
        # The offset of each part in text: the parts before it, each with the space after it.
        self._starts = list(accumulate((len(part) + 1 for part in texts[:-1]), initial=0))

    def line_at(self, offset: int) -> int:
        return self._numbers[bisect.bisect_right(self._starts, offset) - 1]


class Entry(NamedTuple):
    """A changelog entry. text holds its lines from the title to the last line, for comparisons
    with committed entries."""
    title_line: int
    last_line: int
    title: str
    underline: str | None
    intro: Unit | None
    bullets: list[Unit]
    layout: list[tuple[int, str]]
    text: tuple[str, ...]


def has_underline(lines: Sequence[str], index: int) -> bool:
    return index + 1 < len(lines) and bool(UNDERLINE.fullmatch(lines[index + 1].rstrip()))


def is_title(lines: Sequence[str], index: int) -> bool:
    """A title starts at the margin, and its underline follows. After a blank line, a line that
    ends with a date in parentheses also opens an entry, so a missing underline is reported."""
    line = lines[index]
    if not line.strip() or line.startswith(("-", " ")):
        return False
    after_blank = index == 0 or not lines[index - 1].strip()
    return has_underline(lines, index) or (after_blank and bool(TITLE_DATE.search(line.rstrip())))


def parse_entry(lines: Sequence[str], start: int, end: int) -> Entry:
    """The entry whose title is lines[start], up to the next title at lines[end]."""
    underline = lines[start + 1] if has_underline(lines, start) else None
    last = end - 1
    while last > start and not lines[last].strip():
        last -= 1
    intro: list[tuple[int, str]] = []
    bullets: list[list[tuple[int, str]]] = []
    layout: list[tuple[int, str]] = []
    body = start + 1 if underline is None else start + 2  # the line after the title or its underline
    for index in range(body, last + 1):
        line, number = lines[index], index + 1
        if not line.strip():
            continue
        if line.startswith("- "):
            bullets.append([(number, line.removeprefix("- "))])
        elif NESTED_BULLET.match(line):
            layout.append((number, "nested bullet"))
        elif line.startswith("  ") and bullets:
            bullets[-1].append((number, line))
        elif not bullets:
            intro.append((number, line))
        else:
            layout.append((number, "text after the bullet list"))
    return Entry(start + 1, last + 1, lines[start], underline, Unit(intro) if intro else None,
                 [Unit(bullet) for bullet in bullets], layout, tuple(lines[start:last + 1]))


def parse_changelog(lines: Sequence[str]) -> list[Entry]:
    starts = [index for index in range(len(lines)) if is_title(lines, index)]
    return [parse_entry(lines, start, end) for start, end in pairwise([*starts, len(lines)])]


class Version(NamedTuple):
    """The lines of one version of the changelog and the entries that they hold."""
    lines: list[str]
    entries: list[Entry]


def parse_version(lines: Sequence[str]) -> Version:
    return Version(list(lines), parse_changelog(lines))


def preamble(lines: Sequence[str], entries: Sequence[Entry]) -> list[str]:
    """The lines before the first entry, without trailing blank lines."""
    text = list(lines[:entries[0].title_line - 1] if entries else lines)
    while text and not text[-1].strip():
        text.pop()
    return text


def shared_lines(first: Entry, second: Entry) -> int:
    return len(set(first.text) & set(second.text))


def match_entries(committed: Sequence[Entry], working: Sequence[Entry]) -> dict[int, int]:
    """{committed index: working index}. Entries with the same text pair first, in order. Each
    other committed entry pairs with the free working entry of the same title that shares the
    most lines with it, so a new entry with a repeated title is not taken for a changed one.

    Time: O(E + P * L) for E entries, P pairs of a committed entry that changed and a free
    working entry of the same title, and L lines per entry. Departure from the O(N log N) limit:
    the pairing compares each such pair, so P grows with the square of the entries that share a
    title. An index of the lines would not bound P, because such entries can share every line.
    Pairing by position takes linear time, but it pairs each changed committed entry with the
    lowest free working entry of its title. When a new or moved entry of that title is below the
    edited entry, the change is reported at the wrong entry. A title holds the date of its entry,
    so two entries share a title only when they have the same area and subject on the same day,
    and P stays small."""
    by_text: dict[tuple[str, ...], deque[int]] = defaultdict(deque)
    for index, entry in enumerate(working):
        by_text[entry.text].append(index)
    pairs: dict[int, int] = {}
    for index, entry in enumerate(committed):
        same_text = by_text.get(entry.text)
        if same_text:
            pairs[index] = same_text.popleft()
    paired = set(pairs.values())
    free_by_title: dict[str, list[int]] = defaultdict(list)
    for index, entry in enumerate(working):
        if index not in paired:
            free_by_title[entry.title].append(index)
    for index, entry in enumerate(committed):
        candidates = free_by_title.get(entry.title)
        if index in pairs or not candidates:
            continue
        pairs[index] = max(candidates, key=lambda w: (shared_lines(entry, working[w]), -w))
        candidates.remove(pairs[index])
    return pairs


def entry_change(committed: Entry, now: Entry, name: str) -> tuple[int, str] | None:
    """(line, message) for a working entry that differs from the committed entry that it pairs
    with, or None. name is the title that the message shows."""
    size = len(committed.text)
    if now.text[:size] == committed.text and len(now.text) > size and not now.text[size].strip():
        # Lines after a blank line below the committed entry: a new entry that the parser could
        # not see, because it has no underline and no date.
        offset = next(k for k in range(size, len(now.text)) if now.text[k].strip())
        return now.title_line + offset, (f'adds lines after the committed entry "{name}"; a new '
                                         "entry needs a title line and an underline")
    if now.text != committed.text:
        offset = min(first_difference(committed.text, now.text), len(now.text) - 1)
        return now.title_line + offset, f'changes the committed entry "{name}"'
    return None


def kept_neighbours(count: int, size: int, pairs: Mapping[int, int]) -> tuple[list[int], list[int]]:
    """(above, below) for count committed entries, size working entries, and their pairs.
    above[k] is the largest working index of the kept entries before committed entry k, or -1.
    below[k] is the smallest working index of the kept entries from committed entry k on, or
    size."""
    above = list(accumulate((pairs.get(k, -1) for k in range(count)), max, initial=-1))
    below = list(accumulate((pairs.get(k, size) for k in reversed(range(count))), min, initial=size))[::-1]
    return above, below


def replacements(count: int, size: int, pairs: Mapping[int, int]) -> set[int]:
    """Working indexes of the new entries that take the place of a removed committed entry: the
    first new entry between the kept neighbours of the removed entry. committed_entry_changes()
    reports each removal at its replacement."""
    above, below = kept_neighbours(count, size, pairs)
    return {above[k] + 1 for k in range(count) if k not in pairs and above[k] + 1 < below[k + 1]}


def committed_entry_changes(committed: Sequence[Entry], working: Sequence[Entry], pairs: Mapping[int, int],
                            last_line: int) -> list[tuple[int, str]]:
    """(line, message) pairs for the committed entries that the working changelog changes or
    removes. A changed title counts as a removed entry.

    A removed entry is reported where it was: at the first new entry between its kept
    neighbours, else at the next kept entry, else at the end of the file."""
    size = len(working)
    above, below = kept_neighbours(len(committed), size, pairs)
    changes = []
    for index, entry in enumerate(committed):
        name = shortened(entry.title, MAX_TITLE_SHOWN)
        if index in pairs:
            change = entry_change(entry, working[pairs[index]], name)
            if change:
                changes.append(change)
            continue
        before, after = above[index], below[index + 1]
        if before + 1 < after:
            line = working[before + 1].title_line
        elif after < size:
            line = working[after].title_line
        else:
            line = last_line
        changes.append((line, f'removes or retitles the committed entry "{name}"'))
    return changes


def increasing_run(values: Sequence[int]) -> set[int]:
    """Indexes of a longest increasing subsequence of distinct values, found in O(N log N) for N
    values by patience sorting."""
    tails: list[int] = []  # tails[n]: the index of the smallest last value of a run of n + 1
    tail_values: list[int] = []
    previous = [-1] * len(values)
    for index, value in enumerate(values):
        place = bisect.bisect_left(tail_values, value)
        previous[index] = tails[place - 1] if place else -1
        if place == len(tails):
            tails.append(index)
            tail_values.append(value)
        else:
            tails[place], tail_values[place] = index, value
    run = set()
    index = tails[-1] if tails else -1
    while index >= 0:
        run.add(index)
        index = previous[index]
    return run


def moved_problems(committed: Sequence[Entry], working: Sequence[Entry], pairs: Mapping[int, int]) -> list[Problem]:
    """A problem for each kept committed entry out of its committed order. The entries of the
    longest run that keeps the order stay in place, and each other kept entry counts as moved."""
    kept = sorted(pairs)
    in_order = increasing_run([pairs[k] for k in kept])
    problems: list[Problem] = []
    for position, index in enumerate(kept):
        if position not in in_order:
            name = shortened(committed[index].title, MAX_TITLE_SHOWN)
            message = f'moves the committed entry "{name}"; committed entries keep their order'
            problems.append(("5.1.4", working[pairs[index]].title_line, message))
    return problems


def preamble_problems(working: Version, base: Version | None, head: Version | None) -> list[Problem]:
    """Problems of the text before the first entry. The text stays as it is in each committed
    version. Without a version at the base, it is also one header paragraph, and an entry follows."""
    new = preamble(working.lines, working.entries)
    problems: list[Problem] = [] if base is not None else new_preamble_problems(working, new)
    for version in [base] if head is base else [base, head]:
        problem = None if version is None else preamble_change(version, new)
        if problem is not None and problem not in problems:
            problems.append(problem)
    return problems


def preamble_change(committed: Version, new: Sequence[str]) -> Problem | None:
    """The problem of the text new before the first entry when it differs from the text of the
    committed version, or None."""
    old = preamble(committed.lines, committed.entries)
    if old == new:
        return None
    index = first_difference(old, new)
    while index < len(new) and not new[index].strip():
        index += 1
    return ("5.1.1", index + 1, ("the text before the first entry changed; an entry starts with a "
                                 "title line and an underline of dashes"))


def new_preamble_problems(working: Version, new: Sequence[str]) -> list[Problem]:
    """Problems of the text new before the first entry of a changelog without a version at the
    base: the text is one header paragraph, and an entry follows."""
    end = next((k for k, line in enumerate(new) if not line.strip()), len(new))
    extra = next((k for k in range(end, len(new)) if new[k].strip()), None)
    if extra is not None:
        return [("5.1.1", extra + 1, ("text after the header paragraph is not an entry; an entry starts "
                                      "with a title line and an underline of dashes"))]
    if not working.entries:
        return [("5.1.1", 1, ("the new changelog has no entry; an entry starts with a title line and an "
                              "underline of dashes"))]
    if not new:
        return [("5.1.1", 1, "the new changelog has no header paragraph above its first entry")]
    return []


def order_problems(working: Sequence[Entry], committed: AbstractSet[int], replaced: AbstractSet[int]) -> list[Problem]:
    """A problem for each new entry below a committed entry: the newest entry goes on top.
    committed holds the working indexes of the committed entries, and replaced those of the new
    entries that take the place of a removed committed entry, whose removal is reported."""
    problems: list[Problem] = []
    above: Entry | None = None  # the nearest committed entry above
    for index, entry in enumerate(working):
        if index in committed:
            above = entry
        elif above is not None and index not in replaced:
            name = shortened(above.title, MAX_TITLE_SHOWN)
            message = f'new entry below the committed entry "{name}"; put the newest entry on top'
            problems.append(("5.1.1", entry.title_line, message))
    return problems


def opener(unit: Unit, count: int) -> tuple[str, ...]:
    return tuple(re.sub(r"[^\w-]", "", word.lower()) for word in words(unit.text)[:count])


def form_problems(entry: Entry, rules: Rules, today: str, is_new: bool) -> list[Problem]:
    """Problems of the title, the underline, the layout, and the date of an entry."""
    problems: list[Problem] = []
    title = rules.title.fullmatch(entry.title) if rules.title else None
    if not title:
        problems.append(("5.1.1", entry.title_line, f'title does not have the form "{rules.title_form}"'))
    if entry.underline is None:
        problems.append(("5.1.1", entry.title_line, "title has no underline of dashes"))
    # Trailing spaces are not seen, and has_underline() ignores them too.
    elif (dashes := len(entry.underline.rstrip())) != (characters := len(entry.title.rstrip())):
        message = f"underline of {dashes} dashes for a title of {characters} characters"
        problems.append(("5.1.1", entry.title_line + 1, message))
    sentences = len(split_sentences(entry.intro.text)) if entry.intro else 0
    if entry.intro is None:
        problems.append(("5.1.2", entry.title_line, "entry has no introduction"))
    elif rules.intro_sentences and sentences > rules.intro_sentences:
        message = f"introduction of {sentences} sentences, over {rules.intro_sentences}"
        problems.append(("5.1.2", entry.intro.line, message))
    if not entry.bullets:
        problems.append(("5.1.2", entry.title_line, "entry has no bullets"))
    problems += [("5.1.2", number, message) for number, message in entry.layout]
    if is_new and title and title.group("date") != today:
        problems.append(("5.1.3", entry.title_line, (f"entry date {title.group('date')} differs from "
                                                     f"today, {today}; the entry takes the date of its commit")))
    return problems


def sentence_problems(entry: Entry, rules: Rules) -> list[Problem]:
    """Problems of the sentences and the words of the introduction and the bullets."""
    problems: list[Problem] = []
    previous_it = False
    for unit in ([entry.intro] if entry.intro else []) + entry.bullets:
        for offset, sentence in split_sentences(unit.text):
            line = unit.line_at(offset)
            tokens = sentence.split()
            count = len(words(sentence))
            if rules.max_words and count > rules.max_words:
                start = " ".join(tokens[:EXCERPT_WORDS])
                problems.append(("5.3.5", line, f'sentence of {count} words, over {rules.max_words}: "{start} ..."'))
            # The emphasis of the first word can close after it, as in "**README.md**".
            first = tokens[0].lstrip(OPENING_MARKS).rstrip(f"{CLOSING_MARKS},;:.!?") if tokens else ""
            if FILE_NAME.fullmatch(first):
                problems.append(("5.3.4", line, f'sentence starts with the bare file name "{first}"'))
            starts_it = first == "It"
            if starts_it and previous_it:
                problems.append(("5.4.2", line, 'second sentence in a row that starts with "It"'))
            previous_it = starts_it
        for word in SPECULATIVE.finditer(blank_quotes(unit.text)):
            advice = ("keep a condition only when it states the scope"
                      if word.group(1).lower() == "if" else "state what changed")
            message = f'the word "{word.group(1)}" can speculate; {advice}'
            problems.append(("5.2.1", unit.line_at(word.start()), message))
    if entry.intro and THIS_CHANGE.match(entry.intro.text.lstrip(OPENING_MARKS)):
        problems.append(("5.4.3", entry.intro.line, 'introduction starts with "This change"'))
    return problems


def bullet_problems(entry: Entry, older: Entry | None, rules: Rules) -> list[Problem]:
    """Problems of bullets that open with the same words, or that copy a bullet of older."""
    problems: list[Problem] = []
    count = rules.opener_words or 0
    seen: dict[tuple[str, ...], int] = {}
    for bullet in entry.bullets:
        key = opener(bullet, count)
        if count and len(key) == count and key in seen:
            message = f'bullet starts with "{" ".join(key)}", as the bullet at line {seen[key]} does'
            problems.append(("5.4.1", bullet.line, message))
        seen.setdefault(key, bullet.line)
    earlier = {" ".join(b.text.split()): b.line for b in older.bullets} if older else {}
    for bullet in entry.bullets:
        copied = earlier.get(" ".join(bullet.text.split()))
        if copied:
            message = f"bullet repeats the bullet at line {copied} of the previous entry word for word"
            problems.append(("5.4.4", bullet.line, message))
    return problems


def findings_of(path: str, problems: Iterable[Problem], rules: Rules) -> list[Finding]:
    return [Finding(path, line, rule, message) for rule, line, message in problems if rule in rules.usable]


def entry_findings(path: str, entry: Entry, older: Entry | None, rules: Rules, today: str,
                   is_new: bool) -> list[Finding]:
    """Findings of one entry. older is the entry below it, and is_new marks an entry that is
    not committed."""
    problems = (form_problems(entry, rules, today, is_new) + sentence_problems(entry, rules)
                + bullet_problems(entry, older, rules))
    return findings_of(path, problems, rules)


def comparison_problems(committed: Sequence[Entry], working: Sequence[Entry], pairs: Mapping[int, int],
                        last_line: int) -> list[Problem]:
    """The problems of the committed entries of one version that the working changelog changes,
    removes, or moves."""
    changes = committed_entry_changes(committed, working, pairs, last_line)
    return [("5.1.4", line, message) for line, message in changes] + moved_problems(committed, working, pairs)


def check_changelog(path: str, working: Version, base: Version | None, head: Version | None, rules: Rules,
                    today: str, every_entry: bool = False) -> list[Finding]:
    """Findings of the changelog. base and head are its committed versions at the base and at
    HEAD, None where the commit lacks the file. An entry gets the entry checks when it is new or
    changed since the base, or always with every_entry. A new entry in the place of a removed
    entry of HEAD is its new version, so it gets no order or date finding."""
    entries = working.entries
    base_entries = base.entries if base is not None else []
    head_entries = head.entries if head is not None else []
    base_pairs = match_entries(base_entries, entries)
    head_pairs = base_pairs if head is base else match_entries(head_entries, entries)
    head_replaced = replacements(len(head_entries), len(entries), head_pairs)
    problems = preamble_problems(working, base, head)
    problems += order_problems(entries, set(base_pairs.values()) | set(head_pairs.values()), head_replaced)
    comparisons = [(base_entries, base_pairs)] if head is base else [(base_entries, base_pairs),
                                                                     (head_entries, head_pairs)]
    reported: set[Problem] = set()
    for committed, pairs in comparisons:
        for problem in comparison_problems(committed, entries, pairs, max(len(working.lines), 1)):
            if problem not in reported:
                reported.add(problem)
                problems.append(problem)
    found = findings_of(path, problems, rules)
    unchanged = {w for c, w in base_pairs.items() if base_entries[c].text == entries[w].text}
    in_head = set(head_pairs.values())
    for position, entry in enumerate(entries):
        if position in unchanged and not every_entry:
            continue
        older = entries[position + 1] if position + 1 < len(entries) else None
        is_new = position not in in_head and position not in head_replaced
        found += entry_findings(path, entry, older, rules, today, is_new)
    return found


def changelog_body(version: Version) -> list[tuple[int, str]]:
    """(number, line) pairs of the changelog without its heading, the entry titles, and their
    underlines."""
    titles = set()
    for entry in version.entries:
        titles.add(entry.title_line)
        if entry.underline is not None:
            titles.add(entry.title_line + 1)
    return [(n, line) for n, line in enumerate(version.lines, 1)
            if n not in titles and n > CHANGELOG_HEADING_LINES]
