# Software Design Specification

## Version 1.0.0

## 5. Dependency and Communication Design

### 5.1 Android boundaries

Usage Access, package discovery, biometrics, notifications, system settings, boot signals, screen state, and service lifecycle are isolated behind narrow behavior-based boundaries. Core logic receives normalized facts such as the current package identity, permission availability, biometric result, or screen-off event.

Android objects and errors do not propagate into core state or user-visible text. Each platform failure is translated into a small result category with a defined safe behavior.

### 5.2 Storage boundaries

Ordinary preferences, protected preferences, and the encrypted relational store have separate responsibilities. Authentication cannot read the relational database to obtain a PIN verifier. Protection cannot read raw preference files. Presentation cannot access any storage mechanism directly.

A storage write returns only after the durable operation succeeds. Observable state is updated after success, not optimistically before commit, for protection-reducing changes.

### 5.3 Time dependency

Time used for grace sessions and retry delay is supplied to the owning logic through a testable boundary. Session timing uses process-relative elapsed time. Persistent lockout stores a deadline with defensive handling of clock changes and a maximum reported remaining duration.

### 5.4 Concurrency

Credential verification is performed away from the main presentation thread. Protected-package persistence and snapshot changes are serialized. Foreground reports for the same package are coalesced. Only one lock request may be active.

When a foreground target changes during authentication, the earlier completion is discarded unless it still matches the current protected target. When protection is removed while a lock is displayed, the removal is committed first and the lock request is then cancelled in a controlled order.

### 5.5 Failure translation

Dependencies report categories rather than internal exceptions. The retained categories include unavailable, permission required, invalid input, invalid credential, temporarily locked, cancelled, storage unavailable, migration failed, presentation failed, and unexpected local failure.

User-visible wording comes from the presentation layer and gives an action where one exists. Internal detail is redacted.

### 5.6 Dependency exclusions

There is no cloud integration, remote identity provider, telemetry integration, backup provider, file importer, media service, scheduling provider, location provider, network-state automation, generic task registry, or application Accessibility service.
