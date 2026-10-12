# Feature: Complete GamePacketRouter & Dispatcher for handleGamePacket

## 1. Explanation
Implements the central packet routing architecture that connects Netty I/O worker threads with the game simulation loop, replacing the stub inside `GameHandler.handleGamePacket(ctx, msg)`:
```kotlin
private fun handleGamePacket(ctx: ChannelHandlerContext, msg: ByteBuf) {
    GamePacketRouter.route(player, msg, gameMap, dispatcher, scripts)
}
```
In Canary, this corresponds to `ProtocolGame::parsePacket(NetworkMessage &msg)` and `ProtocolGame::parsePacketFromDispatcher(NetworkMessage &msg, uint8_t recvbyte)`.

## 2. Impact
Critical core bridge. Without this implementation, incoming packets sent after login (walking, talking, using items, attacking, opening backpacks) sit unread in the socket or trigger channel disconnects.

## 3. Functionality Affected & Opcode Routing Table

| Opcode | Packet Name | Destination Handler & Logic |
|---|---|---|
| **`0x14`** | Logout Request | `KeepaliveHandler.canLogout(player)` $\to$ save player & close channel |
| **`0x1D` / `0x1E`** | Ping / PingBack | `KeepaliveHandler.handlePing(player)` $\to$ immediate pong packet |
| **`0x64`** | AutoWalk | `MovePacketHandler.handleAutoWalk(player, msg, map)` $\to$ multi-step path |
| **`0x65..0x68`** | Move Cardinal | `MovePacketHandler.handle(player, opcode, msg, map)` (N, E, S, W) |
| **`0x69`** | Stop AutoWalk | Cancels current walking job on `GameDispatcher` |
| **`0x6A..0x6D`** | Move Diagonal | `MovePacketHandler.handle(player, opcode, msg, map)` (NE, SE, SW, NW) |
| **`0x6F..0x72`** | Spot Turns | `MovePacketHandler.handle(player, opcode, msg, map)` (Turn N, E, S, W) |
| **`0x78`** | Throw / Drag Item | `ItemPacketHandler.handleThrow(player, msg, map)` |
| **`0x82`** | Use Item | `ItemPacketHandler.handleUse(player, msg, map, scripts)` (`onUse`) |
| **`0x83`** | Use Item Ex | Crosshair use on tile/item (`onUseWith`) |
| **`0x84`** | Use on Creature | Crosshair use on creature ID |
| **`0x85`** | Rotate Item | Directional rotation of objects |
| **`0x87`** | Close Container | Closes open backpack index |
| **`0x88`** | Up Arrow Container | Navigates to parent container |
| **`0x8C` / `0x8D`** | Look At / Battle Look | `LookPacketHandler.handleLook(player, msg, map)` $\to$ `0xB4` text response |
| **`0x96`** | Player Say | `ChatManager.broadcastSpeech(player, type, text, listeners)` |
| **`0xA0`** | Fight Modes | `CombatPacketHandler.handleFightModes(player, msg)` |
| **`0xA1`** | Attack Target | `CombatPacketHandler.handleAttack(player, msg, targets)` |
| **`0xA2`** | Follow Target | Sets follow target without attack |
| **`0xFA`** | Modal Window Answer | `ModalWindowManager.handleResponse(player, windowId, buttonId, choiceId)` |

## 4. Agent Implementation Details
- Implement `GamePacketRouter.kt` with a single entry point `route(player: Player, buffer: ByteBuf, context: GameContext)`.
- Loop over multi-packet frames: Tibia bundles multiple opcodes into a single TCP packet. While `buffer.isReadable`, read `opcode = buffer.readUnsignedByte()` and dispatch to the designated handler.
- Concurrency rule: Network packets decode immutably on the Netty I/O thread, then dispatch mutations to `GameDispatcher.dispatch(DispatcherLane.WorldCommit) { ... }`.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.GamePacketRouterTest`: Feeds a ByteBuf containing chained packets (Turn North + Move East + Say "Hello") and asserts all handlers execute in correct order.
