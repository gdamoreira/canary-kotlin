package org.opentibia.canary.lua.bindings.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Vocation` metatable ported from
 * src/lua/functions/creatures/player/vocation_functions.cpp.
 */
class VocationFunctionsBindingsTest {

    private class FakeVocationOperations : VocationOperations {
        var lookup: (Any) -> Any? = { null }
        var demotion: Any? = null
        var promotion: Any? = null
        var lastCreated: Any? = null
        var lastSkill: List<Int>? = null
        var lastMagicLevel: Int? = null
        var requiredSkillTries = 25600L
        var requiredManaSpent = 4800L

        override fun create(idOrName: Any): Any? {
            lastCreated = idOrName
            return lookup(idOrName)
        }

        override fun getId(vocation: Any): Int? = (vocation as? Vocation)?.getId()

        override fun getClientId(vocation: Any): Int? = (vocation as? Vocation)?.getClientId()

        override fun getBaseId(vocation: Any): Int? = (vocation as? Vocation)?.getBaseId()

        override fun getName(vocation: Any): String? = (vocation as? Vocation)?.getVocName()

        override fun getDescription(vocation: Any): String? = (vocation as? Vocation)?.getVocDescription()

        override fun getRequiredSkillTries(vocation: Any, skillType: Int, skillLevel: Int): Long? {
            lastSkill = listOf(skillType, skillLevel)
            return requiredSkillTries
        }

        override fun getRequiredManaSpent(vocation: Any, magicLevel: Int): Long? {
            lastMagicLevel = magicLevel
            return requiredManaSpent
        }

        override fun getCapacityGain(vocation: Any): Int? = (vocation as? Vocation)?.getCapGain()

        override fun getHealthGain(vocation: Any): Int? = (vocation as? Vocation)?.getHPGain()

        override fun getHealthGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getHealthGainTicks()

        override fun getHealthGainAmount(vocation: Any): Int? = (vocation as? Vocation)?.getHealthGainAmount()

        override fun getManaGain(vocation: Any): Int? = (vocation as? Vocation)?.getManaGain()

        override fun getManaGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getManaGainTicks()

        override fun getManaGainAmount(vocation: Any): Int? = (vocation as? Vocation)?.getManaGainAmount()

        override fun getMaxSoul(vocation: Any): Int? = (vocation as? Vocation)?.getSoulMax()

        override fun getSoulGainTicks(vocation: Any): Int? = (vocation as? Vocation)?.getSoulGainTicks()

        override fun getBaseAttackSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getBaseAttackSpeed()

        override fun getAttackSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getAttackSpeed()

        override fun getBaseSpeed(vocation: Any): Int? = (vocation as? Vocation)?.getBaseSpeed()

        override fun getDemotion(vocation: Any): Any? = demotion

        override fun getPromotion(vocation: Any): Any? = promotion
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(fake: FakeVocationOperations = FakeVocationOperations()): Pair<LuaEnvironment, FakeVocationOperations> {
        val environment = env()
        VocationFunctions.install(environment, fake)
        return environment to fake
    }

    private fun knight(vocations: Vocations): Vocation = requireNotNull(vocations.getVocation(1))

    private fun loadedVocations(): Vocations {
        val vocations = Vocations()
        vocations.loadFromXml(
            """
            <vocations>
                <vocation id="1" name="Knight" clientid="1" baseid="1" description="A knight"
                          gaincap="5" gainhp="15" gainmana="5" gainhpticks="6" gainhpamount="1"
                          gainmanaticks="6" gainmanaamount="2" manamultiplier="3.0"
                          attackspeed="2000" basespeed="110" soulmax="100" gainsoulticks="120000"
                          fromvoc="0"/>
                <vocation id="2" name="Druid" clientid="4" baseid="2" fromvoc="0"/>
                <vocation id="8" name="Elite Knight" clientid="1" baseid="1" fromvoc="1"/>
            </vocations>
            """.trimIndent(),
        )
        return vocations
    }

    @Test
    fun `Vocation registers the Canary global and method names`() {
        val environment = env()
        val methods = listOf(
            "getId", "getClientId", "getBaseId", "getName", "getDescription",
            "getRequiredSkillTries", "getRequiredManaSpent", "getCapacityGain",
            "getHealthGain", "getHealthGainTicks", "getHealthGainAmount",
            "getManaGain", "getManaGainTicks", "getManaGainAmount",
            "getMaxSoul", "getSoulGainTicks",
            "getBaseAttackSpeed", "getAttackSpeed", "getBaseSpeed",
            "getDemotion", "getPromotion",
        )
        val checks = methods.joinToString(" and ") { "(rawgetmetatable('Vocation').__metatable.$it ~= nil)" }
        environment.loadChunk("callable = (type(Vocation) == 'table') and (getmetatable(Vocation).__call ~= nil)\nmethods = $checks")

        assertThat(environment.getGlobal("callable").toboolean()).isTrue()
        assertThat(environment.getGlobal("methods").toboolean()).isTrue()
    }

    @Test
    fun `Vocation factory resolves numbers names and the Canary string fallback`() {
        val (environment, fake) = installed()
        fake.lookup = { argument -> if (argument == 1 || argument == "Knight") argument else null }
        environment.loadChunk(
            """
            byId = Vocation(1)
            byName = Vocation("Knight")
            invalid = Vocation(true)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").touserdata()).isEqualTo(1)
        assertThat(environment.getGlobal("byName").touserdata()).isEqualTo("Knight")
        assertThat(environment.getGlobal("invalid").isnil()).isTrue()
        assertThat(fake.lastCreated).isEqualTo("")
    }

    @Test
    fun `Vocation getters forward every Canary primitive`() {
        val vocations = loadedVocations()
        val (environment, fake) = installed()
        fake.lookup = { argument -> if (argument == 1) knight(vocations) else null }
        environment.loadChunk(
            """
            v = Vocation(1)
            id = v:getId()
            clientId = v:getClientId()
            baseId = v:getBaseId()
            name = v:getName()
            description = v:getDescription()
            skillTries = v:getRequiredSkillTries(2, 20)
            manaSpent = v:getRequiredManaSpent(2)
            capacity = v:getCapacityGain()
            healthGain = v:getHealthGain()
            healthTicks = v:getHealthGainTicks()
            healthAmount = v:getHealthGainAmount()
            manaGain = v:getManaGain()
            manaTicks = v:getManaGainTicks()
            manaAmount = v:getManaGainAmount()
            maxSoul = v:getMaxSoul()
            soulTicks = v:getSoulGainTicks()
            baseAttack = v:getBaseAttackSpeed()
            attack = v:getAttackSpeed()
            baseSpeed = v:getBaseSpeed()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("id").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("clientId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("baseId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Knight")
        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("A knight")
        assertThat(environment.getGlobal("skillTries").toint()).isEqualTo(25600)
        assertThat(environment.getGlobal("manaSpent").toint()).isEqualTo(4800)
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("healthGain").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("healthTicks").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("healthAmount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("manaGain").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("manaTicks").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("manaAmount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("maxSoul").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("soulTicks").toint()).isEqualTo(120000)
        assertThat(environment.getGlobal("baseAttack").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("attack").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("baseSpeed").toint()).isEqualTo(110)
        assertThat(fake.lastSkill).containsExactly(2, 20)
        assertThat(fake.lastMagicLevel).isEqualTo(2)
    }

    @Test
    fun `Vocation demotion and promotion push the resolved userdata`() {
        val vocations = loadedVocations()
        val knight = knight(vocations)
        val elite = requireNotNull(vocations.getVocation(8))
        val environment = env()
        VocationFunctions.install(
            environment,
            WorldVocationOperations(vocations),
        )
        environment.globals.set("k", environment.userdata.wrap("Vocation", knight))
        environment.globals.set("e", environment.userdata.wrap("Vocation", elite))
        environment.loadChunk(
            """
            promotion = k:getPromotion()
            noDemotion = k:getDemotion()
            demotion = e:getDemotion()
            noPromotion = e:getPromotion()
            promotedId = promotion:getId()
            demotedId = demotion:getId()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("promotion").touserdata()).isSameAs(elite)
        assertThat(environment.getGlobal("noDemotion").isnil()).isTrue()
        assertThat(environment.getGlobal("demotion").touserdata()).isSameAs(knight)
        assertThat(environment.getGlobal("noPromotion").isnil()).isTrue()
        assertThat(environment.getGlobal("promotedId").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("demotedId").toint()).isEqualTo(1)
    }

    @Test
    fun `World Vocation answers through the Vocations registry`() {
        val vocations = loadedVocations()
        val environment = env()
        VocationFunctions.install(environment, WorldVocationOperations(vocations))
        environment.loadChunk(
            """
            byId = Vocation(1)
            byName = Vocation("Elite Knight")
            byLowerName = Vocation("druid")
            unknownName = Vocation("Bard")
            unknownId = Vocation(99)
            name = byName:getName()
            clientId = byName:getClientId()
            skillTries = byId:getRequiredSkillTries(2, 20)
            manaSpent = byId:getRequiredManaSpent(2)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").touserdata()).isSameAs(knight(vocations))
        assertThat(environment.getGlobal("byName").touserdata()).isSameAs(vocations.getVocation(8))
        assertThat(environment.getGlobal("byLowerName").touserdata()).isSameAs(vocations.getVocation(2))
        assertThat(environment.getGlobal("unknownName").isnil()).isTrue()
        assertThat(environment.getGlobal("unknownId").isnil()).isTrue()
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Elite Knight")
        assertThat(environment.getGlobal("clientId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("skillTries").toint()).isEqualTo(25600)
        assertThat(environment.getGlobal("manaSpent").toint()).isEqualTo(4800)
    }

    @Test
    fun `World Vocation pushes nil for every missing registry lookup`() {
        val environment = env()
        VocationFunctions.install(environment, WorldVocationOperations(null))
        environment.globals.set("v", environment.userdata.wrap("Vocation", Vocation(1)))
        environment.loadChunk(
            """
            byId = Vocation(1)
            byName = Vocation("Knight")
            demotion = v:getDemotion()
            promotion = v:getPromotion()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("byId").isnil()).isTrue()
        assertThat(environment.getGlobal("byName").isnil()).isTrue()
        assertThat(environment.getGlobal("demotion").isnil()).isTrue()
        assertThat(environment.getGlobal("promotion").isnil()).isTrue()
    }
}
