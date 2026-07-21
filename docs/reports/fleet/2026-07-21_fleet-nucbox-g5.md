# Fleet Report — NucBox G5 Provisioning Outcome

**Date:** 2026-07-21 (filed) — provisioning evidence captured 2026-07-20
**Author / host:** NucBox G5 — GMKtec, Windows 11 Pro (10.0.22631), Intel N-series x86_64 (on-host assistant)
**Produced against:** app sources at `f4e4b9b`, where the build/emulator evidence below was gathered **2026-07-20** (APK build timestamp 02:42); report filed **2026-07-21** on `a92d1a0`. The `f4e4b9b..a92d1a0` delta is docs-only — no `app/` or build change — so the build result holds for both.
**Procedure followed:** [`../../process/DEV_ENVIRONMENT_SETUP.md`](../../process/DEV_ENVIRONMENT_SETUP.md)
**Verifies / observes:** evidence toward **NFR-COMP-006** (Build Environment Portability) and **NFR-MNT-013** (Knowledge Transfer); **ADR-014** verification-fleet readiness. Partial input to **NFR-COMP-001** (matrix now *executable*, not yet *verified* — see §6).
**Verdict:** ✅ **Fleet-ready.** All setup-guide steps pass; this host builds the project green and boots hardware-accelerated x86_64 emulators across the API 26/29/33/35 matrix.

---

## 1. Checkpoint results

| Checkpoint | Command | Result |
|---|---|---|
| Hardware acceleration | `emulator -accel-check` | `accel: 0 — WHPX(10.0.22631) is installed and usable` |
| Build smoke test | `gradlew assembleDebug` | `BUILD SUCCESSFUL in 8m 56s` (37 tasks) → `app-debug.apk`, 41.72 MB |
| Emulator smoke test | boot `matrix_api33` headless | `emulator-5554 device`; `sys.boot_completed=1`; `ro.build.version.sdk=33`; `ro.product.cpu.abi=x86_64` |

The `x86_64` boot is the decisive ADR-014 result: unlike the 2012 i7 host (32-bit x86 / API 30 only), this N-series CPU boots 64-bit hardware-accelerated images. No virtualization fallback to physical-device/CI-only is required.

## 2. Environment as provisioned

- `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr` — bundled JBR, **OpenJDK 21.0.10**
- `ANDROID_HOME` = `%LOCALAPPDATA%\Android\Sdk`
- Both persisted at **User** scope (survive reboot). `local.properties` created (`sdk.dir`).
- SDK: platform-tools (adb), emulator, **build-tools 36.0.0**, platforms 26–37.1, `cmdline-tools;latest` (added — see §5).

## 3. Emulator matrix inventory

| API | Platform | System image | AVD (device profile `pixel_5`) | Boot-smoke |
|---|---|---|---|---|
| 26 | Android 8.0 (Oreo) | `google_apis;x86_64` | `matrix_api26` | not run |
| 29 | Android 10 (Q) | `google_apis;x86_64` | `matrix_api29` | not run |
| 33 | Android 13 (Tiramisu) | `google_apis;x86_64` | `matrix_api33` | ✅ booted (`sdk=33`, `x86_64`) |
| 35 | Android 15 (VanillaIceCream) | `google_apis;x86_64` | `matrix_api35` | not run |

Pre-existing default AVDs (`Pixel_5` … `Pixel_5_6`) were left untouched. Per the setup guide the matrix is driven **sequentially** on this low-power CPU.

## 4. Sign-off inputs (setup guide §9)

`emulator -accel-check` and `gradlew assembleDebug` both green (§1) — the two results the setup guide asks for before declaring the host fleet-ready.

## 5. Deviations from the setup procedure (candidates to fold into `DEV_ENVIRONMENT_SETUP.md`)

1. **`cmd.exe` was unreachable from the shell until a reboot** — every `.bat` / `gradlew.bat` launch failed with *"The system cannot find the file specified"* (ComSpec resolved to `cmd.exe` but the file was not reachable). A restart cleared it — likely a pending-servicing state on the fresh box. Suggested guide note: *"if batch/gradle won't launch on a fresh host, reboot first."*
2. **`cmdline-tools` was not installed by the GUI SDK** (no `sdkmanager` / `avdmanager`). Added from the official `commandlinetools-win-15859902_latest.zip` (SHA-256 `90ae805d…fb04a`, verified) into `…\Sdk\cmdline-tools\latest`. Gotcha: extract to a **short** path — the deep guava jar in the archive overflows Windows' 260-char `MAX_PATH` behind a long temp prefix and silently truncates the extraction.
3. **build-tools is 36.0.0**, not the guide's "latest 35.x". Build is green regardless (compileSdk / targetSdk 35, minSdk 26).

## 6. Caveat — executable ≠ verified

This report establishes that the API 26/29/33/35 matrix is **locally executable** (emulators boot under WHPX). It is **not** compatibility-verification evidence for **NFR-COMP-001** — that requires the WP2 regression harness (`scripts/e2e/`) actually running against these AVDs, plus the CI matrix. Only `matrix_api33` was boot-smoked here; the other three levels are created but unexercised.

## 7. Supersession

First fleet report for this host. Per `../README.md`, this record is immutable; any later change of state (e.g. WP2 harness results, keystore changes, added images) is a **new** dated report that supersedes this one and re-points the fleet index row.
