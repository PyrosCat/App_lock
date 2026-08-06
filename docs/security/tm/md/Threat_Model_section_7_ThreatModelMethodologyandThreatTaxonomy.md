**7. Threat Model Methodology and Threat Taxonomy**

**7.1 Purpose**

This section defines the methodology used to identify, classify, analyze, and track security threats against App Lock.

It establishes the rules by which threats are:

- Identified.

- Scoped.

- Classified.

- Related to assets.

- Related to trust boundaries.

- Rated for risk.

- Mapped to existing controls.

- Distinguished from planned controls.

- Evaluated for residual risk.

- Converted into security-verification requirements.

This section does not itself constitute the complete threat catalog.

Individual threats are derived using this methodology and the authoritative architecture established in Section 6.

**7.2 Threat Model Baseline**

The Threat Model is based on the approved project baseline.

The baseline consists of:

1.  The approved requirements.

2.  The approved architecture.

3.  The as-built security architecture.

4.  The documented implementation state.

5.  Historical security failures.

6.  Existing regression evidence.

7.  Known platform limitations.

8.  Approved project assumptions.

9.  Approved Architecture Decision Records.

10. The project's traceability and change-control rules.

The Threat Model must model the **system that actually exists**, while clearly distinguishing:

- Implemented controls.

- Implemented but security-unverified controls.

- Planned controls.

- Accepted platform limitations.

- Explicit non-goals.

A requirement existing in the specification does not establish that its control is currently effective.

**7.3 Threat Modeling Objective**

The primary objective is to determine whether an attacker can cause a violation of one or more security properties.

The fundamental security question is:

Can an attacker cross a protected security boundary without satisfying the authorization or trust conditions required by that boundary?

Secondary questions address:

- Confidentiality.

- Integrity.

- Availability of enforcement.

- Authentication.

- Authorization.

- Accountability.

- Cryptographic protection.

- Application integrity.

- Recovery behavior.

- Platform interaction.

**7.4 Threat Modeling Unit**

Each threat must describe a concrete security-relevant scenario.

A threat is not merely a technology or component.

For example:

**Insufficient:**

Accessibility service can stop.

**Threat-oriented:**

An attacker causes the foreground-detection mechanism to stop or become ineffective, causing a protected application to become accessible without App Lock authentication.

The second formulation identifies:

- The attacker action.

- The affected security mechanism.

- The resulting condition.

- The security consequence.

**7.5 Threat Record Structure**

Each individual threat should contain, at minimum:

| **Field** | **Purpose** |
|----|----|
| Threat ID | Stable identifier |
| Title | Concise threat description |
| Asset | Asset placed at risk |
| Security Property | Confidentiality, integrity, availability, authentication, authorization, or accountability |
| Attacker | Applicable adversary |
| Preconditions | Conditions required for exploitation |
| Attack Surface | Entry point or exposed mechanism |
| Trust Boundary | Boundary being attacked or crossed |
| Attack Scenario | Concrete exploitation sequence |
| Security Impact | Result if successful |
| Existing Controls | Controls currently implemented |
| Control Status | Verified, implemented-unverified, planned, accepted, or not applicable |
| Residual Risk | Remaining exposure |
| Likelihood | Risk likelihood |
| Impact | Risk impact |
| Risk Rating | Combined risk classification |
| Requirements | Related requirements |
| Architecture | Related architectural elements |
| Security Tests | Required verification |
| Evidence | Verification evidence |
| Historical Evidence | Relevant prior failures or tests |
| Owner | Responsible engineering authority |
| Review Trigger | Conditions requiring reassessment |
| Status | Current lifecycle state |

No threat should be considered fully analyzed if the attack scenario or resulting security consequence is undefined.

**7.6 Threat Identification Sources**

Threats must be derived from multiple sources rather than from a single generic checklist.

The primary sources are:

**7.6.1 Attack Surface**

Section 5 identifies externally reachable components, platform interfaces, storage surfaces, and runtime interfaces.

