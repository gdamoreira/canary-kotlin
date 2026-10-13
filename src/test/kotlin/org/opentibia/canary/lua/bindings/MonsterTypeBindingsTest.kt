package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the Canary `MonsterType` binding group. Each test installs
 * [MonsterTypeFunctions] with a fake [MonsterTypeOperations] seam and drives it through
 * real Lua chunks using the exact method names from
 * src/lua/functions/creatures/monster/monster_type_functions.cpp.
 */
class MonsterTypeBindingsTest {

    private class FakeType(
        var name: String? = "Rat",
        var health: Int? = 100,
        var maxHealth: Int? = 100,
        var experience: Long? = 10,
        var baseSpeed: Int? = 110,
        var race: Int? = 2,
        var bestiaryClass: String? = "Mammal",
        var corpseId: Int? = 5964,
        var typeName: String? = "Rat",
        var attackable: Boolean? = true,
        var summonable: Boolean? = false,
        var convinceable: Boolean? = false,
        var hostile: Boolean? = true,
        var blockable: Boolean? = false,
        var canSpawn: Boolean? = true,
        val loot: MutableList<LootData> = mutableListOf(),
        val attacks: MutableList<MonsterAttackInfo> = mutableListOf(),
    )

    private class FakeMonsterTypeOps : MonsterTypeOperations {
        val typesByName = mutableMapOf<String, Any>()
        val typesByRaceId = mutableMapOf<Int, Any>()
        var lastLoot: LootData? = null
        val spawnPositions = mutableListOf<Position>()

        override fun create(nameOrRaceId: Any): Any? = when (nameOrRaceId) {
            is Number -> typesByRaceId[nameOrRaceId.toInt()]
            is String -> typesByName[nameOrRaceId]
            else -> null
        }

        override fun getName(monsterType: Any): String? = (monsterType as FakeType).name

        override fun setName(monsterType: Any, name: String): Boolean? {
            (monsterType as FakeType).name = name
            return true
        }

        override fun getHealth(monsterType: Any): Int? = (monsterType as FakeType).health

        override fun setHealth(monsterType: Any, health: Int): Boolean? {
            (monsterType as FakeType).health = health
            return true
        }

        override fun getMaxHealth(monsterType: Any): Int? = (monsterType as FakeType).maxHealth

        override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? {
            (monsterType as FakeType).maxHealth = maxHealth
            return true
        }

        override fun getExperience(monsterType: Any): Long? = (monsterType as FakeType).experience

        override fun setExperience(monsterType: Any, experience: Long): Boolean? {
            (monsterType as FakeType).experience = experience
            return true
        }

        override fun getBaseSpeed(monsterType: Any): Int? = (monsterType as FakeType).baseSpeed

        override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? {
            (monsterType as FakeType).baseSpeed = speed
            return true
        }

        override fun getRace(monsterType: Any): Int? = (monsterType as FakeType).race

        override fun setRace(monsterType: Any, race: Int): Boolean? {
            (monsterType as FakeType).race = race
            return true
        }

        override fun getBestiaryClass(monsterType: Any): String? = (monsterType as FakeType).bestiaryClass

        override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? {
            (monsterType as FakeType).bestiaryClass = bestiaryClass
            return true
        }

        override fun getCorpseId(monsterType: Any): Int? = (monsterType as FakeType).corpseId

        override fun getTypeName(monsterType: Any): String? = (monsterType as FakeType).typeName

        override fun isAttackable(monsterType: Any): Boolean? = (monsterType as FakeType).attackable

        override fun setAttackable(monsterType: Any, value: Boolean): Boolean? {
            (monsterType as FakeType).attackable = value
            return true
        }

        override fun isSummonable(monsterType: Any): Boolean? = (monsterType as FakeType).summonable

        override fun setSummonable(monsterType: Any, value: Boolean): Boolean? {
            (monsterType as FakeType).summonable = value
            return true
        }

        override fun isConvinceable(monsterType: Any): Boolean? = (monsterType as FakeType).convinceable

        override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? {
            (monsterType as FakeType).convinceable = value
            return true
        }

        override fun isHostile(monsterType: Any): Boolean? = (monsterType as FakeType).hostile

        override fun setHostile(monsterType: Any, value: Boolean): Boolean? {
            (monsterType as FakeType).hostile = value
            return true
        }

        override fun isBlockable(monsterType: Any): Boolean? = (monsterType as FakeType).blockable

        override fun setBlockable(monsterType: Any, value: Boolean): Boolean? {
            (monsterType as FakeType).blockable = value
            return true
        }

        override fun canSpawn(monsterType: Any, position: Position): Boolean? {
            spawnPositions += position
            return (monsterType as FakeType).canSpawn
        }

