**11. Security Verification and Evidence Model**

**11.1 Purpose**

This section defines the mandatory model for demonstrating that a security control is effective.

The Threat Model SHALL distinguish between:

- a threat being identified;

- a control being defined;

- a control being implemented;

- a control being functionally tested;

- a control being security-tested;

- evidence being collected;

- evidence being reviewed;

- a requirement being formally verified.

The central rule is:

**A security control SHALL NOT be considered security-verified merely because it has been implemented or because a functional test passes.**

Security verification requires evidence demonstrating that the implemented control addresses the specific security threat or security requirement for which it exists.

**11.2 Security Verification Chain**

Every material security control SHALL follow this chain:

Threat

↓

Security Objective

↓

Security Requirement

↓

Control

↓

Implementation

↓

Security Test

↓

Execution

↓

Evidence

↓

Evidence Review

↓

Verification Decision

↓

RTM / Threat Model Status

A missing link SHALL prevent the control from being promoted to **Security-Verified**.

**11.3 Threat-to-Control Relationship**

Every significant threat SHALL identify the control or controls intended to mitigate it.

The relationship SHALL be explicit.

Example:

Threat:

Attacker launches a protected application after

the previous authorization session has expired.

↓

Security Objective:

Protected applications require valid App Lock authorization.

↓

Control:

ApplicationLockEngine evaluates authorization on

every relevant foreground event.

↓

Implementation:

AppDetectionService →

ApplicationLockEngine →

LockSessionManager →

LockPolicyManager →

LockScreenActivity

↓

Security Test:

Rapid relaunch / fast-switch bypass campaign.

↓

Evidence:

Test execution + device/build configuration +

observed result.

↓

Verification:

Security control verified.

The existence of the implementation alone is insufficient.

**11.4 Security Requirement Traceability**

Security-relevant requirements SHALL remain traceable to:

- threat;

- asset;

- security objective;

- control;

- implementation;

- test case;

- test execution;

- evidence;

- verification status.

The preferred relationship is:

Asset

↓

Threat

↓

Requirement

↓

Control

↓

Implementation

↓

Test

↓

Evidence

Where a requirement addresses multiple threats, all material relationships SHALL be recorded.

Where multiple controls mitigate one threat, each material control SHALL be identified.

**11.5 Functional Verification vs Security Verification**

The project SHALL maintain an explicit distinction between functional verification and security verification.

**Functional / Regression Verification**

Demonstrates that the application behaves as intended under defined functional conditions.

Examples include:

- PIN authentication succeeds with the correct PIN;

- an incorrect PIN is rejected;

- a protected application launches;

- the vault opens after authentication;

- screen capture is blocked;

- the application relocks after screen-off.

**Security Verification**

Demonstrates that the behavior resists a defined adversarial condition.

Examples include:

- attempting to bypass authentication by rapidly relaunching a protected application;

- attempting to return to a protected screen after authentication state should have expired;

- attempting to access vault content without authorization;

- attempting to manipulate lockout state;

- attempting to exploit exported components;

- attempting to interfere with security-critical services;

- attempting to extract encrypted data from storage.

A functional test may contribute evidence to a security case, but SHALL NOT automatically establish security verification.

**11.6 Verification Status Model**

Security controls SHALL use the following status progression:

Planned

↓

Implemented

↓

Regression-Verified

↓

Security-Tested

↓

Evidence-Reviewed

↓

Security-Verified

A control may move backward when affected by change.

For example:

Security-Verified

↓

Security-relevant implementation change

↓

Reverification Required

The prior verification evidence SHALL remain historical evidence but SHALL NOT automatically validate the changed implementation.

**11.7 "Implemented" Is Not "Verified"**

The following SHALL NOT constitute sufficient evidence for Security-Verified status:

- source code exists;

- a method appears correct during code review;

- a requirement is marked implemented;

- a test case has been written;

- a test passes without threat mapping;

- a developer manually confirms behavior;

- a regression campaign passes without security classification;

- documentation states that the control exists.

