package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.account.CoinType
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.players.pvp.Kill
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.house.House
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Canary spell payload for `player:getInstantSpells`. Mirrors the table fields
 * pushed by `Lua::pushInstantSpell` (lua_functions_loader.cpp:690-705).
 */
data class LuaSpell(
    val name: String,
    val words: String,
    val level: Int,
    val magicLevel: Int,
    val mana: Int,
    val manaPercent: Int,
)

/**
 * Engine seam for the social/party/guild, storage-adjacent, modal/UI, house,
 * account/premium, kills/PvP and vocation/spell `Player` primitives in
 * src/lua/functions/creatures/player/player_functions.cpp.
 *
 * Nullable results mirror Canary's `lua_pushnil` guards. `false` and `null` are
 * distinct exactly as in the C++ primitives: e.g. `setGuildLevel` pushes
 * `false` for an unknown rank but `nil` without a guild, and `setGroup` pushes
 * `false` for a missing group argument before it ever looks at the receiver.
 */
interface PlayerSocialOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    fun getParty(player: Any): Any?

    fun getGuild(player: Any): Any?

    /** `player:setGuild(guild)`; a null/non-Guild argument clears membership like Canary. */
    fun setGuild(player: Any, guild: Any?): Boolean?

    fun getGuildLevel(player: Any): Int?

    fun setGuildLevel(player: Any, level: Int): Boolean?

    fun getGuildNick(player: Any): String?

    fun setGuildNick(player: Any, nick: String): Boolean?

    fun showTextDialog(player: Any, itemId: Int, text: String, canWrite: Boolean, length: Int): Boolean?

    fun sendChannelMessage(player: Any, author: String, text: String, type: Int, channelId: Int): Boolean?

    fun sendPrivateMessage(player: Any, speaker: Any?, text: String, type: Int): Boolean?

    fun channelSay(player: Any, speaker: Any?, type: Int, text: String, channelId: Int): Boolean?

    fun openChannel(player: Any, channelId: Int): Boolean?

    fun popupFYI(player: Any, message: String): Boolean?

    fun sendTutorial(player: Any, tutorialId: Int): Boolean?

    fun sendInventory(player: Any): Boolean?

    fun sendLootStats(player: Any, item: Any, count: Int): Boolean?

    fun updateSupplyTracker(player: Any, item: Any): Boolean?

    fun updateKillTracker(player: Any, creature: Any, corpse: Any): Boolean?

    fun getHouse(player: Any): Any?

    fun sendHouseWindow(player: Any, house: Any, listId: Int): Boolean?

    fun setEditHouse(player: Any, house: Any, listId: Int): Boolean?

    fun getIp(player: Any): Long?

    /** `Player::getAccountId()`; nil when the id is 0, like Canary's guard. */
    fun getAccountId(player: Any): Long?

    fun getLastLoginSaved(player: Any): Long?

    fun getLastLogout(player: Any): Long?

    fun getAccountType(player: Any): Int?

    fun setAccountType(player: Any, accountType: Int): Boolean?

    fun getGroup(player: Any): Any?

    fun setGroup(player: Any, group: Any?): Boolean?

    /** Null models Canary's silent `return 1` paths (already at max, <= 0 change, save error). */
    fun addPremiumDays(player: Any, days: Int): Boolean?

    fun removePremiumDays(player: Any, days: Int): Boolean?

    fun getTibiaCoins(player: Any): Int?

    fun addTibiaCoins(player: Any, coins: Int): Boolean?

    fun removeTibiaCoins(player: Any, coins: Int): Boolean?

    fun getTransferableCoins(player: Any): Int?

    fun addTransferableCoins(player: Any, coins: Int): Boolean?

    fun removeTransferableCoins(player: Any, coins: Int): Boolean?

    fun removeTransferableAndTibiaCoins(player: Any, coins: Int): Boolean?

    fun getKills(player: Any): List<Kill>?

    fun setKills(player: Any, kills: List<Kill>): Boolean?

    fun getSkullTime(player: Any): Long?

    fun setSkullTime(player: Any, ticks: Long): Boolean?

    fun getDeathPenalty(player: Any): Int?

    fun getPvpMode(player: Any): Int?

    fun hasAttacked(player: Any, target: Any?): Boolean?

    /** Number, name and Vocation userdata argument forms are forwarded unresolved. */
    fun setVocation(player: Any, vocation: Any?): Boolean?

    fun isPromoted(player: Any): Boolean?

    fun canLearnSpell(player: Any, spellName: String): Boolean?

    fun learnSpell(player: Any, spellName: String): Boolean?

    fun forgetSpell(player: Any, spellName: String): Boolean?

    fun hasLearnedSpell(player: Any, spellName: String): Boolean?

    fun getInstantSpells(player: Any): List<LuaSpell>?

    /** Expects an `InstantSpell` userdata; tables/non-userdata push nil like Canary. */
    fun canCast(player: Any, spell: Any?): Boolean?
}

