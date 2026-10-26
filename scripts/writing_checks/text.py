"""Helpers for lines, sentences, quotes, and code blocks, and the checks of section signs and line widths."""

import bisect
import re
from collections import defaultdict
from collections.abc import Iterable, Mapping, Sequence
from collections.abc import Set as AbstractSet

from writing_checks.findings import Finding
from writing_checks.git_repo import LineMap
from writing_checks.rules import SECTION_SIGN, Rules

NEW_DOC_WIDTH = 120  # the widest wrap width of the recent documents
FENCE = re.compile(r"\s*(`{3,}|~{3,})(.*)")  # a code fence and the text after it
BACKTICK_RUN = re.compile(r"`+")
ELLIPSIS = "..."

# The wrap width estimate of wrap_width().
WRAP_PERCENTILE = 0.95
MIN_PERCENTILE_LINES = 20  # lines that a percentile of the line lengths needs
WIDE_LINE_MARGIN = 5  # characters over NEW_DOC_WIDTH that make a line wide, as an unwrapped paragraph is
MAX_WIDE_SHARE = 0.25  # share of wide lines up to which a short document still counts as wrapped
WORD_SLACK = 8  # characters by which a wrapped line can pass the percentile line: about one word


def split_lines(text: str) -> list[str]:
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return [line.removesuffix("\r") for line in lines]


def words(sentence: str) -> list[str]:
    """Words of a sentence. A label such as "(C1)" counts, a lone dash does not."""
    return [token for token in sentence.split() if re.search(r"\w", token)]


# The marks around the words of a sentence: quotes, a backtick, brackets, and the "*" of Markdown
# emphasis. The opening marks can come before its first word, and the closing marks after its end.
# Closing marks after ".", "!", or "?" can also close a quote, a code span, or emphasis inside a
# sentence, as in "Run `make.` in the shell", and a "*" can be part of a star pattern, as in "No
# Graph.* lookups".
OPENING_MARKS = "\"'`([*"
CLOSING_MARKS = "\"'`)]*"
SENTENCE_END = re.compile(r"(?<!\.\.)(?<!\be\.g)(?<!\bi\.e)(?<!\bincl)(?<!\bvs)"
                          rf"[.!?](?P<closing>[{re.escape(CLOSING_MARKS)}]*)"
                          rf"\s+(?=[^\W_]|[{re.escape(OPENING_MARKS)}])", re.IGNORECASE)
OPENING_RUN = re.compile(f"[{re.escape(OPENING_MARKS)}]*")
STAR_RUN = re.compile(r"\*+")
# A run of "*" that opens Markdown emphasis: after a space, an opening mark, or the start of the
# text, and before a character that is not a space, as in "**two.**". Any other run closes emphasis
# or is part of a star pattern.
EMPHASIS_OPENING = re.compile(rf"(?<![^\s{re.escape(OPENING_MARKS)}])\*+(?=[^\s*])")


class OpenEmphasis:
    """The Markdown emphasis that is open at a place of a text, counted from the runs of "*" in
    the current sentence. A run that EMPHASIS_OPENING matches opens one level, and any other run
    closes one, if one is open. The text has its quotes and code spans blanked, as blank_quotes()
    gives it, so a "*" in them, as in "`*.kt`", is no emphasis.

    Time: O(N) in all for N characters of the text. The places that open_before() reads do not
    decrease, so each run is read once."""

    def __init__(self, text: str) -> None:
        self._runs = [(run.start(), EMPHASIS_OPENING.match(text, run.start()) is not None)
                      for run in STAR_RUN.finditer(text)]
        self._next = 0  # the first run that the count does not hold yet
        self._open = 0

    def open_before(self, place: int) -> bool:
        """True when emphasis is open at place. Each call gives a place at or after the place of the
        call before."""
        while self._next < len(self._runs) and self._runs[self._next][0] < place:
            opens = self._runs[self._next][1]
            self._open = self._open + 1 if opens else max(0, self._open - 1)
            self._next += 1
        return self._open > 0

    def restart(self, place: int) -> None:
        """Starts the count of a sentence at place. The runs before it belong to earlier sentences."""
        while self._next < len(self._runs) and self._runs[self._next][0] < place:
            self._next += 1
        self._open = 0


