# Threat Model — Architectural Reconciliation, Sections 1–5 (Proposed Revisions)

**Purpose.** These proposed replacement texts reconcile Threat Model Sections 1–5 with the approved two‑tier foreground‑detection architecture established in §16.32 (the Architecture Decision Record that supersedes the earlier Accessibility‑only decision). Sections 1–5 currently describe Accessibility as the single, mandatory detection/enforcement mechanism; the texts below align them with the approved model while preserving the mandatory distinction between the **current delivered implementation** (still Accessibility‑only) and the **approved target architecture** (Usage Access baseline + optional Accessibility). No target‑architecture capability is represented as implemented or security‑verified.

**How to apply.** Each block is a drop‑in replacement for the identified subsection. Subsection numbers and titles below match their locations in the approved baseline. Where a block introduces new attack surfaces, stable identifiers are assigned so that later‑section threats can trace to them per §5.30.

---

## Section 1 — Purpose, Scope, and Objectives

### 1.2 System Scope — Replacement

The security‑relevant system scope includes both the current implementation and approved security‑architecture changes that materially affect the Threat Model.

Foreground‑application detection is within scope because it is the trigger for protected‑application enforcement.

The current delivered implementation uses the Android Accessibility framework for foreground detection. The approved target architecture replaces Accessibility as the mandatory detection dependency with a two‑tier detection model:

- **Baseline detection:** Android UsageStatsManager using the Usage Access special permission.
- **Optional enhancement:** Android Accessibility‑based event detection, enabled by the user for faster, more responsive detection.

The lock‑enforcement engine remains common to both detection paths.

The Threat Model SHALL distinguish the current implementation from the approved target architecture. The approved target architecture SHALL NOT be represented as implemented or security‑verified before the corresponding implementation and verification activities are complete.

### 1.3 Security Scope — Replacement

Foreground‑application detection remains a security‑critical function because detection is the trigger that allows App Lock to determine when authentication must be presented.

The Threat Model therefore covers:

- Usage Access and UsageStatsManager detection;
- optional Accessibility‑based detection;
- detection‑source selection;
- the Trigger Processor;
- lock‑engine invocation;
- lock‑screen presentation;
- permission and detection‑health monitoring;
- lifecycle and recovery behavior affecting the active detection path.

Accessibility remains within security scope even though it is optional in the approved architecture, because the optional enhancement can influence detection latency and because peer Accessibility Services remain a relevant attacker and platform boundary.

### 1.4 Security Objectives — SO‑08 Replacement

**SO‑08 — Enforcement Availability.** The security enforcement mechanism must remain operational to the extent supported by the Android platform and the application's defined security guarantees.

The approved architecture SHALL maintain a baseline enforcement path that does not require Accessibility to be enabled. Accessibility loss alone must therefore not constitute loss of App Lock protection once the two‑tier architecture is implemented.

Loss or failure of the baseline detection path — including loss of the required Usage Access permission or of the required lock‑interface presentation capability — remains security‑relevant when it permits protected applications to become accessible without App Lock authorization.

The optional Accessibility enhancement may improve detection responsiveness but is not itself a prerequisite for the core enforcement guarantee.

---

## Section 2 — Protected Assets and Security Properties

### 2.8 Asset A‑006 — Security Enforcement Mechanism — Replacement

**Description.** The continuous operation of the App Lock enforcement mechanism is a security asset. The enforcement path includes the mechanisms responsible for:

- detecting protected applications;
- determining whether authentication is required;
- presenting the authentication interface;
- maintaining authorization state;
- enforcing relock policy;
- monitoring protection health;
- restoring protection across supported lifecycle events.

The approved detection architecture contains two tiers:

1. **Baseline tier:** UsageStatsManager with Usage Access, providing the required detection path without Accessibility.
2. **Enhancement tier:** Accessibility‑based event detection, enabled voluntarily by the user for faster detection.

Both tiers converge on the same Trigger Processor and lock‑enforcement path.

Security‑relevant components include:

- AppDetectionService (the current Accessibility implementation and approved optional enhancement);
- the baseline UsageStats‑based detection implementation when introduced;
- the detection‑source selection layer;
- ApplicationLockEngine;
- LockPolicyManager;
- LockSessionManager;
- LockScreenActivity;
- ProtectionWatchdogService;
- BootReceiver;
- device‑admin uninstall protection where enabled;
- the lock‑interface presentation mechanism required by the baseline detector.

**Security Requirements.**

- **Availability:** the required baseline protection path must remain operational under supported conditions.
- **Integrity:** an attacker must not weaken or disable security enforcement without authorization.
- **Authorization integrity:** security policy must not be modified to remove protection without authorization.
- **Persistence:** security‑critical protection state must not be bypassable through application restart or device reboot where persistence is required.
- **Fail‑safe health reporting:** loss or failure of a required detection mechanism must not silently be represented as healthy protection.
- **Tier independence:** loss of the optional Accessibility enhancement must not, by itself, disable core protected‑application enforcement.

**Security Significance.** The enforcement mechanism is a security asset because protected applications are only protected while App Lock can detect relevant foreground transitions and enforce its authorization boundary.

The approved architecture deliberately separates the enforcement boundary from the Accessibility framework. Under the target architecture, Accessibility is an optional detection enhancement; the mandatory enforcement dependency is the baseline detection path and its associated permission and lock‑interface presentation mechanisms.

The current delivered implementation remains Accessibility‑dependent until the approved two‑tier architecture is implemented and verified. The distinction between current and target architecture is security‑critical and SHALL be preserved throughout the Threat Model.

---

## Section 3 — Threat Model Assumptions, Trust Boundaries, and Non‑Goals

### 3.11 Accessibility Trust Boundary — Replacement

App Lock includes Android's Accessibility framework as an optional foreground‑detection mechanism in the approved architecture. Accessibility is therefore:

- a trusted Android platform interface when enabled;
- an optional detection enhancement;
- a known‑fragility boundary;
- a security‑relevant interface, because peer Accessibility Services may observe or inject UI interactions.

The approved architecture does **not** assume that Accessibility must be enabled for App Lock to provide its core protected‑application enforcement function. The baseline enforcement path uses Android UsageStatsManager together with the Usage Access special permission. When the Accessibility enhancement is enabled, the application may use its event‑driven detection path to obtain faster foreground‑transition detection.

The Threat Model recognizes that the Accessibility enhancement may:

- become unbound;
- have its permission revoked;
- be blocked or complicated by Android restrictions;
- be affected by OEM lifecycle behavior;
- appear enabled while failing to deliver expected events;
- coexist with another Accessibility Service capable of observing or injecting UI interactions.

These conditions remain security‑relevant, but their consequences differ from the previous Accessibility‑only architecture. Loss of Accessibility SHALL be treated as loss of the optional enhancement once the two‑tier architecture is implemented, not as automatic loss of App Lock's core enforcement capability.

The baseline Usage Access path has its own permission, health, latency, and lifecycle dependencies and therefore constitutes the mandatory detection trust boundary for the target architecture.

### 3.12 Boot and Lifecycle Trust Boundary — Replacement

App Lock relies on Android lifecycle and boot mechanisms to maintain and restore the active enforcement architecture after system restart and application lifecycle events. The application assumes that supported Android lifecycle mechanisms will provide the necessary opportunities for:

- boot‑time re‑arm;
- watchdog startup or restart;
- baseline Usage Access detection operation where implemented;
- optional Accessibility‑service restoration where enabled;
- application process recovery where supported;
- restoration of the lock‑enforcement path.

The Threat Model SHALL distinguish between the required baseline detection mechanism and the optional Accessibility enhancement. A failure to restore Accessibility does not necessarily represent loss of App Lock enforcement when the baseline detection path remains healthy. A failure to restore the baseline detection path is security‑relevant when it permits protected applications to become accessible without App Lock authorization. OEM and operating‑system behavior may prevent guaranteed recovery in all circumstances.

### 3.21 Explicit Non‑Goals — NG‑006 / NG‑007 Replacement

