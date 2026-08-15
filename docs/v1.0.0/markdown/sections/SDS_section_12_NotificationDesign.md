# Software Design Specification

## Version 1.0.0

## 12. Notification Design

### 12.1 Purpose and limits

Notifications communicate only the current operation of the retained protection service and conditions requiring user attention. They are not an event log, a record of authentication attempts, or a substitute for the in-app protection-health surface.

### 12.2 Retained notifications

| Notification | When shown | Privacy-safe content | User action |
|---|---|---|---|
| Protection active | A visible protection service is operating because at least one application is selected | States that app protection is active; does not identify protected applications | Opens the authenticated protection dashboard |
| Protection needs attention | Usage Access, service operation, lock presentation, or another required capability is unavailable | States that protection needs attention or is interrupted; does not expose configuration detail | Opens the relevant in-app recovery explanation or Android settings destination |
| Protection stopped | The retained protection service stops unexpectedly while protected selections remain and Android still permits notice | States that protection is not currently active | Opens the authenticated protection dashboard |

The active and stopped notifications are mutually exclusive. Repeated checks for the same unchanged condition update the existing notification rather than generating a stream of notices.

### 12.3 Content and visibility

All notification content is masked by default. It shall not include:

- protected application names, labels, or icons;
- PIN state, failure count, or lockout detail;
- biometric enrollment or result detail;
- database, encryption, or key information;
- a history of applications opened; or
- internal error messages.

The notification title and body remain understandable when shown on a locked phone. An action may navigate to App Lock or an Android settings screen, but it cannot unlock a protected application, change a setting, or remove protection.

### 12.4 Notification permission

On Android versions that require runtime notification permission, the request appears only after an explanation tied to protection operation. Denial is stored only as the user’s Android decision; the application queries the current result when needed.

The application does not repeatedly request permission after denial. It continues to provide in-app status and explains any effect on the visible protection service. Returning from notification settings triggers an immediate recheck.

### 12.5 Delivery and failure

Notification delivery is best effort within Android behavior. A failed delivery does not grant access, change a session, or mark protection healthy. If visible notification capability is required for lawful service operation, inability to provide it changes health to Action required during incomplete setup or Protection interrupted after active protection is lost.

No notification queue, scheduled notification, template database, delivery receipt, archive, history, or analytics store is part of this design.

### 12.6 Accessibility

Notification text is concise, action-oriented, and independent of color or icon alone. Actions have clear labels. Updates avoid unnecessary sound and vibration unless the user-experience requirements identify an interruption as requiring immediate attention.
