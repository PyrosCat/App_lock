> **⚠ Status note (2026-07-19):** written against the old 5-phase model, before the project was
> re-baselined (see `MIGRATION_ASSESSMENT.md`). No longer the active plan. Retained as the
> **design input for migration phase M4** (= Implementation Strategy Phase 3 — Automation):
> the rules-engine/resolver design, fail-secure invariants, and emulator recipes carry over;
> scope must be reworked against SDS §11 (Scheduling & Automation Design) and now includes
> Bluetooth rules. Its §7 "deferred decisions" were never resolved and roll into M4 planning.

# Phase 4 Implementation Plan — Scheduling & Automation (FR-126..145)

Companion validation plan: `docs/PHASE4_TEST_PLAN.md` (execute after implementation;
two isolated final passes gate the commit).

Baseline: Phase 3 complete at `1dc9e25` (main = origin/main, tree clean).
DB v2, 67 JVM tests, hand-rolled `Graph` DI, headless Pixel_5 API 30 emulator.

---

## 1. Scope

### In scope

| FR | Feature | Notes |
|----|---------|-------|
| FR-126 | Time-based lock scheduling | Schedule-scoped protection per app; local time; live re-evaluation, no restart |
| FR-127 | Schedule creation | Name, ranges, days, enabled flag, associated apps |
| FR-128 | Multiple daily time ranges | Unlimited ranges/schedule; overlap = union; overnight wrap supported |
| FR-129 | Day-of-week scheduling | Per-day bitmask + weekday/weekend/daily presets in UI |
| FR-130 | Date-based scheduling | Optional start/end epoch-day window; expired ⇒ auto-disabled |
| FR-131 | Trusted Wi-Fi detection | NetworkCallback push (≤10 s); SSIDs in SQLCipher DB (= stored securely) |
| FR-132 | Wi-Fi lock policies | **Partial:** trusted-network unlock exemption. "Lock on public/unknown Wi-Fi" is already the base behavior (everything locks unless trusted); stronger lock-direction rules (e.g. ignore grace sessions on unknown networks) deferred |
| FR-133 | Location-based locking | Platform `LocationManager` (no GMS geofencing — image may lack Play services and we avoid the dependency); enter/exit with hysteresis |
| FR-134 | Trusted location management | Name/lat/lon/radius/enabled; "use current location" capture + manual entry |
| FR-141 | Rule priority | Fixed default order, deterministic resolver, conflicts logged. Admin-configurable order deferred |
| FR-142 | Manual override | Suspend automation 15 m / 30 m / 1 h / until screen off / until disabled; plus "Lock all now" |
| FR-143 | Automation event logging | New `automation_events` table (encrypted via SQLCipher), timestamped, exportable as text via SAF |
| FR-145 | Config management | **Partial:** global automation on/off, active-rules summary, reset-to-defaults, basic conflict validation on save. Export/import of profiles deferred |

### Stretch (build only if the campaign stays on schedule)

- **FR-136 charging automation** (Low) — trivially testable on the emulator
  (`adb emu power ac on|off`); "no relock while charging" style exemption.
- **FR-138 screen-state actions** (High but largely satisfied) — screen-off already
  clears all sessions (Phase 1 `onScreenOff` + `RelockPolicy`). Delta: log the
  enforcement to `automation_events`; configurable delay-relock deferred.

### Out of scope (defer to Phase 5+, with reasons)

- **FR-135 Bluetooth trusted devices** — the emulator has no Bluetooth stack at all;
  the feature would ship with zero E2E coverage. Defer until hardware is available.
- **FR-137 device-idle detection** (Medium) — needs an interaction-tracking source we
  don't have yet; screen-off session clearing covers the main threat today.
- **FR-139 calendar rules** (Low), **FR-144 recommendations** (Low) — explicitly low priority.
- **FR-140 automation profiles** (High) — grouping layer over the rules built here;
  cleanly additive later (profiles reference schedule/Wi-Fi/location rows). Building it
  now doubles the UI surface before the primitives have proven out.
- FR-141 admin-configurable priority order; FR-145 profile export/import.

---

## 2. Security invariants (non-negotiable, each gets a test)

1. **The self-gate is never automated.** Trusted Wi-Fi / location / schedule windows
   apply ONLY to third-party protected apps. The App Lock UI (vault, intruder log,
   settings, automation config) always requires PIN/biometric (FR-108). The engine
   already early-returns on `context.packageName`; automation state must never feed
   the `SelfLock` path.
