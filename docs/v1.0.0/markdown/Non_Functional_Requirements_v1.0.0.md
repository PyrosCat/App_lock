# Non-Functional Requirements

## Version 1.0.0

## 1. Purpose and Quality Boundary

This specification defines measurable quality expectations for version 1.0.0 of an Android phone application that restricts access to selected applications. It applies only to the capability retained in the companion Software Requirements Specification.

Quality acceptance is limited to:

- conventional Android phones on Android 11 through Android 15, corresponding to API levels 30 through 35;
- PIN authentication with optional eligible biometrics and mandatory PIN fallback;
- Android Usage Access as the single application-detection baseline;
- protected-application selection and search;
- lock presentation, package-scoped sessions, cancellation, and global relock behavior;
- required-capability setup and protection-health recovery;
- privacy-preserving App Lock notifications;
- basic settings, help, on-device diagnostics, secure local data, in-place migration, and destructive reset; and
- core phone accessibility and privacy behavior.

No quality obligation is created for Vault, backup, restore, recovery passwords, cross-device migration, profiles, schedules, automation, intruder media, protected-application notification access, advanced event history, diagnostic export, remote telemetry, accounts, cloud services, an App Lock Accessibility service, tablets, foldables, large-screen modes, work profiles, cloned applications, secondary users, Android versions below API 30, or another excluded capability.

## 2. Measurement Context

Measurements shall use distributed-build settings and a declared test set consisting of API 30 through 35 phone emulators and the available conventional physical Android phones. A result from that set must not be described as universal manufacturer coverage.

Where a target depends on hardware, the evidence shall identify the reference phone, Android version, relevant operating conditions, dataset size, and measurement boundary. Security controls must not be weakened to satisfy a performance or resource target.

Requirement identifiers remain unchanged from the source quality specification. Gaps are intentional. An identifier omitted from the normative sections remains reserved and may not be renumbered or reused.

## 3. Performance

### NFR-PERF-001 - Application Startup Time

The application shall complete cold startup to an interactive primary interface within the measured median and 95th-percentile thresholds below under normal reference-phone conditions.

Acceptance criteria:

- Median cold startup is no more than 2.0 seconds.
- The 95th percentile is no more than 2.5 seconds across 100 measured launches.
- Protection remains Unknown or not verified until required safety checks finish.

Verification: Measurement.

### NFR-PERF-002 - Warm Startup Time

The application shall resume from a normal background state within 750 milliseconds.

Acceptance criteria:

- Median resume time is no more than 750 milliseconds.
- Resume presents no avoidable blank, stale, or protected-content frame.
- Session and protection state are reevaluated before access continues.

Verification: Measurement and test.

### NFR-PERF-003 - User Interface Responsiveness

Interactive controls shall provide perceptible feedback within 100 milliseconds under normal conditions.

Acceptance criteria:

- At least 95 percent of measured interactions respond within 100 milliseconds.
- No normal interaction exceeds 200 milliseconds without visible progress or disabled-state feedback.
- Authentication, settings, application selection, and recovery flows are included.

Verification: Measurement.

### NFR-PERF-004 - Frame Rendering Performance

The interface shall render smoothly during retained version 1.0.0 workflows.

Acceptance criteria:

- The target rendering rate is 60 frames per second on capable supported phones.
- No more than 1 percent of frames are dropped during the defined standard workflow set.
- Authentication privacy is not relaxed to meet the rendering target.

Verification: Measurement.

### NFR-PERF-005 - Database Query Performance

Routine local database queries shall complete within the defined response targets.

Acceptance criteria:

- Simple indexed queries complete within 50 milliseconds.
- Complex retained-feature queries complete within 250 milliseconds.
- The 95th percentile meets the stated limits for the declared dataset.

Verification: Measurement.

### NFR-PERF-006 - Search Performance

Installed-application search shall remain responsive within the supported phone dataset.

Acceptance criteria:

- Results appear within 300 milliseconds after a query change.
- Incremental updates remain responsive during continuous typing.
- The test set contains the declared maximum supported installed-application count.

Verification: Measurement.

### NFR-PERF-008 - Background Task Scheduling

Core health checks, bounded diagnostic cleanup, and local database maintenance shall not noticeably degrade foreground interaction.

Acceptance criteria:

- Foreground response continues to meet NFR-PERF-003 while retained background work runs.
- Protection and authentication take priority over cleanup and diagnostics.
- No Vault, backup, restore, automation, report, or synchronization workload is included.

Verification: Measurement and test.

### NFR-PERF-009 - Memory Allocation Efficiency

The application shall avoid allocation patterns that cause repeated interface stutter or sustained unnecessary memory growth.

Acceptance criteria:

- Profiling identifies no sustained allocation hotspot during the core workflow set.
- Extended lock monitoring does not show unbounded retained memory.
- Sensitive authentication buffers are released promptly.

Verification: Analysis and measurement.

### NFR-PERF-010 - Storage I/O Performance

Persistent local storage work shall minimize user-perceived delay.

Acceptance criteria:

- Routine reads and writes meet the stated interaction targets.
- Storage work does not synchronously block the primary interface for an avoidable period.
- Interrupted writes preserve the last committed valid state.

