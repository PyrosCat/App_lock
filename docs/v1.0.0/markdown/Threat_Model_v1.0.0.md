# Threat Model

## Version 1.0.0

**Document status:** Approved  
**Supported product:** App Lock for conventional Android phones  
**Supported operating systems:** Android 11 through Android 15 (API levels 30 through 35)

---

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

## 2. Protected Assets, Security Properties, and Trust Boundaries

### 2.1 Protected Assets

| Asset | Sensitivity | Required security properties | Security consequence of compromise |
|---|---|---|---|
| PIN verifier, salt, retry state, and lockout state | Critical | Confidentiality, integrity, reliable persistence | PIN guessing may be accelerated, attempts may become unlimited, or an incorrect PIN may be accepted |
| Active biometric request and result | Critical while active | Authenticity, freshness, correct request binding | A stale, cancelled, or unrelated result may be treated as authorization |
| Protected-application selection | Critical | Integrity, availability, confidentiality where practical | An attacker may remove an application from protection or learn which applications the user protects |
| Global relock policy and protection settings | Critical | Integrity, authenticated modification | Authorization may last longer than intended or protection may be reduced without consent |
| Authorization-session state | Critical | Integrity, freshness, bounded lifetime | Protected applications may remain accessible without fresh authentication |
| Foreground detection, presentation, and health state | Critical | Integrity, availability, truthful reporting | A protected application may be usable while protection appears normal |
| Android permission state used by protection | Critical | Freshness, integrity of interpretation | A revoked or unavailable capability may go unnoticed |
| Cryptographic keys and database secret | Critical | Confidentiality, integrity, separation of purpose | Protected local information may become readable or alterable |
| Encrypted configuration database and migration state | Critical | Confidentiality, integrity, consistency, availability | Protection settings may be exposed, lost, or replaced with permissive state |
| Essential notification content | High | Privacy, accuracy, minimal disclosure | Another person may learn protected-application or security-state details |
| Bounded local diagnostic records | High | Privacy, integrity, limited retention | Credentials, protected selections, or misleading health information may be exposed |
| Production package and release configuration | High | Integrity, absence of debugging weakness | Runtime inspection or modification may become easier than the stated boundary allows |

Vault files, intruder media, backup archives, recovery secrets, profiles, schedules, automation rules, remote account data, and third-party notification content are not Version 1.0.0 assets because the corresponding capabilities are absent.

### 2.2 Security Properties

#### 2.2.1 Authentication

Authentication establishes that the person interacting with App Lock supplied the correct PIN or completed an eligible Android biometric prompt for the active request. Device unlock, application launch, possession of the phone, navigation to an App Lock screen, or knowledge of a protected application shall not constitute App Lock authentication.

#### 2.2.2 Authorization

Authorization permits access to a protected application only while that application's session remains valid under the selected global relock policy. A session shall not exist before authentication, shall not be reconstructed from a user-interface state, shall not authorize a different protected application, and shall not survive a security boundary that requires reauthentication.

#### 2.2.3 Confidentiality

Credentials, database keys, protected-application selections, and security-sensitive settings shall receive protection appropriate to their sensitivity. App Lock shall minimize visible information in notifications, recent-app previews, errors, and diagnostics.

#### 2.2.4 Integrity

An unauthenticated path shall not change the PIN, protected-application selection, relock policy, biometric setting, required-capability interpretation, or authorization state. Invalid stored values shall be rejected or replaced only through a controlled safe path.

#### 2.2.5 Availability

The foreground detection, lock presentation, session evaluation, and health-reporting paths shall remain available when Android permits them to operate. Where Android prevents continued operation, the application shall not claim availability it cannot establish.

#### 2.2.6 Truthful Status

Protection status is a security property. A missing permission, known presentation failure, failed integrity check, or unverified health condition shall not be displayed as normal protected operation.

#### 2.2.7 Privacy

The application shall collect and retain only the local information needed for the included functions. It shall not create a broader history, media collection, network record, or exportable diagnostic package.

### 2.3 Trust Boundaries

| Boundary | Reliance | Required treatment |
|---|---|---|
| Android operating system | Process isolation, package identity, permission enforcement, lifecycle delivery, notification behavior, and window behavior | Treat platform results as authoritative only within the documented Android model; disclose platform-dependent limits |
| Android Keystore | Protection of non-exportable application key material | Store no substitute plaintext key; fail safely if protected material becomes unavailable |
| Application sandbox | Isolation of application-private files from ordinary applications | Keep credentials, database files, settings, and diagnostics in private storage |
| App Lock credential | Knowledge of the valid PIN represents owner authority | Keep the PIN separate from encryption keys and require it for credential and protection-reducing changes |
| Android biometric prompt | Eligibility, user interaction, and biometric result | Accept only an active successful result; use PIN for fallback and credential administration |
| Android Usage Access | Foreground-application information | Treat it as required but platform-dependent; detect denial or revocation and report the resulting protection state |
| Lock-presentation capability | Android-controlled ability to place the lock interface before usable protected content | Verify availability and do not describe protection as healthy when it is unavailable |
| Android lifecycle and power management | Process start, process death, boot delivery, and background execution | Clear authorization after lifecycle loss and identify conditions Android prevents the application from correcting |
| Storage and migration | Database reads, writes, integrity, schema change, and recovery | Reject incomplete or corrupt security state; do not silently create permissive defaults |
| Physical access | An attacker may possess an already-unlocked phone | Require App Lock authentication independently of device unlock |
| Hostile privileged UI service | A third-party overlay or user-authorized Accessibility service may observe or influence the screen | Apply best-effort input and display protection; retain the remaining risk explicitly |

### 2.4 Non-Goals

The application does not claim to:

- prevent an authorized phone user from uninstalling it, clearing its data, revoking capabilities, or force-stopping it;
- continue executing after Android has force-stopped it;
- defeat root access, operating-system compromise, malicious firmware, or a platform-controlled debugger;
- recover a forgotten PIN while preserving local App Lock configuration;
- protect content after App Lock has validly authorized access and the protected application displays that content;
- prevent observation by an external camera, coercion, or all forms of shoulder surfing;
- provide identical detection or presentation latency across all Android phone manufacturers;
- secure device classes, Android versions, user/profile arrangements, or duplicated-application environments outside Section 1.4.

---

## 3. Threat Actors and Attack Surface

### 3.1 Threat Actors

| Actor | Capabilities | Boundary |
|---|---|---|
| Unauthorized person with physical access | Uses an already-unlocked phone; launches protected applications; switches rapidly; uses Home, Back, Recents, multi-window, and picture-in-picture; changes Android settings available to the device user; attempts PIN guessing; may uninstall, clear data, or force-stop App Lock | Primary in-scope attacker, subject to the platform limitations stated in this document |
| Malicious ordinary Android application | Sends permitted intents or broadcasts; opens overlays if the user granted that capability; observes generally available package and lifecycle behavior; attempts to influence App Lock through exposed Android surfaces | In scope within ordinary sandbox and permission limits |
| Hostile third-party Accessibility service or overlay | Observes interface content permitted by Android; attempts injected actions, obscured touches, or UI deception | In scope for best-effort defense; complete prevention is not guaranteed after the user grants another service elevated UI access |
| Non-root developer-tools attacker | Uses developer or USB capabilities available on a non-rooted phone; attempts application inspection, process interference, or private-data extraction where Android permits | In scope only within the normal non-root Android boundary |
| Android or manufacturer behavior | Delays foreground information; restricts background work; stops a process; changes permission or window behavior; withholds a startup opportunity | In scope as a security-availability and truthful-status condition |
| Rooted-device or system-level attacker | Reads or changes application memory and files; alters platform results; defeats the sandbox; controls UI and process execution | Outside the guaranteed security boundary; assessed as residual critical risk |
| Compromised dependency or weakened production package | Alters security decisions, enables debugging, discloses secrets, or changes release behavior | In scope for bounded dependency and production-package controls |

### 3.2 Attacker Goals

Relevant attacker goals are to:

- access a protected application without the required authentication;
- keep authorization active longer than the selected relock policy permits;
- change or clear the PIN or protection settings without authority;
- remove an application from the protected selection;
- suppress, delay, or mislead foreground detection and lock presentation;
- cause App Lock to report protected status while protection is interrupted or unverified;
- extract the PIN verifier, database secret, protected-application selection, or diagnostic information;
- bypass retry delay or lockout;
- exploit migration, corruption, restart, reboot, or Keystore failure to obtain permissive state;
- use an exposed Android component, overlay, or injected action to reach an authenticated result;
- weaken the production package sufficiently to observe or alter security decisions.

