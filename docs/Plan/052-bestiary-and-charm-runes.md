# Feature: Bestiary & Charm Runes System

## 1. Explanation
Implements CipSoft's Cyclopedia Bestiary and Charm Runes system (`src/io/iobestiary.cpp`, `data/scripts/systems/bestiary_charms.lua`). Tracks monster kills across 3 unlock stages (e.g. 25, 250, 1000 kills), reveals monster details (HP, loot chances, elemental weaknesses), awards Charm Points, and unlocks socketable Charm Runes:
- **Offensive Charms**: Zap (Energy), Freeze (Ice), Wound (Physical), Enflame (Fire), Poison (Earth), Curse (Death), Divine Wrath (Holy) dealing $5\%$ of monster's max HP.
- **Defensive & Utility Charms**: Dodge (10% evade), Parry (reflects full damage), Low Blow (+9% critical chance).

## 2. Impact
Core mid-to-endgame hunting incentive. Affects combat DPS by up to 20-30% on high-health creatures.

## 3. Functionality Affected
- **Kill Progress Tracker**: Increments count upon monster death, triggering level completions and charm points awards.
- **Charm Assignment**: Assigns an unlocked charm rune to a fully completed monster entry.
- **Proc Chance Engine**: $10\%$ trigger chance per offensive/defensive attack in `CombatEngine`.

## 4. Agent Implementation Details
- Implement `BestiaryManager.kt` storing kill counts and charm allocations per player.
- Implement `CharmRune` definitions with proc callbacks.
- Hook into monster death events and attack calculation pipelines.

## 5. Test Reference
- `org.opentibia.canary.systems.BestiaryTest`: Tests kill thresholds awarding charm points, socketing a charm rune to a completed creature, and verifying the 10% proc rate deals elemental damage.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