2. **Fail secure.** Any unknown signal abstains — it never unlocks:
   - SSID unreadable (permission revoked, location services off, `<unknown ssid>`) ⇒ Wi-Fi layer abstains.
   - Location stale (> 5 min) or unavailable ⇒ location layer abstains.
   - Automation globally off, or resolver failure ⇒ base behavior (protected = locked).
3. **Trust is evaluated, never cached as a session.** A trusted-environment unlock skips
   the lock screen per foreground event; it does not create an unlock session. Leaving
   the trusted environment re-locks on the next event AND actively (see §4.4).
4. **No auto-dismiss.** An already-displayed lock screen is never dismissed by an
   environment becoming trusted.
5. **Trusted SSIDs and coordinates live only in the SQLCipher DB** — never in
   plaintext prefs, never logged in plaintext detail beyond rule names.

---

## 3. Rule semantics (the decisions, made explicit)

**Per-app protection mode** (new column on `protected_apps`):
- `ALWAYS` (default — current behavior, migration-safe): locked whenever no session.
- `SCHEDULED`: locked only while one of its attached schedules is in-window (FR-126
  "schedules during which selected apps require authentication"); outside all windows
  the app opens freely.

**Resolver: layered votes, first non-abstain wins** (FR-141 order, top wins):

| Priority | Layer | Votes |
|----------|-------|-------|
| 1 | Emergency lock | *(reserved — not built in Phase 4)* |
| 2 | Manual lock ("Lock all now") | LOCK for every protected app while active; cleared by next successful auth or manual toggle. Also clears all sessions when activated |
| 3 | Manual override (FR-142) | Forces layers 4–6 to abstain while active ⇒ base `ALWAYS` behavior applies. Expiry: duration / screen-off / manual |
| 4 | App-specific mode | `ALWAYS` ⇒ LOCK (subject to session); `SCHEDULED` ⇒ abstain (schedule layer decides) |
| 5 | Schedule layer | For `SCHEDULED` apps: in-window ⇒ LOCK, out-of-window ⇒ UNLOCK |
| 6 | Location layer | Inside an enabled trusted zone ⇒ UNLOCK; else abstain |
| 7 | Wi-Fi layer | Connected to an enabled trusted SSID ⇒ UNLOCK; else abstain |
| 8 | Global default | Protected ⇒ LOCK |

- A valid unlock session still short-circuits to "no lock screen" (unchanged semantics).
- When two layers emit contradictory non-abstain votes, the winner is taken and a
  `CONFLICT_RESOLVED` automation event records both (FR-141 "conflicts logged").
- Schedule math: minutes-of-day ranges; `end <= start` wraps past midnight; the
  day-of-week mask applies to the day the range **starts**; overlapping ranges union;
  date window is inclusive epoch-days; expired date window ⇒ schedule flagged disabled
  (FR-130). All evaluated in device-local time with an injectable `Clock`/`ZoneId`.

---

## 4. Architecture

New package `com.applock.automation` (data / logic / monitors / ui), wired through `Graph`.

### 4.1 Data layer — DB v3 (`MIGRATION_2_3`, hand-written like 1→2)

```
protected_apps            + lockMode TEXT NOT NULL DEFAULT 'ALWAYS'   (ALTER TABLE)
lock_schedules            id PK, name, enabled, daysMask INTEGER (bit0=Mon..bit6=Sun),
                          startEpochDay INTEGER NULL, endEpochDay INTEGER NULL
schedule_time_ranges      id PK, scheduleId, startMinute (0..1439), endMinute (0..1439)
schedule_apps             (scheduleId, packageName) composite PK
trusted_wifi_networks     ssid TEXT PK, label, enabled, addedAt
trusted_locations         id PK, name, latitude REAL, longitude REAL, radiusMeters REAL, enabled
automation_events         id PK, timestamp, eventType TEXT, detail TEXT
```

DAOs expose `Flow`s for the UI and snapshot queries for the resolver caches.
`AutomationEventType`: SCHEDULE_ACTIVATED / SCHEDULE_DEACTIVATED / WIFI_TRUSTED /
WIFI_UNTRUSTED / LOCATION_ENTERED / LOCATION_EXITED / OVERRIDE_STARTED /
OVERRIDE_ENDED / MANUAL_LOCK / CONFLICT_RESOLVED / AUTOMATION_TOGGLED (FR-143).

**Caution:** `fallbackToDestructiveMigration()` is active in `AppLockDatabase.build` —
the v2→v3 migration must land in the same change as the version bump, and the upgrade
path gets its own test (F-3 analog) before anything else runs on a device with data.

New settings (`SettingsRepository`): `automationEnabled` (default **false** — Phase 1–3
behavior is untouched until the user opts in), `overrideUntilMs` / `overrideMode`,
`manualLockActive`.

