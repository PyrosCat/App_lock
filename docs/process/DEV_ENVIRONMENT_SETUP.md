# Development Environment Setup (Windows)

Reproducible setup for a build + emulator/verification host. Written for the **NucBox G5**
(Windows 11, Intel N-series CPU) joining the ADR-014 verification fleet 2026-07-20, but applies
to any Windows dev machine. Satisfies NFR-COMP-006 (build-environment portability) and
NFR-MNT-013 (knowledge transfer).

Fleet context (ADR-014): 2012 i7 host (Pixel_5 API 30 x86 only), NucBox G5 (this guide),
Moto G 2025 physical device (arriving 2026-07-22).

---

## 0. What this host is for

- **Build** the project (`gradlew` — faster than the 2012 host).
- **Run the emulator matrix** — API 26 / 29 / 33 / 35 AVDs for ADR-014 compatibility evidence.
  Unlike the 2012 host, the N-series CPU is a modern x86_64 part, so **x86_64 system images
  should run** (the 2012 box was stuck on 32-bit x86).
- **Drive the WP2 regression harness** over adb (against emulators here, and the Moto G later).

Because the CPU is low-power, treat the matrix as **sequential** (one AVD at a time) rather than
parallel — correctness over speed. RAM permitting, two concurrent is usually safe.

## 1. Enable hardware acceleration — **do this first** (the critical checkpoint)

On a CPU-limited host, an *unaccelerated* emulator is unusably slow, so confirm acceleration
before installing anything heavy.

> **Fresh-host gotcha (NucBox G5, 2026-07-20):** on a brand-new box, `cmd.exe` / `.bat`
> launches (including `gradlew.bat`) can fail with *"The system cannot find the file
> specified"* even though `cmd.exe` exists — a pending-servicing state. **Reboot once** before
> troubleshooting anything else.

1. **BIOS/UEFI:** ensure Intel Virtualization Technology (VT-x) is **Enabled** (usually default
   on mini PCs; reboot into UEFI to check if step 3 fails).
2. **Windows features:** open "Turn Windows features on or off" → enable **Windows Hypervisor
   Platform** (WHPX) and **Virtual Machine Platform** → reboot. (WHPX coexists with WSL2/Hyper-V;
   it's the recommended path over the deprecated HAXM.)
3. **Verify after the SDK is installed** (step 4): run
   `%LOCALAPPDATA%\Android\Sdk\emulator\emulator -accel-check`
   → expect `accel: 0  WHPX ... is installed and usable` (or "accel is working").
   If it reports HAXM/AEHD instead, that's fine too as long as it says usable.

If acceleration cannot be enabled, stop and tell me — the NucBox then falls back to driving the
Moto G over adb, and the API matrix moves to CI's Linux runners (KVM).

## 2. Install the JDK + Android SDK

Simplest path (matches the 2012 host): **install Android Studio** — it bundles a JDK (the JBR),
the Android SDK, the emulator, and the AVD manager.

- Download from https://developer.android.com/studio, install with defaults.
- Let it install the latest **platform-tools** (adb), **emulator**, and a default platform.
- **`cmdline-tools` may be missing** (the GUI SDK skipped it on the NucBox — no `sdkmanager`
  / `avdmanager`). Add it via SDK Manager → *SDK Tools* → "Android SDK Command-line Tools", or
  drop the official `commandlinetools-win` zip into `…\Sdk\cmdline-tools\latest\`. If you
  extract the zip manually, **use a short destination path** — a deep jar inside overflows
  Windows' 260-char `MAX_PATH` behind a long temp prefix and truncates silently. Verify the
  zip's SHA-256 before extracting.

## 3. Environment variables (persist them once)

From any terminal (adjust the JBR path if Studio installed elsewhere):

```
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

Open a **new** terminal afterward so they take effect. (`JAVA_HOME` is the same lesson from the
2012 host — Gradle fails without it. `ANDROID_HOME` lets Gradle find the SDK without a
`local.properties`, though we set that too in step 5.)

## 4. Install the SDK components for the matrix

Via Android Studio's SDK Manager (or `sdkmanager` CLI), install:

- **Platforms:** Android API **26, 29, 33, 35** (35 = compile/target level).
- **System images** (x86_64, `google_apis` flavor — includes Play-services-free Google APIs
  needed for some tests): one per API level above.
- **Build-tools** (35.x or newer — 36.0.0 verified green on the NucBox), **platform-tools**,
  **emulator**.

`sdkmanager` one-liner (from `%ANDROID_HOME%\cmdline-tools\latest\bin`):
```
sdkmanager "platform-tools" "emulator" "platforms;android-35" ^
  "system-images;android-26;google_apis;x86_64" ^
  "system-images;android-29;google_apis;x86_64" ^
  "system-images;android-33;google_apis;x86_64" ^
  "system-images;android-35;google_apis;x86_64"
```

## 5. Clone the repo + point local.properties at the SDK

```
git clone https://github.com/PyrosCat/App_lock.git
cd App_lock
```

Create `local.properties` (gitignored — not in the repo) with the SDK path:
```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```
(Double-backslashes, escaped colon — Java properties format.)

## 6. Build smoke test (proves the toolchain)

```
gradlew.bat assembleDebug
```
Expect `BUILD SUCCESSFUL` and an APK under `app\build\outputs\apk\debug\`. This is the same
green the 2012 host and CI already produce, so any failure here is environment, not code.

## 7. Create the AVDs + emulator smoke test

Create one AVD per matrix level (AVD Manager GUI, or `avdmanager create avd`). Then boot one
headless and confirm adb sees it:

```
emulator -avd <avd_name> -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
adb devices          # should list emulator-5554  device
adb shell getprop ro.build.version.sdk   # confirms the API level
```

(Headless flags mirror the 2012-host recipe. On the NucBox, also try **without** `-no-window`
and with `-gpu host` — the newer GPU may handle windowed mode the 2012 box couldn't.)

## 8. Debug-signing note (matters for cross-machine upgrade tests)

Android auto-generates a **per-machine** `~/.android/debug.keystore`, so a debug APK built here
won't upgrade-install over one built on the 2012 host (signature mismatch →
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Before WP6's upgrade-install tests, we'll commit a
**shared** debug keystore (the repo's `.gitignore` already allows it via `!debug.keystore`) so
all hosts sign identically. Until then, keep each machine's upgrade tests self-contained.

## 9. Ready for WP2

Once steps 6–7 pass, this host can run the WP2 regression harness (`scripts/e2e/`, landing with
WP2). Report the `emulator -accel-check` result and the `assembleDebug` outcome and I'll confirm
the NucBox is fleet-ready and update ADR-014's fleet note.
