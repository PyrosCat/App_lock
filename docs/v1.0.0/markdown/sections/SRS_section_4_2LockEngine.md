# Software Requirements Specification

## Version 1.0.0

## Section 2 Lock Engine

#### FR-026 - Foreground Application Detection

The application shall use Android Usage Access as the single baseline for determining which application is in the foreground.

Acceptance criteria:

- Protection cannot be declared active until Usage Access is granted and verified.
- Eligible protected applications are identified accurately under supported phone conditions.
- No App Lock Accessibility service is requested, offered, or required.

#### FR-027 - Lock Screen Display

The application shall present its authentication surface, drawn as a system overlay over the protected application, when a protected application becomes active and no valid protection session applies.

Acceptance criteria:

- The protection surface appears before avoidable interaction with protected content.
- The surface identifies the authentication task without exposing sensitive application content.
- Successful authentication is required before access continues.

#### FR-028 - Overlay Security

The protection presentation shall prevent interaction with the protected application until authentication succeeds.

Acceptance criteria:

- Back, cancellation, task changes, and interruptions do not reveal or activate protected content.
- Touch and keyboard input cannot pass through to the protected application.
- Failure to maintain the presentation results in a safe exit and a Protection interrupted state.

#### FR-029 - Unlock Session Validation

The application shall validate the current global protection session before deciding whether to present authentication.

Acceptance criteria:

- A valid unexpired session permits access only under the configured global policy.
- An expired, invalidated, or unverifiable session requires authentication.
- Session validation completes before access is granted.

#### FR-030 - Immediate Relock

The application shall provide one global option to require authentication again after leaving a protected application.

Acceptance criteria:

- When enabled, returning to any protected application requires authentication unless a stricter mandatory event already ended the session.
- When disabled, the global relock rule still applies to each package-scoped session.
- No per-application immediate-relock option is shown.

#### FR-031 - Screen-Off Relock

The application shall invalidate active protection sessions when the phone screen turns off.

Acceptance criteria:

- A protected application requires authentication after the screen is turned on again.
- Session invalidation persists if the application process stops while the screen is off.
- No protected content is made available solely because the device was unlocked.

#### FR-032 - Timeout-Based Relock

The application shall support one global inactivity timeout for protection sessions.

Acceptance criteria:

- Expiration requires authentication on the next protected-application access.
- The current setting is presented consistently in settings and applied to every protected application.
- No per-application, profile, or schedule timeout exists.

#### FR-033 - Device Restart Protection

The application shall treat every device restart as an invalidation of all protection sessions.

Acceptance criteria:

- No pre-restart protection session survives reboot.
- Protected applications require authentication after detection resumes.
- Protection status remains Unknown or not verified, or Action required, until required post-restart checks complete.

#### FR-034 - Lock Engine Initialization

The application shall restore the core protection path after device restart when Android permits execution.

Acceptance criteria:

- Protected-application selections and global policy load without user re-entry.
- Required health checks run before a protected state is reported.
- If Android prevents timely startup, the user is informed at the next available opportunity and receives recovery guidance.

#### FR-035 - Background Service Persistence

The application shall maintain or restore core monitoring through supported Android lifecycle conditions while minimizing resource use.

Acceptance criteria:

- Normal backgrounding, screen changes, and process recreation do not silently disable protection.
- When Android stops required work, the application attempts an approved recovery and verifies the result.
- Unrecoverable interruption produces a truthful Action required notification or status.

#### FR-036 - Protected Application List Monitoring

Changes to the protected-application list shall take effect without restarting App Lock.

Acceptance criteria:

- Newly enabled protection is used on the next applicable access.
- Disabled protection no longer presents App Lock after the change is confirmed.
- The displayed selection state and the active policy remain consistent.

#### FR-038 - Application Removal Detection

The application shall remove obsolete protection records when an installed protected application is removed.

Acceptance criteria:

- Removed applications no longer appear as active protected targets.
- Cleanup does not alter other protected-application selections.
- Reinstallation is treated as a new eligible application and is not silently protected from a stale record.

#### FR-039 - Multi-Window Support

The application shall fail safely when a protected application is used in phone split-screen or another supported resizable window state.

Acceptance criteria:

- An unauthenticated protected application cannot be intentionally activated through split-screen.
- If reliable lock presentation is unavailable in the current window state, protected access is denied and the limitation is explained.
- No tablet, foldable, or multi-pane optimization is required.

#### FR-040 - Picture-in-Picture Handling

The application shall apply a safe rule when a protected application enters or returns from picture-in-picture on a supported phone.

Acceptance criteria:

- Picture-in-picture does not create a new valid protection session.
- Returning to interactive protected content requires authentication when the global policy requires it.
- If the operating system prevents reliable protection, the application denies continued access where possible and reports the limitation truthfully.

