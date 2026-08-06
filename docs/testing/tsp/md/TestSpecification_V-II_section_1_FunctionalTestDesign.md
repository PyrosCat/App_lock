**Volume II — Functional Test Specification**

**Section 1 — Functional Test Design**

**1.1 Purpose**

This section defines the methodology, design principles, test-case structure, coverage expectations, traceability requirements, and execution considerations used to design functional tests for the Android App Lock application.

The purpose of functional test design is to establish a consistent and repeatable method for verifying that implemented application behavior satisfies the functional requirements defined by the Software Requirements Specification (SRS), while remaining aligned with the approved architecture, software design, security requirements, and implementation strategy.

This section establishes **how functional tests are designed**.

Detailed functional testing of individual application capabilities is specified in the subsequent sections of Volume II:

- Section 2 — Authentication Testing

- Section 3 — App Lock Testing

- Section 4 — Vault Testing

- Section 5 — Protected Applications

- Section 6 — Profiles

- Section 7 — Scheduling

- Section 8 — Automation Rules

- Section 9 — Notifications

- Section 10 — Settings

- Section 11 — Recovery Features

- Section 12 — Administrative Functions

- Section 13 — Future Expansion Features

Functional test design shall therefore be treated as a common methodology for those sections rather than as a replacement for their detailed test specifications.

**1.2 Functional Testing Objectives**

Functional testing shall provide objective evidence that implemented application behavior conforms to approved functional requirements.

The primary objectives are to:

1.  Verify that each applicable functional requirement is implemented according to its specified behavior.

2.  Verify expected behavior under valid operating conditions.

3.  Verify rejection or controlled handling of invalid conditions.

4.  Verify boundary and limit behavior.

5.  Verify state transitions and lifecycle behavior.

6.  Verify interactions between related functional requirements.

7.  Verify error handling and recovery behavior.

8.  Verify security-sensitive functional controls where they affect functional behavior.

9.  Detect regressions caused by implementation or dependency changes.

10. Maintain traceability between requirements, tests, execution evidence, defects, and verification status.

11. Provide sufficient evidence to support phase-gate and release decisions.

Functional testing shall not be considered complete solely because individual test cases pass. Requirement coverage, risk coverage, affected dependencies, regression scope, and evidence quality shall also be considered.

**1.3 Functional Test Design Principles**

Functional tests shall be designed according to the following principles.

**1.3.1 Requirement-Based Design**

Tests shall originate from approved functional requirements.

Each test shall identify the requirement or requirements that it verifies. Where a single requirement contains multiple independently verifiable behaviors, the requirement shall be decomposed into appropriate test conditions rather than relying on a single broad test.

Tests shall not introduce undocumented application behavior as though it were a requirement.

Where a requirement is ambiguous, incomplete, contradictory, or otherwise unsuitable for objective verification, the condition shall be identified for resolution rather than silently interpreted as an implementation requirement.

**1.3.2 Behavior-Based Verification**

Tests shall verify observable behavior rather than implementation details unless implementation-specific verification is explicitly required.

The primary concern of a functional test is whether the system:

- Accepts valid input.

- Rejects invalid input.

- Produces the specified result.

- Maintains the required state.

- Transitions between states correctly.

- Provides the specified user-visible behavior.

- Enforces applicable restrictions.

- Handles expected errors.

- Recovers according to the defined behavior.

Implementation details shall only become direct functional test conditions where they produce externally verifiable requirements or where another test specification explicitly requires implementation-level verification.

**1.3.3 Positive and Negative Testing**

Functional test suites shall contain both positive and negative test conditions.

Positive testing verifies that valid operations produce the expected results.

Negative testing verifies that invalid, unauthorized, malformed, incomplete, conflicting, or otherwise prohibited operations are handled correctly.

Examples include:

- Valid authentication versus invalid authentication.

- Valid configuration versus invalid configuration.

- Authorized operation versus unauthorized operation.

