# Feature: Familiars Vocation Summon System

## 1. Explanation
Implements CipSoft's level 200+ Vocation Familiars (`src/creatures/players/grouping/familiars.*`, `data/libs/systems/familiar.lua`, `data/XML/familiars.xml`). Each vocation summons a timed companion (Knight/Paladin/Sorcerer/Druid/Monk familiar). The C++ engine stores familiar definitions (XML) and player familiar state; the summon spells and timers are Lua.

## 2. Impact
Mid-to-endgame solo hunting combat amplifier and boss support.

## 3. Functionality Affected
- **Definitions**: `class Familiars`/`Familiar`/`FamiliarEntry` (XML load).
- **Player state**: `canFamiliar/addFamiliar/removeFamiliar/getFamiliar/setFamiliarLooktype`; storage range.
- **Combat**: `Monster::isFamiliar`, `Player::isFamiliar`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Familiars` XML loader, the `Player` familiar collections + storage range, and the combat familiar flags.
- **Stays Lua (do not port):** `data/libs/systems/familiar.lua` (FAMILIAR_ID map, timers, `RemoveFamiliar`), `data/scripts/spells/familiar/*` (summon spells), `data/scripts/creaturescripts/familiar/*`, `data/XML/familiars.xml` (data).

## 5. Test Reference
- `org.opentibia.canary.systems.FamiliarSystemTest`: loading familiar definitions from XML, add/remove/has familiar on `Player`, and storage-range assignment.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/players/grouping/familiars.hpp`, `src/creatures/players/grouping/familiars.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/players/components/player_storage.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/familiar.lua`, `data/scripts/spells/familiar/*`, `data/scripts/creaturescripts/familiar/*`, `data/XML/familiars.xml`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Familiars::loadFromXml`, `Player::addFamiliar/removeFamiliar/canFamiliar`

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
