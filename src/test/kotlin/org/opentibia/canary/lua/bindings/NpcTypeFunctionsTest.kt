package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Transcript-style tests for the Canary `NpcType` binding group, driven through real
 * Lua chunks with a fake [NpcTypeOperations] seam and the exact method names from
 * src/lua/functions/creatures/npc/npc_type_functions.cpp.
 */
class NpcTypeFunctionsTest {

    private class FakeNpcType

    private class FakeOps : NpcTypeOperations {
        val typesByName = mutableMapOf<String, Any>()
        val booleans = mutableMapOf<String, Boolean>()
        var name = "Tom"
        var nameDescription = "Tom the banker"
        var health = 100
        var maxHealth = 100
        var spawnResult = true
        var lastSpawnPosition: Position? = null
        val voices = mutableListOf<MonsterVoiceInfo>()
        var lastVoice: List<Any?>? = null
        val events = mutableListOf<String>()
        var lastEventType: Int? = null
        var callbackResult: Boolean? = true
        var outfit = Outfit()
        var light = LightInfo(level = 3, color = 7)
        var lastLight: Pair<Int, Int>? = null
        val ints = mutableMapOf<String, Int>()
        var respawnUnderground = 0
        var shopItems = mutableListOf<ShopData>()
        val sounds = mutableListOf<Int>()

        private fun typeOrNull(npcType: Any): FakeNpcType? = npcType as? FakeNpcType

        override fun create(name: String): Any? = typesByName[name]

        override fun isPushable(npcType: Any): Boolean? =
            if (typeOrNull(npcType) == null) null else booleans["pushable"]

        override fun setPushable(npcType: Any, value: Boolean): Boolean? {
            booleans["pushable"] = value
            return true
        }

        override fun floorChange(npcType: Any): Boolean? = booleans["floorChange"]

        override fun setFloorChange(npcType: Any, value: Boolean): Boolean? {
            booleans["floorChange"] = value
            return true
        }

        override fun canSpawn(npcType: Any, position: Position): Boolean? {
            lastSpawnPosition = position
            return spawnResult
        }

        override fun canPushItems(npcType: Any): Boolean? = booleans["pushItems"]

        override fun setCanPushItems(npcType: Any, value: Boolean): Boolean? {
            booleans["pushItems"] = value
            return true
        }

        override fun canPushCreatures(npcType: Any): Boolean? = booleans["pushCreatures"]

        override fun setCanPushCreatures(npcType: Any, value: Boolean): Boolean? {
            booleans["pushCreatures"] = value
            return true
        }

        override fun name(npcType: Any): String? = if (typeOrNull(npcType) == null) null else name

        override fun setName(npcType: Any, value: String): Boolean? {
            name = value
            return true
        }

        override fun nameDescription(npcType: Any): String? = nameDescription

        override fun setNameDescription(npcType: Any, value: String): Boolean? {
            nameDescription = value
            return true
        }

        override fun health(npcType: Any): Int? = health

        override fun setHealth(npcType: Any, value: Int): Boolean? {
            health = value
            return true
        }

        override fun maxHealth(npcType: Any): Int? = maxHealth

        override fun setMaxHealth(npcType: Any, value: Int): Boolean? {
            maxHealth = value
            return true
        }

        override fun getVoices(npcType: Any): List<MonsterVoiceInfo>? = voices.toList()

