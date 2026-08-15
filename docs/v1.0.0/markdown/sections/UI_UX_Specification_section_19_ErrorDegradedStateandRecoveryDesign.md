# UI/UX Specification

> Version 1.0.0

## 19. Error, Degraded-State, and Recovery Design

#### 19.1 Error Categories

<!-- table-widths: 1.7, 2.55, 2.25 -->
| Category | Examples | Safe response |
| --- | --- | --- |
| Input | Invalid PIN format, mismatch, incorrect PIN. | Keep protected, identify correction without revealing secrets. |
| Android access | Denied or revoked Usage Access, missing lock presentation, blocked notifications. | Replace healthy claim, explain consequence, offer verified handoff. |
| Protection operation | Detection or cover cannot be established. | Cover when possible, show Protection interrupted, provide safe exit and recovery. |
| Local data | Save failure, migration failure, corruption, unreadable protected state. | Preserve prior valid data; do not use permissive defaults; offer controlled reset only when necessary. |
| Inventory | Installed-app list unavailable or target identity changed. | Preserve committed choices, disable stale actions, retry and reconcile. |
| Unknown or not verified | Timeout, contradictory, or stale evidence. | Show Not verified and run a fresh check; never infer Protected. |

#### 19.2 Message Structure

Persistent failures shall state: what happened, what is affected, what remains safe or unavailable, and the next action. Technical identifiers and exception text shall not be shown. Retry appears only when the operation is safe and can reasonably succeed; repeated failure moves to the durable Health or Diagnostics explanation.

#### 19.3 Recovery Rules

- A retry shall not duplicate a protection change or destructive action.
- A failed save restores the previously committed visual value.
- A settings handoff is followed by verification, not assumed success.
- A stale target is discarded rather than retargeted by name alone.
- An unreadable credential or configuration never creates authorization.
- A complete reset is the final local recovery only after explicit consequences and authentication where possible.

#### 19.4 User-Facing Error Catalog

<!-- table-widths: 2.1, 2.75, 1.65 -->
| Condition | Required message intent | Primary action |
| --- | --- | --- |
| Usage Access unavailable | App Lock cannot reliably identify protected apps. | Open Usage Access. |
| Lock presentation unavailable | App Lock cannot reliably cover a protected app. | Review Android setting. |
| Protection check timed out | Current protection could not be verified. | Check again. |
| PIN save failed | No new PIN was saved. | Try again. |
| Protection change failed | The previous protection setting remains. | Retry. |
| App identity changed | The selected app can no longer be safely matched. | Return to Apps. |
| Local protected data unreadable | App Lock cannot use the current local configuration safely. | Review reset information. |
| Force-stop detected or reported | Android may have prevented App Lock from running. | Start and check protection. |

## Part VI — Acceptance and Document Consistency
