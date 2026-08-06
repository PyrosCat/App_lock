**6. Security Architecture and Trust Enforcement**

**6.1 Purpose**

This section defines the security architecture that connects the attack surfaces identified in Section 5 to the security boundaries established by the approved Threat Model baseline.

The purpose is to identify:

- Where security decisions are made.

- Which components enforce those decisions.

- Which trust boundaries must not be crossed without authorization.

- Which Android platform mechanisms App Lock relies upon.

- How authentication, authorization, protected-app enforcement, Vault protection, and persistence interact.

- Where security depends on continuous platform or service availability.

This section is a **security model of the existing architecture**.

It is not a replacement for the TAS, SDS, or DDS.

Detailed implementation design remains governed by those documents and by approved Architecture Decision Records.

**6.2 Security Architecture Principles**

The security architecture is based on the following principles.

**SA-001 — Independent App Lock Authorization**

Android device unlock does not constitute App Lock authorization.

The device being unlocked permits access to the Android environment but does not authorize access to App-Lock-protected applications or App Lock's confidential Vault.

The security model therefore contains two independent authorization boundaries:

Android Device Authentication

│

▼

Android Session

│

│ does NOT authorize

▼

App Lock Boundary

│

▼

App Lock Authentication

│

▼

App Lock Authorization

**SA-002 — Current Credential Defines Owner Authority**

Knowledge of the current App Lock credential establishes owner authority.

An authenticated owner may modify:

- Protected applications.

- Vault contents.

- App Lock settings.

- Security policies.

The current version provides no separate administrator, duress, hidden-profile, or multi-user authorization model.

**SA-003 — Authentication Must Precede Authorization**

Authentication establishes whether the requester is authorized.

Authorization determines what that authenticated requester may access.

A component must not infer authorization solely from:

- Device unlock state.

- Application launch.

- Activity visibility.

- Package identity.

- Process ownership.

- Prior application interaction.

**SA-004 — Sensitive Data Has Multiple Protection Boundaries**

Sensitive data is protected through layered boundaries:

1.  Android application sandbox.

2.  Encryption at rest.

3.  Android Keystore protection.

4.  Runtime authorization gating.

These layers have different purposes and must not be treated as interchangeable.

**SA-005 — Security State Must Not Depend Solely on Volatile Runtime State**

Security-critical state that must survive application restart, such as failed-authentication lockout, is persisted through protected storage.

Conversely, authorization sessions are intentionally volatile and must not survive process death or reboot.

**SA-006 — Security Availability Is Part of Security**

The continuous operation of App Lock's enforcement path is itself security-critical.

Loss of:

- Accessibility detection.

- Watchdog operation.

- Boot re-arm.

- Required permissions.

- Device-admin protection where enabled.

can affect whether App Lock can enforce its security boundary.

**6.3 Root of Trust**

The Android Keystore is the primary cryptographic root of trust.

The current architecture uses an Android Keystore-backed MasterKey through the Jetpack Security storage mechanisms.

The MasterKey protects the application's encrypted-storage architecture.

Conceptually:

Android Keystore

│

MasterKey

│

┌────────────┼────────────┐

│ │ │

▼ ▼ ▼

Credential DB Key Lockout State

Store Store Store

│

▼

Encrypted Files

The diagram represents the common root of trust, not a claim that all data uses the same cryptographic primitive or key directly.

The database passphrase and Vault file encryption keys are independent secrets.

**6.4 Keystore Trust Boundary**

The Keystore boundary separates application-usable cryptographic operations from the protected MasterKey material.

The application may request cryptographic operations through the platform but must not treat the underlying non-exportable key as ordinary application data.

**Trusted Assumption**

The baseline assumes:

- Android Keystore protects the MasterKey.

- The MasterKey remains non-exportable.

- Hardware-backed protection is used where supported.

- The Android platform provides the intended key-isolation guarantees.

**Boundary Failure**

A fully compromised/rooted operating system is below this trust boundary.

App Lock therefore does not guarantee confidentiality against an attacker who has obtained root or equivalent system privileges.

Root detection and root-response functionality are defense-in-depth mechanisms, not replacement trust foundations.

**6.5 Application Sandbox Boundary**

Android's application sandbox provides the first storage-isolation boundary.

The intended model is:

Other Application

│

│ denied by OS sandbox

▼

App Lock Private Storage

│

▼

Encrypted Application Data

Another ordinary application is not expected to directly read App Lock's private files.

