# Software Requirements Specification

## Version 1.0.0

## Section 12 Administration, Diagnostics, and Maintenance

#### FR-216 - Security Dashboard

The application shall provide a concise protection-status screen rather than a score or analytics dashboard.

Acceptance criteria:

- The screen shows credential readiness, protected-application count, Usage Access, lock-presentation readiness, core service state, local-data integrity, and current protection state.
- Each non-ready item provides a direct explanation or recovery action.
- Profiles, backup, Vault, event trends, and risk scores are absent.

#### FR-217 - Security Health Assessment

The application shall evaluate whether the complete version 1.0.0 protection path is ready.

Acceptance criteria:

- The same inputs always produce the same controlled state.
- A failed required input cannot be outweighed by unrelated healthy inputs.
- The result contains no backup, automation, Vault, root, cloud, or intruder criterion.

#### FR-218 - Permission Monitoring

The application shall monitor Usage Access and any other capability required to present or communicate core protection.

Acceptance criteria:

- Required capability changes are reflected at startup and after returning from Android settings.
- Loss of a required capability updates status and notification content promptly.
- Camera, location, protected-application notification access, and an App Lock Accessibility service are not monitored or requested.

#### FR-219 - System Diagnostic Scan

The application shall provide an on-device check of required capabilities, credential readiness, policy loading, core service status, and local-data integrity.

Acceptance criteria:

- Results are presented as pass, degraded, failed, or not verified with plain-language explanation.
- Running the scan does not change settings or grant a session.
- Results cannot be exported and contain no secrets or protected content.

#### FR-220 - Application Event Logging

The application shall keep a bounded local diagnostic record for startup, required-capability change, protection-service change, lock-presentation failure, authentication delay state, data error, and recovery attempt.

Acceptance criteria:

- The record contains only the minimum context needed to explain the current issue.
- Retention is fixed and automatically bounded.
- No event-history screen, usage analytics, report, or export is provided.

#### FR-221 - Error Detection and Reporting

The application shall detect core errors and provide a safe recovery action when one is available.

Acceptance criteria:

- Messages describe the user-visible effect without internal technical detail.
- Errors never reveal credentials, cryptographic material, protected content, or storage locations.
- When recovery is unavailable, the application remains fail-secure and explains destructive reset if applicable.

#### FR-224 - Application Repair Function

The application shall provide limited repair actions for core protection readiness.

Acceptance criteria:

- Available actions may retry core initialization, recheck Usage Access and lock presentation, restore safe invalid settings, or clear temporary cache.
- Repair does not fabricate missing credentials, recover deleted data, or restore from backup.
- Completion runs a new health verification and reports the actual result.
