**Test Specification (TS)**

**Volume I — Test Strategy & Governance**

**Section 10 — Test Data Management**

**10.1 Purpose**

This section defines the strategy for creating, controlling, protecting, using, resetting, retaining, and destroying test data throughout the Android App Lock project.

Test data shall support functional, non-functional, security, recovery, migration, compatibility, and release testing while maintaining isolation from production data and preserving reproducibility.

Test data management shall be treated as part of the controlled test environment rather than as an informal testing activity.

**10.2 Test Data Objectives**

Test data shall:

- Provide realistic verification scenarios.

- Support positive and negative testing.

- Exercise boundary conditions.

- Support repeatable test execution.

- Support security testing.

- Support database testing.

- Support migration testing.

- Support backup and restore testing.

- Support failure and recovery testing.

- Prevent exposure of production information.

- Permit controlled reset between tests.

- Maintain traceability to applicable test cases.

**10.3 Test Data Classification**

Test data shall be classified according to its sensitivity and purpose.

| **Classification** | **Description** |
|----|----|
| Synthetic | Artificially generated data with no production origin |
| Derived | Data transformed from another test dataset |
| Sanitized | Data derived from production or realistic sources after approved sanitization |
| Security Test Data | Data specifically designed to test security controls |
| Boundary Data | Data designed to test minimum, maximum, and limit conditions |
| Corrupted Data | Intentionally malformed or damaged data |
| Migration Data | Data representing previous application versions |
| Recovery Data | Data used to verify backup and recovery |
| Performance Data | Data designed to evaluate resource and performance behavior |

Synthetic data shall be preferred whenever practical.

**10.4 Production Data Prohibition**

Production user data shall not be used as test data by default.

Production data may only be introduced into a test environment when there is a documented and approved requirement that cannot reasonably be satisfied using synthetic or sanitized data.

If production-derived information is authorized for testing, it shall be:

- Minimized

- Sanitized

- Access controlled

- Documented

- Isolated

- Securely destroyed when no longer required

**10.5 Synthetic Data**

Synthetic data shall be the primary source for normal testing.

Synthetic datasets shall be capable of representing:

- Normal users

- Multiple profiles

- Protected applications

- Vault contents

- Authentication configurations

- Scheduling rules

- Automation rules

- Notifications

- Backup records

- Database states

- Error conditions

Synthetic data shall not contain real credentials, private keys, production tokens, or other production secrets.

**10.6 Functional Test Data**

Functional test data shall represent normal application usage.

Examples include:

- Valid authentication credentials

- Protected application configurations

- User profiles

- Valid schedules

- Valid automation rules

- Vault records

- Notification configurations

- Backup configurations

Functional datasets shall support repeatable execution of core user workflows.

**10.7 Negative Test Data**

Negative datasets shall intentionally contain invalid or unsupported values.

Examples include:

- Invalid credentials

- Empty required fields

- Malformed configuration

- Invalid schedule ranges

- Duplicate records

- Invalid application identifiers

- Missing database values

- Unsupported settings

The expected result shall be defined before execution.

**10.8 Boundary Test Data**

Boundary datasets shall exercise values at and around defined limits.

For each applicable limit, testing should include:

Below Minimum

│

▼

Minimum

│

▼

Valid Interior

│

▼

Maximum

│

▼

Above Maximum

Boundary datasets shall be maintained for requirements containing explicit numerical or capacity constraints.

**10.9 Security Test Data**

Security testing shall use dedicated datasets designed to evaluate security controls.

Examples include:

- Invalid credentials

- Repeated authentication failures

- Unauthorized identities

- Malformed intents

- Unexpected configuration

- Tampered data

- Corrupted encrypted data

- Invalid backup files

- Expired sessions

- Invalid authorization states

Security test data shall never contain real authentication credentials or production secrets.

**10.10 Authentication Test Data**

Authentication testing shall include controlled accounts representing different states.

Examples include:

- Newly initialized account

- Valid authenticated account

- Invalid credential state

- Locked account

- Expired session

- Failed authentication state

- Recovery state

- Authentication reset state

Authentication test data shall be isolated from production identity systems.

