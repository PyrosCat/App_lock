# Non-Functional Requirements

## Version 1.0.0

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
