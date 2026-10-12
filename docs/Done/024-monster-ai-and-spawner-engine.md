# Feature: Monster AI & Spawner Engine

## 1. Explanation
Manages world spawns and autonomous monster behaviors (`src/map/spawns.cpp`, `src/creatures/monsters/`). Loads monster spawns from `spawns.xml` / OTBM spawn zones, tracks monster life cycles, spawn radius constraints, idle wandering, target acquisition (targeting players), combat AI loops (melee, ranged spells, self-healing, running on low health), and respawn timers.

## 2. Impact
Populates the game world with living PvE encounters. Governs hunting grounds, dungeons, and monster mechanics.

## 3. Functionality Affected
- **Spawn Zones**: Spawns monsters at $(x, y, z)$ within a configured radius.
- **Respawn Timers**: Checks whether players are standing on the spawn tile to block/delay respawning, triggering respawns after $N$ seconds.
- **AI Think Tick**: Evaluates surrounding creatures every 1000ms: acquires target, calculates A* path, approaches target, casts attack spells, flees if health $< \text{runAwayHealth}$.

## 4. Agent Implementation Details
- Implement `SpawnManager.kt` loading spawn points from XML and scheduling respawn events on `GameDispatcher`.
- Implement `MonsterAiEngine.kt` executing the think cycle for active monsters within range of at least one online player.
- Integrate loot dropping upon monster death.

## 5. Test Reference
- `org.opentibia.canary.creatures.MonsterAiTest`: Tests monster target acquisition, movement towards player, and spell casting trigger.
