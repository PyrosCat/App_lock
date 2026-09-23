# ADR-016A — R2 Layer Ranking: infrastructure/logging Below Domain

**Status:** Accepted (2026-09-23) · **Date:** 2026-09-23 · **Source/authority:** Project lead, M7 WP2 change F4 design
check · **Supersedes:** ADR-016, R2 dependency policy only (R1, R3, R4, and the choice of Konsist stay under
ADR-016)

## Context
ADR-008 requires code to log through a thin logging interface, so that a later central logging service replaces
only the implementation. Most code calls `android.util.Log` directly. JVM unit tests run against the Android stub
jar, where every `android.util.Log` call throws. A test that reaches a logging path therefore fails before it can
check any behaviour. Change F4 (M7 WP2) adds the interface and its first callers.

Every core layer must be able to import the interface, including `domain/` and `data/`. ADR-011 lists
`infrastructure/` among the layer packages. ADR-001 puts Infrastructure/Platform at the bottom of the dependency
chain.

ADR-016's R2 rule ranks five layers: domain, data, security, service, and presentation (WP6 implementation note).
R2 does not check a package outside these layers. Such a package can import any layer, and any layer can import it.

## Decision
R2 ranks `infrastructure/logging` as the lowest core layer:

`infrastructure/logging (0) < domain (1) < {data (2), security (2)} < service (3) < presentation (4)`

The complete R2 policy is:
- A core-layer file may import another core layer only when that layer has a lower rank. A file may always import
  its own layer.
- Two different layers with the same rank must not import each other (data and security).
- Thus every core layer may import `infrastructure/logging`, and `infrastructure/logging` must not import a core
  layer. It holds the logging interface and its Android implementation. That implementation imports
  `android.util.Log`, which is not a core layer.
- `platform/`, `di/`, the two ADR-018 pinned entry points, and the root `Application` are exempt.
- The frozen baseline of five existing edges remains. It burns down in M2/M3.
- Other `infrastructure/` subpackages have no rank. R2 does not check them.

## Alternatives considered
- **Put the interface in `domain/`.** Rejected: domain holds business rules, and logging is not a business rule.
- **Leave `infrastructure/` unranked.** Rejected: R2 would not check the package, so the logging code could import
  an outer layer without a test failure.
- **Rank all of `infrastructure/` below domain.** Rejected: domain could then import any later infrastructure
  subpackage, for example Android-bound background work or configuration, without a decision for that subpackage.

## Consequences
- `ArchitectureRulesTest` enforces the new rank. A second test confirms that R2 assigns the rank to the
  `infrastructure/logging` files, so a wrong path cannot leave them unchecked.
- One R2 layer is a subpackage, not a top-level package. This adds a special case to the rule.
- A rank for another `infrastructure/` subpackage needs a new ADR (GOVERNANCE §2.2).
- The rank table in the ADR-016 WP6 implementation note is a historical record. This ADR holds the current table.

## Related requirements
NFR-MNT-001, NFR-MNT-002, NFR-MNT-003.

## Related
ADR-001, ADR-008, ADR-011, ADR-016 (R2 policy superseded), ADR-018.

## Implementation status
2026-09-23: proposed with change F4 and accepted 2026-09-23. `ArchitectureRulesTest` enforces the rank.
