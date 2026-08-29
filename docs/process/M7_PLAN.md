# M7 Plan — Detection & Enforcement Replacement ("the accessibility exit")

**Class:** living (GOVERNANCE.md §5.1). **Status:** authored 2026-08-20; **revised 2026-08-25 per
external review** (API-36/D0, canonical R-002 rule, per-WP RTM, FGS-restart + decision tables,
UsageStats algorithm spec, request-identity model, numeric protocol, exact matrix); **draft pending
user review + commit** (GOVERNANCE §3.3 — the user commits). **Baseline:** the M1 close — **`60265b6`**
(tag `M1_Exit`, the M1-exit sign-off commit). (The prior draft's `d62b601`, the M1/WP8 batch, is
stale.) **Starts on M1 close** (`ROADMAP.md`).

> This plan is the **recommended (hybrid) plan** selected from a five-plan exploration
> (Strangler-Swap / Ports-&-Adapters / Deterministic-State-Machine / Spike-Validated / Dual-Engine).
> The exploration and its evaluation are recorded in the session that produced this file; the
> rationale summary is §7. **WP0's two ADRs (ADR-020 overlay/biometric, ADR-021 poll/detection)
> MUST be Accepted before any WP2 production code lands** (GOVERNANCE §2.2).

**Objective:** replace the app's core engine — swap accessibility-event detection for a
`UsageStatsManager` (Usage Access) polling detector, and swap the `noHistory` Activity lock screen
for a drawn `SYSTEM_ALERT_WINDOW` overlay — **one mechanism at a time**, behind narrow ports, with
the request-identity and fail-secure-readiness models that close R-002 and R-005 built in from the
start. Remove `AppDetectionService` and its manifest declaration. This is the pivotal 1.0.0
milestone: the engine is *replaced*, not extended (ADR-013B; `ROADMAP.md`).

**Exit = M7 gate** (`ROADMAP.md`): the **exact test matrix (§10)** green with the new engine — CI
(api30+35+36), NucBox `full` (api26/29/30/33/35+36), Moto G 2025 (arm64/API 35), the FTL OEM sweep —
on the **API-36 target** (D0 resolved; §5); the OV-4 rapid-relaunch race dispositioned per the
**canonical R-002 evidence standard** below; NFR-PERF-012 end-to-end transition→lock figure recorded
against the **numeric protocol (§11)**; **no accessibility declaration in the merged manifest**;
R-002 and R-005 dispositioned on evidence per that standard; **RTM synchronized per work package**
(§6; GOVERNANCE §1.3 — not batched at close-out), ADRs + changelog synchronized.

> **Canonical R-002 evidence standard — the single standard; WP0 / WP2 / WP6 all defer to this.**
> Four parts, each a different kind of evidence:
> 1. **The decisive A/B — emulator positive-control A/B is the *decisive* proof the fix works.** On the
>    NucBox software-GPU rig that reproduces the 12–37 % race: the old engine reproduces `ABSENT`/`BEHIND`,
>    the new overlay is **never `ABSENT`** (self-healing `BEHIND` only).
> 2. **The no-regression check — the Moto G 2025 proves real-device *no-regression* only** — a budget,
>    single-OEM device already clean on the old engine, so it cannot prove the fix, just that nothing
>    regressed (never `ABSENT` under burst, plus latency + biometric).
> 3. **The OEM/OS residual sweep — a Firebase Test Lab multi-OEM / multi-API sweep closes the OEM/OS
>    residual.**
> 4. **The fallback — without FTL, R-002 stays Open at a reduced rating** under an explicitly approved
>    TM §14.10 compensating treatment with a review trigger (run before M10) — **never** full-Closed on
>    single-OEM evidence.
>
> No work package calls real hardware "decisive" or "primary": the **decisive A/B** is the only proof the
> fix works, the **no-regression check** shows only that nothing regressed, and the **OEM/OS residual
> sweep** (with the **fallback** when FTL is unavailable) disposes of the residual. All burst counts,
> timeouts, and `ABSENT`/`BEHIND` budgets are the numeric protocol in §11.

---

## 1. Scope

**In:**
- `UsageStatsManager` polling detector behind the detection seam (`ForegroundDetectionSource` port +
  `UsageAccessDetector` adapter), hosted in a foreground service (ADR-021).
- Drawn overlay lock surface (`SYSTEM_ALERT_WINDOW`) replacing `LockScreenActivity` (`LockPresenter`
  port + `OverlayLockPresenter` adapter), with a transparent `FragmentActivity` hosting
  `BiometricPrompt` via the overlay-granted background-activity-launch (BAL) (ADR-020).
- **Request-identity presentation model** — one current lock request; stale completions rejected;
  same-package return reuses the request. The committed R-002 remediation (ADR-020).
- **Fail-secure protection readiness** — explicit `loading / ready / failed` policy state; a pre-ready
  or unknown-storage decision never yields an *allow* (closes R-005; the decisions are §2.3).
- Removal of `AppDetectionService` + its `<service … BIND_ACCESSIBILITY_SERVICE>` declaration +
  `res/xml/accessibility_service_config.xml`; the ADR-018 FQCN pin stays **dormant-binding** for the
  2.0.0 return (no shim class shipped).
- FR-179 protection-health re-pointed from accessibility to **Usage Access + overlay** loss; states
  surfaced truthfully (`ProtectionWatchdogService` health source change).
- Manifest/permission changes: **add** `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW`; **remove** the
  accessibility service. (Full permission audit + descoped-feature removal is **M8**.)
- **Target-API baseline (D0, RESOLVED): `targetSdk 36` (Android 16), adopted at WP0.** The AGP/Gradle
  toolchain bump + `compileSdk`/`targetSdk` 36 land in WP0's spike, then carry into production; M1's
  API-35 gate is untouched. The platform surface (FGS/BAL §2.2, overlay, UsageStats §2.4) is validated
  on API 36; the lanes are **§10**.
- Device-harness rework — the M1 harness (`scripts/e2e/`), reworked here as **WP1**: overlay-window
  assertions replace resumed-activity assertions; `appops` grants replace the a11y rebind; raised OV-4
  burst/repeat.
- NFR-PERF-012 benchmark (enforcement response ≤250 ms; documented bounded poll interval; measured
  end-to-end figure).
- RTM updated **per WP** (FR-026/027/028/044/049, FR-179, NFR-PERF-012/015; §6); ADR-020/021 Accepted
  at WP0; ADR-013B/018 status lines updated; changelog; M7 gate record.

**Out (deliberately):**
- v1.0.0 UI/UX-spec product surfaces (onboarding, two-grant setup checklist, settings, help,
  destructive reset) — **M8**. M7 builds only the overlay *lock surface* itself, not the setup flows
  that request the two grants (M7 uses adb/appops grants for its own verification; the in-app grant
  UX is M8).
- Removal of vault/, privacy/intruder, CAMERA, their screens and tables — **M8**.
- The optional accessibility low-latency tier — **2.0.0** (ADR-013B; the `ForegroundDetectionSource`
  port leaves the seam for it, but no second adapter and no source-selection UI ship in 1.0.0).
- Tapjacking / obscured-input hardening against hostile third-party overlays (THR-UI-001/003),
  lockout/biometric-failure accounting (CR-005), Play-listing pre-check — **M9/M10**. M7 keeps the
  existing `FLAG_SECURE` behavior and the existing lockout accounting on the PIN path.
- Interface extraction for auth/repos beyond the two detection/presentation ports — those land with
  their consuming services (M2/M3 lineage, 2.0.0).

## 2. Invariants (mandated by the baseline — not plan choices)

These are fixed by ADR-013B and the v1.0.0 SDS/NFR; every work package below must preserve them:

1. Usage Access is the **sole** detection source; bounded, **documented** poll interval; no usage
   timeline stored — only current + previous package identity.
2. The drawn overlay is the **sole, mandatory** lock presentation; the overlay grant is a
   **precondition** for a *Protected* health state (ADR-013B).
3. Missing configuration, unknown storage state, unready cache, or a failed dependency **never**
   yields an *allow* decision; where Android prevents enforcement, the outcome is *interruption*
   with truthful notice, never a false *Protected*.
4. Biometric is offered when eligible and PIN fallback is **always** available; the auth screen is
   `FLAG_SECURE` on non-debug builds (FR-171).
