# Risk Register

**The single authoritative record of tracked project risks** (GOVERNANCE.md §5.2,
2026-08-10): gate reviews take the risk picture from here; risk statements elsewhere are
inputs or history. The `MIGRATION_ASSESSMENT.md` Phase 11 table is a frozen 2026-07-19
snapshot — its still-live items are tracked here (R-003, R-004). TAS §67 is the risk
*architecture* spec. Each risk has a stable `R-NNN` id; entries are updated in place as
status changes, and closed risks are marked `Closed` with the resolving reference rather
than deleted. Severity = Likelihood × Impact (Low / Medium / High / Critical, consistent
with TM §14). **Affected gate(s)** names the gate(s) a risk can block: per TM §14.10 a High
risk blocks its affected security gate absent remediation or an explicitly approved
compensating treatment.

| ID | Risk | Severity | Affected gate(s) | Status |
|----|------|----------|------------------|--------|
| [R-001](#r-001) | Accessibility-based detection: platform lockdown, Play policy, silent failure | **High** | M2 · M6 | Open |
| [R-002](#r-002) | Lock-engine rapid-relaunch window-ordering race (protected app outruns the lock screen) | **High** (pending real-hardware) | M2 | Open |
| [R-003](#r-003) | Schedule/capacity: enterprise-scale baseline vs solo-developer cadence | **High** | all (pacing) | Open |
| [R-004](#r-004) | `fallbackToDestructiveMigration` — silent data-loss trap on schema mismatch | **High** | M1 (WP7) | Open |

---

## R-001 — Accessibility-based foreground detection: platform lockdown, Play policy, and silent failure

**Category:** Architecture / Compliance / Security · **Likelihood:** High · **Impact:** High–Critical
· **Severity:** High · **Status:** Open · **Opened:** 2026-07-23 · **Owner:** project lead
**Affected gate(s):** M2 — IS Phase-1 gate (two-tier baseline per ADR-013A is the remediation)
· M6 — Play-compliance review (R-001b verification, NFR-COMPY-002/003)
**Related:** ADR-013 → superseded by **ADR-013A** · FR-179, FR-231, FR-233, FR-242, FR-253 ·
NFR-PERF-012 · NFR-COMPY-002/003, NFR-COMP-001
· evidence: `docs/reports/campaigns/2026-07-23_wp2-regression_moto-g-2025.md`

**Update 2026-08-04 — mitigating direction set by ADR-013A (two-tier detection).** A product
decision requires the app to function without accessibility for Play compliance:
UsageStatsManager + overlay becomes the Play-compliant **baseline**, accessibility an **optional**
low-latency enhancement. This addresses R-001a/R-001b **as a design matter** and narrows R-001c
to the optional path. The risk remains **Open** at **High** because the shipping build is still
accessibility-only — the baseline is not built until M2, so the exposure persists until then.
A new consideration also opens: the baseline depends on Usage Access + overlay, each with its own
permission-loss/health story (extends FR-179).

### Description
The app detects protected-app launches via `AccessibilityService` (ADR-013). Real-hardware
testing on a Moto G 2025 (Android 15) surfaced three compounding sub-risks in this approach:

- **R-001a — Restricted Settings (sideloaded installs).** Android 13+ blocks granting
  accessibility (and notification-listener / usage-access / device-admin) through the normal UI
  for apps not installed from a trusted store. On the Moto G 2025 the **"Allow restricted
  settings" escape hatch is removed**, so a sideloaded install cannot enable protection at all on
  such devices. The trend tightens each Android release. **Does not apply to Google Play
  installs.**
- **R-001b — Google Play policy.** Play scrutinises `AccessibilityService` used for
  non-accessibility purposes; app-lockers occupy a grey area and have been rejected/removed.
  A Play rejection would be existential for a production release.
- **R-001c — Silent protection failure.** The service can be *enabled-in-setting but not
  delivering events* (the "malfunctioning" state observed after an adb enable, and the same state
  users can land in). A security app that believes it is protecting the user while it is not is
  the worst failure mode. **Concern to verify:** `AppDetectionService.isEnabled()` / the FR-179
  watchdog appear to check only the *setting*, which reads true in the malfunctioning state — so
  the app likely cannot currently detect its own silent failure.

### Impact
- R-001a: hard adoption blocker for any non-Play distribution, worsening by device/OS version.
- R-001b: cannot ship on Play at all if rejected.
- R-001c: users get a false sense of security; undermines the product's core promise.

### Current mitigations
- **ADR-013A (2026-08-04)** removes the hard accessibility dependency by design (two-tier
  detection) — **pending M2 implementation**; does not change the current build.
- In-app onboarding deep-links to Accessibility settings (correct UX for Play installs).
- FR-179 watchdog alerts on accessibility *revocation* — but does not distinguish the
  malfunctioning-but-enabled state (the R-001c gap).

### Planned actions
1. **Decide the distribution model** (Play-only vs sideload-supported) — this sets the severity of
   R-001a and must be an explicit, recorded decision (candidate ADR).
2. **M2 — build the two-tier detection model per ADR-013A** (the evaluation is now decided): the
   UsageStatsManager + overlay Play-compliant baseline plus optional-accessibility onboarding, and
   the open overlay-vs-background-activity-launch presentation choice; feed the Threat Model v1.
   ADR-013A supersedes ADR-013.
3. **Accessibility-health self-test** — verify events are *actually delivered* (a window-event
   heartbeat), not merely that the setting is on; surface a truthful protection-status to the user.
   Implements FR-231 (startup health check), FR-242 (runtime self-test), FR-253 (a11y recovery);
   closes R-001c.
4. **M6 — Google Play compliance review** (NFR-COMPY-002/003) before release.

### Review triggers
Android major-version bumps (targetSdk changes), any change to detection/ADR-013, Play policy
updates, and the M2 and M6 gates.

---

## R-002 — Lock-engine rapid-relaunch window-ordering race

**Category:** Security / Enforcement · **Likelihood:** Medium (pending real-hardware) · **Impact:** High
· **Severity:** High · **Status:** Open · **Opened:** 2026-08-06 · **Owner:** project lead
**Affected gate(s):** M2 — IS Phase-1 gate (the overlay-vs-activity presentation decision, ADR-013A/AS-020,
is the remediation path; TM §14.10 applies). Not an M1 gate item — WP5/WP6 regression gates only
require the race to be *unchanged*, which the 2026-08-09 post-Hilt matrix confirmed.
**Related:** TM THR-ENF-004 (enforcement race during application switching), HF-002 (fast-relaunch bypass — remediation shown incomplete), HF-003, §9.15 control, §12.41 residual register (fail-open enforcement) · ADR-013A / AS-020 (baseline lock-interface presentation) · `ApplicationLockEngine.kt:61` + `:123`
· evidence: `docs/reports/campaigns/2026-07-23_wp2-matrix_nucbox-g5.md`

### Description
The WP2 emulator-matrix campaign (NucBox, API 26/29/33/35) found the F4 rapid-relaunch defense
(OV-4) fails intermittently on **every** emulator level — **12–37 % in focused loops**, not graded
by API (API 33 ~37 % > API 29 ~12 %). **This is not a detection gap:** logcat shows the engine
receives an accessibility event for every relaunch and launches the lock screen each time
(5–8 `LockDecision(requiresAuthentication=true)` per burst on API 26). The failure is a
**window-ordering race** — the rapidly-relaunched protected-app window slides over the `noHistory`
`LockScreenActivity`, which self-finishes, leaving the **protected app foreground with no lock
screen for the full 6–12 s poll window**. This is the exact race the Phase-3 mitigation anticipated
(`ApplicationLockEngine.kt:61`) and tried to counter via re-launch-on-every-event
(`FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP`, `:123`) — the mitigation does **not deterministically win**
the race on emulators. The single API-35 `smoke_core` failure is the same race on the single-relaunch
path, not a wrong-PIN acceptance.

This supersedes the earlier belief that HF-002 (fast-relaunch bypass) was fully closed in Phase 3:
the fix reduced but did not eliminate the race; it is **probabilistic, not deterministic**.

### Impact
A direct **authorization-bypass window** — a protected application is usable without App Lock
authentication for 6–12 s after a rapid-relaunch burst, defeating the primary enforcement guarantee
(SO-03 / INV-001) for that window. Attack complexity is low: a physical attacker on an unlocked
device (TA-ATK-001) simply relaunches the app repeatedly, and can retry freely until the race is won.

### Severity note (why "pending real-hardware")
Reproducible on **every emulator level** on this low-power software-GPU host, but the one real device
tested (Moto G 2025, API 35, fast arm64) was **clean**. Whether a **slower or older real device**
reproduces it is unknown — so this is a confirmed emulator race whose **production severity is
unresolved**. Rated **High** precautionarily (an enforcement bypass) pending that check; may settle
to Medium if it proves emulator-timing-only, or hold at High if a real slow device reproduces it.

### Current mitigations
- Phase-3 defense: re-lock on every foreground event + re-launch with `NEW_TASK | CLEAR_TOP`
  — reduces but does not deterministically win the race (12–37 % failure on emulator).
- OV-4 harness check exists, **but the `run_all -n 2` gate is unreliable for this property**: API 29
  and API 33 both passed `-n 2` while focused loops found real bypasses on both. A green 2/2 does
  not prove the F4 defense holds — the gate under-samples a probabilistic race.

### Planned actions
1. **Real-hardware validation (decisive):** re-run OV-4 with many bursts on a **slower/older real
   device** to settle whether this is emulator-timing-only or a production exposure. Sets final severity.
2. **App fix — deterministic lock presentation.** The re-launch-on-event Activity approach loses a
   window race by construction (a `noHistory` Activity can be slid over). The **M2 two-tier baseline
   presentation (AS-020) is directly relevant**: a drawn overlay (`SYSTEM_ALERT_WINDOW`) sits on top
   rather than as a slide-over-able Activity and should resist this race — so the open
   overlay-vs-activity-launch presentation decision (ADR-013A) SHALL weigh R-002. Fixing this may
   fall out of the M2 presentation work rather than a standalone patch.
3. **Harness:** raise OV-4 burst count / add a repeat so a green result is meaningful (WP2/WP8).
4. **Threat Model linkage:** cite R-002 from THR-ENF-004, HF-002 (annotate remediation incomplete),
   and the §12.41 residual register at the next TM revision.

### Review triggers
Any lock-engine or lock-screen-presentation change; the M2 presentation-mechanism decision
(overlay vs activity-launch); real-hardware OV-4 results; WP2/WP8 harness changes; the M2 and M3 gates.

---

## R-003 — Schedule/capacity: enterprise-scale baseline vs solo-developer cadence

**Category:** Programme · **Likelihood:** High · **Impact:** Medium–High
· **Severity:** High · **Status:** Open · **Opened:** 2026-08-10 (promoted; risk first
recorded 2026-07-19) · **Owner:** project lead
**Affected gate(s):** all — pacing risk; formally reviewed at every gate (IS §5 weighs
project risks). Not a blocker of any single gate.
**Provenance:** `MIGRATION_ASSESSMENT.md` Phase 11 "Schedule risk" (High, 2026-07-19
snapshot), promoted per GOVERNANCE.md §5.2.

### Description
The re-baselined programme (375 FRs, 171 NFRs, 7 gated IS phases, companion-document
obligations) is enterprise-scale work against a solo-developer cadence with constrained
hardware (2012 dev box; low-power NucBox emulator host). The Phase 12 estimate was
~20–28 solo-dev weeks total, M1 at 3–4 wk.

**Tracking (2026-08-10):** M0 landed on estimate (~1 wk). M1 has consumed ~3 weeks
(WP1 2026-07-20 → WP5 device-gate close 2026-08-09) for 5 of 8 work packages — the 3–4 wk
M1 estimate will overrun. Mitigating context: that period also absorbed unplanned scope
(Threat Model v2 arrival + reconciliation, TSP arrival + gap analysis, DDS arrival,
R-002 discovery/analysis).

### Impact
Gate slippage across M2–M6; the dangerous failure mode is schedule pressure eroding
verification discipline (skipped regression runs, thin gate records) — which converts a
programme risk into a security risk.

### Current mitigations
- Strict WP sequencing with hard gates (nothing after WP2 proceeds while the harness is red).
- Build-once verification assets are landing early (CI, device harness, Konsist, matrix
  runbooks) and amortize across all later phases.
- "(or equivalent)" latitude in the TAS baseline is being used deliberately (ADR-012, ADR-016).

### Planned actions
1. Re-estimate M1-remaining and M2–M6 at the M1 (WP8) gate record; flex scope or timeline
   explicitly rather than silently.
2. Keep feature-visible progress (M3/M4) sequenced mid-programme per the Phase 12 rationale.

### Review triggers
Every phase-gate record; arrival of remaining client documents (TSP V-II remainder..V-VI);
any material scope addition (new spec revisions, new mandated deliverables).

---

## R-004 — `fallbackToDestructiveMigration`: silent data-loss trap on schema mismatch

**Category:** Reliability / Data integrity · **Likelihood:** Medium · **Impact:** High
· **Severity:** High · **Status:** Open — remediation scheduled (M1/WP7) · **Opened:**
2026-08-10 (promoted; risk first recorded 2026-07-19) · **Owner:** project lead
**Affected gate(s):** M1 — WP7 is the in-phase remediation; the IS Phase-0 (M1) gate record
(WP8) verifies closure. Exposure compounds if carried into M2/M3 schema work.
**Provenance:** `MIGRATION_ASSESSMENT.md` Phase 11 "Migration risk" (Medium, 2026-07-19
snapshot), promoted per GOVERNANCE.md §5.2 with Impact re-rated High (vault/security data).
**Related:** FR-228, FR-229 · ADR-007, ADR-012 · `AppLockDatabase.kt` (builder retains
`fallbackToDestructiveMigration()`) · DDS Vol IV (operations/lifecycle) · TS_GAP_ANALYSIS
G-05 (deliberate-failure dataset, WP7 seed).

### Description
`AppLockDatabase.build` still chains `.fallbackToDestructiveMigration()`: any schema
version reached without a matching hand-written migration silently **wipes the encrypted
database** — protected-app configuration, security-event/intruder rows, and the vault
index (leaving vault blobs orphaned on disk). Data-bearing installs exist, and the WP4
`dev`/`qa`/`staging` flavors multiply the upgrade paths that can hit a missed migration.
Upcoming schema work (M2 security platform, M3 backup/config) raises the trigger
likelihood while it remains.

### Impact
Silent destruction of user security data on upgrade — contradicts FR-228 (governed
migrations) and FR-229 (integrity verification), and is the exact failure class the DDS
lifecycle volume prohibits. Worst case: a user loses their vault index and protection
configuration with no error surfaced.

### Current mitigations
- All schema changes to date ship hand-written migrations (`MIGRATION_1_2` precedent);
  the fallback has never fired in a validated flow.
- The B-1-style upgrade test (data survives version bump) is part of campaign practice.

### Planned actions
1. **M1/WP7:** remove the fallback; replace with a fail-safe policy (refuse-to-open +
   surface recovery guidance rather than destroy), plus the FR-229 integrity check.
2. WP7 deliberate-failure drill: corrupted/mismatched-schema dataset proving the fail-safe
   path (feeds TS_GAP G-05 and the WP8 instrumentation seed).
3. Close this risk on the WP7 campaign evidence; cite it here and in the RTM rows
   (FR-228/229) in the same change.

### Review triggers
Any Room schema/version change; WP7 execution; M3 backup/restore design; any new build
flavor that adds an upgrade path.
