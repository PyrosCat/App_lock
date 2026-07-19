# ADR-014 — Supported API Range: minSdk 26, targetSdk 35, Forward-Compatible to Future Levels

**Status:** Accepted · **Date:** 2026-07-19 · **Source:** M0 decision (user-approved 2026-07-19)

## Context
The new baseline leaves the supported Android range "defined by project requirements" (TAS §6). As built: minSdk 26 / targetSdk 35. NFR-COMP-001/009 require verified compatibility across the supported range and forward compatibility.

## Decision
- **minSdk 26** (Android 8.0): natural technical floor — notification channels native, all stack dependencies satisfied, ~97 % device coverage.
- **targetSdk 35**, bumped annually per Google Play policy (NFR-COMPY-002); compileSdk tracks the latest stable.
- **Forward compatibility is a design obligation:** the application shall absorb future API levels (e.g., API 37) without redesign. Platform APIs are confined to the Platform Integration layer (SDS §4.8); reliance on deprecated APIs is prohibited; behavior-change review is part of each annual bump.

## Verification fleet
- Local headless emulator: API 30 (primary interim verified level).
- Physical device: Moto G (2023), Android 13+ (joining 2026-07-20).
- Dedicated emulator host: NucBox G5 (joining 2026-07-20) — extends the locally testable matrix.
- CI Gradle-managed-device matrix across API 26/29/33/35 — M1 deliverable (NFR-COMP-001 evidence).

## Consequences
Until the CI matrix exists, API levels other than 30 are supported-but-unverified; this gap is tracked in the RTM (NFR-COMP-001 row) and closes in M1.
