# Software Design Specification

## Version 1.0.0

> This specification defines the software design for a local-only App Lock on Android phones running Android 11 through Android 15. It creates no design obligation for capabilities listed as excluded.

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

## 2. Design Principles

### 2.1 Single ownership of security state

Every security-relevant state has one authoritative owner:

- credential and lockout state belongs to authentication;
- unlock-session state belongs to session handling;
- the selected protected-package set belongs to protected-application persistence;
- current foreground identity belongs to the Usage Access detection path;
- the protection decision belongs to protection logic;
- permission and service facts belong to protection-health evaluation; and
- visible screen state belongs to presentation.

One responsibility may observe another state but must not create a competing copy that can diverge. In particular, the user interface does not decide that authentication succeeded, and a detector callback does not decide that access is allowed.

### 2.2 Separation of decision and mechanism

Android integration reports facts and performs requested platform actions. It does not own App Lock policy. Core logic determines whether a reported foreground package is selected, whether a current session is valid, and whether authentication is required. Presentation displays the resulting state.

This separation allows the same decision rules to be tested without running Android services or screens and prevents platform callbacks from bypassing authentication.

### 2.3 Deterministic state transitions

The same relevant facts must produce the same decision. A protected package with no valid package-scoped session requires authentication. An unprotected package does not. A failed or cancelled authentication does not create a session. Permission loss does not preserve a healthy status.

Concurrent or repeated foreground reports for the same package are coalesced so that only one lock presentation is active. A stale result from an earlier target cannot unlock a later target.

### 2.4 Secure and private defaults

The default relock behavior is immediate. Notifications are masked. Sessions are memory-only. The raw PIN is short-lived and never persisted. Protected package choices are treated as confidential. Diagnostic output contains no PIN, verifier, encryption material, protected package name, or detailed biometric information.

Failure to read a credential, evaluate lockout, open protected storage, or validate a session cannot be interpreted as successful authentication.

### 2.5 Truthful protection

The application distinguishes between Not configured, Partially configured, Protected, Degraded, Protection interrupted, Action required, and Unknown or not verified conditions. It does not claim that selected applications are protected when Usage Access is disabled, the protection service is not operating, the protected-app store cannot be read, or the lock screen cannot be presented.

Protection health represents current evidence, not user intent. Selecting an application is not by itself evidence that protection is active.

### 2.6 Minimal persistent state

Only information that must survive process death is persisted. Unlock sessions, current foreground identity, active lock-presentation state, and current health are held in memory or derived from Android. Installed-application names and icons are read from Android rather than copied into an authoritative catalog.

No data store is created for a capability outside version 1.0.0.

### 2.7 Lifecycle-aware behavior

The design assumes that activities and processes will be recreated. It explicitly handles screen-off, process death, reboot, permission revocation, service interruption, package installation and removal, and return from Android settings.

State restoration preserves harmless navigation and selection context when practical, but never restores an authenticated session after process death or reboot.

### 2.8 Proportionate abstraction

An abstraction is required where it isolates Android, encrypted storage, biometrics, time, or another dependency that affects security and testing. A separate abstraction is not required merely to support a hypothetical provider or feature. Version 1.0.0 uses direct in-process collaboration and observable state rather than a general event-routing framework.

## 3. Constraints and Assumptions

### 3.1 Android and phone constraints

The minimum supported platform is Android 11 and the maximum verified platform is Android 15. The application targets phone-sized displays. It does not provide expanded navigation, dual-pane layouts, hinge-aware presentation, external-display behavior, or desktop-window optimization.

Android is free to reclaim the process and to apply manufacturer-specific battery restrictions. The design therefore treats a running process as temporary and security-relevant configuration as durable. It does not assume that a foreground service is immortal.

### 3.2 Foreground-detection baseline

Usage Access is the sole foreground-application detection baseline. The application directs the user to the Android Usage Access screen, rechecks access when the user returns, and reads recent usage information to identify the current launchable foreground package.

The application shall not declare, request, or operate an Accessibility service. It shall not show Accessibility as an optional enhancement, fallback, setup step, or recovery path.

Foreground checks run only when setup is sufficiently complete and at least one application is selected for protection. The check interval is bounded to meet the applicable detection-response requirement without an unbounded busy loop. When no applications are selected, protection polling stops.

Usage data is used only for the immediate protection decision. The application does not retain a history of applications opened, durations of use, or user activity.

### 3.3 Lock-presentation constraints

The lock screen is an application-owned, non-exported surface dedicated to authenticating access to the current protected package. It does not appear in recents, does not expose protected package content in its preview, and prevents navigation into the protected application until authentication succeeds or the user leaves the protected task.

Android background-activity limits may prevent or delay presentation in some device states. The software detects a presentation failure or timeout where feasible and reports Protection interrupted. It does not describe the lock screen as a system-level security boundary and does not guarantee that the protected application can never become briefly visible under every operating-system condition.

Only one lock screen may be active. A presentation request contains the target package and a fresh request identity. Completion is accepted only for the current request and current target.

### 3.4 Notification and service constraints

The retained protection service is visible to Android and the user through the required persistent notification. On Android versions requiring runtime notification permission, the application explains why the permission supports protection status and requests it in context.

Denial does not produce repeated prompts. The in-app health surface remains available. If Android allows the service to operate while suppressing the visible notification, health is Degraded; if the selected service mode cannot lawfully operate, health is Action required or Protection interrupted.

### 3.5 Package visibility

The application queries only launchable applications allowed by Android package-visibility rules. It does not request visibility into all installed packages. Its own package and packages without a user-launchable entry are excluded from selection.

Application labels and icons are untrusted display data. They are rendered safely and do not determine identity. The package identifier supplied by Android is the protection identity.

### 3.6 Time and clock behavior

PIN lockout state must survive process death and reboot. Unlock-session grace timing is memory-only and uses a monotonic elapsed-time source while the process remains alive. Wall-clock changes must not extend a session or reduce an active lockout beyond the defined upper bound.

No schedule, calendar, time zone, or daylight-saving rule affects protection.

### 3.7 Offline and local-only operation

All retained functions work without a network connection. The application contains no account session, server token, remote policy, cloud database, remote log destination, or online recovery dependency.

