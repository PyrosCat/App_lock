**15. Continuous Threat Modeling and Change Management**

**15.1 Purpose**

This section defines how the Threat Model SHALL remain current throughout the App Lock development lifecycle.

Threat modeling SHALL be treated as a continuous engineering activity rather than a one-time documentation exercise.

The Threat Model SHALL be reassessed when changes can affect:

- protected assets;

- security properties;

- threat actors;

- attacker capabilities;

- attack surfaces;

- trust boundaries;

- authentication;

- authorization;

- data protection;

- cryptographic controls;

- security controls;

- enforcement mechanisms;

- recovery behavior;

- dependencies;

- Android platform behavior;

- deployment;

- implementation phase;

- residual risk.

The purpose of continuous threat modeling is to prevent the Threat Model from becoming an accurate description of an earlier version of the application while the implemented security architecture has materially changed.

**15.2 Governing Principle**

The Threat Model SHALL describe the **current security reality of the system**, not merely the security architecture originally approved.

A previously analyzed threat SHALL remain relevant until there is sufficient evidence that:

1.  the affected attack path no longer exists;

2.  the applicable control remains effective;

3.  the underlying assumptions remain valid; and

4.  the resulting risk has been reassessed.

A threat SHALL NOT be removed merely because its associated implementation work has been completed.

Mitigated threats remain valuable for:

- regression analysis;

- change-impact analysis;

- historical security evidence;

- future architectural review;

- detection of control regression.

**15.3 Change-Driven Threat Modeling**

The following lifecycle SHALL be used for security-relevant changes:

Change

↓

Impact Analysis

↓

Threat Reassessment

↓

Control Reassessment

↓

Security Testing

↓

Risk Reassessment

↓

Threat Model Update

The sequence SHALL be applied proportionally to the significance of the change.

A minor documentation-only change that cannot affect the security model does not require a full threat reassessment.

A change affecting authentication, cryptography, trust boundaries, enforcement, or protected data SHALL receive explicit security-impact analysis.

**15.4 Threat Model Reassessment Triggers**

Threat Model reassessment SHALL be triggered by material changes involving:

- security requirements or functional requirements with security implications;
- removed or modified requirements;
- architecture or detailed design affecting security boundaries;
- implementation, removal, or replacement of security controls;
- authentication, authorization, session handling, key management, encryption, or secure storage;
- the lock-enforcement mechanism;
- the foreground-detection architecture, including UsageStatsManager, Usage Access, baseline sampling, detection-source selection, Trigger Processor routing, or detector health;
- the lock-interface presentation mechanism or its required permission;
- optional Accessibility Service usage or behavior;
- exported components, permissions, administrative protections, recovery, backup, or deployment;
- security-relevant dependencies;
- Android platform, Android version, OEM behavior, foreground-service, background-launch, overlay, or permission behavior;
- security defects, penetration-test findings, newly discovered attack techniques, or material phase-gate changes.

These triggers SHALL be interpreted according to their actual effect on the current implementation and approved target architecture.

**15.5 Security Requirement Changes**

When a security-relevant requirement is added, removed, or modified, the change SHALL be assessed for its effect on:

- assets;

- security properties;

- attack surfaces;

- threats;

- security controls;

- verification requirements;

- risk;

- residual risk;

- implementation sequencing;

- phase gates;

- traceability;

- applicable ADRs.

A requirement change SHALL NOT be treated as isolated from the existing Threat Model.

For example, changing an authentication requirement may affect:

Authentication

↓

Session State

↓

Authorization

↓

Vault Access

↓

Protected-App Access

↓

Threats

↓

Security Tests

**15.6 Architecture Changes**

Architectural changes SHALL receive security-impact analysis before implementation where the change affects a security boundary. Examples include:

- introducing or removing a security service;
- changing the lock engine;
- adding, removing, or reprioritizing a foreground-detection source;
- changing the UsageStatsManager/Usage Access baseline, sampling service, source-selection layer, or Trigger Processor integration;
- changing the optional Accessibility enhancement;
- changing the lock-interface presentation mechanism;
- changing authentication, storage, encryption, key management, IPC, dependencies, network connectivity, or recovery architecture.

