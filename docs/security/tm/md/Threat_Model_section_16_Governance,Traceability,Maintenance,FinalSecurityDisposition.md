**16. Governance, Traceability, Maintenance, and Final Security Disposition**

**16.1 Purpose**

This section establishes the governance and lifecycle controls that preserve the integrity of this Threat Model after approval.

The Threat Model is a controlled security-engineering artifact. Its conclusions SHALL remain traceable to requirements, architecture, implementation, testing, evidence, risk decisions, and applicable Architecture Decision Records (ADRs).

This section establishes the rules required to prevent security-analysis drift, preserve historical decisions, maintain bidirectional traceability, control changes, and determine whether the Threat Model remains an accurate representation of the system's security posture.

This section also provides the **formal closure of the Threat Model**.

**16.2 Threat Model Authority**

The Threat Model is authoritative for the security analysis it explicitly owns, including:

- threat identification;

- threat actors;

- protected assets;

- security properties;

- attack paths;

- trust boundaries;

- security assumptions;

- security controls;

- risk assessments;

- residual risks;

- security-analysis conclusions;

- security-model review status.

The Threat Model SHALL NOT silently override another project artifact.

The following remain authoritative within their respective domains:

- SRS — functional requirements;

- NFR — non-functional requirements;

- TAS — architectural strategy;

- SDS — software design;

- DDS — detailed design;

- Test Specification — verification requirements and testing governance;

- Implementation Strategy — implementation lifecycle and phase gates;

- RTM — formal requirement traceability;

- ADRs — approved architectural decisions;

- project governance — document and decision lifecycle rules.

When these artifacts conflict, the conflict SHALL be explicitly identified and resolved through the applicable project governance process.

The Threat Model SHALL then be synchronized with the approved resolution.

**16.3 Source-of-Truth Boundaries**

The Threat Model SHALL analyze security consequences without becoming a duplicate of the project's other documents.

It SHALL reference authoritative information rather than independently redefining it.

The intended relationship is:

Requirements

↓

Architecture

↓

Design

↓

Implementation

↓

Threat Analysis

↓

Security Verification

↓

Evidence

The Threat Model validates the security implications of the system represented by these artifacts.

It SHALL NOT silently substitute an alternative architecture, implementation, requirement, or verification result.

**16.4 Bidirectional Security Traceability**

Security traceability SHALL operate in both directions.

A threat SHALL be traceable, where applicable, to:

- affected asset;

- security property;

- requirement;

- trust boundary;

- attack path;

- security control;

- implementation;

- security test;

- evidence;

- risk;

- residual risk;

- applicable ADR.

A security-relevant requirement SHALL be traceable, where applicable, to:

- relevant threat;

- affected asset;

- security control;

- implementation;

- security test;

- evidence.

The minimum conceptual relationship is:

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

A missing relationship SHALL be intentional and explainable.

**16.5 RTM Relationship**

The Requirements Traceability Matrix SHALL remain the formal project mechanism for requirement traceability.

The Threat Model SHALL NOT create a competing requirement-status system.

If Threat Model analysis identifies:

- an untraced security requirement;

- a threat with no applicable requirement;

- an obsolete requirement relationship;

- an incorrect requirement relationship;

- a requirement whose implementation has changed;

- a security requirement whose verification state is stale;

the discrepancy SHALL be raised through the established RTM governance process.

The Threat Model SHALL reference the resulting state rather than silently modifying the RTM.

**16.6 Threat Identification and Identifier Stability**

Every formal threat SHALL have a unique identifier.

Threat identifiers SHALL remain stable for the life of the threat.

Identifiers SHALL NOT be:

- silently changed;

- reused for unrelated threats;

- reassigned to conceal historical findings;

- removed merely because a threat has been mitigated.

When a threat is retired or superseded, its historical identifier SHALL remain traceable.

A materially different threat SHALL receive a new identifier.

This preserves the historical security record and prevents changes in analysis from obscuring prior conclusions.

**16.7 Security-Control Traceability**

Security controls SHALL be identifiable and traceable to the threats they mitigate.

Where a control originates from an existing requirement, the existing requirement identifier SHALL be retained.

Where a control is an architectural or implementation mechanism without a dedicated requirement, the Threat Model SHALL identify the mechanism sufficiently to establish its relationship to the threat.

A control SHALL NOT be represented as effective solely because it is specified.

