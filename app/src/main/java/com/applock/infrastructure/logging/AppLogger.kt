package com.applock.infrastructure.logging

/**
 * The project logging interface (ADR-008). Code logs through this interface, not through `android.util.Log`, so the
 * central logging service can later replace the implementation without changes at the call sites. Production binds
 * [AndroidAppLogger] in `di/AppModule`. JVM tests pass a fake, because `android.util.Log` throws in JVM unit tests.
 *
 * Each level maps to one `android.util.Log` level, with the same arguments: [debug] to `Log.d`, [info] to `Log.i`,
 * [warn] to `Log.w`, and [error] to `Log.e`.
 *
 * Logging is best effort. Callers use it on the UI thread and while they hold locks, so every implementation:
 *  - MUST be thread-safe.
 *  - MUST NOT wait for sink capacity or for delivery to complete.
 *  - MUST drop a message under backpressure.
 *
 * An implementation that needs buffering or slow I/O, such as the later central logging service, queues the work
 * internally. The contract rules out waiting. It does not set a strict limit on how long a call takes.
 */
interface AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)

    fun info(tag: String, message: String, throwable: Throwable? = null)

    fun warn(tag: String, message: String, throwable: Throwable? = null)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}
