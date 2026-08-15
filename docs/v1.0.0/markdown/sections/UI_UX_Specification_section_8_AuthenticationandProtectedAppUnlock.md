# UI/UX Specification

> Version 1.0.0

## 8. Authentication and Protected-App Unlock

#### 8.1 Authentication Contexts

Authentication occurs when opening protected App Lock configuration, unlocking a selected application without a valid session, changing the PIN, reducing protection, ending sessions, or confirming complete reset. The prompt shall state the purpose without unnecessarily naming a protected application.

#### 8.2 Protected-App Lock Presentation

The lock shall be opaque and cover the target before protected content is considered available. Back, Home, Recents, rotation, task switching, biometric cancellation, and any authentication error shall leave the target unauthorized. Cancellation shall return away from the protected target.

Rapid duplicate detections for the same target shall update one cover rather than stack multiple prompts. A target change shall invalidate the old request and rebuild the prompt for the newly verified target.

#### 8.3 PIN

PIN entry provides masked position indicators, digits 0–9, Delete, Unlock, and a safe cancellation action. Copy, paste, predictive retention, and autofill shall not apply. The accessibility tree may expose entered digit count but never digit values.

#### 8.4 Biometrics and Fallback

App Lock may offer the Android biometric prompt only when Android reports current eligibility and a PIN fallback exists. The application-owned surface shall explain why authentication is requested and shall keep “Use PIN” visible before and after the prompt.

Biometric cancellation, rejection, temporary unavailability, permanent lockout, missing enrollment, or changed enrollment creates no session. The next state shall be PIN entry or a clear Android enrollment handoff when the person deliberately requests it.

#### 8.5 Retry Delay and Lockout

Incorrect PIN attempts share one authoritative retry state across relevant authentication surfaces. During a delay or lockout, PIN submission and biometric retry are disabled as defined by the security policy. Rotation, clock changes, process recreation, reboot, or switching authentication presentation shall not shorten the authoritative delay.

The interface shall announce remaining time at meaningful intervals, not every second. On expiry, the originating authentication purpose returns with empty secret input.

#### 8.6 Sessions and Relock

A protected-application session belongs to one protected application; it is not a global bypass. A separate authenticated-settings state is limited to the current sensitive settings flow and does not authorize a protected application. The global relock choice may be Immediate, When screen turns off, or After ten seconds. There are no per-app timeouts or profiles.

<!-- table-widths: 2.25, 1.7, 2.55 -->
| Event | Session result | Presentation result |
| --- | --- | --- |
| Correct PIN or eligible biometric | Create only the requested valid session. | Open the verified destination. |
| Incorrect, cancelled, or expired authentication | No session. | Keep cover or return safely. |
| Screen off under screen-off policy | Invalidate applicable sessions. | Next protected entry locks. |
| Grace period expires | Invalidate applicable sessions. | Next protected entry locks. |
| Process death, reboot, PIN change, or reset | Invalidate all sessions. | Require authentication after recovery. |
| Protected app identity changes | Invalidate its session. | Revalidate target before any prompt. |

#### 8.7 Forgotten PIN

Version 1.0.0 cannot retrieve or replace a forgotten PIN while preserving local configuration. The information screen shall explain that the only supported path is Android-managed clearing of App Lock data or reinstalling the application, followed by setup again. It shall identify that the PIN, protected-app selections, settings, and local diagnostics will be removed.

The authenticated settings area may offer complete local reset after current authentication. It shall not imply that this action is a forgotten-PIN recovery method.
