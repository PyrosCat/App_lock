"""The check of the JUnit 4 assertion messages of Kotlin test files (rule 3.4).

The message is the first argument. A call counts as JUnit 4 when the file's imports, aliases
included, or its type aliases resolve its class to org.junit.Assert, junit.framework.Assert, or
junit.framework.TestCase, which the JUnit 4 jar holds. As in Kotlin, an import wins over a type
alias of the same name. Assert or TestCase without an import counts too, and so does a call without
a class unless the file imports its name one by one from elsewhere. The script does not read star
imports, so a function that a star import of another class provides counts as JUnit 4 too.

The report lists a call to check by hand, not as a finding, when its message is any other
expression, such as a variable, a call, or a concatenation, whose value the script does not read. It
also lists a call whose argument count fits no JUnit 4 form, and a call whose line continues with an
annotation or a label that the script cannot read. The script reads only the type aliases whose
target is a class name, so it also lists a call through another type alias, such as one with type
parameters, and a call through a capitalized name that the file does not resolve, such as a class or
a type alias of another file. A call of a function name that the file imports from a JUnit 4 class
and from another class is listed too, because Kotlin chooses between them by the argument types. A
call through lower-case names, which name variables by the Kotlin conventions, is not read.

A file that uses kotlin.test, JUnit 5, or Hamcrest's MatcherAssert is listed to check by hand as a
whole, because in these libraries the argument types decide where the message is. So is a file that
renames an assertion function in an import, that gives a JUnit 4 class an import alias or a type
alias in backticks that is not a plain name, that gives such a name to a type alias that the script
cannot read, or that holds an import that the script cannot read.

A call, its trailing lambda included, is checked when one of its lines changed, or when no call of
its text starts on its line in the committed version, as after a line removed inside it or a
qualifier or a comment delimiter removed above it. It is also checked when an expression as its
message uses a name that a changed line declares with val or var, or the name of a removed
declaration, also in a template such as "$name". A declaration, a destructuring one such as
"val (message, ok)" included, continues on the next lines that are indented deeper than its first
line or that start inside one of its strings. The script does not read a declared name in backticks
that is not a plain name or that is a hard keyword, as in "val `the message`" or "val `is`", nor the
name of an extension property whose receiver type is in parentheses, as in
"val (() -> Unit).message". For a call to check by hand, the name can be in any argument, because
the script cannot tell its message. Every call is checked when a change of the imports or the type
aliases, such as a removed import, gives a call another class, or when a change of the file changes
the reason why the script reads none of its calls.
"""

import bisect
import re
from collections import Counter, defaultdict
from collections.abc import Container, Iterator, Mapping, Sequence
from collections.abc import Set as AbstractSet
from dataclasses import dataclass
from enum import Enum
from typing import NamedTuple

from writing_checks.findings import Finding
from writing_checks.git_repo import LineMap
from writing_checks.rules import Rules
from writing_checks.text import blank, plural, shortened, skip

HARD_KEYWORDS = frozenset((
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
    "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
    "try", "typealias", "typeof", "val", "var", "when", "while"))
# The keywords that the Kotlin parser does not accept in type arguments. Such a word after a
# "<" makes the "<" a comparison, as in "x<MAX, null>".
TYPE_STOP_WORDS = HARD_KEYWORDS - {"in", "super", "typeof"}
PLAIN_NAME = re.compile(r"[^\W\d]\w*")
RAW_QUOTE = '"""'
MAX_CHAR_CLOSE = 7  # offset of the closing quote of the longest character literal, a Unicode escape


def block_comment_end(text: str, i: int) -> int:
    depth, n = 0, len(text)
    while i < n:
        if text.startswith("/*", i):
            depth, i = depth + 1, i + 2
        elif text.startswith("*/", i):
            depth, i = depth - 1, i + 2
            if depth == 0:
                return i
        else:
            i += 1
    return n


def char_end(text: str, i: int) -> int:
    """Index after the character literal that starts at i, or i + 1 when none starts there."""
    # The closing quote follows one character, or a backslash and the escaped character.
    close = text.find("'", i + (3 if text.startswith("\\", i + 1) else 2))
    return close + 1 if 0 <= close - i <= MAX_CHAR_CLOSE else i + 1


def token_end(text: str, i: int) -> tuple[int, int] | None:
    """(end, kept) for the comment, string, character, or backticked name that starts at i, or
    None when none starts there. end is the index after it, and kept is the number of its
    delimiters at each end that a mask keeps. A backticked name, such as a test name, can hold
    quotes, apostrophes, and braces."""
    if text.startswith("//", i):
        end = text.find("\n", i)
        return (len(text) if end < 0 else end), 0
    if text.startswith("/*", i):
        return block_comment_end(text, i), 0
    if text[i] == '"':
        return string_end(text, i), (len(RAW_QUOTE) if text.startswith(RAW_QUOTE, i) else 1)
    if text[i] == "'":
        return char_end(text, i), 1
    if text[i] == "`":
        close = text.find("`", i + 1)
        return (close + 1 if close > 0 and text.find("\n", i, close) < 0 else i + 1), 1
    return None


def template_end(text: str, i: int) -> int:
    """Index after the "}" that closes the template "{" at i. Each comment, string, character,
    and backticked name in the template is skipped whole, so its braces and quotes do not count."""
    depth, n = 0, len(text)
    while i < n:
        token = token_end(text, i)
        if token:
            i = token[0]
            continue
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def string_end(text: str, i: int) -> int:
    """Index after the Kotlin string literal that starts at i."""
    n = len(text)
    raw = text.startswith(RAW_QUOTE, i)
    j = i + (len(RAW_QUOTE) if raw else 1)
    while j < n:
        if text.startswith("${", j):
            j = template_end(text, j + 1)
        elif text[j] == "$" and (template := SIMPLE_TEMPLATE.match(text, j)) is not None:
            j = template.end()  # a backticked name, as in "$`a b`", can hold a quote
        elif raw and text.startswith(RAW_QUOTE, j):
            j += len(RAW_QUOTE)
            while j < n and text[j] == '"':
                j += 1
            return j
        elif not raw and text[j] == "\\":
            j += 2  # the backslash and the escaped character
        elif not raw and text[j] == '"':
            return j + 1
        elif not raw and text[j] == "\n":
            return j
        else:
            j += 1
    return n


