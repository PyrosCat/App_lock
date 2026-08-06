**13. Historical Failures and Security Findings**

**13.1 Purpose**

This section preserves known security failures, significant security findings, and historically observed weaknesses that materially inform the App Lock security model.

Historical failures SHALL be treated as empirical evidence of how the system can fail.

They SHALL NOT be removed from the Threat Model merely because the underlying defect has been fixed.

The purpose of preserving these findings is to:

- prevent rediscovery of previously observed attack paths;

- preserve the reasoning behind existing controls;

- identify boundaries that have historically failed;

- ensure fixes remain covered by security verification;

- provide concrete abuse cases for future testing;

- identify architectural assumptions that have proven unsafe;

- provide historical context when evaluating future changes.

This section records **what failed and what was learned**.

It does not establish residual-risk acceptance, risk-rating methodology, or governance authority.

**13.2 Historical Finding Classification**

Historical findings SHALL be classified according to their current state:

| **Status** | **Meaning** |
|----|----|
| **Open** | The weakness remains present and requires remediation or explicit treatment elsewhere |
| **Remediated** | The identified failure mechanism has been corrected |
| **Mitigated** | The original weakness remains possible but an additional control materially reduces exposure |
| **Accepted Platform Limitation** | The behavior results from a platform constraint and cannot be completely eliminated within the current architecture |
| **Superseded** | The affected architecture or mechanism has been replaced |
| **Under Reverification** | The previous remediation has been affected by a subsequent change and requires renewed verification |

A finding marked **Remediated** SHALL NOT be interpreted as irrelevant.

Its historical attack path SHALL remain available for regression and security-test design.

**13.3 Historical Finding HF-001 — App Lock Self-Gate Bypass**

**Description**

The application previously allowed a user to authenticate into App Lock, background the application, and subsequently resume directly into a protected App Lock screen without being required to authenticate again.

The failure occurred because the application's own sensitive UI was not consistently re-gated when the application returned from the background.

**Affected Boundary**

The affected boundary was:

Unauthenticated App Lock State

│

│ improper lifecycle transition

▼

Previously Authenticated UI

│

▼

Vault / Protected App-Lock Functionality

This violated the requirement that the App Lock user interface itself remain protected independently of the Android device's unlock state.

**Security Impact**

An attacker with access to an already-unlocked device could potentially exploit application lifecycle behavior to reach protected App Lock functionality without presenting the current App Lock credential.

The failure therefore represented an authentication-boundary bypass.

**Remediation**

The application was changed to re-establish the self-gate when the application returns from the background.

The current implementation uses the application lifecycle and self-lock behavior to prevent a previously authenticated UI state from being treated as permanently authorized.

The standing regression coverage protects this behavior.

**Current Status**

**Remediated.**

The implementation is regression-verified.

It SHALL nevertheless remain a permanent historical abuse case for security verification.

**Security-Test Requirement**

Future security testing SHALL verify:

- backgrounding immediately after authentication;

- returning through Recents;

- returning after application switching;

- returning after process/lifecycle transitions;

- attempting to reach vault functionality without renewed authorization where policy requires it.

**13.4 Historical Finding HF-002 — Fast Relaunch Bypass**

**Description**

A rapid relaunch sequence previously allowed a protected application to become visible while the App Lock authentication activity was being dismissed or replaced.

The timing relationship between the protected application, the lock screen, and activity lifecycle allowed the protected application to win the race.

**Affected Boundary**

Protected App Launch

│

├── LockScreenActivity

│

└── Protected Application

│

▼

Race / Lifecycle Gap

│

▼

Protected UI Visible

**Security Impact**

The failure represented a direct protected-app authentication bypass.

It demonstrated that authentication enforcement could not depend solely on the expected ordering of Android activity lifecycle operations.

**Remediation**

The enforcement path was strengthened so that foreground processing evaluates the target application against the current authentication-session state on each relevant foreground event.

The implementation does not rely solely on deduplicating application launches.

**Current Status**

**Remediated.**

The behavior is covered by the standing regression campaign.

**Security-Test Requirement**

Security testing SHALL include rapid:

- launch;

- relaunch;

- home;

- Recents;

- app switching;

- repeated foreground transitions.

Testing SHALL specifically attempt to expose a race between LockScreenActivity and the protected application.

**13.5 Historical Finding HF-003 — Fast-Switch Relock Defect**

**Description**

The protected-app enforcement path previously exhibited incorrect behavior when rapidly switching between applications.

A previously authenticated application could remain effectively authorized across a transition in which its relock policy should have required authentication.

**Affected Boundary**

The affected boundary was the per-application authorization-session lifecycle.

The relevant invariant is:

Authorization for one protected application SHALL NOT silently become authorization for another protected application.

**Security Impact**

