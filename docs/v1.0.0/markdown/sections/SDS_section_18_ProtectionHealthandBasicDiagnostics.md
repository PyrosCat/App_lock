# Software Design Specification

## Version 1.0.0

## 18. Protection Health and Basic Diagnostics

### 18.1 Purpose

Protection health provides a truthful, current answer to whether the application has enough verified capability to attempt its App Lock function. Basic diagnostics give the user the facts and recovery action needed to address a problem without creating a history or export.

### 18.2 Inputs

Health is derived from:

- complete PIN configuration;
- readable protected preferences;
- readable and schema-compatible encrypted database;
- count of selected protected applications;
- current Usage Access grant;
- current protection-service operation;
- freshness of the most recent valid foreground check;
- current lock-presentation readiness or recent verified presentation result;
- notification capability where required for the retained service; and
- any current unrecoverable initialization error.

The application does not persist the combined health state. It recalculates it after startup, resume, settings return, permission change, service change, protected-selection change, and relevant error.

### 18.3 Health states

| State | Criteria | User presentation |
|---|---|---|
| Not configured | No complete PIN or no selected protected application | Continue setup or select an application |
| Partially configured | A PIN exists but one or more required setup steps, capabilities, or protected selections are incomplete | Resume at the first incomplete step without a healthy claim |
| Protected | Required storage, Usage Access, service, and presentation evidence are healthy | Calm confirmation and last verification time where available |
| Degraded | Protection can operate but a nonessential visibility or responsiveness condition is limited | Explain limitation without claiming interruption |
| Action required | A user-correctable required capability is unavailable | Prominent action and direct Android handoff |
| Protection interrupted | Required detection, service, or presentation has failed while selections exist | Persistent warning; do not use “protected” wording |
| Unknown or not verified | Startup or verification is incomplete or current evidence is stale | Show checking state and retry |

When more than one condition applies, the precedence is Protection interrupted, Action required, Unknown or not verified, Degraded, Protected, Partially configured, then Not configured. Evidence becomes stale after a relevant Android settings handoff, permission change, service change, process recreation, reboot, or failed requested verification; the next health evaluation replaces the stale result. Because no Accessibility service exists in version 1.0.0, Accessibility access is not a health input and is never shown as missing.

### 18.4 Basic diagnostic content

The diagnostic screen may show:

- application version;
- Android version and API level;
- phone form-factor support result;
- PIN configured or not configured, without verifier detail;
- biometric enabled and currently eligible or unavailable, without enrollment detail;
- Usage Access granted or missing;
- protection service active, stopped, or unknown;
- notification capability available or limited;
- encrypted protected-app store available or unavailable;
- number of protected applications, without listing them in exported or notification content; and
- current high-level interruption reason.

### 18.5 Privacy and retention

Diagnostics are read from current state and discarded when no longer needed. There is no diagnostic database, rolling log, metric series, timeline, correlation record, crash package, or export.

Ordinary development logging is minimal and redacted. It does not constitute a user feature or a persistent support history.