### 3.8 Accessibility and privacy constraints

All actionable controls meet the retained touch-target requirement and expose clear labels and states. PIN digits are not announced. Biometric prompts use platform accessibility behavior. Focus returns to the meaningful recovery control after an error. Text scaling must not hide the PIN fallback, permission action, or protection status.

The application lock screen, authenticated settings, and any view showing protected-app choices are excluded from screenshots and recents previews where Android permits. Notifications remain masked regardless of protected package.

## 4. Software Organization

### 4.1 Logical areas

| Logical area | Primary responsibility | Persistent state |
|---|---|---|
| Presentation | Screens, navigation, input, visible loading/error/empty states, accessible feedback | Harmless preferences only |
| Authentication and session | PIN and biometric result handling, retry control, lockout, package-scoped sessions | Protected verifier and lockout state; no persistent session |
| Protection | Foreground-target evaluation, session decision, lock request, relock | None |
| Protected applications | Eligible-app discovery and selected package set | Protected package identifiers |
| Protection health | Current capability, service, presentation, and storage assessment | None |
| Android integration | Usage Access queries, biometric prompt, system settings handoffs, lifecycle signals, notification delivery | None beyond Android-owned state |
| Persistence and security | Private settings, encrypted relational data, protected preferences, key use, migration | As defined by the database specification |
| Help and current diagnostics | User-readable status and recovery guidance | None |

These are logical boundaries. A small implementation may combine adjacent responsibilities when ownership remains clear and tests can verify the boundary.

### 4.2 Dependency direction

Presentation invokes application operations and observes results. Application operations use authentication, protection, and protected-application rules. Those rules use narrow persistence and Android boundaries. Storage and Android implementations do not call into screens.

The permitted direction is:

1. presentation;
2. application operation;
3. core authentication or protection decision;
4. persistence or Android boundary; and
5. local platform facility.

Reverse notification occurs through returned results, observable state, or a narrowly scoped callback. No component reads another component’s private mutable state.

### 4.3 State ownership

| State | Authoritative owner | Consumers |
|---|---|---|
| PIN configured | Protected authentication storage | Onboarding, authentication, health |
| Failed-attempt count and lockout deadline | Authentication | PIN screen, settings authentication |
| Biometric preference | Protected settings | Authentication, settings |
| Biometric eligibility | Android biometric capability query | Authentication, status |
| Protected package set | Encrypted relational persistence | Protection, application list, health |
| Current foreground package | Usage Access detection cycle | Protection only |
| Active package session | In-memory session handling | Protection |
| Current lock request | Protection presentation coordination | Lock screen |
| Permission and service state | Protection-health evaluation | Dashboard, onboarding, notifications |

### 4.4 Initialization order

Startup follows a fail-safe order:

1. initialize protected storage and obtain the database-opening capability;
2. verify schema compatibility and complete any supported migration;
3. load the selected protected-package set;
4. read credential, lockout, biometric, and relock settings;
5. query current Usage Access, notification, and service state;
6. derive protection health;
7. start or resume protection checks only when setup and protected selection require them; and
8. publish visible state to presentation.

If protected storage cannot be opened or migration fails, normal protection operation does not begin with an empty package set. The user receives an unrecoverable local-data message and the option to clear application data through Android.

## 5. Dependency and Communication Design

### 5.1 Android boundaries

Usage Access, package discovery, biometrics, notifications, system settings, boot signals, screen state, and service lifecycle are isolated behind narrow behavior-based boundaries. Core logic receives normalized facts such as the current package identity, permission availability, biometric result, or screen-off event.

Android objects and errors do not propagate into core state or user-visible text. Each platform failure is translated into a small result category with a defined safe behavior.

### 5.2 Storage boundaries

Ordinary preferences, protected preferences, and the encrypted relational store have separate responsibilities. Authentication cannot read the relational database to obtain a PIN verifier. Protection cannot read raw preference files. Presentation cannot access any storage mechanism directly.

A storage write returns only after the durable operation succeeds. Observable state is updated after success, not optimistically before commit, for protection-reducing changes.

### 5.3 Time dependency

Time used for grace sessions and retry delay is supplied to the owning logic through a testable boundary. Session timing uses process-relative elapsed time. Persistent lockout stores a deadline with defensive handling of clock changes and a maximum reported remaining duration.

### 5.4 Concurrency

Credential verification is performed away from the main presentation thread. Protected-package persistence and snapshot changes are serialized. Foreground reports for the same package are coalesced. Only one lock request may be active.

When a foreground target changes during authentication, the earlier completion is discarded unless it still matches the current protected target. When protection is removed while a lock is displayed, the removal is committed first and the lock request is then cancelled in a controlled order.

### 5.5 Failure translation

Dependencies report categories rather than internal exceptions. The retained categories include unavailable, permission required, invalid input, invalid credential, temporarily locked, cancelled, storage unavailable, migration failed, presentation failed, and unexpected local failure.

User-visible wording comes from the presentation layer and gives an action where one exists. Internal detail is redacted.

### 5.6 Dependency exclusions

There is no cloud integration, remote identity provider, telemetry integration, backup provider, file importer, media service, scheduling provider, location provider, network-state automation, generic task registry, or application Accessibility service.

## 6. User Interface Design

### 6.1 Retained screen groups

The presentation contains only the following functional areas:

- welcome and initial setup;
- PIN creation and confirmation;
- Usage Access explanation, Android handoff, and verification;
- optional biometric enablement;
- protected-application selection and search;
- protection dashboard and current health;
- application lock and PIN fallback;
- basic settings, including relock behavior and biometric preference;
- help and current diagnostic status; and
- destructive reset guidance through Android when local credentials or protected storage cannot be recovered.

There are no vault, backup, restore, profile, schedule, automation, intruder-event, event-history, export, or administrative screens.

### 6.2 Presentation state

Each screen exposes a complete state rather than a set of unrelated flags. A screen may be loading, ready, empty, action required, temporarily disabled, or failed. Security-relevant controls remain disabled until the required state is known.

The protected-app list has:

- loading while Android package information and current selections are read;
- ready with selectable eligible applications;
- empty when no eligible launchable applications are available;
- filtered empty when a search has no matches;
- disabled during a committed selection change; and
- error with a retry action when package or protected storage cannot be read.

