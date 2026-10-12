# Feature: Scheduled World Raids & Global Announcements

## 1. Explanation
Implements automated global raid encounters. Canary has **two coexisting systems**: the legacy C++ XML raids (`src/lua/creature/raids.*`, `data-canary/raids/raids.xml`) and a modern Lua scheduler (`data/libs/systems/raids.lua`) built on the Encounter engine. Multi-stage broadcasts spawn monster waves and boss encounters.

## 2. Impact
Server-wide community events, rare loot opportunities, and activity surges.

## 3. Functionality Affected
- **Legacy C++ engine**: `class Raids`/`Raid`/`RaidEvent`, `Game::raids`, startup load, reload hook, task scheduler.
- **Modern Lua scheduler**: `Raid` extends `Encounter` (chance/day gating), `onThink -> raid:tryStart()`.
- **Broadcasts**: global broadcast messages to all online players.
- **Spawning**: boss/minion waves onto the active map.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the legacy `Raids`/`Raid`/`RaidEvent` XML loader/engine + `Game::raids` load/reload, **or** provide the Encounter primitives (`Zone`/`ZoneEvent`/`addEvent`/`getPlayers`/kv/db) the modern Lua scheduler needs. (Decide which path at implementation time; the Lua `raids.lua` stays.)
- **Stays Lua (do not port):** `data/libs/systems/raids.lua`, `data/scripts/globalevents/raids.lua`, `data/scripts/globalevents/global_server_save.lua` raid hooks, `data-canary/raids/raids.xml` (data).

## 5. Test Reference
- `org.opentibia.canary.systems.RaidSchedulerTest`: loading raid definitions, multi-stage timed execution, broadcast delivery, and monster spawning at target coordinates.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/lua/creature/raids.hpp`, `src/lua/creature/raids.cpp`, `src/game/game.hpp`, `src/game/game.cpp`, `src/game/game_reload.cpp`, `src/tasks/task.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/raids.lua`, `data/scripts/globalevents/raids.lua`, `data/scripts/globalevents/global_server_save.lua`, `data-canary/raids/raids.xml`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Raids::loadFromXml`, `Raid::startRaid`, `RaidEvent::executeRaidEvent`, `Game::raids`
