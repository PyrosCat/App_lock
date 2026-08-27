# WP4 Exit Check — Build Environments & Dependency Governance

- **Date:** 2026-08-07
- **Author / host:** 2012 i7 dev box, driving the Moto G 2025 over USB adb.
- **Produced against:** baseline `c838224` (M1/WP3) + the WP4 change set (this commit, pre-push)
- **Verifies:** M1_PLAN §2 (WP4) exit check; ADR-017; FR-226 / FR-227 / FR-234 (mechanism) /
  FR-247 (inventory). RTM rows are **not** updated here — they land at the WP8 close-out batch
  (M1_PLAN §5).

## Result — PASS

`BUILD SUCCESSFUL in 32m 16s` (360 actionable tasks; 307 executed, 53 cached) for:

```
./gradlew testProdDebugUnitTest detekt assembleDebug assembleRelease lint \
          generateLicenseReport -PbuildTime=2026-08-07T18:00:00Z --console=plain --stacktrace
```

`assembleDebug` + `assembleRelease` expand across the new flavor dimension to all 8 variants;
a fixed `-PbuildTime` was passed to demonstrate deterministic injection (CI passes the real UTC
time). No FAILED / error / exception markers in the log.

## Exit-check criteria

| Criterion (M1_PLAN §2 WP4) | Evidence | Status |
|---|---|---|
| All 8 variants assemble | `assembleDevDebug`, `assembleProdDebug`, `assembleQaDebug`, `assembleStagingDebug`, `assembleDevRelease`, `assembleProdRelease`, `assembleQaRelease`, `assembleStagingRelease` all executed | ✅ |
| `devDebug` + `prod` install side-by-side | distinct applicationIds (below) ⇒ co-installable by construction | ✅ (device install is device-gated; distinct package identity is the guarantee) |
| Inventory artifact produced in CI | `generateLicenseReport` → `app/build/reports/dependency-license/index.html` (95 KB); CI uploads it as `license-inventory` | ✅ |

## Variant matrix (from each variant's `output-metadata.json`)

| Variant | applicationId | versionName |
|---|---|---|
| devDebug / devRelease | `com.applock.dev` | `0.1.0-dev` |
| qaDebug / qaRelease | `com.applock.qa` | `0.1.0-qa` |
| stagingDebug / stagingRelease | `com.applock.staging` | `0.1.0-staging` |
| prodDebug / prodRelease | `com.applock` | `0.1.0` |

`prod` is unsuffixed and `isDefault` — the permanent upgrade / Play identity and the left half of
the externally-persisted accessibility & device-admin component strings (ADR-017 + ADR-018).

## BuildConfig fields (generated sources)

| Field | prodRelease | devDebug |
|---|---|---|
| `APPLICATION_ID` | `com.applock` | `com.applock.dev` |
| `ENVIRONMENT` | `prod` | `dev` |
| `SCHEMA_VERSION` | `2` | `2` |
| `BUILD_TIME` | `2026-08-07T18:00:00Z` | `2026-08-07T18:00:00Z` |

`ENVIRONMENT` correctly varies per flavor; `SCHEMA_VERSION` mirrors the Room schema in
`AppLockDatabase` (v2); `BUILD_TIME` flowed from the Gradle property (absent-safe → `"unknown"`
when unset) — this is the FR-227 secure-config **injection mechanism** (no secrets exist yet).

## Regression evidence (behavior unchanged)

`testProdDebugUnitTest`: **71 tests, 2 skipped, 0 failures, 0 errors** (8 suite files). The 2 skips
are the dormant Konsist R2/R4 (activate at WP6); R1 + R3 ran green. This green run is the cited
evidence that the additive build-graph change touched no verified-requirement behavior
(GOVERNANCE RTM rule — no `invalidated` downgrade warranted). detekt and lint passed against their
frozen baselines (no regeneration; both scan variant-agnostic `src/main`).

## Notes

- **Harness needs no change:** `scripts/e2e/lib.sh` already derives `MAIN_ACTIVITY` and
  `A11Y_COMPONENT` from `${APP_ID}` (default `com.applock`), with a standing comment that WP4 adds
  the `.dev`/`.qa`/… suffixes. Running the WP2 suite against a non-prod flavor is
  `APP_ID=com.applock.dev ./scripts/e2e/run_all.sh …`.
- **CI scope:** builds only `devDebug` + `prodRelease` (bounds CI time; `prodRelease` is the R8
  tripwire + shipping identity). All 8 assemble locally, as above.
- Stale top-level `app/build/outputs/apk/{debug,release}/` dirs are pre-flavor leftovers (no
  `clean` was run); the authoritative WP4 outputs are the eight `apk/<flavor>/<type>/` dirs.
- **Wall-clock caveat:** 32m on this 2-core 2012 host is host-specific; the NucBox and CI runners
  are materially faster.
