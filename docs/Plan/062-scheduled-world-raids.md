# Feature: Scheduled World Raids & Global Announcements

## 1. Explanation
Implements automated global raid encounters (`data/libs/systems/raids.lua`, `data-canary/raids/raids.xml`). Runs scheduled or randomized world events that trigger multi-stage broadcast announcements to all online players and spawn monster invasions across specific town/wilderness coordinates:
- Stage 1: Warning message (e.g. *"A deadly chill descends upon Svargrond..."*).
- Stage 2: Influx wave (e.g. *"The ice dragons have invaded the city walls!"*).
- Stage 3: Nemesis boss wave (e.g. Ghazbaran, Ferumbras, Orshabaal).

## 2. Impact
Server-wide community events, rare loot opportunities, and server activity surges.

## 3. Functionality Affected
- **Raid Scheduler Loop**: Runs periodic checks against configured execution intervals on `GameDispatcher`.
- **Global Broadcast Dispatcher**: Transmits broadcast messages to all online player channels (`0xB4`).
- **Dynamic Entity Spawner**: Spawns boss encounters and monster minion waves directly onto the active `GameMap`.

## 4. Agent Implementation Details
- Implement `RaidScheduler.kt` parsing XML raid scripts.
- Implement multi-stage execution timers using coroutine delays.
- Clean up remaining monsters after raid window concludes.

## 5. Test Reference
- `org.opentibia.canary.systems.RaidSchedulerTest`: Triggers a test raid, verifies broadcast delivery to online players, and verifies monster spawning on target map coordinates.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
