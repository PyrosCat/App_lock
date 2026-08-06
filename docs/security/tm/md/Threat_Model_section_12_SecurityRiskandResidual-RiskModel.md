**12. Security Risk and Residual-Risk Model**

**12.1 Purpose**

This section defines how security risks identified by the Threat Model SHALL be assessed, prioritized, treated, accepted, monitored, and reassessed throughout the project lifecycle.

The risk model SHALL prevent security weaknesses from being hidden by:

- implementation status;

- phase progression;

- functional test results;

- planned remediation;

- compensating controls;

- documentation changes;

- informal acceptance.

The fundamental rule is:

**A security risk remains a security risk until its exposure is eliminated, sufficiently reduced, or explicitly accepted through the project's approved governance process.**

**12.2 Risk Model**

The project SHALL use the established risk methodology:

**Risk = Likelihood × Impact**

Risk SHALL be evaluated using the four established levels:

- **Critical**

- **High**

- **Medium**

- **Low**

The Threat Model SHALL use the same conceptual risk scale as the Test Specification and internal engineering risk register.

Risk assessment SHALL consider the complete attack path rather than evaluating individual controls in isolation.

**12.3 Likelihood**

Likelihood represents the plausibility that the identified threat can be successfully exercised under the assumptions of the Threat Model.

Likelihood assessment SHALL consider, where applicable:

- attacker capability;

- attacker access;

- required privileges;

- physical access requirements;

- attack complexity;

- required timing;

- prerequisite conditions;

- availability of attack tooling;

- platform restrictions;

- reproducibility;

- existing compensating controls;

- attacker knowledge required.

Likelihood SHALL describe the threat as it exists against the actual architecture, not an abstract theoretical attack.

**12.4 Impact**

Impact represents the consequence of successful exploitation.

Impact SHALL consider:

- confidentiality loss;

- integrity loss;

- availability loss;

- authentication bypass;

- authorization bypass;

- privacy exposure;

- exposure of sensitive metadata;

- loss of vault content;

- exposure of intruder photographs;

- credential compromise;

- cryptographic key compromise;

- disabling of enforcement;

- persistent compromise;

- inability to recover protected data.

Impact SHALL consider the affected asset and the consequence of losing its security property.

**12.5 Asset-Sensitive Risk Assessment**

Risk SHALL be evaluated in relation to the asset affected.

The highest-value assets include:

1.  PIN credential and credential-protection state;

2.  Android Keystore root of trust;

3.  database passphrase;

4.  vault payloads;

5.  intruder photographs;

6.  encrypted database and associated metadata;

7.  enforcement availability and security state.

An attack that affects enforcement availability may have high security impact even if it does not directly expose stored data.

An attack that exposes only low-sensitivity metadata SHALL not automatically receive the same impact classification as an attack exposing vault plaintext.

**12.6 Threat-Path Risk**

Risk SHALL be evaluated across the complete path:

Attacker

↓

Capability

↓

Attack Surface

↓

Security Boundary

↓

Control

↓

Failure

↓

Asset

↓

Impact

A threat SHALL not be dismissed merely because an individual step appears difficult.

Conversely, an attack SHALL not be rated Critical merely because it is theoretically possible if the required attack conditions place it outside the defined threat boundary.

**12.7 Initial Risk vs Residual Risk**

The Threat Model SHALL distinguish:

**Inherent Risk**

Risk that exists before considering mitigating controls.

**Controlled Risk**

Risk remaining after considering implemented controls.

**Residual Risk**

Risk that remains after all currently accepted controls and compensating measures are considered.

The preferred representation is:

Inherent Threat

↓

Primary Control

↓

Control Effectiveness

↓

Compensating Controls

↓

Residual Risk

Residual risk SHALL be the value used for phase-gate and risk-acceptance decisions.

**12.8 Control Effectiveness**

A control SHALL NOT automatically receive full risk-reduction credit merely because it exists.

Effectiveness SHALL consider its actual verification state.

For example:

| **Control State**       | **Risk Treatment**                           |
|-------------------------|----------------------------------------------|
| Planned                 | No implementation credit                     |
| Implemented             | Limited evidence of existence                |
| Regression-Verified     | Functional confidence                        |
| Security-Verified       | Security evidence may support risk reduction |
| Failed                  | Do not rely on control                       |
| Reverification Required | Prior evidence requires reassessment         |

A planned control SHALL NOT be counted as an effective mitigation.

**12.9 Compensating Controls**

