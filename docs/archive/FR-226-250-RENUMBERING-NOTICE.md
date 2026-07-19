# ⚠ FR-226..FR-250 Identifier Reuse Notice

**The requirement identifiers FR-226 through FR-250 mean two entirely different things depending
on document date.**

| | Before 2026-07-19 (old baseline) | From 2026-07-19 (new baseline) |
|---|---|---|
| Section | 13 — Future Expansion & Advanced Features | 13 — Production Readiness |
| Content | Remote lock, remote vault protection, multi-device sync, trusted devices, MFA, wearables, smart unlock, emergency shortcut, decoy profiles, enterprise management, policy templates, plugins, advanced automation, privacy risk analysis, recommendation engine, subscriptions, licensing, security analytics, AI assistant, advanced recovery, migration tools, developer diagnostics, platform expansion, update framework, long-term maintainability | Build configuration separation, secure configuration, DB migration management, DB integrity verification, background processing, startup health check, dependency validation, permission verification, build versioning, release validation, feature flags, safe defaults, configuration validation, secure error handling, graceful failure, state recovery, runtime self-test, update compatibility, backup validation, restore validation, production logging, dependency inventory, release checklist, readiness verification, acceptance gate |
| Status | **Removed** (see `README.md` in this directory) | **Active** (`docs/srs/Requirements_section_13_ProductionReadiness.docx`) |

## Interpretation rules

1. Any artifact dated **before 2026-07-19** (commits `61f1db4`, `2a9ec37`, `5e49c5f`, `1dc9e25`,
   old docs, old changelog entries) that cites FR-226..250 refers to the **old** meanings.
2. Any artifact from **2026-07-19 onward** (RTM, migration docs, new plans, new code) refers to
   the **new** meanings.
3. **Verified during migration:** no source code, test, or changelog entry references any FR
   above FR-179, so the collision is confined to documentation. No code annotation needs fixing.
4. FR-001..FR-225 are unaffected — byte-identical between old and new baselines.

When in doubt, the RTM (`docs/process/rtm/rtm.csv`) is authoritative: it contains only the new
meanings.