- Valid schedule versus conflicting schedule.

- Valid protected-app operation versus prohibited access.

- Valid recovery operation versus invalid recovery condition.

Negative testing shall verify both rejection and resulting system state. A system that correctly rejects an operation but is left in an unintended state shall not be considered functionally correct.

**1.4 Functional Test Condition Identification**

Functional test design shall begin by identifying testable conditions within each applicable requirement.

A test condition represents a distinct behavior, rule, state, input condition, or outcome that requires verification.

Test conditions should be derived from:

- Functional requirements.

- Acceptance criteria.

- Business rules.

- Input constraints.

- State transitions.

- Error conditions.

- Security-related functional constraints.

- Dependencies between requirements.

- User workflows.

- Recovery requirements.

- Configuration requirements.

- Known defect conditions.

- Regression impact analysis.

A requirement may therefore result in multiple test conditions.

For example, a requirement describing an authentication operation may require separate conditions for:

- Valid credentials.

- Invalid credentials.

- Missing credentials.

- Repeated failures.

- Boundary conditions.

- State after successful authentication.

- State after unsuccessful authentication.

- Recovery from the resulting state.

The exact conditions shall be determined from the authoritative requirement rather than assumed from the example.

**1.5 Test Design Techniques**

Functional testing shall use an appropriate combination of test design techniques.

**1.5.1 Equivalence Partitioning**

Input conditions shall be divided into equivalence classes where appropriate.

At minimum, applicable classes should include:

- Valid inputs.

- Invalid inputs.

- Boundary-valid inputs.

- Boundary-invalid inputs.

The objective is to reduce redundant testing while retaining representative coverage of materially different behavior.

**1.5.2 Boundary Value Analysis**

Boundary conditions shall be explicitly tested where requirements define limits, ranges, capacities, lengths, counts, durations, thresholds, or similar constraints.

Testing shall consider:

- Minimum valid value.

- Maximum valid value.

- Values immediately below the valid range.

- Values immediately above the valid range.

- Empty or zero values where applicable.

- Maximum supported collection sizes where applicable.

Boundary tests shall be derived from the actual requirement limits.

**1.5.3 Decision Table Testing**

Decision tables shall be used where behavior depends upon multiple conditions or combinations of rules.

This is particularly applicable to functionality involving:

- Multiple configuration conditions.

- Scheduling rules.

- Automation rules.

- Profile conditions.

- Authentication state.

- Application state.

- Recovery conditions.

- Conflicting settings.

Decision-table testing shall ensure that materially different combinations of conditions are represented.

**1.5.4 State Transition Testing**

State-based functionality shall be tested through defined state transitions.

Tests shall identify:

1.  Initial state.

2.  Triggering condition.

3.  Expected transition.

4.  Resulting state.

5.  Prohibited or invalid transitions.

6.  Recovery or return transitions where applicable.

This technique is especially important for functionality involving authentication, locking, unlocking, profiles, scheduling, automation, recovery, and application lifecycle behavior.

**1.5.5 Use-Case and Workflow Testing**

End-user workflows shall be tested as sequences of related functional operations.

Workflow tests shall verify that individual functions operate correctly when executed in their intended sequence.

Tests shall consider:

- Normal workflow.

- Interrupted workflow.

- Invalid workflow.

- Repeated workflow.

- Recovery from interruption.

- State persistence across workflow steps.

A function passing in isolation shall not be considered sufficient evidence that the complete workflow operates correctly.

**1.5.6 Pairwise and Combinatorial Testing**

Where numerous configuration variables create a large combination space, pairwise or other controlled combinatorial techniques may be used to improve coverage efficiency.

Combinatorial reduction shall not be used where exhaustive testing is required because of:

- Security risk.

- Safety or criticality.

- Explicit requirements.

- Known defect history.

- Release-critical behavior.

The selected reduction strategy shall be documented where it materially affects coverage.

**1.6 Test Case Structure**