Each security-significant attack surface must be evaluated for abuse.

**7.6.2 Trust Boundaries**

Section 6 identifies boundaries that an attacker may attempt to cross.

Every security boundary must have corresponding threat analysis.

**7.6.3 Security Invariants**

Section 6 defines architectural invariants.

Each invariant must be evaluated for ways an attacker could cause its violation.

**7.6.4 Assets**

Section 4 identifies assets whose confidentiality, integrity, availability, or authorization properties matter.

Threats must identify which asset is affected.

**7.6.5 Historical Failures**

Known failures must be preserved as explicit threat scenarios.

A historical defect demonstrates that an attack path is plausible and must not be discarded merely because the defect has subsequently been fixed.

**7.6.6 Platform Limitations**

Known Android platform limitations must be modeled where they affect security enforcement.

**7.6.7 Requirements**

Security requirements provide expected security behavior.

Where a requirement establishes a security property, threats must be evaluated against the possibility of violating that property.

**7.7 Attacker Classes**

The baseline defines the following attacker classes.

**A-001 — Ordinary Malicious Application**

An independently installed Android application that does not possess root or system privileges.

Capabilities include attempting to:

- Access App Lock through exported components.

- Send malicious intents.

- Interact with exposed interfaces.

- Abuse Android permissions it legitimately possesses.

- Draw overlays where permitted.

- Observe accessible UI information where Android permits it.

- Attempt to influence application lifecycle behavior.

The Android sandbox is assumed to prevent direct access to App Lock private storage.

**A-002 — Physical Attacker With Unlocked Device**

An attacker who possesses the device while the Android operating system is already unlocked.

The attacker may:

- Launch applications.

- Open protected applications.

- Attempt App Lock authentication.

- Change accessible device settings.

- Reboot the device.

- Connect USB.

- Use adb where available.

- Install applications.

- Attempt to revoke App Lock permissions.

- Attempt to uninstall App Lock.

- Attempt to interfere with enforcement.

This is a primary in-scope attacker.

The Threat Model must not assume that Android device unlock implies trust in the person currently holding the device.

**A-003 — adb/USB Attacker**

An attacker with physical USB access and adb capability against a non-rooted device.

The attacker may attempt:

- Application lifecycle manipulation.

- Force-stop.

- Package interaction.

- Settings manipulation.

- Debug-oriented attacks permitted by the device state.

The baseline does not treat adb access as equivalent to unrestricted root access.

The Threat Model must therefore distinguish adb capabilities from root/system compromise.

**A-004 — Production Debug/Instrumentation Attacker**

An attacker attempting to attach debugging or instrumentation capabilities to the production application.

The intended future defenses include debug and tamper detection.

Those defenses are currently not treated as effective controls.

**A-005 — Malicious Accessibility Peer**

An attacker controlling another Accessibility Service.

This attacker may have capabilities that exceed those of an ordinary application because Android intentionally grants Accessibility Services broad UI-observation and interaction capabilities.

The baseline treats this threat as **best effort** rather than guaranteeing complete prevention.

**A-006 — Root/System Attacker**

An attacker with root or equivalent system privileges.

This attacker is below the application's trusted security boundary.

App Lock therefore does not provide a guarantee against this attacker.

Threats involving this attacker may still be modeled for:

- Detection.

- Graceful degradation.

- Data exposure consequences.

- Defense-in-depth.

They must not be represented as ordinary application-level bypasses that App Lock is expected to cryptographically prevent.

**A-007 — Malicious Dependency**

A dependency that introduces malicious or compromised behavior into the application build or runtime.

This threat class is addressed through engineering controls including:

- Dependency governance.

- Dependency scanning.

- Version control.

- Build verification.

- Release-build testing.

It remains distinct from a full CI/signing-key compromise.

**A-008 — Supply-Chain/Signing Infrastructure Compromise**

