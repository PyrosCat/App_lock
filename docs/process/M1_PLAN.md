# M1 Plan — Foundation Retrofit (= Implementation Strategy Phase 0)

Migration phase M1 of `MIGRATION_ASSESSMENT.md` §12. Baseline: `f7ffe29` (M0 committed).

**Objective:** retrofit the engineering foundation the new baseline mandates — CI, static
analysis, build variants, Hilt DI, package layering, migration safety — **without changing any
validated security behavior**. The phase's governing rule: *freeze first, then refactor.* Safety
nets (WP1–WP2) land before anything that touches the proven lock engine.

**Exit = IS Phase 0 gate:** successful automated builds, static analysis, and dependency audit
in CI; zero service-locator lookups; the device gating-regression suite passing on the migrated
build; documentation synchronized (RTM/ADRs/changelog).

---

## 1. Scope

**In:** CI/CD pipeline (GitHub Actions on `PyrosCat/App_lock`); detekt + ktlint + Android lint;
Konsist architecture rules; Dev/QA/Staging/Prod build environments + secure config injection
(FR-226/227); Dependabot + dependency/license inventory (FR-247 partial); Hilt migration
replacing `Graph` (ADR-015); package realignment to the SDS layer layout (ADR-001/011); removal
of `fallbackToDestructiveMigration` with a fail-safe replacement (FR-228, ADR-007); scripted
device regression harness (OV-3/OV-4/F3 + core flows); seed instrumentation tests + Gradle-
managed-device matrix (ADR-014); test-fleet onboarding (Moto G 2023, NucBox G5).

**Out (deliberately):** any feature work; full MVVM/navigation refactor (M3 — M1 only does the
mechanical DI/package work); centralized Security Service facade (M2); **interface extraction for
engine/auth/repos (M2/M3, landing with the services that consume the interfaces — not M1)**;
logging platform (M5 — but a thin logging interface lands here per ADR-008); WorkManager (M5);
kotlinx-serialization (deferred until the M3 configuration service needs it); release
signing/deployment (M6).

> **[2026-08-10]** Interface extraction was listed under M1 in `MIGRATION_ASSESSMENT.md` Phase 12
> (frozen snapshot); that placement is **superseded here** — M1 is mechanical DI/package work only
> (freeze-first), and the SDS interface-first contracts land with the services that consume them
> (Security / Authorization in M2, presentation in M3). Detailed M2/M3 breakdown is deferred to
> those milestones' plans. Authority: living plan over frozen snapshot (GOVERNANCE.md §5.1).

## 2. Work packages (execution order)

### WP1 — CI baseline freeze *(first, before any code change)*
Freeze the current green state so every later step is diffed against it.
- `.github/workflows/ci.yml`: JDK 17 (Temurin), Gradle cache; jobs on push/PR to `main`:
  1. `testDebugUnitTest` (67 tests must pass),
  2. `assembleDebug`,
  3. `assembleRelease` (the R8 smoke that caught the Phase-2 Tink issue),
  4. `lint` (Android lint, baseline file to start).
- `local.properties`/SDK handling: `ANDROID_HOME` from the runner; no secrets required.
- Badge in README.
**Exit check:** two consecutive green runs on unmodified `main`.

### WP2 — Device regression harness *(the security freeze)*
Script the checks that caught F3/F4, so the Hilt/package work can be verified mechanically.
- `scripts/e2e/`: bash/python over adb, parameterized by device serial + applicationId:
  - `ov3_fast_switch` — unlock → switch → return ×10; assert relock each cycle.
  - `ov4_rapid_relaunch` — `am start` ×5 rapid; assert lock screen, no stacking.
  - `f3_self_gate` — unlock self-gate → background → resume from recents; assert SELF_GATE.
  - `smoke_core` — protect Clock → launch → lock appears → PIN unlock → home; vault import
    → export → byte-compare → delete.
- Focus assertions via `dumpsys activity activities | grep mResumedActivity` (no screenshot
  parsing → runs headless); PIN taps at ≥0.8 s spacing per the known recipe; a11y
  delete-then-put rebind built into the reinstall helper.
