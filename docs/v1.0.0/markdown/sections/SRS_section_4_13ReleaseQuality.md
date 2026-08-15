# Software Requirements Specification

## Version 1.0.0

## Section 13 Release Quality

#### FR-228 - Database Migration Management

The application shall provide a defined migration path for every version 1.x local schema change.

Acceptance criteria:

- Supported in-place updates preserve valid credentials, protected-application selections, and retained settings.
- Migration either completes fully or leaves the previous committed data recoverable.
- Cross-device migration and deferred-feature data formats are not required.

#### FR-229 - Database Integrity Verification

The application shall verify local database integrity before relying on stored security policy.

Acceptance criteria:

- Corruption is detected before invalid data can grant access.
- Recoverable inconsistencies are repaired without weakening policy.
- Unrecoverable corruption results in fail-secure guidance and, when necessary, destructive reset.

#### FR-230 - Background Processing

Essential local maintenance that could block interaction shall run without blocking the primary user interface.

Acceptance criteria:

- Only database maintenance, integrity checking, diagnostic cleanup, cache cleanup, and secure local deletion are included.
- Protection and authentication work take priority.
- Vault, backup, restore, report generation, and bulk-file work are absent.

#### FR-231 - Startup Health Check

The application shall check local data, credential readiness, Usage Access, lock-presentation readiness, global policy, and core service state during startup.

Acceptance criteria:

- A protected state is not shown before required checks complete.
- Failed checks map to a controlled Degraded, Protection interrupted, Action required, or Unknown or not verified state.
- Optional biometric unavailability does not by itself interrupt PIN protection.

#### FR-232 - Dependency Validation

The application shall detect absence or failure of a capability required for its core behavior.

Acceptance criteria:

- Failure of a required protection capability prevents a false protected state.
- Failure of an optional capability, such as eligible biometrics, leaves the secure PIN path available.
- Recovery guidance identifies a user action only when one is actually available.

#### FR-233 - Permission Verification

The application shall verify Usage Access and other required version 1.0.0 operating-system capabilities before enabling protection.

Acceptance criteria:

- Verification occurs during onboarding, startup, return from Android settings, and health checks.
- Missing capability causes a truthful state and guidance.
- The application does not verify or request permissions belonging only to excluded features.

#### FR-234 - Build Version Identification

The application shall expose sufficient version information for support and compatibility decisions.

Acceptance criteria:

- The settings or help interface shows the public version and build identifier.
- Local schema compatibility can be determined during startup and update.
- Sensitive build paths, credentials, or internal environment values are not displayed.

#### FR-235 - Release Validation

Version 1.0.0 shall be accepted only on evidence for the retained functional, security, accessibility, migration, compatibility, and performance requirements.

Acceptance criteria:

- Evidence covers the declared API 30 through 35 phone matrix.
- Excluded features and device classes do not create test obligations.
- Any known limitation affecting the core protection promise is stated accurately before distribution.

#### FR-237 - Safe Default Configuration

Initial and reset configuration shall favor secure, understandable core behavior.

Acceptance criteria:

- PIN is required, no protection session exists, protected applications are unselected, and notification content is privacy preserving.
- Protection remains Unknown or not verified until required capabilities and checks are complete.
- No deferred feature is silently enabled or represented.

#### FR-238 - Configuration Validation

Stored and newly entered configuration shall be validated before use.

Acceptance criteria:

- Unsupported, incomplete, or conflicting values are rejected or replaced by documented safe defaults.
- A validation failure cannot grant protected access.
- Only settings included in version 1.0.0 are accepted.

#### FR-239 - Secure Error Handling

Error handling shall protect secrets and preserve a safe access decision.

Acceptance criteria:

- User messages and local diagnostics contain no PIN, cryptographic key, sensitive path, protected content, or raw database statement.
- An uncertain security result denies access or requires fresh authentication.
- The interface provides an actionable next step where one exists.

#### FR-240 - Graceful Failure

Failure of a noncritical capability shall not unnecessarily disable PIN-based core protection, while failure of a required capability shall be reported truthfully.

Acceptance criteria:

- Optional biometric loss falls back to PIN.
- Nonessential diagnostics or cleanup may fail without granting access.
- Usage Access, policy, local-data, or lock-presentation failure cannot be shown as normal protection.

#### FR-241 - Application State Recovery

After process termination or restart, the application shall restore protected-application selections and global policy while treating authentication sessions according to the mandatory relock rules.

Acceptance criteria:

- Committed configuration is restored consistently.
- No uncertain prior session is treated as valid.
- Recovery completes with a new protection-health verification.

#### FR-242 - Runtime Self-Test

The application shall provide a bounded self-test for authentication readiness, Usage Access, lock presentation, policy loading, core service state, and local data.

Acceptance criteria:

- The self-test does not require Vault, backup, cloud, notification interception, automation, or an Accessibility service.
- Test results match the protection-status screen.
- A passing result cannot override a current failed required capability.

#### FR-243 - Secure Update Compatibility

Supported in-place updates shall preserve valid version 1.x credentials, protected-application selections, and retained settings.

Acceptance criteria:

- The first launch after update validates schema, keys, settings, Usage Access, and policy readiness.
- Migration failure does not silently replace a stricter policy with a weaker one.
- No backup restore, cross-device transfer, or deferred-feature migration is promised.

#### FR-246 - Production Logging Configuration

Distributed builds shall use privacy-safe bounded local logging appropriate to version 1.0.0 diagnostics.

Acceptance criteria:

- Debug detail and sensitive values are suppressed.
- Retained events are limited to those required by FR-220 and FR-276.
- No remote telemetry, user export, or long-term report is enabled.
