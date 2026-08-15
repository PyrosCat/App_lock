**9. Security Controls and Mitigations**

**9.1 Purpose**

This section defines the security controls and mitigations used to reduce the threats identified in Section 8.

The purpose of this section is to establish a controlled relationship between:

**Threat → Security Objective → Control → Implementation → Verification Evidence → Residual Risk**

A control SHALL NOT be considered an effective security mitigation solely because corresponding functionality exists in the implementation or because a functional regression test passes.

A control is considered **security-verified** only when the requirements of Section 12 have been satisfied.

This distinction is mandatory because substantial functionality was inherited from the pre-migration implementation and has been functionally regression-verified without yet undergoing the Core Security security-verification process.

**9.2 Control Classification**

Every security control SHALL have one of the following implementation states:

| **State** | **Meaning** |
|----|----|
| **Implemented and Security-Verified** | Implemented and supported by threat-specific security-test evidence. |
| **Implemented, Regression-Verified** | Implemented and functionally verified, but not yet security-verified against the Threat Model. |
| **Implemented, Unverified** | Present in the implementation but lacking sufficient verification evidence. |
| **Planned** | Required by the approved architecture or requirements but not implemented. |
| **Compensating Control** | An alternate measure that reduces exposure while the intended control is unavailable. |
| **Accepted Limitation** | A known limitation that cannot or will not be fully eliminated within the defined security boundary. |
| **Not Applicable** | The control does not apply to the current architecture or threat boundary. |

The terms **implemented**, **verified**, **security-verified**, **accepted**, and **not applicable** SHALL NOT be used interchangeably.

In particular:

**Implemented does not mean verified.**

And:

**Regression-verified does not mean security-verified.**

**9.3 Security Control Model**

The security-control model is layered rather than dependent on a single mechanism.

┌───────────────────────────────┐

│ Threat / Attack │

└───────────────┬───────────────┘

│

▼

┌───────────────────────────────┐

│ Preventive Controls │

│ │

│ Authentication │

│ Authorization │

│ Encryption │

│ Component Isolation │

│ Session Isolation │

└───────────────┬───────────────┘

│

▼

┌───────────────────────────────┐

│ Detection Controls │

│ │

│ Permission Monitoring │

│ Protection Health Checks │

│ Security Events │

│ Root/Tamper Detection │

└───────────────┬───────────────┘

│

▼

┌───────────────────────────────┐

│ Response Controls │

│ │

│ Relock │

│ Lockout │

│ Re-authentication │

│ Security Notification │

│ Configured Degradation │

└───────────────┬───────────────┘

│

▼

┌───────────────────────────────┐

│ Residual Exposure │

│ │

│ Platform limitations │

│ Accessibility fragility │

│ Root/system compromise │

│ Accepted product limitations │

└───────────────────────────────┘

The architecture SHALL NOT assume that any single control is sufficient when multiple independent controls are reasonably available.

**9.4 Authentication Controls**

**9.4.1 PIN Authentication**

The PIN is the primary App Lock authentication credential.

The authentication design SHALL enforce:

- verification of the supplied credential against the stored credential verifier;

- no plaintext PIN persistence;

- Argon2id password hashing;

- unique salt generation;

- no PIN recovery mechanism that bypasses authentication;

- current-PIN verification before PIN modification;

- persistence of failed-authentication state;

- lockout/backoff enforcement;

- destruction of temporary plaintext credential material where technically feasible.

The PIN SHALL NOT be treated as an encryption key for the vault or database.

The cryptographic architecture intentionally separates:

User Credential

│

▼

Argon2id Verifier

│

└──────► Authentication Decision

Android Keystore

│

├──────► Credential Store Protection

├──────► Database Key Protection

└──────► Encrypted File Protection

This separation prevents compromise of the PIN verifier from directly providing the database or vault encryption keys.

However, it also means that PIN authentication is primarily a **runtime authorization control**, while encryption-at-rest provides the principal offline-storage control.

This distinction SHALL remain explicit throughout the Threat Model.

**9.4.2 Biometric Authentication**

Biometric authentication is permitted as an alternative authentication mechanism where supported by the implementation and platform.

