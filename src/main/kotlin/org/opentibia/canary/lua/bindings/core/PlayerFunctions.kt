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

    fun getStorageValue(player: Any, key: Int): Int

    fun setStorageValue(player: Any, key: Int, value: Int): Boolean

    fun getLevel(player: Any): Int

    fun getExperience(player: Any): Long

    fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean

    fun getMana(player: Any): Int

    fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean

    fun getVocation(player: Any): Any
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
 *   player:getStorageValue(key)
 *   player:setStorageValue(key, value)
 *   player:getLevel()
 *   player:getExperience()
 *   player:addExperience(experience[, sendText = false])
 *   player:getMana()
 *   player:addMana(manaChange[, animationOnLoss = false])
 *   player:getVocation()
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

        registry.registerMethod(PLAYER_TYPE, "getStorageValue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getStorageValue(player, args.arg(2).toint()))
        })

        registry.registerMethod(PLAYER_TYPE, "setStorageValue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = args.arg(2).toint()
            val value = args.arg(3).toint()
            LuaValue.valueOf(operations.setStorageValue(player, key, value))
        })

        registry.registerMethod(PLAYER_TYPE, "getLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getLevel(player))
        })

        registry.registerMethod(PLAYER_TYPE, "getExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getExperience(player).toDouble())
        })

        registry.registerMethod(PLAYER_TYPE, "addExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val experience = args.arg(2).optlong(0)
            val sendText = args.arg(3).optboolean(false)
            LuaValue.valueOf(operations.addExperience(player, experience, sendText))
        })

        registry.registerMethod(PLAYER_TYPE, "getMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMana(player))
        })

        registry.registerMethod(PLAYER_TYPE, "addMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val manaChange = args.arg(2).toint()
            val animationOnLoss = args.arg(3).optboolean(false)
            LuaValue.valueOf(operations.addMana(player, manaChange, animationOnLoss))
        })

        registry.registerMethod(PLAYER_TYPE, "getVocation", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Vocation", operations.getVocation(player))
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

    override fun getStorageValue(player: Any, key: Int): Int =
        throw NotImplementedError("PlayerFunctions.getStorageValue seam is not wired")

    override fun setStorageValue(player: Any, key: Int, value: Int): Boolean =
        throw NotImplementedError("PlayerFunctions.setStorageValue seam is not wired")

    override fun getLevel(player: Any): Int =
        throw NotImplementedError("PlayerFunctions.getLevel seam is not wired")

    override fun getExperience(player: Any): Long =
        throw NotImplementedError("PlayerFunctions.getExperience seam is not wired")

    override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean =
        throw NotImplementedError("PlayerFunctions.addExperience seam is not wired")

    override fun getMana(player: Any): Int =
        throw NotImplementedError("PlayerFunctions.getMana seam is not wired")

    override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean =
        throw NotImplementedError("PlayerFunctions.addMana seam is not wired")

    override fun getVocation(player: Any): Any =
        throw NotImplementedError("PlayerFunctions.getVocation seam is not wired")
}
