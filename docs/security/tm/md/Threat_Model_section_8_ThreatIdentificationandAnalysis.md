**8. Threat Identification and Analysis**

**8.1 Purpose**

This section identifies and analyzes the concrete security threats applicable to the approved App Lock architecture.

It converts the:

- Assets defined in Section 4.

- Attack surfaces defined in Section 5.

- Trust boundaries and invariants defined in Section 6.

- Threat methodology defined in Section 7.

into concrete, traceable threat scenarios.

Each threat represents a plausible way an attacker could violate a security property, cross a protected boundary, weaken enforcement, or compromise security-relevant state.

This section describes the **current threat landscape**.

It does not assume that planned controls already exist.

**8.2 Threat Identification Rules**

Threats are identified using the following rules.

**TI-001 — Threats Must Be Concrete**

A threat must describe an attacker action and a resulting security consequence.

The following is insufficient:

Accessibility is unreliable.

The corresponding threat is:

An attacker causes or exploits loss of effective foreground detection so that a protected application becomes usable without App Lock authentication.

**TI-002 — Threats Must Identify the Attacker**

Every threat must identify the attacker class capable of performing the attack.

**TI-003 — Threats Must Identify the Security Boundary**

Every threat must identify the security boundary being attacked or crossed.

**TI-004 — Threats Must Identify the Affected Asset**

Every threat must identify the protected asset or security property placed at risk.

**TI-005 — Current Controls Only**

Only controls that actually exist in the current implementation may be considered current mitigations.

**TI-006 — Historical Defects Remain Threats**

A fixed vulnerability remains represented when it demonstrates a meaningful attack path.

The remediation changes the current risk; it does not erase the threat.

**TI-007 — Planned Controls Remain Planned**

Specified but unimplemented controls do not reduce current risk.

**TI-008 — Platform Limitations Remain Explicit**

An Android platform limitation must not be silently classified as either a guaranteed control or an application defect.

**8.3 Threat Identification Matrix**

The initial threat inventory is organized into the following security domains.

| **Domain** | **Threat IDs** | **Primary Properties** |
|----|----|----|
| Credential | THR-CRED-001–004 | Confidentiality, authentication, integrity |
| Authentication | THR-AUTH-001–004 | Authentication, authorization |
| Protected-App Enforcement | THR-ENF-001–006 | Authorization, availability |
| Session Management | THR-SES-001–004 | Authorization |
| Vault | THR-VAULT-001–005 | Confidentiality, integrity, authorization |
| Cryptographic Storage | THR-CRYPTO-001–005 | Confidentiality, integrity |
| UI / Navigation | THR-UI-001–005 | Authentication, authorization |
| IPC / Components | THR-IPC-001–004 | Authorization, integrity |
| Accessibility | THR-ACC-001–005 | Availability, authorization |
| Lifecycle / Boot | THR-LIFE-001–005 | Availability, authorization |
| Device Admin | THR-DA-001–002 | Availability, integrity |
| Audit / Security State | THR-AUD-001–003 | Integrity, accountability |
| Recovery / Migration | THR-REC-001–005 | Confidentiality, integrity, availability |
| Application Integrity | THR-INT-001–004 | Integrity |
| Platform / Root | THR-PLAT-001–004 | Confidentiality, integrity |
| Supply Chain | THR-SUP-001–003 | Integrity |

The identifiers are stable identifiers.

Threats must not be renumbered merely because their ordering or priority changes.

**8.4 Credential Threats**

**THR-CRED-001 — PIN Confidentiality Compromise**

**Description**

An attacker obtains the user's App Lock PIN or sufficient information to recover it.

**Attacker**

- A-001 Ordinary Malicious Application.

- A-002 Physical Attacker.

- A-003 adb/USB Attacker.

- A-004 Production Debug/Instrumentation Attacker.

- A-006 Root/System Attacker.

**Asset**

- User PIN credential.

**Security Property**

Confidentiality and authentication.

**Attack Surface**

- Credential storage.

- Authentication implementation.

- Runtime memory.

- Debug/instrumentation interfaces.

- Backup/storage extraction.

**Trust Boundary**

Credential storage and runtime authentication boundary.

**Attack Scenario**

An attacker obtains the persisted credential representation or observes/reconstructs the plaintext PIN through storage extraction, instrumentation, memory inspection, or another application weakness.

**Impact**

The attacker can potentially establish App Lock authorization as the legitimate owner.

