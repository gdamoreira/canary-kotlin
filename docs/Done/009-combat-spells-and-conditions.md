# Feature: Combat, Spells, Damage & Condition Engine

## 1. Explanation
Manages combat interactions between creatures: auto-attacks, distance weapons, magic projectiles, damage calculation (formulas based on attack, defense, armor, resistance), conditions (poison, fire, energy, paralyze, invisible, haste), and death penalty.

## 2. Impact
Directly governs core gameplay balance, PvP, and PvE loops. High complexity due to stacking formulas, mitigation, and elemental affinities.

## 3. Functionality Affected
- **Attack Loop**: Auto-attack cooldowns (typically 2000ms attack round).
- **Damage Formulation**: Physical, Fire, Ice, Energy, Earth, Holy, Death, Drown, Lifedrain, Manadrain.
- **Spell Casting**: Mana verification, rune consumption, instant words, target validation, cooldown groups (attack, healing, support).
- **Conditions**: Tick-based condition updates (damage over time, stat buffs/debuffs).

## 4. Agent Implementation Details
- Implement `CombatEngine` with damage formulas matching Canary/Tibia specifications.
- Implement `Condition` classes with periodic tick execution on the scheduler.
- Implement spells registry with target selectors (single target, area of effect, self).
- Implement damage reduction pipeline (armor value check, defense reduction, elemental resist percentages).

## 5. Test Reference
- `org.opentibia.canary.combat.DamageCalculationTest`: Verify damage bounds for test weapon/skill values.
- `org.opentibia.canary.combat.ConditionTest`: Verify DOT damage ticks and expiration.
