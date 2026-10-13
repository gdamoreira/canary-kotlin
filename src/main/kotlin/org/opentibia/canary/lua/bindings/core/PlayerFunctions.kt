package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Canary triple-return payload for `player:addMoney`. Mirrors
 * `PlayerFunctions::luaPlayerAddMoney` (player_functions.cpp:2653):
 * `success, addedMoney, returnValue`.
 */
data class LuaMoneyResult(
    val success: Boolean,
    val addedMoney: Long,
    val returnValue: Int,
)

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

    /** `Player::getBaseMagicLevel` (player.hpp:585); nil when the receiver is not a player. */
    fun getBaseMagicLevel(player: Any): Int?

    /** `Player::getSpentMana` (player.cpp:7849); nil when the receiver is not a player. */
    fun getManaSpent(player: Any): Long?

    /** `PlayerFunctions::luaPlayerAddManaSpent` (player_functions.cpp:1592). */
    fun addManaSpent(player: Any, amount: Long): Boolean?

    /** `PlayerFunctions::luaPlayerGetBaseMaxHealth` (player_functions.cpp:1604). */
    fun getBaseMaxHealth(player: Any): Int?

    /** `PlayerFunctions::luaPlayerGetBaseMaxMana` (player_functions.cpp:1615). */
    fun getBaseMaxMana(player: Any): Int?

    /** `PlayerFunctions::luaPlayerRemoveExperience` (player_functions.cpp:1420). */
    fun removeExperience(player: Any, experience: Long, sendText: Boolean): Boolean?

    /** `PlayerFunctions::luaPlayerSetLevel` (player_functions.cpp:1688). */
    fun setLevel(player: Any, level: Int): Boolean?

    /** `PlayerFunctions::luaPlayerSetMagicLevel` (player_functions.cpp:1704). */
    fun setMagicLevel(player: Any, level: Int, manaSpent: Long): Boolean?

    /** `PlayerFunctions::luaPlayerGetEffectiveSkillLevel` (player_functions.cpp:1638). */
    fun getEffectiveSkillLevel(player: Any, skillType: Int): Int?

    /** `PlayerFunctions::luaPlayerGetSkillPercent` (player_functions.cpp:1650). */
    fun getSkillPercent(player: Any, skillType: Int): Double?

    /** `PlayerFunctions::luaPlayerSetSkillLevel` (player_functions.cpp:1728). */
    fun setSkillLevel(player: Any, skillType: Int, level: Int, tries: Long): Boolean?

    /** `PlayerFunctions::luaPlayerGetItemById` (player_functions.cpp:1905). */
    fun getItemById(player: Any, itemId: Int, deepSearch: Boolean, subType: Int): Any?

    /** `PlayerFunctions::luaPlayerAddItemEx` (player_functions.cpp:2447). */
    fun addItemEx(player: Any, item: Any, canDropOnMap: Boolean, indexOrSlot: Int, flags: Int): Int?

    /** `PlayerFunctions::luaPlayerGetSlotItem` (player_functions.cpp:2843). */
    fun getSlotItem(player: Any, slot: Int): Any?

    /** `PlayerFunctions::luaPlayerGetBackpack` (player_functions.cpp:2868). */
    fun getBackpack(player: Any): Any?

    /** `PlayerFunctions::luaPlayerGetMoney` (player_functions.cpp:2642). */
    fun getMoney(player: Any): Long?

    /** `PlayerFunctions::luaPlayerAddMoney` (player_functions.cpp:2653). */
    fun addMoney(player: Any, money: Long, flags: Int): LuaMoneyResult?

    /** `PlayerFunctions::luaPlayerRemoveMoney` (player_functions.cpp:2671). */
    fun removeMoney(player: Any, money: Long, flags: Int, useBank: Boolean): Boolean?

    /** `PlayerFunctions::luaPlayerGetBankBalance` (player_functions.cpp:2272). */
    fun getBankBalance(player: Any): Long?

    /** `PlayerFunctions::luaPlayerSetBankBalance` (player_functions.cpp:2283). */
    fun setBankBalance(player: Any, bankBalance: Long): Boolean?

    /** `CreatureFunctions::luaCreatureGetCondition` (creature_functions.cpp:747). */
    fun getCondition(player: Any, conditionType: Int, conditionId: Int, subId: Int): Any?

    /** `CreatureFunctions::luaCreatureAddCondition` (creature_functions.cpp:768). */
    fun addCondition(player: Any, condition: Any): Boolean?

    /** `CreatureFunctions::luaCreatureRemoveCondition` (creature_functions.cpp:780). */
    fun removeCondition(
        player: Any,
        conditionType: Int,
        conditionId: Int,
        subId: Int,
        force: Boolean,
    ): Boolean?

    /** `CreatureFunctions::luaCreatureHasCondition` (creature_functions.cpp:806). */
    fun hasCondition(player: Any, conditionType: Int, subId: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetStamina` (player_functions.cpp:2213). */
    fun getStamina(player: Any): Int?

    /**
     * `PlayerFunctions::luaPlayerSetStamina` (player_functions.cpp:2224). Canary pushes
     * no return value, so the C API returns its top stack slot (the stamina argument)
     * for a valid player; the port normalizes that stack quirk to `true`/nil.
     */
    fun setStamina(player: Any, stamina: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetSoul` (player_functions.cpp:2237). */
    fun getSoul(player: Any): Int?

    /** `PlayerFunctions::luaPlayerAddSoul` (player_functions.cpp:2248). */
    fun addSoul(player: Any, soulChange: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetMaxSoul` (player_functions.cpp:2261). */
    fun getMaxSoul(player: Any): Int?
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
 * Batch 1 vitals, skills, inventory, money/bank, conditions and stamina/soul:
 *
 *   player:getBaseMagicLevel()
 *   player:getManaSpent()
 *   player:addManaSpent(amount)
 *   player:getBaseMaxHealth()
 *   player:getBaseMaxMana()
 *   player:removeExperience(experience[, sendText = false])
 *   player:setLevel(level)
 *   player:setMagicLevel(level[, manaSpent = 0])
 *   player:getEffectiveSkillLevel(skillType)
 *   player:getSkillPercent(skillType)
 *   player:setSkillLevel(skillType, level[, tries = 0])
 *   player:getItemById(itemId or name, deepSearch[, subType = -1])
 *   player:addItemEx(item[, canDropOnMap = false[, index = -1[, flags = 0]]])
 *   player:addItemEx(item[, canDropOnMap = true[, slot = 0]])
 *   player:getSlotItem(slot)
 *   player:getBackpack()
 *   player:getMoney()
 *   player:addMoney(money[, flags = 0])
 *   player:removeMoney(money[, flags = 0[, useBank = true]])
 *   player:getBankBalance()
 *   player:setBankBalance(bankBalance)
 *   player:getCondition(conditionType[, conditionId = 0[, subId = 0]])
 *   player:addCondition(condition)
 *   player:removeCondition(conditionType[, conditionId = 0[, subId = 0[, force = false]]])
 *   player:hasCondition(conditionType[, subId = 0])
 *   player:getStamina()
 *   player:setStamina(stamina)
 *   player:getSoul()
 *   player:addSoul(soulChange)
 *   player:getMaxSoul()
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

        register("addItemEx", luaFunction { args ->
            val item = retrieveUserdata(args, 2) as? Item
                ?: return@luaFunction LuaValue.valueOf(false)
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val canDropOnMap = args.arg(3).optboolean(false)
            val indexOrSlot = args.arg(4).optint(if (canDropOnMap) 0 else -1)
            val flags = args.arg(5).optint(0)
            operations.addItemEx(player, item, canDropOnMap, indexOrSlot, flags)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
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

        register("getItemById", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val deepSearch = args.arg(3).toboolean()
            val subType = args.arg(4).optint(-1)
            operations.getItemById(player, itemId, deepSearch, subType)?.let { wrapItemUserdata(env, it) }
                ?: LuaValue.NIL
        })

        register("getSlotItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSlotItem(player, args.arg(2).toint())?.let { wrapItemUserdata(env, it) }
                ?: LuaValue.NIL
        })

        register("getBackpack", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBackpack(player)?.let { wrapItemUserdata(env, it) } ?: LuaValue.NIL
        })

        register("getMoney", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMoney(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("addMoney", luaFunction { args ->
            val player = retrieveUserdata(args, 1)
                ?: return@luaFunction LuaValue.varargsOf(LuaValue.NIL, LuaValue.NIL, LuaValue.NIL)
            val money = args.arg(2).optlong(0)
            val flags = args.arg(3).optint(0)
            val result = operations.addMoney(player, money, flags) ?: return@luaFunction LuaValue.NIL
            LuaValue.varargsOf(
                arrayOf(
                    LuaValue.valueOf(result.success),
                    LuaValue.valueOf(result.addedMoney.toDouble()),
                    LuaValue.valueOf(result.returnValue),
                ),
            )
        })

        register("removeMoney", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val money = args.arg(2).optlong(0)
            val flags = args.arg(3).optint(0)
            val useBank = args.arg(4).optboolean(true)
            operations.removeMoney(player, money, flags, useBank)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getBankBalance", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBankBalance(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("setBankBalance", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val balance = args.arg(2).optlong(0)
            operations.setBankBalance(player, balance)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addSkillTries", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val skillType = args.arg(2).toint()
            val tries = args.arg(3).optlong(0)
            LuaValue.valueOf(operations.addSkillTries(player, skillType, tries))
        })

        register("setSkillLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val skillType = args.arg(2).toint()
            val level = args.arg(3).toint()
            val tries = args.arg(4).optlong(0)
            operations.setSkillLevel(player, skillType, level, tries)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

        register("setLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setLevel(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

        register("removeExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val experience = args.arg(2).optlong(0)
            val sendText = args.arg(3).optboolean(false)
            operations.removeExperience(player, experience, sendText)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

        register("getManaSpent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getManaSpent(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("addManaSpent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val amount = args.arg(2).optlong(0)
            operations.addManaSpent(player, amount)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

        register("getBaseMaxHealth", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBaseMaxHealth(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getBaseMaxMana", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBaseMaxMana(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setMagicLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val level = args.arg(2).toint()
            val manaSpent = args.arg(3).optlong(0)
            operations.setMagicLevel(player, level, manaSpent)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getMagicLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getMagicLevel(player))
        })

        register("getBaseMagicLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBaseMagicLevel(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getSkillLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkillLevel(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getEffectiveSkillLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getEffectiveSkillLevel(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getSkillPercent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkillPercent(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

        register("getCondition", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val conditionType = args.arg(2).toint()
            val conditionId = args.arg(3).optint(0)
            val subId = args.arg(4).optint(0)
            operations.getCondition(player, conditionType, conditionId, subId)?.let {
                env.userdata.wrap("Condition", it)
            } ?: LuaValue.NIL
        })

        register("addCondition", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val condition = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.addCondition(player, condition)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeCondition", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val conditionType = args.arg(2).toint()
            val conditionId = args.arg(3).optint(0)
            val subId = args.arg(4).optint(0)
            val force = args.arg(5).optboolean(false)
            operations.removeCondition(player, conditionType, conditionId, subId, force)?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        register("hasCondition", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val conditionType = args.arg(2).toint()
            val subId = args.arg(3).optint(0)
            operations.hasCondition(player, conditionType, subId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getStamina", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getStamina(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setStamina", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setStamina(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getSoul", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSoul(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addSoul", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addSoul(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getMaxSoul", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMaxSoul(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
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

    override fun getBaseMagicLevel(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getBaseMagicLevel seam is not wired")

    override fun getManaSpent(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getManaSpent seam is not wired")

    override fun addManaSpent(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctions.addManaSpent seam is not wired")

    override fun getBaseMaxHealth(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getBaseMaxHealth seam is not wired")

    override fun getBaseMaxMana(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getBaseMaxMana seam is not wired")

    override fun removeExperience(player: Any, experience: Long, sendText: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctions.removeExperience seam is not wired")

    override fun setLevel(player: Any, level: Int): Boolean? =
        throw NotImplementedError("PlayerFunctions.setLevel seam is not wired")

    override fun setMagicLevel(player: Any, level: Int, manaSpent: Long): Boolean? =
        throw NotImplementedError("PlayerFunctions.setMagicLevel seam is not wired")

    override fun getEffectiveSkillLevel(player: Any, skillType: Int): Int? =
        throw NotImplementedError("PlayerFunctions.getEffectiveSkillLevel seam is not wired")

    override fun getSkillPercent(player: Any, skillType: Int): Double? =
        throw NotImplementedError("PlayerFunctions.getSkillPercent seam is not wired")

    override fun setSkillLevel(player: Any, skillType: Int, level: Int, tries: Long): Boolean? =
        throw NotImplementedError("PlayerFunctions.setSkillLevel seam is not wired")

    override fun getItemById(player: Any, itemId: Int, deepSearch: Boolean, subType: Int): Any? =
        throw NotImplementedError("PlayerFunctions.getItemById seam is not wired")

    override fun addItemEx(
        player: Any,
        item: Any,
        canDropOnMap: Boolean,
        indexOrSlot: Int,
        flags: Int,
    ): Int? = throw NotImplementedError("PlayerFunctions.addItemEx seam is not wired")

    override fun getSlotItem(player: Any, slot: Int): Any? =
        throw NotImplementedError("PlayerFunctions.getSlotItem seam is not wired")

    override fun getBackpack(player: Any): Any? =
        throw NotImplementedError("PlayerFunctions.getBackpack seam is not wired")

    override fun getMoney(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getMoney seam is not wired")

    override fun addMoney(player: Any, money: Long, flags: Int): LuaMoneyResult? =
        throw NotImplementedError("PlayerFunctions.addMoney seam is not wired")

    override fun removeMoney(player: Any, money: Long, flags: Int, useBank: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctions.removeMoney seam is not wired")

    override fun getBankBalance(player: Any): Long? =
        throw NotImplementedError("PlayerFunctions.getBankBalance seam is not wired")

    override fun setBankBalance(player: Any, bankBalance: Long): Boolean? =
        throw NotImplementedError("PlayerFunctions.setBankBalance seam is not wired")

    override fun getCondition(player: Any, conditionType: Int, conditionId: Int, subId: Int): Any? =
        throw NotImplementedError("PlayerFunctions.getCondition seam is not wired")

    override fun addCondition(player: Any, condition: Any): Boolean? =
        throw NotImplementedError("PlayerFunctions.addCondition seam is not wired")

    override fun removeCondition(
        player: Any,
        conditionType: Int,
        conditionId: Int,
        subId: Int,
        force: Boolean,
    ): Boolean? = throw NotImplementedError("PlayerFunctions.removeCondition seam is not wired")

    override fun hasCondition(player: Any, conditionType: Int, subId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctions.hasCondition seam is not wired")

    override fun getStamina(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getStamina seam is not wired")

    override fun setStamina(player: Any, stamina: Int): Boolean? =
        throw NotImplementedError("PlayerFunctions.setStamina seam is not wired")

    override fun getSoul(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getSoul seam is not wired")

    override fun addSoul(player: Any, soulChange: Int): Boolean? =
        throw NotImplementedError("PlayerFunctions.addSoul seam is not wired")

    override fun getMaxSoul(player: Any): Int? =
        throw NotImplementedError("PlayerFunctions.getMaxSoul seam is not wired")
}