**NG‑006 — Absolute Accessibility availability.** App Lock does not guarantee continuous availability of the optional Accessibility enhancement against all Android, OEM, administrative, or system‑level interventions. The approved architecture does not require Accessibility to remain enabled for core App Lock enforcement.

**NG‑007 — Absolute protection against peer Accessibility Services.** A malicious Accessibility Service operating within the Android platform's permitted security model may observe or inject UI interactions. App Lock may provide detection or mitigation but does not claim absolute prevention. The presence of a peer Accessibility Service remains relevant to authentication‑UI integrity and interaction security regardless of whether App Lock is using Accessibility as its active detection source.

### 3.22 Accepted Foundational Assumptions — Replacement of TA‑006 and TA‑007

The following assumptions replace the Accessibility‑only enforcement assumption:

| ID | Assumption | Security Dependency |
|----|----|----|
| TA‑006 | The approved baseline foreground‑detection mechanism operates sufficiently for supported protected‑application enforcement. Accessibility is optional and may provide an enhancement when enabled. | Enforcement |
| TA‑007 | Supported Android boot/lifecycle mechanisms provide expected recovery opportunities for the active detection and enforcement mechanisms. | Persistence |

This assumption SHALL NOT be interpreted as claiming that the baseline mechanism is already implemented. Until the Core Security Platform implementation is complete and security‑verified, the current build remains subject to its existing Accessibility‑only enforcement limitation.

### 3.23 Trust‑Boundary Change Rule — Replacement Entry

The following constitutes a trust‑boundary change:

- changing the foreground‑detection architecture, including replacing, adding, or removing a detection source;
- changing the required status of Accessibility;
- introducing Usage Access as a required security permission;
- changing the lock‑interface presentation mechanism;
- changing the relationship between detection sources and the Trigger Processor;
- changing the application's privileged Android capabilities.

The previously Accessibility‑only detection boundary is therefore superseded by the approved two‑tier detection architecture.

---

## Section 4 — Threat Actors and Attacker Capabilities

### 4.5 TA‑ATK‑003 — ADB/USB Attacker — Replacement (Availability Paragraph)

Force‑stop is treated separately as an **availability attack** because terminating the application can interrupt the active enforcement architecture.

Under the current delivered implementation, force‑stop can interrupt the Accessibility‑based enforcement path. Under the approved two‑tier architecture, force‑stop or equivalent process termination can interrupt the baseline detection service, the optional Accessibility enhancement, the watchdog, or other required enforcement components.

The Threat Model SHALL therefore treat interruption of the **baseline enforcement path** as the security‑critical condition. Loss of the optional Accessibility enhancement alone is not equivalent to loss of App Lock protection once the two‑tier architecture is implemented.

The current architecture cannot guarantee continued enforcement after force‑stop, and this limitation remains a residual security concern until the applicable lifecycle and recovery controls are implemented and verified.

### 4.7 TA‑ATK‑005 — Malicious or Compromised Accessibility Service — Replacement

**Classification: Best effort.**

A peer Accessibility Service represents a special Android attacker because Accessibility can legitimately observe UI information and inject user‑interface events. This threat remains relevant even though Accessibility is optional for App Lock, because a peer Accessibility Service may interact with the authentication interface or observe application UI independently of whether App Lock uses Accessibility as its active foreground detector.

**Capabilities.** Where Android permits the service to operate, the attacker may attempt to:

- observe application UI events;
- observe text or accessibility nodes exposed by applications;
- monitor application transitions;
- inject interaction events;
- manipulate authentication UI;
- interfere with user interaction;
- race App Lock's authentication flow;
- exploit assumptions made by the App Lock lock engine.

**Security Limitation.** App Lock cannot claim absolute prevention against a malicious peer Accessibility Service while operating within the Android accessibility model. Detection and warning mechanisms may reduce the risk, but the presence of another privileged Accessibility Service remains a platform‑level limitation.

**Security Significance.** The threat is no longer characterized by the assumption that App Lock itself must rely on Accessibility for enforcement. Instead, the security significance is that Android permits another Accessibility Service to possess capabilities that can affect UI observation and interaction. The optional Accessibility enhancement introduces an additional detection path, but compromise or failure of that enhancement does not by itself defeat the baseline App Lock enforcement architecture.