        override fun addVoice(npcType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? {
            lastVoice = listOf(text, interval, chance, yell)
            voices += MonsterVoiceInfo(text = text, yellText = yell)
            return true
        }

        override fun getCreatureEvents(npcType: Any): List<String>? = events.toList()

        override fun registerEvent(npcType: Any, name: String): Boolean? {
            events += name
            return true
        }

        override fun setEventType(npcType: Any, event: Int): Boolean? {
            lastEventType = event
            return true
        }

        override fun loadCallback(npcType: Any): Boolean? = callbackResult

        override fun outfit(npcType: Any): Outfit? = outfit

        override fun setOutfit(npcType: Any, outfit: Outfit): Boolean? {
            this.outfit = outfit
            return true
        }

        override fun baseSpeed(npcType: Any): Int? = ints["baseSpeed"]

        override fun setBaseSpeed(npcType: Any, value: Int): Boolean? {
            ints["baseSpeed"] = value
            return true
        }

        override fun walkInterval(npcType: Any): Int? = ints["walkInterval"]

        override fun setWalkInterval(npcType: Any, value: Int): Boolean? {
            ints["walkInterval"] = value
            return true
        }

        override fun walkRadius(npcType: Any): Int? = ints["walkRadius"]

        override fun setWalkRadius(npcType: Any, value: Int): Boolean? {
            ints["walkRadius"] = value
            return true
        }

        override fun light(npcType: Any): LightInfo? = light

        override fun setLight(npcType: Any, color: Int, level: Int): Boolean? {
            lastLight = color to level
            return true
        }

        override fun yellChance(npcType: Any): Int? = ints["yellChance"]

        override fun setYellChance(npcType: Any, value: Int): Boolean? {
            ints["yellChance"] = value
            return true
        }

        override fun yellSpeedTicks(npcType: Any): Int? = ints["yellSpeedTicks"]

        override fun setYellSpeedTicks(npcType: Any, value: Int): Boolean? {
            ints["yellSpeedTicks"] = value
            return true
        }

        override fun respawnTypePeriod(npcType: Any): Int? = ints["respawnPeriod"]

        override fun setRespawnTypePeriod(npcType: Any, period: Int): Boolean? {
            ints["respawnPeriod"] = period
            return true
        }

        override fun respawnTypeIsUnderground(npcType: Any): Int? = respawnUnderground

        override fun setRespawnTypeIsUnderground(npcType: Any, value: Boolean): Boolean? {
            respawnUnderground = if (value) 1 else 0
            return true
        }

        override fun speechBubble(npcType: Any): Int? =
            if (typeOrNull(npcType) == null) null else ints["speechBubble"]

        override fun setSpeechBubble(npcType: Any, value: Int): Boolean? {
            ints["speechBubble"] = value
            return true
        }

        override fun currency(npcType: Any): Int? =
            if (typeOrNull(npcType) == null) null else ints["currency"]

        override fun setCurrency(npcType: Any, value: Int): Boolean? {
            ints["currency"] = value
            return true
        }

        override fun addShopItem(npcType: Any, shop: ShopData): Boolean? {
            shopItems += shop
            return true
        }

        override fun soundChance(npcType: Any): Int? = ints["soundChance"]

        override fun setSoundChance(npcType: Any, value: Int): Boolean? {
            ints["soundChance"] = value
            return true
        }

        override fun soundSpeedTicks(npcType: Any): Int? = ints["soundSpeedTicks"]

        override fun setSoundSpeedTicks(npcType: Any, value: Int): Boolean? {
            ints["soundSpeedTicks"] = value
            return true
        }

        override fun addSound(npcType: Any, soundId: Int): Boolean? {
            if (typeOrNull(npcType) == null) {
                return null
            }
            sounds += soundId
            return true
        }

        override fun getSounds(npcType: Any): List<Int>? = sounds.toList()
    }

    private fun installed(fake: FakeOps = FakeOps()): Pair<LuaEnvironment, FakeOps> {
        val environment = LuaEnvironment().apply { initState() }
        NpcTypeFunctions.install(environment, fake)
        environment.globals.set("t", environment.userdata.wrap("NpcType", FakeNpcType()))
        return environment to fake
    }