#### FR-041 - Recent Applications Protection

App Lock's sensitive surfaces shall not expose protected application content in the Android recent-apps view.

Acceptance criteria:

- Authentication and sensitive settings previews are blanked or replaced.
- Returning from recents reevaluates the current protection session.
- A captured recent-app preview does not reveal a PIN, biometric result, or diagnostic detail.

#### FR-042 - Lock Policy Evaluation

Before granting access, the application shall evaluate only the enabled protection state, the session for the current protected application, global relock rules, and current protection health.

Acceptance criteria:

- The result is deterministic for the same inputs.
- A missing or invalid input defaults to requiring authentication or denying access.
- No schedule, location, network, profile, or per-application policy participates in the decision.

#### FR-044 - Lock-Presentation (Overlay) Capability Verification

The application shall verify that the operating system grants the "Display over other apps" (system overlay) permission required to present the lock, before a protected state is claimed. This permission is mandatory: because detection runs from a background service, the lock is drawn as a system overlay, and without it protection cannot be enforced.

Acceptance criteria:

- A missing or revoked overlay permission is detected before a protected state is claimed, and results in Action required rather than a protected state.
- Guidance identifies the exact user action needed to grant "Display over other apps" without promising that Android settings can be changed automatically.
- Protection is rechecked when the user returns from Android settings.

#### FR-046 - Lock Engine Failure Recovery

The application shall attempt to restore the core protection path after a recoverable monitoring or presentation failure.

Acceptance criteria:

- Recovery never creates or extends an authentication session.
- The protected state is reported only after the restored path passes verification.
- Failed recovery produces an Action required state and clear guidance.

#### FR-047 - Duplicate Overlay Prevention

The application shall prevent simultaneous or stacked authentication presentations for the same protection event.

Acceptance criteria:

- Rapid application switching produces at most one active authentication task.
- Repeated detection events do not reset an active retry delay or create multiple sessions.
- The visible authentication state remains responsive and consistent.

#### FR-048 - Overlay Timeout

An inactive protection presentation shall use the timeout defined by FR-008.

Acceptance criteria:

- Expiration grants no access and creates no session.
- The protected application remains inaccessible after dismissal.
- A later attempt starts a fresh protection presentation.

#### FR-049 - Lock Engine Performance

The application shall detect a protected-application transition and begin enforcement within the approved lock-detection target under normal supported-phone conditions.

Acceptance criteria:

- The measured event begins when Usage Access reports the applicable foreground transition and ends when App Lock begins its protection response.
- The median and upper-percentile targets are stated consistently with the NFR.
- No avoidable protected-content exposure is accepted as a performance tradeoff.

#### FR-050 - Lock Engine Battery Optimization

Core monitoring shall minimize unnecessary wake-ups, polling, and background activity without reducing required protection.

Acceptance criteria:

- Idle monitoring remains within the approved resource limits.
- Nonessential diagnostics and cleanup may be deferred before protection work.
- Battery-saving behavior never converts a Protection interrupted state into a false Protected state.

#### FR-051 - Application Switching Detection

The application shall reevaluate protection when the user switches between protected and unprotected applications.

Acceptance criteria:

- Every transition to a protected application applies the current global policy.
- Rapid repeated switching does not reuse an expired or invalid session.
- Transitions to unprotected applications do not present App Lock unnecessarily.

#### FR-052 - Home Screen Transition Handling

The application shall update protection-session state when the user leaves a protected application for the device home surface.

Acceptance criteria:

- Global immediate-relock and timeout rules are applied consistently.
- Returning from Home requires authentication when the current policy requires it.
- Home transition handling does not depend on profiles or schedules.

#### FR-053 - Task Switching Handling

The application shall reevaluate protection when a protected task is selected from Android recents.

Acceptance criteria:

- An invalid or expired session causes authentication before continued access.
- Selecting another task cannot dismiss authentication into protected content.
- Rapid task switching remains consistent with FR-047.

#### FR-054 - Lock Event Logging

The application shall retain only bounded local diagnostic records needed to identify lock-presentation and transition failures.

Acceptance criteria:

- Records exclude protected content and user-entered credentials.
- Records are automatically bounded and removed by destructive reset.
- No user-visible lock history, report, or export is provided.

#### FR-055 - Lock Engine Health Monitoring

The application shall maintain a current health result for Usage Access, policy readiness, session readiness, core monitoring, and lock presentation.

Acceptance criteria:

- The health result maps deterministically to Protected, Degraded, Protection interrupted, Action required, or Unknown or not verified.
- A failed required check cannot be displayed as protected.
- Recovery is followed by a new verification before the state improves.
