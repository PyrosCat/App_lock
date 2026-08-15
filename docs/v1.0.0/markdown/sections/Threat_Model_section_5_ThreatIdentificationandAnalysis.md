# Threat Model

## Version 1.0.0

## 5. Threat Identification and Analysis

### 5.1 Assessment Method

Each retained threat is assessed against the Version 1.0.0 assets, attack surfaces, security invariants, and Android boundaries defined above. Risk is classified as Critical, High, Medium, or Low according to the likelihood of the attack path and the consequence to authentication, authorization, confidentiality, integrity, availability, privacy, or truthful protection status.

The threat identifiers are retained from the established threat catalogue. Gaps are intentional where a threat belongs only to an excluded capability.

### 5.2 Risk Scale

| Rating | Meaning |
|---|---|
| Critical | A plausible path can grant unauthorized protected-application access, expose primary credential or key material, or broadly defeat protection with no practical user containment |
| High | A material bypass, prolonged protection interruption, unauthorized security change, or false healthy state can occur under credible conditions |
| Medium | The threat requires narrower conditions, produces a limited exposure window, or is materially constrained by Android or another retained control |
| Low | The effect is limited, difficult to exploit, or primarily informational without defeating the core authorization promise |

### 5.3 Credential Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-CRED-001 — PIN Confidentiality Compromise** | An attacker observes PIN entry or obtains PIN-related data from storage, logs, screenshots, clipboard, autofill, diagnostics, or a hostile UI service | The attacker may authenticate as the user and alter protection | SC-AUTH-001, SC-UI-001, SC-PRIV-002, SC-DATA-001 | Physical observation and hostile privileged UI services cannot be completely prevented; **High** |
| **THR-CRED-002 — Unauthorized PIN Reset or Change** | An unauthenticated route reaches credential change or a reset is misrepresented as credential recovery | The attacker replaces the user credential or disables existing protection | SC-AUTH-002, SC-RESET-001, SC-STATE-001 | Android data clear and uninstall remain outside continuing protection; **Medium** |
| **THR-CRED-003 — Credential Reset Through Application Data Manipulation** | Stored verifier, salt, retry state, or credential-existence state is modified, deleted, partially migrated, or replaced | The application may accept a new credential or enter an unsafe first-run state | SC-DATA-001, SC-DATA-003, SC-DATA-004, SC-RESET-001 | Root/system attackers can alter local state; **Medium** within the supported boundary, **Critical** outside it |
| **THR-CRED-004 — Credential Exposure Through Runtime Handling** | PIN digits or verifier inputs remain in memory longer than needed, appear in exceptions, or are passed to an exposed surface | PIN confidentiality is weakened without accessing encrypted storage | SC-AUTH-001, SC-PRIV-002, SC-COMP-001 | A rooted or instrumented process can inspect memory; **Medium** within the supported boundary |

### 5.4 Authentication Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-AUTH-001 — PIN Authentication Bypass** | Error handling, malformed input, stale success state, timeout, cancellation, or screen recreation is interpreted as a correct PIN | Unauthorized session creation | SC-AUTH-001, SC-SESS-001, SC-UI-003 | Residual risk is **Low** after negative-path verification |
| **THR-AUTH-002 — Biometric Authentication Result Abuse** | A stale, cancelled, unrelated, or ineligible biometric result is accepted; lifecycle recreation detaches the result from its request | Unauthorized session creation | SC-AUTH-004, SC-SESS-001, SC-UI-003 | Android biometric integrity remains a platform assumption; **Medium** |
| **THR-AUTH-003 — Authentication State Confusion** | PIN and biometric states overlap, an old result survives navigation, or the lock interface mistakes visible state for authorization | Access is granted without one completed active request | SC-AUTH-004, SC-SESS-001, SC-UI-003 | Residual risk is **Low** after interruption and lifecycle testing |
| **THR-AUTH-004 — Brute-Force Lockout Bypass** | Restart, process termination, reboot, navigation, or time manipulation clears failed-attempt or lockout state | An attacker obtains materially more guesses than policy permits | SC-AUTH-003, SC-DATA-003, SC-LIFE-001 | A weak user-selected PIN remains susceptible within the bounded attempt policy; **Medium** |