- **Record a baseline run against the frozen main** (dated report in `docs/reports/campaigns/`,
  naming per `docs/reports/README.md`).
- Onboard the fleet: NucBox G5 as emulator host (faster, can run windowed x86_64 images the
  2012 machine cannot — revisit the x86-only constraint there); Moto G 2023 via adb (also the
  first real-hardware pass of the gating suite — record it).
**Exit check:** all four scripts pass 2/2 consecutive runs on the baseline build on at least
one device.

### WP3 — Static analysis & architecture rules
- detekt (+ `detekt-formatting`/ktlint ruleset) with a generated baseline — new findings fail
  CI, legacy findings burn down opportunistically.
- **Konsist** architecture tests (JVM test source set — runs in the existing unit-test job):
  - R1: no `Graph.` references outside `core/Graph.kt` and `di/` *(activates the ADR-015
    interim rule now; flips to "Graph must not exist" at WP5 close)*.
  - R2: layer dependency direction per ADR-001/011 (staged: dormant until WP6 moves packages,
    then enforced).
  - R3: no DAO/database types referenced from `presentation/` (SDS §14).
  - R4: platform entry points (services/receivers/activities) only in `platform/`/
    `presentation/` (post-WP6).
- Wire both into `ci.yml`.
**Exit check:** CI red on a deliberate rule violation (verify once with a scratch commit),
green otherwise.

### WP4 — Build environments & dependency governance
- Flavor dimension `environment`: **`dev`, `qa`, `staging`, `prod`** (FR-226's four
  environments; D1 below) × existing debug/release:
  - `applicationIdSuffix` `.dev`/`.qa`/`.staging`; **prod keeps `com.applock`** unchanged.
  - `BuildConfig` fields: `ENVIRONMENT`, `BUILD_TIME`, `SCHEMA_VERSION` (FR-234 partial),
    per-env config via flavor-scoped resources/BuildConfig — **no secrets exist yet**, so
    FR-227 lands as the injection *mechanism* (gradle property → BuildConfig, absent-safe)
    plus a documented rule against hard-coding.
  - CI builds `prodRelease` + `devDebug`; harness scripts take the applicationId parameter
    (a11y component string differs per suffix — recipes already parameterized in WP2).
- Dependabot config (`.github/dependabot.yml`, gradle ecosystem, weekly).
- Dependency/license inventory: license-report plugin task, artifact archived by CI
  (FR-247 → `partial`; full security-status tracking is M6).
**Exit check:** all 8 variants assemble; `devDebug` + `prod` install side-by-side on the
emulator; inventory artifact produced in CI.

### WP5 — Hilt migration (ADR-015)
Mechanical replacement of `Graph`, one seam at a time, harness run before and after.
1. Catalog + plugins: Hilt (KSP), version compatible with Kotlin 2.1.0/AGP 8.7.
2. `@HiltAndroidApp` on `AppLockApplication`; `di/` modules mirroring today's `Graph`
   providers 1:1 (all `@Singleton`, same construction expressions — no behavior change).
3. Entry points: `@AndroidEntryPoint` on `MainActivity`, `LockScreenActivity`,
   `AppDetectionService`, `ProtectionWatchdogService`, `BootReceiver`,
   `UninstallProtectionReceiver`; ViewModels → `@HiltViewModel` with constructor injection
   (`AppListViewModel`, `VaultViewModel`, `IntruderLogViewModel`).
4. Composable `Graph.*` call sites (SelfGate PIN verify, lockout countdown, etc.): inject via
   the host activity or a minimal `@HiltViewModel` — **no restructuring beyond the lookup
   swap**; the real MVVM refactor stays in M3.
5. Delete `core/Graph.kt`; flip Konsist R1 to prohibit its existence.
- R8 watch: Hilt consumer rules usually suffice; CI `assembleRelease` is the tripwire.
**Exit check:** 67 unit tests green; WP2 harness full pass on the Hilt build (emulator +
Moto G); release build clean; zero `Graph` references (Konsist-enforced).

### WP6 — Package realignment (ADR-001/011)
Mechanical `git mv` + import updates into the target layout; **no logic edits in the same
commits** (reviewability + regression isolation).

