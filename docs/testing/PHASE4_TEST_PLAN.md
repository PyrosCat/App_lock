> **⚠ Status note (2026-07-19):** companion to `../process/PHASE4_PLAN.md` — see the status
> note there. Never executed. Becomes the starting skeleton for the M4 validation campaign
> after rework against the new baseline (SDS §11, Bluetooth scope, NFR targets).

# Phase 4 Validation Plan — Scheduling & Automation

Status legend: **DONE** (verified this campaign, evidence noted) · **TODO** (execute) ·
**SKIP** (justified) · **OPT** (run only if time allows).

Execution environment: headless Pixel_5 AVD (API 30, x86, address with `-s`, likely
`emulator-5556`), debug APK, PIN `1234`, Clock protected. Recipes and gotchas in §11.
Implementation plan & rule semantics: `docs/PHASE4_PLAN.md`.

Campaign rule carried over from Phase 3: any touch to `ApplicationLockEngine`,
`LockPolicyManager`, or the self-gate re-triggers §3 in full — that is where both
Phase-3 bypasses (F3/F4) lived.

---

## 0. Re-baseline (run first)

- [ ] **B-0** Boot/verify emulator; screenshot; unlock once with correct PIN (resets
  failure counter). Record baseline: protected apps, vault count, intruder-event count,
  automation tables empty, current SSID (`AndroidWifi`), location mode. Grant
  `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` via `pm grant` (recipe §11).
  Note: prefer `adb reboot` over hard QEMU kills all campaign — hard kills roll back
  unsynced app data.
- [ ] **B-1** Upgrade-path gate (run BEFORE any other on-device test, F-3 analog):
  install Phase 4 build **over** existing Phase 3 data → DB v2→v3 migration runs →
  protected apps, credentials, vault items, intruder events all survive; new tables
  present; `lockMode` defaults to ALWAYS. (`fallbackToDestructiveMigration` makes a
  missing migration destructive — this test protects real data.)

## 1. JVM unit suite (target ≈140 tests, all green before device work)

- [ ] **U-1** `ScheduleEvaluator`: single range in/out; boundary minutes (start
  inclusive, end exclusive — pick and pin the convention); multiple ranges union;
  overlapping ranges; overnight wrap (22:00–06:00 across midnight — mask applies to
  start day); 00:00–00:00 all-day; day-of-week mask (each bit, weekday/weekend
  presets); date window inclusive edges; expired window ⇒ inactive (FR-130); disabled
  schedule ⇒ inactive; empty ranges ⇒ inactive; zone-sensitive evaluation (injectable
  `ZoneId`, DST-transition day sanity).
- [ ] **U-2** `NextTransitionCalculator`: next start, next end, overnight-range end on
  next day, across-week wrap (schedule only on Mondays, evaluated Tuesday), multiple
  schedules ⇒ earliest boundary, no enabled schedules ⇒ null, date-window entry/expiry
  as boundaries.
- [ ] **U-3** `EffectivePolicyResolver` priority matrix (FR-141): full pairwise table —
  manual-lock beats trusted-wifi/location/schedule-out-of-window; override suspends
  schedule+location+wifi but not manual-lock; ALWAYS app ignores schedule layer;
  SCHEDULED app in-window LOCK beats trusted-wifi UNLOCK (priority 5 > 7); SCHEDULED
  out-of-window UNLOCK wins over location/wifi; location beats wifi when contradictory;
  global default LOCK when all abstain; conflict flag set exactly when ≥2 non-abstain
  votes disagree; determinism (same input ⇒ same output, no state).
- [ ] **U-4** Fail-secure: null SSID / unknown sentinel ⇒ wifi abstains; stale location
  (> cutoff) ⇒ abstains; automation globally disabled ⇒ resolver returns base policy;
  no trusted rows ⇒ abstain (never unlock).
- [ ] **U-5** `OverrideState`: each duration; until-screen-off cleared by screen-off
  signal; until-disabled persists; expiry boundary exact; expired override ⇒ layers
  vote again.
- [ ] **U-6** `GeoFence`: inside/outside at radius edge; hysteresis (no exit until
  `r*1.15+20m`); re-entry; zero/negative radius rejected; antimeridian-safe distance
  sanity.
- [ ] **U-7** `WifiSsids`: quote stripping, `<unknown ssid>` ⇒ null, empty, unicode SSID.
- [ ] **U-8** Regression: entire Phase 1–3 suite still green (67 legacy tests).

## 2. Functional — schedules (on-device)

- [ ] **S-1** Create schedule via UI with every FR-127 field (name, start/end, days,
  enabled, apps) → row persisted, appears in list and in summary card.
