package com.applock.domain

/**
 * Atomic policy-readiness + protected-package snapshot (R-005 readiness model, M7 WP2).
 *
 * A single sealed value carries readiness and data together, so a consumer can never read
 * "ready" against a stale package set (the fault the old `emptySet()`-seeded cache allowed).
 * The cache starts [Loading]; the first store emission — including a legitimately empty set —
 * atomically publishes [Ready]; a load/storage error publishes [Failed] (then retries).
 * Coroutine cancellation is not [Failed].
 *
 * Owned by [LockPolicyManager]. The engine-owned readiness aggregate that folds detector and
 * capability state around this lands in later WP2 phases; Phase 0 introduces the policy dimension.
 */
sealed interface PolicyState {

    /** No emission yet: the protected set is *unknown*, not "empty". */
    data object Loading : PolicyState

    /** The store has emitted; [packages] is authoritative and may be legitimately empty. */
    data class Ready(val packages: Set<String>) : PolicyState

    /** A load/storage error; [reason] is a short diagnostic, never user-facing. */
    data class Failed(val reason: String) : PolicyState
}