Each functional test case shall contain sufficient information for an appropriately qualified tester to execute it consistently and determine whether the requirement has been satisfied.

The test case should include, as applicable:

| **Field** | **Description** |
|----|----|
| Test Case ID | Unique identifier for the test case. |
| Requirement ID | Requirement or requirements being verified. |
| Test Objective | Specific behavior being verified. |
| Priority | Test execution priority based on risk and importance. |
| Preconditions | Required application, device, account, configuration, or environmental state. |
| Test Data | Inputs and controlled data required for execution. |
| Setup | Actions required before execution. |
| Test Steps | Ordered execution instructions. |
| Expected Results | Observable expected behavior for each applicable step or outcome. |
| Postconditions | Expected state after execution. |
| Environment | Required device, Android version, build, or configuration. |
| Evidence | Required screenshots, logs, recordings, reports, or other evidence. |
| Automation Status | Manual, automated, partially automated, or planned. |
| Related Tests | Tests whose results or execution may affect this test. |
| Defect References | Applicable defect identifiers. |

Not every test case requires every field. Fields shall be included whenever they are necessary for repeatable execution and objective evaluation.

**1.7 Preconditions and Test State**

Functional test execution shall begin from a known and controlled state.

Preconditions shall identify all material conditions required to execute the test, including where applicable:

- Application installation state.

- Application version.

- Device state.

- Android version.

- Authentication state.

- User/profile state.

- Protected-application state.

- Configuration state.

- Scheduling state.

- Automation state.

- Network condition.

- Test data.

- Required permissions.

- Required system services.

Where a test modifies persistent state, the test shall define whether that state must be restored before subsequent tests.

Tests shall not rely on undocumented state left behind by a previous test unless that dependency is intentional and explicitly documented.

**1.8 Expected Results**

Expected results shall be specific enough to support an objective pass/fail determination.

Expected results shall describe observable behavior rather than subjective judgments.

Weak expected result:

The application works correctly.

Appropriate expected result:

The configured application remains protected when the defined lock condition is active, and an unauthenticated access attempt is rejected according to the specified authentication behavior.

Where multiple outcomes are possible, each applicable outcome shall be explicitly defined.

Expected results shall include relevant state changes where the requirement requires them.

**1.9 Functional Error Testing**

Functional tests shall verify error handling as part of normal functional verification.

Error testing shall consider:

- Invalid input.

- Missing input.

- Unsupported input.

- Conflicting configuration.

- Unauthorized operations.

- Invalid state.

- Interrupted operations.

- Resource unavailability.

- Dependency failure.

- Persistence failure where functionally observable.

- Recovery behavior.

Tests shall verify that errors:

1.  Are detected.

2.  Produce the specified response.

3.  Do not cause unintended state changes.

4.  Do not bypass applicable security or functional controls.

5.  Provide the required user or system feedback.

6.  Permit recovery where recovery is specified.

**1.10 Boundary and Limit Testing**

Functional tests shall explicitly address requirements containing limits.

Examples include:

- Maximum number of configured items.

- Minimum and maximum supported values.

- Maximum lengths.

- Empty collections.

- Repeated operations.

- Maximum supported schedules or rules.

- Repeated authentication attempts.

- State transition limits.

Where the SRS does not define a limit, the test specification shall not invent a contractual limit.

If an implementation limit is discovered during testing but is not defined by the requirements, it shall be recorded for engineering review rather than silently converted into a functional requirement.

**1.11 Functional Interaction Testing**

Individual requirements shall not be tested exclusively in isolation.

Where one feature affects another, functional tests shall verify the interaction.

Examples include:

- Authentication affecting app-lock behavior.

- Profiles affecting protected applications.

- Scheduling affecting lock state.

- Automation rules affecting configuration or application state.

- Notifications reflecting functional state changes.

- Recovery affecting authentication or protected state.

- Settings affecting behavior elsewhere in the application.

Interaction tests shall be derived from documented dependencies and risk analysis.