The encryption layer provides additional protection against acquisition of the underlying storage representation.

The Threat Model therefore treats both the sandbox and cryptographic storage boundary as relevant security controls.

**6.6 Credential Protection Architecture**

The App Lock credential is stored as an Argon2id-derived hash and salt.

The plaintext PIN is not persisted.

The credential store is protected through the encrypted preference architecture and ultimately rooted in the Android Keystore.

The security relationship is:

User PIN

│

▼

Argon2id

│

├── Salt

│

└── Password Hash

│

▼

Encrypted Credential Store

│

▼

MasterKey

│

▼

Android Keystore

**Important Boundary**

The PIN is a credential-verification secret.

It is **not** a cryptographic parent key for the Vault or SQLCipher database.

Changing the PIN therefore does not re-key the Vault or database under the current architecture.

**6.7 Database Key Architecture**

The SQLCipher database uses a separately generated 32-byte random database passphrase.

The relationship is:

SecureRandom

│

▼

32-byte Database Passphrase

│

▼

Encrypted DB-Key Store

│

▼

Android Keystore / MasterKey

│

▼

SQLCipher Database

The database passphrase is independent of the App Lock PIN.

The database therefore does not rely on the user remembering the PIN as its direct encryption key.

**6.8 Vault Encryption Architecture**

Vault payloads are encrypted as files in the application-private encrypted file store.

The filesystem uses UUID-based filenames rather than the user's original display names.

Conceptually:

Vault Payload

│

▼

Encrypted File Store

│

├── Encrypted Content

└── UUID Filename

The original display name is maintained in the encrypted database index rather than the filesystem filename.

This creates two related but distinct confidentiality boundaries:

1.  The encrypted Vault payload.

2.  The encrypted database metadata required to interpret the payload.

Compromise of one encrypted representation does not by itself provide the intended complete Vault representation.

**6.9 Intruder-Photo Protection**

Intruder photographs use the same protected encrypted file-storage architecture as Vault payloads while being maintained in a separate logical directory.

Their security classification remains equivalent to other highly sensitive user data.

The security architecture therefore treats intruder photographs as confidential data rather than ordinary application logs or diagnostic artifacts.

**6.10 Database Metadata Protection**

The database itself is treated as confidential.

This includes metadata that may reveal:

- Protected applications.

- Vault filenames.

- MIME types.

- File sizes.

- Timestamps.

- Security events.

- Intruder events.

- Usage patterns.

Encryption therefore protects more than the Vault's actual file contents.

The database confidentiality boundary is independent of whether an individual row contains an immediately readable secret.

**6.11 Authentication Boundary**

The principal authorization boundary is:

UNAUTHENTICATED

│

PIN / BIOMETRIC

│

▼

AUTHENTICATED

│

▼

AUTHORIZED SESSION

A successful authentication event establishes App Lock authorization according to the applicable policy.

Authentication must not directly grant indefinite authorization.

Session policy determines how long that authorization remains valid.

**6.12 Authentication Mechanisms**

The current implemented authentication mechanisms relevant to the baseline are:

- PIN authentication.

- Biometric authentication through Android BiometricPrompt.

Other authentication mechanisms specified but not currently implemented must not be represented as active controls.

These include pattern, knock, and password authentication.

**6.13 Lock Session Architecture**

Authorization sessions are maintained in memory by LockSessionManager.

The session model is package-specific.

Conceptually:

Package A ──► Session A

Package B ──► Session B

Package C ──► Session C

Unlocking one protected application does not automatically authorize another protected application.

**Session Lifetime**

Session validity is governed by the configured relock policy:

- IMMEDIATE

- GRACE_10S

- SCREEN_OFF

Sessions are invalidated by applicable policy events, screen-off, reboot, and process death.

**Security Property**

The lack of persistent authorization sessions prevents a previously established App Lock session from surviving:

- Reboot.

- Process death.

- Application restart.

**6.14 Protected-App Enforcement Path**

The primary protected-app enforcement path is common to both approved detection tiers. Detection identifies a candidate foreground transition; authentication and authorization determine whether access is permitted.

    Protected App Launched
      -> Foreground Detection
           - Baseline: UsageStatsManager / Usage Access (mandatory)
           - Optional: Accessibility / AppDetectionService (enhancement)
      -> Detection-Source Selection / Trigger Processor
      -> ApplicationLockEngine
      -> LockSessionManager / LockPolicyManager
           - Valid Session -> Allow
           - No Valid Session -> LockScreenActivity -> Authentication
                - Failure -> Lockout
                - Success -> Session State -> Protected App

