# Software Design Specification

## Version 1.0.0

## 3. Constraints and Assumptions

### 3.1 Android and phone constraints

The minimum supported platform is Android 11 and the maximum verified platform is Android 15. The application targets phone-sized displays. It does not provide expanded navigation, dual-pane layouts, hinge-aware presentation, external-display behavior, or desktop-window optimization.

Android is free to reclaim the process and to apply manufacturer-specific battery restrictions. The design therefore treats a running process as temporary and security-relevant configuration as durable. It does not assume that a foreground service is immortal.

### 3.2 Foreground-detection baseline

Usage Access is the sole foreground-application detection baseline. The application directs the user to the Android Usage Access screen, rechecks access when the user returns, and reads recent usage information to identify the current launchable foreground package.

The application shall not declare, request, or operate an Accessibility service. It shall not show Accessibility as an optional enhancement, fallback, setup step, or recovery path.

Foreground checks run only when setup is sufficiently complete and at least one application is selected for protection. The check interval is bounded to meet the applicable detection-response requirement without an unbounded busy loop. When no applications are selected, protection polling stops.

Usage data is used only for the immediate protection decision. The application does not retain a history of applications opened, durations of use, or user activity.

### 3.3 Lock-presentation constraints

The lock screen is an application-owned, non-exported surface dedicated to authenticating access to the current protected package. It does not appear in recents, does not expose protected package content in its preview, and prevents navigation into the protected application until authentication succeeds or the user leaves the protected task.

Android background-activity limits may prevent or delay presentation in some device states. The software detects a presentation failure or timeout where feasible and reports Protection interrupted. It does not describe the lock screen as a system-level security boundary and does not guarantee that the protected application can never become briefly visible under every operating-system condition.

Only one lock screen may be active. A presentation request contains the target package and a fresh request identity. Completion is accepted only for the current request and current target.

### 3.4 Notification and service constraints

The retained protection service is visible to Android and the user through the required persistent notification. On Android versions requiring runtime notification permission, the application explains why the permission supports protection status and requests it in context.

Denial does not produce repeated prompts. The in-app health surface remains available. If Android allows the service to operate while suppressing the visible notification, health is Degraded; if the selected service mode cannot lawfully operate, health is Action required or Protection interrupted.

### 3.5 Package visibility

The application queries only launchable applications allowed by Android package-visibility rules. It does not request visibility into all installed packages. Its own package and packages without a user-launchable entry are excluded from selection.

Application labels and icons are untrusted display data. They are rendered safely and do not determine identity. The package identifier supplied by Android is the protection identity.

### 3.6 Time and clock behavior

PIN lockout state must survive process death and reboot. Unlock-session grace timing is memory-only and uses a monotonic elapsed-time source while the process remains alive. Wall-clock changes must not extend a session or reduce an active lockout beyond the defined upper bound.

No schedule, calendar, time zone, or daylight-saving rule affects protection.

### 3.7 Offline and local-only operation

All retained functions work without a network connection. The application contains no account session, server token, remote policy, cloud database, remote log destination, or online recovery dependency.

### 3.8 Accessibility and privacy constraints

All actionable controls meet the retained touch-target requirement and expose clear labels and states. PIN digits are not announced. Biometric prompts use platform accessibility behavior. Focus returns to the meaningful recovery control after an error. Text scaling must not hide the PIN fallback, permission action, or protection status.

The application lock screen, authenticated settings, and any view showing protected-app choices are excluded from screenshots and recents previews where Android permits. Notifications remain masked regardless of protected package.
