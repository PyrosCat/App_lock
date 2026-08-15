# Database Design Specification

## Version 1.0.0

## 3. Data Classification

### 3.1 Classification model

| Classification | Version 1.0.0 examples | Required handling |
|---|---|---|
| Private settings | Relock choice, haptics, reduced-motion choice | Application-private storage; validated values; excluded from backup |
| Confidential protection configuration | Protected package identifiers, biometric enablement choice | Encrypted or Keystore-backed storage; authenticated access in the interface; no notifications or ordinary logs |
| Secret authentication and encryption material | PIN verifier, salt and verification parameters, retry state, database-opening material | Keystore-backed protection; no export; no diagnostic exposure; no plaintext fallback |
| Transient security state | Current foreground package, active lock target, in-memory session, current health | Memory only; cleared on process death; no history |

### 3.2 Protected package identifiers

A protected package identifier can reveal which applications the user considers sensitive. It is therefore confidential even though the identifier is available elsewhere on the phone. It is stored in the encrypted relational database and is excluded from notification text, general diagnostics, backup, and ordinary logging.

### 3.3 Authentication information

The application stores no raw or reversible PIN. It stores only an approved verifier and the parameters necessary to evaluate future attempts. The verifier remains sensitive because it may be subject to offline guessing if exposed.

Biometric templates and matching remain entirely within Android. The application stores only whether the user chose to offer biometrics, when that choice requires persistence.

### 3.4 Usage and operational information

The current foreground package obtained through Usage Access is processed only for the immediate protection decision. It is not persisted as an event, duration, frequency, recent-app list, metric, or diagnostic item.

Current service and permission states are queried from Android. They are not authoritative database values.

### 3.5 Logging restrictions

Storage logs may identify a generalized operation category and success or failure. They shall not contain:

- the PIN or candidate length beyond a generic validation category;
- the verifier, salt, derivation parameters, or database-opening material;
- a protected package identifier, label, or count tied to a user action;
- the current foreground package;
- file paths or database connection material;
- biometric enrollment detail; or
- complete internal exception output in a user-accessible surface.
