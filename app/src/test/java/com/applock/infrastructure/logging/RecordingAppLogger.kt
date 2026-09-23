package com.applock.infrastructure.logging

/**
 * A recording [AppLogger] for JVM tests, which cannot use `android.util.Log`. It keeps every call in order. While
 * [failure] is set, every call throws it, to model a broken log. It is thread-safe.
 */
class RecordingAppLogger : AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(val level: Level, val tag: String, val message: String, val throwable: Throwable? = null)

    @Volatile
    var failure: RuntimeException? = null

    private val recorded = mutableListOf<Entry>()

    /** A snapshot of the calls so far, in call order. */
    val entries: List<Entry>
        get() = synchronized(recorded) { recorded.toList() }

    override fun debug(tag: String, message: String, throwable: Throwable?) =
        record(Level.DEBUG, tag, message, throwable)

    override fun info(tag: String, message: String, throwable: Throwable?) =
        record(Level.INFO, tag, message, throwable)

    override fun warn(tag: String, message: String, throwable: Throwable?) =
        record(Level.WARN, tag, message, throwable)

    override fun error(tag: String, message: String, throwable: Throwable?) =
        record(Level.ERROR, tag, message, throwable)

    private fun record(level: Level, tag: String, message: String, throwable: Throwable?) {
        failure?.let { throw it }
        synchronized(recorded) { recorded += Entry(level, tag, message, throwable) }
    }
}
