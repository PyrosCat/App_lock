# Independent Formal Code Review - App Lock Android Application

**Date filed:** 2026-08-11  
**Reviewer:** Anonymous independent reviewer  
**Host:** Undisclosed  
**Document class:** Evidence - immutable after commit (`GOVERNANCE.md` Section 5.1)  
**Review type:** Independent static code review  
**Produced against:** commit `363320d1265a5cf876844aeec541abcd583852f0` (`363320d`)  
**Application-code baseline reviewed:** commit `3a0e6fd`; commits after that baseline and through
`363320d` change project documentation and evidence only, not application or build logic.  
**Milestone context:** IS Phase 0 (M1), WP1-WP5 complete, WP6 next.  
**Governance premise:** this review does not authorize product-logic remediation during M1.
Review-driven logic changes begin no earlier than IS Phase 1 (M2). Existing M1 work remains
controlled by `M1_PLAN.md`; changing that plan requires a separate living-document decision.

**Review disposition:** **CHANGES REQUESTED FOR FUTURE AFFECTED GATES; NO NEW M1 LOGIC CHANGE
DIRECTED.** The reviewed baseline contains six material defects or control gaps. One is the
already-authoritative High risk R-002 assigned to the IS Phase 1 (M2) security gate. One maps to
the already-authoritative High risk R-004 and its existing M1/WP7 treatment. The remaining review
findings require triage into the living risk and defect records before their affected gates. This
evidence report does not itself pass, fail, open, close, defer, or accept a project risk.

---

## 1. Purpose and Review Question

This review answers two separate questions:

1. What material defects or control gaps are visible in the delivered Android source baseline?
2. What do those observations mean for the project's actual work-package, milestone, security,
   and release gates?

The questions are deliberately separated. A static finding is evidence for a gate decision; it is
not a gate decision. Gate status comes from the applicable plan and gate record, milestone status
comes from `ROADMAP.md`, requirement status comes from `rtm.csv`, and project-risk status comes
only from `RISK_REGISTER.md`.

## 2. Authority and Interpretation Rules

### 2.1 Governing sources

The review applies the following project-authored authorities:

- [`GOVERNANCE.md`](../../process/GOVERNANCE.md), especially RTM rules, document classes, risk
  authority, milestone citation, and classification vocabularies;
- [`ROADMAP.md`](../../process/ROADMAP.md), the canonical M0-M6 to IS Phase 0-6 mapping and current
  milestone status;
- [`M1_PLAN.md`](../../process/M1_PLAN.md), the applicable WP5-WP8 scope and exit checks;
- [`RISK_REGISTER.md`](../../process/RISK_REGISTER.md), the sole authoritative risk record;
- the [RTM guide](../../process/rtm/RTM.md) and authoritative `rtm.csv` status ledger;
- the Test Specification sections governing [entry and exit
  criteria](../../testing/tsp/md/TestSpecification_V-I_section_11_Entry&ExitCriteria.md), [defect
  management](../../testing/tsp/md/TestSpecification_V-I_section_12_DefectManagement.md), and
  [test metrics and
  reporting](../../testing/tsp/md/TestSpecification_V-I_section_15_TestMetrics&Reporting.md);
- accepted ADRs, the current CI workflow, and dated campaign evidence.

### 2.2 Classification vocabulary

This report does not use migration-assessment P-codes as defect severities or gate criteria.
Consistent with `GOVERNANCE.md` Section 5.4:

- **Defect severity** describes technical impact and uses **Critical / Major / Minor**.
- **Defect priority** describes urgency and is stated as an affected milestone or gate, not as a
  snapshot P-code.
- **Risk severity** uses **Low / Medium / High / Critical** and is authoritative only when recorded
  in `RISK_REGISTER.md` under a stable `R-NNN` identifier.

Where this report says a finding is a Critical defect, it does not silently create a Critical
project risk. The project lead must triage whether the finding creates or changes a tracked risk,
and the living register then controls the gate consequence.

Test Specification Section 11.17 prevents a successful test exit when an unresolved Critical
defect affects the tested scope, absent documented risk acceptance. It does not turn every
Critical defect into a retroactive failure of every earlier or unrelated gate. This report therefore
names an affected gate for each finding. The applicable gate record must still review the finding
against its actual scope, exit criteria, defect status, and authoritative risk treatment.

### 2.3 Gate-status vocabulary

