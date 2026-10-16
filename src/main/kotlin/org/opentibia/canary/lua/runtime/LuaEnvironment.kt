package org.opentibia.canary.lua.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import org.opentibia.canary.game.scheduling.EventsScheduler
import org.opentibia.canary.lua.bindings.ActionBindings
import org.opentibia.canary.lua.bindings.CreatureEventBindings
import org.opentibia.canary.lua.bindings.GlobalEventBindings
import org.opentibia.canary.lua.bindings.MoveEventBindings
import org.opentibia.canary.lua.bindings.TalkActionBindings
import org.opentibia.canary.lua.registry.Actions
import org.opentibia.canary.lua.registry.CreatureEvents
import org.opentibia.canary.lua.registry.EventCallbacks
import org.opentibia.canary.lua.registry.GlobalEvents
import org.opentibia.canary.lua.registry.MoveEvents
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.registry.TalkActions
import org.opentibia.canary.lua.userdata.UserdataRegistry
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mirrors Canary's LuaScriptInterface: owns the shared LuaJ [Globals], preloads the
 * compatibility shim, and installs the Canary-named binding libraries.
 *
 * The lifecycle follows `LuaEnvironment` (`src/lua/scripts/lua_environment.cpp`):
 * [initState] creates the state (the LuaJ standard libraries plus `debug`, matching
 * `Lua::load`'s `luaL_openlibs`), [closeState] drops it, and [reInitState] reloads by
 * closing and re-initializing. Unlike `lua_close`, the JVM reclaims the replaced tables
 * through the garbage collector; [getLuaState] reports null while closed like the C++
 * `luaState == nullptr`.
 */
class LuaEnvironment {
    private var state: Globals? = null

    /** The shared state; accessing it while closed is a programming error. */
    val globals: Globals
        get() = state ?: error("LuaEnvironment state is closed")

    lateinit var userdata: UserdataRegistry
        private set

    /** Port of `Lua::registerClass` and friends (`LuaClassRegistry`). */
    lateinit var classes: LuaClassRegistry
        private set

    /**
     * The world seam owner installed by [initState]; `null` while the state is closed.
     * The boot attaches the `g_monsters()` registry here before running the monster
     * registration scripts (`WorldLuaOperations.attachMonsters`).
     */
    var worldOperations: WorldLuaOperations? = null
        private set

    val actions = Actions()
    val talkActions = TalkActions()
    val eventCallbacks = EventCallbacks()
    val eventsScheduler = EventsScheduler()
    val itemClassifications = org.opentibia.canary.systems.forge.ItemClassifications()

    /**
     * `Item::items` (`g_luaEnvironment()`'s `ItemType` reach): the booted item
     * registry behind the Lua `ItemType(id)` lookups. Assigned by the scripting
     * boot before [initState].
     */
    var itemTypes: org.opentibia.canary.items.ItemTypeRegistry? = null

    lateinit var spells: Spells
        private set

    lateinit var creatureEvents: CreatureEvents
        private set

    lateinit var globalEvents: GlobalEvents
        private set

    lateinit var moveEvents: MoveEvents
        private set

    private val libraries = mutableListOf<() -> LuaValue>()

    fun registerLib(name: String, fetch: () -> LuaValue) {
        libraries.add(fetch)
    }

    /**
     * Binds the canonical spell registry created by `WorldLuaOperations.install`
     * (`SpellBindings` -> `SpellFunctions`), keeping `env.spells` and the Lua
     * `Spell` global on the same instance.
     */
    fun installSpells(registry: Spells) {
        spells = registry
    }

    /** `LuaEnvironment::getLuaState` (lua_environment.cpp:37-47); null while closed. */
    fun getLuaState(): Globals? = state

    fun initState() {
        state = JsePlatform.debugGlobals()
        // LuaJ targets Lua 5.2, which dropped `package.config`; the Canary
        // datapack (`libs/core/quests/loader.lua`) reads the Lua 5.1 field, so
        // the reference value is restored when the package library omits it.
        globals.get("package").takeIf { it.istable() }?.let { packageTable ->
            if (packageTable.get("config").isnil()) {
                packageTable.set("config", LuaValue.valueOf("/\n;\n?\n!\n-\n"))
            }
        }
        loadCompatShim()
        classes = LuaClassRegistry(globals)
        userdata = UserdataRegistry(globals, classes)

        // Install the Canary-named engine primitives backed by the world domain objects.
        val world = WorldLuaOperations.forEnvironment(this)
        worldOperations = world
        world.install(this)
        org.opentibia.canary.lua.bindings.core.ItemClassificationFunctions.install(this, itemClassifications)

        ActionBindings.install(this)
        TalkActionBindings.install(this)
        creatureEvents = CreatureEventBindings.install(this)
        globalEvents = GlobalEventBindings.install(this)
        moveEvents = MoveEventBindings.install(this)
        libraries.forEach { it() }
    }

    /**
     * `LuaEnvironment::closeState` (lua_environment.cpp:63-87): drop the state after
     * clearing the interface caches. The JVM releases the replaced tables through the
     * garbage collector; the C++ timer/area registries are not modelled here.
     */
    fun closeState(): Boolean {
        if (state == null) {
            return false
        }
        state = null
        worldOperations = null
        return true
    }

    /** `LuaEnvironment::reInitState` (lua_environment.cpp:57-61): close, then init. */
    fun reInitState(): Boolean {
        closeState()
        initState()
        return true
    }

    fun loadChunk(chunk: String, chunkName: String = "=(chunk)"): LuaValue {
        val compiled = try {
            globals.load(chunk, chunkName)
        } catch (e: LuaError) {
            throw toScriptError(e, chunkName)
        }
        return try {
            compiled.call()
        } catch (e: LuaError) {
            throw toScriptError(e, chunkName)
        }
    }

    fun getGlobal(name: String): LuaValue = globals.get(name)

    fun loadFile(path: Path): LuaValue {
        val source = Files.readString(path)
        return loadChunk(source, "@${path.toAbsolutePath()}")
    }

    private fun loadCompatShim() {
        val stream = javaClass.classLoader.getResourceAsStream(COMPAT_RESOURCE)
            ?: throw IllegalStateException("Missing $COMPAT_RESOURCE on classpath")
        val source = stream.bufferedReader().use { it.readText() }
        try {
            globals.load(source, "@$COMPAT_RESOURCE").call()
        } catch (e: LuaError) {
            throw toScriptError(e, COMPAT_RESOURCE)
        }
    }

    private fun toScriptError(error: LuaError, chunkName: String): ScriptError {
        val message = error.message ?: "lua error"
        val line = LINE_PATTERN.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return ScriptError(message, chunkName, line)
    }

    private companion object {
        const val COMPAT_RESOURCE = "lua/compat.luaj.lua"
        val LINE_PATTERN = Regex(":(\\d+):")
    }
}
