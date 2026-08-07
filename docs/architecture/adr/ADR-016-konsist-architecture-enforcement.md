# ADR-016 — Konsist for Automated Architecture-Rule Enforcement

**Status:** Accepted — execution in M1 (WP3) · **Date:** 2026-08-06 · **Source:** M1_PLAN §2 (WP3) + §4 (D2); operationalizes ADR-001 / ADR-010 / ADR-011 / ADR-015

## Context
ADR-001 (layered clean architecture) and ADR-011 (single-module `:app` with package-enforced layering) define the target layer structure, and ADR-015 (Hilt) carries an interim rule — *no new `Graph.*` lookup sites*. None of these are mechanically enforced today: layering is upheld by review only (ADR-010 gates major changes manually). M1 will **delete `Graph`** (WP5) and **move packages into the target layers** (WP6); across those mechanical refactors, layer violations and `Graph` reintroduction can regress silently between review points. WP3 introduces static analysis: detekt/ktlint cover style and complexity well but express layer-direction and package-boundary rules poorly.

## Decision
Adopt **Konsist** as the mechanism for enforcing architecture rules, as a **test-only** dependency in the existing JVM unit-test source set. Konsist rules are ordinary Kotlin tests that assert structural invariants over the codebase's declarations; they run inside the existing `testDebugUnitTest` CI job — **no new CI job, no production or runtime footprint**. Wire Konsist into `ci.yml` alongside detekt/ktlint (WP3). Division of labor: **detekt/ktlint = style, complexity, formatting; Konsist = architecture, layering, entry-point placement**.

The following rules are authored in WP3 and **activate in stages** as their enabling work packages land:

| Rule | Invariant | Enforces | Active from |
|------|-----------|----------|-------------|
| R1 | No **new** `Graph.` lookup sites: references confined to a frozen baseline of the existing legacy call sites (currently 10 files) plus `core/Graph.kt` itself; `di/` exempt once WP5 creates it | ADR-015 interim rule | WP3 immediately (baseline frozen) → baseline burns down in WP5 → flips to "`Graph` must not exist" at WP5 close |
| R2 | Layer dependency direction (inner layers must not depend on outer) | ADR-001, ADR-011 | Dormant until WP6 package move, then enforced |
| R3 | No **new** DAO/database types referenced from UI code — selected **by declaration** (ViewModels / `@Composable`s) pre-WP6, because UI code is not yet consolidated under one package (`presentation/` does not exist; `VaultViewModel` sits outside `*/ui`); frozen baseline grandfathers the current UI→DAO couplings (see implementation note); additionally package-enforced over `presentation/` post-WP6 | SDS §14 | WP3 immediately (frozen baseline; burns down at M3) |
| R4 | Platform entry points (services/receivers/activities) only in `platform/` / `presentation/` — **exempting the two FQCN-pinned components** (`AppDetectionService`, `UninstallProtectionReceiver`; ADR-018) | ADR-001, ADR-011, ADR-018 | Post-WP6 |

Staged activation is deliberate and recorded here so it is not mistaken for dead code: R2/R4 would fail against today's pre-realignment package layout, so they are authored but kept **dormant** (documented, not deleted) until WP6 moves packages into the target layers, at which point they flip to enforced.

**Verification:** CI must go **red on a deliberate rule violation** (verified once with a scratch commit during WP3) and green otherwise.

## Alternatives considered
- **detekt-only** — detekt can approximate some import/package constraints, but layer-direction and entry-point-location rules are awkward and far less readable than Konsist's declarative Kotlin assertions. Rejected *as the architecture mechanism*; detekt is retained for style/complexity.
- **Manual review only (status quo under ADR-010)** — insufficient across the WP5/WP6 mechanical refactors, exactly where silent regressions are most likely. ADR-016 **automates what ADR-010 gates** rather than replacing it.
- **ArchUnit** — JVM/bytecode- and reflection-oriented, with weaker awareness of Kotlin-source declarations; Konsist is Kotlin-native and reads source directly. Rejected.

## Consequences
- One new **test-only** dependency; zero production/runtime impact; no new CI job (runs in the existing unit-test job).
- ADR-015's interim rule and the ADR-001/011 layering become **mechanically enforced** rather than review-dependent → materially safer WP5 (Hilt) and WP6 (package realignment).
- Small bookkeeping cost: R2/R4 exist as dormant tests from WP3 until WP6 activates them.
- Konsist reads the codebase's Kotlin declarations; a future change to the layer model requires updating R2/R4 (and would itself be a new ADR under GOVERNANCE §2.2).

## Related
ADR-001, ADR-010, ADR-011, ADR-015, ADR-018 (R4 exemption); SDS §5.5, §14; M1_PLAN WP3 (defines), WP5/WP6 (activate rules); RTM FR-358 (static analysis in CI → `implemented` at WP3 close), FR-361, NFR-MNT-003.

## Implementation note (2026-08-06, WP3) — R1/R3 frozen baselines

Implementation surfaced concrete baselines; recorded here per GOVERNANCE §2.3 (factual
detail, does not alter the decision to adopt Konsist or the R1–R4 invariants).

- **R1 baseline** = the 11 files that reference `Graph` today: `core/Graph.kt` (the definition)
  plus 10 legacy call sites (`AppLockApplication`, `applocker/service/AppDetectionService`,
  `applocker/service/BootReceiver`, `applocker/service/ProtectionWatchdogService`,
  `authentication/ui/LockScreenActivity`, `privacy/ui/IntruderLogViewModel`,
  `ui/AppListViewModel`, `ui/MainActivity`, `ui/SettingsScreen`, `vault/VaultViewModel`).
  Any *other* file referencing `Graph` fails R1. Baseline burns down in WP5; R1 then flips to
  "`Graph` must not exist."
- **R3 needs the same frozen-baseline treatment** (this was not anticipated in the table above,
  which implied a blanket immediate rule). Running the rule showed **five** UI files couple to
  the `core.database` package today — more than an initial narrower grep suggested, because the
  coupling is via both DAO access *and* direct Room-entity imports: `ui/AppListViewModel`
  (`Graph.database.*Dao()` + entity imports), `privacy/ui/IntruderLogViewModel`
  (`Graph.database.intruderEventDao()`), `privacy/ui/IntruderLogScreen`, `vault/VaultViewModel`,
  and `vault/ui/VaultScreen` (entity imports). A blanket R3 would fail on day one. This UI→data
  coupling is resolved by the **M3 MVVM/repository refactor** (SDS §5.5/§14) — which moves UI onto
  domain/state models — **not** by the WP6 package move (which would merely relocate it). R3
  therefore grandfathers those five files and fails any *new* UI declaration that references a
  `com.applock.core.database` type; the baseline burns down at M3.

Both baselines follow the same "freeze existing, block new, burn down" philosophy already used by
the WP1 lint baseline and the WP3 detekt baseline.