**10.11 Application Protection Test Data**

Protected-application datasets shall include representative application configurations.

Testing shall cover:

- No protected applications

- One protected application

- Multiple protected applications

- Large protected-application sets

- Added application

- Removed application

- Duplicate configuration attempts

- Invalid application identifiers

**10.12 Scheduling Test Data**

Scheduling datasets shall include:

- Valid schedules

- Overlapping schedules

- Adjacent schedules

- Empty schedules

- Maximum-duration schedules

- Minimum-duration schedules

- Invalid schedules

- Time-zone transitions

- Daylight Saving Time transitions

Testing shall verify that schedule data produces deterministic and secure results.

**10.13 Automation Test Data**

Automation datasets shall represent combinations of:

- Trigger conditions

- Actions

- Rules

- Priorities

- Conflicts

- Dependencies

- Enabled states

- Disabled states

Testing shall include conflicting rules and unexpected event sequences.

**10.14 Vault Test Data**

Vault datasets shall include:

- Empty vault

- Small vault

- Large vault

- Valid records

- Invalid records

- Deleted records

- Corrupted records

- Encrypted records

- Duplicate records

Testing shall verify confidentiality, integrity, storage behavior, and recovery.

**10.15 Database Test Data**

Database datasets shall support:

- Empty database

- Normal database

- Large database

- Boundary database

- Invalid records

- Duplicate records

- Orphaned records

- Migration datasets

- Corrupted datasets

Database test data shall be versioned where migration or compatibility testing requires a known historical state.

**10.16 Migration Test Data**

Migration datasets shall represent supported previous database and application states.

Datasets shall include:

- Current valid schema

- Previous schema

- Multiple historical versions where supported

- Empty legacy database

- Populated legacy database

- Large legacy database

- Invalid legacy records

- Partially corrupted legacy data

Migration testing shall verify preservation of required information and enforcement of current security requirements.

**10.17 Backup and Restore Data**

Backup datasets shall represent realistic application states.

Testing shall include:

- Empty backup

- Normal backup

- Large backup

- Valid encrypted backup

- Corrupted backup

- Incomplete backup

- Unsupported backup version

- Tampered backup

Restore testing shall verify that restored data does not weaken authentication, authorization, encryption, or other security controls.

**10.18 Corrupted Data**

Corrupted datasets shall be intentionally generated for resilience testing.

Examples include:

- Truncated files

- Invalid database records

- Corrupted encrypted data

- Invalid serialized objects

- Malformed configuration

- Missing fields

- Invalid references

The application shall respond safely and predictably.

**10.19 Performance Test Data**

Performance testing shall use datasets large enough to meaningfully exercise resource behavior.

Examples include:

- Large application lists

- Large vault datasets

- Large schedule sets

- Large automation-rule sets

- Large notification histories

- Large databases

Performance datasets shall be documented so that benchmark results remain comparable across releases.

**10.20 Test Data Generation**

Test data may be generated through:

- Automated generators

- Database fixtures

- Factory methods

- Seed scripts

- Static controlled datasets

- Test utilities

Generated data shall be deterministic where reproducibility is required.

Randomized generation shall record the seed or equivalent information necessary to reproduce a failure.

**10.21 Test Data Version Control**

Important test datasets shall be version controlled.

Version control shall be used for:

- Database fixtures

- Migration datasets

- Security test payloads

- Boundary datasets

- Performance datasets

- Configuration fixtures

- Expected-result datasets

Changes to controlled datasets shall be reviewed when they may alter test results.

**10.22 Test Data Reset**

Tests shall be capable of returning the application to a known state.

Reset mechanisms may include:

- Database recreation

- Database transaction rollback

- Application-data reset

- Test fixture restoration

- Emulator/device reset

- Controlled backup restoration

The appropriate reset mechanism shall be selected according to the test level and scenario.

**10.23 Test Isolation**

Individual tests shall avoid unintended dependence on state created by other tests.

Where practical:

- Tests shall initialize required data.

- Tests shall clean up temporary data.

- Shared mutable datasets shall be minimized.

- Test execution order shall not determine results.

- Parallel tests shall use isolated datasets.