The Threat Model SHALL identify whether the change creates a new threat, changes an attack path, invalidates a control, changes a trust boundary or attacker assumption, changes residual risk, or invalidates verification evidence.

The Threat Model SHALL inform architecture decisions but SHALL NOT silently rewrite the TAS, SDS, DDS, or other authoritative documents. Architectural deviations SHALL follow the applicable governance and ADR process.

**15.7 Security-Control Changes**

Any material change to a security control SHALL trigger reassessment.

This includes:

- implementation;

- modification;

- replacement;

- removal;

- disabling;

- relocation;

- dependency changes;

- changes in configuration;

- changes in enforcement conditions.

For each affected control, the project SHALL determine:

Threat

↓

Required Control

↓

Implementation Phase

↓

Actual Status

↓

Verification

↓

Residual Risk

A control changing from implemented to modified SHALL not retain its previous verification status automatically.

The affected verification SHALL be reassessed according to the project's continuous-verification rules.

**15.8 Previously Verified Requirements and Controls**

Verification SHALL be considered conditional on the configuration against which it was established.

A previously verified security requirement or control SHALL return to a re-verification state when a relevant change can invalidate the evidence.

Examples include:

- implementation changes;

- dependency changes;

- architecture changes;

- security-control changes;

- Android platform changes;

- related security defects;

- changes to configuration;

- changes to authentication behavior;

- changes to cryptographic behavior.

Historical verification evidence SHALL remain preserved.

The status of that evidence SHALL NOT be rewritten to imply that the new implementation has already been verified.

**15.9 Threat Lifecycle**

Threat records SHALL support a controlled lifecycle.

A threat MAY move through states such as:

Identified

↓

Analyzed

↓

Treatment Defined

↓

Mitigation Implemented

↓

Security Verification

↓

Residual Risk Assessed

↓

Monitored / Accepted / Closed

The exact project status terminology SHALL remain consistent with the established governance and traceability model.

A threat SHALL NOT be marked closed merely because code was changed.

Closure requires sufficient evidence that the disposition is justified.

**15.10 Mitigated Threats**

A mitigated threat SHALL remain in the Threat Model.

The record SHALL preserve:

- original attack path;

- original risk assessment;

- mitigation;

- implementation status;

- verification evidence;

- residual risk;

- relevant historical failure information;

- change history.

This is particularly important for previously observed App Lock failures, including:

- self-gate bypass;

- fast-relaunch bypass;

- fast-switch relock defects;

- plaintext database exposure;

- enforcement failures.

These historical cases provide known attack patterns against which future architectural changes can be evaluated.

**15.11 Threat Reopening**

A previously mitigated or closed threat SHALL be reopened when a subsequent change:

- recreates the attack path;

- weakens the mitigation;

- invalidates the security assumption;

- changes the affected trust boundary;

- changes a dependent component;

- introduces equivalent behavior through another path;

- invalidates the evidence supporting the previous disposition.

A historical verification result SHALL not prevent a threat from being reopened.

**15.12 Dependency Changes**

Security-relevant dependency changes SHALL trigger impact analysis.

The assessment SHALL consider:

- new privileges;

- new exported components;

- new runtime behavior;

- new native code;

- cryptographic behavior;

- storage behavior;

- authentication behavior;

- transitive dependencies;

- build-time behavior;

- changes to attack surface.

A dependency update SHALL not automatically invalidate every security control.

However, affected controls SHALL be identified and reassessed based on actual impact.

**15.13 Android Platform Changes**

Android platform changes SHALL be treated as potential security-model changes. Reassessment SHALL consider changes affecting:

- UsageStatsManager, usage-event availability, and Usage Access;
- foreground-service execution and process-management restrictions;
- background-activity-launch rules and applicable exemptions;
- overlay permission and overlay behavior;
- Accessibility Services and Restricted Settings;
- boot behavior, package visibility, permissions, Device Admin, Keystore, biometric authentication, application sandboxing, backup, exported components, and task/activity behavior.

