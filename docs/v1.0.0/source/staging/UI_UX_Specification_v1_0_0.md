# UI/UX Specification

> Version 1.0.0

## Document Status

This draft defines the intended user experience for the first complete phone release of App Lock. It is written as a self-contained product specification. It describes observable behavior and presentation without prescribing source-code structure.

The word “shall” identifies a required user-visible outcome. “May” identifies a permitted choice that does not expand the required feature set. Where Android controls a screen or decision, this specification defines the explanation, handoff, return behavior, and truthful result that App Lock shall provide.

## Part I — Foundations

### 1. Introduction and Scope

#### 1.1 Purpose

This specification defines how a person sets up App Lock, protects selected applications, unlocks them, understands protection health, restores required Android access, changes core settings, and safely resets local configuration. It also defines the visual, accessibility, privacy, phone-layout, error, and recovery standards applied to those experiences.

#### 1.2 Intended Readers

- People approving the finished product experience and wording.
- Interaction, visual, content, accessibility, and localization designers.
- People implementing and evaluating the specified screens and behavior.
- Security and privacy reviewers assessing user-visible controls and claims.

#### 1.3 Included Experience

Version 1.0.0 includes:

- initial setup with a numeric PIN;
- optional eligible Android biometrics with PIN fallback;
- required Android Usage Access and the supported lock-presentation capability;
- selection and basic search of applications to protect;
- protected-application lock presentation, cancellation, retry delay, lockout, session reuse, and relock;
- a protection summary, detailed health view, and guided recovery from required-access loss;
- essential, privacy-masked App Lock notifications;
- basic authentication, privacy, notification, access, diagnostic, help, and reset settings;
- polished light and dark presentation that follows the system theme; and
- portrait-first phone layouts with secure, usable landscape behavior.

#### 1.4 Release Boundary

The following are not part of Version 1.0.0 and shall not appear as controls, setup steps, promises, empty states, permissions, or hidden dependencies:

- Vault storage, file import, preview, export, backup, restore, or device migration;
- recovery passwords or preservation of local configuration after a forgotten PIN;
- profiles, schedules, location or network triggers, automation, recommendations, or manual overrides;
- intruder photographs, event media, security-event history, or advanced reports;
- access to, masking of, or history for notifications produced by other applications;
- bulk protection changes, per-application credentials, or per-application relock policies;
- newly installed application recommendations or review prompts;
- concealment, disguises, fake screens, decoy credentials, or secret launch gestures;
- device administration, uninstall resistance, work profiles, cloned applications, or secondary users;
- an App Lock Accessibility service;
- accounts, cloud services, remote commands, diagnostic export, or routine network communication;
- tablets, foldables, Chromebooks, desktop modes, televisions, vehicles, or wearables; and
- Android versions earlier than Android 11 or later than Android 15 unless separately validated.

#### 1.5 Document Boundary

This document specifies journeys, navigation, screens, transient surfaces, wording, states, visual rules, accessibility, privacy presentation, phone responsiveness, and observable acceptance outcomes. It does not define internal modules, methods, variables, database tables, cryptographic algorithms, development sequencing, or status reporting.

#### 1.6 Completion Principle

A visible capability is complete only when its applicable normal, loading, empty, disabled, authentication, Android-access, degraded, error, interruption, cancellation, and restoration states meet this specification. A successful path alone is insufficient.

### 2. Authority, Constraints, and Assumptions

#### 2.1 Relationship to Companion Specifications

<!-- table-widths: 1.7, 4.8 -->
| Specification | Relationship |
| --- | --- |
| Software Requirements | Defines required capability and business behavior. This document defines its user-visible realization. |
| Non-Functional Requirements | Defines measurable quality, security, privacy, accessibility, performance, and compatibility outcomes. This document defines their presentation and interaction consequences. |
| Software Design | Defines responsibilities and dependency direction. This document relies only on constraints visible to a user. |
| Database Design | Defines protected local information and lifecycle behavior. This document defines when that information is shown, changed, or removed. |
| Threat Model | Defines security boundaries, foreseeable misuse, and accepted platform limitations. This document turns those boundaries into safe interaction and truthful language. |

If two statements appear inconsistent, the interpretation that prevents unauthorized access, avoids a false protection claim, and stays inside the Version 1.0.0 boundary shall be used until the documents are corrected.

#### 2.2 Supported Devices and Versions

The supported form factor is a conventional Android phone running Android 11, 12, 13, 14, or 15, corresponding to API levels 30 through 35. Portrait is the primary layout. Landscape shall remain fully usable and shall not expose protected content, hide the authentication exit, or make a required recovery action unreachable.

The design shall target compact phone windows. It does not require navigation rails, persistent drawers, two-pane layouts, tabletop postures, external displays, keyboard-first desktop operation, or large-screen optimization. Split-screen and picture-in-picture need safe behavior, not a separately optimized design.

#### 2.3 Android Constraints

- Foreground detection requires Android Usage Access. The setup shall name it as required, explain its purpose, and verify its actual state after every settings handoff.
- App Lock shall not request or depend on its own Accessibility service in Version 1.0.0.
- Lock presentation may require an Android-managed capability whose label and route vary by device. The experience shall provide a verified device-appropriate handoff and shall not claim success merely because settings were opened.
- Android may restrict background execution, delay foreground information, revoke access, stop the application, clear its data, or remove it. The interface shall report the state it can verify and shall not promise continuous protection when Android prevents operation.
- Biometric enrollment and the biometric prompt are controlled by Android. App Lock shall not draw a look-alike prompt or claim a biometric capability that Android reports as ineligible.
- Android settings, permission wording, notification behavior, and vendor battery controls can vary. Guidance shall prefer the Android-visible setting name and a text path rather than screenshots that may become inaccurate.

#### 2.4 Assumptions

<!-- table-widths: 2.35, 4.15 -->
| Assumption | Required treatment when false |
| --- | --- |
| The phone is within the declared Android range. | Explain that protection has not been verified for the device and do not show a healthy status. |
| A supported lock-presentation capability is available. | Show Protection interrupted or Action required and provide the narrowest verified recovery route. |
| The person can grant required Android access. | Preserve setup progress, identify the consequence, and allow safe exit without claiming protection. |
| At least one eligible application is installed. | Show a distinct empty state and keep setup incomplete. |
| A PIN remains available before biometrics are enabled. | Never offer biometric-only configuration or recovery. |
| Sensitive processing remains local. | Do not introduce consent for transfer, accounts, or network status into Version 1.0.0 screens. |

#### 2.5 Open Product Choices

The final expressive palette, illustration style, and exact motion curves may be refined during visual design. Those choices may not change semantic color roles, contrast, task hierarchy, state wording, security behavior, or the release boundary. The shipped locale set may be chosen separately; English is the reference copy, and the layout shall remain localizable.

### 3. Users and UX Principles

#### 3.1 Target Users

The experience is intended for people who want a second local privacy boundary around selected applications on a personal phone. They may be setting up security software for the first time, may not recognize Android access labels, and may use assistive technology or increased text and display size.

The design shall not assume knowledge of foreground detection, background execution, system privileges, sessions, or biometric eligibility. It shall explain the immediate purpose and consequence in ordinary language.

#### 3.2 Usage Contexts

The product will often be used quickly, one-handed, in public, under distraction, or immediately after an unexpected lock appears. Setup and recovery may move between App Lock and Android settings. Authentication may occur repeatedly, while configuration is less frequent and more deliberate.

#### 3.3 Privacy Expectations

- PIN digits, PIN length rules beyond what is needed for entry, biometric results, and protected content shall not be exposed in notifications, logs, accessibility announcements, or recents previews.
- Protected application names shall be omitted from the lock surface and notifications by default when naming the target would reveal sensitive intent.
- Installed-application lists and protection choices are configuration-sensitive and shall not appear before App Lock authentication after setup.
- App Lock shall clearly distinguish local processing from Android-managed access. It shall not imply that Usage Access reads content inside another application.
- No screen shall suggest that App Lock prevents uninstall, force-stop, data clearing, root compromise, or every Android timing gap.

#### 3.4 Accessibility Needs

Primary journeys shall work with TalkBack, switch access, large text, increased display size, reduced motion, high-contrast needs, and one-handed touch. Meaning shall not depend on color, position, sound, or animation alone. Authentication shall reveal entered digit count without revealing digit values.

#### 3.5 Design Principles

1. Lead with the current truth. Show the actual protection state and consequence before promotional or explanatory content.
2. Keep one obvious next action. A screen may contain alternatives, but the safest recommended action shall be visually dominant.
3. Cover first, decide second. Protected content shall be visually covered before authentication or transition begins.
4. Explain Android handoffs before leaving. State why access is needed, what Android may call it, and what denial means.
5. Preserve progress, not secrets. Restore safe setup or filter context; clear PIN input and stale authorization.
6. Make destructive outcomes concrete. Name what will be removed, what remains, and whether the action can be undone.
7. Use calm, direct language. Warnings shall be clear without blame, fear, or exaggerated security claims.
8. Make security visually polished. Consistent hierarchy, alignment, spacing, typography, and component states are part of trustworthiness, not decoration.

