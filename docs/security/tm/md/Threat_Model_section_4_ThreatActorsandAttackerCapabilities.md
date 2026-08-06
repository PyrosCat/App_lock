**4. Threat Actors and Attacker Capabilities**

**4.1 Purpose**

This section defines the adversaries App Lock is required to defend against, the capabilities attributed to each adversary, and the limits placed on those capabilities.

The attacker model is intentionally capability-based.

An attacker is not considered in scope merely because an attack is theoretically possible. An attacker class is in scope when the App Lock security model is expected to provide a defined level of protection against that class.

The Threat Model distinguishes between:

- **In-scope attackers** — App Lock must provide the defined security guarantees against these attackers.

- **Best-effort attackers** — App Lock provides mitigation where technically feasible but does not claim an absolute guarantee.

- **Out-of-scope attackers** — the attacker is below or outside the application's defined security boundary.

This distinction must remain explicit throughout the Threat Model.

**4.2 Attacker Classification**

The current attacker model contains the following primary classes:

| **ID** | **Threat Actor** | **Classification** | **Primary Concern** |
|----|----|----|----|
| TA-ATK-001 | Unauthorized person with physical access | In scope | Bypass App Lock while device is unlocked |
| TA-ATK-002 | Malicious ordinary Android application | In scope | Storage, IPC, intent, and interference attacks |
| TA-ATK-003 | ADB/USB attacker on a non-rooted device | In scope, bounded | Offline extraction and application interference |
| TA-ATK-004 | Production-build debugger/instrumentation attacker | In scope | Runtime manipulation and analysis |
| TA-ATK-005 | Malicious or compromised Accessibility Service | Best effort | UI observation and event injection |
| TA-ATK-006 | Rooted-device attacker | Best effort | Circumvention below application trust boundary |
| TA-ATK-007 | Malicious/compromised software dependency | In scope for engineering controls | Supply-chain dependency compromise |
| TA-ATK-008 | Fully compromised/system-level operating system attacker | Out of scope as a guarantee | Defeat of application and platform boundaries |
| TA-ATK-009 | Network attacker | Out of scope for current architecture | No current network security surface |
| TA-ATK-010 | Build-system/signing-key compromise | Out of scope as an application guarantee | Compromise of trusted release infrastructure |

The classifications above are security-model classifications, not statements that the corresponding attack is impossible.

**4.3 TA-ATK-001 — Unauthorized Person with Physical Device Access**

**Classification**

**In scope.**

This is one of the primary App Lock adversaries.

**Attacker Position**

The attacker has physical possession of the Android device while the legitimate App Lock owner is absent.

The device may already be unlocked at the Android operating-system level.

This condition is explicitly within the purpose of App Lock.

**Capabilities**

The attacker may:

- Interact with the Android user interface.

- Launch protected applications.

- Launch App Lock.

- Attempt App Lock authentication.

- Attempt repeated authentication failures.

- Switch between applications.

- Use Back and Recents.

- Reboot the device.

- Modify ordinary Android settings accessible to the device user.

- Attempt to revoke or interfere with App Lock permissions.

- Attempt to uninstall App Lock.

- Install other applications where Android permits it.

- Connect the device to USB.

- Attempt ADB-based interaction where ADB is enabled and authorized.

- Observe visible application behavior.

- Attempt to exploit lifecycle and timing conditions.

- Attempt to interfere with App Lock enforcement mechanisms.

**Security Objective Against This Attacker**

The attacker must not be able to:

- Open a protected application's usable UI without valid App Lock authorization.

- Access Vault content without authentication.

- Access intruder photographs without authentication.

- Reset or change the App Lock credential without knowing the current credential.

- Bypass lockout or failed-attempt protections.

- Disable enforcement in a manner that causes protected applications to become freely accessible.

- Obtain protected plaintext through normal user-accessible device operations.

The Android device being unlocked does not satisfy the App Lock authentication requirement.

**4.4 TA-ATK-002 — Malicious Ordinary Android Application**

**Classification**

**In scope.**

The Threat Model assumes that another installed application may be malicious, compromised, or intentionally designed to attack App Lock.

**Attacker Position**

The malicious application executes within its own Android application sandbox and does not initially possess root or system privileges.

**Capabilities**

The attacker may attempt to:

- Read App Lock private storage.

- Enumerate or infer App Lock files.

- Read publicly accessible application information.

- Send intents to App Lock components.

- Attempt to launch exported components.

- Attempt to launch non-exported components.

- Spoof or manipulate application-level IPC.

- Abuse exported receivers.

- Trigger supported Android framework interactions.

- Attempt to exploit component configuration errors.

