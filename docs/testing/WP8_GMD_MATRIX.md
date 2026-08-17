# WP8 GMD Smoke Matrix + M1 Gate — On-Device Runbook (NucBox handoff)

**For:** the NucBox G5 emulator host (the fleet's device-verification machine), and whoever runs
the M1 exit gate.
**From:** the 2012-box session, 2026-08-16. Shared via git (the only cross-machine channel).
**Status of the work this verifies:** code + CI + this runbook complete, local gate green; the
device matrix and the M1 gate record are what remain. Baseline before this change set:
`origin/main = 13469e9`.

---

## 1. Context — what already landed (the WP8 authoring commit)

WP8 is the **last M1 package**. This change set adds the on-device test infrastructure; running it
across the matrix and recording the IS Phase-0 gate is the remaining, device-and-governance work.

| Area | What landed |
|---|---|
| `app/src/androidTest/java/com/applock/smoke/` | Four instrumentation smoke tests (§3). |
| `app/build.gradle.kts` | `testInstrumentationRunner` (stock `AndroidJUnitRunner` — the real `@HiltAndroidApp` app backs the tests); a `testOptions.managedDevices` block defining devices `api26/29/30/33/35` and groups `ci` (30+35) and `full` (all five). |
| `gradle/libs.versions.toml` | androidx.test + Compose ui-test dependencies. |
| `.github/workflows/ci.yml` | New `instrumentation` job: `ciGroupProdDebugAndroidTest` on API 30+35, KVM-accelerated, `continue-on-error` + not on the PR path (first GMD wiring — non-blocking until proven, see §4). |

**Local gate green on the 2012 box** (against this change set): `compileProdDebugAndroidTestKotlin`
(the suite compiles + the GMD DSL configures), `testProdDebugUnitTest`, `detekt`, `lint` (incl.
`lintAnalyzeProdDebugAndroidTest`). `assembleProdRelease` is unaffected — WP8 adds only
`androidTestImplementation` / `debugImplementation` entries, nothing on the release runtime
classpath. **What only a device can prove is below.**

## 2. Exit obligations — what the matrix + gate close

- The **CI `ci` group (API 30+35)** goes green on the Actions runners (§4).
- The **NucBox `full` group (API 26/29/30/33/35)** runs, with the API-29 caveat in §5.2 decided and
  recorded → **NFR-COMP-001** closes against the executed matrix (ADR-014 D4 split).
- The **Moto G 2025 real-hardware smoke pass** (§6) is green — the only arm64 / native-SQLCipher /
  real-API-35 coverage of the WP8 suite. **[DONE 2026-08-16 — 4/4, see §6.]**
- The **M1 IS Phase-0 gate record** is written to `docs/reports/gates/` and every exit box is
  checked with an evidence link (§7) → **M1 exits**.
- **ADR-015 (Hilt)** is formally closed (§7.3) and the **RTM §5 batch** is applied (§7.2).

## 3. The four smoke tests (what each proves)

All under `com.applock.smoke`, `@RunWith(AndroidJUnit4)`, on the real Hilt app:

| Test | Proves |
|---|---|
| `PinSetupLaunchTest` | Fresh install (credentials cleared) launches `MainActivity` straight to **PIN setup** — real Hilt graph builds, Compose UI inflates. POST_NOTIFICATIONS granted silently on API 33+. |
| `EncryptedDatabaseTest` | `AppLockDatabase.build()` produces a file whose 16-byte header is **not** the SQLite plaintext magic → SQLCipher encryption at rest (FR-162/FR-164). |
| `Argon2OnDeviceTest` | Argon2id at the **production cost (19 MiB / t=2)** hashes + verifies on a real ART heap — the API-29 heap canary (§5.2). |
| `LockScreenLaunchTest` | `LockScreenActivity.createIntent(...)` (the EXTRA_TARGET_PACKAGE contract) launches and **stays resumed** showing the PIN prompt; lockout counters cleared first. |

## 4. CI matrix (API 30 + 35) — automatic

The `instrumentation` job runs on **push to `main`** and **manual dispatch** (not on PRs). On the
Actions runner it enables KVM, accepts the system-image licence, then runs:

```
./gradlew ciGroupProdDebugAndroidTest \
  -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1 \
  -PbuildTime="<utc>" --stacktrace
```

- **`continue-on-error: true`** — a red run does **not** fail the workflow (first GMD wiring; hosted
  images can be flaky, M1_PLAN §3). Triage from the uploaded `androidTest-reports` artifact.
- **When it is green across a few merges, flip `continue-on-error` off** (and consider adding the PR
  trigger) — same baseline burn-down as WP1 lint / WP3 detekt.
- If a hosted image refuses to resolve, the NucBox `full` run (§5) is the authoritative matrix;
  record the CI image as unavailable in the gate record rather than blocking.

## 5. NucBox full matrix (API 26/29/30/33/35) — the authoritative run

Prereqs: the NucBox boots x86_64 images and runs GMD natively (fleet report
`2026-07-21_fleet-nucbox-g5.md`). No device/adb setup needed — GMD manages the emulators.

### 5.1 Run it

```bash
# one group task drives all five levels sequentially:
./gradlew fullGroupProdDebugAndroidTest \
  -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1 --stacktrace

# or one level at a time (useful to isolate a flake):
./gradlew api26ProdDebugAndroidTest
./gradlew api30ProdDebugAndroidTest      # api29 / api33 / api35 likewise
```

Results: `app/build/reports/androidTests/managedDevice/…` (HTML, per device). GMD auto-downloads
each system image on first use. If a level's image source needs changing, edit the
`systemImageSource` for that `create("apiNN")` in `app/build.gradle.kts` (`aosp` vs `aosp-atd` vs
`google_apis`) and note it in the report.

### 5.2 ⚠ API 29 Argon2 heap decision (the "decide at WP8" point)

Per `2026-08-09_wp5-matrix_nucbox-g5.md`: the API-29 emulator image caps the app heap so tightly
(~16 MiB via a Zygote init race) that the 19 MiB Argon2id allocation OOMs. This is an **emulator
image limitation, not an app defect** — real API-29 hardware has a normal heap. GMD can't inject the
`setprop` fix before a test, so choose and **record** one of:

1. **Keep api29 in `full`; accept the Argon2 case may OOM there.** If `Argon2OnDeviceTest` fails only
   on api29 with `OutOfMemoryError`, confirm the app logic on a **manually booted** api29 AVD with
   the heap raised, then record api29-Argon2 as covered-with-caveat:
   ```bash
   emulator -avd <api29_avd> -no-window -no-audio &
   adb root && adb shell setprop dalvik.vm.heapgrowthlimit 256m && adb shell stop && adb shell start
   ./gradlew connectedProdDebugAndroidTest    # runs against the booted emulator, heap raised
   ```
   (The other three smoke tests pass on api29 GMD regardless — only the 19 MiB Argon2 case is affected.)
2. **Drop api29 from the `full` group** (comment its two lines out) and record API 29 as
   image-limited, deferred to real-hardware coverage.

Recommended: **option 1** — keep the coverage, prove the app logic once with the workaround, and
note the caveat. Either way the gate record must state which was chosen.

## 6. Real hardware (Moto G 2025, arm64 / API 35) — REQUIRED

> **Status: DONE (2026-08-16)** — 4/4 PASS on moto g - 2025 (Android 15, arm64). Evidence:
> `docs/reports/campaigns/2026-08-16_wp8-smoke_moto-g-2025.md`. Recipe kept below for re-runs and any
> other real device.

The emulator matrix (§4–§5) is entirely **x86_64**. The Moto G 2025 is the fleet's only **arm64**
target and only real hardware, so it is the sole place the smoke suite exercises the native
`libsqlcipher.so` on ARM (`EncryptedDatabaseTest`) and a real API-35 device — coverage no emulator
provides. GMD manages emulators only, so the phone runs the same suite via the **connected** test task.

Prereqs (from prior fleet runs): reach the Moto G over USB adb (ADB Interface driver bound; RSA prompt
accepted) **or** wireless debugging (`adb pair`, Android 15 supports it). The smoke suite needs **no
accessibility grant** — none of the four tests use the lock engine (same as the WP7 DB drills) — so
skip the a11y / Restricted-Settings dance entirely; adb reachability is all it needs.

```bash
adb devices                 # confirm the Moto G shows as `device`
./gradlew connectedProdDebugAndroidTest --stacktrace
```

`connectedProdDebugAndroidTest` builds the prodDebug app + androidTest APKs, installs both, and runs
all four `com.applock.smoke` tests on the phone. Results in
`app/build/reports/androidTests/connected/…` (HTML). **All four must pass.** If more than one device
or emulator is attached, set `ANDROID_SERIAL=<serial>` (or detach the others) so it targets the phone
— `connected*` otherwise runs on every attached device.

This is the WP8 instrumentation-smoke pass on real hardware. It is **separate from and additional to**
the WP2 gating harness (`scripts/e2e/run_all.sh`, OV-3/OV-4/F3) already green at the WP5/WP6 device
exits — that stays the real-hardware *gating*-regression evidence; this adds real-hardware *smoke*
evidence (arm64 + native SQLCipher) for the WP8 suite.

## 7. The M1 exit gate (governance close-out — do after §4–§5)

Per GOVERNANCE §3.2/§5.2 and the reports immutability rule, land the evidence and the row/status
changes **together** (the operator runs `git add`/commit — D4). Suggested one gate commit:

### 7.1 Campaign report(s) for the device runs
- Emulator matrix: `docs/reports/campaigns/YYYY-MM-DD_wp8-gmd-matrix_nucbox-g5.md` (per
  `docs/reports/README.md` naming): API levels run, per-test pass/fail, the api29 decision (§5.2), and
  the CI `ci`-group run link/conclusion.
- Real hardware: `docs/reports/campaigns/YYYY-MM-DD_wp8-smoke_moto-g-2025.md` — the §6 connected run
  (host-tagged separately so the fleet machines never merge-conflict): device/Android build, the four
  test results, arm64 + native-SQLCipher note.

### 7.2 RTM batch (`docs/process/rtm/rtm.csv`)

> **⚠ Corrected 2026-08-17 (2012-box).** This section originally reproduced M1_PLAN §5 verbatim —
> which predates the **ADR-019 version split** (RTM re-base, 2026-08-14). Against the *current* RTM +
> **SRS v1.0.0 Appendix A §A.19** (the authoritative descope list, per ADR-019 §5), most of that old
> list is now `descoped-v1`; applying it mechanically would **revert the descoping** and overstate
> v1.0.0 scope. **Do not apply the old list.** M1_PLAN §5 carries the same staleness (dated note added
> there). The reconciled disposition:

**Principle.** Honor ADR-019: descoped rows stay `descoped-v1`. The M1 engineering work is credited to
the **retained NFRs**, not their descoped FR twins — Appendix A §A.18 states the FR forms (FR-351–375,
and the FR-226/227/247 build/config/inventory ones) are reserved and the qualities are carried once by
the NFRs. Verify each row against Appendix A §A.19 before touching it.

**Change (retained rows, real M1 evidence):**
- **NFR-COMP-001** `not-started` → `partial` — WP8 device matrix (the §7.1 Moto G report now; CI `ci` +
  NucBox `full` as they land). Upgrade to `implemented-verified` only once the full matrix is green.
- **NFR-TEST-002** `not-started` → `partial` — WP8 androidTest seed + GMD + CI test integration + the
  Moto G connected run.
- **NFR-MNT-003** `not-started` → `partial` — WP3 detekt/ktlint/Konsist coding-standards enforcement.
- **FR-234** `not-started` → `partial` — WP4 BuildConfig provenance (`SCHEMA_VERSION`/`BUILD_TIME`).
  (NFR-MNT-003 and FR-234 trace to WP3/WP4 evidence — a legitimate gate catch-up, not a WP8 claim.)

**Leave `descoped-v1` (honor ADR-019):** FR-226, FR-227, FR-247, FR-355, FR-356, FR-357, FR-358,
FR-361, FR-363 (reserved per Appendix A §A.13/A.18/A.19). Precedent: ADR-019 §Consequences keeps the
Phase-1..3 Vault/intruder code in-tree while its rows read `descoped-v1` — the CI/build infra is the
identical "capability ahead of the descoped requirement" case.

**Already done (no gate action):** FR-228 `implemented-verified` (WP7), FR-229 `partial` (WP7).

**`review` burndown (FR-026..080):** scope to the **retained** rows only — many of FR-026..080 are in
the Appendix A §A.19 reserved list, so cross-check before classifying, and classify against the
**v1.0.0** meanings (ADR-013B/019 narrowed several). Whether it is mandatory *this* gate or a tracked
follow-up is a governance-rule + gate-lead decision.

`implemented-verified` always needs an evidence pointer (§7.1 report / the relevant CI run).

### 7.3 ADR closures
- **ADR-015 (Hilt):** its only open exit criterion was "the device gating-regression suite runs on
  the Hilt build" — **already satisfied** by the closed WP5 device gate
  (`2026-08-08_wp5-harness_moto-g-2025.md` + `2026-08-09_wp5-matrix_nucbox-g5.md`, OV-3/OV-4/F3 on
  the Hilt build). Amend the ADR-015 **status line** to "Accepted — implemented (WP5) and closed at
  the M1 gate (YYYY-MM-DD)", citing those reports + the M1 gate record. (Status/implementation-line
  amendments are permitted; the decision content is unchanged.)