| Current | Target |
|---|---|
| `applocker/session/`, `applocker/policy/`, `privacy/IntruderPolicy`, `vault/VaultFileTypes` | `domain/` (pure logic) |
| `applocker/engine/ApplicationLockEngine`, `privacy/IntruderCaptureManager` | `service/` (application services) |
| `core/database/`, `vault/VaultRepository`, `core/SettingsRepository` | `data/` |
| `core/security/*` | `security/` |
| `applocker/service/*`, `admin/*` | `platform/` ⚠ see FQCN pinning |
| `ui/*`, `authentication/ui/*`, `vault/ui/*`, `privacy/ui/*` | `presentation/<feature>/` |
| Hilt modules | `di/` |

- **⚠ FQCN pinning (D3):** `AppDetectionService` and `UninstallProtectionReceiver` keep their
  original fully-qualified names. The enabled-accessibility-services setting and active
  device-admin registration store component names — renaming them breaks protection for every
  existing install on upgrade (silent a11y unbind / stranded device admin). Documented as a
  permanent constraint in the ADR-011 record; all other classes move freely
  (`BootReceiver`/`ProtectionWatchdogService`/activities are re-registered by manifest or our
  own code and may move).
- Manifest updated for every moved component; unit-test packages move in lockstep.
- Konsist R2/R4 flip from dormant to enforced.
**Exit check:** tests + harness green post-move; Konsist layer rules active; upgrade-install
over a WP5 build on the emulator keeps a11y bound and data intact.

### WP7 — Database migration hardening (FR-163/164, FR-228/229, ADR-007/012)
Two independently-reviewable fixes to `AppLockDatabase`, **one commit each** (regression isolation):
- **(a) Destructive-fallback removal (R-004).** Remove `fallbackToDestructiveMigration()`; replace
  with an explicit fail-safe open path (mirroring the existing plaintext-migration failure
  handling): on `openHelper` failure → move DB files aside as `.recovery-<ts>.bak` (data preserved,
  FR-228 "recovery"), start fresh, raise a persistent notification, log a security event. Never
  crash-loop the accessibility service; never silently wipe. Startup `PRAGMA quick_check` after
  open (FR-229 seed; full integrity framework is M2+).
- **(b) Atomic legacy conversion (R-006 / review CR-003).** Make the plaintext→encrypted import
  atomic: **retain the plaintext source** (as `.bak`) until the encrypted import is committed and
  row-count/schema-verified; idempotent restart at each stage; remove the source only after
  validation. Closes the interruption window where the in-memory snapshot is the only copy.
- Tests: JVM tests for the fail-safe decision logic; on-device drills — (a) upgrade-install with a
  deliberately future schema version → fail-safe path engages, `.bak` present, app usable;
  (b) interrupted legacy conversion (kill/throw between snapshot and committed import) → data
  recovers from the retained source, no loss.
**Exit check:** both deliberate-failure drills pass; normal upgrade (v2→v2) and a clean legacy
conversion untouched; RTM FR-228 → `implemented`, FR-229 → `partial`, with R-004 **and** R-006
closure evidence cited.

### WP8 — Instrumentation seed & managed-device matrix (ADR-014), close-out
- `app/src/androidTest/`: smoke suite — app launches to PIN setup; DB opens encrypted
  (header ≠ SQLite magic); Argon2 hash/verify round-trip on device; lock-screen activity
  launches with extras.
- Gradle managed devices: start with **API 30 + 35** in CI (D4); attempt 26/29/33 and keep
  whichever images run reliably on Actions runners (KVM enabled); the full matrix also runs
  locally on the NucBox. Moto G (API 33+) covers real-hardware manually via the WP2 scripts.
- Close-out: final full harness run; RTM batch update (see §5); changelog entry; ADR-015
  closed; **IS Phase-0 gate review recorded** in `docs/reports/gates/` (scope/exit-criteria
  checklist per Implementation Strategy §5).
**Exit check:** all §Exit items in the M1 gate record checked, with evidence links.

## 3. Risks

