# Threat Model

## Version 1.0.0

## 8. Security Verification and Acceptance

### 8.1 Verification Principles

Verification shall exercise successful use, negative paths, interruption, stale state, and Android limitation behavior. A function is not security-acceptable merely because its normal path works. Each scenario shall verify both what happens and what must not happen.

Testing shall be limited to conventional Android phones and phone emulators across API levels 30 through 35. It shall not create acceptance obligations for excluded device classes, Android versions, profiles, cloned applications, or manufacturer-specific dual-app environments.

### 8.2 Authentication Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-AUTH-001 — Correct PIN** | The active request succeeds and creates one valid session for only the active protected target or authenticated settings context | THR-AUTH-001, THR-SES-001 |
| **VA-AUTH-002 — Incorrect PIN** | Access is denied, failure state advances, and no session exists | THR-AUTH-001, THR-AUTH-004 |
| **VA-AUTH-003 — Repeated failures** | Fixed retry delay and lockout apply at the defined thresholds | THR-AUTH-004 |
| **VA-AUTH-004 — Restart during lockout** | Restart, process termination, and reboot do not provide unrestricted new attempts | THR-AUTH-004, THR-SES-004 |
| **VA-AUTH-005 — PIN cancellation and timeout** | The target remains unauthorized and no success state survives | THR-AUTH-001, THR-AUTH-003 |
| **VA-AUTH-006 — PIN change** | Current PIN is required; old PIN fails after completion; sessions are cleared | THR-CRED-002, THR-AUTH-003, THR-SES-004 |
| **VA-AUTH-007 — Eligible biometric success** | Only the active request succeeds and the normal package-scoped session and global relock rules apply | THR-AUTH-002, THR-SES-001 |
| **VA-AUTH-008 — Biometric cancellation or rejection** | No session is created and PIN fallback remains available | THR-AUTH-002, THR-AUTH-003 |
| **VA-AUTH-009 — Biometric unavailable or locked** | PIN remains fully usable and no access is granted automatically | THR-AUTH-002 |
| **VA-AUTH-010 — Screen recreation during authentication** | Rotation, backgrounding, process recreation, and prompt replacement do not reuse stale success | THR-AUTH-002, THR-AUTH-003, THR-UI-004 |

### 8.3 Enforcement and Session Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-ENF-001 — Protected application without session** | The lock interface is presented and access is not treated as authorized | THR-ENF-001, THR-ENF-006 |
| **VA-ENF-002 — Unprotected application** | No App Lock authentication is introduced | Configuration integrity check; prevents false positives without weakening protected targets |
| **VA-ENF-003 — Cross-application session isolation** | A valid session may be reused only for the same protected application; unlocking one protected application does not authorize another | THR-SES-003 |
| **VA-ENF-004 — Immediate relock** | Leaving the authorized context ends authorization as defined and the next protected access requires authentication | THR-SES-002 |
| **VA-ENF-005 — Screen-off relock** | Screen-off ends authorization and the next protected access requires authentication | THR-SES-002 |
| **VA-ENF-006 — Timeout relock** | Expiry ends authorization; wall-clock changes do not improperly extend it | THR-SES-002 |
| **VA-ENF-007 — Rapid switching** | Repeated transitions between protected and unprotected applications do not bypass evaluation | THR-ENF-004, THR-SES-003 |
| **VA-ENF-008 — Rapid relaunch** | Immediate relaunch of the same protected application does not reuse stale target or expired authorization | THR-ENF-004 |
| **VA-ENF-009 — Back, Home, and Recents** | Navigation does not create authorization or leave usable protected content exposed through the App Lock path | THR-UI-004 |
| **VA-ENF-010 — Phone multi-window and picture-in-picture** | The supported phone either maintains protected presentation or safely denies/cancels the access; no separate optimized layout is required | THR-UI-004, THR-ENF-006 |
| **VA-ENF-011 — Process termination** | All sessions are lost and protected access requires authentication after recovery | THR-LIFE-002, THR-SES-004 |
| **VA-ENF-012 — Device reboot** | Sessions are lost and protection is restored at the earliest Android-permitted opportunity | THR-LIFE-004, THR-LIFE-005, THR-SES-004 |

### 8.4 Permission and Health Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-HEALTH-001 — Usage Access denied during setup** | Setup does not claim protection complete; Action required identifies the missing capability | THR-ENF-002, THR-AUD-003 |
| **VA-HEALTH-002 — Usage Access revoked** | Protected status is withdrawn when the loss is known; recovery guidance opens the correct Android settings destination | THR-ENF-002, THR-ENF-003 |
| **VA-HEALTH-003 — Presentation capability unavailable** | No session is created; state becomes Action required during incomplete setup or Protection interrupted after active protection is lost | THR-ENF-006, THR-UI-004 |
| **VA-HEALTH-004 — Health cannot be verified** | The application displays Unknown or not verified rather than Protected | THR-ENF-003, THR-AUD-003 |
| **VA-HEALTH-005 — Known reduced responsiveness** | The application displays Degraded with accurate wording and does not imply normal response | THR-LIFE-003, THR-PLAT-004 |
| **VA-HEALTH-006 — Force-stop recovery** | On the next launch, the application rechecks required capabilities and does not claim protection during the stopped interval | THR-LIFE-001 |
| **VA-HEALTH-007 — Essential notification privacy** | Lock-screen and notification content identify only the minimum App Lock status and action | THR-UI-005 |
| **VA-HEALTH-008 — No App Lock Accessibility dependency** | Core setup and protection request no Accessibility permission and remain independent of an App Lock Accessibility service | Confirms removal of THR-ACC-001/002/003/005 surfaces |

