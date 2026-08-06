**Test Specification**

**Volume I — Test Strategy & Governance**

**Section 12 — Defect Management**

**12.1 Purpose**

This section defines the governance and management of defects identified throughout the testing lifecycle.

Defect management establishes a controlled process for identifying, recording, classifying, investigating, resolving, verifying, and closing defects while maintaining traceability to requirements, risks, and test results.

Defect management shall support continuous verification throughout the project rather than treating defects as a final-stage testing concern.

**12.2 Defect Management Objectives**

The defect management process shall:

- Ensure defects are consistently recorded.

- Establish a common classification system.

- Provide sufficient information to reproduce defects.

- Prioritize defects according to risk and impact.

- Maintain traceability to affected requirements and tests.

- Ensure corrective changes are verified.

- Prevent resolved defects from silently returning.

- Identify systemic or recurring problems.

- Support phase-gate and release decisions.

- Preserve an accurate historical record of defect activity.

**12.3 Defect Definition**

For this specification, a **defect** is an observed condition in which the implemented system does not satisfy an applicable:

- Functional requirement

- Non-functional requirement

- Security requirement

- Design constraint

- Interface contract

- Acceptance criterion

- Platform compatibility requirement

- Test expectation derived from an approved requirement

A test failure does not automatically establish an application defect. The failure shall first be evaluated to determine whether it originated from the application, test, environment, configuration, data, or another source.

**12.4 Defect Sources**

Defects may be identified through:

- Unit testing

- Integration testing

- Functional testing

- Non-functional testing

- Security testing

- System testing

- End-to-end testing

- Regression testing

- Manual exploratory testing

- Static analysis

- Code review

- Dependency analysis

- Compatibility testing

- Performance testing

- Production observation

- User acceptance testing

The discovery source should be recorded.

**12.5 Defect Lifecycle**

The standard defect lifecycle shall be:

Detected

│

▼

Recorded

│

▼

Triaged

│

▼

Investigated

│

├──────────────► Rejected / Duplicate

│

▼

Confirmed

│

▼

Corrected

│

▼

Retested

│

▼

Regression Tested

│

├──────────────► Failed → Reopened

│

▼

Verified

│

▼

Closed

A defect may move backward in the lifecycle when new evidence requires reconsideration.

**12.6 Defect States**

The following states shall be used as applicable:

| **State** | **Description** |
|----|----|
| Detected | Potential defect has been observed |
| Recorded | Defect has been formally documented |
| Triaged | Impact and priority have been evaluated |
| Investigating | Cause or scope is being determined |
| Confirmed | Defect has been established |
| Rejected | Evidence does not establish a defect |
| Duplicate | Existing defect already represents the issue |
| Deferred | Correction intentionally postponed |
| Corrected | Corrective implementation completed |
| Retest Required | Correction requires verification |
| Regression Required | Related functionality requires regression testing |
| Verified | Correction has been successfully verified |
| Closed | Defect lifecycle is complete |
| Reopened | Previously resolved defect has returned or remains unresolved |

**12.7 Defect Record**

A defect record shall contain enough information to support investigation and verification.

At minimum, the record should contain:

- Defect identifier

- Short title

- Description

- Discovery source

- Date discovered

- Application version/build

- Environment

- Preconditions

- Reproduction steps

- Expected behavior

- Actual behavior

- Severity

- Priority

- Affected component

- Related requirement

- Related test case

- Evidence

- Current status

Additional information shall be recorded when required by the defect's risk or complexity.

**12.8 Reproduction Requirements**

Where practical, defects shall be reproducible before being confirmed.

A reproduction procedure should identify:

1.  Environment

2.  Build

3.  Test data

4.  Preconditions

5.  Actions performed

6.  Expected result

7.  Actual result

Intermittent defects shall not be discarded solely because they cannot be reproduced immediately.

**12.9 Defect Triage**

Triage shall determine:

- Whether the reported behavior represents a defect.

- Whether the defect can be reproduced.

- What requirements are affected.

- What components may be affected.

- Whether security is involved.

- Severity.

- Priority.

- Required corrective action.

- Required verification and regression scope.

Triage shall be proportional to the potential impact of the defect.

**12.10 Defect Severity**

Severity describes the **impact** of a defect.

**Critical**

A defect that can cause severe security, integrity, availability, or core-function failure.

Examples:

- Authentication bypass

- Unauthorized access to protected applications

- Critical loss of protected data

- Critical cryptographic failure

- Complete failure of the application-lock security boundary

**High**

A significant failure that materially affects security, functionality, reliability, or supported operation.

Examples:

- Significant authorization failure

- Major lock-enforcement failure

- Significant data-integrity problem

- Major recovery failure

- Major supported-device compatibility failure

**Medium**

A meaningful defect with limited scope or a viable workaround.

Examples:

- Important functional failure

- Scheduling malfunction

- Recoverable reliability issue

- Significant but non-critical usability problem

**Low**

A minor defect with limited user or system impact.

Examples:

- Minor UI problem

- Cosmetic issue

- Minor usability issue

Severity shall reflect technical impact, not implementation effort.

**12.11 Defect Priority**

Priority determines how urgently a defect should be addressed.

Priority shall consider:

- Severity

- Security impact

- Release timing

- User impact

- Frequency

- Availability of workarounds

- Dependencies

- Risk of delay

Severity and priority shall remain separate attributes.

**12.12 Security Defects**

Defects involving security controls shall receive additional evaluation.

Security-related defects may affect:

- Authentication

- Authorization

- Session management

- Cryptography

- Android Keystore

- Secure storage

- Application locking

- Accessibility security

- Overlay protection

- Intent handling

- Backup protection

- Network security

- Runtime integrity

- Tamper detection

- Anti-debugging

Security defects shall be traceable to the applicable security requirement, threat, or security test where applicable.

**12.13 Critical Security Defects**

Critical security defects shall normally prevent release until:

- The defect is corrected.

- The correction is retested.

- Appropriate security regression testing is completed.

- Relevant evidence is recorded.

- The associated requirement and risk traceability are updated.

Any exception shall require explicit risk acceptance.

**12.14 Defect Impact Analysis**

Before corrective implementation, significant defects shall undergo impact analysis.

The analysis should consider:

- Affected component

- Dependent components

- Shared services

- Security boundaries

- Data structures

- User workflows

- Android platform behavior

- Related requirements

- Related test cases

- Existing defects

- Regression scope

The purpose is to prevent a local correction from creating an unrecognized system-level regression.

**12.15 Root-Cause Analysis**

Root-cause analysis shall be proportional to defect severity, recurrence, and risk.

Potential causes include:

- Requirement defect

- Architecture defect

- Design defect

- Implementation defect

- Configuration defect

- Dependency defect

- Test-data defect

- Test-environment defect

- Test-coverage deficiency

- Process deficiency

For significant or recurring defects, the project should determine why the defect occurred and why it was not detected earlier.

**12.16 Corrective Implementation**

A corrective change shall address the underlying defect while preserving applicable requirements and security controls.

Before implementation is considered complete, the change should be evaluated for:

- Functional impact

- Security impact

- Data impact

- Compatibility impact

- Performance impact

- Regression impact

Implementation completion does **not** constitute defect resolution.

**12.17 Corrective Verification**

Every corrected defect shall undergo appropriate verification.

The original failed test should normally be used as the first verification test.

The verification process shall establish that:

- The original failure condition can no longer produce the defect.

- The expected behavior is achieved.

- No immediately related behavior has been broken.

**12.18 Regression After Correction**

Corrective verification shall be followed by appropriate regression testing.

Regression scope shall be determined by impact analysis and may include:

- Directly affected functionality

- Dependent components

- Security boundaries

- Related workflows

- Shared services

- Database operations

- Platform interactions

Critical and high-risk defects shall receive broader regression testing.

**12.19 Defect Reopening**

A defect shall be reopened when:

- The original defect returns.

- The correction is incomplete.

- Regression testing reveals a related failure.

- The original failure remains under another condition.

- The defect was closed prematurely.

- New evidence demonstrates that the underlying problem remains.

Historical execution and closure information shall be preserved.

**12.20 Rejected Defects**

A reported issue may be rejected when evidence establishes that:

- The behavior satisfies the requirement.

- The behavior is explicitly intended.

- The issue is outside the product scope.

- The reported condition is not sufficiently supported.

The reason for rejection shall be documented.

The defect record should remain available for historical traceability.

**12.21 Duplicate Defects**

A defect shall be marked as duplicate when another defect already represents the same underlying problem.

The duplicate record shall reference the controlling defect.

The duplicate's discovery information shall not be discarded because it may provide additional evidence about the frequency or scope of the problem.

**12.22 Deferred Defects**

Defects may be deferred when correction is intentionally postponed.

A deferred defect shall retain:

- Reason for deferral

- Risk

- Affected requirements

- Release impact

- Planned disposition

- Required mitigation, where applicable

A deferred defect remains unresolved.

**12.23 Accepted Risk**

A defect shall not be reclassified as resolved merely because the project chooses to accept its risk.