Its status SHALL reflect its actual implementation and verification state.

**16.8 Separation of Security States**

The following states SHALL remain distinct:

- specified;

- planned;

- implemented;

- regression-verified;

- security-verified;

- deferred;

- not implemented;

- rejected;

- superseded;

- risk-accepted.

These states SHALL NOT be treated as interchangeable.

In particular:

**Implemented does not mean security-verified.**

A functional regression test demonstrates that the tested behavior operates as intended under the tested conditions.

A security-verification result requires evidence that the specific security property, threat, or control was tested against an appropriate security failure condition.

Risk acceptance does not constitute verification.

**16.9 Security Verification Evidence**

A mitigation SHALL be considered security-verified only when the required security-verification evidence exists.

Where applicable, evidence SHALL identify:

- requirement;

- threat;

- control;

- security test;

- build;

- configuration;

- device or execution environment;

- execution date;

- result;

- evidence artifact.

The existence of a test case alone SHALL NOT establish verification.

The existence of an implementation alone SHALL NOT establish verification.

A prior verification result SHALL be reassessed when the implementation, dependencies, configuration, architecture, or security boundary materially changes.

**16.10 ADR Relationship**

Architecture Decision Records SHALL be used for material architectural decisions affecting the security model.

The Threat Model SHALL reference applicable ADRs involving:

- trust boundaries;

- authentication;

- authorization;

- cryptography;

- key management;

- secure storage;

- enforcement;

- Accessibility Service architecture;

- Device Admin;

- security-sensitive IPC;

- recovery;

- security assumptions;

- material security tradeoffs.

The Threat Model SHALL record the security consequence of the decision but SHALL NOT duplicate the complete ADR.

**16.11 ADR Protection**

An approved ADR SHALL NOT be silently modified to make later Threat Model conclusions appear consistent.

If Threat Model analysis demonstrates that an existing architectural decision is inadequate:

1.  the conflict SHALL be documented;

2.  the security consequence SHALL be recorded;

3.  the applicable decision process SHALL be invoked;

4.  a new or superseding ADR SHALL be created when required;

5.  affected artifacts SHALL be updated;

6.  the Threat Model SHALL be synchronized with the approved decision.

Historical ADR decisions SHALL remain recoverable.

**16.12 Conflict Resolution**

When the Threat Model identifies conflicting project information, it SHALL NOT silently select the interpretation considered most convenient.

The conflict SHALL identify:

- conflicting artifacts;

- affected requirements;

- affected architecture;

- affected threats;

- affected controls;

- affected verification;

- security consequences.

The conflict SHALL be resolved through the established project governance mechanism.

The approved resolution becomes the basis for synchronization of affected artifacts.

**16.13 Change Control**

Material changes to the Threat Model SHALL be controlled.

A material change SHALL identify:

- what changed;

- why it changed;

- triggering event;

- affected threats;

- affected assets;

- affected controls;

- affected requirements;

- affected tests;

- affected evidence;

- affected ADRs;

- affected risks;

- affected security assumptions;

- affected trust boundaries;

- whether phase reassessment is required.

A change SHALL NOT be considered complete until affected traceability is reconciled.

**16.14 Change Classification**

Changes SHALL be classified according to their security significance.

**16.14.1 Administrative Changes**

Examples include:

- typographical corrections;

- formatting corrections;

- broken-reference corrections;

- non-substantive wording corrections.

Administrative changes SHALL NOT alter security conclusions.

**16.14.2 Analytical Changes**

Examples include:

- new threat;

- changed attack path;

- changed likelihood;

- changed impact;

- changed residual risk;

- revised control analysis.

Analytical changes SHALL receive appropriate security review.

**16.14.3 Architectural or Security Changes**

Examples include:

- changed trust boundary;

- changed authentication mechanism;

- changed authorization mechanism;

- changed cryptographic design;

- changed key-management design;

- changed enforcement architecture;

- changed security-critical IPC;

- changed recovery model.

Architectural or security changes SHALL invoke the applicable architecture and security governance processes.

**16.15 Approved Baseline Protection**

The approved Threat Model baseline SHALL be treated as a controlled security baseline.

Once approved:

- the document structure SHALL remain stable;

- section identifiers SHALL remain stable;

- threat identifiers SHALL remain stable;

- established security terminology SHALL remain stable;

