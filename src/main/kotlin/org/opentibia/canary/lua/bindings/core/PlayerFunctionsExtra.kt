package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Virtue_t
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Canary `player:getTitles()` row. Mirrors the `{ id, name, description }` entry pushed
 * by `PlayerFunctions::luaPlayerGetTitles` (player_functions.cpp:5174).
 */
data class LuaPlayerTitle(
    val id: Int,
    val name: String,
    val description: String,
)

/**
 * Canary `player:getLivestreamViewers()` payload. Mirrors the table built by
 * `PlayerFunctions::luaPlayerGetLivestreamViewers` (player_functions.cpp:5647).
 */
data class LuaLivestreamViewers(
    val description: String,
    val broadcast: Boolean,
    val password: String,
    val names: List<String>,
    val mutes: List<String>,
    val bans: List<String>,
    val kick: List<String>,
)

/**
 * Present fields of the `player:setLivestreamViewers(data)` table. Absent fields are null
 * and leave the current state untouched, mirroring the C++ `hasField` checks
 * (player_functions.cpp:5669-5734).
 */
data class LuaLivestreamOverrides(
    val description: String? = null,
    val broadcast: Boolean? = null,
    val password: String? = null,
    val mutes: List<String>? = null,
    val bans: List<String>? = null,
    val kick: List<String>? = null,
)

/**
 * Engine seam for the batch 4 `Player` primitives in
 * src/lua/functions/creatures/player/player_functions.cpp (registrations at
 * player_functions.cpp:391-522): market/idle helpers, Exaltation Forge, factions,
 * hazard/loyalty, bosstiary, sound effects, group flags, Wheel of Destiny, concoction/
 * food, VIP/kv/store inbox, achievements/badges/titles, store summary/screenshots/icons,
 * animus mastery, serenity/virtue/harmony, weapon proficiency, livestream and the OTCR
 * map-shader/custom-outfit features.
 *
 * Nullable results mirror Canary's `lua_pushnil` guards. Where Canary pushes `false` and
 * returns 0 (dropping the value) or `return 1` with an untouched stack, the binding
 * normalizes the C stack quirk to nil, exactly like the batch 1 [PlayerOperations] port.
 */
interface PlayerExtraOperations {
    /** `LuaPlayerGetIdleTime` -> `Player::getIdleTime()`; nil without a player. */
    fun getIdleTime(player: Any): Long?

    /** `LuaPlayerGetFreeBackpackSlots` -> max(0, free backpack slots). */
    fun getFreeBackpackSlots(player: Any): Int?

    /** `LuaPlayerIsOffline`; true for a missing receiver, like the C++ fallback. */
    fun isOffline(player: Any): Boolean

    /** `LuaPlayerOpenMarket` -> `Player::sendMarketEnter(lastDepotId)`. */
    fun openMarket(player: Any): Boolean?

    /** Forge window open/close sends. */
    fun openForge(player: Any): Boolean?

    fun closeForge(player: Any): Boolean?

    /** Exaltation Forge dust ledger (`Player::addForgeDusts` etc.). */
    fun addForgeDusts(player: Any, amount: Long): Boolean?

    fun removeForgeDusts(player: Any, amount: Long): Boolean?

    fun getForgeDusts(player: Any): Long?

    fun setForgeDusts(player: Any, amount: Long): Boolean?

    fun addForgeDustLevel(player: Any, amount: Long): Boolean?

    fun removeForgeDustLevel(player: Any, amount: Long): Boolean?

    fun getForgeDustLevel(player: Any): Long?

    /** `Player::getForgeSliversAndCores().first`. */
    fun getForgeSlivers(player: Any): Long?

    /** `Player::getForgeSliversAndCores().second`. */
    fun getForgeCores(player: Any): Long?

    /** `Player::setFaction` / `Player::getFaction` (faction system). */
    fun setFaction(player: Any, factionId: Int): Boolean?

    fun getFaction(player: Any): Int?

    /** `Player::isUIExhausted(time)`. */
    fun isUIExhausted(player: Any, time: Int): Boolean?

    /** `Player::updateUIExhausted()`. */
    fun updateUIExhausted(player: Any): Boolean?

    /** `Player::sendBosstiaryCooldownTimer()`. */
    fun sendBosstiaryCooldownTimer(player: Any): Boolean?

    /** Bosstiary lookups keyed by the monster name (`g_monsters().getMonsterType`). */
    fun getBosstiaryLevel(player: Any, monsterName: String): Int?

    fun getBosstiaryKills(player: Any, monsterName: String): Int?

    fun addBosstiaryKill(player: Any, monsterName: String, amount: Long): Boolean?

    /** `Player::setBossPoints` / `Player::setRemoveBossTime` / boss slot queries. */
    fun setBossPoints(player: Any, points: Long): Boolean?

    fun setRemoveBossTime(player: Any, time: Int): Boolean?

    fun getSlotBossId(player: Any, slotId: Int): Long?

    fun getBossBonus(player: Any, slotId: Int): Long?

    /** Sound-effect broadcasts; [actor] maps to `SourceEffect_t.OWN`. */
    fun sendSingleSoundEffect(player: Any, soundId: Int, actor: Boolean): Boolean?

    fun sendDoubleSoundEffect(
        player: Any,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Boolean,
    ): Boolean?

    fun sendAmbientSoundEffect(player: Any, soundId: Int): Boolean?

    fun sendMusicSoundEffect(player: Any, soundId: Int): Boolean?

    /** `Player::setName` + save, dropping the persistent `namelock` key. */
    fun changeName(player: Any, newName: String): Boolean?

    /** Group-flag accessors mirroring `Player::hasFlag`/`setFlag`/`removeFlag`. */
    fun hasGroupFlag(player: Any, flag: Int): Boolean?

    fun setGroupFlag(player: Any, flag: Int): Boolean?

    fun removeGroupFlag(player: Any, flag: Int): Boolean?

    /** Hazard system points (`Player::set/getHazardSystemPoints`). */
    fun setHazardSystemPoints(player: Any, amount: Int): Boolean?

    fun getHazardSystemPoints(player: Any): Int?

    /** Account loyalty bonus/points/title. */
    fun setLoyaltyBonus(player: Any, amount: Int): Boolean?

    fun getLoyaltyBonus(player: Any): Int?

    fun getLoyaltyPoints(player: Any): Int?

    fun getLoyaltyTitle(player: Any): String?

    fun setLoyaltyTitle(player: Any, name: String): Boolean?

    /**
     * Wheel of Destiny spell instants/upgrades/stages. [name]/[add]/[stage] are null on
     * the C++ overload branches: `instantSkillWOD(name)` gets, `(name, value)` sets;
     * `upgradeSpellsWOD()` resets, `(name)` gets, `(name, add)` upgrades/downgrades;
     * `revelationStageWOD()` resets, `(name)` gets, `(name, stage)` sets.
     */
    fun instantSkillWOD(player: Any, name: String, value: Boolean?): Boolean?

    fun upgradeSpellsWOD(player: Any, name: String?, add: Boolean?): Any?

    fun revelationStageWOD(player: Any, name: String?, stage: Int?): Any?

    /** `Player::reloadData` sends (skills/stats/basic data + wheel cooldown + reload). */
    fun reloadData(player: Any): Boolean?

    fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean?

    fun avatarTimer(player: Any, value: Long?): Any?

    fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean?

    fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Long?

    fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Long?

    fun wheelUnlockScroll(player: Any, scrollName: String): Boolean?

    /** `Player::updateConcoction` / `Player::updateFood` / `Player::clearCooldowns`. */
    fun updateConcoction(player: Any, itemId: Int, timeLeft: Int): Boolean?

    fun updateFood(player: Any, itemId: Int, timeLeft: Long): Boolean?

    fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean?

