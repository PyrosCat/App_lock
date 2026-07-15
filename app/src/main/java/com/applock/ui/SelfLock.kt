package com.applock.ui

/**
 * Tracks whether App Lock's own UI must re-show the self-gate on return to the
 * foreground (FR-108: the vault — reachable only from behind the gate — must
 * not be accessible without authentication). Without this, the Compose nav
 * state survives backgrounding/resume, so a user who unlocked once could
 * background the app and resume straight into the vault.
 *
 * [suppressNextBackground] carves out the one legitimate self-initiated
 * background: the Storage Access Framework picker used for vault import/export.
 * Callers set it immediately before launching the picker; the lifecycle
 * observer consumes it on the resulting stop so returning from the picker does
 * not bounce the user to the gate. It is cleared again on resume so a stale
 * flag can never swallow a real background.
 */
object SelfLock {

    @Volatile
    var suppressNextBackground: Boolean = false
}
