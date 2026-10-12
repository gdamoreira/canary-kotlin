# Feature: Real NPC Dialogue, Scripts & Shop Data Pipeline

## 1. Explanation
Specifies the full NPC ingestion pipeline reading real Canary NPC definitions, shop inventories, travel routes, and conversation scripts (`data/npc/`, `data/npclib/`):
1. **NPC XML / Lua Definitions (`data/npc/*.xml` or `.lua`)**:
   - Identity: Name, looktype, gender.
   - Spawn coordinates: Static position on map or patrol path.
   - Speech parameters: Walk radius, floor change awareness, yell intervals.
2. **Shop Catalogues**:
   - Buy list: Items the NPC sells to players (Item ID, cost in gold).
   - Sell list: Items the NPC buys from players (Item ID, reward in gold).
3. **Dialogue & Script Hooks (`data/npclib/keyword_handler.lua`)**:
   - Greeting triggers (`hi`, `hello`).
   - Keyword trees (`trade`, `deposit`, `withdraw`, `balance`, `travel`, `yes`, `no`).
   - Quest state condition checks (evaluating player storage values).

## 2. Impact
Powers all towns, merchant economies, travel networks (ships, carpets), banking systems, blessings, and quest hand-ins.

## 3. Functionality Affected
- **NPC Spawner**: Spawns NPCs into their fixed map coordinates during server boot.
- **Shop Inventory Cache**: Populates the merchant catalog displayed in client trade windows (`0x7A`).
- **Bank System Integration**: Interacts with player bank balances stored in the database.

## 4. Agent Implementation Details
- Implement `NpcPipelineService.kt` parsing NPC definitions and populating `Npc` instances on `GameMap`.
- Implement `NpcShopRegistry.kt` keeping buy/sell price catalogs per NPC name.
- Bind dialogue triggers to the `ScriptRegistry` or embedded Lua engine.

## 5. Test Reference
- `org.opentibia.canary.creatures.NpcPipelineServiceTest`: Loads a sample merchant NPC definition, asserts buy/sell items are available in the shop catalogue, and verifies bank deposit keyword responses.
