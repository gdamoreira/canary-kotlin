package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.mount.Mounts
import org.opentibia.canary.systems.rewardchest.Reward

/**
 * Transcript-style tests for the remaining Canary `Player` binding surface in
 * src/lua/functions/creatures/player/player_functions.cpp: outfits, mounts,
 * familiars, pronoun, reward chest, daily reward, client info and XP boosts.
 * Driven through real Lua chunks with the exact Canary method names.
 */
class PlayerRemainderBindingsTest {

    private class FakePlayer

    private class FakeRemainderOps : PlayerRemainderOperations {
        val calls = linkedMapOf<String, List<Any?>>()

        var receiver: Any? = null

        fun player(): Any? = receiver

        private fun <T> rec(name: String, result: T, vararg args: Any?): T {
            calls[name] = args.toList()
            return result
        }

        val reward: Reward = Reward()
        val client = LuaClientInfo(version = 1311, versionString = "11.00", os = 2)

        override fun outfitLookTypeByName(player: Any, name: String): Int? = when (name) {
            "citizen" -> 136
            "hunter" -> 137
            else -> null
        }

        override fun addOutfit(player: Any, lookType: Int, addon: Int): Boolean? =
            rec("addOutfit", true, player, lookType, addon)

        override fun addOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean? =
            rec("addOutfitAddon", true, player, lookType, addon)

        override fun removeOutfit(player: Any, lookType: Int): Boolean? =
            rec("removeOutfit", true, player, lookType)

        override fun removeOutfitAddon(player: Any, lookType: Int, addon: Int): Boolean? =
            rec("removeOutfitAddon", true, player, lookType, addon)

        override fun hasOutfit(player: Any, lookType: Int, addon: Int): Boolean? =
            rec("hasOutfit", true, player, lookType, addon)

        override fun sendOutfitWindow(player: Any): Boolean? = rec("sendOutfitWindow", true, player)

        override fun mountIdByName(name: String): Int = if (name == "Widow Queen") 5 else 0

        override fun addMount(player: Any, mountId: Int): Boolean? =
            rec("addMount", true, player, mountId)

        override fun removeMount(player: Any, mountId: Int): Boolean? =
            rec("removeMount", true, player, mountId)

        override fun hasMount(player: Any, mountId: Int): Boolean? =
            rec("hasMount", true, player, mountId)

        override fun addFamiliar(player: Any, lookType: Int): Boolean? =
            rec("addFamiliar", true, player, lookType)

        override fun removeFamiliar(player: Any, lookType: Int): Boolean? =
            rec("removeFamiliar", true, player, lookType)

        override fun hasFamiliar(player: Any, lookType: Int): Boolean? =
            rec("hasFamiliar", true, player, lookType)

        override fun setFamiliarLooktype(player: Any, lookType: Int): Boolean? =
            rec("setFamiliarLooktype", true, player, lookType)

        override fun getFamiliarLooktype(player: Any): Int? = rec("getFamiliarLooktype", 31, player)

        override fun getPronoun(player: Any): Int? = rec("getPronoun", 1, player)

        override fun setPronoun(player: Any, pronoun: Int): Boolean? =
            rec("setPronoun", true, player, pronoun)

        override fun getReward(player: Any, rewardId: Long, autoCreate: Boolean): Any? =
            rec("getReward", reward, player, rewardId, autoCreate)

        override fun removeReward(player: Any, rewardId: Long): Boolean? =
            rec("removeReward", true, player, rewardId)

        override fun getRewardList(player: Any): List<Long>? =
            rec("getRewardList", listOf(1000L, 2000L), player)

        override fun setDailyReward(player: Any, value: Int): Boolean? =
            rec("setDailyReward", true, player, value)

        override fun getClient(player: Any): LuaClientInfo? = rec("getClient", client, player)

        override fun getBaseXpGain(player: Any): Int? = rec("getBaseXpGain", 100, player)