A compensating control is a separate mechanism that reduces exposure created by a missing, incomplete, or weakened primary control.

Examples may include:

- persisted lockout state compensating for process restarts;

- encrypted storage compensating for raw filesystem exposure;

- self-gating compensating for the possibility of returning to App Lock UI after backgrounding;

- regression coverage compensating for known historical bypasses by detecting recurrence.

A compensating control SHALL be explicitly identified.

The project SHALL NOT describe a compensating control as though it were the missing primary control.

**12.10 Compensating-Control Requirements**

A compensating control SHALL identify:

- threat addressed;

- primary control it supplements or replaces temporarily;

- mechanism;

- limitations;

- verification state;

- residual risk;

- conditions under which it remains effective.

A compensating control SHALL itself be tested when it materially contributes to the security claim.

**12.11 Risk Treatment Categories**

Every material security risk SHALL have a defined treatment.

The permitted treatment categories are:

**Mitigate**

Implement or strengthen controls to reduce likelihood and/or impact.

**Avoid**

Remove the functionality, attack surface, or architecture that creates the risk.

**Transfer**

Move an operational responsibility or consequence to another controlled party where appropriate.

Transfer SHALL NOT be used to pretend that an application-level security obligation has disappeared.

**Accept**

Explicitly acknowledge and authorize the residual risk.

**Monitor**

Maintain the risk because current information is insufficient or because the risk is expected to change.

Monitoring SHALL NOT substitute for remediation where a mandatory control is required.

**12.12 Mandatory Risk Treatment**

Critical and High security risks SHALL receive explicit treatment decisions.

A Critical or High risk SHALL NOT remain in an undefined state such as:

- "known";

- "under consideration";

- "future work";

- "probably acceptable";

- "will fix later."

It SHALL have:

- owner;

- treatment;

- current status;

- required action;

- verification condition;

- review trigger.

**12.13 Critical Risk**

A Critical risk represents a credible threat with potentially catastrophic consequences to a core security boundary or highest-value asset.

Examples include credible paths to:

- complete authentication bypass;

- unrestricted vault disclosure;

- cryptographic root-of-trust compromise;

- credential compromise enabling persistent unauthorized access;

- unrestricted disabling of enforcement.

Critical risks SHALL normally block a phase gate unless an approved governance mechanism explicitly determines otherwise and the project's mandatory security requirements permit such treatment.

**12.14 High Risk**

A High risk represents a significant security exposure affecting a major asset or security boundary.

Examples may include:

- reliable protected-app bypass under realistic attacker conditions;

- unauthorized vault access under bounded conditions;

- persistent disabling of enforcement;

- material exposure of sensitive audit information;

- a security-critical service failure that reliably leaves protection disabled.

High risks SHALL require explicit remediation or documented risk acceptance.

A High risk SHALL NOT disappear because the related feature has moved to another phase.

**12.15 Medium Risk**

A Medium risk represents meaningful but bounded security exposure.

Examples may include:

- limited metadata disclosure;

- difficult-to-exploit authorization inconsistencies;

- defense-in-depth weaknesses;

- bounded platform-specific exposure.

Medium risks SHALL remain tracked and SHALL have an appropriate treatment decision.

A Medium risk may be carried across a phase boundary when:

- the phase gate permits it;

- the exposure is understood;

- the risk is documented;

- ownership exists;

- required remediation or review conditions are defined.

**12.16 Low Risk**

A Low risk represents limited exposure with relatively low consequence or difficult exploitation under the defined threat assumptions.

Low risks SHALL remain visible where they affect a security property, but may normally be handled through routine backlog and monitoring mechanisms.

Low risk classification SHALL NOT be used to hide an issue affecting a Critical asset.

**12.17 Risk Classification Calibration**

The existing Test Specification establishes the four risk levels but does not fully quantify every likelihood and impact boundary.

Therefore, the Threat Model SHALL preserve the established four-band model without inventing unsupported numerical thresholds.

Where calibration becomes necessary, the project SHOULD establish explicit likelihood and impact rubrics through an approved governance decision.

Until then, assessment SHALL be documented with its reasoning.

**12.18 Risk Record**

Each material security risk SHALL contain, at minimum:

- unique risk identifier;

- threat identifier;

- affected asset;

- affected security property;

- attacker capability;

- attack path;

- inherent likelihood;

- inherent impact;

- inherent risk;

- existing controls;

- control verification status;

- compensating controls;

- residual likelihood;

- residual impact;

- residual risk;

- treatment;

- owner;

