package com.applock.domain

/**
 * Pure decision logic for the protection watchdog's self-stop, extracted so it can be unit-tested
 * on the JVM without the `Service` lifecycle (M7 WP2 Phase 0; the lifecycle itself stays an
 * instrumentation test).
 *
 * The watchdog exists to keep protection alive, so it may stand down (stop itself) **only** when
 * there is provably nothing to protect: no PIN is set, or policy has loaded to a definitively empty
 * protected set. While policy is still [PolicyState.Loading] or has [PolicyState.Failed], protection
 * intent is unknown — standing down then is the R-005 cold-start fail-open the old empty-set cache
 * allowed (RISK_REGISTER R-005, planned action 1). This keeps the watchdog up in that window.
 *
 * This is the minimal-correct rule for WP2; the full health-truth rewrite (usage / overlay grant +
 * detector liveness) is WP4.
 */
fun shouldStandDown(pinSet: Boolean, policyState: PolicyState): Boolean = when {
    !pinSet -> true
    policyState is PolicyState.Ready && policyState.packages.isEmpty() -> true
    else -> false
}
