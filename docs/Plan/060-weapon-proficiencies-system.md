# Feature: Weapon Proficiencies System (Tibia 13.30+)

## 1. Explanation
Implements CipSoft's Weapon Proficiencies system (`src/enums/weapon_proficiency.hpp`, `data/items/proficiencies.json`). Characters level up their mastery of weapon families:
- Swords, Axes, Clubs, Bows, Crossbows, Rods, Wands.
- As players deal damage with a weapon family, they accumulate proficiency experience points.
- Gaining proficiency levels unlocks permanent passive modifiers: $+1..+5\%$ physical/elemental base damage and $+1..+3\%$ critical strike chance.

## 2. Impact
Long-term character weapon mastery progression in modern protocol versions ($\ge 13.30$).

## 3. Functionality Affected
- **Weapon Hit Listener**: Increments proficiency XP on each damaging combat round.
- **Proficiency Level Thresholds**: Evaluates level milestones ($1..10$).
- **Combat Modifier Injection**: Injects passive proficiency bonuses into `CombatEngine` calculations.

## 4. Agent Implementation Details
- Implement `WeaponProficiencyEngine.kt` tracking weapon XP and levels per player.
- Serialize proficiency levels into database storage tables.
- Apply proficiency damage bonuses in `CombatEngine`.

## 5. Test Reference
- `org.opentibia.canary.systems.WeaponProficiencyTest`: Tests dealing damage with a sword accumulating sword proficiency XP, advancing level, and verifying damage bonus is applied to subsequent attacks.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
