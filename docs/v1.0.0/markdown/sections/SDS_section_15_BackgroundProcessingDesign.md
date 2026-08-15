# Software Design Specification

## Version 1.0.0

## 15. Background Processing Design

### 15.1 Purpose

Background processing exists only to support the retained Usage Access foreground check, protection continuity within Android limits, session invalidation on relevant lifecycle events, and protection-health reporting.

It is not a general job-processing facility.

### 15.2 Protection-service lifecycle

The visible protection service is requested when:

- a complete PIN configuration exists;
- at least one application is selected; and
- the application has the Android capabilities required to attempt foreground detection.

It stops when no applications remain selected or the local configuration is reset. If Usage Access is revoked, it stops protection checks that cannot produce a reliable target and reports action required rather than repeatedly querying.

### 15.3 Detection cycle

Each cycle:

1. confirms that protection is still required;
2. queries the recent Usage Access information needed to identify the current foreground package;
3. validates freshness and package identity;
4. passes the normalized current target to protection logic;
5. waits for the bounded interval; and
6. repeats while service and lifecycle conditions permit.

Cycles do not write usage history, metrics, task records, or diagnostic timelines. A transient query failure uses a bounded retry. Repeated failure changes protection health to Protection interrupted and avoids a tight retry loop.

### 15.4 Process death and restart

Process death clears all sessions, current-target state, and lock requests. When Android later recreates the application or service, protected storage is verified and the protected set is loaded before detection resumes.

The design does not assume immediate automatic recreation. Until current service operation and a recent foreground check are verified, health is Unknown or not verified or Protection interrupted rather than Protected.

### 15.5 Reboot

Reboot clears every session. After boot, the application may request resumption of the retained visible protection service only as Android permits and only when protected selections exist. If Android defers or prevents service start, the next application entry reports action required and provides the appropriate recovery instruction.

The application does not restore a lock request or foreground identity from before reboot.

### 15.6 Force-stop

Android force-stop prevents application components from restarting until the user explicitly opens the application again. Version 1.0.0 does not claim protection during that interval.

On the next launch, the application reopens protected storage, clears sessions, rechecks Usage Access and notification capability, requests the protection service where permitted, and reports whether protection has resumed.

### 15.7 Screen state

Screen-off clears all package sessions for every relock choice. Screen-on does not create a session and does not assume that the prior foreground target remains current. The next valid Usage Access observation determines the target.

### 15.8 Resource limits

Foreground queries and protection decisions remain lightweight. Credential derivation, database migration, package discovery, and other potentially expensive work run outside the main thread. Background retries are bounded and no network, camera, location, Bluetooth, media, backup, or analytics work is scheduled.

### 15.9 Excluded background facilities

There is no durable task record, generic worker registry, task priority service, execution history, checkpoint store, recurring maintenance schedule, automation dispatcher, backup worker, event uploader, or metrics aggregator.
