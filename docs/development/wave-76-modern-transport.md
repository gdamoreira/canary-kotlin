# Feature: Modern Protocol Transport and Login Challenge

## 1. Explanation
The current Tibia client (protocol version 1525) speaks the **modern transport**:
the outer length is an 8-byte block count, the checksum is a sequence number,
the encrypted payload carries a leading padding-size byte, and the game login is
gated by a server-sent challenge packet (`0x1F`) that the client echoes in its
login packet. The port previously implemented only the legacy framing
(`[u16 length][adler32][xtea]`) and a client-first login, so a real 1525 client
(the OTClient Redemption build) could connect but never complete the game login.

## 2. Impact
Without this, no modern client can enter the world: the client waits for the
challenge before sending the login, and parses every server packet with the
modern header (2-byte block count, 4-byte sequence, 1-byte padding size).
Scripted legacy-framed probes worked, masking the gap.

## 3. Functionality Affected
- Game connection accept: the server now sends `GameServerChallenge` (`0x1F`).
- Game login packet: modern layout (`0x0A` opcode first, no Adler-32) with the
  challenge response fields (`u32 timestamp`, `u8 random`).
- All post-login game packets, both directions: block-count outer length,
  sequence checksum (high bit signals compression), padding-size byte inside the
  encrypted payload.

## 4. Agent Implementation Details
- `network/codec/TibiaMessageEncoder`: modern mode (`modernFraming` +
  `serverSequenceNumber`) and the raw challenge writer.
- `network/codec/TibiaFrameDecoder`: modern inbound decode (block count,
  sequence validation, XTEA, padding strip) for connections past the login.
- `network/protocol/GameHandler`: sends the challenge on channel active, detects
  the modern login (`0x0A` first byte), validates the echoed challenge, and
  switches the codec pair to the modern framing after the login succeeds.
- No compression is emitted (the high bit stays 0), which the client accepts.

## 5. Test Reference
- `TibiaFrameDecoderTest` (modern frames, sequence validation, padding).
- `TibiaMessageEncoderTest` (modern framing and the challenge bytes).
- `GameHandlerModernLoginTest` (challenge + modern login + framing switch).

## 6. Canary Original Code Reference
- **Source Files**: `src/server/network/protocol/transport_codec.cpp`,
  `src/server/network/protocol/protocol_profile.cpp`,
  `src/server/network/protocol/protocolgame.cpp`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods**: `TransportCodec::encodeOutbound`, `TransportCodec::prepareInbound`,
  `ProtocolGame::sendLoginChallenge` (protocolgame.cpp:1442-1480),
  `ProtocolGame::onRecvFirstMessage` (protocolgame.cpp:1267-1470)
