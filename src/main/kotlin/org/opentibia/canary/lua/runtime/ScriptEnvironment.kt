package org.opentibia.canary.lua.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.database.DatabaseConnectionFactory.DBResult
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.Thing

private val logger = KotlinLogging.logger {}

/**
 * Port of the `g_game()` lookups `ScriptEnvironment` performs
 * (`g_game().getCreatureByID`, `g_game().getUniqueItem`,
 * `g_game().removeUniqueItem`). Per AGENTS.md 1.1 the global game singleton is replaced
 * by this injectable resolver; a null resolver behaves like an empty world.
 */
interface ScriptEnvironmentGame {
    /** `Game::getCreatureByID(id)`. */
    fun getCreatureByID(id: Long): Creature?

    /** `Game::getUniqueItem(uid)`. */
    fun getUniqueItem(uid: Int): Item?

    /** `Game::removeUniqueItem(uid)`. */
    fun removeUniqueItem(uid: Int)
}

/**
 * Port of `ScriptEnvironment::getEventInfo`'s four out-parameters
 * (src/lua/scripts/script_environment.hpp:53); Kotlin returns them as a value.
 */
data class ScriptEnvironmentEventInfo(
    val scriptId: Int,
    val scriptInterface: LuaScriptInterface?,
    val callbackId: Int,
    val timerEvent: Boolean,
)

/**
 * Port of `class ScriptEnvironment` (`src/lua/scripts/script_environment.{hpp,cpp}`):
 * the per-script-call identity ([scriptId]/[callbackId]/[timerEvent] plus the owning
 * interface), the nested-callback guard, the temporary item list, the local
 * UID -> item map and the static DB-result registry.
 *
 * The `localMap` UIDs start one above `uint16_t` max (65535), so the first locally
 * registered item is 65536; `getThingByUID` routes `>= 0x10000000` to creatures,
 * `<= 0xFFFF` to the game unique-item map and everything else to the local map,
 * exactly as the C++ lookup does. `resetEnv` clears the static `tempResults` registry
 * because it does the same in `script_environment.cpp:30`.
 */