| Status | Meaning |
|---|---|
| PASS | Every applicable mandatory criterion has current, retained evidence. |
| PASS WITH APPROVED TREATMENT | An unmet condition is carried under an explicit decision naming the risk, treatment, owner, authority, affected gate, and reassessment trigger. The defect or risk remains open. |
| FAIL | An applicable criterion was executed and did not meet its required result. |
| BLOCKED | A prerequisite prevents the gate from being completed. |
| NOT EVALUATED | The gate has not occurred, is outside the review, or lacks sufficient evidence. |

A green regression run proves only the covered regression dimension. It does not prove complete
requirement, security, compatibility, or release readiness.

### 2.4 No review-driven M1 logic change

Until IS Phase 1 (M2), follow-up from this review is limited to:

- recording or reconciling defects and risks;
- requirement and threat traceability;
- assigning findings to the correct milestone and gate;
- designing tests, evidence requirements, and acceptance criteria;
- preserving the current M1 regression baseline;
- correcting documentation that overstates gate status.

No product-logic remediation is directed by this report during M1. This restriction does not
silently amend existing approved M1 work. In particular, `M1_PLAN.md` and R-004 currently assign
database fail-safe work to WP7. If the project intends to defer that already-approved work to M2,
the living plan, roadmap, risk entry, RTM implications, and M1 exit criteria must be changed by the
authorized decision process; this evidence report cannot make that change.

## 3. Scope and Method

### 3.1 Included scope

The static review covered:

- 38 production Kotlin files under `app/src/main`;
- 8 JVM test files under `app/src/test`;
- the Android manifest and security-sensitive component declarations;
- Gradle configuration, CI tasks, lint and detekt baselines, and Konsist architecture rules;
- lock detection and presentation;
- authentication, session, and lockout handling;
- encrypted preferences, SQLCipher Room storage, and migration behavior;
- Vault and intruder-evidence lifecycle behavior;
- Hilt dependency wiring and the removal of the `Graph` service locator;
- relevant requirements, ADRs, threat-model controls, risk entries, and campaign reports.

### 3.2 Excluded scope

This was not a dynamic test campaign. The reviewer did not execute Gradle, CI, emulator, device,
performance, accessibility, dependency-vulnerability, or release-signing commands. Existing
campaigns were assessed as retained external evidence and were not independently reproduced.

The review therefore does not independently mark any executable criterion PASS. It can confirm
that a cited report is relevant, identify evidence limitations, and determine whether a finding
must be presented to an affected gate.

### 3.3 Baseline integrity

The reviewed application source is the same at `3a0e6fd` and `363320d`. The intervening committed
changes are documentation and evidence changes. This report does not assess later uncommitted
source, and its findings must be revalidated if their cited implementation changes.

## 4. Executive Assessment

The project has a strong security-oriented foundation:

- PINs use Argon2id with per-credential salts and constant-time comparison.
- Credential and lockout state use Keystore-backed encrypted preferences.
- Room uses SQLCipher and sensitive blob payloads use authenticated encryption.
- Authorization sessions remain in memory and screen-off clears them.
- Release authentication and management screens use secure-window protection.
- Hilt preserved dependency lifetimes while eliminating the `Graph` service locator.
- CI, lint, detekt, Konsist, build variants, and retained device evidence provide useful M1
  regression controls.

The baseline also contains six material defects or control gaps. They do not all affect the same
gate, and they do not all require work at the current milestone.

| ID | Defect severity | Area | Finding | Governance route | Earliest affected gate |
|---|---|---|---|---|---|
| CR-001 | Critical | Lock enforcement | Rapid relaunch can out-race lock presentation | Existing R-002, High risk; remediation assigned to M2 | IS Phase 1 (M2) security gate |
| CR-002 | Critical | Policy readiness | Unready policy state is interpreted as an allow decision | Defect and risk triage required before M2 | IS Phase 1 (M2) security gate |
| CR-003 | Critical | Legacy migration | Plaintext rollback source is removed before encrypted import commits | Defect/risk and phase assignment required | M1 gate disposition if within WP7; otherwise assigned later gate |
| CR-004 | Major | Schema migration | Missing migration path can destructively recreate data | Existing R-004, High risk; current plan assigns WP7 | IS Phase 0 (M1), WP7/WP8 |
| CR-005 | Major | Authentication | Biometric non-matches bypass product failure accounting | Defect and risk triage required before M2 | IS Phase 1 (M2) security gate |
| CR-006 | Major | Data lifecycle | Blob deletion failure can be reported as successful | Defect and risk triage required before Vault/data-lifecycle gate | IS Phase 2 (M3) or IS Phase 4 (M5), as assigned |

