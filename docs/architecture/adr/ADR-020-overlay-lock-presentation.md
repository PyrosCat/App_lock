# ADR-020 — Overlay Lock Presentation: Drawn SYSTEM_ALERT_WINDOW Surface, Biometric via Transparent Activity, and the Request-Identity Model

**Status:** Accepted (2026-08-30) · **Date:** 2026-08-25 · **Source/authority:** M7 plan
(`docs/process/M7_PLAN.md`, WP2 + §2.3), drafted 2026-08-25; **Accepted at M7 WP0 (2026-08-30) on the
WP0 platform evidence** (GOVERNANCE §2.2; see Implementation status). · **Implements:** ADR-013B (does
not supersede it).

## Context
ADR-013B fixed the 1.0.0 lock **presentation** as a mandatory drawn overlay
(`SYSTEM_ALERT_WINDOW`), replacing the `noHistory` `LockScreenActivity`, and made the overlay grant a
precondition for a *Protected* health state. It did **not** decide *how* the overlay is drawn, how
biometric authentication (which needs an Activity host) is presented from a non-Activity overlay
window, or how the engine tracks which lock request a completion belongs to. M7 WP2 builds this
surface, and three coupled mechanisms need a recorded decision because each carries a platform
constraint that shapes the code:

- `BiometricPrompt` cannot be hosted by a WindowManager overlay window: it requires a
  `FragmentActivity` host and auto-dismisses when its host is not foreground.
- Two **distinct** platform restrictions bear on this surface and must not be conflated:
  **(a) background-activity-launch (BAL)** — launching the biometric Activity while the app is not
  foreground is gated (Android 10+, tightened in 14/15), and is permitted here because the app owns a
  **visible window** (the drawn lock overlay), with the `SYSTEM_ALERT_WINDOW` grant a further BAL
  allowance; **(b) background foreground-service start** — starting the *detection* FGS from the
  background is a *separate* restriction (Android 12+, tightened in 15) where using
  `SYSTEM_ALERT_WINDOW` as the basis requires an **already-visible overlay** on Android 15+. Case (b)
  is ADR-021's territory; this ADR's biometric launch is case (a), confirmed across API 30/33/35/36 at
  WP0.
- The legacy engine re-launched the lock on every detection event and tracked a single
  `lockScreenTarget` string. That races, mis-attributes completions, and (once the auth surface is
  *our own* Activity) reads "the foreground app is App Lock" as if the protected target changed.

## Decision (proposed)
1. **Overlay draw.** The lock surface is a `TYPE_APPLICATION_OVERLAY`, full-screen, focusable,
   touch-modal WindowManager window (it blocks interaction with the task beneath) hosting the
   existing Compose lock UI via `ComposeView` plus a lightweight
   `ViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`. A **stable window title**
   is set so the WP1 harness and FTL probe can assert on it. The overlay is **modal to the protected
   task, never to the device**: Home/Back reach the launcher (the escape-hatch rule).
2. **Port / adapter seam.** A `LockPresenter` port (`present(request)` / `dismiss(requestId)`,
   returning a present success/failure result) lives in `service/` (inward-only, consumed by the
   engine). The `OverlayLockPresenter` adapter lives in `platform/` (the Android/WindowManager
   boundary, Konsist-R2-exempt, so its dependency on the `presentation/` Compose UI is legal). A
   failed draw flips health to *interrupted*, never a false *Protected*.
3. **Biometric hosting (D3).** Biometric is presented by a transparent `BiometricHostActivity`
   (`FragmentActivity`, in `presentation/`, `exported=false`, `excludeFromRecents`, `taskAffinity=""`,
   `FLAG_SECURE` on non-debug builds) launched from the overlay as a **background-activity-launch
   permitted by the app's visible overlay window** (case (a) in Context; *not* the FGS-start rule),
   returning its result to the engine keyed by `requestId`. The overlay stays behind it; PIN is the
   always-available fallback on biometric cancel/error.
4. **`LockScreenActivity` disposition (D4).** Delete `LockScreenActivity` and its manifest
   `<activity>`; introduce the dedicated `BiometricHostActivity`. (Shrinking `LockScreenActivity` into
   the biometric host is rejected: it carries `noHistory`/launch semantics irrelevant to a transparent
   shim and keeps a misleading name that survives the WP5 grep-clean.)