#### 3.6 Experience Goals

<!-- table-widths: 2.1, 4.4 -->
| Goal | Observable measure |
| --- | --- |
| Setup clarity | A first-time user can identify the remaining setup step and whether protection is active without interpreting an icon alone. |
| Unlock clarity | PIN, biometric fallback, retry delay, cancellation, and lockout are distinguishable and never expose the protected target after cancellation. |
| Health clarity | Protected, Degraded, Protection interrupted, Action required, and Unknown or not verified use different wording, consequence, and next action. |
| Recovery clarity | Returning from Android settings rechecks the relevant access and explains the actual result without automatic repeat prompting. |
| Visual quality | Representative screens show deliberate hierarchy, balanced spacing, coherent light/dark treatment, and consistent component states at phone widths. |
| Accessibility | Every primary journey remains complete with screen reader and 200 percent Android font scaling. |

## Part II — Experience Architecture

### 4. Information Architecture and Navigation

#### 4.1 Application Hierarchy

After setup, App Lock has three primary destinations:

- Protection: current status, protected-application count, the highest-priority action, and access to detailed health.
- Apps: installed eligible applications, search, protection state, and individual protection changes.
- Settings: authentication, privacy, notifications, protection access, diagnostics, help, and destructive reset.

Vault, automation, security-event history, backup, restore, and administration are not destinations in Version 1.0.0.

#### 4.2 Top-Level Navigation

Compact phone layouts shall use bottom navigation for Protection, Apps, and Settings. Each destination shall have a text label and icon. The selected destination shall be communicated by label, icon state, accessibility state, and container treatment rather than color alone.

The bottom navigation is hidden during initial setup, protected-application lock presentation, App Lock authentication, lockout, step-up confirmation, full-screen Android handoff explanation, and destructive reset confirmation.

#### 4.3 Back and Up Behavior

- Back within a detail screen returns to the invoking parent and restores the prior safe list position or settings row.
- Back from a modal has the same result as Cancel unless explicit confirmation is required to avoid accidental loss.
- Back from protected-application authentication cancels and returns away from the protected target; it never dismisses the cover onto that target.
- Back from the authenticated root follows normal Android task behavior without ending valid sessions unless the session policy requires it.
- Up is shown only when a parent exists within App Lock. It is not shown on the three top-level destinations.

#### 4.4 External Entry

Notifications may open Protection Health or the relevant access row, but configuration-sensitive content shall remain behind the App Lock gate. Android settings and biometric prompts are the only required external handoffs. There are no public deep links to protected app lists, settings, or diagnostic information.

An unavailable, stale, or already resolved external target shall open the safest authenticated parent and explain that the condition changed. External entry shall never replay a destructive action or create an authentication session.

#### 4.5 Modal Behavior

Dialogs are used for short, bounded decisions. A full screen is used when authentication, long consequences, system handoff preparation, or large text would make a dialog unsafe. Modal focus remains inside the surface; dismissal returns focus to the invoking control.

#### 4.6 Task Restoration

Safe restoration may include the current top-level destination, list scroll position, search term, non-secret setup completion, and a settings row that initiated an Android handoff. PIN digits, biometric prompt state, a success result, lock coverage assumptions, and step-up authorization shall never be restored from presentation state.

After process recreation, App Lock shall re-evaluate configuration, Android access, protection health, target identity, and session validity before restoring a sensitive destination.

### 5. Application and Protection State Model

#### 5.1 Global States

<!-- table-widths: 1.65, 2.65, 2.2 -->
| State | Meaning | Primary presentation |
| --- | --- | --- |
| Not configured | No usable local PIN exists. | Start setup. Do not claim protection. |
| Partially configured | A PIN exists, but required access, application selection, or verification is incomplete. | Resume the earliest incomplete step. |
| Protected | Required access is currently available, at least one app is selected, and protection was freshly verified. | Calm positive status with last-check context and Manage apps. |
| Degraded | Protection can operate, but Android restrictions or a recoverable condition may reduce reliability. | State the limitation and provide one direct recovery action. |
| Protection interrupted | A required capability is unavailable or enforcement has failed. | Prominent interruption, consequence, and Fix protection action. |
| Action required | A user decision or Android setting is required before protection can be established or restored. | Name the required action and affected capability. |
| Unknown or not verified | Current evidence is missing, stale, or contradictory. | Show Checking or Not verified; never substitute the last healthy state. |

“Protected with reduced responsiveness” is not a Version 1.0.0 state because the release uses a single required detection path and has no optional App Lock Accessibility enhancement.

#### 5.2 State Precedence

When more than one condition applies, the visible state shall use this order: Protection interrupted, Action required, Unknown or not verified, Degraded, Protected, Partially configured, Not configured. A lower-severity condition may appear as supporting detail but may not hide the higher-priority consequence.

#### 5.3 State Evidence and Freshness

A protected status requires current evidence for the local credential, selected applications, Usage Access, lock-presentation capability, protection operation, and the most recent verification. Returning from Android settings, resuming after a relevant interruption, rebooting, or detecting an access change shall invalidate stale success evidence and trigger a new check.

The interface may show “Last checked” only with a real timestamp. It shall use “Not verified” when the current result cannot be established within the defined check period.

#### 5.4 State Invariants

- A successful authentication creates only the session allowed for the current context.
- Failed, cancelled, expired, or interrupted authentication creates no session.
- Unlocking one protected application does not authorize another.
- Process death, reboot, PIN change, or complete reset invalidates every session.
- Loss of required access removes the healthy protection claim.
- Empty, missing, corrupted, or unreadable configuration is not treated as a valid unprotected default while the interface still claims protection.

### 6. Content and Terminology

#### 6.1 Voice

Content shall be concise, calm, direct, and specific. It shall name what happened, the consequence, and the next action. It shall avoid blame, alarmist language, unnecessary technical terms, and false certainty.

#### 6.2 Controlled Terms

<!-- table-widths: 1.85, 2.3, 2.35 -->
| Term | Use | Avoid |
| --- | --- | --- |
| Protected app | An app selected to require App Lock authentication. | Secured app, encrypted app, blocked app. |
| PIN | The required numeric App Lock credential. | Password when referring to the PIN. |
| Biometrics | The Android-approved biometric option. | Fingerprint when other eligible modes may apply. |
| Protection access | Required Android capabilities considered together. | Full control, surveillance access. |
| Usage Access | The Android setting used to identify foreground applications. | Usage permission if Android shows a different label. |
| Protection interrupted | A required capability or enforcement path is unavailable. | You are safe, protection is probably working. |
| Action required | A person must complete a specific step. | Error when no system fault occurred. |
| Reset App Lock | Destructive removal of local credential and configuration. | Recover PIN, restore access, reset PIN. |

#### 6.3 Status Copy Pattern

Every persistent status shall contain:

1. a controlled headline;
2. a one-sentence consequence;
3. the affected capability when it can be stated safely;
4. evidence age when the result can become stale; and
5. one primary action.

Examples include “Protection interrupted — Usage Access is unavailable, so App Lock cannot reliably identify protected apps. Open Usage Access.” and “Protection not verified — The current protection state could not be confirmed. Check protection.”

#### 6.4 Authentication Copy

Use “Incorrect PIN. Try again.” without revealing which digit or rule failed. During retry delay, show the authoritative time remaining or the next permitted attempt time. Biometric cancellation, rejection, unavailability, and lockout shall use different messages and shall always keep “Use PIN” available when authentication is permitted.

#### 6.5 Warning Language

Warnings shall state scope and result before the action label. Destructive labels shall name the action: “Remove protection,” “Reset settings,” or “Reset App Lock completely.” Do not use “OK” for a security decision. Do not use “guaranteed,” “unbreakable,” “always protected,” “military-grade,” or claims that App Lock controls Android behavior it cannot control.

## Part III — User Journeys

### 7. Onboarding and Initial Setup

#### 7.1 Required Sequence

1. Welcome and scope.
2. Create and confirm the PIN.
3. Explain local privacy behavior.
4. Explain and obtain required protection access.
5. Select at least one eligible application.
6. Run a protection check.
7. Present the verified outcome and enter the application.

Optional biometrics may be offered after the PIN exists. Declining biometrics shall not make setup incomplete.

#### 7.2 Credential Creation

The create-PIN screen shall show the approved length and composition rule before input. Continue remains disabled until the rule is satisfied. Confirmation uses a separate empty entry. A mismatch clears both entries, explains that they did not match, and restarts creation. PIN digits shall not survive backgrounding, rotation, process recreation, or an error.

The PIN is considered created only after secure local storage succeeds. A save failure shall say that no PIN was saved and shall offer retry or safe cancellation.

#### 7.3 Privacy Explanation