## 5. Gate Alignment

### 5.1 WP5 Hilt behavior-preservation exit

**Assessment: PASS for the defined regression-preservation purpose.**

The [WP5 migration report](../campaigns/2026-08-08_wp5-hilt-migration_2012-i7.md) records 71 unit
tests with two deliberately dormant architecture rules, clean detekt and lint results, clean dev
and minified prod builds, and terminal enforcement of the no-`Graph` rule. The [Moto G
campaign](../campaigns/2026-08-08_wp5-harness_moto-g-2025.md) passed the four gating checks twice.
The [emulator matrix](../campaigns/2026-08-09_wp5-matrix_nucbox-g5.md) showed the pre-existing
R-002 rate was not improved but was not worsened by Hilt.

This is sufficient to support the project's recorded conclusion that WP5 preserved the controlled
baseline. It is not evidence that R-002 is resolved or that lock enforcement is deterministic.
`RISK_REGISTER.md` explicitly assigns R-002 to M2 and states that it is not an M1 gate item.

### 5.2 WP6 entry and execution

**Assessment: eligible under the current roadmap and risk register.**

WP6 is a mechanical package realignment. The applicable rule is to preserve behavior, maintain
the pinned component identities, activate the planned architecture rules, and verify the upgrade
path. This review introduces no instruction to combine finding remediation with WP6 moves. Logic
changes and package moves must remain separate so the WP6 regression result is interpretable.

### 5.3 WP7 database fail-safe work

**Assessment: NOT EVALUATED; existing M1 gate obligation remains authoritative.**

R-004 is a High risk whose affected gate is M1. `M1_PLAN.md` assigns its remediation and deliberate
failure drill to WP7, with closure checked at WP8. CR-004 corroborates that existing risk. CR-003 is
a separate migration interruption defect not yet represented by a stable risk identifier.

This report does not order either code change during M1. Before WP7 begins, the project must resolve
the apparent scope question:

1. retain the approved WP7 work and define whether CR-003 is included in its acceptance scope; or
2. formally defer some or all migration-logic work to M2 and update `M1_PLAN.md`, `ROADMAP.md`,
   R-004, affected RTM rows, and the M1 exit criteria before claiming the revised gate.

Silently omitting planned WP7 logic while retaining the existing M1 PASS criteria would violate
the living-document and gate-reporting rules.

### 5.4 IS Phase 0 (M1) gate

**Assessment: NOT EVALUATED.**

The roadmap identifies WP6-WP8 and the formal M1 gate record as incomplete. This code review cannot
mark M1 PASS or FAIL. The gate record must use the risk picture current in `RISK_REGISTER.md`, not
the severities in this evidence report. Under the current register:

- R-002 is not an M1 gate item and may not be used to retroactively fail WP5.
- R-003 is reviewed at every gate but is not by itself a blocker.
- R-004 is an M1 affected-gate risk and requires the disposition defined by the living plan and
  register.
- any newly accepted review finding that becomes a tracked risk must be in the register before the
  gate record is finalized.

### 5.5 IS Phase 1 (M2) security gate

**Assessment: future gate; current inputs identify blocking work.**

R-001 and R-002 are both authoritative High risks assigned to M2. Under governance, a High risk
blocks its affected security gate unless remediated or covered by an explicitly approved
compensating treatment. CR-001 is evidence for R-002. CR-002 and CR-005 must be triaged before the
M2 gate because they affect fail-secure policy evaluation and authentication controls.

M2 is the earliest milestone at which this report directs product-logic remediation. Closure must
include code changes, focused tests, affected regression suites, requirement evidence, and current
risk/RTM updates. A risk-treatment decision does not close a defect or change an unverified
requirement to `implemented-verified`.

### 5.6 IS Phase 5+6 (M6) security-hardening and release gates

**Assessment: NOT EVALUATED; reviewed baseline is not release-ready.**

The roadmap requires Critical and High vulnerabilities to be resolved for M6. Any review finding
that remains open at M6 must be reconciled with the then-current risk register, test evidence,
requirements, and release criteria. Aggregate unit-test success or an earlier migration gate is
not a substitute for release qualification.

