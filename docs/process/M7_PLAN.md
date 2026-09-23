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

### 2.3 Enforcement decision table — what "hold / interrupted" actually does *(readiness model WP2; detector/capability inputs WP3/WP4)*

The engine composes four **separate** layers, never conflated. (1) A **pure policy decision**,
`evaluate()` → `Allow / Lock / Hold / Recover`, over `(policy readiness × capability state × observed
target)`. (2) The **requested surface** the engine maps that decision to. (3) The **presentation
result** `present()` returns (`Presented / Unavailable / Failed`). (4) The **health transition** the
engine derives from that result. A non-`ready` readiness or a missing capability **never** yields
*allow* (invariant 3); it yields a *hold* / *interruption* with truthful notice, and every shield
surface offers an escape so the user is never trapped. Overlay-draw failure (the last table row) is a
**presentation result**, not an `evaluate()` output. `Allow` is not monolithic: allowing our own auth
surface **preserves** the current request (ADR-020), while a launcher / unprotected `allow` may dismiss
or supersede it; the engine's request-identity logic makes that call, not `evaluate()`.

| Readiness | Capabilities | Observed target | → Decision | Overlay surface | Health |
|---|---|---|---|---|---|
| `ready` | both granted | protected package | **lock** | full lock surface (PIN / biometric) | Protected |
| `ready` | both granted | unprotected / launcher / **our own package** (biometric host) | **allow** | none | Protected |
| **`loading`** (policy cache / detector not yet ready) | any | **any foreground not provably unprotected** (cannot classify while loading) | **hold** (never allow) | neutral **"Protection checking…"** shield covering from t=0 (warm overlay; no uncovered grace); **bounded timeout `T_ready`** (provisional 5 s, configurable, monotonic per load-generation) → on expiry **`Checking → Recovery`**, never reveal the target | Checking / Unknown |
| **`failed`** (storage / Keystore / policy-load error) | any | protected package | **hold / interrupt** | **"Protection recovery required"** shield with an **escape affordance** (buttons deep-linking to App Lock and Android Settings) | Protection interrupted |
| `ready` | **Usage Access revoked** | cannot observe | **cannot enforce** → interruption | none → notification only | Action required |
| `ready` | **overlay revoked** | protected package | **cannot present** → interruption | none possible → notification only (SDS §8.3 step 4 / §8.8) | Protection interrupted |
| `ready` | both granted | protected package, but **overlay draw fails** | **interrupt** (never a false *Protected*) | none | Protection interrupted |