### 3.3 Attack Surface Inventory

| Surface | Information or control crossing the boundary | Principal threats |
|---|---|---|
| Initial setup | PIN creation, capability guidance, first protected-application selection, protection verification | Weak or incomplete setup accepted as protected; PIN disclosure; premature completion |
| Main App Lock entry | Authentication state, settings access, protected-application management | Self-gate bypass; unauthorized configuration change; recent-screen disclosure |
| PIN interface | PIN digits, retry state, timeout, cancellation, input events | Observation, injection, brute force, stale success, navigation bypass |
| Android biometric prompt | Active request, result, error, cancellation, enrollment state | Stale result, false success, fallback bypass, lifecycle confusion |
| Protected-application selection | Installed-application identity and protection state | Unauthorized removal, stale list, removed-application residue, selection disclosure |
| Android Usage Access | Foreground usage information and grant state | Detection failure, revocation, latency, misleading health |
| Lock presentation | Target identity, session state, Android window/activity permission, lifecycle | Race, presentation denial, Back/Home/Recents bypass, obscured interface |
| Package-scoped sessions and global relock | Authentication result, session start, target identity, expiry, screen state | Unauthorized creation, overlong session, cross-application scope confusion, persistence after restart |
| Protection health | Permission state, service health, recent detection/presentation checks | False healthy status, stale status, tampering, unbounded diagnostic detail |
| Essential notifications | Ongoing protection and action-required state | Protected-application disclosure, misleading wording, lock-screen privacy exposure |
| Boot and process lifecycle | Startup event, process recreation, volatile state | Startup gap, stale session restoration, failure to re-establish protection |
| Android power management | Background restrictions and execution opportunities | Delayed or interrupted protection, unreported degradation |
| Encrypted local database | Credential verifier, protected selection, settings, retry state, migration metadata | Plaintext exposure, key extraction, corruption, partial migration, permissive default |
| Android Keystore | Database-key protection and invalidation state | Key loss, key substitution, platform compromise, improper reset |
| Local diagnostics | Bounded health and failure information | Sensitive disclosure, falsification, excessive retention, use as authorization input |
| Android component entry points | Launcher entry, system-delivered startup events, internal screens and background work | Unauthorized invocation, crafted input, state-changing external action |
| Screen, Recents, and overlays | Visual content and user input | Screenshot exposure, spoofing, tapjacking, injected action, protected-content glimpse |
| Installation and update | Signed application package, production flags, dependency set, schema change | Debuggable release, altered package, vulnerable dependency, migration regression |

### 3.4 Excluded Attack Surfaces

There is no Version 1.0.0 attack surface for Vault storage, camera capture, intruder media, backup archives, restore/import processing, account recovery, cloud services, network synchronization, schedule evaluation, location or connection triggers, notification access to other applications, device-administrator controls, or an App Lock Accessibility service.

---

## 4. Security Model and Required Invariants

### 4.1 PIN Model

The PIN is the required local credential. The application shall store only a salted, memory-hard verifier and the state required to enforce retry delay and lockout. It shall not store, log, export, display, or transmit the PIN in plaintext.

PIN creation shall complete before protection can be enabled. PIN change shall require successful verification of the current PIN. A PIN-entry attempt shall produce exactly one of three security outcomes: accepted, rejected, or cancelled/expired. Only accepted shall be capable of creating authorization.

Retry and lockout behavior shall be a fixed global security policy. Restarting the application, terminating its process, navigating away, reopening the screen, or rebooting the phone shall not grant unrestricted additional attempts.

### 4.2 Biometric Model

Biometric use is optional and shall rely on an eligible biometric capability accepted by the Android biometric prompt. The application shall remain fully usable with PIN alone.

A biometric result shall be accepted only when it belongs to the active App Lock authentication request and Android reports success. Cancellation, rejection, timeout, enrollment change, temporary unavailability, permanent unavailability, or platform lockout shall leave the request unauthorized and shall offer PIN fallback where interaction can continue.

Biometrics shall not replace the PIN as the authority for changing the PIN, performing destructive reset confirmation where current authentication is required, or changing security-sensitive authentication settings.

### 4.3 Package-Scoped Authorization Sessions

Version 1.0.0 uses a separate in-memory authorization session for each protected application and one global relock policy. A session may be reused only for the same protected application while the selected global policy permits reuse. Unlocking one protected application shall not authorize another. The application shall not maintain different session durations, credentials, schedules, or profile rules for individual applications.

The session shall:

- be created only after successful App Lock authentication;
- exist only in volatile application memory;
- have an unambiguous creation time and validity rule;
- end on the selected immediate, screen-off, or timeout boundary;
- end on process termination, application restart that loses volatile state, device reboot, PIN change, or destructive reset;
- never be inferred from a visible screen, prior target identity, recent biometric prompt, diagnostic record, or stored flag;
- never be restored from the local database.

### 4.4 Foreground Detection and Lock Presentation

Android Usage Access is the sole Version 1.0.0 foreground-detection baseline. The application shall not require or offer an App Lock Accessibility service.

A foreground observation is untrusted input to the authorization decision. For each relevant transition, the application shall determine whether the foreground application is protected and whether a valid session exists for that same application. Rapid switching, repeated identical foreground observations, and immediate relaunch shall not be ignored solely because the same application was recently observed.

When authentication is required, the application shall use the selected Android-supported presentation capability to place the lock interface before protected access is treated as authorized. Failure to present the lock interface shall not create a session and shall change the reported protection state as defined in Section 4.5.

### 4.5 Protection-Health Model

Protection status shall use the following controlled states:

| State | Meaning | Required presentation |
|---|---|---|
| Protected | Required capabilities are available and recent checks support normal operation | State protection as active without absolute or device-wide claims |
| Degraded | Core protection is operating, but a known Android condition may reduce responsiveness or consistency | Explain the limitation and any practical user action |
| Protection interrupted | A known failure prevents the required protection path from operating | State that protection is interrupted and avoid reassuring language |
| Action required | A user-correctable capability or setting is missing | Identify the specific action and provide a safe Android settings handoff |
| Unknown or not verified | The application cannot establish a reliable current result | State that protection could not be verified; do not display Protected |

The application shall derive status from the capabilities and health information needed for core protection. When more than one condition applies, the state order is Protection interrupted, Action required, Unknown or not verified, Degraded, Protected, Partially configured, then Not configured. Evidence is stale whenever a relevant Android settings handoff, permission change, service change, process recreation, reboot, or an unsuccessful requested verification invalidates the last result; no fixed elapsed-time promise is created. Bounded local diagnostics may explain the condition but shall not become a separate monitoring product.

### 4.6 Configuration and Storage Model

The Version 1.0.0 local data set is limited to credential-verification data, retry and lockout state, protected-application selections, global relock and security settings, capability and health state needed for operation, bounded diagnostic records, cache, and schema-migration metadata.

The configuration database shall be encrypted at rest. Its encryption secret shall be randomly generated, independent of the PIN, and protected through Android Keystore. Credential-verification material and database-key material shall remain separate. No encryption secret shall be placed in ordinary settings, shared storage, notifications, diagnostics, or application resources.

A supported in-place schema migration shall be atomic from the application's perspective. Either the prior valid data remains available or the new schema becomes the valid store. Partial data, missing critical fields, failed integrity checks, and unreadable encryption state shall not be accepted as a healthy empty configuration.

When existing data cannot be used securely, the application shall enter an action-required or interrupted state. A full local reset may be offered, but it shall remove the credential, protected-application selection, settings, session state, bounded records, caches, and unreadable local data before returning to initial setup.

### 4.7 Destructive Forgotten-PIN Handling

The PIN cannot be retrieved. Version 1.0.0 provides no recovery password, backup, account, or data-preserving reset.

The forgotten-PIN path may offer a clearly explained destructive local reset. The path shall:

- state that all App Lock credentials, protected-application selections, settings, and local diagnostic records will be removed;
- require deliberate confirmation that is distinguishable from ordinary navigation;
- never reveal existing protected configuration or credential material;
- never create an authorization session;
- remove the complete App Lock local state rather than selectively preserving protection settings;
- return to initial setup after completion.

Because an Android device user may also clear application data or uninstall the application, destructive reset does not claim to preserve protection against a person who controls those Android actions. This is an accepted platform limitation, not credential recovery.