An attacker who controls the project's build infrastructure, signing keys, or equivalent release authority.

This is outside the application's security guarantee.

The threat may be documented as a project-level assumption or non-goal, but App Lock's runtime security architecture must not claim to defend against an attacker who can legitimately produce and sign an arbitrary replacement application.

**A-009 — Network Attacker**

Network-based attackers are outside the current application threat boundary because App Lock is local-only and has no current network security surface.

Transport-security requirements associated with future cloud functionality are therefore not current mitigations.

If a network feature is introduced, the Threat Model must be reassessed.

**7.8 Attacker Capability Model**

Threat analysis must explicitly state the capabilities assumed for the attacker.

The following distinctions must be preserved:

Ordinary App

│

▼

Physical + Unlocked Device

│

▼

adb / USB

│

▼

Peer Accessibility Service

│

▼

Root / System

Higher capability does not automatically imply that every lower-level capability is available in the same manner.

Threat records must identify the minimum capability required for exploitation.

This prevents over-rating threats by assuming root-level powers for an ordinary application attacker.

**7.9 Security Property Categories**

Every threat must identify the principal security property it threatens.

**7.9.1 Confidentiality**

Protection against unauthorized disclosure.

Examples include:

- PIN recovery.

- Vault payload disclosure.

- Intruder-photo disclosure.

- Database disclosure.

- Protected-app list disclosure.

- Audit-log disclosure.

- Key-material disclosure.

**7.9.2 Integrity**

Protection against unauthorized modification.

Examples include:

- Protected-app policy modification.

- Lockout-state modification.

- Credential modification.

- Vault-index modification.

- Security-log modification.

- Security-control configuration modification.

**7.9.3 Availability**

Protection of the application's ability to enforce its security function.

Examples include:

- Accessibility service loss.

- Watchdog failure.

- Boot re-arm failure.

- Protection permission removal.

- Enforcement-process disruption.

Availability is security-relevant when loss of availability causes protected applications to become accessible.

**7.9.4 Authentication**

Protection against an attacker successfully establishing an identity or credential state they do not possess.

Examples include:

- PIN bypass.

- PIN reset.

- Authentication event spoofing.

- Biometric-result misuse.

**7.9.5 Authorization**

Protection against an authenticated or unauthenticated actor obtaining access beyond the authority granted to them.

Examples include:

- Protected-app access without authorization.

- Vault access without authorization.

- Unauthorized policy modification.

- Cross-application session reuse.

**7.9.6 Accountability**

Protection of the integrity and usefulness of security-relevant records.

Examples include:

- Deleting intruder events.

- Modifying failed-authentication history.

- Altering security events to conceal an attack.

**7.10 Attack-Surface Categories**

Threat identification must evaluate at least the following attack-surface categories.

**AS-001 — Application Components**

Includes:

- Activities.

- Services.

- Broadcast receivers.

- Providers if introduced later.

**AS-002 — Android Framework Interfaces**

Includes:

- Accessibility.

- Device Admin.

- Boot lifecycle.

- Application lifecycle.

- Package management.

- BiometricPrompt.

- Keystore.

**AS-003 — Inter-Process Communication**

Includes:

- Exported components.

- Intents.

- Framework callbacks.

- Permission-protected interfaces.

**AS-004 — User Interface**

Includes:

- Authentication screens.

- Vault screens.

- Settings.

- Navigation.

- Overlays.

- Touch/event handling.

**AS-005 — Local Storage**

Includes:

- EncryptedSharedPreferences.

- SQLCipher.

- Encrypted files.

- Database metadata.

- Configuration state.

**AS-006 — Authentication**

Includes:

- PIN entry.

- Biometric authentication.

- Lockout.

- Credential changes.

- Session establishment.

**AS-007 — Lifecycle and Availability**

Includes:

- Process death.

- Force-stop.

- Reboot.

- Background restrictions.

- OEM process management.

- Permission revocation.

**AS-008 — Build and Release**

Includes:

- Dependencies.

- Minification.

- Release configuration.

- Signing.

- Migration.

- Packaging.

**7.11 Threat Classes**

Threats should be grouped into the following major classes.

**T-C — Confidentiality Threats**

Unauthorized disclosure of protected information.

Examples:

- Credential extraction.

- Database extraction.

- Vault extraction.

- Intruder-photo extraction.

- Metadata disclosure.

**T-I — Integrity Threats**

Unauthorized modification of protected state.

Examples:

- Credential reset.

- Protected-app removal.

- Lockout manipulation.

- Policy weakening.

- Log manipulation.

**T-A — Availability Threats**

Interference with security enforcement.

Examples:

- Accessibility disruption.

- Watchdog disruption.

- Boot persistence failure.

- Permission removal.

- Process interference.

**T-Auth — Authentication Threats**

Attempts to satisfy authentication without possessing valid authorization.

**T-Authz — Authorization Threats**

Attempts to access protected functionality after bypassing or confusing authorization state.

**T-UI — User Interface Abuse**

Attempts to exploit:

- Overlays.

- Tapjacking.

- UI spoofing.

- Navigation.

- Event injection.

- Activity lifecycle behavior.

**T-IPC — Inter-Process Communication Abuse**

Attempts to exploit:

- Exported components.

- Intents.

- Broadcast receivers.

- Permission boundaries.

**T-Platform — Platform-Assumption Threats**

Threats caused by:

- Android lifecycle behavior.

- OEM behavior.

- Accessibility restrictions.

- Keystore behavior.

- Device Admin behavior.

- Permission changes.

**T-Supply — Supply-Chain Threats**

Threats arising from:

- Dependencies.

- Build tooling.

- Release artifacts.

- Signing infrastructure.

**7.12 Threat Preconditions**

Every threat must explicitly identify its prerequisites.

Examples include:

- Physical possession.

- Android device already unlocked.

- USB access.

- adb availability.

- A malicious application installed.

- Accessibility permission granted to another service.

- App Lock accessibility permission revoked.

- Overlay permission granted to an attacker.

- Root/system compromise.

- A particular application lifecycle state.

A threat must not assume capabilities that the attacker class does not possess.

**7.13 Attack-Path Analysis**

Threat analysis should describe an attack as a sequence rather than as a single action.

The preferred structure is:

Attacker Capability

│

▼

Precondition

│

▼

Attack Surface

│

▼

Security Boundary

│

▼

Exploit Action

│

▼

Security Decision

│

▼

Security Impact

This structure makes it possible to determine exactly where a mitigation operates.

**7.14 Threat Versus Vulnerability**

The Threat Model must distinguish threats from vulnerabilities.

A **threat** describes an adversarial scenario that could violate a security property.

A **vulnerability** is a weakness that makes that scenario possible or more likely.

For example:

**Threat:**

An attacker gains access to a protected application without App Lock authentication.

**Vulnerability:**

Foreground detection silently stops while App Lock continues to appear enabled.

The distinction is necessary so that multiple vulnerabilities can be mapped to the same threat and a single control can mitigate multiple vulnerabilities.

**7.15 Threat Versus Risk**

A threat does not automatically constitute a Critical or High risk.

Risk is evaluated using the project's established methodology:

Risk = Likelihood × Impact

The Threat Model must not inflate risk merely because a scenario is technically possible.

Likewise, a difficult attack must not automatically be considered Low if successful exploitation would expose the PIN, Vault, or root of trust.

**7.16 Likelihood Assessment**

Likelihood must consider factors such as:

- Required attacker capability.

- Physical access requirements.

- User interaction requirements.

- Required permissions.

- Attack complexity.

- Availability of the attack path.

- Reliability of exploitation.

- Required timing.

- Existing controls.

- Detectability.

- Platform restrictions.

The Test Specification's risk methodology remains the authoritative basis.

