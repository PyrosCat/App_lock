# Software Requirements Specification

## Version 1.0.0

## Section 11 Performance

#### FR-206 - Application Startup Performance

The application shall reach an interactive primary screen within the startup thresholds defined by NFR-PERF-001 under normal reference-phone conditions.

Acceptance criteria:

- Startup does not block the interface longer than necessary for required safety checks.
- Protection status remains Unknown or not verified until those checks finish.
- Startup failure produces a recoverable message or safe Action required state.

#### FR-207 - Lock Detection Response Time

The application shall respond promptly after Usage Access reports that a protected application has become foreground.

Acceptance criteria:

- The timing boundary and target match FR-049 and NFR-PERF-012.
- Normal detection begins protection within the approved threshold on the declared reference phone.
- Performance testing includes cold, warm, rapid-relaunch, and task-switch cases.

#### FR-208 - Memory Usage Optimization

The application shall control memory use during protection monitoring, authentication, application-list browsing, and local diagnostics.

Acceptance criteria:

- Extended operation reveals no continuing memory growth attributable to unreleased application resources.
- Authentication input and temporary security data are released promptly.
- Memory pressure degrades nonessential work before core protection.

#### FR-209 - CPU Usage Optimization

The application shall avoid unnecessary processor use while monitoring with Usage Access.

Acceptance criteria:

- Idle monitoring avoids continuous high-frequency polling.
- Processor use returns to the defined idle range after a protection event.
- Diagnostic collection does not compete with lock presentation or authentication.

#### FR-210 - Battery Optimization Mode

The application shall reduce nonessential cleanup and diagnostic activity when device resources are constrained while preserving the core protection path.

Acceptance criteria:

- Authentication, Usage Access evaluation, lock presentation, and mandatory relock behavior remain active.
- Deferred nonessential work resumes when conditions permit.
- No user-configurable battery automation rule or threshold is required.

#### FR-211 - Adaptive Background Monitoring

The application shall adapt core monitoring to supported Android lifecycle and screen states without becoming a general automation feature.

Acceptance criteria:

- Monitoring behavior distinguishes active use, backgrounding, screen off, and restart recovery.
- Adaptation does not use Wi-Fi, location, Bluetooth, calendar, charging, or user schedules.
- Every adaptation preserves or truthfully downgrades the protection-health state.

#### FR-213 - Application Stability

The application shall remain stable during normal and abnormal core workflows.

Acceptance criteria:

- Failures do not corrupt committed credentials, settings, or protected-application selections.
- Recoverable failures return to a defined state without device reboot.
- Sensitive details are excluded from user-visible error information.

#### FR-214 - Service Recovery

The application shall restore services required for Usage Access evaluation, lock presentation, and its own essential notification after recoverable termination.

Acceptance criteria:

- Recovery is attempted using behavior permitted by the supported Android version.
- Protection is not reported as restored until verification succeeds.
- No notification listener, backup, Vault, scheduler, or automation service is required.

#### FR-215 - Device Compatibility

The application shall support only conventional Android phones on API levels 30 through 35 within the declared test matrix.

Acceptance criteria:

- Portrait, secure functional landscape, common phone densities, and supported text scaling are verified.
- PIN-only operation works on phones without eligible biometrics.
- No claim is made for tablets, foldables, desktop modes, work profiles, cloned applications, secondary users, or untested manufacturer-specific behavior.
