package org.opentibia.canary.database

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

class DatabaseTasks(
    private val executor: Executor
) {

    private data class DatabaseTask(val name: String, val task: () -> Unit)

    private enum class State {
        Running,
        Terminated
    }

    private val lock = ReentrantLock()
    private val taskSignal = lock.newCondition()
    private val pending = ArrayDeque<DatabaseTask>()
    private val workerDone = CountDownLatch(1)
    private var state = State.Running

    init {
        executor.execute(::threadMain)
    }

    fun addTask(name: String, task: () -> Unit) {
        lock.withLock {
            if (state != State.Running) {
                return
            }
            val wasEmpty = pending.isEmpty()
            pending.addLast(DatabaseTask(name, task))
            if (wasEmpty) {
                taskSignal.signal()
            }
        }
    }

    fun shutdown() {
        lock.withLock {
            state = State.Terminated
        }
        flush()
        lock.withLock {
            taskSignal.signal()
        }
        workerDone.await()
    }

    fun flush() {
        lock.lock()
        try {
            while (pending.isNotEmpty()) {
                val task = pending.removeFirst()
                lock.unlock()
                try {
                    runTask(task)
                } finally {
                    lock.lock()
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun threadMain() {
        try {
            while (true) {
                val task = lock.withLock {
                    while (state == State.Running && pending.isEmpty()) {
                        taskSignal.await()
                    }
                    pending.removeFirstOrNull()
                }
                if (task == null) {
                    break
                }
                runTask(task)
            }
        } finally {
            workerDone.countDown()
        }
    }

    private fun runTask(task: DatabaseTask) {
        try {
            task.task()
        } catch (e: Exception) {
            logger.error(e) { "Database task '${task.name}' failed" }
        }
    }
}
