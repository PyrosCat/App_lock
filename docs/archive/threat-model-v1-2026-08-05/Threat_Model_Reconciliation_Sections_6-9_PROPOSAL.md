# Threat Model — Architectural Reconciliation, Sections 6–9 (Corrected Proposal)

**Purpose.** These proposed replacement texts reconcile Threat Model Sections 6–9 with the approved two-tier foreground-detection architecture (ADR-013A; TM §16.32), consistent with the already-approved Sections 1–5 reconciliation now landed as Threat Model v2. Sections 6–9 in the current baseline model the Android Accessibility framework as the single, mandatory foreground-detection and enforcement mechanism; the texts below align them with the approved model while preserving the mandatory distinction between the **current delivered implementation** (still Accessibility-only) and the **approved target architecture** (Usage Access baseline + optional Accessibility). No target-architecture capability is represented as implemented or security-verified.

**How to apply.** Each block is a drop-in replacement for the identified subsection, or a **new** subsection revision where noted. The merge is a **superset**: every two-tier revision is incorporated and every still-valid original threat/scope/control item is preserved. Stable identifiers (THR-*, INV-*, AS-*, TA-*) are never reassigned. New attack surfaces reference the identifiers established in TM v2 §5.2 (AS-019 UsageStatsManager/Usage Access; AS-020 baseline lock-interface presentation mechanism; AS-021 detection-source selection layer) per the §5.30 traceability rule.

## Corrections applied to the client 6–9 draft (before approval)

1. **§6.32 stable-ID collision fixed (must-fix).** The client draft reused **INV-011** and **INV-012** for new detection invariants, which in the current baseline are **INV-011 "Loss of Enforcement Is a Security Condition"** and **INV-012 "Planned Controls Are Not Effective Controls."** The draft dropped the original INV-011 statement and renumbered "Planned Controls" to INV-013. **Fix:** original INV-011/INV-012 retained (INV-011 reworded tier-aware); the two new detection invariants added as **INV-013** and **INV-014**.
2. **§6.14 added (draft omission).** The client draft reconciled §6.15/§6.23 but omitted **§6.14 Protected-App Enforcement Path**, whose primary enforcement diagram was left Accessibility-only — which would create a new internal inconsistency inside §6. A two-tier §6.14 replacement is supplied.
3. **§6.28 added (draft omission).** The draft omitted **§6.28 Overlay and UI Trust Boundary**, whose "does not use a system overlay" statement is now partially stale because the approved baseline presentation (AS-020) may itself use an overlay. A current-vs-target §6.28 replacement is supplied that keeps the existing inbound-tapjacking gap.
4. **Superset specifics restored.** §6.15 and §9.6.1 (engine behaviors: *exclude App Lock's own package*, *exclude ignored system packages*, apply relock policy; and §6.15's detection-reliability significance), §9.6.2 (residual-risk-between-loss-and-recovery statement), and §9.16 (watchdog/service-fragility compensating control) — all preserved.
5. **Traceability alignment.** New detection surfaces in §6.34 (and referenced in §8.6) aligned to the AS-019/AS-020/AS-021 identifiers established in TM v2 §5.2.
6. **Draft hygiene.** The draft's tool preamble ("Full text of the 35 subsections…") and "Source Completeness" footer are not Threat Model content and are removed. The draft `.docx` contained Markdown as literal text (visible `**`, `|`, `1.`); this proposal supplies clean formatting for landing via the md→docx pipeline.
7. **Retitles / reorders confirmed (all appropriate).** §9.6.1 "Accessibility Detection" → "Two-Tier Foreground Detection"; THR-ENF-002/003/005 generalized "Accessibility"→"Detection"; THR-ACC-001/002/003 prefixed "Optional"; §8.22 top-three priority reordered to lead with THR-ENF-001.

**No change required** (included in the draft but already tier-neutral or already two-tier-compatible; landed unchanged): §6.29, §6.33, §7.2, §7.6.1, §7.23, §7.36, §8.23, §8.25, §9.19.

---

## Section 6 — Security Architecture and Trust Enforcement

### 6.14 Protected-App Enforcement Path — Replacement (new; draft omission)

The primary protected-app enforcement path is common to both approved detection tiers. Detection identifies a candidate foreground transition; authentication and authorization determine whether access is permitted.

    Protected App Launched
      -> Foreground Detection
           - Baseline: UsageStatsManager / Usage Access (mandatory)
           - Optional: Accessibility / AppDetectionService (enhancement)
      -> Detection-Source Selection / Trigger Processor
      -> ApplicationLockEngine
      -> LockSessionManager / LockPolicyManager
           - Valid Session -> Allow
           - No Valid Session -> LockScreenActivity -> Authentication
                - Failure -> Lockout
                - Success -> Session State -> Protected App

The enforcement decision therefore depends on the complete path rather than on any individual component or detection source. The lock engine SHALL NOT treat the identity of a detection source as proof of authorization.

The current delivered implementation differs from this target: it currently relies on Accessibility as the sole detection source. Until the baseline tier is implemented, the diagram's baseline branch is **planned** and the existing Accessibility availability risk remains open.

### 6.15 Foreground Detection Boundary — Replacement