### 6.3 Navigation

Unauthenticated entry into the application routes to setup if no PIN exists or to PIN authentication if a PIN exists. A deep link from a protection notification routes through the same authentication decision before showing protected settings.

Back from the lock screen does not reveal a protected application. It returns the user away from the protected task or leaves the lock in place according to Android navigation behavior. Cancellation never creates an unlocked session.

Returning from Android Usage Access or notification settings triggers an immediate capability recheck. The application does not assume that opening a settings screen changed anything.

### 6.4 Sensitive presentation

PIN entry uses masked positions and a randomized or fixed keypad only as specified by the user-experience requirements; this software design does not create a second keypad mode. The complete PIN is held only long enough to verify or create the protected verifier and is cleared from transient input after completion, cancellation, or failure.

Protected-app choices, security status detail, and authentication screens are protected from screenshots and recents previews where Android permits. The lock screen does not display the name or icon of the protected target unless the user-experience specification explicitly requires it and the privacy impact is accepted.

### 6.5 Accessibility

Controls expose purpose, state, and error text through the platform accessibility semantics. Focus begins at the screen title or the first required input, moves in visual order, and returns to the failed field or recovery action after validation.

PIN positions are announced as filled or empty without announcing digits. Lockout remaining time is announced when it changes meaningfully rather than every second. Color is never the sole indicator of Protected, Protection interrupted, or Action required state. Text scaling and landscape layout preserve the PIN fallback, primary recovery action, and system-settings handoff.

This accessibility design concerns the application’s user interface. The application does not provide an Accessibility service and does not request Accessibility access.

### 6.6 Restoration

Harmless list search, scroll position, and unsaved non-sensitive settings may survive screen recreation. PIN digits, biometric results, active lock requests, and authenticated screen access do not survive process death.

After recreation, the application re-evaluates setup, authentication, protected storage, Usage Access, and health before restoring a destination.

## 7. Authentication and Session Design

### 7.1 Authentication model

The App Lock PIN is the root user credential for version 1.0.0. Eligible platform biometrics provide a convenient local authentication path after PIN setup. Biometrics do not replace the PIN, recover a forgotten PIN, or reveal biometric material to the application.

The same retry and lockout state applies when authenticating to enter App Lock settings and when authenticating a protected application with the PIN.

### 7.2 PIN creation

PIN creation proceeds as follows:

1. the user enters a numeric PIN permitted by the requirements;
2. the user enters it again for confirmation;
3. the two values are compared without persistence;
4. a unique random salt and an approved memory-hard verifier are produced away from the main thread;
5. the complete verifier record is committed atomically to Keystore-backed protected storage;
6. temporary PIN material is cleared; and
7. setup continues only after a successful commit.

A mismatch leaves both fields cleared and explains that the entries did not match. A storage or derivation failure leaves the application not configured and provides a retry. A partial verifier is never treated as configured.

### 7.3 PIN verification

Before accepting a PIN attempt, authentication checks the persistent retry state. If a lockout is active, verification does not run and the screen shows the remaining wait.

For an available attempt:

1. input is validated as a permitted numeric length;
2. stored verification parameters and the verifier are read from protected storage;
3. the candidate is evaluated away from the main thread;
4. a successful result resets failure and lockout state;
5. a failed result increments the consecutive-failure count and may begin or extend lockout; and
6. temporary input is cleared.

Five consecutive PIN failures begin a 30-second lockout. Each further failed PIN after the wait doubles the next lockout duration, up to 30 minutes. A successful PIN resets the sequence. The count and lockout deadline survive process death, force-stop, and reboot.

Failure to read or validate the verifier is an authentication-system failure, not an invalid PIN and not success. The user is directed to the local-data recovery guidance.

### 7.4 Biometric authentication

Biometric authentication is offered only when:

- a PIN is configured;
- the user has enabled biometric use;
- the platform reports an eligible biometric authenticator and enrollment;
- no application PIN lockout is active; and
- the current screen and lifecycle state can safely present the platform prompt.

The platform prompt identifies App Lock authentication and exposes PIN fallback in the surrounding application screen. A successful platform result is accepted only for the current request. Cancellation, temporary unavailability, changed enrollment, permanent lockout, or other failure returns to the PIN path.

The application stores only the user’s biometric preference. It does not store a biometric template, biometric identifier, enrollment count, or detailed failure history.

### 7.5 Session scope

A successful protected-app authentication creates an in-memory session scoped to that package. Unlocking one protected application does not automatically unlock another.

The session contains only the protected package identity, successful-authentication time, and information needed to evaluate the selected relock behavior. It contains no PIN, verifier, biometric result object, or reusable platform token.

### 7.6 Relock behavior

Version 1.0.0 supports three existing relock choices:

| Choice | Behavior |
|---|---|
| Immediate | Leaving the protected application invalidates its session immediately. This is the default. |
| Ten-second grace | Leaving starts a ten-second grace period for that package. Returning within the period may reuse the session; returning after it requires authentication. |
| Screen-off | Leaving does not invalidate the package session, but turning the screen off clears all sessions. Process death, reboot, PIN change, and security failure also clear all sessions. |

Every choice clears all sessions when the screen turns off. No choice survives process death or reboot. A setting change applies to future evaluation and invalidates sessions if retaining them could weaken the newly selected behavior.

### 7.7 Settings authentication

When a PIN exists, entry into protection-reducing settings requires current authentication. This includes deselecting a protected application, disabling biometric use when confirmation is required by the user-experience design, changing the PIN, and opening sensitive diagnostic detail.

Settings authentication does not create a protected-app session. It creates only an authenticated settings state limited to the active application process and current sensitive-settings flow. It ends when the flow is completed or cancelled, App Lock leaves the foreground, the screen turns off, the process terminates, the PIN changes, or a security-relevant error occurs. It is never persisted or restored.

### 7.8 PIN change and forgotten PIN

Changing the PIN requires a successful current-PIN entry specifically for that change; biometric success and a prior authenticated settings state are insufficient. The replacement verifier is written atomically before the old verifier is removed. Successful replacement clears retry state, the authenticated settings state, and all protected-app sessions.

