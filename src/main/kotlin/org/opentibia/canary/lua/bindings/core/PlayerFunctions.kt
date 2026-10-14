package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.appearance.outfit.OutfitPlayer
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.creatures.appearance.outfit.PlayerOutfits
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.AchievementPlayer
import org.opentibia.canary.creatures.players.components.AchievementRegistry
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.creatures.players.components.PlayerAchievement
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.Spells
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.IOBestiary
import org.opentibia.canary.systems.bestiary.MonsterBestiaryInfo
import org.opentibia.canary.systems.mount.Mounts
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.prey.PreyBonus
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import java.util.WeakHashMap
import kotlin.math.max

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
 * Batch 3 (spells/runes, wheel of destiny, bestiary/charm/prey/task hunting,
 * health/combat identity, walk/position/map, training/depot/stash/containers,
 * blessings/achievements/vip/kv), registered through [PlayerBatch3Operations]:
 *
 *   player:sendSpellCooldown(spellId[, time = 0])
 *   player:sendSpellGroupCooldown(groupId[, time = 0])
 *   player:clearSpellCooldowns([spenders = false[, builders = false[, ticks = 0]]])
 *   player:applyImbuementScroll(item, scrollItem)
 *   player:openImbuementWindow([action = Open[, item]])
 *   player:closeImbuementWindow()
 *   player:clearAllImbuements(item)
 *   player:instantSkillWOD(name[, value])
 *   player:upgradeSpellsWOD([name[, add = false]])
 *   player:revelationStageWOD([name[, value = 0]])
 *   player:wheelUnlockScroll(scrollName)
 *   player:onThinkWheelOfDestiny([force = false])
 *   player:avatarTimer([value])
 *   player:getWheelSpellAdditionalArea(spellName)
 *   player:getWheelSpellAdditionalTarget(spellName)
 *   player:getWheelSpellAdditionalDuration(spellName)
 *   player:resetCharmsBestiary()
 *   player:unlockAllCharmRunes()
 *   player:addCharmPoints(points)
 *   player:addMinorCharmEchoes(points)
 *   player:getCharmTier(charmId)
 *   player:getCharmChance(charmId)
 *   player:resetOldCharms()
 *   player:isMonsterBestiaryUnlocked(raceId)
 *   player:addBestiaryKill(name[, amount = 1])
 *   player:charmExpansion([value])
 *   player:getCharmMonsterType(charmId)
 *   player:isMonsterPrey(raceId)
 *   player:getPreyCards()
 *   player:getPreyLootPercentage(raceId)
 *   player:getPreyExperiencePercentage(raceId)
 *   player:preyThirdSlot([value])
 *   player:taskHuntingThirdSlot([value])
 *   player:removePreyStamina([amount = 1])
 *   player:addPreyCards(amount)
 *   player:removeTaskHuntingPoints(amount)
 *   player:getTaskHuntingPoints()
 *   player:addTaskHuntingPoints(amount)
 *   player:getMagicShieldCapacityFlat([useCharges = false])
 *   player:getMagicShieldCapacityPercent([useCharges = false])
 *   player:isPzLocked()
 *   player:getFaction()
 *   player:setFaction(factionId)
 *   player:getName()
 *   player:changeName(newName)
 *   player:isPlayer()
 *   player:canReceiveLoot()
 *   player:isOffline()
 *   player:getIdleTime()
 *   player:addMapMark(position, type, description)
 *   player:setGhostMode(enabled)
 *   player:getMapShader()
 *   player:setMapShader(shaderName)
 *   player:isTraining()
 *   player:setTraining(value)
 *   player:getDepotLocker(depotId)
 *   player:getDepotChest(depotId[, autoCreate = false])
 *   player:getInbox()
 *   player:addOfflineTrainingTime(time)
 *   player:getOfflineTrainingTime()
 *   player:removeOfflineTrainingTime(time)
 *   player:addOfflineTrainingTries(skillType, tries)
 *   player:getOfflineTrainingSkill()
 *   player:setOfflineTrainingSkill(skillId)
 *   player:getStashItemCount(itemId or name)
 *   player:setSpecialContainersAvailable(stashMenu, marketMenu, depotSearchMenu)
 *   player:getStashCount()
 *   player:openStash([isNpc = false])
 *   player:addItemStash(itemId[, count = 1])
 *   player:removeStashItem(itemId or name, count)
 *   player:addItemBatchToPaginedContainer(container, itemId[, count = 1[, tier = 0[, flags = 0]]])
 *   player:getStoreInbox()
 *   player:sendContainer(container)
 *   player:sendUpdateContainer(container)
 *   player:getContainerId(container)
 *   player:getContainerById(id)
 *   player:getContainerIndex(id)
 *   player:getLootPouch()
 *   player:getFreeBackpackSlots()
 *   player:sendBlessStatus()
 *   player:hasBlessing(blessing)
 *   player:addBlessing(blessing[, count = 0])
 *   player:removeBlessing(blessing[, count = 0])
 *   player:getBlessingCount(index[, storeCount = false])
 *   player:isVip()
 *   player:getVipDays()
 *   player:getVipTime()
 *   player:getAchievementPoints()
 *   player:addAchievementPoints(amount)
 *   player:removeAchievementPoints(amount)
 *   player:hasAchievement(id or name)
 *   player:addAchievement(id or name[, sendMessage = true])
 *   player:removeAchievement(id or name)
 *   player:kv()
 *   player:save()
 *
 * Remainder (outfits/mounts/familiars/identity, reward chest, client info and XP
 * boosts), registered through [PlayerRemainderOperations]:
 *
 *   player:addOutfit(lookType or name[, addon = 0])
 *   player:addOutfitAddon(lookType, addon)
 *   player:removeOutfit(lookType)
 *   player:removeOutfitAddon(lookType, addon)
 *   player:hasOutfit(lookType[, addon = 0])
 *   player:sendOutfitWindow()
 *   player:addMount(mountId or mountName)
 *   player:removeMount(mountId or mountName)
 *   player:hasMount(mountId or mountName)
 *   player:addFamiliar(lookType)
 *   player:removeFamiliar(lookType)
 *   player:hasFamiliar(lookType)
 *   player:setFamiliarLooktype(lookType)
 *   player:getFamiliarLooktype()
 *   player:getPronoun()
 *   player:setPronoun(newPronoun)
 *   player:getReward(rewardId[, autoCreate = false])
 *   player:removeReward(rewardId)
 *   player:getRewardList()
 *   player:setDailyReward(value)
 *   player:getClient()
 *   player:getBaseXpGain() / setBaseXpGain(value)
 *   player:getVoucherXpBoost() / setVoucherXpBoost(value)
 *   player:getGrindingXpBoost() / setGrindingXpBoost(value)
 *   player:getXpBoostPercent() / setXpBoostPercent(value)
 *   player:getXpBoostTime() / setXpBoostTime(value)
 *   player:getStaminaXpBoost() / setStaminaXpBoost(value)
 *
 * Canary registers Player methods on the `Player` shared class, which inherits
 * `Creature` through the class-table `__index` chain (`Lua::registerSharedClass`,
 * `player_functions.cpp:118-119`); the port registers them on `Player` only and the
 * `Player` class inherits the `Creature` methods.
 */
object PlayerFunctions {
    const val PLAYER_TYPE = "Player"