**Current Controls**

- PIN is not persisted in plaintext.

- Argon2id hashing is used.

- Salt is stored with the credential material.

- Encrypted storage is rooted in Android Keystore.

**Control Status**

Implemented; security verification status is governed by the security-verification process and must not be inferred solely from functional regression evidence.

**Residual Concern**

Runtime compromise or root/system compromise is outside the guaranteed protection boundary.

**THR-CRED-002 — Unauthorized PIN Reset or Change**

**Description**

An attacker changes or resets the App Lock PIN without proving knowledge of the current credential.

**Attacker**

- A-001.

- A-002.

- A-003.

**Asset**

Credential integrity.

**Security Property**

Authentication and integrity.

**Attack Surface**

- PIN-change functionality.

- Settings.

- Application lifecycle.

- Persistent credential state.

**Attack Scenario**

An attacker obtains access to App Lock settings or invokes a credential-management path that permits a new PIN without current-credential verification.

**Impact**

The attacker establishes a credential they control and can subsequently authenticate.

**Current Control**

PIN changes require the current credential.

No forgotten-PIN recovery path exists.

**Security Significance**

This is a primary authentication-boundary threat.

A recovery mechanism must not be introduced later without explicit security analysis because any recovery path creates a new authentication boundary.

**THR-CRED-003 — Credential Reset Through Application Data Manipulation**

**Description**

An attacker manipulates persisted application state to reset or replace credential material.

**Attacker**

- A-003 where platform capabilities permit.

- A-006.

**Asset**

Credential integrity.

**Attack Surface**

- EncryptedSharedPreferences.

- Application data.

- Storage lifecycle.

**Impact**

Potential credential replacement or bypass.

**Current Controls**

- Android application sandbox.

- Encrypted credential storage.

- No application-level forgotten-PIN reset mechanism.

**Boundary**

Application storage boundary.

**THR-CRED-004 — Credential Exposure Through Runtime Handling**

**Description**

The plaintext PIN becomes observable through runtime memory, logging, debugging, instrumentation, screenshots, clipboard behavior, or another runtime path.

**Attacker**

- A-004.

- A-005.

- A-006.

**Security Property**

Credential confidentiality.

**Current State**

Secure-memory handling is only partially implemented.

The current implementation zeroes the PIN CharArray after use, but a complete secure-memory framework is not yet established.

Clipboard security also remains a legacy review item.

**Control Status**

Partial / unverified.

**Residual Risk**

The persisted credential protection does not eliminate runtime exposure risk.

**8.5 Authentication Threats**

**THR-AUTH-001 — PIN Authentication Bypass**

**Description**

An attacker causes the authentication mechanism to accept an invalid PIN as valid.

**Attackers**

- A-001.

- A-002.

- A-003.

- A-004.

- A-005.

**Security Boundary**

Authentication boundary.

**Impact**

Unauthorized App Lock authorization.

**Relevant Assets**

All assets protected by App Lock authorization.

**Required Analysis**

The threat must include:

- Invalid credential handling.

- Verification result handling.

- Failure paths.

- Exception paths.

- State transitions.

- Authentication-result propagation.

**THR-AUTH-002 — Biometric Authentication Result Abuse**

**Description**

An attacker causes a biometric authentication result to be incorrectly interpreted as valid App Lock authorization.

**Attackers**

- A-002.

- A-004.

- A-005.

**Security Boundary**

BiometricPrompt → App Lock authorization boundary.

**Impact**

Unauthorized session establishment.

**Current Control**

Android BiometricPrompt is used for biometric authentication.

**Security Verification Requirement**

Security testing must demonstrate that only a valid biometric authentication result can invoke the successful authorization path.

**THR-AUTH-003 — Authentication State Confusion**

**Description**

An attacker causes the application to treat an incomplete, cancelled, failed, or stale authentication attempt as successful.

**Attack Surface**

- LockScreenActivity.

- Authentication callbacks.

- Activity lifecycle.

- Biometric flow.

- Session manager.

**Impact**

Unauthorized session establishment.

**Historical Relationship**

This threat class is closely related to historical navigation and lifecycle failures and must remain connected to those historical records.

**THR-AUTH-004 — Brute-Force Lockout Bypass**

**Description**

An attacker obtains additional authentication attempts by resetting or bypassing the persisted failure state.

**Attackers**

- A-002.

- A-003.

