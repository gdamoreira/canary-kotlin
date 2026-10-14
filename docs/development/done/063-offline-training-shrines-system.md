# Feature: Offline Training Shrines System (0x74)

## 1. Explanation
Implements CipSoft's Offline Training (`src/server/network/protocol/protocolgame.cpp` opcode `0x74`). Players interact with training statues (Sword, Axe, Club, Distance, Magic Level); the client disconnects the player, records the logout time and target skill, and awards skill/magic tries on next login based on elapsed offline time.

## 2. Impact
Core daily progression for offline players.

## 3. Functionality Affected
- **Player state**: `addOfflineTrainingTries`, `add/remove/getOfflineTrainingTime`, `get/setOfflineTrainingSkill`; `onThink` accrual.
- **Dialog**: `Game::sendOfflineTrainingDialog`.
- **Protocol**: opcode `0x74` `parseStartOfflineTraining`; client send; `MESSAGE_OFFLINE_TRAINING`.
- **Persistence**: `iologindata_save_player.cpp` / `iologindata_load_player.cpp`.
- **Config**: `RATE_OFFLINE_TRAINING_SPEED`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Player` offline-training state/methods, the `onThink` accrual, `Game::sendOfflineTrainingDialog`, the `0x74` protocol handler + client send, persistence, and config. Bind `player:addOfflineTrainingTries/getOfflineTrainingTime`.
- **Stays Lua (do not port):** `data/scripts/creaturescripts/player/offline_training.lua` (onLogin computes tries from offline time/vocation).

## 5. Test Reference
- `org.opentibia.canary.systems.OfflineTrainingTest`: `0x74` parsing sets the target skill and requests logout, offline-time accrual capped at the training cap, and try conversion on login.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.hpp`, `src/game/game.cpp`, `src/server/network/protocol/protocolgame.cpp`, `src/io/iologindata_save_player.cpp`, `src/io/iologindata_load_player.cpp`, `src/config/config_enums.hpp`
- **Lua Source Files (stay Lua)**: `data/scripts/creaturescripts/player/offline_training.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `parseStartOfflineTraining` (0x74), `Game::sendOfflineTrainingDialog`, `Player::addOfflineTrainingTries`

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.
