# Feature: House Hirelings System

## 1. Explanation
Implements House Hirelings (`data/libs/systems/hireling.lua`, `data/modules/scripts/hirelings/*`). House owners purchase/place customizable Hireling NPCs (Banker, Trader, Cook, Steward). The C++ engine provides the hireling skill/outfit maps, house-owner speech checks, and the naming protocol; the role logic and dialogues are Lua.

## 2. Impact
Private house utility, convenience for players, and gold sink.

## 3. Functionality Affected
- **Game maps**: `Game::m_hirelingSkills` / `m_hirelingOutfits` (+ accessors).
- **Interaction**: `Player::canSpeakWithHireling`.
- **Protocol**: `parseSetHirelingName` (0xEC); cyclopedia hireling summary (`Summary_t::HIRELINGS`).
- **Item**: `ItemID_t::HIRELING_LAMP` + custom attribute.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Game` hireling skill/outfit maps, `Player::canSpeakWithHireling`, the `0xEC` protocol handler, and the cyclopedia hireling summary.
- **Stays Lua (do not port):** `data/libs/systems/hireling.lua` (752 lines: skills/outfits/sex/foods, house access, save/load), `data/modules/scripts/hirelings/hireling_module.lua`, `data/scripts/actions/items/hireling_lamp.lua`, `hireling_foods.lua`, `globalevents/hireling_save.lua`.

## 5. Test Reference
- `org.opentibia.canary.systems.HirelingSystemTest`: hireling skill/outfit maps, `canSpeakWithHireling` ownership check, and `0xEC` name serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/game/game.hpp`, `src/game/game.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/creatures/players/components/player_cyclopedia.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/hireling.lua`, `data/modules/scripts/hirelings/hireling_module.lua`, `data/scripts/actions/items/hireling_lamp.lua`, `data/scripts/actions/items/hireling_foods.lua`, `data/scripts/globalevents/hireling_save.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Player::canSpeakWithHireling`, `parseSetHirelingName` (0xEC)

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
