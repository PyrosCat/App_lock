# Software Design Specification

## Version 1.0.0

## 8. Lock Detection and Enforcement Design

### 8.1 Responsibilities

Protection logic is responsible for:

- receiving the current foreground package from the Usage Access path;
- ignoring ineligible, unprotected, self-owned, and stale package reports;
- checking current protection health and selected-package state;
- evaluating the package-scoped session;
- requesting one lock presentation when authentication is required;
- accepting only the result for the current target;
- creating the session after successful authentication;
- applying the selected relock behavior; and
- reporting an interruption when detection or presentation cannot operate.

It does not verify PINs, query Android package metadata for display, modify the selected package set, store event history, or evaluate schedules and contexts.

### 8.2 Foreground detection

While protection is required, the visible protection service performs bounded Usage Access queries and normalizes the most recent foreground result to a package identity and observation time. A result that is missing, too old, internally inconsistent, or outside the visible launchable set is treated as unknown rather than a protected-app launch.

The detector does not record an application-use timeline. It holds only the current and immediately previous package identities needed to suppress duplicates and apply relock when the user leaves a protected application.

### 8.3 Protection decision

For each valid current package:

1. ignore the App Lock package and its lock surface;
2. determine whether the package is in the current protected set;
3. if it is not protected, notify session handling that the previous protected app was left and take no lock action;
4. if it is protected, verify that protection health is sufficient to attempt presentation;
5. evaluate whether that package has a valid session under the selected relock behavior;
6. if valid, allow continuation without a new prompt;
7. if invalid, create one current lock request and request presentation; and
8. accept success only when the completion matches the current request and current foreground protected package.

Missing configuration, unknown storage state, or a failed dependency never yields an allow result. Where Android prevents enforcement, the outcome is interruption with truthful user notice rather than a false successful protection decision.

### 8.4 Duplicate and rapid-transition handling

Repeated reports for the same protected package must not create repeated lock screens. A lock request remains current until it succeeds, is cancelled, times out, or becomes stale because the foreground target changed.

If the user rapidly switches among applications:

- a result for a prior package is ignored after the target changes;
- an existing valid session is evaluated only for its own package;
- leaving a protected package applies its relock choice once;
- the application lock surface is not treated as a new protected target; and
- returning to the same package during an active request reuses that request rather than stacking another screen.

### 8.5 Lock presentation

The lock surface is presented as a system overlay drawn with the "Display over other apps" permission. This is a design constraint, not a styling choice: because detection runs in a background service, App Lock cannot depend on launching an Activity to cover the protected application — Android 10+ background-activity-launch restrictions make a background Activity launch unreliable — so the lock is drawn as an overlay window a background component can raise deterministically. The overlay permission is therefore required for enforcement; without it the protection path cannot complete and health reports Action required (§8.7 of this document).

The lock surface receives only the current protected target identity and request identity. It retrieves no protected-application content. It immediately checks whether the request is still current and whether a session was created before rendering.

The surface blocks interaction with the protected task while present, removes sensitive previews from recents where Android permits, offers biometric authentication when eligible, and always exposes PIN fallback. Back, home, cancellation, process recreation, or authentication failure does not send a success result.

### 8.6 Relock events

Relock is evaluated when:

- the current protected package leaves the foreground;
- the ten-second grace period expires;
- the screen turns off;
- the process terminates;
- the phone reboots;
- the PIN changes;
- protected storage becomes unavailable;
- a security-relevant state becomes inconsistent; or
- protection is explicitly disabled for that package, after authenticated confirmation.

No schedule, network, location, Bluetooth, charging, calendar, or trusted-context event participates.

### 8.7 Protection states

| State | Meaning |
|---|---|
| Inactive | Setup is incomplete or no application is selected |
| Monitoring | Usage Access and the protection service are available and foreground checks are active |
| Unprotected target | The current package is not selected |
| Authentication required | The current package is selected and lacks a valid session |
| Lock presented | One current authentication surface is active |
| Temporarily unlocked | The current package has a valid in-memory session |
| Action required | A user-correctable capability is unavailable |
| Protection interrupted | Detection or lock presentation is not functioning while protected selections exist |
| Unknown or not verified | Current facts cannot yet be verified |

### 8.8 Failure behavior

Usage Access revocation stops protection decisions and changes health to Action required during incomplete setup or Protection interrupted after active protection is lost. Loss of the overlay permission is treated the same way: the lock cannot be presented, so protection cannot be enforced and health becomes Action required or Protection interrupted rather than a false protected state. A service stop, repeated detector failure, or lock-presentation failure invalidates pending success, retains no session, and changes health to Protection interrupted.

Database unavailability preserves the last known in-memory protected set only for the current process if it was loaded from a verified database. After process recreation, an unreadable store does not become an empty unprotected list; normal operation is blocked and local-data recovery guidance is shown.
