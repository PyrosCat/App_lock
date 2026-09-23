package com.applock.infrastructure.logging

import android.util.Log

/**
 * The production [AppLogger]. It writes to logcat through `android.util.Log`, which is thread-safe. A call without a
 * throwable uses the two-argument `Log` overload, because older API levels append a line break for a null throwable.
 *
 * It meets the best-effort [AppLogger] contract through the platform. liblog writes `android.util.Log` messages to the
 * logd socket in non-blocking mode. When logd is backed up, the write fails with `EAGAIN`, and liblog drops the
 * message and counts it. So a call does not wait for logd, although the time a call takes has no strict limit.
 */
object AndroidAppLogger : AppLogger {
    override fun debug(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
    }
}