### 4.8 Notification and Diagnostic Model

App Lock may issue only its own essential notifications for ongoing protection, degraded protection, interrupted protection, or action required. It shall not read or alter notifications from protected applications.

Notification content shall disclose no PIN information, biometric result details, key material, detailed protected-application list, or unnecessary target application name. Lock-screen visibility shall use privacy-preserving content.

Local diagnostics shall be bounded and shall contain only enough information to explain a current protection, permission, authentication, storage, or migration failure. Diagnostics shall not be exported, transmitted, used as authorization, or presented as a long-term user event history.

### 4.9 Security Invariants

1. A failed, cancelled, expired, or interrupted authentication attempt shall not create authorization.
2. A valid App Lock authentication or an unexpired valid session for the same protected application is required before protected access is treated as authorized.
3. Android device unlock alone shall not create App Lock authorization.
4. No authorization session shall survive process termination or device reboot.
5. Retry delay and lockout shall not be cleared by navigation, application restart, process termination, or device reboot.
6. Biometric failure or unavailability shall not weaken PIN fallback.
7. PIN change shall require the current PIN.
8. A protection-reducing setting shall not be changed through an unauthenticated App Lock path.
9. Every relevant protected-application foreground transition shall be evaluated without relying solely on the last observed target.
10. Missing Usage Access or lock-presentation capability shall not be reported as Protected.
11. A notification or warning shall not be treated as prevention of the condition it reports.
12. The PIN, cryptographic keys, and database secret shall not be persisted or exposed in plaintext.
13. Migration failure, corruption, or Keystore invalidation shall not create authorization or a silently permissive healthy configuration.
14. Destructive reset shall remove, not recover, the local App Lock security state.
15. Notifications and diagnostics shall not expose information unnecessary to their immediate purpose.
16. Root or operating-system compromise shall not be described as a condition the application can reliably defeat.

---

## 5. Threat Identification and Analysis

### 5.1 Assessment Method

Each retained threat is assessed against the Version 1.0.0 assets, attack surfaces, security invariants, and Android boundaries defined above. Risk is classified as Critical, High, Medium, or Low according to the likelihood of the attack path and the consequence to authentication, authorization, confidentiality, integrity, availability, privacy, or truthful protection status.

The threat identifiers are retained from the established threat catalogue. Gaps are intentional where a threat belongs only to an excluded capability.

### 5.2 Risk Scale

| Rating | Meaning |
|---|---|
| Critical | A plausible path can grant unauthorized protected-application access, expose primary credential or key material, or broadly defeat protection with no practical user containment |
| High | A material bypass, prolonged protection interruption, unauthorized security change, or false healthy state can occur under credible conditions |
| Medium | The threat requires narrower conditions, produces a limited exposure window, or is materially constrained by Android or another retained control |
| Low | The effect is limited, difficult to exploit, or primarily informational without defeating the core authorization promise |

### 5.3 Credential Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-CRED-001 — PIN Confidentiality Compromise** | An attacker observes PIN entry or obtains PIN-related data from storage, logs, screenshots, clipboard, autofill, diagnostics, or a hostile UI service | The attacker may authenticate as the user and alter protection | SC-AUTH-001, SC-UI-001, SC-PRIV-002, SC-DATA-001 | Physical observation and hostile privileged UI services cannot be completely prevented; **High** |
| **THR-CRED-002 — Unauthorized PIN Reset or Change** | An unauthenticated route reaches credential change or a reset is misrepresented as credential recovery | The attacker replaces the user credential or disables existing protection | SC-AUTH-002, SC-RESET-001, SC-STATE-001 | Android data clear and uninstall remain outside continuing protection; **Medium** |
| **THR-CRED-003 — Credential Reset Through Application Data Manipulation** | Stored verifier, salt, retry state, or credential-existence state is modified, deleted, partially migrated, or replaced | The application may accept a new credential or enter an unsafe first-run state | SC-DATA-001, SC-DATA-003, SC-DATA-004, SC-RESET-001 | Root/system attackers can alter local state; **Medium** within the supported boundary, **Critical** outside it |
| **THR-CRED-004 — Credential Exposure Through Runtime Handling** | PIN digits or verifier inputs remain in memory longer than needed, appear in exceptions, or are passed to an exposed surface | PIN confidentiality is weakened without accessing encrypted storage | SC-AUTH-001, SC-PRIV-002, SC-COMP-001 | A rooted or instrumented process can inspect memory; **Medium** within the supported boundary |

### 5.4 Authentication Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-AUTH-001 — PIN Authentication Bypass** | Error handling, malformed input, stale success state, timeout, cancellation, or screen recreation is interpreted as a correct PIN | Unauthorized session creation | SC-AUTH-001, SC-SESS-001, SC-UI-003 | Residual risk is **Low** after negative-path verification |
| **THR-AUTH-002 — Biometric Authentication Result Abuse** | A stale, cancelled, unrelated, or ineligible biometric result is accepted; lifecycle recreation detaches the result from its request | Unauthorized session creation | SC-AUTH-004, SC-SESS-001, SC-UI-003 | Android biometric integrity remains a platform assumption; **Medium** |
| **THR-AUTH-003 — Authentication State Confusion** | PIN and biometric states overlap, an old result survives navigation, or the lock interface mistakes visible state for authorization | Access is granted without one completed active request | SC-AUTH-004, SC-SESS-001, SC-UI-003 | Residual risk is **Low** after interruption and lifecycle testing |
| **THR-AUTH-004 — Brute-Force Lockout Bypass** | Restart, process termination, reboot, navigation, or time manipulation clears failed-attempt or lockout state | An attacker obtains materially more guesses than policy permits | SC-AUTH-003, SC-DATA-003, SC-LIFE-001 | A weak user-selected PIN remains susceptible within the bounded attempt policy; **Medium** |

### 5.5 Protected-Application Enforcement Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-ENF-001 — Foreground Detection Failure** | Android Usage Access is delayed, incomplete, denied, or interpreted incorrectly when a protected application becomes foreground | Protected content may appear before authentication | SC-ENF-001, SC-ENF-002, SC-HEALTH-001 | Android reporting latency creates a limited exposure window; **High** |
| **THR-ENF-002 — Deliberate Detection Disruption** | A user or attacker revokes Usage Access, disables the presentation capability, force-stops App Lock, or triggers an Android restriction | The core protection path cannot operate | SC-ENF-001, SC-ENF-003, SC-HEALTH-001, SC-LIFE-001 | Force-stop and user-controlled revocation cannot be prevented; **High** |
| **THR-ENF-003 — Silent Detection Failure** | Required grants appear present while foreground information, processing, or lock presentation no longer functions | The application may claim protection while protected applications remain usable | SC-HEALTH-001, SC-HEALTH-002, SC-PRIV-001 | Some failures may be detectable only when the application next runs or tests the path; **High** |
| **THR-ENF-004 — Enforcement Race During Application Switching** | Rapid switching or relaunch occurs between foreground observation, session validation, and lock presentation | Stale target or authorization state permits a bypass | SC-ENF-002, SC-SESS-002, SC-UI-003 | Platform presentation latency remains; logic-based stale-state bypass shall be eliminated; **Medium** |
| **THR-ENF-005 — Enforcement Bypass Through Detection-Service Restart State** | Process recreation or protection-path restart retains a stale target, session, or healthy status | Unauthorized access or false protection state follows restart | SC-SESS-003, SC-LIFE-001, SC-HEALTH-001 | Android may delay restart; **Medium** |
| **THR-ENF-006 — Enforcement Availability Failure** | Usage Access, lock presentation, foreground execution, or another required common capability is unavailable | Protected applications cannot be reliably intercepted | SC-ENF-001, SC-ENF-003, SC-HEALTH-001, SC-LIFE-002 | Ordinary applications cannot guarantee continuous execution; **High** |

