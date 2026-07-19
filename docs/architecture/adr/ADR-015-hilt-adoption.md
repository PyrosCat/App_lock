# ADR-015 — Hilt Adoption Plan (Replacing the Graph Service Locator)

**Status:** Accepted — execution in M1 · **Date:** 2026-07-19 · **Source:** M0 decision; SDS §5.5, TAS §70

## Context
`core/Graph.kt` is a global service locator, prohibited by SDS §5.5. It was always intended as a placeholder ("can be replaced with Hilt later"). All constructed objects already use constructor injection; only lookup sites couple to `Graph`.

## Decision
Adopt **Hilt** (TAS §70 baseline) in migration phase M1:
1. Add Hilt to the version catalog and `AppLockApplication`.
2. Recreate each `Graph` provider as a Hilt `@Module`/`@Provides` (same lifecycles: all current singletons → `@Singleton`).
3. Migrate lookup sites: ViewModels via `@HiltViewModel`, activities/services via `@AndroidEntryPoint`, the accessibility service via entry-point accessors (a11y services cannot be constructor-injected).
4. Delete `Graph.kt`; CI forbids its reintroduction.

## Interim rule (effective immediately)
No new `Graph.*` lookup sites. New code takes dependencies as constructor parameters even while Graph still wires the object graph.

## Risks / mitigations
R8 + Hilt interaction is exercised by the CI release-build smoke test from day one; the device gating-regression suite (OV-3/OV-4/F3) runs against the migrated build before ADR closure.

## Consequences
NFR-TEST-001 improves (mock substitution); SDS §5 compliance achieved; ~15 files touched mechanically in M1.
