**3. Threat Model Assumptions, Trust Boundaries, and Non-Goals**

**3.1 Purpose**

This section establishes the assumptions on which the App Lock security model depends, identifies the security trust boundaries that separate different levels of authority, and defines security guarantees that App Lock does not claim.

These assumptions are foundational to the Threat Model.

An assumption is not a security control. An assumption identifies a condition that the security architecture relies upon. Where an assumption is violated, the applicable security guarantee may no longer hold.

The Threat Model must not silently convert an assumption into a guarantee, nor treat an out-of-scope condition as evidence that a security control is effective.

**3.2 Foundational Security Model**

App Lock provides an application-level security boundary that operates independently of the Android device-unlock state.

The Android operating system may consider the device unlocked while App Lock still considers the user unauthenticated.

The security model therefore contains at least two distinct authorization boundaries:

Android Device Security

│

│ Device unlocked

▼

┌──────────────────────────────┐

│ Android OS │

│ │

│ App Lock Security Boundary │

│ │ │

│ ▼ │

│ App Lock Authentication │

│ │ │

│ ▼ │

│ Authorized App Lock │

│ Session / Owner State │

└──────────────────────────────┘

Knowledge of, or access to, the Android device does not by itself constitute App Lock authorization.

The security model therefore assumes that an attacker may possess an OS-unlocked device while still being unauthorized by App Lock.

**3.3 Android Platform Trust**

App Lock relies on security properties supplied by the Android platform.

The following platform mechanisms are treated as trusted foundations of the application security model:

- Android application sandboxing.

- Android Keystore.

- Android Package Manager.

- Android component and permission enforcement.

- Device-admin framework.

- Credential/device authentication mechanisms.

- BiometricPrompt when biometric authentication is used.

These mechanisms are not implemented by App Lock and therefore cannot be independently guaranteed by the application.

The Threat Model consequently treats compromise of these foundations as a boundary condition rather than assuming that App Lock can cryptographically compensate for a platform that no longer enforces its own security boundaries.

**3.4 Android Keystore Trust Assumption**

Android Keystore is the root of cryptographic trust for the current App Lock storage architecture.

The application assumes that:

1.  Non-exportable Keystore key material remains unavailable to unauthorized applications.

2.  Keystore-enforced key isolation operates according to the Android platform security model.

3.  Hardware-backed protection is provided where supported by the device.

4.  Applications cannot directly extract usable plaintext key material protected by the Keystore.

5.  The Keystore boundary remains trustworthy while the operating system itself remains within the application's defined trust model.

App Lock uses this trust relationship to protect application secrets including encrypted preference stores and cryptographic material used by the encrypted database and file storage.

A compromise of the Android Keystore trust boundary is therefore treated as a foundational security failure rather than an ordinary application-level vulnerability.

**3.5 Application Sandbox Trust Assumption**

App Lock relies on the Android application sandbox to isolate its private application storage from other ordinary applications.

The application assumes that an ordinary application cannot directly read, modify, or enumerate App Lock's private files without an additional platform or application-level compromise.

Encryption provides an additional protection layer but does not eliminate the importance of sandbox isolation.

The Threat Model therefore considers both:

- Unauthorized access to the application sandbox.

- Attempts to bypass encryption after obtaining storage access.

The two protections are complementary rather than interchangeable.

**3.6 Package Manager Trust Assumption**

The Android Package Manager is trusted to maintain the application identity and component installation boundaries on which App Lock relies.

This includes trust in:

- Package identity.

- Component registration.

- Exported-component enforcement.

- Application installation and removal mechanisms.

- Application permissions.

- Application ownership relationships used by Android framework services.

App Lock does not independently establish a second package-management trust system.

An attacker who has obtained system-level authority capable of defeating Package Manager enforcement is therefore below the application's normal trust boundary.

**3.7 Device-Admin Trust Assumption**

Where the user enables the application's device-admin uninstall protection, App Lock relies on Android's Device Admin framework to enforce the associated administrative relationship.

The application assumes that:

- The framework correctly identifies the registered administrator.

- Administrative callbacks are delivered according to Android's security model.

- Ordinary applications cannot invoke privileged device-admin operations.

- The device-admin boundary remains protected while the operating system is trusted.

Device Admin is an enforcement dependency, not an independent cryptographic boundary.