Major Android-version changes SHALL be considered a defined reassessment trigger. OEM-specific behavior SHALL also be reassessed where it can affect detection, presentation, enforcement availability, or recovery.

**15.14 Foreground-Detection and Enforcement Changes**

Because foreground detection is the trigger for App Lock enforcement, changes to either detection tier or the shared enforcement path SHALL receive elevated security review. The review SHALL consider:

- Usage Access state and UsageStatsManager data availability;
- sampling interval, latency, battery cost, and stale-data detection;
- baseline foreground-service lifecycle, force-stop behavior, and OEM process management;
- detection-source selection, source health, and Trigger Processor routing;
- the selected lock-interface presentation mechanism and Android background-launch/overlay behavior;
- optional Accessibility permission, service binding, event delivery, Restricted Settings, and silent failure;
- watchdog operation, boot recovery, and resulting enforcement exposure.

The Threat Model SHALL preserve the distinction between permission granted, detector running, source responsive, data/events current, protected application detected, lock request routed, lock interface presented, and authorization actually enforced.

Loss of the optional Accessibility enhancement SHALL NOT be treated as total enforcement loss when the verified baseline remains healthy. A change that preserves only permission state SHALL NOT automatically be considered equivalent security behavior.

**15.15 Cryptographic and Key-Management Changes**

Changes involving:

- Android Keystore;

- MasterKey;

- database keys;

- vault encryption;

- key generation;

- key storage;

- key rotation;

- key invalidation;

- cryptographic libraries;

- encryption configuration;

SHALL trigger security-impact analysis.

The assessment SHALL determine whether:

- key ownership changes;

- key derivation changes;

- trust boundaries change;

- recovery behavior changes;

- existing ciphertext remains accessible;

- existing security evidence remains applicable;

- new data-exposure paths are introduced.

A change to cryptographic architecture SHALL receive particular scrutiny because cryptographic controls protect multiple downstream assets.

**15.16 Authentication and Authorization Changes**

Changes to authentication SHALL trigger analysis of:

- credential storage;

- credential verification;

- lockout;

- session creation;

- session invalidation;

- relock policy;

- biometric behavior;

- PIN changes;

- recovery;

- authorization decisions;

- vault access;

- protected-app access.

A change to one authentication mechanism SHALL not be assumed to affect only the authentication screen.

The complete authorization path SHALL be considered.

**15.17 Recovery Changes**

Recovery behavior SHALL be treated as part of the security model.

Changes involving:

- forgotten credentials;

- data clearing;

- uninstall/reinstall;

- database corruption;

- Keystore invalidation;

- migration;

- backup;

- restore;

SHALL be evaluated for both:

- legitimate recovery;

- attacker abuse.

A recovery mechanism that restores legitimate access MAY create a bypass path if its authorization requirements are insufficient.

Conversely, eliminating recovery MAY increase availability or data-loss risk.

Both consequences SHALL be assessed.

**15.18 Phase-Transition Reassessment**

Implementation-phase transitions SHALL provide a formal opportunity to reassess the Threat Model.

At minimum, the project SHALL evaluate whether:

- newly introduced functionality changes the attack surface;

- deferred controls are now required;

- temporary security gaps remain;

- previously accepted risks remain acceptable;

- phase-specific security gates are satisfied;

- new verification evidence exists;

- residual risks have changed.

The Threat Model SHALL remain aligned with the Implementation Strategy's seven-phase lifecycle.

The phase sequence is:

1.  Foundation

2.  Core Security Platform

3.  Core Application Features

4.  Automation & Intelligent Operations

5.  Production Hardening

6.  Security Hardening

7.  Release Readiness

A phase transition SHALL NOT itself prove that security requirements are satisfied.

**15.19 Temporary Phase Risk**

When a security control is intentionally deferred, the Threat Model SHALL identify:

- the threat that exists before the control;

- the current exposure;

- why the control is deferred;

- the phase in which it is introduced;

- whether the existing architecture remains acceptable without it;

- compensating controls, if any;

- required verification;

- the phase gate that controls progression.