/**
 * Registers the Canary `Player` social/UI/house/account/PvP/spell methods:
 *
 *   player:getParty()
 *   player:getGuild()
 *   player:setGuild(guild)
 *   player:getGuildLevel()
 *   player:setGuildLevel(level)
 *   player:getGuildNick()
 *   player:setGuildNick(nick)
 *   player:showTextDialog(id or name or item[, text[, canWrite[, length]]])
 *   player:sendChannelMessage(author, text, type, channelId)
 *   player:sendPrivateMessage(speaker, text[, type])
 *   player:channelSay(speaker, type, text, channelId)
 *   player:openChannel(channelId)
 *   player:popupFYI(message)
 *   player:sendTutorial(tutorialId)
 *   player:sendInventory()
 *   player:sendLootStats(item, count)
 *   player:updateSupplyTracker(item)
 *   player:updateKillTracker(creature, corpse)
 *   player:getHouse()
 *   player:sendHouseWindow(house, listId)
 *   player:setEditHouse(house, listId)
 *   player:getIp()
 *   player:getAccountId()
 *   player:getLastLoginSaved()
 *   player:getLastLogout()
 *   player:getAccountType()
 *   player:setAccountType(accountType)
 *   player:getGroup()
 *   player:setGroup(group)
 *   player:addPremiumDays(days)
 *   player:removePremiumDays(days)
 *   player:getTibiaCoins()
 *   player:addTibiaCoins(coins)
 *   player:removeTibiaCoins(coins)
 *   player:getTransferableCoins()
 *   player:addTransferableCoins(coins)
 *   player:removeTransferableCoins(coins)
 *   player:removeTransferableAndTibiaCoins(coins)
 *   player:getKills()
 *   player:setKills(kills)
 *   player:getSkullTime()
 *   player:setSkullTime(skullTime)
 *   player:getDeathPenalty()
 *   player:getPvpMode()
 *   player:hasAttacked(target)
 *   player:setVocation(id or name or userdata)
 *   player:isPromoted()
 *   player:canLearnSpell(spellName)
 *   player:learnSpell(spellName)
 *   player:forgetSpell(spellName)
 *   player:hasLearnedSpell(spellName)
 *   player:getInstantSpells()
 *   player:canCast(spell)
 *
 * PARITY GAPS (recorded, not invented): `getStorageValue`/`setStorageValue` and
 * `sendTextMessage` are already registered by [PlayerFunctions] and are not
 * re-registered here, so this file never shadows the storage seam. Canary has no
 * `Player:getStorageValueByKey`, `Player:setGuildRank`/`getGuildRank`,
 * `Player:sendCancelMessage`, `Player:sendModalWindow` (modal windows are the
 * separate `ModalWindow` class), `Player:isPremium`, `Player:setPremiumEndsAt`,
 * `Player:getHouseAccessLevel` or `Player:getDeaths` Lua method in this revision.
 */
