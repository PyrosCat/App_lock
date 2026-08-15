# Software Requirements Specification

## Version 1.0.0

## Section 1 Authentication

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
