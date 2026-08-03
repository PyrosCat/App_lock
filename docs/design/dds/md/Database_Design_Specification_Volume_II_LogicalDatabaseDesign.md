**Database Design Specification (DDS)**

**Volume II — Logical Database Design**

**Document Version:** 1.0

**Table of Contents**

6.  Entity Catalog

7.  Entity Relationships

8.  Logical Schema

9.  Keys & Constraints

10. Referential Integrity

**6. Entity Catalog**

**6.1 Purpose**

The logical database model organizes persistent application data into cohesive domains aligned with the application's functional architecture. Each entity has a single, well-defined responsibility and ownership boundary.

The logical model remains implementation-independent. Physical table layouts, indexes, storage optimizations, and migration scripts are defined in Volume III.

**6.2 Design Principles**

All entities shall adhere to the following principles:

- Single Responsibility

- High Cohesion

- Low Coupling

- Normalized data model

- Explicit ownership

- Minimal duplication

- Traceability to SRS requirements

- Security classification

- Lifecycle management

**6.3 Entity Domains**

| **Domain**             | **Purpose**                                  |
|------------------------|----------------------------------------------|
| Configuration          | User and application configuration           |
| Authentication         | Authentication policies and session metadata |
| Protected Applications | Locked application management                |
| Secure Vault           | Vault metadata and organization              |
| Scheduling             | Time-based automation                        |
| Automation             | Context-aware locking rules                  |
| Notifications          | Notification configuration                   |
| Security               | Security policy configuration                |
| Diagnostics            | Operational diagnostics                      |
| Observability          | Metrics and audit events                     |

## **6.4 Entity Catalog**

### **Configuration Domain**

#### ApplicationSettings

Purpose

Stores global application configuration.

Typical Attributes

- Theme

- Language

- Startup behavior

- Accessibility preferences

- Display options

- Backup preferences

Security Classification

Level 2

Owner

Settings Module

#### UserPreferences

Purpose

Stores user-configurable operational preferences.

Examples

- Lock delay

- Animation settings

- Notification behavior

- Haptic feedback

- UI customization

Security Classification

Level 2

### Authentication Domain

#### AuthenticationProfile

Purpose

Defines enabled authentication mechanisms.

Typical Attributes

- PIN enabled

- Password enabled

- Pattern enabled

- Biometric enabled

- Multi-factor configuration

Classification

Level 3

#### SessionState

Purpose

Tracks authenticated application sessions.

Contains

- Session identifier

- Creation timestamp

- Expiration

- Last activity

- Session status

Classification

Level 3

#### RecoveryConfiguration

Purpose

Defines recovery and fallback authentication options.

Classification

Level 4

### Protected Applications Domain

#### ProtectedApplication

Purpose

Represents an installed application managed by the lock engine.

Typical Attributes

- Package identifier

- Display name

- Protection enabled

- Category

- Rule assignments

Classification

Level 3

#### ApplicationGroup

Purpose

Logical grouping of protected applications.

Examples

- Banking

- Social

- Work

- Personal

- Custom Groups

Classification

Level 2

#### ApplicationGroupMember

Purpose

Associates applications with groups.

Classification

Level 2

### Secure Vault Domain

#### VaultItem

Purpose

Represents encrypted vault metadata.

The entity stores metadata only.

Sensitive content remains encrypted.

Classification

Level 4

#### VaultCategory

Purpose

Organizes vault entries.

Examples

- Passwords

- Notes

- Documents

- Images

Classification

Level 3

#### VaultTag

Purpose

Logical organization through tagging.

Classification

Level 2

#### VaultAttachment

Purpose

Metadata describing encrypted attachments.

Classification

Level 4

### Scheduling Domain

#### ScheduleProfile

Purpose

Defines reusable lock schedules.

Examples

- Work Hours

- School

- Night

- Weekend

Classification

Level 2

#### ScheduleRule

Purpose

Individual scheduling rules.

Contains

- Start time

- End time

- Recurrence

- Exceptions

Classification

Level 2

### **Automation Domain**

#### AutomationRule

Purpose

Defines context-sensitive locking behavior.

Examples

- Wi-Fi

- Bluetooth

- Charging

- Device state

- Time

- Geofence

Classification

Level 3

#### AutomationCondition

Purpose

Represents individual trigger conditions.

Classification

Level 3

#### AutomationAction

Purpose

Represents actions executed when rule conditions are satisfied.

Classification

Level 2

### **Notification Domain**

#### NotificationProfile

Purpose

Stores notification preferences.

Examples

- Silent mode

- Visibility

- Reminder intervals

- Priority

Classification

Level 2

### **Security Domain**

#### SecurityPolicy

Purpose

Defines active security policies.

Examples

- Lock timeout

- Failed attempt thresholds

- Root restrictions

- Debug restrictions

Classification

Level 3

#### SecurityEvent

Purpose

Stores security-relevant audit events.

Examples

- Authentication failures

- Policy violations

- Tamper detection

- Root detection

Classification

Level 3

### **Diagnostics Domain**

#### DiagnosticRecord

Purpose

Stores operational diagnostic information.

Examples

- Error identifiers