5. Entry points (services/activities/receivers) live only in `platform/` or `presentation/`
   (Konsist R4); layer direction holds (R2). `AppDetectionService`'s R4 pinned-exemption is
   **removed** when the file is deleted (WP5).
6. **No Room schema change and no new persistence in M7.** Protection / session / health /
   lock-request state is **in-memory** (or transient settings); the encrypted DB stays
   "protected-package identifiers only." **No new writes to the dormant `SecurityEventDao` /
   health-history schema** — DDS v1.0.0 §1.3 excludes security-event and persisted-health history
   (plus sessions, foreground identity, and lock requests), and those tables are inactive-schema
   leftovers slated for M8 removal, so writing to them re-activates an excluded domain. Keep state in
   memory, durable evidence in `docs/reports/`, runtime signal in logcat — not the DB. (Any real
   persistence would need a governed migration and is out of scope.)

### 2.1 Component ownership after the accessibility removal (seam-transfer checklist)

Every responsibility currently homed in a component M7 deletes or repoints **must** land an explicit
new owner and WP — this is the gap a combination plan is most likely to drop. Traced against the
code (`AppDetectionService.kt`, `ProtectionWatchdogService.kt`, `MainActivity.kt`, `BootReceiver.kt`,
`LockScreenActivity.kt`, the androidTest seed, `scripts/e2e/`):

| Responsibility (today) | Current home | New home (WP) |
|---|---|---|
| Foreground detection input → `onAppForegrounded` | `AppDetectionService.onAccessibilityEvent` | `UsageAccessDetector` in the poll service (**WP3**) |
| Screen-off session clear (`onScreenOff`) + poll pause; screen-on resume | `AppDetectionService` `ACTION_SCREEN_OFF` receiver (**only** driver of `onScreenOff`; sole clear path for the `SCREEN_OFF` relock policy) | poll service `SCREEN_OFF`/`SCREEN_ON` receiver (**WP3**) |
| Lock presentation | `LockScreenActivity` (Activity) | `OverlayLockPresenter` + `BiometricHostActivity` (**WP2**) |
| In-app "capability needed" banner + on-resume recheck | `MainActivity:276-335` → `AppDetectionService.isEnabled` + `ACTION_ACCESSIBILITY_SETTINGS` (**compile-blocking** on WP5) | capability/health query → Usage Access + overlay deep-links (**WP4**; polished two-grant checklist is M8) |
| Protection-health source | `ProtectionWatchdogService:94` → `AppDetectionService.isEnabled` | usage+overlay grant + detector-liveness (**WP4**) |
| Instrumentation smoke of the lock surface | androidTest `LockScreenLaunchTest` (launches `LockScreenActivity`) — run by the GMD CI matrix | overlay/biometric-host smoke (**WP2**; matrix runs it **WP6**) |
| Boot restart of protection | `BootReceiver:50` → `ProtectionWatchdogService.start` | same call, now the consolidated poll+health service (**WP3/WP5**, D2) |
| Device-harness lock detection + grants | `scripts/e2e` resumed-activity + a11y rebind | overlay-window probe + `appops` (**WP1**) |
| a11y strings / xml / manifest service / Konsist R4 pin | `strings.xml`, `accessibility_service_config.xml`, manifest, `r4PinnedEntryPoints` | removed; ADR-018 pin dormant (**WP5**) |

Any row without a green WP owner at the M7 gate is an open gap, not a deferral.

### 2.2 Foreground-service restart & start-authorization model *(built across WP3/WP4/WP5; WP0 confirms the platform cells)*

The poll+overlay engine must survive every lifecycle event below. **Two distinct restrictions apply,
and must not be conflated:** a **background FGS start** (Android 12+, tightened 15) — using
`SYSTEM_ALERT_WINDOW` as the basis requires an **already-visible overlay** on 15+; and a
**background-activity-launch (BAL)** — launching an Activity (e.g. the biometric host) needs the app to
own a **visible window** (the drawn overlay). `BOOT_COMPLETED` has its own allowance, and **`specialUse`
is *not* on the Android 15 boot-prohibited FGS-type list.** For each scenario the plan fixes who
restarts monitoring, whether it is permitted per API, and the health state shown if the restart fails.
Platform-uncertain cells (†) are WP0 acceptance items on the API-36 (D0) target.

| Scenario | Who restarts monitoring | Permitted? (API 30 / 33 / 35 / 36) | Health if restart fails |
|---|---|---|---|
| First setup / first protected-app selected (and every normal app-open thereafter) | `MainActivity` (foreground) starts the poll FGS | Yes, all — foreground start, no BAL gate | *Action required* on the setup/main screen |
| Boot (`BOOT_COMPLETED`) | `BootReceiver` best-effort `startForegroundService` (existing try/catch) | 30/33 yes; 35/36 yes for `specialUse` (not boot-prohibited) but subject to the post-boot FGS-start window † | No overlay yet → *Action required* on next app entry (SDS §15.5) |
| Process death + `START_STICKY` recreation | OS recreates the FGS; it re-bootstraps in `loading` (§2.3) until the first confirmed snapshot | Yes, all — system-initiated | `loading` → *Protection interrupted* if a needed start can't be re-acquired |
| Force-stop (user, Settings) | **Nothing** — Android suppresses sticky restart after force-stop | n/a | App not running ⇒ **no health surfaced**; monitoring + watchdog resume only when the user reopens the app (inherent OS limit, stated honestly) |
| Service killed (low memory) while a protected app is visible | Sticky recreation keeps the *detector* alive; the overlay is (re)drawn on the next detection tick while the FGS is up | Yes, all; on 35/36 a *background* biometric-Activity launch still needs the visible overlay as its BAL basis † | Overlay redraw fails → *Protection interrupted* |
| Usage-Access **or** overlay grant revoked, then restored | Revoke → detector stops / cannot draw; restore → capability recheck restarts detection | Yes, all — grant flows are foreground | Revoked: *Action required* / *interrupted* + deep-link notification; restored: *Protected* |

> **BAL** (a visible window permits an Activity launch) governs the **biometric Activity** launch —
> ADR-020; the **FGS-start** rule (visible-overlay as the `SYSTEM_ALERT_WINDOW` justification on 15+)
> governs starting the **detection service** from the background — the Boot and killed-while-visible
> rows, ADR-021. The overlay *draw* itself needs no start-exemption (the FGS is already running). Rows
> marked † are the cells WP0 confirms before WP2 relies on them.

### 2.3 Enforcement decision table — what "hold / interrupted" actually does *(WP3 implements)*

`evaluate()` maps **(policy readiness × capability state × observed target) → (decision × overlay
surface × health)**. A non-`ready` readiness or a missing capability **never** yields *allow*
(invariant 3); it yields an *interruption* with truthful notice, and every shield surface offers an
escape so the user is never trapped behind an overlay.

| Readiness | Capabilities | Observed target | → Decision | Overlay surface | Health |
|---|---|---|---|---|---|
| `ready` | both granted | protected package | **lock** | full lock surface (PIN / biometric) | Protected |
| `ready` | both granted | unprotected / launcher / **our own package** (biometric host) | **allow** | none | Protected |
| **`loading`** (pre-first-snapshot) | any | protected package | **hold** (never allow) | neutral **"Protection checking…"** shield; dismiss-to-launcher; **bounded timeout `T_ready`** (WP0-set) → on expiry treat as `failed` | Checking / Unknown |
| **`failed`** (storage / Keystore / policy-load error) | any | protected package | **hold / interrupt** | **"Protection recovery required"** shield with an **escape affordance** (buttons deep-linking to App Lock and Android Settings) | Protection interrupted |
| `ready` | **Usage Access revoked** | cannot observe | **cannot enforce** → interruption | none → notification only | Action required |
| `ready` | **overlay revoked** | protected package | **cannot present** → interruption | none possible → notification only (SDS §8.3 step 4 / §8.8) | Protection interrupted |
| `ready` | both granted | protected package, but **overlay draw fails** | **interrupt** (never a false *Protected*) | none | Protection interrupted |

**Escape-hatch rule (no unrecoverable loop).** Every shield surface (checking / recovery) MUST
(a) expose a control that reaches App Lock's main screen or Android Settings, (b) honor Home/Back to
the launcher, and (c) self-dismiss at `T_ready`. The overlay is **modal to the protected task, never
to the whole device** — no readiness/capability state can trap the user behind an undismissable
window.