### 5.5 Protected-Application Enforcement Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-ENF-001 — Foreground Detection Failure** | Android Usage Access is delayed, incomplete, denied, or interpreted incorrectly when a protected application becomes foreground | Protected content may appear before authentication | SC-ENF-001, SC-ENF-002, SC-HEALTH-001 | Android reporting latency creates a limited exposure window; **High** |
| **THR-ENF-002 — Deliberate Detection Disruption** | A user or attacker revokes Usage Access, disables the presentation capability, force-stops App Lock, or triggers an Android restriction | The core protection path cannot operate | SC-ENF-001, SC-ENF-003, SC-HEALTH-001, SC-LIFE-001 | Force-stop and user-controlled revocation cannot be prevented; **High** |
| **THR-ENF-003 — Silent Detection Failure** | Required grants appear present while foreground information, processing, or lock presentation no longer functions | The application may claim protection while protected applications remain usable | SC-HEALTH-001, SC-HEALTH-002, SC-PRIV-001 | Some failures may be detectable only when the application next runs or tests the path; **High** |
| **THR-ENF-004 — Enforcement Race During Application Switching** | Rapid switching or relaunch occurs between foreground observation, session validation, and lock presentation | Stale target or authorization state permits a bypass | SC-ENF-002, SC-SESS-002, SC-UI-003 | Platform presentation latency remains; logic-based stale-state bypass shall be eliminated; **Medium** |
| **THR-ENF-005 — Enforcement Bypass Through Detection-Service Restart State** | Process recreation or protection-path restart retains a stale target, session, or healthy status | Unauthorized access or false protection state follows restart | SC-SESS-003, SC-LIFE-001, SC-HEALTH-001 | Android may delay restart; **Medium** |
| **THR-ENF-006 — Enforcement Availability Failure** | Usage Access, lock presentation, foreground execution, or another required common capability is unavailable | Protected applications cannot be reliably intercepted | SC-ENF-001, SC-ENF-003, SC-HEALTH-001, SC-LIFE-002 | Ordinary applications cannot guarantee continuous execution; **High** |

### 5.6 Session Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-SES-001 — Unauthorized Session Creation** | A visible screen, diagnostic state, pending biometric request, error, or crafted component invocation is treated as an authenticated session | A protected application may become accessible without valid authorization | SC-SESS-001, SC-COMP-001, SC-UI-003 | Residual risk is **Low** after negative-path verification |
| **THR-SES-002 — Session Extension Beyond Policy** | Time handling, screen transitions, repeated foreground events, or background/foreground cycling refreshes the session incorrectly | Access remains authorized longer than the user selected | SC-SESS-002, SC-LIFE-001 | System-clock anomalies require monotonic elapsed-time treatment; **Low** after verification |
| **THR-SES-003 — Cross-Application Session Confusion** | A package-scoped session is applied when expired, applied before authentication, or applied to a different protected application | One application receives access outside the defined global policy | SC-SESS-001, SC-SESS-002, SC-ENF-002 | Valid reuse for the same application is permitted only while its session remains valid; cross-application reuse shall be eliminated; **Low** |
| **THR-SES-004 — Session Persistence Across Reboot or Process Death** | Authorization is written to storage or reconstructed after restart | Protected applications open without fresh authentication | SC-SESS-003, SC-LIFE-001, SC-DATA-003 | Residual risk is **Low** within the supported boundary |

### 5.7 Cryptographic Storage Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-CRYPTO-001 — Master Key Compromise** | Keystore-protected key material is exposed, substituted, or used outside its intended purpose | Database-key protection and local confidentiality may fail | SC-DATA-002, SC-DATA-005 | Root/system or Keystore compromise remains outside the guarantee; **Critical** outside the supported boundary |
| **THR-CRYPTO-002 — Database Passphrase Compromise** | The database secret is stored beside the database, logged, embedded, exported, or retained in an unsafe form | Protected configuration becomes readable offline | SC-DATA-001, SC-DATA-002, SC-PRIV-002 | Runtime extraction by a rooted attacker remains; **Medium** within the supported boundary |
| **THR-CRYPTO-004 — Cryptographic Key Reuse or Derivation Error** | The PIN verifier, database secret, or Keystore key is reused for a different purpose or derived through an unsafe relationship | Compromise of one secret weakens another boundary | SC-DATA-002, SC-DATA-005 | Residual risk is **Low** after design and storage verification |
| **THR-CRYPTO-005 — Keystore Invalidation** | Device security changes, platform behavior, or key loss makes protected key material unusable | Configuration becomes unreadable; unsafe error handling may reset or bypass protection | SC-DATA-004, SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | Data may be irrecoverable because backup is absent; **High** availability risk, **Low** authorization-bypass risk after controls |

