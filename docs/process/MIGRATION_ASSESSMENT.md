# Migration Assessment — Transition to the New Documentation Baseline

**Date:** 2026-07-19 (updated same day for SDS §7–17 delivery) · **Assessed against:**
`docs/New docs/` (59 documents: SRS 18 sections, NFR 13 sections, TAS 9 parts, SDS 17 sections,
Implementation Strategy v2.0)
**Existing baseline:** commit `1dc9e25` (Phases 1–3 of the old plan complete and validated)

The new documentation set is adopted as authoritative. This assessment determines how the
existing project migrates to it while preserving the maximum amount of validated work.

---

## Phase 1 — Project Inventory

### Repository tree (annotated)

```
Project Applock/
├── app/                                  Single-module Android application
│   ├── build.gradle.kts                  compileSdk 35, minSdk 26, targetSdk 35, R8 release
│   ├── proguard-rules.pro                Tink/errorprone dontwarn rules (release-critical)
│   └── src/
│       ├── main/java/com/applock/
│       │   ├── AppLockApplication.kt     App entry; Graph.init
│       │   ├── admin/                    Device-admin uninstall protection (opt-in)
│       │   ├── applocker/
│       │   │   ├── engine/               ApplicationLockEngine — core lock decisions
│       │   │   ├── policy/               LockPolicyManager + LockDecision
│       │   │   ├── service/              AppDetectionService (a11y), ProtectionWatchdogService
│       │   │   │                         (FGS), BootReceiver
│       │   │   └── session/              LockSessionManager, RelockPolicy (pure Kotlin)
│       │   ├── authentication/ui/        LockScreenActivity (biometric + PIN), PinPad
│       │   ├── core/
│       │   │   ├── Graph.kt              Hand-rolled service-locator DI
│       │   │   ├── SettingsRepository.kt SharedPreferences settings
│       │   │   ├── database/             Room v2 + SQLCipher; hand-written migrations;
│       │   │   │                         plaintext→encrypted legacy import
│       │   │   └── security/             Argon2PinHasher, CredentialRepository,
│       │   │                             DatabaseKeyProvider, EncryptedFileStore,
│       │   │                             LockoutManager (+ encrypted persistence)
│       │   ├── privacy/                  Intruder selfie: capture manager, policy, log UI
│       │   ├── ui/                       MainActivity (enum-based nav), SelfLock,
│       │   │                             AppListViewModel, SettingsScreen, theme
│       │   └── vault/                    Encrypted vault: repository, file types, UI, VM
│       ├── main/res/                     7 resource files (strings, a11y config, device admin)
│       └── test/                         7 JVM test files, 67 @Test methods, 733 LOC
├── build.gradle.kts / settings.gradle.kts
├── gradle/libs.versions.toml             Version catalog (AGP 8.7.3, Kotlin 2.1.0, KSP)
├── gradle/wrapper (8.10.2) + gradlew.bat
├── changelog.txt                         Human-readable change detail per phase
├── README.md                             Status table (stale: shows Phase 3/4 "Planned")
└── docs/
    ├── Requirements_section_1..13 (old)  FR-001..250 (old numbering)
    ├── Technical Architecture Specification.docx (old)   ← superseded
    ├── Software Design Specification.docx (old)          ← superseded
    ├── Android App Lock System Design Diagrams.docx      ← partially superseded
    ├── Draft_design_App_Lock.docx, app lock.docx         Early drafts
    ├── Functional Requirements Completion Summary.docx    Now inaccurate (250→375)
    ├── PHASE3_TEST_PLAN.md               Executed validation campaign + results
    ├── PHASE4_PLAN.md / PHASE4_TEST_PLAN.md   Automation plan (pre-dates new baseline)
    └── New docs/                         ★ NEW AUTHORITATIVE BASELINE (59 files)
```

### Asset summary

| Asset class | Present | Detail |
|---|---|---|
| Source code | ✓ | ~3,900 LOC Kotlin (main), 36 files, single `:app` module |
| Mobile application | ✓ | Android 8.0+ (minSdk 26), Compose UI, debug+release(R8) build clean |
| Services | ✓ | Accessibility service, foreground watchdog service, boot receiver, device-admin receiver |
| Databases | ✓ | Room v2 over SQLCipher; EncryptedSharedPreferences; EncryptedFile blob stores (vault, intruder) |
| Testing | ◐ | 67 JVM unit tests (pure logic); two executed manual E2E campaigns (docs/PHASE3_TEST_PLAN.md); **no** instrumentation/UI tests, no coverage tooling |
| Security assets | ✓ | Argon2id (OWASP params), SQLCipher, AES-256-GCM EncryptedFile, brute-force lockout, biometric, FLAG_SECURE, uninstall protection |
| Build system | ✓ | Gradle + version catalog; debug/release only (no staging/testing variants) |
| CI/CD | ✗ | None (no `.github/`, no pipeline, no static analysis, no signing config) |
| Automation (dev) | ◐ | Documented emulator/adb E2E recipes (test plans + memory); not scripted |
| Backend / APIs / smart contracts / frontend web / infrastructure-as-code | — | Not applicable — local-only Android app by design (confirmed by new TAS §5 assumptions) |
| Documentation | ✓ | Old set (superseded), new authoritative set, executed test plans, changelog |
| Deployment scripts | ✗ | None |

