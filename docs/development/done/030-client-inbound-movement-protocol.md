# Feature: Client Inbound Movement & Directional Turn Protocol

## 1. Explanation
Implements client-to-server movement and orientation packets (`src/server/network/protocol/protocolgame.cpp` lines 1734-1779):
- **Single-Step Walking**:
  - `0x65`: Move North
  - `0x66`: Move East
  - `0x67`: Move South
  - `0x68`: Move West
  - `0x6A`: Move NorthEast
  - `0x6B`: Move SouthEast
  - `0x6C`: Move SouthWest
  - `0x6D`: Move NorthWest
- **Auto-Walking & Stop**:
  - `0x64` (`parseAutoWalk`): Ingests path byte arrays from client mouse-clicks.
  - `0x69` (`playerStopAutoWalk`): Cancels pending path steps upon obstacle or key press.
- **Directional Turning (Spot Turns)**:
  - `0x6F`: Turn North
  - `0x70`: Turn East
  - `0x71`: Turn South
  - `0x72`: Turn West

## 2. Impact
Foundational gameplay operation. Governs all locomotion, position changes, and orientation updates. Misaligned step validation or missing cancellation triggers causes client rubber-banding and desynchronization.

## 3. Functionality Affected
- **Step Delay Enforcer**: Prevents speed-hacking by rejecting movements before the calculated step duration interval has elapsed.
- **Collision & Floor Triggers**: Evaluates `Tile` flags, teleporters, and stairs.
- **Spectator Broadcast**: Emits `sendCreatureMove` (`0x6D`) or `sendCreatureTurn` (`0x6B`) to all players observing the moving entity.

## 4. Agent Implementation Details
- Implement `MovePacketHandler.kt` routing opcodes `0x64..0x72`.
- Enforce step timing on `Player` within the `GameDispatcher` thread lane.
- Transmit `sendCancelWalk` (`0xB5`) when movement is blocked.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.MovePacketHandlerTest`: Simulates walking in all 8 directions, verifies position updates and spectator broadcast packets, and tests blocked movement generating `0xB5`.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