### 5.6 Session Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-SES-001 — Unauthorized Session Creation** | A visible screen, diagnostic state, pending biometric request, error, or crafted component invocation is treated as an authenticated session | A protected application may become accessible without valid authorization | SC-SESS-001, SC-COMP-001, SC-UI-003 | Residual risk is **Low** after negative-path verification |
| **THR-SES-002 — Session Extension Beyond Policy** | Time handling, screen transitions, repeated foreground events, or background/foreground cycling refreshes the session incorrectly | Access remains authorized longer than the user selected | SC-SESS-002, SC-LIFE-001 | System-clock anomalies require monotonic elapsed-time treatment; **Low** after verification |
| **THR-SES-003 — Cross-Application Session Confusion** | A package-scoped session is applied when expired, applied before authentication, or applied to a different protected application | One application receives access outside the defined global policy | SC-SESS-001, SC-SESS-002, SC-ENF-002 | Valid reuse for the same application is permitted only while its session remains valid; cross-application reuse shall be eliminated; **Low** |
| **THR-SES-004 — Session Persistence Across Reboot or Process Death** | Authorization is written to storage or reconstructed after restart | Protected applications open without fresh authentication | SC-SESS-003, SC-LIFE-001, SC-DATA-003 | Residual risk is **Low** within the supported boundary |

### 5.7 Cryptographic Storage Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-CRYPTO-001 — Master Key Compromise** | Keystore-protected key material is exposed, substituted, or used outside its intended purpose | Database-key protection and local confidentiality may fail | SC-DATA-002, SC-DATA-005 | Root/system or Keystore compromise remains outside the guarantee; **Critical** outside the supported boundary |
| **THR-CRYPTO-002 — Database Passphrase Compromise** | The database secret is stored beside the database, logged, embedded, exported, or retained in an unsafe form | Protected configuration becomes readable offline | SC-DATA-001, SC-DATA-002, SC-PRIV-002 | Runtime extraction by a rooted attacker remains; **Medium** within the supported boundary |
| **THR-CRYPTO-004 — Cryptographic Key Reuse or Derivation Error** | The PIN verifier, database secret, or Keystore key is reused for a different purpose or derived through an unsafe relationship | Compromise of one secret weakens another boundary | SC-DATA-002, SC-DATA-005 | Residual risk is **Low** after design and storage verification |
| **THR-CRYPTO-005 — Keystore Invalidation** | Device security changes, platform behavior, or key loss makes protected key material unusable | Configuration becomes unreadable; unsafe error handling may reset or bypass protection | SC-DATA-004, SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | Data may be irrecoverable because backup is absent; **High** availability risk, **Low** authorization-bypass risk after controls |

### 5.8 User-Interface and Component Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-UI-001 — Tapjacking or Obscured Authentication Input** | A hostile overlay changes what the user sees or receives touches intended for another control | The user may disclose a PIN or confirm a protection-reducing action | SC-UI-001, SC-UI-002 | Android-granted overlay privileges cannot be completely neutralized; **Medium** |
| **THR-UI-002 — UI Spoofing** | Another application imitates the App Lock screen or places misleading content over it | The user may enter the PIN into a hostile interface | SC-UI-001, SC-PRIV-001 | The application cannot prevent another app from drawing a look-alike screen; **Medium** |
| **THR-UI-003 — Touch or Event Injection** | A hostile Accessibility service, automation interface, or crafted input invokes authentication or destructive controls | Unauthorized action may occur without deliberate user input | SC-UI-002, SC-AUTH-001, SC-RESET-001 | A user-authorized hostile Accessibility service retains elevated platform capability; **High** residual outside best-effort defenses |
| **THR-UI-004 — Navigation Around Lock Screen** | Back, Home, Recents, rotation, multi-window, picture-in-picture, timeout, or interruption reveals protected content or creates authorization | Protected application becomes usable without completed authentication | SC-UI-003, SC-ENF-003, SC-SESS-001 | Android window timing may allow a brief visual exposure; **High** |
| **THR-UI-005 — Screen Capture or Recording** | Authentication, protected configuration, or recent-app preview is captured | PIN-entry state or protected-application information is disclosed | SC-UI-001, SC-PRIV-001 | External cameras and some privileged system capture are outside control; **Medium** |
| **THR-IPC-001 — Unauthorized Activity Launch** | An external application invokes an App Lock screen directly with crafted input | Sensitive screen access, false navigation context, or state change | SC-COMP-001, SC-STATE-001 | Required launcher entry remains externally reachable but shall not confer authorization; **Low** |
| **THR-IPC-002 — Unauthorized Service Invocation** | An external application attempts to start or bind to background protection work | Protection state is changed, stopped, or confused | SC-COMP-001, SC-HEALTH-001 | Android system actions remain trusted inputs only within platform rules; **Low** |
| **THR-IPC-003 — Unauthorized Boot-Event Invocation** | A crafted broadcast imitates a startup condition | Protection work is triggered in an unsafe state or stale authorization is restored | SC-COMP-001, SC-LIFE-002, SC-SESS-003 | Safe redundant startup may occur but shall not create authorization; **Low** |
| **THR-ACC-004 — Malicious Peer Accessibility Service** | The user has granted another service Accessibility privileges capable of observing or manipulating App Lock UI | PIN confidentiality, input integrity, and user intent may be compromised | SC-UI-001, SC-UI-002, SC-AUTH-001 | Complete defense is not guaranteed against platform-granted peer privilege; **High** |

### 5.9 Lifecycle, Health, Recovery, and Platform Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-LIFE-001 — Force-Stop Enforcement Loss** | The device user force-stops App Lock | Android prevents execution and the protection path stops | SC-LIFE-001, SC-HEALTH-002 | Continuous protection while force-stopped cannot be guaranteed; **Critical** accepted platform limitation |
| **THR-LIFE-002 — Process Death With Authorization Confusion** | Android terminates the process while a session or authentication request exists | Stale authorization or inconsistent lock state appears after recreation | SC-SESS-003, SC-LIFE-001, SC-UI-003 | Restart latency remains; **Low** authorization risk after controls |
| **THR-LIFE-003 — OEM Background Restriction** | Manufacturer power management delays or prevents required background work | Detection or presentation becomes delayed or interrupted | SC-LIFE-001, SC-HEALTH-001, SC-HEALTH-002 | Manufacturer behavior varies beyond available evidence; **High** |
| **THR-LIFE-004 — Boot Re-Arm Failure** | Required protection work does not resume after reboot | Protected applications may be used before protection is restored | SC-LIFE-002, SC-HEALTH-001, SC-SESS-003 | Android may delay delivery or execution; **High** |
| **THR-LIFE-005 — Startup Security Race** | A protected application becomes usable before App Lock has restored detection and presentation | Temporary unauthorized access | SC-LIFE-002, SC-ENF-002, SC-HEALTH-001 | Startup ordering remains platform-dependent; **High** |
| **THR-AUD-001 — Security Diagnostic Modification** | Bounded local records are altered or deleted | Troubleshooting or displayed health may become misleading | SC-PRIV-002, SC-DATA-003, SC-HEALTH-001 | Diagnostics are not authorization inputs, limiting consequence; **Low** |
| **THR-AUD-003 — Security-State Manipulation** | Stored, cached, or visible health state is changed independently of actual permission and protection checks | Interrupted protection is displayed as healthy | SC-HEALTH-001, SC-DATA-003 | Health may be unverified while the app cannot run; **High** |
| **THR-REC-001 — Database Corruption Causes Security Degradation** | Database damage prevents reliable reading of protected selections or settings | The application may behave as though no applications are protected | SC-DATA-003, SC-DATA-004, SC-RESET-001 | Local configuration may be irrecoverable; **High** availability risk |
| **THR-REC-002 — Database Migration Exposure** | In-place upgrade copies plaintext, loses fields, partially commits, or weakens settings | Confidentiality or protection integrity is lost | SC-DATA-004, SC-DATA-005 | Unsupported downgrade is not guaranteed; **Medium** |
| **THR-REC-003 — Keystore Loss Causes Irrecoverable Data Loss** | Protected key material becomes unavailable | Encrypted configuration cannot be read | SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | No backup exists; destructive reset may be the only recovery; **High** availability risk |
| **THR-REC-005 — Forgotten-PIN Recovery Becomes a Bypass** | The forgotten-PIN path grants access, reveals configuration, or preserves selective security state without the PIN | An attacker converts reset into authentication or disables selected protection covertly | SC-RESET-001, SC-SESS-001, SC-STATE-001 | Full destructive reset still ends local protection and is equivalent in consequence to Android data clear; **High** accepted boundary |
| **THR-INT-001 — Debuggable Production Build** | A distributed package permits debugging or ordinary runtime inspection | An attacker gains easier access to memory and security decisions | SC-BUILD-001 | Root/system instrumentation remains outside the guarantee; **Medium** |
| **THR-INT-004 — Release Configuration Weakening** | Production packaging omits security flags, includes secrets, or changes critical protection behavior | Multiple security boundaries become weaker than specified | SC-BUILD-001, SC-BUILD-002 | Distribution-platform and signing-account compromise remain external; **Medium** |
| **THR-PLAT-001 — Root or System Compromise** | The attacker controls privileged operating-system behavior | Sandbox, memory, UI, files, and sessions can be read or changed | SC-BOUND-001 | No complete application-level mitigation; **Critical** accepted boundary |
| **THR-PLAT-002 — Android Keystore Trust Failure** | Android or hardware fails to preserve the Keystore security properties | Keys or protected local data may be exposed | SC-DATA-002, SC-BOUND-001 | No complete application-level mitigation; **Critical** accepted boundary |
| **THR-PLAT-003 — Android Framework Security Behavior Changes** | A supported Android update changes permission, biometric, lifecycle, notification, or window behavior | Protection may fail or become misleading | SC-BOUND-001, SC-HEALTH-001, SC-BUILD-002 | Unknown platform changes require compatibility verification; **High** |
| **THR-PLAT-004 — OEM Security or Power-Management Interference** | Manufacturer changes suppress execution, delay events, or alter settings behavior | Detection and presentation may become unreliable | SC-LIFE-001, SC-HEALTH-001, SC-BOUND-001 | Universal manufacturer behavior is not guaranteed; **High** |
| **THR-SUP-001 — Malicious Dependency** | A packaged dependency alters security decisions, records secrets, or exposes a component | Authentication, storage, or protection may be compromised | SC-BUILD-002 | Review cannot prove absence of all malicious behavior; **Medium** |
| **THR-SUP-002 — Dependency Update Introduces Security Regression** | An updated library changes cryptography, database, biometric, lifecycle, or UI behavior | A previously controlled threat reappears | SC-BUILD-002, Section 8 acceptance scenarios | Residual risk is **Low** after retained-surface regression verification |
| **THR-SUP-003 — Build or Release Integrity Failure** | An incorrect, altered, unsigned, or inconsistently configured package is distributed | Users receive behavior that does not satisfy this Threat Model | SC-BUILD-001, SC-BUILD-002 | Signing-account or distribution-platform compromise remains external; **Medium** |