- Attempt to observe App Lock UI behavior.

- Attempt to interfere with App Lock lifecycle.

- Attempt to place overlays over App Lock's UI when Android grants the required capability.

- Attempt tapjacking or UI-obscuring attacks.

- Attempt to exploit notification content.

- Attempt to induce unsafe application state transitions.

- Attempt to exploit implementation vulnerabilities or dependencies.

**Explicit Limitation**

The attacker does **not** automatically receive:

- Root privileges.

- System privileges.

- Direct access to another application's private storage.

- Authority to bind to privileged Android framework interfaces unless Android grants it.

- Authority to invoke permission-protected framework operations.

The Threat Model must not silently grant an ordinary malicious application capabilities equivalent to root.

**4.5 TA-ATK-003 — ADB/USB Attacker on a Non-Rooted Device**

**Classification**

**In scope, with bounded capabilities.**

ADB represents a distinct physical-device attack capability because it can interact with the Android system in ways unavailable through ordinary application APIs.

The attacker is assumed to have physical USB access and an ADB configuration that permits the attempted interaction, subject to Android's authorization and security controls.

**Capabilities**

The attacker may attempt to:

- Inspect accessible application/system state.

- Invoke permitted shell commands.

- Start or stop permitted application activities and services.

- Attempt to force-stop App Lock.

- Attempt to manipulate application lifecycle.

- Attempt backup or extraction operations.

- Inspect accessible filesystem locations.

- Reboot the device.

- Attempt to interfere with enforcement availability.

**Important Boundary**

ADB access on a non-rooted device does not automatically provide unrestricted access to App Lock's private storage or Android Keystore.

The Threat Model therefore treats ADB as a meaningful attack capability without equating it to root.

**Security Requirement**

ADB-based interaction must not create a path to:

- Extract the App Lock credential.

- Extract the database passphrase.

- Extract Keystore-protected key material.

- Obtain usable Vault plaintext.

- Obtain usable intruder photographs.

- Bypass App Lock authentication.

- Permanently defeat lockout protections.

Force-stop is treated separately as an **availability attack** because terminating the application can interrupt the active enforcement architecture.

Under the current delivered implementation, force-stop can interrupt the Accessibility-based enforcement path. Under the approved two-tier architecture, force-stop or equivalent process termination can interrupt the baseline detection service, the optional Accessibility enhancement, the watchdog, or other required enforcement components.

The Threat Model SHALL therefore treat interruption of the **baseline enforcement path** as the security-critical condition. Loss of the optional Accessibility enhancement alone is not equivalent to loss of App Lock protection once the two-tier architecture is implemented.

The current architecture cannot guarantee continued enforcement after force-stop, and this limitation remains a residual security concern until the applicable lifecycle and recovery controls are implemented and verified.

**4.6 TA-ATK-004 — Production-Build Debugging and Instrumentation Attacker**

**Classification**

**In scope.**

The Threat Model assumes an attacker may attempt to analyze or instrument a production application build.

**Capabilities**

Depending on the capabilities available on the target device, the attacker may attempt to:

- Attach debugging or instrumentation tooling.

- Inspect application runtime behavior.

- Manipulate application execution.

- Observe authentication flows.

- Attempt to intercept security-sensitive operations.

- Modify runtime state.

- Analyze application binaries.

- Reverse engineer security-sensitive logic.

- Search for cryptographic or authorization weaknesses.

- Exploit debug or test configurations accidentally present in a release artifact.

**Required Security Distinction**

Debug and test functionality must not be treated as a production security control.

A debug build may intentionally permit capabilities required for testing.

The Threat Model therefore distinguishes:

- Development/debug behavior.

- Production behavior.

- Security controls intended to detect or resist production instrumentation.

Debug/tamper protections that are currently specified but not implemented must not be represented as effective mitigations.

**4.7 TA-ATK-005 — Malicious or Compromised Accessibility Service**

**Classification**

**Best effort.**

A peer Accessibility Service represents a special Android attacker because Accessibility can legitimately observe UI information and inject user-interface events. This threat remains relevant even though Accessibility is optional for App Lock, because a peer Accessibility Service may interact with the authentication interface or observe application UI independently of whether App Lock uses Accessibility as its active foreground detector.

**Capabilities**

Where Android permits the service to operate, the attacker may attempt to:

- observe application UI events;

- observe text or accessibility nodes exposed by applications;

- monitor application transitions;

- inject interaction events;

- manipulate authentication UI;

- interfere with user interaction;

- race App Lock's authentication flow;

- exploit assumptions made by the App Lock lock engine.

**Security Limitation**

