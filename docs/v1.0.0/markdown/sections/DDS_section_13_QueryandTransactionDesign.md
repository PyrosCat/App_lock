# Database Design Specification

## Version 1.0.0

## 13. Query and Transaction Design

### 13.1 Supported relational operations

The relational persistence boundary supports:

1. list all protected package identifiers;
2. determine whether one package identifier is present;
3. add one package identifier;
4. remove one package identifier; and
5. run the explicitly supported schema migration and verification.

No general query builder, full-text search, aggregation, pagination, join graph, or reporting query is required.

### 13.2 Read behavior

At verified startup, the complete protected set is read into memory. Equality checks during foreground detection use that in-memory set and do not query the database on every detector cycle.

The protected-app management screen combines the stored set with current Android package information in memory.

### 13.3 Write behavior

Each user selection change produces at most one add or remove transaction. Repeated taps are disabled or coalesced while the current write is unresolved. A user-visible success state is shown only after commit.

Adding an existing package and removing an absent package are idempotent final states. They do not generate error history or duplicate records.

### 13.4 Main-thread restriction

Database opening, migration, full-set reading, and writes execute away from the user-interface thread. Completion is delivered to the owner as a success or defined failure category.

### 13.5 Parameterization and input validation

Every package identifier used in a database operation is supplied as a bound value through the selected persistence framework. Text concatenation is not used to construct executable database statements.

Input validation confirms required form and supported length. Selection additionally confirms that Android currently reports an eligible launchable application.

### 13.6 Transaction isolation

Initialization and migration exclude ordinary access. Protected package writes are serialized. A read made after a successful write observes the committed set.

The design does not require distributed transactions, nested transactions, write batching across unrelated domains, or separate read and write databases.

### 13.7 Performance expectation

The complete protected set and an equality lookup must remain fast for the number of launchable applications reasonably present on a supported phone. The design favors predictable lookup and minimal writes over throughput for a large enterprise dataset.

Performance measurement does not persist package identifiers or query histories.
