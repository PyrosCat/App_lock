# Database Design Specification

## Version 1.0.0

## 5. Storage Technologies

### 5.1 Private application preferences

Ordinary settings use the Android private application preference facility already selected by the software. The file is accessible only inside the application sandbox under ordinary platform operation.

Each setting has:

- a defined supported value set;
- a safe default;
- validation on read and write; and
- clear ownership by the settings responsibility.

Unknown values fall back to the safe supported default. The default relock behavior is immediate.

### 5.2 Keystore-backed protected preferences

Credential, lockout, biometric-choice, and database-opening records use preference encryption rooted in Android Keystore. The application does not substitute an application constant, device identifier, PIN, or package name for Keystore-protected random material.

Protected preference failure is explicit. The application does not fall back to ordinary preferences or plaintext files.

### 5.3 Encrypted embedded relational database

The protected-app database resides in private internal storage and is encrypted using the existing approved embedded database facility. It provides authenticated opening, transaction support, schema versioning, and uniqueness enforcement.

The physical database is local to the phone. It is not copied to shared storage, exposed through a content provider, attached to a remote service, or included in Android backup.

### 5.4 Internal files

Version 1.0.0 does not require user files, export packages, backup artifacts, media, diagnostic bundles, or a persistent application cache. Temporary files used by a supported migration, if any, remain in private storage, have a bounded lifetime, contain only the minimum migration data, and are removed after success or controlled rollback.

### 5.5 External and shared storage

No version 1.0.0 data is stored in external or shared storage. The application does not request general storage access.

### 5.6 Cache

No persistent cache is required. Android-provided labels and icons may be held temporarily in memory for list rendering. Such data is disposable and does not become authoritative.

### 5.7 Android backup configuration

The application explicitly disables or excludes backup of:

- private settings;
- protected preferences;
- encrypted database files;
- database journals and temporary files; and
- any migration artifact.

Backup exclusion is verified on supported API levels. The absence of a backup path is deliberate and is communicated by the recovery design.

## Volume II — Logical Database Design
