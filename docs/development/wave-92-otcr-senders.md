# Wave 92: OTCR Sender Composition and the Attached-Effects Host Adapter

## 1. Explanation

Wave 91 wired `ProtocolGame::isOTCR` and the OTCR outfit-window block. The
remaining OTCR-only frames live in `ProtocolGame`:

- `sendAttachedEffect` (0x34, protocolgame.cpp:11809-11819)
- `sendDetachEffect` (0x35, protocolgame.cpp:11821-11831)
- `sendShader` (0x36, protocolgame.cpp:11833-11843)
- `sendMapShader` (0x37, protocolgame.cpp:11845-11855)
- `sendPlayerTyping` (0x38, protocolgame.cpp:11857-11867)

Each returns early without `isOTCR`. Their writers
(`MiscEffectWriter.writeAttachedEffect`/`writeDetachEffect`/`writeShader`/
`writeMapShader`, `MiscWindowWriter.writePlayerTyping`) already existed, but no
sink exposed them, so the callers had nowhere to go. This wave adds the sink
surface and, with it, the player-side caller:

- `PlayerAttachedEffects` sends through `m_player.client`
  (`player_attached_effects.cpp:844-874`); the port's `AttachedEffectsClient`
  seam existed but was never composed (`client = null`), so
  `sendAttachedEffect`/`sendDetachEffect`/`sendShader`/`sendMapShader` were
  inert for every player.

The same host adapter had two explicitly documented inert defaults
(`Player::isRandomMounted`, `Player::sendOutfitWindow`), both of which the live
player can now resolve.

## 2. Impact

- An OTCR client receives the attached-effect detach/attach, creature shader,
  map shader and player-typing frames the C++ server sends; a plain Tibia
  client receives none (the `!isOTCR` gate).
- The wing/aura/effect/shader toggles reach their `sendShader`/
  `sendAttachedEffect`/`sendDetachEffect`/`sendMapShader` calls through the
  composed session transport instead of a dead seam.
- Random-mount wing/aura/effect sampling now observes the live mount flag, and
  the toggle paths that refresh the outfit window
  (`m_player.sendOutfitWindow()`, player_attached_effects.cpp:102, 118, 291,
  404, 416) actually emit `0xC8`.

## 3. Functionality Affected

- **Outbound frames**: `0x34`, `0x35`, `0x36`, `0x37`, `0x38` (all
  `isOTCR`-gated), plus the existing `0xC8` refresh now reachable from the
  attached-effects toggles.
- **Session state**: the sink carries `ProtocolGame::isOTCR`; both login paths
  set it from the preamble.
- **Attached-effects host adapter**: `isRandomMounted` and `sendOutfitWindow`
  resolve live state.

## 4. Agent Implementation Details

- `network/protocol/ProtocolSink.kt` — the interface gains `var isOTCR`
  alongside `oldProtocol`/`protocolVersion`; `ProtocolFrameSink` supplies the
  plain-Tibia default, `NettyProtocolSink` forwards it to its delegate, and the
  five senders have no-op defaults for transports without a session.
- `creatures/players/PlayerSendSink.kt` — `PlayerFrameSink` implements the five
  senders with the C++ `!isOTCR` early return and the existing writers.
- `creatures/PlayerFeatures.kt` — `attachedEffects` is composed with
  `ProtocolSinkAttachedEffectsClient`, which resolves `Player.protocolSink` per
  call (the C++ `Player::client` pointer semantics); `HostAttachedEffectsPlayer`
  forwards `isRandomMounted`/`sendOutfitWindow` to the live mount state and the
  `sendOutfitWindow` session seam (which becomes non-private).
- `network/protocol/GameHandler.kt` — both session transports receive the
  login `isOTCR`.
- No new shared state: the flag is per-session and set before any sender can
  fire (the C++ `ProtocolGame` member order).

## 5. Test Reference

- `creatures/players/PlayerFrameSinkOtcSendersTest` (5) — the five frames, the
  non-OTCR suppression, and the composed attached-effects client forwarding
  (both OTCR and plain sessions).
- `creatures/players/PlayerAttachedEffectsHostAdapterTest` (3) — the
  `sendOutfitWindow` request on a wing toggle, the random-mount sampling, and
  the unowned-wing clear path.
- Full suite: **838 suites / 8542 tests, all green.**

## 6. Canary Original Code Reference

- **Source Files**: `src/server/network/protocol/protocolgame.cpp`,
  `src/server/network/protocol/protocolgame.hpp`,
  `src/creatures/players/components/player_attached_effects.cpp`,
  `src/creatures/players/player.cpp`, `src/game/game.cpp`
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: `sendAttachedEffect`/`sendDetachEffect`/
  `sendShader`/`sendMapShader`/`sendPlayerTyping` (protocolgame.cpp:11809-11867),
  `PlayerAttachedEffects::sendAttachedEffect`…`sendMapShader`
  (player_attached_effects.cpp:844-874), `Player::isRandomMounted`
  (player.cpp:12943-12945), `Player::sendOutfitWindow` (player.cpp:2541-2543),
  `Game::sendAttachedEffect`/`sendDetachEffect` (game.cpp:12674-12691, the
  spectator fan-out left for the next wave).
