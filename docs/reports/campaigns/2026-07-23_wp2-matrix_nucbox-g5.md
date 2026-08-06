# WP2 Regression Harness — Emulator Matrix API 26/29/33/35 (NucBox G5)

**Date:** 2026-07-23 (filed)
**Author / host:** NucBox G5 — GMKtec, Windows 11 Pro (10.0.22631), Intel N-series x86_64 (on-host assistant)
**Devices under test:** AVDs `matrix_api26` (Android 8.0), `matrix_api29` (Android 10), `matrix_api33` (Android 13), `matrix_api35` (Android 15) — all `google_apis;x86_64`, pixel_5 profile (1080×2340), headless under WHPX (`-gpu swiftshader_indirect`, `-no-snapshot-load`).
**Produced against:** commit `7173284`. App under test: debug `app-debug.apk` v0.1.0 (Phase-3 app code; no `app/**` change since). Harness `scripts/e2e/` as of `7173284` (the lead's turnkey fixes from `a32780b`).
**Verifies / observes:** WP2 security-freeze gating across the emulator matrix — core lock/PIN, IMMEDIATE relock (OV-3), F4 rapid-relaunch defense (OV-4), F3 self-gate/FR-108. Fills the emulator-matrix gap the Moto G report (`2026-07-23_wp2-regression_moto-g-2025.md`) left open.
**Verdict:** ⚠️ **MIXED.** Self-gate (FR-108) and IMMEDIATE relock verified across the whole matrix; **turnkey setup confirmed working on every level**. But the **F4 rapid-relaunch defense (OV-4) fails intermittently on every emulator level** — a prevalent, high-variance window-ordering race (§2), **not** graded by API. The `run_all -n 2` gate passed on API 29/33 and failed on API 26/35, but that pass/fail is largely luck: a focused loop finds a real bypass on **all four** levels, including the two that passed the gate.

---

## 1. Per-level results

Env: `TAP_GAP=1.0 FG_WAIT=15 LOCKSCREEN_WAIT=12` for `run_all`; `LOCKSCREEN_WAIT=6` for the focused OV-4 loops. Turnkey setup (install via `host_path`, adb a11y, PIN, Clock protection) provisioned **fully on all four** — the lead's `a32780b` fixes are confirmed on the emulator.

| API | `run_all -n 2` verdict | smoke_core | ov3 relock | ov4 (F4) | f3 self-gate |
|---|---|---|---|---|---|
| 26 | **FAIL** | 2/2 PASS | 2/2 PASS (10/10 each) | PASS, **FAIL** | 2/2 PASS |
| 29 | PASS 2/2 | 2/2 PASS | 2/2 PASS (10/10 each) | 2/2 PASS | 2/2 PASS |
| 33 | PASS 2/2 | 2/2 PASS | 2/2 PASS (10/10 each) | 2/2 PASS | 2/2 PASS |
| 35 | **FAIL** | **FAIL**, PASS | 2/2 PASS (10/10 each) | PASS, **FAIL** | 2/2 PASS |

Every OV-4 (and the one smoke) failure is the **same window-ordering race** (§2); no other check regressed. OV-3 relocked on **every one of the 10 returns in every run at every level** — the IMMEDIATE-relock property is solid. (API 33 was *also* run on the old harness with manual workarounds on 2026-07-22 — likewise 2/2; see that report.)

## 2. Finding — F4 rapid-relaunch window-ordering race

**Focused OV-4 characterization** — repeat the 5×`am start` burst, then `wait_lockscreen` (full poll) and check whether the protected app is foreground. "BYPASS" = no lock screen after the full poll (12 s for the API-26 loop, 6 s for the others) with the protected app foreground; the API-26 loop additionally re-probed at +1.5 s and the exposure persisted.

| API | Bursts | PASS | INCONSISTENT (lock up + app also fg) | BYPASS (no lock, app fg) | Non-clean rate |
|---|---|---|---|---|---|
| 26 | 10 (2 loops) | 7 | 1 | 2 | **~30%** |
| 29 | 8 | 7 | 0 | 1 | **~12%** |
| 33 | 8 | 5 | 0 | 3 | **~37%** |
| 35 (emu) | 6 | 6 | 0 | 0 | **0% in-loop** (but `run_all` caught 1 OV-4 + 1 smoke) |

The rate does **not** track API level — API 33 (~37%) is *higher* than API 29 (~12%); API 35's in-loop 0/6 sits next to its `run_all` failures. This is a **high-variance timing race present across the whole emulator matrix**, not an API-version property. Reference: **API 35 on real Moto G 2025 hardware was clean** (`…_wp2-regression_moto-g-2025.md`).

**Root cause — not a detection gap.** On API 26, logcat during bursts showed the engine received **5–8** `AppLockEngine: com.google.android.deskclock -> LockDecision(requiresAuthentication=true, reason=protected app, no session)` per burst — it got an accessibility event for every relaunch and launched the lock screen each time. The failure is that the rapidly-relaunched Clock window **outraces the `noHistory` LockScreenActivity**, exactly the mechanism the code anticipates and tries to mitigate: [`ApplicationLockEngine.kt:61`](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt) ("Rapidly relaunching a protected app slides its window over our lock screen, which then self-finishes (noHistory)") and the re-launch on every event via `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP` ([line 123](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt)). That mitigation **does not reliably win the race on the emulators** — the protected app is left foreground with no lock screen for the full poll window (6–12 s).

The one API-35 `smoke_core` failure ("wrong PIN did NOT keep the lock screen — top=deskclock") is the **same race in the single-relaunch path** ([`smoke_core.sh:25`](../../scripts/e2e/smoke_core.sh)), not a wrong-PIN acceptance.

## 3. Interpretation & caveats (honesty per GOVERNANCE §1.5)

- **Prevalent, not graded by API.** Earlier data suggested an API gradient; adding API 33 (~37%) falsified that. The race hits every emulator level at a high but noisy rate (12–37% in focused loops), with no monotonic API ordering. The dominant correlate is **emulator timing** on this low-power host with software GPU, not the Android version.
- **Real-hardware impact is unresolved.** The one real device tested (Moto G 2025, API 35, fast arm64) was clean; whether a **slower or older real device** reproduces it is unknown. So this is a reproducible-on-emulator race whose production severity needs a real-hardware check — not yet a confirmed nor a dismissed production bypass.
- **The `-n 2` gate is unreliable for this property.** API 29 **and** API 33 both passed `run_all -n 2`, yet focused loops found real bypasses on both (12% and 37%). A green 2/2 does **not** prove the F4 defense holds; the gate under-samples a probabilistic race.
- **Not a harness defect.** The harness (post-`a32780b`) provisioned and asserted correctly; the "no lock"/"inconsistent" outcomes reflect real device state, corroborated by logcat.

## 4. What is solidly verified across the matrix

- **Turnkey provisioning** (install/a11y/PIN/Clock-protect) on API 26/29/33/35 emulators.
- **OV-3 IMMEDIATE relock:** 10/10 on every run, every level.
- **F3 self-gate / FR-108:** PASS on every run, every level (26/29/33/35 emulator), adding to the API 35 real-hardware coverage.
- **Core lock/PIN** (smoke): the only smoke failure was the §2 race, not a lock/PIN logic error.

## 5. Recommendations (for the harness/app owner — not actioned here)

1. **App:** strengthen the F4 mitigation so the lock screen wins the relaunch race deterministically (the re-launch-on-event approach loses it 12–37% of the time on emulators). This is the security-critical item.
2. **Real-hardware validation:** re-run OV-4 (many bursts) on a **slower/older real device** to settle whether this is an emulator-only timing artifact or a real production exposure — the decisive open question.
3. **RISK_REGISTER:** consider an R-002-style entry — a lock-engine security race across the supported range.
4. **Harness:** raise OV-4's burst count / add a repeat so a green result is meaningful; `-n 2` masked the race on API 29 and 33.

## 6. Supersession

First emulator-matrix WP2 campaign for this host. Immutable; a later lock-engine change (e.g. an F4-race fix) or the real-hardware validation in §5 re-runs this and files a new dated campaign report superseding this one.