The Threat Model SHALL NOT describe a future control as though it already protects the current implementation.

**15.20 Change Impact Classification**

Security-impacting changes SHOULD be classified according to their effect.

**No Security Impact**

The change cannot reasonably affect:

- assets;

- threats;

- controls;

- trust boundaries;

- security behavior;

- security verification.

No Threat Model update is required beyond normal document maintenance.

**Security-Relevant Change**

The change can affect an existing security control, attack path, requirement, or verification relationship.

Targeted impact analysis and reassessment SHALL be performed.

**Security-Architecture Change**

The change affects:

- trust boundaries;

- authentication;

- authorization;

- cryptography;

- key management;

- enforcement architecture;

- security-critical IPC;

- security-critical dependencies.

Formal security review SHALL be performed and applicable governance procedures SHALL be followed.

**15.21 Threat Model Change Record**

Security-relevant Threat Model changes SHALL preserve change history.

A change record SHOULD identify:

- date;

- affected Threat Model section;

- affected threat/control identifiers;

- reason for change;

- triggering change;

- previous disposition;

- new disposition;

- risk impact;

- verification impact;

- affected requirements;

- affected ADRs;

- affected tests;

- reviewer/approval information where required.

Historical information SHALL remain recoverable.

**15.22 Traceability Impact**

A Threat Model change SHALL trigger traceability review where the change affects:

- requirements;

- threats;

- controls;

- tests;

- evidence;

- risk;

- architecture decisions.

The relationship SHALL remain bidirectional where applicable:

Requirement

↕

Threat

↕

Control

↕

Implementation

↕

Security Test

↕

Evidence

↕

Verification

A change that breaks one of these relationships SHALL be identified rather than silently repaired through inference.

**15.23 ADR Impact**

A Threat Model finding that identifies a material architectural conflict SHALL be routed through the project's architecture-decision process.

The Threat Model SHALL:

- identify the conflict;

- describe the security consequence;

- identify affected architecture;

- identify the relevant decision;

- preserve the relationship to the resulting ADR.

The Threat Model SHALL not directly modify an accepted architectural decision.

If the architecture changes, the resulting decision SHALL be reflected back into the Threat Model and affected engineering documentation.

**15.24 Secure Coding Standard Impact**

A Threat Model finding MAY identify the need for a Secure Coding Standard rule.

Examples include:

- authentication handling;

- cryptographic API usage;

- secure storage;

- exported-component restrictions;

- overlay defense;

- accessibility handling;

- sensitive logging;

- secure memory handling;

- dependency controls.

The Threat Model SHALL identify the security requirement for such a rule.

The Secure Coding Standard SHALL contain the detailed mandatory implementation rule where that document is the appropriate authority.

**15.25 Security Test Impact**

Threat Model changes SHALL be evaluated for security-test impact.

The assessment SHALL determine whether the change requires:

- new security tests;

- modified security tests;

- regression testing;

- penetration testing;

- environment-specific testing;

- new evidence requirements.

The Threat Model SHALL identify the verification need but SHALL NOT duplicate detailed security test procedures that belong in the Security Test Specification.

**15.26 Residual Risk Reassessment**

Every material threat reassessment SHALL determine whether residual risk has:

- decreased;

- increased;

- remained unchanged;

- become unknown because evidence is no longer applicable.

A control improvement does not automatically mean that residual risk is eliminated.

Likewise, a code change does not automatically mean that residual risk has increased.

The assessment SHALL be evidence-based.

**15.27 Unknown or Insufficient Evidence**

If a change prevents the project from determining whether a previous security property remains valid, the Threat Model SHALL identify the condition as an evidence gap.

The project SHALL NOT silently retain the previous security status.

The appropriate state SHALL distinguish:

- verified;

- requires re-verification;

- verification invalidated;

- unknown;

- not applicable.

The exact controlled terminology SHALL follow the project's established governance and verification model.

**15.28 Security Incident or Defect Trigger**

A security defect SHALL trigger Threat Model review when it demonstrates:

- a previously unidentified attack path;

- a failure of an existing control;

- an incorrect security assumption;

