# Software Design Specification

## Version 1.0.0

## 9. Protected Application Management Design

### 9.1 Application discovery

The application requests the set of user-launchable applications visible under Android package rules. For each eligible result it uses the package identifier as identity and obtains the current label and icon for display.

The application excludes itself, its lock surface, packages without a launchable entry, and any platform package explicitly identified as unsafe or meaningless to protect. It does not request visibility into hidden packages.

### 9.2 Selection model

Record existence in the protected-app store means that the package is selected for protection. There is no group, schedule, category, rule assignment, exception, priority, or separate enabled state unless a retained storage migration requires temporary compatibility.

The list combines current installed-app information with the stored selected package identifiers. Display metadata is refreshed from Android and is not authoritative persistent data.

### 9.3 Search and ordering

Search is performed over the current display label and, where the user-experience design permits, the package identifier. It does not require a separate searchable database catalog. Matching is case-insensitive and updates locally without network access.

The default order is a stable user-readable label order. Selected applications may be shown first if that behavior is defined by the user-experience specification. Search and sorting do not alter protection state.

### 9.4 Enabling protection

Enabling protection proceeds as follows:

1. confirm that setup and required Usage Access are understood;
2. validate that the package is still installed and eligible;
3. insert the unique package identifier atomically;
4. update the in-memory protected set only after commit;
5. ensure the protection service is requested when the first package becomes protected; and
6. refresh protection health.

A duplicate selection produces the already-selected state and does not create another record.

### 9.5 Removing protection

Removing protection is a protection-reducing action and requires current authentication. After confirmation:

1. delete the package identifier atomically;
2. update the in-memory protected set after commit;
3. invalidate any session and pending lock request for that package;
4. stop foreground checks when no protected packages remain; and
5. refresh health and the essential service notification.

A failed deletion leaves the application protected and explains that the change was not saved.

### 9.6 Installation, update, and removal

Newly installed and reinstalled applications are not protected until selected. An application update that preserves the package identifier preserves protection. A confirmed uninstall removes the active protected record and any session for that package.

If Android package information is temporarily unavailable, the stored record is not silently deleted. The application retries discovery and shows unavailable metadata without weakening the stored selection. Removal occurs only after Android confirms the package is no longer installed.

### 9.7 Privacy

The protected-app list is confidential configuration. It is stored in the encrypted relational database, omitted from notifications and logs, excluded from backup, and displayed only after authentication when a PIN exists.
