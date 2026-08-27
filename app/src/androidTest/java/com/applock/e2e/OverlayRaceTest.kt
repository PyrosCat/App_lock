package com.applock.e2e

/**
 * Marks the heavy OV-4 overlay-race burst test so lanes can select it independently of the fast
 * smoke suite (a test-tiering convention — build/CI config, not an ADR: GOVERNANCE §2.7).
 *
 * - **Per-push CI (`ci` GMD group)** excludes it (`notAnnotation`) to stay fast — see
 *   `.github/workflows/ci.yml`.
 * - **`full` GMD sweep (NucBox) + Moto G `connectedProdDebugAndroidTest`** run the whole suite,
 *   so OV-4 is included there — the api-level sweep and real-hardware no-regression lanes.
 * - **Firebase Test Lab** selects it alone via `--test-targets "class …OverlayRaceUiTest"`
 *   (scripts/ftl/) — the multi-OEM lane. (`testInstrumentationRunnerArguments` are applied by
 *   Gradle tasks, not baked into the APK, so they do not reach FTL — FTL is governed only by
 *   `--test-targets`.)
 *
 * M7_PLAN.md §10 (lanes) / §11 (protocol) / canonical R-002 standard.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class OverlayRaceTest
