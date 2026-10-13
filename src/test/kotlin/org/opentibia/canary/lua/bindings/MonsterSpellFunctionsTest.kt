package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `MonsterSpell` binding group, ported from
 * src/lua/functions/creatures/monster/monster_spell_functions.cpp. The userdata is a
 * plain data holder, so no engine seam is required.
 */
class MonsterSpellFunctionsTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply {
        initState()
        MonsterSpellFunctions.install(this)
    }

    @Test
    fun `MonsterSpell constructor creates a fresh spell userdata`() {
        val environment = env()

        environment.loadChunk("spell = MonsterSpell()")
        environment.loadChunk("other = MonsterSpell()")

        assertThat(environment.getGlobal("spell").touserdata()).isInstanceOf(MonsterSpellData::class.java)
        assertThat(environment.getGlobal("other").touserdata()).isNotSameAs(environment.getGlobal("spell").touserdata())
    }

    @Test
    fun `MonsterSpell string and chance setters store the Canary values`() {
        val environment = env()

        environment.loadChunk(
            """
            spell = MonsterSpell()
            type = spell:setType('fire')
            script = spell:setScriptName('monster_fire')
            chance = spell:setChance(25)
            interval = spell:setInterval(1500)
            range = spell:setRange(6)
            monster = spell:setOutfitMonster('Dragon')
            item = spell:setOutfitItem(2160)
            """.trimIndent(),
        )

        val spell = environment.getGlobal("spell").touserdata() as MonsterSpellData
        assertThat(environment.getGlobal("type").toboolean()).isTrue()
        assertThat(environment.getGlobal("script").toboolean()).isTrue()
        assertThat(environment.getGlobal("chance").toboolean()).isTrue()
        assertThat(environment.getGlobal("interval").toboolean()).isTrue()
        assertThat(environment.getGlobal("range").toboolean()).isTrue()
        assertThat(environment.getGlobal("monster").toboolean()).isTrue()
        assertThat(environment.getGlobal("item").toboolean()).isTrue()
        assertThat(spell.name).isEqualTo("fire")
        assertThat(spell.scriptName).isEqualTo("monster_fire")
        assertThat(spell.chance).isEqualTo(25)
        assertThat(spell.interval).isEqualTo(1500)
        assertThat(spell.range).isEqualTo(6)
        assertThat(spell.outfitMonster).isEqualTo("Dragon")
        assertThat(spell.outfitItem).isEqualTo(2160)
    }

    @Test
    fun `MonsterSpell combat and condition setters store the Canary values`() {
        val environment = env()

        environment.loadChunk(
            """
            spell = MonsterSpell()
            combat = spell:setCombatValue(10, 20)
            combatType = spell:setCombatType(1)
            attack = spell:setAttackValue(30, 5)
            target = spell:setNeedTarget(true)
            length = spell:setCombatLength(3)
            spread = spell:setCombatSpread(2)
            radius = spell:setCombatRadius(4)
            condition = spell:setConditionType(3)
            damage = spell:setConditionDamage(1, 9, 4)
            speed = spell:setConditionSpeedChange(-20)
            duration = spell:setConditionDuration(5000)
            tick = spell:setConditionTickInterval(1000)
            shoot = spell:setCombatShootEffect(7)
            effect = spell:setCombatEffect(9)
            """.trimIndent(),
        )

        val spell = environment.getGlobal("spell").touserdata() as MonsterSpellData
        assertThat(environment.getGlobal("combat").toboolean()).isTrue()
        assertThat(environment.getGlobal("combatType").toboolean()).isTrue()
        assertThat(environment.getGlobal("attack").toboolean()).isTrue()
        assertThat(environment.getGlobal("target").toboolean()).isTrue()
        assertThat(environment.getGlobal("length").toboolean()).isTrue()
        assertThat(environment.getGlobal("spread").toboolean()).isTrue()
        assertThat(environment.getGlobal("radius").toboolean()).isTrue()
        assertThat(environment.getGlobal("condition").toboolean()).isTrue()
        assertThat(environment.getGlobal("damage").toboolean()).isTrue()
        assertThat(environment.getGlobal("speed").toboolean()).isTrue()
        assertThat(environment.getGlobal("duration").toboolean()).isTrue()
        assertThat(environment.getGlobal("tick").toboolean()).isTrue()
        assertThat(environment.getGlobal("shoot").toboolean()).isTrue()
        assertThat(environment.getGlobal("effect").toboolean()).isTrue()
        assertThat(spell.minCombatValue).isEqualTo(10)
        assertThat(spell.maxCombatValue).isEqualTo(20)
        assertThat(spell.combatType).isEqualTo(1)
        assertThat(spell.attack).isEqualTo(30)
        assertThat(spell.skill).isEqualTo(5)
        assertThat(spell.needTarget).isTrue()
        assertThat(spell.length).isEqualTo(3)
        assertThat(spell.spread).isEqualTo(2)
        assertThat(spell.radius).isEqualTo(4)
        assertThat(spell.conditionType).isEqualTo(3)
        assertThat(spell.conditionMinDamage).isEqualTo(1)
        assertThat(spell.conditionMaxDamage).isEqualTo(9)
        assertThat(spell.conditionStartDamage).isEqualTo(4)
        assertThat(spell.speedChange).isEqualTo(-20)
        assertThat(spell.duration).isEqualTo(5000)
        assertThat(spell.tickInterval).isEqualTo(1000)
        assertThat(spell.shoot).isEqualTo(7)
        assertThat(spell.effect).isEqualTo(9)
    }

    @Test
    fun `MonsterSpell setConditionType rejects the none condition 254`() {
        val environment = env()

        environment.loadChunk(
            """
            spell = MonsterSpell()
            accepted = spell:setConditionType(3)
            rejected = spell:setConditionType(254)
            """.trimIndent(),
        )

        val spell = environment.getGlobal("spell").touserdata() as MonsterSpellData
        assertThat(environment.getGlobal("accepted").toboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").toboolean()).isFalse()
        assertThat(spell.conditionType).isEqualTo(3)
    }

    @Test
    fun `MonsterSpell castSound and impactSound get and set`() {
        val environment = env()

        environment.loadChunk(
            """
            spell = MonsterSpell()
            castBefore = spell:castSound()
            cast = spell:castSound(12)
            castAfter = spell:castSound()
            impactBefore = spell:impactSound()
            impact = spell:impactSound(4)
            impactAfter = spell:impactSound()
            """.trimIndent(),
        )

        val spell = environment.getGlobal("spell").touserdata() as MonsterSpellData
        assertThat(environment.getGlobal("castBefore").toint()).isZero()
        assertThat(environment.getGlobal("cast").toboolean()).isTrue()
        assertThat(environment.getGlobal("castAfter").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("impactBefore").toint()).isZero()
        assertThat(environment.getGlobal("impact").toboolean()).isTrue()
        assertThat(environment.getGlobal("impactAfter").toint()).isEqualTo(4)
        assertThat(spell.soundCastEffect).isEqualTo(12)
        assertThat(spell.soundImpactEffect).isEqualTo(4)
    }
}