### 4.2 Pure logic (JVM-testable, no Android imports — the bulk of new unit tests)

- `ScheduleEvaluator` — `isInWindow(schedule, instant, zone)`, overnight/mask/date-window
  semantics per §3.
- `NextTransitionCalculator` — earliest upcoming boundary across all enabled schedules
  (for alarm scheduling); `null` when no enabled schedule.
- `EffectivePolicyResolver` — the §3 vote table: inputs are plain value objects
  (app mode, schedule state, env state, override/manual-lock state) ⇒
  `EffectivePolicy(requiresLock, cause, conflict?)`. Deterministic, synchronous.
- `OverrideState` — expiry math for the five FR-142 modes.
- `GeoFence` — haversine/`distanceBetween`-style check plus hysteresis: enter at
  `d <= r`, exit at `d > r * 1.15 + 20 m` (no flapping at the boundary); staleness cutoff.
- `WifiSsids` — normalization (strip quotes from `WifiInfo`, `<unknown ssid>` sentinel ⇒ null).

### 4.3 Environment monitors (Android layer, push-based, cached in `StateFlow`)

- `WifiMonitor` — `ConnectivityManager.registerNetworkCallback(TRANSPORT_WIFI)`;
  SSID via `WifiManager.connectionInfo` (API 30 path; quotes stripped). Publishes
  `StateFlow<String?>`. Push callbacks satisfy FR-131's ≤10 s comfortably.
- `LocationMonitor` — `LocationManager.requestLocationUpdates` (network+GPS, ~60 s /
  50 m) **only while** automation is on AND ≥1 enabled trusted location exists; stops
  otherwise (battery, Section-11 NFRs). Publishes `StateFlow<Location?>` with timestamp
  for staleness.
- Both owned by `AutomationController` (see 4.4), started/stopped as rules change.

### 4.4 Enforcement & ticking

- `LockPolicyManager.evaluate` grows a resolver consultation. It stays synchronous and
  allocation-light on the a11y hot path: the resolver reads only in-memory snapshots
  (protected set + modes, current in-window schedule set, last env states, override
  flags) that are refreshed off-thread.
