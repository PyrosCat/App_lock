**5. Attack Surface**

**5.1 Purpose**

This section inventories the security-relevant attack surface of the current App Lock implementation.

The attack surface consists of application components, Android framework interfaces, IPC entry points, permissions, storage locations, lifecycle mechanisms, authentication interfaces, and externally observable behaviors through which an attacker may attempt to influence or obtain information from App Lock.

This section describes **what is exposed**.

It does not determine whether an exposure constitutes a successful attack, nor does it establish the effectiveness of individual mitigations. Those determinations are made in the threat, control, and verification sections.

The inventory is based on the current implementation and manifest configuration rather than on planned functionality.

**5.2 Attack Surface Inventory**

The inventory describes the current implementation and, for traceability, the approved target-architecture surfaces marked *planned*. The current delivered build uses AppDetectionService and the Android Accessibility framework for foreground detection. The approved target architecture additionally introduces Usage Access and UsageStatsManager as the baseline detection path and retains Accessibility as an optional enhancement.

The principal security-relevant attack surfaces are:

| **ID** | **Surface** | **Type** | **Security Significance** |
|----|----|----|----|
| AS-001 | MainActivity | Activity | Primary application UI and application self-gate entry point |
| AS-002 | LockScreenActivity | Activity | Authentication boundary for protected applications |
| AS-003 | AppDetectionService | Accessibility Service | Current foreground-app detection and approved optional enhancement |
| AS-004 | ProtectionWatchdogService | Foreground Service | Protection-health monitoring and recovery signaling |
| AS-005 | BootReceiver | BroadcastReceiver | Boot-time protection re-arm |
| AS-006 | UninstallProtectionReceiver | Device Admin Receiver | Uninstall-protection framework boundary |
| AS-007 | Android Accessibility framework | Framework interface | Current detection interface and approved optional enhancement |
| AS-008 | Device Admin framework | Framework interface | Administrative/uninstall protection |
| AS-009 | Android Keystore | Cryptographic interface | Root of trust for protected key material |
| AS-010 | App-private storage | Storage boundary | Database, encrypted files, preferences, and application state |
| AS-011 | SQLCipher/Room database | Data store | Protected-app configuration, Vault index, and security/intruder metadata |
| AS-012 | Encrypted file store | Data store | Vault payloads and intruder photographs |
| AS-013 | App Lock authentication UI | UI boundary | PIN and biometric authentication |
| AS-014 | Lock-session state | Runtime state | Per-application authorization state |
| AS-015 | Exported Android components | IPC surface | External framework/application interaction |
| AS-016 | Android lifecycle and process management | Lifecycle surface | Process death, force-stop, reboot, and recovery behavior |
| AS-017 | Application notifications | Observable surface | Security-relevant externally visible information |
| AS-018 | Application installation/update boundary | Package surface | Application artifact and dependency integrity |
| AS-019 | UsageStatsManager / Usage Access | Framework interface + special permission | Baseline foreground detection *(planned — target architecture)* |
| AS-020 | Baseline lock-interface presentation mechanism | UI / window surface | Baseline lock-screen presentation via overlay or activity-launch exemption *(planned — target architecture)* |
| AS-021 | Detection-source selection layer | Runtime component | Selects the active detection tier *(planned — target architecture)* |

The inventory is deliberately broader than the six manifest components because security-relevant interfaces also exist through Android services, storage, lifecycle behavior, and cryptographic facilities.

AS-019 through AS-021 describe the approved target architecture and are marked *planned*. They are inventoried here so that target-architecture threats can trace to a stable attack-surface identifier per §5.30. They receive full security classification when implemented and security-verified.

**5.3 Component Inventory**

The current application declares six externally relevant Android components:

**Activities**

- MainActivity

- LockScreenActivity

**Services**

- AppDetectionService

- ProtectionWatchdogService

**BroadcastReceivers**

- BootReceiver

- UninstallProtectionReceiver

**ContentProviders**

No application-owned ContentProvider is declared.

The absence of a ContentProvider is security-relevant because there is no application-defined provider surface through which another application can query or mutate App Lock data.

