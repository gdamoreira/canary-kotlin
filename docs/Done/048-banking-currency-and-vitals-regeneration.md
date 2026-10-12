# Feature: Banking, Currency & Vitals Regeneration Engines

## 1. Explanation
Implements OpenTibia's core economic currency conversion, bank balances, and vocation vitals regeneration (`src/game/bank/bank.cpp`, `src/creatures/players/player.hpp`):
- **Currency Conversion (`Currency.kt`)**:
  - Automatically calculates exchanges between Gold Coins (ID 2148, 1 gp), Platinum Coins (ID 2152, 100 gp), and Crystal Coins (ID 2160, 10,000 gp).
  - Decomposes raw gold quantities into exact currency denominations.
- **Bank Service (`BankService.kt`)**:
  - Deposits, withdrawals, and account-to-account player wire transfers with balance validation.
- **Vitals Regeneration Engine (`VitalsRegenEngine.kt`)**:
  - Periodic tick loop restoring health and mana points up to maximum pool capacities based on vocation rules.

## 2. Impact
Powers NPC banking dialogues (`deposit`, `withdraw`, `transfer`), merchant transactions, and natural player survival/recovery out of combat.

## 3. Functionality Affected
- **Bank Operations**: Thread-safe balance ledger preventing negative balances or overdrafts.
- **Vitals Loop**: Bound checks ensuring health and mana do not overshoot player maximums or revive dead players.

## 4. Test Reference
- `org.opentibia.canary.game.bank.BankSystemTest`: Tests currency conversions, deposits, withdrawals, and inter-player transfers.
- `org.opentibia.canary.game.VitalsRegenerationTest`: Tests periodic health/mana regeneration up to maximum caps.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
