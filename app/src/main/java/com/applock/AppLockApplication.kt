package com.applock

import android.app.Application
import com.applock.domain.LockPolicyManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AppLockApplication : Application() {

    // Injected so the startup policy-cache warm-up (formerly Graph.init) still fires on launch —
    // this also triggers the eager DB build via the injected LockPolicyManager, exactly as before.
    @Inject
    lateinit var policyManager: LockPolicyManager

    override fun onCreate() {
        super.onCreate()
        policyManager.startCaching()
    }
}