def split_sentences(text: str) -> list[tuple[int, str]]:
    """(offset, sentence) pairs, split after ".", "!", or "?" and its CLOSING_MARKS, before a
    word or one of the OPENING_MARKS. A sentence can start with a lower-case name, such as a file
    name. The period of an ellipsis or of the abbreviations "e.g.", "i.e.", "incl.", and "vs."
    does not end a sentence. "etc.", in any case, ends a list. After "etc." or closing marks, a
    sentence ends only when the next word, after its opening marks, starts with a capital. A "*"
    after the end closes emphasis only when the sentence holds open emphasis, as OpenEmphasis
    tells. Any other "*" there is part of a star pattern, as in "The Graph.* API", so its period
    ends no sentence. An end inside a quote or a code span, as quoted_spans() finds them, ends a
    sentence only when the closing marks after it close the span, so the quote or the code span
    stays part of the sentence that holds it, also when a quote inside a code span closes there."""
    spans = quoted_spans(text)
    span_starts = [span_start for span_start, _ in spans]
    outside = blank_spans(text, spans)
    emphasis = OpenEmphasis(outside)
    sentences, start = [], 0
    for end in SENTENCE_END.finditer(text):
        closing = end.group("closing")
        # The last span that starts before the end, the only one that can hold it.
        span = bisect.bisect_right(span_starts, end.start()) - 1
        if span >= 0 and spans[span][1] > end.end("closing"):
            continue
        if "*" in outside[end.start("closing"):end.end("closing")] and not emphasis.open_before(end.start()):
            continue
        word = text[max(0, end.start() - len("etc")):end.start()]
        first = skip(OPENING_RUN, text, end.end())
        if (word.lower() == "etc" or closing) and not text[first:first + 1].isupper():
            continue
        sentences.append((start, text[start:end.end()].strip()))
        start = end.end()
        emphasis.restart(start)
    if text[start:].strip():
        sentences.append((start, text[start:].strip()))
    return sentences


def shortened(text: str, limit: int) -> str:
    return text if len(text) <= limit else f"{text[:limit - len(ELLIPSIS)]}{ELLIPSIS}"


def blank_quotes(text: str) -> str:
    """The text with each quoted span, as quoted_spans() finds them, blanked, its newlines aside,
    so offsets and lines stay. A quoted word such as "would" is a mention."""
    return blank_spans(text, quoted_spans(text))


def blank_spans(text: str, spans: Iterable[tuple[int, int]]) -> str:
    """The text with each (start, end) span blanked, its newlines aside."""
    out = list(text)
    for start, end in spans:
        blank(out, start, end)
    return "".join(out)


def quoted_spans(text: str) -> list[tuple[int, int]]:
    """The (start, end) offsets of the quoted spans of the text, in order. A span is a code span,
    which a run of backticks opens and a run of the same length closes, as in Markdown, or text
    in double quotes, in which a backslash escapes the next character, as in code. A span holds
    its delimiters, and a quote inside a code span is part of the code span. Outside a span, a
    backslash escapes the next character, and a delimiter without a closing one is text.

    Time: O(N log N) for N characters. A backtick run finds its closing run by bisection, and
    after a double quote without a closing one, no later double quote can close."""
    runs: defaultdict[int, list[int]] = defaultdict(list)  # {length: starts of the backtick runs of that length}
    for run in BACKTICK_RUN.finditer(text):
        runs[len(run.group())].append(run.start())
    spans: list[tuple[int, int]] = []
    i, quotes_close = 0, True
    while i < len(text):
        close: int | None = None  # the index after the closing delimiter of a span that starts at i
        if text[i] == "\\":
            i += 2
            continue
        if text[i] == "`":
            end = skip(BACKTICK_RUN, text, i)
            starts = runs[end - i]
            later = bisect.bisect_left(starts, end)
            close = starts[later] + end - i if later < len(starts) else None
            if close is None:
                i = end
                continue
        elif text[i] == '"' and quotes_close:
            close = quote_end(text, i)
            quotes_close = close is not None
        if close is None:
            i += 1
            continue
        spans.append((i, close))
        i = close
    return spans


def quote_end(text: str, i: int) -> int | None:
    """Index after the double quote that closes the one at i, or None. A backslash escapes the
    next character."""
    j = i + 1
    while j < len(text):
        if text[j] == '"':
            return j + 1
        j += 2 if text[j] == "\\" else 1
    return None


def outside_quotes(lines: Sequence[str], across_lines: bool) -> list[str]:
    """Each line with its quoted text blanked. In prose a span can continue on the next lines of
    its paragraph, which a blank line ends. In code a span ends with its line."""
    if not across_lines:
        return [blank_quotes(line) for line in lines]
    result: list[str] = []
    paragraph: list[str] = []
    for line in [*lines, ""]:  # the blank line at the end closes the last paragraph
        if line.strip():
            paragraph.append(line)
            continue
        if paragraph:
            result += blank_quotes("\n".join(paragraph)).split("\n")
            paragraph = []
        result.append(line)
    return result[:len(lines)]


def code_block_lines(lines: Sequence[str]) -> set[int]:
    """Numbers of the lines that open, hold, or close a fenced code block of a Markdown text.
    As in CommonMark, a fence is three or more backticks or tildes, and only a fence of the
    same character and at least the same length closes it. Unlike CommonMark, a fence can have
    any indent, as a code block inside a numbered list has. An indented code block counts as
    text, because the continuation lines of a list item are indented too."""
    numbers: set[int] = set()
    fence: str | None = None
    for number, line in enumerate(lines, 1):
        match = FENCE.match(line)
        if fence:
            numbers.add(number)
            if (match and match.group(1)[0] == fence[0] and len(match.group(1)) >= len(fence)
                    and not match.group(2).strip()):
                fence = None
        elif match and not (match.group(1)[0] == "`" and "`" in match.group(2)):
            fence = match.group(1)
            numbers.add(number)
    return numbers


