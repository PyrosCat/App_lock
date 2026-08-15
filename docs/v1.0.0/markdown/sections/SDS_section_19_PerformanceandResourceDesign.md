# Software Design Specification

## Version 1.0.0

## 19. Performance and Resource Design

### 19.1 Performance priorities

The highest-priority latency path is:

1. obtain a recent foreground result from Usage Access;
2. look up package membership in the in-memory protected set;
3. evaluate the package-scoped session; and
4. request the lock screen when required.

The protection decision after a valid foreground result should complete within the applicable quality requirement, with 250 milliseconds as the retained normal-condition target. Usage Access detection should identify a foreground change within the retained 500-millisecond normal-condition target.

These targets do not create an absolute guarantee that Android will never reveal content before presentation.

### 19.2 Main-thread behavior

PIN derivation and verification, encrypted database open, migration, database writes, installed-app enumeration, and Usage Access queries do not block the main presentation thread. State changes return to presentation through lifecycle-aware observable results.

Visual input feedback remains immediate even while credential verification runs.

### 19.3 Memory

The application keeps only:

- the small set of protected package identifiers;
- the current and prior foreground identity;
- active package sessions;
- one current lock request;
- screen presentation state; and
- short-lived installed-app display information.

No event history, media, backup content, metrics, or usage timeline grows in memory.

### 19.4 Battery and CPU

Foreground polling runs only when at least one package is protected and stops when protection is not required. The interval is bounded and no busy-wait loop is permitted. Repeated errors use backoff and change health after a defined limit.

There is no network polling, location monitoring, Bluetooth monitoring, camera work, analytics upload, schedule evaluation, or periodic database maintenance.

### 19.5 Storage

Persistent storage contains a small settings set, protected credential and key material, and protected package identifiers. No unbounded history is retained. A storage warning is required only when a retained write fails or the database cannot operate; a separate storage-monitoring subsystem is not needed.

### 19.6 Performance verification

Measurement covers cold and warm application entry, PIN-screen responsiveness, PIN verification, protected-package lookup, foreground detection, lock-decision time, lock presentation, list loading, selection commit, and migration.

Measurements use sanitized labels and do not persist protected package names or PIN data.
