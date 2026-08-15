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


## 2. Users and Operating Context

The application is intended for an individual who controls a supported Android phone and wants an additional local access check before selected applications can be opened. Use may occur in public, shared, distracting, low-connectivity, or accessibility-assisted contexts.

The user is expected to understand that Android controls process lifetime, startup timing, power restrictions, and permission availability. Where the operating system prevents reliable detection or lock presentation, the application must identify that limitation and guide the user to a safe recovery action.


## 3. Controlled Terms

**Protected application:** An eligible installed application selected for App Lock protection.

**Protection session:** A time-bounded local authorization created after successful PIN or eligible biometric authentication.

**Protected:** Required capabilities are available, protection state is loaded, and the core protection path has passed its current check.

**Degraded:** The core path remains available, but responsiveness or continuity may be affected by an operating-system condition.

**Protection interrupted:** A required capability or core service is unavailable and App Lock cannot currently make its normal protection promise.

**Action required:** The user must complete a stated step before normal protection can resume.

**Unknown or not verified:** App Lock has not yet completed the checks needed to claim a protected state or current evidence is stale.

**Destructive reset:** Removal of all App Lock credentials, protected-application selections, settings, and local diagnostic records, followed by a return to initial setup.


## 4.1 Authentication

#### FR-001 - Initial Authentication Setup

The application shall require creation and confirmation of a numeric PIN before application protection can be enabled. Eligible biometrics may be offered only after the PIN has been established.

Acceptance criteria:

- Setup cannot reach a protected state without a successfully confirmed PIN.
- Leaving setup before PIN confirmation creates no active protection claim or partial credential.
- Completion records only the credential and settings needed by version 1.0.0.

#### FR-002 - Biometric Authentication

The application shall allow an eligible biometric capability approved by the operating system to be used as an optional convenience after PIN setup.

Acceptance criteria:

- The option is shown only when an eligible biometric capability is available and enrolled.
- A biometric result is accepted only when confirmed by the operating system.
- Absence or loss of biometric eligibility leaves PIN authentication fully usable.

#### FR-003 - PIN Authentication

The application shall use a numeric PIN as the required local authentication method for protected applications and sensitive App Lock settings.

Acceptance criteria:

- PIN entry accepts only the supported numeric length and rejects incomplete or incorrect input.
- The entered PIN is not displayed as readable text after entry.
- A correct PIN creates only the session for the current protected application or authenticated settings context, governed by the global relock policy.

#### FR-006 - Multiple Authentication Methods

The application shall support only the combination of required PIN and optional eligible biometrics.

Acceptance criteria:

- PIN remains configured whenever biometrics are enabled.
- Disabling biometrics does not affect the PIN.
- No pattern, knock code, device credential, decoy credential, or additional authentication method is presented.

#### FR-007 - Authentication Fallback

The application shall fall back from unavailable, cancelled, or unsuccessful biometric authentication to PIN without granting access.

Acceptance criteria:

- Biometric failure or cancellation never creates a protection session.
- The user can proceed directly to PIN entry.
- The protected application remains inaccessible until PIN or a later eligible biometric attempt succeeds.

#### FR-008 - Authentication Timeout

The application shall cancel an inactive authentication presentation after a defined period.

Acceptance criteria:

- Expiration closes or replaces the authentication presentation without revealing protected content.
- No session is created when the presentation expires.
- A later attempt begins with a new authentication request.

#### FR-009 - Authentication Retry Limit

The application shall apply a defined limit to consecutive incorrect PIN attempts.

Acceptance criteria:

- Every incorrect PIN increments the current failure count.
- Reaching the threshold activates the delay required by FR-010.
- The threshold cannot be changed from a protected application's lock presentation.

#### FR-010 - Authentication Delay

The application shall impose progressively longer delays after repeated incorrect PIN attempts.

Acceptance criteria:

- Remaining delay is communicated without revealing the PIN or protected application content.
- Authentication input remains unavailable until the delay expires.
- A successful authentication clears the applicable consecutive-failure state.

#### FR-011 - Secure Password Storage

The application shall never store the PIN in readable or reversibly recoverable form.

Acceptance criteria:

- Inspection of application-managed files reveals no readable PIN.
- Verification uses a salted, approved one-way credential representation.
- Destructive reset removes the stored verifier and related credential state.

#### FR-012 - Platform-Protected Key Storage

The application shall protect cryptographic keys using storage controlled by the Android platform and inaccessible to ordinary application data access.

Acceptance criteria:

- Cryptographic keys are not stored as readable application files or configuration values.
- Keys cannot be exported through any version 1.0.0 user action.
- Loss or invalidation of a required key results in a fail-secure, Action required state.

#### FR-013 - Authentication Logging

The application shall keep only the minimum bounded local diagnostic record needed to identify successful authentication and session creation problems.

Acceptance criteria:

- Records contain no PIN, biometric data, or sensitive user-entered value.
- Records are not presented as a user activity-history feature.
- Retention remains bounded and subject to deletion with local application data.

#### FR-014 - Failed Authentication Logging

The application shall maintain the local failure state needed for retry limits, progressive delay, and immediate troubleshooting.

Acceptance criteria:

- Incorrect attempts update the retry state accurately.
- Diagnostic records contain no attempted PIN value or biometric data.
- Version 1.0.0 provides no export or long-term failed-attempt history.

#### FR-015 - Biometric Enrollment Detection

The application shall respond safely when the operating system reports that biometric eligibility or enrollment has changed.

Acceptance criteria:

- An existing biometric convenience state is not trusted after an invalidating change.
- PIN remains available and is required before biometric use is enabled again.
- Protected applications remain locked until valid authentication succeeds.

#### FR-017 - Authentication Session Management

The application shall maintain one global protection-session policy for all protected applications.

Acceptance criteria:

- Session creation, reuse, and expiration follow the same policy for every protected application.
- Reboot and other mandatory relock events invalidate the session.
- No per-application or profile-specific session setting is available.

#### FR-018 - Authentication Cancellation

The application shall allow the user to cancel an authentication request without granting access.

Acceptance criteria:

- Cancellation returns to a safe prior destination or the device home surface.
- The protected application remains inaccessible.
- Cancellation creates no session and does not clear failure delay already in effect.

#### FR-019 - Authentication Method Change

The application shall require the current PIN before changing the PIN or enabling or disabling eligible biometrics.

Acceptance criteria:

- A settings change is rejected when current-PIN verification fails or is cancelled.
- A new PIN must be entered and confirmed before replacing the previous verifier.
- Completion invalidates existing protection sessions.

#### FR-020 - Authentication Recovery

The application shall provide only destructive forgotten-PIN recovery.

Acceptance criteria:

- The recovery explanation states that the PIN cannot be retrieved and local App Lock configuration will be erased.
- A deliberate confirmation is required before destructive reset begins.
- Completion removes credentials, protected-application selections, settings, and local diagnostics and returns to initial setup.
- No recovery password, backup, account, security question, or preserved-data path is offered.

#### FR-022 - Authentication Accessibility Support

Authentication shall remain usable with supported screen readers, text scaling, contrast settings, and touch accessibility needs.

Acceptance criteria:

- Every authentication control has a meaningful spoken label and state.
- Focus follows the visual task order and does not move behind the protection presentation.
- PIN privacy is maintained without preventing the user from locating and operating keypad controls.
- Authentication remains usable at the supported maximum text and display scaling.

#### FR-023 - Authentication Performance

Authentication processing shall complete without noticeable avoidable delay under normal supported-phone conditions.

Acceptance criteria:

- Successful PIN verification and unlock complete within one second under the defined reference conditions.
- Biometric fallback to PIN remains responsive.
- Security work is not weakened or skipped to meet the timing target.

#### FR-024 - Offline Authentication

All authentication and session decisions shall operate without an Internet connection.

Acceptance criteria:

- PIN and eligible biometric authentication succeed in airplane mode.
- Loss of connectivity does not change credential, session, or relock behavior.
- Authentication does not transmit credentials, protected-application selections, or diagnostic data.


## 4.2 Lock Engine

#### FR-026 - Foreground Application Detection

The application shall use Android Usage Access as the single baseline for determining which application is in the foreground.

Acceptance criteria:

- Protection cannot be declared active until Usage Access is granted and verified.
- Eligible protected applications are identified accurately under supported phone conditions.
- No App Lock Accessibility service is requested, offered, or required.

