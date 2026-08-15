# Threat Model

## Version 1.0.0

## 6. Security Controls and Failure Handling

### 6.1 Control Register

| Control | Required outcome |
|---|---|
| **SC-AUTH-001 — PIN protection and verification** | Store a salted, memory-hard verifier; compare securely; prevent plaintext persistence, clipboard use, autofill retention, predictive retention, logging, and diagnostic disclosure |
| **SC-AUTH-002 — Credential administration** | Require the current PIN before PIN change or biometric-setting changes that reduce protection; never reveal the PIN |
| **SC-AUTH-003 — Retry delay and lockout** | Enforce one fixed global policy whose security state survives navigation, restart, process termination, and reboot |
| **SC-AUTH-004 — Eligible biometric with PIN fallback** | Use only an active successful Android biometric result; cancellation, failure, or unavailability leaves the request unauthorized and returns to PIN |
| **SC-SESS-001 — Explicit package-scoped session creation** | Create a session for only the active protected target after successful authentication; never infer it from UI or stored state |
| **SC-SESS-002 — Global relock enforcement** | End authorization according to the selected immediate, screen-off, or timeout policy without per-application exceptions |
| **SC-SESS-003 — Volatile session boundary** | Keep authorization out of persistent storage and clear it on process termination, reboot, PIN change, and reset |
| **SC-ENF-001 — Usage Access baseline** | Use Android Usage Access as the sole foreground-detection source and require its availability for a Protected status |
| **SC-ENF-002 — Per-transition evaluation** | Re-evaluate protected status and the applicable package-scoped session on every relevant foreground transition, including repeated targets and rapid relaunch |
| **SC-ENF-003 — Secure lock presentation** | Present the lock interface through the required Android-supported capability; cancellation and presentation failure do not authorize access |
| **SC-HEALTH-001 — Truthful protection state** | Derive Protected, Degraded, Protection interrupted, Action required, or Unknown or not verified from actual required capabilities and recent checks |
| **SC-HEALTH-002 — Essential user notice** | Provide privacy-masked action guidance when protection is degraded, interrupted, or cannot be verified; do not equate notice with prevention |
| **SC-STATE-001 — Authenticated protection changes** | Require App Lock authentication before changing the protected selection, relock policy, PIN, biometric use, or another protection-reducing setting |
| **SC-DATA-001 — Encrypted private database** | Keep credential and configuration data in application-private encrypted storage and prevent plaintext side copies |
| **SC-DATA-002 — Android Keystore key protection** | Protect a random database secret with Android Keystore and keep it independent of the PIN and verifier |
| **SC-DATA-003 — Configuration validation and integrity** | Reject incomplete, invalid, corrupt, or unauthenticated security state and prevent a permissive empty configuration from being accepted silently |
| **SC-DATA-004 — Atomic in-place migration** | Preserve the prior valid data or complete the new schema; do not accept partial migration or leave plaintext remnants |
| **SC-DATA-005 — Key separation and failure handling** | Keep each secret limited to one purpose and enter a safe action-required state if protected key material becomes unavailable |
| **SC-RESET-001 — Complete destructive reset** | Explain the loss, require deliberate confirmation, create no session, remove all local App Lock state, and return to setup |
| **SC-UI-001 — Sensitive-screen privacy** | Protect PIN and security-sensitive App Lock screens from ordinary screenshots and recent-app disclosure and avoid unnecessary target detail |
| **SC-UI-002 — Obscured and injected input defense** | Reject or safely handle obscured input and unauthorized accessibility actions on authentication and protection-reducing controls |
| **SC-UI-003 — Safe navigation and interruption** | Ensure Back, Home, Recents, rotation, multi-window, picture-in-picture, timeout, backgrounding, and cancellation do not create authorization |
| **SC-COMP-001 — Minimal Android component exposure** | Keep entry points private unless Android operation requires otherwise; validate all external input and permit no unauthenticated sensitive action |
| **SC-PRIV-001 — Masked App Lock notifications** | Limit notifications to App Lock protection status and action guidance without unnecessary application identity or authentication detail |
| **SC-PRIV-002 — Bounded local diagnostics** | Record only minimum non-secret context, retain it for a bounded period, keep it private, and provide no export or remote transmission |
| **SC-LIFE-001 — Process and background-state recovery** | Clear sessions after process loss; re-evaluate required capabilities and state when execution resumes; disclose unpreventable force-stop and OEM limits |
| **SC-LIFE-002 — Boot and startup recovery** | Clear authorization at reboot and re-establish available protection work without treating startup events as authentication |
| **SC-BUILD-001 — Production-package hardening** | Distribute a non-debuggable signed package with required privacy/security flags and no embedded credential or database secret |
| **SC-BUILD-002 — Bounded dependency and release validation** | Validate the dependencies and production package that directly affect authentication, detection, presentation, database, migration, and key protection |
| **SC-BOUND-001 — Platform-boundary disclosure** | Make no guarantee against root/system compromise, Keystore failure, force-stop, or unsupported manufacturer behavior; represent these accurately to the user |

