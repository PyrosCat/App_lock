# Database Design Specification

## Version 1.0.0

## 17. Backup, Restore, and New-Device Behavior

### 17.1 Boundary

Version 1.0.0 creates no backup package, accepts no restore package, performs no cloud backup or synchronization, and does not participate in Android application-data backup.

### 17.2 New installation

A new installation, including installation on a different phone, begins with:

- no PIN verifier;
- no biometric preference;
- no protected package selection;
- no retry state;
- new database-opening material; and
- no unlock session.

The user completes setup and selects applications again.

### 17.3 Reinstallation

After uninstall and reinstall, prior local data is not expected to be available. The application does not search for an old database, external file, backup manifest, recovery password, or cloud account.

### 17.4 No restore staging

The design requires no restore parser, staging database, compatibility manifest, pre-restore snapshot, import transaction, backup password, restore verification, or post-restore reconciliation.
