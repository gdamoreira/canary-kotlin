# Feature: Soul War / Taint Encounters System

## 1. Explanation
Implements Goshnar's Taint mechanics and Soul War encounter rules (`data/libs/systems/encounters.lua`). Players venturing through Soul War hunting grounds (Flimsy Lost Souls, Brachiodemons, Claustrophobic Inferno) accumulate Taints ($1..5$):
- Each Taint level multiplies damage taken from Goshnar boss encounters by $+15\%$.
- Adds environmental fear/agony meter mechanics (periodically fearing players or draining mana if unmitigated).
- Defeating Goshnar bosses purges taints and unlocks Tier 3 Soul equipment (Soulbleeder, Soultainter, Soulcutter, Soulshredder).

## 2. Impact
Premier endgame questline mechanics and high-risk boss strategy.

## 3. Functionality Affected
- **Taint Tracker**: Evaluates active player taint count stored in character storage.
- **Encounter Boss Multipliers**: Multiplies boss damage in `CombatEngine` based on player taints.
- **Environmental Hazard Loop**: Periodically triggers fear/agony conditions within Soul War arena boundaries.

## 4. Agent Implementation Details
- Implement `SoulWarEncounterEngine.kt` managing taints and arena bounds.
- Hook into boss damage calculations to apply the taint vulnerability multiplier.

## 5. Test Reference
- `org.opentibia.canary.systems.SoulWarEncounterTest`: Tests having 3 taints applying $+45\%$ damage taken from Goshnar boss attacks and purging taints upon boss victory.