Foreground detection is a security trigger boundary. The approved target architecture does not equate that boundary with the Accessibility framework.

**Baseline Detection Tier.** UsageStatsManager, together with the Android **Usage Access** special permission, provides the mandatory foreground-detection mechanism. A foreground service samples usage events to identify the current foreground application. Sampling introduces a detection-latency and battery-cost trade-off that must be measured and controlled during implementation and security verification.

Because background activity-launch restrictions may prevent a background service from directly presenting the lock interface, the baseline tier also depends on the approved presentation mechanism and, where required by that mechanism, the **display-over-other-apps / system-alert-window** permission or the applicable platform background-activity-launch exemption. The precise presentation choice remains an implementation decision within the approved architecture.

**Optional Enhancement Tier.** The existing Accessibility-based detection mechanism is retained as an optional user-enabled enhancement. It is event-driven and is intended to provide faster foreground detection than the sampled baseline. Accessibility is therefore a performance/responsiveness enhancement, not a mandatory prerequisite for App Lock use.

**Detection-Source Selection.** A detection-source selection layer determines which available source supplies triggers to the Trigger Processor. The baseline source remains the required foundation; the Accessibility source may be enabled as an enhancement.

Whichever tier supplies the trigger, the application lock engine evaluates the resulting package information and:

- ignores App Lock's own package where appropriate;
- ignores designated system packages;
- processes protected applications;
- applies the relevant session/relock policy;
- starts the authentication path when authorization is absent.

The security significance of this boundary is twofold. First, a correct authentication implementation is insufficient if the foreground application cannot reliably be detected. Second, the authorization engine must not trust the identity of a detection source as proof of authorization: a detection event only causes App Lock to evaluate policy and session state; authentication remains the authority for access.

The current implementation state must be distinguished from the approved architecture: the delivered build currently relies on Accessibility alone. Until the baseline tier is implemented, the existing Accessibility availability risk remains open.

### 6.22 Watchdog Architecture — Replacement

The watchdog provides security-health monitoring for the protected-app enforcement architecture rather than acting as a second authorization engine.

In the approved target architecture it must be capable of assessing the health of the mandatory baseline path and, separately, the optional Accessibility enhancement when that enhancement is enabled.

    Detection Configuration
      -> Expected Protection State
      -> ProtectionWatchdogService
      -> Health Evaluation
           - Baseline Healthy
           - Baseline Missing / Degraded
           - Optional Accessibility Healthy
           - Optional Accessibility Missing / Degraded

A loss of the optional Accessibility enhancement must not be reported as total loss of App Lock protection when the baseline path is healthy.

A loss or degradation of the mandatory baseline path is a security-critical condition. The watchdog SHALL detect and report it where technically possible and SHALL NOT represent the protection state as healthy when the required baseline is unavailable.

The watchdog cannot independently grant Usage Access or Accessibility permission. It therefore remains a detection and response mechanism, not a complete preventive control.

### 6.23 Protection Availability Boundary — Replacement

The approved target protection architecture is:

    Boot / Application Startup
      -> ProtectionWatchdogService
      -> Detection Configuration
           - Mandatory Usage Access Baseline
           - Optional Accessibility Enhancement
      -> Detection-Source Selection / Trigger Processor
      -> Foreground Detection
      -> Lock Enforcement

Failure of the mandatory baseline, the presentation mechanism required to display the lock interface, or another required security component can interrupt enforcement and is therefore a security condition.

Failure of the optional Accessibility enhancement is a degraded enhancement state. It becomes a total enforcement failure only if the mandatory baseline is also unavailable or the common enforcement path is otherwise unable to operate.

The current implementation differs from this target: Accessibility is currently the sole detection source. That difference remains an open implementation risk until the two-tier architecture is delivered.

### 6.28 Overlay and UI Trust Boundary — Replacement (new; draft omission)

The current delivered architecture does not use a system overlay as the primary lock-screen mechanism; LockScreenActivity is an Activity-based authentication surface. This subsection therefore contains an **inbound UI trust problem**: another application may attempt to place an overlay above the authentication interface where Android permits it.

    Untrusted Application -> System Overlay -> LockScreenActivity -> User Interaction

The current implementation does not yet establish a complete anti-tapjacking / obscured-touch defense. This remains an identified security gap and must not be treated as implemented protection.

The approved target architecture may additionally present the baseline lock interface via an App-Lock-owned overlay (AS-020; display-over-other-apps / system-alert-window) rather than an Activity; if that mechanism is chosen it adds an outbound overlay boundary to analyze when designed, and SHALL NOT be represented as implemented until built and security-verified.

### 6.29 Accessibility Peer-Service Boundary — No change required

Already two-tier-compatible in the current baseline ("the approved baseline therefore treats this as a best-effort defense area rather than a guaranteed protection boundary"). Landed unchanged.

### 6.32 Security-Critical Invariants — Replacement (INV identifiers corrected)

The architecture must preserve the following invariants. INV-001 through INV-010 are unchanged; INV-011 and INV-012 retain their existing identifiers and meaning (INV-011 reworded to be tier-aware); INV-013 and INV-014 are new detection invariants.

