package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the Canary `Npc` binding group. Each test installs
 * [NpcFunctions] with a fake [NpcOperations] seam and drives it through real Lua
 * chunks using the exact method names from
 * src/lua/functions/creatures/npc/npc_functions.cpp.
 */
class NpcFunctionsTest {

    private class FakeNpc
    private class FakePlayer
    private class FakeCreature

    private class FakeOps : NpcOperations {
        val npcsById = mutableMapOf<Long, Any>()
        val npcsByName = mutableMapOf<String, Any>()
        var currency = 3031
        var speechBubble = 1
        var name = "Tom"
        var masterPos: Position? = null
        var placeResult: Any? = null
        val placed = mutableListOf<Triple<Position, Boolean, Boolean>>()
        var sayResult = true
        var lastSay: Triple<String, Int, Boolean>? = null
        var lastSayTarget: Any? = null
        var lastSayPosition: Position? = null
        var turnedTo: Any? = null
        var lastStopEventWalk: Boolean? = null
        var lastTopicId: Int? = null
        val interactions = mutableMapOf<Long, Int>()
        var talkRange = true
        var lastRange: Int? = null
        var shopOpen = true
        var shopTableResult = true
        var lastShopItems: List<ShopData>? = null
        var closedShop = false
        var merchant = true
        val shopItems = mutableListOf<ShopData>()
        var followResult = true
        var followed: Any? = null
        var sellResult = true
        var lastSell: List<Any?>? = null
        var distance: Int? = 3
        var lastUid: Int? = null

        override fun createById(id: Long): Any? = npcsById[id]

        override fun createByName(name: String): Any? = npcsByName[name]

        override fun isNpc(value: Any?): Boolean = value is FakeNpc

        override fun getCurrency(npc: Any): Int? = currency

        override fun setCurrency(npc: Any, currency: Int): Boolean? {
            this.currency = currency
            return true
        }

        override fun getSpeechBubble(npc: Any): Int? = speechBubble

        override fun setSpeechBubble(npc: Any, speechBubble: Int): Boolean? {
            this.speechBubble = speechBubble
            return true
        }

        override fun getId(npc: Any): Long? = (npc as? FakeNpc)?.let { 42L }

        override fun getName(npc: Any): String? = name

        override fun setName(npc: Any, name: String): Boolean? {
            this.name = name
            return true
        }

        override fun setMasterPos(npc: Any, position: Position): Boolean? {
            masterPos = position
            return true
        }

        override fun place(npc: Any, position: Position, extended: Boolean, force: Boolean): Any? {
            placed += Triple(position, extended, force)
            return placeResult
        }

        override fun say(npc: Any, text: String, type: Int, ghost: Boolean, target: Any?, position: Position?): Boolean? {
            lastSay = Triple(text, type, ghost)
            lastSayTarget = target
            lastSayPosition = position
            return sayResult
        }

        override fun turnToCreature(npc: Any, creature: Any?, stopEventWalk: Boolean): Boolean? {
            turnedTo = creature
            lastStopEventWalk = stopEventWalk
            return creature != null
        }

        override fun setPlayerInteraction(npc: Any, creature: Any?, topicId: Int): Boolean? {
            val id = (creature as? FakePlayer)?.let { 7L } ?: return false
            interactions[id] = topicId
            return true
        }

        override fun removePlayerInteraction(npc: Any, creature: Any?): Boolean? {
            val id = (creature as? FakePlayer)?.let { 7L } ?: return false
            interactions.remove(id)
            return true
        }

        override fun isInteractingWithPlayer(npc: Any, creature: Any?): Boolean? =
            interactions.containsKey((creature as? FakePlayer)?.let { 7L })

        override fun isPlayerInteractingOnTopic(npc: Any, creature: Any?, topicId: Int): Boolean? =
            interactions[(creature as? FakePlayer)?.let { 7L }] == topicId

