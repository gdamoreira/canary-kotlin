package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldPlayerOperations
import org.opentibia.canary.map.Position

/**
 * Transcript-style tests for the extended Canary `Player` binding surface. Each test
 * installs [PlayerFunctions] with a fake [PlayerOperations] seam and drives it through
 * real Lua chunks using the exact Canary method names from
 * src/lua/functions/creatures/player/player_functions.cpp.
 */
class PlayerBindingsTest {

    private class FakePlayer

    private class FakePlayerOps : StubPlayerOperations() {
        var guid = 12345L
        var capacity = 500L
        var freeCapacity = 350L
        var lastCapacity: Long? = null
        var maxMana = 200
        var lastMaxMana: Int? = null
        var magicLevel = 12
        var skillLevel = 45
        var skillTries = 999L
        var premiumDays: Int? = 30
        var fightMode = 2
        var chaseMode = true
        var secureMode = false
        var speed = 220
        var lastSpeed: Int? = null

        override fun itemIdByName(name: String): Int = 0

        override fun addItem(
            player: Any,
            itemId: Int,
            count: Int,
            canDropOnMap: Boolean,
            subType: Int,
            slot: Int,
            tier: Int,
        ): List<Any> = emptyList()

        override fun removeItem(
            player: Any,
            itemId: Int,
            count: Int,
            subType: Int,
            ignoreEquipped: Boolean,
            ignoreStoreInbox: Boolean,
        ): Boolean = false

        override fun getItemCount(
            player: Any,
            itemId: Int,
            subType: Int,
            ignoreEquipped: Boolean,
            ignoreStoreInbox: Boolean,
        ): Long = 0L

        override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean = true

        override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean = true

        override fun getStorageValue(player: Any, key: Int): Int = -1

        override fun setStorageValue(player: Any, key: Int, value: Int): Boolean = true

        override fun getLevel(player: Any): Int = 1

        override fun getExperience(player: Any): Long = 0L

        override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean = true

        override fun getMana(player: Any): Int = 0

        override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean = true

        override fun getVocation(player: Any): Any = Unit

        override fun getGuid(player: Any): Long? = guid

        override fun getSpeed(player: Any): Int? = speed

        override fun setSpeed(player: Any, speed: Int): Boolean {
            lastSpeed = speed
            this.speed = speed
            return true
        }

        override fun getCapacity(player: Any): Long? = capacity

        override fun setCapacity(player: Any, capacity: Long): Boolean {
            lastCapacity = capacity
            this.capacity = capacity
            return true
        }

        override fun getFreeCapacity(player: Any): Long? = freeCapacity

        override fun getMaxMana(player: Any): Int? = maxMana

        override fun setMaxMana(player: Any, maxMana: Int): Boolean {
            lastMaxMana = maxMana
            this.maxMana = maxMana
            return true
        }

        override fun getMagicLevel(player: Any): Int = magicLevel

        override fun getSkillLevel(player: Any, skillType: Int): Int? =
            if (skillType in 0..12) skillLevel else null

        override fun getSkillTries(player: Any, skillType: Int): Long? =
            if (skillType in 0..12) skillTries else null

        override fun getPremiumDays(player: Any): Int? = premiumDays

        override fun getFightMode(player: Any): Int? = fightMode

        override fun hasChaseMode(player: Any): Boolean = chaseMode

        override fun hasSecureMode(player: Any): Boolean = secureMode
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakePlayerOps = FakePlayerOps()): Pair<LuaEnvironment, FakePlayerOps> {
        val environment = env()
        PlayerFunctions.install(environment, fake)
        environment.globals.set("p", environment.userdata.wrap("Player", FakePlayer()))
        return environment to fake
    }

    @Test
    fun `Player getGuid returns the identity used by storage and houses`() {
        val (environment, _) = installed()
        environment.loadChunk("guid = p:getGuid()")
        assertThat(environment.getGlobal("guid").toint()).isEqualTo(12345)
    }

    @Test
    fun `Player capacity methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk("capacity = p:getCapacity()")
        environment.loadChunk("free = p:getFreeCapacity()")
        environment.loadChunk("set = p:setCapacity(600)")

        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("free").toint()).isEqualTo(350)
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(fake.lastCapacity).isEqualTo(600L)
    }

    @Test
    fun `Player mana and magic level methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk("maxMana = p:getMaxMana()")
        environment.loadChunk("setMax = p:setMaxMana(250)")
        environment.loadChunk("magic = p:getMagicLevel()")