Verification: Analysis, measurement, and test.

### NFR-PERF-011 - Cryptographic Operation Performance

Local cryptographic operations shall complete without an avoidable user-perceived delay and without reducing approved security.

Acceptance criteria:

- PIN verification and retained local-data protection do not become a significant interaction bottleneck.
- Performance testing uses the approved security parameters without weaker test substitutes.
- Failure or key unavailability remains fail-secure.

Verification: Measurement and security assessment.

### NFR-PERF-012 - Application Lock Detection Latency

Version 1.0.0 detects foreground transitions by polling Usage Access rather than by event callback, so lock latency has two measured components: a detection delay (the poll interval) and an enforcement response. After Usage Access reports an applicable protected-application foreground transition, App Lock shall begin its protection response within 250 milliseconds under normal reference-phone conditions; the detection poll interval shall be a bounded, documented value so that the end-to-end time from a protected application reaching the foreground to the lock overlay being presented remains acceptable on reference phones.

Acceptance criteria:

- Enforcement-response measurement begins at receipt of the operating-system foreground result and ends when protection presentation begins; the normal-condition result is no more than 250 milliseconds.
- The Usage Access poll interval is bounded and documented; the end-to-end foreground-transition-to-lock latency (poll detection plus the enforcement response) is measured and reported per NFR-PERF-015 and is the figure against which lock latency is accepted.
- No avoidable protected-content exposure is accepted as a timing tradeoff.

Verification: Measurement and test.

### NFR-PERF-013 - Concurrent Operation Performance

The application shall remain responsive while retained core operations occur concurrently.

Acceptance criteria:

- Authentication, protection detection, application-list updates, bounded diagnostics, and user interaction do not starve one another.
- No duplicate session, duplicate protection presentation, or partial settings commit occurs.
- Deferred synchronization, backup, Vault, and report workloads are absent.

Verification: Measurement and stress test.

### NFR-PERF-014 - Performance Regression Control

Each version 1.x release shall avoid unacceptable degradation of retained critical workflows relative to its approved baseline.

Acceptance criteria:

- No retained critical workflow increases in execution time by more than 10 percent without a documented, user-acceptable reason.
- Lock detection, authentication, startup, application search, and database access are compared.
- Excluded features create no benchmark obligation.

Verification: Measurement and comparison.

### NFR-PERF-015 - Performance Benchmarking

A repeatable benchmark set shall measure the retained version 1.0.0 performance requirements on the declared phone and API matrix.

Acceptance criteria:

- The benchmark identifies device, API level, build, dataset, and measurement boundary.
- Results for all retained numeric targets are recorded before distribution.
- A failed critical target is corrected or stated as an unresolved release limitation; it is not hidden by averaging unrelated results.

Verification: Measurement and inspection.

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

## 7. Security Quality

### NFR-SEC-001 - Security by Design

Security requirements shall be applied to every retained authentication, protection, platform, data, notification, recovery, and update behavior.

Acceptance criteria:

- The delivered behavior has no undocumented path around PIN or session validation.
- Excluded features do not leave dormant permissions, interfaces, or data flows.

Verification: Security assessment and inspection.

### NFR-SEC-002 - Cryptographic Standards

All retained cryptographic operations shall use current platform-supported algorithms, protocols, and key sizes suitable for the protected data.

Acceptance criteria:

- No deprecated or prohibited algorithm is used.
- Approved parameters are verified in the distributed build.
- Cryptographic failure remains fail-secure.

Verification: Analysis, static assessment, and test.

### NFR-SEC-003 - Secure Randomness

Security-sensitive randomness shall be cryptographically secure.

Acceptance criteria:

- Credential salt, key generation, and any retained security token use an approved secure source.
- Non-cryptographic randomness is absent from security decisions.

Verification: Static analysis and security review.

### NFR-SEC-004 - Secure Key Protection

Cryptographic keys shall remain protected throughout generation, use, invalidation, and deletion.

Acceptance criteria:

- No key is hard-coded, logged, exported, or stored as readable application data.
- Platform key invalidation produces a safe recovery state.

Verification: Static analysis, storage inspection, and security test.

### NFR-SEC-005 - Secure Secret Management

Credentials and other sensitive values shall use only approved local protection mechanisms.

Acceptance criteria:

- The distributed package contains no production secret or user credential.
- Diagnostic output contains no PIN, key, or authentication input.
- Version 1.0.0 includes no server token, account secret, or cloud credential.

Verification: Package inspection and static analysis.

### NFR-SEC-006 - Least Privilege

The application shall request and use only capabilities necessary for retained version 1.0.0 behavior.

Acceptance criteria:

- Usage Access and each other required capability has a documented user-facing purpose.
- No App Lock Accessibility service, camera, location, notification-listener, broad storage, account, Bluetooth, or deferred-feature permission is requested.
- Exported application surfaces are minimized.

Verification: Manifest inspection, runtime test, and security review.

### NFR-SEC-007 - Secure Communication

Version 1.0.0 shall not require application network communication.

Acceptance criteria:

- Network inspection identifies no routine application data transmission.
- An external help handoff, if present, transmits no App Lock credential, selection, or diagnostic data.
- Introduction of a network service is outside this release boundary.