- Component name

- Timestamp

- Severity

Sensitive user information shall never be recorded.

Classification

Level 2

### **Observability Domain**

#### PerformanceMetric

Purpose

Stores performance measurements.

Examples

- Startup duration

- Lock latency

- Database latency

- Resource utilization

Classification

Level 2

**7. Entity Relationships**

**7.1 Relationship Philosophy**

Relationships are designed to:

- Preserve integrity

- Minimize duplication

- Support modular evolution

- Reduce coupling

- Enable efficient querying

**7.2 High-Level Relationship Diagram**

AuthenticationProfile

│

│

SessionState

ApplicationGroup

│

│

ApplicationGroupMember

│

│

ProtectedApplication

│

┌──────┴────────┐

│ │

│ │

ScheduleProfile AutomationRule

│ │

│ │

ScheduleRule AutomationCondition

│

│

AutomationAction

VaultCategory

│

│

VaultItem

│ │

│ │

Tag Attachment

SecurityPolicy

│

│

SecurityEvent

ApplicationSettings

│

│

UserPreferences

**7.3 Relationship Rules**

A Protected Application:

- may belong to multiple groups.

- may reference one or more schedules.

- may reference multiple automation rules.

A Schedule Profile:

- contains one or more Schedule Rules.

A Vault Category:

- contains multiple Vault Items.

Automation Rules:

- contain one or more Conditions.

- contain one or more Actions.

Security Policies:

- generate Security Events.

**8. Logical Schema**

**8.1 Schema Organization**

The logical schema separates persistent information into independent functional domains.

Each entity shall have:

- Unique identifier

- Ownership definition

- Security classification

- Lifecycle definition

- Validation rules

**8.2 Naming Standards**

Entities

PascalCase

Example

ProtectedApplication

Logical Attributes

camelCase

Example

lockTimeout

Identifiers

Singular

Example

applicationId

Relationship entities

Descriptive

Example

ApplicationGroupMember

**8.3 Common Entity Characteristics**

Every persistent entity should support:

- Identifier

- Creation timestamp

- Last modification timestamp

- Version information

- Status

- Ownership metadata

Soft deletion shall only be used where recovery requirements justify additional storage complexity.

**8.4 Domain Isolation**

Entities shall not directly access data outside their domain boundaries.

Cross-domain communication occurs through repositories and application services.

**8.5 Logical Lifecycle States**

Typical lifecycle:

Created

↓

Validated

↓

Active

↓

Modified

↓

Archived

↓

Removed

Not every entity requires all lifecycle states.

**9. Keys & Constraints**

**9.1 Primary Keys**

Each logical entity shall possess a single immutable primary identifier.

Primary identifiers:

- uniquely identify records.

- shall never be reused.

- remain stable throughout the entity lifecycle.

**9.2 Foreign Keys**

Relationships between entities shall use explicit foreign keys.

Foreign keys shall:

- preserve integrity.

- prevent orphan records.

- enforce ownership.

**9.3 Alternate Keys**

Alternate identifiers may exist where natural uniqueness is required.

Examples

- Package identifier

- Vault UUID

- Authentication profile name

**9.4 Unique Constraints**

Unique constraints shall prevent duplicate logical objects including:

- Package identifiers

- Schedule names

- Security policy identifiers

- Vault identifiers

**9.5 Validation Constraints**

Logical validation shall enforce:

- Required values

- Valid ranges

- Enumerated values

- Length restrictions

- Temporal consistency

Business validation remains outside the database whenever practical.

**9.6 Status Constraints**

Entity state transitions shall follow documented lifecycle rules.

Invalid transitions shall be rejected before persistence.

**10. Referential Integrity**

**10.1 Objectives**

Referential integrity ensures:

- Consistent relationships

- No orphan records

- Predictable deletion behavior

- Reliable recovery

- Data quality

**10.2 Parent–Child Relationships**

Parent entities own dependent entities.

Deletion policies shall be explicitly defined for each relationship.

Supported behaviors include:

- Cascade delete

- Restrict delete

- Nullify reference

- Archive dependency

Selection shall be based upon business requirements rather than implementation convenience.

**10.3 Integrity Validation**

Integrity verification shall occur during:

- Entity creation

- Updates

- Deletions

- Schema migrations

- Backup restoration

**10.4 Cross-Domain References**

Cross-domain references shall be minimized.

Where required, dependencies shall remain:

- Explicit

- Documented

- Traceable

- Version compatible

**10.5 Recovery Integrity**

Recovery procedures shall restore:

- Entity relationships

- Constraints

- Ownership

- Version compatibility

Partial restoration shall never produce logically inconsistent data.

**10.6 Migration Integrity**

Schema evolution shall preserve:

- Primary identifiers

- Foreign key relationships

- Constraint consistency

- Entity ownership

- Historical compatibility

Migration failures shall be atomic and recoverable.

**Volume II Summary**

Volume II defines the logical data model by establishing the application's entity catalog, domain boundaries, relationships, logical schema organization, key strategy, and referential integrity rules. It provides the conceptual blueprint for persistent data management while remaining independent of any specific database engine or physical implementation.