An attacker could potentially exploit rapid application switching to obtain access without presenting the required current authentication.

**Remediation**

LockSessionManager was established as an in-memory, per-package authorization mechanism.

The enforcement path now evaluates foreground events against the current session state and applies the applicable RelockPolicy.

Sessions are not globally shared between protected applications.

**Current Status**

**Remediated.**

**Security-Test Requirement**

Testing SHALL include:

- A → B → A transitions;

- A → B → C → A transitions;

- rapid repeated switching;

- switching during authentication;

- switching immediately after authentication;

- switching after the grace period;

- switching after screen-off;

- switching after process death/restart.

**13.6 Historical Finding HF-004 — Build Integrity Failure**

**Description**

A cryptographic dependency introduced compile-time annotation behavior that caused the minified release build to fail.

The failure was not a runtime authentication bypass, but it exposed an important security-engineering dependency: security functionality that cannot survive the production build pipeline cannot be considered reliably deployable.

**Affected Boundary**

The affected boundary was the relationship between:

Security Dependency

│

▼

Application Build

│

▼

Minified Release Artifact

│

▼

Deployable Security Implementation

**Security Impact**

The immediate impact was build failure.

The broader security concern was that dependency or build-system changes could silently prevent security controls from reaching the production artifact or could force emergency build changes that had not received appropriate review.

**Remediation**

The release/minified build was incorporated into standing integration validation.

Dependency governance and dependency auditing were also established as Foundation engineering controls.

**Current Status**

**Remediated as an identified build-integrity failure.**

The broader dependency and supply-chain threat remains an ongoing engineering concern.

**Security-Test Requirement**

The production/minified artifact SHALL continue to be built and validated as part of the project's standing verification process.

A successful debug build SHALL NOT be considered sufficient evidence of release-build integrity.

**13.7 Historical Finding HF-005 — Plaintext Database Storage**

**Description**

Earlier application versions stored database information without the current SQLCipher protection.

This represented a data-at-rest confidentiality gap.

**Affected Assets**

The affected information included database records associated with:

- protected applications;

- vault metadata;

- security events;

- intruder events;

- timestamps;

- other security-sensitive application state.

**Security Impact**

An attacker obtaining the underlying database could inspect sensitive metadata without first defeating the application's normal authentication boundary.

This demonstrated that UI authentication alone was insufficient as a data-at-rest protection mechanism.

**Remediation**

The database was migrated to encrypted storage using SQLCipher.

The database encryption passphrase is independently generated and stored through the Android Keystore-backed credential-storage mechanism.

The current architecture therefore separates:

App Lock Authentication

≠

Database Encryption Key

**Current Status**

**Remediated for the current encrypted-storage architecture.**

The migration path remains security-sensitive and SHALL continue to receive verification.

**Security-Test Requirement**

Testing SHALL verify that:

- newly created databases are encrypted;

- migrated databases are encrypted;

- plaintext database remnants are not left behind;

- database keys are not written to ordinary storage;

- database extraction without the required Keystore material does not yield usable plaintext.

**13.8 Historical Finding HF-006 — Force-Stop / Accessibility Enforcement Limitation**

**Description.** The current delivered application relies on the Android Accessibility framework as its only implemented foreground-detection source. Force-stopping the application can terminate the process, watchdog, and current detection path and may affect the Accessibility service state. The application cannot silently restore an Accessibility grant without user/system participation.

**Affected Boundary (current delivered build).**

    Android Accessibility Framework
      → AppDetectionService
      → ApplicationLockEngine
      → Protected-App Gate

**Security Impact.** In the current build, loss of effective Accessibility detection can allow protected applications to become accessible without App Lock authentication. This is an availability failure with direct authorization consequences.

**Approved Architectural Treatment.** ADR-013A introduces a mandatory UsageStatsManager/Usage Access baseline and retains Accessibility as an optional enhancement. After implementation, loss of Accessibility alone SHALL degrade the application to the healthy baseline rather than disable protection. Force-stop remains security-relevant because it may terminate the required baseline service, watchdog, or presentation path.

**Current Mitigation.** Protection-state monitoring; watchdog behavior; permission-state checks; security-event logging; user notification; recovery guidance. These mechanisms are detective and responsive; they do not guarantee continuous enforcement after the active required detection path has been terminated.

**Current Status.** Open for the current Accessibility-only implementation. The approved two-tier architecture is a planned mitigation and SHALL NOT be described as remediation until its baseline tier, health monitoring, source selection, and presentation path are implemented and security-verified.

**13.9 Historical Finding HF-007 — Accessibility Silent-Failure Risk**

**Description.** In the current delivered build, the Accessibility service may appear enabled while failing to deliver the foreground events required for enforcement. This is more severe than an obvious revocation because permission-state monitoring may incorrectly report that protection is available.

