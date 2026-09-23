package com.applock.service.adapter

import com.applock.infrastructure.logging.RecordingAppLogger
import com.applock.infrastructure.logging.RecordingAppLogger.Entry
import com.applock.infrastructure.logging.RecordingAppLogger.Level
import com.applock.service.engine.AuditEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** JVM tests for [LoggingAuditLog] (M7 WP2 change F4): the log line, the order, and the failure path. */
class LoggingAuditLogTest {

    @Test
    fun `each event is written as one info line with its name only, in call order`() {
        val logger = RecordingAppLogger()
        val audit = LoggingAuditLog(logger)
        AuditEvent.entries.forEach { audit.record(it, "com.example.bank") }

        // The exact match also shows that no line holds the package name.
        assertEquals(AuditEvent.entries.map { Entry(Level.INFO, "LockEngineAudit", it.name) }, logger.entries)
    }

    @Test
    fun `a logger failure propagates to the caller`() {
        // The runtime guard contains this throw and reports it to RuntimeDiagnostics.
        val failure = IllegalStateException("log down")
        val audit = LoggingAuditLog(RecordingAppLogger().apply { this.failure = failure })
        val thrown = assertThrows(IllegalStateException::class.java) {
            audit.record(AuditEvent.UNLOCK_FAILURE, "com.example.bank")
        }
        assertSame(failure, thrown)
    }
}
