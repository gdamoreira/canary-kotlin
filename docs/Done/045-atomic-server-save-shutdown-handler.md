# Feature: Atomic Server Save & OS Signal Shutdown Handler (SIGTERM/SIGINT)

## 1. Explanation
Implements graceful server shutdown and atomic persistence routines (`src/server/signals.cpp`, `src/game/scheduling/save_manager.cpp`). Catches Docker container termination signals (`SIGTERM`) and manual console interrupts (`SIGINT`), stops network acceptors, broadcasts warnings, and saves all players and world entities in an atomic PostgreSQL transaction.

## 2. Impact
Data loss prevention. Hard-killing a server process during a reboot rolls back player items, causes duplicate item exploits, and corrupts house auctions.

## 3. Functionality Affected
- **Signal Trap**: Binds `sun.misc.Signal` or JVM `Runtime.getRuntime().addShutdownHook`.
- **Shutdown Phases**:
  1. Set game state to `GAME_STATE_SHUTDOWN`.
  2. Reject new connections; disconnect active clients cleanly.
  3. Batch execute `UPDATE players` for all online players.
  4. Flush houses, depot items, and market escrows.
  5. Close HikariCP connection pool and Netty EventLoop groups.

## 4. Agent Implementation Details
- Implement `SaveManager.kt` scheduling background auto-saves every 15 minutes.
- Implement `ShutdownHookHandler.kt` executing the ordered shutdown phases within a guaranteed 10-second timeout.

## 5. Test Reference
- `org.opentibia.canary.game.scheduling.ShutdownHandlerTest`: Simulates shutdown trigger, verifies all active in-memory players are flushed to database, and verifies connection closure.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
