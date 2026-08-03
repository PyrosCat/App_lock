**Database Design Specification (DDS)**

**Volume III — Physical Design**

**Document Version:** 1.0

**Table of Contents**

11. Physical Schema

12. Index Strategy

13. Query Optimization

14. Storage Layout

15. Database Security

**11. Physical Schema**

**11.1 Purpose**

This volume defines the physical implementation characteristics of the application's persistent storage. While Volume II established the logical data model, this volume specifies how that model is realized within the embedded database.

The physical design emphasizes security, performance, maintainability, and long-term schema evolution while remaining consistent with the architecture defined in the TAS and SDS.

**11.2 Physical Database Architecture**

The application shall use a single encrypted embedded relational database for structured application data.

Characteristics include:

- ACID-compliant transactions

- Local-only storage

- Private application sandbox

- Encrypted database files

- Version-controlled schema

- Migration support

- Referential integrity enforcement

The database shall remain entirely local to the device unless an explicitly authorized backup or export operation is performed.

**11.3 Schema Organization**

The physical schema is organized by functional domain.

| **Domain**             | **Primary Purpose**                  |
|------------------------|--------------------------------------|
| Configuration          | Application settings and preferences |
| Authentication         | Authentication profiles and sessions |
| Protected Applications | Locked application metadata          |
| Secure Vault           | Vault metadata and organization      |
| Scheduling             | Time-based rules                     |
| Automation             | Context-aware policies               |
| Notifications          | Notification configuration           |
| Security               | Policies and audit events            |
| Diagnostics            | Operational diagnostics              |
| Observability          | Performance metrics                  |

Each domain shall maintain clearly defined ownership boundaries.

**11.4 Table Design Standards**

Every table should follow a consistent structure.

Typical columns include:

- Primary Identifier

- Version

- Status

- Created Timestamp

- Updated Timestamp

- Last Access Timestamp (where applicable)

Sensitive timestamps shall only be retained when operationally required.

**11.5 Data Types**

Column types shall prioritize:

- Predictable storage size

- Platform compatibility

- Efficient indexing

- Forward compatibility

General guidance:

| **Data**       | **Preferred Type**                        |
|----------------|-------------------------------------------|
| Identifier     | Integer or UUID                           |
| Boolean        | Boolean                                   |
| Time           | UTC Timestamp                             |
| Duration       | Integer                                   |
| Enumeration    | Integer or Text Enumeration               |
| Encrypted Data | Binary Blob                               |
| Configuration  | Structured Text or JSON where appropriate |

**11.6 Versioning**

Each schema revision shall possess:

- Schema Version

- Migration Version

- Compatibility Version

Application startup shall validate compatibility before database access.

**11.7 Schema Evolution**

Schema changes shall:

- Preserve existing data

- Be backward compatible when feasible

- Execute atomically

- Support rollback where practical

- Be fully traceable through migration records

**12. Index Strategy**

**12.1 Objectives**

Indexes improve retrieval performance while minimizing storage overhead and write amplification.

Indexes shall be created only when justified by measurable operational requirements.

**12.2 Index Design Principles**

Indexes shall prioritize:

- Frequently queried data

- Authentication lookups

- Protected application retrieval

- Schedule evaluation

- Automation rule execution

- Security event retrieval

Unused or redundant indexes shall be removed during maintenance.

**12.3 Primary Indexes**

Each table shall possess a clustered or primary index based on its immutable primary identifier.

Primary indexes shall:

- Guarantee uniqueness

- Support efficient retrieval

- Remain immutable

**12.4 Secondary Indexes**

Secondary indexes may support:

- Package identifiers

- Vault identifiers

- Rule identifiers

- Schedule identifiers

- Security event timestamps

- Notification identifiers

Selection shall be based on observed query patterns.

**12.5 Composite Indexes**

Composite indexes shall be considered when queries consistently filter using multiple columns.

Examples include:

- Package + Status

- Schedule + Time Range

- Security Event + Timestamp

- Automation Rule + Priority

Column ordering shall follow expected query selectivity.

**12.6 Unique Indexes**

Unique indexes shall enforce logical uniqueness for:

- Application package names

- Vault identifiers

- Security policy names

- Schedule profile names

- Authentication profile names

**12.7 Index Maintenance**

Indexes shall be periodically evaluated for:

- Fragmentation

- Redundancy

- Query utilization

- Storage overhead

Schema migrations shall preserve index consistency.

**13. Query Optimization**

**13.1 Objectives**

Query performance shall support responsive application behavior while minimizing CPU, memory, storage, and battery consumption.

Optimization shall prioritize predictable latency over maximum throughput.

**13.2 Query Design Principles**

Queries shall:

- Retrieve only required columns

- Limit returned rows

- Avoid unnecessary joins

- Use indexed predicates

- Support deterministic execution plans

**13.3 Read Optimization**

Read-heavy operations include:

- Lock engine evaluation

- Authentication verification

- Schedule lookup

- Automation rule evaluation

- Settings retrieval

These operations shall be optimized for low latency.

