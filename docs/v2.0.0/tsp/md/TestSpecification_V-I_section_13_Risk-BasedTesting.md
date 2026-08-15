**Test Specification**

**Volume I — Test Strategy & Governance**

**Section 13 — Risk-Based Testing**

**13.1 Purpose**

This section defines how testing effort is prioritized according to the risks associated with the Android App Lock application.

Risk-based testing ensures that testing resources are concentrated on functionality, security controls, quality attributes, and system behaviors where failure would have the greatest impact.

Risk shall influence:

- Test depth

- Test breadth

- Test priority

- Regression scope

- Test automation priority

- Environment coverage

- Security testing

- Phase-gate decisions

- Release qualification

**13.2 Risk-Based Testing Objectives**

Risk-based testing shall:

- Identify areas requiring increased verification.

- Prioritize testing according to potential impact.

- Ensure security-critical functionality receives appropriate coverage.

- Direct limited testing resources toward meaningful risks.

- Establish a rational basis for regression scope.

- Support phase and release decisions.

- Identify residual testing risk.

- Prevent test coverage from being determined solely by feature count.

**13.3 Risk Definition**

For testing purposes, risk represents the possibility that a system condition may produce an undesirable outcome.

Risk assessment shall consider both:

- **Likelihood** — how likely the failure is to occur.

- **Impact** — the consequence if the failure occurs.

A simplified risk model is:

Risk = Likelihood × Impact

The project may use a more detailed risk model when appropriate.

**13.4 Risk Categories**

Testing risk may originate from:

**Functional Risk**

Failure to satisfy an SRS functional requirement.

**Security Risk**

Failure that could compromise confidentiality, integrity, authentication, authorization, or application protection.

**Reliability Risk**

Failure that causes instability, unexpected termination, state corruption, or inability to recover.

**Performance Risk**

Failure to meet response-time, resource, or operational performance requirements.

**Compatibility Risk**

Failure caused by differences between supported Android versions, devices, configurations, or environments.

**Data Risk**

Loss, corruption, unauthorized modification, or improper handling of application data.

**Platform Risk**

Unexpected behavior resulting from Android platform restrictions, lifecycle behavior, permissions, or APIs.

**Operational Risk**

Failure of installation, configuration, upgrade, recovery, monitoring, or deployment processes.

**Maintainability Risk**

Changes becoming difficult to implement, test, diagnose, or safely maintain.

**13.5 Risk Identification**

Risks shall be identified from available project artifacts and activities, including:

- SRS requirements

- NFR requirements

- Threat Model

- Secure Coding Standard

- Technical Architecture Specification

- Software Design Specification

- Implementation Strategy

- Architecture decisions

- Known defects

- Dependency changes

- Android platform constraints

- Test results

- Production observations

Risk identification shall continue throughout the project.

**13.6 Risk Assessment**

Each significant testing risk should be evaluated according to:

- Likelihood

- Impact

- Detectability, where useful

- Exposure

- Affected requirements

- Affected components

- Existing controls

- Existing test coverage

Risk assessment shall be revisited when material project changes occur.

**13.7 Risk Classification**

A practical classification is:

| **Risk Level** | **Testing Treatment** |
|----|----|
| Critical | Extensive verification; normally release-blocking if unresolved |
| High | Increased test depth and regression coverage |
| Medium | Standard planned verification |
| Low | Proportionate verification |

The classification shall reflect actual project risk rather than test convenience.

**13.8 Critical-Risk Testing**

Critical-risk functionality shall receive the highest verification priority.

Examples may include:

- Authentication

- Authorization

- Application-lock enforcement

- Cryptographic operations

- Android Keystore usage

- Protected data

- Security boundaries

- Recovery of security state

Critical-risk areas should receive appropriate combinations of:

- Functional testing

- Negative testing

- Boundary testing

- Integration testing

- Security testing

- Regression testing

- Failure/recovery testing

**13.9 High-Risk Testing**

High-risk functionality shall receive increased verification depth.

Testing may include:

- Additional test conditions

- Negative cases

- Boundary conditions

- Multiple environments

- Extended regression

- Failure recovery

- Compatibility testing

- Security-focused testing

High-risk functionality shall not rely exclusively on a single successful functional test.

**13.10 Medium-Risk Testing**

Medium-risk functionality shall receive normal planned test coverage appropriate to its requirements.

Testing should establish:

- Expected functionality

- Important boundary conditions

- Relevant error handling

- Integration behavior

- Applicable regression coverage

**13.11 Low-Risk Testing**

Low-risk functionality shall receive proportionate testing.

