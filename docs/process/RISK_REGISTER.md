# Risk Register

Living register of tracked project risks (complements the point-in-time risk snapshot in
`MIGRATION_ASSESSMENT.md` Phase 11; TAS §67 Risk Mitigation Architecture). Each risk has a
stable `R-NNN` id; entries are updated in place as status changes, and closed risks are marked
`Closed` with the resolving reference rather than deleted. Severity = Likelihood × Impact
(Low / Medium / High / Critical).

| ID | Risk | Severity | Status |
|----|------|----------|--------|
| [R-001](#r-001) | Accessibility-based detection: platform lockdown, Play policy, silent failure | **High** | Open |

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
