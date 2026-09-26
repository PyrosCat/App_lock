# Project Applock dependency and deprecation review

Date and external-source verification date: **2026-09-25**

Inspected commit: **e521026e2838c2bff592022046d6d0571d0e818f** (`main`)

Scope: dependency declarations, available dependency evidence, build configuration, source/API usage, maintenance, compatibility, and proposed remediation. **No application code or dependency versions were changed.**

## 1. Executive summary

- **Complete catalog coverage: 66 entries** — 25 version aliases, 34 library aliases, and seven plugins. Each receives an explicit disposition below. Six library aliases obtain their versions from the Compose BOM.
- **Confirmed build-tool advisory:** Gradle **8.13** is in the affected range of **CVE-2026-22816 / GHSA-w78c-w6vf-rw82**. Qualify **8.14.4** as the narrow 8.x remediation, with repository and dependency-integrity controls. This is a build supply-chain issue; compromise of this project was not demonstrated. [Maintainer advisory][S01]
- **Confirmed compatibility debt:** Kotlin **2.1.0** is paired with Gradle/AGP versions outside Kotlin's published compatibility range. Kotlin, KSP, Compose compiler, Hilt, and their processors need a coordinated qualification lane. A successful historical build does not extend vendor support matrices. [Kotlin matrix][S03]
- **Confirmed deprecated or retired surfaces:** Jetpack Security APIs, `kotlinOptions`, the old Hilt Compose accessor, and the Material icons artifact need different remedies. The license-report plugin's **2.x line is explicitly end-of-life**. [Security][S08], [compiler DSL][S05], [Hilt API][S18], [icons][S20], [license plugin][S29]
- **SQLCipher 4.6.1 warrants an update and applicability review:** the maintainer reports two low-severity issues fixed in 4.19.0. The triggering SQL/URI paths were not found in this app's source. Native packaging and exact-passphrase compatibility still need device qualification. [Zetetic advisory][S24]
- **Do not update everything to latest.** Hilt's Gradle plugin from **2.59** requires AGP 9. Kotlin 2.4 needs newer Android shrinking support. CameraX serves features scheduled for M8 removal; Biometric 1.1.0, JUnit 4.13.2, and Konsist 0.17.3 do not justify automatic replacement. [Dagger][S07], [Android Kotlin support][S04]
- **Fresh resolution is complete for the stated audit scope:** 123 configurations, 478 unique Maven coordinates, 427 external artifact records, and **zero unresolved dependencies or artifact failures**. The production release graph contains 133 coordinates. The earlier failed offline reports are retained as superseded attempts, not current graph evidence.
- **Baseline verification now has recorded results:** 397 JVM tests passed, configured detekt reported zero findings, and a debug APK was built. The continuation completed release assembly, full debug lint, instrumentation Kotlin compilation and license reporting, with cache reuse explicitly recorded (§2.3). Lint reports **40 warnings**, with one error, 13 warnings and one hint filtered by its existing baseline. No device, candidate-upgrade or migration qualification is claimed.
- **A dated OSV Maven advisory lookup is now recorded:** 478 package/version queries yielded 21 matched coordinates and 51 unique advisory records. Only `bcprov-jdk18on:1.78.1` among those matches appears in the production release runtime graph; the others are build/test tooling. A version match does not establish exploitability. Kotlin's KAPT-cache advisory also affects the proposed 2.3.21 candidate's disposition (§7.3); it cannot be called a security fix.
- The **security-crypto replacement document remains a proposal**. Its migration/recovery decisions and qualification gates are outstanding. This review adds dependency and compatibility inputs; it neither implements that proposal nor changes the separate R-007 hardening plan.

## 2. Context, authority, and evidence limits

The project builds one Android app with `dev`, `qa`, `staging`, and `prod` flavors, each with debug/release types. `prod` is the installed-user identity `com.applock`; changing it is not a dependency-migration shortcut. The build uses **minSdk 26, compileSdk 36, targetSdk 36, Java/JVM target 17**, release shrinking, Hilt dependency injection, Room over SQLCipher, Compose UI, and Android Keystore-backed Jetpack Security storage.

M7 replaces the detection/enforcement architecture. R-007 concerns lockout persistence and recovery; F2 is its separately governed hardening work. M8 removes vault/intruder features, while later hardening/release milestones govern production acceptance. This review's work-package numbers are local to this report, not M7 WP numbers. GMD means Gradle-managed emulator devices; the Moto G is the physical arm64 device and the NucBox is an emulator host.

Project authorities read include [GOVERNANCE](../../process/GOVERNANCE.md), [M7_PLAN](../../process/M7_PLAN.md), [ROADMAP](../../process/ROADMAP.md), [RISK_REGISTER](../../process/RISK_REGISTER.md), the [RTM](../../process/rtm/rtm.csv), and the active [v1.0.0 NFR](../../v1.0.0/markdown/Non_Functional_Requirements_v1.0.0.md). **NFR-SEC-011** requires dependency-security disposition; **NFR-MNT-007** requires a maintained inventory and controlled dependencies. The RTM's deferral of FR-247/FR-366 is not evidence that these NFR obligations vanished. The build's comment assigning full CVE tracking to M6 is historical scheduling text, not proof that tracking occurred.

The [2026-09-24 security-crypto replacement proposal](../../process/proposals/2026-09-24_SECURITY_CRYPTO_REPLACEMENT_PLAN.md) was read before assessing Security. Its D1–D13 are review decisions, not accepted implementation evidence. The [2026-09-23 R-007 F2 test plan](../../process/proposals/2026-09-23_R007_F2_HARDENING_TEST_PLAN.md) continues to own recovery-hardening experiments and residual disposition.

### 2.1 Evidence labels and work actually performed

- **D (declared):** version in the inspected catalog/wrapper. This is a request or constraint, not necessarily the selected dependency version.
- **F (fresh selected version):** selected by the completed online audit at the inspected commit, with normal cache reuse. Its configuration, dependency edges, selection reasons and raw external artifact hashes are retained. This does not certify publisher integrity, successful compilation, or packaged reachability.
- **H (historical selected version):** coordinate extracted from the existing release license report. Its file timestamp is 2026-09-05; the generating commit and full invocation were not established.
- **Unverified scope:** candidate updates, omitted internal configurations and native/packaged reachability are separate from the completed baseline resolution. Cached JAR presence or a successful report task alone does not establish them.
- **Confirmed:** directly observed code/configuration or an explicit primary-source statement. **Candidate/hypothesis:** a proposed update or behavior requiring tests. **Not identified:** no match in inspected material, not proof of universal absence.

| Evidence/action | Actual result | Limit / retained record |
|---|---|---|
| Git status, fetch, `rev-list --left-right --count main...origin/main` | Initially clean; fetched refs; `0 0` divergence | Inspection baseline above; not a release certification |
| Catalog, root/app build scripts, settings, wrapper, Gradle properties, CI, Dependabot, lint/ProGuard configuration | Read and inventoried | Static configuration review |
| `rg` scans of production, debug, release, unit, and instrumentation source | Located crypto, Hilt, KTX, UI/test, and compatibility uses | Text/reference review, not a complete compiler/API analysis |
| Initial offline release dependency task | Configuration failed: Android user directory `C:\.android` was not writable | Environmental failure, before dependency qualification; no application test executed |
| `:app:dependencies --configuration prodReleaseRuntimeClasspath` with workspace Android/Gradle homes | Exit 0; **23** lines marked `FAILED` | [release-runtime.txt](2026-09-25_dependency-deprecation/release-runtime.txt); incomplete graph |
| Same task for `prodDebugUnitTestRuntimeClasspath` | Exit 0; **27** lines marked `FAILED` | [unit-runtime.txt](2026-09-25_dependency-deprecation/unit-runtime.txt); incomplete graph |
| Same task for `prodDebugAndroidTestRuntimeClasspath` | Exit 0; **6** lines marked `FAILED` | [androidtest-runtime.txt](2026-09-25_dependency-deprecation/androidtest-runtime.txt); incomplete graph |
| Existing `app/build/reports/dependency-license/index.html` | Extracted 133 group/artifact/version rows | [historical-release-inventory.csv](2026-09-25_dependency-deprecation/historical-release-inventory.csv), each row labelled historical |
| Existing lint text report, timestamp 2026-09-22 | Historical summary includes 0 errors/39 warnings, with baseline-filtered findings | Historical baseline only; fresh execution is recorded separately in §2.3 |
| Online `prodReleaseRuntimeClasspath`, resolvable-configuration discovery and app build environment | Exit 0; no `FAILED` entries | [online-discovery.txt](2026-09-25_dependency-deprecation/online-discovery.txt); app buildscript is empty because plugins resolve in root buildscript |
| Invocation-only resolution audit, all eight product variants plus build/test/processor tooling | Exit 0; 123 configurations, zero unresolved dependencies and external-artifact failures | [summary](2026-09-25_dependency-deprecation/resolution-scan-summary.json), [configuration counts](2026-09-25_dependency-deprecation/configuration-summary.csv), [execution log](2026-09-25_dependency-deprecation/resolution-execution.txt) |
| OSV Maven API lookup on the fresh coordinates | Exit 0; 478 queries, 21 matched coordinates, 51 unique advisory records | [scan results](2026-09-25_dependency-deprecation/osv-scan-results.json), [per-advisory disposition](2026-09-25_dependency-deprecation/advisory-triage.csv); §7.3 distinguishes matches and applicability |
| Official release/API/maintainer documentation | Verified sources in §11 on 2026-09-25 | Live pages may change; dates/versions below are this review's observations |

The historical license HTML SHA-256 is `83CADFED1F7B1671087AB27C940E3B3765EA38F05EFA07EF29594ADAA1555880`. Its timestamp is provenance context, not authenticated build provenance. The CSV preserves its inventory independently of the ignored build directory.

Document coverage and local-link checks are retained in the [validation record](2026-09-25_dependency-deprecation/review-validation.json). They are document checks only; graph, advisory and execution evidence have their own records below.

Executed Gradle work used Android Studio's JBR **21.0.10**, `JAVA_HOME=C:/Program Files/Android/Android Studio/jbr`, workspace `.gradle` as `GRADLE_USER_HOME`, and workspace `.android` as `ANDROID_USER_HOME`. Initial reports used `--offline`; the completed audit used online access, `--no-daemon --console=plain` and normal cache reuse, without `--refresh-dependencies`. CI declares Temurin 17; these observations do not establish JDK-17 parity. No test pass is inferred from `BUILD SUCCESSFUL` in dependency-report tasks.

### 2.2 Fresh graph and scan provenance

The completed audit ran from **2026-09-25T11:29:03.843508500Z to 11:29:40.543014Z**; the OSV lookup ran from **11:30:35.2667137Z to 11:30:53.0040098Z**. [Resolution audit script](2026-09-25_dependency-deprecation/resolution-audit.init.gradle) and [OSV query script](2026-09-25_dependency-deprecation/query-osv.ps1) are invocation-only evidence tools. They do not change catalog versions, production code or dependency constraints. OSV received public Maven package names and versions, not project source, credentials or identity. Its query and pagination protocol is documented by the [OSV API][S49].

| Retained evidence | What it establishes / limit |
|---|---|
| [Complete graph JSON, gzip](2026-09-25_dependency-deprecation/resolved-inventory.json.gz) | Configuration attributes, components, selection reasons, edges, applied plugin classes, raw artifact hashes, failures and timestamps. Decompress as UTF-8 JSON. |
| [478-coordinate inventory](2026-09-25_dependency-deprecation/resolved-components.csv) and [current release inventory](2026-09-25_dependency-deprecation/current-release-inventory.csv) | Exact selected versions, configuration membership, incoming parents, advisory matches and explicit initial dispositions; supersedes reliance on the historical CSV. |
| [427 external artifact records](2026-09-25_dependency-deprecation/artifact-hashes.csv) | SHA-256 and size of observed external files, not independent publisher authentication. Platforms/BOMs and redirected multiplatform metadata do not necessarily have JAR/AAR payloads. |
| [Configuration scope](2026-09-25_dependency-deprecation/configuration-scope.csv) | Explicit inspected/uninspected flags. The audit covers variant compile/runtime, annotation/KSP processor, Kotlin tooling, detekt, UTP and root/app buildscript classpaths; it does not claim every internal Gradle configuration. |
| [Matched components](2026-09-25_dependency-deprecation/matched-components.csv), [51 advisory records, gzip](2026-09-25_dependency-deprecation/osv-advisories.json.gz) | All returned matches and original advisory details/ranges/references, with retrieval dates; initial disposition is reviewable, not risk acceptance or an exploit demonstration. |

Representative counts: release runtime **133 components / 112 external files**; debug runtime **134 / 113**; prodDebug JVM-test runtime **144 / 14**; instrumentation compile **127 / 104** and runtime **87 / 67**; prodDebug KSP processors **37 / 37**; root plugin classpath **158 / 149**. The raw-artifact query excludes local project outputs, including `project :app` and its dependency traversal in test artifact collection; application artifacts are collected in the matching app configuration. The complete component graph still includes that traversal. Therefore the test runtime's 14 external files are not the complete runnable test classpath, nor evidence of 130 failed downloads. Compilation/build output must establish the assembled classpath separately.

The audit's Gradle-9 deprecation footer may include the temporary script's legacy resolution APIs. Attribute a production deprecation only with a diagnostic identifying its source. This is not a clean-cache dependency-integrity campaign, a native SBOM, a remote CI audit, or a full vulnerability/reachability assessment. Missing repository-origin attestations, native-library coverage and GitHub Actions execution SHAs remain explicit WP0 gates; the Maven resolution gap itself is closed.

### 2.3 Completed baseline checks and execution limits

The first substantive baseline run on **2026-09-25** used JBR 21, existing dependency versions and `--rerun-tasks`. It completed the JVM tests, configured detekt and debug APK, then was canceled at the user's pause request, returning exit 1. That cancellation is not a diagnosed build failure. [Partial output](2026-09-25_dependency-deprecation/baseline-paused.txt) and [checkpoint metadata](2026-09-25_dependency-deprecation/baseline-paused-summary.json) remain historical evidence.

The four remaining tasks were subsequently invoked without `--rerun-tasks`. The continuation returned **exit 0, BUILD SUCCESSFUL in 3m 46s**, with **98 actionable tasks: 9 executed, 3 from cache, 86 up-to-date**. The output ended at **2026-09-26T01:45:13Z** (still September 25 in the local review timezone). [Completed log](2026-09-25_dependency-deprecation/baseline-continuation.txt), [metadata](2026-09-25_dependency-deprecation/baseline-summary.json) and both commands in [EVIDENCE.md](2026-09-25_dependency-deprecation/EVIDENCE.md) establish the scope. This is a successful incremental baseline check, not a clean rebuild.