There is no data-preserving forgotten-PIN path. The user may clear application data through Android and complete setup again. Protected selections, settings, and credentials are not recoverable after that action.

### 7.9 Authentication states

| State | Entry condition | Permitted next states |
|---|---|---|
| Not configured | No complete verifier exists | PIN setup, unrecoverable local-data error |
| Authentication required | A protected operation has no valid session | PIN entry, biometric prompt, cancelled |
| Authentication in progress | A current request is being evaluated | Authenticated, invalid credential, cancelled, unavailable |
| Temporarily locked | PIN retry deadline is in the future | Authentication required after expiry |
| Authenticated | Current request succeeded | Session created, settings access, invalidated |
| Unavailable | Verifier or protected storage cannot be used | Retry, clear-data guidance |

## 8. Lock Detection and Enforcement Design

### 8.1 Responsibilities

Protection logic is responsible for:

- receiving the current foreground package from the Usage Access path;
- ignoring ineligible, unprotected, self-owned, and stale package reports;
- checking current protection health and selected-package state;
- evaluating the package-scoped session;
- requesting one lock presentation when authentication is required;
- accepting only the result for the current target;
- creating the session after successful authentication;
- applying the selected relock behavior; and
- reporting an interruption when detection or presentation cannot operate.

It does not verify PINs, query Android package metadata for display, modify the selected package set, store event history, or evaluate schedules and contexts.

### 8.2 Foreground detection

While protection is required, the visible protection service performs bounded Usage Access queries and normalizes the most recent foreground result to a package identity and observation time. A result that is missing, too old, internally inconsistent, or outside the visible launchable set is treated as unknown rather than a protected-app launch.

The detector does not record an application-use timeline. It holds only the current and immediately previous package identities needed to suppress duplicates and apply relock when the user leaves a protected application.

### 8.3 Protection decision

For each valid current package:

1. ignore the App Lock package and its lock surface;
2. determine whether the package is in the current protected set;
3. if it is not protected, notify session handling that the previous protected app was left and take no lock action;
4. if it is protected, verify that protection health is sufficient to attempt presentation;
5. evaluate whether that package has a valid session under the selected relock behavior;
6. if valid, allow continuation without a new prompt;
7. if invalid, create one current lock request and request presentation; and
8. accept success only when the completion matches the current request and current foreground protected package.

Missing configuration, unknown storage state, or a failed dependency never yields an allow result. Where Android prevents enforcement, the outcome is interruption with truthful user notice rather than a false successful protection decision.

### 8.4 Duplicate and rapid-transition handling

Repeated reports for the same protected package must not create repeated lock screens. A lock request remains current until it succeeds, is cancelled, times out, or becomes stale because the foreground target changed.

If the user rapidly switches among applications:

- a result for a prior package is ignored after the target changes;
- an existing valid session is evaluated only for its own package;
- leaving a protected package applies its relock choice once;
- the application lock surface is not treated as a new protected target; and
- returning to the same package during an active request reuses that request rather than stacking another screen.

### 8.5 Lock presentation

The lock surface is presented as a system overlay drawn with the "Display over other apps" permission. This is a design constraint, not a styling choice: because detection runs in a background service, App Lock cannot depend on launching an Activity to cover the protected application — Android 10+ background-activity-launch restrictions make a background Activity launch unreliable — so the lock is drawn as an overlay window a background component can raise deterministically. The overlay permission is therefore required for enforcement; without it the protection path cannot complete and health reports Action required (§8.7 of this document).

The lock surface receives only the current protected target identity and request identity. It retrieves no protected-application content. It immediately checks whether the request is still current and whether a session was created before rendering.

The surface blocks interaction with the protected task while present, removes sensitive previews from recents where Android permits, offers biometric authentication when eligible, and always exposes PIN fallback. Back, home, cancellation, process recreation, or authentication failure does not send a success result.

### 8.6 Relock events

Relock is evaluated when:

- the current protected package leaves the foreground;
- the ten-second grace period expires;
- the screen turns off;
- the process terminates;
- the phone reboots;
- the PIN changes;
- protected storage becomes unavailable;
- a security-relevant state becomes inconsistent; or
- protection is explicitly disabled for that package, after authenticated confirmation.

No schedule, network, location, Bluetooth, charging, calendar, or trusted-context event participates.

### 8.7 Protection states

| State | Meaning |
|---|---|
| Inactive | Setup is incomplete or no application is selected |
| Monitoring | Usage Access and the protection service are available and foreground checks are active |
| Unprotected target | The current package is not selected |
| Authentication required | The current package is selected and lacks a valid session |
| Lock presented | One current authentication surface is active |
| Temporarily unlocked | The current package has a valid in-memory session |
| Action required | A user-correctable capability is unavailable |
| Protection interrupted | Detection or lock presentation is not functioning while protected selections exist |
| Unknown or not verified | Current facts cannot yet be verified |

### 8.8 Failure behavior

Usage Access revocation stops protection decisions and changes health to Action required during incomplete setup or Protection interrupted after active protection is lost. Loss of the overlay permission is treated the same way: the lock cannot be presented, so protection cannot be enforced and health becomes Action required or Protection interrupted rather than a false protected state. A service stop, repeated detector failure, or lock-presentation failure invalidates pending success, retains no session, and changes health to Protection interrupted.

Database unavailability preserves the last known in-memory protected set only for the current process if it was loaded from a verified database. After process recreation, an unreadable store does not become an empty unprotected list; normal operation is blocked and local-data recovery guidance is shown.

## 9. Protected Application Management Design

### 9.1 Application discovery

The application requests the set of user-launchable applications visible under Android package rules. For each eligible result it uses the package identifier as identity and obtains the current label and icon for display.

The application excludes itself, its lock surface, packages without a launchable entry, and any platform package explicitly identified as unsafe or meaningless to protect. It does not request visibility into hidden packages.

### 9.2 Selection model

Record existence in the protected-app store means that the package is selected for protection. There is no group, schedule, category, rule assignment, exception, priority, or separate enabled state unless a retained storage migration requires temporary compatibility.

The list combines current installed-app information with the stored selected package identifiers. Display metadata is refreshed from Android and is not authoritative persistent data.