#### FR-027 - Lock Screen Display

The application shall present its authentication surface, drawn as a system overlay over the protected application, when a protected application becomes active and no valid protection session applies.

Acceptance criteria:

- The protection surface appears before avoidable interaction with protected content.
- The surface identifies the authentication task without exposing sensitive application content.
- Successful authentication is required before access continues.

#### FR-028 - Overlay Security

The protection presentation shall prevent interaction with the protected application until authentication succeeds.

Acceptance criteria:

- Back, cancellation, task changes, and interruptions do not reveal or activate protected content.
- Touch and keyboard input cannot pass through to the protected application.
- Failure to maintain the presentation results in a safe exit and a Protection interrupted state.

#### FR-029 - Unlock Session Validation

The application shall validate the current global protection session before deciding whether to present authentication.

Acceptance criteria:

- A valid unexpired session permits access only under the configured global policy.
- An expired, invalidated, or unverifiable session requires authentication.
- Session validation completes before access is granted.

#### FR-030 - Immediate Relock

The application shall provide one global option to require authentication again after leaving a protected application.

Acceptance criteria:

- When enabled, returning to any protected application requires authentication unless a stricter mandatory event already ended the session.
- When disabled, the global relock rule still applies to each package-scoped session.
- No per-application immediate-relock option is shown.

#### FR-031 - Screen-Off Relock

The application shall invalidate active protection sessions when the phone screen turns off.

Acceptance criteria:

- A protected application requires authentication after the screen is turned on again.
- Session invalidation persists if the application process stops while the screen is off.
- No protected content is made available solely because the device was unlocked.

#### FR-032 - Timeout-Based Relock

The application shall support one global inactivity timeout for protection sessions.

Acceptance criteria:

- Expiration requires authentication on the next protected-application access.
- The current setting is presented consistently in settings and applied to every protected application.
- No per-application, profile, or schedule timeout exists.

#### FR-033 - Device Restart Protection

The application shall treat every device restart as an invalidation of all protection sessions.

Acceptance criteria:

- No pre-restart protection session survives reboot.
- Protected applications require authentication after detection resumes.
- Protection status remains Unknown or not verified, or Action required, until required post-restart checks complete.

#### FR-034 - Lock Engine Initialization

The application shall restore the core protection path after device restart when Android permits execution.

Acceptance criteria:

- Protected-application selections and global policy load without user re-entry.
- Required health checks run before a protected state is reported.
- If Android prevents timely startup, the user is informed at the next available opportunity and receives recovery guidance.

#### FR-035 - Background Service Persistence

The application shall maintain or restore core monitoring through supported Android lifecycle conditions while minimizing resource use.

Acceptance criteria:

- Normal backgrounding, screen changes, and process recreation do not silently disable protection.
- When Android stops required work, the application attempts an approved recovery and verifies the result.
- Unrecoverable interruption produces a truthful Action required notification or status.

#### FR-036 - Protected Application List Monitoring

Changes to the protected-application list shall take effect without restarting App Lock.

Acceptance criteria:

- Newly enabled protection is used on the next applicable access.
- Disabled protection no longer presents App Lock after the change is confirmed.
- The displayed selection state and the active policy remain consistent.

#### FR-038 - Application Removal Detection

The application shall remove obsolete protection records when an installed protected application is removed.

Acceptance criteria:

- Removed applications no longer appear as active protected targets.
- Cleanup does not alter other protected-application selections.
- Reinstallation is treated as a new eligible application and is not silently protected from a stale record.

#### FR-039 - Multi-Window Support

The application shall fail safely when a protected application is used in phone split-screen or another supported resizable window state.

Acceptance criteria:

- An unauthenticated protected application cannot be intentionally activated through split-screen.
- If reliable lock presentation is unavailable in the current window state, protected access is denied and the limitation is explained.
- No tablet, foldable, or multi-pane optimization is required.

#### FR-040 - Picture-in-Picture Handling

The application shall apply a safe rule when a protected application enters or returns from picture-in-picture on a supported phone.

Acceptance criteria:

- Picture-in-picture does not create a new valid protection session.
- Returning to interactive protected content requires authentication when the global policy requires it.
- If the operating system prevents reliable protection, the application denies continued access where possible and reports the limitation truthfully.

#### FR-041 - Recent Applications Protection

App Lock's sensitive surfaces shall not expose protected application content in the Android recent-apps view.

Acceptance criteria:

- Authentication and sensitive settings previews are blanked or replaced.
- Returning from recents reevaluates the current protection session.
- A captured recent-app preview does not reveal a PIN, biometric result, or diagnostic detail.

#### FR-042 - Lock Policy Evaluation

Before granting access, the application shall evaluate only the enabled protection state, the session for the current protected application, global relock rules, and current protection health.

Acceptance criteria:

- The result is deterministic for the same inputs.
- A missing or invalid input defaults to requiring authentication or denying access.
- No schedule, location, network, profile, or per-application policy participates in the decision.

#### FR-044 - Lock-Presentation (Overlay) Capability Verification

The application shall verify that the operating system grants the "Display over other apps" (system overlay) permission required to present the lock, before a protected state is claimed. This permission is mandatory: because detection runs from a background service, the lock is drawn as a system overlay, and without it protection cannot be enforced.

Acceptance criteria:

- A missing or revoked overlay permission is detected before a protected state is claimed, and results in Action required rather than a protected state.
- Guidance identifies the exact user action needed to grant "Display over other apps" without promising that Android settings can be changed automatically.
- Protection is rechecked when the user returns from Android settings.

#### FR-046 - Lock Engine Failure Recovery

The application shall attempt to restore the core protection path after a recoverable monitoring or presentation failure.

Acceptance criteria:

- Recovery never creates or extends an authentication session.
- The protected state is reported only after the restored path passes verification.
- Failed recovery produces an Action required state and clear guidance.

#### FR-047 - Duplicate Overlay Prevention

The application shall prevent simultaneous or stacked authentication presentations for the same protection event.

Acceptance criteria:

- Rapid application switching produces at most one active authentication task.
- Repeated detection events do not reset an active retry delay or create multiple sessions.
- The visible authentication state remains responsive and consistent.

#### FR-048 - Overlay Timeout

An inactive protection presentation shall use the timeout defined by FR-008.

Acceptance criteria:

- Expiration grants no access and creates no session.
- The protected application remains inaccessible after dismissal.
- A later attempt starts a fresh protection presentation.

#### FR-049 - Lock Engine Performance

The application shall detect a protected-application transition and begin enforcement within the approved lock-detection target under normal supported-phone conditions.

Acceptance criteria:

- The measured event begins when Usage Access reports the applicable foreground transition and ends when App Lock begins its protection response.
- The median and upper-percentile targets are stated consistently with the NFR.
- No avoidable protected-content exposure is accepted as a performance tradeoff.

#### FR-050 - Lock Engine Battery Optimization

Core monitoring shall minimize unnecessary wake-ups, polling, and background activity without reducing required protection.

Acceptance criteria:

- Idle monitoring remains within the approved resource limits.
- Nonessential diagnostics and cleanup may be deferred before protection work.
- Battery-saving behavior never converts a Protection interrupted state into a false Protected state.

#### FR-051 - Application Switching Detection

The application shall reevaluate protection when the user switches between protected and unprotected applications.

Acceptance criteria:

- Every transition to a protected application applies the current global policy.
- Rapid repeated switching does not reuse an expired or invalid session.
- Transitions to unprotected applications do not present App Lock unnecessarily.

#### FR-052 - Home Screen Transition Handling

The application shall update protection-session state when the user leaves a protected application for the device home surface.

Acceptance criteria:

- Global immediate-relock and timeout rules are applied consistently.
- Returning from Home requires authentication when the current policy requires it.
- Home transition handling does not depend on profiles or schedules.

#### FR-053 - Task Switching Handling

The application shall reevaluate protection when a protected task is selected from Android recents.

Acceptance criteria:

- An invalid or expired session causes authentication before continued access.
- Selecting another task cannot dismiss authentication into protected content.
- Rapid task switching remains consistent with FR-047.

#### FR-054 - Lock Event Logging

The application shall retain only bounded local diagnostic records needed to identify lock-presentation and transition failures.

Acceptance criteria:

- Records exclude protected content and user-entered credentials.
- Records are automatically bounded and removed by destructive reset.
- No user-visible lock history, report, or export is provided.

