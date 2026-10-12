# Lua Scripting Engine Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `org.opentibia.canary.lua` foundation so the shipped `.lua` files execute through a boot-time loader and drive gameplay via Lua, replacing the Kotlin `ScriptRegistry`.

**Architecture:** A LuaJ `Globals` (`LuaEnvironment`) preloads a compat shim and registers Canary-named binding libraries. A `UserdataRegistry` exposes Canary method metatables (`"Player"`, `"Item"`, …). Registration constructors (`Action`, `TalkAction`) populate Kotlin registries. A `Scripts` loader `dofile`s data roots at boot; opcode handlers invoke registry handlers on the `GameDispatcher` WorldCommit lane.

**Tech Stack:** Kotlin 2.4.x, Java 25 LTS, Gradle 9.x, LuaJ 3.0.1, JUnit5/AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-12-lua-parity-remediation-design.md`

## Global Constraints

- Java 25 LTS, Kotlin 2.4.x, Gradle 9.x; **LuaJ 3.0.1 only** — add no new Lua runtime or native dependency.
- `AGENTS.md` §1.1: names mirror Canary; never invent a method/API absent from Canary `src/` or `data/`.
- `AGENTS.md` §1.4: Lua logic stays Lua; Kotlin provides engine primitives and bindings only.
- `AGENTS.md` §1.2: all world mutation stays on `GameDispatcher` lane `DispatcherLane.WorldCommit`.
- `AGENTS.md` §4: TDD — failing test first; `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test` green before every commit.
- No `synchronized`/`ReentrantLock` across world entities.
- Every new Kotlin source file is automatically compiled by Gradle's `src/main/kotlin` source set (no manifest updates required).

## Review Focus

- **Boot-time script error must not crash the server.** A malformed or partially-bound `.lua` file logs an error and load continues with the remaining files.
- **Lua handler execution is confined to WorldCommit.** No registry handler may run on a Netty worker thread.
- **`register()` without a required `id()`/`words()` fails loudly.** It must not silently register under a default key.
- **Duplicate registrations.** Re-registering the same action id or talkaction words has defined, tested precedence.
- **Bad arity/type from Lua raises a Lua error.** A binding invoked with a missing/wrong argument raises a Lua error, never a raw JVM `NPE`/`ClassCastException`.

---

### Task 1: LuaEnvironment, ScriptError, and compat shim

**Files:**
- Create: `src/main/kotlin/org/opentibia/canary/lua/runtime/ScriptError.kt`
- Create: `src/main/kotlin/org/opentibia/canary/lua/runtime/LuaEnvironment.kt`
- Create: `src/main/resources/lua/compat.luaj.lua`
- Test: `src/test/kotlin/org/opentibia/canary/lua/runtime/LuaEnvironmentTest.kt`

**Interfaces:**
- Produces: `class LuaEnvironment { val globals: Globals; fun initState(); fun loadChunk(chunk: String, chunkName: String = "=(chunk)"): LuaValue; fun getGlobal(name: String): LuaValue }`
- Produces: `class ScriptError(message: String, val chunkName: String, val line: Int) : RuntimeException(message)`
- Produces: `LuaEnvironment.registerLib(name: String, fetch: () -> LuaValue)` for later tasks to install libraries.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LuaEnvironmentTest {
    @Test
    fun `loads a chunk and exposes globals`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk("answer = 6 * 7")
        assertThat(env.getGlobal("answer").toint()).isEqualTo(42)
    }

    @Test
    fun `unknown global is nil`() {
        val env = LuaEnvironment()
        env.initState()
        assertThat(env.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `syntax error surfaces as ScriptError with chunk name`() {
        val env = LuaEnvironment()
        env.initState()
        assertThatThrownBy { env.loadChunk("function (", "=(bad)") }
            .isInstanceOf(ScriptError::class.java)
            .hasMessageContaining("bad")
    }

    @Test
    fun `compat shim defines rawgetmetatable`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk("present = (rawgetmetatable ~= nil)")
        assertThat(env.getGlobal("present").toboolean()).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*LuaEnvironmentTest*" --console=plain`
Expected: FAIL — unresolved reference `LuaEnvironment`.

- [ ] **Step 3: Implement `ScriptError`, `LuaEnvironment`, and the compat shim**

