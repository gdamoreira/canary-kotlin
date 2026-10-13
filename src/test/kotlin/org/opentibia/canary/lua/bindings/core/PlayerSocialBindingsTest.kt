package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountErrors
import org.opentibia.canary.account.AccountInfo
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.account.CoinType
import org.opentibia.canary.account.CoinTransactionType
import org.opentibia.canary.account.RemoveCoinsResult
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.players.pvp.Kill
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Transcript-style tests for the Canary `Player` social/party/guild, storage-adjacent,
 * modal/UI, house, account/premium, kills/PvP and vocation/spell bindings.
 *
 * Every method name and argument order is taken from
 * src/lua/functions/creatures/player/player_functions.cpp; the seams record the
 * exact arguments the Lua function forwarded.
 */
class PlayerSocialBindingsTest {

    private class FakePlayer

    private class FakePlayerSocialOps : PlayerSocialOperations {
        var party: Any? = Any()
        var guild: Any? = Any()
        var guildLevel: Int? = 1
        var setGuildLevelResult: Boolean? = true
        var guildNick: String? = "Nightblade"
        var group: Any? = Any()
        var house: Any? = Any()
        var accountId: Long? = 55L
        var accountType = 3
        var setAccountTypeResult: Boolean? = true
        var addPremiumResult: Boolean? = true
        var removePremiumResult: Boolean? = true
        var tibiaCoins: Int? = 50
        var transferableCoins: Int? = 25
        var kills: List<Kill>? = listOf(Kill(7L, 8L, true))
        var skullTicks: Long? = 42L
        var deathPenalty: Int? = 10
        var pvpMode: Int? = 1
        var promoted = true
        var spellKnown = true
        var instantSpells: List<LuaSpell>? = listOf(LuaSpell("exura", "exura", 1, 0, 20, 0))

        var lastGuild: Any? = null
        var lastGuildLevel: Int? = null
        var lastGuildNick: String? = null
        var lastDialog: List<Any?>? = null
        var lastChannelMessage: List<Any?>? = null
        var lastPrivateMessage: List<Any?>? = null
        var lastChannelSay: List<Any?>? = null
        var lastOpenChannel: Int? = null
        var lastPopup: String? = null
        var lastTutorial: Int? = null
        var inventorySends = 0
        var lastLootStats: List<Any?>? = null
        var lastSupplyItem: Any? = null
        var lastKillTracker: List<Any?>? = null
        var lastHouseWindow: List<Any?>? = null
        var lastEditHouse: List<Any?>? = null
        var lastAccountType: Int? = null
        var lastGroup: Any? = null
        var lastPremiumDays: Int? = null
        var lastCoins: Int? = null
        var lastTransferableCoins: Int? = null
        var lastKills: List<Kill>? = null
        var lastSkullTicks: Long? = null
        var lastAttacked: Any? = null
        var lastVocation: Any? = null
        var lastSpellName: String? = null
        var lastCastSpell: Any? = null

        private fun playerOrNull(player: Any): FakePlayer? = player as? FakePlayer

        override fun itemIdByName(name: String): Int = if (name == "sword") 2376 else 0

        override fun getParty(player: Any): Any? = playerOrNull(player)?.let { party }

        override fun getGuild(player: Any): Any? = playerOrNull(player)?.let { guild }

        override fun setGuild(player: Any, guild: Any?): Boolean? {
            if (playerOrNull(player) == null) return null
            lastGuild = guild
            return true
        }

        override fun getGuildLevel(player: Any): Int? = playerOrNull(player)?.let { guildLevel }

        override fun setGuildLevel(player: Any, level: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastGuildLevel = level
            return setGuildLevelResult
        }

        override fun getGuildNick(player: Any): String? = playerOrNull(player)?.let { guildNick }

        override fun setGuildNick(player: Any, nick: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastGuildNick = nick
            return true
        }

        override fun showTextDialog(
            player: Any,
            itemId: Int,
            text: String,
            canWrite: Boolean,
            length: Int,
        ): Boolean? {
            if (playerOrNull(player) == null) return null
            lastDialog = listOf(itemId, text, canWrite, length)
            return true
        }