- approved security assumptions SHALL not be silently rewritten;

- completed analysis SHALL not be discarded merely because implementation changes;

- risk changes SHALL remain historically traceable;

- verification claims SHALL remain evidence-backed.

The purpose of baseline protection is to prevent iterative implementation work from silently changing the security model.

**16.16 Structural Change Control**

Structural changes SHALL be treated differently from ordinary content maintenance.

A structural change includes:

- adding or removing a section;

- merging sections;

- splitting sections;

- changing section purpose;

- materially changing the document hierarchy;

- relocating authoritative analysis between sections.

A structural change SHALL require explicit approval under project governance.

Content SHALL NOT be moved between sections merely for convenience if the movement changes the established organization or weakens traceability.

The approved Threat Model structure SHALL therefore be considered locked unless formally changed.

**16.17 Terminology Control**

The Threat Model SHALL use consistent security terminology.

The following concepts SHALL remain distinct:

- asset;

- threat actor;

- threat;

- attack;

- vulnerability;

- security control;

- mitigation;

- risk;

- residual risk;

- assumption;

- trust boundary;

- verification;

- evidence;

- exception;

- acceptance.

A term SHALL NOT acquire a conflicting meaning elsewhere in the Threat Model.

Where project governance establishes controlled terminology, that terminology SHALL prevail.

**16.18 Security Assumption Governance**

Security assumptions SHALL be explicit.

Each material assumption SHALL identify:

- what is trusted;

- why it is trusted;

- what security property depends on it;

- what happens if it fails;

- whether that failure is in scope;

- applicable mitigation;

- reassessment trigger.

For this Threat Model, material assumptions include the trust placed in:

- Android Keystore;

- Android application sandbox;

- Android package-management integrity;

- Device Admin framework;

- Accessibility framework;

- trusted device authentication mechanisms;

- legitimate owner authority.

Assumptions SHALL NOT remain hidden dependencies of the security model.

**16.19 Trust-Boundary Governance**

Changes that introduce, remove, or materially alter a trust boundary SHALL trigger security analysis.

The Threat Model SHALL maintain explicit consideration of the boundaries between:

- unauthenticated and authenticated App Lock state;

- App Lock and protected third-party applications;

- App Lock and other applications;

- App Lock private storage and external actors;

- application code and Android Keystore;

- App Lock and Accessibility framework;

- App Lock and Device Admin framework;

- App Lock and boot/watchdog mechanisms;

- App Lock UI and potentially hostile UI overlays or accessibility services.

A new communication path crossing a security boundary SHALL not be treated as an ordinary implementation detail.

**16.20 Assumption Failure**

If a security assumption becomes invalid, affected threats SHALL be reassessed.

Examples include:

- material Android platform behavior changes;

- Keystore behavior changes;

- application-sandbox assumptions changing;

- Accessibility framework behavior changing;

- Device Admin behavior changing;

- security-relevant platform restrictions changing.

The reassessment SHALL determine whether the change:

- invalidates a control;

- increases risk;

- introduces a new threat;

- changes a trust boundary;

- requires an ADR;

- requires security testing;

- requires phase reassessment.

**16.21 Cross-Document Synchronization**

A security-relevant change SHALL be evaluated against all affected project artifacts.

Potentially affected artifacts include:

- SRS;

- NFR;

- TAS;

- SDS;

- DDS;

- Secure Coding Standard;

- Test Specification;

- Implementation Strategy;

- RTM;

- ADRs;

- Deployment and Operations documentation.

The Threat Model SHALL NOT become an isolated security interpretation that diverges from the implementation and architecture.

Where a material change affects another authoritative artifact, that artifact SHALL be updated through its applicable governance process.

**16.22 Continuous Verification**

Threat Model maintenance SHALL remain connected to continuous verification.

When a security-relevant implementation change occurs:

1.  affected threats SHALL be identified;

2.  affected assets SHALL be identified;

3.  affected controls SHALL be identified;

4.  affected requirements SHALL be identified;

5.  existing verification evidence SHALL be assessed for continued validity;

6.  required regression testing SHALL be identified;

7.  required security testing SHALL be identified;

8.  risk SHALL be reassessed where necessary;

9.  traceability SHALL be updated;

10. affected ADRs SHALL be reviewed.

A successful build SHALL NOT by itself close a Threat Model change.

