# Independent Formal Code Review - M7/WP1 Security Harness Rework

**Date filed:** 2026-08-31  
**Reviewer:** Anonymous independent reviewer  
**Host:** Undisclosed  
**Document class:** Evidence - immutable after commit (`GOVERNANCE.md` Section 5.1)  
**Review type:** Independent static code review with build/syntax verification  
**Produced against:** uncommitted WP1 working tree based on commit
`0b66597a93ec17c55e2f862d17df7de0c318a5b7` (`0b66597`)  
**Milestone context:** M7, WP1 harness rework; WP0 complete; WP2 presentation swap next  
**Authoritative risk context:** R-002 remains an Open High project risk affecting M7. This review
assesses defects in the proposed R-002 test control; it does not open, close, duplicate, or accept
that risk.

**Review disposition:** **CHANGES REQUESTED. DO NOT USE THE CURRENT WORKING TREE AS WP1, WP2, OR
WP6 GATE EVIDENCE.** The direction is sound, the shell files are syntax-clean, and the Android test
compiles. However, two Critical test-control defects can report security success without proving the
claimed property: `LOCK_ENGINE=prod` still executes the spike-only OV-4 driver, and the OV-4 scorer
does not enforce the stated BEHIND-duration rule. Seven additional Major/Minor control gaps affect
gate-profile integrity, WP2 provisioning, artifact provenance, negative-control coverage, retained
counts, burst isolation, and skip semantics.

This disposition is evidence for the applicable work-package decision; it is not itself a gate
record. Gate state remains controlled by `M7_PLAN.md`, `RISK_REGISTER.md`, the RTM, and the eventual
dated campaign/gate record.

---

## 1. Purpose and Review Question

This review answers four questions:

1. Does the WP1 shell port assert the overlay security boundary it says it asserts?
2. Can the same harness distinguish spike and production execution without false attribution?
3. Can a green result be retained as reproducible evidence under the numeric M7 Section 11
   protocol?
4. Does the harness preserve the strict WP1 -> WP2 -> WP3 mechanism-isolation sequence in
   `M7_PLAN.md`?

The review separates implementation quality from gate status. A compiling test or exit code zero is
not by itself evidence that the protected behavior ran, that the intended engine ran, or that the
canonical profile ran.

## 2. Authority and Classification

### 2.1 Governing sources

The review applies:

- [`GOVERNANCE.md`](../../process/GOVERNANCE.md), especially the separation among evidence,
  defects, risks, and gate authority;
- [`M7_PLAN.md`](../../process/M7_PLAN.md), especially WP1 acceptance, WP2/WP3 sequencing, the
  Section 10 lanes, and the Section 11 numeric protocol;
- [`RISK_REGISTER.md`](../../process/RISK_REGISTER.md), especially R-002;
- [`ADR-020`](../../architecture/adr/ADR-020-overlay-lock-presentation.md) and ADR-021;
- the WP0 Moto G, NucBox, FTL, and biometric campaign evidence; and
- the existing [formal-review classification rules](2026-08-11_formal-code-review.md).

### 2.2 Classification vocabulary

- **Defect severity** uses **Critical / Major / Minor** per the Test Specification and
  `GOVERNANCE.md` Section 5.4.
- **Defect priority** is expressed as the affected work package or gate.
- **Risk severity** remains authoritative only in `RISK_REGISTER.md`. The term “Critical defect” in
  this report does not silently create a Critical project risk.

### 2.3 Review status vocabulary

| Status | Meaning in this review |
|---|---|
| Acceptable | The reviewed implementation supports its stated purpose, subject to dynamic evidence. |
| Changes requested | A code/control defect must be corrected before the affected gate relies on it. |
| Not performed | The check was intentionally omitted from this static review. |
| Not evaluated | Outside the requested file scope or not executable in this review environment. |

## 3. Scope and Method

### 3.1 Included files

