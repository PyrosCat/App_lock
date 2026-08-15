# Database Design Specification

## Version 1.0.0

## 7. Logical Relationships

### 7.1 Relationship model

The supported relational model has no parent-child relationship and no cross-domain foreign key.

Each protected application record is independent and uniquely identified by its package identifier. Settings and protected authentication records are stored outside the relational database and do not reference a protected application row.

### 7.2 Runtime association

At runtime, protection associates:

- a foreground package identifier reported through Usage Access;
- membership in the in-memory protected set loaded from the encrypted database; and
- an optional package-scoped session held in memory.

This association is calculated and is not persisted as a relationship.

### 7.3 Package metadata

The display label and icon are joined in memory from Android package information using the package identifier. They are not a relational entity and do not create a referential requirement.

### 7.4 Consequences of the reduced model

The logical design requires no:

- application-group membership;
- schedule or rule references;
- profile ownership;
- event-to-package relationship;
- notification-to-event relationship;
- vault parent-child relationship;
- session foreign key; or
- generic ownership and archive model.
