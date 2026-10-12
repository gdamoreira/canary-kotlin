# Feature: Boss Reward Chest & Damage Participation Scoring

## 1. Explanation
Implements CipSoft's Boss Reward Container & participation loot scoring (`src/items/containers/rewards/`, `data/scripts/systems/reward_chest.lua`, `src/io/`). When world bosses or nemesis encounters die (e.g. Ferumbras, Morgaroth, Soul War bosses), loot is not dropped on the floor to be stolen. Instead, the engine computes a contribution score per player based on:
- Damage dealt to the boss.
- Damage taken from the boss (tanking score).
- Healing dealt to participants.
Each eligible player receives an individualized Reward Container directly inside the adventurer's guild **Reward Chest**.

## 2. Impact
Eliminates loot theft, bot clicking, and griefing in massive multiplayer boss encounters.

## 3. Functionality Affected
- **Boss Combat Tracker**: Tracks damage dealt, damage mitigated, and healing per player GUID throughout the boss life cycle.
- **Participation Score Formula**: Assigns loot tiers (Tier 1..5) based on normalized contribution percent.
- **Reward Container Dispatcher**: Generates personal loot and deposits into the player's persistent Reward Chest inventory.

## 4. Agent Implementation Details
- Implement `RewardChestEngine.kt` maintaining active boss encounter score sheets.
- Hook into `CombatEngine` to record damage and healing events.
- On boss death, compute scores and spawn private `RewardContainer` instances into database storage.

## 5. Test Reference
- `org.opentibia.canary.systems.RewardChestTest`: Simulates 3 players fighting a boss (DPS, Tank, Healer), calculates participation points, and verifies each player receives their own personal reward container in their reward chest.
