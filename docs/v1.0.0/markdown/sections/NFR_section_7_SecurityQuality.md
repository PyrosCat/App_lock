# Non-Functional Requirements

## Version 1.0.0

## 7. Security Quality

### NFR-SEC-001 - Security by Design

Security requirements shall be applied to every retained authentication, protection, platform, data, notification, recovery, and update behavior.

Acceptance criteria:

- The delivered behavior has no undocumented path around PIN or session validation.
- Excluded features do not leave dormant permissions, interfaces, or data flows.

Verification: Security assessment and inspection.

### NFR-SEC-002 - Cryptographic Standards

All retained cryptographic operations shall use current platform-supported algorithms, protocols, and key sizes suitable for the protected data.

Acceptance criteria:

- No deprecated or prohibited algorithm is used.
- Approved parameters are verified in the distributed build.
- Cryptographic failure remains fail-secure.

Verification: Analysis, static assessment, and test.

### NFR-SEC-003 - Secure Randomness

Security-sensitive randomness shall be cryptographically secure.

Acceptance criteria:

- Credential salt, key generation, and any retained security token use an approved secure source.
- Non-cryptographic randomness is absent from security decisions.

Verification: Static analysis and security review.

### NFR-SEC-004 - Secure Key Protection

Cryptographic keys shall remain protected throughout generation, use, invalidation, and deletion.

Acceptance criteria:

- No key is hard-coded, logged, exported, or stored as readable application data.
- Platform key invalidation produces a safe recovery state.

Verification: Static analysis, storage inspection, and security test.

### NFR-SEC-005 - Secure Secret Management

Credentials and other sensitive values shall use only approved local protection mechanisms.

Acceptance criteria:

- The distributed package contains no production secret or user credential.
- Diagnostic output contains no PIN, key, or authentication input.
- Version 1.0.0 includes no server token, account secret, or cloud credential.

Verification: Package inspection and static analysis.

### NFR-SEC-006 - Least Privilege

The application shall request and use only capabilities necessary for retained version 1.0.0 behavior.

Acceptance criteria:

- Usage Access and each other required capability has a documented user-facing purpose.
- No App Lock Accessibility service, camera, location, notification-listener, broad storage, account, Bluetooth, or deferred-feature permission is requested.
- Exported application surfaces are minimized.

Verification: Manifest inspection, runtime test, and security review.

### NFR-SEC-007 - Secure Communication

Version 1.0.0 shall not require application network communication.

Acceptance criteria:

- Network inspection identifies no routine application data transmission.
- An external help handoff, if present, transmits no App Lock credential, selection, or diagnostic data.
- Introduction of a network service is outside this release boundary.

Verification: Network inspection and test.

### NFR-SEC-008 - Attack Surface Minimization

The application shall expose no unnecessary service, receiver, activity, provider, deep link, or privileged capability.

Acceptance criteria:

- Every externally reachable surface has a retained version 1.0.0 purpose and appropriate access restriction.
- Deferred feature entry points and permissions are absent from the distributed build.

Verification: Static and dynamic security assessment.

### NFR-SEC-009 - Secure Failure Behavior

An uncertain or failed security decision shall default to requiring fresh authentication or denying protected access.

Acceptance criteria:

- No error, timeout, race, migration failure, or process recreation grants access without a valid decision.
- Protection health never reports normal protection with a failed required capability.

Verification: Negative and fault-injection testing.

### NFR-SEC-010 - Security Logging Quality

Retained local security records shall support immediate diagnosis while protecting sensitive information.

Acceptance criteria:

- Records use consistent time, event, severity, and outcome fields.
- Credentials, biometric data, protected content, keys, and unnecessary application-use detail are absent.
- Retention is fixed and bounded.

Verification: Inspection and test.

### NFR-SEC-011 - Dependency Security

Third-party software used by version 1.0.0 shall have an acceptable security posture.

Acceptance criteria:

- No unresolved Critical vulnerability is present.
- A High-severity vulnerability affecting the distributed application is corrected before public distribution unless the dependency is removed.
- Unused dependencies belonging only to excluded features are absent.

Verification: Dependency analysis and inspection.

### NFR-SEC-014 - Vulnerability Remediation

Discovered vulnerabilities shall be evaluated according to their effect on unauthorized access, credential protection, local-data integrity, and privacy.

Acceptance criteria:

- A Critical vulnerability is corrected before distribution.
- Corrective action includes a regression test for the affected retained behavior.

Verification: Inspection and retest.

### NFR-SEC-015 - Security Regression Prevention

Updates shall not reduce verified security of previously delivered retained behavior.

Acceptance criteria:

- Authentication, session, relock, protection presentation, storage, permission, migration, and privacy regression tests pass.
- Previously corrected security defects are not reintroduced.

Verification: Security regression test.

### NFR-SEC-016 - Secure Build Integrity

Distributed artifacts shall be generated through a controlled, repeatable, and verifiable build.

Acceptance criteria:

- The build can be reproduced from the documented inputs.
- The final artifact is authenticated, version identified, and checked before distribution.
- Debug capability and deferred-feature surfaces are absent.

Verification: Build inspection and artifact verification.

### NFR-SEC-017 - Security Documentation

Security behavior, assumptions, required capabilities, data handling, recovery, and known limitations shall match the delivered version 1.0.0 application.

Acceptance criteria:

- Documentation consistently states Usage Access as the single detection baseline and excludes an App Lock Accessibility service.
- No document claims support for a deferred feature or device class.

Verification: Documentation inspection.
