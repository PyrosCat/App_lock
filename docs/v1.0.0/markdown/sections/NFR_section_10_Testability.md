# Non-Functional Requirements

## Version 1.0.0

## 10. Testability

### NFR-TEST-001 - Testable Architecture

Major retained responsibilities shall support isolated testing without requiring the complete phone environment where platform behavior is not the subject of the test.

Acceptance criteria:

- Authentication policy, session rules, protection-state resolution, settings validation, and data migration can be tested independently.
- Android-owned behaviors are covered through controlled integration or device tests.

Verification: Design inspection and test demonstration.

### NFR-TEST-002 - Automated Testing

Automated tests shall cover retained critical behavior and execute before distribution.

Acceptance criteria:

- Unit, integration, migration, regression, and applicable interface tests complete successfully.
- Excluded features create no automated-test obligation.

Verification: Test execution and inspection.

### NFR-TEST-003 - Unit Test Coverage

Security-critical decision logic shall have automated decision and boundary coverage for every retained security rule; no global percentage target is required.

Acceptance criteria:

- Coverage includes session validity, relock, retry delay, protection-state resolution, configuration validation, and supported migration decisions.
- Each rule has at least one permitted case and one denied, expired, invalid, or failure case as applicable.

Verification: Coverage measurement.

### NFR-TEST-004 - Integration Test Coverage

Interactions among authentication, session and relock, application selection, Usage Access detection, lock presentation, required capabilities, local storage, migration, and App Lock notifications shall be tested.

Acceptance criteria:

- Every retained critical boundary has positive, negative, cancellation, interruption, and recovery coverage as applicable.
- Vault, backup, automation, intruder, cloud, notification interception, and Accessibility-service integration are absent.

Verification: Integration test.

### NFR-TEST-005 - Regression Testing

The distributed candidate shall pass the retained critical functional, security, privacy, accessibility, supported-migration, compatibility, and performance regression set.

Acceptance criteria:

- A failed critical regression prevents public distribution until corrected.
- Known limitations are not used to waive unauthorized-access or data-integrity failures.

Verification: Regression test.

### NFR-TEST-006 - Repeatability

Equivalent automated test conditions shall produce consistent results.

Acceptance criteria:

- Nondeterministic tests are identified and corrected before their results are used for acceptance.
- Protection-state and session tests produce deterministic outcomes.

Verification: Repeated test execution.

### NFR-TEST-007 - Test Environment Consistency

Test environments shall represent conventional Android phones on API levels 30 through 35 appropriate to each test.

Acceptance criteria:

- Environment versions and significant limitations are recorded.
- No tablet, foldable, desktop, work-profile, clone, secondary-user, or pre-API-30 environment is required.

Verification: Environment inspection.

### NFR-TEST-008 - Test Data Management

Test data shall be controlled, reproducible, and limited to retained version 1.0.0 data categories.

Acceptance criteria:

- Test datasets cover supported application-list size, valid and invalid configuration, migration, failure, and diagnostic cases.
- Real personal credentials, protected-app activity, or production personal data are not used.

Verification: Inspection.

### NFR-TEST-009 - Continuous Quality Verification

Automated tests, static analysis, security checks, and the retained benchmark set shall provide feedback throughout delivery of version 1.0.0.

Acceptance criteria:

- Critical failures are visible before a distributed artifact is accepted.
- No suite is required for an excluded feature or device class.

Verification: Execution-record inspection.

### NFR-TEST-010 - Defect Verification

A reported defect shall be reproduced where practical, corrected, and retested against the affected retained behavior.

Acceptance criteria:

- Corrective testing verifies the original case and relevant regression cases.
- A defect affecting unauthorized access, credentials, or committed data is not closed solely by code inspection.

Verification: Test and inspection.

### NFR-TEST-011 - Test Coverage Assessment

Verification coverage shall be assessed against version 1.0.0 protection, authentication, privacy, migration, permission, accessibility, compatibility, and recovery risks.

Acceptance criteria:

- Every retained critical risk has identified evidence.
- Explicit exclusions are not treated as untested defects.

Verification: Coverage analysis.