    @Test
    fun `NpcType constructor resolves the type from the seam`() {
        val fake = FakeOps()
        val type = FakeNpcType()
        fake.typesByName["Tom"] = type
        val environment = LuaEnvironment().apply { initState() }
        NpcTypeFunctions.install(environment, fake)

        environment.loadChunk("t = NpcType('Tom')")
        environment.loadChunk("missing = NpcType('Nobody')")

        assertThat(environment.getGlobal("t").touserdata()).isSameAs(type)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `NpcType boolean attributes use get and set branches`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            pushable = t:isPushable()
            setPushable = t:isPushable(true)
            pushableAfter = t:isPushable()
            floor = t:floorChange(true)
            items = t:canPushItems(true)
            creatures = t:canPushCreatures(true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("pushable").toboolean()).isFalse()
        assertThat(environment.getGlobal("setPushable").toboolean()).isTrue()
        assertThat(environment.getGlobal("pushableAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("floor").toboolean()).isTrue()
        assertThat(environment.getGlobal("items").toboolean()).isTrue()
        assertThat(environment.getGlobal("creatures").toboolean()).isTrue()
    }

    @Test
    fun `NpcType name description health and maxHealth round-trip`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            name = t:name()
            renamed = t:name('Banker')
            nameAfter = t:name()
            description = t:nameDescription()
            setDescription = t:nameDescription('a banker')
            health = t:health()
            setHealth = t:health(250)
            maxHealth = t:maxHealth()
            setMaxHealth = t:maxHealth(300)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Tom")
        assertThat(environment.getGlobal("renamed").toboolean()).isTrue()
        assertThat(environment.getGlobal("nameAfter").tojstring()).isEqualTo("Banker")
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("Tom the banker")
        assertThat(environment.getGlobal("setDescription").toboolean()).isTrue()
        assertThat(environment.getGlobal("health").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("maxHealth").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setMaxHealth").toboolean()).isTrue()
    }

    @Test
    fun `NpcType canSpawn delegates the position`() {
        val (environment, fake) = installed()
        environment.globals.set("pos", pushPosition(environment, Position(10, 20, 7)))

        environment.loadChunk("allowed = t:canSpawn(pos)")

        assertThat(environment.getGlobal("allowed").toboolean()).isTrue()
        assertThat(fake.lastSpawnPosition).isEqualTo(Position(10, 20, 7))
    }

    @Test
    fun `NpcType voices round-trip through addVoice and getVoices`() {
        val (environment, fake) = installed()

        environment.loadChunk("added = t:addVoice('Hello', 2000, 10, true)")
        environment.loadChunk("voices = t:getVoices()")

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(fake.lastVoice).isEqualTo(listOf("Hello", 2000, 10, true))
        assertThat(environment.getGlobal("voices").get(1).get("text").tojstring()).isEqualTo("Hello")
        assertThat(environment.getGlobal("voices").get(1).get("yellText").toboolean()).isTrue()
    }

    @Test
    fun `NpcType creature events and eventType use the Canary names`() {
        val (environment, fake) = installed()

        environment.loadChunk(
            """
            before = t:getCreatureEvents()
            registered = t:registerEvent('NpcBanker')
            after = t:getCreatureEvents()
            eventType = t:eventType(5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").istable()).isTrue()
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").get(1).tojstring()).isEqualTo("NpcBanker")
        assertThat(environment.getGlobal("eventType").toboolean()).isTrue()
        assertThat(fake.lastEventType).isEqualTo(5)
    }

    @Test
    fun `NpcType callback registration methods share loadCallback`() {
        val (environment, fake) = installed()

        environment.loadChunk("think = t:onThink(function() end)")
        environment.loadChunk("buy = t:onBuyItem(function() end)")
        environment.loadChunk("check = t:onCheckItem(function() end)")

        assertThat(environment.getGlobal("think").toboolean()).isTrue()
        assertThat(environment.getGlobal("buy").toboolean()).isTrue()
        assertThat(environment.getGlobal("check").toboolean()).isTrue()
        fake.callbackResult = false
        environment.loadChunk("refused = t:onSellItem(function() end)")
        assertThat(environment.getGlobal("refused").toboolean()).isFalse()
    }

    @Test
    fun `NpcType outfit and light mirror the C++ table and multi-return`() {
        val (environment, fake) = installed()
        fake.outfit.lookType = 136

        environment.loadChunk("outfit = t:outfit()")
        environment.loadChunk("setOutfit = t:outfit({lookType = 20, lookHead = 1})")
        environment.loadChunk("level, color = t:light()")
        environment.loadChunk("setLight = t:light(9, 4)")

        assertThat(environment.getGlobal("outfit").get("lookType").toint()).isEqualTo(136)
        assertThat(environment.getGlobal("setOutfit").toboolean()).isTrue()
        assertThat(fake.outfit.lookType).isEqualTo(20)
        assertThat(environment.getGlobal("level").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("color").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("setLight").toboolean()).isTrue()
        assertThat(fake.lastLight).isEqualTo(9 to 4)
    }

    @Test
    fun `NpcType numeric attributes use the Canary names`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            baseSpeed = t:baseSpeed(80)
            walkInterval = t:walkInterval(1500)
            walkRadius = t:walkRadius(4)
            yellChance = t:yellChance(5)
            yellSpeedTicks = t:yellSpeedTicks(1000)
            period = t:respawnTypePeriod(2)
            underground = t:respawnTypeIsUnderground(1)
            undergroundRead = t:respawnTypeIsUnderground()
            bubble = t:speechBubble(2)
            currency = t:currency(2160)
            soundChance = t:soundChance(3)
            soundSpeedTicks = t:soundSpeedTicks(900)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("baseSpeed").toboolean()).isTrue()
        assertThat(environment.getGlobal("walkInterval").toboolean()).isTrue()
        assertThat(environment.getGlobal("walkRadius").toboolean()).isTrue()
        assertThat(environment.getGlobal("yellChance").toboolean()).isTrue()
        assertThat(environment.getGlobal("yellSpeedTicks").toboolean()).isTrue()
        assertThat(environment.getGlobal("period").toboolean()).isTrue()
        assertThat(environment.getGlobal("underground").toboolean()).isTrue()
        assertThat(environment.getGlobal("undergroundRead").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("bubble").toboolean()).isTrue()
        assertThat(environment.getGlobal("currency").toboolean()).isTrue()
        assertThat(environment.getGlobal("soundChance").toboolean()).isTrue()
        assertThat(environment.getGlobal("soundSpeedTicks").toboolean()).isTrue()
    }

    @Test
    fun `NpcType addShopItem forwards the Shop userdata`() {
        val (environment, fake) = installed()

        environment.loadChunk(
            """
            shop = Shop()
            shop:setId(2160)
            shop:setNameItem('crystal coin')
            shop:setCount(3)
            added = t:addShopItem(shop)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(fake.shopItems).hasSize(1)
        assertThat(fake.shopItems[0].itemId).isEqualTo(2160)
        assertThat(fake.shopItems[0].itemName).isEqualTo("crystal coin")
        assertThat(fake.shopItems[0].itemSubType).isEqualTo(3)
    }

    @Test
    fun `NpcType addSound and getSounds keep the C++ stack quirk`() {
        val (environment, _) = installed()

        environment.loadChunk("first = t:addSound(5)")
        environment.loadChunk("second = t:addSound(7)")
        environment.loadChunk("sounds = t:getSounds()")

        assertThat(environment.getGlobal("first").toboolean()).isTrue()
        assertThat(environment.getGlobal("second").toboolean()).isTrue()
        assertThat(environment.getGlobal("sounds").get(1).isnil()).isTrue()
        assertThat(environment.getGlobal("sounds").get(2).toint()).isEqualTo(7)
    }

    @Test
    fun `NpcType methods answer false for a missing receiver where Canary does`() {
        val environment = LuaEnvironment().apply { initState() }
        NpcTypeFunctions.install(environment, FakeOps())
        val other = object {}
        environment.globals.set("other", environment.userdata.wrap("NpcType", other))

        environment.loadChunk(
            """
            pushable = other:isPushable()
            bubble = other:speechBubble()
            currency = other:currency()
            sound = other:addSound(1)
            name = other:name()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("pushable").isnil()).isTrue()
        assertThat(environment.getGlobal("bubble").toboolean()).isFalse()
        assertThat(environment.getGlobal("currency").toboolean()).isFalse()
        assertThat(environment.getGlobal("sound").toboolean()).isFalse()
        assertThat(environment.getGlobal("name").isnil()).isTrue()
    }
}