**13.4 Write Optimization**

Write operations shall:

- Use transactions

- Minimize lock duration

- Batch related updates

- Avoid unnecessary writes

- Preserve durability

**13.5 Background Processing**

Long-running database operations shall execute outside the UI thread.

Examples include:

- Database maintenance

- Backup preparation

- Migration execution

- Diagnostic cleanup

- Metrics aggregation

**13.6 Large Object Strategy**

Large encrypted objects shall not be embedded directly within high-frequency transactional tables unless justified.

Instead:

- Store metadata separately

- Maintain lightweight references

- Optimize retrieval paths

- Minimize page fragmentation

**13.7 Performance Monitoring**

Database performance metrics shall include:

- Query latency

- Transaction duration

- Lock contention

- Database size

- Index utilization

- Migration duration

- Corruption detection events

Collected metrics shall never expose user-sensitive information.

**14. Storage Layout**

**14.1 Storage Organization**

Persistent data shall be separated according to sensitivity and operational characteristics.

Logical categories include:

- Structured relational data

- Encrypted secrets

- Temporary files

- Cache

- Backup artifacts

- Diagnostic data

**14.2 Database Files**

The primary database shall reside within the application's private internal storage.

Access shall be restricted by:

- Android sandbox

- File permissions

- Database encryption

- Application process isolation

**14.3 Temporary Storage**

Temporary data shall:

- Have defined expiration

- Be securely removed

- Never contain unencrypted secrets

- Be excluded from backups

**14.4 Cache Storage**

Cache shall contain only reproducible information.

Cache contents shall:

- Be disposable

- Have bounded size

- Support automatic cleanup

- Never contain authentication secrets

**14.5 Backup Artifacts**

Backup packages shall:

- Be explicitly generated

- Use authenticated encryption

- Include integrity verification

- Record backup metadata

- Support version compatibility

Cryptographic keys shall never be included within backup artifacts.

**14.6 Storage Quotas**

The application shall monitor storage consumption.

Thresholds shall trigger:

- Cleanup operations

- Diagnostic logging

- User notifications when appropriate

- Cache reduction

Critical functionality shall not depend upon unlimited storage availability.

**14.7 Storage Maintenance**

Routine maintenance includes:

- Integrity verification

- Expired record cleanup

- Cache eviction

- Diagnostic pruning

- Performance optimization

Maintenance operations shall be non-disruptive whenever possible.

**15. Database Security**

**15.1 Security Objectives**

Database security shall preserve:

- Confidentiality

- Integrity

- Availability

- Authenticity

- Accountability

Protection shall continue throughout the complete data lifecycle.

**15.2 Encryption**

Sensitive data shall be encrypted before persistence.

Encryption responsibilities include:

- Database encryption

- Sensitive field encryption

- Secure key derivation

- Authenticated encryption

- Key rotation support

Cryptographic keys shall be protected by the Android Keystore whenever supported.

**15.3 Access Control**

Database access shall occur exclusively through approved persistence services.

Direct access from:

- UI components

- Background workers

- External libraries

- Third-party SDKs

shall be prohibited unless explicitly authorized by the architecture.

**15.4 Input Validation**

All database inputs shall be validated before persistence.

Validation includes:

- Data type validation

- Length validation

- Range validation

- Enumeration validation

- Referential validation

- Business rule validation

Parameterized statements shall be used for all database operations.

**15.5 Integrity Protection**

Integrity mechanisms include:

- Referential constraints

- Transaction boundaries

- Version tracking

- Corruption detection

- Migration validation

Integrity failures shall trigger appropriate recovery procedures.

**15.6 Audit Logging**

Security-relevant database events may be audited.

Examples include:

- Schema migration

- Integrity failures

- Recovery operations

- Backup generation

- Restore operations

Audit logs shall exclude:

- Authentication secrets

- Vault contents

- Encryption keys

- Personally identifiable information

- Sensitive application metadata beyond what is operationally necessary

**15.7 Secure Deletion**

When data reaches the end of its lifecycle:

- Logical references shall be removed.

- Temporary copies shall be destroyed.

- Cache entries shall be invalidated.

- Backup retention policies shall be applied.

- Cryptographic keys associated with encrypted content shall be revoked or destroyed where applicable, rendering remaining ciphertext unrecoverable.

Secure deletion behavior shall align with Android storage capabilities and documented retention policies.

**15.8 Security Monitoring**

The persistence layer shall support monitoring of:

- Unauthorized access attempts

- Corruption events

- Migration failures

- Backup failures

- Integrity violations

- Unexpected database growth

- Storage exhaustion

Monitoring data shall integrate with the application's observability framework without exposing confidential information.

**Volume III Summary**

Volume III specifies the physical realization of the database architecture, including schema organization, indexing strategy, query optimization, storage layout, and database security controls. It establishes the implementation constraints necessary to achieve the security, performance, and maintainability objectives defined by the SRS, NFR, TAS, and SDS while remaining independent of any specific implementation framework beyond the selected embedded relational database.
