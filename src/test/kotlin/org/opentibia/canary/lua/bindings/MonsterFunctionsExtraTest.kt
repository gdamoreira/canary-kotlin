package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.creatures.monsters.RespawnType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the batch-2 `Monster` methods ported from
 * src/lua/functions/creatures/monster/monster_functions.cpp (forge, hazard, soul pit,
 * defense, immunity and critical attributes), driven through real Lua chunks with a
 * fake [MonsterOperations] seam.
 */
class MonsterFunctionsExtraTest {

    private class FakeMonster
    private class FakeMonsterType
    private class FakeMonsterSpell

    /** No-op implementations of the batch-1 surface so [FakeOps] can focus on batch 2. */
    private open class BaseOps : MonsterOperations {
        override fun isMonster(monster: Any): Boolean = true
        override fun getType(monster: Any): Any? = null
        override fun getName(monster: Any): String? = null
        override fun getSpawnPosition(monster: Any): Position? = null
        override fun isInSpawnRange(monster: Any, position: Position?): Boolean? = null
        override fun isIdle(monster: Any): Boolean? = null
        override fun setIdle(monster: Any, idle: Boolean): Boolean? = null
        override fun isTarget(monster: Any, creature: Any?): Boolean? = null
        override fun isOpponent(monster: Any, creature: Any?): Boolean? = null
        override fun isFriend(monster: Any, creature: Any?): Boolean? = null
        override fun addFriend(monster: Any, creature: Any?): Boolean? = null
        override fun removeFriend(monster: Any, creature: Any?): Boolean? = null
        override fun getFriendList(monster: Any): List<Any>? = null
        override fun addTarget(monster: Any, creature: Any?, pushFront: Boolean): Boolean? = null
        override fun removeTarget(monster: Any, creature: Any?): Boolean? = null
        override fun getTargetList(monster: Any): List<Any>? = null
        override fun isChallenged(monster: Any): Boolean? = null
        override fun selectTarget(monster: Any, creature: Any?): Boolean? = null
        override fun getTarget(monster: Any): Any? = null
        override fun setTarget(monster: Any, target: Any?): Boolean? = null
        override fun getMaster(monster: Any): Any? = null
        override fun setMaster(monster: Any, master: Any?): Boolean? = null
    }

    private class FakeOps : BaseOps() {
        val monstersById = mutableMapOf<Long, Any>()
        var typeByName: (String) -> Any? = { null }
        var typeByRaceId: (Int) -> Any? = { null }
        var lastSetType: Triple<Any, Any, Boolean>? = null
        var changeTargetDistanceResult = true
        var lastChangeTargetDistance: Pair<Int, Int>? = null
        var searchTargetResult = true
        var lastSearchType: Int? = null
        var lastSpawnInterval: Int? = null
        var respawnType = RespawnType(RespawnPeriod.RESPAWNPERIOD_DAY, true)
        var timeToChangeFiendish = 0L
        var forgeClassification = 0
        var forgeStack = 0
        var forgeConfigured = false
        var lastConfigureStack: Int? = null
        var clearedFiendish = false
        var forgeable = true
        var lastName: Pair<String, String?>? = null
        val hazards = mutableMapOf<String, Boolean>()
        var reflectElement: Triple<Any, Int, Int>? = null
        var defenseAdded: Pair<Any, Int>? = null
        var defense = 0
        var dead = false
        var immune = false
        var criticalChance = 0
        var criticalDamage = 0
        val attackSpells = mutableListOf<Any?>()
        val defenseSpells = mutableListOf<Any?>()

        override fun isMonster(monster: Any): Boolean = monster is FakeMonster

        override fun getMonsterById(id: Long): Any? = monstersById[id]

        override fun setType(monster: Any, nameOrRaceId: Any, restoreHealth: Boolean): Boolean? {
            lastSetType = Triple(monster, nameOrRaceId, restoreHealth)
            // Canary pushes true for any real monster receiver, resolved or not.
            return true
        }

        override fun changeTargetDistance(monster: Any, distance: Int, duration: Int): Boolean? {
            lastChangeTargetDistance = distance to duration
            return changeTargetDistanceResult
        }

        override fun searchTarget(monster: Any, searchType: Int): Boolean? {
            lastSearchType = searchType
            return searchTargetResult
        }

        override fun setSpawnPosition(monster: Any, interval: Int): Boolean? {
            lastSpawnInterval = interval
            return true
        }

