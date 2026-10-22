# Python Code Quality Standard

This document gives the rules for the structure, logic, and resource use of Python code in this repository:
scripts, tests, and document-generation tooling. Generated outputs and third-party code are excluded.

- **Class:** living document. The lead approves each change, and each change gets a changelog entry.
- **Scope:** new and changed code. Bring a function into compliance when a change touches it. Unrelated existing
  code needs no rewrite. No CI check enforces this standard.
- **Related rules:** [`WRITING_RULES.md`](WRITING_RULES.md) covers prose, naming, comments, test descriptions, and the
  writing check. The project's security and governance rules still apply.
- **Departures:** a departure is code that does not follow a rule, unless the rule itself allows the case. Each
  departure needs a code comment with the reason and must be reported. A departure from a "Do not" or
  "must" rule needs the lead's approval, recorded in the changelog.

## 1. Control flow

1. Handle rejected cases first, with guard clauses, early returns, or `continue`. Avoid indentation of any kind
   that obscures the main operation.
2. Remove an `else` after a branch that always returns or raises, when the remaining flow becomes clearer.
3. Limit each nesting path in a function to two loop levels and three combined levels of `if`, `for`, `while`,
   and `match`. Count `async for` as `for`. Deeper nesting needs an algorithmic reason documented as a departure.
4. Each comprehension `for` clause adds one level to both limits, and its `if` filters add none. A two-clause
   comprehension inside a loop has three loop levels. An `elif`, `else`, or `case` branch adds no level beyond its
   parent statement. The `with`, `async with`, and `try` statements, including `except` and `finally`, add no level.
   Loops and conditionals inside them still count.
5. Use a comprehension for a simple transformation. Use an explicit loop for branching or side effects.
6. Give each loop a clear stopping condition. Limit retries and polling by an attempt count or a deadline.

## 2. Complexity and memory

1. Choose the algorithm and data structure for the expected input size, including growth beyond local samples.
   For inputs that grow with use, keep total time within O(N log N). Define N and any other input sizes used in
   the analysis. If the required computation or output exceeds this bound, document the algorithm, expected size,
   and reason as a departure. State other non-obvious time and memory costs in the function's docstring.
2. Count the work inside each loop, including the functions it calls. A list membership test, `list.index()`,
   `list.pop(0)`, `list.insert(0, x)`, and a slice copy each cost up to O(N). Repeated N times, they cost up to O(N²).
3. Build a set or dictionary once for repeated membership or key lookups, when its memory cost is acceptable. A hash
   lookup is O(1) on average, but building the index also takes time. Use `collections.deque` when frequent
   additions or removals at either end are needed and its access pattern fits the task.
4. Move invariant work out of loops. Avoid redundant sorting, parsing, file reads, and database or network calls.
5. Prefer indexing, grouping, or a standard algorithm to a brute-force search when it lowers the time complexity.
6. Process potentially large files and datasets with iterators, generators, or bounded batches. Avoid unbounded
   `read()`, `readlines()`, `list(iterator)`, and `fetchall()` calls when processing can be incremental. Pass each
   pipeline stage's results to the next stage as they are produced, not after collecting the entire output.
7. Aim for O(1) auxiliary memory relative to record count when each record can be processed independently. A fixed
   batch uses O(batch size) memory. Record size, driver buffering, and retained state add to this.
8. For sorting, deduplication, or a library that requires materialization, bound the input or use an external-memory
   approach. A generator alone does not guarantee constant memory.
9. Measure a suspected bottleneck with representative data before adding a cache or a more complex implementation.
   Limit each cache's size and define when entries expire or need to be refreshed.

## 3. Resources and cleanup

1. Manage owned files, locks, database sessions, and network resources with `with` or `async with`. Do not rely on
   a final `.close()` call or on garbage collection for cleanup after a failure.
2. For an API without context-manager support, use `contextlib.closing`, register cleanup with `ExitStack`, or
   provide a context manager that releases resources on success and failure. Use asynchronous equivalents where needed.