**3.8 Authentication Trust Assumptions**

App Lock treats knowledge of the current App Lock credential as the primary authorization authority for the application owner.

The security model assumes:

- The legitimate owner is authorized to modify App Lock configuration after successful authentication.

- The current PIN is not known to unauthorized users.

- PIN changes require successful proof of the current credential.

- There is no separate recovery credential.

- There is no forgotten-PIN bypass.

- There is no second coercion or duress credential.

- There is no multi-user or hidden-profile authorization model in the current version.

Biometric authentication, where enabled, relies on Android's BiometricPrompt and the platform's associated authentication guarantees.

App Lock does not claim to distinguish between the legitimate owner and another person who successfully satisfies the application's configured authentication mechanism.

**3.9 Credential Authorization Boundary**

The App Lock credential establishes the primary application authorization boundary.

The following distinction is mandatory:

Possession of the device is not equivalent to authorization to App Lock.

A person may possess an unlocked Android device while remaining unauthorized to:

- Open protected applications through the App Lock gate.

- Access the Vault.

- Access intruder photographs.

- Modify security policies.

- Change the App Lock credential.

Conversely, successful App Lock authentication represents authorization to perform the actions permitted to the legitimate owner by the current application design.

This is an authorization model based on the current credential rather than on physical possession.

**3.10 Cryptographic Trust Boundary**

The cryptographic architecture establishes a boundary between protected key material and ordinary application execution.

The current model uses a Keystore-backed master key as the root of trust.

The database passphrase and vault encryption keys are not derived from the App Lock PIN.

Consequently, the security properties of the PIN and the security properties of encrypted data are related but not cryptographically equivalent.

The Threat Model must not assume:

"Knowing the PIN is required to decrypt the Vault."

That is not the current architecture.

Instead:

- The PIN protects the App Lock authentication boundary.

- The Keystore protects cryptographic key material.

- SQLCipher protects the database.

- Encrypted file storage protects Vault payloads and intruder photographs.

- The application authorization boundary controls normal runtime access to the Vault.

This distinction is security-critical and must remain consistent throughout the Threat Model.

**3.11 Accessibility Trust Boundary**

App Lock relies on Android's Accessibility framework to detect foreground application transitions used by the protected-application enforcement mechanism.

The Accessibility framework is therefore both:

- A trusted platform dependency.

- A known-fragility boundary.

The application assumes that an enabled Accessibility Service can receive the events required for foreground-app detection under supported platform conditions.

However, the Threat Model explicitly recognizes that:

- The service can become unbound.

- Accessibility permission can be revoked.

- Android restrictions can prevent or complicate granting the service.

- OEM behavior can interfere with service persistence.

- A service may appear enabled while failing to provide the expected events.

- Another Accessibility Service may observe UI activity and inject events.

The Accessibility framework is therefore not treated as an unconditional security guarantee.

Its reliability and security limitations are analyzed as threats and risks in later sections.

**3.12 Boot and Lifecycle Trust Boundary**

App Lock relies on Android lifecycle and boot mechanisms to restore security enforcement after system restart and application lifecycle events.

The application assumes that supported Android lifecycle mechanisms will provide the necessary opportunities for:

- Boot-time re-arm.

- Watchdog startup or restart.

- Accessibility-service restoration where supported.

- Application process recovery where supported.

These mechanisms are not guaranteed to survive every OEM or operating-system intervention.

The Threat Model therefore distinguishes:

- Expected Android lifecycle behavior.

- Supported application recovery.

- Platform-dependent behavior.

- Conditions under which App Lock cannot guarantee continued enforcement.

Loss of enforcement is security-relevant when it permits protected applications to become accessible without App Lock authorization.

**3.13 Application Authorization Trust Boundary**

The primary application authorization boundary is:

Unauthenticated

│

│ Valid App Lock authentication

▼

Authenticated App Lock Session

│

├── Protected application authorization

│

└── App Lock-managed data authorization

The boundary is enforced through the application's authentication and session mechanisms.

An authenticated session is:

- In-memory.

- Associated with a specific protected application.

- Governed by the configured relock policy.

- Invalidated by applicable session-ending events.

- Not persisted across reboot.

- Not shared automatically between protected applications.