### 5.10 Removed Threat Domains

The following established threat domains do not apply to Version 1.0.0 and are intentionally absent from the retained register:

- all `THR-VAULT-*` threats;
- `THR-CRYPTO-003`, which is specific to Vault-file key material;
- all App Lock Accessibility-service availability threats, including `THR-ACC-001`, `THR-ACC-002`, `THR-ACC-003`, and `THR-ACC-005`;
- all `THR-DA-*` device-administrator threats;
- `THR-AUD-002`, which concerns intruder-event disclosure;
- `THR-REC-004`, which concerns backup and restore;
- network-service threats associated with accounts, cloud storage, synchronization, telemetry transmission, or remote administration.

The hostile peer Accessibility threat remains as `THR-ACC-004` because another user-authorized service can attack the App Lock authentication interface even though App Lock itself provides no Accessibility service.

---

## 6. Security Controls and Failure Handling

### 6.1 Control Register

| Control | Required outcome |
|---|---|
| **SC-AUTH-001 — PIN protection and verification** | Store a salted, memory-hard verifier; compare securely; prevent plaintext persistence, clipboard use, autofill retention, predictive retention, logging, and diagnostic disclosure |
| **SC-AUTH-002 — Credential administration** | Require the current PIN before PIN change or biometric-setting changes that reduce protection; never reveal the PIN |
| **SC-AUTH-003 — Retry delay and lockout** | Enforce one fixed global policy whose security state survives navigation, restart, process termination, and reboot |
| **SC-AUTH-004 — Eligible biometric with PIN fallback** | Use only an active successful Android biometric result; cancellation, failure, or unavailability leaves the request unauthorized and returns to PIN |
| **SC-SESS-001 — Explicit package-scoped session creation** | Create a session for only the active protected target after successful authentication; never infer it from UI or stored state |
| **SC-SESS-002 — Global relock enforcement** | End authorization according to the selected immediate, screen-off, or timeout policy without per-application exceptions |
| **SC-SESS-003 — Volatile session boundary** | Keep authorization out of persistent storage and clear it on process termination, reboot, PIN change, and reset |
| **SC-ENF-001 — Usage Access baseline** | Use Android Usage Access as the sole foreground-detection source and require its availability for a Protected status |
| **SC-ENF-002 — Per-transition evaluation** | Re-evaluate protected status and the applicable package-scoped session on every relevant foreground transition, including repeated targets and rapid relaunch |
| **SC-ENF-003 — Secure lock presentation** | Present the lock interface through the required Android-supported capability; cancellation and presentation failure do not authorize access |
| **SC-HEALTH-001 — Truthful protection state** | Derive Protected, Degraded, Protection interrupted, Action required, or Unknown or not verified from actual required capabilities and recent checks |
| **SC-HEALTH-002 — Essential user notice** | Provide privacy-masked action guidance when protection is degraded, interrupted, or cannot be verified; do not equate notice with prevention |
| **SC-STATE-001 — Authenticated protection changes** | Require App Lock authentication before changing the protected selection, relock policy, PIN, biometric use, or another protection-reducing setting |
| **SC-DATA-001 — Encrypted private database** | Keep credential and configuration data in application-private encrypted storage and prevent plaintext side copies |
| **SC-DATA-002 — Android Keystore key protection** | Protect a random database secret with Android Keystore and keep it independent of the PIN and verifier |
| **SC-DATA-003 — Configuration validation and integrity** | Reject incomplete, invalid, corrupt, or unauthenticated security state and prevent a permissive empty configuration from being accepted silently |
| **SC-DATA-004 — Atomic in-place migration** | Preserve the prior valid data or complete the new schema; do not accept partial migration or leave plaintext remnants |
| **SC-DATA-005 — Key separation and failure handling** | Keep each secret limited to one purpose and enter a safe action-required state if protected key material becomes unavailable |
| **SC-RESET-001 — Complete destructive reset** | Explain the loss, require deliberate confirmation, create no session, remove all local App Lock state, and return to setup |
| **SC-UI-001 — Sensitive-screen privacy** | Protect PIN and security-sensitive App Lock screens from ordinary screenshots and recent-app disclosure and avoid unnecessary target detail |
| **SC-UI-002 — Obscured and injected input defense** | Reject or safely handle obscured input and unauthorized accessibility actions on authentication and protection-reducing controls |
| **SC-UI-003 — Safe navigation and interruption** | Ensure Back, Home, Recents, rotation, multi-window, picture-in-picture, timeout, backgrounding, and cancellation do not create authorization |
| **SC-COMP-001 — Minimal Android component exposure** | Keep entry points private unless Android operation requires otherwise; validate all external input and permit no unauthenticated sensitive action |
| **SC-PRIV-001 — Masked App Lock notifications** | Limit notifications to App Lock protection status and action guidance without unnecessary application identity or authentication detail |
| **SC-PRIV-002 — Bounded local diagnostics** | Record only minimum non-secret context, retain it for a bounded period, keep it private, and provide no export or remote transmission |
| **SC-LIFE-001 — Process and background-state recovery** | Clear sessions after process loss; re-evaluate required capabilities and state when execution resumes; disclose unpreventable force-stop and OEM limits |
| **SC-LIFE-002 — Boot and startup recovery** | Clear authorization at reboot and re-establish available protection work without treating startup events as authentication |
| **SC-BUILD-001 — Production-package hardening** | Distribute a non-debuggable signed package with required privacy/security flags and no embedded credential or database secret |
| **SC-BUILD-002 — Bounded dependency and release validation** | Validate the dependencies and production package that directly affect authentication, detection, presentation, database, migration, and key protection |
| **SC-BOUND-001 — Platform-boundary disclosure** | Make no guarantee against root/system compromise, Keystore failure, force-stop, or unsupported manufacturer behavior; represent these accurately to the user |

### 6.2 Authentication Control Requirements

The PIN entry surface shall accept only the defined numeric input, shall not expose entered digits to clipboard, autofill, predictive retention, notifications, or diagnostics, and shall clear temporary entry state after completion or cancellation. Errors shall be generic enough not to reveal verifier or storage detail.

Retry delay and lockout shall be based on persisted security state that cannot be reset by reopening the interface. The policy shall not be user-configurable in Version 1.0.0. No post-threshold camera, location, messaging, or intruder-recording action shall occur.

