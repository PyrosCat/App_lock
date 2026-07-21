# ADR-014 — Supported API Range: minSdk 26, targetSdk 35, Forward-Compatible to Future Levels

**Status:** Accepted · **Date:** 2026-07-19 · **Source:** M0 decision (user-approved 2026-07-19)

## Context
The new baseline leaves the supported Android range "defined by project requirements" (TAS §6). As built: minSdk 26 / targetSdk 35. NFR-COMP-001/009 require verified compatibility across the supported range and forward compatibility.

## Decision
- **minSdk 26** (Android 8.0): natural technical floor — notification channels native, all stack dependencies satisfied, ~97 % device coverage.
- **targetSdk 35**, bumped annually per Google Play policy (NFR-COMPY-002); compileSdk tracks the latest stable.
- **Forward compatibility is a design obligation:** the application shall absorb future API levels (e.g., API 37) without redesign. Platform APIs are confined to the Platform Integration layer (SDS §4.8); reliance on deprecated APIs is prohibited; behavior-change review is part of each annual bump.

## Verification fleet
- Local headless emulator (2012 i7-3520M host): Pixel_5 API 30 x86 (primary interim verified
  level; host runs x86_64 guests unreliably, so this is its only viable image).
- Dedicated emulator host: **NucBox G5** — arrived 2026-07-20, setup in progress; intended to
  run the wider local emulator matrix. A hardware limitation was noted; its impact on the
  matrix is being assessed (RAM/CPU → sequential matrix, still viable; virtualization → falls
  back to driving physical devices + CI runners). This note is updated once confirmed.
- Physical device: **Moto G (2025), Android 15+** — arriving **2026-07-22**. (Originally a Moto
  G 2023 due 2026-07-20; shipping cancelled and replaced.)
- CI Gradle-managed-device matrix across API 26/29/33/35 — M1/WP8 deliverable (NFR-COMP-001
  evidence); leans on CI Linux runners (KVM) independent of local-host limitations.

## Consequences
Until the CI matrix exists, API levels other than 30 are supported-but-unverified; this gap is tracked in the RTM (NFR-COMP-001 row) and closes in M1.