- **INV-001 — No Protected-App Access Without Authorization.** A protected application must not become usable through App Lock's enforcement path without valid App Lock authorization.
- **INV-002 — Device Unlock Does Not Authorize App Lock.** Android device authentication must never implicitly establish an App Lock session.
- **INV-003 — PIN Cannot Be Reset Without Authorization.** Changing the credential requires knowledge of the current credential.
- **INV-004 — Authentication Sessions Are Volatile.** App Lock authorization must not survive reboot or process death.
- **INV-005 — Lockout Survives Process Restart.** Authentication failure state must not be reset by ordinary process restart.
- **INV-006 — Vault UI Requires App Lock Authorization.** Returning to App Lock must not bypass its self-gate.
- **INV-007 — Vault Storage Does Not Depend on Plaintext PIN Persistence.** The plaintext PIN must never be stored as an encryption key or persistent secret.
- **INV-008 — Sensitive Storage Remains Encrypted.** Vault data, intruder photographs, credential material, and database contents must remain behind their defined encryption boundaries.
- **INV-009 — Exported Components Cannot Grant Sensitive Authorization.** External invocation of an exported component must not establish App Lock authorization.
- **INV-010 — Reboot Does Not Create Access.** Reboot must clear authorization sessions without weakening persistent protection.
- **INV-011 — Loss of Enforcement Is a Security Condition.** Loss or degradation of a required enforcement mechanism — the mandatory baseline detection path, the required lock-interface presentation capability, watchdog operation, or equivalent — must be treated as a security-relevant condition, not merely a reliability event.
- **INV-012 — Planned Controls Are Not Effective Controls.** A specified but unimplemented security mechanism must never be represented as providing current protection.
- **INV-013 — Mandatory Detection Does Not Depend on Accessibility.** The approved protected-app enforcement architecture must remain operational with Accessibility disabled. Accessibility may enhance detection responsiveness but must not be the sole required foreground-detection mechanism.
- **INV-014 — Detection Failure Is Classified by Tier.** Loss of the mandatory baseline is an enforcement-security failure. Loss of the optional Accessibility enhancement is an enhancement degradation unless the baseline is also unavailable.

### 6.33 Security Architecture Failure Conditions — No change required

Tier-neutral (outcome-based) in the current baseline. Landed unchanged.

### 6.34 Architecture-to-Attack-Surface Relationship — Replacement

The relationship between Sections 5 and 6 is intentionally one-to-one at the architectural level, with the detection attack surface explicitly separated into its baseline and optional tiers. Attack-surface identifiers are those established in TM v2 §5.2.

| Attack Surface | Primary Security Boundary |
|---|---|
| MainActivity | Application self-gate |
| LockScreenActivity | Authentication boundary |
| AppDetectionService (AS-003) | Optional Accessibility detection enhancement |
| ProtectionWatchdogService | Protection-health monitoring |
| BootReceiver | Reboot persistence |
| UninstallProtectionReceiver | Device Admin |
| UsageStatsManager / Usage Access (AS-019) | Mandatory baseline foreground detection |
| Baseline lock-interface presentation mechanism (AS-020) | Lock-interface presentation boundary |
| Accessibility framework (AS-007) | Optional enhancement foreground detection and peer-service boundary |
| Android Keystore | Cryptographic root of trust |
| Private storage | Sandbox + encryption |
| SQLCipher database | Database confidentiality/integrity |
| Encrypted file store | Payload confidentiality/integrity |
| Authentication UI | Credential authorization |
| Session state | Runtime authorization |
| Lifecycle | Session/enforcement continuity |
| Notifications | Information-disclosure boundary |
| Installation/update | Software integrity |

This mapping provides the foundation for threat/control traceability. The current implementation state must be distinguished from the approved target architecture wherever a target component (AS-019/AS-020, and the detection-source selection layer AS-021) has not yet been implemented.

### 6.35 Security Architecture Change Control — Replacement

The architecture described here is locked to the approved baseline.

A change to any of the following requires security-architecture impact assessment:

- Root of trust.
- Credential architecture.
- Database-key architecture.
- Vault encryption architecture.
- Authentication boundary.
- Session model.
- Protected-app enforcement path.
- Foreground-detection architecture, including the Usage Access baseline, optional Accessibility enhancement, detection-source selection, and lock-interface presentation mechanism.
- Watchdog/recovery architecture.
- Boot persistence.
- Device Admin usage.
- Exported components.
- Android permissions forming security boundaries.
- Storage architecture.
- Backup/restore architecture.
- Runtime authorization model.

A change must not be incorporated into the Threat Model merely by editing this section. The corresponding architectural decision, requirement, implementation, and traceability artifacts must be updated through the project's approved change-control process.

---

## Section 7 — Threat Model Methodology and Threat Taxonomy

### 7.2 Threat Model Baseline — No change required

Landed unchanged (tier-neutral).

### 7.6.1 Attack Surface — No change required

Landed unchanged (references Section 5, which is already reconciled in v2).

### 7.10 Attack-Surface Categories — Replacement (AS-002 only; other categories unchanged)

Category **AS-002 — Android Framework Interfaces** is updated; all other categories (AS-001, AS-003–AS-008) are landed unchanged.

