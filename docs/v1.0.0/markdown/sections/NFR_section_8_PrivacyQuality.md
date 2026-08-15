# Non-Functional Requirements

## Version 1.0.0

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
