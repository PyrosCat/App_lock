# ADR-015 — Hilt Adoption Plan (Replacing the Graph Service Locator)

**Status:** Accepted — implemented in M1/WP5 (2026-08-08) and closed at the M1 gate (2026-08-17): the device gating-regression suite ran green on the Hilt build (WP5 device exits — `docs/reports/campaigns/2026-08-08_wp5-harness_moto-g-2025.md` + `docs/reports/campaigns/2026-08-09_wp5-matrix_nucbox-g5.md`); see the M1 gate record `docs/reports/gates/2026-08-17_gate-m1.md` · **Date:** 2026-07-19 · **Source:** M0 decision; SDS §5.5, TAS §70

## Context
`core/Graph.kt` is a global service locator, prohibited by SDS §5.5. It was always intended as a placeholder ("can be replaced with Hilt later"). All constructed objects already use constructor injection; only lookup sites couple to `Graph`.

## Decision
Adopt **Hilt** (TAS §70 baseline) in migration phase M1:
1. Add Hilt to the version catalog and `AppLockApplication`.
2. Recreate each `Graph` provider as a Hilt `@Module`/`@Provides` (same lifecycles: all current singletons → `@Singleton`).
3. Migrate lookup sites: ViewModels via `@HiltViewModel`, activities/services via `@AndroidEntryPoint`, the accessibility service via entry-point accessors (a11y services cannot be constructor-injected).
4. Delete `Graph.kt`; CI forbids its reintroduction.

## Interim rule (effective immediately)
No new `Graph.*` lookup sites. New code takes dependencies as constructor parameters even while Graph still wires the object graph.

## Risks / mitigations
R8 + Hilt interaction is exercised by the CI release-build smoke test from day one; the device gating-regression suite (OV-3/OV-4/F3) runs against the migrated build before ADR closure.

## Consequences
NFR-TEST-001 improves (mock substitution); SDS §5 compliance achieved; ~15 files touched mechanically in M1.

## Implementation

**2026-08-08 (M1 / WP5) — executed** (Hilt 2.56.2 + hilt-navigation-compose, KSP). `di/AppModule.kt`
reproduces every former `Graph` member as `@Provides @Singleton` (identical construction
expressions; the two settings closures — `LockSessionManager.policyProvider` and `IntruderPolicy`
enabled/threshold — preserved verbatim; `@ApplicationScope` qualifier for the app `CoroutineScope`).
The 12 domain/data classes were left un-annotated (construction lives in the module). Entry points:
`@AndroidEntryPoint` field injection on `MainActivity`, `LockScreenActivity`, `AppDetectionService`,
`ProtectionWatchdogService`; `@HiltViewModel` on `AppListViewModel` / `VaultViewModel` /
`IntruderLogViewModel` plus two thin new VMs (`AuthGateViewModel`, `SettingsViewModel`) for the
composable-only consumers; `AppLockApplication` is `@HiltAndroidApp` and re-runs the startup
policy-cache warm-up via an injected `LockPolicyManager`. `Graph.kt` deleted; Konsist **R1** flipped
from "no new lookups" to the terminal "Graph must not exist / is not referenced."

**Deviations from the Decision's step 3 wording (implementation reality, decision content
unchanged):**
- `AppDetectionService` (the accessibility service) uses **`@AndroidEntryPoint` field injection**,
  which works for an `AccessibilityService`; the "entry-point accessors" this ADR earmarked for the
  a11y service were not needed there.
- `BootReceiver` instead required the **`@EntryPoint` + `EntryPointAccessors`** technique: a
  `BroadcastReceiver.onReceive` overrides an abstract member, so the `super.onReceive()` call Hilt's
  `@AndroidEntryPoint` member injection needs does not compile. (`UninstallProtectionReceiver` needs
  no DI and is not annotated.)

**Verification:** `assembleProdRelease` + `minifyProdReleaseWithR8` clean (the ADR's R8-interaction
risk is retired); 71 unit tests / 2 skipped / 0 failures; detekt + lint green. The device
gating-regression suite (OV-3/OV-4/F3) is the remaining exit criterion before ADR closure — it is
hardware-gated and runs at WP5 close / into WP8. Evidence:
`docs/reports/campaigns/2026-08-08_wp5-hilt-migration_2012-i7.md`.
