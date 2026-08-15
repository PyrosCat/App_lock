# UI/UX Specification

> Version 1.0.0

## 4. Information Architecture and Navigation

#### 4.1 Application Hierarchy

After setup, App Lock has three primary destinations:

- Protection: current status, protected-application count, the highest-priority action, and access to detailed health.
- Apps: installed eligible applications, search, protection state, and individual protection changes.
- Settings: authentication, privacy, notifications, protection access, diagnostics, help, and destructive reset.

Vault, automation, security-event history, backup, restore, and administration are not destinations in Version 1.0.0.

#### 4.2 Top-Level Navigation

Compact phone layouts shall use bottom navigation for Protection, Apps, and Settings. Each destination shall have a text label and icon. The selected destination shall be communicated by label, icon state, accessibility state, and container treatment rather than color alone.

The bottom navigation is hidden during initial setup, protected-application lock presentation, App Lock authentication, lockout, step-up confirmation, full-screen Android handoff explanation, and destructive reset confirmation.

#### 4.3 Back and Up Behavior

- Back within a detail screen returns to the invoking parent and restores the prior safe list position or settings row.
- Back from a modal has the same result as Cancel unless explicit confirmation is required to avoid accidental loss.
- Back from protected-application authentication cancels and returns away from the protected target; it never dismisses the cover onto that target.
- Back from the authenticated root follows normal Android task behavior without ending valid sessions unless the session policy requires it.
- Up is shown only when a parent exists within App Lock. It is not shown on the three top-level destinations.

#### 4.4 External Entry

Notifications may open Protection Health or the relevant access row, but configuration-sensitive content shall remain behind the App Lock gate. Android settings and biometric prompts are the only required external handoffs. There are no public deep links to protected app lists, settings, or diagnostic information.

An unavailable, stale, or already resolved external target shall open the safest authenticated parent and explain that the condition changed. External entry shall never replay a destructive action or create an authentication session.

#### 4.5 Modal Behavior

Dialogs are used for short, bounded decisions. A full screen is used when authentication, long consequences, system handoff preparation, or large text would make a dialog unsafe. Modal focus remains inside the surface; dismissal returns focus to the invoking control.

#### 4.6 Task Restoration

Safe restoration may include the current top-level destination, list scroll position, search term, non-secret setup completion, and a settings row that initiated an Android handoff. PIN digits, biometric prompt state, a success result, lock coverage assumptions, and step-up authorization shall never be restored from presentation state.

After process recreation, App Lock shall re-evaluate configuration, Android access, protection health, target identity, and session validity before restoring a sensitive destination.