| Check | Cumulative observed result | Evidence / remaining limit |
|---|---|---|
| `testProdDebugUnitTest` | **397 tests, 26 suites; zero failures, errors or skips** | [Suite summary](2026-09-25_dependency-deprecation/unit-test-summary.csv), [anonymized JUnit XML, gzip](2026-09-25_dependency-deprecation/unit-test-results.xml.gz); fresh result timestamps 2026-09-25 |
| `detekt` | Completed; configured report lists zero code smells | [XML](2026-09-25_dependency-deprecation/detekt.xml), [metrics](2026-09-25_dependency-deprecation/detekt.md); not an unfiltered or all-variant analyzer certification |
| `assembleDevDebug` | Completed; fresh debug APK produced | [APK hash](2026-09-25_dependency-deprecation/debug-apk-hash.csv); no device installation or runtime qualification |
| Production release compilation / fatal-only lint | Kotlin/Java/Hilt compilation completed in the earlier run; `lintVitalProdRelease` completed without reported errors/warnings | Earlier log warns that 57 baseline entries were not found under a **different lint variant**. This does not establish those findings were fixed. |
| `assembleProdRelease` | Successful continuation accepted release/R8/package outputs **UP-TO-DATE** | [Unsigned APK hash and provenance](2026-09-25_dependency-deprecation/release-apk-hash.csv); APK timestamp 2026-09-25T18:22:42Z predates the continuation. Its creating invocation is not attributed to this review; no fresh release packaging, signing or app-bundle claim. |
| `lintProdDebug` | Full lint executed: **0 unfiltered errors, 40 warnings**; baseline filtered **1 error, 13 warnings, 1 hint** | [Fresh lint output](2026-09-25_dependency-deprecation/lint-prodDebug.txt); 42 unmatched baseline entries require review, not automatic deletion or a claim of 42 fixes. |
| `compileProdDebugAndroidTestKotlin` | Successful continuation accepted **UP-TO-DATE** output | Establishes Gradle's current-output check, not freshly executed compilation or instrumentation/device execution. |
| `generateLicenseReport` | Executed; **133 coordinates**, no difference from the fresh release graph | [HTML with notices](2026-09-25_dependency-deprecation/license-report.html), [inventory](2026-09-25_dependency-deprecation/fresh-license-inventory.csv); matching coordinates alone do not establish complete legal or native-license review. |

The 40 lint warnings comprise `AndroidGradlePluginVersion` (1), `GradleDependency` (14), `NewerVersionAvailable` (12), `InlinedApi` (1), `ExportedService` (1), `UseKtx` (5) and `SetTextI18n` (6). Version suggestions are inputs to compatibility review, not instructions to take the newest release. The two spike-code diagnostics have explicit dispositions in §7.1. Baseline filtering explains the successful lint task despite retained debt; no baseline was regenerated.