#### FR-055 - Lock Engine Health Monitoring

The application shall maintain a current health result for Usage Access, policy readiness, session readiness, core monitoring, and lock presentation.

Acceptance criteria:

- The health result maps deterministically to Protected, Degraded, Protection interrupted, Action required, or Unknown or not verified.
- A failed required check cannot be displayed as protected.
- Recovery is followed by a new verification before the state improves.


## 4.3 Protected Applications Management

#### FR-056 - Protected Application Selection

The application shall display eligible installed applications and allow the user to enable or disable protection for each application individually.

Acceptance criteria:

- Each row clearly identifies the application and current protection state.
- A change is confirmed visually and becomes active without restarting App Lock.
- App Lock and operating-system components that cannot be safely protected are not offered as eligible targets.

#### FR-057 - Application Search

The application shall allow installed applications to be located by their user-visible name.

Acceptance criteria:

- Results update as text is entered and include partial name matches.
- Clearing the query restores the full eligible list.
- Search does not expose internal identifiers in the primary interface.

#### FR-060 - Enable/Disable Protection

The application shall allow protection to be enabled or disabled for one application at a time.

Acceptance criteria:

- Enabling protection requires a ready authentication and protection configuration.
- Disabling protection from settings requires current authentication.
- The resulting status is reflected in both the application list and active protection policy.

#### FR-072 - Protected Application Icons

Protected-application management shall use a clear visual and textual indication of protection state.

Acceptance criteria:

- Protection state is not communicated by color alone.
- Icons remain distinguishable at supported text and display scaling.
- Screen readers announce the application name and protection state together.

#### FR-073 - Application Information

The application shall show only the information needed to identify an eligible application and understand its current App Lock status.

Acceptance criteria:

- The interface includes the user-visible application name, icon, and current protection state.
- Technical identifiers and installation history are not required in the primary flow.
- No use history, unlock count, or session statistics are shown.

#### FR-078 - Application Removal Cleanup

The application shall clean up stale local protection data after an application is uninstalled.

Acceptance criteria:

- Only data associated with the removed application is deleted.
- Cleanup is safe if the application is removed while App Lock is not running.
- Reinstallation does not restore the old protection state automatically.


## 4.4 Privacy Protection

#### FR-095 - Recent Apps Preview Protection

The application shall prevent sensitive App Lock content from appearing in recent-app previews.

Acceptance criteria:

- PIN entry, biometric fallback, settings authentication, reset confirmation, and diagnostic details are obscured.
- Preview protection remains active when App Lock moves to the background unexpectedly.
- Returning from recents restores an appropriate safe state.

#### FR-096 - Screenshot Prevention

The application shall prevent screenshots and screen recording of authentication and other sensitive App Lock screens where Android supports that protection.

Acceptance criteria:

- PIN entry, biometric fallback, sensitive settings, reset confirmation, and diagnostics use screenshot protection.
- Failure of the operating system to enforce the restriction is not represented as guaranteed prevention.
- Non-sensitive help content may remain available to accessibility and support workflows.

#### FR-099 - Secure Keyboard Mode

PIN entry shall use a controlled numeric input surface that limits credential exposure.

Acceptance criteria:

- Copy, paste, autofill, predictive input, and readable credential retention are unavailable.
- Key controls remain operable with supported accessibility services.
- The interface does not reveal the complete PIN after entry.

#### FR-100 - Clipboard Protection

The application shall not place a PIN or other authentication secret on the system clipboard or accept it from the clipboard.

Acceptance criteria:

- Copy actions are unavailable for credential fields.
- Paste and autofill do not populate a PIN field.
- Existing unrelated clipboard content is not exposed or modified unnecessarily.

#### FR-105 - Privacy Feature Management

The application shall provide settings only for privacy behavior included in version 1.0.0.

Acceptance criteria:

- Settings explain recent-screen, screenshot, and App Lock notification privacy behavior.
- No intruder, concealment, notification-interception, Vault, or decoy setting is shown.
- A setting cannot weaken mandatory PIN-entry privacy.


## 4.5 Vault

FR-106 through FR-125 are not included as normative Version 1.0.0 requirements. Their identifiers remain reserved and are not renumbered or reused.

Version 1.0.0 creates no screen, data, permission, background work, compatibility, migration, or acceptance obligation for Vault storage, file handling, Vault cryptography, Vault migration, and Vault verification.

## 4.6 Scheduling and Automation

FR-126 through FR-145 are not included as normative Version 1.0.0 requirements. Their identifiers remain reserved and are not renumbered or reused.

Version 1.0.0 creates no screen, data, permission, background work, compatibility, migration, or acceptance obligation for schedules, profiles, triggers, rules, recommendations, overrides, and automation records.

## 4.7 Notifications and User Experience

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


## 4.8 Security Requirements

#### FR-161 - End-to-End Local Security Architecture

The application shall protect credentials, protected-application selections, settings, and retained local diagnostics throughout their local lifecycle.

Acceptance criteria:

- Sensitive data is protected at rest and exposed only for the minimum time needed for an authorized operation.
- Authentication and policy decisions are performed locally.
- No Vault, backup, cloud, intruder-media, location, or remote-command data path exists.

#### FR-162 - Secure Data Storage

The application shall securely store credentials, protected-application configuration, settings, and bounded diagnostic records.

Acceptance criteria:

- Sensitive values are not readable through ordinary inspection of application-managed storage.
- Stored data is available only to the application under the supported Android security model.
- Corrupt, missing, or unverifiable security data causes a fail-secure result and clear recovery guidance.

#### FR-163 - Cryptographic Key Management

The application shall generate, use, invalidate, and remove local cryptographic keys through platform-protected facilities.

Acceptance criteria:

- Keys are not embedded in the application package or stored as readable values.
- A key is used only for its documented local purpose.
- Key invalidation cannot silently bypass credential or data protection.

#### FR-164 - Data Encryption at Rest

Sensitive local application data shall be encrypted at rest where encryption is required by its classification.

Acceptance criteria:

- Credential-related data and other highly sensitive local records are never stored in readable form.
- Diagnostic records exclude secrets even when encrypted.
- The encryption scope contains no deferred Vault, backup, or intruder-media data.

#### FR-170 - Debug Protection

Distributed builds shall prevent debug access and sensitive diagnostic exposure.

Acceptance criteria:

- Distributed builds do not expose debug-only controls or verbose sensitive output.
- Authentication secrets and cryptographic material are absent from logs and error messages.
- A debug-capable development build cannot be confused with the distributed build.

#### FR-171 - Screen Capture Protection

The application shall protect authentication, sensitive settings, reset confirmation, and diagnostic screens from screen capture where Android permits.

Acceptance criteria:

- The protected-screen inventory matches FR-096.
- Protection remains applied during backgrounding and task switching.
- The application makes no guarantee for screens owned by protected third-party applications.

#### FR-172 - Clipboard Security

Authentication secrets shall never be copied to, accepted from, or intentionally exposed through the system clipboard.

Acceptance criteria:

- PIN controls provide no copy, cut, paste, share, or autofill action.
- Application diagnostics and help never print an authentication secret.
- Clipboard behavior remains consistent across all supported API levels.

#### FR-173 - Secure Memory Handling

The application shall minimize how long authentication input, cryptographic material, and decrypted sensitive values remain in memory.

Acceptance criteria:

- Sensitive values are released or cleared as soon as the operation permits.
- Sensitive state is not retained in long-lived user-interface or diagnostic objects.
- Process recreation does not restore readable credential input.

#### FR-174 - Authentication Brute Force Protection

The application shall resist repeated PIN guessing through attempt tracking and progressive delay.

Acceptance criteria:

- Restarting the authentication presentation does not bypass an active delay.
- Failure state remains consistent across rapid task changes and process recreation where supported.
- No intruder capture, remote alert, or device-wide lockout is required.

#### FR-177 - Data Privacy Controls

The application shall provide only privacy controls relevant to retained local data and App Lock's own notifications.

Acceptance criteria:

- The user can understand what local diagnostic information is retained and can remove all App Lock data through destructive reset.
- App Lock notifications use privacy-preserving content by default.
- No analytics, cloud synchronization, intruder capture, notification interception, or advertising data control is shown because those data flows are absent.

#### FR-178 - Security Audit Logs