class ScriptEnvironment(
    /** Injected `g_game()` seam; a null resolver behaves like an empty world. */
    var game: ScriptEnvironmentGame? = null,
) {
    private var scriptId: Int = 0
    private var scriptInterface: LuaScriptInterface? = null
    private var timerEvent: Boolean = false
    private var npc: Npc? = null

    /** `ScriptEnvironment::callbackId` (published for the script-env guards). */
    var callbackId: Int = 0
        private set

    private val localMap = linkedMapOf<Long, Item>()
    private var lastUID: Long = UINT16_MAX

    /** `ScriptEnvironment::resetEnv` (script_environment.cpp:24-38). */
    fun resetEnv() {
        scriptId = 0
        callbackId = 0
        timerEvent = false
        scriptInterface = null
        localMap.clear()
        tempResults.clear()

        val iterator = tempItems.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().first === this) {
                iterator.remove()
            }
        }
    }

    /** `ScriptEnvironment::setScriptId`. */
    fun setScriptId(newScriptId: Int, newScriptInterface: LuaScriptInterface?) {
        scriptId = newScriptId
        scriptInterface = newScriptInterface
    }

    /** `ScriptEnvironment::getScriptId()`. */
    fun getScriptId(): Int = scriptId

    /** `ScriptEnvironment::getScriptInterface()`. */
    fun getScriptInterface(): LuaScriptInterface? = scriptInterface

    /** `ScriptEnvironment::setTimerEvent()`. */
    fun setTimerEvent() {
        timerEvent = true
    }

    /**
     * `ScriptEnvironment::setCallbackId` (script_environment.cpp:40-52): the first
     * callback is accepted, a nested one is refused after reporting
     * `Nested callbacks!` through the environment's interface. The C++ report goes
     * through the static `Lua::reportError` of the *current* script environment; when
     * this environment is the current one (the combat callback path that uses it) the
     * two are the same.
     */
    fun setCallbackId(callbackId: Int, scriptInterface: LuaScriptInterface?): Boolean {
        if (this.callbackId != 0) {
            this.scriptInterface?.reportError("setCallbackId", NESTED_CALLBACKS_ERROR, true)
            return false
        }
        this.callbackId = callbackId
        this.scriptInterface = scriptInterface
        return true
    }

    /** `ScriptEnvironment::getEventInfo`. */
    fun getEventInfo(): ScriptEnvironmentEventInfo =
        ScriptEnvironmentEventInfo(scriptId, scriptInterface, callbackId, timerEvent)

    /** `ScriptEnvironment::setNpc`. */
    fun setNpc(npc: Npc?) {
        this.npc = npc
    }

    /** `ScriptEnvironment::getNpc`. */
    fun getNpc(): Npc? = npc

    /** `ScriptEnvironment::addTempItem`. */
    fun addTempItem(item: Item) {
        tempItems.add(this to item)
    }

    /** `ScriptEnvironment::addThing` (script_environment.cpp:61-84). */
    fun addThing(thing: Thing?): Long {
        if (thing == null || thing.isRemoved()) {
            return 0
        }

        val creature = thing.getCreature()
        if (creature != null) {
            return creature.id
        }

        val item = thing.getItem()
        if (item != null && item.hasAttribute(ItemAttribute.UNIQUEID)) {
            return item.getUniqueId()
        }

        for ((itemId, itemPtr) in localMap) {
            if (itemPtr === item) {
                return itemId
            }
        }

        if (item == null) {
            return 0
        }
        localMap[++lastUID] = item
        return lastUID
    }

    /** `ScriptEnvironment::insertItem`; a duplicate UID logs and keeps the first item. */
    fun insertItem(uid: Long, item: Item) {
        if (localMap.putIfAbsent(uid, item) != null) {
            logger.error { "Thing uid already taken: $uid" }
        }
    }

    /** `ScriptEnvironment::getThingByUID` (script_environment.cpp:93-114). */
    fun getThingByUID(uid: Long): Thing? {
        if (uid >= CREATURE_UID_BASE) {
            return game?.getCreatureByID(uid)
        }

        if (uid <= UINT16_MAX) {
            val item = game?.getUniqueItem(uid.toInt())
            if (item != null && !item.isRemoved()) {
                return item
            }
            return null
        }

        val item = localMap[uid]
        if (item != null && !item.isRemoved()) {
            return item
        }
        return null
    }

    /** `ScriptEnvironment::getItemByUID`. */
    fun getItemByUID(uid: Long): Item? = getThingByUID(uid)?.getItem()

    /** `ScriptEnvironment::getContainerByUID`. */
    fun getContainerByUID(uid: Long): Container? = getItemByUID(uid)?.getContainer()

    /** `ScriptEnvironment::removeItemByUID`. */
    fun removeItemByUID(uid: Long) {
        if (uid <= UINT16_MAX) {
            game?.removeUniqueItem(uid.toInt())
            return
        }
        localMap.remove(uid)
    }

    companion object {
        /** `ScriptEnvironment::tempItems` (script_environment.hpp:86). */
        private val tempItems = mutableListOf<Pair<ScriptEnvironment, Item>>()

        /** `ScriptEnvironment::tempResults` (script_environment.hpp:99). */
        private val tempResults = linkedMapOf<Long, DBResult>()

        /** `ScriptEnvironment::lastResultId` (script_environment.hpp:98). */
        private var lastResultId: Long = 0

        private const val UINT16_MAX = 0xFFFFL
        private const val CREATURE_UID_BASE = 0x10000000L
        private const val NESTED_CALLBACKS_ERROR = "Nested callbacks!"

        /** `ScriptEnvironment::removeTempItem`. */
        fun removeTempItem(item: Item) {
            val iterator = tempItems.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().second === item) {
                    iterator.remove()
                    break
                }
            }
        }

        /** `ScriptEnvironment::addResult`. */
        fun addResult(res: DBResult): Long {
            tempResults[++lastResultId] = res
            return lastResultId
        }

        /** `ScriptEnvironment::removeResult`. */
        fun removeResult(id: Long): Boolean = tempResults.remove(id) != null

        /** `ScriptEnvironment::getResultByID`. */
        fun getResultByID(id: Long): DBResult? = tempResults[id]
    }
}
