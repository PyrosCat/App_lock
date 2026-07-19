# ADR-002 — Use MVVM for the Presentation Layer

**Status:** Accepted (baseline) · **Date:** 2026-07-19 · **Source:** TAS §70/§71, SDS §6

## Context
UI logic must be independently testable and free of business rules.

## Decision
MVVM: Views render state and forward events; ViewModels own UI state, invoke application services, and stay free of Android view classes; a Navigation Coordinator owns transitions (SDS §6.4).

## Alternatives
MVI; ad-hoc Compose state (current partial approach — 3 of ~8 screens have ViewModels).

## Consequences
PIN entry screens and navigation (enum `when` in MainActivity.kt) are refactored to ViewModels + Navigation Coordinator during M3. SelfLock re-gating semantics (defect F3 fix) must be preserved under regression tests.

## Related requirements
NFR-TEST-001, NFR-MNT-001, SDS §6 traceability set.