The application shall maintain a bounded local security record only where needed to enforce retry behavior, explain current protection failure, or verify recovery.

Acceptance criteria:

- Records exclude PIN values, biometric data, protected content, and unnecessary application-use detail.
- Records expire or rotate within a fixed bound.
- No history browser, report, archive, share action, or export is provided.

#### FR-179 - Permission Change Detection

The application shall detect changes to Usage Access and other capabilities required for the version 1.0.0 protection path.

Acceptance criteria:

- Capability state is checked at startup, return from Android settings, and before a protected state is claimed.
- Revocation changes the protection-health state immediately after detection.
- The user receives accurate restoration guidance.
- No Accessibility-service, camera, location, or third-party notification-access state is monitored.

#### FR-180 - Security Health Monitoring

The application shall present a consolidated protection-health result for the complete core path.

Acceptance criteria:

- Checks cover PIN readiness, optional biometric eligibility, Usage Access, lock-presentation readiness, service continuity, policy loading, and local-data integrity.
- The combined result maps to one controlled protection state.
- Backup, Vault, profiles, schedules, remote services, and root-detection status do not appear.


## 4.9 Application Settings and Configuration Management

#### FR-181 - Application Settings Dashboard

The application shall provide one settings destination for retained version 1.0.0 behavior.

Acceptance criteria:

- Settings are grouped into authentication, session and relock, protection health, privacy, notifications, help, diagnostics, and destructive reset.
- Current values and unavailable states are clear before an action is selected.
- No section exists for Vault, backup, profiles, schedules, automation, intruder features, disguise, notification interception, export, or advanced administration.

#### FR-182 - Security Settings Management

The application shall allow authenticated management of PIN, eligible biometrics, retry behavior explanation, the shared session-duration setting, and global relock behavior.

Acceptance criteria:

- Sensitive changes require current PIN verification.
- Invalid combinations are rejected before they can weaken protection.
- Changes apply consistently to every protected application.

#### FR-183 - Privacy Settings Management

The application shall explain and manage only App Lock notification privacy, protected presentation, screenshot protection, and recent-app privacy.

Acceptance criteria:

- Mandatory authentication privacy cannot be disabled.
- Optional App Lock notification presentation never reveals protected-application activity by default.
- No concealment, intruder, Vault, or notification-interception control appears.

#### FR-184 - Application Default Settings

The application shall maintain one global default configuration for authentication sessions, relock behavior, and App Lock notification privacy.

Acceptance criteria:

- Newly protected applications immediately use the global configuration.
- The application does not prompt for a profile or per-application policy.
- Safe defaults are restored after destructive reset.

#### FR-192 - Factory Reset

The application shall provide an authenticated destructive reset from settings and the destructive forgotten-PIN behavior defined by FR-020.

Acceptance criteria:

- The settings action requires current PIN verification and a separate final confirmation.
- The warning identifies every local data category that will be removed.
- Reset completion leaves no active session or protected state and returns to initial setup.

#### FR-193 - Data Management Controls

The application shall provide only the local data controls needed for bounded diagnostics, cache, and all-data removal.

Acceptance criteria:

- Clearing cache does not remove credentials or protected-application selections.
- Clearing bounded diagnostics removes only diagnostic records.
- All-data removal is treated as destructive reset.
- No Vault, backup, export, archive, or cross-device data control is shown.

#### FR-195 - Configuration Validation

The application shall validate every retained configuration change before applying it.

Acceptance criteria:

- Invalid PIN, session, relock, permission, and capability states are rejected or resolved to safe defaults.
- A failed save leaves the last valid configuration intact.
- Validation cannot create support for an excluded device, feature, or policy type.


## 4.10 Backup and Recovery

FR-196 through FR-205 are not included as normative Version 1.0.0 requirements. Their identifiers remain reserved and are not renumbered or reused.

Version 1.0.0 creates no screen, data, permission, background work, compatibility, migration, or acceptance obligation for backup, restore, recovery password, retention, and new-device migration.

## 4.11 Performance

#### FR-206 - Application Startup Performance

The application shall reach an interactive primary screen within the startup thresholds defined by NFR-PERF-001 under normal reference-phone conditions.

Acceptance criteria:

- Startup does not block the interface longer than necessary for required safety checks.
- Protection status remains Unknown or not verified until those checks finish.
- Startup failure produces a recoverable message or safe Action required state.

#### FR-207 - Lock Detection Response Time

The application shall respond promptly after Usage Access reports that a protected application has become foreground.

Acceptance criteria:

- The timing boundary and target match FR-049 and NFR-PERF-012.
- Normal detection begins protection within the approved threshold on the declared reference phone.
- Performance testing includes cold, warm, rapid-relaunch, and task-switch cases.

#### FR-208 - Memory Usage Optimization

The application shall control memory use during protection monitoring, authentication, application-list browsing, and local diagnostics.

Acceptance criteria:

- Extended operation reveals no continuing memory growth attributable to unreleased application resources.
- Authentication input and temporary security data are released promptly.
- Memory pressure degrades nonessential work before core protection.

#### FR-209 - CPU Usage Optimization

The application shall avoid unnecessary processor use while monitoring with Usage Access.

Acceptance criteria:

- Idle monitoring avoids continuous high-frequency polling.
- Processor use returns to the defined idle range after a protection event.
- Diagnostic collection does not compete with lock presentation or authentication.

#### FR-210 - Battery Optimization Mode

The application shall reduce nonessential cleanup and diagnostic activity when device resources are constrained while preserving the core protection path.

Acceptance criteria:

- Authentication, Usage Access evaluation, lock presentation, and mandatory relock behavior remain active.
- Deferred nonessential work resumes when conditions permit.
- No user-configurable battery automation rule or threshold is required.

#### FR-211 - Adaptive Background Monitoring

The application shall adapt core monitoring to supported Android lifecycle and screen states without becoming a general automation feature.

Acceptance criteria:

- Monitoring behavior distinguishes active use, backgrounding, screen off, and restart recovery.
- Adaptation does not use Wi-Fi, location, Bluetooth, calendar, charging, or user schedules.
- Every adaptation preserves or truthfully downgrades the protection-health state.

#### FR-213 - Application Stability

The application shall remain stable during normal and abnormal core workflows.

Acceptance criteria:

- Failures do not corrupt committed credentials, settings, or protected-application selections.
- Recoverable failures return to a defined state without device reboot.
- Sensitive details are excluded from user-visible error information.

#### FR-214 - Service Recovery

The application shall restore services required for Usage Access evaluation, lock presentation, and its own essential notification after recoverable termination.

Acceptance criteria:

- Recovery is attempted using behavior permitted by the supported Android version.
- Protection is not reported as restored until verification succeeds.
- No notification listener, backup, Vault, scheduler, or automation service is required.

#### FR-215 - Device Compatibility

The application shall support only conventional Android phones on API levels 30 through 35 within the declared test matrix.

Acceptance criteria:

- Portrait, secure functional landscape, common phone densities, and supported text scaling are verified.
- PIN-only operation works on phones without eligible biometrics.
- No claim is made for tablets, foldables, desktop modes, work profiles, cloned applications, secondary users, or untested manufacturer-specific behavior.


## 4.12 Administration, Diagnostics, and Maintenance

#### FR-216 - Security Dashboard

The application shall provide a concise protection-status screen rather than a score or analytics dashboard.

Acceptance criteria:

- The screen shows credential readiness, protected-application count, Usage Access, lock-presentation readiness, core service state, local-data integrity, and current protection state.
- Each non-ready item provides a direct explanation or recovery action.
- Profiles, backup, Vault, event trends, and risk scores are absent.

#### FR-217 - Security Health Assessment

The application shall evaluate whether the complete version 1.0.0 protection path is ready.

Acceptance criteria:

- The same inputs always produce the same controlled state.
- A failed required input cannot be outweighed by unrelated healthy inputs.
- The result contains no backup, automation, Vault, root, cloud, or intruder criterion.

#### FR-218 - Permission Monitoring

The application shall monitor Usage Access and any other capability required to present or communicate core protection.

Acceptance criteria:

- Required capability changes are reflected at startup and after returning from Android settings.
- Loss of a required capability updates status and notification content promptly.
- Camera, location, protected-application notification access, and an App Lock Accessibility service are not monitored or requested.

#### FR-219 - System Diagnostic Scan

