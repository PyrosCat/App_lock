# UI/UX Specification

> Version 1.0.0

## 6. Content and Terminology

#### 6.1 Voice

Content shall be concise, calm, direct, and specific. It shall name what happened, the consequence, and the next action. It shall avoid blame, alarmist language, unnecessary technical terms, and false certainty.

#### 6.2 Controlled Terms

<!-- table-widths: 1.85, 2.3, 2.35 -->
| Term | Use | Avoid |
| --- | --- | --- |
| Protected app | An app selected to require App Lock authentication. | Secured app, encrypted app, blocked app. |
| PIN | The required numeric App Lock credential. | Password when referring to the PIN. |
| Biometrics | The Android-approved biometric option. | Fingerprint when other eligible modes may apply. |
| Protection access | Required Android capabilities considered together. | Full control, surveillance access. |
| Usage Access | The Android setting used to identify foreground applications. | Usage permission if Android shows a different label. |
| Protection interrupted | A required capability or enforcement path is unavailable. | You are safe, protection is probably working. |
| Action required | A person must complete a specific step. | Error when no system fault occurred. |
| Reset App Lock | Destructive removal of local credential and configuration. | Recover PIN, restore access, reset PIN. |

#### 6.3 Status Copy Pattern

Every persistent status shall contain:

1. a controlled headline;
2. a one-sentence consequence;
3. the affected capability when it can be stated safely;
4. evidence age when the result can become stale; and
5. one primary action.

Examples include “Protection interrupted — Usage Access is unavailable, so App Lock cannot reliably identify protected apps. Open Usage Access.” and “Protection not verified — The current protection state could not be confirmed. Check protection.”

#### 6.4 Authentication Copy

Use “Incorrect PIN. Try again.” without revealing which digit or rule failed. During retry delay, show the authoritative time remaining or the next permitted attempt time. Biometric cancellation, rejection, unavailability, and lockout shall use different messages and shall always keep “Use PIN” available when authentication is permitted.

#### 6.5 Warning Language

Warnings shall state scope and result before the action label. Destructive labels shall name the action: “Remove protection,” “Reset settings,” or “Reset App Lock completely.” Do not use “OK” for a security decision. Do not use “guaranteed,” “unbreakable,” “always protected,” “military-grade,” or claims that App Lock controls Android behavior it cannot control.

## Part III — User Journeys