Verification: Network inspection and test.

### NFR-SEC-008 - Attack Surface Minimization

The application shall expose no unnecessary service, receiver, activity, provider, deep link, or privileged capability.

Acceptance criteria:

- Every externally reachable surface has a retained version 1.0.0 purpose and appropriate access restriction.
- Deferred feature entry points and permissions are absent from the distributed build.

Verification: Static and dynamic security assessment.

### NFR-SEC-009 - Secure Failure Behavior

An uncertain or failed security decision shall default to requiring fresh authentication or denying protected access.

Acceptance criteria:

- No error, timeout, race, migration failure, or process recreation grants access without a valid decision.
- Protection health never reports normal protection with a failed required capability.

Verification: Negative and fault-injection testing.

### NFR-SEC-010 - Security Logging Quality

Retained local security records shall support immediate diagnosis while protecting sensitive information.

Acceptance criteria:

- Records use consistent time, event, severity, and outcome fields.
- Credentials, biometric data, protected content, keys, and unnecessary application-use detail are absent.
- Retention is fixed and bounded.

Verification: Inspection and test.

### NFR-SEC-011 - Dependency Security

Third-party software used by version 1.0.0 shall have an acceptable security posture.

Acceptance criteria:

- No unresolved Critical vulnerability is present.
- A High-severity vulnerability affecting the distributed application is corrected before public distribution unless the dependency is removed.
- Unused dependencies belonging only to excluded features are absent.

Verification: Dependency analysis and inspection.

### NFR-SEC-014 - Vulnerability Remediation

Discovered vulnerabilities shall be evaluated according to their effect on unauthorized access, credential protection, local-data integrity, and privacy.

Acceptance criteria:

- A Critical vulnerability is corrected before distribution.
- Corrective action includes a regression test for the affected retained behavior.

Verification: Inspection and retest.

### NFR-SEC-015 - Security Regression Prevention

Updates shall not reduce verified security of previously delivered retained behavior.

Acceptance criteria:

- Authentication, session, relock, protection presentation, storage, permission, migration, and privacy regression tests pass.
- Previously corrected security defects are not reintroduced.

Verification: Security regression test.

### NFR-SEC-016 - Secure Build Integrity

Distributed artifacts shall be generated through a controlled, repeatable, and verifiable build.

Acceptance criteria:

- The build can be reproduced from the documented inputs.
- The final artifact is authenticated, version identified, and checked before distribution.
- Debug capability and deferred-feature surfaces are absent.

Verification: Build inspection and artifact verification.

### NFR-SEC-017 - Security Documentation

Security behavior, assumptions, required capabilities, data handling, recovery, and known limitations shall match the delivered version 1.0.0 application.

Acceptance criteria:

- Documentation consistently states Usage Access as the single detection baseline and excludes an App Lock Accessibility service.
- No document claims support for a deferred feature or device class.

Verification: Documentation inspection.

## 8. Privacy Quality

### NFR-PRIV-001 - Data Minimization

The application shall collect, process, and retain only information required for the documented version 1.0.0 behavior.

Acceptance criteria:

- Each retained data category has a stated purpose.
- Account, cloud, location, camera, Vault, protected-application notification, advertising, and exported diagnostic data are absent.

Verification: Data inventory and inspection.

### NFR-PRIV-002 - Purpose Limitation

Retained information shall be used only for authentication, protection, settings, current health, bounded diagnostics, migration, and local recovery.

Acceptance criteria:

- No retained information is used for analytics, recommendations, advertising, profiling, or undisclosed processing.
- User-facing explanations match actual use.

Verification: Inspection and privacy assessment.

### NFR-PRIV-003 - Local Processing Preference

All retained user and security information shall be processed locally.

Acceptance criteria:

- Primary operation succeeds without network access.
- Network inspection reveals no transmission of retained application data.

Verification: Offline test and network inspection.

### NFR-PRIV-004 - Privacy by Default

Initial and reset configuration shall use the most privacy-preserving supported behavior.

Acceptance criteria:

- App Lock notification text is masked by default.
- Sensitive App Lock screens use screenshot and recent-preview protection.
- No optional data sharing is present.

Verification: Inspection and test.

### NFR-PRIV-005 - Data Exposure Minimization

Sensitive information shall not be unnecessarily displayed, logged, cached, retained, or included in notifications.

Acceptance criteria:

- PIN, biometric result detail, keys, protected content, and unnecessary protected-application activity are absent from diagnostics and notifications.
- Sensitive interface state is cleared on cancellation and backgrounding.

Verification: Privacy test and inspection.

### NFR-PRIV-006 - Metadata Protection

Protected-application selections, authentication events needed for enforcement, and diagnostic context shall be treated as sensitive metadata.

Acceptance criteria:

- Sensitive metadata receives appropriate local access and storage protection.
- Notifications and help do not expose the protected-application list.

Verification: Storage and interface inspection.

### NFR-PRIV-007 - User Data Lifecycle Control

Every retained data category shall have defined creation, storage, retention, migration, and deletion behavior.

Acceptance criteria:

- Fixed diagnostic, cache, and temporary-data bounds are applied consistently.
- Destructive reset removes all App Lock-managed local user and security data.

Verification: Lifecycle test and inspection.

### NFR-PRIV-008 - Privacy Impact Assessment

A privacy assessment shall cover retained capabilities and confirm the absence of excluded high-sensitivity data flows.

Acceptance criteria:

- Assessment covers Usage Access, credentials, protected-application selections, App Lock notifications, local diagnostics, migration, and destructive reset.
- It confirms no camera, location, protected-application notification, Vault, cloud, account, or diagnostic-export flow.

Verification: Privacy assessment.

### NFR-PRIV-009 - Third-Party Privacy Assurance

Included libraries and operating-system integrations shall not introduce data use beyond the stated version 1.0.0 purpose.

Acceptance criteria:

- Third-party behavior and permissions are reviewed before inclusion.
- A component requiring undisclosed collection, advertising identity, or remote telemetry is not included.

Verification: Dependency and privacy review.

### NFR-PRIV-010 - Privacy Compliance Verification

The distributed version 1.0.0 application and its user-facing disclosures shall be verified against applicable privacy obligations.

Acceptance criteria:

- Actual permission, storage, notification, and network behavior matches the disclosures.
- A material privacy mismatch is resolved before public distribution.

Verification: Privacy and compliance assessment.

## 9. Maintainability

### NFR-MNT-001 - Modular Design

The software shall organize authentication, protection, user interface, Android integration, and local data into cohesive responsibilities with documented boundaries.

Acceptance criteria:

- Dependencies between major responsibilities are explicit and justified.
- No empty Vault, backup, automation, intruder, cloud, or Accessibility-service module is required for future use.

Verification: Design inspection.

### NFR-MNT-002 - Separation of Concerns

User interface, protection decisions, local data, security services, and Android-specific integration shall remain logically separated.

Acceptance criteria:

- Interface presentation does not directly determine access or write sensitive persistent data.
- Android-specific behavior is isolated from release-wide protection rules where practical.

Verification: Design and source inspection.

### NFR-MNT-003 - Coding Standards Compliance

Distributed source shall comply with the approved language and Android coding standards applicable to the retained application.

Acceptance criteria:

- Automated formatting and code-quality checks pass.
- Exceptions affecting security or maintainability are absent from the distributed build.

Verification: Static analysis.

### NFR-MNT-004 - Code Readability

The delivered software shall favor clear, direct realization of the reduced capability over unnecessary abstraction or complexity.

Acceptance criteria:

- Complex security decisions and externally used interfaces have adequate technical explanation.
- Deferred-feature frameworks do not add unused paths or dependencies.

Verification: Source inspection.

### NFR-MNT-005 - Documentation Quality

Supporting documentation shall accurately describe delivered Version 1.0.0 behavior.

Acceptance criteria:

- Device, permission, authentication, recovery, data, notification, and exclusion statements agree across the document set.
- No documentation describes an excluded capability as available, optional, or partially complete.

Verification: Documentation inspection.

### NFR-MNT-006 - Change Traceability

Each retained requirement shall be traceable to its verification evidence.

Acceptance criteria:

- FR and NFR identifiers appear unchanged in the applicable evidence.
- Gaps caused by excluded requirements remain gaps and are not reused.

Verification: Traceability inspection.

### NFR-MNT-007 - Dependency Management

External dependencies shall be limited to those needed for retained version 1.0.0 behavior.

Acceptance criteria:

- Each included dependency has a purpose, version, license, and security status.
- Unsupported, end-of-life, or deferred-feature dependencies are absent.

Verification: Dependency inspection.

### NFR-MNT-009 - Configuration Management

Security-relevant application configuration shall be centralized, versioned, validated, and documented.

Acceptance criteria:

- Invalid configuration resolves to a documented safe state.
- Configuration includes only retained settings and no dormant deferred-feature values.

Verification: Design inspection and test.

### NFR-MNT-010 - Backward Compatibility

Compatible version 1.x changes shall preserve retained behavior and same-installation local data unless a documented security correction requires otherwise.

Acceptance criteria:

- Supported upgrade paths preserve valid PIN, protected selections, and retained settings.
- Cross-device migration, backup formats, and deferred-feature data are outside the requirement.

Verification: Upgrade test.

### NFR-MNT-011 - Build Maintainability

The application build shall be automated, repeatable, and documented.

Acceptance criteria:

- A distributed build can be recreated using the documented environment and inputs.
- Failures produce actionable diagnostic information without exposing secrets.

Verification: Independent build test.

### NFR-MNT-012 - Refactoring Quality

Internal restructuring shall preserve externally observable retained behavior unless an intentional specification change is approved for a later release.

Acceptance criteria:

- Regression tests confirm unchanged authentication, protection, data, privacy, and recovery behavior.
- Refactoring does not introduce deferred features or weaken the release boundary.

Verification: Regression test and inspection.

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

## 11. Observability

### NFR-OBS-001 - Diagnostic Record Consistency

Bounded local diagnostic records shall use consistent event names and fields across compatible version 1.x releases.

Acceptance criteria:

- Time, event, severity, outcome, and minimum safe context have stable meanings.
- Remote telemetry and analytics identifiers are absent.

