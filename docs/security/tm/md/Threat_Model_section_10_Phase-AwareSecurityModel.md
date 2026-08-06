**10. Phase-Aware Security Model**

**10.1 Purpose**

This section defines how security posture, control maturity, residual risk, and project phase advancement SHALL be evaluated throughout the project lifecycle.

The Threat Model SHALL distinguish between:

- what the architecture requires;

- what the current implementation contains;

- what has been functionally verified;

- what has been security-verified;

- what remains planned;

- what risk is temporarily accepted;

- what conditions must be satisfied before phase advancement.

The purpose is to prevent **security drift** during implementation.

A control SHALL NOT become "effective" merely because:

- the corresponding requirement exists;

- implementation has started;

- code has been merged;

- a functional test passes;

- a regression test passes;

- an Architecture Decision Record exists;

- a future implementation is described in documentation.

Security status SHALL be established from evidence.

**10.2 Phase Security Model**

The project lifecycle is treated as a sequence of controlled security states.

Phase 0

Foundation

│

│ Foundation Gate

▼

Phase 1

Core Security Platform

│

│ Core Security Gate

▼

Phase 2+

Feature / Automation / Hardening

│

│ Production / Security Gates

▼

Release Readiness

Each phase has:

1.  required security properties;

2.  required engineering controls;

3.  required verification evidence;

4.  explicitly accepted residual risks;

5.  prohibited assumptions;

6.  entry and exit criteria.

A later phase SHALL NOT be considered to retroactively satisfy an earlier phase gate.

**10.3 Phase 0 — Foundation**

**10.3.1 Security Objective**

The purpose of Foundation is to establish a stable engineering base without weakening the security behavior inherited from the existing implementation.

Foundation is therefore primarily a **security-preservation phase**, not the phase in which the complete security architecture is introduced.

The fundamental security invariant is:

Foundation changes SHALL NOT regress the existing authentication, authorization, encryption, or enforcement boundaries.

**10.3.2 Mandatory Foundation Controls**

Before exiting Phase 0, the project SHALL establish the engineering controls required by the approved Foundation exit criteria.

These include:

- successful automated builds;

- successful minified/release builds;

- static analysis;

- architecture-rule enforcement;

- dependency auditing;

- dependency governance;

- dependency-injection cleanup;

- removal of the legacy service-locator architecture;

- hardened database migration behavior;

- standing device regression coverage for the existing lock boundary;

- synchronized requirements traceability;

- synchronized Architecture Decision Records;

- synchronized change records;

- documented phase-gate review.

The database migration hardening SHALL remove reliance on destructive fallback behavior where required by the approved Foundation design.

**10.3.3 Foundation Security Boundary**

The Foundation phase SHALL preserve, at minimum:

- PIN authentication;

- brute-force lockout;

- protected-app enforcement;

- App Lock session behavior;

- vault authentication gating;

- encrypted database storage;

- encrypted vault storage;

- intruder-photo protection;

- security-sensitive component boundaries;

- screen-capture protections;

- reboot/session separation.

A Foundation change that weakens any of these controls SHALL be treated as a security-impacting architectural change.

**10.3.4 Foundation Exit Condition**

Phase 0 SHALL NOT exit solely because the planned engineering tasks are complete.

The exit requires evidence that the migration has not materially weakened the established security boundary.

The project SHALL therefore demonstrate:

Foundation Change

│

▼

Build / Static / Architecture Checks

│

▼

Regression Verification

│

▼

Security-Impact Assessment

│

▼

Foundation Gate Review

│

▼

Phase 1 Entry

The Threat Model itself may be incomplete during Foundation, as defined by the approved project sequencing.

However, this SHALL be treated as a **known process-state limitation**, not evidence that the Threat Model is unnecessary.

**10.4 Phase 1 — Core Security Platform**

**10.4.1 Security Objective**

Phase 1 establishes the formal security architecture and security-verification framework.

This is the phase in which the project transitions from:

"Existing security behavior is preserved and regression-tested"

to:

"Security behavior is formally threat-modeled, controlled, tested, and evidenced."

**10.4.2 Mandatory Phase 1 Deliverables**

Phase 1 SHALL establish, at minimum:

- approved Threat Model;

- Secure Coding Standard;

- centralized security-services architecture;

- centralized authorization service;

- security startup health checks;

- security permission verification;

- formal security-control classification;

- threat-to-control traceability;

- threat-driven security tests;

