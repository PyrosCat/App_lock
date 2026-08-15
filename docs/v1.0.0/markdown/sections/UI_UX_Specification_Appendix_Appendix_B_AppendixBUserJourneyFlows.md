# UI/UX Specification

> Version 1.0.0

## Appendix B — User-Journey Flows

<!-- table-widths: 1.9, 3.3, 1.3 -->
| Journey | Required path | Safe non-success exit |
| --- | --- | --- |
| Initial setup | SCR-001 → 002 → 003 → 004 → 005 → 006 → 007 → 020. | SCR-008 preserves partial progress and resumes the earliest incomplete step. |
| Protected app unlock | Protected target → SCR-011 → PIN or SCR-012 → validated target session. | Cancel/failure returns away; lockout uses SCR-013. |
| Open App Lock | External or launcher entry → SCR-010 → revalidated requested destination. | Exit shows no configuration; forgotten PIN opens SCR-015. |
| Add protection | SCR-022 → SCR-005 or SCR-023 → save → reconciled SCR-022. | Failed save restores previous state. |
| Remove protection | SCR-023/022 → DLG-001 → SCR-014 when required → save. | Cancel/stale scope applies no change. |
| Restore access | SCR-021/054 → explanation → SYS handoff → recheck → SCR-006. | Denial remains visible with truthful non-healthy state. |
| Complete reset | SCR-050 → DLG-006 → SCR-014 → secure deletion → SCR-001. | Cancel or failed authentication changes nothing. |
