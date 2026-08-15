# Software Design Specification

## Version 1.0.0

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
