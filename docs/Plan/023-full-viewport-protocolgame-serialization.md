# Feature: Full Viewport & ProtocolGame Packet Serialization

## 1. Explanation
Implements complete server-to-client packet serializations for rendering the game world (`src/server/network/protocol/protocolgame.cpp`):
- `sendMapDescription(Position)`: Encodes $18 \times 14$ tiles across floor levels ($z$-range $\pm 2$ floors) with ground sprites, item stacks, and creature outfits.
- Floor transition packets: `sendFloorChangeUp` (`0xBE`), `sendFloorChangeDown` (`0xBF`).
- Viewport scrolling: `sendMoveNorth` (`0x65`), `sendMoveEast` (`0x66`), `sendMoveSouth` (`0x67`), `sendMoveWest` (`0x68`).
- Creature packets: `sendAddCreature` (`0x0C`), `sendRemoveCreature` (`0x0D`), `sendCreatureTurn` (`0x6B`), `sendCreatureSay` (`0xAA`).
- Inventory & Containers: `sendInventoryItem` (`0x78`), `sendOpenContainer` (`0x7D`), `sendCloseContainer` (`0x7E`).

## 2. Impact
The most visible and critical client-rendering component. Without this, real Tibia clients connect to port 7172 but display a black screen or disconnect immediately due to unparsed map streams.

## 3. Functionality Affected
- **Client Screen Rendering**: Translates spatial `GameMap` state into the exact byte stream expected by the Tibia client canvas.
- **Creature Appearance**: Serializes outfit looktypes, addons, colors (head, body, legs, feet), light radius, and health percent ($0..100\%$).
- **Tile Compaction**: Encodes skip byte runs ($0..255$ empty tiles) to compress map streams over TCP.

## 4. Agent Implementation Details
- Implement `MapPacketWriter.kt` providing `writeMapDescription`, `writeFloorChange`, and `writeTile`.
- Implement `CreaturePacketWriter.kt` serializing outfits, light, skulls, and turn directions.
- Implement `ContainerPacketWriter.kt` serializing container contents.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.MapPacketWriterTest`: Tests serializing a test $18 \times 14$ tile grid and verifies empty tile run compression and ground item IDs.
