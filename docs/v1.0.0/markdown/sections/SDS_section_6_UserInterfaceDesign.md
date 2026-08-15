# Software Design Specification

## Version 1.0.0

## 6. User Interface Design

### 6.1 Retained screen groups

The presentation contains only the following functional areas:

- welcome and initial setup;
- PIN creation and confirmation;
- Usage Access explanation, Android handoff, and verification;
- optional biometric enablement;
- protected-application selection and search;
- protection dashboard and current health;
- application lock and PIN fallback;
- basic settings, including relock behavior and biometric preference;
- help and current diagnostic status; and
- destructive reset guidance through Android when local credentials or protected storage cannot be recovered.

There are no vault, backup, restore, profile, schedule, automation, intruder-event, event-history, export, or administrative screens.

### 6.2 Presentation state

Each screen exposes a complete state rather than a set of unrelated flags. A screen may be loading, ready, empty, action required, temporarily disabled, or failed. Security-relevant controls remain disabled until the required state is known.

The protected-app list has:

- loading while Android package information and current selections are read;
- ready with selectable eligible applications;
- empty when no eligible launchable applications are available;
- filtered empty when a search has no matches;
- disabled during a committed selection change; and
- error with a retry action when package or protected storage cannot be read.

### 6.3 Navigation

Unauthenticated entry into the application routes to setup if no PIN exists or to PIN authentication if a PIN exists. A deep link from a protection notification routes through the same authentication decision before showing protected settings.

Back from the lock screen does not reveal a protected application. It returns the user away from the protected task or leaves the lock in place according to Android navigation behavior. Cancellation never creates an unlocked session.

Returning from Android Usage Access or notification settings triggers an immediate capability recheck. The application does not assume that opening a settings screen changed anything.

### 6.4 Sensitive presentation

PIN entry uses masked positions and a randomized or fixed keypad only as specified by the user-experience requirements; this software design does not create a second keypad mode. The complete PIN is held only long enough to verify or create the protected verifier and is cleared from transient input after completion, cancellation, or failure.

Protected-app choices, security status detail, and authentication screens are protected from screenshots and recents previews where Android permits. The lock screen does not display the name or icon of the protected target unless the user-experience specification explicitly requires it and the privacy impact is accepted.

### 6.5 Accessibility

Controls expose purpose, state, and error text through the platform accessibility semantics. Focus begins at the screen title or the first required input, moves in visual order, and returns to the failed field or recovery action after validation.

PIN positions are announced as filled or empty without announcing digits. Lockout remaining time is announced when it changes meaningfully rather than every second. Color is never the sole indicator of Protected, Protection interrupted, or Action required state. Text scaling and landscape layout preserve the PIN fallback, primary recovery action, and system-settings handoff.

This accessibility design concerns the application’s user interface. The application does not provide an Accessibility service and does not request Accessibility access.

### 6.6 Restoration

Harmless list search, scroll position, and unsaved non-sensitive settings may survive screen recreation. PIN digits, biometric results, active lock requests, and authenticated screen access do not survive process death.

After recreation, the application re-evaluates setup, authentication, protected storage, Usage Access, and health before restoring a destination.