---

## Phase 2 — Current State Assessment

| Category | Rating | Justification |
|---|---|---|
| Architecture | **Fair** | Coherent package-per-feature layout with genuinely well-separated core logic (engine / policy / session are cleanly decoupled and the session+policy layer is pure Kotlin). But: service-locator DI (`Graph`), no interface abstractions, no layered presentation/domain/data separation, business logic reached directly from composables. Matches the *old* TAS precisely; falls short of the new SDS's Clean-Architecture/DI/interface mandates. |
| Code quality | **Good** | Small, readable, consistently idiomatic Kotlin; constraint-focused comments; deliberate security reasoning visible at decision points (fail-secure patterns, migration fallbacks). Two serious defects were found by testing, not by users, and root-caused properly. |
| Documentation | **Good** | Unusually rich for project size: full requirements set, executed validation plans with evidence, maintained changelog. Weakness: README stale; old TAS/SDS now superseded; no ADRs; no traceability matrix. |
| Testing | **Fair** | 67 targeted JVM tests over the highest-risk pure logic (Argon2 vectors, lockout math, intruder policy, vault types) — good. But no instrumentation tests, no UI tests, no coverage measurement, and the strong E2E campaigns are manual and unrepeatable by CI. Well short of NFR-TEST-002/003 (automated testing, 90% critical-logic coverage). |
| Security | **Good** | Strong for its stage: Argon2id, SQLCipher, encrypted blobs/prefs, lockout with persistence, biometric with PIN fallback, FLAG_SECURE, two gating bypasses (F3/F4) found and fixed during validation. Missing vs new baseline: centralized security services, root/tamper detection, audit-log integrity, threat model, secrets governance. |
| Maintainability | **Good** | ~3.9k LOC, clear ownership per package, injectable clocks/policies in logic classes. Debt is known and localized (Graph, enum nav). |
| Scalability | **Fair** | Fine for a local app at current data volumes; no pagination, no lazy loading, no resource limits — all now explicit requirements (FR-326..350). |
| Deployment readiness | **Poor** | No CI/CD, no build variants beyond debug/release, no signing/release process, no store-compliance work, no reproducible-build story. This is the widest gap vs the new baseline. |
| Technical debt | **Good** (low debt) | Debt is small, documented, and intentional ("move fast, add coverage later"); no dead code observed; changelog discipline maintained. |

---

## Phase 3 — Existing Documentation Review

| Document (old set) | Purpose | Still accurate? | Disposition |
|---|---|---|---|
| Requirements sections 1–12 | FR-001..225 | **Yes — byte-identical to the new SRS sections 1–12** | **Active.** These *are* the new baseline. Physically deduplicate (keep one copy). |
| Requirements section 13 — Future Expansion (FR-226..250 old) | Cloud/remote/multi-device/enterprise/AI/premium | Removed from baseline | **Archive.** Content partially resurfaces as TAS §74 "future evolution" candidates only. |
| Technical Architecture Specification (old) | Concrete engineering blueprint (classes, package tree, phases 1–5) | Superseded, but describes the **as-built** system accurately | **Archive as as-built reference.** Valuable during migration precisely because the code matches it. |
| Software Design Specification (old) | Class/interface sketches, sprints | Superseded | **Archive.** Its interface sketches (PolicyEngine, Trigger, BackupManager) remain useful inspiration for the new interface-extraction work. |
| System Design Diagrams | 50 diagrams | Partially aligned | **Archive; selectively re-derive.** New TAS demands diagrams live under architecture governance. |
| Draft_design_App_Lock / app lock.docx | Early concept notes | Historical | **Archive.** |
| Functional Requirements Completion Summary | "250/250 complete" | **Inaccurate** (375 FRs now; and it described spec completion, not implementation) | **Archive; replace** with a Requirements Traceability Matrix. |
| PHASE3_TEST_PLAN.md | Executed validation + evidence | Accurate historical record | **Active (immutable record).** Methodology feeds the future Test Specification. |
| PHASE4_PLAN.md / PHASE4_TEST_PLAN.md | Automation implementation + validation plan | Technically sound but written against the old phase model; scope excludes Bluetooth which the new Implementation Strategy includes in Automation | **Requires revision** during migration phase M4 (below). The rules-engine/resolver design and fail-secure invariants carry over largely intact. |
| changelog.txt / README.md | Change history / entry point | Changelog accurate; README stale (shows Phases 3–4 "Planned") | changelog **Active**; README **requires revision**. |

**Dependency map:** SRS 1–12 → (new SRS 13–18, NFR, TAS, SDS all reference the same FR set) →
Implementation Strategy governs sequencing → PHASE3/PHASE4 plans depend on old phase model →
README/changelog depend on both. The single collision point: **FR-226..250 mean different things
in old vs new section 13** — nothing in code or the changelog references the old 226–250 range
(highest FR cited in code is FR-179), so the collision is documentation-only. Archive the old
section 13 with a prominent renumbering note.