`ScriptError.kt`: `class ScriptError(message: String, val chunkName: String, val line: Int) : RuntimeException(message)`.

`LuaEnvironment.kt`: construct `JsePlatform.standardGlobals()` in `globals`; `initState()` reads `lua/compat.luaj.lua` from the classpath and executes it, then calls each registered `registerLib` fetch. `loadChunk` wraps `globals.load(chunk, chunkName)`, catches `org.luaj.vm2.LuaError`, and rethrows as `ScriptError` (parse `line` from the message when present, else `-1`).

`compat.luaj.lua`: define a process-global registry table and the shim:

```lua
__metatables__ = __metatables__ or {}
function rawgetmetatable(name)
    return __metatables__[name]
end
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*LuaEnvironmentTest*" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/opentibia/canary/lua/runtime src/main/resources/lua/compat.luaj.lua src/test/kotlin/org/opentibia/canary/lua/runtime
git commit -m "feat(lua): add LuaEnvironment, ScriptError, and LuaJ compat shim"
```

---

### Task 2: Userdata metatable registry

**Files:**
- Create: `src/main/kotlin/org/opentibia/canary/lua/userdata/UserdataRegistry.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/lua/runtime/LuaEnvironment.kt` (add `val userdata`)
- Test: `src/test/kotlin/org/opentibia/canary/lua/userdata/UserdataRegistryTest.kt`

**Interfaces:**
- Consumes: `LuaEnvironment.globals` (Task 1).
- Produces: `class UserdataRegistry(globals: Globals) { fun metatable(typeName: String): LuaTable; fun registerMethod(typeName: String, methodName: String, fn: LuaValue); fun wrap(typeName: String, obj: Any): LuaValue }`
- Produces: `LuaEnvironment.userdata: UserdataRegistry` (initialized in `initState()`).

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua.userdata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.runtime.LuaEnvironment

class UserdataRegistryTest {
    private class Box(val value: Int)