- **ADR-014 (API range):** append a dated implementation note that **NFR-COMP-001 is now `partial`**
  against the executed legs (Moto G now; CI `ci` + NucBox `full` as they land) — its "closes in M1"
  consequence upgrades to `implemented-verified` only when the full matrix is green. Cite §7.1.

### 7.4 IS Phase-0 gate record — `docs/reports/gates/YYYY-MM-DD_gate-m1.md`
The M1 exit artifact. Skeleton (fill each box with an evidence link):

```markdown
# M1 (IS Phase 0) — Foundation Retrofit — Gate Review

Date: YYYY-MM-DD · Host/author: … · Against commit: <sha>

## Exit criteria (M1_PLAN §Exit / Implementation Strategy §5)
- [ ] Automated builds green in CI …………………… <CI run link>
- [ ] Static analysis (detekt + lint + Konsist) green … <CI run link>
- [ ] Dependency/license audit produced …………… <license-inventory artifact>
- [ ] Zero service-locator lookups (Graph gone; Konsist R1 terminal) … <WP5 evidence / CI>
- [ ] Device gating-regression suite passes on the migrated build … <2026-08-11_wp6-device-exit… + WP5 matrix>
- [ ] On-device smoke matrix (GMD) green … <§7.1 emulator report + CI ci-group run>  (api29 caveat: §5.2)
- [ ] Real-hardware smoke pass (Moto G 2025, arm64 / API 35) green … <§7.1 moto-g report>
- [ ] Docs synchronized: RTM (§7.2), ADR-015/014 (§7.3), changelog (§7.5)

## WP roll-up
WP1 CI freeze · WP2 harness · WP3 static analysis/Konsist · WP4 build variants · WP5 Hilt ·
WP6 package realignment · WP7 DB migration hardening · WP8 instrumentation seed + GMD matrix — all
Closed, with evidence pointers.

## Decision
M1 EXIT: GRANTED / BLOCKED — <name/host>, <date>. Next milestone: M7 (accessibility exit).
```

