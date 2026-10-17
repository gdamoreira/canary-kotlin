# Wave 91: OTC Login Preamble, Client OS Record and the OTCR Outfit Block

## 1. Explanation

Canary opens `ProtocolGame::login` with `sendClientLoginPreamble(operatingSystem)`
(protocolgame.cpp:936, 12361-12378), the client feature negotiation:

- `sendFeatures` (protocolgame.cpp:10682-10703) — a `0x43` frame carrying an
  OTCv8 feature map, queued only when the login announced an OTCv8 build
  (`otclientV8 > 0`, probe at protocolgame.cpp:1378-1383);
- `sendOTCRFeatures` (protocolgame.cpp:10706-10725) — a `0x43` frame with the
  configured enabled/disabled OTCR feature ids, queued for every other
  OTClient (`operatingSystem >= CLIENTOS_OTCLIENT_LINUX`), which also sets the
  session's `isOTCR` (protocolgame.hpp:642);
- a `0x32 0x00 uint16_t 0x00` opcode frame for every OTClient, which lets
  OTClient enable its extended-opcode module channel
  (`ProtocolGame::parseExtendedOpcode`, otclient protocolgameparse.cpp:3817).

`isOTCR` then gates the OTCR-only frames. The port had the writers
(`LoginFeatureWriter`, `OutfitWindowWriter.writeOutfitWindowCustomOTCR`,
`MiscEffectWriter`) but never the composition: the preamble was never sent, the
session never recorded `isOTCR`, and the port faked OTClient's module-channel
enable with an extended-opcode-0 frame at the end of the login tail.

## 2. Impact

- OTClient-family clients now receive the real feature negotiation, so modules
  are enabled by Canary's own opcode frame instead of a port-only hack, and the
  OTCR feature ids (`101`/`102`/`103`/`118`) reach the client.
- OTCR clients now receive the outfit window's custom block — the owned
  wings/auras/effects/shaders lists (`sendOutfitWindowCustomOTCR`,
  protocolgame.cpp:9254, 11880-11956) — on both the current-profile and
  old-protocol layouts.
- The reported client operating system reaches the player
  (`Player::setOperatingSystem`, protocolgame.cpp:1031), which the C++
  `player->getOperatingSystem() >= CLIENTOS_OTCLIENT_LINUX` gates read.
- Session players receive the world attached-effects registry
  (`g_game().getAttachedEffects()`, player_attached_effects.cpp:56), without
  which `PlayerAttachedEffects::tameShader` (player_attached_effects.cpp:674)
  could never resolve a shader and the OTCR shader list stayed empty.

## 3. Functionality Affected

- **Outbound login sequence** — two/three new frames before every other world
  frame on any OTClient login; nothing is emitted for a plain Tibia client
  (`operatingSystem < 10` and `otclientV8 == 0`). Because C++ writes one
  message per `writeToOutputBuffer`, the feature frame and the opcode frame are
  separate transport messages.
- **Outbound `0xC8` outfit window** — the trailing OTCR block (props +
  attached effects) for `isOTCR` clients.
- **Session state** — `ProtocolGame::operatingSystem`, `otclientV8`, `isOTCR`;
  `Player::operatingSystem`; the `Player::client->oldProtocol` seam.
- **Config surface** — `g_configManager().getEnabledFeaturesOTC()` /
  `getDisabledFeaturesOTC()` joined the handler, with the C++
  `loadLuaOTCFeatures` fallback (101/102/103/118).
- **Removed** — the port-only "extended opcode 0" login-tail enable.

## 4. Agent Implementation Details

- `network/protocol/login/LoginFeatureWriter.kt` — `writeFeatures`,
  `writeOTCRFeatures`, `writeClientLoginOpcodeFrame` (one frame each) and
  `sendsOTCRFeatures(operatingSystem, otclientV8)` (the `sendOTCRFeatures`
  invocation predicate, i.e. the resulting `isOTCR`). The earlier
  `writeClientLoginPreamble` concatenated two C++ messages into one frame and
  was replaced.
- `network/protocol/GameHandler.kt` — session fields `operatingSystem`,
  `otclientV8`, `isOTCR`; the OTCv8 probe parsed from the decrypted RSA block
  after the challenge; `sendClientLoginPreamble(ctx)` called from both login
  paths (game login before the load, livestream viewer before the admission
  check, protocolgame.cpp:12381); `player.setOperatingSystem(...)` and
  `oldProtocolProvider` bound after the load; `configManager` seam with the
  fallback lists; the versioned `writeFrame<T>` helper.
- `creatures/Creature.kt` — `Player.oldProtocolProvider`, `Player.clientState`
  (`PlayerClientState`), `getOperatingSystem`/`setOperatingSystem`, and the new
  `attachedEffectDefinitions` ctor parameter forwarded to `PlayerFeatures`.
- `network/protocol/outfit/OutfitWindowComposer.kt` — `isOTCR` parameter and
  `buildCustomOtcrFrame` (registry order, owned-only filtering).
- `creatures/PlayerFeatures.kt` — the attached-effects registry is now
  genuinely injected (session players receive the world registry).
- No shared mutable state crosses the Netty thread/world boundary: the
  preamble is written on the login thread through the pipeline encoder, and the
  composer reads per-session state captured in the sink closure.

## 5. Test Reference

- `network/protocol/GameLoginPreambleTest` (6) — OTClient OTCR frames, plain
  client suppression, OTCv8 feature map, no preamble on a failed
  authentication, the player's recorded OS, the livestream viewer preamble.
- `network/protocol/outfit/OutfitWindowComposerTest` (+3) — owned-entry
  filtering, non-OTCR suppression, legacy payload block.
- `network/protocol/login/LoginFeatureWriterTest` (reworked) — per-frame
  writers and the `sendsOTCRFeatures` predicate.
- `network/protocol/GameHandlerLoginVipBurstTest` — the removed tail hack.
- Full suite: **836 suites / 8534 tests, all green.**

## 6. Canary Original Code Reference

- **Source Files**: `src/server/network/protocol/protocolgame.cpp`,
  `src/server/network/protocol/protocolgame.hpp`,
  `src/creatures/players/player.cpp`,
  `src/creatures/players/components/player_attached_effects.cpp`,
  `src/config/configmanager.cpp`; client side
  `src/client/protocolgameparse.cpp` (OTClient Redemption)
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `ProtocolGame::sendClientLoginPreamble`
  (protocolgame.cpp:12361-12378), `sendFeatures` (10682-10703),
  `sendOTCRFeatures` (10706-10725), the OTCv8 probe (1378-1383),
  `Player::setOperatingSystem` (player.cpp:1010-1016),
  `sendOutfitWindowCustomOTCR` (11880-11956), `AddOutfitCustomOTCR` (11869),
  frames `0x43`, `0x32`, and the OTCR tail of `0xC8`.
