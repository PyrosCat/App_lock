package com.applock.domain

import com.applock.data.ProtectedAppDao
import com.applock.data.ProtectedAppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private fun TestScope.eagerScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

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

    @Test
    fun `cache is empty before startCaching`() {
        val dao = FakeProtectedAppDao()
        dao.apps.value = listOf(ProtectedAppEntity(packageName = "com.locked.app"))
        val manager = LockPolicyManager(dao, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        // No startCaching() call - engine must fail closed to "not protected".
        assertEquals(emptySet<String>(), manager.protectedPackages.value)
    }
}
