# ADR-009 — Enforce Dependency Injection Across Application Services

**Status:** Accepted (baseline) · **Date:** 2026-07-19 · **Source:** TAS §71, SDS §5.5

## Context
SDS §5.5: dependencies shall not be retrieved through global service locators or unmanaged singletons unless explicitly approved for infrastructure-wide services.

## Decision
Constructor-based DI supplied by a DI container (Hilt per ADR-015). The existing `core/Graph.kt` service locator is a documented deviation scheduled for removal.

## Consequences
See ADR-015 for the adoption plan and interim rules.

## Related requirements
FR-353, NFR-TEST-001, NFR-MNT-001.
