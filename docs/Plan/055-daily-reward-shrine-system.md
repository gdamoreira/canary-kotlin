# Feature: Daily Reward Shrine & Consecutive Streak System

## 1. Explanation
Implements the Daily Reward Wall system (`data/libs/systems/daily_reward.lua`). Players click Daily Reward Shrines in temples or depots once per server day (midnight UTC reset) to claim rewards and advance their consecutive login streak (Day 1 through Day 7+):
- **Streak Buffs**: Stamina regeneration in depot/house, double mana regeneration in resting zones, soul regeneration.
- **Daily Rewards**: Health/mana potions, blank runes, prey wildcards, temporary XP boost vouchers.

## 2. Impact
Daily player retention driver and resting zone passive regeneration.

## 3. Functionality Affected
- **Streak Calculation**: Evaluates whether last claim was yesterday (advances streak), today (already claimed), or $>48$ hours ago (resets streak to Day 1).
- **Resting Zone Detection**: Detects whether player is standing inside house or depot tiles and applies accelerated stamina/vitals regen.

## 4. Agent Implementation Details
- Implement `DailyRewardEngine.kt` evaluating player claim timestamps and streaks.
- Add daily reward claims history table / storage keys.
- Deliver selected reward items directly into player inbox.

## 5. Test Reference
- `org.opentibia.canary.systems.DailyRewardTest`: Tests claiming reward on Day 1 advancing streak, attempting second claim same day being rejected, and claiming next day advancing to Day 2 with bonus wildcard.
