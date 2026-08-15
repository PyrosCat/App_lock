# UI/UX Specification

> Version 1.0.0

## 16. Dialogs, Notifications, System Handoffs, and Transitions

#### 16.1 Dialog Catalog

<!-- table-widths: 0.85, 1.6, 2.75, 1.3 -->
| ID | Headline | Required consequence | Actions |
| --- | --- | --- | --- |
| DLG-001 | Remove protection from {app}? | The named app will open without App Lock authentication. | Remove protection; Cancel. |
| DLG-006 | Reset App Lock completely? | PIN, protected-app selections, settings, sessions, and local diagnostics are permanently removed; protected-app data is not removed. | Reset App Lock completely; Cancel. |
| DLG-007 | End active sessions? | App Lock and every protected app will require authentication again. | End sessions; Cancel. |
| DLG-008 | Reset preferences? | Name the non-security display and notification preferences restored; retain PIN and protected-app choices. | Reset preferences; Cancel. |
| DLG-009 | Remove protection from all apps? | State the reconciled count and that every listed app will open without App Lock authentication. | Remove all protection; Cancel. |

Destructive confirmation requires an explicit button; outside tap shall not confirm. Scope shall be revalidated immediately before execution.

#### 16.2 Notification Catalog

<!-- table-widths: 0.9, 1.55, 2.55, 1.5 -->
| ID | Private title | Private body | Entry |
| --- | --- | --- | --- |
| NTF-001 | App Lock is active | Protection is running on this device. | Open Protection Health through authentication. |
| NTF-002 | Protection interrupted | Open App Lock to restore or verify protection. | Open the current Health recovery route. |
| NTF-003 | Action required | Open App Lock to review a protection setting. | Open the applicable authenticated row. |

Notifications shall omit protected-app names, PIN or biometric detail, technical identifiers, and raw diagnostic reasons. Dismissing a notification does not dismiss the durable in-app condition. A resolved or stale notification opens the safe parent and states that the condition changed.

#### 16.3 Android Handoffs

<!-- table-widths: 0.9, 1.65, 2.25, 1.7 -->
| ID | Destination | Before leaving | On return |
| --- | --- | --- | --- |
| SYS-001 | Usage Access | Explain purpose, required role, privacy boundary, Android-visible label, and denial consequence. | Recheck and return focus to the Usage Access row. |
| SYS-002 | Lock-presentation setting | Explain the device-visible setting and that protection cannot be verified without it. | Verify the capability and supported presentation behavior. |
| SYS-004 | Notification settings | Name the affected App Lock category and operational consequence. | Recheck runtime permission and relevant channels. |
| SYS-005 | Biometric enrollment | Explain Android ownership and continued PIN fallback. | Re-evaluate eligibility; do not infer enrollment. |
| SYS-007 | App or battery restrictions | Name the observed restriction and use the narrowest verified path. | Recheck the originating restriction and global health. |
| SYS-009 | Android biometric prompt | Show authentication purpose and PIN fallback first. | Consume only the current result; cancellation creates no session. |

#### 16.4 Motion and Interruption

Protected content shall be covered before navigation or animation. Routine movement shall be short, purposeful, interruptible, and replaced by an immediate change or brief fade under reduced motion. Animation shall never extend a session, delay a secure cover, or conceal a status change.

Rotation, backgrounding, task switching, and process recreation may preserve non-secret context only. A dialog returns focus to its invoking control; a system handoff returns focus to its originating row. Session expiry covers sensitive content before the gate appears.

## Part V — Design System and Cross-Cutting Requirements