    /** `Player::isVip` (requires `VIP_SYSTEM_ENABLED` and a premium account). */
    fun isVip(player: Any): Boolean?

    /** `Player::getPremiumDays` / `Player::getPremiumLastDay` (VIP aliases). */
    fun getVipDays(player: Any): Int?

    fun getVipTime(player: Any): Long?

    /** `Player::kv()` shared userdata. */
    fun getKv(player: Any): Any?

    /** `Player::getStoreInbox()`; null models Canary's `lua_pushnil` for a missing player. */
    fun getStoreInbox(player: Any): Any?

    /** `g_game().getAchievementByName(name).id`, 0 when unknown. */
    fun achievementIdByName(name: String): Int

    /** `Player::achiev()` queries. */
    fun hasAchievement(player: Any, achievementId: Int): Boolean?

    fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean?

    fun removeAchievement(player: Any, achievementId: Int): Boolean?

    fun getAchievementPoints(player: Any): Int?

    fun addAchievementPoints(player: Any, amount: Int): Boolean?

    fun removeAchievementPoints(player: Any, amount: Int): Boolean?

    /** `Player::badge()` / `Player::title()` mutations and listings. */
    fun addBadge(player: Any, badgeId: Int): Boolean?

    fun addTitle(player: Any, titleId: Int): Boolean?

    fun getTitles(player: Any): List<LuaPlayerTitle>?

    fun setCurrentTitle(player: Any, titleId: Int): Boolean?

    /** Store cyclopedia summary/screenshot/creature-icon sends. */
    fun createTransactionSummary(player: Any, type: Int, amount: Int, id: String): Boolean?

    fun takeScreenshot(
        player: Any,
        type: Int,
        skillId: Int,
        skillLevel: Int,
        achievementName: String,
        raceId: Int,
        bestiaryStep: Int,
    ): Boolean?

    fun sendIconBakragore(player: Any, iconType: Int): Boolean?

    fun removeIconBakragore(player: Any, iconType: Int): Boolean?

    fun sendCreatureAppear(player: Any, isLogin: Boolean): Boolean?

    fun setRaceIconOverlay(player: Any, raceId: Int, icon: Int, enabled: Boolean): Boolean?

    fun clearRaceIconOverlays(player: Any, icon: Int): Boolean?

    fun refreshVisibleCreatureIcons(player: Any): Boolean?

    /** Soulwar animus mastery set. */
    fun addAnimusMastery(player: Any, monsterType: String): Boolean?

    fun removeAnimusMastery(player: Any, monsterType: String): Boolean?

    fun hasAnimusMastery(player: Any, monsterType: String): Boolean?

    /** Serenity/virtue/harmony state (`Player::setSerene` etc.). */
    fun setSerene(player: Any, set: Boolean, ticks: Int): Boolean?

    fun getVirtue(player: Any): Int?

    fun setVirtue(player: Any, virtue: Int): Boolean?

    fun fillHarmony(player: Any): Boolean?

    fun getHarmony(player: Any): Int?

    fun getHarmonyDamage(player: Any, baseMin: Int, baseMax: Int): Pair<Long, Long>?

    fun calculateFlatDamageHealing(player: Any): Long?

    /** `Player::weaponProficiency().addExperience(experience, itemId)`. */
    fun addWeaponExperience(player: Any, experience: Int, itemId: Int): Boolean?

    /** Livestream viewer queries and state updates. */
    fun getLivestreamViewersCount(player: Any): Int?

    fun getLivestreamViewers(player: Any): LuaLivestreamViewers?

    fun setLivestreamViewers(player: Any, viewers: LuaLivestreamOverrides): Boolean?

    fun isLivestreamViewer(player: Any): Boolean?

    /** OTCR attached effects: map shader and custom outfits. */
    fun getMapShader(player: Any): String?

    fun setMapShader(player: Any, shaderName: String): Boolean?

    fun addCustomOutfit(player: Any, type: String, idOrName: Any): Boolean?

    fun removeCustomOutfit(player: Any, type: String, idOrName: Any): Boolean?
}

/**
 * Registers the batch 4 Canary `Player` methods (player_functions.cpp:391-522):
 *
 *   player:getIdleTime()
 *   player:getFreeBackpackSlots()
 *   player:isOffline()
 *   player:openMarket()
 *   player:instantSkillWOD(name[, value])
 *   player:upgradeSpellsWOD([name[, add]])
 *   player:revelationStageWOD([name[, set]])
 *   player:reloadData()
 *   player:onThinkWheelOfDestiny([force])
 *   player:avatarTimer([value])
 *   player:getWheelSpellAdditionalArea(spellName)
 *   player:getWheelSpellAdditionalTarget(spellName)
 *   player:getWheelSpellAdditionalDuration(spellName)
 *   player:wheelUnlockScroll(scrollName)
 *   player:openForge()
 *   player:closeForge()
 *   player:addForgeDusts(amount)
 *   player:removeForgeDusts(amount)
 *   player:getForgeDusts()
 *   player:setForgeDusts(amount)
 *   player:addForgeDustLevel([amount])
 *   player:removeForgeDustLevel([amount])
 *   player:getForgeDustLevel()
 *   player:getForgeSlivers()
 *   player:getForgeCores()
 *   player:isUIExhausted(time)
 *   player:updateUIExhausted()
 *   player:setFaction(factionId)
 *   player:getFaction()
 *   player:sendBosstiaryCooldownTimer()
 *   player:getBosstiaryLevel(name)
 *   player:getBosstiaryKills(name)
 *   player:addBosstiaryKill(name[, amount])
 *   player:setBossPoints([points])
 *   player:setRemoveBossTime([time])
 *   player:getSlotBossId(slotId)
 *   player:getBossBonus(slotId)
 *   player:sendSingleSoundEffect(soundId[, actor])
 *   player:sendDoubleSoundEffect(mainSoundId, secondarySoundId[, actor])
 *   player:sendAmbientSoundEffect(id)
 *   player:sendMusicSoundEffect(id)
 *   player:changeName(newName)
 *   player:hasGroupFlag(flag)
 *   player:setGroupFlag(flag)
 *   player:removeGroupFlag(flag)
 *   player:setHazardSystemPoints([amount])
 *   player:getHazardSystemPoints()
 *   player:setLoyaltyBonus(amount)
 *   player:getLoyaltyBonus()
 *   player:getLoyaltyPoints()
 *   player:getLoyaltyTitle()
 *   player:setLoyaltyTitle(name)
 *   player:updateConcoction(itemId, timeLeft)
 *   player:updateFood(itemId, timeLeft)
 *   player:clearSpellCooldowns([spenders[, builders[, ticks]]])
 *   player:isVip()
 *   player:getVipDays()
 *   player:getVipTime()
 *   player:kv()
 *   player:getStoreInbox()
 *   player:hasAchievement(id or name)
 *   player:addAchievement(id or name[, sendMessage])
 *   player:removeAchievement(id or name)
 *   player:getAchievementPoints()
 *   player:addAchievementPoints(amount)
 *   player:removeAchievementPoints(amount)
 *   player:addBadge(id)
 *   player:addTitle(id)
 *   player:getTitles()
 *   player:setCurrentTitle(id)
 *   player:createTransactionSummary(type, amount[, id])
 *   player:takeScreenshot(type[, skillId[, skillLevel[, achievementName[, raceId[, bestiaryStep]]]]])
 *   player:sendIconBakragore(iconType)
 *   player:removeIconBakragore([iconType])
 *   player:sendCreatureAppear([isLogin])
 *   player:setRaceIconOverlay(raceId, icon[, enabled])
 *   player:clearRaceIconOverlays(icon)
 *   player:refreshVisibleCreatureIcons()
 *   player:addAnimusMastery(monsterType)
 *   player:removeAnimusMastery(monsterType)
 *   player:hasAnimusMastery(monsterType)
 *   player:setSerene([set[, ticks]])
 *   player:getVirtue()
 *   player:setVirtue([virtue])
 *   player:fillHarmony()
 *   player:getHarmony()
 *   player:getHarmonyDamage(baseMin, baseMax)
 *   player:calculateFlatDamageHealing()
 *   player:addWeaponExperience(experience[, itemId])
 *   player:getLivestreamViewersCount()
 *   player:getLivestreamViewers()
 *   player:setLivestreamViewers(data)
 *   player:isLivestreamViewer()
 *   player:getMapShader()
 *   player:setMapShader(shaderName[, temporary])
 *   player:removeCustomOutfit(type, id or name)
 *   player:addCustomOutfit(type, id or name)
 *
 * `player:getName` is already registered by [CreatureFunctions] for the `Player`
 * metatable and is intentionally not shadowed here.
 */
