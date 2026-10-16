package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * Transcript-style tests for the core Lua binding libraries. Each group is installed
 * with a fake engine seam, then exercised through real Lua chunks using the exact
 * Canary method names (player:addItem, creature:getHealth, item:transform,
 * position:sendMagicEffect, Game.createItem).
 */
class CoreBindingsTest {

    private class FakeItem(
        var id: Int,
        var count: Int = 1,
        var subType: Int = 0,
        var name: String = "item",
    )

    private class FakeCreature(
        val id: Long,
        val name: String,
        var health: Int,
        val maxHealth: Int,
        val position: Position,
        var direction: Int = 0,
    )

    private class FakePlayer

    private class FakeVocation(val id: Int, val name: String)

    private data class AddItemCall(
        val itemId: Int,
        val count: Int,
        val canDropOnMap: Boolean,
        val subType: Int,
        val slot: Int,
        val tier: Int,
    )

    private class FakeGame(private val names: Map<String, Int> = emptyMap()) : GameOperations {
        val createItemCalls = mutableListOf<Pair<Int, Int>>()
        override fun itemIdByName(name: String): Int = names[name] ?: 0
        override fun createItem(itemId: Int, count: Int): List<Any> {
            createItemCalls += itemId to count
            return listOf(FakeItem(itemId, count))
        }
    }

    private class FakePlayerOps : StubPlayerOperations() {
        val addItemCalls = mutableListOf<AddItemCall>()
        var lastMessage: LuaTextMessage? = null
        val storage = mutableMapOf<Int, Int>()
        val vocation = FakeVocation(4, "Elite Knight")
        var level = 33
        var experience = 12_345L
        var mana = 70
        var lastAddExperience: Pair<Long, Boolean>? = null
        override fun itemIdByName(name: String): Int = if (name == "gold coin") 2148 else 0
        override fun addItem(
            player: Any,
            itemId: Int,
            count: Int,
            canDropOnMap: Boolean,
            subType: Int,
            slot: Int,
            tier: Int,
        ): List<Any> {
            addItemCalls += AddItemCall(itemId, count, canDropOnMap, subType, slot, tier)
            return listOf(FakeItem(itemId, count))
        }

        override fun removeItem(
            player: Any,
            itemId: Int,
            count: Int,
            subType: Int,
            ignoreEquipped: Boolean,
            ignoreStoreInbox: Boolean,
        ): Boolean = itemId == 2148 && count == 2

        override fun getItemCount(
            player: Any,
            itemId: Int,
            subType: Int,
            ignoreEquipped: Boolean,
            ignoreStoreInbox: Boolean,
        ): Long = 7L

        override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean = skillType == 0 && tries == 100L

        override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean {
            lastMessage = message
            return true
        }

        override fun getStorageValue(player: Any, key: Int): Int = storage[key] ?: -1

        override fun setStorageValue(player: Any, key: Int, value: Int): Boolean {
            storage[key] = value
            return true
        }

        override fun getLevel(player: Any): Int = level

        override fun getExperience(player: Any): Long = experience

        override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean {
            this.experience += experience
            lastAddExperience = experience to sendText
            return true
        }

        override fun getMana(player: Any): Int = mana

        override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean {
            mana += manaChange
            return true
        }

        override fun getVocation(player: Any): Any = vocation

        override fun getGuid(player: Any): Long? = 1L

        override fun getSpeed(player: Any): Int? = 220

        override fun setSpeed(player: Any, speed: Int): Boolean = true

        override fun getCapacity(player: Any): Long? = 400L

        override fun setCapacity(player: Any, capacity: Long): Boolean = true

        override fun getFreeCapacity(player: Any): Long? = 400L

        override fun getMaxMana(player: Any): Int? = 0

        override fun setMaxMana(player: Any, maxMana: Int): Boolean = true

        override fun getMagicLevel(player: Any): Int = 0

        override fun getSkillLevel(player: Any, skillType: Int): Int? = 10

        override fun getSkillTries(player: Any, skillType: Int): Long? = 0L

        override fun getPremiumDays(player: Any): Int? = 0

        override fun getFightMode(player: Any): Int? = 1

        override fun hasChaseMode(player: Any): Boolean = false

        override fun hasSecureMode(player: Any): Boolean = true
    }

    private class FakeCreatureOps : CreatureOperations {
        val setHealthCalls = mutableListOf<Pair<Any, Int>>()
        override fun getId(creature: Any): Long = (creature as FakeCreature).id
        override fun getName(creature: Any): String = (creature as FakeCreature).name
        override fun getHealth(creature: Any): Int = (creature as FakeCreature).health
        override fun getMaxHealth(creature: Any): Int = (creature as FakeCreature).maxHealth
        override fun setHealth(creature: Any, health: Int): Boolean {
            setHealthCalls += creature to health
            (creature as FakeCreature).health = health
            return true
        }

