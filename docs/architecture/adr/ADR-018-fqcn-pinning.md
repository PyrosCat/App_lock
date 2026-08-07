# ADR-018 — Permanent FQCN Pinning of AppDetectionService and UninstallProtectionReceiver

**Status:** Accepted — binding constraint, enforced from M1 (WP6) onward · **Date:** 2026-08-06 · **Source:** M1_PLAN §4 (D3) pause-point recommendation, adopted 2026-08-06; discovered-constraint record per GOVERNANCE §2.2

## Context
Android persists two of our component names **outside the application**, keyed by fully-qualified class name:

- the accessibility grant lives in the `enabled_accessibility_services` secure setting as `<applicationId>/<FQCN>`;
- the active device-admin registration stores the receiver's `ComponentName`.

If a package refactor renames or moves these classes, the persisted strings dangle on the next upgrade: the accessibility service **silently unbinds** (protection stops with no error; re-granting is user-visible friction and, for sideloads on Android 13+, is aggravated by Restricted Settings — risk R-001a), and the device-admin registration **strands** (uninstall protection dead, deactivation flows broken). Android provides no alias mechanism for services or receivers (`activity-alias` is activities-only), so there is no manifest-level escape. WP6 (ADR-011 package realignment) creates exactly this hazard.

## Decision
The following two fully-qualified class names are **pinned permanently** (verified against the manifest, 2026-08-06):

- `com.applock.applocker.service.AppDetectionService`
- `com.applock.applocker.admin.UninstallProtectionReceiver`

They do not move or rename in WP6 or any later refactor. **No shim/subclass indirection** — a shim would still require the pinned name while adding a permanent vestigial layer; the real classes stay put. All other components move freely: `BootReceiver`, `ProtectionWatchdogService`, and the activities are manifest-registered and re-resolved on update, with no externally persisted name.

Enforcement and verification:
- **Konsist R4** (ADR-016) carries a named exemption for exactly these two FQCNs; everything else must sit in `platform/`/`presentation/` post-WP6.
- The **WP6 exit check** includes an upgrade-install over a WP5 build verifying the accessibility service stays bound and the device-admin registration stays intact.

This constraint may be revisited only via a superseding ADR that includes an explicit, user-visible re-grant migration plan — and Restricted Settings makes any such plan costly, so the working assumption is *never*.

## Alternatives considered
- **Rename + user re-grant flow** — rejected: guarantees a silent protection-loss window on upgrade for every install, plus Restricted Settings friction for sideloads (R-001a).
- **Shim classes at the old FQCNs delegating to moved implementations** — rejected: the pinned names must exist anyway; the shim only adds indirection and a false sense that the constraint was lifted.
- **Pin only the accessibility service** — rejected: the device-admin receiver is persisted the same way and fails the same way.

## Consequences
- Two files live permanently outside the ADR-011 target layout (`applocker/service/`, `applocker/admin/` instead of `platform/`) — a deliberate, documented exception, not drift; Konsist R4's exemption makes it machine-checked rather than tribal knowledge.
- Upgrade safety for every existing install across WP6 and all future refactors.
- Negative: the layer story has a visible asterisk; new contributors must learn why via this ADR (cross-linked from ADR-011 and ADR-016).
- The applicationId half of the same persisted strings is pinned by ADR-017 (prod = `com.applock` permanently); together the two ADRs fix both halves of the externally persisted component identity.

## Related requirements
FR-179 (protection-revocation alerting), FR-253 (accessibility recovery), risk **R-001a** (`docs/process/RISK_REGISTER.md`); no FR mandates the pinning directly — it is a discovered Android-platform constraint. Related ADRs: ADR-011 (constrains its WP6 execution), ADR-013/013A (the pinned service is the current detector and future optional enhancement), ADR-016 (R4 exemption), ADR-017 (applicationId half).