The biometric prompt shall be a convenience path only. The PIN shall remain usable when the phone lacks eligible biometric hardware, has no eligible enrollment, reports an error, or locks biometric use. Biometric success shall not alter the PIN or create a different session policy.

### 6.3 Authorization and Relock Control Requirements

Only one global relock policy shall exist. A user may select a supported relock choice, but shall not configure different choices by application, time, profile, location, network, or other condition.

Immediate relock shall end the applicable package session after leaving its authorized context. Screen-off relock shall end all package sessions when the screen turns off. Timeout relock shall use one global duration and a monotonic elapsed-time basis so wall-clock changes do not improperly extend authorization.

An authentication interface that is dismissed, obscured, timed out, backgrounded, destroyed, or interrupted shall not signal success. The protected target shall remain unauthorized unless its own package-scoped session was already valid independently of the interrupted request.

### 6.4 Detection, Presentation, and Health Control Requirements

The application shall obtain foreground information only through the Version 1.0.0 Usage Access baseline. It shall not ask for Accessibility permission for its own operation.

The protection decision shall evaluate:

- whether protection setup is complete;
- whether the foreground application is in the current protected selection;
- whether a valid session exists for the foreground protected application;
- whether required detection and presentation capabilities are available;
- whether a known interrupted or unverified condition prevents a healthy claim.

No schedule, profile, per-application timeout, location, Wi-Fi, Bluetooth, or other rule shall participate.

If Android prevents lock presentation after a protected target is detected, the application shall create no session, record only the bounded failure context, and move to Protection interrupted, Action required, or Unknown or not verified as appropriate. Essential notification wording shall state what the user must do without claiming the target remained inaccessible when that cannot be established.

### 6.5 Storage, Migration, and Reset Control Requirements

Protected configuration shall be encrypted before persistence. Encryption keys shall be unavailable through ordinary preferences, shared storage, resource files, logs, diagnostic records, screenshots, and notifications. The PIN shall authenticate the user but shall not serve as the database encryption key.

Migration shall validate required fields, values, protected selections, global relock settings, credential state, retry state, and database integrity before the new schema is accepted. A failed migration shall not delete the last valid store before failure is known. Temporary data shall remain private and shall be removed after success or controlled failure.

Corruption or Keystore invalidation shall not cause the application to display a healthy empty selection. If secure recovery is impossible, the application shall explain that local App Lock data cannot be used and shall offer full destructive reset only. Version 1.0.0 shall not attempt backup recovery, key reconstruction from the PIN, or preservation of selected settings.

### 6.6 Notification, Diagnostic, and Component Control Requirements

The ongoing protection notification shall identify App Lock operation without naming a protected application unless the name is essential to a user-directed recovery action. Lock-screen notification text shall be masked. An action-required notification may open only an App Lock explanation or the specific Android settings handoff; it shall not grant a session or change a security setting.

Diagnostic records shall use bounded retention and shall contain category, time, non-secret outcome, and only the minimum context needed to explain the current issue. They shall exclude PIN digits, verifier material, database secrets, biometric data, full protected-application lists, content from protected applications, and third-party notification content.

Externally reachable Android entry points shall be limited to those required for launch or safe platform events. External input shall be validated and shall never directly assert authentication success, session validity, protected selection, PIN change, or reset completion.

### 6.7 Failure-Handling Matrix

| Condition | Required user-visible and security behavior | Prohibited outcome |
|---|---|---|
| Incorrect PIN | Reject; update retry state; show bounded feedback; apply delay or lockout when required | Session creation, counter reset, or disclosure of correct-PIN characteristics |
| PIN entry cancelled or timed out | Close or restore the authentication surface safely; leave target unauthorized | Treating cancellation as success |
| Biometric cancelled, rejected, unavailable, or locked | Leave unauthorized and provide PIN fallback | Automatic access or persistent biometric success state |
| Usage Access denied or revoked | Show Action required during incomplete setup or Protection interrupted after active protection is lost; provide the exact Android settings handoff | Displaying Protected or silently continuing as though detection works |
| Lock-presentation capability denied or revoked | Show Action required during incomplete setup or Protection interrupted after active protection is lost; create no session | Claiming protected applications remain inaccessible when presentation cannot be established |
| Health cannot be established | Show Unknown or not verified and provide appropriate retry or guidance | Inferring Protected from stale status |
| Reduced Android responsiveness | Show Degraded when the baseline operates but a known condition affects responsiveness | Describing degraded protection as identical to normal operation |
| Process termination | Lose all sessions; re-evaluate protection when the app runs again | Restoring authorization from storage or screen state |
| Device reboot | Lose all sessions; attempt safe protection startup when Android permits | Restoring the previous session |
| Force-stop | When next opened, explain that protection could not operate while force-stopped and require normal setup/health validation | Claiming uninterrupted protection during force-stop |
| Rapid switch or relaunch | Re-evaluate the foreground target and its applicable package-scoped session every time | Suppressing the lock because the package was recently observed |
| Database migration failure | Preserve the last valid store when possible; enter Action required during incomplete setup or Protection interrupted after active protection is lost; provide safe retry or reset | Accepting partial data or permissive defaults |
| Database corruption | Stop normal use of unreadable security state; disclose the condition; offer complete reset if unrecoverable | Treating corruption as no protected applications while showing Protected |
| Keystore invalidation | Deny use of unreadable protected data; show Action required; offer complete reset if unrecoverable | Generating a replacement key and silently accepting lost configuration as valid |
| Forgotten PIN | Explain that the PIN cannot be retrieved; offer complete destructive reset with deliberate confirmation | Data-preserving recovery, session creation, selective preservation, or PIN disclosure |
| Destructive reset cancelled | Return without changing local data or authorization | Partial deletion or access grant |
| Destructive reset completed | Remove all local App Lock security state and return to initial setup | Retaining a session, credential, protected selection, or inconsistent residue |
| Application uninstalled or data cleared through Android | No continuing protection guarantee; setup is required after reinstall | Claiming persistence that Android does not provide |
| Notification permission unavailable where required by Android | Explain the effect on ongoing protection and guide the user | Hiding an execution limitation or claiming normal protection |
| Diagnostic write failure | Continue core protection if possible; omit the record and avoid security-state change | Treating logging failure as authorization failure or success |

---

## 7. Risk Assessment and Accepted Limitations

### 7.1 Risk Treatment

Critical and High risks within the supported application boundary require a preventive control, a detection and recovery control, or both. A threat shall not be described as mitigated where the application only displays a warning. Medium risks require proportionate controls and verification. Low risks may be accepted where their effect is bounded and does not undermine the core authorization promise.

Root/system compromise, force-stop, application uninstall, application-data clearing, platform Keystore failure, and Android-controlled execution limits cannot be eliminated by an ordinary App Lock application. They are accepted boundaries only when stated accurately and when the application does not create an additional bypass inside the boundary it does control.

### 7.2 Residual-Limitation Register

| Limitation | Residual consequence | Required representation |
|---|---|---|
| Foreground detection timing | Android Usage Access may not report a transition instantly; limited content may appear before lock presentation | Do not promise zero-latency interception; verify and describe measured behavior for supported phones |
| Lock-presentation timing | Android controls whether and when a background application may present its interface | Report Protection interrupted or Unknown or not verified when presentation cannot be established |
| Permission revocation | The user or Android can remove required capabilities | Detect when possible, guide recovery, and never claim healthy protection after known loss |
| Force-stop | Android can prevent all App Lock execution | State that protection cannot continue while force-stopped |
| Manufacturer power restrictions | A manufacturer may delay or terminate background work | Use Degraded, Protection interrupted, or Unknown or not verified status as supported by actual evidence; do not claim universal behavior |
| Reboot/startup ordering | App Lock may not run before another application is usable | Clear sessions and restore protection at the earliest Android-permitted opportunity; retain the exposure as High risk |
| Uninstall or application-data clearing | Local credentials and selections are removed, ending App Lock protection | Treat as a device-user action outside continuing protection |
| Destructive forgotten-PIN reset | Any person able to reach and confirm reset may erase App Lock configuration; no protected data is recovered | Explain the loss and never present reset as authentication |
| No backup | Corruption or Keystore loss may make local configuration irrecoverable | Offer full reset only; do not imply data can be restored |
| Root/system compromise | A privileged attacker may alter memory, files, UI, permissions, and Keystore behavior | Make no security guarantee in this condition |
| Hostile privileged UI service | A user-authorized overlay or Accessibility service may observe or inject UI actions | Apply best-effort screen and input defenses and disclose the limit |
| Physical observation | Another person or external camera may observe PIN entry | Mask entry and minimize exposure; do not claim complete shoulder-surfing prevention |
| Platform biometric accuracy | Match accuracy and enrollment protection are controlled by Android and device hardware | Accept only platform-approved eligibility and preserve PIN fallback |
| Phone manufacturer variation | Detection, notification, permission, and background behavior may vary | Limit claims to the declared supported range and available compatibility evidence |
| Unsupported device or profile class | Protection has not been specified or verified for the excluded environment | Do not imply support or provide partial claims |

