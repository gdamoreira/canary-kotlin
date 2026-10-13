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
 *
 * Nullable getters mirror Canary's `lua_pushnil` guards (missing receiver, invalid skill
 * type, or no account). The sex and town accessors default to no-ops because the Kotlin
 * [org.opentibia.canary.creatures.Player] models neither.
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

    /** `Player::getGUID()` (player.cpp); nil when the receiver is not a player. */
    fun getGuid(player: Any): Long?

    /** `Creature::getSpeed()` (creature.cpp); nil when the receiver is not a creature. */
    fun getSpeed(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetSpeed` -> `Game::setCreatureSpeed`. */
    fun setSpeed(player: Any, speed: Int): Boolean

    /** `Player::getCapacity()` (player.cpp:5451); nil when the receiver is not a player. */
    fun getCapacity(player: Any): Long?

    /** `PlayerFunctions::luaPlayerSetCapacity` (player_functions.cpp:1123). */
    fun setCapacity(player: Any, capacity: Long): Boolean

    /** `Player::getFreeCapacity()` (player.cpp:5468); nil when the receiver is not a player. */
    fun getFreeCapacity(player: Any): Long?

    /** `Player::getMaxMana()` (player.hpp); nil when the receiver is not a player. */
    fun getMaxMana(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetMaxMana` (player_functions.cpp:1565). */
    fun setMaxMana(player: Any, maxMana: Int): Boolean

    /**
     * `Player::getMagicLevel()` (player.cpp:7420). No-op default of 0 because the Kotlin
     * [org.opentibia.canary.creatures.Player] models no magic-level progression; Canary
     * players also start at base magic level 0.
     */
    fun getMagicLevel(player: Any): Int = 0

    /** `player:getSkillLevel(skillType)`; nil for an out-of-range skill, as Canary pushes. */
    fun getSkillLevel(player: Any, skillType: Int): Int?

    /** `player:getSkillTries(skillType)`; nil for an out-of-range skill. */
    fun getSkillTries(player: Any, skillType: Int): Long?

    /** `Player::getSex()` (player.hpp); no-op default because the domain has no sex state. */
    fun getSex(player: Any): Int = 0

    /** `Player::setSex()` (player.cpp:7108); no-op default because the domain has no sex state. */
    fun setSex(player: Any, sex: Int): Boolean = false

    /** `Player::getTown()` (player.hpp); no-op default because the domain has no town state. */
    fun getTown(player: Any): Any? = null

    /** `Player::setTown()` (player.hpp); no-op default because the domain has no town state. */
    fun setTown(player: Any, town: Any?): Boolean = false

    /** `Player::getPremiumDays()` (player.cpp:7544); nil when the player has no account. */
    fun getPremiumDays(player: Any): Int?

    /** `Player::fightMode` (`FightMode_t`, 1..3); nil when the receiver is not a player. */
    fun getFightMode(player: Any): Int?

    /** `Player::chaseMode` (`CHASEMODE_FOLLOW`); false when the receiver is not a player. */
    fun hasChaseMode(player: Any): Boolean

    /** `Player::secureMode`; false when the receiver is not a player. */
    fun hasSecureMode(player: Any): Boolean
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
 *   player:getGuid()
 *   player:getSpeed()
 *   player:setSpeed(speed)
 *   player:getCapacity()
 *   player:setCapacity(capacity)
 *   player:getFreeCapacity()
 *   player:getMaxMana()
 *   player:setMaxMana(maxMana)
 *   player:getMagicLevel()
 *   player:getSkillLevel(skillType)
 *   player:getSkillTries(skillType)
 *   player:getSex()
 *   player:setSex(newSex)
 *   player:getTown()
 *   player:setTown(town)
 *   player:getPremiumDays()
 *   player:getFightMode()
 *   player:hasChaseMode()
 *   player:hasSecureMode()
 *
 * Canary registers Player methods on the `Player` shared class, which inherits
 * `Creature` through metatables. The Kotlin [org.opentibia.canary.lua.userdata.UserdataRegistry]
 * has no class-inheritance chain, so the methods are registered on the `Player`,
 * `Monster` and `Npc` metatables to keep the same names reachable from any
 * creature-typed userdata, mirroring how the shipped Lua scripts call them
 * (e.g. `data-canary/scripts/movements/citizen.lua` calls `creature:setTown`).
 */
object PlayerFunctions {
    const val PLAYER_TYPE = "Player"

    val PLAYER_TYPES = listOf(PLAYER_TYPE, "Monster", "Npc")

    fun install(env: LuaEnvironment, operations: PlayerOperations = UnwiredPlayerOperations) {
        val registry = env.userdata
        val register: (String, LuaValue) -> Unit = { name, fn ->
            PLAYER_TYPES.forEach { registry.registerMethod(it, name, fn) }
        }

        register("addItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(1)
            val canDropOnMap = args.arg(4).optboolean(true)
            val subType = args.arg(5).optint(1)
            val slot = args.arg(6).optint(0)
            val tier = args.arg(7).optint(0)
            wrapItems(env, operations.addItem(player, itemId, count, canDropOnMap, subType, slot, tier))
        })

        register("removeItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).toint()
            val subType = args.arg(4).optint(-1)
            val ignoreEquipped = args.arg(5).optboolean(false)
            val ignoreStoreInbox = args.arg(6).optboolean(false)
            LuaValue.valueOf(operations.removeItem(player, itemId, count, subType, ignoreEquipped, ignoreStoreInbox))
        })

        register("getItemCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            val ignoreEquipped = args.arg(4).optboolean(false)
            val ignoreStoreInbox = args.arg(5).optboolean(false)
            LuaValue.valueOf(
                operations.getItemCount(player, itemId, subType, ignoreEquipped, ignoreStoreInbox).toDouble(),
            )
        })

        register("addSkillTries", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val skillType = args.arg(2).toint()
            val tries = args.arg(3).optlong(0)
            LuaValue.valueOf(operations.addSkillTries(player, skillType, tries))
        })

        register("sendTextMessage", luaFunction { args ->
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

        register("getStorageValue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getStorageValue(player, args.arg(2).toint()))
        })

        register("setStorageValue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = args.arg(2).toint()
            val value = args.arg(3).toint()
            LuaValue.valueOf(operations.setStorageValue(player, key, value))
        })

        register("getLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getLevel(player))
        })

        register("getExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getExperience(player).toDouble())
        })

        register("addExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val experience = args.arg(2).optlong(0)
            val sendText = args.arg(3).optboolean(false)
            LuaValue.valueOf(operations.addExperience(player, experience, sendText))
        })

        register("getMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMana(player))
        })

        register("addMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val manaChange = args.arg(2).toint()
            val animationOnLoss = args.arg(3).optboolean(false)
            LuaValue.valueOf(operations.addMana(player, manaChange, animationOnLoss))
        })

        register("getVocation", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Vocation", operations.getVocation(player))
        })

        register("getGuid", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getGuid(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getSpeed", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSpeed(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setSpeed", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setSpeed(player, args.arg(2).toint()))
        })

        register("getCapacity", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getCapacity(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("setCapacity", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setCapacity(player, args.arg(2).optlong(0)))
        })

        register("getFreeCapacity", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getFreeCapacity(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getMaxMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMaxMana(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setMaxMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setMaxMana(player, args.arg(2).toint()))
        })

        register("getMagicLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMagicLevel(player))
        })

        register("getSkillLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkillLevel(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getSkillTries", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkillTries(player, args.arg(2).toint())?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getSex", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getSex(player))
        })

        register("setSex", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setSex(player, args.arg(2).toint()))
        })

        register("getTown", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTown(player)?.let { env.userdata.wrap("Town", it) } ?: LuaValue.NIL
        })

        register("setTown", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setTown(player, retrieveUserdata(args, 2)))
        })

        register("getPremiumDays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getPremiumDays(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getFightMode", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getFightMode(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("hasChaseMode", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.hasChaseMode(player))
        })

        register("hasSecureMode", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.hasSecureMode(player))
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

    override fun getGuid(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getGuid seam is not wired")

    override fun getSpeed(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getSpeed seam is not wired")

    override fun setSpeed(player: Any, speed: Int): Boolean =
        throw NotImplementedError("PlayerFunctions.setSpeed seam is not wired")

    override fun getCapacity(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getCapacity seam is not wired")

    override fun setCapacity(player: Any, capacity: Long): Boolean =
        throw NotImplementedError("PlayerFunctions.setCapacity seam is not wired")

    override fun getFreeCapacity(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getFreeCapacity seam is not wired")

    override fun getMaxMana(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getMaxMana seam is not wired")

    override fun setMaxMana(player: Any, maxMana: Int): Boolean =
        throw NotImplementedError("PlayerFunctions.setMaxMana seam is not wired")

    override fun getSkillLevel(player: Any, skillType: Int): Int? =
        throw NotImplementedError("PlayerFunctions.getSkillLevel seam is not wired")

    override fun getSkillTries(player: Any, skillType: Int): Long? =
        throw NotImplementedError("PlayerFunctions.getSkillTries seam is not wired")

    override fun getPremiumDays(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getPremiumDays seam is not wired")

    override fun getFightMode(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getFightMode seam is not wired")

    override fun hasChaseMode(player: Any): Boolean =
        throw NotImplementedError("PlayerFunctions.hasChaseMode seam is not wired")

    override fun hasSecureMode(player: Any): Boolean =
        throw NotImplementedError("PlayerFunctions.hasSecureMode seam is not wired")
}