A device reboot therefore removes App Lock authentication sessions even though persistent cryptographic material and protected data remain available to the application after restart.

The detailed session threat analysis is reserved for Section 8.

**3.14 Storage Trust Boundary**

App Lock's private storage represents a boundary between application-controlled data and other ordinary applications.

The security model uses multiple layers:

Other Applications

│

│ Android Sandbox

▼

App Lock Private Storage

│

├── SQLCipher Database

│

└── Encrypted File Storage

│

▼

Keystore-Protected Keys

The application therefore does not rely solely on filesystem permissions.

An attacker obtaining unauthorized raw storage access is expected to encounter encrypted database and file representations rather than usable plaintext, provided the underlying cryptographic trust boundary remains intact.

**3.15 Inter-Process Communication Trust Boundary**

Android component communication represents a boundary between App Lock and external applications or Android framework services.

The security model distinguishes:

- Components intentionally exposed to Android framework mechanisms.

- Components intentionally inaccessible to other applications.

- Permission-protected framework entry points.

- Unexported application-private components.

App Lock defines no custom application permissions.

Security therefore relies on the combination of Android component export rules and framework permission mechanisms.

The detailed component inventory and attack surface are established in Section 5.

**3.16 Root and System-Compromise Boundary**

A fully rooted or system-compromised Android device is below the application's normal trust boundary.

App Lock does not claim to provide absolute confidentiality or integrity against an attacker with root or system privileges.

Such an attacker may potentially:

- Access application-private storage.

- Interfere with application execution.

- Manipulate application processes.

- Interact directly with protected system interfaces.

- Defeat application-level enforcement.

- Attempt to compromise or bypass the platform mechanisms on which App Lock depends.

Root detection and configurable responses may provide best-effort mitigation where implemented, but they do not change the fundamental trust boundary.

Therefore:

App Lock provides no guaranteed application-level security against a fully compromised operating system.

Root detection must not be documented as equivalent to preventing root compromise.

**3.17 Physical Device Trust Boundary**

The Threat Model assumes that an attacker may obtain physical possession of the device while the legitimate owner is absent and the Android device is already unlocked.

The application therefore does not treat physical possession as proof of authorization.

The security model is specifically intended to continue protecting App Lock-managed assets and protected applications under this condition.

The attacker may attempt actions available through the ordinary device interface and other bounded physical-device capabilities defined by the attacker model.

Detailed attacker capabilities are established in Section 4.

**3.18 Backup and Restore Trust Boundary**

The current application does not provide an application-level backup or cross-device restoration mechanism for protected App Lock data.

Backup extraction is disabled through the current application configuration.

The security model therefore does not rely on a trusted backup provider to protect App Lock data.

This produces two deliberate properties:

- There is no supported backup/restore path that can be used as an App Lock authentication bypass.

- There is also no supported backup-based recovery mechanism for protected data.

Any future backup or migration capability must be threat-modeled independently before it is treated as part of the trusted architecture.

**3.19 Network Trust Boundary**

The current App Lock architecture is local-only.

There is no application network service required for the core App Lock security model.

Accordingly:

- Network attackers are not part of the core Threat Model guarantee.

- There is no current remote authentication service.

- There is no cloud key-management service.

- There is no remote Vault service.

- Transport security requirements associated with a future cloud architecture do not constitute current mitigations.

If a network or cloud feature is introduced, it will create a new security boundary and require Threat Model reassessment.

**3.20 Build and Supply-Chain Trust Boundary**

The application depends on its build environment, dependencies, source code, signing process, and release artifacts.

Dependency compromise and security-relevant dependency failures are within the engineering security analysis.

However, compromise of the CI/build infrastructure or application signing keys is outside the application's direct security guarantee.

Such compromise must be addressed through engineering and organizational controls rather than represented as an Android runtime security guarantee.

A compromised signing authority can invalidate assumptions about the integrity of the application artifact itself.

**3.21 Explicit Non-Goals**

The following are explicit security non-goals or limitations of the current App Lock security model:

**NG-001 — Full protection against a compromised operating system**

App Lock does not guarantee confidentiality or integrity against root or system-level compromise.

**NG-002 — Multi-user security separation**

The current version does not provide independent security domains for multiple users.

**NG-003 — Duress or hidden-profile protection**