The application shall provide an on-device check of required capabilities, credential readiness, policy loading, core service status, and local-data integrity.

Acceptance criteria:

- Results are presented as pass, degraded, failed, or not verified with plain-language explanation.
- Running the scan does not change settings or grant a session.
- Results cannot be exported and contain no secrets or protected content.

#### FR-220 - Application Event Logging

The application shall keep a bounded local diagnostic record for startup, required-capability change, protection-service change, lock-presentation failure, authentication delay state, data error, and recovery attempt.

Acceptance criteria:

- The record contains only the minimum context needed to explain the current issue.
- Retention is fixed and automatically bounded.
- No event-history screen, usage analytics, report, or export is provided.

#### FR-221 - Error Detection and Reporting

The application shall detect core errors and provide a safe recovery action when one is available.

Acceptance criteria:

- Messages describe the user-visible effect without internal technical detail.
- Errors never reveal credentials, cryptographic material, protected content, or storage locations.
- When recovery is unavailable, the application remains fail-secure and explains destructive reset if applicable.

#### FR-224 - Application Repair Function

The application shall provide limited repair actions for core protection readiness.

Acceptance criteria:

- Available actions may retry core initialization, recheck Usage Access and lock presentation, restore safe invalid settings, or clear temporary cache.
- Repair does not fabricate missing credentials, recover deleted data, or restore from backup.
- Completion runs a new health verification and reports the actual result.


## 4.13 Release Quality

#### FR-228 - Database Migration Management

The application shall provide a defined migration path for every version 1.x local schema change.

Acceptance criteria:

- Supported in-place updates preserve valid credentials, protected-application selections, and retained settings.
- Migration either completes fully or leaves the previous committed data recoverable.
- Cross-device migration and deferred-feature data formats are not required.

#### FR-229 - Database Integrity Verification

The application shall verify local database integrity before relying on stored security policy.

Acceptance criteria:

- Corruption is detected before invalid data can grant access.
- Recoverable inconsistencies are repaired without weakening policy.
- Unrecoverable corruption results in fail-secure guidance and, when necessary, destructive reset.

#### FR-230 - Background Processing

Essential local maintenance that could block interaction shall run without blocking the primary user interface.

Acceptance criteria:

- Only database maintenance, integrity checking, diagnostic cleanup, cache cleanup, and secure local deletion are included.
- Protection and authentication work take priority.
- Vault, backup, restore, report generation, and bulk-file work are absent.

#### FR-231 - Startup Health Check

The application shall check local data, credential readiness, Usage Access, lock-presentation readiness, global policy, and core service state during startup.

Acceptance criteria:

- A protected state is not shown before required checks complete.
- Failed checks map to a controlled Degraded, Protection interrupted, Action required, or Unknown or not verified state.
- Optional biometric unavailability does not by itself interrupt PIN protection.

#### FR-232 - Dependency Validation

The application shall detect absence or failure of a capability required for its core behavior.

Acceptance criteria:

- Failure of a required protection capability prevents a false protected state.
- Failure of an optional capability, such as eligible biometrics, leaves the secure PIN path available.
- Recovery guidance identifies a user action only when one is actually available.

#### FR-233 - Permission Verification

The application shall verify Usage Access and other required version 1.0.0 operating-system capabilities before enabling protection.

Acceptance criteria:

- Verification occurs during onboarding, startup, return from Android settings, and health checks.
- Missing capability causes a truthful state and guidance.
- The application does not verify or request permissions belonging only to excluded features.

#### FR-234 - Build Version Identification

The application shall expose sufficient version information for support and compatibility decisions.

Acceptance criteria:

- The settings or help interface shows the public version and build identifier.
- Local schema compatibility can be determined during startup and update.
- Sensitive build paths, credentials, or internal environment values are not displayed.

#### FR-235 - Release Validation

Version 1.0.0 shall be accepted only on evidence for the retained functional, security, accessibility, migration, compatibility, and performance requirements.

Acceptance criteria:

- Evidence covers the declared API 30 through 35 phone matrix.
- Excluded features and device classes do not create test obligations.
- Any known limitation affecting the core protection promise is stated accurately before distribution.

#### FR-237 - Safe Default Configuration

Initial and reset configuration shall favor secure, understandable core behavior.

Acceptance criteria:

- PIN is required, no protection session exists, protected applications are unselected, and notification content is privacy preserving.
- Protection remains Unknown or not verified until required capabilities and checks are complete.
- No deferred feature is silently enabled or represented.

#### FR-238 - Configuration Validation

Stored and newly entered configuration shall be validated before use.

Acceptance criteria:

- Unsupported, incomplete, or conflicting values are rejected or replaced by documented safe defaults.
- A validation failure cannot grant protected access.
- Only settings included in version 1.0.0 are accepted.

#### FR-239 - Secure Error Handling

Error handling shall protect secrets and preserve a safe access decision.

Acceptance criteria:

- User messages and local diagnostics contain no PIN, cryptographic key, sensitive path, protected content, or raw database statement.
- An uncertain security result denies access or requires fresh authentication.
- The interface provides an actionable next step where one exists.

#### FR-240 - Graceful Failure

Failure of a noncritical capability shall not unnecessarily disable PIN-based core protection, while failure of a required capability shall be reported truthfully.

Acceptance criteria:

- Optional biometric loss falls back to PIN.
- Nonessential diagnostics or cleanup may fail without granting access.
- Usage Access, policy, local-data, or lock-presentation failure cannot be shown as normal protection.

#### FR-241 - Application State Recovery

After process termination or restart, the application shall restore protected-application selections and global policy while treating authentication sessions according to the mandatory relock rules.

Acceptance criteria:

- Committed configuration is restored consistently.
- No uncertain prior session is treated as valid.
- Recovery completes with a new protection-health verification.

#### FR-242 - Runtime Self-Test

The application shall provide a bounded self-test for authentication readiness, Usage Access, lock presentation, policy loading, core service state, and local data.

Acceptance criteria:

- The self-test does not require Vault, backup, cloud, notification interception, automation, or an Accessibility service.
- Test results match the protection-status screen.
- A passing result cannot override a current failed required capability.

#### FR-243 - Secure Update Compatibility

Supported in-place updates shall preserve valid version 1.x credentials, protected-application selections, and retained settings.

Acceptance criteria:

- The first launch after update validates schema, keys, settings, Usage Access, and policy readiness.
- Migration failure does not silently replace a stricter policy with a weaker one.
- No backup restore, cross-device transfer, or deferred-feature migration is promised.

#### FR-246 - Production Logging Configuration

Distributed builds shall use privacy-safe bounded local logging appropriate to version 1.0.0 diagnostics.

Acceptance criteria:

- Debug detail and sensitive values are suppressed.
- Retained events are limited to those required by FR-220 and FR-276.
- No remote telemetry, user export, or long-term report is enabled.


## 4.14 Operational Resilience

#### FR-251 - Automatic Service Recovery

The application shall attempt automatic recovery after recoverable termination of the core protection path.

Acceptance criteria:

- Recovery follows behavior permitted by the supported Android version.
- Existing sessions are not extended by recovery.
- Protected status returns only after verification succeeds.

#### FR-252 - Foreground Service Monitoring

Where Android requires an ongoing service and notification for core protection, the application shall monitor that service's readiness.

Acceptance criteria:

- Loss of the required service updates protection health.
- The associated notification uses privacy-preserving content.
- No notification-listener, backup, Vault, scheduler, or automation service is included.

#### FR-254 - Notification Service Recovery

The application shall recover only the notification capability used for its own essential protection messages.

Acceptance criteria:

- Missing runtime notification permission is detected on Android versions where it applies.
- Core locking continues where technically possible, but the degraded communication state is explained in the application.
- Version 1.0.0 does not access protected-application notifications.

#### FR-255 - Permission Change Detection

The application shall detect loss or restoration of Usage Access and other required core capabilities.

Acceptance criteria:

- Detection updates health state and user guidance.
- Restoration is verified before normal protection is reported.
- No excluded-feature permission participates in this requirement.

#### FR-256 - Graceful Degradation

The application shall preserve the safest available core behavior when a noncritical capability is unavailable.

Acceptance criteria:

- PIN remains available when biometrics are unavailable.
- Nonessential diagnostics or cleanup can be deferred.
- Loss of a required detection or presentation capability is reported as Protection interrupted or Action required, not silently Degraded.