The disclosure shall explain that App Lock uses local credential information, installed-application identity, Usage Access, the "Display over other apps" permission, protection settings, and limited local diagnostics. It shall state that Version 1.0.0 has no account, cloud synchronization, third-party notification access, location collection, camera capture, or Vault. The essential disclosure shall remain available offline.

#### 7.4 Protection Access

Usage Access and the "Display over other apps" (system overlay) permission — used to present the lock — are both required. Notifications are requested only when required by the supported Android version or when essential protection alerts are enabled. Each row shall show Not requested, Checking, Available, Denied, Revoked, Restricted, or Unsupported as applicable.

The screen shall explain one capability at a time before opening Android settings. On return, it shall recheck the actual state, return focus to the originating row, and announce the result once. It shall not reopen settings automatically after denial.

#### 7.5 Application Selection

The list shall show eligible installed applications by localized name and icon, support basic name search, and expose a separate protection selection control. Unsupported or unsafe targets shall be disabled with a concise reason. At least one selected application is required to complete setup.

There is no category system, filter sheet, bulk policy editor, per-app credential, new-app recommendation, or work-profile handling in Version 1.0.0.

#### 7.6 Verification and Completion

The protection check shall evaluate current required access, protection operation, selected applications, and a supported lock-presentation check. It shall clear any stale protected claim while checking. Completion shall use the actual resulting state rather than a universal success message.

Only a fresh Protected result enables “Finish setup.” Other results present “Fix protection” or “Check again.” The completion screen shall not claim that force-stop, uninstall, data clear, root compromise, or every manufacturer restriction is prevented.

#### 7.7 Interrupted Setup

App Lock may preserve completed non-secret steps and a draft app selection. On relaunch, it shall route to the earliest incomplete step. Once a PIN exists, resuming setup requires the App Lock gate. Android handoffs and process recreation shall not restore PIN digits or an assumed access result.

### 8. Authentication and Protected-App Unlock

#### 8.1 Authentication Contexts

Authentication occurs when opening protected App Lock configuration, unlocking a selected application without a valid session, changing the PIN, reducing protection, ending sessions, or confirming complete reset. The prompt shall state the purpose without unnecessarily naming a protected application.

#### 8.2 Protected-App Lock Presentation

The lock shall be opaque and cover the target before protected content is considered available. Back, Home, Recents, rotation, task switching, biometric cancellation, and any authentication error shall leave the target unauthorized. Cancellation shall return away from the protected target.

Rapid duplicate detections for the same target shall update one cover rather than stack multiple prompts. A target change shall invalidate the old request and rebuild the prompt for the newly verified target.

#### 8.3 PIN

PIN entry provides masked position indicators, digits 0–9, Delete, Unlock, and a safe cancellation action. Copy, paste, predictive retention, and autofill shall not apply. The accessibility tree may expose entered digit count but never digit values.

#### 8.4 Biometrics and Fallback

App Lock may offer the Android biometric prompt only when Android reports current eligibility and a PIN fallback exists. The application-owned surface shall explain why authentication is requested and shall keep “Use PIN” visible before and after the prompt.

Biometric cancellation, rejection, temporary unavailability, permanent lockout, missing enrollment, or changed enrollment creates no session. The next state shall be PIN entry or a clear Android enrollment handoff when the person deliberately requests it.

#### 8.5 Retry Delay and Lockout

Incorrect PIN attempts share one authoritative retry state across relevant authentication surfaces. During a delay or lockout, PIN submission and biometric retry are disabled as defined by the security policy. Rotation, clock changes, process recreation, reboot, or switching authentication presentation shall not shorten the authoritative delay.

The interface shall announce remaining time at meaningful intervals, not every second. On expiry, the originating authentication purpose returns with empty secret input.

#### 8.6 Sessions and Relock

A protected-application session belongs to one protected application; it is not a global bypass. A separate authenticated-settings state is limited to the current sensitive settings flow and does not authorize a protected application. The global relock choice may be Immediate, When screen turns off, or After ten seconds. There are no per-app timeouts or profiles.

<!-- table-widths: 2.25, 1.7, 2.55 -->
| Event | Session result | Presentation result |
| --- | --- | --- |
| Correct PIN or eligible biometric | Create only the requested valid session. | Open the verified destination. |
| Incorrect, cancelled, or expired authentication | No session. | Keep cover or return safely. |
| Screen off under screen-off policy | Invalidate applicable sessions. | Next protected entry locks. |
| Grace period expires | Invalidate applicable sessions. | Next protected entry locks. |
| Process death, reboot, PIN change, or reset | Invalidate all sessions. | Require authentication after recovery. |
| Protected app identity changes | Invalidate its session. | Revalidate target before any prompt. |

#### 8.7 Forgotten PIN

Version 1.0.0 cannot retrieve or replace a forgotten PIN while preserving local configuration. The information screen shall explain that the only supported path is Android-managed clearing of App Lock data or reinstalling the application, followed by setup again. It shall identify that the PIN, protected-app selections, settings, and local diagnostics will be removed.

The authenticated settings area may offer complete local reset after current authentication. It shall not imply that this action is a forgotten-PIN recovery method.

### 9. Protected-Application Management

#### 9.1 Application List

The Apps destination shall show eligible installed applications by localized name, icon, and current protection state. The list shall support name search and stable localized-name sorting. It shall show distinct states for loading, no eligible apps, no protected apps, no search results, refresh failure, removed applications, and an unavailable inventory.

During refresh, the last committed list may remain visible with a Checking label. Stale rows shall not be editable until identity is reconciled. Search shall not expose technical identifiers in the primary experience.

#### 9.2 Adding and Removing Protection

Adding protection may use the global relock default and takes effect only after secure persistence succeeds. Removing protection shall name the selected application and state that it will open without App Lock authentication. The change requires App Lock authentication when the current session is not sufficient for a protection-reducing action.

Failed changes shall restore the previous committed state and explain that no change was applied. The interface shall not leave an optimistic switch position after persistence fails.

#### 9.3 Application Detail

The detail view shall identify the application, whether it is installed and eligible, whether protection is enabled, the global relock behavior, and any current protection-health limitation affecting it. The only editable application-specific setting is protection enabled or disabled. There are no app-specific credentials, schedules, timeouts, notification rules, or profiles.

#### 9.4 Installation Changes

When a protected application is removed, App Lock shall remove or mark obsolete its local selection and update the visible count. Reinstallation shall not silently inherit protection unless Android identity continuity is securely established. Newly installed applications shall not trigger a recommendation or notification in Version 1.0.0.

### 10. Permissions and Protection Recovery

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

### 11. Vault, Backup, and Recovery

Vault, file storage, backup, restore, recovery password, and device migration are not included in Version 1.0.0. No screen, permission, storage selector, key explanation, progress state, error state, empty state, settings row, or recovery promise for those capabilities shall appear.

### 12. Settings and Administration

#### 12.1 Included Settings

Settings shall contain only:

- Authentication: change PIN, enable or disable eligible biometrics, choose the global relock behavior, and end active sessions.
- Privacy: protected-target identity on the lock surface where allowed, App Lock screenshot/recents behavior where configurable, and privacy explanations.
- Notifications: essential App Lock notification categories and lock-screen privacy.
- Protection access: current required-access states and recovery actions.
- Diagnostics: current protection check, relevant Android/access results, storage integrity result, and bounded recent failure summaries.
- About and support: version, supported Android range, privacy summary, help, and known platform limitations.
- Reset: reset non-security preferences or reset App Lock completely after authentication and explicit confirmation.

#### 12.2 Protection-Reducing Actions

Changing the PIN, ending sessions, disabling biometrics, changing relock behavior to a less restrictive option, removing protection, resetting settings that affect protection, and complete reset shall require a current authenticated context or step-up confirmation as defined by the security policy.

The confirmation shall name the affected scope and shall be invalidated if that scope changes before completion.

#### 12.3 Excluded Settings

There shall be no profiles, schedules, automation, Vault, backup, restore, recovery-password, intruder, event-history, disguise, device-administration, notification-listener, Accessibility-service, diagnostic-export, account, cloud, per-app policy, theme-picker, sound, or vibration settings.

## Part IV — Screen and Interaction Specifications

### 13. Screen Inventory and Navigation Map

#### 13.1 Controlled Screens

