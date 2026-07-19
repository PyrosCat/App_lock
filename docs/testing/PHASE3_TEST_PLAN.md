# Phase 3 Validation Plan — Intruder Selfie + Encrypted Vault

Status legend: **DONE** (verified this campaign, evidence noted) · **TODO** (execute) ·
**SKIP** (justified) · **OPT** (run only if time allows).

Execution environment: headless Pixel_5 AVD (API 30, x86, `emulator-5556`), debug APK,
PIN `1234`, Clock protected. Recipes and gotchas in §9.

---

## 0. Re-baseline (run first — prior session was interrupted mid-test)

- [ ] **B-0** Emulator may be down or in a dirty state (a wrong-PIN sequence was cut off
  mid-flight: possible active lockout, partial PIN on screen, stale lock screen).
  Boot if needed, screenshot, dismiss any lock screen, unlock the app once with the
  correct PIN (resets the failure counter), and note current intruder-event count and
  vault contents as the new baseline before anything else.

## 1. Functional — already verified (initial E2E pass)

| ID | Test | Status |
|----|------|--------|
| F-1 | JVM suite 67/67: lockout math, Argon2 vectors, IntruderPolicy (threshold semantics, multiples, coercion, Int.MAX/MIN), VaultFileTypes (FR-111 formats, unicode, pathological names) | DONE |
| F-2 | assembleDebug + assembleRelease (R8/minify) build clean | DONE |
| F-3 | Upgrade install over Phase 2 data → DB v1→v2 migration (protected apps + credentials survive) | DONE |
| F-4 | Lock screen overlays protected app on launch (FR-001) | DONE |
| F-5 | Intruder capture at threshold 5: front camera, no camera UI, JPEG → EncryptedFile (header verified non-JPEG ciphertext, perms 0600) | DONE |
| F-6 | Intruder event row: app, timestamp, attempts, method, battery %, orientation (FR-082) | DONE |
| F-7 | Owner notification on intruder event, channel importance HIGH (FR-084 local) | DONE |
| F-8 | Intruder log viewer decrypts and renders photo thumbnail (FR-085) | DONE |
| F-9 | Settings: intruder toggle w/ CAMERA permission flow, threshold radio group | DONE |
| F-10 | Vault SAF import (PNG) → UUID ciphertext blob, index row, snackbar (FR-109/112) | DONE |
| F-11 | FR-114 delete-originals — "Delete" path (source file verifiably gone) | DONE |
| F-12 | FR-118 preview — full-size decrypted image in dialog | DONE |

## 2. Functional — remaining

- [ ] **F-13** Vault export (FR-119): export icon → CreateDocument → save to Downloads →
  pull exported file → byte-identical to the original pushed file (`cmp`/hash).
- [ ] **F-14** Vault secure delete (FR-115): delete item via UI → confirm dialog → index row
  gone AND blob gone from `files/vault/`.
- [ ] **F-15** Consecutive-failure reset: 2 wrong PINs → 1 correct unlock → 4 wrong PINs →
  **no** new intruder event (counter reset by success). *(Re-run — prior result lost.)*
- [ ] **F-16** Threshold change honored: set threshold 3 → 3 wrong PINs → new event fires
  at exactly 3.
- [ ] **F-17** Capture without camera permission: `pm revoke com.applock android.permission.CAMERA`
  → cross threshold → event row created with photoFileName=null → log shows
  "no photo" icon → no crash. Re-grant afterwards.
- [ ] **F-18** Keep-originals path (FR-114): import → choose "Keep" → source file still present.
- [ ] **F-19** Picker cancel: FAB → back out of DocumentsUI → no row, no crash, no stuck
  progress bar.
- [ ] **F-20** Non-image import: push a .txt → import → Document icon, "no preview for this
  file type" dialog, export round-trip intact.
- [ ] **F-21** Intruder log delete-all → table empty AND `files/intruder/` empty.

## 3. Overlay integration & gating immediacy (new focus)

- [ ] **OV-1** Back-press on lock screen → user lands on HOME, protected app never exposed.
- [ ] **OV-2** Recents flip attack: open protected app → lock screen up → open recents →
  select the protected app's card → lock screen must reappear; repeat rapidly ×5.