#### FR-257 - Transaction Rollback

Credential, protected-application, settings, and local database changes shall complete atomically or leave the last committed valid state intact.

Acceptance criteria:

- Interrupted writes do not create a partial PIN or partial protection selection.
- Rollback never substitutes an unprotected default for a valid stricter setting without notice.
- Backup, Vault import, and Vault export transactions are outside the requirement.

#### FR-259 - Secure Temporary File Cleanup

Temporary files and caches created by version 1.0.0 shall be removed when no longer required.

Acceptance criteria:

- Cleanup covers only local diagnostics, database maintenance, and interface cache generated by retained features.
- Temporary content contains no readable PIN or cryptographic key.
- Media processing, document conversion, Vault, and backup temporary files are absent.

#### FR-260 - Startup Recovery

After abnormal termination, the application shall restore committed core configuration and verify protection readiness.

Acceptance criteria:

- Protected-application selections and global policy remain consistent.
- Authentication state follows mandatory relock behavior.
- A failed readiness check produces a controlled non-protected state.

#### FR-261 - Unexpected Shutdown Recovery

Unexpected process or device shutdown shall not corrupt committed local security data.

Acceptance criteria:

- Startup integrity and migration checks detect incomplete writes.
- Core monitoring resumes when Android permits it.
- The recovery result is available through bounded local diagnostics.

#### FR-262 - Database Recovery

The application shall attempt safe local database recovery without relying on backup.

Acceptance criteria:

- Integrity is checked before repair is attempted.
- A repair preserves valid security policy or remains fail-secure.
- If safe repair is impossible, the user is told that destructive reset is the available recovery.

#### FR-264 - Configuration Recovery

The application shall recover retained configuration to the last valid committed state or documented safe defaults.

Acceptance criteria:

- Valid credentials and protected selections are preserved whenever possible.
- A fallback never claims protection before required checks complete.
- The user is informed when any setting was replaced or lost.

#### FR-265 - Retry Policy

The application shall use bounded retry for local database access, core service initialization, and protection-health checks.

Acceptance criteria:

- Retry stops after the defined limit and changes to an actionable failure state.
- Retry does not block authentication indefinitely or consume excessive resources.
- No backup, restore, cloud, or bulk file operation is included.

#### FR-266 - Resource Exhaustion Protection

The application shall respond safely to low storage, memory pressure, and other constrained phone resources.

Acceptance criteria:

- Core protection takes priority over cleanup and diagnostics.
- An operation that cannot complete safely leaves committed data intact.
- A user-visible warning is provided when user action is required.

#### FR-267 - Storage Capacity Monitoring

The application shall detect when available local storage is insufficient for safe operation.

Acceptance criteria:

- The warning appears before a required database or migration write would predictably fail.
- Nonessential cache and expired diagnostics may be removed safely.
- No backup or Vault capacity calculation is performed.

#### FR-268 - Watchdog Monitoring

The application shall check the continuing readiness of the core Usage Access, policy, session, and lock-presentation path.

Acceptance criteria:

- A failed check triggers bounded recovery and verification.
- Repeated failure changes the protection state and prompts user action.
- Vault, rule-engine, scheduler, and notification-listener checks are absent.

#### FR-269 - Self-Diagnostics

The application shall allow an authenticated user to run the core diagnostic checks defined by FR-219 and FR-242.

Acceptance criteria:

- The result is consistent with current protection health.
- Running diagnostics does not alter the security policy or create a session.
- Results remain on device and cannot be exported.

#### FR-270 - Recovery Logging

The application shall keep a bounded local record of recovery reason, action, and outcome.

Acceptance criteria:

- Records contain no secrets or protected content.
- Retention is fixed and subject to local deletion.
- No recovery-history screen, report, or export is required.

#### FR-272 - Security Policy Preservation

Recovery shall preserve the last valid authentication configuration, global relock setting, and protected-application policy whenever safe. Authorization sessions remain volatile and are not restored after process loss or reboot.

Acceptance criteria:

- Uncertain policy state requires fresh authentication or denies access.
- No profile, schedule, automation, or per-application policy is restored.
- Any replacement with safe defaults is disclosed to the user.

#### FR-273 - Recovery Verification

Every recovery attempt shall be followed by verification of Usage Access, policy loading, session safety, lock presentation, core service responsiveness, and local-data integrity.

Acceptance criteria:

- Recovery is not declared successful solely because a process restarted.
- Failed verification keeps the appropriate Protection interrupted or Action required state.
- The user can see the current result and next action.

#### FR-274 - Failure Notification

The application shall notify the user when a core failure requires action and App Lock can deliver the notification.

Acceptance criteria:

- Content states the effect without naming protected applications or exposing sensitive details.
- The action opens the relevant recovery guidance.
- The notification is updated after re-verification.

#### FR-275 - Operational Readiness Confirmation

The application shall report normal protection only when PIN authentication, Usage Access, protected-application policy, core monitoring, lock presentation, and local-data integrity are ready.

Acceptance criteria:

- Every required check has a current passing result.
- Optional biometric unavailability does not block PIN readiness.
- A stale, unknown, or failed result cannot be represented as protected.


## 4.15 Observability and Monitoring

#### FR-276 - Structured Logging

The application shall produce a small, consistent set of privacy-safe local diagnostic records for retained core events.

Acceptance criteria:

- Each record contains a time, event type, severity, outcome, and only the minimum non-sensitive context.
- Event names and meanings remain consistent within compatible version 1.x releases.
- No PIN, biometric data, protected content, cryptographic material, or unnecessary application-use detail is recorded.

#### FR-279 - Service Health Monitoring

The application shall monitor the readiness of Usage Access, core monitoring, lock presentation, global policy, and any Android-required ongoing service.

Acceptance criteria:

- A failed required service changes the consolidated protection state.
- A passing state is restored only after a successful recheck.
- Notification interception, Vault, backup, automation, and remote services are not monitored.

#### FR-280 - Health Status Reporting

The application shall report health through the controlled states Protected, Degraded, Protection interrupted, Action required, and Unknown or not verified.

Acceptance criteria:

- Each state has one consistent meaning across the dashboard, notifications, onboarding, and recovery.
- The message explains effect and next action without internal technical detail.
- Conflicting component results resolve to the safer applicable state.

#### FR-283 - Log Rotation

Local diagnostic records shall remain within a fixed storage and age bound.

Acceptance criteria:

- Old eligible records are removed automatically without user scheduling.
- Rotation does not remove current retry enforcement or active failure state.
- The application exposes no configurable archive or retention manager.

#### FR-289 - Security Event Monitoring

The application shall detect repeated authentication failure, loss of a required capability, core service interruption, and local-data integrity failure.

Acceptance criteria:

- Each retained event updates enforcement or health state as applicable.
- Event handling remains local and privacy safe.
- Root detection, intruder capture, remote events, and Vault integrity events are absent.

#### FR-290 - Notification of Critical Events

The application shall issue a privacy-preserving notification when user action is required to restore the core protection promise.

Acceptance criteria:

- Notification severity matches the current protection state.
- The content does not reveal protected-application identity or authentication history.
- Opening the notification leads to the relevant status or recovery screen.

#### FR-293 - Integrity Monitoring

The application shall verify integrity of credential records, global policy, protected-application selections, and local database metadata at defined safety points.

Acceptance criteria:

- Checks occur before relying on stored security state after startup, migration, or recovery.
- A failed check cannot result in access being granted.
- No Vault, backup, cloud, or intruder-media integrity check exists.

#### FR-294 - Diagnostic Self-Test

The application shall provide the authenticated user with an on-device self-test of the retained protection path.

Acceptance criteria:

- Test coverage matches FR-219 and does not create a separate analytics system.
- Results identify pass, degraded, failed, or not verified and provide a safe next action.
- Results remain local and are not shareable or exportable.

#### FR-296 - Exception Monitoring

The application shall retain limited local context for an unexpected core failure and the recovery outcome.

Acceptance criteria:

- Context identifies the affected functional area and operation without secrets, protected content, or internal storage detail.
- Repeated failures can be distinguished within the bounded retention period.
- No remote crash reporting or diagnostic export is required.


## 4.16 Data Lifecycle Management

#### FR-301 - Data Classification

The application shall classify its retained local data according to sensitivity and required protection.

Acceptance criteria:

- The inventory covers credentials, cryptographic material, protected-application selections, settings, diagnostics, cache, temporary data, and migration metadata.
- Each category has a defined storage, retention, and deletion rule.
- Vault, backup, account, location, media, and protected-application notification data are absent.

#### FR-305 - Data Retention Policies

The application shall use fixed documented retention rules for bounded diagnostics, cache, and temporary data.

Acceptance criteria:

- Credentials and active configuration remain only while needed for the installed protection configuration.
- Expired diagnostics and temporary data are removed automatically.
- No user-configurable archive or historical-retention feature is provided.

#### FR-306 - Data Expiration Management

The application shall identify and remove expired diagnostics, cache entries, and temporary data.

Acceptance criteria:

- Expiration does not remove active credential or protection policy data.
- Interrupted cleanup can resume safely.
- Cleanup remains within the fixed version 1.0.0 retention rules.

#### FR-308 - Secure Deletion

The application shall remove local sensitive data when the user performs destructive reset or when a retained data item expires or becomes obsolete.

Acceptance criteria:

- References and platform-protected keys are removed or invalidated as appropriate.
- Deleted data is no longer available through App Lock.
- The requirement creates no Vault, backup, or media-deletion capability.

#### FR-310 - Temporary Data Lifecycle Control

Temporary data created by retained features shall have a defined owner, purpose, and cleanup point.

Acceptance criteria:

- Temporary data is kept only for the current operation or bounded recovery need.
- Normal completion, cancellation, and failure each trigger appropriate cleanup.
- Temporary data never contains a readable PIN or cryptographic key.

#### FR-311 - Metadata Consistency Verification

The application shall verify consistency among protected-application records, settings, key references, and schema metadata.

Acceptance criteria:

- Invalid or missing references are detected before they can weaken protection.
- Removed applications do not leave active protection targets.
- Safe repair is verified before normal protection is reported.

#### FR-312 - Orphaned Data Detection

The application shall identify stale protected-application records and unreferenced local metadata created by retained features.

Acceptance criteria:

- Detection does not delete active credential or policy data.
- Safe obsolete records are removed without affecting other protected applications.
- No Vault-file or backup-package scanning is required.

#### FR-313 - Data Integrity Verification

Critical retained local data shall be validated before use after startup, migration, interrupted write, or recovery.

Acceptance criteria:

- Validation detects structural inconsistency and unauthorized or accidental modification where supported.
- Failure results in a safe state and recovery guidance.
- An integrity check cannot silently replace stricter valid policy with weaker defaults.

#### FR-317 - Cryptographic Key Lifecycle Management

Local cryptographic keys shall have defined generation, active-use, invalidation, and destruction states.

Acceptance criteria:

- A key is available only for its retained local purpose.
- Invalid or missing key state cannot bypass authentication or data protection.
- Destructive reset invalidates or removes keys no longer required.

#### FR-321 - Cache Lifecycle Management

The application shall keep cache within a fixed limit and remove obsolete entries automatically.

Acceptance criteria:

- Clearing cache does not remove credentials, protected selections, or required settings.
- Cache contains no readable PIN or cryptographic key.
- Cache cleanup does not interrupt the core protection response.

#### FR-322 - Data Migration Management

The application shall migrate retained local data during supported in-place version 1.x updates.

Acceptance criteria:

- Migration scope is limited to credentials, protected-application selections, settings, diagnostics metadata, and schema state retained by version 1.0.0.
- Completion is validated before normal use.
- Device-to-device migration, backup import, and deferred-feature formats are excluded.

#### FR-323 - Data Recovery Validation

Local data recovered after an interrupted write or migration shall be validated before returning to active use.

Acceptance criteria:

- Structure, integrity, key availability, and policy consistency are checked.
- Invalid recovery remains fail-secure and may require destructive reset.
- No backup restoration is performed.

#### FR-325 - Data Lifecycle Readiness Verification

The application shall verify that required retention, integrity, temporary-data cleanup, cache control, migration state, and key state are ready before normal protection is reported.

Acceptance criteria:

- Every retained control has a current result.
- Backup and Vault lifecycle do not participate.
- Failed or unknown readiness maps to a controlled non-protected state.


## 4.17 Scalability and Resource Management

#### FR-326 - Scalable Application Management

The application shall manage the installed and protected application lists efficiently within the supported phone capacity.

Acceptance criteria:

- Search and protection-state updates remain responsive for the declared test dataset.
- A change updates only the affected application record.
- No tablet-scale, multi-user, work-profile, or cloned-application dataset is required.

#### FR-332 - Background Processing for Intensive Operations

Database maintenance, integrity checks, and secure cleanup that could block interaction shall execute outside direct user interaction.

Acceptance criteria:

- Core authentication and lock presentation retain priority.
- Cancellation or interruption leaves committed data consistent.
- Encryption of Vault files, backup creation, and backup restoration are absent.

#### FR-333 - Resource Prioritization

The application shall prioritize application detection, lock presentation, authentication, session safety, and required health checks over diagnostics and cleanup.

Acceptance criteria:

- Nonessential work can be deferred during a protection event.
- Deferred work does not create an incorrect health result.
- Priority behavior remains consistent under memory, processor, and storage pressure.

#### FR-334 - Memory Management

The application shall release application-list, authentication, diagnostic, and temporary buffers when no longer needed.

Acceptance criteria:

- Extended core operation reveals no unbounded memory growth.
- Sensitive buffers are not retained for reuse.
- Memory pressure does not convert an invalid session into a valid one.

#### FR-335 - Storage Capacity Monitoring

The application shall monitor storage needed for its local database, bounded diagnostics, cache, temporary data, and safe migration.

Acceptance criteria:

- Low storage is detected before a required write that cannot complete safely.
- The warning gives an actionable recovery step.
- Vault and backup capacity are not calculated or displayed.

#### FR-336 - Storage Optimization

The application shall remove expired diagnostics, obsolete cache, temporary data, and stale metadata without changing valid user configuration.

Acceptance criteria:

- Cleanup never removes an active credential or protected-application selection.
- Interrupted cleanup can resume safely.
- No user-facing optimizer, archive, or forecast is required.

#### FR-337 - Database Optimization

The application shall perform only maintenance needed to preserve responsive and consistent use of its small local database.

Acceptance criteria:

- Maintenance does not block a protection response.
- Integrity is verified after any maintenance that changes stored structure.
- No user-triggered advanced database maintenance screen is provided.

#### FR-338 - Efficient Search Operations

Search shall be optimized only for installed and protected applications.

Acceptance criteria:

- Results meet the NFR search target for the supported dataset.
- Typing remains responsive while results update.
- Vault, audit-history, automation, and report searches are absent.

#### FR-342 - Concurrent Operation Management

The application shall coordinate authentication, protection detection, policy changes, and local database writes to preserve consistency.

Acceptance criteria:

- A settings change cannot partially apply during authentication.
- Concurrent events cannot create duplicate lock presentations or sessions.
- No backup, Vault import, or automation conflict is included.

#### FR-343 - Resource Limit Enforcement

The application shall enforce fixed limits for local diagnostics, cache, and temporary data.

Acceptance criteria:

- Reaching a limit triggers safe cleanup or an actionable warning.
- Limits do not discard active security policy or retry state.
- No configurable backup count, Vault size, or history archive is required.

#### FR-346 - Battery-Aware Operation

The application shall defer nonessential diagnostics and maintenance when necessary to comply with Android power conditions, while retaining or truthfully reporting core protection.

Acceptance criteria:

- Lock presentation and authentication remain prioritized.
- Deferred work resumes when conditions permit.
- Battery state does not act as a user automation trigger.

#### FR-347 - Android Resource Compliance

The application shall operate within the supported Android limits for background execution, notifications, memory, processor use, and power.

Acceptance criteria:

- Core services use only capabilities permitted on API levels 30 through 35.
- Unsupported persistence is not represented as guaranteed.
- Platform restriction that prevents normal protection produces a truthful state and guidance.

#### FR-348 - Thread Management

Concurrent work shall remain responsive and free from deadlock, starvation of protection work, and inconsistent shared state.

Acceptance criteria:

- Direct interaction remains responsive while local maintenance executes.
- Protection and authentication decisions are serialized where consistency requires it.
- Stress testing reveals no duplicate session, duplicate presentation, or partially committed policy caused by concurrency.


## 4.18 Secure Development and Maintenance

