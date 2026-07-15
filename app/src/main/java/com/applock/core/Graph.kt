package com.applock.core

import android.content.Context
import com.applock.applocker.engine.ApplicationLockEngine
import com.applock.applocker.policy.LockPolicyManager
import com.applock.applocker.session.LockSessionManager
import com.applock.core.database.AppLockDatabase
import com.applock.core.security.CredentialRepository
import com.applock.core.security.EncryptedFileStore
import com.applock.core.security.EncryptedPrefsLockoutStorage
import com.applock.core.security.LockoutManager
import com.applock.privacy.IntruderCaptureManager
import com.applock.privacy.IntruderPolicy
import com.applock.vault.VaultRepository
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

    val encryptedFileStore: EncryptedFileStore by lazy { EncryptedFileStore(appContext) }

    val intruderCaptureManager: IntruderCaptureManager by lazy {
        IntruderCaptureManager(
            context = appContext,
            policy = IntruderPolicy(
                enabled = { settings.intruderCaptureEnabled },
                threshold = { settings.intruderCaptureThreshold },
            ),
            intruderEventDao = database.intruderEventDao(),
            securityEventDao = database.securityEventDao(),
            fileStore = encryptedFileStore,
            scope = appScope,
        )
    }

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(
            context = appContext,
            vaultItemDao = database.vaultItemDao(),
            fileStore = encryptedFileStore,
        )
    }

    val lockEngine: ApplicationLockEngine by lazy {
        ApplicationLockEngine(
            context = appContext,
            policyManager = policyManager,
            sessionManager = sessionManager,
            lockoutManager = lockoutManager,
            securityEventDao = database.securityEventDao(),
            intruderCapture = intruderCaptureManager,
            scope = appScope,
        )
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        // Warm the policy cache so the accessibility service gets fast answers.
        policyManager.startCaching()
    }
}
