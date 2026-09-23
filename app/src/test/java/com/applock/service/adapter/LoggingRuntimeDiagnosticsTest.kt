package com.applock.service.adapter

import com.applock.infrastructure.logging.RecordingAppLogger
import com.applock.infrastructure.logging.RecordingAppLogger.Entry
import com.applock.infrastructure.logging.RecordingAppLogger.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [LoggingRuntimeDiagnostics] (M7 WP2 change F4): the log format and the no-throw contract. */
class LoggingRuntimeDiagnosticsTest {

    @Test
    fun `a report is written as one warning with the port and the reason`() {
        val logger = RecordingAppLogger()
        LoggingRuntimeDiagnostics(logger).report("audit", "IllegalStateException")
        assertEquals(
            listOf(Entry(Level.WARN, "LockEngineDiagnostics", "audit: IllegalStateException")),
            logger.entries,
        )
    }

    @Test
    fun `a report never throws, also when the log throws`() {
        val logger = RecordingAppLogger().apply { failure = IllegalStateException("log down") }
        LoggingRuntimeDiagnostics(logger).report("timer_schedule", "rejected") // returns normally
        assertTrue(logger.entries.isEmpty())
    }
}