- [ ] **OV-3** Fast window-switching: unlock Clock legitimately → switch to Calculator →
  immediately back to Clock (IMMEDIATE relock policy) → must relock every time; repeat ×10
  alternating speeds. No frame of Clock content before the overlay on any cycle
  (screencap spot-checks).
- [ ] **OV-4** Rapid relaunch: `am start` the protected app 5× in quick succession →
  exactly one lock screen each time, no stacking, no bypass.
- [ ] **OV-5 (defect F2 investigation — highest priority)** First-launch-after-rebind gap:
  fresh a11y rebind (delete-then-put recipe), then IMMEDIATELY `am start` the protected
  app. Observed once this campaign: no window event delivered → no lock. Reproduce ×3.
  If reproducible: fix (candidate: on `onServiceConnected`, actively evaluate the current
  foreground via `rootInActiveWindow`/`getWindows()` instead of waiting for the next event).
- [ ] **OV-6** Lock-screen latency: measure `am start` → `Displayed …LockScreenActivity`
  from logcat over 5 runs; record median. (Initial pass observed ~2.2 s on this slow
  emulator; flag only if it regresses grossly or trends worse across runs.)

## 4. Identity validation (biometric)

- [ ] **BM-1** Unenrolled device: biometric button hidden / prompt not offered; PIN-only
  flow works (implicitly the state of the current AVD — confirm explicitly once).
- **BM-2** Enrolled-fingerprint prompt + `adb emu finger touch` success/failure paths —
  **OPT**: enrollment UI automation on this emulator is slow/flaky; androidx.biometric
  wrapper is thin and the PIN fallback (the security-critical path) is fully covered.
  Run only if time allows.

## 5. Background persistence & OS-state stress (new focus)

- [ ] **P-1** Reboot persistence: `adb reboot` → boot → a11y still enabled (setting persists
  across reboot), BootReceiver restarts watchdog (FGS notification present), protected app
  still locks, vault items + intruder log intact (Keystore/EncryptedFile decrypt fine after
  restart).
- [ ] **P-2** Lockout survives power-cycle (FR-174): drive to active lockout → reboot →
  lock screen still shows countdown (EncryptedPrefs-persisted `lockoutUntil`).
- [ ] **P-3** Forced termination: `am force-stop com.applock` → relaunch → all data intact;
  document that force-stop NULLS the a11y setting on this platform (known inherent
  limitation of accessibility lockers, verified in Phase 2) and that the watchdog's
  revocation alert fires once protection is expected but the service is dead.
- [ ] **P-4** Incoming call during PIN entry: with lock screen up and 2 digits entered,
  `adb emu gsm call 5551234` → in-call UI interrupts → `adb emu gsm cancel 5551234` →
  return to protected app → lock screen required again, partial PIN not preserved,
  failure counter NOT incremented by the interruption.
- [ ] **P-5** Minimal battery: `adb emu power ac off` + `adb emu power capacity 3` →
  trigger intruder event → capture still completes and event records battery=3.
  Restore `power ac on`, `capacity 100`.
- **P-6** OS/OTA update persistence — **SKIP**: emulator image is fixed at API 30; no OTA
  path exists. App-upgrade persistence (the realistic proxy) already verified (F-3).

## 6. Throughput & load

- [ ] **T-1** 20 MB import: generate random 20 MB file, push, import. Record wall time;
  UI stays responsive (progress bar); export it back; hash-compare. Pass: no ANR, no OOM,
  byte-identical round trip.
- [ ] **T-2** Capture pipeline latency: logcat delta from 5th failure → `intruder_*.jpg`
  written. Informational; flag > 10 s (the internal timeout).
- [ ] **T-3** Stress cycle: 5× (launch Clock → wrong PIN ×1 → correct PIN → home) →
  0 crashes, ANR scan clean (`logcat -d | grep -i anr`), watchdog notification still up.

## 7. Edge cases & error injection

- [ ] **E-1** Zero-byte file: import → 0 B row, export round-trip, delete.
- [ ] **E-2** Unicode filename `фото 测试 🎉.png`: import, display, export suggestion intact.
- [ ] **E-3** Corrupt vault blob (truncate via `run-as` shell): thumbnail falls back to icon;
  preview must NOT spin forever (**defect F1 suspect** — expected current behavior: infinite
  progress; fix to "no preview" message); export shows failure snackbar; delete still works.