def prose_lines(lines: Sequence[str]) -> list[tuple[int, str]]:
    """(number, line) pairs of a Markdown text without code blocks, tables, links, and rules."""
    prose, code = [], code_block_lines(lines)
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if number in code or not stripped or stripped.startswith("|") or "://" in line:
            continue
        if set(stripped) <= set("-=*_ "):
            continue
        prose.append((number, line))
    return prose


def wrap_width(lines: Sequence[str]) -> int | None:
    """The wrap width of a document, or None when its paragraphs are not wrapped.

    The lines of a wrapped paragraph end within one word of the wrap width. So the widest line
    near the 95th percentile of the lengths gives that width, and a few older, wider lines do
    not raise it. A percentile needs 20 lines, so a shorter document counts as not wrapped
    when more than a quarter of its lines are wide."""
    lengths = sorted(len(line) for _, line in prose_lines(lines))
    wide = NEW_DOC_WIDTH + WIDE_LINE_MARGIN
    if len(lengths) < MIN_PERCENTILE_LINES:
        wide_lines = sum(length > wide for length in lengths)
        return None if wide_lines > MAX_WIDE_SHARE * len(lengths) else NEW_DOC_WIDTH
    near_top = lengths[int(WRAP_PERCENTILE * (len(lengths) - 1))]
    if near_top > wide:
        return None
    return max(length for length in lengths if length <= near_top + WORD_SLACK)


def first_difference(old: Sequence[str], new: Sequence[str]) -> int:
    """Index of the first line where two line lists differ, or the shorter length."""
    return next((k for k, (a, b) in enumerate(zip(old, new)) if a != b), min(len(old), len(new)))


def blank(out: list[str], start: int, end: int) -> None:
    for k in range(start, end):
        if out[k] != "\n":
            out[k] = " "


def skip(pattern: re.Pattern[str], text: str, i: int) -> int:
    """Index after the match of pattern at i, for a pattern that can match empty text."""
    match = pattern.match(text, i)
    return match.end() if match else i


def plural(count: int, noun: str, nouns: str = "") -> str:
    return f"{count} {noun if count == 1 else nouns or f'{noun}s'}"


# ---------------------------------------------------------------------------------------------
# Checks for all text

def check_section_sign(path: str, lines: Sequence[str], changed: AbstractSet[int] | None, rules: Rules,
                       across_lines: bool) -> list[Finding]:
    if "1.9" not in rules.usable:
        return []
    # In prose, a code block is an example, as a quote is.
    code = code_block_lines(lines) if across_lines else set()
    return [Finding(path, number, "1.9", 'section sign; write "section 3.3", or name the '
                    "document and the topic")
            for number, line in enumerate(quoted_prose(lines, code, across_lines), 1)
            if (changed is None or number in changed) and SECTION_SIGN in line]


def quoted_prose(lines: Sequence[str], code: AbstractSet[int], across_lines: bool) -> list[str]:
    """Each line with its quoted text blanked, as outside_quotes() gives it. The lines of code, whose
    numbers code holds, are empty, so a quote does not run through a code block."""
    prose = ["" if number in code else line for number, line in enumerate(lines, 1)]
    return outside_quotes(prose, across_lines)


def context_changes(lines: Sequence[str], committed: Sequence[str], changed: AbstractSet[int],
                    removals: Mapping[int, int]) -> set[int]:
    """Numbers of the unchanged lines of a document that a change on another line moved into or
    out of a code block or a quote, as an added or removed fence does. Such a line can turn from an
    example into text. A line counts when its text outside quotes and code blocks, as quoted_prose()
    gives it, differs from that of its committed line, as LineMap gives it. changed and removals
    are the changes of the lines, as LineChanges gives them.

    Time: O(N log N) for N lines of both versions."""
    line_map = LineMap(changed, removals)
    committed_prose = quoted_prose(committed, code_block_lines(committed), True)
    moved: set[int] = set()
    for number, text in enumerate(quoted_prose(lines, code_block_lines(lines), True), 1):
        if number in changed:
            continue
        old = line_map.committed_line(number)
        # A committed line after the end means that the working file changed between its read and
        # the git diff.
        if old > len(committed_prose) or committed_prose[old - 1] != text:
            moved.add(number)
    return moved


def check_width(path: str, numbered_lines: Iterable[tuple[int, str]], changed: AbstractSet[int] | None,
                limit: int, reason: str) -> list[Finding]:
    return [Finding(path, number, "width", f"{len(line)} characters; the limit is {limit} "
                    f"({reason})")
            for number, line in numbered_lines
            if (changed is None or number in changed) and len(line) > limit]
