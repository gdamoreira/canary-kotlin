# Feature: Concoctions & Alchemical Flask Buffs

## 1. Explanation
Implements CipSoft's Concoctions (`src/creatures/players/player.*` concoction state, `data/libs/systems/concoctions.lua`). Players drink 1-hour alchemical flasks granting elemental resistances, leech, or cure effects. The C++ engine owns the concoction enum, active-concoction state, and combat/cyclopedia integration; the effect logic is Lua.

## 2. Impact
Consumable gold sink and tactical preparation for bossing/hunting.

## 3. Functionality Affected
- **Enum**: `Concoction_t` (`utils_definitions.hpp`), Lua enum registration.
- **Player state**: `updateConcoction`, `getActiveConcoctions`, `isConcoctionActive`, `activeConcoctions` map.
- **Combat/cyclopedia**: bestiary multiplier use; cyclopedia misc-stats packet; critical contribution.
- **Config**: `TIBIADROME_CONCOCTION_*`.
- **Binding**: `player:updateConcoction`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Concoction_t` enum, the `Player` active-concoction state + `updateConcoction`, the cyclopedia packet contribution, and config. Bind `player:updateConcoction`.
- **Stays Lua (do not port):** `data/libs/systems/concoctions.lua` (`Concoction` class, conditions/cooldown/tick), `data/scripts/systems/concoctions.lua` (per-concoction configs + registration + onLogin).

## 5. Test Reference
- `org.opentibia.canary.systems.ConcoctionEngineTest`: `updateConcoction` activating a concoction, mutual exclusion, `isConcoctionActive`/`getActiveConcoctions`, and cyclopedia packet serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/utils/utils_definitions.hpp` (`Concoction_t`), `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/lua/functions/core/game/lua_enums.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/concoctions.lua`, `data/scripts/systems/concoctions.lua`, `data/scripts/creaturescripts/player/login.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Player::updateConcoction`, `Player::getActiveConcoctions`, `player:updateConcoction`