**Missing documentation** (referenced as companion documents by the new TAS §72 but not present):
Database Design Specification (DDS), UI/UX Specification, Threat Model, Secure Coding Standard,
Test Specification, Deployment & Operations Guide, Requirements Traceability Matrix (RTM),
SRS introduction/glossary volume. Per the stated assumptions these are **future work, not
defects**. *(SDS detailed component designs — originally missing — were delivered 2026-07-19 as
sections 7–17, closing the largest design gap.)*

---

## Phase 4 — New Documentation Review

### What the new baseline establishes

**Architecture (TAS, 9 parts).** Layered, modular, service-oriented architecture: UI →
Presentation → Application Services → Domain → Security/cross-cutting → Repositories → Platform.
Explicit trust boundaries, security domains, defense-in-depth layers; centralized cross-cutting
services (Security Service, Logging Service, Configuration Service, Recovery Manager, Health
Manager, Background Task Manager); single-process runtime; centralized lifecycle coordination;
data classification L1–L4 driving protection levels; operational architecture (logging,
monitoring, diagnostics, update, recovery, deployment); engineering architecture (CI/CD,
dependency governance, supply chain, testing layers); 10 seed ADRs.

**Technology baseline (TAS §70)** — decisive for compatibility: Kotlin ✓, Jetpack Compose *(or
equivalent)* ✓, MVVM + Clean Architecture, **Hilt (or equivalent)** DI, **Room (or equivalent)** ✓,
**Android Keystore** ✓, **WorkManager** for background, Kotlinx Serialization, JUnit/Espresso/
Mockito, Gradle ✓. The "(or equivalent)" qualifiers grant meaningful adaptation latitude.

**Design (SDS §1–17).** SOLID, Clean Architecture, DI everywhere; **global service locators
prohibited** unless explicitly approved (§5.5); interface-first contracts; module organization is
a *logical* specification — §4.1 explicitly permits adapting the physical structure if logical
boundaries and dependency rules are preserved (this is the single most migration-friendly clause
in the set); MVVM UI with navigation coordinator, presentation models, per-feature packages.
Sections 7–17 (added 2026-07-19) supply the detailed component designs: Authentication & Session
(§7), Lock Engine (§8 — Coordinator/Policy Evaluation Engine/Enforcement Controller/State
Manager decomposition with a deterministic layered evaluation order), Protected Applications
Manager (§9), Secure Vault (§10 — incl. Search and Backup Integration services), Scheduling &
Automation (§11 — Coordinator/Trigger Engine/Conflict Resolution, direct M4 input), Notifications
(§12), Security Services (§13 — the centralized Security Coordinator/Crypto/Key Management/
Secure Storage facade M2 builds), Data Access Layer (§14 — all persistence through repositories,
direct DB access prohibited), Background Processing (§15), Database Interaction (§16), and Error
Handling & Recovery (§17). These decompose-but-do-not-contradict the as-built subsystems, so the
Phase 6 compatibility classifications stand.

**Requirements.** SRS grows 225 → 375 FRs (sections 13–18: Production Readiness, Operational
Resilience, Observability & Monitoring, Data Lifecycle, Scalability & Resource Management,
Secure Development & Maintenance). A formal NFR corpus (171 NFRs, 13 categories) with measurable
targets, e.g.: cold start ≤ 2.0 s median, resume ≤ 750 ms, UI feedback ≤ 100 ms, **lock
enforcement ≤ 250 ms after launch detection**, indexed queries ≤ 50 ms, vault UI ≤ 500 ms,
crash-free sessions ≥ 99.9 %, MTTR ≤ 30 s, ≥ 90 % statement coverage on critical logic.

**Process (Implementation Strategy v2.0).** Seven phases (0 Foundation → 1 Core Security
Platform → 2 Core App Features/MVP → 3 Automation → 4 Production Hardening → 5 Security
Hardening → 6 Release Readiness) with formal phase-gate reviews, continuous activities
(static analysis, dependency scanning, threat-model maintenance), and release governance.

### Undefined / incomplete areas (treat as future work)

1. The companion documents listed in Phase 3 above (DDS, Threat Model, Test Spec, etc.).
2. ~~SDS detailed component designs (sections ≥ 7)~~ — **resolved 2026-07-19**: sections 7–17
   delivered (see Design paragraph above).
3. Supported API-level range ("defined by project requirements" — not stated anywhere; current
   code uses minSdk 26 / targetSdk 35; needs a decision record). **Resolved by ADR-014 (M0).**
4. Concrete CI/CD tooling choices (pipeline stages named, tools not).
5. Priority/severity scheme for the 150 new FRs (old sections carried per-FR priorities; new
   sections 13–18 mostly do not).
6. NFR verification procedures (targets given; measurement protocol deferred to the missing
   Test Specification).

---

## Phase 5 — Requirements Reconciliation

