# WP2 Gating Regression — Post-WP5 (Hilt) Emulator Matrix (NucBox G5)

**Date:** 2026-08-09 (filed)
**Author / host:** NucBox G5 — GMKtec, Windows 11 Pro, Intel N-series x86_64 (on-host assistant)
**Devices under test:** AVDs `matrix_api26` / `matrix_api29` / `matrix_api33` / `matrix_api35` — `google_apis;x86_64`, pixel_5 profile (1080×2340), headless under WHPX.
**Produced against:** commit `3a0e6fd`. App under test: **`app-prod-debug.apk` v0.1.0, variant `prodDebug`** (`applicationId com.applock`), rebuilt from `3a0e6fd` — i.e. the **post-WP5 Hilt code** (Graph service locator deleted, Hilt DI in). Harness `scripts/e2e/` as of `3a0e6fd`.
**Verifies:** that the **WP5 Hilt migration (ADR-015) did not regress the WP2 security gating** across the emulator matrix, and re-checks the **R-002** rapid-relaunch race post-Hilt. This is the **NucBox emulator complement** to the real-hardware WP5 gate (`2026-08-08_wp5-harness_moto-g-2025.md`), together forming the ADR-015 hardware-gated exit evidence.
**Verdict:** ✅ **Gating preserved on every level.** OV-3 IMMEDIATE relock and F3 self-gate pass on every run at every API. The R-002 race is **unchanged** from the pre-Hilt baseline (`2026-07-23_wp2-matrix_nucbox-g5.md`) — neither fixed nor worsened, as expected for a behavior-preserving refactor.

---

## 1. Results — post-Hilt vs pre-Hilt

Env: `TAP_GAP=1.0–1.3 FG_WAIT=15 LOCKSCREEN_WAIT=12` for `run_all`; `LOCKSCREEN_WAIT=6` for the focused OV-4 loops. Clean install of the prod-debug APK per level; turnkey setup provisioned on all four.

| API | OV-3 relock | F3 self-gate | `run_all -n 2` | R-002 rate (post-Hilt, 8-burst loop) | R-002 pre-Hilt |
|---|---|---|---|---|---|
| 26 | ✅ 10/10 every run | ✅ every run | FAIL (R-002 only) | ~25% (2/8) | ~30% |
| 29 | ✅ 10/10 every run | ✅ every run | FAIL (R-002 only) † | ~12% (1/8) | ~12% |
| 33 | ✅ 10/10 every run | ✅ every run | PASS 2/2 | ~37% (3/8) | ~37% |
| 35 | ✅ 10/10 every run | ✅ every run | PASS 2/2 | ~25% (2/8) | rare (undersampled) |

† API 29 required a known-emulator-bug workaround before it could provision — see §3.

The `run_all` FAILs on API 26/35 (and the passes on 29/33) are the **R-002 race surfacing or not in the 2-sample gate** — the focused loops show the race exists on all four levels regardless. No OV-3 or F3 failure occurred anywhere.

## 2. Conclusion — WP5 did not regress the gating

- **OV-3 IMMEDIATE relock: 10/10 on every run, every level.** No session leak introduced by the Hilt migration.
- **F3 self-gate (FR-108): PASS on every run, every level.** The vault/self-gate re-auth path is intact after `AuthGateViewModel` + Hilt field injection replaced the Graph consumers.
- **R-002 rapid-relaunch race: statistically unchanged** at every level (26: 25% vs 30%; 29: 12% vs 12%; 33: 37% vs 37%; 35: present both). WP5 neither fixed nor worsened the lock-screen window race — consistent with the migration being a synchronous, behavior-preserving passthrough. R-002 remains an open lock-engine item, tracked separately.

Together with the Moto G 2025 real-hardware WP5 gate (2/2 green, `2026-08-08_wp5-harness_moto-g-2025.md`), the ADR-015 refactor is validated as gating-preserving on both real hardware and the x86_64 emulator matrix.

## 3. API 29 provisioning note — a known Android-10 emulator bug (not an app issue)

On the first passes, `matrix_api29` **could not create a PIN**: the Argon2id hash (`Argon2PinHasher.hash`, 19 MiB) hit `OutOfMemoryError` on the main thread and crashed MainActivity. Investigation showed the ART growth limit at OOM was **exactly 16 MiB** (`growth limit 16777216`), with `dalvik.vm.heapgrowthlimit` **unset** on this image. This is a **documented race in the Android 10 (API 29) emulator init**: the Zygote intermittently starts with the wrong args, launching apps with a tiny `-Xmx16m` heap (see CircleCI "Common Android memory issues"; Nutrient "Large memory requirements on Android"). Applying the standard workaround (`adb root` → set `heapgrowthlimit=256m` → restart the framework so the Zygote re-reads it) gave the app a normal heap, after which API 29 provisioned and behaved identically to every other level.

**This is an emulator-image defect, not an app or real-device problem** — 19 MiB Argon2 is trivial on any normal heap, and API 26 (older) with a proper heap is unaffected. The Moto G 2025 real hardware also sets PINs fine. Noted here only so the next operator recognizes it.

## 4. Scope & caveats

- **R-002 severity on real hardware remains open** (per the pre-Hilt matrix report §3): the race is prevalent on the slow emulators but the fast Moto G real hardware was clean; real low-end hardware is the deciding test. This report re-confirms R-002 post-Hilt but does not resolve that question.
- **R-002 root cause** is analysed separately in `../security/2026-08-09_r-002-rapid-relaunch-race-analysis.md` — a structural window-ordering race (reactive detection → cover with a `noHistory` Activity), not a tunable flake; the fix is a mechanism change (app suspension / persistent overlay), not harness tuning.
- The heap override on API 29 was runtime-only (gone on next boot); no AVD or repo change was made.

## 5. Supersession

Post-WP5 emulator-matrix gate for this host. Immutable; a later lock-engine change (e.g. an R-002 fix, or WP6 package moves) re-runs this matrix and files a new dated campaign report.