**5.4 AS-001 — MainActivity**

MainActivity is the primary App Lock application interface.

It is externally launchable because it contains the application launcher entry point.

**Exposure**

The component is exported to satisfy the Android launcher contract.

This makes MainActivity an externally reachable Activity and therefore an IPC/UI entry point.

**Security-Relevant Functions**

The Activity provides access to application functionality including:

- App Lock configuration.

- Protected-application management.

- Vault functionality.

- Security settings.

- Authentication-dependent application state.

- Self-gating behavior when returning to the application.

**Security Boundary**

When a PIN has been configured, entering sensitive application functionality is subject to the App Lock self-gate.

The launcher entry point itself must therefore not be equated with authorization to access protected application state.

**Relevant Attack Surface**

An attacker may attempt to:

- Launch the Activity directly.

- Resume the Activity from a previous state.

- Exploit lifecycle transitions.

- Manipulate navigation state.

- Attempt to reach Vault functionality without authentication.

- Abuse application intents or task behavior.

- Exploit state retained across Activity recreation.

Detailed self-gate and lifecycle threats are analyzed separately.

**5.5 AS-002 — LockScreenActivity**

LockScreenActivity is the authentication Activity used to authorize access to protected applications.

**Exposure**

LockScreenActivity is declared exported="false".

It is therefore not intended to be directly launched by another ordinary application.

**Security-Relevant Functions**

The Activity:

- Presents App Lock authentication.

- Supports PIN authentication.

- Initiates biometric authentication where configured.

- Establishes successful App Lock authorization.

- Participates in relock behavior.

- Terminates or redirects unauthorized navigation attempts.

**Security Boundary**

This Activity sits directly on the principal authentication boundary:

Unauthenticated

│

│ Valid authentication

▼

Authenticated App Lock Session

**Relevant Attack Surface**

The Activity remains exposed to attacks that do not require external component launching, including:

- UI interaction.

- Tapjacking.

- Overlay obscuration.

- Event injection.

- Navigation manipulation.

- Back/Recents interaction.

- Lifecycle manipulation.

- Authentication race conditions.

- Screenshot or screen-recording attempts.

- UI spoofing.

exported="false" reduces IPC exposure but does not eliminate attacks against the Activity while it is displayed.

**5.6 AS-003 — AppDetectionService**

AppDetectionService is the current Accessibility Service responsible for foreground-application detection. In the approved architecture, this component becomes the **optional Accessibility enhancement tier** rather than the mandatory detection mechanism.

**Exposure**

The service is:

- exported="false".

- Protected through BIND_ACCESSIBILITY_SERVICE.

The Android Accessibility framework is responsible for binding the service.

**Security-Relevant Functions**

In the current implementation, the service (1) receives Accessibility events, (2) identifies the foreground application, (3) passes foreground information to the application lock engine, (4) causes authorization policy to be evaluated, and (5) initiates the lock-screen path when authentication is required. In the approved architecture, these functions remain valid for the optional Accessibility tier; the baseline enforcement path is provided separately through Usage Access and UsageStatsManager (AS-019).

**Security Boundary**

In the current implementation, this component forms the boundary:

Android Accessibility Framework

│

▼

AppDetectionService

│

▼

ApplicationLockEngine

│

▼

LockScreenActivity

The approved architecture changes the overall model so that two detection sources converge on a common enforcement path:

Usage Access / UsageStatsManager → Detection Selection → Trigger Processor → ApplicationLockEngine → LockScreenActivity

Accessibility / AppDetectionService → Detection Selection → Trigger Processor → ApplicationLockEngine → LockScreenActivity

**Security Significance**

The service remains security-relevant because its availability affects the optional enhancement tier and because Accessibility capabilities can affect authentication-UI integrity. However, under the approved architecture, loss of AppDetectionService SHALL NOT by itself constitute loss of core App Lock enforcement.

**Relevant Attack Surface**

The service is exposed to:

- Accessibility permission changes.

- Service unbinding.

- Process death.

- OEM lifecycle intervention.

- Android background restrictions.

- Restricted Settings behavior.

- Accessibility event delivery failures.

- Peer Accessibility Services.

