# Sequential Implementation Roadmap & Feature Dependencies

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

## Sequenced Phase Breakdown

### Phase 1: Foundation & Event Loop
1. **013 - Configuration Manager**:
   - YAML / HOCON / environment variable loader for server ports, IP, rate limits, world configuration.
   - Validation against Canary's `config.lua.dist`.
2. **004 - Game Dispatcher & Tick Scheduler**:
   - Kotlin Coroutines / Virtual Thread single-threaded actor for game world mutations.
   - Delay scheduler, event queues, and priority lanes (`Maintenance`, `Input`, `Commit`).

### Phase 2: World Engine & Objects
3. **005 - Items & Object Model (OTB & XML)**:
   - Tree node parser for `items.otb`.
   - Item attribute registry (armor, attack, defense, containers, stackability, weight).
4. **006 - Map OTBM Loader & Spatial Grid**:
   - Binary OTBM parser reading towns, waypoints, houses, tiles, items.
   - Spatial indexing: Floor sectors, tile coordinate bounds, fast spectator query ($18 \times 14$ tiles).

### Phase 3: Entities & Spatial State
5. **007 - Creature System & Players**:
   - `Creature`, `Player`, `Monster`, `Npc` models.
   - Inventory containers, equip slots, vitals (HP, MP, Stamina, Capacity, Skills, Levels).
6. **008 - Movement, Pathfinding & Viewport**:
   - Step calculation formulas, collision checking, floor transitions.
   - A* pathfinding algorithm.
   - Viewport synchronization packets (`sendMapDescription`, `sendMoveCreature`).

### Phase 4: Game Networking & Handshake
7. **002 - Status Protocol (`0xFF`)**:
   - XML / JSON status response for server pingers and web engines.
8. **003 - Game Protocol Handshake & Main Loop**:
   - TCP port 7172 acceptor.
   - RSA handshake with character validation, linking TCP session with `Player` entity on map.

### Phase 5: Gameplay Mechanics & Interaction
9. **009 - Combat, Spells & Conditions**:
   - 2000ms attack round tick.
   - Damage mitigation formulas (armor, defense, elemental resistances).
   - Condition ticks (fire, poison, energy, paralyze, haste).
10. **010 - Chat, Channels & Social**:
    - Local speech radius, yell, whisper.
    - Public & private channels, party shared exp calculations, VIP lists.

### Phase 6: Persistence & Extensibility
11. **011 - Persistence & Database Storage**:
    - Database schema & ORM (Exposed / HikariCP).
    - Player save/load routines, depot serialization, house ownership.
12. **012 - Scripting & Event Engine**:
    - Event bus and scripting layer (actions, talkactions, creatureevents, global events).

### Phase 7: Advanced Systems & Parity
13. **014 - Modern Systems**:
    - Imbuements, Prey, Bestiary & Charms, Exaltation Forge, Skill Wheel of Destiny.
