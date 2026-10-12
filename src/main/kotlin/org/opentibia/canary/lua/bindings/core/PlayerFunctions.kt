package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Canary text-message payload for `player:sendTextMessage`. Mirrors the fields used by
 * `PlayerFunctions::luaPlayerSendTextMessage` in
 * src/lua/functions/creatures/player/player_functions.cpp (position variant, channel variant).
 */
data class LuaTextMessage(
    val type: Int,
    val text: String,
    val channelId: Int? = null,
    val position: Position? = null,
    val primaryValue: Int = 0,
    val primaryColor: Int = 0,
    val secondaryValue: Int = 0,
    val secondaryColor: Int = 0,
)

/**
 * Engine seam for the `Player` metatable. Each method corresponds to a C++ primitive in
 * src/lua/functions/creatures/player/player_functions.cpp.
 */
interface PlayerOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    fun addItem(
        player: Any,
        itemId: Int,
        count: Int,
        canDropOnMap: Boolean,
        subType: Int,
        slot: Int,
        tier: Int,
    ): List<Any>

    fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean

    fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long

    fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean

    fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean
}

/**
 * Registers the Canary `Player` userdata methods:
 *
 *   player:addItem(itemId or name, count = 1, canDropOnMap = true, subType = 1, slot = 0, tier = 0)
 *   player:removeItem(itemId or name, count[, subType = -1[, ignoreEquipped = false[, ignoreStoreInbox = false]]])
 *   player:getItemCount(itemId or name[, subType = -1[, ignoreEquipped = false[, ignoreStoreInbox = false]]])
 *   player:addSkillTries(skillType, tries)
 *   player:sendTextMessage(type, text[, position, primaryValue, primaryColor[, secondaryValue, secondaryColor]])
 *   player:sendTextMessage(type, text, channelId)
 */
object PlayerFunctions {
    const val PLAYER_TYPE = "Player"

    fun install(env: LuaEnvironment, operations: PlayerOperations = UnwiredPlayerOperations) {
        val registry = env.userdata

        registry.registerMethod(PLAYER_TYPE, "addItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(1)
            val canDropOnMap = args.arg(4).optboolean(true)
            val subType = args.arg(5).optint(1)
            val slot = args.arg(6).optint(0)
            val tier = args.arg(7).optint(0)
            wrapItems(env, operations.addItem(player, itemId, count, canDropOnMap, subType, slot, tier))
        })

        registry.registerMethod(PLAYER_TYPE, "removeItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).toint()
            val subType = args.arg(4).optint(-1)
            val ignoreEquipped = args.arg(5).optboolean(false)
            val ignoreStoreInbox = args.arg(6).optboolean(false)
            LuaValue.valueOf(operations.removeItem(player, itemId, count, subType, ignoreEquipped, ignoreStoreInbox))
        })

        registry.registerMethod(PLAYER_TYPE, "getItemCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            val ignoreEquipped = args.arg(4).optboolean(false)
            val ignoreStoreInbox = args.arg(5).optboolean(false)
            LuaValue.valueOf(
                operations.getItemCount(player, itemId, subType, ignoreEquipped, ignoreStoreInbox).toDouble(),
            )
        })

        registry.registerMethod(PLAYER_TYPE, "addSkillTries", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val skillType = args.arg(2).toint()
            val tries = args.arg(3).optlong(0)
            LuaValue.valueOf(operations.addSkillTries(player, skillType, tries))
        })

        registry.registerMethod(PLAYER_TYPE, "sendTextMessage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val type = args.arg(2).toint()
            val text = args.arg(3).checkjstring()
            val message = if (args.narg() == 4) {
                LuaTextMessage(type = type, text = text, channelId = args.arg(4).toint())
            } else {
                LuaTextMessage(
                    type = type,
                    text = text,
                    position = retrieveUserdata(args, 4) as? Position,
                    primaryValue = args.arg(5).optint(0),
                    primaryColor = args.arg(6).optint(0),
                    secondaryValue = args.arg(7).optint(0),
                    secondaryColor = args.arg(8).optint(0),
                )
            }
            LuaValue.valueOf(operations.sendTextMessage(player, message))
        })
    }
}

/**
 * Parses Canary's `itemId or name` argument, returning null when an unknown name is given.
 */
internal fun resolveItemId(value: LuaValue, itemIdByName: (String) -> Int): Int? {
    val itemId = if (value.isnumber()) value.toint() else itemIdByName(value.checkjstring())
    return if (itemId == 0) null else itemId
}

internal object UnwiredPlayerOperations : PlayerOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("PlayerFunctions.itemIdByName seam is not wired")

    override fun addItem(
        player: Any,
        itemId: Int,
        count: Int,
        canDropOnMap: Boolean,
        subType: Int,
        slot: Int,
        tier: Int,
    ): List<Any> = throw NotImplementedError("PlayerFunctions.addItem seam is not wired")

    override fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean = throw NotImplementedError("PlayerFunctions.removeItem seam is not wired")

    override fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long = throw NotImplementedError("PlayerFunctions.getItemCount seam is not wired")

    override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean =
        throw NotImplementedError("PlayerFunctions.addSkillTries seam is not wired")

    override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean =
        throw NotImplementedError("PlayerFunctions.sendTextMessage seam is not wired")
}
