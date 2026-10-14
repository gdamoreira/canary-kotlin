# Feature: Boss Reward Chest & Damage Participation Scoring

## 1. Explanation
Implements CipSoft's Boss Reward Container & participation loot scoring (`src/items/containers/rewards/`). On boss/nemesis death, loot is delivered per-participant into each adventurer's guild Reward Chest instead of dropping on the floor, scored by damage dealt/taken and healing.

## 2. Impact
Eliminates loot theft and griefing in large boss encounters.

## 3. Functionality Affected
- **Reward containers**: `class RewardChest`, `class Reward` (`rewardchest.hpp/.cpp`, `reward.hpp/.cpp`).
- **Player chest API**: `Player::getRewardChest/getReward/addReward`.
- **Collection**: `Game::collectRewardChestItems`, `Game::playerRewardChestCollect`; `actions.cpp` opening a reward container.
- **Protocol**: opcode `0xFF`, `parseRewardChestCollect`.
- **Config**: `REWARD_CHEST_COLLECT_ENABLED`, `_MAX_COLLECT_ITEMS`.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port `Reward`/`RewardChest`, `Player::getReward/getRewardChest/addReward`, `Game::collectRewardChestItems/playerRewardChestCollect`, the `0xFF` protocol handler, and config. Bind `container:registerReward/addRewardBossItems`, `creature:getDamageMap/getTargetList`, `monsterType:getBossReward/generateLootRoll`.
- **Stays Lua (do not port):** `data/scripts/systems/reward_chest.lua` (BossDeath score/participation, `BossParticipation.onHealthChange`), `data/libs/systems/reward_boss.lua` (`InsertRewardItems` DB insert, `GetPlayerStats`, `ResetAndSetTargetList`).

## 5. Test Reference
- `org.opentibia.canary.systems.RewardChestTest`: adding a reward to a player's chest, collecting items through `collectRewardChestItems`, collect-limit config, and `0xFF` serialization.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/items/containers/rewards/reward.hpp`, `src/items/containers/rewards/reward.cpp`, `src/items/containers/rewards/rewardchest.hpp`, `src/items/containers/rewards/rewardchest.cpp`, `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/game/game.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/scripts/systems/reward_chest.lua`, `data/libs/systems/reward_boss.lua`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `Game::collectRewardChestItems`, `Player::addReward`, `parseRewardChestCollect` (0xFF)

## 7. Implementation Status

Implemented and tested in Kotlin (`systems/<feature>/` engine core + application services + protocol parsers where applicable). The Lua-authored portions remain Lua per AGENTS.md 1.4. Residual parity gaps are tracked in `docs/development/game-play-features.md`.
