**14. Threat and Risk Assessment Model**

**14.1 Purpose**

This section defines the methodology used to evaluate security threats identified by the Threat Model.

The purpose is to provide a consistent method for determining:

- the likelihood that a threat can be realized;

- the impact if the threat is realized;

- the resulting risk level;

- the priority of security treatment;

- the relationship between risk and security testing;

- the relationship between risk and phase gates;

- the conditions under which residual risk may remain.

The methodology SHALL remain consistent with the risk model established by the Test Specification.

This section defines **risk assessment**.

It does not determine whether a control has been implemented or security-verified.

**14.2 Risk Model**

Security risk SHALL be evaluated using:

**Risk = Likelihood × Impact**

Likelihood represents the estimated probability that the threat can be successfully realized under the defined threat assumptions.

Impact represents the consequence of successful exploitation against the affected asset, security property, or enforcement boundary.

Risk SHALL be evaluated using both dimensions.

A severe consequence SHALL NOT automatically make an event likely.

Likewise, an easy-to-execute attack SHALL NOT automatically make its consequence severe.

**14.3 Risk Dimensions**

Each identified threat SHALL have, at minimum:

- threat identifier;

- affected asset;

- affected security property;

- threat actor;

- attack path;

- likelihood;

- impact;

- resulting risk level;

- applicable controls;

- residual risk where applicable;

- related requirements;

- related security tests;

- current treatment status.

The assessment SHALL be sufficiently explicit that another reviewer can understand why the assigned risk level was selected.

**14.4 Likelihood**

Likelihood SHALL consider the practical conditions necessary for successful exploitation.

The assessment SHALL consider, where applicable:

- attacker capability;

- attacker access;

- required device state;

- required privileges;

- attack complexity;

- required timing;

- required user interaction;

- persistence of the attack opportunity;

- availability of known tooling;

- reproducibility;

- platform dependency;

- effectiveness of existing controls;

- detectability of the attack;

- whether exploitation requires root/system compromise.

Likelihood SHALL describe the threat **within the defined threat boundary**.

A threat requiring a fully compromised operating system SHALL NOT be assigned an ordinary application-attacker likelihood without explicitly accounting for the trust-boundary assumption.

**14.5 Likelihood Calibration**

The project SHALL use the four-level risk model established by the Test Specification.

The qualitative likelihood assessment SHALL be calibrated consistently across threats.

Where a numerical interpretation is useful, the following conceptual scale MAY be used:

| **Likelihood** | **Meaning** |
|----|----|
| **Low** | Exploitation requires unusual conditions, specialized capability, or circumstances unlikely to occur during normal operation |
| **Medium** | Exploitation is practical under identifiable conditions but requires meaningful access, timing, capability, or user interaction |
| **High** | Exploitation is practical for an in-scope attacker with commonly available capability and limited prerequisites |
| **Critical / Very High Condition** | Exploitation is highly practical, repeatable, or broadly accessible and directly threatens a critical security boundary |

The exact numerical interpretation SHALL remain subordinate to the approved Test Specification risk methodology.

Where the Test Specification does not provide quantitative thresholds, the Threat Model SHALL record the rationale rather than inventing unsupported numerical precision.

**14.6 Impact**

Impact SHALL consider the consequence of a successful attack.

Impact factors SHALL include, where applicable:

- confidentiality loss;

- integrity loss;

- authentication bypass;

- authorization bypass;

- availability loss;

- exposure of sensitive metadata;

- exposure of vault content;

- exposure of intruder photos;

- exposure of credentials;

- exposure of cryptographic key material;

- disabling of enforcement;

- destruction or corruption of protected data;

- loss of audit integrity;

- ability to repeat or persist the attack.

Impact SHALL reflect the most significant realistic consequence supported by the attack path.

**14.7 Impact Calibration**

The following qualitative factors SHALL guide impact assessment:

**Low Impact**

The attack causes limited inconvenience or exposes information with little security consequence.

**Medium Impact**

The attack affects a meaningful security property but does not directly compromise the highest-value protected assets.

**High Impact**

The attack can materially compromise protected applications, sensitive metadata, audit information, or availability of the enforcement boundary.

**Critical Impact**

The attack can expose or compromise one or more of the highest-value assets, including:

- PIN credential material;

- database encryption key;

- Android Keystore root material;

- usable vault plaintext;

- large-scale vault data;

- systematic authentication bypass;

- unrestricted defeat of the enforcement mechanism.

The final rating SHALL consider the actual asset and attack path rather than the control name alone.

**14.8 Risk Levels**

The resulting risk SHALL use the four project-defined levels:

- **Critical**

- **High**

- **Medium**

- **Low**

Risk levels SHALL be assigned from the assessed likelihood and impact.

The Threat Model SHALL NOT introduce an independent severity vocabulary that conflicts with the Test Specification.

**14.9 Critical Risk**

A Critical risk represents a threat capable of producing catastrophic compromise of a fundamental security boundary or highest-value asset.

Examples may include:

- recovery of usable vault plaintext through a realistic in-scope attack;

- extraction of cryptographic root material;

- reliable authentication bypass affecting protected assets;

- systematic disabling of enforcement without authentication;

- compromise that allows broad unauthorized access to the protected data store.

Critical risks SHALL receive highest treatment priority.

A Critical risk SHALL NOT be silently carried into a later phase.

Any temporary acceptance SHALL require explicit governance treatment and SHALL comply with mandatory phase-gate requirements.

**14.10 High Risk**

A High risk represents a serious and practical security weakness with significant consequences.

Examples may include:

- reliable protected-app bypass under realistic conditions;

- persistent defeat of lockout;

- unauthorized modification of security policy;

- practical exposure of sensitive metadata;

- reliable manipulation of authentication state;

- a security-critical availability failure that leaves protected applications unguarded.

High risks SHALL normally require remediation or an explicitly approved compensating treatment before the affected security gate is passed.

**14.11 Medium Risk**

A Medium risk represents a meaningful but bounded security exposure.

Examples may include:

- attacks requiring substantial prerequisites;

- limited metadata disclosure;

- attacks against secondary security properties;

- defense-in-depth gaps;

- attacks requiring conditions outside the primary attacker model but still relevant to hardening.

Medium risks SHALL be tracked and treated according to the applicable phase and security objectives.

A Medium rating SHALL NOT mean that the threat is ignored.

**14.12 Low Risk**

A Low risk represents a limited security consequence or an attack requiring sufficiently unusual conditions that immediate remediation is not justified.

Low risks SHALL remain documented where they affect the security model.

A Low rating SHALL NOT be used merely to avoid treatment of an inconvenient finding.

**14.13 Threat Prioritization**

Risk level SHALL determine initial security-treatment priority.

The default priority order SHALL be:

Critical

↓

High

↓

Medium

↓

Low

Within the same risk level, priority SHALL consider:

- exploitability;

- affected asset value;

- breadth of exposure;

- reliability of exploitation;

- ability to detect exploitation;

- persistence;

- remediation complexity;

- dependency on platform behavior;

- whether the weakness can undermine other controls.

**14.14 Security-Critical Assets**

The following assets SHALL receive elevated consideration during impact assessment:

1.  Android Keystore root material;

2.  database encryption key;

3.  PIN credential material;

4.  vault payloads;

5.  intruder photos;

6.  encrypted database and its metadata;

7.  protected-app authorization state;

8.  security and intruder audit records;

9.  enforcement mechanisms.

Compromise of a control protecting one of these assets SHALL be evaluated according to the resulting asset exposure, not merely the local component failure.

**14.15 Security Properties**

Threat assessment SHALL consider the security property being attacked.

The primary properties are:

- **Confidentiality**

- **Integrity**

- **Availability**

- **Authentication**

- **Authorization**

- **Accountability / Audit Integrity**

For App Lock, availability has a special security role.

Loss of availability of the enforcement mechanism may become an effective authorization bypass.

Therefore:

A component whose failure allows protected applications to open without authentication SHALL be treated as a security control, even when its primary engineering classification is reliability.

**14.16 Attack Preconditions**

Each threat SHALL identify meaningful prerequisites. Examples include:

- device already unlocked;
- physical access;
- application installed;
- adb access;
- malicious application installed;
- overlay permission;
- peer Accessibility Service;
- Usage Access revoked, unavailable, or returning stale/incomplete data;
- baseline foreground-detection service stopped or restricted;
- required lock-interface presentation permission or launch capability unavailable;
- optional Accessibility permission revoked or enhancement service unhealthy;
- process killed or device rebooted;
- OEM background restriction;
- root/system privileges;
- compromised application process.

Prerequisites SHALL be explicit. The Threat Model SHALL NOT inflate or reduce risk by silently assuming away conditions specific to the baseline tier, the optional enhancement, or the current delivered implementation.

**14.17 Trust-Boundary Adjustment**

Risk SHALL be evaluated relative to the defined trust boundaries.

An attacker operating outside the application's trust boundary SHALL be distinguished from an attacker who has already compromised a trusted foundation.

For example:

Ordinary App

│

▼

App Sandbox Boundary

│

▼

App Lock

│

▼

Keystore Boundary

│

▼

Android OS / Hardware

An attacker who crosses the application sandbox boundary through root/system compromise is fundamentally different from an ordinary application attacker.

The Threat Model SHALL therefore preserve the distinction between:

- application-level threats;

- platform-boundary threats;

- below-trust-boundary compromise.

**14.18 Existing Controls and Inherent Risk**

Risk assessment SHALL distinguish between:

**Inherent Risk**

The risk before considering existing controls.

**Controlled Risk**

The risk after considering implemented and effective controls.

**Residual Risk**

The remaining risk after applicable controls and accepted mitigations.

A control SHALL only reduce the assessed risk when there is sufficient basis to treat the control as effective.

A planned control SHALL NOT reduce current risk.

An implemented but unverified control SHALL NOT automatically receive the same risk-reduction credit as a security-verified control.

**14.19 Verification-Aware Risk Treatment**

The following model SHALL apply:

Threat

│

▼

Inherent Risk

│

▼

Existing Control

│

├── Planned

│ → No verified risk reduction

│

├── Implemented

│ → Limited / status-dependent confidence

│

├── Regression-Verified

│ → Functional confidence

│

└── Security-Verified

→ Evidence-backed security confidence

│

▼

Residual Risk

The exact risk-reduction treatment SHALL be documented rather than assumed.

**14.20 Compensating Controls**

A compensating control MAY reduce residual risk when the primary control is absent or incomplete. A compensating control SHALL identify: the threat it addresses; the security property protected; the limitation of the primary control; how it reduces exposure; what conditions can defeat it; and whether it has been security-verified.

Examples include:

- watchdog and source-health monitoring when the active foreground detector cannot be automatically restored;
- degradation from an unhealthy optional Accessibility enhancement to a healthy baseline detector;
- lifecycle self-gating when process/session transitions threaten Vault authorization;
- persisted lockout state when process restart could otherwise reset the counter.

Degradation to the baseline tier SHALL only receive compensating-control credit after the baseline and source-selection behavior are implemented and security-verified. A notification alone SHALL NOT be considered equivalent to prevention unless the threat is inherently dependent on user response.

**14.21 Availability Risk as Security Risk**

The availability of the required enforcement path SHALL be explicitly assessed. Threats affecting the following may create direct confidentiality or authorization consequences:

- Usage Access and UsageStatsManager data availability;

- baseline foreground-service execution and sampling;

- detection-source selection and Trigger Processor routing;

- the selected lock-interface presentation mechanism and its required permission;

- optional Accessibility binding or event delivery when the enhancement is enabled;

- watchdog operation, boot re-arm, required permissions, and device-admin protection.

<!-- -->

- Availability Failure → Detection or Presentation Failure → Enforcement Failure → Authentication Bypass → Protected Asset Exposure

The severity SHALL be based on the final realistic consequence, not merely the initial component failure. Loss of the optional Accessibility enhancement alone SHALL NOT be assessed as total enforcement loss after a healthy baseline is implemented and verified.

**14.22 Platform-Dependent Risk**

Where security depends on Android behavior outside the application's direct control, the Threat Model SHALL document: the assumed platform behavior; known platform limitations; affected Android versions; OEM-dependent behavior where known; detection mechanisms; recovery behavior; and remaining exposure.

For the approved target architecture, primary examples include UsageStatsManager and Usage Access behavior, foreground-service restrictions, background-activity-launch restrictions, overlay/presentation permission behavior, and the optional Accessibility framework. For the current delivered build, Accessibility remains the sole implemented detector and its platform dependency remains open.