- A-004.

- A-006.

**Asset**

Credential integrity and authorization boundary.

**Current Control**

Failed-attempt state is persisted in protected storage.

**Required Security Property**

Restarting the application must not reset the attacker's authentication-attempt budget.

**Historical/Verification Requirement**

The security test must explicitly include:

- Application restart.

- Process death.

- Re-entry.

- Reboot where applicable.

- Repeated failed authentication.

**8.6 Protected-App Enforcement Threats**

**THR-ENF-001 — Foreground Detection Failure**

**Description**

App Lock fails to detect that a protected application has become the foreground application.

**Attacker**

- A-002.

- A-003.

- A-005.

**Security Property**

Authorization and availability.

**Attack Surface**

Accessibility framework and AppDetectionService.

**Impact**

A protected application may become usable without App Lock authentication.

**Current State**

This is the principal registered security risk.

The architecture is fail-open with monitoring rather than fail-closed.

**THR-ENF-002 — Deliberate Accessibility Disruption**

**Description**

An attacker deliberately interferes with the Accessibility enforcement mechanism so that protected applications can be opened without detection.

**Attackers**

- A-002.

- A-003.

- A-005.

- A-006.

**Attack Paths**

Potential paths include:

- Revoking accessibility permission.

- Causing service termination.

- Force-stopping App Lock.

- Exploiting platform/OEM restrictions.

- Causing the watchdog to terminate or become ineffective.

**Impact**

Loss of protected-app enforcement.

**Current Control**

Protection monitoring and notification.

**Limitation**

Monitoring does not prevent the underlying failure.

**THR-ENF-003 — Silent Accessibility Failure**

**Description**

The Accessibility service appears enabled but no longer delivers the events required for reliable foreground detection.

**Security Significance**

This is more severe than an obvious permission revocation because the current health check may be unable to distinguish a healthy binding from a non-functional event stream.

**Impact**

Protected applications may open without authentication while the system reports apparently normal configuration.

**Status**

Open threat requiring explicit Core Security analysis.

**THR-ENF-004 — Enforcement Race During Application Switching**

**Description**

An attacker rapidly switches applications or relaunches a protected application during the transition between detection, lock-screen display, and authentication.

**Historical Evidence**

This threat is directly associated with:

- Fast-switch relock defects.

- Fast-relaunch bypass.

- Historical lock-screen lifecycle failures.

**Current Mitigation**

The enforcement engine evaluates protected-app foreground events rather than relying solely on a prior package state.

**Security Significance**

The historical defect remains a required regression/security scenario.

**THR-ENF-005 — Enforcement Bypass Through Service Restart State**

**Description**

An attacker manipulates service/process lifecycle so that a stale authorization or incomplete detection state remains active after enforcement components restart.

**Attack Surface**

- AppDetectionService.

- ProtectionWatchdogService.

- LockSessionManager.

- Process lifecycle.

**Security Property**

Authorization integrity.

**Required Invariant**

Service restart must not create or restore unauthorized authorization state.

**THR-ENF-006 — Enforcement Availability Failure**

**Description**

App Lock loses the ability to continuously enforce protected-app authentication because a required security component is unavailable.

**Relevant Components**

- Accessibility service.

- Watchdog.

- Boot receiver.

- Device Admin protection where enabled.

**Security Significance**

Availability is itself a security property because enforcement failure may result in unauthorized application access.

**8.7 Session Threats**

**THR-SES-001 — Unauthorized Session Creation**

An attacker causes LockSessionManager to record an application as authorized without successful authentication.

**Security Boundary**

Authentication → session authorization.

**Impact**

Protected-app access without valid credentials.

**THR-SES-002 — Session Extension Beyond Policy**

An attacker causes a session to remain valid beyond its configured relock policy.

**Relevant Policies**

- Immediate.

- 10-second grace.

- Screen-off.

**Attack Surface**

- Session timestamps.

- Foreground transitions.

- Lifecycle events.

- Screen-off handling.

**Security Property**

Authorization integrity.

**THR-SES-003 — Cross-Application Session Confusion**

An attacker causes authorization for one protected application to be interpreted as authorization for another.

**Current Architecture**

Sessions are keyed by package.

**Required Invariant**

Unlocking one protected application must not automatically authorize another protected application.

**THR-SES-004 — Session Persistence Across Reboot or Process Death**

An attacker attempts to retain authorization through reboot or process termination.