**16.23 Verification Invalidation**

A previously verified security control SHALL be returned to a verification-required state when a material change can affect the verified security property.

Examples include changes to:

- implementation;

- security-critical dependencies;

- cryptographic primitives;

- key handling;

- authentication;

- authorization;

- storage;

- IPC;

- trust boundaries;

- platform assumptions;

- security configuration.

Previous evidence SHALL remain historically valid for the state in which it was generated, but SHALL NOT automatically be treated as proof of the changed implementation.

**16.24 Review Triggers**

Threat Model reassessment SHALL occur when any of the following occurs:

- a security requirement is added, removed, or materially changed;

- a security control is added, removed, or materially changed;

- authentication architecture changes;

- authorization architecture changes;

- cryptographic or key-management behavior changes;

- protected storage changes;

- the enforcement mechanism changes;

- the Accessibility detection approach changes;

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

**16.25 Historical Security Failures**

Previously discovered security failures SHALL remain part of the Threat Model's historical evidence when they provide meaningful information about attack paths or control effectiveness.

The preserved historical cases include:

- self-gate bypass;

- fast-relaunch bypass;

- fast-switch relock defect;

- release-build cryptographic dependency failure;

- historical plaintext database storage;

- force-stop/accessibility availability limitation.

A fixed vulnerability SHALL remain traceable to the control that corrected it and the verification evidence demonstrating the correction.

Historical failures SHALL not be removed simply because they are no longer reproducible.

**16.26 Known Residual Risks**

The Threat Model SHALL distinguish resolved vulnerabilities from residual risks that remain accepted or incompletely mitigated.

The current security analysis identifies, at minimum:

1.  Accessibility-based enforcement can fail or become unavailable.

2.  Accessibility can be enabled while event delivery is not functioning as expected.

3.  Force-stop and platform/OEM behavior can interrupt enforcement.

4.  The enforcement architecture can fail open when detection disappears.

5.  A malicious overlay can obscure the authentication UI.

6.  Peer accessibility services can observe or inject UI interaction.

7.  Root/system compromise lies below the application's trust boundary.

8.  Vault and database encryption keys are independent of the user's PIN.

9.  Keystore invalidation currently lacks a recovery path.

10. Several security-hardening controls remain planned rather than implemented.

These conditions SHALL NOT be represented as resolved merely because other controls are functioning.

**16.27 Planned Controls**

Controls that are specified but not implemented SHALL remain explicitly identified as planned or not-started.

In particular, the Threat Model SHALL NOT treat the following as effective mitigations until implementation and appropriate verification evidence exist:

- root detection;

- root response;

- tamper detection;

- debug protection;

- anti-tapjacking/overlay-obscure defense;

- Keystore-invalidation recovery;

- security-specific audit-log tamper evidence;

- other security controls identified as not-started by the authoritative RTM.

Planned functionality MAY reduce future risk, but SHALL NOT reduce the current risk rating merely because it is scheduled.

**16.28 Risk-State Integrity**

Risk ratings SHALL describe the current system state supported by evidence.

A risk rating SHALL NOT be reduced because:

- a mitigation is planned;

- a requirement exists;

- an implementation is incomplete;

- a future phase is expected to address it;

- a test has been written but not executed;

- a regression test passed without testing the specific security property.

Risk reduction SHALL require an actual change in exposure and appropriate supporting evidence.

**16.29 Risk Acceptance**

Risk acceptance SHALL be explicit.

Where a risk remains after available mitigation, the record SHALL identify:

- affected threat;

- affected asset;

- current likelihood;

- current impact;

- resulting risk;

- existing controls;

- residual exposure;

- reason for acceptance;

- applicable owner or authority;

- review trigger.

Risk acceptance SHALL NOT be used to conceal an unresolved implementation defect.

**16.30 Governance Failure Conditions**

The following SHALL constitute Threat Model governance failures:

- undocumented security conclusions;

- silent modification of approved risk;

- deletion of historical threats without disposition;

- reuse of retired threat identifiers;

- claiming security verification without evidence;

- treating implementation as verification;

- treating planned controls as effective controls;

- silently overriding an ADR;

- silently resolving document conflicts;

- allowing traceability to become stale;

- modifying the approved structure without authorization;

- changing a security assumption without reassessment;

- changing a trust boundary without threat analysis;

- allowing historical security evidence to disappear;

