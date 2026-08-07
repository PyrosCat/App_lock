# Risk Register

Living register of tracked project risks (complements the point-in-time risk snapshot in
`MIGRATION_ASSESSMENT.md` Phase 11; TAS §67 Risk Mitigation Architecture). Each risk has a
stable `R-NNN` id; entries are updated in place as status changes, and closed risks are marked
`Closed` with the resolving reference rather than deleted. Severity = Likelihood × Impact
(Low / Medium / High / Critical).

| ID | Risk | Severity | Status |
|----|------|----------|--------|
| [R-001](#r-001) | Accessibility-based detection: platform lockdown, Play policy, silent failure | **High** | Open |
| [R-002](#r-002) | Lock-engine rapid-relaunch window-ordering race (protected app outruns the lock screen) | **High** (pending real-hardware) | Open |

---

## R-001 — Accessibility-based foreground detection: platform lockdown, Play policy, and silent failure

**Category:** Architecture / Compliance / Security · **Likelihood:** High · **Impact:** High–Critical
· **Severity:** High · **Status:** Open · **Opened:** 2026-07-23 · **Owner:** project lead
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
