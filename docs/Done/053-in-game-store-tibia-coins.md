# Feature: In-Game Store (GameStore) & Tibia Coins System

## 1. Explanation
Implements the in-game premium shop (`data/modules/scripts/gamestore/*`, `data/libs/gamestore/*`). Players spend Tibia Coins / Transferable Coins on character services, cosmetics, and supplies. Applies to account coin balances (`src/enums/account_coins.hpp`, `src/account/account.*`).

## 2. Impact
Server monetization, premium item delivery, and automated service transactions.

## 3. Functionality Affected
- **Coin ledger**: `coins`, `coins_tournament`, `coins_transferable` account columns (`Account::getCoins/addCoins/removeCoins/registerCoinTransaction`).
- **Transferable balance**: `Player::coinTransferableBalance`.
- **Balance packet**: `Game::sendResourcesBalance` / resources packet.
- **Opcode layer**: store opcodes (`0xEF`, `0xFA`..`0xFE`) are **commented out / WIP** in Canary — no C++ store engine file exists.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the account coin primitives (`Account::getCoins/addCoins/removeCoins/registerCoinTransaction`), `CoinType`/`CoinTransactionType` enums, the three account coin DB columns, `Player::coinTransferableBalance`, and the resource-balance packet. The store opcode handlers stay unimplemented until Canary enables them.
- **Stays Lua (do not port):** the entire store catalog/purchase/delivery logic — `data/libs/gamestore/{store,parsers,purchases,senders,helpers,player,taskboard,constants}.lua` and `data/modules/scripts/gamestore/*` + `catalog/*`.

## 5. Test Reference
- `org.opentibia.canary.account.CoinLedgerTest`: adding/removing coins of each `CoinType`, transferable balance tracking, and resource-balance packet serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/enums/account_coins.hpp`, `src/enums/account_type.hpp`, `src/account/account.hpp`, `src/account/account.cpp`, `src/database/account_repository_db.cpp`, `src/creatures/players/player.hpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/gamestore/*`, `data/modules/scripts/gamestore/*`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Account::getCoins/addCoins/removeCoins/registerCoinTransaction`, `Game::sendResourcesBalance`, `CoinType`, `CoinTransactionType`

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/game-play-features.md`.
