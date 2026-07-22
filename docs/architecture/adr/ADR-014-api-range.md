# ADR-014 — Supported API Range: minSdk 26, targetSdk 35, Forward-Compatible to Future Levels

**Status:** Accepted · **Date:** 2026-07-19 · **Source:** M0 decision (user-approved 2026-07-19)

## Context
The new baseline leaves the supported Android range "defined by project requirements" (TAS §6). As built: minSdk 26 / targetSdk 35. NFR-COMP-001/009 require verified compatibility across the supported range and forward compatibility.

## Decision
- **minSdk 26** (Android 8.0): natural technical floor — notification channels native, all stack dependencies satisfied, ~97 % device coverage.
- **targetSdk 35**, bumped annually per Google Play policy (NFR-COMPY-002); compileSdk tracks the latest stable.
- **Forward compatibility is a design obligation:** the application shall absorb future API levels (e.g., API 37) without redesign. Platform APIs are confined to the Platform Integration layer (SDS §4.8); reliance on deprecated APIs is prohibited; behavior-change review is part of each annual bump.

## Verification fleet

Live per-host status is maintained in the reports fleet index
(`docs/reports/README.md`), backed by dated records in `docs/reports/fleet/` — that is the
source of truth, not this ADR. Fleet composition and the verification approach:

- **2012 i7-3520M host** — Pixel_5 API 30 x86 only (runs x86_64 guests unreliably); primary
  interim verified level and the machine that produced the WP1 CI baseline.
- **NucBox G5** — dedicated emulator host; runs the wider local matrix. **Resolved
  2026-07-20** (report `2026-07-21_fleet-nucbox-g5.md`): WHPX acceleration usable and x86_64
  images boot, so it executes the full API 26/29/33/35 matrix natively — **no
  virtualization fallback needed.** Low-power CPU ⇒ matrix driven sequentially.
- **Moto G (2025), Android 15+** — physical device, arriving 2026-07-22 (replaced a cancelled
  Moto G 2023 order).
- **CI Gradle-managed-device matrix** across API 26/29/33/35 — M1/WP8 deliverable, on CI Linux
  runners (KVM), independent of any local-host limitation.

Boot capability ≠ compatibility verification: NFR-COMP-001 evidence requires the WP2
regression harness actually running against each level, not merely booting the AVDs.

## Consequences
Until the CI matrix exists, API levels other than 30 are supported-but-unverified; this gap is tracked in the RTM (NFR-COMP-001 row) and closes in M1.
