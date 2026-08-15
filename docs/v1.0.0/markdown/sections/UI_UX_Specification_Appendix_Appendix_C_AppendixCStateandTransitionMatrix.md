# UI/UX Specification

> Version 1.0.0

## Appendix C — State and Transition Matrix

<!-- table-widths: 1.85, 2.1, 2.55 -->
| From | Trigger | To |
| --- | --- | --- |
| Not configured | PIN stored | Partially configured. |
| Partially configured | Required access, app selection, and verification succeed | Protected. |
| Protected | Required access revoked or enforcement fails | Protection interrupted or Action required. |
| Protected | Evidence becomes stale | Unknown or not verified. |
| Any configured state | Relevant restriction detected | Degraded when operation remains possible; otherwise Protection interrupted. |
| Any configured state | Complete reset succeeds | Not configured. |
| Any state | Contradictory or unavailable evidence | Unknown or not verified. |