        override fun setBaseXpGain(player: Any, value: Int): Boolean? =
            rec("setBaseXpGain", true, player, value)

        override fun getVoucherXpBoost(player: Any): Int? = rec("getVoucherXpBoost", 20, player)

        override fun setVoucherXpBoost(player: Any, value: Int): Boolean? =
            rec("setVoucherXpBoost", true, player, value)

        override fun getGrindingXpBoost(player: Any): Int? = rec("getGrindingXpBoost", 10, player)

        override fun setGrindingXpBoost(player: Any, value: Int): Boolean? =
            rec("setGrindingXpBoost", true, player, value)

        override fun getXpBoostPercent(player: Any): Int? = rec("getXpBoostPercent", 50, player)

        override fun setXpBoostPercent(player: Any, value: Int): Boolean? =
            rec("setXpBoostPercent", true, player, value)

        override fun getXpBoostTime(player: Any): Int? = rec("getXpBoostTime", 3600, player)

        override fun setXpBoostTime(player: Any, value: Int): Boolean? =
            rec("setXpBoostTime", true, player, value)

        override fun getStaminaXpBoost(player: Any): Int? = rec("getStaminaXpBoost", 30, player)

        override fun setStaminaXpBoost(player: Any, value: Int): Boolean? =
            rec("setStaminaXpBoost", true, player, value)
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(
        fake: FakeRemainderOps = FakeRemainderOps(),
    ): Pair<LuaEnvironment, FakeRemainderOps> {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations(), fake)
        val player = FakePlayer()
        fake.receiver = player
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        return environment to fake
    }