    fun install(
        env: LuaEnvironment,
        operations: PlayerOperations = UnwiredPlayerOperations,
        batch3: PlayerBatch3Operations = WorldPlayerBatch3Operations(),
        remainder: PlayerRemainderOperations = WorldPlayerRemainderOperations(),
    ) {
        val registry = env.userdata
        registry.registerClass(PLAYER_TYPE, CreatureFunctions.CREATURE_TYPE)
        val register: (String, LuaValue) -> Unit = { name, fn ->
            registry.registerMethod(PLAYER_TYPE, name, fn)
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

        // Wave 40 batch 3 lua-player-3: spells/runes, wheel of destiny,
        // bestiary/charm/prey/task hunting, health/combat identity,
        // walk/position/map, training/depot/stash/containers,
        // blessings/achievements/vip/kv (player_functions.cpp).

        register("sendSpellCooldown", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.sendSpellCooldown(player, args.arg(2).optint(1), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendSpellGroupCooldown", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.sendSpellGroupCooldown(player, args.arg(2).optint(1), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("clearSpellCooldowns", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.clearSpellCooldowns(
                player,
                args.arg(2).optboolean(false),
                args.arg(3).optboolean(false),
                args.arg(4).optint(0),
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("applyImbuementScroll", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            val item = retrieveUserdata(args, 2) as? Item
            val scrollItem = retrieveUserdata(args, 3) as? Item
            if (item == null || scrollItem == null) {
                return@luaFunction LuaValue.valueOf(false)
            }
            batch3.applyImbuementScroll(player, item, scrollItem)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("openImbuementWindow", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            val action = args.arg(2).optint(0)
            val item = retrieveUserdata(args, 3) as? Item
            batch3.openImbuementWindow(player, action, item)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("closeImbuementWindow", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            batch3.closeImbuementWindow(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("clearAllImbuements", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            val item = retrieveUserdata(args, 2) as? Item ?: return@luaFunction LuaValue.valueOf(false)
            batch3.clearAllImbuements(player, item)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("instantSkillWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val name = args.arg(2).optjstring("")
            if (args.narg() == 2) {
                batch3.getWheelInstantSkill(player, name)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                val value = args.arg(3).optboolean(false)
                batch3.setWheelInstantSkill(player, name, value)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        register("upgradeSpellsWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            when {
                args.narg() == 1 ->
                    batch3.resetWheelUpgradedSpells(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                args.narg() == 2 -> {
                    val name = args.arg(2).optjstring("")
                    batch3.getWheelSpellUpgrade(player, name)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                }
                else -> {
                    val name = args.arg(2).optjstring("")
                    val add = args.arg(3).optboolean(false)
                    batch3.setWheelSpellUpgrade(player, name, add)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                }
            }
        })

        register("revelationStageWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            when {
                args.narg() == 1 ->
                    batch3.resetWheelUpgradedSpells(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                args.narg() == 2 -> {
                    val name = args.arg(2).optjstring("")
                    batch3.getWheelSpellStage(player, name)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                }
                else -> {
                    val name = args.arg(2).optjstring("")
                    val value = args.arg(3).optint(0)
                    batch3.setWheelSpellStage(player, name, value)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
                }
            }
        })

        register("wheelUnlockScroll", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val scrollName = args.arg(2).optjstring("")
            if (scrollName.isEmpty()) {
                return@luaFunction LuaValue.NIL
            }
            batch3.wheelUnlockScroll(player, scrollName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("onThinkWheelOfDestiny", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val force = args.arg(2).optboolean(false)
            batch3.onThinkWheelOfDestiny(player, force)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("avatarTimer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                batch3.getWheelAvatarTimer(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
            } else {
                val value = args.arg(2).optlong(0)
                batch3.setWheelAvatarTimer(player, value)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        register("getWheelSpellAdditionalArea", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).optjstring("")
            if (spellName.isEmpty() || !batch3.spellExistsByName(spellName)) {
                return@luaFunction LuaValue.NIL
            }
            batch3.getWheelSpellAdditionalArea(player, spellName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getWheelSpellAdditionalTarget", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).optjstring("")
            if (spellName.isEmpty() || !batch3.spellExistsByName(spellName)) {
                return@luaFunction LuaValue.NIL
            }
            batch3.getWheelSpellAdditionalTarget(player, spellName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getWheelSpellAdditionalDuration", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).optjstring("")
            if (spellName.isEmpty() || !batch3.spellExistsByName(spellName)) {
                return@luaFunction LuaValue.NIL
            }
            batch3.getWheelSpellAdditionalDuration(player, spellName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("resetCharmsBestiary", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.resetCharmsBestiary(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("unlockAllCharmRunes", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.unlockAllCharmRunes(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addCharmPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addCharmPoints(player, args.arg(2).optint(0).toShort().toInt())
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addMinorCharmEchoes", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addMinorCharmEchoes(player, args.arg(2).optint(0).toShort().toInt())
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getCharmTier", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getCharmTier(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getCharmChance", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getCharmChance(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("resetOldCharms", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.resetOldCharms(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isMonsterBestiaryUnlocked", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.isMonsterBestiaryUnlocked(player, args.arg(2).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addBestiaryKill", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val raceId = batch3.monsterRaceByName(args.arg(2).optjstring(""))
            if (raceId == 0) {
                return@luaFunction LuaValue.NIL
            }
            batch3.addBestiaryKill(player, raceId, args.arg(3).optint(1))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("charmExpansion", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                batch3.charmExpansion(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                batch3.setCharmExpansion(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        register("getCharmMonsterType", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getCharmMonsterType(player, args.arg(2).toint())?.let { env.userdata.wrap("MonsterType", it) }
                ?: LuaValue.NIL
        })

        register("isMonsterPrey", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.isMonsterPrey(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getPreyCards", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getPreyCards(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getPreyLootPercentage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getPreyLootPercentage(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getPreyExperiencePercentage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getPreyExperiencePercentage(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("preyThirdSlot", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                batch3.preyThirdSlot(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                batch3.setPreyThirdSlot(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        register("taskHuntingThirdSlot", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                batch3.taskHuntingThirdSlot(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                batch3.setTaskHuntingThirdSlot(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        register("removePreyStamina", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.removePreyStamina(player, args.arg(2).optint(1))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addPreyCards", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addPreyCards(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeTaskHuntingPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.removeTaskHuntingPoints(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getTaskHuntingPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            batch3.getTaskHuntingPoints(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.valueOf(false)
        })

        register("addTaskHuntingPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val amount = args.arg(2).optlong(0)
            batch3.addTaskHuntingPoints(player, amount)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getMagicShieldCapacityFlat", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getMagicShieldCapacityFlat(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getMagicShieldCapacityPercent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getMagicShieldCapacityPercent(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("isPzLocked", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.isPzLocked(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getFaction", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getFaction(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setFaction", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.setFaction(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getName", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getName(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("changeName", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.changeName(player, args.arg(2).optjstring(""))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isPlayer", luaFunction { args ->
            LuaValue.valueOf(batch3.isPlayer(retrieveUserdata(args, 1)))
        })

        register("canReceiveLoot", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.canReceiveLoot(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isOffline", luaFunction { args ->
            val player = retrieveUserdata(args, 1)
            val offline = if (player == null) true else batch3.isOffline(player) ?: true
            LuaValue.valueOf(offline)
        })

        register("getIdleTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getIdleTime(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("addMapMark", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            batch3.addMapMark(player, position, args.arg(3).optint(0), args.arg(4).optjstring(""))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setGhostMode", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.setGhostMode(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getMapShader", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getMapShader(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setMapShader", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.setMapShader(player, args.arg(2).optjstring(""))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isTraining", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            val training = batch3.isTraining(player) ?: false
            LuaValue.valueOf(if (training) 1 else 0)
        })

        register("setTraining", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.setTraining(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getDepotLocker", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getDepotLocker(player, args.arg(2).optint(0))?.let { wrapItemUserdata(env, it) }
                ?: LuaValue.valueOf(false)
        })

        register("getDepotChest", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getDepotChest(player, args.arg(2).optint(0), args.arg(3).optboolean(false))
                ?.let { wrapItemUserdata(env, it) } ?: LuaValue.valueOf(false)
        })

        register("getInbox", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getInbox(player)?.let { wrapItemUserdata(env, it) } ?: LuaValue.valueOf(false)
        })

        register("addOfflineTrainingTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addOfflineTrainingTime(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getOfflineTrainingTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getOfflineTrainingTime(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeOfflineTrainingTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.removeOfflineTrainingTime(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("addOfflineTrainingTries", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addOfflineTrainingTries(player, args.arg(2).optint(0), args.arg(3).optlong(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getOfflineTrainingSkill", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getOfflineTrainingSkill(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setOfflineTrainingSkill", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val skillId = args.arg(2).optint(0).toByte().toInt()
            batch3.setOfflineTrainingSkill(player, skillId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getStashItemCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), batch3::itemIdByName) ?: return@luaFunction LuaValue.NIL
            batch3.getStashItemCount(player, itemId)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("setSpecialContainersAvailable", luaFunction { args ->
            val stashMenu = args.arg(2).optboolean(false)
            val marketMenu = args.arg(3).optboolean(false)
            val depotSearchMenu = args.arg(4).optboolean(false)
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.setSpecialContainersAvailable(player, stashMenu, marketMenu, depotSearchMenu)
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getStashCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getStashCount(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("openStash", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.openStash(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addItemStash", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = args.arg(2).optint(0)
            val count = args.arg(3).optint(1)
            batch3.addItemStash(player, itemId, count)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeStashItem", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), batch3::itemIdByName) ?: return@luaFunction LuaValue.NIL
            batch3.removeStashItem(player, itemId, args.arg(3).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("addItemBatchToPaginedContainer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val container = retrieveUserdata(args, 2) as? Container
            if (container == null || !container.hasPagination()) {
                return@luaFunction LuaValue.valueOf(0)
            }
            val itemId = args.arg(3).optint(0)
            if (itemId == 0) {
                return@luaFunction LuaValue.valueOf(0)
            }
            batch3.addItemBatchToPaginedContainer(
                player,
                container,
                itemId,
                args.arg(4).optint(1),
                args.arg(5).optint(0),
                args.arg(6).optint(0),
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getStoreInbox", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getStoreInbox(player)?.let { wrapItemUserdata(env, it) } ?: LuaValue.valueOf(false)
        })

        register("sendContainer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val container = retrieveUserdata(args, 2) as? Container ?: return@luaFunction LuaValue.NIL
            batch3.sendContainer(player, container)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendUpdateContainer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val container = retrieveUserdata(args, 2) as? Container ?: return@luaFunction LuaValue.NIL
            batch3.sendUpdateContainer(player, container)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getContainerId", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val container = retrieveUserdata(args, 2) as? Container ?: return@luaFunction LuaValue.NIL
            batch3.getContainerId(player, container)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getContainerById", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getContainerById(player, args.arg(2).optint(0))?.let { wrapItemUserdata(env, it) }
                ?: LuaValue.NIL
        })

        register("getContainerIndex", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getContainerIndex(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getLootPouch", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getLootPouch(player)?.let { wrapItemUserdata(env, it) } ?: LuaValue.NIL
        })

        register("getFreeBackpackSlots", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getFreeBackpackSlots(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendBlessStatus", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.sendBlessStatus(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("hasBlessing", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.hasBlessing(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addBlessing", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addBlessing(player, args.arg(2).optint(0), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeBlessing", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.removeBlessing(player, args.arg(2).optint(0), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getBlessingCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val index = args.arg(2).optint(0).let { if (it == 0) 1 else it }
            batch3.getBlessingCount(player, index, args.arg(3).optboolean(false))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isVip", luaFunction { args ->
            val player = retrieveUserdata(args, 1)
            val vip = if (player == null) false else batch3.isVip(player) ?: false
            LuaValue.valueOf(vip)
        })

        register("getVipDays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            batch3.getVipDays(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(false)
        })

        register("getVipTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            batch3.getVipTime(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.valueOf(false)
        })

        register("getAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.getAchievementPoints(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.addAchievementPoints(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.removeAchievementPoints(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("hasAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val achievementId = resolveAchievementId(args.arg(2), batch3::achievementIdByName)
            batch3.hasAchievement(player, achievementId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val achievementId = resolveAchievementId(args.arg(2), batch3::achievementIdByName)
            batch3.addAchievement(player, achievementId, args.arg(3).optboolean(true))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val achievementId = resolveAchievementId(args.arg(2), batch3::achievementIdByName)
            batch3.removeAchievement(player, achievementId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("kv", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            batch3.kv(player)?.let { env.userdata.wrap("KV", it) } ?: LuaValue.valueOf(false)
        })

        register("save", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            batch3.save(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addOutfit", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val outfit = args.arg(2)
            val addon = args.arg(3).optint(0)
            val lookType = when {
                outfit.isnumber() -> outfit.toint()
                outfit.isstring() -> remainder.outfitLookTypeByName(player, outfit.tojstring())
                    ?: return@luaFunction LuaValue.NIL
                else -> return@luaFunction LuaValue.valueOf(true)
            }
            remainder.addOutfit(player, lookType, addon)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addOutfitAddon", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.addOutfitAddon(player, args.arg(2).optint(0), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeOutfit", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.removeOutfit(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeOutfitAddon", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.removeOutfitAddon(player, args.arg(2).optint(0), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("hasOutfit", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.hasOutfit(player, args.arg(2).optint(0), args.arg(3).optint(0))
                ?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendOutfitWindow", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.sendOutfitWindow(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addMount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val mount = args.arg(2)
            val mountId = when {
                mount.isnumber() -> mount.toint()
                mount.isstring() -> remainder.mountIdByName(mount.tojstring()).takeIf { it != 0 }
                    ?: return@luaFunction LuaValue.NIL
                else -> return@luaFunction LuaValue.NIL
            }
            remainder.addMount(player, mountId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeMount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val mount = args.arg(2)
            val mountId = when {
                mount.isnumber() -> mount.toint()
                mount.isstring() -> remainder.mountIdByName(mount.tojstring()).takeIf { it != 0 }
                    ?: return@luaFunction LuaValue.NIL
                else -> return@luaFunction LuaValue.NIL
            }
            remainder.removeMount(player, mountId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("hasMount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val mount = args.arg(2)
            val mountId = when {
                mount.isnumber() -> mount.toint()
                mount.isstring() -> remainder.mountIdByName(mount.tojstring()).takeIf { it != 0 }
                    ?: return@luaFunction LuaValue.NIL
                else -> return@luaFunction LuaValue.NIL
            }
            remainder.hasMount(player, mountId)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addFamiliar", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.addFamiliar(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeFamiliar", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.removeFamiliar(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("hasFamiliar", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.hasFamiliar(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setFamiliarLooktype", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setFamiliarLooktype(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getFamiliarLooktype", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getFamiliarLooktype(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getPronoun", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getPronoun(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setPronoun", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setPronoun(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getReward", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val rewardId = args.arg(2).optlong(0)
            val autoCreate = args.arg(3).optboolean(false)
            remainder.getReward(player, rewardId, autoCreate)?.let { wrapItemUserdata(env, it) }
                ?: LuaValue.valueOf(false)
        })

        register("removeReward", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.removeReward(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getRewardList", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val rewards = remainder.getRewardList(player) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                rewards.forEachIndexed { index, rewardId ->
                    table.set(index + 1, LuaValue.valueOf(rewardId.toDouble()))
                }
            }
        })

        register("setDailyReward", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setDailyReward(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getClient", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val client = remainder.getClient(player) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                table.set("version", LuaValue.valueOf(client.version))
                table.set("versionString", LuaValue.valueOf(client.versionString))
                table.set("os", LuaValue.valueOf(client.os))
            }
        })

        register("getBaseXpGain", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getBaseXpGain(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setBaseXpGain", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setBaseXpGain(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getVoucherXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getVoucherXpBoost(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setVoucherXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setVoucherXpBoost(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getGrindingXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getGrindingXpBoost(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setGrindingXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setGrindingXpBoost(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getXpBoostPercent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getXpBoostPercent(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setXpBoostPercent", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setXpBoostPercent(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getXpBoostTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getXpBoostTime(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setXpBoostTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setXpBoostTime(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getStaminaXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.getStaminaXpBoost(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setStaminaXpBoost", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            remainder.setStaminaXpBoost(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
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

/**
 * Parses Canary's `id or name` achievement argument (player_functions.cpp:4591-4597).
 * Unknown names resolve to id 0, which Canary treats as "not unlocked".
 */
internal fun resolveAchievementId(value: LuaValue, achievementIdByName: (String) -> Int): Int =
    if (value.isnumber()) value.toint() else achievementIdByName(value.optjstring(""))

/** `PreySlot_Three` / `PREY_SLOT_THREE` (`src/io/ioprey.hpp`, ioprey.cpp). */
private const val BATCH3_PREY_SLOT_THREE = 2

/**
 * Engine seam for the batch 3 `Player` primitives in
 * src/lua/functions/creatures/player/player_functions.cpp: spells/cooldowns and
 * imbuement windows, wheel-of-destiny spell state, bestiary/charm/prey/task
 * hunting, health/combat identity, map markers/ghost mode/shader, exercise
 * training, offline training, depot/stash/containers, blessings, achievements,
 * VIP time and the player KV handle.
 *
 * Nullable results mirror Canary's `lua_pushnil` guards; the exact `false`
 * results Canary pushes for a missing player (`isPlayer`, `isVip`, `isTraining`,
 * `getTaskHuntingPoints`, `getVipDays`, `getVipTime`, `kv`) and the `true`
 * `isOffline` result are kept as booleans, not collapsed to nil.
 */
interface PlayerBatch3Operations {
    fun itemIdByName(name: String): Int

    fun monsterRaceByName(name: String): Int

    fun achievementIdByName(name: String): Int

    fun spellExistsByName(name: String): Boolean

    fun sendSpellCooldown(player: Any, spellId: Int, time: Int): Boolean?

    fun sendSpellGroupCooldown(player: Any, groupId: Int, time: Int): Boolean?

    fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean?

    fun applyImbuementScroll(player: Any, item: Any, scrollItem: Any): Boolean?

    fun openImbuementWindow(player: Any, action: Int, item: Any?): Boolean?

    fun closeImbuementWindow(player: Any): Boolean?

    fun clearAllImbuements(player: Any, item: Any): Boolean?

    fun getWheelInstantSkill(player: Any, name: String): Boolean?

    fun setWheelInstantSkill(player: Any, name: String, value: Boolean): Boolean?

    fun resetWheelUpgradedSpells(player: Any): Boolean?

    fun getWheelSpellUpgrade(player: Any, name: String): Int?

    fun setWheelSpellUpgrade(player: Any, name: String, add: Boolean): Boolean?

    fun getWheelSpellStage(player: Any, name: String): Int?

    fun setWheelSpellStage(player: Any, name: String, value: Int): Boolean?

    fun wheelUnlockScroll(player: Any, scrollName: String): Boolean?

    fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean?

    fun getWheelAvatarTimer(player: Any): Long?

    fun setWheelAvatarTimer(player: Any, value: Long): Boolean?

    fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean?

    fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Int?

    fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Int?

    fun resetCharmsBestiary(player: Any): Boolean?

    fun unlockAllCharmRunes(player: Any): Boolean?

    fun addCharmPoints(player: Any, points: Int): Boolean?

    fun addMinorCharmEchoes(player: Any, points: Int): Boolean?

    fun getCharmTier(player: Any, charmId: Int): Int?

    fun getCharmChance(player: Any, charmId: Int): Double?

    fun resetOldCharms(player: Any): Boolean?

    fun isMonsterBestiaryUnlocked(player: Any, raceId: Int): Boolean?

    fun addBestiaryKill(player: Any, raceId: Int, amount: Int): Boolean?

    fun charmExpansion(player: Any): Boolean?

    fun setCharmExpansion(player: Any, value: Boolean): Boolean?

    fun getCharmMonsterType(player: Any, charmId: Int): Any?

    fun isMonsterPrey(player: Any, raceId: Int): Boolean?

    fun getPreyCards(player: Any): Long?

    fun getPreyLootPercentage(player: Any, raceId: Int): Int?

    fun getPreyExperiencePercentage(player: Any, raceId: Int): Int?

    fun preyThirdSlot(player: Any): Boolean?

    fun setPreyThirdSlot(player: Any, value: Boolean): Boolean?

    fun taskHuntingThirdSlot(player: Any): Boolean?

    fun setTaskHuntingThirdSlot(player: Any, value: Boolean): Boolean?

    fun removePreyStamina(player: Any, amount: Int): Boolean?

    fun addPreyCards(player: Any, amount: Long): Boolean?

    fun removeTaskHuntingPoints(player: Any, amount: Long): Boolean?

    fun getTaskHuntingPoints(player: Any): Long?

    fun addTaskHuntingPoints(player: Any, amount: Long): Long?

    fun getMagicShieldCapacityFlat(player: Any, useCharges: Boolean): Int?

    fun getMagicShieldCapacityPercent(player: Any, useCharges: Boolean): Int?

    fun isPzLocked(player: Any): Boolean?

    fun getFaction(player: Any): Int?

    fun setFaction(player: Any, factionId: Int): Boolean?

    fun getName(player: Any): String?

    fun changeName(player: Any, newName: String): Boolean?

    fun isPlayer(player: Any?): Boolean

    fun canReceiveLoot(player: Any): Boolean?

    fun isOffline(player: Any): Boolean?

    fun getIdleTime(player: Any): Long?

    fun addMapMark(player: Any, position: Position, type: Int, description: String): Boolean?

    fun setGhostMode(player: Any, enabled: Boolean): Boolean?

    fun getMapShader(player: Any): String?

    fun setMapShader(player: Any, shaderName: String): Boolean?

    fun isTraining(player: Any): Boolean?

    fun setTraining(player: Any, value: Boolean): Boolean?

    fun getDepotLocker(player: Any, depotId: Int): Any?

    fun getDepotChest(player: Any, depotId: Int, autoCreate: Boolean): Any?

    fun getInbox(player: Any): Any?

    fun addOfflineTrainingTime(player: Any, time: Int): Boolean?

    fun getOfflineTrainingTime(player: Any): Int?

    fun removeOfflineTrainingTime(player: Any, time: Int): Boolean?

    fun addOfflineTrainingTries(player: Any, skillType: Int, tries: Long): Boolean?

    fun getOfflineTrainingSkill(player: Any): Int?

    fun setOfflineTrainingSkill(player: Any, skillId: Int): Boolean?

    fun getStashItemCount(player: Any, itemId: Int): Long?

    fun setSpecialContainersAvailable(
        player: Any,
        stashMenu: Boolean,
        marketMenu: Boolean,
        depotSearchMenu: Boolean,
    ): Boolean?

    fun getStashCount(player: Any): Int?

    fun openStash(player: Any, isNpc: Boolean): Boolean?

    fun addItemStash(player: Any, itemId: Int, count: Int): Boolean?

    fun removeStashItem(player: Any, itemId: Int, count: Int): Boolean?

    fun addItemBatchToPaginedContainer(
        player: Any,
        container: Any,
        itemId: Int,
        count: Int,
        tier: Int,
        flags: Int,
    ): Int?

    fun getStoreInbox(player: Any): Any?

    fun sendContainer(player: Any, container: Any): Boolean?

    fun sendUpdateContainer(player: Any, container: Any): Boolean?

    fun getContainerId(player: Any, container: Any): Int?

    fun getContainerById(player: Any, id: Int): Any?

    fun getContainerIndex(player: Any, id: Int): Int?

    fun getLootPouch(player: Any): Any?

    fun getFreeBackpackSlots(player: Any): Int?

    fun sendBlessStatus(player: Any): Boolean?

    fun hasBlessing(player: Any, blessing: Int): Boolean?

    fun addBlessing(player: Any, blessing: Int, count: Int): Boolean?

    fun removeBlessing(player: Any, blessing: Int, count: Int): Boolean?

    fun getBlessingCount(player: Any, index: Int, storeCount: Boolean): Int?

    fun isVip(player: Any): Boolean?

    fun getVipDays(player: Any): Int?

    fun getVipTime(player: Any): Long?

    fun getAchievementPoints(player: Any): Int?

    fun addAchievementPoints(player: Any, amount: Int): Boolean?

    fun removeAchievementPoints(player: Any, amount: Int): Boolean?

    fun hasAchievement(player: Any, achievementId: Int): Boolean?

    fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean?

    fun removeAchievement(player: Any, achievementId: Int): Boolean?

    fun kv(player: Any): Any?

    fun save(player: Any): Boolean?
}

private fun notWired(seam: String): Nothing =
    throw NotImplementedError("PlayerFunctions.$seam seam is not wired")

/** Throwing default used when no batch 3 seam is supplied. */
object UnwiredPlayerBatch3Operations : PlayerBatch3Operations {
    override fun itemIdByName(name: String): Int = notWired("itemIdByName")

    override fun monsterRaceByName(name: String): Int = notWired("monsterRaceByName")

    override fun achievementIdByName(name: String): Int = notWired("achievementIdByName")

    override fun spellExistsByName(name: String): Boolean = notWired("spellExistsByName")

    override fun sendSpellCooldown(player: Any, spellId: Int, time: Int): Boolean? =
        notWired("sendSpellCooldown")

    override fun sendSpellGroupCooldown(player: Any, groupId: Int, time: Int): Boolean? =
        notWired("sendSpellGroupCooldown")

    override fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean? =
        notWired("clearSpellCooldowns")

    override fun applyImbuementScroll(player: Any, item: Any, scrollItem: Any): Boolean? =
        notWired("applyImbuementScroll")

    override fun openImbuementWindow(player: Any, action: Int, item: Any?): Boolean? =
        notWired("openImbuementWindow")

    override fun closeImbuementWindow(player: Any): Boolean? = notWired("closeImbuementWindow")

    override fun clearAllImbuements(player: Any, item: Any): Boolean? = notWired("clearAllImbuements")

    override fun getWheelInstantSkill(player: Any, name: String): Boolean? = notWired("getWheelInstantSkill")

    override fun setWheelInstantSkill(player: Any, name: String, value: Boolean): Boolean? =
        notWired("setWheelInstantSkill")

    override fun resetWheelUpgradedSpells(player: Any): Boolean? = notWired("resetWheelUpgradedSpells")

    override fun getWheelSpellUpgrade(player: Any, name: String): Int? = notWired("getWheelSpellUpgrade")

    override fun setWheelSpellUpgrade(player: Any, name: String, add: Boolean): Boolean? =
        notWired("setWheelSpellUpgrade")

    override fun getWheelSpellStage(player: Any, name: String): Int? = notWired("getWheelSpellStage")

    override fun setWheelSpellStage(player: Any, name: String, value: Int): Boolean? =
        notWired("setWheelSpellStage")

    override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? = notWired("wheelUnlockScroll")

    override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? =
        notWired("onThinkWheelOfDestiny")

    override fun getWheelAvatarTimer(player: Any): Long? = notWired("getWheelAvatarTimer")

    override fun setWheelAvatarTimer(player: Any, value: Long): Boolean? = notWired("setWheelAvatarTimer")

    override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? =
        notWired("getWheelSpellAdditionalArea")

    override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Int? =
        notWired("getWheelSpellAdditionalTarget")

    override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Int? =
        notWired("getWheelSpellAdditionalDuration")

    override fun resetCharmsBestiary(player: Any): Boolean? = notWired("resetCharmsBestiary")

    override fun unlockAllCharmRunes(player: Any): Boolean? = notWired("unlockAllCharmRunes")

    override fun addCharmPoints(player: Any, points: Int): Boolean? = notWired("addCharmPoints")

    override fun addMinorCharmEchoes(player: Any, points: Int): Boolean? = notWired("addMinorCharmEchoes")

    override fun getCharmTier(player: Any, charmId: Int): Int? = notWired("getCharmTier")

    override fun getCharmChance(player: Any, charmId: Int): Double? = notWired("getCharmChance")

    override fun resetOldCharms(player: Any): Boolean? = notWired("resetOldCharms")

    override fun isMonsterBestiaryUnlocked(player: Any, raceId: Int): Boolean? =
        notWired("isMonsterBestiaryUnlocked")

    override fun addBestiaryKill(player: Any, raceId: Int, amount: Int): Boolean? = notWired("addBestiaryKill")

    override fun charmExpansion(player: Any): Boolean? = notWired("charmExpansion")

    override fun setCharmExpansion(player: Any, value: Boolean): Boolean? = notWired("setCharmExpansion")

    override fun getCharmMonsterType(player: Any, charmId: Int): Any? = notWired("getCharmMonsterType")

    override fun isMonsterPrey(player: Any, raceId: Int): Boolean? = notWired("isMonsterPrey")

    override fun getPreyCards(player: Any): Long? = notWired("getPreyCards")

    override fun getPreyLootPercentage(player: Any, raceId: Int): Int? = notWired("getPreyLootPercentage")

    override fun getPreyExperiencePercentage(player: Any, raceId: Int): Int? =
        notWired("getPreyExperiencePercentage")

    override fun preyThirdSlot(player: Any): Boolean? = notWired("preyThirdSlot")

    override fun setPreyThirdSlot(player: Any, value: Boolean): Boolean? = notWired("setPreyThirdSlot")

    override fun taskHuntingThirdSlot(player: Any): Boolean? = notWired("taskHuntingThirdSlot")

    override fun setTaskHuntingThirdSlot(player: Any, value: Boolean): Boolean? =
        notWired("setTaskHuntingThirdSlot")

    override fun removePreyStamina(player: Any, amount: Int): Boolean? = notWired("removePreyStamina")

    override fun addPreyCards(player: Any, amount: Long): Boolean? = notWired("addPreyCards")

    override fun removeTaskHuntingPoints(player: Any, amount: Long): Boolean? =
        notWired("removeTaskHuntingPoints")

    override fun getTaskHuntingPoints(player: Any): Long? = notWired("getTaskHuntingPoints")

    override fun addTaskHuntingPoints(player: Any, amount: Long): Long? = notWired("addTaskHuntingPoints")

    override fun getMagicShieldCapacityFlat(player: Any, useCharges: Boolean): Int? =
        notWired("getMagicShieldCapacityFlat")

    override fun getMagicShieldCapacityPercent(player: Any, useCharges: Boolean): Int? =
        notWired("getMagicShieldCapacityPercent")

    override fun isPzLocked(player: Any): Boolean? = notWired("isPzLocked")

    override fun getFaction(player: Any): Int? = notWired("getFaction")

    override fun setFaction(player: Any, factionId: Int): Boolean? = notWired("setFaction")

    override fun getName(player: Any): String? = notWired("getName")

    override fun changeName(player: Any, newName: String): Boolean? = notWired("changeName")

    override fun isPlayer(player: Any?): Boolean = notWired("isPlayer")

    override fun canReceiveLoot(player: Any): Boolean? = notWired("canReceiveLoot")

    override fun isOffline(player: Any): Boolean? = notWired("isOffline")

    override fun getIdleTime(player: Any): Long? = notWired("getIdleTime")

    override fun addMapMark(player: Any, position: Position, type: Int, description: String): Boolean? =
        notWired("addMapMark")

    override fun setGhostMode(player: Any, enabled: Boolean): Boolean? = notWired("setGhostMode")

    override fun getMapShader(player: Any): String? = notWired("getMapShader")

    override fun setMapShader(player: Any, shaderName: String): Boolean? = notWired("setMapShader")

    override fun isTraining(player: Any): Boolean? = notWired("isTraining")

    override fun setTraining(player: Any, value: Boolean): Boolean? = notWired("setTraining")

    override fun getDepotLocker(player: Any, depotId: Int): Any? = notWired("getDepotLocker")

    override fun getDepotChest(player: Any, depotId: Int, autoCreate: Boolean): Any? = notWired("getDepotChest")

    override fun getInbox(player: Any): Any? = notWired("getInbox")

    override fun addOfflineTrainingTime(player: Any, time: Int): Boolean? = notWired("addOfflineTrainingTime")

    override fun getOfflineTrainingTime(player: Any): Int? = notWired("getOfflineTrainingTime")

    override fun removeOfflineTrainingTime(player: Any, time: Int): Boolean? =
        notWired("removeOfflineTrainingTime")

    override fun addOfflineTrainingTries(player: Any, skillType: Int, tries: Long): Boolean? =
        notWired("addOfflineTrainingTries")

    override fun getOfflineTrainingSkill(player: Any): Int? = notWired("getOfflineTrainingSkill")

    override fun setOfflineTrainingSkill(player: Any, skillId: Int): Boolean? =
        notWired("setOfflineTrainingSkill")

    override fun getStashItemCount(player: Any, itemId: Int): Long? = notWired("getStashItemCount")

    override fun setSpecialContainersAvailable(
        player: Any,
        stashMenu: Boolean,
        marketMenu: Boolean,
        depotSearchMenu: Boolean,
    ): Boolean? = notWired("setSpecialContainersAvailable")

    override fun getStashCount(player: Any): Int? = notWired("getStashCount")

    override fun openStash(player: Any, isNpc: Boolean): Boolean? = notWired("openStash")

    override fun addItemStash(player: Any, itemId: Int, count: Int): Boolean? = notWired("addItemStash")

    override fun removeStashItem(player: Any, itemId: Int, count: Int): Boolean? = notWired("removeStashItem")

    override fun addItemBatchToPaginedContainer(
        player: Any,
        container: Any,
        itemId: Int,
        count: Int,
        tier: Int,
        flags: Int,
    ): Int? = notWired("addItemBatchToPaginedContainer")

    override fun getStoreInbox(player: Any): Any? = notWired("getStoreInbox")

    override fun sendContainer(player: Any, container: Any): Boolean? = notWired("sendContainer")

    override fun sendUpdateContainer(player: Any, container: Any): Boolean? = notWired("sendUpdateContainer")

    override fun getContainerId(player: Any, container: Any): Int? = notWired("getContainerId")

    override fun getContainerById(player: Any, id: Int): Any? = notWired("getContainerById")

    override fun getContainerIndex(player: Any, id: Int): Int? = notWired("getContainerIndex")

    override fun getLootPouch(player: Any): Any? = notWired("getLootPouch")

    override fun getFreeBackpackSlots(player: Any): Int? = notWired("getFreeBackpackSlots")

    override fun sendBlessStatus(player: Any): Boolean? = notWired("sendBlessStatus")

    override fun hasBlessing(player: Any, blessing: Int): Boolean? = notWired("hasBlessing")

    override fun addBlessing(player: Any, blessing: Int, count: Int): Boolean? = notWired("addBlessing")

    override fun removeBlessing(player: Any, blessing: Int, count: Int): Boolean? = notWired("removeBlessing")

    override fun getBlessingCount(player: Any, index: Int, storeCount: Boolean): Int? =
        notWired("getBlessingCount")

    override fun isVip(player: Any): Boolean? = notWired("isVip")

    override fun getVipDays(player: Any): Int? = notWired("getVipDays")

    override fun getVipTime(player: Any): Long? = notWired("getVipTime")

    override fun getAchievementPoints(player: Any): Int? = notWired("getAchievementPoints")

    override fun addAchievementPoints(player: Any, amount: Int): Boolean? = notWired("addAchievementPoints")

    override fun removeAchievementPoints(player: Any, amount: Int): Boolean? =
        notWired("removeAchievementPoints")

    override fun hasAchievement(player: Any, achievementId: Int): Boolean? = notWired("hasAchievement")

    override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? =
        notWired("addAchievement")

    override fun removeAchievement(player: Any, achievementId: Int): Boolean? = notWired("removeAchievement")

    override fun kv(player: Any): Any? = notWired("kv")

    override fun save(player: Any): Boolean? = notWired("save")
}

/** `PlayerAchievement` view over a live [Player] (see the component's seams). */
private class PlayerAchievementView(private val player: Player) : AchievementPlayer {
    override fun getName(): String = player.name

    override fun sendTextMessage(message: String) {
        player.sendTextMessage(message)
    }

    override fun sendCyclopediaCharacterAchievements(
        secretsUnlocked: Int,
        achievementsUnlocked: List<Pair<Achievement, Long>>,
    ) = Unit
}

/**
 * Real, non-no-op implementation of [PlayerBatch3Operations] backed by the Kotlin
 * domain objects and per-player state holders.
 *
 * PARITY GAPS (recorded, not invented):
 *  - The Kotlin `PlayerWheel` does not model instant spells, upgrades, stages,
 *    avatar timers or scroll unlocks, and `Player` has no imbuement engine, so
 *    all wheel/imbuement primitives are inert (`false`/`0`/`true` no-op) instead
 *    of applying state; `clearAllImbuements` answers `false`.
 *  - `Player` models no faction, magic shield, stomping idle timer, ghost mode,
 *    attached map shader, exercise training or stash/depot/inbox/store-inbox/loot
 *    pouch ownership. Faction reports `FACTION_PLAYER` (1); the magic shield,
 *    idle time, shader and training report their Canary zero defaults; depot,
 *    inbox, store inbox and loot pouch report Canary's miss (`false`/nil);
 *    ghost mode and special-container availability succeed without side effects.
 *  - `Creature::name` is immutable, so `changeName` cannot rename; it reports
 *    success like the C++ primitive's no-value stack return.
 *  - `canReceiveLoot` assumes Canary's default 2520 stamina minutes; the port has
 *    no stamina field on `Player`.
 *  - `getStashCount` sums the stashed counts without the C++ `stackSize` division
 *    because the stash seam has no item-type catalog.
 *  - `getStashItemCount`/`removeStashItem` skip the C++ `Item::items[itemId]`
 *    existence check (no item catalog in the stash seam); unknown numeric ids
 *    pass through to the stash state.
 *  - `revelationStageWOD`'s setter mirrors Canary's `wheel().setSpellInstant`
 *    call, not a dedicated stage setter.
 *  - `kv` wraps the player KV with the `KV` metatable rather than Canary's shared
 *    userdata wrapper; the KV method registrations are the KV bindings' scope.
 *  - Container sends and `save` have no protocol/persistence seam and succeed
 *    without transmitting or persisting.
 *  - `getPreyExperiencePercentage`/`getPreyLootPercentage`/`isMonsterPrey` follow
 *    the ported `PlayerPreyState` exactly; `preyThirdSlot`/`taskHuntingThirdSlot`
 *    mutate the ported slot state without the client reload packets.
 */
class WorldPlayerBatch3Operations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val achievementRegistry: AchievementRegistry = InMemoryAchievementRegistry(),
    private val monsterBestiaryInfos: List<MonsterBestiaryInfo> = emptyList(),
    private val monsterTypesByRaceId: Map<Int, Any> = emptyMap(),
    private val bestiary: IOBestiary = IOBestiary(),
    private val spells: Spells? = null,
) : PlayerBatch3Operations {

    private val achievements = mutableMapOf<Long, PlayerAchievement>()

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun monsterRaceByName(name: String): Int =
        monsterBestiaryInfos.firstOrNull { it.name.equals(name, ignoreCase = true) }?.raceid ?: 0

    override fun achievementIdByName(name: String): Int = achievementRegistry.getAchievementByName(name).id

    override fun spellExistsByName(name: String): Boolean = spells?.getInstantSpellByName(name) != null

    override fun sendSpellCooldown(player: Any, spellId: Int, time: Int): Boolean? {
        val target = player as? Player ?: return null
        target.sendSpellCooldown(spellId, time)
        return true
    }

    override fun sendSpellGroupCooldown(player: Any, groupId: Int, time: Int): Boolean? {
        val target = player as? Player ?: return null
        val group = SpellGroup.entries.firstOrNull { it.value == groupId } ?: SpellGroup.SPELLGROUP_NONE
        target.sendSpellGroupCooldown(group, time)
        return true
    }

    /** PARITY GAP: the Kotlin `Player` has no `clearCooldowns`; reports success inertly. */
    override fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean? =
        (player as? Player)?.let { true }

    /** PARITY GAP: no imbuement engine on the Kotlin `Player`; normalized no-op. */
    override fun applyImbuementScroll(player: Any, item: Any, scrollItem: Any): Boolean? =
        (player as? Player)?.let { true }

    override fun openImbuementWindow(player: Any, action: Int, item: Any?): Boolean? =
        (player as? Player)?.let { true }

    override fun closeImbuementWindow(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun clearAllImbuements(player: Any, item: Any): Boolean? =
        (player as? Player)?.let { false }

    override fun getWheelInstantSkill(player: Any, name: String): Boolean? =
        (player as? Player)?.let { false }

    override fun setWheelInstantSkill(player: Any, name: String, value: Boolean): Boolean? =
        (player as? Player)?.let { true }

    override fun resetWheelUpgradedSpells(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun getWheelSpellUpgrade(player: Any, name: String): Int? = (player as? Player)?.let { 0 }

    override fun setWheelSpellUpgrade(player: Any, name: String, add: Boolean): Boolean? =
        (player as? Player)?.let { true }

    override fun getWheelSpellStage(player: Any, name: String): Int? = (player as? Player)?.let { 0 }

    override fun setWheelSpellStage(player: Any, name: String, value: Int): Boolean? =
        (player as? Player)?.let { true }

    override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? =
        (player as? Player)?.let { false }

    override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? = (player as? Player)?.let { true }

    override fun getWheelAvatarTimer(player: Any): Long? = (player as? Player)?.let { 0L }

    override fun setWheelAvatarTimer(player: Any, value: Long): Boolean? = (player as? Player)?.let { true }

    override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? =
        (player as? Player)?.let { false }

    override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Int? =
        (player as? Player)?.let { 0 }

    override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Int? =
        (player as? Player)?.let { 0 }

    override fun resetCharmsBestiary(player: Any): Boolean? {
        val target = player as? Player ?: return null
        val state = target.features.bestiary
        state.setCharmPoints(0)
        state.hasCharmExpansion = false
        state.setUsedRunesBit(0)
        state.setUnlockedRunesBit(0)
        for (rune in CharmRune.entries) {
            if (rune == CharmRune.CHARM_NONE) {
                continue
            }
            state.parseRacebyCharm(rune, set = true, newRaceid = 0)
        }
        return true
    }

    override fun unlockAllCharmRunes(player: Any): Boolean? {
        val target = player as? Player ?: return null
        val state = target.features.bestiary
        for (rune in CharmRune.entries) {
            if (rune == CharmRune.CHARM_NONE) {
                continue
            }
            val charm = bestiary.getBestiaryCharm(rune) ?: continue
            state.setUnlockedRunesBit(bestiary.bitToggle(state.getUnlockedRunesBit(), charm, true))
        }
        return true
    }

    override fun addCharmPoints(player: Any, points: Int): Boolean? {
        val target = player as? Player ?: return null
        val magnitude = if (points >= 0) points else -points
        bestiary.addCharmPoints(target.features.bestiary, magnitude, negative = points < 0)
        return true
    }

    override fun addMinorCharmEchoes(player: Any, points: Int): Boolean? {
        val target = player as? Player ?: return null
        val magnitude = if (points >= 0) points else -points
        bestiary.addMinorCharmEchoes(target.features.bestiary, magnitude, negative = points < 0)
        return true
    }

    override fun getCharmTier(player: Any, charmId: Int): Int? {
        val target = player as? Player ?: return null
        return target.features.bestiary.getCharmTier(CharmRune.fromValue(charmId) ?: CharmRune.CHARM_NONE)
    }

    override fun getCharmChance(player: Any, charmId: Int): Double? {
        val target = player as? Player ?: return null
        val rune = CharmRune.fromValue(charmId) ?: return 0.0
        val charm = bestiary.getBestiaryCharm(rune) ?: return 0.0
        return charm.getChance(target.features.bestiary.getCharmTier(rune))
    }

    /** PARITY GAP: `Player::resetOldCharms` is not ported; reports success inertly. */
    override fun resetOldCharms(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun isMonsterBestiaryUnlocked(player: Any, raceId: Int): Boolean? {
        val target = player as? Player ?: return null
        if (monsterBestiaryInfos.none { it.raceid == raceId }) {
            return null
        }
        val finished = bestiary.getBestiaryFinished(target.features.bestiary, monsterBestiaryInfos)
        return if (raceId in finished) true else null
    }

    override fun addBestiaryKill(player: Any, raceId: Int, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        val info = monsterBestiaryInfos.firstOrNull { it.raceid == raceId } ?: return null
        bestiary.addBestiaryKill(target.features.bestiary, info, amount)
        return true
    }

    override fun charmExpansion(player: Any): Boolean? =
        (player as? Player)?.features?.bestiary?.hasCharmExpansion

    override fun setCharmExpansion(player: Any, value: Boolean): Boolean? {
        val target = player as? Player ?: return null
        target.features.bestiary.hasCharmExpansion = value
        return true
    }

    override fun getCharmMonsterType(player: Any, charmId: Int): Any? {
        val target = player as? Player ?: return null
        val rune = CharmRune.fromValue(charmId) ?: return null
        val raceId = target.features.bestiary.parseRacebyCharm(rune)
        if (raceId <= 0) {
            return null
        }
        return monsterTypesByRaceId[raceId]
    }

    override fun isMonsterPrey(player: Any, raceId: Int): Boolean? {
        val target = player as? Player ?: return null
        return target.getPreyWithMonster(raceId)?.isOccupied() ?: false
    }

    override fun getPreyCards(player: Any): Long? = (player as? Player)?.getPreyCards()

    override fun getPreyLootPercentage(player: Any, raceId: Int): Int? {
        val target = player as? Player ?: return null
        val slot = target.getPreyWithMonster(raceId)
        return if (slot != null && slot.isOccupied() && slot.bonus == PreyBonus.Loot) slot.bonusPercentage else 0
    }

    override fun getPreyExperiencePercentage(player: Any, raceId: Int): Int? {
        val target = player as? Player ?: return null
        val slot = target.getPreyWithMonster(raceId)
        return if (slot != null && slot.isOccupied() && slot.bonus == PreyBonus.Experience && slot.bonusTimeLeft > 0) {
            100 + slot.bonusPercentage
        } else {
            100
        }
    }

    override fun preyThirdSlot(player: Any): Boolean? {
        val target = player as? Player ?: return null
        val slot = target.getPreySlotById(BATCH3_PREY_SLOT_THREE) ?: return null
        return slot.state != PreyDataState.Locked
    }

    override fun setPreyThirdSlot(player: Any, value: Boolean): Boolean? {
        val target = player as? Player ?: return null
        val slot = target.getPreySlotById(BATCH3_PREY_SLOT_THREE) ?: return null
        if (value) {
            slot.eraseBonus()
            slot.state = PreyDataState.Selection
            slot.reloadMonsterGrid(target.getPreyBlackList(), target.level)
            target.reloadPreySlot(BATCH3_PREY_SLOT_THREE)
        } else {
            slot.state = PreyDataState.Locked
        }
        return true
    }

    override fun taskHuntingThirdSlot(player: Any): Boolean? {
        val target = player as? Player ?: return null
        val slot = target.getTaskHuntingSlotById(BATCH3_PREY_SLOT_THREE) ?: return null
        return slot.state != PreyTaskDataState.Locked
    }

    override fun setTaskHuntingThirdSlot(player: Any, value: Boolean): Boolean? {
        val target = player as? Player ?: return null
        val slot = target.getTaskHuntingSlotById(BATCH3_PREY_SLOT_THREE) ?: return null
        if (value) {
            slot.eraseTask()
            slot.reloadReward()
            slot.state = PreyTaskDataState.Selection
            slot.reloadMonsterGrid(target.getTaskHuntingBlackList(), target.level)
            target.reloadTaskSlot(BATCH3_PREY_SLOT_THREE)
        } else {
            slot.state = PreyTaskDataState.Locked
        }
        return true
    }

    override fun removePreyStamina(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.preyEngine.checkPlayerPreys(target.features.prey, amount)
        return true
    }

    override fun addPreyCards(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.addPreyCards(amount)
        return true
    }

    override fun removeTaskHuntingPoints(player: Any, amount: Long): Boolean? =
        (player as? Player)?.useTaskHuntingPoints(amount)

    override fun getTaskHuntingPoints(player: Any): Long? = (player as? Player)?.getTaskHuntingPoints()

    override fun addTaskHuntingPoints(player: Any, amount: Long): Long? {
        val target = player as? Player ?: return null
        target.addTaskHuntingPoints(amount)
        return amount
    }

    /** PARITY GAP: no magic shield model on the Kotlin `Player`; Canary's zero default. */
    override fun getMagicShieldCapacityFlat(player: Any, useCharges: Boolean): Int? =
        (player as? Player)?.let { 0 }

    /** PARITY GAP: see [getMagicShieldCapacityFlat]. */
    override fun getMagicShieldCapacityPercent(player: Any, useCharges: Boolean): Int? =
        (player as? Player)?.let { 0 }

    override fun isPzLocked(player: Any): Boolean? = (player as? Player)?.isPzLocked()

    /** PARITY GAP: `Player` models no faction; `FACTION_PLAYER` (game_definitions.hpp:45). */
    override fun getFaction(player: Any): Int? = (player as? Player)?.let { FACTION_PLAYER }

    /** PARITY GAP: `Player` models no faction field; succeeds inertly. */
    override fun setFaction(player: Any, factionId: Int): Boolean? = (player as? Player)?.let { true }

    override fun getName(player: Any): String? = (player as? Player)?.name

    /** PARITY GAP: `Creature::name` is immutable; reports the C++ no-value success. */
    override fun changeName(player: Any, newName: String): Boolean? = (player as? Player)?.let { true }

    override fun isPlayer(player: Any?): Boolean = player is Player

    /** PARITY GAP: `Player` has no stamina field; Canary's default 2520 minutes always allow loot. */
    override fun canReceiveLoot(player: Any): Boolean? = (player as? Player)?.let { true }

    /** PARITY GAP: `Player` has no online flag; a live Kotlin player is treated as online. */
    override fun isOffline(player: Any): Boolean? = (player as? Player)?.let { false }

    /** PARITY GAP: `Player` has no idle timer; Canary's zero default. */
    override fun getIdleTime(player: Any): Long? = (player as? Player)?.let { 0L }

    /** PARITY GAP: no marker protocol sink; succeeds inertly. */
    override fun addMapMark(player: Any, position: Position, type: Int, description: String): Boolean? =
        (player as? Player)?.let { true }

    /** PARITY GAP: no ghost-mode state; succeeds inertly. */
    override fun setGhostMode(player: Any, enabled: Boolean): Boolean? = (player as? Player)?.let { true }

    /** PARITY GAP: no attached-effect shader state; Canary's empty default. */
    override fun getMapShader(player: Any): String? = (player as? Player)?.let { "" }

    /** PARITY GAP: no attached-effect shader state; succeeds inertly. */
    override fun setMapShader(player: Any, shaderName: String): Boolean? = (player as? Player)?.let { true }

    /** PARITY GAP: no exercise-training state; Canary's false default. */
    override fun isTraining(player: Any): Boolean? = (player as? Player)?.let { false }

    /** PARITY GAP: no exercise-training state; succeeds inertly. */
    override fun setTraining(player: Any, value: Boolean): Boolean? = (player as? Player)?.let { true }

    /** PARITY GAP: the Kotlin `Player` owns no depot; Canary's miss. */
    override fun getDepotLocker(player: Any, depotId: Int): Any? = (player as? Player)?.let { null }

    /** PARITY GAP: see [getDepotLocker]. */
    override fun getDepotChest(player: Any, depotId: Int, autoCreate: Boolean): Any? =
        (player as? Player)?.let { null }

    /** PARITY GAP: the Kotlin `Player` owns no inbox; Canary's miss. */
    override fun getInbox(player: Any): Any? = (player as? Player)?.let { null }

    override fun addOfflineTrainingTime(player: Any, time: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.offlineTraining.addOfflineTrainingTime(time)
        return true
    }

    override fun getOfflineTrainingTime(player: Any): Int? =
        (player as? Player)?.features?.offlineTraining?.getOfflineTrainingTime()

    override fun removeOfflineTrainingTime(player: Any, time: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.offlineTraining.removeOfflineTrainingTime(time)
        return true
    }

    override fun addOfflineTrainingTries(player: Any, skillType: Int, tries: Long): Boolean? {
        val target = player as? Player ?: return null
        val skill = Skill.entries.firstOrNull { it.value == skillType } ?: return false
        return target.features.offlineTraining.addOfflineTrainingTries(skill, tries)
    }

    override fun getOfflineTrainingSkill(player: Any): Int? =
        (player as? Player)?.features?.offlineTraining?.getOfflineTrainingSkill()?.value

    override fun setOfflineTrainingSkill(player: Any, skillId: Int): Boolean? {
        val target = player as? Player ?: return null
        val skill = Skill.entries.firstOrNull { it.value == skillId } ?: return true
        target.features.offlineTraining.setOfflineTrainingSkill(skill)
        return true
    }

    override fun getStashItemCount(player: Any, itemId: Int): Long? {
        val target = player as? Player ?: return null
        return target.stash.getStashItemCount(itemId).toLong()
    }

    /** PARITY GAP: no special-container menu state; succeeds inertly. */
    override fun setSpecialContainersAvailable(
        player: Any,
        stashMenu: Boolean,
        marketMenu: Boolean,
        depotSearchMenu: Boolean,
    ): Boolean? = (player as? Player)?.let { true }

    override fun getStashCount(player: Any): Int? {
        val target = player as? Player ?: return null
        return target.stash.getStashItems().values.sum()
    }

    /** PARITY GAP: no stash-open protocol sink; succeeds inertly. */
    override fun openStash(player: Any, isNpc: Boolean): Boolean? = (player as? Player)?.let { true }

    override fun addItemStash(player: Any, itemId: Int, count: Int): Boolean? {
        val target = player as? Player ?: return null
        target.stash.addItemOnStash(itemId, count)
        return true
    }

    override fun removeStashItem(player: Any, itemId: Int, count: Int): Boolean? {
        val target = player as? Player ?: return null
        return target.stash.withdrawItem(itemId, count)
    }

    /** PARITY GAP: no paginated-container insertion engine; Canary's zero added count. */
    override fun addItemBatchToPaginedContainer(
        player: Any,
        container: Any,
        itemId: Int,
        count: Int,
        tier: Int,
        flags: Int,
    ): Int? = (player as? Player)?.let { 0 }

    /** PARITY GAP: no store inbox ownership on the Kotlin `Player`; Canary's miss. */
    override fun getStoreInbox(player: Any): Any? = (player as? Player)?.let { null }

    /** PARITY GAP: no container-send protocol sink; succeeds inertly. */
    override fun sendContainer(player: Any, container: Any): Boolean? = (player as? Player)?.let { true }

    /** PARITY GAP: no container-update protocol sink; succeeds inertly. */
    override fun sendUpdateContainer(player: Any, container: Any): Boolean? = (player as? Player)?.let { true }

    override fun getContainerId(player: Any, container: Any): Int? {
        val target = player as? Player ?: return null
        val resolved = container as? Container ?: return null
        return target.getContainerID(resolved)
    }

    override fun getContainerById(player: Any, id: Int): Any? = (player as? Player)?.getContainerByID(id)

    override fun getContainerIndex(player: Any, id: Int): Int? = (player as? Player)?.getContainerIndex(id)

    /** PARITY GAP: no loot pouch ownership on the Kotlin `Player`; Canary's miss. */
    override fun getLootPouch(player: Any): Any? = (player as? Player)?.let { null }

    override fun getFreeBackpackSlots(player: Any): Int? {
        val target = player as? Player ?: return null
        val backpack = target.getBackpack() ?: return 0
        return max(0, backpack.capacity - backpack.size())
    }

    override fun sendBlessStatus(player: Any): Boolean? {
        val target = player as? Player ?: return null
        target.sendBlessStatus()
        return true
    }

    override fun hasBlessing(player: Any, blessing: Int): Boolean? = (player as? Player)?.hasBlessing(blessing)

    override fun addBlessing(player: Any, blessing: Int, count: Int): Boolean? {
        val target = player as? Player ?: return null
        target.addBlessing(blessing, count)
        target.sendBlessStatus()
        return true
    }

    override fun removeBlessing(player: Any, blessing: Int, count: Int): Boolean? {
        val target = player as? Player ?: return null
        if (!target.hasBlessing(blessing)) {
            return false
        }
        target.removeBlessing(blessing, count)
        return true
    }

    override fun getBlessingCount(player: Any, index: Int, storeCount: Boolean): Int? =
        (player as? Player)?.getBlessingCount(index, storeCount)

    override fun isVip(player: Any): Boolean? = (player as? Player)?.isPremium()

    override fun getVipDays(player: Any): Int? = (player as? Player)?.account?.getPremiumRemainingDays()

    override fun getVipTime(player: Any): Long? {
        val target = player as? Player ?: return null
        return target.account?.getPremiumLastDay() ?: 0L
    }

    override fun getAchievementPoints(player: Any): Int? {
        val target = player as? Player ?: return null
        return achievementsOf(target).getPoints()
    }

    override fun addAchievementPoints(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        if (amount > 0) {
            achievementsOf(target).addPoints(amount)
        }
        return true
    }

    override fun removeAchievementPoints(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        if (amount > 0) {
            achievementsOf(target).removePoints(amount)
        }
        return true
    }

    override fun hasAchievement(player: Any, achievementId: Int): Boolean? {
        val target = player as? Player ?: return null
        return achievementsOf(target).isUnlocked(achievementId)
    }

    override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? {
        val target = player as? Player ?: return null
        return achievementsOf(target).add(achievementId, sendMessage)
    }

    override fun removeAchievement(player: Any, achievementId: Int): Boolean? {
        val target = player as? Player ?: return null
        return achievementsOf(target).remove(achievementId)
    }

    override fun kv(player: Any): Any? = (player as? Player)?.kv

    /** PARITY GAP: no save-manager seam; reports success without persisting. */
    override fun save(player: Any): Boolean? = (player as? Player)?.let { true }

    private fun achievementsOf(player: Player): PlayerAchievement = achievements.getOrPut(player.id) {
        PlayerAchievement(
            player = PlayerAchievementView(player),
            kv = player.kv,
            registry = achievementRegistry,
        )
    }

    private companion object {
        /** `FACTION_PLAYER` (`src/game/game_definitions.hpp:45`). */
        const val FACTION_PLAYER = 1
    }
}

/**
 * Canary `player:getClient()` payload, mirroring the table
 * `PlayerFunctions::luaPlayerGetClient` pushes: `version`
 * (`Player::getProtocolVersion`), `versionString`
 * (`Client::getClientVersionString`) and `os` (`Player::getOperatingSystem`).
 */
data class LuaClientInfo(
    val version: Int,
    val versionString: String,
    val os: Int,
)

/**
 * Engine seam for the remaining `Player` metatable methods of
 * `PlayerFunctions::init` (src/lua/functions/creatures/player/player_functions.cpp).
 *
 * Nullable results mirror Canary's `lua_pushnil` guards (non-player receiver,
 * unknown outfit/mount name, missing reward, missing client info).
 */
interface PlayerRemainderOperations {
    /**
     * `Outfits::getOutfitByName(player->getSex(), name)` (outfit.cpp:115-123);
     * null mirrors the `Outfit not found` miss.
     */
    fun outfitLookTypeByName(player: Any, name: String): Int?

    /** `PlayerFunctions::luaPlayerAddOutfit` (player_functions.cpp:2922). */
    fun addOutfit(player: Any, lookType: Int, addon: Int): Boolean?

    /** `PlayerFunctions::luaPlayerAddOutfitAddon` (player_functions.cpp:2947). */
    fun addOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean?

    /** `PlayerFunctions::luaPlayerRemoveOutfit` (player_functions.cpp:2961). */
    fun removeOutfit(player: Any, lookType: Int): Boolean?

    /** `PlayerFunctions::luaPlayerRemoveOutfitAddon` (player_functions.cpp:2973). */
    fun removeOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean?

    /** `PlayerFunctions::luaPlayerHasOutfit` (player_functions.cpp:2986). */
    fun hasOutfit(player: Any, lookType: Int, addon: Int): Boolean?

    /** `PlayerFunctions::luaPlayerSendOutfitWindow` (player_functions.cpp:2999). */
    fun sendOutfitWindow(player: Any): Boolean?

    /** `g_game().mounts->getMountByName(name)` (`mounts.cpp`); 0 when unknown. */
    fun mountIdByName(name: String): Int

    /** `PlayerFunctions::luaPlayerAddMount` (player_functions.cpp:3011); `Player::tameMount`. */
    fun addMount(player: Any, mountId: Int): Boolean?

    /** `PlayerFunctions::luaPlayerRemoveMount` (player_functions.cpp:3034); `Player::untameMount`. */
    fun removeMount(player: Any, mountId: Int): Boolean?

    /** `PlayerFunctions::luaPlayerHasMount` (player_functions.cpp:3057); `Player::hasMount`. */
    fun hasMount(player: Any, mountId: Int): Boolean?

    /** `PlayerFunctions::luaPlayerAddFamiliar` (player_functions.cpp:3080). */
    fun addFamiliar(player: Any, lookType: Int): Boolean?

    /** `PlayerFunctions::luaPlayerRemoveFamiliar` (player_functions.cpp:3092). */
    fun removeFamiliar(player: Any, lookType: Int): Boolean?

    /** `PlayerFunctions::luaPlayerHasFamiliar` (player_functions.cpp:3104); `Player::canFamiliar`. */
    fun hasFamiliar(player: Any, lookType: Int): Boolean?

    /** `PlayerFunctions::luaPlayerSetFamiliarLooktype` (player_functions.cpp:3116). */
    fun setFamiliarLooktype(player: Any, lookType: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetFamiliarLooktype` (player_functions.cpp:3128). */
    fun getFamiliarLooktype(player: Any): Int?

    /** `PlayerFunctions::luaPlayerGetPronoun` (player_functions.cpp:2016). */
    fun getPronoun(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetPronoun` (player_functions.cpp:2027). */
    fun setPronoun(player: Any, pronoun: Int): Boolean?

    /**
     * `Player::getReward(rewardId, autoCreate)` (player.cpp:2058). Null mirrors
     * Canary's `lua_pushboolean(false)` miss; the binding returns it as `false`.
     */
    fun getReward(player: Any, rewardId: Long, autoCreate: Boolean): Any?

    /** `PlayerFunctions::luaPlayerRemoveReward` (player_functions.cpp:1244). */
    fun removeReward(player: Any, rewardId: Long): Boolean?

    /** `PlayerFunctions::luaPlayerGetRewardList` (player_functions.cpp:1258). */
    fun getRewardList(player: Any): List<Long>?

    /** `PlayerFunctions::luaPlayerSetDailyReward` (player_functions.cpp:1278). */
    fun setDailyReward(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetClient` (player_functions.cpp:3663). */
    fun getClient(player: Any): LuaClientInfo?

    /** `PlayerFunctions::luaPlayerGetBaseXpGain` (player_functions.cpp:3928). */
    fun getBaseXpGain(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetBaseXpGain` (player_functions.cpp:3939). */
    fun setBaseXpGain(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetVoucherXpBoost` (player_functions.cpp:3952). */
    fun getVoucherXpBoost(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetVoucherXpBoost` (player_functions.cpp:3963). */
    fun setVoucherXpBoost(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetGrindingXpBoost` (player_functions.cpp:3976). */
    fun getGrindingXpBoost(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetGrindingXpBoost` (player_functions.cpp:3987). */
    fun setGrindingXpBoost(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetXpBoostPercent` (player_functions.cpp:4000). */
    fun getXpBoostPercent(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetXpBoostPercent` (player_functions.cpp:4011). */
    fun setXpBoostPercent(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetXpBoostTime` (player_functions.cpp:4062). */
    fun getXpBoostTime(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetXpBoostTime` (player_functions.cpp:4048). */
    fun setXpBoostTime(player: Any, value: Int): Boolean?

    /** `PlayerFunctions::luaPlayerGetStaminaXpBoost` (player_functions.cpp:4024). */
    fun getStaminaXpBoost(player: Any): Int?

    /** `PlayerFunctions::luaPlayerSetStaminaXpBoost` (player_functions.cpp:4035). */
    fun setStaminaXpBoost(player: Any, value: Int): Boolean?
}

/**
 * Real, non-no-op implementation of [PlayerRemainderOperations] backed by the
 * Kotlin domain objects.
 *
 * PARITY GAPS (recorded, not invented):
 *  - The Kotlin `Player` has no XP-boost fields (Canary `player.hpp:1813-1824`),
 *    so the six boosts are held per player here; a domain field would be the
 *    faithful location but `Player` is outside this file's ownership.
 *  - `sendOutfitWindow` has no protocol sink on `Player`
 *    (`PlayerAttachedEffectsPlayer` owns the send seam); it succeeds without
 *    transmitting. `getClient` needs a protocol/client seam and reports nil
 *    until a [clientInfoProvider] is injected.
 *  - `Player::tameMount` validates the id against the player's internal `Mounts`
 *    registry, which the port cannot populate from here; [mountDefinitions] only
 *    resolves names and looks up mounts for [hasMount].
 *  - `addOutfit` with an unknown outfit name raises Canary's `reportErrorFunc`
 *    (which only logs) and returns a stack artifact; the binding normalizes that
 *    miss to nil.
 *  - `getPronoun`/`setPronoun` map out-of-range numbers to `PLAYERPRONOUN_UNSET`
 *    instead of the C++ cast of arbitrary integers into `PlayerPronoun_t`.
 *
 * The per-player outfit and XP-boost maps are weak keys (`Player` uses identity
 * equality) so a released player is not retained, matching the lifetime rule for
 * state that outlives the scripting call.
 */
class WorldPlayerRemainderOperations(
    private val outfitDefinitions: Outfits = Outfits(),
    private val mountDefinitions: Mounts = Mounts(),
    private val clientInfoProvider: ((Player) -> LuaClientInfo?)? = null,
) : PlayerRemainderOperations {

    private val outfitsByPlayer = WeakHashMap<Player, PlayerOutfits>()

    private val xpBoostsByPlayer = WeakHashMap<Player, XpBoostState>()

    override fun outfitLookTypeByName(player: Any, name: String): Int? {
        val target = player as? Player ?: return null
        return outfitDefinitions.getOutfitByName(target.getSex(), name)?.lookType
    }

    override fun addOutfit(player: Any, lookType: Int, addon: Int): Boolean? {
        val target = player as? Player ?: return null
        outfitsOf(target).addOutfit(lookType, addon)
        return true
    }

    override fun addOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean? {
        val target = player as? Player ?: return null
        outfitsOf(target).addOutfit(lookType, addon)
        return true
    }

    override fun removeOutfit(player: Any, lookType: Int): Boolean? =
        (player as? Player)?.let { outfitsOf(it).removeOutfit(lookType) }

    override fun removeOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean? =
        (player as? Player)?.let { outfitsOf(it).removeOutfitAddon(lookType, addon) }

    override fun hasOutfit(player: Any, lookType: Int, addon: Int): Boolean? =
        (player as? Player)?.let { outfitsOf(it).canWear(lookType, addon) }

    /** PARITY GAP: no outfit-window protocol sink; succeeds without transmitting. */
    override fun sendOutfitWindow(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun mountIdByName(name: String): Int = mountDefinitions.getMountByName(name)?.id ?: 0

    override fun addMount(player: Any, mountId: Int): Boolean? =
        (player as? Player)?.features?.mounts?.tameMount(mountId)

    override fun removeMount(player: Any, mountId: Int): Boolean? =
        (player as? Player)?.features?.mounts?.untameMount(mountId)

    override fun hasMount(player: Any, mountId: Int): Boolean? {
        val target = player as? Player ?: return null
        val mount = mountDefinitions.getMountByID(mountId) ?: return null
        return target.features.mounts.hasMount(mount)
    }

    override fun addFamiliar(player: Any, lookType: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.familiars.addFamiliar(lookType)
        return true
    }

    override fun removeFamiliar(player: Any, lookType: Int): Boolean? =
        (player as? Player)?.features?.familiars?.removeFamiliar(lookType)

    override fun hasFamiliar(player: Any, lookType: Int): Boolean? =
        (player as? Player)?.features?.familiars?.canFamiliar(lookType)

    override fun setFamiliarLooktype(player: Any, lookType: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.familiars.setFamiliarLooktype(lookType)
        return true
    }

    override fun getFamiliarLooktype(player: Any): Int? =
        (player as? Player)?.features?.familiars?.familiarLooktype

    override fun getPronoun(player: Any): Int? = (player as? Player)?.getPronoun()?.value

    override fun setPronoun(player: Any, pronoun: Int): Boolean? {
        val target = player as? Player ?: return null
        val value = PlayerPronoun_t.entries.firstOrNull { it.value == pronoun }
            ?: PlayerPronoun_t.PLAYERPRONOUN_UNSET
        target.setPronoun(value)
        return true
    }

    override fun getReward(player: Any, rewardId: Long, autoCreate: Boolean): Any? =
        (player as? Player)?.features?.rewardChest?.getReward(rewardId, autoCreate)

    override fun removeReward(player: Any, rewardId: Long): Boolean? {
        val target = player as? Player ?: return null
        target.features.rewardChest.removeReward(rewardId)
        return true
    }

    override fun getRewardList(player: Any): List<Long>? =
        (player as? Player)?.features?.rewardChest?.getRewardList()

    override fun setDailyReward(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.dailyReward.setDailyReward(value)
        return true
    }

    override fun getClient(player: Any): LuaClientInfo? {
        val target = player as? Player ?: return null
        return clientInfoProvider?.invoke(target)
    }

    override fun getBaseXpGain(player: Any): Int? = (player as? Player)?.let { boostsOf(it).baseXpGain }

    override fun setBaseXpGain(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).baseXpGain = value
        return true
    }

    override fun getVoucherXpBoost(player: Any): Int? =
        (player as? Player)?.let { boostsOf(it).voucherXpBoost }

    override fun setVoucherXpBoost(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).voucherXpBoost = value
        return true
    }

    override fun getGrindingXpBoost(player: Any): Int? =
        (player as? Player)?.let { boostsOf(it).grindingXpBoost }

    override fun setGrindingXpBoost(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).grindingXpBoost = value
        return true
    }

    override fun getXpBoostPercent(player: Any): Int? =
        (player as? Player)?.let { boostsOf(it).xpBoostPercent }

    override fun setXpBoostPercent(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).xpBoostPercent = value
        return true
    }

    override fun getXpBoostTime(player: Any): Int? = (player as? Player)?.let { boostsOf(it).xpBoostTime }

    override fun setXpBoostTime(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).xpBoostTime = value
        return true
    }

    override fun getStaminaXpBoost(player: Any): Int? =
        (player as? Player)?.let { boostsOf(it).staminaXpBoost }

    override fun setStaminaXpBoost(player: Any, value: Int): Boolean? {
        val target = player as? Player ?: return null
        boostsOf(target).staminaXpBoost = value
        return true
    }

    private fun outfitsOf(player: Player): PlayerOutfits = outfitsByPlayer.getOrPut(player) {
        PlayerOutfits(
            outfits = outfitDefinitions,
            player = OutfitPlayer { player.getSex() },
            isPremium = { player.isPremium() },
            hasAccess = { player.isAccessPlayer() },
        )
    }

    private fun boostsOf(player: Player): XpBoostState = xpBoostsByPlayer.getOrPut(player) { XpBoostState() }

    /** Canary `Player` XP-boost fields and their declared defaults (player.hpp:1813-1824). */
    private class XpBoostState {
        var baseXpGain: Int = 100
        var voucherXpBoost: Int = 0
        var grindingXpBoost: Int = 0
        var xpBoostPercent: Int = 0
        var xpBoostTime: Int = 0
        var staminaXpBoost: Int = 100
    }
}
