# Feature: Gnomprona Hazard System & Primal Dungeons

## 1. Explanation
Implements CipSoft's Hazard difficulty scaling system (`data/libs/systems/hazard.lua`, `data/scripts/eventcallbacks/monster/ondroploot_hazard.lua`). Used in Gnomprona primal hunting zones. Hazard level scales from $1$ to $12+$:
- Increases monster damage dealt ($+2\%$ per hazard level) and defense mitigation.
- Increases hazard environmental events (Primal Pod explosions, spawn traps).
- Multiplies loot bonus chances and rolls for rare drops (Primal Bag, Plunder Patriarch encounter).

## 2. Impact
Pinnacle endgame team-hunting difficulty modifier for level 800..2000+ parties.

## 3. Functionality Affected
- **Hazard Zone Tracker**: Evaluates player hazard level when inside configured zone bounding boxes.
- **Combat Multipliers**: Injects damage/defense multipliers into `CombatEngine` calculations.
- **Loot Injection**: Adds hazard bonus drop rolls upon monster death.

## 4. Agent Implementation Details
- Implement `HazardSystemEngine.kt` evaluating player zone levels.
- Apply hazard damage scaling to creature attacks in the combat round loop.
- Hook into `LootEngine` to roll additional hazard loot tiers.

## 5. Test Reference
- `org.opentibia.canary.systems.HazardSystemTest`: Tests hazard level 5 applying $+10\%$ monster damage scaling and adding bonus loot rolls on death.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