Implementation is evidence that the control exists.

It is not evidence that the control successfully mitigates its intended threat.

**11.8 Security Test Design**

Security tests SHALL be derived from threats and controls.

A security test SHOULD define:

1.  threat being exercised;

2.  target asset;

3.  security objective;

4.  control under test;

5.  attacker capability;

6.  preconditions;

7.  attack action;

8.  expected security boundary;

9.  failure condition;

10. expected secure behavior;

11. test environment;

12. evidence requirements.

The test SHALL make clear what constitutes a successful attack and what constitutes successful defense.

**11.9 Adversarial Test Orientation**

Security tests SHALL be written from the attacker's perspective.

The test SHALL attempt to violate a security property rather than merely demonstrate normal operation.

For example, a normal authentication test asks:

Does the correct PIN unlock the application?

A security test asks:

Can an attacker reach protected functionality without presenting the current valid credential?

The latter is the security boundary being verified.

**11.10 Security Test Categories**

The project SHALL organize security verification around the established threat classes.

**Authentication Bypass**

Tests SHALL attempt to:

- bypass PIN authentication;

- exploit stale sessions;

- exploit rapid app switching;

- exploit rapid relaunch;

- exploit lifecycle transitions;

- exploit reboot behavior;

- exploit Back/Recents behavior;

- exploit process death;

- exploit authentication-state inconsistencies.

**Authorization Bypass**

Tests SHALL attempt to:

- access protected applications without authorization;

- access the vault without authorization;

- access protected settings without authorization;

- modify protected policies without authorization;

- disable security controls without authorization.

**Storage and Cryptography**

Tests SHALL attempt to:

- recover plaintext from private storage;

- recover vault content from extracted ciphertext;

- recover database content from extracted files;

- recover filenames or sensitive metadata;

- recover key material from persistent storage;

- exploit backup or restore paths.

**IPC and Component Abuse**

Tests SHALL attempt to:

- invoke exported components unexpectedly;

- spoof system broadcasts where applicable;

- launch protected activities;

- abuse service interfaces;

- inject malicious intents;

- exploit exported receiver behavior.

**Enforcement Availability**

Tests SHALL attempt to:

- interfere with accessibility service operation;

- force-stop the application;

- interfere with watchdog operation;

- revoke required permissions;

- reboot the device;

- induce process death;

- exploit OEM/background restrictions;

- cause silent loss of enforcement.

**UI and Interaction Abuse**

Tests SHALL attempt to:

- exploit malicious overlays;

- obscure security UI;

- exploit tapjacking;

- inject touch events;

- exploit peer accessibility services;

- exploit lifecycle transitions;

- capture authentication UI.

**11.11 Historical Bypass Tests**

Previously discovered security failures SHALL be converted into permanent security regression cases where technically applicable.

The following historical failures SHALL remain represented:

- self-gate bypass;

- fast-relaunch bypass;

- fast-switch relock defect;

- plaintext database exposure;

- migration-related security exposure;

- force-stop/accessibility enforcement limitation.

A historical defect SHALL NOT be removed from security verification merely because the implementation has been corrected.

The historical failure demonstrates that the threat is credible.

**11.12 Security Test Preconditions**

Each security test SHALL identify its required environment.

At minimum, where relevant:

- Android version;

- device/model;

- application version;

- build type;

- release/debug state;

- security configuration;

- permissions;

- accessibility state;

- device-admin state;

- authentication configuration;

- network state where applicable;

- test data;

- database state.

A result without sufficient configuration information SHALL be considered incomplete evidence.

**11.13 Configuration Identification**

Security evidence SHALL identify the exact configuration against which the test was performed.

Where practical, evidence SHALL include:

- application version;

- commit/build identifier;

- build variant;

- APK/package identity;

- Android API level;

- device identifier or test-device identity;

- relevant security settings;

- test configuration;

- date/time;

- test-case identifier.

This prevents evidence from being detached from the implementation that produced it.

**11.14 Evidence Requirements**

