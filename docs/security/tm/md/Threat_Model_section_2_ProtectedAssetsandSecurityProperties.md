**2. Protected Assets and Security Properties**

**2.1 Purpose**

This section defines the assets that App Lock is required to protect and the security properties that apply to those assets.

The assets are ordered by security significance based on the consequences of unauthorized disclosure, modification, or loss of the asset.

The asset model reflects the current implemented storage and protection architecture. It must not assume that a security property exists merely because it is specified elsewhere in the project.

The principal security properties considered in this section are:

- Confidentiality

- Integrity

- Availability

- Authenticity

- Authorization-state integrity

- Security-state persistence

Not every property applies equally to every asset. Each asset is therefore evaluated according to the security consequence associated with its compromise.

**2.2 Asset Classification**

App Lock has six primary categories of security-sensitive assets:

1.  User credential.

2.  Cryptographic key material.

3.  Vault payloads.

4.  Intruder photographs.

5.  Encrypted application database and its metadata.

6.  Security enforcement state and mechanisms.

The sixth category is intentionally treated as a security asset even though it is not a conventional stored secret. Loss of the enforcement mechanism can directly result in unauthorized access to protected applications.

**2.3 Asset A-001 — User PIN Credential**

**Description**

The App Lock PIN is the primary application authentication credential.

The plaintext PIN is not intended to be persisted. The current implementation stores an Argon2id-derived password hash and its salt in the applock_credentials encrypted preference store.

The credential store is protected by the Android Keystore-backed application master key.

**Security Requirements**

The PIN must provide:

- **Confidentiality:** An attacker must not recover the plaintext PIN.

- **Integrity:** An attacker must not modify the credential in a manner that changes the accepted authentication credential without authorization.

- **Authorization integrity:** An attacker must not reset, replace, or change the PIN without demonstrating knowledge of the current credential.

- **Brute-force resistance:** An attacker must not bypass or reset the persistent authentication lockout mechanism to obtain unlimited attempts.

**Security Significance**

Compromise of the PIN can directly defeat the application's authentication boundary because knowledge of the current credential represents authorization as the legitimate App Lock owner.

The threat model therefore treats the following as credential compromise:

- Recovery of the plaintext PIN.

- Recovery of an equivalent usable credential.

- Unauthorized replacement of the PIN.

- Unauthorized reset of the PIN.

- Circumvention of the controls intended to limit credential guessing.

The legitimate owner is intentionally treated as authorized to modify App Lock security state after successful authentication. This authorization model is examined in greater detail in the threat-actor and trust-boundary sections.

**2.4 Asset A-002 — Cryptographic Key Material**

**Description**

App Lock relies on cryptographic key material that protects its encrypted application data.

The current architecture has a common Android Keystore-backed root of trust. Under that root, separate encrypted storage mechanisms protect:

- The credential store.

- The SQLCipher database passphrase.

- The persistent authentication lockout state.

- Encrypted vault and intruder-photo files.

The database passphrase is a separate randomly generated 32-byte secret.

Vault files use encrypted-file protection with independently generated per-file cryptographic material rather than deriving their encryption keys from the user's PIN.

**Security Requirements**

Cryptographic key material must provide:

- **Confidentiality:** Usable key material must not be disclosed to unauthorized actors.

- **Integrity:** Key material must not be substituted, modified, or redirected to attacker-controlled cryptographic material without detection.

- **Isolation:** Independent cryptographic secrets must remain logically distinct even where they share the same underlying Keystore root of trust.

- **Availability:** Required key material must remain usable to authorized application functionality under supported platform conditions.

- **Trust-root integrity:** The Android Keystore protection boundary must remain intact.

**Security Significance**

Compromise of the cryptographic root of trust or usable subordinate key material can defeat multiple confidentiality protections simultaneously.

The database passphrase is particularly sensitive because possession of the passphrase permits decryption of the SQLCipher database.

The Keystore master key is consequently treated as a foundational security asset rather than merely an implementation detail.

**2.5 Asset A-003 — Vault Payloads**

**Description**

Vault payloads are files that users intentionally move into App Lock for confidential storage.

The current implementation stores each payload as an encrypted blob in App Lock's private storage.

