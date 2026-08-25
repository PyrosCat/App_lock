# WP8 Instrumentation Smoke — GMD `full` Matrix (NucBox G5, x86_64 API 26/29/30/33/35)

- **Date / captured:** 2026-08-17 (run 2026-08-17T05:18–05:33Z; ~01:18–01:33 local −0400).
- **Author / host:** NucBox G5 — GMKtec, Windows 11 Pro, Intel N-series **x86_64**, WHPX. On-host assistant.
- **Against:** `origin/main = a66701c` (WP8 authoring `d255de0`; runbook `docs/testing/WP8_GMD_MATRIX.md` §5). Clean tree.
- **Variant:** `prodDebug` androidTest (`applicationId com.applock`), stock `AndroidJUnitRunner` on the real `@HiltAndroidApp` graph; debug so FLAG_SECURE is off and the UI is inspectable.
- **Devices:** Gradle-managed (GMD auto-provisions; distinct from the `matrix_*` AVDs). All x86_64:

  | GMD device | Profile | Image (`systemImageSource`) |
  |---|---|---|
  | `api26` | Pixel 2 | aosp — `default/x86_64` |
  | `api29` | Pixel 2 | aosp — `default/x86_64` |
  | `api30` | Pixel 5 | aosp-atd — `aosp_atd/x86_64` |
  | `api33` | Pixel 5 | aosp-atd — `aosp_atd/x86_64` |
  | `api35` | Pixel 5 | aosp — `default/x86_64` |

- **Verifies:** the WP8 instrumentation smoke suite across the full x86_64 GMD matrix — the emulator half of **NFR-COMP-001** (ADR-014 D4 matrix). Touches FR-162/FR-164 (encryption at rest), FR-011/FR-023 (Argon2id), and the PIN-setup / lock-screen launch contracts. Complements the arm64 real-hardware leg (`2026-08-16_wp8-smoke_moto-g-2025.md`).
- **Command:** `./gradlew fullGroupProdDebugAndroidTest -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1 --continue --stacktrace` → **BUILD SUCCESSFUL in 21m 42s** (incl. cold download of all five system images).

## Result — 20 / 20 PASS (0 failures, 0 errors, 0 skipped)

Every level ran all four `com.applock.smoke` tests green. Per-level testsuite totals (from `app/build/outputs/androidTest-results/managedDevice/debug/flavors/prod/*/TEST-*.xml`):

| Level | tests | failures | errors | skipped | suite time |
|---|---|---|---|---|---|
| api26 | 4 | 0 | 0 | 0 | 10.5 s |
| api29 | 4 | 0 | 0 | 0 | 67.0 s |
| api30 | 4 | 0 | 0 | 0 | 27.0 s |
| api33 | 4 | 0 | 0 | 0 | 7.9 s |
| api35 | 4 | 0 | 0 | 0 | 19.6 s |

No `<failure>`/`<error>` element appears in any of the five result XMLs. HTML report (local artifact, not committed): `app/build/reports/androidTests/managedDevice/debug/flavors/prod/allDevices/index.html`.

### The four tests (identical set per level)

| Test (`com.applock.smoke.*`) | Proves |
|---|---|
| `PinSetupLaunchTest.freshInstallLandsOnPinSetup` | Fresh install (credentials cleared) launches `MainActivity` straight to PIN setup — real Hilt graph builds, Compose inflates. |
| `EncryptedDatabaseTest.databaseHeaderIsNotPlaintextSqliteMagic` | `AppLockDatabase.build()` produces a file whose 16-byte header is **not** the SQLite plaintext magic → SQLCipher at rest (FR-162/FR-164). |
| `Argon2OnDeviceTest.productionCostHashRoundTripsAndRejectsWrongPin` | Argon2id at production cost (19 MiB / t=2) hashes + verifies on a real ART heap; wrong PIN rejected (FR-011/FR-023). |
| `LockScreenLaunchTest.lockScreenWithExtraStaysResumedAndShowsPinPrompt` | `LockScreenActivity.createIntent(...)` stays RESUMED showing the PIN prompt — the EXTRA_TARGET_PACKAGE launch contract. |

## API 29 Argon2 heap decision (§5.2) — RESOLVED: passed natively, no workaround needed

The §5.2 concern was that API 29 might OOM `Argon2OnDeviceTest` (19 MiB Argon2 vs a ~16 MiB heap cap). **It did not.** On GMD's **`aosp` (default) x86_64 API 29** image, api29 ran **4/4 including Argon2** (suite 67.0 s — slower than the others, but a clean pass, no `OutOfMemoryError`).

The earlier heap cap (`2026-08-09_wp5-matrix_nucbox-g5.md` §3) was specific to the **`google_apis;x86_64` `matrix_api29` AVD**, where a Zygote-init race left `dalvik.vm.heapgrowthlimit` unset (~16 MiB). The GMD `aosp` image used here carries a normal heap, so the race does not surface. **Decision recorded: api29 stays in the `full` group; the Argon2 case is covered natively — neither §5.2 option 1 (manual heap workaround) nor option 2 (drop api29) was necessary.**

## CI `ci` group (API 30+35) — green (supplementary)

The GitHub Actions `instrumentation` job (`ciGroupProdDebugAndroidTest`, `continue-on-error`, push-to-`main`) reports conclusion **`success`** on the latest `main` run (`a66701c`, run id 31989135009; the `build` job is `success` too). Because the job is `continue-on-error`, a job-level `success` does not by itself distinguish "tests passed" from "tests failed but the error was continued" — but it does not need to: the NucBox `full` run here is the authoritative matrix and a **superset** of CI's API 30+35, both green above. The CI leg is confirmatory only.

## Scope & caveats

- **All x86_64.** The arm64 / native-`libsqlcipher.so` leg is the Moto G 2025 real-hardware smoke (`2026-08-16_wp8-smoke_moto-g-2025.md`, 4/4). Together they are the WP8 smoke evidence across both ABIs.
- **Smoke, not gating.** These four tests do not exercise the lock engine; the WP2 gating harness (OV-3/OV-4/F3, green at the WP5/WP6 device exits) remains the gating-regression evidence.
- The DEX space-in-method-name fix (`c4565c0`, minSdk 26 → DEX < 040) was already applied before this run; no dex-time failure occurred here.
- Hermeticity: the suite clears `applock_credentials` / `applock_lockout` shared-prefs, so it must run on a test emulator/device (as here), not one holding real user state.
- This report is one input to the **M1 IS Phase-0 gate**; with the Moto G leg it closes **NFR-COMP-001** against the executed matrix (ADR-014 D4).

## Supersession

x86_64 GMD smoke-matrix evidence for the WP8 suite on this host. Immutable; a later change to the smoke suite or the managed-device set re-runs the matrix and files a new dated campaign report.