<!-- table-widths: 1.0, 2.45, 1.4, 1.65 -->
| ID | Screen | Sensitivity | Primary parent |
| --- | --- | --- | --- |
| SCR-001 | Welcome and Scope | Public | Launch before setup. |
| SCR-002 | Create PIN | Secret entry | Setup. |
| SCR-003 | Privacy Explanation | Public | Setup. |
| SCR-004 | Protection Access Setup | Configuration | Setup. |
| SCR-005 | Select Protected Apps | Configuration | Setup or Apps. |
| SCR-006 | Protection Verification | Configuration | Setup or Protection. |
| SCR-007 | Setup Outcome | Configuration | Setup. |
| SCR-008 | Setup Status and Resume | Configuration | Authenticated launch. |
| SCR-010 | Application Gate | Secret entry | Any protected App Lock destination. |
| SCR-011 | Protected-App Lock | Secret entry | Protected target. |
| SCR-012 | Biometric and Fallback | Secret entry | Authentication host. |
| SCR-013 | Lockout | Secret/security | Authentication host. |
| SCR-014 | Step-Up Authentication | Secret/security | Protection-reducing action. |
| SCR-015 | Forgotten PIN and Reset Information | Public/security | Gate help. |
| SCR-020 | Protection Dashboard | Configuration | Top level. |
| SCR-021 | Protection Health | Configuration | Protection. |
| SCR-022 | Protected Apps | Configuration | Top level. |
| SCR-023 | App Details | Configuration | Apps. |
| SCR-050 | Settings | Configuration | Top level. |
| SCR-051 | Authentication Settings | Security | Settings. |
| SCR-052 | Privacy Settings | Configuration | Settings. |
| SCR-053 | Notification Settings | Configuration | Settings. |
| SCR-054 | Protection Access | Configuration | Settings or Health. |
| SCR-055 | Diagnostics and Protection Check | Security | Settings or Health. |
| SCR-056 | About and Support | Public/configuration | Settings. |

#### 13.2 Transient and External Surfaces

<!-- table-widths: 1.2, 2.2, 3.1 -->
| Range | Surface | Included purpose |
| --- | --- | --- |
| DLG-001, DLG-006–009 | Dialogs | Remove app protection, complete reset, end sessions, reset preferences, remove all protection. |
| SHT-002 | Bottom sheet | Choose one global relock behavior. |
| OVL-001–003 | Secure overlays | Protected-target cover, session-expiry cover, protection-check cover. |
| MSG-001–005 | In-app messages | Setting changed, change failed, access unavailable, partial diagnostic result, no search results. |
| NTF-001–003 | Android notifications | Protection active where required, protection interrupted, action required. |
| SYS-001, SYS-002, SYS-004, SYS-005, SYS-007, SYS-009 | Android handoffs | Usage Access, lock presentation, notifications, biometric enrollment, battery/application restrictions, biometric prompt. |

#### 13.3 Navigation Map

Before configuration, launch enters SCR-001 or SCR-008. After a PIN exists, protected App Lock destinations pass through SCR-010. Successful setup enters SCR-020. Bottom navigation connects SCR-020, SCR-022, and SCR-050. Protection links to SCR-021 and SCR-055; Apps links to SCR-023 and SCR-005; Settings links to SCR-051–056. Protected target detection enters SCR-011 independently of the management navigation.

### 14. Screen Specification Standard

Every controlled screen shall meet the following contract:

- Purpose: the single user outcome and why the surface exists.
- Entry and exit: valid entry sources, authentication requirement, safe cancellation, and invalid-entry fallback.
- Layout and content: information order, persistent status, primary action, secondary actions, and content that must not appear.
- State behavior: applicable loading, empty, disabled, Android-access, authentication, degraded, error, and completion states.
- Recovery and restoration: what survives interruption, what is revalidated, and where focus returns.
- Privacy and accessibility: capture/recents treatment, sensitive disclosure, semantics, focus order, announcements, touch targets, and scaling.
- Acceptance: the observable result that demonstrates the screen is safe and complete.

### 15. Detailed Screen Specifications

#### 15.1 Onboarding Screens

##### SCR-001 — Welcome and Scope

Purpose: Introduce App Lock’s limited local protection promise and begin setup without implying that protection already exists.

Entry and exit: Enter on first launch when no PIN exists. “Start setup” enters SCR-002. Back or leaving creates no configuration and no protection state.

Layout and content: Show “Set up App Lock,” a short explanation, and a three-step overview covering PIN, Android access, and app selection. Show one primary action, “Start setup,” followed by “Learn what App Lock can protect.” State that required access must remain available and that Android can interrupt operation.

State behavior: Essential content is packaged locally and has no ordinary loading state. If the Android version or phone form cannot be supported, replace the start action with a device-limitation explanation and do not offer setup.

Restoration, privacy, and accessibility: Return here only when no step was committed; otherwise resume the earliest incomplete step. The surface is public. Announce the title once, then read purpose, overview, and actions in visual order.

Acceptance: A person can state what App Lock protects, what setup requires, and that it does not control all Android conditions before continuing.

##### SCR-002 — Create PIN

Purpose: Create and confirm the required local credential before protection can be enabled.

Entry and exit: Enter from SCR-001 or an authenticated incomplete-setup route. Before secure save, Cancel abandons setup. After save, leaving retains the credential and returns through SCR-010.

Layout and content: Show the PIN rules before masked indicators and a large numeric keypad. Provide digits, Delete, Continue, and Cancel. Confirmation uses the same layout with “Confirm your PIN.”

State behavior: Continue is disabled until the current entry meets the displayed rule. A mismatch clears both entries. During storage, disable the keypad and show “Saving PIN.” A storage failure clears input, states that no PIN was saved, and offers retry or cancellation.

Restoration, privacy, and accessibility: Never restore digits after interruption or recreation. Block screenshots and recents previews. Announce only digit count; all keypad targets are at least 48 dp and have unambiguous labels.

Acceptance: Only two matching valid entries followed by successful secure storage create the credential; no visual success precedes that result.

##### SCR-003 — Privacy Explanation

Purpose: Explain local processing and Android access before the person grants special access.

Entry and exit: Enter after PIN creation. Acknowledgment enables “Review protection access.” Back returns to the safe preceding setup context without removing the PIN.

Layout and content: Group the explanation under Local credential, Installed apps, Usage Access, Essential notifications, and Diagnostics. State what is used, why it is needed, what remains local, and what Version 1.0.0 does not collect. Provide an offline summary and a privacy-policy link when available.

State behavior: A network failure may prevent an external policy page from opening but shall not hide the local summary. The next action remains disabled until the material disclosure is acknowledged.

Restoration, privacy, and accessibility: Preserve reading position across safe configuration changes. Once the PIN exists, process recreation or a later resume routes through SCR-010. Use real headings and descriptive links; disclose no protected app names.

Acceptance: The disclosure accurately distinguishes installed-app identity, Usage Access, and content inside another app, and contains no cloud, camera, location, or notification-listener implication.

##### SCR-004 — Protection Access Setup

Purpose: Obtain and verify the two Android capabilities required for protection.

Entry and exit: Enter after privacy acknowledgment or from SCR-008. Each row opens its Android handoff. Continue to SCR-005 only when both required rows are freshly Available.

Layout and content: Show Usage Access and the "Display over other apps" (lock-presentation) capability as an ordered checklist. Each row contains Required, current state, one-sentence purpose, denial consequence, “Why this is needed,” and one explicit action. Notification access appears only when required on the current Android version and is visually separate from the two core capabilities.

State behavior: Rows support Not requested, Checking, Available, Denied, Revoked, Restricted, and Unsupported. Returning from settings always shows Checking before the actual result. Denial keeps the row in place and does not reopen settings automatically.

Restoration, privacy, and accessibility: Save the originating row before the handoff. On return, recheck both core capabilities, return focus to that row, and announce the changed state once. No Accessibility-service row may appear.

Acceptance: Continue cannot be enabled from navigation history or an assumed settings result; both required capabilities must be verified.

##### SCR-005 — Select Protected Apps

Purpose: Choose at least one eligible installed application for protection.

Entry and exit: Enter from setup or from Apps. In setup, “Review selection” continues to SCR-006. In management, save returns to SCR-022. Cancellation retains only previously committed choices.

Layout and content: Show a title, selected count, basic name search, and a localized-name-sorted list. Each row has icon, localized name, current/draft selected state, and a distinct checkbox or switch. The row label and selection control shall not create ambiguous double actions.

State behavior: Show separate states for inventory loading, no eligible applications, no search result, stale inventory, and refresh failure. Unsupported targets are disabled with a reason. Removed or changed apps are reconciled before the draft can be saved.

Restoration, privacy, and accessibility: A non-secret draft, search term, and list position may survive safe recreation, but identity must be revalidated. Protect recents after the PIN exists. Announce selected count changes and expose checked state without color.

Acceptance: Setup cannot continue with zero selected eligible applications, and an uncommitted or failed save cannot change active protection.

##### SCR-006 — Protection Verification

Purpose: Establish a fresh, truthful protection result before setup completion or after recovery.

Entry and exit: Enter from setup, Protection, Health, or Diagnostics. A Protected result allows setup completion; any other result routes to the highest-priority fix or permits safe exit without a success claim.

Layout and content: Show an overall result and rows for Usage Access, lock presentation, protection operation, protected-app selection, and the supported presentation check. Provide “Run protection check,” a contextual Fix action, and “Check again” after an inconclusive result.

State behavior: Starting clears stale success and shows Checking. Timeouts identify the dependency that is not verified. Partial success maps to Degraded, Action required, Protection interrupted, or Unknown or not verified rather than generic success.

