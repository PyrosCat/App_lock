# Database Design Specification

## Version 1.0.0

## 15. Database Security

### 15.1 Confidentiality

Protected package identifiers are encrypted at rest in the relational database. PIN verifier, retry state, biometric choice where protected, and database-opening material are encrypted and authenticated in Keystore-backed protected preferences.

Ordinary private settings need Android sandbox protection but do not receive weaker copies of confidential information.

### 15.2 Key protection

Database-opening material is generated from a cryptographically secure random source and protected by Android Keystore-backed storage. It is not:

- derived solely from the App Lock PIN;
- hard-coded;
- based on a phone identifier;
- stored in the relational database;
- copied into a log, crash message, or diagnostic screen;
- included in Android backup; or
- exported.

### 15.3 Access control

Only the protected-application persistence responsibility accesses the relational database. Only authentication and storage initialization access the credential and database-opening records. Screens and Android detector callbacks use application operations instead of opening stores directly.

The application exports no database access component.

### 15.4 Integrity

Integrity is protected by authenticated protected preferences, encrypted database opening, transactions, uniqueness constraints, schema validation, and post-migration verification.

An integrity failure cannot be resolved by accepting a row, verifier, or schema value that fails validation.

### 15.5 Credential isolation

The PIN verifier is outside the relational database so that package selection queries cannot expose authentication data and database migration does not need to transform the credential. The raw PIN exists only during setup or verification and never becomes a database value.

### 15.6 Usage privacy

The database contains no foreground-application events from Usage Access. It cannot be used to reconstruct which application the user opened or when it was used. The only application identity retained is the set the user deliberately selected for protection.

### 15.7 Secure removal

Removing one protected application deletes its active row and invalidates its in-memory session. Version 1.0.0 does not promise physical overwrite of flash storage.

Clearing application data or uninstalling removes the application’s local files and protected preferences. Destruction of the Keystore-protected opening material provides cryptographic erasure of any remaining encrypted database bytes within Android storage limits.

### 15.8 No plaintext fallback

If encrypted storage cannot initialize, the application remains unavailable or requires reset. It does not create a plaintext relational database or store credentials in ordinary preferences.

### 15.9 Backup and export security

There is no backup or export path. All application-controlled data is excluded from Android backup, including ordinary preferences. This prevents a restored preference set from referring to absent Keystore material or silently transferring a protected-app list to another phone.

### 15.10 Security verification conditions

Security verification demonstrates:

- encrypted database bytes do not expose selected package identifiers through simple inspection;
- protected preferences cannot be read as plaintext;
- raw PIN values do not appear in any application-controlled storage;
- database-opening material does not appear in the database or logs;
- no Usage Access history is written;
- no Accessibility-service data or configuration exists;
- no application data participates in Android backup; and
- storage failure does not create a bypass or empty protected set.

## Volume IV — Database Operations and Lifecycle