### 6.2 Authentication Control Requirements

The PIN entry surface shall accept only the defined numeric input, shall not expose entered digits to clipboard, autofill, predictive retention, notifications, or diagnostics, and shall clear temporary entry state after completion or cancellation. Errors shall be generic enough not to reveal verifier or storage detail.

Retry delay and lockout shall be based on persisted security state that cannot be reset by reopening the interface. The policy shall not be user-configurable in Version 1.0.0. No post-threshold camera, location, messaging, or intruder-recording action shall occur.

The biometric prompt shall be a convenience path only. The PIN shall remain usable when the phone lacks eligible biometric hardware, has no eligible enrollment, reports an error, or locks biometric use. Biometric success shall not alter the PIN or create a different session policy.

### 6.3 Authorization and Relock Control Requirements

Only one global relock policy shall exist. A user may select a supported relock choice, but shall not configure different choices by application, time, profile, location, network, or other condition.

Immediate relock shall end the applicable package session after leaving its authorized context. Screen-off relock shall end all package sessions when the screen turns off. Timeout relock shall use one global duration and a monotonic elapsed-time basis so wall-clock changes do not improperly extend authorization.

An authentication interface that is dismissed, obscured, timed out, backgrounded, destroyed, or interrupted shall not signal success. The protected target shall remain unauthorized unless its own package-scoped session was already valid independently of the interrupted request.

### 6.4 Detection, Presentation, and Health Control Requirements

The application shall obtain foreground information only through the Version 1.0.0 Usage Access baseline. It shall not ask for Accessibility permission for its own operation.

The protection decision shall evaluate:

- whether protection setup is complete;
- whether the foreground application is in the current protected selection;
- whether a valid session exists for the foreground protected application;
- whether required detection and presentation capabilities are available;
- whether a known interrupted or unverified condition prevents a healthy claim.

No schedule, profile, per-application timeout, location, Wi-Fi, Bluetooth, or other rule shall participate.

If Android prevents lock presentation after a protected target is detected, the application shall create no session, record only the bounded failure context, and move to Protection interrupted, Action required, or Unknown or not verified as appropriate. Essential notification wording shall state what the user must do without claiming the target remained inaccessible when that cannot be established.

### 6.5 Storage, Migration, and Reset Control Requirements

Protected configuration shall be encrypted before persistence. Encryption keys shall be unavailable through ordinary preferences, shared storage, resource files, logs, diagnostic records, screenshots, and notifications. The PIN shall authenticate the user but shall not serve as the database encryption key.

Migration shall validate required fields, values, protected selections, global relock settings, credential state, retry state, and database integrity before the new schema is accepted. A failed migration shall not delete the last valid store before failure is known. Temporary data shall remain private and shall be removed after success or controlled failure.