Restoration, privacy, and accessibility: An in-progress check is not restored after recreation; start a fresh check. Announce final classification once and move focus to the result, then the first failed row. Mask selected app names in summaries.

Acceptance: A Protected result requires fresh success for every required row and never survives a relevant state change without re-evaluation.

##### SCR-007 — Setup Outcome

Purpose: Present the verified setup result and the correct next action.

Entry and exit: Enter when the initial protection check ends. Protected enters SCR-020 through “Finish setup.” Non-healthy outcomes use Fix or Check again; leaving preserves partial setup.

Layout and content: Use the actual result as the heading. Show protected-app count, required-access summary, last-check time, any limitation, and one primary action. An expandable summary may show completed steps without listing app names by default.

State behavior: No selected apps, missing access, failed verification, and stale evidence each produce their defined non-success state. A health change while visible replaces the prior result immediately.

Restoration, privacy, and accessibility: Re-evaluate evidence before restoring the outcome. Announce the result and recommended action as one group. Do not use celebratory artwork for a non-healthy result.

Acceptance: “Setup complete” and “Protected” appear only after the same current verification succeeds.

##### SCR-008 — Setup Status and Resume

Purpose: Explain incomplete setup and route to the earliest required step.

Entry and exit: Enter after authenticated launch when a PIN exists but setup is incomplete. “Resume setup” opens the earliest incomplete screen; “Reset App Lock” opens the destructive confirmation path.

Layout and content: Show the completed and remaining steps, current consequence, and one Resume action. Do not present the top-level navigation or a partial protected claim.

State behavior: Re-evaluate Android access and selected-app validity before calculating the next step. An unreadable setup state becomes Action required and offers a controlled reset rather than assuming an empty valid configuration.

Restoration, privacy, and accessibility: Restore only verified completion markers. Focus begins on “Setup is not complete,” then consequence and Resume. Protect configuration details from recents.

Acceptance: The route is deterministic and never skips a failed or revoked required step.

#### 15.2 Authentication Screens

##### SCR-010 — Application Gate

Purpose: Prevent unauthenticated access to App Lock configuration.

Entry and exit: Enter before any protected App Lock destination when no valid configuration session exists. Success opens the revalidated destination. “Exit App Lock” closes or backgrounds the task without showing protected configuration.

Layout and content: Draw an opaque surface before destination content. Show “Unlock App Lock,” a neutral purpose, masked indicators, numeric keypad, Unlock, eligible “Use biometrics,” “Forgot PIN,” and Exit. “Forgot PIN” opens SCR-015 and never replaces the PIN.

State behavior: During verification, prevent duplicate submission. Incorrect input clears digits and shares the authoritative retry state. Delay routes to SCR-013. Biometric unavailability leaves PIN ready.

Restoration, privacy, and accessibility: Clear digits after submission, error, backgrounding, rotation, or recreation. Block screenshots and recents. Validate the requested destination again after success.

Acceptance: No configuration content is drawn before authorization, and no cancellation or error creates a session.

##### SCR-011 — Protected-App Lock

Purpose: Cover a selected application and obtain authorization for that specific target.

Entry and exit: Enter from a current protected-target detection with no valid target session. Success returns only to the same revalidated target. Cancel, Back, failure, stale target, or interruption returns away from it.

Layout and content: Use an opaque full-screen cover with a neutral “Unlock protected app” heading. Target icon/name may appear only under the approved privacy setting. Provide PIN entry, eligible biometrics, Delete, and “Cancel and return.”

State behavior: Duplicate detection does not stack covers. A target switch invalidates the prior request. Loss of required capability keeps the target covered while showing Protection interrupted and a safe exit.

Restoration, privacy, and accessibility: Never restore secret input or infer authorization from prior visibility. Exclude screenshots and recents. Avoid decorative transition delay. Announce target identity only when the privacy setting permits.

Acceptance: Back, Home, Recents, rotation, task switching, rapid relaunch, and process recreation never dismiss the cover onto an unauthorized target.

##### SCR-012 — Biometric and Fallback

Purpose: Host the Android biometric prompt while preserving clear PIN fallback.

Entry and exit: Enter only after current eligibility is verified. Success returns a result to the active authentication request. Cancellation, rejection, or error returns to PIN or safe cancellation.

Layout and content: The app-owned host states the authentication reason and shows “Use PIN” before launching Android’s prompt. It shall not imitate the system prompt. After an unsuccessful prompt, show a specific concise outcome.

State behavior: Distinguish not recognized, cancelled, no enrollment, unavailable hardware, temporary lockout, permanent lockout, and changed enrollment. None authorizes access.

Restoration, privacy, and accessibility: Do not restore a dismissed system prompt or infer success from its prior visibility. Recheck eligibility on every return. Keep screenshot and recents protection active.

Acceptance: PIN remains available in every biometric failure state in which authentication may continue.

##### SCR-013 — Lockout

Purpose: Explain and enforce the authoritative retry delay.

Entry and exit: Replace the entry area of the originating authentication context when retry is unavailable. At verified expiry, return to that purpose with empty input. Cancel returns safely.

Layout and content: Show “Too many attempts,” a short explanation, remaining time or next permitted time, and Cancel. Disable PIN entry, biometric retry, and Unlock.

State behavior: Clock anomalies, reboot, process recreation, rotation, and method switching cannot shorten the delay. When state cannot be verified, remain disabled under Checking.

Restoration, privacy, and accessibility: Restore only the authoritative lockout state and purpose. Announce remaining time at meaningful intervals. Keep all sensitive-window protections active.

Acceptance: Presentation state cannot create an early retry opportunity.

##### SCR-014 — Step-Up Authentication

Purpose: Confirm identity immediately before a protection-reducing or destructive action.

Entry and exit: Enter from an action whose exact scope has been captured. Success authorizes that action once. Cancel or stale scope applies nothing and returns to the invoking screen.

Layout and content: State the concrete reason and affected scope before credential controls. Provide PIN, eligible biometric option where allowed, and Cancel. The reason cannot change after entry begins.

State behavior: Verify the credential and current action scope together. An expired or changed target yields “This action is no longer available.” Process death cancels the pending action.

Restoration, privacy, and accessibility: Clear secret input on backgrounding or recreation. Block screenshots and recents. Focus begins with reason and scope.

Acceptance: A successful step-up cannot be replayed for another action or a broader scope.

##### SCR-015 — Forgotten PIN and Reset Information

Purpose: Explain the absence of non-destructive recovery without providing a bypass.

Entry and exit: Enter from “Forgot PIN” on SCR-010. Return restores the empty gate. A link may open Android application information for data clearing; App Lock cannot complete that action while unauthenticated.

Layout and content: State that the PIN cannot be retrieved. Explain that clearing App Lock data or reinstalling removes the PIN, protected-app selections, settings, and local diagnostics, then requires setup again. Distinguish App Lock data from data belonging to protected applications.

State behavior: The explanation remains available offline. If the Android settings destination cannot open, show a device-neutral text path.

Restoration, privacy, and accessibility: The surface contains no local configuration detail. Returning to the gate clears all secret input. Consequences are presented as a short list before the external action.

Acceptance: No control claims to reset, reveal, email, bypass, or preserve the forgotten PIN.

#### 15.3 Protection and Application Screens

##### SCR-020 — Protection Dashboard

Purpose: Provide the authenticated home for protection state and the most important next action.

Entry and exit: Enter after successful setup or through authenticated navigation. The status card links to SCR-021; “Manage apps” opens SCR-022; “Check protection” opens SCR-006 or SCR-055.

Layout and content: Place the global state card first, then its primary action, protected-app count, current relock summary, and last-check freshness. Routine secondary information follows. Do not add promotional cards for excluded features.

State behavior: Support Checking, Protected, Degraded, Action required, Protection interrupted, Unknown or not verified, and no protected apps. A relevant access or health change updates the card without requiring manual refresh.

Restoration, privacy, and accessibility: Re-evaluate stale status on resume. Protect the screen from recents according to the configuration-sensitivity policy. Announce a changed high-severity state once.

Acceptance: The first viewport always answers “Is protection currently verified?” and “What should I do next?”

##### SCR-021 — Protection Health

Purpose: Explain every current factor contributing to the global protection state.

Entry and exit: Enter from Protection, notifications, or Diagnostics after authentication. A row action opens the relevant Android handoff or protection check. Back returns to the invoking parent.

Layout and content: Show the overall state, last-check time, and rows for Usage Access, lock presentation, protection operation, app selection, notifications when operationally relevant, and observed battery/background restrictions. Each row shows state, consequence, and one action.

State behavior: While rechecking, retain prior detail only under Checking. Contradictory or timed-out evidence yields Not verified. Resolving one row does not hide another outstanding issue.

Restoration, privacy, and accessibility: After handoff, recheck and return focus to the origin row. Group row name, status, consequence, and action for screen readers without forcing repeated detail.

Acceptance: Every non-healthy global state has at least one displayed cause or an explicit statement that the cause could not be verified.

##### SCR-022 — Protected Apps

