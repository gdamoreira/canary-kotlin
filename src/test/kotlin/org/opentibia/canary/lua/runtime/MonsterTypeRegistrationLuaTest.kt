package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.systems.bestiary.BestiaryRace

/**
 * Transcript tests for the `Game.createMonsterType` / `MonsterType` registration
 * path (src/lua/functions/core/game/game_functions.cpp:118,
 * src/lua/functions/creatures/monster/monster_type_functions.cpp). The scripts
 * mutate the very `MonsterType` the `g_monsters()` registry holds, exactly like
 * the C++ setters write `MonsterType::info`.
 */
class MonsterTypeRegistrationLuaTest {

    private class Fixture(
        val registry: Monsters = Monsters(),
        val env: LuaEnvironment = LuaEnvironment().apply { initState() },
    ) {
        init {
            env.worldOperations!!.attachMonsters(registry)
        }
    }

    @Test
    fun `createMonsterType registers the type and returns a directly callable userdata`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:health(200)
            mType:maxHealth(200)
            mType:experience(5)
            mType:race("blood")
            mType:corpseId(5964)
            mType:baseSpeed(120)
            mType:outfit({ lookType = 21 })
            """.trimIndent(),
        )

        val rat = fixture.registry.getMonsterType("rat")!!
        assertThat(rat.name).isEqualTo("Rat")
        assertThat(rat.info.health).isEqualTo(200)
        assertThat(rat.info.healthMax).isEqualTo(200)
        assertThat(rat.info.experience).isEqualTo(5L)
        assertThat(rat.info.race).isEqualTo(RaceType.RACE_BLOOD)
        assertThat(rat.info.lookcorpse).isEqualTo(5964)
        assertThat(rat.info.baseSpeed).isEqualTo(120)
        assertThat(rat.info.outfit.lookType).isEqualTo(21)
    }

    @Test
    fun `variant and alternate names register every name on the same type`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local variant = Game.createMonsterType("Dragon", "shiny", "Shiny Dragon")
            local forced = Game.createMonsterType("Wolf", "!winter")
            """.trimIndent(),
        )

        // A variant that does not start with "!" registers only `variant|name` plus the
        // alternate name, never the bare name (game_functions.cpp:133-150).
        assertThat(fixture.registry.getMonsterType("dragon", silent = true)).isNull()
        val dragon = fixture.registry.getMonsterType("shiny|dragon")!!
        assertThat(fixture.registry.getMonsterType("shiny dragon")).isSameAs(dragon)
        assertThat(dragon.name).isEqualTo("Shiny Dragon")
        assertThat(dragon.variantName).isEqualTo("shiny")

        val wolf = fixture.registry.getMonsterType("wolf")!!
        assertThat(fixture.registry.getMonsterType("winter|wolf")).isSameAs(wolf)
        assertThat(wolf.variantName).isEqualTo("winter")
    }

    @Test
    fun `duplicate registration raises a script error like the C++ lua_error branch`() {
        val fixture = Fixture()

        val error = runCatching {
            fixture.env.loadChunk(
                """
                Game.createMonsterType("Rat")
                Game.createMonsterType("Rat")
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(ScriptError::class.java)
        assertThat(error!!.message).contains("already registered")
    }

    @Test
    fun `attribute setters write the domain monster info`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:nameDescription("a rat")
            mType:raceId(21)
            mType:Bestiaryclass("Mammal")
            mType:Bestiaryrace(BESTY_RACE_MAMMAL)
            mType:BestiarytoKill(250)
            mType:BestiaryFirstUnlock(5)
            mType:BestiarySecondUnlock(50)
            mType:BestiaryCharmsPoints(10)
            mType:BestiaryStars(1)
            mType:BestiaryOccurrence(2)
            mType:BestiaryLocations("Thais sewers")
            mType:isAttackable(true)
            mType:isHostile(true)
            mType:isSummonable(true)
            mType:isConvinceable(true)
            mType:isIllusionable(true)
            mType:isPushable(false)
            mType:isHealthHidden(true)
            mType:isBlockable(true)
            mType:isRewardBoss(true)
            mType:familiar(true)
            mType:isPreyable(false)
            mType:isPreyExclusive(true)
            mType:isForgeCreature(false)
            mType:canPushItems(true)
            mType:canPushCreatures(true)
            mType:canWalkOnEnergy(false)
            mType:canWalkOnFire(false)
            mType:canWalkOnPoison(false)
            mType:targetPreferPlayer(true)
            mType:targetPreferMaster(true)
            mType:runHealth(30)
            mType:critChance(5)
            mType:manaCost(100)
            mType:armor(33)
            mType:defense(44)
            mType:mitigation(1.5)
            mType:staticAttackChance(66)
            mType:targetDistance(3)
            mType:yellChance(8)
            mType:yellSpeedTicks(900)
            mType:changeTargetChance(7)
            mType:changeTargetSpeed(1500)
            mType:strategiesTargetNearest(1)
            mType:strategiesTargetHealth(2)
            mType:strategiesTargetDamage(3)
            mType:strategiesTargetRandom(4)
            mType:maxSummons(2)
            mType:respawnTypePeriod(RESPAWNPERIOD_DAY)
            mType:respawnTypeIsUnderground(1)
            """.trimIndent(),
        )

        val info = fixture.registry.getMonsterType("rat")!!.info
        assertThat(fixture.registry.getMonsterType("rat")!!.nameDescription).isEqualTo("a rat")
        assertThat(info.raceid).isEqualTo(21)
        assertThat(info.bestiaryClass).isEqualTo("Mammal")
        assertThat(info.bestiaryRace).isEqualTo(BestiaryRace.BESTY_RACE_MAMMAL)
        assertThat(info.bestiaryToUnlock).isEqualTo(250)
        assertThat(info.bestiaryFirstUnlock).isEqualTo(5)
        assertThat(info.bestiarySecondUnlock).isEqualTo(50)
        assertThat(info.bestiaryCharmsPoints).isEqualTo(10)
        assertThat(info.bestiaryStars).isEqualTo(1)
        assertThat(info.bestiaryOccurrence).isEqualTo(2)
        assertThat(info.bestiaryLocations).isEqualTo("Thais sewers")
        assertThat(info.isSummonable).isTrue()
        assertThat(info.isConvinceable).isTrue()
        assertThat(info.isIllusionable).isTrue()
        assertThat(info.pushable).isFalse()
        assertThat(info.hiddenHealth).isTrue()
        assertThat(info.isBlockable).isTrue()
        assertThat(info.isRewardBoss).isTrue()
        assertThat(info.isFamiliar).isTrue()
        assertThat(info.isPreyable).isFalse()
        assertThat(info.isPreyExclusive).isTrue()
        assertThat(info.isForgeCreature).isFalse()
        assertThat(info.canPushItems).isTrue()
        assertThat(info.canPushCreatures).isTrue()
        assertThat(info.canWalkOnEnergy).isFalse()
        assertThat(info.canWalkOnFire).isFalse()
        assertThat(info.canWalkOnPoison).isFalse()
        assertThat(info.targetPreferPlayer).isTrue()
        assertThat(info.targetPreferMaster).isTrue()
        assertThat(info.runAwayHealth).isEqualTo(30)
        assertThat(info.critChance).isEqualTo(5)
        assertThat(info.manaCost).isEqualTo(100)
        assertThat(info.armor).isEqualTo(33)
        assertThat(info.defense).isEqualTo(44)
        assertThat(info.mitigation).isEqualTo(1.5f)
        assertThat(info.staticAttackChance).isEqualTo(66)
        assertThat(info.targetDistance).isEqualTo(3)
        assertThat(info.yellChance).isEqualTo(8)
        assertThat(info.yellSpeedTicks).isEqualTo(900)
        assertThat(info.changeTargetChance).isEqualTo(7)
        assertThat(info.changeTargetSpeed).isEqualTo(1500)
        assertThat(info.strategiesTargetNearest).isEqualTo(1)
        assertThat(info.strategiesTargetHealth).isEqualTo(2)
        assertThat(info.strategiesTargetDamage).isEqualTo(3)
        assertThat(info.strategiesTargetRandom).isEqualTo(4)
        assertThat(info.maxSummons).isEqualTo(2)
        assertThat(info.respawnType.period).isEqualTo(RespawnPeriod.RESPAWNPERIOD_DAY)
        assertThat(info.respawnType.underground).isTrue()
    }

    @Test
    fun `immunities elements voices summons and sounds write the domain monster info`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:combatImmunities("fire")
            mType:combatImmunities("energy")
            mType:conditionImmunities("poison")
            mType:conditionImmunities("invisible")
            mType:addElement(COMBAT_FIREDAMAGE, 20)
            mType:addReflect(COMBAT_DEATHDAMAGE, 10)
            mType:addHealing(COMBAT_HEALING, 30)
            mType:addVoice("squeak", 1000, 5, false)
            mType:addSummon("Cave Rat", 2000, 10, 3)
            mType:soundChance(9)
            mType:soundSpeedTicks(700)
            mType:addSound(SOUND_EFFECT_TYPE_MONSTER_CLOSE_ATK_FIST)
            mType:deathSound(SOUND_EFFECT_TYPE_MONSTER_MELEE_ATK_BITE)
            """.trimIndent(),
        )

        val info = fixture.registry.getMonsterType("rat")!!.info
        assertThat(info.m_damageImmunities)
            .containsExactly(CombatType.COMBAT_FIREDAMAGE, CombatType.COMBAT_ENERGYDAMAGE)
        assertThat(info.m_conditionImmunities)
            .containsExactly(ConditionType.CONDITION_POISON, ConditionType.CONDITION_INVISIBLE)
        assertThat(info.elementMap[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(20)
        assertThat(info.reflectMap[CombatType.COMBAT_DEATHDAMAGE]).isEqualTo(10)
        assertThat(info.healingMap[CombatType.COMBAT_HEALING]).isEqualTo(30)
        assertThat(info.yellSpeedTicks).isEqualTo(1000)
        assertThat(info.yellChance).isEqualTo(5)
        assertThat(info.voiceVector).hasSize(1)
        assertThat(info.voiceVector.first().text).isEqualTo("squeak")
        assertThat(info.summons).hasSize(1)
        assertThat(info.summons.first().name).isEqualTo("Cave Rat")
        assertThat(info.summons.first().speed).isEqualTo(2000)
        assertThat(info.summons.first().chance).isEqualTo(10)
        assertThat(info.summons.first().count).isEqualTo(3)
        assertThat(info.soundChance).isEqualTo(9)
        assertThat(info.soundSpeedTicks).isEqualTo(700)
        assertThat(info.soundVector.map { it.value }).containsExactly(2)
        assertThat(info.deathSound.value).isEqualTo(102)
    }

    @Test
    fun `addAttack and addDefense deserialize the monster spell block`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local melee = MonsterSpell()
            melee:setType("melee")
            melee:setInterval(2000)
            melee:setCombatValue(0, -10)
            mType:addAttack(melee)
            local combat = MonsterSpell()
            combat:setType("combat")
            combat:setCombatType(COMBAT_FIREDAMAGE)
            combat:setChance(25)
            combat:setRange(5)
            combat:setCombatValue(-5, -20)
            mType:addDefense(combat)
            """.trimIndent(),
        )

        val info = fixture.registry.getMonsterType("rat")!!.info
        assertThat(info.attackSpells).hasSize(1)
        val melee = info.attackSpells.first()
        assertThat(melee.isMelee).isTrue()
        assertThat(melee.combatSpell).isTrue()
        assertThat(melee.speed).isEqualTo(2000)
        assertThat(melee.range).isEqualTo(1)
        assertThat(melee.minCombatValue).isEqualTo(-10)
        assertThat(melee.maxCombatValue).isZero()

        assertThat(info.defenseSpells).hasSize(1)
        val combat = info.defenseSpells.first()
        assertThat(combat.isMelee).isFalse()
        assertThat(combat.chance).isEqualTo(25)
        assertThat(combat.range).isEqualTo(5)
        assertThat(combat.minCombatValue).isEqualTo(-20)
        assertThat(combat.maxCombatValue).isEqualTo(-5)
    }

    @Test
    fun `getSounds returns the last sound table like the C++ stack quirk`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            mType:addSound(SOUND_EFFECT_TYPE_MONSTER_MELEE_ATK_FIST)
            mType:addSound(SOUND_EFFECT_TYPE_MONSTER_MELEE_ATK_BITE)
            sounds = mType:getSounds()
            """.trimIndent(),
        )

        // The C++ loop allocates a fresh inner table per sound and `lua_rawseti(-2, i)`
        // writes inside the new table, so the returned value is the last inner table
        // keyed by its position (monster_type_functions.cpp:1877-1885).
        val sounds = fixture.env.globals.get("sounds")
        assertThat(sounds.get(2).toint()).isEqualTo(102)
        assertThat(sounds.get(1).isnil()).isTrue()
    }

    @Test
    fun `MonsterType class constructor resolves registered types`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            Game.createMonsterType("Rat")
            resolved = MonsterType("Rat")
            missing = MonsterType("Unknown")
            """.trimIndent(),
        )

        assertThat(fixture.env.globals.get("resolved").isnil()).isFalse()
        assertThat(fixture.env.globals.get("missing").isnil()).isTrue()
    }

    @Test
    fun `registry-backed getMonsterTypes and getMonsterTypeByName see script types`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            Game.createMonsterType("Rat")
            found = Game.getMonsterTypeByName("rat")
            types = Game.getMonsterTypes()
            """.trimIndent(),
        )

        assertThat(fixture.env.globals.get("found").isnil()).isFalse()
        assertThat(fixture.env.globals.get("types").get("rat").isnil()).isFalse()
    }

    @Test
    fun `attack list getter renders the registered spell blocks`() {
        val fixture = Fixture()

        fixture.env.loadChunk(
            """
            local mType = Game.createMonsterType("Rat")
            local melee = MonsterSpell()
            melee:setType("melee")
            melee:setInterval(2000)
            melee:setCombatValue(0, -10)
            mType:addAttack(melee)
            list = mType:getAttackList()
            """.trimIndent(),
        )

        val list = fixture.env.globals.get("list")
        assertThat(list.length()).isEqualTo(1)
        val block = list.get(1)
        assertThat(block.get("isMelee").toint()).isEqualTo(1)
        assertThat(block.get("isCombatSpell").toint()).isEqualTo(1)
        assertThat(block.get("speed").toint()).isEqualTo(2000)
        assertThat(block.get("spell").isnil()).isTrue()
    }
}