Low risk does not mean untested.

Testing may emphasize:

- Primary expected behavior

- Basic error handling

- Integration with affected components

- Regression where applicable

**13.12 Security Risk Priority**

Security risk shall receive special consideration because a low-frequency failure may still have severe consequences.

For example, a vulnerability that is difficult to reproduce may remain high risk if successful exploitation would compromise protected applications or sensitive data.

Security testing shall therefore consider impact in addition to likelihood.

Detailed security verification is defined in **Volume IV — Security Test Specification**.

**13.13 Risk-Based Test Prioritization**

When testing resources or execution time are limited, tests shall generally be prioritized according to:

1.  Critical security risks

2.  Critical functional risks

3.  High-impact system risks

4.  High-risk changes

5.  Release-critical workflows

6.  Previously failed or unstable areas

7.  Medium-risk functionality

8.  Low-risk functionality

This ordering may be adjusted based on current project conditions.

**13.14 Risk-Based Test Design**

Risk shall influence test design.

Higher-risk functionality should receive broader combinations of:

- Positive tests

- Negative tests

- Boundary tests

- Invalid-input tests

- State-transition tests

- Failure tests

- Recovery tests

- Concurrency tests

- Compatibility tests

- Security-abuse tests

The exact techniques shall depend on the applicable requirement and risk.

**13.15 Risk-Based Regression**

Risk shall determine regression depth after changes.

A change affecting a low-risk isolated component may require targeted regression.

A change affecting authentication, the lock engine, encryption, protected data, or shared infrastructure may require broad regression.

Change

│

▼

Impact Analysis

│

▼

Risk Assessment

│

▼

Regression Scope

│

├── Low Risk → Targeted

├── Medium → Component / Integration

├── High → Broad Regression

└── Critical → Comprehensive Regression

**13.16 Risk-Based Environment Selection**

Risk shall influence environment selection.

High-risk platform-dependent functionality may require testing across:

- Supported Android versions

- Supported device classes

- Relevant manufacturer configurations

- Different storage states

- Network conditions

- Battery states

- Permission states

A single environment shall not automatically be considered sufficient for high-risk platform behavior.

**13.17 Risk-Based Automation**

Automation priority should consider risk and repeatability.

Automation should be prioritized for:

- High-frequency tests

- Critical security tests

- Critical functional tests

- Regression tests

- Defect-derived tests

- Stable deterministic tests

- Tests required during CI/CD

Risk alone does not require automation when the test is inherently unsuitable for automation.

**13.18 Risk-Based Security Testing**

Security testing shall prioritize attack surfaces according to potential consequence.

Areas receiving increased attention may include:

- Authentication

- Authorization

- Application-lock enforcement

- Accessibility

- Overlays

- Intents

- Secure storage

- Backup

- Network communication

- Cryptographic operations

- Runtime integrity

Detailed security test specifications are defined in Volume IV.

**13.19 Risk-Based Non-Functional Testing**

Risk shall also influence non-functional testing.

Examples:

| **Risk**        | **Potential Testing**                       |
|-----------------|---------------------------------------------|
| Performance     | Load and response-time testing              |
| Reliability     | Failure and recovery testing                |
| Battery         | Long-duration consumption testing           |
| Memory          | Memory-pressure testing                     |
| Compatibility   | Multi-device/platform testing               |
| Availability    | Service/process recovery testing            |
| Maintainability | Static analysis and structural verification |

Detailed non-functional testing is defined in Volume III.

**13.20 Risk-Based Integration Testing**

Integration testing shall prioritize interfaces where failures could propagate across multiple components.

Examples include:

- Authentication → Session Management

- Session Management → Lock Engine

- Lock Engine → Protected Applications

- Scheduling → Automation

- Automation → Notifications

- Storage → Recovery

- Configuration → Core Services

Shared infrastructure and security boundaries should receive increased attention.

**13.21 Risk-Based End-to-End Testing**

End-to-end testing shall prioritize workflows with significant user, security, or system consequences.

Examples include:

Configure Protection

↓

Select Application

↓

Application Becomes Protected

↓

Lock Triggered

↓

Authentication Required

↓

Authentication Succeeds

↓

Application Access Granted

Critical workflows should be represented in end-to-end regression coverage.

**13.22 Risk-Based Defect Handling**

Defect severity and risk shall influence:

- Investigation depth

- Corrective priority

- Retest scope

- Regression scope

- Release impact

- Required evidence

Critical defects shall receive substantially greater scrutiny than low-impact defects.

Detailed defect classification and workflow are defined in **Volume VI**.