The current version does not provide a coercion PIN, hidden profile, or equivalent deniable security mode.

Emergency lock, where implemented, does not constitute a hidden-profile security boundary.

**NG-004 — Forgotten-PIN recovery**

There is no security-preserving forgotten-PIN recovery mechanism.

Clearing application data is destructive and does not provide access to previously protected Vault data.

**NG-005 — Network security for a nonexistent network service**

Network authentication, transport security, and remote service compromise are not current App Lock runtime guarantees because the core application is local-only.

**NG-006 — Absolute Accessibility availability**

App Lock cannot guarantee continuous Accessibility Service operation against all Android, OEM, administrative, or system-level interventions.

**NG-007 — Absolute protection against peer Accessibility Services**

A malicious Accessibility Service operating within the Android platform's permitted security model may observe or inject UI interactions. App Lock may provide detection or mitigation but does not claim absolute prevention.

**NG-008 — Absolute protection against root**

Root detection or response mechanisms do not establish a guarantee against an attacker who controls the operating system.

**NG-009 — Cross-device recovery**

The current application does not provide a trusted cross-device restoration mechanism for the protected Vault or security state.

**NG-010 — Protection against compromised build/signing infrastructure**

Application-level security controls do not guarantee integrity against an attacker controlling the trusted build or signing infrastructure.

**3.22 Accepted Foundational Assumptions**

The following assumptions are considered foundational to the current Threat Model:

| **ID** | **Assumption** | **Security Dependency** |
|----|----|----|
| TA-001 | Android sandboxing functions according to the platform security model. | Storage isolation |
| TA-002 | Android Keystore protects non-exportable key material according to the platform model. | Cryptographic root of trust |
| TA-003 | Package Manager maintains application identity and component boundaries. | Application integrity |
| TA-004 | Device Admin enforces its privileged framework boundary. | Uninstall protection |
| TA-005 | Android authentication and BiometricPrompt provide their defined platform guarantees. | Authentication |
| TA-006 | Accessibility operates sufficiently for supported foreground detection. | Enforcement |
| TA-007 | Supported Android boot/lifecycle mechanisms provide expected recovery opportunities. | Persistence |
| TA-008 | The legitimate owner who knows the current App Lock credential is authorized. | Authorization |
| TA-009 | The current architecture has no network dependency for core security. | Local-only boundary |
| TA-010 | Backup extraction remains disabled unless a new security-reviewed mechanism is introduced. | Data protection |
| TA-011 | A fully compromised OS is below the App Lock trust boundary. | Security guarantee |
| TA-012 | No forgotten-PIN recovery exists in the current security model. | Credential integrity |

These assumptions are not permanent truths. A change to any foundational assumption requires Threat Model reassessment.

**3.23 Trust-Boundary Change Rule**

Any change that moves a component, asset, dependency, or security mechanism across a trust boundary requires security reassessment.

Examples include:

- Introducing a cloud service.

- Adding remote authentication.

- Adding a new exported component.

- Changing the credential architecture.

- Deriving encryption keys from a new source.

- Changing the Keystore configuration.

- Adding a backup provider.

- Changing the Accessibility enforcement mechanism.

- Introducing a new privileged Android capability.

- Changing the application's process or storage architecture.

- Introducing a new third-party service that handles sensitive data.

A trust-boundary change must not be treated as an ordinary implementation detail.

**3.24 Section 3 Completion Criteria**

Section 3 is complete when:

- Foundational Android trust assumptions are explicitly defined.

- The Android device-unlock boundary is distinguished from App Lock authorization.

- The application sandbox trust boundary is defined.

- The Keystore trust boundary is defined.

- Authentication and authorization assumptions are defined.

- Accessibility and lifecycle dependencies are explicitly acknowledged.

- Storage and IPC boundaries are established at the appropriate level.

- Root/system compromise is explicitly placed below the application's guarantee boundary.

- Backup, network, and build/supply-chain boundaries are defined.

- Explicit non-goals are documented.

- Foundational assumptions have stable identifiers.

- Changes to trust boundaries are recognized as Threat Model reassessment triggers.

Detailed attacker capabilities are intentionally deferred to Section 4. Detailed Android component exposure and attack surfaces are deferred to Section 5. Detailed threat scenarios and control effectiveness are deferred to Sections 8–12.