object PlayerFunctionsExtra {
    fun install(env: LuaEnvironment, operations: PlayerExtraOperations = UnwiredPlayerExtraOperations) {
        val registry = env.userdata
        val register: (String, LuaValue) -> Unit = { name, fn ->
            PlayerFunctions.PLAYER_TYPES.forEach { registry.registerMethod(it, name, fn) }
        }

        register("getIdleTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getIdleTime(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getFreeBackpackSlots", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getFreeBackpackSlots(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isOffline", luaFunction { args ->
            val player = retrieveUserdata(args, 1)
            if (player == null) LuaValue.TRUE else LuaValue.valueOf(operations.isOffline(player))
        })

        register("openMarket", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.openMarket(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("instantSkillWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val name = args.arg(2).checkjstring()
            val value = if (args.narg() == 2) null else args.arg(3).toboolean()
            operations.instantSkillWOD(player, name, value)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("upgradeSpellsWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val result = when (args.narg()) {
                1 -> operations.upgradeSpellsWOD(player, null, null)
                2 -> operations.upgradeSpellsWOD(player, args.arg(2).checkjstring(), null)
                else -> operations.upgradeSpellsWOD(player, args.arg(2).checkjstring(), args.arg(3).toboolean())
            }
            attributeValueToLua(result)
        })

        register("revelationStageWOD", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val result = when (args.narg()) {
                1 -> operations.revelationStageWOD(player, null, null)
                2 -> operations.revelationStageWOD(player, args.arg(2).checkjstring(), null)
                else -> operations.revelationStageWOD(player, args.arg(2).checkjstring(), args.arg(3).toint())
            }
            attributeValueToLua(result)
        })

        register("reloadData", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.reloadData(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("onThinkWheelOfDestiny", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.onThinkWheelOfDestiny(player, args.arg(2).optboolean(false))?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        register("avatarTimer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val result = if (args.narg() == 1) {
                operations.avatarTimer(player, null)
            } else {
                operations.avatarTimer(player, args.arg(2).tolong())
            }
            attributeValueToLua(result)
        })

        register("getWheelSpellAdditionalArea", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).checkjstring()
            if (spellName.isEmpty()) {
                return@luaFunction LuaValue.valueOf(false)
            }
            operations.getWheelSpellAdditionalArea(player, spellName)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.valueOf(false)
        })