        override fun sendChannelMessage(
            player: Any,
            author: String,
            text: String,
            type: Int,
            channelId: Int,
        ): Boolean? {
            if (playerOrNull(player) == null) return null
            lastChannelMessage = listOf(author, text, type, channelId)
            return true
        }

        override fun sendPrivateMessage(player: Any, speaker: Any?, text: String, type: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastPrivateMessage = listOf(speaker, text, type)
            return true
        }

        override fun channelSay(player: Any, speaker: Any?, type: Int, text: String, channelId: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastChannelSay = listOf(speaker, type, text, channelId)
            return true
        }

        override fun openChannel(player: Any, channelId: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastOpenChannel = channelId
            return true
        }

        override fun popupFYI(player: Any, message: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastPopup = message
            return true
        }

        override fun sendTutorial(player: Any, tutorialId: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastTutorial = tutorialId
            return true
        }

        override fun sendInventory(player: Any): Boolean? {
            if (playerOrNull(player) == null) return null
            inventorySends += 1
            return true
        }

        override fun sendLootStats(player: Any, item: Any, count: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastLootStats = listOf(item, count)
            return true
        }

        override fun updateSupplyTracker(player: Any, item: Any): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSupplyItem = item
            return true
        }

        override fun updateKillTracker(player: Any, creature: Any, corpse: Any): Boolean? {
            if (playerOrNull(player) == null) return null
            lastKillTracker = listOf(creature, corpse)
            return true
        }

        override fun getHouse(player: Any): Any? = playerOrNull(player)?.let { house }

        override fun sendHouseWindow(player: Any, house: Any, listId: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastHouseWindow = listOf(house, listId)
            return true
        }

        override fun setEditHouse(player: Any, house: Any, listId: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastEditHouse = listOf(house, listId)
            return true
        }

        override fun getIp(player: Any): Long? = playerOrNull(player)?.let { 111L }

        override fun getAccountId(player: Any): Long? = playerOrNull(player)?.let { accountId }

        override fun getLastLoginSaved(player: Any): Long? = playerOrNull(player)?.let { 1000L }

        override fun getLastLogout(player: Any): Long? = playerOrNull(player)?.let { 2000L }

        override fun getAccountType(player: Any): Int? = playerOrNull(player)?.let { accountType }

        override fun setAccountType(player: Any, accountType: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastAccountType = accountType
            return setAccountTypeResult
        }

        override fun getGroup(player: Any): Any? = playerOrNull(player)?.let { group }

        override fun setGroup(player: Any, group: Any?): Boolean? {
            if (playerOrNull(player) == null) return null
            lastGroup = group
            return group != null
        }

        override fun addPremiumDays(player: Any, days: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastPremiumDays = days
            return addPremiumResult
        }

        override fun removePremiumDays(player: Any, days: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastPremiumDays = days
            return removePremiumResult
        }

        override fun getTibiaCoins(player: Any): Int? = playerOrNull(player)?.let { tibiaCoins }

        override fun addTibiaCoins(player: Any, coins: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastCoins = coins
            return true
        }

        override fun removeTibiaCoins(player: Any, coins: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastCoins = coins
            return true
        }

        override fun getTransferableCoins(player: Any): Int? = playerOrNull(player)?.let { transferableCoins }

        override fun addTransferableCoins(player: Any, coins: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastTransferableCoins = coins
            return true
        }

        override fun removeTransferableCoins(player: Any, coins: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastTransferableCoins = coins
            return true
        }

        override fun removeTransferableAndTibiaCoins(player: Any, coins: Int): Boolean? {
            if (playerOrNull(player) == null) return null
            lastTransferableCoins = coins
            return true
        }

        override fun getKills(player: Any): List<Kill>? = playerOrNull(player)?.let { kills }

        override fun setKills(player: Any, kills: List<Kill>): Boolean? {
            if (playerOrNull(player) == null) return null
            lastKills = kills
            return true
        }

        override fun getSkullTime(player: Any): Long? = playerOrNull(player)?.let { skullTicks }

