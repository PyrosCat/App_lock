# Threat Model — Architectural Reconciliation, Sections 10–16 (Corrected Proposal)

**Purpose.** These proposed replacement texts reconcile Threat Model Sections 10–16 with the approved two-tier foreground-detection architecture (ADR-013A; TM §16.32), consistent with the already-landed v2 Sections 1–9. Sections 10–16 in the current baseline model the Android Accessibility framework as the single, mandatory foreground-detection and enforcement mechanism in several places; the texts below align them with the approved model while preserving the mandatory distinction between the **current delivered implementation** (still Accessibility-only) and the **approved target architecture** (Usage Access baseline + optional Accessibility). No target-architecture capability is represented as implemented or security-verified.

**How to apply.** Each block is a drop-in replacement for the identified subsection, or a **new/corrected** subsection where noted. The merge is a **superset**: every two-tier revision is incorporated and every still-valid original threat/scope/control/risk item is preserved. Stable identifiers (THR-*, HF-*, INV-*, AS-*, R-*) are never reassigned. New attack surfaces reference the identifiers established in v2 §5.2 (AS-019 UsageStatsManager/Usage Access; AS-020 baseline lock-interface presentation mechanism; AS-021 detection-source selection layer). All unlisted §10–16 content remains unchanged.

## Corrections applied to the client §10–16 drafts (before approval)