**AS-002 — Android Framework Interfaces.** Includes:

- UsageStatsManager and Usage Access.
- Display-over-other-apps / system-alert-window, or the approved background-activity presentation mechanism.
- Accessibility as an optional enhancement.
- Device Admin.
- Boot lifecycle.
- Application lifecycle.
- Package management.
- BiometricPrompt.
- Keystore.

*(Note: the §7.10 category identifiers AS-001…AS-008 are threat-taxonomy categories and are distinct from the Section 5 surface identifiers AS-001…AS-021. This overlap is pre-existing in the baseline and is out of scope for this reconciliation.)*

### 7.11 Threat Classes — Replacement (T-A only; other classes unchanged)

Class **T-A — Availability Threats** is updated; all other classes are landed unchanged.

**T-A — Availability Threats.** Interference with security enforcement. Examples:

- Mandatory baseline detection disruption.
- Optional Accessibility enhancement disruption.
- Watchdog disruption.
- Boot persistence failure.
- Permission removal.
- Process interference.

### 7.23 Accepted Platform Limitations — No change required

Landed unchanged. *(Optional editorial: the bullet "Force-stop effects on Accessibility enforcement" may be generalized to "Force-stop effects on enforcement, including baseline and optional Accessibility detection" for consistency; not required.)*

### 7.29 Single-Point-of-Failure Analysis — Replacement

A security control that is required for enforcement but has no effective independent fallback must be treated as a potential single point of failure. Under the approved architecture, the analysis distinguishes the two detection tiers.

**Mandatory baseline:** UsageStatsManager + Usage Access, together with the required lock-interface presentation mechanism, is the baseline enforcement path. Until another effective baseline exists, loss of this path is a single-point-of-failure condition for foreground detection.

**Optional enhancement:** Accessibility is not a single point of failure for the final architecture because the application must remain usable and capable of protected-app enforcement without it. Its loss is a degraded enhancement condition when the baseline remains healthy.

The Threat Model must ask, for each tier:

1. What happens if the mechanism stops?
2. How quickly is failure detected?
3. Can detection itself fail?
4. Does the system fail open or fail closed?
5. What security exposure results?
6. What recovery is available?
7. Can an attacker deliberately induce the failure?
8. Does loss of the mechanism remove the only effective enforcement path?

The baseline tier is not yet built, so in the current build Accessibility remains this single point of failure until it is implemented.

### 7.30 Fail-Open and Fail-Closed Classification — Replacement

Every security-critical enforcement path must explicitly identify its failure behavior. The approved target architecture is **fail-open with detection/recovery assistance** at the detection layer; it does not guarantee fail-closed enforcement against loss of all detection capability.

For the mandatory baseline:

- loss of Usage Access, baseline detection, or the required presentation mechanism can prevent timely lock enforcement;
- the watchdog is expected to detect certain classes of loss;
- detection is not instantaneous;
- recovery requires user/platform action where Android controls the permission;
- the final architecture must not represent the protection state as healthy when the baseline is unavailable.

For the optional Accessibility enhancement:

- loss of Accessibility reduces the enhancement capability;
- the baseline remains the required protection path;
- Accessibility loss alone must not be classified as total enforcement loss after the two-tier architecture is implemented.

In the current build (baseline not yet implemented), Accessibility loss remains a total enforcement-availability risk; this current-state distinction must remain visible in risk analysis.

### 7.35 Threat Reassessment Triggers — Replacement

Threat Model reassessment is required when any of the following occurs (the foreground-detection triggers are expanded for the two-tier architecture; all other triggers unchanged):

- A security control changes.
- A security-relevant architectural decision changes.
- The authentication mechanism changes.
- Credential handling changes.
- Cryptographic algorithms or key handling change.
- Storage architecture changes.
- The foreground-detection mechanism changes.
- Usage Access, baseline sampling, or lock-interface presentation changes.
- Accessibility enhancement behavior or availability changes.
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

### 7.36 Baseline Drift Prevention — No change required

Landed unchanged (tier-neutral).

---

## Section 8 — Threat Identification and Analysis

### 8.3 Threat Identification Matrix — Replacement

The initial threat inventory is organized into the following security domains. All existing domains and threat-ID ranges are unchanged. Tiered detection-source availability is analyzed within Protected-App Enforcement (THR-ENF-001/006) and the tier-specific attack paths in §8.6, rather than as a separate domain.

| Domain | Threat IDs | Primary Properties |
|---|---|---|
| Credential | THR-CRED-001–004 | Confidentiality, authentication, integrity |
| Authentication | THR-AUTH-001–004 | Authentication, authorization |
| Protected-App Enforcement | THR-ENF-001–006 | Authorization, availability |
| Session Management | THR-SES-001–004 | Authorization |
| Vault | THR-VAULT-001–005 | Confidentiality, integrity, authorization |
| Cryptographic Storage | THR-CRYPTO-001–005 | Confidentiality, integrity |
| UI / Navigation | THR-UI-001–005 | Authentication, authorization |
| IPC / Components | THR-IPC-001–004 | Authorization, integrity |
| Accessibility | THR-ACC-001–005 | Availability, authorization |
| Lifecycle / Boot | THR-LIFE-001–005 | Availability, authorization |
| Device Admin | THR-DA-001–002 | Availability, integrity |
| Audit / Security State | THR-AUD-001–003 | Integrity, accountability |
| Recovery / Migration | THR-REC-001–005 | Confidentiality, integrity, availability |
| Application Integrity | THR-INT-001–004 | Integrity |
| Platform / Root | THR-PLAT-001–004 | Confidentiality, integrity |
| Supply Chain | THR-SUP-001–003 | Integrity |