- Application restart conditions.

- Boot/recovery interactions.

**5.7 AS-004 — ProtectionWatchdogService**

ProtectionWatchdogService is a foreground service responsible for monitoring protection availability.

**Exposure**

The service is:

- exported="false".

- Not intended to be directly invoked by other applications.

**Security-Relevant Functions**

The watchdog SHALL monitor the health of the active baseline enforcement path and, where applicable, the optional Accessibility enhancement. Its security-relevant monitoring responsibilities include:

- baseline Usage Access availability (AS-019);

- baseline detector health;

- lock-interface presentation capability (AS-020);

- Accessibility availability when the enhancement is enabled (AS-003/AS-007);

- protection-loss conditions;

- lifecycle and restart conditions.

The watchdog also records protection-disabled events, raises a high-priority notification when intervention is required, and provides a recovery path into Android settings.

**Security Significance**

The watchdog is part of the application's availability/security monitoring architecture. It does not itself provide the primary foreground-app detection mechanism; its security significance comes from its ability to detect or report loss of that mechanism.

The watchdog must distinguish between loss of a required baseline control, loss of an optional enhancement, and inability to determine detector health. A missing Accessibility enhancement SHALL NOT be reported as total loss of App Lock protection when the baseline path remains healthy.

**Relevant Attack Surface**

The watchdog is exposed to:

- Process death.

- Force-stop.

- OEM background restrictions.

- Android lifecycle behavior.

- Startup/restart failures.

- Timing gaps between protection loss and detection.

- False indications of service health.

The current monitoring model includes a polling interval and therefore does not provide instantaneous detection.

**5.8 AS-005 — BootReceiver**

BootReceiver receives Android boot completion events and participates in restoring protection state.

**Exposure**

The receiver is exported because Android framework boot broadcasts require an externally invocable receiver.

**Security-Relevant Functions**

The receiver:

- Receives BOOT_COMPLETED.

- Re-establishes the watchdog path.

- Records or participates in boot-related security state.

**Security Significance**

Boot persistence is security-relevant because App Lock authentication sessions are intentionally lost during reboot while protection must be re-established.

**External Invocation**

A malicious application may attempt to send a boot-like broadcast where Android permits such invocation.

The current design treats such invocation as harmless because triggering the watchdog/re-arm path does not grant authorization.

**Security Boundary**

The receiver therefore has an intentionally exposed framework-facing entry point with limited security authority.

It must not be assumed that every invocation represents a genuine hardware reboot.

**5.9 AS-006 — UninstallProtectionReceiver**

UninstallProtectionReceiver is the Device Admin receiver used for optional uninstall protection.

**Exposure**

The receiver is exported because the Android Device Admin framework requires the corresponding receiver to be externally reachable by the framework.

**Protection**

It is protected through:

BIND_DEVICE_ADMIN

This restricts invocation to the privileged Android framework boundary.

**Security-Relevant Functions**

The receiver participates in:

- Device-admin registration.

- Administrative callbacks.

- Uninstall-protection behavior.

**Security Significance**

Unauthorized removal of App Lock can eliminate the enforcement mechanism.

Device Admin therefore represents an availability and integrity boundary rather than a data-encryption boundary.

**5.10 AS-007 — Accessibility Framework Interface**

The Android Accessibility framework is an external security-relevant interface. In the current implementation, App Lock uses it for foreground-app detection. In the approved architecture, it becomes an optional detection interface that may provide faster event-driven foreground detection when explicitly enabled by the user.

**Security-Relevant Inputs**

Potential inputs include:

- Accessibility events.

- Service binding state.

- Service enablement state.

- Permission state.

- Platform restrictions affecting accessibility.

- Interactions with peer Accessibility Services.

**Security-Relevant Outputs**

When the Accessibility enhancement is active, App Lock uses these inputs to identify foreground transitions and submit detection information to the common enforcement path.

**Attack Surface Characteristics**

The framework introduces an optional detection dependency; UI-observation and event-injection capabilities that may be available to peer Accessibility Services; platform and OEM availability limitations; and potential silent failure of the event stream, in which a service may appear enabled while the expected event stream is absent.