### 2.4 UsageStats detection contract *(defined deliverable — WP3 implements + tests; ADR-021 fixes P)*

The `UsageAccessDetector` behavior is specified here, not left to implementation:

- **Event selection (fixed across API 30/33/35/36, and 29 if kept in `full`):** each tick
  `queryEvents(now − W, now)`; consume `ACTIVITY_RESUMED` as the foreground signal (the
  `MOVE_TO_FOREGROUND` constant on the older path); ignore `PAUSED/STOPPED` except to bound sessions.
- **Query window `W` & de-dup:** `W` > poll interval `P` so no transition falls between ticks;
  de-duplicate by a `(package, eventTimestamp)` **cursor** — track the last-consumed event time and
  never re-emit an event at or before it.
- **Freshness `F`:** an observation older than `F` (a small multiple of `P`) is stale and does not
  drive a lock; a granted-but-stale detector is *interrupted*, not *Protected* (§2.3; the liveness
  timestamps in WP4).
- **Wall-clock jumps:** `queryEvents` is wall-clock based — detect a non-monotonic `now` (NTP / user
  set-time / DST) and reset the cursor to `now` rather than replaying a backward window.
- **Locked / keyguard behavior (critical):** on Android R+ `queryEvents` yields no usable events
  while the user is **not unlocked** (locked / direct-boot). `SCREEN_ON` alone is **insufficient** —
  gate polling on `UserManager.isUserUnlocked()` and add an **`ACTION_USER_UNLOCKED` / keyguard-dismiss
  transition path**: bootstrap/resume on *unlock*, not merely screen-on; hold detection while locked
  (the OS keyguard is protecting the surface).
- **Process-restart bootstrap:** on (re)start seed the cursor from `now` (not the epoch) and enter
  `loading` until the first confirmed post-start `ACTIVITY_RESUMED`; never emit a retroactive lock for
  a transition that predates the detector.
- **Poll lifecycle:** interval `P` (D1 / ADR-021); pause on screen-off / locked / no protected
  selections; bounded backoff on repeated query failure (SDS §15.3/§15.8).
- **Test surfaces (WP3 acceptance — beyond ordinary app→app switches):** notification shade,
  launcher/recents, Settings, split-screen / multi-resume, and the app's own `BiometricHostActivity`
  (an *allow*, §2.3); plus screen-off→unlock→protected-app and cold-start-into-protected-app.

## 3. Work packages (execution order)

### WP0 — Platform validation spike + design ADRs *(de-risk first; throwaway code)*
**Purpose.** Retire M7's highest-uncertainty unknowns empirically before any production code, and
convert them into two accepted ADRs. The unknowns are all platform behaviors that no amount of
design settles on paper: (a) does a drawn `TYPE_APPLICATION_OVERLAY` window actually win the
rapid-relaunch race that the Activity loses (R-002); (b) `BiometricPrompt` cannot be hosted by a
non-Activity window and auto-dismisses when not foreground — is the transparent-Activity-via-BAL
path reliable across API 30/33/35/36; (c) `UsageStatsManager.queryEvents` is not real-time — what poll
interval meets an acceptable end-to-end latency (NFR-PERF-012) at tolerable battery cost; (d)
targetSdk-36 FGS-from-background + BAL rules (SAW + *visible overlay* condition) for the poller
start and the biometric-activity launch.
**Tasks.**
- **Toolchain + target bump to API 36 (D0).** On the spike branch, upgrade AGP `8.7.3` → the
  36-capable release (+ Gradle, + Kotlin if required — **pin the exact versions**), set
  `compileSdk = 36` / `targetSdk = 36`, and confirm the existing build + static analysis
  (detekt / ktlint / lint / Konsist) still pass on 36 before any production adoption. Surface Android-16
  deprecation / lint deltas here, not mid-engine.
- A minimal throwaway module/branch: a foreground service polling `queryEvents`
  (`ACTIVITY_RESUMED`/`MOVE_TO_FOREGROUND`) + a WindowManager overlay draw (with a **stable window
  title** so WP1 can build its probe against it) + a transparent biometric activity. No engine
  wiring, no persistence.
- Measure on the device matrix (API 30/33/35/36 emulator + Moto G 2025 + NucBox): end-to-end
  transition→overlay latency at several poll intervals; overlay-vs-relaunch race (OV-4-shaped burst,
  many bursts); biometric launch reliability from the overlay; battery/wakelock behavior of the
  poll loop; screen-off/on and process-death recreation.