Where the existing methodology does not provide quantitative thresholds, the Threat Model must not invent numerical probabilities without an approved project decision.

**7.17 Impact Assessment**

Impact must consider the consequences to:

- Credential confidentiality.

- Cryptographic key confidentiality.

- Vault confidentiality.

- Intruder-photo confidentiality.

- Database confidentiality.

- Protected-app confidentiality.

- Security-policy integrity.

- Lockout integrity.

- Audit integrity.

- Enforcement availability.

- User data availability.

The most severe impacts involve compromise of the root of trust or direct disclosure of highly sensitive user data.

**7.18 Risk Rating**

Threats use the project's four-level scale:

- **Critical**

- **High**

- **Medium**

- **Low**

Risk classification must be derived from likelihood and impact.

The rating must be recorded together with the reasoning supporting both factors.

A rating without rationale is incomplete.

**7.19 Control Classification**

Every mitigation associated with a threat must have an explicit status.

**C-VERIFIED — Security Verified**

The control has:

1.  An implemented mechanism.

2.  A threat-specific security test.

3.  Test evidence.

4.  Identified build/configuration context.

5.  Traceability to the relevant requirement and threat.

6.  Verification status supported by the evidence.

**C-IMPL — Implemented but Security-Unverified**

The mechanism exists and may have functional/regression evidence, but the required threat-specific security verification has not been completed.

This is the current status of many legacy security mechanisms.

**C-PLAN — Planned**

The mechanism is specified or scheduled but is not implemented.

It must not be described as protecting the system today.

**C-ACCEPT — Accepted Limitation**

The exposure is known and deliberately accepted through the project's governance process.

**C-N/A — Not Applicable**

The control does not apply to the current architecture or threat boundary.

**7.20 Planned Controls Must Not Reduce Current Risk**

A planned control cannot be used to lower the current risk rating.

For example:

Root detection is planned.

does not constitute mitigation against a current root threat.

Likewise:

Anti-tapjacking protection will be implemented later.

does not mitigate the current overlay threat.

The current-state risk must be evaluated against controls that actually exist.

Future-state risk may be modeled separately.

**7.21 Existing Regression Tests Versus Security Verification**

Functional or regression evidence may demonstrate that a known defect is no longer reproducible.

It does not automatically establish security verification.

For example, a historical fast-relaunch bypass may have a regression test proving that the specific defect remains fixed.

That evidence is valuable historical security evidence.

However, it must not be labeled **security-verified** unless it satisfies the project's security-verification criteria.

This distinction is mandatory throughout the Threat Model.

**7.22 Historical Security Failures**

Historical failures are retained as threat evidence.

The current baseline includes, at minimum:

- Self-gate bypass.

- Fast-relaunch bypass.

- Fast-switch relock defect.

- Plaintext database exposure before encryption migration.

- Cryptographic dependency/build-integrity failure.

- Accessibility force-stop limitation.

A historical defect must remain traceable after remediation.

The remediation may change the current risk.

It must not erase the evidence that the attack path previously existed.

**7.23 Accepted Platform Limitations**

Platform limitations must not be mislabeled as vulnerabilities when they are inherent characteristics of the Android security model.

Examples include:

- Root/system compromise exceeding the application's trust boundary.

- Android-controlled Accessibility permission behavior.

- OEM background-process restrictions.

- Force-stop effects on Accessibility enforcement.

- Device Admin limitations.

- Keystore behavior outside application control.

Where a platform limitation creates meaningful exposure, it must still be modeled and assigned appropriate residual risk.

**7.24 Residual Risk**

Residual risk is the risk remaining after currently effective controls are applied.

It must be evaluated against the **current implementation state**.

The record must distinguish:

Inherent Risk

│

▼

Existing Effective Controls

│

▼

Residual Risk

│

▼

Planned Future Controls

│

▼

Target/Future Risk

Planned controls belong after current residual risk, not inside it.

**7.25 Threat Acceptance**

A residual risk may only be considered accepted when acceptance is explicitly governed.

