package com.applock.di

import android.content.Context
import com.applock.data.AppLockDatabase
import com.applock.data.IntruderEventDao
import com.applock.data.ProtectedAppDao
import com.applock.data.SecurityEventDao
import com.applock.data.SettingsRepository
import com.applock.data.VaultItemDao
import com.applock.data.VaultRepository
import com.applock.domain.IntruderPolicy
import com.applock.domain.LockPolicyManager
import com.applock.domain.LockSessionManager
import com.applock.security.CredentialRepository
import com.applock.security.EncryptedFileStore
import com.applock.security.EncryptedPrefsLockoutStorage
import com.applock.security.LockoutManager
import com.applock.service.ApplicationLockEngine
import com.applock.service.IntruderCaptureManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * WP5 (M1, ADR-015): the Hilt application graph — a 1:1 replacement for the retired
 * `core/Graph` service locator. Every binding reproduces Graph's construction expression
 * exactly (all `@Singleton`, matching Graph's `by lazy` single-instance semantics), so this
 * is a mechanical DI swap with no behavior change. The domain/data classes are intentionally
 * NOT `@Inject`-annotated — construction lives here, keeping them framework-agnostic for the
 * WP6 package moves.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Graph.appScope — the application-wide scope for fire-and-forget work (audit writes, cache
    // refresh). Qualified to disambiguate it from ProtectionWatchdogService's own local scope.
    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppLockDatabase =
        AppLockDatabase.build(context)

    // DAOs are cached by Room per database instance, so these bindings hand out the same
    // instances Graph obtained via database.xDao() — behavior-identical.
    @Provides
    @Singleton
    fun provideProtectedAppDao(database: AppLockDatabase): ProtectedAppDao =
        database.protectedAppDao()

    @Provides
    @Singleton
    fun provideSecurityEventDao(database: AppLockDatabase): SecurityEventDao =
        database.securityEventDao()

    @Provides
    @Singleton
    fun provideIntruderEventDao(database: AppLockDatabase): IntruderEventDao =
        database.intruderEventDao()

    @Provides
    @Singleton
    fun provideVaultItemDao(database: AppLockDatabase): VaultItemDao =
        database.vaultItemDao()

    @Provides
    @Singleton
    fun provideCredentialRepository(@ApplicationContext context: Context): CredentialRepository =
        CredentialRepository(context)

    @Provides
    @Singleton
    fun provideLockoutManager(@ApplicationContext context: Context): LockoutManager =
        LockoutManager(EncryptedPrefsLockoutStorage(context))

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideLockPolicyManager(
        protectedAppDao: ProtectedAppDao,
        @ApplicationScope scope: CoroutineScope,
    ): LockPolicyManager = LockPolicyManager(protectedAppDao, scope)

    // Closure over settings preserved verbatim from Graph — resolved live on every read.
    @Provides
    @Singleton
    fun provideLockSessionManager(settings: SettingsRepository): LockSessionManager =
        LockSessionManager(policyProvider = { settings.relockPolicy })

    @Provides
    @Singleton
    fun provideEncryptedFileStore(@ApplicationContext context: Context): EncryptedFileStore =
        EncryptedFileStore(context)

    // IntruderPolicy's enabled/threshold closures over settings preserved verbatim from Graph.
    // DI provider aggregating IntruderCaptureManager's real dependencies (mirrors Graph).
    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideIntruderCaptureManager(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        intruderEventDao: IntruderEventDao,
        securityEventDao: SecurityEventDao,
        fileStore: EncryptedFileStore,
        @ApplicationScope scope: CoroutineScope,
    ): IntruderCaptureManager = IntruderCaptureManager(
        context = context,
        policy = IntruderPolicy(
            enabled = { settings.intruderCaptureEnabled },
            threshold = { settings.intruderCaptureThreshold },
        ),
        intruderEventDao = intruderEventDao,
        securityEventDao = securityEventDao,
        fileStore = fileStore,
        scope = scope,
    )

    @Provides
    @Singleton
    fun provideVaultRepository(
        @ApplicationContext context: Context,
        vaultItemDao: VaultItemDao,
        fileStore: EncryptedFileStore,
    ): VaultRepository = VaultRepository(
        context = context,
        vaultItemDao = vaultItemDao,
        fileStore = fileStore,
    )

    // DI provider aggregating ApplicationLockEngine's real dependencies (mirrors Graph; the
    // engine's own constructor is likewise baselined for LongParameterList).
    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideApplicationLockEngine(
        @ApplicationContext context: Context,
        policyManager: LockPolicyManager,
        sessionManager: LockSessionManager,
        lockoutManager: LockoutManager,
        securityEventDao: SecurityEventDao,
        intruderCapture: IntruderCaptureManager,
        @ApplicationScope scope: CoroutineScope,
    ): ApplicationLockEngine = ApplicationLockEngine(
        context = context,
        policyManager = policyManager,
        sessionManager = sessionManager,
        lockoutManager = lockoutManager,
        securityEventDao = securityEventDao,
        intruderCapture = intruderCapture,
        scope = scope,
    )
}

/** Qualifies the application-wide [CoroutineScope] (Graph.appScope). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