- [`scripts/e2e/lib.sh`](../../../scripts/e2e/lib.sh)
- [`scripts/e2e/setup_device.sh`](../../../scripts/e2e/setup_device.sh)
- [`scripts/e2e/ov4_rapid_relaunch.sh`](../../../scripts/e2e/ov4_rapid_relaunch.sh)
- [`scripts/e2e/ov3_fast_switch.sh`](../../../scripts/e2e/ov3_fast_switch.sh)
- [`scripts/e2e/smoke_core.sh`](../../../scripts/e2e/smoke_core.sh)
- [`scripts/e2e/f3_self_gate.sh`](../../../scripts/e2e/f3_self_gate.sh)
- [`scripts/e2e/run_all.sh`](../../../scripts/e2e/run_all.sh)
- [`OverlayRaceUiTest.kt`](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt)

The reviewed delta contains 246 additions and 100 deletions across these eight files relative to
`0b66597`.

### 3.2 Supporting sources inspected

The review also read, without expanding the implementation scope:

- `UsagePollService`, `OverlayController`, the spike manifest declarations, and Gradle variant/test
  configuration, to determine what the instrumentation test actually drives;
- the modified `scripts/e2e/README.md`, to check public runner claims;
- current WP1/WP2 plan text and Section 11; and
- prior WP0 campaign evidence, to distinguish a known platform residual from a harness defect.

### 3.3 Executed verification

| Check | Result |
|---|---|
| Git Bash `bash -n` over all seven shell files | **PASS** |
| `git diff --check` over the eight reviewed files | **PASS** |
| `:app:compileProdDebugAndroidTestKotlin --no-daemon` | **PASS**; 35 tasks up-to-date |
| `LOCK_ENGINE=spike run_all.sh --skip-setup -n 0` | **DEFECT REPRODUCED**; printed `VERDICT: PASS (0/0...)`, exit 0 |
| `LOCK_ENGINE=spike smoke_core.sh` | **CONTROL GAP REPRODUCED**; printed SKIPPED, exit 0 |
| Android device execution | **NOT PERFORMED**; omitted from this static code review |
| ShellCheck | **NOT EVALUATED**; ShellCheck is not installed on the review host |

No Moto G, GMD, NucBox, FTL, negative-grant, or production-overlay run was executed by this review.
The report therefore does not independently mark any dynamic WP1 acceptance criterion PASS.

### 3.4 Baseline integrity

The target files were uncommitted when reviewed. Appendix A records SHA-256 hashes so later readers
can determine whether a cited file is the reviewed artifact. Any change to a cited file requires a
new review or an explicit verification addendum; this report must not be edited after commitment.

## 4. Executive Assessment

The change makes several good architectural moves:

- OV-4 delegates to the existing instrumentation test instead of maintaining a second shell race
  implementation.
- The shell probe correctly uses `dumpsys window`, not `dumpsys window windows`, and distinguishes
  current focus from mere title presence.
- Usage Access and overlay capability setup replace the obsolete accessibility setup for the WP0
  usage-poll spike.
- `run_all.sh` omits PIN/relock/self-gate checks from the spike check list rather than pretending the
  spike implements those product behaviors.
- Commands remain scoped through a device serial, result blocks name the selected engine and counts,
  logcat is cleared before an OV-4 run, and silent JUnit assumption skips are converted to failures.
- The Android instrumentation source compiles against `prodDebug`.

Those strengths do not yet make the harness gate-safe.

