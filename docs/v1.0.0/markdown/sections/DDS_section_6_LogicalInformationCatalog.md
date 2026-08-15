# Database Design Specification

## Version 1.0.0

## 6. Logical Information Catalog

### 6.1 Ordinary settings

Ordinary settings are a small non-relational record. Only choices exposed by the version 1.0.0 interface are retained.

| Information | Purpose | Default and validation |
|---|---|---|
| Relock behavior | Determines when a package-scoped session becomes invalid after leaving a protected app | Immediate is default; allowed alternatives are ten-second grace and screen-off |
| Haptic choice | Controls nonessential interface feedback | Uses the user-experience default; ignored when system settings prohibit feedback |
| Reduced-motion choice | Limits nonessential motion | Uses the user-experience default; does not affect security timing |

No general feature flag, profile, schedule, automation, backup, vault, event, or remote-configuration value is included.

### 6.2 PIN verification record

The protected PIN record contains:

- an indicator that a complete credential exists;
- the verifier format version;
- a unique random salt or equivalent verifier input;
- the approved memory-hard verifier;
- the parameters required to evaluate that verifier; and
- only the integrity information required by the chosen protected preference facility.

It does not contain the raw PIN, a reversible encrypted PIN, a recovery answer, a backup password, a device credential, or biometric data.

The record is logically indivisible. A missing required part means the credential is unavailable, not partially configured.

### 6.3 Retry and lockout record

The protected retry record contains:

- the consecutive failed PIN-attempt count; and
- the deadline before which another PIN verification is not permitted.

Five consecutive failed PIN attempts begin a 30-second lockout. Further failures after each wait double the next wait up to 30 minutes. Successful PIN verification or current-PIN-authorized PIN replacement clears the count and deadline.

The record survives process death, force-stop, and reboot. It does not store a detailed attempt history, target package, candidate length, or biometric failure history.

### 6.4 Biometric choice

The persisted biometric value records only the user’s decision to offer the platform prompt. It is meaningful only while a PIN exists. Current hardware, enrollment, temporary availability, and platform lockout are queried directly from Android at the time of authentication.

Destructive local reset clears the choice. PIN replacement preserves it only after current biometric eligibility is rechecked. Enrollment change does not create a database update unless the user later changes the preference.

### 6.5 Database-opening material

The protected database-opening record contains random material required by the encrypted database facility and the minimum format information needed to use it. It is generated once for the local installation.

It is not derived from a short PIN, does not leave protected storage, and is never included in a backup or diagnostic view.

### 6.6 Protected application record

The sole supported relational record represents one selected protected application.

Required information:

- package identifier.

Optional existing information may remain only when already needed for schema compatibility and must not become authoritative if Android can provide the current value.

Record existence means protected. A second enabled state, category, policy reference, group reference, archive state, user name, device identity, or schedule reference is not required.

### 6.7 Nonpersistent information

| Information | Why it is not persistent | Re-creation |
|---|---|---|
| Package-scoped unlock session | Persisting would extend authorization across process death or reboot | New authentication |
| Current foreground package | Usage history is unnecessary and privacy-sensitive | Next Usage Access query |
| Prior foreground package | Needed only for immediate relock evaluation | Next detector sequence |
| Active lock request | A stale request must not survive recreation | New protection decision |
| Protection health | It is a current assessment, not history | Recompute from storage and Android |
| Biometric eligibility | Android owns hardware and enrollment state | Query platform |
| Usage Access grant | Android owns the permission state | Query system setting |
| Notification permission | Android owns the permission state | Query platform |
| Installed app label and icon | Android owns current package metadata | Query package information |
| Diagnostic status | Current support facts do not require history | Query current owners |

### 6.8 Unsupported logical information

There are no supported logical records for vault, backup, recovery, application groups, schedules, automation, notification history, security-event history, diagnostics, telemetry, metrics, media, remote accounts, or administration.
