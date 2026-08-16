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
  real-API-35 coverage of the WP8 suite.
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

### 7.2 RTM batch (`docs/process/rtm/rtm.csv`) — the M1_PLAN §5 rows
Apply at the gate (evidence = the §7.1 report and CI):
> FR-226 `implemented` · FR-227 `partial` · FR-228 `implemented`(already `implemented-verified` since
> WP7 — leave it) · FR-229 `partial` · FR-234 `partial` · FR-247 `partial` · FR-355/356 `partial` ·
> FR-357 `partial` · FR-358 `implemented` · FR-361 `implemented` · FR-363 `implemented` ·
> NFR-COMP-001 `partial` (matrix per D4) · NFR-TEST-002 `partial` · NFR-MNT-003 `partial`
> · plus the `review`→ resolved burndown for FR-026..080 at the gate (per the RTM.md rule).

Any row whose *implementation was already in place from an earlier WP* keeps its state; the gate
verifies, it doesn't re-open. `implemented-verified` needs an evidence pointer — use the §7.1 report
(or the relevant CI run) for the rows the matrix actually exercised.

### 7.3 ADR closures
- **ADR-015 (Hilt):** its only open exit criterion was "the device gating-regression suite runs on
  the Hilt build" — **already satisfied** by the closed WP5 device gate
  (`2026-08-08_wp5-harness_moto-g-2025.md` + `2026-08-09_wp5-matrix_nucbox-g5.md`, OV-3/OV-4/F3 on
  the Hilt build). Amend the ADR-015 **status line** to "Accepted — implemented (WP5) and closed at
  the M1 gate (YYYY-MM-DD)", citing those reports + the M1 gate record. (Status/implementation-line
  amendments are permitted; the decision content is unchanged.)
- **ADR-014 (API range):** append a dated implementation note that **NFR-COMP-001 is closed** against
  the executed matrix (CI `ci` group + NucBox `full`), citing §7.1.

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
- **No custom test runner / Hilt-test app** is used: the tests launch the real activities on the
  real `@HiltAndroidApp` graph. If a future test needs to *replace* a module, that's when
  `HiltTestApplication` + a custom runner come in — out of scope for this seed.
- The tests clear `applock_credentials` / `applock_lockout` shared-prefs for hermeticity, so run them
  on a test emulator/device, not one holding real user state.
- The shipping engine is still accessibility-based (pre-M7); it is irrelevant to these smoke tests.
- If `createEmptyComposeRule` + `ActivityScenario` ever flakes on the slow api26 image (Compose idle
  timing), re-run that single class; note any persistent flake in the report rather than muting it.
```
