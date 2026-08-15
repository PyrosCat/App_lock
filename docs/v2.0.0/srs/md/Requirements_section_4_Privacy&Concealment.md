**Requirements**

**Section 4 – Privacy & Concealment Features (FR-081 – FR-105)**

**FR-081 – Intruder Selfie Capture**

**Priority:** High

**Description:**\
The system shall automatically capture a photograph using the device's front-facing camera after a configurable number of consecutive failed authentication attempts.

**Dependencies:**

- Camera Permission

**Acceptance Criteria:**

- Default threshold is five failed attempts.

- Threshold is user configurable.

- Image captured without displaying the camera interface.

- Image stored in encrypted storage.

**FR-082 – Intruder Event Logging**

**Priority:** High

**Description:**\
The system shall create an encrypted log entry for every intruder event.

**Acceptance Criteria:**\
Each record shall contain:

- Timestamp

- Protected application

- Authentication method

- Number of failed attempts

- Device battery level

- Device orientation

**FR-083 – Intruder Location Capture**

**Priority:** Medium

**Description:**\
The system shall optionally record the device's approximate location during an intruder event.

**Dependencies:**

- Location Permission

**Acceptance Criteria:**

- Feature disabled by default.

- User consent required.

- Failure to obtain location shall not interrupt event logging.

**FR-084 – Intruder Notification**

**Priority:** Medium

**Description:**\
The system shall optionally notify the device owner when an intruder event occurs.

**Acceptance Criteria:**\
Notification methods may include:

- Local notification

- Email

- Encrypted cloud notification

**FR-085 – Intruder History Viewer**

**Priority:** Medium

**Description:**\
The system shall provide a secure interface for viewing historical intruder events.

**Acceptance Criteria:**\
The interface shall display:

- Photograph

- Date and time

- Protected application

- Number of failed attempts

Users may delete individual or all records.

**FR-086 – Calculator Disguise Mode**

**Priority:** High

**Description:**\
The system shall provide an optional disguise mode in which the launcher icon and primary interface appear as a functional calculator.

**Acceptance Criteria:**

- Calculator performs basic arithmetic.

- Hidden authentication sequence opens App Lock.

- No indication of App Lock functionality is visible.

**FR-087 – Alternative Application Disguises**

**Priority:** Medium

**Description:**\
The system shall support multiple disguise profiles.

**Examples:**

- Calculator

- Clock

- Compass

- Notes

- Weather

- Unit Converter

**Acceptance Criteria:**

- User selects disguise.

- Launcher updates automatically.

- Original branding hidden.

**FR-088 – Launcher Icon Camouflage**

**Priority:** High

**Description:**\
The system shall allow the launcher icon to be replaced with an alternative icon.

**Acceptance Criteria:**

- Multiple icon sets supported.

- Icon changes without reinstalling the application.

- Changes survive reboot.

**FR-089 – Application Name Camouflage**

**Priority:** High

**Description:**\
The system shall allow the displayed application name to match the selected disguise.

**Acceptance Criteria:**\
Examples:

- Calculator

- Compass

- Settings

- Utilities

Application label updates correctly throughout the launcher.

**FR-090 – Fake Crash Screen**

**Priority:** High

**Description:**\
The system shall optionally display a simulated application crash when a protected application is opened.

**Acceptance Criteria:**

- Screen resembles Android system crash dialog.

- User-configurable.

- Hidden gesture opens authentication screen.

**FR-091 – Fake Error Message Customization**

**Priority:** Low

**Description:**\
The system shall allow users to customize fake crash messages.

**Acceptance Criteria:**\
User may edit:

- Title

- Message

- Button labels

- Display duration

**FR-092 – Hidden Unlock Gesture**

**Priority:** High

**Description:**\
The system shall support one or more hidden gestures to reveal the authentication screen while in disguise mode.

**Examples:**

- Triple tap

- Long press

- Swipe pattern

