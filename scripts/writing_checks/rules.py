"""The checked rules and their limits, read from the text of WRITING_RULES.md."""

import re
from collections import Counter

SECTION_SIGN = chr(0xA7)
NUMBER_WORDS = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7,
                "eight": 8, "nine": 9, "ten": 10}
# The placeholders of the title form in rule 5.1.1, each with the text that it matches.
TITLE_PARTS = {"area": r"\S.*?", "what": r"\S.*?", "date": r"\d{4}-\d{2}-\d{2}"}


def parse_rules(text: str) -> dict[str, str]:
    """Map rule IDs such as "1.9" or "5.3.5" to the rule text, joined to one line."""
    rules: dict[str, str] = {}
    section: str | None = None
    current: str | None = None
    for line in text.replace("\r", "").split("\n"):
        heading = re.match(r"^#+\s+(\d+(?:\.\d+)*)\.?\s", line)
        if heading or line.startswith("#"):
            section, current = (heading.group(1) if heading else None), None
            continue
        item = re.match(r"^(\d+)\.\s+(.*)", line)
        if item and section:
            current = f"{section}.{item.group(1)}"
            rules[current] = item.group(2).strip()
        elif current and line.startswith("   "):
            if not line.lstrip().startswith("|"):  # a table inside a rule does not end it
                rules[current] = f"{rules[current]} {line.strip()}"
        elif line.strip():
            current = None
    return rules


# The rules that the checks cite, each with the phrases that its checks need in the rule text.
CHECKED_RULES = {
    "1.9": (SECTION_SIGN,),
    "3.4": ("assertion message",),
    "5.1.1": ("newest entry on top", "line of dashes of the same length"),
    "5.1.2": ("no nested bullets",),
    "5.1.3": ("date of the commit",),
    "5.1.4": ("while it is not committed",),
    "5.2.1": ("speculate",),
    "5.3.4": ("bare file name",),
    "5.3.5": ("words or fewer",),
    "5.4.1": ("start with the same",),
    "5.4.2": ('start with "It"',),
    "5.4.3": ('"This change"',),
    "5.4.4": ("word for word",),
}


class Rules:
    """The texts of the checked rules, the limits that they give, and the checks they allow."""

    def __init__(self, text: str) -> None:
        self.text = parse_rules(text)
        self.errors: list[str] = []
        self.usable: set[str] = set()
        self.title_form: str | None = None
        for rule, phrases in CHECKED_RULES.items():
            body = self.text.get(rule)
            missing = [phrase for phrase in phrases if phrase not in (body or "")]
            if body is None:
                self.errors.append(f"WRITING_RULES.md has no rule {rule}, so its check is skipped")
            elif missing:
                self.errors.append(f"rule {rule} of WRITING_RULES.md no longer contains "
                                   f"'{missing[0]}', so its check is skipped")
            else:
                self.usable.add(rule)
        self.intro_sentences = self._number("5.1.2", r"introduction of one or (\w+)")
        self.max_words = self._number("5.3.5", r"to (\w+) words or fewer")
        self.opener_words = self._number("5.4.1", r"start with the same (\w+) words")
        self.title = self._title_pattern()

    def _number(self, rule: str, pattern: str) -> int | None:
        """The number that the rule text gives in the group of pattern, in digits or as a word."""
        match = re.search(pattern, self.text.get(rule, ""))
        word = match.group(1) if match else ""
        value = NUMBER_WORDS.get(word.lower()) or (int(word) if word.isdecimal() else None)
        if value is None and rule in self.usable:
            self.errors.append(f"rule {rule} of WRITING_RULES.md gives no number for its check")
            self.usable.discard(rule)
        return value

    def _title_pattern(self) -> re.Pattern[str] | None:
        match = re.search(r"`([^`]*<date>[^`]*)`", self.text.get("5.1.1", ""))
        if not match:
            if "5.1.1" in self.usable:
                self.errors.append("rule 5.1.1 of WRITING_RULES.md gives no title form")
                self.usable.discard("5.1.1")
            return None
        form = match.group(1)
        counts = Counter(re.findall(r"<(\w+)>", form))
        odd = sorted(name for name, count in counts.items() if name not in TITLE_PARTS or count > 1)
        if odd:
            names = ", ".join(f"<{name}>" for name in odd)
            which = f"placeholder {names} is" if len(odd) == 1 else f"placeholders {names} are"
            self.errors.append(f"rule 5.1.1 of WRITING_RULES.md gives the title form {form}, whose "
                               f"{which} unknown or repeated, so its check is skipped")
            self.usable.discard("5.1.1")
            return None
        pattern = re.escape(form)
        for name, part in TITLE_PARTS.items():
            pattern = pattern.replace(re.escape(f"<{name}>"), f"(?P<{name}>{part})")
        self.title_form = form
        return re.compile(pattern)
