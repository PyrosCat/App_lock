# Writing Rules

This document gives the rules for the text in this repository: code comments and KDoc, test code, repository
documents, changelog entries, and commit subjects. The rules bind every contributor, human or AI assistant, on every
fleet machine.

- **Class:** living document. The lead approves each change, and each change gets a changelog entry.
- **Related rules:** report evidence (immutability, host tags, corrections) is in
  [`docs/reports/README.md`](../reports/README.md). Dates follow the date rules of GOVERNANCE.md.

## 1. All text

1. Write short sentences with direct statements. Follow the principles of ASD-STE100 (simplified technical
   English) where they apply. Keep established technical terms and code identifiers. Do not claim formal
   ASD-STE100 compliance.
2. Use one term for one concept, and use precise verbs.
3. Do not use capitals for emphasis (NEVER, ONLY). Use MUST and MUST NOT only in files that already use these
   keywords.
4. Use em-dashes sparingly. Use a comma, a colon, parentheses, or two sentences instead.
5. Describe the current state. Remove outdated statements, and do not refer to review rounds or to the history
   of a change.
6. Keep requirement and risk IDs (FR-, NFR-, R-) where they help traceability.
7. Cite few documents. Cite the canonical source once, and remove decorative citations. Keep the citations that
   governance requires: RTM evidence pointers, SSOT references, the related requirements of an ADR, and the
   RTM and ADR links of the same commit.
8. Use ISO dates. Committed text does not use relative terms such as "this session" or "yesterday".
9. Refer to another document by its name and the topic, not by a section number. Write "the latency
   distributions of the test plan", not "test plan §12". Use a section number only when the reader must find one
   exact passage. Inside a document, write "section 3.3", not "§3.3".

## 2. Code comments and KDoc

1. Keep comments concise. Remove repetition, obvious descriptions of the code, and project jargon that the
   reader does not need.
2. Keep the comments that explain intent, ordering, concurrency, security constraints, lifecycle behaviour, or
   a decision that is not obvious. Do not shorten them at the cost of accuracy.
3. Use descriptive and consistent names. Rename only where the new name clearly helps. Do not rename broadly or
   change a public API for style alone.

## 3. Test code

1. A test name states the case and the expected result. When a test plan defines case IDs, the test name starts
   with the case ID.
2. Local names in tests are descriptive, as in production code:

   | Do not write | Write |
   |---|---|
   | `h` | `harness` |
   | `f4`, `f5` | `fourthFailure`, `fifthFailure` |
   | `repeat(4) { i -> ... i + 1 ... }` | `(1..4).forEach { count -> ... }` |
   | `out`, `err` | `stdoutFile`, `stderrFile` |
   | `val error = assertThrows(...)` | `val thrown = assertThrows(...)` (`error` hides Kotlin's `error()`) |

3. When a test needs two objects of one kind, name each one by its role, for example `faultyHarness` and
   `healthyHarness`.
4. An assertion message states the expected fact. It is not a question.

## 4. Repository documents

1. Remove repeated explanations. Keep decisions, evidence, limitations, and unresolved risks.
2. The author or host field of a report names the machine, the lead, or both. It never names an AI assistant.

## 5. Changelog entries

`changelog.txt` in the repository root holds the detail that a commit body would otherwise hold.

### 5.1 Form

1. Put the newest entry on top. The title line is `<area>: <what>   (<date>)`, followed by a line of dashes of
   the same length.
2. Start with an introduction of one or two plain sentences. Then give one flat list of bullets, with no nested
   bullets.
3. The date is the date on which the work was completed.
4. Change an entry only while it is not committed.

### 5.2 Content

1. Each sentence says what the change does to what.
2. For new code, name what was added and give its purpose in a few words. Do not describe how the code behaves
   or what it guarantees.
3. Evidence is an action: "The local gate ran 468 unit tests with no failures."
4. Do not add these items:
   - Bullets about things that did not change.
   - Explanations of why the earlier state was wrong. A short cause is correct when the entry fixes a defect.
   - Before-and-after asides, such as "(it still said open)".
   - Reassurances, such as "the status of both rows does not change".
   - A restatement of a recorded decision or policy as if it were a change.
   - References to rules of any kind: governance sections, plan invariants, ADR requirements, or RTM status.
5. Write for a reader outside the project. Use plain words, and explain a phase, a case ID, or another project
   term at its first use, for example "phase P2, in which the current app is tested on devices". Do not use the
   shorthand of a test harness, such as "smoke run", "segment", or "objective rule".

   | Do not write | Write |
   |---|---|
   | The Moto G smoke times of the reboot and biometric segments lower their full-run estimate to about 50 min. | Trial-run timings cut the expected time of the project lead at the phone from 1.5 h to 50 min. |
   | Lead decision 6 confirms the objective rule for residual repeats and for controls that cannot lose a lock. | A case that reproduces a known R-007 weakness as predicted gets the verdict "not met". |

### 5.3 Sentences

1. Each sentence is complete, with a subject and a verb.
2. Each subject says what it is and what it belongs to. A bare name or a bare list of names as the subject reads
   as a fragment, even when the sentence has a verb.

   | Do not write | Write |
   |---|---|
   | Detekt, assembleProdDebug, and compileProdDebugAndroidTestKotlin passed. | In the same gate run, the detekt, assembleProdDebug, and compileProdDebugAndroidTestKotlin tasks passed. |
   | FaultPlan gets a ThrowCancellation script. | The FaultPlan class gets a ThrowCancellation script. |
   | Separate sections list the limits. | Other sections of the report list the limits. |
   | test_lib_r007.sh passed 169 of 169. | The test_lib_r007.sh script passed all 169 host tests. |

3. Do not use appositive chains or semicolon lists. Write "The harness gets X. X does Z.", not "X, a factory for
   Y, does Z".
4. Start a sentence about a file with "The <name> script", "The <name> class", or "The <name> report", not with
   a bare file name.
5. Keep each sentence to 20 words or fewer.

### 5.4 Variety

1. No two bullets of an entry start with the same three words.
2. Do not chain sentences that start with "It", and avoid chains of "its". Name the item instead.
3. Do not start the introduction with "This change". Start with the item that changes, for example "The planning
   document for the R-007 hardening work now shows ...". In a bullet, use "This change ..." only where the action
   needs it.

## 6. Commit subjects

1. A commit subject is one line. The detail goes into the changelog entry.
2. The subject starts with the milestone and the area, for example
   `M7/WP2 (F2 Hardening): P2 JVM baseline characterization tests`. The changelog title of the same work names
   the same milestone, area, and work.
3. A commit or a pull request names no AI assistant as author or co-author.

## 7. Check before hand-off

1. Read every added line of the change, not only the line widths: `git diff -U0 -- <files>`, then each line that
   starts with `+`. Include comments, messages, test names, and each header paragraph that the change touches.
2. For a changelog entry, also read the first three words of each bullet, and check each subject for a noun that
   says what it is. Then read the entry as a reader outside the project: each term is plain or explained.
3. The hand-off states that the check was done and what it changed.
