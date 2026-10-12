# Feature: House Hirelings System

## 1. Explanation
Implements House Hirelings (`data/libs/systems/hireling.lua`). House owners can purchase and place customizable Hireling NPCs inside their owned houses. Hirelings can be trained in skills:
- **Banker**: Accesses personal bank account, deposits, and withdrawals directly inside the house.
- **Trader / Merchant**: Sells standard potions, runes, and supplies; buys creature products.
- **Cook**: Crafts special stat foods providing regeneration and temporary combat buffs.
- **Steward**: Customizes outfit, dress, and greeting dialogues.

## 2. Impact
Private house utility, convenience for players, and gold sink.

## 3. Functionality Affected
- **Hireling Placement Engine**: Ensures hirelings can only be summoned on tiles belonging to the player's house (`HouseTile`).
- **Hireling Skill Unlock**: Tracks purchased job roles (Banker, Trader, Cook) per hireling instance.
- **Dialogue & Shop Hook**: Connects to `BankService` and `NpcShopHandler`.

## 4. Agent Implementation Details
- Implement `HirelingSystem.kt` extending `Npc` with house ownership binding.
- Implement hireling placement, dismissal, and job interaction state machines.
- Persist active hireling instances in database house tables.

## 5. Test Reference
- `org.opentibia.canary.systems.HirelingSystemTest`: Tests summoning hireling inside owned house tile, interacting as banker to deposit gold, and verifying non-house owners cannot interact.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