### 7.5 changelog.txt
A WP8 gate entry (newest-on-top): the matrix result, the api29 decision, ADR-015 closed, M1 exited.

Then **M1 is closed → M7 (the accessibility exit)** is next.

## 8. Notes / caveats

- **Task names:** group tasks are `ciGroupProdDebugAndroidTest` / `fullGroupProdDebugAndroidTest`;
  single-device tasks are `api30ProdDebugAndroidTest` etc. All target the **prodDebug** androidTest
  variant (matches the CI unit-test variant; debug so FLAG_SECURE is off and the UI is inspectable).
- **Validate GMD config locally without an emulator:** `./gradlew ciGroupProdDebugAndroidTest --dry-run`
  builds the task graph — where AGP realizes a task per managed device — but boots nothing, so it
  catches config errors (e.g. an unsupported API level) in seconds. `compile*AndroidTestKotlin` and
  `connected*` do **not** realize GMD device tasks, so they cannot catch these. GMD rejects **API ≤ 26**;
  the api26 floor device needs `android.experimental.testOptions.managedDevices.allowOldApiLevelDevices=true`
  (already in `gradle.properties`). That flag only permits task *creation* — an api26 image that won't
  boot under GMD is the floor caveat (fall back to a manual emulator like the api29 case in §5.2).
- **No custom test runner / Hilt-test app** is used: the tests launch the real activities on the
  real `@HiltAndroidApp` graph. If a future test needs to *replace* a module, that's when
  `HiltTestApplication` + a custom runner come in — out of scope for this seed.
- The tests clear `applock_credentials` / `applock_lockout` shared-prefs for hermeticity, so run them
  on a test emulator/device, not one holding real user state.
- The shipping engine is still accessibility-based (pre-M7); it is irrelevant to these smoke tests.
- If `createEmptyComposeRule` + `ActivityScenario` ever flakes on the slow api26 image (Compose idle
  timing), re-run that single class; note any persistent flake in the report rather than muting it.
```