Purpose: Review, search, add, or remove protection for eligible applications.

Entry and exit: Top-level destination. Row navigation opens SCR-023. “Select apps” opens SCR-005. Removing protection invokes DLG-001 and step-up when required.

Layout and content: Show title, protected count, name search, refresh, and a single list. Each row contains icon, localized name, protection state, and a clear navigation or change model. There is no filter sheet or bulk mode.

State behavior: Show loading, Checking, no protected apps, no eligible apps, no search results, inventory unavailable, and change failure separately. Failed changes restore the committed state.

Restoration, privacy, and accessibility: Restore search and scroll position, then reconcile identity before enabling changes. Expose row name, state, and action distinctly. Protect the list from recents.

Acceptance: Search and individual changes remain responsive on the supported application-count boundary, and stale inventory cannot retarget an action.

##### SCR-023 — App Details

Purpose: Show identity and effective protection for one eligible application and allow protection to be enabled or removed.

Entry and exit: Enter from a revalidated SCR-022 row. Save or confirmed removal returns to the same row. Removed or changed identity returns to Apps with an explanation.

Layout and content: Lead with icon, localized name, installed/eligible state, protection state, global relock summary, and any current health limitation. Provide one protection control and “View protection health.”

State behavior: Save is enabled only for a valid changed draft. Persistence failure preserves the prior committed summary and states that no change was applied. Required-access loss blocks unsafe change and offers Fix protection.

Restoration, privacy, and accessibility: Preserve a non-secret draft only across safe recreation and revalidate identity before enabling save. Focus returns to the first affected field after failure.

Acceptance: No app-specific credential, timeout, schedule, profile, or notification control appears.

#### 15.4 Settings Screens

##### SCR-050 — Settings

Purpose: Provide a short, stable index of included configuration and support areas.

Entry and exit: Top-level destination after authentication. Rows open SCR-051–056. Complete reset begins through DLG-006 and SCR-014.

Layout and content: Group Authentication, Privacy and notifications, Protection access and diagnostics, About and support, and Reset. Show concise supporting state only when it changes a decision, such as “Usage Access unavailable.”

State behavior: If settings cannot be loaded securely, show Action required and offer retry or controlled reset; do not populate permissive defaults and allow saving.

Restoration, privacy, and accessibility: Restore scroll and invoking row. Use headings for groups and avoid placing destructive reset adjacent to routine toggles.

Acceptance: Every row belongs to included Version 1.0.0 behavior and has a defined destination.

##### SCR-051 — Authentication Settings

Purpose: Change the PIN, manage eligible biometrics, choose global relock behavior, and end sessions.

Entry and exit: Enter from Settings. Current authentication is required. PIN change and protection-reducing changes use SCR-014 as required.

Layout and content: Show Change PIN, Biometrics with live eligibility, Relock behavior, and End active sessions. A short note states that PIN remains required as fallback and that forgotten PIN has no non-destructive recovery.

State behavior: Changed biometric enrollment is re-evaluated. A PIN-change storage failure retains the old PIN. Relock changes apply only after verified save. Ending sessions confirms the result.

Restoration, privacy, and accessibility: Do not restore PIN entries or a pending change after interruption. Use system biometric terminology and clear enabled/disabled states.

Acceptance: The old PIN remains valid until the new PIN is securely committed; success invalidates all existing sessions.

##### SCR-052 — Privacy Settings

Purpose: Explain and manage the small set of included presentation-privacy choices.

Entry and exit: Enter from Settings. Changes return to the same row after save.

Layout and content: Include protected-target identity on the lock surface where supported, App Lock recents/screenshot treatment if configurable, notification preview privacy, and a link to the privacy explanation. Default choices minimize disclosure.

State behavior: Unavailable platform behavior is explained and disabled. Failed save restores the committed value. The screen contains no camera, location, intruder, Vault, other-app notification, analytics, or cloud controls.

Restoration, privacy, and accessibility: Revalidate committed values after process recreation. Each setting names the visible consequence rather than using a vague privacy score.

Acceptance: No setting suggests control over screenshots, recents, or notification content owned by another application beyond Android’s supported boundary.

##### SCR-053 — Notification Settings

Purpose: Manage essential App Lock notifications without exposing protected intent.

Entry and exit: Enter from Settings. Android channel management uses SYS-004 and returns to the originating row.

Layout and content: Show only ongoing protection where Android requires it, Protection interrupted, and Action required categories. Include lock-screen privacy and a link to Android notification settings.

State behavior: If Android blocks required notification behavior, show its operational consequence in Protection Health. Turning off an optional alert shall not be represented as turning off protection itself.

Restoration, privacy, and accessibility: Recheck Android runtime permission and channel state after handoff. All examples use generic wording and omit app names.

Acceptance: No history, successful-unlock alert, intruder event, new-app alert, or third-party notification control appears.

##### SCR-054 — Protection Access

Purpose: Show current Android access and provide focused recovery outside setup.

Entry and exit: Enter from Settings or Health. Row actions use the controlled system handoffs. Back returns to the invoking parent and its prior row.

Layout and content: Show Usage Access, lock presentation, notifications when relevant, and detected battery/background restrictions. Required and optional/situational roles are explicit.

State behavior: Every return starts Checking before showing the actual result. A revoked required capability updates global health. Unsupported access cannot be toggled optimistically.

Restoration, privacy, and accessibility: Restore the originating row and announce its new state once. Provide textual Android navigation guidance when a direct destination is unavailable.

Acceptance: No App Lock Accessibility, notification-listener, device-administration, camera, location, media, or document-provider access appears.

##### SCR-055 — Diagnostics and Protection Check

Purpose: Explain current core protection failures without creating an advanced reporting product.

Entry and exit: Enter from Settings, Protection, or Health. “Run check” evaluates current health. Row actions route to the relevant recovery screen. There is no export action.

Layout and content: Show overall state, check freshness, required-access results, protection-operation result, storage-integrity result, app-inventory result, and a bounded recent failure summary with privacy-safe wording.

State behavior: Support idle, checking, result, partial result, timeout, and local-data failure. A partial or failed check never becomes Protected. Old results are clearly aged.

Restoration, privacy, and accessibility: Do not restore an in-progress check. Omit PINs, keys, biometric details, and full protected-app lists. Announce final result once.

Acceptance: Diagnostics are sufficient to identify the current recovery route and cannot be exported, shared, or treated as historical analytics.

##### SCR-056 — About and Support

Purpose: Provide version, compatibility, privacy, help, and platform limitation information.

Entry and exit: Enter from Settings; public help may be opened from selected non-sensitive contexts. External policy or support links state that they leave App Lock.

Layout and content: Show product version, Android 11–15 phone support, local-processing summary, help topics, known force-stop/uninstall/vendor limitations, and legal/privacy links. Keep troubleshooting task-based.

State behavior: Local help remains available without a network connection. Failed external links preserve the page and state that the destination is unavailable.

Restoration, privacy, and accessibility: Public help shall not reveal configuration. Links use descriptive labels and visible focus.

Acceptance: Support text makes no universal compatibility, continuous-operation, uninstall-prevention, or root-resistance claim.

### 16. Dialogs, Notifications, System Handoffs, and Transitions

#### 16.1 Dialog Catalog

<!-- table-widths: 0.85, 1.6, 2.75, 1.3 -->
| ID | Headline | Required consequence | Actions |
| --- | --- | --- | --- |
| DLG-001 | Remove protection from {app}? | The named app will open without App Lock authentication. | Remove protection; Cancel. |
| DLG-006 | Reset App Lock completely? | PIN, protected-app selections, settings, sessions, and local diagnostics are permanently removed; protected-app data is not removed. | Reset App Lock completely; Cancel. |
| DLG-007 | End active sessions? | App Lock and every protected app will require authentication again. | End sessions; Cancel. |
| DLG-008 | Reset preferences? | Name the non-security display and notification preferences restored; retain PIN and protected-app choices. | Reset preferences; Cancel. |
| DLG-009 | Remove protection from all apps? | State the reconciled count and that every listed app will open without App Lock authentication. | Remove all protection; Cancel. |

Destructive confirmation requires an explicit button; outside tap shall not confirm. Scope shall be revalidated immediately before execution.

#### 16.2 Notification Catalog

<!-- table-widths: 0.9, 1.55, 2.55, 1.5 -->
| ID | Private title | Private body | Entry |
| --- | --- | --- | --- |
| NTF-001 | App Lock is active | Protection is running on this device. | Open Protection Health through authentication. |
| NTF-002 | Protection interrupted | Open App Lock to restore or verify protection. | Open the current Health recovery route. |
| NTF-003 | Action required | Open App Lock to review a protection setting. | Open the applicable authenticated row. |

Notifications shall omit protected-app names, PIN or biometric detail, technical identifiers, and raw diagnostic reasons. Dismissing a notification does not dismiss the durable in-app condition. A resolved or stale notification opens the safe parent and states that the condition changed.

#### 16.3 Android Handoffs