        override fun addHealth(creature: Any, healthChange: Int, combatType: Int): Boolean {
            (creature as FakeCreature).health += healthChange
            return true
        }

        override fun getPosition(creature: Any): Position = (creature as FakeCreature).position

        override fun getDirection(creature: Any): Int = (creature as FakeCreature).direction

        override fun setDirection(creature: Any, direction: Int): Boolean {
            (creature as FakeCreature).direction = direction
            return true
        }

        override fun registerEvent(creature: Any, name: String): Boolean? = null

        override fun unregisterEvent(creature: Any, name: String): Boolean? = null
    }

    private class FakeItemOps : ItemOperations {
        val transforms = mutableListOf<Triple<Any, Int, Int>>()
        val attributes = mutableMapOf<Int, Any>()
        var actionId = 0
        var uniqueId = 0L
        override fun itemIdByName(name: String): Int = 0
        override fun itemAttributeByName(name: String): Int = if (name == "duration") 5 else 0
        override fun getId(item: Any): Int = (item as FakeItem).id
        override fun getCount(item: Any): Int = (item as FakeItem).count
        override fun getName(item: Any): String = (item as FakeItem).name
        override fun getSubType(item: Any): Int = (item as FakeItem).subType
        override fun transform(item: Any, itemId: Int, subType: Int): Boolean {
            transforms += Triple(item, itemId, subType)
            (item as FakeItem).id = itemId
            if (subType >= 0) {
                item.subType = subType
            }
            return true
        }

        override fun decay(item: Any, decayId: Int): Boolean = true

        override fun getActionId(item: Any): Int = actionId

        override fun setActionId(item: Any, actionId: Int): Boolean {
            this.actionId = actionId
            return true
        }

        override fun getUniqueId(item: Any): Long = uniqueId

        override fun getAttribute(item: Any, attribute: Int): Any? = attributes[attribute]

        override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean {
            if (attribute == 0) {
                return false
            }
            if (value == null) {
                attributes.remove(attribute)
            } else {
                attributes[attribute] = value
            }
            return true
        }
    }

    private class FakePositionOps : PositionOperations {
        val effects = mutableListOf<Triple<Position, Int, Any?>>()
        override fun getDistance(position: Position, target: Position): Int =
            max(max(abs(position.x - target.x), abs(position.y - target.y)), abs(position.z - target.z))

        override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean {
            effects += Triple(position, magicEffect, spectator)
            return true
        }

        override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = true

        override fun getPathTo(
            position: Position,
            target: Position,
            minTargetDist: Int,
            maxTargetDist: Int,
            fullPathSearch: Boolean,
            clearSight: Boolean,
            maxSearchDist: Int,
        ): List<Int>? = null

        override fun isSightClear(position: Position, target: Position, sameFloor: Boolean): Boolean =
            position.z == target.z

        override fun getTile(position: Position): Any? = null

        override fun getZones(position: Position): List<Any>? = null

        override fun sendDistanceEffect(
            position: Position,
            target: Position,
            distanceEffect: Int,
            spectator: Any?,
        ): Boolean = true

        override fun sendSingleSoundEffect(position: Position, soundId: Int, actor: Any?): Boolean = true

