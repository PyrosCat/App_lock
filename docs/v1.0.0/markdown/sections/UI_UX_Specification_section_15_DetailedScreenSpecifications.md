# UI/UX Specification

> Version 1.0.0

## 15. Detailed Screen Specifications

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
