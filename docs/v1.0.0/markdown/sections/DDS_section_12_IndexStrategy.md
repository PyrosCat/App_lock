# Database Design Specification

## Version 1.0.0

## 12. Index Strategy

### 12.1 Required indexes

The protected package collection requires:

- the primary access structure supplied by the database framework; and
- uniqueness and efficient equality lookup for package identifier.

These may be satisfied by one physical index when the package identifier is the primary key.

### 12.2 Excluded indexes

Version 1.0.0 does not require indexes for:

- application label, icon, category, or version;
- group, profile, schedule, rule, priority, or time range;
- vault item, tag, category, attachment, or search content;
- security event or notification time;
- diagnostic severity or component;
- metric name or measurement time; or
- soft-deletion and archive state.

### 12.3 Index maintenance

The database framework maintains the retained index during ordinary transactions and migration. There is no periodic index-analysis service, index-usage history, or scheduled reindex operation.

An integrity failure affecting the unique package lookup makes the database unavailable until a supported recovery or destructive reset occurs.