The enforcement decision therefore depends on the complete path rather than on any individual component or detection source. The lock engine SHALL NOT treat the identity of a detection source as proof of authorization.

The current delivered implementation differs from this target: it currently relies on Accessibility as the sole detection source. Until the baseline tier is implemented, the diagram's baseline branch is **planned** and the existing Accessibility availability risk remains open.

**6.15 Foreground Detection Boundary**

Foreground detection is a security trigger boundary. The approved target architecture does not equate that boundary with the Accessibility framework.

**Baseline Detection Tier.** UsageStatsManager, together with the Android **Usage Access** special permission, provides the mandatory foreground-detection mechanism. A foreground service samples usage events to identify the current foreground application. Sampling introduces a detection-latency and battery-cost trade-off that must be measured and controlled during implementation and security verification.

Because background activity-launch restrictions may prevent a background service from directly presenting the lock interface, the baseline tier also depends on the approved presentation mechanism and, where required by that mechanism, the **display-over-other-apps / system-alert-window** permission or the applicable platform background-activity-launch exemption. The precise presentation choice remains an implementation decision within the approved architecture.

**Optional Enhancement Tier.** The existing Accessibility-based detection mechanism is retained as an optional user-enabled enhancement. It is event-driven and is intended to provide faster foreground detection than the sampled baseline. Accessibility is therefore a performance/responsiveness enhancement, not a mandatory prerequisite for App Lock use.

**Detection-Source Selection.** A detection-source selection layer determines which available source supplies triggers to the Trigger Processor. The baseline source remains the required foundation; the Accessibility source may be enabled as an enhancement.

Whichever tier supplies the trigger, the application lock engine evaluates the resulting package information and:

- ignores App Lock's own package where appropriate;
- ignores designated system packages;
- processes protected applications;
- applies the relevant session/relock policy;
- starts the authentication path when authorization is absent.

The security significance of this boundary is twofold. First, a correct authentication implementation is insufficient if the foreground application cannot reliably be detected. Second, the authorization engine must not trust the identity of a detection source as proof of authorization: a detection event only causes App Lock to evaluate policy and session state; authentication remains the authority for access.

The current implementation state must be distinguished from the approved architecture: the delivered build currently relies on Accessibility alone. Until the baseline tier is implemented, the existing Accessibility availability risk remains open.

**6.16 Re-Entry Enforcement**

The enforcement architecture intentionally evaluates protected applications on each relevant foreground event.

It does not rely solely on a one-time "already seen" decision.

This is necessary to prevent lifecycle sequences in which:

1.  A protected application is opened.

2.  App Lock displays authentication.

3.  The user changes task state.

4.  The protected application is relaunched rapidly.

5.  The previous lock-screen state incorrectly permits access.

Historical fast-relaunch and fast-switch failures demonstrate why this path must remain security-critical.

**6.17 Vault Authorization Architecture**

Vault authorization is implemented as a runtime application self-gate.

The architecture is:

Application Entry

│

▼

App Lock Self-Gate

│

▼

Authentication

│

▼

Authorized Application State

│

▼

Vault UI

│

▼

Database / Encrypted Files

The Vault is not protected by deriving its encryption key directly from the PIN.

Instead:

- The UI requires App Lock authorization.

- The database is encrypted independently.

- Vault files are encrypted independently.

- Key material ultimately depends on the Keystore trust boundary.

**6.18 Vault Runtime Boundary Versus Offline Boundary**

The Vault architecture contains two distinct security problems.

**Runtime Boundary**

An attacker must not reach Vault functionality through the App Lock UI without authentication.

This is enforced through the self-gate and lifecycle re-gating.

**Offline Boundary**

An attacker obtaining raw storage must not obtain usable Vault plaintext.

This is addressed by:

- Android application sandboxing.

- SQLCipher database encryption.

- Encrypted file storage.

- Keystore-backed key protection.

- UUID filesystem naming.

These boundaries must remain conceptually separate.

The PIN is not the direct cryptographic root for Vault data.

**6.19 Self-Gating Architecture**

App Lock itself is a protected application surface.

When the user leaves App Lock and later returns, sensitive application state must not remain accessible solely because a previous authentication occurred.

The self-gate therefore participates in the same authorization model as protected third-party applications.