object PlayerSocialFunctions {
    fun install(env: LuaEnvironment, operations: PlayerSocialOperations = WorldPlayerSocialOperations()) {
        val registry = env.userdata
        val register: (String, LuaValue) -> Unit = { name, fn ->
            registry.registerMethod(PlayerFunctions.PLAYER_TYPE, name, fn)
        }

        register("getParty", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getParty(player)?.let { registry.wrap("Party", it) } ?: LuaValue.NIL
        })

        register("getGuild", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getGuild(player)?.let { registry.wrap("Guild", it) } ?: LuaValue.NIL
        })

        register("setGuild", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setGuild(player, retrieveUserdata(args, 2)).toLuaResult()
        })

        register("getGuildLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getGuildLevel(player).toLuaResult()
        })

        register("setGuildLevel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setGuildLevel(player, args.arg(2).optint(0)).toLuaResult()
        })

        register("getGuildNick", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getGuildNick(player).toLuaResult()
        })

        register("setGuildNick", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setGuildNick(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("showTextDialog", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemArg = args.arg(2)
            val itemId = when {
                itemArg.isnumber() -> itemArg.toint()
                itemArg.isstring() -> operations.itemIdByName(itemArg.checkjstring())
                itemArg.isuserdata() ->
                    (itemArg.touserdata() as? Item)?.id
                        ?: return@luaFunction LuaValue.valueOf(false)
                else -> return@luaFunction LuaValue.valueOf(false)
            }
            if (itemId == 0) {
                return@luaFunction LuaValue.valueOf(false)
            }
            val text = args.arg(3).optjstring("")
            val canWrite = args.arg(4).optboolean(false)
            var length = args.arg(5).optint(-1)
            if (text.isNotEmpty()) {
                length = maxOf(text.length, length)
            }
            operations.showTextDialog(player, itemId, text, canWrite, length).toLuaResult()
        })

        register("sendChannelMessage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendChannelMessage(
                player,
                args.arg(2).optjstring(""),
                args.arg(3).optjstring(""),
                args.arg(4).optint(0),
                args.arg(5).optint(0),
            ).toLuaResult()
        })

        register("sendPrivateMessage", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendPrivateMessage(
                player,
                retrieveUserdata(args, 2),
                args.arg(3).optjstring(""),
                args.arg(4).optint(SpeakClasses.TALKTYPE_PRIVATE_FROM.value),
            ).toLuaResult()
        })

        register("channelSay", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.channelSay(
                player,
                retrieveUserdata(args, 2),
                args.arg(3).optint(0),
                args.arg(4).optjstring(""),
                args.arg(5).optint(0),
            ).toLuaResult()
        })

        register("openChannel", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.openChannel(player, args.arg(2).optint(0)).toLuaResult()
        })

        register("popupFYI", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.popupFYI(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("sendTutorial", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendTutorial(player, args.arg(2).optint(0)).toLuaResult()
        })

        register("sendInventory", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.sendInventory(player).toLuaResult()
        })

        register("sendLootStats", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(3).optint(0) and 0xFF
            if (count == 0) {
                return@luaFunction LuaValue.NIL
            }
            operations.sendLootStats(player, item, count).toLuaResult()
        })

        register("updateSupplyTracker", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val item = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.updateSupplyTracker(player, item).toLuaResult()
        })

        register("updateKillTracker", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val creature = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            val corpse = retrieveUserdata(args, 3) ?: return@luaFunction LuaValue.NIL
            operations.updateKillTracker(player, creature, corpse).toLuaResult()
        })

        register("getHouse", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getHouse(player)?.let { registry.wrap("House", it) } ?: LuaValue.NIL
        })

        register("sendHouseWindow", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val house = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.sendHouseWindow(player, house, args.arg(3).optint(0)).toLuaResult()
        })

        register("setEditHouse", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val house = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.NIL
            operations.setEditHouse(player, house, args.arg(3).optint(0)).toLuaResult()
        })

        register("getIp", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getIp(player).toLuaResult()
        })

        register("getAccountId", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getAccountId(player).toLuaResult()
        })

        register("getLastLoginSaved", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLastLoginSaved(player).toLuaResult()
        })

        register("getLastLogout", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getLastLogout(player).toLuaResult()
        })

        register("getAccountType", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getAccountType(player).toLuaResult()
        })

        register("setAccountType", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setAccountType(player, args.arg(2).optint(0)).toLuaResult()
        })

        register("getGroup", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getGroup(player)?.let { registry.wrap("Group", it) } ?: LuaValue.NIL
        })

        register("setGroup", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setGroup(player, retrieveUserdata(args, 2)).toLuaResult()
        })

        register("addPremiumDays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addPremiumDays(player, args.arg(2).optint(0) and 0xFFFF).toLuaResult()
        })

        register("removePremiumDays", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removePremiumDays(player, args.arg(2).optint(0) and 0xFFFF).toLuaResult()
        })

        register("getTibiaCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTibiaCoins(player).toLuaResult()
        })

        register("addTibiaCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addTibiaCoins(player, args.arg(2).optint(0).coerceAtLeast(0)).toLuaResult()
        })

        register("removeTibiaCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeTibiaCoins(player, args.arg(2).optint(0).coerceAtLeast(0)).toLuaResult()
        })

        register("getTransferableCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTransferableCoins(player).toLuaResult()
        })

        register("addTransferableCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addTransferableCoins(player, args.arg(2).optint(0).coerceAtLeast(0)).toLuaResult()
        })

        register("removeTransferableCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeTransferableCoins(player, args.arg(2).optint(0).coerceAtLeast(0)).toLuaResult()
        })

        register("removeTransferableAndTibiaCoins", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.removeTransferableAndTibiaCoins(player, args.arg(2).optint(0).coerceAtLeast(0)).toLuaResult()
        })

        register("getKills", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val kills = operations.getKills(player) ?: return@luaFunction LuaValue.NIL
            val table = LuaTable()
            kills.forEachIndexed { index, kill ->
                val entry = LuaTable()
                entry.set(1, LuaValue.valueOf(kill.target.toDouble()))
                entry.set(2, LuaValue.valueOf(kill.time.toDouble()))
                entry.set(3, LuaValue.valueOf(kill.unavenged))
                table.set(index + 1, entry)
            }
            table
        })

        register("setKills", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val table = args.arg(2).checktable()
            val kills = mutableListOf<Kill>()
            var key: LuaValue = LuaValue.NIL
            while (true) {
                val pair = table.next(key)
                key = pair.arg1()
                if (key.isnil()) {
                    break
                }
                val entry = pair.arg(2).checktable()
                kills.add(
                    Kill(
                        target = entry.get(1).checklong(),
                        time = entry.get(2).checklong(),
                        unavenged = entry.get(3).toboolean(),
                    ),
                )
            }
            operations.setKills(player, kills).toLuaResult()
        })

        register("getSkullTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getSkullTime(player).toLuaResult()
        })

        register("setSkullTime", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setSkullTime(player, args.arg(2).optlong(0)).toLuaResult()
        })

        register("getDeathPenalty", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getDeathPenalty(player).toLuaResult()
        })

        register("getPvpMode", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getPvpMode(player).toLuaResult()
        })

        register("hasAttacked", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.hasAttacked(player, retrieveUserdata(args, 2)).toLuaResult()
        })

        register("setVocation", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val vocationArg = args.arg(2)
            val vocation = when {
                vocationArg.isnumber() -> vocationArg.toint()
                vocationArg.isstring() -> vocationArg.checkjstring()
                vocationArg.isuserdata() -> vocationArg.touserdata()
                else -> null
            }
            operations.setVocation(player, vocation).toLuaResult()
        })

        register("isPromoted", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.isPromoted(player).toLuaResult()
        })

        register("canLearnSpell", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.canLearnSpell(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("learnSpell", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.learnSpell(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("forgetSpell", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.forgetSpell(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("hasLearnedSpell", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.hasLearnedSpell(player, args.arg(2).optjstring("")).toLuaResult()
        })

        register("getInstantSpells", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spells = operations.getInstantSpells(player) ?: return@luaFunction LuaValue.NIL
            val table = LuaTable()
            spells.forEachIndexed { index, spell -> table.set(index + 1, pushLuaSpell(env, spell)) }
            table
        })

        register("canCast", luaFunction { args ->
            val player = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val spellArg = args.arg(2)
            if (!spellArg.isuserdata()) {
                return@luaFunction LuaValue.NIL
            }
            operations.canCast(player, spellArg.touserdata()).toLuaResult()
        })
    }
}

/**
 * Mirrors `Lua::pushInstantSpell` (lua_functions_loader.cpp:690-705): a plain
 * table with the spell fields and the `Spell` metatable, not a userdata.
 */
private fun pushLuaSpell(env: LuaEnvironment, spell: LuaSpell): LuaValue {
    val table = LuaTable()
    table.set("name", LuaValue.valueOf(spell.name))
    table.set("words", LuaValue.valueOf(spell.words))
    table.set("level", LuaValue.valueOf(spell.level))
    table.set("mlevel", LuaValue.valueOf(spell.magicLevel))
    table.set("mana", LuaValue.valueOf(spell.mana))
    table.set("manapercent", LuaValue.valueOf(spell.manaPercent))
    table.setmetatable(env.userdata.metatable("Spell"))
    return table
}

/** Converts a nullable seam result to the Lua value Canary would push. */
private fun Any?.toLuaResult(): LuaValue = when (this) {
    null -> LuaValue.NIL
    is Boolean -> LuaValue.valueOf(this)
    is Int -> LuaValue.valueOf(this)
    is Long -> LuaValue.valueOf(this.toDouble())
    is Double -> LuaValue.valueOf(this)
    is String -> LuaValue.valueOf(this)
    else -> LuaValue.NIL
}

/**
 * Real, non-no-op implementation of [PlayerSocialOperations] backed by the
 * Kotlin domain objects (`Player`, `Guild`, `Party`, `House`, `Account`,
 * `PlayerPvp`, `Player.spellBook`).
 *
 * Mirrors the state reachable from
 * src/lua/functions/creatures/player/player_functions.cpp. Capabilities the
 * Kotlin domain does not model yet degrade to documented no-ops:
 *
 * PARITY GAPS: `Player::ip`, `Player::lastLoginSaved`, `Player::lastLogout`
 * and `Player::guildNick` (player.hpp:486, 1716, 1717, 1708) have no Kotlin
 * fields; this seam keeps per-player values in memory so a set followed by a
 * get is coherent, defaulting to Canary's 0/"" initial values. Protocol sends
 * (`sendTextWindow`, `sendChannelMessage`, `sendChannelMessage`,
 * `sendTutorial`, `sendInventory`, `sendLootStats`, `updateSupplyTracker`,
 * `updateKillTracker`, `sendHouseWindow`) have no protocol seam yet and
 * succeed without transmitting, matching the C++ methods' client-less
 * behavior. `showTextDialog` also skips the C++ item text/parent mutation and
 * the `ItemType::maxTextLen` fallback. `setVocation` does not rebroadcast
 * skills/stats nor reload the creature. `canLearnSpell` treats the unmodelled
 * `Player::getMagicLevel()` as 0. `Houses` and `Spells` registries are
 * constructor seams; without them `getHouse` returns nil and the spell queries
 * answer `false`/empty exactly like an empty registry. C++ paths that
 * `return 1` without pushing (e.g. the `addPremiumDays` clamp/save failures)
 * surface as nil.
 *
 * @param itemIdsByName optional `name -> id` catalog for `showTextDialog`.
 * @param houses optional house registry backing `getHouse`; without it the
 *   lookup misses like `g_game().map.houses.getHouseByPlayerId`.
 * @param spells optional instant-spell registry backing `canLearnSpell`,
 *   `getInstantSpells` and `canCast`.
 * @param learnSpells `g_configManager().getBoolean(LEARN_SPELLS)` seam.
 */
class WorldPlayerSocialOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val houses: Houses? = null,
    private val spells: Spells? = null,
    private val learnSpells: Boolean = false,
) : PlayerSocialOperations {

    /** Port bookkeeping for C++ `Player::guildNick` (player.hpp:1708). */
    private val guildNicks = mutableMapOf<Long, String>()

    /** Port bookkeeping for C++ `Player::ip` (player.hpp:486). */
    private val playerIps = mutableMapOf<Long, Long>()

    /** Port bookkeeping for C++ `Player::lastLoginSaved` (player.hpp:1716). */
    private val lastLoginSavedTimes = mutableMapOf<Long, Long>()

    /** Port bookkeeping for C++ `Player::lastLogout` (player.hpp:1717). */
    private val lastLogoutTimes = mutableMapOf<Long, Long>()

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun getParty(player: Any): Any? = (player as? Player)?.getParty()

    override fun getGuild(player: Any): Any? = (player as? Player)?.getGuild()

    override fun setGuild(player: Any, guild: Any?): Boolean? {
        val target = player as? Player ?: return null
        target.setGuild(guild as? Guild)
        return true
    }

    override fun getGuildLevel(player: Any): Int? = (player as? Player)?.getGuildRank()?.level

    override fun setGuildLevel(player: Any, level: Int): Boolean? {
        val target = player as? Player ?: return null
        val guild = target.getGuild() ?: return null
        val rank = guild.getRankByLevel(level) ?: return false
        target.setGuildRank(rank)
        return true
    }

    override fun getGuildNick(player: Any): String? {
        val target = player as? Player ?: return null
        return guildNicks[target.id] ?: ""
    }

    override fun setGuildNick(player: Any, nick: String): Boolean? {
        val target = player as? Player ?: return null
        guildNicks[target.id] = nick
        return true
    }

    override fun showTextDialog(
        player: Any,
        itemId: Int,
        text: String,
        canWrite: Boolean,
        length: Int,
    ): Boolean? = (player as? Player)?.let { true }

    override fun sendChannelMessage(
        player: Any,
        author: String,
        text: String,
        type: Int,
        channelId: Int,
    ): Boolean? = (player as? Player)?.let { true }

    override fun sendPrivateMessage(player: Any, speaker: Any?, text: String, type: Int): Boolean? =
        (player as? Player)?.let { true }

    override fun channelSay(player: Any, speaker: Any?, type: Int, text: String, channelId: Int): Boolean? =
        (player as? Player)?.let { true }

    override fun openChannel(player: Any, channelId: Int): Boolean? = (player as? Player)?.let { true }

    override fun popupFYI(player: Any, message: String): Boolean? = (player as? Player)?.let { true }

    override fun sendTutorial(player: Any, tutorialId: Int): Boolean? = (player as? Player)?.let { true }

    override fun sendInventory(player: Any): Boolean? = (player as? Player)?.let { true }

    override fun sendLootStats(player: Any, item: Any, count: Int): Boolean? = (player as? Player)?.let { true }

    override fun updateSupplyTracker(player: Any, item: Any): Boolean? = (player as? Player)?.let { true }

    override fun updateKillTracker(player: Any, creature: Any, corpse: Any): Boolean? =
        (player as? Player)?.let { true }

    override fun getHouse(player: Any): Any? {
        val target = player as? Player ?: return null
        return houses?.getHouseByPlayerId(target.id)
    }

    override fun sendHouseWindow(player: Any, house: Any, listId: Int): Boolean? {
        if (player as? Player == null || house !is House) {
            return null
        }
        return true
    }

    override fun setEditHouse(player: Any, house: Any, listId: Int): Boolean? {
        if (player as? Player == null || house !is House) {
            return null
        }
        return true
    }

    override fun getIp(player: Any): Long? {
        val target = player as? Player ?: return null
        return playerIps[target.id] ?: 0L
    }

    override fun getAccountId(player: Any): Long? =
        (player as? Player)?.account?.id?.takeIf { it != 0L }

    override fun getLastLoginSaved(player: Any): Long? {
        val target = player as? Player ?: return null
        return lastLoginSavedTimes[target.id] ?: 0L
    }

    override fun getLastLogout(player: Any): Long? {
        val target = player as? Player ?: return null
        return lastLogoutTimes[target.id] ?: 0L
    }

    override fun getAccountType(player: Any): Int? = (player as? Player)?.getAccountType()?.value

    override fun setAccountType(player: Any, accountType: Int): Boolean? {
        val target = player as? Player ?: return null
        val account = target.account ?: return null
        val type = AccountType.entries.firstOrNull { it.value == accountType } ?: return null
        if (account.setAccountType(type) != AccountErrors.Ok) {
            return null
        }
        if (account.save() != AccountErrors.Ok) {
            return null
        }
        return true
    }

    override fun getGroup(player: Any): Any? = (player as? Player)?.group

    override fun setGroup(player: Any, group: Any?): Boolean? {
        val resolved = group as? Group
        if (resolved == null) {
            return false
        }
        val target = player as? Player ?: return null
        target.group = resolved
        return true
    }

    override fun addPremiumDays(player: Any, days: Int): Boolean? {
        val account = accountOf(player) ?: return null
        val premiumDays = account.getPremiumRemainingDays()
        if (premiumDays == 0xFFFF) {
            return null
        }
        val addDays = minOf(0xFFFE - premiumDays, days)
        if (addDays <= 0) {
            return null
        }
        account.addPremiumDays(addDays)
        if (account.save() != AccountErrors.Ok) {
            return null
        }
        return true
    }

    override fun removePremiumDays(player: Any, days: Int): Boolean? {
        val account = accountOf(player) ?: return null
        val premiumDays = account.getPremiumRemainingDays()
        if (premiumDays == 0xFFFF) {
            return null
        }
        val removeDays = minOf(0xFFFE - premiumDays, days)
        if (removeDays <= 0) {
            return null
        }
        account.addPremiumDays(-removeDays)
        if (account.save() != AccountErrors.Ok) {
            return null
        }
        return true
    }

    override fun getTibiaCoins(player: Any): Int? = readCoins(player, CoinType.Normal)

    override fun addTibiaCoins(player: Any, coins: Int): Boolean? = addCoins(player, CoinType.Normal, coins)

    override fun removeTibiaCoins(player: Any, coins: Int): Boolean? = removeCoins(player, CoinType.Normal, coins)

    override fun getTransferableCoins(player: Any): Int? = readCoins(player, CoinType.Transferable)

    override fun addTransferableCoins(player: Any, coins: Int): Boolean? =
        addCoins(player, CoinType.Transferable, coins)

    override fun removeTransferableCoins(player: Any, coins: Int): Boolean? =
        removeCoins(player, CoinType.Transferable, coins)

    override fun removeTransferableAndTibiaCoins(player: Any, coins: Int): Boolean? {
        val account = accountOf(player) ?: return null
        if (account.removeCoins(CoinType.Transferable, CoinType.Normal, coins.toUInt()) != AccountErrors.Ok) {
            return null
        }
        return true
    }

    override fun getKills(player: Any): List<Kill>? = (player as? Player)?.pvpPlayer?.getUnjustifiedKills()

    override fun setKills(player: Any, kills: List<Kill>): Boolean? {
        val target = player as? Player ?: return null
        target.pvpPlayer.setUnjustifiedKills(kills)
        target.pvpPlayer.sendUnjustifiedPoints()
        return true
    }

    override fun getSkullTime(player: Any): Long? = (player as? Player)?.pvpPlayer?.getSkullTicks()

    override fun setSkullTime(player: Any, ticks: Long): Boolean? {
        val target = player as? Player ?: return null
        target.pvpPlayer.setSkullTicks(ticks)
        return true
    }

    override fun getDeathPenalty(player: Any): Int? =
        (player as? Player)?.let { (it.getLostPercent() * 100).toInt() }

    override fun getPvpMode(player: Any): Int? = (player as? Player)?.pvpPlayer?.getMode()?.value

    override fun hasAttacked(player: Any, target: Any?): Boolean? {
        val self = player as? Player ?: return null
        val other = target as? Player ?: return null
        return self.pvpPlayer.hasAttacked(other.pvpPlayer)
    }

    override fun setVocation(player: Any, vocation: Any?): Boolean? {
        val target = player as? Player ?: return null
        val resolved = when (vocation) {
            is Int -> target.vocations?.getVocation(vocation)
            is String -> target.vocations?.let { registry ->
                registry.getVocation(registry.getVocationId(vocation))
            }
            is Vocation -> vocation
            else -> null
        } ?: return false
        target.vocationId = resolved.getId()
        return true
    }

    override fun isPromoted(player: Any): Boolean? = (player as? Player)?.isPromoted()

    override fun canLearnSpell(player: Any, spellName: String): Boolean? {
        val target = player as? Player ?: return null
        val spell = spells?.getInstantSpellByName(spellName) ?: return false
        if (target.hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }
        if (!spell.vocSpellMap.containsKey(target.getVocationId())) {
            return false
        }
        if (target.level < spell.level) {
            return false
        }
        if (spell.magicLevel > 0) {
            return false
        }
        return true
    }

    override fun learnSpell(player: Any, spellName: String): Boolean? {
        val target = player as? Player ?: return null
        target.learnInstantSpell(spellName)
        return true
    }

    override fun forgetSpell(player: Any, spellName: String): Boolean? {
        val target = player as? Player ?: return null
        target.forgetInstantSpell(spellName)
        return true
    }

    override fun hasLearnedSpell(player: Any, spellName: String): Boolean? =
        (player as? Player)?.hasLearnedInstantSpell(spellName)

    override fun getInstantSpells(player: Any): List<LuaSpell>? {
        val target = player as? Player ?: return null
        val registry = spells ?: return emptyList()
        return registry.getInstantSpells().values
            .filter { canCastSpell(target, it) }
            .map { LuaSpell(it.name, it.words, it.level, it.magicLevel, it.mana, it.manaPercent) }
    }

    override fun canCast(player: Any, spell: Any?): Boolean? {
        val target = player as? Player ?: return null
        val instant = spell as? InstantSpell ?: return null
        return canCastSpell(target, instant)
    }

    /** `InstantSpell::canCast` (spells.cpp:1419-1443). */
    private fun canCastSpell(target: Player, spell: InstantSpell): Boolean {
        if (target.hasFlag(PlayerFlags_t.CannotUseSpells)) {
            return false
        }
        if (target.hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }
        if (learnSpells) {
            return target.hasLearnedInstantSpell(spell.name)
        }
        if (spell.isLearnable()) {
            return target.hasLearnedInstantSpell(spell.name)
        }
        return spell.vocSpellMap.isEmpty() || spell.vocSpellMap.containsKey(target.getVocationId())
    }

    private fun accountOf(player: Any): Account? = (player as? Player)?.account

    private fun readCoins(player: Any, type: CoinType): Int? {
        val account = accountOf(player) ?: return null
        val (coins, result) = account.getCoins(type)
        return if (result == AccountErrors.Ok) coins.toInt() else null
    }

    private fun addCoins(player: Any, type: CoinType, coins: Int): Boolean? {
        val account = accountOf(player) ?: return null
        if (account.addCoins(type, coins.toUInt()) != AccountErrors.Ok) {
            return null
        }
        if (account.save() != AccountErrors.Ok) {
            return null
        }
        return true
    }

    private fun removeCoins(player: Any, type: CoinType, coins: Int): Boolean? {
        val account = accountOf(player) ?: return null
        if (account.removeCoins(type, coins.toUInt()) != AccountErrors.Ok) {
            return null
        }
        if (account.save() != AccountErrors.Ok) {
            return null
        }
        return true
    }
}
