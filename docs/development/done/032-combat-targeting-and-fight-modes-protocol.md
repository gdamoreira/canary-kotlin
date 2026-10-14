# Feature: Combat Targeting & Fight Modes Protocol

## 1. Explanation
Implements combat targeting, stance selection, and attack/follow state synchronization (`src/server/network/protocol/protocolgame.cpp` lines 1912-1920):
- **Opcode `0xA0` (`parseFightModes`)**:
  - Sets fight stance: `FIGHTMODE_ATTACK` (Full Attack), `FIGHTMODE_BALANCED` (Balanced), `FIGHTMODE_DEFENSE` (Full Defense).
  - Chase mode: `CHASEMODE_STANDSTILL` (Don't Chase) or `CHASEMODE_FOLLOW` (Chase Opponent).
  - Secure mode: `SECUREMODE_ON` (Cannot attack non-skulled players) or `SECUREMODE_OFF`.
  - Expert PvP mode (for newer client versions).
- **Opcode `0xA1` (`parseAttack`)**:
  - Target Creature ID: Locks combat onto an opponent or clears target if `0`.
- **Opcode `0xA2` (`parseFollow`)**:
  - Follow Creature ID: Locks walking pathfinder onto creature without attacking.
- **Server Feedback**:
  - `sendCancelTarget` (`0xA3`): Signals that target is dead, out of range, or unattackable.

## 2. Impact
Directly governs PvE and PvP combat rounds. Without these opcodes, players cannot target monsters, switch to defensive stance to mitigate damage, or chase fleeing opponents.

## 3. Functionality Affected
- **Attack Scheduler**: Initiates periodic 2000ms attack events via `CombatEngine` on the `GameDispatcher`.
- **Damage Modifiers**: Modifies attack and armor mitigation factors based on active stance (Attack: $1.0\times$ atk / $0.5\times$ def, Balanced: $0.75\times$ / $0.75\times$, Defense: $0.5\times$ / $1.0\times$).
- **Chase Walking**: Schedules auto-follow movements when the target steps away.

## 4. Agent Implementation Details
- Implement `CombatPacketHandler.kt` parsing fight mode bytes and target IDs.
- Integrate targeting state into `Player` and link to `CombatEngine`.
- Dispatch combat cancellation packets if the target dies or enters a Protection Zone (PZ).

## 5. Test Reference
- `org.opentibia.canary.network.protocol.CombatPacketHandlerTest`: Tests sending `0xA0` (setting Full Attack & Chase), sending `0xA1` (targeting monster), and asserting attack loop execution.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
