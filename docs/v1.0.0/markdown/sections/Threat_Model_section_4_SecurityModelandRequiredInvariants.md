# Threat Model

## Version 1.0.0

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