    @Test
    fun `remainder outfit and pronoun bindings use the Canary names and overloads`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            pronoun = p:getPronoun()
            pronounSet = p:setPronoun(2)
            outfit = p:addOutfit(136, 3)
            outfitNoAddon = p:addOutfit(136)
            outfitByName = p:addOutfit("citizen", 1)
            outfitUnknown = p:addOutfit("no such outfit")
            addon = p:addOutfitAddon(136, 3)
            removed = p:removeOutfit(136)
            addonRemoved = p:removeOutfitAddon(136, 3)
            has = p:hasOutfit(136)
            hasAddon = p:hasOutfit(136, 3)
            window = p:sendOutfitWindow()
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("pronoun").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("pronounSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("outfit").toboolean()).isTrue()
        assertThat(environment.getGlobal("outfitNoAddon").toboolean()).isTrue()
        assertThat(environment.getGlobal("outfitByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("outfitUnknown").isnil()).isTrue()
        assertThat(environment.getGlobal("addon").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("addonRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("has").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasAddon").toboolean()).isTrue()
        assertThat(environment.getGlobal("window").toboolean()).isTrue()
        assertThat(fake.calls["setPronoun"]).isEqualTo(listOf(player, 2))
        assertThat(fake.calls["addOutfit"]).isEqualTo(listOf(player, 136, 1))
        assertThat(fake.calls["addOutfitAddon"]).isEqualTo(listOf(player, 136, 3))
        assertThat(fake.calls["removeOutfit"]).isEqualTo(listOf(player, 136))
        assertThat(fake.calls["removeOutfitAddon"]).isEqualTo(listOf(player, 136, 3))
        assertThat(fake.calls["hasOutfit"]).isEqualTo(listOf(player, 136, 3))
    }

    @Test
    fun `remainder mount and familiar bindings resolve names like Canary`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            mount = p:addMount(5)
            mountByName = p:addMount("Widow Queen")
            mountUnknown = p:addMount("no such mount")
            mountRemoved = p:removeMount(5)
            mountRemovedByName = p:removeMount("Widow Queen")
            mountUnknownRemove = p:removeMount("no such mount")
            hasMount = p:hasMount(5)
            hasMountByName = p:hasMount("Widow Queen")
            hasMountUnknown = p:hasMount("no such mount")
            familiar = p:addFamiliar(31)
            familiarRemoved = p:removeFamiliar(31)
            hasFamiliar = p:hasFamiliar(31)
            looktypeSet = p:setFamiliarLooktype(31)
            looktype = p:getFamiliarLooktype()
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("mount").toboolean()).isTrue()
        assertThat(environment.getGlobal("mountByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("mountUnknown").isnil()).isTrue()
        assertThat(environment.getGlobal("mountRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("mountRemovedByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("mountUnknownRemove").isnil()).isTrue()
        assertThat(environment.getGlobal("hasMount").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasMountByName").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasMountUnknown").isnil()).isTrue()
        assertThat(environment.getGlobal("familiar").toboolean()).isTrue()
        assertThat(environment.getGlobal("familiarRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("hasFamiliar").toboolean()).isTrue()
        assertThat(environment.getGlobal("looktypeSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("looktype").toint()).isEqualTo(31)
        assertThat(fake.calls["addMount"]).isEqualTo(listOf(player, 5))
        assertThat(fake.calls["hasMount"]).isEqualTo(listOf(player, 5))
        assertThat(fake.calls["addFamiliar"]).isEqualTo(listOf(player, 31))
        assertThat(fake.calls["hasFamiliar"]).isEqualTo(listOf(player, 31))
    }

    @Test
    fun `remainder reward daily reward and client bindings match Canary`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            reward = p:getReward(1000)
            rewardCreated = p:getReward(1000, true)
            removedReward = p:removeReward(1000)
            list = p:getRewardList()
            listSize = #list
            listFirst = list[1]
            listSecond = list[2]
            dailySet = p:setDailyReward(2)
            client = p:getClient()
            version = client.version
            versionString = client.versionString
            os = client.os
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("reward").touserdata()).isSameAs(fake.reward)
        assertThat(environment.getGlobal("rewardCreated").touserdata()).isSameAs(fake.reward)
        assertThat(environment.getGlobal("removedReward").toboolean()).isTrue()
        assertThat(environment.getGlobal("listSize").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("listFirst").toint()).isEqualTo(1000)
        assertThat(environment.getGlobal("listSecond").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("dailySet").toboolean()).isTrue()
        assertThat(environment.getGlobal("version").toint()).isEqualTo(1311)
        assertThat(environment.getGlobal("versionString").tojstring()).isEqualTo("11.00")
        assertThat(environment.getGlobal("os").toint()).isEqualTo(2)
        assertThat(fake.calls["getReward"]).isEqualTo(listOf(player, 1000L, true))
        assertThat(fake.calls["removeReward"]).isEqualTo(listOf(player, 1000L))
        assertThat(fake.calls["setDailyReward"]).isEqualTo(listOf(player, 2))
    }

    @Test
    fun `remainder xp boost bindings read and write every boost`() {
        val (environment, fake) = installed()
        environment.loadChunk(
            """
            base = p:getBaseXpGain()
            baseSet = p:setBaseXpGain(150)
            voucher = p:getVoucherXpBoost()
            voucherSet = p:setVoucherXpBoost(25)
            grinding = p:getGrindingXpBoost()
            grindingSet = p:setGrindingXpBoost(15)
            percent = p:getXpBoostPercent()
            percentSet = p:setXpBoostPercent(60)
            time = p:getXpBoostTime()
            timeSet = p:setXpBoostTime(7200)
            stamina = p:getStaminaXpBoost()
            staminaSet = p:setStaminaXpBoost(35)
            """.trimIndent(),
        )

        val player = fake.player()
        assertThat(environment.getGlobal("base").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("voucher").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("grinding").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("percent").toint()).isEqualTo(50)
        assertThat(environment.getGlobal("time").toint()).isEqualTo(3600)
        assertThat(environment.getGlobal("stamina").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("baseSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("voucherSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("grindingSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("percentSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("timeSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("staminaSet").toboolean()).isTrue()
        assertThat(fake.calls["setBaseXpGain"]).isEqualTo(listOf(player, 150))
        assertThat(fake.calls["setXpBoostTime"]).isEqualTo(listOf(player, 7200))
    }

    @Test
    fun `remainder bindings return nil for a non-player receiver`() {
        val (environment, _) = installed()
        environment.loadChunk(
            """
            local m = rawgetmetatable('Player')
            nilChecks =
                (m.getPronoun(nil) == nil) and
                (m.setPronoun(nil, 1) == nil) and
                (m.addOutfit(nil, 136) == nil) and
                (m.addOutfitAddon(nil, 136, 1) == nil) and
                (m.removeOutfit(nil, 136) == nil) and
                (m.removeOutfitAddon(nil, 136, 1) == nil) and
                (m.hasOutfit(nil, 136) == nil) and
                (m.sendOutfitWindow(nil) == nil) and
                (m.addMount(nil, 5) == nil) and
                (m.removeMount(nil, 5) == nil) and
                (m.hasMount(nil, 5) == nil) and
                (m.addFamiliar(nil, 31) == nil) and
                (m.removeFamiliar(nil, 31) == nil) and
                (m.hasFamiliar(nil, 31) == nil) and
                (m.setFamiliarLooktype(nil, 31) == nil) and
                (m.getFamiliarLooktype(nil) == nil) and
                (m.getReward(nil, 1) == nil) and
                (m.removeReward(nil, 1) == nil) and
                (m.getRewardList(nil) == nil) and
                (m.setDailyReward(nil, 1) == nil) and
                (m.getClient(nil) == nil) and
                (m.getBaseXpGain(nil) == nil) and
                (m.setBaseXpGain(nil, 1) == nil) and
                (m.getXpBoostTime(nil) == nil) and
                (m.setXpBoostTime(nil, 1) == nil)
            """.trimIndent(),
        )
        assertThat(environment.getGlobal("nilChecks").toboolean()).isTrue()
    }

    @Test
    fun `remainder Player methods register on the Player Monster and Npc metatables`() {
        val (environment, _) = installed()
        val names = listOf(
            "addOutfit", "addOutfitAddon", "removeOutfit", "removeOutfitAddon", "hasOutfit",
            "sendOutfitWindow", "addMount", "removeMount", "hasMount", "addFamiliar",
            "removeFamiliar", "hasFamiliar", "setFamiliarLooktype", "getFamiliarLooktype",
            "getPronoun", "setPronoun", "getReward", "removeReward", "getRewardList",
            "setDailyReward", "getClient", "getBaseXpGain", "setBaseXpGain", "getVoucherXpBoost",
            "setVoucherXpBoost", "getGrindingXpBoost", "setGrindingXpBoost", "getXpBoostPercent",
            "setXpBoostPercent", "getXpBoostTime", "setXpBoostTime", "getStaminaXpBoost",
            "setStaminaXpBoost",
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
    fun `real Player answers the remainder surface through WorldPlayerRemainderOperations`() {
        val environment = env()
        PlayerFunctions.install(
            environment,
            StubPlayerOperations(),
            WorldPlayerBatch3Operations(),
            WorldPlayerRemainderOperations(),
        )
        val player = Player(
            id = 77L,
            name = "Remainder Hero",
            maxHealth = 150,
            health = 150,
            position = Position(10, 10, 7),
        )
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            pronoun = p:getPronoun()
            pronounSet = p:setPronoun(3)
            pronounAfter = p:getPronoun()
            outfitAdded = p:addOutfit(136, 1)
            outfitRemoved = p:removeOutfit(136)
            addonMissing = p:removeOutfitAddon(136, 1)
            addonAdded = p:addOutfitAddon(137, 3)
            addonRemoved = p:removeOutfitAddon(137, 3)
            unknownOutfit = p:addOutfit("no such outfit")
            familiarAdded = p:addFamiliar(31)
            familiarAfterAdd = p:hasFamiliar(31)
            familiarRemoved = p:removeFamiliar(31)
            familiarAfterRemove = p:removeFamiliar(31)
            looktypeSet = p:setFamiliarLooktype(31)
            looktype = p:getFamiliarLooktype()
            rewardMissing = p:getReward(1000)
            reward = p:getReward(1000, true)
            rewardList = p:getRewardList()
            rewardCount = #rewardList
            rewardRemoved = p:removeReward(1000)
            rewardListAfter = #p:getRewardList()
            dailySet = p:setDailyReward(2)
            baseSet = p:setBaseXpGain(150)
            base = p:getBaseXpGain()
            voucherSet = p:setVoucherXpBoost(25)
            voucher = p:getVoucherXpBoost()
            grindingSet = p:setGrindingXpBoost(15)
            grinding = p:getGrindingXpBoost()
            percentSet = p:setXpBoostPercent(60)
            percent = p:getXpBoostPercent()
            timeSet = p:setXpBoostTime(7200)
            time = p:getXpBoostTime()
            staminaSet = p:setStaminaXpBoost(35)
            stamina = p:getStaminaXpBoost()
            unknownMount = p:addMount("no such mount")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("pronoun").toint()).isZero()
        assertThat(environment.getGlobal("pronounSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("pronounAfter").toint()).isEqualTo(3)
        assertThat(player.getPronoun()).isEqualTo(PlayerPronoun_t.PLAYERPRONOUN_HE)
        assertThat(environment.getGlobal("outfitAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("outfitRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("addonMissing").toboolean()).isFalse()
        assertThat(environment.getGlobal("addonAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("addonRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknownOutfit").isnil()).isTrue()
        assertThat(environment.getGlobal("familiarAdded").toboolean()).isTrue()
        assertThat(environment.getGlobal("familiarAfterAdd").toboolean()).isFalse()
        assertThat(environment.getGlobal("familiarRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("familiarAfterRemove").toboolean()).isFalse()
        assertThat(environment.getGlobal("looktypeSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("looktype").toint()).isEqualTo(31)
        assertThat(environment.getGlobal("rewardMissing").toboolean()).isFalse()
        assertThat(environment.getGlobal("reward").touserdata()).isNotNull()
        assertThat(environment.getGlobal("rewardCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("rewardRemoved").toboolean()).isTrue()
        assertThat(environment.getGlobal("rewardListAfter").toint()).isZero()
        assertThat(environment.getGlobal("dailySet").toboolean()).isTrue()
        assertThat(environment.getGlobal("baseSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("base").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("voucher").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("grinding").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("percent").toint()).isEqualTo(60)
        assertThat(environment.getGlobal("time").toint()).isEqualTo(7200)
        assertThat(environment.getGlobal("stamina").toint()).isEqualTo(35)
        assertThat(environment.getGlobal("unknownMount").isnil()).isTrue()
        assertThat(player.features.dailyReward.isDailyReward).isEqualTo(2)
    }

    @Test
    fun `real mount registry answers mount state through WorldPlayerRemainderOperations`() {
        val environment = env()
        val mounts = Mounts()
        mounts.loadFromXml(
            """
            <mounts>
                <mount id="5" clientid="20" name="Widow Queen" speed="10" premium="false" type="fire"/>
            </mounts>
            """.trimIndent(),
        )
        val operations = WorldPlayerRemainderOperations(mountDefinitions = mounts)
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations(), operations)
        val player = Player(
            id = 78L,
            name = "Mount Hero",
            maxHealth = 150,
            health = 150,
            position = Position(10, 10, 7),
        )
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            byId = p:addMount(5)
            byName = p:addMount("Widow Queen")
            unknown = p:addMount("no such mount")
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").toboolean()).isFalse()
        assertThat(environment.getGlobal("byName").toboolean()).isFalse()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(operations.mountIdByName("Widow Queen")).isEqualTo(5)
        assertThat(operations.mountIdByName("no such mount")).isZero()
    }
}