- retaining a verification status after a material change invalidates its evidence.

Governance failures SHALL be corrected through the applicable project governance process.

**16.31 Mandatory Drift-Prevention Invariants**

The following SHALL be treated as permanent invariants of the approved Threat Model:

1.  The approved structure SHALL NOT drift silently.

2.  Section identifiers SHALL remain stable.

3.  Threat identifiers SHALL remain stable.

4.  Historical threats SHALL remain recoverable.

5.  Approved assumptions SHALL NOT be silently changed.

6.  Trust-boundary changes SHALL trigger analysis.

7.  Material security changes SHALL trigger impact assessment.

8.  Risk ratings SHALL NOT change without rationale.

9.  Security-verification status SHALL NOT change without evidence.

10. Planned controls SHALL NOT be represented as implemented.

11. Implemented controls SHALL NOT be represented as security-verified without qualifying evidence.

12. Regression verification SHALL NOT automatically constitute security verification.

13. Approved ADRs SHALL NOT be silently overridden.

14. RTM relationships SHALL remain synchronized.

15. Previous verification SHALL be reassessed after material change.

16. Historical security failures SHALL remain traceable.

17. Superseded decisions SHALL remain recoverable.

18. Document conflicts SHALL be explicitly resolved.

19. Security assumptions SHALL remain explicit.

20. The current security posture SHALL always be distinguishable from planned future improvements.

These invariants exist specifically to prevent the Threat Model from gradually becoming less accurate as implementation evolves.

**16.32 Final Security Posture**

Based on the current approved security analysis and the governed two-tier detection architecture, App Lock's security boundary is fundamentally composed of:

Android Platform Trust

│

├── Application Sandbox

├── Android Keystore

├── Package Manager

├── Device Admin Framework

├── UsageStatsManager / Usage Access

└── Optional Accessibility Framework

│

▼

Detection-Source Selection

│

▼

Trigger Processor

│

▼

App Lock Enforcement

│

┌───────┴────────┐

▼ ▼

Protected Apps App Lock Vault

│

┌─────┴─────┐

▼ ▼

Database Encrypted Files

**16.32.1 Protected Assets**

The primary security guarantees are intended to protect:

- the App Lock credential;

- database encryption material;

- vault payloads;

- intruder photographs;

- encrypted database contents and metadata;

- protected-app configuration;

- security and intruder-event records;

- enforcement state.

The Threat Model SHALL treat the Vault and App Lock's own confidential data store separately from the protected-application enforcement function. Compromise of either boundary constitutes a security concern, but the applicable attack paths and controls are different.

**16.32.2 Primary Authentication Boundary**

The App Lock authentication session is the principal authorization boundary for protected applications and App Lock's own sensitive UI.

Android device unlock SHALL NOT constitute App Lock authorization.

A protected application SHALL require an appropriate App Lock authorization session regardless of whether the Android device itself is already unlocked.

The Vault SHALL remain protected by its App Lock authentication boundary at the user-interface level, while its stored contents remain protected at rest by the Android Keystore-backed encryption architecture.

**16.32.3 Data-at-Rest Boundary**

The Vault, intruder photos, and database depend on Android Keystore-backed encryption and the application sandbox for confidentiality against attackers who do not possess privileges sufficient to defeat the Android platform trust boundary.

The database encryption key and vault encryption keys SHALL remain independent of the user's PIN.

The PIN SHALL therefore be treated as an authentication credential and not as the cryptographic root of the Vault or database.

**16.32.4 Credential Boundary**

The PIN is independently protected through Argon2id hashing and Keystore-protected storage.

The Threat Model SHALL preserve the distinction between:

- proving knowledge of the App Lock credential;

- possessing the cryptographic material protecting stored data.

Knowledge of the PIN authorizes the legitimate owner.

Possession of the underlying encryption keys is a separate security boundary.

**16.32.5 Detection and Enforcement Architecture**

Foreground detection is implemented as a **two-tier detection architecture**.

The **baseline tier** is the mandatory detection path and is designed to permit App Lock to function without Accessibility being enabled.

The baseline tier uses:

- Android UsageStatsManager;

- the Android **Usage Access** special permission;

- a foreground service that samples usage events to identify the current foreground application.

