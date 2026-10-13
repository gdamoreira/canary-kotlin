package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.spawns.MonsterType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests proving the monster binding group answers through the real
 * [WorldLuaOperations] seams backed by the Kotlin [Monster]/[MonsterType] domain and a
 * [org.opentibia.canary.creatures.spawns.MonsterType] catalog, using the exact Canary
 * method names.
 */
class MonsterWorldBindingsTest {

    private fun env(world: WorldLuaOperations): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { world.install(it) }

    private fun monster(id: Long, name: String, position: Position): Monster =
        Monster(id = id, name = name, maxHealth = 100, health = 100, position = position)

    private fun player(id: Long, name: String, position: Position): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    @Test
    fun `World Monster getType resolves the type from the monster name catalog`() {
        val type = MonsterType("Rat")
        val environment = env(WorldLuaOperations(monsterTypesByName = mapOf("Rat" to type)))
        val rat = monster(1, "Rat", Position(10, 20, 7))
        environment.globals.set("m", environment.userdata.wrap("Monster", rat))

        environment.loadChunk(
            """
            isMonster = m:isMonster()
            type = m:getType()
            name = m:getName()
            spawn = m:getSpawnPosition()
            range = m:isInSpawnRange()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("isMonster").toboolean()).isTrue()
        assertThat(environment.getGlobal("type").touserdata()).isSameAs(type)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("spawn").touserdata()).isEqualTo(Position(10, 20, 7))
        assertThat(environment.getGlobal("range").toboolean()).isTrue()
    }

    @Test
    fun `World Monster getType is nil when the name is unknown`() {
        val environment = env(WorldLuaOperations())
        val dragon = monster(1, "Dragon", Position(10, 20, 7))
        environment.globals.set("m", environment.userdata.wrap("Monster", dragon))

        environment.loadChunk("type = m:getType()")

        assertThat(environment.getGlobal("type").isnil()).isTrue()
    }

    @Test
    fun `World Monster target and friend lists round-trip creature userdata`() {
        val environment = env(WorldLuaOperations())
        val rat = monster(1, "Rat", Position(10, 10, 7))
        val hero = player(2, "Hero", Position(11, 10, 7))
        environment.globals.set("m", environment.userdata.wrap("Monster", rat))
        environment.globals.set("p", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            addedTarget = m:addTarget(p, true)
            targetCount = m:getTargetCount()
            targetList = m:getTargetList()
            firstTarget = targetList[1]
            targetPredicate = m:isTarget(p)
            addedFriend = m:addFriend(p)
            friendCount = m:getFriendCount()
            friendPredicate = m:isFriend(p)
            removedTarget = m:removeTarget(p)
            targetCountAfter = m:getTargetCount()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("addedTarget").toboolean()).isTrue()
        assertThat(environment.getGlobal("targetCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("firstTarget").touserdata()).isSameAs(hero)
        assertThat(environment.getGlobal("targetPredicate").toboolean()).isTrue()
        assertThat(environment.getGlobal("addedFriend").toboolean()).isTrue()
        assertThat(environment.getGlobal("friendCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("friendPredicate").toboolean()).isTrue()
        assertThat(environment.getGlobal("removedTarget").toboolean()).isTrue()
        assertThat(environment.getGlobal("targetCountAfter").toint()).isZero()
    }

    @Test
    fun `World Monster isTarget requires a live creature on the same floor`() {
        val environment = env(WorldLuaOperations())
        val rat = monster(1, "Rat", Position(10, 10, 7))
        val hero = player(2, "Hero", Position(11, 10, 7))
        val ghost = player(3, "Ghost", Position(11, 10, 6))
        environment.globals.set("m", environment.userdata.wrap("Monster", rat))
        environment.globals.set("p", environment.userdata.wrap("Player", hero))
        environment.globals.set("g", environment.userdata.wrap("Player", ghost))

        environment.loadChunk("sameFloor = m:isTarget(p)")
        environment.loadChunk("otherFloor = m:isTarget(g)")
        environment.loadChunk("itself = m:isTarget(m)")
        hero.setRemoved()
        environment.loadChunk("removed = m:isTarget(p)")

        assertThat(environment.getGlobal("sameFloor").toboolean()).isTrue()
        assertThat(environment.getGlobal("otherFloor").toboolean()).isFalse()
        assertThat(environment.getGlobal("itself").toboolean()).isFalse()
        assertThat(environment.getGlobal("removed").toboolean()).isFalse()
    }

    @Test
    fun `World Monster getTarget setTarget and master round-trip`() {
        val environment = env(WorldLuaOperations())
        val rat = monster(1, "Rat", Position(10, 10, 7))
        val hero = player(2, "Hero", Position(11, 10, 7))
        environment.globals.set("m", environment.userdata.wrap("Monster", rat))
        environment.globals.set("p", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            before = m:getTarget()
            set = m:setTarget(p)
            after = m:getTarget()
            cleared = m:setTarget(nil)
            clearedTarget = m:getTarget()
            setMaster = m:setMaster(p)
            master = m:getMaster()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("before").isnil()).isTrue()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").touserdata()).isSameAs(hero)
        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(environment.getGlobal("clearedTarget").isnil()).isTrue()
        assertThat(environment.getGlobal("setMaster").toboolean()).isTrue()
        assertThat(environment.getGlobal("master").touserdata()).isSameAs(hero)
    }

    @Test
    fun `World Monster idle selectTarget and challenge state use the Canary names`() {
        val environment = env(WorldLuaOperations())
        val rat = monster(1, "Rat", Position(10, 10, 7))
        val hero = player(2, "Hero", Position(11, 10, 7))
        environment.globals.set("m", environment.userdata.wrap("Monster", rat))
        environment.globals.set("p", environment.userdata.wrap("Player", hero))

        environment.loadChunk(
            """
            idle = m:isIdle()
            setIdle = m:setIdle(true)
            idleAfter = m:isIdle()
            selected = m:selectTarget(p)
            selectedTarget = m:getTarget()
            challenged = m:isChallenged()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("idle").toboolean()).isFalse()
        assertThat(environment.getGlobal("setIdle").toboolean()).isTrue()
        assertThat(environment.getGlobal("idleAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("selected").toboolean()).isTrue()
        assertThat(environment.getGlobal("selectedTarget").touserdata()).isSameAs(hero)
        assertThat(environment.getGlobal("challenged").toboolean()).isFalse()
    }

    @Test
    fun `World MonsterType getters and setters round-trip through the shared info`() {
        val type = MonsterType("Rat")
        val environment = env(WorldLuaOperations(monsterTypesByName = mapOf("Rat" to type)))
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))

        environment.loadChunk(
            """
            name = t:name()
            setName = t:name('Giant Rat')
            newName = t:name()
            health = t:health()
            setHealth = t:health(500)
            newHealth = t:health()
            maxHealth = t:maxHealth()
            setMaxHealth = t:maxHealth(600)
            newMaxHealth = t:maxHealth()
            experience = t:experience()
            setExperience = t:experience(250)
            newExperience = t:experience()
            speed = t:baseSpeed()
            setSpeed = t:baseSpeed(150)
            newSpeed = t:baseSpeed()
            typeName = t:getTypeName()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("setName").toboolean()).isTrue()
        assertThat(environment.getGlobal("newName").tojstring()).isEqualTo("Giant Rat")
        assertThat(environment.getGlobal("health").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newHealth").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("maxHealth").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setMaxHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newMaxHealth").toint()).isEqualTo(600)
        assertThat(environment.getGlobal("experience").toint()).isZero()
        assertThat(environment.getGlobal("setExperience").toboolean()).isTrue()
        assertThat(environment.getGlobal("newExperience").toint()).isEqualTo(250)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(110)
        assertThat(environment.getGlobal("setSpeed").toboolean()).isTrue()
        assertThat(environment.getGlobal("newSpeed").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("typeName").tojstring()).isEqualTo("Rat")
    }

    @Test
    fun `World MonsterType race and blockable use the domain and shared info`() {
        val type = MonsterType("Rat", isBlockable = true)
        val environment = env(WorldLuaOperations(monsterTypesByName = mapOf("Rat" to type)))
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))

        environment.loadChunk(
            """
            race = t:race()
            setRace = t:race('fire')
            newRace = t:race()
            blockable = t:isBlockable()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("race").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("setRace").toboolean()).isTrue()
        assertThat(environment.getGlobal("newRace").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("blockable").toboolean()).isTrue()
    }

