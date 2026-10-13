# Feature: Weapon Proficiencies System (Tibia 13.30+)

## 1. Explanation
Implements CipSoft's Weapon Proficiencies (`src/creatures/players/components/weapon_proficiency.*`, `src/enums/weapon_proficiency.hpp`, `data/items/proficiencies.json`). Characters accumulate proficiency experience per weapon family (Swords, Axes, Clubs, Bows, Crossbows, Rods, Wands), unlocking permanent perks (damage, critical, spell bonuses). This is a C++-dominant feature; the Lua surface is minimal.

## 2. Impact
Long-term weapon mastery progression in protocol versions >= 13.30.

## 3. Functionality Affected
- **Enums/structs**: `WeaponProficiencyBonus_t`, `ProficiencyPerk/Level/Proficiency`, `WeaponProficiencyData`, spell-bonus structs.
- **Component**: `class WeaponProficiency` (JSON load, perks, combat application, kill tracking).
- **Protocol**: `OfficialWeaponProficiencyPayload` (`protocol_profile.hpp`).
- **Binding**: `player:addWeaponExperience`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `WeaponProficiency` component + JSON loader + enums + protocol payload, and bind `player:addWeaponExperience`.
- **Stays Lua (do not port):** `data/scripts/lib/proficiency_helper.lua` (catalyst item), `data/scripts/talkactions/god/weapon_proficiency.lua`.

## 5. Test Reference
- `org.opentibia.canary.systems.WeaponProficiencyTest`: JSON load, adding weapon experience per family, perk level thresholds, and applying the damage bonus to combat.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/weapon_proficiency.hpp`, `src/creatures/players/components/weapon_proficiency.hpp`, `src/creatures/players/components/weapon_proficiency.cpp`, `src/server/network/protocol/protocol_profile.hpp`, `src/lua/functions/creatures/player/player_functions.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/lib/proficiency_helper.lua`, `data/scripts/talkactions/god/weapon_proficiency.lua`, `data/items/proficiencies.json`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `WeaponProficiency::loadFromJson`, `addWeaponExperience`/`addWeaponProficiencyExperience`, `OfficialWeaponProficiencyPayload`

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