        override fun sendDoubleSoundEffect(
            position: Position,
            mainSoundId: Int,
            secondarySoundId: Int,
            actor: Any?,
        ): Boolean = true
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Game createItem uses the Canary name and wraps the seam item`() {
        val environment = env()
        val game = FakeGame(mapOf("gold coin" to 2148))
        GameFunctions.install(environment, game)
        ItemFunctions.install(environment, FakeItemOps())
        environment.loadChunk("item = Game.createItem(2160, 3)")

        val item = environment.getGlobal("item")
        assertThat(item.isuserdata()).isTrue()

        environment.loadChunk("id = item:getId()")
        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(game.createItemCalls).containsExactly(2160 to 3)
    }

    @Test
    fun `Game createItem resolves an item name to an id`() {
        val environment = env()
        val game = FakeGame(mapOf("gold coin" to 2148))
        GameFunctions.install(environment, game)
        environment.loadChunk("item = Game.createItem('gold coin', 5)")
        assertThat(environment.getGlobal("item").isuserdata()).isTrue()
        assertThat(game.createItemCalls).containsExactly(2148 to 5)
    }

    @Test
    fun `Game createItem returns nil for an unknown name`() {
        val environment = env()
        GameFunctions.install(environment, FakeGame())
        environment.loadChunk("item = Game.createItem('does not exist')")
        assertThat(environment.getGlobal("item").isnil()).isTrue()
    }

    @Test
    fun `Player addItem exposes the Canary signature and returns the item`() {
        val environment = env()
        val playerOps = FakePlayerOps()
        PlayerFunctions.install(environment, playerOps)
        ItemFunctions.install(environment, FakeItemOps())
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))

        environment.loadChunk("item = p:addItem(2160, 2, true, 1, 6, 0)")
        environment.loadChunk("id = item:getId()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(playerOps.addItemCalls).containsExactly(AddItemCall(2160, 2, true, 1, 6, 0))
    }

    @Test
    fun `Player removeItem getItemCount and addSkillTries use Canary names`() {
        val environment = env()
        PlayerFunctions.install(environment, FakePlayerOps())
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))

        environment.loadChunk("removed = p:removeItem(2148, 2)")
        environment.loadChunk("count = p:getItemCount('gold coin')")
        environment.loadChunk("trained = p:addSkillTries(0, 100)")

        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("trained").toboolean()).isTrue()
    }

    @Test
    fun `Player sendTextMessage forwards type and text`() {
        val environment = env()
        val playerOps = FakePlayerOps()
        PlayerFunctions.install(environment, playerOps)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))

        environment.loadChunk("ok = p:sendTextMessage(18, 'hello world')")

        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(playerOps.lastMessage).isNotNull()
        assertThat(playerOps.lastMessage!!.type).isEqualTo(18)
        assertThat(playerOps.lastMessage!!.text).isEqualTo("hello world")
    }

    @Test
    fun `Creature methods are available on the creature metatables`() {
        val environment = env()
        val creatureOps = FakeCreatureOps()
        CreatureFunctions.install(environment, creatureOps)
        PositionFunctions.install(environment, FakePositionOps())

        val creature = FakeCreature(7L, "Rat", 30, 100, Position(10, 20, 7))
        environment.globals.set("c", environment.userdata.wrap("Monster", creature))

        environment.loadChunk("id = c:getId()")
        environment.loadChunk("hp = c:getHealth()")
        environment.loadChunk("maxHp = c:getMaxHealth()")
        environment.loadChunk("c:setHealth(55)")
        environment.loadChunk("after = c:getHealth()")
        environment.loadChunk("pos = c:getPosition()")
        environment.globals.set("target", pushPosition(environment, Position(13, 24, 7)))
        environment.loadChunk("dist = pos:getDistance(target)")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("hp").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("maxHp").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("after").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("dist").toint()).isEqualTo(4)
        assertThat(creatureOps.setHealthCalls).containsExactly(creature to 55)
    }

    @Test
    fun `Player userdata also exposes inherited Creature methods`() {
        val environment = env()
        CreatureFunctions.install(environment, FakeCreatureOps())
        val creature = FakeCreature(42L, "Hero", 200, 200, Position(1, 1, 7))
        environment.globals.set("p", environment.userdata.wrap("Player", creature))

        environment.loadChunk("id = p:getId()")
        assertThat(environment.getGlobal("id").toint()).isEqualTo(42)
    }

    @Test
    fun `Item methods use the Canary names`() {
        val environment = env()
        ItemFunctions.install(environment, FakeItemOps())
        val item = FakeItem(2160, 5, 0, "crystal coin")
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("id = item:getId()")
        environment.loadChunk("count = item:getCount()")
        environment.loadChunk("name = item:getName()")
        environment.loadChunk("subType = item:getSubType()")
        environment.loadChunk("transformed = item:transform(2160, 3)")
        environment.loadChunk("decayed = item:decay(0)")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("subType").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("transformed").toboolean()).isTrue()
        assertThat(environment.getGlobal("decayed").toboolean()).isTrue()
    }

    @Test
    fun `Position toString and sendMagicEffect use the Canary names`() {
        val environment = env()
        val positionOps = FakePositionOps()
        PositionFunctions.install(environment, positionOps)
        environment.globals.set("pos", pushPosition(environment, Position(1, 2, 3)))

        environment.loadChunk("text = pos:toString()")
        environment.loadChunk("ok = pos:sendMagicEffect(12)")

        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("( 1, 2, 3 )")
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(positionOps.effects).containsExactly(Triple(Position(1, 2, 3), 12, null))
    }

    @Test
    fun `Player storage level experience mana and vocation use the Canary names`() {
        val environment = env()
        val playerOps = FakePlayerOps()
        PlayerFunctions.install(environment, playerOps)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))

        environment.loadChunk("storage = p:getStorageValue(1000)")
        environment.loadChunk("stored = p:setStorageValue(1000, 42)")
        environment.loadChunk("again = p:getStorageValue(1000)")
        environment.loadChunk("level = p:getLevel()")
        environment.loadChunk("experience = p:getExperience()")
        environment.loadChunk("added = p:addExperience(500, true)")
        environment.loadChunk("addedMana = p:addMana(30)")
        environment.loadChunk("mana = p:getMana()")
        environment.loadChunk("vocation = p:getVocation()")

        assertThat(environment.getGlobal("storage").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("again").toint()).isEqualTo(42)
        assertThat(playerOps.storage).containsEntry(1000, 42)
        assertThat(environment.getGlobal("level").toint()).isEqualTo(33)
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(12_345)
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(playerOps.lastAddExperience).isEqualTo(500L to true)
        assertThat(environment.getGlobal("mana").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("addedMana").toboolean()).isTrue()
        assertThat(environment.getGlobal("vocation").isuserdata()).isTrue()
    }

    @Test
    fun `Creature direction uses the Canary names`() {
        val environment = env()
        CreatureFunctions.install(environment, FakeCreatureOps())
        val creature = FakeCreature(9L, "Rat", 30, 100, Position(10, 20, 7), direction = 2)
        environment.globals.set("c", environment.userdata.wrap("Monster", creature))

        environment.loadChunk("dir = c:getDirection()")
        environment.loadChunk("turned = c:setDirection(3)")
        environment.loadChunk("after = c:getDirection()")

        assertThat(environment.getGlobal("dir").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("turned").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toint()).isEqualTo(3)
        assertThat(creature.direction).isEqualTo(3)
    }

    @Test
    fun `Item action id unique id and attributes use the Canary names`() {
        val environment = env()
        val itemOps = FakeItemOps()
        ItemFunctions.install(environment, itemOps)
        itemOps.uniqueId = 6543L
        itemOps.attributes[5] = 180
        environment.globals.set("item", environment.userdata.wrap("Item", FakeItem(2160)))

        environment.loadChunk("aid = item:getActionId()")
        environment.loadChunk("setAid = item:setActionId(100)")
        environment.loadChunk("uid = item:getUniqueId()")
        environment.loadChunk("attr = item:getAttribute(5)")
        environment.loadChunk("named = item:getAttribute('duration')")
        environment.loadChunk("setAttr = item:setAttribute(5, 7)")

        assertThat(environment.getGlobal("aid").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("setAid").toboolean()).isTrue()
        assertThat(itemOps.actionId).isEqualTo(100)
        assertThat(environment.getGlobal("uid").toint()).isEqualTo(6543)
        assertThat(environment.getGlobal("attr").toint()).isEqualTo(180)
        assertThat(environment.getGlobal("named").toint()).isEqualTo(180)
        assertThat(environment.getGlobal("setAttr").toboolean()).isTrue()
        assertThat(itemOps.attributes[5]).isEqualTo(7)
    }

    @Test
    fun `Position arithmetic and equality metamethods follow Canary`() {
        val environment = env()
        PositionFunctions.install(environment, FakePositionOps())
        environment.loadChunk("a = Position(1, 2, 3)")
        environment.loadChunk("b = Position(4, 5, 6)")
        environment.loadChunk("same = Position(1, 2, 3)")

        environment.loadChunk("sum = a + b")
        environment.loadChunk("diff = a - b")
        environment.loadChunk("equal = (a == same)")
        environment.loadChunk("different = (a == b)")

        assertThat(environment.getGlobal("sum").istable()).isTrue()
        environment.loadChunk("sumText = sum:toString()")
        assertThat(environment.getGlobal("sumText").tojstring()).isEqualTo("( 5, 7, 9 )")
        environment.loadChunk("diffText = diff:toString()")
        assertThat(environment.getGlobal("diffText").tojstring()).isEqualTo("( -3, -3, -3 )")
        assertThat(environment.getGlobal("equal").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
    }

    @Test
    fun `installed metatables expose the exact Canary method names`() {
        val environment = env()
        GameFunctions.install(environment, FakeGame())
        PlayerFunctions.install(environment, FakePlayerOps())
        CreatureFunctions.install(environment, FakeCreatureOps())
        ItemFunctions.install(environment, FakeItemOps())
        PositionFunctions.install(environment, FakePositionOps())

        environment.loadChunk(
            "checks = (rawgetmetatable('Player').__metatable.addItem ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.removeItem ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.getItemCount ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.addSkillTries ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.sendTextMessage ~= nil) and " +
                "(rawgetmetatable('Player').__metatable.getHealth ~= nil) and " +
                "(rawgetmetatable('Monster').__metatable.getHealth ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.transform ~= nil) and " +
                "(rawgetmetatable('Item').__metatable.decay ~= nil) and " +
                "(rawgetmetatable('Position').__metatable.sendMagicEffect ~= nil) and " +
                "(Game.createItem ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
