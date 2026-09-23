package com.applock.service.adapter

import com.applock.infrastructure.logging.AppLogger
import com.applock.service.engine.AuditEvent
import com.applock.service.engine.AuditLog

/**
 * The real [AuditLog] (M7 WP2 change F4). It writes each audit event as one info line to the project log (logcat).
 * It writes nothing to the database: M7 invariant 6 excludes security-event persistence from 1.0.0.
 *
 * The line holds the event name only, not the package name, so the log holds no timeline of the protected apps that
 * the user opened.
 *
 * Delivery is synchronous: each call writes its line before it returns, so the lines keep the order of the calls.
 * The adapter keeps no state, so it is thread-safe.
 *
 * The port requires [record] to be non-blocking, because the self-gate calls it while it holds the runtime lifecycle
 * lock. The adapter meets this through the best-effort [AppLogger] contract. A logger failure propagates to the
 * caller, where the runtime guard contains it and reports it to `RuntimeDiagnostics`.
 */
class LoggingAuditLog(private val logger: AppLogger) : AuditLog {

    override fun record(event: AuditEvent, packageName: String?) {
        logger.info(TAG, event.name)
    }

    private companion object {
        const val TAG = "LockEngineAudit"
    }
}
