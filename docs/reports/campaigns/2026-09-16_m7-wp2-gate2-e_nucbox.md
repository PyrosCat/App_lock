# M7/WP2 Gate-2 (change E real-surface instrumentation) — NucBox G5 (x86_64 emulator lanes)

- **Date (filing):** 2026-09-18 · **Evidence captured:** Leg A 2026-09-16 (results-XML `2026-09-16T04:21:20`); Leg B 2026-09-18 (results-XML `2026-09-18T18:08:34`).
- **Author / host:** the GMKtec NucBox G5 (Windows 11, N-series, Intel UHD Graphics), the fleet's x86_64 emulator
  host. This machine boots x86_64 images; the Moto G / 2012 box / arm64 lanes cannot, so the API-36 emulator legs
  are this host's job (`docs/testing/M7_WP2_GATE2_NUCBOX_PLAN.md`).
- **Verifies (this host):** the two change-E instrumentation classes on x86_64 — `OverlayLockPresenterUiTest` (11)
  + `OverlayWindowHostFaultUiTest` (6). Provides the API-36 evidence for the Moto G report's Open items 1, 3, 5
  (Back-callback routing under targetSdk 36; the aborted/slow biometric-launch gate timing; the no-enrollment
  biometric branch), the surface content and fault-atomicity on API 36, and Open item 4 (a biometric SUCCESS from
  the overlay = Leg B) on a `google_apis` AVD. Contributes to FR-044.
- **Complements (do not edit — each host keeps its own):** the arm64 / API-35 leg on the Moto G 2025
  (`campaigns/2026-09-14_m7wp2-gate2-e_moto-g-2025.md`).

## Result

Both NucBox legs pass:

- **Leg A** — API-36 change-E surfaces + fault-atomicity (`OverlayLockPresenterUiTest` + `OverlayWindowHostFaultUiTest`):
  `tests="17" failures="0" errors="0" skipped="2"` = 15 PASS + 2 expected SKIP, at the tests' 5-second content
  waits. The two skips need enrollment / the Leg-B opt-in.
- **Leg B** — `biometricSuccessFromTheOverlayReportsUnlockSucceeded`, run separately with the opt-in and a real
  enrolled fingerprint: `tests="1" failures="0" errors="0" skipped="0"` = PASS.

Leg A ran on a manually-booted hardware-GPU emulator via `connectedProdDebugAndroidTest` rather than the plan's GMD
lane, because the GMD software-GPU boot did not render the overlay surfaces (§Leg A) — a deviation from the plan's
prescribed lane.

## Scope
- **Change E** is the production `OverlayLockPresenter`: each lock surface (Lock PIN-pad, Checking shield, Recovery
  shield) is a real `TYPE_APPLICATION_OVERLAY` window hosting the C1 Compose UI. The two suites assert, via
  cross-window UiAutomator, that the surfaces render their content, that an interactive overlay blocks a real
  underlying touch (a `SentinelActivity` beneath it) while a dismissed one passes it through, that the escape
  controls and Back emit the right `SafeDismissRequested`, that the biometric single-flight gate honours its
  real-clock timeouts, and that the window-host reset path is failure-atomic under injected faults. These are
  direct-presenter tests: they verify the completion the presenter emits to a bound test sink, not that the
  runtime then navigates (navigation lands at change F).
- **GMD** = Gradle Managed Devices: AGP boots/installs/tears-down its own emulator per `create("apiNN")` in
  `app/build.gradle.kts`. The `api36` device is `Pixel 5`, apiLevel 36, `systemImageSource = "aosp"` (the installed
  `android-36;default` image). `connectedProdDebugAndroidTest` instead runs against a manually-booted emulator —
  the same task the Moto G leg uses.
- **Back under targetSdk 36:** the platform stops dispatching `KEYCODE_BACK`, so Back must arrive via
  `OnBackInvokedCallback`; `backOverTheLockLeavesToHome` exercises that path on API 36.

## Environment
- **Host:** GMKtec NucBox G5, Windows 11, ADR-014 fleet emulator host. SDK at `%LOCALAPPDATA%\Android\Sdk`;
  emulator/adb driven from Git Bash (platform-tools prepended to PATH; the SDK tools are not otherwise on it).
- **AVD:** `matrix_api36` — `android-36;default` (AOSP), x86_64, Pixel-5 profile. Booted headless
  `-no-snapshot -no-audio -no-boot-anim -no-window -memory 3072`.
- **GPU (differs between the two Leg-A runs):**
  - GMD lane: `-gpu auto-no-window` = software rendering (SwiftShader), per GMD's launch params at
    `.android/avd/gradle-managed/dev36_default_x86_64_Pixel_5.avd/emu-launch-params.txt`.
  - Hardware-GPU lane: `-gpu host` = the real Intel(R) UHD Graphics (emulator log
    `vulkan_mode_selected:host gles_mode_selected:host`; SurfaceFlinger `GLES: … Intel(R) UHD Graphics`; HWUI `skiagl`).