<!-- table-widths: 0.9, 1.65, 2.25, 1.7 -->
| ID | Destination | Before leaving | On return |
| --- | --- | --- | --- |
| SYS-001 | Usage Access | Explain purpose, required role, privacy boundary, Android-visible label, and denial consequence. | Recheck and return focus to the Usage Access row. |
| SYS-002 | Lock-presentation setting | Explain the device-visible setting and that protection cannot be verified without it. | Verify the capability and supported presentation behavior. |
| SYS-004 | Notification settings | Name the affected App Lock category and operational consequence. | Recheck runtime permission and relevant channels. |
| SYS-005 | Biometric enrollment | Explain Android ownership and continued PIN fallback. | Re-evaluate eligibility; do not infer enrollment. |
| SYS-007 | App or battery restrictions | Name the observed restriction and use the narrowest verified path. | Recheck the originating restriction and global health. |
| SYS-009 | Android biometric prompt | Show authentication purpose and PIN fallback first. | Consume only the current result; cancellation creates no session. |

#### 16.4 Motion and Interruption

Protected content shall be covered before navigation or animation. Routine movement shall be short, purposeful, interruptible, and replaced by an immediate change or brief fade under reduced motion. Animation shall never extend a session, delay a secure cover, or conceal a status change.

Rotation, backgrounding, task switching, and process recreation may preserve non-secret context only. A dialog returns focus to its invoking control; a system handoff returns focus to its originating row. Session expiry covers sensitive content before the gate appears.

## Part V — Design System and Cross-Cutting Requirements

### 17. Visual and Component Design System

#### 17.1 Visual Direction and Appeal

The interface shall look deliberate, calm, modern, and cohesive. It shall use strong information hierarchy, generous but efficient spacing, stable alignment, restrained elevation, consistent shapes, and one obvious primary action per decision region. Security states shall feel trustworthy and legible rather than theatrical. Healthy states are reassuring, warnings are direct, and interrupted protection is visually prominent without appearing punitive.

The shipped experience shall not look like an unconsidered assembly of default controls. Representative onboarding, Protection, Protected Apps, lock, Health, and Settings screens shall receive a visual design review in both system light and dark themes. Approval shall consider composition, rhythm, density, typography, icon consistency, state distinction, and polish at realistic content lengths.

#### 17.2 Semantic Color Tokens

<!-- table-widths: 1.9, 2.3, 2.3 -->
| Role | Required use | Flexibility |
| --- | --- | --- |
| Primary | Main action, selected navigation, and controlled emphasis. | Hue and exact value may follow the approved product expression. |
| Surface and on-surface | Screens, cards, dialogs, and readable content. | May use neutral or subtly tinted families in light and dark themes. |
| Outline and secondary content | Boundaries and lower-emphasis text. | May vary in value when hierarchy and contrast remain clear. |
| Critical | Protection interruption, destructive action, and severe error. | Need not be a fixed red, but shall be unmistakable and conventionally understandable. |
| Caution | Degraded or action-required conditions. | May vary by palette while remaining distinct from Critical and Positive. |
| Positive | Freshly verified protection and completion. | May vary by palette and shall never be the only success cue. |
| Focus | Keyboard and switch-access focus. | May follow brand direction when it is highly visible on every surface. |
| Scrim | Modal separation. | Opacity may vary; it is never a substitute for an opaque security cover. |

Exact hexadecimal colors are not mandatory. The palette may evolve with branding, theme, or visual refinement when semantic roles remain consistent, every meaningful pair meets contrast requirements, light and dark presentations remain coherent, and status is recognizable through text and icon as well as color. Components shall consume semantic roles rather than infer meaning from a raw color.

#### 17.3 Typography

Use Android’s system sans-serif or an approved highly readable family. A compact type scale shall distinguish display, screen title, section title, body, supporting text, and control labels. Normal user-visible text shall not be smaller than 12 sp. Screen titles shall remain visually dominant after font scaling. Fixed-height text containers and truncation of status, consequences, errors, or primary actions are prohibited.

#### 17.4 Spacing, Shape, and Layout

- Use a 4 dp base spacing system, with 8 dp for related items, 16 dp for standard content, 24 dp between sections, and 32 dp for major separation.
- Use at least 16 dp compact horizontal content padding and preserve Android system, cutout, gesture, and keyboard insets.
- Touch targets shall be at least 48 by 48 dp, with additional separation around destructive or adjacent keypad controls.
- Use a small consistent corner family for controls, cards, and dialogs. Decorative pill shapes are limited to compact status or selection indicators.
- Prefer tonal surface distinction to heavy shadow. Opaque security covers shall not depend on elevation or scrim opacity.
- Long prose shall use a readable line length even on a phone in landscape.

#### 17.5 Core Components

<!-- table-widths: 1.75, 4.75 -->
| Component | Required behavior |
| --- | --- |
| Primary button | One per decision region; explicit result label; defined pressed, focused, disabled, loading, and error behavior. |
| Secondary/text action | Used for cancellation or lower-emphasis alternatives without competing with the primary result. |
| Switch | Used only for an immediate binary change; a change requiring explanation, authentication, or another screen uses a row action. |
| List row | Clear label, optional supporting text, current state, and one coherent tap model. Navigation and selection remain distinct. |
| Status card | Icon, controlled headline, consequence, freshness when relevant, and one recovery or management action. |
| Text field/PIN entry | Persistent prompt, helper/error, appropriate keyboard, no secret restoration, and clear disabled state. |
| Dialog | Named scope, concise consequence, focus containment, explicit cancellation, and visually separated destructive action. |
| Snackbar/message | Brief non-critical result with at most one action; never the sole representation of protection interruption. |
| Progress | Determinate only from real work units; otherwise indeterminate with explanatory text and a bounded outcome. |

#### 17.6 Authentication Controls

PIN controls shall use large labeled targets, stable placement, masked indicators, Delete, and safe cancellation. Biometric authentication shall use Android’s prompt. Failure, delay, lockout, fallback, cancellation, session expiry, and protection interruption shall each have a distinct visual and textual state.

#### 17.7 Icons, Imagery, Haptics, and Motion

Use one coherent Android icon family and optical weight. Directional icons mirror in right-to-left layouts. Every status icon has text. Decorative art may support Welcome, empty, or healthy completion states but shall not imitate Android permission or biometric surfaces, trivialize protection loss, or compete with recovery.

Haptics may confirm direct keypad input, successful authentication, and a high-impact confirmation only when Android settings permit. Passive status changes, countdowns, and repeated errors shall not vibrate continuously.

### 18. Accessibility, Privacy, Phone Layout, and Localization

#### 18.1 Screen Reader and Focus

- Every screen exposes a unique title and logical heading order.
- Controls expose name, role, value, state, and action; decorative imagery is excluded from traversal.
- Focus follows reading and task order, stays inside modals, and returns to the invoking control after dismissal or handoff.
- Dynamic errors, protection changes, and completion results are announced once at an appropriate priority.
- PIN entry announces entered count and state but never digit values.

#### 18.2 Visual and Motor Accessibility

Normal text shall meet at least 4.5:1 contrast; large text and meaningful non-text boundaries shall meet at least 3:1. Meaning shall not rely on color. Primary journeys shall remain complete at 200 percent Android font scaling and increased display size without clipping, overlap, or hidden actions. Touch targets shall be at least 48 dp.

#### 18.3 Reduced Motion and Time

Non-essential motion shall be removed or simplified when reduced motion is requested. User input shall not depend on rapid timing. Security retry delay and session expiry are controlled by policy and shall be explained; visual animation shall not lengthen or shorten them.

#### 18.4 Screenshot, Recents, and Notification Privacy

Credential, protected-app lock, step-up, lockout, and sensitive configuration surfaces shall use secure screenshot and recents treatment where Android supports it. Public Welcome and help may be capturable only when no local configuration is visible. Notifications are masked by default and contain no protected app name or sensitive diagnostic detail.

#### 18.5 Phone Layout and Orientation

Portrait is the reference layout. In landscape, content may scroll or reorganize into a compact arrangement, but the title, current state, secret input, fallback, primary action, and safe cancellation shall remain reachable. The design shall not require two-pane views, navigation rails, or large-screen-specific assets.

Split-screen, picture-in-picture, and recents shall fail safely: a protected target shall not be displayed beside an authentication prompt, the cover shall remain focused and opaque, and an unsupported window state shall cancel or move the target away rather than expose it.

#### 18.6 Localization and RTL

All strings, plurals, dates, times, durations, and numbers shall use locale-aware resources. Do not concatenate sentence fragments or embed essential text in images. Layouts shall tolerate at least 30 percent longer representative strings. Navigation and directional icons mirror in right-to-left locales; numeric PIN order and Android-defined identifiers follow platform conventions.

### 19. Error, Degraded-State, and Recovery Design

#### 19.1 Error Categories