**Current Architecture**

Sessions are in-memory only.

**Security Property**

Authorization integrity.

**Required Invariant**

No App Lock authorization session survives reboot or process death.

**8.8 Vault Threats**

**THR-VAULT-001 — Unauthorized Vault UI Access**

An attacker reaches the Vault interface without satisfying the App Lock self-gate.

**Attackers**

- A-001.

- A-002.

- A-003.

- A-005.

**Historical Evidence**

This includes the previously identified self-gate bypass.

**Current Control**

Lifecycle-based self-gating and re-authentication.

**THR-VAULT-002 — Vault Payload Extraction**

An attacker obtains encrypted Vault files and attempts to recover plaintext.

**Attackers**

- A-001.

- A-002.

- A-003.

- A-006.

**Assets**

Vault payloads.

**Controls**

- Application sandbox.

- AES-256-GCM encrypted file storage.

- Keystore-backed key protection.

- UUID filesystem names.

**Important Boundary**

The Vault encryption key is not derived directly from the App Lock PIN.

**THR-VAULT-003 — Vault Metadata Disclosure**

An attacker obtains Vault index information revealing:

- Original display names.

- MIME types.

- File sizes.

- Timestamps.

- Other metadata.

**Asset**

Encrypted database and Vault index.

**Impact**

Privacy disclosure even where payload plaintext remains protected.

**THR-VAULT-004 — Unauthorized Vault Modification**

An attacker modifies Vault content or metadata without authorization.

**Security Property**

Integrity.

**Attack Surface**

- Database.

- Encrypted file store.

- Vault UI.

- Runtime process.

**Impact**

Data corruption, substitution, deletion, or misleading Vault state.

**THR-VAULT-005 — In-Process Vault Decryption Without PIN**

An attacker with execution inside the trusted App Lock process or equivalent privileged access obtains Vault plaintext without knowing the PIN.

**Architectural Cause**

The Vault keys are Keystore-rooted and independent of the user PIN.

**Security Classification**

This is an intentional architectural consequence rather than an accidental PIN-bypass implementation defect.

**Boundary**

The Threat Model must recognize that runtime authorization and cryptographic key possession are separate boundaries.

**Root Attacker**

A root/system attacker is outside the application's confidentiality guarantee.

**8.9 Cryptographic Storage Threats**

**THR-CRYPTO-001 — MasterKey Compromise**

An attacker obtains or compromises the Android Keystore MasterKey.

**Impact**

Potential compromise of multiple protected storage layers.

**Security Significance**

This is a root-of-trust threat.

**Boundary**

Android Keystore.

**Scope**

Guaranteed defense is limited to the trusted Android platform boundary.

**THR-CRYPTO-002 — Database Passphrase Compromise**

An attacker obtains the 32-byte SQLCipher database passphrase.

**Impact**

Potential disclosure of the entire encrypted database.

**Asset**

Database key material.

**Control**

Keystore-wrapped encrypted storage.

**THR-CRYPTO-003 — Vault Key Compromise**

An attacker obtains the cryptographic material required to decrypt Vault files.

**Impact**

Direct disclosure of Vault payloads.

**Boundary**

Keystore/encrypted-file architecture.

**THR-CRYPTO-004 — Cryptographic Key Reuse or Derivation Error**

An implementation change accidentally causes independent security domains to share inappropriate key material or derive keys from an unintended source.

**Security Significance**

The current architecture intentionally separates:

- PIN hash.

- Database passphrase.

- Vault file encryption.

Any future change to this relationship requires Threat Model reassessment.

**THR-CRYPTO-005 — Keystore Invalidation**

The Android Keystore key becomes permanently unavailable or invalid.

**Impact**

The application may lose access to:

- Credential storage.

- Database passphrase.

- Vault encryption dependencies.

This may cause effective loss of the user's encrypted data.

**Current State**

There is no complete Keystore-invalidation recovery path.

**Risk**

High-priority recovery threat requiring explicit treatment.

**Important Distinction**

This is primarily a data-availability/recovery threat rather than an authentication bypass.

**8.10 UI and Navigation Threats**

**THR-UI-001 — Tapjacking / Obscured Authentication Input**

An attacker places a malicious overlay over the App Lock authentication interface to manipulate or obscure user interaction.

**Attackers**

- A-002.

- A-005.

**Impact**

Potential authentication manipulation or credential disclosure.

**Current State**