Because presenting the lock interface from a background context is subject to Android background-activity-launch restrictions, the baseline tier also requires an approved mechanism for presenting the lock interface. The architecture currently identifies the use of **display-over-other-apps / system alert window permission** as the expected mechanism, either for overlay presentation or for obtaining the applicable activity-launch exemption.

The final implementation decision between those presentation approaches remains an implementation decision for the Core Security Platform phase.

The baseline tier SHALL remain operational when Accessibility is disabled.

**16.32.6 Optional Accessibility Enhancement**

Accessibility-based detection is an **optional enhancement tier**.

It SHALL NOT be a prerequisite for using App Lock.

When enabled by the user, the Accessibility service provides event-driven foreground detection with substantially faster response than periodic UsageStats sampling and can reduce the battery cost associated with frequent polling.

The enhancement tier SHALL therefore be treated as an optimization of responsiveness and battery behavior rather than a required security dependency.

The application SHALL remain capable of enforcing protected-application locking when the user declines to enable Accessibility.

**16.32.7 Detection-Source Selection**

A detection-source selection layer SHALL determine which detection tier is active.

The intended behavior is:

App Lock Detection

│

▼

Detection-Source Selector

/ \\

/ \\

▼ ▼

Baseline Tier Enhancement Tier

Usage Access Accessibility

│ │

│ │

└────────┬─────────┘

▼

Trigger Processor

│

▼

Lock Engine

The baseline tier SHALL always remain available as the required detection mechanism, subject to its own required permissions and platform limitations.

The Accessibility enhancement tier SHALL only participate when the user has explicitly enabled it and the required permission remains available.

Loss or absence of Accessibility SHALL therefore degrade the application from the enhancement tier to the baseline tier rather than disabling App Lock protection entirely.

**16.32.8 Enforcement Boundary**

The lock engine remains the enforcement authority.

The introduction of the second detection source SHALL NOT create a second lock-enforcement implementation.

Both detection tiers SHALL converge through the Trigger Processor abstraction into the existing lock-enforcement path.

The architecture therefore preserves:

Detection Source

↓

Trigger Processor

↓

Lock Engine

↓

Authorization Evaluation

↓

Lock Screen

↓

Authenticated Session

The lock engine remains responsible for determining whether the foreground application requires authentication.

The detection source SHALL identify the foreground transition; it SHALL NOT independently implement authorization policy.

**16.32.9 Accessibility Failure Semantics**

Accessibility loss SHALL no longer be classified as an application-wide protection failure.

The security consequence depends on which detection tier is active.

If Accessibility is disabled while the baseline Usage Access tier remains operational:

- App Lock SHALL continue operating;

- protected applications SHALL continue to be detected;

- the user SHALL remain able to use App Lock;

- the optional responsiveness enhancement SHALL be unavailable.

If the baseline tier loses its required permission or otherwise becomes unhealthy:

- the application SHALL detect the condition where technically possible;

- the condition SHALL be recorded through the applicable health and security-monitoring mechanisms;

- the user SHALL be notified when appropriate;

- the application SHALL not represent the baseline detector as healthy when it cannot perform its required function.

The previous architecture's assumption that loss of Accessibility necessarily removes the entire enforcement mechanism is therefore superseded.

**16.32.10 Accessibility Security Risk**

The project-internal Accessibility risk remains relevant but is materially reduced by the two-tier architecture.

The original risk contains three principal facets:

1.  Google Play scrutiny of Accessibility services used for non-accessibility purposes;

2.  Android Restricted Settings behavior that can prevent Accessibility from being granted in applicable installation circumstances;

3.  silent failure in which Accessibility appears enabled but does not deliver the expected events.

The two-tier architecture addresses these as follows:

| **Risk facet** | **Effect of two-tier architecture** |
|----|----|
| Play-policy scrutiny | Accessibility is no longer a mandatory dependency for core App Lock operation |
| Restricted Settings | Users can operate App Lock using the baseline tier without Accessibility |
| Silent Accessibility failure | Impact is limited to the optional enhancement path when the baseline remains healthy |

The first two risks are therefore substantially reduced by removing Accessibility as a hard dependency.

The third is narrowed from a potential application-wide enforcement failure to an enhancement-tier health issue.

The baseline tier introduces its own permission and health dependencies, particularly Usage Access and the mechanism used to present the lock interface. Those dependencies SHALL therefore become explicit security and health considerations rather than being treated as implicit platform assumptions.

