# Software Requirements Specification

## Version 1.0.0

## Appendix A - Requirement Disposition

The following existing functional requirements are not included as normative version 1.0.0 obligations. Their identifiers remain reserved and are not renumbered or reused. Every inclusive identifier range in this appendix accounts for each identifier within the stated endpoints; the range notation does not create a new identifier.

### A.1 Authentication

- FR-004 - Pattern Authentication.
- FR-005 - Knock Code Authentication.
- FR-016 - Device Credential Integration.
- FR-021 - Randomized Numeric Keypad as a selectable feature.
- FR-025 - Authentication Audit Trail for user review.

### A.2 Lock Engine

- FR-037 - Newly Installed Application Detection and recommendation.
- FR-043 - Accessibility Event Monitoring.
- FR-045 - Accessibility Permission Verification.

### A.3 Protected Applications Management

- FR-058 and FR-059 - categories and individual policies.
- FR-061 through FR-071 - bulk actions, profiles, per-application policy, favorites, recommendations, and newly installed application workflow.
- FR-074 through FR-077 - recommendation exclusions, hidden applications, work profiles, and cloned applications.
- FR-079 and FR-080 - usage statistics and configuration export.

### A.4 Privacy and Concealment

- FR-081 through FR-085 - intruder capture, location, event notification, and history.
- FR-086 through FR-094 - disguises, camouflage, fake screens, hidden gestures, and protected-application notification masking.
- FR-097 and FR-098 - selectable shoulder-surfing options and invisible pattern behavior.
- FR-101 through FR-104 - privacy dashboard, stealth launch, secret launch, and decoy authentication.

### A.5 Vault

- FR-106 through FR-125 - all Vault capability. No Vault screen, permission, data, key, migration, or verification obligation applies.

### A.6 Scheduling and Automation

- FR-126 through FR-145 - all schedules, rules, triggers, profiles, recommendations, overrides, and automation records.

### A.7 Notifications and User Experience

- FR-147 through FR-154 - lock, unlock, failure, intruder, protected-application notification access, and notification history.
- FR-159 and FR-160 - selectable themes and interaction-customization settings.

### A.8 Security

- FR-165 and FR-166 - application network encryption and certificate pinning.
- FR-167 through FR-169 - root detection, root response, and runtime tamper detection.
- FR-175 and FR-176 - emergency or remote lock mode and backup encryption.

### A.9 Settings

- FR-185 through FR-191 - profiles, profile switching, theme selection, language selection, feedback settings, backup configuration, and import or export.
- FR-194 - advanced administrative settings.

### A.10 Backup and Recovery

- FR-196 through FR-205 - all backup, restore, recovery-password, retention, and cross-device migration capability.

### A.11 Performance

- FR-212 - Large Vault Performance.

### A.12 Administration and Diagnostics

- FR-222 - Secure Diagnostic Export.
- FR-223 - Maintenance Mode.
- FR-225 - Administrator Security Controls.

### A.13 Release Quality

- FR-226 and FR-227 - duplicate internal build and configuration-process obligations.
- FR-236 - Feature Flag Support.
- FR-244 and FR-245 - backup and restore validation.
- FR-247 through FR-250 - duplicate inventory, checklist, readiness, and acceptance mechanics.

### A.14 Operational Resilience

- FR-253 - Accessibility Service Recovery.
- FR-258 - interrupted Vault or backup file encryption recovery.
- FR-263 - Backup Recovery.
- FR-271 - separate Safe Mode feature.

### A.15 Observability

- FR-277 and FR-278 - long-term audit logging and continuous performance metrics.
- FR-281 and FR-282 - diagnostic reports and configurable log levels.
- FR-284 through FR-288 - export, event correlation, continuous database, task, and resource monitoring.
- FR-291 and FR-292 - retained startup metrics and historical metrics.
- FR-295 - separate observability dashboard.
- FR-297 through FR-300 - configurable thresholds, audit-trail integrity, operational reports, and separate observability readiness.