Detailed system integration testing remains within **Volume V — Integration, System & Release Testing**. Volume II shall focus on functional behavior and functional interactions necessary to establish correctness of the specified feature.

**1.12 Functional Regression Test Design**

Functional tests shall be reusable for regression testing.

A test previously passing shall not be assumed to remain valid indefinitely.

Regression testing shall be considered when:

- A requirement changes.

- Functional implementation changes.

- A defect is corrected.

- A dependency changes.

- Architecture changes.

- Security controls change.

- Configuration behavior changes.

- Platform behavior changes.

- A related feature changes.

- Regression analysis identifies potential impact.

Previously verified requirements may therefore return to a state requiring verification.

The current verification state shall reflect current evidence rather than historical execution alone.

**1.13 Defect-Oriented Test Design**

Defects discovered during functional testing shall produce appropriate follow-up testing.

When a defect is corrected, testing shall normally include:

1.  Reproduction of the original failure where practical.

2.  Verification that the correction resolves the defect.

3.  Verification of the affected requirement.

4.  Regression testing of directly affected functionality.

5.  Additional risk-based regression where warranted.

A defect correction shall not be considered verified merely because the modified code builds successfully.

The defect lifecycle established in Volume I shall remain applicable:

Detect

↓

Record

↓

Triage

↓

Investigate

↓

Correct

↓

Retest

↓

Regression

↓

Verify

↓

Close

**1.14 Functional Test Prioritization**

Functional tests shall be prioritized using risk and business/technical impact.

Priority should generally consider:

1.  Security-critical functionality.

2.  Authentication and access-control functionality.

3.  Critical application-lock behavior.

4.  Release-critical workflows.

5.  High-impact functional requirements.

6.  High-risk changes.

7.  Previously defective or unstable functionality.

8.  Cross-feature interactions.

9.  Medium-risk functionality.

10. Low-risk functionality.

Priority shall be reassessed when risk changes.

Test priority does not eliminate the requirement to maintain complete functional coverage.

**1.15 Functional Test Automation**

Functional tests shall be evaluated for automation based on repeatability, stability, execution frequency, risk, and suitability for automated verification.

Good candidates generally include:

- Repetitive deterministic tests.

- Regression tests.

- Boundary tests.

- Data-driven tests.

- Stable state-transition tests.

- Release-gate tests.

- Frequently executed high-risk tests.

Manual testing shall remain appropriate where:

- The behavior is difficult to automate reliably.

- Exploratory investigation is required.

- The feature is unstable or changing rapidly.

- Automated verification would provide insufficient evidence.

Automation shall not be treated as a substitute for coverage analysis or engineering judgment.

Detailed automation strategy and CI/CD integration are specified in **Volume VI — Test Management & Traceability**.

**1.16 Test Evidence Requirements**

Functional test execution shall produce sufficient evidence to demonstrate what was tested and what result was obtained.

Evidence may include:

- Test execution records.

- Screenshots.

- Device logs.

- Application logs.

- Automated test results.

- Build identifiers.

- Configuration records.

- Test data identifiers.

- Defect references.

- Video or other recordings where necessary.

- Reports generated by the application or test infrastructure.

Evidence shall be associated with the applicable test execution and controlled configuration.

A test result without sufficient evidence shall be evaluated for whether it provides adequate verification support.

**1.17 Traceability Requirements**

Functional tests shall maintain bidirectional traceability where practical.

The primary relationship is:

Requirement

↓

Test Condition

↓

Test Case

↓

Test Execution

↓

Evidence

↓

Result

↓

Defect, if applicable

The reverse relationship shall also be maintained:

Test Case

↓

Requirement(s)

This permits the project to determine:

- Which requirements are tested.

- Which requirements lack adequate coverage.

- Which tests verify a requirement.

- Which requirements failed.

- Which requirements are affected by defects.

- Which previously verified requirements require re-verification.