1. **§14.35 stale forward-reference FIXED (must-fix; draft gap).** The client 10-15 draft's preamble claimed it fixed "the two incorrect forward references," but only §12.44 was in its change set. Original §14.35 wrongly describes §15 as the "Security Verification and Evidence Traceability Model" (that is §11's domain); §15 is **Continuous Threat Modeling and Change Management**. A corrected §14.35 is supplied below. (§10.26→§11, §11.37→§12, §15.35→§16 were verified correct and need no change.)
2. **§16.34.4 vs §16.32.15 contradiction RESOLVED (draft handled correctly).** Original §16.34.4 read "Continuous Accessibility-based detection is a security-critical availability dependency," contradicting §16.32.15 ("no longer describe Accessibility as the single or mandatory enforcement boundary"). The draft's §16.34.4 now aligns with §16.32.15; landed as-is.
3. **§12.44 stale forward-reference FIXED (draft handled correctly).** Original wrongly said §13 defines "Security Assumptions, Trust Boundaries, Non-Goals"; §13 is Historical Failures. Draft corrects it.
4. **Draft hygiene.** Removed both drafts' tool preambles (incl. the 10-15 preamble's inaccurate "twenty-nine subsections / Sections 10-16" count and its false "two forward references" claim) and the §16 draft's "Document-control note" footer. Cleaned the non-equivalence/flow diagram notation (the drafts' `!=`, `\|`, `v`, and trailing `\` line breaks are rendered here as `≠` chains and clean `→` flows).
5. **Supersets preserved.** Verified no original content dropped: §12.41 residual-risk register (all 10 original entries + baseline entries), §16.25/§16.26/§16.27 (all originals + two-tier), §10.19 and §11.10 (categories consolidated, not dropped), §13.8/§13.9 findings intact.
6. **Risk-status reclassification (flagged for approval).** HF-006 and HF-007 move from *"Accepted platform limitation / residual security concern"* to *"Open, with the approved two-tier baseline as a planned mitigation."* This is correct under the reconciliation (they were accepted only because Accessibility-only had no remedy) and is consistent with R-001 remaining Open — but it is a deliberate risk-status change.
7. **Retitles confirmed (all appropriate).** §11.22 "Security Verification of the Accessibility Boundary" → "…of the Detection and Enforcement Boundaries"; §12.25 "Accessibility Enforcement Risk" → "Detection Architecture and Accessibility Enhancement Risk"; §15.14 "Accessibility Enforcement Changes" → "Foreground-Detection and Enforcement Changes".

---

## Section 10 — Phase-Aware Security Model

### 10.19 Threat Model Reassessment Triggers — Replacement

Threat Model reassessment SHALL occur when any of the following occurs:

- a security control changes;
- a security-relevant architectural decision changes;
- authentication changes;
- authorization changes;
- cryptographic or key-handling changes;
- the protected-application foreground-detection architecture changes;
- a detection source is added, removed, reprioritized, or assigned different failure behavior;
- UsageStatsManager, Usage Access, the baseline sampling service, or baseline detector-health logic changes;
- the lock-interface presentation mechanism changes, including a change between overlay presentation and an activity-launch path;
- the optional Accessibility enhancement, its permission model, or its event-processing behavior changes;
- detection-source selection or Trigger Processor integration changes;
- a security-sensitive dependency changes;
- a security requirement is added or removed;
- a trust boundary changes;
- a new exported component, sensitive data store, or attack surface is introduced;
- a major Android platform version changes relevant detection, foreground-service, background-launch, overlay, permission, or Accessibility behavior;
- a phase security gate is reached;
- penetration testing identifies a previously unknown threat;
- a material security incident or bypass occurs.

These triggers convert Threat Model reassessment from an informal judgment into a controlled lifecycle activity and ensure that changes to either detection tier are evaluated without treating the optional Accessibility enhancement as the sole enforcement dependency.

---

## Section 11 — Security Verification and Evidence Model

### 11.10 Security Test Categories — Replacement

The project SHALL organize security verification around the established threat classes.

**Authentication Bypass.** Tests SHALL attempt to bypass PIN authentication; exploit stale sessions; exploit rapid application switching or relaunch; exploit lifecycle transitions, reboot, Back/Recents behavior, or process death; and exploit authentication-state inconsistencies.

**Authorization Bypass.** Tests SHALL attempt to access protected applications, the Vault, protected settings, or protected policies without authorization; and disable or weaken security controls without authorization.

**Storage and Cryptography.** Tests SHALL attempt to recover plaintext from private storage; recover Vault or database content from extracted ciphertext; recover filenames, sensitive metadata, or key material; and exploit backup, restore, corruption, or migration paths.

**IPC and Component Abuse.** Tests SHALL attempt to invoke exported components unexpectedly; spoof framework broadcasts where applicable; launch protected activities or abuse service interfaces; inject malicious intents; and exploit exported receiver behavior.

**Detection and Enforcement Availability.** Tests SHALL attempt to revoke or disrupt the baseline Usage Access permission; terminate, delay, or starve the baseline foreground-detection service; cause UsageStats sampling or usage-event retrieval to become stale, incomplete, or delayed; cause detection-source selection or Trigger Processor routing to select an unhealthy source or lose a foreground transition; interfere with the lock-interface presentation mechanism or the permission on which it depends; disable or disrupt the optional Accessibility enhancement when enabled; force-stop the application, interfere with watchdog operation, reboot the device, induce process death, or exercise OEM/background restrictions; and cause silent loss of the required baseline enforcement path. Until the two-tier architecture is implemented, equivalent testing SHALL continue to cover the current Accessibility-only delivered build and SHALL NOT claim that the planned baseline tier is already effective.

**UI and Interaction Abuse.** Tests SHALL attempt to exploit malicious overlays, obscured security UI, tapjacking, or injected touch events; exploit peer Accessibility Services; exploit lifecycle transitions; and capture authentication UI.

### 11.12 Security Test Preconditions — Replacement

Each security test SHALL identify its required environment. At minimum, where relevant, the evidence SHALL identify:

- Android version and device/model;
- application version and build type;
- release/debug state and security configuration;
- the active foreground-detection tier;
- Usage Access state, baseline foreground-service state, sampling interval, and detector-health state;
- the selected lock-interface presentation mechanism and its required permission/state;
- Accessibility permission, binding, and event-delivery state when the optional enhancement is enabled;
- detection-source selection configuration;
- device-admin and authentication configuration;
- network state where applicable;
- test data and database state.

A result without sufficient configuration information SHALL be considered incomplete evidence. The evidence SHALL also identify whether it applies to the current Accessibility-only implementation or the approved two-tier target architecture.

### 11.21 Device Coverage — Replacement

Security verification SHALL account for Android platform and device variation where a control depends on platform behavior.

The existing regression evidence includes testing on API 33, API 35, the established NucBox environment, and the established Moto G environment. This evidence establishes functional/regression coverage; it SHALL NOT automatically establish universal security assurance across Android devices.

Controls dependent upon the following SHALL receive platform-sensitive security testing where those differences materially affect the threat:

- UsageStatsManager and Usage Access behavior;
- foreground-service execution and sampling behavior;
- background-activity-launch restrictions and the selected lock-interface presentation mechanism;
- overlay permission and overlay behavior where applicable;
- optional Accessibility permission, binding, and event delivery;
- detection-source selection and failover/degradation behavior;
- boot behavior, permission enforcement, Keystore behavior, and device-admin behavior.

Current Accessibility-only results SHALL remain identified as current-implementation evidence and SHALL NOT be generalized to the unimplemented baseline detector.

### 11.22 Security Verification of the Detection and Enforcement Boundaries — Replacement (retitled from "…of the Accessibility Boundary")

Foreground-detection and enforcement verification SHALL test the complete path from detection input to authorization enforcement, not merely the presence of a permission or service. Each step below is necessary but not sufficient for the next (`≠` denotes "does not by itself establish").

**Baseline Tier.** For the approved baseline tier, verification SHALL distinguish:

    Usage Access Granted
      ≠ Baseline Service Running
      ≠ Usage Events Available and Current
      ≠ Protected App Detected
      ≠ Detection Routed Through Trigger Processor
      ≠ Lock Interface Successfully Presented
      ≠ Unauthorized Use Prevented

**Optional Accessibility Enhancement.** When the optional Accessibility enhancement is enabled, verification SHALL distinguish:

    Accessibility Permission Enabled
      ≠ Service Bound and Responsive
      ≠ Events Delivered
      ≠ Protected App Detected
      ≠ Detection Routed Through Trigger Processor
      ≠ Lock Interface Successfully Presented

The security objective is not merely that a detector appears enabled. It is that a protected application cannot become usable without App Lock authorization under the tested operating conditions.

Verification SHALL also demonstrate that loss or absence of the optional Accessibility enhancement degrades the target architecture to the healthy baseline tier rather than disabling protection. Conversely, loss of the required baseline detector or lock-presentation path SHALL be treated as a security-significant failure.

Until the target architecture is implemented, the current delivered build SHALL continue to be verified as Accessibility-dependent, and the planned baseline path SHALL receive no verification credit.

### 11.36 Section 11 Completion Criteria — Replacement

Section 11 is complete only when:

- the threat-to-control-to-evidence chain is defined;
- functional verification is explicitly separated from security verification;
- security status terminology is standardized;
- adversarial testing requirements are defined;
- historical bypasses are preserved as security evidence;
- configuration identification and evidence sufficiency are mandatory;
- security-test failures have controlled disposition;
- the baseline UsageStatsManager/Usage Access path receives end-to-end security verification after implementation;
- the optional Accessibility enhancement receives tier-specific verification when enabled;
- degradation from the optional enhancement to the baseline tier is verified;
- the current Accessibility-only implementation remains separately identified until superseded;
- Vault runtime authorization and offline confidentiality are separately verified;
- credential and key protection are separately verified;
- change-triggered reverification and verification downgrade rules are explicit;
- security claims are limited to what evidence supports;
- phase gates consume security evidence;
- evidence cannot silently redefine architecture or requirements.

---

## Section 12 — Security Risk and Residual-Risk Model

### 12.25 Detection Architecture and Accessibility Enhancement Risk — Replacement (retitled from "Accessibility Enforcement Risk")

The foreground-detection architecture SHALL remain a first-class security risk until the approved two-tier design is implemented and security-verified.

The current delivered implementation remains Accessibility-only. Its open risk includes:

- Android Restricted Settings limiting Accessibility grants;
- store-policy scrutiny affecting distribution;
- Accessibility appearing enabled while event delivery is impaired;
- force-stop and OEM behavior interrupting detection;
- protection gaps caused by loss of the only implemented detection source.

The approved target architecture changes, but does not eliminate, the risk. It removes Accessibility as a mandatory dependency by introducing a UsageStatsManager/Usage Access baseline. Under that architecture:

- store-policy and Restricted Settings risks are removed from the mandatory baseline path;
- silent Accessibility failure is narrowed to the optional enhancement when the baseline remains healthy;
- new baseline risks arise from Usage Access loss, stale or delayed usage-event sampling, foreground-service interruption, detector-health ambiguity, detection-source selection, and failure of the selected lock-interface presentation mechanism.

The target architecture SHALL NOT reduce current residual risk until the baseline tier is implemented and security-verified.

### 12.26 Enforcement Availability Risk — Replacement

The application treats continuous operation of the required enforcement path as a security asset. The following SHALL be assessed as security-relevant:

- loss or revocation of Usage Access;
- baseline foreground-detection service loss or starvation;
- stale, incomplete, or delayed UsageStats data;
- detection-source selection or Trigger Processor failure;
- failure of the selected lock-interface presentation mechanism or its required permission;
- optional Accessibility enhancement loss when enabled;
- watchdog loss, boot re-arm failure, process death, force-stop, or OEM background restrictions;
- overlay/UI failure or failure to present the authentication screen.

Loss of the optional Accessibility enhancement alone SHALL NOT be rated as total enforcement loss after the baseline tier is implemented and healthy. A control that detects failure only after protected content has become accessible SHALL be treated as detection, not prevention.

### 12.27 Fail-Open Risk — Replacement

Fail-open risk SHALL be represented according to implementation state.

**Current Delivered Implementation.** Loss of AppDetectionService or effective Accessibility event delivery can cause protected applications to become accessible without App Lock authentication. The watchdog and notification mechanism may reduce detection time but do not eliminate the exposure.

**Approved Target Architecture.** Loss of the optional Accessibility enhancement SHALL degrade detection to the healthy UsageStatsManager baseline and SHALL NOT, by itself, disable App Lock. Fail-open exposure remains possible if the required baseline detector, Usage Access, detection-source selection, Trigger Processor path, or lock-interface presentation mechanism becomes unavailable or ineffective.

    Detection ≠ Prevention ≠ Fail-Closed Enforcement

The Threat Model SHALL preserve this distinction. The approved architecture SHALL receive no risk-reduction credit until implemented and security-verified, and any remaining baseline exposure window SHALL be treated as residual risk.

### 12.41 Residual-Risk Register — Replacement

The residual-risk register SHALL include, at minimum, the following current and target-architecture risk areas:

| Risk Area | Current Treatment |
|---|---|
| Current Accessibility-only enforcement reliability | Open; mitigate and monitor until the baseline tier is implemented and verified |
| Baseline UsageStatsManager / Usage Access detection reliability | Approved target control; implementation and security verification required |
| Baseline detection latency and battery trade-off | Measure, tune, and verify against tier-specific requirements |
| Lock-interface presentation availability and permission dependency | Open design/implementation risk; resolve and verify in Phase 1 |
| Detection-source selection and health-state accuracy | Planned control; implementation and end-to-end verification required |
| Optional Accessibility enhancement reliability | Monitor as an enhancement-tier risk; loss must degrade to the baseline |
| Fail-open baseline enforcement | Known residual exposure until prevention or bounded exposure is demonstrated |
| Peer Accessibility interference | Best effort |
| Malicious overlays / tapjacking | Remediation required |
| Keystore invalidation | Remediation required |
| PIN-independent cryptographic keys | Accepted architectural property; evaluate by attacker boundary |
| Root/system compromise | Out of guaranteed boundary; best effort |
| Missing security verification | Phase 1 remediation |
| Root/tamper/debug controls | Deferred defense in depth |
| Security debt | Track and reassess |

The register SHALL distinguish current delivered-build risks from target-architecture risks and SHALL NOT mark the target baseline as an effective mitigation before implementation and verification.

### 12.43 Section 12 Completion Criteria — Replacement

Section 12 is complete only when:

- the approved likelihood × impact model and Critical/High/Medium/Low levels are preserved;
- inherent and residual risk are separated;
- control effectiveness depends on actual verification state;
- compensating controls and treatment categories are explicit;
- material risks have owners and acceptance is separated from closure;
- mandatory requirements cannot be bypassed through informal risk acceptance;
- current Accessibility-only enforcement risk remains represented until superseded;
- baseline Usage Access, detector-health, presentation, and source-selection risks are represented;
- optional Accessibility risk is distinguished from required baseline risk;
- fail-open baseline enforcement remains explicitly represented;
- PIN/UI authorization and cryptographic key protection remain distinct;
- Keystore invalidation, overlay/tapjacking, and security debt remain represented;
- root/system compromise remains outside the guaranteed application boundary;
- phase advancement considers residual risk;
- risk closure requires evidence and closed risks can be reopened;
- risk and verification remain separate but traceable dimensions.

### 12.44 Boundary to Section 13 — Replacement (corrected forward reference)

Section 12 defines how security risk is assessed, treated, accepted, monitored, and closed.

Section 13 preserves historical security failures and findings, including previously observed bypasses, platform limitations, architectural findings, remediation status, and the evidence that must remain available for regression and future security analysis.

---

## Section 13 — Historical Failures and Security Findings

### 13.8 Historical Finding HF-006 — Force-Stop / Accessibility Enforcement Limitation — Replacement

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

### 13.9 Historical Finding HF-007 — Accessibility Silent-Failure Risk — Replacement

**Description.** In the current delivered build, the Accessibility service may appear enabled while failing to deliver the foreground events required for enforcement. This is more severe than an obvious revocation because permission-state monitoring may incorrectly report that protection is available.

**Security Impact (current build).**

    Accessibility Enabled
      → AppDetectionService Appears Healthy
      → No Foreground Events Delivered
      → Protected App Opens Without Enforcement

Under the approved target architecture, this failure is narrowed to the optional enhancement when the baseline UsageStats detector remains healthy. The baseline tier introduces a corresponding need to detect stale or unavailable UsageStats data, sampling-service failure, and incorrect source-health selection.

**Current Status.** Open for the current delivered build. Enhancement-tier silent failure remains security-relevant after migration but SHALL NOT be classified as total enforcement loss when the verified baseline remains operational.

**Required Security Treatment.** The Core Security Platform SHALL implement health verification that can distinguish: permission granted; detector service running; source responsive; events or usage data current and being received; protected-app transition detected; detection routed through the Trigger Processor; lock interface presented; enforcement actually occurring. A permission-state check alone SHALL NOT be treated as proof of detector or enforcement health for either tier.

---

## Section 14 — Threat and Risk Assessment Model

### 14.16 Attack Preconditions — Replacement

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

### 14.20 Compensating Controls — Replacement

A compensating control MAY reduce residual risk when the primary control is absent or incomplete. A compensating control SHALL identify: the threat it addresses; the security property protected; the limitation of the primary control; how it reduces exposure; what conditions can defeat it; and whether it has been security-verified.

Examples include:

- watchdog and source-health monitoring when the active foreground detector cannot be automatically restored;
- degradation from an unhealthy optional Accessibility enhancement to a healthy baseline detector;
- lifecycle self-gating when process/session transitions threaten Vault authorization;
- persisted lockout state when process restart could otherwise reset the counter.

Degradation to the baseline tier SHALL only receive compensating-control credit after the baseline and source-selection behavior are implemented and security-verified. A notification alone SHALL NOT be considered equivalent to prevention unless the threat is inherently dependent on user response.

### 14.21 Availability Risk as Security Risk — Replacement

The availability of the required enforcement path SHALL be explicitly assessed. Threats affecting the following may create direct confidentiality or authorization consequences:

- Usage Access and UsageStatsManager data availability;
- baseline foreground-service execution and sampling;
- detection-source selection and Trigger Processor routing;
- the selected lock-interface presentation mechanism and its required permission;
- optional Accessibility binding or event delivery when the enhancement is enabled;
- watchdog operation, boot re-arm, required permissions, and device-admin protection.

    Availability Failure
      → Detection or Presentation Failure
      → Enforcement Failure
      → Authentication Bypass
      → Protected Asset Exposure

The severity SHALL be based on the final realistic consequence, not merely the initial component failure. Loss of the optional Accessibility enhancement alone SHALL NOT be assessed as total enforcement loss after a healthy baseline is implemented and verified.

### 14.22 Platform-Dependent Risk — Replacement

Where security depends on Android behavior outside the application's direct control, the Threat Model SHALL document: the assumed platform behavior; known platform limitations; affected Android versions; OEM-dependent behavior where known; detection mechanisms; recovery behavior; and remaining exposure.

For the approved target architecture, primary examples include UsageStatsManager and Usage Access behavior, foreground-service restrictions, background-activity-launch restrictions, overlay/presentation permission behavior, and the optional Accessibility framework. For the current delivered build, Accessibility remains the sole implemented detector and its platform dependency remains open.

Platform dependence SHALL NOT automatically classify a threat as low risk.

### 14.26 Risk Reassessment — Replacement

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

### 14.35 Boundary to Section 15 — Replacement (corrected forward reference; not in the client draft)

Section 14 defines **how security risk is assessed and prioritized**. It does not define how the Threat Model is kept current as the system evolves.

Section 15 therefore establishes the **Continuous Threat Modeling and Change Management** model — the reassessment triggers, change classification, cross-document synchronization, ADR and traceability impact, and Threat Model versioning through which the security analysis is maintained as requirements, architecture, controls, dependencies, and Android platform behavior change.

---

## Section 15 — Continuous Threat Modeling and Change Management

### 15.4 Threat Model Reassessment Triggers — Replacement

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

### 15.6 Architecture Changes — Replacement

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

### 15.13 Android Platform Changes — Replacement

Android platform changes SHALL be treated as potential security-model changes. Reassessment SHALL consider changes affecting:

- UsageStatsManager, usage-event availability, and Usage Access;
- foreground-service execution and process-management restrictions;
- background-activity-launch rules and applicable exemptions;
- overlay permission and overlay behavior;
- Accessibility Services and Restricted Settings;
- boot behavior, package visibility, permissions, Device Admin, Keystore, biometric authentication, application sandboxing, backup, exported components, and task/activity behavior.

Major Android-version changes SHALL be considered a defined reassessment trigger. OEM-specific behavior SHALL also be reassessed where it can affect detection, presentation, enforcement availability, or recovery.

### 15.14 Foreground-Detection and Enforcement Changes — Replacement (retitled from "Accessibility Enforcement Changes")

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

### 15.29 Continuous Monitoring of Known Risks — Replacement

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

### 15.34 Section 15 Completion Criteria — Replacement

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

---

## Section 16 — Governance, Traceability, Maintenance, Final Security Disposition

### 16.10 ADR Relationship — Replacement

Architecture Decision Records SHALL be used for material architectural decisions affecting the security model. The Threat Model SHALL reference applicable ADRs involving:

- trust boundaries;
- authentication;
- authorization;
- cryptography;
- key management;
- secure storage;
- enforcement;
- the mandatory UsageStatsManager / Usage Access baseline detection architecture;
- the optional Accessibility enhancement architecture;
- detection-source selection and Trigger Processor integration;
- the mechanism used to present the lock interface from a background context;
- detection-tier health monitoring and failure semantics;
- Device Admin;
- security-sensitive IPC;
- recovery;
- security assumptions;
- material security tradeoffs.

The Threat Model SHALL record the security consequence of the decision but SHALL NOT duplicate the complete ADR.

### 16.18 Security Assumption Governance — Replacement

Security assumptions SHALL be explicit. Each material assumption SHALL identify: what is trusted; why it is trusted; what security property depends on it; what happens if it fails; whether that failure is in scope; applicable mitigation; and its reassessment trigger.

For this Threat Model, material assumptions include the trust placed in:

- Android Keystore;
- Android application sandbox;
- Android package-management integrity;
- Device Admin framework;
- UsageStatsManager and the Usage Access special-permission framework;
- the foreground-service lifecycle used by the baseline detector;
- the approved mechanism used to present the lock interface from a background context;
- the detection-source selection and Trigger Processor path;
- the optional Accessibility framework when the enhancement tier is enabled;
- trusted device authentication mechanisms;
- legitimate owner authority.

Assumptions SHALL NOT remain hidden dependencies of the security model. The Threat Model SHALL distinguish assumptions supporting the required baseline detection path from assumptions supporting the optional Accessibility enhancement. Failure of an enhancement-tier assumption SHALL NOT automatically be treated as failure of the complete enforcement architecture when the baseline path remains healthy.

### 16.19 Trust-Boundary Governance — Replacement

Changes that introduce, remove, or materially alter a trust boundary SHALL trigger security analysis. The Threat Model SHALL maintain explicit consideration of the boundaries between:

- unauthenticated and authenticated App Lock state;
- App Lock and protected third-party applications;
- App Lock and other applications;
- App Lock private storage and external actors;
- application code and Android Keystore;
- App Lock and UsageStatsManager / Usage Access;
- the baseline detector and the detection-source selection / Trigger Processor path;
- App Lock and the lock-interface presentation mechanism, including any overlay or background-activity-launch capability;
- App Lock and the optional Accessibility framework;
- App Lock and Device Admin framework;
- App Lock and boot/watchdog mechanisms;
- App Lock UI and potentially hostile UI overlays or peer accessibility services.

A new communication path crossing a security boundary SHALL NOT be treated as an ordinary implementation detail. The required baseline path and the optional Accessibility enhancement SHALL remain separately identifiable so that a failure or compromise of one path is not silently attributed to the other.

### 16.20 Assumption Failure — Replacement

If a security assumption becomes invalid, affected threats SHALL be reassessed. Examples include:

- material Android platform behavior changes;
- Keystore behavior changes;
- application-sandbox assumptions changing;
- UsageStatsManager or Usage Access behavior changing;
- foreground-service lifecycle or execution restrictions changing;
- the approved lock-interface presentation mechanism becoming unavailable or materially restricted;
- detection-source selection or Trigger Processor assumptions changing;
- Accessibility framework behavior changing;
- Device Admin behavior changing;
- security-relevant platform restrictions changing.

The reassessment SHALL determine whether the change: invalidates a control; increases risk; introduces a new threat; changes a trust boundary; requires an ADR; requires security testing; or requires phase reassessment.

Failure of an optional Accessibility assumption SHALL be analyzed as an enhancement-tier failure when the mandatory baseline remains operational. Failure of a baseline detection, permission, presentation, or Trigger Processor assumption SHALL remain security-significant because it may affect core enforcement availability.

### 16.24 Review Triggers — Replacement

Threat Model reassessment SHALL occur when any of the following occurs:

- a security requirement is added, removed, or materially changed;
- a security control is added, removed, or materially changed;
- authentication architecture changes;
- authorization architecture changes;
- cryptographic or key-management behavior changes;
- protected storage changes;
- the enforcement mechanism changes;
- the baseline UsageStatsManager / Usage Access detection approach changes;
- the optional Accessibility enhancement approach changes;
- the detection-source selection or Trigger Processor integration changes;
- the mechanism used to present the lock interface from a background context changes;
- the required detection, presentation, or permission health model changes;
- a security-critical dependency changes;
- a security-relevant ADR is introduced or superseded;
- a material security defect is discovered;
- a penetration test identifies a new finding;
- a material Android platform change affects a security assumption;
- a trust boundary changes;
- a phase security gate is reached;
- a previously accepted risk materially changes;
- a previously established security assumption becomes questionable.

These triggers make Threat Model reassessment an explicit lifecycle requirement rather than an ad-hoc judgment.

### 16.25 Historical Security Failures — Replacement

Previously discovered security failures SHALL remain part of the Threat Model's historical evidence when they provide meaningful information about attack paths or control effectiveness. The preserved historical cases include:

- self-gate bypass;
- fast-relaunch bypass;
- fast-switch relock defect;
- release-build cryptographic dependency failure;
- historical plaintext database storage;
- force-stop and Accessibility availability limitations affecting the current Accessibility-only implementation.

The approved two-tier target architecture SHALL NOT erase or retrospectively reclassify the historical Accessibility-only failure mode as though it never existed. The historical finding SHALL remain traceable to the implementation state in which it was observed. After the two-tier architecture is implemented, future failures SHALL be classified according to the affected path: mandatory baseline detection, optional Accessibility enhancement, detection-source selection, Trigger Processor, or lock-interface presentation.

A fixed vulnerability SHALL remain traceable to the control that corrected it and the verification evidence demonstrating the correction. Historical failures SHALL NOT be removed simply because they are no longer reproducible.

### 16.26 Known Residual Risks — Replacement

The Threat Model SHALL distinguish resolved vulnerabilities from residual risks that remain accepted or incompletely mitigated. The current security analysis identifies, at minimum:

1. The delivered implementation continues to rely on Accessibility-based detection until the approved two-tier architecture is implemented and verified.
2. Accessibility can appear enabled while expected event delivery is not functioning, affecting the current implementation and the future optional enhancement tier.
3. Force-stop, process termination, foreground-service restrictions, and OEM behavior can interrupt detection or enforcement components.
4. The approved target architecture depends on the baseline UsageStatsManager / Usage Access detector remaining permitted, operational, and sufficiently timely.
5. Loss, revocation, or silent failure of Usage Access or the baseline detector can cause fail-open enforcement if not detected and handled.
6. The mechanism used to present the lock interface from a background context remains an implementation decision and may introduce permission, lifecycle, background-launch, overlay, or tapjacking risks.
7. Detection-source selection, Trigger Processor, or lock-engine integration defects can prevent a valid foreground transition from producing enforcement.
8. A malicious overlay can obscure the authentication UI.
9. Peer accessibility services can observe or inject UI interaction.
10. Root or system compromise lies below the application's guaranteed trust boundary.
11. Vault and database encryption keys are independent of the user's PIN.
12. Keystore invalidation currently lacks a recovery path.
13. Several security-hardening controls and the approved two-tier detection controls remain planned rather than implemented or security-verified.

These conditions SHALL NOT be represented as resolved merely because other controls are functioning. The target architecture and the current delivered implementation SHALL remain separately represented. Approval of the target architecture SHALL NOT reduce current implementation risk until the required controls are implemented and supported by appropriate security-verification evidence.

### 16.27 Planned Controls — Replacement

Controls that are specified but not implemented SHALL remain explicitly identified as planned or not-started. In particular, the Threat Model SHALL NOT treat the following as effective mitigations until implementation and appropriate verification evidence exist:

- the mandatory UsageStatsManager / Usage Access baseline detector;
- the baseline foreground-service sampling and lifecycle implementation;
- the detection-source selection layer;
- the common Trigger Processor integration for both detection tiers;
- the approved lock-interface presentation mechanism for the baseline tier;
- baseline permission, detector, presentation, and health monitoring;
- the optional Accessibility enhancement integrated as a non-mandatory detection source;
- tier-specific fallback and degradation behavior;
- root detection;
- root response;
- tamper detection;
- debug protection;
- anti-tapjacking/overlay-obscure defense;
- Keystore-invalidation recovery;
- security-specific audit-log tamper evidence;
- other security controls identified as not-started by the authoritative RTM.

Planned functionality MAY reduce future risk, but SHALL NOT reduce the current risk rating merely because it is scheduled.

### 16.32.5 Detection and Enforcement Architecture — Replacement

Foreground detection is governed by an approved two-tier target architecture. The target architecture has been approved, but its corresponding implementation and security verification have not yet been completed.

The baseline tier is the mandatory detection path and is designed to permit App Lock to function without Accessibility being enabled. The baseline tier uses:

- Android UsageStatsManager;
- the Android Usage Access special permission;
- a foreground service that samples usage events to identify the current foreground application.

Because presenting the lock interface from a background context is subject to Android background-activity-launch restrictions, the baseline tier also requires an approved mechanism for presenting the lock interface. The architecture currently identifies display-over-other-apps / system-alert-window capability as the expected mechanism, either for overlay presentation or for obtaining the applicable activity-launch exemption. The final implementation decision between those presentation approaches remains an implementation decision for the Core Security Platform phase and SHALL be recorded through the applicable architecture-governance process.

When implemented, the baseline tier SHALL remain operational when Accessibility is disabled. Until implementation and verification are complete, the delivered build remains Accessibility-only, and the approved baseline tier SHALL NOT be represented as an effective current mitigation.

### 16.34.4 Enforcement Boundary — Replacement (resolves the §16.34.4 vs §16.32.15 contradiction)

The approved target enforcement boundary depends on the mandatory UsageStatsManager / Usage Access baseline detector, its required permission and lifecycle, the approved lock-interface presentation mechanism, and the common Trigger Processor and lock-engine path.

Accessibility is an optional enhancement and SHALL NOT be a prerequisite for core protected-application locking in the approved target architecture.

Loss or failure of the optional Accessibility enhancement alone SHALL result in degradation to the baseline detection path when that path remains healthy; it SHALL NOT, by itself, constitute complete loss of App Lock protection.

Loss, revocation, silent failure, or unacceptable degradation of the mandatory baseline detector, its Usage Access permission, its presentation mechanism, the detection-source selection layer, the Trigger Processor, or the lock-engine integration can result in fail-open enforcement and therefore constitutes a security concern rather than an ordinary reliability issue.

The current delivered implementation remains Accessibility-only until the approved two-tier architecture is implemented and security-verified. Accessibility therefore remains a hard availability dependency of the current implementation, and the associated current-state risk SHALL remain open until the migration is complete and supported by evidence.

---

*End of proposed Sections 10–16 reconciliation (corrected). On approval, this lands as Threat Model v2 §10–16 via the md→docx pipeline (same superset method as §1–9), completing the full §1–16 reconciliation; `tm/VERSION.md` is updated to "v2 — §1–16 reconciled."*
