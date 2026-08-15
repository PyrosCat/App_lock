# UI/UX Specification

> Version 1.0.0

## 10. Permissions and Protection Recovery

#### 10.1 Recovery Objective

Recovery shall help a person restore a required Android capability without hiding the period in which protection was reduced or unavailable. App Lock shall distinguish access loss, operating restriction, protection-operation failure, and an unverified state.

#### 10.2 Access Matrix

<!-- table-widths: 1.55, 1.05, 2.1, 1.8 -->
| Access | Status | Consequence when unavailable | Primary action |
| --- | --- | --- | --- |
| Usage Access | Required | App Lock cannot reliably identify a protected app in the foreground. | Open Usage Access. |
| Display over other apps (lock presentation) | Required | App Lock cannot draw the lock over a protected app, so protection cannot be enforced. | Open "Display over other apps" settings. |
| Notifications | Conditional | Required ongoing or action-required alerts may be unavailable or hidden. | Allow notifications or open notification settings. |
| Biometrics | Optional | PIN remains fully available. | Use PIN or open biometric enrollment by choice. |
| Battery/background setting | Situational | Protection may be delayed or stopped by the device. | Review the observed restriction. |

Accessibility, notification-listener, camera, location, storage/media, device-administration, and document-provider access shall not appear.

#### 10.3 Recovery Sequence

1. Detect or receive evidence of a problem.
2. Replace any stale healthy status with the correct state.
3. State what is affected and what App Lock can still do.
4. Explain the specific Android handoff before leaving.
5. Store only the originating row and safe progress.
6. On return, recheck the actual state.
7. Restore focus to the originating row and announce the result.
8. Run a fresh protection check before showing Protected.

#### 10.4 Reboot, Force-Stop, and Vendor Restrictions

After reboot, sessions are invalid and protection health shall be re-established from current evidence. After force-stop, Android may prevent App Lock from operating until it is started again; when App Lock next opens, the interface shall explain the limitation and require a fresh check. Vendor battery or background restrictions shall be described only when detected or relevant, with a device-appropriate guidance path and no universal claim of repair.

#### 10.5 Recovery Completion

Opening settings is not completion. Granting access is not completion until App Lock verifies it. Restoring one access is not completion when another required condition remains unavailable. A healthy status is shown only after the complete current check succeeds.
