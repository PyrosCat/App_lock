# Database Design Specification

## Version 1.0.0

## 9. Keys and Constraints

### 9.1 Package identity

The package identifier is the natural identity of a protected application. It is normalized using one documented representation and compared consistently.

The relational database enforces uniqueness. The application also validates package form before persistence and confirms current eligibility when selection originates from the installed-app screen.

### 9.2 Primary storage key

The physical store may use the package identifier directly as its primary key or use an existing internal row key with an additional unique package constraint. Version 1.0.0 does not require a migration solely to change between these equivalent physical choices.

The supported contract is uniqueness and stable lookup by package identifier, not a particular physical field name.

### 9.3 Value constraints

Ordinary settings accept only retained values. PIN verifier fields must form a complete supported format. Failed-attempt count cannot be negative. Lockout deadline must be absent or a valid time value defensively bounded at read.

Invalid protected authentication state produces unavailable authentication rather than a guessed default.

### 9.4 No foreign keys

No retained relationship requires a foreign key. Dormant foreign keys associated with excluded objects do not appear in the supported logical model.
