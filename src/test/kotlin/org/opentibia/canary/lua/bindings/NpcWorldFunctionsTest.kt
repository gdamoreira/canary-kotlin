package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock

/**
 * Integration tests proving the `Npc`/`NpcType` groups answer through the real
 * [WorldLuaOperations] seams backed by the Kotlin [Npc]/[NpcType] domain objects.
 */
class NpcWorldFunctionsTest {

    private fun env(world: WorldLuaOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    private fun npc(id: Long = 10, name: String = "Tom", type: NpcType? = null): Npc =
        Npc(id = id, name = name, position = Position(5, 5, 7), npcType = type)

    private fun player(id: Long = 7, name: String = "Hero"): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(6, 6, 7))

    @Test
    fun `World NpcType constructor resolves the catalog and name setters mutate the domain`() {
        val type = NpcType("Tom")
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))

        environment.loadChunk(
            """
            t = NpcType('Tom')
            missing = NpcType('Nobody')
            name = t:name()
            renamed = t:name('Banker')
            description = t:nameDescription('a banker')
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("t").touserdata()).isSameAs(type)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Tom")
        assertThat(environment.getGlobal("renamed").toboolean()).isTrue()
        assertThat(type.name).isEqualTo("Banker")
        assertThat(environment.getGlobal("description").toboolean()).isTrue()
        assertThat(type.nameDescription).isEqualTo("a banker")
    }

    @Test
    fun `World NpcType numeric and light attributes mutate the shared info`() {
        val type = NpcType("Tom")
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))
        environment.globals.set("t", environment.userdata.wrap("NpcType", type))

        environment.loadChunk(
            """
            health = t:health()
            setHealth = t:health(250)
            maxHealth = t:maxHealth()
            setMaxHealth = t:maxHealth(300)
            speed = t:baseSpeed()
            setSpeed = t:baseSpeed(80)
            interval = t:walkInterval()
            setInterval = t:walkInterval(1500)
            radius = t:walkRadius()
            setRadius = t:walkRadius(4)
            level, color = t:light()
            setLight = t:light(9, 4)
            bubble = t:speechBubble()
            setBubble = t:speechBubble(2)
            currency = t:currency()
            setCurrency = t:currency(2160)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("health").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setHealth").toboolean()).isTrue()
        assertThat(type.info.health).isEqualTo(250)
        assertThat(environment.getGlobal("maxHealth").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setMaxHealth").toboolean()).isTrue()
        assertThat(type.info.healthMax).isEqualTo(300)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("setSpeed").toboolean()).isTrue()
        assertThat(type.info.baseSpeed).isEqualTo(80)
        assertThat(environment.getGlobal("interval").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("setInterval").toboolean()).isTrue()
        assertThat(type.info.walkInterval).isEqualTo(1500)
        assertThat(environment.getGlobal("radius").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("setRadius").toboolean()).isTrue()
        assertThat(type.info.walkRadius).isEqualTo(4)
        assertThat(environment.getGlobal("level").toint()).isZero()
        assertThat(environment.getGlobal("color").toint()).isEqualTo(215)
        assertThat(environment.getGlobal("setLight").toboolean()).isTrue()
        assertThat(type.info.light.level).isEqualTo(4)
        assertThat(type.info.light.color).isEqualTo(9)
        assertThat(environment.getGlobal("setBubble").toboolean()).isTrue()
        assertThat(type.info.speechBubble).isEqualTo(2)
        assertThat(environment.getGlobal("setCurrency").toboolean()).isTrue()
        assertThat(type.info.currencyId).isEqualTo(2160)
    }

    @Test
    fun `World NpcType voices events and sounds use the domain info`() {
        val type = NpcType("Tom")
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))
        environment.globals.set("t", environment.userdata.wrap("NpcType", type))

        environment.loadChunk(
            """
            added = t:addVoice('Hello', 1200, 15, true)
            voices = t:getVoices()
            registered = t:registerEvent('NpcBanker')
            events = t:getCreatureEvents()
            eventType = t:eventType(5)
            sound = t:addSound(5)
            sounds = t:getSounds()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(type.info.yellSpeedTicks).isEqualTo(1200)
        assertThat(type.info.yellChance).isEqualTo(15)
        assertThat(type.info.voiceVector).hasSize(1)
        assertThat(environment.getGlobal("voices").get(1).get("text").tojstring()).isEqualTo("Hello")
        assertThat(environment.getGlobal("voices").get(1).get("yellText").toboolean()).isTrue()
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(type.info.scripts).containsExactly("NpcBanker")
        assertThat(environment.getGlobal("events").get(1).tojstring()).isEqualTo("NpcBanker")
        assertThat(environment.getGlobal("eventType").toboolean()).isTrue()
        assertThat(type.info.eventType.value).isEqualTo(5)
        assertThat(environment.getGlobal("sound").toboolean()).isTrue()
        assertThat(type.info.soundVector).hasSize(1)
        assertThat(environment.getGlobal("sounds").get(1).toint()).isEqualTo(5)
    }

    @Test
    fun `World NpcType addShopItem appends to the domain shop vector and canSpawn delegates`() {
        val type = NpcType("Tom")
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))

        environment.loadChunk(
            """
            t = NpcType('Tom')
            shop = Shop()
            shop:setId(2160)
            shop:setNameItem('crystal coin')
            shop:setBuyPrice(100)
            added = t:addShopItem(shop)
            underground = t:respawnTypeIsUnderground()
            setUnderground = t:respawnTypeIsUnderground(1)
            period = t:respawnTypePeriod()
            setPeriod = t:respawnTypePeriod(2)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(type.info.shopItemVector).hasSize(1)
        assertThat(type.info.shopItemVector[0].itemId).isEqualTo(2160)
        assertThat(type.info.shopItemVector[0].itemBuyPrice).isEqualTo(100)
        assertThat(environment.getGlobal("underground").toint()).isZero()
        assertThat(environment.getGlobal("setUnderground").toboolean()).isTrue()
        assertThat(type.info.respawnType.underground).isTrue()
        assertThat(environment.getGlobal("setPeriod").toboolean()).isTrue()
        assertThat(type.info.respawnType.period.value).isEqualTo(2)
    }

    @Test
    fun `World Npc identity currency and interaction state use the domain`() {
        val type = NpcType("Tom")
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))
        val tom = npc(type = type)
        val hero = player()
        environment.globals.set("npc", environment.userdata.wrap("Npc", tom))
        environment.globals.set("player", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            isNpc = npc:isNpc()
            id = npc:getId()
            name = npc:getName()
            currency = npc:getCurrency()
            setCurrency = npc:setCurrency(2160)
            currencyAfter = npc:getCurrency()
            bubble = npc:getSpeechBubble()
            setBubble = npc:setSpeechBubble(3)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("isNpc").toboolean()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Tom")
        assertThat(environment.getGlobal("currency").toint()).isEqualTo(3031)
        assertThat(environment.getGlobal("setCurrency").isnil()).isTrue()
        assertThat(environment.getGlobal("currencyAfter").toint()).isEqualTo(2160)
        assertThat(type.info.currencyId).isEqualTo(2160)
        assertThat(environment.getGlobal("setBubble").isnil()).isTrue()
        assertThat(type.info.speechBubble).isEqualTo(3)
    }

    @Test
    fun `World Npc interaction and talk range use the domain`() {
        val environment = env(WorldLuaOperations())
        val tom = npc()
        val hero = player()
        environment.globals.set("npc", environment.userdata.wrap("Npc", tom))
        environment.globals.set("player", environment.userdata.wrap("Player", hero))
        environment.globals.set("near", environment.userdata.wrap("Position", Position(6, 6, 7)))
        environment.globals.set("far", environment.userdata.wrap("Position", Position(20, 20, 7)))

        environment.loadChunk(
            """
            set = npc:setPlayerInteraction(player, 4)
            interacting = npc:isInteractingWithPlayer(player)
            topic = npc:isPlayerInteractingOnTopic(player, 4)
            wrongTopic = npc:isPlayerInteractingOnTopic(player, 9)
            range = npc:isInTalkRange(near)
            awayRange = npc:isInTalkRange(far)
            removed = npc:removePlayerInteraction(player)
            gone = npc:isInteractingWithPlayer(player)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("interacting").toboolean()).isTrue()
        assertThat(environment.getGlobal("topic").toboolean()).isTrue()
        assertThat(environment.getGlobal("wrongTopic").toboolean()).isFalse()
        assertThat(environment.getGlobal("range").toboolean()).isTrue()
        assertThat(environment.getGlobal("awayRange").toboolean()).isFalse()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("gone").toboolean()).isFalse()
    }

    @Test
    fun `World Npc turnToCreature move and turn update the direction`() {
        val environment = env(WorldLuaOperations())
        val tom = npc()
        val hero = player()
        environment.globals.set("npc", environment.userdata.wrap("Npc", tom))
        environment.globals.set("player", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            turned = npc:turnToCreature(player)
            moved = npc:move(1)
            rotated = npc:turn(2)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("turned").toboolean()).isTrue()
        assertThat(environment.getGlobal("moved").isnil()).isTrue()
        assertThat(environment.getGlobal("rotated").isnil()).isTrue()
        assertThat(tom.direction.ordinal).isEqualTo(2)
    }

    @Test
    fun `World Npc shop methods drive the domain shop vector`() {
        val type = NpcType("Tom")
        type.info.shopItemVector.add(
            ShopBlock(itemId = 2160, itemName = "crystal coin", itemBuyPrice = 100, itemSellPrice = 50),
        )
        val environment = env(WorldLuaOperations(npcTypesByName = mapOf("Tom" to type)))
        val tom = npc(type = type)
        val hero = player()
        environment.globals.set("npc", environment.userdata.wrap("Npc", tom))
        environment.globals.set("player", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            merchant = npc:isMerchant()
            item = npc:getShopItem()
            opened = npc:openShopWindow(player)
            closed = npc:closeShopWindow(player)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("merchant").toboolean()).isTrue()
        assertThat(environment.getGlobal("item").toboolean()).isTrue()
        assertThat(environment.getGlobal("opened").toboolean()).isTrue()
        assertThat(environment.getGlobal("closed").toboolean()).isTrue()
    }

    @Test
    fun `World Npc follow say distance and place inherit the documented gaps`() {
        val environment = env(WorldLuaOperations())
        val tom = npc()
        val hero = player()
        environment.globals.set("npc", environment.userdata.wrap("Npc", tom))
        environment.globals.set("player", environment.userdata.wrap("Player", hero))
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(6, 6, 7)))

        environment.loadChunk(
            """
            followed = npc:follow(player)
            said = npc:say('hi')
            distance = npc:getDistanceTo(77)
            placed = npc:place(pos)
            sold = npc:sellItem(player, 2160, 1)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("followed").toboolean()).isFalse()
        assertThat(environment.getGlobal("said").toboolean()).isFalse()
        assertThat(environment.getGlobal("distance").isnil()).isTrue()
        assertThat(environment.getGlobal("placed").isnil()).isTrue()
        assertThat(environment.getGlobal("sold").toboolean()).isTrue()
    }
}
