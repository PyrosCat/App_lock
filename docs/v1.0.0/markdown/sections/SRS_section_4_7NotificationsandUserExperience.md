# Software Requirements Specification

## Version 1.0.0

## Section 7 Notifications and User Experience

#### FR-146 - Notification Management System

The application shall issue only essential App Lock notifications needed to communicate ongoing protection where required by Android, degraded protection, interrupted protection, or action required.

Acceptance criteria:

- Notification text avoids naming protected applications or revealing authentication activity on a locked phone.
- Severity and available action match the current protection-health state.
- Disabling an optional notification does not suppress a notification required for truthful protection or operating-system operation.
- Version 1.0.0 does not read or modify notifications produced by other applications.

#### FR-155 - Security Alerts

The application shall alert the user when a condition directly affects the core protection promise and requires attention.

Acceptance criteria:

- Alerts cover loss of Usage Access, failed lock-presentation readiness, unrecovered core service interruption, and local-data integrity failure.
- The message states the effect and the next safe action in plain language.
- An alert is cleared or updated only after the condition is rechecked.

#### FR-156 - First-Time User Onboarding

The application shall provide a guided initial setup for the complete version 1.0.0 protection path.

Acceptance criteria:

- The flow explains the local nature and limits of App Lock before permissions are requested.
- The flow covers PIN creation, optional eligible biometrics, Usage Access, required lock-presentation readiness, notification permission where applicable, protected-application selection, and a protection check.
- Interrupted setup can resume at the first incomplete required step without preserving an unsafe partial claim.
- Vault, backup, recovery password, automation, intruder, concealment, and notification-access steps are absent.

#### FR-157 - Permission Setup Assistant

The application shall guide the user through only the operating-system capabilities required by version 1.0.0.

Acceptance criteria:

- Usage Access is identified as the single required application-detection baseline.
- Each handoff explains why the capability is needed, what changes if it is denied, and how to return safely.
- Returning from Android settings triggers a fresh verification rather than assuming success.
- The flow does not request an App Lock Accessibility service, camera, location, protected-application notification access, or storage permission for deferred features.

#### FR-158 - Contextual Help System

The application shall provide concise help for core setup, protection, authentication, recovery, and known Android limitations.

Acceptance criteria:

- Help is available from onboarding, protection status, protected-application management, authentication settings, and recovery states.
- Content explains PIN fallback, global relock behavior, Usage Access, interrupted protection, and destructive reset in non-technical language.
- Help does not imply support for excluded devices or features.