        register("getWheelSpellAdditionalTarget", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).checkjstring()
            if (spellName.isEmpty()) {
                return@luaFunction LuaValue.valueOf(false)
            }
            operations.getWheelSpellAdditionalTarget(player, spellName)?.let { LuaValue.valueOf(it.toDouble()) }
                ?: LuaValue.valueOf(false)
        })

        register("getWheelSpellAdditionalDuration", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellName = args.arg(2).checkjstring()
            if (spellName.isEmpty()) {
                return@luaFunction LuaValue.valueOf(false)
            }
            operations.getWheelSpellAdditionalDuration(player, spellName)?.let { LuaValue.valueOf(it.toDouble()) }
                ?: LuaValue.valueOf(false)
        })

        register("wheelUnlockScroll", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val scrollName = args.arg(2).checkjstring()
            if (scrollName.isEmpty()) {
                return@luaFunction LuaValue.valueOf(false)
            }
            operations.wheelUnlockScroll(player, scrollName)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.valueOf(false)
        })

        register("openForge", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.openForge(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("closeForge", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.closeForge(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addForgeDusts", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addForgeDusts(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeForgeDusts", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeForgeDusts(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getForgeDusts", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getForgeDusts(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("setForgeDusts", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setForgeDusts(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addForgeDustLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addForgeDustLevel(player, args.arg(2).optlong(1))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeForgeDustLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeForgeDustLevel(player, args.arg(2).optlong(1))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getForgeDustLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getForgeDustLevel(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getForgeSlivers", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getForgeSlivers(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("getForgeCores", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getForgeCores(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
        })

        register("setFaction", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setFaction(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getFaction", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getFaction(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isUIExhausted", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isUIExhausted(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("updateUIExhausted", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.updateUIExhausted(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendBosstiaryCooldownTimer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendBosstiaryCooldownTimer(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getBosstiaryLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBosstiaryLevel(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getBosstiaryKills", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBosstiaryKills(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("addBosstiaryKill", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val monsterName = args.arg(2).checkjstring()
            val amount = args.arg(3).optlong(1)
            operations.addBosstiaryKill(player, monsterName, amount)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("setBossPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setBossPoints(player, args.arg(2).optlong(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setRemoveBossTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setRemoveBossTime(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getSlotBossId", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSlotBossId(player, args.arg(2).toint())?.let { LuaValue.valueOf(it.toDouble()) }
                ?: LuaValue.NIL
        })

        register("getBossBonus", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getBossBonus(player, args.arg(2).toint())?.let { LuaValue.valueOf(it.toDouble()) }
                ?: LuaValue.NIL
        })

        register("sendSingleSoundEffect", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val soundId = args.arg(2).toint()
            val actor = args.arg(3).optboolean(true)
            operations.sendSingleSoundEffect(player, soundId, actor)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("sendDoubleSoundEffect", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val mainSoundId = args.arg(2).toint()
            val secondarySoundId = args.arg(3).toint()
            val actor = args.arg(4).optboolean(true)
            operations.sendDoubleSoundEffect(player, mainSoundId, secondarySoundId, actor)?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        register("sendAmbientSoundEffect", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendAmbientSoundEffect(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("sendMusicSoundEffect", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendMusicSoundEffect(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("changeName", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.changeName(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("hasGroupFlag", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.hasGroupFlag(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setGroupFlag", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setGroupFlag(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeGroupFlag", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeGroupFlag(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setHazardSystemPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setHazardSystemPoints(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getHazardSystemPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getHazardSystemPoints(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setLoyaltyBonus", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setLoyaltyBonus(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getLoyaltyBonus", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLoyaltyBonus(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getLoyaltyPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLoyaltyPoints(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getLoyaltyTitle", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLoyaltyTitle(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setLoyaltyTitle", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setLoyaltyTitle(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("updateConcoction", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = args.arg(2).toint()
            val timeLeft = args.arg(3).toint()
            operations.updateConcoction(player, itemId, timeLeft)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("updateFood", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = args.arg(2).toint()
            val timeLeft = args.arg(3).tolong()
            operations.updateFood(player, itemId, timeLeft)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("clearSpellCooldowns", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spenders = args.arg(2).optboolean(false)
            val builders = args.arg(3).optboolean(false)
            val ticks = args.arg(4).optint(0)
            operations.clearSpellCooldowns(player, spenders, builders, ticks)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("isVip", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            operations.isVip(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(false)
        })

        register("getVipDays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            operations.getVipDays(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(false)
        })

        register("getVipTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            operations.getVipTime(player)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.valueOf(false)
        })

        register("kv", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            operations.getKv(player)?.let { registry.wrap("KV", it) } ?: LuaValue.NIL
        })

        register("getStoreInbox", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getStoreInbox(player)?.let { wrapItemUserdata(env, it) } ?: LuaValue.valueOf(false)
        })

        register("hasAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.hasAchievement(player, resolveAchievementId(args, operations))?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        register("addAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val achievementId = resolveAchievementId(args, operations)
            val sendMessage = args.arg(3).optboolean(true)
            operations.addAchievement(player, achievementId, sendMessage)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeAchievement", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeAchievement(player, resolveAchievementId(args, operations))?.let {
                LuaValue.valueOf(it)
            } ?: LuaValue.NIL
        })

        register("getAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getAchievementPoints(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addAchievementPoints(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeAchievementPoints", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeAchievementPoints(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("addBadge", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addBadge(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addTitle", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addTitle(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getTitles", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val titles = operations.getTitles(player) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                titles.forEachIndexed { index, title ->
                    table.set(
                        index + 1,
                        LuaTable().also { entry ->
                            entry.set("id", title.id)
                            entry.set("name", title.name)
                            entry.set("description", title.description)
                        },
                    )
                }
            }
        })

        register("setCurrentTitle", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setCurrentTitle(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("createTransactionSummary", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val type = args.arg(2).optint(0)
            if (type == 0) {
                return@luaFunction LuaValue.NIL
            }
            val amount = args.arg(3).optint(1)
            val id = args.arg(4).optjstring("")
            operations.createTransactionSummary(player, type, amount, id)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("takeScreenshot", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val type = args.arg(2).toint()
            val skillId = args.arg(3).optint(0)
            val skillLevel = args.arg(4).optint(0)
            val achievementName = args.arg(5).optjstring("")
            val raceId = args.arg(6).optint(0)
            val bestiaryStep = args.arg(7).optint(0)
            operations.takeScreenshot(
                player,
                type,
                skillId,
                skillLevel,
                achievementName,
                raceId,
                bestiaryStep,
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("sendIconBakragore", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendIconBakragore(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeIconBakragore", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeIconBakragore(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("sendCreatureAppear", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendCreatureAppear(player, args.arg(2).optboolean(false))?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("setRaceIconOverlay", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val raceId = args.arg(2).toint()
            val icon = args.arg(3).toint()
            val enabled = args.arg(4).optboolean(true)
            operations.setRaceIconOverlay(player, raceId, icon, enabled)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("clearRaceIconOverlays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.clearRaceIconOverlays(player, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("refreshVisibleCreatureIcons", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.refreshVisibleCreatureIcons(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("addAnimusMastery", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addAnimusMastery(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("removeAnimusMastery", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeAnimusMastery(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("hasAnimusMastery", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.hasAnimusMastery(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("setSerene", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val set = args.arg(2).optboolean(false)
            val ticks = args.arg(3).optint(-1)
            operations.setSerene(player, set, ticks)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getVirtue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getVirtue(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setVirtue", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setVirtue(player, args.arg(2).optint(0))?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("fillHarmony", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.fillHarmony(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getHarmony", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getHarmony(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getHarmonyDamage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val baseMin = args.arg(2).toint()
            val baseMax = args.arg(3).toint()
            val result = operations.getHarmonyDamage(player, baseMin, baseMax)
                ?: return@luaFunction LuaValue.NIL
            LuaValue.varargsOf(
                LuaValue.valueOf(result.first.toDouble()),
                LuaValue.valueOf(result.second.toDouble()),
            )
        })

        register("calculateFlatDamageHealing", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.calculateFlatDamageHealing(player)?.let { LuaValue.valueOf(it.toDouble()) }
                ?: LuaValue.NIL
        })

        register("addWeaponExperience", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val experience = args.arg(2).toint()
            val itemId = args.arg(3).optint(0)
            operations.addWeaponExperience(player, experience, itemId)?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("getLivestreamViewersCount", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLivestreamViewersCount(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("getLivestreamViewers", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val state = operations.getLivestreamViewers(player) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                table.set("description", state.description)
                table.set("broadcast", LuaValue.valueOf(state.broadcast))
                table.set("password", state.password)
                table.set("names", pushStringList(state.names))
                table.set("mutes", pushStringList(state.mutes))
                table.set("bans", pushStringList(state.bans))
                table.set("kick", pushStringList(state.kick))
            }
        })

        register("setLivestreamViewers", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            val data = args.arg(2)
            if (!data.istable()) {
                return@luaFunction LuaValue.valueOf(false)
            }
            val overrides = parseLivestreamOverrides(data) ?: return@luaFunction LuaValue.valueOf(false)
            operations.setLivestreamViewers(player, overrides)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("isLivestreamViewer", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.valueOf(false)
            operations.isLivestreamViewer(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(false)
        })

        register("getMapShader", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getMapShader(player)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("setMapShader", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setMapShader(player, args.arg(2).checkjstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        register("addCustomOutfit", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val type = args.arg(2).checkjstring()
            val idOrName = if (args.arg(3).isnumber()) args.arg(3).toint() else args.arg(3).checkjstring()
            operations.addCustomOutfit(player, type, idOrName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        register("removeCustomOutfit", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val type = args.arg(2).checkjstring()
            val idOrName = if (args.arg(3).isnumber()) args.arg(3).toint() else args.arg(3).checkjstring()
            operations.removeCustomOutfit(player, type, idOrName)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    /** Mirrors `Lua::getNumber<uint16_t>(L, 2)` mirrored by `isNumber` name fallback. */
    private fun resolveAchievementId(args: Varargs, operations: PlayerExtraOperations): Int =
        if (args.arg(2).isnumber()) args.arg(2).toint() else operations.achievementIdByName(args.arg(2).checkjstring())

    /** Mirrors `pushStringList` (player_functions.cpp:106): a 1-indexed string table. */
    private fun pushStringList(values: List<String>): LuaTable = LuaTable().also { table ->
        values.forEachIndexed { index, value -> table.set(index + 1, value) }
    }

    /**
     * Parses the `setLivestreamViewers` table with Canary's per-field type checks
     * (player_functions.cpp:5669-5734): a present field of the wrong type returns null,
     * which the caller surfaces as `false`.
     */
    private fun parseLivestreamOverrides(data: LuaValue): LuaLivestreamOverrides? {
        val description = data.get("description")
        if (!description.isnil() && description.type() != LuaValue.TSTRING) {
            return null
        }
        val broadcast = data.get("broadcast")
        if (!broadcast.isnil() && broadcast.type() != LuaValue.TBOOLEAN) {
            return null
        }
        val password = data.get("password")
        if (!password.isnil() && password.type() != LuaValue.TSTRING) {
            return null
        }
        val mutes = data.get("mutes")
        if (!mutes.isnil() && !mutes.istable()) {
            return null
        }
        val bans = data.get("bans")
        if (!bans.isnil() && !bans.istable()) {
            return null
        }
        val kick = data.get("kick")
        if (!kick.isnil() && !kick.istable()) {
            return null
        }
        return LuaLivestreamOverrides(
            description = if (description.isnil()) null else description.tojstring(),
            broadcast = if (broadcast.isnil()) null else broadcast.toboolean(),
            password = if (password.isnil()) null else password.tojstring(),
            mutes = if (mutes.isnil()) null else stringList(mutes),
            bans = if (bans.isnil()) null else stringList(bans),
            kick = if (kick.isnil()) null else stringList(kick),
        )
    }

    /** Mirrors `getStringListField` (player_functions.cpp:60-104). */
    private fun stringList(table: LuaValue): List<String> {
        val values = mutableListOf<String>()
        var index = 1
        while (true) {
            val value = table.get(index)
            if (value.isnil()) {
                break
            }
            values += value.checkjstring()
            index++
        }
        return values
    }
}

/**
 * Real implementation of [PlayerExtraOperations] backed by the Kotlin domain where the
 * systems are ported (Exaltation Forge dust ledger, hazard points, group flags, harmony/
 * virtue/serenity, premium account VIP aliases, KV) and by documented port bookkeeping
 * where Canary state lives in components this port does not wire to `Player` yet.
 *
 * PARITY GAPS (recorded, not invented):
 * - `Player::getIdleTime`/`Player::isOffline` have no Kotlin field; this seam keeps
 *   per-player values in memory (default 0/false) so a set followed by a get is coherent.
 * - `Player::changeName` cannot rename the immutable Kotlin `Creature.name`; the override
 *   is retained in memory and the name-lock/save path is unported.
 * - Exaltation Forge slivers/cores need `Player::getForgeSliversAndCores`'s history scan;
 *   the seam keeps per-player counters (default 0).
 * - Faction, UI exhaustion, bosstiary, store inbox, achievements/badges/titles, store
 *   summary, screenshots, creature icons, animus mastery, concoction/food, the Wheel of
 *   Destiny spell/gem queries and OTCR attached effects are protocol/component sends or
 *   state the Kotlin components do not expose through `Player`; they persist in memory
 *   (empty defaults) rather than inventing gameplay decisions.
 * - `Player::getBossBonus` needs `g_ioBosstiary().calculateLootBonus`; 0 until wired.
 * - `Player::weaponProficiency` has no `Player` accessor yet; the call is acknowledged
 *   without applying experience.
 * - Livestream state is kept per player with a zero viewer count; `g_livestream()`
 *   registration is not wired into this operations object.
 * - Sound sends, forge window sends, market entry, market/UI sends, refresh/screenshot
 *   sends and `reloadData` are protocol writes with no Kotlin client sink; they succeed
 *   without transmitting, matching the client-less C++ behavior.
 *
 * @param vipSystemEnabled `VIP_SYSTEM_ENABLED` config seam (configmanager.cpp:168).
 */
class WorldPlayerExtraOperations(
    private val vipSystemEnabled: Boolean = false,
) : PlayerExtraOperations {
    private val idleTimes = mutableMapOf<Long, Long>()
    private val offlineFlags = mutableSetOf<Long>()
    private val uiExhausted = mutableMapOf<Long, Boolean>()
    private val factions = mutableMapOf<Long, Int>()
    private val bossPoints = mutableMapOf<Long, Long>()
    private val removeBossTimes = mutableMapOf<Long, Int>()
    private val bossKills = mutableMapOf<Long, MutableMap<String, Int>>()
    private val bossLevels = mutableMapOf<Long, MutableMap<String, Int>>()
    private val slivers = mutableMapOf<Long, Long>()
    private val cores = mutableMapOf<Long, Long>()
    private val loyaltyBonuses = mutableMapOf<Long, Int>()
    private val loyaltyTitles = mutableMapOf<Long, String>()
    private val instantSkills = mutableMapOf<Long, MutableMap<String, Boolean>>()
    private val upgradeLevels = mutableMapOf<Long, MutableMap<String, Int>>()
    private val stages = mutableMapOf<Long, MutableMap<String, Int>>()
    private val avatarTicks = mutableMapOf<Long, Long>()
    private val concoctions = mutableMapOf<Long, MutableMap<Int, Int>>()
    private val foods = mutableMapOf<Long, MutableMap<Int, Long>>()
    private val storeInboxes = mutableMapOf<Long, Any>()
    private val unlockedAchievements = mutableMapOf<Long, MutableSet<Int>>()
    private val achievementPoints = mutableMapOf<Long, Int>()
    private val badges = mutableMapOf<Long, MutableSet<Int>>()
    private val titles = mutableMapOf<Long, MutableSet<Int>>()
    private val currentTitles = mutableMapOf<Long, Int>()
    private val mapShaders = mutableMapOf<Long, String>()
    private val customOutfits = mutableMapOf<Long, MutableSet<String>>()
    private val animusMasteries = mutableMapOf<Long, MutableSet<String>>()
    private val livestreamStates = mutableMapOf<Long, LuaLivestreamViewers>()
    private val livestreamViewers = mutableSetOf<Long>()
    private val names = mutableMapOf<Long, String>()

    override fun getIdleTime(player: Any): Long? = playerId(player)?.let { idleTimes[it] ?: 0L }

    override fun getFreeBackpackSlots(player: Any): Int? {
        val target = player as? Player ?: return null
        val backpack = target.getBackpack() ?: return 0
        return (backpack.capacity - backpack.items.size).coerceAtLeast(0)
    }

    override fun isOffline(player: Any): Boolean = playerId(player)?.let { it in offlineFlags } ?: false

    override fun openMarket(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun openForge(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun closeForge(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun addForgeDusts(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.addForgeDusts(amount)
        return true
    }

    override fun removeForgeDusts(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.removeForgeDusts(amount)
        return true
    }

    override fun getForgeDusts(player: Any): Long? = (player as? Player)?.getForgeDusts()

    override fun setForgeDusts(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.setForgeDusts(amount)
        return true
    }

    override fun addForgeDustLevel(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.addForgeDustLevel(amount)
        return true
    }

    override fun removeForgeDustLevel(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        target.removeForgeDustLevel(amount)
        return true
    }

    override fun getForgeDustLevel(player: Any): Long? = (player as? Player)?.getForgeDustLevel()

    override fun getForgeSlivers(player: Any): Long? = playerId(player)?.let { slivers[it] ?: 0L }

    override fun getForgeCores(player: Any): Long? = playerId(player)?.let { cores[it] ?: 0L }

    override fun setFaction(player: Any, factionId: Int): Boolean? {
        val target = player as? Player ?: return null
        factions[target.id] = factionId
        return true
    }

    override fun getFaction(player: Any): Int? = playerId(player)?.let { factions[it] ?: 0 }

    override fun isUIExhausted(player: Any, time: Int): Boolean? =
        playerId(player)?.let { uiExhausted[it] ?: false }

    override fun updateUIExhausted(player: Any): Boolean? {
        val target = player as? Player ?: return null
        uiExhausted[target.id] = true
        return true
    }

    override fun sendBosstiaryCooldownTimer(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun getBosstiaryLevel(player: Any, monsterName: String): Int? =
        playerId(player)?.let { bossLevels[it]?.get(monsterName) }

    override fun getBosstiaryKills(player: Any, monsterName: String): Int? =
        playerId(player)?.let { bossKills[it]?.get(monsterName) }

    override fun addBosstiaryKill(player: Any, monsterName: String, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        val kills = bossKills.getOrPut(target.id) { mutableMapOf() }
        kills[monsterName] = (kills[monsterName] ?: 0) + amount.toInt()
        return true
    }

    override fun setBossPoints(player: Any, points: Long): Boolean? {
        val target = player as? Player ?: return null
        bossPoints[target.id] = points
        return true
    }

    override fun setRemoveBossTime(player: Any, time: Int): Boolean? {
        val target = player as? Player ?: return null
        removeBossTimes[target.id] = time
        return true
    }

    override fun getSlotBossId(player: Any, slotId: Int): Long? = playerId(player)?.let { slotId.toLong() }

    override fun getBossBonus(player: Any, slotId: Int): Long? = playerId(player)?.let { 0L }

    override fun sendSingleSoundEffect(player: Any, soundId: Int, actor: Boolean): Boolean? =
        (player as? Player)?.let { true }

    override fun sendDoubleSoundEffect(
        player: Any,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Boolean,
    ): Boolean? = (player as? Player)?.let { true }

    override fun sendAmbientSoundEffect(player: Any, soundId: Int): Boolean? = (player as? Player)?.let { true }

    override fun sendMusicSoundEffect(player: Any, soundId: Int): Boolean? = (player as? Player)?.let { true }

    override fun changeName(player: Any, newName: String): Boolean? {
        val target = player as? Player ?: return null
        names[target.id] = newName
        return true
    }

    override fun hasGroupFlag(player: Any, flag: Int): Boolean? {
        val target = player as? Player ?: return null
        return target.hasFlag(resolveFlag(flag) ?: return false)
    }

    override fun setGroupFlag(player: Any, flag: Int): Boolean? {
        val target = player as? Player ?: return null
        target.setFlag(resolveFlag(flag) ?: return false)
        return true
    }

    override fun removeGroupFlag(player: Any, flag: Int): Boolean? {
        val target = player as? Player ?: return null
        target.removeFlag(resolveFlag(flag) ?: return false)
        return true
    }

    override fun setHazardSystemPoints(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        target.features.hazard.setHazardSystemPoints(amount)
        return true
    }

    override fun getHazardSystemPoints(player: Any): Int? =
        (player as? Player)?.features?.hazard?.getHazardSystemPoints()

    override fun setLoyaltyBonus(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        loyaltyBonuses[target.id] = amount
        return true
    }

    override fun getLoyaltyBonus(player: Any): Int? = playerId(player)?.let { loyaltyBonuses[it] ?: 0 }

    override fun getLoyaltyPoints(player: Any): Int? = playerId(player)?.let { loyaltyBonuses[it] ?: 0 }

    override fun getLoyaltyTitle(player: Any): String? = playerId(player)?.let { loyaltyTitles[it] ?: "" }

    override fun setLoyaltyTitle(player: Any, name: String): Boolean? {
        val target = player as? Player ?: return null
        loyaltyTitles[target.id] = name
        return true
    }

    override fun instantSkillWOD(player: Any, name: String, value: Boolean?): Boolean? {
        val target = player as? Player ?: return null
        if (value == null) {
            return instantSkills[target.id]?.get(name) ?: false
        }
        instantSkills.getOrPut(target.id) { mutableMapOf() }[name] = value
        return true
    }

    override fun upgradeSpellsWOD(player: Any, name: String?, add: Boolean?): Any? {
        val target = player as? Player ?: return null
        if (name == null) {
            upgradeLevels.remove(target.id)
            return true
        }
        val levels = upgradeLevels.getOrPut(target.id) { mutableMapOf() }
        if (add == null) {
            return levels[name] ?: 0
        }
        levels[name] = ((levels[name] ?: 0) + if (add) 1 else -1).coerceAtLeast(0)
        return true
    }

    override fun revelationStageWOD(player: Any, name: String?, stage: Int?): Any? {
        val target = player as? Player ?: return null
        if (name == null) {
            stages.remove(target.id)
            return true
        }
        val values = stages.getOrPut(target.id) { mutableMapOf() }
        if (stage == null) {
            return values[name] ?: 0
        }
        values[name] = stage
        return true
    }

    override fun reloadData(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? = (player as? Player)?.let { true }

    override fun avatarTimer(player: Any, value: Long?): Any? {
        val target = player as? Player ?: return null
        if (value == null) {
            return avatarTicks[target.id] ?: 0L
        }
        avatarTicks[target.id] = value
        return true
    }

    /**
     * `Wheel::getSpellAdditionalArea` reads the wheel gem/spell tables; the Kotlin wheel
     * does not expose that query, so it answers `false` like an unconfigured wheel.
     */
    override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? =
        (player as? Player)?.let { false }

    /** See [getWheelSpellAdditionalArea]; Canary's additional target defaults to 0. */
    override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Long? =
        (player as? Player)?.let { 0L }

    /** See [getWheelSpellAdditionalArea]; Canary's additional duration defaults to 0. */
    override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Long? =
        (player as? Player)?.let { 0L }

    /**
     * `Wheel::unlockScroll` needs the wheel scroll registry; the Kotlin wheel keeps no
     * scroll ledger, so the unlock is not granted.
     */
    override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? = (player as? Player)?.let { false }

    override fun updateConcoction(player: Any, itemId: Int, timeLeft: Int): Boolean? {
        val target = player as? Player ?: return null
        concoctions.getOrPut(target.id) { mutableMapOf() }[itemId] = timeLeft
        return true
    }

    override fun updateFood(player: Any, itemId: Int, timeLeft: Long): Boolean? {
        val target = player as? Player ?: return null
        foods.getOrPut(target.id) { mutableMapOf() }[itemId] = timeLeft
        return true
    }

    override fun clearSpellCooldowns(player: Any, spenders: Boolean, builders: Boolean, ticks: Int): Boolean? =
        (player as? Player)?.let { true }

    override fun isVip(player: Any): Boolean? {
        val target = player as? Player ?: return null
        if (!vipSystemEnabled) {
            return false
        }
        val account = target.account ?: return false
        return account.getPremiumRemainingDays() > 0 || account.getPremiumLastDay() > System.currentTimeMillis() / 1000
    }

    override fun getVipDays(player: Any): Int? = (player as? Player)?.account?.getPremiumRemainingDays()

    override fun getVipTime(player: Any): Long? = (player as? Player)?.account?.getPremiumLastDay()

    override fun getKv(player: Any): Any? = (player as? Player)?.kv

    override fun getStoreInbox(player: Any): Any? = playerId(player)?.let { storeInboxes[it] }

    override fun achievementIdByName(name: String): Int = 0

    override fun hasAchievement(player: Any, achievementId: Int): Boolean? {
        val target = player as? Player ?: return null
        return achievementId in (unlockedAchievements[target.id] ?: emptySet())
    }

    override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? {
        val target = player as? Player ?: return null
        unlockedAchievements.getOrPut(target.id) { mutableSetOf() } += achievementId
        return true
    }

    override fun removeAchievement(player: Any, achievementId: Int): Boolean? {
        val target = player as? Player ?: return null
        return unlockedAchievements.getOrPut(target.id) { mutableSetOf() }.remove(achievementId)
    }

    override fun getAchievementPoints(player: Any): Int? = playerId(player)?.let { achievementPoints[it] ?: 0 }

    override fun addAchievementPoints(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        achievementPoints[target.id] = (achievementPoints[target.id] ?: 0) + amount
        return true
    }

    override fun removeAchievementPoints(player: Any, amount: Int): Boolean? {
        val target = player as? Player ?: return null
        achievementPoints[target.id] = ((achievementPoints[target.id] ?: 0) - amount).coerceAtLeast(0)
        return true
    }

    override fun addBadge(player: Any, badgeId: Int): Boolean? {
        val target = player as? Player ?: return null
        badges.getOrPut(target.id) { mutableSetOf() } += badgeId
        return true
    }

    override fun addTitle(player: Any, titleId: Int): Boolean? {
        val target = player as? Player ?: return null
        titles.getOrPut(target.id) { mutableSetOf() } += titleId
        return true
    }

    override fun getTitles(player: Any): List<LuaPlayerTitle>? = (player as? Player)?.let { emptyList() }

    override fun setCurrentTitle(player: Any, titleId: Int): Boolean? {
        val target = player as? Player ?: return null
        if (titleId == 0) {
            return false
        }
        currentTitles[target.id] = titleId
        return true
    }

    override fun createTransactionSummary(player: Any, type: Int, amount: Int, id: String): Boolean? {
        if (player !is Player) {
            return null
        }
        if (type == 0) {
            return false
        }
        targetSummary(type, amount, id)
        return true
    }

    override fun takeScreenshot(
        player: Any,
        type: Int,
        skillId: Int,
        skillLevel: Int,
        achievementName: String,
        raceId: Int,
        bestiaryStep: Int,
    ): Boolean? = (player as? Player)?.let { true }

    override fun sendIconBakragore(player: Any, iconType: Int): Boolean? = (player as? Player)?.let { true }

    override fun removeIconBakragore(player: Any, iconType: Int): Boolean? = (player as? Player)?.let { true }

    override fun sendCreatureAppear(player: Any, isLogin: Boolean): Boolean? = (player as? Player)?.let { true }

    override fun setRaceIconOverlay(player: Any, raceId: Int, icon: Int, enabled: Boolean): Boolean? =
        (player as? Player)?.let { true }

    override fun clearRaceIconOverlays(player: Any, icon: Int): Boolean? = (player as? Player)?.let { true }

    override fun refreshVisibleCreatureIcons(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun addAnimusMastery(player: Any, monsterType: String): Boolean? {
        val target = player as? Player ?: return null
        animusMasteries.getOrPut(target.id) { mutableSetOf() } += monsterType
        return true
    }

    override fun removeAnimusMastery(player: Any, monsterType: String): Boolean? {
        val target = player as? Player ?: return null
        animusMasteries.getOrPut(target.id) { mutableSetOf() }.remove(monsterType)
        return true
    }

    override fun hasAnimusMastery(player: Any, monsterType: String): Boolean? {
        val target = player as? Player ?: return null
        return monsterType in (animusMasteries[target.id] ?: emptySet())
    }

    override fun setSerene(player: Any, set: Boolean, ticks: Int): Boolean? {
        val target = player as? Player ?: return null
        target.setSerene(set, ticks)
        return true
    }

    override fun getVirtue(player: Any): Int? = (player as? Player)?.getVirtue()?.value

    override fun setVirtue(player: Any, virtue: Int): Boolean? {
        val target = player as? Player ?: return null
        val resolved = Virtue_t.entries.firstOrNull { it.value == virtue } ?: return false
        target.setVirtue(resolved)
        return true
    }

    override fun fillHarmony(player: Any): Boolean? {
        val target = player as? Player ?: return null
        target.fillHarmony()
        return true
    }

    override fun getHarmony(player: Any): Int? = (player as? Player)?.getHarmony()

    override fun getHarmonyDamage(player: Any, baseMin: Int, baseMax: Int): Pair<Long, Long>? =
        (player as? Player)?.getHarmonyDamage(baseMin.toDouble(), baseMax.toDouble())

    /** No `Player::calculateFlatDamageHealing` accessor is ported yet; 0 like an empty buff set. */
    override fun calculateFlatDamageHealing(player: Any): Long? = (player as? Player)?.let { 0L }

    /** No `Player::weaponProficiency` accessor is ported yet; the call is acknowledged. */
    override fun addWeaponExperience(player: Any, experience: Int, itemId: Int): Boolean? =
        (player as? Player)?.let { experience == 0 || itemId >= 0 }

    override fun getLivestreamViewersCount(player: Any): Int? = playerId(player)?.let { 0 }

    override fun getLivestreamViewers(player: Any): LuaLivestreamViewers? = playerId(player)?.let {
        livestreamStates[it] ?: EMPTY_LIVESTREAM
    }

    override fun setLivestreamViewers(player: Any, viewers: LuaLivestreamOverrides): Boolean? {
        val target = player as? Player ?: return null
        val current = livestreamStates[target.id] ?: EMPTY_LIVESTREAM
        livestreamStates[target.id] = current.copy(
            description = viewers.description ?: current.description,
            broadcast = viewers.broadcast ?: current.broadcast,
            password = viewers.password ?: current.password,
            mutes = viewers.mutes ?: current.mutes,
            bans = viewers.bans ?: current.bans,
            kick = viewers.kick ?: current.kick,
        )
        return true
    }

    override fun isLivestreamViewer(player: Any): Boolean? = playerId(player)?.let { it in livestreamViewers }

    override fun getMapShader(player: Any): String? = playerId(player)?.let { mapShaders[it] ?: "" }

    override fun setMapShader(player: Any, shaderName: String): Boolean? {
        val target = player as? Player ?: return null
        mapShaders[target.id] = shaderName
        return true
    }

    override fun addCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? {
        val target = player as? Player ?: return null
        customOutfits.getOrPut(target.id) { mutableSetOf() } += "$type:$idOrName"
        return true
    }

    override fun removeCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? {
        val target = player as? Player ?: return null
        return customOutfits.getOrPut(target.id) { mutableSetOf() }.remove("$type:$idOrName")
    }

    private fun playerId(player: Any): Long? = (player as? Player)?.id

    private fun resolveFlag(flag: Int): PlayerFlags_t? =
        PlayerFlags_t.entries.firstOrNull { it.value == flag }

    private fun targetSummary(type: Int, amount: Int, id: String) {
        // `PlayerCyclopedia::updateStoreSummary` requires the cyclopedia component owned by
        // PlayerFeatures; the summary is acknowledged without persisting a record.
    }

    private companion object {
        val EMPTY_LIVESTREAM = LuaLivestreamViewers(
            description = "",
            broadcast = false,
            password = "",
            names = emptyList(),
            mutes = emptyList(),
            bans = emptyList(),
            kick = emptyList(),
        )
    }
}

/**
 * Default [PlayerExtraOperations] used before [WorldLuaOperations] wires the real seam.
 * Every call fails loudly, mirroring [UnwiredPlayerOperations].
 */
internal object UnwiredPlayerExtraOperations : PlayerExtraOperations {
    override fun getIdleTime(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getIdleTime seam is not wired")

    override fun getFreeBackpackSlots(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getFreeBackpackSlots seam is not wired")

    override fun isOffline(player: Any): Boolean =
        throw NotImplementedError("PlayerFunctionsExtra.isOffline seam is not wired")

    override fun openMarket(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.openMarket seam is not wired")

    override fun openForge(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.openForge seam is not wired")

    override fun closeForge(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.closeForge seam is not wired")

    override fun addForgeDusts(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addForgeDusts seam is not wired")

    override fun removeForgeDusts(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeForgeDusts seam is not wired")

    override fun getForgeDusts(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getForgeDusts seam is not wired")

    override fun setForgeDusts(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setForgeDusts seam is not wired")

    override fun addForgeDustLevel(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addForgeDustLevel seam is not wired")

    override fun removeForgeDustLevel(player: Any, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeForgeDustLevel seam is not wired")

    override fun getForgeDustLevel(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getForgeDustLevel seam is not wired")

    override fun getForgeSlivers(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getForgeSlivers seam is not wired")

    override fun getForgeCores(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getForgeCores seam is not wired")

    override fun setFaction(player: Any, factionId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setFaction seam is not wired")

    override fun getFaction(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getFaction seam is not wired")

    override fun isUIExhausted(player: Any, time: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.isUIExhausted seam is not wired")

    override fun updateUIExhausted(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.updateUIExhausted seam is not wired")

    override fun sendBosstiaryCooldownTimer(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendBosstiaryCooldownTimer seam is not wired")

    override fun getBosstiaryLevel(player: Any, monsterName: String): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getBosstiaryLevel seam is not wired")

    override fun getBosstiaryKills(player: Any, monsterName: String): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getBosstiaryKills seam is not wired")

    override fun addBosstiaryKill(player: Any, monsterName: String, amount: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addBosstiaryKill seam is not wired")

    override fun setBossPoints(player: Any, points: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setBossPoints seam is not wired")

    override fun setRemoveBossTime(player: Any, time: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setRemoveBossTime seam is not wired")

    override fun getSlotBossId(player: Any, slotId: Int): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getSlotBossId seam is not wired")

    override fun getBossBonus(player: Any, slotId: Int): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getBossBonus seam is not wired")

    override fun sendSingleSoundEffect(player: Any, soundId: Int, actor: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendSingleSoundEffect seam is not wired")

    override fun sendDoubleSoundEffect(
        player: Any,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Boolean,
    ): Boolean? = throw NotImplementedError("PlayerFunctionsExtra.sendDoubleSoundEffect seam is not wired")

    override fun sendAmbientSoundEffect(player: Any, soundId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendAmbientSoundEffect seam is not wired")

    override fun sendMusicSoundEffect(player: Any, soundId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendMusicSoundEffect seam is not wired")

    override fun changeName(player: Any, newName: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.changeName seam is not wired")

    override fun hasGroupFlag(player: Any, flag: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.hasGroupFlag seam is not wired")

    override fun setGroupFlag(player: Any, flag: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setGroupFlag seam is not wired")

    override fun removeGroupFlag(player: Any, flag: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeGroupFlag seam is not wired")

    override fun setHazardSystemPoints(player: Any, amount: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setHazardSystemPoints seam is not wired")

    override fun getHazardSystemPoints(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getHazardSystemPoints seam is not wired")

    override fun setLoyaltyBonus(player: Any, amount: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setLoyaltyBonus seam is not wired")

    override fun getLoyaltyBonus(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getLoyaltyBonus seam is not wired")

    override fun getLoyaltyPoints(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getLoyaltyPoints seam is not wired")

    override fun getLoyaltyTitle(player: Any): String? =
        throw NotImplementedError("PlayerFunctionsExtra.getLoyaltyTitle seam is not wired")

    override fun setLoyaltyTitle(player: Any, name: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setLoyaltyTitle seam is not wired")

    override fun instantSkillWOD(player: Any, name: String, value: Boolean?): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.instantSkillWOD seam is not wired")

    override fun upgradeSpellsWOD(player: Any, name: String?, add: Boolean?): Any? =
        throw NotImplementedError("PlayerFunctionsExtra.upgradeSpellsWOD seam is not wired")

    override fun revelationStageWOD(player: Any, name: String?, stage: Int?): Any? =
        throw NotImplementedError("PlayerFunctionsExtra.revelationStageWOD seam is not wired")

    override fun reloadData(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.reloadData seam is not wired")

    override fun onThinkWheelOfDestiny(player: Any, force: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.onThinkWheelOfDestiny seam is not wired")

    override fun avatarTimer(player: Any, value: Long?): Any? =
        throw NotImplementedError("PlayerFunctionsExtra.avatarTimer seam is not wired")

    override fun getWheelSpellAdditionalArea(player: Any, spellName: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.getWheelSpellAdditionalArea seam is not wired")

    override fun getWheelSpellAdditionalTarget(player: Any, spellName: String): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getWheelSpellAdditionalTarget seam is not wired")

    override fun getWheelSpellAdditionalDuration(player: Any, spellName: String): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getWheelSpellAdditionalDuration seam is not wired")

    override fun wheelUnlockScroll(player: Any, scrollName: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.wheelUnlockScroll seam is not wired")

    override fun updateConcoction(player: Any, itemId: Int, timeLeft: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.updateConcoction seam is not wired")

    override fun updateFood(player: Any, itemId: Int, timeLeft: Long): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.updateFood seam is not wired")

    override fun clearSpellCooldowns(
        player: Any,
        spenders: Boolean,
        builders: Boolean,
        ticks: Int,
    ): Boolean? = throw NotImplementedError("PlayerFunctionsExtra.clearSpellCooldowns seam is not wired")

    override fun isVip(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.isVip seam is not wired")

    override fun getVipDays(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getVipDays seam is not wired")

    override fun getVipTime(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.getVipTime seam is not wired")

    override fun getKv(player: Any): Any? =
        throw NotImplementedError("PlayerFunctionsExtra.getKv seam is not wired")

    override fun getStoreInbox(player: Any): Any? =
        throw NotImplementedError("PlayerFunctionsExtra.getStoreInbox seam is not wired")

    override fun achievementIdByName(name: String): Int =
        throw NotImplementedError("PlayerFunctionsExtra.achievementIdByName seam is not wired")

    override fun hasAchievement(player: Any, achievementId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.hasAchievement seam is not wired")

    override fun addAchievement(player: Any, achievementId: Int, sendMessage: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addAchievement seam is not wired")

    override fun removeAchievement(player: Any, achievementId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeAchievement seam is not wired")

    override fun getAchievementPoints(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getAchievementPoints seam is not wired")

    override fun addAchievementPoints(player: Any, amount: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addAchievementPoints seam is not wired")

    override fun removeAchievementPoints(player: Any, amount: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeAchievementPoints seam is not wired")

    override fun addBadge(player: Any, badgeId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addBadge seam is not wired")

    override fun addTitle(player: Any, titleId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addTitle seam is not wired")

    override fun getTitles(player: Any): List<LuaPlayerTitle>? =
        throw NotImplementedError("PlayerFunctionsExtra.getTitles seam is not wired")

    override fun setCurrentTitle(player: Any, titleId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setCurrentTitle seam is not wired")

    override fun createTransactionSummary(player: Any, type: Int, amount: Int, id: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.createTransactionSummary seam is not wired")

    override fun takeScreenshot(
        player: Any,
        type: Int,
        skillId: Int,
        skillLevel: Int,
        achievementName: String,
        raceId: Int,
        bestiaryStep: Int,
    ): Boolean? = throw NotImplementedError("PlayerFunctionsExtra.takeScreenshot seam is not wired")

    override fun sendIconBakragore(player: Any, iconType: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendIconBakragore seam is not wired")

    override fun removeIconBakragore(player: Any, iconType: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeIconBakragore seam is not wired")

    override fun sendCreatureAppear(player: Any, isLogin: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.sendCreatureAppear seam is not wired")

    override fun setRaceIconOverlay(player: Any, raceId: Int, icon: Int, enabled: Boolean): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setRaceIconOverlay seam is not wired")

    override fun clearRaceIconOverlays(player: Any, icon: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.clearRaceIconOverlays seam is not wired")

    override fun refreshVisibleCreatureIcons(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.refreshVisibleCreatureIcons seam is not wired")

    override fun addAnimusMastery(player: Any, monsterType: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addAnimusMastery seam is not wired")

    override fun removeAnimusMastery(player: Any, monsterType: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeAnimusMastery seam is not wired")

    override fun hasAnimusMastery(player: Any, monsterType: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.hasAnimusMastery seam is not wired")

    override fun setSerene(player: Any, set: Boolean, ticks: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setSerene seam is not wired")

    override fun getVirtue(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getVirtue seam is not wired")

    override fun setVirtue(player: Any, virtue: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setVirtue seam is not wired")

    override fun fillHarmony(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.fillHarmony seam is not wired")

    override fun getHarmony(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getHarmony seam is not wired")

    override fun getHarmonyDamage(player: Any, baseMin: Int, baseMax: Int): Pair<Long, Long>? =
        throw NotImplementedError("PlayerFunctionsExtra.getHarmonyDamage seam is not wired")

    override fun calculateFlatDamageHealing(player: Any): Long? =
        throw NotImplementedError("PlayerFunctionsExtra.calculateFlatDamageHealing seam is not wired")

    override fun addWeaponExperience(player: Any, experience: Int, itemId: Int): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addWeaponExperience seam is not wired")

    override fun getLivestreamViewersCount(player: Any): Int? =
        throw NotImplementedError("PlayerFunctionsExtra.getLivestreamViewersCount seam is not wired")

    override fun getLivestreamViewers(player: Any): LuaLivestreamViewers? =
        throw NotImplementedError("PlayerFunctionsExtra.getLivestreamViewers seam is not wired")

    override fun setLivestreamViewers(player: Any, viewers: LuaLivestreamOverrides): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setLivestreamViewers seam is not wired")

    override fun isLivestreamViewer(player: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.isLivestreamViewer seam is not wired")

    override fun getMapShader(player: Any): String? =
        throw NotImplementedError("PlayerFunctionsExtra.getMapShader seam is not wired")

    override fun setMapShader(player: Any, shaderName: String): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.setMapShader seam is not wired")

    override fun addCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.addCustomOutfit seam is not wired")

    override fun removeCustomOutfit(player: Any, type: String, idOrName: Any): Boolean? =
        throw NotImplementedError("PlayerFunctionsExtra.removeCustomOutfit seam is not wired")
}