- security-test evidence requirements;

- security-specific verification status;

- key-management hardening and approved key-management design;

- security-relevant Architecture Decision Records.

The centralized security architecture SHALL NOT be treated as an entry requirement to Phase 1.

It is a Phase 1 deliverable.

**10.5 Phase 1 Security Gate**

The Phase 1 gate is the primary security maturity transition.

The gate SHALL establish that:

1.  the Threat Model is approved;

2.  the Secure Coding Standard is approved;

3.  security controls are mapped to threats;

4.  security requirements are classified;

5.  security tests exist for material security controls;

6.  security evidence requirements are defined;

7.  critical security gaps are either remediated or explicitly accepted through the project's governance process;

8.  previously regression-verified controls have been re-evaluated under the security-verification model;

9.  security-sensitive architectural decisions are recorded;

10. residual risks are known and owned.

The gate SHALL NOT require every planned hardening feature to already exist.

It SHALL require that the project can now **measure and control security maturity explicitly**.

**10.6 Phase 1 Security Reclassification**

The Phase 1 Threat Model SHALL trigger reclassification of existing implementation status.

The previous state:

Implemented and regression-verified

SHALL NOT automatically become:

Security-verified.

Instead:

Existing Regression Evidence

│

▼

Threat Model Mapping

│

▼

Security Test Design

│

▼

Security Test Execution

│

▼

Evidence Review

│

▼

Security Verification

This prevents historical functional verification from being incorrectly promoted to security assurance.

**10.7 Phase 2 — Core Features**

Phase 2 introduces or expands user-facing protected functionality.

Security requirements SHALL remain subordinate to the established Phase 1 security architecture.

New functionality SHALL NOT introduce an independent authentication, encryption, authorization, or key-management mechanism without an approved architectural decision.

Security-sensitive functionality introduced in this phase SHALL inherit:

- centralized authorization;

- centralized security services;

- established cryptographic controls;

- logging/security-event requirements;

- security-test requirements;

- traceability requirements.

A feature cannot create its own security boundary simply because it is implemented in a separate module.

**10.8 Phase 2 Security Conditions**

Before a Phase 2 security-sensitive feature is considered complete:

- its threats SHALL be identified;

- its controls SHALL be documented;

- affected requirements SHALL be traced;

- affected Architecture Decision Records SHALL be updated where necessary;

- security tests SHALL exist where the feature creates a security boundary;

- security evidence SHALL identify the tested build/configuration;

- residual risk SHALL be recorded.

Changes to vault access, authentication, protected-app policies, security settings, or sensitive storage SHALL receive elevated security review.

**10.9 Phase 3 — Automation**

Automation introduces additional environmental inputs and therefore expands the security decision surface.

Examples include:

- scheduling;

- Wi-Fi conditions;

- Bluetooth conditions;

- location conditions;

- rules-engine behavior;

- background execution.

Automation SHALL NOT silently weaken the authentication boundary.

A rule that changes a security state SHALL be treated as security-sensitive.

For example:

Automation Rule

│

▼

Security-Relevant State Change

│

▼

Authorization / Policy Evaluation

│

▼

Allowed State Transition

An automation trigger SHALL NOT be considered equivalent to user authentication unless explicitly authorized by the approved security architecture.

**10.10 Phase 4 — Production Hardening**

Phase 4 strengthens operational resilience and observability.

Security-relevant objectives include:

- security event logging;

- metrics;

- crash recovery;

- background-worker reliability;

- watchdog resilience;

- operational detection;

- security-health visibility;

- failure recovery.

Operational telemetry SHALL NOT expose protected content, credentials, key material, vault filenames, or other confidential information.

Production hardening SHALL improve the ability to detect security failures without creating a secondary information-disclosure channel.

**10.11 Phase 5 — Security Hardening**

Phase 5 addresses advanced defense-in-depth controls.

The planned scope includes:

- root detection;

- configurable root response;

- tamper detection;

- anti-debugging;

- certificate/integrity protections where applicable;

- secure memory handling;

- penetration testing;

- advanced security validation.

These controls SHALL be treated as defense-in-depth.

They SHALL NOT be used to redefine the application's guaranteed security boundary against a fully compromised operating system.

**10.12 Root and System Compromise Across Phases**

The root/system-compromised threat remains outside the guaranteed security boundary throughout all phases.

Phase 5 may improve resistance and detection, but SHALL NOT create a claim that App Lock can cryptographically guarantee confidentiality against an attacker who controls the operating system.