The Accessibility interface SHALL therefore remain a security-relevant attack surface, but it SHALL NOT be represented as the mandatory App Lock enforcement dependency in the approved architecture. This distinction is important and must remain explicit throughout later threat analysis.

**5.11 AS-008 — Device Admin Framework Interface**

The Device Admin framework is an external privileged interface.

App Lock uses it for optional uninstall protection.

**Security-Relevant Boundary**

The application relies on Android to:

- Maintain administrator state.

- Enforce the administrative relationship.

- Deliver administrator callbacks.

- Restrict invocation of protected administrative operations.

**Attack Surface**

Relevant conditions include:

- Administrator activation/deactivation.

- Device-admin lifecycle.

- Uninstall attempts.

- Framework behavior changes.

- Device policy changes.

Detailed attack scenarios belong in the threat analysis rather than this inventory.

**5.12 AS-009 — Android Keystore**

Android Keystore represents the deepest cryptographic attack surface in the application.

**Security-Relevant Material**

The Keystore protects the application MasterKey used by the current encrypted-storage architecture.

The MasterKey is used as the root protecting:

- Credential storage.

- Database key storage.

- Lockout state storage.

- Encrypted file storage mechanisms.

**Security Boundary**

The application must not treat the MasterKey as ordinary application data.

Its security depends on the Android Keystore boundary.

**Attack Surface**

Relevant conditions include:

- Key creation.

- Key retrieval.

- Key use.

- Keystore availability.

- Key invalidation.

- Hardware-backed versus software-backed implementation.

- OS/device migration behavior.

- Keystore reset or corruption.

The current implementation does not provide a complete recovery path for permanent Keystore invalidation.

That condition is therefore an attack/reliability surface requiring later threat and risk analysis.

**5.13 AS-010 — Application-Private Storage**

App Lock maintains security-sensitive information in application-private storage.

The storage surface includes:

- EncryptedSharedPreferences stores.

- SQLCipher database files.

- Encrypted Vault blobs.

- Intruder photographs.

- Application configuration/state.

- Runtime-generated storage artifacts.

**Security Boundary**

The first protection boundary is Android application sandboxing.

The second protection boundary is encryption at rest.

An attacker who obtains only ordinary filesystem access should not be able to treat the stored representations as plaintext application data.

**5.14 AS-011 — SQLCipher/Room Database**

The encrypted database contains security-sensitive application information.

**Stored Information**

The database includes information associated with:

- Protected applications.

- Vault items.

- Vault metadata.

- Security events.

- Intruder events.

- Other application state required by the current design.

**Security Significance**

The database is confidential even when its payload records are not themselves secret credentials.

Database metadata may reveal:

- What applications the user protects.

- Vault item names.

- File sizes.

- MIME types.

- Timestamps.

- Intruder-event information.

- Usage patterns.

- Other security-relevant metadata.

**Cryptographic Boundary**

The SQLCipher database is keyed using a separately generated database passphrase stored through the Keystore-backed storage architecture.

The database passphrase is not derived from the App Lock PIN.

**5.15 AS-012 — Encrypted File Store**

The encrypted file store contains Vault payloads and intruder photographs.

**Storage Model**

Vault and intruder files are stored as encrypted blobs.

Random UUID-based filenames are used rather than the user's actual display names.

**Security-Relevant Properties**

The filesystem representation therefore does not directly expose the original Vault filename.

The encrypted blobs remain dependent on the application's cryptographic storage mechanism for plaintext recovery.

**Attack Surface**

Relevant conditions include:

- File creation.

- File retrieval.

- File deletion.

- File replacement.

- Blob enumeration.

- Metadata leakage.

- Encryption/decryption operations.

- Storage corruption.

- Unauthorized application-process access.

**5.16 AS-013 — Authentication Interface**

The authentication surface includes the PIN entry interface and biometric authentication flow.

**PIN Surface**

The PIN is entered through the App Lock authentication UI.

The plaintext PIN is not intended to be persisted.

The credential verification material consists of an Argon2id hash and salt stored through the encrypted preference architecture.

**Biometric Surface**

