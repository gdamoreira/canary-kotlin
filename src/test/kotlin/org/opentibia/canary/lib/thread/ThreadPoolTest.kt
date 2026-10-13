package org.opentibia.canary.lib.thread

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.lib.di.DI
import org.opentibia.canary.lib.di.Injector
import org.opentibia.canary.lib.logging.InMemoryLogger
import org.opentibia.canary.lib.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ThreadPoolTest {

    @AfterEach
    fun restoreContainer() {
        DI.setTestContainer(null)
    }

    @Test
    fun `start logs the resolved thread count`() {
        val logger = InMemoryLogger()
        val pool = ThreadPool(logger, 2)
        try {
            assertThat(pool.getThreadCount()).isEqualTo(2)
            assertThat(logger.hasLogEntry("info", "Running with 2 threads.")).isTrue()
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `a thread count of zero falls back to at least four threads`() {
        val pool = ThreadPool(InMemoryLogger(), 0)
        try {
            assertThat(pool.getThreadCount())
                .isEqualTo(maxOf(Runtime.getRuntime().availableProcessors(), 4))
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `detach task executes asynchronously`() {
        val pool = ThreadPool(InMemoryLogger(), 2)
        try {
            val latch = CountDownLatch(1)
            pool.detachTask { latch.countDown() }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `submit task returns the task result`() {
        val pool = ThreadPool(InMemoryLogger(), 2)
        try {
            val result = pool.submitTask { 21 * 2 }.get(5, TimeUnit.SECONDS)

            assertThat(result).isEqualTo(42)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `submit loop visits every index exactly once`() {
        val pool = ThreadPool(InMemoryLogger(), 4)
        val visited = ConcurrentHashMap.newKeySet<Long>()
        val duplicate = AtomicBoolean(false)
        try {
            val future = pool.submitLoop(1, 1001) { index ->
                if (!visited.add(index)) {
                    duplicate.set(true)
                }
            }
            future.get(10, TimeUnit.SECONDS)

            assertThat(visited).hasSize(1000)
            assertThat(duplicate).isFalse()
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `wait for tasks drains the queued work`() {
        val pool = ThreadPool(InMemoryLogger(), 1)
        val counter = AtomicInteger()
        try {
            repeat(50) { pool.detachTask { counter.incrementAndGet() } }

            pool.waitForTasks()

            assertThat(counter.get()).isEqualTo(50)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `shutdown is idempotent and logs the lifecycle in order`() {
        val events = mutableListOf<String>()
        val logger = object : Logger() {
            override fun setLevel(name: String) = Unit
            override fun getLevel(): String = "DEBUG"
            override fun info(msg: String) {
                events += msg
            }

            override fun warn(msg: String) = Unit
            override fun error(msg: String) = Unit
            override fun critical(msg: String) = Unit
        }
        var requestedShutdown = false
        val pool = ThreadPool(logger, 1) { requestedShutdown = true }

        pool.shutdown()
        pool.shutdown()

        assertThat(pool.isStopped()).isTrue()
        assertThat(requestedShutdown).isTrue()
        assertThat(events).containsExactly(
            "Running with 1 threads.",
            "Shutting down thread pool...",
            "Thread pool shutdown complete.",
        )
    }

    @Test
    fun `shutdown rejects new tasks`() {
        val pool = ThreadPool(InMemoryLogger(), 1)
        pool.shutdown()

        assertThatThrownBy { pool.detachTask {} }
            .isInstanceOf(RejectedExecutionException::class.java)
    }

    @Test
    fun `worker threads use the canary worker name`() {
        val pool = ThreadPool(InMemoryLogger(), 1)
        try {
            val name = pool.submitTask { Thread.currentThread().name }.get(5, TimeUnit.SECONDS)

            assertThat(name).startsWith("canary-wrk-")
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `set current thread name truncates to the native limit`() {
        val thread = Thread.currentThread()
        val originalName = thread.name
        try {
            ThreadPool.setCurrentThreadName("a-very-long-thread-name")

            assertThat(thread.name).isEqualTo("a-very-long-thr")
            assertThat(thread.name.length).isLessThanOrEqualTo(15)
        } finally {
            thread.name = originalName
        }
    }

    @Test
    fun `get thread id is stable for a thread and increases across threads`() {
        val pool = ThreadPool(InMemoryLogger(), 1)
        try {
            val pair = pool.submitTask { ThreadPool.getThreadId() to ThreadPool.getThreadId() }.get(5, TimeUnit.SECONDS)

            assertThat(pair.first).isEqualTo(pair.second)
        } finally {
            pool.shutdown()
        }

        var first: Short = -1
        val threadOne = Thread { first = ThreadPool.getThreadId() }
        threadOne.start()
        threadOne.join()

        var second: Short = -1
        val threadTwo = Thread { second = ThreadPool.getThreadId() }
        threadTwo.start()
        threadTwo.join()

        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `get instance resolves the container instance`() {
        val pool = ThreadPool(InMemoryLogger(), 1)
        try {
            val injector = Injector().bind(ThreadPool::class) { pool }
            DI.setTestContainer(injector)

            assertThat(ThreadPool.getInstance()).isSameAs(pool)
        } finally {
            pool.shutdown()
        }
    }
}
