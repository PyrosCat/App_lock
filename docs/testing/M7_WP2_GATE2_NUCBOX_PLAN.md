# M7/WP2 Gate-2 (change E) — NucBox emulator plan

**For:** the NucBox G5 emulator host (the fleet's device-verification machine) and its operator.
**Companion:** the arm64 leg is complete on the Moto G 2025
(`docs/reports/campaigns/2026-09-14_m7-wp2-gate2-e_moto-g-2025.md`).
**SSOT:** `docs/process/M7_PLAN.md` §WP2 "Phase 2 detail" change E, "Gate-2 execution (fleet)".

Gate 2 for change E closed on 2026-09-18. The NucBox run of this plan is recorded in
`docs/reports/campaigns/2026-09-16_m7-wp2-gate2-e_nucbox.md`, and the lead accepted the hardware-GPU substitution
for Leg A. The Moto G leg passed the overlay surfaces, the underlying-touch block, the shield
escape requests, Back on API 35, the window-host fault-atomicity, and the aborted or slow biometric launch on
API 35. This plan covers the legs that need an x86_64 emulator. This fleet host runs x86_64 emulators. The
Moto G and the arm64 lanes cannot.

## Prerequisites

1. **Pull the change-E follow-up commit first.** The new tests are in that commit, not in `4c9f311`. On the
   NucBox, run `git pull`. Make sure that `OverlayLockPresenterUiTest` contains the three aborted or slow launch
   tests and `biometricSuccessFromTheOverlayReportsUnlockSucceeded`. Do not use `4c9f311`. It still has the
   ViewTreeLifecycleOwner crash.
2. **Toolchain and boot recipe.** The NucBox boots x86_64 images and runs GMD (see `docs/testing/WP8_GMD_MATRIX.md`
   §5 and the fleet report). Use the Git Bash JAVA_HOME line from that runbook.
3. **Detach the Moto G, or set `ANDROID_SERIAL`.** This makes the connected task use the correct emulator.

## Legs

| Leg | What it closes | Rig | Runnable now |
|---|---|---|---|
| A | Predictive-Back on API 36, the 3 aborted or slow biometric tests on API 36, the no-enrollment branch, surfaces, fault-atomicity | GMD api36 (`aosp`) | Yes |
| B | A demonstrated biometric SUCCESS from the overlay | Manual `google_apis` AVD with an enrolled fingerprint | Yes |

The non-debug FLAG_SECURE deployed-secure proof is not a NucBox task. It is an F item (see
`docs/process/M7_PLAN.md`).

### Leg A — GMD api36 (the main NucBox run)

Run the two change-E instrumentation classes on the API-36 lane. The class filter keeps the run on change-E
evidence. It also excludes the WP0 OV-4 spike test (`OverlayRaceUiTest`), which is not change-E evidence.

```
./gradlew api36ProdDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.applock.e2e.OverlayLockPresenterUiTest,com.applock.e2e.OverlayWindowHostFaultUiTest
```

A bare `aosp` api36 image has no fingerprint HAL and no enrollment. Expect these results:

- **`backOverTheLockLeavesToHome` PASS** shows that predictive-Back routing works under targetSdk 36. The
  platform stops dispatching `KEYCODE_BACK`, so Back must use the `OnBackInvokedCallback`. This is the proof for
  API 36.
- **The three aborted or slow launch tests PASS.** They show the gate timing on API 36 against the real
  elapsed-realtime clock: `abortedBiometricLaunchReclaimsAfterTimeoutAndLeavesThePinPad`,
  `slowBiometricAckWithinTimeoutIsHonouredAndPastTimeoutIsRejected`, and
  `staleBiometricHostWithoutAMatchingLeaseFinishesSilently`.
- **`biometricHostCarriesTheRequestTokenWhenNoEnrollment` PASS** covers the no-enrollment branch. This test runs
  only when no authenticator is enrolled. A bare `aosp` image has no enrollment.
- **`biometricAutoPromptThenManualRetryReportCancelToTheSink` is SKIPPED.** It needs an enrollment, which is
  Leg B. **`biometricSuccessFromTheOverlayReportsUnlockSucceeded` is also SKIPPED,** because there is no opt-in
  argument.
- The other surface tests and all six `OverlayWindowHostFaultUiTest` tests PASS.

**Pass criteria:** the result is `BUILD SUCCESSFUL` with 0 failures. The named tests above show PASS or the
expected SKIP in the results XML.

Optional broader regression: `./gradlew fullGroupProdDebugAndroidTest` runs the same suites across API
26/29/30/33/35/36. It is not necessary to close Gate 2 for change E, but it finds cross-API regressions. If the
API-29 Argon2 case runs out of memory, apply the heap workaround in `docs/testing/WP8_GMD_MATRIX.md` §5.2. That
is an emulator image limit, not an app defect.

### Leg B — biometric SUCCESS from the overlay (manual google_apis AVD)

A real fingerprint match needs the fingerprint HAL. Bare `aosp` images do not have it, so use a **google_apis**
image (see `docs/testing/M7_WP0_BIOMETRIC_MATRIX.md` §3). Instrumentation cannot inject a real fingerprint. The
operator injects it through the emulator console while the test waits.

1. Boot a `google_apis` AVD. Use API 36, or API 35 if necessary:

   ```
   emulator -avd <google_apis_avd> -no-window -no-audio &
   adb wait-for-device
   ```

2. Enrol one fingerprint. A device credential is necessary first:

   ```
   adb shell locksettings set-pin 1234
   # Settings > Security > Fingerprint > enrol; during "touch the sensor":
   adb emu finger touch 1        # repeat about 6 times to complete the enrolment; check it in the Settings UI
   ```

3. In one shell, start the opt-in test:

   ```
   ./gradlew connectedProdDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.applock.e2e.OverlayLockPresenterUiTest#biometricSuccessFromTheOverlayReportsUnlockSucceeded \
     -Pandroid.testInstrumentationRunnerArguments.inject_biometric_success=1
   ```

4. In a second shell, inject the finger when the prompt is ready. The test writes the marker to logcat and waits
   up to 20 s:

   ```
   adb logcat -s AppLockBiometricReady | while read -r line; do adb emu finger touch 1; done
   # stop this watch loop (Ctrl+C) after the test finishes
   ```

**Pass criteria:** the test PASSES. This means the overlay reported `unlockSucceeded` with the exact request
token and the `BIOMETRIC` method. If the enrolment or the HAL is absent, the test SKIPS. A SKIP is not a pass for
this leg.

## Results and reporting

- Write one campaign report for this host: `docs/reports/campaigns/<run date>_m7-wp2-gate2-e_nucbox.md`, named per
  `docs/reports/README.md`. The Gate-2 run filed `2026-09-16_m7-wp2-gate2-e_nucbox.md`. Record the API levels, the
  per-test pass, skip, or fail results, and the image family (`aosp` for Leg A, `google_apis` for Leg B).
- Keep the draft uncommitted until it is final. The user commits it (see `docs/GOVERNANCE.md`). A report is
  immutable after it is committed.
- RTM: `FR-044` stays `partial`. The full verification is at WP6 against the whole set, not at this run.

## Gate-2 close condition

Gate 2 for change E closes when Leg A passes on API 36 and Leg B passes on a `google_apis` AVD. The Moto G report
covers the arm64 and API-35 legs. The deployed FLAG_SECURE proof is out of scope here. It is an F item, recorded
in `docs/process/M7_PLAN.md`. Do not start change F until Leg A and Leg B pass.

## Notes and risks

- **swGPU timing.** The NucBox uses a software GPU. The change-E surface tests drive the presenter directly and
  are not the OV-4 race, so they are stable. If the overlay is slow to appear, increase the per-test waits. Do
  not lower the assertions.
- **Image family.** Leg A needs only `aosp`. Leg B needs `google_apis` for the fingerprint HAL. Do not run Leg B
  on `aosp`, because the test SKIPS.
- **GMD lifecycle and finger injection.** GMD boots and stops its own emulator, so `adb emu finger` cannot reach
  it reliably. Leg B therefore uses a manual AVD and the connected task, not a GMD lane.
- **The app is uninstalled after connected runs.** Gradle uninstalls the app and the test APK after
  `connectedProdDebugAndroidTest`, so app-op grants do not persist. A fingerprint enrolment is a device setting
  and survives the app uninstall.
