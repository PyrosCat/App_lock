# UI/UX Specification

> Version 1.0.0

## 5. Application and Protection State Model

#### 5.1 Global States

<!-- table-widths: 1.65, 2.65, 2.2 -->
| State | Meaning | Primary presentation |
| --- | --- | --- |
| Not configured | No usable local PIN exists. | Start setup. Do not claim protection. |
| Partially configured | A PIN exists, but required access, application selection, or verification is incomplete. | Resume the earliest incomplete step. |
| Protected | Required access is currently available, at least one app is selected, and protection was freshly verified. | Calm positive status with last-check context and Manage apps. |
| Degraded | Protection can operate, but Android restrictions or a recoverable condition may reduce reliability. | State the limitation and provide one direct recovery action. |
| Protection interrupted | A required capability is unavailable or enforcement has failed. | Prominent interruption, consequence, and Fix protection action. |
| Action required | A user decision or Android setting is required before protection can be established or restored. | Name the required action and affected capability. |
| Unknown or not verified | Current evidence is missing, stale, or contradictory. | Show Checking or Not verified; never substitute the last healthy state. |

“Protected with reduced responsiveness” is not a Version 1.0.0 state because the release uses a single required detection path and has no optional App Lock Accessibility enhancement.

#### 5.2 State Precedence

When more than one condition applies, the visible state shall use this order: Protection interrupted, Action required, Unknown or not verified, Degraded, Protected, Partially configured, Not configured. A lower-severity condition may appear as supporting detail but may not hide the higher-priority consequence.

#### 5.3 State Evidence and Freshness

A protected status requires current evidence for the local credential, selected applications, Usage Access, lock-presentation capability, protection operation, and the most recent verification. Returning from Android settings, resuming after a relevant interruption, rebooting, or detecting an access change shall invalidate stale success evidence and trigger a new check.

The interface may show “Last checked” only with a real timestamp. It shall use “Not verified” when the current result cannot be established within the defined check period.

#### 5.4 State Invariants

- A successful authentication creates only the session allowed for the current context.
- Failed, cancelled, expired, or interrupted authentication creates no session.
- Unlocking one protected application does not authorize another.
- Process death, reboot, PIN change, or complete reset invalidates every session.
- Loss of required access removes the healthy protection claim.
- Empty, missing, corrupted, or unreadable configuration is not treated as a valid unprotected default while the interface still claims protection.
