# WP5 Local Verification — Hilt DI Migration (retire the Graph service locator)

- **Date:** 2026-08-08
- **Author / host:** 2012 i7 dev box, driving the Moto G 2025 over USB adb.
- **Produced against:** baseline `7bcb05e` (M1/WP4) + the WP5 change set (this work, pre-push)
- **Verifies:** M1_PLAN §2 (WP5) exit check — **local portion**; ADR-015. RTM rows are **not** updated
  here (WP8 close-out batch, M1_PLAN §5).

## Result — local gates PASS (both commits); device harness pending

The migration is a mechanical, behavior-preserving replacement of `core/Graph` with Hilt, staged as
two independently-green commits. The on-device WP2 gating-regression suite (the 4th exit criterion)
is hardware-gated and not run here.

### Commit A — introduce Hilt (Graph left in place but unreferenced)
`./gradlew testProdDebugUnitTest assembleDevDebug assembleProdRelease` → **BUILD SUCCESSFUL in 9m 54s**
- `testProdDebugUnitTest`: **71 tests, 2 skipped, 0 failures** (Konsist R1 interim + R3 green; the 2
  skips are dormant R2/R4).
- `assembleDevDebug` ✓; `assembleProdRelease` + `minifyProdReleaseWithR8` ✓ — **R8 + Hilt interact
  cleanly** (ADR-015's release-interaction risk, and the Phase-2 Tink déjà vu, retired).

### Commit B — delete Graph.kt + flip Konsist R1
`./gradlew testProdDebugUnitTest detekt assembleDevDebug assembleProdRelease lint` →
**BUILD SUCCESSFUL in 8m 48s**
- `detekt` ✓ (no new findings); `testProdDebugUnitTest`: **71 / 2 skipped / 0 failures** (Konsist R1
  now terminal — "Graph must not exist / is not referenced"; R3 green); `assembleDevDebug` ✓;
  `lint` ✓ (baseline held); `assembleProdRelease` unchanged (UP-TO-DATE from Commit A).
- `grep 'Graph' app/src/main` → references only in comments; `core/Graph.kt` deleted.

## WP5 exit criteria (M1_PLAN §2)

| Criterion | Status |
|---|---|
| Unit tests green (plan said 67; now **71**/2 skip after WP3) | ✅ 71 / 2 skipped / 0 failures |
| Release build clean (R8) | ✅ `assembleProdRelease` + `minifyProdReleaseWithR8` |
| Zero `Graph` references (Konsist-enforced) | ✅ R1 terminal rule green; grep clean; `Graph.kt` gone |
| WP2 harness full pass on the Hilt build (emulator + Moto G) | ⏳ **pending — hardware-gated** (NucBox / Moto G) |

## What was migrated

| Area | Approach |
|---|---|
| `di/AppModule.kt` | `@Provides @Singleton` 1:1 with every `Graph` member; identical construction; `LockSessionManager` + `IntruderPolicy` settings-closures preserved verbatim; `@ApplicationScope` qualifier for the app `CoroutineScope`; the 12 domain/data classes left un-annotated |
| `AppLockApplication` | `@HiltAndroidApp`; startup policy-cache warm-up re-run via injected `LockPolicyManager` |
| Activities / services | `@AndroidEntryPoint` field injection: `MainActivity`, `LockScreenActivity`, `AppDetectionService`, `ProtectionWatchdogService` |
| ViewModels | `@HiltViewModel`: `AppListViewModel`, `VaultViewModel`, `IntruderLogViewModel` + two thin new VMs (`AuthGateViewModel`, `SettingsViewModel`) for the composable-only consumers |
| `BootReceiver` | **`@EntryPoint` + `EntryPointAccessors`** (not `@AndroidEntryPoint`) — see deviation |
| `Graph.kt` + R1 | deleted; Konsist R1 flipped to terminal form |

## Deviations from the plan / ADR-015 (implementation reality)

- **`BootReceiver`:** a `BroadcastReceiver.onReceive` overrides an abstract member, so the
  `super.onReceive()` that Hilt's `@AndroidEntryPoint` member injection requires **does not compile**
  (`Abstract member cannot be accessed directly`). Switched to a Hilt `@EntryPoint` +
  `EntryPointAccessors` — the official pattern for this case. (ADR-015 had earmarked entry-point
  accessors for the a11y service; `AppDetectionService` instead worked with plain field injection.)
- **`UninstallProtectionReceiver`:** uses no DI — left un-annotated (M1_PLAN had listed it).

## Build-hygiene changes (part of this WP)

- **Packaging exclude** `/META-INF/versions/9/OSGI-INF/MANIFEST.MF`: Dagger 2.56 pulls in
  `org.jspecify:jspecify`, whose multi-release-JAR OSGi manifest collides with the same path in
  `bcprov-jdk18on`. Excluded (build metadata, absent from the runtime APK).
- **detekt baseline (`config/detekt/baseline.xml`) — re-keyed 8 pre-existing entries** whose
  signatures shifted due to unavoidable edits: 7 composable `FunctionNaming`/`LongMethod`/
  `CyclomaticComplexMethod` entries (`= viewModel()` → `= hiltViewModel()`) and 1
  `LockScreenActivity` `Wrapping` entry (`Graph.lockEngine` → `lockEngine`). Same debt, re-keyed —
  no finding was newly silenced, and the baseline was **not** regenerated. The two genuinely-new
  `LongParameterList` findings on the DI-provider functions were accepted visibly via `@Suppress`
  at the call site (mirroring the already-baselined `ApplicationLockEngine` constructor).

## Open / next

- **Device gating-regression harness** (OV-3/OV-4/F3 + smoke) on the Hilt build is the remaining WP5
  exit criterion and ADR-015's closure gate — hardware-gated (NucBox emulator / Moto G), consistent
  with how WP2 and R-002 handled hardware gating. Run before WP6 begins.
- RTM batch (FR-358 etc.) lands at WP8; ADR-015 formally closes at the WP8 gate.
- Wall-clock (≈9 min/gate) is specific to this 2-core 2012 host; CI and the NucBox are faster.