### 9.3 Search and ordering

Search is performed over the current display label and, where the user-experience design permits, the package identifier. It does not require a separate searchable database catalog. Matching is case-insensitive and updates locally without network access.

The default order is a stable user-readable label order. Selected applications may be shown first if that behavior is defined by the user-experience specification. Search and sorting do not alter protection state.

### 9.4 Enabling protection

Enabling protection proceeds as follows:

1. confirm that setup and required Usage Access are understood;
2. validate that the package is still installed and eligible;
3. insert the unique package identifier atomically;
4. update the in-memory protected set only after commit;
5. ensure the protection service is requested when the first package becomes protected; and
6. refresh protection health.

A duplicate selection produces the already-selected state and does not create another record.

### 9.5 Removing protection

Removing protection is a protection-reducing action and requires current authentication. After confirmation:

1. delete the package identifier atomically;
2. update the in-memory protected set after commit;
3. invalidate any session and pending lock request for that package;
4. stop foreground checks when no protected packages remain; and
5. refresh health and the essential service notification.

A failed deletion leaves the application protected and explains that the change was not saved.

### 9.6 Installation, update, and removal

Newly installed and reinstalled applications are not protected until selected. An application update that preserves the package identifier preserves protection. A confirmed uninstall removes the active protected record and any session for that package.

If Android package information is temporarily unavailable, the stored record is not silently deleted. The application retries discovery and shows unavailable metadata without weakening the stored selection. Removal occurs only after Android confirms the package is no longer installed.

### 9.7 Privacy

The protected-app list is confidential configuration. It is stored in the encrypted relational database, omitted from notifications and logs, excluded from backup, and displayed only after authentication when a PIN exists.

## 10. Secure Vault

### 10.1 Version 1.0.0 boundary

Version 1.0.0 does not provide a secure vault or retain user files, notes, passwords, images, attachments, categories, tags, searchable vault metadata, vault sessions, or vault-specific keys.

No vault interface, storage entity, import/export path, background operation, notification, recovery flow, or verification obligation is created by this specification. Inactive vault-related software or schema objects, if present, remain outside supported behavior and must not be reachable from the version 1.0.0 interface.

## 11. Scheduling and Automation

### 11.1 Version 1.0.0 boundary

Version 1.0.0 does not include profiles, schedules, time windows, contextual conditions, automation actions, conflict resolution, execution history, or automated policy changes.

Protection depends only on the selected protected-package set, current protection health, and the package-scoped session under the chosen relock behavior. Screen-off, reboot, process death, permission change, and session expiration are security lifecycle events, not automation features.

No scheduler, rule database, trigger service, execution queue, automation notification, or automation recovery flow is required.

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

## 13. Security Services Design

### 13.1 Security boundary

Security support is limited to the controls required for PIN authentication, biometric mediation, retry resistance, encrypted local configuration, private presentation, and safe failure. It does not form a general policy platform.

The trusted local boundary contains:

- Android application sandboxing;
- Android Keystore protection for locally stored encryption material;
- protected preferences for credential and lockout information;
- an encrypted relational database for protected package identifiers;
- platform biometric authentication;
- the application-owned authentication and protection decisions; and
- private application screens.

Usage Access and package information are Android-provided inputs. They are validated and minimized but are not trusted to make an App Lock decision.

### 13.2 Credential protection

The raw PIN is never persisted, logged, included in diagnostics, placed in an application backup, or sent outside the phone. PIN verification uses a unique random salt and an approved memory-hard derivation. The stored verifier contains only the information required to verify later attempts and interpret its format.

Verifier creation and evaluation occur away from the main thread. Temporary character and byte representations are kept for the shortest practical lifetime and are cleared after use where the runtime allows.

An older supported verifier format may be upgraded after successful PIN authentication. Upgrade is atomic: a failed replacement leaves the verified prior record usable rather than leaving an unreadable partial credential.

### 13.3 Key and database protection

The encrypted relational database is opened using random material protected by Android Keystore-backed storage. Key material is never stored in the relational database, displayed, exported, or written to logs.

Loss or invalidation of the Keystore protection means the encrypted database cannot be treated as readable. Version 1.0.0 has no recovery key or backup. The user is told that the local configuration must be cleared and created again.

### 13.4 Biometric security

The application delegates biometric collection and matching to the Android platform prompt. It receives only the completion category required to continue or return to PIN.

Biometric success is accepted only for a current authentication request. A result received after the lock request changed, the screen was destroyed, or the target package changed is ignored. PIN fallback remains present even if the user previously enabled biometrics.

### 13.5 Authorization boundaries

Authentication and authorization are distinct. Successful PIN or biometric verification creates only the session appropriate to the current request:

- protected-app authentication creates a package-scoped session;
- PIN or eligible biometric settings authentication permits the current protected settings flow other than PIN replacement; and
- neither grants access to another package or persists across process death.

Protection-reducing operations verify the current authenticated settings state before changing storage. PIN replacement separately requires current-PIN entry.

### 13.6 Sensitive data handling

The following information is treated as confidential:

- the protected package set;
- PIN verifier and verification parameters;
- failure count and lockout deadline;
- biometric preference;
- database-opening material;
- current foreground package while being evaluated;
- active lock target; and
- detailed protection-health causes.

Only the component that requires a value receives it. User-visible notifications and ordinary logs use generalized wording. No protected package name is placed in a diagnostic record.

### 13.7 Input and interface protection

PIN input accepts only the permitted numeric format and length. Package identifiers originate from Android package information and are checked for valid form before persistence. Settings values are selected from defined options rather than accepted as unrestricted text.

The lock surface is not exported to other applications. External intents cannot provide a success result or construct an authenticated session. Navigation parameters are treated as untrusted until checked against the current lock request.

### 13.8 Security failure behavior

Cryptographic, Keystore, protected-preference, database-opening, and verifier-format failures do not degrade into plaintext storage or a bypass. Authentication remains unavailable and protected settings remain closed.

An unavailable optional biometric capability falls back to PIN without weakening protection. An unavailable required foreground detector or lock presenter changes protection health to Protection interrupted.

### 13.9 Security exclusions