        assertThat(environment.getGlobal("maxMana").toint()).isEqualTo(200)
        assertThat(environment.getGlobal("setMax").toboolean()).isTrue()
        assertThat(fake.lastMaxMana).isEqualTo(250)
        assertThat(environment.getGlobal("magic").toint()).isEqualTo(12)
    }

    @Test
    fun `Player skill methods return nil for invalid skill types`() {
        val (environment, _) = installed()
        environment.loadChunk("level = p:getSkillLevel(2)")
        environment.loadChunk("tries = p:getSkillTries(2)")
        environment.loadChunk("badLevel = p:getSkillLevel(99)")
        environment.loadChunk("badTries = p:getSkillTries(-1)")

        assertThat(environment.getGlobal("level").toint()).isEqualTo(45)
        assertThat(environment.getGlobal("tries").toint()).isEqualTo(999)
        assertThat(environment.getGlobal("badLevel").isnil()).isTrue()
        assertThat(environment.getGlobal("badTries").isnil()).isTrue()
    }

    @Test
    fun `Player speed methods use the Canary names`() {
        val (environment, fake) = installed()
        environment.loadChunk("speed = p:getSpeed()")
        environment.loadChunk("changed = p:setSpeed(300)")

        assertThat(environment.getGlobal("speed").toint()).isEqualTo(220)
        assertThat(environment.getGlobal("changed").toboolean()).isTrue()
        assertThat(fake.lastSpeed).isEqualTo(300)
    }

    @Test
    fun `Player premium days use the Canary name`() {
        val (environment, fake) = installed()
        environment.loadChunk("days = p:getPremiumDays()")
        assertThat(environment.getGlobal("days").toint()).isEqualTo(30)

        fake.premiumDays = null
        environment.loadChunk("noAccount = p:getPremiumDays()")
        assertThat(environment.getGlobal("noAccount").isnil()).isTrue()
    }

    @Test
    fun `Player fight chase and secure mode methods use the Canary names`() {
        val (environment, _) = installed()
        environment.loadChunk("fight = p:getFightMode()")
        environment.loadChunk("chase = p:hasChaseMode()")
        environment.loadChunk("secure = p:hasSecureMode()")

        assertThat(environment.getGlobal("fight").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("chase").toboolean()).isTrue()
        assertThat(environment.getGlobal("secure").toboolean()).isFalse()
    }

    @Test
    fun `Player sex and town methods are Canary no-ops without domain state`() {
        val (environment, _) = installed()
        environment.loadChunk("sex = p:getSex()")
        environment.loadChunk("sexSet = p:setSex(1)")
        environment.loadChunk("town = p:getTown()")
        environment.loadChunk("townSet = p:setTown(nil)")

        assertThat(environment.getGlobal("sex").toint()).isZero()
        assertThat(environment.getGlobal("sexSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("town").isnil()).isTrue()
        assertThat(environment.getGlobal("townSet").toboolean()).isFalse()
    }

    @Test
    fun `extended Player bindings register on the Player Monster and Npc metatables`() {
        val (environment, _) = installed()
        val names = listOf(
            "getGuid", "getSpeed", "setSpeed", "getCapacity", "setCapacity",
            "getFreeCapacity", "getMaxMana", "setMaxMana", "getMagicLevel",
            "getSkillLevel", "getSkillTries", "getSex", "setSex", "getTown", "setTown",
            "getPremiumDays", "getFightMode", "hasChaseMode", "hasSecureMode",
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
    fun `real Player domain answers the extended surface through WorldPlayerOperations`() {
        val environment = env()
        PlayerFunctions.install(environment, WorldPlayerOperations())
        val player = Player(
            id = 777L,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 90,
            mana = 90,
            capacity = 500.0,
            position = Position(10, 10, 7),
            level = 8,
        )
        player.account = Account(
            id = 1,
            name = "acct",
            passwordHash = "x",
            premiumLastDay = getTimeNow() + 30 * Account.SECONDS_PER_DAY,
        )
        player.fightMode = FightMode.Defense
        player.chaseMode = ChaseMode.Follow
        player.secureMode = true
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            guid = p:getGuid()
            cap = p:getCapacity()
            free = p:getFreeCapacity()
            setCap = p:setCapacity(700)
            maxMana = p:getMaxMana()
            magic = p:getMagicLevel()
            fishing = p:getSkillLevel(6)
            tries = p:getSkillTries(6)
            badSkill = p:getSkillLevel(99)
            days = p:getPremiumDays()
            fight = p:getFightMode()
            chase = p:hasChaseMode()
            secure = p:hasSecureMode()
            speed = p:getSpeed()
            setSpeed = p:setSpeed(310)
            newSpeed = p:getSpeed()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("guid").toint()).isEqualTo(777)
        assertThat(environment.getGlobal("cap").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("free").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("setCap").toboolean()).isTrue()
        assertThat(player.capacity).isEqualTo(700.0)
        assertThat(environment.getGlobal("maxMana").toint()).isEqualTo(90)
        assertThat(environment.getGlobal("magic").toint()).isZero()
        assertThat(environment.getGlobal("fishing").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("tries").toint()).isZero()
        assertThat(environment.getGlobal("badSkill").isnil()).isTrue()
        assertThat(environment.getGlobal("days").toint()).isBetween(29, 30)
        assertThat(environment.getGlobal("fight").toint()).isEqualTo(FightMode.Defense.ordinal + 1)
        assertThat(environment.getGlobal("chase").toboolean()).isTrue()
        assertThat(environment.getGlobal("secure").toboolean()).isTrue()
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(220)
        assertThat(environment.getGlobal("setSpeed").toboolean()).isTrue()
        assertThat(environment.getGlobal("newSpeed").toint()).isEqualTo(310)
    }
}
