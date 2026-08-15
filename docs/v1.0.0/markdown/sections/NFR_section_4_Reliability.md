# Non-Functional Requirements

## Version 1.0.0

## 4. Reliability

### NFR-REL-001 - Operational Stability

The application shall maintain its retained core functions during a continuous eight-hour reliability test without unrecoverable failure.

Acceptance criteria:

- Usage Access monitoring, protection presentation, PIN authentication, global relock, and health reporting remain operational.
- No committed credential, protected-application selection, or setting is corrupted.
- Any recoverable failure returns to a verified defined state.

Verification: Endurance test.

### NFR-REL-003 - Mean Time to Recovery

Automatically recoverable core failures shall recover within 30 seconds after Android permits the required work.

Acceptance criteria:

- The measured recovery does not require a device reboot under normal recoverable conditions.
- Recovery includes a successful protection-health verification.
- Failed recovery changes to an Action required state within the same period.

Verification: Fault-injection test and measurement.

### NFR-REL-005 - Data Integrity

Unexpected failure shall not corrupt committed version 1.0.0 local data.

Acceptance criteria:

- Simulated interruptions preserve 100 percent of committed credential, settings, and protected-application records.
- Integrity checks identify no unrecoverable inconsistency after supported recovery cases.
- Deferred Vault and backup data are not part of the test set.

Verification: Fault-injection test and inspection.

### NFR-REL-006 - Transaction Consistency

Every retained local data modification shall complete in a consistent state.

Acceptance criteria:

- No partially committed PIN change, protection selection, or settings change is observable after abnormal termination.
- Database integrity validation succeeds after recovery or the application remains fail-secure.

Verification: Fault-injection and integration test.

### NFR-REL-007 - State Consistency

The application shall preserve a valid state across process termination, phone rotation, supported configuration change, screen off, device restart, and in-place update.

Acceptance criteria:

- No undefined protection, authentication, or migration state is shown.
- Authentication sessions follow the mandatory invalidation rules.
- Display changes do not duplicate actions or bypass confirmation.

Verification: State-transition test.

### NFR-REL-009 - Fault Isolation

A failure in a noncritical retained function shall not unnecessarily disable the independent PIN-based protection path.

Acceptance criteria:

- Optional biometric, help, cache cleanup, or bounded diagnostics failure does not prevent PIN authentication where the core path remains valid.
- Failure of a required detection or presentation capability is contained and reported truthfully.

Verification: Fault-injection test.

### NFR-REL-010 - Consistent Functional Behavior

Equivalent input and state shall produce the same protection, authentication, and recovery result.

Acceptance criteria:

- Repeated state-transition tests produce deterministic outcomes.
- No unexplained difference occurs across equivalent API-level test cases.

Verification: Repeated automated test.

### NFR-REL-011 - Reliability Regression Control

Each compatible release shall preserve or improve the reliability baseline for retained critical behavior.

Acceptance criteria:

- Core crash, recovery, data-integrity, and protection-state results are compared with the approved baseline.
- A regression affecting unauthorized access or committed data is not accepted for distribution.

Verification: Regression test and comparison.

### NFR-REL-012 - Reliability Validation

Reliability evidence shall cover the complete reduced release boundary.

Acceptance criteria:

- Tests include reboot, process termination, permission loss, rapid relaunch, task switching, screen off, low storage, interrupted write, failed migration, and optional biometric loss.
- Coverage is limited to API 30 through 35 phones and retained capability.
- Results identify recovery time and final protection state.

Verification: Test and inspection.
