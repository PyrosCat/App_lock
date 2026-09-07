package com.applock.domain

import com.applock.data.ProtectedAppDao
import com.applock.data.ProtectedAppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockPolicyManagerTest {

    /** Scope whose collector starts eagerly and processes emissions synchronously. */
    private fun TestScope.eagerScope(job: Job = Job()): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job)

    private class FakeProtectedAppDao : ProtectedAppDao {
        val apps = MutableStateFlow<List<ProtectedAppEntity>>(emptyList())

        override fun observeAll(): Flow<List<ProtectedAppEntity>> = apps

        override fun observeEnabledPackages(): Flow<List<String>> =
            apps.map { list -> list.filter { it.enabled }.map { it.packageName } }

        override suspend fun upsert(app: ProtectedAppEntity) {
            apps.value = apps.value.filterNot { it.packageName == app.packageName } + app
        }

        override suspend fun delete(packageName: String) {
            apps.value = apps.value.filterNot { it.packageName == packageName }
        }
    }

    /** DAO whose observe flow throws [failuresBeforeSuccess] times, then streams [source]. */
    private class FlakyProtectedAppDao(
        private val failuresBeforeSuccess: Int,
        val source: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
    ) : ProtectedAppDao {
        var attempts = 0
            private set

        override fun observeAll(): Flow<List<ProtectedAppEntity>> = MutableStateFlow(emptyList())

        override fun observeEnabledPackages(): Flow<List<String>> = flow {
            attempts++
            if (attempts <= failuresBeforeSuccess) error("simulated store failure #$attempts")
            emitAll(source)
        }

        override suspend fun upsert(app: ProtectedAppEntity) = Unit
        override suspend fun delete(packageName: String) = Unit
    }

    // ---- Synchronous facade (isProtected / evaluate) — unchanged behavior --------------------

    @Test
    fun `protected package is detected after cache warms`() = runTest {
        val dao = FakeProtectedAppDao()
        val manager = LockPolicyManager(dao, eagerScope())
        manager.startCaching()

        dao.upsert(ProtectedAppEntity(packageName = "com.locked.app"))
        advanceUntilIdle()

        assertTrue(manager.isProtected("com.locked.app"))
        assertFalse(manager.isProtected("com.free.app"))
    }

    @Test
    fun `disabled entry is not treated as protected`() = runTest {
        val dao = FakeProtectedAppDao()
        val manager = LockPolicyManager(dao, eagerScope())
        manager.startCaching()

        dao.upsert(ProtectedAppEntity(packageName = "com.locked.app", enabled = false))
        advanceUntilIdle()

        assertFalse(manager.isProtected("com.locked.app"))
    }

    @Test
    fun `unprotecting an app removes it from the cache`() = runTest {
        val dao = FakeProtectedAppDao()
        val manager = LockPolicyManager(dao, eagerScope())
        manager.startCaching()

        dao.upsert(ProtectedAppEntity(packageName = "com.locked.app"))
        advanceUntilIdle()
        assertTrue(manager.isProtected("com.locked.app"))

        dao.delete("com.locked.app")
        advanceUntilIdle()
        assertFalse(manager.isProtected("com.locked.app"))
    }

    @Test
    fun `evaluate requires auth only for protected apps without a session`() = runTest {
        val dao = FakeProtectedAppDao()
        val manager = LockPolicyManager(dao, eagerScope())
        manager.startCaching()
        dao.upsert(ProtectedAppEntity(packageName = "com.locked.app"))
        advanceUntilIdle()

        assertTrue(manager.evaluate("com.locked.app", hasValidSession = false).requiresAuthentication)
        assertFalse(manager.evaluate("com.locked.app", hasValidSession = true).requiresAuthentication)
        assertFalse(manager.evaluate("com.free.app", hasValidSession = false).requiresAuthentication)
    }

    // ---- PolicyState lifecycle (R-005 readiness model, M7 WP2 Phase 0) ------------------------

    @Test
    fun `state is Loading and nothing is protected before startCaching`() = runTest {
        val dao = FakeProtectedAppDao()
        dao.apps.value = listOf(ProtectedAppEntity(packageName = "com.locked.app"))
        val manager = LockPolicyManager(dao, eagerScope())

        // Before startCaching() the state is Loading ("unknown", not Ready(empty)). The synchronous
        // facade still reads "not protected" in this window: the pre-WP2 cold-start fail-OPEN
        // (R-005), preserved here on purpose. The fail-secure hold is engine-owned readiness work in
        // a later WP2 phase.
        assertEquals(PolicyState.Loading, manager.state.value)
        assertFalse(manager.isProtected("com.locked.app"))
    }

    @Test
    fun `first emission publishes Ready atomically with the package set`() = runTest {
        val dao = FakeProtectedAppDao()
        dao.apps.value = listOf(ProtectedAppEntity(packageName = "com.locked.app"))
        val manager = LockPolicyManager(dao, eagerScope())

        manager.startCaching()
        advanceUntilIdle()

        assertEquals(PolicyState.Ready(setOf("com.locked.app")), manager.state.value)
    }

    @Test
    fun `a legitimate empty first emission is Ready(empty), not Loading`() = runTest {
        val dao = FakeProtectedAppDao() // empty
        val manager = LockPolicyManager(dao, eagerScope())

        manager.startCaching()
        advanceUntilIdle()

        assertEquals(PolicyState.Ready(emptySet()), manager.state.value)
    }

    @Test
    fun `startCaching is idempotent`() = runTest {
        val dao = FakeProtectedAppDao()
        dao.apps.value = listOf(ProtectedAppEntity(packageName = "com.locked.app"))
        val manager = LockPolicyManager(dao, eagerScope())

        manager.startCaching()
        manager.startCaching() // no-op: does not start a second collector or crash
        advanceUntilIdle()

        assertEquals(PolicyState.Ready(setOf("com.locked.app")), manager.state.value)
    }

    @Test
    fun `a load error publishes Failed then recovers to Ready on retry`() = runTest {
        val dao = FlakyProtectedAppDao(failuresBeforeSuccess = 1)
        dao.source.value = listOf("com.locked.app")
        val manager = LockPolicyManager(dao, eagerScope())

        manager.startCaching()
        advanceUntilIdle() // first attempt throws -> Failed -> backoff -> retry succeeds

        assertEquals(2, dao.attempts)
        assertEquals(PolicyState.Ready(setOf("com.locked.app")), manager.state.value)
    }

    @Test
    fun `coroutine cancellation is not reported as Failed`() = runTest {
        val dao = FakeProtectedAppDao() // MutableStateFlow: never completes, never throws
        val job = Job()
        val manager = LockPolicyManager(dao, eagerScope(job))

        manager.startCaching()
        advanceUntilIdle()
        assertEquals(PolicyState.Ready(emptySet()), manager.state.value)

        job.cancel() // only the cancellation can end the collect
        advanceUntilIdle()

        assertFalse(manager.state.value is PolicyState.Failed)
    }
}