- [ ] **S-2** Live activation without restart (FR-126): set Clock to SCHEDULED, window
  starting +2 min from now → before start: Clock opens free; after boundary alarm
  fires: Clock locks. No app restart, no reinstall.
- [ ] **S-3** Deactivation at window end: after end boundary, Clock opens free again.
- [ ] **S-4** Session invalidation at window start: unlock Clock inside... window OFF;
  keep Clock foregrounded; window starts → lock screen appears over it (active
  enforcement, engine `forceLock` path).
- [ ] **S-5** Overnight range (FR-128): 2-range schedule incl. a wrap range (e.g.
  23:50–00:10 via time-set recipe §11, or real-time window straddling a set clock);
  locked at 23:55 and at 00:05, free at 00:15.
- [ ] **S-6** Multiple ranges same day (FR-128): 2 disjoint windows → locked in both,
  free in the gap.
- [ ] **S-7** Day-of-week (FR-129): schedule for today-only → active; flip device date
  to tomorrow (recipe §11) → inactive. Weekday/weekend preset chips set correct mask.
- [ ] **S-8** Date window (FR-130): endDate = yesterday → schedule shows
  auto-disabled, never fires; date window covering today → fires.
- [ ] **S-9** Schedule edit mid-window: shrink an active window so "now" falls outside
  → re-evaluation applies without restart (FR-126 "changes applied without restarting").
- [ ] **S-10** ALWAYS-mode app untouched by schedules: Clock ALWAYS + attached to no
  schedule → locked regardless of any other schedule's window.

## 3. Gating & engine regression (mandatory — Phase-3 defect ground)

- [ ] **G-1** = Phase 3 OV-3: fast window-switching ×10 alternating speeds → relock
  every time, no content frame (screencap spot-checks).
- [ ] **G-2** = Phase 3 OV-4: rapid `am start` ×5 → exactly one lock screen each, no
  bypass.
- [ ] **G-3** = Phase 3 F3: unlock self-gate → background → resume from recents →
  self-gate required; SAF picker round-trip does NOT spuriously gate.
- [ ] **G-4** Back-press on lock screen → HOME, no exposure (OV-1 re-run).
- [ ] **G-5** Automation OFF (global toggle) ⇒ byte-for-byte Phase-3 behavior: protected
  app locks, grace/screen-off relock policies unchanged.
- [ ] **G-6** Lock-screen latency unchanged: median `am start` → `Displayed
  …LockScreenActivity` over 5 runs comparable to Phase-3 baseline (~2.2 s on this
  emulator); resolver must not add measurable hot-path cost.

## 4. Functional — Wi-Fi rules

- [ ] **W-1** Trust current network (`AndroidWifi`) via UI → row in DB; Clock (ALWAYS,
  no session) now opens with NO lock screen (trusted-network exemption, FR-132).
- [ ] **W-2** Detection latency (FR-131): `svc wifi disable` while Clock foregrounded →
  lock screen appears; logcat delta network-lost → lock ≤ 10 s.
- [ ] **W-3** Re-enable wifi → auto-reconnect to AndroidWifi → Clock opens free again
  on next launch (no stale locked state).
- [ ] **W-4** Untrust (delete row) → Clock locks on next launch even on AndroidWifi.
- [ ] **W-5** Trusted-wifi unlock does NOT create a session: while on trusted wifi open
  Clock (no PIN) → `svc wifi disable` → relaunch Clock → lock screen (trust was
  per-evaluation, invariant §2.3).
- [ ] **W-6** Disabled (not deleted) trusted network ⇒ treated untrusted.

## 5. Functional — location rules

- [ ] **L-1** Create trusted location via "Use current location" after `geo fix` →
  row has captured lat/lon; manual-entry path also works (FR-134 fields).
- [ ] **L-2** Inside zone: `geo fix` to center → Clock opens free (UNLOCK vote).
- [ ] **L-3** Exit: `geo fix` well outside radius → foregrounded Clock gets locked
  (active enforcement); relaunch also locks.
- [ ] **L-4** Hysteresis: fix at radius+5 m after being inside → still trusted (no
  flap); fix at 1.2×radius+50 m → exits.
- [ ] **L-5** Radius honored: point between r and exit-threshold from a cold start
  (no prior inside state) ⇒ NOT trusted (enter requires d ≤ r).
- [ ] **L-6** Location services off (`settings put secure location_mode 0`) ⇒ fail
  secure: Clock locks even at trusted coordinates. Restore mode 3.
- [ ] **L-7** Stale fix: stop sending geo fixes > cutoff ⇒ layer abstains ⇒ locks.

## 6. Functional — priority, override, manual lock, logging