FR-351 through FR-375 are not included as normative Version 1.0.0 requirements. Their identifiers remain reserved and are not renumbered or reused.

Version 1.0.0 creates no screen, data, permission, background work, compatibility, migration, or acceptance obligation for functional product obligations; applicable quality and design outcomes are stated once in the NFR and design specifications.

## 5. Acceptance Boundaries

Version 1.0.0 acceptance applies only to the requirements present in Section 4 and the supporting NFR. A capability listed in the disposition appendix creates no screen, data, permission, background work, compatibility, security assessment, migration, or test obligation for this release.

The supported-device claim is limited to conventional phones on API levels 30 through 35 and the declared physical-phone and emulator evidence. Compatibility outside that boundary must not be inferred from successful installation.


## Appendix A - Requirement Disposition

The following existing functional requirements are not included as normative version 1.0.0 obligations. Their identifiers remain reserved and are not renumbered or reused. Every inclusive identifier range in this appendix accounts for each identifier within the stated endpoints; the range notation does not create a new identifier.

### A.1 Authentication

- FR-004 - Pattern Authentication.
- FR-005 - Knock Code Authentication.
- FR-016 - Device Credential Integration.
- FR-021 - Randomized Numeric Keypad as a selectable feature.
- FR-025 - Authentication Audit Trail for user review.

### A.2 Lock Engine

- FR-037 - Newly Installed Application Detection and recommendation.
- FR-043 - Accessibility Event Monitoring.
- FR-045 - Accessibility Permission Verification.

### A.3 Protected Applications Management

- FR-058 and FR-059 - categories and individual policies.
- FR-061 through FR-071 - bulk actions, profiles, per-application policy, favorites, recommendations, and newly installed application workflow.
- FR-074 through FR-077 - recommendation exclusions, hidden applications, work profiles, and cloned applications.
- FR-079 and FR-080 - usage statistics and configuration export.

### A.4 Privacy and Concealment

- FR-081 through FR-085 - intruder capture, location, event notification, and history.
- FR-086 through FR-094 - disguises, camouflage, fake screens, hidden gestures, and protected-application notification masking.
- FR-097 and FR-098 - selectable shoulder-surfing options and invisible pattern behavior.
- FR-101 through FR-104 - privacy dashboard, stealth launch, secret launch, and decoy authentication.

### A.5 Vault

- FR-106 through FR-125 - all Vault capability. No Vault screen, permission, data, key, migration, or verification obligation applies.

### A.6 Scheduling and Automation

- FR-126 through FR-145 - all schedules, rules, triggers, profiles, recommendations, overrides, and automation records.

### A.7 Notifications and User Experience

- FR-147 through FR-154 - lock, unlock, failure, intruder, protected-application notification access, and notification history.
- FR-159 and FR-160 - selectable themes and interaction-customization settings.

### A.8 Security

- FR-165 and FR-166 - application network encryption and certificate pinning.
- FR-167 through FR-169 - root detection, root response, and runtime tamper detection.
- FR-175 and FR-176 - emergency or remote lock mode and backup encryption.

### A.9 Settings

- FR-185 through FR-191 - profiles, profile switching, theme selection, language selection, feedback settings, backup configuration, and import or export.
- FR-194 - advanced administrative settings.

### A.10 Backup and Recovery

- FR-196 through FR-205 - all backup, restore, recovery-password, retention, and cross-device migration capability.

### A.11 Performance

- FR-212 - Large Vault Performance.

### A.12 Administration and Diagnostics

- FR-222 - Secure Diagnostic Export.
- FR-223 - Maintenance Mode.
- FR-225 - Administrator Security Controls.

### A.13 Release Quality

- FR-226 and FR-227 - duplicate internal build and configuration-process obligations.
- FR-236 - Feature Flag Support.
- FR-244 and FR-245 - backup and restore validation.
- FR-247 through FR-250 - duplicate inventory, checklist, readiness, and acceptance mechanics.

### A.14 Operational Resilience

- FR-253 - Accessibility Service Recovery.
- FR-258 - interrupted Vault or backup file encryption recovery.
- FR-263 - Backup Recovery.
- FR-271 - separate Safe Mode feature.

### A.15 Observability

- FR-277 and FR-278 - long-term audit logging and continuous performance metrics.
- FR-281 and FR-282 - diagnostic reports and configurable log levels.
- FR-284 through FR-288 - export, event correlation, continuous database, task, and resource monitoring.
- FR-291 and FR-292 - retained startup metrics and historical metrics.
- FR-295 - separate observability dashboard.
- FR-297 through FR-300 - configurable thresholds, audit-trail integrity, operational reports, and separate observability readiness.

### A.16 Data Lifecycle

- FR-302 through FR-304 - ownership and creation or modification audit history.
- FR-307 - Secure Data Archiving.
- FR-309 - Vault Data Lifecycle Management.
- FR-314 through FR-316 - backup lifecycle, versioning, and expiration.
- FR-318 through FR-320 - general key rotation or retirement features and capacity forecasting.
- FR-324 - Data Lifecycle Reporting.

### A.17 Scalability and Resource Management

- FR-327 through FR-331 - Vault and audit scalability, general incremental loading, pagination, and deferred-subsystem initialization.
- FR-339 through FR-341 - general maintenance scheduling, resource reports, and forecasting.
- FR-344 and FR-345 - continuous degradation detection and large-dataset validation.
- FR-349 and FR-350 - recurring scalability assessment and separate readiness verification.

### A.18 Secure Development and Maintenance

- FR-351 through FR-375 are not repeated as functional product behavior. Applicable modularity, security, build, testing, dependency, documentation, packaging, and maintainability qualities are stated once in the NFR and design specifications. The source identifiers remain reserved.

### A.19 Complete Reserved-Identifier Record

For exact machine-readable disposition, the identifiers not included in version 1.0.0 are:

- FR-004, FR-005, FR-016, FR-021, FR-025, FR-037, FR-043, FR-045, FR-058, FR-059, FR-061, FR-062, FR-063, FR-064, FR-065, FR-066, FR-067, FR-068, FR-069, FR-070, FR-071, FR-074, FR-075, FR-076, FR-077, FR-079, FR-080, FR-081, FR-082, FR-083, FR-084, FR-085, FR-086, FR-087, FR-088, FR-089, FR-090, FR-091, FR-092, FR-093, FR-094, FR-097, FR-098, FR-101, FR-102, FR-103, FR-104, FR-106, FR-107, FR-108, FR-109, FR-110, FR-111, FR-112, FR-113, FR-114, FR-115, FR-116, FR-117, FR-118, FR-119, FR-120, FR-121, FR-122, FR-123, FR-124, FR-125.
- FR-126, FR-127, FR-128, FR-129, FR-130, FR-131, FR-132, FR-133, FR-134, FR-135, FR-136, FR-137, FR-138, FR-139, FR-140, FR-141, FR-142, FR-143, FR-144, FR-145, FR-147, FR-148, FR-149, FR-150, FR-151, FR-152, FR-153, FR-154, FR-159, FR-160, FR-165, FR-166, FR-167, FR-168, FR-169, FR-175, FR-176, FR-185, FR-186, FR-187, FR-188, FR-189, FR-190, FR-191, FR-194.
- FR-196, FR-197, FR-198, FR-199, FR-200, FR-201, FR-202, FR-203, FR-204, FR-205, FR-212, FR-222, FR-223, FR-225, FR-226, FR-227, FR-236, FR-244, FR-245, FR-247, FR-248, FR-249, FR-250, FR-253, FR-258, FR-263, FR-271, FR-277, FR-278, FR-281, FR-282, FR-284, FR-285, FR-286, FR-287, FR-288, FR-291, FR-292, FR-295, FR-297, FR-298, FR-299, FR-300.
- FR-302, FR-303, FR-304, FR-307, FR-309, FR-314, FR-315, FR-316, FR-318, FR-319, FR-320, FR-324, FR-327, FR-328, FR-329, FR-330, FR-331, FR-339, FR-340, FR-341, FR-344, FR-345, FR-349, FR-350, FR-351, FR-352, FR-353, FR-354, FR-355, FR-356, FR-357, FR-358, FR-359, FR-360, FR-361, FR-362, FR-363, FR-364, FR-365, FR-366, FR-367, FR-368, FR-369, FR-370, FR-371, FR-372, FR-373, FR-374, FR-375.
