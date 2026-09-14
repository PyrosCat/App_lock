package com.applock.platform.lock

/**
 * The E/F reveal gate for the overlay-permission capability (M7 WP2 change E). E authors the overlay
 * grant-path UI (the onboarding card and the settings entry) but keeps it hidden, because production still
 * runs the legacy accessibility detector and `LockScreenActivity`, which need no overlay grant. Showing a
 * sensitive "display over other apps" request while the Activity still provides protection would be
 * premature and confusing.
 *
 * F flips [uiEnabled] to true as part of the grant-before-cutover flow: it reveals the affordance, gets the
 * grant, and only then enables overlay enforcement (Decision D-P2-2). It must not cut an already-protected
 * user over until the grant is present, and may keep the legacy path during that migration window. It is a
 * plain `val` (not a `const`) so the guarded UI is not constant-folded away at compile time.
 */
object OverlayEnforcement {
    /** Whether the overlay grant-path UI is shown. E returns false (authored but hidden); F flips it to true. */
    val uiEnabled: Boolean get() = false
}
