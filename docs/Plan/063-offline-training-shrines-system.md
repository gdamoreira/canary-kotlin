# Feature: Offline Training Shrines System (0x74)

## 1. Explanation
Implements CipSoft's Offline Training system (`src/server/network/protocol/protocolgame.cpp` opcode `0x74`). Players interact with training statues in temples and houses (Sword, Axe, Club, Distance, Magic Level):
- Disconnects the player cleanly and records the logout timestamp and target training skill.
- Each hour offline accumulates skill/magic level tries at $50\%$ of normal online training efficiency (up to a 12-hour training stamina cap).
- When the player logs in next, computes elapsed offline time, awards skill advances, and displays a summary dialog.

## 2. Impact
Core daily progression for offline players, preventing the need for AFK training bots.

## 3. Functionality Affected
- **Offline Training Opcode (`0x74`)**: Dispatches chosen skill target and initiates graceful logout.
- **Offline Math Engine**: Evaluates elapsed seconds since last logout up to $43,200$ seconds (12 hours).
- **Skill Advancement Injection**: Advances player skill tries on login before delivering initial map stream.

## 4. Agent Implementation Details
- Implement `OfflineTrainingEngine.kt` calculating skill tries per elapsed offline hour.
- Add `offlineTrainingSkill` and `offlineTrainingTime` fields to `players` table.
- Hook into player login sequence to apply earned skill points.

## 5. Test Reference
- `org.opentibia.canary.systems.OfflineTrainingTest`: Simulates player training sword skill offline for 10 hours and verifies sword skill points increase by exact calculated tries upon login.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