The identifiers are stable identifiers. Threats must not be renumbered merely because their ordering or priority changes.

### 8.6 Protected-App Enforcement Threats — Replacement

THR-ENF-001 through THR-ENF-006 keep their identifiers. THR-ENF-002/003/005 are generalized from "Accessibility" to "Detection" to reflect the two-tier architecture; the historical Accessibility silent-failure content is preserved within THR-ENF-003.

**THR-ENF-001 — Foreground Detection Failure.** App Lock fails to detect that a protected application has become the foreground application. *Attackers:* A-002, A-003, A-005. *Security property:* authorization and availability. *Attack surface (approved target detection path):* UsageStatsManager and Usage Access baseline (AS-019); the lock-interface presentation mechanism required by the platform (AS-020); optional Accessibility enhancement (AS-003/AS-007); detection-source selection / Trigger Processor (AS-021). *Impact:* a protected application may become usable without App Lock authentication if the effective detection path fails. *Current state:* the final architecture is designed to remain operational without Accessibility; however, the current delivered build is still Accessibility-only, so the present implementation remains exposed to the existing Accessibility availability risk until the baseline is implemented.

**THR-ENF-002 — Deliberate Detection Disruption.** An attacker deliberately interferes with a foreground-detection mechanism or its required permission state so that protected applications are not detected and locked. *Attackers:* A-002, A-003, A-005, A-006. *Attack paths:* removing Usage Access; interfering with the lock-interface presentation permission or mechanism; revoking optional Accessibility permission; causing a detection service to terminate; force-stopping App Lock; exploiting platform/OEM restrictions; causing the watchdog to terminate or become ineffective. The security impact of each path depends on whether it removes the mandatory baseline or only the optional enhancement.

**THR-ENF-003 — Silent Detection Failure.** A configured detection source appears enabled but no longer produces reliable foreground triggers. This includes the existing Accessibility silent-event failure and any equivalent silent failure of the UsageStats sampling path. *Security significance:* silent failure is more severe than an obvious permission revocation because configuration state may appear healthy while the effective detection path is not functioning. *Impact:* protected applications may open without authentication while App Lock reports apparently normal configuration. *Status:* open threat requiring explicit Core Security analysis and tier-specific health testing.

**THR-ENF-004 — Enforcement Race During Application Switching.** An attacker rapidly switches applications or relaunches a protected application during the transition between detection, lock-screen presentation, and authentication. *Historical evidence:* fast-switch relock defects; fast-relaunch bypass; historical lock-screen lifecycle failures. *Current mitigation:* the enforcement engine evaluates protected-app foreground triggers rather than relying solely on a prior package state; the approved detection architecture must preserve that behavior regardless of which detection tier generated the trigger. *Security significance:* the historical defect remains a required regression/security scenario.

**THR-ENF-005 — Enforcement Bypass Through Detection-Service Restart State.** An attacker manipulates detection-source or process lifecycle so that a stale authorization or incomplete detection state remains active after enforcement components restart. *Attack surface:* baseline detection path (AS-019); AppDetectionService where the optional enhancement is enabled (AS-003); ProtectionWatchdogService; LockSessionManager; process lifecycle. *Security property:* authorization integrity. *Required invariant:* detection-source restart must not create or restore unauthorized authorization state.

**THR-ENF-006 — Enforcement Availability Failure.** App Lock loses the ability to continuously enforce protected-app authentication because the mandatory baseline, required lock-interface presentation path, or another common security component is unavailable. *Relevant components:* Usage Access baseline (AS-019); lock-interface presentation mechanism (AS-020); watchdog; boot receiver; Device Admin protection where enabled; optional Accessibility enhancement where enabled. *Security significance:* availability is itself a security property because enforcement failure may result in unauthorized application access; the threat must distinguish complete loss of the mandatory baseline from degradation of the optional Accessibility enhancement.

### 8.12 Accessibility Threats — Replacement

Accessibility threats remain part of the Threat Model because Accessibility is an optional enhancement and a platform trust boundary. They must no longer be modeled as the sole enforcement dependency of the final architecture. THR-ACC-001/002/003 are prefixed "Optional"; identifiers and count are unchanged.

**THR-ACC-001 — Optional Accessibility Permission Revocation.** An attacker or platform condition causes the optional App Lock Accessibility permission to be removed. *Impact:* the event-driven enhancement becomes unavailable. *Security consequence:* in the approved final architecture, the baseline Usage Access path remains responsible for core enforcement, so Accessibility loss alone does not constitute total App Lock protection loss; in the current delivered build, where Accessibility remains the sole detection source, the same condition remains a total enforcement availability risk.