- an inaccurate threat rating;

- an incomplete trust boundary;

- insufficient security testing;

- a previously unknown platform limitation.

The review SHALL determine whether the finding affects:

- other implementations;

- related threats;

- similar components;

- previous verification evidence;

- other devices or Android versions;

- phase gates;

- residual risk.

**15.29 Continuous Monitoring of Known Risks**

Known risks SHALL remain monitored until formally closed or accepted. Monitoring SHALL consider the defined review triggers for each risk.

For the current Accessibility-only implementation, relevant triggers include Android-version changes, Accessibility and Restricted Settings behavior, store-policy changes, force-stop/OEM behavior, and the Core Security and Release Readiness gates.

For the approved target architecture, relevant triggers additionally include:

- UsageStatsManager or Usage Access behavior changes;
- baseline sampling latency, battery, or health changes;
- foreground-service and process-lifecycle changes;
- detection-source selection or Trigger Processor changes;
- lock-interface presentation or permission changes;
- optional Accessibility enhancement behavior changes.

A risk with no current action SHALL still retain its status and review conditions. Approval of the target architecture SHALL NOT close the current implementation risk.

**15.30 Document Synchronization**

When a security change affects multiple engineering documents, the affected documents SHALL be synchronized through their respective governance processes.

Potentially affected artifacts include:

- SRS;

- NFR;

- TAS;

- SDS;

- DDS;

- Threat Model;

- Secure Coding Standard;

- Test Specification;

- Implementation Strategy;

- RTM;

- ADRs;

- deployment and operations documentation.

The Threat Model SHALL not become a substitute for updating the authoritative source document responsible for the changed information.

**15.31 No Silent Reconciliation**

Conflicts discovered during continuous threat modeling SHALL be explicitly identified.

The Threat Model SHALL NOT silently choose between conflicting documents.

Where the conflict concerns:

- requirements;

- architecture;

- design;

- implementation;

- verification;

- governance;

the appropriate project authority and decision process SHALL be used.

The resulting decision SHALL be reflected in affected artifacts.

**15.32 Threat Model Versioning**

Material Threat Model changes SHALL produce a traceable document revision.

A revision SHALL identify the nature of the change and preserve prior security conclusions where necessary for historical analysis.

Version changes SHALL NOT be used to erase previous findings.

A new version represents an updated security assessment, not the deletion of prior security history.

**15.33 Continuous Threat Modeling Operating Model**

The project SHALL operate Threat Modeling as an engineering feedback loop:

System Change

↓

Security Impact Assessment

↓

Threat Model Reassessment

↓

Control Assessment

↓

Risk Assessment

↓

Security Verification

↓

Traceability Update

↓

Architecture / Requirement Decision

↓

Updated Threat Model

The loop SHALL continue throughout implementation and release preparation.

**15.34 Section 15 Completion Criteria**

Section 15 is complete when:

- Threat Modeling is defined as a continuous activity;
- material requirement, architecture, security-control, dependency, platform, and phase-change triggers are defined;
- previously verified controls can return to re-verification;
- mitigated threats remain preserved and can be reopened;
- UsageStatsManager/Usage Access baseline changes receive appropriate scrutiny;
- detection-source selection, Trigger Processor, health monitoring, and lock-interface presentation changes receive appropriate scrutiny;
- optional Accessibility changes receive tier-appropriate scrutiny;
- cryptographic, key-management, authentication, authorization, and recovery changes trigger reassessment;
- temporary phase risks remain explicitly tracked;
- change history, traceability, ADR, Secure Coding Standard, security-test, and residual-risk impacts are assessed;
- insufficient evidence is explicitly identified;
- security defects trigger reassessment and known risks retain review triggers;
- affected engineering documents remain synchronized;
- conflicts are not silently reconciled and Threat Model history is preserved.

**15.35 Boundary to Section 16**

Section 15 defines **how the Threat Model changes when the system changes**.

Section 16 defines **how the Threat Model is governed, traced, controlled, and maintained as an authoritative engineering document**, including its relationships to requirements, controls, tests, ADRs, and other project artifacts.