Biometric authentication is delegated to Android BiometricPrompt.

The application receives the resulting authentication outcome rather than directly implementing biometric recognition.

**Attack Surface**

Relevant attack vectors include:

- Repeated authentication attempts.

- Input observation.

- UI manipulation.

- Event injection.

- Tapjacking.

- Overlay attacks.

- Authentication-state races.

- Navigation around the authentication screen.

- Incorrect handling of authentication failure.

- Incorrect session establishment.

**5.17 AS-014 — Lock Session State**

The authorization session is a runtime security surface.

**Current Representation**

Session state resides in LockSessionManager.unlockedApps.

It is maintained in memory and associated with protected application package names.

**Security-Relevant Properties**

Sessions:

- Are not persisted.

- Do not survive reboot.

- Do not survive process death.

- Are governed by relock policy.

- Are invalidated by applicable lifecycle events.

- Are not shared automatically between protected applications.

**Attack Surface**

Relevant conditions include:

- Session creation.

- Session lookup.

- Session expiration.

- Application switching.

- Screen-off handling.

- Process death.

- Reboot.

- Rapid relaunch.

- Rapid switching.

- Incorrect session association.

**5.18 AS-015 — Exported Component Surface**

The current manifest contains three exported components:

| **Component**               | **Exported** | **Reason**             |
|-----------------------------|-------------:|------------------------|
| MainActivity                |          Yes | Launcher entry point   |
| BootReceiver                |          Yes | Android boot broadcast |
| UninstallProtectionReceiver |          Yes | Device Admin framework |

The security-sensitive Activity and services are not exported:

| **Component**             | **Exported** |
|---------------------------|-------------:|
| LockScreenActivity        |           No |
| AppDetectionService       |           No |
| ProtectionWatchdogService |           No |

No custom permissions are defined by App Lock.

**Security Rule**

Every exported component must have an explicit reason for exposure and a corresponding analysis of whether its externally reachable behavior can cross an authorization boundary.

An exported component must not be considered safe merely because its intended caller is the Android framework.

**5.19 AS-016 — Lifecycle and Process-Control Surface**

Android lifecycle behavior is security-relevant because App Lock relies on continuously running enforcement and intentionally stores authorization sessions only in memory.

Relevant lifecycle conditions include:

- Activity creation.

- Activity destruction.

- Activity pause/resume.

- Process death.

- Service death.

- Service restart.

- Force-stop.

- Device reboot.

- Application restart.

- Background restrictions.

- OEM process-management behavior.

**Security Significance**

Lifecycle transitions can alter:

- Whether enforcement is active.

- Whether an authentication screen remains visible.

- Whether a session remains valid.

- Whether the watchdog is running.

- Whether foreground-app events continue to arrive.

Historical App Lock bypasses demonstrate that lifecycle transitions constitute a genuine attack surface rather than merely a reliability concern.

**5.20 AS-017 — Notification Surface**

App Lock produces notifications associated with foreground-service operation and protection-health conditions.

Notifications are externally observable while the device is accessible.

**Security Requirement**

Notifications must not disclose sensitive information such as:

- PIN values.

- Vault filenames or contents.

- Protected application details.

- Intruder photographs.

- Security-event details beyond what is required for safe user notification.

The notification surface therefore represents a potential metadata-disclosure channel.

The current implementation's notification content must remain consistent with this boundary.

**5.21 AS-018 — Installation and Update Surface**

The application installation and update process represents a security-relevant software-integrity boundary.

Relevant elements include:

- Application package.

- Manifest.

- Compiled application code.

- Third-party dependencies.

- Release configuration.

- Minification/optimization.

- Signing.

- Installation/update behavior.

**Security Significance**

A vulnerable or malicious release artifact can invalidate runtime security controls.

The attack surface therefore extends upstream into the build and dependency process even though those mechanisms are not Android runtime components.

The Threat Model does not treat build-system compromise as an ordinary runtime attacker capability, as established in Section 4.

**5.22 No Application ContentProvider Surface**

The current App Lock application declares no application-owned ContentProvider.

This is an explicit negative attack-surface finding.

There is therefore no App Lock-defined provider API for another application to:

