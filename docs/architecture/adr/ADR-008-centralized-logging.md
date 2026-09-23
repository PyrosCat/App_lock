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

**Implementation status (2026-09-23, M7 WP2 change F4):** the thin logging interface exists in
`infrastructure/logging`. DI binds its logcat implementation. The F4 adapters log through it. The other
`android.util.Log` callers move to it when they next change. The interface is best effort (see its KDoc): an
implementation never waits for sink capacity or delivery and drops a message under backpressure. The central
Logging Service does not exist yet.