3. Check whether a context manager ends a transaction, closes the connection, or does both.
   For example, a [SQLite connection context](https://docs.python.org/3/library/sqlite3.html) commits or rolls back
   the transaction but does not close the connection.
4. State which code owns each resource. The creator releases it unless ownership is explicitly transferred.
   Code that receives a borrowed resource must not close it; its owner remains responsible for cleanup.
5. Hold resources only while needed. Consume a resource-backed iterator inside the owner's `with` scope so leaving that
   scope releases the resource even after an early exit. Do not return an iterator that depends on a closed resource.

## 4. Files and subprocesses

1. Set the encoding explicitly when opening a text file. Use `encoding="utf-8"` for repository text; match the
   documented encoding of an external format. Set `newline` when the format requires specific line endings.
   [Encoding defaults](https://docs.python.org/3/library/io.html#text-encoding) vary by Python version and environment.
2. Pass a subprocess command as an argument list with `shell=False`. For text streams, set the encoding to match
   the command's documented output; specifying `encoding="utf-8"` does not change the child's encoding.
   For binary output or streams with different encodings, capture bytes and decode each text stream separately.
3. Check the result of each subprocess call, with `check=True` or a return-code test. Set a `timeout` on a command
   that can hang.

## 5. Structure and dependencies

1. Give each function, class, and module one responsibility. Split distinct operations into focused helpers, but do
   not create a helper only to reduce indentation.
2. Make parsing, validation, transformation, and output separate steps with explicit inputs and results. Separate
   computation and decisions from filesystem, database, network, and process operations.
3. Pass external dependencies into functions or constructors. Create clients at the entry point or in a dedicated
   factory, not inside business logic.
4. Inject clocks and randomness when they affect decisions, so tests can control time and generated values.
5. Run a script through `main()`, behind an `if __name__ == "__main__":` guard. Importing a module must not
   start work, parse arguments, or open connections.
6. Reusable logic must not import CLI entry points or depend on their global state.
7. Extract a repeated business rule or transformation into a helper. Share code only when it has the same contract
   and the same reason to change, not because it looks similar. Avoid helpers whose flags select unrelated behaviors.
8. Use small interfaces or `Protocol` types where interchangeable implementations help. Add a framework only when
   plain functions, classes, and protocols cannot meet the need.

## 6. Configuration and constants

1. Load environment-specific URLs, paths, and settings from CLI arguments, `os.getenv`, or a configuration utility.
   Stable project constants and portable repository-relative paths may stay in code.
2. Parse and validate configuration once at the entry point. Then pass typed values to the code that needs them.
3. Fail with a clear error when a required setting is missing or invalid. Do not silently substitute production
   credentials or endpoints.
4. Do not put secrets in source code. This includes test credentials that grant real access.
5. Replace an unexplained numeric or string literal in a decision or formula with a descriptive constant. A literal
   `0`, `1`, or empty collection may stay when its meaning is self-evident.
6. Name constants in uppercase. Include the unit when the value has one, such as `REQUEST_TIMEOUT_SECONDS` or
   `SECONDS_IN_A_DAY = 86_400`. Give a count a descriptive name, such as `MAX_RETRIES`. Define constants near the
   code that uses them.
7. Use an `Enum` for a closed set of states, not comparisons such as `status == 4`.
8. Copy protocol-defined values exactly. State the source of a value when the code does not make it clear.

## 7. Types and data contracts

1. Annotate the arguments and return type of every named function, including private helpers, tests, and
   `__init__`. Annotate a function with no result as `-> None`. The conventional `self` and `cls` parameters may
   keep their inferred types.
2. Parameterize container and callable types. Use `Optional`, `Union`, or the equivalent syntax for alternatives.
   Use generics to preserve the relationship between input and output types.
3. Avoid `Any`. For an unknown value, prefer `object` and narrow it. When an untyped library is unavoidable,
   wrap its calls in a small adapter.
4. Use `TypedDict`, dataclasses, or protocols for structured data.
5. Accept interfaces such as `Iterable[T]` or `Mapping[K, V]` when callers need not supply a concrete collection.
6. Type hints do not validate runtime input. Validate external data, such as file contents and API responses,
   before use.
7. Do not silence type errors with broad ignores or unchecked casts. Fix the mismatch. For a confirmed checker
   limitation, use a suppression for the specific diagnostic and explain why the code is valid.

## 8. Errors and diagnostics

1. Catch the specific exceptions that the operation can raise and the handler can handle. Do not use bare
   `except:`, `except BaseException`, or a catch-all `except Exception`. The first two also catch interrupts and
   exits, as the [exception hierarchy](https://docs.python.org/3/library/exceptions.html#exception-hierarchy) shows.
2. Keep `try` blocks narrow, so a handler cannot mistake an unrelated programming error for an expected failure.
3. Do not swallow failures silently. After an unexpected error, do not return an empty value that implies success.
   Return an empty result for an expected absence only when the function's contract defines it.
4. Preserve the original cause when translating an error: `raise DomainError(...) from cause`.
5. Use a module logger for diagnostics. Inside a handler, call `logger.exception()` when the traceback is needed.
6. Log a failure at the layer responsible for reporting it. Do not log and re-raise at every layer.
7. Do not log secrets or sensitive payloads, including those embedded in exception messages or tracebacks.
8. Use `print()` only for intended CLI output. A failed command returns a nonzero exit status.

## 9. Mutable state and defaults

1. Do not use a list, dictionary, set, or other mutable instance as a default argument.
   [Defaults are evaluated once](https://docs.python.org/3/tutorial/controlflow.html#default-argument-values),
   so calls that use a mutable default share that object.
2. Default to `None` and create the value inside the function. Test it with `is None`, because a truthiness test
   replaces a caller's valid empty collection. Use an explicit sentinel when `None` is a meaningful argument.
3. Use `field(default_factory=list)` or the matching factory for a mutable dataclass field.
4. Store per-instance collections on the instance, not in mutable class attributes.
5. Avoid hidden mutable global state. A function that changes a caller-owned collection must state the change in
   its contract.

## 10. Strings

1. Use f-strings for ordinary string interpolation, not `%` formatting, `.format()`, or concatenated fragments.
   Use `str.join()` to assemble a sequence of strings. API templates and bound parameters follow rule 10.2 instead.
2. Pass values through the API's parameters. For logging, pass a template and separate arguments, such as
   `logger.error("Import failed for %s", record_id)`. Use bound parameters in SQL. Do not replace them with
   interpolated strings.

## 11. Standard library and Python versions

1. State the minimum Python version of each tool in its module docstring. Use only syntax and standard-library
   APIs available in that version and every later version in use.
2. Check the standard library and the existing project helpers before writing a general-purpose utility. Choose a
   utility by its documented behavior, not only because it shortens the code.
3. Use `pathlib` for paths, `collections.Counter` for counting, and `defaultdict` for simple grouping.
4. Use `csv`, `json`, `itertools`, and other suitable modules instead of handwritten parsing or iteration code.
5. Use `datetime` and `timedelta` for calendar values and durations, and timezone-aware values for timestamps. Use
   `time.monotonic()` for elapsed time and deadlines inside a running process.

## 12. Verification

1. For a behavior change, test the relevant boundaries and failures with controlled dependencies. When changing
   state or resource handling, also test isolation between repeated calls and cleanup after errors.
2. Do not write tests that only restate the implementation.
3. Run the affected tool's tests on its minimum Python version and the newest version used by the project.
   Run applicable existing checks. Report each required test environment that was not available.
4. Use strict mypy or Pyright checking where it is configured. Otherwise, record the missing type-check coverage.
   Annotations alone do not prove type safety.
