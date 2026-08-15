# Software Design Specification

## Version 1.0.0

## 13. Security Services Design

### 13.1 Security boundary

Security support is limited to the controls required for PIN authentication, biometric mediation, retry resistance, encrypted local configuration, private presentation, and safe failure. It does not form a general policy platform.

The trusted local boundary contains:

- Android application sandboxing;
- Android Keystore protection for locally stored encryption material;
- protected preferences for credential and lockout information;
- an encrypted relational database for protected package identifiers;
- platform biometric authentication;
- the application-owned authentication and protection decisions; and
- private application screens.

Usage Access and package information are Android-provided inputs. They are validated and minimized but are not trusted to make an App Lock decision.

### 13.2 Credential protection

The raw PIN is never persisted, logged, included in diagnostics, placed in an application backup, or sent outside the phone. PIN verification uses a unique random salt and an approved memory-hard derivation. The stored verifier contains only the information required to verify later attempts and interpret its format.

Verifier creation and evaluation occur away from the main thread. Temporary character and byte representations are kept for the shortest practical lifetime and are cleared after use where the runtime allows.

An older supported verifier format may be upgraded after successful PIN authentication. Upgrade is atomic: a failed replacement leaves the verified prior record usable rather than leaving an unreadable partial credential.

### 13.3 Key and database protection

The encrypted relational database is opened using random material protected by Android Keystore-backed storage. Key material is never stored in the relational database, displayed, exported, or written to logs.

Loss or invalidation of the Keystore protection means the encrypted database cannot be treated as readable. Version 1.0.0 has no recovery key or backup. The user is told that the local configuration must be cleared and created again.

### 13.4 Biometric security

The application delegates biometric collection and matching to the Android platform prompt. It receives only the completion category required to continue or return to PIN.

Biometric success is accepted only for a current authentication request. A result received after the lock request changed, the screen was destroyed, or the target package changed is ignored. PIN fallback remains present even if the user previously enabled biometrics.

### 13.5 Authorization boundaries

Authentication and authorization are distinct. Successful PIN or biometric verification creates only the session appropriate to the current request:

- protected-app authentication creates a package-scoped session;
- PIN or eligible biometric settings authentication permits the current protected settings flow other than PIN replacement; and
- neither grants access to another package or persists across process death.

Protection-reducing operations verify the current authenticated settings state before changing storage. PIN replacement separately requires current-PIN entry.

### 13.6 Sensitive data handling

The following information is treated as confidential:

- the protected package set;
- PIN verifier and verification parameters;
- failure count and lockout deadline;
- biometric preference;
- database-opening material;
- current foreground package while being evaluated;
- active lock target; and
- detailed protection-health causes.

Only the component that requires a value receives it. User-visible notifications and ordinary logs use generalized wording. No protected package name is placed in a diagnostic record.

### 13.7 Input and interface protection

PIN input accepts only the permitted numeric format and length. Package identifiers originate from Android package information and are checked for valid form before persistence. Settings values are selected from defined options rather than accepted as unrestricted text.

The lock surface is not exported to other applications. External intents cannot provide a success result or construct an authenticated session. Navigation parameters are treated as untrusted until checked against the current lock request.

### 13.8 Security failure behavior

Cryptographic, Keystore, protected-preference, database-opening, and verifier-format failures do not degrade into plaintext storage or a bypass. Authentication remains unavailable and protected settings remain closed.

An unavailable optional biometric capability falls back to PIN without weakening protection. An unavailable required foreground detector or lock presenter changes protection health to Protection interrupted.

### 13.9 Security exclusions

Version 1.0.0 does not require root detection, remote attestation, enterprise device posture, independent security-event storage, audit-history export, key synchronization, remote revocation, or a second authentication provider. The application does not request an Accessibility service as a security control.