- status;

- related requirements;

- related tests;

- evidence reference;

- review triggers;

- acceptance/decision reference where applicable.

**12.19 Risk Status**

Security risks SHALL use controlled status values.

**Open**

The risk is active and requires treatment or monitoring.

**Mitigating**

Remediation is actively being implemented.

**Verification Pending**

A mitigation has been implemented but sufficient evidence has not yet established its effectiveness.

**Accepted**

Residual risk has been formally accepted.

**Closed**

The threat exposure has been eliminated or reduced to a state where the risk no longer applies.

**Reopened**

A previously closed or accepted risk has become relevant again because of a change, new threat, failed control, or new evidence.

**12.20 Acceptance Is Not Closure**

Risk acceptance SHALL NOT mean that the underlying risk has disappeared.

For example:

Security Weakness

↓

Risk Accepted

↓

Risk remains

↓

Monitoring / Review

The risk may be considered administratively accepted, but the underlying exposure remains part of the security posture.

Only elimination or sufficient reduction of the exposure can produce a true **Closed** state.

**12.21 Risk Acceptance Authority**

Risk acceptance SHALL occur through the project's established governance mechanism.

The Threat Model SHALL record the resulting decision reference.

An engineer marking a risk "acceptable" in a working document SHALL NOT by itself constitute formal risk acceptance.

The acceptance record SHALL identify:

- accepted risk;

- affected asset;

- rationale;

- residual exposure;

- limitations;

- affected requirements;

- conditions;

- review triggers;

- approving authority;

- date/version.

**12.22 Prohibited Risk Acceptance**

Risk acceptance SHALL NOT be used to bypass:

- mandatory security requirements;

- mandatory phase-gate criteria;

- explicit architectural constraints;

- legal/compliance requirements where applicable;

- a known Critical security boundary failure.

If a mandatory requirement cannot be satisfied, the project SHALL use the established deviation/decision process rather than silently converting the failure into "accepted risk."

**12.23 Risk Ownership**

Every material risk SHALL have an identifiable owner.

The owner is responsible for ensuring that:

- the risk remains accurately described;

- treatment remains current;

- evidence is collected;

- review triggers are monitored;

- changes are evaluated;

- acceptance does not silently expire;

- the risk is closed only when closure criteria are met.

Ownership SHALL remain associated with the risk even when implementation responsibility changes.

**12.24 Risk Review Triggers**

A risk SHALL be reassessed when:

- the threat changes;

- attacker capability changes;

- an affected asset changes;

- a control changes;

- a compensating control changes;

- verification status changes;

- a security dependency changes;

- Android behavior changes;

- a vulnerability changes exploitability;

- a security incident occurs;

- a new bypass is discovered;

- a phase gate is reached;

- the acceptance period or review condition is reached.

**12.25 Accessibility Enforcement Risk**

The existing accessibility risk SHALL remain a first-class risk.

The risk includes:

1.  Android Restricted Settings limiting accessibility grants;

2.  store-policy restrictions affecting distribution;

3.  accessibility service state appearing enabled while event delivery is impaired;

4.  force-stop and OEM behavior affecting enforcement;

5.  potential protection gaps caused by loss of detection.

The risk SHALL not be represented as merely a reliability concern.

Because the accessibility service is part of the enforcement path, loss of reliable detection can become a security failure.

**12.26 Enforcement Availability Risk**

The application treats continuous enforcement as a security asset.

Therefore, the following SHALL be assessed as security-relevant:

- accessibility service loss;

- watchdog loss;

- boot re-arm failure;

- permission revocation;

- process death;

- force-stop;

- OEM background restrictions;

- overlay/UI failure;

- failure to present the authentication screen.

A control that detects failure after protected content has already become accessible SHALL be treated as **detection**, not **prevention**.

**12.27 Fail-Open Risk**

The current architecture contains an important accepted limitation:

Loss of the AppDetectionService can cause protected applications to become accessible without App Lock authentication.

The watchdog and notification mechanism reduces detection time but does not eliminate the exposure.

Therefore:

Detection

≠

Prevention

≠

Fail-Closed Enforcement

The Threat Model SHALL preserve this distinction.

The exposure window SHALL be treated as residual risk until the architecture changes.

**12.28 Vault Key / PIN Separation Risk**

The Threat Model SHALL explicitly track the distinction between UI authorization and cryptographic protection.

The PIN controls the App Lock authorization boundary.

The Android Keystore protects the cryptographic keys used for stored data.

Therefore:

An attacker who obtains equivalent in-process/root-level access to the application environment may be able to access vault plaintext without knowing the PIN.

This is not a claim that ordinary applications can read the vault.

It is a consequence of the current trust boundary.

The risk SHALL be evaluated against the defined attacker classes.

It SHALL NOT be incorrectly classified as an authentication bypass against the normal application-sandbox attacker.

**12.29 Keystore Invalidation Risk**

Keystore invalidation SHALL remain a tracked risk until recovery behavior is implemented and verified.

Current consequence:

- credential store may become inaccessible;

- database key may become inaccessible;

- vault encryption material may become inaccessible;

- protected data may become unrecoverable.

The risk is therefore primarily:

- availability;

- recoverability;

- potential permanent data loss.

It SHALL NOT be incorrectly described as a confidentiality bypass.

**12.30 Overlay and Tapjacking Risk**

The authentication UI currently lacks complete anti-overlay/tapjacking protections.

The risk SHALL account for:

- malicious overlays;

- obscured security UI;

- injected interactions;

- UI spoofing;

- peer accessibility services.

FLAG_SECURE SHALL receive credit only for reducing screenshot/screen-recording exposure.

It SHALL NOT receive credit for preventing:

- tapjacking;

- overlays;

- event injection;

- UI spoofing.

**12.31 Root Attacker Risk**

Root/system compromise SHALL remain outside the guaranteed application security boundary.

Root detection and configurable response may reduce exposure, but SHALL be classified as defense-in-depth.

The project SHALL NOT use root-detection implementation as evidence that root-level attackers are fully mitigated.

The residual risk against a fully compromised operating system remains fundamentally different from the risk posed by an ordinary application attacker.

**12.32 Dependency and Supply-Chain Risk**

Dependencies SHALL be treated as part of the security attack surface.

Risk assessment SHALL consider:

- malicious dependency behavior;

- vulnerable dependencies;

- dependency update changes;

- transitive dependencies;

- build failures affecting security controls;

- cryptographic dependency changes;

- release-build integrity.

Dependency controls include:

- auditing;

- pinning/governance;

- static analysis;

- release-build verification;

- change-triggered reassessment.

A dependency SHALL not be considered safe merely because it previously passed testing.

**12.33 Security Debt as Risk**

Security debt SHALL be treated as a risk multiplier where it creates uncertainty about actual protection.

Examples include:

- controls implemented but not security-verified;

- missing Threat Model coverage;

- missing Secure Coding Standard;

- missing anti-tapjacking defense;

- missing Keystore recovery;

- missing tamper detection;

- missing root response;

- incomplete secure-memory controls;

- incomplete audit-log integrity.

Security debt SHALL remain visible even when it is scheduled for later work.

**12.34 Risk and Phase Advancement**

Phase advancement SHALL consider residual security risk.

Residual Risk

↓

Mandatory Gate Criteria

↓

Risk Treatment

↓

Governance Decision

↓

Phase Advancement

A risk SHALL NOT be automatically carried forward merely because its remediation is scheduled for a later phase.

The gate SHALL explicitly determine whether the remaining risk is compatible with the next phase.

**12.35 Risk Cannot Be Resolved by Documentation Alone**

Updating the Threat Model, RTM, ADR, or risk register does not itself reduce technical risk.

Documentation can:

- identify risk;

- clarify assumptions;

- record treatment;

- establish ownership;

- preserve evidence.

It cannot substitute for an actual security control when a control is required.

**12.36 Risk Cannot Be Resolved by Testing Alone**

A failed security control does not become safe merely because the test demonstrates the failure.

Testing provides knowledge.

Risk reduction requires:

- remediation;

- effective compensating control;

- removal of the attack surface;

- or explicit risk acceptance.

A security test that discovers a bypass SHALL therefore create or update the corresponding risk.

**12.37 Risk and Verification Relationship**

Risk and verification SHALL remain separate dimensions.

For example:

| **Implementation** | **Verification**     | **Risk Interpretation**            |
|--------------------|----------------------|------------------------------------|
| Not implemented    | None                 | No mitigation credit               |
| Implemented        | None                 | Limited mitigation credit          |
| Implemented        | Regression-Verified  | Functional confidence              |
| Implemented        | Security-Verified    | Evidence-based mitigation          |
| Implemented        | Failed security test | Control ineffective until resolved |
| Changed            | Prior verification   | Reverification required            |

Security risk SHALL be calculated using the actual effective control state.

**12.38 Risk Escalation**