### A.16 Data Lifecycle

- FR-302 through FR-304 - ownership and creation or modification audit history.
- FR-307 - Secure Data Archiving.
- FR-309 - Vault Data Lifecycle Management.
- FR-314 through FR-316 - backup lifecycle, versioning, and expiration.
- FR-318 through FR-320 - general key rotation or retirement features and capacity forecasting.
- FR-324 - Data Lifecycle Reporting.

### A.17 Scalability and Resource Management

- FR-327 through FR-331 - Vault and audit scalability, general incremental loading, pagination, and deferred-subsystem initialization.
- FR-339 through FR-341 - general maintenance scheduling, resource reports, and forecasting.
- FR-344 and FR-345 - continuous degradation detection and large-dataset validation.
- FR-349 and FR-350 - recurring scalability assessment and separate readiness verification.

### A.18 Secure Development and Maintenance

- FR-351 through FR-375 are not repeated as functional product behavior. Applicable modularity, security, build, testing, dependency, documentation, packaging, and maintainability qualities are stated once in the NFR and design specifications. The source identifiers remain reserved.

### A.19 Complete Reserved-Identifier Record

For exact machine-readable disposition, the identifiers not included in version 1.0.0 are:

- FR-004, FR-005, FR-016, FR-021, FR-025, FR-037, FR-043, FR-045, FR-058, FR-059, FR-061, FR-062, FR-063, FR-064, FR-065, FR-066, FR-067, FR-068, FR-069, FR-070, FR-071, FR-074, FR-075, FR-076, FR-077, FR-079, FR-080, FR-081, FR-082, FR-083, FR-084, FR-085, FR-086, FR-087, FR-088, FR-089, FR-090, FR-091, FR-092, FR-093, FR-094, FR-097, FR-098, FR-101, FR-102, FR-103, FR-104, FR-106, FR-107, FR-108, FR-109, FR-110, FR-111, FR-112, FR-113, FR-114, FR-115, FR-116, FR-117, FR-118, FR-119, FR-120, FR-121, FR-122, FR-123, FR-124, FR-125.
- FR-126, FR-127, FR-128, FR-129, FR-130, FR-131, FR-132, FR-133, FR-134, FR-135, FR-136, FR-137, FR-138, FR-139, FR-140, FR-141, FR-142, FR-143, FR-144, FR-145, FR-147, FR-148, FR-149, FR-150, FR-151, FR-152, FR-153, FR-154, FR-159, FR-160, FR-165, FR-166, FR-167, FR-168, FR-169, FR-175, FR-176, FR-185, FR-186, FR-187, FR-188, FR-189, FR-190, FR-191, FR-194.
- FR-196, FR-197, FR-198, FR-199, FR-200, FR-201, FR-202, FR-203, FR-204, FR-205, FR-212, FR-222, FR-223, FR-225, FR-226, FR-227, FR-236, FR-244, FR-245, FR-247, FR-248, FR-249, FR-250, FR-253, FR-258, FR-263, FR-271, FR-277, FR-278, FR-281, FR-282, FR-284, FR-285, FR-286, FR-287, FR-288, FR-291, FR-292, FR-295, FR-297, FR-298, FR-299, FR-300.
- FR-302, FR-303, FR-304, FR-307, FR-309, FR-314, FR-315, FR-316, FR-318, FR-319, FR-320, FR-324, FR-327, FR-328, FR-329, FR-330, FR-331, FR-339, FR-340, FR-341, FR-344, FR-345, FR-349, FR-350, FR-351, FR-352, FR-353, FR-354, FR-355, FR-356, FR-357, FR-358, FR-359, FR-360, FR-361, FR-362, FR-363, FR-364, FR-365, FR-366, FR-367, FR-368, FR-369, FR-370, FR-371, FR-372, FR-373, FR-374, FR-375.
