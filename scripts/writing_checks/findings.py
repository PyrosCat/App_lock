"""The Finding and CheckError types. A CheckError stops the check of a file or of the whole run."""

from typing import NamedTuple


class Finding(NamedTuple):
    path: str
    line: int
    rule: str
    message: str


class CheckError(Exception):
    pass