No complete anti-tapjacking or obscured-touch defense is currently implemented.

**Status**

Open security threat.

**THR-UI-002 — UI Spoofing**

An attacker presents a deceptive interface intended to cause the user to believe they are interacting with App Lock.

**Impact**

Potential credential disclosure or incorrect user action.

**Current State**

No dedicated anti-spoofing mechanism is currently established.

**THR-UI-003 — Touch/Event Injection**

An attacker injects input or accessibility actions into the authentication flow.

**Attackers**

- A-005.

- A-006.

**Impact**

Potential authentication or navigation manipulation.

**Boundary**

Android input/accessibility trust boundary.

**THR-UI-004 — Navigation Around Lock Screen**

An attacker attempts to use:

- Back.

- Recents.

- Activity switching.

- Relaunch.

- Background/foreground transitions.

to reach protected content without authentication.

**Historical Evidence**

This threat incorporates the historical self-gate, fast-switch, and fast-relaunch failures.

**Current Controls**

- Activity lifecycle handling.

- Navigation handling.

- Self-gating.

- Re-evaluation on protected-app foreground events.

**THR-UI-005 — Screen Capture or Recording**

An attacker attempts to capture App Lock authentication or sensitive UI content.

**Current Control**

FLAG_SECURE in release builds.

**Limitation**

Debug builds intentionally remain capturable for end-to-end testing.

**Security Model**

Production confidentiality depends on release configuration being correctly enforced.

**8.11 IPC and Component Threats**

**THR-IPC-001 — Unauthorized Activity Launch**

An attacker attempts to launch an App Lock Activity in a manner that bypasses its intended authorization flow.

**Relevant Component**

LockScreenActivity.

**Current Boundary**

exported=false.

**Security Significance**

External applications cannot directly launch the sensitive Activity through ordinary exported-component IPC.

**THR-IPC-002 — Unauthorized Service Invocation**

An attacker attempts to invoke the watchdog or Accessibility service through application-level IPC.

**Relevant Components**

- ProtectionWatchdogService.

- AppDetectionService.

**Current Controls**

- exported=false.

- Framework-specific permission requirements for Accessibility.

**THR-IPC-003 — BootReceiver Abuse**

An attacker sends a forged boot-like broadcast to BootReceiver.

**Current Behavior**

The receiver starts/restarts the watchdog and records the corresponding boot event.

**Security Assessment**

A forged boot broadcast does not directly establish App Lock authorization or expose protected data.

**Residual Concern**

The receiver remains an externally reachable component and must not acquire sensitive behavior through future changes without threat reassessment.

**THR-IPC-004 — Device Admin Receiver Abuse**

An attacker attempts to invoke Device Admin functionality outside the intended Android framework path.

**Relevant Component**

UninstallProtectionReceiver.

**Current Control**

BIND_DEVICE_ADMIN.

**Security Boundary**

Android Device Admin framework.

**8.12 Accessibility Threats**

**THR-ACC-001 — Accessibility Permission Revocation**

An attacker causes the App Lock Accessibility permission to be removed.

**Impact**

Foreground detection stops.

**Security Consequence**

Protected-app enforcement may fail open.

**THR-ACC-002 — Accessibility Service Unbind**

An attacker or platform event causes AppDetectionService to become unbound.

**Impact**

Foreground application events are no longer processed.

**Recovery**

Watchdog monitoring where available.

**THR-ACC-003 — Accessibility Silent Failure**

The service remains nominally enabled but fails to deliver usable events.

**Security Significance**

This is the most important current Accessibility threat because ordinary permission-state monitoring may not detect it.

**THR-ACC-004 — Malicious Peer Accessibility Service**

A malicious Accessibility Service observes or manipulates the App Lock authentication UI.

**Security Property**

Authentication and confidentiality.

**Current Position**

Best-effort defense.

Complete prevention is not guaranteed against an Android-granted peer Accessibility Service.

**THR-ACC-005 — Restricted Settings / Platform Enforcement**

Android platform restrictions prevent the user from enabling or restoring App Lock Accessibility functionality under certain installation conditions.

**Impact**

Security-function availability.

**Classification**

Platform limitation and deployment risk.

**Security Significance**

The application cannot independently override Android's permission policy.

**8.13 Lifecycle and Boot Threats**

**THR-LIFE-001 — Force-Stop Enforcement Loss**

An attacker with sufficient device control force-stops App Lock.