The lifecycle observer and related state handling re-establish the authentication boundary when required.

This prevents App Lock from becoming an authorization bypass around its own Vault and security settings.

**6.20 Lock-Screen Navigation Enforcement**

LockScreenActivity participates in preventing navigation around the authentication boundary.

The current architecture includes handling for:

- Back navigation.

- Recents behavior.

- Activity pause.

- Application switching.

- Protected-app re-entry.

The purpose is not merely user-interface consistency.

Navigation must not create a path from:

Authentication Required

│

└──► Unauthenticated

│

▼

Protected Content

without a valid authorization decision.

**6.21 Brute-Force Protection Boundary**

Failed authentication attempts are governed by persisted lockout state.

The lockout counter and lockout-until timestamp are maintained in protected storage rather than solely in memory.

This is necessary because a process restart must not reset the attacker's attempt budget.

The security boundary is therefore:

Authentication Attempt

│

▼

Failure Counter

│

▼

Protected Persistent State

│

▼

Lockout Decision

A restart or ordinary process death must not provide an alternate path around this boundary.

**6.22 Watchdog Architecture**

The watchdog provides security-health monitoring for the protected-app enforcement architecture rather than acting as a second authorization engine.

In the approved target architecture it must be capable of assessing the health of the mandatory baseline path and, separately, the optional Accessibility enhancement when that enhancement is enabled.

    Detection Configuration
      -> Expected Protection State
      -> ProtectionWatchdogService
      -> Health Evaluation
           - Baseline Healthy
           - Baseline Missing / Degraded
           - Optional Accessibility Healthy
           - Optional Accessibility Missing / Degraded

A loss of the optional Accessibility enhancement must not be reported as total loss of App Lock protection when the baseline path is healthy.

A loss or degradation of the mandatory baseline path is a security-critical condition. The watchdog SHALL detect and report it where technically possible and SHALL NOT represent the protection state as healthy when the required baseline is unavailable.

The watchdog cannot independently grant Usage Access or Accessibility permission. It therefore remains a detection and response mechanism, not a complete preventive control.

**6.23 Protection Availability Boundary**

The approved target protection architecture is:

    Boot / Application Startup
      -> ProtectionWatchdogService
      -> Detection Configuration
           - Mandatory Usage Access Baseline
           - Optional Accessibility Enhancement
      -> Detection-Source Selection / Trigger Processor
      -> Foreground Detection
      -> Lock Enforcement

Failure of the mandatory baseline, the presentation mechanism required to display the lock interface, or another required security component can interrupt enforcement and is therefore a security condition.

Failure of the optional Accessibility enhancement is a degraded enhancement state. It becomes a total enforcement failure only if the mandatory baseline is also unavailable or the common enforcement path is otherwise unable to operate.

The current implementation differs from this target: Accessibility is currently the sole detection source. That difference remains an open implementation risk until the two-tier architecture is delivered.

**6.24 Boot Security Architecture**

Reboot has two intentional security consequences.

**Session State**

All in-memory App Lock authorization sessions disappear.

**Persistent Security State**

The following remain available, subject to Keystore/storage integrity:

- Credential material.

- Database key.

- Vault data.

- Lockout state.

- Persistent configuration.

The boot path must therefore restore protection without restoring authorization.

This establishes the intended invariant:

Reboot may reset authorization state, but it must not create unauthorized access.

**6.25 Device Admin Boundary**

Device Admin provides an optional uninstall-protection boundary.

The intended security chain is:

Uninstall Attempt

│

▼

Android Device Admin

│

├── Protection Active

│ │

│ ▼

│ Removal Resisted

│

└── Protection Not Active

│

▼

Normal Android

uninstall behavior

Device Admin does not protect Vault encryption keys directly.

Its purpose is to protect continued availability of the application and its enforcement mechanism.

**6.26 Inter-Application Boundary**

App Lock treats other ordinary applications as untrusted.

The principal isolation mechanisms are:

- Android application sandbox.

- Component export restrictions.

- Framework permissions.

- Device Admin framework protection.

- Accessibility framework controls.

- Cryptographic storage.

App Lock does not define an application-to-application trust relationship with arbitrary installed applications.

**6.27 Exported-Component Trust Model**

Exported components are deliberately limited.

The current model permits:

- Launcher invocation of MainActivity.

- Framework boot invocation of BootReceiver.