A risk SHALL be escalated when:

- likelihood increases materially;

- impact increases materially;

- a primary control fails;

- a compensating control fails;

- a new exploit path is discovered;

- a previously out-of-scope attacker becomes relevant;

- a platform change invalidates an assumption;

- a phase gate approaches with unresolved blocking exposure.

Escalation SHALL update affected:

- Threat Model entries;

- RTM entries;

- ADRs where architecture changes;

- test plans;

- security status;

- phase-gate evidence.

**12.39 Risk Closure Criteria**

A risk may be closed only when one of the following is demonstrated:

**Threat Eliminated**

The affected functionality or attack surface no longer exists.

**Vulnerability Eliminated**

The attack path has been remediated and security verification demonstrates that the vulnerability is no longer exploitable under the defined threat model.

**Risk Reduced**

The exposure has been reduced sufficiently that the remaining risk no longer meets the criteria for an active material risk.

Closure SHALL require evidence.

**12.40 Reopening Closed Risks**

A closed risk SHALL be reopened when:

- the affected implementation changes;

- the attack surface returns;

- a new bypass is discovered;

- a dependency changes the security assumption;

- Android platform behavior changes materially;

- new evidence demonstrates that the previous mitigation was insufficient.

Risk closure SHALL therefore never be treated as permanent immunity.

**12.41 Residual-Risk Register**

The Threat Model SHALL maintain visibility into unresolved material residual risks.

At minimum, the current model SHALL preserve visibility of:

| **Risk Area** | **Current Treatment** |
|----|----|
| Accessibility enforcement reliability | Mitigate / Monitor |
| Fail-open enforcement | Known residual exposure |
| Peer accessibility interference | Best-effort |
| Malicious overlays / tapjacking | Remediation required |
| Keystore invalidation | Remediation required |
| PIN-independent cryptographic keys | Accepted architectural property; evaluate by attacker boundary |
| Root/system compromise | Out of guaranteed boundary; best-effort |
| Missing security verification | Phase 1 remediation |
| Root/tamper/debug controls | Deferred defense-in-depth |
| Security debt | Track and reassess |

The exact severity of each entry SHALL be determined through the approved risk methodology and evidence, rather than assumed solely from this table.

**12.42 Risk Model Invariants**

The following invariants SHALL govern all security-risk decisions.

**Invariant 1 — No Planned-Control Credit**

A planned control SHALL provide zero implementation mitigation credit.

**Invariant 2 — No Verification Without Evidence**

A control SHALL NOT receive Security-Verified status without evidence.

**Invariant 3 — No Silent Acceptance**

Material residual risk SHALL have an explicit treatment decision.

**Invariant 4 — No Risk Disappearance**

Moving work to another phase SHALL NOT remove the risk.

**Invariant 5 — No Documentation-Only Remediation**

Updating documentation SHALL NOT be treated as technical remediation.

**Invariant 6 — No Permanent Verification**

Materially changed controls SHALL require reassessment.

**Invariant 7 — Asset-Sensitive Impact**

Impact SHALL reflect the actual affected asset.

**Invariant 8 — Boundary-Aware Risk**

Attacks outside the guaranteed trust boundary SHALL remain explicitly classified rather than silently assumed away.

**12.43 Section 12 Completion Criteria**

Section 12 is complete only when:

- the approved likelihood × impact model is preserved;

- Critical/High/Medium/Low levels are standardized;

- inherent and residual risk are separated;

- control effectiveness depends on actual verification state;

- compensating controls are explicitly identified;

- risk treatment categories are standardized;

- material risks have owners;

- acceptance is separated from closure;

- mandatory requirements cannot be bypassed through informal risk acceptance;

- accessibility enforcement remains a security risk;

- fail-open enforcement remains explicitly represented;

- PIN/UI authorization and cryptographic key protection remain distinct;

- Keystore invalidation remains represented;

- overlay/tapjacking remains represented;

- root/system compromise remains outside the guaranteed application boundary;

- security debt remains visible;

- phase advancement considers residual risk;

- risk closure requires evidence;

- closed risks can be reopened;

- risk and verification remain separate but traceable dimensions.

**12.44 Boundary to Section 13**

Section 12 defines **how security risk is assessed, treated, accepted, monitored, and closed**.

Section 13 defines the **Security Assumptions, Trust Boundaries, and Explicit Non-Goals**, establishing exactly what the Threat Model trusts, what it does not trust, what the application guarantees, and where the security claims deliberately stop.