- Query Vault metadata.

- Query security logs.

- Insert or modify Vault records.

- Delete application data.

- Retrieve encrypted blobs.

Any future ContentProvider introduction must be treated as a new externally reachable surface and trigger Threat Model reassessment.

**5.23 Backup and Restore Surface**

The current application has no supported application-level backup/restore mechanism.

allowBackup is disabled.

This reduces the current backup extraction surface and prevents a supported restore mechanism from becoming an alternate authorization path.

However, the absence of backup is also a data-recovery limitation.

A future backup or migration mechanism must not reuse this section's current trust assumptions without security review.

**5.24 External Data-Exposure Surfaces**

Security-sensitive information may be exposed through more than direct storage access.

The current attack-surface inventory therefore includes:

- Application UI.

- Authentication UI.

- Notifications.

- Database metadata.

- Filesystem metadata.

- Activity/task state.

- Screenshots and screen recording.

- Accessibility-visible UI.

- Application lifecycle behavior.

- Error handling and recovery behavior.

These surfaces may expose information even when the underlying encrypted storage remains uncompromised.

**5.25 Attack Surface by Security Property**

| **Surface** | **Confidentiality** | **Integrity** | **Availability** |
|----|---:|---:|---:|
| MainActivity | High | High | Medium |
| LockScreenActivity | High | High | High |
| AppDetectionService | Medium | High | Critical |
| ProtectionWatchdogService | Low/Medium | High | High |
| BootReceiver | Low | High | High |
| UninstallProtectionReceiver | Low | High | High |
| Accessibility framework | Medium | High | Critical |
| Device Admin | Low | High | High |
| Android Keystore | Critical | Critical | Critical |
| Private storage | Critical | Critical | High |
| SQLCipher database | Critical | Critical | High |
| Encrypted file store | Critical | Critical | High |
| Authentication UI | Critical | Critical | Critical |
| Session state | High | Critical | Critical |
| Notifications | Medium | Medium | Low |
| Installation/update | Critical | Critical | High |

These classifications identify the security significance of each surface. They do not constitute final threat severity ratings.

Threat severity is determined through the risk methodology established elsewhere in the Threat Model.

The current-implementation classification remains applicable to the current Accessibility path (AS-003/AS-007). For the approved target architecture, the following surfaces require equivalent security classification when implemented:

| **Surface** | **Confidentiality** | **Integrity** | **Availability** |
|----|---:|---:|---:|
| AS-019 UsageStatsManager / Usage Access | Medium | High | Critical |
| AS-020 Baseline lock-interface presentation mechanism | High | Critical | Critical |
| AS-003/AS-007 Accessibility enhancement | Medium | High | Medium/High |

The final classification of the presentation mechanism (AS-020) SHALL be confirmed when the implementation decision between overlay presentation and the applicable activity-launch mechanism is made.

**5.26 Attack Surface Change Rules**

The following changes constitute attack-surface changes and require Threat Model impact assessment:

- Adding an Activity.

- Removing or adding exported="true".

- Adding a Service.

- Adding a BroadcastReceiver.

- Adding a ContentProvider.

- Adding a custom permission.

- Changing an existing component's permission.

- Changing Accessibility behavior.

- Changing Device Admin behavior.

- Adding a network interface.

- Adding backup or restore.

- Changing encryption or key-storage architecture.

- Adding a new storage location.

- Changing Vault file handling.

- Changing authentication mechanisms.

- Changing session persistence.

- Adding externally visible notifications containing new information.

- Changing application lifecycle or process-management behavior.

- Adding a privileged Android capability.

- Adding a third-party component with access to security-sensitive data.

In addition to the existing change triggers, the following changes constitute attack-surface changes and require Threat Model impact assessment:

- adding, removing, or replacing a foreground-detection source;

- changing the required/optional status of Accessibility;

- introducing or changing the Usage Access requirement;

- changing the lock-interface presentation mechanism (overlay or activity-launch);

- changing detection-source selection behavior.

The existing trigger "changing Accessibility behavior" remains applicable to the optional enhancement tier.

A change to any of these surfaces must not be considered documentation-only.