- [ ] **R-1** Priority live (FR-141): SCHEDULED Clock in-window + on trusted wifi ⇒
  LOCKED (schedule outranks wifi); conflict event logged with both votes.
- [ ] **R-2** SCHEDULED Clock out-of-window on untrusted network ⇒ open (schedule
  UNLOCK outranks global default).
- [ ] **R-3** Override 15 min (FR-142): trusted wifi active → start override → Clock
  LOCKS (automation suspended, base applies); override event logged.
- [ ] **R-4** Override expiry: after duration elapses (or time-set past it) → trusted
  wifi unlock behavior resumes; OVERRIDE_ENDED logged.
- [ ] **R-5** Override until-screen-off: screen off (`input keyevent 26`) ends it.
- [ ] **R-6** "Lock all now": with valid grace sessions live → all sessions cleared,
  every protected app locks including trusted-env ones; cleared by next successful
  auth (verify wifi unlock works again after a PIN unlock).
- [ ] **R-7** Automation log (FR-143): every transition this campaign produced rows
  (schedule act/deact, wifi trust/untrust, location enter/exit, override start/end,
  manual lock, conflicts) — timestamped, ordered; SAF export produces readable text
  file (pull + inspect).
- [ ] **R-8** Global automation toggle OFF mid-state (trusted wifi active, schedule
  in-window) ⇒ instant revert to base behavior; toggle logged (FR-145).
- [ ] **R-9** Reset-to-defaults (FR-145): wipes rules + override + manual lock after
  confirm dialog; protected apps and lockModes revert to ALWAYS.
- [ ] **R-10** Save-time conflict validation (FR-145): overlapping schedules on the
  same app warn but save; malformed range (equal start/end handled per pinned
  convention) rejected or normalized — no crash.

## 7. Security invariants (each maps to PHASE4_PLAN §2)

- [ ] **SEC-1** Self-gate never automated: on trusted wifi AND inside trusted zone AND
  schedule out-of-window → App Lock itself still demands PIN on launch and on resume;
  vault unreachable without auth (FR-108).
- [ ] **SEC-2** Fail-secure permission sweep: `pm revoke` fine location → SSID reads
  as unknown → trusted-wifi unlock STOPS applying (Clock locks); same for background
  location revocation. Re-grant afterwards.
- [ ] **SEC-3** Encryption sweep: `applock.db` header ≠ "SQLite format 3"; SSIDs and
  coordinates appear in NO `shared_prefs/*.xml` and in no plaintext file under
  `files/`; automation_events rows only in the encrypted DB.
- [ ] **SEC-4** No auto-dismiss: lock screen up → walk into trusted state (geo fix /
  wifi enable) → lock screen stays; PIN still required this once.
- [ ] **SEC-5** Automation events contain no secrets (no PIN, no raw coordinates in
  exported log beyond rule names — decide & pin exact export redaction, then verify).
- [ ] **SEC-6** New receivers (`TIME_CHANGED`/boot additions) exported=false or
  protected; no new exported surface (`dumpsys package com.applock` check).

## 8. Persistence & OS-state stress

- [ ] **P-1** Reboot with active schedule: `adb reboot` mid-window → after boot +
  a11y rebind: schedule still enforced, boundary alarm re-registered (BootReceiver),
  rules/log rows intact, watchdog FGS notification present.
- [ ] **P-2** Reboot clears session-like state safely: override "until disabled"
  survives reboot (persisted); "until screen off" does not (screen state reset).
  Pin expected semantics, then verify.
- [ ] **P-3** Force-stop: rules intact on relaunch; a11y nulled (known platform
  limitation, documented Phase 2); monitors re-attach on next start.
- [ ] **P-4** TIME_SET/TIMEZONE_CHANGED: jump clock across a boundary → recompute
  fires, schedule state matches new wall clock within one evaluation.
- [ ] **P-5** Doze sanity (**OPT**): `dumpsys deviceidle force-idle` across a
  boundary → alarm (`setExactAndAllowWhileIdle`) still fires or next foreground
  event corrects state; unidle restores monitors.

## 9. Edge cases, error injection, throughput

- [ ] **E-1** Schedule referencing an uninstalled/unprotected app → no crash;
  resolver ignores it; UI shows it greyed or drops it (pin behavior).
- [ ] **E-2** All-day every-day schedule on SCHEDULED app ⇒ equivalent to ALWAYS.
- [ ] **E-3** SCHEDULED app attached to zero schedules ⇒ fail-secure: LOCKED always
  (not "never locked") — pin this convention in resolver + test first in U-3.
