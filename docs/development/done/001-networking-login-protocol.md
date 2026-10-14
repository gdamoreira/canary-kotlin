# Feature: Networking & Login Protocol (ProtocolLogin)

## 1. Explanation
Implements client TCP framing, Adler-32 checksum verification, RSA credential decryption, XTEA session cipher, and the complete OpenTibia Login Protocol (`ProtocolLogin` - Opcode `0x01`).

## 2. Impact
Critical entry point for players connecting to the server. Without this feature, clients cannot authenticate, retrieve the server's Message of the Day (MOTD), or obtain the list of characters and world endpoints to enter the game.

## 3. Functionality Affected
- **Client Handshake**: Validates client packets framed with a 2-byte length prefix.
- **Security & Decryption**: Authenticates credentials encrypted with 1024-bit RSA, sets up session XTEA keys.
- **Account Service**: Verifies account credentials against an `AccountRepository` and loads player characters.
- **Character List Response**: Constructs and encrypts character list packet (`0x64`), session key (`0x28`), and MOTD (`0x14`).

## 4. Agent Implementation Details
- Created `TibiaByteBufExt.kt` providing extension functions for reading/writing Tibia strings and variable-length integers ($\ge 15.00$).
- Created `TibiaFrameDecoder.kt` using Netty's `ByteToMessageDecoder` to parse the 2-byte Little-Endian packet header.
- Implemented `Adler32Checksum.kt` and `XteaCipher.kt` (64 rounds / 32 cycles) in Little-Endian byte order.
- Implemented `RsaCipher.kt` handling 128-byte raw modular exponentiation decryption using standard OpenTibia primes ($p, q$).
- Created `TibiaMessageEncoder.kt` automating 8-byte padding, in-place XTEA encryption, Adler-32 calculation, and 2-byte outer framing.
- Implemented `LoginHandler.kt` parsing client OS, protocol version, RSA credentials, and emitting the character list or error disconnect.
- Implemented `CanaryServer.kt` TCP server bootstrap using Netty.

## 5. Test Reference
- `org.opentibia.canary.network.crypto.Adler32ChecksumTest`: Tests Adler32 against known test vectors.
- `org.opentibia.canary.network.crypto.CryptoTest`: Tests XTEA encrypt/decrypt and RSA encrypt/decrypt round-trips.
- `org.opentibia.canary.network.message.TibiaByteBufExtTest`: Tests Tibia string and variable count serialization.
- `org.opentibia.canary.network.protocol.LoginProtocolTest`: Full end-to-end integration test with `EmbeddedChannel` simulating a real client packet, checking authentication, XTEA responses, and disconnects.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