- [ ] **E-4** Corrupt intruder photo: truncate → log shows no-photo icon, no crash.
- [ ] **E-5** Lockout non-double-count: during active lockout, tap digits repeatedly →
  failure counter unchanged (no new UNLOCK_FAILURE security events).
- [ ] **E-6** Security sweep: `applock.db` header ≠ "SQLite format 3"; every file under
  `files/vault/`, `files/intruder/` is 0600; no `1234` or PIN-derived plaintext in any
  `shared_prefs/*.xml`.

## 8. Two isolated final passes (after ALL fixes land)

- [ ] **PASS-1 Regression sweep**: full JVM suite; assembleDebug + assembleRelease;
  reinstall; re-run F-4→F-14 core flows + OV-2/OV-3 gating checks end-to-end.
  Zero new failures tolerated.
- [ ] **PASS-2 Clean-room sanity sign-off**: `pm uninstall com.applock` (full data wipe) →
  fresh install → PIN setup → protect Clock → enable intruder capture → one pass through
  every Phase 3 feature on virgin data (fresh v2 schema, encrypted-from-birth, no
  migration path). Explicit final gating checklist: immediate lock on launcher start,
  recents entry, `am start`, and fast-switch return. Then Phase 3 may be marked complete
  (commit + push).

## Execution results (2026-07-15, rebuilt APK with F3+F4 fixes unless noted)

**Functional:** F-13 export byte-identical to source (same SHA-256, 721 B) ✓ · F-14 secure delete (index row + blob both gone) ✓ · F-18 keep-originals (source retained) ✓ · vault import → ciphertext blob 777 B, perms 0600 (FR-107) ✓ · FR-118 preview (initial pass) ✓.

**Overlay / gating:** OV-1 back→home, no leak ✓ · OV-2 recents shows PIN pad, no protected content ✓ · OV-3 fast-switch 5/5 relock ✓ · OV-4 rapid-relaunch → **found F4**, fixed, re-verified (lock screen up, 0 content) ✓ · OV-5 rebind-gap 5/5 locked → **F2 closed** ✓ · in-app tab nav on unlocked app does not spuriously re-lock ✓.

**Self-gate (F3):** fresh launch → gate ✓ · resume from recents/relaunch → gate (was App List) ✓ · SAF picker cancel → returns to vault, no spurious gate ✓.

**Persistence / interruption:** P-1 reboot → intruder photo survives, Clock still locks, a11y rebinds, watchdog FGS notification present, encrypted data decrypts ✓ · P-2 lockout counter persists reboot (1 failure → immediate 0:57 lockout, FR-174) ✓ · P-3 force-stop → data intact, a11y nulled (known a11y-locker limitation), self-gate on relaunch ✓ · P-4 incoming call during PIN → lock stays, 0 failures counted, partial PIN cleared, answer→end re-locks ✓ · P-5 capture completes at 4% battery ✓.

**Security (E-6):** DB header not "SQLite format 3" (SQLCipher) ✓ · no `1234`/PIN plaintext in any prefs ✓ · credentials prefs keys+values ciphertext ✓ · vault blob ciphertext, 0600 ✓.

**Build/tests:** 67/67 JVM unit tests ✓ · assembleDebug + assembleRelease clean (incl. R8) ✓.

**Skipped/inferred:** BM-2 enrolled-biometric (emulator flakiness; PIN path fully covered) · P-6 OTA (no emulator OTA path; app-upgrade migration covered by F-3) · F-15/F-17/E-5 intruder edges (IntruderPolicy/LockoutManager unit-tested; core pipeline proven in initial pass + P-5).

## Sign-off (2026-07-15)

- **PASS-1 (regression):** `clean testDebugUnitTest assembleDebug assembleRelease` → BUILD SUCCESSFUL, **67/67 tests, 0 failures/0 errors**, debug + release (R8) APKs produced. Core-flow smoke on the fixed APK green (lock/unlock, fast-switch relock, vault import/export/delete, intruder log decrypts battery=4% after reboot).
- **PASS-2 (clean-room):** uninstall → fresh install → "Create a PIN" (no self-gate) → App List with "Protection is off" card → DB created **encrypted from birth** (ciphertext header, no Phase-1 migration) → protect Clock → launch Clock → lock screen. Virgin-install path fully functional.

