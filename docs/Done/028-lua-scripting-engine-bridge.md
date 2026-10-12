# Feature: Lua Scripting Engine Bridge (LuaJ / GraalVM)

## 1. Explanation
Embeds a Lua runtime into the Kotlin server (`src/lua/`, `data/scripts/`). Enables direct execution of Canary's massive library of existing Lua scripts (hundreds of actions, movements, spells, weapons, creatureevents, and globalevents) without rewriting them into Kotlin.

## 2. Impact
Massive ecosystem compatibility. Allows drop-in usage of the existing `data/scripts` folder from Canary, preserving community-developed quests, mechanics, and boss scripts.

## 3. Functionality Affected
- **Lua Userdata & Metatables**: Exposes `Player`, `Creature`, `Item`, `Tile`, `Position`, `Game` objects to the Lua runtime.
- **Global Event Triggers**: Registers `onUse`, `onStepIn`, `onCastSpell`, `onKill`, `onDeath`, and `onThink` callbacks.
- **Hot-Reloading**: Re-reading modified Lua scripts while the server is running (`/reload scripts`).

## 4. Agent Implementation Details
- Integrate LuaJ (`org.luaj:luaj-jse`) or GraalVM Polyglot Lua into `build.gradle.kts`.
- Implement `LuaEnvironment.kt` binding Lua globals (`Game`, `Item`, `Player`, `Position`, `Tile`).
- Implement `LuaScriptLoader.kt` scanning `data/scripts/` and registering actions/movements into `ScriptRegistry`.

## 5. Test Reference
- `org.opentibia.canary.lua.LuaScriptBridgeTest`: Executes a real Lua snippet from Canary (`Player:getHealth()`, `player:addHealth(50)`) and asserts modification of the Kotlin `Player` instance.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
