# Software Design Specification

## Version 1.0.0

## 20. Maintainability and Verification Support

### 20.1 Maintainable boundaries

Core authentication, retry, session, and protection decisions are deterministic and independently testable. Android and storage behavior is accessed through narrow boundaries. Presentation renders complete states rather than interpreting platform exceptions.

The design avoids duplicate state, hidden global mutation, and circular dependency. Excluded capabilities do not remain as required extension points.

### 20.2 Unit verification

Unit verification covers:

- PIN setup success, mismatch, invalid input, storage failure, and protected verifier upgrade;
- failure counting, five-attempt threshold, 30-second initial lockout, doubling behavior, 30-minute maximum, clock change, and success reset;
- biometric eligibility, success, cancellation, unavailability, stale result, and PIN fallback;
- package-scoped session creation and invalidation;
- immediate, ten-second grace, and screen-off relock behavior;
- process-death and reboot session loss;
- protected and unprotected package decisions;
- duplicate foreground reports and rapid target changes;
- stale lock completion rejection;
- safe defaults for invalid settings; and
- protection-health classification from each input combination.

### 20.3 Storage verification

Storage verification covers:

- PIN and lockout persistence without raw PIN storage;
- database encryption and inability to open without protected material;
- unique protected package insertion;
- idempotent add and remove behavior;
- commit-before-snapshot ordering;
- migration from each explicitly supported source schema;
- interrupted migration and failed verification;
- no destructive empty-database fallback;
- exclusion from Android backup; and
- complete local reset after application-data clearing.

### 20.4 Android integration verification

Android verification covers:

- Usage Access explanation, settings handoff, grant, denial, revocation, and return verification;
- confirmation that the application declares and operates no Accessibility service;
- foreground detection for protected and unprotected launchable apps;
- lock presentation, back/home behavior, recents privacy, and cancellation;
- notification permission behavior on applicable API levels;
- protection-service operation, stop, restart where permitted, and truthful health;
- screen-off session invalidation;
- process recreation;
- reboot;
- force-stop followed by user relaunch;
- package installation, update, and removal; and
- biometric available, unavailable, unenrolled, cancelled, and locked platform states.

### 20.5 User-interface and accessibility verification

Verification covers TalkBack labels and announcements, focus order, masked PIN behavior, lockout announcements, touch targets, contrast, text scaling, landscape use, reduced motion, keyboard behavior where supported, screenshot protection, and recents privacy.

Security and recovery actions remain reachable at the largest supported text scale.

### 20.6 Supported-phone verification

API levels 30 through 35 are each exercised using available emulator coverage, with the complete security-critical path exercised on at least one physical Android phone. Physical-phone checks include Usage Access behavior, protection-service behavior, lock presentation, biometric fallback where hardware is eligible, screen-off, reboot, force-stop, and notification permission where applicable.

Results on tablets, foldables, Chromebooks, earlier APIs, work profiles, secondary users, and cloned apps do not expand version 1.0.0 support.

### 20.7 Security invariants

The completed implementation must demonstrate:

- no raw PIN persistence or logging;
- no session persistence;
- no successful result after cancellation, stale completion, or storage failure;
- no protected-app removal before durable commit;
- no healthy protection claim without current Usage Access and service evidence;
- no Accessibility service declaration or request;
- no protected package names in notifications or ordinary diagnostics;
- no Android backup of application-controlled data;
- no reachability of excluded feature interfaces; and
- no silent data deletion after database or migration failure.

### 20.8 Completion statement

This design is complete for version 1.0.0 when the retained flows, states, storage boundaries, Android limitations, recovery actions, privacy controls, and verification conditions are implemented consistently. Capabilities identified in Section 1.4 remain outside this specification and are not prerequisites for release.