### 4.17 Attacker Goals — Availability Goals Replacement

**Availability goals.** Maintain the security enforcement mechanisms necessary to prevent protected applications from becoming accessible without App Lock authorization. Under the approved architecture this includes:

- baseline Usage Access detection;
- the selected lock‑interface presentation mechanism;
- optional Accessibility detection when enabled;
- detection‑source selection;
- the Trigger Processor;
- lock‑engine invocation;
- watchdog operation;
- boot re‑arm;
- device‑admin uninstall protection where enabled.

The baseline detection path is the mandatory enforcement dependency. Accessibility is an optional enhancement and SHALL NOT be treated as the sole availability dependency of the target architecture.

### 4.18 Security‑Relevant Attacker Actions — Replacement

The following actions are considered security‑relevant regardless of whether they succeed:

- attempting to bypass App Lock authentication;
- attempting to replay a previously valid session;
- attempting to exploit rapid application switching;
- attempting to exploit lifecycle transitions;
- attempting to suppress or disable foreground detection;
- attempting to defeat lockout;
- attempting to reset or modify the credential without authorization;
- attempting to access Vault data outside the authorized UI path;
- attempting to extract database or file encryption keys;
- attempting to read sensitive data through screenshots or recordings;
- attempting to obscure or spoof authentication UI;
- attempting to inject UI events;
- attempting to disable the baseline detection mechanism;
- attempting to disable the optional Accessibility enhancement;
- attempting to interfere with Usage Access;
- attempting to interfere with the lock‑interface presentation mechanism;
- attempting to disable App Lock services;
- attempting to uninstall App Lock while protection is enabled.

Disabling Accessibility alone SHALL NOT be classified as a complete enforcement bypass under the approved architecture.

---

## Section 5 — Attack Surface

### 5.2 Attack Surface Inventory — Replacement

The inventory describes the **current implementation** and, for traceability, the approved **target‑architecture** surfaces marked *planned*. The current delivered build uses AppDetectionService and the Android Accessibility framework for foreground detection. The approved target architecture additionally introduces Usage Access and UsageStatsManager as the baseline detection path and retains Accessibility as an optional enhancement.

| ID | Surface | Type | Security Significance |
|----|----|----|----|
| AS‑001 | MainActivity | Activity | Primary application UI and application self‑gate entry point |
| AS‑002 | LockScreenActivity | Activity | Authentication boundary for protected applications |
| AS‑003 | AppDetectionService | Accessibility Service | Current foreground‑app detection and approved optional enhancement |
| AS‑004 | ProtectionWatchdogService | Foreground Service | Protection‑health monitoring and recovery signaling |
| AS‑005 | BootReceiver | BroadcastReceiver | Boot‑time protection re‑arm |
| AS‑006 | UninstallProtectionReceiver | Device Admin Receiver | Uninstall‑protection framework boundary |
| AS‑007 | Android Accessibility framework | Framework interface | Current detection interface and approved optional enhancement |
| AS‑008 | Device Admin framework | Framework interface | Administrative/uninstall protection |
| AS‑009 | Android Keystore | Cryptographic interface | Root of trust for protected key material |
| AS‑010 | App‑private storage | Storage boundary | Database, encrypted files, preferences, and application state |
| AS‑011 | SQLCipher/Room database | Data store | Protected‑app configuration, Vault index, and security/intruder metadata |
| AS‑012 | Encrypted file store | Data store | Vault payloads and intruder photographs |
| AS‑013 | App Lock authentication UI | UI boundary | PIN and biometric authentication |
| AS‑014 | Lock‑session state | Runtime state | Per‑application authorization state |
| AS‑015 | Exported Android components | IPC surface | External framework/application interaction |
| AS‑016 | Android lifecycle and process management | Lifecycle surface | Process death, force‑stop, reboot, and recovery behavior |
| AS‑017 | Application notifications | Observable surface | Security‑relevant externally visible information |
| AS‑018 | Application installation/update boundary | Package surface | Application artifact and dependency integrity |
| AS‑019 | UsageStatsManager / Usage Access | Framework interface + special permission | Baseline foreground detection *(planned — target architecture)* |
| AS‑020 | Baseline lock‑interface presentation mechanism | UI / window surface | Baseline lock‑screen presentation via overlay or activity‑launch exemption *(planned — target architecture)* |
| AS‑021 | Detection‑source selection layer | Runtime component | Selects the active detection tier *(planned — target architecture)* |

