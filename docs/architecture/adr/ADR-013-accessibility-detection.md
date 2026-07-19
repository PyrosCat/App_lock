# ADR-013 — Accessibility-Service-Based Foreground App Detection

**Status:** Accepted (as built) · **Date:** 2026-07-19 · **Source:** M0 decision; validated Phases 1–3

## Context
Lock enforcement requires detecting protected-app launches within NFR-PERF-012's 250 ms budget. As built: AppDetectionService consumes TYPE_WINDOW_STATE_CHANGED accessibility events feeding the lock engine — validated across gating campaigns (back/recents/fast-switch/rapid-relaunch).

## Decision
Retain accessibility-event detection as the primary mechanism.

## Alternatives
UsageStatsManager polling — rejected: polling latency cannot meet the 250 ms enforcement target reliably and costs battery.

## Known limitation (accepted)
`force-stop` of the app nulls `enabled_accessibility_services` platform-wide (inherent to accessibility lockers). Mitigation: ProtectionWatchdogService revocation alert (FR-179) + recovery guidance; maps forward to FR-253 (Accessibility Service Recovery).

## Consequences
The Trigger Processor abstraction (SDS §8.4) wraps this source in M1 so alternative/additional detection sources can be added without engine changes.
