# Software Requirements Specification

## Version 1.0.0

## Section 8 Security Requirements

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