AS‑019 through AS‑021 describe the approved target architecture and are marked *planned*. They are inventoried here so that target‑architecture threats can trace to a stable attack‑surface identifier per §5.30. They receive full security classification when implemented and security‑verified.

### 5.6 AS‑003 — AppDetectionService — Replacement

AppDetectionService is the current Accessibility Service responsible for foreground‑application detection. In the approved architecture, this component becomes the **optional Accessibility enhancement tier** rather than the mandatory detection mechanism.

**Exposure.** The service is `exported="false"` and protected through `BIND_ACCESSIBILITY_SERVICE`. The Android Accessibility framework is responsible for binding the service.

**Security‑Relevant Functions.** In the current implementation, the service (1) receives Accessibility events, (2) identifies the foreground application, (3) passes foreground information to the application lock engine, (4) causes authorization policy to be evaluated, and (5) initiates the lock‑screen path when authentication is required. In the approved architecture, these functions remain valid for the optional Accessibility tier; the baseline enforcement path is provided separately through Usage Access and UsageStatsManager (AS‑019).

**Security Boundary.** The current implementation forms the boundary:

    Android Accessibility Framework → AppDetectionService → ApplicationLockEngine → LockScreenActivity

The approved architecture changes the overall model to:

    Usage Access / UsageStatsManager ─┐
                                       ├→ Detection Selection → Trigger Processor → ApplicationLockEngine → LockScreenActivity
    Accessibility / AppDetectionService┘

**Security Significance.** The service remains security‑relevant because its availability affects the optional enhancement tier and because Accessibility capabilities can affect authentication‑UI integrity. However, under the approved architecture, loss of AppDetectionService SHALL NOT by itself constitute loss of core App Lock enforcement.

### 5.7 AS‑004 — ProtectionWatchdogService — Replacement

ProtectionWatchdogService is a foreground service responsible for monitoring protection availability. The watchdog SHALL monitor the health of the active baseline enforcement path and, where applicable, the optional Accessibility enhancement. Its security‑relevant monitoring responsibilities therefore include:

- baseline Usage Access availability (AS‑019);
- baseline detector health;
- lock‑interface presentation capability (AS‑020);
- Accessibility availability when the enhancement is enabled (AS‑003/AS‑007);
- protection‑loss conditions;
- lifecycle/restart conditions.

The watchdog must distinguish between loss of a required baseline control, loss of an optional enhancement, and inability to determine detector health. A missing Accessibility enhancement SHALL NOT be reported as total loss of App Lock protection when the baseline path remains healthy.

### 5.10 AS‑007 — Accessibility Framework Interface — Replacement

The Android Accessibility framework is an external security‑relevant interface. In the current implementation, App Lock uses it for foreground‑app detection. In the approved architecture, it becomes an optional detection interface that may provide faster event‑driven foreground detection when explicitly enabled by the user.

**Security‑Relevant Inputs.** Accessibility events; service binding state; service enablement state; permission state; platform restrictions affecting Accessibility; interactions with peer Accessibility Services.

**Security‑Relevant Outputs.** When the Accessibility enhancement is active, App Lock uses these inputs to identify foreground transitions and submit detection information to the common enforcement path.

**Attack Surface Characteristics.** The framework introduces an optional detection dependency; UI‑observation and event‑injection capabilities that may be available to peer Accessibility Services; platform and OEM availability limitations; and potential silent failure of the event stream. The Accessibility interface SHALL therefore remain a security‑relevant attack surface, but it SHALL NOT be represented as the mandatory App Lock enforcement dependency in the approved architecture.

