# Canary C++ to Idiomatic Kotlin Architectural Mapping

## 1. Architectural Philosophy

Translating a 15-year evolved C++ codebase directly into Kotlin (e.g. 1:1 class-for-class with raw pointers, singletons, shared pointers, and manual mutexes) produces brittle, unidiomatic JVM code. Canary's C++ codebase relies heavily on:
1. **Pointers & Memory Lifetimes**: `std::shared_ptr`, `std::weak_ptr`, manual delete, circular reference workarounds.
2. **Global Singletons**: `g_game()`, `g_dispatcher()`, `g_configManager()`, `g_ioban()`.
3. **Lock Synchronization**: `std::scoped_lock`, `std::recursive_mutex` across connection and creature states.
4. **Ad-hoc Inheritance**: Monolithic God objects (`ProtocolGame` has 12,000+ lines, `Game` has 13,000+ lines).

In **Canary-Kotlin**, we adapt these patterns into clean JVM/Kotlin idiomatic architectures:

---

## 2. Structural & Pattern Transformations

| Canary C++ Pattern | Problem on JVM | Idiomatic Kotlin Replacement |
|---|---|---|
| `g_game()`, `g_dispatcher()` Singletons | Untestable, tightly coupled, hidden state | **Dependency Injection (Koin or Constructor Injection)** with clean Interfaces. |
| `std::recursive_mutex`, locks across threads | Contention, deadlocks, lock overhead | **Actor Model / Coroutine Channel Event Loop**: World mutations are confined to a single game thread or Coroutine actor. |
| 12,000-line `ProtocolGame.cpp` | Monolithic, unmaintainable | **Command / Message Pattern & Specialized Packet Handlers**: Separate decoders into domain handlers (`MoveHandler`, `ChatHandler`, `ItemActionHandler`). |
| `std::vector<uint8_t>` & pointer slicing | GC churn, manual index management | **Netty `ByteBuf`** (pooled direct buffers) with Kotlin extension functions (`readTibiaString`, `writeTibiaVarCount`). |
| Manual binary bit-packing for OTBM/OTB | Difficult to read, error-prone | **Sealed Classes & Result/Either types**: Explicit type-safe domain hierarchies with Kotlin DSL builders. |
| Lua C-API userdata wrappers | JNI overhead, GC bridging complexity | **Kotlin Scripting (kts) or LuaJ / GraalVM Polyglot** with clean reflective or type-safe facades. |
| Raw SQL string concatenation & manual statements | SQL injection risk, boilerplate | **JetBrains Exposed ORM / DAO / SQL DSL** with transaction coroutine dispatchers. |
| Int constants & C bitmasks (`0x01`, `0x02`) | Loss of type safety | **Kotlin `value class`**, `enum class`, and `EnumSet` or typed flag wrappers. |

---

## 3. High-Performance Spatial Architecture

- **Position & Coordinates**:
  ```kotlin
  @JvmInline
  value class Direction(val byteValue: Byte) {
      companion object {
          val North = Direction(0)
          val East = Direction(1)
          val South = Direction(2)
          val West = Direction(3)
          val SouthWest = Direction(4)
          val SouthEast = Direction(5)
          val NorthWest = Direction(6)
          val NorthEast = Direction(7)
      }
  }

  data class Position(val x: Int, val y: Int, val z: Int) {
      fun getDistanceTo(other: Position): Int =
          maxOf(abs(x - other.x), abs(y - other.y))
          
      fun isWithinRange(other: Position, rangeX: Int, rangeY: Int): Boolean =
          abs(x - other.x) <= rangeX && abs(y - other.y) <= rangeY && z == other.z
  }
  ```

- **Tile & Map Confinement**:
  Instead of protecting each `Tile` with a mutex, all tile modifications occur strictly on the `GameDispatcher` thread context (`Dispatchers.Default.limitedParallelism(1)` or single-threaded Coroutine Actor). Read-only snapshotting can be passed safely to Netty network threads.

---

## 4. Packet Handling: De-monolithization of ProtocolGame

In Canary, `ProtocolGame::parsePacket` is a monolithic `switch (recvbyte)` statement covering 100+ opcodes.

In Canary-Kotlin, we adopt a **Packet Pipeline with Strategy Handlers**:

```kotlin
sealed interface ClientPacket

data class PlayerMovePacket(val direction: Direction) : ClientPacket
data class ItemUsePacket(val position: Position, val itemId: Int, val stackPos: Int, val index: Int) : ClientPacket
data class PlayerSayPacket(val type: SpeechType, val channelId: Int, val text: String) : ClientPacket

interface PacketHandler<T : ClientPacket> {
    suspend fun handle(player: Player, packet: T)
}
```

This guarantees modular testability: each incoming action can be tested in isolation without firing up a full TCP connection.
