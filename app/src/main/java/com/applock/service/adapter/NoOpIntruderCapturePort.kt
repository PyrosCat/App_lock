package com.applock.service.adapter

import com.applock.service.engine.IntruderCapturePort
import com.applock.service.engine.UnlockMethod

/**
 * The [IntruderCapturePort] adapter (M7 WP2 change F4). It does nothing. Intruder capture (FR-081 to FR-084) is
 * descoped from 1.0.0, and `IntruderCaptureManager` writes security events, which M7 invariant 6 excludes. The runtime
 * audits each failed unlock separately, through `AuditLog`. F6 disables the intruder UI when the new engine goes
 * live, and M8 removes the feature.
 */
object NoOpIntruderCapturePort : IntruderCapturePort {
    override fun onAuthFailure(packageName: String, method: UnlockMethod, failureCount: Int) = Unit
}
