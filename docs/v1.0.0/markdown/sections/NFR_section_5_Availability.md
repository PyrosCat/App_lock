# Non-Functional Requirements

## Version 1.0.0

## 5. Availability

### NFR-AVAIL-001 - Application Availability

The application shall achieve at least 99 percent successful availability across 100 defined supported-phone test operations.

Acceptance criteria:

- The 100 operations comprise 40 valid cold or warm starts, 30 openings of App Lock, and 30 accesses to retained local functions across the supported reference-phone set.
- Device hardware failure and operating-system behavior outside the supported boundary are identified separately.
- No deployed-population or remote-telemetry claim is required.

Verification: Measurement.

### NFR-AVAIL-002 - Core Protection Availability

PIN authentication and the core protection path shall remain available whenever required capabilities are available under supported conditions.

Acceptance criteria:

- Failure of an optional function does not prevent core PIN protection.
- Failure of Usage Access or lock presentation produces a truthful Protection interrupted or Action required state.

Verification: Availability and fault-injection test.

### NFR-AVAIL-003 - Startup Availability

The application shall initialize successfully in at least 99 percent of 100 normal supported startup cases.

Acceptance criteria:

- The 100 cases are distributed across the supported reference-phone set and include clean start, ordinary restart, and post-process-recreation start with valid local data.
- Startup includes local data, credential, policy, Usage Access, and service checks.
- A failed initialization produces sufficient privacy-safe local context for diagnosis.

Verification: Repeated startup test and measurement.

### NFR-AVAIL-004 - Service Continuity

Core protection services shall remain continuous through normal Android phone lifecycle events.

Acceptance criteria:

- Backgrounding, foreground transitions, screen off and on, sleep and wake, rotation, and process recreation are covered.
- Continuity is not claimed when Android has revoked or blocked a required capability.

Verification: Lifecycle test.

### NFR-AVAIL-005 - Device Restart Recovery Availability

The application shall restore its core protection path within 30 seconds after Android permits execution following restart.

Acceptance criteria:

- No pre-restart session remains valid.
- Protected selections and global policy load without manual re-entry.
- An unavailable required capability produces Action required guidance rather than false availability.

Verification: Restart test and measurement.

### NFR-AVAIL-006 - Update Availability

Supported in-place version 1.x updates shall preserve valid credentials, protected-application selections, and retained settings with minimal disruption.

Acceptance criteria:

- The application starts into a verified state after a successful migration.
- A failed update or migration does not leave partially committed local data in normal use.
- Cross-device transfer, backup restore, and deferred data formats are excluded.

Verification: Upgrade and migration test.

### NFR-AVAIL-007 - Graceful Degradation Availability

Failure of a noncritical function shall preserve the safest available core service.

Acceptance criteria:

- Optional biometric loss falls back to PIN.
- Diagnostics and cleanup can be deferred without granting access.
- Required-capability loss is reported as Protection interrupted or Action required.

Verification: Fault-injection test.

### NFR-AVAIL-008 - Recovery Availability

Recoverable core failures shall provide a usable path back to verified normal operation.

Acceptance criteria:

- Recovery does not introduce data inconsistency or extend an invalid session.
- The final state is verified and communicated.
- Unrecoverable local-data failure provides destructive reset rather than an unavailable backup path.

Verification: Recovery test.

### NFR-AVAIL-009 - Offline Availability

All retained version 1.0.0 functions shall remain available without network connectivity.

Acceptance criteria:

- Airplane mode does not prevent PIN authentication, protection detection, settings, help stored in the application, or local diagnostics.
- Network loss does not create retries, warnings, or a Degraded protection state.

Verification: Offline functional test.

### NFR-AVAIL-010 - Availability Monitoring

Current availability shall be measurable through on-device protection health and bounded test evidence.

Acceptance criteria:

- Core availability failures create privacy-safe local diagnostic records.
- The authenticated user can view current readiness and recovery guidance.
- Remote monitoring, fleet availability, and historical trend analysis are not required.

Verification: Inspection and test.
