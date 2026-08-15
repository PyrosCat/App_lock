# Threat Model

## Version 1.0.0

## Appendix B — Scope Exclusions and User Consequences

| Excluded item | Version 1.0.0 treatment | User consequence |
|---|---|---|
| Vault and private media | No storage, import, viewing, organization, export, or deletion capability | App Lock protects application entry only; it is not a private-file product |
| Backup, restore, and device transfer | No archive, import, recovery, or new-device flow | Local configuration must be set up again after data loss or on another phone |
| Recovery password and accounts | No secondary secret, email, cloud identity, or security questions | Forgotten PIN requires destructive local reset |
| Profiles, schedules, and automation | One global protection and relock policy only | Protection does not change automatically by time, place, connection, or profile |
| Intruder capture and event media | No camera use or intruder library | Failed attempts produce retry/lockout state only |
| Third-party notification access | No reading, masking, replacing, or storing another application's notification content | Only App Lock's own essential notifications are controlled |
| App Lock Accessibility service | No permission request or event-based enhancement | Foreground detection uses Android Usage Access only |
| Device-administrator uninstall prevention | No administrator enrollment | The Android device user may uninstall App Lock or clear its data |
| Network, account, cloud, and remote administration | No routine application network communication | Core operation is local and there is no remote recovery or control |
| Advanced diagnostics and reports | Current health and bounded local records only | No export, long-term history, trends, dashboard, or remote telemetry |
| Non-phone and alternate-profile environments | No support claim or tailored acceptance | Behavior is unspecified outside conventional supported phone installations |
| Android 8 through Android 10 | No support or compatibility claim | Android 11 is the minimum supported version |
| Root/system compromise | No assurance that App Lock can resist a privileged attacker | Security guarantee applies only to non-compromised supported phones |

Established identifiers THR-IPC-004 and THR-INT-002/003 remain reserved because their original subjects depend on excluded cross-application sharing, alternate distribution, or maintenance surfaces. They create no Version 1.0.0 control or verification obligation.

---