Both earlier native-strip tasks warned about packaging four libraries without stripping (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libsqlcipher.so`, `libsurface_util_jni.so`). Retain this as a tooling/packaging observation; no native load failure or 16 KiB compatibility conclusion follows. No device tests, migration, candidate upgrade or exploit PoC ran. JDK 17 parity, independent clean-cache provenance and release-device qualification remain gates for future changes. The [completed checkpoint](2026-09-25_dependency-deprecation/RESUME.md) supersedes the earlier pause status.

## 3. Compatibility findings and candidate sequence

| Boundary | Current condition | Decision/recommendation |
|---|---|---|
| AGP ↔ wrapper/JDK/API | AGP 8.13.2 specifies Gradle 8.13 minimum/default, JDK 17, API through 36.1. Project API 36 and JVM 17 fit. | **Retain AGP 8.13.2 initially; update wrapper separately** for the advisory, then qualify the complete tuple. Minimum is not a guarantee for every later Gradle. [AGP 8.13][S02] |
| Kotlin ↔ Gradle/AGP | KGP 2.1.0–2.1.10 lists Gradle through 8.10 and AGP through 8.7.2; project is 8.13/8.13.2. | **Update coordinated toolchain**. KGP/Compose plugin **2.3.21** fits the narrow compatibility matrix but remains below the KAPT-cache advisory's fixed version; §7.3/WP0a applicability review is a prerequisite. If a fixed version is required, qualify 2.4.20 with a supported AGP/R8 tuple. Neither candidate was tested. [S03][S03] [S50][S50] |
| Kotlin bytecode ↔ Android shrinker | AGP 8.13.2 carries R8 8.13.19, the published Kotlin 2.3 floor; Kotlin 2.4 requires R8 9.1.29. | **Gate Kotlin 2.4 on an approved AGP/R8 combination**; bring that work forward if the advisory requires a fixed version. KGP's plugin matrix does not establish D8/R8 support. [S04][S04] |
| KSP ↔ Kotlin/processors/AGP | Current KSP 2.1.0-1.0.29 belongs to the older Kotlin-coupled version scheme. No project `ksp.useKSP2` setting found. Effective engine not proven from this review. | **Update to KSP2**, candidate 2.3.12 (minimum AGP 8.12), together with qualified Room/Hilt processors. New KSP numbering is not a Kotlin-version match. Audit CI/user property overrides; current upstream no longer supports KSP1. [KSP][S06] |
| Hilt plugin ↔ AGP | Current 2.56.2; upstream 2.59+ requires AGP 9 for its Gradle plugin. | **Qualify 2.58 as an 8.x-line candidate**, not 2.60.1 automatically. Keep plugin/runtime/compiler aligned. Its binding-graph validation change may expose incorrect component scopes. [Dagger][S07] |
| Compose ↔ Kotlin | Kotlin Compose plugin shares `kotlin=2.1.0`; library BOM is independently 2024.12.01. | Update compiler with Kotlin; update UI BOM in a separate behavior-tested slice. BOM does not manage KGP, compiler plugin, Activity, Lifecycle, Hilt, or all AndroidX. [Compose compiler][S19], [BOM][S21] |
| Coroutines ↔ Kotlin/tests | Runtime/test 1.9.0; current 1.11.0 was built with Kotlin 2.2.20. AndroidX Test Core 1.7 also changes its coroutines dependencies. | Update only after compiler qualification, align runtime/test, and inspect both production and test selection. Avoid forcing a lower version to hide metadata incompatibility. [Coroutines][S26], [AndroidX Test][S31] |
| New AndroidX ↔ platform floor | Room 2.8 raises its floor to API 23, which fits minSdk 26. Hilt integration 1.4 and Lifecycle 2.11 Compose release notes introduce API 37 / minimum AGP 9.2 requirements; Hilt 1.4 also requires KGP 2.2+. Compose UI 1.12 has the API 37 change. | **Do not put these latest Compose integrations into the AGP 8/API 36 lane.** Qualify Hilt integration **1.3.0**, an earlier Lifecycle/Compose BOM cohort, and their full AAR metadata. Raising compileSdk/AGP is a separate WP9 decision, not an incidental library bump. [Room][S14], [AndroidX Hilt][S17], [Lifecycle][S11], [Compose UI][S46] |
| AGP 9/Gradle 9 branch | Upstream AGP 9.4 requires Gradle 9.6; latest observed Gradle is 9.8.0. | **Defer as a separate major migration**. Built-in Kotlin replaces the Android Kotlin plugin; new DSL and processor/plugin compatibility need explicit work. Do not assemble latest versions independently. [AGP 9.4][S35], [built-in Kotlin][S36], [Gradle releases][S37] |

Recommended order: establish fresh graphs and mitigate the wrapper advisory; repair build/reporting tooling; qualify Kotlin/Compose compiler/KSP/Room processor/Hilt together; then qualify coroutines, AndroidX UI/testing, native/crypto libraries in separate changes. Security storage replacement is a separately approved architectural workstream. Camera/blob retirement should follow M8's data-disposition decision, not an incidental dependency cleanup.

## 4. Complete version-alias inventory

All 25 entries below are **declarations**, not fresh resolved results. “Current upstream” means the stable release observed in the cited source on the review date unless labelled otherwise; it is not a blanket target. Library/plugin consumers are enumerated in §§5–6. WP references resolve to practical implementation guides in §9.

| Version alias | D | Consumers / purpose | Maintenance, constraints, and explicit disposition |
|---|---|---|---|
| `agp` | 8.13.2 | Android application plugin, packaging/shrinking/GMD | Maintained family; 9.4 available. **Retain** 8.13.2 for the narrow lane; defer 9.x. §3, WP1/WP9. [S02][S02] [S35][S35] |
| `kotlin` | 2.1.0 | Android Kotlin and Compose compiler plugins | 2.4.20 observed upstream; current tuple outside matrix and has advisory range match. **Update**;2.3.21 is a conditional compatibility candidate, not a fix; assess 2.4.20 supported tuple if needed. WP0a/WP3. [S03][S03] [S38][S38] [S50][S50] |
| `ksp` | 2.1.0-1.0.29 | Room/Hilt symbol generation | KSP1 unsupported upstream; 2.3.12 observed, AGP ≥8.12. **Update**/confirm KSP2; WP3. [S06][S06] |
| `coreKtx` | 1.15.0 | Core Android compatibility/Kotlin extensions | Active; 1.19.1 observed. **Update** in AndroidX cohort after AAR metadata check, WP4. No package-wide deprecation found. [S10][S10] |
| `lifecycle` | 2.8.7 | Runtime and Compose ViewModels | Active; 2.11.0 observed, but its Compose API 37/AGP 9.2 floor blocks the narrow lane. **Update** to a qualified earlier cohort / **replace** empty runtime KTX artifact (since 2.8), WP4. [S11][S11] |
| `activityCompose` | 1.9.3 | Activity/Compose hosts and back handling | Active; 1.13.0 observed. **Update** after Kotlin; verify back/focus lifecycle, WP4. [S12][S12] |
| `composeBom` | 2024.12.01 | Production and instrumentation Compose constraints | Current documentation uses 2026.09.00; this is **not** an approved API 36 target. **Update** to a cohort whose selected modules fit the retained toolchain, or defer to WP9. WP4. [S21][S21] [S46][S46] |
| `room` | 2.6.1 | Runtime, KTX, KSP processor | Active; 2.8.5 observed. **Update** all three consistently; remove KTX only after reaching merged 2.7+ runtime. WP3/WP5. [S14][S14] |
| `securityCrypto` | 1.1.0-alpha06 | Encrypted prefs/files and master-key wrapper | API family deprecated; stable 1.1.0 does not reverse deprecation. **Replace** under existing proposal; temporary retention for compatibility/legacy reader. WP7. [S08][S08] |
| `biometric` | 1.1.0 | AndroidX BiometricPrompt integration | Still published stable; 1.4 alpha is not a stable replacement mandate. **Retain**; test after adjacent UI changes. [S16][S16] |
| `coroutines` | 1.9.0 | Runtime dispatch/flows and deterministic JVM tests | Active; 1.11.0 observed. **Update** paired Android/test artifacts after Kotlin, WP3b. [S26][S26] |
| `junit` | 4.13.2 | JVM tests; Android testing also uses JUnit4 | JUnit4 is in maintenance mode, not removed. 4.13.2 remains its release. **Retain** for current harness; no forced Jupiter migration. [S30][S30] |
| `bouncycastle` | 1.78.1 | Argon2id PIN derivation | Active `bcprov-jdk18on`; 1.86 observed. **Update** after KDF compatibility/performance and advisory review, WP5a. [S22][S22] [S23][S23] |
| `sqlcipher` | 4.6.1 | Native encrypted Room database | Maintained `sqlcipher-android`; 4.19.0 stable observed, 5.0 beta separate. **Update** on 4.x after native/database qualification, WP5b. [S24][S24] [S25][S25] |
| `sqlite` | 2.4.0 | SQLite support interfaces for Room integration | Active; 2.7.1 observed; KTX merged into base in 2.7. **Replace** unnecessary KTX declaration with base API, **update** with Room constraints, WP5c. Consolidation is distinct from formal API deprecation. [S15][S15] |
| `camerax` | 1.4.1 | Intruder capture core/backend/lifecycle | Active; 1.6.2 observed. **Remove** with governed M8 feature retirement; **retain** until then unless a reachable defect requires earlier update. WP6. [S13][S13] |
| `detekt` | 1.23.7 | Analysis plugin and formatting rules | Stable 1.23.8; 2.0.0-alpha.6 is prerelease. **Update** stable pair as bridge, qualify parsing of chosen Kotlin; defer alpha unless explicitly approved. WP2. [S27][S27] |
| `konsist` | 0.17.3 | Kotlin source architecture tests | 0.17.3 remains latest tagged release in inspected upstream. No EOL declaration found; cadence alone is inconclusive. **Retain**, investigate parser coverage with newer Kotlin; WP2/WP3. [S28][S28] |
| `licenseReport` | 2.9 | Shipping dependency/license report | 2.x EOL at 2.9; 3.x maintained, 3.1.4 observed. **Update** independently to qualified 3.x; WP2. [S29][S29] |
| `hilt` | 2.56.2 | DI runtime, processor, Gradle plugin | Active; 2.60.1 observed but requires AGP 9. **Update** candidate 2.58 in narrow lane; WP3. [S07][S07] |
| `hiltNavigationCompose` | 1.2.0 | `hiltViewModel()` integration | Active integration;1.4.0 observed but requires newer toolchain/API. **Replace** with lifecycle ViewModel Compose artifact, candidate **1.3.0** on current API 36 lane. WP4. [S17][S17] [S18][S18] |
| `androidxTestCore` | 1.6.1 | ApplicationProvider/ActivityScenario | Stable 1.7.0 observed; test coroutines selection changes. **Update** aligned Android test cohort, WP8. [S31][S31] |
| `androidxTestJunit` | 1.2.1 | AndroidJUnit4 integration | Stable 1.3.0 observed. **Update** with runner/core, WP8. [S31][S31] |
| `androidxTestRunner` | 1.6.2 | On-device runner and filtering | Stable 1.7.0 observed. **Update** with core/ext, preserving annotation exclusions and separate heavy campaigns, WP8. [S31][S31] |
| `uiautomator` | 2.3.0 | Cross-window overlay input/content assertions | Stable 2.4.0 observed. **Update** separately within test cohort, preserving oracle semantics; WP8. [S32][S32] |

## 5. Complete library inventory and selection evidence

Scopes: **I** = implementation; **K** = ksp processor; **T** = JVM testImplementation; **AT** = androidTestImplementation; **Dg** = debugImplementation; **DP** = detektPlugins. All are declared in `app/build.gradle.kts`. Version column is **D / F**. F is the selected version in the corresponding fresh production/test/processor/tool configuration; scope membership is retained in resolved-components.csv. Historical values remain separately in historical-release-inventory.csv. Status/source constraints from the named version family in §4 apply to each row, including retained items.

| Library alias / Maven coordinate | D / F | Where and why used | Explicit disposition / guide |
|---|---|---|---|
| `androidx-core-ktx` — `androidx.core:core-ktx` | 1.15.0 / 1.15.0 | I; permission, context, notification/platform compatibility code | **Update**, active API family; WP4, `coreKtx` |
| `androidx-lifecycle-runtime-ktx` — `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 / 2.8.7 | I; lifecycle scopes/owners, including overlay host | **Replace** empty compatibility artifact with `lifecycle-runtime`; preserve needed transitive APIs, WP4 |
| `androidx-lifecycle-viewmodel-compose` — `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 / 2.8.7 | I; Compose/ViewModel lifecycle integration | **Update** with Lifecycle/Hilt integration; WP4 |
| `androidx-activity-compose` — `androidx.activity:activity-compose` | 1.9.3 / 1.9.3 | I; `MainActivity`, lock/activity Compose surfaces, back handlers | **Update** with lifecycle/back regression tests; WP4 |
| `androidx-compose-bom` — `androidx.compose:compose-bom` | 2024.12.01 / 2024.12.01 | I + AT `platform(...)`; aligned Compose constraints | **Update** coherent production/test constraints; WP4 |
| `androidx-compose-ui` — `androidx.compose.ui:ui` | BOM / 1.7.6 | I; Compose rendering/input across screens and overlay | **Update through BOM**; no independent pin; WP4 |
| `androidx-compose-ui-tooling-preview` — `androidx.compose.ui:ui-tooling-preview` | BOM / 1.7.6 | I declaration; no `@Preview`/preview import found | **Remove** if confirm no intended preview consumer; otherwise scope deliberately. Not deprecated; WP6 |
| `androidx-compose-material3` — `androidx.compose.material3:material3` | BOM / 1.3.1 | I; Material3 components/theme/PIN and settings UI | **Update through BOM**, retain Material3; WP4 |
| `androidx-compose-material-icons` — `androidx.compose.material:material-icons-extended` | BOM / 1.7.6 | I; `Icons.*` in Compose screens | **Replace** unmaintained icon artifact with reviewed local vector resources; WP4b |
| `androidx-room-runtime` — `androidx.room:room-runtime` | 2.6.1 / 2.6.1 | I; `AppLockDatabase`, entities/DAOs, repositories | **Update**, keep SQLCipher-backed helper; WP3/WP5c |
| `androidx-room-ktx` — `androidx.room:room-ktx` | 2.6.1 / 2.6.1 | I; suspending/Flow database access | **Retain until runtime upgrade, then remove** at 2.7+ merge; WP5c |
| `androidx-room-compiler` — `androidx.room:room-compiler` | 2.6.1 / 2.6.1 | K; generated database/DAO implementation | **Update** lockstep with runtime and qualify KSP2; WP3/WP5c |
| `androidx-security-crypto` — `androidx.security:security-crypto` | 1.1.0-alpha06 / 1.1.0-alpha06 | I; credential prefs, lockout prefs, database-key prefs, encrypted blobs | **Replace** only through proposal qualification; retain legacy decoder until supported-upgrade gate closes; WP7 |
| `androidx-biometric` — `androidx.biometric:biometric` | 1.1.0 / 1.1.0 | I; biometric authentication flows/prompts | **Retain** stable version; rerun success/cancel/error UI tests after WP4 |
| `bouncycastle-provider` — `org.bouncycastle:bcprov-jdk18on` | 1.78.1 / 1.78.1 | I; `Argon2PinHasher`, credential verifier compatibility | **Update** candidate 1.86; verify KDF byte output and bounded memory/latency; WP5a |
| `sqlcipher-android` — `net.zetetic:sqlcipher-android` | 4.6.1 / 4.6.1 | I; `System.loadLibrary`/`SupportOpenHelperFactory` in database build | **Update** candidate 4.19.0; current coordinate is already the maintained Android artifact, not legacy `android-database-sqlcipher`; WP5b |
| `androidx-sqlite-ktx` — `androidx.sqlite:sqlite-ktx` | 2.4.0 / 2.4.0 | I; source needs `SupportSQLiteDatabase`; no direct KTX extension use found | **Replace** declaration with explicit base `androidx.sqlite:sqlite` if audit holds; coordinate version with Room, WP5c |
| `androidx-camera-core` — `androidx.camera:camera-core` | 1.4.1 / 1.4.1 | I; `IntruderCaptureManager` capture APIs | **Remove** at M8 retirement; retain current family pending that decision, WP6 |
| `androidx-camera-camera2` — `androidx.camera:camera-camera2` | 1.4.1 / 1.4.1 | I; Camera2 backend selected by CameraX | **Remove** with capture, not based on missing direct imports; WP6 |
| `androidx-camera-lifecycle` — `androidx.camera:camera-lifecycle` | 1.4.1 / 1.4.1 | I; ProcessCameraProvider/lifecycle binding | **Remove** with capture; WP6 |
| `kotlinx-coroutines-android` — `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 / 1.9.0 | I; dispatchers, scopes, flows, runtime/lockout scheduling | **Update** with semantic/cancellation tests, WP3b |
| `kotlinx-coroutines-test` — `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 / 1.9.0 | T; deterministic dispatchers/time in runtime/lockout tests | **Update** with runtime, record scheduling differences rather than weakening assertions; WP3b |
| `junit` — `junit:junit` | 4.13.2 / 4.13.2 | T; existing JVM test suite/architecture rules | **Retain** maintenance-line JUnit4; Android/Compose interoperability is a reason to keep it |
| `detekt-formatting` — `io.gitlab.arturbosch.detekt:detekt-formatting` | 1.23.7 / 1.23.7 | DP; ktlint-backed formatting rules | **Update** exact version with detekt plugin, WP2 |
| `konsist` — `com.lemonappdev:konsist` | 0.17.3 / 0.17.3 | T; `ArchitectureRulesTest` source/layer rules R1–R4 | **Retain**, qualify parser and negative-rule fixtures after Kotlin change; WP2/WP3 |
| `hilt-android` — `com.google.dagger:hilt-android` | 2.56.2 / 2.56.2 | I; `AppLockApplication`, `AppModule`, injected Android components/ViewModels | **Update** with compiler/plugin, candidate 2.58 on AGP 8; WP3 |
| `hilt-android-compiler` — `com.google.dagger:hilt-android-compiler` | 2.56.2 / 2.56.2 | K; Hilt/Dagger graph generation | **Update** with runtime/plugin; validate scope errors and generated code; WP3 |
| `androidx-hilt-navigation-compose` — `androidx.hilt:hilt-navigation-compose` | 1.2.0 / 1.2.0 | I; four source files import old `hiltViewModel` package | **Replace** with `androidx.hilt:hilt-lifecycle-viewmodel-compose` and new import; WP4 |
| `androidx-test-core-ktx` — `androidx.test:core-ktx` | 1.6.1 / 1.6.1 | AT; ApplicationProvider, ActivityScenario in device fixtures | **Update** candidate 1.7.0; WP8 |
| `androidx-test-ext-junit` — `androidx.test.ext:junit` | 1.2.1 / 1.2.1 | AT; AndroidJUnit4 test integration | **Update** candidate 1.3.0; WP8 |
| `androidx-test-runner` — `androidx.test:runner` | 1.6.2 / 1.6.2 | AT; `AndroidJUnitRunner`, device orchestration/filtering | **Update** candidate 1.7.0; WP8 |
| `androidx-test-uiautomator` — `androidx.test.uiautomator:uiautomator` | 2.3.0 / 2.3.0 | AT; cross-window input, visible bounds/content and overlay races | **Update** candidate 2.4.0, validate existing API behavior before new DSL adoption; WP8 |
| `androidx-compose-ui-test-junit4` — `androidx.compose.ui:ui-test-junit4` | BOM / 1.7.6 | AT; `createEmptyComposeRule` in PIN setup/lock-screen launch tests | **Update through BOM**, retain JUnit4 harness; selected root/Android test modules are 1.7.6; WP4/WP8 |
| `androidx-compose-ui-test-manifest` — `androidx.compose.ui:ui-test-manifest` | BOM / 1.7.6 | Dg; declares Compose test-host activity; tests found use empty rules and own activities | **Investigate further**, then remove if no host consumer; do not remove solely because the dependency has no imports. WP6 |

Fresh resolution selected 1.7.6 for the five UI/preview/icons/test BOM aliases and 1.3.1 for Material3; both test-only BOM aliases are now verified selections. The full graph preserves platform constraints and selection reasons. These values describe this baseline, not candidate-update qualification.

### 5.1 Transitive observations, without false direct pins

The fresh [coordinate inventory](2026-09-25_dependency-deprecation/resolved-components.csv) now covers runtime, build, processor and test scopes. The historical release CSV is retained for comparison; the fresh release graph independently selected the same 133 coordinates. The observations below are fresh selections. Full license and reachability qualification remains separate from resolution.

| Fresh selection | Meaning / disposition |
|---|---|
| Kotlin stdlib **2.1.10**, while compiler plugin is **2.1.0** | **Selection explained:** Hilt 2.56.2 requests stdlib 2.1.10. Align through a qualified toolchain; these are different artifacts. Old stdlib-jdk7/jdk8 compatibility entries do not determine the main stdlib. JVM tests also select compiler-embeddable 2.0.21 through Konsist; do not force that tooling compiler to equal the application compiler. |
| `com.google.crypto.tink:tink-android` **1.8.0**, Gson **2.8.9** | **Investigate current paths/advisories**; Security's legacy reader introduces crypto/keyset dependencies. Do not force a newer Tink under the deprecated wrapper without compatibility tests, or call their age a vulnerability. WP0/WP7. |
| Dagger/Hilt core **2.56.2**, `org.jspecify:jspecify` **1.0.0** | **Retain aligned selection pending WP3**; inspect resource-exclusion necessity after update. |
| Navigation Compose/runtime/common **2.5.1** | **Remove if no longer needed after Hilt artifact move**; no app Navigation imports found. Verify graph before promising removal. WP4. |
| AppCompat **1.2.0**, Fragment **1.5.1**, ExifInterface **1.3.2**, Startup **1.1.1**, ProfileInstaller **1.3.1** | **Investigate upstream dependency paths**, update parents or retire features first. No specific exploit or package deprecation established here. Do not add unrelated direct pins merely to make a version report look current. |
| Remaining fresh CSV coordinates | **Retain pending relevant work packages and license/native/reachability qualification**. Every row records an initial disposition and OSV matches. Zero OSV matches is not a clean bill of health; a release gate must disposition native payloads and manually identified advisories too. |

## 6. Plugins, wrapper, build, and CI inventory

All seven plugins are declared in the root build with `apply false` and applied in the app build. Their implementation versions in the fresh root buildscript classpath match the declarations below; the retained graph also records the applied implementation classes. Root buildscript contributes 158 selected components, independently of the empty app buildscript classpath.

| Plugin alias / ID | D | Use, status, and explicit disposition |
|---|---|---|
| `android-application` — `com.android.application` | 8.13.2 | Android packaging/variants/shrinking/GMD. **Retain** in narrow lane; defer major update to WP9. [S02][S02] |
| `kotlin-android` — `org.jetbrains.kotlin.android` | 2.1.0 | Kotlin compilation. **Update** in WP3; **remove application of this plugin only when migrating to AGP 9 built-in Kotlin**, not now. [S03][S03] [S36][S36] |
| `kotlin-compose` — `org.jetbrains.kotlin.plugin.compose` | 2.1.0 | Compose compiler. **Update exactly with Kotlin**; retain separate from UI BOM. WP3. [S19][S19] |
| `ksp` — `com.google.devtools.ksp` | 2.1.0-1.0.29 | Room/Hilt processing. **Update** to qualified KSP2, WP3. [S06][S06] |
| `detekt` — `io.gitlab.arturbosch.detekt` | 1.23.7 | Static/style analysis. **Update** stable bridge with ruleset, WP2. [S27][S27] |
| `license-report` — `com.github.jk1.dependency-license-report` | 2.9 | License inventory. **Update** from EOL 2.x to maintained 3.x, WP2. [S29][S29] |
| `hilt` — `com.google.dagger.hilt.android` | 2.56.2 | Android DI code-generation integration. **Update** with runtime/compiler; do not cross 2.59 without AGP 9, WP3/WP9. [S07][S07] |

| Non-catalog item / location | Observed configuration | Explicit disposition and compatibility issue |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.properties` and wrapper scripts/JAR | Gradle 8.13 binary distribution, URL validation on, **no distribution SHA-256 configured** | **Update** to qualified 8.14.4; verify official distribution checksum and wrapper JAR. URL validation is not integrity verification. WP1. [S01][S01] [S42][S42] |
| `app/build.gradle.kts` compiler DSL | `android.kotlinOptions { jvmTarget = "17" }` | **Replace** deprecated DSL with typed `kotlin.compilerOptions`; preserve Java/JVM target 17, WP3. [S05][S05] |
| SDK levels / Java / GMD configuration | min 26/compile 36/target 36, Java 17; local reports JBR 21; CI 17; experimental old-API GMD flag | **Retain** levels for this review; **investigate** actual host parity and metadata before upgrades. Experimental GMD opt-in is documented project tooling debt, not product API deprecation. |
| `settings.gradle.kts` repositories | Google/Maven Central; Plugin Portal for plugins; plugin Google group filter; `FAIL_ON_PROJECT_REPOS` | **Retain approved repositories; strengthen** ownership/content filtering and verification, WP1. Existing filter is not comprehensive exclusive routing. |
| Dependency verification/locks | No committed verification metadata or dependency lockfiles found in inspected source tree | **Investigate/adopt reviewed integrity and reproducibility controls**, WP0/WP1. Never trust a first downloaded checksum merely because the build accepts it. |
| `app/proguard-rules.pro` / packaging | Broad Tink `-dontwarn`; multi-release OSGi manifest exclusion for Hilt/BC | **Investigate and narrow** after resolved-graph/R8 diagnostics; preserve necessary rules until demonstrated redundant. WP3/WP5/WP7. Do not mask new missing-class errors. |
| `app/lint-baseline.xml`, detekt config/baseline | Existing debt filters; auto-correct off | **Retain** governance; **review/burn down** only understood entries. No blanket baseline regeneration during updates. WP2/WP4. |
| `.github/workflows/ci.yml` | `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`, `gradle/actions/setup-gradle@v4` | **Investigate/update and pin reviewed SHAs** in separate CI change. Observed upstream majors: checkout 7, setup-java 6, upload-artifact 7, Gradle actions 6. Current floating v4 tags do not disclose executed patch/SHA. Review each runner/Node and behavior requirement; no finding that every v4 is unsupported. WP8. [S43][S43] |
| CI execution policy | JDK 17, Ubuntu latest; unit/detekt/assemble/lint/license; instrumentation excludes PRs and is `continue-on-error` | **Strengthen release gating** after harness qualification. Current green CI does not certify device suites. Overlay race and R007 device-tool annotations are excluded from fast lane; run intended campaigns explicitly, not blindly. WP8. |
| `.github/dependabot.yml` | Weekly Gradle/actions checks; update PR limit 10 | **Retain**, group only compatible cohorts; inspect repository enablement/backlog separately. File presence does not prove runs/alerts exist. WP0/WP8. |
| License task scope | Only `prodReleaseRuntimeClasspath` | **Retain shipping notices scope; add separate audit coverage** for processors/build/test/native dependencies. Do not merge test-only licenses into shipped notices without reason. WP0/WP2. |

## 7. Deprecated APIs, legacy components, and security applicability

### 7.1 Source findings

Paths here are under `app/src/main/java/com/applock/` unless specified. A source using an old artifact may not produce a deprecation warning until that artifact is upgraded. Conversely, a local `@Suppress("DEPRECATION")` is not proof the current Android reference deprecates the method.

| Finding and source | Classification / consequence | Explicit disposition and implementation route |
|---|---|---|
| `security/CredentialRepository.kt`, `DatabaseKeyProvider.kt`, `EncryptedPrefsLockoutStorage.kt`: three `EncryptedSharedPreferences.create` and three `MasterKey.Builder` sites | **Confirmed upstream deprecation/retirement**. PIN verifier/salt/parameters, exact DB passphrase, and lockout pair have different durability needs. | **Replace** via WP7 and existing proposal. Do not interpret an unreadable store as a new installation. |
| `security/EncryptedFileStore.kt`: fourth `MasterKey.Builder`, one `EncryptedFile.Builder` helper | **Confirmed upstream deprecation**; streamed vault/intruder files, also consumed by `VaultRepository` and `IntruderCaptureManager` through `AppModule` | **Remove or replace**, depending on approved M8 installed-data disposition. Whole-object DataStore is not a substitute for large streaming blobs. WP6/WP7. |
| `app/build.gradle.kts`: `kotlinOptions` | **Confirmed DSL deprecation** from Kotlin 2.0; 2.2 increases it to an error. [S05][S05] | **Replace before compiler bump**, preserve target 17 and flags in typed compilerOptions. WP3. |
| Old `androidx.hilt.navigation.compose.hiltViewModel` imports in `presentation/applist/MainActivity.kt`, `settings/SettingsScreen.kt`, `vault/VaultScreen.kt`, `intruder/IntruderLogScreen.kt` | **Confirmed deprecation in newer API**; moved in 1.3. The pinned 1.2 API is not evidence of a current compiler warning. [S18][S18] | **Replace import/artifact**; verify the self-gate's Activity/Compose callers still share the same owner/instance. Do not accidentally rescope authorization state. WP4. |
| Material `Icons` imports in PIN pad, authentication, app list, settings, vault/intruder UI | **Confirmed artifact maintenance issue**: no longer maintained/recommended; not a claim every icon property is `@Deprecated`. Existing directional icons already use AutoMirrored variants. [S20][S20] | **Replace retained icons** with local resources; skip assets belonging solely to approved M8 deletions. Preserve mirroring, accessibility descriptions and touch areas. WP4b/WP6. |
| Lifecycle runtime KTX, Room KTX, SQLite KTX declarations | **Confirmed consolidation**, at different versions: Lifecycle 2.8, Room 2.7, SQLite 2.7. [S11][S11] [S14][S14] [S15][S15] | **Replace/remove at appropriate version boundary**. Do not remove Room 2.6.1 KTX on the assumption current extensions are already in runtime. WP4/WP5c. |
| `platform/lock/PackageManagerHomeResolver.kt` and `HomeResolverDeviceTest`: suppressed int-flags calls; package lookup in lock UI, overlay, intruder/app-list code | Current PackageManager reference still documents these legacy-compatible overloads; this review did **not** establish all as formally deprecated. [S39][S39] | **Retain needed API 26 compatibility**; investigate actual compile/lint warnings. If modernizing, API 33 typed flags need guarded paths or compatible wrappers and HOME-resolution tests; no mandatory cosmetic rewrite. |
| `platform/spike/SpikeLauncherActivity.kt`: suppression on `checkOpNoThrow` | Current API 36 reference instead deprecates `unsafeCheckOpNoThrow` in favor of `checkOpNoThrow`. [S40][S40] | **Retain** present call pending spike retirement; **investigate** stale suppression. Do not migrate to the more recently deprecated alternative. |
| `presentation/applist/MainActivity.kt`: notification `requestPermissions`; `collectAsState` in Compose | Framework permission use/state collection alone is **not a confirmed deprecation**. Activity Result/lifecycle-aware flow collection may improve particular behavior but have lifecycle implications. | **Retain**, consider modernization only in an independently justified UI slice. No automatic session-state collection rewrite. |
| Legacy `applocker/service/AppDetectionService`, lock Activity, polling spike | Product/architecture transition code, **not proof AccessibilityService, UsageStatsManager or overlay APIs are deprecated** | **Remove/retire only at the M7 cutover gate**. Preserve ADR-017/018 package/component identity obligations and live legacy callers until then. |
| `platform/spike/UsagePollService.kt:134`: `ACTIVITY_RESUMED` comparison | Fresh lint reports `InlinedApi` (API 29 constant, minSdk 26). This is a compatibility diagnostic, **not a deprecation or demonstrated runtime failure**. | **Investigate** the retained spike's API 26–28 event behavior, or retire with M7. If retained, verify foreground transitions on API 26/28 and API 29+ using known event fixtures and a device/emulator; require the expected package transition and no missed enforcement. Any guard/compatibility change belongs in a separate tested source change; revert it if those checks regress. |
| `AndroidManifest.xml:159`: exported `platform.spike.UsagePollService` without a permission | Fresh lint reports `ExportedService`. Manifest comments explicitly document the export as a spike-only adb/OV-4 test aid. This is **not** the separate accessibility-service declaration and is not a proven bypass. | **Investigate/retire at M7 cutover**; inspect merged debug/release manifests and authorized versus unauthorized starts before release acceptance. Remove the export or component through the approved spike-retirement slice; preserve required test control in debug scope. Pass when the shipping graph has only intended entry points and enforcement tests pass. Rollback must not silently restore an unreviewed release export. |
| Device-admin receiver/activation path | Some Device Administration policies are deprecated; current app's empty policy declaration/activation use does not establish use of those policies. [S41][S41] | **Investigate retained-product scope**, not “replace the entire API because old.” Preserve installed component identity and test enrollment/removal if touched. |
| `AndroidManifest.xml` `allowBackup=false`, lint-baseline backup warning; no explicit data-extraction/legacy backup-rule resources found | **Confirmed configuration coverage gap**, not proof of a data leak. Android still documents allowBackup; OEM device transfer may ignore it. [S34][S34] | **Retain flag and add reviewed exclusions** under proposal WP7. Do not delete the flag to remove a warning. Verify both OS rule formats and actual transfer behavior. |
| Explicit-looper Handlers, indeterminate progress indicators, `androidx.lifecycle.compose.LocalLifecycleOwner`, AndroidX BiometricPrompt | Inspected uses do not match common deprecated no-arg Handler, old Compose lifecycle-owner, determinate-progress or platform FingerprintManager patterns | **Retain**. Recheck selected-version compiler output after updates; no general “all APIs clean” claim. |

No uses of AsyncTask, LocalBroadcastManager, `startActivityForResult`, `launchWhen*`, or jcenter were identified in the scoped scans. A future Compose release's deprecated text-field/visual-transformation overloads are not automatically a finding against this app: the PIN surface uses its own keypad. The actual upgraded compiler/lint diagnostics, including baseline-filtered items, remain a required follow-up.

### 7.2 Security advisories versus ordinary age

| Item | Verified advisory/maintenance evidence | Project applicability and disposition |
|---|---|---|
| Gradle 8.13 | CVE-2026-22816, maintainer **High, CVSS4 8.6**, published 2026-01-16; fixed 8.14.4 / 9.3+ | **Version affected.** Repository lookup after unresolvable-host errors can select unexpected artifacts. No compromise demonstrated. Current configuration is not a complete documented mitigation. Prioritize WP1; strict repository ownership/verification is the fallback if the wrapper update cannot yet qualify. [S01][S01] |
| SQLCipher 4.6.1 | Maintainer's 2026-09-08 advisory: two **Low, CVSS4 2.1** issues in ≤4.18.0, fixed 4.19.0 | **Version affected; app trigger not established.** One requires attacker-controlled attached-schema SQL reaching `sqlcipher_export`; another concerns invalid non-empty `hexkey` URI material. Current open uses a byte-array passphrase factory, with neither triggering use found. Document reachability instead of declaring the database plaintext. WP5b. [S24][S24] |
| BC 1.78.1 | Four OSV matches confirmed against maintainer algorithm/range descriptions: GOST CTR, LDAP, PKIX name constraints and lazy ASN.1 (§7.3) | **Affected package; no direct application trigger identified.** The two BC imports are Argon2BytesGenerator/Argon2Parameters. That is not proof of all transitive/R8 reachability. Qualify WP5a and inspect packaged code. Separately, OpenPGP Argon2 S2K CVE-2026-59648 concerns bcpg and must not be assigned to this PIN hasher just because both use Argon2. [S45][S45] [S51][S51] |
| security-crypto alpha06 | Deprecated API family/no future releases; transitive Tink is old | **Maintenance finding**, not an established ciphertext exploit. Replace through qualified migration, never discard user security state simply to remove a scanner entry. [S08][S08] [S09][S09] |
| License plugin 2.9 | Explicit 2.x EOL | **Build-tool maintenance finding**. No specific exploitable CVE asserted. Update maintained 3.x; audit its dependencies separately. [S29][S29] |
| Other Maven build/test components | Completed OSV lookup found matches in KGP, Netty, Protobuf, Commons Compress, jose4j, JDOM and build-tool BC | **Investigate/remediate owning toolchain**, §7.3/WP0a; these are not production-runtime findings merely because the build resolves them. Every advisory has a retained initial disposition. |
| Native payloads, Gradle distribution internals, CI action execution and unreturned advisories | Not comprehensively covered by Maven OSV lookup; Gradle/SQLCipher findings were separately reviewed | **Investigate further** under WP0; absence from OSV results does not supersede a maintainer advisory or certify safety. |

For SQLCipher Community Android, the maintainer lists a LibTomCrypt-based provider. Do not assign commercial OpenSSL fixes to the community AAR without examining the actual binary. A future SBOM must include native payloads and their upstream provenance; Java coordinates alone miss this layer. [Provider matrix][S24]

NFR-SEC-011 and NFR-MNT-007 remain **unqualified by this review**. Known build-tool exposure should not be dismissed because the vulnerable tool is absent from the APK. Conversely, an unreachable advisory in an unshipped test artifact needs a build/test applicability assessment, not a fabricated end-user exploit. Record rationale, owner, remedy, deadline, and recheck date; do not use indefinite “accepted age” as a disposition for an EOL dependency.

### 7.3 Fresh advisory findings and applicability decisions

All **51 unique advisory records** and their affected selected coordinates have individual rows in [advisory-triage.csv](2026-09-25_dependency-deprecation/advisory-triage.csv). Database severity is labelled as such; it is not this project's measured risk. The 21 matching coordinates include different versions in different classpaths and are not 21 separately exploitable flaws. Exact configuration membership and incoming dependency parents are in [matched-components.csv](2026-09-25_dependency-deprecation/matched-components.csv). Primary-source checks below cover the stated families/findings; remaining individual Netty exploit conditions and jose4j's inaccessible maintainer page remain open rather than being described as fully verified.

| Selected component / entry path | Evidence and scope | Disposition and evidence needed to close |
|---|---|---|
| `bcprov-jdk18on:1.78.1`, direct app dependency; `:1.79` via AGP builder/sdk-common/apkzlib and bcpkix | CVE-2025-14813 (GOST CTR), CVE-2026-0636 (explicit LDAP API), CVE-2026-8763 (PKIX name constraints), CVE-2026-13506 (lazy ASN.1/CRL recursion). Maintainer lists branch fixes 1.80.2/1.81.1/1.84 for the first two,1.85 for the latter two. [S51][S51] | **Update app BC under WP5a; investigate build BC separately under WP0a.** No affected direct calls found; cannot infer absence from all packaged or build paths. Preserve Argon2 output and credentials. A production alias bump cannot repair AGP's separate BC selection. |
| `bcpkix-jdk18on:1.79`, AGP build classpath | CVE-2026-5588, empty draft composite signatures; maintainer confirms 1.79 lies in range and describes fixed branches. No production bcpkix coordinate. [S52][S52] | **Investigate** certificate/signature inputs used by packaging tools; prefer supported parent update, or review a narrowly scoped, tested build-classpath override. Do not call PIN verification compromised. |
| `kotlin-gradle-plugin:2.1.0`, Kotlin/Compose plugin path | CVE-2026-53914 / GHSA-r937-wjx7-w2jp; JetBrains CNA record published 2026-06-26 identifies pre-2.4.20 unsafe cache deserialization. Maintainer patch restricts **KAPT incremental-cache** deserialization. Project declarations/applied-plugin inventory use KSP, with no KAPT plugin/configuration found. [S50][S50] [S50a][S50a] | **Investigate applicability before WP3 freeze.** KGP 2.3.21 is also below the fixed version: retain it only as a conditional compatibility candidate with reviewed evidence that the vulnerable path is not exercised and cache trust is controlled. If reachability or policy requires a fixed version, advance the supported KGP 2.4.20/AGP/R8 lane; do not override R8 blindly or describe 2.3.21 as remediation. `--no-build-cache` alone is not proof that KAPT's incremental files are unused. |
| `protobuf-java:3.24.4`, `protobuf-kotlin:3.24.4`, UTP instrumentation/core/launcher | CVE-2024-7254; official advisory confirms crafted unknown fields can exhaust recursion, with fixes 3.25.5/4.27.5/4.28.2. Full/lite and Kotlin wrapper scope matter. [S53][S53] | **Investigate UTP inputs and update owning AGP/UTP cohort**, with device-runner compatibility; no production Protobuf coordinate was matched. Do not force an unqualified 4.x runtime under generated 3.x tools. New DataStore serializer dependencies need their own check, §8. |
| Netty codec/http/http2/common/handler/proxy, each at 4.1.110.Final and 4.1.93.Final | Newer selection comes through AGP/UTP gRPC 1.69.1; older through UTP core gRPC 1.57.2. Records span HTTP parsing, HTTP/2, decompression, SSL and Windows environment-file issues. The Windows advisory is a concrete reason that absence of a product HTTP server does not settle build applicability. [S54][S54] | **Investigate each retained advisory and qualify parent updates** (WP0a). Identify actual pipeline handlers, native engines, network bindings and input trust. Do not globally force every Netty module or change product network code. Two classpaths require independent post-change verification. |
| `commons-compress:1.21`, Android tools repository/sdk-common/sdklib | CVE-2024-26308 and CVE-2024-25710; Apache confirms broken Pack200/DUMP parsing paths and 1.26.0 fixes. [S55][S55] | **Investigate actual archive formats and trust**, update owning tools. APK ZIP use alone does not prove these paths execute. Require bounded malformed-input tests in disposable tooling fixtures if reachable. |
| `jdom2:2.0.6`, Jetifier processor 1.0.0-beta10 in AGP buildscript | CVE-2021-33813, SAXBuilder XXE; maintainer release 2.0.6.1 is retained as the upstream fix reference. Source declares no direct JDOM use. [S56][S56] | **Investigate whether Jetifier transformations run**, XML parser settings and input provenance; prefer parent update or retirement of an unneeded transform. No app dependency should be added to override a plugin classpath. |
| `jose4j:0.9.5`, bundletool 1.18.1 in AGP buildscript | GHSA-3677-xxcr-wjqv / CVE-2024-29371: OSV range match for compressed JWE denial of service. Maintainer issue/release pages could not be retrieved in this review; raw references are retained. | **Investigate further**, including primary fix confirmation and bundletool's JWE call paths. No claim of project exploitability or verified safe target version; do not promote the database match into a proven production issue. |

These results refine the initial recommendation: keep a narrow AGP 8 migration **conditional**, not automatically approved. Fresh resolution establishes what is selected; it does not establish that retaining AGP 8's tool dependencies is acceptable. WP0a must either qualify fixes, substantiate non-reachability/mitigation with approval, or bring the relevant part of WP9 forward. No exploit PoC was run, no build host compromise was established, and no security finding was silently suppressed.

## 8. Inputs the security-crypto proposal must incorporate

The recommendation remains **stable DataStore with an independently reviewed Android Keystore encryption serializer for small protected records**, plus the separately approved blob strategy. DataStore is not encrypted by default, does not provide cross-file atomic cutover, and does not define security recovery policy. Its per-file transaction/durability API is useful but is not proof of device power-loss behavior. [DataStore guide][S33], [DataStore API][S47]

As of this review, stable DataStore is **1.2.1**; `datastore-tink`/`AeadSerializer` belongs to the **1.3 alpha** line (introduced alpha07, latest listed alpha11). The alpha10 change mapping crypto failures to `CorruptionException` is particularly relevant to unsafe empty-default corruption handlers. Do not mix alpha API documentation into a stable 1.2.1 implementation. [DataStore releases][S09a]

The following are **proposed additions/clarifications for the next revision**, not edits made to the proposal:

| Proposal decision/section | Dependency or compatibility input to record | Acceptance evidence |
|---|---|---|
| D1 / implementation slices | Schedule storage replacement independently of M7 invariant 6; a separate exception is needed if new persistence is pulled into M7. Wrapper remediation does not authorize new security stores. | Accepted scope/ADR linkage; no implied residual closure |
| D2 / serializer and build | Freeze a concrete KGP/Compose compiler/KSP/AGP/Gradle tuple before introducing serializer generation. Protobuf Gradle plugin, protoc and lite runtime must be mutually compatible; documentation example versions are not approved dependencies. Strict JSON would add its own runtime/compiler-plugin compatibility checks. | Full processor/runtime graphs, immutable schema fixtures, debug/release R8 builds; no duplicate incompatible protobuf runtime |
| D2 / newly resolved tool dependencies | Current UTP selects Protobuf 3.24.4 with CVE-2024-7254 matches; current KGP also has the KAPT-cache range match. These are build/test findings, not proof a proposed DataStore serializer is affected. A new serializer must not copy those versions as approved defaults. | Independently qualified generated-code/runtime pair, advisory disposition, and toolchain decision under §7.3; any KAPT introduction reopens its applicability assessment |
| D2/D13 / new libraries | Inventory DataStore core/Android modules, serializer dependencies, Tink if chosen, annotations/coroutines/protobuf, their licenses and advisories. Choose stable APIs deliberately. | Version catalog additions each have purpose/disposition; no accidental 1.3 alpha resolution or unreviewed coroutines uplift |
| D4/D6/D8 / legacy key lifecycle | All four current MasterKey builders may share the default alias. Legacy Security also brings Tink/keysets; partial migration or blob retirement does not authorize alias deletion. A still-supported skipped upgrade needs a working legacy decoder. | Dependency/alias ownership map, supported source-version fixtures, retained-key tests and final decoder removal proof |
| D5/D11 / async integration | Compiler/coroutines/Hilt changes can affect initialization/cancellation even before storage changes. Preserve elapsedRealtime injection, one store per file, and explicit FIFO awaiting each suspended write. A single-thread dispatcher alone is insufficient. | Runtime responsiveness, admission freshness, exactly-once result accounting and immediate in-memory reset regression evidence; consume F2 contract rather than redefine it |
| D8/D10 / SQLCipher | Qualify library update separately from wrapping-key migration and schema migration. Preserve **the existing hex string's bytes passed to SQLCipher**, not hex-decoded original random bytes. Current DB recovery can move aside/rebuild an unreadable DB. | Upgrade fixtures prove exact-passphrase/database continuity; migration/key failures do not enter rebuild path. Existing R-004 closure is not proof of this stronger claim. |
| D6 / M8 removals | CameraX retirement and blob-key retention have different gates. Uninstalling a dependency does not settle existing encrypted blob disposition. | Approved preserve/retire policy for installed cohorts, crash-safe index/blob handling, no premature key deletion |
| D9 / API and device scope | Build 26–36 differs from written v1 acceptance 30–35. New AndroidX/Compose candidates may demand API 37/AGP 9.2; this must not silently expand the storage migration's platform change. | Frozen coverage matrix including missing 31/32/34 lanes and real Keystore/native-device evidence |
| D3/D10/D12 / failure handling | Missing records, wrong keys, restore/corruption and new-format read faults must remain typed failures. New dependency defaults/handlers must not generate a DB key or reimport stale legacy security state. | Proposal's kill, reset, restoration, and authoritative-store tests, with negative checks for forbidden key/reset/import paths |
| §14 removal gate | Include release graph, native/JAR/APK inspection and supported upgrade-window closure. An unused direct alias can still arrive transitively; code removal alone is insufficient. | `dependencyInsight`, resolved SBOM and packaged-class inventory, passing legacy migration fixtures or explicitly ended support |

The stable Security 1.1.0 release can be evaluated as a **time-limited bridge** if replacement lead time or a demonstrated compatibility fix justifies it. It does not restore maintenance. A bridge requires opening actual alpha06-generated preferences/files on devices, comparing state, process-death/restart coverage, and a separate record of changed transitive selection. Do not combine that bridge, a forced Tink uplift, new storage format, and database engine upgrade into one unreviewable change.

## 9. Prioritized, reviewable implementation work packages

**Implementation and candidate qualification in this section are future work.** WP0's fresh Maven resolution and advisory lookup have been performed as recorded in §2; its remaining assurance gates are still open. Priorities indicate sequencing/impact, not CVSS scores. Each slice should name its before/after graph and affected requirement evidence. Retain the previous qualified build and synthetic upgrade fixtures before changing a dependency. Do not use production credentials or log key/verifier values. Separate dependency changes from unrelated feature refactors.

### WP0 — P0: establish a trustworthy baseline and complete advisory inventory

**Files/interfaces:** catalog/build/settings/wrapper; CI artifacts; dependency-verification/locking configuration if adopted; a new dated evidence report/SBOM. No product API migration.

**Prerequisites and steps:** obtain authorized repository access or a complete trusted cache; run C1/C2 below for baseline release, debug, JVM test, instrumentation, processor, and plugin graphs. Record selected versions, reasons, repository origins, JDK/SDK/host, commit and artifact hashes. Include all eight variants where they differ, plus native AAR contents. Reconcile every catalog item and CSV coordinate. Choose and pin a maintained scanner/SBOM tool only after reviewing its own dependency/data handling; record advisory database date, licenses, reachability, false-positive rationale and owner for every result. Export Dependabot status/backlog and actual CI action SHAs separately.

**Current completion / remaining verification:** the scoped Maven graphs, external hashes and OSV lookup are complete (§2.2), and the freshly generated license coordinates match the release graph (§2.3); use those artifacts instead of repeating failed offline reports. Repository-origin authentication, native SBOM, substantive license/notice review, actual CI-action SHAs and final advisory applicability/approval remain. Fail if required graphs have unresolved dependencies, unexplained substitutions/preview versions, unknown licenses, or unresolved security applicability. A successful lookup is not completion of those gates.

**Rollback/completion:** audit-only changes can be reverted without touching app data; verification metadata must come from independently checked trusted origins, not a compromised bootstrap. Complete when graphs, licenses and advisories have reproducible provenance and dispositions. This is the entry gate for selecting final target versions, not a reason to postpone narrow advisory mitigation indefinitely.

### WP0a — P0 triage / P1 qualification: resolved build-tool advisories

**Affected files/interfaces:** candidate changes would be in catalog/root build/plugin management/CI cache policy, not application `implementation` dependencies. Evidence tools and reports retain both root buildscript and UTP configurations. Start from the 21-coordinate/51-advisory ledger in §7.3; assign an accountable role and a decision date before implementation, and close applicability before G5. No owner acceptance or deadline agreement is fabricated by this review.

**Implementation slices:** (1) establish whether KAPT tasks/cache readers exist in local and CI task graphs and included builds; inventory cache writers/readers and trust boundaries. Preserve KSP-only operation. If the path is absent, record that evidence and a recheck trigger; otherwise qualify a fixed KGP 2.4.20+ tuple with WP9's prerequisites. Do not assert that disabling Gradle's build cache disables every incremental cache. (2) Evaluate supported AGP/UTP parent releases for Netty, Protobuf, Commons Compress, build BC, bundletool/jose4j and Jetifier/JDOM fixes. Record selected versions in each classloader/configuration after updating. (3) Where parent releases cannot qualify promptly, compare documented input/binding mitigations against a narrowly scoped build dependency override. Any override needs upstream API/binary compatibility evidence and an expiration/removal trigger; never apply a root-wide forced version solely to silence a scan.

**Tests/pass criteria:** rerun graph+OSV commands and classify every remaining record; validate clean and incremental compilation, Hilt/Room processing, archive/resource transforms, bundle/release signing and actual UTP instrumentation on disposable devices. For a reachable parser/network/cache condition, use a bounded maintainer regression fixture in isolated tooling and demonstrate rejection without a hang, unexpected network access or untrusted deserialization. Review exposed gRPC ports, native SSL engines and Windows file lookup separately; do not execute unbounded memory-exhaustion examples on the working host. Passing means a fixed selected component or a substantiated, approved non-reachable/mitigated condition with a recheck trigger, plus unchanged build/test behavior. Absence from the APK alone fails this gate.

**Rollback/risks:** keep the previous toolchain and graph, revert parent/override/cache changes as one reviewed slice if compatibility fails, and retain an effective mitigation or reopen the security blocker. Upgrading app BC or adding a runtime Netty pin does not change a plugin's isolated classpath. Do not delete caches or change CI trust policy during this review; those are proposed remediation steps.

### WP1 — P0: patch wrapper exposure and qualify repository integrity

**Files:** wrapper properties/JAR/scripts; `settings.gradle.kts`; proposed `gradle/verification-metadata.xml`; CI wrapper verification and relevant build documentation. Keep AGP initially 8.13.2.

**Steps:** qualify Gradle 8.14.4, regenerate wrapper artifacts from a trusted distribution, and add its independently verified published SHA-256. Constrain repository ownership using the actual WP0 graph; test plugin markers and AndroidX/Google artifacts before tightening filters. Enable reviewed dependency verification, covering processors/plugins as well as runtime. Evaluate dependency locking separately; locks control selected versions, while verification controls accepted bytes.

**Breaking risks:** stricter resolution can expose artifacts previously obtained from an unintended repository; wrapper/tool versions can affect GMD, cache behavior and plugin APIs. Run C1–C4 on CI JDK 17 and the supported Windows fleet setup, including clean-cache verified resolution. In a disposable test repository, exercise an unavailable/unknown-host source and checksum mismatch: resolution must fail visibly, not accept an unintended substitute. Do not simulate this by redirecting real production repositories to an untrusted host.

**Rollback/exit:** revert the whole wrapper cohort if incompatible, while retaining a proven advisory mitigation; returning to unmitigated 8.13 is not closure. If the existing Kotlin mismatch blocks qualification, land repository verification as an interim mitigation and coordinate WP3. Exit requires recorded advisory disposition, verified wrapper/graph integrity, full release build and unchanged app regression results. [Wrapper procedure][S42]

### WP2 — P1: supported license reporting and reliable static checks

**Files:** catalog, root/app build, `config/detekt/`, lint baseline only for individually reviewed entries, `ArchitectureRulesTest`, workflow artifact paths.

**Slices:** first upgrade license-report 2.9→qualified 3.x (candidate 3.1.4), keeping the release notices scope. Compare coordinates and license attributions against WP0; explicitly report new/removed/missing notices. The maintainer supports Gradle 7–9 on 3.x but documents configuration-cache limitations and Gradle 9 parallel-resolution concerns; validate actual project invocation, with `--no-parallel` for diagnosis if necessary. Second, upgrade detekt and formatting together 1.23.7→1.23.8. Its published compiled-against Kotlin/AGP versions are not a promise that every newer language construct parses. Do not blindly align detekt's embedded compiler to the application classpath. [License plugin][S29], [detekt compatibility][S27]

**Verification:** C3/C4, compare warning counts and rule identities, and run architecture tests with a temporary negative fixture that intentionally violates a rule. The violation must be detected, then remove the fixture and rerun. Investigate parser exceptions or silently skipped files; never solve them by disabling rules globally or regenerating baselines. Konsist stays 0.17.3 unless evidence requires replacement.

**Rollback/exit:** revert tooling pair/config together; archive failing reports. Exit: notices match selected shipping artifacts, original architecture violations remain detectable, and no unreviewed baseline expansion. Defer detekt 2 alpha; if stable cannot analyze the selected Kotlin, resolve that explicit toolchain gate before approving WP3, rather than silently accepting partial analysis.

### WP3 — P1 prerequisite: Kotlin, Compose compiler, KSP2, Hilt and Room processing

**Files:** catalog, root/app build, Gradle properties, generated-source configuration; DI modules/annotations or DAOs only where real compiler diagnostics require changes. Existing generated code should be regenerated, not hand-edited.

**Sequence:** (a) migrate `kotlinOptions` to typed compilerOptions while preserving JVM 17 and existing flags; (b) after WP0a's KAPT-cache applicability decision, evaluate conditional KGP/Compose plugin 2.3.21 + KSP 2.3.12 under AGP 8.13.2/Gradle 8.14.4, or advance the fixed 2.4.20+ compatible lane; (c) in the narrow lane, align Hilt runtime/compiler/plugin on candidate 2.58; (d) move Room runtime/compiler together to a KSP2-qualified release, candidate 2.8.5, carrying WP5c's database tests. These are coordinated qualification slices, not independent Dependabot merges. Keep the current UI BOM at first. Confirm processor configurations actually use KSP2 and record/remove overriding KSP1 settings only after qualification.

**Breaking risks:** Kotlin DSL removals, generated nullability/signatures, Room Kotlin generation and DAO validation, Hilt binding-graph validation, and changed R8 behavior. Fix incorrect component scoping rather than disabling Hilt validation indefinitely. Preserve `AppModule`'s storage singleton, clock binding and application startup order; component-name pinning still applies.

**Verification:** fresh/incremental C1–C4, all generated variants, architecture tests, shrunk release install, authentication/self-gate, service/overlay startup and database fixtures on Moto/NucBox. Inspect compiler warnings and processor output. Generated graph success is insufficient if eager I/O or ViewModel ownership changes.

**Rollback/exit:** revert compiler/plugins/processors/generated configuration as a tuple. No schema or credential-format change is authorized here. Complete only when documented matrices, actual AAR metadata, processing, runtime/device gates, and analyzer coverage agree. If this candidate fails, record the specific boundary and qualify another supported tuple; do not label the candidate “supported by the project” in advance.

### WP3b — P1/P2: coroutines runtime and test semantics

**Files:** `coroutines` alias, runtime/lockout tests and any genuinely affected coroutine calls in `LockEngineRuntime`, `LockoutManager`, repository flows and presenter lifecycle code.

After WP3, qualify Android/test 1.11.0 together. Record production and test graph selection, including AndroidX Test's transitive effects. Run deterministic tests for admission order, stalled writes, cancellation, shutdown, stale-result fencing and duplicate submissions using the existing approved expectations. Rerun the F2 compatibility suite; this work does not choose a new R-007 policy. Exercise production dispatchers/device threads as well as virtual time.

**Risks/exit:** changed test scheduling can reveal races or make a previously insensitive test fail. Diagnose interleaving rather than add arbitrary delays. Require no event-accounting/reset/order violation and no responsiveness regression beyond frozen budgets. Roll back both artifacts and compatibility edits together if needed; no persistent-format change is allowed.

### WP4 / WP4b — P2: Compose, AndroidX UI/lifecycle, Hilt accessor and icons

**Files:** UI/library aliases and dependency declarations; the four old Hilt imports listed in §7; `OverlayLockPresenter`, Activity/Compose hosts; local `res/drawable` assets and icon call sites; UI tests.

**Prerequisites/steps:** freeze an API 36-compatible UI cohort using actual AAR metadata/BOM mapping. **Hilt integration 1.3.0** is the initial artifact-move candidate. Do not select Hilt 1.4, Lifecycle 2.11 Compose or Compose UI 1.12 into the narrow lane. Earlier stable Core/Activity/Lifecycle/BOM candidates must still pass metadata checks; exact final cohort remains open because candidate versions have not been resolved and qualified; the current baseline graph is complete. If no satisfactory maintained cohort exists, defer that update or approve WP9. Change lifecycle-runtime-ktx to base runtime and align ViewModel integration. Update the BOM in both production and AT configurations, inspect each selected module, and resolve only APIs deprecated in that selected release. [BOM mapping][S48]

Move `hiltViewModel` to `androidx.hilt.lifecycle.viewmodel.compose`; explicitly preserve the same ViewModelStoreOwner/key for Activity and Compose access to AuthGateViewModel. Treat icon replacement as a separate small slice: inventory retained glyphs, add reviewed offline vector resources, preserve semantic descriptions/RTL mirroring, replace imports and then remove the extended-icons dependency. Avoid redesigning all screens to replace an artifact.

**Breaking risks/tests:** changed insets, back handling, focus, touch bounds, lifecycle disposal, text scaling, and test semantics. C3–C5 plus TC3 below must prove self-gate/overlay/legacy lock behavior, biometric cancellation/success, screen-off/dismiss, accessibility and RTL. Record screenshot comparisons where visual behavior matters, but verify input/security outcomes too.

**Rollback/exit:** revert cohort+imports/resources together while keeping app identity and persisted formats. Exit: no orphan old Hilt imports/unused Navigation path, one correct auth ViewModel owner, no input leakage/session regression, accepted visual/performance evidence. Do not count merely compiling as completion.

### WP5 — P1/P2: cryptographic and database libraries, in three slices

**WP5a, Bouncy Castle:** update only `bouncycastle` initially, candidate 1.86. Review Android-compatible bytecode and transitive changes, retained packaging exclusions and R8 diagnostics. Exercise `Argon2PinHasherTest`, `PinHasherTest` and synthetic installed credential records using all supported stored algorithms/parameter variants; preserve verifier/salt/algorithm bytes and valid/invalid PIN outcomes. Measure wall time, allocation/peak memory and repeated attempts on Moto and emulator API floor. Do not change KDF parameters during a dependency-only update. Add bounds/tamper cases only through a reviewed behavior slice if missing. Exit requires byte-for-byte known-answer agreement, historical credential compatibility, adjudicated relevant advisories, no OOM/latency regression. Roll back library/API edits if fixtures remain format-compatible; never “fix” by resetting credentials.

**WP5b, SQLCipher:** qualify 4.6.1→4.19.0 after checking the published AAR and release notes. Files: catalog, `AppLockDatabase`, SQLCipher factory/load calls only if required, native packaging/ProGuard and DB tests. Preserve exact passphrase bytes, schema and SQLCipher settings; do not combine rekey, plaintext migration, new wrapping format or Room schema change. Run old-version-created DB/WAL fixtures and fresh installs through the actual production factory, query known rows and integrity, close/reopen after writes, process-kill/reboot and inspect encrypted files. Instrument or assert that a supposedly successful library upgrade did not enter `recoverAndRebuild`; an empty rebuilt database is a failure. Audit SQL export/URI/raw-query paths for the advisory conditions. Check ELF and APK/AAB alignment and run on a confirmed 16 KiB-page Android environment as well as real Moto arm64 and NucBox x86_64. [Page-size guidance][S44]

**WP5b rollback/exit:** test reopening candidate-written files with the previous binary before allowing rollback; copy consistent **closed synthetic** fixtures, never blindly copy a live WAL database. If compatibility fails, use a forward-compatible rescue build rather than data deletion. Exit: exact-key continuity, all rows/schema/WAL intact, no silent rebuild, applicable advisories resolved, release native load/alignment/page-size evidence and acceptable latency. Current 4.6.1 page-size incompatibility was **not** established by this review.

**WP5c, Room/SQLite:** pair runtime/compiler update with WP3 when processor compatibility demands it; test SQLCipher engine separately whenever feasible. Remove Room KTX only after 2.7+ and replace direct SQLite KTX with base `androidx.sqlite:sqlite` after extension audit. Room 2.8.5 changes operations after close to throw; test late Flow/coroutine cancellation during shutdown. Keep `SupportOpenHelperFactory`; adopting a bundled/platform SQLite driver accidentally could bypass encryption. No Room 3/KMP driver migration is needed. Compare schema exports/migrations, transaction rollback, invalidation, concurrent reads/writes and existing recovery behavior. Roll back coherent runtime/compiler/SQLite selection only when on-disk schema remains compatible. Exit: DAO generation and encrypted integration work on the supported floor, with no destructive-schema fallback added.

### WP6 — P2, aligned with M8: remove unused and excluded-feature dependencies

**Files:** catalog/build, unused preview declaration; Compose test-manifest declaration if proven unused; at M8, CameraX/capture code, Hilt providers, manifest CAMERA declarations, vault/intruder screens/repositories and governed schema/blob disposition.

First remove `ui-tooling-preview` only after confirming preview tooling is not a retained requirement. Prove `ui-test-manifest` has no test-host dependency by inspecting the merged debug/AT manifests and running both launch tests; empty-rule usage is a lead, not conclusive proof. Keep `ui-test-junit4`.

For M8, follow the approved feature-removal package and existing-data policy, then remove the three CameraX modules together. Do not upgrade a soon-removed camera stack without a specific defect/security reason. If capture must remain through a delayed M8 or supported old cohort, qualify the aligned stable CameraX family in its own change: permission denial, capture/open/close, backgrounding, device rotation, concurrent lifecycle teardown, native/bitmap memory and encrypted output on physical hardware.

**Exit/rollback:** C1/C3/C4 plus package/manifest inspection prove excluded permissions, entry points and libraries are absent and retained flows pass. Data deletion/schema retirement requires its own restore/rescue policy; restoring Gradle declarations cannot restore deleted user blobs. This package must not delete a shared Keystore alias still used by credentials, lockout, DB key, or supported legacy readers.

### WP7 — P1 architectural priority, separately scheduled: replace security-crypto

**Files/interfaces:** the four security adapters, `AppModule`, `AppLockApplication`, database bootstrap, debug/release lockout factories and inspector, synchronous authentication callers, storage ports, new coordinator/serializer/records, manifest/backup rules, catalog/build and qualified migration fixtures. Exact slices and migration protocol are owned by [the proposal §§11–14](../../process/proposals/2026-09-24_SECURITY_CRYPTO_REPLACEMENT_PLAN.md), not redefined here.

Resolve D1–D13 and §8 compatibility inputs first. Add stable dependencies and isolated serializer/key tests; qualify authority/cutover migration before wiring normal callers; then run the proposal's full boundary and real-device matrix. Keep old readers/keys for every supported source version and dependent store until removal is proven safe. Any alpha06→stable 1.1.0 bridge must be separate and time-limited. Implement explicit backup exclusions while preserving `allowBackup=false`, with restore behavior under D3/D12.

**Exit:** exact credentials/SQLCipher key preserved; every migration kill boundary recoverable with one authority; no legacy fallback after cutover/read failure; reset not reversed by reimport; missing keys/corruption/restore follow approved recovery; no main/event-loop blocking or ordering/accounting weakening. Retain device evidence and obtain format/security review before removing the dependency. Rollback after cutover needs a new-format-aware rescue build, not an old binary selecting stale prefs. This report does not claim any of those tests passed or close R-007.

### WP8 — P2: test dependencies and CI actions/gates

**Files:** AndroidX test aliases/declarations, instrumentation fixtures and runner arguments, `.github/workflows/ci.yml`, Dependabot grouping/pinning. Separate this from product behavior changes so test-oracle drift is visible.

Qualify Core 1.7.0/ExtJUnit 1.3.0/Runner 1.7.0, then UIAutomator 2.4.0, under the selected Kotlin/coroutines and Compose test cohort. Confirm discovery counts, annotation filters, ActivityScenario cleanup, empty Compose rules, cross-window node/input behavior, barrier scripts and logcat/PID evidence. Preserve a baseline APK/test APK pair to compare old/new harnesses against deliberately failing and passing fixtures. A skipped overlay assertion is not a pass.

Update GitHub actions one family at a time after reading the selected major's runner/Node and workflow migrations; retain JDK 17 unless the toolchain decision changes. Pin reviewed full commit SHAs with readable version comments and updater support. Verify checkout credentials/PR handling, Java caching, Gradle cache trust and artifact permissions/retention/names on PR and main runs. Current upstream major observations are not a requirement to choose all newest releases immediately. [Action releases/security guidance][S43]

**Exit/rollback:** comparison shows genuine test discovery and the same security oracle; artifacts survive failures; required release device lanes fail the gate on failure. Make instrumentation gating mandatory only with recorded rig stability and intentional separate heavy campaigns. Revert workflow/actions/test cohort if regressions arise without reverting product data. Do not eliminate flaky security assertions to obtain green CI.

### WP9 — deferred: AGP 9/Gradle 9/Kotlin 2.4/API 37 generation

**Reason for conditional deferral:** this changes several toolchain/SDK/Compose/DI boundaries during M7. It is not necessary merely to patch the Gradle 8 advisory, but fresh KGP/AGP-transitive advisory findings may require earlier work. Advance the necessary supported subset if WP0a cannot substantiate an acceptable narrow cohort; do not assume all API 37/targetSdk behavior changes are required merely to obtain a compiler fix.

**Practical path:** build a reviewed tuple from AGP/Gradle/KGP/R8 and Hilt matrices, including upper tested ranges rather than only minimums. Update wrapper/AGP together, follow built-in Kotlin migration (remove Android Kotlin plugin application/alias where appropriate, preserve Compose compiler integration, migrate typed DSL/source-set APIs), qualify current KSP2/Hilt9-compatible releases, then API 37-compatible AndroidX/BOM. Keep targetSdk behavior changes separate from a compileSdk increase unless explicitly approved. Revisit GMD experimental flags and third-party Gradle plugins. [AGP 9 migration][S36]

**Tests/exit/rollback:** all C1–C5 and TC1–TC8 relevant to changed components, clean/incremental Windows/Linux builds, R8 shrinker and API 36/37 device regressions. Roll back the whole toolchain cohort; no automatic storage-format migration belongs here. Exit is an approved tuple with fresh evidence, not “latest versions installed.” Also defer Room 3, SQLCipher 5 beta, DataStore 1.3 alpha encryption, detekt 2 alpha, new authentication SDKs and JUnit5 conversion absent a reviewed need.

## 10. Verification commands, test cases, fleet, and release gates

### 10.1 Reproducible command sets — qualification recipes

Run candidate qualification from repository root with a recorded JDK 17 toolchain, SDK installation and controlled Gradle/Android homes. §2 records the actual baseline commands/results, including their JBR 21 limitation; these recipes are not a claim that every command below ran. Save command, stdout/stderr, exit code, timestamp, commit, graph and APK hashes. Do not add `--offline` unless the cache is demonstrably complete. A command exiting 0 does not override unresolved dependencies or skipped test assertions.

**C1 — graph and selection evidence:**

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:dependencies --configuration prodReleaseRuntimeClasspath --console=plain
.\gradlew.bat :app:dependencies --configuration prodDebugRuntimeClasspath --console=plain
.\gradlew.bat :app:dependencies --configuration prodDebugUnitTestRuntimeClasspath --console=plain
.\gradlew.bat :app:dependencies --configuration prodDebugAndroidTestRuntimeClasspath --console=plain
.\gradlew.bat buildEnvironment --console=plain
.\gradlew.bat :app:buildEnvironment --console=plain
.\gradlew.bat :app:resolvableConfigurations --console=plain
```

Use the last inventory to select actual resolvable processor/plugin configurations; do not assume the declaration-only `ksp` bucket is resolvable. Inspect the **root** buildscript graph as well as the empty app buildscript. The retained invocation-only audit and [reproduction commands](2026-09-25_dependency-deprecation/EVIDENCE.md) automate the scope used here. Repeat for differing flavors/build types. Record BOM constraints and selected versions for **all six** managed aliases.

**C2 — selection reasons:**

```powershell
.\gradlew.bat :app:dependencyInsight --configuration prodReleaseRuntimeClasspath --dependency kotlin-stdlib --console=plain
.\gradlew.bat :app:dependencyInsight --configuration prodReleaseRuntimeClasspath --dependency security-crypto --console=plain
.\gradlew.bat :app:dependencyInsight --configuration prodReleaseRuntimeClasspath --dependency tink --console=plain
.\gradlew.bat :app:dependencyInsight --configuration prodReleaseRuntimeClasspath --dependency kotlinx-coroutines --console=plain
.\gradlew.bat :app:dependencyInsight --configuration prodDebugAndroidTestRuntimeClasspath --dependency kotlinx-coroutines --console=plain
```

Repeat targeted insight for Room/SQLite, Hilt/Navigation, Compose and each unexpected selection. Inspect AAR metadata and native/JAR contents as well as text graphs. Produce a machine-readable audit inventory with licenses and advisory dispositions; the license plugin alone is not a vulnerability scanner.

**C3 — code and static checks:**

```powershell
.\gradlew.bat :app:checkProdReleaseAarMetadata :app:checkProdDebugAndroidTestAarMetadata --console=plain
.\gradlew.bat :app:testProdDebugUnitTest :app:detekt --console=plain
.\gradlew.bat :app:compileProdDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:lintProdDebug :app:lintProdRelease --console=plain
```

For WP5a isolation, use `:app:testProdDebugUnitTest --tests com.applock.security.Argon2PinHasherTest --tests com.applock.security.PinHasherTest`, followed by the full suite. Preserve baseline-filtered diagnostic counts; handle new warnings individually. Discover any variant-specific task-name changes after a toolchain migration with `:app:tasks --all`, and record the actual replacement command.

**C4 — packaging/notices:**

```powershell
.\gradlew.bat :app:assembleDevDebug :app:assembleProdDebug :app:assembleProdRelease --console=plain
.\gradlew.bat :app:generateLicenseReport --console=plain
```

Also build the actual release bundle when that is the distribution artifact. Inspect the shrunk APK/AAB's manifest, classes, native ABIs and licenses. For native updates, use Android's current page-size/ELF/zip alignment procedure; record `adb shell getconf PAGE_SIZE` on the test environment instead of assuming its page size. Install a correctly signed release-equivalent artifact for release-only R8 paths, without replacing a personal installation.

**C5 — device harness:**

```powershell
.\gradlew.bat ciGroupProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.applock.e2e.OverlayRaceTest,com.applock.r007.R007DeviceTool --console=plain
.\gradlew.bat fullGroupProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.applock.e2e.OverlayRaceTest,com.applock.r007.R007DeviceTool --console=plain
.\gradlew.bat connectedProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.applock.e2e.OverlayRaceTest,com.applock.r007.R007DeviceTool --console=plain
```

These are smoke lanes only. Execute the existing overlay race, biometric, HOME resolution and R-007 campaigns with their documented selectors and prerequisites separately. R007DeviceTool includes purpose-specific device tooling; indiscriminately running every class is not a campaign. Use disposable production-graph installs and scripted fixtures for persistence/fault work; do not clear an existing user's data.

### 10.2 Test cases and explicit acceptance criteria

All cases below are **required future evidence**, not results. Select cases per work package; broad suite reruns follow the affected contracts, rather than treating every dependency bump as unrelated.

| ID / setup and action | Fault/change or boundary | Pass/fail criterion | Evidence |
|---|---|---|---|
| TC1: trusted clean cache plus baseline/candidate build on Windows and CI Linux/JDK 17; resolve every needed configuration | Wrong checksum, unavailable repository, changed selected versions; compile clean then incremental | No unresolved nodes, unexpected origin/version, duplicate incompatible processor/runtime or unapproved preview; tampered artifact rejected; all required builds produce artifacts | Full graphs/insight, verification configuration, environment, hashes, logs and build/diagnostic counts |
| TC2: current synthetic PBKDF2 and Argon2 credential fixtures plus known-answer vectors; verify correct/incorrect PIN on baseline/candidate | BC/compiler/R8 update; parameter variants; repeated submissions and low-memory device workload | Same verifier bytes/results; stored credentials remain usable; bounded timing/memory; no silent reset or changed KDF policy | Fixture IDs, assertion output, memory/latency distributions; never raw PINs/verifiers in general logs |
| TC3: self-gate, legacy lock UI and live overlay with protected target apps; same screen/input script | UI/Hilt/Activity/Compose update; rotation, background/foreground, biometric success/cancel/error, HOME/back, screen-off and dismiss | Auth ViewModel scope correct; no unauthorized session or touch-through; stale dismissed callbacks cannot authorize; protected UI/input and immediate reset contracts preserved | APK hashes, runner counts, event/session trace, UI video/screenshots, timings and exact device/OS |
| TC4: existing LockoutManager/runtime regression fixtures plus approved F2 debug wrapper campaign | Coroutines/DI/storage adapter changes; transient/persistent failure, stall, duplicate, deadline, cancellation/shutdown, kill/restart/reboot | Matches **approved F2 expected behavior/residuals**, with no extra writer, freshness/accounting violation or runtime blocking. Does not assert F2 residuals are solved by dependency updates | Existing case IDs, fault script/barriers, PID/sequence trace, fresh persisted-state inspection, comparison to frozen baseline |
| TC5: old-version synthetic encrypted DB with known rows/schema, same exact passphrase and closed/WAL variants | SQLCipher/Room upgrade; kill before/after transaction commit, reopen/reboot; concurrent query/write and shutdown | Expected committed rows survive, uncommitted transaction absent, schema compatible, no rebuild/reset/rekey; wrong key/corruption does not count as a successful migration | Assertion report, factory/recovery-path trace, file metadata and secured fixture comparison, integrity and native-library version |
| TC6: same native release artifact on Moto arm64, NucBox x86_64, and measured 16 KiB-page environment | Native load, page alignment, release R8 packaging and cold/warm DB open | All required ABIs load and encryption round trips work; ELF/ZIP alignment satisfies platform checks; no crash or unexplained performance regression | Artifact/ABI/ELF details, page-size command, platform check output, logcat and timings |
| TC7: proposal's legacy/fresh/mixed/corrupted/missing-key fixtures, installed upgrade and skip-version paths | Every proposal migration kill boundary, reset, post-cutover read failure, key loss, old-store presence, restore/transfer | Exact credentials/passphrase preserved; single recoverable authority; no stale fallback/reimport; keys retained until all dependents disposed; approved visible recovery instead of fresh security state | Proposal qualification IDs and complete kill ledger, independent fresh-process inspector, first-accessor PID proof, user-visible outcome; no blanket claim from JVM fake tests |
| TC8: baseline and candidate test APKs against known passing and deliberately failing fixture; CI PR/main runs | Runner/UIAutomator/Compose test/action upgrades, filter changes, missing target app or GPU rendering | Expected failures remain failures; zero unexplained skips/test-count loss; real input/assertions execute; artifacts retained; intended release lanes cannot fail green | Discovery counts, filters, skipped reasons, sentinel failure output, CI run/runner/action SHA and uploaded artifacts |

### 10.3 Fleet and measurement gates

Use existing CI GMD **30/35/36** for quick platform regression, NucBox full GMD **26/29/30/33/35/36** for declared build compatibility, and **Moto G API 35 arm64** for real Keystore/native/lifecycle/persistence behavior. Confirm current device/OS inventory before scheduling; these are known project lanes, not devices contacted during this review. The existing API 26 GMD caveat and API 36 software-GPU rendering issue require explicit rig disposition, not automatic skip acceptance.

Written v1.0.0 acceptance spans **30–35**, including **31/32/34**, which the current named GMD groups do not all cover. Obtain appropriate extra emulator/physical/cloud lanes or record an approved release-coverage decision. Include a verified 16 KiB environment for SQLCipher. If WP9 changes platform scope, add the new API/ABI lanes before acceptance. NucBox emulator results cannot establish Moto Keystore or OEM backup behavior.

Before comparisons, freeze baseline distributions and tolerances for cold/warm startup, PIN verification, DB open/query, UI/input response, event-loop admission, screen-off/dismiss, memory/GC, APK/native size and build time. Use repeated identical workloads on the same device with warmup/thermal state recorded; report sample counts and p50/p95/max, errors and ANRs. Apply approved project/F2 thresholds unchanged to touched contracts. Set previously unspecified dependency-specific budgets **before** seeing candidate results; deviations require a recorded reason, not retrospective loosening. Migration startup and KDF costs must not be averaged into a number that conceals stalls in the runtime event loop.

| Gate | Required outcome |
|---|---|
| G0 — evidence | Complete fresh graph/processor/plugin/native inventory; catalog 66/66 accounted for; declared/resolved/packaged distinctions retained |
| G1 — compatibility | Supported reviewed version tuple and AAR metadata; clean/incremental debug/release builds, analyzer coverage and controlled baseline changes |
| G2 — behavior | Relevant unit/instrumentation/security tests pass with expected discovery/skip counts; no contract regression or unapproved scope change |
| G3 — real platforms | Required fleet, release shrinking and native 16 KiB evidence; process-restart/reboot/Keystore/restore claims backed by platform observations |
| G4 — persistent data | Installed-user key/credential/schema compatibility and rollback/rescue demonstrated; WP7's full proposal qualification before format cutover/removal |
| G5 — dependency/release governance | Licenses/security status for each included dependency, no unresolved Critical, High affecting distributed app corrected/removed, EOL/deferred-feature dependencies removed per active NFR; build-tool vulnerabilities dispositioned as well |
| G6 — completion record | New dated evidence report cites commit/artifact/test/CI/device results, updated risk/requirement/ADR state where actually changed, owner and due date for residuals, and removal/deferral decisions |

A passing build, lint report, scanner, or emulator suite alone cannot satisfy all six substantive gates. This review supplies inspection, scoped resolution, advisory lookup and baseline execution evidence, not G1–G5 completion for any proposed upgrade.

## 11. Primary-source register and verification dates

**All sources below were checked on 2026-09-25.** Release dates are included where the inspected page clearly establishes them; a living API/matrix page is identified as such rather than assigned an invented publication date. Links in the inventory resolve to these primary sources. Recheck them at version freeze: this report is a dated review, not a promise about future support or releases.

| ID | Primary source and dated fact used |
|---|---|
| S01 | [Gradle security advisory][S01], published 2026-01-16: CVE-2026-22816, affected/fixed ranges and mitigations |
| S02 | [AGP 8.13 release notes][S02]:8.13.2, Gradle/JDK/API compatibility and R8 version |
| S03 | [Kotlin Gradle compatibility matrix][S03], living documentation: KGP 2.1 and 2.3.21 supported ranges |
| S04 | [Android Kotlin support table][S04], living documentation: Kotlin 2.3/2.4 D8/R8/AGP requirements |
| S05 | [Kotlin compiler options migration][S05] and [Kotlin 2.2 changes](https://kotlinlang.org/docs/whatsnew22.html): kotlinOptions deprecation/escalation and typed DSL |
| S06 | [KSP releases][S06],2.3.12 dated 2026-09-09; [KSP maintainer README](https://github.com/google/ksp): KSP1 status; [pinned release](https://github.com/google/ksp/releases/tag/2.1.0-1.0.29) for historical context |
| S07 | [Dagger/Hilt releases][S07]:2.58 vs 2.59 AGP boundary and 2.60.1 observation;2.59 dated 2026-01-21 |
| S08 | [Jetpack Security release notes][S08]: deprecations in 1.1.0-alpha07 (2025-04-09), stable 1.1.0 (2025-07-30) |
| S09 | [Android cryptography guidance][S09]: Security retirement/no subsequent library releases and platform alternatives |
| S09a | [DataStore releases][S09a]: stable 1.2.1 (2026-03-11),1.3.0-alpha11 (2026-09-09); alpha-only encryption integration and error-handling changes |
| S10 | [Core releases][S10]:1.19.1 observed stable; Kotlin-related requirements and compatibility history |
| S11 | [Lifecycle releases][S11]:2.11.0 stable, KTX consolidation and Compose API 37/AGP 9.2 requirement introduced during 2.11 prereleases |
| S12 | [Activity releases][S12]:1.13.0 (2026-03-11); back/edge-to-edge/Activity behavior changes |
| S13 | [Camera releases][S13]:1.6.2 (2026-08-26), ongoing maintenance |
| S14 | [Room releases][S14]:2.8.5 (2026-09-09), API floor, KTX merge, KSP/Kotlin and close-time behavior |
| S15 | [SQLite releases][S15]:2.7.1 (2026-09-09); KTX consolidation recorded in 2.7.0-alpha03 (2026-04-08) |
| S16 | [Biometric releases][S16]: stable 1.1.0 remains listed separately from 1.4 prereleases |
| S17 | [AndroidX Hilt releases][S17]:1.3.0 (2025-09-10) artifact move;1.4.0 (2026-07-01) and its newer KGP/Compose build floor |
| S18 | [Old hiltViewModel API reference][S18], living API: deprecated package and replacement |
| S19 | [Compose compiler Gradle plugin guide][S19], living guide: Kotlin compiler/plugin setup independent of library BOM |
| S20 | [Android Compose icons guidance][S20], living guide: material-icons maintenance status and Material Symbols/vector alternatives |
| S21 | [Compose BOM guidance][S21], living guide:2026.09.00 example, BOM scope/limits |
| S22 | [Bouncy Castle Java downloads][S22]: bcprov-jdk18on 1.86 (2026-09-11); do not confuse with other BC artifact releases |
| S23 | [Bouncy Castle security advisories][S23] and [maintainer advisory index](https://github.com/bcgit/bc-java/wiki): security review entry points, not a clean bill of health |
| S24 | [SQLCipher 4.19 advisory/release][S24],2026-09-08: two low-severity issues, provider matrix and updated behavior |
| S25 | [SQLCipher Android Community 4.19.0][S25]: Android package documentation; [maintainer release index](https://www.zetetic.net/blog/),5.0.0-beta is separate from stable 4.x |
| S26 | [kotlinx.coroutines releases][S26]:1.11.0 (2026-05-08), compiler dependency changes |
| S27 | [detekt compatibility table][S27]:1.23.7/1.23.8 and 2.0.0-alpha.6 compiled-against dependencies; these are not universal consumer guarantees |
| S28 | [Konsist maintainer releases][S28]:0.17.3 remains latest release observed; no assertion that low activity equals EOL |
| S29 | [Gradle License Report maintainer documentation][S29]:2.x EOL/3.x support and limitations; [releases](https://github.com/jk1/Gradle-License-Report/releases),3.1.4 dated 2026-06-10 |
| S30 | [JUnit4 maintainer README][S30] and [releases](https://github.com/junit-team/junit4/releases): maintenance mode and 4.13.2 |
| S31 | [AndroidX Test releases][S31]: Core/Runner 1.7.0 and ExtJUnit 1.3.0 (2025-07-30), dependency/floor changes |
| S32 | [UIAutomator releases][S32]: stable 2.4.0 (2026-07-01), harness API evolution |
| S33 | [DataStore usage guide][S33], living guide: one instance/file/process, immutable data and single/multiprocess restrictions |
| S34 | [Android Auto Backup guidance][S34], living guide: allowBackup and device-transfer/rule-format distinctions |
| S35 | [AGP 9.4 release notes][S35]: Gradle 9.6/JDK 17 and API 37 build support |
| S36 | [Built-in Kotlin migration][S36], living migration guide: AGP 9 plugin/DSL/source-set changes |
| S37 | [Gradle releases][S37]:9.8.0 dated 2026-09-24; latest is not this report's narrow-lane target |
| S38 | [Kotlin release history][S38]:2.4.20 dated 2026-09-07; not automatically compatible with current Android shrinking |
| S39 | [PackageManager API][S39], living reference: overloads and API-level availability |
| S40 | [AppOpsManager API][S40], living reference: checkOpNoThrow vs unsafeCheckOpNoThrow status |
| S41 | [Device administration overview][S41], living guidance: deprecated policies versus remaining API uses |
| S42 | [Gradle wrapper guide][S42] and [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html): checksum/trust procedures |
| S43 | [GitHub Actions security hardening][S43]; maintainer releases for [checkout](https://github.com/actions/checkout/releases), [setup-java](https://github.com/actions/setup-java/releases), [upload-artifact](https://github.com/actions/upload-artifact/releases), [Gradle actions](https://github.com/gradle/actions/releases). Observed stable versions:7.0.1,6.0.1,7.0.1,6.3.0 respectively; remote executed SHAs were not inspected |
| S44 | [Android 16 KiB page-size guidance][S44], living guide: native ELF/packaging and real-runtime verification |
| S45 | [BC 1.85 maintainer security release][S45]: OpenPGP Argon2 S2K CVE-2026-59648 context; not a direct PIN-hasher vulnerability finding |
| S46 | [Compose UI release notes][S46]:1.12 API 37/AGP 9.2 change and later alpha changes; preview-only deprecations are not all current-app findings |
| S47 | [DataStore API contract source][S47], official AndroidX mirror: transactional/durable update semantics and read failures. The generated Android reference exceeded the retrieval tool's page-size limit; the maintainer source was used instead. |
| S48 | [Compose BOM-to-module mapping][S48], living mapping: use with actual resolved graph; not evidence a local test dependency resolved |
| S49 | [OSV batch-query API][S49], checked 2026-09-25: package/version queries and pagination; [advisory retrieval API](https://google.github.io/osv.dev/get-v1-vulns/) for full returned records. Database matches are evidence of range matching, not primary maintainer confirmation of project exploitability. |
| S50 | [JetBrains-authored CNA record for CVE-2026-53914][S50], published 2026-06-26: pre-2.4.20 cache-deserialization issue. The live JetBrains issues page did not expose its entries to the retrieval tool, so its original CNA record was read. |
| S50a | [JetBrains KAPT cache fix][S50a], commit bf51df665b458fda7c3eaf436c4d88dc119d7ec6: restrict deserialized classes in KAPT incremental caches; supports the reachability distinction, not a blanket KSP security guarantee. |
| S51 | [BC GOST CTR advisory][S51] and [LDAP](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636), updated 2026-05-18; [PKIX name constraints](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%908763) and [lazy ASN.1](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%9013506), updated 2026-08-08. Maintainer algorithm/range/fix descriptions checked 2026-09-25. |
| S52 | [BC composite-signature advisory][S52], updated 2026-05-18: affected/fixed ranges and draft signature validation path. |
| S53 | [Protobuf CVE-2024-7254 advisory][S53], published 2024-09-18: Java/full/lite/Kotlin recursion issue and fixed branches. |
| S54 | [Netty Windows environment-file advisory][S54], published 2024-11-12, and the [maintainer advisory index](https://github.com/netty/netty/security/advisories). Individual returned IDs/references are retained in the ledger; full per-advisory reachability is outstanding. |
| S55 | [Apache Commons Compress security page][S55], publication stamp 2025-07-26: CVE-2024-26308/25710 and 1.26.0 fixes, distinct affected archive formats. |
| S56 | [JDOM 2.0.6.1 release][S56] and [maintainer XXE issue](https://github.com/hunterhacker/jdom/issues/189); version/fix reference checked 2026-09-25. |

## 12. Unresolved decisions and final disposition

The findings support **narrow advisory remediation first, then a qualified compiler/processor cohort, followed by separately tested UI, native/crypto, harness and storage work**. They do not support an all-at-once “latest dependencies” commit. Preserve the existing application/runtime contracts and requirement evidence throughout.

| Open question | Evidence or decision that settles it | Proposed owner / gate |
|---|---|---|
| What graph assurance remains? | Scoped Maven resolution/selection/hashes are complete (§2.2); finish native SBOM, repository provenance and actual CI execution/action-SHA evidence | Build maintainer; G0 |
| Can 8.14.4 qualify immediately, or is an interim verification/filtering mitigation needed? | Windows/CI builds and repository-integrity negative tests; specific plugin/Kotlin failures if any | Build/security reviewers; WP1 |
| Does the AGP 8/API 36 narrow tuple work with KSP2, Hilt 2.58, Room 2.8.5 and stable analyzers? | Clean/incremental generation, analyzer negative fixtures and runtime/device evidence | Build/architecture reviewers; G1–G3 |
| Which exact API 36 UI/BOM/Lifecycle versions satisfy maintenance and metadata constraints? | Resolved AAR metadata/BOM mapping and required bug-fix history; choose earlier compatible stable cohort or approve WP9 | UI/build reviewers; WP4 before version freeze |
| Which returned and manually identified advisories are applicable, especially KGP/AGP tool dependencies? | Use completed OSV ledger and maintainer checks (§7.3); finish packaged/build reachability, native coverage and approved per-advisory disposition | Security/build reviewers; WP0a/G5 |
| Is an alpha06→Security 1.1.0 bridge needed, and how long may legacy readers remain? | Replacement schedule, supported upgrade/skip cohorts, EOL-dependency policy and actual old-data compatibility | Product/security reviewers; proposal D1/D8 and G5 |
| May vault/intruder data be retired, or must blobs remain readable? | Installed-user cohort inventory and approved M8 data-disposition decision; no inferred deletion permission | Product/data reviewers; proposal D6 |
| What release OS range must be qualified, including 31/32/34 and 16 KiB? | Resolve build 26–36 versus written 30–35 scope; document devices/rig substitutions and measured page sizes | Release lead; G3 |
| Can test-manifest/preview and transitive Navigation be removed? | Actual merged manifest, source/graph consumer audit and launch/owner tests | Test/UI reviewers; WP4/WP6 |
| When are storage format/recovery decisions approved and qualified? | Proposal D1–D13 decisions, independent serializer review and full boundary/device results | Security/storage reviewers; WP7/G4 |

For each implemented package, record: exact selected versions and rationale; dependencies introduced/removed; requirement/ADR/risk impact; test commands/results and device coverage; baseline/candidate measurements; supported upgrade and rollback path; residual concern, owner and due date; and the accepted completion gate. Mark deferred candidates with a reason and recheck milestone. Record a security advisory separately from a deprecation or ordinary update request.

This report **does not change RTM status, approve a new architecture, close R-007 or reopen/close another risk by itself**. It identifies evidence to bring to those governed records. Fresh baseline resolution and advisory lookup are complete; baseline execution results are separately recorded in §2.3. No candidate dependency upgrade, installed-data migration, vulnerability remediation or device qualification was performed. Those remain acceptance gates, not missing results to infer from baseline success.

[S01]: https://github.com/gradle/gradle/security/advisories/GHSA-w78c-w6vf-rw82
[S49]: https://google.github.io/osv.dev/post-v1-querybatch/
[S50]: https://raw.githubusercontent.com/CVEProject/cvelistV5/main/cves/2026/53xxx/CVE-2026-53914.json
[S50a]: https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6
[S51]: https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902025%E2%80%9014813
[S52]: https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%905588
[S53]: https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8
[S54]: https://github.com/netty/netty/security/advisories/GHSA-xq3w-v528-46rv
[S55]: https://commons.apache.org/proper/commons-compress/security.html
[S56]: https://github.com/hunterhacker/jdom/releases/tag/JDOM-2.0.6.1
[S02]: https://developer.android.com/build/releases/agp-8-13-0-release-notes
[S03]: https://kotlinlang.org/docs/gradle-configure-project.html
[S04]: https://developer.android.com/build/kotlin-support
[S05]: https://kotlinlang.org/docs/gradle-compiler-options.html
[S06]: https://github.com/google/ksp/releases
[S07]: https://github.com/google/dagger/releases
[S08]: https://developer.android.com/jetpack/androidx/releases/security
[S09]: https://developer.android.com/privacy-and-security/cryptography#jetpack_security-crypto_library
[S09a]: https://developer.android.com/jetpack/androidx/releases/datastore
[S10]: https://developer.android.com/jetpack/androidx/releases/core
[S11]: https://developer.android.com/jetpack/androidx/releases/lifecycle
[S12]: https://developer.android.com/jetpack/androidx/releases/activity
[S13]: https://developer.android.com/jetpack/androidx/releases/camera
[S14]: https://developer.android.com/jetpack/androidx/releases/room
[S15]: https://developer.android.com/jetpack/androidx/releases/sqlite
[S16]: https://developer.android.com/jetpack/androidx/releases/biometric
[S17]: https://developer.android.com/jetpack/androidx/releases/hilt
[S18]: https://developer.android.com/reference/kotlin/androidx/hilt/navigation/compose/hiltViewModel.composable
[S19]: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
[S20]: https://developer.android.com/develop/ui/compose/graphics/images/material
[S21]: https://developer.android.com/develop/ui/compose/bom
[S22]: https://www.bouncycastle.org/download/bouncy-castle-java/
[S23]: https://www.bouncycastle.org/vulnerability-advisory.html
[S24]: https://www.zetetic.net/blog/2026/09/08/sqlcipher-4.19.0-release/
[S25]: https://www.zetetic.net/sqlcipher/sqlcipher-for-android-community/4.19.0
[S26]: https://github.com/Kotlin/kotlinx.coroutines/releases
[S27]: https://detekt.dev/docs/introduction/compatibility/
[S28]: https://github.com/LemonAppDev/konsist/releases
[S29]: https://github.com/jk1/Gradle-License-Report
[S30]: https://github.com/junit-team/junit4
[S31]: https://developer.android.com/jetpack/androidx/releases/test
[S32]: https://developer.android.com/jetpack/androidx/releases/test-uiautomator
[S33]: https://developer.android.com/topic/libraries/architecture/datastore
[S34]: https://developer.android.com/identity/data/autobackup
[S35]: https://developer.android.com/build/releases/agp-9-4-0-release-notes
[S36]: https://developer.android.com/build/migrate-to-built-in-kotlin
[S37]: https://gradle.org/releases/
[S38]: https://kotlinlang.org/docs/releases.html
[S39]: https://developer.android.com/reference/android/content/pm/PackageManager
[S40]: https://developer.android.com/reference/android/app/AppOpsManager
[S41]: https://developer.android.com/work/device-admin
[S42]: https://docs.gradle.org/current/userguide/gradle_wrapper.html
[S43]: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
[S44]: https://developer.android.com/guide/practices/page-sizes
[S45]: https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-85/
[S46]: https://developer.android.com/jetpack/androidx/releases/compose-ui
[S47]: https://github.com/androidx/androidx/blob/androidx-main/datastore/datastore-core/src/commonMain/kotlin/androidx/datastore/core/DataStore.kt
[S48]: https://developer.android.com/develop/ui/compose/bom/bom-mapping
