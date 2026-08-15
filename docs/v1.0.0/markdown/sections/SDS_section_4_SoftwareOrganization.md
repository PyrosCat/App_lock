# Software Design Specification

## Version 1.0.0

## 4. Software Organization

### 4.1 Logical areas

| Logical area | Primary responsibility | Persistent state |
|---|---|---|
| Presentation | Screens, navigation, input, visible loading/error/empty states, accessible feedback | Harmless preferences only |
| Authentication and session | PIN and biometric result handling, retry control, lockout, package-scoped sessions | Protected verifier and lockout state; no persistent session |
| Protection | Foreground-target evaluation, session decision, lock request, relock | None |
| Protected applications | Eligible-app discovery and selected package set | Protected package identifiers |
| Protection health | Current capability, service, presentation, and storage assessment | None |
| Android integration | Usage Access queries, biometric prompt, system settings handoffs, lifecycle signals, notification delivery | None beyond Android-owned state |
| Persistence and security | Private settings, encrypted relational data, protected preferences, key use, migration | As defined by the database specification |
| Help and current diagnostics | User-readable status and recovery guidance | None |

These are logical boundaries. A small implementation may combine adjacent responsibilities when ownership remains clear and tests can verify the boundary.

### 4.2 Dependency direction

Presentation invokes application operations and observes results. Application operations use authentication, protection, and protected-application rules. Those rules use narrow persistence and Android boundaries. Storage and Android implementations do not call into screens.

The permitted direction is:

1. presentation;
2. application operation;
3. core authentication or protection decision;
4. persistence or Android boundary; and
5. local platform facility.

Reverse notification occurs through returned results, observable state, or a narrowly scoped callback. No component reads another component’s private mutable state.

### 4.3 State ownership

| State | Authoritative owner | Consumers |
|---|---|---|
| PIN configured | Protected authentication storage | Onboarding, authentication, health |
| Failed-attempt count and lockout deadline | Authentication | PIN screen, settings authentication |
| Biometric preference | Protected settings | Authentication, settings |
| Biometric eligibility | Android biometric capability query | Authentication, status |
| Protected package set | Encrypted relational persistence | Protection, application list, health |
| Current foreground package | Usage Access detection cycle | Protection only |
| Active package session | In-memory session handling | Protection |
| Current lock request | Protection presentation coordination | Lock screen |
| Permission and service state | Protection-health evaluation | Dashboard, onboarding, notifications |

### 4.4 Initialization order

Startup follows a fail-safe order:

1. initialize protected storage and obtain the database-opening capability;
2. verify schema compatibility and complete any supported migration;
3. load the selected protected-package set;
4. read credential, lockout, biometric, and relock settings;
5. query current Usage Access, notification, and service state;
6. derive protection health;
7. start or resume protection checks only when setup and protected selection require them; and
8. publish visible state to presentation.

If protected storage cannot be opened or migration fails, normal protection operation does not begin with an empty package set. The user receives an unrecoverable local-data message and the option to clear application data through Android.