| ID | Defect severity | Area | Finding | Earliest affected gate |
|---|---|---|---|---|
| WP1-CR-001 | Critical | Engine attribution | `LOCK_ENGINE=prod` still executes the spike-only OV-4 driver | WP2 presentation gate |
| WP1-CR-002 | Critical | R-002 assertion | BEHIND duration and ratio are not measured as Section 11 defines them | WP1 baseline / WP2 |
| WP1-CR-003 | Major | Gate profile | Zero/noncanonical inputs can produce a gating-looking PASS | WP1 acceptance |
| WP1-CR-004 | Major | Sequencing/setup | Accessibility provisioning is removed before the WP2 accessibility-fed presentation step | WP2 entry |
| WP1-CR-005 | Major | Artifact provenance | Missing/failed installs can silently execute stale app or test APKs | WP1 baseline |
| WP1-CR-006 | Major | Negative control | The mandatory missing-overlay-grant behavioral control is not implemented | WP1 acceptance |
| WP1-CR-007 | Major | Evidence retention | A run can PASS without a retained or validated TOP/BEHIND/ABSENT count line | WP1 baseline |
| WP1-CR-008 | Major | Burst isolation | `settle()` can coalesce HOME and target events and manufacture ABSENT failures | WP1 fleet baseline |
| WP1-CR-009 | Minor | Skip semantics | Direct prod-only scripts report unsupported execution as exit 0 | Operator/CI use |

## 5. Gate Alignment

### 5.1 WP1 harness acceptance

**Assessment: current implementation does not support acceptance.**

WP1 requires a Section 11-shaped spike run plus a deliberately missing overlay grant that makes the
behavioral probe fail. The current default is a lighter profile, accepts vacuous inputs, does not
implement the negative control, and can retain a PASS without quantitative counts. The fleet run is
also still pending in `scripts/e2e/README.md`.

WP1 may proceed after the WP1-owned findings are corrected and a dated campaign establishes:

- exact gate-profile parameters and actual sample totals;
- current app/test artifact identities;
- API 30/33/35/36 parser behavior;
- the missing-overlay-grant negative control;
- Moto G strict-1500 ms evidence; and
- any NucBox scaled-timeout result explicitly classified diagnostic, not canonical gate evidence.

### 5.2 WP2 presentation swap

**Assessment: future gate; blocked by WP1-CR-001 and WP1-CR-004 if used unchanged.**

WP2 intentionally changes presentation while keeping accessibility as the known-good detector. The
current `prod` setup assumes an already-running detector after deleting all accessibility setup, and
the production-labeled OV-4 wrapper still invokes the spike launcher/service/actions. A green
`engine=prod` report would therefore not prove production OV-4.

### 5.3 WP3 detection swap

**Assessment: not evaluated, but the detector boundary must remain explicit.**

Usage Access becomes the production detector at WP3, not implicitly during WP2. The harness needs a
separate detector seam (`a11y` vs `usage`) or a formally revised M7 sequence. Reusing
`LOCK_ENGINE=prod` for both presentation and detector selection conflates the mechanisms that the plan
deliberately isolates.

### 5.4 WP6 matrix and R-002 evidence

**Assessment: future gate; the current OV-4 result format is not sufficient closure evidence.**

R-002 remains Open and High in the risk register. Corrected production OV-4 evidence must name the
actual driver, exact artifact hashes, fixed gate profile, exact totals, timeout, device/API, and the
window-state measurements. No finding in this report changes R-002 status.

## 6. Detailed Findings

### WP1-CR-001 - Production mode still executes the spike-only OV-4 driver

**Defect severity:** Critical  
**Area:** Security-test validity / engine attribution  
**Affected gates:** WP2, WP6, M7  
**Related risk:** R-002 (do not duplicate)

#### Evidence

