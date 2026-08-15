# Non-Functional Requirements

## Version 1.0.0

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
