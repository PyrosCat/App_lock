# Architecture Decision Records — Index

Template: ID, Title, Status, Context, Decision, Alternatives, Consequences, Related requirements
(TAS §71.2). ADR-001..010 backfill the baseline decisions enumerated in TAS §71.3; ADR-011+ are
project decisions. New ADRs are required for any change per ADR-010.

**Lifecycle rules — when to create, what may be amended, and the supersession protocol — are
governed by [`../../process/GOVERNANCE.md`](../../process/GOVERNANCE.md) §2.** Core rule: the
decision content of an accepted ADR is immutable; decisions change only by superseding.
This index is updated in the same commit as any ADR addition or status change. A lettered
suffix (e.g. `ADR-013A`) marks an ADR that supersedes `ADR-013` in that lineage, listed directly
beneath its root (GOVERNANCE §2.4).

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](ADR-001-layered-clean-architecture.md) | Adopt Layered Clean Architecture | Accepted (baseline) |
| [ADR-002](ADR-002-mvvm-presentation.md) | Use MVVM for the Presentation Layer | Accepted (baseline) |
| [ADR-003](ADR-003-repository-pattern.md) | Use the Repository Pattern for Data Access | Accepted (baseline) |
| [ADR-004](ADR-004-centralized-security-services.md) | Centralize Security Services | Accepted (baseline) |
| [ADR-005](ADR-005-keystore-key-storage.md) | Android Keystore (or equivalent) for Cryptographic Key Storage | Accepted (baseline; as built) |
| [ADR-006](ADR-006-workmanager-background.md) | Adopt WorkManager for Background Processing | Accepted (baseline; not yet implemented) |
| [ADR-007](ADR-007-room-persistence.md) | Use Room (or equivalent) as the Primary Persistence Layer | Accepted (baseline; as built) |
| [ADR-008](ADR-008-centralized-logging.md) | Centralize Logging and Diagnostics | Accepted (baseline; not yet implemented) |
| [ADR-009](ADR-009-dependency-injection.md) | Enforce Dependency Injection Across Application Services | Accepted (baseline) |
| [ADR-010](ADR-010-architecture-review-gate.md) | Require Architecture Review for Major Technology Changes | Accepted (baseline) |
| [ADR-011](ADR-011-single-module-layout.md) | Retain Single-Module :app with Package-Enforced Layering | Accepted |
| [ADR-012](ADR-012-sqlcipher-room.md) | SQLCipher (net.zetetic) as the Room-"(or equivalent)" Encrypted Persistence | Accepted (as built) |
| [ADR-013](ADR-013-accessibility-detection.md) | Accessibility-Service-Based Foreground App Detection | Superseded by ADR-013A |
| [ADR-013A](ADR-013A-two-tier-detection.md) | Two-Tier Foreground Detection: UsageStatsManager Baseline, Accessibility Optional | Accepted — implementation in M2 |
| [ADR-014](ADR-014-api-range.md) | Supported API Range: minSdk 26, targetSdk 35, Forward-Compatible to Future Levels | Accepted |
| [ADR-015](ADR-015-hilt-adoption.md) | Hilt Adoption Plan (Replacing the Graph Service Locator) | Accepted — execution in M1 |
