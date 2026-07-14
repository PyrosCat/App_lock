package com.applock.core

import android.content.Context
import com.applock.applocker.engine.ApplicationLockEngine
import com.applock.applocker.policy.LockPolicyManager
import com.applock.applocker.session.LockSessionManager
import com.applock.core.database.AppLockDatabase
import com.applock.core.security.CredentialRepository
import com.applock.core.security.EncryptedPrefsLockoutStorage
import com.applock.core.security.LockoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal service locator. Deliberately simple for Phase 1 —
 * can be replaced with Hilt later without touching call sites much.
 */
object Graph {

    lateinit var appContext: Context
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppLockDatabase by lazy { AppLockDatabase.build(appContext) }

    val credentialRepository: CredentialRepository by lazy { CredentialRepository(appContext) }

    val lockoutManager: LockoutManager by lazy {
        LockoutManager(EncryptedPrefsLockoutStorage(appContext))
    }

    val policyManager: LockPolicyManager by lazy {
        LockPolicyManager(database.protectedAppDao(), appScope)
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val sessionManager: LockSessionManager by lazy {
        LockSessionManager(policyProvider = { settings.relockPolicy })
    }

    val lockEngine: ApplicationLockEngine by lazy {
        ApplicationLockEngine(
            context = appContext,
            policyManager = policyManager,
            sessionManager = sessionManager,
            lockoutManager = lockoutManager,
            securityEventDao = database.securityEventDao(),
            scope = appScope,
        )
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        // Warm the policy cache so the accessibility service gets fast answers.
        policyManager.startCaching()
    }
}
