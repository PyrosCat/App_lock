# Non-Functional Requirements

## Version 1.0.0

## 1. Purpose and Quality Boundary

This specification defines measurable quality expectations for version 1.0.0 of an Android phone application that restricts access to selected applications. It applies only to the capability retained in the companion Software Requirements Specification.

Quality acceptance is limited to:

- conventional Android phones on Android 11 through Android 15, corresponding to API levels 30 through 35;
- PIN authentication with optional eligible biometrics and mandatory PIN fallback;
- Android Usage Access as the single application-detection baseline;
- protected-application selection and search;
- lock presentation, package-scoped sessions, cancellation, and global relock behavior;
- required-capability setup and protection-health recovery;
- privacy-preserving App Lock notifications;
- basic settings, help, on-device diagnostics, secure local data, in-place migration, and destructive reset; and
- core phone accessibility and privacy behavior.

No quality obligation is created for Vault, backup, restore, recovery passwords, cross-device migration, profiles, schedules, automation, intruder media, protected-application notification access, advanced event history, diagnostic export, remote telemetry, accounts, cloud services, an App Lock Accessibility service, tablets, foldables, large-screen modes, work profiles, cloned applications, secondary users, Android versions below API 30, or another excluded capability.