**THR-ACC-002 — Optional Accessibility Service Unbind.** An attacker or platform event causes AppDetectionService to become unbound. *Impact:* the optional event-driven foreground detector stops producing events. *Recovery:* health monitoring and user recovery guidance where available.

**THR-ACC-003 — Optional Accessibility Silent Failure.** The service remains nominally enabled but fails to deliver usable events. *Security significance:* this remains an important enhancement-tier threat and a historical current-build risk; it must not be allowed to masquerade as healthy optional detection.

**THR-ACC-004 — Malicious Peer Accessibility Service.** A malicious Accessibility Service observes or manipulates the App Lock authentication UI or injects accessibility actions. *Security property:* authentication and confidentiality. *Current position:* best-effort defense; complete prevention is not guaranteed against an Android-granted peer Accessibility Service. This threat remains relevant even though App Lock does not require Accessibility for the baseline architecture, because a hostile peer service can still attack the authentication UI when the optional enhancement is enabled and can interact with other UI surfaces independently.

**THR-ACC-005 — Restricted Settings / Platform Enforcement.** Android platform restrictions prevent the user from enabling or restoring the optional App Lock Accessibility enhancement under certain installation conditions. *Impact:* loss of the enhancement capability. *Classification:* platform limitation and deployment risk. *Security significance:* the baseline architecture is specifically intended to prevent this condition from making Accessibility a prerequisite for using App Lock.

### 8.13 Lifecycle and Boot Threats — Replacement

**THR-LIFE-001 — Force-Stop Enforcement Loss.** An attacker with sufficient device control force-stops App Lock. *Impact:* the application process, watchdog, and any in-process detection components stop. *Current security behavior:* the current delivered build cannot guarantee continued enforcement after force-stop because it remains Accessibility-only; the approved target architecture must separately assess restoration of the mandatory baseline and must not imply that a force-stop can be silently defeated by the application. *Classification:* known fail-open availability threat.

**THR-LIFE-002 — Process Death With Authorization Confusion.** The process dies and restarts with inconsistent security state. *Required property:* authorization sessions must not be restored automatically. *Current architecture:* sessions are volatile.

**THR-LIFE-003 — OEM Background Restriction.** An OEM or system policy terminates or prevents restart of a security-critical background component. *Impact:* potential loss of baseline detection, optional enhancement detection, watchdog monitoring, or the required presentation path. *Classification:* platform-dependent availability threat.

**THR-LIFE-004 — Boot Re-Arm Failure.** After reboot, App Lock fails to restore the mandatory baseline enforcement path. *Impact:* protected applications may remain unprotected. *Required property:* reboot must clear authorization but restore the required enforcement infrastructure.

**THR-LIFE-005 — Startup Security Race.** The system reaches a usable state before App Lock has re-established required protection. *Security significance:* startup ordering must not create an unrecognized window in which protected applications can be accessed without the intended security state; the analysis must include both baseline detection initialization and optional Accessibility initialization when enabled.

### 8.21.1 Enforcement-Availability Chain — Replacement

The final architecture distinguishes loss of the mandatory baseline from loss of the optional enhancement.

**Baseline failure chain:**

    Usage Access / Baseline Detection Loss
      -> Foreground Detection Degraded or Silent
      -> Protected App Not Triggering Lock Evaluation
      -> Protected App May Open
      -> Authorization Boundary Bypassed

**Optional enhancement failure chain:**

    Accessibility Loss
      -> Optional Enhancement Unavailable
      -> Baseline Remains Active
      -> Core Enforcement Continues

The second chain is the architectural reason Accessibility is no longer a single point of failure in the approved target design; in the current build (baseline not yet implemented) Accessibility remains the sole detector and follows the first chain.

### 8.22 Highest-Priority Current Threats — Replacement

Based on the current architecture and implementation state, the Threat Model identifies the following as requiring particular attention. (Ordering reflects the two-tier architecture — foreground-detection failure leads — and applies the THR-ENF-002/003 retitles; per §7.31, priority is not itself a final risk rating.)

1. THR-ENF-001 — Foreground Detection Failure
2. THR-ENF-003 — Silent Detection Failure
3. THR-ENF-002 — Deliberate Detection Disruption
4. THR-UI-001 — Tapjacking / Obscured Authentication Input
5. THR-CRYPTO-005 — Keystore Invalidation
6. THR-VAULT-005 — In-Process Vault Decryption Without PIN
7. THR-AUTH-004 — Brute-Force Lockout Bypass
8. THR-SES-002 — Session Extension Beyond Policy
9. THR-INT-002 — Runtime Instrumentation
10. THR-REC-001 — Database Corruption Causing Security Degradation

Priority does not itself constitute a final risk rating. Final risk ratings must follow the likelihood × impact methodology and include documented reasoning.

### 8.23 Threats That Are Not Current Mitigations — No change required

Landed unchanged (the planned/gap list is already complete and tier-neutral; the two-tier planned controls are enumerated in §9.18).

### 8.25 Threat Identification Change Control — No change required

Landed unchanged.

---

## Section 9 — Security Controls and Mitigations

### 9.6.1 Two-Tier Foreground Detection — Replacement (retitled from "Accessibility Detection")

