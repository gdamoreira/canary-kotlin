# Feature: Exaltation Forge (Item Tiers & Fusion) Engine

## 1. Explanation
Implements the Exaltation Forge (`data/libs/systems/exaltation_forge.lua`, `src/enums/forge_conversion.hpp`). Players fuse duplicate items using Dust, Slivers, and Exalted Cores to raise equipment to higher Tiers ($1..10$). Tiered items trigger powerful passive combat procs:
- **Onslaught** (Weapons): Chance to deal $+60\%$ critical damage.
- **Ruse** (Armors): Chance to completely dodge an attack.
- **Momentum** (Helmets): Chance to reset active spell cooldowns by 2 seconds.
- **Transcendence** (Legs/Boots): Grants temporary Avatar transformation buffs.

## 2. Impact
Endgame item sink, economy driver, and equipment progression.

## 3. Functionality Affected
- **Fusion Probability Engine**: Calculates success chances and dust/gold costs per tier attempt.
- **Loss / Core Protection**: Exalted Cores prevent item loss on failure.
- **Combat Proc Hooks**: Integrated directly into `CombatEngine` attack/defense calculation rounds.

## 4. Agent Implementation Details
- Implement `ExaltationForgeEngine.kt` handling fusion attempts, currency deductions, and tier promotions.
- Add `tier` property to `Item` model and serialize tier attributes into database item tables.
- Add Onslaught/Ruse/Momentum proc evaluation to `CombatEngine`.

## 5. Test Reference
- `org.opentibia.canary.systems.ExaltationForgeTest`: Tests fusion success advancing tier, failure consuming dust, and combat engine triggering Onslaught critical procs.
