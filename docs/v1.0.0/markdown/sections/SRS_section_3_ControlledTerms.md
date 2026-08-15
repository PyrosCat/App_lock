# Software Requirements Specification

## Version 1.0.0

## 3. Controlled Terms

**Protected application:** An eligible installed application selected for App Lock protection.

**Protection session:** A time-bounded local authorization created after successful PIN or eligible biometric authentication.

**Protected:** Required capabilities are available, protection state is loaded, and the core protection path has passed its current check.

**Degraded:** The core path remains available, but responsiveness or continuity may be affected by an operating-system condition.

**Protection interrupted:** A required capability or core service is unavailable and App Lock cannot currently make its normal protection promise.

**Action required:** The user must complete a stated step before normal protection can resume.

**Unknown or not verified:** App Lock has not yet completed the checks needed to claim a protected state or current evidence is stale.

**Destructive reset:** Removal of all App Lock credentials, protected-application selections, settings, and local diagnostic records, followed by a return to initial setup.