**16.32.11 Baseline Detection Trade-Off**

The baseline tier introduces a deliberate responsiveness and battery trade-off.

UsageStats sampling introduces detection latency related to the sampling interval and creates a recurring battery cost.

The Accessibility enhancement tier is event-driven and therefore provides faster detection with potentially lower battery impact than equivalent high-frequency sampling.

The security model SHALL therefore distinguish:

- **security availability** — whether protected applications remain subject to App Lock enforcement;

- **detection latency** — how quickly the lock is triggered after a protected application reaches the foreground;

- **battery efficiency** — the resource cost of maintaining detection.

A slower baseline detector SHALL not automatically be classified as a security failure if it remains within the approved security and performance requirements.

However, the baseline's actual detection delay SHALL be measured and evaluated against the applicable requirements.

**16.32.12 Current Implementation Status**

The two-tier detection architecture is an approved architectural direction, but its corresponding implementation has not yet been completed.

The architecture decision record formalizing this change supersedes the earlier Accessibility-only architectural decision.

The implementation is scheduled for the **Core Security Platform phase (Implementation Strategy Phase 1)**.

Until that implementation is complete:

- the delivered build continues to rely on Accessibility-based detection;

- Accessibility remains a hard dependency of the current implementation;

- the Accessibility-related project risk remains open;

- the baseline Usage Access detector is not yet an effective mitigation;

- the optional enhancement model is not yet implemented.

The Threat Model SHALL therefore distinguish the **approved target architecture** from the **current delivered implementation**.

The existence of the approved architecture SHALL NOT be treated as evidence that the new controls are already effective.

**16.32.13 Remaining Implementation Decision**

One implementation detail remains intentionally open.

The Core Security Platform implementation SHALL determine whether the baseline lock interface is presented through:

1.  a system-alert-window overlay; or

2.  an activity launched using the applicable background-activity-launch exemption.

This decision SHALL be evaluated against:

- Android platform behavior;

- security properties;

- tapjacking and overlay risks;

- user experience;

- permission requirements;

- Play compliance;

- lifecycle reliability;

- testability.

The decision SHALL be formally recorded through the applicable architecture governance mechanism.

All other aspects of the two-tier detection model are fixed by the approved architectural direction.

**16.32.14 Final Enforcement Model**

The resulting security architecture is therefore:

Android Device Unlocked

│

▼

App Lock Detection

│

┌────────────┴────────────┐

│ │

▼ ▼

REQUIRED BASELINE OPTIONAL ENHANCEMENT

Usage Access Accessibility

UsageStatsManager Event-driven detection

│ │

└────────────┬────────────┘

▼

Detection Selection

│

▼

Trigger Processor

│

▼

Lock Engine

│

▼

Authorization Check

│

┌─────────┴─────────┐

│ │

Authentication Valid Session

│ │

▼ ▼

Lock Screen Protected App

The security model SHALL treat the **baseline Usage Access path as the required enforcement dependency** and the **Accessibility path as an optional enhancement**.

Accessibility SHALL NOT be required for App Lock to provide its core protected-application locking function.

Loss of Accessibility SHALL therefore not, by itself, constitute a loss of App Lock protection.

Loss or failure of the baseline detection mechanism SHALL remain a security-significant condition because the baseline detector is required for core enforcement.

**16.32.15 Final Security Posture Statement**

The approved security posture is:

App Lock SHALL continue to enforce protection of user-selected applications without requiring Accessibility to be enabled. Accessibility may be enabled by the user as an optional enhancement for faster, event-driven foreground detection. The baseline enforcement path uses Usage Access and the associated lock-interface presentation mechanism. Both detection paths converge on the same lock engine and authorization boundary.

This architecture removes Accessibility from the application's fundamental trust and availability dependency while retaining it as an optional performance and responsiveness enhancement.

The Threat Model SHALL therefore no longer describe Accessibility availability as the single or mandatory enforcement boundary.

The mandatory enforcement dependency is the **baseline detection path and its associated permission and presentation mechanisms**.

The Accessibility framework remains a security-relevant optional component whose failure affects enhancement functionality but does not, by itself, disable App Lock.

The target architecture is approved; its implementation remains subject to the Core Security Platform phase and subsequent security verification.

Until that implementation and verification occur, the delivered application's current Accessibility-only behavior and associated risk SHALL remain the operative security posture.

