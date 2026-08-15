# Threat Model

## Version 1.0.0

## Appendix A — Threat-to-Control and Verification Matrix

| Threat group | Threat identifiers | Primary controls | Primary verification |
|---|---|---|---|
| PIN confidentiality and administration | THR-CRED-001 through THR-CRED-004 | SC-AUTH-001, SC-AUTH-002, SC-DATA-001, SC-DATA-003, SC-RESET-001, SC-UI-001, SC-PRIV-002 | VA-AUTH-006, VA-DATA-001, VA-DATA-008 through VA-DATA-010, VA-UI-001, VA-PRIV-001 |
| Authentication correctness and abuse resistance | THR-AUTH-001 through THR-AUTH-004 | SC-AUTH-001, SC-AUTH-003, SC-AUTH-004, SC-SESS-001, SC-UI-003 | VA-AUTH-001 through VA-AUTH-010 |
| Detection and enforcement | THR-ENF-001 through THR-ENF-006 | SC-ENF-001 through SC-ENF-003, SC-HEALTH-001, SC-HEALTH-002, SC-LIFE-001, SC-LIFE-002 | VA-ENF-001, VA-ENF-007 through VA-ENF-012, VA-HEALTH-001 through VA-HEALTH-008 |
| Package-scoped sessions and global relock | THR-SES-001 through THR-SES-004 | SC-SESS-001 through SC-SESS-003, SC-ENF-002, SC-LIFE-001 | VA-AUTH-001, VA-AUTH-005, VA-ENF-003 through VA-ENF-012 |
| Database and cryptographic protection | THR-CRYPTO-001, THR-CRYPTO-002, THR-CRYPTO-004, THR-CRYPTO-005 | SC-DATA-001 through SC-DATA-005, SC-RESET-001 | VA-DATA-001 through VA-DATA-007 |
| UI, component, and peer-service attacks | THR-UI-001 through THR-UI-005; THR-IPC-001 through THR-IPC-003; THR-ACC-004 | SC-UI-001 through SC-UI-003, SC-COMP-001, SC-AUTH-001, SC-RESET-001 | VA-ENF-009, VA-ENF-010, VA-UI-001 through VA-UI-003, VA-COMP-001, VA-COMP-002 |
| Lifecycle and protection health | THR-LIFE-001 through THR-LIFE-005; THR-AUD-001; THR-AUD-003 | SC-LIFE-001, SC-LIFE-002, SC-HEALTH-001, SC-HEALTH-002, SC-PRIV-002 | VA-ENF-011, VA-ENF-012, VA-HEALTH-001 through VA-HEALTH-007, VA-PRIV-002 |
| Corruption, migration, key loss, and destructive reset | THR-REC-001, THR-REC-002, THR-REC-003, THR-REC-005 | SC-DATA-003 through SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | VA-DATA-004 through VA-DATA-010 |
| Production package and dependency integrity | THR-INT-001, THR-INT-004, THR-SUP-001 through THR-SUP-003 | SC-BUILD-001, SC-BUILD-002 | VA-BUILD-001, VA-BUILD-002 and retained security regression scenarios |
| Android platform boundary | THR-PLAT-001 through THR-PLAT-004 | SC-BOUND-001, SC-DATA-002, SC-HEALTH-001, SC-LIFE-001 | Supported API phone verification and explicit limitation review |

---
