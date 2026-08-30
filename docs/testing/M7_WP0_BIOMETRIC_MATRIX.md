# M7 WP0 — Biometric-via-BAL on the emulator API matrix, 30/33/36 (NucBox)

**For:** the NucBox G5 emulator host (the fleet's device-verification machine).
**From:** the 2012 dev box, 2026-08-29. Git is the only cross-machine channel — run the GOVERNANCE
§3.2 session-start check, then `git checkout spike/m7-wp0`.
**Why this is owed:** ADR-020's Consequences ask that the transparent-Activity-via-BAL biometric path
be **proven on API 30/33/35/36**. The Moto G report
(`docs/reports/campaigns/2026-08-26_m7-wp0-spike_moto-g-2025.md`) proved it on **real hardware, API
35** (real Class-3 sensor). **API 30/33/36 are still owed on emulators.** The 2012 dev box **cannot**
run them: it has a known GPU-color-buffer cold-boot failure (systemui ANR / a hard SIGSEGV during
software-GPU boot), so all emulator overlay/biometric work is deferred to the NucBox. This is the last
ADR-020 platform residual before it can move Proposed → Accepted.

---

## 1. The mechanism (spike, already on the branch)

The throwaway spike (`app/src/main/java/com/applock/platform/spike/`) drives the whole path over adb:

- **`UsagePollService`** (`com.applock/com.applock.platform.spike.UsagePollService`) — a `specialUse`
  FGS. Actions: `com.applock.spike.SHOW` (draw the overlay), `…DISMISS`, `…STOP`, and
  **`com.applock.spike.BIOMETRIC`** → calls `SpikeBiometricActivity.launch(...)`.
- **`SpikeBiometricActivity`** — a transparent `FragmentActivity` (`exported=false`) that runs
  `BiometricPrompt.authenticate()` with `BIOMETRIC_WEAK` (accepts Class-3), title "M7 Spike unlock",
  negative button "Use PIN". It is launched **as a background-activity-launch permitted by the app's
  visible overlay window** — that BAL is the thing under test (BAL was restricted on **Android 10** and
  tightened again in **14/15**, i.e. API 29 → 34 → 35).
- Logs go to logcat tag **`M7Spike`** (the spike app's tag — note the OV-4 *test* uses `M7SpikeTest`, a
  different tag): `biometric SUCCESS` / `biometric ERROR <code> <msg>` / `biometric FAILED`.

Build + install the spike once per emulator (or reuse a prior build):
```bash
./gradlew assembleProdDebug --stacktrace
adb install -r app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb shell appops set com.applock android:get_usage_stats allow
adb shell appops set com.applock android:system_alert_window allow
```

## 2. Item A — BAL launch + prompt display (MANDATORY; any image, incl. aosp)

This is the real platform question and needs **no fingerprint hardware** — a device with no enrolled
biometric still proves the launch: the Activity starts (the BAL is permitted by the visible overlay),
`BiometricPrompt.authenticate()` runs, and the framework returns an error callback (no-biometrics /
no-hardware). Run it on **api30, api33, api36** (your usual manually-booted headless emulator recipe —
the same rigs as Items 1/3; aosp images are fine here, no fingerprint HAL needed).

**The sequence matters.** The overlay has to be up *because the protected app is foreground* — that
leaves App Lock in the background with only its overlay window visible, which is the real BAL context.
Bring the protected app forward and let the poll auto-draw the overlay; do **not** just `SHOW` the
overlay and press Home, because the poll then sees the launcher (unprotected) and *dismisses* the
overlay, removing the BAL basis before the launch.

```bash
PKG=com.applock; SVC="$PKG/com.applock.platform.spike.UsagePollService"
TARGET=com.android.deskclock   # any INSTALLED normal (non-Settings) app; on google_apis use com.google.android.deskclock
adb shell am start -n "$PKG/com.applock.platform.spike.SpikeLauncherActivity"   # 1. foreground App Lock (so the FGS start is allowed on API 31+)
sleep 1
adb shell am start-foreground-service -n "$SVC" --es target "$TARGET" --el interval 400   # 2. start the poll FGS
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1              # 3. bring the "protected" app to the foreground
sleep 2                                                                          #    the poll detects it, draws the overlay; App Lock is now background
adb shell dumpsys window | grep -i AppLockSpikeOverlay                           #    confirm the overlay window is up over the target
adb logcat -c                                                                    # 4. CLEAR logcat FIRST — otherwise a biometric callback left in the buffer from an earlier run can false-pass step 6
adb shell am start-foreground-service -n "$SVC" -a com.applock.spike.BIOMETRIC   # 5. BAL: App Lock (background) launches the host — permitted by a BAL exception (visible overlay and/or the held SAW permission; see the anchor note)
sleep 1
adb logcat -d -s M7Spike | tail -8                                              # 6. the biometric line proves the launch (tag M7Spike) — only THIS run's, since the buffer was just cleared
adb shell dumpsys activity activities | grep -i SpikeBiometricActivity           #    supplementary (the transparent host may already have finished)
adb shell am start-foreground-service -n "$SVC" -a com.applock.spike.STOP
```

**Pass (per lane):** a `M7Spike: biometric …` line appears — `SUCCESS`, or an `ERROR` such as
no-biometrics (11) / no-hardware (12) / hw-unavailable (1). Any of these is a PASS for Item A: it means
the transparent host **launched from the background via the overlay's BAL** and the prompt path ran (the
error just reflects the emulator having no enrolled print). **Fail = no biometric line AND a
background-activity-start / BAL denial in the main logcat**
(`adb logcat -d | grep -iE "background activity|abort.*launch|BAL"`) — that is the ADR-020 escalation;
capture it.

