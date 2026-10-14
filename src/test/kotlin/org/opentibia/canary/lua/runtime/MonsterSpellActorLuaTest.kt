package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.monsters.MonsterCombatSpell
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.lua.bindings.MonsterRegisteredSpell

/**
 * Transcript tests for the monster spell actor built by
 * `Monsters::deserializeSpell` (src/creatures/monsters/monsters.cpp:61-283):
 * `addAttack`/`addDefense` keep the combat type/effect/shoot params inside the
 * `CombatSpell` actor, `getAttackList`/`getDefenseList` expose it as the `spell`
 * userdata, and `Loot:addChildLoot` nested loot survives `addLoot`/`getLoot`.
 */
class MonsterSpellActorLuaTest {

    private class Fixture(
        val registry: Monsters = Monsters(),
        val env: LuaEnvironment = LuaEnvironment().apply { initState() },
    ) {
        init {
            env.worldOperations!!.attachMonsters(registry)
        }
    }

    @Test
    fun `addAttack builds the combat spell actor with type effect and shoot`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local combat = MonsterSpell()
            combat:setType("combat")
            combat:setCombatType(COMBAT_FIREDAMAGE)
            combat:setChance(25)
            combat:setRange(5)
            combat:setCombatValue(-5, -20)
            combat:setCombatShootEffect(CONST_ANI_FIRE)
            combat:setCombatEffect(CONST_ME_FIREAREA)
            mType:addAttack(combat)
            list = mType:getAttackList()
            """.trimIndent(),
        )

        val block = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single()
        assertThat(block.combatSpell).isTrue()
        assertThat(block.chance).isEqualTo(25)
        assertThat(block.range).isEqualTo(5)
        assertThat(block.minCombatValue).isEqualTo(-20)
        assertThat(block.maxCombatValue).isEqualTo(-5)

        val actor = block.spell as? MonsterCombatSpell
        assertThat(actor).isNotNull()
        assertThat(actor!!.combat.params.combatType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(actor.combat.params.impactEffect).isEqualTo(CONST_ME_FIREAREA)
        assertThat(actor.combat.params.distanceEffect).isEqualTo(CONST_ANI_FIRE)

        val spellField = fixture.env.getGlobal("list").get(1).get("spell")
        assertThat(spellField.isnil()).isFalse()
        assertThat(spellField.touserdata()).isSameAs(actor)
    }

    @Test
    fun `registered spell name resolves the registry actor instead of building one`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local registered = Spell("instant")
            registered:name("Light Healing")
            registered:id(1)
            registered:words("exura")
            function registered.onCastSpell(creature, variant) return true end
            registered:register()

            local mType = Game.createMonsterType("Rat")
            local heal = MonsterSpell()
            heal:setType("Light Healing")
            heal:setInterval(1500)
            heal:setChance(140)
            heal:setRange(200)
            mType:addAttack(heal)
            """.trimIndent(),
        )

        val block = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single()
        val actor = block.spell as? MonsterRegisteredSpell
        assertThat(actor).isNotNull()
        assertThat(actor!!.spell).isSameAs(fixture.env.spells.getInstantByName("Light Healing"))
        assertThat(actor.spell).isSameAs(fixture.env.spells.getInstant("exura"))
        assertThat(block.combatSpell).isFalse()
        assertThat(block.isMelee).isFalse()
        assertThat(block.chance).isEqualTo(100)
        assertThat(block.speed).isEqualTo(1500)
        assertThat(block.range).isEqualTo(22)
        assertThat(block.minCombatValue).isZero()
        assertThat(block.maxCombatValue).isZero()
    }

    @Test
    fun `unregistered spell name keeps building the combat actor`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local combat = MonsterSpell()
            combat:setType("combat")
            combat:setCombatType(COMBAT_FIREDAMAGE)
            mType:addAttack(combat)
            """.trimIndent(),
        )

        val block = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single()
        assertThat(block.spell).isInstanceOf(MonsterCombatSpell::class.java)
        assertThat(block.combatSpell).isTrue()
    }

    @Test
    fun `melee attack blocks keep the physical origin and block flags`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local melee = MonsterSpell()
            melee:setType("melee")
            melee:setAttackValue(30, 5)
            mType:addAttack(melee)
            """.trimIndent(),
        )

        val block = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single()
        assertThat(block.isMelee).isTrue()
        assertThat(block.range).isEqualTo(1)
        assertThat(block.minCombatValue).isZero()

        val actor = block.spell as? MonsterCombatSpell
        assertThat(actor).isNotNull()
        assertThat(actor!!.combat.params.combatType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(actor.combat.params.blockedByArmor).isTrue()
        assertThat(actor.combat.params.blockedByShield).isTrue()
        assertThat(actor.combat.params.origin).isEqualTo(CombatOrigin.MELEE)
    }

    @Test
    fun `defense blocks expose the actor through getDefenseList`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local ward = MonsterSpell()
            ward:setType("combat")
            ward:setCombatType(COMBAT_HEALING)
            ward:setCombatValue(10, 30)
            mType:addDefense(ward)
            list = mType:getDefenseList()
            """.trimIndent(),
        )

        val block = fixture.registry.getMonsterType("rat")!!.info.defenseSpells.single()
        val actor = block.spell as? MonsterCombatSpell
        assertThat(actor).isNotNull()
        assertThat(actor!!.combat.params.combatType).isEqualTo(CombatType.HEALING)
        assertThat(actor.combat.params.aggressive).isFalse()

        val spellField = fixture.env.getGlobal("list").get(1).get("spell")
        assertThat(spellField.isnil()).isFalse()
        assertThat(spellField.touserdata()).isSameAs(actor)
    }

    @Test
    fun `condition spells build the damage condition with the clamped values`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local fire = MonsterSpell()
            fire:setType("combat")
            fire:setCombatType(COMBAT_FIREDAMAGE)
            fire:setConditionType(CONDITION_FIRE)
            fire:setConditionDamage(10, 40, 5)
            fire:setConditionTickInterval(1000)
            mType:addAttack(fire)
            """.trimIndent(),
        )

        val actor = fixture.registry.getMonsterType("rat")!!.info.attackSpells.single().spell as MonsterCombatSpell
        val condition = actor.combat.params.conditionList.single()
        assertThat(condition).isInstanceOf(ConditionDamage::class.java)
        val damage = condition as ConditionDamage
        assertThat(damage.type).isEqualTo(ConditionType.CONDITION_FIRE)
        assertThat(damage.minDamage).isEqualTo(10)
        assertThat(damage.maxDamage).isEqualTo(40)
        assertThat(damage.startDamage).isEqualTo(5)
        assertThat(damage.tickInterval).isEqualTo(1000)
    }

    @Test
    fun `addLoot keeps the nested child loot round trip`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local parent = Loot("Rat")
            parent:setId(2854)
            parent:setChance(10000)
            parent:setSubType(3)
            parent:setActionId(7)
            parent:setText("nested")
            local child = Loot("Rat")
            child:setId(2160)
            child:setChance(50000)
            child:setMaxCount(5)
            parent:addChildLoot(child)
            mType:addLoot(parent)
            table = mType:getLoot()
            """.trimIndent(),
        )

        val stored = fixture.registry.getMonsterType("rat")!!.info.lootItems.single()
        assertThat(stored.id).isEqualTo(2854)
        assertThat(stored.chance).isEqualTo(10000)
        assertThat(stored.subType).isEqualTo(3)
        assertThat(stored.actionId).isEqualTo(7)
        assertThat(stored.text).isEqualTo("nested")
        assertThat(stored.childLoot).hasSize(1)
        assertThat(stored.childLoot.single().id).isEqualTo(2160)
        assertThat(stored.childLoot.single().chance).isEqualTo(50000)
        assertThat(stored.childLoot.single().countMax).isEqualTo(5)

        val parentTable = fixture.env.getGlobal("table").get(1)
        assertThat(parentTable.get("itemId").toint()).isEqualTo(2854)
        assertThat(parentTable.get("subType").toint()).isEqualTo(3)
        assertThat(parentTable.get("actionId").toint()).isEqualTo(7)
        assertThat(parentTable.get("text").tojstring()).isEqualTo("nested")
        val childTable = parentTable.get("childLoot").get(1)
        assertThat(childTable.get("itemId").toint()).isEqualTo(2160)
        assertThat(childTable.get("chance").toint()).isEqualTo(50000)
        assertThat(childTable.get("maxCount").toint()).isEqualTo(5)
    }

    private companion object {
        /** `CONST_ME_FIREAREA` (`src/utils/utils_definitions.hpp:67`). */
        const val CONST_ME_FIREAREA = 7

        /** `CONST_ANI_FIRE` (`src/utils/utils_definitions.hpp:280`). */
        const val CONST_ANI_FIRE = 4
    }
}