- **R-002 evidence with the current fleet (only the Moto G 2025 as real hardware — a *budget*
  Dimensity-6300 / 4 GB device, not a fast one).** The Moto G was already clean on the old engine, so
  it cannot *prove* the race is fixed — it can only show no-regression. The **decisive** test is a
  **positive-control A/B on the slow software-GPU emulator (NucBox), the rig that reproduces the race
  (12–37 %)**: run the OLD engine's OV-4 burst to confirm the `ABSENT`/`BEHIND` failures reproduce,
  then the NEW overlay on the identical config to show it goes to *never `ABSENT`*, `BEHIND` only as
  sub-poll self-healing flickers. The Moto G contributes budget-real-hardware no-regression +
  never-`ABSENT` under burst + latency + biometric, plus a **best-effort stress** attempt (developer
  options: Don't-keep-activities, minimal background-process-limit, animator scales; memory pressure)
  to try to elicit the race on real hardware.
- **Narrowed residual = OEM window-manager + OS-version overlay handling** (not device speed — the
  Moto G is already budget). Retire it with **Firebase Test Lab's physical multi-OEM / multi-API
  catalog** by reframing the OV-4 race check as a **UIAutomator instrumentation test**
  (`UiAutomation.executeShellCommand()` runs the `am start` bursts + `dumpsys window` z-order/focus
  sampling; grant Usage Access + overlay via `appops` in `@Before`). The same test artifact runs on
  the local emulator, the Moto G (GMD matrix), and FTL — and becomes a permanent WP2/WP6 regression
  asset, not throwaway. (If the overlay ever proves raceable, the analysis §4.1 fallback —
  `setPackagesSuspended()` via Device Owner — is prototypable on the single Moto G via
  `dpm set-device-owner` over adb; it would need a new ADR, likely superseding ADR-013B's
  presentation decision.)
- Record an **evidence report** (`docs/reports/campaigns/`, dated) with the numbers, stating the
  fleet limitation (single OEM/OS real device) explicitly.
- **Finalize ADR-020 / ADR-021** (drafted **Proposed** 2026-08-25 and already indexed in
  `adr/README.md`, with `Alternatives considered` written): **record ADR-021's measured values — poll
  interval `P`, p50/p95/p99 latency, battery profile — in their SSOT** (WP0 report + the detector
  constant + NFR-PERF-012), **not in the ADR body** (GOVERNANCE §2.7); confirm **ADR-020**'s
  biometric-via-BAL (case (a): visible-window Activity launch) and overlay-wins-race behaviors on the
  API-36 target — then move both **Proposed → Accepted** before WP2 (GOVERNANCE §2.2). Any status change
  is a same-commit index update (§2.6).
**Dependencies.** M1 closed. None else.
**Outputs (two distinct kinds — keep them separate, R10).**
- **(1) Disposable spike** — the throwaway polling + overlay + biometric module/branch. **Not merged
  as production; explicit cleanup point: deleted (or its branch abandoned) at WP0 close**, its
  findings surviving only in the evidence report + the ADRs.
- **(2) Committed, surviving artifacts** — the dated evidence report (`docs/reports/campaigns/`); the
  **OV-4 race check reframed as a UIAutomator black-box instrumentation test** (`am start` bursts +
  `dumpsys window` z-order/focus sampling; `appops` grants in `@Before`), authored to run against the
  spike now and the real engine later, so it becomes a **permanent WP1/WP2/WP6 + FTL regression
  asset** (committed, not thrown away); **ADR-020** + **ADR-021** (Proposed → **Accepted** before WP2);
  the NFR-PERF-012 poll-interval decision (D1).

The split matters under the git-only handoff: the disposable code has a clean end, the durable test
survives.
**RTM (this WP's commit):** none — WP0 lands no production code. ADR-020/021 go to the ADR index
(§2.6), not to RTM.
**Acceptance.** The two ADRs are Accepted; the report shows — **all measured per the §11 numeric
protocol and dispositioned per the canonical R-002 standard** — (i) a poll interval whose end-to-end
latency (p50/p95/p99) the lead accepts against NFR-PERF-012; (ii) the **emulator A/B** on the
race-reproducing rig: old engine `ABSENT`/`BEHIND` reproduced, new overlay *never `ABSENT`*
(self-healing `BEHIND` only), **and** the Moto G shows no-regression (never `ABSENT`); (iii) biometric
launching from the overlay on the matrix; (iv) the §2.2 platform cells (†) confirmed **on API 36**
(D0); (v) the **API-36 toolchain builds clean** — build + detekt/ktlint/lint/Konsist green on
`compileSdk`/`targetSdk` 36. **Hard gate — stop and escalate if:** the emulator A/B does **not**
eliminate the `ABSENT` failure, **or** the Moto G regresses, **or** the 36-capable toolchain proves
blocked (invoke the D0 fallback). The **OEM/OS-diversity residual** (FTL sweep) is a
residual-with-plan, not a milestone blocker — gating on an unobtainable device is itself R-003 schedule
risk.
**Risks / implications.** If the drawn overlay does *not* win the race on slow real hardware, ADR-
013B's core remediation is undermined; WP0 surfaces that before sunk cost. Spike discipline
required — the spike informs, it is not shipped.

### WP1 — Harness rework: the security freeze for M7 *(overlay/poll-aware assertions)*
**Purpose.** The WP2 harness that gated M1 asserts on the **resumed Activity** (`LockScreenActivity`
via `dumpsys activity activities`) and rebinds **accessibility** in setup — both die with the old
engine (`ROADMAP.md` M7). Rework it to assert on the **overlay window** and grant via **appops**, so
every later engine swap is mechanically verifiable, and validate the reworked harness against the
WP0 spike build (which has a real overlay) before trusting it to gate production code.
**Tasks.**
- `scripts/e2e/lib.sh`: replace `is_lockscreen()`/`top_component` lock detection with an
  overlay-window probe (`dumpsys window windows` matching our overlay window title — set a stable
  title on the overlay `LayoutParams`); reframe `foreground_is`/OV-4's "protected content not
  foreground" as "our overlay window is present, on top, and focus-holding" (the protected Activity
  legitimately stays the top *Activity* under the overlay).
- Replace `rebind_a11y()` / `a11y_working()` with `grant_usage_access` (`appops set <pkg>
  android:get_usage_stats allow`) + `grant_overlay` (`appops set <pkg> android:system_alert_window
  allow`) + a behavioral `detection_working()` probe (protected app → overlay appears). Remove
  `A11Y_CLASS`/`A11Y_COMPONENT`.
- Raise OV-4 burst count and add an outer repeat so a green result is meaningful for the
  probabilistic race (R-002 planned action #3); keep OV-3 (relock), F3 (self-gate), smoke_core.
- Update `setup_device.sh`, `README.md`, and `run_all.sh` summary.
**Dependencies.** WP0 (needs the spike overlay build to validate overlay assertions).
**Outputs.** Reworked `scripts/e2e/` proven against the spike build; a dated baseline run record.
**RTM (this WP's commit):** none — harness / test infrastructure (no requirement-state changes).
**Acceptance.** The reworked scripts pass against the WP0 spike build **per the §11 protocol**
(defined burst/repeat counts + `ABSENT`/`BEHIND` budget, not an ad-hoc "2/2"); a deliberately-missing
overlay grant makes `detection_working()`/smoke fail (negative control).
**Risks / implications.** Overlay-window detection via `dumpsys window` varies by API level —
validate the grep across the §10 lanes (30/33/35/36) (mirrors the M1 `top_component`
API-portability work).

### WP2 — Lock presentation swap: overlay + request-identity *(keep the a11y detector as input)*
**Purpose.** Swap the *presentation* mechanism only, feeding it from the **known-good** accessibility
detector, so any overlay/biometric defect is not confounded with poll flakiness. Close R-002 by
construction here.
**Tasks.**
- Introduce `LockPresenter` port (`present(request)` / `dismiss(requestId)`; returns a
  present-success/failure result so a missing-capability draw failure flips health to *interrupted*
  rather than a false *Protected* — SDS §8.3 step 4 / §8.8). **Port interface in `service/`**
  (consumed by the engine, inward-only); **adapter `OverlayLockPresenter` in `platform/`** (the
  Android/WindowManager boundary — `platform/` is Konsist-R2-exempt, so its dependency on the
  `presentation/` Compose UI is legal). The adapter draws a `TYPE_APPLICATION_OVERLAY` full-screen
  focusable, touch-modal window (blocks interaction with the task underneath, SDS §8.5) hosting the
  existing Compose lock UI via `ComposeView` + a lightweight
  `ViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`; set a **stable window
  title** so the WP1 harness can probe it.
- Move the auth UI out of `LockScreenActivity` into a reusable composable; keep `FLAG_SECURE`
  (non-debug), lockout countdown, PIN pad, biometric affordance.
- **Request-identity model** (SDS §8.4) — four **distinct** concepts, never conflated (this is the
  R7 hazard: launching our biometric host makes *App Lock* the observed foreground):
  - **Observed foreground package** — whatever the detector last reported (a protected app, the
    launcher, **or App Lock's own `BiometricHostActivity`**).
  - **Logical protected target** — the protected package the live `LockRequest` is guarding.
  - **Active authentication surface** — App Lock's own overlay + `BiometricHostActivity`; while it is
    foreground the *observed foreground is our own package* → treat as **allow** (§2.3) and **do not
    supersede or cancel** the in-flight request.
  - **Current request id** — the single in-flight `LockRequest(targetPackage, requestId)`.
  A completion (`onUnlockSuccess` / failure / dismiss, each carrying its `requestId`) is accepted only
  when its `requestId` is current **and** the logical protected target still holds — explicitly **not**
  gated on "observed foreground == target" (biometric makes our package foreground). Replace the ad-hoc
  `lockScreenTarget` + relaunch-on-every-event logic (`ApplicationLockEngine.kt:38-127`) with
  present / reuse (same target returns) / supersede (new target) on the single current request.
- **Request survives recreation (two cases, neither persisted).** Request identity is **in-memory**
  (DDS §1.3 forbids persisting active lock requests / foreground identity — invariant 6). On
  **Activity / config recreation** (process survives) the `@Singleton` engine keeps the request and the
  recreated surface re-attaches; on **process death** the in-memory reference is *gone*, so recovery
  **re-derives** foreground + policy from the detector's next observation and creates a **fresh**
  request (re-present only if a protected app is currently foreground) — it does *not* restore the old
  one. `BiometricHostActivity` is **`exported=false`** so the auth surface can't be driven externally.
- Biometric: a transparent `BiometricHostActivity` (`FragmentActivity`, `presentation/`, declared in
  the manifest, `excludeFromRecents`/`taskAffinity=""`/`FLAG_SECURE`) launched from the overlay as a
  **background-activity-launch permitted by the app's visible overlay window** (BAL — ADR-020 case (a),
  distinct from the FGS-start rule); returns result to the engine by the `requestId`; overlay stays behind it and
  the PIN pad is the fallback on biometric cancel/error. (Per ADR-020; `BiometricPrompt` cannot live
  in a non-Activity window and auto-dismisses off-foreground.)
- `ApplicationLockEngine.launchLockScreen` → `presenter.present(request)`; `onLockScreenDismissed`
  → presenter dismiss + home. Delete `LockScreenActivity` **and its manifest `<activity>`
  declaration** (recommend delete + a dedicated `BiometricHostActivity`; decide in ADR-020, D4).
- Update Konsist R2 baseline: the grandfathered `service/ApplicationLockEngine.kt -> presentation`
  edge changes shape (now depends on the `LockPresenter` port, not `LockScreenActivity` directly) —
  adjust the baseline entry, keep the rule green.
- **Rework the androidTest smoke seed:** `LockScreenLaunchTest` launches `LockScreenActivity` and is
  run by the GMD CI matrix — replace it with a smoke over the new surface (present the overlay with a
  target/requestId and assert the PIN prompt renders + FLAG_SECURE; and/or a `BiometricHostActivity`
  launch test). Keep the matrix green; update the WP8 GMD runbook reference.
- **(Optional consolidation)** the R-005 readiness state (WP3) is mechanism-agnostic and touches this
  same engine core; it MAY be introduced here to avoid editing the engine twice, and cold-start-
  verified in WP3. Decide at WP2 start.
**Dependencies.** WP0 (ADR-020), WP1 (harness).
**Outputs.** `LockPresenter`/`OverlayLockPresenter`, `BiometricHostActivity`, request-identity engine
change; DI wiring (`AppModule`).
**RTM (this WP's commit):** **FR-027** Lock Screen Display (`partial`→`implemented`) and **FR-028**
Overlay Security (`not-started`→`implemented`) — the overlay presentation lands here;
`implemented-verified` at the WP6 matrix. Request-identity is the R-002 remediation *by construction*
(risk-register note; evidenced at WP6).
**Acceptance.** With accessibility still the detector, **per the canonical R-002 standard + §11
protocol**: the emulator A/B shows the overlay eliminates `ABSENT`, the Moto G shows no-regression
(this WP *re-validates* the remediation — it is **not** "closed on real hardware"); biometric unlock
works from the overlay; OV-3 relock, F3 self-gate, smoke_core green; JVM + instrumentation tests for
request-identity pass — **supersession, stale-result rejection, biometric cancel, rotation/recreation,
and process death** each leave the correct single request state (R7).
**Risks / implications.** Compose-in-overlay lifecycle plumbing is the fiddliest code in M7 (owner
wiring, `WindowManager` add/remove ordering, back-key handling; Home cannot be intercepted and
dismisses to launcher — acceptable per SDS §8.5). This WP re-validates R-002; a real-hardware OV-4
pass here is the primary closure evidence.

### WP3 — Detection swap: UsageStats poll + fail-secure readiness *(keep the overlay; R-005)*
**Purpose.** Swap the *detection* mechanism to the Usage Access poll feeding the same engine seam,
and build the fail-secure readiness model so cold-start/pre-load events cannot fail open (R-005).
**Tasks.**
- Introduce `ForegroundDetectionSource` port (emits normalized `current package + observation time`);
  implement `UsageAccessDetector` **exactly per the §2.4 detection contract** (query window + overlap,
  `(package,timestamp)` dedup cursor, freshness `F`, wall-clock-jump guard, per-API event selection,
  `isUserUnlocked`/keyguard gating, process-restart bootstrap) at the WP0-chosen interval (ADR-021).
- Host the detector in a foreground service (D2: repurpose `ProtectionWatchdogService` into the poll
  host, or a new `ProtectionDetectionService`); lifecycle per SDS §15.2 (start only when PIN set +
  ≥1 protected app + capabilities present; stop when none selected; stop querying + report *Action
  required* if Usage Access revoked); bounded retry + backoff, no tight loop (SDS §15.3).
- **Re-home the screen-state receiver — and gate resume on *unlock*, not screen-on.** `ACTION_SCREEN_OFF`
  currently lives in `AppDetectionService` and is the **only** driver of
  `ApplicationLockEngine.onScreenOff()` (session clear per SDS §8.6/§15.7 — and the sole clear path for
  the `SCREEN_OFF` relock policy). Move it into the poll foreground service: `SCREEN_OFF` →
  `onScreenOff()` + **pause polling** (battery, SDS §15.8). **Do not resume on `SCREEN_ON` alone** —
  `queryEvents` yields nothing while the user is locked (§2.4); resume/bootstrap on
  **`UserManager.isUserUnlocked()` / `ACTION_USER_UNLOCKED` / keyguard-dismiss**. This must land no
  later than WP3 (when the poll service exists) and before WP5 deletes the a11y service, or screen-off
  relock silently breaks.
- Wire the detector to `ApplicationLockEngine.onAppForegrounded`; retire the accessibility service as
  the *input* (still present in the manifest until WP5, now disconnected/disabled for testing).
- **Fail-secure readiness (R-005):** model `LockPolicyManager` cache state as `loading / ready /
  failed` (replace the `emptySet()` seeded fill, `LockPolicyManager.kt:21-40`); `evaluate()` maps
  non-`ready` to the **§2.3 hold/interrupt decisions** (never *not-protected*), and a decision before
  the first confirmed snapshot does not allow. The user-visible surfaces (checking / recovery shields,
  `T_ready`, the escape affordance) are the §2.3 table. Deterministic cold-start / process-restart unit
  + instrumentation tests.
- NFR-PERF-012 instrumentation: measure enforcement response (foreground result → presentation begin,
  ≤250 ms) and end-to-end (transition → overlay), **reported p50/p95/p99 per the §11 protocol** and
  recorded under NFR-PERF-015.
**Dependencies.** WP2 (overlay presentation must exist so the poll drives a real lock), WP0 (ADR-021).
**Outputs.** `ForegroundDetectionSource`/`UsageAccessDetector`, the poll foreground service, the
readiness state model, benchmark harness.
**RTM (this WP's commit):** **FR-026** Foreground Application Detection (`not-started`→`implemented`;
Usage Access baseline replaces the a11y detector — its M1 burndown note is resolved); **NFR-PERF-012**
(`not-started`→`partial`; figures recorded, `implemented-verified` at the WP6 matrix). **R-005**
readiness lands here (register note; Closed at WP6 on the cold-start tests).
**Acceptance.** Accessibility **off**, Usage Access **on** → protected apps detected and locked via
the overlay; OV-3 relock via poll; OV-4 still green (§11); **screen-off relock verified for both the
IMMEDIATE and the `SCREEN_OFF` relock policies** (screen off → return to a protected app → lock
re-presents), proving the re-homed receiver; **`SCREEN_ON`-while-locked does *not* resume detection —
only unlock does** (§2.4); the **§2.4 test surfaces** (shade, launcher, Settings, split-screen, our own
biometric host, screen-off→unlock→protected) pass; the R-005 cold-start test shows a protected app
launched in the pre-load window is **held/locked**, never allowed; NFR-PERF-012 figures recorded and
within the accepted bound.
**Risks / implications.** Poll latency is the dominant term and is device-dependent; the SDS accepts
"poll interval + 250 ms" as the *documented* figure, so honesty (not a hard sub-second promise) is
the bar. `specialUse` FGS + the March-2026 battery/wakelock policy require a frugal loop
(screen-off / no protected selections stop) — the battery profile from WP0 is the evidence.

### WP4 — Health re-point + truthful protection state *(FR-179; consolidates R-005 surface)*
**Purpose.** Re-point protection-health monitoring from accessibility to Usage Access + overlay, and
surface the SDS §8.7 states truthfully, so the watchdog cannot read an empty/unready cache as
"protection unnecessary" (the R-005 watchdog half) and cannot report *Protected* without both grants.
**Tasks.**
- `ProtectionWatchdogService.checkProtectionHealth` (`:86-115`): replace
  `AppDetectionService.isEnabled(this)` with checks for Usage Access grant + overlay grant +
  detector liveness; the alert notification deep-links to the correct settings (Usage Access /
  "Display over other apps"), not `ACTION_ACCESSIBILITY_SETTINGS` (`:152-167`).
- Health readiness: the watchdog treats non-`ready` policy state and unverified detector/presentation
  capability as *Unknown/not verified* or *Protection interrupted*, never as "nothing to protect"
  (R-005 watchdog half, `:87-90`).
- **Detector-liveness — three timestamps, not "a recent observation" (R9).** A quiet phone has no app
  transitions, so "produced a foreground observation recently" would falsely flip to *interrupted*.
  Track **three** independent signals, each judged against a condition-aware threshold:
  1. **last successful *query cycle*** (the poll ran, `queryEvents` returned) — liveness of the loop
     itself, expected every `P` while active;
  2. **last *valid foreground observation*** — informational, **not** a health input on its own (a
     no-transition device is healthy);
  3. **last successful *presentation / readiness check*** (policy `ready`, overlay drawable).
  A granted-but-**stale query cycle** (1 not advancing while active) is *Protection interrupted* (SDS
  §8.8 "repeated detector failure"); (2) never drives *interrupted* by itself. All thresholds are
  suspended under **screen-off / locked / backoff / no protected selections** — the loop is
  *intentionally* paused there (§2.2/§2.4), not failed. This is the Usage-Access analog of the a11y
  "enabled-but-not-delivering" gap (R-001c). Touches RTM FR-231/FR-242 (§6).
- **Re-point the `MainActivity` capability banner** (`:276-335`): replace the direct
  `AppDetectionService.isEnabled` read + `ACTION_ACCESSIBILITY_SETTINGS` deep-link with the new
  capability/health query and Usage Access (`ACTION_USAGE_ACCESS_SETTINGS`) + overlay
  (`ACTION_MANAGE_OVERLAY_PERMISSION`) deep-links; update the `accessibility_needed_*` strings. This
  is the **minimal** truthful recheck M7 needs (it also un-blocks the WP5 compile); the polished
  two-grant setup checklist (UI/UX SCR-001..) is **M8**. Doing it here (off `AppDetectionService`)
  makes WP5's deletion clean.
- **Surface protection-state transitions in-memory only — no DB writes (R11 / invariant 6).** DDS
  v1.0.0 §1.3 excludes security-event history **and** persisted health history, and `SecurityEventDao`
  is an inactive-schema object slated for **M8** removal; **do not** write new rows to it (that would
  re-activate an excluded domain). Hold current health + the last transition in memory for the
  watchdog/UI; emit bounded **logcat** for field triage; capture durable evidence in `docs/reports/`.
  (A persisted event/health log, if ever wanted, is an M9 decision with its own governed schema — not
  M7.)
**Scope note.** M7 produces the *capability signals* (usage granted, overlay granted, detector live,
policy ready) and the watchdog's coarse states (*Protected* / *Action required* / *Protection
interrupted* / *Unknown*). The full seven-state health **vocabulary/dashboard** (incl. *Degraded*,
*Partially configured*) and its screens are **M8** (UI/UX surfaces) — M7 lays the signals they render.
**Dependencies.** WP2, WP3.
**Outputs.** Re-pointed watchdog health + notifications; protection-state surfacing.
**RTM (this WP's commit):** **FR-179** Permission Change Detection is `implemented-verified` today and
this WP changes its health source (a11y → Usage Access + overlay) — **re-verify in this commit or it
drops to `invalidated`** (§1.3 rule 3); **FR-231** Startup Health Check / **FR-242** Runtime Self-Test
→ `partial` **iff** the three-timestamp liveness lands, else leave `not-started` and defer to M9 (do
not overstate, §1.4). **R-001** is re-rated at WP6.
**Acceptance.** Revoking Usage Access **or** the overlay grant while apps are protected flips health
to *Action required*/*Protection interrupted* and raises the correct-destination notification;
granting both restores *Protected*; the watchdog does not stand down on an unready cache; **a quiet
device with no app switches stays *Protected*** (R9 — liveness keys off the query cycle, not
observations).
**Risks / implications.** If D2 consolidates poll + health into one service, this WP edits that
service; if separate, it edits the watchdog and reads detector liveness across the boundary.

### WP5 — Accessibility cutover *(isolated removal commit)*
**Purpose.** Remove the accessibility service and its manifest declaration in one isolated,
reviewable commit, once the new engine is green end-to-end. Nothing ships that references it.
**Tasks.**
- Delete `app/src/main/java/com/applock/applocker/service/AppDetectionService.kt` and
  `res/xml/accessibility_service_config.xml`; remove the `<service … BIND_ACCESSIBILITY_SERVICE>`
  block and the `accessibility_service_label` string.
- Remove `AppDetectionService.kt` from the Konsist R4 `r4PinnedEntryPoints` set
  (`ArchitectureRulesTest.kt:182-185`) — the pin was to protect a persisted grant on upgrade; with no
  production install and the service gone, the pin is dormant-binding in **ADR-018** (2.0.0 return),
  not an R4 exemption. Update ADR-018's implementation-status line to note the 1.0.0 removal;
  `UninstallProtectionReceiver`'s pin is untouched.
- Manifest permission audit for the *detection* surface: `PACKAGE_USAGE_STATS` +
  `SYSTEM_ALERT_WINDOW` present; no accessibility declaration; `FOREGROUND_SERVICE_SPECIAL_USE`
  subtype string updated to describe app-lock foreground detection (Play justification).
- Purge the a11y helpers left in `scripts/e2e/` (already replaced in WP1) and any `A11Y_*` references.
- Confirm `BootReceiver.onReceive` (`:50`) starts the consolidated poll+health service (its
  `ProtectionWatchdogService.start` call now targets the repurposed service, D2) — best-effort under
  targetSdk-35 FGS-from-background rules (existing try/catch pattern), reporting *Action required* on
  next app entry if the OS defers the start (SDS §15.5).
**Dependencies.** WP2, WP3, WP4 all green (androidTest reworked in WP2, `MainActivity` banner
re-pointed in WP4 — both off `AppDetectionService` — so this deletion compiles cleanly).
**Outputs.** Cutover commit; updated ADR-018 status line; updated manifest.
**RTM (this WP's commit):** none — detection already swapped in WP3; FR-043/045/253 stay `descoped-v1`
(unchanged). **ADR-018** implementation-status line is amended for the 1.0.0 a11y-pin removal — an
Accepted-ADR amendment, staged with its §2.3 classification in the changelog (GOVERNANCE §2.8).
**Acceptance.** Merged manifest (`gradlew … processProdReleaseManifest` output) contains **no**
accessibility service and **no** `BIND_ACCESSIBILITY_SERVICE`; Konsist green with the pin removed;
full harness green post-removal; a repo grep of `app/**` for `AppDetectionService`,
`ACTION_ACCESSIBILITY_SETTINGS`, and `accessibility_service` returns **nothing** (docs/ADR history
excepted); unit + androidTest suites compile and pass.
**Risks / implications.** Low — the removal is authorized by ADR-013B (no new ADR needed). The only
trap is leaving a dangling reference (string, xml, Konsist set, harness) — the isolated commit makes
that greppable.

### WP6 — M7 gate: matrix, benchmark, final verification evidence, close-out
**Purpose.** Produce the exit evidence, promote the rows held pending the matrix, and synchronize the
gate documentation. **RTM is not batched here** — each WP already landed its own rows (§6; GOVERNANCE
§1.3). WP6 adds only the final *verification evidence* and the gate disposition.
**Tasks.**
- Full **§10 matrix** with the new engine — the **reworked GMD instrumentation seed** (WP2) runs green;
  the **OV-4 burst on the §10 lanes** is recorded **per the canonical R-002 standard** (the WP0/WP2
  emulator A/B is the decisive proof; the Moto G run is real-device *no-regression* — WP6 does not call
  real hardware "decisive").
- **Verify the §2.1 seam-transfer checklist** in the gate record — every responsibility that lived in a
  removed component has a green new owner (no orphaned relock/health/detection path).
- **Verify the §2.2 restart cells** — boot / process-death / force-stop / permission-revoke behave and
  report the tabled health state across the §10 lanes.
- **Close the R-002 OEM/OS residual:** run the OV-4-as-instrumentation-test on a **Firebase Test Lab**
  physical multi-OEM / multi-API sweep; record the results. If FTL isn't yet provisioned, log the
  residual as a TM §14.10 compensating treatment with a review trigger (before M10) rather than
  overstating R-002 to full Closed (the fallback).
- Record the NFR-PERF-012 end-to-end figure (p50/p95/p99, §11) and the documented poll interval.
- **Final verification promotions only** (rows already landed by their WP, now with matrix evidence):
  **FR-026 / FR-027 / FR-028 / FR-044 → `implemented-verified`**; **FR-049 → `implemented-verified`**;
  **NFR-PERF-012 → `implemented-verified`**; **NFR-PERF-015 → `partial`** (M7 delivers the
  detection-latency benchmark; remaining perf-benchmark scope documented as M9); **FR-179**
  re-verification confirmed (or already applied in WP4). ADR-020/021 → Accepted+implemented status
  lines; ADR-013B implementation-status → 1.0.0 baseline built. Risk register: **R-002** per the
  canonical standard (Closed-with-residual, or High→Medium, residual per the OEM/OS residual sweep /
  fallback — never full-Closed on single-OEM evidence); **R-005 → Closed** (fail-secure readiness tests); **R-001** re-rated (a11y
  gone; residual = the two grants + distribution model, Open for M10).
- Changelog; **M7 gate record** in `docs/reports/gates/` (scope/exit checklist; a new dated record per
  the reports-immutability rule).
**Dependencies.** WP1–WP5.
**Outputs.** Dated campaign report; benchmark record; RTM/ADR/register/changelog updates; gate record.
**Acceptance.** Every §Exit item checked with evidence links; no Critical/High risk carried past the
gate unremediated (TM §14.9/§14.10).

## 4. Risks

| Risk | Mitigation |
|---|---|
| Drawn overlay does **not** win the relaunch race (R-002 premise fails) | Decisive test is the **emulator A/B** (old→new on the rig that reproduces 12–37 %), not real hardware; the only real device (Moto G 2025, a *budget* device already clean) gives no-regression; the **OEM/OS residual** is closed by a Firebase Test Lab physical sweep (OV-4 reframed as an instrumentation test). Fail on the A/B or a Moto G regression = stop/escalate |
| **Fleet: only one real device, one OEM/OS** (Moto G 2025 / Android 15) — OEM window-manager overlay handling unverified | OV-4-as-instrumentation-test runs on FTL's multi-OEM/multi-API physical catalog; residual carried as a TM §14.10 compensating treatment + review trigger if FTL deferred; a cheap second-OEM used device is the alternative |
| `BiometricPrompt` unhostable in an overlay; transparent-Activity path flaky under targetSdk-36 BAL rules | WP0 proves the transparent-`FragmentActivity`-via-BAL path on API 30/33/35/36; PIN fallback is always present so biometric is never a hard dependency |
| Poll latency misses an acceptable end-to-end target | WP0 sets the interval on measured data; NFR-PERF-012's documented figure is "poll + 250 ms", not a hard sub-second cap — the bar is honesty + acceptance, not a promise the platform can't keep |
| Cold-start / process-restart fail-open (R-005) | Readiness model built into WP3 (`loading/ready/failed`, non-ready ⇒ hold), deterministic cold-start tests; watchdog readiness in WP4 |
| Refactor regresses the F3/F4/OV gating semantics the M1 harness protects | WP1 lands the reworked harness first and gates every later WP; one-mechanism-per-WP isolation; nothing proceeds while the harness is red |
| `specialUse` FGS rejected by Play / battery policy penalty | Frugal poll loop (stop on screen-off / no protected selections), battery profile recorded in WP0; the FGS justification string set in WP5; Play review itself is M10 |
| targetSdk-35 FGS-from-background start blocked (boot / app-open) | WP0 confirms start paths under the SAW + visible-overlay rule; the service starts from foreground (MainActivity) as today, boot-start treated as best-effort (existing `ProtectionWatchdogService.start` pattern) |
| Compose-in-overlay lifecycle-owner plumbing bugs | WP0 spike exercises it; WP2 isolates it with the known-good a11y detector so defects aren't confounded with poll flakiness |
| Dormant ADR-018 pin mishandled on removal | WP5 removes the R4 *exemption* but preserves the ADR-018 *record* (2.0.0 return); no shim class |
| **Orphaned screen-off relock** — `onScreenOff` loses its only driver when the a11y service is deleted (breaks the `SCREEN_OFF` relock policy silently) | Explicit receiver re-home in WP3 + screen-off relock in WP3 acceptance + the §2.1 ownership checklist |
| **Presentation-layer coupling to the detection mechanism** — `MainActivity` compiles against `AppDetectionService.isEnabled` and shows an accessibility banner | Re-pointed in WP4 (off the deleted class, onto usage/overlay health) before WP5 deletes it; grep-clean acceptance in WP5 |
| **Instrumentation seed breaks the CI matrix** — `LockScreenLaunchTest` launches the deleted lock Activity | Reworked to the overlay/biometric-host surface in WP2; matrix runs it green in WP6 |

## 5. Decisions flagged for review (pause points)

- **D0 — Target-API baseline — RESOLVED 2026-08-25 (lead): adopt `targetSdk 36` (Android 16),
  effective at WP0.** Google Play requires targetSdk 36 for submissions/updates from 2026-08-31
  (extension to 2026-11-01); v1.0.0 is a new app whose first submission is **M10** (past both dates), so
  the release must be API 36 regardless — the extension would only defer the work. M7 rebuilds the
  targetSdk-sensitive engine (FGS/BAL §2.2, overlay, UsageStats §2.4), so targeting 36 up front
  validates it **once** on the shipping target instead of re-validating after M7. **Execution:** the
  toolchain bump — AGP `8.7.3` → a 36-capable release (pin the exact version in WP0; likely Gradle
  `8.10.2`→newer, possibly a Kotlin bump) — plus `compileSdk`/`targetSdk` 36, is done in **WP0's
  throwaway spike first**, then carried into production; **M1's API-35 device gate is untouched** (the
  closed tree is not re-bumped). A `targetSdk` bump is a build change (recorded in the changelog), not
  an ADR; §10 carries API 36 as a first-class lane. **Fallback (re-decide only if WP0 finds the
  36-capable toolchain blocked):** hold 35 and file the Play extension for the M10 submission.
- **D1 — Poll interval** (latency vs battery). Recommend a fixed bounded default from WP0 data (candidate
  range ~300–800 ms) with backoff on repeated query failure; documented per NFR-PERF-012. → **ADR-021.**
- **D2 — Detector service topology:** one foreground service that both polls and reports health, vs a
  separate poll service + the existing watchdog. Recommend **consolidate** (single FGS — same
  "protection required" lifecycle gate, fewer FGS, smaller Play/battery surface). → **ADR-021.**
- **D3 — Biometric hosting:** transparent `FragmentActivity` launched via SAW/BAL (recommended, the
  only viable path — `BiometricPrompt` needs an Activity host) vs dropping biometric from the overlay
  (rejected — SDS §8.5 mandates biometric-when-eligible). → **ADR-020.**
- **D4 — `LockScreenActivity` disposition:** delete it and add a dedicated `BiometricHostActivity`
  (recommended) vs shrink it to the biometric host. → **ADR-020.**
- **D5 — Overlay UI hosting:** `ComposeView` + `ViewTree*Owner` wiring in the overlay window
  (recommended — reuse the existing Compose lock UI/theme) vs a classic `View`. Implementation note,
  not an ADR.
- **D6 — R-002 real-hardware coverage (fleet):** given only the Moto G 2025 (budget, single OEM/OS),
  how to close the OEM/OS overlay-handling residual — (a) **Firebase Test Lab** physical sweep via the
  OV-4 instrumentation test (recommended — no hardware to own, reuses the test artifact), (b) acquire
  a cheap second-OEM used device (Samsung/Xiaomi — different WMS), or (c) carry it as a TM §14.10
  compensating treatment to M10. Adopting FTL is a new CI/tooling dependency — flag for the lead;
  small enough to be a decision note rather than an ADR unless it becomes a standing CI gate.

## 6. RTM impact — each row lands in its WP's commit (GOVERNANCE §1.3), **not** batched

Consolidated view; the owning WP is named. A row that lands as `implemented`/`partial` is promoted to
`implemented-verified` at **WP6 with the matrix pointer** — that promotion is the *only* RTM action WP6
takes (no batch re-touch). The `review`-era note on FR-026 ("a11y detector does not satisfy v1.0.0")
is resolved by the replacement.

| Row | Change | Owning WP |
|---|---|---|
| **FR-026** Foreground Application Detection | `not-started` → `implemented` → `implemented-verified` | WP3 lands; WP6 verifies (matrix) |
| **FR-027** Lock Screen Display | `partial` → `implemented` → `implemented-verified` | WP2 lands; WP6 verifies |
| **FR-028** Overlay Security | `not-started` → `implemented` → `implemented-verified` | WP2 lands; WP6 verifies |
| **FR-044** Overlay Permission Verification | `not-started` → `implemented` → `implemented-verified` | WP2/WP4 land; WP6 verifies |
| **FR-049** Lock Engine Performance | `not-started` → **`implemented-verified`** (numeric targets = NFR-PERF-012) | WP3 measures; WP6 verifies |
| **FR-179** Permission Change Detection | `implemented-verified` → **re-verify in the WP4 commit, or `invalidated`** (§1.3 rule 3; health source a11y→usage+overlay) | WP4 |
| **NFR-PERF-012** Application Lock Detection Latency | `not-started` → `partial` → `implemented-verified` (benchmark) | WP3 lands; WP6 verifies |
| **NFR-PERF-015** Performance Benchmarking | `not-started` → **`partial`** (M7 delivers the detection-latency benchmark; remaining perf-benchmark scope = M9, documented) | WP6 |
| **FR-231** Startup Health Check / **FR-242** Runtime Self-Test | `not-started` → `partial` **iff** the WP4 three-timestamp liveness lands; else stay `not-started`, defer to M9 (do not overstate, §1.4) | WP4 |
| **FR-043 / FR-045 / FR-253** | stay `descoped-v1` (ADR-013B) — unchanged | — |

Risk register (WP6): **R-002** per the canonical standard (§Exit) — never full-Closed on single-OEM
evidence; **R-005 → Closed**; **R-001** re-rated. ADR-020/021 added to the ADR index (WP0).

## 7. Why this plan (vs the four alternatives)

- **vs Strangler-Swap (minimal-change):** adopted its seam-at-a-time discipline and big-bang-last
  cutover, but the pure swap patches R-005 and leaves the ad-hoc `lockScreenTarget` race logic; the
  request-identity + readiness models are the *specified* fix, so they are built in, not bolted on.
- **vs Ports-&-Adapters (cleanliness):** adopted the two narrow ports (detection, presentation) — the
  ADR-013A "Trigger Processor" seam that hosts the 2.0.0 accessibility tier — but stopped at
  *proportionate* abstraction: two ports, not a full hexagonal rewrite of auth/repos (that lands with
  its consumers, 2.0.0).
- **vs Deterministic-State-Machine (correctness):** adopted the parts that close the two live risks
  by construction (request-identity for R-002, readiness states for R-005) without a full-engine FSM
  rewrite, which would regress the very gating semantics the M1 harness protects at maximum risk.
- **vs Spike-Validated (empirical):** adopted WP0 wholesale — M7's real risk is platform behavior,
  not code, and front-loading the spike + ADRs protects the schedule (R-003) and de-risks R-002
  before sunk cost.
- **vs Dual-Engine flag (reversibility):** rejected the parallel-run flag/source-selection layer —
  it re-admits R-001 exposure during M7, doubles the code paths, and is largely throwaway for 1.0.0;
  WP0 + one-mechanism-per-WP isolation give most of its safety without the complexity. The single
  detection **port** (not a selection layer) is the reusable-for-2.0.0 residue kept.

## 8. Assumptions & open items to resolve before WP2 code

1. **WP0's decisive R-002 evidence is the emulator A/B, not real hardware.** With only the Moto G 2025
   (a *budget* device already clean on the old engine) the race cannot be reproduced on real hardware,
   so the fix is proven on the slow emulator that does reproduce it, plus Moto G no-regression. If the
   A/B does not eliminate the failure, ADR-013B's remediation is unproven and the milestone premise
   must be revisited. The **OEM/OS-diversity residual** is real and closed by the Firebase Test Lab
   sweep (or an approved compensating treatment) — a fleet dependency, not a code one.
2. **Distribution model (Play vs sideload) is still open** (R-001 planned action #1). M7 does not
   require it, but it sets the residual Usage-Access Restricted-Settings friction (R-001a) and the
   M10 compliance story — flag it at the M7 gate as an open-risk input.
3. **ADR-020 and ADR-021 Accepted** (D1–D4 resolved) before WP2.
4. **NFR-PERF-012 acceptance is a documented figure, not a hard sub-second cap** — confirm the lead
   accepts the WP0 poll-interval end-to-end number as the stated target.

## 9. Effort & sequencing

Strict order **WP0 → WP1 → WP2 → WP3 → WP4 → WP5 → WP6**; nothing after WP1 proceeds while the
reworked harness is red. WP2 (presentation) precedes WP3 (detection) so each step changes one
mechanism against a known-good other half. Suggested session cut: WP0 (1–2, the discovery one),
WP1 (1), WP2 (1–2, the fiddly overlay/biometric one), WP3 (1–2, poll + readiness + benchmark),
WP4 (1), WP5 (1, isolated cutover), WP6 + gate record (1). Re-estimate M8–M10 at the M7 gate
(R-003 planned action).

## 10. Test matrix — exact lanes *(replaces the shorthand "API 30–35"; R12)*

The device definitions in `app/build.gradle.kts` (`managedDevices` + `groups`) are the SSOT; the
discrete set below is authoritative — "API 30–35" elsewhere in this plan is shorthand for these lanes.

| Lane | APIs / devices | What runs | Purpose |
|---|---|---|---|
| **CI** (GitHub Actions, KVM, x86_64) | GMD `ci` group = **API 30 + 35 + 36** | reworked overlay/biometric-host androidTest smoke + the OV-4 UIAutomator race test | per-push gate; M7 burns `continue-on-error` off as M1 did |
| **Local emulator** (NucBox `full`, x86_64) | GMD `full` = **API 26 / 29 / 30 / 33 / 35 + 36** | authoritative smoke matrix **+ the emulator A/B positive control** on the software-GPU rig | the **decisive A/B** — the decisive R-002 proof; api29 Argon2-heap caveat from WP8 still applies |
| **Physical** (Moto G 2025, arm64 / API 35) | one real device (budget, single-OEM; Android 15 today) | connected androidTest smoke + OV-4 burst | the **no-regression check** — real-device no-regression + arm64 native SQLCipher + real biometric. targetSdk-36 app runs here on API-35 OS; **Android-16 *OS* behavior is covered by the emulator api36 lane** (add real API-36 hardware coverage if the device updates) |
| **FTL** (Firebase Test Lab, physical) | multi-OEM / multi-API catalog | the OV-4 instrumentation test (same artifact) | the **OEM/OS residual sweep** — closes the OEM/OS residual; the **fallback** if unprovisioned |
| **API 36** (D0 resolved) | API 36 (Android 16) — the shipping target | full smoke + OV-4 + §2.2 cell confirmation | first-class CI + `full` lane; **WP0 adds the `api36` GMD device and confirms the system image is available** |

Adding the `api36` device/group is a `build.gradle.kts` edit (a build change, recorded in the
changelog — D0) done in WP0, not an ADR. The AGP/Gradle bump that compiling against 36 requires is
also WP0 (D0).

## 11. Measurement & pass/fail protocol *(numeric; replaces "many bursts" / "2/2" / "acceptable"; R8)*

All figures below are **candidate defaults — WP0 confirms or adjusts them** (the poll interval one is
recorded in ADR-021); once set they are the fixed acceptance numbers for WP1/WP2/WP3/WP6.

- **OV-4 race burst (per configuration):** **N = 50** bursts, each a storm of **K = 20** rapid
  `am start` relaunches, outer repeat **R = 5**; overlay appearance timeout **`T_appear` = 1500 ms**;
  z-order/focus sampling every **100 ms** via `dumpsys window`.
- **Pass/fail budget:** new overlay — **`ABSENT` = 0** (hard fail on any); `BEHIND` permitted only as a
  sub-poll self-healing flicker resolved within one poll interval `P`, **≤ 2 %** of samples. Old engine
  (paired positive control) **must** reproduce `ABSENT`/`BEHIND` at ≥ the historical 12–37 %, else the
  rig isn't reproducing the race and the A/B is **void** (not a pass).
- **Latency:** report **p50 / p95 / p99** for enforcement response (foreground result → presentation
  begin; target **≤ 250 ms at p95**) and end-to-end (transition → overlay; documented figure = poll
  interval + enforcement, not a hard sub-second promise). **≥ 100** transitions sampled per lane.
- **"Improvement" confidence rule:** declare the overlay an improvement only when the new engine holds
  **`ABSENT` = 0 across the full N·R burst** on the reproducing rig **and** the old engine reproduced
  the failure **in the same session** (paired control) — never on a single lucky run.
- **Battery / CPU:** poll-loop soak **≥ 2 h screen-on-idle + ≥ 8 h screen-off**; acceptance: added
  drain **≤ X %/h** (WP0 sets X from the profile), **no wakelock held across screen-off**, bounded CPU
  wake rate. Recorded in the WP0 report, re-checked at WP6.