def kotlin_mask(text: str) -> str:
    """The text with each comment and the literal contents of each string, character, and
    backticked name blanked. The code in string templates stays, as do the delimiters, the
    newlines, and the offsets."""
    out = list(text)
    mask_code(text, out, 0, len(text))
    return "".join(out)


def mask_code(text: str, out: list[str], i: int, stop: int) -> None:
    """Masks the code text[i:stop] into out, as kotlin_mask describes."""
    while i < stop:
        token = token_end(text, i)
        if token is None:
            i += 1
            continue
        end, kept = token
        if text[i] == '"':
            mask_string(text, out, i, end, kept)
        elif text[i] == "`" and PLAIN_NAME.fullmatch(text, i + 1, end - 1) \
                and text[i + 1:end - 1] not in HARD_KEYWORDS:
            out[i] = out[end - 1] = " "  # `assertTrue` is the name assertTrue
        else:
            blank(out, i + kept, max(i + kept, end - kept))
        i = end


def mask_string(text: str, out: list[str], start: int, end: int, quote: int) -> None:
    """Blanks the literal parts of the string text[start:end], whose quotes are quote characters
    long, and masks the code of each template "${...}" in it."""
    i, stop = start + quote, max(start + quote, end - quote)
    while i < stop:
        if text.startswith("${", i):
            close = template_end(text, i + 1)
            blank(out, i, i + len("${"))
            mask_code(text, out, i + len("${"), close - 1)
            blank(out, close - 1, close)
            i = close
        elif quote == 1 and text[i] == "\\":
            blank(out, i, i + 2)  # the backslash and the escaped character
            i += 2
        else:
            blank(out, i, i + 1)
            i += 1


# A name in masked text: a plain name, or a backticked name. The mask keeps the backticks of a
# name that is not plain, and turns the backticks of a plain name into spaces.
NAME_TEXT = r"(?:[^\W\d]\w*|`[^`\n]*`)"
# The name of an annotation, which can be dotted or have a use-site target, as in "field:Ann".
ANNOTATION_NAME = re.compile(rf" ?{NAME_TEXT}(?:\s*[.:]\s*{NAME_TEXT})*")
# The "(" of annotation arguments is on the line of the name. Kotlin also allows a line break
# inside a comment before it, but the mask hides comments. The lenient form allows any line break
# and gives only the extent of a call that the script reads by hand.
ANNOTATION_ARGUMENTS = re.compile(r"[ \t]*\(")
LENIENT_ANNOTATION_ARGUMENTS = re.compile(r"\s*\(")
LAMBDA_LABEL = re.compile(rf" ?{NAME_TEXT} ?@")  # a label before a trailing lambda, as in "block@"
AT_AFTER_CALL = re.compile(rf"[ \t]*(?: ?{NAME_TEXT} ?)?@")  # an annotation or a label after a ")"
TRAILING_LAMBDA = re.compile(r"\s*\{")  # in masked text, where comments are blank
SAME_LINE_SPACE = re.compile(r"[ \t]*")
SPACE = re.compile(r"\s*")
NAME_BEFORE = re.compile(r"\w\s*\Z")
NAME_REACH = 40  # characters before a "<" in which NAME_BEFORE looks for a name


def annotation_end(masked: str, i: int, arguments: re.Pattern[str] = ANNOTATION_ARGUMENTS) -> int | None:
    """Index after the annotation whose "@" is at i, or None when no annotation starts there.
    An annotation is a name with optional arguments, as in @Suppress ("x"), or a list of
    annotations in brackets, as in @[Suppress("x") Mark]. arguments matches the space and the
    "(" between the name and its arguments."""
    if not masked.startswith("@", i):
        return None
    if masked.startswith("@[", i):
        return call_arguments(masked, i + 1)[1]
    name = ANNOTATION_NAME.match(masked, i + 1)
    if name is None:
        return None
    opening = arguments.match(masked, name.end())
    return call_arguments(masked, opening.end() - 1)[1] if opening else name.end()


def type_arguments_end(masked: str, i: int) -> int | None:
    """Index after the ">" that closes the type arguments opened by the "<" at i, or None when
    the "<" compares. As in the Kotlin parser, type arguments hold only names, dots, commas,
    "?", "*", nested "<>", annotations with their arguments, and function types such as
    "(Int) -> Unit". A number, a literal, an operator, a keyword such as "null", or a closing
    bracket before the ">" makes the "<" a comparison, as in "x<MAX, y)". Spaces do not
    matter."""
    angle = paren = 0
    while i < len(masked):
        c = masked[i]
        annotation = annotation_end(masked, i)
        if annotation is not None:
            i = annotation
            continue
        name = PLAIN_NAME.match(masked, i)
        if name:
            if name.group() in TYPE_STOP_WORDS:
                return None
            i = name.end()
            continue
        if masked.startswith("->", i):
            i += len("->")
            continue
        if masked.startswith(("?.", "?:", "..", "::"), i):
            return None
        if c == "<":
            angle += 1
        elif c == ">":
            angle -= 1
            if angle == 0:
                return i + 1
        elif c == "(":
            paren += 1
        elif c == ")" and paren:
            paren -= 1
        elif c not in " \t\r\n.,?*@":
            return None
        i += 1
    return None


