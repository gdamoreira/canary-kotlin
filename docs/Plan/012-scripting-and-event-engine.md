# Feature: Scripting & Extensibility (Lua / Kotlin Scripting Engine)

## 1. Explanation
Canary provides an extensive Lua scripting layer (`data/scripts`) controlling actions, talkactions, spells, monsters, movements, weapon scripts, and quest callbacks. In Kotlin, this can be implemented via LuaJ / Kahlua / GraalVM Lua, or a native Kotlin DSL / script engine.

## 2. Impact
Extremely high. Over 80% of specific content, quests, events, boss mechanics, and custom behavior in Canary is driven by scripts rather than compiled C++ code.

## 3. Functionality Affected
- **Action Scripts**: `onUse(player, item, fromPosition, target, toPosition, isHotkey)`.
- **Movement Scripts**: `onStepIn(creature, item, position, fromPosition)` / `onStepOut`.
- **CreatureEvents**: `onDeath`, `onKill`, `onLogin`, `onLogout`, `onPrepareDeath`.
- **Global Events**: Timers, startup callbacks, periodic map events.

## 4. Agent Implementation Details
- Provide scripting bridge (via embedded Lua interpreter or native Kotlin DSL).
- Register Lua API functions mirroring Canary's Lua functions (`Player:addItem`, `Tile:getTopCreature`, `Position:sendMagicEffect`, etc.).
- Implement script hot-reloading mechanism (`/reload scripts`).

## 5. Test Reference
- `org.opentibia.canary.scripting.ScriptEngineTest`: Execute test script registering an item action, simulate trigger, assert state change.