The model therefore distinguishes:

| **Condition**                            | **Security Claim**             |
|------------------------------------------|--------------------------------|
| Ordinary application attacker            | Primary defense required       |
| Physical attacker with unlocked device   | Primary defense required       |
| adb attacker without root                | Bounded defense required       |
| Peer accessibility attacker              | Best-effort defense            |
| Rooted device                            | Best-effort detection/response |
| Fully compromised OS                     | No application-level guarantee |
| Compromised signing/build infrastructure | Process-level mitigation only  |

This distinction SHALL remain unchanged unless formally revised through an approved architectural decision.

**10.13 Deferred Controls**

A deferred control SHALL have all of the following:

- defined security purpose;

- identified threat(s);

- planned implementation phase;

- current status;

- rationale for deferral;

- interim mitigation, if one exists;

- residual risk;

- owner or responsible workstream where applicable.

A deferred control SHALL NOT be omitted from the Threat Model merely because implementation is scheduled for a later phase.

**10.14 Safe Deferral Criteria**

Deferral may be accepted when one or more of the following apply:

1.  the control is defense-in-depth against an attacker outside the primary guaranteed boundary;

2.  the existing architecture already provides the primary security property;

3.  the phase intentionally establishes the control before implementing additional functionality;

4.  the control protects a feature that does not yet exist;

5.  a compensating control provides sufficient interim reduction;

6.  the risk is explicitly accepted through project governance.

Deferral SHALL NOT be considered safe when the missing control creates an unrecognized path to:

- authentication bypass;

- unauthorized vault access;

- unauthorized database access;

- credential compromise;

- cryptographic key exposure;

- unrestricted protected-app access.

**10.15 Security Debt**

Security debt SHALL be explicitly tracked.

Security debt includes:

- implemented but unverified security controls;

- known weaknesses awaiting remediation;

- deferred hardening;

- missing security tests;

- missing threat coverage;

- incomplete recovery handling;

- incomplete key-management controls;

- architectural inconsistencies awaiting resolution.

Security debt SHALL NOT disappear merely because implementation moves to a later phase.

**10.16 Phase Advancement Rules**

A phase SHALL NOT advance when a required security condition is merely:

- planned;

- partially implemented;

- undocumented;

- functionally tested but security-unverified;

- blocked without a recorded decision;

- assumed to be covered by another control without traceability.

A phase may advance with known residual risk only when:

1.  the risk is explicitly documented;

2.  the affected threat and asset are identified;

3.  the current control posture is accurately stated;

4.  the residual exposure is understood;

5.  the responsible authority accepts the risk;

6.  the acceptance does not violate a mandatory security gate;

7.  the decision is recorded according to project governance.

**10.17 No Silent Risk Acceptance**

The following SHALL constitute prohibited silent risk acceptance:

- treating a planned feature as implemented;

- treating implementation as verification;

- treating regression evidence as security evidence;

- treating a notification as prevention;

- treating root detection as root protection;

- treating encryption-at-rest as protection against a compromised process;

- treating accessibility monitoring as guaranteed enforcement;

- treating Android device unlock as App Lock authorization;

- treating a future phase's control as a current mitigation;

- treating an undocumented exception as an approved deviation.

Any such assumption SHALL be corrected when discovered.

**10.18 Phase Regression**

Security maturity is not permanently earned.

A previously completed phase SHALL be considered affected when a later change modifies:

- a trust boundary;

- authentication;

- authorization;

- cryptographic implementation;

- key storage;

- protected-app detection;

- accessibility handling;

- exported components;

- device-admin behavior;

- vault storage;

- database storage;

- security-critical permissions;

- security-sensitive dependencies;

- security-relevant architecture.

Affected controls SHALL return to an appropriate verification-required state.

A previously verified requirement SHALL NOT retain its verification status solely because its identifier did not change.

**10.19 Threat Model Reassessment Triggers**

Threat Model reassessment SHALL occur when any of the following occurs:

- a security control changes;

- a security-relevant architectural decision changes;

- authentication changes;

- authorization changes;

- cryptographic/key handling changes;

- protected-app detection changes;

- accessibility architecture changes;

- a security-sensitive dependency changes;

- a security requirement is added;

- a security requirement is removed;

- a trust boundary changes;

- a new exported component is introduced;

- a sensitive data store changes;

- a new attack surface is introduced;

- a major Android platform version changes security behavior;

