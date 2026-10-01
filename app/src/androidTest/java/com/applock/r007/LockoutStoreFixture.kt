package com.applock.r007

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.applock.security.EncryptedPrefsLockoutStorage
import com.applock.security.LockoutSnapshot
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The R-007 fixture writer (test plan phase P2). It writes one stored lockout pair through its own
 * [EncryptedPrefsLockoutStorage] and reports the result of `commit()`, the pair it wrote, the process id, the boot id,
 * and both clocks. The host then checks the pair with [LockoutStoreInspector] in another new process.
 *
 * `am instrument` stops the app first, so no manager of the app writes the store at the same time. The deadline is
 * absolute (`r007_until`) or an offset from the wall clock of the device at the write (`r007_until_offset`), so an
 * active or an expired lockout needs no wrong-PIN ladder. The tool writes nothing when an argument is missing or not
 * valid, and it reports the error instead.
 *
 * Host command (the controller in `scripts/r007/` wraps it):
 * `am instrument -w -r -e r007 fixture -e r007_count <count> -e r007_until_offset <ms>
 * -e class com.applock.r007.LockoutStoreFixture <test-package>/androidx.test.runner.AndroidJUnitRunner`
 */
@RunWith(AndroidJUnit4::class)
@R007DeviceTool
class LockoutStoreFixture {

    @Test
    fun writeFixture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("runs only on request: -e r007 fixture", arguments.getString(ARG) == "fixture")

        val status = Bundle().apply {
            putString("r007_pid", Process.myPid().toString())
            putString("r007_boot_id", bootId())
        }
        Log.i(LOG_TAG, "pid=${Process.myPid()} phase=FIXTURE_BEGIN")
        val wall = System.currentTimeMillis()
        status.putString("r007_wall", wall.toString())
        status.putString("r007_elapsed", SystemClock.elapsedRealtime().toString())
        val request = parse(arguments, wall)
        if (request.error != null) {
            status.putString("r007_error", request.error)
        } else {
            val snapshot = LockoutSnapshot(failureCount = request.count, lockoutUntil = request.until)
            status.putString("r007_count", snapshot.failureCount.toString())
            status.putString("r007_lockout_until", snapshot.lockoutUntil.toString())
            status.putString("r007_commit", EncryptedPrefsLockoutStorage(instrumentation.targetContext)
                .write(snapshot).toString())
        }
        val fields = status.keySet().sorted().joinToString(" ") { "$it=${status.getString(it)}" }
        Log.i(LOG_TAG, "pid=${Process.myPid()} phase=FIXTURE_END $fields")
        instrumentation.sendStatus(STATUS_CODE, status)
    }

    private class Request(val count: Int = 0, val until: Long = 0, val error: String? = null)

    private fun parse(arguments: Bundle, wall: Long): Request {
        val count = arguments.getString(ARG_COUNT)?.toIntOrNull()
        val until = arguments.getString(ARG_UNTIL)
        val offset = arguments.getString(ARG_UNTIL_OFFSET)
        return when {
            count == null || count < 0 -> Request(error = "count")
            (until == null) == (offset == null) -> Request(error = "deadline")
            until != null -> until.toLongOrNull()?.takeIf { it >= 0 }
                ?.let { Request(count, it) } ?: Request(error = "until")
            else -> offset?.toLongOrNull()?.let { wall + it }?.takeIf { it >= 0 }
                ?.let { Request(count, it) } ?: Request(error = "until_offset")
        }
    }

    private fun bootId(): String =
        runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown")

    private companion object {
        const val ARG = "r007"
        const val ARG_COUNT = "r007_count"
        const val ARG_UNTIL = "r007_until"
        const val ARG_UNTIL_OFFSET = "r007_until_offset"
        const val LOG_TAG = "R007Fixture"

        // A status code outside the values AndroidJUnitRunner reports (1, 0, -1 to -4).
        const val STATUS_CODE = 7
    }
}
