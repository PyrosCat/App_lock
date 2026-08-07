# ADR-017 — Build Environment Flavors: dev / qa / staging / prod with Suffixed Application IDs

**Status:** Accepted — execution in M1 (WP4) · **Date:** 2026-08-06 · **Source:** M1_PLAN §2 (WP4) + §4 (D1) pause-point recommendation, adopted 2026-08-06; FR-226/FR-227

## Context
FR-226 mandates four build environments (Development, Testing, Staging, Production) with environment-specific configuration; FR-227 requires secure configuration injection. The project has a single build identity today (`com.applock`, debug/release only). Gradle reserves `test`-prefixed naming: a flavor literally named `test` is illegal in AGP, and "testing" collides confusingly with generated test task/source-set names (`testTestingDebugUnitTest`…). The environment identity also feeds component-identity strings that Android persists externally — the accessibility grant is stored as `<applicationId>/<FQCN>` (see ADR-018 for the FQCN half).

## Decision
- Flavor dimension **`environment`** with flavors **`dev` / `qa` / `staging` / `prod`** — FR-226's "Testing" environment maps to **`qa`** to avoid the Gradle `test*` naming hazard. Crossed with the existing debug/release build types → 8 variants.
- `applicationIdSuffix` **`.dev` / `.qa` / `.staging`**; **`prod` keeps `com.applock` unchanged, permanently**. The production applicationId is a fixed identity: it is the upgrade/install identity, the (future) Play listing identity, and the left half of the externally persisted accessibility and device-admin component strings. Changing it is equivalent to shipping a different app.
- `BuildConfig` fields per flavor: `ENVIRONMENT`, `BUILD_TIME`, `SCHEMA_VERSION` (FR-234 partial).
- FR-227 lands as the injection **mechanism**: Gradle property → `BuildConfig`, absent-safe (no secrets exist yet), plus a documented rule against hard-coding secrets.
- CI builds `prodRelease` + `devDebug`; the WP2 harness takes applicationId as a parameter (already parameterized — the accessibility component string differs per suffix).

## Alternatives considered
- **FR-226's literal environment names** (incl. "Testing") — rejected: Gradle `test*` task/source-set collisions; `qa` is the conventional safe rename.
- **Build types as environments** (adding more build types instead of flavors) — rejected: environment is orthogonal to debug/release; conflating them halves the matrix and breaks the standard debug-signing/minification semantics.
- **Suffixing prod too** (e.g. `.prod`) — rejected: changes the production applicationId, breaking upgrade identity for every existing install and invalidating the persisted component strings.

## Consequences
- 8 assembleable variants; non-prod flavors install side-by-side with prod (distinct applicationIds) — useful for on-device comparison, and exercised by the WP4 exit check.
- Per-flavor accessibility component strings mean device-side tooling must always be applicationId-parameterized (WP2 scripts already are).
- Negative: variant matrix growth adds CI surface; mitigated by building only `prodRelease` + `devDebug` in CI.
- The prod-applicationId-is-permanent constraint binds all future work (cross-linked from ADR-018, which pins the FQCN half of the same persisted strings).

## Related requirements
FR-226 (→ `implemented` at WP4 close), FR-227 (→ `partial`: mechanism only), FR-234 (→ `partial`); related ADRs: ADR-018, ADR-016 (rules run per-variant in the unit-test job).

## Implementation

**2026-08-07 (M1 / WP4) — executed.** `app/build.gradle.kts` declares the `environment` flavor
dimension (`dev` / `qa` / `staging` / `prod`) × debug/release = 8 assembleable variants. Non-prod
flavors carry `applicationIdSuffix` `.dev` / `.qa` / `.staging` (+ matching `versionNameSuffix`
for human-facing build identity); `prod` is `isDefault` with **no** suffix — applicationId stays
`com.applock`. `buildFeatures.buildConfig = true` enables the fields: `ENVIRONMENT` (per flavor),
`BUILD_TIME` (Gradle property `buildTime` → `BuildConfig`, absent-safe, defaults to `"unknown"` —
this is the FR-227 injection *mechanism*; no secrets exist yet and a documented no-hard-coding
rule sits at the injection site), and `SCHEMA_VERSION` (`2`, mirrors the Room schema in
`AppLockDatabase`; FR-234 partial). CI builds `devDebug` + `prodRelease` and injects the real UTC
`buildTime`. All 8 variants assemble locally (WP4 exit check — evidence in
`docs/reports/campaigns/2026-08-07_wp4-build-variants_2012-i7.md`). RTM rows land at the WP8
close-out batch (M1_PLAN §5), not in this work package. This note amends implementation status
only; the decision content above is unchanged (GOVERNANCE §2.3).