def lenient_type_arguments_end(masked: str, i: int) -> int | None:
    """Index after the ">" that closes the "<" at i, for type arguments that type_arguments_end
    rejects, such as a backticked name. Each bracketed group is skipped whole, and "->" closes
    nothing. None when a closing bracket comes first, as after a comparison."""
    depth = 0
    while i < len(masked):
        c = masked[i]
        if masked.startswith("->", i):
            i += len("->")
            continue
        if c in "([{":
            i = call_arguments(masked, i)[1]
            continue
        if c in ")]}":
            return None
        if c == "<":
            depth += 1
        elif c == ">":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return None


def call_arguments(masked: str, open_paren: int) -> tuple[list[tuple[int, int]], int]:
    """Spans of the top-level arguments of the call whose "(" is at open_paren, and the index
    after its ")". Type arguments after a name keep their commas inside one argument, as in
    "emptyMap<kotlin.String, Int>()". A "[" or "{" at open_paren works too: the index is then
    the one after its closing bracket."""
    depth, start, i = 0, open_paren + 1, open_paren
    spans: list[tuple[int, int]] = []
    while i < len(masked):
        c = masked[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                spans.append((start, i))
                return spans, i + 1
        elif depth == 1 and c == "<" and NAME_BEFORE.search(masked, max(0, i - NAME_REACH), i):
            end = type_arguments_end(masked, i)
            if end:
                i = end
                continue
        elif depth == 1 and c == ",":
            spans.append((start, i))
            start = i + 1
        i += 1
    return spans, len(masked)


def trailing_lambda_end(masked: str, i: int, arguments: re.Pattern[str] = ANNOTATION_ARGUMENTS) -> int | None:
    """Index after the trailing lambda that follows the ")" before i, or None when no lambda
    follows. As in the Kotlin grammar, annotations and a label can come before its "{", as in
    '@Suppress("x") block@ {', and the first of them is on the line of the ")". arguments goes
    to annotation_end()."""
    j = skip(SAME_LINE_SPACE, masked, i)
    while (end := annotation_end(masked, j, arguments)) is not None:
        j = skip(SPACE, masked, end)
    label = LAMBDA_LABEL.match(masked, j)
    brace = TRAILING_LAMBDA.match(masked, label.end() if label else j)
    return call_arguments(masked, brace.end() - 1)[1] if brace else None


# The argument counts of the JUnit 4 assertion calls that have a message, which comes first.
MESSAGE_ARITY = {
    "assertEquals": (3, 4), "assertNotEquals": (3, 4), "assertArrayEquals": (3, 4),
    "assertSame": (3,), "assertNotSame": (3,), "assertTrue": (2,), "assertFalse": (2,),
    "assertNull": (2,), "assertNotNull": (2,), "assertThat": (3,), "assertThrows": (3,),
    "fail": (1,),
}
# The argument counts of the same functions without a message. A count in neither table fits
# no JUnit 4 form, so the script cannot tell the message of the call.
NO_MESSAGE_ARITY = {
    "assertEquals": (2, 3), "assertNotEquals": (2, 3), "assertArrayEquals": (2, 3),
    "assertSame": (2,), "assertNotSame": (2,), "assertTrue": (1,), "assertFalse": (1,),
    "assertNull": (1,), "assertNotNull": (1,), "assertThat": (2,), "assertThrows": (2,),
    "fail": (0,),
}
# The classes that the script reads as JUnit 4. The JUnit 4 jar holds all three, and their
# assertion functions take the message first. Without an import, their short names count too.
JUNIT4_CLASSES = ("org.junit.Assert", "junit.framework.Assert", "junit.framework.TestCase")
JUNIT4_SHORT_NAMES = ("Assert", "TestCase")
# Libraries in which the argument types decide where the message is. kotlin.test and JUnit 5
# put it before a lambda, in a lambda, or before a cause. MatcherAssert of Hamcrest has
# assertThat(reason, condition), with two arguments as in assertThat(actual, matcher). The
# script does not read their calls.
UNREAD_LIBRARIES = {"kotlin.test": "kotlin.test", "org.junit.jupiter": "JUnit 5",
                    "MatcherAssert": "Hamcrest's MatcherAssert"}
UNREAD_LIBRARY_USE = re.compile(r"(?<![\w.])(kotlin\s*\.\s*test|org\s*\.\s*junit\s*\.\s*jupiter)\s*\."
                                r"|\b(MatcherAssert)\b")
ASSERT_CALL = re.compile(rf"(?<![\w.$])((?:[^\W\d]\w*\s*\.\s*)*)({'|'.join(MESSAGE_ARITY)})\s*(?=[(<])")
OPEN_PAREN = re.compile(r"\s*\(")
# "fun" and type parameters before a name make a definition, as in "fun <T : Comparable<T>>
# assertEquals(". Type parameters can span lines but hold no brace, semicolon, "=", or quote.
DEFINITION = re.compile(r"\bfun\s+(<[^{};=\"]*>\s*)?\Z")
DEFINITION_REACH = 400  # characters before a name that the definition check reads
# A val or var declaration and its name, which can follow type parameters and the receiver type of
# an extension property, as in "val <T> List<T>.label".
DECLARATION = re.compile(r"\b(?:val|var)\s+(?:<[^=]*?>\s*)?(?:[^\W\d]\w*+(?:\s*<[^=]*?>)?\??\s*\.\s*)*(\w+)")
DESTRUCTURING = re.compile(r"\b(?:val|var)\s*\(([^()]*)\)")  # as in "val (message, ok) = pair"
CODE_NAME = re.compile(r"(?<!\w)[^\W\d]\w*")  # a name in masked code, not the end of a number such as 1L
# An import with an optional alias: the name, ".*", and the alias. It starts a line or follows
# the semicolon of another import, and a semicolon or the end of the line ends it. Kotlin allows
# spaces, comments, and a line break before a dot of the name, comments with line breaks between
# the words, and backticked names for each part of the name and for the alias.
IMPORT = re.compile(rf"(?:^|(?<=;))[ \t]*import\s+({NAME_TEXT}(?:\s*\.\s*{NAME_TEXT})*)(\s*\.\s*\*)?"
                    rf"(?:\s+as\s+({NAME_TEXT}))?[ \t]*(?=;|\r?$)", re.MULTILINE)
# The keyword of an import: "import" before a name, and after no dot, name, or backtick. A keyword
# that no match of IMPORT holds is an import that IMPORT cannot read, such as an import on the
# line of the package header.
IMPORT_KEYWORD = re.compile(r"(?<![\w.$`])import\s+(?=[^\W\d]|`)")
# A type alias, as in "typealias Checks = org.junit.Assert": its name, and then the target that
# the script reads, a class name. Kotlin allows the same spaces, comments, line breaks, and
# backticks as in an import. The script does not read other forms, such as an alias with type
# parameters, a type in parentheses, or an annotated type, although they can qualify a call.
TYPE_ALIAS = re.compile(rf"\btypealias\s+({NAME_TEXT})")
TYPE_ALIAS_TARGET = re.compile(rf"\s*=\s*({NAME_TEXT}(?:\s*\.\s*{NAME_TEXT})*)")
NUMERIC_FORMS = ("assertEquals", "assertNotEquals", "assertArrayEquals")
FLOAT_LITERAL = re.compile(r"[-+]?(\d[\d_]*\.\d+([eE][-+]?\d+)?|\d+[eE][-+]?\d+)[fF]?|"
                           r"[-+]?\d[\d_]*[fF]")
# Kotlin rejects a whole-number literal where a Double or Float is expected, so this literal,
# like a character, true, false, or null, rules out the tolerance overload.
OTHER_LITERAL = re.compile(r"'\s*'|true|false|null|[-+]?(0[xXbB][\da-fA-F_]+|\d[\d_]*)[uU]?[lL]?")
# The escapes of a Kotlin string besides the Unicode escape, and the characters that they stand for.
ESCAPES = {"t": "\t", "b": "\b", "n": "\n", "r": "\r", "'": "'", '"': '"', "\\": "\\", "$": "$"}
UNICODE_ESCAPE = re.compile(r"u([0-9a-fA-F]{4})")
SIMPLE_TEMPLATE = re.compile(r"\$(?:[^\W\d]\w*|`[^`\r\n]+`)")  # a template without braces, as in "$name" or "$`a b`"
BY_HAND = "check by hand"
MAX_SHOWN = 80  # characters of an unread message that the report shows


class LiteralKind(Enum):
    STRING = "string"
    FLOATING = "floating"
    OTHER = "other"


class MessageKind(Enum):
    WORDS = "words"  # a string literal with words
    LABEL = "label"  # a string literal with only templates, escapes, and key names
    UNKNOWN = "unknown"  # an expression whose value the script does not read
    NO_TEXT = "no text"  # true, false, or null


class Call(NamedTuple):
    """An assertion call in masked Kotlin text."""
    name: str
    start: int  # index of its qualifier or its name
    end: int  # index after its ")", or after the "}" of its trailing lambda
    arguments: list[tuple[int, int]]  # spans of the arguments that hold text
    trailing_lambda: bool
    unread: str | None  # why the script cannot tell the arguments, or None


TYPE_ARGUMENTS_UNREAD = "has type arguments that the script cannot read"
AFTER_CALL_UNREAD = "is followed on its line by an annotation or a label that the script cannot read"


class KotlinText:
    """The text of a Kotlin file, its masked text, and the line of each offset. The imports
    attribute holds the full names of each name that the file imports one by one. The names
    attribute holds the full name of each name, as imported_names() gives it, and the unread
    attribute the reason why the script reads none of the calls, as unread_reason() gives it.
    Each one is found once per text."""

    def __init__(self, text: str) -> None:
        self.text = text
        self.masked = kotlin_mask(text)
        self._starts = [0, *(match.end() for match in re.finditer("\n", text))]
        self.imports = imports_by_name(self.masked)
        self.names = imported_names(self.masked, self.imports)
        self.unread = unread_reason(self)

    def line_of(self, offset: int) -> int:
        return bisect.bisect_right(self._starts, offset)

    def any_changed(self, start: int, end: int, changed: AbstractSet[int] | None) -> bool:
        """True when a line from the one of start to the one of end changed. changed is None for
        all lines."""
        if changed is None:
            return True
        return any(number in changed for number in range(self.line_of(start), self.line_of(end) + 1))


def unread_reason(source: KotlinText) -> tuple[int, str] | None:
    """(offset, reason) for a Kotlin file whose assertion messages the script does not read, or
    None. The reasons are a use of one of the UNREAD_LIBRARIES, such as an import or a qualified
    call, an import that hides a JUnit 4 name, as import_reason() describes, a type alias that
    hides the calls through it, as type_alias_reason() describes, and an import that IMPORT
    cannot read, which can bind any name."""
    reasons = []
    use = UNREAD_LIBRARY_USE.search(source.masked)
    if use:
        library = UNREAD_LIBRARIES[re.sub(r"\s", "", use.group(1) or use.group(2))]
        reasons.append((use.start(), f"the file uses {library}"))
    imports = list(IMPORT.finditer(source.masked))
    reasons += [(match.start(), reason) for match in imports
                if (reason := import_reason(source.text, match)) is not None][:1]
    reasons += [(match.start(), reason) for match in TYPE_ALIAS.finditer(source.masked)
                if (reason := type_alias_reason(source.text, match, source.names)) is not None][:1]
    parsed = {match.start() + match.group().index("import") for match in imports}  # the keyword of each import
    unparsed = next((keyword.start() for keyword in IMPORT_KEYWORD.finditer(source.masked)
                     if keyword.start() not in parsed), None)
    if unparsed is not None:
        reasons.append((unparsed, "the file holds an import that the script cannot read"))
    return min(reasons) if reasons else None


def import_reason(text: str, match: re.Match[str]) -> str | None:
    """Why the import that IMPORT matched in the masked text stops the script from reading the
    assertion calls, or None. An alias of an assertion function renames it, and an alias of a
    JUnit 4 class in backticks that is not a plain name hides the class: the mask blanks it."""
    name, alias = import_name(match), match.group(3)
    if not alias:
        return None
    dotted = re.sub(r"\s*\.\s*", ".", text[match.start(1):match.end(1)])
    shown = f"{dotted} as {text[match.start(3):match.end(3)]}"
    if alias in MESSAGE_ARITY or name.rpartition(".")[2] in MESSAGE_ARITY:
        return f"the file imports {shown}"
    if alias.startswith("`") and name in JUNIT4_CLASSES:
        return f"the file imports {shown}, a backticked name"
    return None


def type_alias_reason(text: str, match: re.Match[str], names: Mapping[str, str | None]) -> str | None:
    """Why the type alias that TYPE_ALIAS matched in the masked text stops the script from reading
    the assertion calls, or None. As in an import, a name in backticks that is not a plain name
    hides the calls through it: the mask blanks it. So such an alias of a JUnit 4 class, or of a
    target that the script cannot read, stops the script. names gives the full name of each name,
    as imported_names() describes, so an import of the same name, which wins, leaves the calls
    readable."""
    name, shown = match.group(1), text[match.start(1):match.end(1)]
    full = names[name]
    if not name.startswith("`") or full is not None and not junit4_class(full):
        return None
    if full is None:
        return f"the file declares the type alias {shown}, a backticked name whose target the script cannot read"
    return f"the file declares the type alias {shown} of {full}, a backticked name"


def import_name(match: re.Match[str]) -> str:
    """The name of the import that IMPORT matched in masked text, without spaces around its dots."""
    return re.sub(r"\s", "", match.group(1))


def imports_by_name(masked: str) -> dict[str, list[str]]:
    """{name: full names} for the classes and functions that a Kotlin file imports one by one,
    each under its alias where it has one. One function name can come from several classes."""
    imports: defaultdict[str, list[str]] = defaultdict(list)
    for match in IMPORT.finditer(masked):
        if match.group(2):  # a star import
            continue
        name = import_name(match)
        imports[match.group(3) or name.rpartition(".")[2]].append(name)
    return dict(imports)


def imported_names(masked: str, imports: Mapping[str, Sequence[str]]) -> dict[str, str | None]:
    """{name: full name} for each name of imports, as imports_by_name() gives them, and for each
    type alias of the masked text, as type_aliases() resolves it. An import wins over a type alias
    of the same name, as in Kotlin. A function name that the file imports from one of the
    JUNIT4_CLASSES and from another class gets None: Kotlin chooses the function for each call by
    the argument types, which the script does not read."""
    imported: dict[str, str | None] = {}
    for name, full_names in imports.items():
        in_junit4 = {full.rpartition(".")[0] in JUNIT4_CLASSES for full in full_names}
        imported[name] = None if len(in_junit4) > 1 else full_names[-1]
    return {**type_aliases(masked, imported), **imported}


def type_aliases(masked: str, imports: Mapping[str, str | None]) -> dict[str, str | None]:
    """{name: full name of the target} for each type alias of a Kotlin file, or None for a target
    that the script cannot read, directly or through other type aliases. The imports resolve the
    first name of a target, as in Kotlin, and the other type aliases, above or below, resolve any
    other first name that they declare.

    Time: O(A) for A type aliases. Each alias joins one chain of aliases, in which the first name
    of each target is the next alias, unless the file imports that name, and the chain is
    resolved from its end."""
    targets: dict[str, str | None] = {}
    for match in TYPE_ALIAS.finditer(masked):
        read = TYPE_ALIAS_TARGET.match(masked, match.end())
        targets[match.group(1)] = re.sub(r"\s", "", read.group(1)) if read else None
    resolved: dict[str, str | None] = {}
    for alias in targets:
        chain: list[str] = []
        on_chain: set[str] = set()
        name = alias
        while name in targets and name not in resolved and name not in on_chain:
            chain.append(name)
            on_chain.add(name)
            name = (targets[name] or "").partition(".")[0]
            if name in imports:  # the import, not the type alias of that name, resolves the target
                break
        for link in reversed(chain):
            target = targets[link]
            first, dot, rest = (target or "").partition(".")
            # An unread target and a cycle, which Kotlin rejects, give None.
            base = resolved.get(first) if first in targets and first not in imports else imports.get(first, first)
            resolved[link] = None if target is None or base is None else f"{base}{dot}{rest}"
    return resolved


def junit4_class(name: str) -> bool:
    return name in JUNIT4_CLASSES or name in JUNIT4_SHORT_NAMES


class Owner(Enum):
    """The class of an assertion call, as far as the script can tell it."""
    JUNIT4 = "JUnit 4"
    OTHER = "other"
    UNKNOWN = "unknown"


def qualifier_name(qualifier: str) -> str:
    """The qualifier of a call that ASSERT_CALL matched, without spaces and the last dot."""
    return re.sub(r"\s", "", qualifier).rstrip(".")


def call_owner(qualifier: str, name: str, names: Mapping[str, str | None]) -> Owner:
    """The class of a call. The imports and the type aliases resolve the first name of the
    qualifier, and a qualifier that then names one of the JUNIT4_CLASSES is JUnit 4. The class is
    unknown for a type alias that the script cannot read, and for a qualifier that the file does
    not resolve and that holds a capitalized name, such as a class or a type alias of another
    file. Lower-case names name variables by the Kotlin conventions, so a call through them
    belongs to another class. An unqualified function must belong to one of the JUNIT4_CLASSES
    when the file imports it, and its class is unknown when the file imports it from one of them
    and from another class too."""
    owner = qualifier_name(qualifier)
    if not owner:
        if name not in names:
            return Owner.JUNIT4
        full = names[name]
        if full is None:
            return Owner.UNKNOWN
        return Owner.JUNIT4 if full.rpartition(".")[0] in JUNIT4_CLASSES else Owner.OTHER
    first, dot, rest = owner.partition(".")
    if first in names:
        full = names[first]
        if full is None:
            return Owner.UNKNOWN
        return Owner.JUNIT4 if junit4_class(f"{full}{dot}{rest}") else Owner.OTHER
    if junit4_class(owner):
        return Owner.JUNIT4
    return Owner.UNKNOWN if any(part[:1].isupper() for part in owner.split(".")) else Owner.OTHER


def find_call(masked: str, match: re.Match[str]) -> Call | None:
    """The call whose name ASSERT_CALL matched, or None when no call follows the name. When the
    type arguments do not parse, a lenient pass skips them, so the call still gets its whole
    extent, and the call is unread. So is a call whose line continues with an annotation or a
    label that starts no trailing lambda. A lenient pass then gives its extent, which includes
    a trailing lambda after annotation arguments on the next line."""
    name, i = match.group(2), match.end()
    unread = None
    if masked.startswith("<", i):  # type arguments, as in assertThrows<E>(...)
        strict = type_arguments_end(masked, i)
        unread = None if strict is not None else TYPE_ARGUMENTS_UNREAD
        end = strict if strict is not None else lenient_type_arguments_end(masked, i)
        if end is None:
            return Call(name, match.start(), i, [], False, unread)
        i = end
    paren = OPEN_PAREN.match(masked, i)
    if not paren:
        return None if unread is None else Call(name, match.start(), i, [], False, unread)
    spans, after = call_arguments(masked, paren.end() - 1)
    lambda_end = trailing_lambda_end(masked, after)
    end = after if lambda_end is None else lambda_end
    if lambda_end is None and AT_AFTER_CALL.match(masked, after):
        unread = AFTER_CALL_UNREAD if unread is None else unread
        lenient_end = trailing_lambda_end(masked, after, LENIENT_ANNOTATION_ARGUMENTS)
        end = after if lenient_end is None else lenient_end
    arguments = [(a, b) for a, b in spans if masked[a:b].strip()]
    return Call(name, match.start(), end, arguments, lambda_end is not None, unread)


def without_templates(s: str, raw: bool) -> str:
    """The contents s of a string literal with each template replaced by a space. In a normal
    string, each escape becomes the character that it stands for. In a raw string, a backslash
    is text."""
    out, i = [], 0
    while i < len(s):
        template = SIMPLE_TEMPLATE.match(s, i)
        if s.startswith("${", i):
            i = template_end(s, i + 1)
            out.append(" ")
        elif template:
            i = template.end()
            out.append(" ")
        elif s[i] == "\\" and not raw:
            code = UNICODE_ESCAPE.match(s, i + 1)
            out.append(chr(int(code.group(1), 16)) if code else ESCAPES.get(s[i + 1:i + 2], " "))
            i = code.end() if code else i + 2  # the backslash and the escaped character
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def label_only_text(literal: str) -> bool:
    """True when a string literal holds only templates, escapes, and key names such as "seed=".
    A word is two or more letters of any alphabet."""
    raw = literal.startswith(RAW_QUOTE)
    quote = len(RAW_QUOTE) if raw else 1
    rest = re.sub(r"\b[^\W\d]\w*=", " ", without_templates(literal[quote:-quote], raw))
    return not re.search(r"[^\W\d_]{2,}", rest)


def literal_kind(text: str, masked: str, start: int, end: int) -> LiteralKind | None:
    """The kind of an argument text[start:end] that is one literal, else None."""
    if string_literal(text, masked, start, end) is not None:
        return LiteralKind.STRING
    argument = masked[start:end].strip()
    if FLOAT_LITERAL.fullmatch(argument):
        return LiteralKind.FLOATING
    return LiteralKind.OTHER if OTHER_LITERAL.fullmatch(argument) else None


def string_literal(text: str, masked: str, start: int, end: int) -> str | None:
    """The source of the string literal that alone forms an argument, comments aside, or None."""
    begin = start + len(masked[start:end]) - len(masked[start:end].lstrip())
    if not masked.startswith('"', begin):
        return None
    close = string_end(text, begin)
    return text[begin:close] if close <= end and not masked[close:end].strip() else None


def message_value(text: str, masked: str, start: int, end: int) -> tuple[MessageKind, str]:
    """The kind of a message argument and the text to show. Only a message that is one string
    literal is read. Any other expression is unknown, even when it holds a literal with words:
    a call can drop the words that it receives."""
    argument = masked[start:end].strip()
    if argument in ("true", "false", "null"):
        return MessageKind.NO_TEXT, argument
    literal = string_literal(text, masked, start, end)
    if literal is not None:
        return (MessageKind.LABEL if label_only_text(literal) else MessageKind.WORDS), literal
    return MessageKind.UNKNOWN, shortened(" ".join(text[start:end].split()), MAX_SHOWN)


def message_span(text: str, masked: str, name: str, arguments: Sequence[tuple[int, int]],
                 trailing_lambda: bool) -> tuple[int, int, bool] | None:
    """(start, end, certain) of the message argument of a JUnit 4 call, or None when the call
    has no message. certain is False when only the argument types, which the script does not
    read, can tell a message from a tolerance."""
    start, end = arguments[0]
    if len(arguments) == 3 and not trailing_lambda and name in NUMERIC_FORMS:
        # Three arguments can also be (expected, actual, tolerance), all Double or Float. A
        # string literal can only be the message. Another literal can rule out the tolerance
        # overload: a string, or a whole number, which Kotlin does not accept as a Double.
        kind = literal_kind(text, masked, start, end)
        if kind in (LiteralKind.FLOATING, LiteralKind.OTHER):
            return None
        if kind is None:
            others = [literal_kind(text, masked, a, b) for a, b in arguments[1:]]
            return start, end, any(other in (LiteralKind.STRING, LiteralKind.OTHER) for other in others)
    return start, end, True


def assertion_calls(masked: str) -> Iterator[re.Match[str]]:
    """The matches of ASSERT_CALL in masked text that are calls, not definitions."""
    for match in ASSERT_CALL.finditer(masked):
        if not DEFINITION.search(masked, max(0, match.start() - DEFINITION_REACH), match.start()):
            yield match


def context_moved(source: KotlinText, committed: KotlinText) -> bool:
    """True when the committed imports and type aliases of a Kotlin file give a call of the
    working text another class than its working imports and type aliases do, or when the reason
    why the script reads no call changed. The classes are compared, not lines, so a removed
    import counts too, and a change that no call uses does not."""
    reasons = [None if text.unread is None else text.unread[1] for text in (source, committed)]
    if reasons[0] != reasons[1]:
        return True
    uses = {(qualifier_name(match.group(1)), match.group(2)) for match in assertion_calls(source.masked)}
    return any(call_owner(qualifier, name, source.names) is not call_owner(qualifier, name, committed.names)
               for qualifier, name in uses)


def call_text(source: KotlinText, call: Call) -> str:
    """The text of a call without carriage returns. The working file can have CRLF line ends where
    its committed version has LF, as the core.autocrlf setting of git gives them."""
    return source.text[call.start:call.end].replace("\r", "")


class CommittedCalls:
    """The texts of the assertion calls of the committed version of a Kotlin file, by the line
    where each one starts, and the committed line of each unchanged line of the working file."""

    def __init__(self, committed: KotlinText, lines: LineMap) -> None:
        self._lines = lines
        self._texts: defaultdict[int, set[str]] = defaultdict(set)
        for match in assertion_calls(committed.masked):
            call = find_call(committed.masked, match)
            if call is not None:
                self._texts[committed.line_of(call.start)].add(call_text(committed, call))

    def holds(self, source: KotlinText, call: Call) -> bool:
        """True when a call of the same text starts on the committed line of the line where the
        call of the working text source starts. call_finding() asks only for a call whose lines
        did not change. A change above the call, such as a removed qualifier or comment delimiter,
        leaves another call or none on that line."""
        line = self._lines.committed_line(source.line_of(call.start))
        return call_text(source, call) in self._texts.get(line, set())


@dataclass
class OpenDeclaration:
    """A val or var declaration that continues on the next lines: the indent of its first line,
    its names, and whether a change lies in its lines."""
    indent: int
    names: list[str]
    changed: bool = False


def declared_names(source: KotlinText, changed: AbstractSet[int] | None, removals: Container[int]) -> set[str]:
    """The names that the changed lines declare with val or var. A declaration continues on the
    next lines while they are indented deeper than its first line, as a value on the next line
    is, so a changed line there, or lines removed between two of its lines, count for it too. A
    line that starts inside a string literal continues the declaration whatever its indent, as a
    line of a raw string can start at the margin. removals holds the places of removed lines, as
    LineChanges gives them. None for changed stands for all lines, which need no names: every
    call is then checked.

    Time: O(N) for N lines. A stack holds the declarations that continue. Each one is pushed and
    popped once, a change marks the innermost one, and a popped declaration passes its mark on to
    the declaration whose lines hold it."""
    if changed is None:
        return set()
    names: set[str] = set()
    stack: list[OpenDeclaration] = []
    in_strings = {line for start, end in string_spans(source.text)
                  for line in range(source.line_of(start) + 1, source.line_of(end - 1) + 1)}
    for number, (line, masked_line) in enumerate(zip(source.text.split("\n"), source.masked.split("\n")), 1):
        indent = skip(SAME_LINE_SPACE, line, 0)  # an empty line has no indent, so it ends every declaration
        if number not in in_strings:
            close_declarations(stack, indent, names)
        if stack and (number in changed or number - 1 in removals):
            stack[-1].changed = True
        found = declaration_names(masked_line)
        if found and number in changed:
            names.update(found)
        if found:
            stack.append(OpenDeclaration(indent, found))
    close_declarations(stack, 0, names)
    return names


def close_declarations(stack: list[OpenDeclaration], indent: int, names: set[str]) -> None:
    """Pops the declarations of the stack that a line with this indent ends, as declared_names()
    describes, and adds the names of each popped declaration with a change to names."""
    while stack and stack[-1].indent >= indent:
        closed = stack.pop()
        if not closed.changed:
            continue
        names.update(closed.names)
        if stack:
            stack[-1].changed = True


def declaration_names(masked: str) -> list[str]:
    """The names that val and var declare in masked code, the names of a destructuring declaration
    included. Such a name can have a type, as in "val (message: String, ok) = pair". An extension
    property declares the name after its receiver type, as in "val Foo.message"."""
    names = DECLARATION.findall(masked)
    for match in DESTRUCTURING.finditer(masked):
        names += [part.partition(":")[0].strip() for part in match.group(1).split(",")]
    return names


def removed_declarations(masked: str, committed_masked: str) -> set[str]:
    """The names that the working text declares with val or var fewer times than its committed
    version does, as after a removed declaration. Both texts are masked."""
    return set(Counter(declaration_names(committed_masked)) - Counter(declaration_names(masked)))


def string_spans(text: str, start: int = 0, end: int | None = None) -> list[tuple[int, int]]:
    """The (start, end) offsets of the string literals of the Kotlin code text[start:end], in
    order. A string in a template of another string is part of that string."""
    stop = len(text) if end is None else end
    spans: list[tuple[int, int]] = []
    i = start
    while i < stop:
        token = token_end(text, i)
        if token is None:
            i += 1
            continue
        if text[i] == '"':
            spans.append((i, token[0]))
        i = token[0]
    return spans


def simple_templates(literal: str) -> list[str]:
    """The names of the templates without braces, as in "$name", of a Kotlin string literal and of
    the string literals in its templates, each with its own escapes. In a normal string, a
    backslash escapes the next character, so "\\$name" holds no template. In a raw string, a
    backslash is text."""
    raw = literal.startswith(RAW_QUOTE)
    names: list[str] = []
    i = len(RAW_QUOTE) if raw else 1
    while i < len(literal):
        template = SIMPLE_TEMPLATE.match(literal, i)
        if literal.startswith("${", i):
            close = template_end(literal, i + 1)
            for start, end in string_spans(literal, i + len("${"), close - 1):
                names += simple_templates(literal[start:end])
            i = close
        elif template:
            names.append(template.group()[1:].strip("`"))
            i = template.end()
        elif literal[i] == "\\" and not raw:
            i += 2
        else:
            i += 1
    return names


def message_finding(path: str, line: int, name: str, value: MessageKind, shown: str,
                    certain: bool) -> Finding | None:
    """The finding or the item to check by hand for a message of the kind value, or None."""
    if value is MessageKind.LABEL:
        return Finding(path, line, "3.4", f"the assertion message {shown} holds no words besides "
                       "templates and key names; state the expected result")
    if value is not MessageKind.UNKNOWN:
        return None
    if certain:
        return Finding(path, line, BY_HAND, f"the message is `{shown}`, whose value the script does "
                       "not read; check that it states the expected result (3.4)")
    return Finding(path, line, BY_HAND, f"{name} with three arguments: if `{shown}` is the message, "
                   "not a Double or Float, check that its value states the expected result (3.4)")


def uses_declared(source: KotlinText, start: int, end: int, declared: AbstractSet[str]) -> bool:
    """True when the code source.text[start:end] uses one of the declared names, in its code or in
    a template of one of its string literals. The mask keeps the code of a template in braces, as
    in "${name}", but blanks a template without braces, as in "$name"."""
    if not declared:
        return False
    names = [name.group() for name in CODE_NAME.finditer(source.masked, start, end)]
    for literal_start, literal_end in string_spans(source.text, start, end):
        names += simple_templates(source.text[literal_start:literal_end])
    return any(name in declared for name in names)


def call_finding(path: str, source: KotlinText, call: Call, changed: AbstractSet[int] | None,
                 declared: AbstractSet[str], committed: CommittedCalls | None = None) -> Finding | None:
    """The finding or the item to check by hand for one JUnit 4 call, or None. The call is read
    when one of its lines changed, or when committed, the calls of the committed version, holds no
    call of its text on its line. It is also read when its message uses a name that declared
    holds and the script does not read the value of the message, and a call to check by hand is
    listed when any argument uses such a name."""
    masked = source.masked
    call_changed = (source.any_changed(call.start, call.end, changed)
                    or committed is not None and not committed.holds(source, call))
    line = source.line_of(call.start)
    # The script cannot tell the message of a call that it lists by hand, so a declared name in any
    # argument lists the call too.
    listed = call_changed or any(uses_declared(source, start, end, declared) for start, end in call.arguments)
    if call.unread:
        message = f"{call.name} {call.unread}, so it cannot tell the message; check it by hand (3.4)"
        return Finding(path, line, BY_HAND, message) if listed else None
    count = len(call.arguments) + int(call.trailing_lambda)
    if count not in MESSAGE_ARITY[call.name]:
        if count in NO_MESSAGE_ARITY[call.name] or not listed:
            return None
        return Finding(path, line, BY_HAND, f"{call.name} with {plural(count, 'argument')} fits no "
                       "JUnit 4 form, so the script cannot tell its message; check it by hand (3.4)")
    span = message_span(source.text, masked, call.name, call.arguments, call.trailing_lambda) \
        if call.arguments else None
    if span is None:
        return None
    start, end, certain = span
    start += len(masked[start:end]) - len(masked[start:end].lstrip())
    value, shown = message_value(source.text, masked, start, end)
    # The words of a string literal do not depend on the names in its templates.
    if not (call_changed or value is MessageKind.UNKNOWN and uses_declared(source, start, end, declared)):
        return None
    return message_finding(path, source.line_of(start), call.name, value, shown, certain)


def unknown_class_reason(source: KotlinText, match: re.Match[str]) -> str:
    """Why the script cannot tell the class of the call that ASSERT_CALL matched, for a call whose
    class call_owner() gives as unknown."""
    qualifier, name = qualifier_name(match.group(1)), match.group(2)
    classes = [] if qualifier else list(dict.fromkeys(full.rpartition(".")[0] for full in source.imports.get(name, [])))
    if len(classes) > 1:
        return f"is imported from {' and from '.join(classes)}, and the script does not know which one the call reaches"
    return f"is called through {qualifier or name}, a class or type alias that the script cannot resolve"


def check_assertions(path: str, source: KotlinText, changed: AbstractSet[int] | None, rules: Rules,
                     committed: CommittedCalls | None = None, removals: Container[int] = frozenset(),
                     removed: AbstractSet[str] = frozenset()) -> list[Finding]:
    """Findings and calls to check by hand. changed holds the changed line numbers, None for
    all lines, and removals the places of removed lines, as LineChanges gives them. A call is
    checked when one of its lines changed, or when committed, the calls of the committed version,
    holds no call of its text on its line. It is also checked when its message uses a name that
    the changed lines declare, as declared_names() gives them, or that removed holds, the names of
    removed declarations, as call_finding() describes. A file whose messages the script does not
    read gets one item to check by hand instead."""
    if "3.4" not in rules.usable:
        return []
    if source.unread:
        offset, reason = source.unread
        return [Finding(path, source.line_of(offset), BY_HAND, f"{reason}, so the script does not "
                        "read its assertion messages; check them by hand (3.4)")]
    declared = declared_names(source, changed, removals) | removed
    found = []
    for match in assertion_calls(source.masked):
        owner = call_owner(match.group(1), match.group(2), source.names)
        if owner is Owner.OTHER:
            continue
        call = find_call(source.masked, match)
        if call is not None and owner is Owner.UNKNOWN:
            call = call._replace(unread=call.unread or unknown_class_reason(source, match))
        finding = call_finding(path, source, call, changed, declared, committed) if call is not None else None
        if finding is not None:
            found.append(finding)
    return found