| Risk | Mitigation |
|---|---|
| Hilt/R8 interaction breaks release (Phase-2 déjà vu) | `assembleRelease` in CI from WP1, before Hilt lands; Hilt consumer rules; fix-forward with `-keep` rules as needed |
| Refactor regresses F3/F4 gating semantics | WP2 harness is a hard gate before+after WP5 and WP6; any red = stop |
| A11y/device-admin component rename strands existing installs | D3 FQCN pinning; upgrade-install check in WP6 exit |
| GMD emulator images flaky on CI runners | Matrix is additive (30/35 first); NucBox runs the full matrix locally; documented fallback per ADR-014 |
| Harness timing flakiness on the slow 2012-host emulator | Known recipes (≥0.8 s taps, ≥3.5 s lock-screen wait); NucBox becomes primary harness host once onboarded |
| Hilt version vs Kotlin 2.1.0/KSP pinning | Verify catalog compatibility in WP5 step 1 before committing to versions |
| `applicationIdSuffix` breaks hardcoded `com.applock` assumptions (a11y strings, scripts) | WP2 scripts parameterized from day one; grep-audit for hardcoded package literals in WP4 |

## 4. Decisions flagged for review (pause point)

- **D1 — Environment flavor names:** recommend `dev` / `qa` / `staging` / `prod` (FR-226's
  "Testing" → `qa` to avoid Gradle's `test*` naming collisions), suffixed applicationIds,
  prod = `com.applock`.
  **→ Resolved 2026-08-06: ADR-017 accepted** (prod applicationId recorded as permanently
  fixed — it is the left half of the persisted a11y/device-admin component strings).
- **D2 — Konsist** as the architecture-rule mechanism (new test-only dependency) — recommended
  over detekt-only, because layer rules then live as ordinary JVM tests; requires ADR-016.
  **→ Resolved 2026-08-06: ADR-016 accepted** (execution in WP3; rules R1–R4 staged across
  WP3/WP5/WP6). WP3 is now unblocked.
- **D3 — FQCN pinning** for `AppDetectionService` + `UninstallProtectionReceiver` (recommend
  pin permanently, no shim classes; recorded in ADR-011/013).
  **→ Resolved 2026-08-06: ADR-018 accepted** — recorded as a *new* ADR rather than in
  ADR-011/013 as this line anticipated: ADR-013 is now Superseded (013A), and GOVERNANCE
  §2.2/§2.3 route a discovered binding constraint to its own record; ADR-011 carries a
  cross-link. Exact FQCNs verified against the manifest (`.applocker.admin.`, not `admin/`).
- **D4 — CI device matrix initial scope:** recommend API 30+35 on Actions, full 26/29/33/35 on
  the NucBox, expanding CI as images prove stable (ADR-014 note updated accordingly).
  **→ Resolved 2026-08-06: dated implementation note appended to ADR-014** (no new ADR —
  the matrix commitment already lives in ADR-014; only the execution split was open).

## 5. RTM impact (rows updated at close-out)

FR-226 `implemented` · FR-227 `partial` (mechanism, no secrets yet) · FR-228 `implemented` ·
FR-229 `partial` · FR-234 `partial` · FR-247 `partial` · FR-355/356 `partial` (Dependabot) ·
FR-357 `partial` · FR-358 `implemented` (static analysis in CI) · FR-361 `implemented` ·
FR-363 `implemented` (automated testing integration) · NFR-COMP-001 `partial` (matrix per D4)
· NFR-TEST-002 `partial` · NFR-MNT-003 `partial` · plus `review`-burndown for FR-026..080 at
the gate review (per RTM.md rule 2).

## 6. Effort & sequencing

Assessment estimate: **3–4 solo-dev weeks.** Suggested session cut: WP1+WP3 (1 session),
WP2 (1–2, includes fleet onboarding), WP4 (1), WP5 (1–2, the careful one), WP6 (1),
WP7 (1), WP8 + gate record (1). WP1→WP2→(WP3/WP4 parallelizable)→WP5→WP6→WP7→WP8 strictly in
that order for the safety-net dependencies; nothing after WP2 proceeds while the harness is red.
