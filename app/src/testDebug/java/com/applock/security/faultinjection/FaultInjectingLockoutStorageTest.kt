package com.applock.security.faultinjection

import com.applock.security.LockoutSnapshot
import com.applock.security.LockoutStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** JVM tests of the debug-only R-007 fault-injection wrapper and its script parser (test plan phase P1). */
class FaultInjectingLockoutStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val lines = CopyOnWriteArrayList<String>()
    private val held = CountDownLatch(1)

    /** A real store stand-in that records its calls. */
    private class RecordingStore(var value: LockoutSnapshot = LockoutSnapshot(0, 0L)) : LockoutStorage {
        val writes = CopyOnWriteArrayList<LockoutSnapshot>()
        var readError: Exception? = null
        var writeResult = true

        override fun read(): LockoutSnapshot {
            readError?.let { throw it }
            return value
        }

        override fun write(snapshot: LockoutSnapshot): Boolean {
            writes += snapshot
            if (writeResult) value = snapshot
            return writeResult
        }
    }

    private fun wrapper(store: LockoutStorage, controlDir: File = tmp.root): FaultInjectingLockoutStorage =
        FaultInjectingLockoutStorage(
            delegate = store,
            controlDir = controlDir,
            log = { line ->
                lines += line
                if ("phase=HELD" in line) held.countDown()
            },
            pid = PID,
            pollMs = 1L,
        )

    private fun script(text: String) = File(tmp.root, FaultInjectingLockoutStorage.SCRIPT_FILE).writeText(text)

    // ---- Wrapper -------------------------------------------------------------------------------

    @Test
    fun `without a script every operation passes through and is logged`() {
        val store = RecordingStore(LockoutSnapshot(3, 0L))
        val storage = wrapper(store)

        assertEquals(LockoutSnapshot(3, 0L), storage.read())
        assertTrue(storage.write(LockoutSnapshot(4, 0L)))
        assertEquals(listOf(LockoutSnapshot(4, 0L)), store.writes)
        assertTrue(lines.first().contains("op=INIT phase=CREATED script=absent"))
        assertTrue(lines.any { "op=WRITE index=0 phase=REAL_RESULT script=Normal result=true" in it })
        assertTrue(lines.all { it.startsWith("pid=$PID ") })
    }

    @Test
    fun `a rule for an exact index takes precedence over the rule for any index`() {
        script("WRITE * Throw\nWRITE 1 Normal\n")
        val store = RecordingStore()
        val storage = wrapper(store)

        assertThrows(InjectedStorageFault::class.java) { storage.write(LockoutSnapshot(1, 0L)) }
        assertTrue(storage.write(LockoutSnapshot(2, 0L)))
        assertThrows(InjectedStorageFault::class.java) { storage.write(LockoutSnapshot(3, 0L)) }
        assertEquals(listOf(LockoutSnapshot(2, 0L)), store.writes)
    }

    @Test
    fun `an injected false before the commit does not call the real store`() {
        script("WRITE 0 ReturnFalseBeforeCommit")
        val store = RecordingStore()

        assertFalse(wrapper(store).write(LockoutSnapshot(1, 0L)))
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `an injected acknowledgement ambiguity commits and reports false`() {
        script("WRITE 0 CommitThenReportFalse")
        val store = RecordingStore()

        assertFalse(wrapper(store).write(LockoutSnapshot(1, 0L)))
        assertEquals(LockoutSnapshot(1, 0L), store.value)
    }

    @Test
    fun `a held write waits for its release file and deletes it`() {
        script("WRITE 0 HoldBeforeCommit Normal")
        val store = RecordingStore()
        val storage = wrapper(store)
        var result: Boolean? = null
        val writer = Thread { result = storage.write(LockoutSnapshot(1, 0L)) }.apply { start() }

        assertTrue(held.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertTrue("the real store is not called before the release", store.writes.isEmpty())
        val release = File(tmp.root, "${FaultInjectingLockoutStorage.RELEASE_DIR}/${PID}_WRITE_0")
        release.parentFile?.mkdirs()
        release.createNewFile()

        writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_S))
        assertEquals(true, result)
        assertEquals(listOf(LockoutSnapshot(1, 0L)), store.writes)
        assertFalse("the wrapper deletes the release file", release.exists())
    }

    @Test
    fun `a read held after it read returns the value from before the hold`() {
        script("READ 0 ReadThenHold")
        val store = RecordingStore(LockoutSnapshot(5, 9L))
        val storage = wrapper(store)
        var result: LockoutSnapshot? = null
        val reader = Thread { result = storage.read() }.apply { start() }

        assertTrue(held.await(TIMEOUT_S, TimeUnit.SECONDS))
        store.value = LockoutSnapshot(0, 0L)
        File(tmp.root, "${FaultInjectingLockoutStorage.RELEASE_DIR}/${PID}_READ_0").apply {
            parentFile?.mkdirs()
            createNewFile()
        }

        reader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_S))
        assertEquals(LockoutSnapshot(5, 9L), result)
    }

    @Test
    fun `the script is read again when each operation starts`() {
        val store = RecordingStore()
        val storage = wrapper(store)
        assertTrue(storage.write(LockoutSnapshot(1, 0L)))

        script("WRITE * Throw")
        assertThrows(InjectedStorageFault::class.java) { storage.write(LockoutSnapshot(2, 0L)) }
    }

    @Test
    fun `an invalid script is logged and treated as no script`() {
        script("WRITE 0 Explode")
        val store = RecordingStore()

        assertTrue(wrapper(store).write(LockoutSnapshot(1, 0L)))
        assertTrue(lines.any { "op=SCRIPT phase=ERROR" in it && "line 1" in it })
    }

    @Test
    fun `a real read fault is logged and rethrown unchanged`() {
        val store = RecordingStore().apply { readError = SecurityException("bad key") }

        assertThrows(SecurityException::class.java) { wrapper(store).read() }
        assertTrue(lines.any { "phase=REAL_THREW" in it && "java.lang.SecurityException" in it })
    }

    // ---- Script parser -------------------------------------------------------------------------

    @Test
    fun `a hold continues with Normal unless the rule names a continuation`() {
        val script = DeviceFaultScript.parse(
            """
            # comment
            WRITE 0 HoldBeforeCommit

            WRITE 1 HoldBeforeCommit ReturnFalseBeforeCommit
            READ * Throw
            """.trimIndent()
        )
        assertEquals(WriteRule(DeviceWriteScript.HoldBeforeCommit, DeviceWriteScript.Normal), script.write(0))
        assertEquals(
            WriteRule(DeviceWriteScript.HoldBeforeCommit, DeviceWriteScript.ReturnFalseBeforeCommit),
            script.write(1),
        )
        assertEquals(WriteRule(DeviceWriteScript.Normal), script.write(2))
        assertEquals(DeviceReadScript.Throw, script.read(7))
    }

    @Test
    fun `the parser rejects invalid rules and names the line`() {
        val invalid = listOf(
            "WRITE 0 HoldBeforeCommit CommitThenHold",
            "WRITE 0 Normal Throw",
            "READ 0 Throw Normal",
            "READ -1 Throw",
            "DELETE 0 Normal",
            "WRITE 0",
        )
        invalid.forEach { rule ->
            val error = assertThrows(IllegalArgumentException::class.java) { DeviceFaultScript.parse("\n$rule") }
            assertTrue("$rule: ${error.message}", error.message.orEmpty().startsWith("line 2:"))
        }
    }

    private companion object {
        const val PID = 4242
        const val TIMEOUT_S = 5L
    }
}
