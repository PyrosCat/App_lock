# ADR-001 — Adopt Layered Clean Architecture

**Status:** Accepted (baseline) · **Date:** 2026-07-19 · **Source:** TAS §71 / Parts I–II

## Context
The application requires long-term maintainability, testability, and security isolation across UI, business logic, and storage.

## Decision
Adopt a layered Clean Architecture: Presentation → Application Services → Domain → Repository Interfaces → Repository Implementations → Infrastructure/Platform. Lower layers never depend on higher layers; circular dependencies are prohibited (TAS §8.4, SDS §4.11).

## Alternatives
Single-layer pragmatic structure (as built in Phases 1–3); strict multi-module Clean Architecture.

## Consequences
Existing code must be realigned to the layer packages during migration phase M1 (see MIGRATION_ASSESSMENT.md Phase 8). Layer rules are enforced by static analysis because the project remains single-module (ADR-011).

## Related requirements
FR-351/FR-352 (modular architecture, separation of concerns), NFR-MNT-001/002.