        override fun isInTalkRange(npc: Any, position: Position, range: Int): Boolean? {
            lastRange = range
            return talkRange
        }

        override fun openShopWindow(npc: Any, player: Any?): Boolean? = shopOpen

        override fun openShopWindowTable(npc: Any, player: Any?, items: List<ShopData>): Boolean? {
            lastShopItems = items
            return shopTableResult
        }

        override fun closeShopWindow(npc: Any, player: Any?): Boolean? {
            closedShop = true
            return true
        }

        override fun getShopItem(npc: Any, playerGuid: Long): List<ShopData>? = shopItems.toList()

        override fun isMerchant(npc: Any, playerGuid: Long): Boolean? = merchant

        override fun move(npc: Any, direction: Int): Boolean? = true

        override fun turn(npc: Any, direction: Int): Boolean? = true

        override fun follow(npc: Any, player: Any?): Boolean? {
            followed = player
            return followResult
        }

        override fun sellItem(
            npc: Any,
            player: Any?,
            itemId: Int,
            amount: Double,
            subType: Int,
            actionId: Int,
            ignoreCap: Boolean,
            inBackpacks: Boolean,
        ): Boolean? {
            lastSell = listOf(npc, player, itemId, amount, subType, actionId, ignoreCap, inBackpacks)
            return sellResult
        }

        override fun getDistanceTo(npc: Any, uid: Int): Int? {
            lastUid = uid
            return distance
        }
    }

    private fun installed(fake: FakeOps = FakeOps()): Pair<LuaEnvironment, FakeOps> {
        val environment = LuaEnvironment().apply { initState() }
        NpcFunctions.install(environment, fake)
        return environment to fake
    }

    private fun withNpc(environment: LuaEnvironment, npc: FakeNpc = FakeNpc()): FakeNpc {
        environment.globals.set("npc", environment.userdata.wrap("Npc", npc))
        return npc
    }