### 5.25 Attack Surface by Security Property — Replacement Entries

The current‑implementation classification remains applicable to the current Accessibility path (AS‑003/AS‑007). For the approved target architecture, the following surfaces require equivalent security classification when implemented:

| Surface | Confidentiality | Integrity | Availability |
|----|---:|---:|---:|
| AS‑019 UsageStatsManager / Usage Access | Medium | High | Critical |
| AS‑020 Baseline lock‑interface presentation mechanism | High | Critical | Critical |
| AS‑003/AS‑007 Accessibility enhancement | Medium | High | Medium/High |

The final classification of the presentation mechanism (AS‑020) SHALL be confirmed when the implementation decision between overlay presentation and the applicable activity‑launch mechanism is made.

### 5.26 Attack Surface Change Rules — Replacement Entries

In addition to the existing change triggers, the following changes constitute attack‑surface changes and require Threat Model impact assessment:

- adding, removing, or replacing a foreground‑detection source;
- changing the required/optional status of Accessibility;
- introducing or changing the Usage Access requirement;
- changing the lock‑interface presentation mechanism (overlay or activity‑launch);
- changing detection‑source selection behavior.

The existing trigger "changing Accessibility behavior" remains applicable to the optional enhancement tier.

### 5.27 Planned Versus Effective Attack‑Surface Controls — Replacement

The attack‑surface inventory must distinguish the current implementation from the approved target architecture. The following are approved architectural controls but SHALL NOT be represented as effective controls until implemented and security‑verified:

- UsageStatsManager‑based baseline foreground detection (AS‑019);
- Usage Access permission monitoring;
- baseline detection‑health verification;
- baseline lock‑interface presentation mechanism (AS‑020);
- detection‑source selection (AS‑021);
- two‑tier Trigger Processor integration.

Accessibility remains implemented in the current build, but its role changes under the approved architecture from mandatory detection dependency to optional enhancement. The following states remain distinct:

| Control | Current State | Target State |
|----|----|----|
| Accessibility detection (AS‑003/AS‑007) | Implemented / current detection mechanism | Optional enhancement |
| Usage Access detection (AS‑019) | Not implemented | Mandatory baseline |
| Detection‑source selection (AS‑021) | Not implemented | Required |
| Baseline health monitoring | Not implemented | Required |
| Accessibility health monitoring | Current monitoring exists | Enhancement health monitoring |
| Common Trigger Processor path | Existing lock architecture | Shared by both detection tiers |

### 5.28 Historical Attack‑Surface Findings — Replacement (Finding 5)

The following historical failures demonstrate attack‑surface exposure in lifecycle and navigation behavior:

1. **Self‑gate bypass** — returning to App Lock after prior authentication could expose protected functionality without the required re‑gating.
2. **Fast‑relaunch bypass** — rapid relaunching of a protected application interacted incorrectly with the lock‑screen lifecycle.
3. **Fast‑switch relock defect** — rapid switching away from and back to a protected application did not always produce the required re‑lock behavior.
4. **Plaintext database exposure** — earlier database storage did not provide the current encrypted‑at‑rest boundary before migration.
5. **Force‑stop availability limitation** — terminating App Lock can interrupt the current Accessibility‑based enforcement path.

Finding 5 must be interpreted according to implementation state. For the current delivered build, force‑stop can interrupt the Accessibility‑based enforcement path. For the approved target architecture, the corresponding threat becomes interruption or failure of the **baseline detection and enforcement path**; loss of the optional Accessibility enhancement alone does not constitute the equivalent failure.

These findings are retained as attack‑surface evidence. They do not constitute proof that the corresponding defects remain present in the current build. Current implementation status and security verification are determined separately.

---

*End of proposed Sections 1–5 reconciliation revisions. Sections 6–16 (enforcement architecture §6.14–§6.35; threats §8.6/§8.12; controls §9.6; phase model §10.19; risk §12.25–12.27; historical §13.8; assessment §14.21; and the §16.34.4 statement) require the equivalent reconciliation and are proposed separately.*