Platform dependence SHALL NOT automatically classify a threat as low risk.

**14.23 Root/System Risk**

Root and system compromise SHALL be separately identified.

Because the fully compromised operating system is below the application's trust boundary:

- it SHALL not be represented as an ordinary application-level bypass;

- it SHALL not be used to claim that ordinary application controls are ineffective;

- it SHALL remain a best-effort defense area;

- root detection and response SHALL be evaluated as defense-in-depth;

- no application-level control SHALL claim guaranteed protection against OS-level compromise.

If a control specifically improves resistance against root, its value SHALL be recorded without changing the fundamental trust-boundary assumption.

**14.24 Risk Treatment Categories**

Each threat SHALL receive one or more treatment categories:

**Mitigate**

Reduce likelihood or impact through an additional control.

**Prevent**

Eliminate the attack path through architectural or implementation changes.

**Detect**

Identify attempted or successful exploitation.

**Recover**

Restore secure operation or protected data after failure.

**Accept**

Explicitly retain the residual risk under project governance.

**Avoid**

Remove the feature, dependency, or attack surface producing the risk.

The selected treatment SHALL be appropriate to the threat.

Detection SHALL NOT automatically be treated as prevention.

**14.25 Risk Acceptance**

Risk acceptance SHALL be explicit.

A risk SHALL NOT be considered accepted because:

- no developer has addressed it;

- it is listed in a backlog;

- it is planned for a future phase;

- a notification exists;

- the threat is difficult to reproduce;

- no exploit has yet been observed.

Formal acceptance SHALL identify:

- the affected threat;

- current risk;

- residual risk;

- rationale;

- applicable mitigation;

- limitations;

- acceptance authority;

- review trigger;

- expiration/review condition where applicable.

Mandatory gate-blocking risks SHALL NOT be accepted merely to permit phase progression.

**14.26 Risk Reassessment**

Risk SHALL be reassessed when material conditions change. Triggers include:

- security-control or architecture changes;
- new attack surfaces or sensitive assets;
- cryptographic, key-management, authentication, authorization, or storage changes;
- changes to the foreground-detection architecture, Usage Access baseline, detection-source selection, Trigger Processor, or lock-interface presentation mechanism;
- changes to the optional Accessibility enhancement;
- major Android-version or OEM behavior changes;
- new or removed security requirements;
- security-relevant dependency changes;
- historical vulnerability rediscovery, penetration-test findings, or security incidents;
- phase-gate reviews.

Risk SHALL NOT be assumed unchanged merely because the threat identifier remains the same or because a target-architecture control has been approved but not implemented.

**14.27 Historical Failure Influence**

Historical failures SHALL influence risk assessment.

A previously observed bypass demonstrates that the attack path is not merely theoretical.

Where a historical failure has been successfully remediated, the historical evidence SHALL inform:

- threat plausibility;

- attack-path design;

- security-test priority;

- control selection;

- regression coverage.

The existence of a historical fix SHALL not itself reduce risk unless the current implementation and evidence demonstrate that the fix remains effective.

**14.28 Risk and Security Testing**

Risk SHALL directly influence security-test priority.

At minimum:

| **Risk**     | **Testing Expectation**                                 |
|--------------|---------------------------------------------------------|
| **Critical** | Dedicated security testing required; highest priority   |
| **High**     | Dedicated security testing required                     |
| **Medium**   | Security testing appropriate to the threat and phase    |
| **Low**      | Testing proportionate to consequence and exploitability |

Functional regression testing SHALL not automatically satisfy dedicated security testing for Critical or High threats.

Security testing SHALL attempt to exercise the actual attack path rather than merely confirm that the intended control exists.

**14.29 Risk and Phase Gates**

Risk level SHALL interact with phase gates.

A phase SHALL NOT advance when an unresolved risk:

- violates a mandatory gate criterion;

- represents an unmitigated authentication bypass;

- exposes a critical protected asset through a practical in-scope attack;

- invalidates a mandatory security assumption;

- demonstrates that a required security control is ineffective.

Other residual risks MAY be carried forward only when explicitly permitted by the applicable phase gate and project governance.