| Category | Count | Detail |
|---|---|---|
| **Retained** | **225** (FR-001..225) | Sections 1–12 are byte-identical old→new. Everything implemented so far traces to this range — *no implemented feature lost its requirement basis.* |
| **Modified** | **0** at FR-text level | No retained FR changed its wording. Context changed instead: all FRs are now subject to the NFR corpus, traceability, and phase-gate verification, which raises the acceptance bar for already-"done" work (e.g., FR-026.. lock engine must now also demonstrate ≤ 250 ms enforcement and structured audit logging). |
| **Removed** | **25** (old FR-226..250) | Cloud/remote lock, multi-device sync, wearables, enterprise policy, plugins, AI assistant, subscriptions/licensing, platform expansion. **Implementation impact: zero** — old Phase 5 was never started; no code supports these. Nothing to remove or isolate. My PHASE4 plan's deferral of profiles/Bluetooth cited "Phase 5" as a landing zone — that landing zone no longer exists in this form; both features fold into the new Automation phase instead. |
| **New** | **150 FRs + 171 NFRs** | New FR-226..250 (Production Readiness), FR-251..275 (Operational Resilience), FR-276..300 (Observability), FR-301..325 (Data Lifecycle), FR-326..350 (Scalability/Resources), FR-351..375 (Secure Development). Plus the entire NFR specification. |

**Character of the new work:** almost none of the 150 new FRs are user-facing features. They are
platform/engineering capabilities: build variants, secure config, migration governance, health
checks, feature flags, recovery managers, structured logging, metrics, log rotation/export,
data classification/retention/key rotation, pagination, thread management, CI/static analysis,
dependency inventory, release gates. Several are *partially* satisfied already (FR-228 migration
management — hand-written migrations exist; FR-237 safe defaults; FR-239 secure error handling;
FR-253 a11y recovery — watchdog; FR-241 state recovery — validated in P-1/P-3 tests; FR-308
secure deletion — vault). The dominant net-new subsystems: **observability platform, recovery/
resilience framework, configuration service + feature flags, data-lifecycle management, backup
(FR-196..205 was always required but never built), and the engineering pipeline itself.**

**Overall impact estimate:** the *feature* codebase survives nearly intact; the *engineering
system around it* must be largely built. Requirements volume grows +67 % (225→375 FRs), and the
effective scope of "done" for existing work grows because of NFR verification. Roughly: existing
code covers ~35–40 % of the retained-FR feature surface it always targeted, and ~5–10 % of the
new-FR surface (incidental overlaps listed above).

---

## Phase 6 — Compatibility Analysis

