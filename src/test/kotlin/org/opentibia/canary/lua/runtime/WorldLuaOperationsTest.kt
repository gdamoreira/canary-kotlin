package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests proving the real (non-no-op) Lua binding seams in
 * [WorldLuaOperations] delegate to the Kotlin domain objects. Each test installs the
 * seams through the public `*Functions.install(env, ops)` entry points and runs the
 * exact Canary Lua method names against real [Player]/[Monster]/[Item]/[Container]/
 * [Position] instances.
 */
class WorldLuaOperationsTest {

    private fun envWith(world: WorldLuaOperations = WorldLuaOperations()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    @Test
    fun `Game createItem builds a real Item from the ItemTypeRegistry`() {
        val registry = ItemTypeRegistry().apply {
            register(ItemType(2160, "crystal coin", isStackable = true))
        }
        val environment = envWith(WorldLuaOperations(itemTypes = registry))

        environment.loadChunk("item = Game.createItem(2160, 5)")
        environment.loadChunk("id = item:getId()")
        environment.loadChunk("count = item:getCount()")
        environment.loadChunk("name = item:getName()")
        environment.loadChunk("subType = item:getSubType()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("subType").toint()).isEqualTo(5)
    }

    @Test
    fun `Game createItem resolves a name from the catalog`() {
        val environment = envWith(WorldLuaOperations(itemIdsByName = mapOf("crystal coin" to 2160)))

        environment.loadChunk("item = Game.createItem('crystal coin', 2)")
        environment.loadChunk("id = item:getId()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
    }

    @Test
    fun `Game createItem returns nil for an unknown id when a registry is present`() {
        val environment = envWith(WorldLuaOperations(itemTypes = ItemTypeRegistry()))

        environment.loadChunk("item = Game.createItem(99999, 1)")

        assertThat(environment.getGlobal("item").isnil()).isTrue()
    }

    @Test
    fun `Player getLevel getExperience and getMana reflect the domain object`() {
        val environment = envWith()
        val player = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 120,
            maxMana = 100,
            mana = 70,
            position = Position(100, 200, 7),
            level = 42,
            experience = 12_345,
        )
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk("level = p:getLevel()")
        environment.loadChunk("experience = p:getExperience()")
        environment.loadChunk("mana = p:getMana()")

        assertThat(environment.getGlobal("level").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(12_345)
        assertThat(environment.getGlobal("mana").toint()).isEqualTo(70)
    }

    @Test
    fun `Player storage uses a per-player map`() {
        val environment = envWith()
        val player = Player(id = 7, name = "Hero", maxHealth = 100, health = 100, position = Position(0, 0, 7))
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk("missing = p:getStorageValue(1000)")
        environment.loadChunk("stored = p:setStorageValue(1000, 42)")
        environment.loadChunk("again = p:getStorageValue(1000)")

        assertThat(environment.getGlobal("missing").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("stored").toboolean()).isTrue()
        assertThat(environment.getGlobal("again").toint()).isEqualTo(42)
    }

    @Test
    fun `Player addExperience mutates the domain experience and level`() {
        val environment = envWith()
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(0, 0, 7))
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk("added = p:addExperience(1000)")
        environment.loadChunk("experience = p:getExperience()")
        environment.loadChunk("level = p:getLevel()")

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("experience").toint()).isEqualTo(1000)
        assertThat(environment.getGlobal("level").toint()).isEqualTo(5)
        assertThat(player.experience).isEqualTo(1000L)
        assertThat(player.level).isEqualTo(5)
    }

    @Test
    fun `Player addMana clamps to the domain mana bounds`() {
        val environment = envWith()
        val player = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 100,
            mana = 90,
            position = Position(0, 0, 7),
        )
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk("gained = p:addMana(30)")
        environment.loadChunk("capped = p:getMana()")
        environment.loadChunk("lost = p:addMana(-200)")
        environment.loadChunk("drained = p:getMana()")

        assertThat(environment.getGlobal("gained").toboolean()).isTrue()
        assertThat(environment.getGlobal("capped").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("lost").toboolean()).isTrue()
        assertThat(environment.getGlobal("drained").toint()).isEqualTo(0)
    }

    @Test
    fun `Creature getters reflect the domain creature`() {
        val environment = envWith()
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(10, 20, 7))
        environment.globals.set("c", environment.userdata.wrap("Monster", monster))

        environment.loadChunk("id = c:getId()")
        environment.loadChunk("name = c:getName()")
        environment.loadChunk("hp = c:getHealth()")
        environment.loadChunk("maxHp = c:getMaxHealth()")
        environment.loadChunk("pos = c:getPosition()")
        environment.loadChunk("posText = pos:toString()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("hp").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("maxHp").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("posText").tojstring()).isEqualTo("( 10, 20, 7 )")
    }

    @Test
    fun `Creature health mutators clamp like Canary`() {
        val environment = envWith()
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(10, 20, 7))
        environment.globals.set("c", environment.userdata.wrap("Monster", monster))

