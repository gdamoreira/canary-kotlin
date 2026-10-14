# Wave 41 Plan — Small Sections Sweep + Lua Remainder + `game.cpp`

**Status:** In progress
**Process:** AGENTS.md §6.

## File ownership

| Agent | Owns |
|---|---|
| A1 kv-engine | `kv/**` (new), `protobuf/kv/**` (new) |
| A2 lib-core | `lib/**` (new) |
| A3 utils-core | `utils/**` |
| A4 server-edge | `server/**`, `network/webhook/**` (new) |
| A5 lua-final | `lua/bindings/core/PlayerFunctions.kt`, `lua/modules/**` (new) |
| A6 game-remainder | `game/Game.kt` |

## Tasks

- **A1** `kv.cpp`, `kv_sql.cpp`, `value_wrapper*.cpp` + `kv.proto` typed values: SQL-backed KV engine with the exact scoped-key semantics; migration only if needed: **V16**.
- **A2** `lib/di/*` (DI container/injector), `lib/logging/logger.cpp` (leveled logger + sinks), `lib/messaging/*` (message bus/command handlers), extend metrics (counters/gauges/histograms + Prometheus), `lib/thread/thread_pool.cpp` (bounded pool semantics).
- **A3** `utils/tools.cpp` (string/URI/SHA helpers, time formatting, parseNumber, wildcard), `wildcardtree.cpp`, `batch_update.cpp` (+ its Lua binding seam).
- **A4** `server.cpp`/`signals.cpp` lifecycle/stop parity + signal drain, `network/connection/connection.cpp` state machine/size limits/close reasons, `protocol_profile.cpp`, `protocol_session_hint.cpp`, `network/webhook/webhook.cpp` (Discord webhook client).
- **A5** finish `player_functions.cpp` (~28 remaining), `modules/modules.cpp` (recvbyte Lua client-packet modules), remaining `lua_enums` groups.
- **A6** `game.cpp` remainder: `playerRequestTrade`/`playerAcceptTrade`/`playerCloseTrade`/`playerLookInTrade`, shop (`playerBuyItem`/`playerSellItem`/`playerCloseShop`), party family (`playerInviteToParty`…), guild Game methods, houses/auctions, VIP, highscores, reports, game loop (`checkCreatures`, periodic save).

## Rules

- §1.1 parity (no invented methods), §4 TDD, zero warnings.
- Build: focused tests then full suite once per agent.
- Commit on your branch (no merge/push); report ported methods, test count, gradle result, gaps.

## Post-wave (§6)

Flip the `kv`/`lib`/`utils`/`protobuf`/`server` rows and update `src/lua/` + `game.cpp` rows; update §1–§3, `docs/development/game-play-features.md`, `README.md`, `CHANGELOG.md`; move plan to `Done/`; separate docs commit; push.

## Completion

Completed: kv, lib, utils, protobuf (kv.proto), server edge, lua remainder (player 305/305, modules, enums), and the game.cpp remainder (trade/shop/party/guild/VIP/reports/highscores/checkCreatures). All sections are now IMPLEMENTED except 3 documented PARTIAL rows (game.cpp remainder, protocolgame application, luascript/script_environment).
