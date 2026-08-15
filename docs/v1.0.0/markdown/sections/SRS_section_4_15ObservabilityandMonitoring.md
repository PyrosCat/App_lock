# Software Requirements Specification

## Version 1.0.0

## Section 15 Observability and Monitoring

#### FR-276 - Structured Logging

The application shall produce a small, consistent set of privacy-safe local diagnostic records for retained core events.

Acceptance criteria:

- Each record contains a time, event type, severity, outcome, and only the minimum non-sensitive context.
- Event names and meanings remain consistent within compatible version 1.x releases.
- No PIN, biometric data, protected content, cryptographic material, or unnecessary application-use detail is recorded.

#### FR-279 - Service Health Monitoring

The application shall monitor the readiness of Usage Access, core monitoring, lock presentation, global policy, and any Android-required ongoing service.

Acceptance criteria:

- A failed required service changes the consolidated protection state.
- A passing state is restored only after a successful recheck.
- Notification interception, Vault, backup, automation, and remote services are not monitored.

#### FR-280 - Health Status Reporting

The application shall report health through the controlled states Protected, Degraded, Protection interrupted, Action required, and Unknown or not verified.

Acceptance criteria:

- Each state has one consistent meaning across the dashboard, notifications, onboarding, and recovery.
- The message explains effect and next action without internal technical detail.
- Conflicting component results resolve to the safer applicable state.

#### FR-283 - Log Rotation

Local diagnostic records shall remain within a fixed storage and age bound.

Acceptance criteria:

- Old eligible records are removed automatically without user scheduling.
- Rotation does not remove current retry enforcement or active failure state.
- The application exposes no configurable archive or retention manager.

#### FR-289 - Security Event Monitoring

The application shall detect repeated authentication failure, loss of a required capability, core service interruption, and local-data integrity failure.

Acceptance criteria:

- Each retained event updates enforcement or health state as applicable.
- Event handling remains local and privacy safe.
- Root detection, intruder capture, remote events, and Vault integrity events are absent.

#### FR-290 - Notification of Critical Events

The application shall issue a privacy-preserving notification when user action is required to restore the core protection promise.

Acceptance criteria:

- Notification severity matches the current protection state.
- The content does not reveal protected-application identity or authentication history.
- Opening the notification leads to the relevant status or recovery screen.

#### FR-293 - Integrity Monitoring

The application shall verify integrity of credential records, global policy, protected-application selections, and local database metadata at defined safety points.

Acceptance criteria:

- Checks occur before relying on stored security state after startup, migration, or recovery.
- A failed check cannot result in access being granted.
- No Vault, backup, cloud, or intruder-media integrity check exists.

#### FR-294 - Diagnostic Self-Test

The application shall provide the authenticated user with an on-device self-test of the retained protection path.

Acceptance criteria:

- Test coverage matches FR-219 and does not create a separate analytics system.
- Results identify pass, degraded, failed, or not verified and provide a safe next action.
- Results remain local and are not shareable or exportable.

#### FR-296 - Exception Monitoring

The application shall retain limited local context for an unexpected core failure and the recovery outcome.

Acceptance criteria:

- Context identifies the affected functional area and operation without secrets, protected content, or internal storage detail.
- Repeated failures can be distinguished within the bounded retention period.
- No remote crash reporting or diagnostic export is required.
