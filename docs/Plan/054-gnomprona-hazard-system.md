# Feature: Gnomprona Hazard System & Primal Dungeons

## 1. Explanation
Implements CipSoft's Hazard difficulty scaling (`src/game/game.cpp` `handleHazardSystemAttack`, monster/player hazard state). Used in Gnomprona primal hunting zones. Hazard level (1..12+) increases monster damage/defense and loot bonuses.

## 2. Impact
Pinnacle endgame team-hunting difficulty modifier.

## 3. Functionality Affected
- **Monster hazard flags**: `Monster::getHazard/setHazard/hazardCrit/hazardDodge/hazardDamageBoost/hazardDefenseBoost`.
- **Player hazard points**: `Player::set/getHazardSystemPoints`, `parseAttackRecvHazardSystem`, `parseAttackDealtHazardSystem`, exp bonus.
- **Combat hooks**: `Game::handleHazardSystemAttack`, dodge roll.
- **Config**: `HAZARD_*` / `TOGGLE_HAZARDSYSTEM`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the Monster hazard flags, Player hazard points + attack hooks, `Game::handleHazardSystemAttack`, and hazard config. Bind `monster:hazard()/hazardCrit()/hazardDodge()/hazardDamageBoost()/hazardDefenseBoost()` and `player:setHazardSystemPoints/updateHazard`.
- **Stays Lua (do not port):** `data/libs/systems/hazard.lua` (`Hazard` class, KV levels, `ZoneEvent` enter/leave, `HazardMonster.onSpawn`), `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua` (extra loot rolls).

## 5. Test Reference
- `org.opentibia.canary.systems.HazardSystemTest`: monster hazard flags scaling damage, player hazard points accumulating, and the `handleHazardSystemAttack` dodge/crit rolls.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/monsters/monster.hpp`, `src/creatures/monsters/monster.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/hazard.lua`, `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua`, `data/scripts/lib/register_monster_type.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Game::handleHazardSystemAttack`, `Monster::getHazard/setHazard`, `Player::parseAttackDealtHazardSystem`
