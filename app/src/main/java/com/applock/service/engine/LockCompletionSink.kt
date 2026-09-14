package com.applock.service.engine

import java.util.concurrent.atomic.AtomicReference

/**
 * The app-scoped completion seam for the change-E overlay surfaces (M7 WP2 change E). It is the narrow
 * path by which a surface reports a request-token completion to [LockEngineRuntime] WITHOUT the Hilt
 * cutover, which is change F. The overlay path has two surfaces that cannot hold a runtime reference at
 * the moment they complete: the biometric prompt runs in a separate, intent-launched
 * [com.applock.presentation.authentication.BiometricHostActivity], and the overlay itself is a
 * WindowManager window, not an Activity, so neither can be handed the runtime object or use
 * `startActivityForResult`. They call [LockCompletionBridge] instead, and F binds the runtime to it once
 * during the cutover.
 *
 * The sink mirrors the runtime's request-token completion methods and always carries the FULL
 * [RequestToken] (epoch + id), never a bare id. Thus a completion from a dead process (a foreign epoch)
 * is rejected by the reducer's `matchedRequest`, and never collides with a fresh request's id.
 *
 * A surface MUST NOT dismiss itself after it delivers a completion. The runtime state stays
 * authoritative: on an accepted unlock the reducer emits [Effect.DismissSurface] and the runtime (the
 * sole [LockPresenter] caller) hides the surface. A surface that hid itself would desync the
 * authoritative presentation health fact.
 *
 * Each method returns a [LockCompletionResult]. [LockCompletionResult.ACCEPTED] means a bound sink took
 * the completion. [LockCompletionResult.UNAVAILABLE] means no sink is bound yet, so the completion was
 * DROPPED, never queued: the overlay stays fail-closed (a surface that could not unlock stays up) rather
 * than replaying a stale completion against a later request once F binds.
 */
interface LockCompletionSink {

    /** The user authenticated the lock request [token] with [method] (a PIN or biometric). */
    fun unlockSucceeded(token: RequestToken, method: UnlockMethod): LockCompletionResult

    /** A PIN attempt for the lock request [token] failed. Biometric never uses this method. */
    fun unlockFailed(token: RequestToken, method: UnlockMethod): LockCompletionResult

    /** The user cancelled the biometric prompt for [token]. It never changes the lockout counter. */
    fun biometricCancelled(token: RequestToken): LockCompletionResult

    /**
     * A leave-without-auth request from the live surface [token] to [destination]. [exemptPackages] is
     * the resolved package(s) of an [SafeDestination.OVERLAY_SETTINGS] target (the caller resolves them
     * before dispatching); it is empty, and ignored, for every other destination.
     */
    fun safeDismissRequested(
        token: SurfaceToken,
        destination: SafeDestination,
        exemptPackages: Set<String> = emptySet(),
    ): LockCompletionResult
}

/** The outcome of delivering a completion to the [LockCompletionSink]. Exposed for diagnostics and tests. */
enum class LockCompletionResult {
    /** A bound sink took the completion. */
    ACCEPTED,

    /** No sink is bound, so the completion was dropped (never queued). The overlay stays fail-closed. */
    UNAVAILABLE,
}

/**
 * The process-scoped bridge behind [LockCompletionSink] (M7 WP2 change E). It holds one atomic,
 * initially-unbound delegate. Change F binds the real [LockEngineRuntime] to it exactly once during the
 * DI cutover, and E leaves the runtime itself untouched.
 *
 * ## F's bind contract
 * F calls [bind] once with a thin adapter over the runtime, for example:
 * ```
 * LockCompletionBridge.bind(object : LockCompletionSink {
 *     override fun unlockSucceeded(token: RequestToken, method: UnlockMethod): LockCompletionResult {
 *         runtime.unlockSucceeded(token, method); return LockCompletionResult.ACCEPTED
 *     }
 *     // ... the other three methods forward likewise
 * })
 * ```
 * The runtime's completion methods return `Unit`, so the adapter returns [LockCompletionResult.ACCEPTED]
 * after each forward. The runtime already rejects a stale or foreign-epoch completion internally, so
 * "accepted by the bridge" means "delivered to the runtime", not "acted on by the reducer".
 *
 * ## Guarantees
 * The bind is one-time: a second [bind] returns false and does not replace the delegate. Before a bind,
 * every completion returns [LockCompletionResult.UNAVAILABLE] and is dropped, not buffered. The
 * [AtomicReference] read is lock-free, so a surface on the main thread never blocks on it.
 */
object LockCompletionBridge : LockCompletionSink {

    private val delegate = AtomicReference<LockCompletionSink?>(null)

    /** Whether a sink is bound. Exposed for diagnostics and tests. */
    val isBound: Boolean get() = delegate.get() != null

    /**
     * Binds [sink] as the single delegate. It succeeds (returns true) only for the first call in the
     * process; a later call returns false and leaves the existing delegate in place. Change F calls it
     * once during the cutover.
     */
    fun bind(sink: LockCompletionSink): Boolean = delegate.compareAndSet(null, sink)

    override fun unlockSucceeded(token: RequestToken, method: UnlockMethod): LockCompletionResult =
        dispatch { it.unlockSucceeded(token, method) }

    override fun unlockFailed(token: RequestToken, method: UnlockMethod): LockCompletionResult =
        dispatch { it.unlockFailed(token, method) }

    override fun biometricCancelled(token: RequestToken): LockCompletionResult =
        dispatch { it.biometricCancelled(token) }

    override fun safeDismissRequested(
        token: SurfaceToken,
        destination: SafeDestination,
        exemptPackages: Set<String>,
    ): LockCompletionResult = dispatch { it.safeDismissRequested(token, destination, exemptPackages) }

    /** Delivers to the bound delegate, or drops with [LockCompletionResult.UNAVAILABLE] when unbound. */
    private inline fun dispatch(block: (LockCompletionSink) -> LockCompletionResult): LockCompletionResult =
        delegate.get()?.let(block) ?: LockCompletionResult.UNAVAILABLE

    /** Clears the delegate. TEST-ONLY: the production bind is one-time and process-scoped. */
    internal fun resetForTest() = delegate.set(null)
}