Verification: Inspection and test.

### NFR-OBS-002 - Timestamp Accuracy

Local diagnostic records shall use accurate and consistently formatted timestamps sufficient to reconstruct event order.

Acceptance criteria:

- A single documented time reference and format is used.
- Clock changes do not cause records to be misrepresented as security decisions.

Verification: Test and inspection.

### NFR-OBS-003 - Diagnostic Completeness

Retained diagnostics shall provide enough safe context to identify the affected core operation and its outcome.

Acceptance criteria:

- A current permission, service, data, authentication-delay, or recovery problem can be distinguished.
- Credentials, protected content, and unnecessary use history are absent.

Verification: Diagnostic review.

### NFR-OBS-004 - Metric Quality

User-visible protection health and measurements collected during verification shall accurately reflect observed behavior.

Acceptance criteria:

- A reported passing health check corresponds to a current successful check.
- Test measurements identify their boundary and conditions.
- No continuous product analytics or historical dashboard is required.

Verification: Test and comparison.

### NFR-OBS-005 - Diagnostic Performance Impact

Local diagnostic activity shall not increase average processor use by more than 2 percent during normal reference-phone operation.

Acceptance criteria:

- Diagnostic memory and storage remain within their documented bounds.
- Diagnostics do not delay authentication or lock presentation beyond applicable targets.

Verification: Measurement.

### NFR-OBS-006 - Data Integrity

Local diagnostic records shall not be unintentionally modified or corrupted during normal storage, rotation, and deletion.

Acceptance criteria:

- Testing identifies no unexplained record corruption within the bounded retention period.
- Diagnostic corruption cannot affect an access decision.

Verification: Test.

### NFR-OBS-007 - Information Classification

Diagnostic fields shall be classified and handled according to their sensitivity.

Acceptance criteria:

- Credential, biometric, protected-content, key, and unnecessary application-activity fields are prohibited.
- Sensitive retained context receives appropriate local storage protection.

Verification: Data and logging inspection.

### NFR-OBS-008 - Retention Control

Local diagnostic records shall follow a fixed bounded retention rule.

Acceptance criteria:

- Expired records are removed automatically.
- No archive, user-configurable retention, or long-term history is provided.
- Destructive reset removes all retained diagnostics.

Verification: Lifecycle test.

### NFR-OBS-009 - Diagnostic Availability

Current health and diagnostic results shall be available on device to the authenticated user and to verification tools.

Acceptance criteria:

- The application presents current status and recovery guidance.
- There is no share, export, remote-access, or support-upload action.

Verification: Interface inspection and test.

### NFR-OBS-010 - Observability Validation

The retained protection-health indicators and bounded local records shall be validated before distribution.

Acceptance criteria:

- Every controlled protection state has a verified trigger and recovery transition.
- Logs are checked for privacy, retention, accuracy, and performance impact.
- Trend analysis and a separate observability subsystem are outside the scope.

Verification: Test and inspection.

## 12. Usability and Accessibility

### NFR-UX-001 - User Interface Consistency

The application shall use consistent navigation, terminology, iconography, actions, and state behavior across all retained screens.

Acceptance criteria:

- Equivalent actions and protection states use the same label and presentation.
- Onboarding, protection status, authentication, settings, and recovery do not contradict one another.

Verification: Design review and usability test.

### NFR-UX-002 - Learnability

A first-time user shall be able to complete the primary setup and protection workflow without extensive external instructions.

Acceptance criteria:

- A representative user can create a PIN, understand optional biometrics, grant Usage Access, complete required setup, select an application, and verify protection.
- Explanations communicate limitations without internal technical terminology.

Verification: Usability test.

### NFR-UX-003 - User Interaction Efficiency

Common retained tasks shall avoid unnecessary navigation and repeated input.

Acceptance criteria:

- Protecting or unprotecting one application is available directly from the application list after required authentication.
- Permission recovery returns to the interrupted task and rechecks automatically.
- PIN is not requested again for the same protected application while its valid session applies, except for a sensitive settings change that explicitly requires it.

Verification: Workflow inspection and usability test.

### NFR-UX-004 - Visual Consistency

The application shall present a polished, cohesive phone interface using the approved visual system.

Acceptance criteria:

- Typography, spacing, color roles, icons, surfaces, and component states are applied consistently.
- Protection and error severity are visually clear without appearing alarming during normal use.
- A theme selector or multiple custom themes are not required.

Verification: Visual review.

### NFR-UX-005 - Error Prevention

The interface shall reduce avoidable mistakes through clear state, confirmation, and constrained input.

Acceptance criteria:

- PIN creation requires confirmation.
- Protection-reducing and destructive actions explain their effect before completion.
- Unsupported settings and incomplete required states cannot be saved as valid.

Verification: Usability and negative testing.

### NFR-UX-006 - Error Recovery Support

Error and Degraded-state messages shall explain the effect and the next safe action.

Acceptance criteria:

- Messages avoid internal implementation language.
- Permission loss, interrupted protection, failed local-data recovery, and forgotten PIN each have distinct guidance.
- A retry action is offered only when retry can change the result.

Verification: Content review and usability test.

