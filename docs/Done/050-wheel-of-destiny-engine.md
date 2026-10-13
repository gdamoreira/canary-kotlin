# Feature: Wheel of Destiny (Skill Wheel) Engine

## 1. Explanation
Implements CipSoft's Wheel of Destiny (`src/io/io_wheel.cpp`, `src/creatures/players/components/wheel/`, `src/enums/player_wheel.hpp`). Characters level 50+ earn Promotion Points (1 per level > 50) allocated across Canary's **37-slot** per-vocation perk tree — Dedication (raw stats), Conviction (secondary passives), and Revelation (ultimate spells/augmentations) — plus **gem sockets** with basic/supreme modifiers. Client opcodes: `0x61` (Open Wheel), `0x62` (Save Wheel), `0xE7` (Wheel Gem Action).

## 2. Impact
Endgame character customization and power progression for level 50..1000+ characters.

## 3. Functionality Affected
- **Point Budget**: `PlayerWheel::getWheelPoints` / `getUnusedPoints`.
- **37-slot tree**: per-vocation definitions and prerequisite gating (`IOWheel`, `wheel_definitions.hpp`).
- **Gem sockets**: basic/supreme gem modifiers (`wheel_gems.cpp/.hpp`).
- **Revelation/spell modifications** (`wheel_spells.cpp`).
- **Protocol**: `parseOpenWheel` (0x61), `parseSaveWheel` (0x62), `parseWheelGemAction` (0xE7); `Game::playerOpenWheel/SaveWheel/WheelGemAction`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only — parity):** port `IOWheel`, `PlayerWheel`, `wheel_gems`, `wheel_spells`, `wheel_definitions`, `enums/player_wheel.hpp`, the three protocol handlers, the `Game` entry points, and `Player::wheelUnlockScroll`. **Replace** the existing non-parity `systems/WheelOfDestinyEngine.kt` (4 quadrants / linear bonuses — not Canary's 37-slot tree).
- **Stays Lua (do not port):** `data/scripts/actions/items/wheel_scrolls.lua` (item action calling `player:wheelUnlockScroll`).

## 5. Test Reference
- `org.opentibia.canary.systems.WheelOfDestinyTest` (rewrite): point budget per level, 37-slot allocation, prerequisite gating, gem modifier application, protocol 0x61/0x62 serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/io/io_wheel.cpp`, `src/io/io_wheel.hpp`, `src/creatures/players/components/wheel/player_wheel.cpp`, `src/creatures/players/components/wheel/player_wheel.hpp`, `src/creatures/players/components/wheel/wheel_gems.cpp`, `.../wheel_gems.hpp`, `.../wheel_spells.cpp`, `.../wheel_definitions.hpp`, `src/enums/player_wheel.hpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/actions/items/wheel_scrolls.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `IOWheel::loadFromJson`, `PlayerWheel::getWheelPoints`/`getUnusedPoints`/`addPromotionPoints`, `parseOpenWheel` (0x61), `parseSaveWheel` (0x62), `parseWheelGemAction` (0xE7)

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
