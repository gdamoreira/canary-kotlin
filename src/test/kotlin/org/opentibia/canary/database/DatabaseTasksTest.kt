package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseTasksTest {

    private val executors = mutableListOf<ExecutorService>()
    private var databaseTasks: DatabaseTasks? = null

    @AfterEach
    fun tearDown() {
        databaseTasks?.shutdown()
        databaseTasks = null
        executors.forEach { it.shutdownNow() }
        executors.clear()
    }

    private fun createTasks(): DatabaseTasks {
        val executor = Executors.newSingleThreadExecutor()
        executors.add(executor)
        return DatabaseTasks(executor).also { databaseTasks = it }
    }

    @Test
    fun `executes submitted tasks sequentially in submission order`() {
        val tasks = createTasks()
        val executed = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(5)

        for (i in 1..5) {
            tasks.addTask("task-$i") {
                executed.add("task-$i")
                done.countDown()
            }
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(executed).containsExactly("task-1", "task-2", "task-3", "task-4", "task-5")
    }

    @Test
    fun `flush drains pending tasks on the calling thread while the worker is busy`() {
        val tasks = createTasks()
        val executed = CopyOnWriteArrayList<String>()
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val workerFinished = CountDownLatch(1)
        val testThread = Thread.currentThread()
        var flushThread: Thread? = null

        tasks.addTask("blocking") {
            workerStarted.countDown()
            releaseWorker.await(5, TimeUnit.SECONDS)
            executed.add("blocking")
            workerFinished.countDown()
        }
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue()

        tasks.addTask("pending-1") { executed.add("pending-1") }
        tasks.addTask("pending-2") {
            flushThread = Thread.currentThread()
            executed.add("pending-2")
        }

        tasks.flush()

        assertThat(executed).containsExactly("pending-1", "pending-2")
        assertThat(flushThread).isSameAs(testThread)

        releaseWorker.countDown()
        assertThat(workerFinished.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(executed).containsExactly("pending-1", "pending-2", "blocking")
    }

    @Test
    fun `shutdown rejects new tasks and drains pending tasks`() {
        val tasks = createTasks()
        val executed = CopyOnWriteArrayList<String>()
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val pendingExecuted = CountDownLatch(1)
        val rejectedExecuted = AtomicBoolean(false)

        tasks.addTask("blocking") {
            workerStarted.countDown()
            releaseWorker.await(5, TimeUnit.SECONDS)
            executed.add("blocking")
        }
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue()

        tasks.addTask("pending") {
            executed.add("pending")
            pendingExecuted.countDown()
        }

        val shutdownThread = Thread { tasks.shutdown() }
        shutdownThread.start()

        assertThat(pendingExecuted.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(executed).containsExactly("pending")

        tasks.addTask("rejected") { rejectedExecuted.set(true) }

        releaseWorker.countDown()
        shutdownThread.join(5000)

        assertThat(shutdownThread.isAlive).isFalse()
        assertThat(rejectedExecuted.get()).isFalse()
        assertThat(executed).containsExactly("pending", "blocking")
    }

    @Test
    fun `double shutdown is safe and idempotent`() {
        val tasks = createTasks()
        val executed = CopyOnWriteArrayList<String>()

        tasks.addTask("only") { executed.add("only") }

        tasks.shutdown()
        tasks.shutdown()

        assertThat(executed).containsExactly("only")

        tasks.addTask("after") { executed.add("after") }

        assertThat(executed).containsExactly("only")
    }
}
