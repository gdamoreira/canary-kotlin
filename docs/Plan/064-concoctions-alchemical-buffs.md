# Feature: Concoctions & Alchemical Flask Buffs

## 1. Explanation
Implements CipSoft's Concoctions alchemy consumable system (`data/libs/systems/concoctions.lua`). Players drink specialized 1-hour alchemical flasks to grant high-tier hunting passives:
- **Major Curing Concoction**: Grants continuous cure poison and bleeding immunity.
- **Elemental Affinity Concoctions**: Grants $+8\%$ resistance against Fire, Ice, Energy, or Earth damage.
- **Mana / Life Leech Concoction**: Adds temporary $+3\%$ mana leech or $+5\%$ life leech to all attacks.
- Only one combat concoction can be active simultaneously per player.

## 2. Impact
Consumable gold sink and tactical preparation for high-difficulty bossing and hunting grounds.

## 3. Functionality Affected
- **Buff Expiration Scheduler**: Schedules condition expiration after 3600 seconds on `GameDispatcher`.
- **Combat Modifier Injection**: Injects elemental resistance and leech multipliers into `CombatEngine`.
- **Active Buff Indicator**: Displays concoction icon in client status bar.

## 4. Agent Implementation Details
- Implement `ConcoctionEngine.kt` managing active concoctions, mutual exclusion, and duration timers.
- Add `activeConcoctions` set to `Player`.
- Integrate concoction modifiers with damage mitigation formulas.

## 5. Test Reference
- `org.opentibia.canary.systems.ConcoctionEngineTest`: Tests consuming a fire resistance concoction reducing fire damage taken by 8%, and verifying second concoction consumption replaces the active buff.