### NFR-UX-007 - Accessibility Standards

The application shall conform to applicable Android accessibility guidance for the retained phone interface.

Acceptance criteria:

- No Critical accessibility defect remains in a primary workflow.
- Accessibility does not weaken PIN privacy or protection behavior.

Verification: Automated and manual accessibility assessment.

### NFR-UX-008 - Screen Reader Compatibility

Primary workflows shall be operable with a supported Android screen reader.

Acceptance criteria:

- Interactive controls expose meaningful name, role, state, and action.
- Focus follows task order and does not move behind authentication, dialogs, or sheets.
- Protection state and application protection selection are announced without relying on color or icon alone.

Verification: Manual screen-reader test.

### NFR-UX-009 - Visual Accessibility

Text and interface elements shall remain perceivable across supported visual-accessibility settings.

Acceptance criteria:

- Text and essential controls meet the approved contrast ratios.
- Status is never conveyed by color alone.
- Content remains understandable at the supported maximum font and display scaling without clipping or loss of action.

Verification: Measurement and visual test.

### NFR-UX-010 - Touch Accessibility

Interactive controls shall provide suitable touch targets and separation for reliable phone use.

Acceptance criteria:

- Touch targets comply with current Android guidance.
- PIN controls, application-selection controls, permission actions, and destructive confirmation are not crowded or ambiguous.

Verification: Measurement and usability test.

### NFR-UX-011 - Adaptive User Interface

The interface shall adapt to conventional phone windows, portrait, functional secure landscape, supported text scaling, display scaling, and phone multi-window states.

Acceptance criteria:

- Portrait is the primary optimized presentation.
- Landscape remains complete, secure, and operable.
- Split-screen and picture-in-picture fail safely where a reliable protection presentation cannot be provided.
- No tablet, foldable, large-screen, desktop, or multi-pane layout is required.

Verification: Phone configuration test.

### NFR-UX-012 - Localization Readiness

User-visible content shall be prepared for later localization without requiring version 1.0.0 to ship multiple languages.

Acceptance criteria:

- User-visible strings are externalized from application logic.
- Layout accommodates reasonable text expansion and does not embed meaning in an image alone.
- Version 1.0.0 may ship one approved language.

Verification: Design and source inspection.

### NFR-UX-014 - Accessibility Verification

Accessibility shall be verified across every primary retained workflow.

Acceptance criteria:

- Verification covers onboarding, PIN, biometric fallback, protected-application selection, permission guidance, protection status, settings, destructive reset, and error recovery.
- Automated checks are supplemented by manual screen-reader, text-scaling, focus, contrast, and touch review.

Verification: Accessibility assessment.

## 13. Compatibility and Portability

### NFR-COMP-001 - Android Platform Compatibility

The application shall operate on Android API levels 30 through 35 only.

Acceptance criteria:

- Retained critical workflows are tested on each API level.
- No Critical compatibility defect remains in the declared phone matrix.
- API levels below 30 create no acceptance obligation.

Verification: Compatibility test.

### NFR-COMP-002 - Device Compatibility

The application shall operate on conventional Android phones represented by the declared physical-device and emulator matrix.

Acceptance criteria:

- Device-specific limitation found during testing is documented accurately.
- No universal manufacturer claim is made without evidence.
- Tablet, foldable, desktop, television, automotive, wearable, work-profile, cloned-application, and secondary-user behavior is excluded.

Verification: Device test and inspection.

### NFR-COMP-003 - Display Compatibility

The application shall remain usable and secure across supported compact phone sizes, common phone densities, portrait, functional landscape, and phone multi-window conditions.

Acceptance criteria:

- Content and actions remain visible and operable without overlap or clipping.
- Sensitive screens remain private in recents and supported capture restrictions.
- No large-screen adaptive pane or foldable-posture behavior is required.

Verification: Visual configuration test.

### NFR-COMP-004 - Hardware Compatibility

The application shall operate without eligible biometric hardware and adapt safely to supported hardware capability differences.

Acceptance criteria:

- PIN-only operation is complete.
- Eligible biometrics appear only when supported and enrolled.
- Camera, location, removable storage, Bluetooth, and deferred-feature hardware are not required.

Verification: Capability test.

### NFR-COMP-005 - Android Runtime Compatibility

The application shall conform to runtime, security, permission, notification, and background-execution behavior on API levels 30 through 35.

Acceptance criteria:

- Usage Access remains the single detection baseline on every supported level.
- Version-specific restrictions produce correct Protected, Degraded, Protection interrupted, or Action required behavior.

Verification: API-level integration test.

### NFR-COMP-006 - Build Environment Portability

The application shall be buildable in the documented supported build environment without dependence on an individual workstation.

Acceptance criteria:

- Required tool and dependency versions are documented.
- A clean supported environment can produce the distributed configuration.

Verification: Independent build test.

### NFR-COMP-007 - Configuration Portability

Build and test configuration shall be portable among documented delivery environments without creating an end-user settings-export feature.

Acceptance criteria:

- Environment-specific values are separated from user data and validated.
- Production configuration cannot be silently replaced by a debug configuration.

Verification: Build and configuration inspection.