## 6. Detailed Findings

### CR-001 - Rapid relaunch can bypass lock presentation

**Defect severity:** Critical  
**Area:** Security / authorization enforcement  
**Requirements and controls:** FR-001; SO-03 / INV-001; THR-ENF-004  
**Risk authority:** Existing R-002, High risk, affected gate IS Phase 1 (M2)  
**Current defect state:** Recorded; linked to R-002; no review-driven M1 logic change

#### Evidence

- [`ApplicationLockEngine.launchLockScreen`](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt#L123-L127)
  starts `LockScreenActivity` using `NEW_TASK | CLEAR_TOP`.
- [`LockScreenActivity.onPause`](../../../app/src/main/java/com/applock/authentication/ui/LockScreenActivity.kt#L223-L229)
  finishes an unauthenticated lock activity when another window covers it.
- The manifest declares the lock activity `noHistory`, so losing foreground removes the
  enforcement surface.
- The [post-Hilt emulator matrix](../campaigns/2026-08-09_wp5-matrix_nucbox-g5.md) records the same
  probabilistic bypass observed before Hilt.
- The [R-002 analysis](../security/2026-08-09_r-002-rapid-relaunch-race-analysis.md) identifies a
  lock-presentation ordering race rather than a DI regression.

#### Impact

Repeated relaunch can leave a protected application foreground without a valid authorization
session. This affects the primary protection boundary.

#### Gate treatment

The finding does not fail M1 because the authoritative risk register assigns R-002 to M2 and the
WP5 purpose was regression preservation. M2 must resolve or explicitly treat R-002 before its
security gate. The M2 design must use a deterministic presentation mechanism and a repetition
count capable of detecting a probabilistic failure.

#### Closure evidence

- implementation of the approved M2 presentation decision;
- high-repetition rapid-relaunch tests across the supported API matrix;
- representative slower real-hardware evidence;
- authorization and task-restoration regression coverage;
- R-002, threat-model, RTM, and gate-record updates.

### CR-002 - Policy loading fails open during process startup

**Defect severity:** Critical  
**Area:** Security / initialization and recovery  
**Requirements:** FR-001, FR-017, FR-179  
**Risk authority:** No stable risk identifier assigned by this review  
**Current defect state:** Recorded; triage before M2; no review-driven M1 logic change

#### Evidence

- [`LockPolicyManager`](../../../app/src/main/java/com/applock/applocker/policy/LockPolicyManager.kt#L21-L29)
  initializes the protected-package cache as an empty set and populates it asynchronously.
- [`evaluate`](../../../app/src/main/java/com/applock/applocker/policy/LockPolicyManager.kt#L36-L40)
  interprets absence from that set as not protected; there is no loading or failed state.
- [`ApplicationLockEngine`](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt#L54-L57)
  consumes the decision synchronously.
- [`ProtectionWatchdogService`](../../../app/src/main/java/com/applock/applocker/service/ProtectionWatchdogService.kt#L85-L90)
  can treat the same empty cache as evidence that protection is unnecessary.

#### Impact

An event arriving before the first policy snapshot can be treated as an allow decision. The same
window can stop protection-health monitoring before readiness is established.

#### Gate treatment

This is an M2 core-security input. Before M2 execution, triage must determine whether it becomes a
new living risk and which M2 acceptance criteria cover it. It may not be silently represented as a
verified fail-secure path.

#### Closure evidence

- explicit loading, ready, and failed policy states;
- fail-secure handling of unknown readiness;
- deterministic cold-start and process-restart tests;
- watchdog readiness and restart tests;
- affected requirement, risk, RTM, and M2 gate updates.

### CR-003 - Legacy migration removes the rollback source before commit

**Defect severity:** Critical  
**Area:** Reliability / data integrity / security-policy preservation  
**Requirements:** FR-163, FR-164, FR-228, FR-262, FR-372  
**Risk authority:** No stable risk identifier assigned by this review  
**Current defect state:** Recorded; phase assignment required; no action authorized solely by this report

#### Evidence

- [`snapshotAndRemovePlaintext`](../../../app/src/main/java/com/applock/core/database/AppLockDatabase.kt#L107-L144)
  reads legacy plaintext rows and removes the source database.
- [`build`](../../../app/src/main/java/com/applock/core/database/AppLockDatabase.kt#L90-L99)
  opens the encrypted database and imports the captured rows only after source removal.
- The existing error boundary cannot restore the removed source after process death, encrypted-open
  failure, storage failure, or an exception during import.

#### Impact

An interrupted conversion can permanently lose the protected-application policy and event history.
Loss of the policy can make previously protected applications appear unprotected on the next start.

#### Gate treatment

This finding overlaps the M1 migration-safety objective but is not the same as R-004's missing
Room migration-path risk. Before WP7, the project must explicitly include it in WP7 or assign it to
a later gate under the living governance process. The no-review-driven-M1-logic rule means this
report does not silently expand WP7.

#### Closure evidence

- durable backup or move-before-convert behavior;
- commit and validation before backup removal;
- idempotent restart after interruption at each migration stage;
- row-count, schema, and protected-policy verification;
- risk, RTM, campaign, and affected gate updates.

### CR-004 - Missing Room migration paths can destructively recreate data

**Defect severity:** Major  
**Area:** Reliability / schema migration  
**Requirements:** FR-228, FR-229  
**Risk authority:** Existing R-004, High risk, affected gate IS Phase 0 (M1)  
**Current defect state:** Recorded; linked to R-004; existing M1/WP7 plan controls treatment

#### Evidence

[`AppLockDatabase.build`](../../../app/src/main/java/com/applock/core/database/AppLockDatabase.kt#L90-L94)
enables `fallbackToDestructiveMigration()`. A missing migration path can therefore recreate the
database and discard protected-app policy, Vault index metadata, and security-event records.

#### Impact

A later schema version with an incomplete migration graph can silently destroy user security data
and leave encrypted blobs without their index records.

#### Gate treatment

This review corroborates R-004; it does not create a new risk or reschedule it. The current living
authorities assign remediation and the deliberate-failure drill to WP7, with closure checked in the
WP8 M1 gate record. If the project applies the no-logic-before-M2 direction to WP7 as well, those
living authorities must be revised before the gate criteria are changed.

#### Closure evidence

- removal of destructive fallback;
- the approved fail-safe open and recovery behavior;
- corrupt or mismatched-schema deliberate-failure evidence;
- normal upgrade evidence showing no regression;
- R-004 closure evidence and FR-228/FR-229 RTM updates.

### CR-005 - Biometric non-matches bypass product failure accounting

**Defect severity:** Major  
**Area:** Security / authentication and audit  
**Requirements:** FR-009, FR-010, FR-014, FR-081, FR-174  
**Risk authority:** No stable risk identifier assigned by this review  
**Current defect state:** Recorded; triage before M2; no review-driven M1 logic change

#### Evidence

- [`BiometricPrompt.AuthenticationCallback`](../../../app/src/main/java/com/applock/authentication/ui/LockScreenActivity.kt#L195-L212)
  routes success but leaves `onAuthenticationFailed()` without product failure accounting.
- [`ApplicationLockEngine.onUnlockFailure`](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt#L90-L103)
  is the path that increments persistent lockout state, records failure/lockout events, and invokes
  the configured intruder threshold.

#### Impact

Biometric non-matches do not participate in the product's cross-method consecutive-failure,
progressive-delay, audit, or optional capture controls. Platform biometric lockout is a separate
control and does not by itself verify the product requirements.

#### Gate treatment

Triage this finding as an M2 authentication-control item. Cancellation, negative-button choice,
temporary lockout, permanent lockout, unavailable hardware, and a genuine non-match must remain
distinct so non-attempt outcomes are not counted as failed attempts.

#### Closure evidence

- method-aware failure accounting;
- callback tests for success, non-match, cancellation, hardware error, temporary lockout, and
  permanent lockout;
- persistent lockout, audit, and intruder-threshold integration tests;
- risk/defect, requirement, RTM, and M2 gate updates.

### CR-006 - Blob deletion failure can be reported as success

**Defect severity:** Major  
**Area:** Privacy / data lifecycle / consistency  
**Requirements:** FR-085, FR-115  
**Risk authority:** No stable risk identifier assigned by this review  
**Current defect state:** Recorded; assign before the applicable Vault/data-lifecycle gate

#### Evidence

- [`VaultRepository.delete`](../../../app/src/main/java/com/applock/vault/VaultRepository.kt#L77-L81)
  removes the index row before confirming blob deletion and ignores the delete result.
- [`IntruderLogViewModel.delete`](../../../app/src/main/java/com/applock/privacy/ui/IntruderLogViewModel.kt#L35-L40)
  follows the same row-first pattern for intruder evidence.
- Delete-all iterates the currently observed event snapshot before removing rows, so a concurrent
  capture can produce an additional orphaned blob.

#### Impact

The product can display a successful deletion after losing the only index reference while encrypted
ciphertext remains in private storage. The user has no recovery path and later cleanup cannot
identify the intended item from normal application state.

#### Gate treatment

This is not an M1 logic item. Assign it to the earliest milestone that owns the affected Vault,
privacy, and data-lifecycle acceptance criteria, then require closure before that gate. The roadmap
currently places Vault feature completion in IS Phase 2 (M3) and broad data lifecycle in IS Phase 4
(M5); the project lead must choose the controlling gate rather than leave the finding unassigned.

#### Closure evidence

- recoverable pending-deletion state or equivalent transactional protocol;
- verified blob deletion before final index removal;
- retry and startup reconciliation for orphan rows and blobs;
- coordination with in-flight capture and delete-all operations;
- visible failure outcomes plus risk/defect, RTM, and gate updates.

## 7. Verification and Coverage Assessment

Existing JVM tests cover policy behavior after cache warm-up, session policies, PIN hashers,
lockout arithmetic and persistence, intruder thresholds, MIME classification, and selected
architecture rules. Recorded M1 evidence also covers detekt, lint, minified release assembly,
environment builds, Hilt wiring, the terminal no-`Graph` rule, and selected device regressions.

The reviewed baseline lacks focused automated evidence for:

- lock-presentation lifecycle and high-repetition rapid relaunch;
- policy and watchdog behavior before the first policy snapshot;
- interrupted plaintext conversion and each recovery checkpoint;
- a complete supported Room migration graph and deliberate mismatch behavior;
- biometric callback-to-lockout/audit integration;
- Vault and intruder-storage deletion failure and reconciliation;
- credential upgrade and encrypted-preference failure behavior.

These gaps explain why a green unit-test suite can coexist with the findings. They should be
converted into phase-owned test designs now, while execution remains tied to the milestone that is
authorized to change the corresponding logic.

## 8. Required Governance Follow-up

The following actions do not require product-logic changes and may occur during M1:

1. Preserve this report as immutable evidence once committed.
2. Triage CR-002, CR-003, CR-005, and CR-006 into the project's defect records and determine
   whether each creates a new or changed living risk.
3. Do not invent `R-NNN` identifiers in this report; assign them only through
   `RISK_REGISTER.md` and include category, likelihood, impact, risk severity, owner, affected
   gates, provenance, treatment, and review triggers.
4. Keep CR-001 mapped to R-002 and CR-004 mapped to R-004; do not duplicate those risks.
5. Assign each accepted finding to a canonical `IS Phase N (Mx)` gate using `ROADMAP.md`.
6. Define test and evidence requirements before M2 implementation begins.
7. Reassess affected RTM rows when code changes occur; no status promotion to
   `implemented-verified` without retained evidence.
8. At the M1 gate, use the current risk register and applicable M1 exit criteria. Do not use this
   report's defect severity as a substitute for the authoritative risk picture.
9. File a new dated review or verification report after remediation. Do not edit this evidence
   report after commitment.

## 9. Final Conclusion

The reviewed Hilt migration is adequately supported as a behavior-preserving M1 foundation change.
The independent review does not identify a basis to retroactively fail WP5 or to order unrelated
logic changes during WP6. The source baseline nevertheless contains material security, migration,
authentication, and data-lifecycle defects.

The governance-correct disposition is therefore not a generic "security gate failed." It is:

- **WP5 regression-preservation:** supported as PASS by existing evidence and the living roadmap;
- **IS Phase 0 (M1):** not yet evaluated; complete WP6-WP8 and use the authoritative register and
  gate record;
- **review-driven logic remediation:** begins no earlier than IS Phase 1 (M2);
- **R-002:** High risk and M2 security-gate input, not an M1 blocker;
- **R-004:** High risk currently assigned to M1/WP7 unless the living authorities are formally
  changed;
- **remaining findings:** triage, assign, and verify at their affected gates; and
- **release readiness:** not established by this static review or by the current M1 evidence.

This conclusion preserves the distinction between evidence, living status, risk authority,
phase-specific exit criteria, and release approval required by the project's governance model.