A test that depends on undocumented previous state shall not be considered reliably repeatable.

**10.24 Test Data Security**

Test data shall be protected according to its sensitivity.

Security controls shall apply to:

- Authentication data

- Encryption material

- Security test payloads

- Backup datasets

- Logs containing test information

- Device storage

- CI artifacts

Test data shall not accidentally become a source of credentials or secrets.

**10.25 Secrets and Cryptographic Material**

Test environments shall use dedicated test cryptographic material.

Test keys shall:

- Never be production keys.

- Be clearly identified as test material.

- Be isolated from production infrastructure.

- Be replaceable.

- Be destroyed when required.

Private keys and credentials shall not be committed to source control.

**10.26 Test Data and Logging**

Application logs generated during testing shall be reviewed to ensure that test data is not unnecessarily exposed.

Sensitive information shall not be logged in plaintext unless explicitly required for a controlled security test.

Testing shall verify both:

- Required diagnostic information is available.

- Sensitive information is appropriately protected.

**10.27 Test Data Retention**

Test data shall be retained only for as long as necessary.

Retention periods shall consider:

- Reproducibility

- Active test campaigns

- Defect investigation

- Release evidence

- Migration testing

- Security investigation

- Regulatory or project requirements

Unnecessary test data shall be removed.

**10.28 Test Data Destruction**

Test data shall be securely destroyed when no longer required.

Destruction shall include, where applicable:

- Test credentials

- Authentication records

- Encryption keys

- Backup datasets

- Temporary files

- Security testing artifacts

- Sensitive logs

The destruction method shall be appropriate to the sensitivity of the data.

**10.29 Test Data and CI/CD**

Automated pipelines shall use controlled test datasets.

CI/CD test data shall:

- Be reproducible.

- Avoid production information.

- Be automatically provisioned where practical.

- Be isolated between executions where required.

- Be cleaned up after execution when appropriate.

Automated tests shall not depend on manually created persistent state.

**10.30 Test Data and Phase Strategy**

Test data requirements shall evolve with the Implementation Strategy.

| **Phase** | **Primary Test Data** |
|----|----|
| Phase 0 — Foundation | Build and CI validation data |
| Phase 1 — Core Security | Authentication, encryption, security-state data |
| Phase 2 — Core Features | Users, protected apps, vaults, settings, notifications |
| Phase 3 — Automation | Schedules, triggers, rules, conflicts |
| Phase 4 — Production Hardening | Large datasets, recovery states, performance datasets |
| Phase 5 — Security Hardening | Adversarial, corrupted, tampered, compatibility datasets |
| Phase 6 — Release Readiness | Full representative release datasets |

Test data shall be expanded as functionality and risk increase.

**10.31 Test Data Traceability**

Controlled test datasets shall be traceable to the tests that use them.

Traceability should identify:

- Dataset identifier

- Dataset version

- Test case

- Requirement

- Test environment

- Application version

This allows test failures to be reproduced using the same data configuration.

**10.32 Test Data Defects**

Test data defects shall be treated separately from application defects.

A test-data defect exists when:

- Expected data is incorrect.

- Dataset is incomplete.

- Fixture is inconsistent.

- Migration dataset does not represent its intended version.

- Test data produces an invalid test condition unintentionally.

Test-data defects shall be corrected before affected test results are used as release evidence.

**10.33 Test Data Review**

Controlled test datasets shall be reviewed when:

- Requirements change

- Database schema changes

- Security requirements change

- Test cases change

- New boundaries are introduced

- New supported Android versions are added

- Defects reveal missing scenarios

Review shall determine whether existing datasets remain valid.

**10.34 Summary**

Test data is a controlled component of the Android App Lock verification system. The project shall primarily use synthetic, deterministic, and isolated data while providing specialized datasets for security, boundaries, migration, recovery, performance, and adversarial testing.

Test data shall evolve with the Implementation Strategy, beginning with basic engineering and security datasets and progressing toward comprehensive production-scale and adversarial datasets during hardening and release qualification.

Proper management of test data ensures that test results remain **repeatable, secure, representative, and defensible**, while preventing production information or secrets from entering development and testing environments.