### 5.8 User-Interface and Component Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-UI-001 — Tapjacking or Obscured Authentication Input** | A hostile overlay changes what the user sees or receives touches intended for another control | The user may disclose a PIN or confirm a protection-reducing action | SC-UI-001, SC-UI-002 | Android-granted overlay privileges cannot be completely neutralized; **Medium** |
| **THR-UI-002 — UI Spoofing** | Another application imitates the App Lock screen or places misleading content over it | The user may enter the PIN into a hostile interface | SC-UI-001, SC-PRIV-001 | The application cannot prevent another app from drawing a look-alike screen; **Medium** |
| **THR-UI-003 — Touch or Event Injection** | A hostile Accessibility service, automation interface, or crafted input invokes authentication or destructive controls | Unauthorized action may occur without deliberate user input | SC-UI-002, SC-AUTH-001, SC-RESET-001 | A user-authorized hostile Accessibility service retains elevated platform capability; **High** residual outside best-effort defenses |
| **THR-UI-004 — Navigation Around Lock Screen** | Back, Home, Recents, rotation, multi-window, picture-in-picture, timeout, or interruption reveals protected content or creates authorization | Protected application becomes usable without completed authentication | SC-UI-003, SC-ENF-003, SC-SESS-001 | Android window timing may allow a brief visual exposure; **High** |
| **THR-UI-005 — Screen Capture or Recording** | Authentication, protected configuration, or recent-app preview is captured | PIN-entry state or protected-application information is disclosed | SC-UI-001, SC-PRIV-001 | External cameras and some privileged system capture are outside control; **Medium** |
| **THR-IPC-001 — Unauthorized Activity Launch** | An external application invokes an App Lock screen directly with crafted input | Sensitive screen access, false navigation context, or state change | SC-COMP-001, SC-STATE-001 | Required launcher entry remains externally reachable but shall not confer authorization; **Low** |
| **THR-IPC-002 — Unauthorized Service Invocation** | An external application attempts to start or bind to background protection work | Protection state is changed, stopped, or confused | SC-COMP-001, SC-HEALTH-001 | Android system actions remain trusted inputs only within platform rules; **Low** |
| **THR-IPC-003 — Unauthorized Boot-Event Invocation** | A crafted broadcast imitates a startup condition | Protection work is triggered in an unsafe state or stale authorization is restored | SC-COMP-001, SC-LIFE-002, SC-SESS-003 | Safe redundant startup may occur but shall not create authorization; **Low** |
| **THR-ACC-004 — Malicious Peer Accessibility Service** | The user has granted another service Accessibility privileges capable of observing or manipulating App Lock UI | PIN confidentiality, input integrity, and user intent may be compromised | SC-UI-001, SC-UI-002, SC-AUTH-001 | Complete defense is not guaranteed against platform-granted peer privilege; **High** |

### 5.9 Lifecycle, Health, Recovery, and Platform Threats

