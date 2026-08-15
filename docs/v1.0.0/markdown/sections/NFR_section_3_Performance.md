# Non-Functional Requirements

## Version 1.0.0

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
