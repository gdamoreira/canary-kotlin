# Feature: End-to-End Client Transition (Login to Game World & Viewport Streaming)

## 1. Explanation
Connects the initial account authentication flow (Port 7171) with the live game world connection (Port 7172). Once the client selects their character from the database-backed character list, the server authenticates the game handshake, loads the player's entity into the spatial `GameMap`, and immediately transmits the `SelfAppear` (`0x0A`) and `MapDescription` (`0x64`) viewport packets to render the world on the client's screen.

## 2. Impact
This delivers the complete player entry loop. Real OpenTibia clients can now authenticate against the database, select a character, connect to the world port, and render the initial surrounding map tiles and ground sprites.

## 3. Functionality Affected
- **Handshake Validation**: Validates session keys, character ownership, and XTEA key registration on Port 7172.
- **World Entity Entry**: Spawns the player entity onto the `GameMap` at their persisted coordinates.
- **Viewport Streaming**: Encodes the initial $18 \times 14$ tile grid with tile compaction runs, delivering surrounding terrain to the client canvas.

## 4. Agent Implementation Details
- Updated `GameHandler.kt` to transmit both `PACKET_SELF_APPEAR` and `PACKET_MAP_DESCRIPTION` upon successful connection.
- Connected `MapPacketWriter` directly to `GameHandler` for zero-allocation ByteBuf serialization.
- Validated via `FullLoginToGameWorldFlowTest.kt` simulating both TCP channels sequentially against an in-memory Flyway-migrated database.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.FullLoginToGameWorldFlowTest`: Full end-to-end integration test verifying both TCP connections (Port 7171 and 7172), RSA decryption, XTEA encryption, and map stream decoding.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