Security evidence SHALL be sufficient for an independent reviewer to determine:

1.  what was tested;

2.  why it was tested;

3.  against which threat;

4.  against which implementation;

5.  under what conditions;

6.  what happened;

7.  whether the expected security property held;

8.  whether the evidence supports the claimed verification status.

Evidence may include, where appropriate:

- automated test output;

- instrumentation logs;

- screenshots;

- screen recordings where capture does not itself expose protected information;

- device logs;

- crash reports;

- static-analysis output;

- dependency-analysis reports;

- configuration records;

- penetration-test findings;

- controlled attack results;

- build artifacts;

- code-review evidence.

**11.15 Evidence Integrity**

Security evidence SHALL be protected against accidental or deliberate ambiguity.

Evidence SHALL identify the source and execution context.

Where evidence is manually generated, the record SHALL identify:

- who performed the test;

- when it was performed;

- what build was tested;

- what environment was used;

- the result;

- any deviations from the expected procedure.

Security evidence SHALL NOT contain unnecessary secrets.

PINs, key material, vault plaintext, sensitive filenames, or other protected content SHALL NOT be included merely to make evidence appear complete.

**11.16 Evidence Sufficiency**

Evidence SHALL be evaluated for sufficiency rather than quantity.

Ten screenshots of a normal authentication flow do not establish resistance to authentication bypass.

Conversely, a single reproducible adversarial test with complete configuration and execution evidence may establish a specific narrow security property.

The governing principle is:

**Evidence quality is determined by its ability to demonstrate the security claim, not by its volume.**

**11.17 Negative Testing Requirement**

Material security controls SHALL include negative testing.

A security test SHALL attempt at least one unauthorized or adversarial path where applicable.

Examples:

Valid PIN

→ expected success

Invalid PIN

→ expected failure

No PIN

→ expected failure

Expired session

→ expected failure

Rapid relaunch

→ expected failure

Protected app switch

→ expected reauthentication

The exact attack cases SHALL be derived from the applicable threat rather than mechanically applying the same test to every control.

**11.18 Security-Test Exit Criteria**

A security test campaign SHALL NOT be considered complete merely because all test cases executed.

The campaign SHALL establish:

- required tests were executed;

- required environments were covered;

- failures were recorded;

- security-relevant defects were triaged;

- evidence is available;

- affected requirements are identified;

- affected threats are identified;

- residual risk is understood;

- verification decisions are recorded.

A failed security test SHALL remain visible until it is either:

- remediated and successfully retested;

- formally accepted as residual risk;

- determined to be invalid through documented analysis.

**11.19 Security Defect Handling**

A security test failure SHALL be treated as a security defect or documented security finding.

The finding SHALL identify:

- affected threat;

- affected asset;

- affected requirement;

- affected control;

- observed behavior;

- expected behavior;

- severity;

- reproduction conditions;

- evidence;

- remediation;

- retest requirements.

The defect SHALL NOT be closed solely because a code change was made.

Closure requires appropriate verification evidence.

**11.20 Severity and Risk Relationship**

Security-test failures SHALL use the project's established risk model.

Risk SHALL continue to be evaluated using:

**Risk = Likelihood × Impact**

The resulting risk classification SHALL remain consistent with the project's established:

- Critical;

- High;

- Medium;

- Low

levels.

Severity and risk SHALL not be conflated.

A defect may be technically severe while the current exposure is reduced by another control, or a seemingly small defect may expose a critical asset.

The assessment SHALL therefore consider the complete threat path.

**11.21 Device Coverage**

Security verification SHALL account for Android platform and device variation where the control depends on platform behavior.

The existing regression evidence includes testing on:

- API 33;

- API 35;

- the established NucBox environment;

- the established Moto G environment.

This evidence establishes functional/regression coverage.

It SHALL NOT automatically establish universal security assurance across Android devices.

Controls dependent upon:

- Accessibility behavior;

- background execution;

- boot behavior;

- overlay behavior;

- permission enforcement;

- Keystore behavior;

