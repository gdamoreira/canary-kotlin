# Feature: Exaltation Forge (Item Tiers & Fusion) Engine

## 1. Explanation
Implements the Exaltation Forge (`src/creatures/players/components/player_forge_history.*`, `src/enums/forge_conversion.hpp`). Players fuse duplicate items using Dust, Slivers, and Exalted Cores to raise equipment Tiers (1..10). Tiered items trigger passive combat procs: **Onslaught** (weapons, +crit damage), **Ruse** (armor, dodge), **Momentum** (helmets, cooldown reset), **Transcendence** (legs/boots).

## 2. Impact
Endgame item sink, economy driver, and equipment progression.

## 3. Functionality Affected
- **Fusion engine**: success probability and dust/gold cost per tier attempt (`Player::forgeFuseItems`).
- **Tier transfer / resource conversion**: `forgeTransferItemTier`, `forgeResourceConversion`.
- **Dust ledger**: `set/getAddForgeDusts`, `getForgeDustLevel`, `PlayerForgeHistory`.
- **Item tiers/classification**: item tier + classification attributes.
- **Protocol**: `parseForgeEnter`, `sendOpenForge`, `sendForgeResult`, `getForgeInfoMap`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `PlayerForgeHistory`, the `Player` forge methods, item tier/classification attributes, the forge protocol handlers, and the `Game::playerForge*` methods. Bind `player:addForgeDusts/getForgeDusts/getForgeDustLevel`, `Game.createItemClassification`, and `Monster` forge-stack accessors for Lua.
- **Stays Lua (do not port):** `data/libs/systems/exaltation_forge.lua` (ForgeMonster death dust reward, fiendish/influenced spawn selection), `data/scripts/systems/item_tiers.lua` (classification price table registered via `Game.createItemClassification`).

## 5. Test Reference
- `org.opentibia.canary.systems.ExaltationForgeTest`: fusion success advancing tier, failure consuming dust, core protection preventing loss, tier transfer, and forge protocol serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/forge_conversion.hpp`, `src/creatures/players/components/player_forge_history.hpp`, `.../player_forge_history.cpp`, `src/creatures/players/player.hpp` (forge API ~L1409-1430), `src/items/item.cpp` (tier attrs), `src/game/game.cpp` (`playerForge*`), `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/exaltation_forge.lua`, `data/scripts/systems/item_tiers.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Player::forgeFuseItems`, `forgeTransferItemTier`, `forgeResourceConversion`, `parseForgeEnter`, `sendOpenForge`, `sendForgeResult`