### 7.3 Risk Conclusions

The highest residual risks are enforcement interruption caused by force-stop, permission removal, manufacturer restrictions, startup timing, or Android presentation limits. The application shall reduce these risks through conservative session handling, per-transition evaluation, health checking, accurate status, and recovery guidance. Those measures do not convert Android-controlled limitations into guaranteed prevention.

The highest confidentiality boundary is Android Keystore and the application sandbox. Loss of either boundary may expose local security information. Version 1.0.0 does not add root detection, anti-tamper services, remote revocation, or backup recovery to address conditions outside the stated phone security model.

The destructive forgotten-PIN path accepts loss of local App Lock configuration in exchange for avoiding a recovery secret, account system, backup path, or weak credential bypass. It shall never be counted as successful authentication.

---

## 8. Security Verification and Acceptance

### 8.1 Verification Principles

Verification shall exercise successful use, negative paths, interruption, stale state, and Android limitation behavior. A function is not security-acceptable merely because its normal path works. Each scenario shall verify both what happens and what must not happen.

Testing shall be limited to conventional Android phones and phone emulators across API levels 30 through 35. It shall not create acceptance obligations for excluded device classes, Android versions, profiles, cloned applications, or manufacturer-specific dual-app environments.

### 8.2 Authentication Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-AUTH-001 — Correct PIN** | The active request succeeds and creates one valid session for only the active protected target or authenticated settings context | THR-AUTH-001, THR-SES-001 |
| **VA-AUTH-002 — Incorrect PIN** | Access is denied, failure state advances, and no session exists | THR-AUTH-001, THR-AUTH-004 |
| **VA-AUTH-003 — Repeated failures** | Fixed retry delay and lockout apply at the defined thresholds | THR-AUTH-004 |
| **VA-AUTH-004 — Restart during lockout** | Restart, process termination, and reboot do not provide unrestricted new attempts | THR-AUTH-004, THR-SES-004 |
| **VA-AUTH-005 — PIN cancellation and timeout** | The target remains unauthorized and no success state survives | THR-AUTH-001, THR-AUTH-003 |
| **VA-AUTH-006 — PIN change** | Current PIN is required; old PIN fails after completion; sessions are cleared | THR-CRED-002, THR-AUTH-003, THR-SES-004 |
| **VA-AUTH-007 — Eligible biometric success** | Only the active request succeeds and the normal package-scoped session and global relock rules apply | THR-AUTH-002, THR-SES-001 |
| **VA-AUTH-008 — Biometric cancellation or rejection** | No session is created and PIN fallback remains available | THR-AUTH-002, THR-AUTH-003 |
| **VA-AUTH-009 — Biometric unavailable or locked** | PIN remains fully usable and no access is granted automatically | THR-AUTH-002 |
| **VA-AUTH-010 — Screen recreation during authentication** | Rotation, backgrounding, process recreation, and prompt replacement do not reuse stale success | THR-AUTH-002, THR-AUTH-003, THR-UI-004 |

### 8.3 Enforcement and Session Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-ENF-001 — Protected application without session** | The lock interface is presented and access is not treated as authorized | THR-ENF-001, THR-ENF-006 |
| **VA-ENF-002 — Unprotected application** | No App Lock authentication is introduced | Configuration integrity check; prevents false positives without weakening protected targets |
| **VA-ENF-003 — Cross-application session isolation** | A valid session may be reused only for the same protected application; unlocking one protected application does not authorize another | THR-SES-003 |
| **VA-ENF-004 — Immediate relock** | Leaving the authorized context ends authorization as defined and the next protected access requires authentication | THR-SES-002 |
| **VA-ENF-005 — Screen-off relock** | Screen-off ends authorization and the next protected access requires authentication | THR-SES-002 |
| **VA-ENF-006 — Timeout relock** | Expiry ends authorization; wall-clock changes do not improperly extend it | THR-SES-002 |
| **VA-ENF-007 — Rapid switching** | Repeated transitions between protected and unprotected applications do not bypass evaluation | THR-ENF-004, THR-SES-003 |
| **VA-ENF-008 — Rapid relaunch** | Immediate relaunch of the same protected application does not reuse stale target or expired authorization | THR-ENF-004 |
| **VA-ENF-009 — Back, Home, and Recents** | Navigation does not create authorization or leave usable protected content exposed through the App Lock path | THR-UI-004 |
| **VA-ENF-010 — Phone multi-window and picture-in-picture** | The supported phone either maintains protected presentation or safely denies/cancels the access; no separate optimized layout is required | THR-UI-004, THR-ENF-006 |
| **VA-ENF-011 — Process termination** | All sessions are lost and protected access requires authentication after recovery | THR-LIFE-002, THR-SES-004 |
| **VA-ENF-012 — Device reboot** | Sessions are lost and protection is restored at the earliest Android-permitted opportunity | THR-LIFE-004, THR-LIFE-005, THR-SES-004 |

### 8.4 Permission and Health Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-HEALTH-001 — Usage Access denied during setup** | Setup does not claim protection complete; Action required identifies the missing capability | THR-ENF-002, THR-AUD-003 |
| **VA-HEALTH-002 — Usage Access revoked** | Protected status is withdrawn when the loss is known; recovery guidance opens the correct Android settings destination | THR-ENF-002, THR-ENF-003 |
| **VA-HEALTH-003 — Presentation capability unavailable** | No session is created; state becomes Action required during incomplete setup or Protection interrupted after active protection is lost | THR-ENF-006, THR-UI-004 |
| **VA-HEALTH-004 — Health cannot be verified** | The application displays Unknown or not verified rather than Protected | THR-ENF-003, THR-AUD-003 |
| **VA-HEALTH-005 — Known reduced responsiveness** | The application displays Degraded with accurate wording and does not imply normal response | THR-LIFE-003, THR-PLAT-004 |
| **VA-HEALTH-006 — Force-stop recovery** | On the next launch, the application rechecks required capabilities and does not claim protection during the stopped interval | THR-LIFE-001 |
| **VA-HEALTH-007 — Essential notification privacy** | Lock-screen and notification content identify only the minimum App Lock status and action | THR-UI-005 |
| **VA-HEALTH-008 — No App Lock Accessibility dependency** | Core setup and protection request no Accessibility permission and remain independent of an App Lock Accessibility service | Confirms removal of THR-ACC-001/002/003/005 surfaces |

### 8.5 Storage, Migration, and Reset Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-DATA-001 — PIN storage inspection** | No plaintext PIN appears in database, private settings, cache, logs, diagnostics, or resources | THR-CRED-001, THR-CRED-004 |
| **VA-DATA-002 — Database inspection** | Protected configuration is not readable as plaintext from the stored database | THR-CRYPTO-002 |
| **VA-DATA-003 — Key-location inspection** | Database secret is not stored in plaintext beside the database or in ordinary settings/resources | THR-CRYPTO-001, THR-CRYPTO-002, THR-CRYPTO-004 |
| **VA-DATA-004 — Valid in-place migration** | Credential state, protected selection, global relock setting, retry state, and required security values remain consistent | THR-REC-002 |
| **VA-DATA-005 — Interrupted migration** | Prior valid data remains usable or normal operation stops safely; partial permissive state is not accepted | THR-REC-001, THR-REC-002 |
| **VA-DATA-006 — Corrupt database** | The application shows Action required during incomplete setup or Protection interrupted after active protection is lost and does not show a healthy empty selection | THR-REC-001, THR-AUD-003 |
| **VA-DATA-007 — Keystore invalidation** | No key or session bypass occurs; unreadable data leads only to safe status and full reset | THR-CRYPTO-005, THR-REC-003 |
| **VA-DATA-008 — Forgotten-PIN reset review** | The loss is explicit, the PIN is not revealed, and reset cannot create authorization | THR-CRED-002, THR-REC-005 |
| **VA-DATA-009 — Cancel destructive reset** | All local state remains unchanged and no session is created | THR-REC-005 |
| **VA-DATA-010 — Complete destructive reset** | Credential, selections, settings, sessions, diagnostics, cache, and unusable data are removed before initial setup appears | THR-CRED-003, THR-REC-005 |