5. **Request-identity model.** The engine tracks one in-flight
   `LockRequest(targetPackage, requestId)` and **four distinct concepts, never conflated**:
   - **observed foreground package** — the detector's output (a protected app, the launcher, or our
     own `BiometricHostActivity`);
   - **logical protected target** — the protected package the live request guards;
   - **active authentication surface** — our overlay plus biometric host; while it is foreground the
     observed package is our own, which is an *allow* and **must not** supersede or cancel the
     request;
   - **current request id** — the single in-flight request.

   A completion (`onUnlockSuccess`/failure/dismiss, each carrying its `requestId`) is accepted only
   when its `requestId` is current **and** the logical target still holds. It is explicitly **not**
   gated on "observed foreground == target," because launching biometric makes our package the
   observed foreground. Request identity is **in-memory** (DDS §1.3 forbids persisting it);
   recreation-safety spans two cases, **neither using persistence**:
   - **Activity / configuration recreation** (the process survives): the `@Singleton` engine still
     holds the request; the recreated overlay/host re-attaches to it.
   - **Full process death** (the reference is *gone*): recovery does **not** restore the old request.
     The engine re-derives state from the detector's next foreground observation + current policy and
     creates a **fresh** in-memory request, re-presenting only if a protected app is currently
     foreground (consistent with the `loading` bootstrap — no retroactive lock).

   This replaces the ad-hoc `lockScreenTarget` + relaunch-on-every-event logic and is the committed
   R-002 remediation *by construction*.
6. **Compose-in-overlay hosting (D5, implementation note).** `ComposeView` + `ViewTree*Owner` wiring
   (reuse the existing Compose lock UI and theme), not a classic `View`. Recorded for traceability; it
   is an implementation choice, not a binding architectural constraint.

**Binding constraints (1.0.0):** the overlay grant is a precondition for *Protected* (ADR-013B); PIN
fallback is always available; `BiometricHostActivity` is `exported=false`; no request/session/foreground
state is persisted.

## Alternatives considered
- **Present the lock as a BAL-exemption Activity (relaunch an Activity) instead of a drawn window.**
  Rejected: that is essentially the legacy `noHistory` Activity that loses the R-002 rapid-relaunch
  race (ADR-013B consequence). The drawn overlay is the remediation.
- **Keep `LockScreenActivity` as the biometric host (shrink it).** Rejected (D4): stale name and
  launch semantics; a purpose-built transparent host is cleaner and greppable at the WP5 cutover.
- **Drop biometric from the overlay for 1.0.0.** Rejected: SDS §8.5 mandates biometric-when-eligible.
- **Gate completion on observed-foreground == target (legacy behavior).** Rejected: our own biometric
  host going foreground would reject valid completions or mis-supersede the request (the request-identity
  hazard this ADR exists to remove).
- **Persist the active request/session for recreation.** Rejected: recreation is handled in-memory
  (Decision #5).

## Consequences
**Positive:**
- The R-002 remediation is **structural** (one current request, overlay drawn on top), not a timing
  patch.
- The two-port seam (this ADR's `LockPresenter`, ADR-021's `ForegroundDetectionSource`) leaves the
  presentation seam the deferred 2.0.0 accessibility tier reuses.
- The auth surface is not externally launchable (`exported=false`).

**Negative / costs:**
- Compose-in-overlay lifecycle plumbing is the most delicate code in M7: owner wiring, `WindowManager`
  add/remove ordering, back-key handling.
- The transparent-Activity-via-BAL biometric path is a **platform-behavior dependency**: it must be
  proven on API 30/33/35/36 in WP0. If it proves unreliable, escalate; biometric then falls back to
  PIN, but the path must work when biometric is eligible.
- Home cannot be intercepted and dismisses to the launcher (accepted).

## Related requirements
FR-027 (Lock Screen Display) · FR-028 (Overlay Security) · FR-044 (Overlay Permission Verification) ·
FR-171 (`FLAG_SECURE` auth screen) · FR-179 (health = Usage Access + overlay). Risks: **R-002**
(remediated by construction), **R-005** (a failed present is *interrupted*, never a false *Protected*).
Related ADRs: **implements ADR-013B**; pairs with **ADR-021** (detection).

## Implementation status
**Accepted 2026-08-30 (WP0); not yet implemented.** The WP0 platform evidence is in: the overlay wins
the rapid-relaunch race (decisive emulator A/B + the multi-OEM FTL sweep + Moto G no-regression — the
WP0 R-002 campaign reports in `docs/reports/campaigns/`), and the transparent-Activity biometric host
launches via BAL across API 30/33/35/36 (`2026-08-30_m7-wp0-biometric-matrix_nucbox-g5.md` + the Moto G
real-sensor pass). D3 (transparent host) and D4 (delete `LockScreenActivity`) are ratified. WP2
implements the presentation swap; WP6 verifies on the §10 matrix.
