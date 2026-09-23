package com.applock.service.adapter

import com.applock.infrastructure.logging.AppLogger
import com.applock.service.engine.RuntimeDiagnostics

/**
 * The real [RuntimeDiagnostics] (M7 WP2 change F4). It writes each report as one warning to the project log. The
 * callers pass a port name and a stable reason, never a package name, so the log holds no user data.
 *
 * It is thread-safe: it keeps no state, and each report is one [AppLogger] call. It never throws. A logger failure is
 * dropped, because this sink is the last place that a failure can go.
 *
 * A report must not wait, because the runtime reports some failures while it holds its lifecycle lock. The sink meets
 * this through the best-effort [AppLogger] contract.
 */
class LoggingRuntimeDiagnostics(private val logger: AppLogger) : RuntimeDiagnostics {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // contract: the sink must never throw
    override fun report(port: String, reason: String) {
        try {
            logger.warn(TAG, "$port: $reason")
        } catch (e: Exception) {
            // the log itself failed; nothing is left that could record it
        }
    }

    private companion object {
        const val TAG = "LockEngineDiagnostics"
    }
}