- Secret calculator equation

**Acceptance Criteria:**\
Gesture configurable.\
False activations minimized.

**FR-093 – Notification Masking**

**Priority:** High

**Description:**\
The system shall optionally hide notification content originating from protected applications.

**Acceptance Criteria:**\
Example:

Instead of

"John: Meet me at 7 PM"

Display

"New Notification"

**FR-094 – Notification Category Masking**

**Priority:** Medium

**Description:**\
The system shall allow masking rules to be configured separately for each protected application.

**Acceptance Criteria:**\
User may choose:

- Hide sender

- Hide content

- Hide notification entirely

- Show generic notification

**FR-095 – Recent Apps Preview Protection**

**Priority:** Critical

**Description:**\
The system shall prevent sensitive application content from appearing in the Android Recent Apps screen.

**Acceptance Criteria:**\
Options include:

- Blur preview

- Solid color

- Custom image

- Blank preview

**FR-096 – Screenshot Prevention**

**Priority:** High

**Description:**\
The system shall prevent screenshots and screen recordings while the authentication overlay is displayed.

**Acceptance Criteria:**

- FLAG_SECURE enabled.

- Screenshots blocked.

- Screen recording prevented where supported.

**FR-097 – Shoulder Surfing Protection**

**Priority:** High

**Description:**\
The system shall provide optional protections against observation during authentication.

**Acceptance Criteria:**\
Features include:

- Randomized keypad

- Hidden PIN entry

- Invisible pattern lines

- Delayed character masking

**FR-098 – Invisible Pattern Mode**

**Priority:** Medium

**Description:**\
The system shall optionally hide pattern lines while users enter authentication patterns.

**Acceptance Criteria:**\
Pattern remains functional.\
Nodes briefly highlighted.\
Accessibility unaffected.

**FR-099 – Secure Keyboard Mode**

**Priority:** High

**Description:**\
The system shall provide a secure on-screen keypad that prevents predictive learning and accessibility leakage where permitted by Android.

**Acceptance Criteria:**

- Clipboard disabled during PIN entry.

- Auto-fill disabled.

- Predictive text disabled.

**FR-100 – Clipboard Protection**

**Priority:** Medium

**Description:**\
The system shall prevent authentication credentials from being copied to or pasted from the system clipboard.

**Acceptance Criteria:**

- Copy operations blocked.

- Paste operations ignored.

- Clipboard contents unchanged.

**FR-101 – Privacy Dashboard**

**Priority:** Medium

**Description:**\
The system shall provide a dashboard summarizing privacy-related events.

**Displayed Information:**

- Failed unlock attempts

- Intruder events

- Last successful authentication

- Hidden notifications

- Screenshot prevention status

**FR-102 – Stealth Mode**

**Priority:** High

**Description:**\
The system shall optionally remove the application from the launcher while remaining fully operational.

**Acceptance Criteria:**

- Launcher icon hidden.

- Hidden launch method available.

- Feature reversible.

**FR-103 – Secret Launch Methods**

**Priority:** High

**Description:**\
The system shall support multiple methods of launching the hidden application.

**Examples:**

- Dialer code

- Calculator equation

- Secret widget

- Notification shortcut

- Quick Settings tile

**Acceptance Criteria:**\
At least one recovery launch method always available.

**FR-104 – Decoy Authentication**

**Priority:** Medium

**Description:**\
The system shall optionally support a decoy authentication credential that grants access to a limited or fake environment.

**Acceptance Criteria:**

- Separate credential maintained.

- Decoy environment configurable.

- True protected data remains inaccessible.

**FR-105 – Privacy Feature Management**

**Priority:** Medium

**Description:**\
The system shall allow every privacy and concealment feature to be individually enabled, disabled, and configured.

**Acceptance Criteria:**

- Settings organized logically.

- Changes applied immediately.

- Configuration backed up with user settings.

- Default values restored on request.
