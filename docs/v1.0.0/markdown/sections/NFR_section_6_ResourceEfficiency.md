# Non-Functional Requirements

## Version 1.0.0

## 6. Resource Efficiency

### NFR-RES-001 - CPU Utilization

The application shall minimize processor use during normal operation.

Acceptance criteria:

- Average idle processor use attributable to App Lock does not exceed 2 percent on the declared reference phone.
- A protection or maintenance peak returns to the defined idle range within five seconds after work completes.
- Continuous high-frequency polling is absent.

Verification: Measurement.

### NFR-RES-002 - Memory Utilization

Memory use shall remain stable throughout retained workflows and extended monitoring.

Acceptance criteria:

- Memory remains within the documented reference-phone limit.
- Endurance testing identifies no measurable leak.
- Memory pressure releases nonessential cache before affecting core protection.

Verification: Measurement and endurance test.

### NFR-RES-003 - Battery Consumption

The application shall minimize battery consumption while maintaining required protection.

Acceptance criteria:

- Background operation contributes no more than 1 percent battery consumption per hour under the declared normal reference conditions.
- Power use remains proportional to actual protection activity.
- Core protection is not disabled to meet the target.

Verification: Measurement.

### NFR-RES-004 - Storage Utilization

Local storage shall grow only with retained configuration and bounded diagnostic needs.

Acceptance criteria:

- Temporary data, cache, and diagnostics do not accumulate without limit.
- Storage use contains no Vault, backup, event-media, or report data.
- Destructive reset removes all App Lock-managed local data.

Verification: Measurement and inspection.

### NFR-RES-005 - Cache Efficiency

Cache shall improve interface performance without becoming required security state.

Acceptance criteria:

- Cache remains within a fixed documented limit.
- Removing cache does not alter credentials, protected selections, settings, or session decisions.
- Obsolete entries are removed automatically.

Verification: Test and inspection.

### NFR-RES-006 - Network Efficiency

Version 1.0.0 shall produce no routine application network traffic because it includes no network-dependent function.

Acceptance criteria:

- Core workflow testing identifies no application-initiated network request.
- Credentials, protected-application selections, and diagnostics are not transmitted.
- An optional operating-system handoff to external help does not transmit App Lock data.

Verification: Network inspection and test.

### NFR-RES-007 - Background Processing Efficiency

Background work shall run only when required for core health, local database safety, bounded diagnostics, cache, or cleanup.

Acceptance criteria:

- Concurrent nonessential tasks are minimized.
- Work is deferred when it would interfere with authentication or lock presentation.
- Automation, backup, Vault, reporting, synchronization, and remote telemetry tasks are absent.

Verification: Analysis and measurement.

### NFR-RES-008 - Thread Efficiency

Concurrent execution resources shall not remain active after their retained work completes.

Acceptance criteria:

- Idle worker accumulation does not occur.
- Contention does not materially delay lock presentation or authentication.
- Stress tests identify no deadlock or starvation of core work.

Verification: Analysis and stress test.

### NFR-RES-009 - Input and Output Efficiency

Local storage work shall avoid redundant reads and writes.

Acceptance criteria:

- Repeated unchanged health checks do not create unnecessary persistent writes.
- Related writes are consolidated where safe.
- Storage work does not unnecessarily block direct interaction.

Verification: Analysis and measurement.

### NFR-RES-010 - Resource Cleanup

Files, database connections, listeners, callbacks, and other resources shall be released after their intended use.

Acceptance criteria:

- Automated and endurance testing identify no unreleased critical resource.
- Cancellation and failure paths clean up as reliably as normal completion.

Verification: Analysis and test.

### NFR-RES-011 - Thermal Efficiency

Normal core operation shall not cause sustained thermal throttling attributable to App Lock.

Acceptance criteria:

- Extended monitoring remains below the reference phone's sustained thermal-throttling condition.
- A short protection or database-maintenance peak returns promptly to normal resource use.

Verification: Measurement.

### NFR-RES-012 - Resource Contention

The application shall coexist with other phone applications without unnecessary interference.

Acceptance criteria:

- Under system pressure, nonessential work degrades before authentication or lock presentation.
- App Lock remains responsive or reports an accurate Degraded or Protection interrupted state.

Verification: Stress test.

### NFR-RES-013 - Resource Monitoring

CPU, memory, storage, battery, and network use shall be profiled during version 1.0.0 verification.

Acceptance criteria:

- Evidence identifies the build, device, API level, workflow, and measurement period.
- Significant deviation from the approved baseline is investigated before distribution.
- No user-facing resource dashboard, historical metric store, or remote collection is required.

Verification: Measurement and inspection.