### NFR-COMP-008 - Data Portability

Retained local data shall remain usable across supported same-installation version 1.x updates.

Acceptance criteria:

- Valid credentials, protected-application selections, and retained settings survive tested upgrade paths.
- Cross-device transfer, backup export, restore, and migration to another installation are excluded.

Verification: Upgrade test.

### NFR-COMP-009 - Forward Compatibility

The application shall minimize avoidable dependence on deprecated Android behavior within the supported boundary.

Acceptance criteria:

- Deprecated platform dependencies affecting retained capability are identified.
- Compatibility assessment covers changes relevant to Usage Access, lock presentation, notifications, biometrics, storage, and background work.

Verification: Analysis.

### NFR-COMP-010 - Dependency Portability

Third-party dependencies shall not unnecessarily prevent continued support of the declared Android phone boundary.

Acceptance criteria:

- Each dependency's platform support and maintenance status is reviewed.
- A dependency used solely by a deferred feature is absent.

Verification: Dependency inspection.

### NFR-COMP-011 - Release Compatibility Verification

Compatibility shall be verified before distribution against the API 30 through 35 emulator matrix and the available conventional physical-phone set.

Acceptance criteria:

- Evidence covers installation, update, startup, onboarding, authentication, protection, relock, permissions, health, notifications, local data, accessibility, and destructive reset.
- Unsupported device classes and profile types are not included in the campaign or claimed as supported.

Verification: Compatibility test and inspection.

## 14. Compliance

### NFR-COMPY-001 - Regulatory Compliance

The application shall comply with laws and regulations applicable to its stated local operation and intended distribution jurisdictions.

Acceptance criteria:

- Applicable obligations are identified before public distribution.
- A known material nonconformance affecting distribution is corrected.

Verification: Compliance assessment.

### NFR-COMPY-002 - Google Play Compliance

If distributed through Google Play, version 1.0.0 shall comply with the applicable Google Play Developer Program Policies.

Acceptance criteria:

- Usage Access, notifications, background behavior, data safety, and disclosures are reviewed for the submitted build.
- No known policy violation remains unresolved before submission.

Verification: Policy assessment.

### NFR-COMPY-003 - Android Platform Compliance

The application shall conform to Android requirements applicable to API levels 30 through 35.

Acceptance criteria:

- Permission, Usage Access, biometrics, notifications, background execution, storage, and package behavior are reviewed.
- A platform limitation is documented rather than bypassed or misrepresented.

Verification: Platform compliance test and inspection.

### NFR-COMPY-004 - Security Standards Compliance

The delivered application shall conform to the approved security requirements and practices applicable to the retained threat surface.

Acceptance criteria:

- Security assessment covers authentication, sessions, protection presentation, exported surfaces, local data, migration, logs, notifications, and destructive reset.
- Critical findings are resolved before public distribution.

Verification: Security assessment.

### NFR-COMPY-005 - Privacy Compliance

Privacy behavior and disclosures shall accurately reflect the local, reduced version 1.0.0 data boundary.

Acceptance criteria:

- Data inventory, requested capabilities, storage, retention, deletion, notification, and network behavior match the published disclosure.
- No deferred data flow is present in the distributed build.

Verification: Privacy assessment.

### NFR-COMPY-006 - Open Source License Compliance

Third-party software use shall satisfy applicable license obligations.

Acceptance criteria:

- An accurate dependency and license inventory is available.
- Required notices and source-offer obligations, if any, are satisfied before distribution.

Verification: License inspection.

### NFR-COMPY-007 - Documentation Compliance

The version 1.0.0 SRS, NFR, UI/UX Specification, Threat Model, Software Design Specification, and Database Design Specification shall agree with the delivered application.

Acceptance criteria:

- All documents use the same device, API, authentication, Usage Access, recovery, data, permission, notification, and exclusion boundaries.
- No document names an excluded feature as a Version 1.0.0 obligation.

Verification: Documentation inspection.

### NFR-COMPY-009 - Compliance Evidence

Evidence needed to demonstrate conformance of the distributed version 1.0.0 application shall be retained.

Acceptance criteria:

- Evidence includes applicable functional, non-functional, security, privacy, accessibility, compatibility, dependency, and migration results.
- Evidence is limited to retained requirements and the declared phone boundary.

Verification: Inspection.

## 15. Release and Support Quality

### NFR-OPS-001 - Production Readiness

The application shall satisfy the retained version 1.0.0 functional, quality, security, privacy, accessibility, migration, and supported-phone criteria before public distribution.

Acceptance criteria:

- Required evidence is complete for every retained critical requirement.
- No unresolved defect permits unauthorized access, exposes a credential, corrupts committed data, or falsely reports protection.

Verification: Evidence inspection.

### NFR-OPS-002 - Release Management

Each distributed build shall provide public version identification, concise release notes, known limitations, and installation or upgrade guidance.

Acceptance criteria:

- Notes describe observable changes and limitations without internal administration language.
- Support and compatibility decisions can identify the installed build.

Verification: Inspection.

### NFR-OPS-004 - Deployment Validation

Installation and supported update shall be validated through startup, PIN setup or preservation, protected-application selection, Usage Access, lock presentation, and protection health.