Biometric authentication SHALL:

- rely on Android's platform biometric authentication mechanism;

- require successful completion of the platform authentication prompt;

- result in the same authorization outcome as an accepted App Lock authentication;

- remain subject to the App Lock session and relock policies;

- not create a persistent authorization state that survives the defined session boundary.

Biometric authentication SHALL NOT be interpreted as eliminating the App Lock authentication boundary.

**9.5 Authorization and Session Controls**

**9.5.1 Per-Application Authorization**

Authorization SHALL be maintained independently for each protected package.

Unlocking one protected application SHALL NOT automatically authorize another protected application.

The authorization state is represented by an in-memory session managed by LockSessionManager.

The session:

- is associated with a package name;

- exists only in application memory;

- is not persisted;

- is destroyed by process termination;

- is destroyed by reboot;

- is invalidated according to the configured relock policy;

- is cleared on screen-off where required by the security model.

This design intentionally prevents an attacker from obtaining a persisted authorization token from storage.

**9.5.2 Relock Enforcement**

The following relock policies are supported:

- IMMEDIATE;

- GRACE_10S;

- SCREEN_OFF.

Relock SHALL be evaluated whenever a protected application becomes foregrounded.

The enforcement engine SHALL NOT rely solely on a previous observation of the package.

The current implementation's repeated foreground evaluation is an important security control because it prevents rapid application switching or relaunching from exploiting stale lock state.

Historical fast-switch and fast-relaunch failures SHALL remain regression targets.

**9.5.3 Reboot and Process-Death Protection**

Authentication sessions SHALL NOT survive:

- device reboot;

- application process termination;

- loss of the application process;

- equivalent lifecycle events that destroy in-memory session state.

Persistent authentication sessions are prohibited unless separately approved through the project's architecture-decision process.

**9.6 Protected-App Enforcement Controls**

**9.6.1 Two-Tier Foreground Detection**

The approved architecture separates foreground detection from the lock engine and provides two detection tiers.

**Baseline Tier — UsageStatsManager + Usage Access.** The baseline tier is mandatory and is designed to keep App Lock functional with Accessibility disabled. The baseline control SHALL:

1.  obtain foreground-application information through UsageStatsManager and the Usage Access special permission;
2.  sample usage events through the approved foreground service;
3.  identify the current foreground package with the required detection-latency characteristics;
4.  exclude App Lock's own package where appropriate, and exclude explicitly ignored system packages;
5.  pass the resulting trigger to the Trigger Processor / detection-source selection layer;
6.  cause the common lock engine to evaluate protected-app policy and current authorization state;
7.  use the approved lock-interface presentation mechanism subject to Android background-activity-launch restrictions.

The baseline tier introduces a sampling latency and battery-cost trade-off. These characteristics SHALL be measured, documented, and security-tested rather than assumed to be negligible. The baseline may require the display-over-other-apps / system-alert-window permission or an applicable platform background-activity-launch exemption, depending on the final lock-interface presentation design.

**Enhancement Tier — Accessibility.** The existing Accessibility-based detector is retained as an optional, user-enabled enhancement. The enhancement SHALL:

