# ADR-003 — Use the Repository Pattern for Data Access

**Status:** Accepted (baseline) · **Date:** 2026-07-19 · **Source:** TAS §71, SDS §14

## Context
Business logic must not depend on storage technology; all persistence flows through one controlled layer.

## Decision
All persistent operations pass through repository interfaces; direct database access from services, ViewModels, or presentation components is prohibited (SDS §14).

## Alternatives
Direct DAO usage from ViewModels (partially the current state).

## Consequences
Existing DAOs remain; repository interfaces are extracted in M1 (VaultRepository already exists; protected-apps and security-event access get the same treatment).

## Related requirements
FR-353 (interface-based design), NFR-MNT-001, TAS §34.3.
