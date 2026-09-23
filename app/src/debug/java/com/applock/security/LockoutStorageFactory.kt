package com.applock.security

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.applock.security.faultinjection.FaultInjectingLockoutStorage
import java.io.File

/**
 * The lockout storage of a debug build: the encrypted adapter inside the R-007 fault-injection wrapper. Without a
 * fault script in `files/r007/`, the wrapper passes every operation through and only logs it. The release source
 * set defines the same function without the wrapper.
 */
fun lockoutStorage(context: Context): LockoutStorage =
    FaultInjectingLockoutStorage(
        delegate = EncryptedPrefsLockoutStorage(context),
        controlDir = File(context.filesDir, FaultInjectingLockoutStorage.CONTROL_DIR),
        log = { Log.i(FaultInjectingLockoutStorage.LOG_TAG, it) },
        pid = Process.myPid(),
        timestamp = { "wall=${System.currentTimeMillis()} elapsed=${SystemClock.elapsedRealtime()}" },
    )
