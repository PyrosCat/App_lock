# UI/UX Specification

> Version 1.0.0

## 12. Settings and Administration

#### 12.1 Included Settings

Settings shall contain only:

- Authentication: change PIN, enable or disable eligible biometrics, choose the global relock behavior, and end active sessions.
- Privacy: protected-target identity on the lock surface where allowed, App Lock screenshot/recents behavior where configurable, and privacy explanations.
- Notifications: essential App Lock notification categories and lock-screen privacy.
- Protection access: current required-access states and recovery actions.
- Diagnostics: current protection check, relevant Android/access results, storage integrity result, and bounded recent failure summaries.
- About and support: version, supported Android range, privacy summary, help, and known platform limitations.
- Reset: reset non-security preferences or reset App Lock completely after authentication and explicit confirmation.

#### 12.2 Protection-Reducing Actions

Changing the PIN, ending sessions, disabling biometrics, changing relock behavior to a less restrictive option, removing protection, resetting settings that affect protection, and complete reset shall require a current authenticated context or step-up confirmation as defined by the security policy.

The confirmation shall name the affected scope and shall be invalidated if that scope changes before completion.

#### 12.3 Excluded Settings

There shall be no profiles, schedules, automation, Vault, backup, restore, recovery-password, intruder, event-history, disguise, device-administration, notification-listener, Accessibility-service, diagnostic-export, account, cloud, per-app policy, theme-picker, sound, or vibration settings.

## Part IV — Screen and Interaction Specifications