The approved architecture separates foreground detection from the lock engine and provides two detection tiers.

**Baseline Tier — UsageStatsManager + Usage Access.** The baseline tier is mandatory and is designed to keep App Lock functional with Accessibility disabled. The baseline control SHALL:

1. obtain foreground-application information through UsageStatsManager and the Usage Access special permission;
2. sample usage events through the approved foreground service;
3. identify the current foreground package with the required detection-latency characteristics;
4. exclude App Lock's own package where appropriate, and exclude explicitly ignored system packages;
5. pass the resulting trigger to the Trigger Processor / detection-source selection layer;
6. cause the common lock engine to evaluate protected-app policy and current authorization state;
7. use the approved lock-interface presentation mechanism subject to Android background-activity-launch restrictions.

The baseline tier introduces a sampling latency and battery-cost trade-off. These characteristics SHALL be measured, documented, and security-tested rather than assumed to be negligible. The baseline may require the display-over-other-apps / system-alert-window permission or an applicable platform background-activity-launch exemption, depending on the final lock-interface presentation design.

**Enhancement Tier — Accessibility.** The existing Accessibility-based detector is retained as an optional, user-enabled enhancement. The enhancement SHALL:

1. use the Android Accessibility framework to receive event-driven foreground information;
2. apply the same package-exclusion rules (App Lock's own package and ignored system packages);
3. feed the same Trigger Processor / lock-engine path used by the baseline;
4. provide improved responsiveness where enabled;
5. remain optional for normal App Lock operation;
6. never be required as the sole mechanism for protected-app enforcement in the final architecture.

**Detection-Source Selection.** The detection-source selection layer determines which available source supplies triggers. The baseline is the required foundation; Accessibility may be enabled as an enhancement. The lock engine SHALL remain independent of the detection source: detection identifies a candidate foreground transition; authentication and authorization determine whether access is permitted.

**Current Implementation Status.** The delivered build has not yet implemented the two-tier architecture and continues to rely on Accessibility-only detection. Therefore the baseline control described here is **planned**, not an effective current mitigation. The existing Accessibility availability risk remains open until the Phase 1 implementation is completed and verified.

### 9.6.2 Enforcement Health Monitoring — Replacement

ProtectionWatchdogService provides monitoring of the enforcement architecture. The watchdog SHALL distinguish, where technically possible, between:

- mandatory baseline healthy;
- mandatory baseline missing or degraded;
- optional Accessibility enhancement healthy when enabled;
- optional Accessibility enhancement missing or degraded;
- common enforcement/presentation-path failure.

A loss of the optional Accessibility enhancement must not be reported as total App Lock protection loss when the mandatory baseline is healthy. A loss or silent degradation of the mandatory baseline is a security-critical condition: the watchdog SHALL generate an appropriate security event and user notification where technically possible and SHALL NOT falsely report healthy protection.

Monitoring is a **detection and response control**, not a complete preventive control. The application cannot independently grant Usage Access or Accessibility permission. The exposure created between loss of a required mechanism and its recovery SHALL remain a documented residual risk — for the baseline path as a security-critical exposure, and for the optional enhancement as an enhancement-degradation exposure.

### 9.6.3 Startup and Boot Recovery — Replacement

BootReceiver SHALL initiate the protection recovery path following device boot. The target recovery path SHALL restore the mandatory baseline enforcement infrastructure without restoring stale authentication sessions. If the optional Accessibility enhancement is enabled, its restoration state SHALL be evaluated separately from the baseline. Reboot may restore enforcement infrastructure, but SHALL NOT restore an authenticated App Lock session.

### 9.10 UI and Authentication-Surface Controls — Replacement

The authentication interface SHALL be treated as a security boundary rather than an ordinary application screen. Existing controls include:

- self-gating of protected application functionality;
- lifecycle-based re-gating;
- Back handling;
- Recents exclusion;
- noHistory behavior where applicable;
- completion of unauthenticated lock screens when backgrounded;
- FLAG_SECURE in release builds;
- biometric-dialog lifecycle handling;
- repeated foreground enforcement regardless of detection source.

These controls address historical navigation and screen-capture bypasses. The approved two-tier architecture also requires the lock-interface presentation path to be compatible with the mandatory Usage Access baseline; the final choice between a drawn overlay and an activity launched through the applicable background-activity-launch mechanism remains an implementation decision.

The current implementation does **not** fully address: malicious overlay obscuring; tapjacking; obscured-touch acceptance; malicious accessibility event injection; UI spoofing by a hostile peer accessibility service. These SHALL remain explicit threats and SHALL NOT be marked mitigated merely because FLAG_SECURE is enabled.

### 9.12 Permission and Security-State Monitoring — Replacement

The application SHALL monitor security-relevant permission state changes for every permission that forms part of the approved enforcement architecture. At minimum, this includes:

- Usage Access for the mandatory baseline;
- the permission required by the final lock-interface presentation mechanism, where applicable;
- Accessibility permission when the optional enhancement is enabled;
- Device Admin where uninstall protection is enabled;
- other security-critical grants identified by the implementation.

When a security-critical permission is removed or protection becomes unavailable, the application SHALL: (1) detect the condition where technically possible; (2) record a security event; (3) notify the user; (4) provide an appropriate recovery path; (5) avoid falsely representing the protection state as healthy. Permission monitoring SHALL distinguish optional enhancement loss from mandatory baseline loss. Detection SHALL NOT be treated as equivalent to prevention.

### 9.15 Controls for Historical Vulnerabilities — Replacement

Historical failures SHALL be treated as permanent security regression targets. The following controls are therefore mandatory regression/security-test candidates:

| Historical Failure | Required Control |
|---|---|
| Self-gate resume bypass | Lifecycle self-gating |
| Fast relaunch bypass | Re-evaluate authorization on every protected-app foreground trigger |
| Fast-switch relock defect | Per-trigger enforcement independent of detection source |
| Plaintext database | SQLCipher migration and encrypted persistence |
| Release cryptographic build failure | Minified release-build validation |
| Accessibility loss | Optional-enhancement health monitoring and baseline-continuity verification |
| Detection-source substitution/race | Common Trigger Processor and lock-engine regression coverage |

The existence of a historical fix SHALL NOT by itself establish security verification. The corresponding threat, control, and test evidence SHALL remain traceable.

### 9.16 Compensating Controls — Replacement

Where a primary security mechanism cannot provide complete prevention, a compensating control SHALL be documented. Examples include:

- accessibility health notification compensating for the inability to self-grant the optional Accessibility enhancement;
- baseline and enhancement health monitoring compensating for the inability to self-grant platform permissions, and watchdog monitoring compensating for detection-service fragility;
- process-lifetime sessions compensating for the inability to guarantee secure session persistence;
- destructive reset compensating for the absence of secure PIN recovery;
- encrypted storage compensating for the inability to guarantee confidentiality against a compromised application process.

Compensating controls SHALL NOT be described as equivalent to the primary control.

### 9.18 Current Control Posture — Replacement

At the time of Threat Model authoring, the security posture SHALL be represented conservatively.

**Implemented and Functionally Regression-Verified** (existing functional/regression evidence): PIN authentication; biometric authentication; intruder capture; vault core functionality; vault UI self-gating; encrypted persistence; database encryption; encrypted vault payloads; screen-capture prevention; persistent brute-force lockout; permission-change detection; per-application authorization/session behavior; historical self-gate and rapid-relaunch defenses; existing Accessibility-based detection behavior in the current delivered build. These controls SHALL NOT yet be labeled **security-verified** solely on the basis of the existing regression campaigns.

**Implemented but Pending Security Classification or Verification** (as applicable): Keystore usage; detailed authentication-session controls; device-credential handling; key-management architecture; secure-memory handling; clipboard protection; emergency-lock behavior; audit-log protection; privacy controls; backup/security-recovery behavior; existing Accessibility health monitoring as a security control.

**Planned or Not Yet Effective** (approved target-architecture and other controls not yet effective in the delivered build): UsageStatsManager + Usage Access baseline detection; detection-source selection / Trigger Processor integration for the two-tier architecture; baseline-compatible lock-interface presentation path; tier-specific enforcement health monitoring; root detection; root response; tamper detection; production debug/instrumentation resistance; anti-tapjacking and obscured-touch defense; Keystore-invalidation recovery; formal secure backup; key rotation; security-health scoring; penetration testing; Threat-Model-driven security-test suite.

The optional Accessibility enhancement is architecturally approved, but its optional status does not make its current implementation security-verified. Planned controls SHALL NOT be cited elsewhere in the Threat Model as existing mitigations.

### 9.19 Security Control Invariants — No change required

Landed unchanged (the sixteen §9.19 control invariants are tier-neutral; note these are distinct from the §6.32 INV-* identifiers).

### 9.21 Section 9 Completion Criteria — Replacement

Section 9 is complete only when:

- every material threat identified in Section 8 has at least one documented control, accepted limitation, or explicit statement of why no control is feasible;
- each control has a defined implementation state;
- implemented controls are not incorrectly represented as security-verified;
- preventive, detective, and responsive controls are distinguished;
- compensating controls are explicitly identified;
- historical vulnerabilities remain mapped to their controls;
- authentication, authorization, vault, cryptographic, IPC, enforcement, recovery, and platform controls are represented;
- root/system compromise remains outside the guaranteed application trust boundary;
- known anti-tapjacking, overlay, detection-source, Accessibility, Usage Access, and Keystore-invalidation gaps remain visible;
- the current Accessibility-only implementation is not confused with the approved two-tier target architecture;
- control invariants are established;
- control changes are subject to impact assessment and verification-state review;
- Section 12 will provide the authoritative evidence criteria for promoting a control to **security-verified** status.

---

*End of proposed Sections 6–9 reconciliation (corrected). Sections 10–16 remain outstanding: §10.19, §12.25–12.27, §12.44 (stale forward-ref), §13.8 (HF-006/007), §14.21, §14.35 (stale forward-ref), and the §16.34.4 vs §16.32.15 contradiction still require the equivalent reconciliation and are proposed separately. On approval, this proposal lands as Threat Model v2 §6–9 via the md→docx pipeline (same superset method as §1–5), and `tm/VERSION.md` is updated.*
