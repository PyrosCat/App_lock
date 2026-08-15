# Software Requirements Specification

## Version 1.0.0

## Section 3 Protected Applications Management

#### FR-056 - Protected Application Selection

The application shall display eligible installed applications and allow the user to enable or disable protection for each application individually.

Acceptance criteria:

- Each row clearly identifies the application and current protection state.
- A change is confirmed visually and becomes active without restarting App Lock.
- App Lock and operating-system components that cannot be safely protected are not offered as eligible targets.

#### FR-057 - Application Search

The application shall allow installed applications to be located by their user-visible name.

Acceptance criteria:

- Results update as text is entered and include partial name matches.
- Clearing the query restores the full eligible list.
- Search does not expose internal identifiers in the primary interface.

#### FR-060 - Enable/Disable Protection

The application shall allow protection to be enabled or disabled for one application at a time.

Acceptance criteria:

- Enabling protection requires a ready authentication and protection configuration.
- Disabling protection from settings requires current authentication.
- The resulting status is reflected in both the application list and active protection policy.

#### FR-072 - Protected Application Icons

Protected-application management shall use a clear visual and textual indication of protection state.

Acceptance criteria:

- Protection state is not communicated by color alone.
- Icons remain distinguishable at supported text and display scaling.
- Screen readers announce the application name and protection state together.

#### FR-073 - Application Information

The application shall show only the information needed to identify an eligible application and understand its current App Lock status.

Acceptance criteria:

- The interface includes the user-visible application name, icon, and current protection state.
- Technical identifiers and installation history are not required in the primary flow.
- No use history, unlock count, or session statistics are shown.

#### FR-078 - Application Removal Cleanup

The application shall clean up stale local protection data after an application is uninstalled.

Acceptance criteria:

- Only data associated with the removed application is deleted.
- Cleanup is safe if the application is removed while App Lock is not running.
- Reinstallation does not restore the old protection state automatically.