Acceptance criteria:

- A clean installation completes onboarding successfully.
- A supported in-place update preserves retained valid local data.
- A failed migration remains fail-secure and actionable.

Verification: Installation and upgrade test.

### NFR-OPS-005 - Configuration Integrity

Distributed configuration shall be versioned, validated, and resistant to unauthorized or accidental weakening.

Acceptance criteria:

- Invalid or unknown security configuration resolves to a documented safe state.
- Debug and deferred-feature configuration is absent from the distributed build.

Verification: Configuration inspection and test.

### NFR-OPS-006 - Release Rollback Capability

Failure during installation or migration shall not leave the application in a partially usable or falsely protected state.

Acceptance criteria:

- The last committed valid local data remains available where safe recovery is possible.
- If Android does not permit application downgrade, the documentation does not promise downgrade.
- No backup-based rollback or restore is required.

Verification: Failure and migration test.

### NFR-OPS-008 - Operational Documentation

User-facing documentation shall support installation, setup, authentication, protection status, permission restoration, destructive reset, supported devices, known limitations, and basic troubleshooting.

Acceptance criteria:

- Guidance is accurate for API 30 through 35 phones.
- It does not describe internal delivery administration or excluded capability as available.

Verification: Documentation inspection.

### NFR-OPS-009 - Operational Monitoring Readiness

The application shall provide sufficient on-device protection health and bounded local diagnostics to understand a current core failure.

Acceptance criteria:

- Current status and safe recovery actions are available to the authenticated user.
- Remote monitoring, fleet metrics, event-history analysis, trend reporting, and diagnostic export are absent.

Verification: Interface and diagnostic inspection.

### NFR-OPS-013 - Supportability

The application shall provide sufficient local help and privacy-safe current diagnostics to support basic troubleshooting.

Acceptance criteria:

- Help covers PIN and biometric behavior, Usage Access, lock presentation, relock, notifications, health states, migration failure, and destructive reset.
- Troubleshooting does not require the user to export logs or reveal protected-application activity.

Verification: Support workflow test.

## Appendix A - Non-Functional Requirement Disposition

The following existing identifiers are not normative version 1.0.0 obligations. They remain reserved and are not renumbered or reused.

### A.1 Performance and Reliability

- NFR-PERF-007 - Secure Vault Loading Performance.
- NFR-REL-002 - 1,000-hour MTBF acceptance target.
- NFR-REL-004 - production-population crash-free session metric.
- NFR-REL-008 - separate seven-day endurance campaign.

### A.2 Security and Maintainability Programs

- NFR-SEC-012 and NFR-SEC-013 - separate static and dynamic security-assessment obligations; retained critical security behavior is verified through the focused automated, integration, and regression requirements.
- NFR-SEC-018 - separate independent security assessment.
- NFR-SEC-019 and NFR-SEC-020 - continuing security metrics and annual improvement programs.
- NFR-MNT-008 - technical-debt administration.
- NFR-MNT-013, NFR-MNT-014, and NFR-MNT-015 - organizational knowledge-transfer, periodic assessment, and annual improvement programs.

### A.3 Testing and Observability Programs

- NFR-TEST-012 - annual testing-process improvement program.
- NFR-OBS-011 and NFR-OBS-012 - operational trend analysis and continuing observability program.

### A.4 Usability and Compatibility Programs

- NFR-UX-013 - formal user-satisfaction assessment program.
- NFR-UX-015 - annual usability-improvement program.
- NFR-COMP-012 - annual compatibility-improvement program.

### A.5 Compliance and Operational Administration

- NFR-COMPY-008 - general process-compliance administration.
- NFR-COMPY-010 - annual compliance-review program; applicable release compliance remains normative under NFR-COMPY-001, NFR-COMPY-002, and NFR-COMPY-005.
- NFR-OPS-007 - organizational incident-response program.
- NFR-OPS-003 - separate independent build-reproduction obligation; the distributed artifact remains controlled and verifiable under NFR-SEC-016 and NFR-MNT-011.
- NFR-OPS-010, NFR-OPS-011, and NFR-OPS-012 - maintenance-window, change-management, and recurring metrics-review administration.
- NFR-OPS-014 and NFR-OPS-015 - organizational lifecycle and continuing operational-improvement programs.

## Appendix B - Cross-Document Quality Invariants

The complete version 1.0.0 document set shall preserve these invariants:

1. PIN is mandatory; eligible biometrics are optional and always fall back to PIN.
2. Usage Access is the single application-detection baseline; an App Lock Accessibility service is absent.
3. Each protected application has its own volatile authorization session, governed by one global relock policy.
4. A failed required capability cannot be shown as protected.
5. Forgotten PIN provides destructive reset only and preserves no local configuration.
6. App Lock emits only its own essential masked notifications and does not access protected-application notifications.
7. All retained user and security data remains local, and no routine application network traffic is produced.
8. Diagnostics are current, local, privacy safe, bounded, and not exportable.
9. Compatibility is limited to conventional phones on API levels 30 through 35 and the declared evidence set.
10. A single polished accessible phone visual system that follows system light and dark appearance is required; a theme selector, custom themes, and large-screen layouts are not.
