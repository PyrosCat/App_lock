# ADR-008 — Centralize Logging and Diagnostics

**Status:** Accepted (baseline; not yet implemented) · **Date:** 2026-07-19 · **Source:** TAS §71/§42, SDS §2.16

## Context
Observability requirements (FR-276..300) demand structured, privacy-aware, centrally-filtered logging; scattered `Log.d` calls (current state) cannot satisfy redaction or production configuration (FR-246).

## Decision
All logging flows through a central Logging Service with category separation (application/security/audit/performance/diagnostics), structured formatting, and build-appropriate configuration.

## Consequences
Built in M5; M1–M4 code writes against a thin logging interface from the start so the swap is mechanical.

## Related requirements
FR-246, FR-276..284, NFR-OBS-001..008.
