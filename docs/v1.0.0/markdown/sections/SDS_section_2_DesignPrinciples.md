# Software Design Specification

## Version 1.0.0

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