**Security Impact (current build).**

    Accessibility Enabled
      → AppDetectionService Appears Healthy
      → No Foreground Events Delivered
      → Protected App Opens Without Enforcement

Under the approved target architecture, this failure is narrowed to the optional enhancement when the baseline UsageStats detector remains healthy. The baseline tier introduces a corresponding need to detect stale or unavailable UsageStats data, sampling-service failure, and incorrect source-health selection.

**Current Status.** Open for the current delivered build. Enhancement-tier silent failure remains security-relevant after migration but SHALL NOT be classified as total enforcement loss when the verified baseline remains operational.

**Required Security Treatment.** The Core Security Platform SHALL implement health verification that can distinguish: permission granted; detector service running; source responsive; events or usage data current and being received; protected-app transition detected; detection routed through the Trigger Processor; lock interface presented; enforcement actually occurring. A permission-state check alone SHALL NOT be treated as proof of detector or enforcement health for either tier.

**13.10 Historical Finding HF-008 — Credential/UI and Data-Key Independence**

**Description**

The current architecture does not derive the database or vault encryption keys from the user's App Lock PIN.

The PIN is used for authentication.

The database passphrase and vault encryption are protected through the Android Keystore hierarchy independently of the PIN.

**Security Significance**

This architecture provides strong protection against offline extraction from a normal, non-rooted device.

However, it establishes an important trust-boundary distinction:

PIN

│

└──► UI / Authentication Authorization

Android Keystore

│

├──► Database Key

│

└──► Vault Encryption

The PIN is therefore not the cryptographic root protecting the vault.

**Consequence**

An attacker capable of executing with sufficient authority inside the application's process or below the application's trust boundary may potentially obtain usable plaintext through the application's legitimate cryptographic capabilities without knowing the PIN.

This includes the root/system-compromise class that is outside the application's guaranteed security boundary.

**Current Status**

**Architectural security finding.**

This is not classified as a defect in the current threat model solely because the architecture intentionally trusts the Android Keystore and application sandbox.

However, the distinction SHALL remain explicit.

Any future documentation claiming that "the PIN encrypts the vault" or equivalent SHALL be treated as incorrect unless the architecture is formally changed.

**13.11 Historical Finding HF-009 — Keystore Invalidation Recovery Gap**

**Description**

The application currently lacks a complete recovery path for loss or invalidation of the Android Keystore material protecting application secrets.

Because the same Keystore-rooted protection hierarchy protects critical material, loss of the required key can render:

- PIN credentials;

- database key material;

- vault ciphertext;

unrecoverable.

**Security Impact**

This is primarily a resilience and availability concern with direct confidentiality implications.

An invalidated key does not provide an authentication bypass.

Instead, it can prevent legitimate decryption of protected information.

**Current Status**

**Open.**

The current implementation does not provide a complete recovery mechanism.

**Required Treatment**

The security architecture SHALL explicitly define:

- how key invalidation is detected;

- how affected data is identified;

- what user-visible behavior occurs;

- whether secure recovery is possible;

- when data must be considered permanently unrecoverable;

- how the event is logged;

- how recovery avoids becoming a data-extraction bypass.

**13.12 Historical Finding HF-010 — Authentication Screen Overlay / Tapjacking Exposure**

**Description**

The authentication screen currently protects against screen capture through FLAG_SECURE, but that control does not prevent another application from placing an overlay over the authentication interface.

The application does not currently provide complete anti-tapjacking or obscured-touch defenses.

**Affected Boundary**

Malicious Application

│

▼

TYPE_APPLICATION_OVERLAY

│

▼

App Lock Authentication UI

│

▼

User Interaction

**Security Impact**

Potential attacks include:

- obscuring legitimate controls;

- misleading the user about what they are interacting with;

- manipulating interaction flows;

- attempting to induce unintended input.

This is distinct from screenshot protection.

**Current Status**

**Open security concern.**

FLAG_SECURE SHALL NOT be recorded as remediation for this threat.

**Required Treatment**

The Core Security Platform SHALL evaluate appropriate defenses against:

- obscured touches;

- tapjacking;

- malicious overlays;

- UI spoofing;

- authentication-screen interaction manipulation.

The effectiveness of any selected mitigation SHALL be demonstrated through security-specific testing.

**13.13 Historical Finding HF-011 — Debug and Tamper Resistance Gap**

**Description**

The current implementation does not yet provide the planned production-grade debug and tamper protections represented by the relevant security requirements.

**Security Significance**

An attacker with sufficient instrumentation capability may have greater ability to inspect or manipulate application behavior.

The absence of these controls is particularly relevant to the application's later security-hardening objectives.

**Current Status**

**Planned / not implemented.**

The following SHALL NOT be treated as existing mitigations:

- root detection;

- root response;

- tamper detection;

- debug detection.

