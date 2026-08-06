**1. Purpose, Scope, and Objectives**

**1.1 Purpose**

This Threat Model defines the security threats, trust boundaries, attacker capabilities, security objectives, protections, residual risks, and verification requirements applicable to the App Lock system.

The purpose of the Threat Model is to provide a controlled security analysis of the system as it is actually designed and implemented, while maintaining sufficient structure to evaluate planned changes and future security controls throughout the project lifecycle.

The Threat Model exists to:

- Identify what the App Lock system must protect.

- Identify the actors and conditions that may threaten those protections.

- Establish the security boundaries on which the application depends.

- Analyze how an attacker could bypass authentication, authorization, confidentiality, integrity, or enforcement mechanisms.

- Identify controls that prevent, detect, contain, or mitigate those threats.

- Distinguish implemented controls from controls that have been functionally or security verified.

- Preserve known historical security failures and their resulting lessons.

- Identify residual and accepted security risks.

- Provide traceability between threats, controls, requirements, tests, evidence, and architectural decisions.

- Establish conditions under which the Threat Model must be reassessed.

The Threat Model is a controlled engineering artifact. New information may change its substantive security analysis, but it must not silently change the approved document architecture.

**1.2 System Scope**

The Threat Model applies to the App Lock Android application and the security mechanisms through which the application provides its two primary security functions:

1.  **Protected-application access control** — preventing access to applications designated by the user as protected unless the App Lock authorization requirements have been satisfied.

2.  **Confidential application-managed storage** — protecting the Vault and associated security-sensitive information maintained by App Lock itself, including vault payloads and intruder-capture data.

The scope includes the Android application components, local storage, authentication mechanisms, enforcement mechanisms, cryptographic protections, lifecycle behavior, and Android platform interfaces that materially affect these security functions.

The security-relevant system scope includes, but is not limited to:

- Authentication and credential handling.

- Authentication sessions and authorization state.

- Protected-application enforcement.

- Foreground-application detection.

- Lock-screen behavior.

- Relock policies.

- Vault access control.

- Vault storage and associated metadata.

- Intruder capture and associated data.

- Encrypted application storage.

- Database encryption and database key management.

- Android Keystore integration.

- Application lifecycle and process behavior.

- Protection watchdog behavior.

- Boot-time re-establishment of protection.

- Accessibility-service operation.

- Device-admin uninstall protection.

- Inter-component communication.

- Android permissions and exported components.

- Backup and restore behavior.

- Database migration and recovery behavior.

- Security-relevant dependencies and build integrity where they affect the application security boundary.

The Threat Model addresses these elements only to the extent necessary to analyze security properties, threats, controls, risks, and verification.

It does not replace the detailed architecture, software design, implementation, testing, deployment, or operational documentation maintained elsewhere in the project.

**1.3 Security Scope**

The primary security boundary of App Lock is the boundary between an unauthenticated actor and an authenticated App Lock session.

The Threat Model therefore covers attacks that could cause the application to grant access without satisfying the required authorization conditions.

The security scope also includes attacks that could compromise the confidentiality or integrity of App Lock-managed information without obtaining legitimate authorization.

In particular, the Threat Model addresses attempts to:

- Bypass the App Lock authentication requirement.

- Obtain access to a protected application without a valid authorization session.

- Access the Vault without authorization.

- Obtain vault metadata or payloads without authorization.

- Obtain intruder photographs without authorization.

- Recover or bypass the user's credential.

- Modify security-critical configuration without authorization.

- Bypass or reset authentication lockout controls.

- Disable or circumvent enforcement mechanisms.

- Prevent protection from being re-established after lifecycle events.

- Extract sensitive information from application storage.

- Manipulate security-relevant audit information.

- Exploit exposed Android components or IPC paths.

- Abuse accessibility, overlays, lifecycle behavior, or other Android platform mechanisms to circumvent enforcement.

- Exploit weaknesses in key handling or encrypted storage.

- Exploit security-relevant implementation or dependency weaknesses.

The Threat Model treats continuous enforcement as a security concern where loss of enforcement could allow protected applications to become accessible without App Lock authorization.

**1.4 Security Objectives**

The App Lock security objectives are:

**SO-01 — Credential Confidentiality**

The App Lock credential must remain confidential.

The system must not provide an attacker with the plaintext PIN, an equivalent credential, or an unauthorized mechanism for resetting or changing the credential.

**SO-02 — Credential Integrity**

The credential configuration must not be changed without satisfying the required authorization condition.

In particular, knowledge of device possession or Android device-unlock state must not by itself authorize a PIN change or reset.

**SO-03 — Protected-Application Authorization**

A protected application must not become accessible through the App Lock enforcement path without a valid App Lock authorization session.

Android device unlock must not substitute for App Lock authorization.

**SO-04 — Vault Confidentiality**

Vault payloads and their associated sensitive metadata must remain confidential against unauthorized access.

**SO-05 — Intruder-Data Confidentiality**

Intruder photographs and associated information must remain confidential against unauthorized access.

**SO-06 — Security-State Integrity**

Security-critical state must not be modified without authorization.

This includes, where applicable:

- Protected-application configuration.

- Enforcement policy.

- Lockout state.

- Security settings.

- Security-relevant authorization state.

- Audit information.

**SO-07 — Cryptographic Key Protection**

Cryptographic key material and secrets required to protect App Lock data must remain protected by their defined trust boundaries.

Unauthorized actors must not obtain usable key material or equivalent means of decrypting protected information.

