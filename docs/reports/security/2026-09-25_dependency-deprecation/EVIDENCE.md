# Dependency review evidence — 2026-09-25

Inspected commit: `e521026e2838c2bff592022046d6d0571d0e818f`.

This directory supports [the review](../2026-09-25_dependency-deprecation-review.md). Paths in retained output are anonymized. Retained top-level logs and HTML have trailing whitespace and excess final blank lines normalized; they are not byte-for-byte copies of raw output. Artifact hashes identify the original artifact bytes. No application source or declared dependency versions were edited. Scripts here are reproducibility aids, not an installed scanner or production build plugin.

## Completed resolution and lookup

- `resolution-scan-summary.json`: times, tool versions, counts and exclusions.
- `resolved-inventory.json.gz`: full UTF-8 JSON with graph edges, selection reasons, attributes, artifact hashes, plugin classes and configuration scope.
- `configuration-summary.csv` / `configuration-scope.csv`: inspected configuration counts and explicit omitted configurations.
- `resolved-components.csv`: all 478 selected Maven coordinates, scope/parents, matches and initial dispositions.
- `current-release-inventory.csv`: current 133-coordinate production runtime graph. `historical-release-inventory.csv` is the older license-derived observation, independently retained.
- `artifact-hashes.csv`: 427 unique external file records. Hashes identify observed bytes; publisher authentication was not performed.
- `matched-components.csv` / `advisory-triage.csv`: 21 matching coordinates and 51 unique returned advisories. Initial disposition is not final risk acceptance or proof of an exploit.
- `osv-scan-results.json` / `osv-advisories.json.gz`: dated results and retrieved full advisory records, including ranges and references.
- `online-discovery.txt`, `resolution-execution.txt`, `osv-execution.txt`: successful command output.
- `release-runtime.txt`, `unit-runtime.txt`, `androidtest-runtime.txt`: earlier incomplete offline attempts, superseded for current resolution claims.
- `baseline-interrupted.txt`: first baseline attempt canceled at the user's pause request; no completion or test pass is inferred from it.

The audit excludes local project outputs when collecting raw external artifacts. Test component graphs include the app dependency traversal; the test artifact subset must be read together with matching application artifact configurations. This avoids requiring APK compilation just to inventory external dependencies. It is not a complete assembled test classpath. Metadata-only/platform/multiplatform redirection components need not have binary files.

## Reproduce the completed audit

From the repository root in PowerShell, with the recorded JBR 21.0.10 and Android SDK available:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) '.android'
.\gradlew.bat --no-daemon --console=plain :app:dependencies --configuration prodReleaseRuntimeClasspath :app:resolvableConfigurations :app:buildEnvironment
.\gradlew.bat --no-daemon --console=plain --init-script docs/reports/security/2026-09-25_dependency-deprecation/resolution-audit.init.gradle reviewDependencyInventory
& './docs/reports/security/2026-09-25_dependency-deprecation/query-osv.ps1'
```

The audit writes fresh JSON to `.gradle/dependency-review/resolved-inventory.json`; the query script reads it and writes OSV results beneath `.gradle/dependency-review/osv/`. It sends only public Maven coordinates/versions to OSV. A future invocation will use the then-current repository/cache/advisory state and must retain its own timestamp and output. No clean-cache or independently authenticated repository-origin claim follows from this invocation.

The earlier baseline command completed JVM tests, configured detekt and the debug APK before being canceled at a user-requested pause:

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=2 --rerun-tasks --continue --warning-mode all --console=plain :app:testProdDebugUnitTest :app:detekt :app:assembleDevDebug :app:assembleProdRelease :app:lintProdDebug :app:compileProdDebugAndroidTestKotlin :app:generateLicenseReport
```

`baseline-paused.txt` and `baseline-paused-summary.json` retain that canceled invocation. `unit-test-summary.csv` and `unit-test-results.xml.gz` record 397 tests in 26 suites, with zero failures, errors or skips. `detekt.xml`/`detekt.md` retain the configured analysis; `debug-apk-hash.csv` identifies its debug APK.

The continuation executed the remaining task set:

```powershell
.\gradlew.bat --no-daemon --no-parallel --max-workers=2 --continue --warning-mode all --console=plain :app:assembleProdRelease :app:lintProdDebug :app:compileProdDebugAndroidTestKotlin :app:generateLicenseReport
```

It returned exit 0 and `BUILD SUCCESSFUL in 3m 46s`, with 98 actionable tasks: 9 executed, 3 from cache, 86 up-to-date. Its output ended at 2026-09-26T01:45:13Z, still 2026-09-25 in the local review timezone. The source-verification date remains 2026-09-25.

- `baseline-continuation.txt` / `baseline-summary.json`: completed output, exit status, task list, timestamps and cumulative evidence limits.
- `lint-prodDebug.txt`: freshly generated full lint output: 0 unfiltered errors, 40 warnings; the existing baseline filtered 1 error, 13 warnings and 1 hint. Its 42 unmatched baseline entries are not automatically 42 fixes.
- `license-report.html` / `fresh-license-inventory.csv`: freshly generated 133-coordinate license report, matching the independently resolved production runtime graph. The Jakarta Inject `META-INF` notice files preserve the HTML's relative links. This is not complete legal or native-license qualification.
- `release-apk-hash.csv`: hash of an existing unsigned release APK accepted up-to-date. The creating invocation is not attributed to this review. Release assembly/R8/packaging and instrumentation Kotlin compilation were accepted `UP-TO-DATE`, not freshly executed.

Actual cumulative results are recorded in review §2.3. The earlier `--rerun-tasks` invocation does not imply clean dependencies, empty Kotlin incremental caches, CI JDK 17 parity, device execution or qualification of any candidate update. No device install, migration, deliberate corruption or vulnerability PoC is part of these commands. The successful continuation does not remove baseline-filtered lint debt or establish a clean rebuild.
