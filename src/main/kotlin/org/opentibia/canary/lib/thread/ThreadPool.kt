package org.opentibia.canary.lib.thread

import org.opentibia.canary.lib.di.inject
import org.opentibia.canary.lib.logging.Logger
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Port of Canary's `ThreadPool` (src/lib/thread/thread_pool.hpp:16-82,
 * src/lib/thread/thread_pool.cpp:44-131), backed by a fixed-size
 * [ThreadPoolExecutor] with an unbounded FIFO queue (the `BS::thread_pool`
 * semantics) and `canary-wrk-<index>` worker names.
 *
 * Deviations from the C++ implementation:
 * - worker threads are daemon threads so a leaked pool cannot pin the JVM;
 * - `g_game().setGameState(GAME_STATE_SHUTDOWN)` is replaced by the injected
 *   [requestGameShutdown] callback (the port's standard seam for `g_*` globals);
 * - the signal handlers reset in `ThreadPool::shutdown` belong to the JVM
 *   bootstrap instead of the pool.
 */
class ThreadPool(
    private val logger: Logger,
    threadCount: Int = getNumberOfCores(),
    private val requestGameShutdown: () -> Unit = {},
) : AutoCloseable {

    private val resolvedThreadCount: Int =
        if (threadCount > 0) threadCount else maxOf(getNumberOfCores(), DEFAULT_NUMBER_OF_THREADS)

    private val stopped = AtomicBoolean(false)
    private val nextWorkerIndex = AtomicInteger(0)

    private val executor = ThreadPoolExecutor(
        resolvedThreadCount,
        resolvedThreadCount,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, workerThreadName(nextWorkerIndex.getAndIncrement())).apply { isDaemon = true }
        },
    )

    init {
        start()
    }

    /** Second constructor used by the DI container (`inject<ThreadPool>()`). */
    constructor(logger: Logger) : this(logger, getNumberOfCores(), {})

    /** `detach_task` (thread_pool.hpp:26-29). */
    fun detachTask(task: () -> Unit) {
        executor.execute(task)
    }

    /** `submit_task` (thread_pool.hpp:36-39); the [Future] mirrors `std::future`. */
    fun <T> submitTask(task: () -> T): Future<T> = executor.submit(Callable { task() })

    /**
     * `submit_loop` (thread_pool.hpp:31-34), the `BS::thread_pool::submit_loop`
     * block-parallel loop. The returned future mirrors `BS::multi_future<void>`:
     * it completes when every block finished.
     */
    fun submitLoop(first: Long, last: Long, task: (Long) -> Unit): Future<*> {
        val range = last - first
        if (range <= 0) {
            return CompletableFuture.completedFuture(null)
        }

        val blocks = minOf(resolvedThreadCount.toLong(), range)
        val blockSize = (range + blocks - 1) / blocks
        val futures = mutableListOf<CompletableFuture<*>>()

        var start = first
        while (start < last) {
            val end = minOf(last, start + blockSize)
            val blockStart = start
            futures += CompletableFuture.runAsync(
                {
                    var index = blockStart
                    while (index < end) {
                        task(index)
                        index++
                    }
                },
                executor,
            )
            start = end
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    /** `wait_for_tasks` (thread_pool.hpp:41-43); the barrier waits for the queued work. */
    fun waitForTasks() {
        try {
            executor.submit(Runnable {}).get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: ExecutionException) {
            throw IllegalStateException(e)
        }
    }

    /** `get_thread_count` (thread_pool.hpp:45-47). */
    fun getThreadCount(): Int = resolvedThreadCount

    /** `ThreadPool::start` (thread_pool.cpp:109-111). */
    fun start() {
        logger.info("Running with ${getThreadCount()} threads.")
    }

    /** `ThreadPool::shutdown` (thread_pool.cpp:113-131). Idempotent. */
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return
        }

        logger.info("Shutting down thread pool...")

        requestGameShutdown()

        executor.shutdown()
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        logger.info("Thread pool shutdown complete.")
    }

    /** `isStopped` (thread_pool.hpp:66-68). */
    fun isStopped(): Boolean = stopped.get()

    override fun close() {
        shutdown()
    }

    companion object {
        const val DEFAULT_NUMBER_OF_THREADS: Int = 4

        private const val THREAD_NAME_CAPACITY = 16
        private const val WORKER_PREFIX = "canary-wrk-"

        private val lastThreadId = AtomicInteger(-1)
        private val currentThreadId = ThreadLocal.withInitial { lastThreadId.incrementAndGet().toShort() }

        /** `getNumberOfCores` (src/utils/tools.cpp:2036-2039). */
        fun getNumberOfCores(): Int = Runtime.getRuntime().availableProcessors()

        /** `ThreadPool::getInstance` (thread_pool.cpp:48-50). */
        fun getInstance(): ThreadPool = inject()

        /** `ThreadPool::setCurrentThreadName` (thread_pool.cpp:63-92). */
        fun setCurrentThreadName(name: String) {
            Thread.currentThread().name = name.take(THREAD_NAME_CAPACITY - 1)
        }

        /** `ThreadPool::getThreadId` (thread_pool.hpp:54-64). */
        fun getThreadId(): Short = currentThreadId.get()

        /** `ThreadPool::setWorkerThreadName` (thread_pool.cpp:94-107). */
        private fun workerThreadName(index: Int): String {
            val name = "$WORKER_PREFIX$index"
            return if (name.length > THREAD_NAME_CAPACITY - 1) "canary-wrk" else name
        }
    }
}
