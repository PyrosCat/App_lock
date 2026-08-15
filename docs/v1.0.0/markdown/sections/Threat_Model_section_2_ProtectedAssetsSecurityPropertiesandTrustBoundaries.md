# Threat Model

## Version 1.0.0

## 2. Protected Assets, Security Properties, and Trust Boundaries

### 2.1 Protected Assets

| Asset | Sensitivity | Required security properties | Security consequence of compromise |
|---|---|---|---|
| PIN verifier, salt, retry state, and lockout state | Critical | Confidentiality, integrity, reliable persistence | PIN guessing may be accelerated, attempts may become unlimited, or an incorrect PIN may be accepted |
| Active biometric request and result | Critical while active | Authenticity, freshness, correct request binding | A stale, cancelled, or unrelated result may be treated as authorization |
| Protected-application selection | Critical | Integrity, availability, confidentiality where practical | An attacker may remove an application from protection or learn which applications the user protects |
| Global relock policy and protection settings | Critical | Integrity, authenticated modification | Authorization may last longer than intended or protection may be reduced without consent |
| Authorization-session state | Critical | Integrity, freshness, bounded lifetime | Protected applications may remain accessible without fresh authentication |
| Foreground detection, presentation, and health state | Critical | Integrity, availability, truthful reporting | A protected application may be usable while protection appears normal |
| Android permission state used by protection | Critical | Freshness, integrity of interpretation | A revoked or unavailable capability may go unnoticed |
| Cryptographic keys and database secret | Critical | Confidentiality, integrity, separation of purpose | Protected local information may become readable or alterable |
| Encrypted configuration database and migration state | Critical | Confidentiality, integrity, consistency, availability | Protection settings may be exposed, lost, or replaced with permissive state |
| Essential notification content | High | Privacy, accuracy, minimal disclosure | Another person may learn protected-application or security-state details |
| Bounded local diagnostic records | High | Privacy, integrity, limited retention | Credentials, protected selections, or misleading health information may be exposed |
| Production package and release configuration | High | Integrity, absence of debugging weakness | Runtime inspection or modification may become easier than the stated boundary allows |

Vault files, intruder media, backup archives, recovery secrets, profiles, schedules, automation rules, remote account data, and third-party notification content are not Version 1.0.0 assets because the corresponding capabilities are absent.

### 2.2 Security Properties

#### 2.2.1 Authentication

Authentication establishes that the person interacting with App Lock supplied the correct PIN or completed an eligible Android biometric prompt for the active request. Device unlock, application launch, possession of the phone, navigation to an App Lock screen, or knowledge of a protected application shall not constitute App Lock authentication.

#### 2.2.2 Authorization

Authorization permits access to a protected application only while that application's session remains valid under the selected global relock policy. A session shall not exist before authentication, shall not be reconstructed from a user-interface state, shall not authorize a different protected application, and shall not survive a security boundary that requires reauthentication.

#### 2.2.3 Confidentiality

Credentials, database keys, protected-application selections, and security-sensitive settings shall receive protection appropriate to their sensitivity. App Lock shall minimize visible information in notifications, recent-app previews, errors, and diagnostics.

#### 2.2.4 Integrity

An unauthenticated path shall not change the PIN, protected-application selection, relock policy, biometric setting, required-capability interpretation, or authorization state. Invalid stored values shall be rejected or replaced only through a controlled safe path.

#### 2.2.5 Availability

The foreground detection, lock presentation, session evaluation, and health-reporting paths shall remain available when Android permits them to operate. Where Android prevents continued operation, the application shall not claim availability it cannot establish.

#### 2.2.6 Truthful Status

Protection status is a security property. A missing permission, known presentation failure, failed integrity check, or unverified health condition shall not be displayed as normal protected operation.

#### 2.2.7 Privacy

The application shall collect and retain only the local information needed for the included functions. It shall not create a broader history, media collection, network record, or exportable diagnostic package.

### 2.3 Trust Boundaries

| Boundary | Reliance | Required treatment |
|---|---|---|
| Android operating system | Process isolation, package identity, permission enforcement, lifecycle delivery, notification behavior, and window behavior | Treat platform results as authoritative only within the documented Android model; disclose platform-dependent limits |
| Android Keystore | Protection of non-exportable application key material | Store no substitute plaintext key; fail safely if protected material becomes unavailable |
| Application sandbox | Isolation of application-private files from ordinary applications | Keep credentials, database files, settings, and diagnostics in private storage |
| App Lock credential | Knowledge of the valid PIN represents owner authority | Keep the PIN separate from encryption keys and require it for credential and protection-reducing changes |
| Android biometric prompt | Eligibility, user interaction, and biometric result | Accept only an active successful result; use PIN for fallback and credential administration |
| Android Usage Access | Foreground-application information | Treat it as required but platform-dependent; detect denial or revocation and report the resulting protection state |
| Lock-presentation capability | Android-controlled ability to place the lock interface before usable protected content | Verify availability and do not describe protection as healthy when it is unavailable |
| Android lifecycle and power management | Process start, process death, boot delivery, and background execution | Clear authorization after lifecycle loss and identify conditions Android prevents the application from correcting |
| Storage and migration | Database reads, writes, integrity, schema change, and recovery | Reject incomplete or corrupt security state; do not silently create permissive defaults |
| Physical access | An attacker may possess an already-unlocked phone | Require App Lock authentication independently of device unlock |
| Hostile privileged UI service | A third-party overlay or user-authorized Accessibility service may observe or influence the screen | Apply best-effort input and display protection; retain the remaining risk explicitly |

### 2.4 Non-Goals

The application does not claim to:

- prevent an authorized phone user from uninstalling it, clearing its data, revoking capabilities, or force-stopping it;
- continue executing after Android has force-stopped it;
- defeat root access, operating-system compromise, malicious firmware, or a platform-controlled debugger;
- recover a forgotten PIN while preserving local App Lock configuration;
- protect content after App Lock has validly authorized access and the protected application displays that content;
- prevent observation by an external camera, coercion, or all forms of shoulder surfing;
- provide identical detection or presentation latency across all Android phone manufacturers;
- secure device classes, Android versions, user/profile arrangements, or duplicated-application environments outside Section 1.4.

---