- [`run_all.sh`](../../../scripts/e2e/run_all.sh#L27-L32) selects the full suite for every value other
  than literal `spike` and labels the report with that value.
- [`ov4_rapid_relaunch.sh`](../../../scripts/e2e/ov4_rapid_relaunch.sh#L38-L43) passes only the test
  class and numeric parameters. It does not pass `LOCK_ENGINE`, `OVERLAY_WINDOW_TITLE`,
  `POLL_SERVICE`, an app id, or a scenario-driver selector to instrumentation.
- [`OverlayRaceUiTest.grantAndStart`](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L43-L73)
  hard-codes the spike launcher and spike foreground service flow.
- Its cleanup and settle paths hard-code `com.applock.spike.STOP` and
  `com.applock.spike.DISMISS` at [lines 80-83](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L80-L83)
  and [150-153](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L150-L153).
- `APP_PKG`, `LAUNCHER`, `POLL_SERVICE`, and `OVERLAY_TITLE` remain compile-time spike values at
  [lines 169-176](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L169-L176).
  The claim that only two constants carry the suite to production omits the launcher, actions,
  provisioning model, protected-target setup, and app-id behavior.

#### Impact

After the production scripts begin passing at WP2, OV-4 can still pass against the retained spike
while `run_all.sh` prints `engine=prod`. That is a false attribution at the test guarding the primary
R-002 security boundary. `APP_ID` flavor overrides create the same class of ambiguity because the
shell wrapper is configurable while the test target remains `com.applock`.

#### Required correction

Until a production scenario exists, reject `LOCK_ENGINE=prod` before OV-4 and never emit a production
PASS. Then separate the reusable window assertion from engine-specific driving, for example:

- `SpikeOv4Scenario`: spike launcher, exported spike FGS, target extras, DISMISS/STOP actions;
- `ProductionOv4Scenario`: production provisioning and lifecycle, with no assumption that the
  production detector is an exported shell-startable service; and
- an instrumentation/result field naming the driver, app id, service/adapter, overlay title, and
  test build identity actually used.

The wrapper must compare the requested engine with the test-reported engine and fail on mismatch.

### WP1-CR-002 - OV-4 does not enforce the Section 11 BEHIND rule

**Defect severity:** Critical  
**Area:** Security assertion / overlay focus and content exposure  
**Affected gates:** WP1 baseline, WP2, WP6  
**Related risk:** R-002

#### Evidence

[`awaitOverlay`](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L121-L131)
returns `TOP` as soon as any later poll sees focus. Therefore a burst that is BEHIND for almost the
entire timeout and becomes TOP on the last poll is recorded as TOP. Conversely, a window that is
BEHIND for the entire timeout is returned as BEHIND, and the aggregate assertion permits some such
bursts.

This is the opposite of the documented rule: BEHIND is allowed only as a sub-poll self-healing
flicker resolved within one poll interval, with a ratio no greater than 2%. The implementation records
neither BEHIND sample count nor the maximum consecutive BEHIND duration.

The aggregate check at
[`behind <= maxOf(1, total * 2 / 100)`](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L112-L118)
also exceeds 2% for small profiles. At the wrapper default of 25 outcomes, one BEHIND is 4% but still
passes.

#### Impact

The test can accept a long interval in which the protected activity has focus and the overlay does
not hold the intended modal input boundary. With a scaled 4000 ms timeout, the accepted interval can
be substantially longer. This is a false negative in the control intended to establish that the
overlay closes R-002.

#### Required correction

Define and record the measurement explicitly:

- retain the initial appearance grace separately from post-presence focus behavior;
- count each 100 ms state sample, or record the maximum BEHIND streak per burst;
- require every BEHIND streak to resolve to TOP within the accepted poll interval `P`;
- fail a burst that is still BEHIND at the deadline;
- enforce the exact ratio without a `maxOf(1, ...)` exception; and
- emit TOP, transient-BEHIND samples/streaks, unresolved-BEHIND, and ABSENT separately.

Captured parser fixtures for API 30/33/35/36 and Samsung One UI should unit-test the classification.

### WP1-CR-003 - Gate profile and input validation permit vacuous or noncanonical PASS

**Defect severity:** Major  
**Area:** Test execution / evidence integrity  
**Affected gate:** WP1 acceptance

#### Evidence

- Section 11 defines 50 bursts x 20 relaunches x 5 repeats at `T_appear=1500 ms`. The wrapper defaults
  to 25 x 20 x 1 at
  [`ov4_rapid_relaunch.sh` lines 19-22](../../../scripts/e2e/ov4_rapid_relaunch.sh#L19-L22), while
  `run_all.sh` still emits an unqualified `VERDICT: PASS`.
- `RUNS` is not validated. The executed command
  `LOCK_ENGINE=spike scripts/e2e/run_all.sh --skip-setup -n 0` produced an empty table,
  `VERDICT: PASS (0/0 all green, engine=spike)`, and exit 0.
- `LOCK_ENGINE` is not validated in `run_all.sh`; any non-`spike` value is treated as production. With
  `-n 0`, `LOCK_ENGINE=typo` likewise returns PASS.
- The Kotlin `arg()` helper accepts zero without validation. `ov4_bursts=0` or `ov4_repeat=0` executes
  no measurement and satisfies both assertions.
- A nonnumeric Kotlin argument silently falls back to the default while the shell report can continue
  to display the requested value.
- `OV4_T_APPEAR_MS` is unbounded and unclassified. A slow-rig diagnostic timeout can produce the same
  PASS label as the fixed canonical gate timeout.

#### Impact

An operator or CI job can retain a green-looking report after running no checks, too few checks, the
wrong engine name, or a diagnostic timeout. The result does not reliably establish WP1 acceptance.

#### Required correction

Introduce explicit profiles and fail-closed validation:

- `gate`: fixed 50 x 20 x 5, fixed 1500 ms, positive run count;
- `quick`: smaller smoke profile, result labeled non-gating;
- `diagnostic-swgpu`: timeout may scale, result labeled noncanonical/non-gating;
- reject unknown engine/profile names and non-positive/noninteger counts in both shell and Kotlin;
- place reasonable upper bounds on counts and timeout to prevent accidental multi-hour runs; and
- include the profile and a canonical/noncanonical flag in the report verdict.

### WP1-CR-004 - WP2 setup removes the detector that WP2 intentionally retains

**Defect severity:** Major  
**Area:** Work-package sequencing / device provisioning  
**Affected gate:** WP2 entry and presentation acceptance

#### Evidence

WP2 in `M7_PLAN.md` swaps presentation while keeping accessibility as the known-good detector; the
UsageStats detector lands at WP3. The reviewed setup deletes accessibility provisioning and grants
only Usage Access plus overlay at
[`setup_device.sh` lines 27-32](../../../scripts/e2e/setup_device.sh#L27-L32).

For `LOCK_ENGINE=prod`,
[`arrange_protected`](../../../scripts/e2e/lib.sh#L221-L233) is a no-op and assumes the product detector
is already running. On a clean GMD emulator at WP2, neither assumption is established: Usage Access
is granted before the production UsageStats detector exists, while the retained accessibility
detector has not been enabled.

#### Impact

The full production-path suite cannot be provisioned reproducibly at the WP2 presentation-only step.
Failures would conflate missing detector setup with presentation defects, defeating the plan’s
one-mechanism-per-WP isolation.

#### Required correction

Separate presentation and detection selection, for example `LOCK_ENGINE=activity|overlay` and
`DETECTOR=a11y|usage`. Retain the emulator accessibility provisioning path through WP2, scoped only to
the known-good detector baseline, then remove it at WP3 after the UsageStats detector has parity.
Alternatively, formally change the M7 sequence and its risk rationale before changing the harness;
the harness alone may not silently move the detection swap into WP2.

### WP1-CR-005 - Install handling can execute stale app or test artifacts

**Defect severity:** Major  
**Area:** Reproducibility / artifact provenance  
**Affected gates:** WP1, WP2, WP6

#### Evidence

- If the app APK is missing, [`setup_device.sh`](../../../scripts/e2e/setup_device.sh#L20-L25) assumes
  an app is already installed. If `adb install` does not report Success, it logs information and
  continues.
- [`ov4_rapid_relaunch.sh`](../../../scripts/e2e/ov4_rapid_relaunch.sh#L29-L36) suppresses an
  androidTest install failure with `|| true` and then accepts any already-registered instrumentation
  whose package text matches `${APP_ID}.test/`.
- Neither path proves that the installed app and test APKs are the reviewed pair or correspond to the
  commit/hash stated in a campaign report.

#### Impact

A signature mismatch, stale installed test package, missing local artifact, or failed reinstall can
leave an older harness on the device. That older artifact can pass and be cited as evidence for the
current source.

#### Required correction

Fail immediately on a missing required APK or failed installation unless an explicit
`USE_PREINSTALLED=1` diagnostic mode is selected. For gate runs:

- record host SHA-256 for both APKs;
- install both artifacts successfully;
- query and record the exact instrumentation component and package version/build provenance;
- reject an unexpected pre-existing test runner instead of merely accepting its presence; and
- include device serial, API/build fingerprint, app/test hashes, and source commit in the result
  block.

### WP1-CR-006 - The mandatory missing-grant negative control is absent

**Defect severity:** Major  
**Area:** Test-control validation  
**Affected gate:** WP1 acceptance

#### Evidence

WP1 acceptance requires a deliberately missing overlay grant to make `detection_working()`/smoke
fail. The reviewed files only set the two appops to allow and verify the positive state. No script
denies/revokes the overlay op, restarts the app/service into a clean state, launches the target,
asserts the expected presentation failure, restores the grant, and confirms recovery.

#### Impact

The baseline does not prove that the behavioral probe fails when the capability required to draw the
overlay is absent. A probe that accidentally matches stale window state or the wrong title could pass
the positive path without the required sensitivity check.

#### Required correction

Add an idempotent negative-control scenario. It should:

1. dismiss/force-stop the active test engine;
2. set `android:system_alert_window` to deny/default;
3. confirm the op is not allowed;
4. start detection and launch the normal protected target;
5. assert no valid TOP overlay and assert the setup/protection-health path reports interruption;
6. restore the grant; and
7. rerun the positive probe successfully.

The negative control’s PASS means “the harness detected the deliberately broken capability”; it must
not be represented as a product-protection PASS.

### WP1-CR-007 - OV-4 can PASS without retained quantitative counts

**Defect severity:** Major  
**Area:** Evidence retention / result parsing  
**Affected gates:** WP1, WP2, WP6

#### Evidence

[`ov4_rapid_relaunch.sh`](../../../scripts/e2e/ov4_rapid_relaunch.sh#L41-L54) substitutes
`<no count line in logcat>` when the log record is missing, but still reports PASS whenever the
instrumentation stdout contains `OK (1 test)`. `boost_logcat` is best effort and always returns
success, and setup unconditionally says the buffer is 16 MB without reading the effective size.

The wrapper also does not parse the count line to prove:

- `TOP + BEHIND + ABSENT == bursts * repeat`;
- the logged parameters equal the requested parameters; or
- the line belongs to the requested engine and artifact.

#### Impact

The JUnit assertion may have run, but the retained campaign block lacks the quantitative evidence
needed to audit the profile and budget. Logcat loss or a mismatched/stale artifact can therefore
produce a green but non-reproducible record.

#### Required correction

Make the structured result part of instrumentation output or test-result status rather than relying
only on logcat. Require exactly one current-run result carrying a run id, engine/driver, parameters,
counts, total, timeout, and test build identity. Parse it, validate the arithmetic, and fail the
wrapper if it is missing or inconsistent. If the logcat buffer size matters, read it back and report
the actual value rather than unconditional success text.

### WP1-CR-008 - Burst settle does not establish a new foreground transition

**Defect severity:** Major  
**Area:** Test determinism / false failure  
**Affected gate:** WP1 fleet baseline

#### Evidence

The test configures a 400 ms spike poll interval, but
[`settle()`](../../../app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt#L150-L153) presses
HOME, sends DISMISS, and waits only 300 ms after the command. It does not wait for the detector to
observe the launcher/neutral package.

`UsagePollService` retains `lastForeground` and delivers only when the latest package differs. On a
slow main-thread/software-GPU lane, the next poll can run after the target is relaunched, read both
HOME and target events, select target as latest, and see the same target as the prior burst. It then
suppresses `onForeground`, leaving the overlay absent for a reason created by test setup rather than
the product race.

#### Impact

The test can manufacture ABSENT failures and contaminate the NucBox latency diagnosis. This does not
create a false security pass, but it makes the mandatory high-repetition gate flaky and obscures
whether a failure belongs to detector latency, renderer latency, or scenario isolation.

#### Required correction

Do not use a fixed sleep shorter than or close to the detector interval as a state acknowledgment.
The spike scenario should expose a debug-only reset/acknowledgment that confirms neutral foreground
was processed and `lastForeground` was reset, or the test should otherwise wait on an observable
neutral-state marker before beginning the next burst. Assert the overlay is absent after settle, then
start the measured target transition. Production needs an equivalent scenario boundary that does not
alter release behavior.

### WP1-CR-009 - Unsupported direct checks return success

**Defect severity:** Minor  
**Area:** Operator contract / skip semantics  
**Affected use:** direct script and generic CI invocation

#### Evidence

`smoke_core.sh`, `ov3_fast_switch.sh`, and `f3_self_gate.sh` print SKIPPED under the default spike
engine and exit 0. The behavior was reproduced directly for `smoke_core.sh`. `run_all.sh` correctly
omits those checks in spike mode, but an operator or generic automation invoking the individual file
receives a success code despite executing no assertion.

#### Impact

An unsupported check can be recorded as green outside the engine-aware runner. The text “Validated
at WP2” is also future intent, not current evidence.

#### Required correction

Because `run_all.sh` already omits unsupported checks, individual prod-only scripts should fail fast
with a distinct unsupported/misconfiguration code when invoked under `spike`. If a formal SKIP state
is retained, the runner and report must preserve SKIP as a third status and never convert it to PASS.
Use future-tense wording until a dated WP2 result exists.

## 7. File-by-File Assessment

### `lib.sh`

**Changes requested.** The overlay probe and serial-scoped adb helpers are reasonable, and the
`dumpsys window` correction preserves the WP0 lesson. The engine seam is incomplete because it
combines presentation and detector selection, validates unknown engines only in one helper, and does
not control the instrumentation driver. `overlay_z()` also remains an OEM-format parser without
fixture tests. The retained name `is_lockscreen()` is acceptable as a compatibility shim only if its
new TOP semantics remain explicit.

### `setup_device.sh`

**Changes requested.** The prod/debug path and positive appops checks are improvements. Installation
must be fail-closed and provenance-recorded. The production branch cannot provision WP2’s retained
accessibility detector, and the WP1 negative-grant control is missing. Persistent device changes
(`stayon`, enlarged logcat) should be reported accurately and preferably restored by a cleanup step.

### `ov4_rapid_relaunch.sh`

**Changes requested.** Delegating to instrumentation is the right consolidation. The wrapper needs
an actual engine/scenario handshake, exact artifact installation, strict profile validation, required
structured counts, and a non-gating label for scaled-timeout diagnostics. Existing instrumentation
presence is not proof that the intended test APK is installed.

### `ov3_fast_switch.sh`, `smoke_core.sh`, and `f3_self_gate.sh`

**Conditionally acceptable after skip-contract correction.** Their production assertions are not
dynamically reviewable until WP2. Overlay wording is consistent, and `run_all.sh` does not call them
under the spike. Their individual exit-0 SKIP behavior should be corrected. OV-3 and smoke also depend
on the WP2 detector-provisioning fix.

### `run_all.sh`

**Changes requested.** Engine-aware check selection is a useful improvement. The runner needs strict
engine and numeric validation, an explicit gate/quick/diagnostic profile, non-vacuous run enforcement,
and artifact/profile fields sufficient for a retained campaign record. “PASS” must be reserved for an
executed profile eligible for the named gate.

### `OverlayRaceUiTest.kt`

**Changes requested.** It compiles and remains a valuable black-box window test, but it is not yet the
claimed spike-to-production artifact. The driver is spike-specific, the BEHIND measurement does not
match Section 11, numeric arguments are not validated, and burst setup lacks a detector-state
acknowledgment. Split scenario driving from assertion logic and emit a structured, runner-retained
result.

## 8. Required Remediation and Verification Order

1. **Fail closed on identity and profile.** Validate `LOCK_ENGINE`, run/count/timeout inputs, and
   artifact installation. Add explicit gate/quick/diagnostic profiles.
2. **Correct OV-4 measurement semantics.** Measure BEHIND samples/streak duration, reject unresolved
   BEHIND, enforce the exact ratio, and make burst settle deterministic.
3. **Build the real engine/detector seams.** Keep WP2’s accessibility detector independently
   selectable; add distinct spike and production OV-4 scenario drivers. Reject production mode until
   the production driver exists.
4. **Make evidence self-describing.** Emit and parse a structured current-run result with engine,
   profile, parameters, counts, totals, run id, app/test identity, device serial/API/fingerprint, and
   artifact hashes.
5. **Implement the missing-grant negative control.** Prove failure sensitivity and recovery on at
   least one emulator before fleet execution.
6. **Add host-side parser/control tests.** Cover captured AOSP API 30/33/35/36 and Samsung One UI
   `dumpsys window` fixtures, zero/invalid arguments, engine mismatch, missing count output,
   assumption skip, instrumentation crash, and stale/missing APK handling.
7. **Repeat static verification.** Git Bash syntax, ShellCheck, diff check, Android-test compile, and
   focused unit tests must be clean.
8. **Run the dated WP1 campaign.** Moto G uses the canonical 1500 ms gate profile. NucBox scaled
   timeout runs are diagnostic and labeled as such; API/OEM parser portability and the A/B result are
   recorded separately. File a new immutable verification report.

## 9. Final Conclusion

The WP1 implementation chooses the right broad direction: preserve the operator workflows, move the
race truth into the already-portable instrumentation test, replace obsolete activity/a11y assumptions
for the usage-poll spike, and retain external window truth for the actual presentation boundary. The
source is syntactically sound and the Android test compiles.

The current working tree is nevertheless not a trustworthy security gate. Production labeling does
not select production behavior, BEHIND exposure is not measured to the stated rule, a zero-run command
returns PASS, the canonical profile is not enforced, WP2’s retained detector cannot be provisioned,
artifact installs can go stale, the required negative control is missing, successful counts are
optional, and burst isolation can manufacture failures.

The governance-correct disposition is therefore:

- **WP1 code review:** changes requested;
- **WP1 dynamic acceptance:** not established by this review and not supportable until the listed
  WP1 controls are corrected;
- **WP2 production gate:** must not consume `engine=prod` output until a real production scenario and
  detector seam exist;
- **R-002:** remains Open and High under the authoritative risk register; and
- **release/security readiness:** not established by compilation, shell syntax, prior WP0 evidence,
  or the current uncommitted harness.

## Appendix A - Reviewed File Hashes

| SHA-256 | File |
|---|---|
| `fba8131b1cf716cafa4bbe0e6824326e7a51d56262e2ae82ef87357d69dfbfea` | `scripts/e2e/lib.sh` |
| `7137dc1b298e9bec62d04c77b6983511dd1ee11f659603bc8cb2fc6e88d7057d` | `scripts/e2e/setup_device.sh` |
| `2d194334c8955acd283bcd3740e7a2e76b4a2cedf062f8dac64d90280c0a07e1` | `scripts/e2e/ov4_rapid_relaunch.sh` |
| `897f8160fba8e29bec7d2dfbd4850b6caf1b27a2667fb0f3dfa60989bd59ba03` | `scripts/e2e/ov3_fast_switch.sh` |
| `3774a7ec6cf5e3a5f3acd076bc8242bd7a54ae2d442aef52ec7a8c680d36ff7a` | `scripts/e2e/smoke_core.sh` |
| `098825c276a2834bca7cd982221ca0d11d577e1fd08c0a09a17e85c4f8eb0353` | `scripts/e2e/f3_self_gate.sh` |
| `4ba73361b275fc8bf3fea75f45655b95c9c5523228510a5e6c9d169514d592e4` | `scripts/e2e/run_all.sh` |
| `44e3ae8b436ac1d47aa2aef1aa615d3959a9e06c4e2e6b6d4477de3d2fd9b617` | `app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt` |
