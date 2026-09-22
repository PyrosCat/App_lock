# M7 WP2 change F3 — HomeResolver on-device check — Moto G 2025 (real hardware, arm64 / API 35)

- **Date / captured:** 2026-09-22 
- **Author / host:** 2012 i7 dev box (Windows 10 Pro), driving the Moto G 2025 over USB adb.
- **Device:** moto g - 2025 (serial ZT4229HQ6X), Android **15**, API **35**, ABI **arm64-v8a**, build
  `V1VKS35.22-125-5` (user, release-keys), security patch 2025-08-01. The default launcher before and after the
  run is `com.motorola.launcher3`.
- **Tested build:** commit **`30529e2`** ("M7/WP2 (Change F3): HomeResolver tri-state resolve + episode
  revalidation") plus the three device-check files below. They were uncommitted at run time and are committed
  together with this report. The git blob hashes identify the tested content, so a reader can compare them with the
  committed files:

  | File | Blob |
  |---|---|
  | `app/src/androidTest/java/com/applock/e2e/HomeResolverDeviceTest.kt` | `4b01896842eb` |
  | `app/src/debug/java/com/applock/e2e/FakeHomeActivity.kt` | `9773f93ee203` |
  | `app/src/debug/AndroidManifest.xml` | `6c0997bc2c19` |

- **Variant:** `prodDebug` (applicationId `com.applock`). The build is debuggable. `FakeHomeActivity` is in the
  debug source set only, so release builds do not contain it.
- **Toolchain:** AGP 8.13.2, Gradle 8.13, Kotlin 2.1.0.
- **Verifies / observes:** the platform behaviour that the JVM tests of `PackageManagerHomeResolver` assume, on real
  hardware. Related requirement: **FR-052** (Home Screen Transition Handling, `not-started`). This is platform
  evidence only. It does not change the RTM status, because the resolver is not wired into production until F6.

## Headline

- **PASS: 3 / 3, 0 failed, 0 skipped** (`tests="3" failures="0" errors="0" skipped="0"`, 6.7 s).
- **Resolve cost:** median **0.79 ms**, maximum 1.85 ms over 50 resolves. The test ceiling is a 50 ms median.
- **Default-launcher change:** `PackageManager` reported the new default launcher **2 ms** after the role change
  was confirmed. The resolver then classified the new launcher as Home and the former launcher as not Home.
- **No HOME role holder:** this Motorola build continued to resolve HOME to `com.motorola.launcher3` (no chooser
  within 5 s). The resolver agrees with the platform. The `NoDefault` result does not occur on this device.
- **Device state restored:** the same default launcher, screen timeout, and stay-awake setting as before the run,
  and no App Lock package left installed.

## Context & terms

- **Change F3 (M7 WP2):** the change that makes `PackageManagerHomeResolver` resolve the home launcher again on each
  new foreground episode, adds a three-state resolve result, and resolves again once the last attempt is older than
  2 s (the **TTL**).
- **Home classification:** the lock engine does not evaluate a foreground app that it classifies as Home. A wrong
  Home answer is therefore security-relevant. A demoted former launcher that is still classified Home bypasses the
  lock. A new launcher that is not recognized gets a recovery shield under a failed policy.
- **Foreground episode:** a continuous period with the same app in front. A new episode starts when the observed
  package changes. The resolver then resolves again instead of using its cache.
- **HOME role:** from API 29, the default launcher is the holder of the `android.app.role.HOME` role. The shell
  commands `cmd role get-role-holders`, `add-role-holder`, and `clear-role-holders` read and change it.
- **`resolveActivity`:** the `PackageManager` call that the resolver uses (`MAIN`/`HOME`, `MATCH_DEFAULT_ONLY`). It
  returns the default launcher, the launcher chooser (package `android`) when no default is set, or null.
- **Resolution:** the resolver's result type: `Resolved(package)`, `NoDefault` (null or the chooser), or `Failure`.
- **`FakeHomeActivity`:** a test-only second launcher. It is declared disabled. The test enables it only while it
  runs, so App Lock itself is briefly a launcher candidate.
- **FR-052:** Home Screen Transition Handling. It is `not-started` in the RTM and owned by M7.

## Command and procedure

```
gradlew.bat connectedProdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.applock.e2e.HomeResolverDeviceTest
```

The connected task installs the app and test APKs, runs the class, and uninstalls both APKs. The phone was kept
awake for the run and the settings were restored after it, in a `finally` block:

| Setting / state | Before | During | After |
|---|---|---|---|
| `settings global stay_on_while_plugged_in` | 0 | 2 (stay awake on USB) | 0 |
| `settings system screen_off_timeout` | 30000 ms | 1800000 ms | 30000 ms |
| Wakefulness (`dumpsys power`) | Awake | Awake | — |
| HOME role holder | `com.motorola.launcher3` | changed by the tests | `com.motorola.launcher3` |
| `com.applock*` packages installed | none | app + test APK | none |

## Results

| Test | Asserts | Observed | Time |
|---|---|---|---|
| `resolvesTheDefaultLauncherCheaply` | The resolver returns the current role holder as Home and App Lock as not Home. The median of 50 resolves is under 50 ms. | median 788 µs, max 1848 µs | 0.038 s |
| `aNewEpisodeAfterADefaultLauncherChangeReturnsTheNewLauncher` | After App Lock becomes the role holder, a new episode returns App Lock as Home and the former launcher as not Home. | change visible after 2 ms | 0.121 s |
| `withNoRoleHolderTheResolverAgreesWithThePlatform` | With the role cleared, each candidate is Home exactly when `resolveActivity` returns it. | `com.motorola.launcher3` returned for 5051 ms | 5.157 s |

The test logged these lines (`adb logcat -v time -s HomeResolverDeviceTest:I`):

```
09-22 17:31:24.658 I/HomeResolverDeviceTest(17349): with no HOME role holder, resolveActivity returned com.motorola.launcher3 (sampled after 5051 ms)
09-22 17:31:24.803 I/HomeResolverDeviceTest(17349): resolve latency over 50 calls: median=788us max=1848us
09-22 17:31:24.915 I/HomeResolverDeviceTest(17349): PackageManager reported the new default launcher after 2 ms
```

### Resolve cost

Each sample is one real `resolveActivity` call, because a call with a new episode within the TTL always resolves
again. The median of 0.79 ms supports the F3 design cost of one resolve for each app switch. **Verdict: PASS.**

### Default-launcher change

The test enabled `FakeHomeActivity` and made App Lock the role holder. `resolveActivity` returned the new holder
2 ms after `cmd role get-role-holders` confirmed the change. The resolver then returned App Lock as Home and
`com.motorola.launcher3` as not Home. The platform shows a change of default at once, so a resolve on the next
episode finds it. **Verdict: PASS.**

### No HOME role holder

With `FakeHomeActivity` enabled there are two launcher candidates, and `clear-role-holders` left the role with no
holder. `resolveActivity` still returned `com.motorola.launcher3` for the full 5 s poll. No chooser appeared. The
resolver returned the same answer: `com.motorola.launcher3` as Home, App Lock as not Home. On this device the
platform keeps routing HOME to the former holder, so the `NoDefault` mapping is not used. The JVM tests cover
that mapping. **Verdict: PASS** (the resolver agrees with the platform).

## Notes

- **Validation runs before this run found two test-design defects. Both were fixed before this run.**
  1. At 17:14 the default-change test failed: the role holder did not change. A manual adb check showed the
     cause. On this Android 15 user build the shell cannot change a component state outside a test-only package
     (`pm enable` failed with `SecurityException: Shell cannot change component state for
     ComponentInfo{com.applock.test/com.applock.e2e.FakeHomeActivity} to 1`). The fake launcher was therefore
     never enabled, and `cmd role add-role-holder` failed. The fix moved `FakeHomeActivity` from the androidTest
     APK to the debug source set of the app. The test runs in the App Lock process, so it enables its own
     component through `PackageManager` (`DONT_KILL_APP`) with no shell permission.
  2. At 17:19 the no-holder test failed. It expected the chooser, but `resolveActivity` returned
     `com.motorola.launcher3` for the full 5 s. The fix changed the test to assert that the resolver agrees with
     the platform, and to log the platform answer.

  A validation run at 17:20 passed 3 / 3. This run repeats it against the recorded build.
- **The resolve cost varied between runs.** The two validation runs in which `resolvesTheDefaultLauncherCheaply`
  ran first in the class measured a median of 2.6 ms and 3.1 ms. This run measured 0.79 ms. All values are far
  below the 50 ms ceiling.
- **The tests change the device default launcher for a short time.** The default-change test makes App Lock the
  holder for less than 1 s. The no-holder test leaves the role empty for about 5 s. `@After` restores the original
  holder and resets `FakeHomeActivity` to its declared disabled state; the procedure table shows the restored
  state.
- **Other lanes.** The same test runs in the per-push emulator matrix (API 30, 35, 36) and the NucBox full sweep.
  It skips below API 29 (the API 26 lane), because the HOME role does not exist there.
- **Scope limit.** This check covers the resolver's platform call only. It does not exercise the lock engine,
  because the runtime is not wired into production until F6.

## Disposition

- **The F3 platform assumptions hold on the Moto G 2025:** the resolver returns the default launcher, a change of
  default is visible at once, and one resolve is cheap.
- **`NoDefault` was not exercised on this device**, because this Motorola build keeps the former holder when the
  role is cleared. The mapping stays JVM-covered.
- **FR-052:** no RTM status change. This report is platform evidence for the resolver only.
- **Risk:** this check does not affect the event-driven limitation recorded in the resolver and the F3 changelog:
  a classification stays in effect until the next foreground observation. That limitation is to be filed as a
  proposed residual, the next risk-register entry (R-008).

## Follow-ups

- After F6 wires the runtime, check the lock behaviour end to end on a device: a protected former launcher locks
  after a default change, and a new default launcher is not shielded. Add this to the F fleet close-out.
- In the next emulator-matrix run, record which answer the AOSP images give with no HOME role holder (the chooser
  or the former holder).