When an unresolved defect is accepted as residual risk, the decision shall document:

- Defect

- Risk

- Impact

- Likelihood

- Mitigation

- Rationale

- Decision authority

- Applicable release

- Reassessment conditions

Risk acceptance shall remain distinguishable from successful defect correction.

**12.24 Defect Closure**

A defect may be closed when:

- Corrective implementation is complete.

- The original failure has been successfully retested.

- Required regression testing has passed.

- Required security verification has passed.

- Required evidence exists.

- Related traceability has been updated.

- No unresolved condition prevents closure.

**12.25 Defect and Requirements Verification**

Defects shall be evaluated for their effect on requirement verification.

A previously verified requirement may need to return to a non-verified state when a defect demonstrates that its current implementation no longer satisfies the requirement.

Requirement

│

▼

Verified

│

▼

Defect Discovered

│

▼

Impact Assessment

│

├── No impact ──────► Remains Verified

│

└── Impacted

│

▼

Regression Required

│

┌────┴────┐

▼ ▼

PASS FAIL

│ │

▼ ▼

Verified Verification

Failed

This supports continuous requirement verification throughout the project.

**12.26 Defect Escape**

A defect escape occurs when a defect reaches a later testing stage without being detected at an earlier stage where detection would reasonably have been expected.

Examples include:

- Unit-level defect discovered during integration testing.

- Integration defect discovered during system testing.

- System defect discovered during release testing.

- Release defect discovered after deployment.

Significant escapes shall be analyzed for improvements to requirements, implementation, or testing.

**12.27 Recurring Defects**

Recurring defects shall receive additional investigation.

Recurrence may indicate:

- Architectural weakness

- Inadequate abstraction

- Insufficient regression coverage

- Poor requirements

- Fragile implementation

- Test-environment problems

- Inadequate automation

Where appropriate, a permanent regression test shall be added.

**12.28 Defect Trends**

Defect trends may be monitored using:

- Defects by severity

- Defects by component

- Defects by requirement

- Defects by discovery source

- Open defects

- Closed defects

- Reopened defects

- Escaped defects

- Defect aging

- Security defects

- Regression defects

Metrics shall be used to identify project risk and process weaknesses rather than to create incentives for artificially reducing defect counts.

**12.29 Phase-Gate Defect Review**

Before a phase is approved, open defects shall be reviewed against the applicable phase exit criteria.

The review shall consider:

- Critical defects

- High-severity defects

- Security defects

- Regression failures

- Deferred defects

- Reopened defects

- Residual risks

- Requirement verification status

Phase completion shall not be represented as full verification when mandatory defect-related verification remains unresolved.

**12.30 Release Blocking**

The following conditions should normally block release:

- Unresolved Critical security defects

- Authentication bypass

- Unauthorized access to protected applications

- Critical lock-enforcement failures

- Critical data-integrity failures

- Critical cryptographic failures

- Other defects explicitly designated as release-blocking

Exceptions require documented risk acceptance.

**12.31 Defect Management Records**

Defect records shall provide an auditable history of:

- Discovery

- Classification

- Investigation

- Corrective implementation

- Retesting

- Regression

- Verification

- Closure or disposition

Historical results shall not be deleted merely because the defect has been resolved.

**12.32 Defect Management and Test Traceability**

Defects shall maintain relationships with relevant test artifacts:

Requirement

│

▼

Risk

│

▼

Test Case

│

▼

Test Execution

│

▼

Defect

│

▼

Correction

│

▼

Retest

│

▼

Regression

│

▼

Verification

Detailed operational traceability is defined further in **Volume VI — Test Management & Traceability**.

**12.33 Relationship to Other Test Specification Volumes**

Defect management in this section establishes the **project-wide governance process**.

Detailed treatment is distributed throughout the Test Specification:

- **Volume II** — Functional defects and functional verification

- **Volume III** — Non-functional defects and quality-attribute verification

- **Volume IV** — Security defects and security verification

- **Volume V** — Integration, system, regression, and release defects

- **Volume VI** — Defect classification, workflow, execution records, and traceability

This separation prevents Volume I from duplicating the detailed testing procedures defined elsewhere.

**12.34 Summary**

Defect management is a continuous verification activity.

The fundamental lifecycle is:

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

A defect is **not resolved simply because code was changed**. Resolution requires appropriate verification evidence.

Defects shall remain traceable to requirements, risks, tests, corrective changes, and verification results. Previously verified requirements shall be reassessed when defects or corrective changes may affect their validity.

This establishes the governance foundation for the detailed defect classification, workflow, and traceability processes defined later in **Volume VI**.