**16.33 Current Security-Verification Qualification**

The current project state SHALL be represented accurately.

Existing regression verification demonstrates that previously identified functional bypasses have been corrected and that the core behavior has been exercised on the available test devices.

That evidence SHALL NOT be represented as equivalent to a completed threat-model-driven security verification program.

At the current baseline:

- implemented-and-regression-verified controls exist;

- some security-relevant implementations remain unclassified or pending security verification;

- several hardening controls remain planned;

- no completed penetration-test program establishes comprehensive resistance to the threat model;

- the Threat Model establishes the security analysis required to drive subsequent security verification.

Accordingly, the Threat Model SHALL not claim that all identified threats have been eliminated.

**16.34 Final Security Conclusions**

The Threat Model establishes the following conclusions.

**16.34.1 Primary Security Boundary**

The App Lock authentication session is the principal authorization boundary for protected applications and App Lock's own sensitive UI.

**16.34.2 Data-at-Rest Boundary**

The vault, intruder photos, and database depend on Android Keystore-backed encryption and the application sandbox for confidentiality against non-root offline attackers.

**16.34.3 Credential Boundary**

The PIN is independently protected through Argon2id hashing and Keystore-protected storage.

The PIN is not used as the encryption key for the vault or database.

**16.34.4 Enforcement Boundary**

Continuous Accessibility-based detection is a security-critical availability dependency.

Loss of that mechanism can result in fail-open enforcement and therefore constitutes a security concern rather than an ordinary reliability issue.

**16.34.5 Platform Boundary**

A rooted or system-compromised operating system is outside the application's guaranteed protection boundary.

The application may provide best-effort detection and response, but SHALL NOT claim cryptographic protection against an attacker who has defeated the operating system's trust boundaries.

**16.34.6 Security-Governance Boundary**

Security conclusions SHALL remain evidence-based and traceable.

No future implementation claim SHALL be allowed to silently convert a planned or merely implemented control into a verified security control.

**16.35 Final Baseline Statement**

This Threat Model establishes the approved security baseline for the system as analyzed.

The baseline includes:

- the defined assets;

- the defined security properties;

- the defined threat actors;

- the defined trust boundaries;

- the defined security assumptions;

- the defined attack paths;

- the defined security controls;

- the defined risk methodology;

- the identified residual risks;

- the documented historical failures;

- the current implementation and verification distinctions;

- the established traceability requirements;

- the established change-control requirements;

- the established reassessment triggers;

- the established drift-prevention invariants.

Any future change that materially affects these elements SHALL be evaluated against this baseline.

The existence of a future implementation, requirement, design proposal, or roadmap item SHALL NOT modify this baseline until that change has passed the applicable project governance, traceability, implementation, and verification processes.

**16.36 Threat Model Closure Criteria**

The Threat Model SHALL be considered complete and closed at the approved baseline when all of the following are true:

- all defined assets have security significance established;

- security properties are defined;

- in-scope and out-of-scope attackers are established;

- trust boundaries are identified;

- security assumptions are explicit;

- externally reachable components and security-sensitive interfaces are analyzed;

- primary authentication and authorization boundaries are analyzed;

- data-at-rest protection boundaries are analyzed;

- enforcement availability risks are analyzed;

- known historical failures are preserved;

- known residual risks are recorded;

- planned controls are distinguished from effective controls;

- risk methodology is established;

- security-control relationships are traceable;

- requirement relationships are traceable;

- verification requirements are distinguishable from implementation status;

- evidence requirements are established;

- ADR relationships are governed;

- change-control rules are established;

- Threat Model reassessment triggers are established;

- historical security conclusions are protected;

- structural drift is explicitly prohibited;

- risk acceptance is controlled;

- security-verification claims are evidence-based;

- the final security posture is explicitly stated;

- the approved baseline is formally identified.

**16.37 Final Disposition**

**Threat Model Status: APPROVED BASELINE**

This Threat Model is the controlled security-analysis baseline for the system represented by the approved project artifacts.

It SHALL remain valid until superseded through the established governance process.

Implementation activity SHALL be evaluated against this baseline rather than silently redefining it.

Security findings discovered after approval SHALL be handled as controlled changes, new threats, revised risks, verification findings, or architectural decisions as appropriate.

No section of this Threat Model is considered an invitation to extend the document structure informally.

The approved document is complete.