**SO-08 — Enforcement Availability**

The security enforcement mechanism must remain operational to the extent supported by the Android platform and the application's defined security guarantees.

Unexpected loss of enforcement must not be treated merely as an ordinary availability problem when that loss permits protected applications to become accessible without authorization.

**SO-09 — Security-State Persistence**

Security-critical state that is required to survive application restart or device reboot must not be reset merely by terminating or restarting the application.

In particular, security mechanisms such as authentication lockout must not be bypassable through process restart.

**SO-10 — Security Verification**

Security claims must be supported by appropriate evidence.

An implementation must not be considered security-verified solely because the corresponding functionality exists or passes ordinary functional regression testing.

**1.5 Security Guarantee Boundary**

App Lock is designed to maintain an independent authorization boundary after the Android device itself has been unlocked.

The legitimate Android device-unlock state does not constitute App Lock authorization.

Once the device is unlocked, an unauthorized person possessing the device must still be unable to:

- Access protected applications through the App Lock enforcement boundary.

- Access the App Lock Vault without satisfying App Lock authorization.

- Obtain protected App Lock data through the application's intended interfaces.

- Modify security-critical App Lock state without authorization.

App Lock therefore operates as an additional application-level security boundary above the Android device-unlock boundary.

The security guarantee is based on the trust assumptions and attacker capabilities defined elsewhere in this Threat Model. It does not extend to a fully compromised operating system or an attacker that has obtained control below the application's trust boundary.

**1.6 Security Analysis Scope Across the Project Lifecycle**

The Threat Model is applicable throughout the project lifecycle rather than only at initial implementation.

Security analysis must account for the distinction between:

- Current implementation.

- Planned implementation.

- Implemented but not security-verified functionality.

- Security-verified functionality.

- Accepted residual risk.

- Out-of-scope threats and limitations.

A security control that is planned for a later phase must not be represented as an effective current mitigation.

Similarly, an existing implementation that has only passed functional or regression testing must not automatically be represented as security-verified.

The Threat Model must therefore describe both the current security state and relevant planned security changes without conflating the two.

**1.7 Relationship to Other Project Artifacts**

The Threat Model is part of the project's controlled engineering documentation.

It does not supersede the requirements, architecture, design, implementation, testing, traceability, or decision-record artifacts.

The primary relationships are:

- **SRS/NFR:** define required capabilities and quality attributes that establish security requirements.

- **TAS:** defines the system architecture within which security controls operate.

- **SDS/DDS:** define detailed implementation and design decisions relevant to security.

- **Test Specification:** defines testing and verification processes and evidence requirements.

- **RTM:** provides requirements-to-implementation and verification traceability.

- **ADR:** records approved architectural decisions and resolutions of architectural conflicts.

- **Implementation Strategy:** establishes project phases and associated deliverables.

- **Secure Coding Standard:** defines required implementation practices once established.

The Threat Model identifies security implications and inconsistencies across these artifacts but does not silently override them.

Where a genuine architectural conflict exists, the conflict must be identified and resolved through the project's approved decision and governance process.

**1.8 Threat Model Boundaries**

The Threat Model distinguishes between three broad categories of security exposure:

**In-Scope Security Threats**

Threats against which App Lock is required to provide a defined defense, including attacks by ordinary malicious applications, physical attackers operating an unlocked device, bounded non-root adb/USB attacks, and applicable production-build instrumentation or tampering attempts.

**Best-Effort Security Threats**

Threats for which App Lock provides mitigation where technically feasible but cannot provide an absolute guarantee because the attacker possesses capabilities below or substantially beyond the application's normal trust assumptions.

Examples include rooted devices and malicious peer Accessibility Services.

**Out-of-Scope Security Guarantees**

Threats for which App Lock does not claim a complete application-level security guarantee.

The principal example is a fully compromised or system-privileged Android operating system. An attacker controlling the operating system is below the application's sandbox and cryptographic trust boundary and can potentially defeat protections on which App Lock depends.

Other explicit non-goals are governed by the project's defined security assumptions and scope.

**1.9 Objectives of the Threat Modeling Process**

The Threat Model itself must provide an enduring process for security analysis.

The process objectives are to:

1.  Maintain an accurate representation of the system's security boundaries.

2.  Identify new threats when requirements, architecture, implementation, dependencies, or platform behavior change.

3.  Maintain traceability from threats to mitigations and verification evidence.

4.  Prevent planned security controls from being mistaken for existing protections.

5.  Preserve historical security findings even after remediation.

6.  Make residual risk explicit rather than allowing unresolved exposure to disappear from project documentation.

7.  Require reassessment when security-relevant changes occur.

8.  Ensure that security verification is supported by appropriate evidence.

9.  Prevent undocumented assumptions from becoming implicit security guarantees.

10. Preserve the approved Threat Model structure throughout the project lifecycle.

**1.10 Section 1 Completion Criteria**

Section 1 is complete when the following are established:

- The purpose of the Threat Model is defined.

- The system and security scope are defined.

- The principal security objectives are established.

- The independent App Lock authorization boundary is established.

- The lifecycle role of the Threat Model is established.

- Its relationship to other project artifacts is defined.

- In-scope, best-effort, and out-of-scope security guarantees are distinguished.

- The Threat Model's continuing security-analysis objectives are established.

Detailed assets, trust boundaries, attacker capabilities, attack surfaces, cryptographic architecture, threats, controls, risks, verification, historical findings, residual risks, and governance are intentionally developed in their respective locked sections and are not duplicated here.