| Threat | Preconditions and attack path | Consequence | Required controls | Residual risk |
|---|---|---|---|---|
| **THR-LIFE-001 — Force-Stop Enforcement Loss** | The device user force-stops App Lock | Android prevents execution and the protection path stops | SC-LIFE-001, SC-HEALTH-002 | Continuous protection while force-stopped cannot be guaranteed; **Critical** accepted platform limitation |
| **THR-LIFE-002 — Process Death With Authorization Confusion** | Android terminates the process while a session or authentication request exists | Stale authorization or inconsistent lock state appears after recreation | SC-SESS-003, SC-LIFE-001, SC-UI-003 | Restart latency remains; **Low** authorization risk after controls |
| **THR-LIFE-003 — OEM Background Restriction** | Manufacturer power management delays or prevents required background work | Detection or presentation becomes delayed or interrupted | SC-LIFE-001, SC-HEALTH-001, SC-HEALTH-002 | Manufacturer behavior varies beyond available evidence; **High** |
| **THR-LIFE-004 — Boot Re-Arm Failure** | Required protection work does not resume after reboot | Protected applications may be used before protection is restored | SC-LIFE-002, SC-HEALTH-001, SC-SESS-003 | Android may delay delivery or execution; **High** |
| **THR-LIFE-005 — Startup Security Race** | A protected application becomes usable before App Lock has restored detection and presentation | Temporary unauthorized access | SC-LIFE-002, SC-ENF-002, SC-HEALTH-001 | Startup ordering remains platform-dependent; **High** |
| **THR-AUD-001 — Security Diagnostic Modification** | Bounded local records are altered or deleted | Troubleshooting or displayed health may become misleading | SC-PRIV-002, SC-DATA-003, SC-HEALTH-001 | Diagnostics are not authorization inputs, limiting consequence; **Low** |
| **THR-AUD-003 — Security-State Manipulation** | Stored, cached, or visible health state is changed independently of actual permission and protection checks | Interrupted protection is displayed as healthy | SC-HEALTH-001, SC-DATA-003 | Health may be unverified while the app cannot run; **High** |
| **THR-REC-001 — Database Corruption Causes Security Degradation** | Database damage prevents reliable reading of protected selections or settings | The application may behave as though no applications are protected | SC-DATA-003, SC-DATA-004, SC-RESET-001 | Local configuration may be irrecoverable; **High** availability risk |
| **THR-REC-002 — Database Migration Exposure** | In-place upgrade copies plaintext, loses fields, partially commits, or weakens settings | Confidentiality or protection integrity is lost | SC-DATA-004, SC-DATA-005 | Unsupported downgrade is not guaranteed; **Medium** |
| **THR-REC-003 — Keystore Loss Causes Irrecoverable Data Loss** | Protected key material becomes unavailable | Encrypted configuration cannot be read | SC-DATA-005, SC-RESET-001, SC-HEALTH-001 | No backup exists; destructive reset may be the only recovery; **High** availability risk |
| **THR-REC-005 — Forgotten-PIN Recovery Becomes a Bypass** | The forgotten-PIN path grants access, reveals configuration, or preserves selective security state without the PIN | An attacker converts reset into authentication or disables selected protection covertly | SC-RESET-001, SC-SESS-001, SC-STATE-001 | Full destructive reset still ends local protection and is equivalent in consequence to Android data clear; **High** accepted boundary |
| **THR-INT-001 — Debuggable Production Build** | A distributed package permits debugging or ordinary runtime inspection | An attacker gains easier access to memory and security decisions | SC-BUILD-001 | Root/system instrumentation remains outside the guarantee; **Medium** |
| **THR-INT-004 — Release Configuration Weakening** | Production packaging omits security flags, includes secrets, or changes critical protection behavior | Multiple security boundaries become weaker than specified | SC-BUILD-001, SC-BUILD-002 | Distribution-platform and signing-account compromise remain external; **Medium** |
| **THR-PLAT-001 — Root or System Compromise** | The attacker controls privileged operating-system behavior | Sandbox, memory, UI, files, and sessions can be read or changed | SC-BOUND-001 | No complete application-level mitigation; **Critical** accepted boundary |
| **THR-PLAT-002 — Android Keystore Trust Failure** | Android or hardware fails to preserve the Keystore security properties | Keys or protected local data may be exposed | SC-DATA-002, SC-BOUND-001 | No complete application-level mitigation; **Critical** accepted boundary |
| **THR-PLAT-003 — Android Framework Security Behavior Changes** | A supported Android update changes permission, biometric, lifecycle, notification, or window behavior | Protection may fail or become misleading | SC-BOUND-001, SC-HEALTH-001, SC-BUILD-002 | Unknown platform changes require compatibility verification; **High** |
| **THR-PLAT-004 — OEM Security or Power-Management Interference** | Manufacturer changes suppress execution, delay events, or alter settings behavior | Detection and presentation may become unreliable | SC-LIFE-001, SC-HEALTH-001, SC-BOUND-001 | Universal manufacturer behavior is not guaranteed; **High** |
| **THR-SUP-001 — Malicious Dependency** | A packaged dependency alters security decisions, records secrets, or exposes a component | Authentication, storage, or protection may be compromised | SC-BUILD-002 | Review cannot prove absence of all malicious behavior; **Medium** |
| **THR-SUP-002 — Dependency Update Introduces Security Regression** | An updated library changes cryptography, database, biometric, lifecycle, or UI behavior | A previously controlled threat reappears | SC-BUILD-002, Section 8 acceptance scenarios | Residual risk is **Low** after retained-surface regression verification |
| **THR-SUP-003 — Build or Release Integrity Failure** | An incorrect, altered, unsigned, or inconsistently configured package is distributed | Users receive behavior that does not satisfy this Threat Model | SC-BUILD-001, SC-BUILD-002 | Signing-account or distribution-platform compromise remains external; **Medium** |

### 5.10 Removed Threat Domains

The following established threat domains do not apply to Version 1.0.0 and are intentionally absent from the retained register:

- all `THR-VAULT-*` threats;
- `THR-CRYPTO-003`, which is specific to Vault-file key material;
- all App Lock Accessibility-service availability threats, including `THR-ACC-001`, `THR-ACC-002`, `THR-ACC-003`, and `THR-ACC-005`;
- all `THR-DA-*` device-administrator threats;
- `THR-AUD-002`, which concerns intruder-event disclosure;
- `THR-REC-004`, which concerns backup and restore;
- network-service threats associated with accounts, cloud storage, synchronization, telemetry transmission, or remote administration.

The hostile peer Accessibility threat remains as `THR-ACC-004` because another user-authorized service can attack the App Lock authentication interface even though App Lock itself provides no Accessibility service.

---
