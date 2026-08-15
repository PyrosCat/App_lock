# Threat Model

## Version 1.0.0

## 1. Purpose, Scope, and Security Promise

### 1.1 Purpose

This Threat Model defines the security boundary for Version 1.0.0. It identifies the information and behavior that require protection, the attackers and platform conditions that may threaten that protection, the controls required to reduce those threats, the conditions the application cannot control, and the scenarios that must be satisfied before release.

The document is intentionally limited to the core application-locking function. It does not create obligations for features that are not part of Version 1.0.0.

### 1.2 Security Promise

On a supported, non-compromised Android phone, the application shall restrict access to applications selected by the user until the required App Lock authentication succeeds. The application shall maintain that authorization only for the duration permitted by the selected global relock policy. When an Android permission, presentation capability, or execution condition required for protection is unavailable, the application shall report the condition accurately and shall not describe protection as healthy.

Android device unlock and App Lock authorization are separate security boundaries. Unlocking the phone shall not, by itself, authorize access to protected applications.

### 1.3 Included Security Functions

Version 1.0.0 includes the following security-relevant functions:

- required creation and use of a numeric PIN;
- optional use of an eligible Android biometric, with the PIN always available as fallback;
- protected-application discovery, search, selection, removal cleanup, and authenticated configuration changes;
- foreground-application detection through Android Usage Access;
- lock-interface presentation through the required "Display over other apps" system-overlay permission;
- package-scoped authorization sessions governed by one global relock policy;
- immediate, screen-off, and supported timeout-based relock behavior;
- authentication cancellation, retry delay, and lockout;
- permission guidance, protection-health checks, and recovery guidance;
- essential privacy-masked notifications produced by App Lock;
- basic on-device settings, help, current health information, and bounded local diagnostic records;
- protected local credential and configuration storage;
- encrypted database storage, Android Keystore protection, integrity checking, and safe in-place schema migration;
- controlled destructive reset when a forgotten PIN or unrecoverable local-data condition prevents continued use;
- safe behavior during rapid application switching, rapid relaunch, screen-off, process termination, application restart, device reboot, phone multi-window use, picture-in-picture transitions, and recent-app presentation;
- minimum production-package safeguards necessary to avoid weakening the stated security promise.

### 1.4 Excluded Capabilities

Version 1.0.0 does not include:

- a Vault or any private file, photograph, video, or document store;
- backup, restore, export, import, new-device transfer, or cross-device migration;
- a recovery password, backup password, account recovery, security question, or data-preserving forgotten-PIN recovery;
- profiles, schedules, rules, location conditions, connection conditions, or other automation;
- intruder photography, intruder media, or an intruder-event library;
- notification interception, replacement, masking, or history for notifications produced by other applications;
- advanced event history, trend analysis, diagnostic export, reports, analytics dashboards, remote support telemetry, or remote administration;
- an App Lock Accessibility service or an Accessibility-based foreground-detection enhancement;
- device-administrator or device-owner enrollment for uninstall prevention;
- accounts, servers, cloud storage, synchronization, advertising, or routine application network communication;
- pattern, knock-code, device-credential, decoy, or disguised authentication;
- tablet, foldable-posture, large-screen, desktop, Chromebook, television, automotive, wearable, work-profile, cloned-application, secondary-user, or manufacturer-specific dual-app support;
- Android 8, Android 9, or Android 10 support.

The exclusions are security boundaries. They shall not be presented as partially available behavior.

### 1.5 Security Guarantee Boundary

The security promise assumes:

- the Android operating system, package identity, sandbox, permission model, and Keystore behave according to their documented security boundaries;
- the phone is not rooted and the operating system or firmware is not compromised;
- the user has granted the required Android capabilities and has not force-stopped, uninstalled, or cleared the data of App Lock;
- App Lock receives the execution opportunities that Android makes available to an ordinary installed application;
- a person who knows the current App Lock PIN is authorized to change the protected-application selection and protection settings;
- the platform biometric prompt reports biometric results correctly.

The application cannot guarantee uninterrupted protection after force-stop, uninstall, application-data clearing, operating-system compromise, or removal of a required Android capability. It shall explain these limitations without implying that a warning prevents the underlying exposure.

### 1.6 Security Objectives

Version 1.0.0 shall satisfy the following objectives:

1. A protected application shall not receive App Lock authorization without successful PIN or eligible biometric authentication, unless a valid session for that same protected application already permits access.
2. Failed, cancelled, expired, or interrupted authentication shall not create authorization.
3. Authorization shall end when required by the selected global relock policy.
4. Restart, process termination, device reboot, PIN change, and destructive reset shall invalidate authorization.
5. The PIN, cryptographic keys, database secret, and protected configuration shall not be stored or disclosed in plaintext.
6. Missing, corrupt, or partially migrated security data shall not be silently treated as a valid permissive configuration.
7. Required permission or protection-path loss shall not be reported as healthy protection.
8. App Lock screens, notifications, recent-app previews, and diagnostics shall avoid unnecessary disclosure of security-sensitive information.
9. Android limitations that cannot be prevented shall be stated as residual risk rather than described as solved.

---