**Impact**

The Accessibility service and watchdog stop.

**Current Security Behavior**

The architecture does not guarantee continued enforcement after force-stop.

**Classification**

Known fail-open availability threat.

**THR-LIFE-002 — Process Death With Authorization Confusion**

The process dies and restarts with inconsistent security state.

**Required Property**

Authorization sessions must not be restored automatically.

**Current Architecture**

Sessions are volatile.

**THR-LIFE-003 — OEM Background Restriction**

An OEM or system policy terminates or prevents restart of a security-critical background component.

**Impact**

Potential loss of watchdog or enforcement monitoring.

**Classification**

Platform-dependent availability threat.

**THR-LIFE-004 — Boot Re-Arm Failure**

After reboot, App Lock fails to restore the enforcement path.

**Impact**

Protected applications may remain unprotected.

**Required Property**

Reboot must clear authorization but restore enforcement.

**THR-LIFE-005 — Startup Security Race**

The system reaches a usable state before App Lock has re-established required protection.

**Security Significance**

Startup ordering must not create an unrecognized window in which protected applications can be accessed without the intended security state.

**8.14 Device Admin Threats**

**THR-DA-001 — Uninstall Protection Disabled**

An attacker disables the optional Device Admin protection and then uninstalls App Lock.

**Impact**

Loss of the enforcement application.

**Classification**

Availability and integrity threat.

**Scope**

Applicable only when Device Admin protection is intended to be active.

**THR-DA-002 — Uninstall Path Bypasses Security Expectations**

An attacker uses an Android-supported administrative path to remove App Lock despite the application's expectations.

**Security Significance**

The Threat Model must distinguish Android's guaranteed Device Admin behavior from assumptions about all possible OEM/system-level uninstall paths.

**8.15 Audit and Security-State Threats**

**THR-AUD-001 — Security Log Modification**

An attacker modifies or deletes security events.

**Assets**

- Security audit logs.

- Intruder events.

**Security Property**

Integrity and accountability.

**Current State**

Audit-log tamper evidence remains a planned/review item.

**THR-AUD-002 — Intruder-Event Disclosure**

An attacker obtains intruder-event records or photographs.

**Security Property**

Confidentiality.

**Assets**

- Intruder photos.

- Event metadata.

**Impact**

Potential disclosure of identifiable individuals and security activity.

**THR-AUD-003 — Security-State Manipulation**

An attacker modifies persisted security state to conceal or alter an attack.

Examples include manipulation of:

- Lockout state.

- Protected-app configuration.

- Security settings.

- Event history.

**8.16 Recovery and Migration Threats**

**THR-REC-001 — Database Corruption Causes Security Degradation**

Database corruption causes App Lock to enter a state where security policy is lost or weakened.

**Current Architecture**

The migration/recovery path historically includes destructive fallback behavior.

**Required Direction**

Foundation hardening is intended to replace destructive fallback behavior with a security-preserving fail-safe path.

**Security Significance**

Database recovery must not silently produce a weaker security configuration.

**THR-REC-002 — Database Migration Exposure**

A migration path temporarily exposes or mishandles sensitive information during conversion.

**Relevant Data**

- Credential-related state.

- Vault metadata.

- Security events.

**Historical Evidence**

The application previously used plaintext database storage before migration to encrypted storage.

The migration path remains security-sensitive.

**THR-REC-003 — Keystore Loss Causes Irrecoverable Data Loss**

The application loses its Keystore dependency and cannot decrypt protected persistent data.

**Impact**

Potential permanent loss of Vault and database contents.

**Current State**

No complete recovery mechanism exists.

**THR-REC-004 — Backup/Restore Creates Authorization Bypass**

A future backup or restore mechanism unintentionally restores:

- Credential state.

- Authorization state.

- Keys.

- Protected-app policy.

in a manner that bypasses authentication.

**Current State**

Application backup is disabled and no application-level backup system exists.

**Future Requirement**

Any backup/restore implementation requires a new Threat Model assessment.

**THR-REC-005 — Forgotten-PIN Recovery Becomes a Bypass**

A future forgotten-PIN recovery feature provides a mechanism that allows an attacker to establish authorization without proving the existing credential.

**Current State**

No forgotten-PIN recovery exists.

**Security Position**

The absence of recovery intentionally eliminates a recovery-based authentication bypass at the cost of legitimate-user data loss.