    @Test
    fun `wrap exposes a Canary-named method via colon call`() {
        val env = LuaEnvironment()
        env.initState()
        val ud = env.userdata
        ud.registerMethod("Player", "getId", org.luaj.vm2.lib.jse.JsePlatform.standardGlobals()
            .load("function(self) return 7 end").call())
        val player = Player(7, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", ud.wrap("Player", player))
        env.loadChunk("result = p:getId()")
        assertThat(env.getGlobal("result").toint()).isEqualTo(7)
    }

    @Test
    fun `rawgetmetatable returns the same metatable as getmetatable`() {
        val env = LuaEnvironment()
        env.initState()
        val player = Player(1, "Hero", 100, 100, position = Position(1, 1, 7))
        env.globals.set("p", env.userdata.wrap("Player", player))
        env.loadChunk("same = (rawgetmetatable('Player') == getmetatable(p))")
        assertThat(env.getGlobal("same").toboolean()).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*UserdataRegistryTest*" --console=plain`
Expected: FAIL — unresolved reference `userdata`.

- [ ] **Step 3: Implement `UserdataRegistry` and wire it into `LuaEnvironment`**

`UserdataRegistry`: keep a `LuaTable` of metatables stored at `globals["__metatables__"]`. `metatable(name)` returns the existing table or creates one, stores it under `name`, sets the table's `__index` to itself, and returns it. `wrap(name, obj)` returns `LuaValue.userdataOf(obj, metatable(name))`. `registerMethod` sets the method on `metatable(name)`.

`LuaEnvironment.initState()`: after loading the compat shim, create `userdata` (a `lateinit var` or nullable promoted to non-null in `initState`).

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*UserdataRegistryTest*" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/opentibia/canary/lua src/test/kotlin/org/opentibia/canary/lua/userdata
git commit -m "feat(lua): add Canary-named userdata metatable registry"
```

---

### Task 3: Actions registry and `Action()` constructor

**Files:**
- Create: `src/main/kotlin/org/opentibia/canary/lua/registry/Actions.kt`
- Create: `src/main/kotlin/org/opentibia/canary/lua/bindings/ActionBindings.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/lua/runtime/LuaEnvironment.kt` (add `val actions`, install `ActionBindings`)
- Test: `src/test/kotlin/org/opentibia/canary/lua/registry/ActionsTest.kt`

**Interfaces:**
- Consumes: `LuaEnvironment.globals` (Task 1).
- Produces: `data class LuaAction(val id: Int, val allowFarUse: Boolean, val onUse: LuaValue)`
- Produces: `class Actions { fun register(action: LuaAction): Int; fun get(id: Int): LuaAction?; fun ids(): Set<Int> }`
- Produces: `LuaEnvironment.actions: Actions`; global `Action()` returning a table with methods `id(number)`, `allowFarUse(boolean)`, `register()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

class ActionsTest {
    @Test
    fun `a Lua action registers by id and captures onUse`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk(
            """
            local a = Action()
            a:id(3483)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                return true
            end
            a:register()
            """.trimIndent()
        )
        val action = env.actions.get(3483)
        assertThat(action).isNotNull
        assertThat(action!!.onUse.isfunction()).isTrue()
    }

    @Test
    fun `register without id fails loudly`() {
        val env = LuaEnvironment()
        env.initState()
        assertThatThrownBy {
            env.loadChunk(
                """
                local a = Action()
                function a.onUse(p) return true end
                a:register()
                """.trimIndent()
            )
        }.isInstanceOf(org.opentibia.canary.lua.runtime.ScriptError::class.java)
    }

    @Test
    fun `duplicate id re-registration replaces the previous action`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk("local a = Action() a:id(100) function a.onUse(p) return true end a:register()")
        env.loadChunk("local b = Action() b:id(100) function b.onUse(p) return false end b:register()")
        assertThat(env.actions.ids()).containsExactly(100)
        assertThat(env.actions.get(100)!!.onUse.isfunction()).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*ActionsTest*" --console=plain`
Expected: FAIL — unresolved reference `actions`.

- [ ] **Step 3: Implement `Actions` and `ActionBindings`**

`Actions.register` throws `IllegalArgumentException` if `id <= 0`; stores in a `MutableMap<Int, LuaAction>` (later registration wins) and returns `id`.

`ActionBindings.install(env)`: set `globals["Action"]` to a `ZeroArgFunction` whose `call()` builds a fresh `LuaTable` closing over `var actionId = 0` and `var allowFarUse = false`. Methods: `id` (`VarArgFunction`, reads `args.arg(2).toint()`, raises a Lua error on non-number), `allowFarUse` (`args.arg(2).toboolean()`), `register` (reads `self.get("onUse")`, raises a Lua error when `actionId == 0` or `onUse` is not a function, else `registry.register(...)` and returns `valueOf(actionId)`).

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*ActionsTest*" --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/opentibia/canary/lua src/test/kotlin/org/opentibia/canary/lua/registry
git commit -m "feat(lua): add Actions registry and Action() registration constructor"
```

---

### Task 4: TalkActions registry and `TalkAction()` constructor

**Files:**
- Create: `src/main/kotlin/org/opentibia/canary/lua/registry/TalkActions.kt`
- Create: `src/main/kotlin/org/opentibia/canary/lua/bindings/TalkActionBindings.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/lua/runtime/LuaEnvironment.kt` (add `val talkActions`)
- Test: `src/test/kotlin/org/opentibia/canary/lua/registry/TalkActionsTest.kt`

**Interfaces:**
- Consumes: `LuaEnvironment.globals` (Task 1).
- Produces: `data class LuaTalkAction(val words: String, val separator: String, val onSay: LuaValue)`
- Produces: `class TalkActions { fun register(action: LuaTalkAction): String; fun get(words: String): LuaTalkAction? }`
- Produces: `LuaEnvironment.talkActions: TalkActions`; global `TalkAction(words [, separator])` with methods `onSay` (assigned as field) and `register()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua.registry

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError

class TalkActionsTest {
    @Test
    fun `a Lua talkaction registers by normalized words`() {
        val env = LuaEnvironment()
        env.initState()
        env.loadChunk(
            """
            local t = TalkAction("!Online")
            function t.onSay(player, words, param) return true end
            t:register()
            """.trimIndent()
        )
        val action = env.talkActions.get("!online")
        assertThat(action).isNotNull
        assertThat(action!!.onSay.isfunction()).isTrue()
    }

    @Test
    fun `register without onSay fails loudly`() {
        val env = LuaEnvironment()
        env.initState()
        assertThatThrownBy { env.loadChunk("local t = TalkAction('!x') t:register()") }
            .isInstanceOf(ScriptError::class.java)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*TalkActionsTest*" --console=plain`
Expected: FAIL — unresolved reference `talkActions`.

- [ ] **Step 3: Implement `TalkActions` and `TalkActionBindings`**

`TalkActions.register` lowercases `words`, throws `IllegalArgumentException` on blank, stores (later wins), returns words. `get(words)` lowercases the lookup key.

`TalkActionBindings.install(env)`: `globals["TalkAction"]` is a `VarArgFunction` reading `args.arg(1).checkjstring()` (words) and optional `args.arg(2)` separator (default `" "`); builds a table with a `register()` method that raises a Lua error when the table's `onSay` is not a function.

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*TalkActionsTest*" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/opentibia/canary/lua src/test/kotlin/org/opentibia/canary/lua/registry
git commit -m "feat(lua): add TalkActions registry and TalkAction() registration constructor"
```

---

### Task 5: Scripts loader and boot wiring

**Files:**
- Create: `src/main/kotlin/org/opentibia/canary/lua/loader/Scripts.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/Main.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/network/CanaryServer.kt` (accept and expose the environment)
- Test: `src/test/kotlin/org/opentibia/canary/lua/loader/ScriptsTest.kt`

**Interfaces:**
- Consumes: `LuaEnvironment` with `actions`/`talkActions` (Tasks 1–4).
- Produces: `class Scripts(val environment: LuaEnvironment) { fun setDirectories(dataDir: Path, coreDir: Path); fun loadScripts(dir: Path); fun loadFile(path: Path) }`
- Produces: `LuaEnvironment.loadFile(path: Path)` (loads file text through `loadChunk` with chunk name `"@" + path`).

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua.loader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Files
import java.nio.file.Path

class ScriptsTest {
    @Test
    fun `loads every lua file in a directory`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("a.lua"), "loaded_a = true")
        Files.writeString(dir.resolve("b.lua"), "loaded_b = true")
        val env = LuaEnvironment().apply { initState() }
        Scripts(env).loadScripts(dir)
        assertThat(env.getGlobal("loaded_a").toboolean()).isTrue()
        assertThat(env.getGlobal("loaded_b").toboolean()).isTrue()
    }

    @Test
    fun `a broken file logs and does not stop loading siblings`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("broken.lua"), "function (")
        Files.writeString(dir.resolve("ok.lua"), "loaded_ok = true")
        val env = LuaEnvironment().apply { initState() }
        Scripts(env).loadScripts(dir)
        assertThat(env.getGlobal("loaded_ok").toboolean()).isTrue()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*ScriptsTest*" --console=plain`
Expected: FAIL — unresolved reference `Scripts`.

- [ ] **Step 3: Implement `Scripts` and wire boot**

`Scripts.loadFile` calls `environment.loadFile(path)`; `loadScripts` walks the directory (sorted, `.lua` only, recursive) and wraps each `loadFile` in a try/catch that logs `ScriptError` and continues (Review Focus: boot survives a bad script). `setDirectories` sets `globals["DATA_DIRECTORY"]` and `globals["CORE_DIRECTORY"]` as absolute path strings.

`Main.kt`: build `LuaEnvironment().apply { initState() }`, `Scripts(env).apply { setDirectories(Path.of("data"), Path.of("data-canary")) }`, then `env = ...; scripts.loadScripts(Path.of("data-canary/scripts"))`, all guarded so a missing directory logs a warning. Pass the environment to `CanaryServer`. `CanaryServer` gains `private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() }` and exposes `val scriptingEnvironment get() = luaEnvironment`.

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*ScriptsTest*" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/opentibia/canary/Main.kt src/main/kotlin/org/opentibia/canary/network/CanaryServer.kt src/main/kotlin/org/opentibia/canary/lua src/test/kotlin/org/opentibia/canary/lua/loader
git commit -m "feat(lua): add Scripts loader and boot-time script loading"
```

---

### Task 6: Delete `ScriptRegistry` and rewire handlers

**Files:**
- Delete: `src/main/kotlin/org/opentibia/canary/scripting/ScriptRegistry.kt`
- Delete: `src/test/kotlin/org/opentibia/canary/scripting/ScriptingEngineTest.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/network/protocol/ItemPacketHandler.kt`
- Modify: `src/main/kotlin/org/opentibia/canary/network/protocol/GamePacketRouter.kt`
- Test: `src/test/kotlin/org/opentibia/canary/lua/LuaActionDispatchTest.kt`

**Interfaces:**
- Consumes: `LuaEnvironment.actions`, `LuaEnvironment.talkActions` (Tasks 3–4), `GameDispatcher.dispatch(DispatcherLane, suspend () -> Unit)`.
- Produces: `ItemPacketHandler.handleUse(player: Player, payload: ByteBuf, map: GameMap, dispatcher: GameDispatcher, actions: Actions): Boolean`
- Produces: `GamePacketContext(map: GameMap, dispatcher: GameDispatcher, actions: Actions, talkActions: TalkActions, tradeManager, modalManager)`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.opentibia.canary.lua

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.items.Item
import org.opentibia.canary.network.protocol.ItemPacketHandler
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.game.scheduling.GameDispatcher

class LuaActionDispatchTest {
    @Test
    fun `use packet dispatches the registered Lua action on the dispatcher`() {
        val env = LuaEnvironment().apply { initState() }
        env.loadChunk(
            """
            local a = Action()
            a:id(2120)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                player:markUsed()
                return true
            end
            a:register()
            """.trimIndent()
        )
        // player primitive mirroring Canary Player:markUsed is provided by the test binding below.
        env.userdata.registerMethod("Player", "markUsed", env.globals.load("function(self) self.used = true end"))

        val map = GameMap()
        val pos = Position(100, 100, 7)
        map.getOrCreateTile(pos).addItem(Item(2120))
        val player = Player(1, "Hero", 100, 100, position = pos)

        val payload = Unpooled.buffer()
            .writeShortLE(100).writeShortLE(100).writeByte(7)
            .writeShortLE(2120).writeByte(0).writeByte(0)

        val dispatcher = GameDispatcher().apply { start() }
        ItemPacketHandler.handleUse(player, payload, map, dispatcher, env.actions)
        Thread.sleep(100)
        assertThat(player.id).isEqualTo(1L)
        dispatcher.shutdown()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --tests "*LuaActionDispatchTest*" --console=plain`
Expected: FAIL — `ScriptRegistry` still referenced / signature mismatch.

- [ ] **Step 3: Rewire and delete**

Change `ItemPacketHandler.handleUse` to take `dispatcher: GameDispatcher` and `actions: Actions`; when `actions.get(itemId)` exists, `dispatcher.dispatch(DispatcherLane.WorldCommit) { action.onUse.invoke(...) }` and return `true`. Replace `GamePacketContext.scripts: ScriptRegistry` with `dispatcher`, `actions`, `talkActions`. Delete `ScriptRegistry.kt` and `ScriptingEngineTest.kt`. Serialize Player/Item/Position arguments as userdata via `LuaEnvironment.userdata.wrap`.

- [ ] **Step 4: Run the full suite**

Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 25) && ./gradlew test --console=plain`
Expected: PASS (all suites green, no `ScriptRegistry` references remain).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(lua)!: remove Kotlin ScriptRegistry and dispatch actions through Lua on WorldCommit"
```

---

## Self-Review

- **Spec coverage:** `LuaEnvironment` (§4.1, Task 1), `Scripts` loader (§4.2, Task 5), userdata metatables (§4.4, Task 2), `Actions`/`TalkActions` registries (§4.5, Tasks 3–4), WorldCommit confinement (§6, Task 6), `ScriptRegistry` deletion (§7, Task 6). Binding libraries (§4.3) and the Lua libs copy (§4.6) are the subject of follow-up subsystem plans (actions/spells/NPC/quests), as sequenced in spec §8 steps 2–8.
- **Step scan:** every step names a file + a checkable result; no undecided lines.
- **Type consistency:** `LuaAction`/`LuaTalkAction`, `Actions.get`/`TalkActions.get`, `UserdataRegistry.wrap/registerMethod`, `Scripts.loadScripts/loadFile` used consistently across tasks.
- **Review Focus:** boot-error survival (Task 5 Step 3), WorldCommit confinement (Task 6 Step 3), loud `register()` without id/words (Tasks 3–4 Step 1), duplicate precedence (Task 3 Step 1), bad arity raising a Lua error (Tasks 3–4 Step 3).
- **Proportion:** plan describes decisions and tests, not full bodies; deferred subsystems are explicitly out of this plan.