App Lock cannot claim absolute prevention against a malicious peer Accessibility Service while operating within the Android accessibility model. Detection and warning mechanisms may reduce the risk, but the presence of another privileged Accessibility Service remains a platform-level limitation.

This attacker therefore remains a **best-effort** threat rather than an attacker against which App Lock claims absolute protection.

**Security Significance**

The threat is no longer characterized by the assumption that App Lock itself must rely on Accessibility for enforcement. Instead, the security significance is that Android permits another Accessibility Service to possess capabilities that can affect UI observation and interaction. The optional Accessibility enhancement introduces an additional detection path, but compromise or failure of that enhancement does not by itself defeat the baseline App Lock enforcement architecture.

The resulting risk must remain linked to the Accessibility availability and silent-failure concerns.

**4.8 TA-ATK-006 — Rooted-Device Attacker**

**Classification**

**Best effort.**

Rooted-device compromise is treated differently from ordinary physical or application-level attacks.

**Capabilities**

A rooted attacker may potentially:

- Read application-private storage.

- Modify application files.

- Inspect application processes.

- Manipulate runtime execution.

- Interfere with application lifecycle.

- Attempt to access application secrets.

- Bypass normal application sandbox assumptions.

- Modify or interfere with security state.

- Attempt to disable enforcement.

- Attempt to manipulate Android framework state.

**Security Boundary**

Root access crosses below the normal App Lock application trust boundary.

App Lock therefore does not guarantee confidentiality or integrity against a fully capable rooted attacker.

**Mitigation**

Root detection and configurable root response may provide best-effort defense where implemented.

These mechanisms must not be represented as equivalent to cryptographic protection against root.

If root detection is not implemented and security-verified, the Threat Model must record it as a planned mitigation rather than an active control.

**4.9 TA-ATK-007 — Malicious or Compromised Dependency**

**Classification**

**In scope for engineering security controls.**

A dependency may contain vulnerabilities, malicious behavior, unsafe defaults, or build-time incompatibilities that affect App Lock's security.

**Capabilities**

A compromised dependency may potentially:

- Execute code within the application process.

- Access application data available to its execution context.

- Modify application behavior.

- Interact with security-sensitive APIs.

- Introduce cryptographic weaknesses.

- Undermine authentication or authorization logic.

- Affect build integrity.

- Introduce vulnerabilities into release artifacts.

**Security Boundary**

The dependency executes within the application's process and therefore cannot automatically be treated as an independent sandbox.

Dependency trust is consequently part of the application's software supply-chain security model.

**Required Treatment**

Dependency governance must include appropriate controls such as:

- Dependency auditing.

- Version management.

- Security review.

- Build verification.

- Appropriate dependency restrictions.

- Monitoring for known vulnerabilities.

The exact implementation status of these controls must be determined through the implementation and verification records.

**4.10 TA-ATK-008 — Fully Compromised/System-Level Operating System Attacker**

**Classification**

**Out of scope as a security guarantee.**

This attacker possesses system-level control sufficient to defeat the Android security foundations on which App Lock depends.

**Capabilities**

Such an attacker may potentially:

- Defeat application sandboxing.

- Manipulate App Lock processes.

- Access private storage.

- Interfere with Keystore usage.

- Manipulate framework behavior.

- Modify application execution.

- Disable security mechanisms.

- Control device lifecycle behavior.

- Extract or observe information unavailable to ordinary applications.

**Model Boundary**

This attacker is explicitly below the App Lock trust boundary.

App Lock does not claim to defeat an operating system that no longer enforces the security boundaries upon which App Lock depends.

This classification must not be interpreted as saying that root compromise is harmless.

It means only that the application does not promise an absolute security guarantee against that level of compromise.

**4.11 TA-ATK-009 — Network Attacker**

**Classification**

**Out of scope for the current architecture.**

The core App Lock application is local-only and has no required network security boundary.

A network attacker therefore has no defined remote path into the current App Lock security model.

This classification must be revisited if the application introduces:

- Cloud services.

- Remote authentication.

- Remote synchronization.

- Remote Vault storage.

- Telemetry containing sensitive security information.

- Remote administration.

- Any other security-sensitive network dependency.

A future network feature constitutes a Threat Model change rather than an assumption that the existing model automatically extends to it.

**4.12 TA-ATK-010 — Build-System or Signing-Key Compromise**

**Classification**

**Out of scope as an application-level guarantee.**

An attacker who controls the trusted release build system or application signing keys can potentially produce or distribute a malicious artifact that no runtime security control can reliably distinguish from a legitimately signed release.

**Capabilities**

Such an attacker may potentially:

- Modify source or build inputs.

- Modify dependencies.

- Modify compiled application behavior.

- Produce malicious release artifacts.

- Sign modified artifacts using compromised signing authority.

- Undermine application-level security controls before installation.

**Security Boundary**

This is a build and organizational trust boundary rather than a normal Android runtime attacker boundary.

Build integrity, signing-key protection, dependency governance, and release controls are therefore necessary engineering safeguards, but they must not be represented as runtime App Lock security guarantees.

**4.13 Attacker Capability Matrix**

The following matrix establishes the intended capability boundary.

| **Capability** | **Physical User** | **Malicious App** | **ADB Attacker** | **Peer Accessibility** | **Rooted Attacker** |
|----|---:|---:|---:|---:|---:|
| Physical device access | Yes | No | Yes | No | Yes |
| Android UI interaction | Yes | Indirect | Limited | Indirect | Yes |
| Launch protected apps | Yes | Potentially | Potentially | Indirect | Yes |
| Attempt App Lock authentication | Yes | Limited | Limited | Potentially | Yes |
| Read App Lock private storage directly | No | No | Not assumed | No | Potentially |
| Access Android Keystore directly | No | No | Not assumed | No | Potentially |
| Send ordinary IPC | Yes/indirect | Yes | Via shell where permitted | Framework-mediated | Yes |
| Attempt UI injection | Yes | Potentially | Potentially | Yes | Yes |
| Place UI overlay | Potentially | Potentially | No direct UI capability | Potentially | Yes |
| Force-stop application | User/system dependent | No ordinary authority | Potentially | No | Yes |
| Reboot device | Yes | No ordinary authority | Potentially | No | Yes |
| Modify App Lock private files | No | No | Not assumed | No | Potentially |
| Defeat application sandbox | No | No | No | No | Yes |
| Defeat OS trust boundary | No | No | No | No | Yes |

This matrix defines attacker capabilities at the model level. It does not establish that every capability is available on every Android version or device configuration.

**4.14 Attacker Capability Constraints**

Unless explicitly stated otherwise, the following constraints apply:

1.  An ordinary malicious application does not receive root privileges merely because it is malicious.

2.  A physical attacker does not automatically receive root privileges.

3.  ADB access does not automatically equal unrestricted filesystem access.

4.  A peer Accessibility Service does not automatically equal system privilege.

5.  A rooted attacker is not constrained by the normal application sandbox.

6.  The legitimate owner is not modeled as an attacker merely because they possess full authorization.

7.  An attacker who successfully obtains the current App Lock credential is treated as having crossed the primary authentication boundary.

8.  Security claims must be evaluated against the actual capability level of the attacker, not an assumed universal attacker.

9.  Out-of-scope classification must not be used to hide an architectural weakness that affects an in-scope attacker.

10. Best-effort classification must not be represented as a guaranteed mitigation.

**4.15 Legitimate Owner Boundary**

The legitimate owner is a special security principal.

Knowledge of the current App Lock credential establishes authorization to perform actions permitted by the application's owner model.

The owner may therefore:

- Modify protected-app configuration.

- Access authorized Vault content.

- Modify permitted security settings.

- Change the credential after proving the current credential.

- Manage application state according to the implemented authorization model.

The Threat Model does not attempt to protect App Lock against the legitimate owner's authorized actions.

This means:

App Lock is not a defense against an owner who intentionally discloses, modifies, deletes, or accesses their own protected information.

There is no separate coercion-resistant or hidden-owner security domain in the current model.

**4.16 Credential Compromise Boundary**

Possession of the current App Lock credential represents a critical boundary crossing.

An attacker who obtains the valid credential may be able to authenticate as the authorized owner.

The Threat Model therefore distinguishes:

- **Credential guessing/brute force** — an attack App Lock must defend against.

- **Credential extraction** — an attack App Lock must defend against.

- **Credential reset without authorization** — an attack App Lock must defend against.

- **Credential disclosure by the legitimate owner** — outside the normal authorization threat model.

The absence of a forgotten-PIN recovery mechanism is intentional from a security-boundary perspective: there is no alternate recovery credential that can be abused to bypass the primary credential.

**4.17 Attacker Goals**

Regardless of attacker class, the principal security goals relevant to App Lock are:

**Confidentiality goals**

Prevent unauthorized access to:

- The App Lock PIN.

- Credential verification material.

- Database encryption keys/passphrases.

- Keystore-protected key material.

- Vault payloads.

- Vault metadata and original filenames.

- Intruder photographs.

- Security and intruder audit information.

- Protected-app configuration and associated metadata.

**Integrity goals**

Prevent unauthorized modification of:

- Authentication state.

- Credential configuration.

- Lockout state.

- Protected-app configuration.

- Relock policies.

- Enforcement state.

- Security settings.

- Security audit information.

- Vault metadata and protected data.

**Availability goals**

Maintain the security enforcement mechanisms necessary to prevent protected applications from becoming accessible without App Lock authorization. Under the approved architecture this includes:

- baseline Usage Access detection;

- the selected lock-interface presentation mechanism;

- optional Accessibility detection when enabled;

- detection-source selection;

- the Trigger Processor;

- lock-engine invocation;

- watchdog operation;

- boot re-arm;

- device-admin uninstall protection where enabled.

The baseline detection path is the mandatory enforcement dependency. Accessibility is an optional enhancement and SHALL NOT be treated as the sole availability dependency of the target architecture.

Availability loss becomes a security failure when it causes App Lock to fail open.

**4.18 Security-Relevant Attacker Actions**

The following actions are considered security-relevant regardless of whether they succeed:

- Attempting to bypass App Lock authentication.

- Attempting to replay a previously valid session.

- Attempting to exploit rapid application switching.

- Attempting to exploit lifecycle transitions.

- Attempting to suppress or disable foreground detection.

- Attempting to disable the baseline detection mechanism.

- Attempting to disable the optional Accessibility enhancement.

- Attempting to interfere with Usage Access.

- Attempting to interfere with the lock-interface presentation mechanism.

- Attempting to defeat lockout.

- Attempting to reset or modify the credential without authorization.

- Attempting to access Vault data outside the authorized UI path.

- Attempting to extract database or file encryption keys.

- Attempting to read sensitive data through screenshots or recordings.

- Attempting to obscure or spoof authentication UI.

- Attempting to inject UI events.

- Attempting to disable App Lock services.

- Attempting to uninstall App Lock while protection is enabled.

- Attempting to exploit exported Android components.

- Attempting to exploit dependencies.

- Attempting to obtain sensitive information through notifications or other externally visible channels.

Disabling Accessibility alone SHALL NOT be classified as a complete enforcement bypass under the approved architecture.

These actions become concrete threat records in Section 8.

**4.19 Historical Attacker Capability Considerations**

Previously observed App Lock failures demonstrate that relatively low-complexity attackers can exploit lifecycle and navigation conditions.

Historical failures include:

- Resuming App Lock after authentication without re-entering the self-gate.

- Rapidly relaunching a protected application around the lock-screen lifecycle.

- Rapidly switching away from and back to a protected application without correct re-lock behavior.

These failures demonstrate that the attacker model must not assume an attacker requires sophisticated exploitation tooling.

A user with ordinary physical access can exploit timing, lifecycle, navigation, and state-management defects.

The historical failures are preserved as evidence and are analyzed in detail in Section 13.

**4.20 Attacker Model Rules**

The following rules govern all subsequent threat analysis:

1.  **Do not weaken an attacker merely because the corresponding attack is inconvenient.**

2.  **Do not grant an attacker capabilities that Android would not provide without justification.**

3.  **Do not classify root as an ordinary malicious application capability.**

4.  **Do not treat device unlock as App Lock authorization.**

5.  **Do not treat best-effort mitigation as guaranteed protection.**

6.  **Do not treat planned controls as effective controls.**

7.  **Do not treat regression-tested behavior as security-verified behavior.**

8.  **Do not silently expand or contract attacker scope when analyzing individual threats.**

9.  **Any new capability that materially changes an attacker class requires Threat Model reassessment.**

10. **Any newly discovered attacker path that crosses a previously assumed trust boundary must be recorded and assessed.**

**4.21 Section 4 Completion Criteria**

Section 4 is complete when:

- All explicitly relevant attacker classes are identified.

- Each attacker has an explicit scope classification.

- Capabilities are defined without silently granting excessive privilege.

- Physical-device attacks are represented.

- Malicious application attacks are represented.

- ADB/USB attacks are represented.

- Accessibility-based attacks are represented.

- Root/system compromise is explicitly separated from ordinary attackers.

- Dependency compromise is represented at the appropriate engineering boundary.

- Network attacks are explicitly classified for the current local-only architecture.

- Build/signing compromise is separated from runtime application security.

- Legitimate-owner authority is explicitly defined.

- Attacker goals cover confidentiality, integrity, and availability.

- Historical low-complexity attacks remain represented.

- The attacker model can be directly mapped to the attack surfaces in Section 5.

Section 4 does not yet enumerate individual Android components, exported entry points, storage paths, or detailed attack vectors. Those belong to Section 5, **Attack Surface**.