Detailed requirements traceability and coverage analysis are specified in **Volume VI — Test Management & Traceability**.

**1.18 Requirement Verification State**

Functional verification shall use the current state of evidence.

A requirement shall not remain permanently "verified" merely because a previous test execution passed.

A requirement may require re-verification when implementation, architecture, dependencies, security controls, configuration, or platform behavior changes.

Conceptually:

Requirement

↓

Verified

↓

Relevant Change

↓

Impact Analysis

↓

Re-Test Required

↓

Current Evidence

↓

Verified / Failed / Blocked / Pending

The exact project status vocabulary shall remain consistent with the project's established RTM and test-management rules.

Historical evidence shall be retained where required, but historical verification shall not automatically substitute for current verification.

**1.19 Functional Coverage**

Functional coverage shall be evaluated at multiple levels.

At minimum, coverage analysis should consider:

- Requirement coverage.

- Test-condition coverage.

- Test-case coverage.

- Positive-path coverage.

- Negative-path coverage.

- Boundary coverage.

- State-transition coverage.

- Workflow coverage.

- Interaction coverage.

- Risk coverage.

- Regression coverage.

A high test pass rate shall not be interpreted as complete functional coverage.

For example:

High Pass Rate

\+

Low Requirement Coverage

=

Insufficient Evidence

Likewise:

Low Defect Count

\+

Low Test Execution

=

Unknown Quality

Coverage metrics shall therefore be interpreted together with execution volume, risk, requirement coverage, defect information, and evidence quality.

**1.20 Functional Test Environment**

Functional tests shall execute against controlled and identified environments appropriate to the functionality being tested.

The test record shall identify applicable:

- Application build.

- Source revision.

- Android version.

- Device or emulator.

- Application configuration.

- Required permissions.

- Network state.

- Test data.

- Test-suite version.

Environment selection shall reflect the requirements and risk of the feature.

Device and platform compatibility testing is specified in greater detail in **Volume III — Non-Functional Test Specification**, while security-specific environment requirements are addressed in **Volume IV — Security Test Specification**.

**1.21 Functional Test Data**

Functional test data shall be controlled sufficiently to make test execution repeatable.

Test data shall include, where applicable:

- Test accounts.

- Authentication data.

- Protected applications.

- Profiles.

- Schedules.

- Automation rules.

- Notification conditions.

- Recovery conditions.

- Configuration values.

- Boundary values.

- Invalid values.

Sensitive or security-relevant test data shall be handled according to the project's security and test-data requirements.

Production data shall not be used for functional testing unless explicitly authorized and appropriately protected.

**1.22 Functional Test Independence**

Where practical, test design shall avoid unnecessary dependencies between test cases.

A test case should establish its required state rather than relying on a previous test to leave the environment in a specific condition.

When dependency is unavoidable, the dependency shall be explicitly documented.

Independent tests improve:

- Reproducibility.

- Failure diagnosis.

- Parallel execution.

- Automation.

- Regression selection.

- Defect isolation.

**1.23 Exploratory Testing**

Scripted functional tests shall be supplemented by exploratory testing where risk or complexity warrants it.

Exploratory testing may be used to investigate:

- Unexpected behavior.

- Boundary conditions not fully anticipated by scripted tests.

- Complex workflows.

- New functionality.

- Areas with repeated defects.

- Interactions between features.

- Unusual user sequences.

Exploratory findings shall be converted into formal test cases where the behavior represents a repeatable and important verification condition.

Exploratory testing does not replace requirements-based verification.

**1.24 Functional Test Review**

Functional test cases shall be reviewed before being relied upon for formal verification.

Review shall consider:

- Requirement correctness.

- Requirement traceability.

- Test objective clarity.

- Preconditions.

- Test data.

- Test steps.

- Expected results.

- Negative coverage.

- Boundary coverage.

- State transitions.

- Risk priority.

- Automation suitability.

- Evidence requirements.

- Regression impact.

Test cases shall be revised when requirements or relevant design information changes.

