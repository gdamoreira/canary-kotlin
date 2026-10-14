# Feature: Client Item Manipulation & Container Interaction Protocol

## 1. Explanation
Implements all client-to-server item usage, dragging, equipment, and container browsing opcodes (`src/server/network/protocol/protocolgame.cpp` lines 1790-1884):
- **Item Usage**:
  - `0x82` (`parseUseItem`): Using an item on ground, inventory, or inside container.
  - `0x83` (`parseUseItemEx`): Using an item with crosshair onto another target position/item (e.g. shovel on loose sand, rope on hole).
  - `0x84` (`parseUseWithCreature`): Using an item with crosshair directly onto a creature ID (e.g. health potion on party member).
  - `0x85` (`parseRotateItem`): Rotating directional items (e.g. chairs, tables).
- **Item Movement (Dragging / Throwing)**:
  - `0x78` (`parseThrow`): Moving an item from source (Position / Slot / Container) to destination (Ground / Container / Slot) with count.
- **Container Browsing**:
  - `0x87` (`parseCloseContainer`): Closing an open container window index.
  - `0x88` (`parseUpArrowContainer`): Opening the parent container holding the current container.
  - `0x95` (`parseOpenParentContainer`): Navigating up container hierarchy.

## 2. Impact
Essential for player inventory management, using equipment, looting monsters, organizing backpacks, opening chests, and using tools.

## 3. Functionality Affected
- **Distance Verification**: Validates that ground items are within reach ($\le 1$ tile for usage, $\le 7$ tiles for throwing).
- **Weight & Capacity Validation**: Rejects moves if the player capacity is exceeded.
- **Container Slot Limits**: Rejects items if the destination container is full.
- **Visual Feedback Packets**: Emits `sendAddContainerItem`, `sendRemoveContainerItem`, `sendUpdateContainerItem`, or `sendCancelWalk`.

## 4. Agent Implementation Details
- Implement `ItemInteractionPacketHandler.kt` parsing source/destination parameters.
- Route actions to `ScriptRegistry.executeAction` or default container transfer logic.
- Update `Player` inventory and broadcast tile updates to spectators.

## 5. Test Reference
- `org.opentibia.canary.network.protocol.ItemInteractionPacketHandlerTest`: Tests dragging item from ground to backpack, moving item between containers, and using a closed backpack emitting container open packets (`0x7D`).


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
