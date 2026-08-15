**Requirements**

**Section 2 – Lock Detection Engine (FR-026 – FR-055)**

**FR-026 – Foreground Application Detection**

**Priority:** Critical

**Description:**\
The system shall continuously monitor the device's foreground application to determine whether it is configured as a protected application.

**Dependencies:**

- Accessibility Service

- UsageStatsManager

**Acceptance Criteria:**

- Foreground application detected within 500 milliseconds.

- Protected applications identified accurately.

- Non-protected applications ignored.

**FR-027 – Lock Screen Display**

**Priority:** Critical

**Description:**\
The system shall immediately display the authentication overlay whenever a protected application is launched and authentication is required.

**Acceptance Criteria:**

- Overlay appears before application content becomes visible.

- Overlay blocks user interaction.

- Unlock process begins automatically.

**FR-028 – Overlay Security**

**Priority:** Critical

**Description:**\
The authentication overlay shall prevent interaction with the protected application until authentication succeeds.

**Acceptance Criteria:**

- Touch events blocked.

- Back button configurable.

- Protected application inaccessible.

**FR-029 – Unlock Session Validation**

**Priority:** High

**Description:**\
The system shall verify whether an existing authentication session permits immediate access before displaying the lock screen.

**Acceptance Criteria:**

- Session expiration evaluated.

- Valid sessions bypass authentication.

- Expired sessions require authentication.

**FR-030 – Immediate Relock**

**Priority:** High

**Description:**\
The system shall optionally relock protected applications immediately after the user exits the application.

**Acceptance Criteria:**

- Feature configurable.

- Relock occurs instantly.

- Next launch requires authentication.

**FR-031 – Screen-Off Relock**

**Priority:** High

**Description:**\
The system shall relock all protected applications whenever the device screen turns off.

**Acceptance Criteria:**

- Screen-off event detected.

- Authentication sessions invalidated.

- Protected applications require authentication after wake.

**FR-032 – Timeout-Based Relock**

**Priority:** High

**Description:**\
The system shall automatically relock protected applications after a configurable inactivity timeout.

**Acceptance Criteria:**

- Timeout configurable.

- Timer resets after successful unlock.

- Timer pauses when application closes.

**FR-033 – Device Restart Protection**

**Priority:** Critical

**Description:**\
The system shall invalidate all authentication sessions following device reboot.

**Acceptance Criteria:**

- Sessions cleared.

- Protected applications remain locked.

- User authentication required.

**FR-034 – Lock Engine Initialization**

**Priority:** Critical

**Description:**\
The lock detection engine shall automatically initialize during device startup.

**Dependencies:**

- BOOT_COMPLETED Broadcast Receiver

**Acceptance Criteria:**

- Service starts automatically.

- No user interaction required.

- Engine operational after boot.

**FR-035 – Background Service Persistence**

**Priority:** Critical

**Description:**\
The system shall maintain continuous background monitoring while minimizing battery consumption.

**Acceptance Criteria:**

- Monitoring resumes after interruptions.

- Battery usage remains within defined limits.

- Android restrictions handled gracefully.

**FR-036 – Protected Application List Monitoring**

**Priority:** High

**Description:**\
The system shall dynamically monitor changes to the list of protected applications.

**Acceptance Criteria:**

- Newly protected applications become active immediately.

- Removed applications no longer trigger authentication.

- No application restart required.

**FR-037 – Newly Installed Application Detection**

**Priority:** Medium

**Description:**\
The system shall detect newly installed applications and optionally recommend adding them to protection.

**Acceptance Criteria:**

- Installation detected automatically.

- User notification displayed.

- Recommendation configurable.

**FR-038 – Application Removal Detection**

**Priority:** Medium

**Description:**\
The system shall detect removal of protected applications.

**Acceptance Criteria:**

- Database updated.

- Obsolete entries removed.

- User notified if enabled.

**FR-039 – Multi-Window Support**

**Priority:** Medium

**Description:**\
The system shall correctly identify protected applications operating in Android multi-window mode.

**Acceptance Criteria:**

- Split-screen supported.

- Overlay correctly positioned.

- Unauthorized access prevented.

**FR-040 – Picture-in-Picture Handling**

**Priority:** Medium

**Description:**\
The system shall determine authentication requirements for Picture-in-Picture applications.