**14.30 Risk Register Consistency**

Threat Model risk entries SHALL remain consistent with the project's internal risk register.

Where the same risk is represented in both locations:

- identifiers SHALL remain traceable;

- risk ratings SHALL not silently diverge;

- status SHALL remain synchronized;

- evidence references SHALL remain valid;

- changes SHALL be reflected in both artifacts.

If the Threat Model identifies a security risk absent from the existing register, the risk SHALL be routed into the project's established governance process.

**14.31 Risk Assessment Record**

Each formal threat assessment SHALL be representable using the following minimum structure:

| **Field**               | **Required**         |
|-------------------------|----------------------|
| Threat ID               | Yes                  |
| Threat Description      | Yes                  |
| Threat Actor            | Yes                  |
| Asset                   | Yes                  |
| Security Property       | Yes                  |
| Preconditions           | Yes                  |
| Attack Path             | Yes                  |
| Existing Controls       | Yes                  |
| Inherent Likelihood     | Yes                  |
| Inherent Impact         | Yes                  |
| Inherent Risk           | Yes                  |
| Control Status          | Yes                  |
| Residual Likelihood     | Yes, when applicable |
| Residual Impact         | Yes, when applicable |
| Residual Risk           | Yes, when applicable |
| Treatment               | Yes                  |
| Security Test Reference | When applicable      |
| Evidence Reference      | When available       |
| Related Requirement     | When applicable      |
| Review Trigger          | Yes                  |
| Current Status          | Yes                  |

**14.32 Risk Assessment Integrity Rules**

The following rules SHALL be mandatory:

1.  Planned controls SHALL NOT reduce current risk.

2.  Unverified controls SHALL NOT be represented as security-verified.

3.  Functional verification SHALL NOT automatically equal security verification.

4.  Detection SHALL NOT automatically equal prevention.

5.  Notification SHALL NOT automatically equal mitigation.

6.  Root/system compromise SHALL remain below the guaranteed application trust boundary.

7.  Historical fixes SHALL remain traceable to their original failures.

8.  Risk ratings SHALL have documented rationale.

9.  Risk acceptance SHALL be explicit.

10. Risk SHALL be reassessed after material security changes.

11. Mandatory gate conditions SHALL NOT be bypassed through informal risk acceptance.

12. Risk records SHALL remain synchronized with applicable governance artifacts.

**14.33 Risk Model Invariant**

The Threat Model SHALL preserve the following lifecycle:

Threat Identification

↓

Asset Identification

↓

Attack-Path Analysis

↓

Inherent Risk

↓

Control Identification

↓

Control Effectiveness

↓

Residual Risk

↓

Treatment

↓

Security Verification

↓

Reassessment

No stage SHALL silently substitute for another.

In particular:

**Risk acceptance is not verification.**

**Implementation is not verification.**

**Verification is not risk acceptance.**

These are separate security activities.

**14.34 Section 14 Completion Criteria**

Section 14 is complete when:

- the project risk equation is defined;

- likelihood and impact are separately assessed;

- the four project risk levels are preserved;

- risk-rating rationale is required;

- asset value is reflected in impact;

- attacker prerequisites are explicit;

- trust-boundary assumptions affect assessment;

- planned controls receive no current security credit;

- implementation status is distinguished from verification;

- residual risk is explicitly represented;

- compensating controls are distinguishable from primary controls;

- availability failures with security consequences are treated as security risks;

- root/system compromise remains separately classified;

- risk treatment categories are defined;

- risk acceptance is explicit;

- risk reassessment triggers are defined;

- risk influences security-test priority;

- risk interacts with phase gates;

- Threat Model and risk-register consistency is required;

- historical failures inform risk assessment without replacing it;

- no risk can silently disappear because a control is planned or a phase changes.

**14.35 Boundary to Section 15**

Section 14 defines **how security risk is assessed and prioritized**. It does not define how the Threat Model is kept current as the system evolves.

Section 15 therefore establishes the **Continuous Threat Modeling and Change Management** model — the reassessment triggers, change classification, cross-document synchronization, ADR and traceability impact, and Threat Model versioning through which the security analysis is maintained as requirements, architecture, controls, dependencies, and Android platform behavior change.