The Threat Model must not silently convert an unresolved issue into an accepted risk.

Acceptance must identify:

- What is being accepted.

- Why it is acceptable.

- Scope of the acceptance.

- Duration or review condition.

- Responsible authority.

- Related decision record where required.

**7.26 Threat-to-Control Relationship**

A threat may have:

- One control.

- Multiple controls.

- Preventive controls.

- Detective controls.

- Recovery controls.

- Compensating controls.

Controls should therefore be represented individually rather than collapsing an entire security mechanism into a single statement.

Example:

Threat

│

├── Prevent

│

├── Detect

│

├── Respond

│

└── Recover

This is especially important for Accessibility enforcement, where App Lock cannot guarantee prevention of every failure mode but may detect some failures and guide recovery.

**7.27 Security Control Categories**

Controls should be classified as:

**Preventive**

Designed to prevent exploitation.

Examples:

- exported=false.

- Android sandbox.

- Credential verification.

- Encryption.

- Lockout.

**Detective**

Designed to identify security degradation or attack activity.

Examples:

- Permission-change detection.

- Protection-health monitoring.

- Security event logging.

**Responsive**

Designed to react to a detected condition.

Examples:

- Re-authentication.

- Lockout.

- Security notification.

- Protection recovery guidance.

**Recovery**

Designed to restore an acceptable security state.

Examples:

- Boot re-arm.

- Service restoration guidance.

- Future key-recovery mechanisms where approved.

A control may belong to more than one category where appropriate.

**7.28 Defense-in-Depth**

The Threat Model must recognize layered protection but must not treat independent layers as equivalent.

For example:

Application Sandbox

\+

Encryption at Rest

\+

Keystore Protection

\+

Runtime Authorization

provides defense in depth.

However:

- Sandbox failure does not imply encryption failure.

- Encryption failure does not imply authentication failure.

- Runtime authentication does not replace encryption at rest.

- Keystore compromise is materially more severe because it affects underlying cryptographic trust.

Threat analysis must therefore identify the specific layer being attacked.

**7.29 Single-Point-of-Failure Analysis**

A security control that is required for enforcement but has no effective independent fallback must be treated as a potential single point of failure.

The Accessibility detection path is the principal current example.

The Threat Model must ask:

1.  What happens if the mechanism stops?

2.  How quickly is failure detected?

3.  Can detection itself fail?

4.  Does the system fail open or fail closed?

5.  What security exposure results?

6.  What recovery is available?

7.  Can the attacker deliberately induce the failure?

This analysis is particularly important for availability-critical security mechanisms.

**7.30 Fail-Open and Fail-Closed Classification**

Every security-critical enforcement path must explicitly identify its failure behavior.

The current Accessibility enforcement path is recognized as **fail-open with detection/recovery assistance**.

This means:

- Loss of detection can permit protected applications to open.

- The watchdog can detect certain classes of loss.

- Detection is not instantaneous.

- Recovery requires user/platform action.

- The current architecture does not provide guaranteed fail-closed enforcement.

This is a known security property and must remain visible throughout threat analysis.

**7.31 Threat Prioritization**

Threat analysis priority should be driven by:

1.  Potential compromise of the root of trust.

2.  Direct disclosure of Vault or intruder data.

3.  Authentication bypass.

4.  Authorization bypass.

5.  Security-policy modification.

6.  Enforcement availability failures.

7.  Audit/integrity compromise.

8.  Defense-in-depth weaknesses.

A technically complex threat is not automatically lower priority than a simple threat.

Impact to the primary security boundary remains the dominant consideration.

**7.32 Required Threat Coverage**

Before the Threat Model can be considered complete, every one of the following must have explicit threat analysis:

- Authentication.

- Credential storage.

- Credential change/reset.

- Brute-force protection.

- Authentication sessions.

- Protected-app detection.

- Protected-app relocking.

- App Lock self-gating.

