# Software Requirements Specification

## Version 1.0.0

## Section 4 Privacy Protection

#### FR-095 - Recent Apps Preview Protection

The application shall prevent sensitive App Lock content from appearing in recent-app previews.

Acceptance criteria:

- PIN entry, biometric fallback, settings authentication, reset confirmation, and diagnostic details are obscured.
- Preview protection remains active when App Lock moves to the background unexpectedly.
- Returning from recents restores an appropriate safe state.

#### FR-096 - Screenshot Prevention

The application shall prevent screenshots and screen recording of authentication and other sensitive App Lock screens where Android supports that protection.

Acceptance criteria:

- PIN entry, biometric fallback, sensitive settings, reset confirmation, and diagnostics use screenshot protection.
- Failure of the operating system to enforce the restriction is not represented as guaranteed prevention.
- Non-sensitive help content may remain available to accessibility and support workflows.

#### FR-099 - Secure Keyboard Mode

PIN entry shall use a controlled numeric input surface that limits credential exposure.

Acceptance criteria:

- Copy, paste, autofill, predictive input, and readable credential retention are unavailable.
- Key controls remain operable with supported accessibility services.
- The interface does not reveal the complete PIN after entry.

#### FR-100 - Clipboard Protection

The application shall not place a PIN or other authentication secret on the system clipboard or accept it from the clipboard.

Acceptance criteria:

- Copy actions are unavailable for credential fields.
- Paste and autofill do not populate a PIN field.
- Existing unrelated clipboard content is not exposed or modified unnecessarily.

#### FR-105 - Privacy Feature Management

The application shall provide settings only for privacy behavior included in version 1.0.0.

Acceptance criteria:

- Settings explain recent-screen, screenshot, and App Lock notification privacy behavior.
- No intruder, concealment, notification-interception, Vault, or decoy setting is shown.
- A setting cannot weaken mandatory PIN-entry privacy.