Version 1.0.0 does not require root detection, remote attestation, enterprise device posture, independent security-event storage, audit-history export, key synchronization, remote revocation, or a second authentication provider. The application does not request an Accessibility service as a security control.

## 14. Data Access Design

### 14.1 Storage boundaries

The software uses three persistent storage boundaries and one transient boundary:

| Boundary | Information | Access |
|---|---|---|
| Private preferences | Relock choice, haptic and reduced-motion preferences, and other ordinary version 1.0.0 settings | Settings and presentation through one settings boundary |
| Keystore-backed protected preferences | PIN verifier, verifier format, retry state, biometric preference where protected, and database-opening material | Authentication and storage initialization only |
| Encrypted relational database | Unique protected package identifiers | Protected-application persistence only |
| Process memory | Package sessions, authenticated settings state, current foreground target, active lock request, current health, and current diagnostics | Owning runtime responsibilities only |

Screens, Android callbacks, and notifications do not read or write these stores directly.

### 14.2 Protected-application access

The protected-application persistence boundary supports only the retained operations:

- read the complete protected package set;
- determine whether a package is selected;
- add a unique package identifier;
- remove a package identifier; and
- observe the committed set needed by presentation and protection.

Installed-app discovery and search do not become database queries. Display labels and icons remain Android-owned current information.

### 14.3 Settings access

Settings are read through one validated snapshot with documented defaults. An unknown or unavailable stored value falls back to a safe supported value, with immediate relock as the relock default.

Writing a setting validates that the value is one of the supported choices. A failed write leaves the last durable value active and tells the user that the change was not saved.

### 14.4 Authentication storage access

Credential and lockout operations are atomic at the logical level. PIN setup never exposes a configured state until a complete verifier record exists. Failure counting and lockout deadline are updated together as one authentication outcome.

Authentication storage returns success, not configured, unavailable, or invalid-format outcomes. It does not return internal paths, encryption metadata, or platform exceptions to presentation.

### 14.5 Transaction and snapshot consistency

Protected package addition and removal commit before the in-memory protection snapshot changes. When commit succeeds but snapshot refresh fails, protection re-reads the authoritative set before another decision.

A failed database write never produces a temporary unprotected state. If a user requests removal and the write fails, the package remains protected.

### 14.6 Caching

The in-memory protected set is a runtime snapshot, not an independent source of truth. It is loaded from the verified database at startup and updated after commits. It is discarded on process death.

Application labels and icons may use short-lived presentation caching already provided by the platform or user-interface toolkit. No persistent app-metadata cache is required.

### 14.7 Excluded data access

There are no version 1.0.0 repositories or stores for vault data, schedules, automation, backup, recovery material, notification history, security events, diagnostic history, metrics, app groups, or media.

## 15. Background Processing Design

### 15.1 Purpose

Background processing exists only to support the retained Usage Access foreground check, protection continuity within Android limits, session invalidation on relevant lifecycle events, and protection-health reporting.

It is not a general job-processing facility.

### 15.2 Protection-service lifecycle

The visible protection service is requested when:

- a complete PIN configuration exists;
- at least one application is selected; and
- the application has the Android capabilities required to attempt foreground detection.

It stops when no applications remain selected or the local configuration is reset. If Usage Access is revoked, it stops protection checks that cannot produce a reliable target and reports action required rather than repeatedly querying.

### 15.3 Detection cycle

Each cycle:

1. confirms that protection is still required;
2. queries the recent Usage Access information needed to identify the current foreground package;
3. validates freshness and package identity;
4. passes the normalized current target to protection logic;
5. waits for the bounded interval; and
6. repeats while service and lifecycle conditions permit.

Cycles do not write usage history, metrics, task records, or diagnostic timelines. A transient query failure uses a bounded retry. Repeated failure changes protection health to Protection interrupted and avoids a tight retry loop.

### 15.4 Process death and restart

Process death clears all sessions, current-target state, and lock requests. When Android later recreates the application or service, protected storage is verified and the protected set is loaded before detection resumes.

The design does not assume immediate automatic recreation. Until current service operation and a recent foreground check are verified, health is Unknown or not verified or Protection interrupted rather than Protected.

### 15.5 Reboot

Reboot clears every session. After boot, the application may request resumption of the retained visible protection service only as Android permits and only when protected selections exist. If Android defers or prevents service start, the next application entry reports action required and provides the appropriate recovery instruction.

The application does not restore a lock request or foreground identity from before reboot.

### 15.6 Force-stop

Android force-stop prevents application components from restarting until the user explicitly opens the application again. Version 1.0.0 does not claim protection during that interval.

On the next launch, the application reopens protected storage, clears sessions, rechecks Usage Access and notification capability, requests the protection service where permitted, and reports whether protection has resumed.

### 15.7 Screen state

Screen-off clears all package sessions for every relock choice. Screen-on does not create a session and does not assume that the prior foreground target remains current. The next valid Usage Access observation determines the target.

### 15.8 Resource limits

Foreground queries and protection decisions remain lightweight. Credential derivation, database migration, package discovery, and other potentially expensive work run outside the main thread. Background retries are bounded and no network, camera, location, Bluetooth, media, backup, or analytics work is scheduled.

### 15.9 Excluded background facilities

There is no durable task record, generic worker registry, task priority service, execution history, checkpoint store, recurring maintenance schedule, automation dispatcher, backup worker, event uploader, or metrics aggregator.

## 16. Database Interaction Design

### 16.1 Database role

The encrypted embedded relational database is the authoritative persistent store for the selected protected package identifiers. It is not used for credentials, sessions, settings, installed-app metadata, histories, notifications, diagnostics, or excluded features.

### 16.2 Opening sequence

Before normal access, the application first identifies whether local storage is absent, current encrypted storage, or an explicitly supported earlier source format. A clean installation creates the current encrypted store. A current encrypted source follows the normal opening path below. A supported earlier source follows the migration sequence in the Database Design Specification before normal encrypted opening. An unknown source stops safely.

For a current encrypted source:

1. obtain the Keystore-protected database-opening material;
2. open the encrypted database in private application storage;
3. read the schema version;
4. complete any explicitly supported ordered migration;
5. verify that the protected-package collection and uniqueness constraint are usable; and
6. load the selected package identifiers.

