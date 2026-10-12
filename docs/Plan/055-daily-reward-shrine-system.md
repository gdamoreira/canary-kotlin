# Feature: Daily Reward Shrine & Consecutive Streak System

## 1. Explanation
Implements the Daily Reward Wall (`data/libs/systems/daily_reward.lua`, `data/modules/scripts/daily_reward/daily_reward.lua`). Players claim rewards once per server day and advance a consecutive streak (Day 1..7+), gaining resting-zone regeneration and consumable rewards.

## 2. Impact
Daily player retention driver and resting-zone passive regeneration.

## 3. Functionality Affected
- **Player state**: `Player::setDailyReward` / `isDailyReward`, `DailyRewardBonus`/`DailyRewardStatus` enums.
- **KV streak**: `daily-reward` key/value used by regeneration (`condition.cpp`), cyclopedia streak, player titles.
- **Persistence**: `iologindata_save_player.cpp`, `iologindata_load_player.cpp`.
- **Protocol**: cyclopedia daily-streak packet.

## 4. Agent Implementation Details
- **Kotlin scope (C++ engine only):** port the `Player` daily-reward state and enums, the `daily-reward` KV persistence, the regeneration-bonus application, and the cyclopedia streak packet. Bind `player:setDailyReward` / `player:getDailyReward`.
- **Stays Lua (do not port):** `data/libs/systems/daily_reward.lua` (stamina/soul regen events, global storage), `data/modules/scripts/daily_reward/daily_reward.lua` (shrine UI/claim), `data/scripts/actions/objects/daily_reward_shrine.lua` (item action).

## 5. Test Reference
- `org.opentibia.canary.systems.DailyRewardTest`: `setDailyReward` marking streak state, same-day re-claim rejection, streak advance, and KV persistence round-trip.

## 6. Canary Original Code Reference
- **C++ Engine Source Files**: `src/creatures/creatures_definitions.hpp` (`DailyRewardBonus`/`DailyRewardStatus`), `src/creatures/players/player.hpp`, `src/creatures/players/player.cpp`, `src/creatures/combat/condition.cpp`, `src/io/iologindata_save_player.cpp`, `src/io/iologindata_load_player.cpp`, `src/server/network/protocol/protocolgame.cpp`
- **Lua Source Files (stay Lua)**: `data/libs/systems/daily_reward.lua`, `data/modules/scripts/daily_reward/daily_reward.lua`, `data/scripts/actions/objects/daily_reward_shrine.lua`
- **Canary Repository Root**: `/Users/damoreira/.projects/canary`
- **Key Methods / Opcodes**: `Player::setDailyReward`, `Player::isDailyReward`, cyclopedia daily-streak send
