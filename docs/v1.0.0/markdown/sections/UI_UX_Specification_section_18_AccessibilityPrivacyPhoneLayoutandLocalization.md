# UI/UX Specification

> Version 1.0.0

## 18. Accessibility, Privacy, Phone Layout, and Localization

#### 18.1 Screen Reader and Focus

- Every screen exposes a unique title and logical heading order.
- Controls expose name, role, value, state, and action; decorative imagery is excluded from traversal.
- Focus follows reading and task order, stays inside modals, and returns to the invoking control after dismissal or handoff.
- Dynamic errors, protection changes, and completion results are announced once at an appropriate priority.
- PIN entry announces entered count and state but never digit values.

#### 18.2 Visual and Motor Accessibility

Normal text shall meet at least 4.5:1 contrast; large text and meaningful non-text boundaries shall meet at least 3:1. Meaning shall not rely on color. Primary journeys shall remain complete at 200 percent Android font scaling and increased display size without clipping, overlap, or hidden actions. Touch targets shall be at least 48 dp.

#### 18.3 Reduced Motion and Time

Non-essential motion shall be removed or simplified when reduced motion is requested. User input shall not depend on rapid timing. Security retry delay and session expiry are controlled by policy and shall be explained; visual animation shall not lengthen or shorten them.

#### 18.4 Screenshot, Recents, and Notification Privacy

Credential, protected-app lock, step-up, lockout, and sensitive configuration surfaces shall use secure screenshot and recents treatment where Android supports it. Public Welcome and help may be capturable only when no local configuration is visible. Notifications are masked by default and contain no protected app name or sensitive diagnostic detail.

#### 18.5 Phone Layout and Orientation

Portrait is the reference layout. In landscape, content may scroll or reorganize into a compact arrangement, but the title, current state, secret input, fallback, primary action, and safe cancellation shall remain reachable. The design shall not require two-pane views, navigation rails, or large-screen-specific assets.

Split-screen, picture-in-picture, and recents shall fail safely: a protected target shall not be displayed beside an authentication prompt, the cover shall remain focused and opaque, and an unsupported window state shall cancel or move the target away rather than expose it.

#### 18.6 Localization and RTL

All strings, plurals, dates, times, durations, and numbers shall use locale-aware resources. Do not concatenate sentence fragments or embed essential text in images. Layouts shall tolerate at least 30 percent longer representative strings. Navigation and directional icons mirror in right-to-left locales; numeric PIN order and Android-defined identifiers follow platform conventions.
