# M7 WP0 Emulator Lanes — Decisive A/B, api36 Matrix, Restart Cells (NucBox)

**For:** the NucBox G5 emulator host (the fleet's device-verification machine).
**From:** the 2012-box session, 2026-08-27. Git is the only cross-machine channel.
**What remains:** the WP0 spike, the durable OV-4 test, and the API-36 toolchain are on branch
`spike/m7-wp0`; the dev box has finished the Moto G real-hardware half; the three emulator lanes below
are all that's left before the ADRs can be accepted. Run the governance session-start check, then
`git checkout spike/m7-wp0`. `M7_PLAN.md` holds the full pass/fail protocol and the R-002 disposition
rule if you need the fine print.

---

## 1. Context — what the NucBox owns

The dev box has validated everything real hardware can show, on the Moto G 2025 (arm64, API 35),
recorded in `docs/reports/campaigns/2026-08-26_m7-wp0-spike_moto-g-2025.md`: OV-4 no-regression
(TOP=25, ABSENT=0), latency plus the poll-interval sweep (p95 tracks the interval: ~136 ms at 100 ms up
to ~456 ms at 400 ms), biometric-via-BAL, and the screen-off pause with a CPU/wakelock proxy.

But the Moto G is a budget single-OEM device that was already clean on the old engine, so it can only
show *no-regression* — it cannot prove the race is fixed. The decisive proof needs the slow software-GPU
emulator that actually reproduces the race, and the API-36 coverage needs an emulator too. That is the
NucBox's job:

| On `spike/m7-wp0` | What it is |
|---|---|
| `app/src/main/java/com/applock/platform/spike/` | The throwaway spike: a `UsagePollService` poll service + an `OverlayController` overlay (window title `AppLockSpikeOverlay`) + a biometric host + a launcher. Drive it with `am start-foreground-service … --es target <pkg> --el interval <ms>`; actions are `com.applock.spike.STOP` / `SHOW` / `DISMISS` / `BIOMETRIC`. |
| `app/src/androidTest/java/com/applock/e2e/OverlayRaceUiTest.kt` | The durable OV-4 race check. Black-box (drives `am`/`appops`, reads `dumpsys window`); target is Google Maps with the launcher resolved at runtime; burst counts come from instrumentation args. |
| `app/build.gradle.kts` | The API-36 bump (AGP 8.13.2 / Gradle 8.13 / target 36; AGP auto-installs the `android-36` platform) and the `api36` GMD device, wired into the `ci` group (30+35+36) and `full` (…+36). |

ADR-020 and ADR-021 are still Proposed; they get accepted once this evidence lands.

## 2. Item 1 — Decisive R-002 A/B (the hard gate)

On the software-GPU rig that reproduces the race (the config from the M1 R-002 work, e.g.
`-gpu swiftshader_indirect`), produce a paired result in one session:

- **Old engine, as a positive control:** run the M1 Activity-lock burst
  (`scripts/e2e/ov4_rapid_relaunch.sh` against the `main` build) and confirm it still fails —
  `ABSENT`/`BEHIND` at 12–37 % or more. If it does not fail, the rig isn't exercising the race and the
  comparison is void; retune the rig rather than pass it.
- **New engine:** run `OverlayRaceUiTest` against the spike build and confirm the overlay is **never
  `ABSENT`** (a brief `BEHIND` flicker that self-heals within one poll is allowed).

```bash
adb logcat -G 16M   # enlarge the buffer first — the am-start spam otherwise rotates the counts out
adb shell am instrument -w -e class com.applock.e2e.OverlayRaceUiTest \
  -e ov4_bursts 50 -e ov4_relaunches 20 -e ov4_repeat 5 \
  com.applock.test/androidx.test.runner.AndroidJUnitRunner
# the TOP/BEHIND/ABSENT counts print to logcat tag M7SpikeTest
```

**Pass:** the old engine reproduces the failure and the new overlay holds `ABSENT` = 0 across the whole
burst, in the same session. **If the new overlay does not eliminate `ABSENT`, stop and escalate** — the
overlay remediation would be unproven and the milestone premise is in doubt. This A/B, not the Moto G,
is what actually moves R-002; never close R-002 on single-OEM evidence alone.

## 3. Item 2 — Full smoke matrix incl. api36 + CI (30/35/36)

Run the reworked instrumentation suite (the smoke seed under `app/src/androidTest/.../smoke/` plus
`OverlayRaceUiTest`) across the emulator lanes. GMD manages the emulators for you.

```bash
# CI group = api30 + api35 + api36
./gradlew ciGroupProdDebugAndroidTest --stacktrace

# full group = api26/29/30/33/35 + api36  (or a single lane: api36ProdDebugAndroidTest)
./gradlew fullGroupProdDebugAndroidTest --stacktrace
```

Confirm the `api36` system image is present and actually boots — that confirmation is itself part of
this item. The API-29 image's tight heap still OOMs the 19 MiB Argon2 case (the caveat and workaround
are in `WP8_GMD_MATRIX.md`). `OverlayRaceUiTest` reads `mCurrentFocus` out of `dumpsys window`, whose
format shifts between API levels, so sanity-check that grep on each lane. And watch for a silent skip: a
missing target package makes the test assume-skip and report green having asserted nothing, so confirm
Maps resolved and the test really ran. **Pass:** every lane green, api36 included, nothing skipped.

## 4. Item 3 — Restart cells on API 36

Confirm the three lifecycle behaviours on the api36 emulator, driving the spike with a protected target.
Expected behaviour is in parentheses:

1. **Boot start** — reboot with protection configured; `BootReceiver` best-effort starts the poll
   service. (It starts; or if the OS defers the post-boot foreground-service start, the app reports
   *Action required* on next entry — never a false *Protected*.)
2. **Killed while visible** — with the overlay up over a protected app, kill the poll service. The
   sticky recreation keeps the detector alive and redraws the overlay on the next tick. (Overlay
   re-presents; a redraw failure is *Protection interrupted*, never a false *Protected*.)
3. **Process death** — kill the app process; the OS recreates the service, which re-bootstraps clean and
   waits for the first fresh observation. (State is re-derived, not restored; no stale request or
   retroactive lock.)

The spike is a lite validator (the full readiness and health surface is production WP3/WP4 work), so
what you're recording here is the *platform* behaviour: does the start or recreate happen, does the
overlay redraw. **Pass:** each cell behaves as above on API 36.

## 5. Recording the results

Write one dated NucBox report under `docs/reports/campaigns/`, e.g.
`YYYY-MM-DD_m7-wp0-emulator_nucbox-g5.md`, covering all three items — the A/B counts (old vs new), the
matrix results per lane, and the restart-cell observations. State the commit it was produced against and
the requirements it bears on (FR-026/027/028, NFR-PERF-012). Add a fleet-index row for the NucBox in
`docs/reports/README.md`. Do not edit the Moto G report — each machine keeps its own.

## 6. Notes

- Task names: `ciGroupProdDebugAndroidTest`, `fullGroupProdDebugAndroidTest`, or a single
  `api36ProdDebugAndroidTest`; all use the `prodDebug` androidTest variant. `--dry-run` realizes the GMD
  device tasks without booting anything, which catches config errors fast.
- After these three lanes, the only R-002 residual left is the Firebase Test Lab multi-OEM sweep (the
  same `OverlayRaceUiTest` artifact), after which the lead accepts the poll interval and moves ADR-020 /
  ADR-021 to Accepted.
