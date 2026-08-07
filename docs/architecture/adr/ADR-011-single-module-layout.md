# ADR-011 — Retain Single-Module :app with Package-Enforced Layering

**Status:** Accepted · **Date:** 2026-07-19 · **Source:** M0 decision; SDS §4.1

## Context
SDS §4.1 defines module organization as a logical specification: physical structure may be adapted provided logical boundaries and dependency rules are preserved. The codebase is ~3.9k LOC with one developer; Gradle multi-module would add build complexity with no current benefit.

## Decision
Keep the single `:app` Gradle module. Enforce the SDS layer/dependency rules at the package level (`presentation/`, `domain/`, `service/`, `data/`, `security/`, `infrastructure/`, `platform/`, `di/`) via static analysis introduced in M1.

## Alternatives
Multi-module split per layer or per feature — deferred; revisit when build times or team size make isolation valuable (tracked as a standing review item at phase gates).

## Consequences
Layer violations are caught by lint/detekt dependency rules rather than compiler-enforced module boundaries; the M1 pipeline must include that check for this ADR to hold.

**Cross-links (added 2026-08-06, GOVERNANCE §2.3):** the M1 check this ADR requires is delivered by **ADR-016** (Konsist rules R1–R4). The WP6 package realignment executing this layout is constrained by **ADR-018**: `AppDetectionService` and `UninstallProtectionReceiver` are permanently FQCN-pinned and stay outside the target layers.
