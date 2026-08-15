# Non-Functional Requirements

## Version 1.0.0

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
