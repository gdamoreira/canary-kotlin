package org.opentibia.canary.lua.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import org.opentibia.canary.lua.bindings.ActionBindings
import org.opentibia.canary.lua.bindings.CreatureEventBindings
import org.opentibia.canary.lua.bindings.GlobalEventBindings
import org.opentibia.canary.lua.bindings.MoveEventBindings
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.lua.bindings.TalkActionBindings
import org.opentibia.canary.lua.bindings.core.ContainerFunctions
import org.opentibia.canary.lua.bindings.core.ContainerOperations
import org.opentibia.canary.lua.bindings.core.CreatureFunctions
import org.opentibia.canary.lua.bindings.core.CreatureOperations
import org.opentibia.canary.lua.bindings.core.GameFunctions
import org.opentibia.canary.lua.bindings.core.GameOperations
import org.opentibia.canary.lua.bindings.core.ItemFunctions
import org.opentibia.canary.lua.bindings.core.ItemOperations
import org.opentibia.canary.lua.bindings.core.LuaTextMessage
import org.opentibia.canary.lua.bindings.core.PlayerFunctions
import org.opentibia.canary.lua.bindings.core.PlayerOperations
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.PositionOperations
import org.opentibia.canary.lua.registry.Actions
import org.opentibia.canary.lua.registry.CreatureEvents
import org.opentibia.canary.lua.registry.GlobalEvents
import org.opentibia.canary.lua.registry.MoveEvents
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.registry.TalkActions
import org.opentibia.canary.lua.userdata.UserdataRegistry
import org.opentibia.canary.map.Position
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mirrors Canary's LuaScriptInterface: owns a single LuaJ [Globals], preloads the
 * compatibility shim, and installs the Canary-named binding libraries.
 */
class LuaEnvironment {
    val globals: Globals = JsePlatform.standardGlobals()

    lateinit var userdata: UserdataRegistry
        private set

    val actions = Actions()
    val talkActions = TalkActions()

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

    fun initState() {
        loadCompatShim()
        userdata = UserdataRegistry(globals)

        GameFunctions.install(this, NO_OP_GAME)
        PlayerFunctions.install(this, NO_OP_PLAYER)
        CreatureFunctions.install(this, NO_OP_CREATURE)
        ItemFunctions.install(this, NO_OP_ITEM)
        ContainerFunctions.install(this, NO_OP_CONTAINER)
        PositionFunctions.install(this, NO_OP_POSITION)

        ActionBindings.install(this)
        TalkActionBindings.install(this)
        spells = SpellBindings.install(this)
        creatureEvents = CreatureEventBindings.install(this)
        globalEvents = GlobalEventBindings.install(this)
        moveEvents = MoveEventBindings.install(this)
        libraries.forEach { it() }
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

    fun loadFile(path: Path) {
        val source = Files.readString(path)
        loadChunk(source, "@${path.toAbsolutePath()}")
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

/**
 * No-op-safe engine seams installed at boot so the Lua global surface is complete and
 * callable before the game world is wired. They are deliberately inert placeholders:
 * the runtime services replace each seam via the matching `*Functions.install(env, ops)`
 * overload when the world is available, mirroring Canary's binding registration.
 */
private object NO_OP_GAME : GameOperations {
    override fun itemIdByName(name: String): Int = 0
    override fun createItem(itemId: Int, count: Int): List<Any> = emptyList()
}

private object NO_OP_PLAYER : PlayerOperations {
    override fun itemIdByName(name: String): Int = 0
    override fun addItem(
        player: Any,
        itemId: Int,
        count: Int,
        canDropOnMap: Boolean,
        subType: Int,
        slot: Int,
        tier: Int,
    ): List<Any> = emptyList()

    override fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean = false

    override fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long = 0L

    override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean = false
    override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean = false
    override fun getStorageValue(player: Any, key: Int): Int = 0
    override fun setStorageValue(player: Any, key: Int, value: Int): Boolean = false
    override fun getLevel(player: Any): Int = 0
    override fun getExperience(player: Any): Long = 0L
    override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean = false
    override fun getMana(player: Any): Int = 0
    override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean = false
    override fun getVocation(player: Any): Any = Unit
}

private object NO_OP_CREATURE : CreatureOperations {
    override fun getId(creature: Any): Long = 0L
    override fun getName(creature: Any): String = ""
    override fun getHealth(creature: Any): Int = 0
    override fun getMaxHealth(creature: Any): Int = 0
    override fun setHealth(creature: Any, health: Int): Boolean = false
    override fun addHealth(creature: Any, healthChange: Int, combatType: Int): Boolean = false
    override fun getPosition(creature: Any): Position = Position(0, 0, 0)
    override fun getDirection(creature: Any): Int = 0
    override fun setDirection(creature: Any, direction: Int): Boolean = false
}

private object NO_OP_ITEM : ItemOperations {
    override fun itemIdByName(name: String): Int = 0
    override fun itemAttributeByName(name: String): Int = 0
    override fun getId(item: Any): Int = 0
    override fun getCount(item: Any): Int = 0
    override fun getName(item: Any): String = ""
    override fun getSubType(item: Any): Int = 0
    override fun transform(item: Any, itemId: Int, subType: Int): Boolean = false
    override fun decay(item: Any, decayId: Int): Boolean = false
    override fun getActionId(item: Any): Int = 0
    override fun setActionId(item: Any, actionId: Int): Boolean = false
    override fun getUniqueId(item: Any): Long = 0L
    override fun getAttribute(item: Any, attribute: Int): Any? = null
    override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean = false
}

private object NO_OP_CONTAINER : ContainerOperations {
    override fun itemIdByName(name: String): Int = 0
    override fun getSize(container: Any): Int = 0
    override fun getMaxCapacity(container: Any): Int = 0
    override fun getCapacity(container: Any): Int = 0
    override fun getEmptySlots(container: Any, recursive: Boolean): Int = 0
    override fun getItemHoldingCount(container: Any): Int = 0
    override fun getItemCountById(container: Any, itemId: Int, subType: Int): Int = 0
    override fun getItem(container: Any, index: Int): Any? = null
    override fun hasItem(container: Any, item: Any?): Boolean = false
    override fun getItems(container: Any, recursive: Boolean): List<Any> = emptyList()
}

private object NO_OP_POSITION : PositionOperations {
    override fun getDistance(position: Position, target: Position): Int = 0
    override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false
    override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false
}