Files are identified on the filesystem using random UUID-based names rather than the user's original display names.

The payload itself is protected using AES-256-GCM-based encrypted-file storage.

**Security Requirements**

Vault payloads require:

- **Confidentiality:** Unauthorized actors must not obtain usable plaintext.

- **Integrity:** Unauthorized modification of a payload must not result in acceptance of attacker-controlled or silently corrupted content.

- **Authorization:** Payload access through the application must require appropriate App Lock authorization.

- **Metadata protection:** The relationship between a payload and its user-visible identity must remain confidential to the extent provided by the encrypted database.

**Security Significance**

Vault payloads are among the highest-value application-managed assets because compromise directly exposes user-selected confidential content.

The filesystem representation alone must not be treated as equivalent to the protected vault.

The UUID-based filenames reduce direct filename disclosure, but they do not independently provide confidentiality. Protection depends on the combined security of:

- The encrypted file.

- The database index.

- The cryptographic keys.

- The application storage boundary.

- The authorization boundary.

**2.6 Asset A-004 — Intruder Photographs**

**Description**

Intruder photographs are images captured when authentication failures trigger the application's intruder-capture behavior.

They are stored using the same encrypted file-storage mechanism as other sensitive application-managed files, but in a separate directory.

These images may contain identifiable facial information and therefore represent highly sensitive user-associated data.

**Security Requirements**

Intruder photographs require:

- **Confidentiality:** Unauthorized actors must not obtain the image contents.

- **Integrity:** Unauthorized modification or substitution must be prevented or detectable.

- **Authorization:** Access through the application must require appropriate authorization.

- **Metadata confidentiality:** Associated event information must remain protected.

**Security Significance**

Compromise of intruder photographs may reveal identifiable individuals, authentication events, and information about activity around the protected device.

Their security significance is therefore independent of the Vault.

An attacker must not gain access to intruder photographs merely because the attacker can access application storage or manipulate application lifecycle state.

**2.7 Asset A-005 — Encrypted Application Database**

**Description**

The application database is a Room database protected by SQLCipher.

The database contains security-sensitive application state including, at minimum:

- Protected-application configuration.

- Vault index information.

- Security events.

- Intruder events.

- Vault metadata.

- Timestamps and associated event information.

The database therefore contains both security-critical state and sensitive metadata.

**Security Requirements**

The database requires:

- **Confidentiality:** Unauthorized actors must not obtain readable database contents.

- **Integrity:** Unauthorized actors must not modify security-sensitive rows or security policy state.

- **Availability:** Database corruption or key loss must not silently produce an insecure security state.

- **Metadata confidentiality:** Sensitive relationships and usage information contained within database rows must remain protected.

- **Migration integrity:** Database migration must preserve the security properties of the protected data and must not silently fall back to an insecure representation.

**Security Significance**

The database is not merely an implementation repository.

Even without obtaining a Vault payload, database disclosure may reveal:

- Which applications the user protects.

- Vault item identities.

- File sizes.

- MIME types.

- Timestamps.

- Security-event history.

- Intruder-event information.

- Other behavioral metadata represented by the stored records.

Consequently, confidentiality of the encrypted database itself is a security requirement.

**2.8 Asset A-006 — Security Enforcement Mechanism**

**Description**

The continuous operation of the App Lock enforcement mechanism is a security asset.

The enforcement path includes the Android mechanisms responsible for detecting protected applications, presenting authentication when required, maintaining protection across lifecycle events, and restoring protection after reboot.

Security-relevant components include:

- AppDetectionService.

- ApplicationLockEngine.

- LockPolicyManager.

- LockSessionManager.

- LockScreenActivity.

- ProtectionWatchdogService.

- BootReceiver.

- Device-admin uninstall protection where enabled.

**Security Requirements**

The enforcement mechanism requires:

- **Availability:** Protection must remain operational under supported conditions.

- **Integrity:** An attacker must not weaken or disable security enforcement without authorization.

- **Authorization integrity:** Security policy must not be modified to remove protection without authorization.

- **Persistence:** Security-critical protection state must not be bypassable through application restart or device reboot where persistence is required.

- **Fail-safe behavior:** Loss of a security mechanism must not silently be represented as successful protection.

**Security Significance**

