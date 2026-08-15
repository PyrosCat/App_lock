# Software Design Specification

## Version 1.0.0

## 17. Error Handling and Recovery Design

### 17.1 Error categories

| Category | Meaning | Typical response |
|---|---|---|
| Recoverable transient failure | A local operation may succeed on retry without changing security state | Bounded retry, refresh current facts, preserve last consistent state |
| User action required | Android permission or setting must be changed | Explain the effect, open the exact settings destination, recheck on return |
| Protection interrupted | Required detection, service, or lock presentation is not functioning | Stop healthy claim, invalidate pending access, show persistent warning |
| Unrecoverable local-data failure | Credential or encrypted database cannot be opened or safely migrated | Keep authentication unavailable, explain loss of local configuration, direct user to clear data |

### 17.2 Failure matrix

| Failure | Safe behavior | Recovery |
|---|---|---|
| Usage Access denied or revoked | Foreground identity is not trusted; no healthy protection claim | Open Usage Access settings and recheck |
| Notification permission denied | Do not claim successful visible delivery; assess whether service may continue | Open notification settings when the user chooses |
| Protection service stopped | Clear current detector state and report interruption | Request restart when Android permits; verify before healthy state |
| Lock screen cannot be presented | Create no session; report interruption | Return to App Lock, recheck capability, retry only after current target is known |
| PIN is invalid | Increment failure state; create no session | Retry subject to lockout |
| PIN lockout active | Do not verify another PIN | Wait until displayed expiry |
| Biometric unavailable or cancelled | Create no session | Use PIN fallback |
| Protected preferences unavailable | Treat authentication as unavailable | Retry initialization; if unrecoverable, clear application data |
| Database cannot open | Do not treat protected set as empty | Retry once where safe; otherwise clear-data guidance |
| Migration interrupted or invalid | Do not permit normal database access | Resume or roll back according to supported migration; otherwise clear-data guidance |
| Package information temporarily unavailable | Keep stored selection; show unavailable metadata | Refresh current Android package information |
| Storage exhausted during write | Leave last committed state active | Free storage and retry |
| Process death | Clear all transient state and sessions | Reinitialize and re-evaluate health |
| Force-stop | No protection claim while Android blocks restart | User opens App Lock; application reinitializes |

### 17.3 Retry rules

Retries are bounded and limited to idempotent or safely repeatable operations. PIN verification is never automatically retried. A database write is retried only when the persistence layer can establish that the prior transaction did not commit.

Repeated platform or service failure changes health rather than creating an endless retry loop.

### 17.4 User communication

Messages state:

- what is currently unavailable;
- whether protection is affected;
- whether the user’s last change was saved;
- the single next action; and
- whether local configuration will be lost.

Messages do not expose internal component names, exception text, storage paths, package names, encryption detail, or attacker-oriented information.

### 17.5 Recovery limits

There is no backup restore, remote repair, recovery password, administrator repair path, diagnostic package, or preserved credential copy. Clearing application data is destructive and returns the installation to first setup.