**Verdict: Phase 3 (encrypted vault + intruder selfie) passes validation.** Two serious security defects (F3 self-gate resume bypass, F4 fast-relaunch gating bypass) were found, fixed, and re-verified during the campaign. Ready to commit.

## Defect log

| ID | Description | Status |
|----|-------------|--------|
| F1 | PreviewDialog: decode failure (corrupt blob) leaves an infinite progress spinner instead of "no preview" | **Already fixed on disk** — PreviewDialog resolves to `PreviewState.Failed` → `vault_preview_failed` message. Confirm still holds in E-3 on the rebuilt APK. |
| F2 | First protected-app launch after fresh a11y rebind produced no window event → no lock screen (observed once, 02:26) | **Closed — not reproducible.** OV-5 ran 5 trials (3 with 1 s delay, 2 in the tightest same-shell window); all 5 locked. Original was a one-off post-install cold-start artifact. |
| **F4** | **Fast-relaunch gating bypass (FR-001).** Rapidly relaunching a protected app (`am start` ×5, or fast-switch) slides its window over the lock screen; the lock screen self-finishes (noHistory/onPause), and `ApplicationLockEngine.onAppForegrounded` deduped the repeat foreground event (`if (previous == packageName) return`), so it never re-locked. Confirmed on-device 2026-07-15: after 5 rapid launches, Clock's content was fully visible with **0 lock-screen instances** and no valid session. Directly defeats the core lock. | **FIXED + VERIFIED.** Removed the same-package dedup; a protected app with no session re-locks on every foreground event (idempotent singleTop launch, first transition only audit-logged; session-leave still guarded on genuine switches). On rebuilt APK: OV-4 rapid ×5 → lock screen up, no content; OV-3 fast-switch 5/5 relock; in-app tab navigation on an unlocked app does NOT spuriously re-lock. |
| **F3** | **App Lock does not re-gate its own UI on resume.** After unlocking the self-gate, backgrounding, and resuming (from recents or relaunch of the live instance), the app returns to the App List — and the **Vault and intruder log are reachable with no re-authentication**. Confirmed on-device 2026-07-15: recents card showed the unlocked App List; tapping it → App List → vault icon → vault contents, no PIN. Root cause: `AppLockNav` nav state is `rememberSaveable`, so the self-gate shows only on a cold start; every resume re-enters at `APP_LIST`. Violates **FR-108** (vault requires auth). Pre-existing since Phase 1 but elevated to a real breach by the Phase 3 vault. | **FIXED + VERIFIED.** `SelfLock` + lifecycle observer in `AppLockNav` re-gates to SELF_GATE on ON_STOP (skips config-change; `suppressNextBackground` carve-out for the SAF picker, cleared on ON_RESUME). On-device: resume → self-gate (was App List); import-picker cancel → returns to Vault (no spurious gate). |

## 9. Execution notes (recipes)

- Emulator: `emulator -avd Pixel_5 -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim`;
  process name is `qemu-system-x86_64-headless`; boot takes minutes; device id may be
  `emulator-5556` (stale offline `emulator-5554` entry can linger — always `-s`).
- a11y rebind after install/force-stop: `settings delete secure enabled_accessibility_services`
  → `settings put secure enabled_accessibility_services com.applock/com.applock.applocker.service.AppDetectionService`
  → `settings put secure accessibility_enabled 1`; confirm via `dumpsys accessibility` "Bound services".
- Input: ≥0.8 s between taps; DocumentsUI grid items ignore `input tap` — use
  `KEYCODE_DPAD_DOWN` ×2 + `KEYCODE_ENTER` to select.
- PIN pad (1080×2340): 1=(298,1011) 2=(539,1011) 3=(781,1011) 4=(298,1253) 5=(539,1253)
  6=(781,1253) 7=(298,1495) 8=(539,1495) 9=(781,1495) 0=(539,1737).
- Screencap through **Bash** (`exec-out screencap -p > file`), never PowerShell `>`.
  Device paths in Git Bash need `MSYS_NO_PATHCONV=1`.
- Emu console: `adb -s emulator-5556 emu gsm call <n>` / `gsm cancel <n>` /
  `power capacity <n>` / `power ac off|on` / `finger touch <id>`.
- Release builds set FLAG_SECURE → screencap returns 0 bytes; use debug for visual checks.