**Anchor the evidence to *this* run.** Step 4 clears the buffer, so the step-6 line is necessarily from
this launch (never a stale callback). The definitive launch proof is the `ActivityTaskManager: START …
SpikeBiometricActivity` line — printed with a fresh timestamp at the fire, it cannot be stale — and it
also names the qualifying BAL reason (e.g. `BAL_ALLOW_NON_APP_VISIBLE_WINDOW`, or "SYSTEM_ALERT_WINDOW
permission is granted"): `adb logcat -d | grep "START .*SpikeBiometricActivity"`. Note the app holds
SAW, which is itself a standing BAL exception on every version, so the launch does not strictly require
a drawn overlay — the visible overlay is one qualifying reason, the held SAW permission another.

## 3. Item B — real fingerprint match (OPTIONAL; needs a google_apis image)

The Moto G already proved a real Class-3 match on API 35, so this only adds emulator confirmation.
It needs the **fingerprint HAL**, which bare aosp images lack — use a **google_apis** (or
`google_apis_playstore`) image for the lane(s) you want to cover:
`sdkmanager "system-images;android-33;google_apis;x86_64"` then create/boot that AVD.

```bash
adb shell locksettings set-pin 1234        # a device credential is required before enrolling a print
# Settings > Security > Fingerprint > enrol; during "touch the sensor", simulate the sensor:
adb emu finger touch 1                      # repeat ~6× to complete enrolment (confirm in the Settings UI)
# then run the Item-A sequence (foreground App Lock -> FGS -> launch TARGET -> BIOMETRIC), and authenticate:
adb emu finger touch 1
adb logcat -d -s M7Spike | grep -i "biometric SUCCESS"
```

**Pass:** `M7Spike: biometric SUCCESS` after the simulated touch, on at least one google_apis lane.
The prompt is `FLAG_SECURE` in production but the spike host is not, so a screenshot is fine here if you
want visual evidence. If an image reports `canAuthenticate() != SUCCESS` even after enrolment, note it
and rely on Item A + the Moto G real-hardware pass — Item B is confirmatory, not gating.

## 4. Recording the results

Add the biometric-matrix result to a **new dated NucBox report** under `docs/reports/campaigns/`
(e.g. `YYYY-MM-DD_m7-wp0-biometric-matrix_nucbox-g5.md`), or fold it into your next NucBox campaign
report — per lane: Item A launch outcome (30/33/36) and any Item B match. State the commit produced
against and the requirement it bears on (**FR-027 lock display / the ADR-020 biometric path**). Update
the NucBox fleet-index row in `docs/reports/README.md`. Do **not** edit the Moto G report — each host
keeps its own (reports are immutable once committed).

## 5. Notes

- **What closes:** Item A green on 30/33/36 closes ADR-020's "biometric-via-BAL proven on API
  30/33/35/36" residual (Moto G = 35 real; NucBox = 30/33/36 emulator; Item B is a bonus). After this,
  ADR-020 has no open platform evidence gap — the lead can accept it (with ADR-021) once the poll
  interval and NFR-PERF-012 figure are ratified.
- PIN is always the production fallback, so biometric is never a hard dependency; Item A is about the
  BAL *launch*, not the sensor.
- `SpikeBiometricActivity` is `exported=false`, so it cannot be `am start`ed directly — drive it only
  through the service's `BIOMETRIC` action (which is why the overlay must be shown first, to supply the
  visible-window BAL basis).
- Same fleet split as the other WP0 lanes: emulators here on the NucBox, real hardware on the Moto G;
  the 2012 box is adb-host + builder only (it segfaults on emulator cold boot).
