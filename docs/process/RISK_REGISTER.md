# Risk Register

**The single authoritative record of tracked project risks** (GOVERNANCE.md §5.2,
2026-08-10): gate reviews take the risk picture from here; risk statements elsewhere are
inputs or history. The `MIGRATION_ASSESSMENT.md` Phase 11 table is a frozen 2026-07-19
snapshot — its still-live items are tracked here (R-003, R-004). Independent-review findings are
triaged here too (R-005/R-006 from the 2026-08-11 code review; see the pending-triage section).
TAS §67 is the risk *architecture* spec. Each risk has a stable `R-NNN` id; entries are updated in place as
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
| [R-005](#r-005) | Cold-start policy fail-open: protection cache empty until async load completes | **High** | M2 | Open |
| [R-006](#r-006) | Non-atomic legacy plaintext→encrypted migration: rollback source deleted before import commits | **Medium** | M1/WP7 | Open |

**Defects — not tracked as risks** (2026-08-11 review, decision 2026-08-11): CR-005 biometric failure
accounting (Major, M2) · CR-006 orphaned vault/intruder blob on delete (Major, M3/M5). Held pending a
defect-record convention (owed — ROADMAP M1 gate / TS_GAP G-04). See the defects section below.

## Severity scoring — Likelihood × Impact

Severity = the cell below (adopted 2026-08-11), using the qualitative Likelihood and Impact
definitions in TM §14. Standard qualitative matrix; a rating that departs from it is an explicit,
documented override (none at present).

| Likelihood ↓ \ Impact → | Low | Medium | High | Critical |
|---|---|---|---|---|
| **Critical** | Medium | High | Critical | Critical |
| **High** | Medium | High | High | Critical |
| **Medium** | Low | Medium | High | High |
| **Low** | Low | Low | **Medium** | High |

An entry whose Likelihood or Impact is a range takes the lead's assessed point value. Entries that
predate this matrix (R-001…R-005) keep their recorded severity and are reconciled to it **when next
touched** (GOVERNANCE §6), not retroactively — each already lands on its recorded value except where
a pre-existing impact *range* is involved. Worked examples: R-002 (Medium × High = High), R-005
(Medium × High = High), R-006 (Low × High = Medium).

---

## R-001 — Accessibility-based foreground detection: platform lockdown, Play policy, and silent failure

**Category:** Architecture / Compliance / Security · **Likelihood:** High · **Impact:** High–Critical
· **Severity:** High · **Status:** Open · **Opened:** 2026-07-23 · **Owner:** project lead
**Affected gate(s):** M2 — IS Phase-1 gate (two-tier baseline per ADR-013A is the remediation)
· M6 — Play-compliance review (R-001b verification, NFR-COMPY-002/003)
**Related:** ADR-013 → **ADR-013A** → **ADR-013B** (2026-08-14) · FR-179, FR-231, FR-233, FR-242, FR-253 ·
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

**Update 2026-08-14 — ADR-013B defers accessibility out of the 1.0.0 release.** The year-end 1.0.0
scope decision (ADR-013B, superseding ADR-013A) removes the accessibility tier from the release
entirely: 1.0.0 ships the Usage Access + overlay baseline **only**, with the optional
AccessibilityService deferred to 2.0.0. Consequently **R-001a and R-001b no longer apply to 1.0.0**
— there is no accessibility service in the release for Restricted Settings to block or Play to
scrutinise; both move with the tier to the 2.0.0 Play-compliance review. The residual 1.0.0
exposure is the baseline's own two grants — Usage Access (itself Restricted-Settings-gated for
sideloaded Android 13+) and the now-mandatory overlay — and the fact that the shipping build stays
accessibility-only until the M2 baseline lands. Stays **Open** pending that build and a full
re-scope of this entry (likelihood/impact re-rating) at its next touch. RTM FR-043/FR-045/FR-253 →
`descoped-v1` in the same change.

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
**Related:** TM THR-ENF-004 (enforcement race during application switching), HF-002 (fast-relaunch bypass — remediation shown incomplete), HF-003, §9.15 control, §12.41 residual register (fail-open enforcement) · ADR-013A → **ADR-013B** / AS-020 (baseline lock-interface presentation) · `ApplicationLockEngine.kt:61` + `:123`
· evidence: `docs/reports/campaigns/2026-07-23_wp2-matrix_nucbox-g5.md`

**Update 2026-08-14 — the overlay remediation is now committed 1.0.0 scope (ADR-013B).** ADR-013B
makes the drawn `SYSTEM_ALERT_WINDOW` overlay the **mandatory, sole** lock-presentation mechanism
for 1.0.0 (accessibility deferred to 2.0.0). This converts planned action #2 — the
overlay-vs-activity-launch presentation choice — from an open M2 decision into a **decided**
remediation path for R-002: a drawn overlay sits on top of the foreground task rather than as a
slide-over-able `noHistory` Activity that loses the window race by construction. The race is not
closed until the baseline is built and re-validated on hardware (planned action #1), so the risk
stays **Open**.

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

---

## R-005 — Cold-start policy fail-open (protection cache empty until async load)

**Category:** Security / Enforcement / Initialization · **Likelihood:** Medium · **Impact:** High
· **Severity:** **High** (Medium × High per the scoring rubric) · **Status:** Open
· **Opened:** 2026-08-11 · **Owner:** project lead
**Affected gate(s):** M2 — IS Phase-1 security gate (fail-secure initialization is core-security
scope). Not an M1 item (no review-driven M1 logic; review §5.5).
**Provenance:** 2026-08-11 code review **CR-002** (Critical defect). NEW risk — not previously tracked.
**Related:** FR-001, FR-017, FR-179 · `LockPolicyManager.kt:21-40` (empty-set init + async fill;
absence read as "not protected", no loading/failed state) · `ApplicationLockEngine.kt:54-57`
(synchronous consumer) · `ProtectionWatchdogService.kt:85-90` (same empty cache read as
"protection unnecessary").

### Description
`LockPolicyManager` seeds its protected-package cache as `emptySet()` and fills it asynchronously
(`startCaching` collecting a Room Flow). `evaluate()` treats absence from the set as **not
protected**, with no "loading" or "failed" state. A window-state event arriving before the first
cache emission — cold start, process restart, first event after boot — therefore resolves to an
**allow** decision. The watchdog can read the same empty cache as evidence that protection is
unnecessary, so protection-health monitoring can also stand down before readiness is established.

### Impact
A protected application launched in the pre-load window is foreground without a valid authorization
session — a fail-**open** on the primary enforcement boundary. Comparable in kind to R-002 (a
bypass window) but rooted in initialization rather than a presentation race.

### Current mitigations
- App startup warms the cache early (`startCaching` at application init), shortening the window.
- No fail-secure gate exists for the pre-load state — that absence is the risk.

### Planned actions (M2 — no M1 logic per the review)
1. Model explicit **loading / ready / failed** policy states; treat unknown readiness as
   **fail-secure** (block or hold locked until the first snapshot is confirmed).
2. Deterministic cold-start / process-restart tests + watchdog readiness/restart tests.
3. On implementation, reassess FR-001/FR-017/FR-179 RTM rows with evidence (no promotion without it).

### Review triggers
The M2 core-security work; any change to policy-cache initialization or watchdog readiness logic;
the M1 gate record (as an open-risk input, not an M1 blocker).

---

## R-006 — Non-atomic legacy plaintext→encrypted migration (rollback source removed before commit)

**Category:** Reliability / Data integrity / Security-policy preservation · **Likelihood:** Low
· **Impact:** High · **Severity:** **Medium** · **Status:** Open · **Opened:** 2026-08-11
· **Owner:** project lead
**Affected gate(s):** **M1/WP7** — folded into the fail-safe migration work alongside R-004 (same
`AppLockDatabase` file, same class of fix, one deliberate-failure drill), confirmed 2026-08-11.
Distinct from R-004 (schema-mismatch fallback vs import atomicity).
**Severity note:** **Medium** = Low likelihood × High impact under the scoring rubric (top of
register). Impact is high (permanent security-data loss), but the failure fires only on an
interrupted one-time legacy conversion — a narrow window, Phase-1 plaintext upgraders only — so
likelihood is Low. Remediated in WP7 regardless of severity.
**Provenance:** 2026-08-11 code review **CR-003** (Critical defect). NEW risk.
**Related:** FR-163, FR-164, FR-228, FR-262, FR-372 · `AppLockDatabase.kt:107-144`
(`snapshotAndRemovePlaintext` reads legacy rows into memory, then `check(dbFile.delete())`) ·
`:90-99` (encrypted DB opened + `importLegacyRows` **after** source removal).

### Description
The one-time Phase-1 plaintext→SQLCipher migration reads legacy rows into an **in-memory** snapshot,
deletes the plaintext source, then opens the encrypted DB and imports. Between the source delete and
a committed import there is no durable copy: process death, encrypted-open failure, storage failure,
or an import exception permanently loses the protected-app policy and security/intruder event
history. Loss of policy makes previously protected apps appear unprotected on next start.

### Impact
Permanent loss of security-policy + audit data on an interrupted migration; silent de-protection.
**High impact, low likelihood** (fires only for installs upgrading from a Phase-1 plaintext DB, in a
short window). Distinct from R-004, which is the destructive **schema-mismatch** fallback in the same
builder — this is the plaintext-import atomicity gap.

### Current mitigations
- The migration runs once and has completed cleanly in validated flows; the plaintext→encrypted path
  was E2E-exercised in Phase 3.
- No move-before-convert / commit-before-delete ordering exists — that absence is the risk.

### Planned actions (phase per the WP7 scope decision)
1. **Decide scope:** fold into M1/WP7's fail-safe work (alongside R-004) or assign to M2 — and update
   `M1_PLAN.md`, `ROADMAP.md`, this entry, and M1 exit criteria accordingly (living-doc decision; the
   review does not silently expand WP7).
2. Durable backup / move-before-convert; commit + validate before removing the source; idempotent
   restart at each stage; row-count / schema / protected-policy verification.
3. Interrupted-conversion deliberate-failure test (pairs with the R-004 / TS_GAP G-05 drill).

### Review triggers
The WP7 scope decision; any change to `AppLockDatabase` migration; M3 backup/restore design.

---

## Defects — held, not tracked as risks (2026-08-11 code review)

Decision 2026-08-11: these two review findings are **Major defects**, not project risks. They are
recorded here as an interim holding place **until a defect-record convention exists** (owed — see
`ROADMAP.md` M1-gate scope; TS_GAP G-04, "decide at the M1 gate"). Authoritative detail is the review
evidence `docs/reports/reviews/2026-08-11_formal-code-review.md`.

| Defect | Finding | Severity | Affected gate |
|---|---|---|---|
| CR-005 | Biometric non-matches skip product lockout / intruder / audit accounting (`LockScreenActivity.kt:208-212` — not routed to `ApplicationLockEngine.onUnlockFailure`, `:90-103`). Platform biometric lockout + counted PIN fallback are partial compensating controls; the gap is intruder-capture + cross-method audit. | **Major** | M2 (auth controls) |
| CR-006 | Vault/intruder delete removes the index row before (and ignoring) blob deletion → orphaned ciphertext reported as success (`VaultRepository.kt:78-81`; `IntruderLogViewModel.kt:35-40`). Data-lifecycle / secure-delete consistency gap; the blob stays encrypted (no confidentiality breach). | **Major** | M3 (Vault) or M5 (data lifecycle) — lead chooses |

Related requirements: CR-005 → FR-009/010/014/081/174; CR-006 → FR-085/115.
When the defect-record convention lands, migrate these (and future defects) into it and retire this
interim section.