        override fun setSkullTime(player: Any, ticks: Long): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSkullTicks = ticks
            return true
        }

        override fun getDeathPenalty(player: Any): Int? = playerOrNull(player)?.let { deathPenalty }

        override fun getPvpMode(player: Any): Int? = playerOrNull(player)?.let { pvpMode }

        override fun hasAttacked(player: Any, target: Any?): Boolean? {
            if (playerOrNull(player) == null) return null
            lastAttacked = target
            return true
        }

        override fun setVocation(player: Any, vocation: Any?): Boolean? {
            if (playerOrNull(player) == null) return null
            lastVocation = vocation
            return vocation != null
        }

        override fun isPromoted(player: Any): Boolean? = playerOrNull(player)?.let { promoted }

        override fun canLearnSpell(player: Any, spellName: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSpellName = spellName
            return spellKnown
        }

        override fun learnSpell(player: Any, spellName: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSpellName = spellName
            return true
        }

        override fun forgetSpell(player: Any, spellName: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSpellName = spellName
            return true
        }

        override fun hasLearnedSpell(player: Any, spellName: String): Boolean? {
            if (playerOrNull(player) == null) return null
            lastSpellName = spellName
            return spellKnown
        }

        override fun getInstantSpells(player: Any): List<LuaSpell>? = playerOrNull(player)?.let { instantSpells }

        override fun canCast(player: Any, spell: Any?): Boolean? {
            if (playerOrNull(player) == null) return null
            lastCastSpell = spell
            return spell != null
        }
    }

    private class FakeAccountRepository : AccountRepository {
        var coins = mutableMapOf<Pair<Long, CoinType>, UInt>()
        private var accountType = AccountType.ACCOUNT_TYPE_TUTOR

        override fun findByAccountName(name: String): Account? = null

        override fun authenticate(name: String, password: String): Account? = null

        override fun loadByID(id: Long, acc: AccountInfo): Boolean {
            acc.accountType = accountType
            acc.premiumLastDay = getTimeNow() + 10 * Account.SECONDS_PER_DAY
            acc.premiumRemainingDays = 10
            return true
        }

        override fun save(accInfo: AccountInfo): Boolean {
            accountType = accInfo.accountType
            return true
        }

        override fun getCoins(id: Long, coinType: CoinType): UInt = coins[id to coinType] ?: 0u

        override fun setCoins(id: Long, coinType: CoinType, amount: UInt): Boolean {
            coins[id to coinType] = amount
            return true
        }

        override fun removeCoins(
            id: Long,
            primaryType: CoinType,
            secondaryType: CoinType,
            amount: UInt,
            detail: String,
        ): RemoveCoinsResult {
            var remaining = amount
            val primary = coins[id to primaryType] ?: 0u
            val primaryRemoved = minOf(primary, remaining)
            remaining -= primaryRemoved
            coins[id to primaryType] = primary - primaryRemoved
            val secondary = coins[id to secondaryType] ?: 0u
            val secondaryRemoved = minOf(secondary, remaining)
            coins[id to secondaryType] = secondary - secondaryRemoved
            return RemoveCoinsResult(AccountErrors.Ok, primaryRemoved, secondaryRemoved)
        }

        override fun registerCoinsTransaction(
            id: Long,
            type: CoinTransactionType,
            coins: UInt,
            coinType: CoinType,
            description: String,
        ): Boolean = true
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakePlayerSocialOps = FakePlayerSocialOps()): Pair<LuaEnvironment, FakePlayerSocialOps> {
        val environment = env()
        PlayerSocialFunctions.install(environment, fake)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))
        return environment to fake
    }

    @Test
    fun `Player party and guild methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            party = p:getParty()
            guild = p:getGuild()
            level = p:getGuildLevel()
            levelSet = p:setGuildLevel(2)
            nick = p:getGuildNick()
            nickSet = p:setGuildNick("Warden")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("party").isuserdata()).isTrue()
        assertThat(environment.getGlobal("guild").isuserdata()).isTrue()
        assertThat(environment.getGlobal("level").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("levelSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("nick").tojstring()).isEqualTo("Nightblade")
        assertThat(environment.getGlobal("nickSet").toboolean()).isTrue()
        assertThat(fake.lastGuildLevel).isEqualTo(2)
        assertThat(fake.lastGuildNick).isEqualTo("Warden")
    }

    @Test
    fun `Player guild methods preserve the Canary nil and false contracts`() {
        val (environment, fake) = installed()
        environment.globals.set("g", environment.userdata.wrap("Guild", Any()))
        environment.loadChunk("guildSet = p:setGuild(g)")

        assertThat(environment.getGlobal("guildSet").toboolean()).isTrue()
        assertThat(fake.lastGuild).isNotNull()

        fake.party = null
        fake.guild = null
        fake.guildLevel = null
        fake.guildNick = null
        fake.setGuildLevelResult = false
        environment.loadChunk(
            """
            party = p:getParty()
            guild = p:getGuild()
            level = p:getGuildLevel()
            levelSet = p:setGuildLevel(9)
            nick = p:getGuildNick()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("party").isnil()).isTrue()
        assertThat(environment.getGlobal("guild").isnil()).isTrue()
        assertThat(environment.getGlobal("level").isnil()).isTrue()
        assertThat(environment.getGlobal("levelSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("nick").isnil()).isTrue()
    }

    @Test
    fun `Player chat and UI sends use the Canary names and argument order`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            channel = p:sendChannelMessage("Author", "Hello", 1, 7)
            private = p:sendPrivateMessage(p, "Hi")
            say = p:channelSay(p, 2, "Text", 3)
            open = p:openChannel(9)
            fyi = p:popupFYI("FYI")
            tutorial = p:sendTutorial(5)
            inventory = p:sendInventory()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("channel").toboolean()).isTrue()
        assertThat(environment.getGlobal("private").toboolean()).isTrue()
        assertThat(environment.getGlobal("say").toboolean()).isTrue()
        assertThat(environment.getGlobal("open").toboolean()).isTrue()
        assertThat(environment.getGlobal("fyi").toboolean()).isTrue()
        assertThat(environment.getGlobal("tutorial").toboolean()).isTrue()
        assertThat(environment.getGlobal("inventory").toboolean()).isTrue()
    }

    @Test
    fun `Player chat and UI send seams receive the forwarded arguments`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            p:sendChannelMessage("Author", "Hello", 1, 7)
            p:sendPrivateMessage(p, "Hi")
            p:channelSay(p, 2, "Text", 3)
            p:openChannel(9)
            p:popupFYI("FYI")
            p:sendTutorial(5)
            p:sendInventory()
            """.trimIndent(),
        )

        assertThat(fake.lastChannelMessage).containsExactly("Author", "Hello", 1, 7)
        assertThat(fake.lastPrivateMessage!![1]).isEqualTo("Hi")
        assertThat(fake.lastPrivateMessage!![2]).isEqualTo(4)
        assertThat(fake.lastChannelSay!![1]).isEqualTo(2)
        assertThat(fake.lastChannelSay!![2]).isEqualTo("Text")
        assertThat(fake.lastChannelSay!![3]).isEqualTo(3)
        assertThat(fake.lastOpenChannel).isEqualTo(9)
        assertThat(fake.lastPopup).isEqualTo("FYI")
        assertThat(fake.lastTutorial).isEqualTo(5)
        assertThat(fake.inventorySends).isEqualTo(1)
    }

    @Test
    fun `Player showTextDialog resolves the Canary item argument forms`() {
        val (environment, fake) = installed()
        environment.globals.set("item", environment.userdata.wrap("Item", Item(2376)))
        environment.loadChunk(
            """
            byId = p:showTextDialog(2376, "read me", true, 500)
            byName = p:showTextDialog("sword")
            byItem = p:showTextDialog(item)
            unknown = p:showTextDialog("nope")
            bad = p:showTextDialog(true)
            long = p:showTextDialog(2376, "abcdefghij", false, 3)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").toboolean()).isTrue()
        assertThat(environment.getGlobal("byName").toboolean()).isTrue()
        assertThat(environment.getGlobal("byItem").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").toboolean()).isFalse()
        assertThat(environment.getGlobal("bad").toboolean()).isFalse()
        assertThat(fake.lastDialog).containsExactly(2376, "abcdefghij", false, 10)
    }

    @Test
    fun `Player loot and tracker sends guard their required userdata`() {
        val (environment, fake) = installed()
        environment.globals.set("item", environment.userdata.wrap("Item", Item(2376)))
        environment.globals.set("creature", environment.userdata.wrap("Monster", FakePlayer()))
        environment.globals.set("corpse", environment.userdata.wrap("Container", Item(1987)))
        environment.loadChunk(
            """
            loot = p:sendLootStats(item, 3)
            lootZero = p:sendLootStats(item, 0)
            lootNoItem = p:sendLootStats()
            supply = p:updateSupplyTracker(item)
            supplyNoItem = p:updateSupplyTracker()
            tracker = p:updateKillTracker(creature, corpse)
            trackerNoCorpse = p:updateKillTracker(creature)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("loot").toboolean()).isTrue()
        assertThat(environment.getGlobal("lootZero").isnil()).isTrue()
        assertThat(environment.getGlobal("lootNoItem").isnil()).isTrue()
        assertThat(environment.getGlobal("supply").toboolean()).isTrue()
        assertThat(environment.getGlobal("supplyNoItem").isnil()).isTrue()
        assertThat(environment.getGlobal("tracker").toboolean()).isTrue()
        assertThat(environment.getGlobal("trackerNoCorpse").isnil()).isTrue()
        assertThat(fake.lastLootStats!![1]).isEqualTo(3)
        assertThat(fake.lastKillTracker!!.size).isEqualTo(2)
    }

    @Test
    fun `Player house methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.globals.set("house", environment.userdata.wrap("House", Any()))
        environment.loadChunk(
            """
            found = p:getHouse()
            sent = p:sendHouseWindow(house, 4)
            edit = p:setEditHouse(house, 5)
            missing = p:setEditHouse(nil, 5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("found").isuserdata()).isTrue()
        assertThat(environment.getGlobal("sent").toboolean()).isTrue()
        assertThat(environment.getGlobal("edit").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(fake.lastHouseWindow!![1]).isEqualTo(4)
        assertThat(fake.lastEditHouse!![1]).isEqualTo(5)
    }

    @Test
    fun `Player account methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            ip = p:getIp()
            accountId = p:getAccountId()
            lastLogin = p:getLastLoginSaved()
            lastLogout = p:getLastLogout()
            accountType = p:getAccountType()
            typeSet = p:setAccountType(2)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("ip").toint()).isEqualTo(111)
        assertThat(environment.getGlobal("accountId").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("lastLogin").toint()).isEqualTo(1000)
        assertThat(environment.getGlobal("lastLogout").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("accountType").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("typeSet").toboolean()).isTrue()
        assertThat(fake.lastAccountType).isEqualTo(2)

        fake.accountId = null
        fake.setAccountTypeResult = null
        environment.loadChunk("noAccountId = p:getAccountId()\nnoTypeSet = p:setAccountType(1)")
        assertThat(environment.getGlobal("noAccountId").isnil()).isTrue()
        assertThat(environment.getGlobal("noTypeSet").isnil()).isTrue()
    }

    @Test
    fun `Player group methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.globals.set("g", environment.userdata.wrap("Group", Any()))
        environment.loadChunk(
            """
            group = p:getGroup()
            groupSet = p:setGroup(g)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("group").isuserdata()).isTrue()
        assertThat(environment.getGlobal("groupSet").toboolean()).isTrue()
        assertThat(fake.lastGroup).isNotNull()

        environment.loadChunk("groupCleared = p:setGroup(nil)")
        assertThat(environment.getGlobal("groupCleared").toboolean()).isFalse()
    }

    @Test
    fun `Player premium day methods preserve the Canary silent no-op contract`() {
        val (environment, fake) = installed()
        environment.loadChunk("added = p:addPremiumDays(7)\nremoved = p:removePremiumDays(3)")
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(fake.lastPremiumDays).isEqualTo(3)

        fake.addPremiumResult = null
        fake.removePremiumResult = null
        environment.loadChunk("added = p:addPremiumDays(7)\nremoved = p:removePremiumDays(3)")
        assertThat(environment.getGlobal("added").isnil()).isTrue()
        assertThat(environment.getGlobal("removed").isnil()).isTrue()
    }

    @Test
    fun `Player coin methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            tibia = p:getTibiaCoins()
            added = p:addTibiaCoins(10)
            removed = p:removeTibiaCoins(5)
            transferable = p:getTransferableCoins()
            addedTransferable = p:addTransferableCoins(2)
            removedTransferable = p:removeTransferableCoins(1)
            both = p:removeTransferableAndTibiaCoins(3)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("tibia").toint()).isEqualTo(50)
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("transferable").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("addedTransferable").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedTransferable").toboolean()).isTrue()
        assertThat(environment.getGlobal("both").toboolean()).isTrue()
        assertThat(fake.lastCoins).isEqualTo(5)
        assertThat(fake.lastTransferableCoins).isEqualTo(3)

        fake.tibiaCoins = null
        environment.loadChunk("noCoins = p:getTibiaCoins()")
        assertThat(environment.getGlobal("noCoins").isnil()).isTrue()
    }

    @Test
    fun `Player kills round-trip the Canary kill table`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            kills = p:getKills()
            count = #kills
            target = kills[1][1]
            time = kills[1][2]
            unavenged = kills[1][3]
            set = p:setKills({{10, 20, true}, {30, 40, false}})
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("target").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("time").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("unavenged").toboolean()).isTrue()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(fake.lastKills).isEqualTo(listOf(Kill(10L, 20L, true), Kill(30L, 40L, false)))
    }

    @Test
    fun `Player setKills validates the Canary kill table shape`() {
        val (environment, _) = installed()
        assertThatThrownBy { environment.loadChunk("p:setKills(5)") }
            .isInstanceOf(ScriptError::class.java)
        assertThatThrownBy { environment.loadChunk("p:setKills({{1}})") }
            .isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `Player skull death penalty and pvp methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            skull = p:getSkullTime()
            skullSet = p:setSkullTime(99)
            penalty = p:getDeathPenalty()
            pvp = p:getPvpMode()
            attacked = p:hasAttacked(p)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("skull").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("skullSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("penalty").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("pvp").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("attacked").toboolean()).isTrue()
        assertThat(fake.lastSkullTicks).isEqualTo(99L)
        assertThat(fake.lastAttacked).isNotNull()
    }

    @Test
    fun `Player vocation and spell methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.globals.set("vocation", environment.userdata.wrap("Vocation", Any()))
        environment.loadChunk(
            """
            promoted = p:isPromoted()
            learnable = p:canLearnSpell("exura")
            learned = p:learnSpell("exura")
            forgotten = p:forgetSpell("exura")
            has = p:hasLearnedSpell("exura")
            byId = p:setVocation(5)
            byName = p:setVocation("Knight")
            byUserdata = p:setVocation(vocation)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("promoted").toboolean()).isTrue()
        assertThat(environment.getGlobal("learnable").toboolean()).isTrue()
        assertThat(environment.getGlobal("learned").toboolean()).isTrue()
        assertThat(environment.getGlobal("forgotten").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(environment.getGlobal("byId").toboolean()).isTrue()
        assertThat(environment.getGlobal("byName").toboolean()).isTrue()
        assertThat(environment.getGlobal("byUserdata").toboolean()).isTrue()
        assertThat(fake.lastVocation).isNotNull()

        environment.loadChunk("invalid = p:setVocation(nil)")
        assertThat(environment.getGlobal("invalid").toboolean()).isFalse()
    }

    @Test
    fun `Player instant spells are pushed as Canary spell tables`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            spells = p:getInstantSpells()
            count = #spells
            name = spells[1].name
            words = spells[1].words
            level = spells[1].level
            mlevel = spells[1].mlevel
            mana = spells[1].mana
            manapercent = spells[1].manapercent
            notCast = p:canCast(5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("exura")
        assertThat(environment.getGlobal("words").tojstring()).isEqualTo("exura")
        assertThat(environment.getGlobal("level").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("mlevel").toint()).isZero()
        assertThat(environment.getGlobal("mana").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("manapercent").toint()).isZero()
        assertThat(environment.getGlobal("notCast").isnil()).isTrue()
    }

    @Test
    fun `Player canCast forwards spell userdata to the seam`() {
        val (environment, fake) = installed()
        environment.globals.set("spellUserdata", environment.userdata.wrap("InstantSpell", Any()))
        environment.loadChunk("cast = p:canCast(spellUserdata)")
        assertThat(environment.getGlobal("cast").toboolean()).isTrue()
        assertThat(fake.lastCastSpell).isNotNull()
    }

    @Test
    fun `PlayerSocial bindings register on the Player Monster and Npc metatables`() {
        val environment = env()
        PlayerSocialFunctions.install(environment)
        val names = listOf(
            "getParty", "getGuild", "setGuild", "getGuildLevel", "setGuildLevel",
            "getGuildNick", "setGuildNick", "showTextDialog", "sendChannelMessage",
            "sendPrivateMessage", "channelSay", "openChannel", "popupFYI", "sendTutorial",
            "sendInventory", "sendLootStats", "updateSupplyTracker", "updateKillTracker",
            "getHouse", "sendHouseWindow", "setEditHouse", "getIp", "getAccountId",
            "getLastLoginSaved", "getLastLogout", "getAccountType", "setAccountType",
            "getGroup", "setGroup", "addPremiumDays", "removePremiumDays", "getTibiaCoins",
            "addTibiaCoins", "removeTibiaCoins", "getTransferableCoins",
            "addTransferableCoins", "removeTransferableCoins", "removeTransferableAndTibiaCoins",
            "getKills", "setKills", "getSkullTime", "setSkullTime", "getDeathPenalty",
            "getPvpMode", "hasAttacked", "setVocation", "isPromoted", "canLearnSpell",
            "learnSpell", "forgetSpell", "hasLearnedSpell", "getInstantSpells", "canCast",
        )
        val checks = names.joinToString(" and ") { name ->
            listOf("Player", "Monster", "Npc").joinToString(" and ") { type ->
                "(rawgetmetatable('$type').$name ~= nil)"
            }
        }
        environment.loadChunk("checks = $checks")
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `PlayerSocial bindings push nil when the receiver is not a player`() {
        val environment = env()
        val fake = FakePlayerSocialOps()
        PlayerSocialFunctions.install(environment, fake)
        environment.globals.set("m", environment.userdata.wrap("Monster", Any()))
        environment.loadChunk(
            """
            guild = m:getGuild()
            level = m:getGuildLevel()
            kills = m:getKills()
            accountId = m:getAccountId()
            spells = m:getInstantSpells()
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("guild").isnil()).isTrue()
        assertThat(environment.getGlobal("level").isnil()).isTrue()
        assertThat(environment.getGlobal("kills").isnil()).isTrue()
        assertThat(environment.getGlobal("accountId").isnil()).isTrue()
        assertThat(environment.getGlobal("spells").isnil()).isTrue()
    }

    @Test
    fun `PlayerSocial bindings do not invent methods absent from Canary`() {
        val environment = env()
        PlayerSocialFunctions.install(environment)
        val absent = listOf(
            "getStorageValueByKey",
            "setGuildRank",
            "getGuildRank",
            "sendCancelMessage",
            "sendModalWindow",
            "isPremium",
            "setPremiumEndsAt",
            "getHouseAccessLevel",
            "getDeaths",
        )
        val checks = absent.joinToString(" and ") { name ->
            "(rawgetmetatable('Player').$name == nil)"
        }
        environment.loadChunk("checks = $checks")
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `real Player domain answers the social surface through WorldPlayerSocialOperations`() {
        val repo = FakeAccountRepository()
        val account = Account(id = 1, name = "acct", repository = repo)
        account.load()

        val player = Player(id = 777L, name = "Hero", maxHealth = 150, health = 150, position = Position(10, 10, 7))
        player.account = account
        player.group = Group(name = "GM", id = 1, access = true)
        player.setGuild(Guild(1, "Red Rose"))
        player.setParty(Party())
        player.pvpPlayer.setUnjustifiedKills(listOf(Kill(100L, 200L, true)))
        val enemy = Player(id = 888L, name = "Enemy", maxHealth = 150, health = 150, position = Position(11, 10, 7))
        player.pvpPlayer.addAttacked(enemy.pvpPlayer)

        val vocations = Vocations()
        vocations.loadFromXml(
            "<vocations><vocation id=\"1\" name=\"Knight\" clientid=\"1\" baseid=\"1\" fromvoc=\"0\"/></vocations>",
        )
        player.vocations = vocations
        player.vocationId = 1

        val houses = Houses()
        houses.addHouse(5).owner = 777L

        val spells = Spells()
        val instant = InstantSpell()
        instant.name = "exura"
        instant.words = "exura"
        instant.level = 1
        instant.addVocMap(1, true)
        spells.setInstantSpell("exura", instant)

        val environment = env()
        PlayerSocialFunctions.install(
            environment,
            WorldPlayerSocialOperations(houses = houses, spells = spells),
        )
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        environment.globals.set("e", environment.userdata.wrap("Player", enemy))

        environment.loadChunk(
            """
            party = p:getParty() ~= nil
            guild = p:getGuild() ~= nil
            guildLevel = p:getGuildLevel()
            levelSet = p:setGuildLevel(2)
            guildLevelAfter = p:getGuildLevel()
            p:setGuildNick("Vice")
            nick = p:getGuildNick()
            houseFound = p:getHouse() ~= nil
            accountId = p:getAccountId()
            accountType = p:getAccountType()
            typeSet = p:setAccountType(4)
            accountTypeAfter = p:getAccountType()
            groupFound = p:getGroup() ~= nil
            kills = #p:getKills()
            skull = p:getSkullTime()
            p:setSkullTime(55)
            skullAfter = p:getSkullTime()
            p:setKills({{11, 22, true}})
            killsAfter = #p:getKills()
            attacked = p:hasAttacked(e)
            penalty = p:getDeathPenalty()
            pvpMode = p:getPvpMode()
            promoted = p:isPromoted()
            learnable = p:canLearnSpell("exura")
            spellCount = #p:getInstantSpells()
            p:learnSpell("exura")
            learned = p:hasLearnedSpell("exura")
            p:forgetSpell("exura")
            forgotten = p:hasLearnedSpell("exura")
            vocationSet = p:setVocation("Knight")
            premiumSet = p:addPremiumDays(7)
            coins = p:getTibiaCoins()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("party").toboolean()).isTrue()
        assertThat(environment.getGlobal("guild").toboolean()).isTrue()
        assertThat(environment.getGlobal("guildLevel").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("levelSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("guildLevelAfter").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("nick").tojstring()).isEqualTo("Vice")
        assertThat(environment.getGlobal("houseFound").toboolean()).isTrue()
        assertThat(environment.getGlobal("accountId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("accountType").toint()).isEqualTo(AccountType.ACCOUNT_TYPE_TUTOR.value)
        assertThat(environment.getGlobal("typeSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("accountTypeAfter").toint())
            .isEqualTo(AccountType.ACCOUNT_TYPE_GAMEMASTER.value)
        assertThat(environment.getGlobal("groupFound").toboolean()).isTrue()
        assertThat(environment.getGlobal("kills").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("skull").toint()).isZero()
        assertThat(environment.getGlobal("skullAfter").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("killsAfter").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("attacked").toboolean()).isTrue()
        assertThat(environment.getGlobal("penalty").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("pvpMode").toint()).isZero()
        assertThat(environment.getGlobal("promoted").toboolean()).isTrue()
        assertThat(environment.getGlobal("learnable").toboolean()).isTrue()
        assertThat(environment.getGlobal("spellCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("learned").toboolean()).isTrue()
        assertThat(environment.getGlobal("forgotten").toboolean()).isFalse()
        assertThat(player.vocationId).isEqualTo(1)
        assertThat(environment.getGlobal("vocationSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("premiumSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("coins").toint()).isEqualTo(0)
    }
}
