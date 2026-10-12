# Feature: NPC Interaction, Dialogue & Shop Window System

## 1. Explanation
Manages Non-Player Characters (`Npc`), conversation state machines, keyword handlers, and the in-game NPC trade window (`data/npclib/`, `src/creatures/npcs/`). Supports player greetings (`hi`), trade invocations (`trade`), quest dialogues, buying/selling items, bank balances, and farewells (`bye`).

## 2. Impact
Key to character progression, buying basic equipment, potions, runes, traveling via boats/carpets, promoting vocations, and undertaking quests.

## 3. Functionality Affected
- **Dialogue State Machine**: Tracks conversation focus per player (`IDLE`, `TALKING`, `TRADING`).
- **Keyword Pattern Matching**: Matches player chat strings with configured keyword responses.
- **NPC Trade Window**: Sends `sendShop` (`0x7A`) with available goods, prices, and player balance; processes `0x7A` buy and `0x7B` sell requests.

## 4. Agent Implementation Details
- Implement `Npc.kt` with a thread-safe conversation context.
- Implement `KeywordHandler.kt` parsing patterns (e.g., regex/string tokens for "hi", "trade", "yes", "no").
- Implement `NpcShopHandler.kt` executing purchases and sales (verifying player gold, weight capacity, and inventory space).

## 5. Test Reference
- `org.opentibia.canary.creatures.NpcDialogueTest`: Tests player greeting NPC, initiating trade, and buying a potion.