- **Target app label** in the surfaces resolves to `com.example.target` (an uninstalled placeholder package, so its
  name is shown); no real target app is needed.

## Leg A — the GMD failure, a diagnostic, and the hardware-GPU run

### 1. GMD software-GPU lane — 11/17 fail
`api36ProdDebugAndroidTest` (class-filtered to the two change-E classes) failed 11/17 (2 SKIP, 4 PASS) on two runs.
No failure is an exception, and none is the historical ViewTreeLifecycleOwner crash (that aborts at test 1; here
all 17 ran). By assertion:
- 10 of 11 fail an on-screen-content assertion — `AssertionError: expected on-screen text: "…"` ("Enter your PIN",
  "Checking protection…", "Protection needs attention"): the tests' `By.text` lookups do not find the overlay content.
- 1 of 11 — `aColdPresentWhoseFirstUpdateFailsLeavesNothingBlockingThenRecovers` — fails the touch-delivery
  assertion "a failed cold reveal must not block the underlying touch".
- The 4 that pass assert only internal gate/host logic and read no overlay content.
- Only the three surface tests call `overlayPresent()`; on those it passed. The other failing tests do not check
  window presence.

Increasing the wait to 20 seconds did not resolve the failures: each failing test then spent the full ~20–24 s (per
the results XML) and still failed. The original timeout was restored.

### 2. Diagnostic
Reproduced on a hand-booted `matrix_api36` with `-gpu swiftshader_indirect`. With the Lock surface presented, a
framebuffer capture via `exec-out screencap` showed the launcher with a "System UI isn't responding" ANR.
`dumpsys window` / SurfaceFlinger showed the `AppLockOverlay` window present with an allocated buffer, but the
`TRANSLUCENT` window drew nothing — the launcher showed through — and the tests' `By.text` lookups found no overlay
content. The launcher and system UI render, so the GPU is producing frames.

### 3. Hardware-GPU run
`connectedProdDebugAndroidTest` (same class filter, 5 s waits) on a `-gpu host` emulator with a healthy SystemUI (no
ANR): `tests="17" failures="0" errors="0" skipped="2"`. Content renders quickly (Lock 1.7 s, shields ~2 s); a
framebuffer capture shows the Lock surface rendering the PIN pad. The same classes also pass on real hardware (the
Moto G, API 35 — the companion report).

### Interpretation
The failures appear only on the software-GPU boot, where SystemUI was unresponsive and the overlay did not
composite; the surfaces render on the hardware-GPU boot and on real hardware. The two Leg-A runs differed in both
GPU mode and SystemUI health, so this campaign does not isolate which is decisive. The pattern is consistent with a
rig/environment problem — a software-GPU emulator not compositing the Compose overlay while SystemUI is wedged —
rather than a fault in the change-E code, which renders where the rig is healthy. It is not consistent with an
API-36 UiAutomator/a11y problem, since the same `By.text` assertions pass once the surfaces render.

### Per-test results (hardware-GPU run)
| Test | Asserts | Result |
|---|---|---|
| `lockSurfaceRendersThePinPad` | Lock surface renders the `LockScreen` PIN pad (enter-PIN text, a digit, the app label) | PASS 1.7 s |
| `checkingShieldRendersAndEscapesToAppLock` | Checking shield renders; its escape emits an APP_LOCK safe-dismiss with the readiness token | PASS 1.9 s |
| `recoveryShieldRendersAndExposesBothEscapes` | Recovery shield renders both controls; grant → OVERLAY_SETTINGS (with exempt pkgs), go-home → HOME, both with the token | PASS 2.5 s |
| `interactiveOverlayBlocksUnderlyingTouchAndPassthroughAllowsIt` | An interactive overlay blocks a real sentinel touch; a dismissed one passes it through | PASS 4.7 s |
| `backOverTheLockLeavesToHome` | Back over Lock emits a HOME `SafeDismissRequested` carrying the matching request token, via the `OnBackInvokedCallback` path (targetSdk 36) — verifies the emitted request, not navigation or app visibility | PASS 3.9 s |
| `abortedBiometricLaunchReclaimsAfterTimeoutAndLeavesThePinPad` | An aborted launch's lease reclaims after the real timeout; the PIN pad stays | PASS 6.6 s |
| `slowBiometricAckWithinTimeoutIsHonouredAndPastTimeoutIsRejected` | A within-timeout ack is honoured; a past-timeout ack is rejected (real clock) | PASS 6.9 s |
| `staleBiometricHostWithoutAMatchingLeaseFinishesSilently` | ADR-020: a stale host with no matching lease finishes silently | PASS 2.4 s |
| `biometricHostCarriesTheRequestTokenWhenNoEnrollment` | No-enrollment branch: the host carries the full epoch+id token | PASS 0.4 s |
| `biometricAutoPromptThenManualRetryReportCancelToTheSink` | Enrolled-path auto-prompt + retry report cancel | SKIP — no enrollment on the AOSP image (covered on the Moto G) |
| `biometricSuccessFromTheOverlayReportsUnlockSucceeded` | A real biometric SUCCESS reports `unlockSucceeded` | SKIP — needs the opt-in + enrollment = Leg B |
| `OverlayWindowHostFaultUiTest` × 6 | The window-host reset is failure-atomic under injected faults | 6 PASS (0.3–4.1 s) |