| Subsystem (as-built) | Classification | Technical reasoning |
|---|---|---|
| Lock engine + a11y detection (`applocker/engine`, `service`) | **Compatible with minor changes** | New TAS's Lock Engine component has the same responsibilities (launch interception, lock state evaluation, policy execution, session timeout). Needs: interface extraction, DI constructor injection (already constructor-injected — only the Graph wiring changes), structured audit logging via the future Logging Service, and latency instrumentation for NFR-PERF-012. The F3/F4 hard-won gating semantics must be preserved verbatim. |
| Session + relock policy (`applocker/session`) | **Fully compatible** | Pure Kotlin, injected clock/policy, thread-safe, unit-tested. Maps 1:1 onto the new Domain layer ("session timeout evaluation", Session Policy). Package relocation only. |
| Lock policy manager (`applocker/policy`) | **Compatible with minor changes** | Becomes the Domain "lock decision logic" behind an Authorization Service interface; its in-memory snapshot design is exactly what the ≤ 250 ms enforcement NFR wants. |
| Authentication (Argon2 hasher, CredentialRepository, LockoutManager, biometric flow) | **Compatible with minor changes** | New Authentication Architecture (TAS §14) wants Coordinator + provider components behind interfaces, isolated from business logic — current classes already have those seams (PinHasher interface exists; lockout storage is abstracted). Work: formal interfaces, an AuthenticationCoordinator, session-manager unification. Argon2id/upgrade-on-verify logic is directly reusable. |
| Cryptography & secure storage (SQLCipher Room, DatabaseKeyProvider, EncryptedFileStore, EncryptedPrefs) | **Compatible with minor changes** | Satisfies "encryption before persistence", Keystore-backed keys, repository-ish access. Work: consolidate behind a centralized Security Service facade (TAS §16 "business components never implement crypto directly" — they mostly don't already), formalize L1–L4 data classification, add key-rotation/retirement (FR-317..319) which the current fixed-key design lacks. |
| Database layer (Room v2, hand-written migrations, legacy import) | **Compatible with minor changes** | New baseline mandates Room "(or equivalent)" with versioned migrations (FR-228) — already the practice. Work: remove `fallbackToDestructiveMigration()` (violates FR-228 rollback/recovery), add integrity verification (FR-229), produce the DDS from the as-built schema. |
| Vault (`vault/`) | **Compatible with minor changes** | Matches Secure Vault component responsibilities. Work: repository interface split, use-case extraction, lifecycle requirements (FR-309), search/folders/backup were already planned FRs (117/116/120) and remain retained requirements. |
| Intruder selfie (`privacy/`) | **Fully compatible** | FR-081..085 retained unchanged; implementation validated E2E. Only DI/logging integration touches it. |
| Watchdog FGS + BootReceiver | **Compatible with minor changes** | Direct precursors of the new Health Monitoring / Service Recovery components (FR-251..253, FR-268). Extend rather than replace: fold into Health Manager reporting states (Healthy/Degraded/Warning/Failed). |
| UI layer (MainActivity enum nav, screens, 3 ViewModels) | **Compatible with major refactoring** | Compose ✓ and partial MVVM ✓, but: navigation is an enum `when` in one file (new SDS requires a Navigation Coordinator, per-feature presentation modules, presentation models, UI state objects); PIN screens bypass ViewModels; composables call `Graph.*` directly (prohibited layering). The *screens themselves* (layouts, flows, SelfLock gating) carry over; the wiring is restructured. SelfLock's semantics are security-critical (F3) — port carefully with regression tests. |
| DI (`core/Graph.kt` service locator) | **Incompatible** | Explicitly prohibited by SDS §5.5 ("shall not be retrieved through global service locators"). Replace with Hilt (TAS baseline). Mitigating factor: Graph was designed to be swapped ("can be replaced with Hilt later without touching call sites much") and all constructed objects already use constructor injection — this is a bounded, mechanical migration, not a redesign. |
| Background processing | **Incompatible (absent)** | New baseline mandates WorkManager-coordinated background categories + Background Task Manager. Nothing exists; net-new build. The FGS watchdog remains legitimate (a11y monitoring is exactly the "specialUse" it declares). |
| Settings (`SettingsRepository` on plain SharedPreferences) | **Compatible with major refactoring** | Becomes the Configuration Service: validation (FR-238), versioning, feature flags (FR-236), secure defaults (FR-237), classification-appropriate storage. Current key-value logic is trivially portable; the surrounding service is new. |
| Unit tests (67) | **Fully compatible** | Test pure logic that survives; package moves only. They become the seed of NFR-TEST-003 coverage. |
| Build system | **Compatible with minor changes** | Version catalog ✓ (FR-247 adjacent), R8 ✓. Add Development/Testing/Staging/Production variants (FR-226), static analysis, CI integration, signing config. |
| E2E methodology (test plans, emulator recipes) | **Fully compatible** | Feeds the missing Test Specification; the OV-3/OV-4/F3 gating checks become the permanent security regression suite. |

---

## Phase 7 — Salvageability Assessment

| Area | Reuse immediately | Reuse after modification | Archive for reference | Replace entirely | Est. reuse |
|---|---|---|---|---|---|
| Source code (main) | Session/policy logic, Argon2 hasher, lockout math, intruder policy, vault file logic, migrations (~30 %) | Engine, auth flows, repositories, UI screens, watchdog, DB builder (~60 %) | — | `Graph.kt` service locator, enum navigation shell (~10 %) | **~85–90 %** |
| Test suites | All 67 JVM tests (package paths updated) | — | — | — | **~95 %** |
| Documentation | SRS 1–12 (identical), PHASE3 results (record), changelog | README, PHASE4 plan/test plan (rework for new phase model + Bluetooth) | Old TAS/SDS/diagrams/drafts/FRCS/old §13 | — | **~70 %** by usefulness |
| Build system | Version catalog, wrapper, proguard rules | build.gradle.kts (add variants, analysis plugins) | — | — | **~80 %** |
| Security components | Argon2id, SQLCipher setup, EncryptedFileStore, lockout | Key provider (add rotation), FLAG_SECURE gating | — | — | **~90 %** |
| Automation (dev/E2E) | Emulator recipes, adb gotchas, PIN-pad/dpad scripts knowledge | Convert prose recipes into scripted checks where feasible | — | — | **~100 %** (as knowledge) |
| CI/CD | — | — | — | Everything (nothing exists) | **0 %** |
| Infrastructure / deployment assets | — | — | — | Net-new (signing, pipelines, release artifacts) | **0 %** |

Weighted overall: **roughly 80–85 % of existing engineering output survives** (code + tests +
validated security behavior + docs that are literally the new SRS). The new baseline's cost is
overwhelmingly *additive* (engineering platform + 150 FRs), not *destructive*.

---

## Phase 8 — Repository Organization Review

The single-module layout **may be retained**: SDS §4.1 makes the module organization a logical
specification and explicitly allows physical adaptation if boundaries and dependency rules hold.
Recommendation: keep `:app` single-module now, enforce logical layering via packages + static
analysis (dependency rules), and split into Gradle modules only if/when build times or team size
demand it. This avoids the highest-churn restructuring with zero requirement value.

### Recommended target structure

```
Project Applock/
├── .github/workflows/            ci.yml (build+test+lint+detekt), release.yml     [NEW]
├── app/src/main/java/com/applock/
│   ├── presentation/             feature packages: applist, vault, settings,
│   │                             intruderlog, lockscreen, onboarding, diagnostics
│   ├── domain/                   session, policy, use cases, models (pure Kotlin)
│   ├── service/ (application)    workflow coordinators (lock, auth, backup)
│   ├── data/                     repositories (interfaces + impls), database, mappers
│   ├── security/                 crypto facade, keys, credential, lockout, classification
│   ├── infrastructure/           logging, metrics, diagnostics, config/flags, background
│   ├── platform/                 a11y service, biometric, receivers, FGS, permissions
│   └── di/                       Hilt modules (replaces core/Graph.kt)
├── config/                       detekt.yml, lint.xml, dependency rules               [NEW]
├── docs/
│   ├── srs/                      Sections 1–18 (single deduplicated copy)
│   ├── nfr/                      Sections 0–13
│   ├── architecture/tas/         TAS parts 1–9   + adr/ (ADR-001..010 + new)
│   ├── design/sds/               SDS sections 1–17
│   ├── process/                  Implementation Strategy, phase-gate records, RTM
│   ├── testing/                  Test Specification (future), PHASE3/4 plans, campaign records
│   └── archive/                  old TAS, old SDS, diagrams, drafts, FRCS,
│                                 old section 13 (with FR-renumbering notice)
└── scripts/                      emulator/E2E helper scripts                          [NEW]
```

Specific file actions: move the 49 files out of `docs/New docs/` (space in path, ad-hoc name)
into the tree above; **delete the duplicate** old sections 1–12 (byte-identical); rename
`Non-functional_section_5_SecurityQuality .docx` (trailing space); archive the six superseded
old docs; update README's status table to the new phase model; keep `changelog.txt` at root.

---

## Phase 9 — Project Lifecycle Evaluation

**Current phase (against the old model):** mid **Feature Development** — old Phases 1–3 of 5
complete and validated; automation was next.

**Current phase (re-baselined against the new model):** the project sits *ahead* of the new
lifecycle in features and *behind* it in foundation:

| New IS phase | Status of existing work |
|---|---|
| 0 Foundation | **Incomplete** — no CI/CD, no static analysis, prohibited DI pattern, no secret management process, no docs governance |
| 1 Core Security Platform | **Largely implemented** (auth, keystore-backed crypto, lock engine, a11y, sessions) but never passed the new gate (no threat model, no formal security testing record) |
| 2 Core Application Features | **Partial** — protected apps ✓, vault ✓, settings ◐, notifications ◐, backup ✗, onboarding/dashboard ✗, admin tools ✗ |
| 3 Automation | Planned only (PHASE4_PLAN.md, needs rework) |
| 4–6 Hardening/Release | Not started (essentially all of new FR-226..375) |

**Recommended phase: return to Phase 0 — Foundation Development — executed as a *retrofit*, not
a restart.** Justification: (a) the Implementation Strategy makes Phase 0 a hard prerequisite
with exit criteria (automated builds, static analysis, dependency audit) that nothing currently
satisfies; (b) every subsequent phase's gate requires CI, traceability, and documentation
infrastructure that doesn't exist; (c) continuing feature work first (automation) would pour new
code into a DI pattern the SDS prohibits, guaranteeing double rework. Moving backward here is
cheap precisely because the feature code is small and healthy; it gets cheaper never again.

---

## Phase 10 — Gap Analysis

Priority: **P0** blocks efficient development · **P1** blocks the next phase gates · **P2** needed before release · **P3** deferrable.

| Gap | Area | Priority |
|---|---|---|
| No CI/CD pipeline, no static analysis, no automated quality gate | Operations/Testing | **P0** |
| DI: service locator prohibited by SDS; Hilt migration pending | Architecture | **P0** |
| Requirements Traceability Matrix absent (375 FRs + 171 NFRs unmapped) | Governance | **P0** |
| Repo/docs reorganization + archive + FR-226..250 renumbering notice | Documentation | **P0** |
| API-level support range undefined in new docs (decide + ADR) | Requirements | **P1** |
| Threat Model, Secure Coding Standard missing (Phase 1 gate inputs) | Security | **P1** |
| Test Specification missing; no instrumentation tests; no coverage measurement (90 % target) | Testing | **P1** |
| Build variants (Dev/Test/Staging/Prod), secure config injection (FR-226/227) | Build/Deployment | **P1** |
| Interface extraction + layer realignment (presentation/domain/data) | Design | **P1** |
| Backup & restore subsystem (FR-196..205, 244/245) — retained requirement, never built | Feature | **P1** |
| Observability platform: structured logging, metrics, health, diagnostics (FR-276..300) | Operations | **P2** |
| Recovery/resilience framework (FR-251..275); remove destructive-migration fallback (FR-228/229) | Reliability | **P2** |
| Configuration service + feature flags + validation (FR-236/238; SDS config constraints) | Design | **P2** |
| Data lifecycle: classification, retention, key rotation (FR-301..325) | Security/Data | **P2** |
| DDS, UI/UX Spec, Deployment & Ops Guide | Documentation | **P2** |
| Onboarding, dashboard, diagnostics, admin UI (SDS §6.5 modules) | UI | **P2** |
| Scalability mechanics: pagination, lazy loading, limits (FR-326..350) | Performance | **P3** |
| NFR benchmark harness (startup, lock latency ≤ 250 ms, query timings) | Testing | **P3** (define protocol P1) |
| Notification privacy features (FR-146..160 remainder) | Feature | **P3** |

---

## Phase 11 — Risk Assessment

| Risk | Level | Analysis & mitigation |
|---|---|---|
| Architecture risk | **Medium** | The refactor touches a *proven* lock engine whose two historical bypasses (F3 self-gate, F4 fast-relaunch) lived exactly in the code being restructured. Mitigate: freeze engine behavior with the OV-3/OV-4/F3 device regression suite before refactoring; migrate DI mechanically (Hilt modules mirroring Graph) before any structural change; one subsystem per PR. |
| Migration risk | **Medium** | Data-bearing installs exist (emulator + any sideloads). `fallbackToDestructiveMigration` is a live data-loss trap during upcoming schema work. Mitigate: remove it in M1 and add the FR-229 integrity check early; keep the B-1-style upgrade test mandatory. |
| Schedule risk | **High** | 150 new FRs + 171 NFRs + 8 missing companion documents is an enterprise-scale program against a solo-developer cadence and a slow 2-core emulator. Mitigate: strict phase gating, treat NFR verification tooling as build-once assets, aggressively use the "(or equivalent)" latitude, and sequence so feature-visible progress (automation) lands mid-program, not last. |
| Security risk | **Low–Medium** | Current posture is strong; principal risk is *regression during refactor* rather than absent controls. Key-rotation absence (FR-317/318) is the largest genuine control gap. Mitigate: security regression suite in CI; threat model before touching auth/crypto internals. |
| Integration risk | **Medium** | Hilt + WorkManager + variants introduce new failure modes with the a11y service and R8 (Phase-2 history: Tink/R8 rules). Mitigate: release-build smoke test in CI from day one. |
| Documentation risk | **High** | The new baseline mandates traceability to documents that don't exist yet (Threat Model, Test Spec, DDS, RTM). Untracked, this stalls every phase gate. Mitigate: author skeletons in M0 and grow them per phase; the RTM starts as a spreadsheet/markdown table, not a tool. |
| Technical debt | **Low now, Medium trajectory** | Debt is small and documented today; the risk is accruing new debt if automation is built pre-foundation. Mitigate: foundation-first sequencing (M1 before M4). |
| Maintainability | **Low** | Codebase is small, idiomatic, and the target architecture improves it further. |
| Scalability | **Low** | Local app, bounded data; FR-326..350 mechanisms are straightforward when scheduled. |

---

## Phase 12 — Migration Strategy

Phases map onto the Implementation Strategy (IS) phase model. Effort in solo-dev weeks (rough).

| # | Phase | Objectives & deliverables | Depends on | Key risks | Effort | Exit criteria |
|---|---|---|---|---|---|---|
| **M0** | Baseline & governance | Repo/docs restructure per Phase 8; archive old docs with renumbering notice; deduplicate SRS; RTM skeleton (375 FR + 171 NFR rows, status column seeded from this assessment); README/changelog updates; ADR log started (backfill ADR-001..010 + as-built deviations); API-range decision ADR | — | Low | **1 wk** | Docs tree matches Phase 8; RTM exists; no duplicate/superseded docs outside archive |
| **M1** | Foundation retrofit (= IS Phase 0) | CI pipeline (build, unit tests, lint/detekt/ktlint, release-build smoke, dependency audit); build variants Dev/Test/Staging/Prod + secure config injection (FR-226/227); **Hilt migration replacing `Graph`**; interface extraction for engine/auth/repos; package realignment to target layers; remove `fallbackToDestructiveMigration`; scripted device regression suite (OV-3/OV-4/F3 + core flows) | M0 | Engine regression; R8/Hilt interactions | **3–4 wk** | IS Phase-0 exit criteria: green automated builds + static analysis + dependency audit; zero service-locator lookups; device regression suite passes on the migrated build |
| **M2** | Security platform gate (= IS Phase 1 retrofit) | Centralized Security Service facade (crypto, keys, random, integrity); Authorization Service; key rotation/retirement design (FR-317..319); startup health check (FR-231) + permission verification (FR-233); Threat Model v1 + Secure Coding Standard v1; security test additions (auth, lockout, storage) | M1 | Auth regressions | **2–3 wk** | IS Phase-1 gate review passed and recorded; threat model approved; security suite in CI |
| **M3** | MVP completion (= IS Phase 2) | Backup/restore with validation (FR-196..205, 244/245); Configuration Service + feature flags + validation (FR-236..238); notification handling (FR-146.. subset); onboarding + dashboard + diagnostics UI; navigation coordinator + per-feature presentation modules (completes the UI refactor); Test Specification v1 + first instrumentation tests | M1 (M2 parallelizable) | Scope breadth | **4–6 wk** | All targeted MVP FRs implemented + regression green; functional gate review recorded |
| **M4** | Automation (= IS Phase 3) | Rework PHASE4_PLAN to the new baseline: schedules, Wi-Fi, location, **Bluetooth** (now in scope per IS), rule engine with FR-141 priority, override, automation logging; battery impact assessment (IS exit criterion); PHASE4_TEST_PLAN campaign incl. two-pass sign-off | M1–M3 | Emulator has no Bluetooth — needs hardware or documented deferral at the gate | **3–4 wk** | Automation rules validated; battery assessed; reliability objectives met |
| **M5** | Production hardening (= IS Phase 4) | Observability platform (FR-276..300: structured logging, metrics, health, log rotation/export); resilience framework (FR-251..275: Recovery Manager, retry policies, WorkManager background categories); data lifecycle (FR-301..325); scalability items (FR-326..350); NFR benchmark harness (startup, lock latency, queries) | M1 | Performance targets on low-end hardware | **4–6 wk** | Performance + reliability objectives demonstrated; observability validated |
| **M6** | Security hardening & release (= IS Phases 5–6) | FR-351..375 processes; dependency review, vulnerability scanning, integrity verification; full verification campaign (unit/integration/UI/E2E/accessibility/perf/compat); release checklist (FR-248), readiness verification (FR-249), acceptance gate report (FR-250); signed artifacts | All prior | Independent-assessment availability | **3–4 wk** | FR-250 production acceptance report approved; v1.0.0 artifacts signed and archived |

Total: **~20–28 solo-dev weeks.** The plan preserves the validated feature core throughout —
at no point is working functionality discarded; each phase wraps, extends, or gates it.

---

## Phase 13 — Recommended Next Steps

**Immediate (this week)**
1. Execute M0: restructure `docs/`, archive superseded documents with the FR-226..250
   renumbering notice, deduplicate SRS 1–12, fix the trailing-space filename.
2. Create the RTM skeleton and mark statuses from this assessment (retained-implemented /
   retained-pending / new-pending).
3. Update README to the new phase model; changelog entry for the baseline adoption.
4. Record ADRs: single-module decision, API range, Hilt adoption plan, keep-SQLCipher (as the
   Room "or equivalent" instantiation), keep-accessibility-detection approach.

**Short-term (before major implementation resumes — M1)**
5. Stand up CI (build + 67 tests + release-build smoke) — even before any refactor, this locks
   in the current green state.
6. Add detekt/ktlint/lint with dependency-direction rules approximating SDS layering.
7. Script the OV-3/OV-4/F3 device regression checks (the security freeze harness).
8. Hilt migration replacing `Graph`; remove `fallbackToDestructiveMigration`.
9. Introduce build variants + secure config injection.

**Medium-term (core implementation — M2–M4)**
10. Security Service consolidation + Threat Model + Phase-1 gate review.
11. Backup/restore subsystem; Configuration Service + feature flags; UI restructure with
    navigation coordinator; Test Specification + instrumentation tests.
12. Automation phase from the reworked PHASE4 plan (add Bluetooth scoping decision at the gate).

**Long-term (optimization, hardening, release — M5–M6)**
13. Observability + resilience + data-lifecycle platforms; NFR benchmark harness.
14. Secure-development process requirements (FR-351..375); full verification campaign;
    FR-250 acceptance gate and signed v1.0.0 release.

---

## Phase 14 — Executive Summary

**Overall project health: Good.** Three of five old phases are implemented, E2E-validated on
device, and committed. The codebase is small (~3.9k LOC), clean, security-literate, and carries
67 passing unit tests plus two documented validation campaigns with defect records.

**Implementation maturity:** feature core is mid-development and solid; engineering
infrastructure (CI/CD, static analysis, variants, observability, release process) is essentially
absent — which is precisely what the new baseline targets.

**Alignment with the redesign:** high at the technology level (Kotlin, Compose, Room, Keystore,
SQLCipher all conform, aided by the TAS's "(or equivalent)" clauses), moderate at the structural
level (layering/interfaces/DI need retrofit), low at the process level (no pipeline, no gates,
no traceability). Critically, **all 225 previously-implemented-against requirements are retained
byte-identical**, and the 25 removed requirements (old cloud/enterprise section 13) had zero
implementation — so no built feature loses its mandate.

**Reusable work: ~80–85 %** of existing engineering output (≈85–90 % of source, ≈95 % of tests,
the validated security behavior, and the SRS itself). The only outright incompatibilities are
the `Graph` service locator (prohibited by the new SDS; replace with Hilt — a bounded, planned
swap) and the absence of WorkManager-based background processing (net-new, nothing to replace).

**Major incompatibilities:** service-locator DI; enum-based navigation vs the mandated
navigation-coordinator MVVM structure; `fallbackToDestructiveMigration` vs FR-228; no key
rotation vs FR-317..319.

**Major documentation gaps (future work per the new baseline itself):** Threat Model, Test
Specification, Database Design Specification, Secure Coding Standard, UI/UX Specification,
Deployment & Operations Guide, RTM (SDS detailed-design sections 7–17 were delivered
2026-07-19, closing the largest of the original gaps). Also note the
**FR-226..250 identifier collision** between the removed old section 13 and the new Production
Readiness section — documentation-only, but it must be flagged in the archive to prevent
mis-tracing.

**Recommended repository restructuring:** keep the single `:app` module (explicitly permitted by
SDS §4.1), realign packages to the layered structure, reorganize `docs/` into
srs/nfr/architecture/design/process/testing/archive, and add `.github/workflows` + `config/`.

**Recommended lifecycle phase: move back to Phase 0 (Foundation Development) as a retrofit.**
Feature work (automation was next) should pause for ~3–4 weeks of foundation building so new
code lands in the sanctioned architecture once, not twice.

**Immediate priorities:** docs restructure + RTM (M0), CI freeze of the current green state,
scripted gating-regression harness, Hilt migration, destructive-migration removal.

**Risks requiring management attention:** schedule realism (**High** — the new baseline
describes an enterprise-scale program: 375 FRs, 171 NFRs, 7 gated phases against solo capacity;
scope or timeline must flex), documentation debt (**High** — eight mandated companion documents
don't exist and every phase gate depends on them), and engine-refactor regression (**Medium** —
mitigated by the regression harness before restructuring).

**Confidence in this assessment: High** for requirements reconciliation (byte-level comparison
of old vs new SRS), code compatibility (all 36 source files reviewed or previously known from
validation work), and salvageability. **Medium** for effort estimates (docs specify *what*, not
tool choices, and solo-dev velocity on the constrained emulator environment adds variance).
