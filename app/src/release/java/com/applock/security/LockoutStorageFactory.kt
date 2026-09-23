package com.applock.security

import android.content.Context

/**
 * The lockout storage of a release build: the encrypted adapter, with nothing around it. The debug source set
 * defines the same function with the R-007 fault-injection wrapper, so release builds contain no test code.
 */
fun lockoutStorage(context: Context): LockoutStorage = EncryptedPrefsLockoutStorage(context)
