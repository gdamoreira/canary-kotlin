# Feature: Game Dispatcher & Tick Scheduler

## 1. Explanation
Canary runs game updates on a dedicated single-threaded dispatcher (`g_dispatcher()`) partitioned into prioritized execution lanes (`DispatcherLane::ProtocolInput`, `DispatcherLane::WorldCommit`, `DispatcherLane::Maintenance`). This guarantees thread-safety across map tiles, items, and creatures without lock contention.

## 2. Impact
Core engine architecture. In Kotlin/JVM, replacing C++ mutexes and Asio event loops with Kotlin Coroutines or Project Loom actor channels prevents race conditions, deadlocks, and high CPU lock synchronization.

## 3. Functionality Affected
- **Event Scheduling**: Timed tasks, decay cycles, creature AI think ticks, regeneration intervals.
- **Input Dispatching**: Decouples network I/O threads (Netty EventLoops) from world modification logic.
- **World Commit Loop**: Batch commit for position changes, battle steps, and map state changes.

## 4. Agent Implementation Details
- Implement `GameDispatcher` using Kotlin Coroutines `Channel<GameEvent>(Channel.UNLIMITED)` processed by a single dedicated dispatcher or virtual thread.
- Support scheduled events (`scheduleEvent(delayMs, block)`) using coroutine delays or Java `ScheduledExecutorService`.
- Implement priority lanes: high priority for creature movement/combat commits, standard for inputs, background for persistence/maintenance.

## 5. Test Reference
- `org.opentibia.canary.game.scheduling.GameDispatcherTest`: Test event ordering, priority execution, exception isolation, and graceful shutdown.
