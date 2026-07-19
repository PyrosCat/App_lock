# ADR-006 — Adopt WorkManager for Background Processing

**Status:** Accepted (baseline; not yet implemented) · **Date:** 2026-07-19 · **Source:** TAS §70/§71, SDS §15

## Context
Background work must survive process death and respect Doze/battery policies (TAS Part IV).

## Decision
WorkManager coordinates deferred/periodic background work via a central Background Task Coordinator (SDS §15). AlarmManager remains permitted for time-critical scheduling (TAS §70 baseline: "WorkManager / AlarmManager as appropriate").

## Consequences
Net-new subsystem (M5; automation triggers in M4 may adopt it earlier). The existing specialUse foreground watchdog service remains valid for accessibility-health monitoring and is folded into Health Monitoring rather than replaced.

## Related requirements
FR-230, FR-287, FR-332, NFR-RES-007.