        environment.loadChunk("set = c:setHealth(55)")
        environment.loadChunk("afterSet = c:getHealth()")
        environment.loadChunk("hurt = c:addHealth(-20)")
        environment.loadChunk("afterHurt = c:getHealth()")
        environment.loadChunk("healed = c:addHealth(1000)")
        environment.loadChunk("afterHeal = c:getHealth()")
        environment.loadChunk("overflow = c:setHealth(999)")
        environment.loadChunk("afterOverflow = c:getHealth()")

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterSet").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("hurt").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterHurt").toint()).isEqualTo(35)
        assertThat(environment.getGlobal("healed").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterHeal").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("overflow").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterOverflow").toint()).isEqualTo(100)
    }

    @Test
    fun `Creature direction maps to the domain Direction enum`() {
        val environment = envWith()
        val monster = Monster(id = 9, name = "Rat", maxHealth = 100, health = 30, position = Position(10, 20, 7))
        environment.globals.set("c", environment.userdata.wrap("Monster", monster))

        environment.loadChunk("dir = c:getDirection()")
        environment.loadChunk("turned = c:setDirection(3)")
        environment.loadChunk("after = c:getDirection()")

        assertThat(environment.getGlobal("dir").toint()).isEqualTo(Direction.South.ordinal)
        assertThat(environment.getGlobal("turned").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toint()).isEqualTo(Direction.West.ordinal)
        assertThat(monster.direction).isEqualTo(Direction.West)
    }

    @Test
    fun `Item getters reflect the domain item`() {
        val environment = envWith()
        val item = Item(id = 2160, count = 5, type = ItemType(2160, "crystal coin", isStackable = true))
        environment.globals.set("item", environment.userdata.wrap("Item", item))

        environment.loadChunk("id = item:getId()")
        environment.loadChunk("count = item:getCount()")
        environment.loadChunk("name = item:getName()")
        environment.loadChunk("subType = item:getSubType()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("subType").toint()).isEqualTo(5)
    }

    @Test
    fun `Container getters reflect the domain container`() {
        val environment = envWith()
        val container = Container(id = 1987, capacity = 3, type = ItemType(1987, "backpack", isContainer = true))
        val coin = Item(id = 2160, count = 5)
        container.addItem(coin)
        container.addItem(Item(id = 2148))
        environment.globals.set("bag", environment.userdata.wrap("Container", container))
        environment.globals.set("coin", environment.userdata.wrap("Item", coin))

        environment.loadChunk("size = bag:getSize()")
        environment.loadChunk("maxCapacity = bag:getMaxCapacity()")
        environment.loadChunk("capacity = bag:getCapacity()")
        environment.loadChunk("first = bag:getItem(0)")
        environment.loadChunk("firstId = first:getId()")
        environment.loadChunk("coins = bag:getItemCountById(2160)")
        environment.loadChunk("holds = bag:hasItem(coin)")
        environment.loadChunk("all = bag:getItems()")

        assertThat(environment.getGlobal("size").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("maxCapacity").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstId").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("coins").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("holds").toboolean()).isTrue()
        assertThat(environment.getGlobal("all").rawlen()).isEqualTo(2)
    }

    @Test
    fun `Container recursive queries descend into nested containers`() {
        val environment = envWith()
        val outer = Container(id = 1987, capacity = 3)
        val inner = Container(id = 1988, capacity = 2)
        val coin = Item(id = 2148)
        inner.addItem(coin)
        outer.addItem(inner)
        environment.globals.set("outer", environment.userdata.wrap("Container", outer))
        environment.globals.set("coin", environment.userdata.wrap("Item", coin))

        environment.loadChunk("empty = outer:getEmptySlots(true)")
        environment.loadChunk("holding = outer:getItemHoldingCount()")
        environment.loadChunk("holds = outer:hasItem(coin)")
        environment.loadChunk("all = outer:getItems(true)")

        assertThat(environment.getGlobal("empty").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("holding").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("holds").toboolean()).isTrue()
        assertThat(environment.getGlobal("all").rawlen()).isEqualTo(2)
    }

    @Test
    fun `Position getDistance uses the Canary max-axis formula including z`() {
        val environment = envWith()
        environment.globals.set("from", environment.userdata.wrap("Position", Position(1, 2, 3)))
        environment.globals.set("to", environment.userdata.wrap("Position", Position(4, 6, 1)))

        environment.loadChunk("dist = from:getDistance(to)")

        assertThat(environment.getGlobal("dist").toint()).isEqualTo(4)
    }

    @Test
    fun `WorldLuaOperations installs all six seams`() {
        val environment = envWith()

        environment.loadChunk(
            "checks = (Game.createItem ~= nil) and " +
                "(rawgetmetatable('Player').getLevel ~= nil) and " +
                "(rawgetmetatable('Player').getHealth ~= nil) and " +
                "(rawgetmetatable('Monster').getName ~= nil) and " +
                "(rawgetmetatable('Item').getId ~= nil) and " +
                "(rawgetmetatable('Container').getSize ~= nil) and " +
                "(rawgetmetatable('Position').getDistance ~= nil)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }
}
