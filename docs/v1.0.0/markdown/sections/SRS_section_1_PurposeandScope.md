# Software Requirements Specification

## Version 1.0.0

## 1. Purpose and Scope

This specification defines the required behavior of version 1.0.0 of an Android application that restricts access to selected applications on supported phones. The release is intentionally limited to the smallest complete product that can make and verify that protection promise.

The intended readers are those responsible for design, delivery, verification, security assessment, and acceptance of the application. This document states observable behavior and release boundaries. It does not prescribe internal source-code structures.

### 1.1 Included capability

Version 1.0.0 includes:

- conventional Android phones running Android 11 through Android 15, corresponding to API levels 30 through 35;
- a required numeric PIN;
- optional eligible device biometrics with mandatory PIN fallback;
- selection, search, enabling, disabling, and cleanup of protected applications;
- protection presentation, authentication sessions, cancellation, and global relock behavior;
- Android Usage Access as the single application-detection baseline;
- the Android "Display over other apps" (system overlay) permission, used to present the lock over a protected application;
- setup and recovery guidance for required operating-system capabilities;
- truthful protection-health states and privacy-preserving App Lock notifications;
- basic settings, help, local diagnostics, secure local storage, safe migration, and destructive reset; and
- core phone accessibility and privacy protections.

### 1.2 Excluded capability

Version 1.0.0 does not include:

- Vault storage or file-management features;
- backup, restore, recovery passwords, preserved-data credential recovery, or transfer to another device;
- profiles, schedules, automation, trusted-device rules, or behavioral recommendations;
- intruder photographs, location, event media, or event-history screens;
- disguises, stealth launch, fake screens, hidden gestures, or decoy credentials;
- access to, interception of, or history for notifications produced by protected applications;
- pattern, knock-code, or device-credential authentication;
- per-application credentials, timeouts, schedules, or profiles;
- diagnostic export, remote telemetry, long-term event history, reports, or trend dashboards;
- an App Lock Accessibility service;
- accounts, servers, cloud synchronization, remote commands, or routine application network traffic;
- tablets, foldables, large-screen layouts, desktop modes, television, automotive, wearable devices, work profiles, cloned applications, or secondary users; or
- Android versions below API level 30.

### 1.3 Release interpretation

The application must not claim that protection is active when a capability required for the protection path is unavailable: Usage Access for foreground detection, or the "Display over other apps" overlay permission used to present the lock. Because the lock is drawn as a system overlay, a background service can raise it reliably; without that permission the application cannot enforce protection and must not report a protected state. A missing optional biometric capability does not reduce protection because PIN remains available.

Portrait is the primary presentation. Landscape must remain secure and usable. Split-screen, picture-in-picture, and recent-app transitions on supported phones must fail safely, but do not require a separately optimized experience.

Essential notifications are notifications produced by App Lock itself for ongoing protection where required, degraded protection, interrupted protection, or action required. They do not include notification content from another application.

Forgotten-PIN handling is destructive. It removes local App Lock credentials and configuration and returns the user to initial setup. It does not preserve protected-application selections and cannot retrieve or bypass the previous PIN.
