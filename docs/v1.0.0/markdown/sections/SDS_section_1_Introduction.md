# Software Design Specification

## Version 1.0.0

## 1. Introduction

### 1.1 Purpose

This document defines how the version 1.0.0 application is divided into software responsibilities, how those responsibilities interact, which component owns each security-relevant state, and how the application behaves when Android cannot provide a required capability.

The design supports one primary outcome: a user can select an eligible application on the same phone and require an App Lock PIN, or an eligible biometric prompt with PIN fallback, before that application is allowed to continue.

The specification is deliberately limited. It describes enough structure to implement, test, and support the retained App Lock behavior without carrying forward frameworks, data stores, workflows, or permissions for features that are outside this release.

### 1.2 Intended readers and use

This specification is intended for software implementation, security review, database design, user-experience design, and verification. It should be used to answer the following questions:

- Which software responsibility makes a decision?
- Which information is persistent and which is transient?
- Which Android capability is required?
- What state change follows a user action or Android lifecycle event?
- What happens when a dependency fails or a permission is revoked?
- Which behavior must be verified on supported phones?

Source-code organization may use fewer physical modules than the logical responsibilities shown here. The required outcome is separation of ownership and predictable interaction, not a prescribed count of packages or objects.

### 1.3 Included scope

| Area | Version 1.0.0 design boundary |
|---|---|
| Devices | Android phones on Android 11–15, API levels 30–35 |
| Presentation | Phone-sized, portrait-first interface with safe and usable landscape behavior |
| Authentication | Numeric App Lock PIN; eligible platform biometrics; PIN fallback; retry delay and temporary lockout |
| Application protection | Eligible-app discovery, selection, foreground detection, lock presentation, unlock session, expiration, and relock |
| Detection | Android Usage Access is the single foreground-application detection baseline |
| Permissions and health | Setup guidance, current capability checks, interruption reporting, and direct recovery handoffs |
| Notifications | Essential privacy-masked service and Action required notifications |
| Settings and help | Relock behavior, biometric preference, essential presentation preferences, protection status, help, and current diagnostics |
| Persistence | Private preferences, Keystore-backed protected preferences, encrypted local relational storage, and safe migration |
| Accessibility | Screen-reader support, focus order, text scaling, contrast, touch targets, and reduced-motion behavior |

### 1.4 Explicit exclusions

Version 1.0.0 does not include:

- a vault, protected files, protected notes, attachments, categories, tags, import, export, or vault search;
- backup packages, restore, cloud synchronization, device transfer, or new-device recovery;
- a recovery password, recovery code, recovery question, or data-preserving forgotten-PIN flow;
- profiles, schedules, time rules, contextual automation, trusted places, trusted networks, or policy conflicts;
- intruder photographs, camera access, event media, or media retention;
- advanced event history, notification history, diagnostic export, telemetry, analytics, or performance-history storage;
- tablets, foldable-specific layouts, Chromebooks, desktop modes, televisions, watches, or vehicle displays;
- Android versions earlier than Android 11;
- work profiles, secondary device users, cloned applications, or parallel application instances;
- password, pattern, device-credential, remote, enterprise, or multi-factor authentication;
- an application Accessibility service or any protection behavior dependent on Accessibility access;
- remote administration, user accounts, network services, or externally managed policy;
- camera, location, nearby-device, contact, or general-storage permissions; or
- a general-purpose background-task, message-bus, plugin, analytics, or reporting framework.

Excluded behavior is not partially supported. Inactive software or storage left from earlier work does not make an excluded capability part of this specification.

### 1.5 Supported-device boundary

Support is based on the phone form factor and the currently available application window, not on a marketing device name. The required interface fits a compact phone window and remains operable in landscape. A supported phone may lack biometric hardware or may have biometrics temporarily unavailable; PIN authentication remains the required fallback.

Testing outside API levels 30–35 may be useful for engineering purposes but does not expand the support claim. A device with modified system software, a disabled required capability, a force-stopped application, or operating-system restrictions that prevent foreground detection or lock presentation is reported as Protection interrupted or Unknown or not verified rather than Protected.

### 1.6 Design assumptions

- The application operates entirely on the local phone and does not require network access.
- Android Usage Access can be enabled by the user through a system settings screen.
- Android permits a visible protection service to perform the retained foreground checks subject to version-specific background limits.
- The application can present its own lock screen when Android permits the retained presentation path.
- Package information is obtained only for launchable applications visible under Android package-visibility rules.
- The Android Keystore and application-private storage are available on supported phones.
- Clearing application data or uninstalling the application removes the local configuration.
- Android may terminate the process, delay background work, restrict activity launch, suppress a notification, or prevent automatic restart after force-stop.

### 1.7 Relationship to companion specifications

The functional requirements define what the application must do. The quality requirements define measurable performance, reliability, accessibility, security, privacy, and compatibility outcomes. The user-experience specification defines screens, journeys, wording, and interaction states. The threat model defines the protected assets, relevant attackers, trust boundaries, and required mitigations. The database design defines the retained persistent information and its lifecycle.

Where an Android limitation prevents an absolute protection claim, this specification requires truthful status and recovery guidance. No companion document should convert that limitation into an unsupported guarantee.