**1.25 Entry Criteria for Functional Test Design**

Functional test design for a feature should not proceed to formal approval until sufficient information exists to create objective tests.

Applicable inputs include:

- Approved or baselined functional requirements.

- Relevant acceptance criteria.

- Applicable architecture and design information.

- Relevant security constraints.

- Known dependencies.

- Applicable implementation-phase information.

- Identified risks.

Where required information is missing or contradictory, the uncertainty shall be identified rather than concealed through assumptions.

**1.26 Exit Criteria for Functional Test Design**

Functional test design for a feature may be considered complete when:

1.  Applicable requirements have been identified.

2.  Testable conditions have been derived.

3.  Appropriate positive and negative conditions have been considered.

4.  Applicable boundaries have been identified.

5.  Relevant state transitions have been considered.

6.  Relevant functional interactions have been identified.

7.  Test cases provide objective expected results.

8.  Test cases are traceable to requirements.

9.  Risk-based priorities have been assigned where applicable.

10. Required evidence has been identified.

11. Required regression considerations have been identified.

12. Test cases have completed the applicable review process.

Completion of test design does not imply that the feature itself has passed verification.

**1.27 Relationship to Subsequent Functional Test Sections**

The methodology established by this section shall be applied consistently throughout Volume II.

The subsequent sections shall provide detailed functional testing for their respective application areas:

| **Volume II Section** | **Functional Area**       |
|-----------------------|---------------------------|
| Section 2             | Authentication            |
| Section 3             | App Lock                  |
| Section 4             | Vault                     |
| Section 5             | Protected Applications    |
| Section 6             | Profiles                  |
| Section 7             | Scheduling                |
| Section 8             | Automation Rules          |
| Section 9             | Notifications             |
| Section 10            | Settings                  |
| Section 11            | Recovery Features         |
| Section 12            | Administrative Functions  |
| Section 13            | Future Expansion Features |

Each subsequent section shall apply the design methodology established here while deriving its actual test conditions from the applicable authoritative requirements and project documentation.

**1.28 Traceability to Other Test Specification Volumes**

Functional testing is part of a larger verification system.

The relationship between the volumes is:

Volume I

Test Strategy & Governance

↓

Volume II

Functional Test Specification

↓

Volume III

Non-Functional Test Specification

↓

Volume IV

Security Test Specification

↓

Volume V

Integration, System & Release Testing

↓

Volume VI

Test Management & Traceability

These volumes are complementary.

Functional tests shall not duplicate detailed security, non-functional, integration, or release testing where those concerns are formally specified elsewhere. Instead, applicable relationships and dependencies shall be identified and cross-referenced.

**1.29 Implementation Strategy Alignment**

Functional testing shall align with the project's implementation lifecycle.

Testing shall occur continuously throughout implementation rather than being deferred until final release.

Functional verification should therefore be incorporated into the applicable implementation phases:

1.  Foundation.

2.  Core Security Platform.

3.  Core Application Features.

4.  Automation & Intelligent Operations.

5.  Production Hardening.

6.  Security Hardening.

7.  Release Readiness.

The exact tests executed at each phase shall be determined by implemented functionality, requirements, risk, dependencies, and phase-gate criteria.

A function shall not be considered fully verified merely because its implementation phase has been completed.

**1.30 Section Summary**

Functional Test Design establishes the common methodology for verifying the functional behavior of the Android App Lock application.

The methodology requires:

- Requirement-based test design.

- Positive and negative testing.

- Boundary and equivalence analysis.

- State-transition verification.

- Workflow testing.

- Functional interaction testing.

- Risk-based prioritization.

- Controlled test state and data.

- Objective expected results.

- Evidence-based verification.

- Continuous regression.

- Bidirectional traceability.

- Re-verification following relevant change.

- Appropriate use of automation.

- Coverage analysis beyond simple pass rate.

The detailed functional test specifications that follow shall apply these principles to each functional area of the application.