- Device Admin framework invocation of UninstallProtectionReceiver.

The security architecture requires that none of these externally reachable paths grant unauthorized access to:

- Vault data.

- Credential material.

- Database contents.

- Intruder photographs.

- Protected-app authorization.

The exported status of a component is therefore not itself an authorization grant.

**6.28 Overlay and UI Trust Boundary**

The current delivered architecture does not use a system overlay as the primary lock-screen mechanism; LockScreenActivity is an Activity-based authentication surface. This subsection therefore contains an **inbound UI trust problem**: another application may attempt to place an overlay above the authentication interface where Android permits it.

    Untrusted Application -> System Overlay -> LockScreenActivity -> User Interaction

The current implementation does not yet establish a complete anti-tapjacking / obscured-touch defense. This remains an identified security gap and must not be treated as implemented protection.

The approved target architecture may additionally present the baseline lock interface via an App-Lock-owned overlay (AS-020; display-over-other-apps / system-alert-window) rather than an Activity; if that mechanism is chosen it adds an outbound overlay boundary to analyze when designed, and SHALL NOT be represented as implemented until built and security-verified.

**6.29 Accessibility Peer-Service Boundary**

The Accessibility framework creates an additional trust boundary because another Accessibility Service may have visibility into UI events or the ability to inject accessibility actions.

App Lock does not possess an application-level mechanism capable of fully preventing a privileged peer Accessibility Service from exercising capabilities granted by Android.

The approved baseline therefore treats this as a best-effort defense area rather than a guaranteed protection boundary.

**6.30 Root/System Trust Boundary**

Root or equivalent system compromise is below the application's trust boundary.

The model is:

Application Security Boundary

─────────────────────────────

App Lock

Android Sandbox

Android Keystore

─────────────────────────────

System / Root Boundary

If an attacker controls the lower boundary, App Lock cannot guarantee that:

- Application memory remains confidential.

- Keystore-backed operations remain trustworthy.

- Private storage remains inaccessible.

- Application execution remains untampered.

Root detection and response are therefore mitigation mechanisms, not foundational guarantees.

**6.31 Debug and Tamper Boundary**

Debugging, instrumentation, and application tampering represent an application-integrity surface.

The current baseline identifies debug and tamper protections as planned security-hardening functionality rather than effective current controls.

Accordingly:

- Their attack surfaces are acknowledged.

- Their absence is not represented as mitigation.

- Their future implementation requires security verification.

- Their introduction or modification requires Threat Model reassessment.

**6.32 Security-Critical Invariants**

The architecture must preserve the following invariants. INV-001 through INV-010 are unchanged; INV-011 and INV-012 retain their existing identifiers and meaning (INV-011 reworded to be tier-aware); INV-013 and INV-014 are new detection invariants.

- **INV-001 — No Protected-App Access Without Authorization.** A protected application must not become usable through App Lock's enforcement path without valid App Lock authorization.
- **INV-002 — Device Unlock Does Not Authorize App Lock.** Android device authentication must never implicitly establish an App Lock session.
- **INV-003 — PIN Cannot Be Reset Without Authorization.** Changing the credential requires knowledge of the current credential.
- **INV-004 — Authentication Sessions Are Volatile.** App Lock authorization must not survive reboot or process death.
- **INV-005 — Lockout Survives Process Restart.** Authentication failure state must not be reset by ordinary process restart.
- **INV-006 — Vault UI Requires App Lock Authorization.** Returning to App Lock must not bypass its self-gate.
- **INV-007 — Vault Storage Does Not Depend on Plaintext PIN Persistence.** The plaintext PIN must never be stored as an encryption key or persistent secret.
- **INV-008 — Sensitive Storage Remains Encrypted.** Vault data, intruder photographs, credential material, and database contents must remain behind their defined encryption boundaries.
- **INV-009 — Exported Components Cannot Grant Sensitive Authorization.** External invocation of an exported component must not establish App Lock authorization.
- **INV-010 — Reboot Does Not Create Access.** Reboot must clear authorization sessions without weakening persistent protection.
- **INV-011 — Loss of Enforcement Is a Security Condition.** Loss or degradation of a required enforcement mechanism — the mandatory baseline detection path, the required lock-interface presentation capability, watchdog operation, or equivalent — must be treated as a security-relevant condition, not merely a reliability event.
- **INV-012 — Planned Controls Are Not Effective Controls.** A specified but unimplemented security mechanism must never be represented as providing current protection.
- **INV-013 — Mandatory Detection Does Not Depend on Accessibility.** The approved protected-app enforcement architecture must remain operational with Accessibility disabled. Accessibility may enhance detection responsiveness but must not be the sole required foreground-detection mechanism.
- **INV-014 — Detection Failure Is Classified by Tier.** Loss of the mandatory baseline is an enforcement-security failure. Loss of the optional Accessibility enhancement is an enhancement degradation unless the baseline is also unavailable.