Normal protection does not start from an Unknown or not verified state or a partially migrated database.

### 16.3 Operation contract

Database operations use parameterized statements through the retained persistence boundary. Package identifiers are validated before insertion. Duplicate insertion produces the already-present final state. Removal of an absent identifier produces the absent final state.

The protected-package set is small enough to load as a complete in-memory snapshot. No pagination, join, full-text search, sorting index, or generic query description is required.

### 16.4 Atomicity and concurrency

Each add or remove is one transaction. Schema migration is serialized and runs before ordinary access. Reads may occur concurrently only after database readiness is established.

The process contains one authoritative database instance. Callers do not open independent connections with different encryption or migration behavior.

### 16.5 Failure

An open, encryption, schema, integrity, or migration failure is translated to an unavailable local-configuration state. The application does not silently create an empty replacement and thereby discard protection.

When the failure cannot be repaired without the absent key or a supported migration, the only version 1.0.0 recovery is to clear application data and configure protection again.

### 16.6 Detailed persistence design

The Database Design Specification defines the supported information, constraints, migration, lifecycle, encryption, and verification. This section is authoritative only for the software interaction and sequencing described above.

## 17. Error Handling and Recovery Design

### 17.1 Error categories

| Category | Meaning | Typical response |
|---|---|---|
| Recoverable transient failure | A local operation may succeed on retry without changing security state | Bounded retry, refresh current facts, preserve last consistent state |
| User action required | Android permission or setting must be changed | Explain the effect, open the exact settings destination, recheck on return |
| Protection interrupted | Required detection, service, or lock presentation is not functioning | Stop healthy claim, invalidate pending access, show persistent warning |
| Unrecoverable local-data failure | Credential or encrypted database cannot be opened or safely migrated | Keep authentication unavailable, explain loss of local configuration, direct user to clear data |

### 17.2 Failure matrix

| Failure | Safe behavior | Recovery |
|---|---|---|
| Usage Access denied or revoked | Foreground identity is not trusted; no healthy protection claim | Open Usage Access settings and recheck |
| Notification permission denied | Do not claim successful visible delivery; assess whether service may continue | Open notification settings when the user chooses |
| Protection service stopped | Clear current detector state and report interruption | Request restart when Android permits; verify before healthy state |
| Lock screen cannot be presented | Create no session; report interruption | Return to App Lock, recheck capability, retry only after current target is known |
| PIN is invalid | Increment failure state; create no session | Retry subject to lockout |
| PIN lockout active | Do not verify another PIN | Wait until displayed expiry |
| Biometric unavailable or cancelled | Create no session | Use PIN fallback |
| Protected preferences unavailable | Treat authentication as unavailable | Retry initialization; if unrecoverable, clear application data |
| Database cannot open | Do not treat protected set as empty | Retry once where safe; otherwise clear-data guidance |
| Migration interrupted or invalid | Do not permit normal database access | Resume or roll back according to supported migration; otherwise clear-data guidance |
| Package information temporarily unavailable | Keep stored selection; show unavailable metadata | Refresh current Android package information |
| Storage exhausted during write | Leave last committed state active | Free storage and retry |
| Process death | Clear all transient state and sessions | Reinitialize and re-evaluate health |
| Force-stop | No protection claim while Android blocks restart | User opens App Lock; application reinitializes |

### 17.3 Retry rules

Retries are bounded and limited to idempotent or safely repeatable operations. PIN verification is never automatically retried. A database write is retried only when the persistence layer can establish that the prior transaction did not commit.

Repeated platform or service failure changes health rather than creating an endless retry loop.

### 17.4 User communication

Messages state:

- what is currently unavailable;
- whether protection is affected;
- whether the user’s last change was saved;
- the single next action; and
- whether local configuration will be lost.

Messages do not expose internal component names, exception text, storage paths, package names, encryption detail, or attacker-oriented information.

### 17.5 Recovery limits

There is no backup restore, remote repair, recovery password, administrator repair path, diagnostic package, or preserved credential copy. Clearing application data is destructive and returns the installation to first setup.

## 18. Protection Health and Basic Diagnostics

### 18.1 Purpose

Protection health provides a truthful, current answer to whether the application has enough verified capability to attempt its App Lock function. Basic diagnostics give the user the facts and recovery action needed to address a problem without creating a history or export.

### 18.2 Inputs

Health is derived from:

- complete PIN configuration;
- readable protected preferences;
- readable and schema-compatible encrypted database;
- count of selected protected applications;
- current Usage Access grant;
- current protection-service operation;
- freshness of the most recent valid foreground check;
- current lock-presentation readiness or recent verified presentation result;
- notification capability where required for the retained service; and
- any current unrecoverable initialization error.

The application does not persist the combined health state. It recalculates it after startup, resume, settings return, permission change, service change, protected-selection change, and relevant error.

### 18.3 Health states

| State | Criteria | User presentation |
|---|---|---|
| Not configured | No complete PIN or no selected protected application | Continue setup or select an application |
| Partially configured | A PIN exists but one or more required setup steps, capabilities, or protected selections are incomplete | Resume at the first incomplete step without a healthy claim |
| Protected | Required storage, Usage Access, service, and presentation evidence are healthy | Calm confirmation and last verification time where available |
| Degraded | Protection can operate but a nonessential visibility or responsiveness condition is limited | Explain limitation without claiming interruption |
| Action required | A user-correctable required capability is unavailable | Prominent action and direct Android handoff |
| Protection interrupted | Required detection, service, or presentation has failed while selections exist | Persistent warning; do not use “protected” wording |
| Unknown or not verified | Startup or verification is incomplete or current evidence is stale | Show checking state and retry |

When more than one condition applies, the precedence is Protection interrupted, Action required, Unknown or not verified, Degraded, Protected, Partially configured, then Not configured. Evidence becomes stale after a relevant Android settings handoff, permission change, service change, process recreation, reboot, or failed requested verification; the next health evaluation replaces the stale result. Because no Accessibility service exists in version 1.0.0, Accessibility access is not a health input and is never shown as missing.

### 18.4 Basic diagnostic content

The diagnostic screen may show:

