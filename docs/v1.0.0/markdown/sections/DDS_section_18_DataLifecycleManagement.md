# Database Design Specification

## Version 1.0.0

## 18. Data Lifecycle Management

### 18.1 First installation

The installation starts unconfigured. Keystore-protected database-opening material is created only when the encrypted database is first required. The database is created at the current schema version.

Setup completeness is derived from a complete PIN configuration, current Usage Access, readable protected-app storage, and selected-app state. A duplicate “setup complete” database row is not required.

### 18.2 Ordinary settings lifecycle

Ordinary settings begin at documented defaults, change only through validated user choices, and are updated in place. They remain until changed, application data is cleared, or the application is uninstalled.

They are not archived, historized, synchronized, or backed up.

### 18.3 PIN lifecycle

1. The user enters and confirms a permitted PIN.
2. A complete protected verifier is created atomically.
3. Authentication reads the verifier and evaluates candidates without changing it.
4. An authenticated PIN change atomically replaces the verifier.
5. Successful replacement clears retry state and sessions.
6. Clearing application data or uninstall removes the verifier.

No old verifier or recovery copy is retained after successful replacement.

### 18.4 Lockout lifecycle

1. The count begins at zero with no deadline.
2. Each failed PIN increments the count.
3. Beginning with the fifth failure, a wait deadline is stored.
4. Expiry permits the next attempt but retains the count.
5. A further failure creates the next doubled wait, capped at 30 minutes.
6. Successful PIN verification or current-PIN-authorized PIN replacement clears count and deadline.
7. Data clear or uninstall removes the record.

### 18.5 Biometric-choice lifecycle

Biometric use is disabled before PIN setup. The user may enable it only while eligible, but platform eligibility is not persisted. The protected preference remains until disabled, a destructive local reset completes, application data is cleared, or the application is uninstalled. Ordinary PIN replacement does not enable it automatically and preserves the user's prior choice only after current eligibility is rechecked.

### 18.6 Protected application lifecycle

1. Android reports an eligible launchable package.
2. The user selects it after required setup and authentication.
3. The unique package identifier is inserted and becomes active after commit.
4. Updates to the same package identifier preserve selection.
5. User deselection deletes the row after authentication.
6. Confirmed uninstall deletes the row and clears its session.
7. Reinstallation does not restore the row automatically.
8. Data clear or uninstall of App Lock removes all rows.

There is no archive, soft deletion, group assignment, or selection history.

### 18.7 Database-opening material lifecycle

Opening material is generated once for the local installation, protected through Android Keystore, and used only to open the encrypted database. It remains until application data clear, uninstall, or unrecoverable Keystore invalidation.

There is no rotation requirement in version 1.0.0 unless the selected storage facility performs a compatible platform-managed change without introducing a second key-management system.

### 18.8 Session and detector lifecycle

Package sessions, authenticated settings state, current foreground identity, prior foreground identity, and lock request remain in process memory. They are discarded on process death and reboot. Screen-off clears all package sessions and authenticated settings state. The authenticated settings state also ends when its sensitive flow completes or is cancelled, App Lock leaves the foreground, the PIN changes, or a security-relevant error occurs. No lifecycle event writes these values to storage.

### 18.9 Permission lifecycle

Usage Access, the "Display over other apps" overlay permission, and notification permission are Android-owned states. The application queries them on setup, resume, settings return, and relevant service change. It does not store a duplicate granted flag as authority or retain permission history.

There is no Accessibility permission state because the application provides no Accessibility service.

### 18.10 Reset and uninstall

Clearing application data or uninstalling removes:

- ordinary settings;
- PIN verifier and biometric choice;
- failure count and lockout deadline;
- database-opening material;
- encrypted database and selected packages;
- framework journals and temporary files; and
- all memory-only state.

The next installation or launch after clear begins as a new unconfigured installation.

### 18.11 Key loss and unrecoverable data

If Android Keystore material is invalidated or missing, the application cannot recover the protected preferences or encrypted database through a password or backup. It reports the local configuration as unavailable and directs the user to clear application data.

It does not delete the encrypted database silently, create a new key over the old database, or report an empty protected set as normal.

### 18.12 Retention

| Data | Retention |
|---|---|
| Ordinary settings | Until changed, data clear, or uninstall |
| PIN verifier | Until authenticated replacement, data clear, or uninstall |
| Failure and lockout state | Until successful PIN, credential replacement, data clear, or uninstall |
| Biometric preference | Until disabled, destructive local reset, data clear, or uninstall; PIN replacement preserves the choice only after eligibility is rechecked |
| Database-opening material | Local installation lifetime or unrecoverable invalidation |
| Protected package identifier | Until authenticated deselection, confirmed package uninstall, App Lock data clear, or App Lock uninstall |
| Sessions and current runtime state | Current process and security lifecycle only |

No retained category has a rolling historical retention period.