Their planned requirements remain visible until implementation and security verification are completed.

**13.14 Historical Finding HF-012 — Security Verification Maturity Gap**

**Description**

Several existing security behaviors have been functionally regression-verified but have not yet undergone dedicated threat-model-driven security verification.

This creates a distinction between:

"It works under the tested functional scenarios"

and:

"The identified threat has been specifically tested

and the mitigation has security evidence."

**Security Significance**

Without this distinction, historical regression evidence could be incorrectly promoted to security assurance.

**Current Status**

**Process/security-assurance gap.**

**Required Treatment**

Phase 1 SHALL establish the formal mapping:

Historical Finding

↓

Threat

↓

Security Control

↓

Security Test

↓

Execution Evidence

↓

Security Verification Status

Historical regression tests SHALL be retained where they provide useful protection, but SHALL not automatically satisfy the security-verification requirement.

**13.15 Historical Finding Preservation Rules**

Historical findings SHALL remain preserved when:

- the defect has been fixed;

- the affected code has been refactored;

- the implementation has moved phases;

- the underlying requirement has been renamed;

- the original test has been rewritten;

- the architecture has changed.

A finding may be marked **Superseded** only when the affected mechanism has genuinely been replaced and the replacement security boundary is separately evaluated.

Historical findings SHALL NOT be deleted merely because their current status is "fixed."

**13.16 Historical Findings and Regression Testing**

Every remediated finding that represents a realistic security bypass SHALL have corresponding regression coverage unless technically impossible.

The minimum historical bypass set includes:

- self-gate bypass;

- fast relaunch;

- fast application switching;

- vault resume bypass;

- plaintext database exposure;

- release-build dependency failure.

These cases SHALL remain part of the project's permanent security regression corpus.

**13.17 Historical Findings and Future Changes**

When a future architectural or implementation change affects a historically failed boundary, the affected historical finding SHALL be reconsidered.

The project SHALL NOT assume:

"The bug was already fixed."

Instead, the relevant question SHALL be:

"Does the current implementation still preserve the security property that fixed the historical failure?"

A refactor that changes lifecycle handling, authentication, session management, storage, encryption, accessibility enforcement, or exported components SHALL therefore trigger review of applicable historical findings.

**13.18 Historical Evidence Integrity**

Historical findings SHALL be supported by available evidence where practical, including:

- defect reports;

- test cases;

- test execution records;

- logs;

- screenshots or recordings where appropriate;

- commits;

- Architecture Decision Records;

- requirement references;

- regression results;

- security-test evidence.

Evidence SHALL identify the implementation/configuration under which the failure or remediation was observed when that information is available.

Historical evidence SHALL not be altered to make a previous implementation appear more secure than it was.

**13.19 Findings Not Yet Converted Into Formal Threats**

The following historical or as-built findings SHALL remain available as inputs to the Threat Model even if their final threat classification has not yet been completed:

- accessibility silent failure;

- Keystore invalidation;

- PIN/data-key independence;

- authentication-screen overlay/tapjacking;

- debug/tamper protection gap;

- fail-open enforcement behavior;

- security-verification maturity gap.

Their presence in this section does not itself determine their final risk rating.

The formal threat, control, residual-risk, and governance treatment SHALL be established in the sections dedicated to those subjects.

**13.20 Historical Security Lessons**

The preserved findings establish several architectural lessons that SHALL constrain future design:

1.  **Lifecycle state cannot be assumed to preserve authentication.**

2.  **Rapid application transitions must be treated as adversarial timing conditions.**

3.  **Per-application authorization must remain explicitly scoped.**

4.  **UI authentication and data-at-rest encryption are separate security mechanisms.**

5.  **Permission state is not equivalent to enforcement health.**

6.  **Build integrity is part of security assurance.**

7.  **Platform-dependent enforcement mechanisms require explicit failure analysis.**

8.  **Screen-capture protection does not equal tapjacking protection.**

9.  **Implementation status does not equal security verification.**

10. **Previously fixed vulnerabilities remain useful security tests.**

These lessons SHALL inform future architecture, implementation, and security-test decisions.

**13.21 Section 13 Completion Criteria**

Section 13 is complete when:

- known historical security failures are preserved;

- each significant failure identifies its affected boundary;

- the failure mechanism is documented;

- remediation or current treatment is identified;

- current status is explicit;

- relevant verification evidence is identified;

- historical bypasses remain available for regression/security testing;

- unresolved findings remain visible;

- platform limitations are distinguished from application defects;

- architectural findings are distinguished from implementation defects;

- functional verification is distinguished from security verification;

- historical evidence cannot silently be converted into stronger assurance than it supports.

Section 13 SHALL remain a historical evidence repository.

It SHALL NOT be used to silently accept unresolved risk, redefine security requirements, or replace formal threat, risk, or governance analysis.