### 8.6 UI, Component, Privacy, and Package Acceptance Scenarios

| Scenario | Expected result | Threats addressed |
|---|---|---|
| **VA-UI-001 — Screenshot and Recents privacy** | PIN and sensitive App Lock screens are concealed from ordinary capture and recent-app previews as specified | THR-UI-005, THR-CRED-001 |
| **VA-UI-002 — Obscured touch** | Authentication and destructive/protection-reducing controls reject or safely handle obscured input | THR-UI-001, THR-UI-003, THR-ACC-004 |
| **VA-UI-003 — Injected accessibility action** | A non-deliberate injected action cannot complete PIN authentication or silently confirm destructive/protection-reducing action | THR-UI-003, THR-ACC-004 |
| **VA-COMP-001 — External screen launch** | A direct external invocation cannot open authenticated content, create a session, or alter configuration | THR-IPC-001 |
| **VA-COMP-002 — External background invocation** | Crafted intents or broadcasts cannot change authorization or protection settings | THR-IPC-002, THR-IPC-003 |
| **VA-PRIV-001 — Diagnostic inspection** | Bounded records contain no PIN, verifier, key, biometric data, full protected list, or third-party content | THR-CRED-001, THR-CRED-004, THR-AUD-001 |
| **VA-PRIV-002 — Diagnostic failure** | Record loss or write failure does not create or extend authorization | THR-AUD-001, THR-AUD-003 |
| **VA-BUILD-001 — Production package** | The distributed package is signed, non-debuggable, contains no production secret, and enables required sensitive-screen protections | THR-INT-001, THR-INT-004, THR-SUP-003 |
| **VA-BUILD-002 — Retained dependency validation** | Dependencies directly affecting the included security boundary are checked for known critical incompatibility or vulnerability and the production package passes retained security regression scenarios | THR-SUP-001, THR-SUP-002 |

### 8.7 Acceptance Rule

Version 1.0.0 is security-acceptable only when:

- no known reproducible path within the supported application boundary grants App Lock authorization without successful active authentication or a valid unexpired session for the same protected application;
- failed, cancelled, interrupted, or expired authentication creates no session;
- rapid switching, rapid relaunch, process termination, reboot, and supported phone window modes do not create a stale-session bypass;
- retry delay and lockout remain effective across the defined lifecycle events;
- PIN, key, and protected-configuration material are not stored or exposed in plaintext;
- migration, corruption, Keystore invalidation, and destructive reset do not create authorization or a falsely healthy permissive state;
- known loss of a required protection capability is not displayed as Protected;
- essential notifications and bounded diagnostics disclose no unnecessary sensitive information;
- the stated platform limitations are presented accurately and are not represented as prevented.

This acceptance rule does not claim protection during force-stop, after uninstall or application-data clearing, on rooted or system-compromised phones, or on excluded device and profile classes.

---

## Appendix A — Threat-to-Control and Verification Matrix

| Threat group | Threat identifiers | Primary controls | Primary verification |
|---|---|---|---|
| PIN confidentiality and administration | THR-CRED-001 through THR-CRED-004 | SC-AUTH-001, SC-AUTH-002, SC-DATA-001, SC-DATA-003, SC-RESET-001, SC-UI-001, SC-PRIV-002 | VA-AUTH-006, VA-DATA-001, VA-DATA-008 through VA-DATA-010, VA-UI-001, VA-PRIV-001 |
| Authentication correctness and abuse resistance | THR-AUTH-001 through THR-AUTH-004 | SC-AUTH-001, SC-AUTH-003, SC-AUTH-004, SC-SESS-001, SC-UI-003 | VA-AUTH-001 through VA-AUTH-010 |
| Detection and enforcement | THR-ENF-001 through THR-ENF-006 | SC-ENF-001 through SC-ENF-003, SC-HEALTH-001, SC-HEALTH-002, SC-LIFE-001, SC-LIFE-002 | VA-ENF-001, VA-ENF-007 through VA-ENF-012, VA-HEALTH-001 through VA-HEALTH-008 |
| Package-scoped sessions and global relock | THR-SES-001 through THR-SES-004 | SC-SESS-001 through SC-SESS-003, SC-ENF-002, SC-LIFE-001 | VA-AUTH-001, VA-AUTH-005, VA-ENF-003 through VA-ENF-012 |
| Database and cryptographic protection | THR-CRYPTO-001, THR-CRYPTO-002, THR-CRYPTO-004, THR-CRYPTO-005 | SC-DATA-001 through SC-DATA-005, SC-RESET-001 | VA-DATA-001 through VA-DATA-007 |
| UI, component, and peer-service attacks | THR-UI-001 through THR-UI-005; THR-IPC-001 through THR-IPC-003; THR-ACC-004 | SC-UI-001 through SC-UI-003, SC-COMP-001, SC-AUTH-001, SC-RESET-001 | VA-ENF-009, VA-ENF-010, VA-UI-001 through VA-UI-003, VA-COMP-001, VA-COMP-002 |
| Lifecycle and protection health | THR-LIFE-001 through THR-LIFE-005; THR-AUD-001; THR-AUD-003 | SC-LIFE-001, SC-LIFE-002, SC-HEALTH-001, SC-HEALTH-002, SC-PRIV-002 | VA-ENF-011, VA-ENF-012, VA-HEALTH-001 through VA-HEALTH-007, VA-PRIV-002 |
| Corruption, migration, key loss, and destructive reset | THR-REC-001, THR-REC-002, THR-REC-003, THR-REC-005 | SC-DATA-003 through SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | VA-DATA-004 through VA-DATA-010 |
| Production package and dependency integrity | THR-INT-001, THR-INT-004, THR-SUP-001 through THR-SUP-003 | SC-BUILD-001, SC-BUILD-002 | VA-BUILD-001, VA-BUILD-002 and retained security regression scenarios |
| Android platform boundary | THR-PLAT-001 through THR-PLAT-004 | SC-BOUND-001, SC-DATA-002, SC-HEALTH-001, SC-LIFE-001 | Supported API phone verification and explicit limitation review |

---

## Appendix B — Scope Exclusions and User Consequences

| Excluded item | Version 1.0.0 treatment | User consequence |
|---|---|---|
| Vault and private media | No storage, import, viewing, organization, export, or deletion capability | App Lock protects application entry only; it is not a private-file product |
| Backup, restore, and device transfer | No archive, import, recovery, or new-device flow | Local configuration must be set up again after data loss or on another phone |
| Recovery password and accounts | No secondary secret, email, cloud identity, or security questions | Forgotten PIN requires destructive local reset |
| Profiles, schedules, and automation | One global protection and relock policy only | Protection does not change automatically by time, place, connection, or profile |
| Intruder capture and event media | No camera use or intruder library | Failed attempts produce retry/lockout state only |
| Third-party notification access | No reading, masking, replacing, or storing another application's notification content | Only App Lock's own essential notifications are controlled |
| App Lock Accessibility service | No permission request or event-based enhancement | Foreground detection uses Android Usage Access only |
| Device-administrator uninstall prevention | No administrator enrollment | The Android device user may uninstall App Lock or clear its data |
| Network, account, cloud, and remote administration | No routine application network communication | Core operation is local and there is no remote recovery or control |
| Advanced diagnostics and reports | Current health and bounded local records only | No export, long-term history, trends, dashboard, or remote telemetry |
| Non-phone and alternate-profile environments | No support claim or tailored acceptance | Behavior is unspecified outside conventional supported phone installations |
| Android 8 through Android 10 | No support or compatibility claim | Android 11 is the minimum supported version |
| Root/system compromise | No assurance that App Lock can resist a privileged attacker | Security guarantee applies only to non-compromised supported phones |

Established identifiers THR-IPC-004 and THR-INT-002/003 remain reserved because their original subjects depend on excluded cross-application sharing, alternate distribution, or maintenance surfaces. They create no Version 1.0.0 control or verification obligation.

---

## Document Completion Statement

This Threat Model is complete for the Version 1.0.0 scope when the included security functions, retained threat register, control register, failure behavior, residual limitations, and acceptance scenarios are reflected consistently in the corresponding specifications and no excluded capability is treated as a release obligation.
