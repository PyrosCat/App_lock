package com.applock.security

import com.applock.security.harness.VirtualClocks
import com.applock.security.harness.VirtualRecoveryTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The tests of the shared retry scheduler and its timers. The cases are the base tests in
 * `2026-10-07_R007_F2_HARDENING_P3_SCHEDULER_SPEC.md`. Most tests run the writer queue by hand on the test
 * thread. The thread tests use a real single-thread writer named `lockout-io`.
 */
class RecoverySchedulerTest {

    private val lock = Any()
    private val clocks = VirtualClocks()
    private val timer = VirtualRecoveryTimer(clocks)
    private val events = CopyOnWriteArrayList<RecoveryEvent>()
    private val writer = ManualWriter()
    private val scheduler = newScheduler(writer::launch)

    // ---- Delays ----------------------------------------------------------------------------------

    @Test
    fun `the production delays double from 1 s up to the 60 s cap`() {
        val delays = (1..8).map { attempt -> RecoveryBackoff.PRODUCTION.delayFor(attempt) }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), delays)
    }

    @Test
    fun `a large first delay reaches the cap without an overflow`() {
        val backoff = RecoveryBackoff(firstMs = 1L shl 40, capMs = Long.MAX_VALUE)

        assertEquals(Long.MAX_VALUE, backoff.delayFor(40))
    }

    @Test
    fun `a wall-clock jump fires no retry`() {
        start(ScriptedAction())

        clocks.jumpWall(ONE_HOUR_MS)

        assertEquals("no retry fired", 0, timer.fireDue())
        assertEquals(1, timer.pendingCount())
    }

    // ---- One pending retry and stale retries -----------------------------------------------------

    @Test
    fun `a second start replaces the pending retry, and only the second one fires`() {
        val firstAction = ScriptedAction()
        val secondAction = ScriptedAction()
        start(firstAction)
        start(secondAction)

        advance(1_000)
        writer.runAll()

        assertEquals("the replaced retry never prepares", 0, firstAction.prepares)
        assertEquals(
            listOf(
                "SCHEDULED 1 WRITE 1 1000",
                "CANCELLED 1 REPLACED",
                "SCHEDULED 2 WRITE 1 1000",
                "FIRED 2",
                "STARTED 2",
                "ENDED 2 SUCCESS",
            ),
            trace(),
        )
    }

    @Test
    fun `a retry queued before a newer start is ignored without prepare`() {
        val firstAction = ScriptedAction()
        start(firstAction)
        advance(1_000)
        start(ScriptedAction())

        writer.runAll()

        assertEquals("the stale retry never prepares", 0, firstAction.prepares)
        assertEquals(
            listOf(
                "SCHEDULED 1 WRITE 1 1000",
                "FIRED 1",
                "CANCELLED 1 REPLACED",
                "SCHEDULED 2 WRITE 1 1000",
                "IGNORED 1 STALE",
            ),
            trace(),
        )
        assertEquals("the new chain stays pending", 1, timer.pendingCount())
    }

    @Test
    fun `a retry queued before cancel is ignored, and the state stays idle`() {
        val action = ScriptedAction()
        start(action)
        advance(1_000)
        synchronized(lock) { scheduler.cancel() }

        writer.runAll()

        assertEquals(0, action.prepares)
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "CANCELLED 1 CANCELLED", "IGNORED 1 STALE"), trace())
        assertNull(synchronized(lock) { scheduler.pendingKind() })
    }

    @Test
    fun `a retry that prepare no longer wants ends the chain`() {
        val action = ScriptedAction().apply { wanted = false }
        start(action)

        advance(1_000)
        writer.runAll()

        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "IGNORED 1 NOT_WANTED"), trace())
        assertNull(synchronized(lock) { scheduler.pendingKind() })
    }

    @Test
    fun `scheduleNext with a stale sequence number plans nothing`() {
        val staleSequence = start(ScriptedAction())
        start(ScriptedAction())

        val planned = synchronized(lock) { scheduler.scheduleNext(checkNotNull(staleSequence)) }

        assertFalse(planned)
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "CANCELLED 1 REPLACED", "SCHEDULED 2 WRITE 1 1000"), trace())
    }

    // ---- Ownership during a running retry --------------------------------------------------------

    @Test
    fun `a start during a running retry plans the new chain, and the old run ends abandoned without complete`() {
        val replacementAction = ScriptedAction()
        val runningAction = ScriptedAction(io = {
            synchronized(lock) { scheduler.start(RecoveryKind.WRITE, replacementAction) }
            true
        })
        start(runningAction)

        advance(1_000)
        writer.runAll()

        assertEquals("the run without ownership never completes", 0, runningAction.completes)
        assertEquals(
            listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "STARTED 1", "SCHEDULED 2 WRITE 1 1000", "ENDED 1 ABANDONED"),
            trace(),
        )
        assertEquals(RecoveryKind.WRITE, synchronized(lock) { scheduler.pendingKind() })

        advance(1_000)
        writer.runAll()

        assertEquals("the new chain fires at its own delay", 1, replacementAction.completes)
        assertEquals("ENDED 2 SUCCESS", trace().last())
    }

    @Test
    fun `a cancel during a running retry ends the run abandoned and plans nothing`() {
        val runningAction = ScriptedAction(io = {
            synchronized(lock) { scheduler.cancel() }
            false
        })
        start(runningAction)

        advance(1_000)
        writer.runAll()

        assertEquals(0, runningAction.completes)
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "STARTED 1", "ENDED 1 ABANDONED"), trace())
        assertNull(synchronized(lock) { scheduler.pendingKind() })
        assertEquals(0, timer.pendingCount())
    }

    @Test
    fun `stop cancels a pending retry and refuses later chains`() {
        val sequence = start(ScriptedAction())

        synchronized(lock) { scheduler.stop() }

        assertNull("a stopped scheduler refuses a start", start(ScriptedAction()))
        assertFalse(synchronized(lock) { scheduler.scheduleNext(checkNotNull(sequence)) })
        advance(TEN_MINUTES_MS)
        writer.runAll()
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "CANCELLED 1 STOPPED"), trace())
    }

    @Test
    fun `a stop during a running retry lets the run finish without complete, and the state stays stopped`() {
        var ioFinished = false
        val runningAction = ScriptedAction(io = {
            synchronized(lock) { scheduler.stop() }
            ioFinished = true
            true
        })
        start(runningAction)

        advance(1_000)
        writer.runAll()

        assertTrue("the storage operation finishes", ioFinished)
        assertEquals("the run never completes after the stop", 0, runningAction.completes)
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "STARTED 1", "ENDED 1 ABANDONED"), trace())
        assertNull(start(ScriptedAction()))
    }

    // ---- Cancellation rule -----------------------------------------------------------------------

    @Test
    fun `an adapter cancellation while the manager job runs is a failed attempt, and the next delay follows`() {
        val action = ScriptedAction(io = { throw CancellationException("adapter cancellation") })
        start(action)

        advance(1_000)
        writer.runAll()

        assertEquals(listOf(false), action.results)
        assertEquals(
            listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "STARTED 1", "ENDED 1 RETRY", "SCHEDULED 2 WRITE 2 2000"),
            trace(),
        )
    }

    @Test
    fun `a cancellation of the manager job that the storage operation rethrows ends the chain without a retry`() {
        RealWriter().use { realWriter ->
            val realScheduler = newScheduler(realWriter::launch)
            val action = ScriptedAction(io = {
                realWriter.scope.cancel()
                throw CancellationException("job cancelled")
            })
            synchronized(lock) { realScheduler.start(RecoveryKind.WRITE, action) }

            advance(1_000)
            awaitEvent { it is RecoveryEvent.Ended }

            assertEquals(0, action.completes)
            assertEquals("ENDED 1 THREW", trace().last())
            assertNull(synchronized(lock) { realScheduler.pendingKind() })
            assertEquals(0, timer.pendingCount())
        }
    }

    @Test
    fun `a cancellation of the manager job during a storage operation that then succeeds publishes nothing`() {
        assertJobCancellationEndsTheChain(storageSucceeds = true)
    }

    @Test
    fun `a cancellation of the manager job during a storage operation that then fails plans no retry`() {
        assertJobCancellationEndsTheChain(storageSucceeds = false)
    }

    @Test
    fun `a cancellation of the manager job while the writer waits for the manager lock publishes nothing`() {
        RealWriter().use { realWriter ->
            val realScheduler = newScheduler(realWriter::launch)
            val ioReached = CountDownLatch(1)
            val ioRelease = CountDownLatch(1)
            var writerThread: Thread? = null
            val action = ScriptedAction(io = {
                writerThread = Thread.currentThread()
                ioReached.countDown()
                ioRelease.await(TIMEOUT_S, TimeUnit.SECONDS)
                true
            })
            synchronized(lock) { realScheduler.start(RecoveryKind.WRITE, action) }
            advance(1_000)
            assertTrue(ioReached.await(TIMEOUT_S, TimeUnit.SECONDS))

            synchronized(lock) {
                ioRelease.countDown()
                awaitBlocked(checkNotNull(writerThread))
                realWriter.scope.cancel()
            }
            awaitEvent { it is RecoveryEvent.Ended }

            assertEquals("the run never completes after the cancellation", 0, action.completes)
            assertEquals("ENDED 1 THREW", trace().last())
            assertNull(synchronized(lock) { realScheduler.pendingKind() })
        }
    }

    @Test
    fun `an error from the storage operation ends the chain and clears its state`() {
        val action = ScriptedAction(io = { throw AssertionError("storage error") })
        start(action)

        advance(1_000)
        val thrown = assertThrows(AssertionError::class.java) { writer.runAll() }

        assertEquals("storage error", thrown.message)
        assertEquals(0, action.completes)
        assertEquals(listOf("SCHEDULED 1 WRITE 1 1000", "FIRED 1", "STARTED 1", "ENDED 1 THREW"), trace())
        assertNull(synchronized(lock) { scheduler.pendingKind() })
    }

    // ---- Queue order and threads -----------------------------------------------------------------

    @Test
    fun `a fired retry runs after the writes queued before the firing`() {
        val order = mutableListOf<String>()
        writer.launch { order += "earlier write" }
        val retry = ScriptedAction(io = {
            order += "retry"
            true
        })
        start(retry)

        advance(1_000)
        writer.launch { order += "later write" }
        writer.runAll()

        assertEquals(listOf("earlier write", "retry", "later write"), order)
    }

    @Test
    fun `a retry that falls due during a held write joins the queue behind it and runs after the release`() {
        RealWriter().use { realWriter ->
            val realScheduler = newScheduler(realWriter::launch)
            val order = CopyOnWriteArrayList<String>()
            val writeHeld = CountDownLatch(1)
            val release = CountDownLatch(1)
            val retry = ScriptedAction(io = {
                order += "retry"
                true
            })
            synchronized(lock) { realScheduler.start(RecoveryKind.WRITE, retry) }
            realWriter.launch {
                order += "write begins"
                writeHeld.countDown()
                release.await(TIMEOUT_S, TimeUnit.SECONDS)
                order += "write ends"
            }
            assertTrue(writeHeld.await(TIMEOUT_S, TimeUnit.SECONDS))

            clocks.advance(1_000)
            assertEquals("the firing does not wait for the held write", 1, timer.fireDue())
            assertEquals(listOf("write begins"), order.toList())

            release.countDown()
            awaitEvent { it is RecoveryEvent.Ended }
            assertEquals(listOf("write begins", "write ends", "retry"), order.toList())
        }
    }

    @Test
    fun `a retry fires and queues while a completion callback holds the manager lock`() {
        RealWriter().use { realWriter ->
            val realScheduler = newScheduler(realWriter::launch)
            val lockHeld = CountDownLatch(1)
            val release = CountDownLatch(1)
            synchronized(lock) { realScheduler.start(RecoveryKind.WRITE, ScriptedAction()) }
            realWriter.launch {
                synchronized(lock) {
                    lockHeld.countDown()
                    release.await(TIMEOUT_S, TimeUnit.SECONDS)
                }
            }
            assertTrue(lockHeld.await(TIMEOUT_S, TimeUnit.SECONDS))

            clocks.advance(1_000)
            val firing = thread(name = "firing") { timer.fireDue() }
            firing.join(TimeUnit.SECONDS.toMillis(TIMEOUT_S))

            assertFalse("the firing finishes while the lock is held", firing.isAlive)
            assertTrue("the retry is fired", trace().contains("FIRED 1"))
            assertFalse("the retry waits behind the callback", trace().contains("STARTED 1"))
            release.countDown()
            awaitEvent { it is RecoveryEvent.Ended }
            assertEquals("ENDED 1 SUCCESS", trace().last())
        }
    }

    @Test
    fun `the production timer waits on its own thread while the writer runs other work`() {
        RealWriter().use { realWriter ->
            val productionTimer = ScheduledRecoveryTimer()
            var firedOn: String? = null
            val realScheduler = RecoveryScheduler(
                lock,
                RecoverySetup(productionTimer, RecoveryBackoff(firstMs = 200L, capMs = 200L)) { event ->
                    if (event is RecoveryEvent.Fired) firedOn = Thread.currentThread().name
                    events += event
                },
                realWriter::launch,
            )
            val order = CopyOnWriteArrayList<String>()
            val retry = ScriptedAction(io = {
                order += "retry on ${Thread.currentThread().name.substringBefore(" @")}"
                true
            })
            synchronized(lock) { realScheduler.start(RecoveryKind.READ, retry) }
            realWriter.launch { order += "writer work during the delay" }

            awaitEvent { it is RecoveryEvent.Ended }

            assertEquals(ScheduledRecoveryTimer.THREAD_NAME, firedOn)
            assertEquals(listOf("writer work during the delay", "retry on lockout-io"), order.toList())
            synchronized(lock) { realScheduler.stop() }
        }
    }

    @Test
    fun `a manager that starts no chain creates no timer thread`() {
        val productionTimer = ScheduledRecoveryTimer()
        val manager = LockoutManager(storage = HealthyStorage(), recovery = RecoverySetup(timer = productionTimer))

        runBlocking {
            manager.recordFailure()
            manager.recordSuccess()
        }
        manager.currentState()
        manager.shutdown()

        assertFalse(productionTimer.started)
    }

    @Test
    fun `the production timer starts its thread at the first schedule call`() {
        val productionTimer = ScheduledRecoveryTimer()

        productionTimer.schedule(TEN_MINUTES_MS) { }

        assertTrue(productionTimer.started)
        productionTimer.stop()
    }

    // ---- Virtual test timer ----------------------------------------------------------------------

    @Test
    fun `a stop from a due task of the virtual timer keeps the other due tasks from running`() {
        val ranTasks = mutableListOf<String>()
        timer.schedule(1_000) {
            ranTasks += "first"
            timer.stop()
        }
        timer.schedule(1_000) { ranTasks += "second" }
        clocks.advance(1_000)

        assertEquals(1, timer.fireDue())
        assertEquals(listOf("first"), ranTasks)
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private fun newScheduler(launch: (suspend () -> Unit) -> Unit): RecoveryScheduler =
        RecoveryScheduler(lock, RecoverySetup(timer, RecoveryBackoff.PRODUCTION) { event -> events += event }, launch)

    private fun start(action: RecoveryAction): Long? =
        synchronized(lock) { scheduler.start(RecoveryKind.WRITE, action) }

    private fun advance(ms: Long) {
        clocks.advance(ms)
        timer.fireDue()
    }

    // The storage operation of the retry cancels the manager job and then returns normally with [storageSucceeds].
    private fun assertJobCancellationEndsTheChain(storageSucceeds: Boolean) {
        RealWriter().use { realWriter ->
            val realScheduler = newScheduler(realWriter::launch)
            val action = ScriptedAction(io = {
                realWriter.scope.cancel()
                storageSucceeds
            })
            synchronized(lock) { realScheduler.start(RecoveryKind.WRITE, action) }

            advance(1_000)
            awaitEvent { it is RecoveryEvent.Ended }

            assertEquals("the run never completes after the cancellation", 0, action.completes)
            assertEquals("ENDED 1 THREW", trace().last())
            assertNull(synchronized(lock) { realScheduler.pendingKind() })
            assertEquals("no retry is planned", 0, timer.pendingCount())
        }
    }

    private fun trace(): List<String> = events.map { event ->
        when (event) {
            is RecoveryEvent.Scheduled -> "SCHEDULED ${event.sequence} ${event.kind} ${event.attempt} ${event.delayMs}"
            is RecoveryEvent.Fired -> "FIRED ${event.sequence}"
            is RecoveryEvent.Cancelled -> "CANCELLED ${event.sequence} ${event.reason}"
            is RecoveryEvent.Ignored -> "IGNORED ${event.sequence} ${event.reason}"
            is RecoveryEvent.Started -> "STARTED ${event.sequence}"
            is RecoveryEvent.Ended -> "ENDED ${event.sequence} ${event.outcome}"
        }
    }

    // Waits until [thread] blocks on a monitor, here the manager lock that the test thread holds.
    private fun awaitBlocked(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (thread.state != Thread.State.BLOCKED) {
            check(System.nanoTime() < deadline) { "the writer never blocked on the manager lock" }
            Thread.sleep(1L)
        }
    }

    private fun awaitEvent(predicate: (RecoveryEvent) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (events.none(predicate)) {
            check(System.nanoTime() < deadline) { "no matching recovery event in ${trace()}" }
            Thread.sleep(1L)
        }
    }

    /** A retry action whose operation runs [io] and decides the next step from its result. */
    private class ScriptedAction(
        private val io: () -> Boolean = { true },
        private val decide: (Boolean) -> RecoveryStep = { succeeded ->
            if (succeeded) RecoveryStep.SUCCESS else RecoveryStep.RETRY
        },
    ) : RecoveryAction {
        var wanted = true
        var prepares = 0
        val results = CopyOnWriteArrayList<Boolean>()
        val completes: Int get() = results.size

        override fun prepare(): RecoveryOperation? {
            prepares++
            if (!wanted) return null
            return object : RecoveryOperation {
                override fun perform(): Boolean = io()

                override fun complete(succeeded: Boolean): RecoveryStep {
                    results += succeeded
                    return decide(succeeded)
                }
            }
        }
    }

    /** A writer queue that the test runs by hand, in order, on the test thread. */
    private class ManualWriter {
        private val queue = ArrayDeque<suspend () -> Unit>()

        fun launch(block: suspend () -> Unit) {
            synchronized(queue) { queue.addLast(block) }
        }

        fun runAll() {
            while (true) {
                val block = synchronized(queue) { queue.removeFirstOrNull() } ?: return
                runBlocking { block() }
            }
        }
    }

    /** A real single-thread writer named `lockout-io`, as the manager's default writer. */
    private class RealWriter : AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "lockout-io").apply { isDaemon = true }
        }
        val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

        fun launch(block: suspend () -> Unit) {
            scope.launch { block() }
        }

        override fun close() {
            scope.cancel()
            executor.shutdownNow()
        }
    }

    private class HealthyStorage : LockoutStorage {
        private var stored = LockoutSnapshot(0, 0L)

        override fun read(): LockoutSnapshot = stored

        override fun write(snapshot: LockoutSnapshot): Boolean {
            stored = snapshot
            return true
        }
    }

    private companion object {
        const val TIMEOUT_S = 5L
        const val ONE_HOUR_MS = 60 * 60_000L
        const val TEN_MINUTES_MS = 10 * 60_000L
    }
}