Any future recovery mechanism requires explicit architectural and security approval.

**8.17 Application Integrity Threats**

**THR-INT-001 — Debuggable Production Build**

An attacker gains debugging capabilities against a production application.

**Impact**

Potential runtime inspection or manipulation.

**Current State**

Debug protection is specified but not fully implemented.

**THR-INT-002 — Runtime Instrumentation**

An attacker instruments the application to alter security decisions or observe sensitive runtime data.

**Attacker**

A-004.

**Impact**

Potential authentication bypass, key use observation, or Vault disclosure.

**Boundary**

Application integrity.

**THR-INT-003 — Application Tampering**

An attacker modifies application behavior so that security checks are removed or bypassed.

**Current State**

Tamper/integrity detection is planned.

**Important Classification**

The planned control must not reduce current risk.

**THR-INT-004 — Release Configuration Weakening**

A production artifact is built with a configuration that disables or weakens a security control.

Examples include:

- Missing FLAG_SECURE.

- Debug configuration accidentally shipped.

- Incorrect component export state.

- Incorrect signing/build configuration.

**Security Significance**

Release configuration is part of the security boundary.

**8.18 Platform and Root Threats**

**THR-PLAT-001 — Root/System Compromise**

An attacker obtains root or equivalent system privileges.

**Impact**

Potential compromise of:

- Application memory.

- Private storage.

- Keystore interactions.

- Runtime integrity.

**Security Position**

Out of scope as a guaranteed defense.

**Current Mitigation**

Root detection and response are planned defense-in-depth controls.

**THR-PLAT-002 — Android Keystore Trust Failure**

The platform fails to provide the assumed Keystore isolation.

**Impact**

Potential compromise of the cryptographic root of trust.

**Classification**

Platform trust-boundary failure.

**THR-PLAT-003 — Android Framework Security Behavior Changes**

An Android version changes behavior affecting:

- Accessibility.

- Background execution.

- Device Admin.

- Keystore.

- Boot events.

- Permission enforcement.

**Impact**

Existing assumptions may become invalid.

**Required Action**

Relevant Android major-version changes trigger Threat Model reassessment.

**THR-PLAT-004 — OEM Security/Power Management Interference**

OEM behavior prevents required App Lock components from remaining operational.

**Impact**

Potential enforcement loss.

**Classification**

Availability threat.

**8.19 Supply-Chain Threats**

**THR-SUP-001 — Malicious Dependency**

A dependency introduces malicious or compromised behavior.

**Impact**

Potential:

- Credential interception.

- Runtime manipulation.

- Data exfiltration.

- Security-control bypass.

**Current Mitigations**

Dependency governance and scanning are part of the Foundation engineering controls.

**Security Status**

Must be verified through the project's dependency and build controls.

**THR-SUP-002 — Dependency Update Introduces Security Regression**

A legitimate dependency update changes behavior relied upon by a security control.

**Examples**

- Encryption behavior.

- AndroidX security behavior.

- Lifecycle behavior.

- Database behavior.

**Required Response**

Dependency changes require impact assessment and regression/security testing where applicable.

**THR-SUP-003 — Build/Release Integrity Failure**

The build produces an artifact that does not correspond to the reviewed source or approved configuration.

**Historical Evidence**

A cryptographic dependency's compile-only annotation issue previously caused a minified release-build failure.

**Security Significance**

Release-build validation is therefore a security-relevant engineering control and not merely a build-quality check.

**8.20 Historical Threat Preservation**

The following historical failures are explicitly incorporated into the threat inventory.

| **Historical Failure**              | **Threat Mapping**             |
|-------------------------------------|--------------------------------|
| Self-gate bypass                    | THR-VAULT-001 / THR-UI-004     |
| Fast-relaunch bypass                | THR-ENF-004 / THR-UI-004       |
| Fast-switch relock defect           | THR-ENF-004                    |
| Plaintext database                  | THR-CRYPTO-002 / THR-VAULT-003 |
| Cryptographic release-build failure | THR-SUP-003                    |
| Force-stop accessibility limitation | THR-ENF-002 / THR-LIFE-001     |

Historical evidence must remain linked to the threat even after remediation.

**8.21 Threat Relationships**

Threats must not be analyzed exclusively in isolation.

Several attack chains are particularly important.

**8.21.1 Enforcement-Availability Chain**

Force-Stop / Permission Loss

│

▼