**13.23 Risk Reassessment**

Risk shall be reassessed when material changes occur.

Triggers include:

- New requirements

- Removed requirements

- Architecture changes

- Security changes

- Dependency updates

- Android platform changes

- New defects

- Repeated failures

- New threats

- Significant test results

- Changes to supported devices

- Changes to deployment conditions

A previously low-risk area may become high-risk after a change.

**13.24 Risk and Requirement Changes**

Adding or removing requirements shall trigger risk reassessment.

For an added requirement:

New Requirement

↓

Risk Identification

↓

Risk Assessment

↓

Test Requirements

↓

Test Design

For a removed requirement:

Removed Requirement

↓

Affected Tests Identified

↓

Affected Risks Identified

↓

Test/Traceability Update

Tests shall not remain active solely because they existed previously when the underlying requirement has legitimately been removed.

**13.25 Risk and Previously Verified Requirements**

Previously verified requirements shall be reassessed when their implementation or dependencies change.

A requirement may return to a regression-required state when:

- Its implementation changes.

- A dependency changes.

- A related security control changes.

- A defect affects its behavior.

- Architecture changes affect its execution path.

Risk assessment shall help determine the appropriate regression depth.

**13.26 Residual Testing Risk**

Testing cannot eliminate all possible risk.

Residual risk may remain because of:

- Unsupported environments

- Unobservable conditions

- Limited test data

- Unknown defects

- Platform variability

- Unverified combinations

- Resource constraints

Residual risk shall be documented when it materially affects testing or release decisions.

**13.27 Risk Acceptance**

Risk acceptance shall not be confused with test success.

A risk may be accepted when:

- The risk is understood.

- Potential impact is documented.

- Mitigations have been evaluated.

- The remaining exposure is considered acceptable.

- The decision is explicitly recorded.

An accepted risk does not change the actual test result.

**13.28 Risk-Based Phase Gates**

Risk assessment shall contribute to every phase-gate decision.

Before advancing a phase, the project should evaluate:

- Critical unresolved risks

- High-risk unverified requirements

- Security risks

- Open high-severity defects

- Regression status

- Test coverage

- Residual risk

- Required mitigations

A phase should not be considered adequately verified solely because a numerical test-pass target has been achieved.

**13.29 Risk-Based Release Qualification**

Release testing shall prioritize risks that could materially affect users or security.

Release qualification should emphasize:

- Security boundaries

- Core application locking

- Authentication

- Protected applications

- Data integrity

- Recovery

- Compatibility

- Performance

- Reliability

- Critical user workflows

Detailed release qualification is defined in **Volume V**.

**13.30 Risk Traceability**

Each significant testing risk should be traceable to applicable:

- Requirements

- Threats

- Components

- Test cases

- Defects

- Mitigations

- Verification results

Detailed risk traceability is defined in **Volume VI — Section 7: Risk Traceability**.

**13.31 Risk Coverage**

Risk coverage shall evaluate whether significant identified risks have corresponding verification activities.

A simplified model is:

Risk

│

├── Requirement

│

├── Test Case

│

├── Test Execution

│

└── Result

A significant risk without appropriate verification shall be treated as a testing gap.

**13.32 Risk-Based Testing Metrics**

Useful measures may include:

- High-risk requirements with passing tests

- Critical-risk tests executed

- High-risk tests executed

- High-risk defects discovered

- Risk areas without test coverage

- Regression coverage of high-risk components

- Security-risk verification status

- Residual testing risks

Metrics shall support decision-making rather than become artificial performance targets.

**13.33 Risk-Based Testing Review**

Risk-based testing shall be reviewed periodically and at major project transitions.

Review points should include:

- Completion of major implementation phases

- Architecture changes

- Major security changes

- Major dependency changes

- Release candidates

- Significant defect discoveries

- Changes to supported Android environments

The review shall determine whether the current test strategy remains aligned with current project risks.

**13.34 Summary**

Risk-based testing ensures that verification effort reflects the consequences of failure rather than simply the number of requirements or test cases.

The fundamental process is:

Identify Risk

↓

Assess Risk

↓

Prioritize

↓

Design Tests

↓

Execute Tests

↓

Evaluate Results

↓

Reassess Risk

Risk shall remain dynamic throughout development. Changes to requirements, architecture, implementation, dependencies, security controls, platform behavior, or discovered defects may change the testing priority of an area.

Risk-based testing therefore provides the decision framework for determining **where testing effort should be deepest, where regression should be broadest, and where unresolved verification presents the greatest threat to project and release objectives**.