**Escape-hatch rule (no unrecoverable loop).** Every shield surface (checking / recovery) MUST
(a) expose a control that reaches App Lock's main screen or Android Settings, and (b) honor Home/Back
to the launcher. **The full lock surface never times out**, and **no shield is ever removed to reveal
the guarded target**: every escape (Home / Back / deep-link) navigates to an approved safe destination
(resolved HOME, App Lock, or Settings) *first*, then tears the overlay down. `T_ready` bounds only the
`loading` state (`Checking → Recovery`), never the lock and never a dismiss-to-target. The overlay is
**modal to the protected task, never to the whole device**: no readiness/capability state can trap the
user behind an undismissable window.

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
  as production. Cleanup deferred to WP2 (decided 2026-08-30):** the committed OV-4 UIAutomator test
  targets the spike's `POLL_SERVICE` / `OVERLAY_TITLE`, so the spike is **held through WP1's harness
  rework and deleted at WP2** when the test is repointed to the production overlay — not at WP0 close.
  Its findings otherwise survive only in the evidence reports + the ADRs.
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
**Approach (decided 2026-08-30, from a three-viewpoint exploration this session).** WP1 is the
**in-place shell port** (Plan A): keep the `scripts/e2e/` operator suite and swap its assertions to the
overlay-window (`dumpsys window`) + `appops` model. Two adjacent viewpoints are scoped, not dropped.
**(C) An engine-declared state oracle**, a debug-only, non-persisted snapshot (read via
`dumpsys activity service <APP_ID>/…ProtectionWatchdogService --applock-oracle-v1`; Decision #2)
naming request-identity, readiness, relock and self-gate state, is adopted as a **WP2 test layer** that
**augments** the UI/window-truth assertions (it attests engine intent, not pixels); it is built with the
engine, not in this harness-only WP. **(B) Convergence onto one GMD/FTL instrumentation suite** proceeds **only by
incremental parity migration**: each check moves to instrumentation once it reaches parity, never a
big-bang rewrite. First increment: OV-4 already has instrumentation parity (the WP0 `OverlayRaceUiTest`),
so the bash OV-4 wraps it (`am instrument`) rather than reimplementing the race, leaving one race truth to
repoint at WP2. Window-truth (`dumpsys window`) for overlay presence is retained permanently, since an
engine oracle cannot observe `HIDE_NON_SYSTEM_OVERLAY`.
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
overlay grant makes `detection_working()`/smoke fail (negative control). **Slow-rig caveat (WP0
emulator finding).** The committed spike keeps the un-fixed overlay (the warm-overlay / off-main remedy
is reverted to WP2/WP3), so it coin-flips vs the 1500 ms `T_appear` on the NucBox software-GPU lanes
(more bursts *raise* P(fail), never lower it). So the **NucBox lanes validate probe/grep portability
across API 30/33/35/36 + the old-vs-new A/B delta** (both robust to the coin-flip); the **clean §11
ABSENT=0 pass comes from the Moto G real hardware**, not the swGPU spike. Make
`OverlayRaceUiTest.T_APPEAR_MS` an instrumentation arg so the swGPU lane can scale it, and keep the
lanes on `aosp/default` images (`aosp-atd` ships no target app → OV-4 assume-skips; already fixed in
`build.gradle`).
**Risks / implications.** Overlay-window detection via `dumpsys window` varies by API level —
validate the grep across the §10 lanes (30/33/35/36) (mirrors the M1 `top_component`
API-portability work).
**Formal-review dispositions (2026-08-31 independent review; `docs/reports/reviews/2026-08-31_m7-wp1-harness-formal-code-review.md`).**
A WP1 hardening pass applied six control fixes in code: reject `LOCK_ENGINE=prod` for OV-4 (the test is
spike-hardcoded until WP2) (CR-001); fail-closed input validation so a vacuous run cannot PASS (CR-003);
fail-closed APK / androidTest install with a `USE_PREINSTALLED` escape (CR-005); a `neg_overlay_grant.sh`
missing-grant negative control (CR-006); OV-4 requires + arithmetic-validates its retained count line
(CR-007); prod-only checks skip with exit 3 and `run_all` labels the §11 gate vs a non-gate/diagnostic
profile (CR-009 / CR-003). Three findings are deferred with owners:
- **CR-004 → WP2 (resolved, Decision #4, 2026-09-01):** WP1 removed a11y provisioning; WP2 keeps a11y as
  the *detector*, so the `prod` path re-adds a **scoped, non-destructive** enable of `AppDetectionService`
  (ADR-018 FQCN; component `${APP_ID}/com.applock.applocker.service.AppDetectionService`, suffix on the
  app id only). Edit `enabled_accessibility_services` per-user by **appending only the exact component and
  preserving all others** — never delete/rewrite the list or set `accessibility_enabled` off globally (the
  M1 non-destructive lesson: `2026-07-23_wp2-regression_moto-g-2025.md` §2 / item 4). **Delivery, not
  binding, is the gate** (R-001c: bound ≠ delivering) — the `prod` *test checks* read detector delivery
  from the Decision-#2 oracle `lastForeground` ack and the overlay independently via `dumpsys window`
  (the setup preflight may stay end-to-end, since either-layer failure should fail setup). **Failure
  classification:** a run failure is an a11y *detector artifact* **only** when the oracle shows **no fresh
  foreground ack**; if the ack occurred but the surface failed, it is a **presentation defect** (a real
  WP2 bug, not a re-run) — this keeps "Moto G a11y flakiness" from masking an overlay bug. Also fix the
  existing circular inference (`setup_device.sh:79` reads "no lock" as "not protected" and would toggle a
  protected app **off** when the detector is bound-but-dead): read the switch's actual `checked` state,
  tap only if explicitly `false`, fail on ambiguous. adb-enable works on emulators; for sideloaded
  services on Android 13+ (observed: Moto G 2025 / Android 15) it lands bound-but-not-delivering (R-001a),
  so real HW needs a **one-time** operator Settings toggle. Provisioning is **check-then-act**: if the
  component is present and behaviorally *delivering* (oracle reverify), **preserve it untouched** — the
  `-r` install keeps the setting, so subsequent runs reverify without rewriting or toggling; act only on
  failure — **default fail-closed with exact remediation; `ALLOW_MANUAL_A11Y=1` bounded-waits (~120 s,
  ~2 s cadence) for a fresh delivery ack**; `--skip-setup` cannot bypass the delivery preflight. `neg_a11y_disabled` (proves the probe catches a bound-but-dead
  detector) runs **emulator-only / behind a destructive flag** — adb cannot restore delivery on real HW —
  removing only our component and restoring in an unconditional trap after a session-clear + oracle
  epoch/seq baseline. Scripts are `set -uo pipefail` (not `-e`) and `sh_` swallows stderr, so every
  settings read/write, membership check, unbind wait, and restore needs an **explicit check with retained
  diagnostics**. **Lifecycle:** provisioning + neg-control invocation are **removed at the WP3 cutover**
  (WP3 must prove UsageStats is the *sole* detector, a11y off) — not WP5; WP5 removes the product
  component + confirms no stale test-device setting remains. **No RTM promotion or R-001 reduction**
  results from this temporary bridge. Kept simple: no presentation×detector seam.
- **CR-002 → WP6 / R-002 closure:** the OV-4 BEHIND score is present/absent per burst, not a streak
  duration; before R-002 closure evidence, measure the max BEHIND streak, reject BEHIND-at-deadline, and
  tighten the small-N `maxOf(1, …)` tolerance to the exact 2 %.
- **CR-008 → WP2–WP6:** `settle()` waits a fixed 300 ms (< the poll interval) and can coalesce
  HOME/target events into a manufactured ABSENT on slow lanes; add a debug-only detector-state ack
  (neutral foreground processed / cursor reset) before each burst. (False-FAIL flakiness, not a false pass.)

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
- **Keep the overlay window warm (WP0 swGPU finding — do NOT per-lock add/remove).** WP0's decisive
  A/B (`docs/reports/campaigns/2026-08-28_m7-wp0-emulator_nucbox-g5.md`, Item 1) traced the residual
  slow-rig ABSENT to the spike's per-lock `addView`/`removeView` + main-thread draw straddling the
  1500 ms `T_appear`. Remedy (demonstrated then reverted in the spike; land it here): **`addView` once
  and toggle presence via `updateViewLayout` + visibility, never per-lock add/remove**, so the window
  is always present and ABSENT is impossible by construction (worst case a §11-allowed BEHIND). This
  took the swGPU rig from **2/150 → 0/150 ABSENT** under 4× CPU load; pair with the WP3 off-main poll.
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
- Update Konsist R2 baseline: once the engine depends on the `LockPresenter` port (a `service/` type)
  instead of `LockScreenActivity`, the `service/ApplicationLockEngine.kt -> presentation` edge
  **disappears entirely** (the `OverlayLockPresenter` adapter lives in `platform/`, which is R2-exempt).
  So **remove** that grandfathered baseline exception (`ArchitectureRulesTest.kt`), not reshape it; keep
  the port's vocabulary (surface kind, request) in `service/` so no `presentation` type leaks back
  through it. (The adjacent `IntruderCaptureManager -> presentation` entry is unrelated and stays.)
- **Rework the androidTest smoke seed:** `LockScreenLaunchTest` launches `LockScreenActivity` and is
  run by the GMD CI matrix — replace it with a smoke over the new surface (present the overlay with a
  target/requestId and assert the PIN prompt renders + FLAG_SECURE; and/or a `BiometricHostActivity`
  launch test). Keep the matrix green; update the WP8 GMD runbook reference.
- **R-005 fail-secure readiness model (resolved 2026-09-01: pulled forward from WP3 to here).** The
  readiness *machinery* is presentation-coupled (its shields are overlay surfaces), so it lands with the
  overlay and neither the engine core nor the overlay is edited twice:
  - **Single atomic policy state.** Replace `LockPolicyManager`'s `emptySet()`-seeded
    `StateFlow<Set<String>>` (`LockPolicyManager.kt:21-40`) with one
    `sealed PolicyState { Loading; Ready(packages); Failed(reason) }`. The first emission, including a
    legitimate empty set, publishes `Ready(packages)` **atomically** (no separate readiness flag that
    could read `Ready` against a stale set). Define retry (`Failed` → backoff → `Ready`), idempotent
    `startCaching()`, and **coroutine cancellation is not `Failed`** (only real load/storage errors are).
  - **Engine-owned readiness aggregate.** The decision consumes a
    `ReadinessContext(policy, detector, capability, foreground)` owned by the engine, **not** by
    `LockPolicyManager` (which owns only `PolicyState`). WP2 wires `policy` for real and defaults
    `detector = Ready` / `capability = Assumed`; WP3 fills `DetectorState`, WP4 `CapabilityState`. The
    seam is defined now so WP3/WP4 slot values in without reshaping the engine.
  - **Layered per §2.3.** `evaluate()` returns the pure decision (`Allow / Lock / Hold / Recover`); the
    engine maps decision → surface, `present()` → result, result → health. Overlay-draw failure is a
    presentation result, not an `evaluate()` output.
  - **`loading` semantics.** While `Loading` the cache cannot classify, so the engine **holds any
    foreground it cannot prove unprotected**, drawing the "checking" shield from t=0 on the warm overlay
    (no uncovered grace). Exempt **only** the exact own package and the dynamically-resolved current HOME;
    treat System UI as a transient non-target observation, not a safe exemption. On `Loading → Ready`,
    re-evaluate the held foreground (unprotected → dismiss to reveal; protected → `Checking → Lock`).
  - **Timers keyed to identity.** Every grace / timeout / present callback is keyed to
    `(requestId, load-generation)`; a stale timer or result is rejected (the R-002 discipline extended to
    readiness). `T_ready` is provisional 5 s, configurable, monotonic (`elapsedRealtime`) per generation;
    on expiry `Checking → Recovery`, never a dismiss-to-target.
  - **Watchdog minimal-correct here (forced by the API change).** `ProtectionWatchdogService:87-88` reads
    the cache the `PolicyState` change replaces, so WP2 updates it to stand down **only** on PIN-unset or
    `Ready(empty)`, never on `Loading` / `Failed`. The full health-truth rewrite stays WP4.
  Cold-start / process-restart **verification** on the real engine stays WP3; the capability-revoked rows
  and the health rewrite stay WP4; R-005 stays **Open** until WP6.
- **Engine-declared state oracle: the WP1-decided Plan-C test layer (Decision #2, resolved 2026-09-01).**
  A **debug-only, non-persisted** read-only view of the authoritative engine state, for the harness.
  - **Contract shape (Phase 1) — it *is* the reducer `State`.** One immutable `EngineSnapshot` published
    through a single atomic reference; consumers read it once. Fields (all emitted from WP2, dimensions
    stubbed until their WP): `process.epoch` (random per process), `snapshot.seq` (per publish);
    `policy.state`, `detector.state`, `capability.usage`, `capability.overlay`, `enforcement.health`;
    `request.{id,target,phase}`, `surface.state` + last present-result; `lastForeground.{pkg,seq}`; and
    monotonic edge counters (`relock.count`, `selfgate.count`, `supersede.count`) each incremented by its
    **authoritative producer at the confirmed transition**, never on intent emission (relock =
    `LockSessionManager`; self-gate = `MainActivity`/`SelfLock`). `lastForeground.seq` advances only after
    the observation is processed and the resulting state is published.
  - **Transport (Phase 3) — `ProtectionWatchdogService.dump()`**, read as
    `adb shell dumpsys activity service <APP_ID>/com.applock.platform.ProtectionWatchdogService --applock-oracle-v1`
    (`APP_ID` from the flavor — prod = `com.applock`, no suffix). This **requires D2 to resolve to
    *repurpose* the watchdog** (§WP3, confirmed at WP3 start). A `src/debug` `ContentProvider` is **only a
    live-process-only diagnostic fallback** if the dump command ever fails on a lane — **never** for
    cold-start / process-death / `loading` observations: querying a provider **auto-starts a dead process**
    (its `onCreate` runs in Application init → `startCaching()`), reviving the very state under test and
    defeating `process.epoch`. `dumpsys activity service` on a stopped service, by contrast, prints
    nothing and starts nothing (clean fail-closed). `dump()` reads the atomic reference once and formats it
    with **no locks / `runBlocking` / DB / side-effects**, and never resets a counter.
  - **Wire format — versioned flat `key=value`** (grep-parseable, no `jq`): unique **begin/end
    sentinels**, `schema=1`, one key per line in canonical order, ASCII enums + decimal ints + `none` for
    null, restricted encoding for package/request values, **no** free-text exception messages. The harness
    parser is **fail-closed**: a missing service, missing sentinel, or missing/duplicate/malformed/
    unsupported-version field is a hard failure, never an empty snapshot or skip. The harness must not
    `am start` the service to inspect it.
  - **Release silence.** Gated by `src/debug` (compile-time absence, the strong guarantee) plus a runtime
    `FLAG_DEBUGGABLE` check (backstop); a release `dumpsys` of the service emits no sentinel and no state.
  - **Roles — the oracle *augments*, it does not replace, rendered-boundary evidence.** It attests engine
    *intent*, not pixels, so each check keeps independent boundary evidence: **OV-3** oracle relock-delta +
    request identity, plus `dumpsys window` overlay present-and-topmost; **F3** oracle self-gate delta, plus
    a uiautomator PIN-gate-visible / App-List-inaccessible check (the self-gate is App Lock's own single
    window, so no window-level distinction exists); **smoke_core** oracle request lifecycle / readiness /
    unlock, plus overlay+PIN renders and the target is reachable only after a valid PIN; **OV-4** oracle
    limited to the neutral-foreground ack + correlation, `dumpsys window` authoritative for presence / z-order.
  - **Closes CR-008 (when adopted).** `lastForeground.{epoch,seq}` is the ack that replaces
    `OverlayRaceUiTest.settle()`'s blind 300 ms sleep (read baseline → HOME → poll until same epoch, seq
    advanced, foreground = HOME, prior request cleared → fail on timeout/malformed). CR-008 is recorded
    closed only when that path passes on the required lanes.
  - **No new persistence** (invariant 6: rendered on demand from in-memory state; logcat is a runtime
    signal, not a DB write). **Structured logcat is a diagnostic mirror only — never an assertion source**
    (this is what removes the WP1 log-rotation race from the gate path).
**Implementation sequence and gates (Decision #3, resolved 2026-09-01).** WP2 builds in four phases;
the **gates** are normative, the tactical ordering within a phase is not.
- **Phase 0 — policy-state foundation + watchdog compatibility.** Atomic `PolicyState`, its lifecycle
  tests, and a pure `shouldStandDown(pinSet, policyState)` the watchdog delegates to (the `Service`
  lifecycle stays an Android test). The production engine is untouched.
- **Phase 1 — pure decision/request reducer.** Extract a pure `(State, Event) → (State, List<Effect>)`
  reducer holding the request-identity + readiness logic. **Identity is classified at the edge**
  (`ForegroundObserved(Own|Home|Other)`), **navigation and logging are emitted Effects**, and **timers
  are `ScheduleTimer(gen)` / `TimerFired(gen)`** — so the pure core needs no injected Android ports, only
  a fake to interpret effects. Ship the port/types, the fake clock/scheduler/presenter, and the read-only
  engine **state-snapshot** the oracle later exposes (its transport is Phase 3). Readiness and identity
  are designed together, shipped as separately reviewable changes. **The production
  `ApplicationLockEngine` still drives the old `LockScreenActivity` path** — it is not switched to the
  reducer + port here (a fake cannot satisfy the Hilt graph, and the completion API is still
  package-keyed at the old call sites). Phase 1 proves the *reducer logic*, not R-002/R-005 closure.
- **Phase 2 — real surface + production cutover.** Extract the auth composable (the full biometric /
  auto-launch / lockout-polling / PIN ownership currently in `LockScreenActivity`, **not** in
  `AuthGateViewModel`); build `OverlayLockPresenter` (three surfaces; **`FLAG_SECURE` re-applied to the
  overlay `LayoutParams`**) and `BiometricHostActivity` (**its own `FLAG_SECURE`**, `exported=false`,
  result keyed by `requestId`); switch production DI + the engine + the completion call sites to the
  reducer + port (request-id-keyed for the overlay; the `MainActivity` self-gate stays its own
  package-keyed path, never forced through a request-id overload); land the overlay/shield
  instrumentation + the **replacement smoke**. **Delete `LockScreenActivity` + its manifest entry and
  remove the R2 baseline exception only after that replacement path is green.**
- **Phase 3 — oracle transport + harness + evidence.** Wire the debug oracle transport (Decision #2)
  over the Phase-1 snapshot; repoint OV-3 / F3 / smoke_core; add CR-004 prod a11y provisioning
  (Decision #4); capture the WP2 device evidence.

**Mandatory gates:** (1) pure state-machine tests pass before any WindowManager integration; (2) a
real-surface test accompanies each surface; (3) the replacement smoke passes before `LockScreenActivity`
is deleted; (4) the oracle/harness evidence is the final WP2 gate.

**Phase 1 reducer model (resolved 2026-09-07).** The pure reducer is `reduce(State, Event) ->
(State, List<Effect>)`, deterministic, no I/O and no clock (time enters via `elapsedRealtimeMs` on
events and via `TimerFired`). Shipped as two separately-reviewable changes: **A** the identity/lock lifecycle with
readiness assumed `Ready`; **B** the readiness fold-in (`PolicyState` -> `Checking`/`Recovery`, the
`T_ready` timer, and the `PolicyStateChanged` re-evaluations).

- **Two independent lifecycles in `State`, mutually exclusive** (`require(activeRequest == null ||
  readinessHold == null)`): `activeRequest: LockRequest?(id, target)` for the lock/unlock path (its
  `RequestId` is minted only on entry to `Lock`), and `readinessHold: ReadinessHold?(target, phase in
  {Checking, Recovery}, timer?)` for the not-ready path (`timer`, a `(epoch, generation)` token, is
  non-null iff `Checking`; it carries the generation, so the hold needs no separate field). `ReadinessHold`
  enforces the `Checking` <-> `timer != null` invariant, and `EngineState.init` requires a live timer to
  carry the state's own `epoch` and current `generation`.
  Also `epoch`, `generation`, `nextRequestId`, `policy`, `lastForeground: {Home|Other}?`, and snapshot
  counters. The **surface is a pure projection**, never independent state: `activeRequest -> Lock`;
  `readinessHold.Checking -> Checking`; `readinessHold.Recovery -> Recovery`; both null -> none.
- **Identity classified at the edge** to `Own | Home | Transient | Other(pkg, hasValidSession)`. Only
  **`Own` and `Transient` are true no-ops** (preserve `lastForeground`/`activeRequest`/`readinessHold`/
  timer/surface; emit nothing; optional `lastTransient` projection for diagnostics). **`Home` is not a
  no-op**: advances `lastForeground`, emits `NoteAppLeft(prev)` when `prev is Other`, clears the
  hold/request (`CancelTimer` if a `Checking` timer was live), and `DismissSurface`. `NoteAppLeft` is
  emitted **exactly once per genuine real-app switch** (`prev is Other && prev.pkg != current`).
- **`evaluate` truth table** (only real foregrounds reach it; `Home` always dismisses):
  `Ready`: protected+no-session -> `LOCK`, else `ALLOW`. `Loading`: any `Other` -> `HOLD` (`Checking`
  + `T_ready`). `Failed`: any `Other` -> `RECOVER` (no timer). `Loading`/`Failed` hold every `Other`
  because none can be proven unprotected without the set (R-005 core); `Failed` never yields `ALLOW`.
- **`PolicyStateChanged(Failed)`** (direct, pure, no session lookup): `activeRequest` -> preserve
  `Lock`; `readinessHold` -> ensure `Recovery` (`Checking` cancels its timer); both null &
  `lastForeground is Other(A)` -> create `readinessHold(A, Recovery, timer=null)` + `Present(Recovery)`
  (closes the standing-allow fail-open — no detector re-emission required); else no surface.
- **`PolicyStateChanged(Ready)`**: `readinessHold` -> re-evaluate its `target` by `isProtected` against
  `Ready.packages` only, **session not consulted (fail-secure override:** re-authenticate a
  possibly-sessioned protected target rather than risk a bypass): protected -> `Lock` (mint, log-once,
  cancel `Checking` timer); not protected -> clear + `DismissSurface`. `activeRequest` -> preserve.
- **Tokens & stale rejection.** `epoch` is random per process (seeded into `State`); a foreign epoch is
  rejected (process-death ghosts). Timer token `(epoch, generation)`; completion token `(epoch,
  RequestId)`. `TimerFired(t)` accepted iff `t.epoch == epoch && readinessHold?.phase == Checking &&
  t == readinessHold.timer`; `UnlockSucceeded/Failed/Dismissed(r)` accepted iff `r.epoch == epoch &&
  r.id == activeRequest?.id`.
- **`generation` advances exactly once per invalidating transition** — one that tears down or replaces
  the current surface (superseding a lock or hold with a different target, or taking it `Home` / allowing
  it) **or** opens a new `Checking` hold; `ScreenOff` always advances it as a reset boundary, even from a
  surface-free state. A fresh lock/hold over nothing and an idempotent same-target re-present do not
  advance it. A single event that both cancels the old checking
  timer and opens a new hold advances **once** (the schedule), emitting one `CancelTimer` and scheduling
  one new-generation timer; a `Recovery` hold carries the generation inertly (no timer). `supersedeCount`
  separately counts lock/hold supersessions by a different target. The invariant `activeRequest == null
  || readinessHold == null` is enforced in `EngineState.init`. Lock audit (`LOCK_TRIGGERED`) is logged
  only on the first entry into locking a target (same-target re-observation re-presents idempotently
  without a new id or log).
- **Completions & lifecycle.** Lockout audit + intruder capture stay pure via a **per-failure-token**
  follow-up: `UnlockFailed` mints a `FailureId` into a per-id `pendingFailures` **map** (so concurrent
  in-flight failures each keep their outcome and a later success drops none), the adapter records the
  failure and dispatches `LockoutRecorded(FailureToken, LockoutState, count)`, and the reducer consumes
  the matching entry **exactly once** — emitting `LOCKOUT_TRIGGERED` (iff `LockedOut`) then
  `CaptureIntruder`, in the legacy order — so a duplicated or unsolicited callback cannot fabricate a
  lockout/capture. The follow-up carries the real `LockoutState` (remaining-ms), stored on
  `EngineState.lockout` so the state-observing lock surface can render it (seeded from the persisted
  `LockoutManager` at adapter construction); the projection updates only from the **current lockout
  streak** (a generation bumped on each success) and, within it, only from the newest outcome (the
  monotonic `failureCount`) — so a pre-success or out-of-order follow-up cannot re-lock or regress it,
  while capture/audit still fire for every consumed failure. **Biometric-cancel** is a token-keyed no-op on request
  state (re-asserts the lock surface; a stale cancel is ignored). **Rotation /
  recreation** needs no reducer event: the presenter is **state-observing** and reconstructs from
  `state.surface`, which carries the `RequestId`, so a recreated surface completes correctly and no
  request is lost or duplicated. **Process death** is covered by `epoch`: a fresh process rejects any
  pre-death token and starts with no active request (no ghost).

**Phase 2 detail (scoped 2026-09-07).** Expands the Phase 2 bullet of the implementation sequence into
six separately-reviewable changes (Phase 1's A/B discipline). **C1, C2, G run on this box** (JVM /
compile / Konsist); **E, F need the fleet** (overlay / biometric / grant); D is JVM. Two Phase-1-model
extensions land here because Phase 2 is the first phase with a state-observing surface.

- **C1 — extract the auth composable (presentation-only).** Move the PIN pad + biometric affordance +
  lockout countdown out of `LockScreenActivity.setContent {}` into a reusable `@Composable LockScreen(...)`;
  the Activity keeps hosting it, behavior identical. Independent of C2. Gate: existing suite green; no
  RTM (behavior-preserving; FR-027 stays `partial`).
- **C2 — pure reducer: token-keyed safe-dismiss protocol.** A state-observing overlay makes the current
  `onDismissed` reveal latent-real: it clears `activeRequest` in the same reduction that returns `GoHome`
  as a *later* effect, so publish-before-interpret drops the surface before navigation runs and flashes the
  guarded app. Replace it, and add the shield escape, with one generic two-step handshake. Only
  leave-without-auth paths use it; unlock-success keeps its immediate `DismissSurface`.
  - **Tokens.** `SurfaceToken = RequestToken(epoch,id) | ReadinessToken(epoch,generation)` identifies the
    live surface a request comes from. A per-request `AttemptToken(epoch,attemptId)` keys the navigation
    round-trip, so a follow-up from a since-reverted attempt over the same surface cannot act on a later
    one. `destination: SafeDestination` is a closed enum `HOME | APP_LOCK | OVERLAY_SETTINGS`, never an
    arbitrary intent.
  - **Step 1.** `SafeDismissRequested(surfaceToken, destination)` validates the live surface, transitions
    the guard slot `Guarding -> LeavingFor` over the same `BlockingGuard` (the surface stays visible), and
    emits `NavigateSafely(destination, attemptToken)`. A request while an escape already stands is dropped,
    not allowed to replace it.
  - **Step 2.** The interpreter launches the destination, then enqueues exactly one attempt-keyed follow-up.
    `SafeDismissNavigationIssued(attemptToken)` clears the escape, advances `generation`, and emits
    `DismissSurface`. `SafeDismissNavigationFailed(attemptToken)` reverts `LeavingFor -> Guarding`
    (reconciled with the current policy) so the control can be retried. A supersession in between makes
    either follow-up stale, and it is dropped: the newer surface never comes down for an old navigation.
  - **Model.** The on-screen surface becomes one sum: `GuardState = None | Guarding | LeavingFor` over
    `BlockingGuard = Lock | Checking | Recovery`, plus a separate `ArrivalExemption` for the post-navigation
    OVERLAY_SETTINGS arrival, and the pure `ReadinessToken` and these events / effects (`Effect.GoHome` is
    subsumed by `NavigateSafely`). The presentation DTOs are D's.
  - **Tests.** stale-follow-up rejection, both surface kinds, generation advance, the OVERLAY_SETTINGS
    arrival exemption, navigation-failure retry, and Failed / Ready escape handling (including a preserved
    Checking escape's timer staying inert under Ready).
- **D — `LockPresenter` port + `LockEngineRuntime` interpreter (JVM, no cutover).** `LockPresenter`
  (`service/`, `present(LockPresentation): SurfaceApplyResult { Success | Unavailable | Failed(reason) }`,
  `dismiss(): SurfaceApplyResult`) keeps its vocabulary in `service/` (no `presentation` type leaks).
  `LockEngineRuntime` (`service/`; provided as a **process-lifetime singleton on the application scope** in
  F's `@Provides`, so D itself carries no Hilt annotation) owns the atomic `EngineState`, exposes the old
  public API + the request-token completions, and implements the package-keyed self-gate directly (the
  authoritative session / lockout mutations via the in-process managers; the observational audit / capture
  contained).
  - **Single logical consumer.** Every input enters one `Channel<Input>` (a private sum of a ready
    `EngineEvent` and a raw foreground that the consumer classifies, so the session fact is read in queue
    order, not snapshotted at the caller) drained by one coroutine confined to a single logical thread
    (`Dispatchers.Default.limitedParallelism(1)` or a `HandlerThread`: serialized execution, *not* thread
    affinity — though the single drain coroutine already serializes, so the confinement is defence in depth).
    It runs `reduce -> publish (single-writer
    `MutableStateFlow<EngineState>`) -> ordered, sequential effect interpretation`. Effect-produced
    follow-ups (`RecordUnlockFailure->LockoutRecorded`, `ScheduleTimer->TimerFired`,
    `NavigateSafely->SafeDismissNavigationIssued`/`SafeDismissNavigationFailed`) are **enqueued**, never a
    reentrant `reduce`; the interpreter emits the failure follow-up when the launch throws or nothing
    resolves. "Effects fully dispatched" means each safety-critical presenter or navigation effect
    completes synchronously, **or is accepted onto the single ordered Main queue**, before event N+1 is
    taken. Bare async posting that N+1 could overtake is insufficient. Atomic reads stay for the lock-free
    presenter and do not substitute for serialization.
  - **Presentation DTOs from post-reduction state:** `LockPresentation(target, RequestToken(epoch, id))`,
    `CheckingPresentation(target, ReadinessToken(epoch, generation))`, `RecoveryPresentation(...)`.
    `Surface.Lock` stays id-only: `epoch` is process-constant, so it belongs at the interpreter boundary,
    not in the pure projection.
  - **Edge classifier / Home resolver.** Raw package -> `Own | Transient | Home | Other(pkg,
    sessionManager.hasValidSession(pkg))`. Home is load-bearing: a misclassified launcher draws a Recovery
    shield via `enterRecoveryIfNeeded`. Resolve the current home via `PackageManager.resolveActivity(MAIN/
    HOME)` (min-26+). `RoleManager.ROLE_HOME` is 29+ and does not expose the holder to a non-system app, so
    it serves only as a 29+ invalidation signal. Cache with invalidation, not permanent memoization:
    broadcasts are the optimization, and the correctness rule is to **re-resolve `MAIN/HOME` whenever an
    observed package differs from the cached launcher before classifying it `Other`** (this covers missed
    signals and a default-launcher change). A short-TTL memo can bound the resolve cost.
  - **Biometric mapping invariant (preserve today's semantics).** PIN rejection -> `UnlockFailed(PIN)`;
    biometric success -> `UnlockSucceeded(BIOMETRIC)`; biometric cancel / hardware error -> `BiometricCancelled`;
    `onAuthenticationFailed()` (unrecognized) -> **no event**. Biometric never routes through `UnlockFailed`
    and never touches the lockout counter.
  - **Three-fact `EnforcementHealth` owner (`service/`).** One serialized owner stores three independent
    facts (`detector: Enabled|Disabled`, `overlayGrant: Granted|Missing`, `presentation: Unknown|Succeeded|
    Failed(reason)`), and **only the owner mutates** the derived-health `StateFlow`; producers submit facts.
    Phase-2 `Healthy` = `detector=Enabled ∧ overlayGrant=Granted ∧ presentation≠Failed`. This is the
    `enforcement.health` oracle dimension (Phase 3). WP3 swaps the `detector` fact for Usage Access and WP4
    does the full watchdog rewrite, both without changing the aggregation.
  - Seed `EngineState.lockout` from `LockoutManager.currentState()` at construction. JVM-tested with fakes:
    the four concurrency cases (timer vs `Loading->Ready`; completion vs supersession; synchronous
    `RecordUnlockFailure` follow-up; N's effects not overtaken by N+1), biometric-never-locks-out, health
    aggregation, safe-dismiss ordering. **No Hilt cutover** (a fake cannot satisfy the graph); production
    stays on `ApplicationLockEngine`.
  - **As landed.** A JVM-testable rewrite of `ApplicationLockEngine` with no Hilt cutover (production stays on
    the old path). The pieces:
    - **Ports** (`service/engine`): `LockPresenter` (`present` / `dismiss` both return `SurfaceApplyResult`,
      with the `LockPresentation` DTOs), `SafeNavigator`, `TimerScheduler` (`schedule` returns whether it
      armed), `HomeResolver`, `AuditLog`, `IntruderCapturePort`, and `RuntimeDiagnostics` (a REQUIRED no-throw
      sink for contained adapter faults); E and F supply the adapters. The self-gate is NOT a port: the runtime
      implements it directly with its in-process managers.
    - **Single consumer.** Serialization is structural (one drain coroutine over an unbounded channel), so
      confining the dispatcher is defence in depth, not the correctness boundary; the presenter and navigator
      effects hop through an injected main dispatcher. A raw foreground rides the same channel and is classified
      **in the consumer, in queue order**, so its session fact is read after any preceding `ScreenOff`.
    - **Resilience.** Every Android-bound port is called through an explicit failure policy (reports route
      through `safeReport`, which contains a throwing sink), so no adapter fault (a throw or a non-throwing
      rejection) can terminate the single consumer. present / dismiss record their outcome to health (a success
      clears a prior failure, so health self-heals) and, on a non-success, schedule a bounded delayed
      **surface-keyed re-drive** (per-surface budget, reset when the desired surface changes; on exhaustion the
      re-drives stop and health stays Failed, and a later surface change or `retryPresentation()` restarts it).
      Navigation failure emits a follow-up; a timer-schedule that fails (false OR throw) escalates the shield to
      Recovery (both modes reported); audit / capture / timer-cancel / home-resolution report and continue (home
      additionally fail-secure not-home).
    - **Total lockout boundary.** `LockoutManager`'s storage is Android-backed and can throw, so the seed read
      degrades to `Available` (reported), a failed reset on success is reported and does not kill the drain, and
      a failed failure-record returns and projects a **contained synthetic** `LockedOut` at the threshold (the
      capture and drain survive). That observation is not persisted and enforces nothing on its own: no
      brute-force attempt is blocked by it until F supplies the authoritative countdown (see contract (4) /
      R-007).
    - **Self-gate.** Lives in the runtime, splitting **authoritative** (session / lockout) from
      **observational** (audit / capture, contained), audits `UNLOCK_FAILURE` first (legacy order, so a degraded
      attempt is still recorded), and inherits the same contained-synthetic behaviour.
    - **API + lifetime.** Keeps the old package-keyed API (`onAppForegrounded`, `onScreenOff`,
      `onUnlockSuccess`, `onUnlockFailure`) and adds the request-token completions, `safeDismissRequested`,
      `retryPresentation`, and `shutdown` (which INITIATES teardown under one lifecycle monitor: a barrier for
      the synchronous self-gate, it closes the input channel and terminally shuts down the timer scheduler, then
      REQUESTS drain cancellation, not joined, so an in-flight effect may complete after it returns). It drops
      `onLockScreenDismissed`, whose only caller `LockScreenActivity` is deleted in G.
    - **Footprint.** Four new `service/engine` files (the ports, `EnforcementHealth`, `LockEngineRuntime`, its
      test) PLUS the atomic `LockoutManager.recordFailureAndCount()` (state + count under one lock) and its
      added tests.
  - **Deferred to E / F (contracts recorded).**
    - **(1) Lifetime / channel.** The runtime is a process-lifetime singleton, so F MUST give it the
      **application scope** (not a service scope: the detector service restarts, the engine must not die with
      it). The input channel is unbounded so it never drops a security transition, and closes when the drain
      ends (`shutdown` / scope cancellation), after which a send is rejected and reported, not accumulated. F
      must NOT coalesce raw foreground observations: the engine re-locks on every foreground event by design
      (fast-switch defence), so dedup would reopen that bypass.
    - **(2) `HomeResolver` under failure.** E's adapter should keep a last-known-good launcher and be tested
      for a working Home escape while resolution is unavailable, so a transient `PackageManager` failure does
      not repeatedly shield the launcher.
    - **(3) Presenter driving.** The **runtime is the sole caller of `present` / `dismiss`**, so every apply
      outcome feeds the authoritative `presentation` health fact. E's presenter may READ
      `LockEngineRuntime.state` to know what to render, but it MUST NOT apply a health-affecting surface change
      on its own: a host recreation (the `ComposeView` rebuilding) or a restored capability calls
      **`retryPresentation()`**, so the re-apply runs through the runtime and health stays truthful (an E-driven
      reconcile could leave health stale `Succeeded` after a failed recreation, or stale `Failed` after a
      success the runtime had given up on). Wiring stays acyclic (the presenter depends on the runtime's state /
      port, not the reverse).
    - **(4) Lockout enforcement convergence (R-007).** D's total-lockout boundary is fail-closed-SHAPED only
      inside the engine (`EngineState.lockout` and the self-gate return value observe a `LockedOut` but enforce
      nothing on their own); the real brute-force gate reads `LockoutManager.currentState()`, which returns
      `Available` after a failed write, so a fabricated fallback lockout is not enforced, and the fabricated
      threshold outcome over-reports (`LOCKOUT_TRIGGERED` / a threshold capture with no persisted deadline). F
      MUST converge this: one manager-owned authoritative countdown (an in-memory degraded deadline when the
      store is unwritable) queried by both hosts, and a storage-failure representation distinct from a recorded
      threshold lockout. Deferred here because the fix belongs with F's real adapters and the shared countdown
      query; latent until the F cutover (production still runs the legacy engine). See RISK_REGISTER R-007.
    - **(5) Queue-lag observability (WP4).** The input channel is unbounded (contract (1)) so it never drops a
      security transition, but a stalled `mainDispatcher` (a wedged present / dismiss on the UI thread) would
      let the backlog grow with no health or backlog signal today. WP4's watchdog / health rewrite SHOULD
      surface a queue-lag diagnostic (the age or depth of the oldest un-drained input), so a stuck consumer is
      observable rather than silent unbounded growth. Not a D behaviour change; the seam is the single drain.
    - **(6) Observational-adapter delivery contract (F).** `AuditLog` and `IntruderCapturePort` are called
      concurrently by the drain AND the synchronous self-gate, and they persist off-thread, so the interpreter's
      `guard` (which contains only a synchronous throw before the method returns) cannot see an async failure.
      F's real adapters MUST therefore be **thread-safe**, **non-blocking** (the self-gate holds the lifecycle
      monitor across them, so a slow adapter delays `shutdown`), **order-preserving** for audit, and MUST
      **catch failures inside their own async job and report them to `RuntimeDiagnostics`** (never drop them).
      An application-scope single-consumer adapter (one queue draining to the DAO, serializing drain + self-gate
      calls) satisfies all of it; the existing `IntruderCaptureManager` is fire-and-forget and does NOT
      self-report, so F must wrap it accordingly. F tests: an async-failure-after-return reports to diagnostics,
      and a concurrent drain/self-gate delivery preserves ordering and loses nothing. The D KDocs on these ports
      record this contract.
- **E — real adapters + overlay capability (fleet).**
  - **`OverlayLockPresenter`** (`platform/`, R2-exempt): warm `TYPE_APPLICATION_OVERLAY`, **add-once +
    `updateViewLayout` / visibility toggle** (WP0 swGPU finding; never per-lock add/remove), **`FLAG_SECURE`
    on the overlay `LayoutParams`** (non-debug; dropped on debuggable so fleet screencap works), a stable
    window title for the harness, and the C1 composable hosted via `ComposeView` + a lightweight
    `ViewTreeLifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner`. It has three surfaces
    (Lock / Checking / Recovery). A draw / `addView` failure or a missing grant returns `Unavailable/Failed`.
    Its `present` / `dismiss` MUST be **idempotent and non-throwing** (the D interpreter contains a throw and,
    for dismiss, leaves the surface up, so a throwing hide could otherwise strand the overlay). Reconciliation
    of a failed apply is the runtime's bounded re-drive, not an E-driven re-apply: E does not reconcile the
    overlay on its own (that would desync the authoritative health fact) — a host recreation or a restored
    grant calls **`retryPresentation()`** so the re-apply runs through the runtime (see D's contract (3)).
    Because the `ComposeView` is warm and add-once, E MUST scope the hosted `LockScreen` by the live request
    token (`key(requestToken) { LockScreen(...) }`, the C1 contract): the composable is state-observing and
    keeps its own wrong-PIN prompt and entered digits, so a direct lock-request supersession over the retained
    composition would otherwise leak the prior request's input into the next.
  - **`BiometricHostActivity`** (`presentation/`, `FragmentActivity`): transparent, own `FLAG_SECURE`,
    `exported=false`, `excludeFromRecents`, `taskAffinity=""`. It is launched from the overlay as a BAL
    permitted by the visible overlay window (ADR-020 case (a)). Its intent carries `epoch`+`requestId` plus a
    single-flight lease id, all saved in `savedInstanceState`. It returns the token as both epoch and id, so a
    host recreated in a fresh process returns the old epoch.

    The `BiometricLaunchGate` is keyed by **token AND lease**. The presenter claims a lease before the BAL. The
    host acknowledges (token + lease) in `onCreate`, which completes a one-shot latch that lives ON the lease
    handle, so the latch stays observable even after the host releases the gate. Both release by (token +
    lease). `startActivity` does not throw when the system aborts a background launch silently (`START_ABORTED`
    is not fatal), so an unacknowledged lease expires and is reclaimable, and an aborted launch that never
    started a host does not wedge the gate (the PIN pad stays available). The once-per-request auto-prompt is
    consumed only after that latch completes (a host actually started), not when `startActivity` returns. If the
    lease expires unacknowledged, the auto-launch makes one bounded, cancellable retry (its budget is tracked
    outside Compose, so a host rebuild does not re-arm it), then gives up unmarked, leaving PIN and the manual
    button. `acknowledge` accepts ONLY the gate's exact, unexpired (token, lease); it rejects a free gate, an
    expired (abandoned) lease, and a foreign or reclaimed lease. Thus a host recreated after **full process
    death** finds an empty gate, finishes SILENTLY, and never authenticates over or blocks the fresh runtime's
    re-derived request (ADR-020: process death does not restore the old request).

    A LOCKED_OUT launch is a documented no-retry suppression that matches the legacy Activity: the countdown and
    the manual biometric button remain the paths, and a lockout that later expires does not auto-prompt
    (accepted product behaviour, not a defect). The auto-prompt timing (a 4 s unacknowledged-lease timeout, a
    ~5 s acknowledgement wait, one retry) is opinionated to prevent a runaway BAL launch loop; the fleet
    confirms its aborted and slow-launch behaviour (API 35/36) before any tuning. `TimerScheduler` (Handler /
    `HandlerThread`) posts `TimerFired`.
  - **`PackageManagerHomeResolver`** (`platform/`): resolves `MAIN/HOME`, keeps a last-known-good launcher,
    never throws, and re-resolves whenever the cached entry is **stale** (older than a short TTL) regardless of
    whether the observed package matches the cache, so a default-launcher change is picked up and a failed
    resolve is TTL-bounded. Re-resolving only on a *differing* package trusted a
    cached launcher forever, so a demoted FORMER launcher stayed classified `Home` and bypassed the lock (a
    fail-dangerous gap): re-resolving once the cache is stale closes it, and a JVM test asserts the former
    launcher is no longer `Home` after the default changes. **Two bounded within-TTL residuals remain, both
    deferred to F (validate on-device).** (1) NEW-launcher direction, **fail-safe**: a just-changed default
    launcher reads as `Other` for up to the TTL (~2 s); the new launcher is not a locked app, so it is
    user-visible only under a degraded (`Failed`) presentation policy, as a Recovery shield over home for that
    window. (2) FORMER-launcher direction, **fail-dangerous but bounded**: a demoted former launcher observed
    within the TTL is still trusted as `Home` for up to the TTL (the indefinite past-TTL bypass is closed;
    only this within-TTL window remains); a JVM test documents this residual (former launcher still home within the TTL,
    not-home past it).

    **F3 as built (2026-09-22, `30529e2`):** a tri-state resolve result (`Resolved` / `NoDefault` / `Failure`) and
    a re-resolve on every new foreground episode, whatever the package, in both directions. The runtime flags the
    episode across App Lock's own UI and the system windows. The planned per-candidate negative cache was dropped,
    because it could suppress the re-check of a package that later became the default. A default change
    made through the Settings or role UI is now detected on the next app switch. The ≤TTL windows are not fully
    closed, because a classification stays in effect until the next foreground observation. The remaining cases are
    risk **R-008** (proposed, decision at F6). On-device check: Moto G 2025, 3/3 PASS
    (`docs/reports/campaigns/2026-09-22_m7-wp2-f3-home-resolver_moto-g-2025.md`).
  - **Overlay capability.** Move `SYSTEM_ALERT_WINDOW` out of the throwaway spike block (currently manifest
    line 18, otherwise deleted with the spike) into the permanent permissions. Add the
    `Settings.canDrawOverlays()` check and an `ACTION_MANAGE_OVERLAY_PERMISSION` grant path (onboarding + a
    settings entry). The check submits `overlayGrant`; each `SurfaceApplyResult` (via the runtime, the sole
    presenter caller) submits `presentation`, and a restored grant calls `retryPresentation()`. Shield
    Back controls emit `SafeDismissRequested`.
  - **Safe-dismiss contract with C2 (as implemented).** The `OVERLAY_SETTINGS` escape must carry the
    *resolved* destination package(s) in `SafeDismissRequested.exemptPackages` (E resolves the
    `ACTION_MANAGE_OVERLAY_PERMISSION` target via `PackageManager` before dispatching), and must use the
    shield's `ReadinessToken`. The reducer rejects an `OVERLAY_SETTINGS` request with no package or a
    `RequestToken`, so a lock may only escape to `HOME`/`APP_LOCK` (this keeps the arrival exemption from
    ever arming under a `Ready` policy). After attempting the launch, the interpreter enqueues exactly one
    attempt-keyed follow-up carrying the `NavigateSafely.attempt` token:
    `SafeDismissNavigationIssued(attempt)` once the destination launches, or
    `SafeDismissNavigationFailed(attempt)` if it could not (launch threw or nothing resolved). A transient
    failure then reverts the escape for retry instead of stranding the surface. **Deferred to F, a MANDATORY
    prerequisite for the production cutover:** `NavigationIssued` currently
    fires when `startActivity` returns without throwing, but a silently aborted background launch
    (`START_ABORTED` is non-fatal, confirmed in AOSP `Instrumentation.checkStartActivityResult`) also returns
    without throwing, so the runtime could tear the overlay down before the safe destination is actually
    foreground, briefly revealing the guarded app. F hardens this by: keeping the overlay and `LeavingFor(attempt)`
    active after requesting navigation; completing the attempt only once an approved destination is OBSERVED
    (`Home` / `Own` / the exact resolved Settings package); a monotonic, attempt-keyed timeout that reverts to
    the guard on expiry; and rejecting late observations / callbacks by their `AttemptToken`. Tests: launch
    aborted, wrong-package arrival, timeout, supersession, and successful arrival. E's `RealSafeNavigator` keeps
    its total launch-return contract unchanged; the arrival gating is the runtime/interpreter's, added in F.
  - **Instrumentation (`OverlayLockPresenterUiTest`, UiAutomator).** Per surface, it asserts the RENDERED
    content (the PIN pad on Lock; text plus escape controls on the Checking / Recovery shields), blocks a
    real underlying touch with a sentinel activity (a genuine injected tap, never a focus inference), and
    exercises Back, the shield escape controls, and the biometric button THROUGH the surface UI. It drives
    the presenter directly, so it is insulated from the Moto G accessibility flakiness (R-001a). FLAG_SECURE
    is split: the flag policy is JVM-tested (`OverlayWindowFlagsTest`), and the debuggable APK reads visible
    behaviour (debug drops FLAG_SECURE by design). The deployed-secure proof on a non-debug build is DEFERRED to
    F (see the F section): before the cutover there is no non-debug entrypoint that shows the overlay, so there is
    nothing to screencap; `OverlayWindowFlagsTest` covers the flag policy meanwhile. A companion suite (`OverlayWindowHostFaultUiTest`) fault-injects the
    internal `OverlayWindowHost` seam (a decorator around the real `WindowManager`, not a whole-surface fake;
    the `LockPresenter` / runtime / DI contract is unchanged) to prove the failure-atomic reset: an
    attached-removal failure retains the window (no orphan / duplicate add), an already-detached root cleans
    up without a removal call, and a reset then a different presentation re-adds. It also proves the cold-add
    fail-atomic reveal: a first `present()` whose reveal `updateViewLayout` throws leaves the window in the
    dismissed, pass-through state (a sentinel confirms the underlying touch is not blocked), then recovers on
    the next `present()` without a second add.
  - **Gate-2 execution (fleet).** Primary target = the **Moto G 2025** (real hardware, API 35 / arm64) on
    the `connected` lane, for the content / underlying-touch / escape / Back assertions. Two paths are not
    covered there and complete the gate elsewhere: the API-36 predictive-back routing (targetSdk 36 stops
    dispatching `KEYCODE_BACK`, an Android-16 device behaviour) runs on the **NucBox api36** emulator (`full`
    matrix). The deployed-secure FLAG_SECURE proof is **deferred to F** (Decision 2026-09-14; F owns it, see the
    F section). Biometric-through-the-UI runs only with an enrolled authenticator, which needs a `google_apis`
    image (otherwise the test asserts the no-enrollment host path). The NucBox runbook is
    `docs/testing/M7_WP2_GATE2_NUCBOX_PLAN.md`.
    Gate 2 is not closed until the Moto G run passes. RTM: **FR-044** Overlay Permission Verification
    (`not-started`->`partial`; verification WP6).
- **F — production cutover (fleet).** F replaces the legacy lock path in production. It swaps DI (`AppModule`) to
  `LockEngineRuntime` with the real adapters on the **application scope** (not a service scope; see D's lifetime
  contract). It also delivers three logic pieces that earlier changes deferred to F: safe-dismiss **arrival**
  confirmation (F1), **R-007** degraded-storage lockout enforcement (F2), and the **HomeResolver** residuals (F3).
  The plan was approved on 2026-09-18. This entry is the design record for change F; the changelog holds the
  per-commit detail.
  - **Sequence and status.** Each step is one or more independently gate-green commits. The user commits each one.
    1. **F1** safe-dismiss arrival confirmation: done (`b0175fa`).
    2. **F2** R-007 degraded-storage lockout enforcement: done (`b9e7c53`; residuals `dbfb86b`; report `520f15d`).
    3. **F3** HomeResolver tri-state and episode revalidation: done (`30529e2`; device check `8ad3b4c`; R-008).
    4. **F4** observational adapters: next.
    5. **F2 hardening** R-007 residual treatment: scheduled; option not chosen.
    6. **F5** graph wiring, not visible, with a fleet checkpoint: pending.
    7. **F6** activation and RTM flip: pending.
    8. **Fleet close-out**: pending.
  - **Delivery rules.** The local gate for each step is `gradlew.bat testProdDebugUnitTest detekt assembleProdDebug
    compileProdDebugAndroidTestKotlin`. This box cannot boot emulators, so emulator runs and the `prodRelease`
    FLAG_SECURE proof run on the NucBox. Real-hardware runs use the Moto G, which is attached to this box. A step
    that changes the implementation of an RTM row cites requirement-specific evidence in the same commit. F1 and F3
    are isolated (not wired). F2, F2 hardening, and F4 edit production singletons that the legacy engine and the
    self-gate use now, so each keeps the healthy-path behaviour and tests those callers.
  - **Invariants carried from E (do not change).** Biometric `acknowledge` accepts only the exact live lease
    (ADR-020), and the ack latch lives on the `LeaseHandle`. The overlay auto-prompt is consumed only on ack, with
    one bounded retry whose budget is kept outside Compose. The overlay is added dismissed and is revealed only
    after the fallible flag-apply. `resetHost` is total and failure-atomic. The ViewTree owners sit on the
    window-root `OverlayRoot`. The HomeResolver re-resolves unconditionally once the last attempt is older than the
    TTL. The runtime is the only `present`/`dismiss` caller, surfaces never dismiss themselves, and
    `retryPresentation` is edge-triggered only.
  - **F1 — safe-dismiss arrival confirmation.**
    - `startActivity` also returns for a silently aborted background launch (`START_ABORTED`). So the reducer no
      longer hides the surface when the launch call returns. An escape ends only when its destination is observed.
    - Arrival evidence: `Foreground.Home` for HOME. For OVERLAY_SETTINGS, an approved package that differs from the
      guarded origin; a same-package observation stays an origin re-present. For APP_LOCK, an explicit
      `AppLockForeground` signal keyed by the attempt. `Foreground.Own` is never arrival evidence, because it cannot
      tell `MainActivity` from `BiometricHostActivity`.
    - APP_LOCK and OVERLAY_SETTINGS escapes need a shield (`ReadinessToken`). `LeavingFor.navigationIssued`
      records the issue. An arrival completes the escape in either state, because the drain can see the destination
      before the issued echo.
    - An attempt-keyed arrival timeout (`TimerToken.ArrivalTimer`, through `TimerScheduler`) reverts an escape that
      does not arrive. A scheduling failure reverts at once. Completion, supersession, screen-off, and shutdown
      cancel the timeout.
    - F6 connects `MainActivity.onResume` to the signal and adds the attempt to the launch intent.
    - Evidence: `LockEngineReducerTest`, `LockEngineSafeDismissTest`, `LockEngineRuntimeTest` (M7-new; no verified
      RTM row).
  - **F2 — R-007 degraded-storage lockout enforcement.**
    - `LockoutManager` keeps an in-memory authoritative snapshot, which `currentState()` reads without a lock. It
      persists on a dedicated single-thread dispatcher. A short `synchronized` section admits each mutation: it
      assigns the revision, publishes the snapshot, and enqueues the write. So the snapshot order equals the disk
      order. Persistence is never awaited inside that section.
    - The recorded (persisted) deadline and the in-memory fallback deadline are separate, and `degraded` is derived
      from them. A failed failure-write arms the fallback, also below the 5-failure threshold, if its authentication
      streak is still current. The fallback window starts when the write completes. Only a committed threshold write
      clears `degraded`. A successful unlock advances the streak.
    - `EncryptedPrefsLockoutStorage` calls `commit()` directly and returns its result. Re-seeding after a cold-start
      read failure stops at the first local mutation. Production injects a sleep-aware clock
      (`SystemClock.elapsedRealtime`).
    - The runtime drain never awaits persistence: it admits the mutation at once and awaits the result off the
      drain. The runtime self-gate entry points are `suspend` and await outside the `lifecycleLock` barrier.
      `ApplicationLockEngine` (live until G) uses a completion callback.
    - `LOCKOUT_TRIGGERED` is audited only for a recorded lockout. Intruder capture receives the actual count of
      every failure. A failed reset is reported (`lockout_reset`) and never re-locks the user.
    - RTM: FR-174 stays `implemented-verified` (`docs/reports/campaigns/2026-09-21_m7-wp2-f2-lockout-jvm_2012-i7.md`
      and per-push CI). The four R-007 residuals are treated at F2 hardening.
  - **F3 — HomeResolver tri-state and episode revalidation.**
    - The port is `isHome(packageName, newForegroundEpisode)`. The runtime sets the flag when a package differs from
      the previous raw foreground, including Own and Transient, so `A -> Own -> A` is a transition. ScreenOff does
      not reset the flag.
    - The resolver resolves on the first call, when the last attempt is older than the 2 s TTL, and on every new
      episode, for every package. A repeat within one episode uses the cached answer. After a failure, it makes no
      attempt until the TTL expires.
    - `Failure` keeps the last-known-good launcher. `NoDefault` (null, or the chooser `android`) clears it.
    - The manifest `<queries>` declares `MAIN/HOME`. Do not remove it. Without it, Android 11+ package visibility
      can hide the default launcher (for example, AOSP Launcher3). `resolveActivity` then returns Settings'
      `FallbackHome`, and Settings would be classified as Home.
    - The planned negative cache was dropped (see the HomeResolver note in E). The remaining stale-answer cases are
      risk R-008 (proposed; decision at F6).
    - Evidence: `PackageManagerHomeResolverTest`, `LockEngineRuntimeTest`, the Moto G device check
      (`docs/reports/campaigns/2026-09-22_m7-wp2-f3-home-resolver_moto-g-2025.md`), and the CI emulator lanes of
      `HomeResolverDeviceTest`.
  - **F4 — observational adapters.** F4 adds the real `AuditLog`, `IntruderCapturePort`, and `RuntimeDiagnostics`
    adapters (the `LockEnginePorts.kt` contracts). Delivery is at most once, and every failure is reported.
    - `RuntimeDiagnostics`: a no-throw, thread-safe `Log.w`/metric sink.
    - `AuditLog`: maps `AuditEvent` to `SecurityEventType`. One `Channel` consumer on the app scope drains to
      `SecurityEventDao` and keeps the order of delivered records. Each insert is contained: a DAO failure is caught
      and reported as undelivered, and the drain continues. There is no retry. A record admitted after close is
      reported. Scope cancellation stops the drain, and the drain's `finally` reports the still-queued records once,
      best effort (diagnostics can be tearing down too).
    - `IntruderCapturePort`: a thin wrapper of `IntruderCaptureManager.onAuthFailure(pkg, method.name, count)`. An
      optional diagnostics hook on `IntruderCaptureManager` reports its asynchronous insert failure. The hook
      defaults to a no-op, so legacy construction and tests do not change. This edits a production file although
      the adapter is not wired, so a test asserts that the no-hook behaviour is unchanged.
    - Files: new adapters, placed with the Room-bound `service/` collaborators for R2 (check
      `ArchitectureRulesTest`); a minimal hook edit to `service/IntruderCaptureManager.kt`.
    - Tests (JVM/Robolectric): audit order; a DAO failure on one record does not stop later records; the
      admission-failure report; the queued-at-cancellation report; a concurrent drain and self-gate keep the order
      and lose nothing silently; diagnostics never throws; intruder mapping and the async-failure report; the
      default-hook behaviour is unchanged.
    - RTM: no verified row changes. The hook keeps the FR-081/082 behaviour (cite `IntruderPolicyTest` and the
      unchanged-behaviour test).
  - **F2 hardening — R-007 residual treatment.** The four R-007 residuals move here from F6 (placement adopted
    2026-09-22). This is separate work with its own commits and evidence; it does not reopen F2. The options and the
    analysis are in `docs/process/proposals/2026-09-22_R007_F2_HARDENING_OPTIONS.md`.
    - **Option A**, an authentication redesign: a durable attempt record before PIN verification, and recovery of
      interrupted attempts after a restart. It gives stronger restart protection. It also adds a storage dependency
      to every attempt and can deny legitimate access during a storage fault.
    - **Option B**, mitigation with bounded recovery retries. B1 recovers from a cold-start read failure
      automatically. B2 retries a failed persistent change safely. B3 (optional) persists the degraded deadline.
    - **Recommended start: B1 and B2.** B3 is an explicit scope decision. Residual /3 (process death before a write
      completes) stays largely open under Option B, so the lead must decide it.
    - **Boundaries for either option:** never await storage on the runtime drain; keep one ordered writer; do not
      hold the admission lock across I/O; stale work must not replace newer state; recovery never fabricates
      failures, captures, or audit events, and never extends the fallback deadline; the immediate in-memory reset
      after an accepted success stays.
    - **Exit:** the selected work passes the local gate and a fleet gate (NucBox, Moto G: fault injection, restart,
      and inspection of the persisted state) with a host-tagged report. The lead records a decision for each
      residual in the risk register. FR-174 cites re-verification evidence in the same commit. If Option A is
      chosen, the asynchronous self-gate work planned for F6 moves into it.
  - **F5 — graph wiring, not visible.** F5 adds providers, binding, and monitoring only. It makes no user-visible or
    health-visible change. The new engine is constructed and bound but stays inert: no foreground events reach it,
    and no overlay is shown.
    - `di/AppModule.kt` provides `EnforcementHealth` (`@Singleton`), the F4 adapters, and `LockEngineRuntime` on
      `@ApplicationScope`: a fresh `Epoch`; `policyState = LockPolicyManager.state`; `Dispatchers.Main`; the real
      `OverlayLockPresenter`, `RealSafeNavigator`, `HandlerTimerScheduler.create()`, and
      `PackageManagerHomeResolver(context)`; `ownPackageName`; `SystemClock::elapsedRealtime`. The singleton scope
      gives one shared `EnforcementHealth`.
    - `AppLockApplication.onCreate` injects the runtime and calls `LockCompletionBridge.bind()` once. A `false`
      return is fatal, and the app throws. An unbound or wrongly bound bridge drops completions, so correct PINs
      never unlock and failed PINs never reach lockout accounting.
    - `OverlayGrantMonitor` (singleton) owns the overlay-grant fact and its false-to-true edge. `refresh()` submits
      `submitOverlayGrant(...)`. On a false-to-true edge it calls `runtime.retryPresentation()` exactly once. It is
      the only place with edge detection, so no two callers reset the re-drive budget twice.
    - The unexpected-detach handler on `OverlayLockPresenter.OverlayRoot.onDetachedFromWindow` is de-duplicated and
      suppressed during teardown: one unexpected detach re-arms once, and a normal dismiss or shutdown does not. It
      calls `presenter.release()` first, which clears the host after it confirms the detach. It then calls
      `retryPresentation()`. The order matters, because `ensureAdded()` returns early on a non-null `overlayRoot`.
    - `retryPresentation()` is used for edge-triggered recovery only (a restored overlay grant, an out-of-band host
      detach), never after a `present()` fault. Change E removed the presenter's BadToken self-trigger: a fault fed
      back into the failing present resets the re-drive budget and defeats the bound. A fault inside `present()`
      relies on the runtime's bounded re-drive.
    - **Fleet checkpoint before F6.** It runs the tests, not only compiles them. The runtime, the monitor, and the
      readers share one `EnforcementHealth` instance. `bind()` succeeds once, and a second bind is fatal. An
      overlay-grant revoke and restore flips the fact and fires one `retryPresentation()`. An unexpected detach runs
      release-then-retry once and re-adds. The wired graph uses the hardened `LockoutManager`. Record the result in a
      host-tagged checkpoint report. F6 does not start until this checkpoint passes.
  - **F6 — activation and RTM flip.** The user-visible switch, after the F5 checkpoint passes.
    - `AppDetectionService` injects `LockEngineRuntime`, forwards the raw package and `onScreenOff`, and submits the
      detector health fact on connect and destroy.
    - `AuthGateViewModel` routes `onUnlockSuccess`/`onUnlockFailure` to the runtime self-gate (no
      `SelfGateAuthPort`), keeps `lockoutState()` live, and drops the `ApplicationLockEngine` dependency. Overlay and
      biometric-host completions stay keyed by the request token (read from the observed `EngineState`). The
      self-gate stays keyed by package and is never forced through a request-id overload.
    - The self-gate PIN callback (`MainActivity.kt`, about line 225) becomes asynchronous. A coroutine awaits the F2
      suspend mutation. An in-flight guard prevents a duplicate submission while one is pending, and a
      submission-keyed result prevents a stale unlock from a superseded submission. If F2 hardening selects Option
      A, this work is done there, and F6 reuses it.
    - `MainActivity.onResume` reports the APP_LOCK arrival signal (the F1 seam; the launch intent carries the
      attempt token) and reads the derived `EnforcementHealth.state` for the banner.
    - Health readers: `ProtectionWatchdogService` and the `MainActivity` banner read the derived health.
      "Protected" requires the detector enabled, the overlay granted, and no present failure. A
      `SurfaceApplyResult.Unavailable` or `Failed` is shown, never swallowed.
    - Grant UI: set `OverlayEnforcement.uiEnabled = true` (the cards in `MainActivity`, about line 342, and
      `SettingsScreen`, about line 178). **Protection is gated on the overlay grant (D-P2-2).** Without
      `canDrawOverlays`, protection is reported inactive (present returns `Unavailable`) and the grant UI is
      actionable. After the grant, the monitor's edge fires `retryPresentation()` and protection recovers. Without
      this gate, ungranted users would lose locking, a regression against the Activity, which needs no grant.
    - `OverlayGrantMonitor` callers: startup (`AppLockApplication.onCreate` seeds the fact), `MainActivity.onResume`
      (a grant made in Settings while the app was in the background), and the `ProtectionWatchdogService` tick. All
      go through the single serialized `refresh()`.
    - The legacy path stays in the tree, unwired (deleted in G). A replacement smoke over the overlay and
      biometric-host surface replaces `LockScreenLaunchTest`. Update the WP8 runbook and keep the GMD matrix green.
    - Decide **R-008**: accept it, or add scheduled revalidation.
    - RTM (same commit): **FR-027** `partial`->`implemented`, **FR-028** `not-started`->`implemented`;
      `implemented-verified` at WP6. Check `rtm.csv` for any `implemented-verified` row that this step touches, and
      cite its regression suite. The changelog records the lead's Gate-2 acceptance of 2026-09-18.
  - **Fleet close-out (NucBox, Moto G).** After F6 is pushed:
    - **Deployed-secure FLAG_SECURE proof** (deferred from change E's Gate 2, Decision 2026-09-14). On a
      `prodRelease` build the real overlay shows over a protected app. `screencap` returns black over the overlay
      region, and `dumpsys window` shows `FLAG_SECURE` on the `AppLockOverlay` window. `OverlayWindowFlagsTest`
      covers the flag policy until then.
    - The replacement smoke on a device.
    - Ungranted-upgrade acceptance: start ungranted (protection inactive, banner actionable), grant "display over
      other apps", and confirm that the monitor recovers protection and a real app then locks through the overlay.
    - End-to-end launcher check: after a default-launcher change, a protected former launcher locks, and a new
      default launcher is not shielded.
    - Re-validation of the WP2 acceptance below: the overlay removes `ABSENT`, no Moto G regression, biometric
      unlock from the overlay, and the OV-3 relock, the Phase 3 F3 self-gate check (the Phase 3 self-gate
      resume-bypass finding, not change F3), and smoke_core green.
    - One host-tagged campaign report per host.
- **G — retire the old path (this box).** Delete `LockScreenActivity.kt` + its manifest `<activity>` +
  `ApplicationLockEngine.kt`. **Remove** (not reshape) the `service/ApplicationLockEngine.kt -> presentation`
  R2 baseline row (`ArchitectureRulesTest.kt`); the `IntruderCaptureManager -> presentation` row stays. Do
  this only after F's replacement smoke is green. Gate: R2 + full suite green. (The `platform.spike` package
  + its manifest block + the OV-4 UIAutomator test are deleted in **Phase 3** with the harness repoint, not
  here.)

**Phase 2 decisions (resolved 2026-09-07).**
- **D-P2-1 lockout projection = option A.** The overlay reads `LockoutManager.currentState()` directly for
  its countdown (as `LockScreenActivity` does today); `EngineState.lockout` is the overlay path's **per-streak
  projection**, not global lockout truth (self-gate writes bypass the reducer). Its KDoc states this and the
  Phase-3 oracle labels it accordingly, never as the global counter. Both writers are safe (`LockoutManager`
  mutators are `@Synchronized`). Option B (a `LockoutObserved` reducer event so the overlay renders purely
  from state) stays available for WP6.
- **D-P2-2 overlay-ungranted = gate protection.** Protection is not reportable / enabled without the grant;
  no residual Activity fallback (it would contradict G's deletion).
- **D-P2-3 Home resolution = in scope**, via `resolveActivity(MAIN/HOME)` with re-resolution on launcher
  change (per D). Load-bearing per `enterRecoveryIfNeeded`.

**Phase 2 gates (normative).** Gate 1 (pure state-machine tests before any WindowManager integration) is
already met by Phase 1 (140 tests). Gate 2 (a real-surface test per surface) = E. Gate 3 (replacement smoke
before `LockScreenActivity` is deleted) = F before G. Gate 4 (oracle / harness evidence) is the **final WP2
gate in Phase 3**, not Phase 2.

**Dependencies.** WP0 (ADR-020), WP1 (harness).
**Outputs.** `LockPresenter`/`OverlayLockPresenter`, `BiometricHostActivity`, `LockEngineRuntime` +
request-identity engine change (self-gate implemented in the runtime), the `EnforcementHealth` owner,
`TimerScheduler`, `RuntimeDiagnostics`, the reusable `LockScreen` composable; DI wiring (`AppModule`).
**RTM (this WP's commit):** **FR-027** Lock Screen Display (`partial`→`implemented`) and **FR-028**
Overlay Security (`not-started`→`implemented`) — the overlay presentation lands here;
`implemented-verified` at the WP6 matrix. Request-identity is the R-002 remediation *by construction*
(risk-register note; evidenced at WP6). The **R-005** readiness *model* also lands here (register note;
detector-bootstrap + cold-start verification in WP3; Closed at WP6).
**Acceptance.** With accessibility still the detector, **per the canonical R-002 standard + §11
protocol**: the emulator A/B shows the overlay eliminates `ABSENT`, the Moto G shows no-regression
(this WP *re-validates* the remediation — it is **not** "closed on real hardware"); biometric unlock
works from the overlay; OV-3 relock, F3 self-gate, smoke_core green; JVM + instrumentation tests for
request-identity pass — **supersession, stale-result rejection, biometric cancel, rotation/recreation,
and process death** each leave the correct single request state (R7). Plus the **R-005 readiness suite,
engine-level (not only the mapping matrix)**: a withheld first emission holds any unclassifiable
foreground behind the "checking" shield (never *allow*); `Loading → Ready` re-evaluates the current
foreground; first-emission-empty and error-before/after-`Ready` behave per §2.3; **coroutine cancellation
is not `Failed`**; `T_ready` expiry gives `Checking → Recovery` (never reveals the target); stale
timers/results are rejected; and a shield actually blocks touches to the task beneath.
**Risks / implications.** Compose-in-overlay lifecycle plumbing is the fiddliest code in M7 (owner
wiring, `WindowManager` add/remove ordering, back-key handling; Home cannot be intercepted and
dismisses to launcher — acceptable per SDS §8.5). This WP re-validates R-002; a real-hardware OV-4
pass here is the primary closure evidence.

### WP3 — Detection swap: UsageStats poll + readiness verification *(keep the overlay; R-005)*
**Purpose.** Swap the *detection* mechanism to the Usage Access poll feeding the same engine seam, and
add the **detector-bootstrap** readiness input to the model **built in WP2**, then verify it so
cold-start/pre-load events cannot fail open (R-005).
**Tasks.**
- Introduce `ForegroundDetectionSource` port (emits normalized `current package + observation time`);
  implement `UsageAccessDetector` **exactly per the §2.4 detection contract** (query window + overlap,
  `(package,timestamp)` dedup cursor, freshness `F`, wall-clock-jump guard, per-API event selection,
  `isUserUnlocked`/keyguard gating, process-restart bootstrap) at the WP0-chosen interval (ADR-021).
- Host the detector in a foreground service (D2: repurpose `ProtectionWatchdogService` into the poll
  host, or a new `ProtectionDetectionService`). **Decision #2 leans D2 toward *repurpose*** — the state
  oracle's transport is `ProtectionWatchdogService.dump()`, so a new service moves the harness target; the
  `ContentProvider` escape is unsafe for cold-start/process-death observations (it revives the process),
  so repurpose is strongly preferred. Confirm D2 here at WP3 start. Lifecycle per SDS §15.2 (start only
  when PIN set +
  ≥1 protected app + capabilities present; stop when none selected; stop querying + report *Action
  required* if Usage Access revoked); bounded retry + backoff, no tight loop (SDS §15.3).
- **Poll + draw off the main thread (WP0 swGPU finding).** The spike polled/drew on the main thread, so
  under load the poll tick or first-draw slipped past `T_appear` (the slow-rig ABSENT). Run the detector
  on a `HandlerThread`, posting only the draw to main; with the WP2 warm overlay this held **0/150
  ABSENT (TOP 150/150)** even at 4× CPU load, one first-draw at 5800 ms still passing TOP (presence +
  focus decouples from the pixel draw). Evidence: `2026-08-28_m7-wp0-emulator_nucbox-g5.md` Item 1 /
  Follow-up 3.
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
- **Fail-secure readiness (R-005) — detector-bootstrap input + verification (the model landed in WP2).**
  The `PolicyState` machinery, engine-owned `ReadinessContext`, §2.3 shields, and `T_ready` were built in
  WP2. WP3 adds the **detector-bootstrap** as the second `loading` input (`DetectorState`: no lock before
  the first confirmed post-start observation, so no retroactive lock), and lands the deterministic
  **cold-start / process-restart** unit + instrumentation tests on the real poll engine (a protected app
  opened in the pre-load window is held, never allowed).
- NFR-PERF-012 instrumentation: measure enforcement response (foreground result → presentation begin,
  ≤250 ms) and end-to-end (transition → overlay), **reported p50/p95/p99 per the §11 protocol** and
  recorded under NFR-PERF-015.
**Dependencies.** WP2 (overlay presentation must exist so the poll drives a real lock), WP0 (ADR-021).
**Outputs.** `ForegroundDetectionSource`/`UsageAccessDetector`, the poll foreground service, the
detector-bootstrap readiness input + cold-start/process-restart tests (the readiness *model* is WP2),
benchmark harness.
**RTM (this WP's commit):** **FR-026** Foreground Application Detection (`not-started`→`implemented`;
Usage Access baseline replaces the a11y detector — its M1 burndown note is resolved); **NFR-PERF-012**
(`not-started`→`partial`; figures recorded, `implemented-verified` at the WP6 matrix). **R-005**
readiness *model* landed in WP2; WP3 adds the detector-bootstrap input and the cold-start verification
(register note; Closed at WP6 on the cold-start tests).
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
- Health readiness (full rewrite; WP2 already made the watchdog minimal-correct against `PolicyState`,
  standing down only on PIN-unset or `Ready(empty)`): the watchdog treats non-`ready` policy state and
  unverified detector/presentation capability as *Unknown/not verified* or *Protection interrupted*,
  never as "nothing to protect" (R-005 watchdog half, `:87-90`).
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
| Cold-start / process-restart fail-open (R-005) | Readiness model built in WP2 (`loading/ready/failed`, non-ready ⇒ hold); WP3 adds the detector-bootstrap input + deterministic cold-start tests; watchdog readiness in WP4 |
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
- **D1 — Poll interval — RESOLVED 2026-08-30: P = 200 ms.** The Moto G n=100/interval sweep
  (`docs/reports/campaigns/2026-08-26_m7-wp0-spike_moto-g-2025.md`) shows `p95 ≈ P + ~40 ms`; **200 ms is
  the largest interval that keeps end-to-end p95 < 250 ms** (226 ms measured), so 225 ms (≈265 ms p95)
  was rejected as too high. Bounded, with backoff on repeated query failure; battery stays frugal (the
  loop pauses on screen-off). The value's durable SSOT is the WP3 detector constant + NFR-PERF-012
  (§2.7); recorded here as the WP0 resolution. → **ADR-021 (Accepted 2026-08-30).**
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
