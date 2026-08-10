# R-002 Rapid-Relaunch Lock Race — Root-Cause Analysis

**Date:** 2026-08-09 (filed)
**Author / host:** NucBox G5 — GMKtec, Windows 11 Pro, Intel N-series x86_64 (on-host assistant)
**Produced against:** commit `3a0e6fd` (code inspected: `ApplicationLockEngine.kt`, `AppDetectionService.kt`, `LockScreenActivity` manifest declaration). Behavioural evidence: the NucBox WP2 matrix runs, pre-Hilt (`2026-07-23_wp2-matrix_nucbox-g5.md`) and post-Hilt (`2026-08-09_wp5-matrix_nucbox-g5.md`), plus the API-26 logcat deep-dive recorded in the former.
**Relates to:** **R-002** (RISK_REGISTER — lock-engine rapid-relaunch race) and its historical failure **HF-002** (the Phase-3 F4 bypass this defends). This is an **analysis** of an existing finding, not a new test run.
**Bottom line:** R-002 is a **structural race**, not a tunable flake. "Detect the app, then cover it with a reactively-launched `noHistory` Activity" cannot reliably win against the app's own rapid relaunches. No harness tuning (tap pacing, warm-up, relaunch count) will close it; it needs a **mechanism change** (suspend the app, or hold a persistent lock surface) — which is also what robust app-lockers do.

---

## 1. What we know (not disputed)

- The engine's **detection is not the miss.** During a 5×`am start` burst, logcat showed the engine receive **5–8** `AppLockEngine: <clock> -> LockDecision(requiresAuthentication=true …)` and call `startActivity(LockScreenActivity)` **each time** — yet the protected app still ended foreground with no lock for 6–12 s (API-26 deep-dive, pre-Hilt matrix report §2).
- The failure is in the **lock presentation**, and it is **probabilistic**: focused 8-burst loops give ~12–37% "no lock / inconsistent" across API 26/29/33/35, **non-monotonic** and **amplified on the slow emulators** (the fast Moto G real hardware was clean). It is **unchanged by the WP5 Hilt refactor** (behaviour-preserving).

## 2. Root cause — an unwinnable window-ordering race

Four factors, three in our code, one in the platform:

1. **Reactive + asynchronous detection ⇒ the app is foreground *first*.** The engine acts only *after* the accessibility framework delivers `TYPE_WINDOW_STATE_CHANGED` ([AppDetectionService.kt:30](../../../app/src/main/java/com/applock/applocker/service/AppDetectionService.kt)). Those events are queued/processed on the system's main thread and can be coalesced or delayed, so there is always a gap where the protected app is on top before the lock arrives. A burst of relaunches widens that gap. (This is the same "events stacking on the main thread" race class as CVE-2022-20006.)

2. **Activity-vs-Activity: last `startActivity` wins.** `LockScreenActivity` is a full-screen Activity — `launchMode=singleTop`, `taskAffinity=""` (own task), `noHistory=true`, launched `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP` ([ApplicationLockEngine.kt:123](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt); manifest lines 42–48). It competes in the **same** ActivityManager/WindowManager task ordering as the protected app's own `am start`s. The engine's mitigation — relaunch the lock on *every* event ([ApplicationLockEngine.kt:60-71](../../../app/src/main/java/com/applock/applocker/engine/ApplicationLockEngine.kt)) — is inherently reactive: if the **last** window transition in the burst is the app's relaunch and no further a11y event follows, the app is on top and nothing re-covers it.

3. **`noHistory` turns a momentary loss into a full exposure.** Because the lock is `android:noHistory="true"`, the instant it is covered it **finishes** rather than sitting behind the app — the exact tradeoff the code comment names ("slides its window over our lock screen, which then self-finishes (noHistory)"). So losing the race even briefly means the lock is *gone*, not merely hidden; the OS will not bring it back, and it stays gone until the next a11y event. (`noHistory` + `excludeFromRecents` were chosen so the lock can't be reached via Back/Recents — but that is precisely what makes a lost race non-self-healing.)

4. **Background-Activity-Launch (BAL) restrictions add platform headwind (Android 10+, API 29+).** The lock Activity is started from a *service* (background context). Since API 29 the platform restricts background activity starts, and the rules kept tightening (API 31/33/34). A11y services get an exemption, but the scheduling/priority of background-initiated starts differs by OS version — a plausible contributor to why the rate is **noisy and non-monotonic** across API levels rather than a clean gradient, and why slow emulators (which widen every timing gap) amplify it.

**Synthesis:** the design tries to *win a race it enters late*, in the *same arena* as its opponent, with a lock that *self-destructs* the moment it loses. That is structurally lossy; the observed 12–37% is the loss rate of that race under emulator timing.

## 3. Why tuning cannot fix it

TAP_GAP, warm-up, LOCKSCREEN_WAIT and relaunch-count only change *test* timing, not the ordering guarantee. The engine already relaunches on every event and still loses when the app gets the last transition. Any purely reactive "cover it" scheme has the same ceiling.

## 4. Remediation directions (in rough order of robustness)

1. **Prevent the app from foregrounding at all until authenticated** — Device Owner / device-admin `PackageManager.setPackagesSuspended()` (or an equivalent freeze). Robust because it *removes the race*: there is no window to cover, the app is suspended, not painted over. This is what "unbypassable" lockers do.
2. **Make the lock a persistent surface, not a raced `noHistory` Activity** — e.g. a `TYPE_APPLICATION_OVERLAY` held above all app windows, shown synchronously on detection. (Weaker: overlays are increasingly restricted — Android 15 tightens them — and can still be raced; but a persistent overlay does not self-finish on loss.)
3. **At minimum, stop the self-destruct** — drop `noHistory` and manage the lock's lifecycle explicitly so a transiently-lost race self-heals, and reconsider `CLEAR_TOP`/task handling so a relaunch cannot slip a window above a still-live lock.

This is the **enforcement/presentation** half of the same problem ADR-013A (two-tier detection) and R-001 address on the **detection** half: the reconciled Threat Model already concluded accessibility-reactive mechanisms should not be the sole security-critical enforcement path.

## 5. Caveats

- Grounded in **code inspection + behavioural logcat + literature**, not full per-factor instrumentation. The relative weight of factor 4 (BAL/scheduling) vs. factor 2 (task ordering) is not separately measured.
- A **timestamped `dumpsys activity` / `ActivityTaskManager` "Displayed" trace during a burst** would pin the exact ordering; a fresh capture was attempted here but blocked by emulator provisioning flakiness this session. **Best captured on real hardware** (where R-002's real-world severity is also still open — the fast Moto G was clean; low-end real devices untested).

## 6. References

- Maveris Labs — *Lock Screen Bypass Exploit (CVE-2022-20006)*: events stacking on the SystemUI main thread as a lock-race class. <https://medium.com/maverislabs/lock-screen-bypass-exploit-of-android-devices-cve-2022-20006-604958fcee3a>
- XDA — *"Unbypassable" App Lock*: robust lockers suspend the target app (Device Owner) rather than overlay-cover it. <https://xdaforums.com/t/app-unbypassable-app-lock-adb-setup-required.4787656/>
- Android Developers — *Restrictions on starting activities from the background* (BAL, since API 29). <https://developer.android.com/guide/components/activities/background-starts>
- Android Developers — *Behavior changes: apps targeting Android 15* (overlay/FGS tightening). <https://developer.android.com/about/versions/15/behavior-changes-15>
