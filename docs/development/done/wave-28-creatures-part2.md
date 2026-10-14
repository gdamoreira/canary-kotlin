# Wave 28 Plan — `src/creatures/` Part 2 (Conditions, Monster Runtime, MonsterType)

**Status:** In progress
**Process:** AGENTS.md §6 (plan doc before the wave; docs update + separate commit + push after; plan moved to `Done/`).
**Tracking:** `src/creatures/` part 2. Goal: turn the covered `PARTIAL`/`MISSING` rows into `IMPLEMENTED`.

## Rows being closed

| C++ | Current | Target |
|---|---|---|
| `combat/condition.cpp` | PARTIAL | IMPLEMENTED — drunk/muted/channelmuted/pacified/rooted, full attribute params, Player suppression |
| `monsters/monster.cpp` | PARTIAL | IMPLEMENTED — spells, death/corpse, defense/reflect, walk-back, despawn |
| `monsters/monster_combat_intention.cpp` | MISSING | IMPLEMENTED — combat intention scheduling |
| `monsters/monster_relevance.cpp` | MISSING | IMPLEMENTED — relevance capture |
| `monsters/monsters.cpp` | PARTIAL | IMPLEMENTED — full MonsterType schema, reload |
| `monsters/monster_pathfinding.cpp` | MISSING | IMPLEMENTED (A* done in wave 18 — flip) |
| `monsters/monster_targeting.cpp` | MISSING | IMPLEMENTED (targeting done in wave 22 — flip) |
| `monsters/spawns/spawn_monster.cpp` | PARTIAL | IMPLEMENTED (spawn engine done in wave 18 — flip) |

## Tasks

### Task 1 — Conditions completion (`feat/condition-complete`)
- **Canary sources:** `src/creatures/combat/condition.cpp/.hpp`.
- **Deliverable:** add the remaining condition classes (drunk, muted, channel-muted, pacified, rooted) and the full attribute param set; wire them into the factory.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `combat/condition` (+ additive); no Lua.

### Task 2 — Monster runtime (`feat/monster-complete`)
- **Canary sources:** `src/creatures/monsters/monster.cpp/.hpp` (the remaining ~150 methods not yet ported).
- **Deliverable:** extend `creatures/MonsterAi.kt` / add `creatures/monsters/MonsterBehavior.kt` with spells, death/corpse, defense/reflect, walk-back, and despawn logic.
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures` (+ additive `MonsterAi.kt`); no Lua.

### Task 3 — Combat intention + relevance (`feat/monster-intention-relevance`)
- **Canary sources:** `src/creatures/monsters/monster_combat_intention.cpp`, `monster_relevance.cpp`.
- **Deliverable:** new files `creatures/monsters/MonsterCombatIntention.kt` and `creatures/monsters/MonsterRelevance.kt` (do NOT edit `MonsterAi.kt`).
- **Tests:** transcribe real Canary behavior.
- **Constraints:** `creatures/monsters` (new files only); no Lua.

### Task 4 — MonsterType schema (`feat/monstertype-schema`)
- **Canary sources:** `src/creatures/monsters/monsters.cpp/.hpp` (the `MonsterType`/`MonsterInfo` schema) and the Lua monster registration it consumes.
- **Deliverable:** `creatures/monsters/MonsterType.kt` with the full schema (attacks, defenses, elements, immunities, flags, voices, summons, races) + reload; self-contained (Lua stays Lua).
- **Tests:** transcribe the schema fields/defaults.
- **Constraints:** `creatures/monsters` (new file only); no Lua.

## Verification

`./gradlew clean test` green, zero Kotlin warnings.

## Post-wave (AGENTS.md §6)

Flip the covered `src/creatures/` rows to `IMPLEMENTED` in `docs/development/missing-features.md`; update `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move this document to `docs/development/done/`; commit the docs separately and push to `origin`.

## Completion

Completed. Conditions completed, monster runtime (spells/death/defense/walk/despawn), combat intention + relevance, and full MonsterType schema. Rows flipped to IMPLEMENTED.