- Vault authorization.

- Vault storage.

- Vault metadata.

- Intruder photographs.

- Database encryption.

- Database key storage.

- Keystore.

- Application sandbox.

- Exported components.

- Accessibility.

- Peer Accessibility Services.

- Overlays.

- Tapjacking.

- UI event injection.

- Activity lifecycle.

- Process death.

- Force-stop.

- Reboot.

- Boot persistence.

- Watchdog.

- Device Admin.

- Permission changes.

- Backup/restore.

- Database migration.

- Database corruption.

- Keystore invalidation.

- Debugging.

- Tampering.

- Dependencies.

- Release/build integrity.

- Root/system compromise.

- Physical access.

- adb/USB access.

Any omitted category must be explicitly justified rather than silently excluded.

**7.33 Threat Model Completeness Rule**

The Threat Model must not be declared complete merely because every component has been listed.

Completeness requires coverage across:

Assets

\+

Attackers

\+

Attack Surfaces

\+

Trust Boundaries

\+

Security Properties

\+

Attack Scenarios

\+

Controls

\+

Residual Risks

\+

Security Verification

A threat catalog that lacks any of these dimensions is incomplete.

**7.34 Threat Lifecycle**

Threats follow the project security lifecycle:

Identify

│

▼

Analyze

│

▼

Rate

│

▼

Mitigate

│

▼

Implement

│

▼

Security Test

│

▼

Collect Evidence

│

▼

Verify

│

▼

Monitor

│

▼

Reassess

A threat is not permanently closed merely because a control was implemented.

Changes to the control, architecture, dependency, platform, or attack surface may reopen the analysis.

**7.35 Threat Reassessment Triggers**

Threat Model reassessment is required when any of the following occurs:

- A security control changes.

- A security-relevant architectural decision changes.

- The authentication mechanism changes.

- Credential handling changes.

- Cryptographic algorithms or key handling change.

- Storage architecture changes.

- The foreground-detection mechanism changes.

- Accessibility enforcement changes.

- Watchdog architecture changes.

- Boot persistence changes.

- A security requirement is added.

- A security requirement is removed.

- A security boundary changes.

- A previously trusted platform assumption changes.

- A new externally reachable component is introduced.

- An existing externally reachable component becomes exported.

- A new permission affects the security model.

- Backup/restore is introduced.

- Cloud/network functionality is introduced.

- A major Android platform version materially changes a relevant security mechanism.

- A phase/security gate requires reassessment.

- A newly discovered vulnerability invalidates an existing assumption.

- Penetration testing identifies a previously unmodeled attack path.

These triggers establish Threat Model reassessment as a controlled lifecycle activity rather than an ad-hoc review.

**7.36 Baseline Drift Prevention**

The Threat Model must not silently evolve to match implementation changes.

When implementation diverges from the approved architecture:

1.  The divergence must be identified.

2.  The affected threat/control relationships must be determined.

3.  The appropriate architecture decision/change process must be invoked.

4.  Requirements and traceability must be updated where applicable.

5.  Security impact must be assessed.

6.  Required security testing must be determined.

7.  The Threat Model must be updated only after the authoritative change is established.

The Threat Model is therefore a controlled security artifact, not an informal description of whatever the latest build happens to do.

**7.37 Section 7 Boundary**

Section 7 establishes the rules for constructing and maintaining the Threat Model.

It defines:

- Attacker classes.

- Security properties.

- Attack-surface categories.

- Threat categories.

- Threat-record requirements.

- Risk methodology.

- Control classifications.

- Residual-risk treatment.

- Historical evidence treatment.

- Platform limitation treatment.

- Security-verification distinctions.

- Threat completeness criteria.

- Reassessment triggers.

- Drift-prevention rules.

The next sections should apply this methodology to the concrete threats affecting the approved App Lock architecture.

No threat should be introduced later using a different attacker taxonomy, risk scale, control-status model, or verification definition without an approved change to this section.
