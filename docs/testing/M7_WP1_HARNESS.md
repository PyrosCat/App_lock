# M7 WP1 Harness Validation — Reworked `scripts/e2e` Against the Spike (Moto G + NucBox)

**For:** the dev box (Moto G 2025, real hardware) and the NucBox G5 (emulator lanes).
**From:** the 2012-box session, 2026-08-31. Git is the only cross-machine channel.
**Where:** WP1 is on **`main`** — the `spike/m7-wp0` branch merged at WP0 close, and M7 work continues on
`main`. Run the GOVERNANCE §3.2 session-start check (`git fetch`; `git rev-list --left-right --count
main...origin/main`; `git status`), then work on `main`. Do **not** check out `spike/m7-wp0` — it is
retired (the throwaway spike module rode onto `main` and is deleted at WP2). `M7_PLAN.md` WP1 holds the
acceptance criteria, the slow-rig caveat, and the formal-review dispositions.

---

## 1. What WP1 delivers, and what "validated" means here

The M1 device harness (`scripts/e2e/`) was reworked off the accessibility engine onto the M7
overlay/appops engine (Plan A, in-place shell port). **WP1 is closed (2026-08-31): the baseline run
against the WP0 spike build passed on both hosts** — see the two `docs/reports/campaigns/2026-08-31_m7-wp1-harness_*.md`
records. Two hosts, two different jobs:

- **Moto G 2025 (real hardware, from the dev box) = the clean gate pass.** Run the reworked harness at
  the §11 gate profile → OV-4 `ABSENT=0` plus the negative control. Real hardware does not coin-flip, so
  this is the ABSENT=0 evidence.
- **NucBox emulator lanes (API 30/33/35/36) = probe/grep portability.** The un-fixed spike coin-flips on
  the software-GPU rig (WP0 finding: more bursts *raise* P(fail); the warm-overlay/off-main remedy is a
  WP2/WP3 production item), so these lanes validate that the probes read TOP/BEHIND/ABSENT correctly per
  API — with a **scaled `OV4_T_APPEAR_MS` (diagnostic, non-gate)**, not a strict ABSENT=0.

## 2. Build (both hosts)

```bash
./gradlew :app:assembleProdDebug :app:assembleProdDebugAndroidTest
```

Produces `app-prod-debug.apk` + `app-prod-debug-androidTest.apk` (compile/target SDK 36). The harness
installs both fail-closed; `setup_device.sh` grants Usage Access + overlay via `appops` and provisions
the spike (no PIN/app-list — the spike has none).

## 3. Moto G — the §11 gate profile (dev box, real hardware)

```bash
# real device attached; LOCK_ENGINE defaults to spike, T_appear stays the fixed 1500 ms
OV4_BURSTS=50 OV4_RELAUNCHES=20 OV4_REPEAT=5 scripts/e2e/run_all.sh -n 2
```

This provisions the device, runs OV-4 (spike overlay race) at the §11 gate counts, and the
`neg_overlay_grant` negative control. **Pass:** the report block shows `gate-profile=yes`, OV-4
`ABSENT=0` (BEHIND ≤ 2 %) on both runs, and `neg_overlay_grant` green (overlay absent while the grant is
revoked, recovers after re-grant). Leave `OV4_T_APPEAR_MS` unset — the Moto G does not need scaling.

## 4. NucBox — probe/grep portability across API 30/33/35/36 (emulator lanes)

Use **aosp/default** images (NOT `aosp-atd` — those ship no launchable target app, so OV-4 assume-skips;
the wrapper reports that as a failure, not a silent pass). On the software-GPU rig, scale the appear
budget:

```bash
# per lane (api30 / 33 / 35 / 36), scaled T_appear = diagnostic / non-gate
OV4_T_APPEAR_MS=4000 scripts/e2e/run_all.sh -n 1
```

**Confirm per lane:** OV-4 runs (not assume-skipped) and its `dumpsys window` count line parses (the
instrumentation `zOrder` grep reads correctly), and `neg_overlay_grant` passes (the bash `overlay_z`
probe reads present-vs-absent correctly — its sensitivity check is the operative present/absent
contrast; the decisive old-vs-new A/B is WP0-banked in
`docs/reports/campaigns/2026-08-28_m7-wp0-emulator_nucbox-g5.md`). Expect the verdict labelled
**NON-GATE profile** (scaled T_appear). A stray ABSENT on the swGPU rig is a timing/rig artifact, not a
harness or security defect — two mechanisms produce it: a late overlay appear past the scaled
`T_appear`, and `settle()`-coalescing (the detector suppresses a same-package transition when HOME and
the target land in one poll tick; the proper detector-state-ack fix is deferred to WP2–6, CR-008). Read
these lanes for parser portability, not ABSENT=0 — the clean ABSENT=0 evidence is the Moto G, where fast
rendering + polling make coalescing a non-issue (WP0 recorded 25/25 TOP there).

## 5. Recording the results

One dated report per host under `docs/reports/campaigns/`, host-tagged, e.g.
`YYYY-MM-DD_m7-wp1-harness_moto-g-2025.md` and `…_nucbox-g5.md`. State the commit produced against (on
`main`), the profile (gate vs diagnostic) and exact counts, the per-lane parser results, and the
negative-control outcome. WP1 lands **no RTM/ADR change** (test infrastructure). Add a fleet-index row in
`docs/reports/README.md`; each host keeps its own report — do not edit another host's.

## 6. Notes

- The harness is engine-aware: `LOCK_ENGINE=spike` (default) runs OV-4 + `neg_overlay_grant`; the
  prod-path checks (`smoke_core` / `ov3_fast_switch` / `f3_self_gate`) skip with exit 3 (their validation
  is WP2). `LOCK_ENGINE=prod` for OV-4 is rejected until the WP2 production scenario exists.
- Fail-closed install: a missing APK or a non-Success install aborts; `USE_PREINSTALLED=1` is the
  diagnostic escape (provenance then not established).
- Full env vars and the WP0 device/OEM gotchas (One UI `mCurrentFocus`, post-install detection gap,
  screen-stayon, logcat buffer) are in `scripts/e2e/README.md`.
