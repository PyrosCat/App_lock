package com.applock.r007

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.applock.security.EncryptedPrefsLockoutStorage
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The R-007 fresh-process inspector (test plan phase P1). It reads the persisted lockout pair through its own
 * [EncryptedPrefsLockoutStorage] and reports only the count, the wall deadline, the read error if any, the process
 * id, the boot id, and both clocks. It never writes.
 *
 * `am instrument` stops the app first, so the inspector reads in a new process, and the preferences file is loaded
 * from disk, not from an old in-process cache. The app's debug fault wrapper logs every graph storage operation
 * with its process id, so the host can show that no graph operation ran in this process before the inspection.
 *
 * Host command (the controller in `scripts/r007/` wraps it):
 * `am instrument -w -r -e r007 inspect -e class com.applock.r007.LockoutStoreInspector
 * <test-package>/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
@R007DeviceTool
class LockoutStoreInspector {

    @Test
    @Suppress("TooGenericExceptionCaught") // a read fault is the evidence to report, not a test failure
    fun inspect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requested = InstrumentationRegistry.getArguments().getString(ARG) == "inspect"
        assumeTrue("runs only on request: -e r007 inspect", requested)

        val status = Bundle().apply {
            putString("r007_pid", Process.myPid().toString())
            putString("r007_boot_id", bootId())
            putString("r007_wall", System.currentTimeMillis().toString())
            putString("r007_elapsed", SystemClock.elapsedRealtime().toString())
        }
        Log.i(LOG_TAG, "pid=${Process.myPid()} phase=INSPECT_BEGIN")
        try {
            val snapshot = EncryptedPrefsLockoutStorage(instrumentation.targetContext).read()
            status.putString("r007_count", snapshot.failureCount.toString())
            status.putString("r007_lockout_until", snapshot.lockoutUntil.toString())
        } catch (e: Exception) {
            status.putString("r007_read_error", e.javaClass.name)
        }
        val fields = status.keySet().sorted().joinToString(" ") { "$it=${status.getString(it)}" }
        Log.i(LOG_TAG, "pid=${Process.myPid()} phase=INSPECT_END $fields")
        instrumentation.sendStatus(STATUS_CODE, status)
    }

    private fun bootId(): String =
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown")

    private companion object {
        const val ARG = "r007"
        const val LOG_TAG = "R007Inspect"

        // A status code outside the values AndroidJUnitRunner reports (1, 0, -1 to -4).
        const val STATUS_CODE = 7
    }
}