### 8.5 Storage, Migration, and Reset Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-DATA-001 — PIN storage inspection** | No plaintext PIN appears in database, private settings, cache, logs, diagnostics, or resources | THR-CRED-001, THR-CRED-004 |
| **VA-DATA-002 — Database inspection** | Protected configuration is not readable as plaintext from the stored database | THR-CRYPTO-002 |
| **VA-DATA-003 — Key-location inspection** | Database secret is not stored in plaintext beside the database or in ordinary settings/resources | THR-CRYPTO-001, THR-CRYPTO-002, THR-CRYPTO-004 |
| **VA-DATA-004 — Valid in-place migration** | Credential state, protected selection, global relock setting, retry state, and required security values remain consistent | THR-REC-002 |
| **VA-DATA-005 — Interrupted migration** | Prior valid data remains usable or normal operation stops safely; partial permissive state is not accepted | THR-REC-001, THR-REC-002 |
| **VA-DATA-006 — Corrupt database** | The application shows Action required during incomplete setup or Protection interrupted after active protection is lost and does not show a healthy empty selection | THR-REC-001, THR-AUD-003 |
| **VA-DATA-007 — Keystore invalidation** | No key or session bypass occurs; unreadable data leads only to safe status and full reset | THR-CRYPTO-005, THR-REC-003 |
| **VA-DATA-008 — Forgotten-PIN reset review** | The loss is explicit, the PIN is not revealed, and reset cannot create authorization | THR-CRED-002, THR-REC-005 |
| **VA-DATA-009 — Cancel destructive reset** | All local state remains unchanged and no session is created | THR-REC-005 |
| **VA-DATA-010 — Complete destructive reset** | Credential, selections, settings, sessions, diagnostics, cache, and unusable data are removed before initial setup appears | THR-CRED-003, THR-REC-005 |

### 8.6 UI, Component, Privacy, and Package Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-UI-001 — Screenshot and Recents privacy** | PIN and sensitive App Lock screens are concealed from ordinary capture and recent-app previews as specified | THR-UI-005, THR-CRED-001 |
| **VA-UI-002 — Obscured touch** | Authentication and destructive/protection-reducing controls reject or safely handle obscured input | THR-UI-001, THR-UI-003, THR-ACC-004 |
| **VA-UI-003 — Injected accessibility action** | A non-deliberate injected action cannot complete PIN authentication or silently confirm destructive/protection-reducing action | THR-UI-003, THR-ACC-004 |
| **VA-COMP-001 — External screen launch** | A direct external invocation cannot open authenticated content, create a session, or alter configuration | THR-IPC-001 |
| **VA-COMP-002 — External background invocation** | Crafted intents or broadcasts cannot change authorization or protection settings | THR-IPC-002, THR-IPC-003 |
| **VA-PRIV-001 — Diagnostic inspection** | Bounded records contain no PIN, verifier, key, biometric data, full protected list, or third-party content | THR-CRED-001, THR-CRED-004, THR-AUD-001 |
| **VA-PRIV-002 — Diagnostic failure** | Record loss or write failure does not create or extend authorization | THR-AUD-001, THR-AUD-003 |
| **VA-BUILD-001 — Production package** | The distributed package is signed, non-debuggable, contains no production secret, and enables required sensitive-screen protections | THR-INT-001, THR-INT-004, THR-SUP-003 |
| **VA-BUILD-002 — Retained dependency validation** | Dependencies directly affecting the included security boundary are checked for known critical incompatibility or vulnerability and the production package passes retained security regression scenarios | THR-SUP-001, THR-SUP-002 |

### 8.7 Acceptance Rule

Version 1.0.0 is security-acceptable only when:

- no known reproducible path within the supported application boundary grants App Lock authorization without successful active authentication or a valid unexpired session for the same protected application;
- failed, cancelled, interrupted, or expired authentication creates no session;
- rapid switching, rapid relaunch, process termination, reboot, and supported phone window modes do not create a stale-session bypass;
- retry delay and lockout remain effective across the defined lifecycle events;
- PIN, key, and protected-configuration material are not stored or exposed in plaintext;
- migration, corruption, Keystore invalidation, and destructive reset do not create authorization or a falsely healthy permissive state;
- known loss of a required protection capability is not displayed as Protected;
- essential notifications and bounded diagnostics disclose no unnecessary sensitive information;
- the stated platform limitations are presented accurately and are not represented as prevented.

This acceptance rule does not claim protection during force-stop, after uninstall or application-data clearing, on rooted or system-compromised phones, or on excluded device and profile classes.

---
