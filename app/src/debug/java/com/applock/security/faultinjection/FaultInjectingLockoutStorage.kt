package com.applock.security.faultinjection

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutStorage
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** An injected storage fault. The lockout manager contains it like a real read or write fault. */
class InjectedStorageFault(message: String) : IllegalStateException(message)

/**
 * The R-007 fault-injection wrapper around the real lockout store, in debug builds only (test plan phase P1).
 *
 * Control: the host writes the script file `faults` (see [DeviceFaultScript]) into [controlDir] with
 * `adb shell run-as`. The wrapper reads the file again when each operation starts, and the operation keeps that
 * script to its end. An operation that holds logs `HELD` and waits until the host creates the release file
 * `release/<pid>_<OP>_<index>`; the wrapper then deletes the file. A hold that is never released models an
 * operation that never returns, and the host kills the process.
 *
 * Evidence: each phase of each operation is one log line of key=value pairs: the process id, the thread, the
 * operation and its index, the phase, the script, the count and the deadline, and the [timestamp]. The wrapper logs
 * no credential data. Without a script file, every operation passes through to [delegate].
 */
class FaultInjectingLockoutStorage(
    private val delegate: LockoutStorage,
    private val controlDir: File,
    private val log: (String) -> Unit,
    private val pid: Int,
    private val timestamp: () -> String = { "" },
    private val pollMs: Long = POLL_MS,
) : LockoutStorage {
    private val reads = AtomicInteger()
    private val writes = AtomicInteger()

    init {
        val script = if (File(controlDir, SCRIPT_FILE).exists()) "present" else "absent"
        log(line("op=INIT phase=CREATED script=$script"))
    }

    override fun read(): LockoutSnapshot {
        val index = reads.getAndIncrement()
        val script = currentScript().read(index)
        val op = Operation(READ, index, script.name)
        op.record("BEGIN")
        return when (script) {
            DeviceReadScript.Normal -> op.returned(op.realRead())
            DeviceReadScript.Throw -> op.inject()
            DeviceReadScript.HoldThenRead -> {
                op.hold()
                op.returned(op.realRead())
            }
            DeviceReadScript.HoldThenThrow -> {
                op.hold()
                op.inject()
            }
            DeviceReadScript.ReadThenHold -> {
                val value = op.realRead()
                op.hold()
                op.returned(value)
            }
        }
    }

    override fun write(snapshot: LockoutSnapshot): Boolean {
        val index = writes.getAndIncrement()
        val rule = currentScript().write(index)
        val op = Operation(WRITE, index, rule.then?.let { "${rule.script.name}+${it.name}" } ?: rule.script.name)
        op.record("BEGIN", snapshot)
        val effective = if (rule.script == DeviceWriteScript.HoldBeforeCommit) {
            op.hold()
            checkNotNull(rule.then)
        } else {
            rule.script
        }
        return when (effective) {
            DeviceWriteScript.Normal -> op.writeReturned(op.realWrite(snapshot))
            DeviceWriteScript.ReturnFalseBeforeCommit -> op.writeReturned(false)
            DeviceWriteScript.Throw -> op.inject()
            DeviceWriteScript.CommitThenHold -> {
                val committed = op.realWrite(snapshot)
                op.hold()
                op.writeReturned(committed)
            }
            DeviceWriteScript.CommitThenReportFalse -> {
                op.realWrite(snapshot)
                op.writeReturned(false)
            }
            DeviceWriteScript.HoldBeforeCommit -> error("a hold does not continue with another hold")
        }
    }

    // A script that cannot be read or parsed is logged and treated as no script, so the error is visible in the
    // evidence instead of being mistaken for an injected fault.
    private fun currentScript(): DeviceFaultScript {
        val file = File(controlDir, SCRIPT_FILE)
        if (!file.exists()) return DeviceFaultScript.EMPTY
        return try {
            DeviceFaultScript.parse(file.readText())
        } catch (e: IllegalArgumentException) {
            log(line("op=SCRIPT phase=ERROR message=\"${e.message}\""))
            DeviceFaultScript.EMPTY
        } catch (e: IOException) {
            log(line("op=SCRIPT phase=ERROR message=\"${e.message}\""))
            DeviceFaultScript.EMPTY
        }
    }

    private fun line(fields: String): String =
        "pid=$pid thread=${Thread.currentThread().name} $fields ${timestamp()}".trimEnd()

    private inner class Operation(val name: String, val index: Int, val script: String) {

        fun record(phase: String, value: LockoutSnapshot? = null, extra: String = "") {
            val snapshot = value?.let { " count=${it.failureCount} until=${it.lockoutUntil}" }.orEmpty()
            log(line("op=$name index=$index phase=$phase script=$script$snapshot$extra"))
        }

        @Suppress("TooGenericExceptionCaught") // a real read fault is logged as evidence, then rethrown unchanged
        fun realRead(): LockoutSnapshot =
            try {
                delegate.read()
            } catch (e: Exception) {
                record("REAL_THREW", extra = " error=${e.javaClass.name}")
                throw e
            }

        @Suppress("TooGenericExceptionCaught") // a real write fault is logged as evidence, then rethrown unchanged
        fun realWrite(snapshot: LockoutSnapshot): Boolean {
            val committed = try {
                delegate.write(snapshot)
            } catch (e: Exception) {
                record("REAL_THREW", extra = " error=${e.javaClass.name}")
                throw e
            }
            record("REAL_RESULT", extra = " result=$committed")
            return committed
        }

        fun hold() {
            val release = File(controlDir, "$RELEASE_DIR/${pid}_${name}_$index")
            record("HELD")
            while (!release.exists()) Thread.sleep(pollMs)
            release.delete()
            record("RELEASED")
        }

        fun returned(value: LockoutSnapshot): LockoutSnapshot {
            record("RETURNED", value)
            return value
        }

        fun writeReturned(result: Boolean): Boolean {
            record("RETURNED", extra = " result=$result")
            return result
        }

        fun inject(): Nothing {
            record("THREW")
            throw InjectedStorageFault("injected fault: $name#$index")
        }
    }

    companion object {
        const val LOG_TAG = "R007Fault"
        const val CONTROL_DIR = "r007"
        const val SCRIPT_FILE = "faults"
        const val RELEASE_DIR = "release"
        private const val READ = "READ"
        private const val WRITE = "WRITE"
        private const val POLL_MS = 20L
    }
}
