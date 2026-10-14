# Feature: Inspection (Look At) & Text Message Protocol

## 1. Explanation
Implements item, creature, and tile inspection (`Look At`) along with the server-to-client textual message dispatch system (`src/server/network/protocol/protocolgame.cpp` lines 1854-1859):
- **Opcode `0x8C` (`parseLookAt`)**:
  - Sent when a player Shift+Clicks or uses "Look" on a tile position, inventory slot, or container slot.
- **Opcode `0x8D` (`parseLookInBattleList`)**:
  - Sent when a player looks at a creature directly in the Battle List window.
- **Server Text Response (`sendTextMessage` - Opcode `0xB4`)**:
  - Dispatches inspection text or status notifications to the client screen with formatting and message classes:
    - `MESSAGE_STATUS` (White text in Server Log & Console)
    - `MESSAGE_INFO_DESCR` (Green text in center screen and console)
    - `MESSAGE_STATUS_WARNING` (Red warning banner)
    - `MESSAGE_EVENT_ADVANCE` (Level / Skill up notification)

## 2. Impact
Critical for player information feedback. Informs players of equipment stats (attack, defense, armor), item descriptions, container capacities, creature names, health percentages, and player guild/vocation info.

## 3. Functionality Affected
- **Item Description Builder**: Constructs description strings based on item type attributes (e.g. *"You see a demon shield (Def:35). It weighs 26.00 oz."*).
- **Creature Description Builder**: Constructs creature info strings (e.g. *"You see a dragon. Health: 100%."* or *"You see Alice (Level 120). She is an elite knight."*).
- **Text Packet Framing**: Serializes message class byte and description string.

## 4. Agent Implementation Details
- Implement `LookPacketHandler.kt` routing `0x8C` and `0x8D`.
- Implement `DescriptionFormatter.kt` generating standard Canary description texts.
- Implement `sendTextMessage` in `GameHandler` / `ProtocolGame`.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.LookPacketHandlerTest`: Tests sending `0x8C` on an item and asserts that response contains opcode `0xB4` and the correct attribute string.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