**6.33 Security Architecture Failure Conditions**

The following conditions are architecturally significant failures:

- A protected application becomes accessible without App Lock authorization.

- A Vault payload becomes readable without the required authorization/trust boundary.

- The PIN can be changed without proving the current credential.

- Lockout state can be reset through process restart.

- A previous App Lock session survives reboot.

- App Lock can resume directly into sensitive functionality without its required self-gate.

- An exported component establishes unauthorized access.

- Protected storage becomes available as plaintext through ordinary storage extraction.

- Loss of the enforcement mechanism occurs without the intended security response.

- Key material becomes exportable outside the Keystore trust boundary.

- A future recovery mechanism provides an alternate credential bypass.

These conditions will serve as architectural predicates for later threat and security-test definitions.

**6.34 Architecture-to-Attack-Surface Relationship**

The relationship between Sections 5 and 6 is intentionally one-to-one at the architectural level, with the detection attack surface explicitly separated into its baseline and optional tiers. Attack-surface identifiers are those established in TM v2 §5.2.

| Attack Surface | Primary Security Boundary |
|----|----|
| MainActivity | Application self-gate |
| LockScreenActivity | Authentication boundary |
| AppDetectionService (AS-003) | Optional Accessibility detection enhancement |
| ProtectionWatchdogService | Protection-health monitoring |
| BootReceiver | Reboot persistence |
| UninstallProtectionReceiver | Device Admin |
| UsageStatsManager / Usage Access (AS-019) | Mandatory baseline foreground detection |
| Baseline lock-interface presentation mechanism (AS-020) | Lock-interface presentation boundary |
| Accessibility framework (AS-007) | Optional enhancement foreground detection and peer-service boundary |
| Android Keystore | Cryptographic root of trust |
| Private storage | Sandbox + encryption |
| SQLCipher database | Database confidentiality/integrity |
| Encrypted file store | Payload confidentiality/integrity |
| Authentication UI | Credential authorization |
| Session state | Runtime authorization |
| Lifecycle | Session/enforcement continuity |
| Notifications | Information-disclosure boundary |
| Installation/update | Software integrity |

This mapping provides the foundation for threat/control traceability. The current implementation state must be distinguished from the approved target architecture wherever a target component (AS-019/AS-020, and the detection-source selection layer AS-021) has not yet been implemented.

**6.35 Security Architecture Change Control**

The architecture described here is locked to the approved baseline.

A change to any of the following requires security-architecture impact assessment:

- Root of trust.
- Credential architecture.
- Database-key architecture.
- Vault encryption architecture.
- Authentication boundary.
- Session model.
- Protected-app enforcement path.
- Foreground-detection architecture, including the Usage Access baseline, optional Accessibility enhancement, detection-source selection, and lock-interface presentation mechanism.
- Watchdog/recovery architecture.
- Boot persistence.
- Device Admin usage.
- Exported components.
- Android permissions forming security boundaries.
- Storage architecture.
- Backup/restore architecture.
- Runtime authorization model.

A change must not be incorporated into the Threat Model merely by editing this section. The corresponding architectural decision, requirement, implementation, and traceability artifacts must be updated through the project's approved change-control process.

**6.36 Section 6 Boundary**

Section 6 defines **how the approved architecture establishes and enforces security boundaries**.

It establishes:

- The root of trust.

- Authentication and authorization boundaries.

- Credential protection.

- Database and Vault cryptographic boundaries.

- Protected-app enforcement.

- Session handling.

- Lockout enforcement.

- Watchdog and boot persistence.

- Device Admin protection.

- Application and platform trust boundaries.

- Architectural security invariants.

- Security-critical failure conditions.

It does not yet define the complete catalog of individual threats, attack scenarios, risk ratings, mitigations, security tests, or residual risks.

Those analyses must use the architecture and invariants defined here as their authoritative baseline.

Any later threat that contradicts these architectural facts must trigger a documented baseline review rather than silently changing the model.