**5.27 Planned Versus Effective Attack-Surface Controls**

The attack surface inventory must describe the current implementation, not planned functionality.

Accordingly, controls that are only specified but not implemented must not be presented here as effective protections.

This distinction is particularly important for currently planned or incomplete capabilities including:

- Root detection.

- Root response.

- Tamper/integrity detection.

- Debug protection.

- Anti-tapjacking protection.

- Secure-memory framework.

- Keystore-invalidation recovery.

- Audit-log tamper evidence.

- Secure backup.

Their existence in requirements does not remove the corresponding attack surface.

The following approved architectural controls SHALL NOT be represented as effective controls until implemented and security-verified:

- UsageStatsManager-based baseline foreground detection (AS-019);

- Usage Access permission monitoring;

- baseline detection-health verification;

- baseline lock-interface presentation mechanism (AS-020);

- detection-source selection (AS-021);

- two-tier Trigger Processor integration.

Accessibility remains implemented in the current build, but its role changes under the approved architecture from mandatory detection dependency to optional enhancement. The following states remain distinct:

| **Control** | **Current State** | **Target State** |
|----|----|----|
| Accessibility detection (AS-003/AS-007) | Implemented / current detection mechanism | Optional enhancement |
| Usage Access detection (AS-019) | Not implemented | Mandatory baseline |
| Detection-source selection (AS-021) | Not implemented | Required |
| Baseline health monitoring | Not implemented | Required |
| Accessibility health monitoring | Current monitoring exists | Enhancement health monitoring |
| Common Trigger Processor path | Existing lock architecture | Shared by both detection tiers |

**5.28 Historical Attack-Surface Findings**

The following historical failures demonstrate attack-surface exposure in lifecycle and navigation behavior:

1.  **Self-gate bypass** — returning to App Lock after prior authentication could expose protected functionality without the required re-gating.

2.  **Fast-relaunch bypass** — rapid relaunching of a protected application interacted incorrectly with the lock-screen lifecycle.

3.  **Fast-switch relock defect** — rapid switching away from and back to a protected application did not always produce the required re-lock behavior.

4.  **Plaintext database exposure** — earlier database storage did not provide the current encrypted-at-rest boundary before migration.

5.  **Force-stop availability limitation** — terminating App Lock can interrupt the current Accessibility-based enforcement path.

Finding 5 must be interpreted according to implementation state. For the current delivered build, force-stop can interrupt the Accessibility-based enforcement path. For the approved target architecture, the corresponding threat becomes interruption or failure of the **baseline detection and enforcement path**; loss of the optional Accessibility enhancement alone does not constitute the equivalent failure.

These findings are retained as attack-surface evidence.

They do not constitute proof that the corresponding defects remain present in the current build.

Current implementation status and security verification are determined separately.

**5.29 Attack Surface Completeness Criteria**

The Section 5 inventory is considered structurally complete when:

- All declared Activities are identified.

- All declared Services are identified.

- All declared BroadcastReceivers are identified.

- The absence of application-owned ContentProviders is explicitly recorded.

- Every exported component has an explicit reason for exposure.

- Every security-sensitive unexported component is identified.

- Framework dependencies forming security boundaries are identified.

- Application-private storage is identified.

- Database and encrypted-file surfaces are identified.

- Authentication and session surfaces are identified.

- Lifecycle and process-management surfaces are identified.

- Notification exposure is identified.

- Backup/restore exposure is identified.

- Installation/update exposure is identified.

- Current implementation is distinguished from planned controls.

- Historical attack-surface failures are preserved.

- Attack-surface change triggers are defined.

**5.30 Section 5 Boundary**

Section 5 establishes **where an attacker can interact with or influence App Lock**.

It intentionally does not yet establish:

- Individual threat scenarios.

- Threat severity.

- Attack likelihood.

- Control effectiveness.

- Security-test design.

- Residual risk.

- Final verification status.

Those determinations belong to the subsequent Threat Model sections.

Any future section that identifies a threat must be traceable back to one or more attack surfaces defined here, unless the new threat itself demonstrates that the attack-surface inventory is incomplete.