- `AutomationController` (new, in `Graph`) owns monitors + snapshot refresh + the two
  active-enforcement paths:
  1. **Schedule boundary:** `AlarmManager.setExactAndAllowWhileIdle` at the next
     transition (recomputed after every fire / rule edit / boot / TIME_SET /
     TIMEZONE_CHANGED). On fire: refresh snapshot; clear sessions of apps whose window
     just started; if the current foreground app (engine's `lastForegroundPackage`)
     now requires lock ⇒ launch lock screen. Manifest declares `SCHEDULE_EXACT_ALARM`
     (auto-granted below API 33; fallback `setWindow` ±1 min if denied — fine, and the
     next foreground event re-evaluates anyway). If alarms prove flaky on the headless
     emulator, fallback plan: minute-ticker coroutine inside the existing watchdog FGS,
     active only while ≥1 enabled schedule exists.
  2. **Environment loss:** on trusted-Wi-Fi disconnect / trusted-zone exit, re-evaluate
     the foreground app; lock it if it now requires auth (FR-131 "policies updated
     automatically").
- `BootReceiver` additionally re-registers the boundary alarm. New tiny receiver for
  `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` ⇒ recompute alarm + snapshot.
- `ApplicationLockEngine` changes are minimal and surgical: expose current foreground
  package to the controller and accept a `forceLock(package)` entry point. **Every
  engine/policy-manager touch triggers the OV-3/OV-4/F3 regression suite in the test
  plan** (the two Phase-3 bypasses lived exactly here).

### 4.5 Permissions & FGS strategy (the riskiest compatibility area)

- Add `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` (runtime), `ACCESS_BACKGROUND_LOCATION`,
  `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE_LOCATION`.
- On API 29+ reading the SSID requires fine location + location services ON; monitors
  also run while the app is backgrounded (process kept alive by the watchdog FGS), which
  on API 30+ requires **background location** ("Allow all the time" — grantable only via
  Settings) or a `location`-typed FGS. Plan: watchdog gains
  `foregroundServiceType="specialUse|location"`; UI requests fine location at first
  Wi-Fi/location rule creation and deep-links to Settings for "Allow all the time",
  with a status card showing degraded state until granted. On the emulator both grants
  are one-liners (`pm grant`).
- Everything must degrade fail-secure when permissions are missing (invariant §2.2) —
  the feature silently abstaining is acceptable; unlocking is not.

### 4.6 UI (Compose, same patterns as vault/intruder screens)

New `Screen.AUTOMATION` (+ sub-screens) reached from Settings:
- **Automation hub:** global on/off switch (FR-145), active-rules summary card
  ("2 schedules active · trusted Wi-Fi: Home · override: 22 min left"), override
  buttons (FR-142), "Lock all now", reset-to-defaults, event-log entry.
- **Schedules:** list + editor (name, day chips with weekday/weekend/daily presets,
  1..n time ranges, optional date window, app multi-select from protected apps,
  enable switch). Save-time validation warns on conflicts (FR-145).
- **Trusted Wi-Fi:** current SSID with "Trust this network", list with enable/delete.
- **Trusted locations:** manual lat/lon/radius entry + "Use current location" one-shot
  capture; list with enable/delete. No map dependency.
- **Automation log:** timestamped event list (reuse intruder-log viewer pattern) +
  SAF text export (FR-143 "exportable").
- App List rows: per-app mode indicator when `SCHEDULED` (small "clock" glyph).

All automation UI sits behind the self-gate (invariant §2.1).

---

## 5. Work breakdown (implementation order)

| # | Step | Contents | Exit criteria |
|---|------|----------|---------------|
| 1 | Data layer | Entities, DAOs, `MIGRATION_2_3`, settings keys, Graph wiring | Build green; migration unit-testable; upgrade smoke on emulator (existing v2 data survives) |
| 2 | Pure logic + tests | `ScheduleEvaluator`, `NextTransitionCalculator`, `EffectivePolicyResolver`, `OverrideState`, `GeoFence`, `WifiSsids` — tests written alongside | JVM suite grows 67 → ~140, all green |
| 3 | Monitors | `WifiMonitor`, `LocationMonitor`, permission plumbing, manifest/FGS changes | Manual probe on emulator: SSID + geo fix visible in logcat |
| 4 | Enforcement | Resolver into `LockPolicyManager`; `AutomationController` + alarms + boot/time receivers; engine `forceLock` | Schedule flips lock state without app restart; env-loss locks foreground app |
| 5 | UI | Hub, schedule editor, Wi-Fi/location managers, override, log + export | All FR-127/129/134/142/145-listed fields operable |
| 6 | Hardening pass | Fail-secure sweeps (revoked permission, location off), conflict logging, event coverage | Invariants §2 all demonstrable |
| 7 | Validation campaign | Execute `PHASE4_TEST_PLAN.md` including **PASS-1 + PASS-2** | Zero open defects; sign-off recorded |
| 8 | Ship | `changelog.txt` entry (per workflow), one-line commit subject; user commits/pushes | — |

Estimated new surface: ~15 new source files, ~6 test files; engine/policy diffs kept minimal.

## 6. Risk register

| Risk | Mitigation |
|------|------------|
| Engine hot-path regression re-introduces a gating bypass | Mandatory OV-3/OV-4/F3 re-runs (test plan §3); engine diff kept to `forceLock` + resolver call |
| SSID unreadable on API 30 (location gate) | Fail-secure abstain + status card; emulator `pm grant` recipe; verified early in step 3 |
| Alarm non-delivery (Doze/exact-alarm policy) | Foreground-event re-evaluation is an always-on safety net; watchdog minute-ticker fallback documented |
| `fallbackToDestructiveMigration` wipes data if migration missing | Migration ships in the same change as the version bump; upgrade test runs first on-device |
| GMS absence on the AVD | No GMS dependency taken (platform LocationManager only) |
| Time manipulation defeats schedules (user moves clock) | Out of threat model for Phase 4 (device owner = app owner); TIME_CHANGED recompute keeps rules coherent; noted for root/tamper phase |
| Emulator Wi-Fi flakiness after `svc wifi` toggles | Known-good recipe in test plan; reboot restores AndroidWifi |

## 7. Explicitly deferred decisions for review (before implementation starts)

1. **Scope cuts** in §1 (esp. FR-140 profiles and FR-135 Bluetooth deferral) — confirm.
2. **Override position** in the priority table (slot 3, above app rules) — FR-141's
   default list doesn't mention override; this placement makes "override" mean
   "suspend automation, revert to base locking", never "unlock everything". Confirm.
3. **`SCHEDULED` mode semantics** — out-of-window means the app opens with no lock at
   all (per FR-126 reading). Alternative (schedule only *adds* windows on top of
   always-on) makes schedules meaningless; flagged anyway. Confirm.
4. **Background-location ask** (Allow-all-the-time) vs foreground-only degraded mode
   as the default posture. Plan assumes we ask, with graceful degradation.
