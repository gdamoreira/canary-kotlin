# Feature: Ping/Pong Heartbeat & Session Keepalive Protocol

## 1. Explanation
Implements TCP connection keepalive and round-trip latency tracking (`src/server/network/protocol/protocolgame.cpp` lines 1689-1694):
- **Opcode `0x1E` (`playerReceivePing`)**:
  - Sent periodically by the client. The server responds with `0x1E` (`sendPingBack`).
- **Opcode `0x1D` (`playerReceivePingBack`)**:
  - Response from client when server issues a latency probe packet `0x1D` (`sendPing`).
- **Opcode `0x14` (`logout`)**:
  - Client logout request when clicking "Exit" / "Logout". Validates that player is not in combat (battle sign), not with a red/white skull, and not currently moving before closing session cleanly.

## 2. Impact
Prevents dead TCP socket accumulation, tracks player ping/latency, and ensures clean logout transitions without disconnecting players in combat.

## 3. Functionality Affected
- **Connection Keepalive**: Resets Netty idle timeouts (`ReadTimeoutHandler`).
- **Logout Validator**: Rejects logout if player has battle condition, emitting red status notification *"You may not logout during or immediately after a fight!"*.
- **Graceful Disconnect**: Saves player state via `PlayerStorage`, removes creature from `GameMap`, and notifies spectators.

## 4. Agent Implementation Details
- Add handlers for `0x1D`, `0x1E`, and `0x14` in `GameHandler.kt`.
- Respond immediately to `0x1E` with ping packet `0x1E`.
- On `0x14`, check battle sign flag; if safe, persist player, remove from map, and close channel.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.KeepaliveProtocolTest`: Tests receiving `0x1E` ping and asserting immediate `0x1E` pong response. Tests `0x14` logout saving player state and closing channel.
