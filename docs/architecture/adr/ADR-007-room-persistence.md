# ADR-007 — Use Room (or equivalent) as the Primary Persistence Layer

**Status:** Accepted (baseline; as built) · **Date:** 2026-07-19 · **Source:** TAS §70/§71

## Context
Type-safe local persistence with schema versioning and migration support is required.

## Decision
Room is the persistence layer. As built since Phase 1; encrypted variant per ADR-012.

## Consequences
`fallbackToDestructiveMigration()` in AppLockDatabase.build violates FR-228 (every version needs a migration path with rollback/recovery) and is removed in M1.

## Related requirements
FR-228/229, NFR-REL-005/006.