## Leg B — biometric SUCCESS from the overlay (google_apis API 35, 2026-09-18)
Runs on a `google_apis` image for the fingerprint HAL — `matrix_api35` (API 35, the plan's documented fallback; no
`google_apis;android-36` image is installed), booted headless `-gpu host` (see the host-stability note).

- **Enrollment (device fixture):** PIN `1234` set via `locksettings`; one fingerprint enrolled headlessly by
  screenshot-guided navigation (`FINGERPRINT_ENROLL` → confirm PIN → consent → find-sensor) with the touch injected
  via the emulator console (`adb emu finger touch 1` ×8). Confirmed enrolled: `dumpsys fingerprint` reports
  `"count":1`, strength 15 (STRONG, so `canAuthenticate(BIOMETRIC_WEAK)` = SUCCESS). Enrollment is a device setting
  and survives the app reinstall.
- **The test:** `biometricSuccessFromTheOverlayReportsUnlockSucceeded` via `connectedProdDebugAndroidTest` with
  `-e inject_biometric_success 1`. It presents Lock, the FR-002/FR-007 auto-launched prompt shows, the test logs the
  `AppLockBiometricReady` marker, and a logcat watcher injects the enrolled finger (`adb emu finger touch 1`). The
  overlay then reports `unlockSucceeded` with the request token and the `BIOMETRIC` method.
- **Result:** `Starting/Finished 1 test on matrix_api35 - 15`; `tests="1" failures="0" errors="0" skipped="0"`; PASS
  in 3.713 s, results-XML `2026-09-18T18:08:34`. The marker fired for request 17, the finger was injected, and the
  assertion held. `skipped="0"` confirms both `assumeTrue` gates — the opt-in arg and the enrolled authenticator — held.

## Disposition
- **Gate-2 (change E):** Both test legs passed — Leg A (API-36 surfaces + fault-atomicity, via the hardware-GPU
  connected run) and Leg B (biometric success, `google_apis`), together with the Moto G's arm64 / API-35 legs.
  Gate-2 closure requires lead acceptance of the hardware-GPU substitution.
- **Outstanding:** the non-debug FLAG_SECURE deployed-secure proof (Moto G Open item 2) is a separate change-F item,
  not a Gate-2 blocker per the plan.
- **RTM (GOVERNANCE §1.3 rule 2):** FR-044's `verification` pointer now cites this campaign; status stays `partial`,
  with full verification at WP6. No production-code, ADR, or risk-register change.

## Follow-ups
1. **Fleet finding (this configuration only).** On API 36 `aosp/default` with a software GPU, the GMD lane did not
   composite the change-E Compose overlay surfaces (SystemUI unresponsive); the hardware-GPU emulator rendered them.
   The WP0 OV-4 spike (a simple drawn overlay) is unaffected. The other software-GPU levels (api30/33/35) were not
   run in this campaign. Before excluding these classes from the software-GPU GMD `ci`/`full` groups, check whether
   the failure reproduces on those levels; if it does, route the change-E surface/fault classes to a hardware-GPU
   emulator (`-gpu host`, connected) or FTL and record the requirement in `M7_WP2_GATE2_NUCBOX_PLAN.md`.

## Notes
- **Host stability:** the first Leg-B attempt booted the emulator windowed with `-gpu host`; that moment coincided
  with a host BSOD (bugcheck `0x139`), while Windows was concurrently applying a planned OS upgrade — the cause was
  not established. All reported evidence was captured headless.
- **State hygiene:** the change-E suites snapshot the overlay app-op before their assumption and restore it in
  `@After`; the connected run uninstalls the app + test APK on completion.

## Produced against
`main` @ `7b7bad9` (clean tree). APKs built on this host at that commit, compile/target SDK 36, variant `prodDebug`
(debuggable, so FLAG_SECURE is dropped and the overlay content is inspectable):
- `app-prod-debug.apk` — sha256 `995836be7f2655d9a8d297c38c0990134d232ca312825b9a73422aa5014be18c`
- `app-prod-debug-androidTest.apk` — sha256 `7150dcfdf01a13e05f024782904a7bbdc034eb70615ec6f411de9ca8ed21aee3`