- device-admin behavior

SHALL receive platform-sensitive security testing where those differences materially affect the threat.

**11.22 Security Verification of the Accessibility Boundary**

Because accessibility-based enforcement is security-critical, its verification SHALL test more than permission state.

The verification model SHALL distinguish:

Accessibility Enabled

≠

Service Bound

≠

Events Delivered

≠

Protected App Detected

≠

Lock Screen Successfully Presented

A security test SHALL therefore exercise the complete enforcement path where practical.

The test objective is not merely:

"Accessibility is enabled."

It is:

"A protected application cannot become usable without App Lock authorization under the tested operating conditions."

Known limitations such as silent event loss and OEM background restrictions SHALL remain explicit residual risks.

**11.23 Security Verification of Vault Protection**

Vault verification SHALL distinguish the two security boundaries:

**Runtime Authorization Boundary**

An unauthenticated user SHALL NOT reach vault functionality through the App Lock UI.

**Offline Confidentiality Boundary**

Extracted storage SHALL NOT provide usable vault plaintext without the cryptographic keys protected by the Android Keystore boundary.

The Threat Model SHALL NOT claim that the PIN cryptographically protects the vault.

The current architecture uses:

PIN

↓

UI Authorization

Android Keystore

↓

DB Key / Encrypted Storage

↓

Vault Confidentiality

These are separate protections.

Security evidence SHALL test them separately.

**11.24 Security Verification of Credential Protection**

Credential verification SHALL establish that:

- plaintext PINs are not persisted;

- the stored credential representation is appropriately protected;

- the current PIN is required for PIN change;

- forgotten-PIN recovery does not provide a bypass;

- lockout state cannot be bypassed through restart;

- authentication failures do not expose the credential;

- security-sensitive logs do not expose credential material.

The absence of a forgotten-PIN recovery mechanism SHALL be recorded as an intentional security/usability tradeoff rather than treated as an implementation omission.

**11.25 Security Verification of Key Protection**

Key-management verification SHALL establish:

- the database key is independently generated;

- the database key is not derived from the PIN;

- vault encryption keys are independent from the database key;

- key material is protected by the Android Keystore boundary;

- persistent storage does not expose usable key material;

- key-management changes receive appropriate verification.

The Threat Model SHALL distinguish:

protection of key material

from:

recovery of key material after Keystore failure.

The current absence of Keystore-invalidation recovery SHALL remain a documented security/reliability gap until addressed.

**11.26 Change-Triggered Reverification**

Security verification SHALL be continuous.

A previously verified control SHALL require reassessment when a change affects:

- implementation;

- dependencies;

- configuration;

- Android platform behavior;

- trust boundaries;

- cryptographic primitives;

- key handling;

- authentication;

- authorization;

- protected-app detection;

- security-critical permissions;

- exported components.

The default assumption after a material security change SHALL be:

**Reverification Required**

unless impact analysis demonstrates that existing evidence remains valid.

**11.27 Security Evidence Lifecycle**

Evidence SHALL follow the project lifecycle:

Test Planned

↓

Test Executed

↓

Evidence Captured

↓

Evidence Reviewed

↓

Finding / Pass Determined

↓

Requirement Status Updated

↓

Threat Model Status Updated

↓

RTM Updated

↓

Phase Gate Evidence Available

Evidence SHALL remain associated with the test and build that generated it.

**11.28 Verification Promotion Rules**

A control may be promoted to **Security-Verified** only when:

1.  the threat is identified;

2.  the security objective is defined;

3.  the applicable requirement is identified;

4.  the control is implemented;

5.  an appropriate security test exists;

6.  the security test has executed;

7.  execution evidence is available;

8.  the tested implementation/configuration is identified;

9.  the result demonstrates the expected security property;

10. applicable failures have been resolved or formally accepted;

11. the verification decision is recorded.

No single artifact can substitute for the complete chain when the missing artifact is material to the security claim.

**11.29 Verification Downgrade Rules**

A verification status SHALL be downgraded when:

- implementation changes materially;

- a dependency affecting the control changes;

- a new vulnerability invalidates an assumption;

- the Android platform changes relevant behavior;

- a new threat invalidates the original threat model;

- a security test demonstrates a bypass;

- evidence is found to be incomplete or invalid;

- configuration differs materially from the verified configuration.

The downgrade SHALL preserve the historical evidence and explain why it is no longer sufficient.

**11.30 Security Claims**

Security claims SHALL be stated narrowly enough to be supported by evidence.

For example:

**Supported claim:**

"The tested implementation re-locks the protected application after the defined session-expiration condition on the tested Android configurations."

**Unsupported claim:**

"Protected applications can never be bypassed."

Similarly:

**Supported claim:**

"Extracted vault ciphertext was not decryptable using the tested offline extraction path without access to the protected cryptographic key."

**Unsupported claim:**

"The vault is impossible to decrypt."

The Threat Model SHALL avoid absolute claims unless the architecture and evidence genuinely justify them.

**11.31 Security Evidence and Phase Gates**

Security evidence SHALL feed directly into phase decisions.

Security Evidence

↓

Verification Status

↓

Risk / Residual Risk

↓

Phase Gate

↓

PASS / CONDITIONAL PASS / FAIL

A phase gate SHALL use actual evidence rather than implementation progress reports.

**11.32 Evidence Retention**

Security verification evidence SHALL be retained for the lifetime necessary to demonstrate:

- what was verified;

- when it was verified;

- against which implementation;

- under which configuration;

- which threats were addressed;

- what residual risks remained.

Historical security failures SHALL also remain available as security knowledge and regression-test provenance.

**11.33 Evidence Does Not Override Architecture**

A successful test SHALL NOT authorize behavior that violates the approved architecture.

If implementation differs from the approved architecture, the difference SHALL be resolved through the project's deviation/ADR process.

Likewise, a test passing against an incorrect implementation SHALL NOT silently redefine the security requirement.

The sequence SHALL remain:

Approved Requirement

↓

Approved Architecture

↓

Approved Implementation

↓

Security Verification

Not:

Implementation

↓

Whatever Passed the Test

↓

New Security Definition

**11.34 Verification Authority**

The verification status recorded in the RTM SHALL reflect reviewed evidence, not developer intent.

The Threat Model SHALL identify the security claim and required evidence.

The Test Specification SHALL define the verification procedure.

The implementation SHALL provide the control.

The test execution SHALL provide observed results.

The evidence record SHALL establish provenance.

Project governance SHALL control acceptance of deviations and residual risk.

No individual artifact SHALL silently override another.

**11.35 Security Verification Invariant**

The following invariant SHALL govern the project:

**No security claim without a corresponding threat, control, test, and evidence chain.**

A second invariant SHALL apply:

**No verification status without evidence.**

A third invariant SHALL apply:

**No permanent verification status for a materially changed security control without reassessment.**

**11.36 Section 11 Completion Criteria**

Section 11 is complete only when:

- the threat-to-control-to-evidence chain is defined;

- functional verification is explicitly separated from security verification;

- security status terminology is standardized;

- adversarial testing requirements are defined;

- historical bypasses are preserved as security evidence;

- configuration identification is mandatory;

- evidence sufficiency is defined;

- security-test failures have controlled disposition;

- accessibility enforcement receives end-to-end security verification;

- vault runtime authorization and offline confidentiality are separately verified;

- credential and key protection are separately verified;

- change-triggered reverification is mandatory;

- verification promotion and downgrade rules are explicit;

- security claims are limited to what evidence supports;

- phase gates consume security evidence;

- evidence cannot silently redefine architecture or requirements.

**11.37 Boundary to Section 12**

Section 11 defines **how security claims are tested, evidenced, and verified**.

Section 12 defines the **Security Risk and Residual-Risk Model**, including threat scoring, risk acceptance, compensating controls, security debt, risk ownership, escalation, and the conditions under which unresolved security exposure may or may not be carried forward between project phases.