    @Test
    fun `Npc constructor resolves ids names and npc userdata`() {
        val (environment, fake) = installed()
        val tom = FakeNpc()
        fake.npcsById[5] = tom
        fake.npcsByName["Tom"] = tom
        environment.globals.set("tom", environment.userdata.wrap("Npc", tom))
        val other = object {}

        environment.loadChunk("byId = Npc(5)")
        environment.loadChunk("byName = Npc('Tom')")
        environment.loadChunk("byUserdata = Npc(tom)")
        environment.globals.set("other", environment.userdata.wrap("Player", other))
        environment.loadChunk("byOther = Npc(other)")
        environment.loadChunk("missing = Npc('Nobody')")

        assertThat(environment.getGlobal("byId").touserdata()).isSameAs(tom)
        assertThat(environment.getGlobal("byName").touserdata()).isSameAs(tom)
        assertThat(environment.getGlobal("byUserdata").touserdata()).isSameAs(tom)
        assertThat(environment.getGlobal("byOther").isnil()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Npc identity getters use the Canary names`() {
        val (environment, _) = installed()
        withNpc(environment)

        environment.loadChunk(
            """
            isNpc = npc:isNpc()
            id = npc:getId()
            name = npc:getName()
            renamed = npc:setName('Tom')
            currency = npc:getCurrency()
            setCurrency = npc:setCurrency(2160)
            bubble = npc:getSpeechBubble()
            setBubble = npc:setSpeechBubble(3)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("isNpc").toboolean()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Tom")
        assertThat(environment.getGlobal("renamed").isnil()).isTrue()
        assertThat(environment.getGlobal("currency").toint()).isEqualTo(3031)
        assertThat(environment.getGlobal("setCurrency").isnil()).isTrue()
        assertThat(environment.getGlobal("bubble").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("setBubble").isnil()).isTrue()
    }

    @Test
    fun `Npc setMasterPos stores the position`() {
        val (environment, fake) = installed()
        withNpc(environment)
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(10, 20, 7)))

        environment.loadChunk("ok = npc:setMasterPos(pos)")

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(fake.masterPos).isEqualTo(Position(10, 20, 7))
    }

    @Test
    fun `Npc place returns the npc userdata or nil`() {
        val (environment, fake) = installed()
        val npc = withNpc(environment)
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(10, 20, 7)))
        fake.placeResult = npc

        environment.loadChunk("placed = npc:place(pos)")
        environment.loadChunk("placedArgs = npc:place(pos, true, false)")

        assertThat(environment.getGlobal("placed").touserdata()).isSameAs(npc)
        assertThat(fake.placed).containsExactly(
            Triple(Position(10, 20, 7), false, true),
            Triple(Position(10, 20, 7), true, false),
        )
    }

    @Test
    fun `Npc place returns nil when the world rejects the position`() {
        val (environment, _) = installed()
        withNpc(environment)
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(10, 20, 7)))

        environment.loadChunk("placed = npc:place(pos)")

        assertThat(environment.getGlobal("placed").isnil()).isTrue()
    }

    @Test
    fun `Npc say forwards type ghost target and position`() {
        val (environment, fake) = installed()
        withNpc(environment)
        val player = FakePlayer()
        environment.globals.set("player", environment.userdata.wrap("Player", player))
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(9, 9, 7)))

        environment.loadChunk("plain = npc:say('hi')")

        assertThat(environment.getGlobal("plain").toboolean()).isTrue()
        assertThat(fake.lastSay).isEqualTo(Triple("hi", 10, false))

        environment.loadChunk("full = npc:say('hi', 1, true, player, pos)")

        assertThat(environment.getGlobal("full").toboolean()).isTrue()
        assertThat(fake.lastSay).isEqualTo(Triple("hi", 1, true))
        assertThat(fake.lastSayTarget).isSameAs(player)
        assertThat(fake.lastSayPosition).isEqualTo(Position(9, 9, 7))
    }

    @Test
    fun `Npc say rejects an invalid explicit position`() {
        val (environment, _) = installed()
        withNpc(environment)
        environment.globals.set("player", environment.userdata.wrap("Player", FakePlayer()))
        environment.globals.set("bad", environment.userdata.wrap("Position", Position(0, 10, 7)))

        environment.loadChunk("said = npc:say('hi', 1, false, player, bad)")

        assertThat(environment.getGlobal("said").toboolean()).isFalse()
    }

    @Test
    fun `Npc interaction methods use the player id`() {
        val (environment, _) = installed()
        withNpc(environment)
        environment.globals.set("player", environment.userdata.wrap("Player", FakePlayer()))

        environment.loadChunk(
            """
            before = npc:isInteractingWithPlayer(player)
            set = npc:setPlayerInteraction(player)
            after = npc:isInteractingWithPlayer(player)
            topic = npc:isPlayerInteractingOnTopic(player)
            removed = npc:removePlayerInteraction(player)
            gone = npc:isInteractingWithPlayer(player)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").toboolean()).isFalse()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toboolean()).isTrue()
        assertThat(environment.getGlobal("topic").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("gone").toboolean()).isFalse()
    }

    @Test
    fun `Npc isInTalkRange and turnToCreature use Canary defaults`() {
        val (environment, fake) = installed()
        withNpc(environment)
        val creature = FakeCreature()
        environment.globals.set("creature", environment.userdata.wrap("Creature", creature))
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(10, 20, 7)))

        environment.loadChunk("range = npc:isInTalkRange(pos)")
        environment.loadChunk("range3 = npc:isInTalkRange(pos, 3)")
        environment.loadChunk("turned = npc:turnToCreature(creature)")
        environment.loadChunk("turnedKeep = npc:turnToCreature(creature, false)")

        assertThat(environment.getGlobal("range").toboolean()).isTrue()
        assertThat(fake.lastRange).isEqualTo(3)
        assertThat(environment.getGlobal("range3").toboolean()).isTrue()
        assertThat(environment.getGlobal("turned").toboolean()).isTrue()
        assertThat(environment.getGlobal("turnedKeep").toboolean()).isTrue()
        assertThat(fake.lastStopEventWalk).isFalse()
        assertThat(fake.turnedTo).isSameAs(creature)
    }

    @Test
    fun `Npc shop methods forward to the seam`() {
        val (environment, fake) = installed()
        withNpc(environment)
        fake.shopItems += ShopData(itemId = 2160, itemBuyPrice = 100)
        environment.globals.set("player", environment.userdata.wrap("Player", FakePlayer()))
        environment.globals.set(
            "items",
            org.luaj.vm2.LuaTable().also {
                it.set(
                    1,
                    org.luaj.vm2.LuaTable().also { row ->
                        row.set("clientId", 2160)
                        row.set("subType", 0)
                        row.set("buy", 100)
                        row.set("sell", 50)
                        row.set("storageKey", 7)
                        row.set("storageValue", 3)
                    },
                )
            },
        )

        environment.loadChunk("opened = npc:openShopWindow(player)")
        environment.loadChunk("tableOpened = npc:openShopWindowTable(player, items)")
        environment.loadChunk("closed = npc:closeShopWindow(player)")
        environment.loadChunk("merchant = npc:isMerchant(42)")
        environment.loadChunk("item = npc:getShopItem(42)")

        assertThat(environment.getGlobal("opened").toboolean()).isTrue()
        assertThat(environment.getGlobal("tableOpened").toboolean()).isTrue()
        assertThat(fake.lastShopItems).hasSize(1)
        assertThat(fake.lastShopItems!![0].itemId).isEqualTo(2160)
        assertThat(fake.lastShopItems!![0].itemBuyPrice).isEqualTo(100)
        assertThat(fake.lastShopItems!![0].itemSellPrice).isEqualTo(50)
        assertThat(fake.lastShopItems!![0].itemStorageKey).isEqualTo(7)
        assertThat(fake.lastShopItems!![0].itemStorageValue).isEqualTo(3)
        assertThat(environment.getGlobal("closed").toboolean()).isTrue()
        assertThat(fake.closedShop).isTrue()
        assertThat(environment.getGlobal("merchant").toboolean()).isTrue()
        assertThat(environment.getGlobal("item").toboolean()).isTrue()
    }

    @Test
    fun `Npc follow move turn sellItem and getDistanceTo use the Canary names`() {
        val (environment, fake) = installed()
        withNpc(environment)
        val player = FakePlayer()
        environment.globals.set("player", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            moved = npc:move(1)
            turned = npc:turn(2)
            followed = npc:follow(player)
            sold = npc:sellItem(player, 2160, 3)
            soldFull = npc:sellItem(player, 2160, 3, 1, 5, true, true)
            distance = npc:getDistanceTo(77)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("moved").isnil()).isTrue()
        assertThat(environment.getGlobal("turned").isnil()).isTrue()
        assertThat(environment.getGlobal("followed").toboolean()).isTrue()
        assertThat(fake.followed).isSameAs(player)
        assertThat(environment.getGlobal("sold").toboolean()).isTrue()
        assertThat(fake.lastSell?.get(2)).isEqualTo(2160)
        assertThat(fake.lastSell?.get(6)).isEqualTo(true)
        assertThat(fake.lastSell?.get(7)).isEqualTo(true)
        assertThat(environment.getGlobal("distance").toint()).isEqualTo(3)
        assertThat(fake.lastUid).isEqualTo(77)
    }

    @Test
    fun `Npc getDistanceTo is nil when the thing is missing`() {
        val (environment, fake) = installed()
        withNpc(environment)
        fake.distance = null

        environment.loadChunk("distance = npc:getDistanceTo(77)")

        assertThat(environment.getGlobal("distance").isnil()).isTrue()
    }
}
