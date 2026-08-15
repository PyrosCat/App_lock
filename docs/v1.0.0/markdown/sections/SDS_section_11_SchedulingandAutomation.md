# Software Design Specification

## Version 1.0.0

## 11. Scheduling and Automation

### 11.1 Version 1.0.0 boundary

Version 1.0.0 does not include profiles, schedules, time windows, contextual conditions, automation actions, conflict resolution, execution history, or automated policy changes.

Protection depends only on the selected protected-package set, current protection health, and the package-scoped session under the chosen relock behavior. Screen-off, reboot, process death, permission change, and session expiration are security lifecycle events, not automation features.

No scheduler, rule database, trigger service, execution queue, automation notification, or automation recovery flow is required.