- application version;
- Android version and API level;
- phone form-factor support result;
- PIN configured or not configured, without verifier detail;
- biometric enabled and currently eligible or unavailable, without enrollment detail;
- Usage Access granted or missing;
- protection service active, stopped, or unknown;
- notification capability available or limited;
- encrypted protected-app store available or unavailable;
- number of protected applications, without listing them in exported or notification content; and
- current high-level interruption reason.

### 18.5 Privacy and retention

Diagnostics are read from current state and discarded when no longer needed. There is no diagnostic database, rolling log, metric series, timeline, correlation record, crash package, or export.

Ordinary development logging is minimal and redacted. It does not constitute a user feature or a persistent support history.

## 19. Performance and Resource Design

### 19.1 Performance priorities

The highest-priority latency path is:

1. obtain a recent foreground result from Usage Access;
2. look up package membership in the in-memory protected set;
3. evaluate the package-scoped session; and
4. request the lock screen when required.

The protection decision after a valid foreground result should complete within the applicable quality requirement, with 250 milliseconds as the retained normal-condition target. Usage Access detection should identify a foreground change within the retained 500-millisecond normal-condition target.

These targets do not create an absolute guarantee that Android will never reveal content before presentation.

### 19.2 Main-thread behavior

PIN derivation and verification, encrypted database open, migration, database writes, installed-app enumeration, and Usage Access queries do not block the main presentation thread. State changes return to presentation through lifecycle-aware observable results.

Visual input feedback remains immediate even while credential verification runs.

### 19.3 Memory

The application keeps only:

- the small set of protected package identifiers;
- the current and prior foreground identity;
- active package sessions;
- one current lock request;
- screen presentation state; and
- short-lived installed-app display information.

No event history, media, backup content, metrics, or usage timeline grows in memory.

### 19.4 Battery and CPU

Foreground polling runs only when at least one package is protected and stops when protection is not required. The interval is bounded and no busy-wait loop is permitted. Repeated errors use backoff and change health after a defined limit.

There is no network polling, location monitoring, Bluetooth monitoring, camera work, analytics upload, schedule evaluation, or periodic database maintenance.

### 19.5 Storage

Persistent storage contains a small settings set, protected credential and key material, and protected package identifiers. No unbounded history is retained. A storage warning is required only when a retained write fails or the database cannot operate; a separate storage-monitoring subsystem is not needed.

### 19.6 Performance verification

Measurement covers cold and warm application entry, PIN-screen responsiveness, PIN verification, protected-package lookup, foreground detection, lock-decision time, lock presentation, list loading, selection commit, and migration.

Measurements use sanitized labels and do not persist protected package names or PIN data.

## 20. Maintainability and Verification Support

### 20.1 Maintainable boundaries

Core authentication, retry, session, and protection decisions are deterministic and independently testable. Android and storage behavior is accessed through narrow boundaries. Presentation renders complete states rather than interpreting platform exceptions.

The design avoids duplicate state, hidden global mutation, and circular dependency. Excluded capabilities do not remain as required extension points.

### 20.2 Unit verification

Unit verification covers:

- PIN setup success, mismatch, invalid input, storage failure, and protected verifier upgrade;
- failure counting, five-attempt threshold, 30-second initial lockout, doubling behavior, 30-minute maximum, clock change, and success reset;
- biometric eligibility, success, cancellation, unavailability, stale result, and PIN fallback;
- package-scoped session creation and invalidation;
- immediate, ten-second grace, and screen-off relock behavior;
- process-death and reboot session loss;
- protected and unprotected package decisions;
- duplicate foreground reports and rapid target changes;
- stale lock completion rejection;
- safe defaults for invalid settings; and
- protection-health classification from each input combination.

### 20.3 Storage verification

Storage verification covers:

- PIN and lockout persistence without raw PIN storage;
- database encryption and inability to open without protected material;
- unique protected package insertion;
- idempotent add and remove behavior;
- commit-before-snapshot ordering;
- migration from each explicitly supported source schema;
- interrupted migration and failed verification;
- no destructive empty-database fallback;
- exclusion from Android backup; and
- complete local reset after application-data clearing.

### 20.4 Android integration verification

Android verification covers:

- Usage Access explanation, settings handoff, grant, denial, revocation, and return verification;
- confirmation that the application declares and operates no Accessibility service;
- foreground detection for protected and unprotected launchable apps;
- lock presentation, back/home behavior, recents privacy, and cancellation;
- notification permission behavior on applicable API levels;
- protection-service operation, stop, restart where permitted, and truthful health;
- screen-off session invalidation;
- process recreation;
- reboot;
- force-stop followed by user relaunch;
- package installation, update, and removal; and
- biometric available, unavailable, unenrolled, cancelled, and locked platform states.

### 20.5 User-interface and accessibility verification

Verification covers TalkBack labels and announcements, focus order, masked PIN behavior, lockout announcements, touch targets, contrast, text scaling, landscape use, reduced motion, keyboard behavior where supported, screenshot protection, and recents privacy.

Security and recovery actions remain reachable at the largest supported text scale.

### 20.6 Supported-phone verification

API levels 30 through 35 are each exercised using available emulator coverage, with the complete security-critical path exercised on at least one physical Android phone. Physical-phone checks include Usage Access behavior, protection-service behavior, lock presentation, biometric fallback where hardware is eligible, screen-off, reboot, force-stop, and notification permission where applicable.

Results on tablets, foldables, Chromebooks, earlier APIs, work profiles, secondary users, and cloned apps do not expand version 1.0.0 support.

### 20.7 Security invariants

The completed implementation must demonstrate:

- no raw PIN persistence or logging;
- no session persistence;
- no successful result after cancellation, stale completion, or storage failure;
- no protected-app removal before durable commit;
- no healthy protection claim without current Usage Access and service evidence;
- no Accessibility service declaration or request;
- no protected package names in notifications or ordinary diagnostics;
- no Android backup of application-controlled data;
- no reachability of excluded feature interfaces; and
- no silent data deletion after database or migration failure.

### 20.8 Completion statement

This design is complete for version 1.0.0 when the retained flows, states, storage boundaries, Android limitations, recovery actions, privacy controls, and verification conditions are implemented consistently. Capabilities identified in Section 1.4 remain outside this specification and are not prerequisites for release.