Accessibility Unavailable

│

▼

Foreground Detection Stops

│

▼

Protected App Opens

│

▼

Authorization Boundary Bypassed

This chain demonstrates why an availability failure becomes an authorization threat.

**8.21.2 Credential-Authorization Chain**

Credential Compromise

│

▼

Successful Authentication

│

▼

Session Creation

│

▼

Authorized State

│

▼

Protected Resource Access

A credential threat therefore has consequences across every App Lock authorization boundary.

**8.21.3 Vault-Storage Chain**

Storage Acquisition

│

▼

Database / File Ciphertext

│

▼

Key Material Acquisition

│

▼

Decryption

│

▼

Vault Disclosure

The threat must therefore evaluate both ciphertext protection and key protection.

**8.21.4 UI-Attack Chain**

Malicious Overlay / Accessibility

│

▼

Authentication UI Manipulation

│

├── Credential Exposure

│

└── Authentication Manipulation

This chain remains open where current controls do not fully mitigate overlay or peer-accessibility abuse.

**8.22 Highest-Priority Current Threats**

Based on the current architecture and implementation state, the Threat Model identifies the following as requiring particular attention:

1.  **THR-ENF-003 — Silent Accessibility Failure**

2.  **THR-ENF-001 — Foreground Detection Failure**

3.  **THR-ENF-002 — Deliberate Accessibility Disruption**

4.  **THR-UI-001 — Tapjacking / Obscured Authentication Input**

5.  **THR-CRYPTO-005 — Keystore Invalidation**

6.  **THR-VAULT-005 — In-Process Vault Decryption Without PIN**

7.  **THR-AUTH-004 — Brute-Force Lockout Bypass**

8.  **THR-SES-002 — Session Extension Beyond Policy**

9.  **THR-INT-002 — Runtime Instrumentation**

10. **THR-REC-001 — Database Corruption Causing Security Degradation**

Priority does not itself constitute a final risk rating.

Final risk ratings must follow the likelihood × impact methodology and include documented reasoning.

**8.23 Threats That Are Not Current Mitigations**

The following must remain explicitly classified as gaps or planned controls:

- Root detection.

- Root response.

- Tamper detection.

- Debug protection.

- Anti-tapjacking.

- Complete overlay-obscure defense.

- Complete secure-memory framework.

- Tamper-evident audit logging.

- Key-management/rotation enhancements.

- Keystore-invalidation recovery.

- Secure backup.

- Forgotten-PIN recovery.

No threat record may cite these as current preventive controls.

**8.24 Threat Analysis Completion Criteria**

Threat identification is complete only when:

- Every security-critical asset has threat coverage.

- Every attacker class has applicable threat coverage.

- Every security-significant attack surface has threat coverage.

- Every trust boundary has threat coverage.

- Every security invariant has a corresponding violation scenario.

- Historical failures are preserved.

- Platform limitations are explicitly modeled.

- Current controls are distinguished from planned controls.

- Risk methodology is consistently applied.

- Residual risks are identified.

- Security-verification requirements can be derived from the threats.

Completeness must be demonstrated through traceability rather than by declaring the inventory complete.

**8.25 Threat Identification Change Control**

Adding, removing, merging, or materially redefining a threat requires controlled change.

A threat may be removed only when:

1.  The underlying attack surface no longer exists, or

2.  The relevant trust boundary has been intentionally removed, or

3.  An approved decision establishes that the threat is no longer applicable.

A threat must not be removed simply because:

- It was mitigated.

- It is inconvenient to test.

- It has become low priority.

- The implementation changed without an architectural decision.

- The threat has not recently been observed.

Mitigated threats remain part of the historical security record.

**8.26 Section 8 Boundary**

Section 8 establishes the concrete threat inventory and attack relationships for the approved App Lock architecture.

It identifies threats involving:

- Credentials.

- Authentication.

- Protected-app enforcement.

- Sessions.

- Vault data.

- Cryptographic storage.

- UI and navigation.

- IPC.

- Accessibility.

- Lifecycle and boot.

- Device Admin.

- Audit state.

- Recovery and migration.

- Application integrity.

- Platform trust.

- Supply chain.

The next stage of the Threat Model must evaluate these threats using the approved risk methodology and establish the relationship between each threat, its existing controls, residual risk, and required security verification.

No threat in this section may be silently reclassified as mitigated merely because a corresponding requirement exists or a future control is planned.