Corruption or Keystore invalidation shall not cause the application to display a healthy empty selection. If secure recovery is impossible, the application shall explain that local App Lock data cannot be used and shall offer full destructive reset only. Version 1.0.0 shall not attempt backup recovery, key reconstruction from the PIN, or preservation of selected settings.

### 6.6 Notification, Diagnostic, and Component Control Requirements

The ongoing protection notification shall identify App Lock operation without naming a protected application unless the name is essential to a user-directed recovery action. Lock-screen notification text shall be masked. An action-required notification may open only an App Lock explanation or the specific Android settings handoff; it shall not grant a session or change a security setting.

Diagnostic records shall use bounded retention and shall contain category, time, non-secret outcome, and only the minimum context needed to explain the current issue. They shall exclude PIN digits, verifier material, database secrets, biometric data, full protected-application lists, content from protected applications, and third-party notification content.

Externally reachable Android entry points shall be limited to those required for launch or safe platform events. External input shall be validated and shall never directly assert authentication success, session validity, protected selection, PIN change, or reset completion.

### 6.7 Failure-Handling Matrix

| Condition | Required user-visible and security behavior | Prohibited outcome |
|---|---|---|
| Incorrect PIN | Reject; update retry state; show bounded feedback; apply delay or lockout when required | Session creation, counter reset, or disclosure of correct-PIN characteristics |
| PIN entry cancelled or timed out | Close or restore the authentication surface safely; leave target unauthorized | Treating cancellation as success |
| Biometric cancelled, rejected, unavailable, or locked | Leave unauthorized and provide PIN fallback | Automatic access or persistent biometric success state |
| Usage Access denied or revoked | Show Action required during incomplete setup or Protection interrupted after active protection is lost; provide the exact Android settings handoff | Displaying Protected or silently continuing as though detection works |
| Lock-presentation capability denied or revoked | Show Action required during incomplete setup or Protection interrupted after active protection is lost; create no session | Claiming protected applications remain inaccessible when presentation cannot be established |
| Health cannot be established | Show Unknown or not verified and provide appropriate retry or guidance | Inferring Protected from stale status |
| Reduced Android responsiveness | Show Degraded when the baseline operates but a known condition affects responsiveness | Describing degraded protection as identical to normal operation |
| Process termination | Lose all sessions; re-evaluate protection when the app runs again | Restoring authorization from storage or screen state |
| Device reboot | Lose all sessions; attempt safe protection startup when Android permits | Restoring the previous session |
| Force-stop | When next opened, explain that protection could not operate while force-stopped and require normal setup/health validation | Claiming uninterrupted protection during force-stop |
| Rapid switch or relaunch | Re-evaluate the foreground target and its applicable package-scoped session every time | Suppressing the lock because the package was recently observed |
| Database migration failure | Preserve the last valid store when possible; enter Action required during incomplete setup or Protection interrupted after active protection is lost; provide safe retry or reset | Accepting partial data or permissive defaults |
| Database corruption | Stop normal use of unreadable security state; disclose the condition; offer complete reset if unrecoverable | Treating corruption as no protected applications while showing Protected |
| Keystore invalidation | Deny use of unreadable protected data; show Action required; offer complete reset if unrecoverable | Generating a replacement key and silently accepting lost configuration as valid |
| Forgotten PIN | Explain that the PIN cannot be retrieved; offer complete destructive reset with deliberate confirmation | Data-preserving recovery, session creation, selective preservation, or PIN disclosure |
| Destructive reset cancelled | Return without changing local data or authorization | Partial deletion or access grant |
| Destructive reset completed | Remove all local App Lock security state and return to initial setup | Retaining a session, credential, protected selection, or inconsistent residue |
| Application uninstalled or data cleared through Android | No continuing protection guarantee; setup is required after reinstall | Claiming persistence that Android does not provide |
| Notification permission unavailable where required by Android | Explain the effect on ongoing protection and guide the user | Hiding an execution limitation or claiming normal protection |
| Diagnostic write failure | Continue core protection if possible; omit the record and avoid security-state change | Treating logging failure as authorization failure or success |

---