The enforcement mechanism is a security asset because protected applications are only protected while App Lock can observe application transitions and enforce its authorization boundary.

The current architecture contains a known limitation: loss of accessibility-based detection can cause protected applications to open without the App Lock gate. This is therefore treated as a security exposure rather than an ordinary availability defect.

The detailed analysis of this failure mode belongs in the threat, risk, and control sections.

**2.9 Asset Relationships**

The primary assets are interdependent.

The security model can be represented as:

Android Keystore

│

▼

Cryptographic Root of Trust

│ │

┌───────────┘ └─────────────┐

▼ ▼

Credential Storage Application Data

│ │ │

▼ ▼ ▼

PIN Verification SQLCipher Encrypted Files

│ │

▼ ▼

Vault Metadata Vault Payloads

│

▼

Intruder Events

│

▼

Security Events

The enforcement mechanism operates alongside this storage hierarchy:

Device Unlock

│

▼

App Lock Authorization

│

├──────────────► Protected Application Access

│

└──────────────► Vault / Security Data Access

The Android device-unlock state does not substitute for the App Lock authorization state.

**2.10 Security Property Matrix**

| **Asset** | **Confidentiality** | **Integrity** | **Availability** | **Authorization** | **Persistence** |
|----|----|----|----|----|----|
| A-001 User PIN | Critical | Critical | Required | Critical | Required |
| A-002 Cryptographic Key Material | Critical | Critical | Critical | Critical | Required |
| A-003 Vault Payloads | Critical | Critical | Required | Critical | Required |
| A-004 Intruder Photographs | Critical | High | Required | Critical | Required |
| A-005 Encrypted Database | Critical | Critical | Critical | Critical | Required |
| A-006 Enforcement Mechanism | — | Critical | Critical | Critical | Critical |

The ratings in this matrix describe security significance, not the project's formal threat risk rating. Formal likelihood, impact, and risk classification are established in Section 11.

**2.11 Security-Critical State**

In addition to confidential data, App Lock contains state whose unauthorized modification could directly weaken security.

Security-critical state includes:

- Current authentication credential configuration.

- Failed-authentication counter.

- Lockout-until state.

- Protected-application list.

- Protection policies.

- Relock configuration.

- Enforcement enablement state.

- Device-admin protection state where enabled.

- Security and intruder-event records.

- Vault index state.

- Authorization-session state while active.

The distinction between **confidential assets** and **security-critical state** is intentional.

An asset does not need to contain a secret to require strong integrity protection. A protected-app policy, for example, may be non-secret in some contexts while still being security-critical because unauthorized modification can remove the protection boundary.

**2.12 Asset Protection Principles**

The following principles apply throughout the Threat Model:

1.  Sensitive data must not be treated as protected solely because it resides inside the application sandbox.

2.  Encryption at rest does not replace runtime authorization.

3.  Runtime authorization does not replace encryption at rest.

4.  Cryptographic key material must be evaluated independently from the data it protects.

5.  Security-critical state requires integrity protection even when confidentiality is not its primary property.

6.  Availability failures that remove a security boundary are security-relevant failures.

7.  Metadata is treated as sensitive where it can reveal protected applications, user activity, vault contents, or security events.

8.  A planned protection is not an effective protection until implemented.

9.  An implemented protection is not security-verified until appropriate security evidence exists.

10. The security properties defined here apply to the current system and must be reassessed when the underlying architecture or implementation changes.

**2.13 Section 2 Completion Criteria**

Section 2 is complete when:

- All primary security-sensitive assets have been identified.

- The user credential is explicitly represented as an asset.

- Cryptographic key material and the Keystore root of trust are explicitly represented.

- Vault payloads are separately represented from their database metadata.

- Intruder photographs are separately represented.

- The encrypted database and its metadata are explicitly protected.

- The enforcement mechanism is recognized as a security asset.

- Applicable security properties are defined for each asset.

- Security-critical state is distinguished from confidential data.

- The relationship between the Android device-unlock boundary and App Lock authorization is established.

- Detailed threats, controls, risk ratings, and verification evidence remain reserved for their respective sections.

The asset model established here is the authoritative basis for the threat analysis in Section 8 and the control and risk analysis that follows.
