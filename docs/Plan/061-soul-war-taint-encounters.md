# Feature: Soul War / Taint Encounters System

## 1. Explanation
Implements Goshnar's Taint mechanics (`data/libs/systems/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*`). Players accumulate Taints (1..5) that amplify damage from Goshnar bosses and drive fear/agony conditions. The C++ engine provides the taint/fear/agony condition types and combat fear checks; the encounter engine and quest logic are Lua.

## 2. Impact
Premier endgame questline mechanics and high-risk boss strategy.

## 3. Functionality Affected
- **Conditions**: `CONDITION_GOSHNARTAINT/FEARED/AGONY`, `class ConditionFeared`.
- **Combat**: `Combat::checkFearConditionAffected`.
- **Player**: `setImmuneFear`/`isImmuneFear`, GoshnarTaint player icons.
- **Primitives**: `Zone`, `ZoneEvent`, `addEvent` used by `encounters.lua`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the C++ condition types (GoshnarTaint/Fear/Agony), `ConditionFeared`, the combat fear check, Player fear immunity, the taint player icon, and the `Zone`/`ZoneEvent`/`addEvent` primitives.
- **Stays Lua (do not port):** `data/libs/systems/encounters.lua` (generic Encounter engine), `data/scripts/globalevents/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*` (mechanics, eventcallbacks, spells, moveevents), `data-otservbr-global/world/quest/soul_war/*.otbm`.

## 5. Test Reference
- `org.opentibia.canary.systems.SoulWarEncounterTest`: taint condition stacking/amplification, `ConditionFeared` application, and the combat fear check.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/creatures_definitions.hpp` (conditions), `src/creatures/combat/condition.hpp`, `src/creatures/combat/condition.cpp`, `src/creatures/combat/combat.cpp`, `src/creatures/players/player.hpp`, `src/creatures/monsters/monsters.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/encounters.lua`, `data/scripts/globalevents/encounters.lua`, `data-otservbr-global/scripts/quests/soul_war/*`, `data-otservbr-global/world/quest/soul_war/*`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Combat::checkFearConditionAffected`, `ConditionFeared`, `Player::setImmuneFear`