<!-- table-widths: 1.7, 2.55, 2.25 -->
| Category | Examples | Safe response |
| --- | --- | --- |
| Input | Invalid PIN format, mismatch, incorrect PIN. | Keep protected, identify correction without revealing secrets. |
| Android access | Denied or revoked Usage Access, missing lock presentation, blocked notifications. | Replace healthy claim, explain consequence, offer verified handoff. |
| Protection operation | Detection or cover cannot be established. | Cover when possible, show Protection interrupted, provide safe exit and recovery. |
| Local data | Save failure, migration failure, corruption, unreadable protected state. | Preserve prior valid data; do not use permissive defaults; offer controlled reset only when necessary. |
| Inventory | Installed-app list unavailable or target identity changed. | Preserve committed choices, disable stale actions, retry and reconcile. |
| Unknown or not verified | Timeout, contradictory, or stale evidence. | Show Not verified and run a fresh check; never infer Protected. |

#### 19.2 Message Structure

Persistent failures shall state: what happened, what is affected, what remains safe or unavailable, and the next action. Technical identifiers and exception text shall not be shown. Retry appears only when the operation is safe and can reasonably succeed; repeated failure moves to the durable Health or Diagnostics explanation.

#### 19.3 Recovery Rules

- A retry shall not duplicate a protection change or destructive action.
- A failed save restores the previously committed visual value.
- A settings handoff is followed by verification, not assumed success.
- A stale target is discarded rather than retargeted by name alone.
- An unreadable credential or configuration never creates authorization.
- A complete reset is the final local recovery only after explicit consequences and authentication where possible.

#### 19.4 User-Facing Error Catalog

<!-- table-widths: 2.1, 2.75, 1.65 -->
| Condition | Required message intent | Primary action |
| --- | --- | --- |
| Usage Access unavailable | App Lock cannot reliably identify protected apps. | Open Usage Access. |
| Lock presentation unavailable | App Lock cannot reliably cover a protected app. | Review Android setting. |
| Protection check timed out | Current protection could not be verified. | Check again. |
| PIN save failed | No new PIN was saved. | Try again. |
| Protection change failed | The previous protection setting remains. | Retry. |
| App identity changed | The selected app can no longer be safely matched. | Return to Apps. |
| Local protected data unreadable | App Lock cannot use the current local configuration safely. | Review reset information. |
| Force-stop detected or reported | Android may have prevented App Lock from running. | Start and check protection. |

## Part VI — Acceptance and Document Consistency

### 20. Traceability, Verification, and Document Maintenance

#### 20.1 UX Acceptance

The experience is acceptable when all included screens and transient surfaces exist with their specified states; every protected exit is safe; every required Android handoff is explained and verified; every global state is truthful; the visual system is coherent and polished; and the primary journeys pass accessibility, privacy, phone-orientation, interruption, and long-content review.

#### 20.2 Security Invariants

- Protected content is covered before authentication presentation.
- Authentication failure, cancellation, interruption, and expiry never create authorization.
- A healthy protection claim requires fresh evidence.
- A protection-reducing action names and revalidates its scope.
- Secret input is not restored, logged, announced, copied, or shown in recents.
- Missing or unreadable security state is never interpreted as permission to proceed.

#### 20.3 Companion-Specification Consistency

The controlled screen and surface identifiers in this document shall remain stable within Version 1.0.0. When observable behavior changes, the corresponding Software Requirements, Non-Functional Requirements, Software Design, Database Design, and Threat Model statements shall be reviewed for the same boundary. Internal development identifiers and status records shall not be inserted into this reader-facing specification.

#### 20.4 Verification Summary

Verification shall cover Android 11–15 phones in portrait and landscape; PIN and all biometric outcomes; retry delay and lockout; rapid switching and relaunch; screen off, process death, reboot, and force-stop recovery; required-access denial and revocation; system handoff return; secure recents and screenshots; TalkBack; 200 percent font scaling; reduced motion; light and dark themes; RTL; long strings; storage failure; and migration failure.

## Appendices

### Appendix A — Screen Inventory

The controlled Version 1.0.0 inventory is the set listed in Section 13. SCR-009, SCR-024–025, SCR-030–045, SCR-057, and SCR-060–062 are not included. Their identifiers shall not be reassigned to different screens.

### Appendix B — User-Journey Flows

<!-- table-widths: 1.9, 3.3, 1.3 -->
| Journey | Required path | Safe non-success exit |
| --- | --- | --- |
| Initial setup | SCR-001 → 002 → 003 → 004 → 005 → 006 → 007 → 020. | SCR-008 preserves partial progress and resumes the earliest incomplete step. |
| Protected app unlock | Protected target → SCR-011 → PIN or SCR-012 → validated target session. | Cancel/failure returns away; lockout uses SCR-013. |
| Open App Lock | External or launcher entry → SCR-010 → revalidated requested destination. | Exit shows no configuration; forgotten PIN opens SCR-015. |
| Add protection | SCR-022 → SCR-005 or SCR-023 → save → reconciled SCR-022. | Failed save restores previous state. |
| Remove protection | SCR-023/022 → DLG-001 → SCR-014 when required → save. | Cancel/stale scope applies no change. |
| Restore access | SCR-021/054 → explanation → SYS handoff → recheck → SCR-006. | Denial remains visible with truthful non-healthy state. |
| Complete reset | SCR-050 → DLG-006 → SCR-014 → secure deletion → SCR-001. | Cancel or failed authentication changes nothing. |

### Appendix C — State and Transition Matrix

<!-- table-widths: 1.85, 2.1, 2.55 -->
| From | Trigger | To |
| --- | --- | --- |
| Not configured | PIN stored | Partially configured. |
| Partially configured | Required access, app selection, and verification succeed | Protected. |
| Protected | Required access revoked or enforcement fails | Protection interrupted or Action required. |
| Protected | Evidence becomes stale | Unknown or not verified. |
| Any configured state | Relevant restriction detected | Degraded when operation remains possible; otherwise Protection interrupted. |
| Any configured state | Complete reset succeeds | Not configured. |
| Any state | Contradictory or unavailable evidence | Unknown or not verified. |

### Appendix D — Permission and Protection-Health Matrix

Usage Access and the "Display over other apps" (system overlay) permission are the two unconditional protection capabilities: Usage Access supplies foreground detection and the overlay permission enables the lock presentation. Both must be granted before a protected state is claimed; loss of either yields Action required or Protection interrupted, never a false protected state. Notifications are conditional by Android version and selected essential alerts. Biometrics are optional. Battery/background settings are situational. Absence of every other permission named in the release boundary is expected and shall not lower protection health.

### Appendix E — Component Inventory

The controlled component set consists of bottom navigation, top app bar, status card, settings row, application row, search field, PIN indicators and keypad, primary/secondary/text buttons, switch, checkbox where required for setup selection, dialog, bottom sheet for global relock choice, snackbar/in-app message, progress indicator, Android notification, secure cover, and system-handoff explanation row. Each component shall implement the states defined in Section 17.

### Appendix F — Traceability Matrix

<!-- table-widths: 2.25, 2.0, 2.25 -->
| Experience area | Primary functional requirements | Primary quality areas |
| --- | --- | --- |
| Setup and PIN | FR-001–003, FR-011–012, FR-156–158 | Security, usability, accessibility, privacy. |
| Protected unlock | FR-007–010, FR-017–018, FR-026–055 | Security, performance, reliability. |
| App management | FR-056–057, FR-060, FR-072–073, FR-078 | Usability, performance, compatibility. |
| Privacy and notifications | FR-095–096, FR-099–100, FR-105, FR-146, FR-155 | Privacy, accessibility, Android compatibility. |
| Settings and reset | FR-181–184, FR-192, FR-194–195 | Security, integrity, usability. |
| Health and diagnostics | FR-216–219, FR-221 | Reliability, observability, privacy. |

Only retained or narrowed Version 1.0.0 requirements are represented. A range in this summary does not reactivate an excluded identifier; the Software Requirements Specification remains authoritative for individual disposition.

### Appendix G — Glossary and Controlled Terminology

<!-- table-widths: 1.8, 4.7 -->
| Term | Definition |
| --- | --- |
| App Lock gate | Authentication shown before protected App Lock configuration. |
| Protected-app lock | Opaque authentication cover shown for a selected target application. |
| Protection check | Current evaluation of required access, operation, selection, and presentation. |
| Session | Temporary authorization for one defined application or App Lock context. |
| Relock | Invalidation of an applicable session after the selected global event. |
| Secure cover | Opaque surface that prevents protected content from being displayed during authorization. |
| Destructive reset | Removal of App Lock’s local credential and configuration followed by initial setup. |

### Appendix H — Assumptions and Unresolved Choices

The release assumes conventional personal phones within Android 11–15 and a supported device path for lock presentation. Exact palette values and optional illustration treatment may be selected within the semantic color, contrast, privacy, and accessibility rules without expanding the controlled journeys or permissions. Version 1.0.0 uses a ten-second grace option and English interface content; additional locales are not required. Any choice that would add a feature, device class, credential type, permission, stored data category, or background capability falls outside this draft.