        override fun getLoot(monsterType: Any): List<LootData>? = (monsterType as FakeType).loot

        override fun addLoot(monsterType: Any, loot: LootData): Boolean? {
            lastLoot = loot
            (monsterType as FakeType).loot += loot
            return true
        }

        override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? = (monsterType as FakeType).attacks
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(
        fake: FakeMonsterTypeOps = FakeMonsterTypeOps(),
        type: FakeType = FakeType(),
    ): Triple<LuaEnvironment, FakeMonsterTypeOps, FakeType> {
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))
        return Triple(environment, fake, type)
    }

    @Test
    fun `MonsterType global constructor resolves a name through the seam`() {
        val fake = FakeMonsterTypeOps()
        val type = FakeType(name = "Rat")
        fake.typesByName["Rat"] = type
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)

        environment.loadChunk("found = MonsterType('Rat')")
        environment.loadChunk("missing = MonsterType('Dragon')")

        assertThat(environment.getGlobal("found").touserdata()).isSameAs(type)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `MonsterType global constructor resolves a race id through the seam`() {
        val fake = FakeMonsterTypeOps()
        val type = FakeType(name = "Rat")
        fake.typesByRaceId[44] = type
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)

        environment.loadChunk("found = MonsterType(44)")

        assertThat(environment.getGlobal("found").touserdata()).isSameAs(type)
    }

    @Test
    fun `MonsterType name health and maxHealth use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk("name = t:name()")
        environment.loadChunk("setName = t:name('Giant Rat')")
        environment.loadChunk("newName = t:name()")
        environment.loadChunk("health = t:health()")
        environment.loadChunk("setHealth = t:health(500)")
        environment.loadChunk("newHealth = t:health()")
        environment.loadChunk("maxHealth = t:maxHealth()")
        environment.loadChunk("setMaxHealth = t:maxHealth(600)")
        environment.loadChunk("newMaxHealth = t:maxHealth()")

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("setName").toboolean()).isTrue()
        assertThat(environment.getGlobal("newName").tojstring()).isEqualTo("Giant Rat")
        assertThat(environment.getGlobal("health").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newHealth").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("maxHealth").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setMaxHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newMaxHealth").toint()).isEqualTo(600)
        assertThat(type.name).isEqualTo("Giant Rat")
    }

    @Test
    fun `MonsterType experience and baseSpeed use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("experience = t:experience()")
        environment.loadChunk("setExperience = t:experience(250)")
        environment.loadChunk("newExperience = t:experience()")
        environment.loadChunk("speed = t:baseSpeed()")
        environment.loadChunk("setSpeed = t:baseSpeed(150)")
        environment.loadChunk("newSpeed = t:baseSpeed()")

        assertThat(environment.getGlobal("experience").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("setExperience").toboolean()).isTrue()
        assertThat(environment.getGlobal("newExperience").toint()).isEqualTo(250)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(110)
        assertThat(environment.getGlobal("setSpeed").toboolean()).isTrue()
        assertThat(environment.getGlobal("newSpeed").toint()).isEqualTo(150)
    }

    @Test
    fun `MonsterType race maps the Canary race names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("before = t:race()")
        environment.loadChunk("blood = t:race('blood')")
        environment.loadChunk("afterBlood = t:race()")
        environment.loadChunk("fire = t:race('fire')")
        environment.loadChunk("afterFire = t:race()")
        environment.loadChunk("unknown = t:race('plasma')")

        assertThat(environment.getGlobal("before").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("blood").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterBlood").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("fire").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterFire").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `MonsterType Bestiaryclass and getCorpseId use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("bestiary = t:Bestiaryclass()")
        environment.loadChunk("setBestiary = t:Bestiaryclass('Amphibic')")
        environment.loadChunk("newBestiary = t:Bestiaryclass()")
        environment.loadChunk("corpse = t:getCorpseId()")

        assertThat(environment.getGlobal("bestiary").tojstring()).isEqualTo("Mammal")
        assertThat(environment.getGlobal("setBestiary").toboolean()).isTrue()
        assertThat(environment.getGlobal("newBestiary").tojstring()).isEqualTo("Amphibic")
        assertThat(environment.getGlobal("corpse").toint()).isEqualTo(5964)
    }

    @Test
    fun `MonsterType flag getters and setters use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("attackable = t:isAttackable()")
        environment.loadChunk("summonable = t:isSummonable()")
        environment.loadChunk("hostile = t:isHostile()")
        environment.loadChunk("blockable = t:isBlockable()")
        environment.loadChunk("setAttackable = t:isAttackable(false)")
        environment.loadChunk("setSummonable = t:isSummonable(true)")
        environment.loadChunk("setHostile = t:isHostile(false)")
        environment.loadChunk("setBlockable = t:isBlockable(true)")
        environment.loadChunk("convinceable = t:isConvinceable()")
        environment.loadChunk("setConvinceable = t:isConvinceable(true)")
        environment.loadChunk("newConvinceable = t:isConvinceable()")
        environment.loadChunk("newAttackable = t:isAttackable()")
        environment.loadChunk("newSummonable = t:isSummonable()")
        environment.loadChunk("newHostile = t:isHostile()")
        environment.loadChunk("newBlockable = t:isBlockable()")

        assertThat(environment.getGlobal("attackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("summonable").toboolean()).isFalse()
        assertThat(environment.getGlobal("hostile").toboolean()).isTrue()
        assertThat(environment.getGlobal("blockable").toboolean()).isFalse()
        assertThat(environment.getGlobal("setAttackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("setSummonable").toboolean()).isTrue()
        assertThat(environment.getGlobal("setHostile").toboolean()).isTrue()
        assertThat(environment.getGlobal("setBlockable").toboolean()).isTrue()
        assertThat(environment.getGlobal("convinceable").toboolean()).isFalse()
        assertThat(environment.getGlobal("setConvinceable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newConvinceable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newAttackable").toboolean()).isFalse()
        assertThat(environment.getGlobal("newSummonable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newHostile").toboolean()).isFalse()
        assertThat(environment.getGlobal("newBlockable").toboolean()).isTrue()
    }

    @Test
    fun `MonsterType getTypeName and canSpawn use the Canary names`() {
        val (environment, fake, _) = installed()
        environment.globals.set("pos", environment.userdata.wrap("Position", Position(10, 20, 7)))

        environment.loadChunk("typeName = t:getTypeName()")
        environment.loadChunk("spawn = t:canSpawn(pos)")

        assertThat(environment.getGlobal("typeName").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("spawn").toboolean()).isTrue()
        assertThat(fake.spawnPositions).containsExactly(Position(10, 20, 7))
    }

    @Test
    fun `MonsterType getLoot renders the loot block table`() {
        val type = FakeType(
            loot = mutableListOf(
                LootData(
                    id = 2148,
                    chance = 50_000,
                    subType = -1,
                    countMin = 1,
                    countMax = 5,
                    actionId = -1,
                    unique = false,
                    childLoot = mutableListOf(LootData(id = 2160, chance = 1000)),
                ),
            ),
        )
        val (environment, _, _) = installed(type = type)

        environment.loadChunk(
            """
            loot = t:getLoot()
            itemId = loot[1].itemId
            chance = loot[1].chance
            subType = loot[1].subType
            maxCount = loot[1].maxCount
            minCount = loot[1].minCount
            actionId = loot[1].actionId
            unique = loot[1].unique
            childId = loot[1].childLoot[1].itemId
            count = #loot
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("itemId").toint()).isEqualTo(2148)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(50_000)
        assertThat(environment.getGlobal("subType").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("maxCount").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("minCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("actionId").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("unique").toboolean()).isFalse()
        assertThat(environment.getGlobal("childId").toint()).isEqualTo(2160)
    }

    @Test
    fun `MonsterType addLoot loads a Loot userdata into the seam`() {
        val (environment, fake, type) = installed()
        LootFunctions.install(environment, UnwiredLootOperations)

        environment.loadChunk(
            """
            loot = Loot('Rat')
            loot:setId(2160)
            loot:setMaxCount(3)
            added = t:addLoot(loot)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(fake.lastLoot?.id).isEqualTo(2160)
        assertThat(fake.lastLoot?.countMax).isEqualTo(3)
        assertThat(type.loot.single().id).isEqualTo(2160)
    }

    @Test
    fun `MonsterType getAttackList renders the attack table`() {
        val type = FakeType(
            attacks = mutableListOf(
                MonsterAttackInfo(
                    chance = 20,
                    isCombatSpell = true,
                    isMelee = false,
                    minCombatValue = 10,
                    maxCombatValue = 20,
                    range = 7,
                    speed = 2000,
                ),
            ),
        )
        val (environment, _, _) = installed(type = type)

        environment.loadChunk(
            """
            attacks = t:getAttackList()
            chance = attacks[1].chance
            isCombatSpell = attacks[1].isCombatSpell
            isMelee = attacks[1].isMelee
            minValue = attacks[1].minCombatValue
            maxValue = attacks[1].maxCombatValue
            range = attacks[1].range
            speed = attacks[1].speed
            spell = attacks[1].spell
            count = #attacks
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("isCombatSpell").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("isMelee").toint()).isZero()
        assertThat(environment.getGlobal("minValue").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("maxValue").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("range").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("spell").isnil()).isTrue()
    }

}
