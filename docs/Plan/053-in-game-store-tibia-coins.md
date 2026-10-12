# Feature: In-Game Store (GameStore) & Tibia Coins System

## 1. Explanation
Implements the in-game premium shop (`data/modules/scripts/gamestore/gamestore.lua`, `src/enums/account_coins.hpp`). Players spend Tibia Coins / Transferable Coins to purchase service products:
- **Character Services**: Name changes, sex changes, main vocation blessings, instant XP boosts.
- **Cosmetics**: Premium store outfits, addons, and mounts.
- **Supplies**: Kegs, potions, exercise weapons, and house furniture.
- **Opcodes**: Open Store (`0xFA`), Browse Category (`0xFB`), Purchase Offer (`0xFC`).

## 2. Impact
Server monetization, premium item delivery, and automated service transactions without GM intervention.

## 3. Functionality Affected
- **Coin Ledger**: Manages `coins` and `coins_transferable` in `accounts` table.
- **Transactional Deduction**: Checks sufficient balance, deducts coins, logs to `coins_transactions` table, and delivers product directly into player inventory or inbox.

## 4. Agent Implementation Details
- Implement `GameStoreService.kt` managing store categories and product offers.
- Implement purchase processor validating coin balance and delivering item/service.
- Connect to database `coins_transactions` table.

## 5. Test Reference
- `org.opentibia.canary.economy.GameStoreTest`: Tests browsing offers, purchasing an outfit with Tibia Coins, verifying coin deduction, and confirming delivery to player inbox.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
