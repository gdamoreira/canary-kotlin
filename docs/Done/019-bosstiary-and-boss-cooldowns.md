# Feature: Bosstiary & Boss Cooldown Tracker

## 1. Explanation
Canary includes CipSoft's modern Bosstiary system (`io_bosstiary.cpp`). Tracks boss kill counts across 3 progression tiers (Bane, Archfoe, Nemesis), awards Boss Points, unlocks Boss Slot bonuses (e.g. equipment loot bonus), and manages daily boss room levers and lockout cooldowns.

## 2. Impact
Endgame bossing progression and reward mechanics. Affects boss room levers, instance room access, and loot generation multipliers.

## 3. Functionality Affected
- **Kill Count Tracker**: Increments boss kills per player / party upon boss death.
- **Boss Mastery Preamble**: Bane (25/100/300), Archfoe (5/20/60), Nemesis (1/3/5).
- **Boss Cooldown Management**: Lockouts preventing players from re-entering boss levers within 20 hours.

## 4. Agent Implementation Details
- Implement `BosstiaryManager` and `BossEntry` in `org.opentibia.canary.systems.bosstiary`.
- Implement boss kill listener integrated with `CreatureEvent` / death dispatcher.
- Expose boss bonus modifiers to the `LootEngine`.

## 5. Test Reference
- `org.opentibia.canary.systems.bosstiary.BosstiaryTest`: Tests boss kill accumulation, tier unlock, and bonus multiplier calculation.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