- [ ] **E-4** Unicode/emoji schedule + location names round-trip UI/DB/export.
- [ ] **E-5** 20 schedules × 3 ranges + 10 trusted networks + 10 locations: evaluate
  latency in G-6 rerun unchanged; UI lists scroll; no ANR
  (`logcat -d | grep -i anr`).
- [ ] **E-6** Rapid rule churn: toggle a schedule enabled/disabled ×10 fast → alarm
  re-registration coalesces, no crash, final state correct.
- [ ] **E-7** Wifi toggle churn: `svc wifi disable/enable` ×5 → monitor state settles
  correct, no duplicate automation events flood (debounce or accept + document).
- [ ] **E-8** Stress cycle: 5× (launch protected app → wrong PIN → correct PIN →
  home) with automation ON → 0 crashes, watchdog alive, log coherent.

## 10. Two isolated final passes (after ALL fixes land — commit gate)

- [ ] **PASS-1 Regression sweep**: `clean testDebugUnitTest assembleDebug
  assembleRelease` → full JVM suite green (Phase 1–4), R8 release builds clean
  (new classes may need proguard rules — verify). Reinstall; re-run end-to-end:
  Phase-3 core flows (lock/unlock, vault import/export/delete, intruder log) +
  §3 gating suite + one representative from each Phase-4 group
  (S-2, W-2, L-3, R-1, R-3, SEC-1, SEC-2, P-1). Zero new failures tolerated.
- [ ] **PASS-2 Clean-room sanity sign-off**: `pm uninstall com.applock` → fresh
  install → PIN setup → protect Clock → v3 schema created encrypted-from-birth (no
  migration) → walk EVERY Phase-4 feature once on virgin data: create schedule
  (live activation), trust wifi (unlock + loss-relock), trusted location
  (enter/exit), override, manual lock, log export, global toggle off/on, reset.
  Explicit final gating checklist: immediate lock on launcher start, recents entry,
  `am start`, fast-switch return, self-gate on resume. Then Phase 4 may be marked
  complete (changelog entry + commit + push).

## Defect log

| ID | Description | Status |
|----|-------------|--------|
| — | *(populate during campaign; Phase-3 numbering continues at F5)* | |

## Execution results

*(populate during campaign, Phase-3 format: finding ✓/✗ + evidence per line)*

## Sign-off

*(record PASS-1 / PASS-2 verdicts and date)*

---

## 11. Execution notes (recipes)

Carried over from Phase 3 (full detail in `PHASE3_TEST_PLAN.md` §9 and memory):
emulator launch flags, `qemu-system-x86_64-headless` process name, a11y
delete-then-put rebind, ≥0.8 s tap spacing, PIN-pad coordinates, DocumentsUI
d-pad selection, Bash-only screencap, `MSYS_NO_PATHCONV=1` for device paths,
debug builds for visual checks (release sets FLAG_SECURE).

Phase-4 additions:

- **Permissions:** `adb shell pm grant com.applock android.permission.ACCESS_FINE_LOCATION`
  and `…ACCESS_BACKGROUND_LOCATION`; revoke with `pm revoke` (SEC-2). Revoking may
  kill the process — expect a11y rebind afterwards (delete-then-put recipe).
- **Wi-Fi:** `adb shell svc wifi disable|enable`; state via `adb shell cmd wifi status`
  (SSID `AndroidWifi`). Reconnect after enable takes a few seconds; if the stack
  wedges after many toggles, `adb reboot` restores it.
- **Location:** enable services `adb shell settings put secure location_mode 3`;
  inject fixes via emulator console `adb -s emulator-5556 emu geo fix <LONGITUDE>
  <LATITUDE>` — **longitude first**. Send 2–3 fixes 1 s apart; GPS provider needs a
  beat to surface them.
- **Time control:** probe `adb root` once (google_apis x86 image usually allows).
  If rooted: `adb shell "settings put global auto_time 0"` then
  `adb shell "date MMDDhhmmYYYY.ss"` (toybox format) and broadcast
  `adb shell am broadcast -a android.intent.action.TIME_SET`. If root unavailable:
  fall back to real-time windows (create schedules starting +2 min) for S-2/S-3,
  and drive S-5/S-7/R-4 boundary math purely in JVM tests (U-1/U-2/U-5) — document
  the substitution in results. Restore `auto_time 1` when done.
- **Screen off/on:** `adb shell input keyevent 26` toggles; verify with
  `dumpsys power | grep mWakefulness`.
- **Doze (P-5):** `adb shell dumpsys deviceidle force-idle` / `unforce`.
- **DB spot-checks:** encrypted DB can't be opened via `run-as`+sqlite3 (Phase 2
  note) — verify automation rows through the UI/log viewer, and encryption by
  header bytes only.