    @Test
    fun `World MonsterType canSpawn delegates to the domain callback`() {
        val asked = mutableListOf<Position>()
        val type = MonsterType("Rat", canSpawnAt = { asked += it; it.z > 7 })
        val environment = env(WorldLuaOperations(monsterTypesByName = mapOf("Rat" to type)))
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))
        environment.globals.set("surface", environment.userdata.wrap("Position", Position(10, 20, 7)))
        environment.globals.set("underground", environment.userdata.wrap("Position", Position(10, 20, 8)))

        environment.loadChunk("surfaceAllowed = t:canSpawn(surface)")
        environment.loadChunk("undergroundAllowed = t:canSpawn(underground)")

        assertThat(environment.getGlobal("surfaceAllowed").toboolean()).isFalse()
        assertThat(environment.getGlobal("undergroundAllowed").toboolean()).isTrue()
        assertThat(asked).containsExactly(Position(10, 20, 7), Position(10, 20, 8))
    }

    @Test
    fun `World Loot creation and MonsterType addLoot getLoot round-trip`() {
        val type = MonsterType("Rat")
        val environment = env(
            WorldLuaOperations(
                itemIdsByName = mapOf("crystal coin" to 2160),
                monsterTypesByName = mapOf("Rat" to type),
            ),
        )
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))

        environment.loadChunk(
            """
            loot = Loot('Rat')
            named = loot:setIdFromName('crystal coin')
            max = loot:setMaxCount(3)
            chance = loot:setChance(50000)
            unique = loot:setUnique(true)
            added = t:addLoot(loot)
            table = t:getLoot()
            itemId = table[1].itemId
            maxCount = table[1].maxCount
            chanceRead = table[1].chance
            uniqueRead = table[1].unique
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("named").toboolean()).isTrue()
        assertThat(environment.getGlobal("max").toboolean()).isTrue()
        assertThat(environment.getGlobal("chance").toboolean()).isTrue()
        assertThat(environment.getGlobal("unique").toboolean()).isTrue()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("itemId").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("maxCount").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("chanceRead").toint()).isEqualTo(50_000)
        assertThat(environment.getGlobal("uniqueRead").toboolean()).isTrue()
    }
}
