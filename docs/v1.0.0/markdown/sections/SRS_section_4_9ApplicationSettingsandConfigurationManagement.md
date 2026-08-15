# Software Requirements Specification

## Version 1.0.0

## Section 9 Application Settings and Configuration Management

#### FR-181 - Application Settings Dashboard

The application shall provide one settings destination for retained version 1.0.0 behavior.

Acceptance criteria:

- Settings are grouped into authentication, session and relock, protection health, privacy, notifications, help, diagnostics, and destructive reset.
- Current values and unavailable states are clear before an action is selected.
- No section exists for Vault, backup, profiles, schedules, automation, intruder features, disguise, notification interception, export, or advanced administration.

#### FR-182 - Security Settings Management

The application shall allow authenticated management of PIN, eligible biometrics, retry behavior explanation, the shared session-duration setting, and global relock behavior.

Acceptance criteria:

- Sensitive changes require current PIN verification.
- Invalid combinations are rejected before they can weaken protection.
- Changes apply consistently to every protected application.

#### FR-183 - Privacy Settings Management

The application shall explain and manage only App Lock notification privacy, protected presentation, screenshot protection, and recent-app privacy.

Acceptance criteria:

- Mandatory authentication privacy cannot be disabled.
- Optional App Lock notification presentation never reveals protected-application activity by default.
- No concealment, intruder, Vault, or notification-interception control appears.

#### FR-184 - Application Default Settings

The application shall maintain one global default configuration for authentication sessions, relock behavior, and App Lock notification privacy.

Acceptance criteria:

- Newly protected applications immediately use the global configuration.
- The application does not prompt for a profile or per-application policy.
- Safe defaults are restored after destructive reset.

#### FR-192 - Factory Reset

The application shall provide an authenticated destructive reset from settings and the destructive forgotten-PIN behavior defined by FR-020.

Acceptance criteria:

- The settings action requires current PIN verification and a separate final confirmation.
- The warning identifies every local data category that will be removed.
- Reset completion leaves no active session or protected state and returns to initial setup.

#### FR-193 - Data Management Controls

The application shall provide only the local data controls needed for bounded diagnostics, cache, and all-data removal.

Acceptance criteria:

- Clearing cache does not remove credentials or protected-application selections.
- Clearing bounded diagnostics removes only diagnostic records.
- All-data removal is treated as destructive reset.
- No Vault, backup, export, archive, or cross-device data control is shown.

#### FR-195 - Configuration Validation

The application shall validate every retained configuration change before applying it.

Acceptance criteria:

- Invalid PIN, session, relock, permission, and capability states are rejected or resolved to safe defaults.
- A failed save leaves the last valid configuration intact.
- Validation cannot create support for an excluded device, feature, or policy type.