        override fun getRespawnType(monster: Any): RespawnType? = respawnType

        override fun getTimeToChangeFiendish(monster: Any): Long? = timeToChangeFiendish

        override fun setTimeToChangeFiendish(monster: Any, endTime: Long): Boolean? {
            timeToChangeFiendish = endTime
            return true
        }

        override fun getMonsterForgeClassification(monster: Any): Int? = forgeClassification

        override fun setMonsterForgeClassification(monster: Any, classification: Int): Boolean? {
            forgeClassification = classification
            return true
        }

        override fun getForgeStack(monster: Any): Int? = forgeStack

        override fun setForgeStack(monster: Any, stack: Int): Boolean? {
            forgeStack = stack
            return true
        }

        override fun configureForgeSystem(monster: Any, stack: Int): Boolean? {
            forgeConfigured = true
            lastConfigureStack = stack
            return true
        }

        override fun clearFiendishStatus(monster: Any): Boolean? {
            clearedFiendish = true
            return true
        }

        override fun isForgeable(monster: Any): Boolean? = forgeable

        override fun setName(monster: Any, name: String, nameDescription: String?): Boolean? {
            lastName = name to nameDescription
            return true
        }

        override fun getHazard(monster: Any): Boolean? = hazards["hazard"] ?: false

        override fun setHazard(monster: Any, value: Boolean): Boolean? {
            hazards["hazard"] = value
            return true
        }

        override fun getHazardCrit(monster: Any): Boolean? = hazards["crit"] ?: false

        override fun setHazardCrit(monster: Any, value: Boolean): Boolean? {
            hazards["crit"] = value
            return true
        }

        override fun getHazardDodge(monster: Any): Boolean? = hazards["dodge"] ?: false

        override fun setHazardDodge(monster: Any, value: Boolean): Boolean? {
            hazards["dodge"] = value
            return true
        }

        override fun getHazardDamageBoost(monster: Any): Boolean? = hazards["damage"] ?: false

        override fun setHazardDamageBoost(monster: Any, value: Boolean): Boolean? {
            hazards["damage"] = value
            return true
        }

        override fun getHazardDefenseBoost(monster: Any): Boolean? = hazards["defense"] ?: false

        override fun setHazardDefenseBoost(monster: Any, value: Boolean): Boolean? {
            hazards["defense"] = value
            return true
        }

        override fun getSoulPit(monster: Any): Boolean? = hazards["soulPit"] ?: false

        override fun setSoulPit(monster: Any, value: Boolean): Boolean? {
            hazards["soulPit"] = value
            return true
        }

        override fun addReflectElement(monster: Any, element: Int, percent: Int): Boolean? {
            reflectElement = Triple(monster, element, percent)
            return true
        }

        override fun addDefense(monster: Any, defense: Int): Boolean? {
            defenseAdded = monster to defense
            this.defense += defense
            return true
        }

        override fun getDefense(monster: Any): Int? = defense

        override fun isDead(monster: Any): Boolean? = dead

        override fun isImmune(monster: Any): Boolean? = immune

        override fun setImmune(monster: Any, immune: Boolean): Boolean? {
            this.immune = immune
            return true
        }

        override fun getCriticalChance(monster: Any): Int? = criticalChance

        override fun setCriticalChance(monster: Any, chance: Int): Boolean? {
            criticalChance = chance
            return true
        }

        override fun getCriticalDamage(monster: Any): Int? = criticalDamage

        override fun setCriticalDamage(monster: Any, damage: Int): Boolean? {
            criticalDamage = damage
            return true
        }

        override fun addAttackSpell(monster: Any, spell: Any?): Boolean? {
            attackSpells += spell
            return true
        }

        override fun addDefenseSpell(monster: Any, spell: Any?): Boolean? {
            defenseSpells += spell
            return true
        }
    }

    private fun installed(fake: FakeOps = FakeOps()): Pair<LuaEnvironment, FakeOps> {
        val environment = LuaEnvironment().apply { initState() }
        MonsterFunctions.install(environment, fake)
        environment.globals.set("m", environment.userdata.wrap("Monster", FakeMonster()))
        return environment to fake
    }