- a phase security gate is reached;

- penetration testing identifies a previously unknown threat;

- a material security incident or bypass occurs.

These triggers convert Threat Model reassessment from an informal judgment into a controlled lifecycle activity.

**10.20 Change-Control Relationship**

Security phase status SHALL remain synchronized with:

- Requirements Traceability Matrix;

- Architecture Decision Records;

- Test Specification;

- Secure Coding Standard;

- Threat Model;

- implementation status;

- security-test evidence;

- risk register;

- change history.

A change SHALL propagate to every affected artifact.

No artifact SHALL silently preserve a superseded security assumption.

**10.21 Security Status Vocabulary**

The following vocabulary is normative for this Threat Model:

**Planned**

The control is required or intended but has not been implemented.

**Implemented**

The control exists in the implementation.

**Regression-Verified**

The implementation has passed applicable functional/regression verification.

**Security-Verified**

The control has passed threat-specific security verification with acceptable evidence.

**Accepted Risk**

The remaining exposure has been explicitly reviewed and accepted through governance.

**Compensating Control**

A separate control temporarily or permanently reduces the exposure created by a missing or incomplete primary control.

**Not Applicable**

The control does not apply to the current architecture or threat boundary.

**Reverification Required**

A previously verified control has been affected by a change and its prior evidence is no longer sufficient.

These terms SHALL be used consistently across the Threat Model and related security artifacts.

**10.22 Security Gate Decision Model**

Each security gate SHALL result in one of three controlled outcomes:

**PASS**

All mandatory gate criteria are satisfied and no unresolved blocking security condition exists.

**CONDITIONAL PASS**

The gate may proceed only with explicitly documented, governed, non-blocking residual risks and defined follow-up actions.

**FAIL**

A mandatory security condition is unsatisfied, evidence is insufficient, or a blocking security risk remains unresolved.

A conditional pass SHALL NOT be used to bypass a mandatory security requirement.

**10.23 Phase Security Matrix**

| **Phase** | **Primary Security Objective** | **Mandatory Security State** |
|----|----|----|
| **Phase 0 — Foundation** | Preserve existing security while establishing engineering foundation | Existing boundary protected; regression evidence passing |
| **Phase 1 — Core Security Platform** | Formalize and verify security architecture | Threat Model, Secure Coding Standard, security controls, security tests, traceability |
| **Phase 2 — Core Features** | Extend protected functionality without weakening security boundaries | New security-sensitive features threat-modeled and tested |
| **Phase 3 — Automation** | Prevent automation from bypassing authorization | Security-sensitive state transitions controlled |
| **Phase 4 — Production Hardening** | Improve resilience, detection, and operational security | Observability and recovery without information leakage |
| **Phase 5 — Security Hardening** | Add defense-in-depth and adversarial resistance | Root/tamper/debug controls and penetration testing |

**10.24 Security Maturity Invariant**

The project SHALL maintain the following progression:

Preserve

↓

Formalize

↓

Threat-Model

↓

Implement

↓

Security-Test

↓

Evidence

↓

Verify

↓

Harden

↓

Reassess

No step may be silently skipped.

In particular:

**Implementation SHALL NOT be treated as the end of security work.**

Security verification is an independent lifecycle activity.

**10.25 Section 10 Completion Criteria**

Section 10 is complete only when:

- every project phase has a defined security objective;

- Phase 0 preservation requirements are explicit;

- Phase 1 security-gate requirements are explicit;

- later-phase security responsibilities are defined;

- deferred controls remain visible;

- accepted limitations remain visible;

- compensating controls are distinguishable from primary controls;

- security debt cannot silently disappear;

- previously verified controls can return to reverification-required status;

- Threat Model reassessment triggers are explicit;

- phase advancement cannot bypass mandatory security conditions;

- security status terminology is standardized;

- gate outcomes are standardized;

- root/system compromise remains outside the guaranteed application security boundary;

- no future-phase control is represented as a current mitigation;

- the relationship between the Threat Model, RTM, ADRs, Test Specification, implementation, and evidence remains explicit.

**10.26 Boundary to Section 11**

Section 10 defines **when security controls are required and what maturity is necessary at each project phase**.

It does not define the detailed evidence required to prove that a control works.

Section 11 therefore establishes the **Security Verification and Evidence Model**, defining how threats, requirements, controls, security tests, execution environments, artifacts, and verification decisions are connected and how a control becomes formally security-verified.
