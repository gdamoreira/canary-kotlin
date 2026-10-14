# Feature: Spells, Magic Engine & Player QuestLog System

## 1. Explanation
Implements OpenTibia's spellcasting mechanisms, mana validation, and quest progression tracking (`src/creatures/combat/spells.hpp`, `src/io/player_storage_repository.hpp`):
- **Spell Engine (`SpellRegistry.kt`)**:
  - Validates caster level requirement and mana consumption.
  - Registers instant words (e.g. `exura`, `exura vita`, `exevo flam hur`).
  - Executes functional spell lambdas upon valid cast, consuming player mana.
- **Player Storage & Quest Log (`QuestManager.kt`)**:
  - Key-value integer storage per character (Canary's `getStorageValue` / `setStorageValue`).
  - Unset storage keys return standard `-1`.
  - Maps mission state progressions (`QuestMission`) to quest log entries and mission descriptions.

## 2. Impact
Powers character magic development, rune casting, healing loops, and questlines (Annihilator, Pits of Inferno, Desert Quest, custom quests).

## 3. Functionality Affected
- **Spellbook**: Prevents spellcasting when level is too low or mana is insufficient.
- **Quest State Machine**: Evaluates mission completions based on storage values set by actions, chests, or NPC conversations.

## 4. Test Reference
- `org.opentibia.canary.spells.SpellSystemTest`: Tests instant spell mana consumption, level gating, and health addition.
- `org.opentibia.canary.quest.QuestStorageTest`: Tests storage key-value transitions and multi-mission quest log evaluation.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