    @Test
    fun `Monster constructor resolves an id and passes monster userdata through`() {
        val fake = FakeOps()
        val rat = FakeMonster()
        fake.monstersById[7] = rat
        val environment = LuaEnvironment().apply { initState() }
        MonsterFunctions.install(environment, fake)
        environment.globals.set("existing", environment.userdata.wrap("Monster", rat))
        val other = object {}

        environment.loadChunk("byId = Monster(7)")
        environment.loadChunk("byUserdata = Monster(existing)")
        environment.globals.set("other", environment.userdata.wrap("Item", other))
        environment.loadChunk("byOther = Monster(other)")
        environment.loadChunk("missing = Monster(99)")

        assertThat(environment.getGlobal("byId").touserdata()).isSameAs(rat)
        assertThat(environment.getGlobal("byUserdata").touserdata()).isSameAs(rat)
        assertThat(environment.getGlobal("byOther").isnil()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Monster setType resolves names and race ids and passes restoreHealth`() {
        val (environment, fake) = installed()
        val type = FakeMonsterType()
        fake.typeByRaceId = { type }

        environment.loadChunk("byRace = m:setType(34)")
        environment.loadChunk("missing = m:setType('Dragon', true)")

        assertThat(environment.getGlobal("byRace").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").toboolean()).isTrue()
        assertThat(fake.lastSetType).isEqualTo(Triple<Any, Any, Boolean>(environment.getGlobal("m").touserdata(), "Dragon", true))
    }

    @Test
    fun `Monster changeTargetDistance searchTarget and setSpawnPosition use Canary defaults`() {
        val (environment, fake) = installed()

        environment.loadChunk(
            """
            changed = m:changeTargetDistance(5)
            changedFull = m:changeTargetDistance(2, 500)
            searched = m:searchTarget()
            searchedHp = m:searchTarget(2)
            spawned = m:setSpawnPosition()
            spawnedFull = m:setSpawnPosition(30)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("changed").toboolean()).isTrue()
        assertThat(environment.getGlobal("changedFull").toboolean()).isTrue()
        assertThat(fake.lastChangeTargetDistance).isEqualTo(2 to 500)
        assertThat(environment.getGlobal("searched").toboolean()).isTrue()
        assertThat(fake.lastSearchType).isEqualTo(2)
        assertThat(environment.getGlobal("spawned").toboolean()).isTrue()
        assertThat(environment.getGlobal("spawnedFull").toboolean()).isTrue()
        assertThat(fake.lastSpawnInterval).isEqualTo(30)
    }

    @Test
    fun `Monster getRespawnType returns the period and underground flag`() {
        val (environment, _) = installed()

        environment.loadChunk("period, underground = m:getRespawnType()")

        assertThat(environment.getGlobal("period").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("underground").toboolean()).isTrue()
    }

    @Test
    fun `Monster fiendish and forge setters follow the C++ no-push convention`() {
        val (environment, fake) = installed()

        environment.loadChunk(
            """
            time = m:getTimeToChangeFiendish()
            setTime = m:setTimeToChangeFiendish(1234)
            classification = m:getMonsterForgeClassification()
            setClassification = m:setMonsterForgeClassification(2)
            stack = m:getForgeStack()
            setStack = m:setForgeStack(5)
            configured = m:configureForgeSystem()
            configuredFull = m:configureForgeSystem(9)
            cleared = m:clearFiendishStatus()
            forgeable = m:isForgeable()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("time").toint()).isZero()
        assertThat(environment.getGlobal("setTime").isnil()).isTrue()
        assertThat(fake.timeToChangeFiendish).isEqualTo(1234)
        assertThat(environment.getGlobal("classification").toint()).isZero()
        assertThat(environment.getGlobal("setClassification").isnil()).isTrue()
        assertThat(fake.forgeClassification).isEqualTo(2)
        assertThat(environment.getGlobal("stack").toint()).isZero()
        assertThat(environment.getGlobal("setStack").isnil()).isTrue()
        assertThat(fake.forgeStack).isEqualTo(5)
        assertThat(environment.getGlobal("configured").toboolean()).isTrue()
        assertThat(environment.getGlobal("configuredFull").toboolean()).isTrue()
        assertThat(fake.lastConfigureStack).isEqualTo(9)
        assertThat(environment.getGlobal("cleared").isnil()).isTrue()
        assertThat(fake.clearedFiendish).isTrue()
        assertThat(environment.getGlobal("forgeable").toboolean()).isTrue()
    }

    @Test
    fun `Monster hazard soulPit and immune getters and setters round-trip`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            hazard = m:hazard()
            setHazard = m:hazard(true)
            hazardAfter = m:hazard()
            crit = m:hazardCrit(true)
            dodge = m:hazardDodge(true)
            damage = m:hazardDamageBoost(true)
            defense = m:hazardDefenseBoost(true)
            pit = m:soulPit(true)
            pitAfter = m:soulPit()
            immune = m:immune()
            setImmune = m:immune(true)
            immuneAfter = m:immune()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("hazard").toboolean()).isFalse()
        assertThat(environment.getGlobal("setHazard").toboolean()).isTrue()
        assertThat(environment.getGlobal("hazardAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("crit").toboolean()).isTrue()
        assertThat(environment.getGlobal("dodge").toboolean()).isTrue()
        assertThat(environment.getGlobal("damage").toboolean()).isTrue()
        assertThat(environment.getGlobal("defense").toboolean()).isTrue()
        assertThat(environment.getGlobal("pit").toboolean()).isTrue()
        assertThat(environment.getGlobal("pitAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("immune").toboolean()).isFalse()
        assertThat(environment.getGlobal("setImmune").toboolean()).isTrue()
        assertThat(environment.getGlobal("immuneAfter").toboolean()).isTrue()
    }

    @Test
    fun `Monster defense reflect and dead state use the Canary names`() {
        val (environment, fake) = installed()

        environment.loadChunk(
            """
            defense = m:getDefense()
            added = m:addDefense(7)
            defenseAfter = m:getDefense()
            reflect = m:addReflectElement(3, 25)
            dead = m:isDead()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("defense").toint()).isZero()
        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("defenseAfter").toint()).isEqualTo(7)
        assertThat(fake.defenseAdded?.second).isEqualTo(7)
        assertThat(fake.reflectElement?.second).isEqualTo(3)
        assertThat(fake.reflectElement?.third).isEqualTo(25)
        assertThat(environment.getGlobal("reflect").toboolean()).isTrue()
        assertThat(environment.getGlobal("dead").toboolean()).isFalse()
    }

    @Test
    fun `Monster criticalChance and criticalDamage mirror the C++ boolean push`() {
        val (environment, fake) = installed()
        fake.criticalChance = 15
        fake.criticalDamage = 0

        environment.loadChunk("chance = m:criticalChance()")
        environment.loadChunk("setChance = m:criticalChance(0)")
        environment.loadChunk("chanceAfter = m:criticalChance()")
        environment.loadChunk("damage = m:criticalDamage()")
        environment.loadChunk("setDamage = m:criticalDamage(50)")
        environment.loadChunk("damageAfter = m:criticalDamage()")

        assertThat(environment.getGlobal("chance").toboolean()).isTrue()
        assertThat(environment.getGlobal("setChance").toboolean()).isFalse()
        assertThat(environment.getGlobal("chanceAfter").toboolean()).isFalse()
        assertThat(fake.criticalChance).isZero()
        assertThat(environment.getGlobal("damage").toboolean()).isFalse()
        assertThat(environment.getGlobal("setDamage").toboolean()).isTrue()
        assertThat(environment.getGlobal("damageAfter").toboolean()).isTrue()
        assertThat(fake.criticalDamage).isEqualTo(50)
    }

    @Test
    fun `Monster addAttackSpell and addDefenseSpell always evaluate to nil`() {
        val (environment, fake) = installed()
        val spell = FakeMonsterSpell()
        environment.globals.set("spell", environment.userdata.wrap("MonsterSpell", spell))

        environment.loadChunk("attack = m:addAttackSpell(spell)")
        environment.loadChunk("defense = m:addDefenseSpell(spell)")
        environment.loadChunk("missing = m:addAttackSpell(nil)")

        assertThat(environment.getGlobal("attack").isnil()).isTrue()
        assertThat(environment.getGlobal("defense").isnil()).isTrue()
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
        assertThat(fake.attackSpells).containsExactly(spell)
        assertThat(fake.defenseSpells).containsExactly(spell)
    }

    @Test
    fun `Monster setName stores the optional name description`() {
        val (environment, fake) = installed()

        environment.loadChunk("renamed = m:setName('Giant Rat')")
        environment.loadChunk("described = m:setName('Giant Rat', 'a giant rat')")

        assertThat(environment.getGlobal("renamed").toboolean()).isTrue()
        assertThat(environment.getGlobal("described").toboolean()).isTrue()
        assertThat(fake.lastName).isEqualTo("Giant Rat" to "a giant rat")
    }
}
