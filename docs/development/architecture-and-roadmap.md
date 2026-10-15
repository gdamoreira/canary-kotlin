# Canary Kotlin Architecture & Roadmap

> Aggregated from the former `docs/development/plan/000-*` documents: the C++ to
> Kotlin architectural mapping, the parity validation strategy and the
> sequential implementation roadmap. Content is preserved verbatim with
> headings demoted one level and an index added.

## Index

- [Architectural Design: Canary C++ to Idiomatic Kotlin Architectural Mapping](#architectural-design)
- [Parity Validation Strategy: Validation & Canary Parity Testing Strategy](#parity-validation)
- [Sequential Implementation Roadmap: Sequential Implementation Roadmap & Feature Dependencies](#implementation-roadmap)

---

<a id="architectural-design"></a>

## Architectural Design — Canary C++ to Idiomatic Kotlin Architectural Mapping

### 1. Architectural Philosophy

Translating a 15-year evolved C++ codebase directly into Kotlin (e.g. 1:1 class-for-class with raw pointers, singletons, shared pointers, and manual mutexes) produces brittle, unidiomatic JVM code. Canary's C++ codebase relies heavily on:
1. **Pointers & Memory Lifetimes**: `std::shared_ptr`, `std::weak_ptr`, manual delete, circular reference workarounds.
2. **Global Singletons**: `g_game()`, `g_dispatcher()`, `g_configManager()`, `g_ioban()`.
3. **Lock Synchronization**: `std::scoped_lock`, `std::recursive_mutex` across connection and creature states.
4. **Ad-hoc Inheritance**: Monolithic God objects (`ProtocolGame` has 12,000+ lines, `Game` has 13,000+ lines).

In **Canary-Kotlin**, we adapt these patterns into clean JVM/Kotlin idiomatic architectures:

---

### 2. Structural & Pattern Transformations

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

### 3. High-Performance Spatial Architecture

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

### 4. Packet Handling: De-monolithization of ProtocolGame

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

---

<a id="parity-validation"></a>

## Parity Validation Strategy — Validation & Canary Parity Testing Strategy

To guarantee that the Kotlin implementation matches Canary's C++ code functionality byte-for-byte and behavior-for-behavior, we establish a **3-Layer Parity Validation Strategy**.

---

### 1. Byte-Level Wire Contract Validation (Network & Binary Codecs)

Every network packet and binary parser must be validated against real data generated by or captured from Canary:

1. **Golden Master Test Vectors**:
   - Capture raw byte packets from Canary (e.g. `items.otb`, OTBM map snippets, `ProtocolLogin` login response, `ProtocolGame` map descriptions).
   - Store golden packets in `src/test/resources/fixtures/`.
   - Unit tests decode these exact fixtures and verify field parity.
2. **Round-Trip Serialization Parity**:
   - $\text{Encode}(\text{Decode}(\text{Fixture})) == \text{Fixture}$.
   - Ensures integer endianness, string lengths, variable-length integers ($\ge 15.00$), and padding match Canary's exact output.

---

### 2. Algorithmic & Formula Parity Validation

Key gameplay algorithms in Canary must yield mathematically identical results in Kotlin:

| Subsystem | Canary C++ Source | Test Parity Assertion |
|---|---|---|
| **Adler-32 Checksum** | `utils/tools.cpp:adlerChecksum` | Identical 32-bit unsigned output on all byte inputs. |
| **XTEA Cipher** | `src/server/network/protocol/protocol.cpp` | Identical 64-round transformation in Little-Endian. |
| **Step Duration** | `src/creatures/creature.cpp:getStepDuration` | Formula: $\text{duration} = \frac{\text{groundSpeed} \times 1000}{\text{creatureSpeed}}$ matches integer millisecond timings. |
| **Experience / Level** | `src/game/game.cpp:getExpForLevel` | Formula: $50 \times \frac{n^3 - 6n^2 + 17n - 12}{3}$ matches level curve. |
| **Damage Mitigation** | `src/creatures/combat/combat.cpp` | Armor reduction $P(d) = \text{random}(\text{armor}/2, \text{armor} - 1)$ and defense formulas match bounds. |
| **Spectator Range** | `src/map/map.cpp:getSpectators` | Viewport dimensions ($18 \times 14$ tiles, floor visibility ranges $\pm 2$ floors) match Canary visibility rules. |

---

### 3. End-to-End Simulation Testing

Using Netty's `EmbeddedChannel`, each protocol handler is tested without requiring an external client or running background daemon:

1. **Virtual Client Simulator**:
   - A Kotlin test client that connects to the pipeline, negotiates RSA, creates an XTEA session, and sends command packets.
2. **Scenario Integration Tests**:
   - **Login Scenario**: Handshake $\to$ Authenticate $\to$ Receive Character List $\to$ Parse Worlds.
   - **Game Login Scenario**: Character Select $\to$ Connect Port 7172 $\to$ Parse Map Description $\to$ Assert Player Initial Position.
   - **Movement Scenario**: Send Step East $\to$ Assert Server Move Packet $\to$ Assert Tile Updates $\to$ Assert Player Coordinates Updated.
   - **Chat Scenario**: Send Local Message $\to$ Assert Spectator Receives Message $\to$ Out-of-Range Spectator Does Not.

---

### 4. Documentation Lifecycle Workflow

As implementation proceeds through the roadmap:
1. When work on a feature begins:
   - Move document from `docs/development/plan/` to `docs/development/implementing/`.
2. As code and tests are committed:
   - Run `./gradlew test` and verify against Canary parity assertions.
3. Once all acceptance criteria and tests pass:
   - Move document from `docs/development/implementing/` to `docs/development/done/`.
   - Update document with exact implementation details, file list, and test references.

---

<a id="implementation-roadmap"></a>

## Sequential Implementation Roadmap — Sequential Implementation Roadmap & Feature Dependencies

To guarantee functionality and testability at every stage, features must be implemented in a strict topological order where prerequisites are completed before dependents.

```
       Phase 1: Foundation & Event Loop
       [000 Architecture] -> [013 Config] -> [004 Dispatcher/Scheduler]
                               |
                               v
       Phase 2: World Engine & Objects
       [005 Items OTB/XML] -> [006 Map/OTBM Grid]
                               |
                               v
       Phase 3: Entities & Spatial State
       [007 Creature & Player] -> [008 Movement & Pathfinding]
                               |
                               v
       Phase 4: Game Networking & Handshake
       [002 Status Protocol] -> [003 Game Handshake & ProtocolGame]
                               |
                               v
       Phase 5: Gameplay Mechanics & Interaction
       [009 Combat & Spells] -> [010 Chat & Social]
                               |
                               v
       Phase 6: Persistence & Extensibility
       [011 Database / Storage] -> [012 Scripting Engine]
                               |
                               v
       Phase 7: Advanced Systems & Parity
       [014 Modern Systems (Prey, Forge, Skill Wheel)]
```

---

### Sequenced Phase Breakdown

#### Phase 1: Foundation & Event Loop
1. **013 - Configuration Manager**:
   - YAML / HOCON / environment variable loader for server ports, IP, rate limits, world configuration.
   - Validation against Canary's `config.lua.dist`.
2. **004 - Game Dispatcher & Tick Scheduler**:
   - Kotlin Coroutines / Virtual Thread single-threaded actor for game world mutations.
   - Delay scheduler, event queues, and priority lanes (`Maintenance`, `Input`, `Commit`).

#### Phase 2: World Engine & Objects
3. **005 - Items & Object Model (OTB & XML)**:
   - Tree node parser for `items.otb`.
   - Item attribute registry (armor, attack, defense, containers, stackability, weight).
4. **006 - Map OTBM Loader & Spatial Grid**:
   - Binary OTBM parser reading towns, waypoints, houses, tiles, items.
   - Spatial indexing: Floor sectors, tile coordinate bounds, fast spectator query ($18 \times 14$ tiles).

#### Phase 3: Entities & Spatial State
5. **007 - Creature System & Players**:
   - `Creature`, `Player`, `Monster`, `Npc` models.
   - Inventory containers, equip slots, vitals (HP, MP, Stamina, Capacity, Skills, Levels).
6. **008 - Movement, Pathfinding & Viewport**:
   - Step calculation formulas, collision checking, floor transitions.
   - A* pathfinding algorithm.
   - Viewport synchronization packets (`sendMapDescription`, `sendMoveCreature`).

#### Phase 4: Game Networking & Handshake
7. **002 - Status Protocol (`0xFF`)**:
   - XML / JSON status response for server pingers and web engines.
8. **003 - Game Protocol Handshake & Main Loop**:
   - TCP port 7172 acceptor.
   - RSA handshake with character validation, linking TCP session with `Player` entity on map.

#### Phase 5: Gameplay Mechanics & Interaction
9. **009 - Combat, Spells & Conditions**:
   - 2000ms attack round tick.
   - Damage mitigation formulas (armor, defense, elemental resistances).
   - Condition ticks (fire, poison, energy, paralyze, haste).
10. **010 - Chat, Channels & Social**:
    - Local speech radius, yell, whisper.
    - Public & private channels, party shared exp calculations, VIP lists.

#### Phase 6: Persistence & Extensibility
11. **011 - Persistence & Database Storage**:
    - Database schema & ORM (Exposed / HikariCP).
    - Player save/load routines, depot serialization, house ownership.
12. **012 - Scripting & Event Engine**:
    - Event bus and scripting layer (actions, talkactions, creatureevents, global events).

#### Phase 7: Advanced Systems & Parity
13. **014 - Modern Systems**:
    - Imbuements, Prey, Bestiary & Charms, Exaltation Forge, Skill Wheel of Destiny.

