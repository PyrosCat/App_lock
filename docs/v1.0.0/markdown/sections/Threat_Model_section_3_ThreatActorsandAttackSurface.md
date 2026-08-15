# Threat Model

## Version 1.0.0

## 3. Threat Actors and Attack Surface

### 3.1 Threat Actors

| Actor | Capabilities | Boundary |
|---|---|---|
| Unauthorized person with physical access | Uses an already-unlocked phone; launches protected applications; switches rapidly; uses Home, Back, Recents, multi-window, and picture-in-picture; changes Android settings available to the device user; attempts PIN guessing; may uninstall, clear data, or force-stop App Lock | Primary in-scope attacker, subject to the platform limitations stated in this document |
| Malicious ordinary Android application | Sends permitted intents or broadcasts; opens overlays if the user granted that capability; observes generally available package and lifecycle behavior; attempts to influence App Lock through exposed Android surfaces | In scope within ordinary sandbox and permission limits |
| Hostile third-party Accessibility service or overlay | Observes interface content permitted by Android; attempts injected actions, obscured touches, or UI deception | In scope for best-effort defense; complete prevention is not guaranteed after the user grants another service elevated UI access |
| Non-root developer-tools attacker | Uses developer or USB capabilities available on a non-rooted phone; attempts application inspection, process interference, or private-data extraction where Android permits | In scope only within the normal non-root Android boundary |
| Android or manufacturer behavior | Delays foreground information; restricts background work; stops a process; changes permission or window behavior; withholds a startup opportunity | In scope as a security-availability and truthful-status condition |
| Rooted-device or system-level attacker | Reads or changes application memory and files; alters platform results; defeats the sandbox; controls UI and process execution | Outside the guaranteed security boundary; assessed as residual critical risk |
| Compromised dependency or weakened production package | Alters security decisions, enables debugging, discloses secrets, or changes release behavior | In scope for bounded dependency and production-package controls |

### 3.2 Attacker Goals

Relevant attacker goals are to:

- access a protected application without the required authentication;
- keep authorization active longer than the selected relock policy permits;
- change or clear the PIN or protection settings without authority;
- remove an application from the protected selection;
- suppress, delay, or mislead foreground detection and lock presentation;
- cause App Lock to report protected status while protection is interrupted or unverified;
- extract the PIN verifier, database secret, protected-application selection, or diagnostic information;
- bypass retry delay or lockout;
- exploit migration, corruption, restart, reboot, or Keystore failure to obtain permissive state;
- use an exposed Android component, overlay, or injected action to reach an authenticated result;
- weaken the production package sufficiently to observe or alter security decisions.

### 3.3 Attack Surface Inventory

| Surface | Information or control crossing the boundary | Principal threats |
|---|---|---|
| Initial setup | PIN creation, capability guidance, first protected-application selection, protection verification | Weak or incomplete setup accepted as protected; PIN disclosure; premature completion |
| Main App Lock entry | Authentication state, settings access, protected-application management | Self-gate bypass; unauthorized configuration change; recent-screen disclosure |
| PIN interface | PIN digits, retry state, timeout, cancellation, input events | Observation, injection, brute force, stale success, navigation bypass |
| Android biometric prompt | Active request, result, error, cancellation, enrollment state | Stale result, false success, fallback bypass, lifecycle confusion |
| Protected-application selection | Installed-application identity and protection state | Unauthorized removal, stale list, removed-application residue, selection disclosure |
| Android Usage Access | Foreground usage information and grant state | Detection failure, revocation, latency, misleading health |
| Lock presentation | Target identity, session state, Android window/activity permission, lifecycle | Race, presentation denial, Back/Home/Recents bypass, obscured interface |
| Package-scoped sessions and global relock | Authentication result, session start, target identity, expiry, screen state | Unauthorized creation, overlong session, cross-application scope confusion, persistence after restart |
| Protection health | Permission state, service health, recent detection/presentation checks | False healthy status, stale status, tampering, unbounded diagnostic detail |
| Essential notifications | Ongoing protection and action-required state | Protected-application disclosure, misleading wording, lock-screen privacy exposure |
| Boot and process lifecycle | Startup event, process recreation, volatile state | Startup gap, stale session restoration, failure to re-establish protection |
| Android power management | Background restrictions and execution opportunities | Delayed or interrupted protection, unreported degradation |
| Encrypted local database | Credential verifier, protected selection, settings, retry state, migration metadata | Plaintext exposure, key extraction, corruption, partial migration, permissive default |
| Android Keystore | Database-key protection and invalidation state | Key loss, key substitution, platform compromise, improper reset |
| Local diagnostics | Bounded health and failure information | Sensitive disclosure, falsification, excessive retention, use as authorization input |
| Android component entry points | Launcher entry, system-delivered startup events, internal screens and background work | Unauthorized invocation, crafted input, state-changing external action |
| Screen, Recents, and overlays | Visual content and user input | Screenshot exposure, spoofing, tapjacking, injected action, protected-content glimpse |
| Installation and update | Signed application package, production flags, dependency set, schema change | Debuggable release, altered package, vulnerable dependency, migration regression |

### 3.4 Excluded Attack Surfaces

There is no Version 1.0.0 attack surface for Vault storage, camera capture, intruder media, backup archives, restore/import processing, account recovery, cloud services, network synchronization, schedule evaluation, location or connection triggers, notification access to other applications, device-administrator controls, or an App Lock Accessibility service.

---