**Acceptance Criteria:**

- PiP applications evaluated.

- Lock policy enforced.

- User experience maintained.

**FR-041 – Recent Applications Protection**

**Priority:** High

**Description:**\
The system shall prevent protected application content from appearing in the Recent Apps screen.

**Acceptance Criteria:**

- Preview blurred or replaced.

- Screenshot unavailable.

- Feature configurable.

**FR-042 – Lock Policy Evaluation**

**Priority:** Critical

**Description:**\
The system shall evaluate all configured lock policies before granting access.

**Policies include:**

- Timeout

- Schedule

- Location

- Wi-Fi

- Authentication session

**Acceptance Criteria:**

- Policies evaluated sequentially.

- Conflicts resolved consistently.

- Decision completed within 100 milliseconds.

**FR-043 – Accessibility Event Monitoring**

**Priority:** Critical

**Description:**\
The system shall monitor accessibility events required for application launch detection.

**Acceptance Criteria:**

- Window changes detected.

- Package names identified.

- Performance optimized.

**FR-044 – Overlay Permission Verification**

**Priority:** High

**Description:**\
The system shall verify overlay permission before enabling application protection.

**Acceptance Criteria:**

- Missing permission detected.

- User guided through setup.

- Protection disabled until granted.

**FR-045 – Accessibility Permission Verification**

**Priority:** Critical

**Description:**\
The system shall verify Accessibility Service permission before enabling monitoring.

**Acceptance Criteria:**

- Permission status checked.

- User notified if disabled.

- Monitoring suspended until enabled.

**FR-046 – Lock Engine Failure Recovery**

**Priority:** High

**Description:**\
The system shall automatically recover from unexpected monitoring service failures.

**Acceptance Criteria:**

- Failure detected.

- Service restarted automatically.

- User notified if recovery fails.

**FR-047 – Duplicate Overlay Prevention**

**Priority:** Medium

**Description:**\
The system shall prevent multiple authentication overlays from appearing simultaneously.

**Acceptance Criteria:**

- Single overlay instance.

- Duplicate requests ignored.

- UI remains responsive.

**FR-048 – Overlay Timeout**

**Priority:** Medium

**Description:**\
The system shall automatically dismiss inactive authentication overlays after a configurable period.

**Acceptance Criteria:**

- Timeout configurable.

- Protected application remains locked.

- Session cleared.

**FR-049 – Lock Engine Performance**

**Priority:** High

**Description:**\
The lock detection engine shall detect protected application launches within 500 milliseconds.

**Acceptance Criteria:**

- Average detection time ≤500 ms.

- Peak detection ≤750 ms.

- No noticeable user delay.

**FR-050 – Lock Engine Battery Optimization**

**Priority:** High

**Description:**\
The system shall optimize monitoring operations to minimize battery consumption.

**Acceptance Criteria:**

- Idle CPU usage minimized.

- Background wake-ups reduced.

- Battery consumption documented.

**FR-051 – Application Switching Detection**

**Priority:** High

**Description:**\
The system shall detect transitions between protected and unprotected applications.

**Acceptance Criteria:**

- Transitions identified accurately.

- Appropriate lock policies applied.

- No unauthorized exposure.

**FR-052 – Home Screen Transition Handling**

**Priority:** Medium

**Description:**\
The system shall detect transitions to the device home screen and update authentication sessions accordingly.

**Acceptance Criteria:**

- Home button detected.

- Session managed according to timeout policy.

- Behavior configurable.

**FR-053 – Task Switching Handling**

**Priority:** High

**Description:**\
The system shall detect task switching through Android's Recent Apps interface.

**Acceptance Criteria:**

- Recent Apps navigation monitored.

- Protected tasks remain secured.

- Authentication triggered when required.

**FR-054 – Lock Event Logging**

**Priority:** Medium

**Description:**\
The system shall record all lock events for diagnostic and audit purposes.

**Acceptance Criteria:**

- Timestamp recorded.

- Application package stored.

- Event encrypted.

**FR-055 – Lock Engine Health Monitoring**

**Priority:** Medium

**Description:**\
The system shall continuously monitor the operational health of the lock detection engine.

**Acceptance Criteria:**

- Service status monitored.

- Failures logged.

- Automatic recovery attempted.
