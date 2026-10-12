# Feature: Trade, Modal Windows & Client UI Protocol

## 1. Explanation
Implements safe player-to-player trade, server modal dialog windows, and in-game UI requests (`src/server/network/protocol/protocolgame.cpp` lines 1810-1821, `modal_window/`):
- **Player-to-Player Safe Trade**:
  - `0x7D` (`parseRequestTrade`): Proposes a trade with another player standing within 2 tiles.
  - `0x7E` (`parseLookInTrade`): Inspects an offered item in the trade window.
  - `0x7F` (`playerAcceptTrade`): Accepts the trade proposal.
  - `0x80` (`playerCloseTrade`): Cancels the trade and returns items to original owners.
- **Modal Windows**:
  - `0xFA` (`parseModalWindowAnswer`): Dispatches button ID and choice ID selected by player on a server popup dialog.
  - Server Packet `0xFA` (`sendModalWindow`): Serializes title, description, buttons (e.g. "Yes", "No", "Cancel"), and selectable options list.
- **Player Text & House Windows**:
  - `0x89` (`parseTextWindow`): Reading/writing letters and blackboards.
  - `0x8A` (`parseHouseWindow`): Submitting house access lists (`aleta grav`, `aleta sio`, `aleta som`).

## 2. Impact
Essential for safe player trading, in-game NPC confirmation prompts, quest dialog choices, and house management interfaces.

## 3. Functionality Affected
- **Two-Way Trade State Machine**: Handles item locking, capacity checks, distance verification (auto-cancels if either player walks away), and atomic item exchange.
- **Modal Dialog Dispatcher**: Bridges script callbacks waiting for player UI selections.

## 4. Agent Implementation Details
- Implement `TradeManager.kt` tracking active `TradeSession` instances between two players.
- Implement `ModalWindowManager.kt` assigning modal IDs and routing player button selections back to coroutine jobs or script callbacks.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.TradeProtocolTest`: Tests trade proposal between two players, both accepting, and verifying item swap. Tests walking away cancelling trade.