1.  use the Android Accessibility framework to receive event-driven foreground information;
2.  apply the same package-exclusion rules (App Lock's own package and ignored system packages);
3.  feed the same Trigger Processor / lock-engine path used by the baseline;
4.  provide improved responsiveness where enabled;
5.  remain optional for normal App Lock operation;
6.  never be required as the sole mechanism for protected-app enforcement in the final architecture.

**Detection-Source Selection.** The detection-source selection layer determines which available source supplies triggers. The baseline is the required foundation; Accessibility may be enabled as an enhancement. The lock engine SHALL remain independent of the detection source: detection identifies a candidate foreground transition; authentication and authorization determine whether access is permitted.

**Current Implementation Status.** The delivered build has not yet implemented the two-tier architecture and continues to rely on Accessibility-only detection. Therefore the baseline control described here is **planned**, not an effective current mitigation. The existing Accessibility availability risk remains open until the Phase 1 implementation is completed and verified.

**9.6.2 Enforcement Health Monitoring**

ProtectionWatchdogService provides monitoring of the enforcement architecture. The watchdog SHALL distinguish, where technically possible, between:

- mandatory baseline healthy;
- mandatory baseline missing or degraded;
- optional Accessibility enhancement healthy when enabled;
- optional Accessibility enhancement missing or degraded;
- common enforcement/presentation-path failure.

A loss of the optional Accessibility enhancement must not be reported as total App Lock protection loss when the mandatory baseline is healthy. A loss or silent degradation of the mandatory baseline is a security-critical condition: the watchdog SHALL generate an appropriate security event and user notification where technically possible and SHALL NOT falsely report healthy protection.

Monitoring is a **detection and response control**, not a complete preventive control. The application cannot independently grant Usage Access or Accessibility permission. The exposure created between loss of a required mechanism and its recovery SHALL remain a documented residual risk — for the baseline path as a security-critical exposure, and for the optional enhancement as an enhancement-degradation exposure.

**9.6.3 Startup and Boot Recovery**

BootReceiver SHALL initiate the protection recovery path following device boot. The target recovery path SHALL restore the mandatory baseline enforcement infrastructure without restoring stale authentication sessions. If the optional Accessibility enhancement is enabled, its restoration state SHALL be evaluated separately from the baseline. Reboot may restore enforcement infrastructure, but SHALL NOT restore an authenticated App Lock session.

**9.7 Vault Security Controls**

**9.7.1 Runtime Vault Authorization**

Vault functionality SHALL remain behind the App Lock self-authentication boundary.

The application SHALL re-establish its own authorization boundary when returning from an unauthenticated lifecycle state.

The vault SHALL NOT rely on Android device unlock as authorization.

The following gates are therefore intentionally independent:

Android Device Unlock

│

▼

Android User Session

│

│ independent

▼

App Lock Authentication

│

▼

Vault Authorization

**9.7.2 Database Confidentiality**

The Room database SHALL be protected through SQLCipher.

The database passphrase SHALL:

- be randomly generated;

- be stored separately from the database;

- be protected by Android Keystore-backed storage;

- not be derived from the user's PIN;

- not be persisted in plaintext;

- not be exposed through application logs or user-visible diagnostics.

The database contains security-sensitive metadata and SHALL therefore be treated as a protected asset even when individual vault payloads are encrypted separately.

**9.7.3 Vault Payload Encryption**

Vault payloads SHALL be encrypted using authenticated encryption.

The current encrypted-file mechanism provides AES-256-GCM-based protection with per-file key derivation.

Vault files SHALL:

- remain encrypted while stored;

- use non-semantic UUID filenames;

- avoid storing the original display name in the filesystem;

- remain inaccessible through ordinary application-sandbox access from another application;

- require access to the application's cryptographic trust chain for decryption.

The database index and encrypted file store SHALL be treated as separate cryptographic layers.

**9.7.4 Intruder Photo Protection**

Intruder photos SHALL receive the same confidentiality treatment as vault payloads.

They SHALL:

- remain in encrypted storage;

- not be exposed through public filesystem paths;

- not be placed in shared media storage unless separately authorized and protected;

- be protected from unauthenticated access through the App Lock UI;

- be included in the same security-verification scope as vault payloads.

**9.8 Key-Management Controls**

**9.8.1 Android Keystore Root of Trust**

Android Keystore is the root of trust for application cryptographic storage.

The architecture assumes:

- Keystore-protected master keys are non-exportable;

- hardware-backed protection is used where available;

- the application cannot directly extract the protected master key;

- the Android platform maintains the integrity of the Keystore boundary.

This is a security assumption rather than a control App Lock can independently enforce.

**9.8.2 Separation of Key Material**

The following secrets SHALL remain logically distinct:

| **Secret**                     | **Purpose**             | **PIN-Derived** |
|--------------------------------|-------------------------|----------------:|
| PIN verifier                   | Authentication          |              No |
| Database passphrase            | SQLCipher database      |              No |
| Vault file encryption material | Vault/intruder payloads |              No |
| Keystore master key            | Root protection         |              No |

No control SHALL introduce implicit key reuse between these purposes without an approved architecture decision.

**9.8.3 Key Exposure Prevention**

Application code SHALL NOT:

- log key material;

- expose key material through exported components;

- serialize keys into ordinary preferences;

- place keys in shared storage;

- include secrets in crash reports;

- return raw key material through IPC;

- embed production secrets in application resources.

Any future key-management change SHALL undergo security review and threat-model reassessment.

**9.9 Component and IPC Controls**

The Android component boundary SHALL be minimized.

The current component model is:

| **Component**               | **Exported** | **Protection**              |
|-----------------------------|-------------:|-----------------------------|
| MainActivity                |          Yes | Launcher/self-gate          |
| LockScreenActivity          |           No | Application-private         |
| AppDetectionService         |           No | BIND_ACCESSIBILITY_SERVICE  |
| ProtectionWatchdogService   |           No | Application-private         |
| BootReceiver                |          Yes | System broadcast dependency |
| UninstallProtectionReceiver |          Yes | BIND_DEVICE_ADMIN           |
| ContentProviders            |         None | N/A                         |

Exported components SHALL exist only where required by Android framework behavior.

BootReceiver SHALL treat externally supplied boot-like broadcasts as non-authoritative input. Its behavior SHALL remain limited to the safe recovery action defined by the architecture.

UninstallProtectionReceiver SHALL rely on the device-admin framework permission boundary.

No sensitive functionality SHALL be exposed through an exported component without a separately approved security justification.

**9.10 UI and Authentication-Surface Controls**

The authentication interface SHALL be treated as a security boundary rather than an ordinary application screen. Existing controls include:

- self-gating of protected application functionality;
- lifecycle-based re-gating;
- Back handling;
- Recents exclusion;
- noHistory behavior where applicable;
- completion of unauthenticated lock screens when backgrounded;
- FLAG_SECURE in release builds;
- biometric-dialog lifecycle handling;
- repeated foreground enforcement regardless of detection source.

These controls address historical navigation and screen-capture bypasses. The approved two-tier architecture also requires the lock-interface presentation path to be compatible with the mandatory Usage Access baseline; the final choice between a drawn overlay and an activity launched through the applicable background-activity-launch mechanism remains an implementation decision.

The current implementation does **not** fully address: malicious overlay obscuring; tapjacking; obscured-touch acceptance; malicious accessibility event injection; UI spoofing by a hostile peer accessibility service. These SHALL remain explicit threats and SHALL NOT be marked mitigated merely because FLAG_SECURE is enabled.

**9.11 Brute-Force and Authentication-Abuse Controls**

Authentication attempts SHALL be subject to persistent lockout/backoff controls.

The failed-attempt counter and lockout-until timestamp SHALL survive application restart.

This prevents a simple process restart from resetting the authentication-abuse state.

The security invariant is:

An attacker SHALL NOT obtain additional unrestricted authentication attempts merely by terminating and restarting the application.

Lockout controls SHALL be tested against:

- repeated incorrect PIN entry;

- application restart;

- process death;

- device reboot;

- rapid authentication attempts;

- navigation away from and back to the authentication screen.

**9.12 Permission and Security-State Monitoring**

The application SHALL monitor security-relevant permission state changes for every permission that forms part of the approved enforcement architecture. At minimum, this includes:

- Usage Access for the mandatory baseline;
- the permission required by the final lock-interface presentation mechanism, where applicable;
- Accessibility permission when the optional enhancement is enabled;
- Device Admin where uninstall protection is enabled;
- other security-critical grants identified by the implementation.

When a security-critical permission is removed or protection becomes unavailable, the application SHALL: (1) detect the condition where technically possible; (2) record a security event; (3) notify the user; (4) provide an appropriate recovery path; (5) avoid falsely representing the protection state as healthy. Permission monitoring SHALL distinguish optional enhancement loss from mandatory baseline loss. Detection SHALL NOT be treated as equivalent to prevention.

**9.13 Tamper, Root, and Debugging Controls**

Root detection, tamper detection, and production debug/instrumentation resistance are planned security-hardening controls.

The intended controls include:

- root/environment detection;

- integrity or tamper detection;

- production debug detection;

- configurable response to detected compromise conditions.

Until implemented and security-verified, these controls SHALL NOT be represented as active mitigations.

Their absence does not redefine the application sandbox or Keystore trust boundary.

A rooted or system-compromised device remains outside the application's guaranteed security boundary.

**9.14 Recovery and Failure-Handling Controls**

Security failures SHALL NOT silently convert into authentication bypasses.

The following failure classes require explicit treatment:

| **Failure** | **Required Security Property** |
|----|----|
| Accessibility loss | Detect and notify; do not falsely report healthy protection |
| Watchdog loss | Recover where platform permits |
| Process death | Do not restore sessions |
| Device reboot | Restore enforcement without sessions |
| Keystore invalidation | Fail securely; do not bypass encryption |
| Database corruption | Preserve confidentiality; controlled recovery |
| Forgotten PIN | No authentication bypass |
| App data clearing | Destructive reset only |
| Uninstall | No recovery-based bypass |
| Backup/restore | No unauthorized restoration path |

Where secure recovery cannot be implemented, destructive failure is preferable to an authentication bypass.

**9.15 Controls for Historical Vulnerabilities**

Historical failures SHALL be treated as permanent security regression targets. The following controls are therefore mandatory regression/security-test candidates:

| Historical Failure | Required Control |
|----|----|
| Self-gate resume bypass | Lifecycle self-gating |
| Fast relaunch bypass | Re-evaluate authorization on every protected-app foreground trigger |
| Fast-switch relock defect | Per-trigger enforcement independent of detection source |
| Plaintext database | SQLCipher migration and encrypted persistence |
| Release cryptographic build failure | Minified release-build validation |
| Accessibility loss | Optional-enhancement health monitoring and baseline-continuity verification |
| Detection-source substitution/race | Common Trigger Processor and lock-engine regression coverage |

The existence of a historical fix SHALL NOT by itself establish security verification. The corresponding threat, control, and test evidence SHALL remain traceable.

**9.16 Compensating Controls**

Where a primary security mechanism cannot provide complete prevention, a compensating control SHALL be documented. Examples include:

- accessibility health notification compensating for the inability to self-grant the optional Accessibility enhancement;
- baseline and enhancement health monitoring compensating for the inability to self-grant platform permissions, and watchdog monitoring compensating for detection-service fragility;
- process-lifetime sessions compensating for the inability to guarantee secure session persistence;
- destructive reset compensating for the absence of secure PIN recovery;
- encrypted storage compensating for the inability to guarantee confidentiality against a compromised application process.

Compensating controls SHALL NOT be described as equivalent to the primary control.

**9.17 Control-to-Threat Relationship**

Controls SHALL be evaluated against the threats they are intended to mitigate.

The minimum relationship is:

Threat

│

▼

Security Objective

│

▼

Control

│

▼

Implementation

│

▼

Security Test

│

▼

Evidence

│

▼

Verification Status

│

▼

Residual Risk

A missing link SHALL prevent the control from being promoted to security-verified status.

In particular:

- a requirement without an identified threat may still be functional rather than security-driven;

- an implemented control without a security test is not security-verified;

- a security test without evidence is not sufficient;

- evidence without configuration/build identification is insufficient for reproducibility;

- a verified control does not eliminate residual risk automatically.

**9.18 Current Control Posture**

At the time of Threat Model authoring, the security posture SHALL be represented conservatively.

**Implemented and Functionally Regression-Verified** (existing functional/regression evidence): PIN authentication; biometric authentication; intruder capture; vault core functionality; vault UI self-gating; encrypted persistence; database encryption; encrypted vault payloads; screen-capture prevention; persistent brute-force lockout; permission-change detection; per-application authorization/session behavior; historical self-gate and rapid-relaunch defenses; existing Accessibility-based detection behavior in the current delivered build. These controls SHALL NOT yet be labeled **security-verified** solely on the basis of the existing regression campaigns.

**Implemented but Pending Security Classification or Verification** (as applicable): Keystore usage; detailed authentication-session controls; device-credential handling; key-management architecture; secure-memory handling; clipboard protection; emergency-lock behavior; audit-log protection; privacy controls; backup/security-recovery behavior; existing Accessibility health monitoring as a security control.

**Planned or Not Yet Effective** (approved target-architecture and other controls not yet effective in the delivered build): UsageStatsManager + Usage Access baseline detection; detection-source selection / Trigger Processor integration for the two-tier architecture; baseline-compatible lock-interface presentation path; tier-specific enforcement health monitoring; root detection; root response; tamper detection; production debug/instrumentation resistance; anti-tapjacking and obscured-touch defense; Keystore-invalidation recovery; formal secure backup; key rotation; security-health scoring; penetration testing; Threat-Model-driven security-test suite.

The optional Accessibility enhancement is architecturally approved, but its optional status does not make its current implementation security-verified. Planned controls SHALL NOT be cited elsewhere in the Threat Model as existing mitigations.

**9.19 Security Control Invariants**

The following invariants are mandatory:

1.  **No unauthenticated user may access protected App Lock functionality through the intended application UI.**

2.  **Unlocking one protected application does not authorize another protected application.**

3.  **App Lock authentication remains independent of Android device unlock.**

4.  **Authentication sessions do not survive reboot or process termination.**

5.  **Failed-authentication state cannot be bypassed by ordinary application restart.**

6.  **PIN recovery cannot bypass knowledge of the existing credential.**

7.  **Database and vault encryption keys are not derived from or exposed by the PIN.**

8.  **Sensitive key material is protected by the Android Keystore trust boundary.**

9.  **Vault and intruder payloads remain encrypted at rest.**

10. **The filesystem does not expose vault display names through filenames.**

11. **Sensitive components are not unnecessarily exported.**

12. **Loss of enforcement infrastructure is treated as a security condition, not merely a reliability event.**

13. **Security functionality is never considered verified solely because it is implemented.**

14. **Historical bypasses remain permanent regression targets.**

15. **Known limitations remain explicitly represented rather than silently treated as mitigated.**

16. **A security control may not be promoted to security-verified status without threat-specific evidence.**

**9.20 Control Change Requirements**

Any change to a security control SHALL trigger an impact assessment.

The assessment SHALL determine whether the change affects:

- an identified threat;

- a security property;

- a trust boundary;

- cryptographic material;

- authentication or authorization;

- enforcement continuity;

- a security requirement;

- a previously verified test;

- a residual-risk determination;

- an Architecture Decision Record;

- the Threat Model.

Where applicable, affected verification status SHALL be downgraded until new evidence is produced.

No implementation change SHALL silently preserve a previous security-verification claim.

**9.21 Section 9 Completion Criteria**

Section 9 is complete only when:

- every material threat identified in Section 8 has at least one documented control, accepted limitation, or explicit statement of why no control is feasible;
- each control has a defined implementation state;
- implemented controls are not incorrectly represented as security-verified;
- preventive, detective, and responsive controls are distinguished;
- compensating controls are explicitly identified;
- historical vulnerabilities remain mapped to their controls;
- authentication, authorization, vault, cryptographic, IPC, enforcement, recovery, and platform controls are represented;
- root/system compromise remains outside the guaranteed application trust boundary;
- known anti-tapjacking, overlay, detection-source, Accessibility, Usage Access, and Keystore-invalidation gaps remain visible;
- the current Accessibility-only implementation is not confused with the approved two-tier target architecture;
- control invariants are established;
- control changes are subject to impact assessment and verification-state review;
- Section 12 will provide the authoritative evidence criteria for promoting a control to **security-verified** status.

**9.22 Boundary to Section 10**

Section 9 establishes **what controls exist, what they are intended to mitigate, and what their current implementation status is**.

It does not determine whether the remaining risk is acceptable, nor does it define the security posture appropriate to each implementation phase.

Section 10 therefore establishes the **Phase-Aware Security Model**, defining which controls are mandatory at each project phase, which exposures are temporarily accepted, which controls are deferred, and what security conditions must be satisfied before the project can advance between phases.
